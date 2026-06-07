# Provider Credential Storage

This runbook classifies Aura provider credentials and records the current
Keystore decision. The machine-readable source is
`docs/security/provider-credential-storage.json`; the guard is
`tools/provider_credential_storage_check.py`.

## Decision

Current provider credentials remain in app-private Jetpack DataStore rather
than Android Keystore-backed encrypted storage. They are optional, user-entered
provider values, public release defaults are blank, diagnostics redact them,
and the DataStore preferences file is excluded from Android cloud backup and
device transfer.

This is not strong at-rest protection against a compromised or rooted device.
The disclosure is intentional for the current release posture: users can leave
provider keys blank, enter them only for providers they choose to use, and clear
stored values with the explicit Settings `Clear` action or by saving a blank
value. Revisit a Keystore migration
before treating any provider credential as required, account-bound, or
non-user-rotatable.

## Storage Surface

- DataStore name: `freevibe_prefs`.
- DataStore file: `datastore/freevibe_prefs.preferences_pb`.
- Android 11 Auto Backup: excluded in `backup_rules.xml`.
- Android 12+ cloud backup and device transfer: excluded in
  `data_extraction_rules.xml`.
- Diagnostics: support bundles and source diagnostics redact provider query,
  header, assignment, and `local.properties` credential shapes before sharing.

## Credentials

| ID | Provider | Classification | Runtime storage | Release default | User control |
| --- | --- | --- | --- | --- | --- |
| `wallhaven-api-key` | Wallhaven | `optionalQuotaKey` | DataStore key `wallhaven_api_key`; no bundled BuildConfig field. | Blank. | Settings > API Keys > Wallhaven API Key; Clear or save blank to remove. |
| `pexels-api-key` | Pexels | `optionalQuotaKey` | DataStore key `pexels_api_key`, defaulting to `BuildConfig.PEXELS_API_KEY`. | Blank in public release workflow. | Settings > API Keys > Pexels API Key; Clear or save blank to remove. |
| `pixabay-api-key` | Pixabay | `optionalQuotaKey` | DataStore key `pixabay_api_key`, defaulting to `BuildConfig.PIXABAY_API_KEY`. | Blank in public release workflow. | Settings > API Keys > Pixabay API Key; Clear or save blank to remove. |
| `freesound-api-key` | Freesound | `optionalQuotaKey` | DataStore key `freesound_api_key`, defaulting to `BuildConfig.FREESOUND_API_KEY`. | Blank in public release workflow. | Settings > API Keys > Freesound API Key; Clear or save blank to remove. |
| `soundcloud-client-id` | SoundCloud | `publicClientId` | BuildConfig-only `SOUNDCLOUD_CLIENT_ID`; no DataStore key. | Blank in public release workflow. | No Settings field; blank public default makes the dormant source return no results. |
| `stability-ai-key` | Stability AI | `paidSensitiveSecret` | DataStore key `stability_ai_key`, defaulting to `BuildConfig.STABILITY_AI_KEY`. | Blank in public release workflow. | Settings > API Keys > Stability AI API Key and generated wallpaper key field; Clear or save blank to remove. |

## Guard

Run:

```powershell
py -3 tools\provider_credential_storage_check.py --policy docs\security\provider-credential-storage.json --repo-root .
```

The guard fails if a credential row is missing from this runbook, if a
DataStore preference key is not declared in `PreferencesManager`, if a Settings
label or explicit Clear action is missing for DataStore-backed credentials, if
Gradle release defaults drift away from blank provider values, if DataStore
backup exclusions disappear, or if diagnostics/privacy docs stop describing
redaction and device storage.

The guard also treats `stability-ai-key` as the paid-sensitive sentinel row. It
fails if Stability stops being a DataStore-backed `paidSensitiveSecret`, if the
`STABILITY_AI_KEY` / `stability.ai.key` release default is no longer blank, if
`stability.ai.key` is missing from redaction coverage, or if the explicit Clear
control is no longer documented.
