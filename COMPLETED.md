# Completed Work

Append-only completion ledger for autonomous Aura passes. `ROADMAP.md` remains
the open-work source of truth; this file records shipped or closed items so the
next pass can resume quickly.

## 2026-06-06

- Completed Cycle 18 research for generated release-runtime OSS notices, license
  drift gates, SBOM/release artifacts, and native/copyleft packet planning.
- Completed Cycle 19 current-toolchain notice tooling research and
  AboutLibraries version-gating analysis.
- Completed Cycle 20 isolated compatibility spikes for Google OSS notices and
  AboutLibraries 14.2.1 under local `work/` clones.
- Completed Cycle 21 plugin-only Google OSS notice generation and
  `THIRD-PARTY-NOTICES.md` converter planning.
- Completed Cycle 22 real-repo plugin-only Google OSS notice implementation:
  Gradle plugin wiring, verification metadata updates, markdown converter,
  release workflow packaging, supply-chain docs, and focused notice generation
  verification.
- Restored real-repo `ProviderDisclosureTest` execution after adding the POM
  checksum metadata required by the debug OSS notice task.
- Completed Cycle 23 native/copyleft payload inspection: added
  `tools/native_compliance_inventory.py`, committed
  `docs/legal/native-compliance.md`, wired release `NATIVE-COMPLIANCE.md`
  generation/checksums/uploads, and documented FFmpeg source-correspondence
  review requirements.
- Completed Cycle 24 dependency notice drift gating: added
  `tools/dependency_notice_lock.py`, committed
  `docs/legal/dependency-notices.lock.json`, and wired PR/main verification plus
  release builds to fail on generated Google OSS notice drift.
- Completed Cycle 25 native compliance freshness gating: extended
  `tools/native_compliance_inventory.py`, committed
  `docs/legal/native-compliance.lock.json`, and wired PR/main verification plus
  release builds to fail on native/copyleft artifact or extracted payload drift.
- Completed Cycle 26 curated high-risk dependency overlay: added
  `docs/legal/dependency-notice-overrides.json`,
  `tools/dependency_overlay_check.py`, and wired PR/main verification plus
  release builds to fail on missing, stale, or orphaned high-risk dependency and
  native-payload review metadata.
- Completed Cycle 27 release compliance artifact dry-run validation: added
  `tools/release_artifact_bundle_check.py`, wired release workflow bundle
  validation before artifact upload/publication, and documented the manual
  `workflow_dispatch` dry-run procedure.
- Completed Cycle 28 raw release notice input preservation: added
  `tools/google_oss_raw_archive.py`, wired `GOOGLE-OSS-RAW-INPUTS.zip` into
  release checksums, notes, workflow artifacts, tagged release assets, and the
  bundle validator.
- Completed Cycle 29 user-facing dependency notice access: added generated
  release notice cards to `LicensesScreen.kt`, updated Settings copy, and added
  focused coverage for the release artifact link data.
- Completed Cycle 30 FFmpeg source-correspondence evidence: added
  `docs/legal/ffmpeg-source-correspondence.md`, extracted embedded FFmpeg
  7.1.1 configure lines and license-mode facts into the native compliance
  generator/lock, regenerated the native packet, and updated release review
  docs for the remaining Termux source/build-log owner action.
- Completed Cycle 31 release dependency license policy gate: added
  `docs/legal/dependency-license-policy.json`,
  `tools/dependency_license_policy.py`, and wired PR/main verification plus
  release builds to fail unknown, disallowed, or unreviewed curated dependency
  and native-payload license IDs.
- Completed Cycle 32 raw Google OSS archive retention policy: documented
  `GOOGLE-OSS-RAW-INPUTS.zip` as a permanent tagged public release asset,
  clarified dry-run/release signing docs, and kept release bundle validation
  enforcing the archive in files, checksums, and notes.
- Completed Cycle 33 custom in-app generated dependency notice viewer: added
  a parser for generated Google OSS raw resources, listed generated dependency
  notices in Settings > Open source licenses, and opened full generated notice
  text without adding the stock Play services OSS licenses runtime dependency.
- Completed Cycle 34 generated notice search and high-risk alignment: added
  filtering for generated notice names/license labels and review markers for
  generated rows that map to curated Firebase, Play services, ML Kit,
  NewPipeExtractor, youtubedl-android, ProfileInstaller, and ZXing surfaces.
- Completed Cycle 35 generated notice metadata parity guard: added
  `tools/dependency_notice_lock.py --mode check-metadata`, wired PR/main and
  release workflows to run it after the full generated notice lock check, and
  documented the raw metadata parity command in the supply-chain runbook.
- Completed Cycle 36 runtime provider kill-switch behavior matrix: added
  code-backed `ProviderRuntimeControl` rows for every `ContentSource`, extended
  provider disclosure tests to require explicit disabled behavior/follow-ups, and
  documented current missing/partial controls in
  `docs/legal/provider-runtime-controls.md`.
