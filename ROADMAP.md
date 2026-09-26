# Aura Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions

### P2

- [ ] P2 — Add named shuffle pools for ringtone, notification, and alarm sounds
  Why: the current shuffle worker draws from all downloaded sounds and does not offer notification shuffle. Users need small intentional pools, per-target control, and predictable recovery rather than a global randomizer.
  Evidence: **Verified.** `RingtoneShuffleWorker.kt` reads the broad SOUND download set for ringtone/alarm selection; no notification pool or user-managed membership exists; Peristyle and wallpaper competitors validate named pools as a comprehensible automation model, while ringtone users currently build folder-based rotation externally.
  Touches: sound collection/profile schema, `RingtoneShuffleWorker.kt`, notification target support, Sounds/Library UI, scheduler and boot restoration, history/Undo, export/import, tests.
  Acceptance: users create named pools, add local/downloaded/original sounds, choose ringtone, notification, alarm, or any combination, and set a schedule; the worker avoids an immediate repeat when another valid item exists, skips missing/incompatible media visibly, records history, and restores scheduling after reboot; disabling a pool cancels its work; pools and assignments round-trip through backup.
  Complexity: M

- [ ] P2 — Add named Reddit feed presets for discovery and rotation
  Why: one global comma-separated subreddit preference cannot represent different moods, devices, video feeds, or rotation contexts. Reusable presets create materially more choice while keeping the highest-quality source first.
  Evidence: **Verified.** `PreferencesManager.kt:58,500-520` stores one wallpaper list and one video list with a twelve-subreddit cap; WallFlow supports saved searches and source configurations: https://github.com/ammargitham/WallFlow.
  Touches: Reddit OAuth query model, Room/DataStore preset schema, Wallpapers/Video/Settings UI, rotation source selection, import/export, diagnostics, tests.
  Acceptance: users can create, rename, duplicate, reorder, and delete presets containing subreddit/community list, media type, sort, time window, safe-content setting, and minimum dimensions/duration; Aura ships several editable defaults without silently enabling adult content; the active preset is visible in each feed; rotation can bind to a preset; pagination/cache keys include the full preset; presets round-trip through backup and survive subreddit removal.
  Complexity: M

- [ ] P2 — Repair crash issue intake for no-launch failures
  Why: the current crash template requires an in-app diagnostics bundle even when the crash prevents Aura from opening, and it omits the exact environment and reproduction fields needed to act on device-specific media defects.
  Evidence: **Verified.** `.github/ISSUE_TEMPLATE/crash_report.yml:10-20` requires diagnostics but does not collect reproduction steps, expected/actual result, Aura version, Android version, device model, source/media URL with privacy warning, or a no-launch fallback; the current tracker shows how much resolution depends on device and source details in issues #2 and #44.
  Touches: `.github/ISSUE_TEMPLATE/crash_report.yml`, `SUPPORT.md` or existing troubleshooting docs, diagnostics screen copy, link validation test.
  Acceptance: the template collects minimal repro, expected/actual, app version, Android version, device/model, install channel, feature/source, and whether the issue survives restart; diagnostics are requested when available but never mandatory for a no-launch crash; a redacted `adb logcat` or bugreport fallback is documented with secret-removal guidance; every referenced Settings path and URL exists; a fixture rejects future removal of the fallback.
  Complexity: S

- [ ] P2 — Replace or prove harmless the five under-aligned libwebp objects in the FFmpeg payload
  Why: with the 16 KB gate now reading inside `lib/<abi>/*.zip.so`, five prebuilt libwebp ELFs are measured at `p_align 4096` on both 64-bit ABIs. They are recorded as exceptions so the gate stays useful, but an exception is an acknowledgement, not a fix: on a 16 KB-page device `dlopen` of a 4 KB-aligned object fails, and FFmpeg is configured `--enable-libwebp`, so a WebP path can reach them.
  Evidence: `docs/distribution/native-alignment.json` `nestedArchiveEvidence` and `nestedArchiveAlignmentExceptions`; measured 2026-09-05 against the `io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1` payload as `usr/lib/libsharpyuv.so`, `libwebp.so`, `libwebpdecoder.so`, `libwebpdemux.so`, `libwebpmux.so`, while all 400 other 64-bit LOAD segments in the same archive are 16384; `libpython.zip.so` is fully compliant. Upstream youtubedl-android issue #334.
  Touches: `docs/distribution/native-alignment.json`, `app/build.gradle.kts` (dependency pin), `AudioTrimmer.kt` and `VideoCropScreen.kt` if a WebP path has to be closed off.
  Acceptance: one of three outcomes is committed with its evidence — a newer or rebuilt FFmpeg payload ships all five at 16384 and the exceptions are deleted; or a test proves no Aura code path can make FFmpeg load libwebp, and the policy records that reachability argument instead of a bare exception; or the WebP inputs are rejected before FFmpeg is invoked. In every case the gate still fails if a sixth under-aligned object appears.
  Complexity: M

- [ ] P2 — Add the fastlane store images IzzyOnDroid requires
  Why: `fastlane/metadata/android/en-US/` has no `images/` directory, so there is no icon, phone screenshot, or feature graphic for a store listing to consume. (Changelogs are current — an earlier claim that they stopped at versionCode 8 was a lexical-sort artifact; 22 exist, through 141.)
  Evidence: `ls fastlane/metadata/android/en-US/` returns only `changelogs/`, `full_description.txt`, `short_description.txt`, `title.txt`; IzzyOnDroid App Inclusion Policy requires in-repo Fastlane metadata with icon and screenshots. Screenshot capture itself stays blocked in `Roadmap_Blocked.md`.
  Touches: `fastlane/metadata/android/en-US/images/**`, `tools/store_metadata_preflight.py`.
  Acceptance: `images/icon.png` and at least four `images/phoneScreenshots/` entries exist at the required dimensions, and the preflight fails when the icon or screenshot set is absent.
  Complexity: S

- [ ] P2 — Fix the remaining service and editor reliability defects
  Why: a cluster of small independent defects that each fail silently in the exact paths users hit after process death or on decode failure.
  Evidence: `RotationTriggerService.kt:61-72` (`runBlocking` DataStore read on the main thread in the `intent == null` START_STICKY restart) and `:85-89` (`getLaunchIntentForPackage` may return null before `startForeground`); `SoundEditorViewModel.kt:651-655` (fabricates a sine waveform on decode failure with no signal to the UI) and `:592-603` (`copyUriToCache` writes non-atomically, unlike `downloadToCache` at `:542-584`); `VideoWallpapersViewModel.kt:445-446,1264-1283` (`freevibe_pixabay_video_cache` grows without bound) and `:818-821,1077` (main-thread encode of up to 120 items per load); `VoteRepository.kt:208,342` (a Firebase error reported as a real count of zero, and one that is discarded entirely).
  Touches: `RotationTriggerService.kt`, `SoundEditorViewModel.kt`, `VideoWallpapersViewModel.kt`, `VoteRepository.kt`, tests.
  Acceptance: the service restart path reads preferences off the main thread and survives a null launch intent; waveform extraction failure is surfaced rather than faked; `copyUriToCache` is temp-then-rename and reports limit failures; the video cache is bounded and its encode runs off the main thread; Firebase read errors are distinguishable from zero.
  Complexity: M

- [ ] P2 — Reconcile BatchDownloadService with its documented design
  Why: it is documented as a foreground service in both CLAUDE.md and ARCHITECTURE.md but is a plain `@Singleton` with an ad-hoc scope, so a long batch is killed when the process is backgrounded and `isRunning` is left true.
  Evidence: `BatchDownloadService.kt:41-44,74,113-116,127,140`; CLAUDE.md Key Files; ARCHITECTURE.md.
  Touches: `BatchDownloadService.kt`, manifest FGS declaration or a WorkManager migration, `docs/distribution/foreground-service-declaration.json`, CLAUDE.md, ARCHITECTURE.md.
  Acceptance: batch downloads either run as a declared foreground service or as WorkManager work that survives backgrounding, progress is recoverable after process death, and the docs match the implementation.
  Complexity: M

- [ ] P2 — Remove the bundled FFmpeg module without losing advertised media workflows
  Why: the arm64 FFmpeg archive contributes 35,624,931 bytes to a roughly 63.8 MB APK, and the dependency is a separate module whose removal can bring arm64 below IzzyOnDroid's 30 MB review threshold.
  Evidence: `docs/distribution/native-alignment.json`; `app/build.gradle.kts`; `AudioTrimmer.kt`; `VideoCropScreen.kt`; `VideoWallpapersViewModel.kt`; Android Media3 Transformer documentation; youtubedl-android issue #248; the maintained Ringdroid F-Droid package.
  Touches: `app/build.gradle.kts`, `AudioTrimmer.kt`, `VideoCropScreen.kt`, `VideoWallpapersViewModel.kt`, `YouTubeRepository.kt`, Reddit/video acquisition paths, codec and release-size fixtures.
  Acceptance: video crop uses Media3 Transformer; YouTube and Reddit acquisition avoid yt-dlp merge/remux or perform it through Media3; every advertised sound export and verified lossless-cut case still passes through Media3 or a smaller audited codec path; `youtubedl-android:ffmpeg` is removed, no release APK contains `libffmpeg.zip.so`, full and FOSS arm64 APKs are below 30 MiB, and the native gate records whether legacy JNI packaging is still required.
  Complexity: L

  Note 2026-09-04: this is now a security item as well as a size item. `docs/legal/native-compliance.lock.json:364,428,492` records the bundled build as FFmpeg 7.1.1 on every ABI, and CVE-2026-8461 ("PixelSmash", heap out-of-bounds write in the MagicYUV decoder, reachable from a crafted AVI/MKV/MOV) is fixed only in 8.1.2. The version arrives inside `ffmpeg-0.18.1.aar` and cannot be upgraded independently, so removal is the only complete fix. See the separate P1 item that bounds FFmpeg's input in the meantime.

- [ ] P2 — Codify the design system as tokens and gate it
  Why: the "rectangular 4–12 dp radii, no pill / oval / fully-rounded backdrops" rule is written in ARCHITECTURE.md and CLAUDE.md and enforced by nothing — corner radii are literal numbers at 250+ call sites, and the rule is already broken in shipped code. It is the only major documented project rule with no gate behind it, in a repo with 82 gates.
  Evidence: `VideoWallpapersScreen.kt:884` uses `RoundedCornerShape(50)`, a full pill; `WallpapersScreen.kt:1268` uses 24 dp; 225 uses of `RoundedCornerShape(8)` plus strays at 1, 2, 4, 5, 6, 10, 12; `ui/theme/` contains only `Theme.kt` with colour tokens and no shape or spacing source; 102 hardcoded `Color(0x…)` literals across seven UI files; `SharedComponents.kt:519-548` and `WallpaperDetailScreen.kt:408` also derive provider-badge foreground/background combinations without a contrast token or assertion; `test/tools/` has no design gate.
  Touches: `ui/theme/` (new shape and spacing token files), the seven UI files with colour literals, the two shape violations, a new `tools/design_token_check.py` and its test.
  Acceptance: shape and spacing tokens live in `ui/theme/` and the two violations are corrected or explicitly waived with a recorded reason; a gate rejects literal `RoundedCornerShape(n)` outside the token file and any radius above the documented ceiling; colour literals outside `Theme.kt` and the source-tone tables are rejected or registered; provider badges meet 4.5:1 text and 3:1 UI contrast in AMOLED, dark, and light themes; the gate fails when a pill radius or low-contrast badge is reintroduced.
  Complexity: M

- [ ] P2 — Surface the failures that currently reach the user as nothing
  Why: a cluster of independent silent failures on paths where the user has just tapped something and nothing else can tell them it did not work.
  Evidence: `VoteRepository.kt:407` — `onCancelled(error: DatabaseError) {}`, so a permission-denied or disconnect leaves stale votes with no log; seven `startActivity` calls in empty catches at `FreeVibeWidget.kt:352,381,407` (the widget has no other feedback channel), `ContactPickerScreen.kt:448`, `SoundDetailScreen.kt:564,582`, `WallpaperDetailScreen.kt:620-630`; `VideoWallpaperService.kt:126-134` and `:248-256` swallow display-metrics and `MediaMetadataRetriever` failures so stale or zero dimensions enter the scaling math. Distinct from the tracked "remaining service and editor reliability defects" item, which covers `RotationTriggerService`, `SoundEditorViewModel`, `VideoWallpapersViewModel`, and `VoteRepository.kt:208,342`.
  Touches: `VoteRepository.kt`, `FreeVibeWidget.kt`, `ContactPickerScreen.kt`, `SoundDetailScreen.kt`, `WallpaperDetailScreen.kt`, `VideoWallpaperService.kt`, string resources, tests.
  Acceptance: `onCancelled` logs and marks the vote state degraded; every `startActivity` failure produces user-visible feedback appropriate to its surface, and the widget path uses a widget-visible state rather than a Toast; the two `VideoWallpaperService` swallows log and fall back to a defined value instead of a stale one; tests cover an `ActivityNotFoundException` on each surface.
  Complexity: S

  Note 2026-09-04: a re-scan found six more of the same shape that this item should cover, and they are worse because several are silent in release specifically. `AudioPlaybackManager.kt:103-113` resets playback state with no log in any build. `FreeVibeApp.kt:109-111` logs Firebase App Check init failure only under `BuildConfig.DEBUG`, so if enforcement is ever enabled every later Firestore and Storage failure has no recorded cause. `ColorExtractor.kt:64-67` and `CommunityIdentityProvider.kt:96-99` return null with no log at all. `YouTubeRepository.kt:394-400` is the only one of five sibling catches with no log. `DownloadManager.kt:459-463` swallows a `SecurityException` from a revoked notification permission, so the download completes but the user is never told why no notification appeared. `ParallaxWallpaperService.kt:154-158,285-289` and `OfflineFavoritesManager.kt:113-117` log only in debug. Release-build silence should be the acceptance line, not debug-build silence.

