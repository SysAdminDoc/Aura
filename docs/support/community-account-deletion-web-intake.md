# Community Account Deletion Web Intake

This document defines the private hosted intake contract for users who cannot
open Aura but still need to request community account deletion.

The web form must be hosted outside the public repository until the owner has a
production support surface. Treat every raw form export as private support data.
Do not commit raw contact fields, requester statements, full Firebase UIDs,
database exports, RTDB paths, access tokens, executor packages, or REST apply
receipts.

## Required Form Fields

- `requestCode`: the `AURA-` deletion request code copied from Aura Settings or
  a prior support response.
- `contact`: requester email or support reply handle.
- `requesterStatement`: a plain request to delete the Aura community identity
  and associated community data.
- `attestations.deleteCommunityIdentity`: requester confirms the deletion
  request.
- `attestations.understandsRetainedRecords`: requester saw that private
  moderation, rights, safety, and abuse-prevention records may be retained.
- `attestations.understandsPublicUploadsSeparate`: requester saw that public
  uploads and Storage objects may need separate owner/admin deletion handling.
- `submittedAt`: server timestamp from the support surface.
- `channel`: `private-web`.
- `locale`: optional BCP 47 locale or `und`.

## Private Export Shape

```json
{
  "requestCode": "AURA-123456789ABC",
  "contact": "requester@example.com",
  "requesterStatement": "Please delete my Aura community identity and associated community data.",
  "channel": "private-web",
  "submittedAt": "2026-06-06T14:00:00Z",
  "locale": "en-US",
  "attestations": {
    "deleteCommunityIdentity": true,
    "understandsRetainedRecords": true,
    "understandsPublicUploadsSeparate": true
  }
}
```

## Validation

Convert the raw private export into a redacted intake receipt before attaching
it to release/backend evidence:

```powershell
py -3 tools\community_deletion_web_intake.py --request .\web-intake-request.json --support-reference <ticket-id> --output .\web-intake-receipt.json
```

The receipt normalizes the request code, hashes the contact and requester
statement, records the required attestations, and marks the request
`readyForOperatorLookup`.

After that, continue with the private operator flow in
[`community-account-deletion.md`](community-account-deletion.md):

1. Verify the requester privately in the support system.
2. Run `tools/community_deletion_request_lookup.py` against a current RTDB
   export.
3. Build, review, simulate, package, dry-run, apply, and complete the deletion
   only through the guarded account deletion toolchain.

## Hosting Notes

- Publication status lives in
  [`community-account-deletion-web-url.json`](community-account-deletion-web-url.json).
- Publishable page copy lives in
  [`community-account-deletion-web-page.md`](community-account-deletion-web-page.md).
- While the manifest status is `pendingOwnerUrl`, keep `publicUrl` empty and
  keep the privacy policy text marked as pending owner publication.
- Before Play production submission, publish the web resource URL, set the
  manifest status to `published`, and reference the HTTPS URL from both the
  privacy policy and this support intake document.
- Use HTTPS only.
- Rate-limit submissions by IP/contact in the hosting layer.
- Store raw form exports only in the private support system, not in the repo.
- Show the same retained-record and public-upload caveats as the in-app request
  draft.
- Do not ask users to paste full Firebase UIDs into public issues.

Validate the publication manifest after any status or URL change:

```powershell
py -3 tools\community_deletion_web_url_check.py --manifest docs\support\community-account-deletion-web-url.json --repo-root .
```

Validate the hosted page copy before publication:

```powershell
py -3 tools\community_deletion_web_page_check.py --page docs\support\community-account-deletion-web-page.md
```
