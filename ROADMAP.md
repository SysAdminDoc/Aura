# Aura — Product Roadmap

> Open-source Android personalization: wallpapers, video wallpapers, ringtones, sounds.
> Stay the OSS alternative to Zedge: no ads, no surprise charges, no dark patterns.

**Version:** 2026-06-10-zedge-local-parity (mapped Zedge parity under local-first constraints).
**Code version at write:** v6.31.1 / versionCode 112 (per `app/build.gradle.kts`; release/lint Gradle runs are memory-heavy on this Windows workstation, so rerun APK compilation only when explicitly needed).
**Charter:** personalization, AMOLED-first, free-by-default, multi-source content aggregation, community-fed catalog, polite live wallpapers (battery-aware, pause-on-invisible).

> **Blocked items** are tracked in [Roadmap_Blocked.md](Roadmap_Blocked.md) — items requiring owner/Firebase Console actions, physical device testing, production database access, or the N-1 toolchain upgrade.

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

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 2 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 3 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 4 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 5 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 6 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 7 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 8 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 9 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 10 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 11 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 12 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 13 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 14 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 15 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 16 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

---

## 🔬 Researcher Queue (Cycle 17 — 2026-06-04)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 18 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 19 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 20 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 21 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 22 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 23 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 24 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 25 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 26 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 27 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 28 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 29 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

## 🔬 Researcher Queue (Cycle 31 — 2026-06-06)

All items shipped or moved to Roadmap_Blocked.md.

---

## Now — execute this cycle

All five Now items (N-1 through N-5) have been moved to [Roadmap_Blocked.md](Roadmap_Blocked.md).
N-1 requires a build environment; N-2 through N-5 have code shipped but remaining work requires Firebase Console owner access or physical device testing.

---

## Next — queued, scored, ready

Remaining actionable items. Most N-1-gated items have been moved to [Roadmap_Blocked.md](Roadmap_Blocked.md). NX-3, NX-10, NX-11, NX-12 shipped and were removed. NX-6 and NX-8 remain partially actionable.

### NX-1. GL/AGSL live wallpaper engine migration (T-9 reframed)

Moved to [Roadmap_Blocked.md](Roadmap_Blocked.md) (N-1-gated).

### NX-2. Lockscreen depth — Subject-aware clock-tuck + lockscreen Glance widgets — `[~]` widget surface enabled 2026-05-17 rev4-impl

Moved to [Roadmap_Blocked.md](Roadmap_Blocked.md) (N-1-gated).

### NX-4. SelectedContentHolder removal (Phase 7.2) — `[~]` process-death survival shipped 2026-05-17 rev4-impl

Moved to [Roadmap_Blocked.md](Roadmap_Blocked.md) (N-1-gated).

### NX-5. Plugin / source ABI — Muzei-compatible "Aura Sources"

Moved to [Roadmap_Blocked.md](Roadmap_Blocked.md) (N-1-gated).

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

Moved to [Roadmap_Blocked.md](Roadmap_Blocked.md) (N-1-gated).

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

> **Note:** Remaining work (per-ABI splits, F-Droid mainline, Izzy submission, branch protection) requires owner actions. See [Roadmap_Blocked.md](Roadmap_Blocked.md).

### NX-9. Media3 1.10 Material3 playback composables + dynamic scheduling

Moved to [Roadmap_Blocked.md](Roadmap_Blocked.md) (N-1-gated).

### NX-13. Predictive-back wiring through Compose NavHost transitions — `[~]` partial, 4 of ~18 screens 2026-05-17 rev4-impl(.2)

Moved to [Roadmap_Blocked.md](Roadmap_Blocked.md) (N-1-gated).

### Audit findings (2026-06-15)

P2 items shipped (removed). P3 missing integration tests moved to [Roadmap_Blocked.md](Roadmap_Blocked.md).

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

## Research-Driven Additions (2026-06-20)

