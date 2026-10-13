# Aura Roadmap

Actionable work only. Historical and completed roadmap material is archived in CHANGELOG.md; blocked work is kept in Roadmap_Blocked.md.

## Research-Driven Additions

### P2

- [ ] P2 — Play more than one clip in the video live wallpaper
  Why: `VideoWallpaperService` plays exactly one video. A playlist with per-clip framing is the top-requested capability in the video-wallpaper category, and the whole rotation machinery Aura already owns — scheduler, day/night, collections — has no video equivalent.
  Evidence: no playlist or queue concept in `VideoWallpaperService.kt`; UndeadWallpaper v1.3.7 (per-clip zoom/offset/rotation/speed, shuffle, smart start); Lively #137 (25 comments) on condition-driven change. Depends on the tracked video-cache-bounding and main-thread-encode fixes; do those first.
  Touches: `VideoWallpaperService.kt`, `VideoWallpaperStorage.kt`, `PreferencesManager.kt`, video settings UI, the soak harness, string resources.
  Acceptance: an ordered or shuffled clip list advances at a configured boundary with no black frame at the seam; per-clip fit/crop and mute are preserved; the existing FPS cap, low-battery cap, and `onVisibilityChanged` pause govern the whole playlist, not just the first clip; total decoded storage stays bounded; the soak harness runs the playlist path and asserts nothing survives `onDestroy`.
  Complexity: L

  Note 2026-08-23: compileSdk 36 and Media3 1.11.0 are already shipped, so the old dependency gate is gone. Keep this sequenced after the existing video-cache and main-thread encode reliability work; use Media3 preload first and retain the custom engine only if a measured black-frame test still fails.



### P3

- [ ] P3 — Put spacing on tokens and give text buttons a rectangular press shape
  Why: the design-token pass moved every corner shape and colour literal onto tokens behind `tools/design_token_check.py`, but spacing is still literal dp values, and text buttons still clip their press ripple to Material's pill.
  Evidence: `Arrangement.spacedBy` uses 2, 3, 4, 5, 6, 7, 8, 10, 12, 14 and 16 dp, and padding and spacer values add 9, 18, 20 and 24 dp (census 2026-10-13: 146 `spacedBy(8.dp)`, 64 at 12, 34 at 10, the rest scattered); 156 `TextButton(` calls take the default shape.
  Touches: a new `ui/theme/Spacing.kt`, the screens that use off-scale values, `tools/design_token_check.py` and its test, Roborazzi baselines.
  Acceptance: an `AuraSpacing` scale lives in `ui/theme/`; off-scale values move to the nearest step with screenshots compared before and after; the gate rejects new literal spacing in `spacedBy` and padding outside the scale; every `TextButton` passes an `AuraShapes` token and the gate checks it like the container buttons.
  Complexity: M

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
  Why: Aura is the highest-starred FOSS Android ringtone project under GitHub `topic:ringtone`, a topic that is nearly empty. It is on no awesome-list, and `offa/android-foss` has a one-entry wallpaper section and no live-wallpaper or ringtone section at all. This is the cheapest reach available and it needs no code.
  Evidence: `offa/android-foss` wallpaper section lists one app; `vvolas/Awesome-Live-Wallpaper` is Android-specific and dead since 2016; `w3teal/awesome-ringtone` does not list Aura.
  Touches: no app code; README topics, external PRs, `docs/distribution/channel-strategy.md`.
  Acceptance: `docs/distribution/channel-strategy.md` records which lists were submitted to and when, with links; GitHub topics are set. Topics and external PRs are GitHub writes, so each one waits for Matt's go-ahead.
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

