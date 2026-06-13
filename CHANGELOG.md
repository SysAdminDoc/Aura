# Changelog

All notable changes to Aura will be documented in this file.

## Unreleased
- **Provider health persistence and auto-fallback**: `SourceMetrics` now
  persists failure state to SharedPreferences with a 24-hour cooldown so
  degradation survives process death. `isDegraded()` / `degradedSources()`
  expose persistent health. Daily wallpaper worker skips degraded sources
  when alternatives exist (Bing/Wallhaven fallback chain). Wallpapers,
  Sounds, and Video screens show a subtle "Limited source health" banner
  when degraded sources are active. Diagnostics bundle includes degraded
  source list for support triage.
- **Live wallpaper engine recovery receipts**: new `LiveWallpaperReceiptStore`
  tracks surface lifecycle, visibility changes, draw heartbeats, errors,
  and recovery actions for all three live wallpaper engines (video, weather,
  parallax). SharedPreferences-backed with UTC timestamps. Diagnostics
  bundle now includes a "Live wallpaper engine receipts" section surfacing
  stale/frozen engine state, surface recreation counts, last error, and
  last recovery action for support triage. Draw receipts are throttled to
  one write per 30 seconds to avoid I/O pressure.
- **Navigation 3 decision**: evaluated Nav 2.9 vs Nav 3 for the N-1
  toolchain pass. Decision: skip Nav 2.9, target Navigation 3 directly.
  Codebase surface: 22 destinations, 51 NavType declarations, 31
  navigate calls. Nav 3 eliminates string-route boilerplate, enables
  true predictive-back via NavDisplay, and avoids a two-step migration.
  N-1 scope, NX-4, NX-13, and all "Navigation 2.9" references updated.
- **Manifest consistency check**: new `tools/manifest_consistency_check.py`
  compares dependency/runtime version claims in ROADMAP.md and RESEARCH.md
  current-state sections against `gradle/libs.versions.toml`,
  `app/build.gradle.kts`, and `functions/package.json`. Reports stale
  claims and duplicate active roadmap titles. Aspirational/target versions
  in upgrade roadmap items are skipped.
- **Compose accessibility semantics pass**: Added missing
  `contentDescription` to interactive icons that lacked TalkBack
  element names: VideoWallpapersScreen hide/preview icon buttons,
  WallpaperDetailScreen palette color search dots, SoundDetailScreen
  favorite toggle icon. Verified existing `stateDescription`,
  `customActions`, `progressBarRangeInfo`, and decorative `null`
  contentDescription patterns are correct across all screens.
- **Reduced motion accessibility**: when the system `ANIMATOR_DURATION_SCALE`
  is 0 or the new manual "Reduce animations" toggle in Settings is enabled,
  all live wallpaper particle effects (weather, VFX, touch ripples) are
  disabled and the 30 FPS draw loop stops (static background only). Compose
  transitions already respect the system animation scale natively.
- **Opus audio output format**: AudioTrimmer now supports Opus codec
  (`libopus` at 48kbps in OGG container) for convert, trim-with-fade, and
  standalone fade operations. Sound editor adds a format picker chip row
  (MP3, OGG, Opus, WAV, FLAC, M4A) between fade controls and apply buttons.
  Converting reloads the waveform and resets trim/fade state for the new file.
- **"Set With..." intent-filter receiver**: other apps (gallery, file managers,
  browsers) can now delegate wallpaper-setting to Aura via `ACTION_ATTACH_DATA`
  with `image/*` MIME type. Aura opens the crop/edit/apply flow with the
  received image. Supports `content://` and `file://` URIs via
  `ContentResolver.openInputStream()`, in addition to existing HTTP(S) loading.
  Back navigation returns to the calling app.
- **Wallpaper action capabilities**: added `WallpaperLicensePolicy.kt` with
  per-wallpaper action-capability gates (APPLY/DOWNLOAD/SHARE/EDIT) mirroring
  the existing sound license system. Source-specific rules for Bing (download/
  edit confirmation, share disabled), Reddit (edit disabled), Community
  (confirmation for user-upload license), AI-generated (share confirmation),
  and Creative Commons license gates (NC confirmation, ND edit disabled).
  Unavailable-source wallpapers disable all actions.
- **yt-dlp CVE batch remediation**: expanded `ytdlp-cve-policy.json` from
  schema v1 (CVE-2026-26331 only) to v2 tracking all 5 yt-dlp CVEs
  (CVE-2026-26331, CVE-2026-50019, CVE-2026-50023, CVE-2026-50574,
  CVE-2025-54072). Forbidden options list now covers `--netrc-cmd`,
  `--cookies`, `aria2c`, `--downloader`, and `--exec`. Policy checker
  validates all 6 forbidden options; 9 unit tests cover v1 compat and v2
  rejection of each CVE-related flag. Risk Register consolidated to one row.
- **contentDescription audit (false positive)**: audited all 106
  `contentDescription = null` instances across 21 Compose UI files. Every
  instance is correctly null: icons inside labeled Buttons (text provides the
  label), icons inside IconButtons with `.semantics { onClick(label = ...) }`
  blocks, decorative status icons adjacent to text, thumbnail images, or
  leading/trailing chip/menu icons. No changes needed — the codebase already
  follows proper Compose accessibility patterns via semantics blocks.
- **Centralized notification channels**: extracted all notification channel
  definitions into `NotificationChannels.kt` singleton. `FreeVibeApp.onCreate()`
  creates all three channels (media_playback, daily_wallpaper,
  aura_rotation_triggers) at startup. Removed duplicate channel creation from
  `DailyWallpaperWorker` and `RotationTriggerService`; both now reference the
  centralized channel ID constants.
- **Provider policy report reason**: added `PROVIDER_POLICY` to
  `CommunityReportReason` for reporting community uploads that violate
  third-party provider terms (e.g., re-uploaded Pexels/Pixabay content).
- **Locale.ROOT sweep**: fixed 7 remaining machine-use case transformations
  across AI prompt normalization, wallpaper feed term matching, content source
  label formatting, rate-limit host matching, file extension checks, and daily
  notification source names. Prevents Turkish-locale garbling.
- **Localization extraction (editors + screens)**: moved all hardcoded strings
  from AiWallpaperScreen, WallpaperEditorScreen, WallpaperCropScreen,
  SoundEditorScreen, CollectionsScreen, CommunityReportsScreen,
  WallpaperDetailScreen, WallpapersScreen, VideoWallpapersScreen,
  CategoriesScreen, CreatorProfileScreen, LicensesScreen,
  WallpaperPreviewScreen, and VideoWallpaperPreviewScreen into strings.xml.
  Adds ~130 new string resources with shared common strings for repeated
  labels (Cancel, Back, Home, Lock, Both, Done, Save, Discard, etc.).
- **Premium UI feedback polish**: added a shared inline status banner for
  recoverable provider failures, surfaced last-good-result recovery on
  wallpapers/sounds/video, and tightened Settings/Favorites spacing, state
  labels, borders, and progress feedback.
- **Native compliance drift gate**: native/copyleft inventory checks now derive
  youtubedl-android and NewPipe coordinates from Gradle so dependency bumps
  cannot leave the reviewed payload lock checking stale versions.
- **YouTube extractor self-update**: added a manual Settings action for stable
  yt-dlp runtime updates, rollback until the next successful extraction, and
  diagnostics coverage for active extractor version/status.
- **Localization extraction batch**: moved navigation titles, community
  guidelines/report dialogs, and recent-search actions into Android string
  resources, then refreshed the hardcoded-string baseline.
- **Backup/restore path reconciliation**: startup now clears stale local paths
  for restored favorites/download history when app-private files or MediaStore
  rows are missing, while keeping remote item provenance visible.
- **Unified import validation**: favorites and collection imports now share
  HTTPS URL caps, text caps, source enum checks, duplicate identity handling,
  and oversized collection-token rejection.
- **Transactional collection import**: collection JSON imports now create the
  collection and all normalized items inside one Room transaction, with
  duplicate source-scoped wallpapers collapsed before insert.
- **Custom accessibility semantics**: added source-backed coverage for reusable
  Compose components, Settings rows/toggles, media cards, badges, waveform
  controls, and action labels.
- **External automation hardening**: gated Tasker/MacroDroid rotation broadcasts
  behind an explicit Settings opt-in, added a 30-second burst limit, surfaced
  last-trigger diagnostics, and documented the public intent contract.
- **Localization debt gate**: added a Compose hardcoded-string scanner, JSON
  baseline, migration plan, CI check, and focused tool tests so new
  user-visible Kotlin string literals cannot land unnoticed.
- **Community upload disclosure**: clarified that public community uploads store
  the listing category, public download URL, and sanitized file name alongside
  license, source, uploader, and tags.
- **Cloud Functions runtime migration**: moved the community Functions package,
  lockfile, and Firebase CI lane from Node 20 to Node 22, refreshed the backend
  manifest, and added a high/critical production audit gate for Functions
  dependencies.
- **Supply-chain CVE hardening**: constrained youtubedl-android's vulnerable
  `jackson-databind` and `commons-io` transitives to reviewed patched
  versions, then bumped the Gradle wrapper to 8.12.1 with the reviewed
  distribution checksum and policy-test coverage.
- **Background work device evidence gate (Cycle 160)**: added a checked
  device/emulator capture packet for WorkManager baseline, metered/Data Saver,
  low battery, Doze/App Standby, and rotation-trigger coalescing evidence,
  including adb commands, artifact paths, workflow wiring, and tests.
- **Background work action hints (Cycle 159)**: added Settings and support
  bundle action hints for Data Saver restrictions, metered waits, source
  failures, network/provider errors, Aura Originals validation retries,
  permission cues, apply failures, and WorkManager retry/failure states.
- **Background work support bundle live receipts (Cycle 158)**: merged live
  WorkManager, Data Saver, and persisted worker receipt details into the copied
  crash/support diagnostics bundle.
