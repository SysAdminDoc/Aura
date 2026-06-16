import { after, before, beforeEach, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { strict as assert } from 'node:assert';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';

const PROJECT_ID = 'aura-rules-test';
const DAY_KEY = '20260606';
const MAX_COLLECTION_PAYLOAD_BYTES = 512 * 1024;

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    database: {
      rules: readFileSync('database.rules.json', 'utf8'),
    },
  });
});

beforeEach(async () => {
  await testEnv.clearDatabase();
});

after(async () => {
  await testEnv.cleanup();
});

function dbFor(uid, tokenOptions = {}) {
  return testEnv.authenticatedContext(uid, tokenOptions).database();
}

function adminDb() {
  return dbFor('rules-admin', { admin: true });
}

function unauthenticatedDb() {
  return testEnv.unauthenticatedContext().database();
}

function nowMs() {
  return Date.now() - 1000;
}

function soundMetadata(uid, overrides = {}) {
  return {
    name: 'Soft Bell',
    category: 'ringtone',
    tags: ['soft', 'alert'],
    downloadUrl: 'https://example.com/sounds/soft-bell.mp3',
    storagePath: `sounds/${uid}/soft-bell.mp3`,
    fileType: 'audio/mpeg',
    uploadedAt: nowMs(),
    uploaderId: uid,
    uploaderUid: uid,
    uploaderLabel: 'Uploader',
    license: 'CC0',
    rightsAttested: true,
    rightsAttestedAt: nowMs(),
    sourceUrl: '',
    votes: 0,
    ...overrides,
  };
}

function wallpaperMetadata(uid, overrides = {}) {
  return {
    name: 'Night Grid',
    category: 'abstract',
    tags: ['dark', 'minimal'],
    colors: ['#000000', '#FFFFFF'],
    thumbnailUrl: 'https://example.com/wallpapers/night-grid.jpg',
    fullUrl: 'https://example.com/wallpapers/night-grid.jpg',
    downloadUrl: 'https://example.com/wallpapers/night-grid.jpg',
    storagePath: `wallpapers/${uid}/night-grid.jpg`,
    width: 1440,
    height: 2560,
    fileSize: 123456,
    fileType: 'image/jpeg',
    uploadedAt: nowMs(),
    uploaderId: uid,
    uploaderUid: uid,
    uploaderLabel: 'Uploader',
    license: 'CC BY',
    rightsAttested: true,
    rightsAttestedAt: nowMs(),
    sourceUrl: 'https://example.com/source/night-grid',
    votes: 0,
    ...overrides,
  };
}

function ownerIndexPayload({ uploadId, uid, kind = 'sounds', overrides = {} }) {
  const isSound = kind === 'sounds';
  return {
    uploadId,
    publicId: `${isSound ? 'cu' : 'cw'}_${uploadId}`,
    contentType: isSound ? 'SOUND' : 'WALLPAPER',
    metadataPath: `/${isSound ? 'community_sounds' : 'community_wallpapers'}/${uploadId}`,
    storagePath: `${kind}/${uid}/${uploadId}.${isSound ? 'mp3' : 'jpg'}`,
    title: isSound ? 'Soft Bell' : 'Night Grid',
    createdAt: nowMs(),
    ...overrides,
  };
}

function uploadDeletionPayload({ uploadId, uid, kind = 'sounds', overrides = {} }) {
  const isSound = kind === 'sounds';
  const publicId = `${isSound ? 'cu' : 'cw'}_${uploadId}`;
  return {
    publicId,
    uploadId,
    contentType: isSound ? 'SOUND' : 'WALLPAPER',
    metadataPath: `/${isSound ? 'community_sounds' : 'community_wallpapers'}/${uploadId}`,
    storagePath: `${kind}/${uid}/${uploadId}.${isSound ? 'mp3' : 'jpg'}`,
    uploaderUid: uid,
    deletedByUid: uid,
    deletedAt: nowMs(),
    reason: 'OWNER_DELETE',
    ...overrides,
  };
}

function reportPayload(reporterUid, overrides = {}) {
  return {
    contentId: 'cu_sound_reported',
    contentKey: 'cu_sound_reported',
    contentType: 'SOUND',
    contentSource: 'COMMUNITY',
    reason: 'RIGHTS',
    note: 'This appears to use unlicensed audio.',
    sourceUrl: 'https://example.com/source',
    license: 'CC0',
    uploaderName: 'Uploader',
    reporterUid,
    reportedAt: nowMs(),
    status: 'OPEN',
    ...overrides,
  };
}

