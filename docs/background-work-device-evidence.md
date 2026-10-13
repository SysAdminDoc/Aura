# Background work device evidence

Machine-readable contract: [background-work-device-evidence.json](background-work-device-evidence.json).

## Current decision

This document defines the device/emulator evidence packet for background
schedulers, including the Android 16 quota audit. Status:
`android16PacketReadyDevicePending`.

This packet does not claim that live scheduler evidence has been captured. It
defines the exact adb, dumpsys, WorkManager, Data Saver, low-battery, Doze/App
Standby, and rotation-trigger evidence required before release notes or support
docs can claim those states were verified on a device. The package under test is
`com.freevibe`.

## Scenario matrix

| Scenario | Work names | Required evidence |
| --- | --- | --- |
| `workmanager-baseline` - WorkManager baseline state | All 10 unique work names in the scheduling ledger | Baseline `jobscheduler`, service, package, and notes artifacts before scheduler stress tests. |
| `metered-data-saver` - Metered network and Data Saver state | `auto_wallpaper`, `auto_wallpaper_catch_up`, `daily_wallpaper`, `weather_update`, `aura_originals_download`, `rotation_trigger_oneshot` | Connectivity, netpolicy, jobscheduler, and notes artifacts proving Settings/support action hints match metered and restricted-background state. |
| `low-battery-constraint` - Low battery constraint state | `auto_wallpaper`, `auto_wallpaper_catch_up`, `auto_backup`, `rotation_trigger_oneshot` | Battery, jobscheduler, restore, and notes artifacts proving battery-not-low constrained work defers while battery is forced low. |
| `doze-standby` - Doze and App Standby scheduling delay | All 10 unique work names in the scheduling ledger | Device-idle, jobscheduler, restore, and notes artifacts proving Doze or restricted standby state and the matching Settings diagnostics copy. |
| `android16-job-quota` - Android 16 TOP-started and FGS-concurrent job quota | All 10 unique work names in the scheduling ledger | Compat overrides, jobscheduler, service, support-bundle, restore, and notes artifacts proving quota behavior, stop-reason visibility, and absence of abandoned direct jobs. |
| `rotation-trigger-coalescing` - Rotation trigger coalescing and fallback | `rotation_trigger_oneshot`, `auto_wallpaper` | Public broadcast, jobscheduler, service, and notes artifacts proving repeated trigger broadcasts coalesce and any expedited fallback is documented. |

## Artifact packet

All captured files must live under `artifacts/background-work-device-evidence/`.
The checked required artifact paths are:

- `artifacts/background-work-device-evidence/workmanager-baseline-jobscheduler.txt`
- `artifacts/background-work-device-evidence/workmanager-baseline-services.txt`
- `artifacts/background-work-device-evidence/workmanager-baseline-package.txt`
- `artifacts/background-work-device-evidence/workmanager-baseline-notes.md`
- `artifacts/background-work-device-evidence/metered-data-saver-connectivity.txt`
- `artifacts/background-work-device-evidence/metered-data-saver-netpolicy.txt`
- `artifacts/background-work-device-evidence/metered-data-saver-jobscheduler.txt`
- `artifacts/background-work-device-evidence/metered-data-saver-notes.md`
- `artifacts/background-work-device-evidence/low-battery-battery.txt`
- `artifacts/background-work-device-evidence/low-battery-jobscheduler.txt`
- `artifacts/background-work-device-evidence/low-battery-restore.txt`
- `artifacts/background-work-device-evidence/low-battery-notes.md`
- `artifacts/background-work-device-evidence/doze-standby-deviceidle.txt`
- `artifacts/background-work-device-evidence/doze-standby-jobscheduler.txt`
- `artifacts/background-work-device-evidence/doze-standby-restore.txt`
- `artifacts/background-work-device-evidence/doze-standby-notes.md`
- `artifacts/background-work-device-evidence/android16-quota-compat.txt`
- `artifacts/background-work-device-evidence/android16-quota-jobscheduler.txt`
- `artifacts/background-work-device-evidence/android16-quota-services.txt`
- `artifacts/background-work-device-evidence/android16-quota-support-bundle.txt`
- `artifacts/background-work-device-evidence/android16-quota-restore.txt`
- `artifacts/background-work-device-evidence/android16-quota-notes.md`
- `artifacts/background-work-device-evidence/rotation-trigger-broadcasts.txt`
- `artifacts/background-work-device-evidence/rotation-trigger-jobscheduler.txt`
- `artifacts/background-work-device-evidence/rotation-trigger-services.txt`
- `artifacts/background-work-device-evidence/rotation-trigger-notes.md`

