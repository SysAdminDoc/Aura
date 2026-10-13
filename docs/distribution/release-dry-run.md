# Release dry-run validation

Use a local release dry run before a tagged public release when packaging,
signing, notices, native compliance, or release evidence changes.

## Local dry run

A dry run builds the same signed release artifact set that a public GitHub
Release will publish, but keeps the files in a local `release/` directory for
inspection.

The dry run must:

- Build the signed `fullRelease` APK and AAB with local signing properties.
- Copy the APK to `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
- Copy the AAB to `Aura-vX.Y.Z-versionCode-N-play-release.aab`.
- Validate the AAB with bundletool and record package/version metadata from the bundle manifest.
- Verify the AAB JAR signature and record the upload-key SHA-256 fingerprint.
- Generate `THIRD-PARTY-NOTICES.md`.
- Archive raw Google OSS inputs as `GOOGLE-OSS-RAW-INPUTS.zip`.
- Generate `NATIVE-COMPLIANCE.md`.
- Generate `NATIVE-ALIGNMENT.json`.
- Check that the release build type forces optional provider credentials blank.
- Scan the packaged signed APK for nonblank provider credential values from local `local.properties`.
- Check Fastlane text metadata, current versionCode changelog, and public privacy-policy URL before the signed release build.
- Check the store asset capture plan, planned screenshots, feature-graphic requirements, alt text, and future asset-mode command before the signed release build.
- Check that every manifest permission, reviewed network endpoint, source-backed local storage surface, and SDK data surface has a Data safety matrix row before the signed release build.
- Check WorkManager scheduling, background network posture, and device evidence planning before publication.
- Check community guidelines, Play App content, alternative-store disclosure, release metadata consistency, and SBOM readiness before publication.
- Record Play App Signing owner steps for Play Console > App integrity, including app signing key versus upload key checks.
- Run `tools/release_artifact_bundle_check.py` against the final `release/` directory.
- Upload the checked bundle with `gh release create` or `gh release upload --clobber` only after inspection.

## Bundle validator

Before the APK build, release dry runs validate committed release metadata. Run
the release manifest check first: it is the single source for versionName,
versionCode, and Room schema version, and every other version-shaped artifact
(README badge, release-metadata policy, Fastlane changelog) is derived from it.

```bash
python3 tools/release_clean_clone_check.py --repo-root . --revision HEAD
python3 tools/release_manifest.py --mode check --repo-root .
python3 tools/provider_credential_release_check.py --app-gradle app/build.gradle.kts --local-properties local.properties
python3 tools/provider_credential_storage_check.py --policy docs/security/provider-credential-storage.json --repo-root .
python3 tools/cleartext_release_check.py --repo-root .
python3 tools/network_endpoint_inventory_check.py --inventory docs/security/network-endpoints.json --repo-root .
python3 tools/store_metadata_preflight.py --repo-root .
python3 tools/store_asset_pipeline_check.py --policy docs/distribution/store-assets.json --repo-root .
python3 tools/privacy_policy_link_check.py --policy docs/privacy/privacy-policy-link.json --repo-root .
python3 tools/privacy_data_safety_check.py --policy docs/privacy/data-safety.json --repo-root .
python3 tools/rotation_boot_permission_check.py --policy docs/rotation-trigger-boot-behavior.json --repo-root .
python3 tools/rotation_fgs_policy_check.py --policy docs/rotation-trigger-fgs-policy.json --repo-root .
python3 tools/background_work_scheduling_check.py --policy docs/background-work-scheduling-ledger.json --repo-root .
python3 tools/background_work_network_check.py --policy docs/background-work-network-posture.json --repo-root .
python3 tools/background_work_device_evidence_check.py --policy docs/background-work-device-evidence.json --repo-root .
python3 tools/community_guidelines_consent_check.py --repo-root .
python3 tools/unreachable_surface_check.py --repo-root .
python3 tools/play_app_content_packet_check.py --policy docs/distribution/play-app-content.json --repo-root .
python3 tools/alt_store_metadata_check.py --policy docs/distribution/alt-store-metadata.json --repo-root .
python3 tools/release_metadata_consistency_check.py --policy docs/distribution/release-metadata-consistency.json --repo-root .
python3 tools/sbom_readiness_check.py --policy docs/distribution/sbom-readiness.json --repo-root .
```

The text-mode checks fail when title or description limits drift, the current
versionCode changelog is missing or stale, stale branding returns, privacy URLs
drift, background-work evidence packets drift, or the local release artifact
list no longer matches the docs. The clean-clone check is the umbrella gate. It
uses only files stored in the selected commit and reports signing, generated
artifact, console, publication, and device evidence separately when those
owner-only inputs are unavailable.

After real assets are committed, the future asset-mode command is:

```bash
python3 tools/store_metadata_preflight.py --repo-root . --require-assets --min-phone-screenshots 4
```

Build the signed release artifacts with the flavor-specific release tasks:

```powershell
$env:JAVA_HOME = "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
.\gradlew.bat :app:assembleFullRelease --stacktrace --no-daemon
.\gradlew.bat -PauraReleaseChannel=play :app:bundleFullRelease --stacktrace --no-daemon
```

These are separate invocations on purpose. The APK keeps the GitHub/Obtainium
provider catalog. The Play AAB compiles the Play catalog and removes YouTube
controls and downloader initialization.

Copy and name the release artifacts:

```powershell
$versionName = "X.Y.Z"
$versionCode = "N"
$releaseDir = "release"
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null
Copy-Item app\build\outputs\apk\full\release\app-full-release.apk "$releaseDir\Aura-v$versionName-versionCode-$versionCode-universal-release.apk"
Copy-Item app\build\outputs\bundle\fullRelease\app-full-release.aab "$releaseDir\Aura-v$versionName-versionCode-$versionCode-play-release.aab"
```

Record bundle evidence before upload:

```powershell
$aabName = "Aura-v$versionName-versionCode-$versionCode-play-release.aab"
$aabPath = Join-Path $releaseDir $aabName
bundletool validate --bundle=$aabPath
"bundletool validate passed: $aabName" | Set-Content -Encoding ascii "$releaseDir\bundletool-validate.txt"
"package=$(bundletool dump manifest --bundle=$aabPath --xpath /manifest/@package)" | Set-Content -Encoding ascii "$releaseDir\aab-manifest.txt"
"versionCode=$(bundletool dump manifest --bundle=$aabPath --xpath /manifest/@android:versionCode)" | Add-Content -Encoding ascii "$releaseDir\aab-manifest.txt"
"versionName=$(bundletool dump manifest --bundle=$aabPath --xpath /manifest/@android:versionName)" | Add-Content -Encoding ascii "$releaseDir\aab-manifest.txt"
jarsigner -verify -verbose -certs $aabPath *> "$releaseDir\aab-jarsigner.txt"
keytool -printcert -jarfile $aabPath *> "$releaseDir\aab-keytool.txt"
@"
Play App Signing owner-confirmation-required for com.freevibe.
Open Play Console > App integrity.
Compare the local upload key SHA-256 in aab-keytool.txt with the Play upload key.
Confirm the Play app signing key SHA-256 remains the intended owner-managed key.
Record the owner, date, and decision before uploading the AAB.
"@ | Set-Content -Encoding ascii "$releaseDir\PLAY-APP-SIGNING-OWNER-STEPS.txt"
```

The final directory is validated before upload:

```bash
python3 tools/release_artifact_bundle_check.py \
  --release-dir "$RELEASE_DIR" \
  --apk-name "$APK_NAME" \
  --split-apk-name "Aura-v$VERSION_NAME-versionCode-$VERSION_CODE-arm64-v8a-release.apk" \
  --split-apk-name "Aura-v$VERSION_NAME-versionCode-$VERSION_CODE-armeabi-v7a-release.apk" \
  --split-apk-name "Aura-v$VERSION_NAME-versionCode-$VERSION_CODE-x86-release.apk" \
  --split-apk-name "Aura-v$VERSION_NAME-versionCode-$VERSION_CODE-x86_64-release.apk" \
  --aab-name "$AAB_NAME" \
  --version-name "$VERSION_NAME" \
  --version-code "$VERSION_CODE"