function takedownReceiptPayload(overrides = {}) {
  return {
    reportId: 'report1',
    contentId: 'SOUND::COMMUNITY::cu_sound1',
    contentType: 'SOUND',
    contentSource: 'COMMUNITY',
    reason: 'RIGHTS',
    action: 'HIDE',
    status: 'HIDDEN',
    uploadId: 'sound1',
    metadataPath: '/community_sounds/sound1',
    storagePath: 'sounds/sound-owner/sound1.mp3',
    uploaderUid: 'sound-owner',
    resolverUid: 'rules-admin',
    resolvedAt: nowMs(),
    note: 'Confirmed rights issue',
    ...overrides,
  };
}

function quotaPayload(overrides = {}) {
  const time = nowMs();
  return {
    count: 2,
    firstAt: time - 1000,
    lastAt: time,
    blockedCount: 1,
    lastBlockedAt: time,
    ...overrides,
  };
}

function dedupePayload(overrides = {}) {
  const time = nowMs();
  return {
    createdAt: time,
    expiresAt: time + 60_000,
    target: 'cu_sound_reported',
    ...overrides,
  };
}

function userBlockPayload({ blockerUid, blockedUid, overrides = {} }) {
  return {
    blockerUid,
    blockedUid,
    createdAt: nowMs(),
    reason: 'SPAM',
    ...overrides,
  };
}

function collectionPayload(createdByUid = 'collection-owner', overrides = {}) {
  return {
    version: 1,
    payload: JSON.stringify({
      version: 1,
      collectionName: 'Evening',
      items: [
        {
          wallpaperId: 'wall-1',
          source: 'PEXELS',
          thumbnailUrl: 'https://example.com/thumb.jpg',
          fullUrl: 'https://example.com/full.jpg',
          width: 1440,
          height: 2560,
        },
      ],
    }),
    collectionName: 'Evening',
    itemCount: 1,
    createdAt: nowMs(),
    createdByUid,
    ...overrides,
  };
}

async function seed(path, value) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.database().ref(path).set(value);
  });
}

test('community sound metadata is callable-owned while preserving owner deletion authority', async () => {
  const owner = dbFor('sound-owner');
  const other = dbFor('sound-other');
  const anonymous = unauthenticatedDb();
  const admin = adminDb();

  await assertFails(owner.ref('community_sounds/sound1').set(soundMetadata('sound-owner')));
  await assertSucceeds(admin.ref('community_sounds/sound1').set(soundMetadata('sound-owner')));
  await assertSucceeds(anonymous.ref('community_sounds/sound1').once('value'));
  await assertFails(anonymous.ref('community_sounds/anon').set(soundMetadata('sound-owner')));
  await assertFails(owner.ref('community_sounds/cross-owner').set(soundMetadata('sound-other')));
  await assertFails(admin.ref('community_sounds/bad-path').set(
    soundMetadata('sound-owner', { storagePath: 'wallpapers/sound-owner/not-a-sound.jpg' }),
  ));
  await assertFails(owner.ref('community_sounds/sound1').update({ name: 'Edited' }));

  await assertFails(other.ref('community_sounds/sound1').remove());
  await assertSucceeds(owner.ref('community_sounds/sound1').remove());

  await seed('community_sounds/adminDelete', soundMetadata('sound-owner'));
  await assertSucceeds(adminDb().ref('community_sounds/adminDelete').remove());
});

test('community wallpaper metadata is callable-owned while preserving owner deletion authority', async () => {
  const owner = dbFor('wall-owner');
  const other = dbFor('wall-other');
  const admin = adminDb();

  await assertFails(owner.ref('community_wallpapers/wall1').set(wallpaperMetadata('wall-owner')));
  await assertSucceeds(admin.ref('community_wallpapers/wall1').set(wallpaperMetadata('wall-owner')));
  await assertSucceeds(unauthenticatedDb().ref('community_wallpapers/wall1').once('value'));
  await assertFails(unauthenticatedDb().ref('community_wallpapers/anon').set(wallpaperMetadata('wall-owner')));
  await assertFails(owner.ref('community_wallpapers/cross-owner').set(wallpaperMetadata('wall-other')));
  await assertFails(admin.ref('community_wallpapers/bad-path').set(
    wallpaperMetadata('wall-owner', { storagePath: 'sounds/wall-owner/not-wallpaper.mp3' }),
  ));
  await assertFails(owner.ref('community_wallpapers/wall1').update({ name: 'Edited' }));

  await assertFails(other.ref('community_wallpapers/wall1').remove());
  await assertSucceeds(owner.ref('community_wallpapers/wall1').remove());
});

