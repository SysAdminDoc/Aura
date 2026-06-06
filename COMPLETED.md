# Completed Work

Append-only completion ledger for autonomous Aura passes. `ROADMAP.md` remains
the open-work source of truth; this file records shipped or closed items so the
next pass can resume quickly.

## 2026-06-06

- Completed Cycle 18 research for generated release-runtime OSS notices, license
  drift gates, SBOM/release artifacts, and native/copyleft packet planning.
- Completed Cycle 19 current-toolchain notice tooling research and
  AboutLibraries version-gating analysis.
- Completed Cycle 20 isolated compatibility spikes for Google OSS notices and
  AboutLibraries 14.2.1 under local `work/` clones.
- Completed Cycle 21 plugin-only Google OSS notice generation and
  `THIRD-PARTY-NOTICES.md` converter planning.
- Completed Cycle 22 real-repo plugin-only Google OSS notice implementation:
  Gradle plugin wiring, verification metadata updates, markdown converter,
  release workflow packaging, supply-chain docs, and focused notice generation
  verification.
- Restored real-repo `ProviderDisclosureTest` execution after adding the POM
  checksum metadata required by the debug OSS notice task.
- Completed Cycle 23 native/copyleft payload inspection: added
  `tools/native_compliance_inventory.py`, committed
  `docs/legal/native-compliance.md`, wired release `NATIVE-COMPLIANCE.md`
  generation/checksums/uploads, and documented FFmpeg source-correspondence
  review requirements.
- Completed Cycle 24 dependency notice drift gating: added
  `tools/dependency_notice_lock.py`, committed
  `docs/legal/dependency-notices.lock.json`, and wired PR/main verification plus
  release builds to fail on generated Google OSS notice drift.
- Completed Cycle 25 native compliance freshness gating: extended
  `tools/native_compliance_inventory.py`, committed
  `docs/legal/native-compliance.lock.json`, and wired PR/main verification plus
  release builds to fail on native/copyleft artifact or extracted payload drift.
- Completed Cycle 26 curated high-risk dependency overlay: added
  `docs/legal/dependency-notice-overrides.json`,
  `tools/dependency_overlay_check.py`, and wired PR/main verification plus
  release builds to fail on missing, stale, or orphaned high-risk dependency and
  native-payload review metadata.
- Completed Cycle 27 release compliance artifact dry-run validation: added
  `tools/release_artifact_bundle_check.py`, wired release workflow bundle
  validation before artifact upload/publication, and documented the manual
  `workflow_dispatch` dry-run procedure.
- Completed Cycle 28 raw release notice input preservation: added
  `tools/google_oss_raw_archive.py`, wired `GOOGLE-OSS-RAW-INPUTS.zip` into
  release checksums, notes, workflow artifacts, tagged release assets, and the
  bundle validator.
- Completed Cycle 29 user-facing dependency notice access: added generated
  release notice cards to `LicensesScreen.kt`, updated Settings copy, and added
  focused coverage for the release artifact link data.
- Completed Cycle 30 FFmpeg source-correspondence evidence: added
  `docs/legal/ffmpeg-source-correspondence.md`, extracted embedded FFmpeg
  7.1.1 configure lines and license-mode facts into the native compliance
  generator/lock, regenerated the native packet, and updated release review
  docs for the remaining Termux source/build-log owner action.
- Completed Cycle 31 release dependency license policy gate: added
  `docs/legal/dependency-license-policy.json`,
  `tools/dependency_license_policy.py`, and wired PR/main verification plus
  release builds to fail unknown, disallowed, or unreviewed curated dependency
  and native-payload license IDs.
- Completed Cycle 32 raw Google OSS archive retention policy: documented
  `GOOGLE-OSS-RAW-INPUTS.zip` as a permanent tagged public release asset,
  clarified dry-run/release signing docs, and kept release bundle validation
  enforcing the archive in files, checksums, and notes.
- Completed Cycle 33 custom in-app generated dependency notice viewer: added
  a parser for generated Google OSS raw resources, listed generated dependency
  notices in Settings > Open source licenses, and opened full generated notice
  text without adding the stock Play services OSS licenses runtime dependency.
- Completed Cycle 34 generated notice search and high-risk alignment: added
  filtering for generated notice names/license labels and review markers for
  generated rows that map to curated Firebase, Play services, ML Kit,
  NewPipeExtractor, youtubedl-android, ProfileInstaller, and ZXing surfaces.
- Completed Cycle 35 generated notice metadata parity guard: added
  `tools/dependency_notice_lock.py --mode check-metadata`, wired PR/main and
  release workflows to run it after the full generated notice lock check, and
  documented the raw metadata parity command in the supply-chain runbook.
- Completed Cycle 36 runtime provider kill-switch behavior matrix: added
  code-backed `ProviderRuntimeControl` rows for every `ContentSource`, extended
  provider disclosure tests to require explicit disabled behavior/follow-ups, and
  documented current missing/partial controls in
  `docs/legal/provider-runtime-controls.md`.

## 2026-06-05

- Completed the provider/content-source compliance slice: central
  `ProviderDisclosure` model, Licenses screen integration, legal provider policy
  matrix docs, and unit coverage for complete `ContentSource` disclosure rows.
- Expanded visible Licenses screen rows for missing runtime/native dependencies
  as a stopgap before generated OSS notices.
