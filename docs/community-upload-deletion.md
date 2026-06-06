# Community Upload Deletion Handles

Cycle 55 made new community uploads deletable without parsing public download
URLs. Cycle 56 adds owner-visible delete actions for new sound and wallpaper
uploads that have those handles. Cycle 57 adds tracked Storage rules and
emulator coverage for owner-only blob writes/deletes. Legacy backfill, lifecycle
cleanup, and admin takedown UX remain follow-up work.

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
6. remove both public metadata and the private owner-index row with one RTDB
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

## Remaining Work

- Add admin rights-confirmed takedown actions that write a private resolution
  reason before deleting or hiding public content.
- Add a backfill/admin script for older upload rows that lack `storagePath` and
  `/owner_uploads` entries.
- Add Realtime Database Emulator Suite tests for metadata/index deletion and
  cross-owner rejection.
- Decide whether votes, report records, and moderation audit rows are deleted,
  retained, or tombstoned when an upload is removed.

## Sources

- Google Play account deletion requirements: https://support.google.com/googleplay/android-developer/answer/13327111
- Firebase Realtime Database Android delete data: https://firebase.google.com/docs/database/android/read-and-write
- Firebase DatabaseReference API: https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference
- Firebase Storage Android delete files: https://firebase.google.com/docs/storage/android/delete-files