- **Background work persisted receipts (Cycle 157)**: added local worker
  last-run receipt storage for background work success, retry, failure, error
  class, and deferral reason, then surfaced those receipts in Settings
  diagnostics.
- **Background work Settings diagnostics (Cycle 156)**: added a local
  `Settings` > `Diagnostics` > `Background work` dialog backed by WorkManager
  unique-work `WorkInfo` state counts and `ConnectivityManager` metered/Data
  Saver receipts.
- **Background work diagnostics bundle section (Cycle 155)**: added a local
  crash/support bundle background-work section covering current unique work
  names, inferred enabled state, network posture, constraints, and explicit
  pending markers for live WorkInfo and Data Saver receipts.
- **Background work network posture gate (Cycle 154)**: added a checked worker network posture packet for connected versus unmetered WorkManager constraints, Data Saver diagnostic gaps, metered-network behavior, privacy surfaces, release risk, workflow wiring, and source-backed tests.
- **Background work scheduling ledger (Cycle 153)**: added a checked WorkManager scheduling packet for periodic auto wallpaper, daily wallpaper, weather refresh, Aura Originals download, and rotation trigger one-shots, including unique work names, enqueue policies, constraints, deferral reasons, workflow wiring, and source-backed tests.
- **Rotation trigger foreground-service policy gate (Cycle 152)**: added a checked `specialUse` foreground-service policy packet for `RotationTriggerService`, Play Console declaration text, owner demo-video evidence, workflow wiring, and source safeguards.
- **Rotation trigger boot permission gate (Cycle 151)**: removed the unused `RECEIVE_BOOT_COMPLETED` permission, documented that rotation triggers resume after opening Aura, and added a verify/release gate that blocks boot permission or boot receiver drift without updated release disclosures.
- **Store asset pipeline gate (Cycle 150)**: added a checked screenshot and feature-graphic capture plan covering Fastlane image paths, four planned phone screenshots, alt text, Play/F-Droid source rules, future asset-mode enforcement, and verify/release workflow wiring.
- **SBOM readiness gate (Cycle 149)**: added a checked SBOM readiness packet that keeps generation deferred until the N-1 toolchain upgrade while enforcing the current release evidence floor, future CycloneDX/SPDX artifact names, scope, sources, and workflow wiring.
- **Release metadata consistency gate (Cycle 148)**: added a checked release metadata packet that reconciles app package/version values, Fastlane text, README links, privacy URLs, Play/alternative-store packets, release preflight commands, and expected GitHub release artifacts.
- **Alternative-store disclosure gate (Cycle 147)**: added a checked GitHub/Obtainium/Izzy/F-Droid disclosure packet covering channel status, anti-feature notes, manifest permissions, reviewed network services, proprietary dependency markers, and Izzy submission notes.
- **Community Guidelines consent gate (Cycle 146)**: added versioned community guidelines acceptance before community feeds, uploads, votes, reports, blocks, follows, profiles, and startup identity warm-up, then wired a checked consent policy gate into verify and release.
- **Play App content packet gate (Cycle 145)**: added a checked owner-ready Play App content declaration packet covering ads, app access, target audience, content rating notes, Data safety, UGC, generated content, sensitive permissions, evidence paths, and owner actions.
- **SDK Data safety surface gate (Cycle 144)**: extended the Data safety matrix check with Gradle-marker-backed SDK rows for Firebase Auth, RTDB, Storage, Functions, App Check, and Play services ML Kit/module install surfaces.
- **Local storage Data safety gate (Cycle 143)**: extended the Data safety matrix check with source-backed local storage rows for DataStore, Room, SharedPreferences, diagnostics logs, app-private media, and cache surfaces, including backup/transfer posture.
- **Network Data safety surface gate (Cycle 142)**: extended the Data safety matrix check to reconcile every reviewed network endpoint with privacy rows for data types, sharing, retention, deletion, and user controls.
- **Privacy Data safety matrix (Cycle 141)**: added a manifest-permission privacy ledger and verify/release gate that blocks permission drift without reviewed data type, purpose, retention, deletion, denial, and Play declaration rows.
- **Privacy policy link gate (Cycle 140)**: added an in-app Settings privacy-policy link plus verify/release checks that keep the public policy URL aligned across Settings, README, Fastlane metadata, and release docs.
- **On-device wallpaper decision gate (Cycle 139)**: added an evidence packet and verify-time guard that keeps local wallpaper generation on hold until device, delivery, battery/thermal, license, moderation, fallback, and FOSS-channel criteria are met.
- **Store metadata preflight (Cycle 138)**: added a checked Fastlane text/version/privacy preflight, wired it into verify and release, shortened the Play short description, and added the public privacy-policy URL to full description metadata.
- **Prompt metadata retention cleanup (Cycle 137)**: stopped saving generated wallpaper prompt text in favorite names/tags and added generated PNG cleanup after generated favorite removal.
- **Generated request cost guardrails (Cycle 136)**: added generated wallpaper session request counting, in-flight request rejection, duplicate prompt/style confirmation, and Stability account/cooldown error copy.
- **Stability key policy sentinel (Cycle 135)**: tightened the provider credential storage guard so the Stability key must remain a DataStore-backed paid-sensitive secret with blank release defaults, explicit Clear control, and `stability.ai.key` redaction coverage.
- **Generated content reporting (Cycle 134)**: added generated wallpaper report actions, generated-content reason categories, backend reason allowlist support, and privacy/reporting runbook updates.
- **Generated wallpaper disclosure (Cycle 133)**: added a persisted prompt/privacy disclosure gate before Stability requests plus a Settings review/reset path and privacy runbook.
- **Provider key clear UX (Cycle 132)**: consolidated provider API-key dialogs around explicit Save, Clear, and Cancel actions, and extended the storage policy guard to fail if the Clear path disappears.
- **Provider credential APK scan (Cycle 131)**: added a release-stage APK scanner for nonblank provider credential values and wired it after signed APK packaging before release uploads.
- **Provider credential storage policy (Cycle 130)**: classified provider credentials, documented the app-private DataStore/no-Keystore decision, added a checked storage policy gate, and surfaced Freesound key clearing in Settings.
- **Cleartext release gate (Cycle 129)**: removed the ccMixter HTTP fallback and cleartext network-security exception, added a release cleartext guard with focused tests, and wired it into verify and release preflight before signed APK builds.
- **Network endpoint inventory (Cycle 128)**: added a reviewed endpoint manifest/runbook plus a verify-time scanner and live tool tests so new hard-coded app network hosts require auth/cache/fallback review.
- **Request redaction contract (Cycle 127)**: added a shared request redactor for provider query/header/local-property credentials, reused it in crash diagnostics, and redacted source-metrics failure details before Settings displays them.
- **Diagnostics redaction fixtures (Cycle 126)**: added provider-specific crash diagnostics fixtures for Wallhaven, Pixabay, Freesound, SoundCloud, Pexels, Settings, `local.properties`, file URIs, and app-private paths, and tightened assignment redaction for dotted provider properties.
- **Provider credential release guard (Cycle 125)**: added a release preflight that verifies Gradle provider-key defaults are blank, release CI writes blank optional provider keys before signed builds, and nonblank local provider keys fail unless explicitly allowed for an internal-build review.
- **Always-on backend tool tests (Cycle 124)**: wired the lightweight `test/tools` Python suite into the always-on verify job before Android setup, so policy and support-tool drift tests run on every push, pull request, and manual verify run.
- **GitHub workflow secret guard (Cycle 123)**: added a workflow secret-reference policy and verify-time scanner, limiting workflow secret use to the reviewed release signing secrets and blocking unreviewed secret refs, unreviewed env aliases, forbidden token shortcuts, and unexpected workflow files.
- **GitHub workflow permissions guard (Cycle 122)**: added a workflow event/job-permission policy and verify-time scanner for all workflows, blocking unreviewed triggers, permission drift, unexpected jobs, unexpected workflow files, and scalar `write-all` style permission declarations.
- **GitHub Actions allowlist guard (Cycle 121)**: added a workflow action allowlist and verify-time scanner for all workflow `uses:` references, blocking unexpected workflows, unreviewed actions, local actions, unpinned refs, and forbidden floating refs.
- **Gradle wrapper checksum guard (Cycle 120)**: pinned the Gradle 8.12 wrapper ZIP SHA-256 and added a verify-time wrapper policy check for distribution URL, checksum, URL validation, storage roots, and timeout drift.
- **GitHub security settings receipt (Cycle 119)**: added a private-evidence validator and redacted receipt generator for future owner/admin GitHub branch-protection, Dependabot, code-scanning, secret-scanning, and release-attestation settings proof.
- **Dependabot update policy guard (Cycle 118)**: added weekly Dependabot version-update coverage for GitHub Actions, Gradle, root npm, and Functions npm, plus a verify-time policy check for cadence, target branch, PR limits, labels, and commit prefix.
- **GitHub security workflow policy guard (Cycle 117)**: added a checked workflow policy for Dependency Review, OpenSSF Scorecard, and Release so verify fails when security triggers, permissions, attestation, SARIF upload, release bundle, or unsafe escape-hatch expectations drift.
- **Callable rollout evidence receipt (Cycle 116)**: added a private-evidence validator and redacted receipt generator for future live community callable rollout proof across all seven contracted callable surfaces.
- **Callable wire-protocol guard (Cycle 115)**: added a checked Android callable wire-protocol manifest and validator that keep all seven community callable client methods, payload schemas, Android input types, operation prefixes, App Check token choices, response IDs, and focused tests aligned with the backend callable contract.
- **Android profile edit callable migration (Cycle 114)**: added Android creator profile payload normalization, extended the shared callable client for `updateCreatorProfile`, routed creator profile saves through the callable when Firebase Auth is available, and added an edit action to the creator profile screen.
- **Android wallpaper upload finalizer callable migration (Cycle 113)**: added Android wallpaper upload metadata payload normalization, extended the shared callable client for `finalizeCommunityWallpaperUpload`, and routed post-Storage wallpaper upload metadata finalization through the callable when Firebase Auth is available.
- **Android sound upload finalizer callable migration (Cycle 112)**: added Android sound upload metadata payload normalization, extended the shared callable client for `finalizeCommunitySoundUpload`, and routed post-Storage sound upload metadata finalization through the callable when Firebase Auth is available.
- **Android user-block callable migration (Cycle 111)**: added Android block/unblock payload normalization, extended the shared callable client for `setCommunityUserBlock`, routed user block state changes through the callable when Firebase Auth is available, and preserved direct RTDB fallback only for compatibility cases.
- **Android follow callable migration (Cycle 110)**: added Android follow/unfollow payload normalization, extended the shared callable client for `setCreatorFollow`, routed creator follow state changes through the callable when Firebase Auth is available, and preserved direct RTDB fallback only for compatibility cases.
- **Android vote callable migration (Cycle 109)**: added Android vote payload normalization, extended the shared callable client for `recordCommunityVote`, routed vote submissions through the callable when Firebase Auth is available, and preserved direct RTDB fallback only for compatibility cases.
- **Android report callable migration (Cycle 108)**: added the Android Cloud Functions client dependency, a shared callable request/response adapter, limited-use App Check token selection for report submissions, callable report payload tests, and callable-first report submission with a compatibility fallback while deploy evidence is pending.
- **Wallpaper upload callable emulator coverage (Cycle 107)**: added RTDB-emulator-backed `finalizeCommunityWallpaperUpload` handler coverage for public metadata, owner index, quota, storage-path dedupe, and duplicate upload idempotency through the real Admin SDK backend.
- **Sound upload callable emulator coverage (Cycle 106)**: added RTDB-emulator-backed `finalizeCommunitySoundUpload` handler coverage for public metadata, owner index, quota, storage-path dedupe, and duplicate upload idempotency through the real Admin SDK backend.
- **User block callable emulator coverage (Cycle 105)**: added RTDB-emulator-backed `setCommunityUserBlock` handler coverage for private block rows, reverse-index rows, unblock removals, quota, dedupe, and no-op idempotency through the real Admin SDK backend.
- **Follow callable emulator coverage (Cycle 104)**: added RTDB-emulator-backed `setCreatorFollow` handler coverage for follow writes, unfollow removals, quota, dedupe, and no-op idempotency through the real Admin SDK backend.
- **Vote callable emulator coverage (Cycle 103)**: added RTDB-emulator-backed `recordCommunityVote` handler coverage for vote tally, nested and legacy voter markers, quota, dedupe, and repeat-vote idempotency through the real Admin SDK backend.
- **Report callable emulator coverage (Cycle 102)**: added RTDB-emulator-backed `submitCommunityReport` handler coverage for report, quota, dedupe, and duplicate report writes through the real Admin SDK backend.
- **Profile callable emulator coverage (Cycle 101)**: added `npm run test:functions-emulator` and an RTDB-emulator-backed profile handler test that verifies profile, quota, dedupe, and unchanged-profile idempotency writes through the real Admin SDK backend, then wired the script into the Firebase backend CI lane.
- **Creator profile callable handler (Cycle 100)**: implemented the `updateCreatorProfile` Functions handler with Auth/App Check enforcement, server-derived profile UID and timestamps, display-copy normalization, normalized-profile dedupe, quota checks, and focused unit coverage.
- **Community wallpaper upload callable handler (Cycle 99)**: implemented the `finalizeCommunityWallpaperUpload` Functions handler with Auth/App Check enforcement, server-allocated upload IDs, wallpaper metadata normalization, Storage path ownership checks, storage-path dedupe, public metadata and owner-index writes, and focused unit coverage.
- **Community sound upload callable handler (Cycle 98)**: implemented the `finalizeCommunitySoundUpload` Functions handler with Auth/App Check enforcement, server-allocated upload IDs, sound metadata normalization, Storage path ownership checks, storage-path dedupe, public metadata and owner-index writes, and focused unit coverage.
- **Community block callable handler (Cycle 97)**: implemented the `setCommunityUserBlock` Functions handler with Auth/App Check enforcement, block/unblock payload normalization, state-aware dedupe keys, private and reverse-index writes, and focused unit coverage.
- **Creator follow callable handler (Cycle 96)**: implemented the `setCreatorFollow` Functions handler with Auth/App Check enforcement, follow/unfollow payload normalization, state-aware dedupe keys, no-op idempotency, UTC quota reservation, and focused unit coverage.
- **Community vote callable handler (Cycle 95)**: implemented the `recordCommunityVote` Functions handler with Auth/App Check enforcement, vote-key normalization, existing-voter idempotency, UTC quota reservation, dedupe handling, and focused unit coverage.
- **Community report callable handler (Cycle 94)**: implemented the `submitCommunityReport` Functions handler core with Auth/App Check identity enforcement, server-derived reporter UID, HTTPS/source validation, UTC quota reservation, dedupe handling, and focused unit coverage.
- **Cloud Functions scaffold (Cycle 93)**: added a Node 20 TypeScript `functions/` project with App Check/Auth fail-closed callable exports, a manifest-synced callable contract mirror, a UTC quota decision engine, backend manifest coverage, and CI tests.
- **Hosted deletion page template gate (Cycle 92)**: added checked publishable copy and a validator for the hosted account deletion request page before the owner assigns a live HTTPS URL.
- **Callable contract manifest gate (Cycle 91)**: added a backend JSON manifest and validator for the community callable quota contract, including UTC quota-day boundary, App Check/Auth requirements, final write paths, and CI coverage.
- **Upload deletion execution receipt (Cycle 90)**: added a redacted receipt builder for owner/admin public-upload deletion evidence after clean account-deletion upload plans, covering Storage, metadata, owner-index, and tombstone completion.
- **Auth deletion execution receipt (Cycle 89)**: added a redacted receipt builder for owner-approved Firebase Auth deletion evidence, including UID/support validation, post-delete not-found verification, and private evidence hashing.
- **Account deletion web URL gate (Cycle 88)**: added a privacy-policy-backed hosted deletion URL manifest and validator that keeps the web request route in an explicit pending owner-publication state until a live HTTPS URL is linked from policy and support docs.
- **Account deletion upload handoff (Cycle 87)**: added a private upload deletion planner that consumes the Auth package, enumerates owned public sound/wallpaper uploads with valid Storage handles, and blocks rows needing backfill or manual review before owner/admin deletion workflow execution.
- **Account deletion Auth package (Cycle 86)**: added a private Firebase Auth deletion package builder that requires matching request-code lookup and backend completion evidence before exposing the full UID to owner-approved Auth deletion.
- **In-app local community cleanup (Cycle 85)**: Settings > Community identity now offers `Clear local` for the current device fallback identity, refreshes the redacted summary after clearing, and keeps backend/Auth/upload deletion in the support chain.
- **Account deletion cleanup sequence (Cycle 84)**: added a post-completion sequencing tool that requires a completed backend receipt before ordering requester local cleanup, operator Firebase Auth deletion, and public upload deletion handoff.
- **Account deletion web intake (Cycle 83)**: added a private hosted-form contract and validator that hashes requester contact/statement fields, requires deletion/retention/public-upload attestations, and emits a redacted intake receipt before operator lookup.
- **Account deletion completion receipt (Cycle 82)**: added a redacted receipt builder that validates applied REST receipts against private executor packages, rejects dry-run receipts, and keeps full UIDs, RTDB paths, database hosts, update payloads, and access tokens out of requester-facing artifacts.
- **Account deletion REST executor (Cycle 81)**: added a guarded RTDB REST executor with dry-run default, explicit request-code and plan-hash confirmations for apply mode, and unit coverage for PATCH and bearer-token handling.
- **Account deletion executor package (Cycle 80)**: added a private package builder that validates account deletion plan, review, and simulation receipts before emitting the RTDB null-update payload for a future trusted executor.
- **Account deletion apply simulator (Cycle 79)**: added an offline backend simulator that verifies reviewed deletion plans, applies null updates to an RTDB export copy, and emits hashed receipts without contacting Firebase.
- **Account deletion review gate (Cycle 78)**: added a backend review tool that cross-checks deletion request-code lookup output against dry-run RTDB null-update plans and emits redacted receipts before any future trusted apply step.
- **Deletion request code lookup (Cycle 77)**: added a backend lookup tool that maps shared `AURA-` deletion request codes to candidate UID evidence in RTDB exports, with unit coverage and backend CI change detection.
- **Community deletion request routing (Cycle 76)**: the Community identity dialog can now share a redacted deletion request draft, and support docs describe the private request/operator handling flow without exposing full Firebase UIDs.
- **Community identity request surface (Cycle 75)**: Settings now exposes the current community auth label, redacted identity suffix, and a deletion request code when a Firebase identity exists without creating a new identity just by opening the panel.
- **Account deletion dry-run planner (Cycle 74)**: added a backend tool and policy doc that plan RTDB marker deletion for vote markers, follows, creator profiles, block indexes, and community shares while retaining aggregate counts and moderation audit records.
- **Report/profile block actions (Cycle 73)**: community reports now carry optional uploader UID metadata, admin report cards can block reported community uploaders, and creator profile rows expose confirmed block actions that immediately remove matching creator rows.
- **Blocked creators review (Cycle 72)**: Settings now shows blocked community creators with reason/timestamp metadata and per-row unblock actions backed by `CommunityBlockRepository`.
- **Visible block creator actions (Cycle 71)**: community sound and wallpaper detail surfaces now expose confirmed block actions when uploader identity is available, write through `CommunityBlockRepository`, and remove matching uploader rows from the current UI state.
- **Community block-user filtering (Cycle 70)**: added Android block-list repository reads/writes, kept public browsing from creating an identity solely for filtering, and filtered community sound feeds, wallpaper feeds, and creator profile lists by private block state.
- **Public takedown copy (Cycle 69)**: community upload dialogs now disclose public listing behavior and rights-takedown outcomes, report dialogs explain the private rights-takedown route, and owner-delete confirmations describe public metadata/index removal plus private moderation record retention.
- **Community block-user policy (Cycle 68)**: reserved private user block lists and admin reverse indexes in RTDB rules, added emulator coverage, and extended the callable quota contract with `setCommunityUserBlock`.
- **Community deletion tombstones (Cycle 67)**: owner and admin upload deletes now write private deletion tombstones with owner-scoped Storage handles, RTDB rules coverage, and a retention policy for deleted upload evidence.
- **Legacy upload backfill planning (Cycle 66)**: added a dry-run RTDB backfill planner for legacy community uploads missing `storagePath` and owner indexes, with tests for URL parsing, update generation, and unsafe-row blocking.
- **Community Storage lifecycle policy (Cycle 65)**: added an offline orphan-report tool and unittest, documented the no-auto-delete policy for committed upload prefixes, and defined the two-report manual cleanup gate.
- **Community backend deploy evidence (Cycle 64)**: added a deterministic Firebase backend manifest, CI manifest check, and deploy/rollback runbook for Realtime Database and Cloud Storage rules changes.
- **Callable quota contract (Cycle 63)**: community quota policies now include callable function names, payload schemas, final write paths, protected ledger coverage, and limited-use App Check token decisions for reports, uploads, votes, follows, and profile edits; added the backend migration runbook.
- **Closed report review filters (Cycle 62)**: the admin community report queue now switches between Open, Hidden, Dismissed, and Restored status filters, including from the empty state, so closed moderation outcomes remain reviewable.
- **Admin upload delete actions (Cycle 61)**: custom-claim admins can delete qualifying rights-reported community uploads from the report queue; the flow records a `DELETE` takedown receipt, hides the content, deletes the Storage object, removes upload metadata/index rows, and marks the receipt succeeded or failed for retry evidence.
- **Rights takedown receipts (Cycle 60)**: hiding a rights report for a community sound or wallpaper now records a private admin takedown receipt with the current upload metadata path, Storage deletion handle, uploader UID, resolver UID, timestamp, and RTDB rules/emulator coverage that reject stale or mismatched handles.
- **Firebase rules CI gate (Cycle 59)**: the main verify workflow now detects Firebase rules/config/test/runbook changes, installs pinned npm tooling, and runs the combined RTDB + Storage emulator suite.
- **RTDB rules harness (Cycle 58)**: added Realtime Database emulator config and tests for community upload metadata, owner upload indexes, reports, report resolutions, quota/dedupe ledgers, and collection shares; aligned collection share rules to `shared_collections` with `createdByUid`; and made `database.rules.json` emulator/deploy-compatible.
- **Storage rules harness (Cycle 57)**: added tracked Firebase Storage rules, Firebase emulator config, a local npm rules-unit-testing harness, and Storage emulator tests for owner-only community upload writes/deletes, MIME/size ceilings, public reads, and unmanaged path denial.
- **Visible community owner deletes (Cycle 56)**: owner-owned community sound and wallpaper detail surfaces now show delete actions only when Firebase metadata proves the signed-in owner and a `storagePath` deletion handle; confirmations call the existing blob and metadata delete paths.
- **Community upload deletion handles (Cycle 55)**: new community sound and wallpaper uploads now store canonical Storage paths, write private owner indexes, and expose repository owner-delete methods that remove Storage blobs plus public metadata/index rows for new uploads.
- **Community quota policy (Cycle 54)**: added typed quota/rate-limit rows for reports, sound uploads, wallpaper uploads, votes, follows, and profile edits, reserved admin-only RTDB quota/dedupe ledgers, and documented the App-Checked callable migration path.
- **Firebase App Check client rollout (Cycle 53)**: debug builds now install the Firebase App Check debug provider, release builds install the Play Integrity provider before Firebase-backed community startup work, and the rollout runbook covers debug tokens, side-loaded distribution settings, metrics burn-in, and enforcement gates.
- **Admin report review (Cycle 52)**: custom-claim admins can open Settings > Community reports, review open reports, hide reported content through the global moderation list, dismiss reports, or restore hidden content with resolution metadata.
- **Community report queue intake (Cycle 51)**: sound and wallpaper detail screens now submit private reports with rights/source-removed/safety/spam/other reasons, source/license/uploader context, RTDB report and resolution rules, and admin resolution metadata.
- **Community upload rights metadata (Cycle 50)**: community sound and wallpaper uploads now require rights attestation, selected CC0/CC BY/CC BY-NC metadata, optional HTTPS source URLs, RTDB rule validation, community sound license gates, and wallpaper license detail display.
- **Sound license capability gates (Cycle 49)**: sounds now derive action capabilities from source/license/provenance metadata; YouTube apply/download requires confirmation, SoundCloud is link-only until reviewed, missing remote licenses disable live-source actions, saved sound favorites preserve license metadata, and sound shares include source/uploader/license provenance.
- **Provider removal reconciliation (Cycle 48)**: explicit 404/410/gone/removed/deleted provider failures now mark saved wallpaper/sound favorites and matching download-history rows as source-unavailable during apply/download paths.
- **Pexels enhancement guardrails (Cycle 47)**: Discover and video-wallpaper discovery now treat Pexels as an enhancement source; Pexels-only batches are dropped, disabled-Pexels Discover still returns Wallhaven/Pixabay fallback inventory, and Pexels photo rows keep creator/source-page context.
- **Saved-source availability states (Cycle 46)**: favorites and download history now persist a source-availability state; marked items show "Source unavailable" in saved surfaces and detail screens, hide live-source affordances, and keep local saved wallpaper paths usable.
- **Generated wallpaper source switch (Cycle 45)**: Settings now has a default-on generated-wallpapers source switch; disabled mode hides generation entry points, blocks Stability requests before prompt or key validation, and keeps saved generated local wallpapers visible.
- **Pixabay video request-cache and backoff (Cycle 44)**: Pixabay video metadata now uses an app-private 24-hour fresh-cache path before API calls, persists 429 backoff from `Retry-After` or `X-RateLimit-Reset`, and falls back to stale cached video rows during active backoff.
- **Pixabay photo request-cache and backoff (Cycle 43)**: Pixabay photo results now use a 24-hour fresh-cache path before API calls and 429 responses set an in-session backoff from `Retry-After` or `X-RateLimit-Reset`; video metadata policy handling followed in Cycle 44.
- **Wallhaven source switch (Cycle 42)**: Settings now has a default-on Wallhaven source switch; disabled mode hides Wallhaven browsing and color/random/similar actions, removes Wallhaven from rotation pickers, skips Wallhaven API calls before key reads/cache fallback, and records disabled diagnostics separately from outages.
- **Bing Daily source switch (Cycle 41)**: Settings now has a default-on Bing Daily source switch; disabled mode skips daily-image API calls before cache fallback or Retrofit use, hides Bing from rotation pickers, and records disabled diagnostics separately from outages.
- **Community source switch (Cycle 40)**: Settings now has a default-on Community source switch; disabled mode skips startup identity warm-up, hides community tabs/uploads/votes/creator profile entry points, blocks feed/upload/follow Firebase calls, and records disabled diagnostics separately from Firebase outages.
- **Pexels and Pixabay source switches (Cycle 39)**: Settings now has default-on Pexels and Pixabay source switches; disabled mode hides their wallpaper tabs, skips Discover/search/style-biased/video API calls before bundled keys are read, removes disabled Pixabay from rotation pickers, and records disabled source diagnostics separately from outages.
- **Reddit source switch (Cycle 38)**: Settings now has a default-on Reddit feature switch; disabled mode hides Reddit wallpaper browsing, skips daily picks, background rotations, repository calls, and video wallpaper discovery, and records disabled source diagnostics separately from outages.
- **YouTube legal-mode switch (Cycle 37)**: Settings now has a default-on YouTube feature switch; disabled mode hides YouTube sound browsing, falls back to bundled sounds, skips YouTube video wallpaper discovery, blocks stream resolution before cache/downloader use, and records disabled source diagnostics separately from outages.
- **Provider runtime controls (Cycle 36)**: added a checked runtime-control matrix for every content source, documented current disabled-provider behavior, and identified YouTube as the next legal-mode/offline-risk switch.
- **Generated notice metadata parity (Cycle 35)**: `tools/dependency_notice_lock.py` now has a `check-metadata` mode, and PR/main plus release workflows fail when raw Google OSS metadata rows no longer match the reviewed dependency notice lockfile.
- **Generated notice search and review markers (Cycle 34)**: the in-app generated dependency notice viewer now supports filtering by dependency name or license label and highlights generated rows that map to curated high-risk dependency review surfaces.
- **In-app generated dependency notices (Cycle 33)**: Settings > Open source licenses now reads generated Google OSS raw resources directly, lists generated dependency notices in-app, and opens full notice text without adding the stock Play services OSS licenses runtime dependency.
- **Raw Google OSS input retention (Cycle 32)**: documented that `GOOGLE-OSS-RAW-INPUTS.zip` stays attached to every tagged public release, clarified release/dry-run docs, and kept the bundle validator enforcing the archive in release files, checksums, and notes.
- **Dependency license policy gate (Cycle 31)**: added `docs/legal/dependency-license-policy.json` and `tools/dependency_license_policy.py`, then wired PR/main verification and release builds to fail unknown, disallowed, or unreviewed curated license IDs before publishing release artifacts.
- **FFmpeg source-correspondence evidence (Cycle 30)**: native compliance now extracts embedded FFmpeg 7.1.1 configure lines and license-mode flags from the resolved youtubedl-android FFmpeg payload, locks those facts for drift review, and adds `docs/legal/ffmpeg-source-correspondence.md` as the release-owner checklist for remaining Termux source/build-log evidence.
- **Generated notice access in Settings (Cycle 29)**: Settings > Open source licenses now starts with release notice cards for `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, and `NATIVE-COMPLIANCE.md`, while keeping manual library rows and content-source disclosures separate.
- **Raw release notice input archive (Cycle 28)**: release builds now create `GOOGLE-OSS-RAW-INPUTS.zip` with generated `dependencies.json`, `third_party_license_metadata`, `third_party_licenses`, and a manifest, then include the archive in checksums, release notes, workflow artifacts, tagged release assets, and bundle validation.
- **Release artifact dry-run validation (Cycle 27)**: added `tools/release_artifact_bundle_check.py` and a release dry-run runbook so manual release workflow runs and tag releases fail when the final APK/notices/native/checksum/release-note bundle is incomplete or internally inconsistent.
- **Curated dependency overlay gate (Cycle 26)**: added `docs/legal/dependency-notice-overrides.json` and `tools/dependency_overlay_check.py` so PR/main verification and release builds require reviewed source URLs, license IDs, usage notes, and release-review notes for high-risk dependencies and native payloads.
- **Native compliance drift gate (Cycle 25)**: `tools/native_compliance_inventory.py` now writes/checks `docs/legal/native-compliance.lock.json`, and PR/main verification plus release builds fail when native/copyleft artifact hashes or extracted payload facts drift without review.
- **Dependency notice drift gate (Cycle 24)**: added `tools/dependency_notice_lock.py` and `docs/legal/dependency-notices.lock.json` so PR/main verification and release builds fail when generated release dependency notices drift without review.
- **Native compliance packet (Cycle 23)**: release builds now generate `NATIVE-COMPLIANCE.md`, include it in `SHA256SUMS.txt`, and upload/attach it beside tagged APK releases. Added `tools/native_compliance_inventory.py` and committed `docs/legal/native-compliance.md` with youtubedl-android, yt-dlp/Python, QuickJS, FFmpeg, and NewPipeExtractor payload evidence.
- **Release third-party notices (Cycle 22)**: release builds now generate Google OSS license outputs, convert them to `THIRD-PARTY-NOTICES.md`, include the notices in `SHA256SUMS.txt`, and upload/attach the notices beside tagged APK releases. Added `tools/google_oss_to_markdown.py` and documented the local notice-generation path.
- **Provider disclosure matrix (Cycle 17 partial)**: Settings > Open source licenses now gets content-source disclosures from a central `ProviderDisclosure` model covering every `ContentSource`, including dormant legacy sources, local media, community uploads, bundled Aura Picks, and AI-generated content. Added `docs/legal/provider-policy.md`, expanded visible runtime/native dependency notice rows, and added a unit test that fails when new content sources lack policy coverage.
- **Baseline Profile + macrobenchmark harness (L-8/U-13 / Cycle 2 P1 partial)**: added a `:baselineprofile` producer module, `ProfileInstaller`, a shell-profileable manifest entry, startup and grid-scroll Macrobenchmark tests, manual self-hosted physical-device CI artifact upload, and the runbook for generating/attaching Baseline Profile and frame-timing evidence. Physical-device generation is pending because no adb target is attached locally.
- **Developer verification + Izzy prep (NX-8 / Cycle 2 P1)**: release notes now include Android developer verification status for `com.freevibe`, and new distribution docs capture the owner-only ADC/PDC package registration path, IzzyOnDroid submission checklist, F-Droid blocker state, and branch-protection owner action for requiring `verify`.
- **Supply-chain CI follow-up (NX-8/NX-12 / Cycle 2 P1)**: OpenSSF Scorecard now runs as SARIF-only with job-scoped code-scanning upload permissions, and Gradle dependency verification metadata includes the clean-runner JUnit BOM module hashes and Linux `aapt2` artifact exposed by CI.
- **Crash/ANR diagnostics (NX-8 / Cycle 2 P1)**: Settings now exposes a local-only crash diagnostics bundle with last crash timestamp, manual Copy/Share actions, app/Android/ABI/source context, reproduction fields, and sanitized `crash.log` tail. Added a crash report issue template, support docs, sanitizer/parser tests, and the missing Windows `aapt2` dependency-verification checksums uncovered by the focused test run.
- **Supply-chain verification (NX-8/NX-12 / Cycle 2 P1)**: release workflow now grants attestation permissions and uses `actions/attest@v4` against `SHA256SUMS.txt`, release notes include the artifact attestation URL, pull requests run Dependency Review, OpenSSF Scorecard runs on main/schedule/manual triggers with SARIF upload, `gradle/verification-metadata.xml` records resolved dependency checksums, and `docs/distribution/supply-chain.md` documents release verification and deferred SBOM scope.
- **Distribution channel strategy (NX-8 / Cycle 2 P0)**: new `docs/distribution/channel-strategy.md` records the full-vs-foss decision. Aura stays full-only for GitHub Releases/Obtainium and treats IzzyOnDroid as the realistic near-term app-store target; F-Droid mainline is blocked until Firebase, the Google Services plugin, and Play Services ML Kit are isolated behind a real FOSS flavor or removed. New `tools/fdroid_preflight.py` provides a no-build check that reports the current blocker state.
- **Release integrity (NX-8 / Cycle 2 P0)**: `.github/workflows/release.yml` now builds signed `assembleRelease` artifacts instead of debug APKs, restores signing material from GitHub secrets, rejects debuggable APKs, runs `apksigner verify --print-certs`, and publishes `SHA256SUMS.txt` plus release notes with versionCode/versionName and signing certificate SHA-256. New distribution docs cover required secrets, local release verification, and Obtainium checksum checks.
- **Unit-test harness repair**: stale JVM test fixtures now construct `SelectedContentHolder` with mocked SharedPreferences + Moshi, wire the Smart Crop detector dependency into crop ViewModel tests, stub NX-6 rotation-trigger preference flows, and use a pure `SmartCropCalculator.SubjectBounds` overload so local tests do not depend on stubbed Android `RectF` constructors.
- **Android 17 EyeDropper API (NX-10)**: new "Pick colour" FAB on the wallpaper Discover tab opens the system EyeDropper overlay on Android 17+ devices. The picked colour seeds a Wallhaven `colors=` search. Raw-string Intent integration is compatible with compileSdk 35 today and resolves to a direct API call once the toolchain bumps. FAB auto-hides on builds where the system EyeDropper app isn't installed (un-updated GSI).
- **Photo Picker 9:16 portrait grid (NX-11)**: wallpaper community upload, collection QR import, and parallax-from-photo gallery picker now request the Android 17 `PhotoPickerUiCustomizationParams` 9:16 portrait aspect ratio via a drop-in `AuraPickVisualMedia` subclass. Reflection ships the runtime behaviour at compileSdk 35; becomes a straight-line API call once the toolchain bumps. Android 16 and below pass through transparently to the existing 1:1 grid.
- **Smart Crop video variant (NX-3)**: TopAppBar "Smart" action on the video crop screen extracts the loop-start frame, runs the same subject segmentation wallpaper crop uses, and pans the video so the subject lands at the viewport centre. Keeps the user's chosen zoom (different from the wallpaper variant, which auto-zooms). Toasts a "drag to position" fallback when segmentation can't find a subject.
- **Smart Crop (NX-3)**: new "Smart Crop" chip on the wallpaper crop screen runs ML Kit Subject Segmentation against the loaded bitmap, then centres the detected subject in the 9:16 viewport at ~75 % coverage with a fill-viewport floor. Falls back to "Couldn't detect a subject — drag to position manually" when segmentation returns no foreground subject. Seven unit tests cover the pure-geometry helper.
- **Editor unsaved-changes guard (NX-13)**: backing out of the wallpaper editor with non-default filters or the sound editor with active trim / fade settings now prompts a "Discard edits?" confirmation instead of silently throwing away the work. Discard resets state and exits; Keep editing dismisses.
- **Contributor docs (U-12)**: new `CONTRIBUTING.md` covers the charter (no ads, no tracking, AMOLED-first, free by default), build steps, code style, commit conventions, and test guidance. New `ARCHITECTURE.md` describes the layered model, package map, key abstractions, process-death + live-wallpaper engine discipline, and design system rules.
- **Rotation triggers (NX-6)**: opt-in per-unlock and screen-off pre-stage rotation via a new `RotationTriggerService` foreground service. Two Settings toggles ("Change on every unlock" + "Pre-stage on screen off") gate the service lifecycle; users see a low-priority notification only when at least one trigger is on. Each fire enqueues a one-shot expedited `AutoWallpaperWorker` that respects the existing rotation source / target / constraint prefs.
- **Tasker hook (L-2)**: new `com.freevibe.action.ROTATE_NOW` + `com.freevibe.action.SHUFFLE_NOW` exported broadcast actions. Tasker / MacroDroid / adb-shell scripts can wire Aura into calendar events, geofences, Bluetooth-connected, etc. with a one-line Send Intent.
- **Lockscreen widget (NX-2)**: widget category bumped from `home_screen` to `home_screen|keyguard` so Android 16 QPR2+ users can place the existing widget on the lockscreen surface without code changes. Older Android versions silently ignore the keyguard bit.
- **Back-press cancellation (NX-13)**: pressing back during a Stability AI generation now cancels the in-flight job and saves the user's API credit budget. Video crop guards against accidental back-out mid-FFmpeg with a "Cropping in progress" toast.
- **Process-death selection survival (NX-4)**: `SelectedContentHolder` now persists the single selected wallpaper + selected sound to a SharedPreferences JSON snapshot on every selection, so detail screens are no longer blank after process death. Pager list is intentionally still in-memory only.
- **CI verification (NX-12)**: new `.github/workflows/verify.yml` runs `assembleDebug` + `testDebugUnitTest` + `lintDebug` on every push to main and every PR. Uploads test + lint reports as artifacts on failure.
- **Fastlane refresh (NX-8 partial)**: fastlane metadata bumped from stale FreeVibe naming to Aura with current feature set; `changelogs/111.txt` lands v6.31.0 release notes; new `obtainium.json` at repo root lets Obtainium users track Aura via the GitHub Releases feed.

## v6.31.1
- **Fix: Sounds/search crash on Android < 13 (issue #2)**: enabled core library desugaring (`desugar_jdk_libs:2.1.5`). NewPipeExtractor's `Utils.encodeUrlUtf8` calls `URLEncoder.encode(String, Charset)` — an API 33 method — on every YouTube search, which threw `NoSuchMethodError` and crashed the app the moment the Sounds tab loaded on Android 8–12. Desugaring backports the method down to the minSdk 26 floor.

## v6.31.0
- **Shareable collections**: wallpaper collections now publish Firebase-backed Aura links, include those links in the system share sheet, and can display scannable QR codes.
- **Collection import**: Collections now has an import action for pasted Aura links, shared JSON files, and QR images, with imported media remaining URL-backed until opened or applied.
- **Deep-link support**: `aura://collection/import/{token}` and shared JSON intents route directly into the Collections import flow.

