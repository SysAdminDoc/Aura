# Release signing and GitHub distribution

Aura release tags publish signed, non-debuggable release APKs for GitHub Releases and Obtainium. Debug APKs are development-only and must not be attached to public releases.

## GitHub secrets

The release workflow requires these repository secrets:

| Secret | Purpose |
| --- | --- |
| `AURA_RELEASE_KEYSTORE_BASE64` | Base64-encoded `freevibe.jks` release keystore. |
| `AURA_RELEASE_KEYSTORE_PASSWORD` | Keystore password. |
| `AURA_RELEASE_KEY_ALIAS` | Release key alias. |
| `AURA_RELEASE_KEY_PASSWORD` | Release key password. |

On Windows, create the base64 value from the local keystore with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("freevibe.jks")) | Set-Content -NoNewline freevibe.jks.base64.txt
```

Do not commit `freevibe.jks`, `local.properties`, copied APKs, or generated base64 files.

## Workflow contract

`.github/workflows/release.yml` now performs this sequence for tags and manual dispatch:

1. Restores the release keystore from GitHub secrets.
2. Writes a temporary `local.properties` with signing paths and blank optional provider keys.
3. Runs `./gradlew :app:assembleRelease --stacktrace --no-daemon`.
4. Copies the signed universal APK to `release/Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
5. Generates `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, and `NATIVE-COMPLIANCE.md`.
6. Runs `apksigner verify --verbose --print-certs`.
7. Fails if `aapt dump badging` reports `application-debuggable`.
8. Creates a GitHub artifact attestation for the checksum file.
9. Publishes `SHA256SUMS.txt` and release notes containing versionName, versionCode, APK SHA-256, signing certificate SHA-256, artifact attestation URL, and Android developer verification status.
10. Runs `tools/release_artifact_bundle_check.py` against the final `release/` directory before upload or publication.

Manual `workflow_dispatch` runs upload the same files as workflow artifacts for dry-run inspection. Tag runs also attach the APK, third-party notices, raw Google OSS input archive, native compliance packet, and checksum file to the GitHub Release. The dry-run procedure lives in [release-dry-run.md](release-dry-run.md), and raw input archive retention is documented in [raw-oss-input-retention.md](raw-oss-input-retention.md).

## Local release check

Use Android Studio's bundled JBR on Windows:

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
.\gradlew.bat :app:assembleRelease --stacktrace --no-daemon
```

Verify the local APK:

```powershell
$buildTools = Get-ChildItem "$env:LOCALAPPDATA/Android/Sdk/build-tools" | Sort-Object Name | Select-Object -Last 1
& "$($buildTools.FullName)/apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
& "$($buildTools.FullName)/aapt.exe" dump badging app/build/outputs/apk/release/app-release.apk | Select-String application-debuggable
Get-FileHash app/build/outputs/apk/release/app-release.apk -Algorithm SHA256
```

The `application-debuggable` search should return no output.

## Obtainium

`obtainium.json` tracks GitHub Releases with an APK asset filter. Users should install the `Aura-vX.Y.Z-versionCode-N-universal-release.apk` asset and compare it to `SHA256SUMS.txt` when verifying a release manually.

## Developer verification

Android developer verification and IzzyOnDroid prep live in [developer-verification.md](developer-verification.md). Keep release notes at `owner-confirmation-required` until the owner confirms `com.freevibe` and the current signing certificate SHA-256 in Android Developer Console or Play Console.
