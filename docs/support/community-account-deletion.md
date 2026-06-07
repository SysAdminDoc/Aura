# Community Account Deletion Requests

Aura community identity is anonymous by default. The app can show a redacted
identity suffix and request code after a Firebase community identity exists.

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

## Operator Handling

1. Receive the request draft through a private support channel.
2. Verify the requester and confirm the request code maps to the intended UID.
3. Run the request-code lookup against a current RTDB export:

   ```powershell
   py -3 tools\community_deletion_request_lookup.py --database-export .\rtdb-export.json --request-code AURA-123456789ABC --output .\deletion-request-lookup.json
   ```

4. Run the dry-run planner against a current RTDB export:

   ```powershell
   py -3 tools\community_account_deletion_plan.py --database-export .\rtdb-export.json --uid <matched-private-uid> --output .\account-deletion-plan.json
   ```

5. Review the lookup and plan consistency:

   ```powershell
   py -3 tools\community_account_deletion_review.py --lookup .\deletion-request-lookup.json --plan .\account-deletion-plan.json --request-code AURA-123456789ABC --output .\account-deletion-review.json
   ```

6. Simulate the reviewed null updates against the same export:

   ```powershell
   py -3 tools\community_account_deletion_apply_simulator.py --database-export .\rtdb-export.json --plan .\account-deletion-plan.json --review .\account-deletion-review.json --output .\account-deletion-simulation.json
   ```

7. Build the private executor package:

   ```powershell
   py -3 tools\community_account_deletion_executor_package.py --plan .\account-deletion-plan.json --review .\account-deletion-review.json --simulation .\account-deletion-simulation.json --request-code AURA-123456789ABC --operator <private-ticket-or-initials> --output .\account-deletion-executor-package.json
   ```

8. Review retained public upload and moderation records against
   `docs/community-account-deletion-policy.md`.
9. Apply changes only through the future trusted executor or callable
   orchestrator.

Do not request or publish a full Firebase UID in a public issue.