- Completed Cycle 37 YouTube provider legal-mode switch: added a default-on
  YouTube provider preference and Settings switch, blocked YouTube search,
  import, top hits, similar sounds, playback/download/video resolution, and video
  discovery when disabled, added bundled sound fallbacks, and recorded disabled
  source diagnostics separately from outages.
- Completed Cycle 38 Reddit provider source switch: added a default-on Reddit
  provider preference and Settings switch, hid Reddit wallpaper browsing when
  disabled, skipped daily picks/background rotations/repository calls/video
  discovery, and recorded disabled source diagnostics separately from outages.
- Completed Cycle 39 Pexels and Pixabay source switches: added default-on
  provider preferences and Settings switches, hid disabled provider tabs, skipped
  Discover/search/style-biased/video API calls before bundled keys are read,
  removed disabled Pixabay from rotation pickers, and queued Pixabay
  TTL/rate-limit enforcement for the later policy slices.
- Completed Cycle 40 Community source switch: added a default-on Community
  provider preference and Settings switch, skipped startup identity warm-up when
  disabled, hid community tabs/uploads/votes/creator profile entry points,
  blocked sound/wallpaper/creator repository Firebase calls, and recorded
  disabled diagnostics separately from Firebase outages.
- Completed Cycle 41 Bing Daily source switch: added a default-on Bing Daily
  provider preference and Settings switch, skipped Bing daily-image API calls
  before cache fallback or Retrofit use, hid Bing from rotation pickers when
  disabled, and recorded disabled diagnostics separately from outages.
- Completed Cycle 42 Wallhaven source switch: added a default-on Wallhaven
  provider preference and Settings switch, hid Wallhaven browsing plus
  color/random/similar actions when disabled, removed disabled Wallhaven from
  rotation pickers, skipped Wallhaven API calls before key reads/cache fallback,
  and recorded disabled diagnostics separately from outages.
- Completed Cycle 43 Pixabay photo request-cache and backoff: added a
  24-hour Pixabay metadata TTL, served fresh cached Pixabay photo results before
  API calls, parsed `Retry-After`/`X-RateLimit-Reset` from 429 responses into an
  in-session backoff, and queued Pixabay video metadata caching for the next
  policy slice.
- Completed Cycle 44 Pixabay video request-cache and backoff: added persistent
  app-private 24-hour video metadata caching, restored cached stream URLs with
  cached rows, persisted 429 backoff from `Retry-After`/`X-RateLimit-Reset`, and
  marked Pixabay runtime controls covered for photo and video metadata.
- Completed Cycle 45 generated-content source switch: added a default-on
  generated-wallpapers preference and Settings switch, hid the Wallpapers
  Generate chip when disabled, blocked the AI wallpaper ViewModel before prompt
  or key validation, and marked AI-generated runtime controls covered while
  keeping saved local generated wallpapers visible.
- Completed Cycle 46 saved-source availability states: added Room-backed source
  availability metadata for favorites and download history, migrated the
  database to v15, preserved provider source names in new download records,
  surfaced "Source unavailable" badges in Favorites/Downloads/detail screens,
  and kept local saved wallpaper paths usable when upstream content is removed.
- Completed Cycle 47 Pexels enhancement guardrails: Discover and video-wallpaper
  discovery now drop Pexels-only batches unless non-Pexels base inventory is
  present, disabled-Pexels Discover still returns Wallhaven/Pixabay fallback
  inventory, and Pexels photo rows keep creator/source-page attribution fields.
- Completed Cycle 48 provider removal failure reconciliation: explicit
  404/410/gone/removed/deleted provider failures now mark saved wallpaper/sound
  favorites unavailable during apply/download paths, and failed re-downloads
  mark matching download-history rows unavailable.
- Completed Cycle 49 sound license capability gates: added normalized sound
  action policy, favorite license persistence with Room v16 and export/import
  preservation, confirmation gates for restricted apply/download actions,
  disabled states for link-only or missing-license content, and provenance-rich
  sound share text.
- Completed Cycle 50 community upload rights metadata: sound and wallpaper
  uploads now require selected CC0/CC BY/CC BY-NC metadata plus rights
  attestation, store uploader UID/attestation/source fields, validate them in
  RTDB rules, feed selected community sound licenses into action gates, and show
  wallpaper license metadata on detail surfaces.
- Completed Cycle 51 community report queue intake: added private report
  payload validation, Firebase report and resolution repository paths, RTDB
  rules for authenticated report creation and admin-only review/resolution,
  sound/wallpaper detail report actions, ViewModel report submission, and
  support docs for the reporting workflow.
