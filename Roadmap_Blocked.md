# Aura — Blocked Roadmap Items

> Items moved here from ROADMAP.md because they are blocked on external dependencies:
> owner/Firebase Console actions, physical device testing, production database access,
> or the N-1 toolchain upgrade gate.
>
> Move items back to ROADMAP.md when the blocker is resolved.

---

## Blocker: External Authorization and Legal Review

- **P1 — Move Reddit's core feeds to registered OAuth and deletion reconciliation**
  - Current state: Reddit is intentionally Aura's default wallpaper and video
    source, but both feeds use anonymous `.rss` endpoints. The app and its local
    configuration contain no Reddit OAuth client ID or approved API registration.
  - Blocker: Reddit must approve and issue the installed-app client identity.
    Shipping an invented or maintainer-personal identity would not satisfy the
    API terms or provide a durable release transport.
  - Resume when the release owner supplies the approved client ID and registered
    user-agent identity. Implement anonymous installed-client tokens, safe
    refresh, 401 retry, 429 backoff, paging, deletion reconciliation, diagnostics,
    and fixtures while keeping Reddit first in wallpaper and video discovery.

- **P0 — Gate YouTube extraction and offline actions by authorization and release channel**
  - Current state: YouTube-first discovery is intentional. NewPipe and yt-dlp
    currently resolve, download, convert, and apply streams in GitHub-channel
    builds, while Play builds exclude YouTube.
  - Blocker: YouTube's terms require service permission or authorization from
    YouTube and the applicable rights holder for download behavior. The
    repository has no written authorization record that an automated change can
    truthfully substitute for.
  - Resume when the release owner provides a reviewed authorization record that
    names permitted channels and actions. Then enforce it in provider capability,
    repository, download, conversion, wallpaper, and tone paths while retaining
    official playback and attribution for unapproved actions.

- **P0 — Resolve GPL obligations for the combined APK**
  - Current state: the MIT repository packages NewPipeExtractor under
    GPL-3.0-or-later and an FFmpeg payload configured with `--enable-gpl` and
    `--enable-version3`. Runtime and legal locks also disagree on the pinned
    NewPipe version.
  - Blocker: selecting compatible distribution terms or removing the GPL
    components is a legal decision. Automated edits cannot decide whether the
    APK is a combined work or what corresponding-source and installation
    obligations apply.
  - Resume after counsel or a documented owner legal review selects the path.
    Apply that decision consistently to the app license, source archive, build
    scripts, notices, dependency locks, native packet, and release artifacts.

---

## Blocker: N-1 Toolchain Upgrade (AGP 9 / Gradle 9 / Kotlin 2.3)

N-1 itself is the largest single gate. Until it lands, these items cannot proceed:

