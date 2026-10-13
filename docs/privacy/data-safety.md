# Privacy Data Safety Matrix

This matrix is the release-reviewed source of truth for Aura manifest
permissions, network data surfaces, local storage surfaces, and SDK data
surfaces.
`tools/privacy_data_safety_check.py` compares this document's JSON contract
with `app/src/main/AndroidManifest.xml`, `docs/security/network-endpoints.json`,
Gradle dependency files, both Android backup rule formats, and source-backed
local storage and SDK rows. Every excluded path must appear in the Android 11,
Android 12+ cloud, and device-transfer rules and in one local storage row.

## Permission Ledger

| Permission | Purpose | Data safety row | Sharing | Release posture |
| --- | --- | --- | --- | --- |
| `android.permission.SET_WALLPAPER` | Apply selected images as home or lock-screen wallpaper. | User-provided photos or generated images; local only. | Not shared. | Core personalization feature. |
| `android.permission.WRITE_SETTINGS` | Set selected sounds as ringtones, notifications, or alarms. | Audio files; local only. | Not shared. | Sensitive special access for sound apply flows. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Restore previously applied ringtone, notification, and alarm sound assignments after device boot. | Audio files and app interactions; local only. | Not shared. | Reviewed boot receiver does not start wallpaper rotation triggers. |
| `com.android.alarm.permission.SET_ALARM` | Support alarm-tone actions for selected sounds. | Audio files; local only. | Not shared. | Alarm sound integration. |
| `android.permission.RECORD_AUDIO` | Record community sounds after an explicit user action. | Voice or sound recordings; user-generated content. | Shared only when uploaded. | Sensitive runtime permission. |
| `android.permission.INTERNET` | Fetch provider content, community data, generated wallpapers, and release-linked resources. | App interactions, user-generated content, generated wallpaper prompts depending on feature. | Shared with selected providers only for enabled features. | Network/provider disclosures required. |
| `android.permission.ACCESS_NETWORK_STATE` | Check connectivity for provider and worker operations. | Diagnostics; local only. | Not shared. | Normal network support. |
| `android.permission.WRITE_EXTERNAL_STORAGE` (`maxSdkVersion=28`) | Support legacy downloads/exports on API 28 and below. | Photos/videos, audio files, files/docs; local only. | Not shared. | Legacy storage compatibility only. |
| `android.permission.FOREGROUND_SERVICE` | Run user-visible media or rotation work. | App interactions; local only. | Not shared. | Foreground service declaration must match behavior. |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep audio/media playback controls alive. | App interactions and audio files; local only. | Not shared. | Media playback foreground service type. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Run opt-in screen-off/unlock rotation triggers. | App interactions; local only. | Not shared. | Special-use foreground service subtype is documented in the manifest. |
| `android.permission.POST_NOTIFICATIONS` | Show required foreground-service and worker notifications. | App interactions; local only. | Not shared. | Runtime notification permission. |
| `android.permission.ACCESS_COARSE_LOCATION` | Fetch approximate weather for optional weather wallpaper effects. | Approximate location. | Shared with Open-Meteo when weather is enabled. | Location disclosure required. |
| `android.permission.WRITE_CONTACTS` | Write the selected ringtone URI to the contact selected through Android's contact picker, or to the contact a library backup names when the user restores it. | Contacts and audio files; local only. | Not shared. | Sensitive permission requested only when applying a per-contact ringtone or restoring contact ringtones from a library backup. |
| `android.permission.ACCESS_NOTIFICATION_POLICY` | Inspect Do Not Disturb priority-caller rules for per-contact ringtone guidance. | App interactions and notification policy state; local only. | Not shared. | Special access used only to explain DND behavior; Aura does not change the policy silently. |

## Data Safety Answer Notes

- Approximate location is used only for optional weather effects and is shared
  with the weather provider when that feature is enabled.
- Contacts are selected through Android's contact picker for per-contact
  ringtone assignment and are not uploaded by Aura. DND policy state is read
  locally only when Aura needs to explain whether that ringtone can ring.
