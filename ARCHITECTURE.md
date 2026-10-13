# Aura: Architecture

Living architecture overview for contributors. Internal working notes live in `CLAUDE.md` (gitignored). This file describes the layered model + extension points.

## Layered model

```
┌────────────────────────────────────────────────────────────────────┐
│  Compose UI                                                        │
│   16+ screens, 5 bottom-nav tabs (Wallpapers / Videos / Sounds /   │
│   Library / Settings) + Editors / Collections / Downloads /        │
│   Onboarding. Material 3 + custom rectangular 4-12dp shape system. │
├────────────────────────────────────────────────────────────────────┤
│  ViewModels (Hilt @HiltViewModel)                                  │
│   One per screen surface. Expose StateFlow<UiState>. Bridge to     │
│   repositories + services. Lifecycle-scoped coroutines via         │
│   viewModelScope. SelectedContentHolder singleton bridges          │
│   selection across NavBackStackEntries (persists to disk for       │
│   process-death survival: NX-4).                                   │
├────────────────────────────────────────────────────────────────────┤
│  Repositories (data/repository/)                                   │
│   One per active source: Wallhaven / Pexels / Pixabay / Bing /     │
│   Reddit / YouTube / AI / Collection / Vote / Upload / Favorites / │
│   CreatorProfile / SearchHistory / WallpaperUpload. Dormant source │
│   repositories remain only for legacy record attribution.          │
│   Aggregator: WallpaperRepository.getDiscover() mixes feeds with   │
│   per-source timeouts.                                             │
├────────────────────────────────────────────────────────────────────┤
│  Network (data/remote/)                                            │
│   Retrofit interfaces + Moshi + OkHttp. ProviderCapability sets    │
│   lifecycle, build, channel, media, priority, and allowed actions. │
│   SourceMetrics wraps active fetches with latency/success counters.│
├────────────────────────────────────────────────────────────────────┤
│  Local (data/local/)                                               │
│   Room DB v20 (favorites, downloads, search_history, wallpaper_    │
│   cache, wallpaper_history, wallpaper_collections,                 │
│   wallpaper_collection_items, local_wallpapers, and                │
│   rotation_exclusions). DataStore: Settings + Onboarding +         │
│   User Styles + Rotation triggers + named sound shuffle pools.     │
│   PreferencesManager is the only reader/writer.                    │
├────────────────────────────────────────────────────────────────────┤
│  Services (service/)                                               │
│   WallpaperService implementations:                                │
│     VideoWallpaperService (MediaPlayer + Canvas GIF renderer)      │
│     ParallaxWallpaperService (ML Kit subject segmentation +        │
│       accelerometer parallax: see N-3 / NX-3)                      │
│     WeatherWallpaperService (Open-Meteo + Canvas particle overlay) │
│     DualWallpaperService (separate home/lock slots)                │
│   Foreground services:                                             │
│     AudioPlaybackService (Media3 MediaSession + AudioPreviewCache) │
│     RotationTriggerService (NX-6: USER_PRESENT + SCREEN_OFF)       │
│   Workers (CoroutineWorker + Hilt):                                │
│     AutoWallpaperWorker (periodic 15-min+ rotation)                │
│     DailyWallpaperWorker (daily pick refresh)                      │
│     WeatherUpdateWorker (30-min weather poll)                      │
│     AuraOriginalsDownloader (N-5: CC0 sound pack download)         │
│     RingtoneShuffleWorker (one schedule per named sound pool)      │
│     BatchDownloadWorker (favorites batch, survives process death)  │
│   One-shot services:                                               │
│     WallpaperApplier (applyByLocator: http/file/content/path)      │
│     SoundApplier (RingtoneManager + MediaStore)                    │
│     DownloadManager (StateFlow-driven download queue)              │
│     MediaCopyStore (original provenance + optimized apply copies)  │
│     AudioTrimmer (Media3 transforms + final-codec fallback)        │
│     ContactRingtoneService (per-contact ringtone assignment)       │
│     SmartCropDetector (NX-3: ML Kit subject bbox)                  │
│     SmartCropCalculator (pure geometry, unit-tested)               │
│     ColorExtractor (Palette + ColorAccentSelector)                 │
│     SourceMetrics (in-session p50/p95 per source)                  │
│   Receivers:                                                       │
│     FreeVibeWidgetReceiver (Glance widget)                         │
│     TaskerActionReceiver (L-2: ROTATE_NOW + SHUFFLE_NOW)           │
├────────────────────────────────────────────────────────────────────┤
│  External                                                          │
│   YouTube: NewPipe Extractor + yt-dlp (no API key)                 │
│   Media3: clips, fades, pitch-preserving speed, and platform       │
│     audio encoding in the Sound Editor                             │
│   FFmpeg/Python: bundled through youtubedl-android for extraction, │
│     video crop, missing codecs, and unsupported lossless cuts      │
│   Firebase: RTDB (community voting) + Storage (uploads) + Auth     │
│     (anonymous default; optional Google sign-in queued)            │
│   ML Kit: Subject Segmentation (unbundled, Google Play services)   │
│   Stability AI: AI wallpaper generation (bring-your-own-key)       │
│   Open-Meteo: free weather, no key, no rate limit                  │
└────────────────────────────────────────────────────────────────────┘
```

