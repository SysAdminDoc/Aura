# Aura Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions

### P2

- [ ] P2 — Add named Reddit feed presets for discovery and rotation
  Why: one global comma-separated subreddit preference cannot represent different moods, devices, video feeds, or rotation contexts. Reusable presets create materially more choice while keeping the highest-quality source first.
  Evidence: **Verified.** `PreferencesManager.kt:58,500-520` stores one wallpaper list and one video list with a twelve-subreddit cap; WallFlow supports saved searches and source configurations: https://github.com/ammargitham/WallFlow.
  Touches: Reddit OAuth query model, Room/DataStore preset schema, Wallpapers/Video/Settings UI, rotation source selection, import/export, diagnostics, tests.
  Acceptance: users can create, rename, duplicate, reorder, and delete presets containing subreddit/community list, media type, sort, time window, safe-content setting, and minimum dimensions/duration; Aura ships several editable defaults without silently enabling adult content; the active preset is visible in each feed; rotation can bind to a preset; pagination/cache keys include the full preset; presets round-trip through backup and survive subreddit removal.
  Complexity: M


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

### P2


- [ ] P2 — Make the accessibility release gate fail when primary scenarios are waived away
  Category: testing
  Where: docs/qa/accessibility-release-gate.json; tools/accessibility_release_gate_check.py; app/src/test/java/com/freevibe/ui/screens/ReleasePolishContractTest.kt
  Problem: The gate can pass with zero executed primary scenarios because six are waived and only secondary surfaces are counted. It cannot support its claim that primary flows work at 200 percent.
  Evidence: Current JSON records zero scenarios in the claimed primary set, six waivers, five executed surfaces, and 20 excused checks. One baseline visibly truncates text, yet the checker returns ok because it validates bookkeeping.
  Fix: Define a non-waivable matrix for onboarding, each feed, detail/apply, Downloads, Library, and Settings in both themes at 200 percent. Require semantic and contrast/touch-target evidence.
  Acceptance: Removing any required scenario fails; the current truncated fixture fails; complete both-theme evidence is required before release.
  Confidence: Verified
  Effort: M




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


- [ ] P2 — Wire New from follows to actual followed creators
  Category: correctness
  Where: app/src/main/java/com/freevibe/ui/screens/community/CreatorProfileScreen.kt:326-335,634-665
  Problem: The New from follows control is visible but does not change the displayed uploads.
  Evidence: UI state toggles the option, but the list pipeline does not consume followed creator IDs. Follow state already exists in CreatorProfileRepository.
  Fix: Feed followed IDs into query/filter state, define empty/loading/error states, and preserve the choice across recreation.
  Acceptance: With two followed and one unfollowed fixtures, the filter shows only two; unfollow updates it; zero follows explains how to follow.
  Confidence: Verified
  Effort: M



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


- [ ] P2 — Move collection QR work off the main thread and bound decode memory
  Category: perf
  Where: app/src/main/java/com/freevibe/service/CollectionExporter.kt:43-46,141-155,261-297; app/src/main/java/com/freevibe/ui/screens/collections/CollectionsScreen.kt:144-150,366-370; app/src/test/java/com/freevibe/service/CollectionExporterTest.kt:89-104
  Problem: QR generation performs about 590,000 setPixel calls during composition, while maximum import can hold compressed bytes, a roughly 48 MB bitmap, another 48 MB IntArray, and decoder buffers.
  Evidence: remember invokes 768x768 bitmap generation synchronously. Import permits 12 million pixels and copies pixels for ZXing. The safety test only searches source constants.
  Fix: Generate a bulk pixel array off the main thread with loading state, set pixels once, and downsample imports to a QR-appropriate edge before allocation.
  Acceptance: QR dialog has no frame over the agreed threshold; maximum fixture decodes under a 128 MiB heap; exported QR round-trips.
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
  Where: tools/compose_hardcoded_string_check.py; docs/localization/hardcoded-string-baseline.json; app/src/main/java/com/freevibe/ui/screens/downloads/DownloadsScreen.kt:329-364; app/src/main/java/com/freevibe/ui/screens/favorites/FavoritesScreen.kt:667-731; app/src/main/java/com/freevibe/ui/screens/settings/WallpaperHistoryScreen.kt:48,54,115-122; app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt:770-777,1019-1047; app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperFeedQuality.kt:88-123; app/src/main/java/com/freevibe/data/repository/AiWallpaperRepository.kt:19-27; app/src/main/java/com/freevibe/service/MediaIngestion.kt:488-494
  Problem: The current checker and baseline miss user-visible literals outside its narrow Compose patterns, so Chinese and future locales silently fall back to embedded English that the gate reports as clean.
  Evidence: Direct review found hardcoded labels/status text in the listed production screens, repository errors, and English-built list conjunctions. The wallpaper card's TalkBack summary also assembles English-only orientation, AMOLED, vote-count, and saved-state phrases outside Android resources. The baseline can be grown in write mode without a required reason, and the 2026-09-25 tool run already drifted on a new `Applied automatically` message.
  Fix: Parse all Kotlin user-facing sinks, including Snackbar/toast/state/error constructors and content descriptions. Extract current literals, require a reason for any unavoidable baseline entry, and forbid automatic baseline growth.
  Acceptance: The named strings and wallpaper-card semantics come from resources in English and Chinese, including proper plurals for vote counts; a fixture in each supported sink fails; the baseline is empty or every entry has a reviewed reason; write mode cannot increase it silently.
  Confidence: Verified
  Effort: M


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


