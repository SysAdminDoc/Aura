import { after, before, beforeEach, test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';

import { updateCreatorProfileHandler } from '../../functions/lib/profileHandler.js';

const PROJECT_ID = 'aura-rules-test';
const PROFILE_UID = 'profile-owner';
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST,
    'functions profile emulator test must run under firebase emulators:exec --only database',
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
    auth: { uid: PROFILE_UID },
    app: { appId: 'aura-emulator-test' },
    data: {
      operationId: `profile-emulator-${Date.now()}`,
      clientSentAt: Date.now() - 1_000,
      payload: {
        displayName: ' Profile   Owner ',
        bio: 'Emulator backed profile update.',
        websiteUrl: 'https://example.com/profile',
        avatarUrl: 'https://example.com/avatar.jpg',
        ...overrides,
      },
    },
  };
}

async function readValue(path) {
  return (await getDatabase(app).ref(path).get()).val();
}

test('profile callable handler writes profile, quota, and dedupe rows in the database emulator', async () => {
  const result = await updateCreatorProfileHandler(validRequest());

  assert.equal(result.status, 'accepted');
  assert.equal(result.targetPath, `/creator_profiles/${PROFILE_UID}`);

  const profile = await readValue(`creator_profiles/${PROFILE_UID}`);
  assert.equal(profile.profileUid, PROFILE_UID);
  assert.equal(profile.displayName, 'Profile Owner');
  assert.equal(profile.bio, 'Emulator backed profile update.');
  assert.equal(profile.websiteUrl, 'https://example.com/profile');
  assert.equal(profile.avatarUrl, 'https://example.com/avatar.jpg');
  assert.equal(typeof profile.createdAt, 'number');
  assert.equal(typeof profile.updatedAt, 'number');

  const quotas = await readValue(`community_write_quotas/${PROFILE_UID}`);
  const quotaDays = Object.keys(quotas);
  assert.equal(quotaDays.length, 1);
  assert.equal(quotas[quotaDays[0]].profile_edits.count, 1);
  assert.equal(typeof quotas[quotaDays[0]].profile_edits.lastAt, 'number');

  const dedupe = await readValue(`community_write_dedupe/${PROFILE_UID}/profile_edits`);
  const dedupeKeys = Object.keys(dedupe);
  assert.equal(dedupeKeys.length, 1);
  assert.match(dedupeKeys[0], /^profile_[a-f0-9]{64}$/);
  assert.equal(dedupe[dedupeKeys[0]].targetPath, `/creator_profiles/${PROFILE_UID}`);
});

test('unchanged profile update is idempotent before mutating emulator quota', async () => {
  const first = await updateCreatorProfileHandler(validRequest({ displayName: 'Same Name' }));
  const second = await updateCreatorProfileHandler(validRequest({ displayName: 'Same Name' }));

  assert.equal(first.status, 'accepted');
  assert.equal(second.status, 'duplicate');

  const quotas = await readValue(`community_write_quotas/${PROFILE_UID}`);
  const quotaDay = Object.keys(quotas)[0];
  assert.equal(quotas[quotaDay].profile_edits.count, 1);
});