- Completed Cycle 52 admin report review: added an admin-only open report
  screen, Settings navigation, RTDB report status indexing, hide/dismiss/restore
  actions, moderation hide/unhide wiring, report resolution feedback, and
  focused ViewModel coverage for moderation and non-admin gating.
- Completed Cycle 53 Firebase App Check client rollout: added debug and release
  App Check provider installers, installed App Check before Firebase-backed
  community startup work, documented debug token and Play Integrity rollout
  steps, refreshed Gradle dependency verification metadata, and updated the
  generated dependency notice lock for App Check artifacts.
- Completed Cycle 54 community quota/rate-limit design: added typed quota
  policy rows for reports, uploads, votes, follows, and profile edits, reserved
  admin-only RTDB quota and dedupe ledgers, documented the App-Checked callable
  migration plan, and linked the policy from community support/admin runbooks.
- Completed Cycle 55 community upload deletion handles: new sound and wallpaper
  uploads now store canonical Storage paths and private owner-index rows,
  repositories expose owner delete methods for new rows, RTDB rules protect the
  owner index, and the deletion runbook documents remaining UI/admin/backfill
  work.
- Completed Cycle 56 visible community owner delete actions: repositories now
  expose owner-and-`storagePath` availability probes, sound and wallpaper
  ViewModels expose delete entry points, and detail surfaces show confirmed
  owner-only delete actions for new community uploads.
- Completed Cycle 57 Storage rules and emulator harness: added tracked
  `firebase.json`, `storage.rules`, local npm Firebase rules dependencies, and
  Storage emulator tests for owner-only community upload writes/deletes,
  MIME/size ceilings, public reads, and closed unmanaged paths.
- Completed Cycle 58 Realtime Database rules harness: added Database emulator
  config, `test:database-rules` and `test:firebase-rules` scripts, RTDB
  emulator tests for upload metadata, owner indexes, reports, report
  resolutions, quota/dedupe ledgers, and collection shares, aligned tracked
  collection-share rules to `shared_collections`, added `createdByUid` to
  published shares, and made `database.rules.json` emulator/deploy-compatible.
- Completed Cycle 59 Firebase rules CI gate: added a path-gated
  `firebase-rules` job to `.github/workflows/verify.yml` that installs Java 17,
  Node 20, pinned npm dependencies, and runs `npm run test:firebase-rules` when
  Firebase rules, config, tests, runbooks, or the workflow change.
- Completed Cycle 60 rights takedown receipts: hiding a `RIGHTS` report for a
  community sound or wallpaper now writes a private admin receipt with the
  upload metadata path, Storage deletion handle, uploader UID, resolver UID,
  timestamp, and note when the current upload row proves the deletion handle;
  RTDB rules and emulator tests reject non-admin, non-rights, stale-handle, and
  mismatched-path receipt writes.
- Completed Cycle 61 admin upload delete actions: qualifying rights reports now
  expose a confirmed `Delete upload` admin action that records a `DELETE`
  receipt, hides the content ID, deletes the Storage object, removes public
  upload metadata plus the owner index, and updates the receipt to `SUCCEEDED`
  or `FAILED` for retry evidence.
- Completed Cycle 62 closed report review filters: the admin report queue now
  switches between Open, Hidden, Dismissed, and Restored status feeds, including
  from the empty state, so closed moderation outcomes remain reviewable.
- Completed Cycle 63 callable quota enforcement contract: quota policies now
  name each App-Checked callable, payload schema, final write path set, protected
  ledger path set, and limited-use App Check token decision for reports,
  uploads, votes, follows, and profile edits; the backend migration runbook
  records request envelope, transaction sequence, error mapping, Android
  migration order, and verification requirements.
- Completed Cycle 64 community backend deploy/rollback evidence: added a
  deterministic Firebase backend manifest tool, committed the current backend
  manifest, wired the manifest check into the Firebase rules CI job, and added
  a deploy/rollback runbook covering preflight, production-project dry run,
  deployment evidence, rollback, App Check enforcement rollback separation, and
  release checklist entries.
- Completed Cycle 65 community Storage lifecycle/orphan cleanup policy: added an
  offline orphan-report tool, unit coverage for orphan and metadata mismatch
  classification, CI execution for backend lifecycle/tool changes, and a policy
  runbook that blocks automatic deletes on committed upload prefixes while
  requiring two matching orphan reports before manual cleanup.
- Completed Cycle 66 legacy upload backfill planning: added a dry-run RTDB
  backfill planner for legacy community rows missing canonical `storagePath` and
  owner indexes, unit coverage for Firebase Storage URL parsing and unsafe-row
  blocking, CI path coverage for all backend tool tests, and a backfill runbook
  with candidate requirements, apply gate, and evidence requirements.
- Completed Cycle 67 community deletion tombstones: owner and admin upload
  deletes now remove public metadata plus owner indexes while writing private
  admin-only tombstones with owner-scoped Storage handles, RTDB rules/emulator
  coverage, and a deletion retention policy for removed uploads.