## Research-Driven Additions — 2026-09-25

### P2



## Deep Audit Additions — 2026-09-25

### P2

- [ ] P2 — Finish server-side Storage attestation for community uploads
  Category: security
  Where: `functions/src/soundUploadHandler.ts:77-90,171-183,217-257,460-469`; `functions/src/wallpaperUploadHandler.ts:69-88,169-189,513-522`; `app/src/main/java/com/freevibe/data/model/CommunitySoundUpload.kt:24-64`; `app/src/main/java/com/freevibe/data/repository/UploadRepository.kt:121-150,363-378`; `functions/test/finalizeCommunitySoundUpload.test.cjs:15-35`; `functions/test/finalizeCommunityWallpaperUpload.test.cjs:18-38`
  Problem: The new finalizers only partly bind published metadata to the uploaded object. The sound path references a nonexistent `payload.fileSize`, its Android caller never sends that field, and it never compares the declared MIME type with Storage metadata. Both media types still publish client-selected HTTPS URLs, and wallpaper dimensions are never decoded from the stored JPEG.
  Evidence: `npm --prefix functions test` and `npm run test:functions-emulator` both stop in TypeScript compilation at `soundUploadHandler.ts:178,181` because `CommunitySoundUploadPayload` has no `fileSize`. `verifyStorageObject()` returns only existence, MIME, and byte size. The wallpaper check accepts any Storage MIME whose top-level type is `image`, then persists the submitted URLs and dimensions. The JavaScript fakes have no `verifyStorageObject` method or missing-object, MIME, URL, size, and dimension mismatch cases, so runtime coverage would still fail after the type error is corrected. Storage creation rules reduce exposure, but they do not prove that later public metadata describes the same object.
  Fix: Add one shared attestation result whose required fields come from Admin Storage and media inspection. Derive the public locator from the verified bucket/object instead of accepting it from the caller, require exact allowed MIME and byte size, decode JPEG dimensions server-side, and add `fileSize` to the sound client contract. Update the fakes before publication logic is considered complete. Keep quota-failure reconciliation in its existing roadmap item.
  Acceptance: Both Functions test commands compile and pass; missing objects and unavailable metadata fail closed; wrong owner path, URL, size, exact MIME, and wallpaper dimensions are rejected without a public row; a matching sound and wallpaper publish only server-derived object metadata; Android payload tests prove sound size is bounded and transmitted.
  Confidence: Verified
  Effort: M



