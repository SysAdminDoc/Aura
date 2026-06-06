# Firebase Rules Harness

Aura now tracks Firebase deploy/test configuration for community backend rules:

- `database.rules.json` for Realtime Database.
- `storage.rules` for Cloud Storage upload blobs.
- `firebase.json` for emulator/deploy rule file mapping.
- `test/firebase/storage.rules.test.mjs` for Storage emulator rules coverage.

## Local Verification

Install the pinned local tooling once:

```powershell
npm install
```

Run the Storage rules suite:

```powershell
npm run test:storage-rules
```

The command starts the local Firebase Storage emulator through the project-local
`firebase-tools` dependency and runs Node's built-in test runner. A Firebase
login is not required for local emulator execution; the CLI may still print an
unauthenticated warning.

## Current Storage Policy

Tracked `storage.rules` allow:

- public reads for committed community upload blobs under `sounds/{uid}/...` and
  `wallpapers/{uid}/...`;
- owner-only sound creates under `sounds/{uid}/...`;
- owner-only wallpaper creates under `wallpapers/{uid}/...`;
- owner or custom-claim admin deletes;
- no updates/overwrites after create; and
- no access to unmanaged paths.

The rules enforce app-matched ceilings:

- sounds: supported audio MIME type and `20 MB` maximum;
- wallpapers: `image/jpeg` and `4 MB` maximum.

## Audit Note

`npm audit --audit-level=moderate` currently reports a moderate transitive
`uuid` advisory through `firebase-tools` -> `gaxios`. npm only offers a forced
downgrade to `firebase-tools@13.13.3`. Aura keeps the current `firebase-tools`
pin for emulator compatibility and treats this as a dev-test-tool dependency
risk until the upstream CLI dependency graph publishes a non-downgrade fix.

## Remaining Work

- Add Realtime Database emulator tests for upload metadata, owner index, report
  queue, quota namespaces, and collection shares.
- Add admin takedown receipt tests once that flow is implemented.
- Add CI wiring for `npm ci` and `npm run test:storage-rules` on rules changes.
- Define Cloud Storage lifecycle/orphan cleanup policy for abandoned upload
  objects.
