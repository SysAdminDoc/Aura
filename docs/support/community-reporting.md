# Community Reporting

Cycles 51 through 54 add the first private report queue for Aura community and
mirrored provider content. Cycle 63 defines the callable quota contract that
will move report submission behind App-Checked backend enforcement. Reports are
separate from local hide/downvote behavior and feed admin hide/restore actions
through the existing moderation hide list.

## Report Reasons

Users can report content for:

- Rights or license
- Source removed
- Safety issue
- Spam
- Other

Reports store the content key, content type, source, reason, optional note,
source URL, license, uploader label, reporter UID, timestamp, and status.

## Storage Paths

- `/community_reports/{reportId}` stores the private report intake record.
- `/community_report_resolutions/{reportId}` stores admin resolution metadata.
- `/community_takedown_receipts/{reportId}` stores private rights-confirmed
  admin takedown receipts for community uploads when the resolver can prove the
  current upload metadata row still has a matching `storagePath` deletion
  handle and uploader UID.
- `/moderation/{contentId}` remains the public feed hide list used by current
  moderation filters.
- `/community_write_quotas/{uid}/{yyyyMMdd}/reports` and
  `/community_write_dedupe/{uid}/reports/{dedupeKey}` are reserved admin-only
  ledgers for the App-Checked callable report quota rollout.
- `/community_user_blocks/{blockerUid}/{blockedUid}` stores private user block
  rows readable by the blocker and admins.
- `/community_blocked_by/{blockedUid}/{blockerUid}` stores an admin-only reverse
  index for abuse review and future cleanup.

Only custom-claim admins can read report records or write resolution records.
Reporter UIDs are not public catalog data.

## Current Runtime Behavior

- Sound and wallpaper detail screens expose a report action for remote/provider
  content.
- Report submission requires the Community source switch to be enabled because
  the queue uses the same Firebase-backed community surface.
- ViewModels submit the current source, license, uploader, and HTTPS source URL
  context with each report.
- Custom-claim admins see Settings > Community reports, which lists open
  reports with reason, content key, source, license, uploader, source URL, and
  reporter suffix context.
- Admins can switch the report queue between Open, Hidden, Dismissed, and
  Restored status filters to review closed moderation history without exposing
  reporter details publicly.
- Admin actions can hide reported content by writing `/moderation/{contentId}`,
  dismiss a report without moderation changes, or restore hidden content by
  removing the moderation entry.
- Resolution writes mark reports hidden, dismissed, or restored with resolver
  UID, timestamp, and note metadata.
- When an admin hides a `RIGHTS` report for a community sound or wallpaper, the
  resolver also writes a private takedown receipt that records report ID,
  content ID, upload ID, metadata path, Storage path, uploader UID, resolver
  UID, timestamp, and note metadata. Receipts fail closed for non-rights
  reports, mirrored/provider content, or upload rows missing deletion handles.
- For qualifying rights reports, admins can also choose `Delete upload`. The
  delete path records a `DELETE` takedown receipt, hides the content ID through
  moderation, removes the Storage object and upload metadata/index rows, and
  marks the receipt `SUCCEEDED` or `FAILED` for retry evidence.
- Report quota policy is defined in
  [`docs/community-quota-rate-limits.md`](../community-quota-rate-limits.md):
  10 reports per UID per day, 2-minute cooldown, and content-key/reason dedupe
  once App-Checked callable enforcement is deployed.
- The callable migration contract is defined in
  [`docs/community-callable-quota-enforcement.md`](../community-callable-quota-enforcement.md):
  `submitCommunityReport` must require Firebase Auth, App Check, a limited-use
  App Check token, server-derived UID, quota ledger updates, dedupe ledger
  updates, and the final `/community_reports/{reportId}` write.
- The block-user policy is defined in
  [`docs/community-block-user-policy.md`](../community-block-user-policy.md):
  block state is private to the blocker and admins, while the reverse index is
  admin-only.

## Remaining Follow-Up

- App Check client providers and the report quota policy are tracked, but
  Firebase console enforcement and callable backend quota enforcement still need
  the backend rollout pass before public production reliance.
- Owner-delete storage handles are tracked in
  [`docs/community-upload-deletion.md`](../community-upload-deletion.md), and
  Cycle 60 adds private admin rights-confirmed takedown receipts for new rows
  with deletion handles. Cycle 61 adds admin delete actions that consume those
  receipts and record retry state. Public takedown request copy remains open.
- Cycle 58 adds Realtime Database emulator coverage for authenticated report
  creation, reporter UID validation, admin-only reads, admin status updates, and
  admin-only resolution receipts. Cycle 60 extends that coverage to
  rights-confirmed takedown receipt authorization and storage-handle matching.
- Cycle 62 adds closed-report status filters for Hidden, Dismissed, and Restored
  review queues. Cycle 63 adds the callable quota enforcement contract for the
  backend report submission migration. Cycle 68 reserves the private block-user
  data contract; UI filtering remains open.
