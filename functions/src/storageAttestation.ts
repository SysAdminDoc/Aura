import { HttpsError } from "firebase-functions/v2/https";

const PRODUCTION_STORAGE_HOST = "firebasestorage.googleapis.com";
const DOWNLOAD_PATH_REGEX = /^\/v0\/b\/([^/]+)\/o\/([^/]+)$/;

export interface StorageObjectMetadata {
  readonly exists: boolean;
  readonly bucket?: string;
  readonly contentType?: string;
  readonly size?: number;
  readonly downloadTokens?: readonly string[];
}

export interface StorageAttestationRules {
  readonly allowedContentTypes: ReadonlySet<string>;
  readonly maxBytes: number;
  readonly declaredContentType: string;
  readonly declaredBytes: number;
}

export interface AttestedStorageObject {
  readonly storagePath: string;
  readonly bucket: string;
  readonly contentType: string;
  readonly size: number;
  readonly downloadUrl: string;
}

export interface JpegDimensions {
  readonly width: number;
  readonly height: number;
}

// The few GCS metadata fields read here; the SDK's FileMetadata type carries many more.
export interface StorageFileMetadataLike {
  readonly bucket?: unknown;
  readonly contentType?: unknown;
  readonly size?: unknown;
  readonly metadata?: unknown;
}

export function storageObjectMetadataFrom(metadata: StorageFileMetadataLike): StorageObjectMetadata {
  const custom = metadata.metadata;
  const rawTokens = custom !== null && typeof custom === "object"
    ? (custom as Record<string, unknown>).firebaseStorageDownloadTokens
    : undefined;
  // GCS reports size as a decimal string.
  const size = typeof metadata.size === "number" || typeof metadata.size === "string"
    ? Number(metadata.size)
    : undefined;
  return {
    exists: true,
    bucket: typeof metadata.bucket === "string" && metadata.bucket ? metadata.bucket : undefined,
    contentType: typeof metadata.contentType === "string" && metadata.contentType ? metadata.contentType : undefined,
    size: size !== undefined && Number.isFinite(size) ? size : undefined,
    downloadTokens: typeof rawTokens === "string"
      ? rawTokens.split(",").map((token) => token.trim()).filter((token) => token !== "")
      : undefined,
  };
}

/**
 * Binds a client's finalize request to the object actually sitting in Storage. Anything the
 * server can't confirm fails closed, and the returned values are the only ones worth publishing.
 */
export function attestStorageObject(
  storagePath: string,
  declaredDownloadUrl: string,
  metadata: StorageObjectMetadata,
  rules: StorageAttestationRules,
  context: Record<string, unknown> = {},
): AttestedStorageObject {
  if (!metadata.exists) {
    throw attestationError(context, storagePath, "object-missing", "Storage object does not exist at the declared path.");
  }

  const { bucket, contentType: storedType, size, downloadTokens } = metadata;
  if (
    !bucket ||
    !storedType ||
    size === undefined ||
    !Number.isFinite(size) ||
    downloadTokens === undefined ||
    downloadTokens.length === 0
  ) {
    throw attestationError(context, storagePath, "metadata-unavailable", "Storage object metadata is unavailable.", {
      missing: missingMetadataFields(metadata),
    });
  }

  const actualType = normalizeContentType(storedType);
  const declaredType = normalizeContentType(rules.declaredContentType);
  if (!rules.allowedContentTypes.has(actualType) || actualType !== declaredType) {
    throw attestationError(
      context,
      storagePath,
      "content-type-mismatch",
      "Declared file type does not match the stored object.",
      { declared: declaredType, actual: actualType },
    );
  }

  if (!Number.isSafeInteger(size) || size <= 0 || size > rules.maxBytes) {
    throw attestationError(
      context,
      storagePath,
      "size-out-of-range",
      "Stored object size is outside the allowed range.",
      { actual: size, max: rules.maxBytes },
    );
  }
  if (size !== rules.declaredBytes) {
    throw attestationError(
      context,
      storagePath,
      "size-mismatch",
      "Declared file size does not match the stored object.",
      { declared: rules.declaredBytes, actual: size },
    );
  }

  const emulatorHost = storageEmulatorHost();
  let parsed: URL;
  try {
    parsed = new URL(declaredDownloadUrl);
  } catch (_error) {
    throw attestationError(context, storagePath, "download-url-mismatch", "Download URL does not point at the stored object.");
  }
  const pathMatch = DOWNLOAD_PATH_REGEX.exec(parsed.pathname);
  const protocolAllowed = emulatorHost
    ? parsed.protocol === "http:" || parsed.protocol === "https:"
    : parsed.protocol === "https:";
  if (
    !protocolAllowed ||
    parsed.host !== (emulatorHost ?? PRODUCTION_STORAGE_HOST) ||
    !pathMatch ||
    decodePathSegment(pathMatch[1]) !== bucket ||
    decodePathSegment(pathMatch[2]) !== storagePath
  ) {
    throw attestationError(context, storagePath, "download-url-mismatch", "Download URL does not point at the stored object.");
  }

  // The token proves the client got the URL from Storage; the published URL is rebuilt around it.
  const declaredTokens = parsed.searchParams.getAll("token");
  if (declaredTokens.length !== 1 || !downloadTokens.includes(declaredTokens[0])) {
    throw attestationError(
      context,
      storagePath,
      "download-token-unknown",
      "Download URL token does not belong to the stored object.",
    );
  }

  const origin = emulatorHost ? `http://${emulatorHost}` : `https://${PRODUCTION_STORAGE_HOST}`;
  return {
    storagePath,
    bucket,
    contentType: actualType,
    size,
    downloadUrl: `${origin}/v0/b/${encodeURIComponent(bucket)}/o/${encodeURIComponent(storagePath)}` +
      `?alt=media&token=${encodeURIComponent(declaredTokens[0])}`,
  };
}

