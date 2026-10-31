# SBOM readiness

This packet keeps Aura's SBOM decision explicit until the N-1 toolchain
upgrade can absorb a CycloneDX or SPDX generation lane without broad dependency
or Gradle-plugin churn. The machine-readable contract is
[`sbom-readiness.json`](sbom-readiness.json).

## Current decision

| Field | Value |
| --- | --- |
| Package | `com.freevibe` |
| Status | `deferredUntilN1ToolchainUpgrade` |
| Current release SBOM artifact | Not generated |
| Current provenance surface | Signed APK/AAB, checksums, generated notices, raw notice inputs, native compliance packet, native alignment packet, and local build receipt |

The deferral is a release-owner decision, not an absence of supply-chain
evidence. Aura already gates release dependency notices, curated high-risk
license rows, native/copyleft payload facts, Gradle checksum metadata, release
bundle completeness, and checksum validation before publication.

## Current release evidence

Current releases must keep these evidence files and controls in place:

- `gradle/verification-metadata.xml`
- `docs/legal/dependency-notices.lock.json`
- `docs/legal/dependency-notice-overrides.json`
- `docs/legal/dependency-license-policy.json`
- `docs/legal/native-compliance.lock.json`
- `Aura-vX.Y.Z-versionCode-N-universal-release.apk`
- `Aura-vX.Y.Z-versionCode-N-play-release.aab`
- `THIRD-PARTY-NOTICES.md`
- `GOOGLE-OSS-RAW-INPUTS.zip`
- `NATIVE-COMPLIANCE.md`
- `NATIVE-ALIGNMENT.json`
- `SHA256SUMS.txt`
- `RELEASE_NOTES.md`
- `tools/release_artifact_bundle_check.py`

These controls do not claim to be a complete SBOM. They are the current
release evidence floor until the dedicated SBOM lane lands.

## Future SBOM lane

When N-1 lands, the SBOM implementation should generate and publish at least
one machine-readable release artifact. The reviewed candidate names are:

- `SBOM.cyclonedx.json`
- `SBOM.cyclonedx.xml`
- `SBOM.spdx.json`

The SBOM scope must cover:

- Gradle plugins and version-catalog dependencies.
- Release runtime dependency graph.
- Native and youtubedl-android payloads.
- Release APK/AAB digests and signing certificate fingerprints.

The local release lane should include the chosen SBOM file in `SHA256SUMS.txt`,
tagged GitHub Release assets, and release notes. If provenance evidence is added
for the chosen format, the release notes should also include the verification
command.

## Release gate

Local release checks must run:

- `tools/sbom_readiness_check.py`
- `tools/release_artifact_bundle_check.py`
- `tools/dependency_notice_lock.py --mode check`
- `tools/native_compliance_inventory.py --mode check-lock`
- `tools/dependency_overlay_check.py --overlay`
- `tools/dependency_license_policy.py --policy`

The readiness check fails if this packet loses its source-backed decision,
current evidence paths, current release artifact list, future SBOM artifact
names, future scope, or local release wiring.

## Sources

- CycloneDX Gradle plugin: https://github.com/CycloneDX/cyclonedx-gradle-plugin
- CycloneDX tool center: https://cyclonedx.org/tool-center/
- SPDX overview: https://spdx.dev/about/overview/
