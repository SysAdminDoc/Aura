# Community Callable Quota Enforcement

Cycle 63 turns the Cycle 54 quota policy into a backend contract for callable
functions. Cycle 93 adds the first checked Cloud Functions project scaffold,
Cycle 94 adds the first handler-backed callable for community reports, and
Cycle 95 adds the handler-backed vote callable. Cycle 96 adds the
handler-backed creator follow callable and refines follow dedupe to include the
desired state. Cycle 97 adds the handler-backed user block callable and
refines user-block dedupe to include the desired state. Cycle 98 adds the
handler-backed sound upload finalizer. Cycle 99 adds the handler-backed
wallpaper upload finalizer. Cycle 100 adds the handler-backed profile edit
callable and refines profile dedupe to include the normalized public profile
hash. Cycle 108 adds the first Android callable migration adapter for report
submission, Cycle 109 adds the Android vote callable migration adapter, Cycle
110 adds the Android follow callable migration adapter, Cycle 111 adds the
Android user-block callable migration adapter, Cycle 112 adds the Android
sound upload finalizer callable migration adapter, Cycle 113 adds the Android
wallpaper upload finalizer callable migration adapter, and Cycle 114 adds the
Android profile edit callable migration adapter. Cycle 115 adds the checked
Android callable wire-protocol manifest. Every exported callable now has a
handler core, Android client adapter, and machine-checked Android envelope
coverage, while production enforcement still waits for owner-approved deploy
evidence, App Check console evidence, live callable invocation evidence, and
direct RTDB rule tightening.

## Contract Source

`CommunityQuotaPolicies` is the code-backed policy table. Each row now defines:

- `surfaceKey` for the protected quota and dedupe ledgers.
- Daily limit, cooldown, and dedupe-key source.
- Required enforcement layers.
- Callable function name, payload schema, final write paths, and limited-use
  App Check token decision.

Unit tests keep all community write surfaces covered and fail if a callable
contract loses auth, App Check, ledger, or final-write coverage.

`docs/community-callable-contract.json` is the backend-facing manifest for the
same contract. It pins the quota day boundary to UTC and is validated by:

```powershell
py -3 tools\community_callable_contract_check.py --contract docs\community-callable-contract.json
```

`functions/src/communityContract.ts` mirrors that manifest for the Node 20
Functions project. `functions/test/communityContract.test.cjs` fails if the
Functions contract drifts from `docs/community-callable-contract.json`.

`docs/community-callable-wire-protocol.json` is the Android-facing manifest for
the callable client wire protocol. It maps each contracted surface to its
Android client method, backend payload schema, Android input type, payload
builder, operation-ID prefix, resource-ID response field, and App Check token
selection. It is validated by:

```powershell
py -3 tools\community_callable_wire_protocol_check.py --contract docs\community-callable-contract.json --protocol docs\community-callable-wire-protocol.json
```

## Callable Matrix

| Surface | Callable | Payload | Final writes | Limited-use token |
| --- | --- | --- | --- | --- |
| Reports | `submitCommunityReport` | `CommunityReportInput` | `/community_reports/{reportId}` | Yes |
| Sound uploads | `finalizeCommunitySoundUpload` | `CommunitySoundUploadMetadata` | `/community_sounds/{uploadId}`, `/owner_uploads/{uid}/sounds/{uploadId}` | Yes |
| Wallpaper uploads | `finalizeCommunityWallpaperUpload` | `CommunityWallpaperUploadMetadata` | `/community_wallpapers/{uploadId}`, `/owner_uploads/{uid}/wallpapers/{uploadId}` | Yes |
| Votes | `recordCommunityVote` | `CommunityVoteInput` | `/votes/{contentId}`, `/voters/{contentId}/{uid}` | No |
| Follows | `setCreatorFollow` | `CommunityFollowInput` | `/creator_follows/{uid}/{creatorId}` | No |
| User blocks | `setCommunityUserBlock` | `CommunityUserBlockInput` | `/community_user_blocks/{uid}/{blockedUid}`, `/community_blocked_by/{blockedUid}/{uid}` | No |
| Profile edits | `updateCreatorProfile` | `CreatorProfileUpdateInput` | `/creator_profiles/{uid}` | No |

Every callable also owns these protected ledgers for its surface:

- `/community_write_quotas/{uid}/{yyyyMMdd}/{surface}`
- `/community_write_dedupe/{uid}/{surface}/{dedupeKey}`

## Functions Scaffold Status

