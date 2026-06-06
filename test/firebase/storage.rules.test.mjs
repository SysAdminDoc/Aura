import { after, before, test } from 'node:test';
import { readFileSync } from 'node:fs';
import { strict as assert } from 'node:assert';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  deleteObject,
  getDownloadURL,
  ref,
  uploadBytes,
  uploadString,
} from 'firebase/storage';

const PROJECT_ID = 'aura-rules-test';
const SOUND_LIMIT_BYTES = 20 * 1024 * 1024;
const WALLPAPER_LIMIT_BYTES = 4 * 1024 * 1024;

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    storage: {
      rules: readFileSync('storage.rules', 'utf8'),
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

function storageFor(uid, tokenOptions = {}) {
  return testEnv.authenticatedContext(uid, tokenOptions).storage();
}

function unauthenticatedStorage() {
  return testEnv.unauthenticatedContext().storage();
}

function pathFor(prefix, uid, name) {
  return `${prefix}/${uid}/${Date.now()}_${process.pid}_${name}`;
}

test('sound uploads require the owner path, supported audio type, and 20 MB ceiling', async () => {
  const owner = storageFor('sound-owner');
  const other = storageFor('sound-other');
  const anonymous = unauthenticatedStorage();
  const validPath = pathFor('sounds', 'sound-owner', 'tone.mp3');

  await assertSucceeds(
    uploadString(ref(owner, validPath), 'audio-bytes', 'raw', { contentType: 'audio/mpeg' }),
  );
  await assertSucceeds(getDownloadURL(ref(owner, validPath)));
  await assertFails(
    uploadString(ref(other, pathFor('sounds', 'sound-owner', 'cross.mp3')), 'audio-bytes', 'raw', {
      contentType: 'audio/mpeg',
    }),
  );
  await assertFails(
    uploadString(ref(anonymous, pathFor('sounds', 'sound-owner', 'anon.mp3')), 'audio-bytes', 'raw', {
      contentType: 'audio/mpeg',
    }),
  );
  await assertFails(
    uploadString(ref(owner, pathFor('sounds', 'sound-owner', 'video.mp4')), 'bytes', 'raw', {
      contentType: 'video/mp4',
    }),
  );
  await assertFails(
    uploadBytes(
      ref(owner, pathFor('sounds', 'sound-owner', 'oversize.mp3')),
      new Uint8Array(SOUND_LIMIT_BYTES + 1),
      { contentType: 'audio/mpeg' },
    ),
  );
});

test('wallpaper uploads require owner path, jpeg type, and 4 MB ceiling', async () => {
  const owner = storageFor('wall-owner');
  const other = storageFor('wall-other');
  const validPath = pathFor('wallpapers', 'wall-owner', 'image.jpg');

  await assertSucceeds(
    uploadBytes(ref(owner, validPath), new Uint8Array([1, 2, 3, 4]), { contentType: 'image/jpeg' }),
  );
  await assertSucceeds(getDownloadURL(ref(owner, validPath)));
  await assertFails(
    uploadBytes(ref(other, pathFor('wallpapers', 'wall-owner', 'cross.jpg')), new Uint8Array([1]), {
      contentType: 'image/jpeg',
    }),
  );
  await assertFails(
    uploadBytes(ref(owner, pathFor('wallpapers', 'wall-owner', 'png.png')), new Uint8Array([1]), {
      contentType: 'image/png',
    }),
  );
  await assertFails(
    uploadBytes(
      ref(owner, pathFor('wallpapers', 'wall-owner', 'oversize.jpg')),
      new Uint8Array(WALLPAPER_LIMIT_BYTES + 1),
      { contentType: 'image/jpeg' },
    ),
  );
});

test('only owners or admins can delete community upload blobs', async () => {
  const owner = storageFor('delete-owner');
  const other = storageFor('delete-other');
  const admin = storageFor('rules-admin', { admin: true });
  const ownerDeletePath = pathFor('sounds', 'delete-owner', 'owner-delete.mp3');
  const adminDeletePath = pathFor('wallpapers', 'delete-owner', 'admin-delete.jpg');

  await assertSucceeds(
    uploadString(ref(owner, ownerDeletePath), 'audio-bytes', 'raw', { contentType: 'audio/mpeg' }),
  );
  await assertFails(deleteObject(ref(other, ownerDeletePath)));
  await assertSucceeds(deleteObject(ref(owner, ownerDeletePath)));

  await assertSucceeds(
    uploadBytes(ref(owner, adminDeletePath), new Uint8Array([1, 2, 3, 4]), { contentType: 'image/jpeg' }),
  );
  await assertSucceeds(deleteObject(ref(admin, adminDeletePath)));
});

test('unmanaged storage paths stay closed', async () => {
  const owner = storageFor('sound-owner');

  await assertFails(
    uploadString(ref(owner, pathFor('avatars', 'sound-owner', 'avatar.jpg')), 'bytes', 'raw', {
      contentType: 'image/jpeg',
    }),
  );
});

test('test environment initialized storage rules', () => {
  assert.ok(testEnv);
});
