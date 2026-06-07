# Community Block User Policy

Cycle 68 reserved the data contract for user-to-user blocks on Aura community
surfaces. Cycle 70 adds Android repository support and private block-list
filtering for community feeds and creator profile surfaces. Cycle 71 adds
confirmed block actions on community sound and wallpaper detail surfaces when
the uploader identity is present. Cycle 72 adds a Settings review surface for
blocked creators and one-tap unblock. Callable-backed writes remain follow-up
work.

## Policy

Aura community uploads are publicly browsable UGC, so block controls must be
available before wider store distribution. A block is a private preference by
the signed-in user. It is not a moderation action, does not delete content, and
does not notify the blocked user.

Blocking a user hides that user's community sounds, community wallpapers, and
creator profile dashboard rows from the blocker when a Firebase UID is already
available. Aura does not create a Firebase anonymous identity only to filter a
public feed; users without a Firebase UID see the public feed until they take a
community write action or a visible block/unblock entry point signs them in.

Admin moderation remains separate: reports, takedown receipts, deletion
tombstones, and `/moderation/{contentId}` records continue to carry enforcement
evidence.

## Storage Paths

- `/community_user_blocks/{blockerUid}/{blockedUid}` is the blocker's private
  list. The blocker and custom-claim admins can read it. The blocker or admins
  can create or remove rows.
- `/community_blocked_by/{blockedUid}/{blockerUid}` is an admin-only reverse
  index for abuse review and future cleanup. The blocker or admins can maintain
  the matching reverse row, but regular users cannot read it.

Each row stores:

- `blockerUid`
- `blockedUid`
- `createdAt`
- `reason`

Reasons:

- `SPAM`
- `HARASSMENT`
- `SAFETY`
- `RIGHTS`
- `OTHER`

Rules reject anonymous writes, cross-user writes, mismatched path payloads, and
self-block rows. The current direct RTDB write is a compatibility bridge
exposed through `CommunityBlockRepository`. The Cycle 63 callable contract
includes `setCommunityUserBlock` so block writes can move behind Auth,
App Check, quota, and dedupe ledgers.

## Runtime Behavior

- `CommunityBlockRepository.blockedUserIds()` listens to the signed-in user's
  private block list when a Firebase UID already exists.
- `UploadRepository.getCommunityUploads()` filters blocked uploaders by
  canonical `uploaderUid` and legacy `uploaderId`, then re-emits the current
  upload snapshot when block state changes.
- `WallpaperUploadRepository.getCommunityWallpapers()` filters blocked
  community wallpaper uploaders during fetch.
- `CreatorProfileRepository` removes blocked creators from top creators,
  followed creators, and followed upload lists.
- Community sound and wallpaper models carry `communityUploaderId` from
  canonical `uploaderUid` or legacy `uploaderId` metadata.
- `SoundDetailScreen` and `WallpaperDetailScreen` show confirmed `Block
  creator` actions for blockable community uploads, then remove visible rows
  from the same uploader after the private block write succeeds.
- Settings shows a `Blocked creators` review dialog with blocked uploader IDs,
  reason/timestamp metadata, and per-row unblock actions.
- Block and unblock repository methods maintain both the private list and
  admin reverse index.

## Remaining Work

- Add visible UI entry points from report cards and creator profile surfaces.
- Move block/unblock writes to `setCommunityUserBlock` and then tighten direct
  RTDB writes.
- Decide account-deletion cleanup for outbound blocks and inbound reverse
  indexes.

## Sources

- Google Play User Generated Content policy:
  https://support.google.com/googleplay/android-developer/answer/9876937
- Google Play moderation requirements:
  https://support.google.com/googleplay/android-developer/answer/12923286
- Firebase Realtime Database Android read/write documentation:
  https://firebase.google.com/docs/database/android/read-and-write
- Firebase Realtime Database Security Rules:
  https://firebase.google.com/docs/database/security
