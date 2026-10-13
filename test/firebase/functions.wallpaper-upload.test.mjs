import { after, before, beforeEach, test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';

import {
  finalizeCommunityWallpaperUploadHandler,
  normalizeWallpaperUploadPayload,
  wallpaperUploadDedupeKey,
} from '../../functions/lib/wallpaperUploadHandler.js';

const PROJECT_ID = 'aura-rules-test';
const OWNER_UID = 'wallOwnerEmulator';
const STORAGE_PATH = `wallpapers/${OWNER_UID}/1700000000000_night_grid.jpg`;
const BUCKET = `${PROJECT_ID}.appspot.com`;
const WALL_BYTES = 410_000;
const DOWNLOAD_TOKEN = 'emulator-wallpaper-token';
const EMULATOR_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
const OBJECT_PATH = `/v0/b/${BUCKET}/o/${encodeURIComponent(STORAGE_PATH)}?alt=media&token=${DOWNLOAD_TOKEN}`;
// The client sends HTTPS; the finalizer republishes the emulator's own http origin.
const WALLPAPER_URL = `https://${EMULATOR_HOST}${OBJECT_PATH}`;
const PUBLISHED_URL = `http://${EMULATOR_HOST}${OBJECT_PATH}`;
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');
const { getStorage } = requireFromFunctions('firebase-admin/storage');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST && process.env.FIREBASE_STORAGE_EMULATOR_HOST,
    'functions wallpaper upload emulator test must run under firebase emulators:exec --only database,storage',
  );
  app = getApps()[0] ?? initializeApp({
    projectId: PROJECT_ID,
    databaseURL: `https://${PROJECT_ID}.firebaseio.com`,
    storageBucket: `${PROJECT_ID}.appspot.com`,
  });
});

// SOI, a JFIF APP0 segment and a baseline SOF0 frame header, padded out to the requested size.
function jpegBytes(size, width = 1080, height = 1920) {
  const header = Buffer.from([
    0xff, 0xd8,
    0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
    0xff, 0xc0, 0x00, 0x11, 0x08, height >> 8, height & 0xff, width >> 8, width & 0xff,
    0x03, 0x01, 0x22, 0x00, 0x02, 0x11, 0x01, 0x03, 0x11, 0x01,
  ]);
  return Buffer.concat([header, Buffer.alloc(size - header.length, 0)]);
}

// The finalizer attests the uploaded object, download token and JPEG frame header included.
async function saveWallpaper(bytes) {
  await getStorage(app).bucket().file(STORAGE_PATH).save(bytes, {
    contentType: 'image/jpeg',
    metadata: { metadata: { firebaseStorageDownloadTokens: DOWNLOAD_TOKEN } },
    resumable: false,
  });
}

beforeEach(async () => {
  await getDatabase(app).ref().set(null);
  await saveWallpaper(jpegBytes(WALL_BYTES));
});

after(async () => {
  if (app) {
    await deleteApp(app);
  }
});

function validRequest(overrides = {}) {
  return {
    auth: { uid: OWNER_UID },
    app: { appId: 'aura-emulator-test' },
    data: {
      operationId: `wallpaper-upload-emulator-${Date.now()}`,
      clientSentAt: Date.now() - 1_000,
      payload: {
        name: ' Night   Grid ',
        category: ' AMOLED ',
        tags: [' Dark ', 'DARK', 'lock-screen!!!', 'minimal'],
        colors: ['#778899', '#112233', '#778899'],
        thumbnailUrl: WALLPAPER_URL,
        fullUrl: WALLPAPER_URL,
        downloadUrl: WALLPAPER_URL,
        storagePath: STORAGE_PATH,
        width: 1080,
        height: 1920,
        fileSize: WALL_BYTES,
        fileType: 'image/jpeg',
        originalFileName: ' Night Grid.jpg ',
        uploaderLabel: ' Wall Owner ',
        license: 'cc0 1.0',
        rightsAttested: true,
        sourceUrl: 'https://example.com/wallpaper-source',
        ...overrides,
      },
    },
  };
}

async function readValue(path) {
  return (await getDatabase(app).ref(path).get()).val();
}

