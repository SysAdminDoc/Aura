# Generated Wallpaper Privacy

Aura's generated wallpaper flow is optional and disabled when the generated
wallpapers source switch is off.

## User Disclosure

Before the first Stability request on a device, Aura shows a generated
wallpaper disclosure. The disclosure states that:

- The user's prompt is sent to Stability to generate an image.
- The request uses the user's Stability key and may spend provider credits.
- Generated images are stored locally in Aura for preview, save, apply, and
  delete workflows.
- Users should not include private, identifying, or unsafe content in prompts.

Users can review or reset the disclosure acceptance in Settings > API Keys >
Generated wallpaper disclosure.

## Data Sent

When the user taps Generate after accepting the disclosure, Aura sends the
prompt, selected style preset, aspect ratio, output format, and Stability API
key to the Stability image generation endpoint.

Aura does not send local favorites, downloads, Firebase identity values,
provider keys for other services, crash diagnostics, or community account
deletion data with the generation request.

## Local Storage

Generated images are written to app-private local storage under the generated
wallpaper cache. Users can save generated results to Favorites, apply them as
wallpapers, or delete app data through Android system settings. Aura prunes the
generated wallpaper cache to the most recent local outputs.

## Release Checklist

- Confirm the generated wallpaper disclosure appears before the first request
  on a fresh install.
- Confirm canceling the disclosure does not call Stability.
- Confirm Settings can review and reset the disclosure acceptance.
- Confirm diagnostics and source metrics still redact provider keys and request
  details before sharing.
- Confirm generated-content reporting remains tracked separately until the
  in-app generated-content report path ships.
