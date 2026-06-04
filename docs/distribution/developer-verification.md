# Android developer verification and store prep

Aura distributes outside Google Play today through GitHub Releases and
Obtainium, with IzzyOnDroid as the near-term app-store candidate. Keep this
runbook current before broader non-Play distribution.

## Current status

| Area | Status |
| --- | --- |
| Package name | `com.freevibe` |
| Release artifact | Signed universal APK from `.github/workflows/release.yml` |
| Release provenance | `SHA256SUMS.txt`, signing certificate SHA-256, and GitHub artifact attestation in release notes |
| Android developer verification | Owner action required; package registration cannot be completed from the repo |
| Branch protection | Owner action required; GitHub API check on 2026-06-04 showed `main` has no required status checks |
| F-Droid mainline | Blocked until a real FOSS flavor removes or isolates Firebase, Google Services, and Play Services ML Kit |
| IzzyOnDroid | Ready for owner submission after a signed `v*` GitHub Release is visible |

## Android developer verification path

Android's current verification guide says regional enforcement starts in
September 2026 for Brazil, Indonesia, Singapore, and Thailand, with global
rollout continuing in 2027 and beyond. It also says certified-device installs
are affected regardless of download source, so GitHub, Obtainium, and
IzzyOnDroid users are in scope.

Owner steps:

1. Choose the console path:
   - If Aura is only distributed outside Google Play, use Android Developer
     Console.
   - If Aura is also distributed through Google Play, use Play Console and
     register outside-Play package names/keys there.
2. Complete identity verification for the owner account. Organizations should
   prepare D-U-N-S and Search Console website verification.
3. Register package name `com.freevibe`.
4. Add the app signing key SHA-256 fingerprint. Use the "Signing certificate
   SHA-256" value printed by the release workflow and `apksigner`.
5. Prove ownership by uploading the signed APK requested by the console. If the
   console provides an asset-folder challenge snippet, add it to the release
   candidate before signing that proof APK.
6. Record the package status as `registered` only after the console confirms
   registration. Until then, release notes should keep
   `owner-confirmation-required`.

Sources:

- Android verification overview: https://developer.android.com/developer-verification/guides
- Android Developer Console registration: https://developer.android.com/developer-verification/guides/android-developer-console
- Play Console path: https://developer.android.com/developer-verification/guides/full-distribution

## Release checklist

For each `v*` GitHub Release:

1. Confirm the release was created by `.github/workflows/release.yml`.
2. Confirm the release APK is named
   `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
3. Confirm `SHA256SUMS.txt` contains the APK digest.
4. Confirm release notes include:
   - APK SHA-256.
   - Signing certificate SHA-256.
   - GitHub artifact attestation URL.
   - Android developer verification status.
5. Verify the APK locally with `apksigner verify --verbose --print-certs`.
6. Compare the local APK SHA-256 to `SHA256SUMS.txt`.
7. Install or update through Obtainium and confirm the selected asset is the
   signed universal APK.

Manual `workflow_dispatch` releases accept an
`android_developer_verification_status` input. Use `registered` only when the
owner has confirmed `com.freevibe` and the current signing key in Android
Developer Console or Play Console.

## IzzyOnDroid submission prep

IzzyOnDroid distributes official upstream APKs and records the signing key on
first inclusion so future updates can be checked for signature continuity. Do
not submit a debug APK or locally signed test build.

Submit after a signed `v*` release includes:

- Repository URL: `https://github.com/SysAdminDoc/Aura`
- Package id: `com.freevibe`
- APK URL from the GitHub Release.
- `SHA256SUMS.txt` URL from the same release.
- Signing certificate SHA-256 from release notes.
- License: MIT, from `LICENSE`.
- Source-code tag matching the release.
- App metadata from `fastlane/metadata/android/en-US/`.
- Disclosure that the current full build uses Firebase, Google Services, and
  Play Services ML Kit while F-Droid mainline remains blocked.

Source: https://apt.izzysoft.de/fdroid/index/apk

## Branch protection owner action

`verify.yml` exists, but branch protection still needs an owner/admin setting.
The GitHub API check on 2026-06-04 returned `required_status_checks: null` for
`main`, so this is not complete in repository settings.

Owner action:

1. Open GitHub Settings > Branches > Branch protection rules for `main`.
2. Enable required status checks before merging.
3. Require the `verify` check from `Verify (build + unit tests + lint)`.
4. Keep this documented as owner-blocked until the API reports a required
   status check for `main`.

Source: https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/managing-a-branch-protection-rule

## F-Droid state

Run the no-build preflight before any F-Droid work:

```powershell
py -3 tools/fdroid_preflight.py --expect-blocked
```

Expected result today: `F-Droid mainline status: blocked`.

Do not open an F-Droid mainline metadata PR until the criteria in
`docs/distribution/channel-strategy.md` are met.
