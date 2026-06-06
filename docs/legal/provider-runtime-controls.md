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
| Wikimedia Commons | Covered | No active repository path. | New feeds do not request Wikimedia; saved legacy rows can remain visible. | None. |
| Internet Archive | Covered | Removed active feed. | New sound feeds do not request Internet Archive; old saved records can still render. | None. |
| Reddit | Covered | Settings exposes a Reddit provider-enabled flag in addition to editable wallpaper subreddit lists. | Disabled mode hides Reddit wallpaper browsing, skips daily picks, video wallpaper Reddit jobs, scheduled Reddit rotations, and repository network calls while recording disabled diagnostics. | Move video subreddit lists into Preferences if distribution profiles need source-specific Reddit video curation. |
| NASA | Covered | No active repository path. | New feeds do not request NASA; saved legacy rows can remain visible. | None. |
| Freesound | Covered | No active browsing tab uses Freesound; optional key is retained for compatibility. | New sound browsing uses YouTube/community/bundled paths; saved records keep attribution. | None. |
| Jamendo | Covered | No active repository path. | New feeds do not request Jamendo; saved legacy rows can remain visible. | None. |
| Audius | Covered | No active browsing tab uses Audius. | New feeds do not request Audius; saved legacy rows can remain visible. | None. |
| ccMixter | Covered | No active browsing tab uses ccMixter. | New feeds do not request ccMixter; saved legacy rows can remain visible. | None. |
| Local device media | Not applicable | User action and Android permission/picker grants. | No remote provider is contacted; user can cancel picker flows or delete local copies. | None. |
| YouTube | Covered | Settings exposes a YouTube provider-enabled flag in addition to query customization and blocked words. | Disabled mode hides YouTube browsing, skips top hits and video discovery, falls back to bundled sounds, and blocks stream resolution before cache or downloader use. | Carry the flag into channel-specific distribution defaults when store profiles are added. |
| Pexels | Covered | Settings exposes a Pexels provider-enabled flag in addition to the optional API key. | Disabled mode hides Pexels wallpaper browsing, skips Discover/search/style-biased/video API calls, and records disabled diagnostics before reading bundled keys. | Carry the flag into channel-specific distribution defaults when store profiles are added. |
| Pixabay | Covered | Settings exposes a Pixabay provider-enabled flag in addition to the optional API key; photo and video metadata requests use 24-hour fresh-cache paths and 429 backoff. | Disabled mode hides Pixabay wallpaper browsing, removes it from rotation pickers, skips wallpaper/video API calls, and records disabled diagnostics before reading bundled keys. | None. |
| Klipy | Covered | Removed active feed. | New feeds do not request Klipy; saved legacy rows can remain visible. | None. |
| SoundCloud | Covered | No active browsing tab uses SoundCloud. | New feeds do not request SoundCloud; saved legacy rows can remain visible. | None. |
| Aura Community | Covered | Settings exposes a Community source-enabled flag in addition to Firebase availability. | Disabled mode skips startup identity warm-up, hides community tabs/uploads/votes/creator profile entry points, blocks feed/upload/follow repository calls, and records disabled diagnostics separately from Firebase outages. | None for runtime disablement; keep separate public-data deletion, takedown, and App Check hardening work tracked in `ROADMAP.md`. |
| Aura Picks | Covered | Ships with app assets and curated metadata. | No remote provider is contacted; removal requires changing bundled content metadata or assets. | None. |
| AI-generated | Partial | Provider key gates generation; no separate generated-content source flag. | Blank key prevents provider-backed generation, but generated local outputs can remain saved. | Add a generated-content source flag if store/distribution profiles need to remove generation entirely. |

## Current Runtime-Control Notes

- Wallhaven now has a default-on provider flag covering featured/search, color,
  random, similar, Discover, and auto-wallpaper rotation source paths.
- YouTube now has a runtime legal-mode flag covering sound browsing, explicit
  search/import, similar-sound lookup, top-hit prefetch, video discovery, and
  stream resolution. Distribution profiles still need channel-specific defaults
  before store builds can claim YouTube is off by default.
- Reddit now has a default-on provider flag covering wallpaper browse, daily
  pick/background jobs, scheduled rotations, repository calls, and video
  wallpaper discovery. Video wallpaper subreddit curation remains hardcoded.
- Bing Daily now has a default-on provider flag covering Discover secondary
  daily-image calls and auto-wallpaper rotation choices.
- Pexels and Pixabay now have default-on provider flags covering wallpaper and
  video API calls before bundled keys are read. Pixabay photo requests and video
  metadata requests now use 24-hour fresh-cache paths and 429 backoff.
- Community now has a default-on source flag covering startup identity warm-up,
  sound/wallpaper community feeds, uploads, vote actions, creator profile
  navigation, and creator follow/unfollow calls. Data lifecycle, deletion,
  takedown, and App Check hardening remain separate community compliance items.

## Policy Sources

- Pexels API wallpaper guidance:
  https://help.pexels.com/hc/en-us/articles/4405588861721-Can-I-use-the-API-as-a-wallpaper-app
- Pixabay API documentation:
  https://pixabay.com/api/docs/
- Reddit Data API Terms:
  https://redditinc.com/policies/data-api-terms
- Reddit Developer Terms:
  https://redditinc.com/policies/developer-terms
- YouTube API Services Developer Policies:
  https://developers.google.com/youtube/terms/developer-policies
