import { after, before, beforeEach, test } from 'node:test';
import { strict as assert } from 'node:assert';
import { createRequire } from 'node:module';

import { submitCommunityReportHandler } from '../../functions/lib/reportHandler.js';

const PROJECT_ID = 'aura-rules-test';
const REPORTER_UID = 'reporter-emulator';
const requireFromFunctions = createRequire(new URL('../../functions/package.json', import.meta.url));
const { getApps, initializeApp, deleteApp } = requireFromFunctions('firebase-admin/app');
const { getDatabase } = requireFromFunctions('firebase-admin/database');

let app;

before(async () => {
  assert.ok(
    process.env.FIREBASE_DATABASE_EMULATOR_HOST,
    'functions report emulator test must run under firebase emulators:exec --only database',
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
    auth: { uid: REPORTER_UID },
    app: { appId: 'aura-emulator-test' },
    data: {
      operationId: `report-emulator-${Date.now()}`,
      clientSentAt: Date.now() - 1_000,
      payload: {
        contentId: 'SOUND::COMMUNITY::cu_reported_sound',
        contentType: 'SOUND',
        contentSource: 'COMMUNITY',
        reason: 'RIGHTS',
        note: 'Possible rights issue.',
        sourceUrl: 'https://example.com/source',
        license: 'CC0',
        uploaderName: 'Uploader',
        uploaderUid: 'uploader-emulator',
        ...overrides,
      },
    },
  };
}

async function readValue(path) {
  return (await getDatabase(app).ref(path).get()).val();
}

test('report callable handler writes report, quota, and dedupe rows in the database emulator', async () => {
  const result = await submitCommunityReportHandler(validRequest());

  assert.equal(result.status, 'accepted');
  assert.match(result.reportId, /^-/);
  assert.equal(result.targetPath, `/community_reports/${result.reportId}`);

  const report = await readValue(`community_reports/${result.reportId}`);
  assert.equal(report.reporterUid, REPORTER_UID);
  assert.equal(report.contentId, 'SOUND::COMMUNITY::cu_reported_sound');
  assert.equal(report.contentKey, 'SOUND::COMMUNITY::cu_reported_sound');
  assert.equal(report.contentType, 'SOUND');
  assert.equal(report.contentSource, 'COMMUNITY');
  assert.equal(report.reason, 'RIGHTS');
  assert.equal(report.status, 'OPEN');
  assert.equal(report.sourceUrl, 'https://example.com/source');
  assert.equal(typeof report.reportedAt, 'number');

  const quotas = await readValue(`community_write_quotas/${REPORTER_UID}`);
  const quotaDays = Object.keys(quotas);
  assert.equal(quotaDays.length, 1);
  assert.equal(quotas[quotaDays[0]].reports.count, 1);

  const dedupe = await readValue(`community_write_dedupe/${REPORTER_UID}/reports`);
  const dedupeKeys = Object.keys(dedupe);
  assert.equal(dedupeKeys.length, 1);
  assert.equal(
    dedupe[dedupeKeys[0]].targetPath,
    `/community_reports/${result.reportId}`,
  );
});

test('same content and reason report is idempotent through emulator dedupe', async () => {
  const first = await submitCommunityReportHandler(validRequest({ note: 'First note' }));
  const second = await submitCommunityReportHandler(validRequest({ note: 'Second note' }));

  assert.equal(first.status, 'accepted');
  assert.equal(second.status, 'duplicate');
  assert.equal(second.targetPath, `/community_reports/${first.reportId}`);

  const reports = await readValue('community_reports');
  assert.equal(Object.keys(reports).length, 1);

  const quotas = await readValue(`community_write_quotas/${REPORTER_UID}`);
  const quotaDay = Object.keys(quotas)[0];
  assert.equal(quotas[quotaDay].reports.count, 1);
});
