const assert = require("node:assert/strict");
const test = require("node:test");

const { buildDedupeMarker, evaluateCommunityQuotaAttempt } = require("../lib/quotaEngine.js");
const {
  finalizeCommunityWallpaperUploadHandler,
  normalizeWallpaperUploadPayload,
  wallpaperUploadDedupeKey,
} = require("../lib/wallpaperUploadHandler.js");

const NOW = Date.UTC(2026, 5, 7, 12, 0, 0);

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
        thumbnailUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fgrid.jpg",
        fullUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fgrid.jpg",
        downloadUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fgrid.jpg",
        storagePath: "wallpapers/wallOwner1/1700000000000_night_grid.jpg",
        width: 1080,
        height: 1920,
        fileSize: 410_000,
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

class FakeWallpaperUploadBackend {
  constructor(nowMillis = NOW) {
    this.now = nowMillis;
    this.nextId = "wallA";
    this.dedupe = new Map();
    this.quotas = new Map();
    this.wallpapers = new Map();
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

  async commitWallpaperUpload(input) {
    const metadataPath = `/community_wallpapers/${input.uploadId}`;
    const publicRow = {
      name: input.payload.name,
      category: input.payload.category,
      tags: input.payload.tags,
      colors: input.payload.colors,
      thumbnailUrl: input.payload.thumbnailUrl,
      fullUrl: input.payload.fullUrl,
      downloadUrl: input.payload.downloadUrl,
      storagePath: input.payload.storagePath,
      width: input.payload.width,
      height: input.payload.height,
      fileSize: input.payload.fileSize,
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
      publicId: `cw_${input.uploadId}`,
      contentType: "WALLPAPER",
      metadataPath,
      storagePath: input.payload.storagePath,
      title: input.payload.name,
      createdAt: input.uploadedAt,
    };
    this.wallpapers.set(input.uploadId, publicRow);
    this.ownerUploads.set(`${input.payload.uploaderKey}/wallpapers/${input.uploadId}`, ownerIndex);
    this.dedupe.set(`${input.uid}/${input.surfaceKey}/${input.dedupeKey}`, input.dedupeMarker);
  }
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
    thumbnailUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fgrid.jpg",
    fullUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fgrid.jpg",
    downloadUrl: "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fgrid.jpg",
    storagePath: "wallpapers/wallOwner1/1700000000000_night_grid.jpg",
    width: 1080,
    height: 1920,
    fileSize: 410_000,
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
  assert.equal(Object.hasOwn(backend.wallpapers.get("wallA"), "originalFileName"), false);
  assert.deepEqual(backend.ownerUploads.get("wallOwner1/wallpapers/wallA"), {
    uploadId: "wallA",
    publicId: "cw_wallA",
    contentType: "WALLPAPER",
    metadataPath: "/community_wallpapers/wallA",
    storagePath: "wallpapers/wallOwner1/1700000000000_night_grid.jpg",
    title: "Night Grid",
    createdAt: NOW,
  });
  assert.equal(backend.quotas.get("wallOwner1/20260607/wallpaper_uploads").count, 1);

  const payload = normalizeWallpaperUploadPayload(validRequest().data.payload, "wallOwner1");
  assert.equal(
    backend.dedupe.get(`wallOwner1/wallpaper_uploads/${wallpaperUploadDedupeKey(payload)}`).targetPath,
    "/community_wallpapers/wallA",
  );
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
