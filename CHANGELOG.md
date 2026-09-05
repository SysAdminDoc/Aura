# Changelog

All notable changes to Aura will be documented in this file.

## Unreleased

- **The contributor docs now match the build, and a check keeps them there**:
  `ARCHITECTURE.md` described a "Favorites" bottom tab and Room database v14
  against a shipped "Library" tab and v17, and `CONTRIBUTING.md` asked for
  "JDK 17+ and Android SDK 35" when the build compiles against SDK 36 and Gradle
  refuses any JDK newer than 21. Neither file was covered by any check. Both are
  corrected, both are now read by the release gate, and it understands the boxed
  diagram format `ARCHITECTURE.md` uses so a stale tab name there fails the build
  rather than sitting unnoticed.

- **The distribution runbook stopped contradicting its own tooling**: it told the
  reader that F-Droid mainline was blocked and that the preflight command would
  report `blocked`, when that command had been reporting `ready-for-review` ever
  since the FOSS flavor isolated Firebase and Play Services. Nothing was checking
  the document, so it drifted twice unnoticed. It now records what each
  distribution channel actually means for the verification decision, including
  Accrescent, and a new check fails the build if the document disagrees with the
  preflight command again or if its review date passes.

- **The 16 KB page-size check now reads inside the packed native payloads**: FFmpeg
  and Python ship as archives renamed to `.so`, and the release gate had been
  recording each one as "skipped". That left roughly 250 native libraries per
  architecture completely unmeasured. The gate now opens them, and on 64-bit builds
  it went from checking a handful of segments to checking over 900. It found five
  WebP libraries inside the FFmpeg payload that are built for 4 KB pages rather than
  16 KB. They come prebuilt from an upstream project, so they are recorded with the
  reason and the upstream report rather than quietly ignored, and the check fails if
  a sixth appears or if one of the five is silently dropped.

- **Store release notes for 6.45.1 and 6.45.2 are complete again**: both Fastlane
  changelogs were written without the "Recent highlights" opening the release gate
  requires, so neither would have passed a publish check. Both now carry it.

- **The release metadata policy tracks the build again**: it had been left at
  6.45.0 / versionCode 146 across two version bumps. That single stale pair was
  failing nine of the repository's release gates, which masked anything else they
  would have caught. The version drift check now names the file to edit and both
  values instead of only reporting a mismatch, and the bump checklist in
  `CONTRIBUTING.md` lists every file a version change has to touch.

## v6.45.2

- **App bundle builds work again**: `bundleFullRelease` had failed with a
  resource duplication error ever since the AGP 8.9 upgrade, because that plugin
  line no longer supports per-ABI APK splits while a bundle task runs. Splits now
  turn themselves off for bundle builds and stay on for APK builds. The AAB still
  carries all four ABIs, so nothing changes for users. One build note: run
  assemble and bundle tasks as separate Gradle invocations from now on, since a
  combined one would produce a universal-only APK set.

## v6.45.1

