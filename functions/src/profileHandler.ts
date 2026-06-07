import { createHash } from "node:crypto";

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

const PROFILE_SURFACE = surfaceByFunctionName("updateCreatorProfile");
const MAX_DISPLAY_NAME = 80;
const MAX_BIO = 280;
const MAX_URL = 2_048;
const MAX_OPERATION_ID = 120;
const WHITESPACE_REGEX = /\s+/g;
const CONTROL_REGEX = /[\u0000-\u001F\u007F]/g;

interface CallableRequestLike {
  readonly data?: unknown;
  readonly auth?: {
    readonly uid?: string;
  };
  readonly app?: unknown;
}

interface CreatorProfileEnvelope {
  readonly operationId: string;
  readonly clientSentAt: number;
  readonly payload: Record<string, unknown>;
}

export interface CreatorProfilePayload {
  readonly displayName: string;
  readonly bio: string;
  readonly websiteUrl: string;
  readonly avatarUrl: string;
}

export interface CreatorProfileRow extends CreatorProfilePayload {
  readonly profileUid: string;
  readonly createdAt: number;
  readonly updatedAt: number;
}

interface CommitProfileInput {
  readonly uid: string;
  readonly surfaceKey: string;
  readonly dedupeKey: string;
  readonly targetPath: string;
  readonly profile: CreatorProfileRow;
  readonly dedupeMarker: DedupeMarker;
}

export interface ProfileBackend {
  nowMillis(): number;
  readProfile(uid: string): Promise<CreatorProfileRow | null>;
  readDedupeMarker(uid: string, surfaceKey: string, dedupeKey: string): Promise<DedupeMarker | null>;
  reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
  ): Promise<QuotaDecision>;
  commitProfile(input: CommitProfileInput): Promise<void>;
}

export function createUpdateCreatorProfileCallable(backend = new FirebaseProfileBackend()) {
  return onCall(callableRuntimeOptionsFor(PROFILE_SURFACE), async (request) => {
    return updateCreatorProfileHandler(request, backend);
  });
}

export async function updateCreatorProfileHandler(
  request: CallableRequestLike,
  backend: ProfileBackend,
) {
  const uid = requireCallableIdentity(request, PROFILE_SURFACE);
  const nowMillis = backend.nowMillis();
  const envelope = normalizeEnvelope(request.data);
  const payload = normalizeProfilePayload(envelope.payload);
  const targetPath = `/creator_profiles/${uid}`;
  const existing = await backend.readProfile(uid);

  if (existing !== null && samePublicProfile(existing, payload)) {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      targetPath,
      serverTimeMillis: nowMillis,
    };
  }

  const dayKey = utcQuotaDayKey(nowMillis);
  const dedupeKey = profileDedupeKey(uid, payload);
  const dedupe = await backend.readDedupeMarker(uid, PROFILE_SURFACE.surfaceKey, dedupeKey);
  const decision = await backend.reserveQuota(
    uid,
    dayKey,
    PROFILE_SURFACE,
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
      `Creator profile quota blocked by ${decision.reason}.`,
      {
        operationId: envelope.operationId,
        reason: decision.reason,
        retryAfterMillis: decision.retryAfterMillis,
        serverTimeMillis: decision.serverTimeMillis,
        surfaceKey: PROFILE_SURFACE.surfaceKey,
      },
    );
  }

  await backend.commitProfile({
    uid,
    surfaceKey: PROFILE_SURFACE.surfaceKey,
    dedupeKey,
    targetPath,
    profile: {
      ...payload,
      profileUid: uid,
      createdAt: positiveNumberOrDefault(existing?.createdAt, nowMillis),
      updatedAt: nowMillis,
    },
    dedupeMarker: buildDedupeMarker({
      nowMillis,
      targetPath,
    }),
  });

  return {
    operationId: envelope.operationId,
    status: "accepted",
    targetPath,
    serverTimeMillis: decision.serverTimeMillis,
  };
}