- **N-1 — Toolchain upgrade triad** (AGP 9 + Gradle 9 + Kotlin 2.3 + Hilt 2.59)
  - Scope: AGP 8.9.3 -> 9.2.x, Gradle 8.12 -> 9.5+, Kotlin 2.1.0 -> 2.3.20, KSP1 -> KSP2, Hilt 2.53.1 -> 2.59.x, Navigation 3.x, etc.
  - Rescoped 2026-08-20: compileSdk 36 landed on AGP 8.9.3 at targetSdk 35, so this no
    longer gates anything that only needed a compileSdk of 36. The Compose BOM moves
    independently and is out of this scope. OkHttp 5.4, Coil 3.5, and Media3 1.10+ came
    off this blocker and were taken in the dependency refresh.
  - Compose BOM ceiling 2026-08-20: 2026.06.01 (Compose 1.11.4) is the highest this
    stack accepts. 2026.07+ ships Compose 1.12.x, whose AAR metadata demands compileSdk
    37 and AGP 9.1, so the Compose line is capped here until this item lands after all.
  - Risk: Memory-heavy Gradle runs on this workstation. R8 keep-rule regressions, KSP2 cache issues.
  - Gates: N-3/N-4/NX-2/NX-7 and most Next-tier items.
  - Scope notes 2026-08-20: AGP 9.x ships built-in Kotlin — the standalone `org.jetbrains.kotlin.android` plugin must be removed or the build fails; Gradle 9.1+ is the floor; use Hilt **2.59.2**, not 2.59 (2.59 shipped a broken Gradle plugin, dagger#5099); Kotlin stable is now 2.4.x with the K1 frontend removed; AGP 9.3 adds an `analyzeReleaseR8Config` keep-rule analyzer useful for the queued R8 item.
  - Audit evidence 2026-09-25: the runtime graph now resolves `kotlin-stdlib:2.4.0` through Coil 3.5.0 while the Kotlin plugins remain at 2.1.0, and `app/build.gradle.kts:174-181` suppresses metadata-version validation. Serial Full and FOSS packaging emitted repeated R8 “Unexpected error during rewriting of Kotlin metadata” warnings; both attempts were stopped when free memory reached about 1.7 GB, so no current artifact was proven. N-1 acceptance must remove the metadata bypass, align compiler/library/R8 metadata support, and package both flavors locally with the documented bounded-worker settings and no metadata-rewrite warning storm.

- **P0 — API 37 toolchain and target-SDK release gate** (Cycle 10)
  - Needs compileSdk 37, which needs an AGP beyond the 8.9.3 the project now pins.
    Blocked until N-1 completes.
  - Note 2026-08-20: budget for the targetSdk 36 behavior trio on the way — predictive back on by default (`onBackPressed` no longer called), edge-to-edge opt-out removed, and orientation/resize flags ignored on sw>=600dp (opt-out dies entirely at targetSdk 37).

- **P2 — Direct Android 17 API cleanup for shipped bridges** (Cycle 10)
  - EyeDropper and Photo Picker 9:16 shipped through reflection; direct API needs compileSdk 37.

- **P2 — Video wallpaper playlists and per-video behavior profiles** (Cycle 1)
  - Depends on NX-1 GL/AGSL/ExoPlayer engine migration, which itself depends on N-1.

- **P3 — Missing integration test execution in CI** (Next audit findings)
  - Instrumented tests need CI infrastructure that N-1 build verification would establish.

- **P1 — Room 2.8.x persistence refresh**
  - Blocker: Room 2.8.4 KSP failed locally with `AbstractMethodError` in Room's kotlinx-serialization bundle serializer under Kotlin 2.1.0 / KSP 2.1.0-1.0.29.
  - Current state: Aura is on Room 2.7.2 to satisfy WorkManager 2.11.2 without taking the larger Kotlin/KSP/toolchain upgrade.
  - Resume when N-1 upgrades Kotlin/KSP. Acceptance remains: all schema versions migrate cleanly, KSP succeeds with Kotlin codegen, and favorites/downloads/collections behavior is unchanged.
  - Note 2026-08-20: Room 3.0.1 is now the stable line (KSP-only, coroutine-first, package renames) — the 2.8.x target is superseded, and the move is a real migration to plan inside N-1, not a version bump.

### N-1-gated Next items (NX)

- **NX-1** — GL/AGSL live wallpaper engine migration (Media3 ExoPlayer + AGSL pipeline)
- **NX-2** — Lockscreen depth (subject-aware clock-tuck + lockscreen Glance widgets)
- **NX-4** — SelectedContentHolder removal (nav-graph-scoped ViewModel + Navigation 3)
- **NX-5** — Plugin/source ABI (Muzei-compatible "Aura Sources")
- **NX-7** — Favorites sync via Firestore + Google sign-in
- **NX-9** — Media3 1.10 Material3 playback composables
- **NX-13** — Predictive-back wiring (remaining 14 screens need Navigation 3)

---

## Blocker: Firebase Console / Owner Actions

These items have code shipped but require Firebase Console access, production RTDB access, or owner-only actions to complete:

- **P1 — Turn on Firebase Authentication so community sign-in and admin moderation work**
  - Why: Firebase Auth was never set up on `aura-1b494` (the Identity Toolkit config answers `CONFIGURATION_NOT_FOUND` and the project has no users), so `ensureSignedIn()` fails on every install and every signed-in community action fails before it reaches the backend. Since the private database rules went live on 2026-09-30, a global hide from the app also needs an account with the `admin` claim.
  - Needs: Matt signed in to the Firebase console: Authentication, Get started, then enable Anonymous. The API route (`identityPlatform:initializeAuth`) answers `BILLING_NOT_ENABLED` on Spark, while the console's own Get started doesn't need billing.
  - Then: open Aura on the phone Matt moderates from, find its new anonymous UID in the Authentication users list, and grant `admin: true` (`docs/firebase-admin-claims.md`, or Identity Toolkit `accounts:update` with `customAttributes` using the CLI login). Until then, hide content globally with `npx firebase database:set /moderation/<contentId> true --project aura-1b494`.
  - Acceptance: a fresh install signs in anonymously; an admin hide from the app lands in `/moderation`; a non-admin hide stays on that phone.
  - Moved from ROADMAP.md 2026-10-13: the console step needs Matt's sign-in.

- **P1 — Deploy the community functions, then finish the vote migration**
  - Why: production has never had a Cloud Function (the Cloud Functions, Cloud Build and Artifact Registry APIs were never enabled), so votes aren't recorded, and reports, follows, blocks, profile edits and collection links all fail. The private database rules went live on 2026-09-30 ahead of the functions. `/vote_counts` was seeded by running the backend's own `seedLegacyVoteCounts` locally against production (33 rows, 36 votes) and the 35 legacy vote markers were backfilled, so counts and Top Voted already read the new paths. Cloud Storage for Firebase isn't enabled and the project has no bucket (`aura-1b494.firebasestorage.app` doesn't exist), so community uploads need that too.
  - Needs: Firebase Auth (item above) and the Blaze plan on `aura-1b494`. The project is on Spark, and `snafumatthew@gmail.com` (the account the Firebase CLI on this PC is signed in to since 2026-09-30) has no Cloud billing account, so the functions dry run stops at enabling Artifact Registry. At current usage Blaze stays inside its no-cost quotas, but it needs a card and has no hard cap, so set a budget alert.
  - Steps: `npx firebase deploy --only functions:community --project aura-1b494` (it also creates the Scheduler jobs for `pruneExpiredSharedCollections` and `seedLegacyVoteCounts`); create the default Storage bucket and deploy `storage.rules`; check the seeding job's "Seeded legacy vote counts" log line. Once most installs run 6.46.1 or later, export the database, run `python tools/community_vote_privacy_backfill.py --database-export <export.json> --output <update.json> --drop-legacy`, apply it with `firebase database:update / <update.json> --project aura-1b494`, then delete the `seedLegacyVoteCounts` export from `functions/src/index.ts`.
  - Acceptance: a vote from a phone raises its `/vote_counts` row; a real community sound upload finalizes; a collection link made on one phone imports on the other; the prune job shows a successful run in the Functions log.
  - Moved from ROADMAP.md 2026-10-13: needs Blaze billing with a card on Matt's account.

