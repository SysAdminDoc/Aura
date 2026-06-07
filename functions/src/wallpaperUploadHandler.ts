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

const WALLPAPER_UPLOAD_SURFACE = surfaceByFunctionName("finalizeCommunityWallpaperUpload");
const MAX_OPERATION_ID = 120;
const MAX_UPLOAD_ID = 240;
const MAX_NAME = 80;
const MAX_TAGS = 8;
const MAX_TAG_LENGTH = 24;
const MAX_COLORS = 6;
const MAX_SHORT_TEXT = 120;
const MAX_FILE_NAME = 240;
const MAX_URL = 2_048;
const MAX_STORAGE_PATH = 512;
const MAX_WALLPAPER_BYTES = 4 * 1024 * 1024;
const MAX_WALLPAPER_DIMENSION = 2_560;
const FIREBASE_KEY_REGEX = /[.#$[\]/]/g;
const STORAGE_SEGMENT_REGEX = /[^a-zA-Z0-9_-]/g;
const UPLOAD_TAG_SANITIZE_REGEX = /[^a-z0-9_\- ]/g;
const COLOR_HEX_REGEX = /^#[0-9A-Fa-f]{6}$/;
const WHITESPACE_REGEX = /\s+/g;
const CONTROL_REGEX = /[\u0000-\u001F\u007F]/g;
const VALID_CATEGORIES = new Set(["abstract", "amoled", "nature", "minimal", "city", "space", "other"]);
const VALID_LICENSES = new Set(["CC0", "CC BY", "CC BY-NC"]);
const VALID_FILE_TYPE = "image/jpeg";
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

interface CommunityWallpaperUploadEnvelope {
  readonly operationId: string;
  readonly clientSentAt: number;
  readonly payload: Record<string, unknown>;
}

export interface CommunityWallpaperUploadPayload {
  readonly name: string;
  readonly category: string;
  readonly tags: readonly string[];
  readonly colors: readonly string[];
  readonly thumbnailUrl: string;
  readonly fullUrl: string;
  readonly downloadUrl: string;
  readonly storagePath: string;
  readonly width: number;
  readonly height: number;
  readonly fileSize: number;
  readonly fileType: "image/jpeg";
  readonly originalFileName: string;
  readonly uploaderLabel: string;
  readonly license: string;
  readonly rightsAttested: true;
  readonly sourceUrl: string;
  readonly uploaderKey: string;
}

interface CommitWallpaperUploadInput {
  readonly uid: string;
  readonly surfaceKey: string;
  readonly dedupeKey: string;
  readonly uploadId: string;
  readonly payload: CommunityWallpaperUploadPayload;
  readonly uploadedAt: number;
  readonly dedupeMarker: DedupeMarker;
}

export interface WallpaperUploadBackend {
  nowMillis(): number;
  createUploadId(): Promise<string>;
  readDedupeMarker(uid: string, surfaceKey: string, dedupeKey: string): Promise<DedupeMarker | null>;
  reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
  ): Promise<QuotaDecision>;
  commitWallpaperUpload(input: CommitWallpaperUploadInput): Promise<void>;
}

export function createFinalizeCommunityWallpaperUploadCallable(backend = new FirebaseWallpaperUploadBackend()) {
  return onCall(callableRuntimeOptionsFor(WALLPAPER_UPLOAD_SURFACE), async (request) => {
    return finalizeCommunityWallpaperUploadHandler(request, backend);
  });
}

