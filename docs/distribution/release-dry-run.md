# Release dry-run validation

Use a manual release workflow run before a tagged public release when release packaging, signing, notices, native compliance, or artifact provenance changed.

## GitHub Actions dry run

The release workflow is configured with `workflow_dispatch`. GitHub documents manual workflow runs through the Actions tab, GitHub CLI, and REST API, and workflow artifacts are the supported way to store generated files from a run. Aura uses that manual branch run as the dry-run lane.

Manual branch runs:

- Build the signed release APK with the same release signing secrets as a tag release.
- Generate `THIRD-PARTY-NOTICES.md`.
- Archive raw Google OSS inputs as `GOOGLE-OSS-RAW-INPUTS.zip`.
- Generate `NATIVE-COMPLIANCE.md`.
- Check that optional provider credentials are blank before the signed release build.
- Scan the packaged signed APK for nonblank provider credential values from the temporary release `local.properties`.
- Check Fastlane text metadata, current versionCode changelog, and public privacy-policy URL before the signed release build.
- Check the store asset capture plan, planned screenshots, feature-graphic
  requirements, alt text, and future asset-mode command before the signed
  release build.
- Check that the same public privacy-policy URL is present in Settings, README, Fastlane metadata, and release docs before the signed release build.
- Check that every manifest permission, reviewed network endpoint,
  source-backed local storage surface, and SDK data surface has a Data safety
  matrix row before the signed release build.
- Check that the community guidelines doc, consent dialog, DataStore version
  key, and repository gates remain wired before the signed release build.
- Check that the owner-ready Play App content packet covers ads, app access,
  target audience, content rating notes, Data safety, UGC, generated content,
  sensitive permissions, and explicit owner actions before the signed release
  build.
- Check that GitHub/Obtainium/Izzy/F-Droid channel status, anti-feature notes,
  manifest permissions, reviewed network services, and proprietary dependency
  markers remain disclosed before the signed release build.
- Check that package/version metadata, Fastlane text, README links, release
  preflight commands, privacy URLs, and expected release artifacts remain
  consistent before the signed release build.
- Check that the SBOM decision, current evidence floor, future SBOM artifact
  names, future SBOM scope, and workflow wiring remain documented before the
  signed release build.
- Check that WorkManager unique work names, enqueue policies, constraints,
  deferral reasons, and scheduler source terms remain documented before the
  signed release build.
- Check that background worker connected/unmetered network posture, Data Saver
  gaps, and release risk remain documented before the signed release build.
- Check dependency notice, native compliance, and curated overlay drift gates.
- Generate `SHA256SUMS.txt` and `RELEASE_NOTES.md`.
- Run `tools/release_artifact_bundle_check.py` against the final `release/` directory.
- Upload the bundle with `actions/upload-artifact`.
- Do not create or update a GitHub Release, because the public release upload step is tag-only.

Dry-run workflow artifacts use the workflow's configured 30-day retention window. Tagged public releases keep `GOOGLE-OSS-RAW-INPUTS.zip` attached as release evidence under [raw-oss-input-retention.md](raw-oss-input-retention.md).

Run from GitHub UI:

1. Open Actions.
2. Select `Build & Release APK`.
3. Select `Run workflow`.
4. Choose `main`.
5. Set `android_developer_verification_status` to the current owner-confirmed state, usually `owner-confirmation-required`.
6. Start the run.
7. Download the `aura-release-*` workflow artifact.
8. Confirm it contains the APK, `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, `NATIVE-COMPLIANCE.md`, `SHA256SUMS.txt`, `RELEASE_NOTES.md`, `apksigner.txt`, and `aapt-badging.txt`.

Run from GitHub CLI:

```powershell
gh workflow run "Build & Release APK" --ref main -f android_developer_verification_status=owner-confirmation-required
```

## Bundle validator

Before the APK build, release dry runs also validate committed store metadata:

```bash
python3 tools/store_metadata_preflight.py --repo-root .
python3 tools/store_asset_pipeline_check.py --policy docs/distribution/store-assets.json --repo-root .
python3 tools/privacy_policy_link_check.py --policy docs/privacy/privacy-policy-link.json --repo-root .
python3 tools/privacy_data_safety_check.py --policy docs/privacy/data-safety.json --repo-root .
python3 tools/rotation_boot_permission_check.py --policy docs/rotation-trigger-boot-behavior.json --repo-root .
python3 tools/rotation_fgs_policy_check.py --policy docs/rotation-trigger-fgs-policy.json --repo-root .
python3 tools/background_work_scheduling_check.py --policy docs/background-work-scheduling-ledger.json --repo-root .
python3 tools/background_work_network_check.py --policy docs/background-work-network-posture.json --repo-root .
python3 tools/community_guidelines_consent_check.py --repo-root .
python3 tools/play_app_content_packet_check.py --policy docs/distribution/play-app-content.json --repo-root .
python3 tools/alt_store_metadata_check.py --policy docs/distribution/alt-store-metadata.json --repo-root .
python3 tools/release_metadata_consistency_check.py --policy docs/distribution/release-metadata-consistency.json --repo-root .
python3 tools/sbom_readiness_check.py --policy docs/distribution/sbom-readiness.json --repo-root .
```

The text-mode check fails when title or description limits drift, the current
versionCode changelog is missing or stale, stale branding returns, or the
Fastlane full description loses the public privacy-policy URL. The optional
`--require-assets` mode remains reserved for the screenshot and feature-graphic
pipeline. The privacy link gate fails when the in-app Settings link, README,
Fastlane metadata, release workflow, or this dry-run document loses the public
privacy-policy URL contract. The Data safety matrix fails when
`AndroidManifest.xml` gains, removes, or changes a permission, the reviewed
network endpoint inventory changes, local storage rows cite missing source
files, or SDK rows cite missing Gradle dependency markers/source files without
matching purpose, data type, collection/sharing, retention, deletion,
denial/user-control, backup posture, and Play declaration rows.
The rotation boot permission gate fails when
`android.permission.RECEIVE_BOOT_COMPLETED` returns to the manifest, when
boot-completed receiver terms appear in app source, or when release
disclosures still claim boot scheduling.
The rotation foreground-service policy gate fails when the special-use service
permission, manifest subtype, source safeguards, Play declaration packet,
owner evidence action, or workflow wiring drifts.
The background work scheduling ledger gate fails when a WorkManager row,
unique work name, enqueue policy, constraint, deferral reason, source term,
release doc, or workflow command drifts from the checked scheduler matrix.
The background work network posture gate fails when connected/unmetered
posture, Data Saver gap text, source terms, release docs, or workflow commands
drift from the checked worker network matrix.
The community guidelines consent gate fails when the legal guidelines doc,
versioned preference, shared consent dialog, Settings entry, community screens,
or repository access gates drift apart.
The Play App content packet fails when the owner-ready declaration file loses
required sections, source URLs, evidence paths, no-ads/privacy alignment,
target-audience guardrails, UGC/generated-content controls, sensitive
permission rows, or required owner actions.
The alternative-store disclosure matrix fails when channel statuses,
anti-feature notes, manifest permissions, reviewed network services,
proprietary dependency markers, or Izzy/F-Droid submission notes drift from the
current full-build decision.
The release metadata consistency gate fails when package/version metadata,
Fastlane text, README links, privacy URLs, preflight commands, packet package
names, or expected release artifact documentation drift apart.
The store asset pipeline gate fails when the capture-pending status, Fastlane
image paths, four planned phone screenshot slots, feature-graphic dimensions,
alt text, source URLs, or workflow wiring drift apart. After real assets are
committed, the future asset-mode command is:

```bash
python3 tools/store_metadata_preflight.py --repo-root . --require-assets --min-phone-screenshots 4
```

The SBOM readiness gate fails when the deferred-until-N-1 decision, current
evidence paths, future SBOM artifact names, future scope, source URLs, release
docs, or workflow wiring drift apart.

The release workflow validates the final directory before upload:

```bash
python3 tools/release_artifact_bundle_check.py \
  --release-dir "$RELEASE_DIR" \
  --apk-name "$APK_NAME" \
  --version-name "$VERSION_NAME" \
  --version-code "$VERSION_CODE"