## v6.30.0
- **Creator profiles**: Settings now links to a creator profile dashboard with current identity, upload count, total votes, saved favorites count, followed creators, followed uploads, and top creators leaderboard.
- **Follow creators**: creator follows persist through Firebase RTDB and the profile screen surfaces new uploads from followed creators.
- **OAuth guardrail**: Google sign-in remains disabled until the Firebase config includes an OAuth client; anonymous Firebase identity continues to back community uploads.

## v6.29.0
- **Community wallpaper uploads**: Wallpapers now includes a Community source where users can pick gallery images, crop them automatically to the phone aspect ratio, and upload compressed JPEGs under 4 MB to Firebase Storage.
- **Wallpaper upload metadata**: community uploads require name, category, and tags, then store Palette-derived colors, dimensions, uploader label, and Firebase RTDB metadata for discovery.
- **Wallpaper community moderation**: community wallpapers use the existing vote/hide controls and hide negative-score uploads from the feed.

## v6.28.0
- **Community sound uploads completed**: Sounds > Community can now record from the microphone or pick an audio file, then require name, category, and searchable tags before upload.
- **Community Picks**: top-voted community sounds surface in a dedicated section, while community results are sorted by vote count and hidden once the user hides them.
- **Moderation polish**: negative-score community uploads are filtered out of the feed, and sound cards expose upvote/hide actions for uploaded content.

