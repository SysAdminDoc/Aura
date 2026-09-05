# Release signing and GitHub distribution

Aura releases are built locally on the workstation and uploaded to GitHub
Releases for GitHub/Obtainium users, with a Play-ready AAB retained for the
same version. Debug APKs are development-only and must not be attached to public
releases.

## Published certificate

Official Aura builds are signed with:

```text
SHA-256: F2:8E:44:BE:A3:2F:5B:28:90:C8:26:8B:7F:BE:D4:3C:44:A4:D6:71:A5:12:FB:07:EB:F1:8F:DD:41:C6:6E:5A
```

Published in `README.md`, the Fastlane `full_description.txt`, and
`docs/distribution/signing-certificate.json`. `tools/signing_certificate_check.py`
fails when any of those three loses the digest, and when the digest stops
matching the release keystore on a machine that holds it. That check is the
reason the value cannot quietly drift: before it existed, the digest lived only
in release-note prose, where neither a gate nor a store could read it.

The digest is what AppVerifier and IzzyOnDroid's `AllowedAPKSigningKeys` key
off, and it is the only way a sideloading user can tell an official build from a
re-signed one. It must not change: an APK signed with a different key cannot
update an existing install, so rotating it would strand every current user.

## Local signing inputs

Release signing reads the ignored local files already used by Gradle:

- `freevibe.jks`
- `local.properties`

`local.properties` must contain the signing path, keystore password, key alias,
and key password. It must keep optional provider API keys blank for public
release builds.

Do not commit `freevibe.jks`, `local.properties`, copied APKs, release
directories, or generated signing evidence.

The FOSS reproducibility lane is deliberately separate from public signing. Run
`py -3 tools\foss_reproducibility_check.py --build-twice` from a clean checkout;
the tool passes `-Paura.reproducibleFossBuild=true`, builds two unsigned isolated
artifacts, and compares their raw and signature-stripped archive digests. Never
publish those unsigned verification APKs in place of the owner-signed release.

## Local release contract

Before publishing a GitHub Release:

1. Set `JAVA_HOME` to Android Studio's bundled JBR.
2. Run every release preflight command listed below.
3. Build the signed release APK and AAB with `.\gradlew.bat :app:assembleFullRelease :app:bundleFullRelease --stacktrace --no-daemon`.
4. Copy the APK to `release/Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
5. Copy the AAB to `release/Aura-vX.Y.Z-versionCode-N-play-release.aab`.
6. Generate `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, `NATIVE-COMPLIANCE.md`, and `NATIVE-ALIGNMENT.json`.
7. Run `apksigner verify --verbose --print-certs`.
8. Confirm `aapt dump badging` does not report `application-debuggable`.
9. Run `bundletool validate`, `bundletool dump manifest`, `jarsigner -verify`, and `keytool -printcert -jarfile` against the AAB.
10. Write `SHA256SUMS.txt`, `RELEASE_NOTES.md`, `apksigner.txt`, `aapt-badging.txt`, `aab-manifest.txt`, `bundletool-validate.txt`, `aab-jarsigner.txt`, `aab-keytool.txt`, and `PLAY-APP-SIGNING-OWNER-STEPS.txt`.
11. Run `tools/release_artifact_bundle_check.py` against the final `release/` directory.
12. Upload the checked files with `gh release create` or `gh release upload --clobber`.

The dry-run procedure lives in [release-dry-run.md](release-dry-run.md), and
raw input archive retention is documented in
[raw-oss-input-retention.md](raw-oss-input-retention.md).

## Local release check

