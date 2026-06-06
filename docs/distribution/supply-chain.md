# Supply-chain verification

Aura is side-loaded through GitHub Releases and Obtainium, so release artifacts need proof that is stronger than a plain APK attachment.

## Active controls

| Control | Location | Purpose |
| --- | --- | --- |
| Signed release APK | `.github/workflows/release.yml` | Builds the release variant, verifies the signature, rejects debuggable APKs, and publishes checksums. |
| Third-party notices | `tools/google_oss_to_markdown.py`, `.github/workflows/release.yml` | Runs the Google OSS Licenses Gradle task for the release variant and publishes `THIRD-PARTY-NOTICES.md` next to the APK. |
| Dependency notice lockfile | `tools/dependency_notice_lock.py`, `docs/legal/dependency-notices.lock.json` | Fails PR/main/release checks when generated release dependency notices drift without review. |
| Dependency notice overlay | `tools/dependency_overlay_check.py`, `docs/legal/dependency-notice-overrides.json` | Requires curated source, license, usage, and release-review metadata for high-risk generated dependencies and native payloads. |
| Native compliance packet | `tools/native_compliance_inventory.py`, `.github/workflows/release.yml` | Inventories youtubedl-android, yt-dlp/Python, FFmpeg, QuickJS, and NewPipeExtractor payload evidence and publishes `NATIVE-COMPLIANCE.md` next to the APK. |
| Native compliance lockfile | `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.lock.json` | Fails PR/main/release checks when native/copyleft artifact hashes or extracted payload facts drift without review. |
| Artifact attestation | `.github/workflows/release.yml` | Uses `actions/attest@v4` against `release/SHA256SUMS.txt` so release artifact digests are bound to the GitHub Actions build. |
| Gradle dependency verification | `gradle/verification-metadata.xml` | Records SHA-256 checksums for resolved Gradle plugins and app dependencies. |
| Dependency Review | `.github/workflows/dependency-review.yml` | Runs on pull requests and fails high/critical vulnerable dependency additions. |
| OpenSSF Scorecard | `.github/workflows/scorecard.yml` | Runs on main pushes, branch-protection changes, weekly schedule, and manual dispatch; keeps public result publishing disabled and uploads SARIF to code scanning. |
| F-Droid blocker preflight | `tools/fdroid_preflight.py` | Confirms that F-Droid mainline remains blocked until proprietary dependency boundaries change. |

## Release verification

For each `v*` release:

1. Confirm the GitHub Release contains `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
2. Confirm the GitHub Release contains `THIRD-PARTY-NOTICES.md`.
3. Confirm the GitHub Release contains `NATIVE-COMPLIANCE.md`.
4. Confirm `SHA256SUMS.txt` includes the APK, `THIRD-PARTY-NOTICES.md`, and `NATIVE-COMPLIANCE.md`.
5. Confirm the generated release notes include the APK SHA-256, third-party notices entry, native compliance packet entry, signing certificate SHA-256, and artifact attestation URL.
6. Spot-check `THIRD-PARTY-NOTICES.md` for high-risk dependencies such as NewPipeExtractor, youtubedl-android, Firebase, Play services ML Kit, ZXing, Palette, and ProfileInstaller.
7. Spot-check `docs/legal/dependency-notice-overrides.json` when any high-risk dependency or payload changed intentionally.
8. Spot-check `NATIVE-COMPLIANCE.md` for youtubedl-android library, youtubedl-android ffmpeg, yt-dlp, Python, QuickJS, FFmpeg, and NewPipeExtractor evidence.
9. Verify the APK locally with `apksigner verify --verbose --print-certs`.
10. Compare the local SHA-256 values to `SHA256SUMS.txt`.

## Third-party notices

The release workflow uses Google's OSS Licenses Gradle plugin only. It intentionally does not add the `play-services-oss-licenses` runtime dependency or stock Google notice activity, because that runtime path was found to pull broad UI dependency upgrades on the current AGP 8.7.3 / Gradle 8.12 stack.

Generate notices locally after the release notice task has run:

```powershell
.\gradlew.bat :app:releaseOssLicensesTask --stacktrace --no-daemon
python tools\dependency_notice_lock.py --mode check --lockfile docs\legal\dependency-notices.lock.json
python tools\dependency_overlay_check.py --overlay docs\legal\dependency-notice-overrides.json
python tools\google_oss_to_markdown.py --variant release --output build\reports\THIRD-PARTY-NOTICES.md
```

Generated dependency notices do not replace Aura's content-source disclosures. `ProviderDisclosure.kt` remains the source of truth for provider policy rows such as YouTube, Reddit, Pexels, Pixabay, community uploads, bundled media, and AI-generated content.

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

## Native compliance packet

`NATIVE-COMPLIANCE.md` is a factual inventory, not legal advice. It reads resolved Gradle cache artifacts and, in release CI, the final signed APK. It records artifact hashes, ABI payload paths, nested yt-dlp/Python facts, FFmpeg payload entries, and upstream source/build references that release owners must review.

Generate the local packet after dependencies have been resolved:

```powershell
python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json
python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md
```

Release CI adds final APK inspection:

```bash
python3 tools/native_compliance_inventory.py --apk "$RELEASE_DIR/Aura-vX.Y.Z-versionCode-N-universal-release.apk" --output "$RELEASE_DIR/NATIVE-COMPLIANCE.md"
```

Treat a youtubedl-android, NewPipeExtractor, yt-dlp, Python, QuickJS, or FFmpeg version change as a required native packet refresh. FFmpeg remains a release-owner review item because the AAR does not encode the exact configure line or source correspondence required by FFmpeg's legal checklist.

When a native/copyleft artifact or payload changes intentionally, refresh the lockfile and markdown packet together:

```powershell
python tools\native_compliance_inventory.py --mode write-lock --lockfile docs\legal\native-compliance.lock.json
python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md
```

Review the artifact hashes, payload facts, and FFmpeg notes in the same change as the dependency update.

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
