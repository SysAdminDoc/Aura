# Supply-chain verification

Aura is side-loaded through GitHub Releases and Obtainium, so release artifacts
need locally reproducible evidence before publication. GitHub hosts the checked
release files; local tooling is the source of truth for build, notice, native,
checksum, and release-note validation.

## Active controls

| Control | Local source | Purpose |
| --- | --- | --- |
| Signed release APK | `.\gradlew.bat :app:assembleFullRelease --stacktrace --no-daemon` | Builds the non-debuggable GitHub/Obtainium release variant with local signing inputs. |
| Signed Play AAB | `.\gradlew.bat -PauraReleaseChannel=play :app:bundleFullRelease --stacktrace --no-daemon` | Builds the Play-ready app bundle for the same version and upload key, with GitHub-only providers disabled by the compiled channel policy. |
| Release bundle validator | `tools/release_artifact_bundle_check.py` | Fails dry runs when the final APK/AAB/notices/native/checksum/release-note bundle is incomplete or internally inconsistent. |
| Third-party notices | `tools/google_oss_to_markdown.py` | Generates `THIRD-PARTY-NOTICES.md` from the release OSS license task output. |
| Raw Google OSS inputs | `tools/google_oss_raw_archive.py`, `docs/distribution/raw-oss-input-retention.md` | Archives generated license metadata and raw text inputs as `GOOGLE-OSS-RAW-INPUTS.zip`. |
| Dependency notice lockfile | `tools/dependency_notice_lock.py --mode check` | Fails when generated release dependency notices or raw metadata rows drift without review. |
| Dependency notice overlay | `tools/dependency_overlay_check.py --overlay` | Requires curated source, license, usage, and release-review metadata for high-risk dependencies and native payloads. |
| Dependency license policy | `tools/dependency_license_policy.py --policy` | Fails when curated dependency or native-payload license IDs are disallowed, unknown, or missing required review notes. |
| Native compliance packet | `tools/native_compliance_inventory.py --mode check-lock` | Inventories youtubedl-android, yt-dlp/Python, FFmpeg, QuickJS, and NewPipeExtractor payload evidence and publishes `NATIVE-COMPLIANCE.md`. |
| Native alignment packet | `tools/native_alignment_check.py` | Records Android native page-alignment evidence as `NATIVE-ALIGNMENT.json`. |
| Gradle dependency verification | `gradle/verification-metadata.xml` | Records SHA-256 checksums for resolved Gradle plugins and app dependencies. |
| Gradle wrapper policy | `tools/gradle_wrapper_check.py` | Pins the Gradle wrapper distribution URL, SHA-256, validation, storage roots, and timeout. |
| Provider credential release guard | `tools/provider_credential_release_check.py` | Confirms release variants force optional provider keys blank even when a developer has local debug keys configured. |
| Provider credential APK scan | `tools/provider_credential_apk_scan.py` | Scans packaged signed APKs for nonblank local provider values before publication. |
| Provider credential storage policy | `tools/provider_credential_storage_check.py` | Checks Android Keystore AES-GCM storage, restore recovery, backup exclusions, clear controls, export boundaries, and privacy/support disclosures for user-entered credentials. |
| Cleartext release guard | `tools/cleartext_release_check.py` | Rejects cleartext manifest/network drift in public releases. |
| Network endpoint inventory | `tools/network_endpoint_inventory_check.py` | Fails when provider network-code hosts drift from the reviewed endpoint inventory. |
| Store metadata preflight | `tools/store_metadata_preflight.py` | Checks Fastlane text limits, current changelog, branding, and privacy-policy URL. |
| Store asset pipeline | `tools/store_asset_pipeline_check.py` | Checks screenshot/feature-graphic planning, Fastlane image paths, alt text, and future asset-mode command. |
| Privacy policy link gate | `tools/privacy_policy_link_check.py` | Keeps Settings, Fastlane metadata, README, and release dry-run docs aligned to the public privacy-policy URL. |
| Privacy Data safety matrix | `tools/privacy_data_safety_check.py` | Keeps permissions, network endpoints, local storage, SDK data surfaces, and both Android backup rule formats mapped to Play declaration rows. |
| Rotation trigger boot permission | `tools/rotation_boot_permission_check.py` | Keeps the removed boot-completed permission decision aligned with manifest and release disclosures. |
| Rotation foreground-service policy | `tools/rotation_fgs_policy_check.py` | Keeps foreground-service declarations, Settings, Play, and release evidence aligned. |
| Background work scheduling ledger | `tools/background_work_scheduling_check.py` | Checks WorkManager unique work names, enqueue policies, constraints, deferral reasons, source terms, and release docs. |
| Background work network posture | `tools/background_work_network_check.py` | Checks worker network posture, Data Saver gaps, privacy surfaces, scheduler source terms, and release docs. |
| Background work device evidence | `tools/background_work_device_evidence_check.py` | Checks the device/emulator scheduler evidence plan, adb/dumpsys commands, source URLs, and release docs. |
| Community guidelines consent | `tools/community_guidelines_consent_check.py` | Checks UGC guidelines, consent state, Settings entry, community screens, repository gates, and Play packet evidence. |
| Library transfer contract | `tools/export_format_check.py` | Keeps every backup limit tied to the runtime contract and rejects silent item truncation or unstaged publication. |
| Provider truth contract | `tools/provider_truth_check.py` | Checks provider media, lifecycle, priority, credentials, builds, channels, actions, app diagnostics, README, Play copy, and distribution packets against one manifest. |
| Play App content packet | `tools/play_app_content_packet_check.py` | Keeps Play app access, target audience, content rating, Data safety, UGC, generated content, and sensitive-permission evidence aligned. |
| Alternative-store disclosure matrix | `tools/alt_store_metadata_check.py` | Keeps GitHub/Obtainium/Izzy/F-Droid channel status, permission disclosures, network service rows, and proprietary dependency markers aligned. |
| Release metadata consistency | `tools/release_metadata_consistency_check.py` | Keeps package/version metadata, Fastlane text, README links, privacy URLs, release docs, and artifact lists aligned. |
| SBOM readiness policy | `tools/sbom_readiness_check.py` | Keeps the deferred SBOM decision, current evidence floor, future artifact names, future scope, and local release wiring aligned. |
| GitHub workflow policy guards | `tools/github_actions_allowlist_check.py`, `tools/github_workflow_permissions_check.py`, `tools/github_workflow_secrets_check.py`, `tools/github_security_workflow_check.py` | Confirm the repository has no required workflow files under the current local-only policy and fail if a workflow is reintroduced without policy review. |
| F-Droid blocker preflight | `tools/fdroid_preflight.py` | Confirms that the mainline full build remains blocked from F-Droid until proprietary dependency boundaries change. |