- [ ] P2 — Browse the device's own sounds and offer a way back to the stock ringtone
  Why: Aura writes ringtones but never reads them — it cannot show what is currently set, cannot let the user pick from sounds already on the device, and captures only its *own* last-applied URI, so there is no path back to the OEM default from inside the app. Applying a ringtone is effectively irreversible, and the category's only maintained editor was abandoned for six years, so this shelf is uncontested.
  Evidence: `RingtoneManager.TYPE_*` appears only in `SoundApplier.kt:65-67`, `RingtoneShuffleWorker.kt:65,87`, and `RingtoneRestorationReceiver.kt:53-55` — all write or restore-Aura's-own-value paths; no `RingtoneManager.getCursor()` anywhere; no revert string in `strings.xml`; UltimateRingtonePicker; ringdroid #16, open since 2015-12-10.
  Touches: `SoundApplier.kt`, `SoundsScreen.kt` or a new device-sounds surface, `PreferencesManager.kt` (capture the pre-Aura URI on first apply), `RingtoneRestorationReceiver.kt`, string resources, tests.
  Acceptance: a device-sounds view lists and previews system and user sounds per type and marks the one currently set; the pre-Aura URI for each of the three types is captured before the first overwrite and never overwritten again; a "restore original" action returns each type to that URI and reports honestly when the original is gone; tests cover first apply, repeat apply, and a missing original.
  Complexity: M

- [ ] P2 — Ship an opt-in in-app update check
  Why: the entire distribution channel is sideload. Users who do not run Obtainium have no way to learn a new version exists, and the current gap — three versions published in the changelog and none reachable — is exactly the case where they would want to know. One HTTPS request to the releases endpoint with no identifiers is compatible with the no-tracking charter as long as it is off by default.
  Evidence: no update check anywhere in `app/src/main/java` (the only `releases/latest` references are static links in `LicensesScreen.kt:70,76,82`); `obtainium.json`; README's install section documents manual SHA-256 verification and `adb install -r`.
  Touches: a new update-check service, `SettingsPermissionsAboutSection.kt`, `PreferencesManager.kt`, `ProviderNetworkPolicy.kt` / `network_endpoint_inventory_check.py`, `docs/privacy/data-safety.md`, string resources, tests.
  Acceptance: an opt-in check compares the installed versionCode against the newest published Release, links to it, and shows the release notes; it is off by default, respects data-saver and metered-network posture, never auto-downloads or auto-installs, and is declared in the endpoint inventory and the data-safety doc; a test covers no-release, same-version, newer-version, and network-failure.
  Complexity: S