- Completed Cycle 68 community block-user policy: reserved private
  `/community_user_blocks` rows and admin-only `/community_blocked_by` reverse
  indexes, added typed block policy helpers, emulator coverage, callable quota
  metadata, and a block-user runbook.
- Completed Cycle 69 public takedown copy: community sound and wallpaper upload
  dialogs now disclose public listing behavior and confirmed rights-takedown
  outcomes, the report dialog explains the private rights-takedown route, owner
  delete confirmations describe public metadata/index removal plus retained
  private records, and reusable policy copy has focused unit coverage.
- Completed Cycle 70 community block-user filtering: added Android block-list
  repository reads, block/unblock update methods, no-sign-in filtering reads for
  public browsing, community sound/wallpaper feed filtering, creator profile
  list filtering, and focused unit coverage for unblock paths and blocked
  creator/uploader matching.
- Completed Cycle 71 visible block creator actions: community sound and
  wallpaper detail surfaces now show confirmed block actions when uploader
  identity is available, block through `CommunityBlockRepository`, and remove
  matching uploader rows from the active UI state with focused ViewModel and
  policy-copy coverage.
- Completed Cycle 72 blocked creators review: Settings now lists blocked
  community creators with reason/timestamp metadata and supports per-row unblock
  actions through `CommunityBlockRepository`, with focused Settings ViewModel
  coverage.
- Completed Cycle 73 report/profile block actions: community reports now carry
  optional uploader UID metadata, report rules validate that field, admin report
  cards can block reported community uploaders, and creator profile rows expose
  confirmed block actions with immediate dashboard filtering.
- Completed Cycle 74 account deletion dry-run planning: added
  `tools/community_account_deletion_plan.py`, backend tool coverage, and
  `docs/community-account-deletion-policy.md` for vote markers, follows,
  creator profiles, block indexes, collection shares, retained aggregate vote
  counts, retained moderation audit records, and public upload workflow
  boundaries.
- Completed Cycle 75 community identity request surface: Settings now shows a
  read-only community identity dialog with auth type, redacted identity suffix,
  and a deletion request code when a Firebase identity exists; the summary path
  does not create a local fallback UUID or Firebase anonymous account just by
  opening Settings.
- Completed Cycle 76 community deletion request routing: the identity dialog
  now shares a redacted deletion request draft through the device share sheet,
  `docs/support/community-account-deletion.md` defines user/operator handling,
  and the README links the private request workflow.
- Completed Cycle 77 deletion request code lookup: added
  `tools/community_deletion_request_lookup.py` and backend tool tests to map an
  `AURA-` request code to candidate UID evidence paths in a current RTDB export
  before running the dry-run deletion planner.
- Completed Cycle 78 account deletion review gate: added
  `tools/community_account_deletion_review.py` and backend tool tests to
  cross-check request-code lookup output against dry-run RTDB null-update plans,
  reject ambiguous or mismatched artifacts, and emit a redacted review receipt
  before any future trusted apply step.
- Completed Cycle 79 account deletion apply simulator: added
  `tools/community_account_deletion_apply_simulator.py` and backend tool tests
  to verify review/plan hashes, reject retained-root deletes through the review
  gate, simulate null updates against a copied RTDB export, prune empty objects,
  and emit a hashed simulation receipt without contacting Firebase.
- Completed Cycle 80 account deletion executor package: added
  `tools/community_account_deletion_executor_package.py` and backend tool tests
  to validate plan, review, and simulation receipts before producing the
  private RTDB null-update payload that a future trusted executor can consume.
- Completed Cycle 81 account deletion REST executor: added
  `tools/community_account_deletion_rest_executor.py` and backend tool tests
  for a dry-run-default RTDB REST executor that requires matching request-code
  and plan-hash confirmations plus an OAuth2 token before sending a multi-path
  `PATCH`.
- Completed Cycle 82 account deletion completion receipt: added
  `tools/community_account_deletion_completion_receipt.py` and backend tool
  tests to validate applied REST receipts against private executor packages,
  reject dry-run receipts as completion evidence, and emit a user-safe redacted
  receipt for requester responses.
- Completed Cycle 83 account deletion web intake: added
  `docs/support/community-account-deletion-web-intake.md`,
  `tools/community_deletion_web_intake.py`, and backend tool tests to validate
  private hosted form exports, hash requester contact/statement fields, require
  deletion/retention/upload attestations, and hand off to request-code lookup
  without committing raw requester data.
- Completed Cycle 84 account deletion local/Auth cleanup sequence: added
  `tools/community_account_deletion_cleanup_sequence.py` and backend tool tests
  to require a completed backend receipt before ordering requester local app
  cleanup, operator Firebase Auth deletion, and separate public upload deletion
  handoff.
