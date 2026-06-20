# Research — Aura

## Executive Summary

Aura is a mature (v6.33.0, 185 Kotlin source files, 98 test files, 52,704 lines of production code) open-source Android personalization app combining wallpapers, video wallpapers, ringtones, and sounds — the only FOSS project covering this breadth. Zedge (500M+ installs, 76% unfavorable on Trustpilot) is the only commercial app matching this scope, and the FOSS wallpaper space (WallYou 1k stars, Paperize 1.1k stars, Muzei 4.9k stars, Peristyle 643 stars) is entirely wallpaper-only. Aura's strongest axes are this unique combination, its privacy-forward no-ads stance, and the depth of its audit hardening (34+ audit passes since v5.2.0).

Since the last research pass, v6.33.0 shipped: Android 17 audio hardening compliance, OEM battery optimization guidance (Samsung/Xiaomi/OnePlus/Huawei/vivo/ASUS), ringtone shuffle from downloads, SoundsViewModel decomposition, Compose @Preview fixtures, AV1 hardware decode detection, and NASA APOD wallpaper source. These closed the top 7 of 8 prior research priorities.

The highest-value directions now, in priority order:
1. **SettingsScreen decomposition** — 3,407 lines, the largest file in the codebase; partially extracted (SettingsComponents.kt + DiagnosticsComponents.kt) but still needs further breakup for maintainability and testability
2. **String resource extraction** — 362 strings.xml entries vs ~340 hardcoded English strings in SettingsScreen alone; blocks localization (U-11), F-Droid community translations, and RTL audit
3. **Microphone recording for personal ringtones** — CommunityAudioRecorder infrastructure exists (AAC, 128kbps, 44.1kHz) but is not wired to the personal ringtone creation flow; Ringdroid's core feature, quick win
4. **Wikipedia POTD wallpaper source** — WallYou ships this; zero-auth API, follows the NASA APOD pattern just shipped in v6.33.0
5. **Sequential wallpaper selection** — WallFlow and Paperize offer no-repeat-until-cycle-complete; addresses repeated-wallpaper complaints from auto-rotation users
6. **Time-of-day sound profiles** — RandTune and Ringtone Scheduler offer time-based sound switching; Aura already has the scheduling infrastructure (WorkManager, SolarCalculator) but hasn't extended it to sounds
7. **Direct boot wallpaper persistence** — Doodle supports this; wallpaper appears immediately after reboot before unlock, improving perceived reliability
8. **On-device wallpaper style learning** — Vanderwaals (AGPL-3.0) proves MobileNetV4-Conv-Small is viable for taste-adaptive feeds; already tracked as P3 in ROADMAP

## Product Map

- Core workflows: browse/search/apply static wallpapers, live video wallpapers, GIF/video crops, sounds, ringtones, notification sounds, alarms; edit/crop/preview media; rotate wallpapers by scheduler or external automation; shuffle ringtones from downloads; manage favorites, downloads, collections, profiles, reports, backups, diagnostics.
- User personas: privacy-focused Android users, ad-free Zedge replacement seekers, AMOLED enthusiasts, live-wallpaper users, ringtone/sound users, community contributors, Tasker/MacroDroid automation users, F-Droid/degoogle users.
- Platforms: Android (minSdk 26, targetSdk 35, package `com.freevibe`); GitHub Releases + Obtainium primary; IzzyOnDroid near-term; F-Droid blocked by Firebase/Play Services.
- Key integrations: Wallhaven, Bing, Pexels, Pixabay, YouTube (NewPipe + yt-dlp), NASA APOD, Freesound/legacy sources, Open-Meteo, Firebase (Auth/RTDB/Storage/Functions/App Check), ML Kit segmentation, Stability AI, Room, DataStore, MediaStore, SAF, WorkManager, Glance widgets, Media3 ExoPlayer.

## Competitive Landscape

