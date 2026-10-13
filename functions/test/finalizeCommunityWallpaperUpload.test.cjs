const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt, settledQuotaState } = require("../lib/quotaEngine.js");
const {
  buildWallpaperMetadata,
  finalizeCommunityWallpaperUploadHandler,
  normalizeWallpaperUploadPayload,
  wallpaperUploadDedupeKey,
} = require("../lib/wallpaperUploadHandler.js");

// These cases pin the production download host.
delete process.env.FIREBASE_STORAGE_EMULATOR_HOST;

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);
const MAX_WALLPAPER_BYTES = 4 * 1024 * 1024;
const HEAD_BYTES = 256 * 1024;
const BUCKET = "aura-test.appspot.com";
const STORAGE_PATH = "wallpapers/wallOwner1/1700000000000_night_grid.jpg";
const WALL_BYTES = 410_000;
// Same object and token as the published URL, spelled the way a client might: lowercase
// escapes and the query in another order.
const CLIENT_URL =
  `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/wallpapers%2fwallOwner1%2f1700000000000_night_grid.jpg` +
  "?token=tok-wall-1&alt=media";
const PUBLISHED_URL =
  `https://firebasestorage.googleapis.com/v0/b/${BUCKET}/o/wallpapers%2FwallOwner1%2F1700000000000_night_grid.jpg` +
  "?alt=media&token=tok-wall-1";

// SOI, a JFIF APP0 segment, then a baseline SOF0 frame header for a 3-component image.
function jpegHead(width, height) {
  return Uint8Array.from([
    0xff, 0xd8,
    0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
    0xff, 0xc0, 0x00, 0x11, 0x08, height >> 8, height & 0xff, width >> 8, width & 0xff,
    0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
  ]);
}

function validRequest(overrides = {}) {
  return {
    auth: { uid: "wallOwner1" },
    app: { appId: "aura-test-app" },
    data: {
      operationId: "wall-upload-op-1",
      clientSentAt: NOW - 1_000,
      payload: {
        name: " Night   Grid ",
        category: " AMOLED ",
        tags: [" Dark ", "DARK", "lock-screen!!!", "minimal"],
        colors: ["#778899", "#112233", "#778899"],
        thumbnailUrl: CLIENT_URL,
        fullUrl: CLIENT_URL,
        downloadUrl: CLIENT_URL,
        storagePath: STORAGE_PATH,
        width: 1080,
        height: 1920,
        fileSize: WALL_BYTES,
        fileType: "image/jpeg",
        originalFileName: " Night Grid.png ",
        uploaderLabel: " Wall Owner ",
        license: "cc0 1.0",
        rightsAttested: true,
        sourceUrl: "https://example.com/source",
        ...overrides,
      },
    },
  };
}

function storedWallpaper(overrides = {}) {
  return {
    exists: true,
    bucket: BUCKET,
    contentType: "image/jpeg",
    size: WALL_BYTES,
    downloadTokens: ["tok-old", "tok-wall-1"],
    ...overrides,
  };
}

class FakeWallpaperUploadBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.nextId = "wallA";
    this.dedupe = new Map();
    this.quotas = new Map();
    this.settlements = [];
    this.commitFailure = null;
    this.wallpapers = new Map();
    this.ownerUploads = new Map();
    this.storageObject = storedWallpaper();
    this.head = jpegHead(1080, 1920);
    this.verifiedPaths = [];
    this.headReads = [];
  }

  async verifyStorageObject(storagePath) {
    this.verifiedPaths.push(storagePath);
    return this.storageObject;
  }

  async readStorageObjectHead(storagePath, maxBytes) {
    this.headReads.push({ storagePath, maxBytes });
    return this.head === null ? null : this.head.subarray(0, maxBytes);
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

  async commitWallpaperUpload(input) {
    if (this.commitFailure) throw this.commitFailure;
    const metadataPath = `/community_wallpapers/${input.uploadId}`;
    const ownerIndex = {
      uploadId: input.uploadId,
      publicId: `cw_${input.uploadId}`,
      contentType: "WALLPAPER",
      metadataPath,
      storagePath: input.storedObject.storagePath,
      title: input.payload.name,
      createdAt: input.uploadedAt,
    };
    this.wallpapers.set(input.uploadId, buildWallpaperMetadata(input));
    this.ownerUploads.set(`${input.payload.uploaderKey}/wallpapers/${input.uploadId}`, ownerIndex);
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
}

// Resolves with the refusal and checks nothing was published.
async function refuse(backend, request = validRequest()) {
  const error = await finalizeCommunityWallpaperUploadHandler(request, backend).then(
    () => assert.fail("expected the wallpaper upload to be refused"),
    (caught) => caught,
  );
  assert.equal(backend.wallpapers.size, 0);
  assert.equal(backend.ownerUploads.size, 0);
  return error;
}

async function refuseForStorage(backend, reason, request = validRequest()) {
  const error = await refuse(backend, request);
  assert.equal(error.code, "failed-precondition");
  assert.equal(error.details.reason, reason);
  assert.equal(error.details.operationId, "wall-upload-op-1");
  return error;
}

test("accepted wallpaper upload writes public metadata, owner index, quota, and dedupe", async () => {
  const backend = new FakeWallpaperUploadBackend();
  const result = await finalizeCommunityWallpaperUploadHandler(validRequest(), backend);

  assert.deepEqual(result, {
    operationId: "wall-upload-op-1",
    uploadId: "wallA",
    publicId: "cw_wallA",
    status: "accepted",
    targetPath: "/community_wallpapers/wallA",
    ownerIndexPath: "/owner_uploads/wallOwner1/wallpapers/wallA",
    serverTimeMillis: NOW,
  });
  assert.deepEqual(backend.wallpapers.get("wallA"), {
    name: "Night Grid",
    category: "amoled",
    tags: ["dark", "lock-screen", "minimal"],
    colors: ["#778899", "#112233"],
    thumbnailUrl: PUBLISHED_URL,
    fullUrl: PUBLISHED_URL,
    downloadUrl: PUBLISHED_URL,
    storagePath: STORAGE_PATH,
    width: 1080,
    height: 1920,
    fileSize: WALL_BYTES,
    fileType: "image/jpeg",
    uploadedAt: NOW,
    uploaderId: "wallOwner1",
    uploaderUid: "wallOwner1",
    uploaderLabel: "Wall Owner",
    license: "CC0",
    rightsAttested: true,
    rightsAttestedAt: NOW,
    sourceUrl: "https://example.com/source",
    votes: 0,
  });
  assert.notEqual(backend.wallpapers.get("wallA").downloadUrl, CLIENT_URL);
  assert.equal(Object.hasOwn(backend.wallpapers.get("wallA"), "originalFileName"), false);
  assert.deepEqual(backend.ownerUploads.get("wallOwner1/wallpapers/wallA"), {
    uploadId: "wallA",
    publicId: "cw_wallA",
    contentType: "WALLPAPER",
    metadataPath: "/community_wallpapers/wallA",
    storagePath: STORAGE_PATH,
    title: "Night Grid",
    createdAt: NOW,
  });
  assert.deepEqual(backend.verifiedPaths, [STORAGE_PATH]);
  assert.deepEqual(backend.headReads, [{ storagePath: STORAGE_PATH, maxBytes: HEAD_BYTES }]);
  assert.equal(backend.quotas.get("wallOwner1/20260607/wallpaper_uploads").count, 1);

  const payload = normalizeWallpaperUploadPayload(validRequest().data.payload, "wallOwner1");
  assert.equal(
    backend.dedupe.get(`wallOwner1/wallpaper_uploads/${wallpaperUploadDedupeKey(payload)}`).targetPath,
    "/community_wallpapers/wallA",
  );
});

test("client thumbnail and full URLs are replaced by the attested object URL", async () => {
  const backend = new FakeWallpaperUploadBackend();
  const request = validRequest({
    thumbnailUrl: "https://cdn.example.com/thumb.jpg",
    fullUrl: "https://cdn.example.com/full.jpg",
  });
  assert.equal((await finalizeCommunityWallpaperUploadHandler(request, backend)).status, "accepted");

  const row = backend.wallpapers.get("wallA");
  assert.equal(row.thumbnailUrl, PUBLISHED_URL);
  assert.equal(row.fullUrl, PUBLISHED_URL);
  assert.equal(row.downloadUrl, PUBLISHED_URL);
});

test("active storage-path dedupe returns duplicate without creating another upload", async () => {
  const backend = new FakeWallpaperUploadBackend();
  const payload = normalizeWallpaperUploadPayload(validRequest().data.payload, "wallOwner1");
  backend.dedupe.set(
    `wallOwner1/wallpaper_uploads/${wallpaperUploadDedupeKey(payload)}`,
    buildDedupeMarker({
      nowMillis: NOW - 1_000,
      targetPath: "/community_wallpapers/existingWall",
      ttlMillis: 5_000,
    }),
  );

  const result = await finalizeCommunityWallpaperUploadHandler(validRequest(), backend);

  assert.equal(result.status, "duplicate");
  assert.equal(result.targetPath, "/community_wallpapers/existingWall");
  assert.equal(backend.wallpapers.size, 0);
  assert.equal(backend.ownerUploads.size, 0);
});

test("cooldown and daily-limit quota rejections do not commit wallpaper metadata", async () => {
  const cooldownBackend = new FakeWallpaperUploadBackend();
  cooldownBackend.quotas.set("wallOwner1/20260607/wallpaper_uploads", {
    count: 1,
    lastAt: NOW - 60_000,
  });
  await assert.rejects(
    () => finalizeCommunityWallpaperUploadHandler(validRequest(), cooldownBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "cooldown");
      assert.equal(error.details.retryAfterMillis, 540_000);
      return true;
    },
  );
  assert.equal(cooldownBackend.wallpapers.size, 0);

  const limitBackend = new FakeWallpaperUploadBackend(Date.UTC(2026, 5, 7, 23, 59, 58));
  limitBackend.quotas.set("wallOwner1/20260607/wallpaper_uploads", {
    count: 5,
    lastAt: limitBackend.now - 600_000,
  });
  await assert.rejects(
    () => finalizeCommunityWallpaperUploadHandler(validRequest(), limitBackend),
    (error) => {
      assert.equal(error.code, "resource-exhausted");
      assert.equal(error.details.reason, "daily-limit");
      assert.equal(error.details.retryAfterMillis, 2_000);
      return true;
    },
  );
  assert.equal(limitBackend.wallpapers.size, 0);
});

