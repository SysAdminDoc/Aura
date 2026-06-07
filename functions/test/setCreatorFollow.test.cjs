const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt } = require("../lib/quotaEngine.js");
const {
  followDedupeKey,
  normalizeFollowPayload,
  setCreatorFollowHandler,
} = require("../lib/followHandler.js");

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);

function validRequest(overrides = {}) {
  return {
    auth: { uid: "follower1" },
    app: { appId: "aura-test-app" },
    data: {
      operationId: "follow-op-1",
      clientSentAt: NOW - 1_000,
      payload: {
        creatorId: "creator.one",
        label: " Creator   One ",
        following: true,
        ...overrides,
      },
    },
  };
}

class FakeFollowBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.dedupe = new Map();
    this.quotas = new Map();
    this.follows = new Map();
  }

  nowMillis() {
    return this.now;
  }

  async readFollowState(uid, creatorKey) {
    return this.follows.has(`${uid}/${creatorKey}`);
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

  async commitFollow(input) {
    const followKey = `${input.uid}/${input.payload.creatorKey}`;
    if (input.payload.following) {
      this.follows.set(followKey, {
        creatorId: input.payload.creatorId,
        label: input.payload.label,
        followedAt: input.followedAt,
      });
    } else {
      this.follows.delete(followKey);
    }
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
}

test("accepted follow writes creator row, quota, and dedupe marker", async () => {
  const backend = new FakeFollowBackend();
  const result = await setCreatorFollowHandler(validRequest(), backend);

  assert.deepEqual(result, {
    operationId: "follow-op-1",
    status: "accepted",
    following: true,
    targetPath: "/creator_follows/follower1/creator_one",
    serverTimeMillis: NOW,
  });
  assert.deepEqual(backend.follows.get("follower1/creator_one"), {
    creatorId: "creator.one",
    label: "Creator One",
    followedAt: NOW,
  });
  assert.equal(backend.quotas.get("follower1/20260607/follows").count, 1);
  assert.equal(
    backend.dedupe.get("follower1/follows/creator_one_follow").targetPath,
    "/creator_follows/follower1/creator_one",
  );
});

test("accepted unfollow removes creator row with a separate dedupe key", async () => {
  const backend = new FakeFollowBackend();
  backend.follows.set("follower1/creator_one", {
    creatorId: "creator.one",
    label: "Creator One",
    followedAt: NOW - 10_000,
  });
  backend.dedupe.set(
    "follower1/follows/creator_one_follow",
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/creator_follows/follower1/creator_one",
      ttlMillis: 5_000,
    }),
  );

  const result = await setCreatorFollowHandler(validRequest({ following: false }), backend);

  assert.equal(result.status, "accepted");
  assert.equal(result.following, false);
  assert.equal(backend.follows.has("follower1/creator_one"), false);
  assert.equal(backend.dedupe.has("follower1/follows/creator_one_unfollow"), true);
});

test("no-op follow states return duplicate before quota reservation", async () => {
  const followedBackend = new FakeFollowBackend();
  followedBackend.follows.set("follower1/creator_one", {
    creatorId: "creator.one",
    label: "Creator One",
    followedAt: NOW,
  });

  const followedResult = await setCreatorFollowHandler(validRequest(), followedBackend);

  assert.equal(followedResult.status, "duplicate");
  assert.equal(followedBackend.quotas.size, 0);

  const missingBackend = new FakeFollowBackend();
  const missingResult = await setCreatorFollowHandler(validRequest({ following: false }), missingBackend);

  assert.equal(missingResult.status, "duplicate");
  assert.equal(missingBackend.quotas.size, 0);
});

test("active same-state dedupe returns duplicate before follow commit", async () => {
  const backend = new FakeFollowBackend();
  backend.dedupe.set(
    "follower1/follows/creator_one_follow",
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/creator_follows/follower1/creator_one",
      ttlMillis: 5_000,
    }),
  );

  const result = await setCreatorFollowHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(backend.follows.size, 0);
});

test("cooldown and daily-limit quota rejections do not commit follow changes", async () => {
  const cooldownBackend = new FakeFollowBackend();
  cooldownBackend.quotas.set("follower1/20260607/follows", {
    count: 1,
    lastAt: NOW - 1_000,
  });
  await assert.rejects(
    () => setCreatorFollowHandler(validRequest(), cooldownBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "cooldown");
      assert.equal(error.details.retryAfterMillis, 4_000);
      return true;
    },
  );
  assert.equal(cooldownBackend.follows.size, 0);

  const limitBackend = new FakeFollowBackend(Date.UTC(2026, 5, 7, 23, 59, 58));
  limitBackend.quotas.set("follower1/20260607/follows", {
    count: 50,
    lastAt: limitBackend.now - 5_000,
  });
  await assert.rejects(
    () => setCreatorFollowHandler(validRequest(), limitBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "daily-limit");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
  assert.equal(limitBackend.follows.size, 0);
});

test("callable identity requires Firebase Auth and App Check", async () => {
  const backend = new FakeFollowBackend();
  await assert.rejects(
    () => setCreatorFollowHandler({ ...validRequest(), auth: undefined }, backend),
    { code: "unauthenticated" },
  );
  await assert.rejects(
    () => setCreatorFollowHandler({ ...validRequest(), app: undefined }, backend),
    { code: "failed-precondition" },
  );
});

test("follow payload is sanitized and rejects follower overrides", () => {
  const payload = normalizeFollowPayload({
    creatorId: " c/r.e#[] ",
    label: " Label   One ",
    following: true,
  });

  assert.equal(payload.creatorId, "c/r.e#[]");
  assert.equal(payload.creatorKey, "c_r_e___");
  assert.equal(payload.label, "Label One");
  assert.equal(followDedupeKey(payload), "c_r_e____follow");
  assert.throws(
    () => normalizeFollowPayload({ creatorId: "  ", following: true }),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeFollowPayload({ creatorId: "creator.one", following: true, followerUid: "fake" }),
    { code: "invalid-argument" },
  );
});
