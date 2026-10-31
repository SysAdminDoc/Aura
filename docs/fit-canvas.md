# Fit Canvas

Fit keeps the complete wallpaper visible. Aura fills the unused screen area
without stretching the image or forcing a crop.

## Choices

| Choice | Result |
| --- | --- |
| AMOLED black | Uses opaque black for the lowest light output on OLED screens. |
| Your color | Uses the opaque hex color chosen in the preview. |
| From image | Uses the largest color population from Android's Palette analysis. |
| Blurred edge | Enlarges a small sampled copy behind the fitted media, then adds a light darkening layer for legibility. |

Fill keeps its existing center-crop behavior. Choosing a Fit Canvas option does
not change the saved source file.

## Video behavior

Aura reads the first valid frame at 0, 100, 500, or 1,000 milliseconds. That
representative frame is bounded to a 512-pixel long edge. Aura makes the canvas
once and reuses it, so playback never calculates a blur for every frame.

For normal video, the fitted foreground and static canvas are composed into one
validated H.264 apply copy at up to 30 frames per second. For GIF wallpaper, the
service keeps one canvas bitmap and draws each GIF frame over it. The source
media is not stretched. Local video runs through Media3, so Fit Canvas doesn't
start YouTube tooling in a release channel where YouTube is unavailable.

## Fallbacks and limits

| Input | Defined behavior |
| --- | --- |
| Portrait, landscape, or ultrawide | Centers the complete media with its original aspect ratio. Even extreme dimensions are clamped before allocation. |
| Transparent image or GIF | Shows the selected canvas through transparent pixels. Transparent pixels do not influence Palette color selection. |
| HDR with blurred edge | Uses the Palette color instead. If no usable color exists, it uses AMOLED black. |
| Missing or unreadable representative frame | Uses AMOLED black. Video preparation still validates the finished media before replacing the active wallpaper. |

Static and normal-video canvases are capped at 8,000,000 output pixels. Static
Fit decode uses the screen's long edge instead of the larger crop budget. GIF
canvas files are capped at 250,000 pixels, representative frames at a 512-pixel
long edge, and managed video remains under the existing 256 MB limit.

The preview and apply paths share presentation values, mode names, color
normalization, and Palette selection. Fit Canvas defaults are included in an
explicit portable library backup. Android cloud backup and device transfer
exclude the preference store, active video, generated video copy, and cached
canvas image.