test("wallpaper finalize fails closed on a missing object or unavailable metadata", async () => {
  const missing = new FakeWallpaperUploadBackend();
  missing.storageObject = { exists: false };
  await refuseForStorage(missing, "object-missing");
  assert.deepEqual(missing.headReads, []);

  for (const field of ["bucket", "contentType", "size", "downloadTokens"]) {
    const backend = new FakeWallpaperUploadBackend();
    backend.storageObject = storedWallpaper({ [field]: undefined });
    const error = await refuseForStorage(backend, "metadata-unavailable");
    assert.deepEqual(error.details.missing, [field]);
  }
});

test("wallpaper finalize refuses another owner's storage path before reading Storage", async () => {
  const backend = new FakeWallpaperUploadBackend();
  const error = await refuse(backend, validRequest({ storagePath: "wallpapers/otherOwner/1700000000000_night_grid.jpg" }));
  assert.equal(error.code, "invalid-argument");
  assert.equal(error.details.field, "storagePath");
  assert.deepEqual(backend.verifiedPaths, []);
});

test("wallpaper finalize refuses a download URL that doesn't point at the stored object", async () => {
  for (const downloadUrl of [
    CLIENT_URL.replace("firebasestorage.googleapis.com", "storage.example.com"),
    CLIENT_URL.replace(BUCKET, "other-bucket.appspot.com"),
    CLIENT_URL.replace("1700000000000_night_grid.jpg", "other.jpg"),
  ]) {
    await refuseForStorage(new FakeWallpaperUploadBackend(), "download-url-mismatch", validRequest({ downloadUrl }));
  }

  await refuseForStorage(
    new FakeWallpaperUploadBackend(),
    "download-token-unknown",
    validRequest({ downloadUrl: CLIENT_URL.replace("tok-wall-1", "tok-forged") }),
  );
});

test("wallpaper finalize requires the stored size and MIME type to match exactly", async () => {
  const smaller = new FakeWallpaperUploadBackend();
  smaller.storageObject = storedWallpaper({ size: WALL_BYTES - 1 });
  await refuseForStorage(smaller, "size-mismatch");

  const overLimit = new FakeWallpaperUploadBackend();
  overLimit.storageObject = storedWallpaper({ size: MAX_WALLPAPER_BYTES + 1 });
  await refuseForStorage(overLimit, "size-out-of-range");

  for (const contentType of ["image/png", "image/jpg", "audio/mpeg"]) {
    const backend = new FakeWallpaperUploadBackend();
    backend.storageObject = storedWallpaper({ contentType });
    const error = await refuseForStorage(backend, "content-type-mismatch");
    assert.equal(error.details.declared, "image/jpeg");
    assert.deepEqual(backend.headReads, []);
  }
});

test("wallpaper finalize publishes the stored JPEG's own dimensions and refuses others", async () => {
  for (const [width, height] of [[1080, 1919], [1920, 1080]]) {
    const backend = new FakeWallpaperUploadBackend();
    backend.head = jpegHead(width, height);
    const error = await refuseForStorage(backend, "dimensions-mismatch");
    assert.deepEqual(error.details.declared, { width: 1080, height: 1920 });
    assert.deepEqual(error.details.actual, { width, height });
    assert.deepEqual(backend.settlements, ["released"]);
  }

  const small = new FakeWallpaperUploadBackend();
  small.storageObject = storedWallpaper({ size: 1_000 });
  small.head = jpegHead(720, 1280);
  const request = validRequest({ fileSize: 1_000, width: 720, height: 1280 });
  assert.equal((await finalizeCommunityWallpaperUploadHandler(request, small)).status, "accepted");
  assert.deepEqual(small.headReads, [{ storagePath: STORAGE_PATH, maxBytes: 1_000 }]);
  assert.equal(small.wallpapers.get("wallA").width, 720);
  assert.equal(small.wallpapers.get("wallA").height, 1280);
  assert.equal(small.wallpapers.get("wallA").fileSize, 1_000);
});

