# Community Quota and Rate-Limit Design

Cycle 54 defines the first enforceable quota contract for Aura community writes.
It does not claim production enforcement until the callable backend and Firebase
Console App Check enforcement are deployed.

## Goals

- Keep anonymous community writes useful for real users while making scripted
  abuse expensive.
- Treat Firebase Auth as identity, App Check as app attestation, and backend
  quota ledgers as abuse controls.
- Keep client-side validation for UX, but avoid trusting client-owned counters.
- Preserve the current RTDB metadata schema until callable endpoints are ready.

## Policy Matrix

| Surface | Daily limit | Cooldown | Dedupe key | Enforcement target |
| --- | ---: | ---: | --- | --- |
| Reports | 10 | 2 minutes | content key + reason | App-Checked callable write |
| Sound uploads | 3 | 15 minutes | Storage path | App-Checked callable + Storage rules |
| Wallpaper uploads | 5 | 10 minutes | Storage path | App-Checked callable + Storage rules |
| Votes | 100 | 3 seconds | content ID | App-Checked callable + existing RTDB transaction |
| Follows | 50 | 5 seconds | creator ID | App-Checked callable write |
| Profile edits | 12 | 5 minutes | profile UID | App-Checked callable write |

The matching code contract is `CommunityQuotaPolicies`. Unit tests verify every
required surface has a limit, cooldown, dedupe key, and callable enforcement row.

## Backend Ledgers

Two protected RTDB namespaces are now reserved:

- `/community_write_quotas/{uid}/{yyyyMMdd}/{surface}` stores count,
  first-at, last-at, blocked-count, and last-blocked-at values.
- `/community_write_dedupe/{uid}/{surface}/{dedupeKey}` stores duplicate-action
  markers with creation and expiration timestamps.

`database.rules.json` makes both namespaces admin-only. Regular app clients
must not write them directly, because any client-owned quota counter can be
reset, omitted, or raced by a modified client. The callable backend or a trusted
Admin SDK job should own ledger updates.

## Rollout Order

1. Keep the current direct RTDB/Storage writes while App Check request metrics
   are monitored for Realtime Database, Cloud Storage, and Authentication.
2. Add Cloud Functions callable endpoints for reports, votes, follows, profile
   edits, and upload metadata finalization. Configure each callable with App
   Check enforcement and Firebase Auth checks.
3. In each callable, derive the UID from Firebase Auth, verify the surface
   policy from `CommunityQuotaPolicies`, update the quota ledger transactionally,
   write or reject the community action, and write a dedupe marker when accepted.
4. Add Storage rules before upload enforcement: sounds must stay under 20 MB in
   `sounds/{uid}/...`, wallpapers must stay under 4 MB in
   `wallpapers/{uid}/...`, and MIME types must match the client-side allowlists.
5. Move Android repositories to call the callable endpoints. Keep local
   validation, progress UI, and friendly quota errors.
6. After metrics prove valid clients are sending App Check tokens, enable App
   Check enforcement for RTDB and Cloud Storage.
7. Tighten direct RTDB rules so non-admin clients can no longer write the old
   report, vote, follow, profile, and upload metadata paths directly.

## Verification Plan

- Unit test `CommunityQuotaPolicies` for required surface coverage.
- Run `npm run test:database-rules` after every RTDB rules edit; Cycle 58 covers
  anonymous user, regular user, owner, and custom-claim admin personas for the
  protected quota and dedupe namespaces.
- For each callable, test accepted writes, duplicate writes, cooldown rejections,
  daily-limit rejections, and admin override behavior.
- Manually smoke test debug-provider and release-device flows before toggling
  Firebase Console enforcement.

## Remaining Implementation Work

- Add the callable backend project and wire Android repositories to it.
- Define quota reset timezone in code and backend deployment config.
- Decide whether blocked quota attempts should create private moderation events.
- Continue owner-delete and rights-confirmed takedown flows after Cycle 55's
  new-upload deletion handles and owner indexes.

## Sources

- Firebase App Check metrics: https://firebase.google.com/docs/app-check/monitor-metrics
- App Check for Cloud Functions: https://firebase.google.com/docs/app-check/cloud-functions
- Cloud Functions callable behavior: https://firebase.google.com/docs/functions/callable
- Realtime Database Security Rules: https://firebase.google.com/docs/database/security
- Realtime Database rules API: https://firebase.google.com/docs/reference/security/database
- Cloud Storage Security Rules: https://firebase.google.com/docs/storage/security
