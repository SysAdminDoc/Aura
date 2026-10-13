# Provider Credential Storage

The checked policy is `docs/security/provider-credential-storage.json`. Its guard is
`tools/provider_credential_storage_check.py`.

## Storage decision

Aura stores user-entered provider credentials in
`aura_provider_credentials.xml`. Each value is encrypted with AES-GCM and a
non-exportable Android Keystore key named `aura_provider_credentials_v1`.
Legacy values in Jetpack DataStore move to the encrypted store on first read or
write, then the old value is removed.

Both `aura_provider_credentials.xml` and
`datastore/freevibe_prefs.preferences_pb` are excluded from Android 11 cloud
backup, Android 12+ cloud backup, and device transfer.

## Recovery states

At rest, readable values remain encrypted and never appear in diagnostics.

Backup and transfer rules exclude the encrypted file because its key is bound
to the device. Aura still defends against an OEM or manual restore that copies
ciphertext without the key.

Restore has two outcomes:

- A temporary Keystore error leaves ciphertext untouched. Settings asks the
  user to unlock the phone and retry.
- A missing or invalidated key, an invalid envelope, or failed AES-GCM
  authentication makes the value unrecoverable. Aura removes the unreadable
  ciphertext and saves a non-secret re-entry marker. Settings keeps a visible
  notice until each affected blank key is saved or cleared.

Deletion is available through each visible key dialog's Clear action or by
saving a blank value. Android clear-app-data and uninstall remove the encrypted
preferences and app-owned Keystore key. The retired Freesound value is deleted
at startup.

Export never includes provider credentials. Library backups ignore credential
fields on import and do not write them on export. Collection exports,
theme-pack files, generated wallpaper reports, source diagnostics, and support
bundles also exclude the values. Diagnostic text reports only whether storage
is available, temporarily unavailable, or needs re-entry.

## Credential classes

| ID | Provider | Classification | At rest | Backup and transfer | Restore | Deletion | Export |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `wallhaven-api-key` | Wallhaven | `optionalQuotaKey` | Android Keystore AES-GCM. | Excluded. | Clear unreadable ciphertext and request re-entry. | Settings Clear, save blank, clear app data, or uninstall. | Excluded. |
| `pexels-api-key` | Pexels | `optionalQuotaKey` | Android Keystore AES-GCM. Public BuildConfig default is blank. | Excluded. | Clear unreadable ciphertext and request re-entry. | Settings Clear, save blank, clear app data, or uninstall. | Excluded. |
| `pixabay-api-key` | Pixabay | `optionalQuotaKey` | Android Keystore AES-GCM. Public BuildConfig default is blank. | Excluded. | Clear unreadable ciphertext and request re-entry. | Settings Clear, save blank, clear app data, or uninstall. | Excluded. |
| `freesound-api-key` | Freesound | `optionalQuotaKey` and retired. | Encrypted only until startup cleanup. | Excluded. | Never restored. | Startup cleanup, clear app data, or uninstall. | Excluded. |
| `stability-ai-key` | Stability AI | `paidSensitiveSecret` | Android Keystore AES-GCM. Public BuildConfig default is blank and the FOSS build has no field. | Excluded. | Clear unreadable ciphertext and request re-entry. | Settings Clear, save blank, clear app data, or uninstall. | Excluded, including generated wallpaper reports. |

## Guard

Run:

```powershell
py -3 tools\provider_credential_storage_check.py --policy docs\security\provider-credential-storage.json --repo-root .
```

The guard checks all six credential rows, blank release defaults, the encrypted
store implementation, both backup rule formats, recovery UI, diagnostics
status, redaction terms, legacy cleanup, and the absence of credential fields
from the library export source. Tests cover same-device reads, missing and
invalidated keys, invalid ciphertext, temporary failures, clear-all behavior,
and export/import rejection.
