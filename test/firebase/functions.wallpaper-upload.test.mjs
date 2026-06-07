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
const WALLPAPER_URL = 'https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwnerEmulator%2Fnight_grid.jpg';
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST,
    'functions wallpaper upload emulator test must run under firebase emulators:exec --only database',
  );
  app = getApps()[0] ?? initializeApp({
    projectId: PROJECT_ID,
    databaseURL: `https://${PROJECT_ID}.firebaseio.com`,
  });
});

beforeEach(async () => {
  await getDatabase(app).ref().set(null);
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
        fileSize: 410_000,
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
  assert.equal(wallpaper.thumbnailUrl, WALLPAPER_URL);
  assert.equal(wallpaper.fullUrl, WALLPAPER_URL);
  assert.equal(wallpaper.downloadUrl, WALLPAPER_URL);
  assert.equal(wallpaper.storagePath, STORAGE_PATH);
  assert.equal(wallpaper.width, 1080);
  assert.equal(wallpaper.height, 1920);
  assert.equal(wallpaper.fileSize, 410_000);
  assert.equal(wallpaper.fileType, 'image/jpeg');
  assert.equal(wallpaper.originalFileName, 'Night Grid.jpg');
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
