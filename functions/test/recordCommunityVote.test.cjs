const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt } = require("../lib/quotaEngine.js");
const {
  normalizeVoteContentId,
  recordCommunityVoteHandler,
} = require("../lib/voteHandler.js");

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);

function validRequest(overrides = {}) {
  return {
    auth: { uid: "voter1" },
    app: { appId: "aura-test-app" },
    data: {
      operationId: "vote-op-1",
      clientSentAt: NOW - 1_000,
      payload: {
        contentId: "WALLPAPER::COMMUNITY::cw.one",
        ...overrides,
      },
    },
  };
}

class FakeVoteBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.dedupe = new Map();
    this.quotas = new Map();
    this.votes = new Map();
    this.legacyVoters = new Set();
  }

  nowMillis() {
    return this.now;
  }

  async hasExistingVote(uid, contentId) {
    return this.votes.get(contentId)?.voters?.[uid] === true ||
      this.legacyVoters.has(`${contentId}/${uid}`);
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

  async commitVote(input) {
    const existing = this.votes.get(input.contentId) ?? { upvotes: 0, voters: {} };
    if (existing.voters[input.uid] === true) {
      return { status: "duplicate" };
    }
    const next = {
      upvotes: existing.upvotes + 1,
      voters: {
        ...existing.voters,
        [input.uid]: true,
      },
    };
    this.votes.set(input.contentId, next);
    this.legacyVoters.add(`${input.contentId}/${input.uid}`);
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
    return {
      status: "accepted",
      upvotes: next.upvotes,
    };
  }
}

test("accepted vote increments tally and writes voter and dedupe markers", async () => {
  const backend = new FakeVoteBackend();
  const result = await recordCommunityVoteHandler(validRequest(), backend);

  assert.deepEqual(result, {
    operationId: "vote-op-1",
    status: "accepted",
    targetPath: "/votes/WALLPAPER::COMMUNITY::cw_one",
    serverTimeMillis: NOW,
    upvotes: 1,
  });
  assert.equal(backend.votes.get("WALLPAPER::COMMUNITY::cw_one").voters.voter1, true);
  assert.equal(backend.legacyVoters.has("WALLPAPER::COMMUNITY::cw_one/voter1"), true);
  assert.equal(backend.quotas.get("voter1/20260607/votes").count, 1);
  assert.equal(
    backend.dedupe.get("voter1/votes/WALLPAPER::COMMUNITY::cw_one").targetPath,
    "/votes/WALLPAPER::COMMUNITY::cw_one",
  );
});

test("existing voter marker returns duplicate before quota reservation", async () => {
  const backend = new FakeVoteBackend();
  backend.votes.set("WALLPAPER::COMMUNITY::cw_one", {
    upvotes: 3,
    voters: { voter1: true },
  });

  const result = await recordCommunityVoteHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(result.targetPath, "/votes/WALLPAPER::COMMUNITY::cw_one");
  assert.equal(backend.quotas.size, 0);
  assert.equal(backend.votes.get("WALLPAPER::COMMUNITY::cw_one").upvotes, 3);
});

test("active dedupe marker returns duplicate before vote commit", async () => {
  const backend = new FakeVoteBackend();
  backend.dedupe.set(
    "voter1/votes/WALLPAPER::COMMUNITY::cw_one",
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/votes/WALLPAPER::COMMUNITY::cw_one",
      ttlMillis: 5_000,
    }),
  );

  const result = await recordCommunityVoteHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(backend.votes.size, 0);
});

test("cooldown and daily-limit quota rejections do not commit votes", async () => {
  const cooldownBackend = new FakeVoteBackend();
  cooldownBackend.quotas.set("voter1/20260607/votes", {
    count: 1,
    lastAt: NOW - 1_000,
  });
  await assert.rejects(
    () => recordCommunityVoteHandler(validRequest(), cooldownBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "cooldown");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
  assert.equal(cooldownBackend.votes.size, 0);

  const limitBackend = new FakeVoteBackend(Date.UTC(2026, 5, 7, 23, 59, 58));
  limitBackend.quotas.set("voter1/20260607/votes", {
    count: 100,
    lastAt: limitBackend.now - 3_000,
  });
  await assert.rejects(
    () => recordCommunityVoteHandler(validRequest(), limitBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "daily-limit");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
  assert.equal(limitBackend.votes.size, 0);
});

test("callable identity requires Firebase Auth and App Check", async () => {
  const backend = new FakeVoteBackend();
  await assert.rejects(
    () => recordCommunityVoteHandler({ ...validRequest(), auth: undefined }, backend),
    { code: "unauthenticated" },
  );
  await assert.rejects(
    () => recordCommunityVoteHandler({ ...validRequest(), app: undefined }, backend),
    { code: "failed-precondition" },
  );
});

test("content IDs are sanitized and required", () => {
  assert.equal(normalizeVoteContentId(" a/b.c#d[e] "), "a_b_c_d_e_");
  assert.throws(
    () => normalizeVoteContentId("  "),
    { code: "invalid-argument" },
  );
});
