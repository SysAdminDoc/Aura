# Generated Wallpaper Privacy

Aura's generated wallpaper flow is optional and disabled when the generated
wallpapers source switch is off.

## User Disclosure

Before the first Stability request on a device, Aura shows a generated
wallpaper disclosure. The disclosure states that:

- The user's prompt is sent to Stability to generate an image.
- The request uses the user's Stability key and may spend provider credits.
- Provider pricing and rate limits can change, so repeated generations should
  be checked against the user's Stability account.
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

## Credits, Rate Limits, and Duplicate Requests

Aura treats generated wallpaper requests as potentially billable provider
actions:

- The Generate button is disabled while a request is in progress, and the
  ViewModel rejects a second direct request while the first coroutine is active.
- The screen shows a local session request count. This count is device-local and
  resets with the app process; it is not a provider billing balance.
- If the same prompt and style already produced a wallpaper in the current
  session, Aura asks for confirmation before sending another Stability request.
- Out-of-credit and rate-limit errors point users to the Stability account page
  and a cooldown rather than retrying automatically.

## Local Storage

Generated images are written to app-private local storage under the generated
wallpaper cache. Users can save generated results to Favorites or apply them as
wallpapers. New generated favorites use a generic `Generated wallpaper` name and
store only non-prompt tags such as `ai-generated` and the selected style preset.
Prompt words are not copied into favorite names or tags.

Removing a saved generated wallpaper from Favorites also removes its app-private
generated PNG after the Undo window closes. Aura still prunes the generated
wallpaper cache to the most recent local outputs, and users can delete all app
data through Android system settings.

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
- Confirm rapid taps or direct ViewModel calls create only one active request.
- Confirm repeating the same prompt and style after a successful generation asks
  for confirmation before another request.
- Confirm 402 and 429 Stability responses include provider account/cooldown
  actions.
- Confirm generated favorites do not store prompt text in name or tags.
- Confirm removing a saved generated wallpaper deletes its generated PNG after
  the Undo window closes.
