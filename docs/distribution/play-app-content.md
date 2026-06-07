# Play App Content Declaration Packet

This packet is the owner-ready source for Play Console > Policy > App content.
It records the answers Aura should use for app review and the owner actions
that must be completed before a Play production submission.

Machine-readable contract:
[`play-app-content.json`](play-app-content.json).

## Owner Summary

| Field | Answer |
| --- | --- |
| Package | `com.freevibe` |
| App | Aura |
| Status | `ownerActionRequired` |
| Primary category | Personalization |
| Distribution today | GitHub Releases and Obtainium; Play submission is not yet live. |
| Privacy policy | `https://github.com/SysAdminDoc/Aura/blob/main/docs/privacy/privacy-policy.md` |

Do not submit Aura to Play production until these owner actions are complete:

- `publish-hosted-deletion-url`: publish the hosted HTTPS account deletion
  request URL, move `docs/support/community-account-deletion-web-url.json` to
  `published`, and link the URL from the privacy/support docs.
- `confirm-ugc-guidelines-consent`: confirm or implement clear in-app community
  terms/guidelines consent before users engage with UGC.
- `complete-live-content-rating-questionnaire`: complete Play Console's live
  content rating questionnaire using this packet.
- `capture-play-console-app-content-receipt`: archive owner-visible App content
  answers or screenshots after submission.

## Privacy Policy

Answer: use the public privacy policy URL above.

Evidence:

- `docs/privacy/privacy-policy.md`
- `fastlane/metadata/android/en-US/full_description.txt`
- `docs/privacy/privacy-policy-link.json`

## Ads

Answer: No, Aura does not contain ads.

Aura has no ad SDKs, display ads, native ads, subscriptions, or in-app
purchases. Some sources accept optional user-supplied provider keys, but those
are not ads or purchases sold by Aura.

Evidence:

- `docs/privacy/privacy-policy.md`
- `fastlane/metadata/android/en-US/full_description.txt`
- `tools/store_metadata_preflight.py`

## App Access

Answer: no restricted reviewer credentials are required for the main app.

Reviewer instructions:

- Install and open the app normally.
- Browsing, local editing, wallpapers, sounds, widgets, local imports, and most
  settings do not require login.
- Community actions use anonymous Firebase identity when enabled.
- Optional provider keys and Stability generation can be skipped unless the
  owner supplies test credentials for those provider-specific flows.
- App Check enforcement evidence is not complete, so owner live evidence must
  be attached before production community enforcement claims.

Evidence:

- `docs/privacy/privacy-policy.md`
- `docs/community-app-check-rollout.md`
- `docs/community-backend-runbook.md`

## Target Audience

Answer: target `18+`; Aura is not designed for children.

Rationale:

- Aura displays remote provider media, public community uploads, and online
  content whose maturity can vary by source.
- Aura includes generated wallpaper prompts, optional YouTube tooling, optional
  provider keys, and user-generated community uploads.
- The store listing and UI should avoid child-directed marketing elements.

Evidence:

- `fastlane/metadata/android/en-US/full_description.txt`
- `docs/distribution/channel-strategy.md`
- `docs/privacy/data-safety.md`

## Content Rating

Use the live Play questionnaire, but these notes are the current answer packet.

| Form area | Aura answer |
| --- | --- |
| App category | Personalization utility for wallpapers, live wallpapers, sounds, ringtones, widgets, and local editing tools. |
| User-generated content | Yes. Community sounds, wallpapers, creator profiles, reports, votes, follows, blocks, and collection shares exist. |
| Online content | Yes. Aura displays remote provider content and public community catalog content from configured sources. |
| Location | Approximate location is requested only for optional weather wallpaper effects and shared with Open-Meteo when enabled. |
| Contacts | Contacts are accessed locally only for per-contact ringtone assignment. |
| Microphone | Microphone recording is used only when the user explicitly records a community sound. |
| Generated content | Generated wallpapers are text-to-image requests sent to Stability only after disclosure acceptance and user action. |
| Ads and purchases | No ads, subscriptions, or in-app purchases. |
| Gambling, real-money games, financial products | No. |
| Health, medical, news, government, COVID-19 | No. |

Evidence:

- `app/src/main/AndroidManifest.xml`
- `docs/privacy/data-safety.md`
- `docs/support/community-reporting.md`
- `docs/privacy/ai-generation.md`

## Data Safety

Answer from the checked matrix, not by retyping rows into this file.

Evidence:

- `docs/privacy/data-safety.json`
- `docs/privacy/data-safety.md`
- `tools/privacy_data_safety_check.py`

Current checked coverage:

- 15 manifest permissions.
- 15 reviewed network endpoint rows.
- 12 source-backed local storage rows.
- 6 Gradle-marker-backed SDK rows.

