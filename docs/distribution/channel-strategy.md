# Distribution channel strategy

Aura is a full-feature GitHub/Obtainium/IzzyOnDroid app for now. A Firebase-free
`foss` flavor boundary exists for local review, but do not open an F-Droid
mainline metadata PR until a release-mode source-build, metadata, and signing
review is complete.

## Decision

| Channel | Current status | Build artifact | Rationale |
| --- | --- | --- | --- |
| GitHub Releases | Primary | Signed release APK from the local release lane | Keeps the complete app: Firebase community uploads/votes, Play Services subject segmentation, YouTube extraction, and signed release provenance. |
| Obtainium | Primary | Same GitHub Release APK | `obtainium.json` tracks `v*` releases and the signed APK asset. |
| IzzyOnDroid | Candidate | Same GitHub Release APK after owner submission | Best near-term non-Play app-store path because it can list APKs that F-Droid mainline cannot build. Requires release signing/checksum discipline and metadata review. |
| F-Droid mainline | Blocked | No published artifact | The local `foss` flavor builds without Firebase or Play Services dependencies, but mainline submission still needs release/source-build metadata and owner review. |

## Full vs. FOSS matrix

| Surface | `full` build today | Future `foss` requirement |
| --- | --- | --- |
| Community uploads/votes/moderation | Firebase Auth, Realtime Database, Storage, admin Custom Claims, App Check client providers | `foss` hard-disables the community provider and compiles against local no-op Firebase adapters. |
| Subject segmentation/parallax | Google Play Services ML Kit subject segmentation plus ModuleInstallClient | `foss` compiles against local no-op segmentation adapters and falls back to center-crop/single-layer wallpaper behavior. |
| Google Services plugin | Applied for full builds with `google-services.json` | FOSS-only Gradle tasks skip the Google Services and OSS Licenses plugins. |
| YouTube/NewPipe/yt-dlp | Kept | Review F-Droid source-build expectations for native/Python payloads before submission. |
| Release signing | GitHub release workflow signs with owner key | For F-Droid reproducible builds, decide whether upstream signature copying is viable or whether F-Droid signs its own APK. |

## Preflight

Run this lightweight scan before any F-Droid flavor work:

```powershell
py -3 tools/fdroid_preflight.py --expect-pass
```

Expected result today: `F-Droid mainline status: ready-for-review`.

For machine-readable output:

```powershell
py -3 tools/fdroid_preflight.py --expect-pass --json
```

The script intentionally does not compile APKs. It scans the Gradle files for
FOSS-active proprietary dependency blockers and checks whether a `foss`
`productFlavors` boundary exists. Pair it with `.\gradlew.bat
:app:assembleFossDebug` and dependency-tree review before changing public
channel status.

The release-mode reproducibility gate builds two isolated tracked-source roots,
disables signing only for those verification artifacts, and compares both raw and
signature-stripped APK archive digests:

```powershell
py -3 tools\foss_reproducibility_check.py --build-twice --output-dir build\reproducibility
```

## Unblock criteria for F-Droid mainline

1. Prove `assembleFossRelease` without Firebase/Play Services dependencies.
2. Run a dependency tree review for the FOSS release flavor.
3. Review source-build expectations for bundled native/yt-dlp payloads.
4. Document any remaining anti-features or non-free network-service dependencies before metadata submission.
5. Decide whether upstream signature copying is viable or whether F-Droid signs its own APK.

Until those criteria are met, GitHub Releases + Obtainium remain the supported
install/update path and IzzyOnDroid is the realistic app-store submission target.

Android developer verification, branch-protection owner actions, and IzzyOnDroid submission prep are tracked in [developer-verification.md](developer-verification.md).
Alternative-store anti-feature, permission, network-service, and proprietary
dependency disclosure rows are tracked in
[alt-store-metadata.md](alt-store-metadata.md) and checked by
`tools/alt_store_metadata_check.py`.
