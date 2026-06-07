# Crash diagnostics

Aura does not use automatic crash analytics. If a release-only crash, freeze, or ANR happens, users can create a local diagnostics bundle from:

`Settings` > `Diagnostics` > `Crash diagnostics bundle`

The bundle includes:

- Aura version and build type.
- Android version, security patch, device model, and ABI.
- Active source/provider context from the current session.
- Auto-wallpaper and scheduler source settings.
- Background-work support context for the current unique WorkManager surfaces:
  auto wallpaper rotation, daily wallpaper notification, weather refresh, Aura
  Originals download, and rotation trigger one-shots.
- Last local crash timestamp and sanitized `crash.log` tail.
- Reproduction fields for crash or ANR reports.

The bundle is local until the user taps `Copy` or `Share`. Aura does not upload it automatically.

## Background work context

The background-work section records each known unique work name, the current
enabled state Aura can infer from local settings, the expected network posture,
and the constraints that explain common deferrals. This is support context, not
a live scheduler audit.

For live scheduler state, use `Settings` > `Diagnostics` > `Background work`.
That dialog reads WorkManager unique-work `WorkInfo` state counts plus
`ConnectivityManager` metered/Data Saver status, and now includes persisted
last result, success/failure timestamps, error class, and deferral reason from
worker execution. The copied support bundle includes the same live background
work receipt snapshot when it can be read locally. Each row also includes an
action hint that translates Data Saver, metered-network, source, hash,
permission, and retry/backoff receipts into the next user or support step. The
remaining diagnostics gap is direct OS scheduler evidence for cases such as
quota downgrade, low battery, and Doze/App Standby maintenance-window delays.

## Redaction

The generated bundle redacts:

- Bearer tokens and authorization headers.
- Provider query-string values named `apikey`, `key`, `token`, and `client_id`.
- API keys, tokens, passwords, secrets, client IDs, and dotted provider
  property names in assignment form, including `local.properties` entries such
  as `stability.ai.key`.
- App-private Android paths under `com.freevibe`.
- `file://` paths.

The same request redactor is used before in-app source diagnostics store the
last provider error detail for the current session.

Provider credential storage is classified in
[`docs/security/provider-credential-storage.md`](../security/provider-credential-storage.md).
The checked policy keeps user-entered provider keys out of backups, device
transfer, source diagnostics, and support bundles, but does not claim
Keystore-backed at-rest protection for the current optional provider keys.

## Sources

- AndroidX WorkManager exposes unique-work status lookups through
  `getWorkInfosForUniqueWork` and related observable APIs:
  https://developer.android.com/reference/androidx/work/WorkManager
- Android's Data Saver guidance points apps at
  `ConnectivityManager.isActiveNetworkMetered()` and
  `ConnectivityManager.getRestrictBackgroundStatus()` for metered and restricted
  background data state:
  https://developer.android.com/develop/connectivity/network-ops/data-saver

Before opening a GitHub issue, paste the bundle into the `Crash or ANR report` issue template and fill in the reproduction fields.