## Package map

```
app/src/main/java/com/freevibe/
├── FreeVibeApp.kt                 # Application + Coil image loader + crash logging
├── MainActivity.kt                # Single-activity host
├── data/
│   ├── local/                     # Room + DataStore
│   ├── model/                     # Wallpaper, Sound, FavoriteEntity, …
│   ├── paging/                    # PagingSources
│   ├── remote/                    # Retrofit + RateLimitInterceptor + SourceMetrics hooks
│   └── repository/                # one per source + aggregators
├── di/
│   └── AppModule.kt               # Hilt bindings: OkHttp, Retrofit, Room, Moshi
├── service/                       # See "Services" layer above
├── ui/
│   ├── FreeVibeRoot.kt            # NavHost + bottom nav + widget deep linking
│   ├── components/                # SharedComponents (AuraStateCard, GlassCard, …)
│   ├── navigation/                # NavGraph definitions
│   ├── screens/                   # one per feature surface
│   └── theme/                     # Material 3 + Aura design tokens
└── widget/
    └── FreeVibeWidget.kt          # Glance home + lockscreen widget (NX-2)
```

## Key abstractions

### `WallpaperApplier.applyByLocator(uri, target)`

Scheme-dispatching wallpaper-set entry point. Routes:
- `http(s)://` → OkHttp + bounded `readCapped` (64 MB ceiling) → `BitmapFactory.decodeByteArray`
- `file://` / absolute path → `BitmapFactory.decodeFile` with `inSampleSize`
- `content://` → `ContentResolver.openInputStream` + `copyCapped`

Use this for any new "apply a wallpaper" path. The HTTP-only `applyFromUrl` is legacy. It crashes on `file://` and `content://` schemes.

### `SelectedContentHolder`

Singleton bridging detail screens with the list ViewModel they came from. Holds:
- `selectedWallpaper: StateFlow<Wallpaper?>`: persisted to SharedPreferences JSON (NX-4)
- `wallpaperList: StateFlow<List<Wallpaper>>`: in-memory only; detail-screen pager source
- `selectedSound: StateFlow<Sound?>`: persisted

Full removal is queued behind Navigation 2.9 type-safe routes (NX-4 full sweep, N-1-gated).

### `AutoWallpaperWorker`

Single rotation worker handling both the legacy and enhanced scheduler paths. Reads:
- `prefs.schedulerEnabled` → `doSchedulerWork()` (home/lock split, collections, day/night)
- `prefs.autoWallpaperEnabled` → `doLegacyWork()`

Enqueued in three ways:
- `WorkManager.enqueueUniquePeriodicWork`: periodic 15-min+ rotation (`AutoWallpaperWorker.schedule`)
- `RotationTriggerService.enqueueRotation()`: one-shot expedited on USER_PRESENT / SCREEN_OFF (NX-6)
- `TaskerActionReceiver` → `enqueueRotation()`: broadcast-driven (L-2)

### `SmartCropDetector` + `SmartCropCalculator`

Reusable across wallpaper crop, video crop, and any future surface that needs subject-aware framing. The detector wraps ML Kit Subject Segmentation; the calculator is pure geometry (unit-tested). Different viewport conventions (wallpaper absolute scale vs. video relative-to-fit) are reconciled in the call site, not in the helper.

### `WallpaperHistoryManager`

Records every applied wallpaper with extracted Palette colours. Drives:
- Home/lock widget background tint
- Material You accent (Settings → Theme)
- "On this day" recall (queued, L-7)

### `MediaCopyStore`

Download history owns the untouched locator, source provenance, SHA-256 digest,
and technical metadata. `MediaCopyStore` owns optional files under
`files/apply_copies/`. Their names include source and settings digests, so a
repeat apply can validate and reuse its prior output. Replacing or deleting a
copy never changes the original locator. Copy mutations are serialized, and a
database failure retains the previously referenced file. Video originals live under
`files/media_originals/`; wallpaper and sound downloads remain in MediaStore.
See [`docs/media-copy-lifecycle.md`](docs/media-copy-lifecycle.md).

