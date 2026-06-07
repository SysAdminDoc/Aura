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
- Completed Cycle 37 YouTube provider legal-mode switch: added a default-on
  YouTube provider preference and Settings switch, blocked YouTube search,
  import, top hits, similar sounds, playback/download/video resolution, and video
  discovery when disabled, added bundled sound fallbacks, and recorded disabled
  source diagnostics separately from outages.
- Completed Cycle 38 Reddit provider source switch: added a default-on Reddit
  provider preference and Settings switch, hid Reddit wallpaper browsing when
  disabled, skipped daily picks/background rotations/repository calls/video
  discovery, and recorded disabled source diagnostics separately from outages.
- Completed Cycle 39 Pexels and Pixabay source switches: added default-on
  provider preferences and Settings switches, hid disabled provider tabs, skipped
  Discover/search/style-biased/video API calls before bundled keys are read,
  removed disabled Pixabay from rotation pickers, and queued Pixabay
  TTL/rate-limit enforcement for the later policy slices.
- Completed Cycle 40 Community source switch: added a default-on Community
  provider preference and Settings switch, skipped startup identity warm-up when
  disabled, hid community tabs/uploads/votes/creator profile entry points,
  blocked sound/wallpaper/creator repository Firebase calls, and recorded
  disabled diagnostics separately from Firebase outages.
- Completed Cycle 41 Bing Daily source switch: added a default-on Bing Daily
  provider preference and Settings switch, skipped Bing daily-image API calls
  before cache fallback or Retrofit use, hid Bing from rotation pickers when
  disabled, and recorded disabled diagnostics separately from outages.
- Completed Cycle 42 Wallhaven source switch: added a default-on Wallhaven
  provider preference and Settings switch, hid Wallhaven browsing plus
  color/random/similar actions when disabled, removed disabled Wallhaven from
  rotation pickers, skipped Wallhaven API calls before key reads/cache fallback,
  and recorded disabled diagnostics separately from outages.
- Completed Cycle 43 Pixabay photo request-cache and backoff: added a
  24-hour Pixabay metadata TTL, served fresh cached Pixabay photo results before
  API calls, parsed `Retry-After`/`X-RateLimit-Reset` from 429 responses into an
  in-session backoff, and queued Pixabay video metadata caching for the next
  policy slice.
- Completed Cycle 44 Pixabay video request-cache and backoff: added persistent
  app-private 24-hour video metadata caching, restored cached stream URLs with
  cached rows, persisted 429 backoff from `Retry-After`/`X-RateLimit-Reset`, and
  marked Pixabay runtime controls covered for photo and video metadata.
- Completed Cycle 45 generated-content source switch: added a default-on
  generated-wallpapers preference and Settings switch, hid the Wallpapers
  Generate chip when disabled, blocked the AI wallpaper ViewModel before prompt
  or key validation, and marked AI-generated runtime controls covered while
  keeping saved local generated wallpapers visible.
- Completed Cycle 46 saved-source availability states: added Room-backed source
  availability metadata for favorites and download history, migrated the
  database to v15, preserved provider source names in new download records,
  surfaced "Source unavailable" badges in Favorites/Downloads/detail screens,
  and kept local saved wallpaper paths usable when upstream content is removed.
- Completed Cycle 47 Pexels enhancement guardrails: Discover and video-wallpaper
  discovery now drop Pexels-only batches unless non-Pexels base inventory is
  present, disabled-Pexels Discover still returns Wallhaven/Pixabay fallback
  inventory, and Pexels photo rows keep creator/source-page attribution fields.
- Completed Cycle 48 provider removal failure reconciliation: explicit
  404/410/gone/removed/deleted provider failures now mark saved wallpaper/sound
  favorites unavailable during apply/download paths, and failed re-downloads
  mark matching download-history rows unavailable.