- Completed Cycle 85 in-app local community cleanup: Settings > Community
  identity now exposes `Clear local` for the current device fallback identity,
  refreshes the displayed identity summary after clearing, and keeps backend,
  Firebase Auth, public upload, and moderation deletion in the support/operator
  chain.
- Completed Cycle 86 account deletion Auth package: added
  `tools/community_account_deletion_auth_package.py` and backend tool tests to
  build a private Firebase Auth deletion package only after request-code lookup
  and backend completion evidence match.
- Completed Cycle 87 account deletion upload handoff plan: added
  `tools/community_account_deletion_upload_plan.py` and backend tool tests to
  enumerate owned public upload rows from a current RTDB export, block rows
  with missing or mismatched `storagePath` handles, and hand candidates to the
  owner/admin upload deletion workflow without deleting anything directly.
- Completed Cycle 88 account deletion web URL gate: added
  `docs/privacy/privacy-policy.md`,
  `docs/support/community-account-deletion-web-url.json`,
  `tools/community_deletion_web_url_check.py`, and backend tool tests so the
  hosted deletion URL remains explicitly `pendingOwnerUrl` until an HTTPS
  owner-published request URL is live and linked from policy/support docs.
- Completed Cycle 89 Auth deletion execution receipt: added
  `tools/community_account_deletion_auth_execution_receipt.py` and backend
  tool tests to validate owner-approved Firebase Auth deletion evidence against
  the private Auth package and emit a redacted post-delete receipt.
- Completed Cycle 90 upload deletion execution receipt: added
  `tools/community_account_deletion_upload_execution_receipt.py` and backend
  tool tests to validate clean-plan owner/admin deletion evidence for Storage,
  public metadata, owner index, and tombstone completion.
- Completed Cycle 91 callable contract manifest gate: added
  `docs/community-callable-contract.json`,
  `tools/community_callable_contract_check.py`, and backend tool tests so the
  callable quota contract is machine-checkable and quota reset days are pinned
  to UTC before the Cloud Functions project is added.
- Completed Cycle 92 hosted deletion page template gate: added
  `docs/support/community-account-deletion-web-page.md`,
  `tools/community_deletion_web_page_check.py`, and backend tool tests so the
  owner has checked publishable page copy before assigning a live HTTPS URL.
- Completed Cycle 93 Cloud Functions scaffold: added the Node 20 TypeScript
  `functions/` project, fail-closed App Check/Auth callable exports for the
  seven contracted community surfaces, a manifest-synced callable contract
  mirror, a UTC quota decision engine, Functions unit tests, backend manifest
  coverage, and CI change detection.
- Completed Cycle 94 community report callable handler: implemented
  `submitCommunityReport` handler logic with Firebase Auth/App Check identity
  enforcement, server-derived reporter UID, report payload normalization,
  HTTPS source validation, UTC quota reservation, duplicate handling, and
  focused Functions tests for accepted, duplicate, cooldown, daily-limit,
  unauthenticated, and missing-App-Check paths.
- Completed Cycle 95 community vote callable handler: implemented
  `recordCommunityVote` handler logic with Firebase Auth/App Check identity
  enforcement, content ID normalization matching the Android Firebase-key
  storage form, existing nested/legacy voter marker idempotency before quota
  reservation, UTC quota checks, dedupe handling, vote tally transactions,
  legacy voter-marker mirroring, and focused Functions tests for accepted,
  duplicate, cooldown, daily-limit, unauthenticated, missing-App-Check, and
  invalid-content-ID paths.
- Completed Cycle 96 creator follow callable handler: refined the follow
  dedupe contract from creator-only to creator-plus-desired-state, implemented
  `setCreatorFollow` handler logic with Firebase Auth/App Check identity
  enforcement, follow/unfollow payload normalization, no-op state idempotency
  before quota reservation, UTC quota checks, action-specific dedupe handling,
  final follow-row set/remove writes, and focused Functions tests for accepted
  follow, accepted unfollow, duplicate no-ops, same-state dedupe, cooldown,
  daily-limit, unauthenticated, missing-App-Check, and invalid payload paths.
- Completed Cycle 97 community block callable handler: refined the block dedupe
  contract from blocked-user-only to blocked-user-plus-desired-state,
  implemented `setCommunityUserBlock` handler logic with Firebase Auth/App
  Check identity enforcement, block/unblock payload normalization, self-block
  and blocker-override rejection, no-op state idempotency before quota
  reservation, UTC quota checks, action-specific dedupe handling, private block
  row and admin reverse-index set/remove writes, and focused Functions tests
  for accepted block, accepted unblock, duplicate no-ops, same-state dedupe,
  cooldown, daily-limit, unauthenticated, missing-App-Check, and invalid
  payload paths.
