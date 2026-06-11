# Research -- Aura

Updated 2026-06-10 (fifth pass: Zedge Android 9.24.2 on-device UI capture, official Zedge web/support review, local-first anti-goal filter, and two consecutive no-new-item Zedge parity passes). Supersedes the earlier 2026-06-10 dependency pass; still-valid conclusions retained and compressed.

## Executive Summary

Aura is a mature open-source Android personalization app (v6.31.1, 161 Kotlin files, 16+ screens) combining multi-source wallpapers, video wallpapers, YouTube-powered sounds, community uploads via Firebase callables, ML Kit parallax, weather effects, and a sound editor. Three production-critical findings dominate this pass:

1. **Reddit's unauthenticated .json endpoints returned 403 on 2026-05-30** -- confirmed dead. Reddit also closed OAuth access to new registrations since November 2025. The Reddit source (7 wallpaper subs, 4 video subs, Wallpaper of the Day) is fully broken and must be decommissioned or replaced with an authenticated fallback.
2. **The yt-dlp JavaScript runtime requirement** (introduced in yt-dlp 2025.11.12, which Aura ships via youtubedl-android 0.18.1) means YouTube extraction now depends on QuickJS being bundled. Aura's bundled copy includes QuickJS, so current builds work. But the ecosystem is fragile -- Seal issue #2413 shows the failure mode when the JS runtime is missing. A yt-dlp runtime self-update with rollback is the ecosystem's survival strategy.
3. **N-1 toolchain gap is now critical**: AGP 9.1.1 is stable (April 2026), Kotlin 2.4.0 shipped June 2026, Gradle 9.5.1 is current, Compose BOM 2026.05.00 is stable, Room 2.7.1 and Navigation 2.9.6 are available. Aura is pinned to AGP 8.7.3 / Kotlin 2.1.0 / Gradle 8.12 -- three major versions behind on AGP and Gradle, four minor versions behind on Kotlin. This blocks targetSdk 37, Compose adaptive layouts, KSP2, type-safe navigation, and Coil 3.

Top opportunities this pass, in priority order:

1. **Reddit source decommission** -- broken since May 30, affects Wallpaper of the Day, 7 wallpaper subs, 4 video subs. Replace WOTD with a non-Reddit source; disable Reddit tabs; clean up dead code paths.
2. **N-1 toolchain upgrade** -- AGP 9.1+, Kotlin 2.3+, Gradle 9.x, Compose BOM 2026.05, unblocks targetSdk 37 and all dependent items.
3. **Vulnerable transitives from youtubedl-android** -- jackson-databind 2.11.1 and commons-io 2.5 ship with CVEs; one-file Gradle constraint fix.
4. **Gradle wrapper 8.12 CVE-2025-27148** -- one-line bump to 8.12.1+ or jump directly to Gradle 9.x with N-1.
5. **GitHub Actions SHA pinning** -- post-tj-actions consensus; Dependabot github-actions ecosystem already configured.
6. **Coil 2.7 to Coil 3.x migration** -- 25-40% runtime perf improvement, Compose Multiplatform ready, restartable/skippable composables.
7. **OkHttp 4.12 to OkHttp 5.x migration** -- separate Android artifact, DNS-over-HTTPS stable, Java 9 modules.
8. **Media3 1.5 to 1.10** -- Material3 playback widgets, Dolby Vision Profile 10, dynamic scheduling.
9. **yt-dlp runtime self-update with rollback** -- Seal proves the pattern; protects against extraction breakage.
10. **Local-folder wallpaper rotation source** -- Paperize's core feature, WallFlow's top request; Aura has zero SAF folder infrastructure.
11. **Zedge local-first parity guardrails** -- default UI must keep AI generation, accounts, credits, premium gates, reward ads, and offer notifications out of the primary path.
12. **Unified local Library hub** -- account-free replacement for My Zedge: Downloads, Favorites, Collections, Local Imports, Recent Activity, backup/restore.
13. **Universal on-device search** -- one search entry across wallpapers, videos, sounds, collections, downloads, favorites, and local files.
14. **24H local wallpaper packs** -- Zedge has a dedicated 24H tab; Aura has the primitives but no pack/timeline UX.
15. **Sticker/text overlays and local theme packs** -- lower-priority polish for Zedge-style personalization without accounts or remote stores.

