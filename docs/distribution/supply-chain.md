# Supply-chain verification

Aura is side-loaded through GitHub Releases and Obtainium, so release artifacts need proof that is stronger than a plain APK attachment.

## Active controls

| Control | Location | Purpose |
| --- | --- | --- |
| Signed release APK | `.github/workflows/release.yml` | Builds the release variant, verifies the signature, rejects debuggable APKs, and publishes checksums. |
| Release bundle validator | `tools/release_artifact_bundle_check.py`, `.github/workflows/release.yml` | Fails manual dry runs and tag releases when the final APK/notices/native/checksum/release-note bundle is incomplete or internally inconsistent. |
| Third-party notices | `tools/google_oss_to_markdown.py`, `.github/workflows/release.yml` | Runs the Google OSS Licenses Gradle task for the release variant and publishes `THIRD-PARTY-NOTICES.md` next to the APK. |
| Raw Google OSS inputs | `tools/google_oss_raw_archive.py`, `.github/workflows/release.yml`, `docs/distribution/raw-oss-input-retention.md` | Archives generated `dependencies.json`, license metadata, and raw license text inputs for drift review, and keeps the archive attached to every tagged public release. |
| Dependency notice lockfile | `tools/dependency_notice_lock.py`, `docs/legal/dependency-notices.lock.json` | Fails PR/main/release checks when generated release dependency notices or raw metadata rows drift without review. |
| Dependency notice overlay | `tools/dependency_overlay_check.py`, `docs/legal/dependency-notice-overrides.json` | Requires curated source, license, usage, and release-review metadata for high-risk generated dependencies and native payloads. |
| Dependency license policy | `tools/dependency_license_policy.py`, `docs/legal/dependency-license-policy.json` | Fails PR/main/release checks when curated dependency or native-payload license IDs are disallowed, unknown, or missing required review notes. |
| Native compliance packet | `tools/native_compliance_inventory.py`, `.github/workflows/release.yml` | Inventories youtubedl-android, yt-dlp/Python, FFmpeg, QuickJS, and NewPipeExtractor payload evidence and publishes `NATIVE-COMPLIANCE.md` next to the APK. |
| Native compliance lockfile | `tools/native_compliance_inventory.py`, `docs/legal/native-compliance.lock.json` | Fails PR/main/release checks when native/copyleft artifact hashes or extracted payload facts drift without review. |
| FFmpeg source correspondence checklist | `docs/legal/ffmpeg-source-correspondence.md` | Records resolved FFmpeg AAR/payload hashes, embedded version/configure evidence, source candidates, and remaining owner actions for source correspondence. |
| Artifact attestation | `.github/workflows/release.yml` | Uses `actions/attest@v4` against `release/SHA256SUMS.txt` so release artifact digests are bound to the GitHub Actions build. |
| Gradle dependency verification | `gradle/verification-metadata.xml` | Records SHA-256 checksums for resolved Gradle plugins and app dependencies. |
| Gradle wrapper policy | `gradle/wrapper/gradle-wrapper.properties`, `tools/gradle_wrapper_check.py`, `.github/workflows/verify.yml` | Pins the Gradle 8.12 wrapper distribution SHA-256, keeps URL validation enabled, and rejects wrapper distribution drift. |
| Provider credential release guard | `tools/provider_credential_release_check.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Fails release preflight when optional provider keys from `local.properties` would be bundled into `BuildConfig`; release CI writes blank provider defaults before signed APK builds. |
| Provider credential APK scan | `tools/provider_credential_apk_scan.py`, `.github/workflows/release.yml` | Scans packaged signed APKs for any nonblank provider values from release `local.properties` before notices, checksums, uploads, or tagged publication. |
| Provider credential storage policy | `docs/security/provider-credential-storage.json`, `docs/security/provider-credential-storage.md`, `tools/provider_credential_storage_check.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Classifies each provider credential, documents the no-Keystore storage decision, proves DataStore backup/transfer exclusions, and checks explicit Settings clear controls plus diagnostics/privacy disclosures. |
| Cleartext release guard | `tools/cleartext_release_check.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Fails release preflight when network security config enables cleartext, the manifest explicitly enables cleartext, or provider-network code reintroduces raw HTTP URLs or OkHttp HTTP scheme builders. |
| Network endpoint inventory | `docs/security/network-endpoints.json`, `docs/security/network-endpoints.md`, `tools/network_endpoint_inventory_check.py`, `.github/workflows/verify.yml` | Fails verification when app network-code URL hosts drift from the reviewed endpoint/auth/cache/fallback inventory. |
| Store metadata preflight | `tools/store_metadata_preflight.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Fails PR/main/release checks when Fastlane text metadata exceeds Play limits, loses the current versionCode changelog, reintroduces stale branding, or drops the public privacy-policy URL. Asset mode is available for the screenshot and feature-graphic pipeline. |
| Privacy policy link gate | `docs/privacy/privacy-policy-link.json`, `tools/privacy_policy_link_check.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Fails PR/main/release checks when the public privacy-policy URL is missing from Settings, Fastlane metadata, README, release dry-run docs, or the release workflow gate. |
| Privacy Data safety matrix | `docs/privacy/data-safety.json`, `docs/privacy/data-safety.md`, `tools/privacy_data_safety_check.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Fails PR/main/release checks when manifest permissions, reviewed network endpoint IDs, source-backed local storage surfaces, or SDK dependency/data surfaces drift without matching data type, collection/sharing, retention, deletion, user-control, backup posture, and Play declaration rows. |
| Community guidelines consent | `docs/legal/community-guidelines.md`, `tools/community_guidelines_consent_check.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Fails PR/main/release checks when the guidelines doc, versioned DataStore key, consent dialog, Settings entry, community screens, repository gates, or Play packet UGC evidence drift apart. |
| Play App content packet | `docs/distribution/play-app-content.json`, `docs/distribution/play-app-content.md`, `tools/play_app_content_packet_check.py`, `.github/workflows/verify.yml`, `.github/workflows/release.yml` | Fails PR/main/release checks when ads, app access, target audience, content rating notes, Data safety, UGC, generated content, sensitive permissions, evidence paths, source URLs, or owner actions drift from the owner-ready Play packet. |
| On-device wallpaper decision gate | `docs/ai/on-device-wallpaper-decision.json`, `tools/on_device_ai_decision_check.py`, `.github/workflows/verify.yml` | Keeps on-device wallpaper generation on hold until device baseline, delivery, battery/thermal, license, moderation, fallback, and FOSS-channel evidence is complete, and blocks early production runtime dependencies or model artifacts. |
| Dependency Review | `.github/workflows/dependency-review.yml` | Runs on pull requests and fails high/critical vulnerable dependency additions. |
| OpenSSF Scorecard | `.github/workflows/scorecard.yml` | Runs on main pushes, branch-protection changes, weekly schedule, and manual dispatch; keeps public result publishing disabled and uploads SARIF to code scanning. |
| GitHub Actions allowlist | `docs/distribution/github-actions-allowlist.json`, `tools/github_actions_allowlist_check.py`, `.github/workflows/verify.yml` | Fails verification when workflow files use unreviewed actions, local actions, unpinned refs, forbidden floating refs, or unexpected workflow files. |
| GitHub workflow permissions policy | `docs/distribution/github-workflow-permissions.json`, `tools/github_workflow_permissions_check.py`, `.github/workflows/verify.yml` | Fails verification when workflow events, workflow-level permissions, job-level permissions, expected jobs, or expected workflow files drift without review. |
| GitHub workflow secret policy | `docs/distribution/github-workflow-secrets.json`, `tools/github_workflow_secrets_check.py`, `.github/workflows/verify.yml` | Fails verification when workflow secret references, release secret env aliases, forbidden token shortcuts, or expected workflow files drift without review. |
| GitHub security workflow policy | `docs/distribution/github-security-workflows.json`, `tools/github_security_workflow_check.py`, `.github/workflows/verify.yml` | Fails verification when dependency review, scorecard, or release workflow security controls drift or add unsafe trigger/permission escape hatches. |
| Backend tool unit tests | `test/tools`, `.github/workflows/verify.yml` | Runs the Python backend/tool unit suite on every verify workflow invocation before Android setup, so policy and support-tool drift tests are not only path-gated by Firebase rule changes. |
| Dependabot update policy | `.github/dependabot.yml`, `tools/dependabot_config_check.py`, `.github/workflows/verify.yml` | Opens weekly version-update PRs for GitHub Actions, Gradle, root Firebase npm, and Functions npm with small PR limits and checked schedule/label/target-branch policy. |
| GitHub security settings receipt | `docs/distribution/github-security-settings-evidence.md`, `tools/github_security_settings_receipt.py` | Validates owner-provided private evidence for branch protection, Dependabot, code scanning, secret scanning, and release attestation settings before emitting a redacted receipt. |
| F-Droid blocker preflight | `tools/fdroid_preflight.py` | Confirms that F-Droid mainline remains blocked until proprietary dependency boundaries change. |

## Release verification

For each `v*` release:

1. Confirm the GitHub Release contains `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
2. Confirm the GitHub Release contains `THIRD-PARTY-NOTICES.md`.
3. Confirm the GitHub Release contains `GOOGLE-OSS-RAW-INPUTS.zip`; this archive is a permanent public release asset under [raw-oss-input-retention.md](raw-oss-input-retention.md).
4. Confirm the GitHub Release contains `NATIVE-COMPLIANCE.md`.
5. Confirm `SHA256SUMS.txt` includes the APK, `THIRD-PARTY-NOTICES.md`, `GOOGLE-OSS-RAW-INPUTS.zip`, and `NATIVE-COMPLIANCE.md`.
6. Confirm the generated release notes include the APK SHA-256, third-party notices entry, raw Google OSS input archive entry, native compliance packet entry, signing certificate SHA-256, and artifact attestation URL.
7. Spot-check `THIRD-PARTY-NOTICES.md` for high-risk dependencies such as NewPipeExtractor, youtubedl-android, Firebase, Play services ML Kit, ZXing, Palette, and ProfileInstaller.
8. Spot-check `GOOGLE-OSS-RAW-INPUTS.zip` for `dependencies.json`, `third_party_license_metadata`, `third_party_licenses`, and `MANIFEST.json`.
9. Spot-check `docs/legal/dependency-notice-overrides.json` when any high-risk dependency or payload changed intentionally.
10. Spot-check `docs/legal/dependency-license-policy.json` when a curated overlay license ID, required coordinate prefix, required payload target, or disallowed pattern changes.
11. Spot-check `NATIVE-COMPLIANCE.md` for youtubedl-android library, youtubedl-android ffmpeg, yt-dlp, Python, QuickJS, FFmpeg, and NewPipeExtractor evidence.
12. Spot-check `docs/legal/ffmpeg-source-correspondence.md` when any FFmpeg payload hash, version, configure evidence, or youtubedl-android FFmpeg coordinate changes.
13. Confirm the release workflow ran `tools/release_artifact_bundle_check.py` before uploading artifacts.
14. Confirm the release workflow ran `tools/provider_credential_release_check.py` after writing release `local.properties` and before `:app:assembleRelease`.
15. Confirm the release workflow ran `tools/provider_credential_storage_check.py` before `:app:assembleRelease`.
16. Confirm the release workflow ran `tools/cleartext_release_check.py` before `:app:assembleRelease`.
17. Confirm the release workflow ran `tools/store_metadata_preflight.py`, `tools/privacy_policy_link_check.py`, `tools/privacy_data_safety_check.py`, `tools/community_guidelines_consent_check.py`, and `tools/play_app_content_packet_check.py` before `:app:assembleRelease`.
18. Confirm the release workflow ran `tools/provider_credential_apk_scan.py` after packaging the signed APK and before release uploads.
19. Verify the APK locally with `apksigner verify --verbose --print-certs`.
20. Compare the local SHA-256 values to `SHA256SUMS.txt`.

