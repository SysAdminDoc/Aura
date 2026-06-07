# Rotation trigger foreground-service policy

Aura uses `RotationTriggerService` for opt-in wallpaper rotation triggers that
listen for device unlock and screen-off broadcasts while the app process is
alive. The machine-readable contract is
[`rotation-trigger-fgs-policy.json`](rotation-trigger-fgs-policy.json).

## Current decision

| Field | Value |
| --- | --- |
| Status | `ownerActionRequired` |
| Service | `.service.RotationTriggerService` |
| Foreground service type | `specialUse` |
| Manifest subtype | `wallpaper_rotation_triggers` |
| Required permissions | `android.permission.FOREGROUND_SERVICE`, `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` |
| Current Play posture | Owner must capture Play Console declaration evidence and a demo video before Play production submission. |

## Service contract

`RotationTriggerService` is exported false and starts only when the user enables
"Change on every unlock" or the screen-off pre-stage toggle in Settings. The
service dynamically registers `ACTION_USER_PRESENT` and `ACTION_SCREEN_OFF`
with `Context.RECEIVER_NOT_EXPORTED`, posts a persistent low-priority
notification, and starts foreground with
`ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on Android 14 and newer.

Each trigger enqueues the existing `AutoWallpaperWorker` as one unique
`rotation_trigger_oneshot` request with `ExistingWorkPolicy.KEEP`. The request
uses connected-network and battery-not-low constraints, and `OutOfQuotaPolicy`
downgrades expedited work to non-expedited work when quota is unavailable. This
means the service listens for opted-in trigger events, while actual wallpaper
application can still be deferred by system constraints.

## Play Console declaration packet

Use these answers for the Play Console foreground-service declaration:

| Prompt | Aura answer |
| --- | --- |
| App functionality | Aura lets users opt into wallpaper rotation when the device unlocks or when the screen turns off, so the next visible wallpaper can be refreshed without requiring a manual app open. |
| Why foreground service | Android does not deliver these trigger broadcasts to a manifest-only background receiver for Aura's use case. The foreground service keeps a user-visible, opt-in listener alive only while at least one trigger toggle is enabled. |
| If deferred | Wallpaper rotation may happen later or not at all until network and battery constraints allow `AutoWallpaperWorker` to run. The app remains usable and the user can still change wallpaper manually. |
| If interrupted | Trigger listening stops until Aura restarts the service from Settings or app cold start. Existing wallpapers remain applied; no user data is lost. |
| Demo video status | `ownerActionRequired`; record Settings toggle enablement, the persistent notification, unlock or screen-off trigger behavior, and toggle disablement stopping the service. |

## Owner evidence

Before Play production submission, archive evidence for:

- Play Console foreground-service declaration row for `specialUse`.
- Demo video URL or internal evidence pointer showing how the user enables,
  triggers, and disables the feature.
- Android 14 or newer smoke result showing the notification while enabled and
  service stop after both toggles are disabled.

## Release gate

Verify and release workflows must run:

- `tools/rotation_fgs_policy_check.py --policy docs/rotation-trigger-fgs-policy.json --repo-root .`

The gate fails if the foreground-service permission, service type, subtype
property, source safeguards, Play packet row, owner action, or workflow wiring
drifts.

## Sources

- Android foreground service types: https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
- Android 14 foreground service type requirements: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Play Console foreground service declarations: https://support.google.com/googleplay/android-developer/answer/13392821
