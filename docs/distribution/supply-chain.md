# Supply-chain verification

Aura is side-loaded through GitHub Releases and Obtainium, so release artifacts need proof that is stronger than a plain APK attachment.

## Active controls

| Control | Location | Purpose |
| --- | --- | --- |
| Signed release APK | `.github/workflows/release.yml` | Builds the release variant, verifies the signature, rejects debuggable APKs, and publishes checksums. |
| Release bundle validator | `tools/release_artifact_bundle_check.py`, `.github/workflows/release.yml` | Fails manual dry runs and tag releases when the final APK/notices/native/checksum/release-note bundle is incomplete or internally inconsistent. |
| Third-party notices | `tools/google_oss_to_markdown.py`, `.github/workflows/release.yml` | Runs the Google OSS Licenses Gradle task for the release variant and publishes `THIRD-PARTY-NOTICES.md` next to the APK. |
| Raw Google OSS inputs | `tools/google_oss_raw_archive.py`, `.github/workflows/release.yml` | Archives generated `dependencies.json`, license metadata, and raw license text inputs for drift review. |
| Dependency notice lockfile | `tools/dependency_notice_lock.py`, `docs/legal/dependency-notices.lock.json` | Fails PR/main/release checks when generated release dependency notices drift without review. |
| Dependency notice overlay | `tools/dependency_overlay_check.py`, `docs/legal/dependency-notice-overrides.json` | Requires curated source, license, usage, and release-review metadata for high-risk generated dependencies and native payloads. |
| Dependency license policy | `tools/dependency_license_policy.py`, `docs/legal/dependency-license-policy.json` | Fails PR/main/release checks when curated dependency or native-payload license IDs are disallowed, unknown, or missing required review notes. |
| Native compliance packet | `tools/native_compliance_inventory.py`, `.github/workflows/release.yml` | Inventories youtubedl-android, yt-dlp/Python, FFmpeg, QuickJS, and NewPipeExtractor payload evidence and publishes `NATIVE-COMPLIANCE.md` next to the APK. |
| Native compliance lockfile | `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.lock.json` | Fails PR/main/release checks when native/copyleft artifact hashes or extracted payload facts drift without review. |
| FFmpeg source correspondence checklist | `docs/legal/ffmpeg-source-correspondence.md` | Records resolved FFmpeg AAR/payload hashes, embedded version/configure evidence, source candidates, and remaining owner actions for source correspondence. |
| Artifact attestation | `.github/workflows/release.yml` | Uses `actions/attest@v4` against `release/SHA256SUMS.txt` so release artifact digests are bound to the GitHub Actions build. |
| Gradle dependency verification | `gradle/verification-metadata.xml` | Records SHA-256 checksums for resolved Gradle plugins and app dependencies. |
| Dependency Review | `.github/workflows/dependency-review.yml` | Runs on pull requests and fails high/critical vulnerable dependency additions. |
| OpenSSF Scorecard | `.github/workflows/scorecard.yml` | Runs on main pushes, branch-protection changes, weekly schedule, and manual dispatch; keeps public result publishing disabled and uploads SARIF to code scanning. |
| F-Droid blocker preflight | `tools/fdroid_preflight.py` | Confirms that F-Droid mainline remains blocked until proprietary dependency boundaries change. |

## Release verification

For each `v*` release:

1. Confirm the GitHub Release contains `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
2. Confirm the GitHub Release contains `THIRD-PARTY-NOTICES.md`.
3. Confirm the GitHub Release contains `GOOGLE-OSS-RAW-INPUTS.zip`.
4. Confirm the GitHub Release contains `NATIVE-COMPLIANCE.md`.
5. Confirm `SHA256SUMS.txt` includes the APK, `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, and `NATIVE-COMPLIANCE.md`.
6. Confirm the generated release notes include the APK SHA-256, third-party notices entry, raw Google OSS input archive entry, native compliance packet entry, signing certificate SHA-256, and artifact attestation URL.
7. Spot-check `THIRD-PARTY-NOTICES.md` for high-risk dependencies such as NewPipeExtractor, youtubedl-android, Firebase, Play services ML Kit, ZXing, Palette, and ProfileInstaller.
8. Spot-check `GOOGLE-OSS-RAW-INPUTS.zip` for `dependencies.json`, `third_party_license_metadata`, `third_party_licenses`, and `MANIFEST.json`.
9. Spot-check `docs/legal/dependency-notice-overrides.json` when any high-risk dependency or payload changed intentionally.
10. Spot-check `docs/legal/dependency-license-policy.json` when a curated overlay license ID, required coordinate prefix, required payload target, or disallowed pattern changes.
11. Spot-check `NATIVE-COMPLIANCE.md` for youtubedl-android library, youtubedl-android ffmpeg, yt-dlp, Python, QuickJS, FFmpeg, and NewPipeExtractor evidence.
12. Spot-check `docs/legal/ffmpeg-source-correspondence.md` when any FFmpeg payload hash, version, configure evidence, or youtubedl-android FFmpeg coordinate changes.
13. Confirm the release workflow ran `tools/release_artifact_bundle_check.py` before uploading artifacts.
14. Verify the APK locally with `apksigner verify --verbose --print-certs`.
15. Compare the local SHA-256 values to `SHA256SUMS.txt`.

