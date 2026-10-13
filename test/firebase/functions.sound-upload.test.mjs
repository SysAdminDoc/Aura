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
const BUCKET = `${PROJECT_ID}.appspot.com`;
const SOUND_BYTES = 48_213;
const DOWNLOAD_TOKEN = 'emulator-sound-token';
const EMULATOR_HOST = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
const OBJECT_PATH = `/v0/b/${BUCKET}/o/${encodeURIComponent(STORAGE_PATH)}?alt=media&token=${DOWNLOAD_TOKEN}`;
// The client sends HTTPS; the finalizer republishes the emulator's own http origin.
const DOWNLOAD_URL = `https://${EMULATOR_HOST}${OBJECT_PATH}`;
const PUBLISHED_URL = `http://${EMULATOR_HOST}${OBJECT_PATH}`;
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');
const { getStorage } = requireFromFunctions('firebase-admin/storage');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST && process.env.FIREBASE_STORAGE_EMULATOR_HOST,
    'functions sound upload emulator test must run under firebase emulators:exec --only database,storage',
  );
  app = getApps()[0] ?? initializeApp({
    projectId: PROJECT_ID,
    databaseURL: `https://${PROJECT_ID}.firebaseio.com`,
    storageBucket: `${PROJECT_ID}.appspot.com`,
  });
});

// The finalizer attests the uploaded object, download token included, before publishing metadata.
async function saveSound() {
  await getStorage(app).bucket().file(STORAGE_PATH).save(Buffer.alloc(SOUND_BYTES, 1), {
    contentType: 'audio/mpeg',
    metadata: { metadata: { firebaseStorageDownloadTokens: DOWNLOAD_TOKEN } },
    resumable: false,
  });
}

beforeEach(async () => {
  await getDatabase(app).ref().set(null);
  await saveSound();
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
        fileSize: SOUND_BYTES,
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
  assert.equal(sound.downloadUrl, PUBLISHED_URL);
  assert.equal(sound.storagePath, STORAGE_PATH);
  assert.equal(sound.fileType, 'audio/mpeg');
  assert.equal(sound.fileSize, SOUND_BYTES);
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

test('sound upload is refused when the storage object is missing', async () => {
  await getStorage(app).bucket().file(STORAGE_PATH).delete();

  await assert.rejects(
    () => finalizeCommunitySoundUploadHandler(validRequest()),
    { code: 'failed-precondition' },
  );
  assert.equal(await readValue('community_sounds'), null);
});

function withOperationId(request, operationId) {
  return { ...request, data: { ...request.data, operationId } };
}

async function soundUploadLedger() {
  const days = await readValue(`community_write_quotas/${OWNER_UID}`);
  return Object.values(days)[0].sound_uploads;
}

test('an accepted sound upload settles its quota reservation', async () => {
  await finalizeCommunitySoundUploadHandler(withOperationId(validRequest(), 'settle-op-1'));

  const ledger = await soundUploadLedger();
  assert.equal(ledger.count, 1);
  assert.equal(typeof ledger.lastAt, 'number');
  assert.equal(ledger.pending, undefined);
});

test('a refused sound upload gives back its quota unit so the retry goes straight through', async () => {
  await getStorage(app).bucket().file(STORAGE_PATH).delete();
  await assert.rejects(
    () => finalizeCommunitySoundUploadHandler(withOperationId(validRequest(), 'refused-op-1')),
    { code: 'failed-precondition' },
  );

  const refunded = await soundUploadLedger();
  assert.equal(refunded.count, 0);
  assert.equal(refunded.lastAt, undefined);
  assert.equal(refunded.pending, undefined);
  assert.equal(refunded.releasedCount, 1);

  // Sound uploads have a 15-minute cooldown; the refused attempt no longer starts one.
  await saveSound();
  const retry = await finalizeCommunitySoundUploadHandler(withOperationId(validRequest(), 'refused-op-2'));
  assert.equal(retry.status, 'accepted');
  const spent = await soundUploadLedger();
  assert.equal(spent.count, 1);
  assert.equal(spent.pending, undefined);
});

test('sound upload is refused when the stored size differs from the declared size', async () => {
  await assert.rejects(
    () => finalizeCommunitySoundUploadHandler(validRequest({ fileSize: SOUND_BYTES - 1 })),
    (error) => {
      assert.equal(error.code, 'failed-precondition');
      assert.equal(error.details.reason, 'size-mismatch');
      return true;
    },
  );
  assert.equal(await readValue('community_sounds'), null);
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