- **P3 (residual) — Register the reproducible FOSS lane with rbtlog**
  - Shipped in the signing-transparency work: the release certificate SHA-256 is
    published in `README.md`, the Fastlane `full_description.txt`, and
    `docs/distribution/release-signing.md`, recorded machine-readably in
    `docs/distribution/signing-certificate.json`, and held there by
    `tools/signing_certificate_check.py`, which also compares it against the real
    keystore on a machine that holds one and reports unknown rather than failing
    on one that does not.
  - Remaining: submit the reproducible FOSS recipe to
    codeberg.org/IzzyOnDroid/rbtlog so the reproducible-build badge can be
    awarded. `tools/foss_reproducibility_check.py` is the recipe.
  - Blocker: this follows the IzzyOnDroid submission decision, which is itself
    gated on the ~30 MB per-APK ceiling that the FFmpeg and Python payload still
    exceeds, and registration is an owner action against an external tracker.

- **P1 — Register Aura for Android developer verification**
  - Install guidance and the register-vs-abstain decision record are complete in
    `docs/distribution/developer-verification.md`.
  - Remaining: the release owner must complete identity verification in Android
    Developer Console, register `com.freevibe`, prove ownership with the existing
    release signing key, and confirm the package/key status before changing release
    notes from `owner-confirmation-required`.