- [ ] P2 — Pool and preload the video-feed player
  Why: every scroll stop in the video feed constructs, prepares, and releases an ExoPlayer, so the user sees "Preparing preview" on each card instead of a preview that is already warm. Media3 1.11.0 is already pinned and ships the pieces for this exact pattern.
  Evidence: `VideoWallpapersScreen.kt:952` builds `ExoPlayer.Builder(context)…prepare(); play()` inside `remember(item.id, streamUrl)` and releases it in `onDispose`; only one card previews at a time (`VideoWallpapersScreen.kt:569`, `activePreviewId`); Media3 1.11.0 added `PlayerPool` and `rememberPooledPlayer` in `media3-ui-compose` for preloading in a sliding-window UI, plus `DefaultPreloadManager` and `ExoPlayer.Builder.enablePerStreamMediaProgression()` (https://developer.android.com/jetpack/androidx/releases/media3).
  Note 2026-09-25: pin Media3 1.11.1 before implementing the pool. Its 2026-09-10 fixes cover secondary-renderer prewarming stalls, `Surface` ownership after seek reset, stale seek frames, and fully consumed HLS chunks retrying after `EOFException`, all of which intersect this feed.
  Touches: `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt`, `app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt`, `gradle/libs.versions.toml` (add `media3-ui-compose`), `gradle/verification-metadata.xml`, `baselineprofile/`.
  Acceptance: a pooled player is reused across cards instead of being rebuilt per item, and the next item in scroll order is preloaded through `DefaultPreloadManager`; the pool is bounded and every player is released when the feed leaves composition, verified with the same retention assertion style the live-wallpaper soak already uses; time from scroll-stop to first frame is measured before and after and recorded; the existing preview-unavailable and playback-error states still render.
  Complexity: M


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


- [ ] P2 — Stop clipping helper text at large font sizes
  Category: a11y
  Where: `SettingsComponents.kt:82-88,141-148,212-219`; `SharedComponents.kt:401-417,503-509`; `LibraryScreen.kt:239,290-305`; `WallpapersScreen.kt:519-561`; `SoundsScreen.kt:495-554`; `app/src/test/screenshots/settings_provider_disabled_light_font2_0.png`
  Problem: Section and switch summaries are capped to a line count and end in an ellipsis at 200 percent text, the diagnostics cards break titles mid-word, and the feed history dropdown can overlap the search controls. The accessibility gate's Settings/light cell records `noClippedText: fail` from this baseline.
  Fix: Let helper text wrap when the font scale is large (drop the caps or make them scale-aware), give the diagnostics cards a one-column layout when their titles don't fit, and reuse the measured layout from `UniversalSearchScreen.kt:346-385` for the history dropdown.
  Acceptance: The 200 percent Settings baseline is re-recorded with no ellipsis or mid-word break, a Library and a feed baseline at 200 percent join it, and the Settings/light cell in `docs/qa/accessibility-release-gate.json` moves to `noClippedText: pass`.
  Confidence: Verified
  Effort: M


- [ ] P2 — Let people favorite motion wallpapers and add them to collections
  Category: feature
  Where: app/src/main/java/com/freevibe/ui/screens/favorites/FavoritesScreen.kt, FavoritesViewModel.kt, data/repository/FavoritesRepository.kt, ui/screens/videowallpapers/VideoWallpapersScreen.kt
  Problem: Videos can now be downloaded, shared and reported, but not favorited or collected. Favorites only has Wallpapers and Sounds tabs, FavoritesRepository reads only WALLPAPER and SOUND types, and nothing can rebuild a VideoWallpaperItem from a saved row to apply it later.
  Fix: Store VIDEO favorites with the fields Apply needs (source, video ID, page URL, creator, license, size, duration), add a Videos tab that opens the item in the video preview with Apply and Download, and carry them through favorites backup export/import and collections.
  Acceptance: A Reddit and a YouTube video favorited from the card and the full-screen preview appear under Favorites > Videos after a restart and a backup round trip, apply from there, and respect the same license checks as the feed.
  Confidence: Verified
  Effort: M

- [ ] P2 — Move ViewModel and repository messages to string resources
  Category: ux
  Where: docs/localization/hardcoded-string-baseline.json (each entry whose reason names this item); app/src/main/java/com/freevibe/ui/screens/**/*ViewModel.kt; ui/screens/wallpapers/WallpaperCommunityActions.kt, WallpaperSearchActions.kt; ui/screens/sounds/SoundApplyActions.kt, SoundIdentity.kt, SoundQuality.kt; ui/screens/settings/SettingsDialogs.kt (communityIdentitySuffixLabel); app/src/main/java/com/freevibe/service/MediaIngestion.kt (imageFormatSupportForFlow, toHumanList, sniff errors); wallpapers/WallpaperDetailScreen.kt sourceDisplayName; sounds/SoundUiTokens.kt soundSourceTone; settings/DiagnosticsComponents.kt sourceDisplayName
  Problem: 77 of the 91 entries left in the hardcoded-string baseline are English messages set in ViewModel state, thrown as exception messages or built by media ingestion, so Chinese users still see English errors and confirmations. Labels for Aura's own sources (Community, Local, AI generated, Aura Originals) are also spelled in Kotlin outside Resources.wallpaperSourceName, and the image-format error joins formats with an English "or".
  Fix: Give ViewModel state a UiText type (a resource ID with arguments, or a provider's own message) that Compose resolves. Have MediaIngestion return a resource ID and arguments instead of a sentence, and join format names with ListFormatter on API 33+ and a resource conjunction below it. Route the own-source labels through Resources.wallpaperSourceName and its sound counterpart.
  Acceptance: Every baseline entry whose reason points here is gone and write mode has pruned it; Chinese shows translated text for a failed upload, a blocked creator, a disabled source and a rejected image format; what's left in the baseline is brand names, file names and internal signals.
  Confidence: Verified
  Effort: M

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

### P3

- [ ] P3 — Keep a hidden duplicate's attribution on the wallpaper card that stays
  Category: ux
  Where: `app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperFeedQuality.kt` (`coalesceContentDuplicates`); `Wallpaper` model; wallpaper detail credits
  Problem: when two posts carry the same image, the feed now shows it once and drops the second post. The dropped post's author and source page aren't shown anywhere.
  Fix: carry the hidden posts as attribution aliases on the card that stays, and list them in the detail screen's credits.
  Acceptance: a coalesced card's detail screen names every post that carried the image, each linking to its own source page.
  Effort: S


## Drain Leftovers — 2026-09-30

Remainders of items closed in the 2026-09-30 drain whose full acceptance did not land.

### P2

- [ ] P2 — Prove offline rotation on a phone and finish its follow-ups
  Why: rotation work no longer carries a network constraint and the check moved into the worker (`RotationNetworkGate`), but nothing has run on a device. Favorites and collection sources still wait for a network even when their pictures are already on the phone, and the catch-up one-shot writes its receipts under the caller's work name.
  Evidence: `RotationNetworkGate.kt`, `AutoWallpaperWorker.kt` (`auto_wallpaper_catch_up`, `relaxStoredNetworkConstraint`), `RotationTriggerService.kt`; `readRotationNetworkState` has no JVM test.
  Touches: `AutoWallpaperWorker.kt`, `RotationNetworkGate.kt`, `BackgroundWorkDiagnosticsReader.kt`, a device session.
  Acceptance: in airplane mode with a wallpaper saved, a periodic tick and an unlock trigger both apply it; with nothing saved, Settings shows no error, one `auto_wallpaper_catch_up` waits in `dumpsys jobscheduler` and fires on reconnect; with Wi-Fi only on mobile data nothing is fetched and the saved wallpaper still applies; a tile tap offline shows the toast; an upgrade from the published build keeps its next fire time and loses the constraint; scheduling with the idle option no longer crashes; favorites and collection rotation applies an on-device picture offline; the catch-up has its own diagnostics row.
  Complexity: M

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

### P3

- [ ] P3 — Finish resumable downloads and prove a resume on a phone
  Why: interrupted downloads now resume from a kept partial file, but only on paper and in JVM tests. A download started from a detail screen still dies with that screen, signed links never resume, and a cancelled transfer waits for the next read before it lets go of the socket.
  Evidence: `ResumableDownloadTransport.kt` resumes only when the URL matches exactly and the server sent a strong ETag (a weak one blocks it even with a usable Last-Modified); `DownloadManager.kt` restores interrupted cards when Hilt first builds the manager, not at app start; a file that arrived whole but failed to publish is reused on retry without revalidating; cancellation is checked between reads and the OkHttp `Call` isn't cancelled.
  Touches: `DownloadManager.kt`, `ResumableDownloadTransport.kt`, `DownloadResumeLedger.kt`, the detail screens' ViewModels, a device session.
  Acceptance: on a phone, a download cut by airplane mode resumes with a 206 and leaves one MediaStore copy; killing the app mid-download brings back an interrupted card whose Retry finishes it, and TalkBack reads that card; Settings > Clear cache removes the partials; a batch favorites download resumes after a WorkManager retry; a download started from a detail screen survives leaving the screen; cancelling closes the connection at once; the weak ETag and signed link limits are either lifted or written down in the download docs.
  Complexity: M

- [ ] P3 — Finish the TalkBack announcements and hear them on a phone
  Why: feeds, downloads and sound previews now announce through polite live regions, but nobody has listened to them. Playback announcements cover the Sounds feed and sound detail only, and the gate checks that some live region exists on each async surface, which the older status banners already satisfied.
  Evidence: `ui/components/LiveAnnouncements.kt` (`AuraFeedStatus`, `PreviewPlaybackStatus`, `AuraFeedSection`); `SoundEditorScreen.kt` and `DeviceSoundsScreen.kt` still rely on the play button's state description; `docs/qa/accessibility-release-gate.json` carries a boolean `liveRegion` per surface; there is no Sounds feed scenario in `ProductionRouteState.kt`.
  Touches: `LiveAnnouncements.kt`, `SoundEditorScreen.kt`, `DeviceSoundsScreen.kt`, `ProductionRouteState.kt`, `AccessibilityReleaseGateTest.kt`, `tools/accessibility_release_gate_check.py`, a device session with TalkBack.
  Acceptance: the Sound Editor and Device Sounds screens announce a preview starting and stopping; the gate names the live node it expects per scenario by tag and a Sounds feed scenario exists; on a phone each of loading, ready, error and empty is spoken once per feed, a focused play button doesn't say "Playing" twice, a finished download isn't read again when a row above it is dismissed, and swipe order is header, notices, list in both the bar and rail layouts; the "loaded" line either clears after a few seconds or the reason it stays is written down.
  Complexity: S

- [ ] P3 — Make the wallpaper grid screenshots independent of image load timing
  Why: the grid screenshot tiles point at images that never load in a JVM test. One verify run in three caught the tiles before the load had failed, so unchanged code produced two different pictures and the run failed with nothing wrong.
  Evidence: three runs on 2026-10-13 at `9730be42`. A verify run failed on `wallpapers_grid_success_amoled_1280x800.png` with empty dark tiles, then a record run and a second verify both matched the committed broken-image baseline. `ProductionRouteState.kt` `WallpapersGridState()` feeds both grid baselines.
  Touches: `app/src/main/java/com/freevibe/ui/qa/ProductionRouteState.kt`, `app/src/test/java/com/freevibe/ui/qa/ProductionRouteStateScreenshotTest.kt`, the grid baselines under `app/src/test/screenshots/`.
  Acceptance: the grid scenarios render through a test image loader that settles before capture (a fixed bitmap or a fixed error), ten verify runs in a row pass, and the baselines show the state the scenario is named for.
  Complexity: S

- [ ] P3 — Carry contact ringtones in the scheduled auto backup and let a remembered one be forgotten
  Why: the manual library export now carries contact ringtones, but the scheduled auto backup goes through `FavoritesExporter` and leaves them out, so someone who relies on the automatic file loses them anyway. Aura's list of remembered contact ringtones also has no way to drop an entry, so a contact whose ringtone was later changed in the Contacts app keeps exporting the old sound.
  Evidence: `ContactRingtoneBackupManager.exportEntries()` is called only from `LibraryExporter.exportLibrary`; `FavoritesExporter` has no `contactRingtones` section; the list in `PreferencesManager.contactRingtoneLedgerJson` is written by `ContactRingtoneBackupManager.record` and never trimmed; `ContactRingtoneService.clearContactRingtone` exists but no screen calls it.
  Touches: `app/src/main/java/com/freevibe/service/FavoritesExporter.kt`, `app/src/main/java/com/freevibe/service/ContactRingtoneBackup.kt`, `app/src/main/java/com/freevibe/ui/screens/sounds/ContactPickerScreen.kt`, `docs/data/export-format.json`, tests.
  Acceptance: the scheduled backup file carries the same `contactRingtones` section as the manual export and restores through the same path; the contact picker can clear a contact's ringtone and that removes it from the remembered list; a restore on a phone with two same-named contacts is seen changing neither on a real device.
  Complexity: S

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


## Issue Intake (2026-09-26)

Open GitHub issues checked against this list on 2026-09-26. The only open issue is #47 (translation call, help wanted). It is covered by the P2 item above that cites it ("Reported: #47"): Simplified Chinese landed through PR #48 on 2026-08-12, and the issue stays open as the umbrella for further languages. No new items.