- Completed Cycle 98 community sound upload callable handler: implemented
  `finalizeCommunitySoundUpload` handler logic with Firebase Auth/App Check
  identity enforcement, server-allocated upload IDs, sound metadata
  normalization, Storage path ownership checks under `sounds/{uid}/...`, HTTPS
  URL validation, storage-path dedupe, UTC quota checks, public
  `/community_sounds/{uploadId}` metadata writes, private
  `/owner_uploads/{uid}/sounds/{uploadId}` index writes, and focused Functions
  tests for accepted finalization, active dedupe, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid ownership/payload paths.
- Completed Cycle 99 community wallpaper upload callable handler: implemented
  `finalizeCommunityWallpaperUpload` handler logic with Firebase Auth/App Check
  identity enforcement, server-allocated upload IDs, wallpaper metadata
  normalization, Storage path ownership checks under `wallpapers/{uid}/...`,
  HTTPS URL validation, dimension/file-size/file-type checks, storage-path
  dedupe, UTC quota checks, public `/community_wallpapers/{uploadId}` metadata
  writes, private `/owner_uploads/{uid}/wallpapers/{uploadId}` index writes,
  and focused Functions tests for accepted finalization, active dedupe,
  cooldown, daily-limit, unauthenticated, missing-App-Check, and invalid
  ownership/payload paths.
- Completed Cycle 100 creator profile callable handler: refined profile edit
  dedupe from profile UID only to profile UID plus normalized public profile
  hash, implemented `updateCreatorProfile` handler logic with Firebase
  Auth/App Check identity enforcement, server-derived profile UID,
  display-name/bio/HTTPS URL normalization, server-assigned timestamps,
  identical-profile idempotency before quota reservation, UTC quota checks,
  final `/creator_profiles/{uid}` writes, and focused Functions tests for
  accepted update, duplicate, cooldown, daily-limit, unauthenticated,
  missing-App-Check, and invalid payload paths.
- Completed Cycle 101 profile callable emulator coverage: added
  `test/firebase/functions.profile.test.mjs`, the
  `npm run test:functions-emulator` script, real Admin SDK backend invocation
  for `updateCreatorProfileHandler`, RTDB-emulator assertions for profile,
  quota, and dedupe rows, unchanged-profile idempotency coverage, and backend
  manifest script tracking plus CI execution for Functions-related emulator
  tests.
- Completed Cycle 102 report callable emulator coverage: added
  `test/firebase/functions.report.test.mjs`, default real-backend invocation
  for `submitCommunityReportHandler`, expanded
  `npm run test:functions-emulator` to all callable emulator test files, and
  verified report, quota, dedupe, and duplicate report writes through the RTDB
  emulator.
- Completed Cycle 103 vote callable emulator coverage: added
  `test/firebase/functions.vote.test.mjs`, default real-backend invocation for
  `recordCommunityVoteHandler`, serialized the callable emulator test runner,
  and verified vote tally, nested voter, legacy voter, quota, dedupe, and
  repeat-vote idempotency writes through the RTDB emulator.
- Completed Cycle 104 follow callable emulator coverage: added
  `test/firebase/functions.follow.test.mjs`, default real-backend invocation
  for `setCreatorFollowHandler`, and verified follow writes, unfollow removals,
  quota, dedupe, and missing-unfollow idempotency through the RTDB emulator.
- Completed Cycle 105 user block callable emulator coverage: added
  `test/firebase/functions.block.test.mjs`, default real-backend invocation
  for `setCommunityUserBlockHandler`, and verified private block rows,
  reverse-index rows, unblock removals, quota, dedupe, and missing-unblock
  idempotency through the RTDB emulator.
- Completed Cycle 106 sound upload callable emulator coverage: added
  `test/firebase/functions.sound-upload.test.mjs`, default real-backend
  invocation for `finalizeCommunitySoundUploadHandler`, and verified public
  metadata, owner index, quota, storage-path dedupe, and duplicate upload
  idempotency through the RTDB emulator.
- Completed Cycle 107 wallpaper upload callable emulator coverage: added
  `test/firebase/functions.wallpaper-upload.test.mjs`, default real-backend
  invocation for `finalizeCommunityWallpaperUploadHandler`, and verified public
  metadata, owner index, quota, storage-path dedupe, and duplicate upload
  idempotency through the RTDB emulator.
- Completed Cycle 108 Android report callable migration: added the Android
  `firebase-functions` dependency under the existing Firebase BoM, a shared
  callable invoker/client, limited-use App Check token selection for report
  calls, callable report payload tests that omit server-owned fields, and
  callable-first `CommunityReportRepository.submitReport()` with a compatibility
  fallback while deploy evidence and direct-rule tightening remain pending.