export function normalizeProfilePayload(payload: Record<string, unknown>): CreatorProfilePayload {
  if (Object.prototype.hasOwnProperty.call(payload, "uid") ||
      Object.prototype.hasOwnProperty.call(payload, "profileUid") ||
      Object.prototype.hasOwnProperty.call(payload, "ownerUid")) {
    throwInvalid("profileUid", "Profile UID is derived from Firebase Auth.");
  }
  if (Object.prototype.hasOwnProperty.call(payload, "createdAt") ||
      Object.prototype.hasOwnProperty.call(payload, "updatedAt")) {
    throwInvalid("updatedAt", "Profile timestamps are assigned by the server.");
  }

  const displayName = normalizeText(requiredString(payload, "displayName"), MAX_DISPLAY_NAME);
  if (displayName.length < 2) {
    throwInvalid("displayName", "Display name must contain at least two characters.");
  }

  return {
    displayName,
    bio: normalizeText(optionalString(payload, "bio"), MAX_BIO),
    websiteUrl: normalizeOptionalHttpsUrl(optionalString(payload, "websiteUrl"), "websiteUrl"),
    avatarUrl: normalizeOptionalHttpsUrl(optionalString(payload, "avatarUrl"), "avatarUrl"),
  };
}

export function profileDedupeKey(uid: string, payload: CreatorProfilePayload): string {
  const digest = createHash("sha256")
    .update(JSON.stringify({
      profileUid: uid,
      displayName: payload.displayName,
      bio: payload.bio,
      websiteUrl: payload.websiteUrl,
      avatarUrl: payload.avatarUrl,
    }))
    .digest("hex");
  return `profile_${digest}`;
}

function samePublicProfile(existing: CreatorProfileRow, payload: CreatorProfilePayload): boolean {
  return existing.displayName === payload.displayName &&
    existing.bio === payload.bio &&
    existing.websiteUrl === payload.websiteUrl &&
    existing.avatarUrl === payload.avatarUrl;
}

function normalizeEnvelope(data: unknown): CreatorProfileEnvelope {
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

function normalizeOptionalHttpsUrl(value: string, field: string): string {
  const normalized = normalizeText(value, MAX_URL);
  if (!normalized) return "";
  let parsed: URL;
  try {
    parsed = new URL(normalized);
  } catch (_error) {
    throwInvalid(field, `${field} must be a valid HTTPS URL.`);
  }
  if (parsed.protocol !== "https:" || !parsed.hostname) {
    throwInvalid(field, `${field} must use HTTPS and include a host.`);
  }
  return normalized;
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
    throwInvalid(field, `${field} must be an object.`);
  }
  return value as Record<string, unknown>;
}

function requiredString(value: Record<string, unknown>, field: string): string {
  const raw = value[field];
  if (typeof raw !== "string") {
    throwInvalid(field, `${field} must be a string.`);
  }
  return raw;
}

function optionalString(value: Record<string, unknown>, field: string): string {
  const raw = value[field];
  if (raw === undefined || raw === null) return "";
  if (typeof raw !== "string") {
    throwInvalid(field, `${field} must be a string.`);
  }
  return raw;
}

function requiredNumber(value: Record<string, unknown>, field: string): number {
  const raw = value[field];
  if (typeof raw !== "number" || !Number.isFinite(raw)) {
    throwInvalid(field, `${field} must be a finite number.`);
  }
  return raw;
}

function positiveNumberOrDefault(value: number | undefined, fallback: number): number {
  return value !== undefined && Number.isFinite(value) && value > 0 ? value : fallback;
}

function throwInvalid(field: string, message: string): never {
  throw new HttpsError("invalid-argument", message, { field });
}

class FirebaseProfileBackend implements ProfileBackend {
  private readonly root = getDatabase().ref();

  nowMillis(): number {
    return Date.now();
  }

  async readProfile(uid: string): Promise<CreatorProfileRow | null> {
    const snapshot = await this.root.child("creator_profiles").child(uid).get();
    const value = snapshot.val();
    if (value === null || typeof value !== "object") return null;
    return value as CreatorProfileRow;
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
      throw new HttpsError("aborted", "Unable to reserve creator profile quota.");
    }
    return decision;
  }

  async commitProfile(input: CommitProfileInput): Promise<void> {
    await this.root.update({
      [`creator_profiles/${input.uid}`]: input.profile,
      [`community_write_dedupe/${input.uid}/${input.surfaceKey}/${input.dedupeKey}`]: input.dedupeMarker,
    });
  }

  private quotaRef(uid: string, dayKey: string, surfaceKey: string) {
    return this.root
      .child("community_write_quotas")
      .child(uid)
      .child(dayKey)
      .child(surfaceKey);
  }
}
