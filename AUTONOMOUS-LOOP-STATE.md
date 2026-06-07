# Autonomous Loop State

**Assigned project:** `C:\Users\--\repos\Aura`
**Current pass:** 2026-06-07 Cycle 142 network Data safety surface gate
**Last commit before pass:** `a5c5deb` (`ci(privacy): gate data safety permissions`)

## 2026-06-05 Result

- Shipped a partial provider/content-source compliance slice: central `ProviderDisclosure` model, Licenses screen integration, legal provider policy matrix doc, and unit coverage for complete `ContentSource` disclosure rows.
- Expanded visible Licenses screen rows for missing runtime/native dependencies as a stopgap before generated OSS notices.
- Verified on a local mirror with `.\gradlew.bat --no-daemon --max-workers=2 :app:testDebugUnitTest --tests com.freevibe.data.legal.ProviderDisclosureTest` after installing Android SDK command-line tools, Android 35 platform, build-tools 35.0.0, and platform-tools.

## 2026-06-06 Result

- Completed Cycle 18 roadmap/research pass for generated release-runtime OSS notices, license drift gates, SBOM/release artifacts, and native/copyleft packet planning.
- Added `docs/research/cycle-18-2026-06-06.md` with local evidence and verified primary-source research.
- Completed Cycle 19 roadmap/research pass for current-toolchain notice tooling sequence and AboutLibraries version gating.
- Added `docs/research/cycle-19-2026-06-06.md` with local Gradle/workflow inspection and primary-source plugin research.
- Completed Cycle 20 isolated compatibility spikes for Google OSS notices and AboutLibraries 14.2.1 under `work/`.
- Added `docs/research/cycle-20-2026-06-06.md` with exact commands, generated artifact paths, and spike results.
- Completed Cycle 21 plugin-only Google OSS notice generation and `THIRD-PARTY-NOTICES.md` markdown conversion spike.
- Added `docs/research/cycle-21-2026-06-06.md` with converter details, plugin-only generation results, Google runtime dependency convergence risk, and ProviderDisclosure preservation notes.
- Completed Cycle 22 real-repo plugin-only Google OSS notice implementation, markdown converter, release artifact wiring, and focused verification.
- Added `docs/research/cycle-22-2026-06-06.md` with implementation files, verification commands, dependency graph check results, and remaining gaps.
- Updated `ROADMAP.md` with Cycle 18, Cycle 19, Cycle 20, Cycle 21, and Cycle 22 P0/P1 handoff items, Appendix V/W/X/Y/Z sources, and a `Continuation State` section.
- Implemented Google OSS licenses Gradle plugin first without `play-services-oss-licenses:17.5.1`; generated notices with `:app:releaseOssLicensesTask`; added `tools/google_oss_to_markdown.py`; wired `THIRD-PARTY-NOTICES.md` into release artifacts/checksums.
- Verification: real-repo `:app:releaseOssLicensesTask` passed after refreshing POM checksum metadata; converter generated 251 dependency records and 288 notice sections; release runtime graph did not show `play-services-oss-licenses`, Activity Compose 1.12.1, Compose 1.11.0-beta02, or Material Components 1.13.0.
- ProviderDisclosure focused unit test now passes with `JAVA_HOME` set to Android Studio JBR and `ANDROID_HOME` set to the local Android SDK.
- Decision: keep the stock Google runtime/activity behind a dependency convergence audit; next compliance gap is native/copyleft payload inspection.
- Did not run full Gradle APK/lint builds; Cycle 22 used focused Gradle notice/dependency tasks because `CLAUDE.md` warns repeated APK/lint runs can exhaust this workstation.
- Recent history was checked with `rtk git log -10 --oneline --decorate` for this pass.
- Completed Cycle 23 native/copyleft payload inspection.
- Added `tools/native_compliance_inventory.py` to inspect resolved Gradle cache artifacts and optional final APK payloads.
- Generated `docs/legal/native-compliance.md` with youtubedl-android common/library/ffmpeg hashes, NewPipeExtractor hashes, yt-dlp 2025.11.12 facts, python3.12 payload facts, QuickJS references, FFmpeg ABI payload entries, and release review notes.
- Wired release `NATIVE-COMPLIANCE.md` generation into `.github/workflows/release.yml`, including checksums, release notes, workflow artifact upload, and tagged release attachment.
- Updated `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the native packet.
- Cycle 23 verification: `python -m py_compile tools\native_compliance_inventory.py`; `python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md`.
- Completed Cycle 24 dependency notice drift gating.
- Added `tools/dependency_notice_lock.py` to write/check deterministic Google OSS release notice lockfiles.
- Committed `docs/legal/dependency-notices.lock.json` with 251 release dependency records and 288 notice section hashes.
- Wired `.github/workflows/verify.yml` and `.github/workflows/release.yml` to run `:app:releaseOssLicensesTask` and check `docs/legal/dependency-notices.lock.json`.
- Updated `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the lockfile gate.
- Cycle 24 verification: `:app:releaseOssLicensesTask` passed; `python -m py_compile tools\dependency_notice_lock.py tools\google_oss_to_markdown.py tools\native_compliance_inventory.py`; `python tools\dependency_notice_lock.py --mode check --lockfile docs\legal\dependency-notices.lock.json` returned status `ok`.
- Completed Cycle 25 native compliance freshness gating.
- Extended `tools/native_compliance_inventory.py` with `write-lock` and `check-lock` modes.
- Committed `docs/legal/native-compliance.lock.json` with 8 native/copyleft coordinates, 23 artifact records, and 36 payload entries.
- Wired `.github/workflows/verify.yml` and `.github/workflows/release.yml` to check `docs/legal/native-compliance.lock.json`.
- Updated `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for native lockfile review.
- Cycle 25 verification: `python -m py_compile tools\dependency_notice_lock.py tools\google_oss_to_markdown.py tools\native_compliance_inventory.py`; `python tools\dependency_notice_lock.py --mode check --lockfile docs\legal\dependency-notices.lock.json`; `python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json`; `python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md`.
- Completed Cycle 26 curated high-risk dependency overlay.
- Added `docs/legal/dependency-notice-overrides.json` with source URLs, license IDs, usage descriptions, targets, and release-review notes for Firebase, Google Play services, ML Kit subject segmentation, NewPipeExtractor, youtubedl-android, yt-dlp, Python, QuickJS, FFmpeg, ProfileInstaller, and ZXing.
- Added `tools/dependency_overlay_check.py` to fail missing required entries, duplicate IDs, missing fields, non-HTTPS source URLs, unsupported target types, and stale/orphaned targets against the dependency/native locks.
- Wired `.github/workflows/verify.yml` and `.github/workflows/release.yml` to run the overlay check after generated notice/native lock checks.
- Updated `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the overlay gate.
- Cycle 26 verification: `python -m py_compile tools\dependency_notice_lock.py tools\dependency_overlay_check.py tools\google_oss_to_markdown.py tools\native_compliance_inventory.py`; `python tools\dependency_notice_lock.py --mode check --lockfile docs\legal\dependency-notices.lock.json`; `python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json`; `python tools\dependency_overlay_check.py --overlay docs\legal\dependency-notice-overrides.json`.
- Completed Cycle 27 release compliance artifact dry-run validation.
- Added `tools/release_artifact_bundle_check.py` to validate required release files, APK naming, checksum entries, checksum digest matches, release-note evidence, signing digest output, and non-debuggable `aapt` evidence.
- Wired `.github/workflows/release.yml` to run the bundle validator after release notes are generated and before workflow artifact upload or tag-release publication.
- Added `docs/distribution/release-dry-run.md` with GitHub UI, GitHub CLI, workflow artifact, and local smoke-test instructions.
- Updated `docs/distribution/release-signing.md`, `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for release dry-run validation.
- Cycle 27 verification: `python -m py_compile tools\release_artifact_bundle_check.py`; local temporary bundle smoke test; `python -m py_compile tools\dependency_notice_lock.py tools\dependency_overlay_check.py tools\google_oss_to_markdown.py tools\native_compliance_inventory.py tools\release_artifact_bundle_check.py`; dependency notice lock check; native compliance lock check; dependency overlay check.
- Completed Cycle 28 raw release notice input preservation.
- Added `tools/google_oss_raw_archive.py` to create deterministic `GOOGLE-OSS-RAW-INPUTS.zip` archives containing `dependencies.json`, `third_party_license_metadata`, `third_party_licenses`, and `MANIFEST.json`.
- Wired `.github/workflows/release.yml` to generate the raw input archive after `THIRD-PARTY-NOTICES.md`, include it in `SHA256SUMS.txt`, mention it in release notes, upload it as a workflow artifact, and attach it to tagged GitHub Releases.
- Updated `tools/release_artifact_bundle_check.py` to require the raw input archive in release files, checksums, and release notes.
- Updated `docs/distribution/release-dry-run.md`, `docs/distribution/release-signing.md`, `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for raw notice input preservation.
- Cycle 28 verification: `python -m py_compile tools\google_oss_raw_archive.py`; local generated-root archive smoke test; local release bundle smoke test; release-compliance Python compile and lock checks; dependency overlay check.
- Completed Cycle 29 user-facing dependency notice access.
- Added a `Release Notices` section to `LicensesScreen.kt` with cards for `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, and `NATIVE-COMPLIANCE.md` via the latest GitHub Release.
- Updated the Settings licenses subtitle to mention generated notices.
- Added `LicensesScreenTest` coverage for the release artifact link names, URLs, labels, and artifact descriptions.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for generated notice access.
- Cycle 29 verification: focused `:app:testDebugUnitTest --tests com.freevibe.ui.screens.licenses.LicensesScreenTest` passed; release-compliance Python compile and lock checks; dependency overlay check.
- Completed Cycle 30 FFmpeg source-correspondence evidence.
- Added `docs/legal/ffmpeg-source-correspondence.md` with the resolved youtubedl-android FFmpeg AAR hash, nested `libffmpeg.zip.so` hashes, embedded FFmpeg 7.1.1 configure lines for all four ABIs, FFmpeg 7.1.1 source candidate, and release-owner checklist.
- Extended `tools/native_compliance_inventory.py` to extract embedded FFmpeg version, configure line, configure SHA-256, and license-mode facts from nested FFmpeg shared-library payloads.
- Regenerated `docs/legal/native-compliance.md` and `docs/legal/native-compliance.lock.json` so FFmpeg configure evidence is part of the native drift gate.
- Updated `docs/legal/dependency-notice-overrides.json`, `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for FFmpeg source-correspondence review.
- Cycle 30 verification: release-compliance Python compile checks; dependency notice lock check; native compliance lock check; native markdown regeneration; dependency overlay check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 31 release dependency license policy gate.
- Added `docs/legal/dependency-license-policy.json` with allowed, review-required, disallowed, required overlay, required coordinate-prefix, and required native-payload policy.
- Added `tools/dependency_license_policy.py` to fail unknown, disallowed, or unreviewed curated license IDs and to verify required high-risk dependency/native-payload coverage against the current locks.
- Wired `.github/workflows/verify.yml` and `.github/workflows/release.yml` to run the policy check after the generated notice, native compliance, and overlay checks.
- Updated `docs/distribution/supply-chain.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the policy gate.
- Cycle 31 verification: release-compliance Python compile checks; dependency notice lock check; native compliance lock check; dependency overlay check; dependency license policy check; sentinel disallowed-license failure check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 32 raw Google OSS archive retention policy.
- Added `docs/distribution/raw-oss-input-retention.md` to state that `GOOGLE-OSS-RAW-INPUTS.zip` stays attached to every tagged public release that publishes generated notices.
- Clarified `docs/distribution/supply-chain.md`, `docs/distribution/release-dry-run.md`, and `docs/distribution/release-signing.md` so dry-run artifacts are retention-bound review evidence while tagged release assets are the public retention surface.
- Kept `.github/workflows/release.yml` behavior aligned with the decision and named the raw archive constant in `tools/release_artifact_bundle_check.py`.
- Updated `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the retention decision.
- Cycle 32 verification: release-compliance Python compile checks; dependency notice lock check; native compliance lock check; dependency overlay check; dependency license policy check; release bundle smoke test with raw archive; negative release bundle smoke test without raw archive; `git diff --check`; changed-line attribution scan.
- Completed Cycle 33 custom in-app generated dependency notice viewer.
- Added `app/src/main/java/com/freevibe/ui/screens/licenses/GeneratedDependencyNotices.kt` to parse generated Google OSS `third_party_license_metadata` offsets and `third_party_licenses` bytes into in-app notice rows.
- Updated `LicensesScreen.kt` to load generated raw resources, show generated dependency notices before manual library rows, and open full generated notice text in a selectable dialog.
- Extended `LicensesScreenTest` with parser coverage for metadata ranges, license label summaries, and invalid-range skipping.
- Cycle 33 verification: focused `:app:testDebugUnitTest --tests com.freevibe.ui.screens.licenses.LicensesScreenTest` passed with Android Studio JBR; release-compliance Python compile checks; dependency notice lock check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 34 generated notice search and high-risk alignment.
- Extended `GeneratedDependencyNotices.kt` with filtering and review markers for generated notice rows that match curated overlay surfaces.
- Updated `LicensesScreen.kt` with a generated-notice filter field, review watchlist section, all-notices section, and no-match state.
- Extended `LicensesScreenTest` with filter and review-marker coverage.
- Cycle 34 verification: focused `:app:testDebugUnitTest --tests com.freevibe.ui.screens.licenses.LicensesScreenTest` passed with Android Studio JBR; release-compliance Python compile checks; dependency notice lock check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 35 generated notice metadata parity guard.
- Added `tools/dependency_notice_lock.py --mode check-metadata` to validate raw Google OSS metadata-derived notice rows against `docs/legal/dependency-notices.lock.json`.
- Wired `.github/workflows/verify.yml` and `.github/workflows/release.yml` to run the metadata parity check after the full generated notice lock check.
- Updated `docs/distribution/supply-chain.md`, `docs/research/cycle-35-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the metadata parity gate.
- Cycle 35 verification: release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; malformed-range and missing-row negative fixtures; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 36 runtime provider kill-switch behavior matrix.
- Added code-backed `ProviderRuntimeControl` rows for every `ContentSource`, with current control, disabled behavior, and follow-up status.
- Extended `ProviderDisclosureTest` so runtime controls cover every source exactly once and partial/missing controls must carry concrete follow-ups.
- Added `docs/legal/provider-runtime-controls.md`, linked it from `docs/legal/provider-policy.md`, and recorded YouTube as the highest-risk missing provider switch.
- Cycle 36 verification: focused `:app:testDebugUnitTest --tests com.freevibe.data.legal.ProviderDisclosureTest` passed with Android Studio JBR; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 37 YouTube provider legal-mode switch.
- Added a default-on `youtube_provider_enabled` preference and Settings switch for YouTube features.
- Gated `YouTubeRepository` before search, cached preview lookup, audio stream resolution, and video stream resolution.
- Updated Sounds so disabled YouTube mode hides the YouTube secondary tab, blocks direct YouTube search/import/playback/download/similar paths, skips top-hit provider calls, and falls back to bundled ringtone/notification/alarm content.
- Updated Video Wallpapers so disabled YouTube mode skips discovery, pre-resolve work, and apply/download paths.
- Extended `SourceMetrics` with disabled-source counters and surfaced disabled counts in Settings diagnostics and crash context.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-37-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the covered YouTube runtime control.
- Cycle 37 verification: focused Sounds, sound-tab helper, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 38 Reddit provider source switch.
- Added a default-on `reddit_provider_enabled` preference and Settings switch for Reddit features.
- Gated `RedditRepository` before subreddit fetches, subreddit search, multi-subreddit aggregation, and daily-pick lookup.
- Updated Wallpapers so disabled Reddit mode clears daily picks, redirects the Reddit tab to Discover, hides the Reddit tab, and avoids Reddit loads during refresh/filter transitions.
- Updated Video Wallpapers, Daily Wallpaper, and Auto Wallpaper workers to skip Reddit fetch paths when the source is disabled.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-38-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the covered Reddit runtime control.
- Cycle 38 verification: focused Wallpapers, Settings, video wallpaper helper, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 39 Pexels and Pixabay source switches.
- Added default-on `pexels_provider_enabled` and `pixabay_provider_enabled` preferences plus Settings switches.
- Gated `WallpaperRepository` Pexels/Pixabay photo calls before key reads, covering Discover, search, style-biased Discover, and source-tab loads.
- Updated Wallpapers so disabled Pexels/Pixabay tabs hide and stale selections redirect to Discover.
- Updated Video Wallpapers to skip disabled Pexels/Pixabay video jobs before key reads.
- Updated auto-rotation source pickers and workers so disabled Pixabay is not offered for new rotation choices and does not retry indefinitely when already selected.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-39-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the covered Pexels switch and partial Pixabay runtime/policy state.
- Cycle 39 verification: focused Wallpapers, Settings, video wallpaper helper, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 40 Community source switch.
- Added a default-on `community_provider_enabled` preference and Settings switch for Community features.
- Gated startup community identity warm-up, sound uploads, community sound feeds, wallpaper uploads, community wallpaper feeds, creator dashboard, and follow/unfollow repository calls before Firebase-backed work.
- Updated Sounds so disabled Community mode hides the Community secondary tab, blocks record/upload/vote actions, clears stale community loads, and redirects to bundled ringtone content.
- Updated Wallpapers and wallpaper detail views so disabled Community mode hides uploads, vote controls, community source selection, and stale community requests while returning empty disabled-source data.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-40-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the covered Community runtime control.
- Cycle 40 verification: focused Sounds, Wallpapers, Settings, source metrics, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 41 Bing Daily source switch.
- Added a default-on `bing_provider_enabled` preference and Settings switch for Bing Daily.
- Gated `WallpaperRepository.getBingDaily()` before cache fallback or Retrofit calls when disabled, returning an empty source result and recording disabled diagnostics.
- Updated legacy and scheduled auto-wallpaper workers so disabled Bing rotation sources exit successfully instead of retrying.
- Hid Bing Daily from auto-wallpaper source pickers when disabled while preserving stale selected values.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-41-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the covered Bing Daily runtime control.
- Cycle 41 verification: focused WallpaperRepository, Settings, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 42 Wallhaven source switch.
- Added a default-on `wallhaven_provider_enabled` preference and Settings switch for Wallhaven.
- Gated Wallhaven featured/search/similar/random/color repository paths before key reads, cache fallback, or Retrofit calls when disabled, returning empty source results and recording disabled diagnostics.
- Updated Wallpapers so disabled Wallhaven mode hides the Wallhaven tab, blocks color/random/similar actions, filters Wallhaven cached Discover previews, and hides Wallhaven-only Discover action buttons.
- Updated legacy and scheduled auto-wallpaper workers so disabled Wallhaven rotation sources exit successfully instead of retrying.
- Hid Wallhaven from auto-wallpaper source pickers when disabled while preserving stale selected values.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-42-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the covered Wallhaven runtime control.
- Cycle 42 verification: focused WallpaperRepository, Wallpapers, Settings, and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 43 Pixabay photo request-cache and backoff.
- Added `WallpaperCacheManager.TTL_PIXABAY` as a 24-hour fresh-cache TTL for Pixabay photo metadata.
- Updated `WallpaperRepository.getPixabay()` to serve fresh cached photo results before API calls, cache successful non-empty responses, and use stale cache or empty results during active rate-limit backoff.
- Added 429 backoff parsing for `Retry-After` and `X-RateLimit-Reset` headers.
- At the Cycle 43 boundary, Pixabay video metadata remained the next policy slice because `VideoWallpapersViewModel` called it directly without a durable 24-hour cache/backoff guard.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-43-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the Pixabay photo policy slice.
- Cycle 43 verification: focused WallpaperRepository and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 44 Pixabay video request-cache and backoff.
- Added persistent app-private 24-hour cache helpers for Pixabay video metadata and resolved stream URLs.
- Updated `VideoWallpapersViewModel` to serve fresh cached Pixabay video metadata before API calls, use stale cache during active backoff, and mark Pixabay degraded instead of retrying when no stale cache exists.
- Persisted Pixabay video 429 backoff from `Retry-After` or `X-RateLimit-Reset` headers.
- Marked Pixabay runtime controls covered because photo and video metadata paths now both enforce provider cache/backoff behavior.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-44-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the Pixabay video policy slice.
- Cycle 44 verification: focused VideoWallpapersViewModel and provider disclosure unit tests; release-compliance Python compile checks; dependency notice lock check; generated notice metadata parity check; native compliance lock check; dependency overlay check; dependency license policy check; `git diff --check`; changed-line attribution scan.
- Completed Cycle 45 generated-content source switch.
- Added a default-on generated-wallpapers preference and Settings switch.
- Hid the Wallpapers Generate chip when generated wallpapers are disabled.
- Updated the AI wallpaper screen and ViewModel so disabled mode hides the key surface, disables the request button, and blocks generation before prompt/key validation or Stability repository calls.
- Marked AI-generated runtime controls covered while keeping saved local generated wallpapers visible.
- Updated `ProviderDisclosure.kt`, `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-45-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the generated-content source switch.
- Completed Cycle 46 saved-source availability states.
- Added `sourceAvailability` and `sourceAvailabilityReason` metadata to favorites and download history with a v14-to-v15 Room migration and schema snapshot.
- Added repository/ViewModel hooks to mark or clear unavailable source state for saved favorites/download records.
- Updated favorite/domain mapping and favorite export/import so unavailable source state survives backups and restored wallpaper favorites prefer their offline path when available.
- Updated new download records to preserve provider source names instead of collapsing source to `WALLPAPER` or `SOUND`.
- Surfaced "Source unavailable" in Favorites, Downloads, wallpaper detail, and sound detail; unavailable saved items no longer show live-source link/similar/share affordances as normal provider content.
- Updated `docs/research/cycle-46-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the saved-source availability slice.
- Completed Cycle 47 Pexels enhancement guardrails.
- Added `keepPexelsAsDiscoverEnhancement()` so wallpaper Discover drops Pexels-only result batches unless a non-Pexels base source returned visible inventory.
- Added `keepPexelsVideosAsEnhancement()` so video-wallpaper discovery applies the same enhancement-only rule before orientation filtering and ranking.
- Added focused tests proving disabled-Pexels Discover returns Wallhaven/Pixabay fallback inventory without calling Pexels APIs, Pexels photo rows keep photographer/source-page context, and Pexels video rows only remain in mixed-source batches.
- Updated `docs/legal/provider-runtime-controls.md`, `docs/research/cycle-47-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the Pexels enhancement guardrail.
- Completed Cycle 48 provider removal failure reconciliation.
- Added `SourceAvailabilityPolicy.kt` with a shared classifier for explicit 404/410/gone/removed/deleted provider failures.
- Updated wallpaper and sound apply/download paths to mark saved favorites `SOURCE_UNAVAILABLE` with provider-specific reasons when remote removal is proven.
- Updated `DownloadManager` to mark matching download-history rows unavailable when re-downloads fail with explicit removed/gone statuses.
- Added focused tests for the classifier plus wallpaper/sound download reconciliation.
- Updated `docs/research/cycle-48-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the reconciliation slice.
- Completed Cycle 49 sound license capability gates.
- Added `SoundLicensePolicy.kt` with normalized sound action capabilities for apply, download, share, edit/trim, and Aura Originals use.
- Added Room v16 favorite-license persistence plus favorite export/import preservation so saved sounds keep license metadata.
- Updated `SoundsViewModel`, `SoundDetailScreen`, quick apply, and contact assignment so restricted actions are disabled or require confirmation before stream resolution/download/apply work starts.
- Updated sound share text to include name, creator, source, normalized license, and source link.
- Added focused tests for the license matrix, favorite license round trips, export/import preservation, and unconfirmed YouTube download blocking.
- Updated `docs/legal/sound-license-capabilities.md`, `docs/research/cycle-49-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the sound license capability slice.
- Completed Cycle 50 community upload rights attestation and selected license metadata.
- Added `CommunityUploadRights.kt` with selected-license normalization, rights attestation validation, and optional HTTPS source URL validation.
- Updated sound and wallpaper upload dialogs to require a selected CC0/CC BY/CC BY-NC license and rights confirmation before upload.
- Updated upload repositories to validate rights metadata before media upload and store uploader UID, license, attestation, attestation timestamp, and source URL metadata.
- Updated community sound policy so selected community licenses use normal license-specific action gates while legacy rows keep the `User Upload` fallback.
- Added wallpaper license preservation and detail display for community wallpaper provenance.
- Hardened RTDB rules so new community sound/wallpaper records require license, attestation, uploader UID, timestamp, and blank-or-HTTPS source URL fields.
- Updated `docs/legal/community-upload-rights.md`, `docs/legal/sound-license-capabilities.md`, `docs/research/cycle-50-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the community rights slice.
- Completed Cycle 51 community report queue intake and admin resolution metadata.
- Added `CommunityReport.kt` with report reasons, resolution statuses, payload normalization, source URL validation, and resolution validation.
- Added `CommunityReportRepository.kt` with Firebase report creation and admin resolution writes.
- Added `/community_reports` and `/community_report_resolutions` RTDB rules for authenticated report creation plus admin-only read/update/resolution.
- Added `CommunityReportDialog` and sound/wallpaper detail report actions.
- Updated `SoundsViewModel` and `WallpapersViewModel` to submit current content source, license, uploader, and HTTPS source URL context with reports.
- Updated `docs/support/community-reporting.md`, `docs/research/cycle-51-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the report intake slice.
- Completed Cycle 52 admin report review and report-to-moderation wiring.
- Added `CommunityReportsScreen.kt` with admin gating, open report cards, and Hide/Dismiss/Restore actions.
- Extended `CommunityReportRepository.kt` with a status-filtered open-report Flow and `CommunityReport.kt` with record parsing helpers.
- Wired admin Settings navigation to the `community_reports` route.
- Added `/community_reports` status indexing for the open-report query.
- Added focused tests for admin hide, dismiss, restore, non-admin feed gating, and Settings admin access.
- Updated `docs/support/community-reporting.md`, `docs/research/cycle-52-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the admin report review slice.
- Completed Cycle 53 Firebase App Check client rollout.
- Added debug and release `AppCheckInstaller` source-set implementations.
- Added Firebase App Check Play Integrity and debug provider dependencies under the existing Firebase BoM.
- Installed App Check in `FreeVibeApp.onCreate()` before Firebase-backed community startup work.
- Added `docs/community-app-check-rollout.md` for debug-token registration, Play Integrity console setup, side-loaded distribution settings, metrics burn-in, and enforcement gates.
- Refreshed Gradle dependency verification metadata and generated dependency notice locks for App Check, Play Integrity, and Play Core artifacts.
- Updated `docs/research/cycle-53-2026-06-06.md`, `docs/support/community-reporting.md`, `docs/legal/provider-runtime-controls.md`, `docs/distribution/channel-strategy.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the client App Check slice.
- Completed Cycle 54 community quota/rate-limit design.
- Added `CommunityQuotaPolicy.kt` with typed policy rows for reports, sound uploads, wallpaper uploads, votes, follows, and profile edits.
- Added `CommunityQuotaPolicyTest.kt` to keep required surfaces, ledger paths, cooldowns, dedupe keys, and App-Checked callable enforcement covered.
- Reserved `/community_write_quotas` and `/community_write_dedupe` as admin-only RTDB namespaces so regular clients cannot spoof backend quota counters.
- Added `docs/community-quota-rate-limits.md` with the policy matrix, protected ledger schema, App-Checked callable rollout order, and verification plan.
- Updated `docs/research/cycle-54-2026-06-06.md`, `docs/community-app-check-rollout.md`, `docs/support/community-reporting.md`, `docs/firebase-admin-claims.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the quota design slice.
- Completed Cycle 55 community upload deletion handles.
- Added `CommunityUploadOwnership.kt` for sound/wallpaper owner-index payloads, private index paths, and multi-location delete updates.
- Added `CommunityUploadDeletionHelpers.kt` so repositories can delete Firebase Storage paths and treat already-missing objects as removable.
- Updated sound and wallpaper uploads to store `storagePath` and write `/owner_uploads/{uid}/sounds|wallpapers/{uploadId}` rows alongside public metadata.
- Added `deleteSoundUpload()` and `deleteWallpaperUpload()` owner methods for new rows with deletion handles.
- Updated `database.rules.json` with `storagePath` validation and owner/admin-only owner-index access.
- Updated `docs/community-upload-deletion.md`, `docs/research/cycle-55-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the deletion-handle slice.
- Completed Cycle 56 visible community owner delete actions.
- Added owner-and-`storagePath` availability probes to sound and wallpaper upload repositories.
- Added sound and wallpaper ViewModel delete entry points that call the owner delete methods and remove deleted items from visible feeds.
- Added confirmed owner-only delete actions to the sound detail screen and wallpaper detail More sheet.
- Added focused ViewModel tests for delete availability delegation and successful delete state.
- Updated `docs/community-upload-deletion.md`, `docs/research/cycle-56-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the visible owner-delete slice.
- Completed Cycle 57 Storage rules and emulator harness.
- Added tracked `firebase.json` and `storage.rules` for community upload blobs.
- Added local npm rules-unit-testing dependencies and `test/firebase/storage.rules.test.mjs`.
- Covered owner-only sound/wallpaper creates, public reads, MIME and size ceilings, anonymous/cross-owner rejection, owner/admin deletes, blocked overwrites, and unmanaged path denial.
- Documented the local rules harness and current `firebase-tools` transitive dev-dependency audit caveat.
- Updated `docs/firebase-rules-harness.md`, `docs/community-upload-deletion.md`, `docs/firebase-admin-claims.md`, `docs/research/cycle-57-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the Storage rules slice.
- Completed Cycle 58 Realtime Database rules harness.
- Added a Database emulator port, `test:database-rules`, `test:firebase-rules`, and `test/firebase/database.rules.test.mjs`.
- Covered community sound/wallpaper metadata public reads, authenticated owner creates, owner/admin deletes, bad path rejection, owner-index privacy, report creation/admin resolution, quota/dedupe ledger admin-only access, and collection-share publish/read rules.
- Aligned tracked collection-share rules from the unused `collection_shares` path to the app-used `shared_collections` path, added `createdByUid`, added payload bounds, allowed owner/admin cleanup, blocked non-owner overwrites, and denied the old path.
- Made `database.rules.json` emulator/deploy-compatible by removing pseudo-comment fields and replacing unsupported regex syntax with supported equality/string checks.
- Updated `.gitignore`, `docs/firebase-rules-harness.md`, `docs/firebase-admin-claims.md`, `docs/community-upload-deletion.md`, `docs/community-quota-rate-limits.md`, `docs/support/community-reporting.md`, `docs/research/cycle-58-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the RTDB rules slice.
- Completed Cycle 59 Firebase rules CI gate.
- Added a `firebase-rules` job to `.github/workflows/verify.yml` with path detection for Firebase rules, emulator config, npm files, rules tests, rules runbooks, and the workflow itself.
- The CI job installs Java 17 for the Realtime Database emulator, Node 20 with npm cache, pinned npm dependencies via `npm ci`, and runs `npm run test:firebase-rules`.
- Updated `docs/firebase-rules-harness.md`, `docs/firebase-admin-claims.md`, `docs/research/cycle-59-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the CI gate.
- Completed Cycle 60 rights takedown receipts.
- Added private admin `/community_takedown_receipts/{reportId}` payload helpers and repository writes for hidden `RIGHTS` reports against community sound/wallpaper uploads with current `storagePath` deletion handles.
- Extended RTDB rules so takedown receipts are admin-only and must match the current upload metadata row's `storagePath` and uploader UID.
- Added RTDB emulator coverage for non-admin rejection, non-rights rejection, stale-handle rejection, mismatched-path rejection, and sound/wallpaper success cases.
- Updated `docs/support/community-reporting.md`, `docs/community-upload-deletion.md`, `docs/firebase-rules-harness.md`, `docs/research/cycle-60-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the rights receipt slice.
- Cycle 60 verification: `node --check test\firebase\database.rules.test.mjs`; `py -3 -m json.tool database.rules.json > $null`; focused `CommunityReportTest`; `npm run test:database-rules`.
- Completed Cycle 61 admin upload delete actions.
- Added `CommunityReportRepository.deleteReportedCommunityUpload()` to write `DELETE` receipts, hide content IDs, delete Storage objects, remove public upload metadata plus owner indexes, and update receipts to `SUCCEEDED` or `FAILED`.
- Added a confirmed `Delete upload` action to qualifying rights report cards for community sound and wallpaper uploads.
- Extended RTDB rules and emulator coverage for `STARTED`, `SUCCEEDED`, and `FAILED` delete receipt states after initial handle-matched receipt creation.
- Updated `docs/support/community-reporting.md`, `docs/community-upload-deletion.md`, `docs/firebase-rules-harness.md`, `docs/research/cycle-61-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the admin delete slice.
- Cycle 61 verification: focused `CommunityReportsViewModelTest` and `CommunityReportTest`; `npm run test:database-rules`; `node --check test\firebase\database.rules.test.mjs`; `py -3 -m json.tool database.rules.json > $null`.
- Completed Cycle 62 closed report review filters.
- Added status filter chips to the admin community report queue for Open, Hidden, Dismissed, and Restored reports, including the empty state.
- Switched `CommunityReportsViewModel` to a status-driven report feed using the existing indexed `CommunityReportRepository.reports(status)` query.
- Updated `docs/support/community-reporting.md`, `docs/research/cycle-62-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for closed-report review.
- Cycle 62 verification: focused `CommunityReportsViewModelTest`.
- Completed Cycle 63 callable quota enforcement contract.
- Added `CommunityQuotaCallableContract` so every quota policy names its App-Checked callable, payload schema, final public/private write paths, and limited-use App Check token decision.
- Extended `CommunityQuotaPolicyTest` to prove callable names are unique lower-camel names, every callable requires Auth and App Check, every policy exposes protected quota/dedupe ledger paths, and report/upload callables consume limited-use App Check tokens.
- Added `docs/community-callable-quota-enforcement.md` with the request envelope, backend sequence, quota/dedupe transaction rules, error mapping, Android migration order, and verification plan.
- Updated `docs/community-quota-rate-limits.md`, `docs/community-app-check-rollout.md`, `docs/support/community-reporting.md`, `docs/research/cycle-63-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for the callable backend handoff.
- Cycle 63 verification: focused `CommunityQuotaPolicyTest`.
- Completed Cycle 64 community backend deploy/rollback evidence.
- Added `tools/community_backend_manifest.py` with deterministic write/check/print modes for Firebase backend deploy targets, pinned CLI version, rules-test scripts, and SHA-256 hashes for `firebase.json`, `database.rules.json`, `storage.rules`, `package.json`, and `package-lock.json`.
- Committed `docs/community-backend-manifest.json` for the current deployable backend state.
- Wired `.github/workflows/verify.yml` to run the backend manifest check before Firebase rules tests when backend files, runbooks, or the manifest tool change.
- Added `docs/community-backend-runbook.md` covering required preflight, production-project dry run, deploy command, rollback command, App Check rollback separation, and release checklist evidence.
- Updated `docs/firebase-rules-harness.md`, `docs/firebase-admin-claims.md`, `docs/community-app-check-rollout.md`, `docs/research/cycle-64-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for backend deploy evidence.
- Cycle 64 verification: `py -3 -m py_compile tools\community_backend_manifest.py`; `py -3 tools\community_backend_manifest.py --mode check`; `npx firebase deploy --help`.
- Completed Cycle 65 Storage lifecycle/orphan cleanup policy.
- Added `tools/community_storage_orphan_report.py` to compare exported Storage object names against exported RTDB community metadata and classify orphan candidates, metadata rows that reference missing objects, legacy rows missing `storagePath`, wrong-prefix metadata rows, and unmanaged objects.
- Added `test/tools/community_storage_orphan_report_test.py` for the orphan report classifier.
- Wired `.github/workflows/verify.yml` so backend lifecycle/tool changes run the orphan-report unittest before the Firebase rules suite.
- Added `docs/community-storage-lifecycle-policy.md` documenting that committed `sounds/` and `wallpapers/` prefixes must not get automatic lifecycle delete rules while upload-in-progress and committed objects share prefixes; manual cleanup requires two matching orphan reports at least 24 hours apart.
- Updated `docs/community-upload-deletion.md`, `docs/firebase-rules-harness.md`, `docs/community-backend-runbook.md`, `docs/research/cycle-65-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for lifecycle/orphan cleanup.
- Cycle 65 verification: `py -3 -m py_compile tools\community_storage_orphan_report.py`; `py -3 -m unittest discover -s test/tools -p '*_test.py'`; `npm run test:firebase-rules`.
- Completed Cycle 66 legacy upload backfill planning.
- Added `tools/community_upload_backfill_plan.py` to parse legacy Firebase Storage URLs or stored object paths, derive safe `storagePath` values, and emit dry-run RTDB multi-path updates for missing metadata `storagePath`, missing `uploaderUid`, and `/owner_uploads` index payloads.
- Added `test/tools/community_upload_backfill_plan_test.py` for Firebase Storage URL parsing, Google Storage URL parsing, generated update payloads, already-backfilled rows, and blocked unsafe rows.
- Updated `.github/workflows/verify.yml` so all backend tool tests under `test/tools` run when lifecycle/backfill tools or docs change.
- Added `docs/community-upload-backfill.md` with candidate requirements, apply gate, evidence requirements, and remaining trusted-writer work.
- Updated `docs/community-upload-deletion.md`, `docs/community-storage-lifecycle-policy.md`, `docs/research/cycle-66-2026-06-06.md`, `ROADMAP.md`, `CHANGELOG.md`, and `COMPLETED.md` for legacy backfill planning.
- Cycle 66 verification: `py -3 -m py_compile tools\community_upload_backfill_plan.py test\tools\community_upload_backfill_plan_test.py`; `py -3 -m unittest discover -s test/tools -p '*_test.py'`; `npm run test:firebase-rules`.
- Completed Cycle 67 community deletion tombstones.
- Added private `/community_upload_deletions/{publicId}` tombstones for owner
  deletes and admin rights takedowns.
