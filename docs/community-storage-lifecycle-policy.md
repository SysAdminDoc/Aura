# Community Storage Lifecycle and Orphan Cleanup Policy

Cycle 65 defines how Aura handles abandoned community upload blobs without
risking valid public content.

## Current Storage Shape

Committed community upload blobs live under:

- `sounds/{uid}/...`
- `wallpapers/{uid}/...`

The matching public metadata rows are:

- `/community_sounds/{uploadId}/storagePath`
- `/community_wallpapers/{uploadId}/storagePath`

Owner indexes under `/owner_uploads/{uid}/sounds|wallpapers/{uploadId}` are
private lookup accelerators, not the source of truth for public blob retention.

## Lifecycle Decision

Do not attach an automatic Cloud Storage lifecycle `Delete` rule to
`sounds/` or `wallpapers/` while committed uploads and upload-in-progress blobs
share those prefixes. Prefix-and-age lifecycle rules cannot prove whether a blob
has public metadata, and Cloud Storage lifecycle actions are asynchronous.

The current safe policy is:

1. Owner/admin deletes remove Storage objects immediately through app/admin code.
2. Metadata-save failures attempt immediate best-effort blob cleanup in the
   upload repositories.
3. Operators use an offline orphan report before any manual Storage deletion.
4. Automatic lifecycle delete rules can be introduced only after uploads use a
   dedicated temporary prefix such as `tmp/community_uploads/{uid}/...` that is
   never public-read and never referenced by committed metadata rows.

## Orphan Report

`tools/community_storage_orphan_report.py` compares an exported list of Storage
object names with an exported Realtime Database JSON snapshot. It reports:

- managed Storage objects under `sounds/` or `wallpapers/`;
- public metadata rows that reference missing Storage objects;
- Storage objects under managed prefixes that no metadata row references;
- legacy metadata rows missing `storagePath`;
- metadata rows with the wrong Storage prefix; and
- unmanaged Storage objects outside Aura community prefixes.

Input schema:

```json
{
  "objects": [
    { "name": "sounds/user1/example.mp3" },
    { "name": "wallpapers/user1/example.jpg" }
  ]
}
```

The Storage input can also be a plain JSON string array of object names. The
database export should include `community_sounds` and `community_wallpapers`
objects with the same shape as the Realtime Database export.

Run:

```powershell
py -3 tools\community_storage_orphan_report.py --storage-objects storage-objects.json --database-export database-export.json --output community-storage-orphans.json
```

## Deletion Gate

Manual orphan deletion requires:

- two orphan reports from the same Firebase project at least 24 hours apart;
- the same object path appears in `orphanCandidates` in both reports;
- no matching `/community_sounds` or `/community_wallpapers` metadata row exists;
- no pending takedown/delete receipt references the object path;
- the object path starts with `sounds/` or `wallpapers/`; and
- the operator records the before/after report hashes in the backend evidence
  packet.

Objects listed in `metadataWithMissingObject` are not deleted by this process.
They are metadata repair, owner-notification, or public-feed removal cases.

Rows listed in `legacyRowsMissingStoragePath` require backfill or manual review.
Rows listed in `invalidMetadataStoragePaths` require metadata repair before
owner/admin delete actions can rely on them.

## Future Temporary Prefix

When upload repositories move to a callable finalization flow, add a non-public
temporary prefix:

- `tmp/community_uploads/sounds/{uid}/...`
- `tmp/community_uploads/wallpapers/{uid}/...`

Only after that migration, attach a lifecycle rule that deletes objects under
`tmp/community_uploads/` after a short age window such as two days. Do not use
that rule for committed `sounds/` or `wallpapers/` objects.

## Sources

- Cloud Storage lifecycle management: https://docs.cloud.google.com/storage/docs/lifecycle
- Cloud Storage deleting objects: https://docs.cloud.google.com/storage/docs/deleting-objects
- Firebase Storage Android delete files: https://firebase.google.com/docs/storage/android/delete-files
