import type { CommunityCallableSurface } from "./communityContract";

const DAY_MILLIS = 24 * 60 * 60 * 1_000;
const DEDUPE_TTL_MILLIS = 7 * DAY_MILLIS;

export interface QuotaLedgerState {
  readonly count?: number;
  readonly firstAt?: number;
  readonly lastAt?: number;
  readonly blockedCount?: number;
  readonly lastBlockedAt?: number;
}

export interface DedupeMarker {
  readonly createdAt: number;
  readonly expiresAt: number;
  readonly targetPath?: string;
}

export type QuotaDecision =
  | {
      readonly status: "accepted";
      readonly code: "ok";
      readonly quota: QuotaLedgerState;
      readonly serverTimeMillis: number;
    }
  | {
      readonly status: "duplicate";
      readonly code: "already-exists";
      readonly targetPath?: string;
      readonly retryAfterMillis: number;
      readonly serverTimeMillis: number;
    }
  | {
      readonly status: "blocked";
      readonly code: "resource-exhausted";
      readonly reason: "cooldown" | "daily-limit";
      readonly retryAfterMillis: number;
      readonly quota: QuotaLedgerState;
      readonly serverTimeMillis: number;
    };

export interface QuotaAttemptInput {
  readonly surface: CommunityCallableSurface;
  readonly nowMillis: number;
  readonly quota?: QuotaLedgerState;
  readonly dedupe?: DedupeMarker | null;
}

export interface DedupeMarkerInput {
  readonly nowMillis: number;
  readonly targetPath?: string;
  readonly ttlMillis?: number;
}

export function utcQuotaDayKey(timestampMillis: number): string {
  requireValidTimestamp(timestampMillis);
  const instant = new Date(timestampMillis);
  const month = String(instant.getUTCMonth() + 1).padStart(2, "0");
  const day = String(instant.getUTCDate()).padStart(2, "0");
  return `${instant.getUTCFullYear()}${month}${day}`;
}

export function millisUntilNextUtcDay(timestampMillis: number): number {
  requireValidTimestamp(timestampMillis);
  const instant = new Date(timestampMillis);
  const nextMidnight = Date.UTC(
    instant.getUTCFullYear(),
    instant.getUTCMonth(),
    instant.getUTCDate() + 1,
  );
  return nextMidnight - timestampMillis;
}

export function evaluateCommunityQuotaAttempt(input: QuotaAttemptInput): QuotaDecision {
  const { surface, nowMillis } = input;
  requireValidTimestamp(nowMillis);
  const quota = input.quota ?? {};
  const duplicate = activeDedupeMarker(input.dedupe, nowMillis);
  if (duplicate !== null) {
    return {
      status: "duplicate",
      code: "already-exists",
      targetPath: duplicate.targetPath,
      retryAfterMillis: duplicate.expiresAt - nowMillis,
      serverTimeMillis: nowMillis,
    };
  }

  const lastAt = positiveNumberOrUndefined(quota.lastAt);
  if (lastAt !== undefined) {
    const elapsed = nowMillis - lastAt;
    if (elapsed < surface.minIntervalMillis) {
      return {
        status: "blocked",
        code: "resource-exhausted",
        reason: "cooldown",
        retryAfterMillis: surface.minIntervalMillis - Math.max(0, elapsed),
        quota: nextBlockedQuotaState(quota, nowMillis),
        serverTimeMillis: nowMillis,
      };
    }
  }

  const currentCount = Math.max(0, Math.trunc(quota.count ?? 0));
  if (currentCount >= surface.dailyLimit) {
    return {
      status: "blocked",
      code: "resource-exhausted",
      reason: "daily-limit",
      retryAfterMillis: millisUntilNextUtcDay(nowMillis),
      quota: nextBlockedQuotaState(quota, nowMillis),
      serverTimeMillis: nowMillis,
    };
  }

  return {
    status: "accepted",
    code: "ok",
    quota: nextAcceptedQuotaState(quota, nowMillis),
    serverTimeMillis: nowMillis,
  };
}

export function nextAcceptedQuotaState(
  quota: QuotaLedgerState,
  nowMillis: number,
): QuotaLedgerState {
  requireValidTimestamp(nowMillis);
  const currentCount = Math.max(0, Math.trunc(quota.count ?? 0));
  return {
    ...quota,
    count: currentCount + 1,
    firstAt: positiveNumberOrUndefined(quota.firstAt) ?? nowMillis,
    lastAt: nowMillis,
  };
}

export function nextBlockedQuotaState(
  quota: QuotaLedgerState,
  nowMillis: number,
): QuotaLedgerState {
  requireValidTimestamp(nowMillis);
  const blockedCount = Math.max(0, Math.trunc(quota.blockedCount ?? 0));
  return {
    ...quota,
    blockedCount: blockedCount + 1,
    lastBlockedAt: nowMillis,
  };
}

export function buildDedupeMarker(input: DedupeMarkerInput): DedupeMarker {
  const { nowMillis } = input;
  requireValidTimestamp(nowMillis);
  const ttlMillis = input.ttlMillis ?? DEDUPE_TTL_MILLIS;
  if (!Number.isFinite(ttlMillis) || ttlMillis <= 0) {
    throw new Error("Dedupe TTL must be positive");
  }
  return {
    createdAt: nowMillis,
    expiresAt: nowMillis + ttlMillis,
    targetPath: input.targetPath,
  };
}

function activeDedupeMarker(
  marker: DedupeMarker | null | undefined,
  nowMillis: number,
): DedupeMarker | null {
  if (marker === null || marker === undefined) return null;
  if (!Number.isFinite(marker.expiresAt)) return null;
  return marker.expiresAt > nowMillis ? marker : null;
}

function requireValidTimestamp(timestampMillis: number): void {
  if (!Number.isFinite(timestampMillis) || timestampMillis < 0) {
    throw new Error("Timestamp must be a non-negative finite millisecond value");
  }
}

function positiveNumberOrUndefined(value: number | undefined): number | undefined {
  if (value === undefined || !Number.isFinite(value) || value <= 0) return undefined;
  return value;
}