- Aura keeps the name and lookup key of each contact whose ringtone it set,
  with the sound's identity, in its preferences on the device. An explicit
  library backup writes that record to a file the user saves. Aura does not
  upload the file. It has no `READ_CONTACTS` permission, so a restore asks
  Android to update the one contact that matches a saved lookup key or exact
  name and never reads the contact list.
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
| `reddit-rss` | Configured subreddit names and Atom response metadata shared with Reddit when enabled; search filtering stays on-device. | Reddit source switch and local cache cleanup. |
| `nasa-apod` | Date parameters, APOD metadata, and provider key parameter shared with NASA when enabled. | Wallpaper source/provider controls and local cache cleanup. |
| `wikimedia-potd` | Date parameters and provider response metadata shared with Wikimedia when enabled. | Wallpaper source/provider controls and local cache cleanup. |
| `lemmy-wallpapers` | Community names, post metadata, and source URLs shared with Lemmy when enabled. | Wallpaper source/provider controls and local cache cleanup. |
| `open-meteo-api` | Approximate location shared with Open-Meteo only when weather effects are enabled. | Weather effects toggle and Android location permission. |
| `generated-wallpaper-api` | Prompt, key-authenticated request metadata, and generated output handled through Stability when the user starts generation. | Generated wallpaper switch, disclosure, and Stability key controls. |
| `tiktok-creator-embed` | Creator handles, video IDs, and embed metadata shared with TikTok when the Ringtones tab loads in GitHub and Obtainium builds. | Ringtones tab use, cache cleanup, and favorites removal. |
| `tiktok-media-cdn` | Clip requests shared with TikTok's CDN for duration labels, previews, and sounds the user applies or downloads. | Cache cleanup; only the extracted sound track is kept. |
| `youtube-newpipe` | Video IDs and provider response metadata shared with YouTube tooling during enabled YouTube flows. | YouTube source switch and sound query settings. |
| `youtube-pot-provider` | YouTube attestation challenge, video-bound token context, and response metadata shared only with the HTTPS provider selected by the user. | PO-token provider URL, YouTube source switch, and clear-app-data controls. |
| `aura-collection-links` | No app-initiated network collection; links are import locators. | User chooses whether to import a collection link. |
| `aura-update-check` | Aura's version in the User-Agent of one request to GitHub for the latest release, only when the user turns the update check on. | Check for updates switch, off by default. |
| `firebase-community` | Firebase UID, community uploads, votes, follows, reports, blocks, profile edits, storage uploads, and callable payloads. | Community source switch, upload/report/delete flows, and support deletion tools. |

## Local Storage Ledger

`localStorageSurfaces` rows in `docs/privacy/data-safety.json` must cite
existing source files, name the user data classes stored locally, and state
whether current backup/transfer rules exclude, include, or treat the files as
transient cache.

| Surface | Local data | User control | Backup posture |
| --- | --- | --- | --- |
| `preferences-datastore` | App settings, Fit Canvas defaults, generated-content disclosure, provider switches, scheduler preferences, legacy provider-key migration inputs, and the name, lookup key, and sound identity for each contact ringtone set through Aura. | Feature switches, wallpaper preview controls, generated disclosure reset, explicit library export/import, or clear app data. | Excluded from cloud backup and device transfer. Non-secret Fit Canvas defaults and the contact ringtone record can be copied through an explicit portable library backup. |
| `provider-credential-store` | User-entered provider API keys in Android Keystore-backed encrypted SharedPreferences. Unreadable restored ciphertext is cleared and replaced by a non-secret re-entry marker. | Settings API key Clear actions, saving blank, re-entry after key loss, or clear app data. | Excluded from cloud backup and device transfer. |
| `room-database` | Favorites, downloads, search history, wallpaper cache metadata, wallpaper history, collections, local wallpaper identity and health metadata, and rotation exclusions. | Remove rows through app surfaces, relink unavailable local media, restore rotation items, clear caches/history, export/import replacements, or clear app data. | Database and sidecars are excluded from cloud backup and device transfer. Portable library backup can copy locator-free local metadata and rotation exclusions without media bytes or raw local paths. |
| `community-identity-and-vote-prefs` | Local fallback identity and local vote-hide state. | Settings Community identity Clear local action for fallback identity; app data clear for vote-hide state. | Excluded from cloud backup and device transfer. |
| `widget-and-selection-prefs` | Widget tint colors and selected wallpaper/sound navigation snapshots. | Change selected content, remove widgets, clear history where exposed, or clear app data. | Excluded from cloud backup and device transfer. |
| `live-wallpaper-prefs-and-media` | Live wallpaper media, Fit Canvas background and apply copies, parallax images, weather coordinates/state, video settings, and video battery diagnostics. | Replace/disable live wallpaper, change Fill or Fit, clear weather state, clear app data, or uninstall. | Managed media, generated Fit Canvas files, and declared live wallpaper preferences are excluded from cloud backup and device transfer. |
| `crash-diagnostics-log` | Local crash log tail, app/device diagnostics, and source context. | Diagnostics bundle requires explicit Copy or Share and redacts app-private paths and provider credentials. | Excluded from cloud backup and device transfer. |
| `background-work-receipts` | Bounded scheduler status, result, timing, and failure-class fields for registered workers. | Settings can display or share sanitized receipt summaries; clear app data removes them. | Excluded from cloud backup and device transfer. |
| `coil-and-audio-caches` | Provider image/audio previews, temporary trims, and editor cache files. | Settings cache cleanup, Android clear cache, Android clear app data, or uninstall. | Transient cache. |
| `offline-favorite-files` | Offline favorite image/audio files plus linked favorite metadata. | Remove the favorite/offline copy, clear offline favorites, clear app data, or uninstall. | Excluded from cloud backup and device transfer. |
| `generated-wallpaper-files` | Generated wallpaper PNG files and local generated-output metadata. | Generated disclosure/source controls, generated favorite removal cleanup, clear app data, or uninstall. | Excluded from cloud backup and device transfer. |
| `optimized-apply-copy-files` | Device-sized wallpaper, video, and sound working copies plus technical metadata. | Delete only the optimized copy in Downloads, delete the complete download, clear app data, or uninstall. | Excluded from cloud backup and device transfer. |
| `saved-video-original-files` | Source-quality video wallpaper and animated image bytes with provider provenance and technical metadata. | Open or delete the saved video in Downloads, clear app data, or uninstall. | Excluded from cloud backup and device transfer. |
| `community-recording-temp-files` | Temporary microphone recordings before community upload. | Cancel/discard recording, upload with rights attestation, clear cache/app data, or uninstall. | Transient cache. |
| `share-out-temp-files` | Temporary collection export JSON and share artifacts. | User chooses share recipient; clear cache/app data or uninstall removes app-side temp files. | Transient cache. |
| `aura-originals-files` | Downloaded Aura Originals bundled audio files. | Wi-Fi worker constraints and clear app data/uninstall for local cleanup. | Excluded from cloud backup and device transfer. |