```

`assembleFullRelease` produces five APKs: one per architecture plus the universal
one. All five are published, all five belong in `SHA256SUMS.txt`, and all five have
to be named in the release notes, because a split nobody mentions is a split nobody
downloads. Pass each per-ABI file with its own `--split-apk-name`; the check
verifies each one exists, is checksummed, matches the version, and claims a
distinct architecture.

The check fails when:

- A required artifact is missing or empty.
- The APK name does not match the version name and version code.
- The AAB name does not match the version name and version code.
- `SHA256SUMS.txt` is missing the APK, AAB, third-party notice, raw Google OSS input archive, native-compliance digest, or native-alignment digest.
- A recorded checksum does not match the file bytes.
- `RELEASE_NOTES.md` lacks the APK digest, AAB digest, notice/native-compliance/native-alignment entries, signing certificate, upload-key certificate, Play App Signing owner steps, local build receipt, build type, or package ID.
- `apksigner.txt` lacks the signing certificate SHA-256 digest.
- `aapt-badging.txt` reports `application-debuggable`.
- `aab-manifest.txt` does not include `com.freevibe`, versionCode, and versionName.
- `bundletool-validate.txt` does not record a successful validation for the named AAB.
- `aab-jarsigner.txt` does not record `jar verified.`
- `aab-keytool.txt` does not include an upload-key SHA-256 fingerprint.
- `PLAY-APP-SIGNING-OWNER-STEPS.txt` does not name the Play App Signing owner checks.

## Local script smoke test

The validator can be exercised without a full APK build by creating a temporary
release directory with tiny placeholder files. This checks parser and checksum
behavior only; it does not replace a signed local release build.

```powershell
$tmp = Join-Path $env:TEMP "aura-release-bundle-smoke"
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $tmp | Out-Null
$apk = "Aura-v6.42.0-versionCode-143-universal-release.apk"
$aab = "Aura-v6.42.0-versionCode-143-play-release.aab"
"apk" | Set-Content -Encoding ascii (Join-Path $tmp $apk)
"aab" | Set-Content -Encoding ascii (Join-Path $tmp $aab)
"third-party" | Set-Content -Encoding ascii (Join-Path $tmp "THIRD-PARTY-NOTICES.md")
"raw" | Set-Content -Encoding ascii (Join-Path $tmp "GOOGLE-OSS-RAW-INPUTS.zip")
"native" | Set-Content -Encoding ascii (Join-Path $tmp "NATIVE-COMPLIANCE.md")
'{"status":"ok","policyKind":"nativePageAlignment","packageName":"com.freevibe","requiredLoadSegmentAlignmentBytes":16384,"checked64BitLoadSegments":2,"seen64BitAbis":["arm64-v8a","x86_64"]}' | Set-Content -Encoding ascii (Join-Path $tmp "NATIVE-ALIGNMENT.json")
"Signer #1 certificate SHA-256 digest: test" | Set-Content -Encoding ascii (Join-Path $tmp "apksigner.txt")
"package: name='com.freevibe'" | Set-Content -Encoding ascii (Join-Path $tmp "aapt-badging.txt")
'manifest package="com.freevibe" android:versionCode="143" android:versionName="6.42.0"' | Set-Content -Encoding ascii (Join-Path $tmp "aab-manifest.txt")
"bundletool validate passed: $aab" | Set-Content -Encoding ascii (Join-Path $tmp "bundletool-validate.txt")
"jar verified." | Set-Content -Encoding ascii (Join-Path $tmp "aab-jarsigner.txt")
"Certificate fingerprints:`n`t SHA256: upload-test" | Set-Content -Encoding ascii (Join-Path $tmp "aab-keytool.txt")
"Play App Signing owner-confirmation-required for com.freevibe. Open Play Console App integrity, compare upload key and app signing key." | Set-Content -Encoding ascii (Join-Path $tmp "PLAY-APP-SIGNING-OWNER-STEPS.txt")
$files = @($apk, $aab, "THIRD-PARTY-NOTICES.md", "GOOGLE-OSS-RAW-INPUTS.zip", "NATIVE-COMPLIANCE.md", "NATIVE-ALIGNMENT.json")
$sumLines = foreach ($file in $files) { "$((Get-FileHash (Join-Path $tmp $file) -Algorithm SHA256).Hash.ToLower())  $file" }
$sumLines | Set-Content -Encoding ascii (Join-Path $tmp "SHA256SUMS.txt")
$apkHash = (Get-FileHash (Join-Path $tmp $apk) -Algorithm SHA256).Hash.ToLower()
$aabHash = (Get-FileHash (Join-Path $tmp $aab) -Algorithm SHA256).Hash.ToLower()
@"
Aura 6.42.0 (versionCode 143)