- [ ] P2 — Add StrictMode and LeakCanary to debug builds
  Why: the two recurring defect classes in this repo's history are main-thread preference and disk reads, and orphaned bitmaps and media players — precisely the two things these tools catch automatically, and neither is present. The `runBlocking` DataStore read on the main thread and the editor bitmap orphaning both reached shipped code and were found by reading, not by tooling.
  Evidence: no `StrictMode` and no `leakcanary` anywhere in `app/src/main/java`, `app/build.gradle.kts`, or `gradle/libs.versions.toml`; the tracked editor-bitmap and `RotationTriggerService.kt:61-72` items; `SoundEditorViewModel.kt:520-532` nests six empty catches around MediaPlayer teardown.
  Touches: `FreeVibeApp.kt`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`.
  Acceptance: debug builds install a thread policy (disk and network reads/writes) and a VM policy (leaked closables, activity leaks) that log rather than crash, and LeakCanary is a `debugImplementation` only; release and FOSS release artifacts contain neither, asserted by the APK scan; the existing known violations are enumerated in `CLAUDE.md` so new ones are distinguishable.
  Complexity: S

  Note 2026-09-04: add `detectImplicitUriPermissionGrant()` to the VM policy while wiring this. Android 18 stops auto-granting URI permissions for `ACTION_SEND`, `ACTION_SEND_MULTIPLE`, and `ACTION_IMAGE_CAPTURE`, and Android 17 ships the detector specifically so apps can find the call sites early (https://developer.android.com/about/versions/17/behavior-changes-all). Aura is currently compliant by accident rather than by rule: every `ACTION_SEND` outside `CollectionExporter.kt:130-138` sends `text/plain` and no content URI, so nothing breaks today, but the queued "share the media file" item introduces exactly the pattern the change targets. Having the detector already on turns that from a future regression into a build-time signal.

- [ ] P2 — Play more than one clip in the video live wallpaper
  Why: `VideoWallpaperService` plays exactly one video. A playlist with per-clip framing is the top-requested capability in the video-wallpaper category, and the whole rotation machinery Aura already owns — scheduler, day/night, collections — has no video equivalent.
  Evidence: no playlist or queue concept in `VideoWallpaperService.kt`; UndeadWallpaper v1.3.7 (per-clip zoom/offset/rotation/speed, shuffle, smart start); Lively #137 (25 comments) on condition-driven change. Depends on the tracked video-cache-bounding and main-thread-encode fixes; do those first.
  Touches: `VideoWallpaperService.kt`, `VideoWallpaperStorage.kt`, `PreferencesManager.kt`, video settings UI, the soak harness, string resources.
  Acceptance: an ordered or shuffled clip list advances at a configured boundary with no black frame at the seam; per-clip fit/crop and mute are preserved; the existing FPS cap, low-battery cap, and `onVisibilityChanged` pause govern the whole playlist, not just the first clip; total decoded storage stays bounded; the soak harness runs the playlist path and asserts nothing survives `onDestroy`.
  Complexity: L

  Note 2026-08-23: compileSdk 36 and Media3 1.11.0 are already shipped, so the old dependency gate is gone. Keep this sequenced after the existing video-cache and main-thread encode reliability work; use Media3 preload first and retain the custom engine only if a measured black-frame test still fails.

- [ ] P2 — Finish Simplified Chinese coverage and document translation contributions
  Category: ux
  Where: app/src/main/res/values/strings.xml; app/src/main/res/values-zh/strings.xml; app/src/full/res/values-zh/strings.xml; app/src/main/res/xml/locales_config.xml; CONTRIBUTING.md
  Problem: Simplified Chinese now ships, but 182 main-resource keys fall back to English and contributors still have no documented translation workflow.
  Evidence: PR #48 merged and locales_config registers en/zh. Current key comparison finds 1,806 default keys versus 1,624 main Chinese keys; the missing clean subset clusters in contact_picker_dnd_* plus action_back. The Full-only Chinese set is complete at 66 of 66. No Weblate/Crowdin/Transifex config or CONTRIBUTING translation instructions exist.
  Fix: Translate/review the 182 missing keys with placeholder/plural validation, document folder naming and review steps, and add a locale parity gate that allows deliberate fallbacks only with a reason.
  Acceptance: Chinese resources cover every required key with matching placeholders/plurals; Android 13+ language picker exposes Chinese; a native-speaker screenshot pass covers browse, apply, contact picker, and errors; contributor instructions reproduce the validation commands.
  Confidence: Verified
  Effort: M
  Reported: #47 — Reporter opened the translation umbrella with “Aura is English only right now”; PR #48 supplied Simplified Chinese, leaving the current residual coverage and contribution-path work.

- [ ] P2 — Give TalkBack announcements and a controlled reading order
  Why: the interactive-element audit recorded clean labels on 2026-08-11; the missing layer is *announcements*. Three `liveRegion` usages cover an app whose primary surfaces are async grids, a download queue, and audio playback, so a screen-reader user gets no notification when results arrive, a download finishes, or playback state changes. Reading order is entirely unmanaged.
  Evidence: `liveRegion` 3 occurrences, `heading` 7, `traversalIndex` 0, `isTraversalGroup` 0 across `app/src/main/java`; 48 `AuraStateCard` usages across 16 of 79 screen files show where async state transitions already exist and go unannounced.
  Touches: `SharedComponents.kt` (`AuraStateCard`), `DownloadsScreen.kt`, `SoundDetailScreen.kt`, the three feed screens, `app/src/androidTest/.../AccessibilityReleaseGateTest.kt`, `tools/accessibility_release_gate_check.py`.
  Acceptance: loading→ready, loading→error, and empty transitions announce politely once and do not re-announce on recomposition; download completion and playback state changes announce; feed sections are traversal groups with a defined order; the accessibility gate asserts a live region exists on each async surface it already covers.
  Complexity: M

- [ ] P2 — Preflight live-wallpaper capability and provide a truthful static fallback
  Why: `AndroidManifest.xml:37-40` marks live wallpaper optional, but `LiveWallpaperLauncher.kt:15-35` only tries direct/chooser intents and reports a generic failure; `WallpaperApplier.isSupported()` covers static wallpaper operations, not live-wallpaper feature/service availability. This is distinct from the existing P1 item that detects a live wallpaper that was active and later disappeared.
  Evidence: `AndroidManifest.xml:37-40`, `LiveWallpaperLauncher.kt:15-35`, `WallpaperApplier.kt:225-228`; Android `WallpaperManager`/live-wallpaper APIs; the UndeadWallpaper community thread consulted on 2026-08-11 reports OEM devices that disable live wallpapers.
  Touches: `LiveWallpaperLauncher.kt`, video/parallax entry points, `VideoWallpapersScreen.kt`, `WallpaperDetailScreen.kt`, capability tests, strings.
  Acceptance: before launch, Aura checks `PackageManager.FEATURE_LIVE_WALLPAPER`, resolves the requested service and action, and distinguishes unsupported, unavailable, and security-denied states; image sources offer static apply when valid, video-only sources explain the limitation; no path claims success after an unresolvable intent; tests cover no feature, missing service, security failure, and static fallback.
  Complexity: S

- [ ] P2 — Make direct media downloads validator-aware and resumable
  Why: `DownloadManager.downloadFile()` always issues an unconditional GET, starts a new temp file at byte zero, and deletes it after interruption; `DownloadProgress` is process-local and `DownloadEntity` stores only completed MediaStore rows. Size caps prevent oversized writes but do not prevent a mobile user from paying for the same interrupted 64 MiB transfer repeatedly. This complements, rather than duplicates, the existing BatchDownloadService item: that item fixes job lifetime, while this one fixes per-file transport.
  Evidence: `app/src/main/java/com/freevibe/service/DownloadManager.kt:114-185`, `app/src/main/java/com/freevibe/data/model/Models.kt:150-159`; RFC 9111 sections on incomplete/partial responses and validation; OkHttp’s cache/client API; cssnr/remote-wallpaper-android issue #26 requesting HTTP caching.
  Touches: `DownloadManager.kt`, `Models.kt`, `Database.kt`/Room migration, `DownloadEntity`/DAO, `DownloadsScreen.kt`, transport tests with a local HTTP server, cleanup/diagnostics.
  Acceptance: a stable download identity persists temp path, URL, byte count, size, and ETag/Last-Modified when available; retries send `Range` plus `If-Range` only with a matching validator and accept continuation only for a valid `206`; `200`, validator mismatch, range mismatch, or changed length safely truncates and restarts; completion remains temp-then-atomic MediaStore publication; process death resumes or clearly marks a recoverable failure; size/sniffing caps apply to the aggregate bytes; tests cover 206 resume, 200 restart, 412/validator change, cancellation, stale-temp cleanup, and no duplicate MediaStore rows.
  Complexity: M

- [ ] P2 — Ship the 24H wallpaper-pack editor its Settings toggle already promises
  Why: the toggle schedules `WallpaperPackWorker` every 15 minutes, but no UI can create or edit a pack, so the worker polls DataStore JSON that is always empty — perpetual no-op battery work shipped as a feature; time-of-day playlists are also Wallpaper Engine's most-praised capability.
  Evidence: commit `2025c41` ("editor UI for defining individual slots is a follow-up"); `SettingsWallpaperSection.kt:249`; `WallpaperPackManager.kt` (worker parses `prefs.wallpaperPackJson` that nothing writes); Wallpaper Engine Android time-of-day playlists.
  Touches: a pack editor surface (settings section or dedicated screen), `WallpaperPackManager.kt`, `SettingsViewModel.kt`, `PreferencesManager.kt`, string resources, tests.
  Acceptance: users can create, edit, and delete packs with wallpapers assigned per daypart (morning/day/evening/night) and per target (home/lock/both); the worker is enqueued only when an enabled pack has at least one slot and is cancelled when the last one is removed; with no pack defined the toggle explains what to do instead of scheduling empty work; tests cover empty-pack gating and slot resolution across the overnight wrap.
  Complexity: M

- [ ] P2 — Ship the sound-profile editor its Settings toggle already promises
  Why: same defect class as the pack editor — the toggle schedules `SoundProfileWorker` every 15 minutes and the worker defers with "no sound profiles defined" forever, because no UI can create a profile.
  Evidence: commit `3bfb2d7` ("Profile editor UI for defining individual profiles is a follow-up"); `SettingsSoundSection.kt:198`; `SoundProfileManager.kt:82-93` (empty-profile deferral each run).
  Touches: a profile editor surface, `SoundProfileManager.kt`, `SettingsViewModel.kt`, `PreferencesManager.kt`, string resources, tests.
  Acceptance: users can create named profiles mapping ringtone/notification/alarm URIs to start/end hours, enable/disable each, and delete them; the worker is enqueued only when at least one enabled profile exists; profile application records into the existing `lastApplied*Uri` restoration data so boot restoration does not stomp it; tests cover empty gating, overlapping windows, and the overnight wrap.
  Complexity: M

- [ ] P2 — Finish live-wallpaper dimming on the video and parallax engines
  Why: `LiveWallpaperDimming` (dim + double-tap reveal) shipped wired into `WeatherWallpaperService` only, with the other two engines named as follow-up wiring that never happened; the Settings toggle reads as engine-agnostic, so on video/parallax it is a silent no-op.
  Evidence: commit `517f642` ("reusable by VideoWallpaperService and ParallaxWallpaperService (left as follow-up wiring)"); grep shows no dimming reference in `VideoWallpaperService.kt` or `ParallaxWallpaperService.kt`; Muzei recede mode is the category reference.
  Touches: `VideoWallpaperService.kt`, `ParallaxWallpaperService.kt`, `LiveWallpaperDimming.kt`, the live-wallpaper soak harness, string resources.
  Acceptance: dim level and double-tap reveal behave identically on all three engines; re-dim after reveal follows the one-shot delayed-frame pattern CLAUDE.md documents for `WeatherWallpaperService.scheduleDraw()`; the soak harness runs the dimmed path and asserts no extra bitmap retention; until parity lands the toggle copy names the engines it affects.
  Complexity: S

- [ ] P2 — Classify OEM ringtone-write failures instead of failing generically
  Why: `SoundApplier` calls `RingtoneManager.setActualDefaultRingtoneUri` with no OEM-failure handling, and Samsung devices are documented throwing `IllegalArgumentException` ("cannot keep your settings in the secure settings") on notification-sound writes — the user sees a generic failure for a known, explainable device behavior in the app's core action.
  Evidence: `SoundApplier.kt:70,109`; Samsung developer-forum reports of the secure-settings exception on Galaxy devices; Samsung community threads on tones not persisting after updates.
  Touches: `SoundApplier.kt`, `ContactRingtoneService.kt`, error string resources, `SettingsDiagnosticsSection.kt` or the diagnostics bundle.
  Acceptance: the secure-settings failure class is caught and distinguished from missing `WRITE_SETTINGS`; the user gets device-specific guidance including a one-tap route to the system sound picker as fallback; the failure class is counted in diagnostics; a test covers the `IllegalArgumentException` path for each of the three sound types.
  Complexity: S

  Note 2026-09-04: the Samsung developer-forum URL behind the `IllegalArgumentException` claim now 404s and the exception string appears in no Stack Overflow question, so treat the specific message as unconfirmed and catch the class defensively rather than matching on text. The item still stands on its own logic: `SoundApplier.kt:70,109` has no OEM-failure handling at all. Community evidence that does hold up is the `WRITE_SETTINGS` posture — the most-upvoted review on the category's leading editor asks for exactly the flow Aura already has, an entry in the system picker with no elevated permission, so confirm `WRITE_SETTINGS` is requested only when the user taps "set as default" and never as a precondition for saving. Separately, the contact-ringtone failure that dominates this category throws nothing at all; that is a distinct P2 item added 2026-09-04.

- [ ] P2 — Prefetch the next rotation wallpaper
  Why: `AutoWallpaperWorker` fetches from the provider at fire time, so a dead or metered-blocked network at the trigger means a skipped rotation; prefetching the next candidate after each successful rotation makes remote-source rotation as reliable as local, and Wallora demonstrates the pattern.
  Evidence: `AutoWallpaperWorker.kt` provider fetch in `doWork`; Wallora README (prefetch cache for instant apply); WallFlow's open offline-mode request.
  Touches: `AutoWallpaperWorker.kt`, `DailyWallpaperWorker.kt`, a bounded prefetch cache (or `OfflineFavoritesManager` reuse), rotation diagnostics, tests.
  Acceptance: after each successful rotation the next candidate downloads to a bounded cache (count and byte budget) respecting metered/data-saver posture; at fire time a cached candidate applies without network and the cache refills afterward; cache misses fall back to the current fetch path; local-source rotation is unchanged; diagnostics report prefetch hit/miss; tests cover hit, miss, budget eviction, and metered deferral.
  Complexity: M

### P3

- [ ] P3 — Expand external automation with safe, stable parameters
  Why: Aura already exposes limited widget, tile, and broadcast entry points, but automators cannot select a named feed, collection, wallpaper target, or controlled action. A small versioned contract can improve Tasker and launcher integration without exposing arbitrary URLs, paths, or privileged operations.
  Evidence: **Verified gap, Likely demand.** `AndroidManifest.xml`, widget/tile receivers, and rotation actions expose fixed behavior; Peristyle and Wallora document external-intent and Tasker integration; https://github.com/Hamza417/Peristyle; https://github.com/thissayantan/wallora.
  Touches: an opt-in automation receiver/service, manifest export policy and signature/permission review, stable preset/collection IDs, rotation/apply coordinator, diagnostics/activity log, documentation, tests.
  Acceptance: a disabled-by-default, versioned contract supports only allowlisted actions such as apply next, apply a named local collection, choose home/lock/both, select a Reddit preset, pause, and resume; inputs use stable IDs and strict size/type validation; no arbitrary URL, file path, provider credential, community mutation, or YouTube extraction can enter through it; calls are throttled, logged, and return a safe result; disabled and malformed calls change nothing; instrumentation covers an untrusted external app.
  Complexity: M

- [ ] P3 — Emit a CycloneDX SBOM from the resolved dependency graph
  Why: the EU Cyber Resilience Act requires a machine-readable SBOM of at least top-level dependencies from 2027-12-11; Aura's readiness doc defers this to N-1, but the CycloneDX Gradle plugin works on the current toolchain and reads the resolved graph, so the `commons-io`/`jackson`/`commons-compress` constraints appear correctly.
  Evidence: `docs/distribution/sbom-readiness.json` (`status: deferredUntilN1ToolchainUpgrade`, `futureSbomArtifacts`); `app/build.gradle.kts` constraints block; CycloneDX Gradle plugin.
  Touches: `app/build.gradle.kts` or a convention plugin, `tools/sbom_readiness_check.py`, release artifact bundle.
  Acceptance: a release task emits `SBOM.cyclonedx.json` covering the release runtime graph plus native payloads; the pinned constraint versions appear as resolved; the artifact is published with the release and checked by the bundle gate.
  Complexity: M

- [ ] P3 — Strengthen dependency verification with trusted PGP keys
  Why: `gradle/verification-metadata.xml` exists with 1364 components but sets `verify-signatures=false`, so it is checksum-only and must be rewritten on every version bump — which is why it drifts; trusted keys survive upgrades and Gradle now reports key rotation separately from new dependencies.
  Evidence: `gradle/verification-metadata.xml:4-5`; the file is also CRLF-in-index (see the byte-hygiene item); JitPack `NewPipeExtractor` and a prerelease `youtubedl-android` are exactly the risk profile verification exists for.
  Touches: `gradle/verification-metadata.xml`, `tools/gradle_wrapper_check.py` or a new verification gate.
  Acceptance: signature verification is enabled with trusted keys for signed artifacts and checksums retained only for unsigned ones; a clean-clone build verifies; the regeneration command is documented.
  Complexity: M

- [ ] P3 — Add a wallpaper position lock and launcher-parallax suppression
  Why: launcher-driven zoom and scroll parallax move applied wallpapers off the framing the user chose, and users explicitly ask for a lock; Aura's crop and editor work is undone by it.
  Evidence: WallYou #289 ("Force the wallpapers to be non-movable"), darkmodewallpaper #87 (14 comments), #218, WallFlow #25, doodle-android #93; `WallpaperApplier.kt`.
  Touches: `WallpaperApplier.kt`, live-wallpaper engines' `onOffsetsChanged`, settings toggle, string resources.
  Acceptance: an opt-in setting applies wallpapers sized so the launcher cannot pan or zoom them, live engines ignore offset changes when it is on, and the behavior is documented as launcher-dependent where the platform cannot guarantee it.
  Complexity: M

- [ ] P3 — Add a user-supplied URL or self-hosted wallpaper source
  Why: Aura has eight third-party feeds and no way for a user to point it at their own — no WebDAV, no SMB, no arbitrary URL. For a local-first app whose charter is not depending on anyone's marketplace, that is the missing source, and it is the only one that cannot rot, rate-limit, or change its terms.
  Evidence: no WebDAV, SMB, or custom-endpoint client under `data/remote/`; `ProviderCapability.kt` already models `LOCAL` and `ProviderConfiguration.REQUIRED_KEY`, so the policy layer can express it; cssnr/remote-wallpaper-android; WallFlow #113 ("Reddit stopped working", open, in an app whose maintainer stopped pushing in 2024) is the counter-example.
  Touches: a new provider client and repository, `ProviderCapability.kt`, `ProviderDisclosure.kt`, `ProviderNetworkPolicy.kt`, `tools/network_endpoint_inventory_check.py`, settings UI, tests.
  Acceptance: a user can register one or more HTTPS endpoints returning an image or an image list, with optional basic auth stored through `ProviderCredentialStore`; the source is opt-in, off by default, declared in the disclosure layer so its provenance is recorded, and cleartext is refused; failure states are visible and per-endpoint; a test covers a single image, a listing, an unreachable host, and a non-image response.
  Complexity: M

- [ ] P3 — Narrow the R8 keep rules
  Why: nine wildcard keeps preserve entire packages — including Aura's whole network layer — that the libraries' own consumer rules already cover, which defeats obfuscation of the app's own DTOs and adds dex the shrinker could remove. Small next to the native payload, but free.
  Evidence: `app/proguard-rules.pro:2-3` keeps `com.freevibe.data.remote.**` and all its members; `:9,25,26,29-32` do the same for `retrofit2`, `org.schabi.newpipe.extractor`, `org.mozilla.javascript`, `com.yausername`, `org.apache.commons.compress`, `org.apache.commons.io`; Retrofit, Moshi, and commons-* all ship consumer rules; Moshi KSP codegen needs only the generated adapters kept.
  Touches: `app/proguard-rules.pro`, release verification.
  Acceptance: each remaining keep names a class or a narrow member set with a comment stating what breaks without it; a release build passes the JVM suite, the Roborazzi suite, and a manual pass over every provider; dex method count and APK size before and after are recorded.
  Complexity: S

- [ ] P3 — Record the ML Kit dependency risk and decide a fallback
  Why: parallax wallpapers, smart crop, and depth portraits rest on a Play-services beta artifact published 2023-11-06 and never promoted to stable. If it is withdrawn or crashes, three advertised features stop working in the `full` flavor — and they are already absent from `foss`, which the README feature table does not mention, in the very artifact IzzyOnDroid would ship.
  Evidence: `app/build.gradle.kts:345-349` pins the `play-services-mlkit-subject-segmentation` beta as `fullImplementation` with a comment noting no bundled artifact exists; `SmartCropDetector.kt`, `DepthPortraitComposer.kt`, `ParallaxWallpaperService.kt`; `app/src/foss/java/com/google/mlkit/vision/segmentation/subject/SubjectSegmentation.kt` is a stub; README's feature table does not distinguish the flavors.
  Touches: `docs/distribution/` (a dependency-risk record), README feature table, `tools/fdroid_preflight.py`, `SmartCropDetector.kt`, `DepthPortraitComposer.kt`, `ParallaxWallpaperService.kt`.
  Acceptance: a record names the artifact, its 2023 publish date, the three features that depend on it, and the chosen response if it is withdrawn or crashes; all three features degrade visibly rather than silently when segmentation is unavailable, and tests cover those paths; the README states which features the FOSS build omits; the preflight asserts the README statement matches the `foss` source set.
  Complexity: S

  Note 2026-08-23: upstream issue googlesamples/mlkit#1017 reports an uncatchable API 36 SIGSEGV in the exact beta1 artifact. Before choosing a fallback, run the full release build through `SmartCropDetector`, `DepthPortraitComposer`, and `ParallaxWallpaperService` on API 36; if reproduced, prevent inference on affected devices until a patched artifact or tested replacement is available. Confidence: Needs live validation in Aura.

- [ ] P3 — Ship a haptic pattern alongside a ringtone
  Why: Android 16 added envelope-based vibration builders that describe amplitude and frequency curves and abstract away device capability, and Aura already owns both the sound editor and the apply path. Current compileSdk 36 exposes the APIs, so the remaining work is device-capability fallback and integration.
  Evidence: no `VibrationEffect`, `BasicEnvelopeBuilder`, or `WaveformEnvelopeBuilder` anywhere in `app/src/main/java`; `SoundApplier.kt` and `ContactRingtoneService.kt` are the apply surfaces; developer.android.com custom-haptic-effects.
  Touches: `SoundApplier.kt`, `SoundEditorScreen.kt`, `ContactRingtoneService.kt`, `PreferencesManager.kt`, theme-pack recipe schema, string resources.
  Acceptance: a small preset set of vibration patterns can be previewed in the editor and stored with a sound; the pattern is applied where the platform allows and the limitation is stated where it does not; devices without envelope support fall back to a simple waveform and say so; patterns round-trip through theme-pack export and import.
  Complexity: M

- [ ] P3 — Claim the distribution and discovery surfaces that are currently empty
  Why: Aura is the highest-starred FOSS Android ringtone project under GitHub `topic:ringtone`, a topic that is nearly empty, and the F-Droid ringtone shelf holds one abandoned fork. It is on no awesome-list, and `offa/android-foss` has a one-entry wallpaper section and no live-wallpaper or ringtone section at all. This is the cheapest reach available and it needs no code.
  Evidence: `offa/android-foss` wallpaper section lists one app; `vvolas/Awesome-Live-Wallpaper` is Android-specific and dead since 2016; `w3teal/awesome-ringtone` does not list Aura; F-Droid's RFP queue shows live unserved wallpaper demand.
  Touches: no app code; README topics, external PRs, `docs/distribution/channel-strategy.md`.
  Acceptance: `docs/distribution/channel-strategy.md` records which lists were submitted to and when, with links; GitHub topics are set; submissions happen only after the Fastlane-image, signing-transparency, and reproducibility prerequisites in this roadmap are complete; an IzzyOnDroid inclusion request waits for the owner decision recorded in `Roadmap_Blocked.md`.
  Complexity: S

- [ ] P3 — Restart the rotation countdown on manual wallpaper changes
  Why: a manual apply does not touch the periodic schedule (`ExistingPeriodicWorkPolicy.UPDATE` keeps the existing cadence and the apply coordinator never reschedules), so rotation can overwrite a user's deliberate choice moments after they made it — a documented complaint class in Paperize.
  Evidence: `WallpaperApplyCoordinator.kt` (no rescheduling); `AutoWallpaperWorker.kt:307` (`ExistingPeriodicWorkPolicy.UPDATE`); Paperize #591.
  Touches: `WallpaperApplyCoordinator.kt`, `AutoWallpaperWorker.kt` scheduling companion, settings copy, tests.
  Acceptance: a manual apply from any surface (detail, shuffle, widget, tile, external broadcast) restarts the rotation countdown, governed by an on-by-default "restart timer on manual change" setting; rotation diagnostics show the recomputed next-fire time; a test proves the next fire moves after a manual apply and does not move when the setting is off.
  Complexity: S

- [ ] P3 — Add Undo and Skip actions to the rotation notification
  Why: the daily-rotation notification is display-only, so recovering from an unwanted rotated wallpaper requires opening the app, finding history, and undoing — while Aura already owns a working undo path; Peristyle and Paperize both ship notification-level controls.
  Evidence: `DailyWallpaperWorker.kt` thumbnail notification with no actions; existing undo via `WallpaperHistoryManager`/`ApplyFeedbackBus`; Peristyle 9.7.5 delete-from-notification; Paperize pause/resume.
  Touches: `DailyWallpaperWorker.kt`, `AutoWallpaperWorker.kt`, a notification action receiver, `WallpaperHistoryManager.kt`, string resources, tests.
  Acceptance: the rotation notification offers Undo (restores the previous wallpaper through the existing history path) and Skip/Next; actions work with the app process dead; the notification can be silenced per channel without disabling rotation; tests cover undo-restores-previous and skip-advances.
  Complexity: M

- [ ] P3 — Offline procedural wallpaper generator
  Why: Tapet's entire paid differentiator is offline procedural generation at exact screen resolution with palette control; Aura owns palette extraction, Material You seeds, an AGSL pipeline, and rotation, so a deterministic on-device generator neutralizes it while fitting the charter exactly (offline, no AI, no provider). Distinct from the rejected R-1 AI generation: no model, no network, reproducible from a seed.
  Evidence: Tapet Play listing (premium palettes/patterns); Waller gradient generator and Shader Editor demand on F-Droid; `ColorExtractor`/`WallpaperPalette`, `AgslShaderGallery.kt`, and the rotation source picker as existing infrastructure.
  Touches: a new generator service (pattern families seeded by palette + RNG seed), `ContentSource` enum, WallpapersScreen entry point, rotation source picker, `ProviderDisclosure.kt` (local provenance), tests.
  Acceptance: users generate wallpapers offline at exact screen resolution from a chosen palette (including the current Material You palette) and pattern family, then save/apply/favorite them; a "Generated" rotation source produces a fresh image per rotation with no network; output carries provenance metadata distinct from AI and provider content; generation is deterministic given a seed, and tests assert determinism and resolution.
  Complexity: L

## Research-Driven Additions — 2026-09-04

Evidence for every item below is in RESEARCH.md (2026-09-04 pass).

### P1

- [ ] P1 — Validate release assets and digests in the publication gate
  Category: testing
  Where: tools/published_state.py:91-125; tools/release_publication_check.py:69-88; test/tools/release_publication_check_test.py:44-94; tools/release_artifact_bundle_check.py:21-35,227-320; docs/distribution/release-signing.md:48-63
  Problem: Publication checks only whether a GitHub tag exists. They neither inventory nor validate assets, and an unknown remote state can still report ok, so an incomplete release passes.
  Evidence: v6.45.3 is now published with five APKs and SHA256SUMS.txt, disproving the stale P0 publication-gap entry. The checker exits zero without fetching assets, while the documented bundle contract also names an AAB, notices, raw OSS inputs, native reports, notes, and verification receipts. Current tests mock tag existence only.
  Fix: Define the deliberate public asset contract, fetch names and GitHub digests, compare them with the checksum manifest, and add a strict online mode where unknown state fails. Keep owner-only evidence private only if documentation explicitly separates it from public assets.
  Acceptance: Missing, wrong-version, duplicate, and digest-mismatched fixture assets fail; unknown remote state cannot be publication verified in strict mode; the live release passes only after its public assets match the declared contract.
  Confidence: Verified
  Effort: M

- [ ] P1 — Move to targetSdk 36
  Why: Play has required targetSdk 36 for new apps and updates since 2026-08-31 and Accrescent removes apps that miss the target-SDK bar rather than hiding them, so targetSdk 35 closes two of the three stores Aura's distribution docs plan for. The work is small because the behavior changes are already satisfied, and it is not blocked: `Roadmap_Blocked.md` blocks targetSdk 37, which needs compileSdk 37 and an AGP beyond 8.9.3.
  Evidence: `app/build.gradle.kts:81,95` (compileSdk 36, targetSdk 35, with an in-file comment explaining the split); Play target API requirements (https://support.google.com/googleplay/android-developer/answer/11926878); Accrescent publishing requirements (https://accrescent.app/docs/guide/publish/requirements.html); `AndroidManifest.xml:64` sets `android:enableOnBackInvokedCallback="true"`, `MainActivity.kt:257` calls `enableEdgeToEdge()`, the manifest declares no `screenOrientation`, `resizeableActivity`, or `maxAspectRatio`, and no `onBackPressed()` override exists — the seven `BackHandler` uses are scoped selection and unsaved-changes guards.
  Touches: `app/build.gradle.kts`, `docs/distribution/release-metadata-consistency.json`, `tools/manifest_consistency_check.py`, `docs/security/target37-compatibility.json`, `README.md`, Roborazzi route fixtures.
  Acceptance: `targetSdk = 36` builds, all gates pass, and every route renders correctly edge-to-edge on an API 36 image with insets applied and no content trapped behind the status or navigation bar; predictive back animates out of every top-level destination and the unsaved-changes guards still intercept; a large-screen or foldable configuration change does not lose editor state; `docs/security/target37-compatibility.json` records the 36 milestone so the armed 37 gate reads the right baseline.
  Complexity: M

- [ ] P1 — Bound what reaches the bundled FFmpeg 7.1.1
  Why: the shipped FFmpeg predates the fix for a heap out-of-bounds write reachable from a crafted media file, and it cannot be upgraded independently because it arrives inside `ffmpeg-0.18.1.aar`. Removing FFmpeg entirely is already queued at P2 and is L-sized; this bounds the exposure now.
  Evidence: `docs/legal/native-compliance.lock.json:364,428,492` records `"FFmpeg version": "FFmpeg version 7.1.1"` for all four ABIs with `--enable-gpl --enable-version3` and no codec exclusions; CVE-2026-8461 ("PixelSmash") affects FFmpeg before 8.1.2 and triggers on an odd `slice_height` in a crafted AVI, MKV, or MOV parsed by the MagicYUV decoder; user-chosen local video reaches FFmpeg through `VideoCropScreen.kt`, and remuxed yt-dlp output reaches it through `AudioTrimmer.kt` and `VideoWallpapersViewModel.kt`.
  Touches: `app/src/main/java/com/freevibe/service/MediaIngestion.kt`, `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoCropScreen.kt`, `app/src/main/java/com/freevibe/service/AudioTrimmer.kt`, a new `docs/security/ffmpeg-exposure.json`, `test/tools/`.
  Acceptance: every FFmpeg invocation is preceded by a container and codec check that rejects anything outside a declared allowlist, and MagicYUV is not on it; Media3 handles the crop path wherever it already can, with FFmpeg reached only on a recorded fallback; a rejected file produces a clear message rather than a silent failure; a policy JSON records the bundled FFmpeg version and its known-unfixed advisories, and a gate fails when the version in the native-compliance lock changes without the policy being reviewed.
  Complexity: M

- [ ] P1 — Make post-boot ringtone restoration prove it worked
  Why: the receiver reports success no matter what happens, so a device where restoration silently fails is indistinguishable from one where it worked, and "my ringtone reset itself" is a recurring, vendor-acknowledged complaint in this category with no diagnostic path in any app.
  Evidence: `RingtoneRestorationReceiver.kt:72-77` catches everything and returns `Result.success()` with a comment describing it as best-effort; Google acknowledged a Pixel-side reset bug (https://9to5google.com/2022/05/06/google-pixel-ringtone-bug/) and users still report resets in 2026 (https://old.reddit.com/r/AndroidQuestions/comments/1vkjdd5/); `BootObservationStore.recordBoot` already exists in the same receiver for the rotation side, so the recording pattern is in place.
  Touches: `app/src/main/java/com/freevibe/service/RingtoneRestorationReceiver.kt`, `app/src/main/java/com/freevibe/service/SoundApplier.kt`, the Rotation Health or Diagnostics surface, string resources, tests.
  Acceptance: each of the three sound types records its restoration outcome (restored, already correct, source missing, write refused) with a timestamp; a failed restoration returns `Result.retry()` or `Result.failure()` rather than success; Diagnostics shows the last restoration result per type; tests cover a missing source URI, a `SecurityException` on write, and a successful no-op.
  Complexity: M

### P2

- [ ] P2 — Detect the per-contact ringtone assignments that will not ring
  Why: a custom contact ringtone silently reverting to the default is the most recurrent complaint in this product category, the update call succeeds when it happens, and no app on the market detects it. Aura already writes the correct table, which puts it one read-back away from being the only app that tells the truth about this. Distinct from the tracked OEM ringtone-write item: that one classifies a thrown exception on the system default; this one has no exception to catch, because the write succeeds and the wrong contact rings.
  Evidence: Google's own support thread is locked with 159 "same question" reports and no working answer (https://support.google.com/phoneapp/thread/221331571/); it recurs across Pixel 4a, 6a, 7 Pro, and 8 Pro (https://old.reddit.com/r/GooglePixel/comments/18eqrae/, https://old.reddit.com/r/AndroidQuestions/comments/17j9cdp/); users independently identified the same cause three times — the contact was copied from another phone, is stored on the SIM, or is an unlinked duplicate, so the row that received `custom_ringtone` is not the row the incoming call resolves against (https://old.reddit.com/r/GooglePixel/comments/1fgdg84/, https://old.reddit.com/r/AndroidQuestions/comments/m4gden/); `ContactRingtoneService.kt:141-155` writes the aggregate `Contacts` URI, which is right, and only checks that one row changed, which the failure mode satisfies.
  Touches: `app/src/main/java/com/freevibe/service/ContactRingtoneService.kt`, `app/src/main/java/com/freevibe/ui/screens/sounds/ContactPickerScreen.kt`, string resources in `values` and `values-zh`, tests.
  Acceptance: after writing, the service re-reads `CUSTOM_RINGTONE` for the contact and reports a mismatch as a failure rather than a success; before writing, it queries the contact's raw contacts and warns when any of them is on a SIM account or when the aggregate has raw contacts from more than one account, naming the fix (move the contact to the device account, or link the duplicates); tests cover a clean write, a write that reads back empty, a SIM-account raw contact, and a multi-account aggregate.
  Complexity: M

- [ ] P2 — Back up and restore contact-to-ringtone assignments
  Why: ringtone assignments disappear on reset, restore, and device change, and the only existing remedy anywhere is a hand-written Tasker script. Aura already owns the backup format, so this is a differentiator no competitor has.
  Evidence: https://old.reddit.com/r/tasker/comments/sbz15u/ documents the schema people rebuild by hand (`custom_ringtone, display_name` from `content://com.android.contacts/contacts`) and the trap — "Do not save the actual `_id` of the Contract or of the Ringtone File, because after a device reset… We change device"; `LibraryExporter.kt` and `ImportPayloadValidation.kt` already carry favorites, collections, searches, wallpaper packs, and sound profiles; `docs/data/export-format.json` defines the schema.
  Touches: `app/src/main/java/com/freevibe/service/LibraryExporter.kt`, `app/src/main/java/com/freevibe/service/ContactRingtoneService.kt`, `app/src/main/java/com/freevibe/service/LibraryImportPlan.kt`, `app/src/main/java/com/freevibe/service/ImportPayloadValidation.kt`, `docs/data/export-format.json`, `tools/export_format_check.py`, tests.
  Acceptance: an export records each assignment by contact lookup key and display name plus the sound's own identity, never a raw contact `_id` or a MediaStore row id; an import matches contacts by lookup key, falls back to display name, and reports every entry it could not match instead of failing the whole import; the export-format gate covers the new section; tests cover a matched restore, a changed lookup key, a missing contact, and a missing sound.
  Complexity: M

