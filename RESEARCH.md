# Research — Aura

## Executive Summary

Aura is a mature open-source Android personalization app (wallpapers, video wallpapers, ringtones, sounds) at v6.31.1 with ~49K lines of Kotlin, 87 test files, and a 1600-line ROADMAP covering security, compliance, distribution, and platform migration. It is the only FOSS app combining wallpapers and ringtones — a validated market gap since Zedge (100M+ installs, Trustpilot 1.6 stars) is the only commercial combo app and users actively seek alternatives. Aura's strongest asset is trust: no ads, no subscription, no tracking, MIT license, extensive privacy/release documentation.

Top 10 new opportunities in priority order:
1. Remediate 99 `contentDescription = null` instances across 20 UI files (accessibility)
2. Track 4 additional yt-dlp CVEs beyond the one already in the Risk Register
3. Add time-of-day wallpaper rotation (distinct from existing adaptive tint)
4. Add "Set With..." intent-filter so other apps can delegate wallpaper-setting to Aura
5. Add reduced-motion / animation accessibility support
6. Evaluate Navigation 3 (stable since Nov 2025) vs. the planned Navigation 2.9 upgrade
7. Add Opus audio output format in AudioTrimmer for better ringtone quality/size
8. Centralize notification channel creation (currently in 3 separate files)
9. Split WallpapersViewModel (1262 lines) alongside the already-planned SettingsScreen split
10. Add Compose @Preview functions for UI development (currently zero exist)

## Product Map

- **Core workflows:** Browse multi-source wallpapers → preview → apply (home/lock/both); browse YouTube sounds → preview → trim/fade → apply as ringtone/notification/alarm; import local videos/GIFs → crop → set as live wallpaper; schedule automatic wallpaper rotation; manage favorites, collections, downloads, diagnostics.
- **User personas:** Ad-free Zedge refugees, privacy-focused Android users, AMOLED enthusiasts, wallpaper power users, offline collection curators, community contributors.
- **Platforms:** Android 8.0+ (minSdk 26), target SDK 35, Kotlin 2.1.0 / Compose / Material 3. Distribution via GitHub Releases + Obtainium; IzzyOnDroid near-term; F-Droid blocked until FOSS flavor isolates Firebase/GMS.
- **Key integrations:** Wallhaven, Pexels, Pixabay, Bing Daily, Reddit (legacy), YouTube (NewPipe + yt-dlp), Freesound (legacy), Firebase (community), ML Kit (parallax), Open-Meteo (weather), Stability AI (generation).

## Competitive Landscape