```

The check fails when:

- A required artifact is missing or empty.
- The APK name does not match the version name and version code.
- `SHA256SUMS.txt` is missing the APK, third-party notice, raw Google OSS input archive, or native-compliance digest.
- A recorded checksum does not match the file bytes.
- `RELEASE_NOTES.md` lacks the APK digest, notice/native-compliance entries, signing certificate, attestation line, build type, or package ID.
- `apksigner.txt` lacks the signing certificate SHA-256 digest.
- `aapt-badging.txt` reports `application-debuggable`.

## Local script smoke test

The validator can be exercised without a full APK build by creating a temporary release directory with tiny placeholder files. This checks parser and checksum behavior only; it does not replace a GitHub Actions dry run with real signing secrets.

```powershell
$tmp = Join-Path $env:TEMP "aura-release-bundle-smoke"
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $tmp | Out-Null
$apk = "Aura-v6.31.1-versionCode-112-universal-release.apk"
"apk" | Set-Content -Encoding ascii (Join-Path $tmp $apk)
"third-party" | Set-Content -Encoding ascii (Join-Path $tmp "THIRD-PARTY-NOTICES.md")
"raw" | Set-Content -Encoding ascii (Join-Path $tmp "GOOGLE-OSS-RAW-INPUTS.zip")
"native" | Set-Content -Encoding ascii (Join-Path $tmp "NATIVE-COMPLIANCE.md")
"Signer #1 certificate SHA-256 digest: test" | Set-Content -Encoding ascii (Join-Path $tmp "apksigner.txt")
"package: name='com.freevibe'" | Set-Content -Encoding ascii (Join-Path $tmp "aapt-badging.txt")
$apkHash = (Get-FileHash (Join-Path $tmp $apk) -Algorithm SHA256).Hash.ToLower()
$thirdHash = (Get-FileHash (Join-Path $tmp "THIRD-PARTY-NOTICES.md") -Algorithm SHA256).Hash.ToLower()
$rawHash = (Get-FileHash (Join-Path $tmp "GOOGLE-OSS-RAW-INPUTS.zip") -Algorithm SHA256).Hash.ToLower()
$nativeHash = (Get-FileHash (Join-Path $tmp "NATIVE-COMPLIANCE.md") -Algorithm SHA256).Hash.ToLower()
@"
$apkHash  $apk
$thirdHash  THIRD-PARTY-NOTICES.md
$rawHash  GOOGLE-OSS-RAW-INPUTS.zip
$nativeHash  NATIVE-COMPLIANCE.md
"@ | Set-Content -Encoding ascii (Join-Path $tmp "SHA256SUMS.txt")
@"
Aura 6.31.1 (versionCode 112)

Signed release artifact:
- APK: $apk
- APK SHA-256: $apkHash
- Third-party notices: THIRD-PARTY-NOTICES.md
- Raw Google OSS inputs: GOOGLE-OSS-RAW-INPUTS.zip
- Native compliance packet: NATIVE-COMPLIANCE.md
- Signing certificate SHA-256: test
- GitHub artifact attestation: test
- Build type: release, android:debuggable=false

Android developer verification:
- Package: com.freevibe
"@ | Set-Content -Encoding ascii (Join-Path $tmp "RELEASE_NOTES.md")
python tools\release_artifact_bundle_check.py --release-dir $tmp --apk-name $apk --version-name 6.31.1 --version-code 112
```

Expected output:

```json
{"apk": "Aura-v6.31.1-versionCode-112-universal-release.apk", "releaseDir": "<temp path>", "status": "ok", "versionCode": "112", "versionName": "6.31.1"}
```

## Sources

- GitHub manual workflow runs: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
- GitHub workflow dispatch event: https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows
- GitHub workflow artifacts: https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts
- GitHub release assets: https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
