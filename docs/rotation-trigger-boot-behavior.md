# Rotation trigger boot behavior

Aura removed `android.permission.RECEIVE_BOOT_COMPLETED` because the app does
not register a boot receiver. The machine-readable contract is
[`rotation-trigger-boot-behavior.json`](rotation-trigger-boot-behavior.json).

## Current decision

| Field | Value |
| --- | --- |
| Status | `permissionRemoved` |
| Removed permission | `android.permission.RECEIVE_BOOT_COMPLETED` |
| Boot receiver | None |
| User-facing behavior | Rotation triggers resume after opening Aura. |

## Behavior

Opt-in unlock and screen-off rotation triggers use
`RotationTriggerService`, which dynamically registers `ACTION_USER_PRESENT` and
`ACTION_SCREEN_OFF` while the foreground service is running. Aura starts or
stops that service when the user changes the Settings toggles and reconciles
the service on app cold start.

After a device reboot, Aura does not start itself from boot. Existing
WorkManager periodic jobs can continue through platform scheduling, but
unlock/screen-off trigger listeners resume after opening Aura because there is
no boot receiver.

## Release gate

Verify and release workflows must run:

- `tools/rotation_boot_permission_check.py --policy docs/rotation-trigger-boot-behavior.json --repo-root .`

The gate fails if `android.permission.RECEIVE_BOOT_COMPLETED` returns to the
manifest, if boot-completed source terms appear in app code, if store/privacy
disclosure docs still claim boot scheduling, or if the boot behavior packet
loses the current decision.

## Future boot receiver option

If Aura later needs boot restoration for rotation triggers, add a dedicated
receiver only after the release owner accepts the foreground-service policy
impact. The receiver must:

- Start `RotationTriggerService` only when the user already opted into unlock or
  screen-off triggers.
- Preserve the visible foreground notification and special-use foreground
  service declaration.
- Update Play App content, Data safety, alternative-store disclosures, release
  QA, and this packet in the same change.

## Sources

- Android `ACTION_BOOT_COMPLETED`: https://developer.android.com/reference/android/content/Intent#ACTION_BOOT_COMPLETED
- Android foreground service types: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Play Console foreground service declarations: https://support.google.com/googleplay/android-developer/answer/13392821
