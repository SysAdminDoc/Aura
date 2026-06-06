# Sound License Capabilities

Cycle 49 adds item-level sound action gates derived from existing `Sound`
metadata: `source`, `license`, `uploaderName`, `sourcePageUrl`, and
`sourceAvailability`.

## Runtime Decisions

| Sound source/license | Apply | Download | Share | Edit/trim | Aura Originals |
| --- | --- | --- | --- | --- | --- |
| Aura Picks with CC0/Public Domain metadata | Allowed | Allowed | Allowed with provenance | Allowed | Allowed after bundled curation metadata is present |
| CC BY / CC BY-SA | Allowed with attribution metadata | Allowed with attribution metadata | Allowed with source/uploader/license text | Allowed unless no-derivatives applies | Disabled until reviewed as CC0-compatible |
| CC BY-NC variants | Confirmation required | Confirmation required | Allowed with source/uploader/license text | Confirmation required unless no-derivatives applies | Disabled |
| No-derivatives variants | Allowed with attribution metadata | Allowed with attribution metadata | Allowed with source/uploader/license text | Disabled | Disabled |
| YouTube | Confirmation required | Confirmation required | Allowed as a source-link share | Disabled | Disabled |
| SoundCloud | Disabled until source permissions are reviewed | Disabled until source permissions are reviewed | Allowed as a source-link share | Disabled | Disabled |
| Community uploads with selected CC0/CC BY metadata | Allowed by selected license | Allowed by selected license | Allowed with stored provenance | Allowed by selected license | Disabled until reviewed |
| Community uploads with selected CC BY-NC metadata | Confirmation required | Confirmation required | Allowed with stored provenance | Confirmation required | Disabled until reviewed |
| Legacy community uploads without attested license metadata | Confirmation required | Confirmation required | Allowed with stored provenance | Confirmation required | Disabled until reviewed |
| Missing remote license metadata | Disabled | Disabled | Disabled | Disabled | Disabled |
| Source unavailable | Disabled | Disabled | Disabled | Disabled | Disabled |

## Persistence

- `FavoriteEntity` now preserves a nullable `license` field so saved sound
  favorites restore with the same license gate as the live search row.
- Room schema v16 adds the nullable favorite license column.
- Favorites export/import preserves the license field with the same length and
  HTTPS validation rules used by the rest of the favorite provenance payload.

## UI Behavior

- `SoundsViewModel.applySound()` and `downloadSound()` enforce the action gate
  before resolving remote streams or starting downloads.
- `SoundDetailScreen` disables unsupported trim/contact/save/share actions,
  prompts for confirmation-required actions, and shares sound provenance with
  name, creator, source, normalized license, and source link.
- `SoundsScreen` quick apply uses the same confirmation and disabled-state
  behavior as detail.
- `ContactPickerViewModel` enforces the apply gate before assigning a ringtone
  to a contact.

## Remaining Follow-Up

- Report and takedown flows should show the stored license/source context once
  the moderation queue is implemented.
- The standalone audio editor should keep enforcing the same policy if new
  entry points are added outside Sound Detail.