- Completed Cycle 109 Android vote callable migration: added Android
  `CommunityVoteInput` payload normalization, extended `CommunityCallableClient`
  for `recordCommunityVote`, routed `VoteRepository.upvote()` through the
  callable when Firebase Auth is available, preserved direct RTDB fallback only
  for missing endpoint or missing Auth compatibility, and covered the vote
  callable envelope with focused unit tests.
- Completed Cycle 110 Android follow callable migration: added
  `CommunityFollowInput` payload normalization, extended `CommunityCallableClient`
  for `setCreatorFollow`, routed `CreatorProfileRepository` follow/unfollow
  writes through the callable when Firebase Auth is available, preserved direct
  RTDB fallback only for missing endpoint or missing Auth compatibility, and
  covered follow plus unfollow callable envelopes with focused unit tests.
- Completed Cycle 111 Android user-block callable migration: added
  `CommunityUserBlockInput` payload normalization, extended
  `CommunityCallableClient` for `setCommunityUserBlock`, routed
  `CommunityBlockRepository` block/unblock writes through the callable when
  Firebase Auth is available, preserved direct RTDB fallback only for missing
  endpoint or missing Auth compatibility, and covered block plus unblock
  callable envelopes with focused unit tests.
- Completed Cycle 112 Android sound upload finalizer callable migration: added
  `CommunitySoundUploadMetadataInput` payload normalization, extended
  `CommunityCallableClient` for `finalizeCommunitySoundUpload`, routed
  `UploadRepository.uploadSound()` metadata finalization through the callable
  after Storage upload when Firebase Auth is available, preserved direct RTDB
  fallback only for missing endpoint or missing Auth compatibility, and covered
  the sound upload callable envelope with focused unit tests.
- Completed Cycle 113 Android wallpaper upload finalizer callable migration:
  added `CommunityWallpaperUploadMetadataInput` payload normalization, extended
  `CommunityCallableClient` for `finalizeCommunityWallpaperUpload`, routed
  `WallpaperUploadRepository.uploadWallpaper()` metadata finalization through
  the callable after Storage upload when Firebase Auth is available, preserved
  direct RTDB fallback only for missing endpoint or missing Auth compatibility,
  and covered the wallpaper upload callable envelope with focused unit tests.
- Completed Cycle 114 Android profile edit callable migration: added
  `CreatorProfileUpdateInput` payload normalization, extended
  `CommunityCallableClient` for `updateCreatorProfile`, routed
  `CreatorProfileRepository.updateCreatorProfile()` through the callable when
  Firebase Auth is available, preserved direct RTDB fallback only for missing
  endpoint or missing Auth compatibility, added a creator profile edit action,
  and covered the callable envelope plus ViewModel state update with focused
  unit tests.
- Completed Cycle 115 callable wire-protocol guard: added
  `docs/community-callable-wire-protocol.json`,
  `tools/community_callable_wire_protocol_check.py`, and backend tool tests so
  all seven contracted Android callable surfaces are machine-checked against
  the backend callable contract, Android client methods, payload builders,
  shared request envelope, App Check token choices, operation prefixes,
  response resource IDs, and focused client tests.
- Completed Cycle 116 callable rollout evidence receipt: added
  `docs/community-callable-rollout-evidence.md`,
  `tools/community_callable_rollout_receipt.py`, and backend tool tests so
  future owner-provided live callable invocation evidence can be validated
  against the callable contract and wire-protocol manifests, then reduced to a
  redacted receipt that omits raw project IDs, operation IDs, resource IDs,
  command output, credentials, and tokens.
- Completed Cycle 117 GitHub security workflow policy guard: added
  `docs/distribution/github-security-workflows.json`,
  `tools/github_security_workflow_check.py`, and backend tool tests so
  Dependency Review, OpenSSF Scorecard, and Release workflow triggers,
  permissions, SARIF upload, attestation, release bundle, and unsafe
  escape-hatch expectations are checked in the always-on verify job.
- Completed Cycle 118 Dependabot update policy guard: added
  `.github/dependabot.yml`, `tools/dependabot_config_check.py`, and backend
  tool tests so GitHub Actions, Gradle, root npm, and Functions npm version
  update surfaces have checked weekly cadence, `main` target branch, PR limit,
  labels, and commit prefix policy.
- Completed Cycle 119 GitHub security settings receipt: added
  `docs/distribution/github-security-settings-evidence.md`,
  `tools/github_security_settings_receipt.py`, and backend tool tests so future
  owner/admin branch-protection, Dependabot, code-scanning, secret-scanning,
  and release-attestation evidence can be validated before emitting a redacted
  receipt.
- Completed Cycle 120 Gradle wrapper checksum guard: added
  `distributionSha256Sum` for the reviewed Gradle 8.12 bin ZIP, plus
  `tools/gradle_wrapper_check.py` and backend tool tests so wrapper URL,
  checksum, URL validation, storage roots, and timeout drift fail verification.