- Extended owner/admin upload delete flows to remove public metadata plus owner
  indexes while writing tombstones with owner-scoped Storage handles.
- Added RTDB rules/emulator coverage for admin-only tombstone reads, owner-only
  `OWNER_DELETE` creates, admin takedown creates, admin-only updates, wrong
  Storage root denial, wrong owner prefix denial, and owner misuse of
  `ADMIN_TAKEDOWN`.
- Added `docs/community-deletion-retention-policy.md` and
  `docs/research/cycle-67-2026-06-06.md`; updated upload deletion, rules
  harness, backend runbook, roadmap, changelog, and completed docs.
- Cycle 67 verification: focused `CommunityUploadOwnershipTest`; `py -3 -m json.tool database.rules.json`; `py -3 tools\community_backend_manifest.py --mode check`; `node --check test\firebase\database.rules.test.mjs`; `npm run test:firebase-rules`.
- Completed Cycle 68 community block-user policy.
- Added `CommunityBlockPolicy.kt` and focused tests for block payload path
  generation plus self-block rejection.
- Added `user_blocks` to `CommunityQuotaPolicies` with a
  `setCommunityUserBlock` callable contract and final writes for the private
  block list plus admin reverse index.
- Reserved `/community_user_blocks/{blockerUid}/{blockedUid}` and
  `/community_blocked_by/{blockedUid}/{blockerUid}` in RTDB rules.
