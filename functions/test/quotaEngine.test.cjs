const assert = require("node:assert/strict");
const test = require("node:test");

const { surfaceByKey } = require("../lib/communityContract.js");
const {
  buildDedupeMarker,
  evaluateCommunityQuotaAttempt,
  millisUntilNextUtcDay,
  utcQuotaDayKey,
} = require("../lib/quotaEngine.js");

const reports = surfaceByKey("reports");

test("accepted quota attempt increments count and sets timestamps", () => {
  const now = Date.UTC(2026, 5, 7, 12, 0, 0);
  const decision = evaluateCommunityQuotaAttempt({
    surface: reports,
    nowMillis: now,
    quota: { count: 2, firstAt: now - 60_000, lastAt: now - reports.minIntervalMillis },
  });

  assert.equal(decision.status, "accepted");
  assert.equal(decision.quota.count, 3);
  assert.equal(decision.quota.firstAt, now - 60_000);
  assert.equal(decision.quota.lastAt, now);
});

test("active dedupe marker returns duplicate before quota mutation", () => {
  const now = Date.UTC(2026, 5, 7, 12, 0, 0);
  const marker = buildDedupeMarker({
    nowMillis: now - 1_000,
    targetPath: "/community_reports/report1",
    ttlMillis: 5_000,
  });
  const decision = evaluateCommunityQuotaAttempt({
    surface: reports,
    nowMillis: now,
    quota: { count: reports.dailyLimit },
    dedupe: marker,
  });

  assert.equal(decision.status, "duplicate");
  assert.equal(decision.code, "already-exists");
  assert.equal(decision.targetPath, "/community_reports/report1");
  assert.equal(decision.retryAfterMillis, 4_000);
});

test("cooldown blocks recent accepted attempts", () => {
  const now = Date.UTC(2026, 5, 7, 12, 0, 0);
  const decision = evaluateCommunityQuotaAttempt({
    surface: reports,
    nowMillis: now,
    quota: { count: 1, lastAt: now - 30_000, blockedCount: 2 },
  });

  assert.equal(decision.status, "blocked");
  assert.equal(decision.reason, "cooldown");
  assert.equal(decision.retryAfterMillis, reports.minIntervalMillis - 30_000);
  assert.equal(decision.quota.blockedCount, 3);
  assert.equal(decision.quota.lastBlockedAt, now);
});

test("daily limit blocks until the next UTC day boundary", () => {
  const now = Date.UTC(2026, 5, 7, 23, 59, 58);
  const decision = evaluateCommunityQuotaAttempt({
    surface: reports,
    nowMillis: now,
    quota: { count: reports.dailyLimit, lastAt: now - reports.minIntervalMillis },
  });

  assert.equal(decision.status, "blocked");
  assert.equal(decision.reason, "daily-limit");
  assert.equal(decision.retryAfterMillis, 2_000);
});

test("UTC quota day key is stable across local timezone settings", () => {
  assert.equal(utcQuotaDayKey(Date.UTC(2026, 0, 1, 0, 0, 0)), "20260101");
  assert.equal(utcQuotaDayKey(Date.UTC(2026, 11, 31, 23, 59, 59)), "20261231");
  assert.equal(millisUntilNextUtcDay(Date.UTC(2026, 5, 7, 23, 59, 59, 500)), 500);
});
