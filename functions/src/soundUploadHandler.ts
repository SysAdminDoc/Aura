import { createHash } from "node:crypto";

import { getDatabase } from "firebase-admin/database";
import { getStorage } from "firebase-admin/storage";
import { HttpsError, onCall } from "firebase-functions/v2/https";

import { requireCallableIdentity } from "./callableScaffold";
import {
  callableRuntimeOptionsFor,
  surfaceByFunctionName,
  type CommunityCallableSurface,
} from "./communityContract";
import {
  buildDedupeMarker,
  quotaOperationKey,
  utcQuotaDayKey,
  type DedupeMarker,
  type QuotaDecision,
  type QuotaReservation,
  type QuotaSettlement,
} from "./quotaEngine";
import {
  type QuotaSettlingBackend,
  reserveQuotaLedger,
  runWithQuotaReservation,
  settleQuotaLedger,
} from "./quotaReservation";
import {
  attestStorageObject,
  storageObjectMetadataFrom,
  type AttestedStorageObject,
  type StorageObjectMetadata,
} from "./storageAttestation";

const SOUND_UPLOAD_SURFACE = surfaceByFunctionName("finalizeCommunitySoundUpload");
const MAX_OPERATION_ID = 120;
const MAX_UPLOAD_ID = 240;
const MAX_NAME = 80;
const MAX_TAGS = 8;
const MAX_TAG_LENGTH = 24;
const MAX_SHORT_TEXT = 120;
const MAX_URL = 2_048;
const MAX_STORAGE_PATH = 512;
// Same ceiling storage.rules applies to sounds/{uid}/ uploads.
const MAX_SOUND_BYTES = 20 * 1024 * 1024;
const FIREBASE_KEY_REGEX = /[.#$[\]/]/g;
const STORAGE_SEGMENT_REGEX = /[^a-zA-Z0-9_-]/g;
const UPLOAD_TAG_SANITIZE_REGEX = /[^a-z0-9_\- ]/g;
const WHITESPACE_REGEX = /\s+/g;
const CONTROL_REGEX = /[\u0000-\u001F\u007F]/g;
const VALID_CATEGORIES = new Set(["ringtone", "notification", "alarm"]);
const VALID_LICENSES = new Set(["CC0", "CC BY", "CC BY-NC"]);
const VALID_AUDIO_MIMES = new Set([
  "audio/mpeg",
  "audio/mp3",
  "audio/wav",
  "audio/x-wav",
  "audio/ogg",
  "audio/flac",
  "audio/aac",
  "audio/mp4",
  "audio/x-m4a",
  "audio/m4a",
  "audio/webm",
]);
const SERVER_DERIVED_FIELDS = new Set([
  "uid",
  "ownerUid",
  "uploadId",
  "publicId",
  "uploaderId",
  "uploaderUid",
  "uploadedAt",
  "rightsAttestedAt",
  "votes",
]);

interface CallableRequestLike {
  readonly data?: unknown;
  readonly auth?: {
    readonly uid?: string;
  };
  readonly app?: unknown;
}

interface CommunitySoundUploadEnvelope {
  readonly operationId: string;
  readonly clientSentAt: number;
  readonly payload: Record<string, unknown>;
}

export interface CommunitySoundUploadPayload {
  readonly name: string;
  readonly category: string;
  readonly tags: readonly string[];
  readonly downloadUrl: string;
  readonly storagePath: string;
  readonly fileType: string;
  readonly fileSize: number;
  readonly uploaderLabel: string;
  readonly license: string;
  readonly rightsAttested: true;
  readonly sourceUrl: string;
  readonly isAiGenerated: boolean | null;
  readonly uploaderKey: string;
}

export interface CommitSoundUploadInput {
  readonly uid: string;
  readonly surfaceKey: string;
  readonly dedupeKey: string;
  readonly uploadId: string;
  readonly payload: CommunitySoundUploadPayload;
  readonly storedObject: AttestedStorageObject;
  readonly uploadedAt: number;
  readonly dedupeMarker: DedupeMarker;
}

export interface SoundUploadBackend extends QuotaSettlingBackend {
  nowMillis(): number;
  createUploadId(): Promise<string>;
  readDedupeMarker(uid: string, surfaceKey: string, dedupeKey: string): Promise<DedupeMarker | null>;
  reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
    operationKey?: string,
  ): Promise<QuotaDecision>;
  verifyStorageObject(storagePath: string): Promise<StorageObjectMetadata>;
  commitSoundUpload(input: CommitSoundUploadInput): Promise<void>;
}