Use Android Studio's bundled JBR on Windows:

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
python tools\provider_credential_release_check.py --app-gradle app\build.gradle.kts --local-properties local.properties
python tools\provider_credential_storage_check.py --policy docs\security\provider-credential-storage.json --repo-root .
python tools\cleartext_release_check.py --repo-root .
python tools\network_endpoint_inventory_check.py --inventory docs\security\network-endpoints.json --repo-root .
python tools\store_metadata_preflight.py --repo-root .
python tools\store_asset_pipeline_check.py --policy docs\distribution\store-assets.json --repo-root .
python tools\privacy_policy_link_check.py --policy docs\privacy\privacy-policy-link.json --repo-root .
python tools\privacy_data_safety_check.py --policy docs\privacy\data-safety.json --repo-root .
python tools\rotation_boot_permission_check.py --policy docs\rotation-trigger-boot-behavior.json --repo-root .
python tools\rotation_fgs_policy_check.py --policy docs\rotation-trigger-fgs-policy.json --repo-root .
python tools\background_work_scheduling_check.py --policy docs\background-work-scheduling-ledger.json --repo-root .
python tools\background_work_network_check.py --policy docs\background-work-network-posture.json --repo-root .
python tools\background_work_device_evidence_check.py --policy docs\background-work-device-evidence.json --repo-root .
python tools\community_guidelines_consent_check.py --repo-root .
python tools\play_app_content_packet_check.py --policy docs\distribution\play-app-content.json --repo-root .
python tools\alt_store_metadata_check.py --policy docs\distribution\alt-store-metadata.json --repo-root .
python tools\release_metadata_consistency_check.py --policy docs\distribution\release-metadata-consistency.json --repo-root .
python tools\sbom_readiness_check.py --policy docs\distribution\sbom-readiness.json --repo-root .
.\gradlew.bat :app:assembleFullRelease :app:bundleFullRelease --stacktrace --no-daemon
python tools\provider_credential_apk_scan.py --local-properties local.properties --apk app\build\outputs\apk\full\release\app-full-release.apk
```

The provider credential check fails if ignored `local.properties` contains
nonblank Pexels, Pixabay, Freesound, SoundCloud, or Stability values. Only use
`--allow-nonblank-local-provider-keys` for an explicitly internal build review;
public GitHub, Obtainium, and Izzy builds must keep those defaults blank and
rely on user-entered settings.

The APK scan fails if any nonblank provider value from `local.properties`
appears in the release APK. It reports property names and APK entries only, not
credential values.

For policy docs that match normalized tool paths, the local release preflight
set is:

- `tools/provider_credential_release_check.py`
- `tools/provider_credential_storage_check.py`
- `tools/cleartext_release_check.py`
- `tools/network_endpoint_inventory_check.py`
- `tools/store_metadata_preflight.py`
- `tools/store_asset_pipeline_check.py`
- `tools/privacy_policy_link_check.py`
- `tools/privacy_data_safety_check.py`
- `tools/rotation_boot_permission_check.py`
- `tools/rotation_fgs_policy_check.py`
- `tools/background_work_scheduling_check.py`
- `tools/background_work_network_check.py`
- `tools/background_work_device_evidence_check.py`
- `tools/community_guidelines_consent_check.py`
- `tools/play_app_content_packet_check.py`
- `tools/alt_store_metadata_check.py`
- `tools/release_metadata_consistency_check.py`
- `tools/sbom_readiness_check.py`
- `tools/release_artifact_bundle_check.py`

Verify the local APK:

```powershell
$buildTools = Get-ChildItem "$env:LOCALAPPDATA/Android/Sdk/build-tools" | Sort-Object Name | Select-Object -Last 1
& "$($buildTools.FullName)/apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/full/release/app-full-release.apk
& "$($buildTools.FullName)/aapt.exe" dump badging app/build/outputs/apk/full/release/app-full-release.apk | Select-String application-debuggable
Get-FileHash app/build/outputs/apk/full/release/app-full-release.apk -Algorithm SHA256
bundletool validate --bundle app/build/outputs/bundle/fullRelease/app-full-release.aab
bundletool dump manifest --bundle app/build/outputs/bundle/fullRelease/app-full-release.aab --xpath /manifest/@package
bundletool dump manifest --bundle app/build/outputs/bundle/fullRelease/app-full-release.aab --xpath /manifest/@android:versionCode
bundletool dump manifest --bundle app/build/outputs/bundle/fullRelease/app-full-release.aab --xpath /manifest/@android:versionName
jarsigner -verify -verbose -certs app/build/outputs/bundle/fullRelease/app-full-release.aab
keytool -printcert -jarfile app/build/outputs/bundle/fullRelease/app-full-release.aab
Get-FileHash app/build/outputs/bundle/fullRelease/app-full-release.aab -Algorithm SHA256
```

The `application-debuggable` search should return no output.

## Play App Signing

The AAB is signed with the local upload key. Before any Play upload, the release
owner must open Play Console > App integrity, compare the local upload-key
SHA-256 from `aab-keytool.txt`, and confirm the owner-managed Play app signing
key for `com.freevibe` remains intended. Record that check in
`PLAY-APP-SIGNING-OWNER-STEPS.txt`; the local artifact validator fails if the
receipt is missing.

## Obtainium

`obtainium.json` tracks GitHub Releases with an APK asset filter. Users should
install the `Aura-vX.Y.Z-versionCode-N-universal-release.apk` asset and compare
it to `SHA256SUMS.txt` when verifying a release manually.

## Developer verification

Android developer verification and IzzyOnDroid prep live in
[developer-verification.md](developer-verification.md). Keep release notes at
`owner-confirmation-required` until the owner confirms `com.freevibe` and the
current signing certificate SHA-256 in Android Developer Console or Play
Console.