- **N-2 (remaining)** — Firebase BoM 34 + Custom Claims admin path
  - Code + rules shipped. Remaining: deploy `database.rules.json` + grant Custom Claims to existing admins in Firebase Console.

- **P0 — Add Firebase App Check for community writes** (Cycle 1)
  - Code shipped (debug/release providers, callable handlers, Android clients, emulator tests).
  - Remaining: Firebase Console App Check registration, debug-token registration, metrics burn-in, direct-rule tightening, RTDB/Storage enforcement.

- **P1 — Source provenance panel + community report queue** (Cycle 1)
  - Code shipped (report dialogs, admin queue, callable handlers, Android clients).
  - Remaining: live callable invocation evidence, deploy evidence, direct-rule tightening.

- **P0 — Firebase rules test and deploy harness** (Cycle 9)
  - Emulator tests, CI wiring, and runbook shipped.
  - Remaining: run and archive a real production-project dry run after owner access is confirmed.

- **P0 — Community owner field normalization** (Cycle 9)
  - Owner field, indexes, Storage rules, emulator tests, delete actions shipped.
  - Remaining: legacy backfill against production RTDB export.

- **P1 — Storage rules and orphan lifecycle cleanup** (Cycle 9)
  - Storage rules, emulator tests, lifecycle policy, orphan report tool shipped.
  - Remaining: run real exported Storage/RTDB orphan reports after owner access.

- **P1 — App Check and community abuse throttling** (Cycle 9)
  - Providers, quota policies, callable handlers, Android clients shipped.
  - Remaining: Firebase Console metrics/enforcement evidence.

- **P1 — Moderation report queue and audit trail** (Cycle 9)
  - Report intake, admin queue, block system, callable handlers shipped.
  - Remaining: full callable protocol coverage, owner-approved deploy evidence.

- **P2 — Community backend operations runbook** (Cycle 9)
  - Manifest, CI gate, runbook shipped.
  - Remaining: takedown SLA packet, owner/admin deletion verification evidence, live orphan report.

- **P0 — Community upload public-data lifecycle and deletion workflow** (Cycle 4)
  - Owner indexes, delete methods, Storage rules, deletion tombstones shipped.
  - Remaining: public request copy and live backfill evidence after owner access confirmed.

- **P0 — Accountless community deletion contract** (Cycle 12)
  - Full toolchain shipped (deletion planner, lookup, review, simulator, executor, receipts, web intake, local cleanup, Auth packages).
  - Remaining: actual owner-run Auth/upload deletion and hosted URL publication.

- **P0 — Firebase deletion orchestrator and web request runbook** (Cycle 12)
  - Tooling shipped. Remaining: trusted production dry run, web request page publication.

- **P1 — Owner indexes and Storage deletion handles** (Cycle 12)
  - New upload handling shipped. Remaining: run planner against fresh production RTDB export.

- **P1 — Vote, follow, profile, and moderation deletion semantics** (Cycle 12)
  - Policy, tooling, block system, callable handlers shipped.
  - Remaining: owner-approved production execution evidence, hosted URL publication.

- **P2 — Community data receipt/export surface** (Cycle 12)
  - Identity panel, deletion code, toolchain shipped.
  - Remaining: owned upload IDs, follow/vote counts, export output, live hosted URL.

