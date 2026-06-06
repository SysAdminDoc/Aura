# Provider Runtime Controls

This matrix is the runtime-control companion to `ProviderDisclosure.kt` and
`provider-policy.md`. It records whether each content source has a real disable
path, what happens when it is unavailable, and what still needs implementation.
`ProviderDisclosureTest` verifies that every `ContentSource` has a checked
runtime-control row.

| Source | Status | Current control | Disabled behavior | Follow-up |
| --- | --- | --- | --- | --- |
| Wallhaven | Partial | Optional API key plus sketchy/NSFW toggles; no full source disable flag. | Blank API key still allows public SFW Wallhaven calls; unsafe tiers coerce to SFW. | Add a source-enabled flag before publishing distribution profiles that must remove Wallhaven entirely. |
| Lorem Picsum | Covered | No active repository path. | New feeds do not request Lorem Picsum; saved legacy rows can remain visible. | None. |
| Bing Image of the Day | Missing | Always attempted as a Discover secondary source. | No first-class disabled state; failures fall back to other Discover sources. | Add a source-enabled flag or remote distribution profile before claiming Bing can be disabled. |
| Wikimedia Commons | Covered | No active repository path. | New feeds do not request Wikimedia; saved legacy rows can remain visible. | None. |
| Internet Archive | Covered | Removed active feed. | New sound feeds do not request Internet Archive; old saved records can still render. | None. |
| Reddit | Missing | Wallpaper subreddit lists are editable; video subreddits are currently hardcoded in the video ViewModel. | No source-disabled state; empty or failed requests degrade through feed fallback behavior. | Add one runtime flag that removes Reddit from wallpaper and video entry points and records disabled diagnostics. |
| NASA | Covered | No active repository path. | New feeds do not request NASA; saved legacy rows can remain visible. | None. |
| Freesound | Covered | No active browsing tab uses Freesound; optional key is retained for compatibility. | New sound browsing uses YouTube/community/bundled paths; saved records keep attribution. | None. |
| Jamendo | Covered | No active repository path. | New feeds do not request Jamendo; saved legacy rows can remain visible. | None. |
| Audius | Covered | No active browsing tab uses Audius. | New feeds do not request Audius; saved legacy rows can remain visible. | None. |
| ccMixter | Covered | No active browsing tab uses ccMixter. | New feeds do not request ccMixter; saved legacy rows can remain visible. | None. |
| Local device media | Not applicable | User action and Android permission/picker grants. | No remote provider is contacted; user can cancel picker flows or delete local copies. | None. |
| YouTube | Covered | Settings exposes a YouTube provider-enabled flag in addition to query customization and blocked words. | Disabled mode hides YouTube browsing, skips top hits and video discovery, falls back to bundled sounds, and blocks stream resolution before cache or downloader use. | Carry the flag into channel-specific distribution defaults when store profiles are added. |
| Pexels | Partial | Blank API key returns empty results for Pexels calls; default BuildConfig key can still enable it. | When the effective key is blank, Pexels wallpaper and video calls short-circuit to empty results. | Add an explicit source-enabled flag so distribution profiles can disable Pexels even when a key is bundled. |
| Pixabay | Partial | Blank API key returns empty results for Pixabay calls; default BuildConfig key can still enable it. | When the effective key is blank, Pixabay wallpaper and video calls short-circuit to empty results. | Add an explicit source-enabled flag and provider TTL guard before claiming policy-complete disablement. |
| Klipy | Covered | Removed active feed. | New feeds do not request Klipy; saved legacy rows can remain visible. | None. |
| SoundCloud | Covered | No active browsing tab uses SoundCloud. | New feeds do not request SoundCloud; saved legacy rows can remain visible. | None. |
| Aura Community | Missing | Firebase configuration availability and auth/rules failures only; no runtime community-off flag. | No source-disabled state; Firebase failures surface through upload/feed errors and cached local state. | Add a community-enabled flag that hides upload/actions and reports disabled diagnostics separately from outages. |
| Aura Picks | Covered | Ships with app assets and curated metadata. | No remote provider is contacted; removal requires changing bundled content metadata or assets. | None. |
| AI-generated | Partial | Provider key gates generation; no separate generated-content source flag. | Blank key prevents provider-backed generation, but generated local outputs can remain saved. | Add a generated-content source flag if store/distribution profiles need to remove generation entirely. |

## Current Runtime-Control Notes

- YouTube now has a runtime legal-mode flag covering sound browsing, explicit
  search/import, similar-sound lookup, top-hit prefetch, video discovery, and
  stream resolution. Distribution profiles still need channel-specific defaults
  before store builds can claim YouTube is off by default.
- Reddit has editable wallpaper subreddit lists but no single off switch, and
  video wallpaper subreddits are hardcoded. A single provider flag should remove
  both surfaces.
- Pexels and Pixabay currently short-circuit when their effective API key is
  blank, but a bundled default key means that is not a reliable disable control.
- Community features depend on Firebase availability and rules, but operator
  disablement should be distinct from outages so the UI can hide upload/vote
  actions deliberately.

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
