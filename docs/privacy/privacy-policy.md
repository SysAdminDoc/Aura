# Aura Privacy Policy

Aura is an open-source Android personalization app for wallpapers, video
wallpapers, ringtones, sounds, and optional community uploads.

Aura has no ads, does not sell personal data, and does not use cross-app
tracking.

## Account Model

Aura does not require an account for local browsing, downloaded content, local
favorites, or local wallpaper/sound tools. Community features can use an
anonymous Firebase identity when a user uploads, votes, follows, reports,
blocks, or manages creator/community data.

## Data Stored On The Device

- App preferences such as theme, provider switches, scheduler settings, and
  API keys entered by the user.
- Favorites, downloads, search history, wallpaper cache metadata, offline
  favorite files, edited sounds, generated wallpapers, and diagnostics created
  by explicit user action.
- A local fallback community identity only when a local community identifier is
  needed.

User-entered provider API keys are stored in app-private Jetpack DataStore and
the preferences DataStore file is excluded from cloud backup and device
transfer. These keys are not protected by Android Keystore-backed encrypted
storage; users can clear each saved key in Settings with the key dialog's Clear
action or by saving a blank value.

## Community Data

When community features are used, Aura can store community sound/wallpaper
metadata, upload ownership indexes, vote markers, follows, block rows, reports,
creator profile rows, collection shares, and moderation/deletion evidence in
Firebase services.

Public community upload metadata and uploaded media can be visible to other
users. Private moderation, rights, safety, abuse-prevention, report, and
deletion evidence is retained for operator review and is not public.

## Account Deletion

Users with Aura installed can open `Settings` > `Community identity` to view a
redacted identity suffix and an `AURA-` deletion request code when a Firebase
identity exists. The same panel includes `Clear local`, which removes only the
local fallback community identity from the current device.

Support deletion handling is documented in
[`docs/support/community-account-deletion.md`](../support/community-account-deletion.md).
The hosted web deletion request URL is pending owner publication before Play
production submission. The field contract is tracked in
[`docs/support/community-account-deletion-web-intake.md`](../support/community-account-deletion-web-intake.md),
publishable page copy is tracked in
[`docs/support/community-account-deletion-web-page.md`](../support/community-account-deletion-web-page.md),
and publication status is tracked in
[`docs/support/community-account-deletion-web-url.json`](../support/community-account-deletion-web-url.json).
The URL manifest must move from `pendingOwnerUrl` to `published` only after an
HTTPS deletion request URL is live and referenced from this policy and the
support intake document.

Backend deletion requests are routed through private operator tooling. Public
uploads, Storage objects, Firebase Authentication deletion, local device cleanup,
and retained moderation evidence have separate steps so deletion does not leave
orphaned public data or remove private safety records out of sequence.

## Diagnostics

Aura does not use automatic crash analytics. Settings exposes a local crash
diagnostics bundle that a user can copy or share manually. The bundle is
sanitized before sharing and does not upload itself automatically.

## Third-Party Services

Aura can fetch content or metadata from configured providers such as Wallhaven,
Pexels, Pixabay, Reddit, YouTube tooling, Open-Meteo, Firebase, and optional
Stability image generation. Provider usage depends on enabled features,
provider switches, and user actions.

Generated wallpaper prompts are sent to Stability only after the user reviews
and accepts the in-app disclosure. The generated wallpaper privacy runbook is
tracked in [`docs/privacy/ai-generation.md`](ai-generation.md).

New saved generated wallpaper favorites use generic names and non-prompt tags;
Aura does not copy prompt words into favorite names or tags by default.
Removing a saved generated wallpaper also removes its app-private generated PNG
after the Undo window closes.

Generated wallpaper reports use Aura's private Firebase-backed report queue and
do not include Stability keys, other provider keys, local generated-image file
paths, or prompt text unless the user writes prompt text in the report note.

## Contact

Use the project support channel or issue tracker for questions. Do not publish
full Firebase UIDs, access tokens, private database exports, or raw deletion
request form exports in public issues.