export function createFinalizeCommunitySoundUploadCallable(backend = new FirebaseSoundUploadBackend()) {
  return onCall(callableRuntimeOptionsFor(SOUND_UPLOAD_SURFACE), async (request) => {
    return finalizeCommunitySoundUploadHandler(request, backend);
  });
}

export async function finalizeCommunitySoundUploadHandler(
  request: CallableRequestLike,
  backend: SoundUploadBackend = new FirebaseSoundUploadBackend(),
) {
  const uid = requireCallableIdentity(request, SOUND_UPLOAD_SURFACE);
  const nowMillis = backend.nowMillis();
  const envelope = normalizeEnvelope(request.data);
  const payload = normalizeSoundUploadPayload(envelope.payload, uid);
  const dayKey = utcQuotaDayKey(nowMillis);
  const dedupeKey = soundUploadDedupeKey(payload);
  const dedupe = await backend.readDedupeMarker(uid, SOUND_UPLOAD_SURFACE.surfaceKey, dedupeKey);
  const decision = await backend.reserveQuota(
    uid,
    dayKey,
    SOUND_UPLOAD_SURFACE,
    nowMillis,
    dedupe,
    quotaOperationKey(envelope.operationId),
  );

  if (decision.status === "duplicate") {
    return {
      operationId: envelope.operationId,
      status: "duplicate",
      targetPath: decision.targetPath,
      serverTimeMillis: decision.serverTimeMillis,
    };
  }

  if (decision.status === "blocked") {
    throw new HttpsError(
      decision.code,
      `Community sound upload quota blocked by ${decision.reason}.`,
      {
        operationId: envelope.operationId,
        reason: decision.reason,
        retryAfterMillis: decision.retryAfterMillis,
        serverTimeMillis: decision.serverTimeMillis,
        surfaceKey: SOUND_UPLOAD_SURFACE.surfaceKey,
      },
    );
  }

  // Anything that stops the upload from landing refunds the reserved unit and cooldown.
  const reserved = { uid, dayKey, surface: SOUND_UPLOAD_SURFACE, reservation: decision.reservation };
  const { uploadId, targetPath, ownerIndexPath } = await runWithQuotaReservation(backend, reserved, async () => {
    const storedObject = attestStorageObject(
      payload.storagePath,
      payload.downloadUrl,
      await backend.verifyStorageObject(payload.storagePath),
      {
        allowedContentTypes: VALID_AUDIO_MIMES,
        maxBytes: MAX_SOUND_BYTES,
        declaredContentType: payload.fileType,
        declaredBytes: payload.fileSize,
      },
      { operationId: envelope.operationId },
    );

    const allocatedId = sanitizeUploadId(await backend.createUploadId());
    if (!allocatedId) {
      throw new HttpsError("internal", "Unable to allocate sound upload ID.");
    }
    const allocatedTarget = `/community_sounds/${allocatedId}`;

    await backend.commitSoundUpload({
      uid,
      surfaceKey: SOUND_UPLOAD_SURFACE.surfaceKey,
      dedupeKey,
      uploadId: allocatedId,
      payload,
      storedObject,
      uploadedAt: nowMillis,
      dedupeMarker: buildDedupeMarker({
        nowMillis,
        targetPath: allocatedTarget,
      }),
    });
    return {
      uploadId: allocatedId,
      targetPath: allocatedTarget,
      ownerIndexPath: `/owner_uploads/${payload.uploaderKey}/sounds/${allocatedId}`,
    };
  });

  return {
    operationId: envelope.operationId,
    uploadId,
    publicId: `cu_${uploadId}`,
    status: "accepted",
    targetPath,
    ownerIndexPath,
    serverTimeMillis: decision.serverTimeMillis,
  };
}