## Release dry runs

Manual `workflow_dispatch` runs on `main` are Aura's release dry-run lane. They build the signed release APK, generate third-party notices, archive the raw Google OSS inputs, generate the native compliance packet, run the lock/overlay gates, produce checksums/release notes, validate the final bundle, and upload the result as a workflow artifact without creating a GitHub Release.

Procedure: [release-dry-run.md](release-dry-run.md).

## GitHub security workflows

`docs/distribution/github-security-workflows.json` records the security-sensitive workflow requirements for Dependency Review, OpenSSF Scorecard, and Release. The policy intentionally checks exact workflow snippets so review can see when trigger, permission, attestation, SARIF upload, release bundle, or dependency-verification behavior changes.

`docs/distribution/github-actions-allowlist.json` records the reviewed action refs allowed across every workflow, including the self-hosted performance workflow. The allowlist blocks unexpected workflow files, local actions, missing action refs, and floating refs such as `main`, `master`, or `latest`.

`docs/distribution/github-workflow-permissions.json` records the reviewed workflow event and permission baseline for every workflow. The policy keeps release-only write permissions isolated to the release job, requires read-only permissions for verify/Firebase/performance/Scorecard defaults, and fails unexpected jobs or event-trigger additions such as `pull_request_target`.

`docs/distribution/github-workflow-secrets.json` records the only reviewed workflow secret references. At present, only the release workflow may read the four release signing secrets, and those secrets must be assigned to reviewed environment variable names before use.