- Completed Cycle 49 sound license capability gates: added normalized sound
  action policy, favorite license persistence with Room v16 and export/import
  preservation, confirmation gates for restricted apply/download actions,
  disabled states for link-only or missing-license content, and provenance-rich
  sound share text.
- Completed Cycle 50 community upload rights metadata: sound and wallpaper
  uploads now require selected CC0/CC BY/CC BY-NC metadata plus rights
  attestation, store uploader UID/attestation/source fields, validate them in
  RTDB rules, feed selected community sound licenses into action gates, and show
  wallpaper license metadata on detail surfaces.
- Completed Cycle 51 community report queue intake: added private report
  payload validation, Firebase report and resolution repository paths, RTDB
  rules for authenticated report creation and admin-only review/resolution,
  sound/wallpaper detail report actions, ViewModel report submission, and
  support docs for the reporting workflow.
- Completed Cycle 52 admin report review: added an admin-only open report
  screen, Settings navigation, RTDB report status indexing, hide/dismiss/restore
  actions, moderation hide/unhide wiring, report resolution feedback, and
  focused ViewModel coverage for moderation and non-admin gating.
- Completed Cycle 53 Firebase App Check client rollout: added debug and release
  App Check provider installers, installed App Check before Firebase-backed
  community startup work, documented debug token and Play Integrity rollout
  steps, refreshed Gradle dependency verification metadata, and updated the
  generated dependency notice lock for App Check artifacts.
- Completed Cycle 54 community quota/rate-limit design: added typed quota
  policy rows for reports, uploads, votes, follows, and profile edits, reserved
  admin-only RTDB quota and dedupe ledgers, documented the App-Checked callable
  migration plan, and linked the policy from community support/admin runbooks.
- Completed Cycle 55 community upload deletion handles: new sound and wallpaper
  uploads now store canonical Storage paths and private owner-index rows,
  repositories expose owner delete methods for new rows, RTDB rules protect the
  owner index, and the deletion runbook documents remaining UI/admin/backfill
  work.
- Completed Cycle 56 visible community owner delete actions: repositories now
  expose owner-and-`storagePath` availability probes, sound and wallpaper
  ViewModels expose delete entry points, and detail surfaces show confirmed
  owner-only delete actions for new community uploads.
- Completed Cycle 57 Storage rules and emulator harness: added tracked
  `firebase.json`, `storage.rules`, local npm Firebase rules dependencies, and
  Storage emulator tests for owner-only community upload writes/deletes,
  MIME/size ceilings, public reads, and closed unmanaged paths.
- Completed Cycle 58 Realtime Database rules harness: added Database emulator
  config, `test:database-rules` and `test:firebase-rules` scripts, RTDB
  emulator tests for upload metadata, owner indexes, reports, report
  resolutions, quota/dedupe ledgers, and collection shares, aligned tracked
  collection-share rules to `shared_collections`, added `createdByUid` to
  published shares, and made `database.rules.json` emulator/deploy-compatible.
- Completed Cycle 59 Firebase rules CI gate: added a path-gated
  `firebase-rules` job to `.github/workflows/verify.yml` that installs Java 17,
  Node 20, pinned npm dependencies, and runs `npm run test:firebase-rules` when
  Firebase rules, config, tests, runbooks, or the workflow change.
- Completed Cycle 60 rights takedown receipts: hiding a `RIGHTS` report for a
  community sound or wallpaper now writes a private admin receipt with the
  upload metadata path, Storage deletion handle, uploader UID, resolver UID,
  timestamp, and note when the current upload row proves the deletion handle;
  RTDB rules and emulator tests reject non-admin, non-rights, stale-handle, and
  mismatched-path receipt writes.
- Completed Cycle 61 admin upload delete actions: qualifying rights reports now
  expose a confirmed `Delete upload` admin action that records a `DELETE`
  receipt, hides the content ID, deletes the Storage object, removes public
  upload metadata plus the owner index, and updates the receipt to `SUCCEEDED`
  or `FAILED` for retry evidence.
