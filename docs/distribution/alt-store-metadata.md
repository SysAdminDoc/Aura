# Alternative-store disclosure matrix

This packet is the source for GitHub/Obtainium/IzzyOnDroid disclosure text and
the blocker record for any future F-Droid mainline work. The machine-readable
contract is [`alt-store-metadata.json`](alt-store-metadata.json).

## Channel status

| Channel | Status | Artifact | Disclosure |
| --- | --- | --- | --- |
| GitHub Releases | Supported | Signed release APK from `.github/workflows/release.yml` | Primary public install/update path with checksums, release notes, and artifact attestation. |
| Obtainium | Supported | Same GitHub Release APK selected by `obtainium.json` | Uses upstream GitHub tags and APK assets; users receive the same signed full build. |
| IzzyOnDroid | Candidate | Same signed GitHub Release APK after owner submission | Candidate listing must disclose proprietary dependencies, non-free network services, sensitive permissions, UGC moderation, and optional provider keys. |
| F-Droid mainline | Blocked | None | Blocked until a real FOSS flavor removes or isolates Firebase, Google Services, and Play Services ML Kit dependencies. |

## Anti-feature notes

| Label | Applies | en-US detail |
| --- | --- | --- |
| NonFreeDep | Yes | The full build depends on Firebase libraries, the Google Services Gradle plugin, and Google Play services ML Kit subject segmentation. |
| NonFreeNet | Yes | Community features use Firebase Auth, Realtime Database, Storage, Functions, and App Check. Remote providers include Wallhaven, Bing, Pexels, Pixabay, Reddit, Freesound, SoundCloud, Audius, ccMixter, Open-Meteo, Stability, and YouTube extraction paths. |
| Tracking | No | Aura does not use automatic analytics or crash reporting. Diagnostics export is local-only and user-initiated. |
| Ads | No | Aura has no ads, ad SDKs, subscriptions, or in-app purchases. |
| TetheredNet | No | The full build is not entirely dependent on one irreplaceable service; provider switches and local/bundled flows leave core personalization usable when individual services are disabled. |

## Sensitive permissions

Every manifest permission must stay listed in the JSON `permissions` array with
purpose and store-facing disclosure text. The rows currently cover wallpaper
application, sound assignment, recording, network access, storage compatibility,
foreground services, notifications, optional coarse location, and local contact
ringtone assignment.

## Network services

Every reviewed endpoint ID in `docs/security/network-endpoints.json` must stay
listed in the JSON `networkServices` array. Current rows cover Wallhaven, Bing,
Pexels, Pixabay, Reddit, Openverse, Freesound, SoundCloud, Audius, ccMixter,
Open-Meteo, Stability, YouTube/NewPipe extraction, Aura collection links, and
Firebase community surfaces.

## Generated wallpaper provider key behavior

Public GitHub/Obtainium/Izzy builds ship blank optional provider defaults.
Generated wallpaper requests require user disclosure acceptance, a user-provided
Stability key, and an explicit generation action. The provider credential
release, storage, and APK-scan checks enforce blank public defaults and
redaction coverage.

## UGC moderation

Community features require the current Community Guidelines version before
users can browse community feeds, upload, vote, report, block, follow, edit
profiles, or warm community identity at startup. Reporting, blocking, owner
delete, admin moderation, takedown receipt, and deletion support paths are
documented in the Play App content packet and support docs.

## F-Droid state

Run the existing no-build preflight before any F-Droid work:

```powershell
py -3 tools\fdroid_preflight.py --expect-blocked
```

Expected result today: `F-Droid mainline status: blocked`.

Do not open an F-Droid mainline metadata PR until the unblock criteria in
`docs/distribution/channel-strategy.md` pass.

## IzzyOnDroid submission notes

Owner submission should use the signed GitHub Release APK and checksum, include
anti-feature details at least in `en-US`, disclose Firebase/Google Play
services dependencies and remote provider network services, and avoid claiming
F-Droid mainline readiness.

## Release checklist

Before a public release intended for alternative-store submission:

1. Run `py -3 tools\alt_store_metadata_check.py --policy docs\distribution\alt-store-metadata.json --repo-root .`.
2. Run `py -3 tools\fdroid_preflight.py --expect-blocked` and keep the expected blocked result until a FOSS flavor exists.
3. Confirm `docs/distribution/channel-strategy.md` still matches this packet.
4. Confirm the release APK, checksum, release notes, and attestation are attached to the GitHub Release before IzzyOnDroid owner submission.

## Sources

- F-Droid Inclusion Policy: https://f-droid.org/docs/Inclusion_Policy/
- F-Droid Anti-Features: https://f-droid.org/en/docs/Anti-Features/
- F-Droid descriptions, graphics, and screenshots: https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/
- IzzyOnDroid YAML metadata: https://izzyondroid.org/docs/general/YamlMetadata/
- IzzyOnDroid APK repository notes: https://apt.izzysoft.de/fdroid/index/apk
