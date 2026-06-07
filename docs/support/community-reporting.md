# Community Reporting

Cycles 51 through 54 add the first private report queue for Aura community and
mirrored provider content. Reports are separate from local hide/downvote
behavior and feed admin hide/restore actions through the existing moderation
hide list.

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
- Add report tabs or filters for closed reports if admins need historical
  review beyond the current open queue.