PR/main verification runs:

```bash
python3 tools/github_actions_allowlist_check.py --policy docs/distribution/github-actions-allowlist.json --repo-root .
python3 tools/github_workflow_permissions_check.py --policy docs/distribution/github-workflow-permissions.json --repo-root .
python3 tools/github_workflow_secrets_check.py --policy docs/distribution/github-workflow-secrets.json --repo-root .
python3 tools/github_security_workflow_check.py --policy docs/distribution/github-security-workflows.json --repo-root .
python3 tools/provider_credential_storage_check.py --policy docs/security/provider-credential-storage.json --repo-root .
python3 tools/cleartext_release_check.py --repo-root .
python3 tools/network_endpoint_inventory_check.py --inventory docs/security/network-endpoints.json --repo-root .
python3 tools/store_metadata_preflight.py --repo-root .
python3 tools/privacy_policy_link_check.py --policy docs/privacy/privacy-policy-link.json --repo-root .
python3 tools/privacy_data_safety_check.py --policy docs/privacy/data-safety.json --repo-root .
python3 tools/community_guidelines_consent_check.py --repo-root .
python3 tools/play_app_content_packet_check.py --policy docs/distribution/play-app-content.json --repo-root .
python3 tools/on_device_ai_decision_check.py --policy docs/ai/on-device-wallpaper-decision.json --repo-root .
python3 -m unittest discover -s test/tools -p '*_test.py'
```