## Release dry runs

Manual `workflow_dispatch` runs on `main` are Aura's release dry-run lane. They build the signed release APK, generate third-party notices, archive the raw Google OSS inputs, generate the native compliance packet, run the lock/overlay gates, produce checksums/release notes, validate the final bundle, and upload the result as a workflow artifact without creating a GitHub Release.

Procedure: [release-dry-run.md](release-dry-run.md).

## Third-party notices

The release workflow uses Google's OSS Licenses Gradle plugin only. It intentionally does not add the `play-services-oss-licenses` runtime dependency or stock Google notice activity, because that runtime path was found to pull broad UI dependency upgrades on the current AGP 8.7.3 / Gradle 8.12 stack.

Generate notices locally after the release notice task has run:

```powershell
.\gradlew.bat :app:releaseOssLicensesTask --stacktrace --no-daemon
python tools\dependency_notice_lock.py --mode check --lockfile docs\legal\dependency-notices.lock.json
python tools\dependency_overlay_check.py --overlay docs\legal\dependency-notice-overrides.json
python tools\dependency_license_policy.py --policy docs\legal\dependency-license-policy.json --overlay docs\legal\dependency-notice-overrides.json
python tools\google_oss_to_markdown.py --variant release --output build\reports\THIRD-PARTY-NOTICES.md
python tools\google_oss_raw_archive.py --variant release --output build\reports\GOOGLE-OSS-RAW-INPUTS.zip
```

Generated dependency notices do not replace Aura's content-source disclosures. `ProviderDisclosure.kt` remains the source of truth for provider policy rows such as YouTube, Reddit, Pexels, Pixabay, community uploads, bundled media, and AI-generated content. Settings > Open source licenses links users to the latest release notice artifacts while keeping provider disclosures visible in-app.

## Raw Google OSS inputs

`GOOGLE-OSS-RAW-INPUTS.zip` preserves the exact generated files used by the markdown converter and dependency notice lock:

- `dependencies.json`
- `third_party_license_metadata`
- `third_party_licenses`
- `MANIFEST.json`

The manifest records source paths, file sizes, and SHA-256 digests for each raw input. Keep this archive beside `THIRD-PARTY-NOTICES.md` in workflow artifacts and tagged GitHub Releases so future drift reviews can inspect the raw Google OSS output without rerunning Gradle.

## Dependency notice lockfile

`docs/legal/dependency-notices.lock.json` is generated from Google's release OSS outputs. It records:

- Sorted release dependency coordinates from `dependencies.json`.
- SHA-256 hashes for the generated dependency, license metadata, and license text inputs.
- Notice section names, offsets, lengths, and notice-text SHA-256 hashes.

PR/main verification and the release workflow run:

```bash
python3 tools/dependency_notice_lock.py --mode check --lockfile docs/legal/dependency-notices.lock.json
python3 tools/native_compliance_inventory.py --mode check-lock --lockfile docs/legal/native-compliance.lock.json
python3 tools/dependency_overlay_check.py --overlay docs/legal/dependency-notice-overrides.json
python3 tools/dependency_license_policy.py --policy docs/legal/dependency-license-policy.json --overlay docs/legal/dependency-notice-overrides.json
```

When a dependency, version, or generated notice text changes intentionally, refresh the lockfile after rerunning `:app:releaseOssLicensesTask`:

```powershell
python tools\dependency_notice_lock.py --mode write --lockfile docs\legal\dependency-notices.lock.json
```

Review the lockfile diff in the same change as the dependency update. Do not regenerate it as a standalone cleanup unless the generated Google outputs are unchanged and the prior lockfile was stale.

## Dependency notice overlay

`docs/legal/dependency-notice-overrides.json` fills the review gap that generated Google OSS outputs leave open: source URLs, precise license IDs, usage descriptions, and release-owner review notes for dependencies and payloads that deserve manual attention before publishing a side-loaded APK.

The overlay currently covers:

- Firebase Android SDK coordinates.
- Google Play services and the ML Kit subject-segmentation coordinate.
- NewPipeExtractor and youtubedl-android coordinate families.
- Embedded yt-dlp, Python, QuickJS, and FFmpeg payloads found by the native-compliance lock.
- ProfileInstaller and ZXing.

PR/main verification and the release workflow run:

```bash
python3 tools/dependency_overlay_check.py --overlay docs/legal/dependency-notice-overrides.json
```

