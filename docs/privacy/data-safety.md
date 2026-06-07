# Privacy Data Safety Matrix

This matrix is the release-reviewed source of truth for Aura manifest
permissions. `tools/privacy_data_safety_check.py` compares this document's JSON
contract with `app/src/main/AndroidManifest.xml` so new permissions cannot ship
without a reviewed privacy and store-disclosure row.

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

## Release Checklist

- Run `python3 tools/privacy_data_safety_check.py --policy docs/privacy/data-safety.json --repo-root .`.
- Re-review this matrix whenever `AndroidManifest.xml`, provider SDKs,
  community data paths, diagnostics, generated wallpaper behavior, or Fastlane
  metadata changes.
- Keep `docs/privacy/privacy-policy.md`, Play Data safety answers, and
  Fastlane full description consistent with this matrix.
- Keep `docs/security/network-endpoints.json` and the `networkSurfaces` rows in
  `docs/privacy/data-safety.json` in lockstep.

## Sources

- Google Play User Data policy:
  https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play Data safety form guidance:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play permissions and APIs that access sensitive information:
  https://support.google.com/googleplay/android-developer/answer/9888170
- Android permissions overview:
  https://developer.android.com/guide/topics/permissions/overview