## v6.27.0
- **Video battery dashboard**: Settings > Video Wallpapers now shows live device battery, wallpaper-service heartbeat, effective FPS, media type, presentation mode, and estimated impact.
- **Auto battery saver**: Video/GIF live wallpapers can automatically cap playback at 15 FPS when the device drops below 15% battery and is not charging.
- **Debug FPS overlay**: Canvas-rendered motion wallpapers can show a compact FPS readout for development and frame-pacing checks.

## v6.26.0
- **Touch-reactive live wallpaper effects**: Weather live wallpapers now support transient touch ripples and spark bursts, rendered as bounded Canvas overlays.
- **Settings control**: Settings > Smart Features adds Touch effects with Off, Subtle ripples, and Ripples + sparkles modes.
- **Guardrails**: touch bursts are capped and expire quickly so inactive wallpapers do not keep extra work alive.

## v6.25.0
- **Video timeline thumbnails**: Loop & Crop now renders a bounded strip of sampled video frames beneath the loop scrubber, with graceful fallback to the plain slider when extraction is unavailable.
- **Frame sampling guardrails**: thumbnail positions are evenly spread across the clip and capped to six frames to avoid expensive work on long videos.
- **Phase 5.2 completion**: roadmap now marks the video loop editor complete: thumbnails, loop range selection, loop preview, and FFmpeg trim/crop export are all implemented.