Cycle 93 added:

- `functions/package.json` with Node 20, `firebase-functions` 7.2.5,
  `firebase-admin` 13.10.0, and TypeScript 5.9.3.
- `functions/src/index.ts` exports all seven contracted callables with
  `enforceAppCheck` and per-surface `consumeAppCheckToken` options.
- `functions/src/callableScaffold.ts` requires Firebase Auth and App Check, then
  returns `failed-precondition` while write handlers are pending.
- `functions/src/quotaEngine.ts` implements pure UTC quota-day, cooldown,
  daily-limit, duplicate, accepted-state, blocked-state, and dedupe-marker
  decisions.
- `functions/test/*.test.cjs` covers manifest sync, runtime App Check options,
  limited-use token choices, UTC day boundaries, duplicate handling, cooldown,
  and daily-limit decisions.

Cycle 94 added:

- `functions/src/reportHandler.ts` implements `submitCommunityReport` handler
  logic with server-derived reporter UID, envelope validation, report payload
  normalization, HTTPS source URL validation, UTC quota reservation, duplicate
  handling, and final `/community_reports/{reportId}` plus dedupe-marker writes.
- `functions/test/submitCommunityReport.test.cjs` covers accepted, duplicate,
  cooldown, daily-limit, unauthenticated, missing-App-Check, reporter-override,
  and insecure-source-URL cases.

Cycle 95 added:

- `functions/src/voteHandler.ts` implements `recordCommunityVote` handler logic
  with content ID normalization, existing nested/legacy voter-marker
  idempotency before quota reservation, UTC quota checks, dedupe handling, vote
  tally transactions, and legacy voter-marker mirroring.
- `functions/test/recordCommunityVote.test.cjs` covers accepted,
  existing-voter duplicate, active-dedupe duplicate, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid-content-ID cases.

Cycle 96 added:

- `functions/src/followHandler.ts` implements `setCreatorFollow` handler logic
  with follow/unfollow payload normalization, server-derived follower UID,
  no-op state idempotency before quota reservation, action-specific dedupe keys,
  UTC quota checks, and final follow row set/remove writes.
- `functions/test/setCreatorFollow.test.cjs` covers accepted follow, accepted
  unfollow, no-op duplicates, same-state dedupe, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid payload cases.

Cycle 97 added:

- `functions/src/blockHandler.ts` implements `setCommunityUserBlock` handler
  logic with block/unblock payload normalization, server-derived blocker UID,
  self-block rejection, no-op state idempotency before quota reservation,
  action-specific dedupe keys, UTC quota checks, and private block plus admin
  reverse-index set/remove writes.
- `functions/test/setCommunityUserBlock.test.cjs` covers accepted block,
  accepted unblock, no-op duplicates, same-state dedupe, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid payload cases.

Cycle 98 added:

- `functions/src/soundUploadHandler.ts` implements
  `finalizeCommunitySoundUpload` handler logic with server-derived uploader
  UID, server-allocated upload IDs, sound metadata normalization, Storage path
  ownership checks under `sounds/{uid}/...`, HTTPS URL validation, UTC quota
  checks, storage-path dedupe, and final public metadata plus owner-index
  writes.
- `functions/test/finalizeCommunitySoundUpload.test.cjs` covers accepted
  finalization, active storage-path dedupe, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid ownership/payload cases.

Cycle 99 added:

- `functions/src/wallpaperUploadHandler.ts` implements
  `finalizeCommunityWallpaperUpload` handler logic with server-derived uploader
  UID, server-allocated upload IDs, wallpaper metadata normalization, Storage
  path ownership checks under `wallpapers/{uid}/...`, HTTPS URL validation,
  dimension/file-size/file-type checks, UTC quota checks, storage-path dedupe,
  and final public metadata plus owner-index writes.
- `functions/test/finalizeCommunityWallpaperUpload.test.cjs` covers accepted
  finalization, active storage-path dedupe, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid ownership/payload cases.

Cycle 100 added:

- `functions/src/profileHandler.ts` implements `updateCreatorProfile` handler
  logic with server-derived profile UID, public display copy normalization,
  HTTPS URL validation, server-assigned `createdAt`/`updatedAt` timestamps,
  identical-profile idempotency before quota reservation, UTC quota checks,
  normalized-profile dedupe, and final `/creator_profiles/{uid}` writes.
