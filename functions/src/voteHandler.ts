import { getDatabase } from "firebase-admin/database";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import { requireCallableIdentity } from "./callableScaffold";
import {
  callableRuntimeOptionsFor,
  surfaceByFunctionName,
  type CommunityCallableSurface,
} from "./communityContract";
import {
  buildDedupeMarker,
  evaluateCommunityQuotaAttempt,
  utcQuotaDayKey,
  type DedupeMarker,
  type QuotaDecision,
  type QuotaLedgerState,
} from "./quotaEngine";

const VOTE_SURFACE = surfaceByFunctionName("recordCommunityVote");
const MAX_CONTENT_ID = 240;
const MAX_OPERATION_ID = 120;
const FIREBASE_KEY_REGEX = /[.#$[\]/]/g;
const WHITESPACE_REGEX = /\s+/g;
const CONTROL_REGEX = /[\u0000-\u001F\u007F]/g;

interface CallableRequestLike {
  readonly data?: unknown;
  readonly auth?: {
    readonly uid?: string;
  };
  readonly app?: unknown;
}

interface CommunityVoteEnvelope {
  readonly operationId: string;
  readonly clientSentAt: number;
  readonly payload: Record<string, unknown>;
}

interface CommitVoteInput {
  readonly uid: string;
  readonly contentId: string;
  readonly surfaceKey: string;
  readonly dedupeKey: string;
  readonly dedupeMarker: DedupeMarker;
}

export interface VoteCommitResult {
  readonly status: "accepted" | "duplicate";
  readonly upvotes?: number;
}

export interface VoteBackend {
  nowMillis(): number;
  hasExistingVote(uid: string, contentId: string): Promise<boolean>;
  readDedupeMarker(uid: string, surfaceKey: string, dedupeKey: string): Promise<DedupeMarker | null>;
  reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
  ): Promise<QuotaDecision>;
  commitVote(input: CommitVoteInput): Promise<VoteCommitResult>;
}

export function createRecordCommunityVoteCallable(backend = new FirebaseVoteBackend()) {
  return onCall(callableRuntimeOptionsFor(VOTE_SURFACE), async (request) => {
    return recordCommunityVoteHandler(request, backend);
  });
}

export async function recordCommunityVoteHandler(
  request: CallableRequestLike,
  backend: VoteBackend = new FirebaseVoteBackend(),
) {
  const uid = requireCallableIdentity(request, VOTE_SURFACE);
  const nowMillis = backend.nowMillis();
  const envelope = normalizeEnvelope(request.data);
  const contentId = normalizeVoteContentId(requiredString(envelope.payload, "contentId"));
  const targetPath = `/votes/${contentId}`;
  if (await backend.hasExistingVote(uid, contentId)) {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      targetPath,
      serverTimeMillis: nowMillis,
    };
  }

  const dayKey = utcQuotaDayKey(nowMillis);
  const dedupeKey = contentId;
  const dedupe = await backend.readDedupeMarker(uid, VOTE_SURFACE.surfaceKey, dedupeKey);
  const decision = await backend.reserveQuota(
    uid,
    dayKey,
    VOTE_SURFACE,
    nowMillis,
    dedupe,
  );

  if (decision.status === "duplicate") {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      targetPath: decision.targetPath ?? targetPath,
      serverTimeMillis: decision.serverTimeMillis,
    };
  }

  if (decision.status === "blocked") {
    throw new HttpsError(
      decision.code,
      `Community vote quota blocked by ${decision.reason}.`,
      {
        operationId: envelope.operationId,
        reason: decision.reason,
        retryAfterMillis: decision.retryAfterMillis,
        serverTimeMillis: decision.serverTimeMillis,
        surfaceKey: VOTE_SURFACE.surfaceKey,
      },
    );
  }

  const commit = await backend.commitVote({
    uid,
    contentId,
    surfaceKey: VOTE_SURFACE.surfaceKey,
    dedupeKey,
    dedupeMarker: buildDedupeMarker({
      nowMillis,
      targetPath,
    }),
  });

  return {
    operationId: envelope.operationId,
    status: commit.status,
    targetPath,
    serverTimeMillis: decision.serverTimeMillis,
    upvotes: commit.upvotes,
  };
}

export function normalizeVoteContentId(value: string): string {
  const normalized = value
    .replace(CONTROL_REGEX, " ")
    .replace(WHITESPACE_REGEX, " ")
    .trim()
    .slice(0, MAX_CONTENT_ID)
    .replace(FIREBASE_KEY_REGEX, "_");
  if (!normalized) {
    throw new HttpsError("invalid-argument", "Vote content ID is required.", { field: "contentId" });
  }
  return normalized;
}

