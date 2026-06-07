const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt } = require("../lib/quotaEngine.js");
const {
  normalizeUserBlockPayload,
  setCommunityUserBlockHandler,
  userBlockDedupeKey,
} = require("../lib/blockHandler.js");

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);

function validRequest(overrides = {}) {
  return {
    auth: { uid: "blocker1" },
    app: { appId: "aura-test-app" },
    data: {
      operationId: "block-op-1",
      clientSentAt: NOW - 1_000,
      payload: {
        blockedUid: "blocked.one",
        blocked: true,
        reason: "spam",
        ...overrides,
      },
    },
  };
}

class FakeUserBlockBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.dedupe = new Map();
    this.quotas = new Map();
    this.privateBlocks = new Map();
    this.reverseBlocks = new Map();
  }

  nowMillis() {
    return this.now;
  }

  async readBlockState(blockerKey, blockedKey) {
    return this.privateBlocks.has(`${blockerKey}/${blockedKey}`);
  }

  async readDedupeMarker(uid, surfaceKey, dedupeKey) {
    return this.dedupe.get(`${uid}/${surfaceKey}/${dedupeKey}`) ?? null;
  }

  async reserveQuota(uid, dayKey, surface, nowMillis, dedupe) {
    const key = `${uid}/${dayKey}/${surface.surfaceKey}`;
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

  async commitUserBlock(input) {
    const privateKey = `${input.payload.blockerKey}/${input.payload.blockedKey}`;
    const reverseKey = `${input.payload.blockedKey}/${input.payload.blockerKey}`;
    if (input.payload.blocked) {
      const row = {
        blockerUid: input.payload.blockerKey,
        blockedUid: input.payload.blockedKey,
        createdAt: input.createdAt,
        reason: input.payload.reason,
      };
      this.privateBlocks.set(privateKey, row);
      this.reverseBlocks.set(reverseKey, row);
    } else {
      this.privateBlocks.delete(privateKey);
      this.reverseBlocks.delete(reverseKey);
    }
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
}

test("accepted block writes private and reverse index rows", async () => {
  const backend = new FakeUserBlockBackend();
  const result = await setCommunityUserBlockHandler(validRequest(), backend);

  assert.deepEqual(result, {
    operationId: "block-op-1",
    status: "accepted",
    blocked: true,
    targetPath: "/community_user_blocks/blocker1/blocked_one",
    serverTimeMillis: NOW,
  });
  const row = {
    blockerUid: "blocker1",
    blockedUid: "blocked_one",
    createdAt: NOW,
    reason: "SPAM",
  };
  assert.deepEqual(backend.privateBlocks.get("blocker1/blocked_one"), row);
  assert.deepEqual(backend.reverseBlocks.get("blocked_one/blocker1"), row);
  assert.equal(backend.quotas.get("blocker1/20260607/user_blocks").count, 1);
  assert.equal(
    backend.dedupe.get("blocker1/user_blocks/blocked_one_block").targetPath,
    "/community_user_blocks/blocker1/blocked_one",
  );
});

test("accepted unblock removes private and reverse index rows with a separate dedupe key", async () => {
  const backend = new FakeUserBlockBackend();
  const row = {
    blockerUid: "blocker1",
    blockedUid: "blocked_one",
    createdAt: NOW - 10_000,
    reason: "OTHER",
  };
  backend.privateBlocks.set("blocker1/blocked_one", row);
  backend.reverseBlocks.set("blocked_one/blocker1", row);
  backend.dedupe.set(
    "blocker1/user_blocks/blocked_one_block",
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/community_user_blocks/blocker1/blocked_one",
      ttlMillis: 5_000,
    }),
  );

  const result = await setCommunityUserBlockHandler(validRequest({ blocked: false }), backend);

  assert.equal(result.status, "accepted");
  assert.equal(result.blocked, false);
  assert.equal(backend.privateBlocks.has("blocker1/blocked_one"), false);
  assert.equal(backend.reverseBlocks.has("blocked_one/blocker1"), false);
  assert.equal(backend.dedupe.has("blocker1/user_blocks/blocked_one_unblock"), true);
});