- `functions/test/updateCreatorProfile.test.cjs` covers accepted update,
  identical-profile duplicate, active normalized-profile dedupe, cooldown,
  daily-limit, unauthenticated, missing-App-Check, UID/timestamp override, and
  invalid public-copy cases.

Do not claim production callable enforcement until all callable surfaces have
owner-approved deploy evidence, live callable invocation evidence, Firebase
Console App Check evidence, and direct RTDB rule tightening.

## Android Client Status

Cycle 108 added:

- `firebase-functions` under the existing Firebase BoM.
- `CommunityCallableClient` and a Firebase-backed invoker that call named
  HTTPS callables through `FirebaseFunctions`.
- Limited-use App Check token selection for report submission, derived from
  `CommunityQuotaPolicies.reports`.
- `buildCommunityReportCallablePayload()` so Android omits server-owned
  reporter UID, report timestamp, report status, and report key fields.
- Callable-first `CommunityReportRepository.submitReport()` with a direct RTDB
  compatibility fallback while the callable endpoint is not yet deployed.

Cycle 109 added:

- `CommunityVoteInput` payload normalization for Android vote callable
  requests.
- `CommunityCallableClient.recordCommunityVote()` using
  `CommunityQuotaPolicies.votes`.
- Callable-first `VoteRepository.upvote()` when Firebase Auth is available,
  with direct RTDB fallback only for missing callable endpoint or missing Auth
  compatibility.

Cycle 110 added:

- `CommunityFollowInput` payload normalization for Android follow and unfollow
  callable requests.
- `CommunityCallableClient.setCreatorFollow()` using
  `CommunityQuotaPolicies.follows`.
- Callable-first `CreatorProfileRepository.followCreator()` and
  `unfollowCreator()` when Firebase Auth is available, with direct RTDB
  fallback only for missing callable endpoint or missing Auth compatibility.

Cycle 111 added:

- `CommunityUserBlockInput` payload normalization for Android block and unblock
  callable requests.
- `CommunityCallableClient.setCommunityUserBlock()` using
  `CommunityQuotaPolicies.userBlocks`.
- Callable-first `CommunityBlockRepository.blockUser()` and `unblockUser()`
  when Firebase Auth is available, with direct RTDB fallback only for missing
  callable endpoint or missing Auth compatibility.

Cycle 112 added:

- `CommunitySoundUploadMetadataInput` payload normalization for Android sound
  upload finalizer callable requests.
- `CommunityCallableClient.finalizeCommunitySoundUpload()` using
  `CommunityQuotaPolicies.soundUploads` and limited-use App Check tokens.
- Callable-first `UploadRepository.uploadSound()` metadata finalization after
  Storage upload when Firebase Auth is available, with direct RTDB fallback
  only for missing callable endpoint or missing Auth compatibility.

Cycle 113 added:

- `CommunityWallpaperUploadMetadataInput` payload normalization for Android
  wallpaper upload finalizer callable requests.
- `CommunityCallableClient.finalizeCommunityWallpaperUpload()` using
  `CommunityQuotaPolicies.wallpaperUploads` and limited-use App Check tokens.
- Callable-first `WallpaperUploadRepository.uploadWallpaper()` metadata
  finalization after Storage upload when Firebase Auth is available, with
  direct RTDB fallback only for missing callable endpoint or missing Auth
  compatibility.

Cycle 114 added:

- `CreatorProfileUpdateInput` payload normalization for Android profile edit
  callable requests.
- `CommunityCallableClient.updateCreatorProfile()` using
  `CommunityQuotaPolicies.profileEdits`.
- Callable-first `CreatorProfileRepository.updateCreatorProfile()` when
  Firebase Auth is available, with direct RTDB fallback only for missing
  callable endpoint or missing Auth compatibility.
- Creator profile screen edit UI that uses the repository update path and keeps
  local dashboard state in sync after a successful save.

Cycle 115 added:

- `docs/community-callable-wire-protocol.json` as the checked Android callable
  wire-protocol manifest for all seven contracted community write surfaces.
- `tools/community_callable_wire_protocol_check.py` to fail drift between the
  backend callable contract and Android client method names, input types,
  quota-policy accessors, payload builders, shared request envelope,
  operation-ID prefixes, response resource-ID mappings, App Check token
  choices, and focused client tests.
- Backend CI execution for the wire-protocol guard before the broader backend
  tool test sweep.

Report, vote, follow, user-block, sound upload finalization, wallpaper upload
finalization, and profile edit writes are the Android write surfaces with
callable client code and checked Android wire-protocol coverage today.

