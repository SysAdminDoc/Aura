# Community Account Deletion Requests

Aura community identity is anonymous by default. The app can show a redacted
identity suffix and request code after a Firebase community identity exists.

## Web Request Intake

Users who cannot open Aura can submit the same `AURA-` request code through a
private hosted support surface. The field contract, privacy boundaries, and
validation command live in
[`community-account-deletion-web-intake.md`](community-account-deletion-web-intake.md).
The current publication state for the hosted URL lives in
[`community-account-deletion-web-url.json`](community-account-deletion-web-url.json);
it remains `pendingOwnerUrl` until the owner publishes an HTTPS request page and
links it from both the privacy policy and support intake document.
Publishable page copy lives in
[`community-account-deletion-web-page.md`](community-account-deletion-web-page.md).

Validate private web form exports before operator lookup:

```powershell
py -3 tools\community_deletion_web_intake.py --request .\web-intake-request.json --support-reference <ticket-id> --output .\web-intake-receipt.json
```

Validate the hosted URL publication manifest before Play submission or after
any URL/status change:

```powershell
py -3 tools\community_deletion_web_url_check.py --manifest docs\support\community-account-deletion-web-url.json --repo-root .
```

Validate the hosted page copy before publication:

```powershell
py -3 tools\community_deletion_web_page_check.py --page docs\support\community-account-deletion-web-page.md
```

The receipt hashes contact and statement fields and does not include raw
requester contact, full Firebase UIDs, RTDB paths, database exports, executor
packages, or access tokens.

## In-App Request Draft

Open:

`Settings` > `Community identity`

If a Firebase identity exists, the dialog shows an `AURA-` deletion request
code. Use `Copy code` to copy only the routing code, or `Share` to create a
pre-filled request draft in the device share sheet.

The draft includes:

- Deletion request code.
- Redacted identity suffix.
- Auth state label.
- A deletion request statement.

The draft does not include the full Firebase UID. Support/admin tooling must
map the request code privately and verify the request before applying any
deletion plan.

## Current Backend Status

`tools/community_account_deletion_plan.py` can create a dry-run Realtime
Database null-update plan for user marker paths from an exported RTDB JSON
file. The plan is review-only until a trusted executor or callable
orchestrator exists.

The current planner covers vote markers, follows, creator profile rows, block
rows, reverse indexes, and community share rows. Public uploads, Storage
objects, owner-upload indexes, Auth deletion, and local app cache cleanup still
require the trusted orchestrator path.

`tools/community_account_deletion_review.py` validates that a request-code
lookup and dry-run plan describe exactly one matching user, that every planned
update is a null delete, and that retained public/moderation roots are still
declared. It emits a redacted review receipt for private operator evidence; it
does not apply any database changes.

`tools/community_account_deletion_apply_simulator.py` applies the reviewed
null-update plan to a local copy of the RTDB export, prunes empty objects, and
emits a hashed simulation receipt. It does not contact Firebase and is only
evidence for a future trusted executor.

`tools/community_account_deletion_executor_package.py` validates the plan,
review receipt, and simulation receipt, then builds the private RTDB update
package a future trusted executor can consume. The package contains full update
paths and must not be published.

`tools/community_account_deletion_rest_executor.py` is the guarded operator
executor. It defaults to dry-run and only applies through the Realtime Database
REST API when the operator provides a matching request code, matching plan hash,
database URL, and OAuth2 access token.

`tools/community_account_deletion_completion_receipt.py` validates the private
executor package against an applied REST executor receipt, rejects dry-runs, and
emits a user-safe completion receipt that omits full UIDs, RTDB paths, database
hosts, update payloads, and access tokens.

`tools/community_account_deletion_cleanup_sequence.py` starts only from the
completion receipt and records the remaining local device cleanup, Firebase
Auth deletion, and public upload deletion sequencing without exposing the full
UID.

`tools/community_account_deletion_auth_package.py` validates the private
request-code lookup against the completed backend deletion receipt and builds
the private Firebase Auth deletion package. The package contains the full UID
and must never be shared with the requester or committed.

`tools/community_account_deletion_auth_execution_receipt.py` validates private
owner-approved Firebase Auth deletion evidence against the Auth package and
emits a redacted receipt after post-delete lookup confirms the Auth user is not
found.

`tools/community_account_deletion_upload_plan.py` consumes the private Auth
package plus a current RTDB export and creates a private public-upload deletion
handoff plan. It enumerates owned uploads with valid `storagePath` handles and
blocks rows that need backfill or manual review.

`tools/community_account_deletion_upload_execution_receipt.py` validates private
owner/admin upload deletion workflow evidence against a clean upload handoff
plan and emits a redacted receipt after Storage deletes, metadata deletes,
owner-index deletes, and tombstone writes are all complete.

## Operator Handling

1. Receive the request draft through a private support channel.
2. For web submissions, validate the raw private form export:

   ```powershell
   py -3 tools\community_deletion_web_intake.py --request .\web-intake-request.json --support-reference <ticket-id> --output .\web-intake-receipt.json
   ```

3. Verify the requester and confirm the request code maps to the intended UID.
4. Run the request-code lookup against a current RTDB export:

   ```powershell
   py -3 tools\community_deletion_request_lookup.py --database-export .\rtdb-export.json --request-code AURA-123456789ABC --output .\deletion-request-lookup.json
   ```

