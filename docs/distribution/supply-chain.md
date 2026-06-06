# Supply-chain verification

Aura is side-loaded through GitHub Releases and Obtainium, so release artifacts need proof that is stronger than a plain APK attachment.

## Active controls

| Control | Location | Purpose |
| --- | --- | --- |
| Signed release APK | `.github/workflows/release.yml` | Builds the release variant, verifies the signature, rejects debuggable APKs, and publishes checksums. |
| Third-party notices | `tools/google_oss_to_markdown.py`, `.github/workflows/release.yml` | Runs the Google OSS Licenses Gradle task for the release variant and publishes `THIRD-PARTY-NOTICES.md` next to the APK. |
| Artifact attestation | `.github/workflows/release.yml` | Uses `actions/attest@v4` against `release/SHA256SUMS.txt` so release artifact digests are bound to the GitHub Actions build. |
| Gradle dependency verification | `gradle/verification-metadata.xml` | Records SHA-256 checksums for resolved Gradle plugins and app dependencies. |
| Dependency Review | `.github/workflows/dependency-review.yml` | Runs on pull requests and fails high/critical vulnerable dependency additions. |
| OpenSSF Scorecard | `.github/workflows/scorecard.yml` | Runs on main pushes, branch-protection changes, weekly schedule, and manual dispatch; keeps public result publishing disabled and uploads SARIF to code scanning. |
| F-Droid blocker preflight | `tools/fdroid_preflight.py` | Confirms that F-Droid mainline remains blocked until proprietary dependency boundaries change. |

## Release verification

For each `v*` release:

1. Confirm the GitHub Release contains `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
2. Confirm the GitHub Release contains `THIRD-PARTY-NOTICES.md`.
3. Confirm `SHA256SUMS.txt` includes both the APK and `THIRD-PARTY-NOTICES.md`.
4. Confirm the generated release notes include the APK SHA-256, third-party notices entry, signing certificate SHA-256, and artifact attestation URL.
5. Spot-check `THIRD-PARTY-NOTICES.md` for high-risk dependencies such as NewPipeExtractor, youtubedl-android, Firebase, Play services ML Kit, ZXing, Palette, and ProfileInstaller.
6. Verify the APK locally with `apksigner verify --verbose --print-certs`.
7. Compare the local SHA-256 values to `SHA256SUMS.txt`.

## Third-party notices

The release workflow uses Google's OSS Licenses Gradle plugin only. It intentionally does not add the `play-services-oss-licenses` runtime dependency or stock Google notice activity, because that runtime path was found to pull broad UI dependency upgrades on the current AGP 8.7.3 / Gradle 8.12 stack.

Generate notices locally after the release notice task has run:

```powershell
.\gradlew.bat :app:releaseOssLicensesTask --stacktrace --no-daemon
python tools\google_oss_to_markdown.py --variant release --output build\reports\THIRD-PARTY-NOTICES.md
```

Generated dependency notices do not replace Aura's content-source disclosures. `ProviderDisclosure.kt` remains the source of truth for provider policy rows such as YouTube, Reddit, Pexels, Pixabay, community uploads, bundled media, and AI-generated content.

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
