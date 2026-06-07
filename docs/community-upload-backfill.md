# Community Upload Legacy Backfill

Cycle 66 adds a dry-run plan for legacy community upload rows that predate
`storagePath` and `/owner_uploads` metadata.

## Problem

New uploads store:

- public metadata `storagePath`;
- `uploaderUid`; and
- private `/owner_uploads/{uid}/sounds|wallpapers/{uploadId}` index rows.

Older rows may only have public download URLs and `uploaderId`. Those rows
remain visible, but owner/admin delete actions fail closed because they cannot
prove a canonical Storage object path.

## Backfill Plan Tool

`tools/community_upload_backfill_plan.py` reads a Realtime Database JSON export
and produces a dry-run multi-path update plan. It never writes to Firebase.

Run:

```powershell
py -3 tools\community_upload_backfill_plan.py --database-export database-export.json --output community-upload-backfill-plan.json
```

The planner accepts Firebase Storage download URLs such as:

- `https://firebasestorage.googleapis.com/v0/b/<bucket>/o/sounds%2Fuid%2Ffile.mp3?...`
- `https://storage.googleapis.com/<bucket>/wallpapers/uid/file.jpg`
- direct stored paths such as `sounds/uid/file.mp3`

## Candidate Requirements

A row becomes a backfill candidate only when all of these are true:

- `storagePath` is currently blank.
- A supported URL field can be parsed into `sounds/...` for sound rows or
  `wallpapers/...` for wallpaper rows.
- `uploaderUid`, `uploaderId`, or `ownerUid` is present.
- `uploadedAt` or `createdAt` is present and positive.

The generated update plan adds:

- `/{community_root}/{uploadId}/storagePath`
- `/{community_root}/{uploadId}/uploaderUid` when only `uploaderId` existed
- `/owner_uploads/{uid}/{sounds|wallpapers}/{uploadId}` owner-index payload

Rows are blocked when the path cannot be derived, the path points at the wrong
prefix, the owner UID is missing, the timestamp is missing, or the metadata row
is malformed.

## Apply Gate

Before applying a generated plan:

1. Run `npm run test:firebase-rules`.
2. Run `py -3 -m unittest discover -s test/tools -p '*_test.py'`.
3. Confirm the plan was generated from a fresh production RTDB export.
4. Sample at least five candidates and manually verify that their `downloadUrl`
   opens the same Storage object path in Firebase Console.
5. Apply only the generated multi-location updates from the `updates` objects.
6. Re-run the orphan report from
   [`docs/community-storage-lifecycle-policy.md`](community-storage-lifecycle-policy.md)
   and verify newly backfilled rows no longer appear as legacy rows.
7. Archive the input export hash, plan hash, operator, timestamp, and Firebase
   project ID in the backend evidence packet.

## Remaining Work

- Add a trusted writer for applying reviewed plans when owner access and service
  account handling are available.
- Decide deletion retention/tombstone behavior for removed upload metadata,
  votes, reports, moderation rows, and takedown receipts.

## Sources

- Firebase Realtime Database Android read/write/delete:
  https://firebase.google.com/docs/database/android/read-and-write
- Firebase DatabaseReference API:
  https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference
- Firebase Storage Android delete files:
  https://firebase.google.com/docs/storage/android/delete-files