- **P0 — IP takedown/report queue** (Cycle 13)
  - Report intake, admin actions, block system, callable handlers shipped.
  - Remaining: full callable protocol coverage, owner-approved deploy evidence.

- **P1 — Community upload rights attestation** (Cycle 13)
  - License chips, rights confirmation, source URL, detail display shipped.
  - Remaining: legacy/backfill coverage and callable upload finalization.

- **P1 — Verify and rotate repository-exposed provider credentials**
  - Category: security
  - Where: current `app/google-services.json:18`; historical commits
    `2f2b53a4`, `b275e969`, `d96439fd`, `b17eb50f`,
    `ed077643`, `de3fb151`, `e958e75b`, and `2c616f12` for
    Pexels/Pixabay defaults.
  - Problem: provider keys remain recoverable from public history, and the
    current Firebase client key is necessarily shipped but its API/package/SHA
    restrictions cannot be verified from the repository.
  - Evidence: current-run redacted gitleaks history scan covered 1,105 commits
    and reported 12 hits: two Firebase client-config revisions plus ten
    Pexels/Pixabay default-key revisions. No secret value was recorded in this
    roadmap.
  - Fix: In the provider consoles, revoke/rotate every historical Pexels/Pixabay
    key and verify no usage continues. In Google Cloud/Firebase, restrict the
    Android client key to the package/signing certificate and only required
    APIs. Add redacted restriction/rotation evidence and a reviewed gitleaks
    allowlist only for the expected Firebase client config.
  - Acceptance: Old provider keys receive unauthorized responses; new keys are
    absent from Git/history and supplied only through the existing local or
    owner-controlled path; Firebase rejects another package/signing identity;
    a redacted current/history scan has no unreviewed finding.
  - Confidence: Needs-repro
  - Effort: S
  - Blocker: requires Pexels, Pixabay, Google Cloud, and Firebase owner access.

---

## Blocker: Physical Device / Emulator

These items require adb-connected device or Android 17 emulator testing:

- **P2 — Cover the pre-export half of the Room migration chain (1 → 8)**
  - The downgrade half landed 2026-08-20 and is fully covered by JVM tests: an older APK
    now opens without crashing, the previous database is copied aside first, and the user
    gets an explicit warning pointing at backup/restore instead of a silent wipe.
  - Already covered on device: `migrate8To9` and `migrateEveryExportedSchemaVersionToCurrent`
    (every exported start version 9..15 through to 16).
  - Blocker: `MIGRATION_1_2` … `MIGRATION_7_8` have no exported schema JSON — the export
    floor of 9 is deliberate policy — so testing them means hand-writing a v1 schema in SQL
    and running the chain through `MigrationTestHelper`, which is instrumentation-only.
    Writing 200 lines of hand-authored schema that cannot be run here would be guessing.
  - Resume by extending `DatabaseMigrationTest` with a `createVersion1Database()` built the
    way `createVersion8Database()` already is, then
    `helper.runMigrationsAndValidate(TEST_DB, 16, true, *DatabaseMigrations.ALL_MIGRATIONS)`.

- **P2 — Measure the collection QR dialog's frame times on a device**
  - The code half landed 2026-10-13: the share dialog builds its QR on `Dispatchers.Default` behind a
    progress state, the bitmap is filled with one `setPixels` call, and imports are sampled down before
    ZXing reads one byte of luma a pixel. `CollectionQrImportTest` (Robolectric NATIVE) decodes an exported
    QR inside a 4096 x 2929 photo through the real `BitmapFactory` path.
  - Blocker: the acceptance was no dropped frames while the dialog opens, which only a device trace shows.
  - Resume by resetting `adb shell dumpsys gfxinfo com.freevibe reset`, opening and closing a collection's
    share dialog ten times, then reading `dumpsys gfxinfo com.freevibe` (or JankStats in a debug build) and
    recording the janky-frame count here.