- Added Firebase emulator coverage for anonymous/cross-user rejection, private
  blocker reads, admin reverse-index reads, mismatched payload rejection,
  self-block rejection, and unblock deletes.
- Added `docs/community-block-user-policy.md` and
  `docs/research/cycle-68-2026-06-06.md`; updated reporting, quota, callable,
  rules harness, backend runbook, roadmap, changelog, and completed docs.
- Cycle 68 verification: focused `CommunityBlockPolicyTest` plus `CommunityQuotaPolicyTest`; `py -3 -m json.tool database.rules.json`; `py -3 tools\community_backend_manifest.py --mode check`; `node --check test\firebase\database.rules.test.mjs`; `npm run test:firebase-rules`.

## Still Open

- Exact Termux package commit, FFmpeg package patches, dependency source set, and build logs for the resolved youtubedl-android ffmpeg 0.18.1 AAR.
- Standalone editor policy enforcement if new editor entry points are added outside Sound Detail.
- Firebase App Check console registration, debug-token registration, metrics burn-in, and enforcement.
- Cloud Functions implementation and Android repository migration for the Cycle 63 callable quota contract.
- Real production-project Firebase backend dry-run evidence after owner access is confirmed.
- Real exported Storage/RTDB orphan reports after owner access is confirmed.
- Real production RTDB legacy backfill plan after owner access is confirmed.
- Trusted account deletion apply/orchestrator, hosted private web deletion
  request page, local/Auth deletion cleanup, and deployable callable backend
  implementation.

## Cycle 69 Result - 2026-06-06

- Added reusable community public-listing/takedown policy copy and focused unit
  coverage.
- Sound and wallpaper upload dialogs now disclose public listing metadata and
  confirmed rights-takedown outcomes before attestation.
- The shared report dialog now explains the private rights-takedown route, and
  owner-delete confirmations now describe public metadata/index removal plus
  retained private moderation records.
- Updated `docs/research/cycle-69-2026-06-06.md`, rights/reporting docs,
  `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 69 verification: focused `CommunityPolicyCopyTest` passed locally.

## Cycle 70 Result - 2026-06-06

- Added `CommunityBlockRepository` for private block-list reads plus block and
  unblock writes that maintain the admin reverse index.
- Added no-sign-in block-list filtering: public community browsing does not
  create a Firebase identity only to filter feeds.
- Community sound feeds, community wallpaper feeds, and creator profile lists
  now hide blocked uploaders/creators when a Firebase UID already exists.
- Updated `docs/research/cycle-70-2026-06-06.md`,
  `docs/community-block-user-policy.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 70 verification: focused `CommunityBlockPolicyTest` plus
  `CreatorProfileRepositoryTest` passed locally.

## Cycle 71 Result - 2026-06-06

- Added `communityUploaderId` metadata to community sound and wallpaper UI
  models so block actions can target canonical upload owners.
- Community sound and wallpaper detail surfaces now show confirmed `Block
  creator` actions when uploader identity is available and the item is not an
  owner-deletable upload.
- Block actions write through `CommunityBlockRepository` and remove matching
  uploader rows from active sound/wallpaper UI state.
- Updated `docs/research/cycle-71-2026-06-06.md`,
  `docs/community-block-user-policy.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 71 verification: focused `SoundsViewModelTest`,
  `WallpapersViewModelTest`, and `CommunityPolicyCopyTest` reports recorded
  zero failures and zero errors.

## Cycle 72 Result - 2026-06-06

- Added structured `CommunityBlockedUser` rows and
  `CommunityBlockRepository.blockedUsers()` for the current user's private
  community block list.
- Settings now has a `Blocked creators` review dialog with blocked uploader
  IDs, reason/timestamp metadata, empty-state copy, and per-row unblock
  actions.
- The Settings view model now delegates unblock writes through
  `CommunityBlockRepository.unblockUser()` and reports success/error state for
  the UI toast path.
- Updated `docs/research/cycle-72-2026-06-06.md`,
  `docs/community-block-user-policy.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 72 verification: focused `SettingsViewModelTest` passed locally.

## Cycle 73 Result - 2026-06-06

- Added optional `uploaderUid` metadata to community report inputs and stored
  report records so admin report cards can target canonical community uploaders.
- Community sound and wallpaper report submissions now forward
  `communityUploaderId` into report metadata when available.
- Admin report cards now expose confirmed `Block creator` actions for reports
  that carry community uploader UID metadata.
- Creator profile rows now expose confirmed `Block creator` actions for
  non-current-user creators and immediately remove matching dashboard rows after
  the private block write succeeds.
- Updated `database.rules.json`, `docs/community-backend-manifest.json`,
  `docs/research/cycle-73-2026-06-06.md`,
  `docs/community-block-user-policy.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 73 verification: focused report/profile/sound/wallpaper ViewModel and
  model tests passed locally; backend manifest check and Firebase rules suite
  passed locally.

## Cycle 74 Result - 2026-06-06

- Added `tools/community_account_deletion_plan.py` to build deterministic RTDB
  null-update plans for a Firebase UID from an exported database JSON.
- The planner covers nested and legacy vote markers, outbound/inbound follows,
  creator profiles, outbound/inbound block rows, block reverse indexes, and
  app/legacy community share rows.
- The account deletion policy now documents retained aggregate vote counts,
  retained moderation/report audit roots, and why public uploads stay on the
  owner/admin upload deletion workflow.
- Added backend tool tests and wired the new planner/doc into Firebase backend
  CI change detection and runbook references.
- Updated `docs/research/cycle-74-2026-06-06.md`, `ROADMAP.md`,
  `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 74 verification: Python compile for the planner and backend tool
  unittest discovery passed locally.

## Cycle 75 Result - 2026-06-06

- Added `CommunityIdentitySummary` and read-only summary helpers to
  `CommunityIdentityProvider`.
- The summary path reads only the existing Firebase UID or existing local
  fallback ID, so Settings does not create a local fallback UUID or Firebase
  anonymous account just by opening the community identity panel.
- Settings now has a `Community identity` row and dialog showing auth type,
  redacted identity suffix, copyable deletion request code when a Firebase
  identity exists, and retained-data policy copy.
- Updated `docs/research/cycle-75-2026-06-06.md`,
  `docs/community-account-deletion-policy.md`, `ROADMAP.md`, `COMPLETED.md`,
  and `CHANGELOG.md`.
- Cycle 75 verification: focused `CommunityIdentityProviderTest` and
  `SettingsViewModelTest` passed locally with Android Studio JBR.

## Cycle 76 Result - 2026-06-06

- Added `CommunityDeletionRequest.kt` with a redacted deletion request
  subject/body builder.
- Settings > Community identity now exposes a `Share` action when a Firebase
  deletion request code exists; it launches the device share sheet with request
  code, redacted identity suffix, auth label, and deletion statement, without
  including the full Firebase UID.
- Added `docs/support/community-account-deletion.md` for user and operator
  handling, and linked it from `README.md`.
- Updated `docs/research/cycle-76-2026-06-06.md`,
  `docs/community-account-deletion-policy.md`, `ROADMAP.md`, `COMPLETED.md`,
  and `CHANGELOG.md`.
- Cycle 76 verification: focused `CommunityIdentityProviderTest` passed
  locally with Android Studio JBR.

## Cycle 77 Result - 2026-06-06

- Added `tools/community_deletion_request_lookup.py` to map `AURA-` deletion
  request codes to candidate UID evidence paths in an exported RTDB JSON file.
- The lookup scans known UID-bearing roots and fields, computes the same
  SHA-256 based request code as Android, and emits matched UID, sanitized UID,
  and evidence paths.
- Added backend tool coverage in
  `test/tools/community_deletion_request_lookup_test.py`.
- Wired the lookup tool into Firebase backend CI change detection.
- Updated `docs/research/cycle-77-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`, `docs/community-backend-runbook.md`,
  `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 77 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 78 Result - 2026-06-06

- Added `tools/community_account_deletion_review.py` to validate request-code
  lookup output against dry-run account deletion plans before any future trusted
  apply step.
- The review gate requires exactly one lookup match, matching sanitized UID
  keys, null-only RTDB update values, categorized update coverage, and required
  retained roots for aggregate votes, moderation records, and public upload
  workflow boundaries.
- The review receipt emits redacted UID-key evidence, update/category counts,
  lookup evidence hash, and plan hash without exposing the full Firebase UID.
- Added backend tool coverage in
  `test/tools/community_account_deletion_review_test.py`.
- Wired the review tool into Firebase backend CI change detection.
- Updated `docs/research/cycle-78-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 78 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 79 Result - 2026-06-06

- Added `tools/community_account_deletion_apply_simulator.py` to simulate
  reviewed account deletion RTDB null updates against a copied database export
  without contacting Firebase.
- The simulator validates `readyForTrustedApply` review status, plan hash,
  UID-key hash, retained roots, and update counts before applying local null
  updates.
- It emits a hashed simulation receipt with deleted, missing-before, remaining
  path, plan, review, and snapshot hashes, and can optionally write the
  simulated post-delete export for private operator review.
- Tightened `tools/community_account_deletion_review.py` so account deletion
  plans cannot delete retained aggregate vote counts, private moderation roots,
  public upload metadata, or owner-upload indexes.
- Added backend tool coverage in
  `test/tools/community_account_deletion_apply_simulator_test.py`.
- Wired the simulator into Firebase backend CI change detection.
- Updated `docs/research/cycle-79-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 79 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 80 Result - 2026-06-06

- Added `tools/community_account_deletion_executor_package.py` to build the
  private RTDB null-update package that a future trusted account deletion
  executor can consume.
- The package builder validates review status, plan hash, UID-key hash,
  retained roots, simulation status, simulation review hash, zero remaining
  update paths, request code, and operator label before emitting the package.
- The output includes `readyForTrustedExecutor`, full RTDB null-update payload,
  hashes, redacted UID suffix, snapshot hash, operator, and private package
  warning.
- Added backend tool coverage in
  `test/tools/community_account_deletion_executor_package_test.py`.
- Wired the package builder into Firebase backend CI change detection.
- Updated `docs/research/cycle-80-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 80 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 81 Result - 2026-06-06

- Added `tools/community_account_deletion_rest_executor.py` as the guarded
  operator executor for private account deletion packages.