## SDK Surface Ledger

`sdkSurfaces` rows in `docs/privacy/data-safety.json` must cite current Gradle
dependency markers and existing source paths. These rows cover SDK-backed
collection, sharing, attestation, and local-processing surfaces that may not
map one-to-one to a manifest permission or reviewed network endpoint.

| Surface | Data safety row | User control | Release posture |
| --- | --- | --- | --- |
| `firebase-auth-sdk` | Firebase UID, anonymous authentication state, and deletion request code for community identity. | Community source switch, account deletion request tools, and local fallback identity clear control. | Disclose anonymous Firebase identity when community features are enabled. |
| `firebase-realtime-database-sdk` | Community votes, reports, blocks, creator profiles, collection shares, and related Firebase UID metadata. | Community source switch, explicit community actions, owner delete flows, and account deletion request tools. | Disclose community account, app interaction, report, and public profile/share data. |
| `firebase-storage-sdk` | Community sound/image uploads, storage upload handles, and report attachments. | Explicit upload/report actions, owner delete flows, and account deletion request tools. | Disclose user-generated media handled by Firebase Storage. |
| `firebase-functions-sdk` | Callable payloads, operation IDs, quota/dedupe metadata, and community write metadata. | Community source switch plus report, vote, follow, block, upload, and profile actions. | Disclose callable-backed community writes and quota metadata. |
| `firebase-app-check-sdk` | App integrity tokens, app/device attestation signals, and debug App Check tokens outside release artifacts. | Community features can be disabled; debug tokens remain developer-only. | Disclose service-provider abuse-prevention processing for Firebase-backed community features. |
| `play-services-mlkit-subject-segmentation-sdk` | User-selected images processed locally, segmentation masks, and Play services module-install request metadata. | User-selected smart crop/parallax actions with normal-crop fallback. | Disclose Play services ML Kit/module-install dependency; Aura does not upload image bytes for subject segmentation. |

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
- Keep Gradle dependency markers, SDK source paths, and the `sdkSurfaces` rows
  in `docs/privacy/data-safety.json` in lockstep.

## Sources

- Google Play User Data policy:
  https://support.google.com/googleplay/android-developer/answer/10144311
- Google Play Data safety form guidance:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play permissions and APIs that access sensitive information:
  https://support.google.com/googleplay/android-developer/answer/9888170
- Android permissions overview:
  https://developer.android.com/guide/topics/permissions/overview
