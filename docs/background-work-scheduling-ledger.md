# Background work scheduling ledger

Machine-readable contract: [background-work-scheduling-ledger.json](background-work-scheduling-ledger.json).

## Current decision

The checked ledger records the complete WorkManager scheduling contract and its
Android 16 quota/lifecycle audit. Status:
`android16QuotaAuditedDevicePending`.

This packet is intentionally source-backed. The release gate validates each
unique work row against the Kotlin scheduler source, the public runbook text,
release docs, and verify/release workflow wiring.

## Scheduling matrix

| Work | Worker | Type | Unique work | Policy | Timing | Constraints | Expedited |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Auto wallpaper rotation | `AutoWallpaperWorker` | Periodic | `auto_wallpaper` | `ExistingPeriodicWorkPolicy.UPDATE` | Default 360 minutes; WorkManager floor 15 minutes; one source, configurable clock-based day/night sources, or system light/dark theme sources | Connected network when any active phase is remote, unmetered when Wi-Fi-only is enabled, battery-not-low, optional charging, optional idle | No |
| Automatic backup | `AutoBackupWorker` | Periodic | `auto_backup` | `ExistingPeriodicWorkPolicy.UPDATE` | User-selected hours; minimum 1 hour | Battery-not-low | No |
| Daily wallpaper notification | `DailyWallpaperWorker` | Periodic | `daily_wallpaper` | `ExistingPeriodicWorkPolicy.KEEP` | 24 hours with 8-hour initial delay | Connected network | No |
| Ringtone restoration | `RingtoneRestorationWorker` | One-time | `ringtone_restoration` | `ExistingWorkPolicy.REPLACE` | Device boot or package replacement | No explicit constraints | No |
| Sound shuffle pool | `RingtoneShuffleWorker` | Periodic | `sound_shuffle_pool_$poolId` | `ExistingPeriodicWorkPolicy.UPDATE` | Per pool: 1, 6, 12, 24, or 72 hours | No explicit constraints | No |
| Sound shuffle pool, Run now | `RingtoneShuffleWorker` | One-time | `sound_shuffle_pool_${poolId}_now` | `ExistingWorkPolicy.REPLACE` | When the person taps Run now | No explicit constraints | No |
| Sound profile | `SoundProfileWorker` | Periodic | `sound_profile` | `ExistingPeriodicWorkPolicy.UPDATE` | 15 minutes | No explicit constraints | No |
| Wallpaper pack | `WallpaperPackWorker` | Periodic | `wallpaper_pack` | `ExistingPeriodicWorkPolicy.UPDATE` | 15 minutes | No explicit constraints | No |
| Weather effect refresh | `WeatherUpdateWorker` | Periodic | `weather_update` | `ExistingPeriodicWorkPolicy.KEEP` | 30 minutes | Connected network | No |
| Aura Originals download | `AuraOriginalsDownloader` | One-time | `aura_originals_download` | `ExistingWorkPolicy.KEEP` | Enqueued on app startup, idempotent after hashes match | Unmetered network | Yes, downgraded to non-expedited on quota exhaustion |
| Rotation trigger one-shot | `AutoWallpaperWorker` through `RotationTriggerService` | One-time | `rotation_trigger_oneshot` | `ExistingWorkPolicy.KEEP` | Unlock, screen-off, Tasker, MacroDroid, adb, or Termux trigger | Connected network; battery-not-low for unlock and screen-off | Tile and automation requests only, downgraded to non-expedited on quota exhaustion |
| Favorites batch download | `BatchDownloadWorker` | One-time | `favorites_batch_download` | `ExistingWorkPolicy.KEEP` | Download all or Download selected in Library favorites; resumes pending items after process death | Connected network | No |

## Android 16 quota audit

Every persistent Aura job is a `CoroutineWorker` managed by WorkManager. There
are no direct `JobScheduler` or `JobService` implementations and Aura never
retains `JobParameters`, so Android 16's
`STOP_REASON_TIMEOUT_ABANDONED` penalty does not directly apply. The release
check discovers every `CoroutineWorker` from source and requires an exact ledger
match so a new worker cannot silently bypass this conclusion.

Aura also has no long-running worker: no worker calls `setForeground` or
`setForegroundAsync`. Every worker explicitly propagates
`CancellationException`, allowing WorkManager to own job stop/completion
lifecycle rather than leaving in-process work behind after a stop.