- **P2 — Record the GridScrollBenchmark frame timings the stability work was meant to move**
  - The stability half landed 2026-08-20: every model rendered in a Compose list carries
    `@Immutable`, `composeCompiler` emits metrics and reports, `compose-stability.conf` is
    checked in, and `tools/compose_stability_check.py` fails when a list-rendered model
    loses its annotation. The first report reads 11 stable classes and 0 unstable.
  - Blocker: the remaining acceptance is a before-and-after frame-timing measurement, and
    `GridScrollBenchmark` is a Macrobenchmark that only produces real numbers on a physical
    device. A compiler report says the cells *can* skip recomposition; only the benchmark
    says what that was worth.
  - Resume by running `:baselineprofile:connectedFullBenchmarkAndroidTest` on a phone,
    against the commit before the annotations and the commit after, and recording both.

- **P2 — Split VideoWallpapersViewModel into delegates (1318 lines)**
  - The pure top-level helpers (feed parsing, cache codec, Reddit motion selection) are fully
    covered by `VideoWallpapersViewModelTest` and could move safely. The blocker is the other
    ~600 lines: `load()`, its per-source fetch orchestration, `streamUrls`/`_resolvedIds`
    eviction, `loadJob` cancellation ownership, and the YouTube path that calls the static
    `NewPipe.getService(...)` global. `VideoWallpapersViewModelTest` constructs no ViewModel and
    exercises none of this, so a delegate extraction of the loader is verifiable only by "it
    compiles" — no behavioral test would catch a wiring/loadJob/streaming regression, and this
    is the exact video-streaming path the on-device audit flagged fragile (BufferQueue storm).
  - Resume when the loader/streaming behavior can be exercised on a device/emulator (or once a
    JVM harness can drive `load()` with mocked NewPipe + provider APIs), then extract verbatim
    and confirm browse/apply/immersive paging on device.

- **P1 — Android 16 job-quota device evidence**
  - Source audit, complete worker ledger, WorkInfo stop-reason diagnostics, and the Android 16 capture packet are implemented.
  - Remaining: capture TOP-started and foreground-service-concurrent quota behavior on a connected Android 16+ device, including compat overrides, jobscheduler/services output, copied support bundle, and override reset evidence.

- **P2 — Exercise yt-dlp update promotion and rollback on a disposable device**
  - Current evidence: real YouTube sound search, extraction, playback, and ringtone
    apply succeeded on the API 29 Full debug emulator; search, extraction, and
    playback also succeeded in the installed v6.45.3 build on the S22. This
    disproves the former P0 missing-runtime/live-extraction claim.
  - Remaining: stage one accepted and one rejected updater payload, verify
    Settings reports the validated active version, then prove rollback leaves
    the prior extractor usable.
  - Blocker: needs a disposable device profile plus controlled updater payloads;
    do not replace the differently signed personal-phone installation.

- **P1 — YouTube PO-token live-provider validation**
  - The reviewed bgutil 1.3.1 plugin, SHA-256 install guard, credential-free HTTPS provider setting, yt-dlp request options, explicit extractor failover, and degraded Sounds state shipped in v6.36.0.
  - Remaining: configure a reachable self-hosted HTTPS bgutil endpoint and prove search/playback on a video that fails without a PO token.
  - Blocker: no external provider endpoint is configured, and the connected phone has Aura signed by a different key; replacing it would require uninstalling the user's app/data.

- **P2 — Complete the Video SurfaceView BufferQueue soak**
  - Saved Android 16 logs identified PlayerView zoom resizing a decoded 1280x720 stream to a 4117x2316 SurfaceView, followed by Qualcomm output-port configuration failures and two concurrent BufferQueue timeout streams. v6.36.0 now keeps both surfaces at fixed view bounds, moves crop scaling into the codec, and stops the feed player before immersive playback begins.
  - Current evidence: Reddit feed and immersive playback worked in the installed
    S22 production build and API 29 Full debug build with no Aura process error;
    two animated screenshots and the live-wallpaper service state were captured.
  - Remaining: capture the original two-minute feed-to-immersive logcat soak on a
    current debuggable Android 16 image and assert no sustained
    `dequeueBuffer` or codec-configuration failure.
  - Blocker: the S22 installation is signed differently and must not be replaced;
    use a disposable Android 16 image or a matching signed build.