## v6.24.0
- **Video loop trim editor**: the crop editor is now a Loop & Crop flow with start/end range controls and a preview that loops only the selected segment.
- **Trimmed video export**: FFmpeg crop output now includes the selected `-ss`/`-t` range, so applied live wallpapers skip intros/outros instead of always exporting the full clip.
- **Loop helper coverage**: added focused tests for loop-range coercion and FFmpeg trim argument formatting.

## v6.23.0
- **Video wallpaper presentation controls**: online video apply now offers Fill and Fit before setup. Fill keeps the premium full-screen crop behavior, while Fit preserves the complete frame with letterboxing.
- **Runtime scale-mode support**: `VideoWallpaperService` now reads the selected scale mode for both MediaPlayer videos and canvas-rendered GIF wallpapers.
- **Roadmap closure**: Phase 5.1 fit/fill/crop controls are now represented in the apply flow; remaining video work is the deeper loop trim/timeline editor.

## v6.22.0
- **Local video/GIF wallpapers**: Video Wallpapers and Settings now open a single system picker for local `video/*` clips and animated GIFs, then copy the selection into Aura-managed storage for live wallpaper setup.
- **Animated GIF live wallpaper playback**: `VideoWallpaperService` now detects `.gif` selections and renders them through a bounded canvas loop while keeping the existing MediaPlayer path for videos.
- **Import UX cleanup**: removed the dead "GIF not supported" Settings entry, updated gallery actions and fallback toasts to use motion-wallpaper copy, and expanded storage tests for GIF/MOV/MKV extension handling.

