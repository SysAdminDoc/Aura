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

const REPORT_SURFACE = surfaceByFunctionName("submitCommunityReport");
const MAX_CONTENT_ID = 240;
const MAX_NOTE = 500;
const MAX_SOURCE_URL = 2_048;
const MAX_SHORT_TEXT = 120;
const MAX_OPERATION_ID = 120;
const REPORT_KEY_REGEX = /[.#$[\]/]/g;
const WHITESPACE_REGEX = /\s+/g;
const CONTROL_REGEX = /[\u0000-\u001F\u007F]/g;
const CONTENT_TYPE_REGEX = /^[A-Z_]{3,40}$/;
const VALID_REASONS = new Set(["RIGHTS", "SOURCE_REMOVED", "SAFETY", "SPAM", "OTHER"]);
const VALID_CONTENT_SOURCES = new Set([
  "WALLHAVEN",
  "PICSUM",
  "BING",
  "WIKIMEDIA",
  "INTERNET_ARCHIVE",
  "REDDIT",
  "NASA",
  "FREESOUND",
  "JAMENDO",
  "AUDIUS",
  "CCMIXTER",
  "LOCAL",
  "YOUTUBE",
  "PEXELS",
  "PIXABAY",
  "KLIPY",
  "SOUNDCLOUD",
  "COMMUNITY",
  "BUNDLED",
  "AI_GENERATED",
]);

interface CallableRequestLike {
  readonly data?: unknown;
  readonly auth?: {
    readonly uid?: string;
  };
  readonly app?: unknown;
}

interface CommunityReportPayload {
  readonly contentId: string;
  readonly contentKey: string;
  readonly contentType: string;
  readonly contentSource: string;
  readonly reason: string;
  readonly note: string;
  readonly sourceUrl: string;
  readonly license: string;
  readonly uploaderName: string;
  readonly uploaderUid: string;
  readonly reporterUid: string;
  readonly reportedAt: number;
  readonly status: "OPEN";
}

interface CommunityReportEnvelope {
  readonly operationId: string;
  readonly clientSentAt: number;
  readonly payload: Record<string, unknown>;
}

interface CommitAcceptedReportInput {
  readonly uid: string;
  readonly surfaceKey: string;
  readonly dedupeKey: string;
  readonly reportId: string;
  readonly report: CommunityReportPayload;
  readonly dedupeMarker: DedupeMarker;
}

export interface SubmitReportBackend {
  nowMillis(): number;
  createReportId(): Promise<string>;
  readDedupeMarker(uid: string, surfaceKey: string, dedupeKey: string): Promise<DedupeMarker | null>;
  reserveQuota(
    uid: string,
    dayKey: string,
    surface: CommunityCallableSurface,
    nowMillis: number,
    dedupe: DedupeMarker | null,
  ): Promise<QuotaDecision>;
  commitAcceptedReport(input: CommitAcceptedReportInput): Promise<void>;
}

export function createSubmitCommunityReportCallable(backend = new FirebaseSubmitReportBackend()) {
  return onCall(callableRuntimeOptionsFor(REPORT_SURFACE), async (request) => {
    return submitCommunityReportHandler(request, backend);
  });
}

export async function submitCommunityReportHandler(
  request: CallableRequestLike,
  backend: SubmitReportBackend,
) {
  const reporterUid = requireCallableIdentity(request, REPORT_SURFACE);
  const nowMillis = backend.nowMillis();
  const envelope = normalizeEnvelope(request.data);
  const report = normalizeReportPayload(envelope.payload, reporterUid, nowMillis);
  const dayKey = utcQuotaDayKey(nowMillis);
  const dedupeKey = `${report.contentKey}_${report.reason}`;
  const dedupe = await backend.readDedupeMarker(reporterUid, REPORT_SURFACE.surfaceKey, dedupeKey);
  const decision = await backend.reserveQuota(
    reporterUid,
    dayKey,
    REPORT_SURFACE,
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
      `Community report quota blocked by ${decision.reason}.`,
      {
        operationId: envelope.operationId,
        reason: decision.reason,
        retryAfterMillis: decision.retryAfterMillis,
        serverTimeMillis: decision.serverTimeMillis,
        surfaceKey: REPORT_SURFACE.surfaceKey,
      },
    );
  }

  const reportId = await backend.createReportId();
  const targetPath = `/community_reports/${reportId}`;
  await backend.commitAcceptedReport({
    uid: reporterUid,
    surfaceKey: REPORT_SURFACE.surfaceKey,
    dedupeKey,
    reportId,
    report,
    dedupeMarker: buildDedupeMarker({
      nowMillis,
      targetPath,
    }),
  });

  return {
    operationId: envelope.operationId,
    reportId,
    status: "accepted",
    targetPath,
    serverTimeMillis: decision.serverTimeMillis,
  };
}

