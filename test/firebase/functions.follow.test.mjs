import { after, before, beforeEach, test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';

import { setCreatorFollowHandler } from '../../functions/lib/followHandler.js';

const PROJECT_ID = 'aura-rules-test';
const FOLLOWER_UID = 'follower-emulator';
const CREATOR_ID = 'creator.emulator';
const CREATOR_KEY = 'creator_emulator';
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST,
    'functions follow emulator test must run under firebase emulators:exec --only database',
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
    auth: { uid: FOLLOWER_UID },
    app: { appId: 'aura-emulator-test' },
    data: {
      operationId: `follow-emulator-${Date.now()}`,
      clientSentAt: Date.now() - 1_000,
      payload: {
        creatorId: CREATOR_ID,
        label: ' Creator   Emulator ',
        following: true,
        ...overrides,
      },
    },
  };
}

async function readValue(path) {
  return (await getDatabase(app).ref(path).get()).val();
}

test('follow callable handler writes follow, quota, and dedupe rows in the database emulator', async () => {
  const result = await setCreatorFollowHandler(validRequest());

  assert.equal(result.status, 'accepted');
  assert.equal(result.following, true);
  assert.equal(result.targetPath, `/creator_follows/${FOLLOWER_UID}/${CREATOR_KEY}`);

  const follow = await readValue(`creator_follows/${FOLLOWER_UID}/${CREATOR_KEY}`);
  assert.equal(follow.creatorId, CREATOR_ID);
  assert.equal(follow.label, 'Creator Emulator');
  assert.equal(typeof follow.followedAt, 'number');

  const quotas = await readValue(`community_write_quotas/${FOLLOWER_UID}`);
  const quotaDays = Object.keys(quotas);
  assert.equal(quotaDays.length, 1);
  assert.equal(quotas[quotaDays[0]].follows.count, 1);
  assert.equal(typeof quotas[quotaDays[0]].follows.lastAt, 'number');

  const dedupe = await readValue(`community_write_dedupe/${FOLLOWER_UID}/follows`);
  assert.equal(dedupe[`${CREATOR_KEY}_follow`].targetPath, `/creator_follows/${FOLLOWER_UID}/${CREATOR_KEY}`);
});

test('unfollow callable handler removes follow row with a separate emulator dedupe marker', async () => {
  await getDatabase(app)
    .ref(`creator_follows/${FOLLOWER_UID}/${CREATOR_KEY}`)
    .set({
      creatorId: CREATOR_ID,
      label: 'Creator Emulator',
      followedAt: Date.now() - 10_000,
    });

  const result = await setCreatorFollowHandler(validRequest({ following: false }));

  assert.equal(result.status, 'accepted');
  assert.equal(result.following, false);
  assert.equal(await readValue(`creator_follows/${FOLLOWER_UID}/${CREATOR_KEY}`), null);

  const quotas = await readValue(`community_write_quotas/${FOLLOWER_UID}`);
  const quotaDay = Object.keys(quotas)[0];
  assert.equal(quotas[quotaDay].follows.count, 1);

  const dedupe = await readValue(`community_write_dedupe/${FOLLOWER_UID}/follows`);
  assert.equal(dedupe[`${CREATOR_KEY}_unfollow`].targetPath, `/creator_follows/${FOLLOWER_UID}/${CREATOR_KEY}`);
});

test('missing unfollow is idempotent before mutating emulator quota', async () => {
  const result = await setCreatorFollowHandler(validRequest({ following: false }));

  assert.equal(result.status, 'duplicate');
  assert.equal(result.following, false);
  assert.equal(result.targetPath, `/creator_follows/${FOLLOWER_UID}/${CREATOR_KEY}`);
  assert.equal(await readValue(`community_write_quotas/${FOLLOWER_UID}`), null);
});
