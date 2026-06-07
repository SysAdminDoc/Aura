# Distribution channel strategy

Aura is a full-feature GitHub/Obtainium/IzzyOnDroid app for now. Do not open an F-Droid mainline metadata PR until a real FOSS flavor exists or the proprietary dependency blockers are removed.

## Decision

| Channel | Current status | Build artifact | Rationale |
| --- | --- | --- | --- |
| GitHub Releases | Primary | Signed release APK from `.github/workflows/release.yml` | Keeps the complete app: Firebase community uploads/votes, Play Services subject segmentation, YouTube extraction, and signed release provenance. |
| Obtainium | Primary | Same GitHub Release APK | `obtainium.json` tracks `v*` releases and the signed APK asset. |
| IzzyOnDroid | Candidate | Same GitHub Release APK after owner submission | Best near-term non-Play app-store path because it can list APKs that F-Droid mainline cannot build. Requires release signing/checksum discipline and metadata review. |
| F-Droid mainline | Blocked | None | Current Gradle files include Firebase, the Google Services plugin, and Play Services ML Kit dependencies. F-Droid policy rejects those for mainline builds. |

## Full vs. FOSS matrix

| Surface | `full` build today | Future `foss` requirement |
| --- | --- | --- |
| Community uploads/votes/moderation | Firebase Auth, Realtime Database, Storage, admin Custom Claims, App Check client providers | Disable community surfaces or replace with a self-hostable backend contract using FOSS dependencies. |
| Subject segmentation/parallax | Google Play Services ML Kit subject segmentation plus ModuleInstallClient | Use a bundled/open model, MediaPipe task dependency acceptable to F-Droid, or disable subject-aware parallax/smart crop for FOSS. |
| Google Services plugin | Applied at app level for `google-services.json` | Move the plugin and `google-services.json` behind a full-only build path or remove it. |
| YouTube/NewPipe/yt-dlp | Kept | Review F-Droid source-build expectations for native/FFmpeg/Python payloads before submission. |
| Release signing | GitHub release workflow signs with owner key | For F-Droid reproducible builds, decide whether upstream signature copying is viable or whether F-Droid signs its own APK. |

## Preflight

Run this lightweight scan before any F-Droid work:

```powershell
py -3 tools/fdroid_preflight.py --expect-blocked
```

Expected result today: `F-Droid mainline status: blocked`.

For machine-readable output:

```powershell
py -3 tools/fdroid_preflight.py --expect-blocked --json
```

The script intentionally does not compile APKs. It scans the Gradle files for the current proprietary dependency blockers and checks whether a `productFlavors` boundary exists.

## Unblock criteria for F-Droid mainline

1. Add `full` and `foss` product flavors or make a documented decision to permanently skip F-Droid mainline.
2. Move Firebase/Auth/Database/Storage and `com.google.gms.google-services` into the `full` path only.
3. Replace or disable Play Services ML Kit subject segmentation in `foss`.
4. Prove `assembleFossRelease` without Firebase/Play Services dependencies.
5. Run a dependency tree review for the FOSS flavor.
6. Document any remaining anti-features or non-free network-service dependencies before metadata submission.

Until those criteria are met, GitHub Releases + Obtainium remain the supported install/update path and IzzyOnDroid is the realistic app-store submission target.

Android developer verification, branch-protection owner actions, and IzzyOnDroid submission prep are tracked in [developer-verification.md](developer-verification.md).
Alternative-store anti-feature, permission, network-service, and proprietary
dependency disclosure rows are tracked in
[alt-store-metadata.md](alt-store-metadata.md) and checked by
`tools/alt_store_metadata_check.py`.
