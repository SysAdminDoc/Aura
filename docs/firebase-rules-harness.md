# Firebase Rules Harness

Aura now tracks Firebase deploy/test configuration for community backend rules:

- `database.rules.json` for Realtime Database.
- `storage.rules` for Cloud Storage upload blobs.
- `firebase.json` for emulator/deploy rule file mapping.
- `test/firebase/database.rules.test.mjs` for Realtime Database emulator rules
  coverage.
- `test/firebase/storage.rules.test.mjs` for Storage emulator rules coverage.

## Local Verification

Install the pinned local tooling once:

```powershell
npm install
```

Run the Realtime Database rules suite:

```powershell
npm run test:database-rules
```

Run the Storage rules suite:

```powershell
npm run test:storage-rules
```

Run both suites together:

```powershell
npm run test:firebase-rules
```

These commands start local Firebase emulators through the project-local
`firebase-tools` dependency and run Node's built-in test runner. A Firebase
login is not required for local emulator execution; the CLI may still print an
unauthenticated warning.

## CI Verification

`.github/workflows/verify.yml` includes a `firebase-rules` job. It detects
changes to Firebase rules, emulator config, npm lockfiles, rules tests, this
runbook, the admin-claims runbook, or the workflow itself. When those files
change, the job installs the pinned npm dependencies with `npm ci` and runs:

```bash
npm run test:firebase-rules
```

Manual `workflow_dispatch` runs always execute the Firebase rules suite.

## Current Realtime Database Policy

Tracked `database.rules.json` now loads in the Realtime Database emulator and
covers:

- public reads for `community_sounds` and `community_wallpapers`;
- authenticated owner creates for new community upload metadata;
- owner or custom-claim admin deletes for upload metadata;
- private `/owner_uploads/{uid}` indexes readable and writable only by the owner
  or admins;
- authenticated report creation with reporter UID validation;
- admin-only report reads, report resolutions, takedown receipts, quota
  ledgers, and dedupe ledgers;
- takedown receipt validation that requires the current community upload
  metadata row to contain the same `storagePath` and uploader UID recorded in
  the receipt;
- app-matched `shared_collections/{token}` public reads, authenticated creator
  writes, owner/admin cleanup, and bounded payloads; and
- denial for the old unused `collection_shares` path.

The emulator pass also keeps the rules file deploy-compatible: no top-level or
inline pseudo-comment properties, and prefix checks use Realtime Database string
methods such as `beginsWith()`.

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

- Define Cloud Storage lifecycle/orphan cleanup policy for abandoned upload
  objects.
