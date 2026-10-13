const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt, settledQuotaState } = require("../lib/quotaEngine.js");
const {
  buildSoundMetadata,
  finalizeCommunitySoundUploadHandler,
  normalizeSoundUploadPayload,
  soundUploadDedupeKey,
} = require("../lib/soundUploadHandler.js");

// These cases pin the production download host.
delete process.env.FIREBASE_STORAGE_EMULATOR_HOST;

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);
const MAX_SOUND_BYTES = 20 * 1024 * 1024;
const BUCKET = "aura-test.appspot.com";
const STORAGE_PATH = "sounds/soundOwner1/1700000000000_soft_bell.mp3";
const SOUND_BYTES = 48_213;
// Same object and token as the published URL, spelled the way a client might: lowercase
// escapes and the query in another order.
const CLIENT_URL =
  `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/sounds%2fsoundOwner1%2f1700000000000_soft_bell.mp3` +
  "?token=tok-sound-1&alt=media";
const PUBLISHED_URL =
  `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/sounds%2FsoundOwner1%2F1700000000000_soft_bell.mp3` +
  "?alt=media&token=tok-sound-1";

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
        downloadUrl: CLIENT_URL,
        storagePath: STORAGE_PATH,
        fileType: "audio/mpeg",
        fileSize: SOUND_BYTES,
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

function storedSound(overrides = {}) {
  return {
    exists: true,
    bucket: BUCKET,
    contentType: "audio/mpeg",
    size: SOUND_BYTES,
    downloadTokens: ["tok-old", "tok-sound-1"],
    ...overrides,
  };
}

class FakeSoundUploadBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.nextId = "soundA";
    this.dedupe = new Map();
    this.quotas = new Map();
    this.settlements = [];
    this.commitFailure = null;
    this.sounds = new Map();
    this.ownerUploads = new Map();
    this.storageObject = storedSound();
    this.verifiedPaths = [];
  }

  async verifyStorageObject(storagePath) {
    this.verifiedPaths.push(storagePath);
    return this.storageObject;
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

  async reserveQuota(uid, dayKey, surface, nowMillis, dedupe, operationKey) {
    const key = `${uid}/${dayKey}/${surface.surfaceKey}`;
    const decision = evaluateCommunityQuotaAttempt({
      surface,
      nowMillis,
      quota: this.quotas.get(key) ?? {},
      dedupe,
      operationKey,
    });
    if (decision.status !== "duplicate") {
      this.quotas.set(key, decision.quota);
    }
    return decision;
  }

  async settleQuota(uid, dayKey, surface, reservation, settlement) {
    this.settlements.push(settlement);
    const key = `${uid}/${dayKey}/${surface.surfaceKey}`;
    const next = settledQuotaState(this.quotas.get(key) ?? {}, reservation, settlement, this.now);
    if (next !== null) this.quotas.set(key, next);
  }

  async commitSoundUpload(input) {
    if (this.commitFailure) throw this.commitFailure;
    const metadataPath = `/community_sounds/${input.uploadId}`;
    const ownerIndex = {
      uploadId: input.uploadId,
      publicId: `cu_${input.uploadId}`,
      contentType: "SOUND",
      metadataPath,
      storagePath: input.storedObject.storagePath,
      title: input.payload.name,
      createdAt: input.uploadedAt,
    };
    this.sounds.set(input.uploadId, buildSoundMetadata(input));
    this.ownerUploads.set(`${input.payload.uploaderKey}/sounds/${input.uploadId}`, ownerIndex);
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
}

// Resolves with the refusal and checks nothing was published.
async function refuse(backend, request = validRequest()) {
  const error = await finalizeCommunitySoundUploadHandler(request, backend).then(
    () => assert.fail("expected the sound upload to be refused"),
    (caught) => caught,
  );
  assert.equal(backend.sounds.size, 0);
  assert.equal(backend.ownerUploads.size, 0);
  return error;
}

async function refuseForStorage(backend, reason, request = validRequest()) {
  const error = await refuse(backend, request);
  assert.equal(error.code, "failed-precondition");
  assert.equal(error.details.reason, reason);
  assert.equal(error.details.operationId, "sound-upload-op-1");
  return error;
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
    downloadUrl: PUBLISHED_URL,
    storagePath: STORAGE_PATH,
    fileType: "audio/mpeg",
    fileSize: SOUND_BYTES,
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
  assert.notEqual(backend.sounds.get("soundA").downloadUrl, CLIENT_URL);
  assert.equal(Object.hasOwn(backend.sounds.get("soundA"), "originalFileName"), false);
  assert.deepEqual(backend.ownerUploads.get("soundOwner1/sounds/soundA"), {
    uploadId: "soundA",
    publicId: "cu_soundA",
    contentType: "SOUND",
    metadataPath: "/community_sounds/soundA",
    storagePath: STORAGE_PATH,
    title: "Soft Bell",
    createdAt: NOW,
  });
  assert.deepEqual(backend.verifiedPaths, [STORAGE_PATH]);
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

test("sound finalize fails closed on a missing object or unavailable metadata", async () => {
  const missing = new FakeSoundUploadBackend();
  missing.storageObject = { exists: false };
  await refuseForStorage(missing, "object-missing");

  for (const field of ["bucket", "contentType", "size", "downloadTokens"]) {
    const backend = new FakeSoundUploadBackend();
    backend.storageObject = storedSound({ [field]: undefined });
    const error = await refuseForStorage(backend, "metadata-unavailable");
    assert.deepEqual(error.details.missing, [field]);
  }

  const noTokens = new FakeSoundUploadBackend();
  noTokens.storageObject = storedSound({ downloadTokens: [] });
  await refuseForStorage(noTokens, "metadata-unavailable");
});

test("sound finalize refuses another owner's storage path before reading Storage", async () => {
  const backend = new FakeSoundUploadBackend();
  const error = await refuse(backend, validRequest({ storagePath: "sounds/otherOwner/1700000000000_soft_bell.mp3" }));
  assert.equal(error.code, "invalid-argument");
  assert.equal(error.details.field, "storagePath");
  assert.deepEqual(backend.verifiedPaths, []);
});

test("sound finalize refuses a download URL that doesn't point at the stored object", async () => {
  for (const downloadUrl of [
    CLIENT_URL.replace("firebasestorage.googleapis.com", "storage.example.com"),
    CLIENT_URL.replace(BUCKET, "other-bucket.appspot.com"),
    CLIENT_URL.replace("1700000000000_soft_bell.mp3", "other.mp3"),
  ]) {
    await refuseForStorage(new FakeSoundUploadBackend(), "download-url-mismatch", validRequest({ downloadUrl }));
  }

  await refuseForStorage(
    new FakeSoundUploadBackend(),
    "download-token-unknown",
    validRequest({ downloadUrl: CLIENT_URL.replace("tok-sound-1", "tok-forged") }),
  );
});

test("sound finalize requires the stored size to equal the declared size within the limit", async () => {
  const mismatch = new FakeSoundUploadBackend();
  mismatch.storageObject = storedSound({ size: SOUND_BYTES + 1 });
  const error = await refuseForStorage(mismatch, "size-mismatch");
  assert.equal(error.details.declared, SOUND_BYTES);
  assert.equal(error.details.actual, SOUND_BYTES + 1);

  for (const size of [0, MAX_SOUND_BYTES + 1]) {
    const backend = new FakeSoundUploadBackend();
    backend.storageObject = storedSound({ size });
    await refuseForStorage(backend, "size-out-of-range");
  }

  for (const fileSize of [undefined, 0, -1, 1.5, "48213", MAX_SOUND_BYTES + 1]) {
    const backend = new FakeSoundUploadBackend();
    const invalid = await refuse(backend, validRequest({ fileSize }));
    assert.equal(invalid.code, "invalid-argument");
    assert.equal(invalid.details.field, "fileSize");
    assert.deepEqual(backend.verifiedPaths, []);
  }

  const atLimit = new FakeSoundUploadBackend();
  atLimit.storageObject = storedSound({ size: MAX_SOUND_BYTES });
  const accepted = await finalizeCommunitySoundUploadHandler(validRequest({ fileSize: MAX_SOUND_BYTES }), atLimit);
  assert.equal(accepted.status, "accepted");
  assert.equal(atLimit.sounds.get("soundA").fileSize, MAX_SOUND_BYTES);
});

test("sound finalize requires the stored MIME type to be exactly the declared one", async () => {
  const ogg = new FakeSoundUploadBackend();
  ogg.storageObject = storedSound({ contentType: "audio/ogg" });
  const error = await refuseForStorage(ogg, "content-type-mismatch");
  assert.equal(error.details.declared, "audio/mpeg");
  assert.equal(error.details.actual, "audio/ogg");

  // An alias of the declared type, or something that isn't audio at all, is still a mismatch.
  for (const contentType of ["audio/mp3", "application/octet-stream"]) {
    const backend = new FakeSoundUploadBackend();
    backend.storageObject = storedSound({ contentType });
    await refuseForStorage(backend, "content-type-mismatch");
  }

  // Case and parameters don't change the type, and the row carries the normalized one.
  const withParams = new FakeSoundUploadBackend();
  withParams.storageObject = storedSound({ contentType: "Audio/MPEG; charset=binary" });
  assert.equal((await finalizeCommunitySoundUploadHandler(validRequest(), withParams)).status, "accepted");
  assert.equal(withParams.sounds.get("soundA").fileType, "audio/mpeg");
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
  assert.equal(payload.fileSize, SOUND_BYTES);
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

test("a sound upload whose write fails refunds its quota unit and cooldown", async () => {
  const backend = new FakeSoundUploadBackend();
  backend.commitFailure = new Error("database unavailable");
  await assert.rejects(() => finalizeCommunitySoundUploadHandler(validRequest(), backend), /database unavailable/);

  const refunded = backend.quotas.get("soundOwner1/20260607/sound_uploads");
  assert.deepEqual(backend.settlements, ["released"]);
  assert.equal(refunded.count, 0);
  assert.equal(refunded.lastAt, undefined);
  assert.equal(refunded.pending, undefined);

  // The person's retry is a new call, and it goes through inside the old cooldown.
  backend.commitFailure = null;
  backend.now = NOW + 1;
  const retry = validRequest();
  const result = await finalizeCommunitySoundUploadHandler({ ...retry, data: { ...retry.data, operationId: "retry-op" } }, backend);
  assert.equal(result.status, "accepted");
  assert.deepEqual(backend.settlements, ["released", "finalized"]);
  const spent = backend.quotas.get("soundOwner1/20260607/sound_uploads");
  assert.equal(spent.count, 1);
  assert.equal(spent.pending, undefined);
});

test("a sound upload refused for its stored object refunds its quota unit", async () => {
  const backend = new FakeSoundUploadBackend();
  backend.storageObject = { exists: false };
  await assert.rejects(() => finalizeCommunitySoundUploadHandler(validRequest(), backend), { code: "failed-precondition" });

  assert.deepEqual(backend.settlements, ["released"]);
  assert.equal(backend.quotas.get("soundOwner1/20260607/sound_uploads").count, 0);
});