/**
 * Reads width and height from the first frame header of a JPEG. Only the leading bytes are
 * needed, since SOFn comes before the entropy-coded data. Returns null for anything that
 * isn't a well-formed JPEG prefix.
 */
export function readJpegDimensions(head: Uint8Array): JpegDimensions | null {
  if (head.length < 4 || head[0] !== 0xff || head[1] !== 0xd8) return null;
  let offset = 2;
  while (offset < head.length) {
    if (head[offset] !== 0xff) return null;
    // Any number of 0xFF fill bytes may precede a marker.
    while (offset < head.length && head[offset] === 0xff) offset += 1;
    if (offset >= head.length) return null;
    const marker = head[offset];
    offset += 1;

    // TEM and RSTn stand alone with no length field.
    if (marker === 0x01 || (marker >= 0xd0 && marker <= 0xd7)) continue;
    // A second SOI, EOI, or scan data before any frame header means there is no usable frame.
    if (marker < 0xc0 || marker === 0xd8 || marker === 0xd9 || marker === 0xda) return null;

    if (offset + 2 > head.length) return null;
    const length = (head[offset] << 8) | head[offset + 1];
    if (length < 2) return null;

    if (isStartOfFrameMarker(marker)) {
      if (length < 8 || offset + 7 > head.length) return null;
      const height = (head[offset + 3] << 8) | head[offset + 4];
      const width = (head[offset + 5] << 8) | head[offset + 6];
      // Height 0 defers to a DNL marker later in the stream, which a header read can't confirm.
      if (width === 0 || height === 0) return null;
      return { width, height };
    }
    offset += length;
  }
  return null;
}

function isStartOfFrameMarker(marker: number): boolean {
  // 0xC4 (DHT), 0xC8 (JPG), and 0xCC (DAC) share the range but aren't frame headers.
  return marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
}

function normalizeContentType(value: string): string {
  return value.split(";")[0].trim().toLowerCase();
}

function decodePathSegment(value: string): string | null {
  try {
    return decodeURIComponent(value);
  } catch (_error) {
    return null;
  }
}

function storageEmulatorHost(): string | undefined {
  const raw = process.env.FIREBASE_STORAGE_EMULATOR_HOST?.trim();
  if (!raw) return undefined;
  return raw.replace(/^https?:\/\//i, "").replace(/\/+$/, "").toLowerCase() || undefined;
}

function missingMetadataFields(metadata: StorageObjectMetadata): string[] {
  const missing: string[] = [];
  if (!metadata.bucket) missing.push("bucket");
  if (!metadata.contentType) missing.push("contentType");
  if (metadata.size === undefined || !Number.isFinite(metadata.size)) missing.push("size");
  if (!metadata.downloadTokens || metadata.downloadTokens.length === 0) missing.push("downloadTokens");
  return missing;
}

function attestationError(
  context: Record<string, unknown>,
  storagePath: string,
  reason: string,
  message: string,
  extra: Record<string, unknown> = {},
): HttpsError {
  return new HttpsError("failed-precondition", message, { ...context, reason, storagePath, ...extra });
}