- The executor defaults to dry-run without contacting Firebase, converts the
  private package updates to RTDB REST multi-path `PATCH` keys, and rejects
  non-HTTPS database URLs except localhost emulator hosts.
- Apply mode requires matching request-code and plan-hash confirmations plus an
  OAuth2 token from `--access-token` or `FIREBASE_DATABASE_ACCESS_TOKEN`.
- Added backend tool coverage in
  `test/tools/community_account_deletion_rest_executor_test.py` for dry-run
  no-network behavior, endpoint validation, confirmation failures, patch
  payload conversion, and mocked bearer-token `PATCH` apply.
- Wired the executor into Firebase backend CI change detection.
- Updated `docs/research/cycle-81-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 81 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 82 Result - 2026-06-06

- Added `tools/community_account_deletion_completion_receipt.py` to generate a
  redacted requester-facing receipt after account deletion REST apply.
- The receipt builder validates the private executor package, requires an
  applied REST receipt with HTTP 200, checks request-code, update-count,
  updates-hash, plan-hash, and package-hash consistency, and rejects dry-run
  receipts as completion evidence.
- The output keeps full Firebase UIDs, RTDB paths, database hosts, update
  payloads, and access tokens out of the shareable artifact while preserving
  request code, support reference, redacted UID suffix, marker-delete count,
  hashes, retained data categories, completed actions, remaining operator
  actions, and user next steps.
- Added backend tool coverage in
  `test/tools/community_account_deletion_completion_receipt_test.py` for
  redaction, dry-run rejection, and REST/package mismatch rejection.
- Wired the completion receipt tool into Firebase backend CI change detection.
- Updated `docs/research/cycle-82-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 82 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 83 Result - 2026-06-06

- Added `docs/support/community-account-deletion-web-intake.md` to define the
  private hosted account deletion request form contract for users who cannot
  open Aura.
- Added `tools/community_deletion_web_intake.py` to validate private form
  exports, normalize the `AURA-` request code, require requester contact,
  requester statement, support reference, allowed channel, and
  deletion/retention/public-upload attestations.
- The web-intake receipt hashes requester contact and statement text, omits raw
  requester contact, statement text, full Firebase UIDs, RTDB paths, database
  exports, executor packages, and access tokens, and marks the request
  `readyForOperatorLookup`.
- Added backend tool coverage in
  `test/tools/community_deletion_web_intake_test.py` for redaction, missing
  attestation rejection, and invalid request-code rejection.
- Wired the web-intake validator into Firebase backend CI change detection.
- Updated `docs/research/cycle-83-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 83 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 84 Result - 2026-06-06

- Added `tools/community_account_deletion_cleanup_sequence.py` to build the
  post-backend cleanup sequence from a completed account deletion completion
  receipt.
- The tool validates completion receipt schema/status, normalized request code,
  matching support reference, marker count, and required hashes before emitting
  local/Auth cleanup steps.
- The sequence orders requester local app cleanup, operator Firebase Auth
  deletion after private UID reverification, and separate public upload deletion
  handoff while omitting full Firebase UIDs, RTDB paths, database hosts, update
  payloads, requester contact, and access tokens.
- Added backend tool coverage in
  `test/tools/community_account_deletion_cleanup_sequence_test.py` for
  completion gating, support-reference matching, marker-count validation, and
  redaction.
- Wired the cleanup sequence tool into Firebase backend CI change detection.
- Updated `docs/research/cycle-84-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 84 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 85 Result - 2026-06-06

- Reworked `CommunityIdentityProvider` local fallback ID access to use a
  synchronized cached accessor and added `clearLocalFallbackIdentity()`.
- Added `CommunityIdentityCleanupState` and
  `SettingsViewModel.clearLocalCommunityIdentity()` so Settings can clear only
  the current device fallback identity, refresh the community identity summary,
  and report success/no-op/failure messages.
- Added `Clear local` to the Community identity dialog with a busy state and
  wrapped button row.
- Updated dialog copy to state that local cleanup does not delete backend RTDB,
  Firebase Auth, public upload, Storage, or moderation records.
- Extended `SettingsViewModelTest` to cover local cleanup delegation, summary
  refresh, and success feedback.
- Updated `docs/research/cycle-85-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`, `ROADMAP.md`, `COMPLETED.md`,
  and `CHANGELOG.md`.
- Cycle 85 verification: focused Android identity/settings JVM tests passed
  locally; backend tool unittest discovery passed locally.

## Cycle 86 Result - 2026-06-06

- Added `tools/community_account_deletion_auth_package.py` to build the private
  Firebase Auth deletion package after account deletion backend completion.
- The package builder validates request-code lookup, requires exactly one match,
  validates the completed backend receipt through the cleanup sequence gate,
  checks support reference, and verifies that the private UID derives the
  requested `AURA-` code.
- The private package includes full UID, safe UID, UID hash, redacted suffix,
  completion receipt hash, lookup evidence hash, operator, and an execution
  warning.
- Added backend tool coverage in
  `test/tools/community_account_deletion_auth_package_test.py` for the happy
  path, backend-completion gating, completion request-code mismatch, and
  UID/code mismatch rejection.
- Wired the Auth package tool into Firebase backend CI change detection.
- Updated `docs/research/cycle-86-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 86 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 87 Result - 2026-06-06

- Added `tools/community_account_deletion_upload_plan.py` to build a private
  public-upload deletion handoff plan from a current RTDB export and the private
  Auth deletion package.
- Added `validate_auth_deletion_package()` to
  `tools/community_account_deletion_auth_package.py` for downstream private
  tooling.
- The upload planner scans `community_sounds` and `community_wallpapers` for
  rows owned by the deleted UID, accepts rows with matching Storage prefixes,
  blocks missing or mismatched handles, and emits owner/admin workflow
  candidates without deleting Storage objects or RTDB metadata.
- Added backend tool coverage in
  `test/tools/community_account_deletion_upload_plan_test.py` for owned upload
  collection, blocked missing/wrong handles, all-ready status, and invalid Auth
  package rejection.
- Wired the upload handoff planner into Firebase backend CI change detection.
- Updated `docs/research/cycle-87-2026-06-06.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `docs/community-upload-deletion.md`,
  `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 87 verification: Python compile and backend tool unittest discovery
  passed locally.

## Cycle 88 Result - 2026-06-07

- Added `docs/privacy/privacy-policy.md` with Aura data categories, no-ads and
  no-tracking statements, retained-record disclosure, deletion handling
  boundaries, and the current pending owner-publication status for the hosted
  deletion URL.
- Added `docs/support/community-account-deletion-web-url.json` as the canonical
  hosted deletion URL publication manifest.
- Added `tools/community_deletion_web_url_check.py` to validate both
  `pendingOwnerUrl` and `published` states. Pending state requires an empty URL
  and privacy-policy pending text; published state requires an HTTPS URL linked
  from both policy and support intake docs.
- Added backend tool coverage in
  `test/tools/community_deletion_web_url_check_test.py` for pending, published,
  missing support link, and non-HTTPS cases.
- Wired the URL manifest validator into Firebase backend CI change detection
  and the backend release checklist.
- Updated `docs/research/cycle-88-2026-06-07.md`,
  `docs/support/community-account-deletion.md`,
  `docs/support/community-account-deletion-web-intake.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 88 verification: Python compile, URL manifest check, backend
  tool unittest discovery, diff hygiene, and attribution/ASCII scans.

## Cycle 89 Result - 2026-06-07

- Added `tools/community_account_deletion_auth_execution_receipt.py` to
  validate private owner-approved Firebase Auth deletion evidence after backend
  completion.
- The receipt builder validates the private Auth package, request code, support
  reference, full UID/UID hash, owner-approved method, execution status,
  post-delete `notFound` verification, private evidence reference, and private
  evidence SHA-256.
- The emitted receipt omits full Firebase UID, project ID, raw command output,
  service-account credentials, console exports, and tokens.
- Added `validate_auth_execution_receipt()` for future downstream private
  deletion tooling.
- Added backend tool coverage in
  `test/tools/community_account_deletion_auth_execution_receipt_test.py` for
  the redacted happy path plus UID mismatch, support-reference mismatch,
  unapproved method, and invalid private evidence hash rejection.
- Wired the Auth execution receipt tool into Firebase backend CI change
  detection.
- Updated `docs/research/cycle-89-2026-06-07.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 89 verification: Python compile, focused Auth execution receipt
  tests, backend tool unittest discovery, diff hygiene, and attribution/ASCII
  scans.

## Cycle 90 Result - 2026-06-07

- Added `tools/community_account_deletion_upload_execution_receipt.py` to
  validate private owner/admin public-upload deletion workflow evidence after a
  clean account-deletion upload handoff plan.
- The receipt builder rejects blocked plans, requires every planned upload
  candidate to have matching private execution evidence, and verifies Storage
  deletion, public metadata deletion, owner-index deletion, and private
  tombstone write completion for each row.
- The emitted receipt records counts and hashes while omitting full Firebase
  UIDs, raw upload IDs, RTDB paths, Storage paths, project ID, command output,
  credentials, and tokens.
- Added `validate_upload_execution_receipt()` for future downstream private
  deletion tooling.
- Added backend tool coverage in
  `test/tools/community_account_deletion_upload_execution_receipt_test.py` for
  the redacted happy path plus blocked plan, missing candidate evidence,
  incomplete delete row, and path mismatch rejection.
- Wired the upload execution receipt tool into Firebase backend CI change
  detection.
- Updated `docs/research/cycle-90-2026-06-07.md`,
  `docs/support/community-account-deletion.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `docs/community-upload-deletion.md`,
  `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 90 verification: Python compile, focused upload execution
  receipt tests, backend tool unittest discovery, diff hygiene, and
  attribution/ASCII scans.

## Cycle 91 Result - 2026-06-07

- Added `docs/community-callable-contract.json` as the backend-facing manifest
  for the current `CommunityQuotaPolicies` callable contract.
- The manifest records all seven community write surfaces, daily limits,
  cooldowns, dedupe keys, protected quota/dedupe ledgers, function names,
  payload schemas, final write paths, Auth/App Check requirements, limited-use
  App Check token decisions, and UTC quota-day boundary.
- Added `tools/community_callable_contract_check.py` to validate the manifest
  against the Android quota policy constants.
- Added backend tool coverage in
  `test/tools/community_callable_contract_check_test.py` for the valid manifest
  summary plus missing surface, duplicate function name, limited-use App Check
  drift, and ledger namespace drift failures.
- Wired the callable contract manifest check into Firebase backend CI change
  detection.
- Updated `docs/research/cycle-91-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`, `docs/community-backend-runbook.md`,
  `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 91 verification: Python compile, focused callable contract
  tests, callable manifest check, backend tool unittest discovery, diff hygiene,
  and attribution/ASCII scans.

## Cycle 92 Result - 2026-06-07

- Added `docs/support/community-account-deletion-web-page.md` with publishable
  hosted account deletion page copy for owner publication.
- The page template includes Aura branding, app-independent deletion request
  copy, required `AURA-` request code, web-intake field names, retained-record
  disclosure, public-upload caveat, and privacy-policy link requirement.
- Added `tools/community_deletion_web_page_check.py` to validate required page
  terms, required form fields, app-independent request copy, and forbidden
  sensitive identifier/secret requests.
- Added backend tool coverage in
  `test/tools/community_deletion_web_page_check_test.py` for the valid template
  plus missing field, sensitive identifier, and app-dependent request path
  rejection.
- Wired the hosted page validator into Firebase backend CI change detection.
- Updated `docs/research/cycle-92-2026-06-07.md`,
  `docs/support/community-account-deletion-web-intake.md`,
  `docs/support/community-account-deletion.md`,
  `docs/privacy/privacy-policy.md`,
  `docs/community-account-deletion-policy.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 92 verification: Python compile, focused hosted page tests, hosted
  page check, backend tool unittest discovery, diff hygiene, and
  attribution/ASCII scans passed.

## Cycle 93 Result - 2026-06-07

- Added the Node 20 TypeScript `functions/` project with pinned
  `firebase-functions` 7.2.5, `firebase-admin` 13.10.0, and TypeScript 5.9.3.
- Added `functions/src/communityContract.ts` as a runtime mirror of
  `docs/community-callable-contract.json`.
- Added `functions/src/quotaEngine.ts` with pure UTC quota-day, duplicate,
  cooldown, daily-limit, accepted-state, blocked-state, and dedupe-marker
  decisions.
- Added fail-closed App Check/Auth callable exports for all seven contracted
  community write surfaces in `functions/src/index.ts`.
- Added `functions/test/communityContract.test.cjs` and
  `functions/test/quotaEngine.test.cjs` for manifest sync, runtime options,
  limited-use token choices, UTC day boundaries, duplicate handling, cooldown,
  and daily-limit behavior.
- Updated `firebase.json`, `.github/workflows/verify.yml`, `package.json`,
  `.gitignore`, `tools/community_backend_manifest.py`, and
  `docs/community-backend-manifest.json` so Functions source is part of backend
  verification.
- Updated `docs/research/cycle-93-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 93 verification: Functions test suite, backend manifest check,
  callable contract manifest check, high-severity npm audit, diff hygiene, and
  attribution/ASCII scans passed. The current Firebase Admin/Functions
  dependency chain still reports moderate transitive `uuid` advisories; forced
  audit remediation would downgrade Firebase Admin across a breaking boundary.

## Cycle 94 Result - 2026-06-07

- Added `functions/src/reportHandler.ts` and switched
  `submitCommunityReport` from fail-closed scaffold to a handler-backed
  callable.
- The handler requires Firebase Auth and App Check, rejects client-supplied
  `reporterUid`, validates the common envelope, normalizes report payload
  fields, requires HTTPS source URLs when present, derives dedupe keys
  server-side, reserves UTC quota through the backend adapter, and writes the
  report plus dedupe marker after quota acceptance.
- Added `functions/test/submitCommunityReport.test.cjs` for accepted,
  duplicate, cooldown, daily-limit, unauthenticated, missing-App-Check,
  reporter-override, and insecure-source-URL paths.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-94-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`,
  `docs/support/community-reporting.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 94 verification: Functions test suite, backend manifest check,
  callable contract manifest check, diff hygiene, and attribution/ASCII scans.
  Emulator-backed callable invocation, Android report migration, owner-approved
  deploy evidence, and Firebase Console App Check evidence remain open.

## Cycle 95 Result - 2026-06-07

- Added `functions/src/voteHandler.ts` and switched `recordCommunityVote` from
  fail-closed scaffold to a handler-backed callable.
- The handler requires Firebase Auth and App Check, validates the common
  envelope, normalizes the vote content ID to the Android/Firebase key storage
  form, returns duplicates for existing nested or legacy voter markers before
  quota reservation, checks UTC quota and dedupe state, commits the vote tally
  transaction, mirrors the legacy voter marker, and writes a server-owned
  dedupe marker.
- Added `functions/test/recordCommunityVote.test.cjs` for accepted,
  existing-voter duplicate, active-dedupe duplicate, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid-content-ID paths.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-95-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 95 verification: Functions test suite, backend manifest check,
  callable contract manifest check, high-severity npm audit, diff hygiene, and
  attribution/ASCII scans. Emulator-backed callable invocation, Android vote
  migration, owner-approved deploy evidence, direct RTDB rule tightening, and
  Firebase Console App Check evidence remain open.

## Cycle 96 Result - 2026-06-07

- Refined the follows quota contract from `creatorId` to
  `creatorId + desired state` in the Android policy table, backend JSON
  manifest, Functions contract mirror, validator expectations, and quota
  runbook so follow and unfollow retries do not block each other.
- Added `functions/src/followHandler.ts` and switched `setCreatorFollow` from
  fail-closed scaffold to a handler-backed callable.
- The handler requires Firebase Auth and App Check, rejects client-supplied
  follower UID overrides, validates the common envelope, normalizes creator ID,
  path key, label, and desired state, returns duplicate no-op states before
  quota reservation, uses action-specific dedupe keys, checks UTC quota, and
  sets or removes the final follow row with a server-owned dedupe marker.
- Added `functions/test/setCreatorFollow.test.cjs` for accepted follow,
  accepted unfollow, duplicate no-op states, same-state dedupe, cooldown,
  daily-limit, unauthenticated, missing-App-Check, and invalid payload paths.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-96-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 96 verification: Functions test suite, backend manifest check,
  callable contract manifest check, callable contract unittest, focused Android
  quota policy test, high-severity npm audit, diff hygiene, and
  attribution/ASCII scans. Emulator-backed callable invocation, Android follow
  migration, owner-approved deploy evidence, direct RTDB rule tightening, and
  Firebase Console App Check evidence remain open.