## Release verification

For each `v*` release:

1. Build the signed release APK locally and name it `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
2. Build the signed release AAB locally and name it `Aura-vX.Y.Z-versionCode-N-play-release.aab`.
3. Generate and inspect `THIRD-PARTY-NOTICES.md`.
4. Generate and inspect `GOOGLE-OSS-RAW-INPUTS.zip`; this archive is retained with every public release under [raw-oss-input-retention.md](raw-oss-input-retention.md).
5. Generate and inspect `NATIVE-COMPLIANCE.md`.
6. Generate and inspect `NATIVE-ALIGNMENT.json`.
7. Write `apksigner.txt` from `apksigner verify --verbose --print-certs`.
8. Write `aapt-badging.txt` from `aapt dump badging` and confirm it does not report `application-debuggable`.
9. Write `aab-manifest.txt`, `bundletool-validate.txt`, `aab-jarsigner.txt`, `aab-keytool.txt`, and `PLAY-APP-SIGNING-OWNER-STEPS.txt` from the Play AAB validation commands.
10. Write `SHA256SUMS.txt` with digests for the APK, AAB, notices, raw Google OSS archive, native compliance packet, and native alignment packet.
11. Write `RELEASE_NOTES.md` with the APK SHA-256, AAB SHA-256, third-party notice entry, raw Google OSS input archive entry, native compliance packet entry, native alignment packet entry, signing certificate SHA-256, upload-key SHA-256, Play App Signing owner steps, local build receipt, package ID, and release build type.
12. Run `tools/release_artifact_bundle_check.py` against the final release directory.
13. Upload the checked files with `gh release create` or `gh release upload --clobber`.
14. Compare the GitHub Release assets to local `SHA256SUMS.txt` after upload.

## Local release checks

Commit the release candidate and run the exact-commit umbrella check before the
release APK build:

```bash
python3 tools/release_clean_clone_check.py --repo-root . --revision HEAD
```

It archives only Git-tracked inputs, verifies the required evidence set, and
runs the local release, documentation, and legal gates listed in the release
metadata policy. It reports owner-only signing, artifact, console, publication,
and device evidence with pass, fail, or unknown status.

The umbrella command runs these source-backed checks in its isolated tree:

```bash
python3 tools/github_actions_allowlist_check.py --policy docs/distribution/github-actions-allowlist.json --repo-root .
python3 tools/github_workflow_permissions_check.py --policy docs/distribution/github-workflow-permissions.json --repo-root .
python3 tools/github_workflow_secrets_check.py --policy docs/distribution/github-workflow-secrets.json --repo-root .
python3 tools/github_security_workflow_check.py --policy docs/distribution/github-security-workflows.json --repo-root .
python3 tools/gradle_wrapper_check.py --properties gradle/wrapper/gradle-wrapper.properties
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
python3 tools/export_format_check.py --spec docs/data/export-format.json --repo-root .
python3 tools/provider_truth_check.py --manifest docs/providers/provider-manifest.json --repo-root .
python3 tools/play_app_content_packet_check.py --policy docs/distribution/play-app-content.json --repo-root .
python3 tools/alt_store_metadata_check.py --policy docs/distribution/alt-store-metadata.json --repo-root .
python3 tools/release_metadata_consistency_check.py --policy docs/distribution/release-metadata-consistency.json --repo-root .
python3 tools/sbom_readiness_check.py --policy docs/distribution/sbom-readiness.json --repo-root .
python3 tools/on_device_ai_decision_check.py --policy docs/ai/on-device-wallpaper-decision.json --repo-root .
python3 -m unittest discover -s test/tools -p '*_test.py'
```

Run these checks while assembling the release evidence:

```powershell
.\gradlew.bat :app:fullReleaseOssLicensesTask --stacktrace --no-daemon
python tools\dependency_notice_lock.py --mode check --variant fullRelease --lockfile docs\legal\dependency-notices.lock.json
python tools\dependency_notice_lock.py --mode check-metadata --variant fullRelease --lockfile docs\legal\dependency-notices.lock.json
python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json
python tools\dependency_overlay_check.py --overlay docs\legal\dependency-notice-overrides.json
python tools\dependency_license_policy.py --policy docs\legal\dependency-license-policy.json --overlay docs\legal\dependency-notice-overrides.json
python tools\google_oss_to_markdown.py --variant fullRelease --output build\reports\THIRD-PARTY-NOTICES.md
python tools\google_oss_raw_archive.py --variant fullRelease --output build\reports\GOOGLE-OSS-RAW-INPUTS.zip
python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md
python tools\provider_credential_apk_scan.py --local-properties local.properties --apk release\Aura-vX.Y.Z-versionCode-N-universal-release.apk
python tools\release_artifact_bundle_check.py --release-dir release --apk-name Aura-vX.Y.Z-versionCode-N-universal-release.apk --aab-name Aura-vX.Y.Z-versionCode-N-play-release.aab --version-name X.Y.Z --version-code N
```

## Release dry runs

Release dry runs are local. They build the signed release APK and Play-ready
AAB, generate third-party notices, archive raw Google OSS inputs, generate the
native compliance and alignment packets, run lock/overlay/license gates, produce
checksums and release notes, and validate the final bundle before anything is
uploaded to GitHub.

Procedure: [release-dry-run.md](release-dry-run.md).

## GitHub workflow policy

Aura's current distribution policy is local builds only. The policy JSON files
under `docs/distribution/github-*.json` intentionally contain empty required
workflow sets. The matching Python checks pass when `.github/workflows` is
absent and fail if workflow files, workflow permissions, workflow secrets, or
security workflow controls are reintroduced without updating the policy in the
same change.

## Third-party notices

Google's OSS Licenses Gradle task is the release notice input. Aura does not add
the `play-services-oss-licenses` runtime dependency or stock Google notice
activity because that runtime path pulls broad UI dependency upgrades on the
current AGP 8.9.3 / Gradle 8.12 stack.

Generated dependency notices do not replace Aura's content-source disclosures.
`ProviderCapability.kt` is the production registry, and
`docs/providers/provider-manifest.json` is its checked public mirror. The
provider-truth gate rejects drift in media types, lifecycle, default order,
credentials, build or channel availability, and permitted actions. Settings >
Open source licenses uses the same registry order while keeping provider
disclosures visible in-app.

## Raw Google OSS inputs

`GOOGLE-OSS-RAW-INPUTS.zip` preserves the exact generated files used by the
markdown converter and dependency notice lock:

- `dependencies.json`
- `third_party_license_metadata`
- `third_party_licenses`
- `MANIFEST.json`

The manifest records source paths, file sizes, and SHA-256 digests for each raw
input. Keep this archive beside `THIRD-PARTY-NOTICES.md` in tagged GitHub
Releases so future drift reviews can inspect the raw Google OSS output without
rerunning Gradle.

## Native compliance packet

`NATIVE-COMPLIANCE.md` is a factual inventory, not legal advice. It records
artifact hashes, ABI payload paths, nested yt-dlp/Python facts, FFmpeg payload
entries, and upstream source/build references that release owners must review.

`docs/legal/ffmpeg-source-correspondence.md` is the FFmpeg-specific release
review checklist. It records the resolved FFmpeg AAR hash, nested
`libffmpeg.zip.so` hashes, embedded FFmpeg configure lines, the FFmpeg source
candidate, and remaining owner actions for source correspondence.

Treat a youtubedl-android, NewPipeExtractor, yt-dlp, Python, QuickJS, or FFmpeg
version change as a required native packet refresh. When FFmpeg payload facts
change, refresh `docs/legal/ffmpeg-source-correspondence.md` in the same change
and keep the unresolved-owner-action section accurate.

## Gradle dependency verification

Gradle checksum metadata is committed at `gradle/verification-metadata.xml`.
Regenerate it only during a dependency-resolution maintenance pass.

Use Adoptium JDK 21:

```powershell
$env:JAVA_HOME = "C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"
.\gradlew.bat --write-verification-metadata sha256 :app:dependencies --stacktrace --no-daemon
```

Then review and commit the resulting diff. Future dependency changes should
update `gradle/verification-metadata.xml` in the same commit as the version or
catalog change.

## Provider credential release guard

Provider API keys and client IDs are optional user-controlled settings. Public
release builds must not bundle local Pexels, Pixabay, or
Stability values into `BuildConfig`. For local development, a nonblank ignored
`local.properties` provider key fails by default. Use
`--allow-nonblank-local-provider-keys` only for an explicitly internal build
review; public GitHub, Obtainium, and Izzy builds must keep provider defaults
blank and rely on user-entered settings.

After packaging, scan the signed APK with `tools/provider_credential_apk_scan.py`.
The scanner reports property names and APK entries only, not credential values.

## SBOM scope

SBOM generation is deferred until after the N-1 toolchain upgrade because the
Android dependency graph is already scheduled for a large AGP/Gradle/Kotlin/KSP
migration. The deferred decision is checked by [sbom-readiness.md](sbom-readiness.md)
and `tools/sbom_readiness_check.py` so the release evidence floor cannot drift
while Aura waits for the toolchain change.

When N-1 lands, add a CycloneDX or SPDX SBOM lane that covers:

- Gradle plugins and version-catalog dependencies.
- Release runtime dependency graph.
- Native, FFmpeg, and youtubedl-android payloads.
- Release APK/AAB digests and signing certificate fingerprints.

The reviewed candidate artifact names are `SBOM.cyclonedx.json`,
`SBOM.cyclonedx.xml`, and `SBOM.spdx.json`. The chosen artifact should be
included in `SHA256SUMS.txt`, tagged GitHub Release assets, release notes, and
any local provenance evidence supported by the selected format.