- [ ] P2 — Keep every primary destination usable at 200 percent text
  Category: a11y
  Where: `app/src/main/java/com/freevibe/ui/FreeVibeRoot.kt:241-301,1156-1224`
  Problem: The fixed bottom bar and navigation rail cannot contain five always-visible text labels at the platform's 200 percent font setting. Portrait labels are cut mid-word, while the landscape rail truncates Wallpapers and pushes Settings completely off-screen.
  Evidence: Current-run API 35 emulator captures at `font_scale=2.0` show `Wallpa`, `Sound`, and `Settin` in the 64 dp portrait bar. At 2400 by 1080 landscape, the 86 dp rail shows only four destinations and clips the selected label. The code fixes bar height to 64 dp, rail width to 86 dp, labels to one line, and gives the full-height rail no scroll or compact mode. This concrete defect is actionable independently of the remaining scanner matrix in `Roadmap_Blocked.md`.
  Fix: Introduce a large-text navigation presentation that preserves all five targets, such as semantic icon-only compact navigation or a scroll-safe rail, and size it from the actual container and insets. Keep full accessible names even when visible labels are shortened or hidden.
  Acceptance: At 200 percent text in portrait and landscape, all five destinations are simultaneously reachable with no clipped text or target; TalkBack announces their full localized names and selected state; normal text, RTL, gesture navigation, and three-button navigation retain at least 48 dp targets; screenshot tests cover both navigation forms.
  Confidence: Verified
  Effort: M

- [ ] P2 — Replace URL-only wallpaper dedupe with safe media identity
  Category: correctness
  Where: `app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperFeedQuality.kt:67-85,253-264`; wallpaper feed cache and provider merge tests
  Problem: Feed dedupe treats a normalized URL as content identity. Exact duplicate files at different URLs remain side by side, while lowercasing the entire URL can collapse distinct resources whose case-sensitive paths differ.
  Evidence: The current-run Popular feed rendered its first two cards as the same artwork under Reddit IDs `rd_1wpu3qi` and `rd_1wpu03j`. Their distinct `i.redd.it` URLs downloaded to two 519,117-byte, 978 by 2,030 JPEGs with the same SHA-256, `0D1C55A70581FACE052D4CA39B102900B5A29BBF89A492B8B45187FB65F30D1F`. `wallpaperKey()` strips query and fragment, lowercases the full URL, and has no content fingerprint.
  Fix: Canonicalize only URL components that are case-insensitive, preserve path/query identity where providers use it, and attach a bounded content digest or decoded-thumbnail fingerprint to fetched media. Coalesce exact duplicates while retaining attribution aliases and the highest-quality metadata.
  Acceptance: Distinct URLs with identical bytes render once; differently cased, case-sensitive paths remain distinct; signed/resized variants of one asset resolve according to a documented provider rule; pagination and cache restoration do not reintroduce a duplicate; unit fixtures cover all four cases.
  Confidence: Verified
  Effort: M


### P3


## Drain Leftovers — 2026-09-30

Remainders of items closed in the 2026-09-30 drain whose full acceptance did not land.

### P2

- [ ] P2 — Give restart-on-manual-apply a Settings switch and a diagnostics line
  Why: a manual apply now restarts the rotation interval (CANCEL_AND_REENQUEUE, scheduler-aware), but `AUTO_WP_RESTART_ON_MANUAL` defaults on with no setter and no UI, so nobody can turn it off, and Diagnostics can't show when the next rotation is due.
  Touches: `PreferencesManager.kt` (setter), `SettingsRotationDelegate.kt`, rotation settings UI, Diagnostics export, strings (en + zh), tests.
  Acceptance: a switch under rotation settings toggles the pref; Diagnostics shows the next scheduled rotation time from `WorkInfo.nextScheduleTimeMillis`; a test covers the off path leaving the countdown untouched.
  Complexity: S

