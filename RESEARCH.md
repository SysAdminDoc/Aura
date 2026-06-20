# Research — Aura

## Executive Summary

Aura is a mature (v6.32.9, 178 Kotlin source files, 97 test files, 520 commits in 3 months) open-source Android personalization app combining wallpapers, video wallpapers, ringtones, and sounds — the only FOSS project covering this breadth. Zedge (500M+ installs, 76% unfavorable on Trustpilot) is the only commercial app matching this scope, and the FOSS wallpaper space (WallYou 1k★, Paperize 1.1k★, Muzei 4.9k★, Peristyle 643★) is entirely wallpaper-only. Aura's strongest axis is this unique combination plus its privacy-forward, no-ads stance.

The highest-value directions, in priority order:
1. **Android 17 background audio hardening compliance** — sound preview/apply paths will silently fail on API 37 without foreground service or visible activity
2. **SoundsViewModel decomposition** — the largest ViewModel is a monolith handling YouTube/Community/playback/upload state
3. **OEM battery optimization guidance** — auto-wallpaper reliability is the #1 complaint across all FOSS wallpaper changers
4. **Ringtone shuffle from favorites** — unique feature no FOSS app offers, buildable on existing ContactRingtoneService
5. **NASA APOD wallpaper source** — highly requested across WallYou/Muzei/Reddit, zero-auth API
6. **AV1 video codec preference** — majority of devices now support hardware decode, 30-50% smaller files
7. **Compose @Preview fixtures** — zero previews across 178 source files limits development velocity
8. **On-device wallpaper style learning** — Vanderwaals proves MobileNetV4 is viable for taste-adaptive feeds

## Product Map

- Core workflows: browse/search/apply static wallpapers, live video wallpapers, GIF/video crops, sounds, ringtones, notification sounds, alarms; edit/crop/preview media; rotate wallpapers by scheduler or external automation; manage favorites, downloads, collections, profiles, reports, backups, diagnostics.
- User personas: privacy-focused Android users, ad-free Zedge replacement seekers, AMOLED enthusiasts, live-wallpaper users, ringtone/sound users, community contributors, F-Droid/degoogle users.
- Platforms: Android (minSdk 26, targetSdk 35, package `com.freevibe`); GitHub Releases + Obtainium primary; IzzyOnDroid near-term; F-Droid blocked by Firebase/Play Services.
- Key integrations: Wallhaven, Bing, Pexels, Pixabay, YouTube (NewPipe + yt-dlp), Freesound/legacy sources, Open-Meteo, Firebase (Auth/RTDB/Storage/Functions/App Check), ML Kit segmentation, Stability AI, Room, DataStore, MediaStore, SAF, WorkManager, Glance widgets, Media3 ExoPlayer.

## Competitive Landscape

### Zedge (commercial, 500M+ installs, ~19.6M MAU)
- **Learn from:** Unified wallpaper + ringtone + notification discovery; AI text-to-ringtone generation (Audio AI — text prompt to ringtone); per-contact tone assignment; massive curated catalog.
- **Avoid:** Aggressive ads (76% unfavorable on Trustpilot); credit/coin economy; unauthorized charges and billing disputes; background resource drain (reported "always running 100%"); AI content quality flooding ("AI slop").

