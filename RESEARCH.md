# Research — Aura

## Executive Summary

Aura is a mature open-source Android personalization app for wallpapers, video wallpapers, sounds, ringtone assignment, community uploads, scheduler automation, diagnostics, and signed GitHub/Obtainium distribution. Its strongest current shape is a privacy-forward, no-ads Android alternative to Zedge/Backdrops/Wallpaper Engine that already has broad source coverage and unusually strong policy tooling. The highest-value direction is not more surface catalog breadth; it is making remote/community data, media metadata, release evidence, and large-screen/accessibility behavior feel as reliable as the local-first core. Priority opportunities: 1. hydrate Community Favorites from remote upload metadata instead of local cache only; 2. stop using YouTube thumbnail size as video orientation metadata; 3. finish real-screen accessibility, AAB, HEIF/AVIF, screenshot, and adaptive-layout work already in the roadmap; 4. split the Sounds browse/playback stack like the existing Settings and Wallpapers refactor tracks; 5. use newer OkHttp request metadata hooks to make source diagnostics more precise after the dependency bump.

## Product Map

- Core workflows: browse/search/apply static wallpapers, live video wallpapers, GIF/video crops, sounds, ringtones, notification sounds, and alarms; edit/crop/preview media; rotate wallpapers by scheduler or external automation; manage favorites, downloads, collections, profiles, reports, backups, and diagnostics.
- User personas: privacy-focused Android personalization users, ad-free Zedge replacement seekers, local/offline wallpaper curators, live-wallpaper and video-loop users, ringtone/sound users, community contributors, and maintainers preparing store/F-Droid/Izzy release channels.
- Platforms and distribution: Android app, minSdk 26, targetSdk 35, package `com.freevibe`; GitHub Releases and Obtainium are primary; Play, Izzy, and F-Droid readiness are partially tracked through policy docs and roadmap items.
- Key integrations and data flows: Wallhaven, Bing, Pexels, Pixabay, YouTube via NewPipe plus yt-dlp, Freesound/Audius/ccMixter/Reddit/SoundCloud surfaces, Open-Meteo, Firebase Auth/RTDB/Storage/Functions/App Check, ML Kit segmentation, Stability BYO generation, Room, DataStore, MediaStore, SAF, WorkManager, Glance widgets, and Media3 playback.

## Competitive Landscape

- Zedge: Does broad unified discovery across wallpapers, live wallpapers, ringtones, notifications, and AI tools well. Aura should learn from unified search and content breadth, while avoiding ads, credits, subscription pressure, promotional push, and tracking-heavy trust erosion.
- Backdrops and Panels: Show the value of curated collections, creator presentation, and daily freshness. Aura should learn from curation and attribution quality, while avoiding subscription-first wallpaper gating and privacy backlash.
- Wallpaper Engine and UndeadWallpaper: Set expectations for video wallpaper playlists, local video/GIF handling, per-wallpaper controls, and media-engine stability. Aura should learn from explicit media metadata and playback controls, while avoiding desktop-only assumptions and battery-heavy effects.
- Paperize and Peristyle: Establish local/offline folder workflows, home/lock separation, simple rotation, and clear image-format expectations. Aura should learn from local-library depth and format clarity, while preserving its richer remote/community scope.
- WallYou and WallFlow: Show FOSS Material 3 source aggregation, saved searches, tablet/wide-screen awareness, and lightweight browsing. Aura should learn from tablet posture and source clarity, while avoiding stale single-source or scheduler claims without device evidence.
- Muzei: Provides the mature Android precedent for a live-wallpaper source/provider API. Aura should learn from versioned extension boundaries, while avoiding a mode where the app becomes only a passive wallpaper provider.

## Security, Privacy, and Reliability

- Verified risk: `app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersViewModel.kt` resolves top-voted community wallpaper IDs through `cacheManager.getByIds(...)`; `VoteRepository.getTopVotedIds(...)` returns only IDs/counts; `WallpapersScreen.kt` only renders the top-voted row when resolved local wallpaper objects exist. Community Favorites can disappear for valid remote-only uploads.
- Verified risk: `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt` still uses YouTube thumbnail dimensions as a proxy for video orientation. `app/src/main/java/com/freevibe/service/VideoWallpaperStorage.kt` already probes local video width, height, duration, MIME, and orientation-style metadata, so remote-video browse behavior is weaker than local media handling.
- Verified risk: real Aura accessibility coverage is still incomplete. The existing roadmap already owns replacing synthetic accessibility gates with checks against real Wallpapers, Sounds, Settings, editor, and detail flows; this remains higher value than another synthetic control test.
- Verified risk: format support policy is inconsistent across flows. The existing roadmap already owns HEIF/AVIF acceptance, rejection copy, and metadata-scrub tests because `MediaIngestion.kt`, `AutoWallpaperWorker.kt`, and `WallpaperUploadRepository.kt` expose different effective format rules.
- Verified guardrail: `.github/workflows/verify.yml` now runs manifest consistency, media ingestion, accessibility release-gate checks, dependency review, lint, tests, assemble, and native compliance. New research should not re-add the older "wire manifest consistency into CI" item as if it were still absent.
- Needs live validation: App Check enforcement, callable rollout behavior, background-work evidence, AAB dry-run artifacts, low-RAM bitmap behavior, and large-screen behavior still require owner project/device evidence rather than static repo inspection.
- Likely low current fit: Android 17 local-network permission changes do not map to Aura's current public integration model; contact privacy is relevant only to existing sharing/contact roadmap work, not a new urgent item.