## Product Map

- **Core workflows**: Browse wallpapers (Wallhaven/Pexels/Pixabay/Bing; Reddit dead) > preview > apply home/lock/dual. Browse sounds (YouTube via NewPipe+yt-dlp, community) > preview > trim/fade > apply as ringtone/notification/alarm. Video wallpapers > crop > live wallpaper. Community upload/vote/report/follow/block via Firebase callables. Auto-rotation scheduler.
- **Personas**: ad-free Zedge refugee; AMOLED owner; automation power user; privacy-focused user; displaced Panels/WallFlow users.
- **Distribution**: GitHub Releases + Obtainium; IzzyOnDroid near-term; F-Droid blocked on FOSS flavor.
- **Key integrations**: Firebase BoM 34.13.0, youtubedl-android 0.18.1 (bundles yt-dlp 2025.11.12 + QuickJS), NewPipe Extractor 0.24.8 (stale; 0.26.3 available), ML Kit subject segmentation, Open-Meteo, Stability AI BYO-key.

## Competitive Landscape

### UndeadWallpaper (new)
- Active FOSS video live wallpaper app (maocide/UndeadWallpaper, 4.92 stars). Ships playlist/shuffle/loop, per-video zoom/offset/rotation/speed/volume, smart start times on unlock. Direct competitor to Aura's video wallpaper surface. Learn: playlist model, per-video behavior profiles, smart unlock start. Avoid: nothing -- it validates the P2 video playlist roadmap item.

### Magic Portrait / depth-effect niche (carried)
- Google Magic Portrait is Pixel-exclusive; MagicFX Wallpaper is $9.99; no FOSS implementation. Aura owns prerequisites (subject segmentation, weather effects, dual wallpaper). "MagicFX but FOSS" remains a strong headline.

### Panels / WallApp (carried)
- Panels shut down 2025-12-31; open-sourced as `panels-art/WallApp` (Apache-2.0, KMP). Learn: artist-curated drops with attribution. Avoid: credits/pricing model.

