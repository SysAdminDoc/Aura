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

## Verification

- `py -3 -m py_compile tools\community_account_deletion_plan.py test\tools\community_account_deletion_plan_test.py`
- `py -3 -m unittest discover -s test/tools -p '*_test.py'`
- `.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest --tests com.freevibe.service.CommunityIdentityProviderTest --tests com.freevibe.ui.screens.settings.SettingsViewModelTest`

## Remaining Work

- Add a trusted deletion executor after the Cloud Functions/backend deployment
  surface exists.
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