export function normalizeReportPayload(
  payload: Record<string, unknown>,
  reporterUid: string,
  reportedAt: number,
): CommunityReportPayload {
  if (Object.prototype.hasOwnProperty.call(payload, "reporterUid")) {
    throwInvalid("reporterUid", "Reporter UID is derived from Firebase Auth.");
  }

  const contentId = normalizeText(requiredString(payload, "contentId"), MAX_CONTENT_ID);
  if (!contentId) throwInvalid("contentId", "Report content ID is required.");
  const contentType = normalizeText(requiredString(payload, "contentType"), 40).toUpperCase();
  if (!CONTENT_TYPE_REGEX.test(contentType)) {
    throwInvalid("contentType", "Report content type is invalid.");
  }
  const contentSource = normalizeText(requiredString(payload, "contentSource"), 40).toUpperCase();
  if (!VALID_CONTENT_SOURCES.has(contentSource)) {
    throwInvalid("contentSource", "Report content source is invalid.");
  }
  const reason = normalizeText(requiredString(payload, "reason"), 40).toUpperCase();
  if (!VALID_REASONS.has(reason)) {
    throwInvalid("reason", "Report reason is invalid.");
  }

  const uploaderUid = normalizeText(optionalString(payload, "uploaderUid"), MAX_CONTENT_ID);
  return {
    contentId,
    contentKey: sanitizeReportKey(contentId),
    contentType,
    contentSource,
    reason,
    note: normalizeText(optionalString(payload, "note"), MAX_NOTE),
    sourceUrl: normalizeSourceUrl(optionalString(payload, "sourceUrl")),
    license: normalizeText(optionalString(payload, "license"), MAX_SHORT_TEXT),
    uploaderName: normalizeText(optionalString(payload, "uploaderName"), MAX_SHORT_TEXT),
    uploaderUid,
    reporterUid,
    reportedAt,
    status: "OPEN",
  };
}

function normalizeEnvelope(data: unknown): CommunityReportEnvelope {
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

function sanitizeReportKey(value: string): string {
  return value.trim().replace(REPORT_KEY_REGEX, "_").slice(0, MAX_CONTENT_ID);
}

function normalizeSourceUrl(value: string): string {
  const normalized = normalizeText(value, MAX_SOURCE_URL);
  if (!normalized) return "";
  let parsed: URL;
  try {
    parsed = new URL(normalized);
  } catch (_error) {
    throwInvalid("sourceUrl", "Report source URL must be a valid HTTPS URL.");
  }
  if (parsed.protocol !== "https:" || !parsed.hostname) {
    throwInvalid("sourceUrl", "Report source URL must use HTTPS and include a host.");
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

function throwInvalid(field: string, message: string): never {
  throw new HttpsError("invalid-argument", message, { field });
}

class FirebaseSubmitReportBackend implements SubmitReportBackend {
  private readonly root = getDatabase().ref();

  nowMillis(): number {
    return Date.now();
  }

  async createReportId(): Promise<string> {
    const key = this.root.child("community_reports").push().key;
    if (!key) {
      throw new HttpsError("internal", "Unable to allocate report ID.");
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
      throw new HttpsError("aborted", "Unable to reserve community report quota.");
    }
    return decision;
  }

  async commitAcceptedReport(input: CommitAcceptedReportInput): Promise<void> {
    await this.root.update({
      [`community_reports/${input.reportId}`]: input.report,
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
