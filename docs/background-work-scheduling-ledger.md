# Background work scheduling ledger

Machine-readable contract: [background-work-scheduling-ledger.json](background-work-scheduling-ledger.json).

## Current decision

Cycle 153 records the current WorkManager scheduling contract in a checked
ledger before adding the full Settings diagnostics surface. Status:
`ledgerReadySettingsPending`.

This packet is intentionally source-backed. The release gate validates each
unique work row against the Kotlin scheduler source, the public runbook text,
release docs, and verify/release workflow wiring.

## Scheduling matrix

| Work | Worker | Type | Unique work | Policy | Timing | Constraints | Expedited |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Auto wallpaper rotation | `AutoWallpaperWorker` | Periodic | `auto_wallpaper` | `ExistingPeriodicWorkPolicy.UPDATE` | Default 360 minutes; WorkManager floor 15 minutes | Connected network by default, unmetered when Wi-Fi-only is enabled, battery-not-low, optional charging, optional idle | No |
| Daily wallpaper notification | `DailyWallpaperWorker` | Periodic | `daily_wallpaper` | `ExistingPeriodicWorkPolicy.KEEP` | 24 hours with 8-hour initial delay | Connected network | No |
| Weather effect refresh | `WeatherUpdateWorker` | Periodic | `weather_update` | `ExistingPeriodicWorkPolicy.KEEP` | 30 minutes | Connected network | No |
| Aura Originals download | `AuraOriginalsDownloader` | One-time | `aura_originals_download` | `ExistingWorkPolicy.KEEP` | Enqueued on app startup, idempotent after hashes match | Unmetered network | Yes, downgraded to non-expedited on quota exhaustion |
| Rotation trigger one-shot | `AutoWallpaperWorker` through `RotationTriggerService` | One-time | `rotation_trigger_oneshot` | `ExistingWorkPolicy.KEEP` | Unlock, screen-off, Tasker, MacroDroid, adb, or Termux trigger | Connected network and battery-not-low | Yes, downgraded to non-expedited on quota exhaustion |

## Deferral reasons

Settings and support diagnostics still need to expose these user-actionable
states from WorkInfo and local receipts:

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

The full Cycle 14 P0 item remains open until Settings diagnostics and support
bundles expose, for every unique work name:

- enabled state;
- last success UTC;
- last failure UTC;
- last error class;
- current `WorkInfo.State`;
- declared constraints;
- user-actionable deferral reason text.

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
- Android WorkManager state model: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/states
- Android Doze and App Standby behavior: https://developer.android.com/training/monitoring-device-state/doze-standby
