const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt } = require("../lib/quotaEngine.js");
const {
  finalizeCommunitySoundUploadHandler,
  normalizeSoundUploadPayload,
  soundUploadDedupeKey,
} = require("../lib/soundUploadHandler.js");

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);

function validRequest(overrides = {}) {
  return {
    auth: { uid: "soundOwner1" },
    app: { appId: "aura-test-app" },
    data: {
      operationId: "sound-upload-op-1",
      clientSentAt: NOW - 1_000,
      payload: {
        name: " Soft   Bell ",
        category: " Notification ",
        tags: [" Calm ", "CALM", "bell!!!", "lo-fi"],
        downloadUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/sounds%2FsoundOwner1%2Fbell.mp3",
        storagePath: "sounds/soundOwner1/1700000000000_soft_bell.mp3",
        fileType: "audio/mpeg",
        originalFileName: " Soft Bell.mp3 ",
        uploaderLabel: " Sound Owner ",
        license: "cc-by",
        rightsAttested: true,
        sourceUrl: "https://example.com/source",
        ...overrides,
      },
    },
  };
}

class FakeSoundUploadBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.nextId = "soundA";
    this.dedupe = new Map();
    this.quotas = new Map();
    this.sounds = new Map();
    this.ownerUploads = new Map();
  }

  nowMillis() {
    return this.now;
  }

  async createUploadId() {
    return this.nextId;
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

  async commitSoundUpload(input) {
    const metadataPath = `/community_sounds/${input.uploadId}`;
    const publicRow = {
      name: input.payload.name,
      category: input.payload.category,
      tags: input.payload.tags,
      downloadUrl: input.payload.downloadUrl,
      storagePath: input.payload.storagePath,
      fileType: input.payload.fileType,
      uploadedAt: input.uploadedAt,
      uploaderId: input.uid,
      uploaderUid: input.uid,
      uploaderLabel: input.payload.uploaderLabel,
      license: input.payload.license,
      rightsAttested: true,
      rightsAttestedAt: input.uploadedAt,
      sourceUrl: input.payload.sourceUrl,
      votes: 0,
    };
    const ownerIndex = {
      uploadId: input.uploadId,
      publicId: `cu_${input.uploadId}`,
      contentType: "SOUND",
      metadataPath,
      storagePath: input.payload.storagePath,
      title: input.payload.name,
      createdAt: input.uploadedAt,
    };
    this.sounds.set(input.uploadId, publicRow);
    this.ownerUploads.set(`${input.payload.uploaderKey}/sounds/${input.uploadId}`, ownerIndex);
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
}

test("accepted sound upload writes public metadata, owner index, quota, and dedupe", async () => {
  const backend = new FakeSoundUploadBackend();
  const result = await finalizeCommunitySoundUploadHandler(validRequest(), backend);

  assert.deepEqual(result, {
    operationId: "sound-upload-op-1",
    uploadId: "soundA",
    publicId: "cu_soundA",
    status: "accepted",
    targetPath: "/community_sounds/soundA",
    ownerIndexPath: "/owner_uploads/soundOwner1/sounds/soundA",
    serverTimeMillis: NOW,
  });
  assert.deepEqual(backend.sounds.get("soundA"), {
    name: "Soft Bell",
    category: "notification",
    tags: ["calm", "bell", "lo-fi"],
    downloadUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/sounds%2FsoundOwner1%2Fbell.mp3",
    storagePath: "sounds/soundOwner1/1700000000000_soft_bell.mp3",
    fileType: "audio/mpeg",
    uploadedAt: NOW,
    uploaderId: "soundOwner1",
    uploaderUid: "soundOwner1",
    uploaderLabel: "Sound Owner",
    license: "CC BY",
    rightsAttested: true,
    rightsAttestedAt: NOW,
    sourceUrl: "https://example.com/source",
    votes: 0,
  });
  assert.equal(Object.hasOwn(backend.sounds.get("soundA"), "originalFileName"), false);
  assert.deepEqual(backend.ownerUploads.get("soundOwner1/sounds/soundA"), {
    uploadId: "soundA",
    publicId: "cu_soundA",
    contentType: "SOUND",
    metadataPath: "/community_sounds/soundA",
    storagePath: "sounds/soundOwner1/1700000000000_soft_bell.mp3",
    title: "Soft Bell",
    createdAt: NOW,
  });
  assert.equal(backend.quotas.get("soundOwner1/20260607/sound_uploads").count, 1);

  const payload = normalizeSoundUploadPayload(validRequest().data.payload, "soundOwner1");
  assert.equal(
    backend.dedupe.get(`soundOwner1/sound_uploads/${soundUploadDedupeKey(payload)}`).targetPath,
    "/community_sounds/soundA",
  );
});

test("active storage-path dedupe returns duplicate without creating another upload", async () => {
  const backend = new FakeSoundUploadBackend();
  const payload = normalizeSoundUploadPayload(validRequest().data.payload, "soundOwner1");
  backend.dedupe.set(
    `soundOwner1/sound_uploads/${soundUploadDedupeKey(payload)}`,
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/community_sounds/existingSound",
      ttlMillis: 5_000,
    }),
  );

  const result = await finalizeCommunitySoundUploadHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(result.targetPath, "/community_sounds/existingSound");
  assert.equal(backend.sounds.size, 0);
  assert.equal(backend.ownerUploads.size, 0);
});