test("no-op block states return duplicate before quota reservation", async () => {
  const blockedBackend = new FakeUserBlockBackend();
  blockedBackend.privateBlocks.set("blocker1/blocked_one", {
    blockerUid: "blocker1",
    blockedUid: "blocked_one",
    createdAt: NOW,
    reason: "OTHER",
  });

  const blockedResult = await setCommunityUserBlockHandler(validRequest(), blockedBackend);

  assert.equal(blockedResult.status, "duplicate");
  assert.equal(blockedBackend.quotas.size, 0);

  const missingBackend = new FakeUserBlockBackend();
  const missingResult = await setCommunityUserBlockHandler(validRequest({ blocked: false }), missingBackend);

  assert.equal(missingResult.status, "duplicate");
  assert.equal(missingBackend.quotas.size, 0);
});

test("active same-state dedupe returns duplicate before block commit", async () => {
  const backend = new FakeUserBlockBackend();
  backend.dedupe.set(
    "blocker1/user_blocks/blocked_one_block",
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/community_user_blocks/blocker1/blocked_one",
      ttlMillis: 5_000,
    }),
  );

  const result = await setCommunityUserBlockHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(backend.privateBlocks.size, 0);
  assert.equal(backend.reverseBlocks.size, 0);
});

test("cooldown and daily-limit quota rejections do not commit block changes", async () => {
  const cooldownBackend = new FakeUserBlockBackend();
  cooldownBackend.quotas.set("blocker1/20260607/user_blocks", {
    count: 1,
    lastAt: NOW - 200,
  });
  await assert.rejects(
    () => setCommunityUserBlockHandler(validRequest(), cooldownBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "cooldown");
      assert.equal(error.details.retryAfterMillis, 800);
      return true;
    },
  );
  assert.equal(cooldownBackend.privateBlocks.size, 0);

  const limitBackend = new FakeUserBlockBackend(Date.UTC(2026, 5, 7, 23, 59, 58));
  limitBackend.quotas.set("blocker1/20260607/user_blocks", {
    count: 100,
    lastAt: limitBackend.now - 1_000,
  });
  await assert.rejects(
    () => setCommunityUserBlockHandler(validRequest(), limitBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "daily-limit");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
  assert.equal(limitBackend.privateBlocks.size, 0);
});

test("callable identity requires Firebase Auth and App Check", async () => {
  const backend = new FakeUserBlockBackend();
  await assert.rejects(
    () => setCommunityUserBlockHandler({ ...validRequest(), auth: undefined }, backend),
    { code: "unauthenticated" },
  );
  await assert.rejects(
    () => setCommunityUserBlockHandler({ ...validRequest(), app: undefined }, backend),
    { code: "failed-precondition" },
  );
});

test("block payload is sanitized and rejects invalid ownership", () => {
  const payload = normalizeUserBlockPayload({
    blockedUid: " blocked/one#[] ",
    blocked: true,
    reason: "rights",
  }, "blocker.1");

  assert.equal(payload.blockerKey, "blocker_1");
  assert.equal(payload.blockedUid, "blocked_one___");
  assert.equal(payload.reason, "RIGHTS");
  assert.equal(userBlockDedupeKey(payload), "blocked_one____block");
  assert.throws(
    () => normalizeUserBlockPayload({ blockedUid: "blocker.1", blocked: true }, "blocker.1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeUserBlockPayload({ blockedUid: "blocked.one", blocked: true, blockerUid: "fake" }, "blocker.1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeUserBlockPayload({ blockedUid: "blocked.one", blocked: true, reason: "UNKNOWN" }, "blocker.1"),
    { code: "invalid-argument" },
  );
});
