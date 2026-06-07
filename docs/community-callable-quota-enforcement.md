# Community Callable Quota Enforcement

Cycle 63 turns the Cycle 54 quota policy into a backend contract for callable
functions. The repo does not yet include a deployed Cloud Functions project;
this document is the implementation contract that the backend and Android
repository migration must follow.

## Contract Source

`CommunityQuotaPolicies` is the code-backed policy table. Each row now defines:

- `surfaceKey` for the protected quota and dedupe ledgers.
- Daily limit, cooldown, and dedupe-key source.
- Required enforcement layers.
- Callable function name, payload schema, final write paths, and limited-use
  App Check token decision.

Unit tests keep all community write surfaces covered and fail if a callable
contract loses auth, App Check, ledger, or final-write coverage.

## Callable Matrix

| Surface | Callable | Payload | Final writes | Limited-use token |
| --- | --- | --- | --- | --- |
| Reports | `submitCommunityReport` | `CommunityReportInput` | `/community_reports/{reportId}` | Yes |
| Sound uploads | `finalizeCommunitySoundUpload` | `CommunitySoundUploadMetadata` | `/community_sounds/{uploadId}`, `/owner_uploads/{uid}/sounds/{uploadId}` | Yes |
| Wallpaper uploads | `finalizeCommunityWallpaperUpload` | `CommunityWallpaperUploadMetadata` | `/community_wallpapers/{uploadId}`, `/owner_uploads/{uid}/wallpapers/{uploadId}` | Yes |
| Votes | `recordCommunityVote` | `CommunityVoteInput` | `/votes/{contentId}`, `/voters/{contentId}/{uid}` | No |
| Follows | `setCreatorFollow` | `CommunityFollowInput` | `/creator_follows/{uid}/{creatorId}` | No |
| Profile edits | `updateCreatorProfile` | `CreatorProfileUpdateInput` | `/creator_profiles/{uid}` | No |

Every callable also owns these protected ledgers for its surface:

- `/community_write_quotas/{uid}/{yyyyMMdd}/{surface}`
- `/community_write_dedupe/{uid}/{surface}/{dedupeKey}`

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
   lower-volume than votes/follows/profile edits.
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
4. Migrate votes and follows next while keeping the existing local optimistic UI
   state.
5. Migrate upload metadata finalization after Storage rules and owner indexes
   are verified. Storage upload bytes still go through Firebase Storage; the
   callable owns the public metadata and owner-index final write.
6. Migrate profile edits last because profile copy and display-name validation
   can change without affecting moderation safety.
7. After each surface is callable-backed, tighten direct RTDB writes for that
   path to owner/admin migration exceptions or admin-only writes.

## Verification

- Run `CommunityQuotaPolicyTest` after contract edits.
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