## v6.21.0
- **YouTube-only sound feed**: Sounds browsing and in-tab search now use YouTube results only. Audius is removed from the active Sounds experience and user-facing source copy; legacy Freesound attribution remains only for older saved content.
- **Intent-specific YouTube discovery**: default sound searches now seed from `Ringtones`, `Notifications`, and `Alarms`, then add one precise sound-effect query per tab. Duration filters clamp notifications to very short clips, keep alarms short and direct, and avoid long ringtone compilations.

## v6.20.0
- **Wallpaper detail overlay placement**: the compact wallpaper apply card now sits lower on devices with three-button navigation, using the previously empty bottom inset so more of the wallpaper remains visible above the controls.

## v6.19.0
- **Wallpaper detail visibility**: opening a wallpaper detail now keeps the image visible by default with a compact bottom action card instead of the full metadata/apply panel covering the wallpaper. Dense metadata, palette, tags, and extended actions remain available from Details, with a clear Show image action to collapse the panel again.

## v6.18.0
- **Sound source cleanup**: Sounds browsing and search now aggregate only Freesound, Audius, and YouTube, removing Aura Picks/bundled results, ccMixter, SoundCloud, and the old Openverse fallback from the user-facing sound feed.
- **YouTube readiness**: Opening or clearing the YouTube sounds tab now loads a default YouTube query automatically, and YouTube play buttons show a loading spinner plus clearer copy while the stream resolves or buffers before playback starts.

## v6.17.0
- **Secondary-flow premium polish**: refined the post-browse UX for sound detail, contact assignment, wallpaper preview, video preview, wallpaper edit/crop, and sound trim/edit flows so recovery, permission, loading, empty, and action states now match the v6.16 design system instead of falling back to ad hoc centered spinners and copy.
- **First-run finish**: tightened onboarding status labels, feature badges, page indicators, and navigation buttons to the same rectangular 4-12dp shape language, and simplified the first welcome page so the primary CTA is not crowded by a partially visible feature card.
- **Contact ringtone assignment**: replaced plain permission prompts and empty contact views with shared `AuraStateCard` recovery affordances, upgraded contact search to the shared compact search field, switched circular initials to rectangular avatars, and added per-contact applying feedback while assignments run.
- **Sound detail quality**: upgraded the waveform play control, permission warning, secondary action row, disabled share handling, similar-sound skeleton loading, and empty-similar state for clearer hierarchy, stronger touch targets, and more trustworthy feedback.
- **Editor and preview consistency**: brought wallpaper editor/crop and sound editor unavailable/loading/first-run states onto the shared state-card pattern, tightened chip/button radii to the 4-12dp system, clarified audio editor microcopy, and normalized preview action controls.

## v6.16.0
- **Premium UX polish pass**: tightened Aura's Compose design system around neutral AMOLED surfaces, brass/mist/coral accents, rectangular 4-12dp radii, zero letter spacing, calmer elevation, and no pill-shaped status backdrops.
- **Navigation and component consistency**: removed decorative gradient orbs from the app root, replaced Material pill indicators/badges with quieter rectangular count badges, normalized `GlassCard`, `HighlightPill`, search dropdowns, bottom navigation, cards, settings rows, sheets, preview surfaces, and diagnostic metrics.
- **States and feedback**: added a shared `AuraStateCard` pattern and applied it to wallpapers, sounds, video wallpapers, downloads, favorites, collections, wallpaper history, and loading/error/empty states so recovery copy, actions, icons, and spacing feel consistent.
- **Workflow clarity**: clarified the wallpaper generation entry point, improved sound/video retry and gallery fallback affordances, improved API-key visibility accessibility copy, and made empty states explain the next useful action instead of stopping at "nothing here."
- **Verification**: `assembleDebug`, `testDebugUnitTest`, and `lintDebug` are green. USB install was not performed because the connected phone already has `com.freevibe` installed with a different signing key; uninstalling it would remove or disturb the user's installed app.