The check fails when a required high-risk entry is missing, an entry uses an unsupported target type, an entry lacks required metadata, an entry source URL is not HTTPS, or an entry target no longer maps to the current dependency/native locks. When a covered coordinate or payload changes intentionally, update the overlay source URL, license ID, usage, and review note in the same commit as the dependency change.

## Dependency license policy

`docs/legal/dependency-license-policy.json` is the release-owner policy for curated license IDs. Google's generated OSS notice lock records dependency coordinates and notice hashes, but it does not expose normalized SPDX license IDs. Aura therefore gates license policy through the curated overlay and native payload facts that already map high-risk coordinates and embedded payloads to owner-reviewed license IDs.

The policy currently:

- Allows reviewed permissive IDs such as Apache-2.0 and MIT.
- Requires release-owner review notes for GPL, Google SDK terms, PSF-plus-bundled-dependency, and mixed Unlicense payload entries.
- Fails unknown or disallowed license IDs in `docs/legal/dependency-notice-overrides.json`.
- Requires overlay coverage for the current Firebase, Google Play services, ML Kit, NewPipeExtractor, youtubedl-android, ProfileInstaller, ZXing, FFmpeg, Python, QuickJS, and yt-dlp surfaces.
- Scans extracted native payload facts for disallowed release modes such as nonfree FFmpeg flags.

PR/main verification and the release workflow run:

```bash
python3 tools/dependency_license_policy.py --policy docs/legal/dependency-license-policy.json --overlay docs/legal/dependency-notice-overrides.json
```

When a dependency or payload changes intentionally, update the generated locks, curated overlay, and policy file together. A new license ID should either be listed as allowed, listed as review-required with a review note in the overlay, or rejected by a disallowed pattern.

## Native compliance packet

`NATIVE-COMPLIANCE.md` is a factual inventory, not legal advice. It reads resolved Gradle cache artifacts and, in release CI, the final signed APK. It records artifact hashes, ABI payload paths, nested yt-dlp/Python facts, FFmpeg payload entries, and upstream source/build references that release owners must review.

`docs/legal/ffmpeg-source-correspondence.md` is the FFmpeg-specific release review checklist. It records the resolved `ffmpeg-0.18.1.aar` hash, nested `libffmpeg.zip.so` hashes, embedded FFmpeg 7.1.1 configure lines, the FFmpeg source candidate, and the remaining Termux-source/build-log evidence that owners must confirm before publishing changed FFmpeg payloads.

Generate the local packet after dependencies have been resolved:

```powershell
python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json
python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md
```

Release CI adds final APK inspection:

```bash
python3 tools/native_compliance_inventory.py --apk "$RELEASE_DIR/Aura-vX.Y.Z-versionCode-N-universal-release.apk" --output "$RELEASE_DIR/NATIVE-COMPLIANCE.md"
```

Treat a youtubedl-android, NewPipeExtractor, yt-dlp, Python, QuickJS, or FFmpeg version change as a required native packet refresh. The resolved FFmpeg AAR now exposes embedded configure lines, but FFmpeg remains a release-owner review item until the exact Termux package commit, patches, dependency source set, and build logs are tied to the shipped binaries.

When a native/copyleft artifact or payload changes intentionally, refresh the lockfile and markdown packet together:

```powershell
python tools\native_compliance_inventory.py --mode write-lock --lockfile docs\legal\native-compliance.lock.json
python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md
```

Review the artifact hashes, payload facts, and FFmpeg notes in the same change as the dependency update.

When FFmpeg payload facts change, refresh `docs/legal/ffmpeg-source-correspondence.md` in the same change and keep the unresolved-owner-action section accurate.

## Gradle dependency verification

Gradle checksum metadata is committed at `gradle/verification-metadata.xml`. Regenerate it only during a dependency-resolution maintenance pass, not by hand.

Use Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
.\gradlew.bat --write-verification-metadata sha256 :app:dependencies --stacktrace --no-daemon
```

Then review and commit the resulting diff. Future dependency changes should update `gradle/verification-metadata.xml` in the same commit as the version/catalog change.

Do not use `--dependency-verification=off` in CI or release workflows. If checksum verification fails, investigate dependency drift instead of suppressing it.

Clean runners can resolve additional metadata files that a local cache does not surface. If CI fails on missing module/POM checksums, refresh metadata with the same command above plus `--refresh-dependencies`, then review the generated XML diff before committing it.

## SBOM scope

SBOM generation is deferred until after the N-1 toolchain upgrade because the Android dependency graph is already scheduled for a large AGP/Gradle/Kotlin/KSP migration. When N-1 lands, add a CycloneDX or SPDX SBOM lane that covers:

- Gradle plugins and version-catalog dependencies.
- Runtime APK dependencies for the release variant.
- Native/FFmpeg/youtubedl-android artifacts.
- The release APK digest and signing certificate fingerprint.
