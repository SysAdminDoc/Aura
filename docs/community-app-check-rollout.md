# Community App Check Rollout

Cycle 53 installs Firebase App Check on the Android client. Debug builds use the
debug provider, and release builds use the Play Integrity provider. Enforcement
is intentionally a console-side rollout step after request metrics show that
legitimate installs are sending tokens.

## Client State

- `app/src/debug/java/com/freevibe/service/AppCheckInstaller.kt` installs the
  debug provider for debug builds.
- `app/src/release/java/com/freevibe/service/AppCheckInstaller.kt` installs the
  Play Integrity provider for release builds.
- `FreeVibeApp.onCreate()` installs App Check before the app warms community
  identity, votes, reports, uploads, or other Firebase-backed repositories.
- `app/build.gradle.kts` keeps App Check versions under the existing Firebase
  BoM and keeps the debug provider out of release builds.

## Firebase Console Setup

1. Open Firebase Console > Security > App Check.
2. Register Android app `com.freevibe` with the Play Integrity provider.
3. Add the release signing certificate SHA-256 fingerprint used for GitHub
   release APKs.
4. For the current GitHub/Obtainium/Izzy distribution path, configure Play
   Integrity advanced settings for outside-Google-Play installs:
   `PLAY_RECOGNIZED` not required, `LICENSED` not required, and Device
   integrity as the minimum device verdict.
5. Keep enforcement disabled while monitoring App Check request metrics for
   Realtime Database, Cloud Storage, and Authentication.

## Debug Builds

1. Build and launch a debug APK.
2. Trigger a Firebase-backed action such as report submit, vote, follow, or
   upload.
3. Read Logcat for the debug token printed by the debug provider.
4. Register that token in Firebase Console > App Check > app overflow menu >
   Manage debug tokens.
5. Keep debug tokens private. Do not commit them, paste them into issue
   trackers, or include them in release artifacts.

## Enforcement Gate

Do not enable enforcement until all of these are true:

- Debug provider token is registered and local Firebase-backed actions still
  work.
- A signed release APK has been installed through the intended public channel
  and has generated valid App Check metrics.
- Realtime Database, Cloud Storage, and Authentication metrics show that normal
  traffic is verified.
- The release owner has recorded the enforcement date and rollback plan in the
  backend evidence packet described by
  [`docs/community-backend-runbook.md`](community-backend-runbook.md).

## Remaining Abuse Controls

App Check proves a request came from an authorized app install. It does not
limit a legitimate client from submitting too many reports, votes, uploads,
follows, or profile edits. The next backend slice still needs quota counters or
trusted server mediation for those write paths. The current quota policy,
protected ledger namespaces, and callable migration order are tracked in
[`docs/community-quota-rate-limits.md`](community-quota-rate-limits.md). Cycle
63 adds the callable function names, final write paths, and limited-use App
Check token decisions in
[`docs/community-callable-quota-enforcement.md`](community-callable-quota-enforcement.md).