- [ ] P2 — Remove dormant legacy sound providers while preserving saved attribution
  Category: maintainability
  Where: app/src/main/java/com/freevibe/data/repository/AudiusRepository.kt, CcMixterRepository.kt, SoundCloudRepository.kt, FreesoundRepository.kt, FreesoundV2Repository.kt; app/src/main/java/com/freevibe/di/AppModule.kt:109-117,179-227; app/src/test/java/com/freevibe/ui/screens/sounds/SoundsViewModelTest.kt:1461-1572; ROADMAP.md and Roadmap_Blocked.md provider entries
  Problem: Five repository implementations and their Hilt clients have no production caller, yet tests carry inert mocks and release R8 retains about 48 KB of abandoned provider DTO/API code. The old roadmap and blocked roadmap also disagree on whether they are actionable.
  Evidence: Repository-wide reference tracing finds no production construction or injection. SoundsViewModel tests stub all five but pass none to the ViewModel. The product direction is now explicit: preserve Reddit-first discovery and YouTube sound/video, so the earlier provider-choice blocker is resolved.
  Fix: Remove all five repositories, Retrofit providers, obsolete credentials/endpoints, and inert test parameters. Keep ContentSource enum values and legacy attribution parsing so saved rows remain readable. Update provider disclosure to describe only live sources.
  Acceptance: No abandoned endpoint has a repository/provider; sound tests construct only real dependencies; release APK contains none of those packages; legacy saved metadata still shows correct attribution; Reddit remains the primary media source and YouTube remains available for sound/video.
  Confidence: Verified
  Effort: M

- [ ] P2 — Reach or remove the three unreachable surfaces beyond the two already tracked
  Why: the wallpaper-pack and sound-profile editors are already tracked, but the same defect class has three more instances that nothing records, and one of them silently disables a documented setting.
  Evidence: `PreferencesManager.kt:342` `setAutoWallpaperTarget` has no caller anywhere, so `AutoWallpaperWorker.kt:144` always reads the `"BOTH"` default from `PreferencesManager.kt:308` and the rotation home/lock target cannot be changed by any user action; `Screen.kt:215-227` `Screen.VideoWallpaperPreview` is registered as a destination in `FreeVibeRoot.kt:738-756` but `createRoute` is never called and no literal navigation to that route exists; `ThemePackRecipeManager.kt:273,285` is the only non-import writer of the two tracked pack and profile stores, so a theme pack can populate a feature the user cannot otherwise reach or edit.
  Touches: `app/src/main/java/com/freevibe/data/local/PreferencesManager.kt`, `app/src/main/java/com/freevibe/ui/screens/settings/SettingsRotationDelegate.kt`, `app/src/main/java/com/freevibe/ui/navigation/Screen.kt`, `app/src/main/java/com/freevibe/ui/FreeVibeRoot.kt`, `tools/`, tests.
  Acceptance: the rotation target is either settable from Settings with the three states the worker already understands, or the preference and its read are removed and the worker's behavior documented as fixed; `Screen.VideoWallpaperPreview` is either reachable from the video feed or removed along with its destination; a gate fails when a `Screen` object is registered as a destination with no `createRoute` call site, and when a `PreferencesManager` setter has no caller outside tests and importers.
  Complexity: M