5. Run the dry-run planner against a current RTDB export:

   ```powershell
   py -3 tools\community_account_deletion_plan.py --database-export .\rtdb-export.json --uid <matched-private-uid> --output .\account-deletion-plan.json
   ```

6. Review the lookup and plan consistency:

   ```powershell
   py -3 tools\community_account_deletion_review.py --lookup .\deletion-request-lookup.json --plan .\account-deletion-plan.json --request-code AURA-123456789ABC --output .\account-deletion-review.json
   ```

7. Simulate the reviewed null updates against the same export:

   ```powershell
   py -3 tools\community_account_deletion_apply_simulator.py --database-export .\rtdb-export.json --plan .\account-deletion-plan.json --review .\account-deletion-review.json --output .\account-deletion-simulation.json
   ```

8. Build the private executor package:

   ```powershell
   py -3 tools\community_account_deletion_executor_package.py --plan .\account-deletion-plan.json --review .\account-deletion-review.json --simulation .\account-deletion-simulation.json --request-code AURA-123456789ABC --operator <private-ticket-or-initials> --output .\account-deletion-executor-package.json
   ```

9. Dry-run the guarded REST executor:

   ```powershell
   py -3 tools\community_account_deletion_rest_executor.py --package .\account-deletion-executor-package.json --database-url https://<database-name>.firebaseio.com --mode dry-run --output .\account-deletion-rest-dry-run.json
   ```

10. Review retained public upload and moderation records against
   `docs/community-account-deletion-policy.md`.
11. Apply only after requester verification, retained-record review, and
    operator approval:

   ```powershell
   $env:FIREBASE_DATABASE_ACCESS_TOKEN = "<OAuth2 access token>"
   py -3 tools\community_account_deletion_rest_executor.py --package .\account-deletion-executor-package.json --database-url https://<database-name>.firebaseio.com --mode apply --confirm-request-code AURA-123456789ABC --confirm-plan-hash <planHash> --output .\account-deletion-rest-apply.json
   ```

12. Build the redacted completion receipt only from the applied REST receipt:

   ```powershell
   py -3 tools\community_account_deletion_completion_receipt.py --package .\account-deletion-executor-package.json --rest-receipt .\account-deletion-rest-apply.json --request-code AURA-123456789ABC --support-reference <ticket-id> --output .\account-deletion-completion-receipt.json
   ```

13. Build the local/Auth cleanup sequence after backend completion:

   ```powershell
   py -3 tools\community_account_deletion_cleanup_sequence.py --completion-receipt .\account-deletion-completion-receipt.json --support-reference <ticket-id> --output .\account-deletion-cleanup-sequence.json
   ```

14. Build the private Auth deletion package only after backend completion and
    private UID reverification:

   ```powershell
   py -3 tools\community_account_deletion_auth_package.py --lookup .\deletion-request-lookup.json --completion-receipt .\account-deletion-completion-receipt.json --request-code AURA-123456789ABC --support-reference <ticket-id> --operator <private-ticket-or-initials> --output .\account-deletion-auth-package.json
   ```

15. Delete the Firebase Auth user only through an owner-approved Firebase
    Console, Admin SDK, or CLI path for the production project. Archive the
    private Auth package and command evidence with backend evidence.

16. Build the redacted Auth execution receipt from the private owner evidence:

   ```powershell
   py -3 tools\community_account_deletion_auth_execution_receipt.py --auth-package .\account-deletion-auth-package.json --execution-evidence .\auth-execution-evidence.json --support-reference <ticket-id> --output .\account-deletion-auth-execution-receipt.json
   ```

17. If the requester asks to remove public uploads, build the private upload
    deletion handoff plan:

   ```powershell
   py -3 tools\community_account_deletion_upload_plan.py --database-export .\rtdb-export.json --auth-package .\account-deletion-auth-package.json --output .\account-deletion-upload-plan.json
   ```

18. Use the owner/admin upload deletion workflow for every candidate upload and
    resolve any blocked rows through backfill or manual review before claiming
    public uploads were removed.

19. Build the redacted upload execution receipt from the private owner/admin
    workflow evidence:

   ```powershell
   py -3 tools\community_account_deletion_upload_execution_receipt.py --upload-plan .\account-deletion-upload-plan.json --execution-evidence .\upload-execution-evidence.json --support-reference <ticket-id> --output .\account-deletion-upload-execution-receipt.json
   ```

20. Share only the completion receipt and requester-facing local cleanup
    instructions with the requester. Keep lookup, plan,
    review, simulation, executor package, REST apply receipt, Auth package, raw
    Auth execution evidence, upload plan, raw upload execution evidence,
    database export, access token, full UID, Storage paths, and RTDB paths
    private.

If the requester still has Aura installed, they can open `Settings` >
`Community identity` > `Clear local` after support confirms backend completion.
That clears only the local fallback community identity stored on the device and
does not delete backend, Firebase Auth, public upload, or moderation records.
If Aura is no longer installed, the equivalent local-device action is clearing
Aura app data from Android system settings or reinstalling after support
confirms completion.

Do not request or publish a full Firebase UID in a public issue.