test('owner upload indexes are callable-owned while owners can delete their own index rows', async () => {
  const owner = dbFor('index-owner');
  const other = dbFor('index-other');
  const admin = adminDb();
  const payload = ownerIndexPayload({ uploadId: 'sound1', uid: 'index-owner' });
  const path = 'owner_uploads/index-owner/sounds/sound1';

  await assertFails(owner.ref(path).set(payload));
  await assertSucceeds(admin.ref(path).set(payload));
  await assertSucceeds(owner.ref(path).once('value'));
  await assertSucceeds(admin.ref(path).once('value'));
  await assertFails(other.ref(path).once('value'));
  await assertFails(other.ref(path).set(payload));
  await assertFails(owner.ref(path).set({ ...payload, uploadId: 'different' }));
  await assertFails(owner.ref(path).update({ title: 'Edited' }));
  await assertSucceeds(owner.ref(path).remove());
});

test('votes, follows, and creator profiles reject direct user writes', async () => {
  const user = dbFor('callable-user');
  const admin = adminDb();

  await assertFails(user.ref('votes/content1/upvotes').set(1));
  await assertSucceeds(admin.ref('votes/content1/upvotes').set(1));
  await assertFails(user.ref('votes/content1/voters/callable-user').set(true));
  await assertSucceeds(admin.ref('votes/content1/voters/callable-user').set(true));
  await assertFails(user.ref('voters/content1/callable-user').set(true));
  await assertSucceeds(admin.ref('voters/content1/callable-user').set(true));

  const followPayload = {
    creatorId: 'creator1',
    label: 'Creator One',
    followedAt: nowMs(),
  };
  await assertFails(user.ref('creator_follows/callable-user/creator1').set(followPayload));
  await assertSucceeds(admin.ref('creator_follows/callable-user/creator1').set(followPayload));

  const profilePayload = {
    profileUid: 'callable-user',
    displayName: 'Aura Maker',
    bio: 'Builds AMOLED packs',
    websiteUrl: 'https://example.com/profile',
    avatarUrl: '',
    createdAt: nowMs(),
    updatedAt: nowMs(),
  };
  await assertFails(user.ref('creator_profiles/callable-user').set(profilePayload));
  await assertSucceeds(admin.ref('creator_profiles/callable-user').set(profilePayload));
  await assertSucceeds(unauthenticatedDb().ref('creator_profiles/callable-user').once('value'));
});

test('community upload deletion tombstones stay private and validate ownership evidence', async () => {
  const owner = dbFor('delete-owner');
  const other = dbFor('delete-other');
  const admin = adminDb();
  const path = 'community_upload_deletions/cu_sound1';
  const payload = uploadDeletionPayload({ uploadId: 'sound1', uid: 'delete-owner' });

  await assertFails(unauthenticatedDb().ref(path).set(payload));
  await assertFails(other.ref(path).set(payload));
  await assertSucceeds(owner.ref(path).set(payload));
  await assertFails(owner.ref(path).once('value'));
  await assertSucceeds(admin.ref(path).once('value'));
  await assertFails(owner.ref(path).update({ deletedAt: nowMs() }));

  await assertFails(owner.ref('community_upload_deletions/cu_badpath').set(
    uploadDeletionPayload({
      uploadId: 'badpath',
      uid: 'delete-owner',
      overrides: {
        publicId: 'cu_badpath',
        storagePath: 'wallpapers/delete-owner/badpath.jpg',
      },
    }),
  ));

  await assertFails(owner.ref('community_upload_deletions/cu_wrongowner').set(
    uploadDeletionPayload({
      uploadId: 'wrongowner',
      uid: 'delete-owner',
      overrides: {
        publicId: 'cu_wrongowner',
        storagePath: 'sounds/someone-else/wrongowner.mp3',
      },
    }),
  ));

  await assertFails(owner.ref('community_upload_deletions/cu_badreason').set(
    uploadDeletionPayload({
      uploadId: 'badreason',
      uid: 'delete-owner',
      overrides: {
        publicId: 'cu_badreason',
        reason: 'ADMIN_TAKEDOWN',
      },
    }),
  ));

  await assertSucceeds(admin.ref('community_upload_deletions/cw_wall1').set(
    uploadDeletionPayload({
      uploadId: 'wall1',
      uid: 'delete-owner',
      kind: 'wallpapers',
      overrides: {
        deletedByUid: 'rules-admin',
        reason: 'ADMIN_TAKEDOWN',
      },
    }),
  ));
});

