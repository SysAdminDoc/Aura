# Research — Aura

Date: 2026-09-25 — replaces all prior research.

Confidence labels:

- **Verified:** confirmed in Aura's repository, tracker, release artifacts, or a primary source.
- **Likely:** supported by several credible sources or a strong code-level inference, but not reproduced on hardware during this pass.
- **Assumption:** a product premise that still needs an owner decision.
- **Needs live validation:** evidence identifies a concrete test, but the needed account, device, or legal decision was unavailable.

## Executive Summary

**Verified:** Aura v6.45.3 is a mature Android personalization app for static wallpapers, video and animated wallpapers, ringtones, notifications, alarms, scheduled rotation, editing, local media, and community sharing. It supports Android 8.0 and later, targets API 36, builds full and FOSS flavors, and publishes signed GitHub release APKs (`app/build.gradle.kts:90-106`, `README.md`, `ARCHITECTURE.md`). Its strongest shape is the combination of Reddit-first visual discovery, YouTube-first sound discovery, local-first media ownership, and unusually broad application and automation paths. The highest-value direction is reliability across displays and media lifecycles, backed by enforceable release gates. More providers or social features would dilute that work.

Highest-value opportunities, in priority order:

1. **Verified:** render each live wallpaper engine with `WallpaperService.Engine.getDisplayContext()`. Aura currently passes the service context into all three clock-overlay paths, although Android requires the engine display context when displays have different densities (`VideoWallpaperService.kt:689`, `WeatherWallpaperService.kt:480`, `ParallaxWallpaperService.kt:550`, `WallpaperClockOverlay.kt:88`, [Android API reference](https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine#getDisplayContext())).
2. **Verified:** move to the 1.11.1 patch of Media3 before implementing the existing player-pool roadmap item. The 2026-09-10 patch fixes a secondary-renderer prewarming stall, incorrect `Surface` ownership after a seek reset, stale frames, and an HLS retry defect that overlap Aura's preview and Reddit media paths (`gradle/libs.versions.toml:19`, [Media3 release notes](https://developer.android.com/jetpack/androidx/releases/media3)).
3. **Verified:** complete the existing silent-failure and service-reliability items before adding playback modes. Empty Firebase cancellation handlers, swallowed activity-launch failures, and fallback display metrics still create false success or stale state (`ROADMAP.md`, `VoteRepository.kt`, `FreeVibeWidget.kt`, `VideoWallpaperService.kt`).
4. **Verified:** finish the accessibility and localization gates already in `ROADMAP.md`. Issue [#47](https://github.com/SysAdminDoc/Aura/issues/47) remains the only open tracker item, Simplified Chinese is the only contributed locale, and repository review still finds user-facing literals outside the current checker (`app/src/main/res/values-zh/`, `tools/compose_hardcoded_string_check.py`).
5. **Verified:** upgrade the repository's Firebase CLI from 15.19.1 to 15.31.0 and require a clean root audit. On 2026-09-25, `npm audit` reported nine moderate development-tool vulnerabilities and named 15.31.0 as the non-major fix; the production Functions tree reported zero (`package.json:15`, `package-lock.json:4446-4448`, [Firebase CLI v15.31.0](https://github.com/firebase/firebase-tools/releases/tag/v15.31.0)).
6. **Verified:** complete the existing FFmpeg removal and release-size work. The arm64 release APK is about 64 MB and the universal APK about 210 MB, while `youtubedl-android:ffmpeg` remains bundled (`app/build.gradle.kts:252,444-445`, [v6.45.3 assets](https://github.com/SysAdminDoc/Aura/releases/tag/v6.45.3)).
7. **Needs live validation:** resolve the blocked YouTube authorization, Reddit OAuth, and combined GPL distribution decisions before expanding those integrations (`Roadmap_Blocked.md`, [YouTube policies](https://developers.google.com/youtube/terms/developer-policies), [Reddit Data API terms](https://redditinc.com/policies/data-api-terms), [GPL FAQ](https://www.gnu.org/licenses/gpl-faq.en.html)).
8. **Verified:** finish the existing resumable-download, relink, export-contract, and rotation-recovery items. Competitor releases and user discussions repeatedly reward rotations that recover after manual changes, reboot, display changes, and missing media; Aura already has the right items and should not duplicate them (`ROADMAP.md`, [Paperize v4.1.1](https://github.com/Anthonyy232/Paperize/releases/tag/v4.1.1), [UndeadWallpaper v1.4.0](https://github.com/maocide/UndeadWallpaper/releases/tag/v1.4.0)).

## Product Map

### Core workflows

- **Verified:** discover, search, filter, favorite, collect, edit, download, and apply static wallpapers to home, lock, or both (`WallpapersScreen.kt`, `WallpaperDetailScreen.kt`, `WallpaperEditorScreen.kt`, `WallpaperApplier.kt`).
- **Verified:** preview Reddit, YouTube, local, GIF, and optional catalog video sources, then crop and install them through Android's live wallpaper flow (`VideoWallpapersScreen.kt`, `VideoCropScreen.kt`, `VideoWallpaperService.kt`).
- **Verified:** search YouTube or use Aura Originals, trim and transform audio, then apply it as ringtone, notification, alarm, or contact ringtone (`SoundsScreen.kt`, `SoundEditorScreen.kt`, `AudioTrimmer.kt`, `SoundApplier.kt`).
- **Verified:** automate wallpaper changes by interval, time, theme, unlock, screen-off, collection, or source, with history and health diagnostics (`AutoWallpaperWorker.kt`, `DailyWallpaperWorker.kt`, `RotationTriggerService.kt`, `WallpaperHistoryManager.kt`).

### User personas

- **Verified:** discovery-first users want a good visual or sound without knowing a source name (`README.md`, browse routes under `ui/screens/`).
- **Verified:** collectors organize favorites, collections, local imports, downloads, history, and exports (`LibraryExporter.kt`, Room entities in `data/local/`).
- **Verified:** automators use schedules, widgets, tiles, broadcasts, named sources, and device events (`SettingsScreen.kt`, widget and tile packages, `RotationTriggerService.kt`).
- **Likely:** privacy-conscious and alternative-store users value the FOSS flavor, local media, no mandatory account, and reproducible APK metadata (`app/build.gradle.kts`, `docs/distribution/`, [F-Droid inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/)).

### Platforms and distribution

- **Verified:** minSdk 26, compileSdk 36, targetSdk 36, versionCode 149, and versionName 6.45.3 (`app/build.gradle.kts:90-106`).
- **Verified:** the 2026-09-13 GitHub release provides arm64, armeabi-v7a, x86, x86_64, universal APKs, and SHA-256 checksums ([release](https://github.com/SysAdminDoc/Aura/releases/tag/v6.45.3)).
- **Verified:** full builds include Firebase community features; FOSS builds use local no-op adapters and exclude Firebase (`app/build.gradle.kts:421-434`).

### Key integrations and data flows

- **Verified:** Reddit Atom feeds lead wallpaper and video discovery; optional Wallhaven, Bing, Pexels, Pixabay, NASA, and Wikimedia sources supplement them (`README.md:204-215`, repository classes under `data/repository/`).
- **Verified:** NewPipeExtractor searches YouTube and `youtubedl-android` resolves or downloads streams; Media3 handles playback and transforms (`app/build.gradle.kts:436-445`, `ARCHITECTURE.md:75-80`).
- **Verified:** database schema version 20 stores favorites, downloads, search history, cache, and library records through Room; DataStore holds settings; WorkManager schedules background changes (`Database.kt:30-45`, `ARCHITECTURE.md:35-40,140-180`).
- **Verified:** Firebase RTDB, Storage, Auth, and Functions support voting, uploads, reports, sharing, and moderation in the full flavor (`ARCHITECTURE.md`, `functions/`, Firebase repositories).

## Competitive Landscape

| Product | What it does well and what Aura should learn | What Aura should avoid |
|---|---|---|
| [Paperize](https://github.com/Anthonyy232/Paperize) | Treats manual apply, timer reset, lifecycle pauses, foldables, Android 17, and localization as one rotation-reliability system. Its 2026-08-30 and 2026-09-12 releases validate Aura's existing timer and adaptive-display priorities. | Do not narrow Aura to folder rotation or copy release automation that conflicts with Aura's local-only release policy. |
| [WallFlow](https://github.com/ammargitham/WallFlow) | Saved searches and reusable source configurations make Reddit and Wallhaven discovery easy to repeat. Aura's existing named-feed roadmap item is the right parity move. | Do not add provider count without health checks, attribution, and stable pagination. |
| [Peristyle](https://github.com/Hamza417/Peristyle) | Provides a polished offline collection, Wallhaven search, automation hooks, and a switchable immersive home. Its 2026-09-14 release shows that search and presentation details still matter after the core engine works. | Do not create parallel home modes until Aura's current routes share stable behavior and test tags. |
| [UndeadWallpaper](https://github.com/maocide/UndeadWallpaper) | Its 2026-09-11 release joins large video playlists, gapless loops, aspect-ratio handling, decoder recovery, and background ingestion. This supports Aura's existing playlist and ingestion work. | Do not promise unlimited queues without bounded memory, storage, and player ownership. |
| [Muzei](https://github.com/muzei/muzei) | Has a durable provider contract, rotation lifecycle, dimming, and extension model. Aura can learn from the narrow provider boundary. | Do not adopt an open plugin API before trust, capability declarations, and failure isolation exist. |
| [Wallora](https://github.com/thissayantan/wallora) | Combines several sources with search, favorites, downloads, and external intents in a small product. It supports Aura's external-automation roadmap item. | Do not make external intents accept arbitrary URLs or filesystem paths. |
| [Backdrops](https://play.google.com/store/apps/details?id=com.backdrops.wallpapers) | Builds identity around curated original art, community submissions, favorites sync, and paid shuffle. Aura should keep clear curation and a visible rotation entry point. | Avoid coin layers, mandatory accounts, and cloud sync as a prerequisite for basic library use. |
| [Walli](https://play.google.com/store/apps/details?id=com.shanga.walli) | Uses artist curation, follows, and playlists to make a large catalog feel intentional. Aura can improve attribution and collection storytelling. | Avoid ad-heavy discovery and a remote-only library. |
| [Tapet](https://play.google.com/store/apps/details?id=com.sharpregion.tapet) | Generates wallpapers on-device, supports palettes, and schedules changes without catalog rights or network availability. | A full generator would split Aura's focus; a small procedural source is only worthwhile after the current engine backlog is clear. |
| [Wallpaper Engine](https://store.steampowered.com/app/431960/Wallpaper_Engine/) | Offers local video transfer, playlists, frame-rate controls, audio policy, and battery-aware playback. Aura should expose measurable performance choices where they affect live media. | Avoid requiring a desktop companion or hiding unsupported codec behavior behind transfer success. |
| [YTDLnis](https://github.com/deniscerri/ytdlnis) | Separates user-facing acquisition choices from yt-dlp execution and makes format selection explicit. Its architecture is useful for Aura's media boundary. | Do not inherit a general-purpose downloader surface or broaden unauthorized download behavior. |

## Reported Issues

- **Verified, open:** [#47](https://github.com/SysAdminDoc/Aura/issues/47), opened 2026-07-30, asks contributors to translate Aura. PR [#48](https://github.com/SysAdminDoc/Aura/pull/48) supplied Simplified Chinese, but the umbrella remains useful for other locales. The actionable local work is already captured by the localization-gate and Simplified Chinese roadmap items; no duplicate is needed.
- **Verified, closed:** [#44](https://github.com/SysAdminDoc/Aura/issues/44) reported valid YouTube WebM/Opus downloads rejected as non-audio. v6.38.1 added EBML container recognition and the reporter confirmed the fix on 2026-07-30. Reopening it would duplicate completed work.
- **Verified, closed:** [#2](https://github.com/SysAdminDoc/Aura/issues/2) reported a Sounds-tab crash on Android 10 caused by a NewPipe Java API call above Aura's runtime floor. Core library desugaring now covers the API 26 floor (`app/build.gradle.kts:168-172`, `README.md:287-292`). Keep the existing low-API smoke test rather than create a new feature item.
- **Verified:** there were no open pull requests on 2026-09-25. Discussions [#45](https://github.com/SysAdminDoc/Aura/discussions/45) and [#46](https://github.com/SysAdminDoc/Aura/discussions/46) contain no reproducible report or requested behavior, so they do not justify roadmap work.

## Security, Privacy, and Reliability

- **Verified:** the root JavaScript toolchain has nine moderate advisories through `firebase-tools` 15.19.1. The affected chains include OpenTelemetry baggage allocation, `csv-parse` prototype replacement, `qs` denial of service, `stream-json` quadratic filtering, and `uuid` buffer bounds. `npm audit` identifies `firebase-tools` 15.31.0 as the non-major fix, and that release pins fixed `csv-parse` and `stream-json` families (`package.json:15`, `package-lock.json`, advisory URLs in Sources).
- **Verified:** `npm audit --omit=dev` in `functions/` reported zero vulnerabilities on 2026-09-25. A Grype directory scan also reported no known package vulnerabilities. The new audit item is deployment-tool hardening, not an APK runtime vulnerability.
- **Verified:** all three live engines draw the clock with service resources even though Android says concurrent engines can target displays with different densities. Surface dimensions are mostly engine-local, so the remaining defect is concentrated in fallback metrics and overlay resources (`WallpaperClockOverlay.kt:80-93`, `WeatherWallpaperService.kt:259-260,480`, `ParallaxWallpaperService.kt:293-294,550`, `VideoWallpaperService.kt:689`).
- **Verified:** the 1.11.1 patch of Media3 directly fixes prewarming and `Surface` ownership defects relevant to the queued player pool, plus an HLS retry problem relevant to Reddit streams. Taking the patch before pooling reduces the chance of building tests around known bad behavior (`gradle/libs.versions.toml:19`, [release notes](https://developer.android.com/jetpack/androidx/releases/media3)).
- **Likely:** Aura's single 400 by 400 widget bitmap is below Android 17's strict RemoteViews budget even on a 320 by 480 pixel display, but the code documents an obsolete approximate 5 MB limit instead of the API 37 formula (`FreeVibeWidget.kt:119-139`, [Android 17 behavior changes](https://developer.android.com/about/versions/17/behavior-changes-17)). Add a byte-budget assertion to the existing API 37 gate, not a separate roadmap item.
- **Needs live validation:** Android 17 requires native libraries loaded dynamically by target-37 apps to be read-only. Aura does not call `System.load()` directly, but `youtubedl-android` extracts Python and FFmpeg payloads at runtime (`app/build.gradle.kts:252-255`). Exercise the full flavor under the existing API 37 gate before deciding whether the library is affected.
- **Needs live validation:** YouTube download rights, Reddit OAuth approval and deletion terms, and the combined MIT/GPL packaging decision remain external blockers already recorded in `Roadmap_Blocked.md`. Public sources establish the constraints but cannot choose Aura's legal position.

## Architecture Assessment

### Strengths

- **Verified:** full and FOSS product flavors isolate optional Firebase code, while shared interfaces keep most application code common (`app/build.gradle.kts:421-434`, flavor source sets).
- **Verified:** Room migrations are explicit through database version 20, downgrade loss is guarded and documented, and library export covers the important user-owned records (`Database.kt`, `DatabaseMigrations.kt`, `DatabaseDowngradeGuard.kt`, `LibraryExporter.kt`).
- **Verified:** media ingestion, application, download, and live wallpaper services are distinct boundaries rather than being embedded wholly in Composables (`service/`, `data/repository/`, `ARCHITECTURE.md`).

### Boundary and refactor candidates

- **Verified:** `WallpapersScreen.kt`, `SoundsScreen.kt`, and `VideoWallpapersScreen.kt` are roughly 80 to 91 KB each. Their route orchestration, state rendering, item actions, and dialogs should continue moving into tested components as those files are touched. Do not schedule a broad rewrite (`app/src/main/java/com/freevibe/ui/screens/`).
- **Verified:** `VideoWallpapersViewModel.kt` and editor ViewModels still own network or file work. The existing roadmap item that moves IO behind repositories is the correct boundary, with no second refactor item needed (`VideoWallpapersViewModel.kt`, `SoundEditorViewModel.kt`, `ROADMAP.md`).
- **Verified:** display-specific resources belong to each `WallpaperService.Engine`. Cache sharing may remain service-level, but density, window metrics, surface transforms, and overlays must be engine-local (`VideoWallpaperService.kt`, `WeatherWallpaperService.kt`, `ParallaxWallpaperService.kt`).
- **Verified:** deployment tooling is part of the release boundary. The root lockfile, Functions lockfile, emulator checks, backend manifest, and Firebase CLI version should be validated together (`package.json`, `package-lock.json`, `functions/package-lock.json`, `docs/community-backend-manifest.json`, `tools/`).

### Test and documentation gaps

- **Verified:** no test creates two live wallpaper engines with distinct densities and surfaces. Add that test with the display-context fix and include preview versus applied engine state.
- **Verified:** the player-pool acceptance test must cover rapid scroll, seek reset, HLS end-of-input, stale-frame prevention, bounded pool size, and release when composition ends. The 1.11.1 patch of Media3 provides the fixed baseline (`ROADMAP.md`, `VideoWallpapersScreen.kt`).
- **Verified:** the root npm audit is not a release gate even though Firebase CLI is used by backend validation and deployment. The Functions production audit and root development audit need separate assertions so an APK claim is not confused with toolchain risk (`package.json`, `functions/package.json`, release scripts under `tools/`).
- **Verified:** observability work should stay in the existing silent-failure and diagnostics items. Aura already has rotation history, health records, logs, and crash reports; the gap is that several error paths never reach them (`VoteRepository.kt`, `VideoWallpaperService.kt`, `FreeVibeWidget.kt`, `ROADMAP.md`).
- **Verified:** Android 17 coverage already exists as a blocked device/toolchain lane. Extend that lane with RemoteViews byte accounting and the runtime-extracted native payload test rather than creating two more backlog entries (`Roadmap_Blocked.md`).
- **Verified:** accessibility and localization checks still miss user-facing state and error sinks. Existing roadmap items name the affected screens and should remain the single source of implementation detail (`tools/compose_hardcoded_string_check.py`, `docs/localization/`, `ROADMAP.md`).

## Rejected Ideas

- **More wallpaper catalog APIs:** rejected because Aura already advertises eight visual sources and has unused repository implementations queued for removal (`README.md`, `ROADMAP.md`). Reliability and attribution matter more than another feed.
- **A general-purpose download manager:** rejected because it would broaden YouTube policy and maintenance risk. Aura should keep acquisition limited to media needed for a user-requested apply or save action ([YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies), [YTDLnis](https://github.com/deniscerri/ytdlnis)).
- **A public plugin marketplace:** rejected until provider capabilities, sandboxing, signing, failure isolation, and update policy exist. Muzei's narrow provider contract is useful; an installable marketplace is not (`ROADMAP.md`, [Muzei](https://github.com/muzei/muzei)).
- **Mandatory cloud library sync:** rejected because it conflicts with Aura's account-optional, full/FOSS, local-library model and adds deletion obligations. Keep portable export and relink work first (`LibraryExporter.kt`, `Roadmap_Blocked.md`, [Backdrops](https://play.google.com/store/apps/details?id=com.backdrops.wallpapers)).
- **Shared household or team accounts:** rejected because Aura personalizes one Android user's device, while its genuinely multi-user surfaces are community uploads, votes, reports, profiles, and shared collections. Those Firebase surfaces already have quota, deletion, and authorization work in `ROADMAP.md` and `Roadmap_Blocked.md`.
- **A standalone Android 17 widget redesign:** rejected as duplicate work. The current bitmap is bounded, and byte-budget coverage belongs in the existing API 37 gate (`FreeVibeWidget.kt:119-139`, `Roadmap_Blocked.md`).
- **A new live-wallpaper rendering stack now:** rejected until the display-context defect and engine lifecycle tests land. The GL/AGSL migration is already parked as NX-1 in `Roadmap_Blocked.md`.
- **Blanket dependency upgrades:** rejected because Room 3, Navigation 3, Lifecycle 2.10, and newer toolchain lines have coupled Kotlin, KSP, AGP, and compile-SDK constraints. Follow `Roadmap_Blocked.md` and upgrade only lines with a verified benefit.
- **Duplicate playlist, shuffle-pool, saved-feed, timer-reset, accessibility, or localization items:** rejected because each is already actionable in `ROADMAP.md`. Competitor evidence strengthens their priority but does not create new work.

## Sources

### Project and tracker

- https://github.com/SysAdminDoc/Aura
- https://github.com/SysAdminDoc/Aura/releases/tag/v6.45.3
- https://github.com/SysAdminDoc/Aura/issues/2
- https://github.com/SysAdminDoc/Aura/issues/44
- https://github.com/SysAdminDoc/Aura/issues/47
- https://github.com/SysAdminDoc/Aura/pull/48
- https://github.com/SysAdminDoc/Aura/discussions/45
- https://github.com/SysAdminDoc/Aura/discussions/46

### Open-source products and catalogs

- https://github.com/Anthonyy232/Paperize
- https://github.com/Anthonyy232/Paperize/releases/tag/v4.1.0
- https://github.com/Anthonyy232/Paperize/releases/tag/v4.1.1
- https://github.com/ammargitham/WallFlow
- https://github.com/Hamza417/Peristyle
- https://github.com/Hamza417/Peristyle/releases/tag/v9.8.0
- https://github.com/maocide/UndeadWallpaper
- https://github.com/maocide/UndeadWallpaper/releases/tag/v1.4.0
- https://github.com/muzei/muzei
- https://github.com/thissayantan/wallora
- https://github.com/deniscerri/ytdlnis
- https://github.com/TeamNewPipe/NewPipeExtractor
- https://github.com/yausername/youtubedl-android
- https://github.com/pcqpcq/open-source-android-apps/blob/master/categories/personalization.md
- https://github.com/offa/android-foss
- https://f-droid.org/en/categories/wallpaper/

### Commercial and adjacent products

- https://play.google.com/store/apps/details?id=com.backdrops.wallpapers
- https://play.google.com/store/apps/details?id=com.shanga.walli
- https://play.google.com/store/apps/details?id=com.hampusolsson.abstruct
- https://play.google.com/store/apps/details?id=com.sharpregion.tapet
- https://www.zedge.net/
- https://store.steampowered.com/app/431960/Wallpaper_Engine/
- https://help.wallpaperengine.io/en/mobile/setup.html
- https://tasker.joaoapps.com/

### Community signal

- https://www.reddit.com/r/androidapps/comments/1p90p95/
- https://www.reddit.com/r/androidapps/comments/1nl2zwj/
- https://www.reddit.com/r/droidappshowcase/comments/1rx6v9a/
- https://www.reddit.com/r/GooglePixel/comments/18eqrae/
- https://www.reddit.com/r/AndroidQuestions/comments/m4gden/
- https://www.reddit.com/r/tasker/comments/sbz15u/
- https://news.ycombinator.com/item?id=41641704

### Platform, policy, and standards

- https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/reference/android/appwidget/AppWidgetManager
- https://developer.android.com/docs/quality-guidelines/adaptive-app-quality
- https://developer.android.com/develop/ui/compose/accessibility
- https://developer.android.com/develop/ui/compose/testing/common-patterns
- https://developer.android.com/guide/topics/resources/app-languages
- https://developer.android.com/develop/background-work/background-tasks/data-transfer-options
- https://developer.android.com/develop/background-work/services/fgs/timeout
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate
- https://www.w3.org/TR/WCAG22/
- https://f-droid.org/en/docs/Inclusion_Policy/
- https://izzyondroid.org/docs/general/AppInclusionPolicy/
- https://www.youtube.com/static?template=terms
- https://developers.google.com/youtube/terms/developer-policies
- https://redditinc.com/policies/data-api-terms
- https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki
- https://www.gnu.org/licenses/gpl-faq.en.html

### Dependencies and security

- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/jetpack/androidx/versions
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/glance
- https://coil-kt.github.io/coil/changelog/
- https://github.com/firebase/firebase-tools/releases/tag/v15.31.0
- https://github.com/advisories/GHSA-8988-4f7v-96qf
- https://github.com/advisories/GHSA-8cw4-87c7-c6xx
- https://github.com/advisories/GHSA-x5fp-wj9c-mxmx
- https://github.com/advisories/GHSA-4mjr-xmp4-gh2g
- https://github.com/advisories/GHSA-528h-pc64-c93x
- https://github.com/advisories/GHSA-w5hq-g745-h8pq

### Research and engineering

- https://research.google/pubs/mobile-applications-on-device-testing-at-google-scale/
- https://ieeexplore.ieee.org/document/9477481/
- https://arxiv.org/abs/2402.09001
- https://engineering.fb.com/2026/06/22/video-engineering/adopting-av1-for-real-time-communication-rtc-meta/

## Open Questions

- **Needs live validation:** does the owner have written authorization from YouTube and applicable rights holders for extraction, download, conversion, and reuse as wallpaper or device sound? The answer determines which actions may ship, not whether discovery can remain YouTube-first.
- **Needs live validation:** is Aura registered and approved for the Reddit API use, cache duration, offline records, and deletion process it intends to ship? No approval evidence belongs in the repository.
- **Needs live validation:** has counsel or a documented owner review selected a distribution position for NewPipeExtractor and the GPL-enabled FFmpeg payload inside the MIT-licensed project? The package facts are verified; the legal choice is not.
- **Assumption:** are GitHub and Obtainium the only committed channels after 2026-09-25, or are Google Play, IzzyOnDroid, Accrescent, and F-Droid active targets? That decision changes provider capabilities, backend packaging, and artifact-size priority.