- Completed Cycle 121 GitHub Actions allowlist guard: added
  `docs/distribution/github-actions-allowlist.json`,
  `tools/github_actions_allowlist_check.py`, and backend tool tests so every
  workflow `uses:` reference is checked against reviewed action refs and
  floating/local/unpinned action drift fails verification.
- Completed Cycle 122 GitHub workflow permissions guard: added
  `docs/distribution/github-workflow-permissions.json`,
  `tools/github_workflow_permissions_check.py`, and backend tool tests so
  workflow events, top-level permissions, job-level permissions, expected jobs,
  and expected workflow files are checked before Android setup in verify.
- Completed Cycle 123 GitHub workflow secret guard: added
  `docs/distribution/github-workflow-secrets.json`,
  `tools/github_workflow_secrets_check.py`, and backend tool tests so workflow
  secret references are limited to reviewed release signing secrets and
  forbidden token shortcuts fail verification.
- Completed Cycle 124 always-on backend tool tests: wired
  `python3 -m unittest discover -s test/tools -p '*_test.py'` into the
  always-on verify job before Android setup so policy and support-tool drift
  tests run on every push, pull request, and manual verify run.
- Completed Cycle 125 provider credential release guard: added
  `tools/provider_credential_release_check.py`, focused tests, verify/release
  workflow wiring, and release docs so optional provider keys from
  `local.properties` cannot be bundled into public release `BuildConfig`
  defaults without an explicit internal-build override warning.
- Completed Cycle 126 diagnostics redaction fixture suite: extended
  `CrashDiagnosticsText.sanitize()` for dotted provider-property assignment
  names, added provider-specific crash-log sentinel fixtures for query/header
  credentials, `local.properties`, file URIs, and app-private paths, and updated
  support/issue-template copy.
- Completed Cycle 127 redacted request logging contract: added
  `RequestRedactor`, moved crash diagnostics provider-secret redaction onto the
  shared helper, redacted `SourceMetrics` failure messages before Settings can
  display them, and covered provider query/header/local-property credentials
  plus request formatting with focused service tests.
- Completed Cycle 128 network endpoint inventory runbook: added
  `docs/security/network-endpoints.json`, `docs/security/network-endpoints.md`,
  `tools/network_endpoint_inventory_check.py`, live tool tests, supply-chain
  docs, and verify workflow wiring so hard-coded app network hosts require
  reviewed endpoint/auth/cache/fallback inventory coverage.
- Completed Cycle 129 cleartext release gate: removed the ccMixter HTTP
  fallback path and `ccmixter.org` cleartext network-security exception, added
  `tools/cleartext_release_check.py` with live and negative tests, wired verify
  and release workflows to run it before Android build work, and updated the
  endpoint/supply-chain docs for the HTTPS-only provider posture.
- Completed Cycle 130 provider credential storage policy: added
  `docs/security/provider-credential-storage.json`,
  `docs/security/provider-credential-storage.md`,
  `tools/provider_credential_storage_check.py`, focused policy tests, verify
  and release workflow wiring, privacy/support/supply-chain docs, and the
  missing Freesound Settings key clear control so provider credential storage
  classification and backup/transfer exclusions stay checked.
- Completed Cycle 131 provider credential APK scan: added
  `tools/provider_credential_apk_scan.py`, focused fixture tests, release
  workflow wiring after signed APK packaging, release workflow policy coverage,
  and release runbook updates so packaged APKs are scanned for nonblank
  provider credential values before uploads or tagged publication.
- Completed Cycle 132 provider key clear UX: consolidated Settings provider
  key dialogs around Save, Clear, and Cancel actions, extended
  `tools/provider_credential_storage_check.py` plus focused tests to require
  the explicit Clear path, and updated provider storage/privacy/supply-chain
  docs for the clearer user control.
- Completed Cycle 133 generated wallpaper disclosure gate: added a persisted
  generated-content disclosure flag, blocked Stability requests until prompt,
  key, and disclosure acceptance are present, added the in-flow disclosure
  dialog plus Settings review/reset entry, and documented prompt sharing,
  provider key/credit use, and local generated-image storage.
- Completed Cycle 134 generated-content report path: added generated wallpaper
  report actions for fresh results and saved generated favorites, introduced
  Offensive/Unsafe/Deceptive/Other generated-content reasons, extended the
  callable report reason allowlist, allowed AI-generated reports independent of
  the Community source switch, and documented retention, moderation response,
  and provider-key/local-path exclusions.

## 2026-06-05

- Completed the provider/content-source compliance slice: central
  `ProviderDisclosure` model, Licenses screen integration, legal provider policy
  matrix docs, and unit coverage for complete `ContentSource` disclosure rows.
- Expanded visible Licenses screen rows for missing runtime/native dependencies
  as a stopgap before generated OSS notices.
