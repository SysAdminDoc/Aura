import { after, before, beforeEach, test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';

import { setCommunityUserBlockHandler } from '../../functions/lib/blockHandler.js';

const PROJECT_ID = 'aura-rules-test';
const BLOCKER_UID = 'blocker-emulator';
const BLOCKED_UID = 'blocked.emulator';
const BLOCKED_KEY = 'blocked_emulator';
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST,
    'functions block emulator test must run under firebase emulators:exec --only database',
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
    auth: { uid: BLOCKER_UID },
    app: { appId: 'aura-emulator-test' },
    data: {
      operationId: `block-emulator-${Date.now()}`,
      clientSentAt: Date.now() - 1_000,
      payload: {
        blockedUid: BLOCKED_UID,
        blocked: true,
        reason: 'spam',
        ...overrides,
      },
    },
  };
}

async function readValue(path) {
  return (await getDatabase(app).ref(path).get()).val();
}

test('block callable handler writes private, reverse, quota, and dedupe rows in the database emulator', async () => {
  const result = await setCommunityUserBlockHandler(validRequest());

  assert.equal(result.status, 'accepted');
  assert.equal(result.blocked, true);
  assert.equal(result.targetPath, `/community_user_blocks/${BLOCKER_UID}/${BLOCKED_KEY}`);

  const privateRow = await readValue(`community_user_blocks/${BLOCKER_UID}/${BLOCKED_KEY}`);
  assert.equal(privateRow.blockerUid, BLOCKER_UID);
  assert.equal(privateRow.blockedUid, BLOCKED_KEY);
  assert.equal(privateRow.reason, 'SPAM');
  assert.equal(typeof privateRow.createdAt, 'number');

  const reverseRow = await readValue(`community_blocked_by/${BLOCKED_KEY}/${BLOCKER_UID}`);
  assert.deepEqual(reverseRow, privateRow);

  const quotas = await readValue(`community_write_quotas/${BLOCKER_UID}`);
  const quotaDays = Object.keys(quotas);
  assert.equal(quotaDays.length, 1);
  assert.equal(quotas[quotaDays[0]].user_blocks.count, 1);
  assert.equal(typeof quotas[quotaDays[0]].user_blocks.lastAt, 'number');

  const dedupe = await readValue(`community_write_dedupe/${BLOCKER_UID}/user_blocks`);
  assert.equal(dedupe[`${BLOCKED_KEY}_block`].targetPath, `/community_user_blocks/${BLOCKER_UID}/${BLOCKED_KEY}`);
});

test('unblock callable handler removes private and reverse rows with a separate emulator dedupe marker', async () => {
  const row = {
    blockerUid: BLOCKER_UID,
    blockedUid: BLOCKED_KEY,
    createdAt: Date.now() - 10_000,
    reason: 'OTHER',
  };
  await getDatabase(app).ref().update({
    [`community_user_blocks/${BLOCKER_UID}/${BLOCKED_KEY}`]: row,
    [`community_blocked_by/${BLOCKED_KEY}/${BLOCKER_UID}`]: row,
  });

  const result = await setCommunityUserBlockHandler(validRequest({ blocked: false }));

  assert.equal(result.status, 'accepted');
  assert.equal(result.blocked, false);
  assert.equal(await readValue(`community_user_blocks/${BLOCKER_UID}/${BLOCKED_KEY}`), null);
  assert.equal(await readValue(`community_blocked_by/${BLOCKED_KEY}/${BLOCKER_UID}`), null);

  const quotas = await readValue(`community_write_quotas/${BLOCKER_UID}`);
  const quotaDay = Object.keys(quotas)[0];
  assert.equal(quotas[quotaDay].user_blocks.count, 1);

  const dedupe = await readValue(`community_write_dedupe/${BLOCKER_UID}/user_blocks`);
  assert.equal(dedupe[`${BLOCKED_KEY}_unblock`].targetPath, `/community_user_blocks/${BLOCKER_UID}/${BLOCKED_KEY}`);
});

test('missing unblock is idempotent before mutating emulator quota', async () => {
  const result = await setCommunityUserBlockHandler(validRequest({ blocked: false }));

  assert.equal(result.status, 'duplicate');
  assert.equal(result.blocked, false);
  assert.equal(result.targetPath, `/community_user_blocks/${BLOCKER_UID}/${BLOCKED_KEY}`);
  assert.equal(await readValue(`community_write_quotas/${BLOCKER_UID}`), null);
});