export async function finalizeCommunityWallpaperUploadHandler(
  request: CallableRequestLike,
  backend: WallpaperUploadBackend = new FirebaseWallpaperUploadBackend(),
) {
  const uid = requireCallableIdentity(request, WALLPAPER_UPLOAD_SURFACE);
  const nowMillis = backend.nowMillis();
  const envelope = normalizeEnvelope(request.data);
  const payload = normalizeWallpaperUploadPayload(envelope.payload, uid);
  const dayKey = utcQuotaDayKey(nowMillis);
  const dedupeKey = wallpaperUploadDedupeKey(payload);
  const dedupe = await backend.readDedupeMarker(uid, WALLPAPER_UPLOAD_SURFACE.surfaceKey, dedupeKey);
  const decision = await backend.reserveQuota(
    uid,
    dayKey,
    WALLPAPER_UPLOAD_SURFACE,
    nowMillis,
    dedupe,
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
      `Community wallpaper upload quota blocked by ${decision.reason}.`,
      {
        operationId: envelope.operationId,
        reason: decision.reason,
        retryAfterMillis: decision.retryAfterMillis,
        serverTimeMillis: decision.serverTimeMillis,
        surfaceKey: WALLPAPER_UPLOAD_SURFACE.surfaceKey,
      },
    );
  }

  const uploadId = sanitizeUploadId(await backend.createUploadId());
  if (!uploadId) {
    throw new HttpsError("internal", "Unable to allocate wallpaper upload ID.");
  }
  const targetPath = `/community_wallpapers/${uploadId}`;
  const ownerIndexPath = `/owner_uploads/${payload.uploaderKey}/wallpapers/${uploadId}`;

  await backend.commitWallpaperUpload({
    uid,
    surfaceKey: WALLPAPER_UPLOAD_SURFACE.surfaceKey,
    dedupeKey,
    uploadId,
    payload,
    uploadedAt: nowMillis,
    dedupeMarker: buildDedupeMarker({
      nowMillis,
      targetPath,
    }),
  });

  return {
    operationId: envelope.operationId,
    uploadId,
    publicId: `cw_${uploadId}`,
    status: "accepted",
    targetPath,
    ownerIndexPath,
    serverTimeMillis: decision.serverTimeMillis,
  };
}

export function normalizeWallpaperUploadPayload(
  payload: Record<string, unknown>,
  uploaderUid: string,
): CommunityWallpaperUploadPayload {
  for (const field of SERVER_DERIVED_FIELDS) {
    if (Object.prototype.hasOwnProperty.call(payload, field)) {
      throwInvalid(field, `${field} is derived by the backend.`);
    }
  }

  const uploaderKey = sanitizeOwnerKey(uploaderUid);
  if (!uploaderKey) throwInvalid("uploaderUid", "Uploader UID is required.");
  const storagePath = normalizeStoragePath(requiredString(payload, "storagePath"), uploaderUid);
  const name = normalizeShortText(requiredString(payload, "name"), MAX_NAME);
  if (!name) throwInvalid("name", "Wallpaper name is required.");
  const category = normalizeCategory(requiredString(payload, "category"));
  const tags = normalizeTags(payload.tags);
  const colors = normalizeColors(payload.colors);
  const thumbnailUrl = normalizeHttpsUrl(requiredString(payload, "thumbnailUrl"), "thumbnailUrl");
  const fullUrl = normalizeHttpsUrl(requiredString(payload, "fullUrl"), "fullUrl");
  const downloadUrl = normalizeHttpsUrl(requiredString(payload, "downloadUrl"), "downloadUrl");
  const width = normalizePositiveInteger(payload, "width", MAX_WALLPAPER_DIMENSION);
  const height = normalizePositiveInteger(payload, "height", MAX_WALLPAPER_DIMENSION);
  const fileSize = normalizePositiveInteger(payload, "fileSize", MAX_WALLPAPER_BYTES);
  const fileType = normalizeFileType(requiredString(payload, "fileType"));
  const originalFileName = normalizeShortText(
    optionalString(payload, "originalFileName") || fileNameFromStoragePath(storagePath),
    MAX_FILE_NAME,
  ) || "community-wallpaper.jpg";
  const uploaderLabel = normalizeShortText(optionalString(payload, "uploaderLabel"), MAX_SHORT_TEXT)
    || uploaderUid.slice(0, 8);
  const license = normalizeLicense(requiredString(payload, "license"));
  const rightsAttested = requiredBoolean(payload, "rightsAttested");
  if (!rightsAttested) {
    throwInvalid("rightsAttested", "Wallpaper upload rights must be confirmed.");
  }
  const sourceUrl = normalizeOptionalHttpsUrl(optionalString(payload, "sourceUrl"), "sourceUrl");

  return {
    name,
    category,
    tags,
    colors,
    thumbnailUrl,
    fullUrl,
    downloadUrl,
    storagePath,
    width,
    height,
    fileSize,
    fileType,
    originalFileName,
    uploaderLabel,
    license,
    rightsAttested: true,
    sourceUrl,
    uploaderKey,
  };
}

export function wallpaperUploadDedupeKey(payload: CommunityWallpaperUploadPayload): string {
  return `sp_${createHash("sha256").update(payload.storagePath).digest("hex")}`;
}

function normalizeEnvelope(data: unknown): CommunityWallpaperUploadEnvelope {
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
    throwInvalid("category", "Wallpaper upload category is invalid.");
  }
  return normalized;
}