### Paperize (1.1k★, GPL-3.0, actively maintained)
- **Learn from:** Fully offline-first design eliminates privacy concerns; v4.0.0-alpha live wallpaper mode validates NX-1 GL/AGSL engine approach; folder-based local organization; dark/light theme-aware wallpaper switching (most-requested feature, #516).
- **Avoid:** No online sources limits discovery; minSdk 31 excludes older devices.

### WallYou (1k★, GPL-3.0, very active — v15.2 Jun 2026)
- **Learn from:** Privacy-first multi-source aggregation (Wallhaven, NASA APOD, Wikipedia POTD); fast release cadence (15 releases in 2026); part of the you-apps ecosystem. Top requests: staggered grid (#280), Reddit/Lemmy (#267), sub-15min intervals (#229).
- **Avoid:** Auto-changer reliability bugs on LineageOS/Samsung (#230, #259); limited to wallpapers only.

### Muzei (4.9k★, Apache-2.0, canonical plugin architecture)
- **Learn from:** Provider/source plugin API is the model for NX-5; blur/dim effect that recedes for icons; massive ecosystem of third-party sources (NASA APOD, Reddit, Unsplash, 500px, Flickr, Bing, NatGeo). Top requests: home/lock separation (#794), music-based wallpaper (#128), search/filter (#800).
- **Avoid:** API release cadence (2-year gaps); art-only positioning limits appeal; UI dated.

### Wallpaper Engine (commercial, cross-platform)
- **Learn from:** Video wallpaper playlists with time-of-day scheduling; interactive wallpapers; desktop-to-phone library sync; v2.8 dynamic model generation + HDR video; explicit battery/performance controls. The gold standard for live wallpapers.
- **Avoid:** Desktop-dependency model; heavy 3D effects that drain battery; proprietary Scene format.

### Vanderwaals (29★, AGPL-3.0, novel — Nov 2025)
- **Learn from:** On-device MobileNetV4-Conv-Small learns user visual style preferences — genuinely novel personalization approach; 6,000+ GitHub + 5,400+ Bing wallpapers; zero analytics. Proves on-device ML recommendation is feasible.
- **Avoid:** Tiny community; limited to wallpapers only.

### RandTune (niche, Google Play)
- **Learn from:** Ringtone rotation/randomization per call is unique — no other app replicates this; per-contact random playlists; mini-game alarm feature.
- **Avoid:** No content library; niche appeal.

### Muviz Edge (commercial)
- **Learn from:** Real-time audio visualizer overlay on screen edges; music source selection; AOD integration with visualizer; automatic album art color extraction for palette; AMOLED pixel shifting for burn-in protection.
- **Avoid:** Commercial, closed source; niche appeal.

## Security, Privacy, and Reliability

- **P1 — Android 17 background audio hardening:** Apps must have a visible activity or foreground service (not SHORT_SERVICE) to play audio on API 37. Audio APIs **fail silently** — no exception thrown. Aura's `AudioPlaybackService` (`AudioPlaybackService.kt`) is a foreground service with MediaSession which should comply, but `SoundApplier.kt` ringtone-set and `AudioPlaybackManager.kt` preview paths need verification against the new silent-failure semantics. Files to audit: `AudioPlaybackService.kt`, `AudioPlaybackManager.kt`, `SoundApplier.kt`, `SoundsViewModel.kt`.

- **P2 — OEM battery optimization kills auto-wallpaper:** The #1 reliability complaint across WallYou (#230), Paperize, and all FOSS wallpaper changers. Samsung, Xiaomi, and OnePlus aggressively kill background workers via proprietary battery optimization. DontKillMyApp.com documents per-OEM workarounds. Aura's `AutoWallpaperWorker` and `RotationTriggerService` are affected. A manufacturer-detected Settings guidance screen would address this.

- **AV1 risk register update:** The existing risk register says "AV1 hardware decode <10% install base." Research shows Android 13+ mandates hardware AV1 decode for new device certification, and Meta reports 50%+ video watch is now AV1. The risk row should be updated to reflect current install base; the mitigation can shift from "gate on Performance Class >= 33" to "gate on MediaCodecList hardware decode availability."

- **Credential Manager for NX-7:** Google Sign-In (`play-services-auth`) is deprecated and being removed from Play Services. NX-7 (Favorites sync) should use `androidx.credentials` Credential Manager instead. This updates NX-7's implementation approach, not a new item.

- **Room 2.x maintenance mode:** Room 3.0 shipped (March 2026) with a complete rewrite (KSP-only, coroutines-only, new package `androidx.room3`). Room 2.x enters maintenance. Aura should stay on 2.7.2 until after N-1, then evaluate Room 3.0 as a future migration.

- **Compose stability annotations:** Only 6 `@Immutable`/`@Stable` annotations across 3 files vs 178 source files. Strong Skipping (now stable in Compose 1.10+) reduces the urgency but hot-path data classes (`Wallpaper`, `Sound`, `VideoWallpaperItem` in `Models.kt`) still benefit from explicit stability markers.

## Architecture Assessment

- **SoundsViewModel is the largest monolith:** Handles YouTube search, community tab, Freesound, playback state, upload flow, and tab management in a single ViewModel. `WallpapersViewModel` and `SettingsScreen` have undergone partial decomposition; `SoundsViewModel` needs the same treatment. Split candidates: browse state, playback management, upload flow. Files: `SoundsViewModel.kt`, `SoundsScreen.kt`, `SoundDetailScreen.kt`.

- **Zero `@Preview` composables:** 178 Kotlin source files, 0 `@Preview` annotations. Aura has Roborazzi screenshot tests (v6.32.3) for CI regression but no interactive development-time previews. All active Compose competitors (Paperize, WallYou, Peristyle) ship previews. This limits iteration speed on UI changes.

- **No adaptive layout APIs:** Zero usage of `WindowSizeClass`, `NavigationSuiteScaffold`, or `ListDetailPaneScaffold`. Existing L-10 roadmap item covers this; remains the right path after N-1. Compose Material 3 Adaptive 1.1.0 is stable with predictive back built in.

- **No localization:** Zero locale translations. Only English `values/strings.xml`. Existing U-11 covers this. F-Droid community typically expects 5-10 languages minimum.

- **AGP 9 migration notes for N-1:** R class fields are non-final — any `when`/`switch` on resource IDs must convert to `if-else`. `kotlin-android` plugin deprecated when using AGP 9 (built-in Kotlin support). Hilt 2.59 requires AGP 9. Factory constructors in Dagger-generated code changed from public to private (2.57 breaking change).

## Rejected Ideas

- **Zedge-style ads, credits, streaks, subscription pressure:** Rejected. Charter is OSS, MIT, no surprise charges. Source: Zedge 76% unfavorable on Trustpilot; Panels shutdown (Dec 2025) as cautionary tale.
- **Full KLWP-grade WYSIWYG wallpaper scripting:** Rejected per R-6. Aura is curation + personalization, not an authoring tool.
- **Unsplash as built-in source:** Rejected as premature. API requires key registration, attribution enforcement, and rate limits. Better as a plugin via NX-5 (Muzei-compatible source ABI).
- **Google Photos cloud album sync:** Rejected. SAF, Photo Picker, and local folders satisfy the use case without cloud dependency.
- **Spatial audio / Dolby Atmos ringtones:** Rejected. Infrastructure not ready — spatial audio is paused during ringtone playback by default. Eclipsa Audio (Google/Samsung) is landing in AOSP but no consumer ringtone support exists.
- **AI text-to-ringtone generation:** Under consideration rather than rejected. Zedge has this as a headline feature, but it requires a server-side model API (cost/complexity). Revisit when a credible OSS or BYO-key generator emerges.
- **Desktop-to-phone wallpaper sync (Wallpaper Engine model):** Rejected as premature. Depends on L-4 (KMP) and L-5 (desktop companion). The sync protocol would add significant complexity for a small user base.
- **Items already tracked:** HEIF/AVIF, adaptive layouts, screenshot tests, AAB, localization, F-Droid, Tasker expansion, audio visualizer, shader playground, HDR wallpapers — all exist in ROADMAP.md or Roadmap_Blocked.md. Not re-added.

## Sources

**OSS competitors:**
- https://github.com/Anthonyy232/Paperize
- https://github.com/you-apps/WallYou
- https://github.com/ammargitham/WallFlow
- https://github.com/muzei/muzei
- https://github.com/Hamza417/Peristyle
- https://github.com/avinaxhroy/Vanderwaals
- https://github.com/markusfisch/ShaderEditor
- https://github.com/patzly/doodle-android
- https://github.com/althafvly/ringdroid
- https://github.com/rocksdanister/lively

**Commercial and community signals:**
- https://www.trustpilot.com/review/www.zedge.net
- https://zedge.pissedconsumer.com/review.html
- https://www.wallpaperengine.io/android/en
- https://dontkillmyapp.com
- https://play.google.com/store/apps/details?id=com.ezgood.randtunereborn
- https://alternativeto.net/software/zedge/

**Platform and standards:**
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/reference/android/app/wallpaper/WallpaperDescription
- https://developer.android.com/identity/credential-manager
- https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://kotlinlang.org/docs/whatsnew23.html
- https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- https://coil-kt.github.io/coil/upgrading_to_coil3/
- https://scientiamobile.com/av1-codec-hardware-decode-adoption/
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive

**Dependencies and security:**
- https://github.com/google/dagger/releases/tag/dagger-2.59
- https://firebase.google.com/support/releases
- https://developer.android.com/training/wearables/watch-face-push

## Open Questions

- Should ringtone shuffle use a system-level `ContentProvider` rotator or a foreground-service approach similar to `RotationTriggerService`?
- Is NASA APOD API rate limiting sufficient for the expected Aura user base, or should a client-side cache-with-daily-refresh be the default pattern?
- Should on-device style learning (MobileNetV4) be a standalone Discover feed ranker or integrated into the existing `WallpaperFeedQuality` scoring system?
- Which OEMs should be prioritized for battery optimization guidance? Samsung, Xiaomi, and OnePlus are the top 3 by complaint volume per DontKillMyApp.com.
