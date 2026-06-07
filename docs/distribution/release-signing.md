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
3. Runs `tools/provider_credential_release_check.py` to confirm optional provider keys are blank before they can be bundled into `BuildConfig`.
4. Runs the provider credential storage, cleartext release, store metadata preflight, privacy-policy link, Data safety matrix, community guidelines consent, Play App content, alternative-store disclosure, release metadata consistency, and SBOM readiness checks.
5. Runs `./gradlew :app:assembleRelease --stacktrace --no-daemon`.
6. Copies the signed universal APK to `release/Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
7. Runs `tools/provider_credential_apk_scan.py` against the packaged APK and temporary release `local.properties`.
8. Generates `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, and `NATIVE-COMPLIANCE.md`.
9. Runs `apksigner verify --verbose --print-certs`.
10. Fails if `aapt dump badging` reports `application-debuggable`.
11. Creates a GitHub artifact attestation for the checksum file.
12. Publishes `SHA256SUMS.txt` and release notes containing versionName, versionCode, APK SHA-256, signing certificate SHA-256, artifact attestation URL, and Android developer verification status.
13. Runs `tools/release_artifact_bundle_check.py` against the final `release/` directory before upload or publication.

Manual `workflow_dispatch` runs upload the same files as workflow artifacts for dry-run inspection. Tag runs also attach the APK, third-party notices, raw Google OSS input archive, native compliance packet, and checksum file to the GitHub Release. The dry-run procedure lives in [release-dry-run.md](release-dry-run.md), and raw input archive retention is documented in [raw-oss-input-retention.md](raw-oss-input-retention.md).

## Local release check

Use Android Studio's bundled JBR on Windows:

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
python tools\provider_credential_release_check.py --app-gradle app\build.gradle.kts --release-workflow .github\workflows\release.yml --local-properties local.properties
python tools\store_metadata_preflight.py --repo-root .
python tools\privacy_policy_link_check.py --policy docs\privacy\privacy-policy-link.json --repo-root .
python tools\privacy_data_safety_check.py --policy docs\privacy\data-safety.json --repo-root .
python tools\community_guidelines_consent_check.py --repo-root .
python tools\play_app_content_packet_check.py --policy docs\distribution\play-app-content.json --repo-root .
python tools\alt_store_metadata_check.py --policy docs\distribution\alt-store-metadata.json --repo-root .
python tools\release_metadata_consistency_check.py --policy docs\distribution\release-metadata-consistency.json --repo-root .
python tools\sbom_readiness_check.py --policy docs\distribution\sbom-readiness.json --repo-root .
.\gradlew.bat :app:assembleRelease --stacktrace --no-daemon
python tools\provider_credential_apk_scan.py --local-properties local.properties --apk app\build\outputs\apk\release\app-release.apk
```

The provider credential check fails if ignored `local.properties` contains nonblank Pexels, Pixabay, Freesound, SoundCloud, or Stability values. Only use `--allow-nonblank-local-provider-keys` for an explicitly internal build review; public GitHub, Obtainium, and Izzy builds must keep those defaults blank and rely on user-entered settings.

The APK scan fails if any nonblank provider value from `local.properties`
appears in the release APK. It reports property names and APK entries only,
not credential values.

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