Notes files must state device model/API level, app version, build variant,
network state, battery state, steps run, and whether the copied support bundle
matched the captured device state.

## ADB capture commands

Baseline:

```bash
adb shell dumpsys jobscheduler com.freevibe
adb shell dumpsys activity services com.freevibe
adb shell dumpsys package com.freevibe
```

Metered network and Data Saver:

```bash
adb shell dumpsys connectivity
adb shell dumpsys netpolicy
adb shell dumpsys jobscheduler com.freevibe
```

Low battery:

```bash
adb shell dumpsys battery
adb shell cmd battery unplug
adb shell cmd battery set level 10
adb shell dumpsys jobscheduler com.freevibe
```

After the low-battery capture, restore the device battery override and save the
output as `artifacts/background-work-device-evidence/low-battery-restore.txt`:

```bash
adb shell cmd battery reset
adb shell dumpsys battery
```

Doze and App Standby:

```bash
adb shell dumpsys deviceidle
adb shell dumpsys battery unplug
adb shell dumpsys deviceidle force-idle
adb shell am set-standby-bucket com.freevibe restricted
adb shell dumpsys jobscheduler com.freevibe
```

After the Doze/App Standby capture, restore the device and save the output as
`artifacts/background-work-device-evidence/doze-standby-restore.txt`:

```bash
adb shell dumpsys deviceidle unforce
adb shell am set-standby-bucket com.freevibe active
adb shell cmd battery reset
```

Rotation trigger coalescing:

```bash
adb shell am broadcast -a com.freevibe.action.ROTATE_NOW -p com.freevibe
adb shell am broadcast -a com.freevibe.action.SHUFFLE_NOW -p com.freevibe
adb shell dumpsys jobscheduler com.freevibe
adb shell dumpsys activity services com.freevibe
```

Android 16 TOP-started and foreground-service-concurrent quota enforcement:

```bash
adb shell am compat enable OVERRIDE_QUOTA_ENFORCEMENT_TO_TOP_STARTED_JOBS com.freevibe
adb shell am compat enable OVERRIDE_QUOTA_ENFORCEMENT_TO_FGS_JOBS com.freevibe
adb shell am set-standby-bucket com.freevibe active
adb shell dumpsys jobscheduler com.freevibe
adb shell dumpsys activity services com.freevibe
```

Capture the Settings diagnostics and copied support bundle after exercising the
rotation trigger and other enabled workers. The packet must correlate any
`QUOTA`, `TIMEOUT`, `BACKGROUND_RESTRICTION`, or constraint stop reason with the
jobscheduler/service state, and confirm there is no Aura `JobService`, retained
`JobParameters`, or `STOP_REASON_TIMEOUT_ABANDONED`. Then restore both compat
changes and save the output:

```bash
adb shell am compat reset OVERRIDE_QUOTA_ENFORCEMENT_TO_TOP_STARTED_JOBS com.freevibe
adb shell am compat reset OVERRIDE_QUOTA_ENFORCEMENT_TO_FGS_JOBS com.freevibe
```

## Release gate

Verify and release workflows run:

```bash
python3 tools/background_work_device_evidence_check.py --policy docs/background-work-device-evidence.json --repo-root .
```

The gate fails when:

- a required scenario is missing;
- a scenario loses a unique work name from the scheduling ledger;
- a required adb, dumpsys, jobscheduler, battery, Data Saver, Doze/App Standby,
  or rotation-trigger command is removed from the policy or this document;
- artifact paths move outside `artifacts/background-work-device-evidence/`;
- source URLs, release docs, or workflow wiring drift.

## Sources

- Android Debug Bridge: https://developer.android.com/tools/adb
- Android WorkManager request constraints and backoff: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android WorkManager observation and stop reasons: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/observe
- Android WorkManager long-running worker quota behavior: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running
- Android WorkManager state model: https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/states
- Android Doze and App Standby behavior: https://developer.android.com/training/monitoring-device-state/doze-standby
- Android Data Saver guidance: https://developer.android.com/develop/connectivity/network-ops/data-saver
- Android 16 behavior changes for all apps: https://developer.android.com/about/versions/16/behavior-changes-all
