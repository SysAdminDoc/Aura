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

## Generated Content Reports

Generated wallpaper results and saved generated wallpaper favorites expose a
Report action. Report categories are Offensive, Unsafe, Deceptive, and Other.

Reports use Aura's private Firebase-backed report queue. A generated-content
report includes the generated wallpaper content ID, content type, source,
selected reason, optional user note, and a generic generated-wallpaper label.
Reports do not include the user's Stability key, provider keys for other
services, prompt text unless the user writes it in the note, or local file
paths for generated images.

Report records are private to admins and retained for moderation and release
review. Admins can dismiss, hide, or restore reports through the existing
Community reports review queue. Generated-content reports do not create public
community upload metadata and do not expose reporter identity publicly.

## Release Checklist

- Confirm the generated wallpaper disclosure appears before the first request
  on a fresh install.
- Confirm canceling the disclosure does not call Stability.
- Confirm Settings can review and reset the disclosure acceptance.
- Confirm generated wallpaper results and saved generated wallpaper favorites
  expose the generated-content report action.
- Confirm generated-content reports cover Offensive, Unsafe, Deceptive, and
  Other reasons.
- Confirm diagnostics and source metrics still redact provider keys and request
  details before sharing.
- Confirm generated-content reports omit Stability keys, local file paths, and
  prompt text unless the user enters prompt text in the report note.