The checks fail if a required control is missing, if a guarded workflow file is removed, if permissions drift, if a new job or secret reference is added without policy review, or if a forbidden escape hatch such as `pull_request_target`, `GITHUB_TOKEN`, release dependency-verification suppression, writable contents in Dependency Review, or persisted checkout credentials in Scorecard appears. Update the relevant policy in the same change as an intentional workflow hardening change, and keep the diff tied to the reviewed workflow file.

## Dependabot update policy

`.github/dependabot.yml` opens weekly version-update PRs against `main` for the dependency surfaces Aura currently uses:

- GitHub Actions workflows.
- Gradle plugins and version-catalog dependencies from the repository root.
- Root Firebase rules npm dependencies.
- Firebase Functions npm dependencies under `functions/`.

PR/main verification runs:

```bash
python3 tools/dependabot_config_check.py --config .github/dependabot.yml
```

The check fails if an expected surface is missing, an unsupported surface is added, update cadence or target branch drifts, open PR limits rise above five, or the `dependencies`/`security` labels and `deps` commit prefix are removed. Live Dependabot alerts, security updates, and repository security settings still require owner/admin confirmation in GitHub.

## GitHub security settings receipt

`github-security-settings-evidence.md` defines the private evidence schema and redacted receipt command for live GitHub repository settings. Use it only after an owner/admin captures current branch protection, required checks, Dependabot alerts/security updates, Scorecard SARIF code scanning, secret scanning, and release-attestation visibility.

Receipt command:

```bash
python3 tools/github_security_settings_receipt.py --workflow-policy docs/distribution/github-security-workflows.json --dependabot-config .github/dependabot.yml --settings-evidence private/github-security-settings-evidence.json --support-reference github-settings-<ticket-or-release> --output artifacts/github-security-settings-receipt.json
```

The receipt keeps policy and evidence hashes but omits raw repository names, private evidence paths, required-check names, screenshots, API responses, credentials, and tokens.

## Third-party notices