test('community report intake is callable-owned and admin-readable', async () => {
  const reporter = dbFor('reporter1');
  const other = dbFor('reporter2');
  const admin = adminDb();
  const path = 'community_reports/report1';

  await assertFails(reporter.ref(path).set(reportPayload('reporter1', { uploaderUid: 'uploader-1' })));
  await assertSucceeds(admin.ref(path).set(reportPayload('reporter1', { uploaderUid: 'uploader-1' })));
  await assertFails(unauthenticatedDb().ref('community_reports/anon').set(reportPayload('reporter1')));
  await assertFails(other.ref('community_reports/report2').set(reportPayload('reporter1')));
  await assertFails(admin.ref('community_reports/report3').set(reportPayload('reporter1', { uploaderUid: 'u'.repeat(241) })));
  await assertSucceeds(admin.ref('community_reports/providerPolicy').set(
    reportPayload('reporter1', {
      contentId: 'WALLPAPER::PEXELS::pexels-123',
      contentKey: 'WALLPAPER::PEXELS::pexels-123',
      contentType: 'WALLPAPER',
      contentSource: 'PEXELS',
      reason: 'PROVIDER_POLICY',
      sourceUrl: 'https://www.pexels.com/photo/example-123/',
      license: 'Pexels License',
      uploaderUid: '',
    }),
  ));
  await assertFails(reporter.ref(path).once('value'));
  await assertSucceeds(admin.ref(path).once('value'));
  await assertFails(reporter.ref(path).update({ status: 'HIDDEN' }));
  await assertSucceeds(admin.ref(path).update({
    status: 'HIDDEN',
    resolverUid: 'rules-admin',
    resolvedAt: nowMs(),
    resolutionNote: 'Hidden during review',
  }));
});

test('admin-only report resolution receipts reject regular clients', async () => {
  const admin = adminDb();
  const reporter = dbFor('reporter1');
  const resolutionPath = 'community_report_resolutions/report1';
  const resolution = {
    reportId: 'report1',
    status: 'DISMISSED',
    resolverUid: 'rules-admin',
    resolvedAt: nowMs(),
    note: 'No policy issue found',
  };

  await assertFails(reporter.ref(resolutionPath).set(resolution));
  await assertSucceeds(admin.ref(resolutionPath).set(resolution));
  await assertFails(reporter.ref(resolutionPath).once('value'));
  await assertSucceeds(admin.ref(resolutionPath).once('value'));
});

test('admin-only takedown receipts must match current upload deletion handles', async () => {
  const admin = adminDb();
  const reporter = dbFor('reporter1');
  const receiptPath = 'community_takedown_receipts/report1';
  const receipt = takedownReceiptPayload();

  await seed('community_sounds/sound1', soundMetadata('sound-owner', {
    storagePath: 'sounds/sound-owner/sound1.mp3',
  }));
  await assertFails(reporter.ref(receiptPath).set(receipt));
  await assertFails(admin.ref(receiptPath).set({ ...receipt, reason: 'SPAM' }));
  await assertFails(admin.ref(receiptPath).set({ ...receipt, status: 'DISMISSED' }));
  await assertFails(admin.ref(receiptPath).set({
    ...receipt,
    storagePath: 'sounds/sound-owner/stale.mp3',
  }));
  await assertFails(admin.ref(receiptPath).set({
    ...receipt,
    metadataPath: '/community_wallpapers/sound1',
  }));
  await assertSucceeds(admin.ref(receiptPath).set(receipt));
  await assertFails(reporter.ref(receiptPath).once('value'));
  await assertSucceeds(admin.ref(receiptPath).once('value'));

  const deleteReceiptPath = 'community_takedown_receipts/delete1';
  await assertFails(admin.ref(deleteReceiptPath).set(takedownReceiptPayload({
    reportId: 'delete1',
    action: 'DELETE',
  })));
  await assertSucceeds(admin.ref(deleteReceiptPath).set(takedownReceiptPayload({
    reportId: 'delete1',
    action: 'DELETE',
    deleteState: 'STARTED',
  })));
  await assertSucceeds(admin.ref('community_sounds/sound1').remove());
  await assertSucceeds(admin.ref(deleteReceiptPath).update({
    deleteState: 'SUCCEEDED',
    deletedAt: nowMs(),
    storageDeleted: true,
    metadataDeleted: true,
  }));
  await assertFails(admin.ref('community_takedown_receipts/delete2').set(takedownReceiptPayload({
    reportId: 'delete2',
    action: 'DELETE',
    deleteState: 'STARTED',
  })));

  await seed('community_wallpapers/wall1', wallpaperMetadata('wall-owner', {
    storagePath: 'wallpapers/wall-owner/wall1.jpg',
  }));
  await assertSucceeds(admin.ref('community_takedown_receipts/report2').set(takedownReceiptPayload({
    reportId: 'report2',
    contentId: 'WALLPAPER::COMMUNITY::cw_wall1',
    contentType: 'WALLPAPER',
    uploadId: 'wall1',
    metadataPath: '/community_wallpapers/wall1',
    storagePath: 'wallpapers/wall-owner/wall1.jpg',
    uploaderUid: 'wall-owner',
  })));
});

