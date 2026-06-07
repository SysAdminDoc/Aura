# Release metadata consistency

This packet keeps Aura's release-facing metadata aligned across Fastlane,
README, release docs, privacy packets, Play App content, alternative-store
disclosures, and GitHub Release artifacts. The machine-readable contract is
[`release-metadata-consistency.json`](release-metadata-consistency.json).

## Current package

| Field | Value |
| --- | --- |
| Package | `com.freevibe` |
| Version name | `6.31.1` |
| Version code | `112` |
| Fastlane metadata root | `fastlane/metadata/android/en-US` |
| Privacy policy URL | `https://github.com/SysAdminDoc/Aura/blob/main/docs/privacy/privacy-policy.md` |

## Metadata surfaces

- `app/build.gradle.kts` is the source for package, version name, and version
  code.
- Fastlane `title.txt`, `short_description.txt`, `full_description.txt`, and
  `changelogs/112.txt` are the store text surface.
- `README.md` must keep links to the privacy policy, release signing, channel
  strategy, alternative-store disclosures, release metadata consistency, SBOM
  readiness, store asset planning, developer verification, and supply-chain
  docs.
- `docs/privacy/privacy-policy-link.json`,
  `docs/distribution/play-app-content.json`, and
  `docs/distribution/alt-store-metadata.json` must keep the same package and
  privacy-policy URL where those fields apply.

## Release preflights

Verify and release workflows must keep these release-facing gates before
Android build or release publication work:

- `tools/store_metadata_preflight.py`
- `tools/store_asset_pipeline_check.py`
- `tools/privacy_policy_link_check.py`
- `tools/privacy_data_safety_check.py`
- `tools/community_guidelines_consent_check.py`
- `tools/play_app_content_packet_check.py`
- `tools/alt_store_metadata_check.py`
- `tools/sbom_readiness_check.py`
- `tools/release_artifact_bundle_check.py`

## Release artifacts

Release dry runs and tagged releases must document these expected artifacts:

- `Aura-vX.Y.Z-versionCode-N-universal-release.apk`
- `THIRD-PARTY-NOTICES.md`
- `GOOGLE-OSS-RAW-INPUTS.zip`
- `NATIVE-COMPLIANCE.md`
- `SHA256SUMS.txt`
- `RELEASE_NOTES.md`
- `apksigner.txt`
- `aapt-badging.txt`

## Release checklist

Before any public release:

1. Run `py -3 tools\release_metadata_consistency_check.py --policy docs\distribution\release-metadata-consistency.json --repo-root .`.
2. Run the store metadata, store asset pipeline, privacy, Data safety, community guidelines, Play App content, alternative-store, and SBOM readiness gates listed above.
3. Confirm the current `versionCode` changelog mentions the current `versionName`.
4. Confirm the GitHub Release or dry-run artifact contains the expected release files.

## Sources

- Google Play app creation and store listing fields: https://support.google.com/googleplay/android-developer/answer/9859152
- Google Play store listing best practices: https://support.google.com/googleplay/android-developer/answer/13393723
- fastlane supply metadata docs: https://docs.fastlane.tools/actions/supply/
- GitHub Actions workflow artifacts: https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts
- GitHub Releases: https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases
- F-Droid descriptions, graphics, and screenshots: https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/