### WallFlow (update)
- Effectively unmaintained. Reddit source broken (#113), search broken (#111), auto-wallpaper broken on Android 16 (#110). Orphaned Wallhaven power users are Aura's natural landing spot.

### Paperize (update)
- v4 regression sentiment continues. Folder-rotation core remains the feature Aura lacks. Dark/light wallpapers (#516) -- Aura already ships this.

### Zedge (commercial, update)
- JustUseApp safety score 33.4/100 (167k reviews). Core complaints: un-closeable ads, credits expiring, $5 minimum spend, subscription gates. Aura's "preview > one-tap set, zero interstitials" flow directly answers these. Sound-pack culture active but homeless.

### Zedge local-first parity pass (2026-06-10)

Scope: Zedge's public Android app, official website, Play listing, Help Center, press/blog/search-visible pages, screenshots/case studies, and competitor comparisons were reviewed. Public Zedge source code, CLI, serve UI, and product roadmap were not discoverable; the current app GUI and official docs are the verifiable surfaces. Aura was verified against repo docs/code/searches plus an installed Aura 6.21.0 UX snapshot; repo docs identify current code as v6.31.1 / versionCode 112.

Decision filter: the target Aura direction is local/device-first. Zedge AI generation, accounts, followers, creator profiles, uploads-as-social, credits, subscriptions, premium gates, reward ads, daily offers, and offer notifications are anti-goals. Local parity means preserving the user value without requiring accounts, cloud services, remote generation, ads, or paid credits.

On-device Zedge 9.24.2 menu/layout receipts:
- First-run prompt: "Get notified" with "Allow Notifications" and "Not now"; body promises new content and offers.
- Bottom navigation: Wallpapers, Ringtones, AI Generator, My Zedge.
- Top search: "Search Zedge"; visible credit wallet showing 10 credits.
- Wallpapers secondary tabs: Wallpapers, Video wallpapers, Dual, 24H Wallpapers.
- Wallpaper quick links: Popular, Newest, Categories, Premium; sections include Featured, Popular Collections, Popular, and See More.
- Video wallpapers: Popular, Newest, Categories; Featured, Popular Collections, Popular; badges such as Live Wallpaper.
- Dual: Popular, Newest, Categories; featured topic chips; items labeled Lock Screen and Dual Wallpaper with credit prices.
- 24H Wallpapers: Popular, Newest, Categories; featured topic chips; items labeled 24H Wallpaper with credit prices.
- Ringtones: Ringtones and Notification sounds; Popular, Newest, Categories, Premium; inline play buttons and durations.
- AI Generator: Create, Community, My Creations; Choose Photo, Write Idea, Create Ringtones with AI, Start Creating.
- My Zedge: avatar/profile area, follower/following counts, Log in, Recent Activity, Uploads, Downloads, Favorites, Collections, notifications, settings, credits.
- Credits screen: Daily Reward, Watch ads/get credits, Daily Offers, Game Zone, Credit Station, paid credit tiers.

Parity matrix:

| Zedge surface / behavior | Aura verification | Classification | Roadmap action |
|--------------------------|-------------------|----------------|----------------|
| Bottom nav centered on Wallpapers, Ringtones, AI Generator, My Zedge | Aura installed snapshot shows Wallpapers, Videos, Sounds, Favorites, Settings plus a prominent Generate entry on the home surface | Partial / anti-parity | Add default local-first mode that hides primary Generate/account/cloud prompts; replace My Zedge with local Library |
| Universal "Search Zedge" entry | Aura has wallpaper/sound search surfaces but no single search across all content types and saved/local content | Partial | P1 universal on-device search |
| Wallpapers / Video wallpapers / Dual / 24H secondary tabs | Aura has wallpapers, video wallpapers, dual apply, rotation, time-of-day/weather effects | Partial | P1 24H local packs; P2 consistent browse rails; label dual packs clearly |
| Popular / Newest / Categories / Premium quick links | Aura has provider filters, chips, collections, and curated areas, but not a consistent browse skeleton across wallpapers/videos/sounds | Partial | P2 Popular/Newest/Categories/Collections rails; omit Premium |
| Featured / Popular Collections / Popular rails with See More | Aura has collections and curated feeds; consistency varies by media type | Partial | P2 shared browse model/components |
| Ringtones and Notification sounds with play/duration list rows | Aura has sounds, ringtone/notification/alarm application, contact ringtones, and sound editing | Full for capability, partial for Zedge-like browse UX | P2 consistent sound rails, no account or premium |
| My Zedge content hub: Downloads, Favorites, Collections, Recent Activity | Aura has favorites/downloads/collections separately | Partial | P1 unified local Library hub |
| Account-backed sync/library continuity | Aura has local/export concepts, not full library transfer | Missing as local equivalent | P1 account-free whole-library backup and device transfer |
| Permission disclosure and notification prompt | Aura has privacy/provider docs and diagnostics but no permission matrix tied to first use | Partial | P2 permission/source transparency panel |
| 24H Wallpapers as ready-made daypart packs | Aura has scheduler primitives but no pack editor/timeline preview | Missing/partial | P1 24H local wallpaper packs |
| Wallpaper filters/stickers | Aura has editing/crop/filter-style primitives, smart crop, blur/color controls; no local sticker/text layer editor found | Partial/missing | P3 sticker and text overlays |
| Icon packs/widgets/theme personalization | Aura has app/widget surfaces but no portable local theme pack recipe | Partial/missing | P3 local theme pack |
| AI Generator / AI community / My Creations | Aura has optional external generation/community features | Rejected anti-parity | Move out of primary local-first UX; keep only optional explicit external-service lane |
| Credits, daily rewards, paid tiers, reward ads | Aura charter rejects ads/coins/subscriptions | Rejected anti-parity | Do not implement |
| Login, followers, creator profile, social uploads | Aura includes community concepts but user target rejects accounts/services | Rejected anti-parity | Replace with local Library/export/import; no account requirement |
| Push offer notifications | Aura should notify only for opted-in feature work | Rejected anti-parity | Do not add offer/news prompts |
| Zedge scraping/import source | Legal/ToS risk | Rejected anti-parity | Do not add |

Adjacent pass exhaustion:
- Pass 1 found app layout, tabs, credits, My Zedge, AI Generator, quick links, collections, and premium gates.
- Pass 2 reclassified accounts/AI/credits/ads as anti-goals after the local-first constraint and found local backup/transfer plus permission/source transparency as replacements.
- Pass 3 found user-review evidence that excessive AI/search/navigation changes are trust risks, not desired parity.
- Pass 4 found app icons/widgets/home-screen personalization as a local pack opportunity.
- Pass 5 found Play-listing filters/stickers and selected-interval rotation references; Aura already has filters/rotation partially, leaving sticker/text overlays and 24H pack UX.
- Passes 6 and 7 found no new local-first parity items beyond filters/stickers/rotation/parallax/icons/widgets, satisfying the two-consecutive-pass stop condition.

### Alynx Live Wallpaper
- ExoPlayer + custom OpenGL ES renderer for center-crop video. Learn: GL renderer architecture relevant to NX-1.

### Doodle Live Wallpapers (carried)
- Battery-frugal: renders only on state change. Relevant constraint for NX-1 GL engine.

## Security, Privacy, and Reliability

### New risks found (2026-06-10 pass 4)

- **yt-dlp JS-runtime fragility (Verified)**: yt-dlp 2025.11.12 deprecated YouTube extraction without an external JS runtime. QuickJS is bundled in youtubedl-android 0.18.1, so current builds work. But Seal issue #2413 shows the failure mode ("No supported JavaScript runtime could be found") when the bundled runtime is missing or outdated. A yt-dlp runtime self-update with rollback (Seal proves the pattern at scale) is the ecosystem's survival strategy. Without it, any YouTube-side change that outpaces the bundled yt-dlp version breaks sound search and video wallpaper discovery silently.
- **Reddit source confirmed dead (Verified)**: On 2026-05-30, Reddit returned 403 on unauthenticated .json endpoints. OAuth credentials are no longer available to new registrations since November 2025. The Reddit source is irrecoverable without existing OAuth credentials. All 7 wallpaper subs, 4 video subs, and Wallpaper of the Day are broken.
- **N-1 toolchain debt now critical (Verified)**: AGP 9.1.1 stable (April 2026), Kotlin 2.4.0 (June 2026), Gradle 9.5.1, Room 2.7.1, Navigation 2.9.6, Compose BOM 2026.05.00, Media3 1.10. Aura is on AGP 8.7.3 / Kotlin 2.1.0 / Gradle 8.12. The `-Xskip-metadata-version-check` flag in `kotlinOptions` is already a symptom. Upgrading unblocks: targetSdk 37, KSP2, type-safe navigation, Compose adaptive layouts, Coil 3, OkHttp 5, predictive back completion, and SBOM generation.
- **Coil 3.x available (Verified)**: Maven coordinates changed to `io.coil-kt.coil3`. 25-40% runtime improvement, 35-48% allocation reduction. Network loading requires explicit dependency (`coil-network-okhttp`). Compose methods updated to be restartable/skippable. Migration is medium effort but high reward for an image-heavy app.
- **OkHttp 5.x stable (Verified)**: Separate JVM/Android artifacts, DNS-over-HTTPS promoted to stable, Java 9 modules from 5.2+. Retrofit 3.0 is also stable with forward binary compatibility from 2.x. Migration is a natural pair with the N-1 toolchain upgrade.
- **NewPipe Extractor 0.26.3 available (Verified)**: Released March 2026. Fixes YouTube playlist extraction and Bandcamp search. SoundCloud now uses 64-bit track IDs (overflow prevention). Aura is pinned at 0.24.8.

### Carried risks (roadmapped in earlier passes, still open)
- Cloud Functions Node 20 decommission 2026-04-30 (past EOL; migration to Node 22 urgent). CVE-2025-27148 Gradle wrapper. Tag-pinned Actions. App Check PLAY_RECOGNIZED default. Vulnerable jackson-databind/commons-io transitives. Open-Meteo attribution gap.

### Missing guardrails
- **Provider health watchdog**: SourceMetrics records failures but nothing flags persistent 100%-failure. Reddit was dead ~10 days before anyone noticed.
- **yt-dlp version drift monitor**: No mechanism to detect when bundled yt-dlp version is too old for YouTube's current player JS.

### Clean bill (Verified, GitHub Advisory DB, 2026-06-10)
Zero advisories affect Aura's pinned OkHttp 4.12, Okio, Moshi, Coil 2.7, Media3 1.5, Retrofit 2.11, Kotlin 2.1 stdlib, WorkManager, Firebase BoM 34.13.0, ML Kit. Exposure is limited to youtubedl-android transitives (jackson-databind, commons-io).

## Architecture Assessment

- **N-1 is now a 3-generation jump**: AGP 8.7.3 -> 9.1+, Gradle 8.12 -> 9.x, Kotlin 2.1.0 -> 2.3+. This is substantially more work than the single-step upgrade originally scoped. The AGP 9 migration guide documents breaking changes including namespace requirement enforcement, default minification changes, and KSP2 requirement. Recommend: jump directly to AGP 9.1 + Gradle 9.5 + Kotlin 2.3 in one coordinated pass, then stabilize before bumping Kotlin further.
- **Coil 2 -> 3 migration**: Package rename (`coil` -> `coil3`), explicit network dependency, separate Android/JVM artifacts. Medium effort for 161 Kotlin files but most usage is `AsyncImage` / `rememberAsyncImagePainter` which stays the same. Biggest risk: custom `ImageLoaderFactory` in `FreeVibeApp.kt` needs rework.
- **Retrofit 2 -> 3 migration**: Forward binary compatible. Low effort -- coordinate change only for most cases.
- **Navigation type-safe routes**: 2.9.6 enables `@Serializable` route classes. The NX-4 `SelectedContentHolder` replacement should pair with this. Currently blocked on N-1.
- **Media3 1.10 Material3 playback composables**: New `Player` composable combining `ContentFrame` with customizable controls. Directly relevant to Aura's sound preview and video preview UIs.
- **Predictive back**: Default-on for targetSdk 34+. Aura has 4/18 screens done. Completing this requires N-1 + targetSdk bump.
- **Reddit decommission code cleanup**: `RedditRepository.kt`, `RedditPost.kt`, Reddit-related code in `WallpaperRepository.kt`, `VideoWallpapersViewModel.kt`, `DailyWallpaperWorker.kt`, wallpaper/video source lists. WOTD needs a fallback source (Wallhaven top/day or Pexels curated).
- **Cloud Functions Node 20 -> 22**: `functions/package.json` engine field + `firebase.json` runtime. Node 20 EOL was April 30, 2026 -- already past.
- **16KB page size**: youtubedl-android 0.18.0+ ships aligned 64-bit libs. One verification run closes this.
- **SAF folder infrastructure**: Zero `OPEN_DOCUMENT_TREE` / `DocumentFile` usage. Both scheduled-backup and folder-rotation-source need it.

## Rejected Ideas

| Idea | Reason | Source |
|------|--------|--------|
| Google Photos shared-album rotation source | No API for third parties; scraping is fragile + ToS risk | Paperize #531 |
| Switching youtubedl-android to a "maintained fork" | JunkFood02 Maven group is the same line | Maven Central |
| Day/night wallpaper pairs as new feature | Already shipped (v6.15.0 SystemThemeListener) | Paperize #516 |
| Tapet-style procedural generator as separate tab | Folded into U-6 AGSL shader gallery scope | Tapet |
| Zedge as a scraped source | ToS/legal exposure | WallYou |
| In-app update checker | Obtainium/Izzy own updates | F-Droid/Izzy policy |
| Reddit OAuth for continued access | New OAuth registrations closed Nov 2025; existing creds unavailable | Reddit API docs, May 2026 |
| Unsplash source | API ToS explicitly prohibits wallpaper apps | Unsplash API Terms |
| Subscriptions/ads/coins | Charter contradiction | R-3/R-4/R-5 |
| KLWP-grade scripting | Charter contradiction (R-6) | KLWP |
| Freesound OAuth2 | Preview HQ MP3 128kbps sufficient | R-2 |
| Zedge-style primary AI Generator / AI community | Local-first direction; current mobile image generation quality does not meet the target bar; keep any external generation optional and explicit | Zedge Android 9.24.2 capture, user direction |
| Zedge account/login/follower profile parity | The continuity need should be solved with local Library, export/import, and backup/restore, not identity | Zedge My Zedge capture, user direction |
| Zedge credits, daily rewards, paid tiers, and reward ads | Charter contradiction and service dependency | Zedge credits capture, user direction |
| Zedge push offer notifications | Trust risk; notifications should be feature-triggered only | Zedge first-run notification prompt |
| On-device SD generation (unbounded) | Hold decision (Cycle 139 evidence gate) | U-2/U-14 |

## Sources

### Platform and toolchain
- https://developer.android.com/build/releases/agp-9-1-0-release-notes
- https://blog.jetbrains.com/kotlin/2026/06/kotlin-2-4-0-released/
- https://docs.gradle.org/current/release-notes.html
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/navigation
- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/jetpack/androidx/releases/work
- https://developer.android.com/guide/practices/page-sizes
- https://developer.android.com/about/versions/17/release-notes

### Dependencies and migrations
- https://coil-kt.github.io/coil/upgrading_to_coil3/
- https://square.github.io/okhttp/changelogs/changelog/
- https://github.com/square/retrofit/releases
- https://github.com/TeamNewPipe/NewPipeExtractor/releases
- https://github.com/yausername/youtubedl-android/releases
- https://github.com/yt-dlp/yt-dlp/issues/15012

### Security advisories
- https://nvd.nist.gov/vuln/detail/CVE-2025-27148
- https://www.herodevs.com/blog-posts/cve-2025-52999-denial-of-service-via-stack-overflow-in-jackson-core
- https://www.stepsecurity.io/blog/pinning-github-actions-for-enhanced-security-a-complete-guide

### Services and APIs
- https://medium.com/@alex_79882/reddits-api-is-officially-dead-in-2026-here-s-what-i-use-instead-f88ee5b809c8
- https://dev.to/matheus_releaserun/nodejs-20-end-of-life-migration-playbook-for-april-30-2026-2onh
- https://firebase.google.com/docs/app-check/android/play-integrity-provider
- https://open-meteo.com/en/terms

### Competitors and community
- https://github.com/maocide/UndeadWallpaper
- https://github.com/ammargitham/WallFlow/issues
- https://github.com/Anthonyy232/Paperize/issues
- https://github.com/JunkFood02/Seal/issues/2413
- https://github.com/AlynxZhou/alynx-live-wallpaper
- https://github.com/patzly/doodle-android
- https://alternativeto.net/software/zedge/
- https://justuseapp.com/en/app/1086101495/zedge-wallpapers/reviews

### Zedge parity sources
- https://play.google.com/store/apps/details?hl=en_US&id=net.zedge.android
- https://www.zedge.net/
- https://www.zedge.net/ringtones
- https://help.zedge.net/hc/en-us/articles/360025266492-Permissions-on-Zedge
- Android device capture: `net.zedge.android` 9.24.2 / versionCode 92400200 on SM-S938B, Android 16, 2026-06-10; screenshots/UI XML stored outside repo under `%LOCALAPPDATA%\Temp\aura-zedge-research`.
- Aura verification: repo search and docs at v6.31.1 / versionCode 112 plus installed `com.freevibe` 6.21.0 UX snapshot used only to confirm current on-device navigation shape.

## Open Questions

1. **Does the project have existing Reddit OAuth credentials?** If so, authenticated access may still work; if not, Reddit is irrecoverable and decommission is the only path.
2. **Firebase console App Check PLAY_RECOGNIZED setting** -- console-only visibility; determines off-Play eligibility.
3. **Owner timeline for N-1** -- the toolchain gap has grown from one step to three; blocks all targetSdk 37 and downstream items.
4. **Cloud Functions Node 20 runtime status** -- Node 20 EOL was April 30, 2026; verify whether deployed functions are still running or have been auto-disabled.
