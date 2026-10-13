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
Android callable wire-protocol manifest, and Cycle 116 adds the redacted live
callable rollout receipt gate. Every exported callable now has a handler core,
Android client adapter, machine-checked Android envelope coverage, and a
checked receipt format for future live invocation evidence, while production
enforcement still waits for owner-approved deploy evidence, App Check console
evidence, actual live callable invocation evidence, and direct RTDB rule
tightening.

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

`functions/src/communityContract.ts` mirrors that manifest for the Node 22
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
| Votes | `recordCommunityVote` | `CommunityVoteInput` | `/vote_markers/{uid}/{contentId}`, `/vote_locks/{uid}/{contentId}`, `/vote_counts/{contentId}`, and the upload row's `votes` field for community uploads | No |
| Follows | `setCreatorFollow` | `CommunityFollowInput` | `/creator_follows/{uid}/{creatorId}` | No |
| User blocks | `setCommunityUserBlock` | `CommunityUserBlockInput` | `/community_user_blocks/{uid}/{blockedUid}`, `/community_blocked_by/{blockedUid}/{uid}` | No |
| Profile edits | `updateCreatorProfile` | `CreatorProfileUpdateInput` | `/creator_profiles/{uid}` | No |
| Collection shares | `publishSharedCollection` | `SharedCollectionInput` | `/shared_collections/{token}` | Yes |

Every callable also owns these protected ledgers for its surface:

- `/community_write_quotas/{uid}/{yyyyMMdd}/{surface}`
- `/community_write_dedupe/{uid}/{surface}/{dedupeKey}`

## Functions Scaffold Status

Cycle 93 added:

- `functions/package.json` with Node 22, `firebase-functions` 7.2.5,
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

The vote schema was later split so voter UIDs never sit in a public tree:

- `/vote_counts/{contentId}/upvotes` is the public count. Anyone can read one
  row, and the collection answers only the `orderByChild('upvotes')` leaderboard
  query with `limitToLast` of 200 or less.
- `/vote_markers/{uid}/{contentId}` records who voted. Only that account (or an
  admin) can read it.
- The callable takes a private lock at `/vote_locks/{uid}/{contentId}` in a
  transaction, so a second call from the same account for the same item is
  turned away while the first is counted. The marker, the `ServerValue.increment`
  on the count, and the lock removal then land in one atomic update. A run that
  dies before that update leaves no marker, so the vote can be cast again once
  the lock's 5 minute lease runs out.
- A missing count row is seeded first from the legacy `/votes/{contentId}/upvotes`
  value, never below the number of distinct legacy voters. The seed transaction
  leaves any whole-number row alone. Community uploads also get the count
  mirrored into their own `votes` field, which the upload feeds sort by. The
  mirror is one transaction on the upload row and skips a row that is gone, so
  a deleted upload can't come back as a stub.
- `seedLegacyVoteCounts` runs every 24 hours (UTC) and gives every content ID
  under `/votes` or `/voters` a count row through that same seed transaction, so
  it and the callable can run in either order without losing a vote. It walks
  each root in key order and saves the last key it finished under the
  admin-only `/vote_seed_cursor/{root}`, so a run that hits its batch cap or its
  8 minute budget (inside the 9 minute function timeout) picks up there next
  time. A root walked to the end is marked done so later runs go straight to
  the other one, and once both are done the cursor is cleared for a fresh pass.
- `/votes` and `/voters` are admin-only now, except the old per-item
  `/votes/{contentId}/upvotes` leaf, which stays readable for older app builds.
  `tools/community_vote_privacy_backfill.py` turns a database export into the
  one-time multi-path update that copies legacy voter markers across. It never
  writes counts, since an absolute count from an export would overwrite votes
  cast after it, and `--drop-legacy` refuses while any legacy content in the
  export still lacks a count row.

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

Storage attestation (2026-10-13):