## Request Envelope

All callable requests use a common envelope:

| Field | Source | Rule |
| --- | --- | --- |
| `operationId` | Client-generated UUID | Required for logs and retry correlation; not trusted for quota identity. |
| `clientSentAt` | Client wall clock | Informational only; server time owns ledgers. |
| `payload` | Surface-specific object | Normalized and revalidated by the callable. |

The backend derives `uid` from Firebase Authentication. It must reject payloads
that try to override the authenticated UID, uploader UID, reporter UID, follower
UID, profile UID, or owner index UID unless the caller has an admin claim.

## Backend Sequence

1. Require Firebase Auth and App Check on every callable.
2. Use limited-use App Check token consumption for reports and upload
   finalizers. Those surfaces publish or create moderation records and are
   lower-volume than votes/follows/user blocks/profile edits.
3. Normalize the payload with the same bounds used by Android and Firebase
   rules.
4. Derive the policy row from `surfaceKey`; never accept a policy or limit from
   the client.
5. Derive the dedupe key server-side.
6. Transactionally inspect `/community_write_dedupe/{uid}/{surface}/{dedupeKey}`.
   If an unexpired marker exists, return a duplicate/idempotent response before
   writing the public action.
7. Transactionally update `/community_write_quotas/{uid}/{yyyyMMdd}/{surface}`.
   Reject when the daily limit would be exceeded or when `lastAt` is inside the
   cooldown window. Increment `blockedCount` and set `lastBlockedAt` for blocked
   attempts.
8. Write the public action and any private owner index in one Admin SDK
   multi-location update when the surface needs more than one path.
9. Write the dedupe marker with `createdAt`, `expiresAt`, and `target`.
10. Return a small result object with `status`, `targetPath`, `retryAfterMillis`
    when blocked, and the server timestamp used for the write.

## Error Codes

| Code | Meaning |
| --- | --- |
| `UNAUTHENTICATED` | Missing Firebase Auth. |
| `FAILED_PRECONDITION` | Missing or invalid App Check. |
| `PERMISSION_DENIED` | Authenticated caller cannot write the requested owner/admin path. |
| `INVALID_ARGUMENT` | Payload fails normalization or bounds checks. |
| `RESOURCE_EXHAUSTED` | Daily limit or cooldown blocks the write. |
| `ALREADY_EXISTS` | Dedupe marker proves an equivalent write already exists. |
| `ABORTED` | Transaction conflict exceeded backend retry budget. |

Android repositories should map `RESOURCE_EXHAUSTED` to quota copy that includes
the retry window, and treat `ALREADY_EXISTS` as a non-destructive duplicate
response when the server provides an existing target.

## Android Migration

1. Add the Cloud Functions client dependency under the existing Firebase BoM.
2. Add a small repository adapter for the envelope, result object, and quota
   error mapping.
3. Migrate reports first because reports do not require a binary upload
   progress flow.
4. Keep local optimistic UI state on profile edits while the callable owns the
   server write.
5. Migrate wallpaper upload metadata finalization after Storage rules and owner
   indexes are verified. Storage upload bytes still go through Firebase
   Storage; the callable owns the public metadata and owner-index final write.
6. After each surface is callable-backed, tighten direct RTDB writes for that
   path to owner/admin migration exceptions or admin-only writes.

## Verification

- Run `CommunityQuotaPolicyTest` after contract edits.
- Run `tools/community_callable_contract_check.py` after any callable contract
  or deployment-manifest edit.
- Run `tools/community_callable_wire_protocol_check.py` after any callable
  contract or Android callable-client edit.
- Run `npm --prefix functions test` after any Functions source or contract edit.
- Run `npm run test:functions-emulator` after any emulator-backed handler
  persistence test or root backend script edit.
- Add callable unit tests for accepted, duplicate, cooldown, daily-limit, and
  unauthorized writes for every surface.
- Add Emulator Suite tests before direct RTDB rules are tightened.
- Smoke test debug-provider and signed release-device App Check behavior before
  console enforcement.
- Record every callable deployment and rollback command in the Firebase
  deploy/rollback runbook when the functions project is added.

## Sources

- Firebase App Check overview: https://firebase.google.com/docs/app-check
- App Check for Cloud Functions: https://firebase.google.com/docs/app-check/cloud-functions
- Callable Cloud Functions: https://firebase.google.com/docs/functions/callable
- Realtime Database Security Rules: https://firebase.google.com/docs/database/security