## v6.15.0
- **Deep audit pass** — eleven real bugs found in the v6.13–v6.14 deltas (AI wallpaper, Phase 6.2 dark/light auto-switch, Phase 6.4 adaptive tint, Phase 2.5 seasonal/Pexels). All fixes ship with unit-test regression nets.
- **Data integrity (P0)**: `WeatherUpdateWorker` was storing latitude/longitude with `putLong(value.toLong())` — silently truncating fractional degrees. A user at 39.7392° was stored as `39`, a user near the equator at 0.5° was stored as `0`. The reader then used the `tintLat != 0.0 && tintLon != 0.0` sentinel to gate adaptive tinting, so anyone within 1° of Null Island had tinting disabled entirely. Switched to `putFloat` (~7 sig figs, sub-meter precision) plus a `location_present` boolean sentinel. Reader falls back to the legacy Long keys for a single update cycle so existing installs don't lose tinting between upgrade and the next 30-min worker tick.
- **Correctness (P1)**: `SolarCalculator.sunTimes` default UTC-offset arg used `TimeZone.getDefault().rawOffset` which ignores DST. Every region observing daylight saving had sunrise/sunset shifted by an hour for ~half the year, which the adaptive-tint phase math depends on. Switched to `getOffset(System.currentTimeMillis())`.
- **Battery + correctness (P1)**: `SystemThemeListener` (Phase 6.2 new code) ran a 500 ms `while (true)` polling loop that (a) never stopped when the user disabled auto-switch, (b) trapped the outer flow-collector so further preference emissions couldn't propagate, and (c) woke the CPU twice a second forever. Replaced with `ComponentCallbacks.onConfigurationChanged` — an actual event, delivered even while the app is fully backgrounded.
- **Reliability (P1)**: `SystemThemeListener.applyStoredWallpaper` called `WallpaperApplier.applyFromUrl`, which only speaks HTTP — `OkHttp.Request.Builder().url(...)` throws `IllegalArgumentException` for `file://` or `content://` schemes. Users whose last applied wallpaper was AI-generated (`file:/data/.../foo.png`) silently lost auto-switch. New `WallpaperApplier.applyByLocator` dispatches on scheme: http(s) → existing OkHttp path, file/content/absolute-path → bounded two-pass `BitmapFactory` decode with the same 64 MB ceiling and `inSampleSize` sampling.
- **Storage leak (P1)**: `AiWallpaperRepository.pruneOldFiles` was defined but never called — the 50-image cap was a promise, not an enforcement. Now invoked after every successful generation. Also sweeps stale `.tmp` files left by interrupted writes.
- **Thread safety + responsiveness (P1)**: `AiWallpaperViewModel.applyWallpaper` decoded the full-resolution PNG via `BitmapFactory.decodeFile` on the Main coroutine context (a 3–4 MB PNG → ~10 MB bitmap synchronously on the UI thread). Re-routed through `applyByLocator` so the disk read + decode + sampling all happen on `Dispatchers.IO`.
- **Structured concurrency (P2)**: `AiWallpaperRepository.generate` wrapped the body in `runCatching` which captures `CancellationException`. A back-navigation mid-generation was surfaced as a generic error message instead of a clean coroutine teardown. Switched to explicit `try`/`catch` with cancellation rethrow.
- **Performance (P2)**: `WeatherWallpaperService.draw` allocated a new `ColorMatrix` + `Paint` every frame at 30 FPS whenever adaptive tint was enabled (~30 allocations/sec under steady-state). Cached the `Paint` by 5-minute time bucket; only rebuilds when the bucket changes. Also short-circuits to the no-tint draw path during the neutral-midday window.
- **UX (P2)**: Settings dark/light mode wallpaper slot opened an `AlertDialog` only when wallpaper history was non-empty — a fresh install / cleared history made the slot affordance a dead click. Now opens regardless and shows a "No wallpapers applied yet" explanatory empty state with guidance.
- **UX (P3)**: Settings VFX picker confirm button was labeled "Cancel" even though each radio click already committed synchronously. Relabeled to "Close". Mirrored to the dark/light slot picker.
- **Error messages (P2)**: `AiWallpaperRepository` now maps Stability AI HTTP codes (401/402/403/422/429/5xx) to actionable user copy ("API key invalid", "Out of credits", "Content policy", "Rate limited") instead of "Generation failed (HTTP 429): {raw JSON}".
- **Maintenance**: Hoisted the per-call `\\s+` regex in `AiWallpaperRepository` to a file-level constant. Restored DRY between WallpaperApplier's HTTP and local decode paths via shared `computeSampleSize`.
- **Tests**: 30 new unit tests across `SolarCalculatorTest` (10 — DST regression, polar day/night clamps, equinox day length, golden-hour tint band, intensity scaling), `AiWallpaperRepositoryFriendlyErrorTest` (10 — per-status-code copy, body-append rules), `WallpaperLocatorSchemeTest` (10 — http/file/content/path/unknown classification, case-insensitivity, three-part split with URLs containing pipes). Fixed pre-existing `SettingsViewModelTest` fixture gap (missing mocks for `adaptiveTintIntensity`, `darkModeWallpaperId`, `lightModeWallpaperId`, `stabilityAiKey` added in v6.13/6.14). 248/248 unit tests green.

## v6.14.0
- **AI Wallpaper Generation (Phase 3.1)**: New dedicated screen accessible via the "AI" chip in the Wallpapers header row. Enter a text prompt, pick a style (Photographic, Anime, Digital Art, Cinematic, Fantasy, Neon, Pixel Art, or None), and generate a 9:16 PNG via the Stability AI API. The result can be set as Home screen, Lock screen, or Both, and saved to Favorites. API key is entered in-screen (animated field, password-masked) and persisted in DataStore. Generated images are stored in `filesDir/ai_wallpapers/` with automatic pruning to the 50 most recent.
- **ContentSource.AI_GENERATED**: New enum value in `ContentSource`; `sourceDisplayName()` updated to return "AI Generated".
- **Version fix**: `build.gradle.kts` was still at 6.12.0/versionCode 92 despite the 6.13.0 commit. Bumped directly to 6.14.0/versionCode 94 since Phase 3.1 lands here.
- **ROADMAP cleanup**: Marked Phase 2.4 "Change your style" Settings entry and Phase 5.3 VFX Particle Overlays as done — both were already implemented in prior sessions but left unchecked.

## v6.13.0
- **Seasonal content**: `SeasonalContentManager` provides date-driven themes — Holiday (Dec), Halloween (Oct 15–31), New Year (Jan 1–3), Valentine (Feb 10–14), Summer (Jun 21–Sep 1). Returns null off-season; fully injectable singleton.
- **Sounds tab seasonal carousel**: When a seasonal theme is active, a `SoundCollectionSpec` with the seasonal query and amber-gold `SEASONAL` tone is prepended to the sound collection carousel on all three tone tabs (Ringtones, Notifications, Alarms).
- **Wallpapers Discover seasonal banner**: A `SeasonalBannerCard` full-line item appears in the staggered grid between the daily pick hero and the curated collection shortcuts. Tapping it searches for the seasonal wallpaper query.
- **Style-personalized Discover feed**: `WallpaperRepository.getDiscover()` now accepts `userStyles` from the user's onboarding preferences. When styles are non-empty, an additional style-biased Wallhaven search runs alongside the toplist, widening the feed toward the user's aesthetic preferences.
- **ROADMAP reconciliation**: Marked 1.2 (Freesound v2), 1.3 (SoundCloud CC), 1.4 (Drop IA), 2.3 (QuickApplySheet), 2.6 (Sound Detail redesign) as done — all were previously implemented but left unchecked.
- **Tests**: 19 new unit tests in `SeasonalContentManagerTest` covering all season windows, boundary dates, and off-season null returns. Existing ViewModel tests updated for new constructor params.

