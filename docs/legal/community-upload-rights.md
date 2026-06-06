# Community Upload Rights

Cycle 50 adds explicit upload-rights metadata for community sounds and
wallpapers. Uploaders must confirm they own the file or have permission to
share it before the app writes public community metadata.

## Accepted Licenses

Community uploads currently accept:

- `CC0`
- `CC BY`
- `CC BY-NC`

Legacy community sound rows without stored license metadata still render as
`User Upload` and keep confirmation-required action behavior.

## Required Metadata

Sound and wallpaper uploads now store the same rights fields:

- `uploaderId` and `uploaderUid`
- `uploaderLabel`
- `license`
- `rightsAttested`
- `rightsAttestedAt`
- `sourceUrl`

`sourceUrl` is optional, but when present it must use HTTPS. The app trims the
URL to the database limit before upload, and RTDB rules reject non-HTTPS values.

## Runtime Behavior

- Sound and wallpaper upload dialogs keep the upload action disabled until the
  uploader checks the rights confirmation box.
- Upload repositories validate the selected license and attestation before
  reading or uploading media.
- RTDB rules require the selected license, true attestation, attestation
  timestamp, and authenticated uploader UID for new community records.
- Community sound action gates now use the selected license when present.
  `CC0` and `CC BY` rows follow the normal personal-use sound action matrix;
  `CC BY-NC` requires confirmation for apply/download/edit, and community rows
  remain disabled for Aura Originals until separate curation review.
- Community wallpaper detail surfaces now show the stored license and source
  link when present.

## Remaining Follow-Up

- Add report and takedown actions that include rights, safety, and
  source-removed reasons.
- Add an admin queue that can hide, delete, restore, and record a resolution
  reason without exposing reporter identity.
- Add public copy explaining community visibility and takedown expectations in
  the upload flow once the report queue exists.
