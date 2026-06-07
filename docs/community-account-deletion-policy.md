# Community Account Deletion Policy

Cycle 74 defined the first dry-run deletion contract for Aura community
identity data. Cycle 75 added the first user-visible request surface. The
contract focuses on user-identifying RTDB marker paths that can be removed
without deleting public uploads or moderation evidence out of sequence.

## Scope

The dry-run planner accepts a Firebase UID and an exported Realtime Database
JSON file:

```powershell
py -3 tools\community_account_deletion_plan.py --database-export .\rtdb-export.json --uid <firebase-uid> --output .\account-deletion-plan.json
```

The generated `updates` object is a Realtime Database multi-path update shape
whose values are `null`. It is a review artifact until a trusted admin script
or callable deletion orchestrator applies it.

Operators should pair the dry-run plan with the request-code lookup and review
receipt before any future apply step:

```powershell
py -3 tools\community_account_deletion_review.py --lookup .\deletion-request-lookup.json --plan .\account-deletion-plan.json --request-code AURA-123456789ABC --output .\account-deletion-review.json
```

The review receipt validates one lookup match, matching sanitized UID keys,
null-only update values, category coverage, required retained roots, and emits
hashes plus a redacted UID-key suffix for private release evidence.

Operators can then simulate the reviewed null updates against the same export:

```powershell
py -3 tools\community_account_deletion_apply_simulator.py --database-export .\rtdb-export.json --plan .\account-deletion-plan.json --review .\account-deletion-review.json --output .\account-deletion-simulation.json
```

The simulator verifies the review hash and plan hash, applies the null updates
to a local copy, prunes empty objects, and emits a hashed receipt with deleted,
missing-before, and remaining-path counts. It does not contact Firebase.

After review and simulation pass, operators can build the private executor
package:

```powershell
py -3 tools\community_account_deletion_executor_package.py --plan .\account-deletion-plan.json --review .\account-deletion-review.json --simulation .\account-deletion-simulation.json --request-code AURA-123456789ABC --operator <private-ticket-or-initials> --output .\account-deletion-executor-package.json
```

The executor package validates all hashes again and contains the full RTDB
multi-path update payload for the future trusted executor. It is private
operator evidence and must not be published.

The guarded REST executor defaults to dry-run:

```powershell
py -3 tools\community_account_deletion_rest_executor.py --package .\account-deletion-executor-package.json --database-url https://<database-name>.firebaseio.com --mode dry-run --output .\account-deletion-rest-dry-run.json
```

Apply mode requires explicit request-code and plan-hash confirmations plus an
OAuth2 token in `FIREBASE_DATABASE_ACCESS_TOKEN` or `--access-token`:

```powershell
py -3 tools\community_account_deletion_rest_executor.py --package .\account-deletion-executor-package.json --database-url https://<database-name>.firebaseio.com --mode apply --confirm-request-code AURA-123456789ABC --confirm-plan-hash <planHash> --output .\account-deletion-rest-apply.json
```

## Deleted Marker Paths

The planner removes:

- `/votes/{contentId}/voters/{uid}` nested vote markers.
- `/voters/{contentId}/{uid}` legacy vote markers.
- `/creator_follows/{uid}` outbound follows.
- `/creator_follows/{followerUid}/{uid}` inbound follows that reference the
  deleted creator.
- `/creator_profiles/{uid}` creator profile rows.
- `/community_user_blocks/{uid}` outbound blocks.
- `/community_user_blocks/{blockerUid}/{uid}` private block-list rows that
  reference the deleted user.
- `/community_blocked_by/{uid}` inbound block reverse indexes.
- `/community_blocked_by/{blockedUid}/{uid}` outbound block reverse-index rows.
- `/shared_collections/{token}` and `/collection_shares/{token}` rows created
  by the UID.

UIDs are normalized with the same Firebase RTDB key replacement used by
community votes, so slash/dot variants resolve to the stored key form.

## Retained Data

- `/votes/{contentId}/upvotes` aggregate counts are retained after per-user
  markers are removed. Aggregate counts no longer identify the deleted user,
  and decrementing them from a dry-run export would be race-prone.
- `/community_reports`, `/community_report_resolutions`,
  `/community_takedown_receipts`, `/community_upload_deletions`, and
  `/moderation` are retained as private safety, rights, and abuse records.
- Public upload metadata, owner-upload indexes, and Storage objects are not
  deleted by this planner. They must go through the owner/admin upload deletion
  workflow so Storage blobs, metadata rows, owner indexes, and tombstones remain
  consistent.

## User-Facing Request Surface

Settings > Community identity shows the current community auth label and a
redacted identity suffix. When a Firebase identity already exists, it also shows
an `AURA-` deletion request code that can be copied or shared as a redacted
request draft.

Opening the panel is read-only. It does not call `ensureSignedIn()`, does not
create a Firebase anonymous account, and does not create the local fallback UUID
solely to show an identity panel. Users without a Firebase identity see that no
backend deletion request code is available yet.

The request code is a routing handle for support/admin tooling, not proof of
ownership by itself. A trusted deletion executor still needs to verify the
request before applying the dry-run plan.

User and operator handling instructions live in
[`docs/support/community-account-deletion.md`](support/community-account-deletion.md).
Operators can use `tools/community_deletion_request_lookup.py` with a current
RTDB export to map a request code to candidate UID evidence before running the
dry-run planner and review receipt.

## Verification

- `py -3 -m py_compile tools\community_account_deletion_plan.py test\tools\community_account_deletion_plan_test.py`
- `py -3 -m unittest discover -s test/tools -p '*_test.py'`
- `.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest --tests com.freevibe.service.CommunityIdentityProviderTest --tests com.freevibe.ui.screens.settings.SettingsViewModelTest`
- `py -3 -m py_compile tools\community_deletion_request_lookup.py test\tools\community_deletion_request_lookup_test.py`
- `py -3 -m py_compile tools\community_account_deletion_review.py test\tools\community_account_deletion_review_test.py`
- `py -3 -m py_compile tools\community_account_deletion_apply_simulator.py test\tools\community_account_deletion_apply_simulator_test.py`
- `py -3 -m py_compile tools\community_account_deletion_executor_package.py test\tools\community_account_deletion_executor_package_test.py`
- `py -3 -m py_compile tools\community_account_deletion_rest_executor.py test\tools\community_account_deletion_rest_executor_test.py`

## Remaining Work

- Run the guarded REST executor only after requester verification, retained
  record review, operator approval, and production-project access are confirmed.
- Add local/Auth deletion and community cache cleanup after the trusted
  executor owns final sequencing.
- Publish a hosted private support route or web deletion page before Play
  production submission.
- Decide whether future callable-backed vote deletion should decrement
  aggregate counts transactionally.

## Sources

- Google Play account deletion requirements:
  https://support.google.com/googleplay/android-developer/answer/13327111
- Firebase Realtime Database delete data:
  https://firebase.google.com/docs/database/android/read-and-write#delete_data
- Firebase Auth delete users:
  https://firebase.google.com/docs/auth/android/manage-users#delete_a_user
- Firebase Storage delete files:
  https://firebase.google.com/docs/storage/android/delete-files
