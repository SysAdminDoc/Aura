const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker } = require("../lib/quotaEngine.js");
const {
  normalizeReportPayload,
  submitCommunityReportHandler,
} = require("../lib/reportHandler.js");

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);

function validRequest(overrides = {}) {
  return {
    auth: { uid: "reporter1" },
    app: { appId: "aura-test-app" },
    data: {
      operationId: "op-1",
      clientSentAt: NOW - 1_000,
      payload: {
        contentId: "sound/one",
        contentType: "sound",
        contentSource: "community",
        reason: "spam",
        note: "  noisy   spam  ",
        sourceUrl: "https://example.com/source",
        license: "CC BY",
        uploaderName: "Uploader",
        uploaderUid: "uploader1",
        ...overrides,
      },
    },
  };
}

class FakeReportBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.nextId = "reportA";
    this.dedupe = new Map();
    this.quotas = new Map();
    this.reports = new Map();
  }

  nowMillis() {
    return this.now;
  }

  async createReportId() {
    return this.nextId;
  }

  async readDedupeMarker(uid, surfaceKey, dedupeKey) {
    return this.dedupe.get(`${uid}/${surfaceKey}/${dedupeKey}`) ?? null;
  }

  async reserveQuota(uid, dayKey, surface, nowMillis, dedupe) {
    const key = `${uid}/${dayKey}/${surface.surfaceKey}`;
    const { evaluateCommunityQuotaAttempt } = require("../lib/quotaEngine.js");
    const decision = evaluateCommunityQuotaAttempt({
      surface,
      nowMillis,
      quota: this.quotas.get(key) ?? {},
      dedupe,
    });
    if (decision.status !== "duplicate") {
      this.quotas.set(key, decision.quota);
    }
    return decision;
  }

  async commitAcceptedReport(input) {
    this.reports.set(input.reportId, input.report);
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
}

test("accepted report writes payload, quota, and dedupe marker", async () => {
  const backend = new FakeReportBackend();
  const result = await submitCommunityReportHandler(validRequest(), backend);

  assert.deepEqual(result, {
    operationId: "op-1",
    reportId: "reportA",
    status: "accepted",
    targetPath: "/community_reports/reportA",
    serverTimeMillis: NOW,
  });
  const report = backend.reports.get("reportA");
  assert.equal(report.contentId, "sound/one");
  assert.equal(report.contentKey, "sound_one");
  assert.equal(report.contentType, "SOUND");
  assert.equal(report.contentSource, "COMMUNITY");
  assert.equal(report.reason, "SPAM");
  assert.equal(report.note, "noisy spam");
  assert.equal(report.reporterUid, "reporter1");
  assert.equal(report.reportedAt, NOW);
  assert.equal(report.status, "OPEN");
  assert.equal(backend.quotas.get("reporter1/20260607/reports").count, 1);
  assert.equal(
    backend.dedupe.get("reporter1/reports/sound_one_SPAM").targetPath,
    "/community_reports/reportA",
  );
});

test("active dedupe marker returns duplicate without writing a second report", async () => {
  const backend = new FakeReportBackend();
  backend.dedupe.set(
    "reporter1/reports/sound_one_SPAM",
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/community_reports/existing",
      ttlMillis: 5_000,
    }),
  );

  const result = await submitCommunityReportHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(result.targetPath, "/community_reports/existing");
  assert.equal(backend.reports.size, 0);
});

test("cooldown rejection writes blocked quota evidence", async () => {
  const backend = new FakeReportBackend();
  backend.quotas.set("reporter1/20260607/reports", {
    count: 1,
    lastAt: NOW - 30_000,
  });

  await assert.rejects(
    () => submitCommunityReportHandler(validRequest(), backend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "cooldown");
      assert.equal(error.details.retryAfterMillis, 90_000);
      return true;
    },
  );
  assert.equal(backend.quotas.get("reporter1/20260607/reports").blockedCount, 1);
  assert.equal(backend.reports.size, 0);
});

test("daily limit rejection returns retry at UTC boundary", async () => {
  const backend = new FakeReportBackend(Date.UTC(2026, 5, 7, 23, 59, 58));
  backend.quotas.set("reporter1/20260607/reports", {
    count: 10,
    lastAt: backend.now - 120_000,
  });

  await assert.rejects(
    () => submitCommunityReportHandler(validRequest(), backend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "daily-limit");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
});

test("callable identity requires Firebase Auth and App Check", async () => {
  const backend = new FakeReportBackend();
  await assert.rejects(
    () => submitCommunityReportHandler({ ...validRequest(), auth: undefined }, backend),
    { code: "unauthenticated" },
  );
  await assert.rejects(
    () => submitCommunityReportHandler({ ...validRequest(), app: undefined }, backend),
    { code: "failed-precondition" },
  );
});

test("payload cannot override reporter UID or use insecure source URL", () => {
  assert.throws(
    () => normalizeReportPayload({ reporterUid: "other" }, "reporter1", NOW),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeReportPayload(validRequest({ sourceUrl: "http://example.com" }).data.payload, "reporter1", NOW),
    { code: "invalid-argument" },
  );
});