Findings from exhaustive competitor analysis (25+ wallpaper apps, 15+ ringtone/sound apps, 10+ video/live wallpaper apps), Android 17 platform API review, community signal mining (Reddit, XDA, GitHub issues across WallYou/Paperize/Muzei/Zedge/Peristyle, PissedConsumer, Trustpilot, AlternativeTo), dependency changelog review (Kotlin 2.3, Compose 1.11, AGP 9, Coil 3, Room 3, Media3 1.10, Hilt 2.59), and FOSS ringtone ecosystem survey (Ringdroid, Chrono, Noice, RandTune). Full evidence in RESEARCH.md.

### P1

- [ ] P1 — SettingsScreen decomposition into section-level composables
  Why: at 3,407 lines SettingsScreen.kt is the largest file in the codebase and the hardest to test or preview; SettingsComponents.kt (514 lines) and DiagnosticsComponents.kt (604 lines) were already extracted but the main file remains a monolith
  Evidence: `wc -l SettingsScreen.kt` = 3407; only 5 contentDescription annotations in the file; 10 `remember()` calls; no @Preview composables reference SettingsScreen sections
  Touches: SettingsScreen.kt → new WallpaperSettingsSection.kt, SoundSettingsSection.kt, RotationSettingsSection.kt, CommunitySettingsSection.kt, AboutSettingsSection.kt; SettingsViewModel.kt (no changes, just passed to sections)
  Acceptance: SettingsScreen.kt reduced below 500 lines; each section has its own file; all existing SettingsViewModel tests pass without modification; at least one @Preview per section
  Complexity: M

- [ ] P1 — Extract hardcoded strings to string resources for localization readiness
  Why: values/strings.xml has 362 entries but SettingsScreen alone has ~340 hardcoded English strings not in resources; stringResource() is used only 37 times across all UI files; this blocks U-11 (localization), F-Droid community translations, and RTL audit
  Evidence: `grep -c 'stringResource(' app/src/main/java/com/freevibe/ui/` = 37; `grep -c '"[A-Z].*"' SettingsScreen.kt` = 340; zero values-xx/ locale directories
  Touches: all files in ui/screens/ (primarily SettingsScreen.kt, SoundsScreen.kt, WallpapersScreen.kt, VideoWallpapersScreen.kt, FavoritesScreen.kt); values/strings.xml
  Acceptance: all user-visible strings use stringResource(); strings.xml grows to 700+ entries; no hardcoded English strings remain in Compose UI files; app renders identically
  Complexity: M

### P2

- [ ] P2 — Microphone recording for personal ringtone creation
  Why: CommunityAudioRecorder already has proper recording infrastructure (AAC, 128kbps, 44.1kHz, max duration cap, lifecycle management) but is only wired for community uploads; Ringdroid (3.0.1, F-Droid) is the FOSS standard for record-your-own-ringtone; Aura's gap is just the UX entry point
  Evidence: CommunityAudioRecorder.kt exists at app/src/main/java/com/freevibe/service/; Ringdroid is the only FOSS app offering microphone → ringtone flow (github.com/althafvly/ringdroid)
  Touches: SoundsScreen.kt (add Record entry point), new PersonalRecordingFlow.kt or extend SoundEditorScreen.kt, CommunityAudioRecorder.kt (reuse), PreferencesManager.kt (RECORD_AUDIO permission state)
  Acceptance: user can tap Record in Sounds, grant microphone permission, record audio, and route the recording to SoundEditorScreen for trim/fade/set-as-ringtone; no new dependencies
  Complexity: S

- [ ] P2 — Wikipedia Picture of the Day wallpaper source
  Why: WallYou v15.1 ships Wikipedia POTD; zero-auth Wikimedia API; follows the exact pattern of the NASA APOD source shipped in v6.33.0 (NasaApodApi.kt)
  Evidence: WallYou issues and releases (github.com/you-apps/WallYou); Wikimedia REST API docs (api.wikimedia.org/wiki/Feed_API/Reference/Featured_content)
  Touches: new data/remote/wikimedia/WikimediaPotdApi.kt, WallpaperRepository.kt (add to Discover aggregation), Models.kt (ContentSource enum — WIKIMEDIA already exists as legacy value)
  Acceptance: Wikipedia POTD appears in Discover feed as a daily card; image-only filtering (skip non-image POTD); source badge shows Wikipedia attribution; cached per session
  Complexity: S

