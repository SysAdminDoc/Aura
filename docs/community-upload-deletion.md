# Community Upload Deletion Handles

Cycle 55 made new community uploads deletable without parsing public download
URLs. Cycle 56 adds owner-visible delete actions for new sound and wallpaper
uploads that have those handles. Cycle 57 adds tracked Storage rules and
emulator coverage for owner-only blob writes/deletes. Cycle 58 adds RTDB
emulator coverage for public metadata and owner-index delete authorization.
Cycle 60 adds private admin rights-confirmed takedown receipts for new rows with
deletion handles. Cycle 61 adds an admin delete action that consumes those
handles, removes the Storage object plus metadata rows, and records retry state.
Cycle 65 adds the Storage lifecycle/orphan cleanup policy and offline orphan
report tool. Cycle 66 adds a dry-run legacy backfill plan for rows missing
`storagePath` and owner indexes. Cycle 67 adds private deletion tombstones and a
retention policy for removed uploads.

## New Metadata

New sound uploads write:

- `/community_sounds/{uploadId}/storagePath`
- `/owner_uploads/{uid}/sounds/{uploadId}`

New wallpaper uploads write:

- `/community_wallpapers/{uploadId}/storagePath`
- `/owner_uploads/{uid}/wallpapers/{uploadId}`

The public metadata paths remain readable by everyone. The owner index is
private to the uploader and custom-claim admins.

## Delete Behavior

`UploadRepository.deleteSoundUpload(uploadId)` and
`WallpaperUploadRepository.deleteWallpaperUpload(uploadId)` now:

1. resolve the current Firebase UID;
2. load the upload metadata;
3. require the metadata owner to match the current UID;
4. require a nonblank `storagePath`;
5. delete the Firebase Storage object, treating already-missing objects as
   removable; and
6. remove both public metadata and the private owner-index row while writing a
   private `/community_upload_deletions/{publicId}` tombstone with one RTDB
   multi-location update.

This prevents new owner deletes from depending on public download URLs or broad
database scans.

## Visible Owner Actions

`UploadRepository.canDeleteSoundUpload(uploadId)` and
`WallpaperUploadRepository.canDeleteWallpaperUpload(uploadId)` now check the
current owner and require a nonblank `storagePath`. Detail screens only show
delete actions when these probes return true.

Sound details show an owner-only `Delete upload` button below the report action.
Wallpaper details show an owner-only `Delete upload` action in the More sheet.
Both actions require confirmation before calling the repository delete method.

Rows without Cycle 55 deletion handles, rows owned by another UID, and rows that
cannot be read all fail closed and do not show the delete action.

## Admin Takedown Receipts

When an admin hides a `RIGHTS` report for a community sound or wallpaper, the
report resolver reads the current public upload metadata row and writes
`/community_takedown_receipts/{reportId}` only when it can record a matching
`metadataPath`, `storagePath`, and uploader UID. Realtime Database rules require
that receipt storage handles still match the current upload metadata row, and
only custom-claim admins can read or write receipts.

The current admin action hides content through the existing moderation list and
records the receipt. Admins can also use `Delete upload` on qualifying rights
reports. That action writes a `DELETE` receipt with `deleteState = STARTED`,
deletes the Storage object, removes public metadata plus the private owner index,
writes a private deletion tombstone, then updates the receipt to `SUCCEEDED`. If
Storage or metadata deletion fails, the receipt is updated to `FAILED` with
failure stage, timestamp, and bounded failure text so the case can be retried
without losing the handle evidence.

## Retention Policy

[`docs/community-deletion-retention-policy.md`](community-deletion-retention-policy.md)
defines what remains after owner deletes and admin takedowns. Public metadata and
Storage objects are removed immediately. Private deletion tombstones, reports,
resolutions, and takedown receipts remain admin-only. Vote aggregates and voter
markers are retained until the callable/admin cleanup pass can migrate vote
markers away from public read paths.

## Remaining Work

- Use [`docs/community-upload-backfill.md`](community-upload-backfill.md) to
  generate and review dry-run updates for older upload rows that lack
  `storagePath` and `/owner_uploads` entries.
- Migrate vote marker privacy and account-deletion semantics before claiming
  deleted upload vote data is minimized.
- Run the two-report orphan cleanup gate from
  [`docs/community-storage-lifecycle-policy.md`](community-storage-lifecycle-policy.md)
  before any manual Storage deletion outside owner/admin app flows.

## Sources

- Google Play account deletion requirements: https://support.google.com/googleplay/android-developer/answer/13327111
- Firebase Realtime Database Android delete data: https://firebase.google.com/docs/database/android/read-and-write
- Firebase DatabaseReference API: https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference
- Firebase Storage Android delete files: https://firebase.google.com/docs/storage/android/delete-files