test("wallpaper finalize fails closed when the stored bytes aren't a readable JPEG", async () => {
  const pngSignature = Uint8Array.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x0d]);
  for (const head of [jpegHead(1080, 1920).subarray(0, 25), pngSignature, new Uint8Array(0), null]) {
    const backend = new FakeWallpaperUploadBackend();
    backend.head = head;
    await refuseForStorage(backend, "jpeg-unreadable");
  }
});

test("callable identity requires Firebase Auth and App Check", async () => {
  const backend = new FakeWallpaperUploadBackend();
  await assert.rejects(
    () => finalizeCommunityWallpaperUploadHandler({ ...validRequest(), auth: undefined }, backend),
    { code: "unauthenticated" },
  );
  await assert.rejects(
    () => finalizeCommunityWallpaperUploadHandler({ ...validRequest(), app: undefined }, backend),
    { code: "failed-precondition" },
  );
});

test("wallpaper upload payload normalizes fields and rejects invalid metadata", () => {
  const payload = normalizeWallpaperUploadPayload(
    validRequest({
      tags: [" A ", "a", "wallpaper!!!"],
      colors: ["#aabbcc", "#AABBCC", "#112233"],
    }).data.payload,
    "wallOwner1",
  );

  assert.equal(payload.name, "Night Grid");
  assert.equal(payload.category, "amoled");
  assert.deepEqual(payload.tags, ["a", "wallpaper"]);
  assert.deepEqual(payload.colors, ["#AABBCC", "#112233"]);
  assert.equal(payload.fileType, "image/jpeg");
  assert.equal(payload.license, "CC0");
  assert.equal(payload.uploaderKey, "wallOwner1");
  assert.match(wallpaperUploadDedupeKey(payload), /^sp_[a-f0-9]{64}$/);

  assert.throws(
    () => normalizeWallpaperUploadPayload(validRequest({ uploaderUid: "other" }).data.payload, "wallOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeWallpaperUploadPayload(
      validRequest({ storagePath: "wallpapers/otherOwner/wall.jpg" }).data.payload,
      "wallOwner1",
    ),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeWallpaperUploadPayload(validRequest({ fileType: "image/png" }).data.payload, "wallOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeWallpaperUploadPayload(validRequest({ width: 0 }).data.payload, "wallOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeWallpaperUploadPayload(validRequest({ fileSize: 5 * 1024 * 1024 }).data.payload, "wallOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeWallpaperUploadPayload(validRequest({ thumbnailUrl: "http://example.com/wall.jpg" }).data.payload, "wallOwner1"),
    { code: "invalid-argument" },
  );
  assert.throws(
    () => normalizeWallpaperUploadPayload(validRequest({ colors: ["not-hex"] }).data.payload, "wallOwner1"),
    { code: "invalid-argument" },
  );
});

test("a wallpaper upload whose write fails refunds its quota unit and cooldown", async () => {
  const backend = new FakeWallpaperUploadBackend();
  backend.commitFailure = new Error("database unavailable");
  await assert.rejects(() => finalizeCommunityWallpaperUploadHandler(validRequest(), backend), /database unavailable/);

  const refunded = backend.quotas.get("wallOwner1/20260607/wallpaper_uploads");
  assert.deepEqual(backend.settlements, ["released"]);
  assert.equal(refunded.count, 0);
  assert.equal(refunded.lastAt, undefined);
  assert.equal(refunded.pending, undefined);

  // The person's retry is a new call, and it goes through inside the old cooldown.
  backend.commitFailure = null;
  backend.now = NOW + 1;
  const retry = validRequest();
  const result = await finalizeCommunityWallpaperUploadHandler({ ...retry, data: { ...retry.data, operationId: "retry-op" } }, backend);
  assert.equal(result.status, "accepted");
  assert.deepEqual(backend.settlements, ["released", "finalized"]);
  const spent = backend.quotas.get("wallOwner1/20260607/wallpaper_uploads");
  assert.equal(spent.count, 1);
  assert.equal(spent.pending, undefined);
});

test("a wallpaper upload refused for its stored object refunds its quota unit", async () => {
  const backend = new FakeWallpaperUploadBackend();
  backend.storageObject = { exists: false };
  await assert.rejects(() => finalizeCommunityWallpaperUploadHandler(validRequest(), backend), { code: "failed-precondition" });

  assert.deepEqual(backend.settlements, ["released"]);
  assert.equal(backend.quotas.get("wallOwner1/20260607/wallpaper_uploads").count, 0);
});
