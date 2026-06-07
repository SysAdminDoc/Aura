# Community Backend Deploy and Rollback Runbook

Cycle 64 defines the deployable evidence packet for Aura community backend
rules. The backend surface currently includes Firebase Realtime Database rules,
Cloud Storage rules, local Emulator Suite tests, and a deterministic manifest of
the files that affect rules deployment.

## Backend Manifest

`docs/community-backend-manifest.json` records the current deploy target set,
Firebase CLI package version, rules-test scripts, and SHA-256 hashes for:

- `firebase.json`
- `database.rules.json`
- `storage.rules`
- `package.json`
- `package-lock.json`

Regenerate it after changing any of those files:

```powershell
py -3 tools\community_backend_manifest.py --mode write
```

Verify it locally:

```powershell
py -3 tools\community_backend_manifest.py --mode check
```

The `firebase-rules` CI job runs the same check before the Emulator Suite.

## Required Preflight

Run from the repo root:

```powershell
git status --short --branch
npm ci
py -3 tools\community_backend_manifest.py --mode check
py -3 tools\community_callable_contract_check.py --contract docs\community-callable-contract.json
py -3 tools\community_deletion_web_url_check.py --manifest docs\support\community-account-deletion-web-url.json --repo-root .
npm run test:firebase-rules
npx firebase --version
```

The current pinned CLI is `firebase-tools` 15.19.1. Do not deploy from a global
Firebase CLI unless its version matches or the manifest has been deliberately
updated.

## Dry Run

Use the real Firebase project ID. Do not use the emulator project ID for a
production validation.

```powershell
$projectId = "<project-id>"
npx firebase deploy --only database,storage --project $projectId --dry-run
```

The pinned CLI advertises `deploy --dry-run` as validating and building without
deploying changes. It can still enable APIs on the target project while
validating. Treat it as an operator-gated production-project action, not a local
unit test.

Record this evidence before a real deploy:

- Git commit SHA.
- `docs/community-backend-manifest.json` SHA-256 or copied JSON.
- `npm run test:firebase-rules` result.
- `npx firebase --version` output.
- Dry-run command and exit code.
- Firebase project ID.
- Operator initials and timestamp.
- App Check enforcement state for Realtime Database, Cloud Storage, and
  Authentication.

## Deploy

Deploy only after the preflight and dry run pass:

```powershell
$projectId = "<project-id>"
$message = "Aura community backend <git-sha>"
npx firebase deploy --only database,storage --project $projectId --message $message
```

After deploy:

- Save the deploy command output with the release/backend evidence.
- Smoke test one public read, one authenticated owner metadata write, one
  authenticated report write, and one admin-only read/update if an admin account
  is available.
- Check Firebase Console App Check metrics. Enabling App Check enforcement is a
  separate console-side action and must keep its own rollback note.

## Rollback

Rollback means redeploying the last known-good backend file set. Do not edit
rules directly in the Firebase Console as the primary rollback path.

1. Identify the known-good commit from `git log --oneline -- firebase.json
   database.rules.json storage.rules package.json package-lock.json`.
2. Restore the backend file set in the worktree:

   ```powershell
   $knownGood = "<commit-sha>"
   git checkout $knownGood -- firebase.json database.rules.json storage.rules package.json package-lock.json docs/community-backend-manifest.json
   py -3 tools\community_backend_manifest.py --mode write
   npm ci
   npm run test:firebase-rules
   npx firebase deploy --only database,storage --project <project-id> --dry-run
   npx firebase deploy --only database,storage --project <project-id> --message "Aura community backend rollback <known-good-sha>"
   ```

3. Commit the rollback file set if the repository needs to remain aligned with
   production. If the rollback is a short-lived production mitigation, open the
   follow-up item that either recommits the known-good files or fixes forward.

If App Check enforcement is the failure source, first disable enforcement for
the affected Firebase service in the console, record the timestamp, and then
decide whether rules rollback is also needed.

## Release Checklist Entry

Every release or backend-only change that touches Firebase community behavior
must include:

- Backend manifest check result.
- Callable contract manifest check result when callable policy, quota, or
  backend contract files change.
- Firebase rules test result.
- Dry-run result or explicit owner note explaining why the deploy was deferred.
- Deployed project ID and command output when deployed.
- Rollback target commit.
- App Check monitor/enforce state.
- Storage lifecycle/orphan cleanup status from
  [`docs/community-storage-lifecycle-policy.md`](community-storage-lifecycle-policy.md),
  including orphan report hashes when manual cleanup is performed.
- Deletion retention status from
  [`docs/community-deletion-retention-policy.md`](community-deletion-retention-policy.md),
  including tombstone-path evidence for owner deletes or admin takedowns.
- Block-user rollout status from
  [`docs/community-block-user-policy.md`](community-block-user-policy.md),
  including callable migration and feed-filtering state.
- Account deletion dry-run status from
  [`docs/community-account-deletion-policy.md`](community-account-deletion-policy.md),
  including the reviewed plan hash when an account deletion request is tested
  or processed.
- Deletion request-code lookup output from
  `tools/community_deletion_request_lookup.py` when support receives an
  `AURA-` request code instead of a full UID.
- Web-intake receipt from `tools/community_deletion_web_intake.py` when support
  receives the request through the hosted private web form instead of the
  in-app share draft.
- Hosted account deletion web URL manifest status from
  `tools/community_deletion_web_url_check.py` before Play production
  submission or after any web deletion URL change.
- Account deletion review receipt from
  `tools/community_account_deletion_review.py` before any future trusted apply
  step accepts a deletion plan.
- Account deletion apply simulation receipt from
  `tools/community_account_deletion_apply_simulator.py` when a reviewed plan is
  tested against an exported RTDB snapshot.
- Private account deletion executor package from
  `tools/community_account_deletion_executor_package.py` before any future
  trusted executor or callable applies RTDB updates.
- Guarded REST executor dry-run/apply receipt from
  `tools/community_account_deletion_rest_executor.py` when reviewed account
  deletion RTDB updates are tested or applied.
- Redacted account deletion completion receipt from
  `tools/community_account_deletion_completion_receipt.py` after an applied
  REST receipt exists and before any requester-facing completion response is
  shared.
- Local/Auth cleanup sequence from
  `tools/community_account_deletion_cleanup_sequence.py` after backend
  completion and before Firebase Auth deletion or requester local-cleanup
  instructions are handled.
- Private Firebase Auth deletion package from
  `tools/community_account_deletion_auth_package.py` before deleting the Auth
  user through an owner-approved Firebase Console, Admin SDK, or CLI path.
- Redacted Firebase Auth deletion execution receipt from
  `tools/community_account_deletion_auth_execution_receipt.py` after the
  owner-approved Auth deletion action and post-delete not-found verification.
- Private account deletion upload handoff plan from
  `tools/community_account_deletion_upload_plan.py` before claiming public
  uploads were removed for a deleted account request.
- Redacted public-upload deletion execution receipt from
  `tools/community_account_deletion_upload_execution_receipt.py` after every
  planned Storage object, metadata row, owner index, and tombstone action has
  private owner/admin workflow evidence.

## Sources

- Firebase CLI deploy reference: https://firebase.google.com/docs/cli
- Firebase App Check overview: https://firebase.google.com/docs/app-check
- App Check enforcement: https://firebase.google.com/docs/app-check/enable-enforcement
- Cloud Storage lifecycle management: https://docs.cloud.google.com/storage/docs/lifecycle
