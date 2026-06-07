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

const FOLLOW_SURFACE = surfaceByFunctionName("setCreatorFollow");
const MAX_CREATOR_ID = 240;
const MAX_LABEL = 120;
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

interface CommunityFollowEnvelope {
  readonly operationId: string;
  readonly clientSentAt: number;
  readonly payload: Record<string, unknown>;
}

export interface CommunityFollowPayload {
  readonly creatorId: string;
  readonly creatorKey: string;
  readonly label: string;
  readonly following: boolean;
}

interface CommitFollowInput {
  readonly uid: string;
  readonly surfaceKey: string;
  readonly dedupeKey: string;
  readonly payload: CommunityFollowPayload;
  readonly followedAt: number;
  readonly dedupeMarker: DedupeMarker;
}

export interface FollowBackend {
  nowMillis(): number;
  readFollowState(uid: string, creatorKey: string): Promise<boolean>;
  readDedupeMarker(uid: string, surfaceKey: string, dedupeKey: string): Promise<DedupeMarker | null>;
  reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
  ): Promise<QuotaDecision>;
  commitFollow(input: CommitFollowInput): Promise<void>;
}

export function createSetCreatorFollowCallable(backend = new FirebaseFollowBackend()) {
  return onCall(callableRuntimeOptionsFor(FOLLOW_SURFACE), async (request) => {
    return setCreatorFollowHandler(request, backend);
  });
}

export async function setCreatorFollowHandler(
  request: CallableRequestLike,
  backend: FollowBackend,
) {
  const uid = requireCallableIdentity(request, FOLLOW_SURFACE);
  const nowMillis = backend.nowMillis();
  const envelope = normalizeEnvelope(request.data);
  const payload = normalizeFollowPayload(envelope.payload);
  const targetPath = `/creator_follows/${uid}/${payload.creatorKey}`;
  const currentlyFollowing = await backend.readFollowState(uid, payload.creatorKey);

  if (currentlyFollowing === payload.following) {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      following: payload.following,
      targetPath,
      serverTimeMillis: nowMillis,
    };
  }

  const dayKey = utcQuotaDayKey(nowMillis);
  const dedupeKey = followDedupeKey(payload);
  const dedupe = await backend.readDedupeMarker(uid, FOLLOW_SURFACE.surfaceKey, dedupeKey);
  const decision = await backend.reserveQuota(
    uid,
    dayKey,
    FOLLOW_SURFACE,
    nowMillis,
    dedupe,
  );

  if (decision.status === "duplicate") {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      following: payload.following,
      targetPath: decision.targetPath ?? targetPath,
      serverTimeMillis: decision.serverTimeMillis,
    };
  }

  if (decision.status === "blocked") {
    throw new HttpsError(
      decision.code,
      `Creator follow quota blocked by ${decision.reason}.`,
      {
        operationId: envelope.operationId,
        reason: decision.reason,
        retryAfterMillis: decision.retryAfterMillis,
        serverTimeMillis: decision.serverTimeMillis,
        surfaceKey: FOLLOW_SURFACE.surfaceKey,
      },
    );
  }

  await backend.commitFollow({
    uid,
    surfaceKey: FOLLOW_SURFACE.surfaceKey,
    dedupeKey,
    payload,
    followedAt: nowMillis,
    dedupeMarker: buildDedupeMarker({
      nowMillis,
      targetPath,
    }),
  });

  return {
    operationId: envelope.operationId,
    status: "accepted",
    following: payload.following,
    targetPath,
    serverTimeMillis: decision.serverTimeMillis,
  };
}

export function normalizeFollowPayload(payload: Record<string, unknown>): CommunityFollowPayload {
  if (Object.prototype.hasOwnProperty.call(payload, "uid") ||
      Object.prototype.hasOwnProperty.call(payload, "followerUid")) {
    throwInvalid("followerUid", "Follower UID is derived from Firebase Auth.");
  }

  const creatorId = normalizeText(requiredString(payload, "creatorId"), MAX_CREATOR_ID);
  const creatorKey = sanitizeFirebaseKey(creatorId);
  if (!creatorKey) throwInvalid("creatorId", "Creator ID is required.");
  const following = requiredBoolean(payload, "following");
  const label = normalizeText(optionalString(payload, "label"), MAX_LABEL)
    || creatorId.slice(0, 8)
    || creatorKey.slice(0, 8);

  return {
    creatorId,
    creatorKey,
    label,
    following,
  };
}

export function followDedupeKey(payload: CommunityFollowPayload): string {
  return `${payload.creatorKey}_${payload.following ? "follow" : "unfollow"}`;
}

function normalizeEnvelope(data: unknown): CommunityFollowEnvelope {
  const value = objectOrInvalid(data, "request");
  const operationId = normalizeText(requiredString(value, "operationId"), MAX_OPERATION_ID);
  if (!operationId) throwInvalid("operationId", "Operation ID is required.");
  const clientSentAt = requiredNumber(value, "clientSentAt");
  if (clientSentAt <= 0) {
    throwInvalid("clientSentAt", "Client timestamp must be positive.");
  }
  return {
    operationId,
    clientSentAt,
    payload: objectOrInvalid(value.payload, "payload"),
  };
}

function sanitizeFirebaseKey(value: string): string {
  return value.replace(FIREBASE_KEY_REGEX, "_");
}

function normalizeText(value: string, maxLength: number): string {
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

function optionalString(value: Record<string, unknown>, field: string): string {
  const raw = value[field];
  return typeof raw === "string" ? raw : "";
}

function requiredNumber(value: Record<string, unknown>, field: string): number {
  const raw = value[field];
  if (typeof raw !== "number" || !Number.isFinite(raw)) {
    throw new HttpsError("invalid-argument", `${field} must be a finite number.`, { field });
  }
  return raw;
}

function requiredBoolean(value: Record<string, unknown>, field: string): boolean {
  const raw = value[field];
  if (typeof raw !== "boolean") {
    throw new HttpsError("invalid-argument", `${field} must be a boolean.`, { field });
  }
  return raw;
}

function throwInvalid(field: string, message: string): never {
  throw new HttpsError("invalid-argument", message, { field });
}

class FirebaseFollowBackend implements FollowBackend {
  private readonly root = getDatabase().ref();

  nowMillis(): number {
    return Date.now();
  }

  async readFollowState(uid: string, creatorKey: string): Promise<boolean> {
    const snapshot = await this.root
      .child("creator_follows")
      .child(uid)
      .child(creatorKey)
      .get();
    return snapshot.exists();
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
      throw new HttpsError("aborted", "Unable to reserve creator follow quota.");
    }
    return decision;
  }

  async commitFollow(input: CommitFollowInput): Promise<void> {
    const followPath = `creator_follows/${input.uid}/${input.payload.creatorKey}`;
    const updates: Record<string, unknown> = {
      [`community_write_dedupe/${input.uid}/${input.surfaceKey}/${input.dedupeKey}`]: input.dedupeMarker,
    };

    if (input.payload.following) {
      updates[followPath] = {
        creatorId: input.payload.creatorId,
        label: input.payload.label,
        followedAt: input.followedAt,
      };
    } else {
      updates[followPath] = null;
    }

    await this.root.update(updates);
  }

  private quotaRef(uid: string, dayKey: string, surfaceKey: string) {
    return this.root
      .child("community_write_quotas")
      .child(uid)
      .child(dayKey)
      .child(surfaceKey);
  }
}