- **P1 — Baseline Profile + Macrobenchmark** (Cycle 1)
  - Harness shipped 2026-06-04. The audit found its obsolete Favorites selector
    can silently measure the wrong screen; that actionable repair is now in
    ROADMAP.md.
  - Remaining after the selector fails closed: physical-device profile
    generation and before/after metrics comparison.
  - Blocker: the attached S22 has a differently signed production installation.
    Use another physical test profile or a matching signed build without
    replacing personal app data.

- **P1 — Android 17 Contact Picker** (Cycles 4/10)
  - Permission minimization shipped. Remaining: API 37 picker smoke + clear-ringtone validation after Android 17 toolchain.

- **P2 — Complete the 200% font, display-size, and contrast audit** (Cycle 5)
  - Verified code/layout defects: helper text is capped and truncates in
    `SettingsComponents.kt:82-88,141-148,212-219`,
    `SharedComponents.kt:503-509`, and
    `LibraryScreen.kt:239,290-305`; the feed history dropdown can overlap
    search controls at 200 percent in `SharedComponents.kt:401-417`,
    `WallpapersScreen.kt:519-561`, and `SoundsScreen.kt:495-554`.
    `UniversalSearchScreen.kt:346-385` already has the measured layout pattern
    to reuse.
  - The code defects above are now a ROADMAP.md P2 ("Stop clipping helper text
    at large font sizes"). The gate's `primaryMatrix` in
    `docs/qa/accessibility-release-gate.json` takes no waiver and stays red
    until every cell has evidence. Remaining blocker: capture every required
    route and nested dialog in AMOLED/dark/light at 200 percent, run
    Accessibility Scanner contrast and touch-target checks on a disposable
    device profile, and record each cell there.

- **P2 — Widget and live-wallpaper accessibility/localization coverage** (Cycle 5)
  - Needs widget actions TalkBack pass, keyguard placement, launcher picker inspection.

- **P0 — Store listing metadata preflight** (Cycle 8) (remaining)
  - Text validation shipped. Remaining: screenshot/feature-graphic asset requirement.

- **P1 — Screenshot and feature-graphic pipeline** (Cycle 8)
  - Framework shipped. Remaining: actual 4+ phone screenshots and feature graphic capture.

- **P1 — Android 17 large-screen/adaptive-layout smoke** (Cycle 10)
  - Needs tablet/foldable/landscape emulator screenshots.

- **P1 — Android 17 background-audio hardening regression suite** (Cycle 10)
  - Needs device logcat/dumpsys checks for AudioHardening entries.

- **P1 — Target-37 privacy/security compatibility preflight** (Cycle 10)
  - Needs Android 17 network smoke across all providers.

- **P1 — Rotation trigger reliability tests** (Cycle 14)
  - Needs device testing under various standby buckets, expedited quota exhaustion.

- **P1 — Background network and data-saver posture** (Cycle 14)
  - Remaining: run and archive the real capture packet on device.

- **P2 — Battery/vitals regression lab** (Cycle 14)
  - Needs physical device dumpsys/batterystats outputs.

---

## Blocker: Content Curation / Human Judgment

- **N-5 (remaining)** — Aura Originals bundled CC0 sound pack
  - Infrastructure + manifest schema shipped. Remaining: moderator review pass to curate 200-500 CC0 sound entries into `assets/aura_originals_manifest.json`.

- **P3 — Microsoft Spotlight daily-image source**
  - Blocker: Microsoft documents Spotlight content endpoints as Windows product endpoints, not
    as a supported public image API with redistribution and attribution terms. Community clients
    reverse-engineer the endpoint, but that is not enough to pass Aura's provider policy gate.
  - Resume when Microsoft publishes a stable third-party endpoint and terms that permit the
    requested opt-in wallpaper use, or when the release owner records an explicit legal decision
    accepting the current terms and endpoint risk.

- **P2 — Warn before a contact ringtone write that a SIM copy, a second account or a shared number will keep it from ringing**
  - Current state: after writing, Aura reads the contact back through the picker's URI grant and reports a ringtone the contact didn't keep, with the likely causes and fixes. The warning before the write isn't built.
  - Blocker: telling a SIM copy, a second account or another contact with the same number apart takes `RawContacts`, `Phone` and `PhoneLookup` queries, and every one of them needs `READ_CONTACTS`. Aura asks only for `WRITE_CONTACTS` on purpose, and `ContactPermissionContractTest` asserts `READ_CONTACTS` stays out of the manifest.
  - Resume when the owner decides whether a runtime `READ_CONTACTS` request, asked only on the contact screen, is worth it. If yes: treat a `sim` segment in the account type as SIM (`vnd.sec.contact.sim`, `com.android.contacts.sim`, MIUI's misspelled `com.anddroid.contacts.sim`), leave messaging overlay accounts (WhatsApp, Signal, Telegram) out of the account count or nearly every contact gets flagged, flag a number whose `PhoneLookup` resolves to a different contact, and update the privacy docs and the contract test in the same change.

---

## Blocker: Dependent on Other Blocked Items

- **P1 — Make Reddit galleries first-class wallpaper and video items**
  - Current state: the anonymous Atom feed drops preview-only galleries and does
    not provide a durable mixed-media child model.
  - Blocker: the accepted implementation depends on the registered Reddit OAuth
    response models from the blocked core-feed migration above. Building another
    parser around the unsupported anonymous feed would create a second transport
    that must immediately be removed.
  - Resume after the OAuth transport lands. Preserve post and child identities,
    attribution, order, dimensions, captions, mixed-media routing, selection,
    favorites, collections, export, and visible deleted-child states.

- **P2 — Source deletion and takedown reconciliation** (Cycle 3)
  - Room metadata, UI badges, gone classifier shipped.
  - Remaining: provider catalog reload pruning (needs callable/deploy) + community moderation integration (needs report queue deploy).

- **P2 — Source-deleted and rights-revoked local states** (Cycle 13)
  - Unavailable-source state and remote-gone classifier shipped.
  - Remaining: provider catalog pruning tied to deploy/callable work.

- **P1 — Preserve item-level license and provenance through durable flows** (Cycle 17)
  - Entity fields and export preservation shipped.
  - Remaining: action-capability fields (restrict download/share/edit based on license terms) need design + Room migration + UI gating.

- **P3 — Tag OkHttp calls for source diagnostics after the next OkHttp upgrade**
  - Current state: Aura exposes `SourceMetrics`, but request-purpose attribution is still spread across repositories.
  - Blocker: roadmap item explicitly depends on newer OkHttp call-tag/interceptor behavior after the next OkHttp upgrade.
  - Resume when OkHttp is upgraded beyond the current pinned stack and repository request builders can be updated/tested together.

---

## Roadmap file hygiene

`ROADMAP.md` is actionable only. Blocked work stays in this file, and duplicate
blocked-roadmap variants are normalized to this exact filename:
`Roadmap_Blocked.md`.

---

## How to unblock

| Blocker | Action needed |
|---------|--------------|
| N-1 Toolchain | Run AGP 9 / Gradle 9 / Kotlin 2.3 upgrade, verify assembleDebug/test/lint |
| Firebase Console | Owner logs into Firebase Console, deploys rules, registers App Check, grants Custom Claims |
| Physical device | Connect an Android device via adb (or start an Android 17 emulator with working adb) |
| Content curation | Owner/moderator reviews and selects CC0 sounds for the Aura Originals manifest |