## Cycle 97 Result - 2026-06-07

- Refined the user-block quota contract from `blockedUid` to
  `blockedUid + desired state` in the Android policy table, backend JSON
  manifest, Functions contract mirror, validator expectations, and quota
  runbook so block and unblock retries do not block each other.
- Added `functions/src/blockHandler.ts` and switched `setCommunityUserBlock`
  from fail-closed scaffold to a handler-backed callable.
- The handler requires Firebase Auth and App Check, rejects client-supplied
  blocker UID overrides, validates the common envelope, normalizes blocker UID,
  blocked UID, desired state, and reason, rejects self-blocks after path-key
  normalization, returns duplicate no-op states before quota reservation, uses
  action-specific dedupe keys, checks UTC quota, and sets or removes the
  private block row plus admin reverse-index row with a server-owned dedupe
  marker.
- Added `functions/test/setCommunityUserBlock.test.cjs` for accepted block,
  accepted unblock, duplicate no-op states, same-state dedupe, cooldown,
  daily-limit, unauthenticated, missing-App-Check, and invalid payload paths.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-97-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`, `docs/community-block-user-policy.md`,
  `ROADMAP.md`, `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 97 verification: Functions test suite, backend manifest check,
  callable contract manifest check, callable contract unittest, focused Android
  quota policy test, high-severity npm audit, diff hygiene, and
  attribution/ASCII scans. Emulator-backed callable invocation, Android block
  migration, owner-approved deploy evidence, direct RTDB rule tightening, and
  Firebase Console App Check evidence remain open.

## Cycle 98 Result - 2026-06-07

- Added `functions/src/soundUploadHandler.ts` and switched
  `finalizeCommunitySoundUpload` from fail-closed scaffold to a handler-backed
  callable.
- The handler requires Firebase Auth and App Check, rejects client-supplied
  owner/uploader/upload ID/timestamp/vote overrides, validates the common
  envelope, normalizes sound upload metadata, requires an authenticated-owner
  `sounds/{uid}/...` Storage path, derives storage-path dedupe server-side,
  checks UTC quota, allocates the public upload ID server-side, and writes both
  `/community_sounds/{uploadId}` and
  `/owner_uploads/{uid}/sounds/{uploadId}` with a server-owned dedupe marker.
- Added `functions/test/finalizeCommunitySoundUpload.test.cjs` for accepted
  finalization, active storage-path dedupe, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid ownership/payload paths.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-98-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 98 verification: Functions test suite, backend manifest check,
  callable contract manifest check, high-severity npm audit, diff hygiene, and
  attribution/ASCII scans. Emulator-backed callable invocation, Android sound
  upload migration, owner-approved deploy evidence, direct RTDB rule
  tightening, and Firebase Console App Check evidence remain open.

## Cycle 99 Result - 2026-06-07

- Added `functions/src/wallpaperUploadHandler.ts` and switched
  `finalizeCommunityWallpaperUpload` from fail-closed scaffold to a
  handler-backed callable.
- The handler requires Firebase Auth and App Check, rejects client-supplied
  owner/uploader/upload ID/timestamp/vote overrides, validates the common
  envelope, normalizes wallpaper upload metadata, requires an
  authenticated-owner `wallpapers/{uid}/...` Storage path, validates JPEG file
  metadata under the 4 MB/2560 px bounds, derives storage-path dedupe
  server-side, checks UTC quota, allocates the public upload ID server-side, and
  writes both `/community_wallpapers/{uploadId}` and
  `/owner_uploads/{uid}/wallpapers/{uploadId}` with a server-owned dedupe
  marker.
- Added `functions/test/finalizeCommunityWallpaperUpload.test.cjs` for
  accepted finalization, active storage-path dedupe, cooldown, daily-limit,
  unauthenticated, missing-App-Check, and invalid ownership/payload paths.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-99-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 99 verification: Functions test suite, backend manifest check,
  callable contract manifest check, high-severity npm audit, diff hygiene, and
  attribution/ASCII scans. Emulator-backed callable invocation, Android
  wallpaper upload migration, owner-approved deploy evidence, direct RTDB rule
  tightening, and Firebase Console App Check evidence remain open.

## Cycle 100 Result - 2026-06-07

- Added `functions/src/profileHandler.ts` and switched
  `updateCreatorProfile` from the fail-closed scaffold to a handler-backed
  callable.
- The handler requires Firebase Auth and App Check, rejects client-supplied
  profile UID/owner UID/timestamp overrides, validates the common envelope,
  normalizes public display name, bio, website URL, and avatar URL, derives
  profile UID and timestamps server-side, returns duplicate for unchanged
  public profile rows before quota reservation, derives normalized-profile
  dedupe server-side, checks UTC quota, and writes
  `/creator_profiles/{uid}` with a server-owned dedupe marker.
- Refined the profile edit dedupe contract from profile UID only to profile
  UID plus normalized public profile hash so distinct edits are not hidden by
  a long-lived profile-level dedupe marker.
- Added `functions/test/updateCreatorProfile.test.cjs` for accepted update,
  identical-profile duplicate, active normalized-profile dedupe, cooldown,
  daily-limit, unauthenticated, missing-App-Check, and invalid
  ownership/payload paths.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-100-2026-06-07.md`,
  `docs/community-callable-quota-enforcement.md`,
  `docs/community-quota-rate-limits.md`,
  `docs/community-backend-runbook.md`, `ROADMAP.md`, `COMPLETED.md`, and
  `CHANGELOG.md`.
- Cycle 100 verification: Functions test suite, backend manifest check,
  callable contract manifest check, focused Android quota policy test,
  high-severity npm audit, diff hygiene, and attribution/ASCII scans.
  Emulator-backed callable invocation, Android profile edit migration,
  owner-approved deploy evidence, direct RTDB rule tightening, and Firebase
  Console App Check evidence remain open.

## Cycle 101 Result - 2026-06-07

- Added `test/firebase/functions.profile.test.mjs` for RTDB-emulator-backed
  `updateCreatorProfileHandler` persistence coverage.
- Added `npm run test:functions-emulator`, which builds the Functions project
  and runs the profile handler test under
  `firebase emulators:exec --only database`.
- Wired `npm run test:functions-emulator` into the `firebase-rules` CI lane
  after Functions unit tests and before the rules suite.
- The emulator test resolves `firebase-admin` from the `functions/` package,
  invokes the profile handler with its real Admin SDK backend, and verifies
  accepted profile writes, quota rows, dedupe markers, and unchanged-profile
  idempotency in the RTDB emulator.
- Updated `functions/src/profileHandler.ts` so direct handler invocations can
  default to the real Firebase backend when a fake backend is not provided.
- Updated `tools/community_backend_manifest.py` and refreshed
  `docs/community-backend-manifest.json` so Functions-related emulator test
  scripts are tracked in the backend manifest.
- Updated `docs/research/cycle-101-2026-06-07.md`,
  `docs/community-backend-runbook.md`,
  `docs/community-callable-quota-enforcement.md`, `ROADMAP.md`,
  `COMPLETED.md`, and `CHANGELOG.md`.
- Cycle 101 verification: new Functions emulator test, Functions test suite,
  backend manifest check, callable contract manifest check, high-severity npm
  audit, diff hygiene, and attribution/ASCII scans. RTDB-emulator-backed
  handler persistence coverage for report, vote, follow, block, sound upload,
  and wallpaper upload handlers, full callable wire-protocol coverage, Android
  callable migration, owner-approved deploy evidence, direct RTDB rule
  tightening, and Firebase Console App Check evidence remain open.

## Cycle 102 Result - 2026-06-07

- Added `test/firebase/functions.report.test.mjs` for RTDB-emulator-backed
  `submitCommunityReportHandler` persistence coverage.
- Updated `submitCommunityReportHandler()` so direct handler invocations can
  default to the real Firebase backend when a fake backend is not provided.
- Expanded `npm run test:functions-emulator` to run all
  `test/firebase/functions.*.test.mjs` files.
- The emulator test verifies accepted report writes, server-derived reporter
  UID and timestamp fields, quota rows, dedupe markers, and same-content plus
  same-reason duplicate handling in the RTDB emulator.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-102-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, and loop state.
- Cycle 102 verification: expanded Functions emulator test suite, Functions
  test suite, backend manifest check, callable contract manifest check,
  high-severity npm audit, diff hygiene, and attribution/ASCII scans.
  RTDB-emulator-backed handler persistence coverage for vote, follow, block,
  sound upload, and wallpaper upload handlers, full callable wire-protocol
  coverage, Android callable migration, owner-approved deploy evidence, direct
  RTDB rule tightening, and Firebase Console App Check evidence remain open.

## Cycle 103 Result - 2026-06-07

- Added `test/firebase/functions.vote.test.mjs` for RTDB-emulator-backed
  `recordCommunityVoteHandler` persistence coverage.
- Updated `recordCommunityVoteHandler()` so direct handler invocations can
  default to the real Firebase backend when a fake backend is not provided.
- Serialized `npm run test:functions-emulator` with
  `node --test --test-concurrency=1` to prevent shared RTDB emulator cleanup
  races across callable test files.
- The emulator test verifies accepted vote tally writes, nested voter markers,
  legacy voter markers, quota rows, dedupe markers, and repeat-vote
  idempotency in the RTDB emulator.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-103-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, and loop state.
- Cycle 103 verification: expanded Functions emulator test suite, Functions
  test suite, backend manifest check, callable contract manifest check,
  high-severity npm audit, diff hygiene, and attribution/ASCII scans.
  RTDB-emulator-backed handler persistence coverage for follow, block, sound
  upload, and wallpaper upload handlers, full callable wire-protocol coverage,
  Android callable migration, owner-approved deploy evidence, direct RTDB rule
  tightening, and Firebase Console App Check evidence remain open.

## Cycle 104 Result - 2026-06-07

- Added `test/firebase/functions.follow.test.mjs` for RTDB-emulator-backed
  `setCreatorFollowHandler` persistence coverage.
- Updated `setCreatorFollowHandler()` so direct handler invocations can
  default to the real Firebase backend when a fake backend is not provided.
- The emulator test verifies accepted follow writes, server-derived follower
  UID and timestamp fields, quota rows, follow dedupe markers, accepted
  unfollow removals, unfollow dedupe markers, and missing-unfollow idempotency
  in the RTDB emulator.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-104-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, and loop state.
- Cycle 104 verification: expanded Functions emulator test suite, Functions
  test suite, backend manifest check, callable contract manifest check,
  high-severity npm audit, diff hygiene, and attribution/ASCII scans.
  RTDB-emulator-backed handler persistence coverage for block, sound upload,
  and wallpaper upload handlers, full callable wire-protocol coverage, Android
  callable migration, owner-approved deploy evidence, direct RTDB rule
  tightening, and Firebase Console App Check evidence remain open.

## Cycle 105 Result - 2026-06-07

- Added `test/firebase/functions.block.test.mjs` for RTDB-emulator-backed
  `setCommunityUserBlockHandler` persistence coverage.
- Updated `setCommunityUserBlockHandler()` so direct handler invocations can
  default to the real Firebase backend when a fake backend is not provided.
- The emulator test verifies accepted private block writes, matching
  reverse-index writes, server-derived blocker UID and timestamp fields, quota
  rows, block dedupe markers, accepted unblock removals, unblock dedupe
  markers, and missing-unblock idempotency in the RTDB emulator.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-105-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 105 verification: expanded Functions emulator test suite, Functions
  test suite, backend manifest check, callable contract manifest check,
  high-severity npm audit, diff hygiene, and attribution/ASCII scans.
  RTDB-emulator-backed handler persistence coverage for sound upload and
  wallpaper upload handlers, full callable wire-protocol coverage, Android
  callable migration, owner-approved deploy evidence, direct RTDB rule
  tightening, and Firebase Console App Check evidence remain open.

## Cycle 106 Result - 2026-06-07

- Added `test/firebase/functions.sound-upload.test.mjs` for RTDB-emulator-backed
  `finalizeCommunitySoundUploadHandler` persistence coverage.
- Updated `finalizeCommunitySoundUploadHandler()` so direct handler invocations
  can default to the real Firebase backend when a fake backend is not provided.
- The emulator test verifies accepted public metadata writes, owner-index
  writes, server-derived uploader UID and timestamp fields, quota rows,
  storage-path dedupe markers, and same-storage-path idempotency in the RTDB
  emulator.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-106-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 106 verification: expanded Functions emulator test suite, Functions
  test suite, backend manifest check, callable contract manifest check,
  high-severity npm audit, diff hygiene, and attribution/ASCII scans.
  RTDB-emulator-backed handler persistence coverage for wallpaper upload,
  full callable wire-protocol coverage, Android callable migration,
  owner-approved deploy evidence, direct RTDB rule tightening, and Firebase
  Console App Check evidence remain open.

## Cycle 107 Result - 2026-06-07

- Added `test/firebase/functions.wallpaper-upload.test.mjs` for
  RTDB-emulator-backed `finalizeCommunityWallpaperUploadHandler` persistence
  coverage.
- Updated `finalizeCommunityWallpaperUploadHandler()` so direct handler
  invocations can default to the real Firebase backend when a fake backend is
  not provided.
- The emulator test verifies accepted public metadata writes, owner-index
  writes, server-derived uploader UID and timestamp fields, quota rows,
  storage-path dedupe markers, and same-storage-path idempotency in the RTDB
  emulator.
- Refreshed `docs/community-backend-manifest.json`.
- Updated `docs/research/cycle-107-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 107 verification: expanded Functions emulator test suite, Functions
  test suite, backend manifest check, callable contract manifest check,
  high-severity npm audit, diff hygiene, and attribution/ASCII scans. All seven
  callable handler surfaces now have RTDB-emulator-backed persistence coverage.
  Full callable wire-protocol coverage, Android callable migration,
  owner-approved deploy evidence, direct RTDB rule tightening, and Firebase
  Console App Check evidence remain open.

## Cycle 108 Result - 2026-06-07

- Added the Android `firebase-functions` dependency under the existing Firebase
  BoM and refreshed Gradle dependency verification metadata.
- Added `CommunityCallableClient`, `CommunityCallableInvoker`, and a
  Firebase-backed invoker using `FirebaseFunctions.getHttpsCallable`.
- Added limited-use App Check token selection for report submissions from
  `CommunityQuotaPolicies.reports`.
- Added `buildCommunityReportCallablePayload()` so report callable requests omit
  server-owned reporter UID, timestamp, status, and report key fields.
- Updated `CommunityReportRepository.submitReport()` to try the
  `submitCommunityReport` callable first when Firebase Auth has a current UID,
  while preserving the direct RTDB write as a compatibility fallback until
  owner-approved deploy evidence and direct-rule tightening are complete.
- Refreshed `docs/legal/dependency-notices.lock.json` after release OSS notice
  generation; generated notices now cover 260 dependency records and 305 notice
  sections.
- Updated `docs/research/cycle-108-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 108 verification: focused Android report callable/model tests, release
  OSS notice generation, dependency notice lock checks, native compliance lock
  check, dependency overlay check, dependency license policy check, Python
  compile checks, diff hygiene, and attribution/ASCII scans. Vote, follow,
  block, upload finalizer, and profile Android callable migrations, full
  callable wire-protocol coverage, owner-approved deploy evidence, direct RTDB
  rule tightening, and Firebase Console App Check evidence remain open.

## Cycle 109 Result - 2026-06-07

- Added `CommunityVoteInput` payload normalization for Android callable vote
  requests, matching the backend content ID bounds and Firebase key handling.
- Extended `CommunityCallableClient` with `recordCommunityVote`, using
  `CommunityQuotaPolicies.votes` and standard App Check tokens.
- Updated `VoteRepository.upvote()` to prefer the `recordCommunityVote`
  callable when Firebase Auth is available, return false for duplicate or
  blocked callable outcomes without bypassing quota/App Check errors, and
  preserve the direct RTDB transaction only for missing callable endpoint or
  missing Firebase Auth compatibility.
- Extended `CommunityCallableClientTest` coverage for vote request envelopes,
  content ID normalization, and non-limited-use token selection.
- Updated `docs/research/cycle-109-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 109 verification: focused Android callable client tests, callable
  contract check, generated notice lock checks, native compliance lock check,
  dependency overlay check, dependency license policy check, high-severity npm
  audit, and diff hygiene passed. Follow, block, upload finalizer, and profile
  Android callable migrations, full callable wire-protocol coverage,
  owner-approved deploy evidence, direct RTDB rule tightening, and Firebase
  Console App Check evidence remain open.

## Cycle 110 Result - 2026-06-07

- Added `CommunityFollowInput` payload normalization for Android follow and
  unfollow callable requests.