- [ ] P2 — Dim the MP4 path of the video live wallpaper and name the dimmed engines
  Why: dimming now reaches the GIF path of `VideoWallpaperService` and the parallax engine, but MP4 playback renders straight to the surface through ExoPlayer, so the dim overlay never draws over it. The toggle copy doesn't say which live wallpapers honor it.
  Touches: `VideoWallpaperService.kt` (render MP4 through a GL or TextureView-style composition pass, or apply the dim in the player's video effect chain), `LiveWallpaperDimming.kt`, settings strings, a soak run on device.
  Acceptance: an MP4 live wallpaper dims and reveals on touch like the GIF path; the setting text lists the engines it affects; a 30-minute on-device soak shows no frame-rate or memory regression.
  Complexity: M

- [ ] P2 — Measure first-play latency and resolve counts for on-demand sound previews on a phone
  Why: Sounds now resolves YouTube previews only for visible rows plus one lookahead (`SoundPreviewWarmup`). JVM tests prove the budget, cancellation and shared concurrency, but no device run has compared first-play latency or counted `Audio preview resolved` log lines against the old eager fan-out (ten resolutions in about eight seconds on API 29).
  Touches: debug build on the S22 or S25, logcat `YouTubeRepo` lines, a short timing script.
  Acceptance: opening Ringtones logs no more than about eight resolutions before any tap; scrolling adds only the newly visible rows; time from tapping a top-row preview to audio is no worse than the previous release on the same phone.
  Complexity: S

- [ ] P2 — Prove display-context rendering on a second density
  Why: Video, Weather and Parallax now draw with `displayContext` on API 29+, but no test runs two engines on displays of different density.
  Touches: Robolectric or instrumented test for `resolveDecodeTarget()` and `resolveScreenSize()` with a secondary display.
  Acceptance: a test with two display configurations shows each engine sizing its decode target from its own display.
  Complexity: S

### P3

- [ ] P3 — Cover the AV1 branches of the video wallpaper feed and the fragmented-MP4 fallback on device
  Why: `VideoWallpapersViewModelTest` compiles again but never exercises `Av1CodecSupport` returning true or false, and the MediaExtractor duration fallback for fragmented MP4 has only a JVM test. When neither the retriever nor MediaExtractor reports a duration, the too-short check is skipped entirely, so a short clip with no duration metadata gets through. HLS sources are never rewrapped (`RedditRssParser.kt`, `VideoWallpapersViewModel.kt`).
  Touches: `VideoWallpapersViewModelTest.kt`, an androidTest with a fragmented MP4 fixture (the "Waves" clip), `VideoWallpaperStorage.kt`, `RedditRssParser.kt`.
  Acceptance: AV1-supported and unsupported cases each pick the expected rendition; an on-device test on API 26 and API 29 applies a fragmented MP4 with no container duration; an unknown-duration clip is measured by decoding frames or rejected with a clear message rather than skipped.
  Complexity: M

- [ ] P3 — Prove the wallpaper detail pager no longer recomposes per drag frame
  Why: the page offset read moved into `graphicsLayer`, but nothing measures recomposition, so a later edit can move it back unnoticed.
  Touches: `WallpaperDetailScreen.kt`, a Compose test with a recomposition counter.
  Acceptance: a test drags the pager across a page and asserts page content recomposes a bounded number of times, independent of frame count.
  Complexity: S

- [ ] P3 — Settle the root npm audit residue and add Sound detail width tests
  Why: after the firebase-tools 15.31.0 bump, `npm audit` at the repo root still reports 10 findings that come through firebase-tools itself. The container-width layout fix in Sound detail has no test at narrow width or 200 percent text.
  Touches: root `package.json`/`package-lock.json`, the dependency gate's accepted-advisory list, `SoundDetailScreen.kt` tests.
  Acceptance: each remaining advisory is fixed or recorded with a reason in the gate; a Compose test shows the secondary actions stacking at 320 dp and at fontScale 2.0.
  Complexity: S

- [ ] P3 — Finish the debug-build StrictMode and LeakCanary pass
  Why: debug builds now run StrictMode and LeakCanary, but nothing asserts LeakCanary is absent from release APKs, the violations it logs on a real device haven't been listed, and `detectImplicitUriPermissionGrant` needs compileSdk 37.
  Touches: release APK scan in the build gates, repo notes, `FreeVibeApp.kt`.
  Acceptance: a gate fails if `leakcanary` classes appear in a release APK; the violations seen during a device session are recorded and each has a fix or an open item; the URI-grant check is enabled once compileSdk reaches 37.
  Complexity: S

- [ ] P3 — Localize the Active download status that TalkBack reads
  Why: `downloadProgressStatusLabel` in `DownloadsScreen.kt` builds "Download failed: <reason>" and the percent label in English, so a Chinese TalkBack user hears an English prefix around a Chinese reason.
  Touches: `DownloadsScreen.kt`, `values/strings.xml`, `values-zh/strings.xml`, `DownloadsScreenPolishTest.kt`.
  Acceptance: the status label comes from string resources in both locales; a test under the zh locale reads the failed and in-progress labels without English text.
  Complexity: S


## Issue Intake (2026-09-26)

Open GitHub issues checked against this list on 2026-09-26. The only open issue is #47 (translation call, help wanted). It is covered by the P2 item above that cites it ("Reported: #47"): Simplified Chinese landed through PR #48 on 2026-08-12, and the issue stays open as the umbrella for further languages. No new items.