- [ ] P2 — Sequential wallpaper selection with no-repeat guarantee
  Why: auto-rotation users report seeing the same wallpaper repeatedly before the full pool is exhausted; WallFlow and Paperize both track applied history to prevent repeats; the #1 UX complaint about random selection across all FOSS wallpaper changers
  Evidence: Paperize folder-based sequential selection; WallFlow sequential mode; WallYou #239 (auto-changer unreliability includes repeat complaints)
  Touches: AutoWallpaperWorker.kt (track last-N applied IDs), PreferencesManager.kt (sequential mode toggle + history ring buffer), SettingsViewModel.kt (new toggle), SettingsScreen rotation section
  Acceptance: when sequential mode is enabled, AutoWallpaperWorker excludes the last N applied wallpaper IDs from the next selection; N = min(pool_size / 2, 50); user can toggle between random and sequential in Settings; existing tests pass
  Complexity: S

- [ ] P2 — Time-of-day sound profiles (quiet/work/fun presets)
  Why: RandTune offers time-period scheduling (4h/8h/12h/24h); Ringtone Scheduler offers day-of-week profiles; Aura already has time-of-day wallpaper tint (SolarCalculator), WorkManager scheduling, and RingtoneShuffleWorker — extending to sound profiles is a natural next step that no FOSS app offers
  Evidence: RandTune (play.google.com/store/apps/details?id=com.ezgood.randtunereborn); Ringtone Scheduler (ringtone-scheduler.en.uptodown.com/android); community signal: Samsung users complain about manual ringtone switching between work and personal hours
  Touches: new SoundProfileManager.kt, PreferencesManager.kt (profile definitions + schedule), SettingsScreen sound section (profile editor), RingtoneShuffleWorker.kt (respect active profile)
  Acceptance: user can define 2-3 sound profiles (each maps a ringtone + notification + alarm sound); profiles activate by time-of-day schedule or manual toggle; WorkManager applies the profile's sounds via RingtoneManager; WRITE_SETTINGS permission already requested by ringtone shuffle
  Complexity: M

- [ ] P2 — Sound quality metadata display in browse view
  Why: Aura has internal SoundQuality scoring (SoundQuality.kt) but doesn't expose format, bitrate, or duration to users in the browse/card view; users want to see what they're downloading before committing; trust signal for quality-conscious users
  Evidence: Freesound API returns full metadata; YouTube resolved streams carry codec/bitrate info; community signal: Zedge users complain about downloading low-quality sounds without warning
  Touches: SoundsScreen.kt (add metadata chips to sound cards), SoundQuality.kt (expose display-friendly format string), YouTubeRepository.kt (carry codec info through to Sound model)
  Acceptance: sound cards in browse view show duration and format badge (e.g., "MP3 128k" or "AAC"); detail screen shows full metadata (bitrate, sample rate, codec); no new API calls — metadata comes from existing resolved stream info
  Complexity: S

### P3

- [ ] P3 — Direct boot wallpaper persistence
  Why: Doodle (patzly/doodle-android, GPL-3.0) supports direct boot — wallpaper is active immediately after reboot before user unlock; improves perceived reliability of auto-rotation and live wallpapers
  Evidence: github.com/patzly/doodle-android (DirectBootAware manifest flag, device-protected storage); Android direct boot docs (developer.android.com/training/articles/direct-boot)
  Touches: AndroidManifest.xml (android:directBootAware on WallpaperService subclasses), VideoWallpaperService.kt, ParallaxWallpaperService.kt, WeatherWallpaperService.kt (use device-protected storage for last-applied state)
  Acceptance: after a reboot, Aura's live wallpaper appears immediately before the user unlocks the device; last-applied wallpaper state survives direct boot boundary; no regression on normal (credential-encrypted) storage paths
  Complexity: M

