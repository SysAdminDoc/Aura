# Community Reporting

Cycle 51 adds the first private report queue for Aura community and mirrored
provider content. Reports are separate from local hide/downvote behavior and
from the existing admin moderation hide list.

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

Only custom-claim admins can read report records or write resolution records.
Reporter UIDs are not public catalog data.

## Current Runtime Behavior

- Sound and wallpaper detail screens expose a report action for remote/provider
  content.
- Report submission requires the Community source switch to be enabled because
  the queue uses the same Firebase-backed community surface.
- ViewModels submit the current source, license, uploader, and HTTPS source URL
  context with each report.
- Admin resolution writes can mark reports hidden, dismissed, or restored with
  resolver UID, timestamp, and note metadata.

## Remaining Follow-Up

- Add an admin review surface that lists open reports, opens source/detail
  context, and performs hide/delete/restore actions.
- Add App Check and quota/rate-limit enforcement before public production
  reliance.
- Wire report resolution to the current `/moderation/{contentId}` hide/unhide
  path and any future owner-delete/takedown flow.