Android 16 still charges ordinary and expedited WorkManager jobs against job
runtime quota when they continue after a TOP-state start and while they run
concurrently with a foreground service. The only Aura concurrency path is
`RotationTriggerService` enqueuing `rotation_trigger_oneshot`. That path uses
unique `KEEP` to coalesce chatty unlock and screen-off events, `REPLACE` for an
explicit tile or automation request so a waiting passive trigger can't swallow
its countdown restart (a rotation that is already applying finishes first,
because `AutoWallpaperWorker` applies under one run lock and can't be cancelled
mid-apply), and
`RUN_AS_NON_EXPEDITED_WORK_REQUEST` to preserve the request when expedited quota
is unavailable. Only explicit requests are expedited. WorkManager refuses to
build expedited work with a battery constraint, so the unlock and screen-off
rotations run as ordinary work and keep battery-not-low. Settings and copied support diagnostics now report every unique
work name and summarize non-active `WorkInfo.stopReason` values such as `QUOTA`,
`TIMEOUT`, `BACKGROUND_RESTRICTION`, and constraint stops.

## Deferral reasons

Settings and support diagnostics expose user-actionable hints for these states
where Aura can infer them from WorkInfo, metered/Data Saver receipts, and local
worker receipts:

- Connected network is unavailable.
- Unmetered network is unavailable for Wi-Fi-only or Aura Originals work.
- Battery-not-low, charging, or idle constraints are not met.
- Location permission or last-known location is missing for weather effects.
- Notification permission is denied for daily wallpaper notifications.
- The Reddit provider is disabled for daily wallpaper notifications.
- Expedited quota is exhausted and the work was downgraded to a normal
  WorkRequest.
- Unique KEEP coalesced a pending one-shot.
- Doze or App Standby delayed execution until a maintenance window.
- A remote fetch, manifest, or hash check failed and WorkManager is waiting for
  exponential backoff.

## Settings and support gaps

`Settings` > `Diagnostics` > `Background work` reads current `WorkInfo.State`
counts and Android stop reasons for every unique work name and shows active
metered/Data Saver state. Cycle 157 added persisted worker receipts for last
success UTC, last failure UTC, last error class, last result, and last deferral
reason. Cycle 158 merged those live receipts into copied support bundles. Cycle
159 added an `actionHint` row that translates Data Saver restrictions,
metered-only waits, source failures, hash/download validation failures,
permission cues, and retry/backoff state into user or support next steps. The
full Cycle 14 P0 item remains open until Settings diagnostics and support
bundles also expose, for every unique work name:

- enabled state;
- declared constraints;
- direct OS scheduler evidence for quota downgrade, low battery,
  Doze/App Standby, and constraint-delay causes that WorkManager does not expose
  through the current local receipt layer.

The P1 unique-work policy matrix is closed by this packet because the ledger
now records unique work names, work type, enqueue policy, interval, initial
delay, constraints, retry/backoff posture, schedule trigger, cancel trigger,
and update semantics.

## Release gate

Verify and release workflows run:

```bash
python3 tools/background_work_scheduling_check.py --policy docs/background-work-scheduling-ledger.json --repo-root .
```

The gate fails when:

- a required work row is missing;
- a source-discovered `CoroutineWorker` is absent from the Android 16 audit or
  ledger, swallows cancellation, starts long-running foreground work, or adds a
  direct `JobScheduler`/`JobService` dependency without updating the audit;
- a row loses its unique work name, worker class, source path, enqueue API,
  existing-work policy, constraints, deferral reasons, or source terms;
- Kotlin scheduler source no longer contains the reviewed unique work name or
  source terms;
- this document loses the status, matrix, Settings gap, release gate, source
  URLs, or any unique work name;
- verify/release workflow wiring or release runbook commands drift.

## Sources

- Android WorkManager WorkRequest definition, constraints, retry, expedited work, and periodic-work scheduling: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android WorkManager unique work management: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work
- Android WorkManager observation and stop reasons: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe
- Android WorkManager long-running worker quota behavior: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- Android WorkManager state model: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/states
- Android Doze and App Standby behavior: https://developer.android.com/training/monitoring-device-state/doze-standby
- Android 16 behavior changes for all apps: https://developer.android.com/about/versions/16/behavior-changes-all