- Extended `CommunityCallableClient` with `setCreatorFollow`, using
  `CommunityQuotaPolicies.follows` and standard App Check tokens.
- Updated `CreatorProfileRepository.followCreator()` and `unfollowCreator()` to
  prefer the `setCreatorFollow` callable when Firebase Auth is available,
  preserve no-op duplicate responses as success, avoid direct RTDB fallback for
  quota/App Check/validation errors, and keep the direct RTDB write/remove path
  only for missing callable endpoint or missing Firebase Auth compatibility.
- Extended `CommunityCallableClientTest` coverage for follow and unfollow
  request envelopes, text normalization, desired-state payloads, and
  non-limited-use token selection.
- Updated `docs/research/cycle-110-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 110 verification: focused Android callable client tests, callable
  contract check, generated notice lock checks, native compliance lock check,
  dependency overlay check, dependency license policy check, high-severity npm
  audit, diff hygiene, and attribution/ASCII scan passed. Block, upload
  finalizer, and profile Android callable migrations, full callable
  wire-protocol coverage, owner-approved deploy evidence, direct RTDB rule
  tightening, and Firebase Console App Check evidence remain open.

## Cycle 111 Result - 2026-06-07

- Added `CommunityUserBlockInput` payload normalization for Android block and
  unblock callable requests.
- Extended `CommunityCallableClient` with `setCommunityUserBlock`, using
  `CommunityQuotaPolicies.userBlocks` and standard App Check tokens.
- Updated `CommunityBlockRepository.blockUser()` and `unblockUser()` to prefer
  the `setCommunityUserBlock` callable when Firebase Auth is available,
  preserve no-op duplicate responses as success, avoid direct RTDB fallback for
  quota/App Check/validation errors, and keep the direct RTDB update path only
  for missing callable endpoint or missing Firebase Auth compatibility.
- Extended `CommunityCallableClientTest` coverage for block and unblock request
  envelopes, desired-state payloads, reason handling, blocker-UID omission, and
  non-limited-use token selection.
- Updated `docs/research/cycle-111-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 111 verification: focused Android callable client and block policy
  tests, callable contract check, generated notice lock checks, native
  compliance lock check, dependency overlay check, dependency license policy
  check, high-severity npm audit, diff hygiene, and attribution/ASCII scan
  passed. Upload finalizer and profile Android callable migrations, full
  callable wire-protocol coverage, owner-approved deploy evidence, direct RTDB
  rule tightening, and Firebase Console App Check evidence remain open.

## Cycle 112 Result - 2026-06-07

- Added `CommunitySoundUploadMetadataInput` payload normalization for Android
  sound upload finalizer callable requests.
- Extended `CommunityCallableClient` with `finalizeCommunitySoundUpload`, using
  `CommunityQuotaPolicies.soundUploads` and limited-use App Check tokens.
- Updated `UploadRepository.uploadSound()` to prefer the
  `finalizeCommunitySoundUpload` callable for metadata finalization after
  Storage upload when Firebase Auth is available, preserve duplicate responses
  as success, avoid direct RTDB fallback for quota/App Check/validation/storage
  ownership errors, and keep the direct RTDB metadata path only for missing
  callable endpoint or missing Firebase Auth compatibility.
- Extended `CommunityCallableClientTest` coverage for sound upload finalizer
  request envelopes, metadata normalization, server-owned field omission, and
  limited-use token selection.
- Updated `docs/research/cycle-112-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 112 verification: focused Android callable client, upload rights, and
  upload ownership tests, callable contract check, generated notice lock
  checks, native compliance lock check, dependency overlay check, dependency
  license policy check, high-severity npm audit, diff hygiene, and
  attribution/ASCII scan passed. Wallpaper upload finalizer and profile Android
  callable migrations, full callable wire-protocol coverage, owner-approved
  deploy evidence, direct RTDB rule tightening, and Firebase Console App Check
  evidence remain open.

## Cycle 113 Result - 2026-06-07

- Added `CommunityWallpaperUploadMetadataInput` payload normalization for
  Android wallpaper upload finalizer callable requests.
- Extended `CommunityCallableClient` with
  `finalizeCommunityWallpaperUpload`, using
  `CommunityQuotaPolicies.wallpaperUploads` and limited-use App Check tokens.
- Updated `WallpaperUploadRepository.uploadWallpaper()` to prefer the
  `finalizeCommunityWallpaperUpload` callable for metadata finalization after
  Storage upload when Firebase Auth is available, preserve duplicate responses
  as success, avoid direct RTDB fallback for quota/App Check/validation/storage
  ownership errors, and keep the direct RTDB metadata path only for missing
  callable endpoint or missing Firebase Auth compatibility.
- Extended `CommunityCallableClientTest` coverage for wallpaper upload
  finalizer request envelopes, metadata normalization, server-owned field
  omission, and limited-use token selection.
- Updated `docs/research/cycle-113-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 113 verification: focused Android callable client, wallpaper upload
  repository validation, upload rights, and upload ownership tests, callable
  contract check, dependency notice lock checks, native lock check, dependency
  overlay check, dependency license policy check, high-severity Functions npm
  audit, diff hygiene, and attribution/ASCII scans passed. Profile Android
  callable migration, full callable wire-protocol coverage, owner-approved
  deploy evidence, direct RTDB rule tightening, and Firebase Console App Check
  evidence remain open.

## Cycle 114 Result - 2026-06-07

- Added `CreatorProfileUpdateInput` payload normalization for Android creator
  profile edit callable requests.
- Extended `CommunityCallableClient` with `updateCreatorProfile`, using
  `CommunityQuotaPolicies.profileEdits` and standard App Check token handling.
- Updated `CreatorProfileRepository` with current-profile dashboard reads and a
  callable-first `updateCreatorProfile()` save path when Firebase Auth is
  available, with direct RTDB fallback only for missing callable endpoint or
  missing Firebase Auth compatibility.
- Added creator profile screen edit UI for display name, bio, website URL, and
  avatar URL, with local dashboard state sync after a successful save.
- Extended `CommunityCallableClientTest` and `CreatorProfileViewModelTest`
  coverage for profile update request envelopes, server-owned field omission,
  standard token selection, and successful UI-state updates.
- Updated `docs/research/cycle-114-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 114 verification: focused Android callable client, creator profile
  repository, and creator profile ViewModel tests, callable contract check,
  dependency notice lock checks, native lock check, dependency overlay check,
  dependency license policy check, high-severity Functions npm audit, and diff
  hygiene plus attribution/ASCII scans passed. Full callable wire-protocol
  coverage, owner-approved deploy evidence, direct RTDB rule tightening, and
  Firebase Console App Check evidence remain open.

## Cycle 115 Result - 2026-06-07

- Added `docs/community-callable-wire-protocol.json` as the checked Android
  callable wire-protocol manifest for report, vote, follow, user-block, sound
  upload finalization, wallpaper upload finalization, and profile edit writes.
- Added `tools/community_callable_wire_protocol_check.py` to compare the wire
  manifest against the backend callable contract and Android client source,
  including method signatures, quota-policy accessors, payload builders, shared
  request envelope use, operation-ID prefixes, response resource-ID mappings,
  App Check token choices, and focused client tests.
- Added `test/tools/community_callable_wire_protocol_check_test.py` coverage
  for the live manifest plus missing-surface, token-choice, missing-method,
  Android-input-type, and missing-focused-test drift cases.
- Wired `.github/workflows/verify.yml` to run the callable wire-protocol check
  in the Firebase backend CI lane when callable/backend files change.
- Updated `docs/research/cycle-115-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 115 verification: callable wire-protocol manifest check, backend tool
  tests, callable contract check, dependency notice lock checks, native lock
  check, dependency overlay check, dependency license policy check,
  high-severity Functions npm audit, diff hygiene, and attribution/ASCII scans
  passed. Owner-approved deploy evidence, live callable invocation evidence,
  direct RTDB rule tightening, and Firebase Console App Check evidence remain
  open.

## Cycle 116 Result - 2026-06-07

- Added `docs/community-callable-rollout-evidence.md` as the private-evidence
  and redacted-receipt runbook for future live callable rollout proof.
- Added `tools/community_callable_rollout_receipt.py` to validate
  owner-provided private live callable invocation evidence against the callable
  contract and Android wire-protocol manifests before emitting a redacted
  receipt.
- Added `test/tools/community_callable_rollout_receipt_test.py` coverage for
  redaction and drift failures around missing surfaces, App Check token mode,
  operation prefixes, manifest hashes, Functions App Check state, and duplicate
  receipt rows.
- Wired `.github/workflows/verify.yml` change detection for the rollout
  receipt tool and evidence runbook, and updated backend/callable runbooks.
- Updated `docs/research/cycle-116-2026-06-07.md`, `ROADMAP.md`,
  `COMPLETED.md`, `CHANGELOG.md`, backend runbook, callable quota enforcement
  doc, and loop state.
- Cycle 116 verification: focused rollout receipt tests, backend tool tests,
  callable wire-protocol check, callable contract check, dependency notice lock
  checks, native lock check, dependency overlay check, dependency license
  policy check, high-severity Functions npm audit, diff hygiene, and
  attribution/ASCII scans passed. Owner-approved deploy evidence, actual live
  callable invocation evidence, direct RTDB rule tightening, and Firebase
  Console App Check evidence remain open.

## Cycle 117 Result - 2026-06-07

- Added `docs/distribution/github-security-workflows.json` as the checked
  workflow policy for Dependency Review, OpenSSF Scorecard, and Release.
- Added `tools/github_security_workflow_check.py` to validate workflow policy
  metadata, required snippets, missing workflow files, duplicate workflow
  names/paths, and forbidden unsafe snippets.
- Added `test/tools/github_security_workflow_check_test.py` coverage for the
  live policy and drift cases around required snippets, forbidden snippets,
  duplicate names, duplicate paths, and missing workflow files.
- Wired `.github/workflows/verify.yml` so the policy check runs in the
  always-on verify job before Android setup.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-117-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 117 verification: GitHub security workflow policy check, focused
  policy tests, backend tool tests, callable wire-protocol check, callable
  contract check, dependency notice lock checks, native lock check, dependency
  overlay check, dependency license policy check, high-severity Functions npm
  audit, diff hygiene, and attribution/ASCII scans passed. Owner-approved
  deploy evidence, actual live callable invocation evidence, direct RTDB rule
  tightening, Firebase Console App Check evidence, and live GitHub repository
  security-settings evidence remain open.

## Cycle 118 Result - 2026-06-07

- Added `.github/dependabot.yml` with weekly version-update PR coverage for
  GitHub Actions, Gradle, root Firebase rules npm, and Firebase Functions npm.
- Added `tools/dependabot_config_check.py` to validate the four expected
  Dependabot update surfaces, `main` target branch, Monday
  `America/New_York` schedule, open PR limit, `deps` commit prefix, and
  `dependencies`/`security` labels.
- Added `test/tools/dependabot_config_check_test.py` coverage for the live
  config and drift cases around unsupported surfaces, duplicate surfaces,
  cadence drift, missing labels, and excessive open PR limits.
- Wired `.github/workflows/verify.yml` so the Dependabot update policy check
  runs in the always-on verify job.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-118-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 118 verification: Dependabot update policy check, focused Dependabot
  policy tests, backend tool tests, GitHub security workflow policy check,
  callable wire-protocol check, callable contract check, dependency notice lock
  checks, native lock check, dependency overlay check, dependency license
  policy check, high-severity Functions npm audit, diff hygiene, and
  attribution/ASCII scans passed. Owner-approved deploy evidence, actual live
  callable invocation evidence, direct RTDB rule tightening, Firebase Console
  App Check evidence, live GitHub repository security-settings evidence, and
  live Dependabot alerts/security-updates evidence remain open.

## Cycle 119 Result - 2026-06-07

- Added `docs/distribution/github-security-settings-evidence.md` as the
  private-evidence and redacted-receipt runbook for future live GitHub
  repository security settings proof.
- Added `tools/github_security_settings_receipt.py` to validate
  owner/admin-provided private evidence against the checked workflow policy and
  Dependabot config before emitting a redacted receipt.
- Added `test/tools/github_security_settings_receipt_test.py` coverage for
  receipt redaction and drift failures around missing required checks, policy
  hash drift, disabled Dependabot alerts, disabled secret scanning, and
  support-reference drift.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-119-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 119 verification: focused GitHub security settings receipt tests,
  backend tool tests, Dependabot policy check, GitHub security workflow policy
  check, callable wire-protocol check, callable contract check, dependency
  notice lock checks, native lock check, dependency overlay check, dependency
  license policy check, high-severity Functions npm audit, diff hygiene, and
  attribution/ASCII scans passed. Owner-approved deploy evidence, actual live
  callable invocation evidence, direct RTDB rule tightening, Firebase Console
  App Check evidence, and owner/admin GitHub security settings evidence remain
  open.

## Cycle 120 Result - 2026-06-07

- Added `distributionSha256Sum` to
  `gradle/wrapper/gradle-wrapper.properties` for the reviewed Gradle 8.12 bin
  ZIP checksum.
- Added `tools/gradle_wrapper_check.py` to validate the wrapper distribution
  URL, SHA-256, URL validation setting, storage roots, and network timeout.
- Added `test/tools/gradle_wrapper_check_test.py` coverage for the live wrapper
  and drift cases around missing checksum, checksum drift, `-all`
  distribution drift, disabled URL validation, and low network timeout.
- Wired `.github/workflows/verify.yml` so the Gradle wrapper policy check runs
  before Android setup.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-120-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 120 verification: Gradle wrapper policy check, focused wrapper policy
  tests, backend tool tests, Dependabot policy check, GitHub security workflow
  policy check, callable wire-protocol check, callable contract check,
  dependency notice lock checks, native lock check, dependency overlay check,
  dependency license policy check, high-severity Functions npm audit, diff
  hygiene, and attribution/ASCII scans passed. Future Gradle upgrades must
  update the wrapper URL, checksum, policy constant, tests, and related
  dependency-verification metadata together.

## Cycle 121 Result - 2026-06-07

- Added `docs/distribution/github-actions-allowlist.json` with reviewed
  GitHub Actions refs for all workflow `uses:` entries and the required
  workflow file set.
- Added `tools/github_actions_allowlist_check.py` to reject missing refs, local
  actions, forbidden floating refs, unreviewed actions, unexpected workflow
  files, missing workflow files, and unused allowlist entries.
- Added `test/tools/github_actions_allowlist_check_test.py` coverage for the
  live workflow set and drift cases around unreviewed actions, floating refs,
  unpinned actions, missing workflows, and unused allowlist entries.
- Wired `.github/workflows/verify.yml` so the action allowlist check runs
  before Android setup.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-121-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 121 verification: GitHub Actions allowlist check, focused allowlist
  tests, backend tool tests, Gradle wrapper policy check, Dependabot policy
  check, GitHub security workflow policy check, callable wire-protocol check,
  callable contract check, dependency notice lock checks, native lock check,
  dependency overlay check, dependency license policy check, high-severity
  Functions npm audit, diff hygiene, and attribution/ASCII scans passed.

## Cycle 122 Result - 2026-06-07

- Added `docs/distribution/github-workflow-permissions.json` with reviewed
  workflow files, allowed events, top-level permissions, job-level permissions,
  and expected job IDs for all GitHub Actions workflows.
- Added `tools/github_workflow_permissions_check.py` to reject missing or
  unexpected workflow files, unreviewed events, scalar permission declarations,
  workflow/job permission drift, missing jobs, and unexpected jobs.
- Added `test/tools/github_workflow_permissions_check_test.py` coverage for the
  live workflow set and drift cases around unreviewed events, job permission
  escalation, missing expected job permissions, unexpected jobs, and scalar
  permission declarations.
- Wired `.github/workflows/verify.yml` so the workflow permissions check runs
  before Android setup.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-122-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 122 verification: GitHub workflow permissions policy check, focused
  permissions tests, backend tool tests, GitHub Actions allowlist check, Gradle
  wrapper policy check, Dependabot policy check, GitHub security workflow
  policy check, callable wire-protocol check, callable contract check,
  dependency notice lock checks, native lock check, dependency overlay check,
  dependency license policy check, high-severity Functions npm audit, diff
  hygiene, and attribution/ASCII scans passed.

## Cycle 123 Result - 2026-06-07

- Added `docs/distribution/github-workflow-secrets.json` with reviewed workflow
  files, forbidden token shortcuts, and release signing secret/env-name pairs.
- Added `tools/github_workflow_secrets_check.py` to reject missing or
  unexpected workflow files, unreviewed secret references, unreviewed
  environment aliases, forbidden token patterns, non-env secret expressions,
  and unused policy entries.
