# Provider Policy Matrix

This matrix is the human-readable companion to `ProviderDisclosure.kt`. The app
uses the code model to populate Settings > Open source licenses, and unit tests
fail when a `ContentSource` enum value lacks a disclosure row.

| Source | Status | Content | License/terms summary | Required provenance | Cache/action policy |
| --- | --- | --- | --- | --- | --- |
| Wallhaven | Active | Wallpapers | Per-image rights from uploader/source | Source page when available | Browse, favorite, apply, download, and share links with source context. |
| Lorem Picsum | Legacy | Placeholder photos | Provider-defined placeholder-photo terms | Source link when present | Legacy/sample only; do not use as a primary catalog. |
| Bing Image of the Day | Active | Daily wallpapers | Provider-defined image use | Copyright text and source link | Cache daily metadata for freshness and continuity. |
| Wikimedia Commons | Active | Daily featured wallpapers | Public-domain or Creative Commons per file | Author, license, source page | Use as a small Discover enhancement with full provenance. |
| Internet Archive | Legacy | Audio | Per-item archived license | Item URL, creator, license | Removed source; retained for legacy favorite compatibility. |
| Reddit | Active | Wallpapers and video wallpapers | User-owned content under Reddit platform terms | Subreddit, author, permalink | Cache only browsing/user-save metadata and media. |
| NASA | Active | APOD wallpapers | NASA media guidelines; some third-party restrictions | Credit line and source link | Use daily and historical Discover items with visible credit. |
| Freesound | Legacy | Sounds from older saved records | Creative Commons per sound | Sound page, uploader, license | Preserve attribution for saved sounds. |
| Jamendo | Legacy | Music | Provider-defined music licensing | Artist, track, license, source page | Legacy records only. |
| Audius | Legacy | Music | Provider-defined artist/content terms | Artist and source page | Legacy records only. |
| ccMixter | Legacy | Creative Commons music | Creative Commons per track | Artist, license, source page | Legacy records only. Aura has no ccMixter client. |
| Local device media | Local | User-selected media | User-controlled local content | Do not invent license/uploader data | User controls edit, apply, favorite, and delete. |
| YouTube | Active | Sounds and video wallpapers | Provider-defined content terms | Watch/source link and creator context | Optional extractor/downloader features; respect legal-mode disablement. |
| Pexels | Active | Photos and videos | Pexels License | Photographer/source page | User key required in public releases; enhancement source only. |
| Pixabay | Active | Photos and videos | Pixabay Content License | Uploader/source page | User key required in public releases; respect cache and rate limits. |
| Klipy | Legacy | Animated media | Provider-defined media terms | Creator/source metadata | Legacy records only. |
| SoundCloud | Legacy | Sounds/music | Provider-defined creator/content terms | Artist, track, source page | Dormant by default; avoid unaudited ripping/downloading paths. |
| Aura Community | Community | User uploads | Uploader-selected CC0/CC BY/CC BY-NC or legacy User Upload | Uploader identity, selected license, attestation timestamp, and optional HTTPS source URL | Firebase-backed upload, vote, favorite, apply, and report flows; new uploads require rights attestation. |
| Aura Originals | Active | Original bundled sounds | Original waveform synthesis dedicated under CC0 1.0 | Aura Originals license page | Ships in app resources for offline preview, download, edit, and apply. |
| AI-generated | Generated | Generated wallpapers | Generator/provider terms plus user prompt context | Provider, creation time, prompt/style metadata | Store only user-generated outputs and restore/export/apply metadata. |

Runtime disablement and unavailable-source behavior are tracked separately in
[provider-runtime-controls.md](provider-runtime-controls.md). Release-runtime
dependency notice generation, native/copyleft packets, and item-level license
capability gates remain tracked in `ROADMAP.md`. Sound action capabilities are
documented in [sound-license-capabilities.md](sound-license-capabilities.md).
Community upload rights metadata is documented in
[community-upload-rights.md](community-upload-rights.md).
