# Supply-chain verification

Aura is side-loaded through GitHub Releases and Obtainium, so release artifacts need proof that is stronger than a plain APK attachment.

## Active controls

| Control | Location | Purpose |
| --- | --- | --- |
| Signed release APK | `.github/workflows/release.yml` | Builds the release variant, verifies the signature, rejects debuggable APKs, and publishes checksums. |
| Artifact attestation | `.github/workflows/release.yml` | Uses `actions/attest@v4` against `release/SHA256SUMS.txt` so the APK digest is bound to the GitHub Actions build. |
| Gradle dependency verification | `gradle/verification-metadata.xml` | Records SHA-256 checksums for resolved Gradle plugins and app dependencies. |
| Dependency Review | `.github/workflows/dependency-review.yml` | Runs on pull requests and fails high/critical vulnerable dependency additions. |
| OpenSSF Scorecard | `.github/workflows/scorecard.yml` | Runs on main pushes, branch-protection changes, weekly schedule, and manual dispatch; keeps public result publishing disabled and uploads SARIF to code scanning. |
| F-Droid blocker preflight | `tools/fdroid_preflight.py` | Confirms that F-Droid mainline remains blocked until proprietary dependency boundaries change. |

## Release verification

For each `v*` release:

1. Confirm the GitHub Release contains `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
2. Confirm `SHA256SUMS.txt` includes that APK.
3. Confirm the generated release notes include the APK SHA-256, signing certificate SHA-256, and artifact attestation URL.
4. Verify the APK locally with `apksigner verify --verbose --print-certs`.
5. Compare the local SHA-256 to `SHA256SUMS.txt`.

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
