# Community Block User Policy

Cycle 68 reserves the data contract for user-to-user blocks on Aura community
surfaces. UI filtering and callable-backed writes remain follow-up work, but
the rules and policy now define where block state lives and who can see it.

## Policy

Aura community uploads are publicly browsable UGC, so block controls must be
available before wider store distribution. A block is a private preference by
the signed-in user. It is not a moderation action, does not delete content, and
does not notify the blocked user.

When the UI is wired, blocking a user should hide that user's community sounds,
wallpapers, creator profile entry points, follow suggestions, and future direct
creator interactions from the blocker. Admin moderation remains separate:
reports, takedown receipts, deletion tombstones, and `/moderation/{contentId}`
records continue to carry enforcement evidence.

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
self-block rows. The current direct RTDB write is a compatibility bridge; the
Cycle 63 callable contract now includes `setCommunityUserBlock` so block writes
can move behind Auth, App Check, quota, and dedupe ledgers.

## Remaining Work

- Add repository and UI entry points from report cards, creator profiles, and
  upload detail sheets.
- Filter community feeds, creator profile surfaces, and follow suggestions by
  the signed-in user's private block list.
- Move block/unblock writes to `setCommunityUserBlock` and then tighten direct
  RTDB writes.
- Decide account-deletion cleanup for outbound blocks and inbound reverse
  indexes.

## Sources

- Google Play User Generated Content policy:
  https://support.google.com/googleplay/android-developer/answer/9876937
- Google Play moderation requirements:
  https://support.google.com/googleplay/android-developer/answer/12923286
- Firebase Realtime Database Security Rules:
  https://firebase.google.com/docs/database/security
