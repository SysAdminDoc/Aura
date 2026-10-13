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

Wallpaper rotation is the one place where the network is no longer a
WorkManager constraint. A rotation saves the next wallpaper ahead of time, and
that copy can only apply offline if the work is allowed to start offline. So
`auto_wallpaper` and `rotation_trigger_oneshot` start on any network or none,
and `AutoWallpaperWorker` asks `RotationNetworkGate` before every source call
and every download, including the one that saves the next wallpaper. A run with
nothing saved and no connection it may use ends with success, writes a
`deferred` receipt and hands the rotation to `auto_wallpaper_catch_up`, which
does carry the network constraint. The worker never retries or polls while the
phone is offline.

## Network posture matrix

| Work | Unique work | Network posture | Metered behavior | Data Saver posture |
| --- | --- | --- | --- | --- |
| Auto wallpaper rotation | `auto_wallpaper` | Not required by WorkManager; the worker checks before it fetches | A saved wallpaper applies on any network or none; fetches and downloads are blocked on metered when Wi-Fi-only is enabled | Settings reads active metered/Data Saver state |
| Rotation catch-up | `auto_wallpaper_catch_up` | Connected by default; unmetered when Wi-Fi-only is enabled | User-selectable | Settings reads active metered/Data Saver state |
| Automatic backup | `auto_backup` | Not required | No network used | Local SAF export is unaffected by Data Saver |
| Daily wallpaper notification | `daily_wallpaper` | Connected | Allowed for Reddit metadata and thumbnail fetch | Settings reads active metered/Data Saver state |
| Ringtone restoration | `ringtone_restoration` | Not required | No network used | Local URI restoration is unaffected by Data Saver |
| Sound shuffle pool | `sound_shuffle_pool_$poolId` | Not required | No network used | Local pool members are unaffected by Data Saver |
| Sound shuffle pool, Run now | `sound_shuffle_pool_${poolId}_now` | Not required | No network used | Local pool members are unaffected by Data Saver |
| Sound profile | `sound_profile` | Not required | No network used | Local profile application is unaffected by Data Saver |
| Wallpaper pack | `wallpaper_pack` | Not required at scheduling time | Local slots use no network; remote locators may use the active metered network | Remote locators inherit platform Data Saver behavior and retry on the next periodic run |
| Weather effect refresh | `weather_update` | Connected | Allowed for lightweight Open-Meteo refresh | Settings reads active metered/Data Saver state |
| Aura Originals download | `aura_originals_download` | Unmetered | Blocked | Current larger-transfer-safe posture; Settings reads active metered/Data Saver state |
| Rotation trigger one-shot | `rotation_trigger_oneshot` | Not required by WorkManager; the worker checks before it fetches; battery-not-low for unlock and screen-off | Same as auto wallpaper rotation, so Wi-Fi-only now covers tile, automation, unlock and screen-off rotations too | Settings reads active metered/Data Saver state; expedited-quota labeling remains open |
| Favorites batch download | `favorites_batch_download` | Connected | Allowed for a batch the person started, within each provider's per-action cap | Started from the foreground; DownloadManager size caps apply per item |

## Data Saver handling

Android exposes Data Saver state through `ConnectivityManager`, including
metered-network checks and background restriction status. Aura now shows those
values in Settings diagnostics and support bundles. When Data Saver reports
restricted background data, diagnostics now tell the user to allow unrestricted
data for Aura or use Wi-Fi before refreshing the receipt. The release posture
is:

- larger first-run Aura Originals downloads require unmetered network;
- automatic backup, ringtone restoration, sound shuffle pools, sound profiles, and local
  wallpaper-pack slots perform no remote request;
- every wallpaper rotation, scheduled or triggered, can be user-tightened to
  unmetered network through the existing Wi-Fi-only setting. The worker
  enforces it before each fetch and download, and the catch-up one-shot waits
  on an unmetered network constraint;
- a rotation that Data Saver or a missing network leaves without a usable
  connection applies a saved wallpaper if it has one, and otherwise waits in
  the catch-up one-shot without retrying;
- daily wallpaper and weather refresh use connected-network constraints and
  diagnostics label them as metered/Data Saver candidates when the live network
  receipt indicates a restriction;
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
