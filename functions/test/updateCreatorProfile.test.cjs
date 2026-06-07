const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt } = require("../lib/quotaEngine.js");
const {
  normalizeProfilePayload,
  profileDedupeKey,
  updateCreatorProfileHandler,
} = require("../lib/profileHandler.js");

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);

function validRequest(overrides = {}) {
  return {
    auth: { uid: "profileOwner1" },
    app: { appId: "aura-test-app" },
    data: {
      operationId: "profile-op-1",
      clientSentAt: NOW - 1_000,
      payload: {
        displayName: " Aura   Creator ",
        bio: " Neon walls\r\nand alert tones. ",
        websiteUrl: "https://example.com/aura",
        avatarUrl: "https://example.com/avatar.jpg",
        ...overrides,
      },
    },
  };
}

class FakeProfileBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.dedupe = new Map();
    this.quotas = new Map();
    this.profiles = new Map();
  }

  nowMillis() {
    return this.now;
  }

  async readProfile(uid) {
    return this.profiles.get(uid) ?? null;
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

  async commitProfile(input) {
    this.profiles.set(input.uid, input.profile);
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
}

test("accepted profile update writes public row, quota, and dedupe marker", async () => {
  const backend = new FakeProfileBackend();
  const result = await updateCreatorProfileHandler(validRequest(), backend);

  assert.deepEqual(result, {
    operationId: "profile-op-1",
    status: "accepted",
    targetPath: "/creator_profiles/profileOwner1",
    serverTimeMillis: NOW,
  });
  assert.deepEqual(backend.profiles.get("profileOwner1"), {
    profileUid: "profileOwner1",
    displayName: "Aura Creator",
    bio: "Neon walls and alert tones.",
    websiteUrl: "https://example.com/aura",
    avatarUrl: "https://example.com/avatar.jpg",
    createdAt: NOW,
    updatedAt: NOW,
  });
  assert.equal(backend.quotas.get("profileOwner1/20260607/profile_edits").count, 1);

  const payload = normalizeProfilePayload(validRequest().data.payload);
  assert.equal(
    backend.dedupe.get(`profileOwner1/profile_edits/${profileDedupeKey("profileOwner1", payload)}`).targetPath,
    "/creator_profiles/profileOwner1",
  );
});

test("identical public profile returns duplicate before quota reservation", async () => {
  const backend = new FakeProfileBackend();
  backend.profiles.set("profileOwner1", {
    profileUid: "profileOwner1",
    displayName: "Aura Creator",
    bio: "Neon walls and alert tones.",
    websiteUrl: "https://example.com/aura",
    avatarUrl: "https://example.com/avatar.jpg",
    createdAt: NOW - 60_000,
    updatedAt: NOW - 60_000,
  });

  const result = await updateCreatorProfileHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(result.targetPath, "/creator_profiles/profileOwner1");
  assert.equal(backend.quotas.size, 0);
});

test("active normalized-profile dedupe returns duplicate without writing profile", async () => {
  const backend = new FakeProfileBackend();
  const payload = normalizeProfilePayload(validRequest().data.payload);
  backend.dedupe.set(
    `profileOwner1/profile_edits/${profileDedupeKey("profileOwner1", payload)}`,
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/creator_profiles/profileOwner1",
      ttlMillis: 5_000,
    }),
  );

  const result = await updateCreatorProfileHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(result.targetPath, "/creator_profiles/profileOwner1");
  assert.equal(backend.profiles.size, 0);
});

test("cooldown and daily-limit quota rejections do not commit profile changes", async () => {
  const cooldownBackend = new FakeProfileBackend();
  cooldownBackend.quotas.set("profileOwner1/20260607/profile_edits", {
    count: 1,
    lastAt: NOW - 60_000,
  });
  await assert.rejects(
    () => updateCreatorProfileHandler(validRequest(), cooldownBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "cooldown");
      assert.equal(error.details.retryAfterMillis, 240_000);
      return true;
    },
  );
  assert.equal(cooldownBackend.profiles.size, 0);

  const limitBackend = new FakeProfileBackend(Date.UTC(2026, 5, 7, 23, 59, 58));
  limitBackend.quotas.set("profileOwner1/20260607/profile_edits", {
    count: 12,
    lastAt: limitBackend.now - 300_000,
  });
  await assert.rejects(
    () => updateCreatorProfileHandler(validRequest(), limitBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "daily-limit");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
  assert.equal(limitBackend.profiles.size, 0);
});

test("callable identity requires Firebase Auth and App Check", async () => {
  const backend = new FakeProfileBackend();
  await assert.rejects(
    () => updateCreatorProfileHandler({ ...validRequest(), auth: undefined }, backend),
    { code: "unauthenticated" },
  );
  await assert.rejects(
    () => updateCreatorProfileHandler({ ...validRequest(), app: undefined }, backend),
    { code: "failed-precondition" },
  );
});

test("profile payload normalizes public copy and rejects client-owned fields", () => {
  const payload = normalizeProfilePayload(validRequest({
    displayName: "  Creator\u0000Name  ",
    bio: "Line one\nline two",
    websiteUrl: "",
    avatarUrl: "https://example.com/avatar.jpg",
  }).data.payload);

  assert.deepEqual(payload, {
    displayName: "Creator Name",
    bio: "Line one line two",
    websiteUrl: "",
    avatarUrl: "https://example.com/avatar.jpg",
  });
  assert.match(profileDedupeKey("profileOwner1", payload), /^profile_[a-f0-9]{64}$/);
  assert.equal(
    profileDedupeKey("profileOwner1", payload),
    profileDedupeKey("profileOwner1", normalizeProfilePayload({
      displayName: "Creator   Name",
      bio: "Line one line two",
      avatarUrl: "https://example.com/avatar.jpg",
    })),
  );

  assert.throws(
    () => normalizeProfilePayload(validRequest({ profileUid: "other" }).data.payload),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeProfilePayload(validRequest({ updatedAt: NOW }).data.payload),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeProfilePayload(validRequest({ displayName: " " }).data.payload),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeProfilePayload(validRequest({ websiteUrl: "http://example.com" }).data.payload),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeProfilePayload(validRequest({ avatarUrl: 123 }).data.payload),
    { code: "invalid-argument" },
  );
});