export function normalizeSoundUploadPayload(
  payload: Record<string, unknown>,
  uploaderUid: string,
): CommunitySoundUploadPayload {
  for (const field of SERVER_DERIVED_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(payload, field)) {
      throwInvalid(field, `${field} is derived by the backend.`);
    }
  }

  const uploaderKey = sanitizeOwnerKey(uploaderUid);
  if (!uploaderKey) throwInvalid("uploaderUid", "Uploader UID is required.");
  const storagePath = normalizeStoragePath(requiredString(payload, "storagePath"), uploaderUid);
  const name = normalizeShortText(requiredString(payload, "name"), MAX_NAME);
  if (!name) throwInvalid("name", "Sound name is required.");
  const category = normalizeCategory(requiredString(payload, "category"));
  const tags = normalizeTags(payload.tags);
  const downloadUrl = normalizeHttpsUrl(requiredString(payload, "downloadUrl"), "downloadUrl");
  const fileType = normalizeFileType(requiredString(payload, "fileType"));
  const fileSize = normalizePositiveInteger(payload, "fileSize", MAX_SOUND_BYTES);
  const uploaderLabel = normalizeShortText(optionalString(payload, "uploaderLabel"), MAX_SHORT_TEXT)
    || uploaderUid.slice(0, 8);
  const license = normalizeLicense(requiredString(payload, "license"));
  const rightsAttested = requiredBoolean(payload, "rightsAttested");
  if (!rightsAttested) {
    throwInvalid("rightsAttested", "Sound upload rights must be confirmed.");
  }
  const sourceUrl = normalizeOptionalHttpsUrl(optionalString(payload, "sourceUrl"), "sourceUrl");
  const isAiGenerated = optionalBoolean(payload, "isAiGenerated");

  return {
    name,
    category,
    tags,
    downloadUrl,
    storagePath,
    fileType,
    fileSize,
    uploaderLabel,
    license,
    rightsAttested: true,
    sourceUrl,
    isAiGenerated,
    uploaderKey,
  };
}

export function soundUploadDedupeKey(payload: CommunitySoundUploadPayload): string {
  return `sp_${createHash("sha256").update(payload.storagePath).digest("hex")}`;
}

function normalizeEnvelope(data: unknown): CommunitySoundUploadEnvelope {
  const value = objectOrInvalid(data, "request");
  const operationId = normalizeShortText(requiredString(value, "operationId"), MAX_OPERATION_ID);
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

function normalizeCategory(value: string): string {
  const normalized = normalizeShortText(value, 40).toLowerCase();
  if (!VALID_CATEGORIES.has(normalized)) {
    throwInvalid("category", "Sound upload category is invalid.");
  }
  return normalized;
}

function normalizeTags(value: unknown): readonly string[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    throwInvalid("tags", "Sound upload tags must be an array.");
  }
  const tags: string[] = [];
  for (const entry of value) {
    if (typeof entry !== "string") {
      throwInvalid("tags", "Sound upload tags must be strings.");
    }
    const tag = entry
      .replace(CONTROL_REGEX, " ")
      .trim()
      .toLowerCase()
      .replace(UPLOAD_TAG_SANITIZE_REGEX, "")
      .replace(WHITESPACE_REGEX, " ")
      .slice(0, MAX_TAG_LENGTH);
    if (tag && !tags.includes(tag)) tags.push(tag);
    if (tags.length >= MAX_TAGS) break;
  }
  return tags;
}

function normalizeFileType(value: string): string {
  const fileType = normalizeShortText(value, 120).toLowerCase();
  if (!VALID_AUDIO_MIMES.has(fileType)) {
    throwInvalid("fileType", "Sound upload file type is invalid.");
  }
  return fileType;
}

function normalizeLicense(value: string): string {
  const normalized = normalizeShortText(value, 40).toUpperCase();
  const mapped = normalized === "CC0 1.0"
    ? "CC0"
    : normalized === "CC-BY" || normalized === "ATTRIBUTION"
      ? "CC BY"
      : normalized === "CC-BY-NC" || normalized === "ATTRIBUTION-NONCOMMERCIAL"
        ? "CC BY-NC"
        : normalized;
  if (!VALID_LICENSES.has(mapped)) {
    throwInvalid("license", "Sound upload license is invalid.");
  }
  return mapped;
}

function normalizeStoragePath(value: string, uploaderUid: string): string {
  const normalized = value.replace(CONTROL_REGEX, "").trim();
  if (normalized.length <= "sounds/".length || normalized.length > MAX_STORAGE_PATH) {
    throwInvalid("storagePath", "Sound upload storage path is invalid.");
  }
  const ownerSegment = sanitizeStorageSegment(uploaderUid);
  const parts = normalized.split("/");
  if (
    parts.length !== 3 ||
    parts[0] !== "sounds" ||
    parts[1] !== ownerSegment ||
    parts[2].trim() === "" ||
    parts[2] === "." ||
    parts[2] === ".."
  ) {
    throwInvalid("storagePath", "Sound upload storage path must belong to the authenticated user.");
  }
  return normalized;
}

function sanitizeStorageSegment(value: string): string {
  return value
    .trim()
    .replace(STORAGE_SEGMENT_REGEX, "_")
    .replace(/^_+|_+$/g, "")
    || "user";
}

function sanitizeOwnerKey(value: string): string {
  return normalizeShortText(value, MAX_UPLOAD_ID).replace(FIREBASE_KEY_REGEX, "_");
}