test("cooldown and daily-limit quota rejections do not commit sound metadata", async () => {
  const cooldownBackend = new FakeSoundUploadBackend();
  cooldownBackend.quotas.set("soundOwner1/20260607/sound_uploads", {
    count: 1,
    lastAt: NOW - 60_000,
  });
  await assert.rejects(
    () => finalizeCommunitySoundUploadHandler(validRequest(), cooldownBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "cooldown");
      assert.equal(error.details.retryAfterMillis, 840_000);
      return true;
    },
  );
  assert.equal(cooldownBackend.sounds.size, 0);

  const limitBackend = new FakeSoundUploadBackend(Date.UTC(2026, 5, 7, 23, 59, 58));
  limitBackend.quotas.set("soundOwner1/20260607/sound_uploads", {
    count: 3,
    lastAt: limitBackend.now - 900_000,
  });
  await assert.rejects(
    () => finalizeCommunitySoundUploadHandler(validRequest(), limitBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "daily-limit");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
  assert.equal(limitBackend.sounds.size, 0);
});

test("callable identity requires Firebase Auth and App Check", async () => {
  const backend = new FakeSoundUploadBackend();
  await assert.rejects(
    () => finalizeCommunitySoundUploadHandler({ ...validRequest(), auth: undefined }, backend),
    { code: "unauthenticated" },
  );
  await assert.rejects(
    () => finalizeCommunitySoundUploadHandler({ ...validRequest(), app: undefined }, backend),
    { code: "failed-precondition" },
  );
});

test("sound upload payload normalizes fields and rejects ownership overrides", () => {
  const payload = normalizeSoundUploadPayload(
    validRequest({
      tags: [" A ", "a", "tone!!!"],
    }).data.payload,
    "soundOwner1",
  );

  assert.equal(payload.name, "Soft Bell");
  assert.equal(payload.category, "notification");
  assert.deepEqual(payload.tags, ["a", "tone"]);
  assert.equal(payload.license, "CC BY");
  assert.equal(payload.uploaderKey, "soundOwner1");
  assert.match(soundUploadDedupeKey(payload), /^sp_[a-f0-9]{64}$/);

  assert.throws(
    () => normalizeSoundUploadPayload(validRequest({ uploaderUid: "other" }).data.payload, "soundOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeSoundUploadPayload(
      validRequest({ storagePath: "sounds/otherOwner/clip.mp3" }).data.payload,
      "soundOwner1",
    ),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeSoundUploadPayload(validRequest({ downloadUrl: "http://example.com/clip.mp3" }).data.payload, "soundOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeSoundUploadPayload(validRequest({ rightsAttested: false }).data.payload, "soundOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeSoundUploadPayload(validRequest({ tags: ["ok", 12] }).data.payload, "soundOwner1"),
    { code: "invalid-argument" },
  );
});
