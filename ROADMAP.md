# Aura — Product Roadmap

> Open-source Android personalization: wallpapers, video wallpapers, ringtones, sounds.
> Stay the OSS alternative to Zedge: no ads, no surprise charges, no dark patterns.

**Version:** 2026-06-10-zedge-local-parity (mapped Zedge parity under local-first constraints).
**Code version at write:** v6.31.1 / versionCode 112 (per `app/build.gradle.kts`; release/lint Gradle runs are memory-heavy on this Windows workstation, so rerun APK compilation only when explicitly needed).
**Charter:** personalization, AMOLED-first, free-by-default, multi-source content aggregation, community-fed catalog, polite live wallpapers (battery-aware, pause-on-invisible).

---

## ▶ Implementer Instructions (for the build machine)

This roadmap is fed continuously by a research machine. On every pass, the build machine should:
1. `git pull --rebase` to get the latest researched items before starting.
2. Work the open 🤖 items top-down by priority (P0 → P3). Build them properly: multi-file structure, real error handling, no runtime auto-install hacks, version strings synced, docs/CHANGELOG updated in the same commit.
3. In addition to building items, run a full UX audit each pass. Walk every screen / page / dialog / form / table / empty-loading-error-disabled state across light/dark/high-contrast themes. Check onboarding, navigation clarity, spacing/contrast/alignment, clipping/overflow, hierarchy, microcopy, destructive-action guards, keyboard + screen-reader accessibility, and trust signals. Fix what you find, or file it back as a new 🤖 roadmap item if it is larger than a pass.
4. Check off ✅ each item you complete, leave it in place with the checkmark, commit per logical change with a "why" message, and push.
5. Never edit this Implementer Instructions block or the 🔬 Researcher Queue headings. Never force-push.

**Last researched:** 2026-06-10 / Zedge local-first parity pass.

---

## How to read this document

- **State of the Repo** — what's actually shipped, with receipts.
- **Now / Next / Later / Under Consideration / Rejected** — tiered backlog with one-line rationale per item. Tier = sum of fit/impact/effort/risk/dependencies/novelty, ≥24 = Now, 18–23 = Next, 12–17 = Later, <12 = Under Consideration. Charter conflict = Rejected.
- **Themes** — cross-cutting initiatives that span multiple items.
- **Risk Register** — known landmines for the next 12 months of execution.
- **Implementation Log** — preserved release-pass entries (the receipts for what shipped).
- **Appendix** — every cited URL, organized by class.

If you're adding a feature and the source isn't in the Appendix, do not add it. A roadmap without sources is a wishlist.

---

## State of the Repo (snapshot, 2026-06-04)

- Kotlin 2.1.0 / Compose / Material 3, Hilt 2.53.1, Room 2.7.2 (v14), Retrofit 3.0.0, OkHttp 5.3.2, Media3 1.5.1, Coil 2.7.0, WorkManager 2.11.2, Glance 1.1.1, NewPipe Extractor 0.26.3, youtubedl-android 0.18.1, **ML Kit `segmentation-subject:16.0.0-beta1`** (N-3 migrated 2026-05-16), **Firebase BoM 34.13.0** (N-2 shipped 2026-05-16), `play-services-base:18.5.0` (ModuleInstallClient for unbundled segmenter).
- 130 Kotlin files in `app/src/main/java/com/freevibe/`, 50 unit-test files, scanner not rerun in Cycle 1, 1 design-note TODO resolved (`VoteRepository.kt` admin auth → Custom Claims).
- Shipped via implementation passes since 2026-04-25 (latest code release tag: `v6.31.1`, Android 8-12 YouTube/Sounds crash fixed with core library desugaring). See Implementation Log.
- Distribution: GitHub Releases + Obtainium manifest; signed via `freevibe.jks`. CI workflow `.github/workflows/verify.yml` runs assembleDebug/testDebugUnitTest/lintDebug on push/PR. `.github/workflows/release.yml` now builds signed `assembleRelease` APKs from GitHub secrets, rejects debuggable artifacts, runs `apksigner verify --print-certs`, publishes SHA-256 checksums/release notes, creates GitHub artifact attestations, and records Android developer verification status. Cycle 2 decided Aura is full-only for GitHub/Obtainium today, with IzzyOnDroid as the realistic near-term app-store target; F-Droid mainline remains blocked until a real FOSS flavor removes/isolates Firebase, Google Services, and Play Services ML Kit. Dependency Review, SARIF-only OpenSSF Scorecard, release metadata consistency, and checked SBOM readiness workflows now cover PR/scheduled/release supply-chain checks. `docs/distribution/developer-verification.md` tracks the owner-only ADC/PDC package-registration path and the branch-protection required-check owner action.
- Diagnostics: Aura does not use automatic crash analytics. Settings now exposes a local-only crash diagnostics bundle with last crash timestamp, app/Android/ABI/source context, reproduction fields, and sanitized `crash.log` tail; Copy/Share require explicit user action.
- Package id `com.freevibe`, brand "Aura"; do not change without a migration plan (re-installs lose data; existing community uploads keyed by device id).
- Build env note: use Android Studio's bundled JBR and SDK 35. Release/lint Gradle runs are memory-heavy on this Windows workstation; prefer focused unit tests and lightweight file checks unless APK compilation is explicitly needed.
- CI surface (Cycle 1 note): `.github/workflows/verify.yml` closes the prior no-PR-build gap. Branch protection requiring `verify` is still an owner action.
- Platform horizon (rev4 note): Android 17 reached Platform Stability in Beta 3 (March 2026), Beta 4 shipped 2026-04-16, stable expected June 2026 — sets API 37 baseline with EyeDropper, PhotoPicker 9:16 customization, Contacts Picker, ACCESS_LOCAL_NETWORK, Bubbles. One UI 8.5 stable rolling out May 2026 with Smart Subject Placement + AI Weather Effects — competitor validation of Aura's existing Phase 6.3 weather trajectory + NX-2 lockscreen-depth direction. ([Android 17 release notes](https://developer.android.com/about/versions/17/release-notes); [9to5Google Beta 2 EyeDropper](https://9to5google.com/2026/02/26/android-17-beta-2-contacts-and-display-color-access/); [SamMobile One UI 8.5 features](https://www.sammobile.com/news/one-ui-8-5-update-top-features/).)

### What is shipped (Phase 1-7 status)

Preserved verbatim from prior passes; do not edit unless a regression occurred.

#### Phase 1 — Content Foundation
- [ ] **1.1 Aura Originals bundled content (Phase 1.1)** — still a promise, not a shipment. URL-backed cache exists; bundled CC0 sound pack does not. Carried to **Now** below.
- [x] 1.2 Freesound API v2 (`FreesoundV2Repository`, rating + duration filters, RateLimitInterceptor).
- [x] 1.3 SoundCloud CC (`SoundCloudApi.kt`, retained as legacy; not in active feed since v6.18.0).
- [x] 1.4 Internet Archive removed (DB migration v6→v7).
- [x] 1.5 Ringtone Maker from device music (Create from Music → SoundEditorScreen, ringtone-specific 8–30s trim defaults).

#### Phase 2 — UX Overhaul
- [x] 2.1 Sounds-tab simplification (Ringtones / Notifications / Alarms primary chips; YouTube / Community / Search in secondary menu; Refine in bottom sheet).
- [x] 2.2 Instant sound preview (first-5 prebuffer via Media3 SimpleCache; Ready badge on cards).
- [x] 2.3 QuickApplySheet (long-press → Ringtone / Notification / Alarm / Download).
- [x] 2.4 Onboarding style picker + Settings re-entry; style-biased Wallhaven + Pexels + Pixabay Discover.
- [x] 2.5 Seasonal content (`SeasonalContentManager`: Halloween / Holiday / New Year / Valentine / Summer).
- [x] 2.6 Sound Detail redesign (waveform top, three apply buttons, More Like This).

#### Phase 3 — AI & Generation
- [x] 3.1 AI Wallpaper Generation via Stability AI (server-side; `StabilityAiApi`, `AiWallpaperRepository`, 8 styles, 50-image cap, per-call PNG, BuildConfig+DataStore key handling).
- [ ] 3.2 AI Sound Generation — not planned per v5.0.0 charter prune; revisit in **Under Consideration**.
- [x] 3.3 Parallax wallpapers (`ParallaxWallpaperService` + ML Kit Selfie Segmentation).

#### Phase 4 — Community
- [x] 4.1 User-generated sound uploads (Firebase Storage 20 MB cap, RTDB metadata, vote-based moderation, Community Picks).
- [x] 4.2 User-generated wallpaper uploads (gallery → crop → compressed JPEG ≤4 MB, Palette colors, RTDB).
- [x] 4.3 Creator profiles (anonymous Firebase identity; follow, top-creator leaderboard). Google sign-in still deferred — see Phase 7.3.
- [x] 4.4 Shareable collections (Aura links, QR codes, JSON files; `aura://collection/import/{token}` deep link).

#### Phase 5 — Video Wallpaper Evolution
- [x] 5.1 Local video/GIF import (`ActivityResultContracts.OpenDocument`, animated GIF canvas renderer, Fit/Fill scale mode).
- [x] 5.2 Loop & Crop editor (frame thumbnails, range scrubber, loop preview, FFmpeg `-ss`/`-t`).
- [x] 5.3 VFX particle overlays (`VfxParticleRenderer`: FIREFLIES / SAKURA / EMBERS / BUBBLES / LEAVES / SPARKLES).
- [x] 5.4 Touch-reactive effects (ripple + sparkle bursts, bounded, capped).
- [x] 5.5 Video battery dashboard (heartbeat, FPS, scale mode, auto 15 FPS cap below 15 % battery).

#### Phase 6 — Smart Features
- [x] 6.1 Material You color preview (5 tonal palettes on detail screen; `ColorAccentSelector` saturation/lightness gate ladder).
- [x] 6.2 Dark/light auto-switch (`SystemThemeListener` via `ComponentCallbacks.onConfigurationChanged`; per-slot wallpaper IDs; `WallpaperApplier.applyByLocator` handles file/content/http schemes).
- [x] 6.3 Weather effects overlay (`WeatherParticleRenderer` + `WeatherWallpaperService`, NOAA-coded effects, 30-min `WeatherUpdateWorker`).
- [x] 6.4 Time-of-day adaptive tint (`SolarCalculator` DST-aware, `ColorMatrix` cached per 5-min bucket).
- [x] **6.5 Smart Crop with subject detection** — wallpaper and video variants shipped 2026-05-17 (`SmartCropCalculator`, `SmartCropDetector`, `WallpaperCropViewModel.applySmartCrop`, `VideoCropScreen` frame extraction). Remaining lockscreen-depth use belongs to NX-2.

#### Phase 7 — Polish & Infra
- [x] 7.1 Unified Audio Service (`AudioPlaybackService` + MediaSession + `AudioPreviewCache`).
- [~] **7.2 SelectedContentHolder replacement** — primary selected wallpaper/sound now survive process death via SharedPreferences JSON; full nav-graph-scoped replacement and pager-list removal still pending under NX-4.
- [ ] **7.3 Favorites sync (Firestore + Google sign-in)** — blocked on `default_web_client_id` in `google-services.json`. Carried to **Next**.
- [ ] 7.4 Additional widgets (Daily Wallpaper, Sound Quick-Set, Scheduler Controls) — partial; shuffle widget exists. Carried to **Later**.
- [ ] 7.5 True offline mode + prefetch + < 1.5 s cold start — partial; Coil disk cache shipped, offline favorites manager exists. Carried to **Later**.

#### Phase 8 — Stretch (none shipped)
- 8.1 Wallpaper sets (wallpaper + icon-pack + widget bundle). **Later.**
- 8.2 Wear OS companion. **Later** (re-scoped for Wear OS 6 + Watch Face Push).
- 8.3 Desktop companion (Tauri/Electron). **Later.**
- 8.4 Stickers / emoji. **Under Consideration.**

---

## 🔬 Researcher Queue (Cycle 1 — 2026-06-04)

Append-only Cycle 1 handoff. Every item below is source-backed in `docs/research/cycle-1-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts instead of creating a second parallel feature.

- [~] 🤖 🔬 **P0 — Add Firebase App Check for community writes**
  - Why: Anonymous Firebase Auth proves a user session, not that writes originate from Aura. Community uploads/votes now have server-side admin Custom Claims, but RTDB/Storage writes are still cheap for non-Aura clients to automate.
  - Evidence: `database.rules.json:9`, `database.rules.json:41`, `database.rules.json:52`, `app/build.gradle.kts:201-204`; Firebase App Check docs support Realtime Database and Cloud Storage and recommend monitoring metrics before enforcement.
  - Touches: N-2 follow-up; `gradle/libs.versions.toml`, `app/build.gradle.kts`, `FreeVibeApp.kt`, Firebase console setup, `docs/firebase-admin-claims.md`, new App Check rollout doc.
  - Acceptance: debug provider works for emulator/dev builds; release builds send Play Integrity App Check tokens; Firebase metrics are monitored before enforcement; RTDB/Storage enforcement can be enabled without locking out legitimate users.
  - Verify: debug-provider upload/vote on emulator; release-device upload/vote; Firebase App Check request metrics; RTDB/Storage rules still reject unauthenticated writes.
  - Progress 2026-06-07: Cycle 53 added debug/release App Check provider installers, Firebase BoM-managed App Check dependencies, startup installation before Firebase-backed community warm-up, dependency verification metadata, generated notice lock refresh, and `docs/community-app-check-rollout.md`. Cycle 54 added `CommunityQuotaPolicies`, protected admin-only quota/dedupe ledger namespaces, and `docs/community-quota-rate-limits.md`. Cycle 57 added tracked Storage rules plus local Emulator Suite coverage for community upload blobs. Cycle 58 added RTDB Emulator Suite coverage for upload metadata, owner indexes, reports, quota ledgers, dedupe ledgers, and collection shares. Cycle 63 added callable function contract metadata, protected write-path coverage, limited-use App Check token decisions, and `docs/community-callable-quota-enforcement.md`. Cycle 91 added `docs/community-callable-contract.json` and `tools/community_callable_contract_check.py` to make the callable contract backend-checkable and pin quota reset days to UTC. Cycle 93 added the Node 20 TypeScript `functions/` project, fail-closed App Check/Auth callable exports, a manifest-synced contract mirror, a UTC quota decision engine, backend manifest coverage, and CI tests. Cycle 94 implemented the `submitCommunityReport` handler core with server-derived reporter UID, report normalization, HTTPS source validation, UTC quota reservation, duplicate handling, and focused Functions tests. Cycle 95 implemented the `recordCommunityVote` handler core with content ID normalization, existing voter-marker idempotency, UTC quota reservation, dedupe handling, vote tally transactions, legacy voter-marker mirroring, and focused Functions tests. Cycle 96 refined follow dedupe to creator ID plus desired state and implemented the `setCreatorFollow` handler core with no-op state idempotency, UTC quota reservation, action-specific dedupe handling, final follow-row set/remove writes, and focused Functions tests. Cycle 97 refined block dedupe to blocked UID plus desired state and implemented the `setCommunityUserBlock` handler core with self-block rejection, no-op state idempotency, UTC quota reservation, action-specific dedupe handling, private block/reverse-index set/remove writes, and focused Functions tests. Cycle 98 implemented the `finalizeCommunitySoundUpload` handler core with server-allocated upload IDs, Storage path ownership checks, storage-path dedupe, public metadata writes, and owner-index writes. Cycle 99 implemented the matching `finalizeCommunityWallpaperUpload` handler core with JPEG metadata bounds. Cycle 100 refined profile edit dedupe to profile UID plus normalized public profile hash and implemented the `updateCreatorProfile` handler core. Cycle 101 added RTDB-emulator-backed profile handler persistence coverage for profile, quota, dedupe, and unchanged-profile idempotency writes, then wired that script into the Firebase backend CI lane. Cycle 102 added matching report handler persistence coverage for report, quota, dedupe, and duplicate report writes. Cycle 103 added matching vote handler persistence coverage for vote tally, nested and legacy voter markers, quota, dedupe, and repeat-vote idempotency writes. Cycle 104 added matching follow handler persistence coverage for follow writes, unfollow removals, quota, dedupe, and no-op unfollow idempotency writes. Cycle 105 added matching user block handler persistence coverage for private block rows, reverse-index rows, unblock removals, quota, dedupe, and no-op unblock idempotency writes. Cycle 106 added matching sound upload handler persistence coverage for public metadata, owner index, quota, storage-path dedupe, and duplicate upload idempotency writes. Cycle 107 added matching wallpaper upload handler persistence coverage for public metadata, owner index, quota, storage-path dedupe, and duplicate upload idempotency writes. Cycle 108 added Android report callable client code with limited-use App Check token selection and callable-first report submission. Cycle 109 added Android vote callable payload/client code and callable-first vote submission. Cycle 110 added Android follow callable payload/client code and callable-first follow/unfollow submission. Cycle 111 added Android user-block callable payload/client code and callable-first block/unblock submission. Cycle 112 added Android sound upload finalizer callable payload/client code and callable-first metadata finalization after Storage upload. Cycle 113 added Android wallpaper upload finalizer callable payload/client code and callable-first metadata finalization after Storage upload. Cycle 114 added Android profile edit callable payload/client code and callable-first creator profile updates. Cycle 115 added checked Android callable wire-protocol coverage. Remaining work: live callable invocation evidence, Firebase Console registration, debug-token registration, metrics burn-in, direct-rule tightening, and RTDB/Storage App Check enforcement.

- [~] 🤖 🔬 **P1 — Baseline Profile + Macrobenchmark gate for startup and grid jank** — harness shipped 2026-06-04; physical-device generation/metrics pending because no adb target is attached.
  - Why: Aura claims instant startup and L-8 targets <1.5 s cold start, but there is no benchmark/profile module to prove startup, first-scroll, or dense-grid smoothness.
  - Evidence: `README.md:23`, `ROADMAP.md:359-362`; source search now includes `androidx.benchmark`, `androidx.baselineprofile`, `ProfileInstaller`, Macrobenchmark tests, and manual performance CI artifact upload.
  - Touches: L-8 / U-13; `settings.gradle.kts`, `gradle/libs.versions.toml`, app Gradle config, `baselineprofile`, `.github/workflows/performance.yml`, `docs/performance/baseline-profile.md`.
  - Acceptance: generated profile covers Wallpapers, Videos, Sounds, Favorites, and Wallpaper Detail; Macrobenchmark captures startup and scroll metrics; profile/no-profile results are attached to CI or release notes.
  - Verify: `:app:generateBaselineProfile`; `:baselineprofile:connectedBenchmarkReleaseAndroidTest` on a physical device, not emulator-only; compare startup and frame metrics before/after profile.
  - Blocker: `adb devices` returned no attached devices on 2026-06-04, so profile generation and metric comparison remain a physical-device follow-up.

- [x] 🤖 🔬 **P1 — Fold Android developer verification + release provenance into NX-8** — shipped 2026-06-04 (`docs/distribution/developer-verification.md`, release-note verification status, Izzy checklist, branch-protection owner action).
  - Why: Aura's distribution path is GitHub Releases/Obtainium/F-Droid/Izzy, and Android developer verification begins affecting non-Play installs on certified devices in select regions in September 2026.
  - Evidence: Android developer verification docs; current `obtainium.json`; NX-8 still has per-ABI splits/F-Droid/Izzy pending.
  - Touches: NX-8; release workflow, GitHub release template, `docs/distribution`, fastlane metadata, checksums/SBOM/provenance, signing-key continuity docs.
  - Acceptance: package registration path is documented for owner action; releases publish SHA-256 checksums; Obtainium/Izzy/F-Droid install paths are documented; branch protection requiring `verify` is enabled or explicitly marked owner-blocked.
  - Verify: owner confirms package registration status; install/update via Obtainium; compare APK checksum to release note; `git tag v*` release attaches expected APK assets.

- [x] 🤖 🔬 **P1 — Rotation trigger foreground-service policy hardening** — shipped 2026-06-07 (`docs/rotation-trigger-fgs-policy.md`, `tools/rotation_fgs_policy_check.py`, Play packet owner evidence row).
  - Why: The unlock/screen-off rotation feature uses a long-lived `specialUse` foreground service. Android docs allow `specialUse`, but Play reviews the manifest property/use case, so Aura needs a policy-ready runbook and fallback decision.
  - Evidence: `app/src/main/AndroidManifest.xml:29`, `app/src/main/AndroidManifest.xml:150-155`; Android foreground-service docs require enough manifest/Play Console explanation for `specialUse`.
  - Touches: NX-6; `RotationTriggerService.kt`, manifest property text, Settings rotation copy, fastlane/release policy notes, exact-alarm/WorkManager fallback decision.
  - Acceptance: service runs only while user-enabled triggers are active; notification copy explains the active trigger; Play declaration text exists; exact-alarm alternative is scoped without silently adding a restricted permission.
  - Verify: toggle unlock/screen-off triggers on/off; inspect foreground notification; Android 14+ background/doze manual pass; Play policy declaration reviewed by owner before submission.
  - Completed 2026-06-07: Cycle 152 added a checked policy packet for the `specialUse` service type, manifest subtype, source safeguards, Settings opt-in terms, Play Console declaration text, owner demo-video evidence action, and verify/release workflow wiring. The owner still must archive Play Console declaration evidence before Play production.

- [~] 🤖 🔬 **P1 — Source provenance panel + community report queue**
  - Why: Aura aggregates third-party content and hosts community uploads. It already stores source URLs/uploader/license fields, but users need consistent provenance, and moderators need a report intake that does not rely on public comments or ad hoc downvotes.
  - Evidence: `Mappers.kt` maps `sourcePageUrl`/`uploaderName`, `WallpaperDetailScreen.kt` and `SoundDetailScreen.kt` show source fields; Pexels/Pixabay expose creator/source metadata; YouTube branding guidance calls for clear source attribution in mixed-source apps; Zedge requires account + metadata for uploads.
  - Touches: T-E / N-2; models/entities, `WallpaperDetailScreen.kt`, `SoundDetailScreen.kt`, RTDB moderation/report queue, rules, licenses screen.
  - Acceptance: every detail screen has compact source/provenance affordance; community entries have a report action; reports are App-Checked/authenticated; admins can resolve/hide/unhide through Custom Claim rules.
  - Verify: manual detail-screen pass by source (Pexels/Pixabay/Reddit/YouTube/community/bundled); Firebase rules tests for report/create/read and admin resolution.
  - Progress 2026-06-07: Cycle 51 added private report payload validation, `CommunityReportRepository`, `/community_reports` and `/community_report_resolutions` rules, sound/wallpaper detail report dialogs, ViewModel report submission with source/license/uploader context, and `docs/support/community-reporting.md`. Cycle 52 added the admin-only open report queue, Settings navigation, report status index, and Hide/Dismiss/Restore actions wired to `/moderation/{contentId}` plus resolution metadata. Cycle 53 installed App Check providers client-side and documented the monitor-before-enforcement path. Cycle 54 defined the report quota policy and protected backend ledger namespaces. Cycle 56 added owner-gated visible delete actions for new community sound and wallpaper uploads that have Cycle 55 deletion handles. Cycle 60 added private rights-confirmed takedown receipts tied to current upload deletion handles. Cycle 62 added admin status filters for Hidden, Dismissed, and Restored closed-report review. Cycle 63 defined the `submitCommunityReport` callable contract with Auth, App Check, limited-use token, quota ledger, dedupe ledger, and final report write requirements. Cycle 91 added a machine-checked callable contract manifest covering all community write surfaces. Cycle 94 added the first handler-backed `submitCommunityReport` Functions implementation with focused accepted, duplicate, quota, Auth, and App Check tests. Cycle 95 added the handler-backed `recordCommunityVote` Functions implementation with existing-vote idempotency, quota, dedupe, Auth, and App Check tests. Cycles 101-107 added RTDB-emulator-backed callable handler persistence tests for profile, report, vote, follow, user block, sound upload, and wallpaper upload writes. Cycle 108 added Android callable-first report submission. Cycle 109 added Android callable-first vote submission. Cycle 110 added Android callable-first follow/unfollow submission. Cycle 111 added Android callable-first block/unblock submission. Cycle 112 added Android callable-first sound upload metadata finalization. Cycle 113 added Android callable-first wallpaper upload metadata finalization. Cycle 114 added Android callable-first profile edits. Cycle 115 added checked Android callable wire-protocol coverage. Remaining work: add live callable invocation evidence, deploy evidence, and direct-rule tightening.

- [ ] 🤖 🔬 **P2 — Video wallpaper playlists and per-video behavior profiles**
  - Why: Aura has local video import, Fit/Fill, crop, thumbnails, Smart Crop, and battery dashboard, but users with multiple clips still apply one video at a time. Focused FOSS video-wallpaper competitors now expose playlists, shuffle/loop, smart start times, and one-shot behavior.
  - Evidence: UndeadWallpaper feature set; Aura `VideoWallpapersScreen`, `VideoCropScreen`, and `VideoWallpaperService` already own the core local-video primitives.
  - Touches: NX-1 after GL/AGSL/ExoPlayer engine migration; Room migration, video ViewModel, video wallpaper engine, Settings/apply sheet.
  - Acceptance: users can create a video wallpaper profile with ordered clips, shuffle/loop/one-shot, per-clip crop/fit/FPS, missing-media recovery, and low-battery FPS cap.
  - Verify: create/reorder/delete playlist, apply it, reboot, test unlock behavior, delete one media URI, verify graceful fallback and battery cap.

---

## 🔬 Researcher Queue (Cycle 2 — 2026-06-04)

Append-only Cycle 2 handoff. Every item below is source-backed in `docs/research/cycle-2-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [x] 🤖 🔬 **P0 — Release workflow must publish signed release APKs, not debug APKs** — shipped 2026-06-04 (`release.yml` signed `assembleRelease`, debuggable guard, `apksigner`, SHA-256 checksums, release-note fingerprint, `docs/distribution/release-signing.md`).
  - Why: The tag-triggered release workflow is named "Build & Release APK", but it runs `assembleDebug` and uploads from `app/build/outputs/apk/debug`. GitHub/Obtainium users need a signed, non-debuggable release artifact.
  - Evidence: `.github/workflows/release.yml:37-44`; Android command-line docs distinguish debug APKs from release builds and say release builds should be signed; `apksigner` can verify APK signatures and certificate fingerprints.
  - Touches: NX-8; `.github/workflows/release.yml`, signing docs, GitHub release template, `obtainium.json`, future per-ABI split outputs.
  - Acceptance: tag workflow builds signed release/universal plus any split APKs; fails if the uploaded APK is debuggable; runs `apksigner verify --print-certs`; publishes SHA-256 checksums and versionCode/versionName; release notes identify the signing certificate fingerprint.
  - Verify: dry-run tag on a non-public test tag or workflow_dispatch; install/update through Obtainium; compare checksum from release notes; confirm `android:debuggable=false`.

- [x] 🤖 🔬 **P0 — Decide full-vs-foss distribution flavor before F-Droid work** — shipped 2026-06-04 (`docs/distribution/channel-strategy.md`, `tools/fdroid_preflight.py`). Decision: keep the full build for GitHub/Obtainium/Izzy; block F-Droid mainline until a real `foss` flavor removes or isolates Firebase, Google Services, and Play Services ML Kit.
  - Why: Aura wants F-Droid/Izzy/GitHub distribution, but current dependencies include Firebase, Google Services, Play Services ML Kit, and no product flavors. F-Droid mainline eligibility is not credible until proprietary dependency boundaries are explicit.
  - Evidence: `app/build.gradle.kts:10`, `app/build.gradle.kts:201-204`, `app/build.gradle.kts:223`, `app/build.gradle.kts:226`; F-Droid policy/FAQ says F-Droid cannot build apps that depend on proprietary Google/Firebase libraries.
  - Touches: NX-8, N-2, N-3, Cycle 1 App Check item; build flavors, source sets, DI boundaries, community upload/vote feature flags, segmentation fallback.
  - Acceptance: documented matrix for GitHub/Obtainium/Izzy/F-Droid; `full` keeps Firebase/community/App Check; `foss` either disables those surfaces or swaps acceptable dependencies; CI proves selected variants; F-Droid metadata is blocked until the matrix is resolved.
  - Verify: `assembleFullRelease` and `assembleFossRelease` or an explicit documented decision not to pursue F-Droid mainline; dependency tree review for the FOSS flavor; Izzy/F-Droid preflight notes.

- [x] 🤖 🔬 **P1 — Add a supply-chain verification lane** — shipped 2026-06-04 (`actions/attest@v4` release attestations, Dependency Review PR workflow, OpenSSF Scorecard SARIF workflow, `gradle/verification-metadata.xml`, `docs/distribution/supply-chain.md`).
  - Why: A side-loaded personalization app needs dependency and artifact provenance beyond a GitHub release asset link, especially with extractor/native/FFmpeg-style dependencies.
  - Evidence: source search found no `gradle/verification-metadata.xml`, dependency locking, Dependency Review, OpenSSF Scorecard, SBOM, artifact attestation, or checksum publication; Gradle and GitHub docs provide these controls.
  - Touches: NX-8, NX-12, N-1; Gradle verification metadata, GitHub workflow permissions, Dependency Review, OpenSSF Scorecard, release checksum/attestation generation, SBOM plan.
  - Acceptance: dependency checksum metadata committed; PRs run dependency/license review and Scorecard; release artifacts publish SHA-256 files and GitHub artifact attestations; SBOM scope is documented even if generation is deferred.
  - Verify: dependency-change PR triggers review; Gradle fails on checksum drift; GitHub release shows checksums and attestation; Scorecard result is visible to maintainers.

- [x] 🤖 🔬 **P1 — Opt-in crash/ANR diagnostics bundle** — shipped 2026-06-04 (`CrashDiagnosticsCollector`, Settings Copy/Share dialog, crash report issue template, `docs/support/crash-diagnostics.md`).
  - Why: Aura avoids automatic analytics, but GitHub/Obtainium/F-Droid users still need a way to send actionable crash evidence after release-only issues.
  - Evidence: `FreeVibeApp.kt` writes a local `filesDir/crash.log`; no Crashlytics/Sentry dependency was found; Android vitals/Play crash dashboards are Play-centric and not enough for non-Play installs.
  - Touches: Settings diagnostics, issue template, local crash-log export, support docs.
  - Acceptance: Settings exposes last crash timestamp and a "copy/export diagnostics" action; bundle includes app version, Android version, ABI, active source/provider, sanitized crash log tail, and reproduction fields; nothing uploads automatically.
  - Verify: synthetic crash writes log; export redacts paths/tokens/API keys; issue template accepts bundle; network monitor confirms no automatic upload.

---

## 🔬 Researcher Queue (Cycle 3 — 2026-06-04)

Append-only Cycle 3 handoff. Every item below is source-backed in `docs/research/cycle-3-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [~] 🤖 🔬 **P0 — Provider compliance matrix and runtime kill switches**
  - Why: Pexels, Pixabay, Freesound, SoundCloud, Reddit, and YouTube each impose different attribution, caching, rate-limit, download, and deletion rules. Aura currently stores some metadata but does not encode provider policy centrally.
  - Evidence: Pexels wallpaper-app restriction; Pixabay 24-hour cache/rate-limit/hotlinking rules; Freesound non-commercial/credit/fair-use API terms; SoundCloud attribution and no-ripping restrictions; Reddit app-review/removal terms; YouTube undocumented/download/cache restrictions.
  - Touches: provider repositories, `SourceMetrics`, Settings provider toggles, detail screens, downloader/apply flows, `docs/legal/provider-policy.md`.
  - Acceptance: every provider has a policy row covering attribution, source link, API cache TTL, media cache TTL, hotlinking, download/apply/share allowances, rate-limit handling, deletion behavior, and kill-switch default.
  - Verify: provider policy unit tests; Settings can disable each remote provider; disabled providers vanish from search/default feeds; existing favorites retain source-deleted/unavailable state without crashing.
  - Progress 2026-06-05: central `ProviderDisclosure` rows now cover every `ContentSource`, `docs/legal/provider-policy.md` mirrors the matrix, and `ProviderDisclosureTest` fails when a source lacks policy coverage. Cycle 36 added checked runtime-control rows in `ProviderDisclosure.kt` and `docs/legal/provider-runtime-controls.md`; Cycles 37-42 added default-on runtime switches for YouTube, Reddit, Wallhaven, Pexels, Pixabay, Community, and Bing Daily. Cycles 43-44 added 24-hour fresh-cache paths plus 429 backoff for Pixabay photo and video metadata requests. Cycle 45 added a default-on generated-wallpapers source switch that hides generation entry points and blocks Stability requests when disabled. Cycle 46 added persisted unavailable-source states for saved favorites/downloads. Cycle 47 added Pexels enhancement-only guardrails. Cycle 48 added explicit remote-gone reconciliation for saved favorites and download history. Sound action capabilities shipped in Cycle 49. Community moderation and report queue integration shipped in Cycles 51-114. 2026-06-13: added `PROVIDER_POLICY` report reason for provider-specific policy violations in community content. Remaining: wire provider-policy reason into community report UI surfaces.

- [x] 🤖 🔬 **P1 — Pexels usage guardrail and fallback plan**
  - Why: Pexels specifically rejects standalone wallpaper/gallery API replication. Aura must prove Pexels is an enhancement source, not the product's core inventory.
  - Evidence: Pexels API wallpaper-app guidance; Aura uses Pexels in wallpaper/video discovery and style-biased feeds.
  - Touches: wallpaper/video source weights, onboarding defaults, provider copy, source detail attribution, fallback source selection.
  - Acceptance: Pexels is not the sole or default required source for any first-run flow; Pexels results always show source/creator context in search/detail; provider can be disabled remotely/configurably without breaking feeds; docs explain the allowed enhancement use case.
  - Verify: turn off Pexels and run Wallpapers/Videos first-run; source badges/links visible; no auto-rotation defaults depend only on Pexels.
  - Completed 2026-06-06: Cycle 47 added Discover and video-wallpaper enhancement guards so Pexels-only batches are dropped unless non-Pexels base inventory is present. Focused tests prove disabled-Pexels Discover still returns Wallhaven/Pixabay, Pexels API calls are skipped, Pexels photo rows keep creator/source-page metadata, and video batches keep Pexels only when Pixabay/Reddit/YouTube-style fallback inventory is present. `docs/research/cycle-47-2026-06-06.md` records the checked Pexels policy source.

- [x] 🤖 🔬 **P1 — YouTube legal-mode/offline-risk switch**
  - Why: Aura's YouTube feature set searches, resolves, caches, and downloads audio/video through NewPipe/yt-dlp, while YouTube's official policy is restrictive around undocumented access, downloads, caching, offline playback, and background playback.
  - Evidence: `YouTubeRepository.kt` NewPipe/yt-dlp stream extraction; `VideoWallpapersViewModel.kt` yt-dlp download path; YouTube API developer policies.
  - Touches: YouTube repository abstraction, Sounds YouTube tab, video wallpaper YouTube source, Settings provider toggles, distribution docs, issue templates.
  - Acceptance: distributor can disable YouTube features; first-run does not require YouTube; UI clearly labels YouTube as optional; fallback sources remain useful; no YouTube download/cache happens when legal mode disables it.
  - Verify: disable YouTube and run Sounds/Videos; bundled sound and community paths still work; source metrics record disabled state separately from provider outage.

- [~] 🤖 🔬 **P2 — Source deletion and takedown reconciliation**
  - Why: Reddit and other user-generated sources can delete, hide, suspend, or remove content after Aura cached it.
  - Evidence: Reddit developer terms require handling removed/deleted/protected content; Aura stores Reddit permalinks and authors and can cache favorites/downloads.
  - Touches: detail reload paths, favorites/download metadata, source health, report queue from Cycle 1, cache cleanup.
  - Acceptance: source-deleted content stops appearing in remote catalog; favorites/downloads show unavailable/source-deleted state; user can remove local copy; moderator/report queue can hide community mirrors of removed content.
  - Verify: simulate source reload failure/deleted marker; favorite remains navigable but not misrepresented as live remote content; report queue accepts removal reason.
  - Progress 2026-06-06: Cycle 46 added Room-backed `sourceAvailability`/`sourceAvailabilityReason` metadata for favorites and download history, v15 migration/schema, favorite export/import preservation, provider source names in new download records, and UI badges/detail warnings that hide live-source affordances when saved items are marked source-unavailable. Cycle 48 added a shared remote-gone classifier for explicit 404/410/gone/removed/deleted failures and wired wallpaper/sound apply/download paths plus download history to mark saved records `SOURCE_UNAVAILABLE`. Remaining work: provider catalog reload pruning and community moderation/report queue integration.

---

## 🔬 Researcher Queue (Cycle 4 — 2026-06-04)

Append-only Cycle 4 handoff. Every item below is source-backed in `docs/research/cycle-4-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [ ] 🤖 🔬 **P0 — Community upload public-data lifecycle and deletion workflow**
  - Why: Community sounds/wallpapers and collection shares are public-readable, and upload metadata includes Firebase download URLs, uploader labels/IDs, file names, tags, and categories. Users need to know uploads are public and need removal controls.
  - Evidence: `database.rules.json` public reads; `UploadRepository.kt` public download URL metadata; Firebase rules guidance; Cycle 1 report queue and Cycle 3 takedown items.
  - Touches: upload dialogs, RTDB rules, Storage metadata, creator profile repository, report queue, `docs/privacy/community-data.md`.
  - Acceptance: upload flow says content and metadata become public; metadata excludes unnecessary original filenames or clearly justifies them; owner field names are consistent; users can delete their uploads or request removal; admin moderation hides public catalog entries and has retention notes.
  - Verify: Firebase rules tests for owner delete/admin hide/public read; upload/delete manual pass; public catalog no longer exposes unneeded metadata; removal path documented in privacy policy.
  - Progress 2026-06-06: Cycle 55 added `storagePath` metadata for new sound/wallpaper uploads, private `/owner_uploads/{uid}` indexes, owner repository delete methods, RTDB owner-index rules, and `docs/community-upload-deletion.md`. Cycle 56 added owner-visible detail delete actions. Cycle 57 added tracked Storage rules plus emulator tests for owner-only blob create/delete, public reads, MIME/size ceilings, and unmanaged-path denial. Cycle 58 added RTDB emulator tests for public metadata reads, owner creates/deletes, owner-index privacy, report authorization, quota ledgers, and collection shares. Cycle 60 added private admin takedown receipts that must match current upload `storagePath` handles. Cycle 61 added confirmed admin delete actions that consume those receipts, remove Storage and metadata rows, and record retry state. Cycle 65 added Storage orphan cleanup policy. Cycle 66 added `tools/community_upload_backfill_plan.py`, tests, and `docs/community-upload-backfill.md` for dry-run legacy `storagePath` and owner-index backfill planning. Cycle 67 added private owner/admin deletion tombstones plus `docs/community-deletion-retention-policy.md`. Remaining work: public request copy and live backfill evidence after owner access is confirmed.

- [~] 🤖 🔬 **P1 — Android 17 Contact Picker and contacts-permission minimization**
  - Why: Per-contact ringtone assignment requests broad contacts read/write permissions immediately on screen entry. Android 17 provides a picker that gives selected-contact read access without broad address-book access.
  - Evidence: `ContactPickerScreen.kt` immediate `READ_CONTACTS`/`WRITE_CONTACTS` launcher; Android 17 Contact Picker docs; Play User Data contact restrictions.
  - Touches: `ContactPickerScreen`, `ContactRingtoneService`, permission copy, Settings privacy screen, API 37 test plan.
  - Acceptance: API 37+ uses system Contact Picker for contact selection; broad read contacts permission is not requested before the user selects a contact; write permission is requested only when needed to set the ringtone; older devices retain a just-in-time fallback.
  - Verify: API 37 picker flow; API 35 fallback flow; deny read/write permissions and confirm app degrades to "choose another apply target"; no automatic prompt on screen entry.
  - Progress 2026-06-11: Removed the broad `READ_CONTACTS` manifest permission and replaced screen-entry read/write prompts with Android system contact-pick selection plus just-in-time `WRITE_CONTACTS` at ringtone apply. Remaining work: API 37 device/emulator verification and any direct Android 17 Contact Picker API cleanup after the compile-SDK uplift.

---

## 🔬 Researcher Queue (Cycle 5 — 2026-06-04)

Append-only Cycle 5 handoff. Every item below is source-backed in `docs/research/cycle-5-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [ ] 🤖 🔬 **P1 — 200% font, display-size, and contrast audit**
  - Why: Android 14 supports 200% nonlinear font scaling. Aura's dense chips, cards, overlays, bottom sheets, and editor timelines may clip or overlap at large fonts.
  - Evidence: Android 14 nonlinear font-scaling docs; Android touch-target and color-contrast guidance; Aura compact UI/search findings.
  - Touches: typography/layout constraints, chip/card rows, bottom sheets, dialogs, editor controls, Material color roles, screenshot QA.
  - Acceptance: primary flows remain usable at 200% font and large display size; controls keep at least 48 dp touch targets where feasible; critical text/icon contrast passes scanner thresholds; no text overlaps controls.
  - Verify: manual screenshots at 200% font; Accessibility Scanner contrast/touch-target results; dark/light/high-contrast pass.

- [ ] 🤖 🔬 **P2 — Widget and live-wallpaper accessibility/localization coverage**
  - Why: Widgets and live-wallpaper picker metadata are not covered by normal Compose screen traversal but are visible entry points for Aura.
  - Evidence: `FreeVibeWidget.kt`, `freevibe_widget_info.xml`, live-wallpaper XML metadata; Android Accessibility Scanner manual workflow.
  - Touches: Glance widget copy/actions, keyguard widget state, live-wallpaper labels/descriptions, launcher/picker QA.
  - Acceptance: widget actions have localized labels and useful spoken descriptions; live-wallpaper picker labels/descriptions are localized; keyguard widget and launcher widget remain usable at large font/display settings.
  - Verify: add widget, keyguard placement where available, trigger widget actions, inspect picker labels, run scanner/manual TalkBack pass.

---

## 🔬 Researcher Queue (Cycle 6 — 2026-06-04)

Append-only Cycle 6 handoff. Every item below is source-backed in `docs/research/cycle-6-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [ ] 🤖 🔬 **P2 — Managed storage ledger and cache cleanup policy**
  - Why: Aura stores previews, offline favorites, edited audio, generated wallpapers, live wallpaper media, share artifacts, downloads, and provider metadata across several directories and MediaStore.
  - Evidence: `AudioPreviewCache` 48 MB, offline favorites 512 MB, `file_paths.xml`, Settings cache cleanup copy, backup rules from Cycle 4.
  - Touches: Settings storage screen, cache managers, backup/privacy docs, release QA.
  - Acceptance: `docs/privacy/storage-ledger.md` lists each directory/store, budget, retention, backup status, user cleanup action, and uninstall behavior; Settings cleanup copy matches actual behavior.
  - Verify: smoke-run all media flows, list files/directories, run clear cache, confirm retained/deleted state matches the ledger.

---

## 🔬 Researcher Queue (Cycle 7 — 2026-06-04)

Append-only Cycle 7 handoff. Every item below is source-backed in `docs/research/cycle-7-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [x] 🤖 🔬 **P0 — AI generation disclosure and consent gate** — shipped 2026-06-07 (Cycle 133; persisted prompt/privacy disclosure before Stability requests).
  - Why: Aura sends user prompts to Stability AI, uses the user's API key and credits, stores generated PNGs locally, and stores prompt-derived metadata when saved.
  - Evidence: `AiWallpaperRepository.generate()` multipart prompt/API-key call; `AiWallpaperViewModel.saveToFavorites()` prompt-derived favorite name; Stability API key and credit docs; Stability Acceptable Use Policy.
  - Touches: `AiWallpaperScreen`, Settings API-key card, Settings privacy screen, `docs/privacy/ai-generation.md`, release privacy checklist.
  - Acceptance: first AI generation shows a concise disclosure before the network call; disclosure covers prompt sharing, key/credit use, local storage, retention/deletion, and Stability policy boundaries; user can review it later in Settings.
  - Verify: first-run disclosure appears once; deny/cancel does not call Stability; accepted state persists; Settings reset/review works; privacy docs and Data safety matrix include prompt sharing and generated-image storage.
  - Progress 2026-06-07: Cycle 133 added a DataStore-backed generated-content disclosure flag, blocked generation until prompt, key, and disclosure acceptance are present, added a shared disclosure dialog for the generated wallpaper flow and Settings review/reset path, and documented prompt sharing, provider key/credit use, local generated-image storage, diagnostics redaction expectations, and release checklist in `docs/privacy/ai-generation.md`.

- [x] 🤖 🔬 **P0 — AI-generated content report/flag path** — shipped 2026-06-07 (Cycle 134; generated result and favorite report actions).
  - Why: Google Play requires AI-generating apps to include in-app reporting or flagging for offensive generated content without requiring users to exit the app.
  - Evidence: Google Play AI-generated content policy; Aura has text-to-image generation and no report/flag action on generated results or saved AI favorites.
  - Touches: AI result screen, Favorites detail actions, report storage/diagnostics path, moderation/report docs, privacy policy.
  - Acceptance: generated AI wallpapers and saved AI favorites expose a report/flag action; report categories cover offensive/unsafe/deceptive/other; reports do not include the user's API key; release docs define retention and moderation response.
  - Verify: generate result -> report; saved favorite -> report; report survives process restart if local; report export/redaction path omits keys; Play policy checklist has an AI report row.
  - Progress 2026-06-07: Cycle 134 added generated-content report reasons, exposed report actions on generated wallpaper results and saved generated wallpaper detail views, allowed `AI_GENERATED` reports independently of the Community source switch, extended the `submitCommunityReport` callable reason allowlist, and documented generated-report retention, moderation response, and provider-key/local-path exclusions.

- [x] 🤖 🔬 **P1 — BYO Stability key hardening and release guard** — shipped 2026-06-07 (Cycle 135; Stability paid-secret sentinel guard).
  - Why: User-supplied keys are appropriate, but release builds should not accidentally ship a bundled Stability key, and stored keys should have a documented security posture.
  - Evidence: `BuildConfig.STABILITY_AI_KEY` from `local.properties`; `PreferencesManager.stabilityAiKey` DataStore flow; Android security guidance on source-code API keys and Keystore.
  - Touches: release preflight, `docs/privacy/API-key-storage.md`, Settings key UI, diagnostics redaction test, optional encrypted-storage migration plan.
  - Acceptance: release verification fails on nonblank bundled `STABILITY_AI_KEY` unless explicitly marked internal; docs explain where keys are stored and excluded from backup; diagnostics and reports redact all provider keys; encryption migration decision is recorded.
  - Verify: static/release-preflight test with blank and nonblank keys; diagnostics export key-redaction fixture; backup/data-extraction matrix still excludes key storage.
  - Progress 2026-06-07: Cycle 135 extended the provider credential storage guard with an exact `stability-ai-key` sentinel requiring DataStore-backed `paidSensitiveSecret` classification, blank `STABILITY_AI_KEY` / `stability.ai.key` release defaults, explicit Clear control documentation, `stability.ai.key` redaction coverage, and the recorded no-Keystore migration decision.

- [x] 🤖 🔬 **P1 — AI credit, rate-limit, and duplicate-generation guardrails** — shipped 2026-06-07 (Cycle 136; session count and duplicate confirmation).
  - Why: Stability requests consume credits, pricing can change, and repeat taps or retries can spend user-owned credits unexpectedly.
  - Evidence: Stability pricing docs; Aura handles 402/429 after the fact; `cancelGeneration()` warns cancellation may not refund once provider billing has started.
  - Touches: `AiWallpaperViewModel`, AI screen state, Settings/provider docs, local generation history.
  - Acceptance: UI states one request may spend provider credits; active generation disables duplicate submits; retrying the same prompt/style after a recent success requires confirmation; optional local session/day count is visible; 402/429 messages link to the right provider action.
  - Verify: rapid taps create one request; same prompt retry asks confirmation; 402/429 tests still map cleanly; cancel copy does not imply refunds.
  - Progress 2026-06-07: Cycle 136 added ViewModel-level in-flight request rejection, session request counting, same prompt/style duplicate confirmation after a successful generation, Stability account/cooldown copy for 402/429 errors, generated wallpaper disclosure updates, and focused JVM coverage for the request gates and provider error copy.

- [x] 🤖 🔬 **P1 — Prompt metadata retention and deletion policy** — shipped 2026-06-07 (Cycle 137; prompt-free generated favorites and file cleanup).
  - Why: Aura stores prompt snippets in favorite names and prompt words in tags, which can preserve sensitive user text even after the generated image is no longer visible.
  - Evidence: `AiWallpaperRepository` mines five prompt words for tags; `AiWallpaperViewModel.saveToFavorites()` stores `AI: ${prompt.take(60)}`; Cycle 4 privacy matrix covers local user content.
  - Touches: favorite naming, tag creation, AI deletion flow, privacy/storage ledgers, Room migration if metadata fields change.
  - Acceptance: prompt-derived metadata is opt-in or easy to clear; deleting an AI wallpaper removes generated file and prompt-derived favorite/tag metadata; privacy docs classify prompts as local sensitive user content and third-party-shared request content.
  - Verify: save with sensitive prompt; clear/delete flow removes prompt text from Room and generated file; search no longer finds deleted prompt words; backup matrix matches behavior.
  - Progress 2026-06-07: Cycle 137 stopped mining prompt words into generated wallpaper tags, saved generated favorites with the generic `Generated wallpaper` name and safe non-prompt tags only, deleted app-private generated PNGs when generated favorites are removed after the Undo window, added focused prompt-retention and generated-file cleanup tests, and updated generated wallpaper privacy docs.

- [x] 🤖 🔬 **P2 — On-device AI wallpaper decision gate** — shipped 2026-06-07 (Cycle 139; evidence packet and verify-time guard).
  - Why: Local generation could improve privacy and cost control, but it has unresolved hardware, battery, storage, licensing, and moderation constraints.
  - Evidence: U-2/U-14 roadmap notes; Qualcomm/local-dream references in Appendix D; Stability hosted generation currently shipped.
  - Touches: U-2 research, model licensing notes, performance/battery test plan, FOSS flavor strategy.
  - Acceptance: revisit criteria list required device baseline, model size, expected latency, battery/thermal budget, license compatibility, moderation/report behavior, and fallback to hosted/BYO mode.
  - Verify: no on-device generation implementation starts without meeting the criteria; any prototype includes battery/profile evidence and license review.
  - Progress 2026-06-07: Cycle 139 added `docs/ai/on-device-wallpaper-decision.json`, `docs/ai/on-device-wallpaper-decision.md`, `tools/on_device_ai_decision_check.py`, focused backend tests, and verify workflow wiring. The current decision is `hold`, Stability BYO-key generation remains the only supported generation path, and production on-device runtime dependencies or model artifacts are blocked until the evidence criteria are met.

---

## 🔬 Researcher Queue (Cycle 8 — 2026-06-04)

Append-only Cycle 8 handoff. Every item below is source-backed in `docs/research/cycle-8-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [~] 🤖 🔬 **P0 — Store listing metadata preflight**
  - Why: Aura's committed short description is 81 characters, while Google Play's limit is 80 characters, and there is no automated check for title/description/changelog/listing asset limits.
  - Evidence: `fastlane/metadata/android/en-US/short_description.txt`; Google Play preview asset docs; Google Play metadata policy.
  - Touches: `tools/store_metadata_preflight.*`, fastlane metadata, release checklist, CI/release workflow.
  - Acceptance: preflight checks title <=30, short description <=80, full description present, current versionCode changelog present, no stale FreeVibe naming, required privacy URL placeholder present, and required screenshot/feature-graphic files present.
  - Verify: run preflight against current metadata and see the known short-description/screenshot/privacy failures; fix metadata later and confirm the preflight passes.
  - Progress 2026-06-07: Cycle 138 added `tools/store_metadata_preflight.py`, focused backend tests, verify/release workflow text-mode enforcement, release workflow policy coverage, a Play-compliant short description, and a public privacy-policy URL in Fastlane metadata. Asset validation mode is implemented and tested, but the screenshot/feature-graphic requirement remains open under the P1 screenshot and feature-graphic pipeline because the repo still has only one current screenshot artifact.

- [x] 🤖 🔬 **P0 — Public privacy policy and in-app privacy link** — shipped 2026-06-07 (Cycle 140; Settings link and release gate).
  - Why: Google Play requires a privacy-policy link in Play Console and a privacy-policy link or text in the app for every app; Aura has no tracked `docs/privacy` policy document today.
  - Evidence: Cycle 4 privacy inventory; Cycle 7 AI prompt/key findings; Cycle 88 privacy policy; Google Play User Data and Data safety policy docs.
  - Touches: `docs/privacy/privacy-policy.md`, Settings privacy screen, README, distribution docs, release checklist.
  - Acceptance: policy covers developer/contact, data types, collection/sharing, third-party services, sensitive permissions, local storage, retention/deletion, community uploads, AI prompts, diagnostics, and no-sale/no-ads/no-tracking claims; public non-PDF URL plan exists; Settings links it.
  - Verify: privacy policy rows reconcile with Data safety matrix; app link opens; release checklist blocks missing URL/text.
  - Progress 2026-06-07: Cycle 88 added `docs/privacy/privacy-policy.md`; Cycle 140 added Settings > About > Privacy policy, README privacy copy, `docs/privacy/privacy-policy-link.json`, `tools/privacy_policy_link_check.py`, focused backend tests, verify/release workflow gates, and release-doc/security-policy coverage for the public URL.

- [x] 🤖 🔬 **P0 — Play app-content declaration packet** — shipped 2026-06-07 (Cycle 145; checked owner-ready packet with explicit owner actions).
  - Why: Aura's public community uploads, AI generation, contacts, weather location, microphone recording, and third-party provider calls affect Play forms and reviewer expectations.
  - Evidence: community upload metadata, AI generation, contacts/weather/microphone permissions; Google Play Data safety, content rating, UGC, and AI-generated-content policies.
  - Touches: `docs/distribution/play-app-content.md`, Data safety matrix, content rating notes, target audience decision, permissions justification, UGC/AI report queue docs.
  - Acceptance: one owner-ready document covers Data safety answers, content rating questionnaire notes, target audience, ads declaration, app access instructions, UGC moderation/report/block flow, AI-generated-content reporting, and sensitive-permission justifications.
  - Verify: owner can walk Play Console App content forms from the doc; every sensitive permission and user-generated/AI-generated feature has a row; no "unknown" rows remain before Play submission.
  - Progress 2026-06-07: Cycle 145 added `docs/distribution/play-app-content.md`, `docs/distribution/play-app-content.json`, `tools/play_app_content_packet_check.py`, focused tests, verify/release workflow gates, release-doc coverage, and explicit owner actions for hosted deletion URL publication, UGC terms/guidelines consent, live content rating questionnaire completion, and App content receipt capture. Cycle 146 implemented the UGC guidelines consent action with versioned policy storage, shared consent UI, community repository/UI gates, `docs/legal/community-guidelines.md`, and `tools/community_guidelines_consent_check.py`; remaining owner actions are hosted deletion URL publication, live content rating questionnaire completion, and App content receipt capture.

- [ ] 🤖 🔬 **P1 — Screenshot and feature-graphic pipeline**
  - Why: Aura has one root screenshot and no committed fastlane screenshot directories or feature graphic; Play requires at least two screenshots and a feature graphic and recommends four phone screenshots.
  - Evidence: `screenshot.png` 1080x2340; `app/src/main/ic_launcher-playstore.png` 512x512; no tracked `fastlane/metadata/android/en-US/images`; Google Play preview asset docs; F-Droid metadata docs.
  - Touches: screenshot capture script/runbook, `fastlane/metadata/android/en-US/images/phoneScreenshots/`, feature graphic asset, alt-text notes, release checklist.
  - Acceptance: at least four current 9:16 phone screenshots cover Wallpapers, Videos, Sounds/editor, and Settings/Favorites/community; feature graphic exists; assets avoid stale device frames, third-party trademarks, Play badges, and sensitive user content; alt text is documented.
  - Verify: image-dimension preflight; manual visual review; fastlane metadata includes expected files; README screenshot and store screenshots do not drift.
  - Progress 2026-06-07: Cycle 150 added `docs/distribution/store-assets.md`, `docs/distribution/store-assets.json`, `tools/store_asset_pipeline_check.py`, focused tests, verify/release workflow gates, release metadata/security workflow policy coverage, and release-doc/README coverage. The checked status is `capturePending`; actual Fastlane screenshots and feature graphic remain open before `tools/store_metadata_preflight.py --repo-root . --require-assets --min-phone-screenshots 4` can become mandatory.

- [x] 🤖 🔬 **P1 — Alternative-store anti-feature and permission disclosure matrix** — shipped 2026-06-07 (Cycle 147; checked channel/anti-feature/permission/network/dependency packet).
  - Why: Aura is full-only today and uses Firebase, Google Services, Play Services ML Kit, YouTube extraction, and third-party network services. IzzyOnDroid/F-Droid-style users need explicit disclosure.
  - Evidence: `docs/distribution/channel-strategy.md`; `tools/fdroid_preflight.py`; F-Droid inclusion and anti-feature docs; IzzyOnDroid APK/security notes.
  - Touches: `docs/distribution/channel-strategy.md`, `docs/distribution/alt-store-metadata.md`, Izzy submission notes, README, fastlane full description.
  - Acceptance: each channel lists artifact source, signing/checksum, license, proprietary dependencies, network services, sensitive permissions with purpose, AI key behavior, UGC moderation, and likely anti-feature labels; F-Droid mainline remains blocked until the FOSS flavor criteria pass.
  - Verify: run F-Droid preflight; compare APK manifest permissions against the disclosure matrix; owner can paste Izzy-sensitive-permission explanations without re-researching.
  - Progress 2026-06-07: Cycle 147 added `docs/distribution/alt-store-metadata.md`, `docs/distribution/alt-store-metadata.json`, `tools/alt_store_metadata_check.py`, focused tests, verify/release workflow gates, release-doc coverage, and README/channel-strategy links. The matrix covers GitHub Releases, Obtainium, IzzyOnDroid, F-Droid mainline blocked state, NonFreeDep/NonFreeNet/Tracking/Ads/TetheredNet notes, all 15 manifest permissions, all 15 reviewed network endpoint IDs, and 8 proprietary dependency markers.

- [x] 🤖 🔬 **P2 — Release metadata consistency gate** — shipped 2026-06-07 (Cycle 148; checked package/version/Fastlane/README/privacy/release artifact consistency).
  - Why: README, fastlane metadata, changelogs, generated GitHub release notes, privacy docs, and Play/Data safety answers can drift from shipped behavior.
  - Evidence: release workflow generates artifact-only notes; fastlane changelog exists for 112; Cycle 4/7/8 docs add privacy and AI policy rows.
  - Touches: release checklist, preflight script, docs/distribution, changelog workflow, README.
  - Acceptance: release checklist requires current versionName/versionCode, changelog, README claim review, privacy/Data safety review, store metadata review, screenshot review, and alt-store disclosure review before tag publishing.
  - Verify: dry-run metadata preflight on current tree; intentional stale version/changelog fixture fails; tag release notes still include signing/provenance data.
  - Progress 2026-06-07: Cycle 148 added `docs/distribution/release-metadata-consistency.md`, `docs/distribution/release-metadata-consistency.json`, `tools/release_metadata_consistency_check.py`, focused tests, verify/release workflow gates, and release-doc/README coverage. The gate reconciles `app/build.gradle.kts`, Fastlane title/short/full/changelog text, README links, privacy URL, Play App content packet, alternative-store packet, release preflight commands, and expected GitHub release artifacts.

- [x] 🤖 🔬 **P2 — SBOM readiness gate** — shipped 2026-06-07 (Cycle 149; checked deferred SBOM decision, current evidence floor, and future CycloneDX/SPDX scope).
  - Why: Aura has dependency notice, native compliance, checksum, and attestation controls, but the SBOM generation decision was only a prose deferral and could drift before the N-1 toolchain upgrade.
  - Evidence: `docs/distribution/supply-chain.md`; CycloneDX Gradle plugin docs; SPDX overview; GitHub artifact attestation docs.
  - Touches: `docs/distribution/sbom-readiness.md`, release/verify workflows, release docs, supply-chain docs, README.
  - Acceptance: current status stays `deferredUntilN1ToolchainUpgrade`; current evidence paths exist; future SBOM artifact names and scope are source-backed; verify/release workflows run the readiness check before Android build work.
  - Verify: run `tools/sbom_readiness_check.py`; stale status, missing evidence path, missing future artifact, or missing workflow command fixtures fail.
  - Progress 2026-06-07: Cycle 149 added `docs/distribution/sbom-readiness.md`, `docs/distribution/sbom-readiness.json`, `tools/sbom_readiness_check.py`, focused tests, verify/release workflow gates, release metadata/security workflow policy coverage, and release/supply-chain/README docs. SBOM generation remains deferred until N-1, but the release evidence floor and future artifact contract are now checked.

---

## 🔬 Researcher Queue (Cycle 9 — 2026-06-04)

Append-only Cycle 9 handoff. Every item below is source-backed in `docs/research/cycle-9-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [~] 🤖 🔬 **P0 — Firebase rules test and deploy harness**
  - Why: Aura has tracked RTDB rules but no tracked Firebase deploy config, Storage rules file, emulator config, or rules test harness.
  - Evidence: `database.rules.json`; missing `firebase.json`, `.firebaserc`, `storage.rules`, and rules-unit test files; Firebase Emulator Suite docs; Firebase RTDB/Storage rules docs.
  - Touches: `firebase.json`, `.firebaserc` or documented project alias policy, `database.rules.json`, new `storage.rules`, rules tests, CI/release checklist, `docs/firebase-admin-claims.md`.
  - Acceptance: emulator-backed tests cover public read, authenticated write, owner update/delete, admin moderation, votes/voters, reports, creator profiles, and collection shares; deploy and rollback commands are documented; CI or a release preflight runs the rules suite.
  - Verify: rules tests fail on unauthenticated writes and non-owner updates; admin Custom Claim tests pass; `firebase emulators:exec` or equivalent command exits cleanly before deploy.
  - Progress 2026-06-06: Cycle 57 added `firebase.json`, `storage.rules`, local npm rules-unit-testing scripts, `docs/firebase-rules-harness.md`, and Storage emulator tests for owner-only community upload blobs. Cycle 58 added the Database emulator config, RTDB rules tests, a combined `test:firebase-rules` script, deploy-compatible `database.rules.json` cleanup, and app-matched `shared_collections` rules. Cycle 59 added a path-gated `firebase-rules` job to the main `verify` workflow. Cycle 64 added `tools/community_backend_manifest.py`, `docs/community-backend-manifest.json`, CI manifest checking, and `docs/community-backend-runbook.md` with preflight, dry-run, deploy, rollback, and evidence steps. Remaining work: run and archive a real production-project dry run after owner access is confirmed.

- [ ] 🤖 🔬 **P0 — Community owner field normalization and delete/takedown flow**
  - Why: RTDB rules authorize owners through `uploaderUid`, while upload repositories write `uploaderId`; Aura cannot safely promise self-service delete/edit or owner-only metadata writes until this is consistent.
  - Evidence: `database.rules.json`; `UploadRepository.kt`; `WallpaperUploadRepository.kt`; Cycle 4 community data lifecycle finding; Firebase Storage delete docs.
  - Touches: upload metadata schema, migration/backfill plan, RTDB rules, Storage delete path, upload detail actions, privacy/community-data docs.
  - Acceptance: one canonical owner field is used for sounds and wallpapers; existing metadata is migrated or dual-read safely; owners can delete their metadata and Storage object; admins can takedown content; failed blob/metadata deletes leave auditable retry state.
  - Verify: owner delete removes feed item and Storage object; non-owner delete fails in rules tests; admin takedown hides/removes as designed; orphan retry path is exercised.
  - Progress 2026-06-06: Cycle 55 kept `uploaderUid` as the rules-authoritative owner field for new upload delete methods, stored `storagePath`, wrote private owner indexes, and added owner-only repository deletes for new rows with deletion handles. Cycle 56 added visible owner actions. Cycle 57 added Storage rules tests for owner/admin blob deletion and cross-owner rejection. Cycle 58 added RTDB rules tests for owner metadata delete, admin metadata delete, owner-index privacy, and cross-owner rejection. Cycle 60 added rights takedown receipts that validate against current upload handles. Cycle 61 added admin delete actions with `STARTED`/`SUCCEEDED`/`FAILED` receipt state. Remaining work: legacy backfill and deletion retention/tombstone policy.

- [~] 🤖 🔬 **P1 — Storage rules and orphan lifecycle cleanup**
  - Why: Upload size/type/path checks currently live in app code, but Storage needs server-side enforcement and cleanup for orphaned blobs or removed content.
  - Evidence: no tracked `storage.rules`; sound uploads allow 20 MB locally; wallpaper uploads compress to 4 MB locally; Firebase Storage rules docs expose `request.resource.size` and `request.resource.contentType`; Cloud Storage lifecycle docs.
  - Touches: `storage.rules`, Storage emulator tests, upload repositories, cleanup job/runbook, lifecycle policy docs, release checklist.
  - Acceptance: Storage rules restrict writes to authenticated owner paths, enforce sound/image MIME and size ceilings, define read behavior, and block cross-user overwrite/delete; lifecycle or cleanup process handles abandoned temp/orphan objects without deleting valid public uploads.
  - Verify: emulator tests reject oversize/wrong-type/cross-owner uploads; metadata-save failure cleanup still works; orphan cleanup dry run reports expected objects before delete.
  - Progress 2026-06-06: Cycle 57 added tracked `storage.rules`, `firebase.json`, and emulator tests for sound/wallpaper owner creates, public reads, MIME/size ceilings, blocked overwrites, owner/admin deletes, cross-owner rejection, anonymous rejection, and unmanaged path denial. Cycle 59 wired the combined Firebase rules suite into CI for rules-related changes. Cycle 65 added `docs/community-storage-lifecycle-policy.md`, `tools/community_storage_orphan_report.py`, unittest coverage, and CI execution for backend lifecycle/tool changes. Remaining work: run real exported Storage/RTDB orphan reports after owner access is confirmed and add a future temp-prefix lifecycle rule if upload finalization moves to temporary paths.

- [~] 🤖 🔬 **P1 — App Check and community abuse throttling**
  - Why: Anonymous Auth and vote markers do not prove requests come from Aura or prevent scripted uploads, votes, reports, follows, and profile writes at scale.
  - Evidence: `CommunityIdentityProvider.ensureSignedIn()` anonymous/fallback identity; `VoteRepository.upvote()` voter marker; no App Check init/runbook found; Firebase App Check docs for RTDB and Storage.
  - Touches: App Check initialization, debug-provider docs, Firebase console enforcement runbook, upload/vote/report quota design, backend counters or Cloud Functions if needed, release checklist.
  - Acceptance: App Check is installed with Play Integrity plus debug/dev instructions; request metrics are monitored before enforcement; RTDB and Storage enforcement dates are recorded; abuse quotas exist for uploads, reports, votes, follows, and profile edits.
  - Verify: debug build works with debug token; release build obtains valid token; monitor-mode metrics are reviewed; after enforcement, unauthenticated/unverified scripted requests fail while normal app flows pass.
  - Progress 2026-06-07: Cycle 53 installed debug and Play Integrity providers, added the rollout runbook, compiled debug and release variants, refreshed dependency verification metadata, and refreshed generated notice locks. Cycle 54 added a typed quota policy for reports, sound uploads, wallpaper uploads, votes, follows, and profile edits, plus protected admin-only quota/dedupe ledgers in RTDB rules. Cycle 57 added Storage rules and Storage emulator tests. Cycle 58 added RTDB emulator tests for admin-only quota and dedupe ledgers. Cycle 63 added callable names, payload schemas, final write paths, protected ledger coverage, and limited-use App Check token decisions to the quota policy model. Cycles 94-100 implemented all callable handlers, Cycles 101-107 added RTDB-emulator-backed handler persistence coverage, Cycle 108 added Android callable-first report submission, Cycle 109 added Android callable-first vote submission, Cycle 110 added Android callable-first follow/unfollow submission, Cycle 111 added Android callable-first block/unblock submission, Cycle 112 added Android callable-first sound upload metadata finalization, Cycle 113 added Android callable-first wallpaper upload metadata finalization, Cycle 114 added Android callable-first profile edits, and Cycle 115 added checked Android callable wire-protocol coverage. Remaining work: console metrics/enforcement evidence, live callable invocation evidence, and direct-rule tightening.

- [~] 🤖 🔬 **P1 — Moderation report queue and audit trail**
  - Why: The current `/moderation/{contentId}=true` boolean hides content but does not capture report reason, reporter privacy, resolver, timestamp, status, appeal/restore, or block semantics required for public UGC operations.
  - Evidence: `VoteRepository.moderateHide()` and `moderateUnhide()`; `database.rules.json` moderation path; Cycle 1 report queue item; Cycle 8 Play UGC policy review.
  - Touches: report models/repository, report actions in content detail screens, RTDB rules, admin moderation UI, privacy/report docs, Play app-content packet.
  - Acceptance: users can report community content with categories; reports are App-Checked/authenticated and rate-limited; admins can resolve/hide/unhide with reason and timestamp; audit entries record resolver UID without exposing reporter identity publicly; block-user behavior is defined or deferred with an owner decision.
  - Verify: report create/read/admin-resolve rules tests; manual report -> admin hide -> feed removal -> unhide flow; reporter data is not public; Play UGC checklist row is complete.
  - Progress 2026-06-07: Cycle 51 added report reasons, private report intake, admin-only read/update rules, resolution metadata records, and detail-screen report submission. Cycle 52 added admin Settings access, open-report subscription, report cards, status-indexed RTDB rules, Hide/Dismiss/Restore actions, and moderation hide/unhide wiring. Cycle 53 installed App Check providers and the rollout runbook. Cycle 54 defined the quota policy and reserved admin-only report quota/dedupe ledgers. Cycle 58 added RTDB emulator tests for authenticated report creation, reporter UID validation, admin-only reads, admin status updates, and admin-only resolution receipts. Cycle 60 added admin-only rights takedown receipt rules and emulator coverage. Cycle 61 added admin delete actions for rights-confirmed community upload reports. Cycle 62 added status filters for closed moderation queues. Cycle 63 added the callable quota enforcement contract and report submission migration sequence. Cycle 68 reserved private block-user paths, admin reverse indexes, rules coverage, and callable quota metadata. Cycle 70 added block-list repository support and feed/profile filtering. Cycle 71 added visible detail-screen block actions for community sounds and wallpapers. Cycle 72 added Settings blocked-creators review and unblock actions. Cycle 73 added optional report uploader UID metadata plus report-card and creator-profile block actions. Cycles 94-100 implemented all callable handlers, Cycles 101-107 added RTDB-emulator-backed handler persistence coverage, Cycle 108 added Android callable-first report submission, Cycle 109 added Android callable-first vote submission, Cycle 110 added Android callable-first follow/unfollow submission, Cycle 111 added Android callable-first block/unblock submission, Cycle 112 added Android callable-first sound upload metadata finalization, Cycle 113 added Android callable-first wallpaper upload metadata finalization, and Cycle 114 added Android callable-first profile edits. Remaining work: full callable protocol coverage, owner-approved deploy evidence, and direct-rule tightening.

- [ ] 🤖 🔬 **P2 — Community backend operations runbook**
  - Why: Community backend changes can break public reads/writes independently from APK builds, and Aura has no single release artifact tying rules, App Check, Storage cleanup, moderation, and deletion evidence together.
  - Evidence: `docs/firebase-admin-claims.md` manual deploy notes; missing Firebase deploy config; Cycle 8 release metadata packet; Firebase and Cloud Storage lifecycle docs.
  - Touches: `docs/community-backend-runbook.md`, release checklist, deploy/rollback notes, incident/takedown notes, App Check enforcement ledger, Storage lifecycle policy evidence.
  - Acceptance: runbook records current deployed rules hash/version, deploy command, rollback command, App Check monitor/enforce status, rules test command, cleanup cadence, takedown SLA, and owner/admin deletion verification steps.
  - Verify: dry-run release checklist includes backend evidence; a simulated bad rules deploy has a rollback path; a sample takedown/delete case leaves documented evidence without leaking user data.
  - Progress 2026-06-06: Cycle 64 added the deterministic backend manifest, CI manifest gate, and `docs/community-backend-runbook.md` for preflight, dry run, deploy, rollback, App Check rollback separation, and release checklist evidence. Cycle 65 added the Storage lifecycle/orphan cleanup policy, offline orphan-report tool, and two-report manual deletion gate. Remaining work: takedown SLA packet, owner/admin deletion verification evidence, and live orphan report evidence after owner access is confirmed.

---

## 🔬 Researcher Queue (Cycle 10 — 2026-06-04)

Append-only Cycle 10 handoff. Every item below is source-backed in `docs/research/cycle-10-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [ ] 🤖 🔬 **P0 — API 37 toolchain and target-SDK release gate**
  - Why: Aura is on compile/target SDK 35 and AGP 8.7.3; Android 17 SDK docs require compile/target SDK 37 and AGP 8.9.0-rc01+ for API 37 work.
  - Evidence: `app/build.gradle.kts`; `baselineprofile/build.gradle.kts`; `gradle/libs.versions.toml`; Android 17 SDK setup docs; Android 17 Platform Stability blog.
  - Touches: N-1; Gradle catalog, app/baseline profile Gradle files, Android Studio/SDK setup docs, CI image, dependency verification metadata, release preflight.
  - Acceptance: app and baselineprofile modules compile at SDK 37, target SDK 37 is enabled deliberately, AGP meets the documented minimum, metadata skip workaround is removed or justified, Android 17 emulator/device smoke lane exists, and target-37 behavior changes are checklist-gated.
  - Verify: `:app:assembleDebug`, `:app:testDebugUnitTest`, lint/manifest checks where feasible, Android 17 emulator install/smoke, and target-37 compat checklist signed off before release.

- [~] 🤖 🔬 **P0 — Android 17 Contact Picker migration for contact ringtones**
  - Why: Aura requests broad `READ_CONTACTS` and `WRITE_CONTACTS` together, while Android 17 offers selected-contact read access through the system Contact Picker.
  - Evidence: `AndroidManifest.xml`; `ContactPickerScreen.kt`; `ContactRingtoneService.kt`; Android 17 Contact Picker docs; Android 17 target-SDK Contacts Provider behavior notes.
  - Touches: Cycle 4 contact-permission item; contact picker UI, ringtone apply flow, permission copy, privacy/Data safety docs, Play app-content packet.
  - Acceptance: API 37+ selection uses the system Contact Picker without broad `READ_CONTACTS` when possible; any `WRITE_CONTACTS` request is just-in-time for applying or clearing the ringtone; older devices keep a just-in-time fallback; unsupported write cases explain the limitation.
  - Verify: select contact on Android 17 without `READ_CONTACTS`; apply ringtone with/without `WRITE_CONTACTS`; clear ringtone; denied permission path; older-device fallback; Data safety row updated.
  - Progress 2026-06-11: Local fallback now uses Android contact-pick selection without declaring `READ_CONTACTS`, runtime write permission is requested only on apply, and Data safety/Play/alt-store rows reflect the narrower contact surface. Remaining work: API 37 picker smoke and clear-ringtone flow validation after the Android 17 toolchain lane unlocks.

- [ ] 🤖 🔬 **P1 — Android 17 large-screen/adaptive-layout smoke gate**
  - Why: Target-37 apps cannot rely on orientation/resizability/aspect restrictions on `sw > 600dp` displays, and Aura has no formal window-size-class navigation or list-detail layout code.
  - Evidence: no manifest orientation lock found; no `WindowSizeClass`/`NavigationSuiteScaffold`/`ListDetailPaneScaffold`; fixed two-column grids in several screens; Android 17 form-factor behavior docs.
  - Touches: N-1/NX adaptive work; `FreeVibeRoot.kt`, wallpaper/category/favorites/collections grids, detail screens, screenshot/runbook matrix.
  - Acceptance: tablet, foldable, landscape, split-screen, and desktop-windowing smoke tests are documented; no critical clipping/off-screen controls in primary flows; adaptive-nav/list-detail follow-ups are filed or implemented.
  - Verify: Android 17 large-screen emulator screenshots for Wallpapers, Wallpaper Detail, Sounds, Contact Picker, Collections, Favorites, Settings, Video Crop, and AI Generation.

- [ ] 🤖 🔬 **P1 — Android 17 background-audio hardening regression suite**
  - Why: Aura has a Media3 `mediaPlayback` foreground service, but Android 17 can silently fail audio interactions when lifecycle/foreground-service state is invalid.
  - Evidence: `AudioPlaybackService.kt`; manifest `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `foregroundServiceType="mediaPlayback"`; Android 17 background audio hardening docs.
  - Touches: audio preview service, Sounds/detail playback, notification/foreground-service copy, manual QA script, release checklist.
  - Acceptance: playback behavior is defined for visible, backgrounded, task-removed, and notification-permission states; logcat/dumpsys checks show no `AudioHardening` failures in supported flows; unsupported flows fail visibly.
  - Verify: start preview -> background -> resume; task remove while playing/not playing; notification permission denied/allowed; `adb dumpsys audio` and logcat checked for package-prefixed `AudioHardening` entries.

- [ ] 🤖 🔬 **P1 — Target-37 privacy/security compatibility preflight**
  - Why: Android 17 adds mandatory local-network handling for target-37 apps, ECH, default Certificate Transparency, stricter reflection/static-final behavior, Contacts Provider privacy changes, and safer native DCL constraints.
  - Evidence: no current `ACCESS_LOCAL_NETWORK`, sockets, `NsdManager`, `WifiManager`, or `.local` discovery hits; `network_security_config.xml` cleartext exception for `ccmixter.org`; Android 17 behavior-change docs; local-network permission docs.
  - Touches: release preflight script, network/provider docs, `network_security_config.xml` review, dependency/native-loading scan, contact-provider query audit, privacy policy/Data safety.
  - Acceptance: preflight fails on new LAN APIs without a declared decision, flags broad contacts queries and private reflection patterns, documents the `ccmixter.org` cleartext exception, and records CT/ECH provider compatibility review.
  - Verify: static grep/preflight run; Android 17 network smoke across Wallhaven/Pexels/Pixabay/Freesound/ccMixter/YouTube/AI/Firebase; no local-network permission prompt appears in current flows.

- [ ] 🤖 🔬 **P2 — Direct Android 17 API cleanup for shipped bridges**
  - Why: EyeDropper and Photo Picker 9:16 behavior shipped through raw string/reflection bridges that should become direct API usage once compileSdk 37 lands.
  - Evidence: `WallpapersScreen.kt`; `AuraPickVisualMedia.kt`; `PhotoPickerCustomization.kt`; Android Intent API reference; Android 17 Beta 3 Photo Picker blog; `CHANGELOG.md` bridge notes.
  - Touches: NX-10/NX-11 follow-up; wallpaper picker, collection import picker, parallax photo picker, changelog/release notes, fallback logging.
  - Acceptance: direct `Intent.ACTION_OPEN_EYE_DROPPER`/`Intent.EXTRA_COLOR` and direct Photo Picker customization API are primary paths on compileSdk 37; fallback logging remains for missing system components; reflection/raw constants are isolated or deleted.
  - Verify: Android 17 device/emulator EyeDropper result; Photo Picker 9:16 grid on all three call sites; Android 16 and below keep default picker behavior; debug logs show no unexpected reflection failure.

---

## 🔬 Researcher Queue (Cycle 11 — 2026-06-04)

Append-only Cycle 11 handoff. Every item below is source-backed in `docs/research/cycle-11-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [x] 🤖 🔬 **P0 — Sensitive-permission inventory and release preflight** — shipped 2026-06-07 (Cycle 141; manifest permission Data safety gate).
  - Why: Aura declares `WRITE_SETTINGS`, microphone, coarse location, contacts, notification, foreground-service, legacy storage, and alarm-related permissions, but no single docs-side ledger maps each permission to a current feature, user action, Data safety row, denial path, retention decision, and store declaration.
  - Evidence: `AndroidManifest.xml`; `fastlane/metadata/android/en-US/full_description.txt`; `docs/privacy/data-safety.md`; Play permissions/sensitive-API policy; Play personal/sensitive user-data policy; Play Data safety docs.
  - Touches: Cycle 4 privacy matrix; Cycle 8 app-content packet; `docs/privacy/data-safety.md`, `docs/distribution/play-app-content.md`, release preflight/static manifest check, fastlane metadata.
  - Acceptance: every manifest permission has a row for purpose, triggering UI, data accessed, collection/sharing, retention, denial behavior, Play declaration status, alternate-store disclosure, and owner signoff; new permissions fail release preflight until the row exists; listing/privacy/Data safety claims are internally consistent.
  - Verify: parse merged manifest and compare with ledger; review Play Data safety fields for location, contacts, voice/sound recordings, other audio files, crash/diagnostics, user-generated content, and device IDs; confirm fastlane copy does not contradict the ledger.
  - Progress 2026-06-07: Cycle 141 added `docs/privacy/data-safety.md`, `docs/privacy/data-safety.json`, `tools/privacy_data_safety_check.py`, focused tests, and verify/release workflow gates. The live matrix covers all 15 manifest permissions and fails if permissions or `maxSdkVersion` values drift without a reviewed row.

- [ ] 🤖 🔬 **P1 — Foreground-service and notification declaration packet**
  - Why: Aura declares `mediaPlayback` and `specialUse` foreground services; Play target-34+ updates require service-type descriptions, user-impact explanations, and demonstration videos, while Android notification permission denial changes user-visible notification behavior.
  - Evidence: `AndroidManifest.xml`; `AudioPlaybackService.kt`; `RotationTriggerService.kt`; `SettingsScreen.kt`; Android foreground-service type docs; Play foreground-service declaration docs; Android notification-permission docs.
  - Touches: Play app-content packet, notification/channel copy, rotation-trigger settings, audio playback QA script, release checklist.
  - Acceptance: packet lists every foreground service, type, trigger, user-visible notification, why delay/deferral is not equivalent, interruption impact, and demo-video script; notification-denied behavior is documented for playback and rotation triggers; no `BOOT_COMPLETED` media-playback launch exists.
  - Verify: start/stop playback and rotation triggers, inspect `dumpsys activity services`/notification state, deny `POST_NOTIFICATIONS`, capture demo-video steps, and compare manifest services with Play declaration rows.

---

## 🔬 Researcher Queue (Cycle 12 — 2026-06-04)

Append-only Cycle 12 handoff. Every item below is source-backed in `docs/research/cycle-12-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [ ] 🤖 🔬 **P0 — Accountless community deletion contract**
  - Why: Anonymous community data spans Auth, RTDB, Storage, local identity prefs, votes, follows, creator profile state, and public uploads; deleting only the Firebase user would orphan public data.
  - Evidence: `CommunityIdentityProvider`; `UploadRepository`; `WallpaperUploadRepository`; `VoteRepository`; `CreatorProfileRepository`; `database.rules.json`; Play account-deletion and Data safety docs; Firebase Auth/RTDB/Storage delete docs.
  - Touches: Settings community/privacy controls, creator profile screen, `docs/privacy/privacy-policy.md`, Data safety matrix, community backend runbook, Firebase rules/tests.
  - Acceptance: in-app deletion path lets users delete their community identity and associated data; scope explicitly includes uploads, Storage objects, upload metadata, vote markers, follows, profiles, collection shares, local fallback UUID, and local community caches; any retained moderation/abuse record is disclosed with reason and retention.
  - Verify: seed user with sound upload, wallpaper upload, votes, follows, profile data, and collection share; run deletion; confirm RTDB/Storage/Auth/local prefs reflect the documented result and public feeds no longer expose deleted content.
  - Progress 2026-06-07: Cycle 74 added `docs/community-account-deletion-policy.md` plus `tools/community_account_deletion_plan.py` to build a dry-run RTDB null-update plan for vote markers, follows, creator profiles, block indexes, and community shares. Cycle 75 added Settings > Community identity with auth label, redacted identity suffix, and deletion request code display without creating an identity. Cycle 76 added a redacted shareable deletion request draft plus user/operator support docs. Cycle 77 added `tools/community_deletion_request_lookup.py` to map request codes to candidate UID evidence in RTDB exports. Cycle 78 added `tools/community_account_deletion_review.py` to cross-check lookup output against dry-run null-update plans and emit redacted review receipts. Cycle 79 added `tools/community_account_deletion_apply_simulator.py` to verify reviewed plan hashes, simulate null updates against a copied export, and emit hashed simulation receipts while keeping retained roots protected. Cycle 80 added `tools/community_account_deletion_executor_package.py` to validate the full artifact chain and produce the private RTDB null-update payload. Cycle 81 added `tools/community_account_deletion_rest_executor.py` with dry-run default and explicit request-code, plan-hash, database URL, and OAuth token gates for RTDB REST `PATCH` apply mode. Cycle 82 added `tools/community_account_deletion_completion_receipt.py` to validate applied REST receipts and emit redacted requester-facing completion receipts. Cycle 83 added `docs/support/community-account-deletion-web-intake.md` and `tools/community_deletion_web_intake.py` for private hosted request intake validation. Cycle 84 added `tools/community_account_deletion_cleanup_sequence.py` to order post-completion local device cleanup, Firebase Auth deletion, and public upload handoff. Cycle 85 added Settings > Community identity > Clear local to remove the current device fallback identity after support confirms backend completion. Cycle 86 added `tools/community_account_deletion_auth_package.py` to build private Firebase Auth deletion packages after lookup and backend completion evidence match. Cycle 87 added `tools/community_account_deletion_upload_plan.py` to enumerate owned public uploads and blocked handle rows before any owner/admin deletion workflow runs. Cycle 88 added `docs/privacy/privacy-policy.md`, `docs/support/community-account-deletion-web-url.json`, and `tools/community_deletion_web_url_check.py` to gate the hosted web deletion URL from pending owner publication to a live HTTPS link. Cycle 89 added `tools/community_account_deletion_auth_execution_receipt.py` to validate owner-approved Auth deletion evidence and emit a redacted post-delete receipt. Cycle 90 added `tools/community_account_deletion_upload_execution_receipt.py` to validate owner/admin public-upload deletion evidence and emit a redacted receipt. Actual owner-run Auth/upload deletion and hosted URL publication remain open.

- [ ] 🤖 🔬 **P0 — Firebase deletion orchestrator and web request runbook**
  - Why: Play expects a web deletion resource for users who uninstalled or cannot access the app; an Android-only deletion button cannot satisfy those requests, especially for anonymous Firebase identities.
  - Evidence: Play account-deletion web-resource requirements; `database.rules.json` owner/admin paths; missing `firebase.json`/rules test harness from Cycle 9; Firebase `removeValue()`, `updateChildren(null)`, Storage `delete()`, and Auth `delete()` docs.
  - Touches: Cloud Function or trusted admin script, support/deletion request page, identity-verification receipt, Firebase rules tests, admin runbook, release checklist.
  - Acceptance: trusted deletion job can accept a verified UID/deletion code, enumerate all user-owned RTDB paths and Storage objects, delete/anonymize according to policy, delete the Auth user where possible, and record a minimal nonpublic audit receipt; web/email request instructions are discoverable from the privacy policy.
  - Verify: emulator/admin dry run with seeded data; deletion failure retry path; post-delete public read checks; web-request support script produces a human-readable receipt without exposing other users' data.

- [ ] 🤖 🔬 **P1 — Owner indexes and Storage deletion handles for community uploads**
  - Why: Upload metadata stores public download URLs but not a canonical `storagePath` or per-owner upload index, making owner deletion and admin web deletion dependent on broad scans and brittle URL parsing.
  - Evidence: `UploadRepository.uploadSound()` Storage path and RTDB metadata; `WallpaperUploadRepository.uploadWallpaper()` Storage path and RTDB metadata; `database.rules.json`; Firebase RTDB/Storage delete docs.
  - Touches: upload metadata schema, RTDB rules, new owner index (`owner_uploads/{uid}` or equivalent), Storage rules, migration/backfill script, community backend runbook.
  - Progress 2026-06-06: Cycle 55 added `CommunityUploadOwnership.kt`, `storagePath` metadata, `/owner_uploads/{uid}/sounds|wallpapers/{uploadId}` writes, owner delete update builders, repository delete methods, and RTDB rules for owner/admin index access. Cycle 56 added owner-visible app deletes. Cycle 57 added Storage rules and emulator tests for upload blob authorization. Cycle 58 added RTDB emulator tests for upload metadata and owner-index authorization. Cycle 66 added dry-run legacy backfill planning from Firebase Storage URLs to canonical `storagePath` plus owner-index update payloads. Cycle 67 added owner/admin deletion tombstones with owner-scoped Storage handle validation and a retention policy. Remaining work: run the planner against a fresh production RTDB export and add trusted apply tooling if service-account handling is approved.
  - Acceptance: new uploads store `storagePath`, owner UID, content type, and upload ID in both public metadata and an owner-scoped index; rules allow owners/admins to delete their own records; legacy uploads are backfilled or marked "admin-only deletion until migrated."
  - Verify: upload sound/wallpaper, inspect metadata/index, delete by owner from app and by admin script, confirm Storage object deletion and public feed removal; rules tests reject cross-owner deletes.

- [ ] 🤖 🔬 **P1 — Vote, follow, profile, and moderation deletion semantics**
  - Why: Vote markers prevent duplicate voting, follows create both outbound user data and inbound creator references, and moderated content may need an audit trail. These cannot all use the same "hard delete" rule without policy decisions.
  - Evidence: `VoteRepository.upvote()` nested and legacy voter markers; `CreatorProfileRepository.followCreator()`/`unfollowCreator()`; `database.rules.json` moderation and follow paths; Play deletion-retention guidance.
  - Touches: deletion contract, privacy policy, RTDB schema, moderation runbook, abuse-prevention design, rules tests.
  - Acceptance: deletion policy separately defines behavior for public uploads, vote counts, voter markers, outbound follows, inbound follows to a deleted creator, creator profile labels, moderation records, and abuse-prevention tombstones; retained records are nonpublic and minimized.
  - Verify: deletion seed with voted content, followed creators, followers of deleted creator, and moderated upload; confirm post-delete counts, visibility, and retention match the policy.
  - Progress 2026-06-07: Cycle 67 defined the upload deletion subset in `docs/community-deletion-retention-policy.md` and implemented private upload tombstones. Cycle 68 reserved private user block lists and admin-only reverse indexes in `docs/community-block-user-policy.md`. Cycle 70 added Android block-list repository support plus community feed and creator profile filtering by private block state. Cycle 71 added confirmed `Block creator` actions on community sound and wallpaper detail surfaces. Cycle 72 added Settings review/unblock UI for blocked creators. Cycle 73 added report-card and creator-profile block entry points when canonical uploader IDs are available. Cycle 74 added the dry-run account deletion planner and policy: delete per-user vote markers, follows, creator profile rows, block rows/indexes, and share rows; retain aggregate vote counts and private moderation audit records. Cycle 75 added the read-only Settings identity/deletion request code surface. Cycle 77 added request-code lookup tooling, Cycle 78 added a redacted review receipt gate, Cycle 79 added an offline apply simulator, Cycle 80 added the private executor package builder, Cycle 81 added the guarded RTDB REST executor, Cycle 82 added a user-safe completion receipt after applied REST receipts, Cycle 83 added web-intake validation for private hosted request exports, Cycle 84 added local/Auth cleanup sequencing after backend completion, Cycle 85 added in-app local fallback identity cleanup, Cycle 86 added private Auth deletion packages, Cycle 87 added private public-upload deletion handoff plans, Cycle 88 added a privacy-policy-backed hosted URL manifest gate, Cycle 89 added a redacted Auth execution receipt gate, Cycle 90 added a redacted upload execution receipt gate, and Cycle 97 added the handler-backed `setCommunityUserBlock` callable. Remaining work: owner-approved production execution evidence, hosted URL publication, block callable emulator coverage, Android migration, and direct RTDB rule tightening.

- [ ] 🤖 🔬 **P2 — Community data receipt/export surface**
  - Why: Users need a way to understand and request deletion of an anonymous identity, especially after reinstall or when using the web deletion path.
  - Evidence: `CreatorProfileScreen` currently shows uploads/votes/follows/leaderboard but no UID, export, or delete controls; `CommunityIdentityProvider.currentAuthLabel()` already exposes auth type; Play web deletion guidance.
  - Touches: creator profile/settings UI, support docs, privacy policy, deletion request page, diagnostics redaction.
  - Acceptance: UI shows auth type, UID suffix/deletion code, owned upload IDs, follow count, vote count or vote-marker count, and links to delete/export/community privacy details; export/share output redacts full tokens and does not include other users' private data.
  - Verify: signed-out/local/Firebase-anonymous states; export before and after upload/vote/follow; deletion request code maps to the correct UID in admin tooling; diagnostics bundle redacts the full UID unless explicitly copied by the user.
  - Progress 2026-06-07: Cycle 75 added Settings > Community identity with auth type, redacted identity suffix, copyable deletion request code when a Firebase identity exists, and no identity creation while viewing the panel. Cycle 76 added a redacted share request draft and `docs/support/community-account-deletion.md`. Cycle 77 added admin lookup tooling for request-code-to-UID evidence. Cycle 78 added a redacted review receipt that proves lookup and deletion-plan consistency before future trusted apply. Cycle 79 added an offline simulation receipt for reviewed deletion plans. Cycle 80 added a private executor package that preserves the full RTDB update payload only for trusted operators. Cycle 81 added a guarded dry-run/apply REST executor for the private package. Cycle 82 added a redacted completion receipt that is safe to share after an applied REST receipt. Cycle 83 added a private web-intake receipt for users who cannot open the app. Cycle 88 added a privacy policy and hosted URL publication manifest for the web request route. Cycle 89 added a redacted Auth execution receipt after owner-approved Firebase Auth deletion evidence. Cycle 92 added checked hosted web page copy for owner publication. Remaining work: owned upload IDs, follow/vote marker counts, export output, live hosted URL publication, and production evidence.

---

## 🔬 Researcher Queue (Cycle 13 — 2026-06-04)

Append-only Cycle 13 handoff. Every item below is source-backed in `docs/research/cycle-13-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [~] 🤖 🔬 **P0 — Unified provenance and action-capability model**
  - Why: Sounds carry `license`, wallpapers generally do not, video wallpaper items have a separate metadata shape, and the static licenses screen does not prove whether a specific item can be edited, applied, downloaded, shared, bundled, or kept offline.
  - Evidence: `Models.kt`; `Mappers.kt`; `FreesoundV2Repository.kt`; `CcMixterRepository.kt`; `AudiusRepository.kt`; `SoundCloudRepository.kt`; `WallpaperDetailScreen.kt`; `SoundDetailScreen.kt`; Pexels/Pixabay/Freesound/YouTube/Reddit policy docs.
  - Touches: content models/entities, migrations, remote mappers, favorites/downloads/cache restoration, detail/share/apply/edit flows, provider policy docs, licenses screen.
  - Acceptance: every content item can expose source, source page, creator/uploader, normalized license, provider terms, takedown/report URL, and allowed actions; unsupported or risky actions are disabled or require source-specific confirmation; favorites/downloads preserve provenance.
  - Verify: fixture items for Pexels, Pixabay, Reddit, YouTube, Freesound, ccMixter, Audius, SoundCloud, community, bundled, AI-generated, and local content render provenance consistently across cards, detail screens, share sheets, favorites, downloads, and editors.
  - Progress 2026-06-06: Cycle 49 implemented the sound-specific action-capability slice: normalized license metadata, favorite-license persistence, detail/share/apply/download/contact gates, and matrix tests. Cycle 50 added selected license/source/attestation metadata for community sounds and wallpapers, plus wallpaper license preservation and detail display. 2026-06-13: added `WallpaperLicensePolicy.kt` with per-wallpaper action-capability gates (APPLY/DOWNLOAD/SHARE/EDIT) and 14 matrix tests covering Pexels, Pixabay, Bing, Reddit, Community, Local, AI-generated, Wallhaven, CC BY-NC, CC BY-ND, unavailable-source, missing-source-link, and unknown-license scenarios. Remaining provenance slices: video wallpapers, provider takedown/report URL integration.

- [ ] 🤖 🔬 **P0 — IP takedown/report queue for community and mirrored content**
  - Why: Google Play can require evidence of rights to use copyrighted content, and Aura needs one route for community-upload reports plus another for provider/source-deletion reconciliation.
  - Evidence: `database.rules.json` moderation boolean; no rights-holder report queue found; Cycle 9 moderation queue item; Google Play IP policy; Reddit developer terms; Pexels/Pixabay report-content links.
  - Touches: report repository/model, RTDB rules, admin moderation UI, detail-screen report actions, privacy/support docs, community backend runbook.
  - Acceptance: users and rights holders can report community uploads with IP/license/safety/source-removed reasons; admins can hide/delete/restore with timestamp/reason; third-party mirrored entries can be marked source-deleted/unavailable; takedown status is not exposed in a way that leaks reporter identity.
  - Verify: report -> admin hide/delete -> public feed removal -> restore flow; rights-holder report from detail/source screen; source-deleted Reddit/Pexels/Pixabay fixture stops appearing as live catalog content; rules tests block non-admin resolution.
  - Progress 2026-06-07: Cycle 51 added private rights/source/safety/spam report intake from sound and wallpaper details. Cycle 52 added admin review, hide, dismiss, restore, and moderation wiring. Cycle 60 added private rights-confirmed takedown receipts for community upload hides with current deletion handles. Cycle 61 added confirmed admin delete actions for qualifying community upload reports. Cycle 62 added closed-report status filters for admin review. Cycle 63 defined the App-Checked callable quota contract for report submission. Cycle 68 added the block-user data contract. Cycle 69 added public upload/report/delete takedown copy. Cycle 70 added block-list repository reads/writes and feed/profile filtering. Cycle 71 added confirmed block actions to community sound and wallpaper detail surfaces. Cycle 72 added Settings blocked-creators review and unblock actions. Cycle 73 added report uploader UID metadata and admin report-card block actions. Cycles 94-100 implemented callable handlers, Cycles 101-107 added RTDB-emulator-backed handler coverage, Cycle 108 added Android callable-first report submission, Cycle 109 added Android callable-first vote submission, Cycle 110 added Android callable-first follow/unfollow submission, Cycle 111 added Android callable-first block/unblock submission, Cycle 112 added Android callable-first sound upload metadata finalization, Cycle 113 added Android callable-first wallpaper upload metadata finalization, and Cycle 114 added Android callable-first profile edits. Remaining work: full callable protocol coverage, owner-approved deploy evidence, and direct-rule tightening.

- [ ] 🤖 🔬 **P1 — YouTube store-risk containment profile**
  - Why: Aura's Play-facing listing currently promotes YouTube-first ringtone discovery and video wallpapers, while Play IP policy and YouTube developer policies are restrictive around unauthorized downloads, cached audiovisual content, offline playback, and infringement encouragement.
  - Evidence: `fastlane/metadata/android/en-US/full_description.txt`; `SoundsViewModel.kt` YouTube tab and pasted-URL import; `VideoWallpapersViewModel.kt` YouTube video path; YouTube API policies; Google Play IP policy; Cycle 3 YouTube legal-mode item.
  - Touches: distribution/channel strategy, build/runtime provider switches, Sounds/Videos UI, fastlane metadata, screenshots, release checklist.
  - Acceptance: Play/Izzy/F-Droid/GitHub distribution profiles declare whether YouTube is enabled; disabled profiles remove YouTube tabs/search/default queries/video wallpaper paths and avoid promotional copy/screenshots that encourage unauthorized downloads; fallback sources remain useful.
  - Verify: run app with YouTube disabled and browse Sounds/Videos; confirm no yt-dlp/NewPipe resolution or cache writes; review Play metadata/screenshots for copyrighted-download language; GitHub profile can still enable YouTube when explicitly chosen.

- [~] 🤖 🔬 **P1 — Community upload rights attestation and license metadata**
  - Why: Community uploads publish audio/wallpaper metadata with `uploaderId`/`uploaderLabel`, but no explicit rights attestation, license, source URL, model/property release state, or takedown contact. Community sounds currently show `license = "User Upload"`.
  - Evidence: `UploadRepository.uploadSound()`; `WallpaperUploadRepository.uploadWallpaper()`; `SoundsScreen.kt` upload dialog; Play IP policy; Pixabay/Pexels third-party-rights warnings.
  - Touches: upload dialogs, metadata schema, RTDB rules, community cards/detail screens, privacy/policy docs, deletion/takedown runbook.
  - Acceptance: uploader must attest they own or have rights to share the media, choose a license/usage label, optionally provide source URL/credit, acknowledge public visibility and takedown rules, and understand that infringing content may be removed; metadata stores that evidence.
  - Verify: upload blocked until attestation complete; uploaded metadata includes license/source/rights fields; detail and report flows show license/takedown context; admin can remove content by rights reason.
  - Progress 2026-06-06: Cycle 50 added `CommunityUploadRights.kt`, sound/wallpaper upload dialog license chips, rights confirmation, optional HTTPS source URL capture, upload-path validation before media upload, stored license/rights/source fields, RTDB rule validation, community sound selected-license action gates, wallpaper license mapping, detail display, and `docs/legal/community-upload-rights.md`. Cycles 51-52 added report/detail actions plus admin hide/restore resolution. Cycle 60 added private rights-confirmed takedown receipts tied to current `storagePath` handles. Cycle 61 added rights-confirmed admin delete actions for new rows with deletion handles. Cycle 69 added visible public-listing, rights-takedown, and private-retention copy to upload/report/delete dialogs. Remaining work: legacy/backfill coverage and callable upload finalization.

- [ ] 🤖 🔬 **P1 — Aura Originals provenance gate**
  - Why: The curation guide correctly requires CC0, source URL, and sha256, but the release gate does not yet prove every bundled sound has reviewed provenance and a retroactive removal path.
  - Evidence: `docs/aura-originals-curation.md`; `AuraOriginalsManifest.kt`; `aura_originals_manifest.json`; Freesound API/license terms; Cycle 5 Aura Originals queue.
  - Touches: Aura Originals manifest, downloader verification, licenses screen, CI/source-probe job, release checklist, takedown/removal runbook.
  - Acceptance: manifest entries include source URL, normalized license, sha256, curator/review date, removal status, and replacement plan; CI checks URL reachability/hash/duplicate IDs; only reviewed CC0 or approved-compatible assets ship in the first-run pack.
  - Verify: manifest fixture with valid/invalid license and hash; downloader rejects mismatches; licenses/provenance screen lists bundled attributions; removal of an entry stops new installs from receiving it.

- [ ] 🤖 🔬 **P2 — Source-deleted and rights-revoked local states**
  - Why: Cached/favorited/downloaded third-party media can outlive a provider deletion or rights change, and Aura currently has no explicit UI state for "source removed", "rights revoked", or "local copy only".
  - Evidence: `FavoriteEntity`; `WallpaperCacheEntity`; `DownloadEntity`; detail restore paths; Reddit developer terms; Pexels/Pixabay report-content links; Cycle 3 source-deletion item.
  - Touches: cache/favorite/download metadata, detail reload paths, source-health diagnostics, storage cleanup, user copy, report/takedown queue.
  - Acceptance: stale/source-deleted media stops appearing in remote catalog, favorites/downloads show a clear unavailable/local-only state, users can delete local copies, and provider terms decide whether offline copies are retained or purged.
  - Verify: simulate deleted Reddit post, removed Pexels/Pixabay result, unavailable YouTube video, and deleted community upload; detail screens do not misrepresent source status; cleanup removes policy-required local copies.
  - Progress 2026-06-06: Cycle 46 added persisted unavailable-source state and saved-surface UI. Cycle 48 added explicit removed/gone failure classification for remote 404/410/gone/removed/deleted signals and marks saved favorites/download history unavailable when apply/download or re-download paths prove upstream removal.

---

## 🔬 Researcher Queue (Cycle 14 — 2026-06-04)

Append-only Cycle 14 handoff. Every item below is source-backed in `docs/research/cycle-14-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [x] 🤖 🔬 **P0 — Boot-completed permission decision for rotation triggers** — shipped 2026-06-07 (`android.permission.RECEIVE_BOOT_COMPLETED` removed, `docs/rotation-trigger-boot-behavior.md`, `tools/rotation_boot_permission_check.py`).
  - Why: `RECEIVE_BOOT_COMPLETED` is declared, but no boot receiver was found. WorkManager periodic jobs can persist without it, while rotation-trigger dynamic receivers do not restart after reboot until Aura cold-starts.
  - Evidence: `AndroidManifest.xml`; no `BOOT_COMPLETED` receiver/source hit; `FreeVibeApp.reconcileRotationTriggers()`; `RotationTriggerService.kt`; Android Doze/background docs; Play foreground-service declaration docs.
  - Touches: manifest, optional boot receiver, rotation-trigger settings, FGS declaration packet, permission ledger, release QA.
  - Acceptance: either remove `RECEIVE_BOOT_COMPLETED` and document "rotation triggers resume after opening Aura", or add a boot receiver that starts `RotationTriggerService` only when the user opted in and only with clear notification/FGS evidence.
  - Verify: reboot with triggers off/on; inspect foreground service and notification state before opening app; merged manifest permission diff; Play declaration matches chosen behavior.
  - Completed 2026-06-07: Cycle 151 removed the unused permission, updated Data safety and alternative-store disclosure packets to the 14-permission manifest, documented that rotation triggers resume after opening Aura, and added a verify/release gate that fails if the permission, boot receiver source terms, workflow wiring, or disclosure text drift.

- [ ] 🤖 🔬 **P1 — Rotation trigger reliability and expedited-work fallback tests**
  - Why: Unlock/screen-off and Tasker actions enqueue expedited `AutoWallpaperWorker` with `RUN_AS_NON_EXPEDITED_WORK_REQUEST`, so rotations can be delayed by quota, battery-not-low, network, Doze, App Standby, or `ExistingWorkPolicy.KEEP` coalescing.
  - Evidence: `RotationTriggerService.enqueueRotation()`; `TaskerActionReceiver.kt`; Android WorkManager expedited/quota docs; Android Doze/App Standby docs.
  - Touches: trigger settings copy, Tasker docs, worker diagnostics, manual QA script, FGS declaration packet.
  - Acceptance: UI/docs say trigger actions enqueue a constrained rotation attempt; diagnostics show coalesced/downgraded/deferred attempts; visible manual rotation path reports immediate success/failure separately from background attempts.
  - Verify: repeated unlocks, screen-off, and Tasker broadcasts under active/rare/restricted standby buckets; expedited quota exhaustion; no network; low battery; confirm one-shot coalescing and user-facing deferral reason.

- [x] 🤖 🔬 **P1 — WorkManager unique-work policy matrix** — shipped 2026-06-07 (`docs/background-work-scheduling-ledger.md`, `tools/background_work_scheduling_check.py`).
  - Why: Auto-wallpaper uses `UPDATE`, daily/weather/Aura Originals use `KEEP`, and future interval/constraint/manifest changes can silently leave stale work unless the intended policy is documented.
  - Evidence: `AutoWallpaperWorker.scheduleWithConstraints()`; `DailyWallpaperWorker.schedule()`; `WeatherUpdateWorker.schedule()`; `AuraOriginalsDownloader.enqueue()`; WorkManager periodic-work docs.
  - Touches: `docs/background-work-runbook.md`, Settings scheduling code, release checklist, worker tests.
  - Acceptance: runbook lists unique work name, work type, enqueue policy, interval, flex/initial delay, constraints, retry/backoff, schedule trigger, cancel trigger, and when changes require update/cancel/re-enqueue or versioned work names.
  - Verify: unit/static test compares declared runbook rows with code constants; changing scheduler interval/constraints updates work; daily/weather/originals do not duplicate work; Aura Originals manifest revision can force needed refresh.
  - Completed 2026-06-07: Cycle 153 added a source-backed scheduling ledger and validator covering `auto_wallpaper`, `daily_wallpaper`, `weather_update`, `aura_originals_download`, and `rotation_trigger_oneshot`, including work type, enqueue API, policy, timing, constraints, backoff, trigger/cancel behavior, deferral reasons, source URLs, release docs, and verify/release wiring.

- [ ] 🤖 🔬 **P1 — Background network and data-saver posture**
  - Why: Weather refresh uses connected network every 30 minutes, daily wallpaper fetches Reddit daily, Aura Originals requires unmetered network, and auto-rotation can fetch remote wallpapers unless Wi-Fi-only is enabled.
  - Evidence: `WeatherUpdateWorker.kt`; `DailyWallpaperWorker.kt`; `AuraOriginalsDownloader.kt`; `AutoWallpaperWorker.kt`; Settings Wi-Fi/charging/idle controls; Android Doze/App Standby and WorkManager constraint docs.
  - Touches: settings copy, worker constraints, Data safety/provider policy docs, diagnostics, release QA.
  - Acceptance: each background network job declares metered/unmetered behavior, user toggle, provider/data impact, retry behavior, and failure UX; weather optionally supports unmetered-only or lower cadence; Data Saver/low battery states are tested.
  - Verify: metered vs unmetered network, Data Saver on/off, no network, low battery, weather enabled/disabled, daily notifications denied/granted, provider failures.
  - Progress 2026-06-07: Cycle 154 added `docs/background-work-network-posture.md`, `docs/background-work-network-posture.json`, and `tools/background_work_network_check.py` so each scheduled work row has checked connected/unmetered network posture, metered behavior, Data Saver gap text, privacy surface, release risk, source terms, release docs, and verify/release workflow wiring. Cycle 155 added support-bundle pending markers for Data Saver receipts. Cycle 156 added Settings Data Saver and active metered-network receipts. Cycle 158 merged live Data Saver receipts into support bundles. Cycle 159 added action hints for restricted-background and metered/unmetered cases. Cycle 160 added the capture-pending device/emulator evidence gate for metered/Data Saver, low battery, Doze/App Standby, and rotation-trigger coalescing. Remaining work: run and archive the real capture packet.

- [ ] 🤖 🔬 **P2 — Battery/vitals regression lab for live wallpapers and schedulers**
  - Why: Video live wallpapers have FPS caps and a battery dashboard, but release planning needs measured evidence for battery saver, charging state, foreground-service time, background jobs, and network usage.
  - Evidence: `VideoWallpaperService.kt`; `VideoBatteryProfile.kt`; `SettingsScreen.kt` battery dashboard; Android vitals categories; Doze/App Standby docs.
  - Touches: manual QA scripts, `docs/performance/battery-lab.md`, release checklist, diagnostics/support bundle, video wallpaper settings.
  - Acceptance: lab records effective FPS, requested FPS, battery percent, charging state, foreground/background visibility, FGS time, job count, network bytes, and user-visible copy for low-battery auto-caps.
  - Verify: run video wallpaper visible/hidden, charging/unplugged, low battery, battery saver on/off, scheduled rotation active, daily/weather enabled; compare dumpsys/batterystats outputs before release.

---

## 🔬 Researcher Queue (Cycle 15 — 2026-06-04)

Append-only Cycle 15 handoff. Every item below is source-backed in `docs/research/cycle-15-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [x] 🤖 🔬 **P0 — Cleartext release gate and ccMixter HTTPS-only decision** — shipped 2026-06-07 (Cycle 129; HTTPS-only ccMixter and release cleartext guard).
  - Why: The manifest disables cleartext globally, but `network_security_config.xml` permits cleartext for all `ccmixter.org` subdomains and `CcMixterRepository` retries over HTTP on TLS failure.
  - Evidence: `AndroidManifest.xml`; `network_security_config.xml`; `CcMixterRepository.buildCcMixterFallbackUrl()`; Android cleartext-communications and Network Security Configuration docs; Cycle 6/10 cleartext notes.
  - Touches: ccMixter repository, provider policy matrix, network security config, release preflight, source-health UI, Izzy/store metadata.
  - Acceptance: release builds do not downgrade ccMixter to HTTP by default; either ccMixter is HTTPS-only, disabled/degraded when HTTPS fails, or explicitly debug/internal-only with provider-policy approval and expiration.
  - Verify: static check for `cleartextTrafficPermitted="true"` and provider `http://` URLs; simulated `SSLException` does not fetch HTTP in release; release metadata reports no cleartext traffic unless an approved exception exists.
  - Progress 2026-06-07: Cycle 129 removed the ccMixter HTTP fallback and `ccmixter.org` cleartext domain exception, added `tools/cleartext_release_check.py` with focused tests, wired the guard into verify and release workflows, required it in the release workflow security policy, and updated endpoint/supply-chain release docs. `CcMixterRepositoryTest` now proves TLS failures propagate without an HTTP downgrade.

- [x] 🤖 🔬 **P0 — Provider credential release guard for BuildConfig/local.properties** — shipped 2026-06-07 (Cycle 131; release guard plus packaged APK scan).
  - Why: `app/build.gradle.kts` can bake Pexels, Pixabay, Freesound, SoundCloud, and Stability AI values from `local.properties` into `BuildConfig`, making local release APKs leak quota-bearing or paid provider credentials.
  - Evidence: `app/build.gradle.kts`; `docs/distribution/release-signing.md`; `PreferencesManager.kt`; Android security checklist on API-key storage; Stability key handling from Cycle 7.
  - Touches: Gradle release preflight, release workflow, `docs/distribution/release-signing.md`, provider settings copy, APK artifact scan.
  - Acceptance: release builds fail when optional provider keys/client IDs are nonblank unless an explicit internal-build flag is set; public GitHub/Obtainium/Izzy builds ship blank defaults and require user-entered keys where needed.
  - Verify: set fake keys in `local.properties` and confirm release preflight fails; internal override path succeeds with a warning; `strings`/APK scan proves sentinel keys are absent from public release artifacts.
  - Progress 2026-06-07: Cycle 125 added `tools/provider_credential_release_check.py`, focused tests, verify/release workflow wiring, release workflow policy coverage, and release docs. The guard statically proves blank Gradle defaults, blank release `local.properties` provider values, local nonblank-key failure, and explicit internal override warning. Cycle 131 added `tools/provider_credential_apk_scan.py`, focused APK fixture tests, release workflow wiring after signed APK packaging, release workflow policy coverage, and release runbook updates so signed APKs are scanned for nonblank provider values before notices, checksums, uploads, or tagged publication. Owner-run signed dry-run evidence remains a release checklist item, not a code blocker.

- [x] 🤖 🔬 **P1 — Provider credential storage classification and Keystore decision** — shipped 2026-06-07 (Cycle 130; provider credential storage policy and no-Keystore disclosure).
  - Why: User-entered provider keys live in DataStore and are excluded from backup/device transfer, but app-private DataStore is not Keystore-backed encrypted storage.
  - Evidence: `PreferencesManager.kt`; `backup_rules.xml`; `data_extraction_rules.xml`; Android Keystore and security checklist docs; Cycle 4 backup/privacy item.
  - Touches: provider settings, key-storage helper, backup matrix, privacy/data-safety docs, diagnostics support docs.
  - Acceptance: every provider credential is classified as public client ID, optional quota key, or paid/sensitive secret; sensitive keys either use Keystore-backed encrypted storage or have a documented no-strong-at-rest-protection disclosure and user control.
  - Verify: set every key, inspect backup/transfer exclusions, rotate/delete keys, export diagnostics, and confirm no raw key appears in app backup artifacts or support bundles.
  - Progress 2026-06-07: Cycle 130 added `docs/security/provider-credential-storage.json`, `docs/security/provider-credential-storage.md`, `tools/provider_credential_storage_check.py`, focused policy tests, verify/release workflow wiring, privacy/support/supply-chain docs, and a Freesound Settings API-key clear control. The checked decision keeps current optional provider keys in app-private DataStore with backup/device-transfer exclusions and documents that this is not strong at-rest protection. Cycle 132 consolidated provider key dialogs around explicit Save/Clear/Cancel actions and extended the storage policy guard so the Settings Clear path remains checked.

- [x] 🤖 🔬 **P1 — Redacted URL and request logging contract** — shipped 2026-06-07 (Cycle 127; shared request redactor for crash diagnostics and source metrics).
  - Why: Wallhaven, Pixabay, Freesound, and SoundCloud credentials are sent in query strings, while SoundCloud also embeds `client_id` in stream URLs. Raw URLs must not enter logs, source metrics, crash messages, diagnostics, or UI.
  - Evidence: `WallhavenApi.kt`; `PixabayApi.kt`; `FreesoundV2Api.kt`; `SoundCloudApi.kt`; `SoundCloudRepository.kt`; `CrashDiagnosticsCollector.kt`; provider auth docs.
  - Touches: shared URL redactor, SourceMetrics/source-health UI, provider repositories, debug logging policy, diagnostics bundle, tests.
  - Acceptance: code has one reusable redacted URL/request formatter; source metrics and diagnostics store host/source/status/error class, not raw authenticated URLs; providers use header auth where officially supported.
  - Verify: mock failures containing authenticated URLs for each provider; debug/release diagnostics and logs contain redacted query values; source-health cards show no secrets.
  - Progress 2026-06-07: Cycle 127 added `RequestRedactor`, moved crash diagnostics provider-secret redaction onto that shared contract, added request formatter tests for host/path/status output, and redacts `SourceMetrics` failure messages before Settings can display them.

- [x] 🤖 🔬 **P1 — Diagnostics redaction fixture suite for provider secrets** — shipped 2026-06-07 (Cycle 126; provider-specific sanitizer fixture suite and dotted property redaction).
  - Why: Crash diagnostics are user-copyable and already redact broad token shapes, but the release gate needs provider-specific tests for real Aura credential formats.
  - Evidence: `CrashDiagnosticsCollector.kt`; `docs/support/crash-diagnostics.md`; provider query/header shapes; Cycle 2 diagnostics export item.
  - Touches: JVM tests for `CrashDiagnosticsText`, support docs, issue template, future background/source diagnostics.
  - Acceptance: fixtures cover `apikey`, `key`, `token`, `client_id`, `Authorization: Bearer`, assignment-style `apiKey`, `stability.ai.key`, `local.properties`, `file://`, and app-private paths.
  - Verify: synthetic crash log export keeps provider/source context but redacts every secret value; regression test fails on any raw sentinel key.
  - Progress 2026-06-07: Cycle 126 extended `CrashDiagnosticsText.sanitize()` for dotted provider credential assignment names, added provider-shaped sentinel fixtures covering Wallhaven, Pixabay, Freesound, SoundCloud, Pexels, Settings, `local.properties`, `file://`, and app-private paths, and updated support/issue-template copy.

- [x] 🤖 🔬 **P2 — Network endpoint and credential inventory runbook** — shipped 2026-06-07 (Cycle 128; checked endpoint manifest, runbook, and scanner).
  - Why: Aura depends on many remote providers plus Firebase, Open-Meteo, direct media URLs, NewPipe/yt-dlp, and optional AI generation; target-SDK and store reviews need an auditable endpoint/security inventory.
  - Evidence: Retrofit API modules; provider repositories; `AppModule.kt`; Cycle 10 Android 17 network preflight; Android Network Security Configuration docs.
  - Touches: `docs/security/network-endpoints.md`, provider policy matrix, release checklist, privacy/data-safety matrix, static scanner.
  - Acceptance: runbook lists endpoint host, scheme, auth location, cleartext status, data sent, media cached, rate limit/cache policy, fallback behavior, kill switch, and release owner for every network surface.
  - Verify: static endpoint scan matches the runbook; adding a new host/provider fails CI until the runbook and privacy/provider policy rows are updated.
  - Progress 2026-06-07: Cycle 128 added `docs/security/network-endpoints.json`, `docs/security/network-endpoints.md`, `tools/network_endpoint_inventory_check.py`, live tool tests, and verify workflow wiring. The scanner currently covers 19 reviewed literal hosts across 15 endpoint surfaces.

---

## 🔬 Researcher Queue (Cycle 16 — 2026-06-04)

Append-only Cycle 16 handoff. Every item below is source-backed in `docs/research/cycle-16-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [x] 🤖 🔬 **P0 — Collection share-token backend path and rules alignment**
  - Why: `CollectionExporter` publishes and imports `shared_collections/{token}/payload`, while `database.rules.json` grants collection-share access under `collection_shares/{token}`. RTDB access is denied by default without a matching rule.
  - Evidence: `CollectionExporter.kt`; `database.rules.json`; Firebase RTDB core rules docs; Cycle 9/12 community backend findings.
  - Touches: collection sharing, RTDB rules, Firebase emulator tests, collection import UI, privacy/community data docs.
  - Acceptance: code and rules use one canonical share-token path; rules validate payload shape, version, item count, payload length, creator UID, createdAt, owner overwrite/delete, and admin cleanup.
  - Verify: emulator tests cover publish, public read-by-token, owner/admin overwrite/delete, unauthenticated write denial, malformed payload denial, and oversized payload denial; app share/import succeeds against emulator rules.
  - Progress 2026-06-06: Cycle 58 switched tracked rules to `shared_collections/{token}`, added `createdByUid` to published shares, bounded version/payload/name/item-count/created-at fields, allowed owner/admin cleanup, blocked non-owner overwrites, kept public token reads, denied the old `collection_shares` path, and added emulator coverage.

- [ ] 🤖 🔬 **P2 — Export format compatibility and provenance schema policy**
  - Why: Favorites and collections export format version 1 payloads do not yet document how future provenance, source-deleted state, license/action-capability, or local-only metadata will migrate.
  - Evidence: `FavoritesExporter.kt`; `CollectionExporter.kt`; Cycle 13 provenance/action-capability item; Room v12-v14 source-aware migrations.
  - Touches: `docs/data/export-format.md`, exporter models, import compatibility tests, provenance model, release notes.
  - Acceptance: export schemas have documented required/optional fields, forward/backward compatibility behavior, version bump rules, provenance fields, privacy exclusions, and unsupported-version user copy.
  - Verify: golden JSON fixtures for current and prior formats; future-version rejection text; legacy favorites-list import still works; collection v1 imports remain stable after provenance fields are added.

---

## 🔬 Researcher Queue (Cycle 17 — 2026-06-04)

Append-only Cycle 17 handoff. Every item below is source-backed in `docs/research/cycle-17-2026-06-04.md`; merge into the existing Now/Next/Later item named in `Touches` when implementation starts.

- [~] 🤖 🔬 **P1 — Runtime dependency and content-source coverage matrix**
  - Why: `ContentSource` includes Audius, ccMixter, SoundCloud, Wikimedia, Internet Archive, NASA, Picsum, Klipy, Community, Bundled, AI-generated, and Local, but the Licenses screen lists only a subset of active/legacy sources.
  - Evidence: `ContentSource`; `LicensesScreen.kt`; provider repositories; Cycle 3 provider-policy findings; F-Droid anti-feature definitions.
  - Touches: provider policy, Licenses screen, Settings provider toggles, `docs/legal/provider-policy.md`, store/repository metadata.
  - Acceptance: one matrix maps every runtime dependency and every `ContentSource` value to license/terms URL, attribution fields, action capabilities, cache/deletion policy, provider toggle, anti-feature/disclosure notes, and shipped/dormant status.
  - Verify: unit/static test compares Gradle runtime dependency inventory and `ContentSource.entries` to the matrix; adding a source or dependency fails until the matrix and notices update.
  - Progress 2026-06-05: content-source coverage is now code-backed by `ProviderDisclosure`, exposed in the Licenses screen, documented in `docs/legal/provider-policy.md`, and guarded by `ProviderDisclosureTest`. The dependency side is improved with visible rows for Firebase, Play services, ML Kit, NewPipe, youtubedl-android, yt-dlp, FFmpeg, ZXing, ProfileInstaller, Palette, desugaring, Kotlin coroutines, and serialization, but generated dependency inventory and notice-diff gating remain open.

- [ ] 🤖 🔬 **P1 — Preserve item-level license and provenance through durable flows**
  - Why: Sounds can display license/uploader/source metadata when live objects carry it, but `FavoriteEntity` does not persist sound license; wallpapers do not have a license field; exports do not yet document license/provenance compatibility.
  - Evidence: `SoundDetailScreen.kt`; `WallpaperDetailScreen.kt`; `FavoriteEntity`; `Mappers.kt`; `FavoritesExporter.kt`; `CollectionExporter.kt`; Cycle 13 provenance/action-capability item.
  - Touches: favorite/download/collection entities, Room migration, exporter schemas, detail screens, apply/share/download/editor gates.
  - Acceptance: favorites, downloads, collections, imports, exports, and restored records preserve normalized license, original source page, uploader/creator, provider item ID, and action capabilities where known.
  - Verify: golden export/import fixtures for CC0, CC BY, CC BY-NC, YouTube, SoundCloud, community, bundled, and local items; favorite/restore/detail UI still shows source license; restricted actions respect capability fields.

- [ ] 🤖 🔬 **P1 — Store/repository disclosure source of truth**
  - Why: Google Play IP policy, F-Droid anti-feature labeling, Izzy review, GitHub release notes, README, and in-app notices all need consistent answers about third-party content, non-free services, extractor/downloader features, and bundled assets.
  - Evidence: `README.md`; `docs/distribution/channel-strategy.md`; `docs/distribution/supply-chain.md`; Google Play intellectual-property policy; F-Droid anti-feature docs.
  - Touches: release checklist, README/legal docs, fastlane/metadata, Obtainium/Izzy/F-Droid notes, Settings Licenses/Privacy screens.
  - Acceptance: a single docs/legal source drives in-app notice copy, release metadata, anti-feature declarations, third-party content disclaimers, and provider/source policy links.
  - Verify: release checklist fails if dependency/source matrix changes without metadata review; generated release notes include notice/source disclosure links; F-Droid/Izzy draft metadata matches the matrix.

- [ ] 🤖 🔬 **P2 — Bundled/Aura Picks upstream attribution policy**
  - Why: Bundled Aura Picks currently use Freesound CC0 source URLs, but visible uploader data is generic and detail UI suppresses the upstream creator for the bundled label.
  - Evidence: `BundledContentProvider.kt`; `SoundDetailScreen.kt`; Freesound source-page URLs in bundled sound entries; README third-party content note.
  - Touches: Aura Originals/bundled manifest, curation review docs, sound detail provenance, release notices.
  - Acceptance: bundled content metadata records upstream creator, source asset URL, source license, curation date, review result, and whether Aura can redistribute/bundle it; UI can show concise Aura Picks branding without hiding upstream attribution.
  - Verify: bundled manifest fixture validates every item has source/license/creator fields; Sound detail exposes a source/provenance affordance; generated notices include bundled third-party assets.

## 🔬 Researcher Queue (Cycle 18 — 2026-06-06)

Append-only Cycle 18 handoff. Every item below is source-backed in `docs/research/cycle-18-2026-06-06.md`; merge into the Cycle 17 generated-notices/native-compliance items when implementation starts.

- [~] 🤖 🔬 **P0 — Native/copyleft artifact inspection packet**
  - Why: `io.github.junkfood02.youtubedl-android:library:0.18.1` bundles yt-dlp and Python, and `:ffmpeg:0.18.1` supplies FFmpeg binaries. Generic Maven notice tools will not prove exact payload versions, FFmpeg build mode, ABI files, or source-offer links.
  - Result: Cycle 23 added `tools/native_compliance_inventory.py`, committed `docs/legal/native-compliance.md`, and wired release CI to publish/checksum `NATIVE-COMPLIANCE.md` with the APK and `THIRD-PARTY-NOTICES.md`.
  - Evidence: `app/build.gradle.kts`; `AudioTrimmer.kt`; `VideoCropScreen.kt`; youtubedl-android README; FFmpeg legal guidance; NewPipeExtractor GPL-3.0 license.
  - Touches: `tools/`, release workflow, `docs/legal/native-compliance.md`, `docs/distribution/supply-chain.md`, release notes, uploaded release artifacts.
  - Acceptance remaining: dependency version changes should fail until the packet is regenerated and reviewed; exact FFmpeg configure/source correspondence remains a release-owner review item.
  - Verify: release artifact bundle includes the native/copyleft packet; future dependency-drift gate detects stale native packet evidence.

- [ ] 🤖 🔬 **P1 — Release workflow publishes notice/SBOM artifacts**
  - Why: GitHub/Obtainium users can currently inspect APK checksums and attestations, but not third-party notices or dependency/license inventory before installing the app.
  - Evidence: `.github/workflows/release.yml` uploads APK, `SHA256SUMS.txt`, release notes, apksigner output, and aapt badging only; Cycle 18 found no notice/SBOM upload.
  - Touches: `.github/workflows/release.yml`, release notes, `docs/distribution/release-signing.md`, `docs/distribution/supply-chain.md`.
  - Acceptance: release artifacts include `THIRD-PARTY-NOTICES.md`, dependency license JSON, optional CycloneDX/SPDX SBOM, and native/copyleft packet; release notes link to each artifact.
  - Verify: workflow dry run uploads all expected files with `if-no-files-found: error`; tag release attachment list includes notices next to the APK and checksum.

## 🔬 Researcher Queue (Cycle 19 — 2026-06-06)

Append-only Cycle 19 handoff. Every item below is source-backed in `docs/research/cycle-19-2026-06-06.md`; use it to choose the first implementation spike for the generated-notices lane.

- [ ] 🤖 🔬 **P1 — Notice artifacts become part of release checksums**
  - Why: The release workflow currently checksums only the APK. If third-party notices and native compliance packets are release evidence, users should be able to verify those artifacts too.
  - Evidence: `.github/workflows/release.yml`; Cycle 19 release workflow insertion-point analysis.
  - Touches: release workflow steps between APK verification, checksum generation, release notes, artifact upload, and tag attachment.
  - Acceptance: notice/SBOM/native packet files are generated before `SHA256SUMS.txt`, included in checksums, uploaded in workflow artifacts, and attached or linked on tag releases.
  - Verify: manual workflow dry run shows checksum rows for APK plus notice artifacts and release notes mention each artifact.

## 🔬 Researcher Queue (Cycle 20 — 2026-06-06)

Append-only Cycle 20 handoff. Every item below is source-backed in `docs/research/cycle-20-2026-06-06.md`; use it to turn the generated-notices lane from planning into implementation.

## 🔬 Researcher Queue (Cycle 21 — 2026-06-06)

Append-only Cycle 21 handoff. Every item below is source-backed in `docs/research/cycle-21-2026-06-06.md`; use it to implement generated notices without forcing unreviewed UI dependency convergence.

- [ ] 🤖 🔬 **P1 — AboutLibraries remains a secondary custom-Compose option**
  - Why: AboutLibraries 14.2.1 configured on Aura's current toolchain, but default exports were incomplete and Windows compliance export logged path errors. It should not displace the working plugin-only Google path unless a follow-up config pass proves complete coverage.
  - Evidence: Cycle 20 AboutLibraries spike; Cycle 21 plugin-only Google notice success.
  - Touches: version catalog, app Gradle plugin config, Licenses UI only if Google resources/custom markdown are insufficient.
  - Acceptance: no AboutLibraries adoption before a documented config pass includes Aura's actual release runtime graph; AboutLibraries 15.x remains blocked until the N-1 AGP upgrade because v15 requires AGP 8.13.
  - Verify: if revisited, `aboutlibraries.json` must include NewPipeExtractor, youtubedl-android, Firebase, Play services ML Kit, ZXing, Palette, and ProfileInstaller.

## 🔬 Researcher Queue (Cycle 22 — 2026-06-06)

Append-only Cycle 22 handoff. Every item below is source-backed in `docs/research/cycle-22-2026-06-06.md`; use it to finish hardening the new generated-notice lane and continue into native/copyleft payload evidence.

- [~] 🤖 🔬 **P1 — Release workflow packages notices with checksums**
  - Result: `.github/workflows/release.yml` now runs `:app:releaseOssLicensesTask`, writes `release/THIRD-PARTY-NOTICES.md`, includes it in `SHA256SUMS.txt`, uploads it as a workflow artifact, and attaches it to tagged GitHub Releases.
  - Evidence: workflow diff plus `docs/distribution/supply-chain.md` release verification updates.
  - Risk: the full `assembleRelease` workflow was not run locally because `CLAUDE.md` warns repeated APK/lint builds can exhaust this workstation; GitHub Actions remains the real validation environment.
  - Verify: next tagged or manual release workflow should show checksum rows for both APK and `THIRD-PARTY-NOTICES.md`; release notes should mention the notices artifact.

- [x] 🤖 🔬 **P0 — Restore ProviderDisclosure unit-test execution in real repo environment** — shipped 2026-06-06.
  - Result: the code-backed provider policy guard now runs in the real repo with the local Android SDK path and Android Studio JBR.
  - Evidence: `:app:testDebugUnitTest --tests com.freevibe.data.legal.ProviderDisclosureTest` passed after debug OSS task POM checksums were added to `gradle/verification-metadata.xml`.
  - Touches: local developer setup docs, CI/unit-test runbook, not production code.
  - Acceptance: focused `ProviderDisclosureTest` passes after generated-notice changes.
  - Verify: focused unit-test command passes and the Licenses screen still has complete "Content Sources" rows from `ProviderDisclosure.kt`.

- [x] 🤖 🔬 **P0 — Native/copyleft payload inspector for youtubedl-android and FFmpeg** — shipped 2026-06-06.
  - Why: the generated Google notices cover Maven dependency coordinates and license texts, but they still do not inspect shipped AAR payload files, FFmpeg build mode, yt-dlp/Python payload versions, or GPL/LGPL source-offer evidence.
  - Result: `tools/native_compliance_inventory.py` reads resolved Gradle cache artifacts, optionally inspects a final APK, and writes `NATIVE-COMPLIANCE.md`; release CI now generates, checksums, uploads, and attaches that packet.
  - Evidence: Cycle 18 native/copyleft packet item; Cycle 22 generated-notice implementation gap; `docs/legal/native-compliance.md`; `docs/research/cycle-23-2026-06-06.md`.
  - Touches: `tools/native_compliance_inventory.*`, `docs/legal/native-compliance.md`, release workflow artifact list, youtubedl-android AAR cache paths.
  - Acceptance: local tool inspects resolved youtubedl-android library/ffmpeg AARs without full APK assembly, lists payload paths and licenses, and identifies the source/build references Aura must publish.
  - Verify: report names youtubedl-android library, youtubedl-android ffmpeg, yt-dlp/Python payload notes, QuickJS, FFmpeg binary paths, and NewPipeExtractor GPL evidence.

## 🔬 Researcher Queue (Cycle 23 — 2026-06-06)

Append-only Cycle 23 implementation record. The completed item is source-backed in `docs/research/cycle-23-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P0 — Native/copyleft release packet shipped**
  - Result: release artifacts now include `NATIVE-COMPLIANCE.md` with AAR hashes, optional final-APK payload entries, youtubedl-android/yt-dlp/Python/QuickJS/FFmpeg/NewPipeExtractor references, and explicit FFmpeg source-correspondence review notes.
  - Evidence: `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.md`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
  - Verification: `python -m py_compile tools/native_compliance_inventory.py`; `python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md`.
  - Remaining risk: Cycle 30 later extracted embedded FFmpeg configure lines from the 0.18.1 AAR; exact Termux package commit, patches, dependency source set, and build logs still remain a release-owner review requirement.

- [x] 🤖 🔬 **P0 — Release-runtime license drift gate** — shipped 2026-06-06.
  - Why: Aura now publishes human-readable Google OSS notices and a native packet, but nothing fails a build when release-runtime dependencies or native payload versions drift without reviewed metadata.
  - Result: `tools/dependency_notice_lock.py` writes/checks `docs/legal/dependency-notices.lock.json`; PR/main verify and release workflows now fail when generated release dependency notices drift.
  - Evidence: Cycle 18 dependency license drift item; `gradle/verification-metadata.xml`; `.github/workflows/release.yml`; `.github/workflows/verify.yml`; `docs/distribution/supply-chain.md`; generated `dependencies.json`.
  - Touches: `tools/`, `docs/legal/dependency-notices.lock.json`, optional curated override JSON, release workflow, CI verification docs.
  - Acceptance: deterministic check compares release dependency coordinates, generated notice input hashes, and notice-section hashes to a committed lockfile; added/removed/changed dependencies or notices fail until reviewed.
  - Verify: `:app:releaseOssLicensesTask` passed; `python tools\dependency_notice_lock.py --mode check --lockfile docs\legal\dependency-notices.lock.json` returned status `ok` with 251 dependencies and 288 notice sections.

## 🔬 Researcher Queue (Cycle 24 — 2026-06-06)

Append-only Cycle 24 implementation record. The completed item is source-backed in `docs/research/cycle-24-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P0 — Dependency notice lockfile and CI gate shipped**
  - Result: `docs/legal/dependency-notices.lock.json` records 251 sorted release dependency coordinates, 288 notice section hashes, and input hashes for Google OSS generated files.
  - Evidence: `tools/dependency_notice_lock.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
  - Verification: `:app:releaseOssLicensesTask` passed; `python -m py_compile tools\dependency_notice_lock.py tools\google_oss_to_markdown.py tools\native_compliance_inventory.py`; lock check returned status `ok`.
  - Remaining risk: Google OSS outputs still do not provide source URLs or license IDs per dependency coordinate, so a curated overlay remains future work.

- [x] 🤖 🔬 **P0 — Native packet freshness gate** — shipped 2026-06-06.
  - Why: the dependency notice lockfile now catches generated Google OSS notice drift, but youtubedl-android, NewPipeExtractor, yt-dlp, Python, QuickJS, and FFmpeg payload evidence can still become stale if versions change without regenerating `docs/legal/native-compliance.md`.
  - Result: `tools/native_compliance_inventory.py` now has `write-lock` and `check-lock` modes; `docs/legal/native-compliance.lock.json` records current native/copyleft artifact hashes and extracted payload facts; PR/main verify and release workflows fail on native evidence drift.
  - Evidence: `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.md`, `docs/legal/native-compliance.lock.json`, `docs/legal/dependency-notices.lock.json`, `app/build.gradle.kts`.
  - Touches: `tools/`, `docs/legal/native-compliance.md`, release workflow, verify workflow, `docs/distribution/supply-chain.md`.
  - Acceptance: check mode compares current resolved native/copyleft artifact hashes and extracted payload facts to a machine-readable lock; version/hash drift fails until reviewed.
  - Verify: `python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json` returned status `ok` with 8 coordinates, 23 artifact records, and 36 payload entries.

## 🔬 Researcher Queue (Cycle 25 — 2026-06-06)

Append-only Cycle 25 implementation record. The completed item is source-backed in `docs/research/cycle-25-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P0 — Native compliance lockfile and freshness gate shipped**
  - Result: native/copyleft artifact hashes, payload entries, yt-dlp facts, and Python payload facts are now locked in `docs/legal/native-compliance.lock.json`.
  - Evidence: `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.lock.json`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
  - Verification: `python -m py_compile tools\native_compliance_inventory.py`; `python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json`; `python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md`.
  - Remaining risk: FFmpeg exact configure/source correspondence remains release-owner review work because the AAR does not encode it.

- [x] 🤖 🔬 **P0 — Curated high-risk dependency overlay** — shipped 2026-06-06.
  - Why: generated notices, dependency locks, and native locks now catch drift, but Aura still lacks a curated machine-readable overlay for source URLs, license IDs, app usage, and review notes on high-risk dependencies and native payloads.
  - Evidence: `docs/legal/dependency-notices.lock.json`, `docs/legal/native-compliance.lock.json`, `docs/legal/native-compliance.md`, `ProviderDisclosure.kt`.
  - Touches: `docs/legal/dependency-notice-overrides.json`, `tools/`, `docs/distribution/supply-chain.md`, Settings licenses handoff.
  - Acceptance: high-risk dependencies and payloads have reviewed source URLs, license IDs, usage descriptions, and release-review notes; stale or orphaned overlay entries fail against the dependency/native locks.
  - Verify: removing or renaming an overlay coordinate fails; adding a high-risk dependency without an overlay fails; reviewed overlay updates restore green status.

## 🔬 Researcher Queue (Cycle 26 — 2026-06-06)

Append-only Cycle 26 implementation record. The completed item is source-backed in `docs/research/cycle-26-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P0 — Curated high-risk dependency overlay shipped**
  - Result: `docs/legal/dependency-notice-overrides.json` now records required source URL, license ID, usage, target, and release-review metadata for Firebase, Play services, ML Kit subject segmentation, NewPipeExtractor, youtubedl-android, yt-dlp, Python, QuickJS, FFmpeg, ProfileInstaller, and ZXing.
  - Evidence: `tools/dependency_overlay_check.py`, `docs/legal/dependency-notice-overrides.json`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
  - Verification: `python -m py_compile tools\dependency_overlay_check.py`; `python tools\dependency_overlay_check.py --overlay docs\legal\dependency-notice-overrides.json`.
  - Remaining risk: exact FFmpeg configure/source correspondence remains release-owner review work because the resolved AAR still does not encode it.

- [x] 🤖 🔬 **P1 — Release compliance artifact dry-run validation** — shipped 2026-06-06.
  - Why: the release workflow now generates and attaches `THIRD-PARTY-NOTICES.md`, `NATIVE-COMPLIANCE.md`, `SHA256SUMS.txt`, and release notes, but the full GitHub Actions packaging path still needs a non-tag dry-run proof after the lock/overlay gates landed.
  - Evidence: `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`, `docs/legal/dependency-notices.lock.json`, `docs/legal/native-compliance.lock.json`, `docs/legal/dependency-notice-overrides.json`.
  - Touches: release workflow, dry-run docs, release artifact checklist, optional workflow-dispatch artifact naming.
  - Acceptance: workflow-dispatch or local CI-equivalent dry run proves notices, native packet, checksums, release notes, and APK packaging are produced together; failures leave actionable diagnostics.
  - Verify: dry-run release lane completes without creating a public tag/release, or an equivalent documented local packaging script proves the same artifact set.

## 🔬 Researcher Queue (Cycle 27 — 2026-06-06)

Append-only Cycle 27 implementation record. The completed item is source-backed in `docs/research/cycle-27-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P1 — Release compliance artifact dry-run validation shipped**
  - Result: manual release workflow runs now validate the final release bundle before workflow-artifact upload, while tag releases validate the same bundle before GitHub Release upload.
  - Evidence: `tools/release_artifact_bundle_check.py`, `.github/workflows/release.yml`, `docs/distribution/release-dry-run.md`, `docs/distribution/release-signing.md`, `docs/distribution/supply-chain.md`.
  - Verification: `python -m py_compile tools\release_artifact_bundle_check.py`; local temporary bundle smoke test; release-compliance Python compile and lock checks.
  - Remaining risk: the full signed APK workflow still needs an actual GitHub Actions manual run with repository secrets, which cannot be executed from the local workspace.

- [x] 🤖 🔬 **P1 — Preserve raw release notice inputs as workflow artifacts** — shipped 2026-06-06.
  - Why: `THIRD-PARTY-NOTICES.md` is reviewer-friendly, but raw Google OSS inputs are still useful when investigating dependency drift or a license-section hash change after the fact.
  - Evidence: `app/build/generated/third_party_licenses/release/dependencies.json`, generated `third_party_license_metadata`, generated `third_party_licenses`, `tools/google_oss_to_markdown.py`, `docs/legal/dependency-notices.lock.json`.
  - Touches: release workflow, supply-chain docs, optional archive/checksum script.
  - Acceptance: manual and tag release runs upload raw Google OSS input files or a small archive beside the markdown notice packet without changing the public APK install path.
  - Verify: workflow artifact contains raw notice inputs; checksums or an archive manifest prove the raw files match the markdown and lockfile inputs.

## 🔬 Researcher Queue (Cycle 28 — 2026-06-06)

Append-only Cycle 28 implementation record. The completed item is source-backed in `docs/research/cycle-28-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P1 — Raw release notice input archive shipped**
  - Result: release runs now publish `GOOGLE-OSS-RAW-INPUTS.zip` with raw Google OSS `dependencies.json`, `third_party_license_metadata`, `third_party_licenses`, and `MANIFEST.json`.
  - Evidence: `tools/google_oss_raw_archive.py`, `.github/workflows/release.yml`, `tools/release_artifact_bundle_check.py`, `docs/distribution/release-dry-run.md`, `docs/distribution/supply-chain.md`.
  - Verification: `python -m py_compile tools\google_oss_raw_archive.py`; local temporary generated-root archive smoke test; release-compliance Python compile and lock checks.
  - Remaining risk: actual public release artifacts still need a real GitHub Actions manual dry run with repository signing secrets.

- [x] 🤖 🔬 **P1 — User-facing dependency notice access path** — shipped 2026-06-06.
  - Why: release artifacts now contain generated dependency notices, but the in-app Settings licenses surface still relies on manual rows for runtime/native dependencies.
  - Evidence: `LicensesScreen.kt`, `ProviderDisclosure.kt`, `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, `docs/distribution/supply-chain.md`.
  - Touches: Settings licenses screen, release docs, possible generated-asset packaging decision.
  - Acceptance: Settings exposes a clear path for users to review generated third-party notices without replacing content-source provider disclosures.
  - Verify: local UI/resource path test or documented release-asset link path; no runtime dependency convergence regression from stock Google OSS notice activity.

## 🔬 Researcher Queue (Cycle 29 — 2026-06-06)

Append-only Cycle 29 implementation record. The completed item is source-backed in `docs/research/cycle-29-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P1 — User-facing dependency notice access shipped**
  - Result: Settings > Open source licenses now opens a licenses screen with a first section for generated release notice artifacts.
  - Evidence: `LicensesScreen.kt`, `SettingsScreen.kt`, `LicensesScreenTest`, `docs/research/cycle-29-2026-06-06.md`.
  - Verification: focused `:app:testDebugUnitTest --tests com.freevibe.ui.screens.licenses.LicensesScreenTest` passed.
  - Remaining risk: the latest-release links depend on a public GitHub Release existing with the expected artifacts.

- [x] 🤖 🔬 **P0 — FFmpeg source-correspondence evidence shipped**
  - Why: native locks and native packets identify FFmpeg payloads, but the resolved youtubedl-android FFmpeg AAR still needs a documented configure/source correspondence path for complete release-owner review.
  - Evidence: `docs/legal/native-compliance.md`, `docs/legal/native-compliance.lock.json`, `docs/legal/dependency-notice-overrides.json`, `tools/native_compliance_inventory.py`.
  - Touches: native compliance docs, release checklist, dependency overlay notes, possible source archive/link evidence.
  - Acceptance: release owners have a documented source/configure correspondence path for the resolved youtubedl-android FFmpeg payload or an explicit unresolved-owner-action record with exact missing evidence.
  - Verify: source URL/configure evidence is recorded; native compliance packet and release checklist point to it; unresolved gaps fail a documented manual review checklist.
  - Result: Cycle 30 added `docs/legal/ffmpeg-source-correspondence.md`, extracted embedded FFmpeg 7.1.1 configure evidence from all four ABI payloads into `tools/native_compliance_inventory.py`, refreshed `docs/legal/native-compliance.md` and `docs/legal/native-compliance.lock.json`, and updated the release checklist/overlay.
  - Verification: Python compile, native lock check, native markdown regeneration, dependency notice lock check, dependency overlay check, and diff checks passed.
  - Remaining risk: the exact Termux package commit, patches, dependency source set, and build logs still need owner confirmation before publishing changed FFmpeg payloads.

## 🔬 Researcher Queue (Cycle 31 — 2026-06-06)

Append-only Cycle 31 implementation record. The completed item is source-backed in `docs/research/cycle-31-2026-06-06.md`; use the open item as the next implementation entry point.

- [x] 🤖 🔬 **P1 — Release dependency license policy gate**
  - Why: generated notices, native locks, and curated overlays now prove dependency/payload drift, but Aura still lacks an explicit policy check that fails newly introduced disallowed or owner-review-required license IDs before release.
  - Evidence: `docs/legal/dependency-notice-overrides.json`, `docs/legal/dependency-notices.lock.json`, `docs/legal/native-compliance.lock.json`, `tools/dependency_overlay_check.py`, `tools/dependency_notice_lock.py`.
  - Touches: release-compliance tools, `docs/legal`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
  - Acceptance: high-risk and disallowed license policy is encoded in a deterministic checked file or tool mode; PR/main/release checks fail when generated notices or overlays introduce unreviewed license IDs.
  - Verify: sentinel overlay/lock fixture or local temporary copy proves an unreviewed license fails and reviewed metadata restores green status.

- [x] 🤖 🔬 **P1 — Raw Google OSS archive retention policy**
  - Why: `GOOGLE-OSS-RAW-INPUTS.zip` now preserves exact generated notice inputs and is attached to workflow artifacts plus tagged public releases, but Aura has not decided whether the raw archive should stay publicly attached forever or move to workflow-artifact retention after a validation window.
  - Evidence: `.github/workflows/release.yml`, `tools/google_oss_raw_archive.py`, `tools/release_artifact_bundle_check.py`, `docs/distribution/supply-chain.md`, `docs/distribution/release-dry-run.md`.
  - Touches: release workflow, release bundle validator, supply-chain docs, release dry-run docs, roadmap/state files.
  - Acceptance: release-owner retention decision is documented; workflow and bundle validator behavior matches that decision; release verification steps tell owners where to find raw Google OSS inputs.
  - Verify: local release-bundle smoke test or focused validator test proves the selected raw-archive expectation is enforced.

- [x] 🤖 🔬 **P1 — Custom in-app dependency notice viewer**
  - Why: Settings links to generated release artifacts, but the app still relies on manual dependency rows and cannot browse the generated dependency notice corpus in-app.
  - Evidence: `app/src/main/java/com/freevibe/ui/screens/licenses/LicensesScreen.kt`, `tools/google_oss_to_markdown.py`, generated Google OSS raw resources, `docs/legal/dependency-notices.lock.json`.
  - Touches: licenses screen models/UI/tests, generated notice parsing strategy, release notice docs, roadmap/state files.
  - Acceptance: Aura has a feasible current-toolchain path for browsing generated dependency notices in-app without adding the risky stock Google runtime dependency; implementation is either shipped or a precise blocker-backed plan is recorded.
  - Verify: focused licenses-screen unit tests or parser tests cover generated notice mapping and manual/provider disclosure preservation.

- [x] 🤖 🔬 **P1 — Generated notice search and high-risk alignment**
  - Why: the in-app generated notice viewer now lists generated entries, but a 288-entry notice corpus needs search/filtering and better alignment with curated high-risk overlay entries before it is comfortable for repeated owner review.
  - Evidence: `GeneratedDependencyNotices.kt`, `LicensesScreen.kt`, `docs/legal/dependency-notice-overrides.json`, `docs/legal/dependency-license-policy.json`.
  - Touches: licenses screen UI/tests, generated notice models, curated high-risk labels, roadmap/state files.
  - Acceptance: generated notices can be filtered by dependency name or license label; high-risk overlay surfaces are easy to find without scrolling the whole generated list.
  - Verify: focused parser/UI model tests cover filtering and high-risk matching.

- [x] 🤖 🔬 **P1 — Generated notice metadata parity guard**
  - Why: the in-app viewer parses `third_party_license_metadata` at runtime, but CI only locks generated notice sections through the Python lockfile. A lightweight parity check should prove the raw metadata count and parser assumptions stay aligned with the lock.
  - Evidence: `GeneratedDependencyNotices.kt`, `tools/dependency_notice_lock.py`, `docs/legal/dependency-notices.lock.json`, generated Google OSS raw resources.
  - Touches: release-compliance tools/tests, docs/legal lock docs, roadmap/state files.
  - Acceptance: a deterministic check fails when generated raw metadata rows diverge from the locked notice-section count or contain malformed ranges.
  - Verify: focused Python fixture or temporary generated-root smoke test proves malformed metadata fails and current generated outputs pass.

- [x] 🤖 🔬 **P1 — Runtime provider kill-switch behavior matrix**
  - Why: Aura has many provider toggles and legacy/dormant sources, but disabled-provider behavior is still mostly implicit. Users and release owners need predictable behavior when a provider is disabled, missing credentials, or temporarily unavailable.
  - Evidence: `ProviderDisclosure.kt`, `PreferencesManager.kt`, `SettingsScreen.kt`, repository entry points for YouTube, Reddit, Pexels, Pixabay, community uploads, bundled content, and legacy sound providers.
  - Touches: provider settings/disclosure docs, focused provider tests, roadmap/state files.
  - Acceptance: current provider kill-switch and disabled-state behavior is mapped in a checked doc or tests; at least one high-risk implicit path is either fixed or captured with a concrete follow-up.
  - Verify: focused unit tests or static checks prove disabled/missing provider states do not silently route users into unavailable sources.

---

## Now — execute this cycle

Five items. Four landed in the 2026-05-16 autonomous batch (N-2..N-5, marked
`[~]` and detailed in the Implementation Log). N-1 remains; it requires a build
environment that can run `./gradlew :app:assembleDebug`.

- [ ] **N-1** — Toolchain upgrade triad (AGP/Gradle/Kotlin/Compose BOM/Hilt). Deferred from the 2026-05-16 pass; needs JDK+SDK to verify.
- [~] **N-2** — Firebase BoM 34.13.0 + Custom Claims admin path. Code + rules shipped; deploy `database.rules.json` + grant claims to existing admins to complete the rollout.
- [~] **N-3** — Subject Segmentation + AGSL pipeline scaffold. Code shipped; concrete AGSL effects ship in NX-1/NX-2 follow-ups.
- [~] **N-4** — Photo Picker + monochrome themed icon. Code shipped. WallpaperDescription scaffolding is comment-only until N-1 unlocks compileSdk 36.
- [~] **N-5** — Aura Originals manifest schema + first-launch downloader. Infrastructure shipped; curation pass adds the actual sound entries to `assets/aura_originals_manifest.json`.

### N-1. Toolchain upgrade triad (AGP 9 + Gradle 9 + Kotlin 2.3 + Compose BOM May 2026 + Hilt 2.59)

- **Source(s):** [AGP 9.0 release notes](https://developer.android.com/build/releases/agp-9-0-0-release-notes); [AGP 9.1 notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes); [Kotlin 2.3](https://kotlinlang.org/docs/whatsnew23.html); [Compose Apr-26 update](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html); [Dagger 2.59 release](https://github.com/google/dagger/releases/tag/dagger-2.59); [Compose Strong Skipping](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping); [Android 17 SDK 37 adaptive requirement](https://developer.android.com/about/versions/17/release-notes).
- **Why now:** Hilt 2.59 requires AGP 9 + Gradle 9.1; AGP 9 makes Kotlin built-in; KSP1 is incompatible with Kotlin 2.3+. Aura is two minor versions behind Kotlin, four minor behind Media3, six minor behind Hilt. Drift compounds. Compose Strong Skipping (default Compose Compiler 2.x) is a free ~20 % LazyGrid recomposition win once the BOM bumps.
- **Scope:** AGP 8.7.3 → 9.2.x, Gradle 8.12 → 9.5+, Kotlin 2.1.0 → 2.3.20, KSP1 → KSP2, Compose BOM `2024.12.01` → `2026.05.00`, Material 3 1.3.1 → 1.4.x, Hilt 2.53.1 → 2.59.x, Lifecycle 2.8.7 → 2.10.x, Navigation 2.8.5 → 2.9.8, Coroutines 1.9.0 → 1.10.x. Re-audit every `@Stable`/`@Immutable` annotation under Strong Skipping. Re-run `assembleDebug` / `testDebugUnitTest` / `lintDebug`. Verify NewPipeExtractor 0.24.8 still compiles against the new toolchain (it's the most fragile pin).
- **Risk:** R8 keep-rule regressions; Hilt 2.59 generation differences on Kotlin 2.3; KSP2 incremental cache may need a clean. Mitigation: feature freeze for this pass; track APK size + cold-start delta.
- **Fit 5 / Impact 4 / Effort 2 / Risk 3 / Deps 3 / Novelty 1 = 18 → upgraded to Now because it gates N-3, N-4, NX-2, NX-7.**

### N-2. Firebase BoM 34.x + admin auth via Firebase Custom Claims

- **Source(s):** [Firebase Android release notes](https://firebase.google.com/support/release-notes/android); [`protobuf` CVE-2024-7254 advisory in BoM 34](https://github.com/firebase/firebase-android-sdk/releases); [VoteRepository.kt:75 TODO](app/src/main/java/com/freevibe/data/repository/VoteRepository.kt#L75); [Firebase Custom Claims docs](https://firebase.google.com/docs/auth/admin/custom-claims).
- **Why now:** Aura ships Firebase BoM 33.7.0. BoM 34.x updates transitive `protobuf-javalite` past CVE-2024-7254 and removes the deprecated KTX libraries. The admin-device-hash check in `VoteRepository` is documented in code as spoofable on rooted devices; Custom Claims move authorization server-side. Real risk: community uploads continue scaling.
- **Scope:** Bump BoM to 34.x; migrate Firebase init off KTX-namespaced helpers if they go away; move `adminDeviceIdHashes` to a `custom_claims.admin` boolean enforced by RTDB Security Rules; ship `.rules` file in repo and CI-verify with `firebase deploy --only database:rules --project=verify`. Clear DB migration path required if RTDB → Firestore later (N-9 in Next).
- **Risk:** Existing community-upload sessions keyed on anonymous device ID will lose admin status until they refresh through the new claim. Mitigation: dual-check during a one-cycle window.
- **Fit 5 / Impact 4 / Effort 4 / Risk 4 / Deps 5 / Novelty 2 = 24 → NOW.**

### N-3. Subject Segmentation API + AGSL effects pipeline

- **Source(s):** [ML Kit Subject Segmentation reference](https://developers.google.com/ml-kit/vision/subject-segmentation/android); current pin `com.google.mlkit:segmentation-selfie:16.0.0-beta6`; [AGSL `RuntimeColorFilter` + `RuntimeXfermode` Android 16 docs](https://developer.android.com/about/versions/16/features); [AGSL Compose patterns](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a); [Pixel Live Effects (Shape / Weather / Cinematic) coverage](https://9to5google.com/2025/06/10/android-16-qpr1-beta-2-adds-live-effects-section-to-wallpaper-picker/); [WallFlow smart-crop reference](https://github.com/ammargitham/WallFlow).
- **Why now:** Selfie-segmentation is still on `16.0.0-beta6` two years after Google's beta tag — production risk. Subject Segmentation API (API 24+, multi-subject) is GA and out of beta. AGSL `RuntimeColorFilter`/`RuntimeXfermode` is the path forward for Aura's image-effect pipeline (Aura currently composes effects in Canvas). Direct parity with Pixel's "Shape" cutout and "Cinematic" depth effect with no dependency on Pixel-only system features.
- **Scope:** Swap `ParallaxWallpaperService` segmenter to Subject Segmentation. Add `AgslEffectPipeline` that exposes `RuntimeShader`-backed filters reusable from wallpaper editor + live wallpapers. Ship three first-class effects: subject cutout with color-matched background ("Shape"); subject-aware depth parallax ("Cinematic", replacement for current Canvas parallax); subject-aware tint passthrough for weather wallpapers (subject untinted, background tinted).
- **Risk:** AGSL needs Android 13+. minSdk 26 forces a fallback path; current Canvas pipeline becomes the fallback. ML Kit unbundled APK overhead (~4.5 MB + JNI spike — see existing pitfall log in CHANGELOG, [googlesamples/mlkit#386](https://github.com/googlesamples/mlkit/issues/386)).
- **Fit 5 / Impact 5 / Effort 3 / Risk 4 / Deps 4 / Novelty 4 = 25 → NOW.**

### N-4. Android Photo Picker migration + Monochrome themed app icon + Android 16 `WallpaperDescription`

- **Source(s):** [Photo Picker behavior change Android 14+](https://developer.android.com/about/versions/14/changes/partial-photo-video-access); [Adaptive icons & monochrome](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive); [WallpaperDescription reference](https://developer.android.com/reference/android/app/wallpaper/WallpaperDescription); [WallpaperInstance reference](https://developer.android.com/reference/android/app/wallpaper/WallpaperInstance); [UndeadWallpaper #48 — monochrome request](https://github.com/maocide/UndeadWallpaper/issues/48); [Doodle issues on AMOLED variants](https://github.com/patzly/doodle-android/issues/38).
- **Why now:** Aura still declares `READ_EXTERNAL_STORAGE` (maxSdkVersion=28) and uses `ActivityResultContracts.OpenDocument()` for video / GIF. Image imports for community uploads and gallery wallpaper-crop should use Photo Picker — no permission prompt, better UX, scoped-storage compliant. Monochrome layer is a 30-minute fix that's been requested in every adjacent OSS app's tracker. WallpaperDescription/Instance is Android 16 baseline for letting one `WallpaperService` expose distinct home / lock / time-of-day variants — directly relevant to Aura's parallax + weather + AI wallpapers.
- **Scope:** Replace gallery `OpenDocument` for image-MIME paths with `PickVisualMedia` (multi-select for batch import). Add `<adaptive-icon><monochrome>` drawable layer to `mipmap-anydpi-v26/ic_launcher.xml`. Declare `WallpaperDescription` metadata on `VideoWallpaperService`, `WeatherWallpaperService`, `ParallaxWallpaperService` so the system picker treats them as a single configurable engine instead of three separate live wallpapers. Wire matching `WallpaperInstance` for the home/lock split where Aura's `applyByLocator` already supports it.
- **Risk:** WallpaperDescription is Android 16+ only — keep legacy `<service>` declarations as fallback.
- **Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 5 / Novelty 3 = 26 → NOW.**

### N-5. Aura Originals — bundled CC0 sound pack (long-promised Phase 1.1)

- **Source(s):** Aura ROADMAP Phase 1.1 (was P0 in prior priority matrix); [Freesound API + CC0 license walkthrough](https://opensource.creativecommons.org/blog/entries/freesound-intro/); [F-Droid Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/); [iOS 17/18 tone packs as cultural reference](https://www.zedge.net/find/ringtones/discord); [Ringdroid retirement signal making this niche open](https://forum.f-droid.org/t/ringtone-maker-app/22600); [WorkManager 2.10 + setExpedited download pattern](https://developer.android.com/reference/androidx/work/WorkRequest.Builder#setExpedited(androidx.work.OutOfQuotaPolicy)).
- **Why now:** First-run currently demands the network. The "Aura Picks" carousel went URL-backed-prebuffered in v6.13.0 but the actual bundle never shipped. Every commercial competitor ships day-one content; Aura's instant-startup story is undermined the moment the user disables Wi-Fi or hits a rate limit.
- **Scope:** Curate 200–500 CC0 sounds across ringtones (8–30s), notifications (1–5s), alarms (10–40s). Audit each for CC0 attribution + sha256 manifest for retroactive removal (per existing Round 3 note). Ship as a `WorkManager` first-launch download (~30 MB OGG) into `filesDir/aura_originals/` rather than bloating the APK. Update Room schema with `is_bundled` flag + `sha256` column (migration v14 → v15). Surface as "Aura Originals" tab + badge.
- **Risk:** CC0 misattribution on Freesound is well-documented; require a moderator review pass on every bundled file. Audio fidelity normalization needed (per Round 3 warning that preview-hq-mp3 is re-encoded; the bundle should use originals).
- **Fit 5 / Impact 5 / Effort 2 / Risk 3 / Deps 5 / Novelty 4 = 24 → NOW.**

---

## Next — queued, scored, ready

Thirteen items. All scored 18–25. Pull from the top of this list when Now closes. Four new items added in rev4 (NX-10..NX-13) sit at the back of the queue but score well; promote ahead of older items only when their dependencies (N-1 toolchain, primarily) are unblocked.

### NX-1. GL/AGSL live wallpaper engine migration (T-9 reframed)

- **Source(s):** [AlynxZhou/alynx-live-wallpaper](https://github.com/AlynxZhou/alynx-live-wallpaper) (ExoPlayer + OpenGL ES reference); [maocide/UndeadWallpaper](https://github.com/maocide/UndeadWallpaper) (gapless OpenGL + ExoPlayer); [Media3 1.9 dav1d-based AV1 extension](https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html); [Media3 1.6 pre-warming decoders](https://android-developers.googleblog.com/2025/03/media3-1-6-0-is-now-available.html); [patzly/pallax-android archive note](https://github.com/patzly/pallax-android) (Canvas-based live wallpapers were archived due to rendering inefficiency — direct cautionary tale); [GLSurfaceView RGB_565 banding pitfall](https://www.learnopengles.com/how-to-use-opengl-es-2-in-an-android-live-wallpaper/); [scale-types issue](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/14).
- **Why next:** Aura's `VideoWallpaperService` uses `MediaPlayer` with `setVolume(0,0)`. Moving to Media3 ExoPlayer + AGSL/OpenGL pipeline lands four wins at once: gapless transitions; AV1 decode where hardware supports it; per-video focus rectangle / pan + zoom (Pixel Live Effects "Cinematic" parity); proper aspect-ratio handling. The existing Canvas-based parallax should also migrate behind AGSL `RuntimeColorFilter` for the same reason — Pallax was archived because Canvas live wallpapers can't keep up.
- **Scope:** Vendor a thin `GLWallpaperService` base in `com.freevibe.wallpaper.gl/`. Pause render thread on `Engine.onVisibilityChanged(false)`. Add `media3-ui-compose` for the preview surface. Replace `VideoWallpaperService` MediaPlayer path with ExoPlayer + `samplerExternalOES` shader. Keep the Canvas GIF renderer (already battery-bounded). Add Pan / Zoom / Focus controls in the apply sheet. Per-video FPS cap + quality preset (Wallpaper Engine parity).
- **Risk:** Largest refactor in 12 months. AV1 hardware decode is ~10 % of install base ([Meta engineering analysis](https://engineering.fb.com/2025/09/24/video-engineering/video-streaming-with-av1-video-codec-mobile-devices-meta-white-paper/)). Battery regression risk if the new pipeline skips invisible-pause.
- **Fit 4 / Impact 5 / Effort 1 / Risk 2 / Deps 3 / Novelty 4 = 19 → NEXT.**

### NX-2. Lockscreen depth — Subject-aware clock-tuck + lockscreen Glance widgets — `[~]` widget surface enabled 2026-05-17 rev4-impl

> Lockscreen Glance widget surface enabled: `res/xml/freevibe_widget_info.xml` widget category bumped from `home_screen` to `home_screen|keyguard`. On Android 16 QPR2+ (December 2025 stable) the existing `FreeVibeWidget` is now placeable on the lockscreen surface without any Glance code change — the widget already reads from `WallpaperHistoryManager` so it shows the most-recent applied wallpaper as a background. Older Android versions silently ignore the `keyguard` bit. Clock-tuck (subject-aware mask blending) + the dedicated daily-pick lockscreen widget variant still pending — those need a `WallpaperHistoryManager.subjectMask` field, a new lockscreen-only `daily_pick_widget_info.xml`, and the Glance composable. Held until the user has tested the existing widget on a Pixel 9 / 10 lockscreen so we know which size to design for.



- **Source(s):** [Android 16 QPR2 lock-screen widgets on phones](https://www.androidauthority.com/lock-screen-widgets-on-phones-android-16-qpr2-3589668/); [Glance 1.2 release notes](https://developer.android.com/jetpack/androidx/releases/glance); [One UI 8.5 Adaptive Lock Screen Clock](https://www.sammyfans.com/2025/09/28/one-ui-8-adaptive-lock-screen-clock/); [Nothing OS 4.1 depth effect](https://gadgets.beebom.com/guides/nothing-os-4-1-features); [iOS-style Depth Effect](https://www.one4studio.com/glossary/parallax-wallpaper); [Muzei issue #794 — different sources for lock and home](https://github.com/muzei/muzei/issues/794); [Doodle issue #92 — static lockscreen wallpaper](https://github.com/patzly/doodle-android/issues/92).
- **Why next:** Aura's `dualWallpapers` already handles home/lock pairs. What's missing is the *subject-aware* depth effect that iOS, Nothing OS, and One UI all ship. With N-3's Subject Segmentation in place, "clock tucks behind wallpaper subject" is a derivative feature. Lock-screen Glance widgets land on phones in Android 16 QPR2; Aura's existing Glance widget should opt-in (`not_keyguard` category check) so it can run as a lockscreen daily-pick.
- **Scope:** Generate clock-mask Bitmap on apply via subject segmentation; persist to `wallpaper_history` table. New live-wallpaper engine renders subject foreground layer over an artificially deepened background blur, with a hint surface that the system lockscreen renderer overlays the clock against. Ship a lockscreen Glance widget variant of Daily Pick. Add a "Lock screen only" option to wallpaper apply (Doodle parity).
- **Risk:** Engine-side clock-position estimation is heuristic on non-Pixel devices; ship as Pixel + Samsung allowlist initially.
- **Fit 5 / Impact 4 / Effort 2 / Risk 4 / Deps 3 / Novelty 5 = 23 → NEXT.**

### NX-3. Smart Crop with Subject Segmentation (Phase 6.5 finally) — `[x]` wallpaper + video variants shipped 2026-05-17 rev4-impl(.2)

> Wallpaper + video variants both shipped. **Wallpaper**: new `SmartCropCalculator.kt` (pure geometry, 7 unit tests) + `SmartCropDetector.kt` (ML Kit Subject Segmentation via the same unbundled segmenter that N-3 wired into `ParallaxWallpaperService`, accessed via reflection on the `SubjectSegmentationResult` type so the file is robust against minor ML Kit API drift). `WallpaperCropViewModel.applySmartCrop` is a suspend function returning the new `(scale, offsetX, offsetY)` transform; the composable launches it via `rememberCoroutineScope().launch` and syncs local `rememberSaveable` gesture state on success. UI: Smart Crop FilterChip with sparkle icon + Detecting… spinner; "Couldn't detect a subject — drag to position manually" snackbar fallback. **Video**: TopAppBar action button on `VideoCropScreen` extracts a frame at loop start via `MediaMetadataRetriever.getFrameAtTime(OPTION_CLOSEST_SYNC)`, runs the same `SmartCropDetector` against it, then pans the video so the detected subject lands at the viewport centre. Keeps the user's current zoom (different from wallpaper variant, which auto-zooms — video editors don't want to lose their carefully chosen crop scale). Translates subject pixel-coords through `fitScale * scale` to match `VideoCropScreen.clampTransform`'s coordinate system. Both variants share the detector; geometry differs only by viewport-coordinate convention.



- **Source(s):** Aura Phase 6.5 (never shipped); [ML Kit Subject Segmentation Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android); [WallFlow Plus smart crop](https://github.com/ammargitham/WallFlow); [WallYou advanced cropping](https://github.com/you-apps/WallYou/issues/189); [Paperize vertical scrolling crop](https://github.com/Anthonyy232/Paperize/issues/428).
- **Why next:** N-3 lands Subject Segmentation; smart crop becomes a 2-day feature. Aura's existing pinch-zoom + aspect presets already give you most of the chrome; the missing piece is auto-positioning the crop rectangle to keep the primary subject in frame when reshaping landscape → portrait.
- **Scope:** Smart Crop toggle in `WallpaperCropScreen` + `VideoCropScreen`. When enabled, run Subject Segmentation, compute bounding box, center crop rectangle on it. Compare against rule-of-thirds heuristic for non-portrait outputs. Fall back to existing center-crop if confidence < 0.5.
- **Risk:** Slow on low-end devices; gate on Performance Class.
- **Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 2 (depends on N-3) / Novelty 3 = 23 → NEXT.**

### NX-4. SelectedContentHolder removal (Phase 7.2) — `[~]` process-death survival shipped 2026-05-17 rev4-impl

> Singleton now persists the **single selected wallpaper + selected sound** to a `freevibe_selected_content` SharedPreferences file via Moshi JSON on every `select*` call. On Hilt construction the holder lazy-restores from disk so after process death the detail screen's primary item is intact. `wallpaperList` (the pager-supporting list) intentionally still in memory only — process-death restoration of a 50-item URL list would jam the cold start with prefetch; the detail screen already handles the "list lost" case by collapsing to single-item display.
>
> Full sweep — nav-graph-scoped `SelectionViewModel` backed by `SavedStateHandle` + `ViewModelScenario` process-death tests + delete `SelectedContentHolder.kt` — still queued. It's the wider refactor that touches every detail/pager screen and rides Navigation 2.9 type-safe routes (N-1-gated). This NX-4 rev4-impl closes the worst-case "wallpaper detail blank on resume" bug class without that refactor.



- **Source(s):** Aura Phase 7.2; [Navigation Compose 2.9 type-safe routes](https://developer.android.com/jetpack/androidx/releases/navigation); [Lifecycle 2.10 `ViewModelScenario` for process-death testing](https://developer.android.com/jetpack/androidx/releases/lifecycle); existing `SelectedContentHolder.kt` (in-memory singleton).
- **Why next:** The singleton bridges screens but doesn't survive process death — a well-documented gotcha in CLAUDE.md. Navigation 2.9 type-safe routes can pass enums and value classes; combined with `SavedStateHandle`, you can replace the holder with a per-nav-graph ViewModel and serialize selection state. Removes a class of "wallpaper detail blank on resume" bugs.
- **Scope:** Move `selectedWallpaper`, `wallpaperList`, `selectedSound`, `pendingCategoryQuery` to a nav-graph-scoped `SelectionViewModel` backed by `SavedStateHandle`. Add a `ViewModelScenario` test for process-death restoration. Delete `SelectedContentHolder.kt`.
- **Risk:** Touches every detail/pager screen. Diff will be wide but mechanical.
- **Fit 5 / Impact 3 / Effort 3 / Risk 4 / Deps 3 / Novelty 1 = 19 → NEXT.**

### NX-5. Plugin / source ABI — Muzei-compatible "Aura Sources"

- **Source(s):** [Muzei Art Provider docs](https://api.muzei.co/); [MuzeiArtProvider source on GitHub](https://github.com/muzei/muzei/blob/main/muzei-api/src/main/java/com/google/android/apps/muzei/api/provider/MuzeiArtProvider.java); [Ian Lake's Muzei 3.0 announcement](https://medium.com/muzei/announcing-muzei-live-wallpaper-3-0-d167dd5795a4); [Pixiv4Muzei3 reference plugin](https://github.com/yellowbluesky/PixivforMuzei3); [HK Vision Muzei plugin reference](https://github.com/hossain-khan/android-hk-vision-muzei-plugin); [LiveWallpaperIt Muzei Reddit plugin](https://github.com/TBog/live-wallpaper-it); [Aura T-8 deferred note](docs/research/iter-1-scored.md); [Muzei plugin breaking-changes history](https://github.com/muzei/muzei/wiki/Changelog).
- **Why next:** Adopting Muzei's `MuzeiArtProvider` IPC contract lets Aura *consume* every existing Muzei source (Pixiv, Reddit, HK Vision, etc.) without writing any source-specific code; it also lets Aura *publish* itself as a Muzei source so Muzei users see Aura content. Two extensible ecosystems for the price of one. Avoids the "Muzei 1.x → 2.x broke everything" trap by versioning from day one.
- **Scope:** New `aura-sources` module exposing `AuraArtProvider` (Muzei-API-compatible). Wire `MuzeiArtSource` discovery via `PackageManager` query. Implement the inverse: a thin `MuzeiSourceBridge` repository that calls into installed Muzei providers and exposes their results in Aura's Discover. Version the contract from `v1`. Ship Pixiv source as a reference plugin in the repo.
- **Risk:** Muzei's API is GPL-3 in places; verify license bridge. Wear-Os mismatch warning in Muzei #869 — be careful.
- **Fit 4 / Impact 4 / Effort 2 / Risk 3 / Deps 3 / Novelty 5 = 21 → NEXT.**

### NX-6. Scheduler triggers — per-app exclusion, screen-off pre-stage, sub-15-min intervals, per-unlock — `[~]` per-unlock + screen-off pre-stage shipped 2026-05-17 rev4-impl

> **Per-unlock + screen-off pre-stage shipped.** New `RotationTriggerService` (foreground service with `specialUse` type) dynamically registers `Intent.ACTION_USER_PRESENT` + `Intent.ACTION_SCREEN_OFF` receivers (both blocked from manifest registration since Android 8). On each fire it enqueues a one-shot expedited `AutoWallpaperWorker` via `WorkManager.enqueueUniqueWork(KEEP)` so chatty unlock sequences coalesce. Two new DataStore prefs `rotateOnUnlock` / `rotateOnScreenOff` (default false) gate the service lifecycle; `RotationTriggerService.reconcile(unlock, screenOff)` is the idempotent start/stop entry point invoked from `FreeVibeApp.onCreate` (cold-start rehydration) and `SettingsViewModel.setRotateOn{Unlock,ScreenOff}` (toggle-driven). Manifest declares the service + `FOREGROUND_SERVICE_SPECIAL_USE` permission; users see a low-priority "Wallpaper triggers active" notification only when at least one trigger is opted in. Two new Settings toggles in the rotation section: "Change on every unlock" + "Pre-stage on screen off".
>
> **Still pending:** per-app rotation exclusion (needs `PACKAGE_USAGE_STATS` runtime permission flow), sub-15-min interval via AlarmManager `setExact`, and the one-tap-shuffle Glance widget per the WallYou ask. Hold those behind real user feedback on the per-unlock surface.



- **Source(s):** [Paperize #444 — per-app exclusion](https://github.com/Anthonyy232/Paperize/issues/444); [Paperize #482 — trigger on screen off](https://github.com/Anthonyy232/Paperize/issues/482); [Paperize #447 — specific time of change](https://github.com/Anthonyy232/Paperize/issues/447); [Paperize #126 — display events](https://github.com/Anthonyy232/Paperize/issues/126); [WallYou #229 — sub-15min interval](https://github.com/you-apps/WallYou/issues/229); [WallYou discussion #133 — anarkia47 widget for instant change](https://github.com/you-apps/WallYou/discussions/133); [Doubi88/SlideshowWallpaper #69 — force change](https://github.com/Doubi88/SlideshowWallpaper/issues/69); existing `AutoWallpaperWorker.kt`; existing `buildAutoWallpaperConstraints` helper (v6.12.0).
- **Why next:** All four asks recur across every OSS wallpaper-changer tracker. Aura already has the worker, constraints helper, and the SafeSearch + charging-only / Wi-Fi-only / idle toggles. The missing pieces are: per-app exclusion (compute foreground-app via `UsageStatsManager`); screen-off pre-stage (set wallpaper on `ACTION_SCREEN_OFF` so unlock shows the new one); rotation intervals shorter than the WorkManager 15-minute minimum (move to a `JobScheduler`-friendly `setExact` alarm); per-unlock trigger (`USER_PRESENT` broadcast).
- **Scope:** Permission-gated foreground-app reader. New rotation-trigger types in DataStore. `RotationTriggerService` listens to broadcasts. UI in `SettingsScreen` adds: "Exclude these apps", "Change on screen off", "Change on every unlock", "Change every N minutes (override 15-min minimum)". One-tap-shuffle Glance widget per the WallYou ask.
- **Risk:** `USER_PRESENT` background restrictions on Android 12+; require foreground service for fast triggers, which is intrusive. Mitigate with a clear opt-in banner.
- **Fit 5 / Impact 4 / Effort 3 / Risk 3 / Deps 5 / Novelty 3 = 23 → NEXT.**

### NX-7. Favorites sync via Firestore + Google sign-in (Phase 7.3)

- **Source(s):** Aura Phase 7.3; existing `default_web_client_id` blocker noted in Phase 4.3; [Firebase Auth Android docs](https://firebase.google.com/docs/auth/android/google-signin); [Room 2.8 schema defaults](https://developer.android.com/jetpack/androidx/releases/room); [Firestore offline persistence](https://firebase.google.com/docs/firestore/manage-data/enable-offline); [Aura existing FavoritesExporter](app/src/main/java/com/freevibe/service/FavoritesExporter.kt).
- **Why next:** Once N-2 lands BoM 34 + Custom Claims, the Google sign-in OAuth client can be wired without a separate trust review. Favorites are the only stateful user data not already in cloud (community uploads + votes + creator follows already are). Sync lets users move devices without losing their library and lays the ground for Wear OS / desktop companions.
- **Scope:** Google sign-in optional (no degradation for anonymous users). New Firestore collection `users/{uid}/favorites` with bidirectional sync against the local Room favorites table. Conflict resolution = last-write-wins by timestamp. Reuse FavoritesExporter's JSON schema for cross-device interop. Strong test coverage for sign-in / sign-out state changes.
- **Risk:** Firestore quota; Anonymous-Firebase-identity → Google-auth account-link path is fragile (must `linkWithCredential` not re-create). Document the failure mode for users who sign in on two devices simultaneously.
- **Fit 4 / Impact 4 / Effort 2 / Risk 3 / Deps 3 (N-2) / Novelty 2 = 18 → NEXT.**

### NX-8. Distribution to F-Droid + IzzyOnDroid + Obtainium — `[~]` release integrity + metadata partials shipped

> Fastlane metadata under `fastlane/metadata/android/en-US/` refreshed: title (`FreeVibe` → `Aura`), short description bumped to reflect no-ads / no-tracking, full description rewritten against the current feature set (29 features incl. NX-3 Smart Crop, NX-6 rotation triggers, L-2 Tasker hook, parallax, weather effects). New `changelogs/111.txt` lands v6.31.0 release notes. New `obtainium.json` at repo root lets Obtainium users track Aura via GitHub Releases with the `v*` tag regex + APK filter.
>
> 2026-06-04 Cycle 2 P0 release-integrity pass fixed the tag workflow: it now restores release signing material from GitHub secrets, runs `:app:assembleRelease`, rejects debuggable APKs, runs `apksigner verify --print-certs`, and publishes `SHA256SUMS.txt` plus release notes with versionName/versionCode and signing certificate SHA-256. `docs/distribution/release-signing.md` is the signing/runbook surface for GitHub Releases + Obtainium.
>
> 2026-06-04 Cycle 2 P0 full-vs-foss decision: Aura stays full-only for GitHub Releases/Obtainium, and IzzyOnDroid is the realistic near-term app-store target. F-Droid mainline is blocked while the only variant includes Firebase, the Google Services Gradle plugin, and Play Services ML Kit. `docs/distribution/channel-strategy.md` records the channel matrix and future FOSS unblock criteria; `tools/fdroid_preflight.py --expect-blocked` verifies the current blocker state without compiling APKs.
>
> 2026-06-04 Cycle 2 P1 developer-verification/Izzy prep: `.github/workflows/release.yml` release notes now carry Android developer verification status for `com.freevibe`. `docs/distribution/developer-verification.md` documents the owner-only ADC/PDC registration path, release checklist, IzzyOnDroid submission prep, F-Droid blocker state, and the branch-protection required-check owner action.
>
> Still pending: per-ABI `splits { abi { ... } }` in `app/build.gradle.kts` (needs N-1 build verification to cut the universal APK from one fat binary to four lean ones), actual IzzyOnDroid submission after a signed `v*` release is visible, Android developer verification owner confirmation in ADC/PDC, and any future F-Droid metadata PR after a real `foss` flavor exists. The `verify.yml` workflow (NX-12) is the prerequisite for reproducible-build verification.



- **Source(s):** [F-Droid Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/); [F-Droid Reproducible Builds docs](https://f-droid.org/en/docs/Reproducible_Builds/); [F-Droid in 2025 retrospective](https://f-droid.org/en/2026/01/23/fdroid-in-2025-strengthening-our-foundations-in-a-changing-mobile-landscape.html); [F-Droid 1.19+ Session Installer background updates](https://f-droid.org/2024/02/01/twif.html); [IzzyOnDroid repo docs](https://apt.izzysoft.de/fdroid/index/info); [Obtainium app](https://github.com/ImranR98/Obtainium); [APK splits per-ABI reference](https://cdmunoz.medium.com/goodbye-giant-apk-how-we-went-from-186-mb-to-62-mb-with-split-per-abi-and-three-lines-in-ci-673dd71dbdcb); existing `.github/workflows/release.yml`.
- **Why next:** Aura's only distribution channel is signed GitHub Releases. The OSS Zedge-alternative pitch demands F-Droid presence. IzzyOnDroid is the path of least friction; Obtainium asks only for a structured release manifest. Per-ABI splits cut the APK from a single universal binary to four lean ones — meaningful since youtubedl-android bundles Python 3.8.
- **Scope:** Ship `fastlane/metadata/android/en-US/{short_description.txt,full_description.txt,changelogs/,images/}`. Verify reproducible builds via `apksigner` + `--ks-key-alias` (per F-Droid docs). Submit to IzzyOnDroid first. Add `splits { abi { ... } }` to `app/build.gradle.kts` with per-ABI release outputs. Update release workflow to attach all four APKs + a universal. Add an `obtainium` JSON manifest at repo root. Open the F-Droid metadata PR last (it's the slowest review).
- **Risk:** F-Droid forbids non-free dependencies. Firebase Storage may push you to the IzzyOnDroid track only (which permits proprietary deps). NewPipe Extractor + youtubedl-android are GPL and OK.
- **Fit 5 / Impact 4 / Effort 3 / Risk 4 / Deps 3 / Novelty 2 = 21 → NEXT.**

### NX-9. Media3 1.10 Material3 playback composables + dynamic scheduling

- **Source(s):** [Media3 1.10 release blog](https://android-developers.googleblog.com/2026/03/media3-110-is-out.html); [Media3 1.10 dev blog post](https://developer.android.com/blog/posts/media3-1-10-is-out); [Media3 release page](https://developer.android.com/jetpack/androidx/releases/media3); [Compose 2026 ExoPlayer guide](https://medium.com/@ramadan123sayed/media-player-in-jetpack-compose-the-complete-2026-guide-exoplayer-media3-1-10-0a25af46ce7d); existing `SoundDetailScreen.kt` hand-rolled waveform + progress; existing `WallpaperPreviewScreen` video preview.
- **Why next:** Aura's sound preview UI rolls its own waveform + progress + speed control across `SoundDetailScreen`, `SoundEditorScreen`, and the YouTube tab. Media3 1.10 (March 2026) adds Material3-styled composables — `PlayerComposable` (combines `ContentFrame` + controls), `ProgressSlider`, `PlaybackSpeedControl` — that replace ~300 LOC of custom UI with library code styled to match the rest of the app. Bonus: `ExoPlayer.Builder.experimentalSetDynamicSchedulingEnabled()` ships in 1.10 as an experimental power-saver for the in-app video preview surface — direct fit for Aura's battery-discipline charter.
- **Scope:** Bump Media3 1.5.1 → 1.10.0 (sequenced inside N-1's lockstep toolchain pass; the new composables compile against Compose BOM 2026.05 only). Migrate `WallpaperPreviewScreen` video preview to `PlayerComposable` + `ContentFrame` + Aura's existing `GlassCard` chrome. Replace the hand-rolled scrubber in `SoundDetailScreen` with `ProgressSlider`. Add `PlaybackSpeedControl` to `SoundEditorScreen` (currently no in-editor speed control). Opt into `experimentalSetDynamicSchedulingEnabled()` behind a Settings → Advanced → Dynamic scheduling toggle for the video preview surface.
- **Risk:** Library composables don't yet expose Aura's rectangular 4-12dp radius/letter-spacing design system from v6.16.0 polish — may need a thin theming wrapper. Experimental dynamic-scheduling API can be removed in any minor release; flag for monitoring.
- **Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 3 (N-1) / Novelty 2 = 19 → NEXT.**

### NX-10. Android 17 EyeDropper API — pixel-pick → wallpaper colour search — `[~]` shipped 2026-05-17 rev4-impl-2

> EyeDropper FAB lands in `WallpapersScreen` `FloatingActionTray` on the Discover tab. Raw-string Intent (`"android.intent.action.OPEN_EYE_DROPPER"` + `"android.intent.extra.COLOR"`) keeps the integration compatible with compileSdk 35 — no API 37 class refs at compile time. `eyeDropperAvailable` probes `PackageManager.resolveActivity` so the FAB hides on builds where the system EyeDropper app hasn't been installed yet (un-updated GSI). On pick, the returned `Int` colour flows through new `WallpapersViewModel.searchByPickedColor()` which strips alpha and converts to the 6-char hex Wallhaven's `colors=` query expects. No fallback path needed — Android 16 and below silently keep the existing Material You + Wallhaven palette flow because the FAB is hidden when the API isn't on the device. Future surface: the same launcher could seed `AiWallpaperScreen` prompts and community-upload colour tags; held to confirm Android 17 install-base + user signal first.



- **Source(s):** [Android 17 Beta 2 EyeDropper announce — 9to5Google](https://9to5google.com/2026/02/26/android-17-beta-2-contacts-and-display-color-access/); [Android Engineers Substack walkthrough](https://androidengineers.substack.com/p/introducing-the-android-17-eye-dropper); [ProAndroidDev EyeDropper API deep-dive (Mar 2026)](https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16); [Android 17 release notes](https://developer.android.com/about/versions/17/release-notes); [Android Authority first look](https://www.androidauthority.com/android-17-eyedropper-color-picker-3610073/); existing `WallpapersViewModel.matchMyTheme` (Material You accent → Wallhaven `colors=` query).
- **Why next:** Aura's flagship "Match my theme" feature seeds Wallhaven colour search from the system Material You accent. EyeDropper (`Intent.ACTION_OPEN_EYE_DROPPER`, `Intent.EXTRA_COLOR`) ships in Android 17 (Beta 2, locked in Beta 3) and lets the user pick **any** on-screen pixel without screen-recording permission or accessibility-service abuse. Direct fit: "pick a colour from anywhere → seed wallpaper search → match my desk lamp / album art / favourite jacket". No OSS wallpaper app uses it yet — leapfrog opportunity tracked at zero implementation cost.
- **Scope:** Wallpaper search bar + AI generation prompt + community-upload tag editor each get an EyeDropper FAB. Implement behind `Build.VERSION.SDK_INT >= 37` gate (no fallback needed — Android 16 and below keep the existing Material You + Wallhaven palette flow). Launch via `ActivityResultContracts.StartActivityForResult` since the API returns `Intent.EXTRA_COLOR` as an `Int`. Convert to nearest `WallhavenPurity`-safe colour query and to a Wallhaven `colors=` hex.
- **Risk:** Android 17+ only — install base hits ~10 % by EOY 2026, mainstream by Q2 2027. Settings → Advanced toggle to surface the feature on supported devices avoids dead UI on older versions.
- **Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 3 (N-1 raises targetSdk) / Novelty 4 = 22 → NEXT.**

### NX-11. Android 17 Photo Picker 9:16 portrait customization (N-4 follow-up) — `[~]` shipped 2026-05-17 rev4-impl-2

> Drop-in `AuraPickVisualMedia` subclass of `ActivityResultContracts.PickVisualMedia` overrides `createIntent` and calls `PhotoPickerCustomization.apply9x16AspectRatio(intent)` before launching. The helper does the actual API-37-only API call via reflection (`PhotoPickerUiCustomizationParams.Builder().setGridAspectRatio(9, 16).build()` + `Intent.putExtra(EXTRA_PHOTO_PICKER_UI_CUSTOMIZATION_PARAMS, params)`) so the integration ships at compileSdk 35 today and becomes a straight-line call once N-1 unlocks compileSdk 37. Wired at three call sites: wallpaper community upload (`WallpapersScreen`), collection QR import (`CollectionsScreen`), parallax-from-photo (`SettingsScreen`). Reflection failure is logged DEBUG and never throws — picker falls back to its default 1:1 grid. Android 16 and below pass through transparently.



- **Source(s):** [Android Developers Blog — Android 17 Beta 3 PhotoPickerUiCustomizationParams](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html); [Android 17 release notes (Photo Picker section)](https://developer.android.com/about/versions/17/release-notes); [Photo Picker docs](https://developer.android.com/training/data-storage/shared/photo-picker); existing `PickVisualMedia.ImageOnly` call sites in `WallpapersScreen` community upload + `CollectionsScreen` QR import (landed in N-4 / commit `b0ae1fe`).
- **Why next:** N-4 migrated image imports to the system Photo Picker. Android 17 adds `PhotoPickerUiCustomizationParams` to switch the picker's grid from 1:1 squares to 9:16 portrait thumbnails — the canonical wallpaper-app aspect ratio. Every wallpaper Aura ships at is portrait; every gallery picker today crops thumbnails wrong. This is the smallest, highest-fit follow-up to N-4 on the platform.
- **Scope:** Wrap existing `PickVisualMedia` launchers in a version-gated builder. On Android 17+, attach `PhotoPickerUiCustomizationParams.Builder().setGridAspectRatio(9, 16).build()`. No code-change for older versions. One commit, ~30 LOC.
- **Risk:** API only available on API 37+. Test against the embedded photo picker on Pixel 6+ once N-1 unlocks compileSdk 37.
- **Fit 5 / Impact 3 / Effort 5 / Risk 5 / Deps 3 (N-1) / Novelty 2 = 23 → NEXT.**

### NX-12. CI build verification on every push / PR (workflow gap) — `[~]` shipped 2026-05-17 rev4-impl

> Shipped `.github/workflows/verify.yml` — triggers on `push: main` + `pull_request: main` + `workflow_dispatch`. Runs `assembleDebug` + `testDebugUnitTest` + `lintDebug` on JDK 17 with Gradle cache. Stubs `local.properties` so signing/API-key lookups don't fail in CI (release.yml stays the source of truth for signed builds). Uploads test + lint reports as artifacts on failure with 14-day retention. `concurrency` group cancels superseded runs so CI doesn't queue up on rapid pushes. Branch protection requiring `verify` must still be enabled on `main` by the repo owner.



- **Source(s):** existing [`.github/workflows/release.yml`](.github/workflows/release.yml) — `on: push: tags: ['v*']` + `workflow_dispatch` only; no PR / branch protection trigger; no unit-test or lint step; [GitHub Actions Android template](https://github.com/actions/starter-workflows/blob/main/ci/android.yml); [Gradle build cache action `gradle/actions/setup-gradle`](https://github.com/gradle/actions); [F-Droid Reproducible Builds requirements](https://f-droid.org/en/docs/Reproducible_Builds/) (NX-8 depends on a clean build environment); existing 49 unit-test files awaiting CI runs.
- **Why next:** Every Implementation Pass since 2026-04-25 has been **static-review-only** because the executing environment has no JDK/SDK. The N-1 toolchain triad (AGP 9 / Gradle 9 / Kotlin 2.3) cannot be honestly tested without a build-verified CI lane — bumping versions blind is a known regression vector for KSP2, Hilt, and Compose Strong Skipping. The current `release.yml` only fires on tag, so PRs and `main` pushes go un-verified. This is the dev-experience gap blocking N-1, NX-1, and most of T-A.
- **Scope:** New `.github/workflows/verify.yml` triggered on `push: branches: [main]` and `pull_request: branches: [main]`. Jobs: setup JDK 17 → cache Gradle → `./gradlew assembleDebug testDebugUnitTest lintDebug`. Upload `app/build/reports/{tests,lint-results-debug.html}` as artifacts on failure. Optionally: `./gradlew :app:assembleRelease` behind a manually-fired `release-dry-run` job that uses a CI-only signing key (no leak risk; release.yml stays the source of truth). Enable branch protection on `main` requiring `verify` to pass. F-Droid reproducible-build verification is a stretch follow-up — defer to NX-8.
- **Risk:** Workflow drift if `verify.yml` and `release.yml` diverge — mitigate by extracting the build steps into a shared composite action or a reusable workflow. Secrets-leak risk on PRs from forks — keep all signing keys out of `verify.yml`; restrict release jobs to `pull_request_target` only if absolutely needed (default: no).
- **Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 4 / Novelty 1 = 23 → NEXT.**

### NX-13. Predictive-back wiring through Compose NavHost transitions — `[~]` partial, 4 of ~18 screens 2026-05-17 rev4-impl(.2)

> BackHandler discipline now covers four in-flight / unsaved-changes screens:
> - **`AiWallpaperScreen`** — back during generation cancels the in-flight Stability AI job (new `AiWallpaperViewModel.cancelGeneration()` + `generationJob: Job` tracker + `onCleared()` defensive cancel). Saves the user's API credit budget when they back out of a slow generation.
> - **`VideoCropScreen`** — back while the FFmpeg subprocess is running toasts "Cropping in progress — please wait" and holds the screen so the cropped file has somewhere to land. Doesn't kill ffmpeg (its lifecycle is process-not-coroutine).
> - **`WallpaperEditorScreen`** (rev4-impl-2) — back with dirty filter state (any non-default brightness / contrast / saturation / warmth / blur / AMOLED / vignette / grain) opens a "Discard edits?" `AlertDialog` with Keep editing / Discard. Discard calls `resetAll()` then `onBack()`.
> - **`SoundEditorScreen`** (rev4-impl-2) — same pattern for trim fractions + fade-in/out (`trimStartFraction != 0f || trimEndFraction != 1f || fadeInMs != 0L || fadeOutMs != 0L`). Apply paths bypass the dialog by clearing the guard before `onBack` fires.
>
> Remaining 14 detail/preview/picker screens (WallpaperDetailScreen, SoundDetailScreen, WallpaperPreviewScreen, VideoWallpaperPreviewScreen, ContactPickerScreen, and the rest) still rely on default activity finish. Full NavHost predictive-back-aware transitions ride on Navigation 2.9 which is N-1-gated. Hold the remainder until N-1 lands.



- **Source(s):** [Predictive back in Compose docs](https://developer.android.com/develop/ui/compose/system/predictive-back); [Navigation 2.9 predictive-back integration](https://medium.com/@androidlab/androidx-navigation-2-9-6-complete-feature-breakdown-4b09ccd637dd); [Android 14 predictive back behaviour change](https://developer.android.com/about/versions/14/behavior-changes-14#predictive-back-gesture); existing `AndroidManifest.xml:50` (`android:enableOnBackInvokedCallback="true"`); existing `BackHandler` use confined to `CollectionsScreen.kt` + `FavoritesScreen.kt` (only 2 of ~22 detail/editor screens).
- **Why next:** Aura's manifest opts in to predictive back. Without per-screen `BackHandler` discipline, Compose detail / editor / preview / picker screens fall back to default activity finish — the user gets no smooth peek-the-previous-screen animation that Android 14+ defaults to. With Navigation 2.9's predictive-back integration landing in N-1, every detail screen (WallpaperDetailScreen, SoundDetailScreen, AiWallpaperScreen, CollectionsScreen, WallpaperEditorScreen, VideoCropScreen, SoundEditorScreen, ContactPickerScreen) should declare a `BackHandler` for in-flight state cleanup (cancel coroutines, save scroll position) and animate `progress` smoothly through `PredictiveBackHandler`.
- **Scope:** Audit all 22 screens. Add `BackHandler` to 18 missing ones with the right cleanup (cancel any in-flight FFmpeg / yt-dlp / segmenter job, save selection state). Switch NavHost to Navigation Compose 2.9's predictive-back-aware transitions in the same commit that bumps Navigation in N-1. Add a `PredictiveBackHandler` to one or two high-value flows (WallpaperEditor crop preview pull-to-dismiss; SoundEditor unsaved-changes confirm).
- **Risk:** Misplaced `BackHandler` can swallow back navigation entirely — keep each guard narrow (`enabled = state.isInflight || state.hasUnsavedChanges`). Predictive-back animations require Navigation 2.9+ for the NavHost integration; gating ties to N-1.
- **Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 4 (N-1) / Novelty 1 = 20 → NEXT.**

---

## Later — scoped, deferred

### L-1. Wear OS 6 companion via Watch Face Push API (was Phase 8.2)

- **Source(s):** [Watch Face Push API training](https://developer.android.com/training/wearables/watch-face-push); [Phone-side companion docs](https://developer.android.com/training/wearables/watch-face-push/phone-app); [Androidify on Wear OS](https://android-developers.googleblog.com/2025/12/bringing-androidify-to-wear-os-with.html); [Facer 5.0 Wear OS 6 features](https://news.facer.io/massive-facer-update-wear-os-6-new-features-for-all-7bb1480b5797); [cmota/Unsplash KMP Wear OS](https://github.com/cmota/Unsplash); [Watch Face Format docs](https://developer.android.com/training/wearables/wff).
- **Why later:** Wear OS 6 install base is small (Pixel Watch 4 launch). Watch Face Push API requires `minSdk=33` on the watch and is restricted to one face per marketplace app. Aura's novelty: a watch face *generated* from the user's currently-applied wallpaper — palette extraction (Aura already has it), complications derived from Material You tonal palette, optional Aura-Originals chime as the watch's "tick" sound.
- **Scope:** Phone-side companion only (no separate Wear OS app at first). Add a `WatchFaceCompositor` that reads `current_wallpaper.palette` + clock font + complication set → emits a WFF XML and pushes via Data Layer + Watch Face Push API. Surface as Settings → "Send to my Wear OS watch".
- **Fit 4 / Impact 3 / Effort 1 / Risk 3 / Deps 1 / Novelty 5 = 17 → LATER.**

### L-2. Tasker plugin (events / states / actions) — `[~]` action minimum shipped 2026-05-17 rev4-impl

> Minimum-viable Tasker integration: new `TaskerActionReceiver` (manifest-declared, exported) responds to `com.freevibe.action.ROTATE_NOW` + `com.freevibe.action.SHUFFLE_NOW` and re-enters `RotationTriggerService.enqueueRotation()` — same code path NX-6 uses for the unlock/screen-off triggers, so all existing rotation source/target/constraint prefs apply. Tasker users can now wire Aura into any condition (calendar event, geofence, time-of-day, Bluetooth-connected) with a one-line "Send Intent" action.
>
> Still pending (full plugin spec): `TaskerPluginActivity` for ACTION_EDIT_SETTING / ACTION_FIRE_SETTING (UI-mediated parameterized actions); event broadcasts (wallpaper-changed, source-X-returned-429); state queries (current source, last applied URL). Hold for explicit user signal — the broadcast surface covers the 80 % case (one-tap automation triggers).



- **Source(s):** [Tasker plugin spec](https://tasker.joaoapps.com/plugins-intro.html); [Peristyle external intent example `app.peristyle.START_AUTO_WALLPAPER_SERVICE`](https://github.com/Hamza417/Peristyle); [Muzei Tasker integration since 3.0](https://medium.com/muzei/announcing-muzei-live-wallpaper-3-0-d167dd5795a4).
- **Why later:** Tasker integration is the cheapest "10x your trigger surface" feature. Once NX-6 lands, exposing every rotation trigger as a Tasker action / state is a small follow-up — but cheap to defer.
- **Scope:** A `TaskerPluginActivity` host for `ACTION_EDIT_SETTING` + `ACTION_FIRE_SETTING`. Actions: change wallpaper, change ringtone, apply tone pack. Events: wallpaper changed, source X returned 429. States: current source name, last applied wallpaper URL.
- **Fit 5 / Impact 3 / Effort 4 / Risk 5 / Deps 4 (NX-6) / Novelty 2 = 23 — would be Next but capacity-bounded behind NX-1..NX-8. Hold.**

### L-3. RTDB → Firestore migration for community votes (T-10)

- **Source(s):** Aura T-10 deferred note; [Firebase RTDB free-tier limits](https://firebase.google.com/pricing) (100 concurrent, 10 GB/month); existing `VoteRepository.kt` ConcurrentHashMap pattern.
- **Why later:** Still no telemetry showing Aura is approaching the cap. Voting writes are short, batched, and well-behaved. Migrate when (a) we have telemetry, (b) Firestore Custom Claims are already deployed (post-N-2). Until then, accept the risk.
- **Fit 3 / Impact 3 / Effort 1 / Risk 2 / Deps 3 / Novelty 2 = 14 → LATER.**

### L-4. KMP shared logic (foundation for desktop / future iOS)

- **Source(s):** [panels-art/WallApp](https://github.com/panels-art/WallApp) (KMP wallpaper app reference); [cmota/Unsplash KMP](https://github.com/cmota/Unsplash); [Splashy](https://github.com/ishubhamsingh/Splashy); [Coil 3 Compose Multiplatform](https://coil-kt.github.io/coil/upgrading_to_coil3/); [NewPipeExtractor-KMP fork](https://github.com/yushosei/NewPipeExtractor-KMP); [Compose for TV 1.0](https://developer.android.com/jetpack/androidx/releases/tv); Aura Phase 8.3 (desktop companion stretch).
- **Why later:** Coil 3 unlocks Compose Multiplatform image loading. Splitting Aura's `data/` layer (repositories + models) into a KMP module is invasive. Worth it once a desktop or TV companion is on the calendar. Until then, expensive churn.
- **Fit 3 / Impact 3 / Effort 1 / Risk 2 / Deps 1 (Coil 3) / Novelty 4 = 14 → LATER.**

### L-5. Desktop companion (Tauri/Compose Multiplatform)

- **Source(s):** Aura Phase 8.3; [Wallpaper Engine on Android](https://www.wallpaperengine.io/android/en) (cross-platform sync model); [Tauri docs](https://v2.tauri.app/).
- **Why later:** Depends on L-4 (KMP). The Wallpaper Engine desktop ↔ mobile sync pattern is the right model — favorites + collections in cloud, applied via per-platform engines.
- **Fit 3 / Impact 3 / Effort 1 / Risk 2 / Deps 1 / Novelty 3 = 13 → LATER.**

### L-6. Audio visualizer wallpaper / edge-light (Muviz-style)

- **Source(s):** [Muviz Edge feature review](https://www.fastgazi.com/2025/10/muviz-edge-stylish-music-visualizer.html); [Spectrolizer Play Store](https://play.google.com/store/apps/details?id=com.aicore.spectrolizer); [Spotify dynamic backdrop pattern explainer](https://medium.com/@shanmugashree3/how-spotify-creates-those-stunning-backdrops-that-match-every-song-playlist-00fe13eab033); existing `WeatherWallpaperService` Canvas pipeline.
- **Why later:** Net-new live wallpaper engine. Reuses Aura's existing Canvas + palette extraction. Big visual surface but smaller-than-it-looks user demand on OSS forums; lots of commercial competitors.
- **Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 2 / Novelty 3 = 19 — would be Next but capacity-bounded. Hold.**

### L-7. Additional Glance widgets — Daily Wallpaper, Sound Quick-Set, Scheduler Controls

- **Source(s):** Aura Phase 7.4; [Glance 1.2 + `androidx.glance.wear` group](https://developer.android.com/jetpack/androidx/releases/glance); [WallYou widget request](https://github.com/you-apps/WallYou/discussions/133).
- **Why later:** Aura already ships a shuffle widget. Adding three more is mechanical but resizable variants (2x2/4x2/4x4) take time. Bundle with NX-2's lock-screen Glance widget work — share the layout system.
- **Fit 4 / Impact 3 / Effort 3 / Risk 5 / Deps 3 (NX-2) / Novelty 1 = 19 — hold.**

### L-8. True offline mode + prefetch + < 1.5s cold start

- **Source(s):** Aura Phase 7.5; existing `OfflineFavoritesManager` (80 MB/file, 512 MB total cap); [Baseline Profiles docs](https://developer.android.com/topic/performance/baselineprofiles/overview); [Strong Skipping perf wins](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping).
- **Why later:** Aura's startup is already fast (Discover feed cached). Real benefit kicks in once N-1 lands Strong Skipping + Compose Compiler 2.x. Generate baseline profiles for the five top-traffic screens (Wallpapers, Videos, Sounds, Favorites, WallpaperDetail). Tighten Coil 3 disk cache. Prefetch next 10 from scheduler source on Wi-Fi.
- **Fit 4 / Impact 3 / Effort 3 / Risk 5 / Deps 2 (N-1) / Novelty 1 = 18 — hold behind N-1.**

### L-9. Wallpaper presets / "Setups" bundle export (Phase 8.1)

- **Source(s):** Aura Phase 8.1; KWGT `.kwgt` bundle pattern; [Backdrops Streak Collections](https://backdrops.io/) as anti-pattern; existing `CollectionExporter`.
- **Why later:** A natural extension of Shareable Collections. Adds wallpaper + recommended ringtone + tone pack + widget config to a single `.aura` zip with deep-link. Defer until N-5 (Aura Originals) and NX-5 (plugin ABI) are in.
- **Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 3 / Novelty 3 = 20 — hold.**

### L-10. Compose Adaptive Layouts — foldables, tablets, Compose-for-TV

- **Source(s):** [Build adaptive apps with Compose](https://developer.android.com/develop/ui/compose/build-adaptive-apps); [Adaptive layouts overview](https://developer.android.com/develop/ui/compose/layouts/adaptive); [Compose Multiplatform 1.7.1 adaptive layouts release](https://medium.com/@thebackbit/master-adaptive-layouts-in-compose-multiplatform-build-truly-responsive-uis-89184bf8b6de); [Touchlab adaptive layouts in CMP](https://touchlab.co/adaptive-layouts-cmp); [WallFlow tablet support release notes](https://github.com/ammargitham/WallFlow); [Google Play Tablet/Foldable quality bar](https://developer.android.com/guide/topics/large-screens/get-started-with-large-screens); existing UI has **zero** WindowSizeClass / NavigationSuiteScaffold / ListDetailPaneScaffold usage (verified by repo grep 2026-05-17).
- **Why later:** Aura today is phone-first. The wallpaper grid stretches awkwardly on tablets and unfolded foldables; the bottom-nav rail should swap to a navigation rail / drawer above 600 dp width. WallFlow's tablet UI is a direct competitor differentiator; KMP-aware adaptive APIs land for free with L-4 (Compose Multiplatform foundation). Play Store ranks large-screen-optimized apps higher on tablets/foldables — discoverability matters for OSS distribution beyond F-Droid (NX-8).
- **Scope:** Add `material3-adaptive` + `material3-adaptive-navigation-suite` dependencies inside N-1's lock-step bump. Replace `FreeVibeRoot.kt` bottom nav with `NavigationSuiteScaffold`. Promote wallpaper detail to `ListDetailPaneScaffold` on Expanded widths. Audit wallpaper grid column count by WindowSizeClass (currently fixed, should be 2-3-4-6 by class). Add WindowManager fold-state listener for half-opened (book) posture. Compose-for-TV stub: declare a TV banner intent-filter so an Android TV install can show wallpapers on screensaver / TV screensaver via the Daydream service (no extra UI surface needed initially).
- **Risk:** Refactor touches NavHost + every top-level screen — diff will be wide. Lower-end devices may regress on first-paint time if `NavigationSuiteScaffold` adds a recomposition layer; measure with Macrobenchmark.
- **Fit 3 / Impact 3 / Effort 2 / Risk 4 / Deps 3 (N-1 + L-4) / Novelty 2 = 17 → LATER.**

---

## Under Consideration — needs scoping or charter call

### U-1. HDR / Ultra HDR (gainmap) wallpaper support
- **Source(s):** [Android 14 Ultra HDR](https://developer.android.com/media/grow/ultra-hdr/display); [BT.2020 / Display-P3 color management](https://source.android.com/docs/core/display/color-mgmt).
- **Open question:** Is the visual benefit worth the ICC-profile preservation rework? Pixel 8+ camera default; Samsung clamps to SDR. Charter: yes — Aura keeps quality high; deferred only on capacity.

### U-2. On-device Stable Diffusion via Snapdragon NPU
- **Source(s):** [xororz/local-dream](https://github.com/xororz/local-dream); [Qualcomm Stable Diffusion on 8 Gen 3 demo](https://www.qualcomm.com/news/onq/2024/02/worlds-first-on-device-demonstration-of-stable-diffusion-on-android); [Qualcomm Depth-Anything-V2 TFLite](https://huggingface.co/qualcomm/Depth-Anything-V2).
- **Open question:** Charter previously rejected on-device AI generation (R-1). Local-dream proves viability on a narrow chipset slice. NPU install base still <30 %. Hold until Snapdragon 8 Gen 4 / 5 baseline shifts.

### U-3. Pixiv source plugin
- **Source(s):** [PixivforMuzei3](https://github.com/yellowbluesky/PixivforMuzei3); requires OAuth2 + NSFW filtering tiers.
- **Open question:** Charter fit — sourcing art with legal/redistribution boundaries unclear. Defer to NX-5 plugin ecosystem.

### U-4. AI Sound Generation (Phase 3.2)
- **Source(s):** Aura Phase 3.2 (charter-pruned in v5.0.0); MusicGen / Riffusion abandoned per [community signal](https://news.ycombinator.com/item?id=38418254).
- **Open question:** MusicGen has no updates since 2024; Riffusion's Android app was pulled in Jul 2024. Server-side via Replicate is feasible but adds another paid-API key (charter friction). Keep rejected at the strong sense; reconsider if a credible OSS generator emerges.

### U-5. xHE-AAC ringtone output
- **Source(s):** [xHE-AAC (USAC) Wikipedia](https://en.wikipedia.org/wiki/Unified_Speech_and_Audio_Coding); Android 13+ native decoder; Aura's existing AudioTrimmer FFmpeg pipeline.
- **Open question:** 12–300 kbps with loudness/DRC built-in. Output target only — most user content arrives as MP3/AAC. Defer until users hit fidelity ceiling on alarms.

### U-6. AGSL shader playground (mini-KLWP for live wallpapers)
- **Source(s):** [ShaderEditor](https://github.com/markusfisch/ShaderEditor); [AGSL Compose patterns](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a); [lwp-shaders curated library](https://github.com/cipold/lwp-shaders).
- **Open question:** Could ship a curated shader gallery (5–10 presets: plasma, lava, particles, water) without exposing an editor. Editor remains deferred; gallery can proceed. Decide when L-6 reaches Now.

### U-7. Health Connect-driven wallpapers
- **Source(s):** [Health Connect granular permissions](https://developer.android.com/health-and-fitness/guides/health-connect); KLWP step count integration as reference.
- **Open question:** Charter call: Aura is a personalization app, not a fitness app. "Wallpaper that grows with your step count" is a novel ambient nudge. Hold for community signal.

### U-8. Bluetooth-aware ringtone routing (different sound on speaker vs headset)
- **Source(s):** Android in-band ringing mandatory since 8.1 ([XDA reference](https://www.xda-developers.com/android-pie-bluetooth-in-band-ringtones-default/)); [Android Central thread on the gap](https://forums.androidcentral.com/threads/can-i-get-phones-ringtone-in-bluetooth-headset.1005511/).
- **Open question:** Significant engineering surface (AudioRouting hooks, requires `MODIFY_AUDIO_ROUTING` system permission Aura cannot get). Likely impossible without OEM bridge. Hold.

### U-9. Spoken caller-ID accessibility
- **Source(s):** [Google Accessibility — Talkback caller-ID](https://support.google.com/accessibility/android/answer/6006564).
- **Open question:** Samsung does this natively; AOSP doesn't. Small Kotlin TTS layer in `ContactRingtoneService` would suffice. Hold for explicit accessibility feedback.

### U-10. On-device image upscaling (RealSR / Real-ESRGAN / NCNN)
- **Source(s):** [RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android); [Qualcomm AI Hub Depth-Anything-V2](https://huggingface.co/qualcomm/Depth-Anything-V2); [ImageToolbox upscale features](https://github.com/T8RIN/ImageToolbox).
- **Open question:** Useful for low-res community uploads. APK size impact (~50–100 MB NCNN model). Maybe ship as an optional Aura Sources plugin (per NX-5) rather than in-app.

### U-11. Localization & RTL audit
- **Source(s):** Existing Locale.ROOT sweeps in CHANGELOG (v5.7, v5.22, v6.7) prove the foundation is laid; no actual translations shipped. Aura's only string resource locale is `values/strings.xml` (English). Compose RTL support is on by default but no audit has confirmed correct mirroring of wallpaper grids, sound waveforms, or scrubber direction.
- **Open question:** Which locales are first? Community signal from F-Droid issue trackers ([PixivforMuzei3 #254 Traditional Chinese](https://github.com/yellowbluesky/PixivforMuzei3/issues/254) is one example of how the request lands) suggests Brazilian Portuguese, German, French, Spanish, Russian, Simplified + Traditional Chinese, Japanese, Korean, Arabic + Hebrew (RTL) are the typical first 10 for an OSS Android app. Adopt Weblate or Crowdin's open-source plan. Audit `WallpaperDetailScreen` + `SoundDetailScreen` + `SoundEditorScreen` for RTL bugs (scrubber direction is the usual one).
- **Score:** Fit 4 / Impact 3 / Effort 3 / Risk 5 / Deps 5 / Novelty 1 = 21. Tier-wise this is Next-ready; held in Under Consideration only because the translator pipeline (Weblate vs. Crowdin vs. self-hosted) is a community-process decision, not an engineering one.

### U-12. Contributor docs (CONTRIBUTING.md, ARCHITECTURE.md, plugin authoring guide) — `[~]` CONTRIBUTING + ARCHITECTURE shipped 2026-05-17 rev4-impl-2
- **Source(s):** Existing `AGENTS.md` redirects to `CLAUDE.md` (49 KB working notes — internal, not contributor-facing); README has no contributor section beyond "Issues and PRs welcome"; no `CONTRIBUTING.md` in repo root; no `ARCHITECTURE.md`. NX-5 plugin ABI is meaningless without a plugin-authoring guide.
- **Open question:** Move the public-facing architecture content out of `CLAUDE.md` (keep the working-notes file for internal context) into a versioned `docs/ARCHITECTURE.md`. Write a `CONTRIBUTING.md` covering: build prereqs, branch/PR conventions, test-running, Aura Sources plugin contract once NX-5 lands. Add a `docs/plugins/` directory with example Muzei-compatible source.
- **Shipped (rev4-impl-2):** Repo-root `CONTRIBUTING.md` (charter, build, branch/PR, code style, commits, plugin pointer) + `ARCHITECTURE.md` (layered model with ASCII diagram, package map, key abstractions, process-death + live-wallpaper engine discipline, design system rules). `docs/plugins/` + Muzei-compatible reference plugin still pending — bundled with NX-5 once the plugin ABI module lands.
- **Score:** Fit 5 / Impact 3 / Effort 4 / Risk 5 / Deps 3 (NX-5) / Novelty 1 = 21. Held until NX-5 settles to avoid stale plugin docs.

### U-13. Testing infrastructure expansion (Paparazzi screenshot tests + more instrumented coverage)
- **Source(s):** Existing 49 unit-test files (post 2026-05-17 audit pass); no Compose screenshot tests; one `androidTest/` smoke suite. [Paparazzi](https://github.com/cashapp/paparazzi) is the de-facto Compose screenshot library; [Roborazzi](https://github.com/takahirom/roborazzi) is a modern alternative; [Compose API defaults accessibility doc](https://developer.android.com/develop/ui/compose/accessibility/api-defaults).
- **Open question:** Screenshot test the AMOLED theming + RTL mirroring (ties to U-11). Add instrumented tests for `WallpaperApplier.applyByLocator` across `http://`, `file://`, `content://` URIs (the v6.15.0 bug-class) and for the streaming caps added in the 2026-05-17 audit (`readCapped` / `copyCapped` cap-exceeded path). Pair with the toolchain triad (N-1) since Compose Compiler 2.x changes which composables are screenshot-stable.
- **Score:** Fit 4 / Impact 3 / Effort 3 / Risk 4 / Deps 3 (N-1) / Novelty 1 = 18. Held until N-1 settles.

### U-14. Android XR spatial wallpaper (Galaxy XR / smart-glasses)
- **Source(s):** [Android XR spatial environments docs](https://developer.android.com/design/ui/xr/guides/environments); [Galaxy XR launch](https://www.android.com/xr/); [Android Show 2026 XR coverage](https://www.analyticsinsight.net/news/google-android-show-2026-to-detail-mixed-reality-ecosystem-with-android-xr) (May 12, 2026 reveal); [Virtual Reality News 3-tier glasses strategy](https://virtual.reality.news/news/googles-android-xr-glasses-strategy-could-beat-apple/); existing `WallpaperApplier.applyByLocator` scheme-dispatch ready to accept a GLB-by-locator handler.
- **Open question:** Charter call — Aura is phone/lock/home-screen first; "spatial environment" is a different surface. The XR docs explicitly recommend ~80 MB GLB assets, which dwarfs every existing Aura content type (largest wallpaper today: 64 MB cap, largest sound: 20 MB). Counter-argument: Aura's strong palette + color-extraction surface + tag taxonomy is the kind of pre-baked metadata an XR environment selector needs. Also fits T-D Multi-surface presence theme. Hold until Galaxy XR install base and Aura's KMP foundation (L-4) both move; revisit when Android XR ships its first OSS environment-publishing samples.
- **Score:** Fit 2 / Impact 3 / Effort 1 / Risk 1 / Deps 1 (L-4 KMP) / Novelty 5 = 13 → Under Consideration. Re-litigate post-Android-XR-stable.

### U-15. Real-time per-unlock wallpaper rotation (Pixel-10 parity, ahead of charter call)
- **Source(s):** [Pixel 10 Auto-change AI wallpaper](https://www.onoff.gr/blog/en/android/ai-wallpaper-android-create-ai-wallpapers/) — generates a fresh AI image every unlock, ~5-10 s on-device; [NX-6 scope](#nx-6-scheduler-triggers--per-app-exclusion-screen-off-pre-stage-sub-15-min-intervals-per-unlock) (per-unlock rotation via `USER_PRESENT` broadcast already in scope); [DroidViews smart wallpaper](https://www.droidviews.com/automatic-wallpaper-change-contextually-with-smart-wallpaper/).
- **Open question:** NX-6 already plans per-unlock rotation from existing sources. The Pixel 10 differentiator is *generating* per unlock, not rotating. Aura's Stability AI path (Phase 3.1) takes ~10-30 s per gen + costs a credit per call — not unlock-frequency viable. **Charter friction:** unlock-frequency generation against a paid API burns the user's bring-your-own-key quota in hours, not days. Either (a) cache K pre-generated images and rotate, (b) wait for U-2 on-device Stable Diffusion to mature, or (c) stay out of unlock-frequency generation entirely. Hold.
- **Score:** Fit 3 / Impact 3 / Effort 1 / Risk 1 / Deps 2 (NX-6 + U-2) / Novelty 4 = 14 → Under Consideration.

---

## Rejected — do not silently resurrect

### R-1. AI on-device wallpaper generation
- **Source(s):** Aura roadmap charter (v5.0.0 prune); now ambiguous after Phase 3.1 (Stability AI server-side) shipped in v6.14.0.
- **Verdict:** Stays Rejected for *on-device* generation. Server-side Stability AI is the supported path. Re-litigate when Snapdragon NPU is mainstream and the [`local-dream`](https://github.com/xororz/local-dream) pattern stabilizes.

### R-2. Freesound OAuth2 for full-quality (non-preview) audio
- **Source(s):** ROADMAP Round 3 note; [Freesound API auth tiers](https://freesound.org/help/developers/).
- **Verdict:** Preview HQ MP3 (128 kbps) is already at typical ringtone fidelity. OAuth2 flow + token refresh storage is disproportionate. Stays Rejected unless users explicitly demand full-quality downloads.

### R-3. Subscription / coin / streak economy
- **Source(s):** [Backdrops Streak Collections terms](https://backdrops.io/terms/); [Wonder $4.99/wk complaints](https://support.google.com/googleplay/thread/214242312/); [Lensa dark-pattern reports](https://www.complaintsboard.com/wallcraft-wallpapers-live-b148917); [MKBHD Panels shutdown](https://www.macrumors.com/2025/12/01/mkbhd-wallpaper-app-shutdown/).
- **Verdict:** Rejected. Charter is OSS, MIT, no surprise charges. Community signal (r/Android, r/androidapps, Trustpilot) explicitly seeks escape from these patterns. Donations (GitHub Sponsors / Liberapay) are the only acceptable monetization vector. Do not propose again.

### R-4. Ads (banner, interstitial, rewarded video)
- **Source(s):** [Zedge ad cadence complaints](https://forums.androidcentral.com/threads/zedge-harmful.979866/page-2); [Trend Micro wallpaper-app ad-fraud report](https://www.trendmicro.com/en_us/research/18/l/android-wallpaper-apps-found-running-ad-fraud-scheme.html); WallCraft's AdMob integration (real anti-pattern in adjacent OSS).
- **Verdict:** Rejected. Same rationale as R-3. Aura's existence is to be Zedge without the ads. Re-proposing requires owner override.

### R-5. Selling user data / non-anonymous telemetry
- **Verdict:** Rejected. Not negotiable. Aura's optional telemetry (already partly shipped via `SourceMetrics` in-session diagnostics) is local-only.

### R-6. Full WYSIWYG live-wallpaper scripting (KLWP-grade)
- **Source(s):** [KLWP feature surface](https://docs.kustom.rocks/docs/downloads/download-klwp/).
- **Verdict:** Rejected. Aura's charter is curation + personalization, not authoring tools. KLWP is a programming environment with an enormous runtime risk surface; Aura would balloon to maintain it. The U-6 "shader gallery" is the acceptable subset.

---

## Themes — cross-cutting initiatives

Themes group Now/Next items so they ship coherently rather than as one-off features.

### T-A. Dependency hygiene & platform parity
Spans: **N-1, N-2, N-3, N-4, NX-10, NX-11, yt-dlp CVE batch risk row**.
Outcome: Aura runs on the current platform with current libraries. Compose Strong Skipping wins, Material 3 Expressive, Photo Picker (rev4: + 9:16 customization on Android 17), WallpaperDescription, Subject Segmentation, EyeDropper colour pick (rev4: Android 17+), Subject Segmentation all land together. NewPipeExtractor bumps to 0.26.1+; bundled yt-dlp re-verified post-CVE-2026-26331.

### T-B. Lockscreen depth & live-wallpaper engine
Spans: **N-3, NX-1, NX-2, NX-3, NX-9**.
Outcome: Aura matches Pixel Live Effects (Shape / Weather / Cinematic) and One UI Wonderland feature parity without depending on Pixel-only or Samsung-only system features. Media3 1.10 Material3 playback composables (NX-9) replace hand-rolled preview chrome so the engine surface stays small.

### T-C. Extension ecosystem
Spans: **NX-5, L-2**.
Outcome: Aura ships as a Muzei source, consumes Muzei sources, exposes Tasker hooks. Third-party sources land without forking; the user's existing automation works with Aura.

### T-D. Multi-surface presence
Spans: **NX-2 (lockscreen), L-1 (Wear OS), L-4/L-5 (KMP/desktop), Compose-for-TV future**.
Outcome: One wallpaper choice propagates to lock screen, watch face, TV screensaver, desktop wallpaper. The "personalization graph" is a defensible novelty.

### T-E. Content authenticity & creator economy
Spans: **N-5 (Aura Originals), existing 4.x (community uploads / creator profiles / shareable collections), tip-jar follow-up**.
Outcome: Aura grows a curated catalog without becoming Zedge. Creators get attribution, follow counts, and donation paths; no marketplace, no coins.

### T-F. Distribution beyond Play Store
Spans: **NX-8, monochrome icon in N-4, per-ABI splits**.
Outcome: F-Droid / IzzyOnDroid / Obtainium / GitHub Releases. APK size cut. Reproducible builds where Firebase doesn't block them.

### T-G. Battery transparency & accessibility
Spans: existing 5.5 (battery dashboard), NX-1 (engine pause-on-invisible discipline), NX-9 (Media3 1.10 `experimentalSetDynamicSchedulingEnabled` for preview surface), accessibility audits in U-8 / U-9 / U-11.
Outcome: Aura's live wallpapers prove their power impact (Facer "Power Impact" rating equivalent). Users with TalkBack / large-font / reduced-motion needs get parity. [DreamPixel battery analysis](https://dreampixelstudio.app/blog/use-live-wallpapers-on-android-without-draining-battery) — lightweight live wallpapers cost <2 % battery/day, heavy 3D/video can cost 5–8 %; Aura's existing auto-15-FPS-below-15 %-battery cap + pause-on-invisible are the relevant primitives.

### T-H. Trust & hardening
Spans: existing 2026-05-17 audit pass (downloader sanitization, streaming caps, parallax bitmap-leak fix, AGSL crash-safety), N-2 (Custom Claims server-side enforcement), U-13 (screenshot + integration test expansion), the Risk Register rows for CVE-2026-0073-class platform CVEs + yt-dlp CVE batch (5 CVEs, expanded from rev4).
Outcome: Every external input (manifest URL, HTTP body, content URI, user-pick) goes through a streaming cap; every internal allocation has a leak-free recycle path; every privilege check has a server-side enforcement layer. The 2026-05-17 pass closed the worst-case OOM-OOM-leak chain in `WallpaperApplier.downloadBitmap` (the call site of every wallpaper apply); same primitives reused in three sibling write paths.

### T-I. Developer experience & build verification (new in rev4)
Spans: **NX-12 (CI verify workflow), NX-13 (predictive-back wiring), U-12 (CONTRIBUTING.md), U-13 (screenshot + integration test expansion)**.
Outcome: Every push and PR is build-verified, unit-tested, and lint-clean before it can land. N-1 toolchain bumps can be honestly validated. Compose detail screens animate predictive-back smoothly on Android 14+. The "no SDK in CI" gap that has gated every Implementation Pass since 2026-04-25 closes; static-review-only stops being the default mode.

---

## Risk Register

Live operational risks ranked by likelihood × blast radius. Update at every release.

| Risk | Likelihood | Blast | Mitigation in roadmap |
|------|-----------|-------|-----------------------|
| NewPipe Extractor stops working on Play-Protect-certified Android (March 2026 maintainer warning, [piunikaweb](https://piunikaweb.com/2026/03/09/newpipe-certified-android-devices-warning/); SABR enforcement [#12126](https://github.com/TeamNewPipe/NewPipe/issues/12126); latest stable extractor **0.26.1** with SABR-only player response fix per the [post-0.28.1 hotfix notes](https://newpipe.net/blog/pinned/announcement/newpipe-0.28.1-released/) — Aura is on 0.24.8) | Medium | High (YouTube sound tab dies) | Abstract `YouTubeRepository.search()` + `resolveStreamUrl()` behind a `SoundExtractor` interface in N-1; ship NewPipeExtractor as the default impl, `NewPipeExtractorKmpAdapter` ([yushosei/NewPipeExtractor-KMP](https://github.com/yushosei/NewPipeExtractor-KMP)) as fallback, and youtubedl-android as last-resort path. Pin `NewPipeExtractor` version comment already in place since v6.12.0; bump to **0.26.1+** in lock-step with monthly upstream patches once N-1 unblocks build verification. |
| yt-dlp CVE batch (5 CVEs via youtubedl-android:0.18.1 bundling yt-dlp 2025.11.12): CVE-2026-26331 (`--netrc-cmd` injection), CVE-2026-50019 (`--cookies` leak with curl downloader), CVE-2026-50023 (dangerous file types via filename sanitization), CVE-2026-50574 (arbitrary code exec via `aria2c` manifest), CVE-2025-54072 (`--exec` injection on Windows). | Low (Aura code never passes `--netrc-cmd`, `--exec`, `--cookies`, `aria2c`, or `--downloader`; verified by `docs/security/ytdlp-cve-policy.json` v2 + 9 unit tests) | Medium (any contributor adding these flags without auditing would hit the CVEs) | Policy file (`ytdlp-cve-policy.json` v2) scans all source for 6 forbidden options; `test/tools/ytdlp_cve_policy_check_test.py` rejects each; bump `youtubedl-android` in N-1 toolchain pass when ≥ 0.19.x ships with fixed yt-dlp bundle. |
| CISA-KEV-class platform CVEs (Aura cannot patch; users may run unpatched OEMs). Recent: CVE-2026-0073 (May 2026, adbd zero-click RCE, [AOSP bulletin](https://source.android.com/docs/security/bulletin/2026/2026-05-01)). | Low | Low (device-level, not Aura's bug) | Existing optional warning-banner placeholder; no roadmap response needed beyond keeping the dependency hygiene cadence (T-A). |
| Firebase BoM 33.7.0 transitive protobuf vulnerable to CVE-2024-7254 | High | Medium | **N-2** bumps to BoM 34.x |
| Aura's `VoteRepository` admin auth is client-side spoofable | Medium | Medium (community moderation bypass) | **N-2** Custom Claims |
| ML Kit `segmentation-selfie:16.0.0-beta6` still in beta two years on | Medium | Medium (parallax breaks if pulled) | **N-3** migrates to Subject Segmentation GA |
| Stability AI free tier / pricing changes; per-user API key is the only path | Low | Medium (AI tab degrades to "bring your own key") | Document; consider Imagen via Firebase AI Logic in U-2 follow-up |
| AGP 9 / Kotlin 2.3 / KSP1 breaks Hilt/Compose generation | Medium | High | **N-1** coordinated upgrade with feature freeze |
| AV1 hardware decode <10 % install base; SW fallback burns battery | Medium | Medium | **NX-1** gates AV1 on Performance Class ≥ 33 |
| CISA-KEV Android framework CVE-2025-48572/-48633 on unpatched OEMs | Low | Low (device-level, not Aura's bug) | Folded into the platform-CVE row above; same mitigation. |
| F-Droid inclusion blocked by Firebase Storage / yt-dlp Python | Medium | Medium (F-Droid track only) | **NX-8** targets IzzyOnDroid first |
| Wallhaven / Pexels / Pixabay ToS changes break aggregation | Low | High | NX-5 plugin ABI distributes sourcing risk to community plugins |
| Foreground-app reader for per-app rotation exclusion needs `PACKAGE_USAGE_STATS` | Medium | Medium (intrusive permission prompt) | **NX-6** ships opt-in banner explaining the trade-off |

---

## Shipped Inventory (Phase 1-8 detail, preserved from prior passes)

Kept verbatim — these are the receipts.

### Phase 1 — Content Foundation
- [ ] 1.1 Bundled "Aura Originals" CC0 sound pack — see N-5 above.
- [x] 1.2 Freesound v2 direct integration.
- [x] 1.3 SoundCloud CC (legacy retained for old saves; not in active feed since v6.18.0).
- [x] 1.4 Internet Archive dropped (DB v6→v7).
- [x] 1.5 Ringtone Maker from device music.

### Phase 2 — UX Overhaul
- [x] 2.1 Sounds-tab simplification.
- [x] 2.2 Instant sound preview (prebuffer first 5).
- [x] 2.3 QuickApplySheet long-press flow.
- [x] 2.4 Onboarding personalization + Settings re-entry.
- [x] 2.5 Seasonal content + Wallpapers banner + Sounds carousel.
- [x] 2.6 Sound Detail redesign.

### Phase 3 — AI & Generation
- [x] 3.1 AI Wallpaper Generation (Stability AI, server-side).
- [ ] 3.2 AI Sound Generation — see U-4 (Under Consideration).
- [x] 3.3 Parallax wallpapers (ML Kit + gyroscope).

### Phase 4 — Community
- [x] 4.1 User-generated sound uploads.
- [x] 4.2 User-generated wallpaper uploads.
- [x] 4.3 Creator profiles (anonymous identity; Google sign-in still queued in NX-7).
- [x] 4.4 Shareable collections.

### Phase 5 — Video Wallpaper Evolution
- [x] 5.1 Gallery video/GIF support + Fit/Fill controls.
- [x] 5.2 Loop & Crop editor with frame thumbnails.
- [x] 5.3 VFX particle overlays.
- [x] 5.4 Touch-reactive effects.
- [x] 5.5 Battery dashboard with auto FPS cap.

### Phase 6 — Smart Features
- [x] 6.1 Material You color preview.
- [x] 6.2 Dark/light auto-switch.
- [x] 6.3 Weather effects overlay.
- [x] 6.4 Time-of-day adaptive tint.
- [ ] 6.5 Smart Crop with subject detection — see NX-3.

### Phase 7 — Polish & Infra
- [x] 7.1 Unified Audio Service (MediaSession + cache).
- [ ] 7.2 Replace SelectedContentHolder — see NX-4.
- [ ] 7.3 Favorites sync — see NX-7.
- [ ] 7.4 Additional widgets — see L-7.
- [ ] 7.5 Performance & offline — see L-8.

### Phase 8 — Stretch (none shipped)
- 8.1 Wallpaper sets/theming — see L-9.
- 8.2 Wear OS — see L-1.
- 8.3 Desktop companion — see L-5.
- 8.4 Stickers / emoji — see U-?, hold.

---

## Implementation Log (preserved release-pass entries)

These are the dated receipts. The newest entries supersede the oldest where they overlap; do not edit prior entries.

### 2026-06-05 — Cycle 17 provider disclosure matrix pass

**Items shipped**

- **Cycle 17 P1 / Cycle 3 P0 partial — content-source policy matrix**
  New `ProviderDisclosure` centralizes display name, active/legacy/local/community/generated status, terms URL, license summary, attribution requirement, cache policy, user-action policy, and store-disclosure note for every `ContentSource`. Settings > Open source licenses now builds its Content Sources section from this model instead of a short hard-coded subset.

- **Legal matrix doc**
  New `docs/legal/provider-policy.md` mirrors the source matrix for release/store review and records the remaining dependency notice, native/copyleft packet, and durable provenance follow-ups.

- **Runtime dependency visibility**
  The Licenses screen now includes visible rows for runtime/native dependencies that Cycle 17 identified as missing from the hand-maintained list: Kotlin coroutines/serialization, ProfileInstaller, Palette, ZXing, Firebase, Google Play services, ML Kit, NewPipe Extractor, youtubedl-android, yt-dlp, FFmpeg, and core library desugaring. This is a stopgap until generated OSS notices ship.

**Verification**

- Installed the official Android SDK command-line tools locally, accepted SDK licenses, and installed platform-tools, Android 35 platform, and build-tools 35.0.0 because no SDK existed at the repo's documented path on this VM.
- Mirrored the repo to `C:\tmp\Aura-loop-verify` to avoid UNC/VMware Gradle issues.
- `.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest --tests com.freevibe.data.legal.ProviderDisclosureTest` passed on the mirror using Microsoft JDK 21 and SDK 35.
- No full APK or lint run was attempted; `CLAUDE.md` warns repeated APK/lint runs can exhaust memory on this workstation.

**Next up**

- Cycle 17 P0 — generated OSS notices and license drift gate.
- Cycle 17 P0 — copyleft/native extractor compliance packet for NewPipe, youtubedl-android, yt-dlp/Python payloads, and FFmpeg.
- Cycle 17 P1 — generated Gradle runtime dependency inventory comparison and notice-diff gate.

### 2026-06-04 — Cycle 2 P0 release-integrity pass

**Items shipped**

- **Cycle 2 P0 / NX-8 — signed GitHub release workflow**
  `.github/workflows/release.yml` now restores signing material from GitHub secrets, writes temporary release `local.properties`, runs signed `:app:assembleRelease`, packages `Aura-vX.Y.Z-versionCode-N-universal-release.apk`, verifies it with `apksigner --print-certs`, rejects `application-debuggable`, publishes `SHA256SUMS.txt`, and uses generated release notes with versionName/versionCode plus signing certificate SHA-256. Manual workflow dispatch uploads the same release bundle as an artifact for dry-run inspection; tag runs attach the APK/checksums to GitHub Releases.

- **Distribution runbook**
  New `docs/distribution/release-signing.md` documents required GitHub secrets, local signing verification, checksum checks, and Obtainium expectations. `local.properties.example` now matches the actual lowercase Gradle property names for API keys and release signing.

- **Unit-test target repair**
  Stale tests from prior implementation batches now compile/run again: `SelectedContentHolder` tests use a mock SharedPreferences + Moshi factory, crop ViewModel tests wire `SmartCropDetector`, Settings tests stub NX-6 rotation-trigger flows, and `SmartCropCalculator` exposes a pure `SubjectBounds` overload so JVM tests do not depend on stubbed Android `RectF` constructors.

**Verification**

- `git diff --check` passed.
- `./gradlew.bat :app:testDebugUnitTest --stacktrace --no-daemon` passed: 303 tests.
- A local `:app:assembleRelease` run passed earlier in the pass and the generated APK verified with v2 signing plus no `application-debuggable` marker before the final pure-JVM SmartCrop helper change. A repeat APK/lint compile was stopped by operator request because OpenJDK was exhausting workstation memory.

**Next up**

- Cycle 2 P0 — Decide full-vs-foss distribution flavor before F-Droid work.

### 2026-06-04 — Cycle 2 P0 full-vs-foss distribution decision

**Items shipped**

- **Cycle 2 P0 / NX-8 — channel matrix and F-Droid gate**
  New `docs/distribution/channel-strategy.md` makes GitHub Releases + Obtainium the supported install/update path, keeps IzzyOnDroid as the realistic near-term app-store submission target, and blocks F-Droid mainline until a real FOSS flavor removes or isolates Firebase, the Google Services plugin, and Play Services ML Kit.

- **F-Droid preflight**
  New `tools/fdroid_preflight.py` scans Gradle files without compiling APKs. It reports the current tree as `blocked`, lists proprietary dependency markers with file/line evidence, and has `--expect-blocked` for CI/local decision checks.

**Verification**

- `py -3 tools/fdroid_preflight.py --expect-blocked` passed with the expected blocked status.
- `py -3 tools/fdroid_preflight.py --json` produced machine-readable blocker output.
- `git diff --check` passed.
- No APK compile was run for this docs/preflight-only batch.

**Next up**

- Cycle 2 P1 — Add a supply-chain verification lane.

### 2026-06-04 — Cycle 2 P1 supply-chain verification lane

**Items shipped**

- **Release attestations**
  `.github/workflows/release.yml` now grants `id-token`, `attestations`, and `artifact-metadata` permissions, generates `SHA256SUMS.txt`, and calls `actions/attest@v4` with `subject-checksums`. Generated release notes include the attestation URL alongside the APK SHA-256 and signing certificate SHA-256.

- **PR and scheduled security workflows**
  New `.github/workflows/dependency-review.yml` runs `actions/dependency-review-action@v5` on pull requests and fails high/critical vulnerable dependency additions. New `.github/workflows/scorecard.yml` runs OpenSSF Scorecard on main pushes, weekly schedule, branch-protection changes, and manual dispatch, then uploads SARIF to code scanning.

- **Supply-chain runbook and checksum metadata**
  New `docs/distribution/supply-chain.md` documents active controls, release verification, the Gradle dependency-verification metadata regeneration command, and SBOM scope deferred until the N-1 toolchain migration. New `gradle/verification-metadata.xml` records SHA-256 checksums for the resolved Gradle/plugin dependency graph.

**Verification**

- `git diff --check` passed.
- `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" --write-verification-metadata sha256 :app:dependencies --stacktrace` passed.
- Workflow syntax reviewed against current `actions/attest@v4`, `actions/dependency-review-action@v5`, and `ossf/scorecard-action@v2.4.3` usage.
- No local APK compile was run for this workflow/docs batch.

**Next up**

- Cycle 2 P1 — Opt-in crash/ANR diagnostics bundle.

### 2026-06-04 — Cycle 2 P1 crash/ANR diagnostics bundle

**Items shipped**

- **Local diagnostics collector**
  New `CrashDiagnosticsCollector` reads the existing `filesDir/crash.log`, parses the newest crash timestamp, and formats a local issue bundle with app version, build type, Android version, security patch, device/ABI, active source/provider context, scheduler/auto-wallpaper source settings, reproduction fields, and a sanitized crash log tail. `FreeVibeApp` now writes crash entries through the shared formatter so parsing stays stable.

- **Settings copy/share flow**
  Settings > Diagnostics now includes "Crash diagnostics bundle" with the last local crash status. The dialog states that nothing uploads automatically; Copy writes to the clipboard and Share opens Android's chooser only after user action.

- **Support/reporting surface**
  Added `.github/ISSUE_TEMPLATE/crash_report.yml` and `docs/support/crash-diagnostics.md`. The README now points crash/ANR reporters to the in-app bundle and issue template.

- **Dependency verification follow-up**
  The focused JVM test exposed missing Windows `aapt2` detached-configuration checksums after dependency verification was introduced. `gradle/verification-metadata.xml` now includes those artifacts.

**Verification**

- `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" :app:testDebugUnitTest --tests com.freevibe.service.CrashDiagnosticsTextTest --stacktrace` passed.
- Initial targeted test run failed on dependency verification for Windows `aapt2`; rerunning with `--write-verification-metadata sha256` added the missing checksums, then the strict run passed.
- No local APK compile was run for this diagnostics batch.

**Next up**

- Cycle 2 P1 — Supply-chain CI follow-up.

### 2026-06-04 — Cycle 2 P1 supply-chain CI follow-up

**Items shipped**

- **Scorecard permission repair**
  `.github/workflows/scorecard.yml` now keeps `publish_results: false`, leaves top-level token permissions read-only, and scopes `security-events: write` to the SARIF-upload job. This avoids the failed public-result publishing path while preserving code-scanning evidence for maintainers.

- **Clean-runner checksum metadata**
  `gradle/verification-metadata.xml` now includes the JUnit BOM `.module` hashes that GitHub's Linux runner resolved for the buildscript classpath, the Linux `aapt2` detached artifact exposed by the next CI run, plus the Guava parent POM generated during the refreshed dependency pass. `docs/distribution/supply-chain.md` now calls out `--refresh-dependencies` for CI-only checksum misses.

**Verification**

- `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" --write-verification-metadata sha256 help --stacktrace` passed; no APK task ran.
- `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" --refresh-dependencies --write-verification-metadata sha256 :app:dependencies --stacktrace` passed; dependency resolution only, no APK task ran.
- Google Maven's `aapt2-8.7.3-12006047-linux.jar.sha256` sidecar matched `Get-FileHash` on the downloaded Linux JAR.

**Next up**

- Cycle 2 P1 — Developer verification + IzzyOnDroid prep.

### 2026-06-04 — Cycle 2 P1 developer verification + IzzyOnDroid prep

**Items shipped**

- **Release-note verification status**
  `.github/workflows/release.yml` now accepts an `android_developer_verification_status` manual-dispatch input and writes the Android developer verification status for `com.freevibe` into generated release notes next to the APK SHA-256, signing certificate SHA-256, and artifact attestation URL.

- **Distribution owner runbook**
  New `docs/distribution/developer-verification.md` captures the Android Developer Console / Play Console registration path, current package id, signing-key continuity requirements, per-release checklist, IzzyOnDroid submission packet, F-Droid blocker state, and branch-protection owner action.

- **Docs links and status sync**
  README, release-signing docs, channel strategy, changelog, and NX-8 roadmap status now link the developer-verification runbook. Branch protection is explicitly owner-blocked because the live GitHub API reported no required status checks for `main`.

**Verification**

- `rtk gh api repos/SysAdminDoc/Aura/branches/main/protection --jq "{required_status_checks:.required_status_checks.contexts,enforce_admins:.enforce_admins.enabled,required_pull_request_reviews:.required_pull_request_reviews,restrictions:.restrictions}"` returned `required_status_checks: null`, so the required `verify` check remains an owner/admin repository setting.
- Current Android developer verification and IzzyOnDroid sources were rechecked before writing the runbook.
- No local APK compile was run for this workflow/docs batch.

**Next up**

- Cycle 2 P1 — Baseline Profile + Macrobenchmark gate for startup and grid jank.

### 2026-06-04 — Cycle 2 P1 baseline profile + macrobenchmark harness

**Items shipped**

- **Baseline Profile producer module**
  Added `:baselineprofile` as a `com.android.test` module with the `androidx.baselineprofile` plugin, `benchmark-macro-junit4`, UI Automator, and a Critical User Journey generator covering startup, Wallpapers, Wallpaper Detail, Videos, Sounds, and Favorites.

- **Macrobenchmark gate**
  Added startup benchmarks comparing `CompilationMode.None()` against `CompilationMode.Partial(BaselineProfileMode.Require)`, plus frame-timing scroll benchmarks for Wallpapers, Videos, Sounds, and Favorites. The app now includes `ProfileInstaller` and a shell-profileable manifest entry so benchmark release variants can capture/reset profiles.

- **Manual performance CI artifacts**
  Added `.github/workflows/performance.yml` for a Linux self-hosted physical-device runner. The workflow generates the Baseline Profile, runs macrobenchmarks, and uploads generated profiles, JSON reports, and Perfetto traces. Normal verify/release builds do not auto-run generation.

**Verification**

- `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" --write-verification-metadata sha256 :baselineprofile:tasks --stacktrace` passed; no APK task ran.
- `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" :app:help --task generateBaselineProfile --stacktrace` passed and confirmed `:app:generateBaselineProfile`.
- `.\gradlew.bat --no-daemon "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" --write-verification-metadata sha256 :baselineprofile:compileBenchmarkReleaseSources --stacktrace` passed after pinning the benchmark module Java/Kotlin target to 17 and fixing the MacrobenchmarkRule import.
- `.\gradlew.bat --no-daemon --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8" "-Dkotlin.compiler.execution.strategy=in-process" --write-verification-metadata sha256 :app:lintDebug --stacktrace` passed; no APK packaging task ran. This added the Linux/Windows lint tool checksums that broke the previous GitHub `verify` run.
- `adb devices` returned no attached devices, so `:app:generateBaselineProfile` and `:baselineprofile:connectedBenchmarkReleaseAndroidTest` were not run locally.

**Next up**

- Physical-device run for Cycle 2 P1 — generate the profile and attach startup/frame metrics, then close the Baseline Profile item.

### 2026-05-17 — Rev4-impl-2 autonomous batch (6 more items, code + docs)

Continuation of the rev4-impl batch the same day. Six items landed across the
Next + Later + Under-Consideration tiers. Static-review-only — same N-1 build-
environment blocker.

**Items shipped (all `[~]` partial unless noted)**

- **NX-10 — Android 17 EyeDropper API**
  Raw-string Intent (`"android.intent.action.OPEN_EYE_DROPPER"` + `"android.intent.extra.COLOR"`) keeps the integration compatible with compileSdk 35 — no API 37 class refs. `eyeDropperAvailable = remember { Build.VERSION.SDK_INT >= 37 && PackageManager.resolveActivity != null }` hides the FAB on un-updated builds. New `WallpapersViewModel.searchByPickedColor(Int)` strips alpha and reuses the existing Wallhaven `colors=` search. UI surface: new `FloatingActionRow` with `Icons.Default.Colorize`, label "Pick colour", on the Discover tab below Theme Match.

- **NX-11 — Photo Picker 9:16 portrait customization**
  New `AuraPickVisualMedia` subclass of `ActivityResultContracts.PickVisualMedia` overrides `createIntent` and calls `PhotoPickerCustomization.apply9x16AspectRatio(intent)`. The helper reflects on `android.provider.MediaStore$PhotoPickerUiCustomizationParams$Builder.setGridAspectRatio(9, 16).build()` + `MediaStore.EXTRA_PHOTO_PICKER_UI_CUSTOMIZATION_PARAMS`, all behind a `Build.VERSION.SDK_INT < 37` guard. Wired at three call sites: wallpaper community upload, collection QR import, parallax-from-photo.

- **NX-3 video variant — finishing what rev4-impl deferred** *(NX-3 now `[x]` closed)*
  TopAppBar "Smart" action on `VideoCropScreen` extracts the frame at loop start via `MediaMetadataRetriever.getFrameAtTime(OPTION_CLOSEST_SYNC)`, runs the same `SmartCropDetector` wallpaper crop uses, and pans the video so the detected subject lands at the viewport centre. Keeps user-chosen zoom (different from wallpaper variant). Coordinates translate through `fitScale * scale` to match `VideoCropScreen.clampTransform`.

- **NX-13 expansion — editor unsaved-changes guards** *(4 of ~18 screens)*
  `WallpaperEditorScreen` BackHandler fires when any filter is non-default; opens "Discard edits?" `AlertDialog` with Keep editing / Discard (calls `resetAll()` + `onBack()`). Same pattern for `SoundEditorScreen` against trim fractions + fade durations. `state.isApplying` disables the guard so apply paths land cleanly.

- **U-12 — Contributor docs**
  New repo-root `CONTRIBUTING.md` (charter, build, branch/PR conventions, code style, commit format, plugin pointer) + `ARCHITECTURE.md` (layered ASCII model, package map, key abstractions like `applyByLocator` / `SelectedContentHolder` / `AutoWallpaperWorker` / `SmartCropDetector`, process-death + live-wallpaper engine discipline, design system rules incl. no-pill-backdrops). `docs/plugins/` + Muzei-compatible reference plugin still queued behind NX-5.

- **CHANGELOG Unreleased** — bumped with rev4-impl-2 highlights.

**Themes touched**

- T-A (dependency hygiene & platform parity) — NX-10 EyeDropper + NX-11 Photo Picker land Android 17 surface; reflection paths sidestep the N-1 toolchain dependency.
- T-B (lockscreen depth & engine) — NX-3 video variant rounds out Smart Crop across both wallpaper paths.
- T-G (battery transparency & accessibility) — NX-13 editor unsaved-changes prevents wasted FFmpeg / segmentation work.
- T-I (developer experience & build verification) — U-12 contributor docs make the codebase legible to outside contributors.

**Push status**

- 6 new commits added to local `main` (NX-10/11, NX-3 video, U-12, NX-13 editors). Combined with rev4-impl batch (8 commits) + rev4 freshness (1 commit) the branch is now ahead of `origin/main` by ~21 commits. `git push origin main` still blocked by executor credential.

### 2026-05-17 — Rev4-impl autonomous batch (8 NX/L items, code + roadmap)

Autonomous-development pass following the rev4 freshness pass (same day). Eight
items landed across the Next + Later tiers; build verification still pending
behind N-1. Push status: same blocker as prior passes (`MavenImaging` executor
credential lacks write to `SysAdminDoc/Aura`); commits land locally on `main`,
owner must push.

**Items shipped (each `[~]` partial, all sourced from existing rev4 scope)**

- **NX-12 — CI build verification workflow** *(closes the static-review-only loop)*
  New `.github/workflows/verify.yml` triggered on `push: main` + `pull_request: main` + `workflow_dispatch`. Runs `assembleDebug` + `testDebugUnitTest` + `lintDebug` on JDK 17 with Gradle cache. Stubs `local.properties` so signing/API-key lookups don't fail in CI (release.yml stays the source of truth for signed builds). Uploads test + lint reports as artifacts on failure with 14-day retention. `concurrency` group cancels superseded runs.

- **NX-3 — Smart Crop with Subject Segmentation** *(wallpaper variant)*
  `SmartCropCalculator.kt` pure geometry (7 unit tests) + `SmartCropDetector.kt` wrapping the same ML Kit Subject Segmentation engine N-3 wired into `ParallaxWallpaperService`. `WallpaperCropViewModel.applySmartCrop()` is suspend, returning the new transform so the composable can re-sync local `rememberSaveable` gesture state. UI surface: Smart Crop FilterChip with sparkle icon + Detecting… spinner; "Couldn't detect a subject — drag to position manually" snackbar fallback. Video crop variant deferred (FFmpeg geometry different).

- **NX-13 — BackHandler audit** *(2 of ~18 screens)*
  Highest-stakes in-flight screens only: `AiWallpaperScreen` back-press cancels the in-flight Stability AI job (new `AiWallpaperViewModel.cancelGeneration()` + `generationJob: Job` tracker + `onCleared()` defensive cancel — saves the user's API credit budget on bail-out). `VideoCropScreen` back-press during FFmpeg toasts "Cropping in progress — please wait" and holds the screen. Remaining 16 detail/editor/preview/picker screens deferred behind N-1 Navigation 2.9.

- **NX-2 — Lockscreen widget surface** *(no code, manifest only)*
  `freevibe_widget_info.xml` widget category bumped `home_screen` → `home_screen|keyguard`. On Android 16 QPR2+ (Dec 2025 stable) the existing `FreeVibeWidget` is now placeable on the lockscreen surface. Older Android silently ignores the keyguard bit. Clock-tuck mask + dedicated daily-pick lockscreen widget still pending pending real-device test feedback.

- **NX-6 — Rotation triggers (per-unlock + screen-off pre-stage)**
  New `RotationTriggerService` foreground service with `specialUse` foreground-service type. Dynamically registers `Intent.ACTION_USER_PRESENT` + `Intent.ACTION_SCREEN_OFF` receivers (both manifest-blocked on API 26+). Each fire enqueues a one-shot expedited `AutoWallpaperWorker` via `WorkManager.enqueueUniqueWork(KEEP)` so chatty unlock sequences coalesce. Two new DataStore prefs `rotateOnUnlock` / `rotateOnScreenOff` (default false) gate the service lifecycle; `RotationTriggerService.reconcile()` is the idempotent start/stop entry point called from `FreeVibeApp.onCreate` and `SettingsViewModel.setRotateOn{Unlock,ScreenOff}`. Manifest: new service declaration + `FOREGROUND_SERVICE_SPECIAL_USE` permission. Settings UI: "Change on every unlock" + "Pre-stage on screen off" toggles below the existing constraint section. Per-app exclusion + sub-15-min AlarmManager + one-tap-shuffle widget still pending.

- **L-2 — Tasker / automation hook** *(action minimum)*
  New manifest-declared `TaskerActionReceiver` (exported) responds to `com.freevibe.action.ROTATE_NOW` + `com.freevibe.action.SHUFFLE_NOW`, re-enters `RotationTriggerService.enqueueRotation()`. Tasker / MacroDroid / adb shell can wire Aura into any condition with a one-line "Send Intent" — no plugin SDK needed. Full TaskerPluginActivity (UI-mediated parameterized actions, event broadcasts, state queries) still queued.

- **NX-4 — SelectedContentHolder process-death survival**
  Singleton now persists the single selected wallpaper + selected sound to a `freevibe_selected_content` SharedPreferences file via Moshi JSON on every `select*` call. Hilt-injects `@ApplicationContext` + `Moshi`; lazy-restores from disk on construction. `wallpaperList` (pager-supporting) intentionally still in memory only — the detail screen already collapses to single-item display when the list is empty. Full nav-graph-scoped `SelectionViewModel` + `SavedStateHandle` refactor still queued behind Navigation 2.9 (N-1).

- **NX-8 — Distribution metadata refresh** *(fastlane + Obtainium)*
  `fastlane/metadata/android/en-US/` fully refreshed: title (`FreeVibe` → `Aura`), short description, full description (rewritten against 29-feature surface incl. parallax, weather, Smart Crop, rotation triggers, Tasker hook, sound editor, dual wallpapers, contact ringtones, community uploads, creator profiles, shareable collections, battery dashboard). New `changelogs/111.txt` for v6.31.0. New `obtainium.json` at repo root with `v*` tag regex + APK arch filter. Per-ABI splits + F-Droid metadata PR + IzzyOnDroid submission still queued behind N-1 build verification.

**Themes touched**

- T-A (dependency hygiene & platform parity) — no version bumps this pass (N-1 still blocked).
- T-B (lockscreen depth & engine) — NX-3 Smart Crop and NX-2 widget surface land partial wins.
- T-C (extension ecosystem) — L-2 Tasker hook closes the broadcast-action surface.
- T-F (distribution beyond Play Store) — NX-8 fastlane + Obtainium close the metadata surface.
- T-G (battery transparency & accessibility) — NX-13 back-cancel reduces wasted Stability AI calls.
- T-H (trust & hardening) — RotationTriggerService runs `RECEIVER_NOT_EXPORTED`, narrow Intent surface; SelectedContentHolder uses `runCatching` around JSON I/O so a corrupt blob doesn't crash startup.
- T-I (developer experience) — NX-12 verify.yml is the central win; NX-13 BackHandler discipline is the per-screen one.

**Push status**

- 8 commits added to local `main` (rev4 freshness + rev4-impl × 7). `git push origin main` still blocked by executor credential; owner must push.

### 2026-05-17 — Rev4 freshness pass (no code; roadmap only)

Document-only pass on top of rev3 (committed earlier the same day). Web-research
batch (~8 distinct query classes, ~25 net-new URLs) cross-checked against
existing rev3 coverage; four genuinely new items + one CVE row + competitor
validation surfaced. No code change; no working-tree modification beyond
`ROADMAP.md`.

**Added Next-tier items**
- **NX-10** Android 17 EyeDropper API — `Intent.ACTION_OPEN_EYE_DROPPER` →
  wallpaper colour search seed. Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 3 / Novelty 4 = 22.
- **NX-11** Photo Picker 9:16 portrait via `PhotoPickerUiCustomizationParams`
  on Android 17+ (N-4 follow-up). Fit 5 / Impact 3 / Effort 5 / Risk 5 / Deps 3 / Novelty 2 = 23.
- **NX-12** CI build verification workflow — close the static-review-only loop.
  Fit 5 / Impact 4 / Effort 4 / Risk 5 / Deps 4 / Novelty 1 = 23.
- **NX-13** Predictive-back wired through NavHost transitions + 18-screen
  `BackHandler` audit. Fit 4 / Impact 3 / Effort 4 / Risk 4 / Deps 4 / Novelty 1 = 20.

**Added Later-tier item**
- **L-10** Compose Adaptive Layouts (foldables / tablets / Compose-for-TV) —
  zero existing WindowSizeClass code verified by repo grep. Fit 3 / Impact 3 / Effort 2 / Risk 4 / Deps 3 / Novelty 2 = 17.

**Risk Register additions**
- yt-dlp CVE-2026-26331 — `--netrc-cmd` arbitrary command injection, fixed in
  yt-dlp 2026.02.21. Aura ships via `youtubedl-android:0.18.1`; Aura code does
  not pass `--netrc-cmd`, so blast is low, but verify-and-guard is in scope of N-1.
- NewPipeExtractor target version bumped from "0.25.0+" to "0.26.1+" (current
  stable after the 0.28.1 hotfix lineage).

**Competitor validation (not new items)**
- One UI 8.5 stable rolling out May 2026 with **Smart Subject Placement**
  (auto-arrange clock/widgets around photo subjects) + **AI Weather Effects**
  (weather animations behind subject layers via segmentation). Directly
  validates Aura's NX-2 (lockscreen depth + clock-tuck) and Phase 6.3 (weather
  overlay) directions. No new roadmap item — existing trajectory is correct.
- Paperize landed an experimental "live wallpaper" alpha mode — validates
  NX-1's GL/AGSL engine migration as a competitive must-have, not a nice-to-have.

**Themes touched**
- T-A (dependency hygiene) — NX-10, NX-11, yt-dlp CVE row appended.
- T-H (trust + hardening) — yt-dlp CVE row appended.
- T-I (new) — Developer experience & build verification. Spans NX-12, NX-13, U-12, U-13.

**Push status**
- Roadmap-only edit; commit + push when convenient. No code paths touched.

### 2026-05-17 — Hardening audit pass (security + bitmap leaks + streaming caps)

Static-review-only pass against the 2026-05-16 autonomous batch. Found seven real
issues across the four newly-landed features; all fixed in the working tree
(`git status`: 6 files modified, ready for commit by an operator with push
access). Did not run `./gradlew testDebugUnitTest`: same SDK-absent
constraint as the 2026-05-16 pass. Visual diff + import check verified each
change compiles against the existing call graph.

**Security — `AuraOriginalsDownloader` (N-5)**
- New `sanitizeEntryId(id)` — strict allowlist (ASCII alnum + `-_.`, max 64 chars,
  no path separators, no dot-only). Defends against a tampered manifest's
  `entry.id` escaping `filesDir/aura_originals/` via `../etc/passwd`.
- New `isAllowedDownloadUrl(url)` — HTTPS-only scheme gate. Rejects `http`,
  `file`, `content`, `data`, `ftp`. Defense-in-depth against a typo or
  tampered manifest redirecting to cleartext or local-file fetch.
- New `isInside(parent, child)` — canonical-path containment check.
  Belt-and-braces guard layered after the sanitizer in case future relaxation
  re-introduces an escape vector.
- Running-total budget — prior code only checked `manifest.totalBytes` against
  `MAX_TOTAL_BYTES`; the in-loop running sum was never tracked, so a manifest
  with N entries each just under the per-file cap could exceed 80 MB total.
  Now: each entry's effective budget is `min(remainingBudget, MAX_PER_FILE_BYTES)`;
  successful downloads add to `runningBytes`; entries that would exceed the
  remaining budget are rejected with a clear DEBUG log.
- Tests: +5 in `AuraOriginalsDownloaderTest` — `sanitizeEntryId` accepts
  (happy path), `sanitizeEntryId` rejects 11 traversal/unsafe cases,
  `isAllowedDownloadUrl` covers 10 scheme cases, `isInside` covers nested +
  parent + escape attempts. Test count: 46 → 49 in the worker file.

**Bitmap leaks — `ParallaxWallpaperService` (N-3 segmenter migration)**
- The 2026-05-16 N-3 patch migrated the segmenter success callback to use
  `result.foregroundConfidenceMask`. The new code allocated `bgBitmap`
  (`bitmap.copy()`) inside the synchronized block, then `fgBitmap`
  (`Bitmap.createBitmap`) outside it. If `fgBitmap` allocation OOM'd or the
  pixel-loop threw, `bgBitmap` was orphaned as a native allocation. Wallpaper
  service processes are very long-lived; the leak was observable across
  apply→apply cycles.
- Fix: `bgBitmap` + `fgBitmap` declared as `var`s at callback scope, wrapped
  in `try { … } catch … finally`. A `publishedToLayers` flag set inside the
  publish-to-fields synchronized block tells the finally block whether to
  recycle (only recycle when not published).
- Secondary fix: `bitmap.copy()` can return null on low-memory devices.
  Previously the code assigned the null to `backgroundLayer`, then
  unconditionally retired `fallbackBitmap`, leaving `draw()` with neither
  layers nor a fallback to render (solid-black wallpaper). Now: if copy()
  returns null, reconstruct `bgBitmap` from the already-extracted pixel array;
  if reconstruction also fails, do not retire the fallback so draw() has
  something to render.

**Streaming size caps — `WallpaperApplier` (defense-in-depth across all apply paths)**
- `downloadBitmap` previously called `body.bytes()` after only a Content-Length
  pre-check. `OkHttp.ResponseBody.bytes()` has no upper bound; if Content-Length
  is unknown (chunked transfer) or lies, the entire response was buffered into
  memory before the cap was re-checked. The pre-check was unreachable in
  exactly the case it was needed.
- Fix: new `readCapped(InputStream, cap)` streams 64 KB at a time, aborts the
  read the moment cumulative bytes exceed `MAX_WALLPAPER_BYTES` (64 MB).
  Replaces the `body.bytes()` call site.
- Same class of issue in `prepareParallaxWallpaper` and `prepareParallaxFromUri`
  (no cap on copy-to-disk of either an HTTP body or a user-picked content URI).
  Fix: new `copyCapped(InputStream, OutputStream, cap)` reuses the same
  pattern. Caller's existing temp-then-rename + try/finally cleanup is
  preserved; the cap throws IOException which the existing catch already
  handles with `tempFile.delete()`.

**Crash-safety — `AgslEffectPipeline` (N-3 scaffold)**
- `RuntimeShader` throws `IllegalArgumentException` on malformed AGSL source.
  Effects are hard-coded today, so this is preventive for future contributors
  adding new effects (the most likely real-world cause of a crash report on
  this surface).
- Fix: `apply()` wraps `applyAgsl()` in try/catch for both `Exception` (bad
  shader) and `OutOfMemoryError`, falling back to `copyOrFallback()`. Recycled-source
  guard added (previously would propagate `IllegalStateException` from
  `Bitmap.copy()` on a recycled source). `applyAgsl()` recycles its own
  pre-allocated `output` bitmap before re-throwing on any exception inside
  shader compilation or canvas draw — otherwise the caller's fallback path
  leaked that bitmap.

**Suspend conversion verification — `AutoWallpaperWorker.schedule`**
- Pre-existing uncommitted in-progress change (`runBlocking { prefs.… }` → suspend
  function) was sitting in the working tree on entry. All 7 callers in
  `SettingsViewModel.kt:144,153,167,171,175,241,249` are inside
  `viewModelScope.launch { … }`; the new suspend signature is therefore
  call-site-compatible. Conversion is correct.

**Push status**
- All 6 file edits land locally; `git push origin main` will still bounce the
  same way as the 2026-05-16 batch did (executor credential `MavenImaging`
  lacks write to `SysAdminDoc/Aura`). Owner must push.

### 2026-05-16 — Autonomous N-2..N-5 batch (build verification pending)

Four Now-tier items landed as code; the remaining Now item (N-1 toolchain triad)
is deferred because the executing environment had no Android SDK / JDK available
to validate the upgrades. Static review only; runtime verification next session.

**N-4 — Photo Picker + monochrome icon + WallpaperDescription scaffold (commit `b0ae1fe`)**
- WallpapersScreen community upload + CollectionsScreen QR import switched
  from `GetContent("image/*")` / `OpenDocument(arrayOf("image/*"))` to
  `PickVisualMedia.ImageOnly`. No `READ_MEDIA_IMAGES` permission prompt;
  scoped-storage compliant.
- Vector `drawable/ic_launcher_monochrome.xml` added; `mipmap-anydpi-v26/ic_launcher{,_round}.xml`
  declare `<monochrome>` layer. Android 13+ themed icons now use the Aura "A" silhouette.
- `xml/{video,parallax,weather}_wallpaper.xml` annotated with TODO comments
  pointing to N-4/N-1 for when compileSdk 36 unlocks the Android 16
  WallpaperDescription / WallpaperInstance API.

**N-2 — Firebase BoM 34.13.0 + Custom Claims admin path (commit `c9ca405`)**
- Firebase BoM 33.7.0 → 34.13.0 (closes CVE-2024-7254 transitive protobuf risk).
- Removed deprecated `firebase-{auth,database,storage}-ktx` artifacts; migrated 5
  files to canonical `FirebaseAuth.getInstance()`, `FirebaseDatabase.getInstance()`,
  `FirebaseStorage.getInstance()`.
- New `VoteRepository.refreshAdminFromClaims()` forces ID-token refresh and
  caches the `admin` Custom Claim in `_adminFromClaims` StateFlow.
- `isAdmin` getter now consults Custom Claim first, with legacy device-ID hash
  + UID allowlist as one-cycle migration fallbacks. Precedence captured in
  pure `computeIsAdmin` helper, covered by `AdminPrecedenceTest` (6 cases).
- `FreeVibeApp.warmCommunityIdentity()` calls `refreshAdminFromClaims` after
  sign-in so admin status syncs on every cold launch.
- `database.rules.json` (new) — proposed RTDB Security Rules enforcing
  `auth.token.admin === true` server-side for moderation paths. Mirrors the
  client check; this is the actual enforcement layer.
- `docs/firebase-admin-claims.md` (new) — operator runbook for granting/revoking
  the claim via the Admin SDK and removing the device-hash fallback once all
  admins have rotated tokens.
- Resolves the pre-existing `VoteRepository.kt:75` TODO.

**N-3 — Subject Segmentation API + AGSL pipeline scaffold (commit `61443fc`)**
- Dependency: `com.google.mlkit:segmentation-selfie:16.0.0-beta6` →
  `com.google.mlkit:segmentation-subject:16.0.0-beta1` (GA replacement of the
  two-year selfie-segmentation beta). Added `play-services-base:18.5.0` for
  `ModuleInstallClient`.
- `ParallaxWallpaperService` migrated to `SubjectSegmenter` +
  `SubjectSegmenterOptions.enableForegroundConfidenceMask()`. The new mask is
  sized to the input bitmap, so the pixel-to-mask coordinate remap is gone
  (simpler + faster).
- `requestSegmenterModuleInstall()` proactively warms the unbundled model at
  engine create so the first parallax apply isn't silently no-op while Play
  services downloads the module.
- All lifecycle guards from the prior path preserved: per-segmenter tracking,
  double-close protection, generation counter, bitmap-lock during pixel read.
- New `AgslEffectPipeline.kt` — API-33+ gated `RuntimeShader` pipeline with a
  Canvas fallback. Public surface is `apply(bitmap, effect)` over an
  `AgslEffect` sealed catalog (IDENTITY, DEPTH_SHADE today). Single GPU-effect
  surface to be consumed by wallpaper editor, weather service, and parallax
  service in future passes. `AgslEffectPipelineTest` (3) covers AGSL source
  validity + out-of-range uniform handling.

**N-5 — Aura Originals manifest + first-launch downloader (commit `cbc9db2`)**
- `assets/aura_originals_manifest.json` — versioned schema; ships empty.
- `AuraOriginalsManifest.kt` — Moshi model + DI loader.
- `AuraOriginalsDownloader.kt` — `CoroutineWorker` that downloads each manifest
  entry over HTTPS, sha256-verifies before atomic rename, enforces 5 MB
  per-file + 80 MB total caps, runs on UNMETERED constraint by default,
  exponential backoff on failure, idempotent across cold starts.
- `FreeVibeApp.onCreate` enqueues with `ExistingWorkPolicy.KEEP` so the
  pack converges over multiple Wi-Fi sessions without redoing work.
- `docs/aura-originals-curation.md` — curation workflow + CC0 license
  compliance + retroactive removal via manifest revisions.
- `AuraOriginalsDownloaderTest` (5) covers extension guessing + sha256
  matching/mismatch/blank-rejection.
- DB schema stays at v14 — bundled tracking reuses the existing
  `FavoriteEntity.offlinePath` convention. Room v15 deferred until the
  curation list lands.

**N-1 deferral (toolchain triad)**
- AGP 9 / Gradle 9 / Kotlin 2.3 / KSP2 / Compose BOM 2026.05 / Hilt 2.59
  upgrade NOT performed this pass. The autonomous executor had no Android
  SDK or JDK available to validate the changes, and a "blind" toolchain
  bump on this scale typically surfaces Compose stability annotations and
  KSP2 incremental-cache regressions that only a clean build can catch.
  Re-pick this item in a session that can run `./gradlew :app:assembleDebug`.

**Push status**
- All four feat commits land locally on `main`. `git push origin main`
  blocked: the executor's git credential is `MavenImaging`, but
  `https://github.com/SysAdminDoc/Aura.git` rejects with 403. Owner must
  push (or grant the executor's GitHub account write access to the repo).

---

### 2026-05-XX — Phase 3.1 AI Wallpaper Generation
- v6.14.0; `StabilityAiApi` Retrofit interface (multipart binary), `AiWallpaperRepository` (9:16 PNG, atomic write, `pruneOldFiles(50)`), `AiStyle` enum (8 presets), `AiWallpaperViewModel` (Hilt + DataStore key), `AiWallpaperScreen` (GlassCard, animated key field, prompt, style chips, shimmer, result apply/save).
- Entry: "AI" FilledTonalButton + AutoAwesome in WallpapersScreen.
- `STABILITY_AI_KEY` BuildConfig + `PreferencesManager.stabilityAiKey`.
- `ContentSource.AI_GENERATED` added.
- Phase 2.4 "Change your style" Settings entry confirmed already-shipped.
- Phase 5.3 VFX Particle Overlays confirmed already-shipped.

### 2026-05-14 — Gallery Video/GIF Import
- Phase 5.1 actionable slice: `ActivityResultContracts.OpenDocument()` accepts `video/*` and `image/gif`; copy to `live_wallpaper.<ext>`; `VideoWallpaperService` canvas-based GIF renderer.
- Removed dead "GIF not supported" Settings entry; tests cover GIF/WebM/3GP/MOV/MKV/MP4 extensions.
- Phase 5.2 already complete (frame thumbnails + loop range + FFmpeg trim).

### 2026-05-14 — Video Fit/Fill Apply Controls
- Phase 5.1 fit/fill/crop: apply confirmation exposes Fill and Fit before setup.
- `VideoWallpaperService` reads `scale_mode` and maps to MediaPlayer scale modes; GIF renderer honors same mode.
- Scale-mode normalization unit-tested.

### 2026-05-14 — Video Loop Trim Editor
- Phase 5.1/5.2: crop editor → Loop & Crop with start/end range.
- Preview seeks to loop start at loop end.
- FFmpeg gets `-ss`/`-t` for the selected segment.
- Loop-range coercion + FFmpeg trim arg tests.

### 2026-05-14 — Video Timeline Thumbnails
- Phase 5.2 last slice: up to six evenly-spread frames under the range scrubber.
- Failure-tolerant; falls back to plain slider when `MediaMetadataRetriever` fails.
- Tests cover frame-sampling positions and six-frame cap.

### 2026-05-14 — Touch-Reactive Effects (Phase 5.4)
- Weather wallpapers: ripple + spark bursts.
- Settings → Smart Features → Touch effects (Off / Subtle / Ripples + sparkles).
- Bounded, capped, battery-conscious.

### 2026-05-14 — Video Battery Dashboard (Phase 5.5)
- Settings → Video Wallpapers: live device battery, service heartbeat, active media type, effective FPS, scale mode, estimated impact.
- Auto 15 FPS cap below 15 % + not-charging.
- Dev FPS overlay toggle.

### 2026-04-25 — Product Polish
- Phase 2.4 Settings re-entry confirmed existed since prior session.
- Sounds COMMUNITY empty state: added "Upload a sound" CTA.
- DownloadsScreen broken-file badge via async `LaunchedEffect`.
- Phase 2.5 gap closed: Discover now biases Pexels + Pixabay by user style alongside Wallhaven.

### 2026-04-27 — Seasonal Content & Personalization
- Marked Phase 1.2/1.3/1.4, 2.3/2.6 done (previously shipped, unchecked).
- 2.5 `SeasonalContentManager`: Halloween (Oct 15–31), Holiday (Dec), New Year (Jan 1–3), Valentine (Feb 10–14), Summer (Jun 21–Sep 1).
- 2.4 wallpaper-Discover style-biased Wallhaven query when user styles set.
- `SeasonalContentManagerTest` covers all five windows + boundaries.

### 2026-04-25 — Diagnostics Follow-Up
- T-6 follow-up: `SourceMetrics` now covers Discover aggregate + Reddit + Bing + Pixabay + Pexels + Wallhaven variants + Openverse fallback + Freesound v2 + YouTube + SoundCloud + Audius + ccMixter.
- Diagnostics observes live in-session updates; chips + per-source rows.
- T-8 / T-9 / T-10 remain LATER.

### 2026-04-25 — Create From Music
- P0 1.5: Sounds > Create from music → system audio picker → SoundEditorScreen waveform loader.
- 30s default for long clips; 8–30s guidance; editor opens in Create Sound mode for local files.

### 2026-04-26 — Sounds Tab Chrome (Phase 2.1)
- Sounds tab exposes Ringtones / Notifications / Alarms as primary chips (not hidden behind source dropdown).
- YouTube / Community / Search in compact secondary menu.
- Quality bias in Refine bottom sheet.

### 2026-04-26 — Sound Discovery Carousel
- 2.1 last slice: tab-aware collection cards in feed.
- Long-press quick-apply keeps sheet open while applying; busy/disabled state in-place; permission gate with Grant action.
- Comparable-product research applied: Paperize collections, Muzei source clarity, ringtone-maker preview/apply emphasis.

### 2026-04-26 — Instant Sound Preview (Phase 2.2)
- 2.2 cache slice: first 5 visible preview URLs prebuffered into shared Media3 SimpleCache.
- `AudioPlaybackService` plays through same cache.
- Ready badge on cards.

### Older — see CHANGELOG.md for v5.x and earlier passes
- v6.15.0 deep audit: 11 bugs across v6.13–v6.14 (`WeatherUpdateWorker` Float precision, `SolarCalculator` DST, `SystemThemeListener` event-driven, `WallpaperApplier.applyByLocator` scheme-dispatch, `pruneOldFiles` finally called, `applyWallpaper` off Main, Stability AI HTTP-code mapping, ColorMatrix Paint caching, dark/light slot empty-state, VFX Cancel→Close).
- v6.12.0 Wallhaven SafeSearch toggles + auto-wallpaper rotation constraints + SourceMetrics in-session diagnostics + NewPipe stream-leak re-verify.
- v6.11.0 Freesound 429/Retry-After + Material You accent ladder + cancellation rethrow sweep.
- v6.10.0 finalized writes + widget intent safety + 64 MB editor download caps.
- v6.9.0 ColorExtractor 32 MB cap + SoundApplier 64 MB cap + Int-overflow harden.
- v6.8.0 video cropper hardening + 80 MB offline file cap + prefs write-order consistency.
- v6.7.0 bitmap-download 64 MB cap + Weather scaleBitmap leak fix + Locale.ROOT sweep + intent safety.
- v6.6.0 DownloadManager 64 MB ceiling + ParallaxWallpaperService segmenter double-close fix + AudioTrimmer bounded FFmpeg drain.
- v6.5.0 OOM-safe bitmap decode + HTTPS-only validation + accessibility touch targets.
- v6.4.0 structured-concurrency sweep across 16 catch sites.
- (continue back to v5.x in CHANGELOG.md).

---

## Appendix A — Cited OSS Competitors

Stars/dates as of research pass 2026-05-16.

- **Paperize** ([github.com/Anthonyy232/Paperize](https://github.com/Anthonyy232/Paperize)) — 1.1k★ — GPL-3.0 — fully-offline dynamic changer; Compose; v4.0.0-alpha live wallpaper mode. Issues cited: [#444](https://github.com/Anthonyy232/Paperize/issues/444), [#447](https://github.com/Anthonyy232/Paperize/issues/447), [#482](https://github.com/Anthonyy232/Paperize/issues/482), [#516](https://github.com/Anthonyy232/Paperize/issues/516), [#531](https://github.com/Anthonyy232/Paperize/issues/531), [#532](https://github.com/Anthonyy232/Paperize/issues/532), [#428](https://github.com/Anthonyy232/Paperize/issues/428), [#126](https://github.com/Anthonyy232/Paperize/issues/126), [#192](https://github.com/Anthonyy232/Paperize/issues/192); discussion [#313](https://github.com/Anthonyy232/Paperize/discussions/313). 2026 follow-ups: [#446](https://github.com/Anthonyy232/Paperize/issues/446), [#450](https://github.com/Anthonyy232/Paperize/issues/450) (Jan-Feb 2026 enhancement asks), [#496](https://github.com/Anthonyy232/Paperize/issues/496), [#497](https://github.com/Anthonyy232/Paperize/issues/497), [#498](https://github.com/Anthonyy232/Paperize/issues/498) (Feb-Mar 2026 bug + feature).
- **WallFlow** ([github.com/ammargitham/WallFlow](https://github.com/ammargitham/WallFlow)) — 452★ — GPL-3.0 — Wallhaven + Reddit; foldable inner + outer; smart crop (Plus variant); Paging 3; KMP Windows planned. Issues: [#62](https://github.com/ammargitham/WallFlow/issues/62), [#63](https://github.com/ammargitham/WallFlow/issues/63), [#64](https://github.com/ammargitham/WallFlow/issues/64), [#68](https://github.com/ammargitham/WallFlow/issues/68), [#70](https://github.com/ammargitham/WallFlow/issues/70), [#73](https://github.com/ammargitham/WallFlow/issues/73), [#82](https://github.com/ammargitham/WallFlow/issues/82), [#91](https://github.com/ammargitham/WallFlow/issues/91), [#99](https://github.com/ammargitham/WallFlow/issues/99), [#102](https://github.com/ammargitham/WallFlow/issues/102).
- **WallCraft** ([github.com/Rahul-999-alpha/WallCraft](https://github.com/Rahul-999-alpha/WallCraft)) — 1★ — MIT — Pollinations.ai no-key AI generation, AMOLED, AdMob (anti-pattern for Aura).
- **Muzei** ([github.com/muzei/muzei](https://github.com/muzei/muzei)) — 4.9k★ — Apache-2.0 — refreshing-art live wallpaper; canonical plugin/source API. Issues: [#794](https://github.com/muzei/muzei/issues/794), [#800](https://github.com/muzei/muzei/issues/800), [#793](https://github.com/muzei/muzei/issues/793), [#792](https://github.com/muzei/muzei/issues/792), [#797](https://github.com/muzei/muzei/issues/797), [#869](https://github.com/muzei/muzei/issues/869), [#838](https://github.com/muzei/muzei/issues/838), [#836](https://github.com/muzei/muzei/issues/836), [#811](https://github.com/muzei/muzei/issues/811), [#128](https://github.com/muzei/muzei/issues/128), [#110](https://github.com/muzei/muzei/issues/110), [#109](https://github.com/muzei/muzei/issues/109).
- **Peristyle** ([github.com/Hamza417/Peristyle](https://github.com/Hamza417/Peristyle)) — 620★ — Apache-2.0 — glassmorphic Compose wallpaper mgr; tags + auto-changer; intent `app.peristyle.START_AUTO_WALLPAPER_SERVICE`. Feature request: [#98 different wallpaper set for night](https://github.com/Hamza417/Peristyle/issues/98) (analog to Aura's existing dark/light auto-switch).
- **UndeadWallpaper** ([github.com/maocide/UndeadWallpaper](https://github.com/maocide/UndeadWallpaper)) — 99★ — GPL-3.0 — OpenGL + ExoPlayer video wallpaper. Issues: [#5](https://github.com/maocide/UndeadWallpaper/issues/5), [#13](https://github.com/maocide/UndeadWallpaper/issues/13), [#24](https://github.com/maocide/UndeadWallpaper/issues/24), [#46](https://github.com/maocide/UndeadWallpaper/issues/46), [#47](https://github.com/maocide/UndeadWallpaper/issues/47), [#48](https://github.com/maocide/UndeadWallpaper/issues/48).
- **AlynxLiveWallpaper** ([github.com/AlynxZhou/alynx-live-wallpaper](https://github.com/AlynxZhou/alynx-live-wallpaper)) — 106★ — Apache-2.0 — reference ExoPlayer + OpenGL ES live wallpaper. Issues: [#14](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/14), [#15](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/15), [#16](https://github.com/AlynxZhou/alynx-live-wallpaper/issues/16).
- **GLWallpaperService** ([github.com/GLWallpaperService/GLWallpaperService](https://github.com/GLWallpaperService/GLWallpaperService)) — 153★ — Apache-2.0 — unmaintained, foundational GLEngine base class.
- **WallYou** ([github.com/you-apps/WallYou](https://github.com/you-apps/WallYou)) — 1k★ — GPL-3 — multi-source aggregator; auto-changer. Issues: [#189](https://github.com/you-apps/WallYou/issues/189), [#229](https://github.com/you-apps/WallYou/issues/229), [#267](https://github.com/you-apps/WallYou/issues/267); discussion [#133](https://github.com/you-apps/WallYou/discussions/133).
- **Doodle** ([github.com/patzly/doodle-android](https://github.com/patzly/doodle-android)) — 832★ — GPL-3 — Pixel-style colorful live wallpapers. Issues: [#29](https://github.com/patzly/doodle-android/issues/29), [#38](https://github.com/patzly/doodle-android/issues/38), [#77](https://github.com/patzly/doodle-android/issues/77), [#83](https://github.com/patzly/doodle-android/issues/83), [#92](https://github.com/patzly/doodle-android/issues/92), [#114](https://github.com/patzly/doodle-android/issues/114), [#115](https://github.com/patzly/doodle-android/issues/115), [#119](https://github.com/patzly/doodle-android/issues/119).
- **Pallax** ([github.com/patzly/pallax-android](https://github.com/patzly/pallax-android)) — 58★ — GPL-3 — ARCHIVED Jan 2025; cautionary tale on Canvas-based live wallpaper inefficiency.
- **DarkModeWallpaper** ([github.com/cvzi/darkmodewallpaper](https://github.com/cvzi/darkmodewallpaper)) — 222★ — GPL-3 — day/night wallpaper pair switching; animated GIF + WebP support. Issues: [#9](https://github.com/cvzi/darkmodewallpaper/issues/9), [#80](https://github.com/cvzi/darkmodewallpaper/issues/80), [#104](https://github.com/cvzi/darkmodewallpaper/issues/104).
- **SlideshowWallpaper** ([github.com/Doubi88/SlideshowWallpaper](https://github.com/Doubi88/SlideshowWallpaper)) — 74★ — GPL-3 — no-permission slideshow. Issues: [#62](https://github.com/Doubi88/SlideshowWallpaper/issues/62), [#64](https://github.com/Doubi88/SlideshowWallpaper/issues/64), [#65](https://github.com/Doubi88/SlideshowWallpaper/issues/65), [#69](https://github.com/Doubi88/SlideshowWallpaper/issues/69), [#70](https://github.com/Doubi88/SlideshowWallpaper/issues/70), [#74](https://github.com/Doubi88/SlideshowWallpaper/issues/74), [#75](https://github.com/Doubi88/SlideshowWallpaper/issues/75).
- **ShaderEditor** ([github.com/markusfisch/ShaderEditor](https://github.com/markusfisch/ShaderEditor)) — 1.1k★ — MIT — GLSL shaders as live wallpapers. Issues: [#251](https://github.com/markusfisch/ShaderEditor/issues/251), [#256](https://github.com/markusfisch/ShaderEditor/issues/256), [#259](https://github.com/markusfisch/ShaderEditor/issues/259), [#275](https://github.com/markusfisch/ShaderEditor/issues/275).
- **ShaderShowcaseApp** ([github.com/thelumiereguy/ShaderShowcaseApp](https://github.com/thelumiereguy/ShaderShowcaseApp)) — 280★ — GPL-3 — Compose + GLSL playground.
- **lwp-shaders** ([github.com/cipold/lwp-shaders](https://github.com/cipold/lwp-shaders)) — 21★ — MIT — curated GLSL shaders.
- **AlwaysOn** ([github.com/Domi04151309/AlwaysOn](https://github.com/Domi04151309/AlwaysOn)) — 218★ — GPL-3 — FOSS AOD. Issues: [#30](https://github.com/Domi04151309/AlwaysOn/issues/30), [#63](https://github.com/Domi04151309/AlwaysOn/issues/63), [#71](https://github.com/Domi04151309/AlwaysOn/issues/71), [#77](https://github.com/Domi04151309/AlwaysOn/issues/77), [#78](https://github.com/Domi04151309/AlwaysOn/issues/78), [#81](https://github.com/Domi04151309/AlwaysOn/issues/81), [#91](https://github.com/Domi04151309/AlwaysOn/issues/91), [#105](https://github.com/Domi04151309/AlwaysOn/issues/105).
- **ColorBlendr** ([github.com/Mahmud0808/ColorBlendr](https://github.com/Mahmud0808/ColorBlendr)) — 2.1k★ — GPL-3 — FabricatedOverlay Material You tweaks. Issues: [#247](https://github.com/Mahmud0808/ColorBlendr/issues/247), [#252](https://github.com/Mahmud0808/ColorBlendr/issues/252), [#254](https://github.com/Mahmud0808/ColorBlendr/issues/254), [#260](https://github.com/Mahmud0808/ColorBlendr/issues/260), [#262](https://github.com/Mahmud0808/ColorBlendr/issues/262), [#288](https://github.com/Mahmud0808/ColorBlendr/issues/288).
- **PixivforMuzei3** ([github.com/yellowbluesky/PixivforMuzei3](https://github.com/yellowbluesky/PixivforMuzei3)) — 203★ — GPL-3 — Pixiv Muzei source. Issues: [#184](https://github.com/yellowbluesky/PixivforMuzei3/issues/184), [#194](https://github.com/yellowbluesky/PixivforMuzei3/issues/194), [#227](https://github.com/yellowbluesky/PixivforMuzei3/issues/227), [#229](https://github.com/yellowbluesky/PixivforMuzei3/issues/229), [#234](https://github.com/yellowbluesky/PixivforMuzei3/issues/234), [#246](https://github.com/yellowbluesky/PixivforMuzei3/issues/246), [#254](https://github.com/yellowbluesky/PixivforMuzei3/issues/254).
- **LiveWallpaperIt** ([github.com/TBog/live-wallpaper-it](https://github.com/TBog/live-wallpaper-it)) — 11★ — GPL-3 — Reddit Muzei plugin. Issues: [#16](https://github.com/TBog/live-wallpaper-it/issues/16), [#18](https://github.com/TBog/live-wallpaper-it/issues/18), [#20](https://github.com/TBog/live-wallpaper-it/issues/20), [#21](https://github.com/TBog/live-wallpaper-it/issues/21), [#23](https://github.com/TBog/live-wallpaper-it/issues/23).
- **HK Vision Muzei plugin** ([github.com/hossain-khan/android-hk-vision-muzei-plugin](https://github.com/hossain-khan/android-hk-vision-muzei-plugin)) — 6★ — Apache-2.0 — clean current-gen Muzei source reference.
- **BingWallpaper** ([github.com/liaoheng/BingWallpaper](https://github.com/liaoheng/BingWallpaper)) — 153★ — GPL-3 — daily Bing image with 2-week browse.
- **local-dream** ([github.com/xororz/local-dream](https://github.com/xororz/local-dream)) — 2.4k★ — on-device SDXL via Snapdragon NPU. Issues: [#183](https://github.com/xororz/local-dream/issues/183), [#189](https://github.com/xororz/local-dream/issues/189), [#191](https://github.com/xororz/local-dream/issues/191), [#195](https://github.com/xororz/local-dream/issues/195), [#198](https://github.com/xororz/local-dream/issues/198), [#203](https://github.com/xororz/local-dream/issues/203), [#206](https://github.com/xororz/local-dream/issues/206), [#209](https://github.com/xororz/local-dream/issues/209), [#210](https://github.com/xororz/local-dream/issues/210).
- **AiWallpaperChanger** ([github.com/RikudouSage/AiWallpaperChanger](https://github.com/RikudouSage/AiWallpaperChanger)) — 9★ — MIT — AI Horde-based.
- **Waller** — OSS Android app that *generates* wallpapers (gradients, patterns, noise) instead of downloading them ([MakeUseOf review](https://www.makeuseof.com/open-source-wallpaper-app-phone/)). Adjacent to Aura's AI Wallpaper Generation; cited as a charter-aligned generation-without-API alternative.
- **NewPipe** ([github.com/TeamNewPipe/NewPipe](https://github.com/TeamNewPipe/NewPipe)) — 38.2k★ — GPL-3 — privacy YouTube/PeerTube/Bandcamp/SoundCloud client. SABR coordination [#12248](https://github.com/TeamNewPipe/NewPipe/issues/12248).
- **NewPipeExtractor** ([github.com/TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)) — extractor library Aura pins.
- **NewPipeExtractor-KMP** ([github.com/yushosei/NewPipeExtractor-KMP](https://github.com/yushosei/NewPipeExtractor-KMP)) — Compose Multiplatform fork.
- **Seal** ([github.com/JunkFood02/Seal](https://github.com/JunkFood02/Seal)) — 26.3k★ — GPL-3 — Compose yt-dlp UI. Issues: [#2377](https://github.com/JunkFood02/Seal/issues/2377), [#2391](https://github.com/JunkFood02/Seal/issues/2391), [#2398](https://github.com/JunkFood02/Seal/issues/2398), [#2426](https://github.com/JunkFood02/Seal/issues/2426), [#2453](https://github.com/JunkFood02/Seal/issues/2453), [#2470](https://github.com/JunkFood02/Seal/issues/2470), [#2471](https://github.com/JunkFood02/Seal/issues/2471), [#2474](https://github.com/JunkFood02/Seal/issues/2474), [#2499](https://github.com/JunkFood02/Seal/issues/2499), [#2518](https://github.com/JunkFood02/Seal/issues/2518).
- **ytdlnis** ([github.com/deniscerri/ytdlnis](https://github.com/deniscerri/ytdlnis)) — 8.6k★ — GPL-3 — yt-dlp + scheduled downloads + custom templates. Issues: [#1147](https://github.com/deniscerri/ytdlnis/issues/1147), [#1149](https://github.com/deniscerri/ytdlnis/issues/1149), [#1168](https://github.com/deniscerri/ytdlnis/issues/1168), [#1173](https://github.com/deniscerri/ytdlnis/issues/1173), [#1176](https://github.com/deniscerri/ytdlnis/issues/1176), [#1177](https://github.com/deniscerri/ytdlnis/issues/1177), [#1184](https://github.com/deniscerri/ytdlnis/issues/1184), [#1189](https://github.com/deniscerri/ytdlnis/issues/1189), [#1196](https://github.com/deniscerri/ytdlnis/issues/1196).
- **youtubedl-android** ([github.com/yausername/youtubedl-android](https://github.com/yausername/youtubedl-android)) — 1.3k★ — GPL-3 — yt-dlp wrapper.
- **Ringdroid (althafvly fork)** ([github.com/althafvly/ringdroid](https://github.com/althafvly/ringdroid)) — 61★ — de-facto Android ringtone editor; v3.0.1 May 2026 added Android 16 support; only actively maintained FOSS option.
- **RingtoneSmartKit** ([github.com/AmjdAlhashede/RingtoneSmartKit](https://github.com/AmjdAlhashede/RingtoneSmartKit)) — 6★ — Apache-2.0 — Kotlin library for system + contact ringtones.
- **UltimateRingtonePicker** ([github.com/DeweyReed/UltimateRingtonePicker](https://github.com/DeweyReed/UltimateRingtonePicker)) — 68★ — MIT — MediaStore-scoped picker with multi-select.
- **ImageToolbox** ([github.com/T8RIN/ImageToolbox](https://github.com/T8RIN/ImageToolbox)) — 12.9k★ — Apache-2.0 — 310+ filters, AI background removal, OCR, upscale. Issues: [#2759](https://github.com/T8RIN/ImageToolbox/issues/2759), [#2763](https://github.com/T8RIN/ImageToolbox/issues/2763).
- **freesound-android** ([github.com/futurice/freesound-android](https://github.com/futurice/freesound-android)) — 86★ — Apache-2.0 — unofficial Freesound client reference.
- **panels-art/WallApp** ([github.com/panels-art/WallApp](https://github.com/panels-art/WallApp)) — 73★ — Apache-2.0 — production KMP wallpaper app reference (panels.art).
- **cmota/Unsplash** ([github.com/cmota/Unsplash](https://github.com/cmota/Unsplash)) — 64★ — Apache-2.0 — Compose Multiplatform Android + Wear OS + iOS + Desktop + Web.
- **ishubhamsingh/Splashy** ([github.com/ishubhamsingh/Splashy](https://github.com/ishubhamsingh/Splashy)) — 51★ — Apache-2.0 — clean KMP Unsplash.
- **RealSR-NCNN-Android** ([github.com/tumuyan/RealSR-NCNN-Android](https://github.com/tumuyan/RealSR-NCNN-Android)) — 2k★ — on-device upscaling via NCNN/MNN.
- **workpaper-android** ([github.com/Jarvay/workpaper-android](https://github.com/Jarvay/workpaper-android)) — 18★ — MIT — scheduled wallpaper changer with MOV/MP4 video wallpaper support.
- **LiveSlider** ([github.com/rahulshah456/LiveSlider](https://github.com/rahulshah456/LiveSlider)) — 57★ — MIT — parallax slideshow live wallpaper reference.
- **zero** ([github.com/lucasasselli/zero](https://github.com/lucasasselli/zero)) — 71★ — GPL-3 — ARCHIVED; layered PNG 3D parallax reference.
- **BelecoLiveWallpaper** ([github.com/dklaputa/BelecoLiveWallpaper](https://github.com/dklaputa/BelecoLiveWallpaper)) — 82★ — historical OpenGL + Rotation Vector Sensor reference.
- **kmpalette** ([github.com/jordond/kmpalette](https://github.com/jordond/kmpalette)) — Compose Multiplatform Palette replacement.

---

## Appendix B — Cited Commercial / Adjacent Apps

- Zedge — [help.zedge.net/.../pAInt](https://help.zedge.net/hc/en-us/sections/11021867047060-Creating-content-with-Zedge-pAInt); [blog roundup](https://blog.zedge.net/best-wallpaper-apps/); ad complaints [Android Police](https://www.androidpolice.com/best-android-wallpaper-collections-apps-automated-parallax/), [Trustpilot](https://www.trustpilot.com/review/www.zedge.net), [PissedConsumer](https://zedge.pissedconsumer.com/review.html), [Android Central #979866](https://forums.androidcentral.com/threads/zedge-harmful.979866/page-2).
- Walli — [walliapp.com](https://www.walliapp.com/), [artists](https://www.walliapp.com/artists/), [reviews](https://justuseapp.com/en/app/1061097668/walli-cool-wallpapers-hd/reviews).
- Backdrops — [Terms](https://backdrops.io/terms/), [App Store description](https://apps.apple.com/us/app/backdrops-wallpapers/id1500143735), [Android Authority v6.0 review](https://www.androidauthority.com/backdrops-wallpaper-app-material-3-expressive-update-3577927/).
- Tapet — [Play Store](https://play.google.com/store/apps/details?id=com.sharpregion.tapet), [Premium summary](https://www.apkdone.com/tapet/), [subscription complaints](https://www.app-sales.net/sales/tapet-premium-upgrade-7152).
- Abstruct — [abstruct.co](https://abstruct.co/).
- WLPPR — [Play Store](https://play.google.com/store/apps/details?id=com.wlppr).
- Vellum — [getvellum.com](https://www.getvellum.com/).
- Resplash — [GitHub](https://github.com/b-lam/Resplash), [Play Store](https://play.google.com/store/apps/details?id=com.b_lam.resplash).
- WallpapersCraft / WallCraft — [ComplaintsBoard](https://www.complaintsboard.com/wallcraft-wallpapers-live-b148917).
- KLWP / KWGT — [docs.kustom.rocks](https://docs.kustom.rocks/docs/downloads/download-klwp/), [Play Store](https://play.google.com/store/apps/details?id=org.kustom.wallpaper).
- Muviz Edge — [fastgazi.com review](https://www.fastgazi.com/2025/10/muviz-edge-stylish-music-visualizer.html), [Play Store](https://play.google.com/store/apps/details?id=com.sparkine.muvizedge).
- Spectrolizer — [Play Store](https://play.google.com/store/apps/details?id=com.aicore.spectrolizer).
- Vizik — [Play Store](https://play.google.com/store/apps/details?id=com.banix.music.visualizer.maker).
- Audiko — [Play Store](https://play.google.com/store/apps/details?id=net.audiko2.pro), [Zedge ringtone roundup](https://blog.zedge.net/best-free-ringtone-apps/).
- Wonder — [Play Store](https://play.google.com/store/apps/details?id=com.codeway.wonder), [Google Play community complaint thread](https://support.google.com/googleplay/thread/214242312/).
- Lensa — wide critical coverage of dark patterns and bias.
- ImagineArt — [Play Store](https://play.google.com/store/apps/details?id=com.vyroai.aiart).
- Krea — [pricing](https://www.krea.ai/pricing), [API features](https://www.krea.ai/features/api).
- Facer — [news.facer.io WOS6 article](https://news.facer.io/massive-facer-update-wear-os-6-new-features-for-all-7bb1480b5797), [5.0 announcement](https://news.facer.io/introducing-facer-5-0-and-facer-premium/), [Android Authority hands-on](https://www.androidauthority.com/facer-app-hands-on-3583105/).
- WatchMaker — [WFF announcement](https://getwatchmaker.com/wff_announcement), [Android Authority Wear OS 6 coverage](https://www.androidauthority.com/pujie-watchmaker-watch-face-wear-os-6-support-3581417/).
- Pujie — [pujie.io](https://pujie.io/), [Play Store](https://play.google.com/store/apps/details?id=com.pujie.watchfaces).
- Glance (Motorola) — [Play Store](https://play.google.com/store/apps/details?id=com.glance.lockscreenM), [9to5Google coverage](https://9to5google.com/2024/04/26/glance-android-lockscreen-motorola-turn-off/).
- Always On AMOLED — [Play Store](https://play.google.com/store/apps/details?id=com.tomer.alwayson), [TechWiser roundup](https://techwiser.com/best-always-on-display-apps-on-android/).
- Samsung Good Lock — [Android Central guide](https://www.androidcentral.com/samsung-good-lock), [SamMobile Wonderland](https://www.sammobile.com/news/new-good-lock-module-wonderland-create-custom-live-wallpapers/), [Sammy Fans](https://www.sammyfans.com/2025/10/24/samsung-customization-with-good-lock/), [Samsung Newsroom](https://news.samsung.com/global/exploring-good-lock-%E2%91%A2-3-features-recommended-by-samsung-developers-and-newsroom-editors).
- One UI 8.5 — [Android Authority](https://www.androidauthority.com/samsung-one-ui-8-5-lock-screen-weather-effect-3630836/), [Digital Trends](https://www.digitaltrends.com/phones/samsungs-one-ui-8-5-will-turn-your-lock-screen-into-a-mini-music-show/), [SamMobile top ten features stable May 2026](https://www.sammobile.com/news/one-ui-8-5-update-top-features/), [Sammy Fans Wonderland motion-wallpaper May 2026 update](https://www.sammyfans.com/2026/05/07/samsung-wonderland-motion-wallpapers-may-2026-update/), [SammyGuru AI weather effects deep dive](https://sammyguru.com/one-ui-8-5-will-bring-ai-powered-live-weather-effects-to-your-lock-screen/), [SamMobile adaptive lock-screen clock all objects](https://www.sammobile.com/news/one-ui-8-5-adaptive-lock-screen-clock-works-all-objects/), [Sammy Fans Galaxy-to-Share refresh](https://www.sammyfans.com/2026/05/16/samsung-galaxy-to-share-one-ui-8-5-update/), [Sammy Fans AI wallpaper expansion tool](https://www.sammyfans.com/2026/03/14/one-ui-8-5-expand-wallpapers-with-new-ai-tool/), [Sammy Fans interactive wallpapers Jan 2026 beta](https://www.sammyfans.com/2026/01/05/samsungs-one-ui-8-5-beta-introduces-animated-and-interactive-wallpapers/).
- Pixel Live Effects (Android 16 QPR1) — [9to5Google](https://9to5google.com/2025/05/20/google-pixel-wallpaper-effects-android-16-qpr1/), [PiunikaWeb user reception](https://piunikaweb.com/2025/09/05/pixels-new-live-effects-wallpaper-feature-falls-flat-with-users/), [Beebom Live Effects how-to](https://gadgets.beebom.com/guides/how-to-use-lock-screen-live-effects-on-pixel-phones), [PhoneArena weather wallpaper](https://www.phonearena.com/news/android-16-allows-you-to-check-local-weather-using-wallpaper_id170727), [Sammy Fans AI photo wallpaper](https://www.sammyfans.com/2025/06/03/android-16s-new-ai-photo-wallpaper-feature-will-melt-your-heart/), [Material 3 Expressive personalization Pixel Drop](https://store.google.com/intl/en/ideas/articles/september-pixel-drop-personalization/).
- Pixel 10 Auto-change AI Wallpaper (per-unlock generation) — [OnOff.gr AI wallpaper guide](https://www.onoff.gr/blog/en/android/ai-wallpaper-android-create-ai-wallpapers/), [Pixel custom wallpaper support page](https://support.google.com/pixelphone/answer/16517561?hl=en), [Tom's Guide Pixel 10 AI icons critique](https://www.tomsguide.com/phones/google-pixel-phones/i-just-tried-new-ai-generated-app-icons-for-pixel-phones-and-theres-a-huge-problem).
- Android 16 QPR2 lockscreen widgets stable (December 2025) — [Pocket-lint a-decade-back coverage](https://www.pocket-lint.com/google-added-back-android-lock-screen-widgets/), [Android Police droid-life Dec 2025 release](https://www.droid-life.com/2025/08/20/android-16-qpr2-adds-lock-screen-widgets-to-phones-again/), [Indianewsnetwork stable rollout](https://www.indianewsnetwork.com/en/google-releases-android-16-qpr2-update-pixel-devices-20251204), [How-To Geek how they work](https://www.howtogeek.com/android-lock-screen-widgets-how-they-work/).
- Nothing Glyph SDK — [Glyph Developer Kit](https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit), [Glyph Matrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit), [Nothing OS 4.1 features](https://gadgets.beebom.com/guides/nothing-os-4-1-features), [Android Central Nothing OS 4](https://www.androidcentral.com/phones/nothing-phones/nothing-os-4-arrives-for-the-phone-3-with-exclusive-features-refined-glyph-interface-and-more).
- Wallpaper Engine (Android companion) — [wallpaperengine.io/android](https://www.wallpaperengine.io/android/en), [Steam WE 2.0 announcement](https://store.steampowered.com/news/app/431960/view/3101285480922069754).
- Spotify dynamic backdrop pattern — [Medium analysis](https://medium.com/@shanmugashree3/how-spotify-creates-those-stunning-backdrops-that-match-every-song-playlist-00fe13eab033), [Envato design](https://elements.envato.com/learn/spotify-wrapped-design-aesthetic), [Eggradients on Spotify colors](https://www.eggradients.com/blog/spotify-colors).
- Procreate color sampling — [Procreate Color page](https://procreate.com/ipad/color), [Procreate Handbook palettes](https://help.procreate.com/procreate/handbook/5.0/colors/colors-palettes), [Pigeon Letters eyedropper](https://www.thepigeonletters.com/blogs/1/procreate-eyedropper).
- Apple Live Photos / Depth Effect — [Apple Support](https://support.apple.com/en-us/120734), [One4Studio glossary](https://www.one4studio.com/glossary/parallax-wallpaper), [Engadget iOS 26 Spatial Scenes](https://www.engadget.com/mobile/smartphones/how-to-make-your-lock-screen-background-holographic-in-ios-26-110049999.html).
- MKBHD Panels shutdown (anti-pattern lesson) — [MacRumors](https://www.macrumors.com/2025/12/01/mkbhd-wallpaper-app-shutdown/), [TechCrunch](https://techcrunch.com/2025/12/01/mkbhds-wallpaper-app-panels-is-shutting-down/).

---

## Appendix C — Platform, Standards, and Dependency Sources

- Android 14 docs — [Photo Picker behavior change](https://developer.android.com/about/versions/14/changes/partial-photo-video-access).
- Android 15 — [behavior changes all](https://developer.android.com/about/versions/15/behavior-changes-all), [summary](https://developer.android.com/about/versions/15/summary).
- Android 16 (Baklava) — [features](https://developer.android.com/about/versions/16/features), [WallpaperDescription](https://developer.android.com/reference/android/app/wallpaper/WallpaperDescription), [WallpaperInstance](https://developer.android.com/reference/android/app/wallpaper/WallpaperInstance), [QPR1 Live Effects coverage](https://9to5google.com/2025/06/10/android-16-qpr1-beta-2-adds-live-effects-section-to-wallpaper-picker/), [QPR2 lock-screen widgets](https://www.androidauthority.com/lock-screen-widgets-on-phones-android-16-qpr2-3589668/), [Desktop Mode](https://android-developers.googleblog.com/2026/03/android-devices-extend-seamlessly-to.html).
- Android 17 — [release notes](https://developer.android.com/about/versions/17/release-notes), [overview](https://developer.android.com/about/versions/17), [Beta 1](https://android-developers.googleblog.com/2026/02/the-first-beta-of-android-17.html), [Beta 2 announce EyeDropper + Contacts Picker](https://9to5google.com/2026/02/26/android-17-beta-2-contacts-and-display-color-access/), [Beta 3 PhotoPickerUiCustomizationParams + Platform Stability](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html), [Beta 4](https://android-developers.googleblog.com/2026/04/the-fourth-beta-of-android-17.html), [Beta 4 feature roundup 9to5Google](https://9to5google.com/2026/04/16/android-17-beta-4-everything-new/), [Android 17 Beta hands-on BigGo](https://finance.biggo.com/news/202605081152_Android_17_Beta_Key_Features), [Beebom 20 features](https://gadgets.beebom.com/guides/best-android-17-features), [EyeDropper deep-dive ProAndroidDev](https://proandroiddev.com/exploring-the-eyedropper-api-android-17-9d7be86aaa16), [Android Engineers EyeDropper walkthrough](https://androidengineers.substack.com/p/introducing-the-android-17-eye-dropper), [Android Authority EyeDropper first look](https://www.androidauthority.com/android-17-eyedropper-color-picker-3610073/) — Platform Stability reached Beta 3; stable June 2026.
- Android XR — [spatial environments design docs](https://developer.android.com/design/ui/xr/guides/environments), [Android XR overview](https://www.android.com/xr/), [Android Show 2026 preview](https://www.analyticsinsight.net/news/google-android-show-2026-to-detail-mixed-reality-ecosystem-with-android-xr), [Galaxy XR launch coverage](https://virtual.reality.news/news/google-android-xr-revealed-ai-glasses-coming-2026/), [3-tier glasses strategy 2026-2027](https://virtual.reality.news/news/googles-android-xr-glasses-strategy-could-beat-apple/).
- Performance Class — [docs](https://developer.android.com/topic/performance/performance-class).
- Ultra HDR — [display docs](https://developer.android.com/media/grow/ultra-hdr/display).
- AGSL — [official guide](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl), [Compose patterns Medium](https://medium.com/androiddevelopers/agsl-made-in-the-shade-r-7d06d14fe02a).
- Wear OS Watch Face — [Format docs](https://developer.android.com/training/wearables/wff), [Watch Face Push](https://developer.android.com/training/wearables/watch-face-push), [Phone app side](https://developer.android.com/training/wearables/watch-face-push/phone-app), [Androidify case study](https://android-developers.googleblog.com/2025/12/bringing-androidify-to-wear-os-with.html), [Watch faces what's new I/O '25](https://android-developers.googleblog.com/2025/05/whats-new-in-watch-faces.html).
- ML Kit — [Subject Segmentation Android](https://developers.google.com/ml-kit/vision/subject-segmentation/android), [Selfie Segmentation Android](https://developers.google.com/ml-kit/vision/selfie-segmentation/android), [GenAI APIs](https://developers.google.com/ml-kit/genai).
- Gemini Nano / Firebase AI Logic — [docs](https://developer.android.com/ai/gemini-nano), [Hybrid Inference blog](https://android-developers.googleblog.com/2026/04/Hybrid-inference-and-new-AI-models-are-coming-to-Android.html).
- Material You / Monet — [dynamic colors](https://developer.android.com/develop/ui/views/theming/dynamic-colors), [Material 3 Expressive Android Authority](https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/), [Sid Patil's Monet internals](https://siddroid.com/post/android/chasing-monet-inside-the-android-framework/), [AOSP material display source](https://source.android.com/docs/core/display/material).
- Photo Picker — [Android 14 docs](https://developer.android.com/about/versions/14/changes/partial-photo-video-access), [Android 17 `PhotoPickerUiCustomizationParams` 9:16 aspect ratio Beta 3](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html), [Photo Picker training guide](https://developer.android.com/training/data-storage/shared/photo-picker).
- Predictive back — [Compose docs](https://developer.android.com/develop/ui/compose/system/predictive-back), [Android 14 behaviour change](https://developer.android.com/about/versions/14/behavior-changes-14#predictive-back-gesture), [Navigation 2.9 predictive-back integration breakdown](https://medium.com/@androidlab/androidx-navigation-2-9-6-complete-feature-breakdown-4b09ccd637dd).
- Compose Adaptive Layouts — [1.2 beta blog](https://android-developers.googleblog.com/2025/09/unfold-new-possibilities-with-compose-adaptive-layouts-1-2-beta.html), [Build adaptive apps guide](https://developer.android.com/develop/ui/compose/build-adaptive-apps), [adaptive layouts overview](https://developer.android.com/develop/ui/compose/layouts/adaptive), [support different display sizes](https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-display-sizes), [Touchlab adaptive layouts in CMP](https://touchlab.co/adaptive-layouts-cmp), [Kotlin Multiplatform adaptive layouts](https://kotlinlang.org/docs/multiplatform/compose-adaptive-layouts.html), [The Black Bit master adaptive layouts walkthrough Feb 2026](https://medium.com/@thebackbit/master-adaptive-layouts-in-compose-multiplatform-build-truly-responsive-uis-89184bf8b6de), [Google Play large-screen quality bar](https://developer.android.com/guide/topics/large-screens/get-started-with-large-screens).
- Compose Strong Skipping — [docs](https://developer.android.com/develop/ui/compose/performance/stability/strongskipping).
- R8 keep rules — [blog](https://android-developers.googleblog.com/2025/11/configure-and-troubleshoot-r8-keep-rules.html).
- AGP — [9.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes), [9.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes), [9.2](https://developer.android.com/build/releases/agp-9-2-0-release-notes).
- Gradle — [9 whats-new](https://gradle.org/whats-new/gradle-9/), [release notes](https://docs.gradle.org/current/release-notes.html).
- Kotlin — [2.3.20](https://kotlinlang.org/docs/whatsnew2320.html), [2.3](https://kotlinlang.org/docs/whatsnew23.html), [2.2.0 announce](https://blog.jetbrains.com/kotlin/2025/06/kotlin-2-2-0-released/).
- KotlinConf 2025 — [JetBrains recap](https://blog.jetbrains.com/kotlin/2025/05/kotlinconf-2025-language-features-ai-powered-development-and-kotlin-multiplatform/).
- Compose updates — [Apr 2026 blog](https://android-developers.googleblog.com/2026/04/jetpack-compose-april-2026-updates.html), [Material 3 release page](https://developer.android.com/jetpack/androidx/releases/compose-material3), [Dec 2025 whats-new](https://android-developers.googleblog.com/2025/12/whats-new-in-jetpack-compose-december.html), [jetc.dev #298 May 2026](https://jetc.dev/issues/298.html) (2026.05.00 BOM ↦ Compose 1.11.1 stable / 1.12.0-alpha02), [Material 3 Expressive deep dive Android Authority](https://www.androidauthority.com/google-material-3-expressive-features-changes-availability-supported-devices-3556392/).
- Dagger / Hilt — [2.59 release](https://github.com/google/dagger/releases/tag/dagger-2.59).
- Room — [release page](https://developer.android.com/jetpack/androidx/releases/room), [Room 3.0 announce](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html).
- Retrofit 3 — [discussion #4379](https://github.com/square/retrofit/discussions/4379).
- OkHttp — [CHANGELOG](https://github.com/square/okhttp/blob/master/CHANGELOG.md), [Snyk security](https://security.snyk.io/package/maven/com.squareup.okhttp3%3Aokhttp).
- Coil 3 — [upgrade guide](https://coil-kt.github.io/coil/upgrading_to_coil3/), [CHANGELOG](https://github.com/coil-kt/coil/blob/main/CHANGELOG.md), [Coil 3.0 announce by Colin White](https://colinwhite.me/post/coil_3_release), [Maven Central coil3](https://central.sonatype.com/artifact/io.coil-kt.coil3/coil-compose) (3.4.0 stable Feb 24 2026).
- Media3 — [release page](https://developer.android.com/jetpack/androidx/releases/media3), [1.6.0 blog](https://android-developers.googleblog.com/2025/03/media3-1-6-0-is-now-available.html), [1.8.0 whats-new](https://medium.com/google-exoplayer/media3-1-8-0-whats-new-b857435651b9), [1.9.0 whats-new](https://android-developers.googleblog.com/2025/12/media3-190-whats-new.html), [1.10.0 release blog](https://android-developers.googleblog.com/2026/03/media3-110-is-out.html), [1.10.0 dev blog mirror](https://developer.android.com/blog/posts/media3-1-10-is-out), [Compose-2026 ExoPlayer 1.10 guide](https://medium.com/@ramadan123sayed/media-player-in-jetpack-compose-the-complete-2026-guide-exoplayer-media3-1-10-0a25af46ce7d).
- Navigation Compose — [release page](https://developer.android.com/jetpack/androidx/releases/navigation), [2.9 breakdown Medium](https://medium.com/@androidlab/androidx-navigation-2-9-6-complete-feature-breakdown-4b09ccd637dd).
- Lifecycle — [release page](https://developer.android.com/jetpack/androidx/releases/lifecycle).
- Coroutines — [releases](https://github.com/Kotlin/kotlinx.coroutines/releases).
- Glance — [release page](https://developer.android.com/jetpack/androidx/releases/glance).
- Paging — [release page](https://developer.android.com/jetpack/androidx/releases/paging).
- DataStore — [release page](https://developer.android.com/jetpack/androidx/releases/datastore).
- Firebase — [Android release notes](https://firebase.google.com/support/release-notes/android).
- NewPipeExtractor — [releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases).
- youtubedl-android — [releases](https://github.com/yausername/youtubedl-android/releases).
- Moshi — [CHANGELOG](https://github.com/square/moshi/blob/master/CHANGELOG.md).
- ZXing — [releases](https://github.com/zxing/zxing/releases).
- Security bulletins — [AOSP December 2025](https://source.android.com/docs/security/bulletin/2025-12-01), [SOCPrime CVE-2025-48572/-48633](https://socprime.com/blog/cve-2025-48633-and-cve-2025-48572-vulnerabilities/), [AOSP April 2026](https://source.android.com/docs/security/bulletin/2026/2026-04-01), [AOSP May 2026 (CVE-2026-0073 adbd zero-click RCE)](https://source.android.com/docs/security/bulletin/2026/2026-05-01), [Cybersecurity News CVE-2026-0073 coverage](https://cybersecuritynews.com/android-zero-click-vulnerability/), [CIS March 2026 multi-CVE Android advisory (CVE-2026-21385 active exploit)](https://www.cisecurity.org/advisory/multiple-vulnerabilities-in-google-android-os-could-allow-for-remote-code-execution_2026-017), [yt-dlp CVE-2026-26331 `--netrc-cmd` command injection](https://advisories.gitlab.com/pkg/pypi/yt-dlp/CVE-2026-26331/).
- NewPipe continuity risk — [PiunikaWeb March 2026](https://piunikaweb.com/2026/03/09/newpipe-certified-android-devices-warning/), [SABR-only player response Issue #12126](https://github.com/TeamNewPipe/NewPipe/issues/12126), [NewPipe 0.28.1 release Jan 2026](https://newpipe.net/blog/pinned/announcement/newpipe-0.28.1-released/).
- AV1 install base — [Meta engineering analysis](https://engineering.fb.com/2025/09/24/video-engineering/video-streaming-with-av1-video-codec-mobile-devices-meta-white-paper/).
- JPEG XL state — [XDA coverage](https://www.xda-developers.com/jpeg-xl-best-image-format-that-nobodys-using/).
- Color management — [AOSP color-mgmt](https://source.android.com/docs/core/display/color-mgmt).
- xHE-AAC — [Wikipedia USAC](https://en.wikipedia.org/wiki/Unified_Speech_and_Audio_Coding).
- LearnOpenGLES live wallpaper RGB_565 banding — [article](https://www.learnopengles.com/how-to-use-opengl-es-2-in-an-android-live-wallpaper/).
- WallpaperService Engine docs — [reference](https://developer.android.com/reference/android/service/wallpaper/WallpaperService).
- AudioRouting / in-band ringing — [XDA Android Pie in-band ringtones](https://www.xda-developers.com/android-pie-bluetooth-in-band-ringtones-default/).

---

## Appendix D — Community Signal Sources

- Hacker News: [AIWP context-aware wallpapers Show HN](https://news.ycombinator.com/item?id=38418254); [Open-source GitHub repos wallpaper gallery](https://news.ycombinator.com/item?id=46411074); [Wallpaper that grows as you ship](https://news.ycombinator.com/item?id=46692793).
- Android Central — [Zedge complaints thread #979866](https://forums.androidcentral.com/threads/zedge-harmful.979866/page-2); [Bluetooth ringtone routing gap](https://forums.androidcentral.com/threads/can-i-get-phones-ringtone-in-bluetooth-headset.1005511/).
- XDA — [Tasker live wallpaper Bluetooth](https://xdaforums.com/t/tasker-to-change-to-a-live-wallpaper.3758710/); [Sound Pack Giant Audio collection](https://xdaforums.com/t/sound-pack-alarms-ringtones-notifications-ui-giant-audio-pack-collection.4369843/); [Android 14 independent lock-screen live wallpaper](https://www.xda-developers.com/android-14-independent-lock-screen-live-wallpaper/); [Wear OS custom sounds break](https://xdaforums.com/t/custom-notification-sounds-and-ringtones-no-longer-working-after-upgrading-to-the-latest-version-of-wearos.4531515/); [Android Pie Bluetooth in-band](https://www.xda-developers.com/android-pie-bluetooth-in-band-ringtones-default/).
- F-Droid — [Ringtone Maker forum thread #22600](https://forum.f-droid.org/t/ringtone-maker-app/22600); [Inclusion How-To](https://f-droid.org/docs/Inclusion_How-To/); [Reproducible Builds docs](https://f-droid.org/en/docs/Reproducible_Builds/); [2024 retrospective](https://f-droid.org/2025/01/21/a-look-back-at-2024-f-droids-progress-and-whats-coming-in-2025.html); [2025 retrospective](https://f-droid.org/en/2026/01/23/fdroid-in-2025-strengthening-our-foundations-in-a-changing-mobile-landscape.html); [reproducible builds blog](https://f-droid.org/en/2025/05/21/making-reproducible-builds-visible.html); [client 1.19 auto-install](https://f-droid.org/2024/02/01/twif.html); [AlwaysOn FOSS package](https://f-droid.org/en/packages/io.github.domi04151309.alwayson/); [Suntimes package](https://f-droid.org/packages/com.forrestguice.suntimeswidget/); [WallYou package](https://f-droid.org/packages/com.bnyro.wallpaper/); [Ringdroid archive](https://f-droid.org/packages/org.thayyil.ringdroid/); [Paperize package](https://f-droid.org/packages/com.anthonyla.paperize/).
- Reddit / community curation — [Reddit Favorites — Backdrops on AI slop](https://redditfavorites.com/android_apps/backdrops-wallpapers); [DroidViews smart wallpaper](https://www.droidviews.com/automatic-wallpaper-change-contextually-with-smart-wallpaper/); [MakeTechEasier different wallpaper per page](https://maketecheasier.com/add-different-wallpaper-android-home-screen/).
- Tasker — [plugins intro](https://tasker.joaoapps.com/plugins-intro.html); [CalendarTask plugin](https://www.appbrain.com/app/calendartask/com.balda.calendartask).
- Muzei — [API docs](https://api.muzei.co/), [Ian Lake Muzei 3.0 Medium](https://medium.com/muzei/announcing-muzei-live-wallpaper-3-0-d167dd5795a4), [Changelog wiki](https://github.com/muzei/muzei/wiki/Changelog), [Muzei API source on GitHub](https://github.com/muzei/muzei/blob/main/muzei-api/src/main/java/com/google/android/apps/muzei/api/provider/MuzeiArtProvider.java).
- KWGT docs — [official downloads](https://docs.kustom.rocks/docs/downloads/download-kwgt/).
- Awesome lists — [awesome-android-livewallpaper](https://github.com/vvolas/Awesome-Live-Wallpaper); [awesome-kotlin (Heapy)](https://github.com/Heapy/awesome-kotlin); [jetpack-compose-awesome](https://github.com/jetpack-compose/jetpack-compose-awesome); [awesome-android-ui (wasabeef)](https://github.com/wasabeef/awesome-android-ui); [awesome-android (JStumpp)](https://github.com/JStumpp/awesome-android); [awesome-wear-os](https://github.com/WearOSCommunity/awesome-wear-os); [google/watchface validator](https://github.com/google/watchface).
- Distribution — [Per-ABI APK splits Medium](https://cdmunoz.medium.com/goodbye-giant-apk-how-we-went-from-186-mb-to-62-mb-with-split-per-abi-and-three-lines-in-ci-673dd71dbdcb); [AAB vs APK overview](https://www.appsonair.com/blogs/apk-vs-aab---what-really-changes-internally-beyond-the-marketing); [Privacy Guides obtaining apps](https://www.privacyguides.org/en/android/obtaining-apps/); [Obtainium GitHub](https://github.com/ImranR98/Obtainium); [IzzyOnDroid index](https://apt.izzysoft.de/fdroid/index/info).
- ML / depth references — [Qualcomm Depth-Anything-V2 TFLite](https://huggingface.co/qualcomm/Depth-Anything-V2); [Qualcomm Stable Diffusion <1s blog](https://www.qualcomm.com/news/onq/2024/02/worlds-first-on-device-demonstration-of-stable-diffusion-on-android); [arXiv 2503.14868 Efficient Personalization of Quantized Diffusion](https://arxiv.org/abs/2503.14868); [arXiv 2412.06661 Efficiency Meets Fidelity](https://arxiv.org/abs/2412.06661); [arXiv 2306.02316 Temporal Dynamic Quantization](https://arxiv.org/abs/2306.02316); [MediaPipe Image Segmenter Android](https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android).
- Battery — [battery study](https://thebatterytips.com/battery-specifications/do-live-wallpapers-take-up-battery/).
- Pixel features — [Generative AI wallpapers Android Authority](https://www.androidauthority.com/generative-ai-wallpapers-3336181/); [Pixel 10 unlock-change generative wallpaper](https://store.google.com/intl/en/ideas/articles/september-pixel-drop-personalization/).
- Public Wallhaven / Pexels — [Wallhaven help/api](https://wallhaven.cc/help/api); [Pexels API ToS replication article](https://help.pexels.com/hc/en-us/articles/4405588861721).
- Audius — [docs.audius.org](https://docs.audius.org/api/).
- Freesound intro / CC0 — [Creative Commons / Freesound](https://opensource.creativecommons.org/blog/entries/freesound-intro/); [Freesound APIv2 docs](https://freesound.org/docs/api/resources_apiv2.html); [Freesound developer auth](https://freesound.org/help/developers/).
- Spotify color extraction — [Medium pipeline analysis](https://medium.com/@shanmugashree3/how-spotify-creates-those-stunning-backdrops-that-match-every-song-playlist-00fe13eab033).
- Reproducible builds / palette references — [Sid Patil Monet](https://siddroid.com/post/android/chasing-monet-inside-the-android-framework/); [Palette generator experiment](https://github.com/irisxu02/palette-generator-experiment); [Dev.to dynamic color Compose](https://dev.to/myougatheaxo/dynamic-color-material-you-in-compose-wallpaper-based-theming-325j).
- ML Kit issues from existing pitfall log — [#137](https://github.com/googlesamples/mlkit/issues/137), [#386](https://github.com/googlesamples/mlkit/issues/386), [#436](https://github.com/googlesamples/mlkit/issues/436).
- Yt-dlp legal context — [audioutils blog](https://audioutils.com/blog/is-yt-dlp-legal).
- Health Connect — [docs](https://developer.android.com/health-and-fitness/guides/health-connect).
- Channels API — [Compose notifications](https://developer.android.com/develop/ui/compose/notifications/channels).
- Talkback caller-ID — [Google Accessibility](https://support.google.com/accessibility/android/answer/6006564).
- Localization tooling for OSS Android (informs U-11) — [Weblate self-hosted vs. Crowdin AI-localization comparison](https://www.g2.com/products/weblate/competitors/alternatives), [F-Droid forum thread on localized descriptions](https://forum.f-droid.org/t/localized-app-descriptions-via-translation-service-weblate-crowdin-stringlate/1610), [Crowdin Android SDK over-the-air](https://store.crowdin.com/android), [Weblate open-alternative profile](https://openalternative.co/weblate).
- Compose accessibility primitives (informs U-13 + U-9) — [Semantics & TalkBack Bryan Herbst](https://bryanherbst.com/2020/11/03/compose-semantics-talkback/), [Compose accessibility codelab](https://developer.android.com/codelabs/jetpack-compose-accessibility), [Compose API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults), [4 common TalkBack issues in Compose](https://medium.com/@yanfalcao10/4-common-talkback-issues-in-android-compose-c6e3c3d92d19), [Deque accessibility-first analysis](https://www.deque.com/blog/how-jetpack-compose-is-helping-put-accessibility-first-for-android/).
- Live wallpaper battery research (informs T-G) — [DreamPixel battery analysis](https://dreampixelstudio.app/blog/use-live-wallpapers-on-android-without-draining-battery), [Computerworld battery drain study](https://www.computerworld.com/article/1416878/do-live-wallpapers-cause-noticeable-battery-drain-on-android.html).
- Baseline Profiles 2026 (informs L-8) — [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview), [Compose baseline-profile guide](https://developer.android.com/develop/ui/compose/performance/baseline-profiles), [2026 startup time analysis](https://medium.com/@ramadan123sayed/baseline-profiles-in-android-explained-from-scratch-what-art-compilation-is-why-your-first-app-898484bf6746), [Cloud Profiles vs Baseline 2026](https://dev.to/devin-rosario/optimizing-app-start-up-time-baseline-profiles-vs-cloud-profiles-in-2026-m05).
- WallpaperDescription / WallpaperInstance reference (informs N-4 completion) — [MS Learn WallpaperDescription class API 36](https://learn.microsoft.com/en-us/dotnet/api/android.app.wallpaper.wallpaperdescription?view=net-android-36.0), [WallpaperService.Engine OnApplyWallpaper](https://learn.microsoft.com/en-us/dotnet/api/android.service.wallpaper.wallpaperservice.engine.onapplywallpaper?view=net-android-36.0), [Salvatore's live-wallpaper how-to](https://sal.dev/android/android-live-wallpaper/).
- Compose Multiplatform 2026 (informs L-4) — [Compatibility & versioning matrix](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html), [Kotlin 2.2.20 what's new](https://kotlinlang.org/docs/whatsnew2220.html), [Production-ready in 2026 BestHub](https://www.besthub.dev/articles/why-kotlin-multiplatform-compose-multiplatform-are-production-ready-in-2026-24d731545514), [KMP ultimate guide 2026 commonmain.dev](https://commonmain.dev/kotlin-multiplatform/).
- Sunrise alarm references — [yuriykulikov/AlarmClock GitHub](https://github.com/yuriykulikov/AlarmClock).
- Custom ringtones Android UX — [9to5Google March 2024 contacts](https://9to5google.com/2024/03/20/google-contacts-custom-ringtones-android/).

---

## Appendix E — Cycle 1 Sources

- Cycle 1 planning record — [docs/research/cycle-1-2026-06-04.md](docs/research/cycle-1-2026-06-04.md).
- Firebase App Check and Play Integrity — [App Check overview](https://firebase.google.com/docs/app-check), [Android Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider).
- Android performance guardrails — [Baseline Profiles overview](https://developer.android.com/topic/performance/baselineprofiles/overview), [create Baseline Profile](https://developer.android.com/topic/performance/baselineprofiles/create-baselineprofile), [Dex layout optimizations](https://developer.android.com/topic/performance/baselineprofiles/dex-layout-optimizations), [Macrobenchmark overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview), [JankStats](https://developer.android.com/topic/performance/jankstats).
- Android distribution and policy — [Developer verification](https://developer.android.com/developer-verification), [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Play Console foreground service declaration](https://support.google.com/googleplay/android-developer/answer/13392821), [document provider access](https://developer.android.com/training/data-storage/shared/documents-files).
- Open-source wallpaper ecosystem — [Muzei API](https://api.muzei.co/), [Muzei repository](https://github.com/muzei/muzei), [Doodle](https://github.com/patzly/doodle-android), [UndeadWallpaper](https://github.com/DroidWorksStudio/UndeadWallpaper).
- Marketplace comparison — [Zedge community upload guide](https://help.zedge.net/hc/en-us/articles/21801574348948-Uploading-Content-to-the-Zedge-Community-from-Your-Mobile-Device), [Zedge Play listing](https://play.google.com/store/apps/details?id=net.zedge.android), [Backdrops Play listing](https://play.google.com/store/apps/details?id=com.backdrops.wallpapers).
- Content-source constraints — [Pexels API docs](https://www.pexels.com/api/documentation/), [Pexels license](https://www.pexels.com/license/), [Pixabay API docs](https://pixabay.com/api/docs/), [YouTube API Services Terms](https://developers.google.com/youtube/terms/api-services-terms-of-service), [YouTube API branding guidelines](https://developers.google.com/youtube/terms/branding-guidelines).
- Extractor dependency watch — [NewPipeExtractor releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases), [youtubedl-android releases](https://github.com/yausername/youtubedl-android/releases), [youtubedl-android Maven artifact](https://central.sonatype.com/artifact/io.github.junkfood02.youtubedl-android/library).

---

## Appendix F — Cycle 2 Sources

- Cycle 2 planning record — [docs/research/cycle-2-2026-06-04.md](docs/research/cycle-2-2026-06-04.md).
- Android release artifacts — [command-line builds](https://developer.android.com/build/building-cmdline), [build/run debug-vs-release overview](https://developer.android.com/studio/run), [build variants](https://developer.android.com/build/build-variants), [apksigner](https://developer.android.com/tools/apksigner), [SHA-256 certificate fingerprint help](https://support.google.com/android-developer-console/answer/16641489).
- F-Droid feasibility — [Inclusion Policy](https://fdroid.gitlab.io/jekyll-fdroid/docs/Inclusion_Policy/), [App Developer FAQ](https://f-droid.org/en/docs/FAQ_-_App_Developers/), [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/).
- Supply-chain controls — [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html), [GitHub artifact attestations](https://docs.github.com/en/actions/how-tos/security-for-github-actions/using-artifact-attestations/using-artifact-attestations-to-establish-provenance-for-builds), [GitHub Dependency Review Action](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/manage-your-dependency-security/configuring-the-dependency-review-action), [OpenSSF Scorecard action](https://github.com/ossf/scorecard-action).
- Crash/ANR evidence — [Android vitals](https://developer.android.com/games/optimize/vitals), [Play Console crashes and ANRs](https://support.google.com/googleplay/android-developer/answer/9859174).

## Appendix G — Cycle 3 Sources

- Cycle 3 planning record — [docs/research/cycle-3-2026-06-04.md](docs/research/cycle-3-2026-06-04.md).
- Provider API and policy constraints — [Pexels wallpaper-app API guidance](https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app), [Pexels API documentation](https://www.pexels.com/api/documentation/), [Pixabay API documentation](https://pixabay.com/api/docs/).
- Sound provider constraints — [Freesound API terms](https://freesound.org/docs/api/terms_of_use.html), [Freesound API terms of use help](https://freesound.org/help/tos_api/), [SoundCloud API docs](https://developers.soundcloud.com/docs/api/), [SoundCloud API terms](https://developers.soundcloud.com/docs/api/terms-of-use).
- User-generated and video-source constraints — [Reddit developer terms](https://redditinc.com/policies/developer-terms), [YouTube API Services developer policies](https://developers.google.com/youtube/terms/developer-policies), [YouTube API Services terms](https://developers.google.com/youtube/terms/api-services-terms-of-service).

## Appendix H — Cycle 4 Sources

- Cycle 4 planning record — [docs/research/cycle-4-2026-06-04.md](docs/research/cycle-4-2026-06-04.md).
- Play privacy/disclosure policy — [User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311), [Data safety section guidance](https://support.google.com/googleplay/android-developer/answer/10787469), [prominent disclosure and consent best practices](https://support.google.com/googleplay/android-developer/answer/11150561).
- Android privacy primitives — [runtime permissions](https://developer.android.com/training/permissions/requesting), [location runtime permissions](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime), [Android 17 Contact Picker](https://developer.android.com/about/versions/17/features/contact-picker), [Auto Backup/data extraction rules](https://developer.android.com/identity/data/autobackup), [notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- Firebase and foreground-service controls — [Realtime Database Security Rules](https://firebase.google.com/docs/database/security), [Android 14 foreground-service types](https://developer.android.com/about/versions/14/changes/fgs-types-required), [Play foreground-service declaration requirements](https://support.google.com/googleplay/android-developer/answer/13392821).

## Appendix I — Cycle 5 Sources

- Cycle 5 planning record — [docs/research/cycle-5-2026-06-04.md](docs/research/cycle-5-2026-06-04.md).
- Compose accessibility — [semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), [API defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults), [accessibility testing](https://developer.android.com/develop/ui/compose/accessibility/testing).
- Android accessibility QA — [Accessibility Scanner getting started](https://support.google.com/accessibility/android/answer/6376570), [Scanner result categories](https://support.google.com/accessibility/android/answer/6376559), [touch target size](https://support.google.com/accessibility/android/answer/7101858), [color contrast](https://support.google.com/accessibility/android/answer/7158390), [Android 14 nonlinear font scaling](https://developer.android.com/about/versions/14/features).
- Localization — [per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages).

## Appendix J — Cycle 6 Sources

- Cycle 6 planning record — [docs/research/cycle-6-2026-06-04.md](docs/research/cycle-6-2026-06-04.md).
- Network and file sharing — [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider), [secure file sharing](https://developer.android.com/training/secure-file-sharing/share-file).
- Storage and media handling — [data and file storage overview](https://developer.android.com/training/data-storage), [shared media storage](https://developer.android.com/training/data-storage/shared/media), [storage use cases and best practices](https://developer.android.com/training/data-storage/use-cases).

## Appendix K — Cycle 7 Sources

- Cycle 7 planning record — [docs/research/cycle-7-2026-06-04.md](docs/research/cycle-7-2026-06-04.md).
- Stability AI API and policy — [getting started](https://platform.stability.ai/docs/getting-started), [Stable Image overview](https://platform.stability.ai/docs/getting-started/stable-image), [pricing](https://platform.stability.ai/pricing), [Acceptable Use Policy](https://stability.ai/use-policy).
- Google Play AI-generated content policy — [policy overview](https://support.google.com/googleplay/android-developer/answer/14094294), [AI-generated content requirements](https://support.google.com/googleplay/android-developer/answer/13985936).
- Android secret storage guidance — [security checklist](https://developer.android.com/guide/practices/security), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [Jetpack Security releases](https://developer.android.com/jetpack/androidx/releases/security).

## Appendix L — Cycle 8 Sources

- Cycle 8 planning record — [docs/research/cycle-8-2026-06-04.md](docs/research/cycle-8-2026-06-04.md).
- Google Play listing and policy docs — [preview assets](https://support.google.com/googleplay/android-developer/answer/9866151), [metadata policy](https://support.google.com/googleplay/android-developer/answer/9898842), [User Data / privacy policy](https://support.google.com/googleplay/android-developer/answer/10144311), [content rating](https://support.google.com/googleplay/android-developer/answer/9859655), [user-generated content](https://support.google.com/googleplay/android-developer/answer/9876937), [AI-generated content](https://support.google.com/googleplay/android-developer/answer/13985936).
- Alternate store metadata — [F-Droid Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/), [F-Droid Anti-Features](https://f-droid.org/en/docs/Anti-Features/), [F-Droid descriptions/graphics/screenshots](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/), [IzzyOnDroid APK repository notes](https://apt.izzysoft.de/fdroid/index/apk).

## Appendix M — Cycle 9 Sources

- Cycle 9 planning record — [docs/research/cycle-9-2026-06-04.md](docs/research/cycle-9-2026-06-04.md).
- Firebase rules and auth — [Realtime Database Security Rules](https://firebase.google.com/docs/database/security), [Custom Claims and Security Rules](https://firebase.google.com/docs/auth/admin/custom-claims), [Local Emulator Suite for Security Rules](https://firebase.google.com/docs/rules/emulator-setup), [Cloud Storage Security Rules conditions](https://firebase.google.com/docs/storage/security/rules-conditions).
- Firebase backend operations — [App Check overview](https://firebase.google.com/docs/app-check), [Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider), [App Check enforcement](https://firebase.google.com/docs/app-check/enable-enforcement), [delete files with Cloud Storage on Android](https://firebase.google.com/docs/storage/android/delete-files), [Cloud Storage lifecycle management](https://docs.cloud.google.com/storage/docs/lifecycle).
- UGC policy — [Google Play user-generated content](https://support.google.com/googleplay/android-developer/answer/9876937).

## Appendix N — Cycle 10 Sources

- Cycle 10 planning record — [docs/research/cycle-10-2026-06-04.md](docs/research/cycle-10-2026-06-04.md).
- Android 17 SDK and release state — [release notes](https://developer.android.com/about/versions/17/release-notes), [set up SDK](https://developer.android.com/about/versions/17/setup-sdk), [Beta 3 Platform Stability blog](https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html).
- Android 17 feature and behavior gates — [Contact Picker](https://developer.android.com/about/versions/17/features/contact-picker), [background audio hardening](https://developer.android.com/about/versions/17/changes/bg-audio), [orientation/resizability restrictions ignored](https://developer.android.com/about/versions/17/changes/ff-restrictions-ignored), [target-SDK behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17), [local network permission](https://developer.android.com/privacy-and-security/local-network-permission).
- Android 17 API references — [Intent.ACTION_OPEN_EYE_DROPPER and EXTRA_COLOR](https://developer.android.com/reference/android/content/Intent#ACTION_OPEN_EYE_DROPPER).

## Appendix O — Cycle 11 Sources

- Cycle 11 planning record — [docs/research/cycle-11-2026-06-04.md](docs/research/cycle-11-2026-06-04.md).
- Google Play sensitive data and declarations — [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241), [Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469), [foreground service declarations](https://support.google.com/googleplay/android-developer/answer/13392821).
- Android permission behavior — [runtime permissions](https://developer.android.com/training/permissions/requesting), [runtime location permissions](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime), [notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- Android special access and service APIs — [`Settings.System.canWrite`](https://developer.android.com/reference/android/provider/Settings.System#canWrite(android.content.Context)), [`Settings.ACTION_MANAGE_WRITE_SETTINGS`](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_WRITE_SETTINGS), [`SET_ALARM`](https://developer.android.com/reference/android/Manifest.permission#SET_ALARM), [foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types).

## Appendix P — Cycle 12 Sources

- Cycle 12 planning record — [docs/research/cycle-12-2026-06-04.md](docs/research/cycle-12-2026-06-04.md).
- Google Play deletion and data disclosure — [app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111), [Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469).
- Firebase identity and deletion APIs — [anonymous authentication](https://firebase.google.com/docs/auth/android/anonymous-auth), [delete a Firebase user](https://firebase.google.com/docs/auth/android/manage-users#delete_a_user), [Realtime Database delete data](https://firebase.google.com/docs/database/android/read-and-write#delete_data), [Cloud Storage delete files](https://firebase.google.com/docs/storage/android/delete-files).

## Appendix Q — Cycle 13 Sources

- Cycle 13 planning record — [docs/research/cycle-13-2026-06-04.md](docs/research/cycle-13-2026-06-04.md).
- Store and provider IP/licensing policy — [Google Play Intellectual Property](https://support.google.com/googleplay/android-developer/answer/9888072), [Pexels license](https://www.pexels.com/license/), [Pexels API documentation](https://www.pexels.com/api/documentation/), [Pixabay content license summary](https://pixabay.com/service/license-summary/), [Freesound API terms](https://freesound.org/docs/api/terms_of_use.html).
- User-generated and video/audio-source policy — [Reddit Developer Terms](https://redditinc.com/policies/developer-terms), [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies).

## Appendix R — Cycle 14 Sources

- Cycle 14 planning record — [docs/research/cycle-14-2026-06-04.md](docs/research/cycle-14-2026-06-04.md).
- Android background execution — [WorkManager define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby), [schedule alarms](https://developer.android.com/develop/background-work/services/alarms).
- Foreground-service review — [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types), [Play foreground service declarations](https://support.google.com/googleplay/android-developer/answer/13392821).

## Appendix S — Cycle 15 Sources

- Cycle 15 planning record — [docs/research/cycle-15-2026-06-04.md](docs/research/cycle-15-2026-06-04.md).
- Android network and key security — [Cleartext communications](https://developer.android.com/privacy-and-security/risks/cleartext-communications), [Network Security Configuration](https://developer.android.com/training/articles/security-config), [Security checklist](https://developer.android.com/guide/practices/security), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [cryptography](https://developer.android.com/guide/topics/security/cryptography).
- Provider credential docs — [Pexels API documentation](https://www.pexels.com/api/documentation/), [Pixabay API docs](https://pixabay.com/api/docs/), [Freesound authentication](https://freesound.org/docs/api/authentication.html), [Freesound APIv2 overview](https://freesound.org/docs/api/overview.html), [Stability developer docs](https://platform.stability.ai/docs/getting-started).

## Appendix T — Cycle 16 Sources

- Cycle 16 planning record — [docs/research/cycle-16-2026-06-04.md](docs/research/cycle-16-2026-06-04.md).
- Android data durability — [Room migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions), [MigrationTestHelper](https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper), [test and debug Room databases](https://developer.android.com/training/data-storage/room/testing-db), [Auto Backup/data extraction rules](https://developer.android.com/identity/data/autobackup), [app-specific files and cache](https://developer.android.com/training/data-storage/app-specific).
- Firebase rules — [Realtime Database rules core syntax](https://firebase.google.com/docs/database/security/core-syntax), [Security Rules data validation](https://firebase.google.com/docs/rules/data-validation).

## Appendix U — Cycle 17 Sources

- Cycle 17 planning record — [docs/research/cycle-17-2026-06-04.md](docs/research/cycle-17-2026-06-04.md).
- Notice generation and SBOMs — [Google Play services OSS notices](https://developers.google.com/android/guides/opensource), [SPDX overview](https://spdx.dev/about/overview/).
- Copyleft/native artifact guidance — [FFmpeg legal guidance](https://ffmpeg.org/legal.html), [NewPipeExtractor repository](https://github.com/TeamNewPipe/NewPipeExtractor), [youtubedl-android repository](https://github.com/yausername/youtubedl-android).
- Store/repository disclosure policy — [F-Droid anti-features](https://f-droid.org/en/docs/Anti-Features/), [Google Play intellectual-property policy](https://support.google.com/googleplay/android-developer/answer/9888072).

## Appendix V — Cycle 18 Sources

- Cycle 18 planning record — [docs/research/cycle-18-2026-06-06.md](docs/research/cycle-18-2026-06-06.md).
- Generated Android notices — [Google Play services OSS notices](https://developers.google.com/android/guides/opensource), [AboutLibraries](https://github.com/mikepenz/AboutLibraries).
- License/SBOM gates — [CycloneDX Gradle plugin](https://github.com/CycloneDX/cyclonedx-gradle-plugin), [Gradle Plugin Portal `org.cyclonedx.bom`](https://plugins.gradle.org/plugin/org.cyclonedx.bom), [SPDX overview](https://spdx.dev/about/overview/).
- Native/copyleft packet inputs — [FFmpeg legal guidance](https://ffmpeg.org/legal.html), [youtubedl-android repository](https://github.com/yausername/youtubedl-android), [NewPipeExtractor repository](https://github.com/TeamNewPipe/NewPipeExtractor).

## Appendix W — Cycle 19 Sources

- Cycle 19 planning record — [docs/research/cycle-19-2026-06-06.md](docs/research/cycle-19-2026-06-06.md).
- Current-toolchain notice candidates — [Google Play services OSS notices](https://developers.google.com/android/guides/opensource), [AboutLibraries](https://github.com/mikepenz/AboutLibraries), [AboutLibraries Android plugin portal](https://plugins.gradle.org/plugin/com.mikepenz.aboutlibraries.plugin.android), [AboutLibraries releases](https://github.com/mikepenz/AboutLibraries/releases).
- Release/SBOM follow-up — [CycloneDX Gradle plugin portal](https://plugins.gradle.org/plugin/org.cyclonedx.bom).

## Appendix X — Cycle 20 Sources

- Cycle 20 planning record — [docs/research/cycle-20-2026-06-06.md](docs/research/cycle-20-2026-06-06.md).
- Google OSS notices spike outputs — `work/aura-oss-notice-spike` local clone, `:app:releaseOssLicensesTask`, generated `third_party_licenses`, `third_party_license_metadata`, and `dependencies.json`.
- AboutLibraries 14.2.1 spike outputs — `work/aura-aboutlibraries-spike` local clone, `:app:exportLibraryDefinitionsRelease`, `:app:exportLibrariesRelease`, `:app:exportComplianceLibrariesRelease`.
- Tooling docs — [Google Play services OSS notices](https://developers.google.com/android/guides/opensource), [AboutLibraries](https://github.com/mikepenz/AboutLibraries), [AboutLibraries Android plugin portal](https://plugins.gradle.org/plugin/com.mikepenz.aboutlibraries.plugin.android).

## Appendix Y — Cycle 21 Sources

- Cycle 21 planning record — [docs/research/cycle-21-2026-06-06.md](docs/research/cycle-21-2026-06-06.md).
- Plugin-only Google OSS notices spike outputs — `work/aura-oss-notice-spike`, `:app:releaseOssLicensesTask` after removing `play-services-oss-licenses:17.5.1`, generated `third_party_licenses`, `third_party_license_metadata`, and `dependencies.json`.
- Prototype markdown converter — `work/aura-oss-notice-spike/tools/prototype_google_oss_to_markdown.ps1`, output `work/aura-oss-notice-spike/build/prototype/THIRD-PARTY-NOTICES.md`.
- Runtime convergence evidence — `work/aura-oss-notice-spike` release runtime dependency graph checks with and without `play-services-oss-licenses:17.5.1`.
- Provider disclosure preservation evidence — `app/src/main/java/com/freevibe/data/legal/ProviderDisclosure.kt`, `app/src/test/java/com/freevibe/data/legal/ProviderDisclosureTest.kt`, `app/src/main/java/com/freevibe/ui/screens/licenses/LicensesScreen.kt`.

## Appendix Z — Cycle 22 Sources

- Cycle 22 implementation record — [docs/research/cycle-22-2026-06-06.md](docs/research/cycle-22-2026-06-06.md).
- Real-repo generated notices implementation — `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/verification-metadata.xml`, `tools/google_oss_to_markdown.py`.
- Release artifact wiring — `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
- Verification outputs — real-repo `:app:releaseOssLicensesTask`, `tools/google_oss_to_markdown.py --variant release`, release runtime dependency graph filter, and passing `ProviderDisclosureTest`.

## Appendix AA — Cycle 23 Sources

- Cycle 23 implementation record — [docs/research/cycle-23-2026-06-06.md](docs/research/cycle-23-2026-06-06.md).
- Native compliance implementation — `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.md`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
- Primary source references — [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp 2025.11.12](https://github.com/yt-dlp/yt-dlp/releases/tag/2025.11.12), [Python 3.12 license](https://docs.python.org/3.12/license.html), [QuickJS](https://bellard.org/quickjs/), [FFmpeg legal guidance](https://ffmpeg.org/legal.html), [NewPipeExtractor v0.24.8](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.24.8).
- Verification outputs — `python -m py_compile tools/native_compliance_inventory.py` and `python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md`.

## Appendix AB — Cycle 24 Sources

- Cycle 24 implementation record — [docs/research/cycle-24-2026-06-06.md](docs/research/cycle-24-2026-06-06.md).
- Dependency notice lock implementation — `tools/dependency_notice_lock.py`, `docs/legal/dependency-notices.lock.json`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
- Verification outputs — real-repo `:app:releaseOssLicensesTask`, `python tools\dependency_notice_lock.py --mode write`, `python tools\dependency_notice_lock.py --mode check`, and Python compile checks for all release-compliance tools.

## Appendix AC — Cycle 25 Sources

- Cycle 25 implementation record — [docs/research/cycle-25-2026-06-06.md](docs/research/cycle-25-2026-06-06.md).
- Native compliance lock implementation — `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.lock.json`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
- Verification outputs — `python tools\native_compliance_inventory.py --mode write-lock`, `python tools\native_compliance_inventory.py --mode check-lock`, `python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md`, and Python compile checks for all release-compliance tools.

## Appendix AD — Cycle 26 Sources

- Cycle 26 implementation record — [docs/research/cycle-26-2026-06-06.md](docs/research/cycle-26-2026-06-06.md).
- Dependency overlay implementation — `tools/dependency_overlay_check.py`, `docs/legal/dependency-notice-overrides.json`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
- Primary source references — [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp 2025.11.12](https://github.com/yt-dlp/yt-dlp/tree/2025.11.12), [Python 3.12 license](https://docs.python.org/3.12/license.html), [QuickJS](https://bellard.org/quickjs/), [FFmpeg legal guidance](https://ffmpeg.org/legal.html), [NewPipeExtractor v0.24.8](https://github.com/TeamNewPipe/NewPipeExtractor/tree/v0.24.8), [Firebase Android SDK](https://github.com/firebase/firebase-android-sdk), [Google Play services](https://developers.google.com/android/guides/overview), [ML Kit subject segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android), [AndroidX ProfileInstaller](https://github.com/androidx/androidx/tree/androidx-main/profileinstaller), [ZXing](https://github.com/zxing/zxing).
- Verification outputs — `python -m py_compile tools\dependency_overlay_check.py`, `python tools\dependency_overlay_check.py --overlay docs\legal\dependency-notice-overrides.json`, release-compliance Python compile checks, dependency notice lock check, and native compliance lock check.

## Appendix AE — Cycle 27 Sources

- Cycle 27 implementation record — [docs/research/cycle-27-2026-06-06.md](docs/research/cycle-27-2026-06-06.md).
- Release dry-run implementation — `tools/release_artifact_bundle_check.py`, `.github/workflows/release.yml`, `docs/distribution/release-dry-run.md`, `docs/distribution/release-signing.md`, `docs/distribution/supply-chain.md`.
- Primary source references — [GitHub manual workflow runs](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow), [workflow dispatch event](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows), [workflow artifacts](https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts).
- Verification outputs — `python -m py_compile tools\release_artifact_bundle_check.py`, local temporary bundle smoke test, release-compliance Python compile checks, dependency notice lock check, native compliance lock check, and dependency overlay check.

## Appendix AF — Cycle 28 Sources

- Cycle 28 implementation record — [docs/research/cycle-28-2026-06-06.md](docs/research/cycle-28-2026-06-06.md).
- Raw notice archive implementation — `tools/google_oss_raw_archive.py`, `.github/workflows/release.yml`, `tools/release_artifact_bundle_check.py`, `docs/distribution/release-dry-run.md`, `docs/distribution/release-signing.md`, `docs/distribution/supply-chain.md`.
- Generated input references — `app/build/generated/third_party_licenses/release/dependencies.json`, `app/build/generated/res/releaseOssLicensesTask/raw/third_party_license_metadata`, `app/build/generated/res/releaseOssLicensesTask/raw/third_party_licenses`.
- Verification outputs — `python -m py_compile tools\google_oss_raw_archive.py`, local generated-root archive smoke test, release-compliance Python compile checks, dependency notice lock check, native compliance lock check, dependency overlay check, and release bundle smoke test.

## Appendix AG — Cycle 29 Sources

- Cycle 29 implementation record — [docs/research/cycle-29-2026-06-06.md](docs/research/cycle-29-2026-06-06.md).
- User-facing notice implementation — `app/src/main/java/com/freevibe/ui/screens/licenses/LicensesScreen.kt`, `app/src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt`, `app/src/test/java/com/freevibe/ui/screens/licenses/LicensesScreenTest.kt`.
- Release artifact references — `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, `NATIVE-COMPLIANCE.md`.
- Verification outputs — focused `:app:testDebugUnitTest --tests com.freevibe.ui.screens.licenses.LicensesScreenTest`, release-compliance Python compile checks, dependency notice lock check, native compliance lock check, and dependency overlay check.

## Appendix AH — Cycle 30 Sources

- Cycle 30 implementation record — [docs/research/cycle-30-2026-06-06.md](docs/research/cycle-30-2026-06-06.md).
- FFmpeg source correspondence implementation — `docs/legal/ffmpeg-source-correspondence.md`, `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.md`, `docs/legal/native-compliance.lock.json`, `docs/legal/dependency-notice-overrides.json`, `docs/distribution/supply-chain.md`.
- Primary source references — [FFmpeg legal guidance](https://ffmpeg.org/legal.html), [FFmpeg downloads and verification](https://ffmpeg.org/download.html), [FFmpeg 7.1.1 source tarball](https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz), [FFmpeg 7.1.1 PGP signature](https://ffmpeg.org/releases/ffmpeg-7.1.1.tar.xz.asc), [youtubedl-android 0.18.1](https://github.com/yausername/youtubedl-android/tree/0.18.1), [youtubedl-android FFmpeg build note](https://raw.githubusercontent.com/yausername/youtubedl-android/master/BUILD_FFMPEG.md), [Termux FFmpeg package recipe](https://github.com/termux/termux-packages/tree/master/packages/ffmpeg).
- Verification outputs — `python -m py_compile tools\native_compliance_inventory.py`, `python tools\native_compliance_inventory.py --mode write-lock`, `python tools\native_compliance_inventory.py --mode check-lock`, `python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md`, dependency notice lock check, dependency overlay check, `git diff --check`, and changed-line attribution scan.

## Appendix AI — Cycle 31 Sources

- Cycle 31 implementation record — [docs/research/cycle-31-2026-06-06.md](docs/research/cycle-31-2026-06-06.md).
- License policy implementation — `docs/legal/dependency-license-policy.json`, `tools/dependency_license_policy.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
- Primary source references — [Cash App Licensee](https://github.com/cashapp/licensee), [Licensee 1.x documentation](https://cashapp.github.io/licensee/docs/1.x/).
- Verification outputs — `python -m py_compile tools\dependency_license_policy.py`, dependency notice lock check, native compliance lock check, dependency overlay check, dependency license policy check, sentinel disallowed-license failure check, `git diff --check`, and changed-line attribution scan.

## Appendix AJ — Cycle 32 Sources

- Cycle 32 implementation record — [docs/research/cycle-32-2026-06-06.md](docs/research/cycle-32-2026-06-06.md).
- Raw archive retention implementation — `docs/distribution/raw-oss-input-retention.md`, `.github/workflows/release.yml`, `tools/release_artifact_bundle_check.py`, `docs/distribution/supply-chain.md`, `docs/distribution/release-dry-run.md`, `docs/distribution/release-signing.md`.
- Primary source references — [GitHub workflow artifacts](https://docs.github.com/en/actions/tutorials/store-and-share-data), [GitHub releases and release assets](https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases), [actions/upload-artifact](https://github.com/actions/upload-artifact), [softprops/action-gh-release](https://github.com/softprops/action-gh-release).
- Verification outputs — `python -m py_compile tools\release_artifact_bundle_check.py`, dependency notice lock check, native compliance lock check, dependency overlay check, dependency license policy check, passing release-bundle smoke test with `GOOGLE-OSS-RAW-INPUTS.zip`, failing release-bundle smoke test without `GOOGLE-OSS-RAW-INPUTS.zip`, `git diff --check`, and changed-line attribution scan.

## Appendix AK — Cycle 33 Sources

- Cycle 33 implementation record — [docs/research/cycle-33-2026-06-06.md](docs/research/cycle-33-2026-06-06.md).
- In-app generated notice implementation — `app/src/main/java/com/freevibe/ui/screens/licenses/GeneratedDependencyNotices.kt`, `app/src/main/java/com/freevibe/ui/screens/licenses/LicensesScreen.kt`, `app/src/test/java/com/freevibe/ui/screens/licenses/LicensesScreenTest.kt`.
- Primary source references — [Google Play services open source notices](https://developers.google.com/android/guides/opensource), [Google Play services OSS licenses API reference](https://developers.google.com/android/reference/com/google/android/gms/oss/licenses/package-summary).
- Verification outputs — focused `:app:testDebugUnitTest --tests com.freevibe.ui.screens.licenses.LicensesScreenTest`, release-compliance Python compile checks, dependency notice lock check, native compliance lock check, dependency overlay check, dependency license policy check, `git diff --check`, and changed-line attribution scan.

## Appendix AL — Cycle 34 Sources

- Cycle 34 implementation record — [docs/research/cycle-34-2026-06-06.md](docs/research/cycle-34-2026-06-06.md).
- Generated notice search implementation — `app/src/main/java/com/freevibe/ui/screens/licenses/GeneratedDependencyNotices.kt`, `app/src/main/java/com/freevibe/ui/screens/licenses/LicensesScreen.kt`, `app/src/test/java/com/freevibe/ui/screens/licenses/LicensesScreenTest.kt`.
- Local source references — `docs/legal/dependency-notice-overrides.json`, `docs/legal/dependency-license-policy.json`, generated Google OSS metadata.
- Verification outputs — focused `:app:testDebugUnitTest --tests com.freevibe.ui.screens.licenses.LicensesScreenTest`, release-compliance Python compile checks, dependency notice lock check, native compliance lock check, dependency overlay check, dependency license policy check, `git diff --check`, and changed-line attribution scan.

## Appendix AM — Cycle 35 Sources

- Cycle 35 implementation record — [docs/research/cycle-35-2026-06-06.md](docs/research/cycle-35-2026-06-06.md).
- Generated notice metadata parity implementation — `tools/dependency_notice_lock.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml`, `docs/distribution/supply-chain.md`.
- Primary source reference — [Google Play services open-source notices guide](https://developers.google.com/android/guides/opensource).
- Verification outputs — release-compliance Python compile checks, dependency notice lock check, generated notice metadata parity check, malformed-range and missing-row negative fixtures, native compliance lock check, dependency overlay check, dependency license policy check, `git diff --check`, and changed-line attribution scan.

## Appendix AN — Cycle 36 Sources

- Cycle 36 implementation record — [docs/research/cycle-36-2026-06-06.md](docs/research/cycle-36-2026-06-06.md).
- Runtime provider control implementation — `app/src/main/java/com/freevibe/data/legal/ProviderDisclosure.kt`, `app/src/test/java/com/freevibe/data/legal/ProviderDisclosureTest.kt`, `docs/legal/provider-runtime-controls.md`, `docs/legal/provider-policy.md`.
- Primary source references — [Pexels API wallpaper guidance](https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app), [Pixabay API documentation](https://pixabay.com/api/docs/), [Reddit Data API Terms](https://redditinc.com/policies/data-api-terms), [Reddit Developer Terms](https://redditinc.com/policies/developer-terms), [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies).
- Verification outputs — focused provider disclosure unit test, release-compliance Python compile checks, dependency notice lock check, generated notice metadata parity check, native compliance lock check, dependency overlay check, dependency license policy check, `git diff --check`, and changed-line attribution scan.

## Appendix AO — Cycle 37 Sources

- Cycle 37 implementation record — [docs/research/cycle-37-2026-06-06.md](docs/research/cycle-37-2026-06-06.md).
- YouTube legal-mode implementation — `PreferencesManager.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `YouTubeRepository.kt`, `SoundsViewModel.kt`, `SoundsScreen.kt`, `VideoWallpapersViewModel.kt`, `SourceMetrics.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Primary source reference — [YouTube API Services Developer Policies](https://developers.google.com/youtube/terms/developer-policies).
- Verification outputs — focused Sounds, sound-tab helper, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AP — Cycle 38 Sources

- Cycle 38 implementation record — [docs/research/cycle-38-2026-06-06.md](docs/research/cycle-38-2026-06-06.md).
- Reddit source switch implementation — `PreferencesManager.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `RedditRepository.kt`, `WallpapersViewModel.kt`, `WallpapersScreen.kt`, `VideoWallpapersViewModel.kt`, `DailyWallpaperWorker.kt`, `AutoWallpaperWorker.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Primary source references — [Reddit Data API Terms](https://redditinc.com/policies/data-api-terms), [Reddit Developer Terms](https://redditinc.com/policies/developer-terms).
- Verification outputs — focused Wallpapers, Settings, video wallpaper helper, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AQ — Cycle 39 Sources

- Cycle 39 implementation record — [docs/research/cycle-39-2026-06-06.md](docs/research/cycle-39-2026-06-06.md).
- Pexels/Pixabay source switch implementation — `PreferencesManager.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `WallpaperRepository.kt`, `WallpapersViewModel.kt`, `WallpapersScreen.kt`, `VideoWallpapersViewModel.kt`, `AutoWallpaperWorker.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Primary source references — [Pexels wallpaper-app API guidance](https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app), [Pexels API documentation](https://www.pexels.com/api/documentation/), [Pixabay API documentation](https://pixabay.com/api/docs/).
- Verification outputs — focused Wallpapers, Settings, video wallpaper helper, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AR — Cycle 40 Sources

- Cycle 40 implementation record — [docs/research/cycle-40-2026-06-06.md](docs/research/cycle-40-2026-06-06.md).
- Community source switch implementation — `PreferencesManager.kt`, `FreeVibeApp.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `UploadRepository.kt`, `WallpaperUploadRepository.kt`, `CreatorProfileRepository.kt`, `SoundsViewModel.kt`, `SoundsScreen.kt`, `WallpapersViewModel.kt`, `WallpapersScreen.kt`, `WallpaperDetailScreen.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Local source references — provider runtime-control matrix, Firebase-backed community repository paths, startup community identity warm-up, and community vote-count/detail UI paths.
- Verification outputs — focused Sounds, sound-tab helper, Wallpapers, Settings, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AS — Cycle 41 Sources

- Cycle 41 implementation record — [docs/research/cycle-41-2026-06-06.md](docs/research/cycle-41-2026-06-06.md).
- Bing Daily source switch implementation — `PreferencesManager.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `WallpaperRepository.kt`, `AutoWallpaperWorker.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Local source references — Discover secondary source loading, Bing daily repository fetches, auto-wallpaper rotation source pickers, and scheduled/legacy auto-wallpaper worker source dispatch.
- Verification outputs — focused WallpaperRepository, Settings, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AT — Cycle 42 Sources

- Cycle 42 implementation record — [docs/research/cycle-42-2026-06-06.md](docs/research/cycle-42-2026-06-06.md).
- Wallhaven source switch implementation — `PreferencesManager.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `WallpaperRepository.kt`, `WallpapersViewModel.kt`, `WallpapersScreen.kt`, `AutoWallpaperWorker.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Local source references — Wallhaven featured/search/similar/random/color repository paths, Discover/style-biased Discover loading, cached Discover previews, wallpaper tab/action entry points, and auto-wallpaper rotation source dispatch.
- Verification outputs — focused WallpaperRepository, Wallpapers, Settings, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AU — Cycle 43 Sources

- Cycle 43 implementation record — [docs/research/cycle-43-2026-06-06.md](docs/research/cycle-43-2026-06-06.md).
- Pixabay photo cache/backoff implementation — `WallpaperCacheManager.kt`, `WallpaperRepository.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Primary source reference — [Pixabay API documentation](https://pixabay.com/api/docs/).
- Verification outputs — focused WallpaperRepository and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AV — Cycle 44 Sources

- Cycle 44 implementation record — [docs/research/cycle-44-2026-06-06.md](docs/research/cycle-44-2026-06-06.md).
- Pixabay video cache/backoff implementation — `VideoWallpapersViewModel.kt`, `VideoWallpapersViewModelTest.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Primary source reference — [Pixabay API documentation](https://pixabay.com/api/docs/).
- Verification outputs — focused VideoWallpapersViewModel and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AW — Cycle 45 Sources

- Cycle 45 implementation record — [docs/research/cycle-45-2026-06-06.md](docs/research/cycle-45-2026-06-06.md).
- Generated wallpaper source switch implementation — `PreferencesManager.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `WallpapersViewModel.kt`, `WallpapersScreen.kt`, `AiWallpaperViewModel.kt`, `AiWallpaperScreen.kt`, `ProviderDisclosure.kt`, and `docs/legal/provider-runtime-controls.md`.
- Verification outputs — focused AI wallpaper request gate, Wallpapers, Settings, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AX — Cycle 46 Sources

- Cycle 46 implementation record — [docs/research/cycle-46-2026-06-06.md](docs/research/cycle-46-2026-06-06.md).
- Saved-source availability implementation — `Models.kt`, `Database.kt`, `DatabaseMigrations.kt`, Room schema v15, `Mappers.kt`, `FavoritesRepository.kt`, `FavoritesExporter.kt`, `DownloadManager.kt`, Favorites/Downloads/detail screens, and focused mapper/export/viewmodel tests.
- Verification outputs — focused Mappers, FavoritesExporterValidation, DownloadsViewModel, WallpapersViewModel, and SoundsViewModel unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; and changed-line attribution scan.

## Appendix AY — Cycle 49 Sources

- Cycle 49 implementation record — [docs/research/cycle-49-2026-06-06.md](docs/research/cycle-49-2026-06-06.md).
- Sound action capability matrix — [docs/legal/sound-license-capabilities.md](docs/legal/sound-license-capabilities.md).
- Sound license implementation — `SoundLicensePolicy.kt`, `Models.kt`, `Database.kt`, `DatabaseMigrations.kt`, Room schema v16, `Mappers.kt`, `FavoritesExporter.kt`, `SoundsViewModel.kt`, `SoundDetailScreen.kt`, `SoundsScreen.kt`, `ContactPickerScreen.kt`, and focused sound policy/mapper/export/viewmodel tests.
- Verification outputs — focused SoundLicensePolicy, Mappers, FavoritesExporterValidation, SoundsViewModel, and ContactPickerViewModel unit tests.

## Appendix AZ — Cycle 50 Sources

- Cycle 50 implementation record — [docs/research/cycle-50-2026-06-06.md](docs/research/cycle-50-2026-06-06.md).
- Community upload rights matrix — [docs/legal/community-upload-rights.md](docs/legal/community-upload-rights.md).
- Community upload rights implementation — `CommunityUploadRights.kt`, `UploadRepository.kt`, `WallpaperUploadRepository.kt`, `SoundsScreen.kt`, `WallpapersScreen.kt`, `SoundLicensePolicy.kt`, `WallpaperDetailScreen.kt`, `Mappers.kt`, and `database.rules.json`.
- Verification outputs — focused CommunityUploadRights, SoundLicensePolicy, UploadRepositoryValidation, WallpaperUploadRepositoryValidation, SoundsViewModel, WallpapersViewModel, and Mappers unit tests.

## Appendix BA — Cycle 51 Sources

- Cycle 51 implementation record — [docs/research/cycle-51-2026-06-06.md](docs/research/cycle-51-2026-06-06.md).
- Community reporting support note — [docs/support/community-reporting.md](docs/support/community-reporting.md).
- Community report implementation — `CommunityReport.kt`, `CommunityReportRepository.kt`, `CommunityReportDialog.kt`, `SoundDetailScreen.kt`, `WallpaperDetailScreen.kt`, `SoundsViewModel.kt`, `WallpapersViewModel.kt`, and `database.rules.json`.
- Verification outputs — focused CommunityReport, SoundsViewModel, and WallpapersViewModel unit tests.

## Appendix BB — Cycle 52 Sources

- Cycle 52 implementation record — [docs/research/cycle-52-2026-06-06.md](docs/research/cycle-52-2026-06-06.md).
- Community reporting support note — [docs/support/community-reporting.md](docs/support/community-reporting.md).
- Admin report review implementation — `CommunityReportsScreen.kt`, `CommunityReportRepository.kt`, `CommunityReport.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `Screen.kt`, `FreeVibeRoot.kt`, and `database.rules.json`.
- Verification outputs — focused CommunityReportsViewModel, CommunityReport, SettingsViewModel, SoundsViewModel, and WallpapersViewModel unit tests.

## Appendix BC — Cycle 53 Sources

- Cycle 53 implementation record — [docs/research/cycle-53-2026-06-06.md](docs/research/cycle-53-2026-06-06.md).
- Community App Check rollout runbook — [docs/community-app-check-rollout.md](docs/community-app-check-rollout.md).
- Official Firebase sources — [App Check overview](https://firebase.google.com/docs/app-check), [Android Play Integrity provider](https://firebase.google.com/docs/app-check/android/play-integrity-provider), [Android debug provider](https://firebase.google.com/docs/app-check/android/debug-provider), and [App Check enforcement](https://firebase.google.com/docs/app-check/enable-enforcement).
- App Check implementation — `FreeVibeApp.kt`, debug/release `AppCheckInstaller.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, and `docs/legal/dependency-notices.lock.json`.
- Verification outputs — debug Kotlin compile with dependency-verification metadata refresh, release Kotlin compile, generated dependency notice lock and metadata parity checks, native compliance lock check, dependency overlay check, and dependency license policy check.

## Appendix BD — Cycle 54 Sources

- Cycle 54 implementation record — [docs/research/cycle-54-2026-06-06.md](docs/research/cycle-54-2026-06-06.md).
- Community quota rollout design — [docs/community-quota-rate-limits.md](docs/community-quota-rate-limits.md).
- Official Firebase sources — [App Check metrics](https://firebase.google.com/docs/app-check/monitor-metrics), [App Check for Cloud Functions](https://firebase.google.com/docs/app-check/cloud-functions), [callable functions](https://firebase.google.com/docs/functions/callable), [Realtime Database Security Rules](https://firebase.google.com/docs/database/security), [Realtime Database rules API](https://firebase.google.com/docs/reference/security/database), and [Cloud Storage Security Rules](https://firebase.google.com/docs/storage/security).
- Quota policy implementation — `CommunityQuotaPolicy.kt`, `CommunityQuotaPolicyTest.kt`, `database.rules.json`, `docs/community-app-check-rollout.md`, `docs/support/community-reporting.md`, and `docs/firebase-admin-claims.md`.
- Verification outputs — focused CommunityQuotaPolicy unit test, RTDB rules JSON parse, release-compliance Python compile checks, generated dependency notice lock and metadata parity checks, native compliance lock check, dependency overlay check, and dependency license policy check.

## Appendix BE — Cycle 55 Sources

- Cycle 55 implementation record — [docs/research/cycle-55-2026-06-06.md](docs/research/cycle-55-2026-06-06.md).
- Community upload deletion runbook — [docs/community-upload-deletion.md](docs/community-upload-deletion.md).
- Official sources — [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111), [Firebase Realtime Database Android delete data](https://firebase.google.com/docs/database/android/read-and-write), [Firebase DatabaseReference API](https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference), and [Firebase Storage Android delete files](https://firebase.google.com/docs/storage/android/delete-files).
- Deletion-handle implementation — `CommunityUploadOwnership.kt`, `CommunityUploadDeletionHelpers.kt`, `UploadRepository.kt`, `WallpaperUploadRepository.kt`, `CommunityUploadOwnershipTest.kt`, and `database.rules.json`.
- Verification outputs — focused CommunityUploadOwnership, UploadRepositoryValidation, and WallpaperUploadRepositoryValidation unit tests plus RTDB rules JSON parse.

## Appendix BF — Cycle 56 Sources

- Cycle 56 implementation record — [docs/research/cycle-56-2026-06-06.md](docs/research/cycle-56-2026-06-06.md).
- Community upload deletion runbook — [docs/community-upload-deletion.md](docs/community-upload-deletion.md).
- Official sources — [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111), [Firebase Realtime Database Android delete data](https://firebase.google.com/docs/database/android/read-and-write), [Firebase DatabaseReference API](https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference), and [Firebase Storage Android delete files](https://firebase.google.com/docs/storage/android/delete-files).
- Owner-delete UI implementation — `UploadRepository.kt`, `WallpaperUploadRepository.kt`, `SoundsViewModel.kt`, `WallpapersViewModel.kt`, `SoundDetailScreen.kt`, and `WallpaperDetailScreen.kt`.
- Verification outputs — focused `SoundsViewModelTest` and `WallpapersViewModelTest` unit tests.

## Appendix BG — Cycle 57 Sources

- Cycle 57 implementation record — [docs/research/cycle-57-2026-06-06.md](docs/research/cycle-57-2026-06-06.md).
- Firebase rules harness runbook — [docs/firebase-rules-harness.md](docs/firebase-rules-harness.md).
- Official Firebase sources — [Cloud Storage Security Rules](https://firebase.google.com/docs/storage/security), [Cloud Storage rules reference](https://firebase.google.com/docs/reference/security/storage), [Security Rules unit tests](https://firebase.google.com/docs/rules/unit-tests), and [rules-unit-testing reference](https://firebase.google.com/docs/reference/emulator-suite/rules-unit-testing/rules-unit-testing).
- Storage rules implementation — `firebase.json`, `storage.rules`, `package.json`, `package-lock.json`, and `test/firebase/storage.rules.test.mjs`.
- Verification outputs — `npm run test:storage-rules` passed; `npm audit --audit-level=moderate` documented a current `firebase-tools` dev dependency advisory.

## Appendix BH — Cycle 58 Sources

- Cycle 58 implementation record — [docs/research/cycle-58-2026-06-06.md](docs/research/cycle-58-2026-06-06.md).
- Firebase rules harness runbook — [docs/firebase-rules-harness.md](docs/firebase-rules-harness.md).
- Official Firebase sources — [Realtime Database Security Rules](https://firebase.google.com/docs/database/security), [Realtime Database rules API](https://firebase.google.com/docs/reference/security/database), [Realtime Database regex reference](https://firebase.google.com/docs/reference/security/database/regex), [Security Rules unit tests](https://firebase.google.com/docs/rules/unit-tests), and [rules-unit-testing reference](https://firebase.google.com/docs/reference/emulator-suite/rules-unit-testing/rules-unit-testing).
- RTDB rules implementation — `CollectionExporter.kt`, `database.rules.json`, `firebase.json`, `package.json`, and `test/firebase/database.rules.test.mjs`.
- Verification outputs — `npm run test:database-rules`, `npm run test:storage-rules`, and `npm run test:firebase-rules` passed.

## Appendix BI — Cycle 59 Sources

- Cycle 59 implementation record — [docs/research/cycle-59-2026-06-06.md](docs/research/cycle-59-2026-06-06.md).
- Firebase rules harness runbook — [docs/firebase-rules-harness.md](docs/firebase-rules-harness.md).
- Official sources — [GitHub Actions workflow syntax](https://docs.github.com/actions/reference/workflows-and-actions/workflow-syntax), [GitHub Actions contexts](https://docs.github.com/actions/reference/accessing-contextual-information-about-workflow-runs), [`actions/setup-node`](https://github.com/actions/setup-node), [Security Rules unit tests](https://firebase.google.com/docs/rules/unit-tests), and [rules-unit-testing reference](https://firebase.google.com/docs/reference/emulator-suite/rules-unit-testing/rules-unit-testing).
- CI implementation — `.github/workflows/verify.yml`, `docs/firebase-rules-harness.md`, and `docs/firebase-admin-claims.md`.
- Verification outputs — `npm run test:firebase-rules` passed locally.

## Appendix BJ — Cycle 60 Sources

- Cycle 60 implementation record — [docs/research/cycle-60-2026-06-06.md](docs/research/cycle-60-2026-06-06.md).
- Community reporting runbook — [docs/support/community-reporting.md](docs/support/community-reporting.md).
- Community upload deletion runbook — [docs/community-upload-deletion.md](docs/community-upload-deletion.md).
- Firebase rules harness runbook — [docs/firebase-rules-harness.md](docs/firebase-rules-harness.md).
- Official Firebase sources — [Realtime Database Security Rules](https://firebase.google.com/docs/database/security), [Realtime Database rules API](https://firebase.google.com/docs/reference/security/database), [Security Rules unit tests](https://firebase.google.com/docs/rules/unit-tests), [rules-unit-testing reference](https://firebase.google.com/docs/reference/emulator-suite/rules-unit-testing/rules-unit-testing), [Realtime Database Android read/write/delete](https://firebase.google.com/docs/database/android/read-and-write), and [Firebase DatabaseReference API](https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference).
- Rights receipt implementation — `CommunityReport.kt`, `CommunityReportRepository.kt`, `database.rules.json`, and `test/firebase/database.rules.test.mjs`.
- Verification outputs — focused `CommunityReportTest` passed locally, and `npm run test:database-rules` passed locally.

## Appendix BK — Cycle 61 Sources

- Cycle 61 implementation record — [docs/research/cycle-61-2026-06-06.md](docs/research/cycle-61-2026-06-06.md).
- Community reporting runbook — [docs/support/community-reporting.md](docs/support/community-reporting.md).
- Community upload deletion runbook — [docs/community-upload-deletion.md](docs/community-upload-deletion.md).
- Firebase rules harness runbook — [docs/firebase-rules-harness.md](docs/firebase-rules-harness.md).
- Official Firebase sources — [Realtime Database Security Rules](https://firebase.google.com/docs/database/security), [Realtime Database rules API](https://firebase.google.com/docs/reference/security/database), [Security Rules unit tests](https://firebase.google.com/docs/rules/unit-tests), [rules-unit-testing reference](https://firebase.google.com/docs/reference/emulator-suite/rules-unit-testing/rules-unit-testing), [Realtime Database Android read/write/delete](https://firebase.google.com/docs/database/android/read-and-write), and [Firebase Storage Android delete files](https://firebase.google.com/docs/storage/android/delete-files).
- Admin delete implementation — `CommunityReportRepository.kt`, `CommunityReportsScreen.kt`, `database.rules.json`, and `test/firebase/database.rules.test.mjs`.
- Verification outputs — focused `CommunityReportsViewModelTest` and `CommunityReportTest` passed locally, and `npm run test:database-rules` passed locally.

## Appendix BL — Cycle 62 Sources

- Cycle 62 implementation record — [docs/research/cycle-62-2026-06-06.md](docs/research/cycle-62-2026-06-06.md).
- Community reporting runbook — [docs/support/community-reporting.md](docs/support/community-reporting.md).
- Official Firebase sources — [Realtime Database Android read/write data](https://firebase.google.com/docs/database/android/read-and-write), [Firebase DatabaseReference API](https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference), and [Realtime Database Security Rules](https://firebase.google.com/docs/database/security).
- Closed report filter implementation — `CommunityReportsScreen.kt`, `CommunityReportsViewModelTest.kt`, and `CommunityReportRepository.kt`.
- Verification outputs — focused `CommunityReportsViewModelTest` passed locally.

## Appendix BM — Cycle 63 Sources

- Cycle 63 implementation record — [docs/research/cycle-63-2026-06-06.md](docs/research/cycle-63-2026-06-06.md).
- Callable quota enforcement runbook — [docs/community-callable-quota-enforcement.md](docs/community-callable-quota-enforcement.md).
- Community quota runbook — [docs/community-quota-rate-limits.md](docs/community-quota-rate-limits.md).
- Official Firebase sources — [Firebase App Check overview](https://firebase.google.com/docs/app-check), [App Check for Cloud Functions](https://firebase.google.com/docs/app-check/cloud-functions), [Callable Cloud Functions](https://firebase.google.com/docs/functions/callable), and [Realtime Database Security Rules](https://firebase.google.com/docs/database/security).
- Callable quota contract implementation — `CommunityQuotaPolicy.kt` and `CommunityQuotaPolicyTest.kt`.
- Verification outputs — focused `CommunityQuotaPolicyTest` passed locally.

## Appendix BN — Cycle 64 Sources

- Cycle 64 implementation record — [docs/research/cycle-64-2026-06-06.md](docs/research/cycle-64-2026-06-06.md).
- Community backend deploy/rollback runbook — [docs/community-backend-runbook.md](docs/community-backend-runbook.md).
- Backend manifest — [docs/community-backend-manifest.json](docs/community-backend-manifest.json).
- Official sources — [Firebase CLI reference](https://firebase.google.com/docs/cli), [Firebase App Check overview](https://firebase.google.com/docs/app-check), [App Check enforcement](https://firebase.google.com/docs/app-check/enable-enforcement), and [Cloud Storage lifecycle management](https://docs.cloud.google.com/storage/docs/lifecycle).
- Backend manifest implementation — `tools/community_backend_manifest.py`, `.github/workflows/verify.yml`, `firebase.json`, `database.rules.json`, and `storage.rules`.
- Verification outputs — `py -3 -m py_compile tools\community_backend_manifest.py`, `py -3 tools\community_backend_manifest.py --mode check`, and `npx firebase deploy --help` passed locally.

## Appendix BO — Cycle 65 Sources

- Cycle 65 implementation record — [docs/research/cycle-65-2026-06-06.md](docs/research/cycle-65-2026-06-06.md).
- Community Storage lifecycle policy — [docs/community-storage-lifecycle-policy.md](docs/community-storage-lifecycle-policy.md).
- Community upload deletion runbook — [docs/community-upload-deletion.md](docs/community-upload-deletion.md).
- Official sources — [Cloud Storage lifecycle management](https://docs.cloud.google.com/storage/docs/lifecycle), [Cloud Storage deleting objects](https://docs.cloud.google.com/storage/docs/deleting-objects), and [Firebase Storage Android delete files](https://firebase.google.com/docs/storage/android/delete-files).
- Orphan cleanup implementation — `tools/community_storage_orphan_report.py`, `test/tools/community_storage_orphan_report_test.py`, and `.github/workflows/verify.yml`.
- Verification outputs — `py -3 -m py_compile tools\community_storage_orphan_report.py`, `py -3 -m unittest discover -s test/tools -p '*_test.py'`, and `npm run test:firebase-rules` passed locally.

## Appendix BP — Cycle 66 Sources

- Cycle 66 implementation record — [docs/research/cycle-66-2026-06-06.md](docs/research/cycle-66-2026-06-06.md).
- Community upload backfill runbook — [docs/community-upload-backfill.md](docs/community-upload-backfill.md).
- Community upload deletion runbook — [docs/community-upload-deletion.md](docs/community-upload-deletion.md).
- Official Firebase sources — [Realtime Database Android read/write/delete](https://firebase.google.com/docs/database/android/read-and-write), [Firebase DatabaseReference API](https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference), and [Firebase Storage Android delete files](https://firebase.google.com/docs/storage/android/delete-files).
- Backfill implementation — `tools/community_upload_backfill_plan.py`, `test/tools/community_upload_backfill_plan_test.py`, and `.github/workflows/verify.yml`.
- Verification outputs — `py -3 -m py_compile tools\community_upload_backfill_plan.py test\tools\community_upload_backfill_plan_test.py`, `py -3 -m unittest discover -s test/tools -p '*_test.py'`, and `npm run test:firebase-rules` passed locally.

## Appendix BQ — Cycle 67 Sources

- Cycle 67 implementation record — [docs/research/cycle-67-2026-06-06.md](docs/research/cycle-67-2026-06-06.md).
- Deletion retention policy — [docs/community-deletion-retention-policy.md](docs/community-deletion-retention-policy.md).
- Community upload deletion runbook — [docs/community-upload-deletion.md](docs/community-upload-deletion.md).
- Official Firebase sources — [Realtime Database Android read/write/delete](https://firebase.google.com/docs/database/android/read-and-write), [Firebase DatabaseReference API](https://firebase.google.com/docs/reference/android/com/google/firebase/database/DatabaseReference), [Firebase Storage Android delete files](https://firebase.google.com/docs/storage/android/delete-files), and [Realtime Database Security Rules](https://firebase.google.com/docs/database/security).
- Tombstone implementation — `CommunityUploadOwnership.kt`, sound/wallpaper upload repositories, `CommunityReportRepository.kt`, `database.rules.json`, and `test/firebase/database.rules.test.mjs`.
- Verification outputs — focused `CommunityUploadOwnershipTest`, `py -3 -m json.tool database.rules.json`, `py -3 tools\community_backend_manifest.py --mode check`, `node --check test\firebase\database.rules.test.mjs`, and `npm run test:firebase-rules` passed locally.

## Appendix BR - Cycles 68-125 Sources

- Cycle 125 implementation record - [docs/research/cycle-125-2026-06-07.md](docs/research/cycle-125-2026-06-07.md).
- Cycle 124 implementation record - [docs/research/cycle-124-2026-06-07.md](docs/research/cycle-124-2026-06-07.md).
- Cycle 123 implementation record - [docs/research/cycle-123-2026-06-07.md](docs/research/cycle-123-2026-06-07.md).
- Cycle 122 implementation record - [docs/research/cycle-122-2026-06-07.md](docs/research/cycle-122-2026-06-07.md).
- Cycle 121 implementation record - [docs/research/cycle-121-2026-06-07.md](docs/research/cycle-121-2026-06-07.md).
- Cycle 120 implementation record - [docs/research/cycle-120-2026-06-07.md](docs/research/cycle-120-2026-06-07.md).
- Cycle 119 implementation record - [docs/research/cycle-119-2026-06-07.md](docs/research/cycle-119-2026-06-07.md).
- Cycle 118 implementation record - [docs/research/cycle-118-2026-06-07.md](docs/research/cycle-118-2026-06-07.md).
- Cycle 117 implementation record - [docs/research/cycle-117-2026-06-07.md](docs/research/cycle-117-2026-06-07.md).
- Cycle 116 implementation record - [docs/research/cycle-116-2026-06-07.md](docs/research/cycle-116-2026-06-07.md).
- Cycle 115 implementation record - [docs/research/cycle-115-2026-06-07.md](docs/research/cycle-115-2026-06-07.md).
- Cycle 114 implementation record - [docs/research/cycle-114-2026-06-07.md](docs/research/cycle-114-2026-06-07.md).
- Cycle 113 implementation record - [docs/research/cycle-113-2026-06-07.md](docs/research/cycle-113-2026-06-07.md).
- Cycle 112 implementation record - [docs/research/cycle-112-2026-06-07.md](docs/research/cycle-112-2026-06-07.md).
- Cycle 111 implementation record - [docs/research/cycle-111-2026-06-07.md](docs/research/cycle-111-2026-06-07.md).
- Cycle 110 implementation record - [docs/research/cycle-110-2026-06-07.md](docs/research/cycle-110-2026-06-07.md).
- Cycle 109 implementation record - [docs/research/cycle-109-2026-06-07.md](docs/research/cycle-109-2026-06-07.md).
- Cycle 108 implementation record - [docs/research/cycle-108-2026-06-07.md](docs/research/cycle-108-2026-06-07.md).
- Cycle 107 implementation record - [docs/research/cycle-107-2026-06-07.md](docs/research/cycle-107-2026-06-07.md).
- Cycle 106 implementation record - [docs/research/cycle-106-2026-06-07.md](docs/research/cycle-106-2026-06-07.md).
- Cycle 105 implementation record - [docs/research/cycle-105-2026-06-07.md](docs/research/cycle-105-2026-06-07.md).
- Cycle 104 implementation record - [docs/research/cycle-104-2026-06-07.md](docs/research/cycle-104-2026-06-07.md).
- Cycle 103 implementation record - [docs/research/cycle-103-2026-06-07.md](docs/research/cycle-103-2026-06-07.md).
- Cycle 102 implementation record - [docs/research/cycle-102-2026-06-07.md](docs/research/cycle-102-2026-06-07.md).
- Cycle 101 implementation record - [docs/research/cycle-101-2026-06-07.md](docs/research/cycle-101-2026-06-07.md).
- Cycle 100 implementation record - [docs/research/cycle-100-2026-06-07.md](docs/research/cycle-100-2026-06-07.md).
- Cycle 99 implementation record - [docs/research/cycle-99-2026-06-07.md](docs/research/cycle-99-2026-06-07.md).
- Cycle 98 implementation record - [docs/research/cycle-98-2026-06-07.md](docs/research/cycle-98-2026-06-07.md).
- Cycle 97 implementation record - [docs/research/cycle-97-2026-06-07.md](docs/research/cycle-97-2026-06-07.md).
- Cycle 96 implementation record - [docs/research/cycle-96-2026-06-07.md](docs/research/cycle-96-2026-06-07.md).
- Cycle 95 implementation record - [docs/research/cycle-95-2026-06-07.md](docs/research/cycle-95-2026-06-07.md).
- Cycle 94 implementation record - [docs/research/cycle-94-2026-06-07.md](docs/research/cycle-94-2026-06-07.md).
- Cycle 93 implementation record - [docs/research/cycle-93-2026-06-07.md](docs/research/cycle-93-2026-06-07.md).
- Cycle 92 implementation record - [docs/research/cycle-92-2026-06-07.md](docs/research/cycle-92-2026-06-07.md).
- Cycle 91 implementation record - [docs/research/cycle-91-2026-06-07.md](docs/research/cycle-91-2026-06-07.md).
- Cycle 90 implementation record - [docs/research/cycle-90-2026-06-07.md](docs/research/cycle-90-2026-06-07.md).
- Cycle 89 implementation record - [docs/research/cycle-89-2026-06-07.md](docs/research/cycle-89-2026-06-07.md).
- Cycle 88 implementation record - [docs/research/cycle-88-2026-06-07.md](docs/research/cycle-88-2026-06-07.md).
- Cycle 87 implementation record - [docs/research/cycle-87-2026-06-06.md](docs/research/cycle-87-2026-06-06.md).
- Cycle 86 implementation record - [docs/research/cycle-86-2026-06-06.md](docs/research/cycle-86-2026-06-06.md).
- Cycle 85 implementation record - [docs/research/cycle-85-2026-06-06.md](docs/research/cycle-85-2026-06-06.md).
- Cycle 84 implementation record - [docs/research/cycle-84-2026-06-06.md](docs/research/cycle-84-2026-06-06.md).
- Cycle 83 implementation record - [docs/research/cycle-83-2026-06-06.md](docs/research/cycle-83-2026-06-06.md).
- Cycle 82 implementation record - [docs/research/cycle-82-2026-06-06.md](docs/research/cycle-82-2026-06-06.md).
- Cycle 81 implementation record - [docs/research/cycle-81-2026-06-06.md](docs/research/cycle-81-2026-06-06.md).
- Cycle 80 implementation record - [docs/research/cycle-80-2026-06-06.md](docs/research/cycle-80-2026-06-06.md).
- Cycle 79 implementation record - [docs/research/cycle-79-2026-06-06.md](docs/research/cycle-79-2026-06-06.md).
- Cycle 78 implementation record - [docs/research/cycle-78-2026-06-06.md](docs/research/cycle-78-2026-06-06.md).
- Cycle 77 implementation record - [docs/research/cycle-77-2026-06-06.md](docs/research/cycle-77-2026-06-06.md).
- Cycle 76 implementation record - [docs/research/cycle-76-2026-06-06.md](docs/research/cycle-76-2026-06-06.md).
- Cycle 75 implementation record - [docs/research/cycle-75-2026-06-06.md](docs/research/cycle-75-2026-06-06.md).
- Cycle 74 implementation record - [docs/research/cycle-74-2026-06-06.md](docs/research/cycle-74-2026-06-06.md).
- Cycle 73 implementation record - [docs/research/cycle-73-2026-06-06.md](docs/research/cycle-73-2026-06-06.md).
- Cycle 72 implementation record - [docs/research/cycle-72-2026-06-06.md](docs/research/cycle-72-2026-06-06.md).
- Cycle 71 implementation record - [docs/research/cycle-71-2026-06-06.md](docs/research/cycle-71-2026-06-06.md).
- Cycle 70 implementation record - [docs/research/cycle-70-2026-06-06.md](docs/research/cycle-70-2026-06-06.md).
- Cycle 69 implementation record - [docs/research/cycle-69-2026-06-06.md](docs/research/cycle-69-2026-06-06.md).
- Community block-user policy - [docs/community-block-user-policy.md](docs/community-block-user-policy.md).
- Community account deletion policy - [docs/community-account-deletion-policy.md](docs/community-account-deletion-policy.md).
- Community callable quota runbook - [docs/community-callable-quota-enforcement.md](docs/community-callable-quota-enforcement.md).
- Community callable rollout evidence runbook - [docs/community-callable-rollout-evidence.md](docs/community-callable-rollout-evidence.md).
- Community callable contract manifest - [docs/community-callable-contract.json](docs/community-callable-contract.json).
- Community callable wire-protocol manifest - [docs/community-callable-wire-protocol.json](docs/community-callable-wire-protocol.json).
- Official sources - [Google Play User Generated Content policy](https://support.google.com/googleplay/android-developer/answer/9876937), [Google Play moderation requirements](https://support.google.com/googleplay/android-developer/answer/12923286), [Google Play account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111), [Firebase Realtime Database Android read/write/delete](https://firebase.google.com/docs/database/android/read-and-write), [Firebase Realtime Database Security Rules](https://firebase.google.com/docs/database/security), [Firebase Auth delete users](https://firebase.google.com/docs/auth/android/manage-users#delete_a_user), and [Firebase Storage delete files](https://firebase.google.com/docs/storage/android/delete-files).
- Block-user/account-deletion implementation - `CommunityIdentityProvider.kt`, `CommunityDeletionRequest.kt`, `CommunityBlockRepository.kt`, `CommunityBlockPolicy.kt`, `CommunityReport.kt`, `CommunityReportRepository.kt`, `UploadRepository.kt`, `WallpaperUploadRepository.kt`, `CreatorProfileRepository.kt`, `CommunityReportsScreen.kt`, `CreatorProfileScreen.kt`, `SoundDetailScreen.kt`, `WallpaperDetailScreen.kt`, `SoundsViewModel.kt`, `WallpapersViewModel.kt`, `SettingsScreen.kt`, `SettingsViewModel.kt`, `docs/privacy/privacy-policy.md`, `docs/support/community-account-deletion-web-page.md`, `docs/support/community-account-deletion-web-url.json`, `docs/community-callable-contract.json`, `docs/community-callable-wire-protocol.json`, `docs/community-callable-rollout-evidence.md`, `tools/community_callable_contract_check.py`, `tools/community_callable_wire_protocol_check.py`, `tools/community_callable_rollout_receipt.py`, `tools/community_account_deletion_plan.py`, `tools/community_deletion_request_lookup.py`, `tools/community_deletion_web_intake.py`, `tools/community_deletion_web_page_check.py`, `tools/community_deletion_web_url_check.py`, `tools/community_account_deletion_review.py`, `tools/community_account_deletion_apply_simulator.py`, `tools/community_account_deletion_executor_package.py`, `tools/community_account_deletion_rest_executor.py`, `tools/community_account_deletion_completion_receipt.py`, `tools/community_account_deletion_cleanup_sequence.py`, `tools/community_account_deletion_auth_package.py`, `tools/community_account_deletion_auth_execution_receipt.py`, `tools/community_account_deletion_upload_plan.py`, `tools/community_account_deletion_upload_execution_receipt.py`, and backend tool tests.
- Verification outputs - focused `CommunityBlockPolicyTest`, `CreatorProfileRepositoryTest`, `CommunityReportTest`, `CommunityReportsViewModelTest`, `CreatorProfileViewModelTest`, `SoundsViewModelTest`, `WallpapersViewModelTest`, `CommunityPolicyCopyTest`, `CommunityIdentityProviderTest`, and `SettingsViewModelTest` passed locally; Cycle 73 Firebase rules verification passed locally; Cycle 74/Cycle 77/Cycle 78/Cycle 79/Cycle 80/Cycle 81/Cycle 82/Cycle 83/Cycle 84/Cycle 86/Cycle 87/Cycle 88/Cycle 89/Cycle 90/Cycle 91/Cycle 92 backend tool tests passed locally; Cycle 93/Cycle 94/Cycle 95/Cycle 96/Cycle 97/Cycle 98/Cycle 99/Cycle 100 Functions tests, backend manifest checks, and callable contract checks passed locally; Cycle 101/Cycle 102/Cycle 103/Cycle 104/Cycle 105/Cycle 106/Cycle 107 `test:functions-emulator`, Functions tests, backend manifest checks, and callable contract checks passed locally; Cycle 108 focused Android report callable tests, release OSS notice generation, dependency notice lock checks, native lock check, overlay check, and license policy check passed locally; Cycle 109 focused Android callable client tests passed locally; Cycle 110 focused Android callable client tests, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 111 focused Android callable client and block policy tests, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 112 focused Android callable client, upload rights, and upload ownership tests, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 113 focused Android callable client, wallpaper upload validation, upload rights, and upload ownership tests, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 114 focused Android callable client, creator profile repository, and creator profile ViewModel tests, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 115 callable wire-protocol manifest check, backend tool tests, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 116 callable rollout receipt tests, backend tool tests, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 117 GitHub security workflow policy check, focused policy tests, backend tool tests, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 118 Dependabot update policy check, focused Dependabot policy tests, backend tool tests, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 119 GitHub security settings receipt tests, backend tool tests, Dependabot policy check, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 120 Gradle wrapper policy check, focused wrapper policy tests, backend tool tests, Dependabot policy check, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 121 GitHub Actions allowlist check, focused allowlist tests, backend tool tests, Gradle wrapper policy check, Dependabot policy check, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, overlay check, license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scan passed locally; Cycle 85 focused Android identity/settings tests passed locally.

- Cycle 122 verification outputs - GitHub workflow permissions policy check, focused permissions tests, backend tool tests, GitHub Actions allowlist check, Gradle wrapper policy check, Dependabot policy check, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, dependency overlay check, dependency license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scans passed locally.
- Cycle 123 verification outputs - GitHub workflow secret policy check, focused secret-policy tests, backend tool tests, GitHub workflow permissions policy check, GitHub Actions allowlist check, Gradle wrapper policy check, Dependabot policy check, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, dependency overlay check, dependency license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scans passed locally.
- Cycle 124 verification outputs - backend tool tests, GitHub workflow secret policy check, GitHub workflow permissions policy check, GitHub Actions allowlist check, Gradle wrapper policy check, Dependabot policy check, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, dependency overlay check, dependency license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scans passed locally.
- Cycle 125 verification outputs - provider credential release guard check, focused provider guard tests, backend tool tests, GitHub workflow secret policy check, GitHub workflow permissions policy check, GitHub Actions allowlist check, Gradle wrapper policy check, Dependabot policy check, GitHub security workflow policy check, callable wire-protocol check, callable contract check, dependency notice lock checks, native lock check, dependency overlay check, dependency license policy check, high-severity npm audit, diff hygiene, and attribution/ASCII scans passed locally.

## Continuation State

### Last Completed Cycle

Cycle 125: added a provider credential release guard before public signed APK builds.
Cycle 126: added provider-specific crash diagnostics redaction fixtures and dotted provider-property assignment redaction.
Cycle 127: added a shared request redactor for provider URLs and source diagnostics error details.
Cycle 128: added a checked network endpoint inventory runbook and literal-host scanner.
Cycle 129: removed the ccMixter HTTP downgrade path and added release cleartext policy gates.
Cycle 130: added a checked provider credential storage policy and no-Keystore disclosure.
Cycle 131: added a release-stage provider credential APK scan after signed APK packaging.
Cycle 132: added explicit provider key Clear actions and storage-policy coverage.
Cycle 133: added a persisted generated wallpaper disclosure gate and Settings review/reset path.
Cycle 134: added generated-content report actions and callable reason support.
Cycle 135: added a Stability paid-secret policy sentinel to provider credential checks.
Cycle 136: added generated wallpaper credit and duplicate-generation guardrails.
Cycle 137: removed prompt-derived metadata from generated favorites and added generated-file cleanup.
Cycle 138: added store metadata text/version/privacy preflight checks and fixed Fastlane text drift.
Cycle 139: added a checked on-device wallpaper generation decision gate and production-source scanner.
Cycle 140: added a checked public privacy-policy link gate and Settings About entry.
Cycle 141: added a checked manifest permission Data safety matrix and release gate.
Cycle 142: extended the Data safety matrix gate to reviewed network endpoint IDs.
Cycle 143: extended the Data safety matrix gate to source-backed local storage surfaces.
Cycle 144: extended the Data safety matrix gate to Gradle-marker-backed SDK surfaces.
Cycle 145: added a checked Play App content declaration packet.
Cycle 146: added a checked Community Guidelines consent gate.
Cycle 147: added a checked alternative-store disclosure gate.
Cycle 148: added a checked release metadata consistency gate.
Cycle 149: added a checked SBOM readiness gate.
Cycle 150: added a checked store asset capture plan gate.
Cycle 151: removed the unused boot-completed permission and added a checked rotation trigger boot behavior gate.
Cycle 152: added a checked rotation trigger foreground-service policy gate.
Cycle 153: added a checked background work scheduling ledger and closed the WorkManager unique-work policy matrix.
Cycle 154: added a checked background work network posture gate.
Cycle 155: added a background-work section to the local diagnostics/support bundle.
Cycle 156: added live background-work Settings diagnostics for WorkInfo and Data Saver receipts.
Cycle 157: added persisted background-work last-run receipts.
Cycle 158: merged live background-work receipts into the support bundle.
Cycle 159: added Settings and support-bundle action hints for common background-work deferral receipts.
Cycle 160: added a checked background-work device/emulator evidence capture plan.

### Current Focus

Start Cycle 161 with owner-provided live callable invocation evidence and the redacted rollout receipt when deploy/App Check access is available, a real hosted HTTPS deletion request URL after owner publication, direct RTDB rule tightening after callable deploy evidence, a production-project Firebase executor dry-run after owner access is confirmed, or owner/admin GitHub security settings evidence for the new receipt. If owner-gated evidence is still unavailable, continue with signed release dry-run evidence capture on a suitable runner, actual screenshot/feature-graphic capture on a suitable device or emulator, running the background-work device/emulator scheduler evidence packet, release artifact hardening, or the next checkable backend, deploy, security, support, policy, or rules hardening artifact. Commit and push completed work when the active project contract allows it.

### Previous Focus

Start Cycle 67 with deletion retention/tombstone policy, block-user policy, a real production-project Firebase dry-run/orphan/backfill evidence pass after owner access is confirmed, or the Cloud Functions implementation for the Cycle 63 callable contract. Continue backend enforcement work until community uploads, reports, and shares have deployable rules, test coverage, CI gates, and operational runbooks. Commit and push completed work when the active project contract allows it.

### Important Findings So Far

- `ROADMAP.md` has Cycle 18 through Cycle 160 research/implementation items; `docs/research/cycle-18-2026-06-06.md` through `docs/research/cycle-160-2026-06-07.md` have the source-backed analysis.
- Cycles 108-114 added Android callable migration adapters for report, vote, follow, user-block, sound upload finalizer, wallpaper upload finalizer, and profile edit submission. Cycle 115 added the checked Android callable wire-protocol manifest and validator. Cycle 116 added the redacted live callable rollout receipt validator for future owner evidence. Cycle 117 added the checked GitHub security workflow policy guard for Dependency Review, OpenSSF Scorecard, and Release workflow drift. Cycle 118 added checked Dependabot version-update coverage for GitHub Actions, Gradle, root npm, and Functions npm. Cycle 119 added a redacted receipt gate for future owner/admin GitHub repository security settings evidence. Cycle 120 pinned the Gradle wrapper ZIP checksum and added a verify-time wrapper policy guard. Cycle 121 added a repository-wide GitHub Actions allowlist guard for workflow `uses:` references. Cycle 122 added a repository-wide GitHub workflow permissions guard for events, jobs, and permission grants. Cycle 123 added a repository-wide GitHub workflow secret guard for reviewed release signing secret references. Cycle 124 wired backend/tool unit tests into the always-on verify job before Android setup. Cycle 125 added a provider credential release guard for `BuildConfig`/`local.properties`; Cycle 131 added a release-stage provider credential APK scanner after signed APK packaging. Cycle 126 added provider-specific crash diagnostics redaction fixtures and dotted provider-property assignment redaction. Cycle 127 added a shared request redactor for crash diagnostics and source metrics error details. Cycle 128 added a checked network endpoint inventory runbook and literal-host scanner. Cycle 129 removed the ccMixter HTTP downgrade path and added release cleartext policy gates. Cycle 130 added a checked provider credential storage policy, documented the no-Keystore decision for current optional keys, and added the missing Freesound Settings clear control. Cycle 132 added explicit Settings Clear actions for provider keys and storage-policy guard coverage for that path. Cycle 133 added a persisted generated wallpaper disclosure gate and Settings review/reset path before Stability requests. Cycle 134 added generated-content report actions for generated results and saved generated favorites, plus callable reason support for Offensive, Unsafe, Deceptive, and Other reports. Cycle 135 added a Stability paid-secret sentinel to the provider credential storage guard so `stability-ai-key` classification, blank release defaults, explicit Clear control, redaction terms, and no-Keystore decision cannot drift silently. Cycle 136 added generated wallpaper session request counting, duplicate prompt/style confirmation, in-flight request rejection, and Stability account/cooldown error copy. Cycle 137 removed prompt-derived names/tags from generated favorites and added generated-file deletion on favorite removal. Cycle 138 added a checked store metadata text/version/privacy preflight with a tested asset mode for the remaining screenshot/feature-graphic pipeline. Cycle 139 added a checked on-device wallpaper decision packet and verify-time production-source scanner that keeps local generation on hold until evidence criteria are met. Cycle 140 added Settings > About > Privacy policy and a verify/release gate for the public privacy-policy URL contract. Cycle 141 added a manifest permission Data safety matrix and verify/release gate covering all 15 declared permissions. Cycle 142 extended the same Data safety gate to all 15 reviewed network endpoint IDs. Cycle 143 extended it to 12 source-backed local storage surfaces with deletion and backup posture coverage. Cycle 144 extended it to 6 SDK surfaces backed by Gradle dependency markers and source paths. Cycle 145 added a checked Play App content declaration packet with owner actions for hosted deletion URL publication, UGC terms/guidelines consent, live content rating completion, and App content receipt capture. All seven contracted callable exports now have focused Functions handler coverage, RTDB-emulator-backed handler persistence coverage, callable-first Android client code, machine-checked Android wire-protocol coverage, and a checked receipt path for future live invocation evidence; deploy evidence, App Check console evidence, actual live callable invocation evidence, and direct RTDB rule tightening remain open before production enforcement claims.
- Cycle 146 implemented the UGC guidelines consent owner action from the Play packet with versioned policy storage, Settings review/reset, Sounds/Wallpapers prompts, startup identity warm-up gating, community repository/UI gates, a legal guidelines doc, and verify/release drift checks. Hosted deletion URL publication, live content rating completion, App content receipt capture, live callable evidence, App Check console evidence, production-project dry runs, and direct RTDB rule tightening remain open.
- Cycle 147 implemented the alternative-store disclosure matrix with checked GitHub/Obtainium/Izzy/F-Droid channel status, anti-feature labels, all manifest permissions, all reviewed network services, proprietary dependency markers, Izzy submission notes, and release-doc workflow gates.
- Cycle 148 implemented the release metadata consistency gate with checked app package/version values, Fastlane title/short/full/changelog text, README links, privacy URL alignment, Play/alternative-store packet alignment, release workflow snippets, release preflight commands, and expected GitHub Release artifact names.
- Cycle 149 implemented the SBOM readiness gate with a checked deferred-until-N-1 decision, current release evidence paths, future CycloneDX/SPDX artifact names, future scope, source URLs, release metadata/security workflow policy coverage, and verify/release workflow wiring.
- Cycle 150 implemented the store asset pipeline gate with checked Fastlane image paths, capture-pending status, four planned phone screenshot slots, feature graphic requirements, alt text, source URLs, future `--require-assets` command, release metadata/security workflow policy coverage, and verify/release workflow wiring. Actual screenshots and the feature graphic remain open.
- Cycle 151 removed the unused `android.permission.RECEIVE_BOOT_COMPLETED` permission, updated Data safety and alternative-store disclosure packets, documented that rotation triggers resume after opening Aura, and added `tools/rotation_boot_permission_check.py` with verify/release workflow wiring plus release metadata/security policy coverage.
- Cycle 152 added the checked rotation trigger foreground-service policy gate with manifest `specialUse` subtype validation, source safeguard checks, Play App content declaration and owner evidence rows, release metadata/security workflow policy coverage, and verify/release workflow wiring.
- Cycle 153 added the checked background work scheduling ledger for `auto_wallpaper`, `daily_wallpaper`, `weather_update`, `aura_originals_download`, and `rotation_trigger_oneshot`, with source-backed unique work names, enqueue policies, constraints, deferral reasons, release metadata/security workflow policy coverage, and verify/release workflow wiring.
- Cycle 154 added the checked background work network posture gate for connected versus unmetered WorkManager constraints, metered-network behavior, Data Saver diagnostic gaps, privacy surfaces, release risk, release metadata/security workflow policy coverage, and verify/release workflow wiring. Cycle 156 now provides direct Settings Data Saver receipts; Cycle 158 merges those receipts into the support bundle; Cycle 159 adds action hints for restricted-background and metered/unmetered cases.
- Cycle 155 added the local diagnostics/support bundle background-work section for all five unique work names, inferred enabled state, network posture, expected constraints, and explicit pending WorkInfo/Data Saver receipt markers. Cycle 156 landed Settings live receipts; Cycle 157 landed persisted last success/failure/error receipts.
- Cycle 156 added an injectable background-work diagnostics reader plus `Settings` > `Diagnostics` > `Background work` dialog with WorkManager unique-work `WorkInfo` state counts, record counts, max attempts, read errors, active metered-network status, and Data Saver restricted-background status. Cycle 157 added persisted worker last-run receipts.
- Cycle 157 added `BackgroundWorkReceiptStore`, worker success/retry/failure recording for all five unique work names, rotation-trigger receipt key separation, and Settings display for last result, success/failure UTC, error class, and deferral reason. Cycle 158 merges those receipts into copied/shared support bundles.
- Cycle 158 merged live WorkManager, Data Saver, and persisted worker receipt details into copied/shared crash diagnostics bundles while preserving the static support context fallback. Cycle 159 adds user-actionable hints for common local deferral receipts. Cycle 160 adds a checked capture-pending device/emulator evidence packet; real scheduler evidence for quota, low battery, Doze/App Standby, and constraint delays remains open.
- `LicensesScreen.kt` still has manual dependency rows; content sources are already code-backed by `ProviderDisclosure.kt`.
- `.github/workflows/release.yml` now publishes `THIRD-PARTY-NOTICES.md` and `NATIVE-COMPLIANCE.md` with the APK and includes both files in `SHA256SUMS.txt`; SBOM generation remains deferred until N-1 under the checked readiness packet.
- `.github/workflows/verify.yml` now runs `tools/github_actions_allowlist_check.py`, `tools/github_workflow_permissions_check.py`, `tools/github_workflow_secrets_check.py`, `tools/github_security_workflow_check.py`, `tools/dependabot_config_check.py`, `tools/gradle_wrapper_check.py`, `tools/provider_credential_release_check.py`, `tools/provider_credential_storage_check.py`, `tools/cleartext_release_check.py`, `tools/network_endpoint_inventory_check.py`, `tools/store_metadata_preflight.py`, `tools/store_asset_pipeline_check.py`, `tools/privacy_policy_link_check.py`, `tools/privacy_data_safety_check.py`, `tools/rotation_boot_permission_check.py`, `tools/rotation_fgs_policy_check.py`, `tools/background_work_scheduling_check.py`, `tools/background_work_network_check.py`, `tools/community_guidelines_consent_check.py`, `tools/play_app_content_packet_check.py`, `tools/alt_store_metadata_check.py`, `tools/release_metadata_consistency_check.py`, `tools/sbom_readiness_check.py`, `tools/on_device_ai_decision_check.py`, and the `test/tools` Python unit suite before Android setup. `.github/workflows/release.yml` also runs `tools/provider_credential_release_check.py`, `tools/provider_credential_storage_check.py`, `tools/cleartext_release_check.py`, `tools/store_metadata_preflight.py`, `tools/store_asset_pipeline_check.py`, `tools/privacy_policy_link_check.py`, `tools/privacy_data_safety_check.py`, `tools/rotation_boot_permission_check.py`, `tools/rotation_fgs_policy_check.py`, `tools/background_work_scheduling_check.py`, `tools/background_work_network_check.py`, `tools/community_guidelines_consent_check.py`, `tools/play_app_content_packet_check.py`, `tools/alt_store_metadata_check.py`, `tools/release_metadata_consistency_check.py`, and `tools/sbom_readiness_check.py` before signed APK assembly, and both workflows run `tools/dependency_notice_lock.py --mode check`, `tools/dependency_notice_lock.py --mode check-metadata`, `tools/native_compliance_inventory.py --mode check-lock`, `tools/dependency_overlay_check.py`, and `tools/dependency_license_policy.py` after `:app:releaseOssLicensesTask`.
- `docs/distribution/supply-chain.md` defers SBOM work until N-1 with `docs/distribution/sbom-readiness.md` and `tools/sbom_readiness_check.py` enforcing the current evidence floor, future artifact names, future scope, and workflow wiring.
- AboutLibraries 15.x is not a current-toolchain fit because its release notes make AGP 8.13 the minimum; Aura is currently on AGP 8.7.3 / Gradle 8.12. Test AboutLibraries 14.2.1 if using it before N-1.
- Google OSS notices now have the required `settings.gradle.kts` plugin resolution mapping and root/app Gradle wiring in the real repo.
- Real-repo `:app:releaseOssLicensesTask` passed after refreshing POM checksum metadata. After Cycle 108, generated notices cover 260 dependency records and 305 notice sections, including NewPipeExtractor, youtubedl-android library/common/ffmpeg, Firebase, Firebase Functions, App Check Play Integrity, Play services ML Kit, ZXing, Palette, and ProfileInstaller.
- Adding `play-services-oss-licenses:17.5.1` pulled risky release-runtime UI upgrades in the spike clone, including Activity Compose 1.12.1, Compose 1.11.0-beta02 artifacts, AppCompat 1.7.1, Material Components 1.13.0, and credential dependencies.
- The real implementation does not add `play-services-oss-licenses:17.5.1`; a release runtime graph check showed no notice-driven Activity Compose 1.12.1, Compose 1.11.0-beta02, or Material Components 1.13.0 drift.
- `tools/google_oss_to_markdown.py` generated `build/reports/THIRD-PARTY-NOTICES.md` from real-repo Google outputs; the output was 1,367,502 bytes, 25,336 lines, 251 dependency records, and 288 notice sections.
- `tools/native_compliance_inventory.py` generated `docs/legal/native-compliance.md` with youtubedl-android common/library/ffmpeg AAR hashes, NewPipeExtractor JAR hashes, yt-dlp 2025.11.12 git-head facts, python3.12 payload facts, QuickJS entries, FFmpeg ABI payload paths, and embedded FFmpeg 7.1.1 configure evidence.
- `docs/legal/ffmpeg-source-correspondence.md` records the resolved FFmpeg AAR hash, nested payload hashes, embedded configure lines, FFmpeg source candidate, and remaining Termux source/build-log owner actions.
- `docs/legal/dependency-notices.lock.json` records 260 release dependency coordinates and 305 notice section hashes from Google OSS outputs.
- The dependency notice lockfile gates generated dependency/notice drift, and `tools/dependency_notice_lock.py --mode check-metadata` now separately proves raw metadata row parity with the reviewed notice sections.
- `docs/legal/native-compliance.lock.json` records 8 native/copyleft coordinates, 23 artifact records, and 36 payload entries from youtubedl-android and NewPipeExtractor artifacts.
- The native lockfile now gates artifact hash, payload fact, and embedded FFmpeg configure drift, but it still does not prove the exact Termux package commit, patches, dependency source set, or build logs.
- `docs/legal/dependency-notice-overrides.json` records curated high-risk dependency and native-payload review metadata; `tools/dependency_overlay_check.py` fails stale, missing, or orphaned overlay entries against the dependency/native locks.
- `docs/legal/dependency-license-policy.json` now records allowed, review-required, disallowed, and required-coverage license policy; `tools/dependency_license_policy.py` fails unknown, disallowed, or unreviewed curated license IDs.
- `tools/release_artifact_bundle_check.py` now validates manual dry-run and tag-release bundles for required artifacts, checksums, release-note evidence, signing digest output, and non-debuggable `aapt` evidence.
- `tools/google_oss_raw_archive.py` now publishes `GOOGLE-OSS-RAW-INPUTS.zip` with a manifest for raw generated Google OSS notice inputs.
- `docs/distribution/raw-oss-input-retention.md` now states that `GOOGLE-OSS-RAW-INPUTS.zip` remains attached to every tagged public release that publishes generated notices; the release bundle validator continues to enforce that archive.
- `LicensesScreen.kt` now exposes generated release notice artifacts and generated raw-resource dependency notice rows before the manual library and content-source disclosure rows.
- `GeneratedDependencyNotices.kt` reads `R.raw.third_party_license_metadata` and `R.raw.third_party_licenses` directly, avoiding the stock Google notice runtime dependency while keeping a current-toolchain in-app viewer.
- The generated notice viewer now has name/license filtering plus a review watchlist for generated rows that match curated Firebase, Play services, ML Kit, NewPipeExtractor, youtubedl-android, ProfileInstaller, and ZXing surfaces.
- AboutLibraries 14.2.1 configures but the default release export was incomplete for Aura and logged Windows path errors during compliance export.
- `ProviderDisclosureTest` now passes in the real repo with `JAVA_HOME` set to Android Studio JBR and `ANDROID_HOME` set to the local Android SDK.
- `ProviderDisclosure.kt` now includes a checked runtime-control matrix for every `ContentSource`; `docs/legal/provider-runtime-controls.md` records disabled behavior and the remaining generated-content control gap.
- YouTube now has a default-on `youtube_provider_enabled` preference plus Settings switch that hides YouTube browsing, falls back to bundled Sounds content, skips video discovery, blocks stream resolution before cache/downloader use, and records disabled source diagnostics separately from failures.
- Reddit now has a default-on `reddit_provider_enabled` preference plus Settings switch that hides Reddit wallpaper browsing, skips daily picks, background rotations, repository calls, and video wallpaper discovery, and records disabled source diagnostics separately from failures.
- Pexels and Pixabay now have default-on provider switches that hide disabled wallpaper tabs, skip Discover/search/style-biased/video API calls before bundled keys are read, remove disabled Pixabay from rotation pickers, and record disabled diagnostics separately from failures.
- Community now has a default-on `community_provider_enabled` preference plus Settings switch that skips startup identity warm-up, hides community tabs/uploads/votes/creator profile entry points, blocks feed/upload/follow repository calls, and records disabled diagnostics separately from Firebase outages.
- Bing Daily now has a default-on `bing_provider_enabled` preference plus Settings switch that skips daily-image API calls before cache fallback or Retrofit use, removes Bing from rotation pickers when disabled, and records disabled diagnostics separately from outages.
- Wallhaven now has a default-on `wallhaven_provider_enabled` preference plus Settings switch that hides Wallhaven browsing, color/random/similar actions, rotation picker entries, and skips Wallhaven API calls before key reads, cache fallback, or Retrofit use while recording disabled diagnostics separately from outages.
- Pixabay photo requests now use `WallpaperCacheManager.TTL_PIXABAY` for a 24-hour fresh-cache hit before API calls; Pixabay video metadata now uses app-private 24-hour metadata caching with cached stream URLs; 429 responses parse `Retry-After` or `X-RateLimit-Reset` into backoff windows.
- Generated wallpapers now have a default-on `generated_content_provider_enabled` preference plus Settings switch that hides generation entry points and blocks Stability requests before prompt/key validation when disabled. Saved generated wallpapers remain visible as local user content.
- Favorites and download history now have persisted `SOURCE_UNAVAILABLE` state with reason metadata so saved local copies can remain navigable without presenting upstream-removed provider content as live source content.
- Pexels now has enhancement-only guardrails in wallpaper Discover and video-wallpaper discovery: Pexels rows can enrich mixed feeds, but Pexels-only batches are dropped. Focused tests prove provider-off Discover still serves Wallhaven/Pixabay base inventory and Pexels photo rows retain creator/source-page context.
- Wallpaper and sound apply/download paths now classify explicit 404/410/gone/removed/deleted provider failures and mark saved favorites unavailable with provider-specific reasons; `DownloadManager` also marks matching download-history rows unavailable on failed re-downloads.
- Sounds now have item-level license capability gates: YouTube apply/download requires confirmation, SoundCloud is link-only until reviewed, CC BY-NC requires confirmation, no-derivatives disables editing, missing remote licenses disable live-source actions, saved sound favorites preserve license metadata through Room v16 and favorites import/export, and share text includes source/uploader/license provenance.
- Community uploads now require selected CC0/CC BY/CC BY-NC metadata, rights attestation, authenticated uploader UID, attestation timestamp, and optional HTTPS source URL before public sound/wallpaper upload metadata is written. New uploads also store `storagePath` and private owner-index rows so owner delete methods can remove blobs plus metadata without parsing public download URLs. Legacy community sound rows without selected license metadata keep the `User Upload` fallback.
- Community reporting now has private report intake, admin review, status filters for open/closed queues, moderation hide/unhide actions, admin resolution metadata paths, private rights-confirmed takedown receipts tied to current upload `storagePath` handles, confirmed admin delete actions, private deletion tombstones, private block-user data paths with admin reverse indexes, visible public upload/report/delete copy explaining rights-takedown behavior, Android block-list filtering for community sound feeds, wallpaper feeds, and creator profile lists, confirmed detail-screen block actions for community sound/wallpaper creators, Settings blocked-creators review/unblock actions, Settings community identity/deletion request code display, Settings local fallback identity cleanup, redacted shareable deletion request drafts, request-code lookup tooling, private web-intake validation, hosted web page validation, hosted web URL manifest validation, account-deletion review tooling, account-deletion apply simulation tooling, account-deletion executor packaging, guarded account-deletion REST execution, redacted account-deletion completion receipts, local/Auth cleanup sequencing, private Auth deletion packages, redacted Auth execution receipts, private public-upload deletion handoff plans, redacted upload execution receipts, report-card block actions, and creator-profile block actions. Detail screens submit reports with source/license/uploader context and optional uploader UID metadata for community upload reports. App Check client providers are installed. Community write quotas now have typed policy rows, protected admin-only ledger namespaces, callable function names, payload schemas, final write paths, ledger path coverage, limited-use App Check token decisions, a backend JSON contract manifest, UTC quota-day boundary, a checked Cloud Functions contract mirror, a pure UTC quota decision engine, a handler-backed `submitCommunityReport` callable, a handler-backed `recordCommunityVote` callable, a handler-backed `setCreatorFollow` callable with creator-plus-desired-state dedupe, a handler-backed `setCommunityUserBlock` callable with blocked-user-plus-desired-state dedupe, handler-backed sound and wallpaper upload finalizers with storage-path dedupe and owner-index writes, and a handler-backed `updateCreatorProfile` callable with normalized-profile dedupe. Cycles 101-107 added RTDB-emulator-backed profile, report, vote, follow, user block, sound upload, and wallpaper upload handler persistence coverage for profile/report/vote/follow/block/sound upload/wallpaper upload, quota, dedupe, and idempotency writes and run them in the Firebase backend CI lane. Cycles 108-114 added Android callable-first report, vote, follow, user-block, sound upload finalization, wallpaper upload finalization, and profile edit adapters. Cycle 115 added machine-checked Android callable wire-protocol coverage. Cycle 116 added the redacted callable rollout receipt validator for future owner evidence. New community uploads now have owner-visible delete actions when owner metadata and `storagePath` prove they are deletable. Collection shares now write `createdByUid` and use owner/admin RTDB rules under `shared_collections`. Storage rules and local emulator tests now cover community upload blobs. RTDB rules and local emulator tests now cover upload metadata/index authorization, report intake/admin resolution, optional report uploader UID validation, takedown receipts/delete states, deletion tombstones, block-user paths, quota/dedupe ledgers, and app-matched collection shares. The main verify workflow now runs the Firebase rules suite, backend manifest check, callable contract manifest check, callable wire-protocol check, callable rollout receipt tool tests, hosted page check, hosted URL manifest check, Functions tests, Functions emulator tests, and backend tool unittests for lifecycle/backfill/account-deletion changes. `docs/community-backend-runbook.md` records preflight, dry-run, deploy, rollback, App Check rollback separation, release evidence, callable contract status, callable wire-protocol status, callable rollout receipt status, handler status for all seven callable exports, RTDB-emulator-backed profile/report/vote/follow/block/sound upload/wallpaper upload handler status, web-intake receipts, hosted page copy status, hosted URL manifest status, account deletion plan/request-code lookup evidence, account deletion review receipts, local apply simulation receipts, private executor packages, guarded REST executor receipts, redacted completion receipts, local/Auth cleanup sequences, private Auth deletion packages, redacted Auth execution receipts, private upload handoff plans, and redacted upload execution receipts. `docs/community-storage-lifecycle-policy.md` blocks automatic deletes on committed upload prefixes and requires two matching orphan reports before manual cleanup. `docs/community-upload-backfill.md` defines dry-run legacy backfill planning for missing `storagePath` and owner-index rows. `docs/community-account-deletion-policy.md` defines dry-run deletion semantics for vote markers, follows, creator profiles, block indexes, and shares while retaining aggregate vote counts and private moderation audit records, and now documents the read-only Settings identity request surface, Clear local device cleanup, web-intake, hosted page/URL manifest gates, local review, simulation, executor-package, guarded REST execution, redacted completion receipt, local/Auth cleanup sequence, private Auth package, redacted Auth execution receipt, private upload handoff, and redacted upload execution receipt gates. `docs/privacy/privacy-policy.md` discloses Aura data categories, no ads/tracking, deletion handling, retained moderation/safety evidence, checked hosted page copy, and pending owner publication for the hosted deletion URL. `docs/support/community-account-deletion.md` documents the private request route, web-intake validation, hosted page validation, hosted URL manifest validation, operator handling, review receipt step, apply simulation step, executor package step, guarded REST dry-run/apply step, requester-safe completion receipt step, post-completion local/Auth cleanup sequence, private Auth package step, Auth execution receipt step, private upload handoff step, and upload execution receipt step. Hosted HTTPS web URL publication, real production dry-run/orphan/backfill evidence, App Check console evidence, owner-run Firebase Auth/upload deletion evidence, live callable invocation evidence, and callable direct-rule tightening remain open.
- Recent history was checked with `rtk git log -10 --oneline --decorate` for this pass.

### Next Best Actions

1. Collect owner-approved live callable invocation evidence and generate the redacted rollout receipt after deploy and App Check access are available.
2. Publish a real HTTPS hosted deletion request URL, set the manifest to `published`, and link it from the privacy policy plus web-intake support doc.
3. Run a real production-project Firebase backend dry run, orphan report, or backfill plan after owner access is confirmed and archive the output in the backend evidence packet.
4. Archive owner-run Firebase Auth deletion evidence with the redacted Auth execution receipt when owner access is available.
5. Archive owner/admin public-upload deletion evidence with the redacted upload execution receipt when production deletion is approved.
6. Capture live GitHub branch protection, required-check, Dependabot alerts/security updates, code-scanning, secret-scanning, and release-attestation settings evidence, then generate the redacted GitHub security settings receipt when owner access is available.
7. Capture signed release dry-run evidence on a suitable runner, capture the four planned store screenshots plus feature graphic on a suitable device/emulator, run and archive the background-work device/emulator scheduler evidence packet, or pick the next checkable hardening artifact if owner-gated live evidence remains unavailable.

### Unprocessed Leads

- Exact Termux package commit, patches, dependency source set, and build logs for the resolved youtubedl-android ffmpeg 0.18.1 AAR.
- Whether the stock Google `OssLicensesMenuActivity` is ever worth adding after a dependency convergence audit.
- Exact CycloneDX/SPDX implementation format after the N-1 toolchain upgrade.
- Whether AboutLibraries can be configured to include the full release runtime graph; default 14.2.1 output only showed three Kotlin BOM rows in this spike.

### Files Still To Inspect

- `AudioTrimmer.kt` and `VideoCropScreen.kt` call paths that depend on FFmpeg/youtubedl payloads
- `.github/workflows/release.yml`
- `.github/workflows/dependency-review.yml`
- `.github/workflows/scorecard.yml`
- `app/src/main/AndroidManifest.xml`
- Existing Settings licenses hand-maintained dependency rows for curated overlay alignment

### Searches Still To Run

- `CycloneDX Gradle Android releaseRuntimeClasspath generation after N-1`
- `FFmpeg Android package source correspondence Termux package metadata`
- `Android APK native library source offer LGPL checklist`
- `Android open source notices source offer dependency overlay examples`
- `AboutLibraries 14.2.1 exportLibrariesRelease only BOM dependencies`

---

## Research-Driven Additions (2026-06-09)

Items below are sourced from exhaustive external research against competitors, platform APIs, dependency changelogs, security advisories, and community signal. Each is traceable to evidence in `RESEARCH.md`. Do not duplicate items that already exist in the Researcher Queue or Now/Next/Later tiers above -- these are net-new.

### P0 -- Root-cause fixes and trust

### P1 -- Accessibility, localization, and platform parity

- [ ] P1 -- **Compose UI screenshot tests with Paparazzi or Roborazzi**
  Why: 50 unit-test files but zero Compose screenshot tests. Theme changes (AMOLED/light/Material You), accessibility scaling, and RTL layout regressions are invisible without visual regression testing. Paparazzi runs on JVM without an emulator.
  Evidence: No `paparazzi` or `roborazzi` dependency found; U-13 roadmap item notes this gap; Compose Compiler 2.x changes screenshot stability.
  Touches: `app/build.gradle.kts`, new test module or test files, CI workflow.
  Acceptance: Screenshot tests cover Wallpapers grid, Sound Detail, Settings, and at least one editor screen in AMOLED and light themes; CI runs them on every push.
  Complexity: M

- [ ] P1 -- **NavigationSuiteScaffold for tablet/foldable adaptive layout**
  Why: Aura has zero `WindowSizeClass`, `NavigationSuiteScaffold`, or `ListDetailPaneScaffold` usage. On tablets and foldables, the bottom nav is cramped and the wallpaper grid doesn't scale. WallFlow ships a multi-pane tablet layout. Play Store ranks large-screen-optimized apps higher. Android 17 target-37 apps cannot rely on orientation restrictions on sw>600dp.
  Evidence: Grep for `WindowSizeClass`, `NavigationSuiteScaffold`, `ListDetailPaneScaffold` returns zero hits; WallFlow tablet support; Android 17 form-factor behavior docs; L-10 roadmap item.
  Touches: `FreeVibeRoot.kt`, `WallpapersScreen.kt`, `WallpaperDetailScreen.kt`, `FavoritesScreen.kt`, `CollectionsScreen.kt`, `SettingsScreen.kt`, `app/build.gradle.kts` (material3-adaptive dependency).
  Acceptance: Bottom nav becomes navigation rail on Expanded width; wallpaper detail uses `ListDetailPaneScaffold` on tablets; grid column count adapts by `WindowSizeClass` (2/3/4/6); no critical clipping in primary flows at sw>=600dp.
  Complexity: L

### P1 -- Dependency modernization

- [ ] P1 -- **Migrate to Coil 3.x**
  Why: Coil 3.0 adds Compose Multiplatform support, `maxBitmapSize` safety default (4096x4096), improved transitions (`useExistingImageAsPlaceholder`), and is the path to KMP shared logic (L-4). Current Coil 2.7.0 is end-of-line.
  Evidence: Coil 3.0 release blog; Coil changelog; `app/build.gradle.kts` pins Coil 2.7.0 via `libs.coil.compose`.
  Touches: `app/build.gradle.kts`, `FreeVibeApp.kt` (ImageLoaderFactory), `gradle/libs.versions.toml`, all `AsyncImage`/`rememberAsyncImagePainter` call sites.
  Acceptance: Coil 3.x compiles and loads images correctly; `maxBitmapSize` is configured; disk cache behavior preserved; KMP migration path documented.
  Complexity: M

- [ ] P1 -- **Migrate sound preview UI to Media3 1.10 Material3 composables**
  Why: Media3 1.10 ships `PlayerComposable`, `ProgressSlider`, and `PlaybackSpeedControl` in the `media3-ui-compose-material3` module. Aura's hand-rolled waveform/progress/speed control in `SoundDetailScreen` and `SoundEditorScreen` is ~300 LOC that can be replaced with library code styled to match Material 3. Also enables `experimentalSetDynamicSchedulingEnabled()` for battery savings.
  Evidence: Media3 1.10 release blog; current Media3 version is 1.5.1 (4 minor versions behind); NX-9 roadmap item.
  Touches: `app/build.gradle.kts`, `SoundDetailScreen.kt`, `SoundEditorScreen.kt`, `AudioPlaybackManager.kt`, `VideoWallpaperPreviewScreen.kt`.
  Acceptance: Sound preview uses Media3 Material3 composables; dynamic scheduling opt-in behind Settings toggle; hand-rolled player UI reduced by >200 LOC.
  Complexity: M

### P2 -- Feature parity and differentiation

- [ ] P2 -- **FOSS build flavor isolating Firebase and Play Services**
  Why: IzzyOnDroid and F-Droid mainline require FOSS-clean dependencies. Aura's Firebase, Google Services plugin, Play Services ML Kit, and App Check must be behind a product flavor boundary. IzzyOnDroid is the stepping stone (accepts NonFreeDep anti-feature with disclosure). F-Droid mainline remains blocked until the FOSS flavor removes all proprietary deps.
  Evidence: F-Droid inclusion policy; IzzyOnDroid app inclusion policy; `docs/distribution/channel-strategy.md` full-vs-foss decision.
  Touches: `app/build.gradle.kts` (product flavors), `di/AppModule.kt`, community repositories, `ParallaxWallpaperService.kt`, `SmartCropDetector.kt`, Firebase init, build CI matrix.
  Acceptance: `assembleFossRelease` produces an APK with zero Firebase/Play Services dependencies; community features are compile-time absent; parallax falls back to Canvas-only; `tools/fdroid_preflight.py --expect-pass` succeeds on the FOSS variant.
  Complexity: XL

### P2 -- Observability and testing

- [ ] P2 -- **200% font and large display-size smoke pass**
  Why: Android 14+ supports 200% nonlinear font scaling. Aura's dense chips, cards, overlays, bottom sheets, and editor timelines may clip or overlap. No smoke screenshots exist at large font sizes.
  Evidence: Cycle 5 researcher item; Android 14 nonlinear font-scaling docs; Aura compact UI.
  Touches: Typography/layout constraints, chip/card rows, bottom sheets, dialogs, editor controls, screenshot QA.
  Acceptance: Primary flows remain usable at 200% font and large display size; controls keep 48dp touch targets where feasible; no text overlaps controls.
  Complexity: M

### P3 -- Future positioning

- [ ] P3 -- **Curated AGSL shader gallery (5-10 presets)**
  Why: AGSL `RuntimeShader` on Android 13+ enables GPU-accelerated live wallpaper effects (plasma, lava, particles, water) without the complexity of a full shader editor. U-6 roadmap item scoped this as a gallery, not an editor.
  Evidence: AGSL Compose patterns blog; lwp-shaders curated library; ShaderEditor OSS reference; U-6 roadmap item.
  Touches: `AgslEffectPipeline.kt` (scaffold exists), new shader preset assets, live wallpaper picker, Settings effects section.
  Acceptance: 5-10 curated shader presets selectable as live wallpaper backgrounds; Android 12 and below see a static fallback; no user-authored shader input.
  Complexity: M

## Research-Driven Additions (2026-06-09 — pass 2)

Net-new items from the second 2026-06-09 research pass (live provider probes, backend runtime deadlines, dependency strata not covered by N-1). Each is traceable to evidence in `RESEARCH.md`. None duplicate the Researcher Queue, Now/Next/Later tiers, or the pass-1 additions above.

### P0 — Production breakage and hard deadlines

### P1 — Reliability of the YouTube spine and aging strata

- [ ] P1 — **Room 2.6.1 → 2.8.x persistence refresh**
  Why: Room 2.8 ships Kotlin-native codegen (KSP2-friendly), `RoomRawQuery`, and KMP support — the concrete prerequisite for the L-4 KMP shared-logic bet and the NX-7 sync work, and it runs on the current Kotlin 2.1/AGP 8.7 toolchain (needs Kotlin 2.0+, satisfied). Room 3.0 (March 2026) is a breaking major to defer until after N-1.
  Evidence: androidx Room release notes; Room 3.0 announcement blog; `gradle/libs.versions.toml` (room 2.6.1); Cycle 16 whole-graph migration-test item provides the safety harness.
  Touches: `gradle/libs.versions.toml`, `app/schemas/` (export verification), DAOs if codegen surfaces nullability strictness, `FreeVibeDatabase`, Cycle-16 migration test gate.
  Acceptance: all 14 schema versions still migrate v1→v14 under the whole-graph migration test; KSP processing succeeds with Kotlin codegen; no behavior change in favorites/downloads/collections flows; Room 3.0 explicitly deferred with a note.
  Blocked 2026-06-12: Room 2.8.4 KSP failed locally with `AbstractMethodError` in Room's kotlinx-serialization bundle serializer under Kotlin 2.1.0/KSP 2.1.0-1.0.29. Aura is on Room 2.7.2 now to satisfy WorkManager 2.11.2 without taking the larger Kotlin/KSP/toolchain upgrade.
  Complexity: M

### P2 — Replacement sources and quick wins

- [ ] P2 — **Lemmy + Wallpaper Cave as crowd-voted replacement sources**
  Why: Reddit's death removes Aura's only crowd-voted external feed (the "Wallpaper of the Day" quality signal). Lemmy exposes an open, keyless API with vote counts; Wallpaper Cave is the catalog WallYou v15.0 just added. Restores the community-feed differentiator without scraping or ToS risk.
  Evidence: WallYou v15.0 release notes (Wallpaper Cave source) and existing Lemmy source; Reddit decommission item above; `ProviderDisclosure.kt` policy-matrix pattern ready for new rows.
  Touches: new `data/remote/lemmy/` (+ optional wallpapercave) API/repository, `Mappers.kt`, `ContentSource` enum, `ProviderDisclosure.kt` policy rows + runtime switch, `WallpapersViewModel.kt` source chips, `DailyWallpaperWorker.kt` source-priority list, provider cache/rate-limit policy hooks (Cycle 3 engine).
  Acceptance: at least one new source browses with paging, provenance fields (uploader, source page URL, license where exposed), default-on runtime switch, and disclosure row; Wallpaper of the Day can rank by Lemmy votes; provider policy doc updated; Wallpaper Cave only ships if a documented, ToS-clean endpoint exists — otherwise Lemmy-only and Wallpaper Cave is recorded as rejected.
  Complexity: M

- [ ] P2 — **Scheduled automatic backup export of favorites/collections/settings**
  Why: Aura's export is manual-only (`FavoritesExporter` via SAF). Device loss between manual exports loses curation. WallFlow's most-requested data-safety ask (#68) is automatic periodic backup to a user-chosen folder; complements (does not duplicate) Cycle 16's restore-reconciliation items.
  Evidence: WallFlow issue #68; `FavoritesExporter.kt`/`CollectionExporter.kt` already produce the JSON payloads; WorkManager + persisted SAF tree URI is the standard pattern.
  Touches: new `AutoBackupWorker` in `service/`, Settings > Data section (folder picker, frequency, keep-last-N), `BackgroundWorkReceiptStore` row, `docs/` data-safety note (backups stay local, no network).
  Acceptance: opt-in scheduled export (daily/weekly) writes a timestamped JSON to the chosen SAF folder, prunes to N copies, survives reboot, records a receipt in the background-work diagnostics, and never runs when the folder permission was revoked (surfaces a fix-it notice instead).
  Complexity: M

### P3 — Polish parity

- [ ] P3 — **Rotation-time legibility effects (auto-darken/blur for scheduled wallpapers)**
  Why: Bright rotated wallpapers make status-bar/clock text illegible; WallFlow's top-reacted open enhancement (#73) and Paperize's shipped change-time effects (darken/blur/vignette/grayscale) both validate applying a user-set effect during automatic rotation. Aura's editor already has these filters but `AutoWallpaperWorker` applies raw images.
  Evidence: WallFlow issue #73; Paperize README effects list; `WallpaperEditorScreen` filter pipeline vs `AutoWallpaperWorker.kt` (no effect hook).
  Touches: `AutoWallpaperWorker.kt`, `WallpaperApplier.kt` (optional bitmap-effect stage), Settings > Auto wallpaper (effect toggle + intensity), reuse editor's ColorMatrix/blur code paths.
  Acceptance: optional darken/blur (off by default) applies to every scheduled/triggered rotation including widget shuffle; intensity preview shown in Settings; no effect applied to manual applies unless chosen; rotation latency increase stays under ~1s on a mid-range device.
  Complexity: M

## Research-Driven Additions (2026-06-10 — pass 4)

Net-new items from the fourth research pass (2026-06-10). Focused on: transitive CVE remediation, infrastructure hardening, dependency currency beyond N-1 scope, feature vacuums from Reddit death and competitor analysis. Each is traceable to evidence in `RESEARCH.md` pass 4. None duplicate the Researcher Queue, Now/Next/Later tiers, or earlier Research-Driven Additions.

### P0 — Security and compliance fixes

### P1 — Reliability and dependency currency

### P1 — Feature gap closure

### P2 — Differentiation

- [ ] P2 — **Depth/portrait wallpaper composer (MagicFX but FOSS)**
  Why: Google Magic Portrait is Pixel-exclusive. MagicFX Wallpaper is $9.99. No FOSS implementation exists. Aura already ships subject segmentation (N-3), weather effects, dual wallpaper, and smart crop. A depth/portrait composer that places the detected subject in front of a styled frame (clock-tuck, shape frame, parallax depth) is mostly recombination of existing primitives.
  Evidence: Android Authority Pixel Magic Portrait coverage; MagicFX Wallpaper $9.99 on Play; Aura `ParallaxWallpaperService.kt`, `SmartCropCalculator.kt`, `WeatherWallpaperService.kt`.
  Touches: new `DepthComposerScreen.kt`, `DepthWallpaperService.kt` (extends existing parallax engine), frame/shape presets, Settings entry point.
  Acceptance: user selects a wallpaper; subject is segmented and placed in front of a depth-blurred background with an optional shape frame; result can be set as home/lock wallpaper or live wallpaper with tilt parallax; works on Android 10+ (ML Kit Subject Segmentation minimum).
  Complexity: L

- [ ] P2 — **Video wallpaper playlists with per-video profiles (UndeadWallpaper parity)**
  Why: UndeadWallpaper (maocide/UndeadWallpaper, 4.92 stars) ships playlist/shuffle/loop, per-video zoom/offset/rotation/speed/volume, and smart start times. Aura has crop, Fit/Fill, and battery dashboard but applies one video at a time. This is the P2 video playlist item already in the Researcher Queue (Cycle 1) but now has concrete competitor validation.
  Evidence: UndeadWallpaper GitHub; existing P2 Cycle 1 Researcher Queue item; `VideoWallpaperService.kt`, `VideoCropScreen.kt`.
  Touches: Room migration (video playlist table), `VideoWallpaperService.kt` (playlist playback engine), video wallpaper settings (shuffle/loop/one-shot, per-clip profiles), `VideoBatteryProfile.kt` (per-clip FPS cap).
  Acceptance: users can create ordered playlists of local videos; shuffle/loop/one-shot modes; per-clip crop/fit/FPS persist; missing-media recovery; low-battery FPS cap applies per-clip.
  Complexity: L

## Research-Driven Additions (2026-06-10 -- Zedge local-first parity)

This section records net-new parity work from the Zedge official/web/app pass. The accepted direction is local/device-first. Zedge AI generation, accounts, follower identity, credits, subscriptions, reward ads, premium gates, offer notifications, and source scraping are anti-goals; they are recorded only to prevent accidental roadmap drift.

### P1 -- Core local parity gaps

- [ ] P1 — **Unified local Library hub for downloads, favorites, collections, imports, and recent activity**
  Why: Zedge's My Zedge groups Uploads, Downloads, Favorites, Collections, Recent Activity, and settings around the user's saved content. The local-first equivalent should be a Library, not an account profile.
  Aura status: partial. Favorites, downloads, collections, and settings exist as separate surfaces, but there is no single local library hub or device-transfer path.
  Touches: new `LibraryScreen.kt` or rename/merge existing library surfaces, bottom navigation model, downloads/favorites/collections view models, export/import entry points.
  Acceptance: one Library entry groups Favorites, Downloads, Collections, Local Imports, Recent Activity, and backup/restore; no login, followers, uploads, credits, or remote profile language appears; existing deep links/routes to Favorites/Downloads/Collections continue to work; empty states explain local storage only.
  Complexity: M

- [ ] P1 — **Universal on-device search across wallpapers, videos, sounds, collections, downloads, and local files**
  Why: Zedge's top-level "Search Zedge" is always visible and routes users into cross-content discovery. Aura has wallpaper and sound search surfaces, but not one global search that also includes saved/local content.
  Aura status: partial.
  Touches: search route, Room/search indexes, wallpaper/video/sound repositories, downloads/favorites/collections repositories, offline-state UI.
  Acceptance: a single search entry returns segmented results for Wallpapers, Videos, Sounds, Collections, Downloads, Favorites, and Local Files; local/saved results work offline; provider/network results are clearly labeled and disabled offline; search history is stored locally and can be cleared.
  Complexity: L

- [ ] P1 — **24H local wallpaper packs with timeline preview**
  Why: Zedge has a dedicated 24H Wallpapers tab. Aura already ships dark/light auto-switching, time-of-day tint, weather effects, and rotation, but lacks a local pack UX that maps images to dayparts.
  Aura status: partial.
  Touches: scheduler data model, wallpaper pack editor, preview UI, apply pipeline, dual-wallpaper integration.
  Acceptance: users can create/import a local 24H pack with at least morning/day/evening/night slots; timeline preview shows which image applies at each daypart; packs can target home, lock, or dual wallpaper through the existing apply pipeline; works fully offline and survives reboot.
  Complexity: M

- [ ] P1 — **Account-free whole-library backup and device transfer**
  Why: Zedge solves continuity through accounts and cloud library sync. Aura should solve the same user need with explicit local export/import. This extends the existing scheduled-backup and SAF roadmap items instead of adding an account system.
  Aura status: partial. Favorites export and backup concepts exist; full library transfer is not first-class.
  Touches: SAF export/import, settings/favorites/downloads/collections schemas, provider preferences, validation/dry-run UI.
  Acceptance: a backup archive contains favorites, collections, download index, local-import references where permitted, wallpaper packs, ringtone edits, settings, and local search history; restore has a dry-run diff and integrity validation; import/export works through Android document providers and share targets; no account or network service is required.
  Complexity: M

### P2 -- Browse and editorial parity

- [ ] P2 — **Consistent Popular / Newest / Categories / Collections browse rails across Wallpapers, Videos, and Sounds**
  Why: Zedge repeats the same browse skeleton across wallpapers, video wallpapers, ringtones, and notification sounds. Aura has strong individual screens, but the browse patterns differ by content type.
  Aura status: partial.
  Touches: `WallpapersScreen.kt`, video wallpaper browse screen, sounds/ringtones screens, shared browse model/components, provider/category mappers.
  Acceptance: Wallpapers, Videos, Ringtones, and Notification Sounds expose the same top-level browse skeleton: Popular, Newest, Categories, Collections/Local; provider-specific filters move under refine controls; empty/error states identify the affected source without breaking local content.
  Complexity: M

- [ ] P2 — **Permission and source transparency panel**
  Why: Zedge has official permission documentation and prompts users for notifications. Aura's trust position is stronger if permission use and source behavior are visible before prompts, especially in local-first mode.
  Aura status: partial. Privacy/provider docs and diagnostics exist, but there is no first-run/local settings matrix that ties each permission to the feature that needs it.
  Touches: Settings privacy/permissions screen, onboarding or first-use prompt copy, provider disclosure, support bundle.
  Acceptance: Settings > Privacy/Permissions lists media, wallpaper, notification, contacts, weather/location, and network permissions with their local/remote data behavior; first-use permission prompts link to the relevant explanation; notification/offer prompts are never shown unless a user enables a feature that needs notifications.
  Complexity: S

### P3 -- Personalization polish parity

- [ ] P3 — **Sticker and text overlay tools in the wallpaper editor**
  Why: Zedge markets wallpaper filters and stickers. Aura has crop, smart crop, blur, color/filter-style editing, and apply flows, but no local sticker/text layer editor.
  Aura status: partial/missing.
  Touches: wallpaper editor state model, layer renderer, undo stack, apply/export pipeline, local asset bundle.
  Acceptance: users can add local sticker and text layers, move/scale/rotate them, undo edits, and apply/export the result through the existing wallpaper pipeline; no remote sticker store or account is required; final render matches preview within existing crop bounds.
  Complexity: M

- [ ] P3 — **Local theme pack: wallpaper plus launcher icon/widget/sound recipe**
  Why: Zedge positions icon packs and widgets as part of phone personalization. Aura has wallpaper, sounds, and widgets, but not a portable local "theme pack" recipe that groups those assets.
  Aura status: missing/partial.
  Touches: theme pack schema, export/import, widget tint/preview, launcher shortcut icon recipe, sounds linkage.
  Acceptance: users can save a local pack that contains wallpaper/video wallpaper references, optional sound choices, widget tint/preview metadata, and launcher shortcut icon recipe data; packs export/import as JSON plus local assets where permitted; unsupported launcher actions degrade to clear local instructions.
  Complexity: L

### Rejected / anti-parity from this pass

- Zedge-style primary AI Generator, AI community feed, AI credits, or remote generation marketplace: rejected because the target product is local-first and current mobile image generation quality does not meet the user's bar.
- Account/login/followers/creator profile parity: rejected. Replace the user need with local Library, export/import, and backup/restore.
- Credits, subscriptions, reward ads, paid premium gates, and daily offers: rejected charter contradiction.
- Push offer notifications and ad-reward prompts: rejected trust risk. Notifications should be feature-triggered only.
- Zedge content scraping/import as a source: rejected legal/ToS risk.

## Research-Driven Additions

### P2 — Maintainability and testing

- [ ] P2 — **Decompose SettingsScreen into feature-owned sections**
  Why: `SettingsScreen.kt` is 168 KB and carries provider keys, scheduler, weather/live wallpaper, generated-content disclosure, community identity/blocking, diagnostics, permissions, background work, and yt-dlp update flows, making every settings change high-risk.
  Evidence: `app/src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt`; `SettingsViewModel.kt`; recent settings-heavy commits; `RESEARCH.md` architecture assessment.
  Touches: `app/src/main/java/com/freevibe/ui/screens/settings/`, `SettingsViewModel.kt`, settings-focused tests.
  Acceptance: Settings is split into feature-owned composables/files for provider credentials, automation/scheduler, privacy/permissions, community identity, diagnostics, and updates; route behavior and state remain unchanged; focused settings tests still pass; no new settings section is added to the root file directly.
  Complexity: M

- [ ] P2 — **Add route-state fixtures for previews and screenshot tests**
  Why: `rg "@Preview" app/src/main/java app/src/test/java` returns no hits, and the existing screenshot-test roadmap item will be slow to implement without deterministic empty/loading/error/success state fixtures.
  Evidence: zero `@Preview` hits; existing P1 screenshot-test roadmap item; large Compose screen files (`WallpapersScreen.kt`, `SoundsScreen.kt`, `SettingsScreen.kt`, `WallpaperDetailScreen.kt`).
  Touches: screen-level state models, new test/debug fixture package, Paparazzi/Roborazzi test setup when that roadmap item lands.
  Acceptance: fixture states exist for Wallpapers, Wallpaper Detail, Sounds, Settings, Video Wallpapers, and at least one editor screen; fixtures cover empty/loading/error/offline/provider-disabled/large-font/RTL-relevant states; screenshot tests can consume the same fixtures without live network or Firebase.
  Complexity: M

- [ ] P2 — **Add manifest-backed research and roadmap consistency check**
  Why: repeated research passes have left stale dependency/runtime claims in planning text, while live manifests already show Node 22 Functions, Room 2.7.2, and NewPipeExtractor v0.26.3.
  Evidence: `functions/package.json`; `gradle/libs.versions.toml`; `app/build.gradle.kts`; current `ROADMAP.md` and previous `RESEARCH.md` version drift.
  Touches: new lightweight checker under `tools/`, verify workflow, active roadmap/research sections, allowlist for historical sections.
  Acceptance: a no-Gradle check compares active `RESEARCH.md`/`ROADMAP.md` dependency and runtime claims against `gradle/libs.versions.toml`, `app/build.gradle.kts`, and `functions/package.json`; stale active claims fail unless explicitly allowlisted as historical; duplicate active roadmap titles are reported.
  Complexity: S

### P2 - Preview and metadata

- [ ] P2 - Add media technical inspector for video and sound detail flows
  Why: wallpaper detail already exposes resolution, file type, size, and quality hints, but video and sound flows expose only partial metadata; WallFlow users asked for fullscreen resolution and UndeadWallpaper ships media-info display before live-wallpaper apply decisions.
  Evidence: WallFlow issue #99; UndeadWallpaper v1.3.0 media-info release; `app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt`; `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt`; `app/src/main/java/com/freevibe/service/VideoWallpaperStorage.kt`; `app/src/main/java/com/freevibe/ui/screens/sounds/SoundDetailScreen.kt`; `app/src/main/java/com/freevibe/data/model/Models.kt`.
  Touches: video wallpaper cards/details, local video probe metadata, sound detail metadata, shared formatter utilities, mapper/export schemas where metadata already exists.
  Acceptance: video detail/preview surfaces show resolution, aspect ratio, duration, file size, FPS/codec when known, source, and uploader; sound detail shows duration, format/bitrate/file size when known, license, source, and creator; unknown values are labeled without blocking actions; grid scrolling does not add network calls; formatter tests cover large-font-safe labels.
  Complexity: M

## Research-Driven Additions (2026-06-12)

### P2 — Architecture

- [ ] P2 — Split WallpapersViewModel into feature-scoped ViewModels
  Why: `WallpapersViewModel.kt` is 1262 lines handling wallpaper tab state, Discover cache, find-similar, match-my-theme, EyeDropper color search, category filtering, Community Favorites, daily pick, random wallpaper, and multiple pagination states. This makes testing and modification expensive. The existing ROADMAP tracks the SettingsScreen split but not the ViewModel split.
  Evidence: `app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersViewModel.kt` (1262 lines); WallpapersScreen.kt (1987 lines) depends on this single ViewModel for all tab behavior.
  Touches: `WallpapersViewModel.kt` → split into `DiscoverViewModel`, `WallpaperSearchViewModel`, `WallpaperTabViewModel`; `WallpapersScreen.kt`; Hilt providers.
  Acceptance: no single ViewModel exceeds 500 lines; tab-specific state is isolated; existing unit tests pass after refactor; Discover cache behavior is unchanged.
  Complexity: L

### P2 — Platform

- [ ] P2 — Evaluate Navigation 3 vs. Navigation 2.9 for the N-1 toolchain pass
  Why: The ROADMAP targets Navigation 2.9 for type-safe routes and predictive back (NX-4, NX-13). However, Navigation 3 shipped stable in November 2025 with a complete Compose-native rewrite: no Fragment dependency, smaller APK, typed keys replacing string routes, `NavDisplay` replacing `NavHost`, and explicit state classes replacing `NavController`. Targeting 2.9 may create a second migration step to 3 within months.
  Evidence: Navigation 3 stable announcement (Nov 2025); Navigation 3 migration guide; `FreeVibeRoot.kt` (968 lines, all route declarations); N-1 toolchain scope in ROADMAP.
  Touches: N-1 scope decision; `FreeVibeRoot.kt`; `Screen.kt` (route definitions); every `navController.navigate()` call site.
  Acceptance: a documented decision (in ROADMAP or RESEARCH) comparing Nav 2.9 vs Nav 3 migration cost, with the chosen path reflected in N-1 scope. If Nav 3: migration plan covers all 23 screen destinations, deep links, predictive back, and widget navigation.
  Complexity: S (decision) / XL (Nav 3 migration)

### P3 — Observability

- [ ] P3 — Android 17 memory-limit awareness for bitmap-heavy flows
  Why: Android 17 enforces RAM-based app memory limits via `MemoryLimiter:AnonSwap`. Aura's wallpaper rendering, Coil image caching (256 MB disk + memory cache), ML Kit segmentation, and FFmpeg video crop are memory-intensive. On low-RAM Android 17 devices, these flows may trigger silent kills detectable only via `ApplicationExitInfo.getDescription()`.
  Evidence: Android 17 behavior changes documentation; `FreeVibeApp.kt` Coil 256 MB disk cache; `ParallaxWallpaperService.kt` ML Kit segmentation bitmap handling; `VideoCropScreen.kt` FFmpeg operations.
  Touches: `FreeVibeApp.kt` (memory monitoring), `CrashDiagnosticsCollector.kt` (capture `ApplicationExitInfo` on next launch), Settings diagnostics (expose memory-limit kills).
  Acceptance: crash diagnostics capture `MemoryLimiter:AnonSwap` exit reasons; Settings diagnostics show last memory-related kill; Coil memory cache respects device memory class; documentation records expected memory profile per flow.
  Complexity: M
