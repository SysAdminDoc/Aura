# Contributing to Aura

Thanks for the interest. Aura is an open-source Android personalization app — wallpapers, video wallpapers, ringtones, sounds, AI art — and patches that fit the [charter](#charter) are welcome.

## Charter

Read this before opening a PR. Anything that conflicts with the charter will be closed.

- **Personalization first** — wallpapers, video wallpapers, ringtones, notifications, alarms, parallax, weather, AI art. Adjacent features are fine; tangents (note-taking, gallery management, photo editor) are not.
- **AMOLED-first** — deep blacks, true zero burn-in. Every new surface ships with a dark theme; light theme is a follow-up, never a launch blocker.
- **Free by default** — no ads, no subscriptions, no coins, no streaks, no surprise charges. Donations via GitHub Sponsors / Liberapay are the only acceptable monetization vector.
- **Multi-source aggregation** — Wallhaven, Pexels, Pixabay, Reddit, Bing, YouTube, Firebase-backed community uploads. New sources land behind a `WallpaperRepository` or `SoundRepository` interface; no source-specific code in ViewModels.
- **Polite live wallpapers** — pause on invisible, cap FPS on low battery, honor user-toggled effects. Battery dashboard is the live spec.
- **No tracking, no account required** — all preferences in `DataStore` (local). Firebase voting + uploads use anonymous identity. Optional Google sign-in is opt-in only.

If your PR contradicts the charter and you think the charter is wrong, open an issue first to discuss.

## Build

Requires JDK 21 and Android SDK 36. Android Studio Ladybug (2024.2.1) or later.
Use Adoptium JDK 21 specifically, not "17 or newer": the app compiles to Java 17
bytecode, but Gradle 8.12.1 refuses newer JDKs, and the JBR bundled with current
Android Studio is JDK 25.

```bash
./gradlew assembleFullDebug          # use gradlew.bat on Windows
./gradlew testFullDebugUnitTest      # unit tests
./gradlew verifyRoborazziFullDebug   # screenshot goldens
./gradlew lintFullDebug
./gradlew assembleFullRelease        # requires signing config in local.properties
python -m pytest test/tools          # release-policy gates
```

The app builds in two flavors, `full` and `foss`, so unqualified task names like
`testDebugUnitTest` are ambiguous and Gradle will refuse them. Always name the
flavor.

Run build, unit-test, lint, signing, checksum, and release-artifact checks locally before pushing or publishing. Public install artifacts are signed APK/AAB outputs produced on this machine and attached to GitHub Releases with local receipts.

Gradle wrapper is pinned to 8.12. AGP 8.9.3. Kotlin 2.1.0. JDK 21 to run the build, Java 17 as the compile target. The app compiles against SDK 36 but still targets 35, so none of the Android 16 behavior changes apply. See [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties) and [`app/build.gradle.kts`](app/build.gradle.kts).

`local.properties` example:

```
sdk.dir=/Users/you/Library/Android/sdk
pexels.api.key=    # optional — Aura ships a default
pixabay.api.key=   # optional
freesound.api.key= # optional
soundcloud.client.id= # optional
stability.ai.key= # optional — user-supplied for AI wallpaper generation
signing.keystore.path=../freevibe.jks  # release builds only
signing.keystore.password=
signing.key.alias=freevibe
signing.key.password=
```

## Architecture

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the layered overview, package map, and contribution patterns.

## Branches & PRs

- `main` is the only long-lived branch. Branch off, name `feat/<short-slug>` or `fix/<short-slug>`, open a PR.
- Squash on merge unless the history is meaningful (multiple atomic refactors).
- Each PR description must include:
  - **What changed and why** (one paragraph)
  - **Roadmap reference** if applicable — `[ROADMAP.md](ROADMAP.md)` item ID (e.g. `NX-3`, `L-2`)
  - **Screenshots** for UI changes — capture at 125 % DPI on a 1080×2400 viewport
  - **Test plan** — what you ran, what you checked

## Code style

- **Kotlin idiomatic** — `val` over `var`, `?.let` over null checks, `runCatching` over try/catch when the failure mode is uniform, structured concurrency (always rethrow `CancellationException`).
- **Compose** — `collectAsStateWithLifecycle()` over `collectAsState`. `rememberSaveable` for gesture / scroll state. `derivedStateOf` for computed UI values.
- **Hilt** — every singleton-scoped service in `service/` and every repository in `data/repository/` gets `@Singleton`-annotated. ViewModels get `@HiltViewModel`. No manual `Component`s.
- **No `runBlocking` from UI handlers** — every entry point that reads `DataStore` is suspend or uses `viewModelScope.launch`. The one historical exception (`AutoWallpaperWorker.schedule`) became suspend in v6.12.0; do not re-introduce the pattern.
- **Locale.ROOT** — every machine-use `lowercase()` / `uppercase()` / `format()` call uses `Locale.ROOT`. Turkish locale dotless-i has cost us bugs before.
- **No pill / oval / fully-rounded backdrops in GUI work** — radii are `0`, `4`, `6`, `8`, `10`, or `12 dp`. Status badges differentiate via colour / border / font weight, not stadium shape. See repo CLAUDE.md for the design rule.
- **Image bounds** — every HTTP image fetch goes through a size cap. See `WallpaperApplier.MAX_WALLPAPER_BYTES` (64 MB), `ColorExtractor.MAX_EXTRACT_BYTES` (32 MB), `OfflineFavoritesManager.MAX_PER_FILE_BYTES` (80 MB). Use `readCapped` / `copyCapped` helpers when adding a new write path.
- **Recycle defensively** — `Bitmap.createBitmap` can return the same object; check `!==` before recycling.

## Tests

- Unit tests live in `app/src/test/java/com/freevibe/` and run via `testFullDebugUnitTest`. Pure-Kotlin code (geometry helpers, content filters, query builders) should land with tests; ViewModels can rely on integration coverage if mocking is heavy.
- Instrumented tests live in `app/src/androidTest/java/com/freevibe/` and run via `connectedFullDebugAndroidTest`. Required for any flow that touches MediaStore, Room migrations, or FFmpeg subprocess wiring.
- Screenshot tests use Roborazzi and run on the JVM: `verifyRoborazziFullDebug` checks the committed goldens in `app/src/test/screenshots/`, and `recordRoborazziFullDebug` rewrites them. Re-record only after looking at the generated PNGs — an unreviewed re-record turns a regression into the new baseline.
- Release policy is enforced by Python gates in `tools/`, each mirrored by a test in `test/tools/`. Run `python -m pytest test/tools` before pushing; a gate that changes behavior needs its mirror test updated in the same commit.
- Bumping the version touches more than `app/build.gradle.kts`. `docs/distribution/release-metadata-consistency.json` carries its own `versionName`/`versionCode` pair, and `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` has to exist, name the versionName, and contain the literal `Recent highlights:`. Run the gate suite after any bump; several gates pin the literal version and fail by design until every file agrees.

## Commits

- One commit per logical change. No `wip:` or `chore: bump` commits — squash them locally before opening the PR.
- Conventional-commits-ish format: `feat(NX-3): smart crop with subject segmentation` / `fix(scheduler): rethrow CancellationException in worker schedule`.
- **No AI-attribution metadata in commit messages, code comments, README, CHANGELOG, ROADMAP, or any other tracked file.** `CLAUDE.md`, `CODEX_CHANGELOG.md`, and `.claude/` are gitignored.

## Roadmap

[`ROADMAP.md`](ROADMAP.md) is the source of truth for what's queued, and it holds only work that is still outstanding. Finished items are deleted from it; the record of what shipped lives in [`CHANGELOG.md`](CHANGELOG.md) and the git history.

Items are grouped by priority, **P0** (most urgent) through **P3**, and each one is written in the same shape:

```
- [ ] P2 — Short title in the imperative
  Why: the one-line reason this is worth doing
  Evidence: file paths, a competitor, a standard — whatever makes the case checkable
  Touches: the files or modules a change would land in
  Acceptance: what has to be observably true before it can be deleted from the roadmap
  Complexity: S/M/L/XL
```

When you open an issue about queued work, quote the item's title — the roadmap has no ID scheme. New items need the same six fields, and the Evidence line is the one that decides whether an item survives review: a claim nobody can check does not become a roadmap item.

Work that cannot proceed without something outside the repository — Firebase Console access, a physical device, an owner decision — lives in [`Roadmap_Blocked.md`](Roadmap_Blocked.md) with its blocker named, not in `ROADMAP.md`.

## Plugins (Aura Sources)

A Muzei-compatible `MuzeiArtProvider` IPC contract would let third-party sources plug into Aura without forking. It is not built, and it is deliberately parked: the ownership, security, and compatibility questions have to be settled before an ABI is published, because an ABI is much harder to change once other apps depend on it. The entry is tracked in [`Roadmap_Blocked.md`](Roadmap_Blocked.md). There is no plugin documentation to read yet, and there won't be until that decision is made.

## Code of conduct

Be polite. Issue tracker is for bugs and feature requests; flames go to `/dev/null`. Maintainers reserve the right to lock disrespectful threads.

## License

MIT — see [`LICENSE`](LICENSE). Contributions are licensed under the same terms.