Local media identity is separate from its replaceable locator. Room records the
health of downloaded and favorited files, while local wallpaper rows keep a
stable ID across document URI changes. `PathBackedRecordReconciler` labels a
locator as available, missing, permission-revoked, or corrupt without clearing
it. `LocalMediaRelinkManager` validates a user-selected replacement, commits
all Room associations in one transaction, and preserves rotation and theme
references. SAF folder repair uses exact SHA-256 matches and a 500-item batch
limit. See [`docs/local-media-relink.md`](docs/local-media-relink.md).

### `SoundShufflePoolManager`

Named sound pools live as bounded Moshi JSON in DataStore. The manager owns all
pool mutations, resolves downloaded and bundled IDs at run time, filters each
candidate against ringtone, notification, or alarm duration limits, and writes
a bounded apply history. One unique WorkManager job is scheduled per enabled
pool. Boot and package-replacement handling migrate the former global toggles
once, then restore every eligible pool schedule. Portable backup strips file
locators and history before export. See
[`docs/sound-shuffle-pools.md`](docs/sound-shuffle-pools.md).

## Process-death + lifecycle

- ViewModels: scoped to `NavBackStackEntry`. Survive config changes; recreated after process death.
- `SelectedContentHolder`: persists primary selection to disk on every `select*` call; restores on Hilt construction. Pager list is in-memory only; detail screen collapses to single-item display when list is empty.
- `WallpaperService` engines: pause render threads on `onVisibilityChanged(false)`. Sensors deregister when invisible. Media3 sound exports cancel with their coroutine. Retained FFmpeg work has a 120-second process timeout, and video crop keeps its back-press guard.

## Live-wallpaper engine discipline

Every live wallpaper must:

1. Stop rendering when `onVisibilityChanged(false)`.
2. Cap FPS to 30 by default; cap to 15 when battery < 15 % AND not charging.
3. Use SurfaceView (not TextureView). It used 30% less battery in the existing benchmark.
4. Synchronize bitmap access (parallax engine writes from segmenter callback, reads from draw loop).
5. Recycle bitmap layers in `onDestroy` and on engine teardown.

`VideoWallpaperService` and `ParallaxWallpaperService` are the reference implementations. New engines should follow the same lifecycle protocol. See ROADMAP NX-1 (GL/AGSL engine migration) for the planned consolidation.

## Build and Release

- Local debug verification uses `assembleDebug`, `testDebugUnitTest`, and `lintDebug`.
- Local release verification builds signed APK/AAB artifacts with `freevibe.jks`, checks metadata, records SHA-256 hashes, and publishes artifacts through GitHub Releases.
- Per-ABI APK splits + F-Droid reproducible-build verification are queued (NX-8).

## Design system

- AMOLED-first dark theme; neutral surfaces; brass / mist / coral accents.
- Rectangular radii, 12 dp at most, all taken from `AuraShapes` in `ui/theme/Shapes.kt`. **No pill / oval / fully-rounded backdrops.** Material buttons get an explicit shape because their default is a pill, and nav items hide their pill indicator. See the v6.16.0 changelog for the rule.
- Colour literals live in `ui/theme/` or in a table registered in `tools/design_token_check.py` with a reason. Provider badge colours go through `sourceBadgeTone`, which keeps the text at 4.5:1 on the glass card over any wallpaper.
- `tools/design_token_check.py` fails when a literal corner shape, a radius above the ceiling, a pill default, an unregistered colour literal or a badge that skips the contrast tone comes back.
- Zero letter-spacing; calmer elevation; status differentiated by colour / border / font weight, not shape.
- Shared components: `GlassCard`, `HighlightPill`, `CountBadge`, `AuraStateCard`, `CompactSearchField`.

## Roadmap

[`ROADMAP.md`](ROADMAP.md) is the source of truth for what's queued. Every item links to sources in the Appendix. Items shipped in the Implementation Log are dated and traceable.

## Related docs

- [`CONTRIBUTING.md`](CONTRIBUTING.md): how to open a PR, code style, charter.
- [`ROADMAP.md`](ROADMAP.md): tiered backlog with scoring and sources.
- [`CHANGELOG.md`](CHANGELOG.md): release notes.
- [`docs/firebase-admin-claims.md`](docs/firebase-admin-claims.md): Firebase Custom Claims runbook (N-2).
- [`docs/aura-originals-curation.md`](docs/aura-originals-curation.md): Aura Originals CC0 curation workflow (N-5).
- [`docs/media-copy-lifecycle.md`](docs/media-copy-lifecycle.md) explains source preservation, optimized apply copies, rollback, and HDR handling.
- [`docs/sound-shuffle-pools.md`](docs/sound-shuffle-pools.md) defines sound pool membership, target limits, scheduling, history, and backup behavior.
