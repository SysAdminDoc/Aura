# Store assets

This packet defines the screenshot and feature-graphic capture pipeline for
Aura's Fastlane metadata. The machine-readable contract is
[`store-assets.json`](store-assets.json).

## Current status

| Field | Value |
| --- | --- |
| Package | `com.freevibe` |
| Status | `capturePending` |
| Metadata root | `fastlane/metadata/android/en-US` |
| Asset preflight | `tools/store_metadata_preflight.py --repo-root . --require-assets --min-phone-screenshots 4` |

The asset-mode preflight is already implemented, but it is not yet enabled in
verify/release because the repo still lacks reviewed current screenshots and a
feature graphic. This packet keeps the capture plan checked until those image
files are committed and `--require-assets` can become mandatory.

## Required paths

| Asset | Path | Requirement |
| --- | --- | --- |
| Icon | `fastlane/metadata/android/en-US/images/icon.png` | 512x512 PNG |
| Feature graphic | `fastlane/metadata/android/en-US/images/featureGraphic.png` | 1024x500 JPEG or 24-bit PNG without alpha |
| Phone screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` | At least four current phone screenshots |

Phone screenshots must be actual in-app UI, use JPEG or 24-bit PNG without
alpha, have a minimum short side of 1080 px, use portrait 9:16 composition, and
avoid device frames, Play badges, third-party trademarks, stale branding,
ratings/ranking claims, price/sale language, and call-to-action wording.

## Planned shots

| ID | Target | Coverage | Alt text |
| --- | --- | --- | --- |
| `wallpapers` | `phoneScreenshots/01-wallpapers.png` | Wallpaper browsing grid with source-aware controls. | Aura wallpaper browsing grid with source-aware controls. |
| `video-wallpapers` | `phoneScreenshots/02-video-wallpapers.png` | Video wallpaper library, crop/apply context, or recovery state without third-party branding. | Aura video wallpaper library and editing flow. |
| `sounds-editor` | `phoneScreenshots/03-sounds-editor.png` | Sound discovery or editor controls with license/source context visible. | Aura sounds screen with editing and source controls. |
| `settings-favorites-community` | `phoneScreenshots/04-settings-favorites-community.png` | Privacy/provider/community controls, saved local content, or community moderation affordances. | Aura settings and saved content controls. |

Each alt text value must be 140 characters or less and must identify the
important UI state without starting with "photo of" or "image of".

## Release gate

Verify and release workflows must run:

- `tools/store_asset_pipeline_check.py --policy docs/distribution/store-assets.json --repo-root .`

When current screenshots and a feature graphic are committed, update this
packet to `ready`, enable:

```bash
python3 tools/store_metadata_preflight.py --repo-root . --require-assets --min-phone-screenshots 4
```

and remove the `capturePending` exception from the release checklist.

## Sources

- Google Play preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- F-Droid descriptions, graphics, and screenshots: https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/
- fastlane supply metadata docs: https://docs.fastlane.tools/actions/supply/