## v6.12.0
- Round 20 audit — Wallhaven SafeSearch toggles, auto-wallpaper rotation constraints, in-session source diagnostics, NewPipe stream-leak re-verify
- **Privacy / control**: Settings → API Keys now exposes the long-orphaned `showNsfwContent` toggle as a real UI control, plus a new `showSketchyContent` toggle for Wallhaven's intermediate sketchy tier. Without an API key both opt-ins coerce back to SFW-only — Wallhaven would otherwise reject the request and leave the user with an empty grid. `computeWallhavenPurity` extracted as a pure helper with full 8-combo unit coverage
- **Battery / data hygiene**: Auto-wallpaper rotation gains three opt-in execution constraints — Charging only, Wi-Fi only (sets `NetworkType.UNMETERED`), and Device idle only. ViewModel re-schedules the WorkManager job on every toggle change so the running worker picks up new constraints without waiting for the next interval boundary. `buildAutoWallpaperConstraints` extracted as a pure helper for unit testing
- **Observability**: New `SourceMetrics` singleton tracks per-source request count, success ratio, last error, and rolling p50/p95 latency for the current session. Settings → Diagnostics surfaces a snapshot dialog with a Reset button. Initial hooks land in `WallpaperRepository.getWallhaven` and `FreesoundV2Repository.search`; pattern is documented for follow-up coverage of the remaining content sources. CancellationException intentionally excluded from failure stats (it's structured-concurrency teardown, not a source failure)
- **Maintenance**: NewPipe Extractor v0.24.8 stream lifecycle re-verified clean (no `InputStream` / `BufferedReader` without `.use { }`). Version pinned with a documenting comment in `build.gradle.kts` so future bumps trigger a re-audit
- **Tests**: 19 new unit tests (5 `WallhavenPurityTest`, 5 `AutoWallpaperConstraintsTest`, 9 `SourceMetricsTest`); 186/186 total green

## v6.11.0
- Round 19 audit — Freesound rate-limit resilience, smarter Material You accent fallback, cancellation rethrow sweep
- **Reliability**: New `RateLimitInterceptor` wraps the OkHttp client and bounds-retries Freesound v2 API on HTTP 429. Honors `Retry-After` (capped at 30 s ceiling so a pathological response can't stall the app), max 2 retries, 1.5 s default fallback when the header is missing or negative. Scoped to `freesound.org` only — Wallhaven / Reddit / Pexels / Pixabay / SoundCloud pass through unchanged. Previously a routine search past Freesound's 60 req/min limit would silently blank the Sounds tab
- **Theming**: `ColorExtractor` now exposes `bestAccentColor` — a saturation/lightness-gated fallback ladder (dominant → vibrantDark → vibrant → vibrantLight → mutedDark → muted → mutedLight → dominant). Cartoon, monochrome, or near-greyscale wallpapers no longer hand the widget a dim grey "accent" via `Palette.getDominantColor`. The widget reads the new `tint_accent` SP key with a graceful fallback to legacy `tint_vibrant_light` for palettes cached before the upgrade
- **Structured concurrency**: 5 catch sites now rethrow `CancellationException` — `WallpaperHistoryManager.record` (widget palette write + widget refresh), `WallpapersViewModel.loadRandom`, `VideoWallpapersViewModel.applyVideoWallpaper` yt-dlp branch, `AudioTrimmer.applyFadeViaFfmpeg`. Cancellation now tears down cleanly instead of being surfaced as a generic state error or a swallowed log line
- **Tests**: 16 new unit tests (7 for `RateLimitInterceptor`, 9 for `ColorAccentSelector`); 167/167 total green

## v6.10.0
- Round 18 audit — finalized writes, widget intent safety, editor download caps, startup concurrency
- **Reliability**: `SoundEditorViewModel.downloadToCache` now checks the return value of `tmpFile.renameTo(file)`. Previously a rename failure (cross-volume rename on some OEM scoped-cache dirs, stale target file, or SELinux) was silent — the editor then tried to open a file that wasn't there. Falls back to `copyRecursively` + delete before throwing
- **Intent safety**: Three remaining widget callbacks (`OpenFavoritesAction`, `OpenCurrentWallpaperAction`, `OpenAppAction`) now wrap `startActivity` in try/catch. A missing or disabled launch activity no longer crashes the widget host process
- **Structured concurrency**: `FreeVibeApp.evictStaleCaches` now rethrows `CancellationException` instead of swallowing it. This matched the already-corrected `warmCommunityIdentity` pattern; the full app-startup background block now uniformly respects cancellation
- **Bounds**: `WallpaperCropViewModel.load` and `WallpaperEditorViewModel.loadFromUrl` now cap buffered image downloads at 64 MB (Content-Length + streamed), matching `WallpaperApplier` / `DualWallpaperService` / `DownloadManager`. A hostile CDN URL can no longer OOM the crop/edit flow

## v6.9.0
- Round 17 audit — last-mile download caps
- **Bounds**: `ColorExtractor.extractFromUrl` caps buffered response at 32 MB (palette tinting only needs a 200×200 downsample; a hostile redirect to a giant image would otherwise balloon the heap just for widget tint extraction). Also hardened `calculateSampleSize` against `sample` integer overflow on pathological near-Int.MAX dimensions
- **Bounds**: `SoundApplier.saveUrlToMediaStore` caps downloads at 64 MB (matches `DownloadManager`). Previously a misresolved URL returning an endless stream could write to MediaStore until the user's storage filled

## v6.8.0
- Round 16 audit — video cropper hardening, offline-cache bounds, preferences consistency
- **Safety**: `VideoCropScreen` HTTP download for remote crop input now caps at 256 MB (Content-Length + streamed). Local file paths are validated with `File.exists() + canRead()` before handing to FFmpeg (previously surfaced as cryptic "Invalid data found" errors)
- **Resources**: `VideoCropScreen` FFmpeg process now uses a 4 KB bounded drain for its merged stdout/stderr instead of `readText()` — a chatty ffmpeg run could previously allocate MBs of String data just to log the last 500 chars
- **Structured concurrency**: `VideoCropScreen` outer and inner catch blocks now rethrow `CancellationException`
- **Bounds**: `OfflineFavoritesManager.cacheOffline` enforces an 80 MB per-file ceiling (in addition to the existing 512 MB total budget) so one hostile favorite URL can't blow the whole offline cache in a single download. Also added `CancellationException` rethrow
- **Bounds**: `SoundEditorViewModel.downloadToCache` caps audio downloads at 96 MB — the editor is for short clips, and a misresolved YouTube URL previously could fill cacheDir while the user waits
- **Consistency**: `PreferencesManager.setVideoFpsLimit` / `setVideoPlaybackSpeed` now write SharedPreferences FIRST, then DataStore. `VideoWallpaperService` (which can only read SharedPreferences because WallpaperService can't easily subscribe to DataStore) always sees the new value even if the suspending DataStore write is cancelled mid-flight. Previously the opposite order could leave the runtime service stale for the remainder of its lifetime

## v6.7.0
- Round 15 audit — deeper sweep across bitmap download paths, locale correctness, intent safety, and startup hardening
- **Safety**: `WallpaperApplier.downloadBitmap` and `DualWallpaperService.downloadBitmap` now enforce a 64 MB ceiling on the buffered byte array (Content-Length + actual size) so a hostile CDN can't OOM us during decode
- **Safety**: `DailyWallpaperWorker` notification-thumbnail download now caps at 4 MB + propagates `CancellationException` (previously swallowed, which let a cancelled worker continue allocating)
- **Reliability**: `WeatherWallpaperService.scaleBitmap` no longer leaks the intermediate `scaled` bitmap when `Bitmap.createBitmap(scaled, x, y, …)` throws, and now uses the real `scaled.width/height` consistently (previous code computed crop coordinates from a theoretical value that could diverge from the actual bitmap size, causing slightly off-center crops)
- **Startup**: `FreeVibeApp.warmCommunityIdentity` is now try/caught so a Firebase-auth failure at boot can't reach the uncaught-exception handler and crash the app (CancellationException still propagates)
- **Locale.ROOT sweep**: `AutoWallpaperWorker.normalizeWallpaperRotationSource` (Turkish locale broke source comparison), `SoundQuality` source-name titlecase, `WallpaperDetailScreen` file-type uppercase + `formatCompactCount` + `formatFileSizeLabel`, `SettingsViewModel.formatBytes`, `SharedComponents.formatBytes`, `SoundEditorScreen.formatMs` timestamp, `WallpaperCropScreen`/`VideoCropScreen` zoom-percent, `WallpaperEditorScreen` slider value. All machine-use numeric formatting now uses `Locale.ROOT` so non-English locales don't substitute commas or non-Latin digits
- **Intent safety**: `SettingsScreen.openNotificationSettings` falls back to app-details when an OEM Android build doesn't expose `ACTION_APP_NOTIFICATION_SETTINGS` (previously crashed with ANFE on some MIUI/EMUI devices). `SoundDetailScreen` + `WallpaperDetailScreen` share buttons now skip empty share URLs (was opening a blank share sheet) and wrap `startActivity` in try/catch. `ContactPickerScreen` "Open Settings" wrapped in try/catch
- **Schema resilience**: `WallhavenWallpaper.id` and `url` fields now have `""` defaults, so a malformed Wallhaven response (null id/url) yields a filterable Wallpaper with blank fields instead of a JsonDataException that kills the whole page

## v6.6.0
- Round 14 audit — reliability, safety, resource bounds, and unit-test recovery
- **Security/safety**: `DownloadManager` now enforces a 64 MB ceiling per file for both images and audio (rejects both Content-Length-advertised and streamed overruns) to prevent a malicious/broken server from filling storage
- **Reliability**: `ParallaxWallpaperService` no longer double-closes the ML Kit segmenter when a new image arrives before the previous segmentation callback fires (tracked per-segmenter with explicit nulling + synchronized guard in success/failure listeners)
- **Reliability**: `ParallaxWallpaperService.scaleBitmapCenterCrop` no longer leaks the intermediate `scaled` bitmap when `Bitmap.createBitmap(scaled, x, y, …)` throws OOM/IllegalArgument
- **Reliability**: `VideoWallpaperService` now tracks the last-played path in addition to `lastModified`, so picking a different video file that happens to share the previous file's timestamp triggers re-init instead of silently keeping the old stream
- **Resources**: `AudioTrimmer` replaced four unbounded `readText()` calls on FFmpeg's merged stdout/stderr with a bounded drain (8 KB chunks, unlimited reads but no retention) — previously a chatty FFmpeg run could allocate MBs of throwaway String data
- **Structured concurrency**: Added missing `CancellationException` rethrow across 8 more catch sites — `FreeVibeWidget` (OpenCurrentWallpaper, applyFromSource, applyRandom), `WallpapersViewModel` (loadWallpapers, findSimilar), `ContactPickerViewModel` (search), `VoteRepository` (moderateHide, getTopVotedIds), `FavoritesExporter.parseJson`
- **Cleanup**: Removed unused `Canvas`/`Matrix`/`Paint`/`SurfaceTexture` imports from `VideoWallpaperService`
- **Testability**: `MainActivity.isAllowedLaunchUrl` now uses pure-JVM scheme extraction instead of `android.net.Uri.parse`, so launch-URL validation is directly unit-testable (was previously broken in local unit tests with a "Method parse in android.net.Uri not mocked" runtime failure)
- **Tests**: Fixed pre-existing `MainActivityLaunchNavigationTest.buildLaunchWallpaper preserves wallpaper metadata` failure; updated `FavoritesExporterValidationTest` to match v6.5.0's HTTPS-only policy; added a new test covering unsafe launch-URL rejection (http/file/content/javascript schemes); 151 total unit tests pass.

## v6.5.3
- Fix adaptive icon support: generate proper 108dp foreground PNGs, circular round icons, restore mipmap-anydpi-v26 XML wrappers
- Remove orphaned vector icon drawables that didn't match brand

## v6.5.2
- Restore original glowing-A beam icon from v6.1.0 across all mipmap densities

## v6.5.1
- Restore original adaptive vector app icon (reverts PNG logo changes from v6.2.0)

## v6.5.0
- Security: OOM-safe bitmap decode, HTTPS-only URL validation, SoundUrlResolver HTTP fix
- Correctness: CancellationException rethrow in 8 more catch sites
- Accessibility: IconButton touch targets to 36dp minimum (8 targets)
- Performance: remember() wrapping, regex hoisting, LaunchedEffect key fixes

## v6.4.0
- Structured concurrency audit: CancellationException sweep across 16 catch sites, 4 ViewModels

## v6.3.0
- Upload/download security hardening, UI polish pass

## v6.2.0
- Undo correctness, preview-apply stability

## v6.1.0
- Video preview, adaptive widget tint, parallax from gallery, collection sharing

## v6.0.0
- Undo, Widget preview, Bulk favorites, Preview mode, Collection rotation

## v5.26.0
- ModifierParameter lint cleanup

## v5.25.0
- UI state @Immutable, DailyWallpaperWorker backoff

## v5.24.0
- Compose stability, HTTPS enforcement, API key input sanitization

## v5.23.0
- Coil disk cache, shared OkHttp, crossfade

## v5.22.0
- Final locale sweep, remaining Regex hoisting, ProGuard verified

## v5.21.0
- Regex hoisting, dead code removal, perf

## v5.20.0
- Parallax atomicity, bitmap decode safety, widget feedback, locale