test('community quota and dedupe ledgers are admin-only', async () => {
  const user = dbFor('quota-user');
  const admin = adminDb();
  const quotaPath = `community_write_quotas/quota-user/${DAY_KEY}/reports`;
  const dedupePath = 'community_write_dedupe/quota-user/reports/cu_sound_reported';

  await assertFails(user.ref(quotaPath).set(quotaPayload()));
  await assertFails(user.ref(dedupePath).set(dedupePayload()));
  await assertSucceeds(admin.ref(quotaPath).set(quotaPayload()));
  await assertSucceeds(admin.ref(dedupePath).set(dedupePayload()));
  await assertFails(user.ref(quotaPath).once('value'));
  await assertSucceeds(admin.ref(quotaPath).once('value'));
});

test('community user block lists are callable-owned, private, and maintain an admin reverse index', async () => {
  const blocker = dbFor('blocker1');
  const blocked = dbFor('blocked1');
  const other = dbFor('block-other');
  const admin = adminDb();
  const listPath = 'community_user_blocks/blocker1/blocked1';
  const reversePath = 'community_blocked_by/blocked1/blocker1';
  const payload = userBlockPayload({ blockerUid: 'blocker1', blockedUid: 'blocked1' });

  await assertFails(unauthenticatedDb().ref(listPath).set(payload));
  await assertFails(other.ref(listPath).set(payload));
  await assertFails(blocker.ref(listPath).set(payload));
  await assertSucceeds(admin.ref(listPath).set(payload));
  await assertSucceeds(blocker.ref(listPath).once('value'));
  await assertFails(blocked.ref(listPath).once('value'));
  await assertSucceeds(admin.ref(listPath).once('value'));

  await assertFails(admin.ref('community_user_blocks/blocker1/blocked-mismatch').set(payload));
  await assertFails(admin.ref('community_user_blocks/blocker1/blocker1').set(
    userBlockPayload({ blockerUid: 'blocker1', blockedUid: 'blocker1' }),
  ));

  await assertFails(blocker.ref(reversePath).set(payload));
  await assertSucceeds(admin.ref(reversePath).set(payload));
  await assertFails(blocker.ref(reversePath).once('value'));
  await assertFails(blocked.ref(reversePath).once('value'));
  await assertSucceeds(admin.ref(reversePath).once('value'));
  await assertFails(other.ref(reversePath).set(payload));

  await assertFails(blocker.ref(listPath).remove());
  await assertFails(blocker.ref(reversePath).remove());
  await assertSucceeds(admin.ref(listPath).remove());
  await assertSucceeds(admin.ref(reversePath).remove());
});

test('collection share tokens use the app path, public reads, and bounded payloads', async () => {
  const owner = dbFor('collection-owner');
  const other = dbFor('collection-other');
  const admin = adminDb();
  const anonymous = unauthenticatedDb();
  const path = 'shared_collections/token12345';
  const ownerDeletePath = 'shared_collections/tokenOwnerDelete';

  await assertSucceeds(owner.ref(path).set(collectionPayload('collection-owner')));
  await assertSucceeds(anonymous.ref(path).once('value'));
  await assertFails(anonymous.ref('shared_collections/anon12345').set(collectionPayload('collection-owner')));
  await assertFails(owner.ref('shared_collections/wrongOwner').set(collectionPayload('someone-else')));
  await assertFails(other.ref(path).update({ collectionName: 'Overwritten' }));
  await assertSucceeds(owner.ref(path).update({ collectionName: 'Evening Set' }));
  await assertFails(owner.ref('shared_collections/oversize1').set(
    collectionPayload('collection-owner', { payload: 'x'.repeat(MAX_COLLECTION_PAYLOAD_BYTES + 1) }),
  ));
  await assertFails(owner.ref('collection_shares/legacy12345').set(collectionPayload('collection-owner')));
  await assertSucceeds(admin.ref(path).remove());

  await assertSucceeds(owner.ref(ownerDeletePath).set(collectionPayload('collection-owner')));
  await assertSucceeds(owner.ref(ownerDeletePath).remove());
});

test('test environment initialized database rules', () => {
  assert.ok(testEnv);
});