function sanitizeUploadId(value: string): string {
  return normalizeShortText(value, MAX_UPLOAD_ID).replace(FIREBASE_KEY_REGEX, "_");
}

function normalizeHttpsUrl(value: string, field: string): string {
  const normalized = normalizeShortText(value, MAX_URL);
  if (!normalized) throwInvalid(field, `${field} is required.`);
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

function normalizeOptionalHttpsUrl(value: string, field: string): string {
  const normalized = normalizeShortText(value, MAX_URL);
  if (!normalized) return "";
  return normalizeHttpsUrl(normalized, field);
}

function normalizePositiveInteger(
  value: Record<string, unknown>,
  field: string,
  maxValue: number,
): number {
  const raw = value[field];
  if (typeof raw !== "number" || !Number.isFinite(raw)) {
    throwInvalid(field, `${field} must be a finite number.`);
  }
  const normalized = Math.trunc(raw);
  if (normalized !== raw || normalized <= 0 || normalized > maxValue) {
    throwInvalid(field, `${field} is outside the allowed range.`);
  }
  return normalized;
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

function requiredBoolean(value: Record<string, unknown>, field: string): boolean {
  const raw = value[field];
  if (typeof raw !== "boolean") {
    throwInvalid(field, `${field} must be a boolean.`);
  }
  return raw;
}

function optionalBoolean(value: Record<string, unknown>, field: string): boolean | null {
  const raw = value[field];
  if (raw === undefined || raw === null) return null;
  if (typeof raw !== "boolean") {
    throwInvalid(field, `${field} must be a boolean when provided.`);
  }
  return raw;
}

function throwInvalid(field: string, message: string): never {
  throw new HttpsError("invalid-argument", message, { field });
}

class FirebaseSoundUploadBackend implements SoundUploadBackend {
  private readonly root = getDatabase().ref();

  nowMillis(): number {
    return Date.now();
  }

  async verifyStorageObject(storagePath: string): Promise<StorageObjectMetadata> {
    try {
      const bucket = getStorage().bucket();
      const file = bucket.file(storagePath);
      const [metadata] = await file.getMetadata();
      return storageObjectMetadataFrom(metadata);
    } catch (e: unknown) {
      const code = (e as { code?: number })?.code;
      if (code === 404) return { exists: false };
      throw e;
    }
  }

  async createUploadId(): Promise<string> {
    const key = this.root.child("community_sounds").push().key;
    if (!key) {
      throw new HttpsError("internal", "Unable to allocate sound upload ID.");
    }
    return key;
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
    operationKey?: string,
  ): Promise<QuotaDecision> {
    return reserveQuotaLedger(
      this.root.child("community_write_quotas").child(uid),
      dayKey,
      surface,
      nowMillis,
      dedupe,
      operationKey,
    );
  }

  async settleQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    reservation: QuotaReservation,
    settlement: QuotaSettlement,
  ): Promise<void> {
    await settleQuotaLedger(this.quotaRef(uid, dayKey, surface.surfaceKey), reservation, settlement, this.nowMillis());
  }

  async commitSoundUpload(input: CommitSoundUploadInput): Promise<void> {
    const metadataPath = `/community_sounds/${input.uploadId}`;
    await this.root.update({
      [`community_sounds/${input.uploadId}`]: buildSoundMetadata(input),
      [`owner_uploads/${input.payload.uploaderKey}/sounds/${input.uploadId}`]: {
        uploadId: input.uploadId,
        publicId: `cu_${input.uploadId}`,
        contentType: "SOUND",
        metadataPath,
        storagePath: input.storedObject.storagePath,
        title: input.payload.name,
        createdAt: input.uploadedAt,
      },
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

// Exported so the test fake publishes exactly what the real backend writes.
export function buildSoundMetadata(input: CommitSoundUploadInput): Record<string, unknown> {
  const { uid: uploaderUid, payload, storedObject, uploadedAt } = input;
  return {
    name: payload.name,
    category: payload.category,
    tags: payload.tags,
    // Object fields come from Storage, never from the client's payload.
    downloadUrl: storedObject.downloadUrl,
    storagePath: storedObject.storagePath,
    fileType: storedObject.contentType,
    fileSize: storedObject.size,
    uploadedAt,
    uploaderId: uploaderUid,
    uploaderUid,
    uploaderLabel: payload.uploaderLabel,
    license: payload.license,
    rightsAttested: true,
    rightsAttestedAt: uploadedAt,
    sourceUrl: payload.sourceUrl,
    ...(payload.isAiGenerated === null ? {} : { isAiGenerated: payload.isAiGenerated }),
    votes: 0,
  };
}