### Zedge (commercial, 500M+ installs, ~35M MAU)
- **Learn from:** AI text-to-ringtone generation (Audio AI, August 2025); creator marketplace with verified channels and revenue sharing; video ringtones (cinematic loops paired with custom audio); unified sound + wallpaper discovery.
- **Avoid:** Aggressive ads (76% unfavorable on Trustpilot, 1.8/5 PissedConsumer); credit/coin economy; unauthorized charges and billing disputes; search returning "irrelevant stupid stuff"; AI content quality flooding; background resource drain.

### Paperize (1.1k stars, GPL-3.0, v4.0.0-alpha Dec 2025)
- **Learn from:** v4.0.0-alpha adds live wallpaper mode for animated rotation transitions; fully offline-first design (zero permissions variant); AVIF/HEIC/HEIF/SVG/TIFF format support; folder-based album auto-import; per-wallpaper preset effects (brightness, blur, vignette applied pre-set). Dark/light theme-aware wallpaper switching most-requested (#516).
- **Avoid:** v4 alpha is buggy (community reports regressions vs v3.2.1); minSdk 31 excludes older devices; no online sources limits discovery.

### WallYou (1k stars, GPL-3.0, v15.2 Jun 2026)
- **Learn from:** Privacy-first multi-source aggregation including Wikipedia POTD and Wallpaper Cave (v15.0); Unsplash bot-blocking bypass; Zedge scraping as source; fast release cadence (15 releases in 2026); Weblate for community translations. Top requests: staggered grid with custom columns (#280), Reddit/Lemmy (#267), sub-15min intervals (#229).
- **Avoid:** Auto-changer reliability bugs on LineageOS/Samsung (#230, #259, #266); Material You color integration gap (#277); limited to wallpapers only.

### Muzei (4.9k stars, Apache-2.0, canonical plugin architecture)
- **Learn from:** Open provider/source plugin API enabling massive third-party ecosystem (Unsplash, Pixiv, Immich, Reddit, NASA APOD, Bing, NatGeo plugins); blur/dim effect that recedes artwork behind icons with double-tap reveal; MuzeiArtDocumentsProvider for file-browsing sources. Top requests: home/lock separation (#794), music-reactive wallpaper (#128), dimming affecting system theme colors (#797).
- **Avoid:** 2-year release cadence gaps; art-only positioning limits broad appeal; Material You color not persistent (#836); black screen flicker on wallpaper switch (#831).

### Wallpaper Engine (commercial, v2.8.8 Jun 2026)
- **Learn from:** Video wallpaper playlists with time-of-day scheduling; interactive SceneScript wallpapers; v2.8 dynamic model generation + HDR video (DX11 backend); quality presets + per-wallpaper FPS limits; PC-to-mobile wireless sync via 4-digit PIN. The gold standard for live wallpapers.
- **Avoid:** Desktop-dependency model (Steam Workshop requires PC purchase); heavy 3D effects drain battery; proprietary Scene format; no standalone Android content library.

### Peristyle (643 stars, Apache-2.0, v9.7.0 Jun 2026 — very active)
- **Learn from:** External intent API (`app.peristyle.START_AUTO_WALLPAPER_SERVICE`) for Tasker/MacroDroid integration; glassmorphic UI with real-time blur effects and caustic shadows; per-wallpaper tagging system; on-the-fly image compression before applying; zero internet permissions model. 118 releases — fastest cadence in the FOSS wallpaper space.
- **Avoid:** Samsung Z Flip auto-wallpaper desync (#22); day/night mode wallpaper switching not supported (#98).

### Vanderwaals (29 stars, AGPL-3.0, v4.5.0 2026)
- **Learn from:** On-device MobileNetV4-Conv-Small learns visual preferences via 1280-dimensional embeddings with EMA (0.30 learning rate for new users, 0.15 for experienced); epsilon-greedy exploration (20% initial, decays to 5% over 100 interactions); scoring: 70% deep visual features + 20% LAB color palette + 10% category/temporal/exploration; "upload one wallpaper, get 100+ similar" instant personalization; Daily Playlist (15 pre-selected); personalization insights dashboard; quantized embeddings reduced downloads from 60MB to 6MB.
- **Avoid:** Tiny community; limited library (11,400 wallpapers); glassmorphism via pre-rendered slices (not real-time).

### RandTune (niche, Google Play)
- **Learn from:** Ringtone playlist randomization per incoming call — truly unique; per-contact random playlists; time-period scheduling (4h/8h/12h/24h); notification sound randomization (premium).
- **Avoid:** Overrides contact-specific ringtones when active; mixed reliability reviews; no content library.

### Doodle (835 stars, GPL-3.0, v6.0.0 Oct 2024)
- **Learn from:** SVG-based vector rendering for infinite-resolution wallpapers; power-efficient parallax on page swipe only (not sensor-based); direct boot support (wallpaper active immediately after reboot); extremely lightweight footprint.
- **Avoid:** Java-only codebase; limited to pre-bundled designs; no online sources.

## Security, Privacy, and Reliability

- **SHIPPED — Android 17 background audio hardening (v6.33.0):** VideoWallpaperService now sets non-media AudioAttributes and deselects audio tracks after prepare. `AudioPlaybackService` (foreground service with MediaSession) should comply. Remaining validation: device logcat/dumpsys checks under API 37 (tracked in Roadmap_Blocked.md).

- **SHIPPED — OEM battery optimization guidance (v6.33.0):** `OemBatteryGuidance.kt` detects Samsung, Xiaomi, OnePlus/OPPO, Huawei/Honor, vivo, and ASUS with manufacturer-specific Settings deep-links and user-facing instructions.

- **Credential Manager for NX-7:** Google Sign-In (`play-services-auth`) is deprecated and being removed from Play Services. NX-7 (Favorites sync) should use `androidx.credentials` Credential Manager instead. This updates NX-7's implementation approach, not a new item.

- **Room 2.x maintenance mode:** Room 3.0 shipped (March 2026) with a complete rewrite (KSP-only, coroutines-only, new package `androidx.room3`). Room 2.x enters maintenance. Aura should stay on 2.7.2 until after N-1, then evaluate Room 3.0 as a future migration.

- **Compose stability annotations:** 6 `@Immutable`/`@Stable` annotations across 3 files vs 185 source files. Strong Skipping (stable in Compose 1.10+) reduces urgency but hot-path data classes (`Wallpaper`, `Sound`, `VideoWallpaperItem` in `Models.kt`) still benefit.

- **AV1 risk register (updated):** Risk register said "AV1 hardware decode <10% install base." Research shows Android 13+ mandates hardware AV1 decode for new device certification. Aura v6.33.0 shipped `Av1CodecSupport` gating on `MediaCodecList` hardware availability — mitigation complete.

- **SettingsScreen accessibility gap:** SettingsScreen.kt (3,407 lines) has only 5 `contentDescription` annotations vs 340 hardcoded user-facing strings. SettingsComponents.kt (514 lines) has 9. The settings surface is the weakest screen for TalkBack/accessibility coverage.

- **NewPipe Extractor SABR risk:** Aura is on v0.24.8; latest stable is 0.26.3+. SABR enforcement continues tightening. The existing risk register row covers this; N-1 toolchain bump is the gate.

## Architecture Assessment

- **SettingsScreen.kt is the largest file (3,407 lines):** Partial extraction has moved shared primitives to SettingsComponents.kt (514 lines) and diagnostics to DiagnosticsComponents.kt (604 lines), but the main file remains a monolith. Further decomposition into section-level composables (Wallpaper settings, Sound settings, Rotation settings, Community settings, About/Legal) would improve testability and preview coverage.

- **SoundsViewModel partially decomposed (1,423 lines):** v6.33.0 extracted community operations to `SoundCommunityActions.kt`. Remaining: YouTube browse state, playback management, tab management, and search still in one ViewModel. Further split candidates: `SoundBrowseState` (YouTube/tab management), `SoundPlaybackManager` (player lifecycle).

- **String extraction needed for localization:** `values/strings.xml` has 362 entries, but SettingsScreen alone contains ~340 hardcoded English strings not in resources. Across all UI files, `stringResource()` is used only 37 times. This blocks U-11 (localization) and F-Droid community translations.

- **Zero adaptive layout APIs:** No `WindowSizeClass`, `NavigationSuiteScaffold`, or `ListDetailPaneScaffold` usage (confirmed by grep). L-10 covers this; remains N-1-gated.

- **CommunityAudioRecorder exists but is unused for personal ringtones:** The recording infrastructure (AAC, 128kbps, 44.1kHz, max duration cap, proper lifecycle) is wired only for community uploads. Reusing it for a "record your own ringtone" personal flow requires only a UX entry point and routing to SoundEditorScreen.

- **AgslEffectPipeline scaffolded but incomplete:** `AgslEffectPipeline.kt` has the API surface (`isSupported`, `apply()`, `AgslEffect` enum) with planned effects (DEPTH_SHADE, SUBJECT_TINT_PASS, SHAPE_BACKDROP, WEATHER_FADE) listed in comments. Concrete shader programs have not been added yet. Blocked behind N-1 for Compose BOM alignment.

- **Good structured concurrency discipline:** 198 CancellationException/rethrowIfCancelled usages across 54 files; `CancellationUtils.kt` provides the shared `rethrowIfCancelled()` helper. All 20 screens use `collectAsStateWithLifecycle` (zero legacy `collectAsState`). 143 `Locale.ROOT` usages across 62 files.

- **AGP 9 migration notes for N-1:** R class fields are non-final — any `when`/`switch` on resource IDs must convert to `if-else`. AGP 9 has built-in Kotlin — the `kotlin-android` plugin causes a build error if applied alongside AGP 9 (remove it, or opt out with `android.builtInKotlin=false`). Hilt 2.59 requires AGP 9 and Gradle 9.1. `BaseExtension` removed; use `androidComponents.onVariants()`. `targetSdk` defaults to `compileSdk` (was `minSdk`). `resValues` and `shaders` build features disabled by default.

- **Coil 3.x migration (2.7.0 → 3.5.0):** Package rename (`coil` → `coil3`), artifact rename (`coil-base` → `coil-core`), network loading no longer included by default (add `coil3-network-okhttp`), `Drawable` replaced by `Image` interface, `android.net.Uri` replaced by `coil3.Uri`, `Coil` singleton renamed to `SingletonImageLoader`, default decode size changed to `Size.ORIGINAL`, max bitmap capped at 4096x4096. High-effort migration but enables Compose Multiplatform support (L-4).

- **targetSdk 36 deadline:** Google Play requires targetSdk 36 by August 31, 2026. Aura is on targetSdk 35. This is a forcing function for N-1 — compileSdk 36 requires AGP 8.9+ minimum.

- **Compose 1.11 / Material 3 1.4.0:** `@PreviewWrapper` annotation for automatic theme/layout in previews. Experimental Grid Layout (2D tracks/gaps), FlexBox (wrap/alignment), Styles API (state-based styling). Material 3 Expressive adds 15 updated components with shape morphing and motion physics. Compose 1.12 will require compileSdk 37 and AGP 9.

- **Media3 1.10.1 Compose Material3 module:** New `media3-ui-compose-material3` provides `Player`, `ProgressSlider`, `PlayPauseButton`, `TimeText` composables. Could replace Aura's custom waveform/progress UI for sound previews. Also: `player.mute()`/`unmute()` promoted to stable; `experimentalSetDynamicSchedulingEnabled()` reduces CPU wake-ups.

## Rejected Ideas

- **Zedge-style ads, credits, streaks, subscription pressure:** Rejected. Charter is OSS, MIT, no surprise charges. Source: Zedge 76% unfavorable on Trustpilot; Panels shutdown (Dec 2025) as cautionary tale.
- **Full KLWP-grade WYSIWYG wallpaper scripting:** Rejected per R-6. Aura is curation + personalization, not an authoring tool.
- **Unsplash as built-in source:** Rejected as premature. API requires key registration, attribution enforcement, and rate limits. Better as a plugin via NX-5. WallYou ships it with a bot-blocking bypass — brittle.
- **Google Photos cloud album sync:** Rejected. SAF, Photo Picker, and local folders satisfy the use case without cloud dependency.
- **Spatial audio / Dolby Atmos ringtones:** Rejected. Infrastructure not ready — spatial audio is paused during ringtone playback by default. Eclipsa Audio (Google/Samsung) is landing in AOSP but no consumer ringtone support exists.
- **AI text-to-ringtone generation:** Under consideration (U-4) rather than rejected. Zedge shipped Audio AI in August 2025 as a headline feature, but it requires a server-side model API (cost/complexity). Revisit when a credible OSS or BYO-key generator emerges.
- **Desktop-to-phone wallpaper sync:** Rejected as premature. Depends on L-4 (KMP) and L-5. Wallpaper Engine's PC pairing model proves demand but the sync protocol adds significant complexity.
- **Per-contact notification sound assignment:** Rejected. Android's NotificationManager per-conversation NotificationChannel is per-app; a personalization app cannot set system-wide per-contact notification sounds. Only the messaging app itself can do this.
- **Sleep sound / ambient mixing with timer:** Rejected for now. Noice (FOSS, 35 ambient sounds, sleep timer, alarm integration) does this well and is purpose-built. Aura attempting this would duplicate a well-served niche without adding the FOSS wallpaper/sound combination value. Revisit if strong user signal.
- **Zedge scraping as wallpaper source:** Rejected. Brittle (WallYou's implementation requires constant updates); legal risk (ToS violation); content quality unreliable. Aura already aggregates better-quality sources with proper APIs.
- **Wallpaper Cave as source:** Rejected for the same reasons as Zedge scraping — no public API, scraping-only access, uncertain ToS compliance. Better as a future NX-5 community plugin.
- **Items already tracked:** HEIF/AVIF, adaptive layouts, screenshot tests, AAB, localization, F-Droid, Tasker expansion, audio visualizer, shader playground, HDR wallpapers, on-device style learning — all exist in ROADMAP.md or Roadmap_Blocked.md. Not re-added.

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
- https://github.com/b-lam/Resplash

**Commercial and community signals:**
- https://www.trustpilot.com/review/www.zedge.net
- https://zedge.pissedconsumer.com/review.html
- https://www.wallpaperengine.io/android/en
- https://dontkillmyapp.com
- https://play.google.com/store/apps/details?id=com.ezgood.randtunereborn
- https://alternativeto.net/software/zedge/
- https://blog.zedge.net/best-ai-ringtone-maker-customize-ringtones/
- https://diffuse.app/
- https://dreampixelstudio.app/blog/use-live-wallpapers-on-android-without-draining-battery

**Platform and standards:**
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/about/versions/17/features
- https://developer.android.com/reference/android/app/wallpaper/WallpaperDescription
- https://developer.android.com/identity/credential-manager
- https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://developer.android.com/develop/ui/views/graphics/agsl
- https://developer.android.com/about/versions/16/features
- https://scientiamobile.com/av1-codec-hardware-decode-adoption/
- https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive

**Dependencies and security:**
- https://github.com/google/dagger/releases/tag/dagger-2.59
- https://firebase.google.com/support/releases
- https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html
- https://coil-kt.github.io/coil/upgrading_to_coil3/
- https://kotlinlang.org/docs/whatsnew23.html
- https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://developer.android.com/jetpack/androidx/releases/glance

**Ringtone/sound ecosystem:**
- https://f-droid.org/packages/com.vicolo.chrono/
- https://f-droid.org/en/packages/com.github.ashutoshgngwr.noice/
- https://github.com/AmjdAlhashede/RingtoneSmartKit
- https://ringtone-scheduler.en.uptodown.com/android

## Open Questions

- Should on-device style learning (MobileNetV4) be a standalone Discover feed ranker or integrated into the existing `WallpaperFeedQuality` scoring system?
- What is the right UX entry point for microphone recording — a FAB on SoundsScreen, a "Record" tab, or a button in SoundEditorScreen?
- Should sequential wallpaper selection track history in Room (persistent) or in-memory (per-session only)?
- For time-of-day sound profiles, should profiles be user-created (flexible) or preset (quiet/work/fun — simpler)?