function normalizeEnvelope(data: unknown): CommunityVoteEnvelope {
  const value = objectOrInvalid(data, "request");
  const operationId = normalizeShortText(requiredString(value, "operationId"), MAX_OPERATION_ID);
  if (!operationId) {
    throw new HttpsError("invalid-argument", "Operation ID is required.", { field: "operationId" });
  }
  const clientSentAt = requiredNumber(value, "clientSentAt");
  if (clientSentAt <= 0) {
    throw new HttpsError("invalid-argument", "Client timestamp must be positive.", { field: "clientSentAt" });
  }
  return {
    operationId,
    clientSentAt,
    payload: objectOrInvalid(value.payload, "payload"),
  };
}

function normalizeShortText(value: string, maxLength: number): string {
  return value
    .replace(CONTROL_REGEX, " ")
    .replace(WHITESPACE_REGEX, " ")
    .trim()
    .slice(0, maxLength);
}

function objectOrInvalid(value: unknown, field: string): Record<string, unknown> {
  if (value === null || typeof value !== "object" || Array.isArray(value)) {
    throw new HttpsError("invalid-argument", `${field} must be an object.`, { field });
  }
  return value as Record<string, unknown>;
}

function requiredString(value: Record<string, unknown>, field: string): string {
  const raw = value[field];
  if (typeof raw !== "string") {
    throw new HttpsError("invalid-argument", `${field} must be a string.`, { field });
  }
  return raw;
}

function requiredNumber(value: Record<string, unknown>, field: string): number {
  const raw = value[field];
  if (typeof raw !== "number" || !Number.isFinite(raw)) {
    throw new HttpsError("invalid-argument", `${field} must be a finite number.`, { field });
  }
  return raw;
}

class FirebaseVoteBackend implements VoteBackend {
  private readonly root = getDatabase().ref();

  nowMillis(): number {
    return Date.now();
  }

  async hasExistingVote(uid: string, contentId: string): Promise<boolean> {
    const [nested, legacy] = await Promise.all([
      this.root.child("votes").child(contentId).child("voters").child(uid).get(),
      this.root.child("voters").child(contentId).child(uid).get(),
    ]);
    return nested.exists() || legacy.exists();
  }

  async readDedupeMarker(
    uid: string,
    surfaceKey: string,
    dedupeKey: string,
  ): Promise<DedupeMarker | null> {
    const snapshot = await this.root
      .child("community_write_dedupe")
      .child(uid)
      .child(surfaceKey)
      .child(dedupeKey)
      .get();
    const value = snapshot.val();
    if (value === null || typeof value !== "object") return null;
    return value as DedupeMarker;
  }

  async reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
  ): Promise<QuotaDecision> {
    let decision: QuotaDecision | null = null;
    const result = await this.quotaRef(uid, dayKey, surface.surfaceKey).transaction(
      (current: unknown) => {
        const quota = current !== null && typeof current === "object"
          ? current as QuotaLedgerState
          : {};
        decision = evaluateCommunityQuotaAttempt({
          surface,
          nowMillis,
          quota,
          dedupe,
        });
        if (decision.status === "duplicate") {
          return current;
        }
        return decision.quota;
      },
      undefined,
      false,
    );
    if (!result.committed || decision === null) {
      throw new HttpsError("aborted", "Unable to reserve community vote quota.");
    }
    return decision;
  }

  async commitVote(input: CommitVoteInput): Promise<VoteCommitResult> {
    let sawExistingVoter = false;
    let upvotes = 0;
    const result = await this.root.child("votes").child(input.contentId).transaction(
      (current: unknown) => {
        const voteData = current !== null && typeof current === "object"
          ? current as Record<string, unknown>
          : {};
        const voters = voteData.voters !== null && typeof voteData.voters === "object"
          ? voteData.voters as Record<string, unknown>
          : {};
        if (voters[input.uid] === true) {
          sawExistingVoter = true;
          return undefined;
        }
        const currentUpvotes = typeof voteData.upvotes === "number" ? voteData.upvotes : 0;
        upvotes = Math.max(0, Math.trunc(currentUpvotes)) + 1;
        return {
          ...voteData,
          upvotes,
          voters: {
            ...voters,
            [input.uid]: true,
          },
        };
      },
      undefined,
      false,
    );

    if (!result.committed) {
      if (sawExistingVoter) return { status: "duplicate" };
      throw new HttpsError("aborted", "Unable to commit community vote.");
    }

    await this.root.update({
      [`voters/${input.contentId}/${input.uid}`]: true,
      [`community_write_dedupe/${input.uid}/${input.surfaceKey}/${input.dedupeKey}`]: input.dedupeMarker,
    });
    return {
      status: "accepted",
      upvotes,
    };
  }

  private quotaRef(uid: string, dayKey: string, surfaceKey: string) {
    return this.root
      .child("community_write_quotas")
      .child(uid)
      .child(dayKey)
      .child(surfaceKey);
  }
}
