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
5. Runs `apksigner verify --verbose --print-certs`.
6. Fails if `aapt dump badging` reports `application-debuggable`.
7. Publishes `SHA256SUMS.txt` and release notes containing versionName, versionCode, APK SHA-256, and signing certificate SHA-256.

Manual `workflow_dispatch` runs upload the same files as workflow artifacts for dry-run inspection. Tag runs also attach the APK and checksum file to the GitHub Release.

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