- Added `test/tools/github_workflow_secrets_check_test.py` coverage for the
  live workflow set and drift cases around unreviewed secrets, forbidden token
  references, alias drift, missing workflows, and unused policy entries.
- Wired `.github/workflows/verify.yml` so the workflow secret policy check runs
  before Android setup.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-123-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 123 verification: GitHub workflow secret policy check, focused
  secret-policy tests, backend tool tests, GitHub workflow permissions policy
  check, GitHub Actions allowlist check, Gradle wrapper policy check,
  Dependabot policy check, GitHub security workflow policy check, callable
  wire-protocol check, callable contract check, dependency notice lock checks,
  native lock check, dependency overlay check, dependency license policy check,
  high-severity Functions npm audit, diff hygiene, and attribution/ASCII scans
  passed.

## Cycle 124 Result - 2026-06-07

- Added a `Backend tool unit tests` step to `.github/workflows/verify.yml`
  before Android setup.
- The always-on verify job now runs
  `python3 -m unittest discover -s test/tools -p '*_test.py'` on push, pull
  request, and manual verify runs.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-124-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 124 verification: backend tool tests, GitHub workflow secret policy
  check, GitHub workflow permissions policy check, GitHub Actions allowlist
  check, Gradle wrapper policy check, Dependabot policy check, GitHub security
  workflow policy check, callable wire-protocol check, callable contract check,
  dependency notice lock checks, native lock check, dependency overlay check,
  dependency license policy check, high-severity Functions npm audit, diff
  hygiene, and attribution/ASCII scans passed.

## Cycle 125 Result - 2026-06-07

- Added `tools/provider_credential_release_check.py` to validate blank provider
  `BuildConfig` defaults, blank release workflow provider-key writes, local
  nonblank provider key rejection, and explicit internal override warnings.
- Added `test/tools/provider_credential_release_check_test.py` coverage for the
  live Gradle/workflow policy, nonblank local provider key rejection, explicit
  internal override warning, missing blank release workflow assignment, and
  nonblank `BuildConfig` default drift.
- Wired `.github/workflows/verify.yml` to run the provider credential release
  guard before Android setup.
- Wired `.github/workflows/release.yml` to run the provider credential release
  guard after writing release `local.properties` and before
  `:app:assembleRelease`.
- Added the provider guard command to
  `docs/distribution/github-security-workflows.json` so release workflow policy
  validation fails if the guard is removed.
- Updated release signing, release dry-run, supply-chain, roadmap, changelog,
  completion, and loop-state docs.
- Cycle 125 verification: provider credential release guard check, focused
  provider guard tests, backend tool tests, GitHub workflow secret policy check,
  GitHub workflow permissions policy check, GitHub Actions allowlist check,
  Gradle wrapper policy check, Dependabot policy check, GitHub security
  workflow policy check, callable wire-protocol check, callable contract check,
  dependency notice lock checks, native lock check, dependency overlay check,
  dependency license policy check, high-severity Functions npm audit, diff
  hygiene, and attribution/ASCII scans passed. Full signed APK dry-run plus APK
  strings scan remains open because repeated release builds are memory-heavy on
  this Windows workstation.

## Cycle 126 Result - 2026-06-07

- Extended `CrashDiagnosticsText.sanitize()` so dotted provider credential
  assignment names, including local provider properties ending in `key` or
  `client.id`, redact values while keeping key names visible.
- Added provider-specific `CrashDiagnosticsTextTest` fixtures covering
  Wallhaven `apikey`, Pixabay `key`, Freesound `token`, SoundCloud
  `client_id`, Pexels `Authorization: Bearer`, Settings `apiKey`,
  `stability.ai.key`, `local.properties`, `file://`, and app-private paths.
- Updated `docs/support/crash-diagnostics.md`,
  `.github/ISSUE_TEMPLATE/crash_report.yml`,
  `docs/research/cycle-126-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 126 verification:
  `.\gradlew.bat --no-daemon --max-workers=2 ":app:testDebugUnitTest" --tests "com.freevibe.service.CrashDiagnosticsTextTest" --stacktrace`
  passed after the expanded fixture caught and the implementation fixed the
  missing dotted provider-property redaction.

## Cycle 127 Result - 2026-06-07

- Added `RequestRedactor` as the shared request redaction contract for bearer
  headers, bare bearer tokens, provider query values, and assignment-style
  provider credentials including dotted `local.properties` names.
- Moved `CrashDiagnosticsText.sanitize()` provider-secret redaction onto
  `RequestRedactor` after app-private path and file URI redaction.
- Updated `SourceMetrics.recordFailure()` to redact error messages before
  storing the 200-character snapshot detail displayed by Settings source
  diagnostics.
- Added focused `RequestRedactorTest` coverage for provider query/header/local
  property credentials and request formatter host/path/status output.
- Added focused `SourceMetricsTest` coverage proving authenticated provider
  URLs and header/local property values are redacted before snapshot storage.
- Updated `docs/support/crash-diagnostics.md`,
  `docs/research/cycle-127-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 127 verification:
  `.\gradlew.bat --no-daemon --max-workers=2 ":app:testDebugUnitTest" --tests "com.freevibe.service.RequestRedactorTest" --tests "com.freevibe.service.SourceMetricsTest" --tests "com.freevibe.service.CrashDiagnosticsTextTest" --stacktrace`
  passed.

## Cycle 128 Result - 2026-06-07

- Added `docs/security/network-endpoints.json` as the reviewed endpoint
  manifest for app network surfaces.
- Added `docs/security/network-endpoints.md` as the release-review runbook
  covering host, scheme, auth location, data sent, media cache, rate/cache
  policy, fallback behavior, kill switch, and owner for each surface.
- Added `tools/network_endpoint_inventory_check.py` to validate inventory
  schema, Markdown coverage, source roots, and literal HTTP(S) hosts in
  network-facing Kotlin source.
- Added `test/tools/network_endpoint_inventory_check_test.py` with live-repo
  coverage plus negative tests for unreviewed hosts, missing endpoint fields,
  and stale docs.
- Wired `.github/workflows/verify.yml` to run the endpoint inventory scanner
  before Android setup.
- Updated `docs/distribution/supply-chain.md`,
  `docs/research/cycle-128-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 128 verification: network inventory tool compile, live endpoint
  inventory scan, focused network inventory tests, full backend/tool unittest
  suite, GitHub Actions allowlist, workflow permissions policy, workflow secret
  policy, GitHub security workflow policy, Dependabot policy, Gradle wrapper
  policy, provider credential release guard without local secret inspection,
  diff hygiene, and attribution scan passed.

## Cycle 129 Result - 2026-06-07

- Removed the ccMixter HTTP fallback path from `CcMixterRepository`; TLS/API
  failures now fail closed through the HTTPS Retrofit API path and source
  metrics record the provider failure.
- Removed the `ccmixter.org` `cleartextTrafficPermitted="true"` exception from
  `network_security_config.xml`.
- Replaced the old fallback URL helper test with `CcMixterRepositoryTest`
  coverage proving an `SSLHandshakeException` propagates and records a
  ccMixter failure instead of falling back to HTTP.
- Added `tools/cleartext_release_check.py` and
  `test/tools/cleartext_release_check_test.py` so release policy fails on
  network-security cleartext exceptions, manifest cleartext enablement,
  provider `http://` URL literals, or OkHttp `.scheme("http")` builders.
- Wired `.github/workflows/verify.yml` and `.github/workflows/release.yml` to
  run the cleartext guard before Android build work, and added the release
  workflow snippet to `docs/distribution/github-security-workflows.json`.
- Updated `docs/security/network-endpoints.json`,
  `docs/security/network-endpoints.md`, `docs/distribution/supply-chain.md`,
  `docs/research/cycle-129-2026-06-07.md`, `ROADMAP.md`, `COMPLETED.md`,
  `CHANGELOG.md`, and loop state.
- Cycle 129 verification: cleartext guard compile, live cleartext scan,
  focused cleartext guard tests, focused ccMixter repository test, endpoint
  inventory scan, backend tool tests, GitHub Actions allowlist, workflow
  permissions policy, workflow secret policy, GitHub security workflow policy,
  Dependabot policy, Gradle wrapper policy, provider credential release guard
  without local secret inspection, diff hygiene, and attribution scan passed.

## Cycle 130 Result - 2026-06-07

- Added `docs/security/provider-credential-storage.json` as the
  machine-readable provider credential storage classification policy.
- Added `docs/security/provider-credential-storage.md` to document the current
  app-private DataStore storage decision, backup/device-transfer exclusions,
  no-Keystore disclosure, credential classes, and user clear controls.
- Added `tools/provider_credential_storage_check.py` and
  `test/tools/provider_credential_storage_check_test.py` so policy/docs
  coverage, DataStore preference keys, Settings clear controls, blank
  `BuildConfig` defaults, backup/device-transfer exclusions, and
  privacy/diagnostics disclosures stay checked.
- Added the missing Freesound API-key row to Settings so every DataStore-backed
  provider key has a visible edit/clear path.
- Wired `.github/workflows/verify.yml` and `.github/workflows/release.yml` to
  run the provider credential storage policy before Android build work, and
  added the release workflow snippet to
  `docs/distribution/github-security-workflows.json`.
- Updated privacy, crash diagnostics, supply-chain, provider policy, research,
  roadmap, changelog, completion, and loop-state docs.
- Cycle 130 verification: provider storage guard compile, live provider
  storage scan, focused provider storage tests, focused Settings unit
  compile/test, backend tool tests, GitHub Actions allowlist, workflow
  permissions policy, workflow secret policy, GitHub security workflow policy,
  Dependabot policy, Gradle wrapper policy, provider credential release guard
  with a blank release-style local properties fixture, cleartext guard,
  endpoint inventory scan, diff hygiene, and attribution scan passed. The
  workstation's ignored `local.properties` still contains nonblank local
  provider keys, so it was not used as public-release evidence.

## Cycle 131 Result - 2026-06-07

- Added `tools/provider_credential_apk_scan.py` to scan release APK zip entries
  for any nonblank provider credential values from a reviewed
  `local.properties` file.
- Added `test/tools/provider_credential_apk_scan_test.py` coverage for blank
  release properties, clean nonblank fixtures, embedded sentinel detection,
  multi-APK scanning, missing-file rejection, and no-secret-value error output.
- Wired `.github/workflows/release.yml` to run the provider credential APK scan
  after signed APK packaging and before notice generation, checksums, uploads,
  or tagged publication.
- Added the release workflow snippet to
  `docs/distribution/github-security-workflows.json` so workflow policy
  validation fails if the scan is removed.
- Updated release signing, release dry-run, supply-chain, research, roadmap,
  changelog, completion, and loop-state docs.
- Cycle 131 verification: provider APK scanner compile, focused scanner tests,
  backend tool tests, GitHub Actions allowlist, workflow permissions policy,
  workflow secret policy, GitHub security workflow policy, Dependabot policy,
  Gradle wrapper policy, provider credential release guard with a blank
  release-style local properties fixture, provider credential storage policy,
  cleartext guard, and endpoint inventory scan passed.

## Cycle 132 Result - 2026-06-07

- Added a shared `ProviderApiKeyDialog` for Settings provider key entry with
  Save, Clear, and Cancel actions.
- Routed Wallhaven, Pexels, Pixabay, Freesound, and Stability key dialogs
  through the shared dialog so every stored provider key has an explicit Clear
  action instead of relying only on saving a blank value.
- Extended `tools/provider_credential_storage_check.py` and
  `test/tools/provider_credential_storage_check_test.py` so the policy fails if
  the Settings provider-key Clear action disappears.
- Updated provider credential storage policy/docs, privacy copy, supply-chain
  docs, research, roadmap, changelog, completion, and loop state.
- Cycle 132 verification: provider storage guard compile, live provider
  storage scan, focused provider storage tests, backend tool tests, focused
  Settings unit compile/test, GitHub Actions allowlist, workflow permissions
  policy, workflow secret policy, GitHub security workflow policy, Dependabot
  policy, Gradle wrapper policy, provider credential release guard with a blank
  release-style local properties fixture, cleartext guard, and endpoint
  inventory scan passed.

## Cycle 133 Result - 2026-06-07

- Added a DataStore-backed generated wallpaper disclosure acceptance flag.
- Blocked Stability generation requests until the generated-content provider is
  enabled, prompt text is present, a Stability key is available, and the
  disclosure is accepted.
- Added a reusable generated wallpaper disclosure dialog to the generation flow
  and Settings review/reset path.
- Documented generated wallpaper prompt sharing, provider key/credit use, local
  storage, diagnostics redaction expectations, and release checklist in
  `docs/privacy/ai-generation.md`, and linked it from the privacy policy.
- Updated roadmap, changelog, completion, research, and loop-state docs.
- Cycle 133 verification: focused Android request-gate and Settings ViewModel
  unit tests, backend tool tests, provider credential storage policy, GitHub
  Actions allowlist, workflow permissions policy, workflow secret policy,
  GitHub security workflow policy, Dependabot policy, Gradle wrapper policy,
  provider credential release guard with a blank release-style local properties
  fixture, cleartext guard, and endpoint inventory scan passed.

## Cycle 134 Result - 2026-06-07

- Added generated-content report reasons for Offensive, Unsafe, Deceptive, and
  Other generated wallpaper reports.
- Exposed report actions for freshly generated wallpaper results and saved
  generated wallpaper favorites.
- Routed generated reports through the existing private report queue without
  including Stability keys, other provider keys, local generated-image file
  paths, or prompt text unless the user writes prompt text in the note.
- Allowed generated wallpaper reports independently of the Community source
  switch while preserving the existing Community switch guard for non-generated
  provider/community reports.
- Extended the Functions `submitCommunityReport` reason allowlist and focused
  backend test coverage for generated-content reports.
- Updated privacy, support, research, roadmap, changelog, completion, and
  loop-state docs.
- Cycle 134 verification: focused Android CommunityReport, AI wallpaper request
  gate/report payload, and WallpapersViewModel unit tests; Functions build and
  tests; backend tool tests; GitHub Actions allowlist; workflow permissions
  policy; workflow secret policy; GitHub security workflow policy; Dependabot
  policy; Gradle wrapper policy; provider credential release guard with a blank
  release-style local properties fixture; provider credential storage policy;
  cleartext guard; and endpoint inventory scan passed.

## Cycle 135 Result - 2026-06-07

- Extended `tools/provider_credential_storage_check.py` with an exact
  `stability-ai-key` sentinel for Stability's paid-sensitive credential
  posture.
- Required Stability to remain a DataStore-backed `paidSensitiveSecret` with
  blank `STABILITY_AI_KEY` / `stability.ai.key` public release defaults,
  explicit Clear control documentation, `stability.ai.key` redaction coverage,
  and the documented no-Keystore decision.
- Added focused policy tests for Stability classification drift and missing
  redaction coverage.
- Updated provider credential storage policy, crash diagnostics, supply-chain,
  research, roadmap, changelog, completion, and loop-state docs.
- Cycle 135 verification: focused provider credential tool tests, live provider
  credential storage policy scan, and provider credential release guard with a
  blank release-style local properties fixture passed.

## Cycle 136 Result - 2026-06-07

- Added ViewModel-level in-flight request rejection so rapid direct calls cannot
  cancel and replace an active Stability generation request.
- Added local session request counting and visible generated wallpaper credit
  copy.
- Added same prompt/style duplicate detection after a successful generation and
  a confirmation dialog before sending another matching Stability request.
- Updated 402 and 429 Stability error copy with account/cooldown actions.
- Updated generated wallpaper privacy/runbook docs, research, roadmap,
  changelog, completion, and loop-state docs.
- Cycle 136 verification: focused generated wallpaper request-gate and
  Stability error-copy unit tests passed.

## Cycle 137 Result - 2026-06-07

- Stopped mining prompt words into generated wallpaper tags.
- Saved generated favorites with the generic `Generated wallpaper` name and
  sanitized tags limited to reviewed non-prompt generated values.
- Deleted generated PNG files when AI-generated wallpaper favorites are removed
  through the wallpaper favorite toggle.
- Deleted generated PNG files after Favorites bulk-delete Undo closes without a
  restore.
- Updated generated wallpaper privacy docs, research, roadmap, changelog,
  completion, and loop-state docs.
- Cycle 137 verification: focused generated wallpaper prompt-retention tests
  and AI-generated favorite removal cleanup tests passed.

## Cycle 138 Result - 2026-06-07

- Added `tools/store_metadata_preflight.py` for Fastlane store metadata
  title, short description, full description, privacy-policy URL, stale
  branding, current versionCode changelog, and current versionName checks.