- **Zedge** (100M+ installs): Broad catalog, AI generation, 24H dynamic wallpapers, marketplace. Learn from: time-of-day wallpaper rotation, unified search. Avoid: ads, credits, subscription traps, cluttered UI (51% of complaints are ads).
- **WallYou** (FOSS, actively maintained): Material 3, Zedge/WallpaperCave/Unsplash sources, predictive back, Material You color extraction. Learn from: Lemmy source, Unsplash integration, sub-15min intervals. Avoid: broken auto-changer reliability (#230, #266).
- **Backdrops** (5M+): Handcrafted in-house wallpapers, zero upsell. Learn from: "zero junk" philosophy, AMOLED category, icon-pack pairings. Avoid: small library, no ringtones.
- **Peristyle** (FOSS, very active): Polished local-gallery manager, Crowdin i18n, SD card support. Learn from: "Set With..." intent delegation (v9.7), external storage handling. Avoid: local-only scope.
- **WallFlow** (FOSS, semi-dormant): TFLite object detection for smart crop, AVIF support requested. Learn from: EXIF tag preservation, post-processing effects. Avoid: single-source dependency, project stagnation.
- **Tapet**: 100% offline procedural generation, 200+ pattern algorithms. Learn from: offline-first generation, palette continuity. Avoid: abstract-only styles.
- **Resplash** (FOSS): Unsplash API frontend, Muzei integration, EXIF data display. Learn from: Unsplash as high-quality free source, photographer credit patterns. Avoid: single-source limitation.
- **Muzei** (FOSS): Mature plugin API (`MuzeiArtProvider`), Wear OS, blur/dim controls. Learn from: plugin ecosystem, lock/home different-source requests (#794). Avoid: no manual browsing.

## Security, Privacy, and Reliability

- **yt-dlp CVE batch:** The Risk Register tracks CVE-2026-26331 but 4 additional CVEs exist: CVE-2026-50019 (cookie leak with curl downloader), CVE-2026-50023 (dangerous file type via filename sanitization), CVE-2026-50574 (arbitrary code execution via aria2c manifests), CVE-2025-54072 (`--exec` command injection on Windows, bypass of CVE-2024-22423). Aura should pin yt-dlp ≥ 2026.02.21 and verify bundled version covers all five.
- **99 `contentDescription = null` instances** across 20 UI files: `SharedComponents.kt` (10), `SoundsScreen.kt` (11), `WallpapersScreen.kt` (11), `VideoWallpapersScreen.kt` (11), `SoundDetailScreen.kt` (10), `AiWallpaperScreen.kt` (6), `CollectionsScreen.kt` (6), `CommunityReportsScreen.kt` (5). These are non-decorative interactive elements that need accessibility labels.
- **Zero reduced-motion support:** No code references `ANIMATOR_DURATION_SCALE`, `prefersReducedMotion`, or `ReducedMotion`. Live wallpaper particle renderers, weather effects, and Compose animations run at full intensity regardless of user accessibility preferences.
- **Notification channels scattered:** Created independently in `FreeVibeApp.kt` (media_playback), `DailyWallpaperWorker.kt` (daily), and `RotationTriggerService.kt` (triggers). Should be centralized for consistency and to prevent channel-creation race conditions.
- **Android 17 memory limits:** New `MemoryLimiter:AnonSwap` enforcement may affect Aura's bitmap-heavy wallpaper rendering and Coil image caching on low-RAM devices.
- **European Accessibility Act (EAA):** Took effect June 28, 2025. Legally relevant for EU distribution via F-Droid/IzzyOnDroid. Aura's current accessibility posture (99 null contentDescriptions, zero reduced-motion support, zero @Preview for visual regression testing) needs attention.

## Architecture Assessment

- **SettingsScreen.kt (3736 lines):** Already noted in ROADMAP for splitting. Owns provider keys, automation, weather/live wallpaper, community identity, diagnostics, permissions, and yt-dlp flows.
- **WallpapersViewModel.kt (1262 lines):** Large ViewModel handling wallpaper state, tabs, findSimilar, matchMyTheme, cached discover, and multiple load operations. Candidate for splitting into tab-specific or feature-specific ViewModels.
- **FreeVibeRoot.kt (968 lines):** NavHost with all route declarations. Will grow further with Navigation 3 migration.
- **Zero @Preview composables:** No Compose Preview functions exist anywhere in the codebase. Prevents visual iteration during development and blocks future screenshot-test infrastructure (Paparazzi/Roborazzi).
- **Localization readiness:** Only 161 entries in `strings.xml` (English-only). Many UI strings are hardcoded in Kotlin. A localization pipeline (Weblate/Crowdin) cannot start until strings are extracted.
- **Zero WindowSizeClass / adaptive layout code:** Confirmed by grep. All layouts are phone-first with fixed grid columns. Android 16 mandates adaptive support on ≥600dp displays.
- **Navigation 3 consideration:** The ROADMAP targets Navigation 2.9 for type-safe routes and predictive back. However, Navigation 3 shipped stable in Nov 2025 with a complete Compose-native rewrite (no Fragment dependency, smaller APK, typed keys replacing string routes). The N-1 toolchain pass should evaluate whether to skip 2.9 and go directly to 3.

## Rejected Ideas

- **Unsplash as a new wallpaper source:** Investigated based on WallYou, WallFlow, and Resplash precedent. Unsplash API terms prohibit wallpaper-app use cases that replicate Unsplash's core service. WallYou works around bot-blocking, which is a ToS gray area. Aura's trust positioning conflicts with this. Source: Unsplash API terms.
- **Lemmy communities as wallpaper source:** Investigated based on WallYou v14 implementation. Niche user base, Reddit-like API surface. Better as a future NX-5 plugin than a core source. Low impact relative to existing Wallhaven/Pexels/Pixabay coverage.
- **Artist marketplace / revenue share (Walli model):** Users praise Walli's artist compensation. But marketplace, payment rails, and creator accounts conflict with Aura's charter (R-3, R-4). Community tips via GitHub Sponsors/Liberapay are the accepted alternative.
- **Google Photos album as wallpaper source:** Investigated from Paperize #531. Requires account-bound cloud API, adds a Google dependency Aura is trying to reduce, and SAF/Photo Picker already covers local gallery. Rejected in prior research.
- **Notification.ProgressStyle (Android 16):** Multi-step progress notifications. Investigated for download/apply feedback. Low value since Aura's downloads are typically fast and the existing notification system works. Defer to a polish pass.

## Sources

**OSS projects:**
- https://github.com/ammargitham/WallFlow/issues
- https://github.com/you-apps/WallYou/issues
- https://github.com/Anthonyy232/Paperize/issues
- https://github.com/Hamza417/Peristyle
- https://github.com/maocide/UndeadWallpaper
- https://github.com/muzei/muzei
- https://github.com/patzly/doodle-android
- https://github.com/T8RIN/ImageToolbox

**Commercial and market signal:**
- https://play.google.com/store/apps/details?id=net.zedge.android
- https://backdrops.io/
- https://play.google.com/store/apps/details?id=com.shanga.walli
- https://play.google.com/store/apps/details?id=com.sharpregion.tapet

**Platform and library:**
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/about/versions/16/features
- https://developer.android.com/reference/android/app/wallpaper/WallpaperDescription
- https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html
- https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- https://coil-kt.github.io/coil/upgrading_to_coil3/
- https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://kotlinlang.org/docs/whatsnew23.html

**Security:**
- https://advisories.gitlab.com/pkg/pypi/yt-dlp/CVE-2026-26331/
- https://nvd.nist.gov/vuln/detail/CVE-2026-50019
- https://nvd.nist.gov/vuln/detail/CVE-2026-50023
- https://nvd.nist.gov/vuln/detail/CVE-2026-50574
- https://nvd.nist.gov/vuln/detail/CVE-2025-54072

**Community signal:**
- https://www.reddit.com/r/androidapps/ (wallpaper app recommendations 2025-2026)
- https://www.reddit.com/r/Android/ (Zedge alternative discussions)

## Open Questions

- Should the N-1 toolchain pass target Navigation 2.9 or skip directly to Navigation 3? Navigation 3 is stable but requires a non-trivial migration of all routing/destination logic.
- Which physical device/emulator matrix is available for the Android 17 memory-limit and background-audio-hardening validation?
- Is the European Accessibility Act (EAA) compliance relevant to Aura's current distribution scope (GitHub Releases, Obtainium, IzzyOnDroid)?