The release workflow uses Google's OSS Licenses Gradle plugin only. It intentionally does not add the `play-services-oss-licenses` runtime dependency or stock Google notice activity, because that runtime path was found to pull broad UI dependency upgrades on the current AGP 8.7.3 / Gradle 8.12 stack.

Generate notices locally after the release notice task has run:

```powershell
.\gradlew.bat :app:releaseOssLicensesTask --stacktrace --no-daemon
python tools\dependency_notice_lock.py --mode check --lockfile docs\legal\dependency-notices.lock.json
python tools\dependency_notice_lock.py --mode check-metadata --lockfile docs\legal\dependency-notices.lock.json
python tools\dependency_overlay_check.py --overlay docs\legal\dependency-notice-overrides.json
python tools\dependency_license_policy.py --policy docs\legal\dependency-license-policy.json --overlay docs\legal\dependency-notice-overrides.json
python tools\google_oss_to_markdown.py --variant release --output build\reports\THIRD-PARTY-NOTICES.md
python tools\google_oss_raw_archive.py --variant release --output build\reports\GOOGLE-OSS-RAW-INPUTS.zip
```

Generated dependency notices do not replace Aura's content-source disclosures. `ProviderDisclosure.kt` remains the source of truth for provider policy rows such as YouTube, Reddit, Pexels, Pixabay, community uploads, bundled media, and AI-generated content. Settings > Open source licenses links users to the latest release notice artifacts while keeping provider disclosures visible in-app.

## Raw Google OSS inputs

`GOOGLE-OSS-RAW-INPUTS.zip` preserves the exact generated files used by the markdown converter and dependency notice lock:

- `dependencies.json`
- `third_party_license_metadata`
- `third_party_licenses`
- `MANIFEST.json`

The manifest records source paths, file sizes, and SHA-256 digests for each raw input. Keep this archive beside `THIRD-PARTY-NOTICES.md` in workflow artifacts and tagged GitHub Releases so future drift reviews can inspect the raw Google OSS output without rerunning Gradle.

Retention decision: `GOOGLE-OSS-RAW-INPUTS.zip` remains attached to every tagged public GitHub Release that publishes `THIRD-PARTY-NOTICES.md`. Workflow artifacts remain useful for dry-run review, but they are retention-bound and are not the long-term release evidence surface. Full policy: [raw-oss-input-retention.md](raw-oss-input-retention.md).

## Dependency notice lockfile

`docs/legal/dependency-notices.lock.json` is generated from Google's release OSS outputs. It records:

- Sorted release dependency coordinates from `dependencies.json`.
- SHA-256 hashes for the generated dependency, license metadata, and license text inputs.
- Notice section names, offsets, lengths, and notice-text SHA-256 hashes.
- A metadata-only parity check that proves the raw Google OSS metadata rows still match the reviewed notice-section count and ranges.

PR/main verification and the release workflow run:

```bash
python3 tools/dependency_notice_lock.py --mode check --lockfile docs/legal/dependency-notices.lock.json
python3 tools/dependency_notice_lock.py --mode check-metadata --lockfile docs/legal/dependency-notices.lock.json
python3 tools/native_compliance_inventory.py --mode check-lock --lockfile docs/legal/native-compliance.lock.json
python3 tools/dependency_overlay_check.py --overlay docs/legal/dependency-notice-overrides.json
python3 tools/dependency_license_policy.py --policy docs/legal/dependency-license-policy.json --overlay docs/legal/dependency-notice-overrides.json
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

## Dependency license policy

`docs/legal/dependency-license-policy.json` is the release-owner policy for curated license IDs. Google's generated OSS notice lock records dependency coordinates and notice hashes, but it does not expose normalized SPDX license IDs. Aura therefore gates license policy through the curated overlay and native payload facts that already map high-risk coordinates and embedded payloads to owner-reviewed license IDs.

The policy currently:

- Allows reviewed permissive IDs such as Apache-2.0 and MIT.
- Requires release-owner review notes for GPL, Google SDK terms, PSF-plus-bundled-dependency, and mixed Unlicense payload entries.
- Fails unknown or disallowed license IDs in `docs/legal/dependency-notice-overrides.json`.
- Requires overlay coverage for the current Firebase, Google Play services, ML Kit, NewPipeExtractor, youtubedl-android, ProfileInstaller, ZXing, FFmpeg, Python, QuickJS, and yt-dlp surfaces.
- Scans extracted native payload facts for disallowed release modes such as nonfree FFmpeg flags.

PR/main verification and the release workflow run:

```bash
python3 tools/dependency_license_policy.py --policy docs/legal/dependency-license-policy.json --overlay docs/legal/dependency-notice-overrides.json
```

When a dependency or payload changes intentionally, update the generated locks, curated overlay, and policy file together. A new license ID should either be listed as allowed, listed as review-required with a review note in the overlay, or rejected by a disallowed pattern.

## Native compliance packet

`NATIVE-COMPLIANCE.md` is a factual inventory, not legal advice. It reads resolved Gradle cache artifacts and, in release CI, the final signed APK. It records artifact hashes, ABI payload paths, nested yt-dlp/Python facts, FFmpeg payload entries, and upstream source/build references that release owners must review.

`docs/legal/ffmpeg-source-correspondence.md` is the FFmpeg-specific release review checklist. It records the resolved `ffmpeg-0.18.1.aar` hash, nested `libffmpeg.zip.so` hashes, embedded FFmpeg 7.1.1 configure lines, the FFmpeg source candidate, and the remaining Termux-source/build-log evidence that owners must confirm before publishing changed FFmpeg payloads.

Generate the local packet after dependencies have been resolved:

```powershell
python tools\native_compliance_inventory.py --mode check-lock --lockfile docs\legal\native-compliance.lock.json
python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md
```

Release CI adds final APK inspection:

```bash
python3 tools/native_compliance_inventory.py --apk "$RELEASE_DIR/Aura-vX.Y.Z-versionCode-N-universal-release.apk" --output "$RELEASE_DIR/NATIVE-COMPLIANCE.md"
```

Treat a youtubedl-android, NewPipeExtractor, yt-dlp, Python, QuickJS, or FFmpeg version change as a required native packet refresh. The resolved FFmpeg AAR now exposes embedded configure lines, but FFmpeg remains a release-owner review item until the exact Termux package commit, patches, dependency source set, and build logs are tied to the shipped binaries.

When a native/copyleft artifact or payload changes intentionally, refresh the lockfile and markdown packet together:

```powershell
python tools\native_compliance_inventory.py --mode write-lock --lockfile docs\legal\native-compliance.lock.json
python tools\native_compliance_inventory.py --output docs\legal\native-compliance.md
```

Review the artifact hashes, payload facts, and FFmpeg notes in the same change as the dependency update.

When FFmpeg payload facts change, refresh `docs/legal/ffmpeg-source-correspondence.md` in the same change and keep the unresolved-owner-action section accurate.

## Gradle dependency verification

Gradle checksum metadata is committed at `gradle/verification-metadata.xml`. Regenerate it only during a dependency-resolution maintenance pass, not by hand.

The Gradle wrapper itself is pinned separately in `gradle/wrapper/gradle-wrapper.properties` with the official SHA-256 for `gradle-8.12-bin.zip`. PR/main verification runs:

```bash
python3 tools/gradle_wrapper_check.py --properties gradle/wrapper/gradle-wrapper.properties
python3 tools/provider_credential_release_check.py --app-gradle app/build.gradle.kts --release-workflow .github/workflows/release.yml --local-properties local.properties
python3 tools/provider_credential_storage_check.py --policy docs/security/provider-credential-storage.json --repo-root .
python3 tools/cleartext_release_check.py --repo-root .
python3 tools/store_metadata_preflight.py --repo-root .
python3 tools/privacy_policy_link_check.py --policy docs/privacy/privacy-policy-link.json --repo-root .
python3 tools/privacy_data_safety_check.py --policy docs/privacy/data-safety.json --repo-root .
python3 tools/community_guidelines_consent_check.py --repo-root .
python3 tools/play_app_content_packet_check.py --policy docs/distribution/play-app-content.json --repo-root .
```

The check fails if the wrapper distribution URL, SHA-256, URL validation, storage roots, or timeout drifts. When upgrading Gradle, update `distributionUrl`, `distributionSha256Sum`, `tools/gradle_wrapper_check.py`, and the focused wrapper tests in the same change after verifying the official Gradle checksum.

Use Android Studio's bundled JBR:

```powershell
$env:JAVA_HOME = "C:/Program Files/Android/Android Studio/jbr"
.\gradlew.bat --write-verification-metadata sha256 :app:dependencies --stacktrace --no-daemon
```

Then review and commit the resulting diff. Future dependency changes should update `gradle/verification-metadata.xml` in the same commit as the version/catalog change.

Do not use `--dependency-verification=off` in CI or release workflows. If checksum verification fails, investigate dependency drift instead of suppressing it.

Clean runners can resolve additional metadata files that a local cache does not surface. If CI fails on missing module/POM checksums, refresh metadata with the same command above plus `--refresh-dependencies`, then review the generated XML diff before committing it.

## Provider credential release guard

Provider API keys and client IDs are optional user-controlled settings. Public release builds must not bundle local Pexels, Pixabay, Freesound, SoundCloud, or Stability values into `BuildConfig`.

PR/main verification checks the Gradle wiring and release workflow wiring. Release CI runs the same guard after writing its temporary `local.properties`, which must keep these provider values blank:

```bash
python3 tools/provider_credential_release_check.py --app-gradle app/build.gradle.kts --release-workflow .github/workflows/release.yml --local-properties local.properties
```

For local development, a nonblank ignored `local.properties` provider key fails by default. Use `--allow-nonblank-local-provider-keys` only for an explicitly internal build review; the command still returns a warning status and reports key names without printing values.

After release APK packaging, the release workflow scans the signed APK for any
nonblank provider values from the temporary release `local.properties`:

```bash
python3 tools/provider_credential_apk_scan.py --local-properties local.properties --apk "$RELEASE_DIR/Aura-vX.Y.Z-versionCode-N-universal-release.apk"
```

Public release runs should report zero checked provider values because the
workflow writes blank defaults. Internal local runs with explicit provider
values must keep the APK scan in the review packet, and the scanner reports
only property names and APK entries if a value is found.

## Provider credential storage policy

User-entered Wallhaven, Pexels, Pixabay, Freesound, and Stability values are stored in app-private Jetpack DataStore and the DataStore file is excluded from cloud backup and device transfer. SoundCloud remains a blank-by-default public client ID in `BuildConfig` only. The reviewed decision is not to migrate the current optional provider keys to Android Keystore storage yet; `docs/security/provider-credential-storage.md` records that this is not strong at-rest protection, and Settings lets users clear each stored key with an explicit Clear action or by saving a blank value.

PR/main verification and the release workflow run:

```bash
python3 tools/provider_credential_storage_check.py --policy docs/security/provider-credential-storage.json --repo-root .
```

The check fails when a credential row is missing from the policy or Markdown, a DataStore-backed key lacks a Settings label or explicit Clear action, the preferences DataStore file is no longer excluded from backup and device transfer, a `BuildConfig` provider default is no longer blank, or the privacy/support diagnostics disclosures drift from the reviewed storage decision. It also treats Stability as the paid-sensitive sentinel credential and fails if the Stability row stops using DataStore, loses its blank `STABILITY_AI_KEY` release default, loses `stability.ai.key` redaction coverage, or no longer documents explicit Clear control.

## Cleartext release guard

Public releases are HTTPS-only for reviewed provider API surfaces. ccMixter no
longer has an HTTP retry path or a `network_security_config.xml` domain
exception; TLS failures fail closed and are reported through source metrics.

PR/main verification and the release workflow run:

```bash
python3 tools/cleartext_release_check.py --repo-root .
```

The check fails on `cleartextTrafficPermitted="true"`, explicit manifest
`usesCleartextTraffic="true"`, provider-network `http://` URL literals, or
OkHttp `.scheme("http")` builders under the reviewed provider source roots.

## SBOM scope

SBOM generation is deferred until after the N-1 toolchain upgrade because the Android dependency graph is already scheduled for a large AGP/Gradle/Kotlin/KSP migration. When N-1 lands, add a CycloneDX or SPDX SBOM lane that covers:

- Gradle plugins and version-catalog dependencies.
- Runtime APK dependencies for the release variant.
- Native/FFmpeg/youtubedl-android artifacts.
- The release APK digest and signing certificate fingerprint.
