# Privacy Data Safety Matrix

This matrix is the release-reviewed source of truth for Aura manifest
permissions, network data surfaces, and local storage surfaces.
`tools/privacy_data_safety_check.py` compares this document's JSON contract
with `app/src/main/AndroidManifest.xml`, `docs/security/network-endpoints.json`,
and source-backed local storage rows so new privacy-relevant surfaces cannot
ship without reviewed store-disclosure coverage.

## Permission Ledger

| Permission | Purpose | Data safety row | Sharing | Release posture |
| --- | --- | --- | --- | --- |
| `android.permission.SET_WALLPAPER` | Apply selected images as home or lock-screen wallpaper. | User-provided photos or generated images; local only. | Not shared. | Core personalization feature. |
| `android.permission.WRITE_SETTINGS` | Set selected sounds as ringtones, notifications, or alarms. | Audio files; local only. | Not shared. | Sensitive special access for sound apply flows. |
| `com.android.alarm.permission.SET_ALARM` | Support alarm-tone actions for selected sounds. | Audio files; local only. | Not shared. | Alarm sound integration. |
| `android.permission.RECORD_AUDIO` | Record community sounds after an explicit user action. | Voice or sound recordings; user-generated content. | Shared only when uploaded. | Sensitive runtime permission. |
| `android.permission.INTERNET` | Fetch provider content, community data, generated wallpapers, and release-linked resources. | App interactions, user-generated content, generated wallpaper prompts depending on feature. | Shared with selected providers only for enabled features. | Network/provider disclosures required. |
| `android.permission.ACCESS_NETWORK_STATE` | Check connectivity for provider and worker operations. | Diagnostics; local only. | Not shared. | Normal network support. |
| `android.permission.WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | Support legacy downloads/exports on API 28 and below. | Photos/videos, audio files, files/docs; local only. | Not shared. | Legacy storage compatibility only. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Restore opted-in wallpaper rotation scheduling after reboot. | App interactions; local only. | Not shared. | Background scheduling disclosure required. |
| `android.permission.FOREGROUND_SERVICE` | Run user-visible media or rotation work. | App interactions; local only. | Not shared. | Foreground service declaration must match behavior. |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep audio/media playback controls alive. | App interactions and audio files; local only. | Not shared. | Media playback foreground service type. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Run opt-in screen-off/unlock rotation triggers. | App interactions; local only. | Not shared. | Special-use foreground service subtype is documented in the manifest. |
| `android.permission.POST_NOTIFICATIONS` | Show required foreground-service and worker notifications. | App interactions; local only. | Not shared. | Runtime notification permission. |
| `android.permission.ACCESS_COARSE_LOCATION` | Fetch approximate weather for optional weather wallpaper effects. | Approximate location. | Shared with Open-Meteo when weather is enabled. | Location disclosure required. |
| `android.permission.READ_CONTACTS` | Search contacts for per-contact ringtone assignment. | Contacts; local only. | Not shared. | Sensitive permission for targeted ringtone feature. |
| `android.permission.WRITE_CONTACTS` | Write the selected ringtone URI to the chosen contact. | Contacts and audio files; local only. | Not shared. | Sensitive permission for targeted ringtone feature. |

## Data Safety Answer Notes

- Approximate location is used only for optional weather effects and is shared
  with the weather provider when that feature is enabled.
- Contacts are used locally for per-contact ringtone assignment and are not
  uploaded by Aura.
- Microphone recordings are local until the user uploads a community sound.
- Community uploads, votes, follows, reports, creator profiles, and collection
  shares use Firebase when the user uses those community features.
- Generated wallpaper prompts are sent to Stability only after the user accepts
  the generated wallpaper disclosure and supplies a key.
- Crash diagnostics are local-only unless the user copies or shares a sanitized
  bundle.
- Aura has no ads, does not sell personal data, and does not use cross-app
  tracking.

## Network Surface Ledger

`tools/privacy_data_safety_check.py` also reconciles this matrix with
`docs/security/network-endpoints.json`. Every endpoint ID in that inventory
must have a matching `networkSurfaces` row in `docs/privacy/data-safety.json`.

| Endpoint | Data safety row | User control |
| --- | --- | --- |
| `wallhaven-api` | Search queries and provider response metadata shared with Wallhaven when enabled. | Wallhaven source switch and optional key controls. |
| `bing-daily` | Locale/market parameters and provider response metadata shared with Bing when enabled. | Bing source switch and wallpaper source selection. |
| `pexels-api` | Search queries and provider response metadata shared with Pexels when enabled. | Pexels source switch and optional key controls. |
| `pixabay-api` | Search queries and provider response metadata shared with Pixabay when enabled. | Pixabay source switch and optional key controls. |
| `reddit-json` | Subreddit names, search queries, and provider response metadata shared with Reddit when enabled. | Reddit source switch and subreddit settings. |
| `openverse-audio` | Search queries and provider response metadata shared with Openverse during sound browsing. | Sound browsing controls and local cache cleanup. |
| `freesound-v2` | Search queries and provider response metadata shared with Freesound during sound browsing. | Freesound key controls and local sound cleanup. |
| `soundcloud-api` | Search queries and provider response metadata shared with SoundCloud during sound browsing. | SoundCloud client ID controls and local sound cleanup. |
| `audius-api` | Search queries and provider response metadata shared with Audius during sound browsing. | Sound browsing controls and local cache cleanup. |
| `ccmixter-api` | Search queries and provider response metadata shared with ccMixter during sound browsing. | Sound browsing controls and local cache cleanup. |
| `open-meteo-api` | Approximate location shared with Open-Meteo only when weather effects are enabled. | Weather effects toggle and Android location permission. |
| `stability-api` | Prompt, key-authenticated request metadata, and generated output handled through Stability when the user starts generation. | Generated wallpaper switch, disclosure, and Stability key controls. |
| `youtube-newpipe` | Video IDs and provider response metadata shared with YouTube tooling during enabled YouTube flows. | YouTube source switch and sound query settings. |
| `aura-collection-links` | No app-initiated network collection; links are import locators. | User chooses whether to import a collection link. |
| `firebase-community` | Firebase UID, community uploads, votes, follows, reports, blocks, profile edits, storage uploads, and callable payloads. | Community source switch, upload/report/delete flows, and support deletion tools. |

## Local Storage Ledger

`localStorageSurfaces` rows in `docs/privacy/data-safety.json` must cite
existing source files, name the user data classes stored locally, and state
whether current backup/transfer rules exclude, include, or treat the files as
transient cache.

| Surface | Local data | User control | Backup posture |
| --- | --- | --- | --- |
| `preferences-datastore` | Provider keys, app settings, generated-content disclosure, provider switches, and scheduler preferences. | Settings key Clear actions, feature switches, generated disclosure reset, or clear app data. | Excluded from cloud backup and device transfer. |
| `room-database` | Favorites, downloads, search history, wallpaper cache metadata, wallpaper history, and collections. | Remove rows through app surfaces, clear caches/history, export/import replacements, or clear app data. | Included by current backup/transfer rules; Room sidecars are excluded. |
| `community-identity-and-vote-prefs` | Local fallback identity and local vote-hide state. | Settings Community identity Clear local action for fallback identity; app data clear for vote-hide state. | Included by current backup/transfer rules. |
| `widget-and-selection-prefs` | Widget tint colors and selected wallpaper/sound navigation snapshots. | Change selected content, remove widgets, clear history where exposed, or clear app data. | Included by current backup/transfer rules. |
| `live-wallpaper-prefs-and-media` | Live wallpaper media, parallax images, weather coordinates/state, video settings, and video battery diagnostics. | Replace/disable live wallpaper, clear weather state, clear app data, or uninstall. | Mixed: weather/live-wallpaper prefs are excluded; managed media and runtime stats are not fully excluded today. |
| `crash-diagnostics-log` | Local crash log tail, app/device diagnostics, and source context. | Diagnostics bundle requires explicit Copy or Share and redacts app-private paths and provider credentials. | Excluded from cloud backup and device transfer. |
| `coil-and-audio-caches` | Provider image/audio previews, temporary trims, and editor cache files. | Settings cache cleanup, Android clear cache, Android clear app data, or uninstall. | Transient cache. |
| `offline-favorite-files` | Offline favorite image/audio files plus linked favorite metadata. | Remove the favorite/offline copy, clear offline favorites, clear app data, or uninstall. | Included by current backup/transfer rules. |
| `generated-wallpaper-files` | Generated wallpaper PNG files and local generated-output metadata. | Generated disclosure/source controls, generated favorite removal cleanup, clear app data, or uninstall. | Included by current backup/transfer rules. |
| `community-recording-temp-files` | Temporary microphone recordings before community upload. | Cancel/discard recording, upload with rights attestation, clear cache/app data, or uninstall. | Transient cache. |
| `share-out-temp-files` | Temporary collection export JSON and share artifacts. | User chooses share recipient; clear cache/app data or uninstall removes app-side temp files. | Transient cache. |
| `aura-originals-files` | Downloaded Aura Originals bundled audio files. | Wi-Fi worker constraints and clear app data/uninstall for local cleanup. | Included by current backup/transfer rules. |

## Release Checklist

- Run `python3 tools/privacy_data_safety_check.py --policy docs/privacy/data-safety.json --repo-root .`.
- Re-review this matrix whenever `AndroidManifest.xml`, provider SDKs,
  community data paths, diagnostics, generated wallpaper behavior, or Fastlane
  metadata changes.
- Keep `docs/privacy/privacy-policy.md`, Play Data safety answers, and
  Fastlane full description consistent with this matrix.
- Keep `docs/security/network-endpoints.json` and the `networkSurfaces` rows in
  `docs/privacy/data-safety.json` in lockstep.
- Keep app-private source paths and the `localStorageSurfaces` rows in
  `docs/privacy/data-safety.json` in lockstep with backup/transfer rules.

## Sources

- Google Play User Data policy:
  https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play Data safety form guidance:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play permissions and APIs that access sensitive information:
  https://support.google.com/googleplay/android-developer/answer/9888170
- Android permissions overview:
  https://developer.android.com/guide/topics/permissions/overview