- **Simplified Chinese**: Aura now speaks Chinese. The full interface translation
  was written and reviewed by native speaker Chloemlla (#48). The app follows the
  system language, and Android 13 and newer list Aura in per-app language
  settings. Wallpaper generator strings ship only in the Full flavor, so FOSS
  builds carry no orphaned translations.

## v6.45.0

- **YouTube sound search now works across Android 8.0 and newer**: Aura builds
  NewPipe's search URL with the encoder overload available since API 1, avoiding
  the API 33-only overload that crashed older devices. Release-minified Full and
  FOSS tests now launch Sounds and exercise offline search on API 26, 27, and 29.

- **Sound Editor transforms now use Android's media stack**: Media3 owns clipping,
  fades, pitch-preserving speed from 0.5x to 2x, M4A, WAV, and available OGG or
  Opus exports. FFmpeg is limited to final MP3, FLAC, unavailable OGG or Opus
  encoding, unsupported lossless cuts, video crop, and the yt-dlp runtime. M4A is
  now the default export format, and fixture tests compare the platform WAV path
  byte for byte. An API 35 device corpus also verifies exact PCM fades,
  pitch-preserving 2x speed, and AAC output. The native payload remains, so legacy
  JNI packaging still cannot be disabled. Signed split APKs grew by about 0.15 MB.

- **FOSS builds now omit the external wallpaper generator**: Stability network
  code, encrypted credential binding, generator screens, settings controls,
  provider copy, BuildConfig key, and baseline-profile entries stay out of the
  FOSS source set. The F-Droid preflight locks that boundary and confirms that
  the FOSS route is a no-op.

- **Runtime yt-dlp updates now require explicit consent**: the update manager
  accepts only a typed confirmation from the Settings warning. Its copy explains
  that a replacement binary bypasses F-Droid or other repository review checks.

- **Screenshot and accessibility gates now exercise production route renderers**: the old
  debug-only route drawings have been removed. Wallpaper, sound, settings, video, and
  editor states now run through the same composables used by the app, with localized
  pseudo-locale, RTL, theme, compact, expanded, and large-font coverage.

- **Settings state and side effects now live in feature delegates**: the ViewModel remains
  the existing screen facade, while rotation, media providers, community identity, and
  diagnostics each own their flows and jobs under the ViewModel lifecycle.

- **Settings search now finds controls, not only sections**: localized row titles and
  descriptions are indexed alongside intentional aliases such as OLED, Wi-Fi, backup,
  App Check, YouTube, and battery saver. Selecting a result scrolls to and highlights the
  exact setting, with production tests covering the supported routes and no-result state.

- **Runtime feedback now follows the localization path**: editor presets and filters,
  editor processing messages, Favorites actions, Settings feedback, and storage sizes use
  resources. The hardcoded-string gate now checks these ViewModel states and editor controls,
  while its baseline keeps unrelated legacy findings visible for later extraction.

- **Shared image and audio entry now opens the existing editors**: granted user-owned
  `ACTION_SEND` and `ACTION_EDIT` files are copied to the bounded app outbox, sniffed, and
  routed to wallpaper crop or Sound Editor. Remote links, multi-file shares, revoked grants,
  malformed payloads, and oversized inputs get a visible recovery message.

- **Local wallpaper rotation now has an indexed catalog**: users can add several SAF folders,
  rescan them incrementally, search and tag indexed images, see duplicate content, repair
  revoked grants, and assign each folder to home, lock screen, or both. Rotation keeps the
  old single-folder preference working while using the catalog without broad storage access.

- **Clock and date overlays are now optional**: new static applications and the weather,
  parallax, and GIF live-wallpaper paths can show localized time, date, or both in one of four
  corner positions. The device's time format and time zone are used, and disabling the setting
  adds no overlay work to the existing live render loops.

- **The Glance widget pin is now documented**: Aura stays on Glance 1.2.0-rc01 because the
  widget's generated-preview API arrived in the 1.2 line, while stable remains 1.1.1 and the
  1.2.0 stable release has not shipped. The catalog records the upgrade trigger and prerelease
  risk next to the pin.

- **Android 16 platform APIs now have guarded integrations**: live wallpaper services publish
  per-instance descriptions and restore their selected media or weather settings, downloads
  use the Android 16 progress-centric notification with a compatibility fallback, and the
  AGSL bitmap pipeline uses RuntimeColorFilter and RuntimeXfermode when they are available.

- **Video wallpaper feed loading is now serialized**: warm-cache results stay visible while the
  network refresh finishes, an immediate pagination request is ignored instead of duplicating
  provider calls, and the next accepted page appends without dropping cached items.

- **Sound Editor exports now support gapless ringtone loops**: OGG output writes Android's
  `ANDROID_LOOP=true` marker, the trim preview wraps from the selected end back to its start,
  and supported unprocessed source files can use a stream-copy cut whose packet bytes are
  checked before export completes.

- **Per-contact ringtones now understand Do Not Disturb**: assignment explains missing
  notification-policy access, blocked calls, disabled priority calls, and unstarred
  contacts. The contact flow opens Android's priority-caller or contact editor screens,
  keeps an explicit assign-anyway path, and adds a VIP-only preset that silences the
  default ringtone after assigning the selected contact tone.

- **New Rotation health screen, in Settings under Diagnostics**: when automatic
  wallpaper change quietly stops, there was nothing to look at. The five reasons it can
  stop all look the same from the home screen: rotation is off, it's waiting for its
  turn, Android is holding it back to save battery, the schedule got dropped after a
  restart, or the last run failed. Each needs a different response. The screen names
  which one it is, shows when rotation last ran and when it is next due, whether Android
  is restricting Aura's background use, and whether a restart was ever seen. There's a
  Run now button, so you can watch a rotation happen instead of guessing. When the device
  refuses to answer something, it says so rather than showing a plausible-looking value.

- **Media playback, image loading, and networking libraries all moved up**: Media3,
  Coil, OkHttp, Navigation, Paging, DataStore, Compose, and the YouTube extractor had all
  been stuck behind one toolchain blocker. Compiling against Android 16's SDK cleared it.
  Nothing changes on screen; this is the groundwork the video playlist and gapless seam
  work needs.

- **Fixed a crash on Android 8.0 when a live wallpaper published its colours**: the call
  that tells the system a wallpaper's colours changed only exists from Android 8.1, and
  Aura made it on all seven of its publish paths. On Android 8.0 that is an immediate
  crash of the wallpaper service. Every engine now goes through one guarded helper, so
  adding a publish point cannot bring the crash back, and a release gate fails the build
  if one calls the platform directly again.

- **Android Lint runs again, and now has nothing muted**: it had been unable to complete
  a single run: three Compose detectors threw against the old build plugin's lint API and
  took the whole analysis down with them, so none of the other checks reported either.
  It had been broken long enough that a detector was switched off to work around it. The
  build plugin is now on a version whose lint matches, the run completes, the workaround
  is gone, and the thirteen real errors hiding behind the failure are fixed.

- **The app compiles against Android 16's SDK while still targeting Android 15**:
  compiling against a newer platform only widens what Aura can call behind version
  checks. None of Android 16's behaviour changes apply, because those follow the target,
  which has not moved. This is what let the media, image-loading, and networking
  libraries move off a blocker they had been stuck behind.

## v6.44.0

- **Aura notices when its live wallpaper is no longer the one running**: a wallpaper
  service dropped after a reboot, replaced by another app, or killed by an OEM battery
  manager looked exactly like a working one: the phone showed a stock wallpaper while
  Aura's settings still read "on". Aura now asks the system which live wallpaper is
  actually running, after a reboot or app update and whenever you open Diagnostics, and
  offers to set yours back in one tap. It stays quiet unless it is certain: if the device
  will not answer, or you never applied an Aura live wallpaper in the first place, nothing
  is shown.

- **Installing an older Aura no longer crashes it on every launch, and no longer wipes
  your library in silence**: Room refuses to open a database written by a newer build,
  and nothing caught that, so an ordinary rollback left the app dead on startup with no
  way out but clearing app data. Aura now recognises the situation before it opens the
  database, copies the existing library aside, and tells you what happened with a pointer
  to backup and restore. If you reinstall the newer version, the copy is picked up again
  and nothing is lost. Where the copy could not be written, usually for lack of space, it
  says that instead of implying your data is still there.

## v6.43.0

- **Grid cells stop redrawing when nothing about them changed**: the models behind the
  wallpaper, video, download, history, and collection lists are now all declared immutable
  to the Compose compiler, so a cell can skip recomposition when its contents have not
  moved. Several of them were not, which meant every cell redrew whenever anything above
  it did. The compiler also emits stability reports again, and a check fails the build if
  a list model quietly loses its annotation, so this cannot drift back. The first report
  reads eleven stable classes and none unstable.

- **A download that is a third the size, because it carries code for your phone only**:
  The single universal APK was 199 MB, and roughly three quarters of that was native
  code for architectures your device will never run. Releases now ship one APK per
  architecture alongside the universal one. On `arm64-v8a`, which is nearly every phone
  made since 2017, that is 60 MB. Obtainium picks the right one automatically; if you
  install by hand and are not sure, the universal APK still works everywhere. Nobody
  loses support: 32-bit `armeabi-v7a` is still built, because Android 8 and 9 devices
  that need it are still in the supported range.

  This does not reach the 30 MB per-APK limit IzzyOnDroid sets, and splitting was never
  going to. What is left is the FFmpeg and Python payload the sound editor depends on,
  and that is tracked separately.

- **Release gates check what is published, not just what is on disk**: a document can
  satisfy every content check while returning 404 to users, which is how the in-app
  privacy policy button opened a dead link for months with every gate reporting ok. The
  gates can now ask whether a link actually resolves and whether a policy that claims
  something enforces it names a mechanism that exists. Both answers are three-valued: an
  unreachable host is "not checked", never "broken", so an offline build is never failed
  over someone else's outage. The native-alignment policy claimed enforcement by a
  release workflow that was deleted a year ago; it now names the local gates that really
  do enforce it, and deleting one of them fails the build.

- **The wallpaper editor stops throwing away full-size bitmaps, and stops losing your
  depth portrait without a word**: every filter render allocated a new bitmap and
  dropped the one it replaced, so dragging a slider handed the collector up to 64 MiB
  per frame. Displaced bitmaps are now freed a generation later, which is late enough
  that nothing can still be painting them and early enough that the editor never holds
  more than one. Composing a depth portrait and then touching any filter used to discard
  the composition in silence; the editor now says so. Apply, export, and parallax also
  read the editor's current state instead of a snapshot taken before their work started,
  so they no longer write out the previous frame.

- **Name the Android 17 memory-limiter shutdown instead of letting it look like nothing**:
  Android 17 caps how much memory any app may hold, whatever SDK it targets, and when
  it kills a process there is no exception and no stack trace. The diagnostics bundle
  reported such a death as an ordinary exit and the crash log stayed empty, so the one
  failure mode Aura is most exposed to was the one users could not report. Recent exits
  are now labelled when Android attributed them to the limiter, the count appears in
  Settings beside the crash-log state, and the bundle records the wallpaper editor's
  worst-case peak allocation against the ceiling it is held to. Raising the editor's
  size cap past that ceiling now fails a test.

- **Live wallpapers now theme the rest of your phone**: none of the three wallpaper
  engines answered the system's request for wallpaper colors, so while an Aura live
  wallpaper was on screen the launcher, quick settings, and every app that follows
  Material You themed from nothing. All three now publish a palette: the weather and
  parallax engines derive it from the image they decoded, the video engine from one
  representative frame, and a shader preset publishes the palette it was authored with.
  Colors are recomputed only when the wallpaper source actually changes, never per
  frame, and the quantizing happens on the decode thread rather than the render path.
  A new Settings toggle turns publication off for anyone who does not want their
  launcher recolored.

- **Security: bound yt-dlp downloads before any bytes are written**: the video-wallpaper
  import passed no size cap, and the 256 MB ceiling was only checked once the file had
  already been written in full, so a long video wrote gigabytes to the device and was then
  rejected. Both download branches now pass `--max-filesize` and `--no-playlist` up front.
  The Reddit HLS path also moves its finished file into place instead of copying it, so a
  download no longer needs twice the video's size on disk at once. A gate counts yt-dlp
  executions against bounded downloads, so a new branch that forgets the cap fails the
  build rather than shipping, a forbidden-option scan cannot see an option nobody passed.

## v6.42.0

- **Release: publish the versions that were finished but never shipped**: v6.39.0,
  v6.40.0, and v6.41.0 were tagged and left unreleased, so the download page still served
  v6.38.1 and Obtainium silently held everyone there. A gate now fails when the declared
  `versionName` has no matching git tag *and* no published GitHub Release, closing the half
  that a tag-only check missed. The release check is skipped rather than guessed at when
  GitHub cannot be reached, so an offline checkout still builds.

- **Build: enable the Gradle build cache, parallel execution, and the configuration
  cache**: a clean `:app:testFullDebugUnitTest` drops from 7m08s to 5m35s, and 28s when
  the caches are warm. No task reported a configuration-cache problem. The heap moved to
  3072m because parallel workers need more headroom than a serial build, with metaspace
  capped so a runaway processor fails instead of taking the machine down. Isolated
  Projects stays off while it is incubating.
- **Reliability: keep SharedPreferences out of the UI layer, and let the gate find its own
  scope**: onboarding state, the Pixabay video feed cache, and the video-wallpaper
  selection now persist through the data and service layers instead of composables reaching
  for `getSharedPreferences`. The write-order gate discovers preference bridges by reading
  `PreferencesManager` rather than consulting a hand-written list, so a newly added bridge
  is policed the moment it is written, and it now rejects direct preference access anywhere
  under `ui/`. A known set of bridges is still required to keep bridging, which discovery
  alone cannot detect.
- **Docs: check stated version facts against the build**: README and the working notes
  are now compared with `app/build.gradle.kts`, the exported Room schema, and the real
  bottom-navigation destinations, so a stale Room version, version badge, versionCode, or
  tab list fails the release-metadata gate instead of shipping. The first run caught a tab
  list naming a "Favorites" tab the app has not built since it became Library, and the
  release dry-run walkthrough was still worked through v6.34.6.
- **Docs: publish the contributor guides that returned 404**: `CONTRIBUTING.md` and
  `ARCHITECTURE.md` were caught by the blanket `*.md` ignore rule, so GitHub showed no
  contributing guidelines and the architecture overview was unreachable. Both are tracked
  now, and the link gate walks every tracked root-level markdown file and resolves every
  relative target instead of only `docs/`-prefixed ones.
- **Docs: correct the contributing guide**: it described a roadmap with item IDs, an
  Appendix, and Now/Next/Later tiers that no longer exist, told contributors to run
  ambiguous unqualified Gradle tasks, said screenshot tests were still queued when
  Roborazzi has been running for months, and linked a `docs/plugins/` directory that was
  never created.
- **Build: restore dependency verification on a cold cache**: four Maven metadata
  artifacts had no recorded checksum, so `checkFullDebugAarMetadata` failed before
  compiling anything on a fresh clone. Each added digest was verified against the
  checksum published by repo1.maven.org rather than trusted from the local cache.
- **Reliability: route weather-wallpaper settings through the data layer**: daily wallpaper,
  VFX, and touch-effect writes now go through `PreferencesManager`; the preference gate scans
  every settings source file for direct runtime `SharedPreferences` writes.
- **Reliability: stream ordinary wallpaper applies**: URL, file, and content sources now go
  through `WallpaperManager.setStream` with the existing 64 MiB cap; bitmap decoding remains
  for edited and pixel-transformed output, and oversized chunked responses fail visibly.
- **UX: shuffle no longer immediately repeats**: rotation excludes a recent history window
  scaled to the fetched candidate pool, while one-item sources still make progress and
  sequential rotation remains unchanged.
- **Security: make inbound wallpaper and cleartext policy explicit**: the network security
  config now denies cleartext in a declared base policy, and `ACTION_ATTACH_DATA` accepts only
  provider-backed image URIs carrying an explicit read grant.

## v6.41.0 (2026-08-10)

- **Fix: the JVM unit test suite could not compile**: `groupingBy` emits an anonymous
  `Grouping` class carrying no Kotlin metadata, and Kotlin 2.1.0's incremental compiler asserts
  when it reads that class back (`Couldn't load KotlinClass`). In `app/src/test` this was fatal:
  `compileFullDebugUnitTestKotlin` aborted, so no unit test in the project could run. The two
  uses in `BackgroundWorkDiagnosticsReader` were survivable but silently forced a full
  non-incremental recompile on every build while still reporting `BUILD SUCCESSFUL`. All three
  now use `groupBy`, and `tools/kotlin_toolchain_hazard_check.py` fails the build if the
  construct returns.
- **Fix: source files no longer carry bytes that hide them from tooling**: a raw NUL byte in
  `AuraOriginalsDownloader.kt` made ripgrep report `binary file matches` and refuse to display
  the file; the line it concealed was the path-traversal guard. Repaired that byte, three U+FFFD
  replacement characters in `VoteRepository.kt`, and the line endings of 54 files. A new
  `.gitattributes` pins tracked text to LF, 14 files had mixed endings, including two that
  release gates hash, and `tools/source_byte_hygiene_check.py` now rejects NUL bytes, U+FFFD,
  invalid UTF-8, and stray carriage returns across all 784 tracked text files.
- **Security: patched two live advisories in the community backend**: `functions` resolved
  `protobufjs` 7.6.4 (GHSA-j3f2-48v5-ccww, denial of service via infinite loop in `.proto` option
  parsing) and `body-parser` 1.20.5 (GHSA-v422-hmwv-36x6, request size enforcement silently
  disabled by an invalid `limit`). `protobufjs` was held at the vulnerable version by an override
  originally added *as* a security pin.
- **Fix: every documentation link in README and the app now resolves**: `.gitignore` excluded
  all markdown except README, so none of `docs/` was ever published. All 11 documentation links
  in README returned 404, as did the privacy policy that Settings > About opens. The 50
  documents README, the app, and the release gates reference are now tracked; the 162-file
  factory-loop research archive stays local, and agent working notes remain untracked.
- **New gate: links are checked against what is published, not what is on disk**:
  `tools/published_state.py` adds tracked-in-git and tag-exists predicates, and
  `tools/docs_link_check.py` walks every `docs/*.md` link in README and app source and fails
  when one would 404. The privacy policy gate now uses the same predicate, so it can no longer
  report `ok` for a document nobody can open.
- **Fix: live-wallpaper settings no longer strand on the old value**: five Settings toggles
  (reduce animations, adaptive tint, tint intensity, live-wallpaper dimming, shader preset)
  wrote DataStore before the SharedPreferences bridge the wallpaper engines actually read.
  Backing out of Settings cancelled the coroutine between the two writes, leaving the live
  wallpaper on the old value permanently while the toggle read as changed. All five bridges
  moved into `PreferencesManager`, which already codified the correct order for the video
  settings, and `tools/preference_write_order_check.py` now holds all nine bridges to it.
- **Privacy: community voting no longer touches the network before you opt in**:
  `VoteRepository` attached a Firebase Realtime Database moderation listener from its `init`
  block with no consent check, while every other entry point in the class gates on
  `isCommunityAccessEnabled()`. Both consent preferences default to off and the class is a
  singleton constructed as soon as the Videos or Settings tab opens, so a user who never
  enabled community features still opened an RTDB socket for the lifetime of the process, and
  the listener was never removed. The listener now follows the consent preferences: it attaches
  only once community features and the guidelines are both accepted, detaches when either is
  withdrawn, and clears cached moderation hides with the socket.
- **New gate: npm overrides cannot silently rot**: `tools/npm_override_policy_check.py` and
  `docs/security/npm-override-policy.json` record the advisory floor behind every pin and fail
  when the manifest or the resolved lockfile drops below it, when a shipped override is not
  policed, when a pin uses a range operator instead of an exact version, or when an entry cites
  no advisory.

## v6.40.0 (2026-07-29)

- **Fix: live wallpapers no longer pile up decode threads**: the weather and parallax engines both
  start a wallpaper decode from `onSurfaceCreated` *and* `onSurfaceChanged`, each on a bare thread
  with no coordination, so every surface churn (rotation, unlock, launcher restart, preview
  teardown) started another full-screen decode alongside the ones still running, inside a process
  that is never restarted. Decodes are now serialized per engine with at most one waiting behind
  the one running, since a third request would only produce the state the waiting one is about to.

- **Fix: parallax frees its layers when the surface goes away**: the engine held up to four
  full-screen bitmaps and a native ML Kit segmentation client until the engine itself was
  destroyed, even though a destroyed surface cannot draw any of it. They are now released with the
  surface and rebuilt on the next one, and the accelerometer listener is registered and released
  exactly once instead of relying on repeated unregister calls.

- **New: cross-engine live-wallpaper lifecycle soak harness**: video, GIF, weather, and parallax
  are driven through repeated surface create/change/destroy, visibility, battery-saver,
  unlock, and file-replacement cycles, and each engine now reports what it holds (players, posted
  callbacks, sensor listeners, receivers, decoded bitmaps, segmenters, decode threads) straight
  from its own state. The soak asserts nothing survives `onDestroy` and that peak usage stays
  within what one engine can hold at once, so anything accumulating per cycle fails. The same
  scenario script runs on the JVM and, for real decoders and real sensors, on an emulator.

## v6.39.0 (2026-07-29)

- **Fix: offline favorites render offline**: wallpaper favorites are cached to a managed local
  file when saved, but the grid still requested the remote thumbnail, so airplane mode or a cold
  image cache showed broken cards over bytes already on disk. The grid now prefers the existing
  local file (no second copy), falling back to the remote URL when it has been evicted; sound
  favorites are unaffected.

- **Fix: every wallpaper apply commits through one coordinator**: browsing recorded history,
  undo, style learning, the night-variant locator, and feedback, while the editor, crop, and AI
  screens called `WallpaperApplier` directly and skipped all of it, so a wallpaper applied from
  the editor never appeared in history and could not be undone. Each surface now declares a
  persistence policy (browse / derived output / background) and commits through
  `WallpaperApplyCoordinator`: nothing is written unless the system call succeeded, each effect
  happens exactly once, and cancellation or failure leaves no history row, no stale locator, and
  no false success.

- **Deleting downloads and collections is now undoable**: a downloaded file was destroyed the
  moment you tapped delete, and a whole collection vanished outright, even though removing a
  single item from a collection already offered Undo. Deleting a download now moves its file
  into a bounded staging area (100 entries, 24-hour retention) and keeps its row, so Undo
  restores both; a `content://` MediaStore entry is only released at purge time. Deleting a
  collection returns a snapshot that restores the collection and every membership row.

- **Release truth comes from one manifest**: versionName, versionCode, and the Room schema
  version are read from `app/build.gradle.kts` and `Database.kt`, and the README badge, the
  release-metadata policy JSON, and the Fastlane changelog are generated from them by
  `tools/release_manifest.py`. The two release gates that had the version hardcoded now derive
  it, so a bump can no longer leave four artifacts stale; the YouTube store-risk check is
  mandatory and fails closed if the capability registry ever puts YouTube on the Play channel
  without recorded owner-approved evidence.

- **Fix: video wallpapers recover from silent decoder death**: the engine had no error
  listener, no progress watchdog, and no rebuild budget, so a decoder that died across an OEM
  sleep/wake cycle left a frozen wallpaper until the video was re-picked. Prepare errors,
  runtime errors, and a player that reports "playing" while its position stays frozen now all
  route through a bounded recovery policy: rebuilds back off exponentially (1s to 30s), resume
  at the position where playback stopped rather than restarting, stop after four attempts, and
  on exhaustion hold the last rendered frame instead of entering a restart loop. Every
  transition is recorded as a diagnostic receipt, and a long healthy run restores the budget.

- **Fix: wallpaper editor source-loading races**: Apply stayed enabled while the image was
  still downloading, filter sliders moved before the decode finished were recorded but never
  rendered, and an older URL's decoded bitmap could land on top of a newer source because loads
  had no ownership. Loads now cancel and carry an ownership token, so a stale success is dropped
  and its bitmap recycled and a stale failure cannot raise an error for a source the user has
  replaced; Apply, export, and parallax wait for a decoded source; and pending filter parameters
  replay as soon as the source lands.

- **Fix: Sound detail readability at default font scale**: on a 411x891 phone the four
  secondary actions shared one row and ellipsized "Contact", while the source-policy and
  permission explanations were capped at two lines and cut mid-sentence. The action row now
  reflows to a 2x2 grid from the real available width (not just font scale), and the policy and
  permission copy wrap in full. Source badge colors gained per-theme tones so every provider,
  YouTube red was ~4.0:1 on white, now meets the WCAG 2.2 4.5:1 normal-text target on both the
  light and AMOLED surfaces, verified by a contrast test over every `ContentSource`.

- **Fix: a transient 403 no longer permanently disables a saved item**: any failure mentioning
  403 was treated as proof the source was gone, so a refused or throttled request stuck a
  permanent "unavailable" badge on a wallpaper or sound that was fine. Failures are now
  classified: only an explicit removal or a 404/410 is permanent; 401/403/408/429/5xx, timeouts,
  and transport errors are transient and persist nothing. A later successful apply or download
  clears any previously recorded unavailable state.
- **Provider capability registry**: lifecycle, build flavor, distribution channel,
  configuration, permission, health, attribution, default state, kill switch, and network
  endpoints now live once per content source in `ProviderCapability`. A contract gate fails the
  build when the disclosure list, runtime-control list, or endpoint manifest disagrees with it,
  or when an impossible combination appears (a legacy source that can still fetch, an active
  networked source with no declared endpoint, a credential- or permission-gated source shipped
  enabled). The gate immediately caught Wikimedia Commons being documented as a dormant legacy
  source while `WallpaperRepository` fetched its Picture of the Day on every Discover refresh;
  its disclosure, runtime control, and network policy now say so. Diagnostics and the licenses
  screen render the registry rather than a second hand-maintained description.

- **Fix: whole-library restore is versioned, atomic, and honest about what it dropped**:
  import previously ignored the payload version, wrote favourites, collections, search history,
  and preference blobs one after another, and silently discarded every `file://`/`content://`
  locator, so one failure could leave a half-merged library and a device transfer could quietly
  lose AI-generated and local items. Restore now parses, version-checks, migrates, and validates
  the whole payload into a plan *before* the first write; v1 payloads are migrated explicitly
  (their `downloads` section is reported, not dropped); payloads with a missing, corrupt, or
  future version are refused outright; all database writes run in one Room transaction with the
  two preference blobs rolled back on failure, so an error leaves the pre-import state intact;
  and the result now reports every skipped row grouped by reason, invalid, non-portable,
  duplicate, over-limit, dropped-by-migration, instead of a bare count.

- **Fix: generated wallpapers are deleted only after the last reference goes**: pruning past
  the 50-file cap, and unfavouriting, deleted the PNG outright, so a generated wallpaper that
  was still in a collection, in history, pinned to a day/night slot, or used by a 24H wallpaper
  pack turned into a broken card or a rotation that silently stopped working. A new
  `GeneratedAssetReferenceIndex` checks favourites, collections, history, the wallpaper cache,
  downloads, day/night slots, the last night-variant locator, and wallpaper-pack slots (across
  all three legal locator spellings) before anything is deleted; referenced files no longer
  count against the cap, orphan cleanup can only ever touch Aura's own managed directory, and
  Settings › Storage now reports in-use, reclaimable, and stale-reference counts.

- **Security: one automation gate for every exported entry point**: `MainActivity` is exported
  and accepted the `ROTATE_NOW` / `SHUFFLE_NOW` actions directly, enqueueing rotation work
  without the opt-in consent and 30-second throttle that `TaskerActionReceiver` applies. Both
  surfaces now route through a shared `ExternalAutomationDispatcher`, so a disabled, malformed,
  or rate-limited request enqueues nothing from either path, an accepted one enqueues exactly
  once, ordinary launcher shortcuts are untouched, and diagnostics record which entry point the
  request arrived on.
- **Security: bounded archive extraction**: theme-pack import now runs through a shared
  `ArchiveExtractionGuard` that rejects path traversal, absolute/UNC/drive-letter names, control
  characters, link entries, entry floods (512 max), oversize entries, oversize archives, and
  entries expanding past a 200:1 compression ratio, deleting the staging directory on any
  failure.
- **Security: commons-compress 1.28.0**: `youtubedl-android` 0.18.1 resolved commons-compress
  1.12, which carries published archive-expansion DoS advisories. A dependency constraint now
  pins the reviewed 1.28.0 release (binary-compatible with the `ZipFile` /
  `ZipArchiveInputStream` API `youtubedl-common`'s `ZipUtils` binds to) across full and FOSS
  builds, and the dependency notice lock was regenerated from the real `fullRelease` graph
  (303 dependency records / 309 notice sections; it had drifted against a pre-flavor `release`
  variant).

## v6.38.1 (2026-07-29)

- **Fix: YouTube ringtones failed with "not audio" (#44)**: YouTube frequently serves audio as
  Opus in a WebM container, whose EBML signature the media sniffer did not recognize, so valid
  downloads were rejected before they could be applied. WebM is now sniffed as a container and
  resolved to `audio/webm` in audio flows.
- **Build: release lint**: disabled the `NullSafeMutableLiveData` lint check, whose detector
  crashes under the pinned AGP 8.7.3 / Kotlin 2.1.0 toolchain and blocked release builds; the
  app uses StateFlow, so the check had nothing to inspect.

## v6.38.0 (2026-07-23)

- **Settings search**: a search field at the top of Settings filters the section list by title
  and description, so scroll-buried areas (Backup, Diagnostics, external services/API keys) are
  reachable by keyword instead of scrolling.

## v6.37.0 (2026-07-23)

- **Undo when removing from a collection**: long-press removal now offers an Undo action that
  re-adds the wallpaper, matching the favorite-removal pattern.
- **Rotation reliability coverage**: added a WorkManager-integration test harness
  (`AutoWallpaperWorkerSchedulingTest`, via `androidx.work:work-testing`) that verifies the
  auto-wallpaper periodic work re-arms idempotently and that the Wi-Fi-only preference produces
  an UNMETERED constraint, guarding the changer-stall/metered-fetch failure class.
- **Undo when hiding a sound or video**: hiding a community sound or video now shows a snackbar
  with an Undo action (backed by `VoteRepository.undoDownvote`, mirroring the existing
  favorite-removal pattern), so an accidental Hide is recoverable instead of permanent.
- **Accessible pagination**: the load-more footer on the wallpaper, sound, and video lists now
  shows a "Loading more…" label with a polite live region (shared `LoadMoreIndicator`), so
  TalkBack announces progress and the fetch is distinguishable from a hung list.

- **Coil 3.2.0 → 3.4.0**: upgraded the image pipeline and enabled
  `memoryCacheMaxSizePercentWhileInBackground` (15%) so the bitmap cache shrinks while the app
  is backgrounded, lower off-screen RAM for a wallpaper app. (3.5.0 requires compileSdk 36 →
  blocked on N-1.)
- **Media3 1.8.0 → 1.9.4**: enabled `experimentalSetDynamicSchedulingEnabled(true)` on the
  video-wallpaper ExoPlayer players (feed, immersive, preview, crop) for a more power-efficient
  playback loop. (1.10.1 requires compileSdk 36 → blocked on N-1.)

## v6.36.0 (2026-07-16)

Content-first discovery overhaul with persistent media feeds, swipe-first previews,
and a no-OAuth Reddit RSS pipeline.

- **Documented Aura's local-first model**: the README now states the app's
  account-free core, lack of ads and internal credits, explicit AI labeling and
  filtering, and device-local offline-library behavior in a factual comparison.
- **Clarified Android developer-verification installs**: README install guidance
  now covers checksum verification, signature-safe ADB updates, and Android's
  one-time advanced flow. The distribution runbook records the decision to
  register Aura's existing package and signing certificate while leaving only
  the identity-sensitive Android Developer Console work owner-gated.
- **Bounded persistent sound-feed caching**: the shared sound cache now removes
  expired entries during writes, caps feed/search snapshots with least-recently
  used eviction, refreshes recency on reads, and is injected as the single Hilt
  instance used by Sounds instead of bypassing its singleton lock.
- **Stopped Reddit cooldown retries from repeating every wallpaper provider**:
  deferred RSS pages now retry only Reddit, merge into the current tab/page,
  retain secondary pagination, and abandon stale retries after navigation.
- **Preserved raw Reddit motion cursors in the video cache**: fresh and stale
  cached RSS pages now retain the final Atom entry plus explicit exhaustion
  state, so filtered text/gallery tails no longer make pagination overlap.
- **Avoided ineffective video preview prebuffers**: the preview disk cache now
  accepts only progressive MP4/WebM URLs and skips HLS manifests, GIFs, local
  content, and unknown stream shapes before allocating a cache writer.
- **Made Reddit wallpaper and video communities configurable**: Settings now
  provides validated, keyboard-safe editors for both feeds, with bounded lists,
  duplicate cleanup, optional `r/` prefixes, and automatic safe-default restore.
- **Added automatic dark/OLED wallpaper variants**: an optional setting now
  black-point-darkens manual and automated applies during system dark mode or a
  scheduler night window, then reapplies the exact original source in light mode.
- **Enforced image format policy before local decodes**: apply, rotation, crop,
  and editor paths now verify sniffed image bytes and surface format/Android
  compatibility guidance instead of collapsing unsupported input into decode errors.
- **Localized action-layer feedback**: wallpaper apply/style, community sound,
  personal recording, and generated-wallpaper outcomes now resolve through app
  resources instead of embedding English messages inside ViewModels and delegates.
- **Recovered denied wallpaper-trigger starts**: Android background launches now
  persist unlock/screen-off service requests, alert the user that triggers are
  paused, and automatically re-arm them once Aura is visibly resumed.
- **Added generated widget previews**: Glance 1.2 now publishes a responsive,
  deterministic Aura widget preview to Android 15+ pickers once per app version,
  without loading account, network, or wallpaper-history data.
- **Labeled media browse surfaces for TalkBack**: video browse cards, immersive
  previews, and sound rows now announce the media title together with the action,
  while embedded player internals stay out of the accessibility tree.
- **Added a Next wallpaper Quick Settings tile**: the system tile mirrors Aura's
  automatic-rotation state and queues the same one-shot worker used by trusted
  automation, including when periodic rotation is disabled.
- **Refreshed paging, JSON, and QR dependencies**: Paging 3.3.6, Moshi 1.15.2,
  and ZXing 3.5.4 bring bounded bug fixes without changing Aura's public formats.
- **Migrated the image stack to Coil 3.2 and Compose 1.8**: every image surface,
  singleton cache, OkHttp fetcher, GIF decoder, and widget bitmap conversion now
  uses Coil 3, aligned by the June 2025 Compose BOM.
- **Kept failed community-sound deletions actionable**: the detail screen now waits
  for Firebase deletion to succeed before navigating back, so failures remain visible
  and can be retried in place.
- **Linked Library directly to backup controls**: the Backup & restore row now carries
  a Settings section anchor and scrolls the destination to the backup controls.
- **Made whole-library exports portable by construction**: version 2 backups no longer
  include download rows whose local paths only exist on the exporting device; legacy
  backups remain importable and their non-portable download rows are ignored.
- **Added a video wallpaper motion guard**: the existing automatic battery control
  now holds videos and GIFs on a static frame while Android Battery Saver is active,
  resumes the retained decoder when saver exits, and reports the state in Settings.
- **Made FOSS release builds reproducible**: an unsigned two-root verification lane
  now compares raw and signature-stripped APK digests, serializes release shrinking,
  and stabilizes Jackson's nondeterministic R8 class merge for IzzyOnDroid evidence.
- **Labeled AI-generated community content**: new sound and wallpaper uploads carry
  explicit AI disclosure metadata, generated wallpapers are auto-labeled when shared,
  and both community feeds can hide declared AI content without hiding legacy uploads.
- **Added day/night wallpaper scheduling**: auto-rotation can keep one source,
  switch between independently selected day and night sources at configurable
  clock boundaries, or follow the system light/dark theme. Phase sources and
  hours persist in DataStore, continue through WorkManager after reboot, fall
  back safely to the main source when unset, and update network constraints when
  their mode or source changes.
- **Upgraded the sound editor for precise exports**: trim bounds now accept exact
  milliseconds, the waveform supports anchored pinch zoom and pan, linear/smooth/
  exponential fades run in the same sample-accurate FFmpeg pass, and MP3, OGG,
  Opus, WAV, FLAC, or M4A exports offer compatible bitrate choices before saving
  to Music/Aura.
- **Removed obsolete sound and Reddit JSON plumbing**: the permanently empty
  Top Hits flow no longer threads through sound playback, detail, selection, and
  moderation code, while the retired Retrofit Reddit API, DTO mapper path, Hilt
  provider, and stale baseline-profile symbols are gone.
- **Hardened Android 16 background scheduling**: the checked ledger now covers all
  nine `CoroutineWorker` implementations and all 10 unique work names, rejects
  unaudited direct `JobScheduler` or long-running worker additions, and documents
  the one foreground-service-concurrent rotation path. Settings and copied support
  diagnostics expose WorkManager stop reasons such as quota, timeout, background
  restriction, and constraint stops; the device-evidence packet now includes the
  Android 16 TOP/FGS quota compat-override capture and cleanup procedure.
- **Simplified the visual system across discovery, Settings, and Sounds**:
  larger type, flatter grouped rows, underline-based tabs, compact headers,
  on-demand search, consolidated overflow actions, quieter metadata, and a
  waveform-first sound detail layout move primary controls higher in the
  viewport while removing repeated borders and status pills.
- **Made Videos and Sounds content-first**: removed the Video explainer and
  secondary browse rail, removed the Sounds collection and "Top 5" sections,
  shortened motion previews, and moved secondary actions into compact menus.
  Video providers now request 30 items per page and automatically continue to
  a 24-item initial target; animated progress and skeletons replace ambiguous
  blank waits on both feeds.
- **Warm media relaunches and swipe discovery**: sound feeds and fresh signed
  preview URLs now persist across relaunches; audio prebuffer/playback share one
  disk-cache key; NewPipe resolves preview and download audio before falling
  back to yt-dlp; and playback starts with a 250 ms buffer target. Video feeds
  render cached results before background refresh, video preview bytes use a
  bounded disk cache, and tapping a video opens a full-screen vertical pager
  that resolves the current and next item. Wallpaper pager context now survives
  process death and prefetches adjacent full-resolution images.
- **Restored Reddit without an OAuth client ID**: wallpaper and cinemagraph
  discovery now uses one combined public Atom/RSS request at a time, 100-entry
  pages carried forward with the last raw `t3_...` cursor, cursor-keyed persistent
  page metadata, a rate-limit cooldown that survives relaunches, and stale/offline fallback. Reddit
  is the first ranking tier on both wallpaper home feeds; direct-original mobile
  communities replace landscape-heavy or dormant defaults, while preview-only
  gallery thumbnails are excluded. Native `v.redd.it` posts resolve through the
  bundled Media3 HLS playback module instead of being discarded. Aura
  does not use the unofficial pattern found in several OSS clients that embeds
  Reddit's own Android client ID. Direct GIFs animate in the video feed and
  full-screen pager and can be applied as live wallpapers.
- **Removed the finite wallpaper ceiling**: public Reddit Atom pages now load via
  real `after` cursors, Discover rotates every theme through page one before
  advancing provider pages, ranking no longer deletes the low-scoring tail of
  each batch, and the near-end grid trigger re-arms when inventory grows.
- **Made wallpaper preview image-first**: the vertical swipe pager now keeps only
  Back, position, Set, Favorite, and More over the image. Full metadata and
  secondary actions stay collapsed until the user asks for them, while the pager
  appends newly loaded results without reordering the item currently on screen.
- **Raised the Jackson security floor to 2.18.9**: yt-dlp's legacy transitive
  Jackson 2.11.1 dependency now resolves to the patched 2.18.9 line, with the
  resolved artifacts covered by Gradle SHA-256 dependency verification.
- **Shipped a current yt-dlp payload with guarded updates**: the app-level
  extractor payload is the official 2026.07.04 release, pinned by SHA-256 and
  verified from the packaged resource; stable-channel updates now roll back if
  they fail to reach that security floor.
- **Stabilized video preview surfaces**: feed playback now stops before the
  immersive player opens, and both players crop inside fixed SurfaceView bounds
  instead of resizing decoder output buffers after format discovery.
- **Added an optional YouTube PO-token provider path**: Settings can point yt-dlp
  at a credential-free HTTPS bgutil server. Aura packages the reviewed 1.3.1
  provider plugin, verifies its release hash before activation, and keeps the
  standard extractor path when no provider is configured.
- **Made YouTube extraction failover explicit**: search, preview, and download
  audio now share a cancellation-safe NewPipe-to-yt-dlp policy. Sounds reports
  when the backup extractor is active and shows a specific "YouTube changed
  something" recovery state when neither engine can serve the request.
- **Updated Media3 from 1.5.1 to 1.8.0** across ExoPlayer, HLS, sessions, and
  player UI, retaining Aura's compileSdk 35 floor while taking the newer audio,
  video, scrubbing, and media-session reliability fixes.
- **Bounded Reddit RSS pagination metadata**: page cursors now live in one
  atomically updated 64-entry rolling value instead of permanent per-page DataStore
  keys, and first use removes every legacy `reddit_rss_page_v2_*` key.

## v6.35.1 (2026-07-15)

On-device QA pass on a Galaxy S22 Ultra (Android 16) following the v6.35.0 audit.
Verified live: cold start (245 ms, clean logcat), onboarding, all five tabs,
dark/light themes, sound preview first-play progress, universal search typing and
provider handoff, whole-library export/import round-trip through SAF, wallpaper
apply, and reboots with the weather live wallpaper active. A persistent logcat
crash monitor during the session caught two P1s that only reproduce on-device:
- **Fixed weather wallpaper crash loop on Android 16**: calling
  `setTouchEventsEnabled(true)` from `onSurfaceCreated` recurses on SDK 36,
  the framework re-runs `updateSurface()`, which re-dispatches
  `onSurfaceCreated`, ending in `StackOverflowError` every time the system
  recreates the wallpaper surface (observed as a repeating wallpaper-process
  crash starting minutes after reboot). The call now happens once in
  `Engine.onCreate`, before any surface exists.
- **Fixed BOOT_COMPLETED ANR**: `RingtoneRestorationReceiver` did DataStore and
  ContentResolver reads under the broadcast deadline via `goAsync()`; under
  post-boot CPU pressure the deadline blew and the process ANR'd. The receiver
  now enqueues a WorkManager job (no deadline) and returns immediately.
- **Fixed duplicate apply feedback**: applying a wallpaper showed two stacked
  snackbars (the screen-local "Set as … wallpaper" and the global
  "Applied to …" with Undo). Feedback now goes through the global bus only,
  which survives navigation and carries the Undo action. Same fix on the undo
  path ("Reverted" + "Reverted to previous wallpaper").
- **Fixed "1 items" grammar** in the whole-library export/import feedback:
  proper plurals resources.
- Version-consistency gates synced (fastlane changelogs 134/135, release
  metadata JSON), these had drifted red with the v6.35.0 bump.

## v6.35.0 (2026-07-15)

Deep audit release, ~45 verified fixes across correctness, data safety, UX, i18n, and theming.

### Critical / high
- **Live wallpapers crash-looped on the lock screen after reboot**: the direct-boot
  manifest flag survived a fix that moved all reads back to credential-encrypted
  storage, so FBE devices bound the services before first unlock and every prefs
  read threw. Removed `directBootAware` from all three wallpaper services.
- **Hostile or stale `navigate_to` launch extras could crash the app at startup**:
  the exported launcher activity passed the extra into navigation unvalidated.
  Unknown routes now fall back to the start destination.
- **Discover secondary sources (NASA, Wikipedia, Lemmy, Bing) were silently missing
  most of the time**: the 1.2 s budget discarded completed results on timeout while
  still blocking on the stragglers. Completed sources are now kept and stragglers
  cancelled.

### Correctness & reliability
- Sequential (no-shuffle) wallpaper rotation no longer pins to the first item after
  one full cycle: the no-repeat FIFO now dedupes and resets when exhausted, and
  lock-only rotation applies the deterministic pick instead of a random alternate.
- Day/night rotation schedules now require network if either half of the day needs
  it, instead of baking in the constraint of whichever source matched at
  scheduling time.
- Ringtone/alarm shuffle and time-of-day sound profiles survive reboots and app
  updates: the workers now record what they applied so the boot restoration
  receiver no longer stomps them with a stale manual selection; single profiles
  and partial 24H packs re-apply on their next window instead of running once.
- Disabling ringtone shuffle no longer silently kills alarm shuffle (shared worker
  is cancelled only when both are off).
- 60-second voice recordings are no longer deleted as "too short" when the
  recorder auto-stops at the cap; recording auto-finalizes, and start/stop
  failures surface errors instead of doing nothing.
- Audio previews: the progress bar works on the first play after launch, and
  player errors (expired stream URLs) clear the stuck playing/resolving state.
- Sounds tab: switching to Community mid-load no longer lets the stale load
  overwrite the community feed; exiting search cancels the in-flight search.
- Wallpaper search actions (find similar, random, color) now cancel and replace
  each other and the browse load, no more feed clobbering across tabs.
- Video wallpapers: searching after scrolling no longer 400s Pixabay with a stale
  page number; a cancelled load no longer clears the new load's spinner (showing
  a false "No matches" during search); the applying overlay blocks input and
  concurrent applies are guarded; evicted stream URLs un-mark their items so they
  don't spin forever.
- Rotate-on-unlock/screen-off survives process death: the trigger service re-reads
  its toggles on a sticky restart instead of stopping itself.
- Wallpaper editor: releasing a filter slider at its default no longer lets the
  previous render overwrite the image, Reset clears the processing overlay, and a
  single overlay drag no longer flushes the whole 20-step undo history (gesture
  coalescing, with undo-boundary reset).
- Weather wallpaper: dim reveal re-dims under reduced motion, and the legacy
  coordinate fallback for adaptive tint actually takes effect after upgrade.
- SourceMetrics: a persisted sub-threshold failure streak is cleared on success
  (no more false DEGRADED after restart), and provider timeouts now count as
  failures so degraded-source cooldowns actually engage; NASA/Wikipedia policy
  aliases resolve in diagnostics.
- Lemmy: responses are cached per the declared 15-min TTL, pagination uses the
  pre-filter page size, and Wikipedia POTD uses the UTC date its feed is keyed by.
- Provider credential store: keystore key generation is process-locked (no more
  orphaned ciphertext from concurrent first use), and a transient keystore
  failure no longer flags credential storage broken for the whole session.

### Data safety
- Failed scheduled backups no longer leave empty files that evict good backups
  from retention; theme-pack import failures no longer orphan up to 128 MB of
  extracted assets (import dirs are also pruned when superseded), exports clean
  their temp copies and delete truncated output, and colliding asset names inside
  a pack no longer overwrite each other.
- Whole-library backup (shipped unreachable in v6.34.x) is now wired into
  Settings > Backup with validated, idempotent import (https-only URLs,
  enum-checked sources, name-merged collections).
- Gallery video export no longer leaves an orphaned 0-byte MediaStore row on
  failure (pending-flag flow with cleanup).
- Diagnostics bundles redact live-wallpaper media paths like the crash log.

### UX / accessibility / i18n
- Universal search: fast typing no longer drops keystrokes, rotation keeps the
  query, URL fields no longer match every item (searching "http" returned
  everything), favorites aren't listed twice, favorite-origin local files open
  the item instead of a Downloads dead end, the history dropdown tracks the real
  field height at large font scale, and the offline state shows the disabled
  provider reasons instead of a generic no-results card.
- Preview-screen wallpaper applies report failures via snackbar (previously
  silent); apply/undo feedback strings are localized resources.
- Settings: permission-scope badges no longer break in non-English locales
  (enum-based), the hardcoded green "granted" tint uses a theme token, ~120
  hardcoded English strings across diagnostics/dialogs/subtitles moved to
  resources (with proper plurals, also fixing "90 min → 1 hours"), shader/VFX/
  interval pickers scroll on short screens, theme-pack rows disable while a
  transfer runs, and progress bars use on-scale 4 dp radii.
- Sound detail: a failed similar-sounds load shows an honest error with Retry
  instead of "No close matches".
- Widget: last-shuffle time honors the device 12/24-hour setting.
- Embedded photo picker: reflection failures in the async session callback fall
  back to the standard picker instead of crashing; bottom sheet uses an on-scale
  12 dp radius; OEM battery-guidance deep links now resolve on Android 11+
  (package visibility declarations).

## v6.34.6 (2026-07-01)
- **Unified local Library hub**: added a first-class Library tab that groups
  Favorites, Downloads, Collections, Local imports, Recent activity, and
  backup/restore entry points while preserving existing deep links and
  local-first copy.
- **Functions dependency hardening**: pinned safe transitive versions for
  `form-data`, `protobufjs`, and `uuid` so the community Functions package
  audits cleanly without forcing an incompatible Firebase Admin major upgrade.
- **Provider credential storage**: migrated user-entered provider keys from
  DataStore to Android Keystore-backed AES-GCM storage, with legacy migration,
  backup exclusions, and a Settings warning when Keystore access is unavailable.
- **Source health diagnostics**: expanded Settings diagnostics with per-provider
  last success/failure/disabled timestamps, fallback status, retry guidance, and
  sanitized support-bundle source health rows.
- **Background recovery diagnostics**: added manufacturer-aware OEM battery
  recovery guidance for Data Saver, metered-network waits, missing WorkInfo, and
  long-enqueued WorkManager jobs in Settings and support bundles.
- **Release documentation**: removed workflow-era build/release references from
  active setup docs and clarified the local-only verification and signed
  artifact path.
- **Adaptive large-screen shell**: added width-aware primary navigation that
  keeps bottom navigation on compact screens and switches to a permanent rail on
  expanded screens, with stable expanded Wallpapers/Sounds fixtures.
- **Universal on-device search**: added a Library search entry that returns
  segmented saved/local wallpapers, videos, sounds, collections, downloads,
  favorites, and local files with offline provider handoff states and local
  search history controls.
- **FOSS flavor boundary**: added full/FOSS distribution flavors, isolated
  Firebase, Google Services, Play Services ML Kit, and App Check to the full
  flavor, compiled FOSS against local no-op adapters, and made the F-Droid
  preflight pass for the FOSS variant.
- **Consistent browse rails**: added shared Popular, Newest, Categories, and
  Collections/Local rails across Wallpapers, Video Wallpapers, and Sounds while
  preserving source-specific refine controls and local fallback actions.
- **Depth portrait composer**: added a wallpaper-editor depth panel that
  segments subjects into blurred/tinted/AMOLED portrait backgrounds with frame
  presets, MediaStore export, apply actions, and Aura Parallax handoff.
- **Wallpaper text and sticker layers**: added local text/sticker overlays in
  the wallpaper editor with drag/move, scale, rotate, color, undo, MediaStore
  export, and apply/parallax rendering through the existing wallpaper pipeline.
- **Local theme packs**: added Settings export/import for portable Aura theme
  packs that bundle JSON recipes for wallpapers, video wallpaper, sounds,
  widget tint metadata, and launcher shortcuts with bounded local assets where
  Android grants file access.
- **Play-ready local AAB dry run**: made release artifact validation require
  the signed Play AAB, bundle manifest evidence, bundletool validation, upload
  key fingerprint, checksums, and Play App Signing owner-step receipt alongside
  the GitHub/Obtainium APK.
- **HEIF/AVIF wallpaper ingestion**: added a single image-format policy for
  auto-rotation, local apply, editor, and community upload flows. HEIF is
  accepted on Android 8+, AVIF is accepted where Android 14+ decoding is
  available, and community uploads transcode to fresh JPEG bytes with
  metadata/location stripping before Firebase upload.
- **Wallpaper ViewModel split**: moved browse/loading, shared wallpaper state,
  and identity helpers out of the root Hilt ViewModel, keeping wallpaper
  ViewModel files under the 500-line feature-boundary gate.
- **Sounds ViewModel split**: moved feed loading, YouTube search/import,
  playback/progress, community upload/moderation, and sound selection state into
  feature-owned modules. Sound playback now continues when switching tabs.
- **Embedded Photo Picker imports**: added an API-gated embedded image picker
  for wallpaper uploads and collection QR imports, with portrait-grid
  customization and the existing scoped Photo Picker as fallback.
- **Pseudolocale and RTL gate**: enabled debug pseudolocales and added compact
  English XA plus Arabic XB RTL route fixture coverage before real translation
  packs are introduced.
- **Provider backoff policy**: normalized provider timeout, backoff, cache
  fallback, and disabled-source diagnostics across network sources, with
  coverage for Retry-After, timeout, DNS, and disabled-provider cases.
- **Curated AGSL shader gallery**: added six built-in live-wallpaper shader
  backgrounds, a Settings picker with no user-authored shader input, API 33+
  RuntimeShader rendering, and static Canvas fallback for older Android releases.
- **On-device wallpaper style learning**: Discover ranking now adapts after
  local apply, favorite, and hide signals, stores the learned taste profile only
  in app-private preferences, and exposes a Settings reset action.

## v6.34.5 (2026-07-01)
- **Route-level accessibility release gate**: replaced primitive-only Compose
  accessibility checks with debug route fixtures for Wallpapers, Sounds detail,
  Settings diagnostics, Videos, and Wallpaper Editor states. The JSON policy and
  validator now require every automated fixture to match the executed surfaces.

## v6.34.4 (2026-06-28)
- **Compose localization extraction**: moved the remaining scanned Compose UI
  strings for onboarding, downloads, collections, detail, preview, diagnostics,
  licenses, community, editor, and video wallpaper flows into resources, reduced
  the hardcoded-string baseline to zero entries, and taught the checker to ignore
  preview fixtures and Compose animation tooling labels.
- **Local release policy gates**: converted release validation to the
  local-only distribution model, removed workflow requirements from release
  policy checks, refreshed release dry-run/supply-chain/SBOM metadata, and
  kept signed APK/AAB, checksum, notice, native-compliance, and GitHub Release
  upload evidence documented together.

## v6.34.3 (2026-06-27)
- **Settings decomposition**: split the 2,628-line Settings screen into
  feature-owned Compose section files for wallpaper rotation, scheduler,
  backup, smart/live wallpaper, sounds, video, services/community, storage,
  diagnostics, permissions, and about. Added a package-level state holder and
  focused contracts that keep the root Settings screen below 500 lines.

## v6.34.2 (2026-06-27)
- **Roadmap hygiene**: replaced stale shipped implementation history in
  `ROADMAP.md` with the current actionable backlog, normalized blocked work into
  `Roadmap_Blocked.md`, and removed the duplicate `Roadmap_Blocks.md` variant.

## v6.33.0 (2026-06-19)
- **Android 17 audio hardening compliance**: video wallpaper MediaPlayer now
  sets non-media AudioAttributes and deselects audio tracks after prepare so
  muted video playback does not create an AudioTrack that Android 17's
  background audio enforcement could silently reject.
- **OEM battery optimization guidance**: when any wallpaper rotation trigger
  is active, Settings shows a manufacturer-detected guidance item (Samsung,
  Xiaomi, OnePlus, Huawei, vivo, ASUS) that deep-links to the relevant OEM
  battery settings page. Addresses the #1 reliability complaint across all
  FOSS wallpaper changers.
- **Ringtone shuffle from downloads**: new Settings toggle under Sounds
  periodically sets a random downloaded sound as the system ringtone via
  WorkManager. Configurable interval (hourly to every 3 days), avoids
  repeating the last-applied sound. No new permissions required, uses
  already-downloaded sounds in MediaStore.
- **SoundsViewModel decomposition**: community operations (voting, recording,
  uploading, reporting, blocking, deletion) extracted to focused
  `SoundCommunityActions` class. SoundsViewModel delegates to it via thin
  one-liner methods. Constructor unchanged, all existing tests pass
  without modification.
- **Compose @Preview fixtures**: added preview composables for AuraStateCard
  (empty/error/light variants), SettingsSection (dark/light), SettingsToggle,
  SettingsItem, SettingsMetric, and OEM battery guidance. Previews render in
  Android Studio without Hilt injection for faster UI iteration.
- **AV1 codec support detection**: new `Av1CodecSupport` singleton queries
  `MediaCodecList` for hardware AV1 decode capability at startup. Exposes
  `hasHardwareAv1Decode` and `preferredVideoMimeTypes()` for codec-aware
  video format selection. Risk register updated to reflect current AV1
  install base (Android 13+ mandates hardware decode).
- **NASA APOD wallpaper source**: Astronomy Picture of the Day appears in the
  Discover feed as a daily hero card on page 1 and random historical images on
  subsequent pages. Zero-auth NASA API, image-only filtering, cached per
  session, source badge shows NASA attribution and photographer credit.
- **Test fixture repair**: contract tests updated to follow Settings UI
  primitives (SettingsSection, SettingsToggle, SettingsRadioOptionRow,
  SettingsMetric) to their new home in SettingsComponents.kt after the
  recent extraction refactor.

## v6.32.9 (2026-06-19)
- **Backup reliability hardening**: scheduled favorites backup now clamps
  persisted retention values at runtime before pruning, preventing corrupt
  preferences from crashing cleanup or deleting more backups than intended.
- **Settings recovery polish**: changing local/backup folders releases stale
  SAF grants, and permission recovery dialogs now show calm feedback if Android
  settings cannot be opened on an OEM build.

## v6.32.8 (2026-06-19)
- **Settings automation polish**: surfaced scheduled favorites backup with
  writable-folder validation, interval and retention controls, and clear paused
  states when SAF permission needs repair.
- **Rotation legibility control**: added a Settings dimming slider for
  auto-rotated and trigger-rotated wallpapers so bright images can be made more
  readable behind the clock and status bar.

## v6.32.7 (2026-06-18)
- **Collection import safety**: QR-code collection imports now cap input bytes,
  validate decoded image dimensions before bitmap allocation, and always recycle
  decoded bitmaps after ZXing scans.

## v6.32.6 (2026-06-18)
- **External link safety**: Settings, licenses, video policy links, and
  wallpaper source links now use a shared launcher that rejects local/script
  schemes and shows consistent feedback when Android has no handler.

## v6.32.5 (2026-06-18)
- **yt-dlp rollback hardening**: guarded recursive cleanup to the managed
  yt-dlp runtime directory and made rollback restore failures preserve the
  currently installed runtime instead of risking a half-copied recovery state.

## v6.32.4 (2026-06-18)
- **Community reliability**: bounded Firebase RTDB reads with an 8-second
  timeout across community votes, uploads, creator profiles, moderation reports,
  blocked-user lists, and shared collection imports so flaky networks fail into
  existing error/empty states instead of leaving screens loading indefinitely.
- **Creator dashboard performance**: replaced per-upload vote-count reads with a
  single timed vote-index read, eliminating the cold-load fan-out that could
  trigger dozens of individual Firebase requests.
- **Wallpaper editor trust**: when memory pressure forces an edited preview to
  render below the source resolution, the editor now shows a persistent
  reduced-resolution warning instead of silently applying a lower-detail output.

## v6.32.3 (2026-06-17)
- **Compose screenshot coverage**: added Roborazzi JVM screenshot tests with
  deterministic route-state fixtures for wallpapers, sound detail, settings,
  video wallpapers, and the wallpaper editor. CI now verifies the golden
  captures on every push.

## v6.32.2 (2026-06-17)
- **YouTube video metadata reliability**: video wallpaper search now probes
  yt-dlp stream metadata for dimensions, rotation, duration, MIME type, and
  codec instead of using thumbnail dimensions as orientation data. Unknown
  metadata is labeled as unknown instead of being inferred from thumbnails.

## v6.32.1 (2026-06-17)
- **Reddit provider restored**: `isProviderEnabled()` always returned false
  (pref value read and discarded), Reddit wallpaper feeds were completely dead.
- **Bitmap leak fixes**: QR decode in CollectionExporter now recycles bitmap
  after pixel extraction; applyDarken recycles result on canvas draw failure;
  ParallaxWallpaperService handles Bitmap.copy null on OOM.
- **CancellationException rethrow** in SmartCropDetector, SoundUrlResolver,
  ContactRingtoneService, and AudioTrimmer (trim/normalize/convert).
- **Thread safety**: UploadRepository community uploads uses synchronizedSet
  and AtomicReference for cross-thread snapshot access.
- **Performance**: fetchWallpapersByKeys parallelized with async/awaitAll
  and 8s per-key timeout; recordDisabledProvider no longer fires real API
  requests for disabled providers.
- **Security**: AudioPreviewCache disables cross-protocol redirects
  (HTTPS-to-HTTP downgrade prevention).
- **Reliability**: SoundEditorViewModel onPrepared callback guards against
  released MediaPlayer race with stopPlayback.

## v6.32.0 (2026-06-17)
- **Community Favorites hydration**: Top-voted community wallpapers that exist
  only in Firebase RTDB now resolve in the Community Favorites row instead of
  silently dropping. fetchTopVoted detects cw_ IDs missing from Room cache and
  fetches metadata from /community_wallpapers.
- **Scheduled automatic backup**: New AutoBackupWorker exports favorites to a
  user-chosen SAF folder on a configurable interval (daily/weekly). Old backups
  are pruned to keep the last N copies. Preferences for backup enable, folder
  URI, interval, and keep count.
- **Rotation-time darken effect**: Optional darken (0-100%, off by default)
  applied to auto-rotated and triggered wallpapers via a brightness ColorMatrix
  in WallpaperApplier. Improves status-bar and clock legibility on bright
  rotated wallpapers.

## v6.31.4 (2026-06-16)
- **Settings feedback and picker polish**: Settings now uses Aura's shared
  snackbar feedback instead of raw Android toasts, and repeated radio pickers
  now share one full-row 48dp selectable pattern for clearer touch,
  keyboard, and screen-reader behavior.
- **Favorites recovery empty states**: empty wallpaper and sound favorites now
  expose a direct Import backup action with clearer restore-focused copy,
  reducing first-run and device-migration dead ends.
- **Release-polish guardrails**: expanded contract coverage for Settings
  snackbar feedback, shared picker rows, and Favorites restore actions.

## v6.31.3 (2026-06-16)
- **Premium UX polish pass**: unified browse headers for sounds and video
  wallpapers, added clearer tab/source-specific copy, made bottom navigation
  selection visible beyond color alone, and standardized Aura snackbar feedback
  across detail, editor, collection, generated-wallpaper, sound, and wallpaper
  flows.
- **Accessibility and state feedback**: shared state/status components now mark
  headings and polite live regions so loading, degraded-source, empty, and error
  states read more predictably.

## v6.31.2 (2026-06-15)
- **Community upload deletion request copy**: sound and wallpaper upload
  disclosures now tell users to copy/share the Settings deletion request code
  or use the privacy-policy hosted web request path if Aura is unavailable.
- **Community deletion orchestrator apply gate**: the trusted deletion
  orchestrator can now run an explicit RTDB apply mode with request-code,
  plan-hash, OAuth token, and timeout gates, then emit a redacted completion
  receipt while preserving dry-run as the default.
- **Video wallpaper provenance links**: apply confirmation now shows source,
  license, creator, provider terms, report, and rights/takedown links for
  remote video wallpapers, with policy-backed action gating before apply.
- **Community deletion orchestrator dry-run**: added a trusted operator bundle
  builder that composes request-code lookup, RTDB deletion planning, review,
  local simulation, private executor packaging, REST dry-run receipts, pre-Auth
  upload Storage-handle inventory, retry guidance, and requester-safe receipt
  output without contacting Firebase in dry-run mode.
- **Target-37 toolchain gate**: added a release preflight that keeps the current
  SDK-35 lane green but blocks any partial API 37 compile/target bump unless
  AGP, Gradle, and the installed Android SDK platform/build-tools floor are
  ready for Android 17.
- **Community upload public-data disclosure**: upload dialogs now disclose owner
  deletion and deletion-request paths before publishing, and sound/wallpaper
  upload callables no longer store original local filenames in public catalog
  metadata.
- **Provider-policy reports**: the existing "Provider policy violation" report
  reason is now accepted by the community report Cloud Function and RTDB report
  validation, so wallpaper/sound report dialogs can submit provider-specific
  policy concerns instead of falling back to generic safety or rights reasons.
- **Community write hardening**: community report submission, voting, creator
  follows/profile edits, user blocks, and upload metadata finalization now rely
  on the App Check/Auth-backed Cloud Function callables instead of falling back
  to client-side Realtime Database writes. RTDB rules now keep those mutation
  paths callable/admin-owned while preserving public reads and owner delete
  authority for existing upload records.
- **Deep audit pass**: Fixed callable fallback bypass in both upload repositories
  (sound + wallpaper), if the Cloud Function returned an unexpected status or
  threw a non-`CommunityCallableException` error, the already-uploaded Storage
  file was deleted without falling through to the direct-database metadata write.
  Removed wasted API calls in `AutoWallpaperWorker` when rotation source
  providers are disabled (both scheduler and legacy paths were calling the
  provider's fetch function before returning success). Fixed sound editor fade
  slider range: `coerceAtLeast(100f)` let the slider extend past the clip's
  actual half-duration, mismatching the ViewModel's clamping and causing the
  thumb to jump on release.
- **Compact-screen disclosure and collection polish**: community guidelines and
  generated-wallpaper disclosure dialogs now keep long policy copy scrollable
  with bounded dialog corners. Collection import, QR share, and wallpaper-detail
  collection picker flows now stay scrollable and keyboard-aware on short
  screens. Wallpaper detail horizontal action chips now keep row-level
  accessibility labels even when partially clipped in the scroller.
- **Additional release-readiness polish**: Settings API-key and YouTube query
  dialogs now remain scrollable and keyboard-aware on compact screens, with
  password keyboard hints for provider keys. Wallpaper, video wallpaper, AI
  style, and sound browse/filter controls now use explicit 48dp touch-target
  rows where compact controls previously appeared and align on the app's
  bounded 8dp control shape.
- **Stricter release UI polish**: creator profile editing now stays scrollable
  and keyboard-aware on compact screens, AI wallpaper prompts avoid IME
  overlap, contact assignment keeps the selected-contact flow scrollable, and
  Settings picker rows expose full 48dp radio targets. Preview, feed, detail,
  sound, and video chrome now use bounded corner radii instead of circular
  backdrops for more consistent visual language.
- **Release polish pass**: community report, sound upload, and wallpaper upload
  dialogs now keep form content scrollable and IME-aware on compact screens.
  Sound upload category/license chips wrap instead of overflowing at large text
  sizes, and sound detail bottom spacing now uses navigation-bar insets instead
  of fixed padding.
- **Device-verified wallpaper header polish**: fixed `CompactSearchField`
  vertical expansion on real devices so the wallpaper header keeps a compact
  search row instead of consuming the first viewport before wallpaper cards.
  Settings overview text now renders active personalization as a complete
  sentence instead of a one-phrase fragment. Settings toggles now expose one
  labeled row-level accessibility target instead of a separate unlabeled switch.
- **Planning-doc manifest gate**: expanded `tools/manifest_consistency_check.py`
  beyond ROADMAP/RESEARCH so current-state README sections and local context
  docs are checked against Gradle and Functions manifests. `verify.yml` now
  runs the gate before Gradle work, and new fixture tests reject stale README,
  and community callable runtime claims.
- **Sound detail large-text polish**: the ringtone/notification/alarm and
  secondary sound actions now switch to stacked/wrapped controls at larger font
  scales, use minimum heights instead of fixed heights, and allow two-line
  centered labels so 200% text does not clip the primary apply actions.
- **Provider health persistence and auto-fallback**: `SourceMetrics` now
  persists failure state to SharedPreferences with a 24-hour cooldown so
  degradation survives process death. `isDegraded()` / `degradedSources()`
  expose persistent health. Daily wallpaper worker skips degraded sources
  when alternatives exist (Bing/Wallhaven fallback chain). Wallpapers,
  Sounds, and Video screens show a subtle "Limited source health" banner
  when degraded sources are active. Diagnostics bundle includes degraded
  source list for support triage.
- **Live wallpaper engine recovery receipts**: new `LiveWallpaperReceiptStore`
  tracks surface lifecycle, visibility changes, draw heartbeats, errors,
  and recovery actions for all three live wallpaper engines (video, weather,
  parallax). SharedPreferences-backed with UTC timestamps. Diagnostics
  bundle now includes a "Live wallpaper engine receipts" section surfacing
  stale/frozen engine state, surface recreation counts, last error, and
  last recovery action for support triage. Draw receipts are throttled to
  one write per 30 seconds to avoid I/O pressure.
- **Permission and source transparency panel**: new Settings section
  between Diagnostics and About lists every manifest permission with
  its scope (Local/Remote), data behavior description, and live
  Granted/Not granted status for runtime permissions (notifications,
  location, contacts, microphone). Covers wallpaper, internet,
  notifications, location, contacts, microphone, modify settings, and
  foreground service.
- **Navigation 3 decision**: evaluated Nav 2.9 vs Nav 3 for the N-1
  toolchain pass. Decision: skip Nav 2.9, target Navigation 3 directly.
  Codebase surface: 22 destinations, 51 NavType declarations, 31
  navigate calls. Nav 3 eliminates string-route boilerplate, enables
  true predictive-back via NavDisplay, and avoids a two-step migration.
  N-1 scope, NX-4, NX-13, and all "Navigation 2.9" references updated.
- **Manifest consistency check**: new `tools/manifest_consistency_check.py`
  compares dependency/runtime version claims in ROADMAP.md and RESEARCH.md
  current-state sections against `gradle/libs.versions.toml`,
  `app/build.gradle.kts`, and `functions/package.json`. Reports stale
  claims and duplicate active roadmap titles. Aspirational/target versions
  in upgrade roadmap items are skipped.
- **Compose accessibility semantics pass**: Added missing
  `contentDescription` to interactive icons that lacked TalkBack
  element names: VideoWallpapersScreen hide/preview icon buttons,
  WallpaperDetailScreen palette color search dots, SoundDetailScreen
  favorite toggle icon. Verified existing `stateDescription`,
  `customActions`, `progressBarRangeInfo`, and decorative `null`
  contentDescription patterns are correct across all screens.
- **Reduced motion accessibility**: when the system `ANIMATOR_DURATION_SCALE`
  is 0 or the new manual "Reduce animations" toggle in Settings is enabled,
  all live wallpaper particle effects (weather, VFX, touch ripples) are
  disabled and the 30 FPS draw loop stops (static background only). Compose
  transitions already respect the system animation scale natively.
- **Opus audio output format**: AudioTrimmer now supports Opus codec
  (`libopus` at 48kbps in OGG container) for convert, trim-with-fade, and
  standalone fade operations. Sound editor adds a format picker chip row
  (MP3, OGG, Opus, WAV, FLAC, M4A) between fade controls and apply buttons.
  Converting reloads the waveform and resets trim/fade state for the new file.
- **"Set With..." intent-filter receiver**: other apps (gallery, file managers,
  browsers) can now delegate wallpaper-setting to Aura via `ACTION_ATTACH_DATA`
  with `image/*` MIME type. Aura opens the crop/edit/apply flow with the
  received image. Supports `content://` and `file://` URIs via
  `ContentResolver.openInputStream()`, in addition to existing HTTP(S) loading.
  Back navigation returns to the calling app.
- **Wallpaper action capabilities**: added `WallpaperLicensePolicy.kt` with
  per-wallpaper action-capability gates (APPLY/DOWNLOAD/SHARE/EDIT) mirroring
  the existing sound license system. Source-specific rules for Bing (download/
  edit confirmation, share disabled), Reddit (edit disabled), Community
  (confirmation for user-upload license), AI-generated (share confirmation),
  and Creative Commons license gates (NC confirmation, ND edit disabled).
  Unavailable-source wallpapers disable all actions.
- **Video wallpaper provenance and license policy**: `VideoWallpaperItem`
  gains `contentSource`, `license`, and `sourcePageUrl` fields. New
  `VideoWallpaperLicensePolicy.kt` gates APPLY/DOWNLOAD/SHARE per
  provider: YouTube disables share and requires apply/download
  confirmation, Reddit requires apply/download confirmation, Pexels and
  Pixabay require download confirmation. Missing source link or uploader
  disables share for remote sources. 10 focused tests.
- **Foreground-service declaration packet**: checked JSON manifest
  documents both foreground services (AudioPlaybackService/mediaPlayback,
  RotationTriggerService/specialUse), all three notification channels,
  Play declaration justifications, and demo-video steps. CI-gated.
- **Managed storage ledger**: checked JSON ledger documents all 13
  storage locations (cacheDir, filesDir, databases, DataStore,
  MediaStore) with path, budget, retention, backup exclusion status,
  user cleanup action, and uninstall behavior. CI-gated.
- **Export format golden fixtures**: seven compatibility tests cover v1
  favorites export schema forward/backward compatibility, including
  future-version unknown-field resilience and legacy v0 plain-list
  format parsing.
- **Aura Originals upstream attribution**: manifest schema gains
  `creator`, `curationDate`, and `reviewResult` fields for upstream
  Freesound attribution. CI provenance gate validates CC0 license,
  HTTPS URLs, creator attribution, and review status on all entries.
- **Baseline profile generated**: 34,521 profiled methods covering
  Wallpapers, Videos, Sounds, Favorites, and Wallpaper Detail critical
  journeys. Generated on Samsung S22 Ultra (SM-S908U1, Android 16, API
  36). Startup profile included. Compiled into release APKs to
  pre-optimize hot methods via ART for faster cold start and reduced
  first-scroll jank.
- **Test suite restored**: fixed 11 pre-existing test compilation and
  runtime failures from the recent audit pass (SourceMetrics context,
  reduceAnimations pref, external automation title, applicationContext
  mock). 554/554 tests green.
- **yt-dlp CVE batch remediation**: expanded `ytdlp-cve-policy.json` from
  schema v1 (CVE-2026-26331 only) to v2 tracking all 5 yt-dlp CVEs
  (CVE-2026-26331, CVE-2026-50019, CVE-2026-50023, CVE-2026-50574,
  CVE-2025-54072). Forbidden options list now covers `--netrc-cmd`,
  `--cookies`, `aria2c`, `--downloader`, and `--exec`. Policy checker
  validates all 6 forbidden options; 9 unit tests cover v1 compat and v2
  rejection of each CVE-related flag. Risk Register consolidated to one row.
- **contentDescription audit (false positive)**: audited all 106
  `contentDescription = null` instances across 21 Compose UI files. Every
  instance is correctly null: icons inside labeled Buttons (text provides the
  label), icons inside IconButtons with `.semantics { onClick(label = ...) }`
  blocks, decorative status icons adjacent to text, thumbnail images, or
  leading/trailing chip/menu icons. No changes needed, the codebase already
  follows proper Compose accessibility patterns via semantics blocks.
- **Centralized notification channels**: extracted all notification channel
  definitions into `NotificationChannels.kt` singleton. `FreeVibeApp.onCreate()`
  creates all three channels (media_playback, daily_wallpaper,
  aura_rotation_triggers) at startup. Removed duplicate channel creation from
  `DailyWallpaperWorker` and `RotationTriggerService`; both now reference the
  centralized channel ID constants.
- **Provider policy report reason**: added `PROVIDER_POLICY` to
  `CommunityReportReason` for reporting community uploads that violate
  third-party provider terms (e.g., re-uploaded Pexels/Pixabay content).
- **Locale.ROOT sweep**: fixed 7 remaining machine-use case transformations
  across AI prompt normalization, wallpaper feed term matching, content source
  label formatting, rate-limit host matching, file extension checks, and daily
  notification source names. Prevents Turkish-locale garbling.
- **Localization extraction (editors + screens)**: moved all hardcoded strings
  from AiWallpaperScreen, WallpaperEditorScreen, WallpaperCropScreen,
  SoundEditorScreen, CollectionsScreen, CommunityReportsScreen,
  WallpaperDetailScreen, WallpapersScreen, VideoWallpapersScreen,
  CategoriesScreen, CreatorProfileScreen, LicensesScreen,
  WallpaperPreviewScreen, and VideoWallpaperPreviewScreen into strings.xml.
  Adds ~130 new string resources with shared common strings for repeated
  labels (Cancel, Back, Home, Lock, Both, Done, Save, Discard, etc.).
- **Premium UI feedback polish**: added a shared inline status banner for
  recoverable provider failures, surfaced last-good-result recovery on
  wallpapers/sounds/video, and tightened Settings/Favorites spacing, state
  labels, borders, and progress feedback.
- **Native compliance drift gate**: native/copyleft inventory checks now derive
  youtubedl-android and NewPipe coordinates from Gradle so dependency bumps
  cannot leave the reviewed payload lock checking stale versions.
- **YouTube extractor self-update**: added a manual Settings action for stable
  yt-dlp runtime updates, rollback until the next successful extraction, and
  diagnostics coverage for active extractor version/status.
- **Localization extraction batch**: moved navigation titles, community
  guidelines/report dialogs, and recent-search actions into Android string
  resources, then refreshed the hardcoded-string baseline.
- **Backup/restore path reconciliation**: startup now clears stale local paths
  for restored favorites/download history when app-private files or MediaStore
  rows are missing, while keeping remote item provenance visible.
- **Unified import validation**: favorites and collection imports now share
  HTTPS URL caps, text caps, source enum checks, duplicate identity handling,
  and oversized collection-token rejection.
- **Transactional collection import**: collection JSON imports now create the
  collection and all normalized items inside one Room transaction, with
  duplicate source-scoped wallpapers collapsed before insert.
- **Custom accessibility semantics**: added source-backed coverage for reusable
  Compose components, Settings rows/toggles, media cards, badges, waveform
  controls, and action labels.
- **External automation hardening**: gated Tasker/MacroDroid rotation broadcasts
  behind an explicit Settings opt-in, added a 30-second burst limit, surfaced
  last-trigger diagnostics, and documented the public intent contract.
- **Localization debt gate**: added a Compose hardcoded-string scanner, JSON
  baseline, migration plan, CI check, and focused tool tests so new
  user-visible Kotlin string literals cannot land unnoticed.
- **Community upload disclosure**: clarified that public community uploads store
  the listing category, public download URL, and sanitized file name alongside
  license, source, uploader, and tags.
- **Cloud Functions runtime migration**: moved the community Functions package,
  lockfile, and Firebase CI lane from Node 20 to Node 22, refreshed the backend
  manifest, and added a high/critical production audit gate for Functions
  dependencies.
- **Supply-chain CVE hardening**: constrained youtubedl-android's vulnerable
  `jackson-databind` and `commons-io` transitives to reviewed patched
  versions, then bumped the Gradle wrapper to 8.12.1 with the reviewed
  distribution checksum and policy-test coverage.
- **Background work device evidence gate (Cycle 160)**: added a checked
  device/emulator capture packet for WorkManager baseline, metered/Data Saver,
  low battery, Doze/App Standby, and rotation-trigger coalescing evidence,
  including adb commands, artifact paths, workflow wiring, and tests.
- **Background work action hints (Cycle 159)**: added Settings and support
  bundle action hints for Data Saver restrictions, metered waits, source
  failures, network/provider errors, Aura Originals validation retries,
  permission cues, apply failures, and WorkManager retry/failure states.
- **Background work support bundle live receipts (Cycle 158)**: merged live
  WorkManager, Data Saver, and persisted worker receipt details into the copied
  crash/support diagnostics bundle.
- **Background work persisted receipts (Cycle 157)**: added local worker
  last-run receipt storage for background work success, retry, failure, error
  class, and deferral reason, then surfaced those receipts in Settings
  diagnostics.
- **Background work Settings diagnostics (Cycle 156)**: added a local
  `Settings` > `Diagnostics` > `Background work` dialog backed by WorkManager
  unique-work `WorkInfo` state counts and `ConnectivityManager` metered/Data
  Saver receipts.
- **Background work diagnostics bundle section (Cycle 155)**: added a local
  crash/support bundle background-work section covering current unique work
  names, inferred enabled state, network posture, constraints, and explicit
  pending markers for live WorkInfo and Data Saver receipts.
- **Background work network posture gate (Cycle 154)**: added a checked worker network posture packet for connected versus unmetered WorkManager constraints, Data Saver diagnostic gaps, metered-network behavior, privacy surfaces, release risk, workflow wiring, and source-backed tests.
- **Background work scheduling ledger (Cycle 153)**: added a checked WorkManager scheduling packet for periodic auto wallpaper, daily wallpaper, weather refresh, Aura Originals download, and rotation trigger one-shots, including unique work names, enqueue policies, constraints, deferral reasons, workflow wiring, and source-backed tests.
- **Rotation trigger foreground-service policy gate (Cycle 152)**: added a checked `specialUse` foreground-service policy packet for `RotationTriggerService`, Play Console declaration text, owner demo-video evidence, workflow wiring, and source safeguards.
- **Rotation trigger boot permission gate (Cycle 151)**: removed the unused `RECEIVE_BOOT_COMPLETED` permission, documented that rotation triggers resume after opening Aura, and added a verify/release gate that blocks boot permission or boot receiver drift without updated release disclosures.
- **Store asset pipeline gate (Cycle 150)**: added a checked screenshot and feature-graphic capture plan covering Fastlane image paths, four planned phone screenshots, alt text, Play/F-Droid source rules, future asset-mode enforcement, and verify/release workflow wiring.
- **SBOM readiness gate (Cycle 149)**: added a checked SBOM readiness packet that keeps generation deferred until the N-1 toolchain upgrade while enforcing the current release evidence floor, future CycloneDX/SPDX artifact names, scope, sources, and workflow wiring.
- **Release metadata consistency gate (Cycle 148)**: added a checked release metadata packet that reconciles app package/version values, Fastlane text, README links, privacy URLs, Play/alternative-store packets, release preflight commands, and expected GitHub release artifacts.
- **Alternative-store disclosure gate (Cycle 147)**: added a checked GitHub/Obtainium/Izzy/F-Droid disclosure packet covering channel status, anti-feature notes, manifest permissions, reviewed network services, proprietary dependency markers, and Izzy submission notes.
- **Community Guidelines consent gate (Cycle 146)**: added versioned community guidelines acceptance before community feeds, uploads, votes, reports, blocks, follows, profiles, and startup identity warm-up, then wired a checked consent policy gate into verify and release.
- **Play App content packet gate (Cycle 145)**: added a checked owner-ready Play App content declaration packet covering ads, app access, target audience, content rating notes, Data safety, UGC, generated content, sensitive permissions, evidence paths, and owner actions.
- **SDK Data safety surface gate (Cycle 144)**: extended the Data safety matrix check with Gradle-marker-backed SDK rows for Firebase Auth, RTDB, Storage, Functions, App Check, and Play services ML Kit/module install surfaces.
- **Local storage Data safety gate (Cycle 143)**: extended the Data safety matrix check with source-backed local storage rows for DataStore, Room, SharedPreferences, diagnostics logs, app-private media, and cache surfaces, including backup/transfer posture.
- **Network Data safety surface gate (Cycle 142)**: extended the Data safety matrix check to reconcile every reviewed network endpoint with privacy rows for data types, sharing, retention, deletion, and user controls.
- **Privacy Data safety matrix (Cycle 141)**: added a manifest-permission privacy ledger and verify/release gate that blocks permission drift without reviewed data type, purpose, retention, deletion, denial, and Play declaration rows.
- **Privacy policy link gate (Cycle 140)**: added an in-app Settings privacy-policy link plus verify/release checks that keep the public policy URL aligned across Settings, README, Fastlane metadata, and release docs.
- **On-device wallpaper decision gate (Cycle 139)**: added an evidence packet and verify-time guard that keeps local wallpaper generation on hold until device, delivery, battery/thermal, license, moderation, fallback, and FOSS-channel criteria are met.
- **Store metadata preflight (Cycle 138)**: added a checked Fastlane text/version/privacy preflight, wired it into verify and release, shortened the Play short description, and added the public privacy-policy URL to full description metadata.
- **Prompt metadata retention cleanup (Cycle 137)**: stopped saving generated wallpaper prompt text in favorite names/tags and added generated PNG cleanup after generated favorite removal.
- **Generated request cost guardrails (Cycle 136)**: added generated wallpaper session request counting, in-flight request rejection, duplicate prompt/style confirmation, and Stability account/cooldown error copy.
- **Stability key policy sentinel (Cycle 135)**: tightened the provider credential storage guard so the Stability key must remain a DataStore-backed paid-sensitive secret with blank release defaults, explicit Clear control, and `stability.ai.key` redaction coverage.
- **Generated content reporting (Cycle 134)**: added generated wallpaper report actions, generated-content reason categories, backend reason allowlist support, and privacy/reporting runbook updates.
- **Generated wallpaper disclosure (Cycle 133)**: added a persisted prompt/privacy disclosure gate before Stability requests plus a Settings review/reset path and privacy runbook.
- **Provider key clear UX (Cycle 132)**: consolidated provider API-key dialogs around explicit Save, Clear, and Cancel actions, and extended the storage policy guard to fail if the Clear path disappears.
- **Provider credential APK scan (Cycle 131)**: added a release-stage APK scanner for nonblank provider credential values and wired it after signed APK packaging before release uploads.
- **Provider credential storage policy (Cycle 130)**: classified provider credentials, documented the app-private DataStore/no-Keystore decision, added a checked storage policy gate, and surfaced Freesound key clearing in Settings.
- **Cleartext release gate (Cycle 129)**: removed the ccMixter HTTP fallback and cleartext network-security exception, added a release cleartext guard with focused tests, and wired it into verify and release preflight before signed APK builds.
- **Network endpoint inventory (Cycle 128)**: added a reviewed endpoint manifest/runbook plus a verify-time scanner and live tool tests so new hard-coded app network hosts require auth/cache/fallback review.
- **Request redaction contract (Cycle 127)**: added a shared request redactor for provider query/header/local-property credentials, reused it in crash diagnostics, and redacted source-metrics failure details before Settings displays them.
- **Diagnostics redaction fixtures (Cycle 126)**: added provider-specific crash diagnostics fixtures for Wallhaven, Pixabay, Freesound, SoundCloud, Pexels, Settings, `local.properties`, file URIs, and app-private paths, and tightened assignment redaction for dotted provider properties.
- **Provider credential release guard (Cycle 125)**: added a release preflight that verifies Gradle provider-key defaults are blank, release CI writes blank optional provider keys before signed builds, and nonblank local provider keys fail unless explicitly allowed for an internal-build review.
- **Always-on backend tool tests (Cycle 124)**: wired the lightweight `test/tools` Python suite into the always-on verify job before Android setup, so policy and support-tool drift tests run on every push, pull request, and manual verify run.
- **GitHub workflow secret guard (Cycle 123)**: added a workflow secret-reference policy and verify-time scanner, limiting workflow secret use to the reviewed release signing secrets and blocking unreviewed secret refs, unreviewed env aliases, forbidden token shortcuts, and unexpected workflow files.
- **GitHub workflow permissions guard (Cycle 122)**: added a workflow event/job-permission policy and verify-time scanner for all workflows, blocking unreviewed triggers, permission drift, unexpected jobs, unexpected workflow files, and scalar `write-all` style permission declarations.
- **GitHub Actions allowlist guard (Cycle 121)**: added a workflow action allowlist and verify-time scanner for all workflow `uses:` references, blocking unexpected workflows, unreviewed actions, local actions, unpinned refs, and forbidden floating refs.
- **Gradle wrapper checksum guard (Cycle 120)**: pinned the Gradle 8.12 wrapper ZIP SHA-256 and added a verify-time wrapper policy check for distribution URL, checksum, URL validation, storage roots, and timeout drift.
- **GitHub security settings receipt (Cycle 119)**: added a private-evidence validator and redacted receipt generator for future owner/admin GitHub branch-protection, Dependabot, code-scanning, secret-scanning, and release-attestation settings proof.
- **Dependabot update policy guard (Cycle 118)**: added weekly Dependabot version-update coverage for GitHub Actions, Gradle, root npm, and Functions npm, plus a verify-time policy check for cadence, target branch, PR limits, labels, and commit prefix.
- **GitHub security workflow policy guard (Cycle 117)**: added a checked workflow policy for Dependency Review, OpenSSF Scorecard, and Release so verify fails when security triggers, permissions, attestation, SARIF upload, release bundle, or unsafe escape-hatch expectations drift.
- **Callable rollout evidence receipt (Cycle 116)**: added a private-evidence validator and redacted receipt generator for future live community callable rollout proof across all seven contracted callable surfaces.
- **Callable wire-protocol guard (Cycle 115)**: added a checked Android callable wire-protocol manifest and validator that keep all seven community callable client methods, payload schemas, Android input types, operation prefixes, App Check token choices, response IDs, and focused tests aligned with the backend callable contract.
- **Android profile edit callable migration (Cycle 114)**: added Android creator profile payload normalization, extended the shared callable client for `updateCreatorProfile`, routed creator profile saves through the callable when Firebase Auth is available, and added an edit action to the creator profile screen.
- **Android wallpaper upload finalizer callable migration (Cycle 113)**: added Android wallpaper upload metadata payload normalization, extended the shared callable client for `finalizeCommunityWallpaperUpload`, and routed post-Storage wallpaper upload metadata finalization through the callable when Firebase Auth is available.
- **Android sound upload finalizer callable migration (Cycle 112)**: added Android sound upload metadata payload normalization, extended the shared callable client for `finalizeCommunitySoundUpload`, and routed post-Storage sound upload metadata finalization through the callable when Firebase Auth is available.
- **Android user-block callable migration (Cycle 111)**: added Android block/unblock payload normalization, extended the shared callable client for `setCommunityUserBlock`, routed user block state changes through the callable when Firebase Auth is available, and preserved direct RTDB fallback only for compatibility cases.
- **Android follow callable migration (Cycle 110)**: added Android follow/unfollow payload normalization, extended the shared callable client for `setCreatorFollow`, routed creator follow state changes through the callable when Firebase Auth is available, and preserved direct RTDB fallback only for compatibility cases.
- **Android vote callable migration (Cycle 109)**: added Android vote payload normalization, extended the shared callable client for `recordCommunityVote`, routed vote submissions through the callable when Firebase Auth is available, and preserved direct RTDB fallback only for compatibility cases.
- **Android report callable migration (Cycle 108)**: added the Android Cloud Functions client dependency, a shared callable request/response adapter, limited-use App Check token selection for report submissions, callable report payload tests, and callable-first report submission with a compatibility fallback while deploy evidence is pending.
- **Wallpaper upload callable emulator coverage (Cycle 107)**: added RTDB-emulator-backed `finalizeCommunityWallpaperUpload` handler coverage for public metadata, owner index, quota, storage-path dedupe, and duplicate upload idempotency through the real Admin SDK backend.
- **Sound upload callable emulator coverage (Cycle 106)**: added RTDB-emulator-backed `finalizeCommunitySoundUpload` handler coverage for public metadata, owner index, quota, storage-path dedupe, and duplicate upload idempotency through the real Admin SDK backend.
- **User block callable emulator coverage (Cycle 105)**: added RTDB-emulator-backed `setCommunityUserBlock` handler coverage for private block rows, reverse-index rows, unblock removals, quota, dedupe, and no-op idempotency through the real Admin SDK backend.
- **Follow callable emulator coverage (Cycle 104)**: added RTDB-emulator-backed `setCreatorFollow` handler coverage for follow writes, unfollow removals, quota, dedupe, and no-op idempotency through the real Admin SDK backend.
- **Vote callable emulator coverage (Cycle 103)**: added RTDB-emulator-backed `recordCommunityVote` handler coverage for vote tally, nested and legacy voter markers, quota, dedupe, and repeat-vote idempotency through the real Admin SDK backend.
- **Report callable emulator coverage (Cycle 102)**: added RTDB-emulator-backed `submitCommunityReport` handler coverage for report, quota, dedupe, and duplicate report writes through the real Admin SDK backend.
- **Profile callable emulator coverage (Cycle 101)**: added `npm run test:functions-emulator` and an RTDB-emulator-backed profile handler test that verifies profile, quota, dedupe, and unchanged-profile idempotency writes through the real Admin SDK backend, then wired the script into the Firebase backend CI lane.
- **Creator profile callable handler (Cycle 100)**: implemented the `updateCreatorProfile` Functions handler with Auth/App Check enforcement, server-derived profile UID and timestamps, display-copy normalization, normalized-profile dedupe, quota checks, and focused unit coverage.
- **Community wallpaper upload callable handler (Cycle 99)**: implemented the `finalizeCommunityWallpaperUpload` Functions handler with Auth/App Check enforcement, server-allocated upload IDs, wallpaper metadata normalization, Storage path ownership checks, storage-path dedupe, public metadata and owner-index writes, and focused unit coverage.
- **Community sound upload callable handler (Cycle 98)**: implemented the `finalizeCommunitySoundUpload` Functions handler with Auth/App Check enforcement, server-allocated upload IDs, sound metadata normalization, Storage path ownership checks, storage-path dedupe, public metadata and owner-index writes, and focused unit coverage.
- **Community block callable handler (Cycle 97)**: implemented the `setCommunityUserBlock` Functions handler with Auth/App Check enforcement, block/unblock payload normalization, state-aware dedupe keys, private and reverse-index writes, and focused unit coverage.
- **Creator follow callable handler (Cycle 96)**: implemented the `setCreatorFollow` Functions handler with Auth/App Check enforcement, follow/unfollow payload normalization, state-aware dedupe keys, no-op idempotency, UTC quota reservation, and focused unit coverage.
- **Community vote callable handler (Cycle 95)**: implemented the `recordCommunityVote` Functions handler with Auth/App Check enforcement, vote-key normalization, existing-voter idempotency, UTC quota reservation, dedupe handling, and focused unit coverage.
- **Community report callable handler (Cycle 94)**: implemented the `submitCommunityReport` Functions handler core with Auth/App Check identity enforcement, server-derived reporter UID, HTTPS/source validation, UTC quota reservation, dedupe handling, and focused unit coverage.
- **Cloud Functions scaffold (Cycle 93)**: added a Node 20 TypeScript `functions/` project with App Check/Auth fail-closed callable exports, a manifest-synced callable contract mirror, a UTC quota decision engine, backend manifest coverage, and CI tests.
- **Hosted deletion page template gate (Cycle 92)**: added checked publishable copy and a validator for the hosted account deletion request page before the owner assigns a live HTTPS URL.
- **Callable contract manifest gate (Cycle 91)**: added a backend JSON manifest and validator for the community callable quota contract, including UTC quota-day boundary, App Check/Auth requirements, final write paths, and CI coverage.
- **Upload deletion execution receipt (Cycle 90)**: added a redacted receipt builder for owner/admin public-upload deletion evidence after clean account-deletion upload plans, covering Storage, metadata, owner-index, and tombstone completion.
- **Auth deletion execution receipt (Cycle 89)**: added a redacted receipt builder for owner-approved Firebase Auth deletion evidence, including UID/support validation, post-delete not-found verification, and private evidence hashing.
- **Account deletion web URL gate (Cycle 88)**: added a privacy-policy-backed hosted deletion URL manifest and validator that keeps the web request route in an explicit pending owner-publication state until a live HTTPS URL is linked from policy and support docs.
- **Account deletion upload handoff (Cycle 87)**: added a private upload deletion planner that consumes the Auth package, enumerates owned public sound/wallpaper uploads with valid Storage handles, and blocks rows needing backfill or manual review before owner/admin deletion workflow execution.
- **Account deletion Auth package (Cycle 86)**: added a private Firebase Auth deletion package builder that requires matching request-code lookup and backend completion evidence before exposing the full UID to owner-approved Auth deletion.
- **In-app local community cleanup (Cycle 85)**: Settings > Community identity now offers `Clear local` for the current device fallback identity, refreshes the redacted summary after clearing, and keeps backend/Auth/upload deletion in the support chain.
- **Account deletion cleanup sequence (Cycle 84)**: added a post-completion sequencing tool that requires a completed backend receipt before ordering requester local cleanup, operator Firebase Auth deletion, and public upload deletion handoff.
- **Account deletion web intake (Cycle 83)**: added a private hosted-form contract and validator that hashes requester contact/statement fields, requires deletion/retention/public-upload attestations, and emits a redacted intake receipt before operator lookup.
- **Account deletion completion receipt (Cycle 82)**: added a redacted receipt builder that validates applied REST receipts against private executor packages, rejects dry-run receipts, and keeps full UIDs, RTDB paths, database hosts, update payloads, and access tokens out of requester-facing artifacts.
- **Account deletion REST executor (Cycle 81)**: added a guarded RTDB REST executor with dry-run default, explicit request-code and plan-hash confirmations for apply mode, and unit coverage for PATCH and bearer-token handling.
- **Account deletion executor package (Cycle 80)**: added a private package builder that validates account deletion plan, review, and simulation receipts before emitting the RTDB null-update payload for a future trusted executor.
- **Account deletion apply simulator (Cycle 79)**: added an offline backend simulator that verifies reviewed deletion plans, applies null updates to an RTDB export copy, and emits hashed receipts without contacting Firebase.
- **Account deletion review gate (Cycle 78)**: added a backend review tool that cross-checks deletion request-code lookup output against dry-run RTDB null-update plans and emits redacted receipts before any future trusted apply step.
- **Deletion request code lookup (Cycle 77)**: added a backend lookup tool that maps shared `AURA-` deletion request codes to candidate UID evidence in RTDB exports, with unit coverage and backend CI change detection.
- **Community deletion request routing (Cycle 76)**: the Community identity dialog can now share a redacted deletion request draft, and support docs describe the private request/operator handling flow without exposing full Firebase UIDs.
- **Community identity request surface (Cycle 75)**: Settings now exposes the current community auth label, redacted identity suffix, and a deletion request code when a Firebase identity exists without creating a new identity just by opening the panel.
- **Account deletion dry-run planner (Cycle 74)**: added a backend tool and policy doc that plan RTDB marker deletion for vote markers, follows, creator profiles, block indexes, and community shares while retaining aggregate counts and moderation audit records.
- **Report/profile block actions (Cycle 73)**: community reports now carry optional uploader UID metadata, admin report cards can block reported community uploaders, and creator profile rows expose confirmed block actions that immediately remove matching creator rows.
- **Blocked creators review (Cycle 72)**: Settings now shows blocked community creators with reason/timestamp metadata and per-row unblock actions backed by `CommunityBlockRepository`.
- **Visible block creator actions (Cycle 71)**: community sound and wallpaper detail surfaces now expose confirmed block actions when uploader identity is available, write through `CommunityBlockRepository`, and remove matching uploader rows from the current UI state.
- **Community block-user filtering (Cycle 70)**: added Android block-list repository reads/writes, kept public browsing from creating an identity solely for filtering, and filtered community sound feeds, wallpaper feeds, and creator profile lists by private block state.
- **Public takedown copy (Cycle 69)**: community upload dialogs now disclose public listing behavior and rights-takedown outcomes, report dialogs explain the private rights-takedown route, and owner-delete confirmations describe public metadata/index removal plus private moderation record retention.
- **Community block-user policy (Cycle 68)**: reserved private user block lists and admin reverse indexes in RTDB rules, added emulator coverage, and extended the callable quota contract with `setCommunityUserBlock`.
- **Community deletion tombstones (Cycle 67)**: owner and admin upload deletes now write private deletion tombstones with owner-scoped Storage handles, RTDB rules coverage, and a retention policy for deleted upload evidence.
- **Legacy upload backfill planning (Cycle 66)**: added a dry-run RTDB backfill planner for legacy community uploads missing `storagePath` and owner indexes, with tests for URL parsing, update generation, and unsafe-row blocking.
- **Community Storage lifecycle policy (Cycle 65)**: added an offline orphan-report tool and unittest, documented the no-auto-delete policy for committed upload prefixes, and defined the two-report manual cleanup gate.
- **Community backend deploy evidence (Cycle 64)**: added a deterministic Firebase backend manifest, CI manifest check, and deploy/rollback runbook for Realtime Database and Cloud Storage rules changes.
- **Callable quota contract (Cycle 63)**: community quota policies now include callable function names, payload schemas, final write paths, protected ledger coverage, and limited-use App Check token decisions for reports, uploads, votes, follows, and profile edits; added the backend migration runbook.
- **Closed report review filters (Cycle 62)**: the admin community report queue now switches between Open, Hidden, Dismissed, and Restored status filters, including from the empty state, so closed moderation outcomes remain reviewable.
- **Admin upload delete actions (Cycle 61)**: custom-claim admins can delete qualifying rights-reported community uploads from the report queue; the flow records a `DELETE` takedown receipt, hides the content, deletes the Storage object, removes upload metadata/index rows, and marks the receipt succeeded or failed for retry evidence.
- **Rights takedown receipts (Cycle 60)**: hiding a rights report for a community sound or wallpaper now records a private admin takedown receipt with the current upload metadata path, Storage deletion handle, uploader UID, resolver UID, timestamp, and RTDB rules/emulator coverage that reject stale or mismatched handles.
- **Firebase rules CI gate (Cycle 59)**: the main verify workflow now detects Firebase rules/config/test/runbook changes, installs pinned npm tooling, and runs the combined RTDB + Storage emulator suite.
- **RTDB rules harness (Cycle 58)**: added Realtime Database emulator config and tests for community upload metadata, owner upload indexes, reports, report resolutions, quota/dedupe ledgers, and collection shares; aligned collection share rules to `shared_collections` with `createdByUid`; and made `database.rules.json` emulator/deploy-compatible.
- **Storage rules harness (Cycle 57)**: added tracked Firebase Storage rules, Firebase emulator config, a local npm rules-unit-testing harness, and Storage emulator tests for owner-only community upload writes/deletes, MIME/size ceilings, public reads, and unmanaged path denial.
- **Visible community owner deletes (Cycle 56)**: owner-owned community sound and wallpaper detail surfaces now show delete actions only when Firebase metadata proves the signed-in owner and a `storagePath` deletion handle; confirmations call the existing blob and metadata delete paths.
- **Community upload deletion handles (Cycle 55)**: new community sound and wallpaper uploads now store canonical Storage paths, write private owner indexes, and expose repository owner-delete methods that remove Storage blobs plus public metadata/index rows for new uploads.
- **Community quota policy (Cycle 54)**: added typed quota/rate-limit rows for reports, sound uploads, wallpaper uploads, votes, follows, and profile edits, reserved admin-only RTDB quota/dedupe ledgers, and documented the App-Checked callable migration path.
- **Firebase App Check client rollout (Cycle 53)**: debug builds now install the Firebase App Check debug provider, release builds install the Play Integrity provider before Firebase-backed community startup work, and the rollout runbook covers debug tokens, side-loaded distribution settings, metrics burn-in, and enforcement gates.
- **Admin report review (Cycle 52)**: custom-claim admins can open Settings > Community reports, review open reports, hide reported content through the global moderation list, dismiss reports, or restore hidden content with resolution metadata.
- **Community report queue intake (Cycle 51)**: sound and wallpaper detail screens now submit private reports with rights/source-removed/safety/spam/other reasons, source/license/uploader context, RTDB report and resolution rules, and admin resolution metadata.
- **Community upload rights metadata (Cycle 50)**: community sound and wallpaper uploads now require rights attestation, selected CC0/CC BY/CC BY-NC metadata, optional HTTPS source URLs, RTDB rule validation, community sound license gates, and wallpaper license detail display.
- **Sound license capability gates (Cycle 49)**: sounds now derive action capabilities from source/license/provenance metadata; YouTube apply/download requires confirmation, SoundCloud is link-only until reviewed, missing remote licenses disable live-source actions, saved sound favorites preserve license metadata, and sound shares include source/uploader/license provenance.
- **Provider removal reconciliation (Cycle 48)**: explicit 404/410/gone/removed/deleted provider failures now mark saved wallpaper/sound favorites and matching download-history rows as source-unavailable during apply/download paths.
- **Pexels enhancement guardrails (Cycle 47)**: Discover and video-wallpaper discovery now treat Pexels as an enhancement source; Pexels-only batches are dropped, disabled-Pexels Discover still returns Wallhaven/Pixabay fallback inventory, and Pexels photo rows keep creator/source-page context.
- **Saved-source availability states (Cycle 46)**: favorites and download history now persist a source-availability state; marked items show "Source unavailable" in saved surfaces and detail screens, hide live-source affordances, and keep local saved wallpaper paths usable.
- **Generated wallpaper source switch (Cycle 45)**: Settings now has a default-on generated-wallpapers source switch; disabled mode hides generation entry points, blocks Stability requests before prompt or key validation, and keeps saved generated local wallpapers visible.
- **Pixabay video request-cache and backoff (Cycle 44)**: Pixabay video metadata now uses an app-private 24-hour fresh-cache path before API calls, persists 429 backoff from `Retry-After` or `X-RateLimit-Reset`, and falls back to stale cached video rows during active backoff.
- **Pixabay photo request-cache and backoff (Cycle 43)**: Pixabay photo results now use a 24-hour fresh-cache path before API calls and 429 responses set an in-session backoff from `Retry-After` or `X-RateLimit-Reset`; video metadata policy handling followed in Cycle 44.
- **Wallhaven source switch (Cycle 42)**: Settings now has a default-on Wallhaven source switch; disabled mode hides Wallhaven browsing and color/random/similar actions, removes Wallhaven from rotation pickers, skips Wallhaven API calls before key reads/cache fallback, and records disabled diagnostics separately from outages.
- **Bing Daily source switch (Cycle 41)**: Settings now has a default-on Bing Daily source switch; disabled mode skips daily-image API calls before cache fallback or Retrofit use, hides Bing from rotation pickers, and records disabled diagnostics separately from outages.
- **Community source switch (Cycle 40)**: Settings now has a default-on Community source switch; disabled mode skips startup identity warm-up, hides community tabs/uploads/votes/creator profile entry points, blocks feed/upload/follow Firebase calls, and records disabled diagnostics separately from Firebase outages.
- **Pexels and Pixabay source switches (Cycle 39)**: Settings now has default-on Pexels and Pixabay source switches; disabled mode hides their wallpaper tabs, skips Discover/search/style-biased/video API calls before bundled keys are read, removes disabled Pixabay from rotation pickers, and records disabled source diagnostics separately from outages.
- **Reddit source switch (Cycle 38)**: Settings now has a default-on Reddit feature switch; disabled mode hides Reddit wallpaper browsing, skips daily picks, background rotations, repository calls, and video wallpaper discovery, and records disabled source diagnostics separately from outages.
- **YouTube legal-mode switch (Cycle 37)**: Settings now has a default-on YouTube feature switch; disabled mode hides YouTube sound browsing, falls back to bundled sounds, skips YouTube video wallpaper discovery, blocks stream resolution before cache/downloader use, and records disabled source diagnostics separately from outages.
- **Provider runtime controls (Cycle 36)**: added a checked runtime-control matrix for every content source, documented current disabled-provider behavior, and identified YouTube as the next legal-mode/offline-risk switch.
- **Generated notice metadata parity (Cycle 35)**: `tools/dependency_notice_lock.py` now has a `check-metadata` mode, and PR/main plus release workflows fail when raw Google OSS metadata rows no longer match the reviewed dependency notice lockfile.
- **Generated notice search and review markers (Cycle 34)**: the in-app generated dependency notice viewer now supports filtering by dependency name or license label and highlights generated rows that map to curated high-risk dependency review surfaces.
- **In-app generated dependency notices (Cycle 33)**: Settings > Open source licenses now reads generated Google OSS raw resources directly, lists generated dependency notices in-app, and opens full notice text without adding the stock Play services OSS licenses runtime dependency.
- **Raw Google OSS input retention (Cycle 32)**: documented that `GOOGLE-OSS-RAW-INPUTS.zip` stays attached to every tagged public release, clarified release/dry-run docs, and kept the bundle validator enforcing the archive in release files, checksums, and notes.
- **Dependency license policy gate (Cycle 31)**: added `docs/legal/dependency-license-policy.json` and `tools/dependency_license_policy.py`, then wired PR/main verification and release builds to fail unknown, disallowed, or unreviewed curated license IDs before publishing release artifacts.
- **FFmpeg source-correspondence evidence (Cycle 30)**: native compliance now extracts embedded FFmpeg 7.1.1 configure lines and license-mode flags from the resolved youtubedl-android FFmpeg payload, locks those facts for drift review, and adds `docs/legal/ffmpeg-source-correspondence.md` as the release-owner checklist for remaining Termux source/build-log evidence.
- **Generated notice access in Settings (Cycle 29)**: Settings > Open source licenses now starts with release notice cards for `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, and `NATIVE-COMPLIANCE.md`, while keeping manual library rows and content-source disclosures separate.
- **Raw release notice input archive (Cycle 28)**: release builds now create `GOOGLE-OSS-RAW-INPUTS.zip` with generated `dependencies.json`, `third_party_license_metadata`, `third_party_licenses`, and a manifest, then include the archive in checksums, release notes, workflow artifacts, tagged release assets, and bundle validation.
- **Release artifact dry-run validation (Cycle 27)**: added `tools/release_artifact_bundle_check.py` and a release dry-run runbook so manual release workflow runs and tag releases fail when the final APK/notices/native/checksum/release-note bundle is incomplete or internally inconsistent.
- **Curated dependency overlay gate (Cycle 26)**: added `docs/legal/dependency-notice-overrides.json` and `tools/dependency_overlay_check.py` so PR/main verification and release builds require reviewed source URLs, license IDs, usage notes, and release-review notes for high-risk dependencies and native payloads.
- **Native compliance drift gate (Cycle 25)**: `tools/native_compliance_inventory.py` now writes/checks `docs/legal/native-compliance.lock.json`, and PR/main verification plus release builds fail when native/copyleft artifact hashes or extracted payload facts drift without review.
- **Dependency notice drift gate (Cycle 24)**: added `tools/dependency_notice_lock.py` and `docs/legal/dependency-notices.lock.json` so PR/main verification and release builds fail when generated release dependency notices drift without review.
- **Native compliance packet (Cycle 23)**: release builds now generate `NATIVE-COMPLIANCE.md`, include it in `SHA256SUMS.txt`, and upload/attach it beside tagged APK releases. Added `tools/native_compliance_inventory.py` and committed `docs/legal/native-compliance.md` with youtubedl-android, yt-dlp/Python, QuickJS, FFmpeg, and NewPipeExtractor payload evidence.
- **Release third-party notices (Cycle 22)**: release builds now generate Google OSS license outputs, convert them to `THIRD-PARTY-NOTICES.md`, include the notices in `SHA256SUMS.txt`, and upload/attach the notices beside tagged APK releases. Added `tools/google_oss_to_markdown.py` and documented the local notice-generation path.
- **Provider disclosure matrix (Cycle 17 partial)**: Settings > Open source licenses now gets content-source disclosures from a central `ProviderDisclosure` model covering every `ContentSource`, including dormant legacy sources, local media, community uploads, bundled Aura Picks, and AI-generated content. Added `docs/legal/provider-policy.md`, expanded visible runtime/native dependency notice rows, and added a unit test that fails when new content sources lack policy coverage.
- **Baseline Profile + macrobenchmark harness (L-8/U-13 / Cycle 2 P1 partial)**: added a `:baselineprofile` producer module, `ProfileInstaller`, a shell-profileable manifest entry, startup and grid-scroll Macrobenchmark tests, manual self-hosted physical-device CI artifact upload, and the runbook for generating/attaching Baseline Profile and frame-timing evidence. Physical-device generation is pending because no adb target is attached locally.
- **Developer verification + Izzy prep (NX-8 / Cycle 2 P1)**: release notes now include Android developer verification status for `com.freevibe`, and new distribution docs capture the owner-only ADC/PDC package registration path, IzzyOnDroid submission checklist, F-Droid blocker state, and branch-protection owner action for requiring `verify`.
- **Supply-chain CI follow-up (NX-8/NX-12 / Cycle 2 P1)**: OpenSSF Scorecard now runs as SARIF-only with job-scoped code-scanning upload permissions, and Gradle dependency verification metadata includes the clean-runner JUnit BOM module hashes and Linux `aapt2` artifact exposed by CI.
- **Crash/ANR diagnostics (NX-8 / Cycle 2 P1)**: Settings now exposes a local-only crash diagnostics bundle with last crash timestamp, manual Copy/Share actions, app/Android/ABI/source context, reproduction fields, and sanitized `crash.log` tail. Added a crash report issue template, support docs, sanitizer/parser tests, and the missing Windows `aapt2` dependency-verification checksums uncovered by the focused test run.
- **Supply-chain verification (NX-8/NX-12 / Cycle 2 P1)**: release workflow now grants attestation permissions and uses `actions/attest@v4` against `SHA256SUMS.txt`, release notes include the artifact attestation URL, pull requests run Dependency Review, OpenSSF Scorecard runs on main/schedule/manual triggers with SARIF upload, `gradle/verification-metadata.xml` records resolved dependency checksums, and `docs/distribution/supply-chain.md` documents release verification and deferred SBOM scope.
- **Distribution channel strategy (NX-8 / Cycle 2 P0)**: new `docs/distribution/channel-strategy.md` records the full-vs-foss decision. Aura stays full-only for GitHub Releases/Obtainium and treats IzzyOnDroid as the realistic near-term app-store target; F-Droid mainline is blocked until Firebase, the Google Services plugin, and Play Services ML Kit are isolated behind a real FOSS flavor or removed. New `tools/fdroid_preflight.py` provides a no-build check that reports the current blocker state.
- **Release integrity (NX-8 / Cycle 2 P0)**: `.github/workflows/release.yml` now builds signed `assembleRelease` artifacts instead of debug APKs, restores signing material from GitHub secrets, rejects debuggable APKs, runs `apksigner verify --print-certs`, and publishes `SHA256SUMS.txt` plus release notes with versionCode/versionName and signing certificate SHA-256. New distribution docs cover required secrets, local release verification, and Obtainium checksum checks.
- **Unit-test harness repair**: stale JVM test fixtures now construct `SelectedContentHolder` with mocked SharedPreferences + Moshi, wire the Smart Crop detector dependency into crop ViewModel tests, stub NX-6 rotation-trigger preference flows, and use a pure `SmartCropCalculator.SubjectBounds` overload so local tests do not depend on stubbed Android `RectF` constructors.
- **Android 17 EyeDropper API (NX-10)**: new "Pick colour" FAB on the wallpaper Discover tab opens the system EyeDropper overlay on Android 17+ devices. The picked colour seeds a Wallhaven `colors=` search. Raw-string Intent integration is compatible with compileSdk 35 today and resolves to a direct API call once the toolchain bumps. FAB auto-hides on builds where the system EyeDropper app isn't installed (un-updated GSI).
- **Photo Picker 9:16 portrait grid (NX-11)**: wallpaper community upload, collection QR import, and parallax-from-photo gallery picker now request the Android 17 `PhotoPickerUiCustomizationParams` 9:16 portrait aspect ratio via a drop-in `AuraPickVisualMedia` subclass. Reflection ships the runtime behaviour at compileSdk 35; becomes a straight-line API call once the toolchain bumps. Android 16 and below pass through transparently to the existing 1:1 grid.
- **Smart Crop video variant (NX-3)**: TopAppBar "Smart" action on the video crop screen extracts the loop-start frame, runs the same subject segmentation wallpaper crop uses, and pans the video so the subject lands at the viewport centre. Keeps the user's chosen zoom (different from the wallpaper variant, which auto-zooms). Toasts a "drag to position" fallback when segmentation can't find a subject.
- **Smart Crop (NX-3)**: new "Smart Crop" chip on the wallpaper crop screen runs ML Kit Subject Segmentation against the loaded bitmap, then centres the detected subject in the 9:16 viewport at ~75 % coverage with a fill-viewport floor. Falls back to "Couldn't detect a subject, drag to position manually" when segmentation returns no foreground subject. Seven unit tests cover the pure-geometry helper.
- **Editor unsaved-changes guard (NX-13)**: backing out of the wallpaper editor with non-default filters or the sound editor with active trim / fade settings now prompts a "Discard edits?" confirmation instead of silently throwing away the work. Discard resets state and exits; Keep editing dismisses.
- **Contributor docs (U-12)**: new `CONTRIBUTING.md` covers the charter (no ads, no tracking, AMOLED-first, free by default), build steps, code style, commit conventions, and test guidance. New `ARCHITECTURE.md` describes the layered model, package map, key abstractions, process-death + live-wallpaper engine discipline, and design system rules.
- **Rotation triggers (NX-6)**: opt-in per-unlock and screen-off pre-stage rotation via a new `RotationTriggerService` foreground service. Two Settings toggles ("Change on every unlock" + "Pre-stage on screen off") gate the service lifecycle; users see a low-priority notification only when at least one trigger is on. Each fire enqueues a one-shot expedited `AutoWallpaperWorker` that respects the existing rotation source / target / constraint prefs.
- **Tasker hook (L-2)**: new `com.freevibe.action.ROTATE_NOW` + `com.freevibe.action.SHUFFLE_NOW` exported broadcast actions. Tasker / MacroDroid / adb-shell scripts can wire Aura into calendar events, geofences, Bluetooth-connected, etc. with a one-line Send Intent.
- **Lockscreen widget (NX-2)**: widget category bumped from `home_screen` to `home_screen|keyguard` so Android 16 QPR2+ users can place the existing widget on the lockscreen surface without code changes. Older Android versions silently ignore the keyguard bit.
- **Back-press cancellation (NX-13)**: pressing back during a Stability AI generation now cancels the in-flight job and saves the user's API credit budget. Video crop guards against accidental back-out mid-FFmpeg with a "Cropping in progress" toast.
- **Process-death selection survival (NX-4)**: `SelectedContentHolder` now persists the single selected wallpaper + selected sound to a SharedPreferences JSON snapshot on every selection, so detail screens are no longer blank after process death. Pager list is intentionally still in-memory only.
- **CI verification (NX-12)**: new `.github/workflows/verify.yml` runs `assembleDebug` + `testDebugUnitTest` + `lintDebug` on every push to main and every PR. Uploads test + lint reports as artifacts on failure.
- **Fastlane refresh (NX-8 partial)**: fastlane metadata bumped from stale FreeVibe naming to Aura with current feature set; `changelogs/111.txt` lands v6.31.0 release notes; new `obtainium.json` at repo root lets Obtainium users track Aura via the GitHub Releases feed.

## v6.31.1
- **Fix: Sounds/search crash on Android < 13 (issue #2)**: enabled core library desugaring (`desugar_jdk_libs:2.1.5`). NewPipeExtractor's `Utils.encodeUrlUtf8` calls `URLEncoder.encode(String, Charset)`, an API 33 method, on every YouTube search, which threw `NoSuchMethodError` and crashed the app the moment the Sounds tab loaded on Android 8 to 12. Desugaring backports the method down to the minSdk 26 floor.

## v6.31.0
- **Shareable collections**: wallpaper collections now publish Firebase-backed Aura links, include those links in the system share sheet, and can display scannable QR codes.
- **Collection import**: Collections now has an import action for pasted Aura links, shared JSON files, and QR images, with imported media remaining URL-backed until opened or applied.
- **Deep-link support**: `aura://collection/import/{token}` and shared JSON intents route directly into the Collections import flow.

## v6.30.0
- **Creator profiles**: Settings now links to a creator profile dashboard with current identity, upload count, total votes, saved favorites count, followed creators, followed uploads, and top creators leaderboard.
- **Follow creators**: creator follows persist through Firebase RTDB and the profile screen surfaces new uploads from followed creators.
- **OAuth guardrail**: Google sign-in remains disabled until the Firebase config includes an OAuth client; anonymous Firebase identity continues to back community uploads.

## v6.29.0
- **Community wallpaper uploads**: Wallpapers now includes a Community source where users can pick gallery images, crop them automatically to the phone aspect ratio, and upload compressed JPEGs under 4 MB to Firebase Storage.
- **Wallpaper upload metadata**: community uploads require name, category, and tags, then store Palette-derived colors, dimensions, uploader label, and Firebase RTDB metadata for discovery.
- **Wallpaper community moderation**: community wallpapers use the existing vote/hide controls and hide negative-score uploads from the feed.

## v6.28.0
- **Community sound uploads completed**: Sounds > Community can now record from the microphone or pick an audio file, then require name, category, and searchable tags before upload.
- **Community Picks**: top-voted community sounds surface in a dedicated section, while community results are sorted by vote count and hidden once the user hides them.
- **Moderation polish**: negative-score community uploads are filtered out of the feed, and sound cards expose upvote/hide actions for uploaded content.

## v6.27.0
- **Video battery dashboard**: Settings > Video Wallpapers now shows live device battery, wallpaper-service heartbeat, effective FPS, media type, presentation mode, and estimated impact.
- **Auto battery saver**: Video/GIF live wallpapers can automatically cap playback at 15 FPS when the device drops below 15% battery and is not charging.
- **Debug FPS overlay**: Canvas-rendered motion wallpapers can show a compact FPS readout for development and frame-pacing checks.

## v6.26.0
- **Touch-reactive live wallpaper effects**: Weather live wallpapers now support transient touch ripples and spark bursts, rendered as bounded Canvas overlays.
- **Settings control**: Settings > Smart Features adds Touch effects with Off, Subtle ripples, and Ripples + sparkles modes.
- **Guardrails**: touch bursts are capped and expire quickly so inactive wallpapers do not keep extra work alive.

## v6.25.0
- **Video timeline thumbnails**: Loop & Crop now renders a bounded strip of sampled video frames beneath the loop scrubber, with graceful fallback to the plain slider when extraction is unavailable.
- **Frame sampling guardrails**: thumbnail positions are evenly spread across the clip and capped to six frames to avoid expensive work on long videos.
- **Phase 5.2 completion**: roadmap now marks the video loop editor complete: thumbnails, loop range selection, loop preview, and FFmpeg trim/crop export are all implemented.

## v6.24.0
- **Video loop trim editor**: the crop editor is now a Loop & Crop flow with start/end range controls and a preview that loops only the selected segment.
- **Trimmed video export**: FFmpeg crop output now includes the selected `-ss`/`-t` range, so applied live wallpapers skip intros/outros instead of always exporting the full clip.
- **Loop helper coverage**: added focused tests for loop-range coercion and FFmpeg trim argument formatting.

## v6.23.0
- **Video wallpaper presentation controls**: online video apply now offers Fill and Fit before setup. Fill keeps the premium full-screen crop behavior, while Fit preserves the complete frame with letterboxing.
- **Runtime scale-mode support**: `VideoWallpaperService` now reads the selected scale mode for both MediaPlayer videos and canvas-rendered GIF wallpapers.
- **Roadmap closure**: Phase 5.1 fit/fill/crop controls are now represented in the apply flow; remaining video work is the deeper loop trim/timeline editor.

## v6.22.0
- **Local video/GIF wallpapers**: Video Wallpapers and Settings now open a single system picker for local `video/*` clips and animated GIFs, then copy the selection into Aura-managed storage for live wallpaper setup.
- **Animated GIF live wallpaper playback**: `VideoWallpaperService` now detects `.gif` selections and renders them through a bounded canvas loop while keeping the existing MediaPlayer path for videos.
- **Import UX cleanup**: removed the dead "GIF not supported" Settings entry, updated gallery actions and fallback toasts to use motion-wallpaper copy, and expanded storage tests for GIF/MOV/MKV extension handling.

## v6.21.0
- **YouTube-only sound feed**: Sounds browsing and in-tab search now use YouTube results only. Audius is removed from the active Sounds experience and user-facing source copy; legacy Freesound attribution remains only for older saved content.
- **Intent-specific YouTube discovery**: default sound searches now seed from `Ringtones`, `Notifications`, and `Alarms`, then add one precise sound-effect query per tab. Duration filters clamp notifications to very short clips, keep alarms short and direct, and avoid long ringtone compilations.

## v6.20.0
- **Wallpaper detail overlay placement**: the compact wallpaper apply card now sits lower on devices with three-button navigation, using the previously empty bottom inset so more of the wallpaper remains visible above the controls.

## v6.19.0
- **Wallpaper detail visibility**: opening a wallpaper detail now keeps the image visible by default with a compact bottom action card instead of the full metadata/apply panel covering the wallpaper. Dense metadata, palette, tags, and extended actions remain available from Details, with a clear Show image action to collapse the panel again.

## v6.18.0
- **Sound source cleanup**: Sounds browsing and search now aggregate only Freesound, Audius, and YouTube, removing Aura Picks/bundled results, ccMixter, SoundCloud, and the old Openverse fallback from the user-facing sound feed.
- **YouTube readiness**: Opening or clearing the YouTube sounds tab now loads a default YouTube query automatically, and YouTube play buttons show a loading spinner plus clearer copy while the stream resolves or buffers before playback starts.

## v6.17.0
- **Secondary-flow premium polish**: refined the post-browse UX for sound detail, contact assignment, wallpaper preview, video preview, wallpaper edit/crop, and sound trim/edit flows so recovery, permission, loading, empty, and action states now match the v6.16 design system instead of falling back to ad hoc centered spinners and copy.
- **First-run finish**: tightened onboarding status labels, feature badges, page indicators, and navigation buttons to the same rectangular 4-12dp shape language, and simplified the first welcome page so the primary CTA is not crowded by a partially visible feature card.
- **Contact ringtone assignment**: replaced plain permission prompts and empty contact views with shared `AuraStateCard` recovery affordances, upgraded contact search to the shared compact search field, switched circular initials to rectangular avatars, and added per-contact applying feedback while assignments run.
- **Sound detail quality**: upgraded the waveform play control, permission warning, secondary action row, disabled share handling, similar-sound skeleton loading, and empty-similar state for clearer hierarchy, stronger touch targets, and more trustworthy feedback.
- **Editor and preview consistency**: brought wallpaper editor/crop and sound editor unavailable/loading/first-run states onto the shared state-card pattern, tightened chip/button radii to the 4-12dp system, clarified audio editor microcopy, and normalized preview action controls.

## v6.16.0
- **Premium UX polish pass**: tightened Aura's Compose design system around neutral AMOLED surfaces, brass/mist/coral accents, rectangular 4-12dp radii, zero letter spacing, calmer elevation, and no pill-shaped status backdrops.
- **Navigation and component consistency**: removed decorative gradient orbs from the app root, replaced Material pill indicators/badges with quieter rectangular count badges, normalized `GlassCard`, `HighlightPill`, search dropdowns, bottom navigation, cards, settings rows, sheets, preview surfaces, and diagnostic metrics.
- **States and feedback**: added a shared `AuraStateCard` pattern and applied it to wallpapers, sounds, video wallpapers, downloads, favorites, collections, wallpaper history, and loading/error/empty states so recovery copy, actions, icons, and spacing feel consistent.
- **Workflow clarity**: clarified the wallpaper generation entry point, improved sound/video retry and gallery fallback affordances, improved API-key visibility accessibility copy, and made empty states explain the next useful action instead of stopping at "nothing here."
- **Verification**: `assembleDebug`, `testDebugUnitTest`, and `lintDebug` are green. USB install was not performed because the connected phone already has `com.freevibe` installed with a different signing key; uninstalling it would remove or disturb the user's installed app.

## v6.15.0
- **Deep audit pass**: eleven real bugs found in the v6.13 to v6.14 deltas (AI wallpaper, Phase 6.2 dark/light auto-switch, Phase 6.4 adaptive tint, Phase 2.5 seasonal/Pexels). All fixes ship with unit-test regression nets.
- **Data integrity (P0)**: `WeatherUpdateWorker` was storing latitude/longitude with `putLong(value.toLong())`, silently truncating fractional degrees. A user at 39.7392° was stored as `39`, a user near the equator at 0.5° was stored as `0`. The reader then used the `tintLat != 0.0 && tintLon != 0.0` sentinel to gate adaptive tinting, so anyone within 1° of Null Island had tinting disabled entirely. Switched to `putFloat` (~7 sig figs, sub-meter precision) plus a `location_present` boolean sentinel. Reader falls back to the legacy Long keys for a single update cycle so existing installs don't lose tinting between upgrade and the next 30-min worker tick.
- **Correctness (P1)**: `SolarCalculator.sunTimes` default UTC-offset arg used `TimeZone.getDefault().rawOffset` which ignores DST. Every region observing daylight saving had sunrise/sunset shifted by an hour for ~half the year, which the adaptive-tint phase math depends on. Switched to `getOffset(System.currentTimeMillis())`.
- **Battery + correctness (P1)**: `SystemThemeListener` (Phase 6.2 new code) ran a 500 ms `while (true)` polling loop that (a) never stopped when the user disabled auto-switch, (b) trapped the outer flow-collector so further preference emissions couldn't propagate, and (c) woke the CPU twice a second forever. Replaced with `ComponentCallbacks.onConfigurationChanged`, an actual event, delivered even while the app is fully backgrounded.
- **Reliability (P1)**: `SystemThemeListener.applyStoredWallpaper` called `WallpaperApplier.applyFromUrl`, which only speaks HTTP, `OkHttp.Request.Builder().url(...)` throws `IllegalArgumentException` for `file://` or `content://` schemes. Users whose last applied wallpaper was AI-generated (`file:/data/.../foo.png`) silently lost auto-switch. New `WallpaperApplier.applyByLocator` dispatches on scheme: http(s) → existing OkHttp path, file/content/absolute-path → bounded two-pass `BitmapFactory` decode with the same 64 MB ceiling and `inSampleSize` sampling.
- **Storage leak (P1)**: `AiWallpaperRepository.pruneOldFiles` was defined but never called, the 50-image cap was a promise, not an enforcement. Now invoked after every successful generation. Also sweeps stale `.tmp` files left by interrupted writes.
- **Thread safety + responsiveness (P1)**: `AiWallpaperViewModel.applyWallpaper` decoded the full-resolution PNG via `BitmapFactory.decodeFile` on the Main coroutine context (a 3 to 4 MB PNG → ~10 MB bitmap synchronously on the UI thread). Re-routed through `applyByLocator` so the disk read + decode + sampling all happen on `Dispatchers.IO`.
- **Structured concurrency (P2)**: `AiWallpaperRepository.generate` wrapped the body in `runCatching` which captures `CancellationException`. A back-navigation mid-generation was surfaced as a generic error message instead of a clean coroutine teardown. Switched to explicit `try`/`catch` with cancellation rethrow.
- **Performance (P2)**: `WeatherWallpaperService.draw` allocated a new `ColorMatrix` + `Paint` every frame at 30 FPS whenever adaptive tint was enabled (~30 allocations/sec under steady-state). Cached the `Paint` by 5-minute time bucket; only rebuilds when the bucket changes. Also short-circuits to the no-tint draw path during the neutral-midday window.
- **UX (P2)**: Settings dark/light mode wallpaper slot opened an `AlertDialog` only when wallpaper history was non-empty, a fresh install / cleared history made the slot affordance a dead click. Now opens regardless and shows a "No wallpapers applied yet" explanatory empty state with guidance.
- **UX (P3)**: Settings VFX picker confirm button was labeled "Cancel" even though each radio click already committed synchronously. Relabeled to "Close". Mirrored to the dark/light slot picker.
- **Error messages (P2)**: `AiWallpaperRepository` now maps Stability AI HTTP codes (401/402/403/422/429/5xx) to actionable user copy ("API key invalid", "Out of credits", "Content policy", "Rate limited") instead of "Generation failed (HTTP 429): {raw JSON}".
- **Maintenance**: Hoisted the per-call `\\s+` regex in `AiWallpaperRepository` to a file-level constant. Restored DRY between WallpaperApplier's HTTP and local decode paths via shared `computeSampleSize`.
- **Tests**: 30 new unit tests across `SolarCalculatorTest` (10, DST regression, polar day/night clamps, equinox day length, golden-hour tint band, intensity scaling), `AiWallpaperRepositoryFriendlyErrorTest` (10, per-status-code copy, body-append rules), `WallpaperLocatorSchemeTest` (10, http/file/content/path/unknown classification, case-insensitivity, three-part split with URLs containing pipes). Fixed pre-existing `SettingsViewModelTest` fixture gap (missing mocks for `adaptiveTintIntensity`, `darkModeWallpaperId`, `lightModeWallpaperId`, `stabilityAiKey` added in v6.13/6.14). 248/248 unit tests green.

## v6.14.0
- **AI Wallpaper Generation (Phase 3.1)**: New dedicated screen accessible via the "AI" chip in the Wallpapers header row. Enter a text prompt, pick a style (Photographic, Anime, Digital Art, Cinematic, Fantasy, Neon, Pixel Art, or None), and generate a 9:16 PNG via the Stability AI API. The result can be set as Home screen, Lock screen, or Both, and saved to Favorites. API key is entered in-screen (animated field, password-masked) and persisted in DataStore. Generated images are stored in `filesDir/ai_wallpapers/` with automatic pruning to the 50 most recent.
- **ContentSource.AI_GENERATED**: New enum value in `ContentSource`; `sourceDisplayName()` updated to return "AI Generated".
- **Version fix**: `build.gradle.kts` was still at 6.12.0/versionCode 92 despite the 6.13.0 commit. Bumped directly to 6.14.0/versionCode 94 since Phase 3.1 lands here.
- **ROADMAP cleanup**: Marked Phase 2.4 "Change your style" Settings entry and Phase 5.3 VFX Particle Overlays as done, both were already implemented in prior sessions but left unchecked.

## v6.13.0
- **Seasonal content**: `SeasonalContentManager` provides date-driven themes, Holiday (Dec), Halloween (Oct 15 to 31), New Year (Jan 1 to 3), Valentine (Feb 10 to 14), Summer (Jun 21 to Sep 1). Returns null off-season; fully injectable singleton.
- **Sounds tab seasonal carousel**: When a seasonal theme is active, a `SoundCollectionSpec` with the seasonal query and amber-gold `SEASONAL` tone is prepended to the sound collection carousel on all three tone tabs (Ringtones, Notifications, Alarms).
- **Wallpapers Discover seasonal banner**: A `SeasonalBannerCard` full-line item appears in the staggered grid between the daily pick hero and the curated collection shortcuts. Tapping it searches for the seasonal wallpaper query.
- **Style-personalized Discover feed**: `WallpaperRepository.getDiscover()` now accepts `userStyles` from the user's onboarding preferences. When styles are non-empty, an additional style-biased Wallhaven search runs alongside the toplist, widening the feed toward the user's aesthetic preferences.
- **ROADMAP reconciliation**: Marked 1.2 (Freesound v2), 1.3 (SoundCloud CC), 1.4 (Drop IA), 2.3 (QuickApplySheet), 2.6 (Sound Detail redesign) as done, all were previously implemented but left unchecked.
- **Tests**: 19 new unit tests in `SeasonalContentManagerTest` covering all season windows, boundary dates, and off-season null returns. Existing ViewModel tests updated for new constructor params.

## v6.12.0
- Round 20 audit, Wallhaven SafeSearch toggles, auto-wallpaper rotation constraints, in-session source diagnostics, NewPipe stream-leak re-verify
- **Privacy / control**: Settings → API Keys now exposes the long-orphaned `showNsfwContent` toggle as a real UI control, plus a new `showSketchyContent` toggle for Wallhaven's intermediate sketchy tier. Without an API key both opt-ins coerce back to SFW-only, Wallhaven would otherwise reject the request and leave the user with an empty grid. `computeWallhavenPurity` extracted as a pure helper with full 8-combo unit coverage
- **Battery / data hygiene**: Auto-wallpaper rotation gains three opt-in execution constraints, Charging only, Wi-Fi only (sets `NetworkType.UNMETERED`), and Device idle only. ViewModel re-schedules the WorkManager job on every toggle change so the running worker picks up new constraints without waiting for the next interval boundary. `buildAutoWallpaperConstraints` extracted as a pure helper for unit testing
- **Observability**: New `SourceMetrics` singleton tracks per-source request count, success ratio, last error, and rolling p50/p95 latency for the current session. Settings → Diagnostics surfaces a snapshot dialog with a Reset button. Initial hooks land in `WallpaperRepository.getWallhaven` and `FreesoundV2Repository.search`; pattern is documented for follow-up coverage of the remaining content sources. CancellationException intentionally excluded from failure stats (it's structured-concurrency teardown, not a source failure)
- **Maintenance**: NewPipe Extractor v0.24.8 stream lifecycle re-verified clean (no `InputStream` / `BufferedReader` without `.use { }`). Version pinned with a documenting comment in `build.gradle.kts` so future bumps trigger a re-audit
- **Tests**: 19 new unit tests (5 `WallhavenPurityTest`, 5 `AutoWallpaperConstraintsTest`, 9 `SourceMetricsTest`); 186/186 total green

## v6.11.0
- Round 19 audit, Freesound rate-limit resilience, smarter Material You accent fallback, cancellation rethrow sweep
- **Reliability**: New `RateLimitInterceptor` wraps the OkHttp client and bounds-retries Freesound v2 API on HTTP 429. Honors `Retry-After` (capped at 30 s ceiling so a pathological response can't stall the app), max 2 retries, 1.5 s default fallback when the header is missing or negative. Scoped to `freesound.org` only, Wallhaven / Reddit / Pexels / Pixabay / SoundCloud pass through unchanged. Previously a routine search past Freesound's 60 req/min limit would silently blank the Sounds tab
- **Theming**: `ColorExtractor` now exposes `bestAccentColor`, a saturation/lightness-gated fallback ladder (dominant → vibrantDark → vibrant → vibrantLight → mutedDark → muted → mutedLight → dominant). Cartoon, monochrome, or near-greyscale wallpapers no longer hand the widget a dim grey "accent" via `Palette.getDominantColor`. The widget reads the new `tint_accent` SP key with a graceful fallback to legacy `tint_vibrant_light` for palettes cached before the upgrade
- **Structured concurrency**: 5 catch sites now rethrow `CancellationException`, `WallpaperHistoryManager.record` (widget palette write + widget refresh), `WallpapersViewModel.loadRandom`, `VideoWallpapersViewModel.applyVideoWallpaper` yt-dlp branch, `AudioTrimmer.applyFadeViaFfmpeg`. Cancellation now tears down cleanly instead of being surfaced as a generic state error or a swallowed log line
- **Tests**: 16 new unit tests (7 for `RateLimitInterceptor`, 9 for `ColorAccentSelector`); 167/167 total green

## v6.10.0
- Round 18 audit, finalized writes, widget intent safety, editor download caps, startup concurrency
- **Reliability**: `SoundEditorViewModel.downloadToCache` now checks the return value of `tmpFile.renameTo(file)`. Previously a rename failure (cross-volume rename on some OEM scoped-cache dirs, stale target file, or SELinux) was silent, the editor then tried to open a file that wasn't there. Falls back to `copyRecursively` + delete before throwing
- **Intent safety**: Three remaining widget callbacks (`OpenFavoritesAction`, `OpenCurrentWallpaperAction`, `OpenAppAction`) now wrap `startActivity` in try/catch. A missing or disabled launch activity no longer crashes the widget host process
- **Structured concurrency**: `FreeVibeApp.evictStaleCaches` now rethrows `CancellationException` instead of swallowing it. This matched the already-corrected `warmCommunityIdentity` pattern; the full app-startup background block now uniformly respects cancellation
- **Bounds**: `WallpaperCropViewModel.load` and `WallpaperEditorViewModel.loadFromUrl` now cap buffered image downloads at 64 MB (Content-Length + streamed), matching `WallpaperApplier` / `DualWallpaperService` / `DownloadManager`. A hostile CDN URL can no longer OOM the crop/edit flow

## v6.9.0
- Round 17 audit, last-mile download caps
- **Bounds**: `ColorExtractor.extractFromUrl` caps buffered response at 32 MB (palette tinting only needs a 200×200 downsample; a hostile redirect to a giant image would otherwise balloon the heap just for widget tint extraction). Also hardened `calculateSampleSize` against `sample` integer overflow on pathological near-Int.MAX dimensions
- **Bounds**: `SoundApplier.saveUrlToMediaStore` caps downloads at 64 MB (matches `DownloadManager`). Previously a misresolved URL returning an endless stream could write to MediaStore until the user's storage filled

## v6.8.0
- Round 16 audit, video cropper hardening, offline-cache bounds, preferences consistency
- **Safety**: `VideoCropScreen` HTTP download for remote crop input now caps at 256 MB (Content-Length + streamed). Local file paths are validated with `File.exists() + canRead()` before handing to FFmpeg (previously surfaced as cryptic "Invalid data found" errors)
- **Resources**: `VideoCropScreen` FFmpeg process now uses a 4 KB bounded drain for its merged stdout/stderr instead of `readText()`, a chatty ffmpeg run could previously allocate MBs of String data just to log the last 500 chars
- **Structured concurrency**: `VideoCropScreen` outer and inner catch blocks now rethrow `CancellationException`
- **Bounds**: `OfflineFavoritesManager.cacheOffline` enforces an 80 MB per-file ceiling (in addition to the existing 512 MB total budget) so one hostile favorite URL can't blow the whole offline cache in a single download. Also added `CancellationException` rethrow
- **Bounds**: `SoundEditorViewModel.downloadToCache` caps audio downloads at 96 MB, the editor is for short clips, and a misresolved YouTube URL previously could fill cacheDir while the user waits
- **Consistency**: `PreferencesManager.setVideoFpsLimit` / `setVideoPlaybackSpeed` now write SharedPreferences FIRST, then DataStore. `VideoWallpaperService` (which can only read SharedPreferences because WallpaperService can't easily subscribe to DataStore) always sees the new value even if the suspending DataStore write is cancelled mid-flight. Previously the opposite order could leave the runtime service stale for the remainder of its lifetime

## v6.7.0
- Round 15 audit, deeper sweep across bitmap download paths, locale correctness, intent safety, and startup hardening
- **Safety**: `WallpaperApplier.downloadBitmap` and `DualWallpaperService.downloadBitmap` now enforce a 64 MB ceiling on the buffered byte array (Content-Length + actual size) so a hostile CDN can't OOM us during decode
- **Safety**: `DailyWallpaperWorker` notification-thumbnail download now caps at 4 MB + propagates `CancellationException` (previously swallowed, which let a cancelled worker continue allocating)
- **Reliability**: `WeatherWallpaperService.scaleBitmap` no longer leaks the intermediate `scaled` bitmap when `Bitmap.createBitmap(scaled, x, y, …)` throws, and now uses the real `scaled.width/height` consistently (previous code computed crop coordinates from a theoretical value that could diverge from the actual bitmap size, causing slightly off-center crops)
- **Startup**: `FreeVibeApp.warmCommunityIdentity` is now try/caught so a Firebase-auth failure at boot can't reach the uncaught-exception handler and crash the app (CancellationException still propagates)
- **Locale.ROOT sweep**: `AutoWallpaperWorker.normalizeWallpaperRotationSource` (Turkish locale broke source comparison), `SoundQuality` source-name titlecase, `WallpaperDetailScreen` file-type uppercase + `formatCompactCount` + `formatFileSizeLabel`, `SettingsViewModel.formatBytes`, `SharedComponents.formatBytes`, `SoundEditorScreen.formatMs` timestamp, `WallpaperCropScreen`/`VideoCropScreen` zoom-percent, `WallpaperEditorScreen` slider value. All machine-use numeric formatting now uses `Locale.ROOT` so non-English locales don't substitute commas or non-Latin digits
- **Intent safety**: `SettingsScreen.openNotificationSettings` falls back to app-details when an OEM Android build doesn't expose `ACTION_APP_NOTIFICATION_SETTINGS` (previously crashed with ANFE on some MIUI/EMUI devices). `SoundDetailScreen` + `WallpaperDetailScreen` share buttons now skip empty share URLs (was opening a blank share sheet) and wrap `startActivity` in try/catch. `ContactPickerScreen` "Open Settings" wrapped in try/catch
- **Schema resilience**: `WallhavenWallpaper.id` and `url` fields now have `""` defaults, so a malformed Wallhaven response (null id/url) yields a filterable Wallpaper with blank fields instead of a JsonDataException that kills the whole page

## v6.6.0
- Round 14 audit, reliability, safety, resource bounds, and unit-test recovery
- **Security/safety**: `DownloadManager` now enforces a 64 MB ceiling per file for both images and audio (rejects both Content-Length-advertised and streamed overruns) to prevent a malicious/broken server from filling storage
- **Reliability**: `ParallaxWallpaperService` no longer double-closes the ML Kit segmenter when a new image arrives before the previous segmentation callback fires (tracked per-segmenter with explicit nulling + synchronized guard in success/failure listeners)
- **Reliability**: `ParallaxWallpaperService.scaleBitmapCenterCrop` no longer leaks the intermediate `scaled` bitmap when `Bitmap.createBitmap(scaled, x, y, …)` throws OOM/IllegalArgument
- **Reliability**: `VideoWallpaperService` now tracks the last-played path in addition to `lastModified`, so picking a different video file that happens to share the previous file's timestamp triggers re-init instead of silently keeping the old stream
- **Resources**: `AudioTrimmer` replaced four unbounded `readText()` calls on FFmpeg's merged stdout/stderr with a bounded drain (8 KB chunks, unlimited reads but no retention), previously a chatty FFmpeg run could allocate MBs of throwaway String data
- **Structured concurrency**: Added missing `CancellationException` rethrow across 8 more catch sites, `FreeVibeWidget` (OpenCurrentWallpaper, applyFromSource, applyRandom), `WallpapersViewModel` (loadWallpapers, findSimilar), `ContactPickerViewModel` (search), `VoteRepository` (moderateHide, getTopVotedIds), `FavoritesExporter.parseJson`
- **Cleanup**: Removed unused `Canvas`/`Matrix`/`Paint`/`SurfaceTexture` imports from `VideoWallpaperService`
- **Testability**: `MainActivity.isAllowedLaunchUrl` now uses pure-JVM scheme extraction instead of `android.net.Uri.parse`, so launch-URL validation is directly unit-testable (was previously broken in local unit tests with a "Method parse in android.net.Uri not mocked" runtime failure)
- **Tests**: Fixed pre-existing `MainActivityLaunchNavigationTest.buildLaunchWallpaper preserves wallpaper metadata` failure; updated `FavoritesExporterValidationTest` to match v6.5.0's HTTPS-only policy; added a new test covering unsafe launch-URL rejection (http/file/content/javascript schemes); 151 total unit tests pass.

## v6.5.3
- Fix adaptive icon support: generate proper 108dp foreground PNGs, circular round icons, restore mipmap-anydpi-v26 XML wrappers
- Remove orphaned vector icon drawables that didn't match brand

## v6.5.2
- Restore original glowing-A beam icon from v6.1.0 across all mipmap densities

## v6.5.1
- Restore original adaptive vector app icon (reverts PNG logo changes from v6.2.0)

## v6.5.0
- Security: OOM-safe bitmap decode, HTTPS-only URL validation, SoundUrlResolver HTTP fix
- Correctness: CancellationException rethrow in 8 more catch sites
- Accessibility: IconButton touch targets to 36dp minimum (8 targets)
- Performance: remember() wrapping, regex hoisting, LaunchedEffect key fixes

## v6.4.0
- Structured concurrency audit: CancellationException sweep across 16 catch sites, 4 ViewModels

## v6.3.0
- Upload/download security hardening, UI polish pass

## v6.2.0
- Undo correctness, preview-apply stability

## v6.1.0
- Video preview, adaptive widget tint, parallax from gallery, collection sharing

## v6.0.0
- Undo, Widget preview, Bulk favorites, Preview mode, Collection rotation

## v5.26.0
- ModifierParameter lint cleanup

## v5.25.0
- UI state @Immutable, DailyWallpaperWorker backoff

## v5.24.0
- Compose stability, HTTPS enforcement, API key input sanitization

## v5.23.0
- Coil disk cache, shared OkHttp, crossfade

## v5.22.0
- Final locale sweep, remaining Regex hoisting, ProGuard verified

## v5.21.0
- Regex hoisting, dead code removal, perf

## v5.20.0
- Parallax atomicity, bitmap decode safety, widget feedback, locale

## Roadmap archive, 2026-08-10, ROADMAP.md

<details>
<summary>Original roadmap snapshot</summary>

```markdown
# Aura Roadmap

This file contains incomplete, actionable work only. Completed work lives in git and
`CHANGELOG.md`; externally blocked work belongs in `Roadmap_Blocked.md`.

## Research-Driven Additions

### P0

### P1

- [ ] P1: Test production composables instead of look-alike route fixtures
  Why: screenshot/accessibility gates can stay green while real screens regress because debug fixtures redraw simplified UIs.
  Evidence: `debug/.../AuraRouteStateFixtures.kt`; `AuraRouteStateScreenshotTest.kt`; `tools/accessibility_release_gate_check.py`; Android Compose testing guidance.
  Touches: production screen state injection, Roborazzi tests, accessibility gate, pseudo/RTL/theme fixtures.
  Acceptance: major loading/empty/error/ready/permission states render actual production composables with fake dependencies in light/dark, compact/expanded, pseudo/RTL, and 200% font cases; Compose accessibility checks run; deleting a production symbol breaks the gate; fixture-only surfaces cannot satisfy release.
  Complexity: L

### P2

- [ ] P2: Trim SettingsViewModel into feature-slice delegates (960 lines)
  Why: settings keeps growing across providers, rotation, community, and diagnostics, concentrating state/job ownership in one ViewModel.
  Evidence: `ui/screens/settings/SettingsViewModel.kt` (960 lines).
  Touches: `SettingsViewModel.kt`, feature delegates, tests and split gate.
  Acceptance: the file is under about 500 lines, behavior is unchanged, delegate job ownership is explicit, a split gate exists, and tests pass.
  Complexity: M

- [ ] P2: Extend Settings search from sections to row-level anchors
  Why: the shipped section-title/description search cannot find visible controls such as OLED theme, Wi-Fi, backup, or App Check.
  Evidence: `SettingsSearch.kt`; isolated API 35 queries “theme” and “OLED.”
  Touches: settings search index, row metadata/anchors, localized resources, navigation/highlight tests.
  Acceptance: localized row labels, descriptions, and intentional aliases match; selecting a result expands and scrolls/highlights the exact row; tests cover OLED, Wi-Fi, backup, App Check, YouTube, battery saver, and no-result behavior.
  Complexity: M

- [ ] P2: Close residual runtime localization gaps
  Why: the pseudo/RTL gate exists, but user-visible editor labels and ViewModel messages remain outside resources and outside the current scanner.
  Evidence: `WallpaperEditorScreen.kt`; `WallpaperEditorViewModel.kt`; `FavoritesViewModel.kt`; `SettingsViewModel.kt`; `tools/compose_hardcoded_string_check.py`.
  Touches: residual string resources/formatters, ViewModels/models, hardcoded-string gate, production pseudo-locale tests.
  Acceptance: identified runtime literals are resource-backed and locale-formatted, the gate scans composables plus ViewModels/models, production route tests exercise them under XA/XB, and real translations/language picker remain deferred until reviewed.
  Complexity: M

- [ ] P2: Accept user-owned shared image and audio through bounded ingestion
  Why: Aura supports JSON sharing and image “Set as,” but not normal share/edit entry into its existing image crop and Sound Editor workflows.
  Evidence: `AndroidManifest.xml`; `MainActivity.kt`; DarkModeLiveWallpaper sharing; Ringdroid open/edit flow.
  Touches: manifest filters, external-media dispatcher, `MediaIngestion`/`ShareOutbox`, image editor/crop and Sound Editor navigation/tests.
  Acceptance: user-owned/generated `ACTION_SEND`/`ACTION_EDIT` image/audio routes to a target preview; MIME is sniffed, copy is bounded, `content://` ClipData/read grants are used, cleanup is tested, and malformed/revoked inputs recover; remote items remain link-only/disabled until the blocked per-license capability model permits them.
  Complexity: M

- [ ] P2: Build an indexed multi-folder local wallpaper catalog
  Why: one rotation folder cannot represent collectors' existing libraries, tags, missing folders, or independent home/lock source sets.
  Evidence: current single-folder preferences/SAF path; Paperize, Peristyle, Muzei, and Fossify Gallery.
  Touches: persisted SAF grants, Room media index/tags, scanner/dedupe, local browse/search, rotation source picker/diagnostics.
  Acceptance: users can add/remove multiple SAF folders, rescan incrementally, tag/search/dedupe items, diagnose revoked/missing grants, and choose per-home/lock collections without broad storage permission.
  Complexity: L

### P3

- [ ] P3: Add Microsoft Spotlight as an opt-in daily-image source after terms validation
  Why: a keyless daily-image source adds low-frequency breadth without another high-volume feed.
  Evidence: WallYou source registry; existing Bing/NASA/Wikimedia daily-source plumbing.
  Touches: provider registry/client, attribution/licensing, network-endpoints manifest, source toggle UI/tests.
  Acceptance: after the P0 capability registry lands, a stable endpoint and use/attribution terms pass its policy gate; Spotlight is opt-in, preserves source URL/provenance, degrades visibly, and is recorded in the endpoint manifest. Lorem Picsum is intentionally excluded because `ProviderDisclosure.kt` forbids new default sourcing.
  Complexity: M

- [ ] P3: Optional clock/date overlay on applied/live wallpapers
  Why: Paperize issue 533 validates the niche, and Aura already has an overlay composer; it adds no background cost while off.
  Evidence: Paperize issue 533; `WallpaperEditorScreen.kt` overlay pipeline.
  Touches: editor overlay composer, live-wallpaper renderer, settings/format controls, screenshots.
  Acceptance: an opt-in overlay renders localized time/date with time-zone and 12/24-hour behavior, contrast/burn-in-safe position choices, and no background work while off; supported static/live paths have production screenshot coverage.
  Complexity: L
```

</details>