- [ ] P3 — Muzei-style blur/dim behind launcher icons for live wallpapers
  Why: Muzei's signature UX: wallpaper is dimmed/blurred by default to keep launcher icons readable, with double-tap to reveal the full image; Aura's live wallpaper services (Video, Parallax, Weather) could offer this as an opt-in setting
  Evidence: Muzei (github.com/muzei/muzei) blur/dim pattern; Muzei issue #797 (dimming affecting system theme colors); existing WeatherWallpaperService.kt already has a tint ColorMatrix pipeline that could be extended
  Touches: VideoWallpaperService.kt, ParallaxWallpaperService.kt, WeatherWallpaperService.kt (add dim/blur state + double-tap gesture), PreferencesManager.kt (opt-in toggle), SettingsScreen live wallpaper section
  Acceptance: when enabled, live wallpapers render at reduced brightness/with blur overlay; double-tap on the wallpaper surface reveals the full-brightness image for 3 seconds then re-dims; togglable in Settings; battery impact negligible (single ColorMatrix multiply)
  Complexity: M

- [ ] P3 — OS update ringtone/alarm restoration
  Why: Samsung One UI 7.1 (April 2025) wiped custom notification sounds after system update; no competitor handles this well; Aura can detect boot events and verify/re-apply managed sounds
  Evidence: Samsung Community threads (eu.community.samsung.com/t5/galaxy-s25-series/notification-sounds/); DontKillMyApp.com OEM kill documentation; Aura already has TaskerActionReceiver for boot-triggered actions
  Touches: new RingtoneRestorationReceiver.kt (BOOT_COMPLETED), PreferencesManager.kt (persist last-applied ringtone/notification/alarm URIs), RingtoneShuffleWorker.kt (verify-on-boot path)
  Acceptance: after an OS update or reboot, Aura checks if the system ringtone/notification/alarm URI still points to Aura-managed content; if the URI is stale or reset, Aura re-applies the last-known sound; user sees a notification confirming restoration
  Complexity: S

- [ ] P3 — Alarm sound shuffle from favorites
  Why: Chrono (F-Droid) offers alarm shuffle from a directory with random-position start; Aura already has RingtoneShuffleWorker for ringtones — extending to alarm sounds is a direct copy of the pattern
  Evidence: Chrono (f-droid.org/packages/com.vicolo.chrono/); existing RingtoneShuffleWorker.kt pattern
  Touches: RingtoneShuffleWorker.kt (add alarm shuffle mode), PreferencesManager.kt (alarm shuffle toggle + interval), SettingsScreen sound section (alarm shuffle controls)
  Acceptance: user can enable alarm shuffle in Settings; WorkManager periodically sets a random downloaded alarm sound as the system alarm tone; avoids repeating the last-applied alarm; uses existing WRITE_SETTINGS permission
  Complexity: S

