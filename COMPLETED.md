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

## 2026-06-05

- Completed the provider/content-source compliance slice: central
  `ProviderDisclosure` model, Licenses screen integration, legal provider policy
  matrix docs, and unit coverage for complete `ContentSource` disclosure rows.
- Expanded visible Licenses screen rows for missing runtime/native dependencies
  as a stopgap before generated OSS notices.