Signed release artifacts:
- APK: $apk
- APK SHA-256: $apkHash
- AAB: $aab
- AAB SHA-256: $aabHash
- THIRD-PARTY-NOTICES.md
- GOOGLE-OSS-RAW-INPUTS.zip
- NATIVE-COMPLIANCE.md
- NATIVE-ALIGNMENT.json
- Signing certificate SHA-256: test
- Upload key certificate SHA-256: upload-test
- Play App Signing owner steps: owner-confirmation-required in PLAY-APP-SIGNING-OWNER-STEPS.txt
- Local build receipt: smoke-test
- Build type: release, android:debuggable=false

Android developer verification:
- Package: com.freevibe
"@ | Set-Content -Encoding ascii (Join-Path $tmp "RELEASE_NOTES.md")
python tools\release_artifact_bundle_check.py --release-dir $tmp --apk-name $apk --aab-name $aab --version-name 6.42.0 --version-code 143
```

Expected output:

```json
{"aab": "Aura-v6.42.0-versionCode-143-play-release.aab", "apk": "Aura-v6.42.0-versionCode-143-universal-release.apk", "releaseDir": "<temp path>", "status": "ok", "versionCode": "143", "versionName": "6.42.0"}
```

## Sources

- Android App Bundles: https://developer.android.com/guide/app-bundle
- Android app signing: https://developer.android.com/studio/publish/app-signing
- bundletool: https://developer.android.com/tools/bundletool
- Play App Signing: https://support.google.com/googleplay/android-developer/answer/9842756
- GitHub Releases: https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
- GitHub CLI release create: https://cli.github.com/manual/gh_release_create
- GitHub CLI release upload: https://cli.github.com/manual/gh_release_upload
