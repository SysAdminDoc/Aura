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

const USER_BLOCK_SURFACE = surfaceByFunctionName("setCommunityUserBlock");
const MAX_UID = 240;
const MAX_OPERATION_ID = 120;
const FIREBASE_KEY_REGEX = /[.#$[\]/]/g;
const WHITESPACE_REGEX = /\s+/g;
const CONTROL_REGEX = /[\u0000-\u001F\u007F]/g;
const VALID_REASONS = new Set(["SPAM", "HARASSMENT", "SAFETY", "RIGHTS", "OTHER"]);

interface CallableRequestLike {
  readonly data?: unknown;
  readonly auth?: {
    readonly uid?: string;
  };
  readonly app?: unknown;
}

interface CommunityUserBlockEnvelope {
  readonly operationId: string;
  readonly clientSentAt: number;
  readonly payload: Record<string, unknown>;
}

export interface CommunityUserBlockPayload {
  readonly blockerKey: string;
  readonly blockedUid: string;
  readonly blockedKey: string;
  readonly blocked: boolean;
  readonly reason: string;
}

interface CommitUserBlockInput {
  readonly uid: string;
  readonly surfaceKey: string;
  readonly dedupeKey: string;
  readonly payload: CommunityUserBlockPayload;
  readonly createdAt: number;
  readonly dedupeMarker: DedupeMarker;
}

export interface UserBlockBackend {
  nowMillis(): number;
  readBlockState(blockerKey: string, blockedKey: string): Promise<boolean>;
  readDedupeMarker(uid: string, surfaceKey: string, dedupeKey: string): Promise<DedupeMarker | null>;
  reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
  ): Promise<QuotaDecision>;
  commitUserBlock(input: CommitUserBlockInput): Promise<void>;
}

export function createSetCommunityUserBlockCallable(backend = new FirebaseUserBlockBackend()) {
  return onCall(callableRuntimeOptionsFor(USER_BLOCK_SURFACE), async (request) => {
    return setCommunityUserBlockHandler(request, backend);
  });
}

export async function setCommunityUserBlockHandler(
  request: CallableRequestLike,
  backend: UserBlockBackend = new FirebaseUserBlockBackend(),
) {
  const uid = requireCallableIdentity(request, USER_BLOCK_SURFACE);
  const nowMillis = backend.nowMillis();
  const envelope = normalizeEnvelope(request.data);
  const payload = normalizeUserBlockPayload(envelope.payload, uid);
  const targetPath = `/community_user_blocks/${payload.blockerKey}/${payload.blockedKey}`;
  const currentlyBlocked = await backend.readBlockState(payload.blockerKey, payload.blockedKey);

  if (currentlyBlocked === payload.blocked) {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      blocked: payload.blocked,
      targetPath,
      serverTimeMillis: nowMillis,
    };
  }

  const dayKey = utcQuotaDayKey(nowMillis);
  const dedupeKey = userBlockDedupeKey(payload);
  const dedupe = await backend.readDedupeMarker(uid, USER_BLOCK_SURFACE.surfaceKey, dedupeKey);
  const decision = await backend.reserveQuota(
    uid,
    dayKey,
    USER_BLOCK_SURFACE,
    nowMillis,
    dedupe,
  );

  if (decision.status === "duplicate") {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      blocked: payload.blocked,
      targetPath: decision.targetPath ?? targetPath,
      serverTimeMillis: decision.serverTimeMillis,
    };
  }

  if (decision.status === "blocked") {
    throw new HttpsError(
      decision.code,
      `Community user block quota blocked by ${decision.reason}.`,
      {
        operationId: envelope.operationId,
        reason: decision.reason,
        retryAfterMillis: decision.retryAfterMillis,
        serverTimeMillis: decision.serverTimeMillis,
        surfaceKey: USER_BLOCK_SURFACE.surfaceKey,
      },
    );
  }

  await backend.commitUserBlock({
    uid,
    surfaceKey: USER_BLOCK_SURFACE.surfaceKey,
    dedupeKey,
    payload,
    createdAt: nowMillis,
    dedupeMarker: buildDedupeMarker({
      nowMillis,
      targetPath,
    }),
  });

  return {
    operationId: envelope.operationId,
    status: "accepted",
    blocked: payload.blocked,
    targetPath,
    serverTimeMillis: decision.serverTimeMillis,
  };
}

export function normalizeUserBlockPayload(
  payload: Record<string, unknown>,
  blockerUid: string,
): CommunityUserBlockPayload {
  if (Object.prototype.hasOwnProperty.call(payload, "uid") ||
      Object.prototype.hasOwnProperty.call(payload, "blockerUid")) {
    throwInvalid("blockerUid", "Blocker UID is derived from Firebase Auth.");
  }

  const blockerKey = sanitizeFirebaseKey(normalizeText(blockerUid, MAX_UID));
  if (!blockerKey) throwInvalid("blockerUid", "Blocker UID is required.");
  const blockedUid = normalizeText(requiredString(payload, "blockedUid"), MAX_UID);
  const blockedKey = sanitizeFirebaseKey(blockedUid);
  if (!blockedKey) throwInvalid("blockedUid", "Blocked UID is required.");
  if (blockerKey === blockedKey) {
    throwInvalid("blockedUid", "A user cannot block themselves.");
  }
  const blocked = requiredBoolean(payload, "blocked");
  const reason = normalizeReason(optionalString(payload, "reason"));

  return {
    blockerKey,
    blockedUid: blockedKey,
    blockedKey,
    blocked,
    reason,
  };
}

export function userBlockDedupeKey(payload: CommunityUserBlockPayload): string {
  return `${payload.blockedKey}_${payload.blocked ? "block" : "unblock"}`;
}

function normalizeEnvelope(data: unknown): CommunityUserBlockEnvelope {
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

function normalizeReason(value: string): string {
  const normalized = normalizeText(value, 40).toUpperCase();
  if (!normalized) return "OTHER";
  if (!VALID_REASONS.has(normalized)) {
    throwInvalid("reason", "Block reason is invalid.");
  }
  return normalized;
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

class FirebaseUserBlockBackend implements UserBlockBackend {
  private readonly root = getDatabase().ref();

  nowMillis(): number {
    return Date.now();
  }

  async readBlockState(blockerKey: string, blockedKey: string): Promise<boolean> {
    const snapshot = await this.root
      .child("community_user_blocks")
      .child(blockerKey)
      .child(blockedKey)
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
      throw new HttpsError("aborted", "Unable to reserve community user block quota.");
    }
    return decision;
  }

  async commitUserBlock(input: CommitUserBlockInput): Promise<void> {
    const privatePath = `community_user_blocks/${input.payload.blockerKey}/${input.payload.blockedKey}`;
    const reversePath = `community_blocked_by/${input.payload.blockedKey}/${input.payload.blockerKey}`;
    const updates: Record<string, unknown> = {
      [`community_write_dedupe/${input.uid}/${input.surfaceKey}/${input.dedupeKey}`]: input.dedupeMarker,
    };

    if (input.payload.blocked) {
      const row = {
        blockerUid: input.payload.blockerKey,
        blockedUid: input.payload.blockedKey,
        createdAt: input.createdAt,
        reason: input.payload.reason,
      };
      updates[privatePath] = row;
      updates[reversePath] = row;
    } else {
      updates[privatePath] = null;
      updates[reversePath] = null;
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