- Added a tested optional asset mode for future Fastlane icon,
  feature-graphic, and phone screenshot dimensions plus no-alpha requirements.
- Wired text-mode store metadata preflight into verify and release workflows
  before Android setup or signed APK assembly.
- Updated the release workflow security policy so the release preflight keeps
  the store metadata guard.
- Shortened the Fastlane short description to fit Google Play's 80-character
  limit and added the public privacy-policy URL to the full description.
- Updated release/supply-chain docs, research, roadmap, changelog, completion,
  and loop-state docs.
- Cycle 138 verification: focused store metadata preflight, backend tool tests,
  workflow policy checks, provider/network/security policy checks, and
  cleartext/endpoint/dependabot/wrapper guards passed.

## Cycle 139 Result - 2026-06-07

- Added `docs/ai/on-device-wallpaper-decision.json` and
  `docs/ai/on-device-wallpaper-decision.md` with the current `hold` decision.
- Required evidence for device baseline, model delivery/storage,
  latency/battery/thermal behavior, license redistribution, moderation and
  reporting, hosted/BYO fallback, and FOSS-channel impact before local
  wallpaper generation can move toward production.
- Added `tools/on_device_ai_decision_check.py` and focused tests to validate
  the decision packet, reject premature approval, require HTTPS sources and
  evidence refs, and block early production on-device runtime dependencies or
  model artifacts.
- Wired the guard into the verify workflow before Android setup.
- Updated generated wallpaper privacy docs, supply-chain docs, research,
  roadmap, changelog, completion, and loop-state docs.
- Cycle 139 verification: on-device decision policy check and backend tool
  tests passed.

## Cycle 140 Result - 2026-06-07

- Added Settings > About > Privacy policy, opening the public policy URL tracked
  in `docs/privacy/privacy-policy.md`.
- Added `docs/privacy/privacy-policy-link.json` and
  `tools/privacy_policy_link_check.py` to verify the public privacy-policy URL
  remains HTTPS and appears in Settings, README, Fastlane metadata, verify and
  release workflows, and release dry-run docs.
- Wired the privacy-link gate into verify and release workflows before Android
  setup or signed APK assembly.
- Updated release workflow security policy, release signing docs, release
  dry-run docs, supply-chain docs, README, privacy policy wording, research,
  roadmap, changelog, completion, and loop-state docs.
- Cycle 140 verification: privacy-link policy check, backend tool tests, and a
  focused Settings Gradle unit-test compile pass succeeded.

## Cycle 141 Result - 2026-06-07

- Added `docs/privacy/data-safety.md` with a release-reviewed manifest
  permission ledger and Data safety answer notes.
- Added `docs/privacy/data-safety.json` with one structured row for each
  manifest permission, including purpose, triggering user action, data types,
  collection/sharing status, retention, deletion path, denial behavior, and
  Play declaration posture.
- Added `tools/privacy_data_safety_check.py` and focused tests to parse
  `AndroidManifest.xml`, require exact permission and `maxSdkVersion` coverage,
  require complete reviewed rows, and ensure the Markdown matrix lists every
  permission.
- Wired the Data safety matrix check into verify and release workflows before
  Android setup or signed APK assembly.
- Updated release workflow security policy, release signing docs, release
  dry-run docs, supply-chain docs, privacy policy, research, roadmap,
  changelog, completion, and loop-state docs.
- Cycle 141 verification: Data safety matrix check and focused backend tests
  passed.

## Cycle 142 Result - 2026-06-07

- Added `networkEndpointInventory` and `networkSurfaces` coverage to
  `docs/privacy/data-safety.json`.
- Added a Network Surface Ledger to `docs/privacy/data-safety.md`.
- Extended `tools/privacy_data_safety_check.py` so every endpoint ID in
  `docs/security/network-endpoints.json` must have one matching privacy row.
- Required data types, collection/sharing status, user control, retention, and
  deletion path for each reviewed network surface.
- Extended focused tests for missing and extra network surface rows.
- Updated release dry-run docs, supply-chain docs, research, roadmap,
  changelog, completion, and loop-state docs.
- Cycle 142 verification: expanded Data safety matrix check and focused backend
  tests passed.

## Next Cycle

Continue this same assigned project, Aura. Start Cycle 143 from the
`ROADMAP.md` Continuation State and
`docs/research/cycle-142-2026-06-07.md`. The account
deletion dry-run planner, read-only Settings identity surface, redacted
shareable request draft, request-code lookup tool, review receipt gate, offline
apply simulator, private executor package builder, and guarded REST executor
are implemented; Cycle 82 added the redacted completion receipt after applied
REST receipts; Cycle 83 added the private web-intake contract and validator;
Cycle 84 added local/Auth cleanup sequencing after backend completion; Cycle 85
added Settings > Community identity > Clear local for the current device
fallback identity; Cycle 86 added private Firebase Auth deletion packages after
lookup and backend completion evidence match; Cycle 87 added private
public-upload handoff plans for owned upload rows and blocked handle review;
Cycle 88 added the privacy-policy-backed hosted URL manifest gate for pending
owner publication; Cycle 89 added a redacted Auth execution receipt for
owner-approved private deletion evidence after post-delete not-found
verification; Cycle 90 added a redacted upload execution receipt for
owner/admin public-upload deletion workflow evidence after clean plans; Cycle
91 added a machine-checked callable contract manifest with UTC quota-day
boundary; Cycle 92 added checked hosted deletion page copy for owner
publication; Cycle 93 added the Node 20 Cloud Functions scaffold, fail-closed
community callable exports, a manifest-synced Functions contract mirror, and a
UTC quota decision engine; Cycle 94 added the handler-backed
`submitCommunityReport` callable with focused quota, dedupe, Auth, App Check,
and payload validation tests; Cycle 95 added the handler-backed
`recordCommunityVote` callable with focused existing-vote idempotency, quota,
dedupe, Auth, App Check, and content-ID validation tests; Cycle 96 added the
handler-backed `setCreatorFollow` callable with focused follow/unfollow
idempotency, action-specific dedupe, quota, Auth, App Check, and payload
validation tests; Cycle 97 added the handler-backed `setCommunityUserBlock`
callable with focused block/unblock idempotency, action-specific dedupe, quota,
Auth, App Check, and payload validation tests; Cycle 98 added the
handler-backed `finalizeCommunitySoundUpload` callable with focused metadata
normalization, storage-path ownership, storage-path dedupe, quota, Auth, App
Check, and payload validation tests; Cycle 99 added the handler-backed
`finalizeCommunityWallpaperUpload` callable with focused metadata
normalization, storage-path ownership, storage-path dedupe, quota, Auth, App
Check, and payload validation tests; Cycle 100 added the handler-backed
`updateCreatorProfile` callable with focused public profile normalization,
normalized-profile dedupe, quota, Auth, App Check, and payload validation
tests; Cycle 101 added RTDB-emulator-backed `updateCreatorProfile` handler
persistence coverage for profile, quota, dedupe, and unchanged-profile
idempotency writes; Cycle 102 added RTDB-emulator-backed
`submitCommunityReport` handler persistence coverage for report, quota,
dedupe, and duplicate report writes; Cycle 103 added RTDB-emulator-backed
`recordCommunityVote` handler persistence coverage for vote tally, nested
voter, legacy voter, quota, dedupe, and repeat-vote idempotency writes; Cycle
104 added RTDB-emulator-backed `setCreatorFollow` handler persistence coverage
for follow writes, unfollow removals, quota, dedupe, and missing-unfollow
idempotency writes; Cycle 105 added RTDB-emulator-backed
`setCommunityUserBlock` handler persistence coverage for private block rows,
reverse-index rows, unblock removals, quota, dedupe, and missing-unblock
idempotency writes; Cycle 106 added RTDB-emulator-backed
`finalizeCommunitySoundUpload` handler persistence coverage for public
metadata, owner index, quota, storage-path dedupe, and duplicate upload
idempotency writes; Cycle 107 added RTDB-emulator-backed
`finalizeCommunityWallpaperUpload` handler persistence coverage for public
metadata, owner index, quota, storage-path dedupe, and duplicate upload
idempotency writes; Cycle 108 added the Android report callable client adapter
and callable-first report submission with a compatibility direct-write fallback
until deploy evidence is available; Cycle 109 added the Android vote callable
payload/client adapter and callable-first vote submission with compatibility
fallback only for missing callable endpoint or missing Firebase Auth; Cycle 110
added the Android follow callable payload/client adapter and callable-first
follow/unfollow submission with compatibility fallback only for missing
callable endpoint or missing Firebase Auth; Cycle 111 added the Android
user-block callable payload/client adapter and callable-first block/unblock
submission with compatibility fallback only for missing callable endpoint or
missing Firebase Auth; Cycle 112 added the Android sound upload finalizer
callable payload/client adapter and callable-first metadata finalization after
Storage upload with compatibility fallback only for missing callable endpoint
or missing Firebase Auth; Cycle 113 added the Android wallpaper upload
finalizer callable payload/client adapter and callable-first metadata
finalization after Storage upload with compatibility fallback only for missing
callable endpoint or missing Firebase Auth; Cycle 114 added the Android
profile edit callable payload/client adapter, callable-first creator profile
updates with compatibility fallback only for missing callable endpoint or
missing Firebase Auth, and creator profile edit UI; Cycle 115 added checked
Android callable wire-protocol coverage for all seven contracted callable
client surfaces; Cycle 116 added a redacted receipt gate for future
owner-provided live callable rollout evidence; Cycle 117 added a checked
GitHub security workflow policy guard for Dependency Review, OpenSSF Scorecard,
and Release workflow drift; Cycle 118 added checked Dependabot version-update
coverage for GitHub Actions, Gradle, root npm, and Functions npm; Cycle 119
added a redacted receipt gate for future owner/admin GitHub evidence; Cycle
120 pinned the Gradle wrapper ZIP checksum and added a wrapper policy guard;
Cycle 121 added a repository-wide GitHub Actions allowlist guard; Cycle 122
added a repository-wide GitHub workflow permissions guard; Cycle 123 added a
repository-wide GitHub workflow secret guard; Cycle 124 wired backend/tool
unit tests into the always-on verify job before Android setup; Cycle 125 added
a provider credential release guard for `BuildConfig` and `local.properties`;
Cycle 126 added provider-specific crash diagnostics redaction fixtures and
dotted provider-property assignment redaction; Cycle 127 added a shared request
redactor for crash diagnostics and source metrics error details; Cycle 128
added a checked network endpoint inventory runbook and literal-host scanner;
Cycle 129 removed the ccMixter HTTP downgrade path and added release cleartext
policy gates; Cycle 130 added a checked provider credential storage policy,
documented the no-Keystore decision for current optional provider keys, and
added the missing Freesound Settings clear control; Cycle 131 added a
release-stage provider credential APK scan after signed APK packaging; Cycle
132 added explicit provider key Clear actions and storage-policy coverage;
Cycle 133 added a persisted generated wallpaper disclosure gate and Settings
review/reset path before Stability requests; Cycle 134 added generated-content
report actions and callable reason support; Cycle 135 added a Stability
paid-secret sentinel to the provider credential storage guard; Cycle 136 added
generated wallpaper session request counting, duplicate prompt/style
confirmation, in-flight request rejection, and Stability account/cooldown error
copy; Cycle 137 removed prompt-derived names/tags from generated favorites and
added generated-file deletion on favorite removal; Cycle 138 added checked
Fastlane store metadata text/version/privacy preflight and fixed the current
short-description/privacy URL drift; Cycle 139 added a checked on-device
wallpaper generation decision packet and production-source scanner; Cycle 140
added a checked public privacy-policy link gate and Settings About entry; Cycle
141 added a checked manifest permission Data safety matrix and release gate;
Cycle 142 extended the Data safety matrix gate to every reviewed network
endpoint ID.
Actual live callable invocation evidence, a live hosted HTTPS web deletion URL,
direct RTDB rule tightening, App Check console evidence, production-project
dry-run evidence, and owner/admin GitHub repository security-settings evidence
remain open.
Next collect owner-approved live callable invocation evidence and generate the
redacted rollout receipt when deploy/App Check access is available, publish the
hosted URL after owner approval, tighten direct RTDB write rules after callable
deploy evidence, run a real production-project Firebase executor dry-run after
owner access is confirmed, collect owner/admin GitHub security settings
evidence and generate the redacted receipt when access is available, or
continue with signed release dry-run evidence capture on a suitable runner,
the screenshot and feature-graphic pipeline for the remaining store metadata
asset gap, expanded Data safety matrix enforcement for SDK/local-store/
diagnostics/Firebase surfaces, Play app-content declaration packet work, or the
next checkable backend, deploy, security, support, policy, or rules hardening
artifact if owner-gated evidence is still unavailable.
Commit and push completed work when the active project contract allows it.

## Previous Cycle Prompt

Continue this same assigned project, Aura. Start Cycle 67 from the `ROADMAP.md` Continuation State and `docs/research/cycle-66-2026-06-06.md`. The Google OSS notices plugin-only path is implemented; `tools/google_oss_to_markdown.py` generates `THIRD-PARTY-NOTICES.md`; `tools/google_oss_raw_archive.py` archives raw Google OSS inputs and the repo now keeps `GOOGLE-OSS-RAW-INPUTS.zip` attached to tagged public releases; `GeneratedDependencyNotices.kt` parses generated raw resources for an in-app notice viewer with search and review markers; `tools/native_compliance_inventory.py` generates `NATIVE-COMPLIANCE.md`, extracts embedded FFmpeg configure evidence, and gates native evidence drift; `tools/dependency_notice_lock.py` gates generated release notice drift and raw metadata parity; `tools/dependency_overlay_check.py` gates curated high-risk dependency/native-payload review metadata; `tools/dependency_license_policy.py` gates allowed, review-required, disallowed, and unknown curated license IDs; `tools/release_artifact_bundle_check.py` gates final release bundle consistency; `ProviderDisclosure.kt` now has checked runtime-control rows for every content source; YouTube, Reddit, Wallhaven, Pexels, Pixabay, Community, Bing Daily, and generated wallpapers now have runtime provider/source switches that block active fetch/resolver/upload/action paths before remote calls or bundled key reads where applicable; Pixabay photo and video metadata now have 24-hour request caches and 429 backoff; favorites/download history now have persisted unavailable-source states for saved local copies; Pexels is now enhancement-only in wallpaper Discover and video-wallpaper discovery; explicit removed/gone provider failures now reconcile saved favorite and download-history source states; sounds now have normalized license action gates and saved favorite license preservation; community sound and wallpaper uploads now require selected license metadata, rights attestation, uploader UID, timestamp, optional HTTPS source URL, canonical `storagePath`, and private owner-index rows before public metadata is written; repository owner delete methods can remove new upload blobs plus public metadata/index rows; sound and wallpaper detail surfaces now show confirmed owner-only delete actions when owner metadata and `storagePath` prove a new upload is deletable; collection shares now write `createdByUid` and use owner/admin RTDB rules under `shared_collections`; tracked Storage rules and local emulator tests now cover community upload blob authorization; RTDB rules and local emulator tests now cover community upload metadata, owner indexes, reports, report resolutions, takedown receipts/delete states, quota/dedupe ledgers, and app-matched `shared_collections`; the main verify workflow now runs the combined Firebase rules suite, backend manifest check, and backend tool unittests for lifecycle/backfill changes; `docs/community-backend-runbook.md` covers preflight, dry run, deploy, rollback, App Check rollback separation, and release evidence; `docs/community-storage-lifecycle-policy.md` blocks automatic deletes on committed upload prefixes and requires two matching orphan reports before manual cleanup; `docs/community-upload-backfill.md` defines dry-run legacy backfill planning for missing `storagePath` and owner-index rows; community reports now have private intake, admin review, status filters for open/closed queues, moderation hide/unhide actions, admin resolution metadata paths, rights-confirmed takedown receipts, and confirmed admin delete actions tied to current upload deletion handles; App Check client providers are installed for debug and release builds with a rollout runbook; community write quotas now have typed policy rows, protected admin-only RTDB quota/dedupe ledgers, callable function names, payload schemas, final write path contracts, protected ledger path coverage, and limited-use App Check token decisions. Next add deletion retention policy, block-user policy, a real production-project Firebase backend dry run/orphan/backfill evidence pass after owner access is confirmed, Cloud Functions implementation for the callable quota contract, or Android callable migration adapters. Keep AboutLibraries secondary: 14.2.1 configures, but default exports were incomplete and the compliance export logged Windows path errors; do not use AboutLibraries 15.x until N-1 upgrades AGP because v15 requires AGP 8.13. Commit and push completed work when the active project contract allows it.