## Architecture Assessment

- `SettingsScreen.kt`, `WallpapersScreen.kt`, `SoundsScreen.kt`, `SoundsViewModel.kt`, `WallpaperDetailScreen.kt`, `WallpapersViewModel.kt`, `VideoWallpapersScreen.kt`, and `FreeVibeRoot.kt` are the largest user-facing files. Existing roadmap items cover Settings and Wallpapers ViewModel decomposition; Sounds browse/playback/upload/YouTube/community state still needs an equivalent split.
- `SelectedContentHolder.kt` persists selected wallpaper and sound state across process death, but intentionally keeps the wallpaper pager list in memory only. Existing NX-4/replacement-state roadmap work covers this; do not add a duplicate.
- No Compose `@Preview` fixtures were found and no Material3 adaptive APIs were found in source. Existing screenshot, route-fixture, and adaptive-layout items remain the right implementation path before aggressive UI polish refactors.
- Source diagnostics are already visible through `SourceMetrics`, but many OkHttp requests are built across repositories and services without a shared typed request-purpose tag. Newer OkHttp interceptor/builder changes create a low-risk follow-up after Aura moves beyond its current 5.3.2 dependency.
- Documentation hygiene remains important, but the active roadmap already contains incomplete-only ROADMAP cleanup and validator work. New research should append only implementation-ready deltas and avoid another broad documentation cleanup item.

## Rejected Ideas

- Zedge-style ads, credits, streaks, reward prompts, or subscription pressure: rejected because they contradict Aura's no-ads/no-tracking trust posture.
- Panels-style account-first paid wallpaper feed: rejected because curation is useful, but the commercial model and privacy perception are a poor fit.
- Core Unsplash or scraped Zedge ingestion: rejected for policy, licensing, and trust risk; any experimental provider should live behind the future source/plugin boundary.
- Full Wallpaper Engine desktop parity or KLWP-grade scripting: rejected as too broad for the Android-first product; media metadata, playlists, and scoped presets are better fits.
- Bulk YouTube download expansion: rejected because Aura's current YouTube surface is intentionally constrained by legal-mode and provider-control work.
- Google Photos cloud album sync: rejected because SAF, Photo Picker, local folders, and backup/export flows satisfy the use case without adding account-bound cloud dependency.
- New roadmap items for Play AAB, HEIF/AVIF, synthetic accessibility replacement, screenshot fixtures, adaptive layouts, or ROADMAP hygiene: rejected as duplicates of existing active roadmap entries.

## Sources

**OSS and adjacent projects:**
- https://github.com/you-apps/WallYou
- https://github.com/Anthonyy232/Paperize
- https://f-droid.org/en/packages/com.anthonyla.paperize/
- https://apt.izzysoft.de/ftp/repo/fdroid/index/apk/app.simple.peri
- https://github.com/ammargitham/WallFlow
- https://github.com/muzei/muzei
- https://api.muzei.co/reference/com.google.android.apps.muzei.api.provider/index.html
- https://github.com/maocide/UndeadWallpaper
- https://github.com/AlynxZhou/alynx-live-wallpaper

**Commercial and community signals:**
- https://play.google.com/store/apps/details?id=net.zedge.android
- https://help.zedge.net/hc/en-us/articles/360024313191-ZEDGE-for-Android-FAQ
- https://play.google.com/store/apps/details?id=com.backdrops.wallpapers
- https://play.google.com/store/apps/details?id=io.wallpaperengine.weclient
- https://www.wallpaperengine.io/
- https://www.theverge.com/2024/9/24/24253023/mkbhd-panels-wallpaper-app-response-criticism
- https://www.reddit.com/r/fossdroid/comments/1fym2hz/open_source_wallpaper_changer_from_internal/

**Platform and standards:**
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/guide/app-bundle
- https://developer.android.com/training/testing/ui-tests/screenshot
- https://developer.android.com/develop/ui/compose/accessibility/testing
- https://developer.android.com/ndk/reference/group/image-decoder
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaMetadataRetriever.java
- https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html

**Dependencies and security:**
- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/jetpack/androidx/releases/work
- https://coil-kt.github.io/coil/changelog/
- https://square.github.io/okhttp/changelogs/changelog/
- https://firebase.google.com/support/release-notes/admin/node
- https://www.sonarsource.com/blog/ytdlnis-argument-injection-rce

## Open Questions

- Which physical devices and Android API levels should be treated as release-blocking for accessibility, scheduler, large-screen, and low-memory evidence?
- Should Play readiness target a private internal-app-sharing AAB first, or a complete public Play launch packet?
- Should HEIF/AVIF be accepted everywhere through safe transcode, or intentionally rejected from community upload while remaining local-only?
- Which Firebase path should be authoritative for hydrating top-voted community wallpaper metadata when the local Room cache misses?
- Should the Sounds decomposition land before or after the Media3 1.10 playback-widget migration already represented in the roadmap?