- `functions/src/storageAttestation.ts` binds a finalize request to the object in Storage before anything is published. A missing object fails closed, and so does missing metadata (bucket, content type, size or download tokens). The stored content type has to be on the allowed list and equal the declared one. The stored size has to equal the declared `fileSize` and stay under the limit. The declared download URL has to name the same bucket and object on the Storage host and carry one of the object's own download tokens, and the public row gets a URL the server rebuilt from those values, never the client's spelling.
- Sound finalize requests now carry a required `fileSize`, which the app counts from the bytes it read. Wallpaper uploads accept only `image/jpeg`, and the finalizer reads width and height from the stored JPEG's frame header (the first 256 KiB, by ranged download), so the published dimensions are the file's own. The app declares the size it actually encoded, which matters when it shrinks a wallpaper to get under 4 MB.
- Each refusal carries a `details.reason`: `object-missing`, `metadata-unavailable`, `content-type-mismatch`, `size-out-of-range`, `size-mismatch`, `download-url-mismatch`, `download-token-unknown`, `jpeg-unreadable` or `dimensions-mismatch`. `functions/test/storageAttestation.test.cjs` and the two finalizer suites cover each one and check that no public row was written; the emulator suites in `test/firebase/` add the size and dimension refusals against a real Storage emulator.

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

Collection share links moved behind a callable too:

- `functions/src/collectionShareHandler.ts` implements `publishSharedCollection`.
  The app sends the exported collection JSON as `document` plus a display name.
  The server parses it, refuses anything that isn't a version 1 document with
  1 to 250 item objects inside 512 KB of UTF-8, counts the items itself, picks a
  random 128-bit token, and writes `createdByUid`, `createdAt`, and an
  `expiresAt` 30 days out. Callers can't send any of those fields. Shares cost a
  limited-use App Check token, 10 a day with a 30 second cooldown.
- `pruneExpiredSharedCollections` runs every 24 hours (UTC) and deletes shares
  whose `createdAt` is at least 30 days old, oldest first, in batches of 500.
  The rules index `shared_collections` on `createdAt` for that query, and they
  already refuse reads of an expired share, so the job only reclaims space.
- `functions/test/publishSharedCollection.test.cjs` covers the accepted write,
  replay, cooldown, daily limit, missing Auth or App Check, refused fields and
  sizes, name fallback, and the refund on a failed write.
  `test/firebase/functions.collection-share.test.mjs` runs the handler and the
  prune against the emulator with the repo rules loaded.

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

Cycle 116 added:

- `docs/community-callable-rollout-evidence.md` as the private-evidence and
  redacted-receipt runbook for future live callable rollout proof.
- `tools/community_callable_rollout_receipt.py` to validate owner-provided
  private live invocation evidence against the callable contract and Android
  wire-protocol manifests, then emit a receipt that hashes project IDs,
  operation IDs, resource IDs, private evidence references, and caller UID
  hashes.
- Backend tool tests that reject missing surface evidence, token-mode drift,
  operation-prefix drift, manifest-hash drift, invalid Functions App Check
  state, and duplicate receipt surfaces.

`CommunityCallableClient.publishSharedCollection()` publishes collection links.
`CollectionExporter` checks the 250 item and 512 KB link limits first, and a
share sheet still sends the collection file when the link can't be made (quota,
size, or no backend). A read the rules refuse because the share expired shows
"Collection link is expired or unavailable."

Until `publishSharedCollection` is deployed, a call that comes back `NOT_FOUND`
or `UNIMPLEMENTED` writes the share directly to `/shared_collections/{token}`
with the same fields and 30 day `expiresAt` the function stores
(`publishShareWithFallback` and `directSharedCollectionRecord`). Any other
refusal still fails the link. Vote counts follow the same migration rule: each
read prefers `/vote_counts/{contentId}/upvotes` and falls back to the legacy
`/votes/{contentId}/upvotes` tally for a row that isn't there yet, and the
leaderboard reads the legacy tree only while `/vote_counts` is empty. The
rules keep `/votes/{contentId}/upvotes` public, so the per-item fallback works
under them, while the whole-tree leaderboard read is admin-only. Voting itself
has no fallback, so votes aren't recorded until `recordCommunityVote` is
deployed.

