# Provider Runtime Controls

This matrix is the runtime-control companion to `ProviderDisclosure.kt` and
`provider-policy.md`. It records whether each content source has a real disable
path, what happens when it is unavailable, and what still needs implementation.
`ProviderDisclosureTest` verifies that every `ContentSource` has a checked
runtime-control row.

| Source | Status | Current control | Disabled behavior | Follow-up |
| --- | --- | --- | --- | --- |
| Wallhaven | Covered | Settings exposes a Wallhaven provider-enabled flag in addition to the optional API key and sketchy/NSFW toggles. | Disabled mode hides Wallhaven browsing, color/random/similar actions, rotation picker entries, and skips Wallhaven API calls while recording disabled diagnostics. | None. |
| Lorem Picsum | Covered | No active repository path. | New feeds do not request Lorem Picsum; saved legacy rows can remain visible. | None. |
| Bing Image of the Day | Covered | Settings exposes a Bing Daily provider-enabled flag. | Disabled mode skips Bing daily-image API calls, returns empty source results, records disabled diagnostics, and hides Bing from rotation pickers unless already selected. | None. |
| Wikimedia Commons | Covered | Discover can request the daily featured image through the shared secondary-source budget. | It is skipped when secondary discovery is disabled or the provider is unavailable; saved rows keep attribution. | None. |
| Internet Archive | Covered | Removed active feed. | New sound feeds do not request Internet Archive; old saved records can still render. | None. |
| Reddit | Covered | Settings exposes a Reddit provider-enabled flag in addition to editable wallpaper subreddit lists. | Disabled mode hides Reddit wallpaper browsing, skips daily picks, video wallpaper Reddit jobs, scheduled Reddit rotations, and repository network calls while recording disabled diagnostics. | Move video subreddit lists into Preferences if distribution profiles need source-specific Reddit video curation. |
| NASA | Covered | Discover can request the daily APOD and a small historical selection through the shared secondary-source budget. | It is skipped when secondary discovery is disabled or the provider is unavailable; saved rows keep attribution. | None. |
| Freesound | Covered | No active browsing tab uses Freesound; any old stored key is deleted at startup. | New sound browsing uses YouTube/community/bundled paths; saved records keep attribution. | None. |
| Jamendo | Covered | No active repository path. | New feeds do not request Jamendo; saved legacy rows can remain visible. | None. |
| Audius | Covered | No active browsing tab uses Audius. | New feeds do not request Audius; saved legacy rows can remain visible. | None. |
| ccMixter | Covered | No active browsing tab uses ccMixter. | New feeds do not request ccMixter; saved legacy rows can remain visible. | None. |
| Local device media | Not applicable | User action and Android permission/picker grants. | No remote provider is contacted; user can cancel picker flows or delete local copies. | None. |
| YouTube | Covered | Settings exposes a YouTube provider-enabled flag in addition to query customization and blocked words. | Disabled mode hides YouTube browsing, skips top hits and video discovery, falls back to bundled sounds, and blocks stream resolution before cache or downloader use. | Carry the flag into channel-specific distribution defaults when store profiles are added. |
| Pexels | Covered | Settings exposes a Pexels provider switch and user API key field. Public releases keep the source off until that key is supplied. | Disabled mode hides Pexels wallpaper browsing, skips Discover/search/style-biased/video API calls, and records disabled diagnostics before reading credentials. When enabled, Discover/video batches drop Pexels-only results unless non-Pexels base inventory is present. | None. |
| Pixabay | Covered | Settings exposes a Pixabay provider switch and user API key field. Public releases keep the source off until that key is supplied; active requests use 24-hour fresh-cache paths and 429 backoff. | Disabled mode hides Pixabay wallpaper browsing, removes it from rotation pickers, skips wallpaper/video API calls, and records disabled diagnostics before reading credentials. | None. |
| Klipy | Covered | Removed active feed. | New feeds do not request Klipy; saved legacy rows can remain visible. | None. |
| SoundCloud | Covered | No active browsing tab uses SoundCloud. | New feeds do not request SoundCloud; saved legacy rows can remain visible. | None. |
| Aura Community | Covered | Settings exposes a Community source-enabled flag in addition to Firebase availability and installed App Check providers. | Disabled mode skips startup identity warm-up, hides community tabs/uploads/votes/creator profile entry points, blocks feed/upload/follow repository calls, and records disabled diagnostics separately from Firebase outages. | None for runtime disablement; keep separate public-data deletion, takedown, App Check enforcement, and quota work tracked in `ROADMAP.md`. |
| Aura Originals | Covered | Twenty-five original Ogg tones ship with app resources and CC0 metadata. | No remote provider is contacted; removal requires changing the packaged resources and metadata. | None. |
| AI-generated | Covered | Settings exposes a generated-wallpapers source flag in addition to the provider key. | Disabled mode hides generation entry points and blocks Stability requests before provider-key validation while saved generated outputs can remain visible. | Carry the flag into channel-specific distribution defaults when store profiles are added. |

## Current Runtime-Control Notes

- Wallhaven has a default-off provider flag covering featured/search, color,
  random, similar, Discover, and auto-wallpaper rotation source paths.
- YouTube now has a runtime legal-mode flag covering sound browsing, explicit
  search/import, similar-sound lookup, top-hit prefetch, video discovery, and
  stream resolution. Distribution profiles still need channel-specific defaults
  before store builds can claim YouTube is off by default.
- Reddit now has a default-on provider flag covering wallpaper browse, daily
  pick/background jobs, scheduled rotations, repository calls, and video
  wallpaper discovery. Video wallpaper subreddit curation remains hardcoded.
- Bing Daily has a default-off provider flag covering Discover secondary
  daily-image calls and auto-wallpaper rotation choices.
- Pexels and Pixabay have provider flags covering wallpaper and video API calls.
  They default off because public releases do not embed provider keys. Pexels wallpaper Discover and
  video-wallpaper discovery keep Pexels as enhancement-only inventory by
  dropping Pexels-only batches unless a non-Pexels base source is present.
  Pixabay photo requests and video metadata requests now use 24-hour fresh-cache
  paths and 429 backoff.
- Community has a default-off source flag covering startup identity warm-up,
  sound/wallpaper community feeds, uploads, vote actions, creator profile
  navigation, and creator follow/unfollow calls. Data lifecycle, deletion,
  takedown, App Check enforcement, and quota hardening remain separate
  community compliance items.
  New community uploads require selected license, rights attestation, uploader
  UID, timestamp, and optional HTTPS source URL metadata; see
  [community-upload-rights.md](community-upload-rights.md).
- Generated wallpapers have a default-off source flag covering the Wallpapers
  Generate chip, the generator API-key surface, and the ViewModel request gate.
  Saved generated wallpapers stay visible because they are local user content.
- Sound actions now have item-level license capability gates for apply,
  download, share, edit/trim, contact assignment, and Aura Originals use. The
  human-readable matrix lives in
  [sound-license-capabilities.md](sound-license-capabilities.md).
- Aura Originals now ship as local CC0 Ogg resources. They appear before remote
  results and use the same bounded media validation for download and apply.

## Policy Sources

- Pexels API wallpaper guidance:
  https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app
- Pexels API documentation:
  https://www.pexels.com/api/documentation/
- Pixabay API documentation:
  https://pixabay.com/api/docs/
- Reddit Data API Terms:
  https://redditinc.com/policies/data-api-terms
- Reddit Developer Terms:
  https://redditinc.com/policies/developer-terms
- YouTube API Services Developer Policies:
  https://developers.google.com/youtube/terms/developer-policies
