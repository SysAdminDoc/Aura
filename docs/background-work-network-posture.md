# Background work network posture

Machine-readable contract: [background-work-network-posture.json](background-work-network-posture.json).

## Current decision

Cycle 154 records the network and Data Saver posture for every WorkManager
surface listed in [background-work-scheduling-ledger.md](background-work-scheduling-ledger.md).
Status: `networkPostureCheckedSettingsPending`.

This packet does not change runtime behavior. It makes the current posture
explicit and checks it against scheduler source. Cycle 156 added the first
Settings receipt slice: `Settings` > `Diagnostics` > `Background work` now
reads active metered-network state and Data Saver restricted-background status
from `ConnectivityManager`. Cycle 159 added action hints that combine those
network receipts with worker last-run receipts in Settings and copied support
bundles.

## Network posture matrix

| Work | Unique work | Network posture | Metered behavior | Data Saver posture |
| --- | --- | --- | --- | --- |
| Auto wallpaper rotation | `auto_wallpaper` | Connected by default; unmetered when Wi-Fi-only is enabled | User-selectable | Settings reads active metered/Data Saver state |
| Daily wallpaper notification | `daily_wallpaper` | Connected | Allowed for Reddit metadata and thumbnail fetch | Settings reads active metered/Data Saver state |
| Weather effect refresh | `weather_update` | Connected | Allowed for lightweight Open-Meteo refresh | Settings reads active metered/Data Saver state |
| Aura Originals download | `aura_originals_download` | Unmetered | Blocked | Current larger-transfer-safe posture; Settings reads active metered/Data Saver state |
| Rotation trigger one-shot | `rotation_trigger_oneshot` | Connected plus battery-not-low | Allowed for explicit user/automation-triggered attempts | Settings reads active metered/Data Saver state; expedited-quota labeling remains open |

## Data Saver handling

Android exposes Data Saver state through `ConnectivityManager`, including
metered-network checks and background restriction status. Aura now shows those
values in Settings diagnostics and support bundles. When Data Saver reports
restricted background data, diagnostics now tell the user to allow unrestricted
data for Aura or use Wi-Fi before refreshing the receipt. The release posture
is:

- larger first-run Aura Originals downloads require unmetered network;
- auto wallpaper can be user-tightened to unmetered network through the
  existing Wi-Fi-only setting;
- daily wallpaper, weather refresh, and explicit rotation trigger attempts use
  connected-network constraints and diagnostics label them as metered/Data
  Saver candidates when the live network receipt indicates a restriction;
- Data safety and endpoint inventory rows remain the privacy surface for which
  remote services can be contacted by each worker.

## Release gate

Verify and release workflows run:

```bash
python3 tools/background_work_network_check.py --policy docs/background-work-network-posture.json --repo-root .
```

The gate fails when:

- a network posture row is missing for a scheduled work item;
- a row's unique work name no longer matches the scheduling ledger;
- worker or settings source terms drift;
- a row loses its metered policy, Data Saver policy, privacy surface, or release
  risk classification;
- this document loses required sections, source URLs, or unique work names;
- release docs or verify/release workflows lose the command.

## Sources

- Android WorkManager constraints and `NetworkType.UNMETERED`: https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work
- Android Data Saver guidance: https://developer.android.com/develop/connectivity/network-ops/data-saver
- AndroidX WorkManager `NetworkType` reference: https://developer.android.com/reference/androidx/work/NetworkType
- Android `ConnectivityManager` metered and background restriction APIs: https://developer.android.com/reference/android/net/ConnectivityManager
