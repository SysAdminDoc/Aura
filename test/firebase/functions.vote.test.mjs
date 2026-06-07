import { after, before, beforeEach, test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';

import { recordCommunityVoteHandler } from '../../functions/lib/voteHandler.js';

const PROJECT_ID = 'aura-rules-test';
const VOTER_UID = 'voter-emulator';
const CONTENT_ID = 'WALLPAPER::COMMUNITY::cw_vote_target';
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST,
    'functions vote emulator test must run under firebase emulators:exec --only database',
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
    auth: { uid: VOTER_UID },
    app: { appId: 'aura-emulator-test' },
    data: {
      operationId: `vote-emulator-${Date.now()}`,
      clientSentAt: Date.now() - 1_000,
      payload: {
        contentId: CONTENT_ID,
        ...overrides,
      },
    },
  };
}

async function readValue(path) {
  return (await getDatabase(app).ref(path).get()).val();
}

test('vote callable handler writes vote, legacy voter, quota, and dedupe rows in the database emulator', async () => {
  const result = await recordCommunityVoteHandler(validRequest());

  assert.equal(result.status, 'accepted');
  assert.equal(result.upvotes, 1);
  assert.equal(result.targetPath, `/votes/${CONTENT_ID}`);

  const vote = await readValue(`votes/${CONTENT_ID}`);
  assert.equal(vote.upvotes, 1);
  assert.equal(vote.voters[VOTER_UID], true);

  const legacyVoter = await readValue(`voters/${CONTENT_ID}/${VOTER_UID}`);
  assert.equal(legacyVoter, true);

  const quotas = await readValue(`community_write_quotas/${VOTER_UID}`);
  const quotaDays = Object.keys(quotas);
  assert.equal(quotaDays.length, 1);
  assert.equal(quotas[quotaDays[0]].votes.count, 1);
  assert.equal(typeof quotas[quotaDays[0]].votes.lastAt, 'number');

  const dedupe = await readValue(`community_write_dedupe/${VOTER_UID}/votes`);
  const dedupeKeys = Object.keys(dedupe);
  assert.deepEqual(dedupeKeys, [CONTENT_ID]);
  assert.equal(dedupe[CONTENT_ID].targetPath, `/votes/${CONTENT_ID}`);
});

test('repeat vote is idempotent through emulator voter markers before quota changes', async () => {
  const first = await recordCommunityVoteHandler(validRequest());
  const second = await recordCommunityVoteHandler(validRequest());

  assert.equal(first.status, 'accepted');
  assert.equal(second.status, 'duplicate');
  assert.equal(second.targetPath, `/votes/${CONTENT_ID}`);

  const vote = await readValue(`votes/${CONTENT_ID}`);
  assert.equal(vote.upvotes, 1);
  assert.equal(vote.voters[VOTER_UID], true);

  const quotas = await readValue(`community_write_quotas/${VOTER_UID}`);
  const quotaDay = Object.keys(quotas)[0];
  assert.equal(quotas[quotaDay].votes.count, 1);
});