- [ ] P2 — Pool and preload the video-feed player
  Why: every scroll stop in the video feed constructs, prepares, and releases an ExoPlayer, so the user sees "Preparing preview" on each card instead of a preview that is already warm. Media3 1.11.0 is already pinned and ships the pieces for this exact pattern.
  Evidence: `VideoWallpapersScreen.kt:952` builds `ExoPlayer.Builder(context)…prepare(); play()` inside `remember(item.id, streamUrl)` and releases it in `onDispose`; only one card previews at a time (`VideoWallpapersScreen.kt:569`, `activePreviewId`); Media3 1.11.0 added `PlayerPool` and `rememberPooledPlayer` in `media3-ui-compose` for preloading in a sliding-window UI, plus `DefaultPreloadManager` and `ExoPlayer.Builder.enablePerStreamMediaProgression()` (https://developer.android.com/jetpack/androidx/releases/media3).
  Note 2026-09-25: pin Media3 1.11.1 before implementing the pool. Its 2026-09-10 fixes cover secondary-renderer prewarming stalls, `Surface` ownership after seek reset, stale seek frames, and fully consumed HLS chunks retrying after `EOFException`, all of which intersect this feed.
  Touches: `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt`, `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt`, `gradle/libs.versions.toml` (add `media3-ui-compose`), `gradle/verification-metadata.xml`, `baselineprofile/`.
  Acceptance: a pooled player is reused across cards instead of being rebuilt per item, and the next item in scroll order is preloaded through `DefaultPreloadManager`; the pool is bounded and every player is released when the feed leaves composition, verified with the same retention assertion style the live-wallpaper soak already uses; time from scroll-stop to first frame is measured before and after and recorded; the existing preview-unavailable and playback-error states still render.
  Complexity: M

- [ ] P2 — Take Glance 1.2.0 stable, Coil 3.6.2, and OkHttp 5.5.0
  Why: the Glance pin carries a written upgrade trigger that has now fired, and the app is shipping a release-candidate widget stack to users. The other two are post-refresh drift and are small enough to take in the same pass. The Kotlin, AGP, Compose, and Room lines stay out of scope; they are blocked behind the N-1 toolchain triad.
  Evidence: `gradle/libs.versions.toml:22-25` pins `glance = "1.2.0-rc01"` with the comment "A 1.2.0 stable has not shipped… Revisit when the generated-preview API reaches stable"; Glance 1.2.0 stable published 2026-08-26 (https://developer.android.com/jetpack/androidx/releases/glance); Coil 3.6.2 published 2026-09-04, with `allowPartialImage` added in 3.6.0 (https://coil-kt.github.io/coil/changelog/); OkHttp 5.5.0 supersedes the pinned 5.4.0.
  Touches: `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`, `app/src/main/java/com/freevibe/widget/`, widget tests, Roborazzi baselines.
  Acceptance: all three versions move, dependency verification entries are built from the upstream-published `.sha256` rather than the local cache, the widget's generated preview still publishes, `:app:testFullDebugUnitTest` and `:app:testFossDebugUnitTest` are green, the Roborazzi gate passes, and the prerelease-pin comment is replaced with the stable pin.
  Complexity: S

- [ ] P2 — Strip the dependency-info blob from release artifacts
  Why: the APK signing block carries a Google-encrypted dependency payload that IzzyOnDroid's scanner flags and that works against byte-for-byte reproducibility, which is the submission Aura's distribution docs are aiming at.
  Evidence: `dependenciesInfo` appears nowhere in `app/build.gradle.kts`, so AGP's default is in effect; IzzyOnDroid's APK checks list `DEPENDENCY_INFO_BLOCK` among the signing-block BLOBs it reports (https://android.izzysoft.de/articles/named/iod-scan-apkchecks); `tools/foss_reproducibility_check.py` compares signature-stripped archives, so the blob is a live variable in that comparison.
  Touches: `app/build.gradle.kts`, `tools/foss_reproducibility_check.py`, `docs/distribution/supply-chain.md`, `test/tools/`.
  Acceptance: `dependenciesInfo { includeInApk = false; includeInBundle = false }` is set, a freshly built release APK's signing block contains no dependency-info entry, the reproducibility check still passes, and a gate fails if the setting is removed.
  Complexity: S

- [ ] P2 — Run the API 35 half of the device-blocked backlog
  Why: two items sit in `Roadmap_Blocked.md` under "Blocker: Physical Device / Emulator" purely for want of an instrumentation target, and an emulator that can run both was available on this machine.
  Evidence: `Roadmap_Blocked.md` blocks the Room 1→8 migration chain because `MigrationTestHelper` is instrumentation-only, and records the resume recipe — `createVersion1Database()` built the way `createVersion8Database()` already is, then `runMigrationsAndValidate(TEST_DB, 16, true, *DatabaseMigrations.ALL_MIGRATIONS)`; `adb devices -l` returned an Android 15 / API 35 `x86_64` emulator on 2026-09-04 and `app/build.gradle.kts:201` keeps `x86_64` in the split set; `docs/distribution/native-alignment.json` records `AudioTrimmerInstrumentedTest` already running on an API 35 x86_64 emulator.
  Touches: `app/src/androidTest/java/com/freevibe/data/local/DatabaseMigrationTest.kt`, `app/schemas/`, `Roadmap_Blocked.md`, `docs/qa/`.
  Acceptance: the migration chain from a hand-authored version 1 schema through to the current version runs and passes on an API 35 emulator, with the run recorded in `docs/qa/` including the AVD image and the date; the items are removed from the device blocker or, if they genuinely still need hardware, the blocker entry names the API level and the capability that is missing rather than "a device".
  Complexity: M

### P3

- [ ] P3 — Preserve Ultra HDR gainmaps through the wallpaper transform path
  Why: an HDR wallpaper survives only when Aura does nothing to it. Any night variant, clock overlay, editor pass, or download quietly flattens it to SDR on a device that could have displayed it, and the user is never told.
  Evidence: `WallpaperApplier.kt:110` streams the encoded source through `setStream` when no transform is needed, which preserves a gainmap; `:94` and `:167` call `setBitmap` with bitmaps built as `Bitmap.Config.ARGB_8888` at `:439` and `:459`; `:267` re-encodes downloads as JPEG at quality 94; `grep -r Gainmap app/src/main/java` returns nothing; `Bitmap.getGainmap()` and `setGainmap()` are available from API 34.
  Touches: `app/src/main/java/com/freevibe/service/WallpaperApplier.kt`, `app/src/main/java/com/freevibe/service/MediaIngestion.kt`, `app/src/main/java/com/freevibe/ui/screens/editor/WallpaperEditorViewModel.kt`, string resources, tests.
  Acceptance: on API 34 and above, a source that carries a gainmap keeps it through the night-variant and clock-overlay paths by carrying the gainmap onto the result bitmap; where a transform cannot preserve it, the user is told the wallpaper will be applied in SDR before it happens; downloads of a gainmap source are written without re-encoding; tests assert gainmap presence before and after each transform on an API 34+ fixture.
  Complexity: M

- [ ] P3 — Share the media file, not only its source URL
  Why: sharing a wallpaper or a sound currently sends a link the recipient has to open, which is the least useful half of the action, and the app already contains the pattern that would fix it.
  Evidence: `WallpaperDetailScreen.kt:624-629` and `SoundDetailScreen.kt:563,581` build `Intent(ACTION_SEND)` with `type = "text/plain"` and only the source page URL; `CollectionExporter.kt:130-138` already builds an `ACTION_SEND` with a FileProvider URI and `FLAG_GRANT_READ_URI_PERMISSION`.
  Touches: `app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt`, `app/src/main/java/com/freevibe/ui/screens/sounds/SoundDetailScreen.kt`, `app/src/main/java/com/freevibe/service/CollectionExporter.kt` (extract the shared helper), `res/xml/file_paths.xml`, string resources, tests.
  Acceptance: sharing offers the file itself when a local copy exists and falls back to the link when it does not, with attribution text preserved in both cases; the intent carries `FLAG_GRANT_READ_URI_PERMISSION` and a correct MIME type; nothing outside the app's own FileProvider paths is ever exposed; tests cover a downloaded item, a not-yet-downloaded item, and a chooser with no target.
  Complexity: S

- [ ] P3 — Trim leading silence and normalise loudness on sound export
  Why: OEM ascending-ring ramps and a two-to-three second start cut-off mean a ringtone with lead-in silence is effectively inaudible for its first seconds, and users cannot fix it themselves because the editor exports at the source's own level.
  Evidence: start cut-off reported across devices (https://old.reddit.com/r/AndroidQuestions/comments/1bmv0x2/, https://old.reddit.com/r/GooglePixel/comments/1imtw5d/); loudness is the most-repeated missing feature in the category's editor reviews — "no facility to increase the volume of the cut piece… Please add a normalizer"; `AudioTrimmer.kt` already owns Media3 clipping, PCM fades, and pitch-preserving speed, so the transform stage exists.
  Touches: `app/src/main/java/com/freevibe/service/AudioTrimmer.kt`, `app/src/main/java/com/freevibe/ui/screens/editor/SoundEditorScreen.kt`, `app/src/main/java/com/freevibe/ui/screens/editor/SoundEditorViewModel.kt`, string resources, the `docs/distribution/native-alignment.json` fixture corpus, tests.
  Acceptance: the editor offers optional leading-silence trim and loudness normalisation, both off by default and both shown in the waveform before export; normalisation targets a stated level and never clips; a fixture with two seconds of digital silence exports with the silence removed and its first audible sample at the trim point; a quiet fixture exports within a stated tolerance of the target level; existing verified-lossless cuts still bypass both transforms.
  Complexity: M

- [ ] P3 — Move network and file IO out of the two largest ViewModels
  Why: the two biggest ViewModels in the app are big because they carry a repository's worth of IO inline, which is also why the one with the most complex loader cannot be split and why neither has meaningful test coverage of its network paths. This is the prerequisite for the `VideoWallpapersViewModel` delegate split held in `Roadmap_Blocked.md`, not a duplicate of it: that item is blocked because `load()`, its per-source fetch orchestration, and its cancellation ownership are unverifiable, and extracting the IO behind a fake client is what makes them testable.
  Evidence: `SoundEditorViewModel.kt` injects `OkHttpClient` at line 136 and runs `newCall(request).execute()` plus `FileOutputStream(tmpFile)` inline at lines 655-718; `VideoWallpapersViewModel.kt` injects `OkHttpClient` at line 434 with raw `.execute()` at lines 747, 763, and 1198 and raw `java.io.File` handling at line 709; they are 929 and 1,339 lines.
  Touches: `app/src/main/java/com/freevibe/ui/screens/editor/SoundEditorViewModel.kt`, `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt`, new repository types under `app/src/main/java/com/freevibe/data/repository/`, `app/src/main/java/com/freevibe/di/AppModule.kt`, tests.
  Acceptance: neither ViewModel injects `OkHttpClient` or touches `java.io.File` directly; the extracted repositories are covered by tests exercising success, HTTP failure, and cancellation against a fake client; both ViewModels' existing behaviour is unchanged, proven by their current tests passing untouched; a gate fails when `OkHttpClient` is injected into anything under `ui/`.
  Complexity: L

## Audit Findings — 2026-09-13

### P1

- [ ] P1 — Accept valid fragmented Reddit MP4s without weakening short-video validation
  Category: correctness
  Where: app/src/main/java/com/freevibe/data/repository/RedditRssParser.kt:3-12; app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt:94-99,616,784-805; app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt:667-724,1169-1235; app/src/main/java/com/freevibe/service/VideoWallpaperStorage.kt:29,79-83,159-211
  Problem: API 29 MediaMetadataRetriever reports duration 0 for a valid fragmented Reddit MP4, and Aura converts unknown duration into Selected video is too short. The first ranked Reddit card can download fully and then fail even though it is an eight-second playable loop.
  Evidence: On the current-run API 29 emulator, Reddit item Waves downloaded exactly 1,111 KiB and failed at VideoWallpaperStorage.kt:199. Its source post 1wcnmwo resolves to an MP4 with two four-second CMAF fragments; ffprobe reports H.264, 394x854, and 8.0 seconds. RSS carries no duration, Apply does not gate it, yt-dlp remux can leave fragmented MP4 unchanged, and probeVideoFile() maps missing duration to zero. The next Reddit item applied and animated successfully, isolating this to metadata probing.
  Fix: Represent duration as known versus unknown. Reject only a known positive duration below 1,000 ms, use Media3 or MediaExtractor when retriever returns zero, and force Reddit HLS through a conventional fast-start MP4 stream-copy rewrap before final validation. Keep corrupt/truncated/zero-sample rejection. Optional manifest/preview metadata may disable genuinely sub-second items before download.
  Acceptance: The captured Waves fixture applies on API 29 and persists at about eight seconds; a genuine 250 ms video still fails; corrupt and zero-sample fixtures fail; API 26, 29, and current instrumentation covers fragmented and conventional MP4 plus cleanup and size caps.
  Confidence: Verified
  Effort: M

- [ ] P1 — Verify community upload objects server-side before publication
  Category: security
  Where: functions/src/wallpaperUploadHandler.ts:99-110,192-249,540-578; functions/src/soundUploadHandler.ts:100-111,193-238,493-527; storage.rules:19-42
  Problem: Finalization trusts client-supplied storagePath, HTTPS URLs, MIME type, size, and wallpaper dimensions. An authenticated and App-Checked caller can publish a nonexistent owner-shaped object path, an unrelated HTTPS URL, or false media metadata.
  Evidence: Both handlers validate syntax and ranges, then publish the submitted values. Neither backend exposes a Storage lookup and neither Firebase implementation calls getStorage(), bucket(), or file(). Accepted-upload tests publish through fakes with no stored object. Storage rules validate initial creation but cannot bind later finalizer claims to the actual object.
  Fix: Read Admin Storage metadata for the exact authenticated-owner object before reserving quota or publishing. Verify existence, bucket/object identity, canonical URL, content type, byte size, and decoded media properties. Delete or quarantine mismatches.
  Acceptance: Function tests reject a missing object, wrong owner path, mismatched URL, false size/MIME, and false dimensions; a matching object publishes; quota is not consumed for rejected claims.
  Confidence: Verified
  Effort: M

- [ ] P1 — Keep voter/follower identities private and restore aggregate queries
  Category: security
  Where: database.rules.json:3-19,208-212; functions/src/voteHandler.ts:270-312; app/src/main/java/com/freevibe/data/repository/VoteRepository.kt:420-433; app/src/main/java/com/freevibe/data/repository/CreatorProfileRepository.kt:359-374
  Problem: Public vote nodes and authenticated parent-readable follow nodes expose raw Firebase UIDs, while the app's own parent query for Top Voted and creator totals is denied. The UI then treats permission failure as an empty leaderboard and zero totals.
  Evidence: voteHandler writes the UID under two publicly readable trees. Anonymous Firebase sign-in makes the follow-tree barrier weak. RTDB does not inherit child grants upward, so both production /votes parent reads fail and catch to empty data. Upload metadata starts at votes: 0 and the vote handler does not maintain that fallback aggregate.
  Fix: Store public aggregate counts separately from private per-user markers. Permit each account to read only its own state, update aggregate nodes transactionally in callable functions, and point leaderboard/profile queries at that schema.
  Acceptance: Rules tests prove one user cannot enumerate another user's vote/follow markers; exact production query paths return nonzero fixtures; vote changes update counts atomically without exposing UIDs.
  Confidence: Verified
  Effort: L

- [ ] P1 — Replace seven-day semantic dedupe with operation identity
  Category: correctness
  Where: functions/src/quotaEngine.ts:3-5,142-159; functions/src/followHandler.ts:85-116,176-178; functions/src/blockHandler.ts:86-117,184-186; functions/src/profileHandler.ts:93-122,188-198
  Problem: Dedupe keys encode desired state for seven days. A valid A to B to A sequence reuses the first A marker and returns duplicate without applying the final state. Block, unblock, block can therefore report success while leaving the creator unblocked.
  Evidence: Follow and block compare current state first, then reserve a marker keyed only by target plus the desired action. Profile hashes the desired public payload. Tests cover same-state retries but no A/B/A sequence, so old A and B markers remain active.
  Fix: Deduplicate transport retries with a client operation ID or monotonic transition version. Store the operation result, not only the semantic target.
  Acceptance: Tests for follow/unfollow/follow, block/unblock/block, and profile A/B/A all finish in A; retrying one operation ID is idempotent; different IDs are never collapsed.
  Confidence: Verified
  Effort: M

- [ ] P1 — Count or reject every body in imported theme-pack archives
  Category: security
  Where: app/src/main/java/com/freevibe/service/ThemePackRecipeManager.kt:557-575,766-800; app/src/main/java/com/freevibe/service/ArchiveExtractionGuard.kt:97-151; app/src/test/java/com/freevibe/service/ThemePackArchiveExtractionTest.kt:112-126
  Problem: Asset bodies are capped, but directory-named and unrecognized ZIP entries are drained by closeEntry() outside byte and compression-ratio accounting. A small archive can force unbounded decompression and CPU work.
  Evidence: beginEntry() runs for each entry, but only recognized assets pass through copyZipEntryCapped() and commitEntry(). There is no guarded else drain or compressed input-size ceiling. Existing tests use empty directory and ignored entries.
  Fix: Reject unexpected nonempty entries immediately or drain every entry through the capped counter and commit it to total/ratio accounting. Add a compressed input ceiling before opening the archive.
  Acceptance: Nonempty directory-body and highly compressible ignored-entry fixtures fail within the declared budget; total expanded bytes include every accepted entry; a normal exported pack still round-trips.
  Confidence: Verified
  Effort: M

- [ ] P1 — Route toolbar back through editor unsaved-change guards
  Category: correctness
  Where: app/src/main/java/com/freevibe/ui/screens/editor/WallpaperEditorScreen.kt:147-193; app/src/main/java/com/freevibe/ui/screens/editor/SoundEditorScreen.kt:168-200,959-965; app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoCropScreen.kt:150-183,299-360,417-487; app/src/main/java/com/freevibe/ui/FreeVibeRoot.kt:732-736,960-966
  Problem: Wallpaper and Sound toolbar arrows bypass real dirty-state dialogs. Video Crop has no dirty guard at all, its toolbar also bypasses the active-export BackHandler, and normal system back can leave the parent route rather than close the crop overlay.
  Evidence: Wallpaper/Sound system BackHandler opens discard UI but toolbar calls raw popBackStack. Video Crop stores pan, zoom, trim, and smart-crop state locally; its only BackHandler is enabled during export, while the toolbar always calls onBack. Leaving composition can cancel result delivery. The screen is reached from the production video Apply flow.
  Fix: Define one requestExit() per editor. Route toolbar, system, predictive, and secondary back through it; keep busy editors mounted; add explicit dirty tracking to Video Crop and make clean back close only that overlay.
  Acceptance: Clean back leaves one level; dirty Wallpaper/Sound exits preserve data until explicit Discard; Video Crop adjustments cannot vanish silently; no back path dismisses active apply/export; tests dispatch toolbar and system back for all three.
  Confidence: Verified
  Effort: S

- [ ] P1 — Stop presenting persistent Hide as wallpaper apply actions
  Category: ux
  Where: app/src/main/res/values/strings.xml:1790; app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt:297-301,820-849,1114-1118,1158-1222; app/src/main/java/com/freevibe/ui/components/WallpaperStyleActions.kt:33-42,73-83; app/src/main/java/com/freevibe/data/repository/VoteRepository.kt:164-171,322-384
  Problem: A wallpaper card control announced as Show wallpaper apply actions performs the same destructive callback as Hide. It always removes the card and records a persistent negative taste signal; with community enabled it also persists a hidden ID, and an admin path can hide globally. Wallpaper offers no Undo.
  Evidence: WallpaperGrid assigns one closure to onLongPress and onDownvote. Both the mislabeled custom action and physical long click call it. The same closure calls skipWallpaper and conditionally downvote. Sounds/Videos wire undoDownvote, but Wallpaper shows a no-action snackbar; resetting taste does not clear hidden IDs.
  Fix: Split show-apply-actions from Hide, keep one correctly named local Hide, wire undoDownvote plus reversal of the taste signal, add durable hidden-wallpaper recovery, and keep admin moderation as an explicitly separate action.
  Acceptance: Show wallpaper apply actions opens choices and changes no feed/taste/vote state; Hide has immediate Undo; hidden media remains recoverable after restart; tests cover FOSS/community-off, regular community, and admin behavior.
  Confidence: Verified
  Effort: M

- [ ] P1 — Resolve YouTube sound streams only for the visible window
  Category: perf
  Where: app/src/main/java/com/freevibe/ui/screens/sounds/SoundBrowseQueries.kt:17-47; app/src/main/java/com/freevibe/ui/screens/sounds/SoundBrowseViewModel.kt:334-367,484; app/src/main/java/com/freevibe/ui/screens/sounds/SoundYouTubeActions.kt:202-255; app/src/main/java/com/freevibe/data/repository/YouTubeRepository.kt:228-339,449-473; app/src/main/java/com/freevibe/ui/screens/sounds/SoundPlaybackActions.kt:85-104; app/src/main/java/com/freevibe/service/AudioPreviewCache.kt:52-76
  Problem: Opening sound tabs can resolve up to 12 streams before a tap, and direct search can resolve all 30 results at concurrency six, followed by eight prebuffer jobs. This spends CPU, battery, and mobile data on unheard media.
  Evidence: The code launches three searches per category and resolves four from each; direct search maps every fallback result through extraction. Current-run API 29 logs showed ten lofi ringtone resolutions in about eight seconds before selection. Playback and apply worked, so this is eager fan-out rather than provider failure.
  Fix: Resolve only visible rows plus one lookahead through one bounded, cancellable queue keyed by query/tab. Resolve remaining items on visibility or tap.
  Acceptance: A 30-result test makes no more resolver calls than the visible budget; switching query cancels obsolete work; combined resolve/prebuffer concurrency stays bounded; first-play latency does not regress.
  Confidence: Verified
  Effort: M

- [ ] P1 — Repair the flavored dependency-notice compliance lane
  Category: docs
  Where: tools/dependency_notice_lock.py:44-46,120-134,278-280,324-326; docs/distribution/supply-chain.md:97-107; docs/legal/dependency-notices.lock.json; CONTRIBUTING.md:84-87
  Problem: The documented notice command and tool default target a nonexistent release flavor, the repair hint repeats that bad default, and the checked-in lock is materially stale.
  Evidence: The documented :app:releaseOssLicensesTask and default check fail because Aura has fullRelease/fossRelease. The fullRelease checker reports 136 added and 127 removed dependencies plus 13 added and six removed notice sections. CONTRIBUTING claims a mirrored test that does not exist.
  Fix: Require a variant or default to fullRelease, repair all commands/hints, regenerate the reviewed lock, and add fixture tests for flavored paths and drift.
  Acceptance: Every copied command exists and executes; current fullRelease check passes; changing a dependency fails a fixture; the Python suite directly tests the notice checker.
  Confidence: Verified
  Effort: M

- [ ] P1 — Pin the release runtime to JDK 21 and remove JBR 25 instructions
  Category: docs
  Where: docs/distribution/release-signing.md:52,71-74; docs/distribution/release-dry-run.md:74-78; docs/distribution/supply-chain.md:181-185; README.md:240-245; CONTRIBUTING.md:20-23; CLAUDE.md:22,26,32; app/build.gradle.kts:144-154
  Problem: Release runbooks select Android Studio JBR 25.0.2, which Gradle 8.12.1 cannot configure, while contributor docs correctly require JDK 21. Following release documentation stops before any build.
  Evidence: With the documented JBR, gradlew help --no-daemon failed in three seconds with What went wrong: 25.0.2. The same task and both serialized release builds passed with Adoptium JDK 21. The required runtime exists only in inconsistent prose, not a machine-readable declaration.
  Fix: Declare JDK 21 once through a Gradle toolchain or checked configuration, make every runbook consume it, and add a preflight/gate that rejects an unsupported runtime with a clear message.
  Acceptance: Every release command configures with the declared JDK; JDK 25 gets an immediate actionable preflight error; changing any documented major fails a fixture; Full and FOSS release builds pass serially.
  Confidence: Verified
  Effort: S

### P2

- [ ] P2 — Move shared-collection and deletion-ledger writes behind bounded backend operations
  Category: security
  Where: app/src/main/java/com/freevibe/service/CollectionExporter.kt:189-206; database.rules.json:96-111,240-249; test/firebase/database.rules.test.mjs:322-380; functions/src/communityContract.ts:35-157
  Problem: Clients can create unlimited bounded shared-collection nodes without quota, expiry, or App Check, and can fabricate owner-claimed deletion records for uploads that never existed. This enables storage/cost abuse and corrupts the audit trail.
  Evidence: CollectionExporter writes RTDB directly and no collection policy exists in the callable quota contract. The rules test creates a tombstone in an empty database and expects success. Existing ownership checks do prevent cross-account media deletion, so the issue is abuse and ledger integrity.
  Fix: Use callable operations with App Check, per-user rate/byte/count quotas, server timestamps, and TTL cleanup. Create deletion evidence only from backend-verified metadata and ownership.
  Acceptance: Rules tests reject fabricated tombstones and direct share writes; callable tests enforce quotas/expiry; valid owner shares and verified deletions succeed.
  Confidence: Verified
  Effort: M

- [ ] P2 — Reconcile quota reservations when the protected write fails
  Category: reliability
  Where: functions/src/quotaEngine.ts:119-134; functions/src/wallpaperUploadHandler.ts:130,168; functions/src/soundUploadHandler.ts:131,169; functions/src/voteHandler.ts:94,125; functions/src/followHandler.ts:101,133; functions/src/blockHandler.ts:102,134; functions/src/profileHandler.ts:108,139; functions/src/reportHandler.ts:133,166
  Problem: Each callable increments quota/cooldown before a separate action commit. A Firebase failure after reservation consumes a low daily allowance even though nothing was stored.
  Evidence: Accepted reservation writes count and lastAt, then each handler commits independently with no rollback, finalization state, or reconciliation. Tests do not inject commit failure after reservation.
  Fix: Model pending/finalized reservations with recovery, or compensate failed commits without weakening abuse limits. Replays must reuse operation identity.
  Acceptance: Forced commit failures remain retryable without counting as successful; stale pending reservations reconcile deterministically; a successful action consumes one unit.
  Confidence: Verified
  Effort: M

- [ ] P2 — Accept WebM community sounds consistently
  Category: correctness
  Where: app/src/main/java/com/freevibe/service/MediaIngestion.kt:404,439-467; app/src/main/java/com/freevibe/data/repository/UploadRepository.kt:46; functions/src/soundUploadHandler.ts:37; storage.rules:19
  Problem: Local ingestion and Storage rules accept WebM audio, but client upload validation and backend finalization omit it. A file accepted by Aura cannot complete upload.
  Evidence: MediaIngestion maps audio/webm and tests exercise it; storage.rules permits it. UploadRepository and soundUploadHandler use narrower allowlists.
  Fix: Define one supported sound MIME contract mirrored by client/server fixtures. Include WebM only after decode, preview, apply, and moderation paths pass.
  Acceptance: One WebM fixture ingests, uploads, finalizes, previews, downloads, and applies; unsupported MIME fails at the first boundary with matching messages.
  Confidence: Verified
  Effort: S

- [ ] P2 — Validate complete theme-pack contents before mutating local state
  Category: correctness
  Where: app/src/main/java/com/freevibe/service/ThemePackRecipeManager.kt:166,183-197,260-337,557-800
  Problem: Import validates the outer archive more strongly than nested recipes/media, then writes pieces as it proceeds. An invalid later entry can leave partial pack/profile state.
  Evidence: Supported pieces are parsed and persisted independently; inner recipes do not all use standalone validators, and the full operation has no staged transaction.
  Fix: Parse to an immutable staged model, validate every recipe/reference first, then commit preferences/database changes atomically. Delete staged files on failure.
  Acceptance: Bad final recipe, missing asset, duplicate slot, and invalid locator fixtures leave preferences, rows, and files unchanged; a valid pack round-trips.
  Confidence: Verified
  Effort: M

- [ ] P2 — Remove completed work from Active Downloads and make failures recoverable
  Category: ux
  Where: app/src/main/java/com/freevibe/service/DownloadManager.kt:333-350,464-466; app/src/main/java/com/freevibe/ui/screens/downloads/DownloadsViewModel.kt:20-29; app/src/main/java/com/freevibe/ui/screens/downloads/DownloadsScreen.kt:107-244
  Problem: Terminal progress remains in activeDownloads until manual dismissal, so a successful file appears twice under Active and history indefinitely. Failed cards expose no visible cause or retry action.
  Evidence: Current-run S22 capture 10b-downloads-reopen.png shows the same Reddit PNG completed under Active and in history after reopening. The success path sets isComplete and never clears it; the screen renders every map entry.
  Fix: Auto-remove successful progress after a short announced completion state or use a separate recent-status area. Show safe failure details, Retry, and Dismiss; persist request metadata when retry must survive death.
  Acceptance: A completion appears once in history and leaves Active after the interval/reopen; a forced failure displays its reason and Retry succeeds without duplicate MediaStore rows.
  Confidence: Verified
  Effort: M

- [ ] P2 — Populate MediaStore TITLE for downloaded YouTube tones
  Category: correctness
  Where: app/src/main/java/com/freevibe/service/SoundApplier.kt:154-180 insertMediaStoreAudio()
  Problem: Aura sets DISPLAY_NAME but not MediaStore.Audio.Media.TITLE. WebM tones can appear blank in system ringtone pickers and metadata consumers.
  Evidence: On the current-run API 29 emulator, applying a YouTube lofi ringtone succeeded and changed the system URI to Aura row 35, but ContentResolver returned a populated _display_name with empty title/title_key. A local OGG Aura Original auto-populated title, proving container-dependent behavior.
  Fix: Derive a sanitized human title and write TITLE for every inserted ringtone, notification, and alarm. Keep the extension only in DISPLAY_NAME.
  Acceptance: OGG, MP3, M4A, and WebM fixtures all produce nonblank TITLE; the OEM picker displays it; MIME and type flags remain correct.
  Confidence: Verified
  Effort: S

- [ ] P2 — Make the accessibility release gate fail when primary scenarios are waived away
  Category: testing
  Where: docs/qa/accessibility-release-gate.json; tools/accessibility_release_gate_check.py; app/src/test/java/com/freevibe/ui/screens/ReleasePolishContractTest.kt
  Problem: The gate can pass with zero executed primary scenarios because six are waived and only secondary surfaces are counted. It cannot support its claim that primary flows work at 200 percent.
  Evidence: Current JSON records zero scenarios in the claimed primary set, six waivers, five executed surfaces, and 20 excused checks. One baseline visibly truncates text, yet the checker returns ok because it validates bookkeeping.
  Fix: Define a non-waivable matrix for onboarding, each feed, detail/apply, Downloads, Library, and Settings in both themes at 200 percent. Require semantic and contrast/touch-target evidence.
  Acceptance: Removing any required scenario fails; the current truncated fixture fails; complete both-theme evidence is required before release.
  Confidence: Verified
  Effort: M

- [ ] P2 — Give collection tiles explicit labels and visible removal
  Category: a11y
  Where: app/src/main/java/com/freevibe/ui/screens/collections/CollectionsScreen.kt:477-513
  Problem: Collection media tiles lack a useful merged label, and removal is discoverable only through long press.
  Evidence: The production grid attaches long-click without a visible affordance or item-specific description. No Remove button or overflow action reaches the callback.
  Fix: Merge title/type/source semantics, expose a custom Remove action, and add visible overflow or selection-mode removal.
  Acceptance: TalkBack announces each tile/action; removal works without long press; instrumentation finds and removes an item by semantic label.
  Confidence: Verified
  Effort: S

- [ ] P2 — Make wallpaper crop presets change and expose the selected ratio
  Category: ux
  Where: app/src/main/java/com/freevibe/ui/screens/editor/WallpaperCropScreen.kt:260-295; app/src/main/java/com/freevibe/ui/screens/editor/WallpaperEditorViewModel.kt:173-190
  Problem: Ratio presets lack clear selected state and do not consistently produce an observable crop-geometry change, so taps can appear inert.
  Evidence: Callbacks update ratio inputs but the overlay is not keyed to explicit preset identity; controls omit selected semantics; the ViewModel has crop values but no restored/announced preset state.
  Fix: Model ratio explicitly, recompute the crop rectangle around its current center within bounds, style one selected preset, and announce it.
  Acceptance: Every preset visibly/semantically selects, changes a nonmatching crop, survives recreation, and exports within one pixel of the requested ratio.
  Confidence: Verified
  Effort: M

- [ ] P2 — Render wallpaper preview time from a ticking locale-aware clock
  Category: correctness
  Where: app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperPreviewScreen.kt:166-170,182-204,225-228
  Problem: Preview time is captured once and formatted with hardcoded 24-hour/date patterns. It freezes and can disagree with system settings.
  Evidence: The composable remembers current time without a ticker and uses H:mm plus a fixed date pattern even though it represents the applied live preview.
  Fix: Use a lifecycle-aware minute ticker, DateFormat.is24HourFormat(), and locale formatters shared with the applied overlay.
  Acceptance: Preview updates across a minute; 12-hour, 24-hour, English, and Chinese match system format; ticker stops off-screen.
  Confidence: Verified
  Effort: S

- [ ] P2 — Expose selected state on custom cards, chips, and tabs
  Category: a11y
  Where: app/src/main/java/com/freevibe/ui/screens/onboarding/OnboardingScreen.kt:377-432; app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperPreviewScreen.kt:83-93,148-161; app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt:805-844
  Problem: Several custom choices communicate selection only through color/decoration. Screen readers hear ordinary buttons with no current value.
  Evidence: The controls retain selected values in Compose state but do not set selected, role, or stateDescription on the clickable nodes.
  Fix: Use selectable()/selectableGroup() or Role.RadioButton plus selected semantics and a non-color visual indicator.
  Acceptance: TalkBack announces selected state; single-choice groups expose one selection; semantics tests cover onboarding, preview target, and sound mode.
  Confidence: Verified
  Effort: S

- [ ] P2 — Derive search and source menus from live provider capabilities
  Category: correctness
  Where: app/src/main/java/com/freevibe/ui/screens/search/UniversalSearchScreen.kt:210-231; app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt:367-384; app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt:855-911; app/src/main/java/com/freevibe/ui/screens/sounds/SoundBrowseQueries.kt:45-65
  Problem: Universal Search and wallpaper menus advertise a static provider set that can disagree with enabled keys, flavor availability, and the providers each feed actually queries.
  Evidence: Menu entries are declared separately from provider-enabled flows and credentials. Current-run testing confirmed different live sets for wallpapers, videos, and sounds, while Universal Search does not derive from those capabilities.
  Fix: Build filters from the provider capability/configuration model, scoped by media type and current availability. Disable unavailable entries with the setup reason and keep Reddit first, with YouTube available for sound and video.
  Acceptance: Toggling a provider or removing its key updates every menu immediately; selecting each enabled source queries only it; a Full/FOSS media matrix passes.
  Confidence: Verified
  Effort: M

- [ ] P2 — Preserve result identity when Universal Search opens or downloads media
  Category: correctness
  Where: app/src/main/java/com/freevibe/ui/screens/search/UniversalSearchScreen.kt:422-430,740-817; app/src/main/java/com/freevibe/ui/FreeVibeRoot.kt:478-480
  Problem: Search can insert duplicate downloaded media and Open often routes to a generic destination rather than the selected item. Users lose context and may see multiple rows for one asset.
  Evidence: Download actions construct rows independently instead of using the canonical scoped identity path, while Open passes only a broad destination for multiple result types. No exact-detail locator travels with the result.
  Fix: Reuse DownloadManager identity/replacement semantics and route with media type, provider, item ID, and source URL. Use a generic feed only when the item is unavailable, with an explanation.
  Acceptance: Downloading a result twice leaves one history row and one MediaStore object; Open lands on that exact wallpaper/sound/video; unavailable legacy results show a specific fallback.
  Confidence: Verified
  Effort: M

- [ ] P2 — Stop using localized display strings as upload-dialog control values
  Category: correctness
  Where: app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt:329-337; app/src/main/java/com/freevibe/ui/screens/sounds/SoundCommunityActions.kt:207; app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt:338-345; app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperCommunityActions.kt:182
  Problem: Dialog dismissal depends on comparing returned localized strings. Translation wording can keep a completed dialog open or close it for the wrong result.
  Evidence: Community helpers return presentation text and callers branch on equality with resources instead of a typed outcome. The app ships English and Chinese, so localized control flow is reachable.
  Fix: Return a sealed success/cancel/failure result with message data. Use resources only when rendering and dismiss only on typed success/cancel.
  Acceptance: English and Chinese tests produce identical behavior; changing wording cannot change control flow; success, cancel, validation failure, and network failure are covered.
  Confidence: Verified
  Effort: S

- [ ] P2 — Clear stale-content warnings after a successful refresh
  Category: reliability
  Where: app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt:186-187,303-311,685-707; app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt:174,381-393,623-640
  Problem: A stale-cache or degraded-source warning can remain after a later successful refresh, telling users fresh content is stale.
  Evidence: Warning state is remembered separately and set on fallback branches, while success paths update items/loading without consistently resetting it on both primary feeds.
  Fix: Make freshness/degradation part of each generation's load result and replace it atomically with items. Ignore completion from superseded requests.
  Acceptance: Forced fallback shows a warning; restored network plus refresh clears it; an older delayed failure cannot re-add it after newer success.
  Confidence: Verified
  Effort: S

- [ ] P2 — Show a terminal empty state instead of Loading YouTube sounds
  Category: ux
  Where: app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt:959-986,1023-1027
  Problem: A completed YouTube query with zero results is rendered with loading copy, implying work is active and offering no recovery.
  Evidence: The zero-item branch reuses Loading YouTube sounds after loading flags are false. Empty successful provider responses are a normal terminal state and the branch is reachable for any narrow query.
  Fix: Distinguish loading, empty query, no results, disabled, degraded, and failed. Echo the query and offer Clear filters/try another term for no results.
  Acceptance: A fake empty success never shows a spinner/loading wording; retry and clear work; loading copy appears only while a job is active.
  Confidence: Verified
  Effort: S

- [ ] P2 — Make wallpaper-editor overlays operable and announced
  Category: a11y
  Where: app/src/main/java/com/freevibe/ui/screens/editor/WallpaperEditorScreen.kt:520-590,735-765,809-839
  Problem: Overlay handles rely on precise drag/tap interaction and expose incomplete semantics. TalkBack cannot identify the active overlay, its bounds, or equivalent move/resize actions.
  Evidence: Production manipulation uses pointer input/custom drawing without adjustable or custom actions. The accessibility gate does not exercise this editor.
  Fix: Add selected overlay semantics, named Move/Resize/Delete actions, coarse step controls, and focus order between canvas, overlays, and properties.
  Acceptance: A TalkBack-only test selects, moves, resizes, and deletes overlays; focus never lands on an unlabeled handle; exported geometry is unchanged.
  Confidence: Verified
  Effort: M

- [ ] P2 — Add loading, first-frame, and decode-failure states to video crop
  Category: ux
  Where: app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoCropScreen.kt:139-147,185-236,403-490
  Problem: The crop surface can be blank while playback prepares and has no first-frame or decode-error state. Users cannot distinguish loading from broken media.
  Evidence: The screen prepares playback and renders the surface, but no Player.Listener first-frame/error result drives user-facing state. The issue did not reproduce with the two valid Reddit fixtures.
  Fix: Track preparing, first frame, ready, and decode failure; render bounded progress and an error with Back/Choose another video.
  Acceptance: A delayed fake player shows progress until first frame; an unsupported codec fixture shows an error rather than blank canvas; valid crop stays unchanged.
  Confidence: Needs-repro
  Effort: M

- [ ] P2 — Give an all-hidden video feed a durable recovery path
  Category: ux
  Where: app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt:243-258,307-317,585-599; app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt:541-555; app/src/main/java/com/freevibe/data/repository/VoteRepository.kt:325,380
  Problem: Hiding every loaded video produces an empty feed after snackbar Undo expires, with no way to restore hidden items. The preference persists across refresh/restart.
  Evidence: Visible items filter persisted downvotes; Hide writes through VoteRepository; the empty state reloads providers but exposes no undo or hidden-items list.
  Fix: Add Manage hidden videos and Restore all to the empty-filter state plus a durable hidden-items screen shared with wallpapers.
  Acceptance: After hiding all fixtures and restarting, the empty state explains why and restores one/all; refresh alone does not discard the preference.
  Confidence: Verified
  Effort: M

- [ ] P2 — Make the sound waveform scrubber adjustable to accessibility services
  Category: a11y
  Where: app/src/main/java/com/freevibe/ui/screens/sounds/SoundDetailScreen.kt:845-885
  Problem: The waveform exposes progress-bar semantics but no SetProgress action. TalkBack announces position but cannot seek.
  Evidence: Pointer taps/drags and ProgressBarRangeInfo exist, but there is no Slider, setProgress, or equivalent semantic action.
  Fix: Add setProgress semantics through the bounded seek callback with elapsed/total state and coarse accessibility increments.
  Acceptance: Semantics actions seek to 25, 50, and 75 percent; TalkBack announces elapsed position; touch scrubbing stays accurate.
  Confidence: Verified
  Effort: S

- [ ] P2 — Wire New from follows to actual followed creators
  Category: correctness
  Where: app/src/main/java/com/freevibe/ui/screens/community/CreatorProfileScreen.kt:326-335,634-665
  Problem: The New from follows control is visible but does not change the displayed uploads.
  Evidence: UI state toggles the option, but the list pipeline does not consume followed creator IDs. Follow state already exists in CreatorProfileRepository.
  Fix: Feed followed IDs into query/filter state, define empty/loading/error states, and preserve the choice across recreation.
  Acceptance: With two followed and one unfollowed fixtures, the filter shows only two; unfollow updates it; zero follows explains how to follow.
  Confidence: Verified
  Effort: M

- [ ] P2 — Keep community edit/report dialogs open until mutation succeeds
  Category: reliability
  Where: app/src/main/java/com/freevibe/ui/screens/community/CreatorProfileScreen.kt:143-173,350-361; app/src/main/java/com/freevibe/ui/components/CommunityReportDialog.kt:94-100
  Problem: Profile editing and reporting dismiss immediately after launching async work. Validation/network failure loses entered context.
  Evidence: Confirm callbacks clear dialog state before suspend results. Results exist, but dismissal is not conditioned on success.
  Fix: Add submitting state, disable duplicate submit, retain fields, show inline safe errors, and dismiss only on confirmed success.
  Acceptance: Forced failure keeps each dialog open with data and Retry; success dismisses once; double tap produces one backend call.
  Confidence: Verified
  Effort: S

- [ ] P2 — Add loading and failure states to Community Reports
  Category: ux
  Where: app/src/main/java/com/freevibe/ui/screens/community/CommunityReportsScreen.kt:82-86,110-112; app/src/main/java/com/freevibe/data/repository/CommunityReportRepository.kt:68-105
  Problem: Reports has no distinct loading/error state and Refresh communicates only success. Offline or denied queries can look like a legitimate empty queue.
  Evidence: Repository failures collapse before empty rendering and no retry card/error binds to load result.
  Fix: Expose loading/content/empty/error with last-updated time. Preserve stale content under warning and provide Retry.
  Acceptance: Offline and denied tests show different errors; legitimate empty says no reports; refresh updates time; content remains during recoverable failure.
  Confidence: Verified
  Effort: S

- [ ] P2 — Make the embedded image picker fit small and enlarged displays
  Category: a11y
  Where: app/src/main/java/com/freevibe/ui/components/EmbeddedImagePickerSheet.kt:45-124
  Problem: The picker uses a fixed 420 dp non-scrollable layout. Small windows, landscape, or enlarged display/text can clip choices/actions.
  Evidence: The sheet fixes height at line 89 and its content has no scrolling. The 200-percent matrix does not cover it.
  Fix: Use window-aware max height, verticalScroll or LazyColumn, safe insets, sticky actions, and adaptive thumbnails.
  Acceptance: 320x480, landscape, split-screen, and 200-percent tests reach every image/action without overlap; focus scrolls selection into view.
  Confidence: Verified
  Effort: S

- [ ] P2 — Reuse one bounded image request for wallpaper detail and palette extraction
  Category: perf
  Where: app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt:109-110,221-234,312-333,829-852; app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersViewModel.kt:455-467; app/src/main/java/com/freevibe/service/ColorExtractor.kt:46-62,106-109; app/src/main/java/com/freevibe/di/AppModule.kt:65-90
  Problem: Settling a page starts uncancelled original-size lookahead requests while the current image is independently fetched/buffered again for palette extraction. Large Reddit originals can overlap transfers and decodes.
  Evidence: Pager already composes one adjacent page; the effect also enqueues current, next, and next-plus-one without target size and ignores Disposable handles. ColorExtractor uses a separate no-cache OkHttp path with a 32 MiB cap. Tests check only that enqueue text exists.
  Fix: collectLatest settled page, skip current/already-composed items, use cancellable disk-oriented bounded prefetch, and derive palette from Coil's cached small result or displayed drawable.
  Acceptance: At most one network request per URL; rapid 20-page sweep leaves no stale jobs and only declared lookahead; decode stays display-bounded; next-page latency does not regress.
  Confidence: Verified
  Effort: M

- [ ] P2 — Coalesce download progress before publishing Compose state
  Category: perf
  Where: app/src/main/java/com/freevibe/service/DownloadManager.kt:193-214,464-466,503-515,660-666; app/src/main/java/com/freevibe/ui/screens/downloads/DownloadsViewModel.kt:20-23; app/src/main/java/com/freevibe/ui/screens/downloads/DownloadsScreen.kt:107-119
  Problem: Every 8 KiB read allocates a new map and emits StateFlow even though notification updates are throttled later. A 64 MiB file emits about 8,192 times.
  Evidence: The copy loop calls updateProgress per buffer; it uses map plus on every call; only notification publication has a 250 ms throttle.
  Fix: Coalesce UI state by elapsed time and meaningful byte/percent delta while always emitting initial, terminal, and error states.
  Acceptance: A deterministic 64 MiB/8 KiB stream stays within a declared event bound; terminal bytes are exact; recompositions are bounded; throughput does not regress.
  Confidence: Verified
  Effort: S

- [ ] P2 — Move collection QR work off the main thread and bound decode memory
  Category: perf
  Where: app/src/main/java/com/freevibe/service/CollectionExporter.kt:43-46,141-155,261-297; app/src/main/java/com/freevibe/ui/screens/collections/CollectionsScreen.kt:144-150,366-370; app/src/test/java/com/freevibe/service/CollectionExporterTest.kt:89-104
  Problem: QR generation performs about 590,000 setPixel calls during composition, while maximum import can hold compressed bytes, a roughly 48 MB bitmap, another 48 MB IntArray, and decoder buffers.
  Evidence: remember invokes 768x768 bitmap generation synchronously. Import permits 12 million pixels and copies pixels for ZXing. The safety test only searches source constants.
  Fix: Generate a bulk pixel array off the main thread with loading state, set pixels once, and downsample imports to a QR-appropriate edge before allocation.
  Acceptance: QR dialog has no frame over the agreed threshold; maximum fixture decodes under a 128 MiB heap; exported QR round-trips.
  Confidence: Verified
  Effort: M

- [ ] P1 — Prevent YouTube video wallpaper installs from selecting unsupported AV1
  Category: functionality
  Where: app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt:733-743; app/src/main/java/com/freevibe/service/Av1CodecSupport.kt:11-57; app/src/main/java/com/freevibe/service/VideoWallpaperService.kt:363-427
  Problem: The YouTube yt-dlp selector constrains the container to MP4 but not the video codec. Aura can install an AV1 stream on a device without an AV1 decoder, replacing the user's wallpaper with a black screen while the service retries playback.
  Evidence: Current-run API 29 search for `loop` returned the seven-second YouTube item `Loop Background | Live Wallpaper | Chilling Cat | No Sound`. Apply downloaded a 293,136-byte 1920x1080 MP4 that ffprobe identifies as AV1 Main. The system accepted `VideoWallpaperService`, but `NuPlayerDecoder` repeatedly logged `Failed to create video/av01 decoder`, and emu-youtube-video-home-black2.png records the black installed wallpaper. `Av1CodecSupport` still has no production caller.
  Fix: Feed device decoder capability into yt-dlp format selection, prefer AVC/H.264 as the compatibility fallback, and validate the downloaded codec before opening the system picker. Select AV1 only after a supported decoder and a successful bounded decode probe; otherwise obtain or transcode a compatible stream and preserve the previous wallpaper on failure.
  Acceptance: The captured YouTube fixture installs and animates on an AVC-only API 29 device without decoder errors; an AV1-capable device may select AV1 after a decode probe; an incompatible download is rejected before the system picker with a retry path and never replaces the current wallpaper.
  Confidence: Verified
  Effort: M

- [ ] P2 — Add save, download, share, and in-app report actions to video items
  Category: ux
  Where: app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt:605-634,883-949,1168-1402
  Problem: Video cards/immersive preview expose Apply plus Upvote/Hide, while wallpapers and sounds support richer keep/share/report workflows. Useful Reddit media cannot be retained without applying it.
  Evidence: Current-run S22 capture 13-video-actions.png shows only Upvote video wallpaper and Hide video wallpaper. Code confirms those are the only card callbacks; immersive mode adds Apply but no save/download/share.
  Fix: Add Favorite/collection, Download, Share source, and Report using existing license checks, DownloadManager, attribution, and report paths. Keep Reddit attribution/terms visible.
  Acceptance: Each action works for Reddit and YouTube fixtures, respects terms, creates one canonical download, and is reachable from card and immersive views.
  Confidence: Verified
  Effort: M

- [ ] P2 — Expand the localization gate to every user-facing string path
  Category: testing
  Where: tools/compose_hardcoded_string_check.py; docs/localization/hardcoded-string-baseline.json; app/src/main/java/com/freevibe/ui/screens/downloads/DownloadsScreen.kt:329-364; app/src/main/java/com/freevibe/ui/screens/favorites/FavoritesScreen.kt:667-731; app/src/main/java/com/freevibe/ui/screens/settings/WallpaperHistoryScreen.kt:48,54,115-122; app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt:770-777,1019-1047; app/src/main/java/com/freevibe/data/repository/AiWallpaperRepository.kt:19-27; app/src/main/java/com/freevibe/service/MediaIngestion.kt:488-494
  Problem: The current checker and baseline miss user-visible literals outside its narrow Compose patterns, so Chinese and future locales silently fall back to embedded English that the gate reports as clean.
  Evidence: Direct review found hardcoded labels/status text in the listed production screens, repository errors, and English-built list conjunctions. The baseline can also be grown in write mode without a required reason.
  Fix: Parse all Kotlin user-facing sinks, including Snackbar/toast/state/error constructors and content descriptions. Extract current literals, require a reason for any unavoidable baseline entry, and forbid automatic baseline growth.
  Acceptance: The named strings come from resources in English and Chinese; a fixture in each supported sink fails; the baseline is empty or every entry has a reviewed reason; write mode cannot increase it silently.
  Confidence: Verified
  Effort: M

- [ ] P2 — Make the performance harness fail when it misses a destination
  Category: testing
  Where: baselineprofile/src/main/java/com/freevibe/benchmark/AuraBenchmarkFlows.kt:27-43,56-71; baselineprofile/src/main/java/com/freevibe/benchmark/GridScrollBenchmark.kt:20-30; app/src/main/java/com/freevibe/ui/navigation/Screen.kt:365-368; docs/performance/baseline-profile.md:17-21,39-46
  Problem: The harness still taps Favorites even though bottom navigation now contains Library. Missing selectors and shell waits are ignored, so it waits about 16 seconds and measures the wrong screen while reporting success.
  Evidence: tapBottomNav uses nullable click and waitForAuraShell discards its boolean result. Selectors are hardcoded English. The runbook also names a deleted performance workflow. A baseline was regenerated after the nav change, proving the no-op can produce nominal output.
  Fix: Add stable test tags/resource IDs, fail immediately on missing shell/destination, navigate Library then Favorites, and document only the local physical-device lane.
  Acceptance: An invalid destination fails immediately; English and Chinese runs reach identical routes; trace evidence proves Library-to-Favorites navigation; every documented command/file exists.
  Confidence: Verified
  Effort: S

### P3

- [ ] P3 — Replace OEM emoji category art with adaptive app-owned visuals
  Category: visual
  Where: app/src/main/java/com/freevibe/ui/screens/categories/CategoriesScreen.kt:27-52,71-119
  Problem: Platform emoji and fixed columns vary by OEM and become cramped on narrow/enlarged displays.
  Evidence: The category model embeds emoji strings and the grid does not derive columns from available width. This route is reachable in both themes.
  Fix: Use a coherent app vector set with themed tints and GridCells.Adaptive at a tested minimum width. Keep a non-color selected indicator.
  Acceptance: Samsung/AOSP, light/dark, 320 dp, tablet, and 200-percent screenshots use consistent art without clipped labels; semantics omit decorative emoji.
  Confidence: Verified
  Effort: S

- [ ] P3 — Expose collection rename instead of hiding implemented behavior
  Category: ux
  Where: app/src/main/java/com/freevibe/ui/screens/collections/CollectionsScreen.kt:228-230,399-438; collection ViewModel and DAO rename methods
  Problem: Rename exists in the data layer but has no discoverable user action. Correcting a name requires creating a replacement collection.
  Evidence: ViewModel/DAO rename functions have no visible collection-list/card menu entry.
  Fix: Add Rename to overflow, reuse create-name validation, retain text on error, and announce success.
  Acceptance: Rename works from a visible menu; blank/duplicate names show inline errors; contents/order survive restart.
  Confidence: Verified
  Effort: S

## Research-Driven Additions — 2026-09-25

### P1

- [ ] P1 — Render each live wallpaper engine with its display context
  Why: Android can run concurrent wallpaper engines on displays with different densities, but Aura sizes clock overlays and fallback surfaces from service resources, so secondary-display rendering can be scaled incorrectly.
  Evidence: **Verified.** Android's `WallpaperService.Engine.getDisplayContext()` contract says to avoid the service context in a multiple-display environment; `WallpaperClockOverlay.kt:88` reads `context.resources.displayMetrics.density`; `VideoWallpaperService.kt:689`, `WeatherWallpaperService.kt:480`, and `ParallaxWallpaperService.kt:550` pass their service context; the weather and parallax fallbacks also read service metrics at `WeatherWallpaperService.kt:259-260` and `ParallaxWallpaperService.kt:293-294`; https://developer.android.com/reference/android/service/wallpaper/WallpaperService.Engine#getDisplayContext
  Touches: `VideoWallpaperService.kt`, `WeatherWallpaperService.kt`, `ParallaxWallpaperService.kt`, `WallpaperClockOverlay.kt`, live-wallpaper engine and rendering tests.
  Acceptance: on API 29 and later, every engine obtains its context only after `onCreate(SurfaceHolder)` and uses that context for density, fallback dimensions, overlays, and display resources; API 26 through 28 keep an explicit service-context fallback; a test creates two concurrent engines with distinct densities and surfaces and proves each produces independently scaled output; preview and applied-engine lifecycle tests pass without service-global display state.
  Complexity: M

### P2

- [ ] P2 — Upgrade Firebase CLI to 15.31.0 and gate the root audit
  Why: Aura's deployment CLI is pinned to 15.19.1 and brings nine moderate advisories into the repository toolchain even though the production Functions dependency tree is clean.
  Evidence: **Verified on 2026-09-25.** `package.json:15` and `package-lock.json:4446-4448` pin 15.19.1; root `npm audit` reports nine moderate vulnerabilities through Firebase CLI dependencies and names 15.31.0 as the non-major fix; 15.31.0 pins `stream-json` 3.6.0 or later and `csv-parse` 7.0.2 or later; `npm audit --omit=dev` in `functions/` reports zero; https://github.com/firebase/firebase-tools/releases/tag/v15.31.0.
  Touches: `package.json`, `package-lock.json`, Firebase emulator and backend-manifest checks under `tools/`, release documentation that names the CLI version.
  Acceptance: the root lockfile resolves Firebase CLI 15.31.0 or a newer reviewed 15.x patch; root and `functions/` audits report zero moderate, high, or critical findings without `--force`, blanket overrides, or ignored advisories; existing Firebase emulator, rules, Functions, and community-backend manifest checks pass; the gate labels root findings as deployment-tool findings so they are not reported as APK runtime vulnerabilities.
  Complexity: S

## Issue Intake (2026-09-26)

Open GitHub issues checked against this list on 2026-09-26. The only open issue is #47 (translation call, help wanted). It is covered by the P2 item above that cites it ("Reported: #47"): Simplified Chinese landed through PR #48 on 2026-08-12, and the issue stays open as the umbrella for further languages. No new items.

- [ ] P3: Keep #47 current (issue #47)
  Why: the issue body still says "zero translations" although zh ships; a stale umbrella issue puts off the next contributor.
  Next: edit the body to list the languages that exist, the coverage percentage and the review path from docs, then leave it open.
  Evidence: https://github.com/SysAdminDoc/Aura/issues/47
