import { after, before, beforeEach, test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';

import {
  finalizeCommunitySoundUploadHandler,
  normalizeSoundUploadPayload,
  soundUploadDedupeKey,
} from '../../functions/lib/soundUploadHandler.js';

const PROJECT_ID = 'aura-rules-test';
const OWNER_UID = 'soundOwnerEmulator';
const STORAGE_PATH = `sounds/${OWNER_UID}/1700000000000_soft_bell.mp3`;
const DOWNLOAD_URL = 'https://firebasestorage.googleapis.com/v0/b/aura/o/sounds%2FsoundOwnerEmulator%2Fsoft_bell.mp3';
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST,
    'functions sound upload emulator test must run under firebase emulators:exec --only database',
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
      operationId: `sound-upload-emulator-${Date.now()}`,
      clientSentAt: Date.now() - 1_000,
      payload: {
        name: ' Soft   Bell ',
        category: ' Notification ',
        tags: [' Calm ', 'CALM', 'bell!!!', 'lo-fi'],
        downloadUrl: DOWNLOAD_URL,
        storagePath: STORAGE_PATH,
        fileType: 'audio/mpeg',
        originalFileName: ' Soft Bell.mp3 ',
        uploaderLabel: ' Sound Owner ',
        license: 'cc-by',
        rightsAttested: true,
        sourceUrl: 'https://example.com/sound-source',
        ...overrides,
      },
    },
  };
}

async function readValue(path) {
  return (await getDatabase(app).ref(path).get()).val();
}

test('sound upload callable handler writes metadata, owner index, quota, and dedupe rows in the database emulator', async () => {
  const result = await finalizeCommunitySoundUploadHandler(validRequest());

  assert.equal(result.status, 'accepted');
  assert.match(result.uploadId, /^-/);
  assert.equal(result.publicId, `cu_${result.uploadId}`);
  assert.equal(result.targetPath, `/community_sounds/${result.uploadId}`);
  assert.equal(result.ownerIndexPath, `/owner_uploads/${OWNER_UID}/sounds/${result.uploadId}`);

  const sound = await readValue(`community_sounds/${result.uploadId}`);
  assert.equal(sound.name, 'Soft Bell');
  assert.equal(sound.category, 'notification');
  assert.deepEqual(sound.tags, ['calm', 'bell', 'lo-fi']);
  assert.equal(sound.downloadUrl, DOWNLOAD_URL);
  assert.equal(sound.storagePath, STORAGE_PATH);
  assert.equal(sound.fileType, 'audio/mpeg');
  assert.equal(Object.hasOwn(sound, 'originalFileName'), false);
  assert.equal(sound.uploaderId, OWNER_UID);
  assert.equal(sound.uploaderUid, OWNER_UID);
  assert.equal(sound.uploaderLabel, 'Sound Owner');
  assert.equal(sound.license, 'CC BY');
  assert.equal(sound.rightsAttested, true);
  assert.equal(sound.rightsAttestedAt, sound.uploadedAt);
  assert.equal(sound.sourceUrl, 'https://example.com/sound-source');
  assert.equal(sound.votes, 0);
  assert.equal(typeof sound.uploadedAt, 'number');

  const ownerIndex = await readValue(`owner_uploads/${OWNER_UID}/sounds/${result.uploadId}`);
  assert.deepEqual(ownerIndex, {
    uploadId: result.uploadId,
    publicId: `cu_${result.uploadId}`,
    contentType: 'SOUND',
    metadataPath: `/community_sounds/${result.uploadId}`,
    storagePath: STORAGE_PATH,
    title: 'Soft Bell',
    createdAt: sound.uploadedAt,
  });

  const quotas = await readValue(`community_write_quotas/${OWNER_UID}`);
  const quotaDays = Object.keys(quotas);
  assert.equal(quotaDays.length, 1);
  assert.equal(quotas[quotaDays[0]].sound_uploads.count, 1);
  assert.equal(typeof quotas[quotaDays[0]].sound_uploads.lastAt, 'number');

  const payload = normalizeSoundUploadPayload(validRequest().data.payload, OWNER_UID);
  const dedupeKey = soundUploadDedupeKey(payload);
  const dedupe = await readValue(`community_write_dedupe/${OWNER_UID}/sound_uploads/${dedupeKey}`);
  assert.equal(dedupe.targetPath, `/community_sounds/${result.uploadId}`);
});

test('same storage path sound upload is idempotent through emulator dedupe', async () => {
  const first = await finalizeCommunitySoundUploadHandler(validRequest());
  const second = await finalizeCommunitySoundUploadHandler(validRequest({ name: 'Duplicate Bell' }));

  assert.equal(first.status, 'accepted');
  assert.equal(second.status, 'duplicate');
  assert.equal(second.targetPath, `/community_sounds/${first.uploadId}`);

  const sounds = await readValue('community_sounds');
  assert.equal(Object.keys(sounds).length, 1);

  const quotas = await readValue(`community_write_quotas/${OWNER_UID}`);
  const quotaDay = Object.keys(quotas)[0];
  assert.equal(quotas[quotaDay].sound_uploads.count, 1);
});