test('wallpaper upload callable handler writes metadata, owner index, quota, and dedupe rows in the database emulator', async () => {
  const result = await finalizeCommunityWallpaperUploadHandler(validRequest());

  assert.equal(result.status, 'accepted');
  assert.match(result.uploadId, /^-/);
  assert.equal(result.publicId, `cw_${result.uploadId}`);
  assert.equal(result.targetPath, `/community_wallpapers/${result.uploadId}`);
  assert.equal(result.ownerIndexPath, `/owner_uploads/${OWNER_UID}/wallpapers/${result.uploadId}`);

  const wallpaper = await readValue(`community_wallpapers/${result.uploadId}`);
  assert.equal(wallpaper.name, 'Night Grid');
  assert.equal(wallpaper.category, 'amoled');
  assert.deepEqual(wallpaper.tags, ['dark', 'lock-screen', 'minimal']);
  assert.deepEqual(wallpaper.colors, ['#778899', '#112233']);
  assert.equal(wallpaper.thumbnailUrl, PUBLISHED_URL);
  assert.equal(wallpaper.fullUrl, PUBLISHED_URL);
  assert.equal(wallpaper.downloadUrl, PUBLISHED_URL);
  assert.equal(wallpaper.storagePath, STORAGE_PATH);
  assert.equal(wallpaper.width, 1080);
  assert.equal(wallpaper.height, 1920);
  assert.equal(wallpaper.fileSize, WALL_BYTES);
  assert.equal(wallpaper.fileType, 'image/jpeg');
  assert.equal(Object.hasOwn(wallpaper, 'originalFileName'), false);
  assert.equal(wallpaper.uploaderId, OWNER_UID);
  assert.equal(wallpaper.uploaderUid, OWNER_UID);
  assert.equal(wallpaper.uploaderLabel, 'Wall Owner');
  assert.equal(wallpaper.license, 'CC0');
  assert.equal(wallpaper.rightsAttested, true);
  assert.equal(wallpaper.rightsAttestedAt, wallpaper.uploadedAt);
  assert.equal(wallpaper.sourceUrl, 'https://example.com/wallpaper-source');
  assert.equal(wallpaper.votes, 0);
  assert.equal(typeof wallpaper.uploadedAt, 'number');

  const ownerIndex = await readValue(`owner_uploads/${OWNER_UID}/wallpapers/${result.uploadId}`);
  assert.deepEqual(ownerIndex, {
    uploadId: result.uploadId,
    publicId: `cw_${result.uploadId}`,
    contentType: 'WALLPAPER',
    metadataPath: `/community_wallpapers/${result.uploadId}`,
    storagePath: STORAGE_PATH,
    title: 'Night Grid',
    createdAt: wallpaper.uploadedAt,
  });

  const quotas = await readValue(`community_write_quotas/${OWNER_UID}`);
  const quotaDays = Object.keys(quotas);
  assert.equal(quotaDays.length, 1);
  assert.equal(quotas[quotaDays[0]].wallpaper_uploads.count, 1);
  assert.equal(typeof quotas[quotaDays[0]].wallpaper_uploads.lastAt, 'number');

  const payload = normalizeWallpaperUploadPayload(validRequest().data.payload, OWNER_UID);
  const dedupeKey = wallpaperUploadDedupeKey(payload);
  const dedupe = await readValue(`community_write_dedupe/${OWNER_UID}/wallpaper_uploads/${dedupeKey}`);
  assert.equal(dedupe.targetPath, `/community_wallpapers/${result.uploadId}`);
});

test('wallpaper upload is refused when the stored object size differs from the declared size', async () => {
  await saveWallpaper(jpegBytes(1_000));

  await assert.rejects(
    () => finalizeCommunityWallpaperUploadHandler(validRequest()),
    (error) => {
      assert.equal(error.code, 'failed-precondition');
      assert.equal(error.details.reason, 'size-mismatch');
      return true;
    },
  );
  assert.equal(await readValue('community_wallpapers'), null);
});

test('wallpaper upload is refused when the stored JPEG has other dimensions', async () => {
  await saveWallpaper(jpegBytes(WALL_BYTES, 1080, 1919));

  await assert.rejects(
    () => finalizeCommunityWallpaperUploadHandler(validRequest()),
    (error) => {
      assert.equal(error.code, 'failed-precondition');
      assert.equal(error.details.reason, 'dimensions-mismatch');
      return true;
    },
  );
  assert.equal(await readValue('community_wallpapers'), null);
});

test('same storage path wallpaper upload is idempotent through emulator dedupe', async () => {
  const first = await finalizeCommunityWallpaperUploadHandler(validRequest());
  const second = await finalizeCommunityWallpaperUploadHandler(validRequest({ name: 'Duplicate Grid' }));

  assert.equal(first.status, 'accepted');
  assert.equal(second.status, 'duplicate');
  assert.equal(second.targetPath, `/community_wallpapers/${first.uploadId}`);

  const wallpapers = await readValue('community_wallpapers');
  assert.equal(Object.keys(wallpapers).length, 1);

  const quotas = await readValue(`community_write_quotas/${OWNER_UID}`);
  const quotaDay = Object.keys(quotas)[0];
  assert.equal(quotas[quotaDay].wallpaper_uploads.count, 1);
});