## User-Generated Content

Answer: Aura has UGC and must be submitted as a UGC app if community features
are enabled for the Play build.

Current implemented and documented controls:

- Community upload rights/license attestation.
- Private report queue for community/provider content.
- Generated-content report reasons for generated wallpapers.
- Private block-list and visible block/unblock entry points.
- Admin moderation queue, hide/restore, rights takedown receipts, and delete
  receipt paths.
- Owner delete paths for eligible community uploads with current storage
  handles.
- Account deletion support docs and private cleanup tooling.

Open owner action:

- `confirm-ugc-guidelines-consent`: Play UGC guidance calls for clear terms or
  guidelines before users engage with UGC. Aura has rights attestation and
  reporting/blocking flows, but Play production submission needs owner
  confirmation that terms/guidelines consent is complete in app, or a follow-up
  implementation that adds it.

Evidence:

- `docs/legal/community-upload-rights.md`
- `docs/support/community-reporting.md`
- `docs/community-block-user-policy.md`
- `docs/community-upload-deletion.md`
- `docs/community-account-deletion-policy.md`

## Generated Content

Answer: Aura has generated wallpaper content.

Current implemented and documented controls:

- Generated wallpapers are optional and disabled when the generated wallpaper
  source switch is off.
- Stability requests are sent only after disclosure acceptance and explicit user
  action.
- Prompts should not contain private, identifying, or unsafe content.
- Generated results and saved generated wallpaper favorites expose a Report
  action.
- Report categories are Offensive, Unsafe, Deceptive, and Other.
- Reports omit Stability keys, local generated-image file paths, and prompt
  text unless the user writes prompt text in the report note.

Evidence:

- `docs/privacy/ai-generation.md`
- `docs/support/community-reporting.md`
- `docs/privacy/data-safety.md`

## Sensitive Permissions

| Permission | Play-facing justification |
| --- | --- |
| `android.permission.WRITE_SETTINGS` | User-granted special access to set selected sounds as ringtone, notification, or alarm tones. |
| `android.permission.RECORD_AUDIO` | User-started community sound recording only. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Opt-in wallpaper rotation triggers for screen-off and unlock. |
| `android.permission.POST_NOTIFICATIONS` | Foreground service and worker notifications when Android requires visible status. |
| `android.permission.ACCESS_COARSE_LOCATION` | Optional weather wallpaper effects. |
| `android.permission.READ_CONTACTS` | Local per-contact ringtone picker. |
| `android.permission.WRITE_CONTACTS` | Local write of selected ringtone URI to a chosen contact. |
| `android.permission.WRITE_EXTERNAL_STORAGE` | Legacy API 28-and-below user-directed downloads and exports. |

Use `docs/privacy/data-safety.md` for the complete permission ledger, denial
behavior, retention, deletion path, and Data safety answer.

## Owner Actions

| ID | Status | Required evidence |
| --- | --- | --- |
| `publish-hosted-deletion-url` | `requiredBeforePlayProduction` | Published HTTPS URL linked from privacy/support docs and URL manifest set to `published`. |
| `confirm-ugc-guidelines-consent` | `requiredBeforePlayProduction` | In-app terms/guidelines consent is confirmed or implemented before community UGC engagement. |
| `complete-live-content-rating-questionnaire` | `ownerConfirmationRequired` | Owner confirms Play Console content rating answers and resulting rating. |
| `capture-play-console-app-content-receipt` | `ownerConfirmationRequired` | Owner archives App content answers or screenshots for release evidence. |

## Release Checklist

- Run `python3 tools/play_app_content_packet_check.py --policy docs/distribution/play-app-content.json --repo-root .`.
- Run `python3 tools/privacy_data_safety_check.py --policy docs/privacy/data-safety.json --repo-root .`.
- Confirm the privacy policy URL is live and appears in the store listing,
  in-app Settings, README, Fastlane metadata, release workflow, and release
  docs.
- Confirm every owner action in this packet is either complete or intentionally
  marked as blocking Play production.
- Re-run this packet when permissions, provider SDKs, UGC flows, generated
  wallpaper behavior, store metadata, target audience, or App content answers
  change.

## Sources

- Google Play App content overview:
  https://support.google.com/googleplay/android-developer/answer/9859455
- Google Play target audience and app content:
  https://support.google.com/googleplay/android-developer/answer/9867159
- Google Play Data safety form guidance:
  https://support.google.com/googleplay/android-developer/answer/10787469
- Google Play moderation requirements for UGC apps:
  https://support.google.com/googleplay/android-developer/answer/12923286
- Google Play AI-generated content policy help:
  https://support.google.com/googleplay/android-developer/answer/14094294
