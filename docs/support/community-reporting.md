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
- Report quota policy is defined in
  [`docs/community-quota-rate-limits.md`](../community-quota-rate-limits.md):
  10 reports per UID per day, 2-minute cooldown, and content-key/reason dedupe
  once App-Checked callable enforcement is deployed.

## Remaining Follow-Up

- App Check client providers and the report quota policy are tracked, but
  Firebase console enforcement and callable backend quota enforcement still need
  the backend rollout pass before public production reliance.
- Add owner-delete/takedown flows for rights-confirmed removals and public
  takedown request copy.
- Add report tabs or filters for closed reports if admins need historical
  review beyond the current open queue.