- Completed Cycle 62 closed report review filters: the admin report queue now
  switches between Open, Hidden, Dismissed, and Restored status feeds, including
  from the empty state, so closed moderation outcomes remain reviewable.
- Completed Cycle 63 callable quota enforcement contract: quota policies now
  name each App-Checked callable, payload schema, final write path set, protected
  ledger path set, and limited-use App Check token decision for reports,
  uploads, votes, follows, and profile edits; the backend migration runbook
  records request envelope, transaction sequence, error mapping, Android
  migration order, and verification requirements.
- Completed Cycle 64 community backend deploy/rollback evidence: added a
  deterministic Firebase backend manifest tool, committed the current backend
  manifest, wired the manifest check into the Firebase rules CI job, and added
  a deploy/rollback runbook covering preflight, production-project dry run,
  deployment evidence, rollback, App Check enforcement rollback separation, and
  release checklist entries.
- Completed Cycle 65 community Storage lifecycle/orphan cleanup policy: added an
  offline orphan-report tool, unit coverage for orphan and metadata mismatch
  classification, CI execution for backend lifecycle/tool changes, and a policy
  runbook that blocks automatic deletes on committed upload prefixes while
  requiring two matching orphan reports before manual cleanup.
- Completed Cycle 66 legacy upload backfill planning: added a dry-run RTDB
  backfill planner for legacy community rows missing canonical `storagePath` and
  owner indexes, unit coverage for Firebase Storage URL parsing and unsafe-row
  blocking, CI path coverage for all backend tool tests, and a backfill runbook
  with candidate requirements, apply gate, and evidence requirements.
- Completed Cycle 67 community deletion tombstones: owner and admin upload
  deletes now remove public metadata plus owner indexes while writing private
  admin-only tombstones with owner-scoped Storage handles, RTDB rules/emulator
  coverage, and a deletion retention policy for removed uploads.
- Completed Cycle 68 community block-user policy: reserved private
  `/community_user_blocks` rows and admin-only `/community_blocked_by` reverse
  indexes, added typed block policy helpers, emulator coverage, callable quota
  metadata, and a block-user runbook.
- Completed Cycle 69 public takedown copy: community sound and wallpaper upload
  dialogs now disclose public listing behavior and confirmed rights-takedown
  outcomes, the report dialog explains the private rights-takedown route, owner
  delete confirmations describe public metadata/index removal plus retained
  private records, and reusable policy copy has focused unit coverage.
- Completed Cycle 70 community block-user filtering: added Android block-list
  repository reads, block/unblock update methods, no-sign-in filtering reads for
  public browsing, community sound/wallpaper feed filtering, creator profile
  list filtering, and focused unit coverage for unblock paths and blocked
  creator/uploader matching.
- Completed Cycle 71 visible block creator actions: community sound and
  wallpaper detail surfaces now show confirmed block actions when uploader
  identity is available, block through `CommunityBlockRepository`, and remove
  matching uploader rows from the active UI state with focused ViewModel and
  policy-copy coverage.
- Completed Cycle 72 blocked creators review: Settings now lists blocked
  community creators with reason/timestamp metadata and supports per-row unblock
  actions through `CommunityBlockRepository`, with focused Settings ViewModel
  coverage.
- Completed Cycle 73 report/profile block actions: community reports now carry
  optional uploader UID metadata, report rules validate that field, admin report
  cards can block reported community uploaders, and creator profile rows expose
  confirmed block actions with immediate dashboard filtering.

## 2026-06-05

- Completed the provider/content-source compliance slice: central
  `ProviderDisclosure` model, Licenses screen integration, legal provider policy
  matrix docs, and unit coverage for complete `ContentSource` disclosure rows.
- Expanded visible Licenses screen rows for missing runtime/native dependencies
  as a stopgap before generated OSS notices.