Production order on 2026-09-30: `database.rules.json` went live before any
function, after `/vote_counts` was seeded by running `seedLegacyVoteCounts`
locally against production and the legacy vote markers were backfilled. From
then on the rules refuse the direct share write, so collection links wait for
`publishSharedCollection`. Firebase Authentication isn't set up on the project
yet either, so every signed-in community action fails at sign-in.

Report, vote, follow, user-block, sound upload finalization, wallpaper upload
finalization, profile edit, and collection share writes are the Android write
surfaces with callable client code and checked Android wire-protocol coverage
today.

## Request Envelope

All callable requests use a common envelope:

| Field | Source | Rule |
| --- | --- | --- |
| `operationId` | Client-generated UUID | Required for logs and retry correlation. Keys the pending quota reservation so a replay of the same call can't take a second unit; never used to skip a limit. |
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
   attempts. An accepted attempt takes its unit as a reservation: `count` and
   `lastAt` move, and `pending/{operationKey}` records `at` plus the previous
   `lastAt` as `prevLastAt`.
8. Write the public action and any private owner index in one Admin SDK
   multi-location update when the surface needs more than one path.
9. Write the dedupe marker with `createdAt`, `expiresAt`, and `target`.
10. Settle the reservation in a second ledger transaction. When the action
    stored something, drop the pending entry and keep the unit. When anything
    after the reservation threw (a storage precondition, an ID allocation, the
    write itself) or the write stored nothing (a vote that lost the marker race),
    refund it: `count` drops by one, `lastAt` goes back to `prevLastAt` unless a
    newer reservation has moved it, and `releasedCount`/`lastReleasedAt` record
    the refund. A failed settle is logged and never changes the caller's result.
11. Return a small result object with `status`, `targetPath`, `retryAfterMillis`
    when blocked, and the server timestamp used for the write.

### Reservations that never settle

A run that dies between steps 7 and 10 leaves its pending entry behind. For five
minutes (callables time out after 60 seconds) a replay with the same operation
ID is blocked with reason `in-progress` instead of taking a second unit. After
that the replay takes the entry over and settles it as its own, keeping the
original cooldown stamp in `cooldownAt` so a refund still restores it. Entries
for other operations older than five minutes are dropped on the next attempt
and counted in `expiredCount`. Their unit stays spent, because the write may
have landed and a refund could hand out a free one. The pending map can't grow
past the day's limit, since every entry in it is counted.

Ledgers are per UTC day, so for the first five minutes after midnight (or the
surface's cooldown, if longer) `reserveQuotaLedger` also reads yesterday's
ledger. A replay whose run started before midnight is still blocked as
`in-progress`, and yesterday's `lastAt` still counts toward the cooldown. Every
callable backend reserves through that one helper.

Refunds don't loosen the limits on what gets published: only a write that
landed keeps its unit, so the daily cap still bounds stored content. A caller
who forces failures on purpose gets no cooldown, but each of those calls still
needs App Check, and the upload finalizers burn a limited-use token every time.

## Error Codes

| Code | Meaning |
| --- | --- |
| `UNAUTHENTICATED` | Missing Firebase Auth. |
| `FAILED_PRECONDITION` | Missing or invalid App Check. |
| `PERMISSION_DENIED` | Authenticated caller cannot write the requested owner/admin path. |
| `INVALID_ARGUMENT` | Payload fails normalization or bounds checks. |
| `RESOURCE_EXHAUSTED` | Daily limit or cooldown blocks the write, or the same operation is still in flight (`in-progress`). |
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
- Run `tools/community_callable_rollout_receipt.py` after owner-approved live
  callable invocation evidence is collected.
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