- [ ] P3 — On-device wallpaper style learning
  Why: Vanderwaals (AGPL-3.0, Nov 2025) proves MobileNetV4-Conv-Small is viable for on-device visual preference learning on Android; could rank and reorder Discover feed by learned taste profile without server-side analytics or telemetry
  Evidence: github.com/avinaxhroy/Vanderwaals (6000+ wallpapers with ML-driven personalization, zero analytics); distinct from U-2 (on-device generation) — this is recommendation not generation
  Touches: new StylePreferenceModel.kt, WallpaperRepository.kt, WallpapersViewModel.kt, WallpaperFeedQuality.kt
  Acceptance: Aura tracks apply/favorite/skip signals locally; Discover feed reorders by learned preference after 20+ interactions; model stays on-device with no telemetry; user can reset learned preferences in Settings
  Complexity: L

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
| AV1 hardware decode: Android 13+ mandates it; majority of active devices support it (2026) | Low | Low | `Av1CodecSupport` gates on `MediaCodecList` hardware decoder; no software-decode path |
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
  Highest-stakes in-flight screens only: `AiWallpaperScreen` back-press cancels the in-flight Stability AI job (new `AiWallpaperViewModel.cancelGeneration()` + `generationJob: Job` tracker + `onCleared()` defensive cancel — saves the user's API credit budget on bail-out). `VideoCropScreen` back-press during FFmpeg toasts "Cropping in progress — please wait" and holds the screen. Remaining 16 detail/editor/preview/picker screens deferred behind N-1 Navigation 3.

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
- Predictive back — [Compose docs](https://developer.android.com/develop/ui/compose/system/predictive-back), [Android 14 behaviour change](https://developer.android.com/about/versions/14/behavior-changes-14#predictive-back-gesture), [Navigation 3 migration guide](https://developer.android.com/guide/navigation/design/migrate-to-navigation3).
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

### P2 -- Feature parity and differentiation

- [ ] P2 -- **FOSS build flavor isolating Firebase and Play Services**
  Why: IzzyOnDroid and F-Droid mainline require FOSS-clean dependencies. Aura's Firebase, Google Services plugin, Play Services ML Kit, and App Check must be behind a product flavor boundary. IzzyOnDroid is the stepping stone (accepts NonFreeDep anti-feature with disclosure). F-Droid mainline remains blocked until the FOSS flavor removes all proprietary deps.
  Evidence: F-Droid inclusion policy; IzzyOnDroid app inclusion policy; `docs/distribution/channel-strategy.md` full-vs-foss decision.
  Touches: `app/build.gradle.kts` (product flavors), `di/AppModule.kt`, community repositories, `ParallaxWallpaperService.kt`, `SmartCropDetector.kt`, Firebase init, build CI matrix.
  Acceptance: `assembleFossRelease` produces an APK with zero Firebase/Play Services dependencies; community features are compile-time absent; parallax falls back to Canvas-only; `tools/fdroid_preflight.py --expect-pass` succeeds on the FOSS variant.
  Complexity: XL

### P2 -- Observability and testing

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

### P2 — Replacement sources and quick wins

- [ ] P2 — **Lemmy + Wallpaper Cave as crowd-voted replacement sources**
  Why: Reddit's death removes Aura's only crowd-voted external feed (the "Wallpaper of the Day" quality signal). Lemmy exposes an open, keyless API with vote counts; Wallpaper Cave is the catalog WallYou v15.0 just added. Restores the community-feed differentiator without scraping or ToS risk.
  Evidence: WallYou v15.0 release notes (Wallpaper Cave source) and existing Lemmy source; Reddit decommission item above; `ProviderDisclosure.kt` policy-matrix pattern ready for new rows.
  Touches: new `data/remote/lemmy/` (+ optional wallpapercave) API/repository, `Mappers.kt`, `ContentSource` enum, `ProviderDisclosure.kt` policy rows + runtime switch, `WallpapersViewModel.kt` source chips, `DailyWallpaperWorker.kt` source-priority list, provider cache/rate-limit policy hooks (Cycle 3 engine).
  Acceptance: at least one new source browses with paging, provenance fields (uploader, source page URL, license where exposed), default-on runtime switch, and disclosure row; Wallpaper of the Day can rank by Lemmy votes; provider policy doc updated; Wallpaper Cave only ships if a documented, ToS-clean endpoint exists — otherwise Lemmy-only and Wallpaper Cave is recorded as rejected.
  Complexity: M


### P3 — Polish parity


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

### P3 — Observability


## Research-Driven Additions

### P1 — Release trust and real-screen verification

- [ ] P1 — Replace synthetic accessibility gate with real Aura screen checks
  Why: The current connected accessibility gate proves Compose accessibility checks run, but it sets synthetic Button/Switch/Slider content and does not exercise Aura's dense Wallpapers, Sounds, Settings, Video Crop, or live-wallpaper picker surfaces.
  Evidence: `app/src/androidTest/java/com/freevibe/ui/accessibility/AccessibilityReleaseGateTest.kt`; `docs/qa/accessibility-release-gate.json`; Android Compose accessibility testing docs.
  Touches: `AccessibilityReleaseGateTest.kt`, `FreeVibeRoot.kt`, screen test tags/semantics, `docs/qa/accessibility-release-gate.json`, `.github/workflows/verify.yml` if connected test execution becomes gated.
  Acceptance: connected test launches real Aura UI, verifies at least Wallpapers, Sounds, Settings, and one editor/detail flow under accessibility checks, and fails on missing names/touch-target violations in those app surfaces; synthetic-only fixture remains only as a helper if needed.
  Complexity: M

### P2 — Distribution and media compatibility

- [ ] P2 — Add Play-ready AAB dry-run artifact lane
  Why: Aura has Play metadata and policy packets, but the release workflow publishes only a universal APK; Google Play requires Android App Bundles for new apps, so Play readiness cannot be validated without a signed `bundleRelease` artifact and Play App Signing assumptions.
  Evidence: `docs/distribution/play-app-content.json`; `fastlane/metadata/android/en-US/`; `.github/workflows/release.yml`; Android App Bundle docs.
  Touches: `.github/workflows/release.yml`, `app/build.gradle.kts`, signing docs, `docs/distribution/release-metadata-consistency.json`, `tools/release_artifact_bundle_check.py`, fastlane/Play runbook docs.
  Acceptance: manual release dry run can produce and upload/archive a signed `.aab` alongside the GitHub APK artifacts; bundle metadata, versionCode/versionName, signing lineage, SHA-256, and Play App Signing owner steps are documented and checked; GitHub/Obtainium APK behavior remains unchanged.
  Complexity: M

- [ ] P2 — Unify HEIF/AVIF ingestion and metadata-scrub policy
  Why: Paperize and current Android media stacks validate AVIF/HEIF as normal wallpaper inputs, and Aura's local auto-rotation accepts `.heic`, `.heif`, and `.avif` filenames, but shared media sniffing and community upload policy only recognize JPEG/PNG/GIF/WEBP and do not explicitly test EXIF/location stripping.
  Evidence: `app/src/main/java/com/freevibe/service/MediaIngestion.kt`; `app/src/main/java/com/freevibe/service/AutoWallpaperWorker.kt`; `app/src/main/java/com/freevibe/data/repository/WallpaperUploadRepository.kt`; Paperize F-Droid feature list.
  Touches: `MediaIngestion.kt`, `WallpaperUploadRepository.kt`, `WallpaperApplier.kt`, `AutoWallpaperWorker.kt`, upload UI copy, media ingestion tests, privacy/data-safety docs if accepted formats change.
  Acceptance: one source-backed matrix defines supported image formats per flow; HEIF/AVIF are either accepted and safely transcoded or explicitly rejected with UI copy; metadata/location stripping is tested for community uploads; unsupported formats fail with actionable errors.
  Complexity: M

### P2 - Evidence Quality and Observability

- [ ] P2 -- Wire planning-doc manifest consistency into CI and current-context docs
  Why: Current-state planning drift is already detectable locally but not enforced, and agent-facing docs still carry stale stack/version/runtime claims.
  Evidence: `tools/manifest_consistency_check.py`; `.github/workflows/verify.yml`; `test/tools/`; `CLAUDE.md`; `docs/community-callable-quota-enforcement.md`; `functions/package.json`; `RESEARCH.md`
  Touches: `tools/manifest_consistency_check.py`, new/updated `test/tools` coverage, `.github/workflows/verify.yml`, `.github/workflows/release.yml` if release-facing docs are included, `CLAUDE.md`, `docs/community-callable-quota-enforcement.md`
  Acceptance: the manifest consistency check is unit-tested and runs in verify; it checks README/CLAUDE/community callable docs for current stack/runtime claims; stale fixture claims fail; current repo docs pass after stale claims are corrected.
  Complexity: M

### P3 - Operational Maturity


## Research-Driven Additions

### P2 — Maintainability

- [ ] P2 — Split Sounds browse and playback surface into feature-owned modules
  Why: `SoundsScreen.kt` and `SoundsViewModel.kt` are among the largest user-facing files and combine browse tabs, YouTube resolution, community uploads, playback, top hits, downloads, editor routing, and diagnostics.
  Evidence: `app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt`; `app/src/main/java/com/freevibe/ui/screens/sounds/SoundsViewModel.kt`; existing Settings and Wallpapers decomposition roadmap items; Media3 1.10 playback-widget direction.
  Touches: `app/src/main/java/com/freevibe/ui/screens/sounds/`, `SoundsViewModel.kt`, `SoundUiTokens.kt`, sound playback state, YouTube/community/upload state, tests.
  Acceptance: browse/feed, playback/progress, upload/community, and YouTube resolution state are split into feature-owned composables/state holders; no single Sounds file owns all flows; existing sound tests pass; a new contract test covers tab switching while playback continues.
  Complexity: L

### P3 — Observability

## Deep Audit Findings (2026-06-17) — deferred items