function normalizeTags(value: unknown): readonly string[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    throwInvalid("tags", "Wallpaper upload tags must be an array.");
  }
  const tags: string[] = [];
  for (const entry of value) {
    if (typeof entry !== "string") {
      throwInvalid("tags", "Wallpaper upload tags must be strings.");
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

function normalizeColors(value: unknown): readonly string[] {
  if (value === undefined || value === null) return [];
  if (!Array.isArray(value)) {
    throwInvalid("colors", "Wallpaper upload colors must be an array.");
  }
  const colors: string[] = [];
  for (const entry of value) {
    if (typeof entry !== "string") {
      throwInvalid("colors", "Wallpaper upload colors must be strings.");
    }
    const color = entry.trim().toUpperCase();
    if (!COLOR_HEX_REGEX.test(color)) {
      throwInvalid("colors", "Wallpaper upload colors must be #RRGGBB values.");
    }
    if (!colors.includes(color)) colors.push(color);
    if (colors.length >= MAX_COLORS) break;
  }
  return colors;
}

function normalizeFileType(value: string): "image/jpeg" {
  const fileType = normalizeShortText(value, 120).toLowerCase();
  if (fileType !== VALID_FILE_TYPE) {
    throwInvalid("fileType", "Wallpaper upload file type is invalid.");
  }
  return VALID_FILE_TYPE;
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
    throwInvalid("license", "Wallpaper upload license is invalid.");
  }
  return mapped;
}

function normalizeStoragePath(value: string, uploaderUid: string): string {
  const normalized = value.replace(CONTROL_REGEX, "").trim();
  if (normalized.length <= "wallpapers/".length || normalized.length > MAX_STORAGE_PATH) {
    throwInvalid("storagePath", "Wallpaper upload storage path is invalid.");
  }
  const ownerSegment = sanitizeStorageSegment(uploaderUid);
  const parts = normalized.split("/");
  if (
    parts.length !== 3 ||
    parts[0] !== "wallpapers" ||
    parts[1] !== ownerSegment ||
    parts[2].trim() === "" ||
    parts[2] === "." ||
    parts[2] === ".."
  ) {
    throwInvalid("storagePath", "Wallpaper upload storage path must belong to the authenticated user.");
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

function fileNameFromStoragePath(storagePath: string): string {
  return storagePath.split("/").pop() ?? "community-wallpaper.jpg";
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

function throwInvalid(field: string, message: string): never {
  throw new HttpsError("invalid-argument", message, { field });
}

class FirebaseWallpaperUploadBackend implements WallpaperUploadBackend {
  private readonly root = getDatabase().ref();

  nowMillis(): number {
    return Date.now();
  }

  async createUploadId(): Promise<string> {
    const key = this.root.child("community_wallpapers").push().key;
    if (!key) {
      throw new HttpsError("internal", "Unable to allocate wallpaper upload ID.");
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
      throw new HttpsError("aborted", "Unable to reserve community wallpaper upload quota.");
    }
    return decision;
  }

  async commitWallpaperUpload(input: CommitWallpaperUploadInput): Promise<void> {
    const metadataPath = `/community_wallpapers/${input.uploadId}`;
    await this.root.update({
      [`community_wallpapers/${input.uploadId}`]: buildWallpaperMetadata(
        input.uid,
        input.payload,
        input.uploadedAt,
      ),
      [`owner_uploads/${input.payload.uploaderKey}/wallpapers/${input.uploadId}`]: {
        uploadId: input.uploadId,
        publicId: `cw_${input.uploadId}`,
        contentType: "WALLPAPER",
        metadataPath,
        storagePath: input.payload.storagePath,
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

function buildWallpaperMetadata(
  uploaderUid: string,
  payload: CommunityWallpaperUploadPayload,
  uploadedAt: number,
): Record<string, unknown> {
  return {
    name: payload.name,
    category: payload.category,
    tags: payload.tags,
    colors: payload.colors,
    thumbnailUrl: payload.thumbnailUrl,
    fullUrl: payload.fullUrl,
    downloadUrl: payload.downloadUrl,
    storagePath: payload.storagePath,
    width: payload.width,
    height: payload.height,
    fileSize: payload.fileSize,
    fileType: payload.fileType,
    originalFileName: payload.originalFileName,
    uploadedAt,
    uploaderId: uploaderUid,
    uploaderUid,
    uploaderLabel: payload.uploaderLabel,
    license: payload.license,
    rightsAttested: true,
    rightsAttestedAt: uploadedAt,
    sourceUrl: payload.sourceUrl,
    votes: 0,
  };
}
