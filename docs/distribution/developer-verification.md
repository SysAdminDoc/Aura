# Android developer verification and store prep

Aura distributes outside Google Play through signed GitHub Releases and
Obtainium, with IzzyOnDroid as the near-term app-store candidate. Keep this
runbook current before broader non-Play distribution.

## Current status

| Area | Status |
| --- | --- |
| Package name | `com.freevibe` |
| Release artifact | Locally built and signed universal APK |
| Release provenance | `SHA256SUMS.txt`, `apksigner.txt`, and the signing certificate SHA-256 in release notes |
| Android developer verification | Decision recorded; owner identity verification and package registration remain |
| F-Droid mainline | `ready-for-review` since the FOSS flavor isolated Firebase, Play Services, and ML Kit; confirm with `tools/fdroid_preflight.py` |
| IzzyOnDroid | Ready for owner submission after a signed `v*` GitHub Release is visible |
| Accrescent | Not submitted. Enforces the Play target-SDK bar by removing apps and caps an APK set at 128 MiB; Aura is on targetSdk 35 |

## Decision record: register the existing release identity

**Decision (2026-07-16):** use a full-distribution Android Developer Console
account to register `com.freevibe` and Aura's existing stable release signing
certificate before the global enforcement expansion. Registration verifies the
developer/package relationship; it does not publish Aura on Google Play, replace
the signing key, or prevent distribution through GitHub, Obtainium, or
IzzyOnDroid.

Registering is preferred because Aura is publicly distributed to more than the
20-device limit of Android's limited-distribution account. Keeping the existing
certificate also preserves Android update continuity for current users.

The alternative is to abstain. Aura could still be installed with ADB or by
users who enable Android's advanced flow, but normal installs and updates of an
unregistered package can fail where verification is enforced. That adds a
24-hour setup delay, warning-heavy onboarding, and recurring support burden.
Rotating to a newly registered key is not a substitute: Android would reject it
as an update over releases signed with the existing key.

**Review by 2026-12-31.** `tools/distribution_decision_check.py` fails once that
date passes, so the decision cannot sit unexamined while the enforcement
timetable moves.

### What the decision means per channel

The register-or-abstain question is settled above. What changed since it was
recorded is that the two alternative stores Aura targets took opposite public
positions, so the consequences are worth stating plainly rather than leaving
them to be re-derived.

- **GitHub Releases and Obtainium:** unaffected either way. Sideloading a
  signed APK does not consult the verification registry, and ADB installs are
  exempt from enforcement entirely.
- **IzzyOnDroid:** unaffected by the decision. It distributes the upstream APK
  under Aura's own signing key, which registration preserves rather than
  changes.
- **F-Droid mainline:** F-Droid published an open letter on 2026-02-24 opposing
  developer verification and advising developers not to register "now or ever".
  That is a position on the program, not an inclusion criterion, and F-Droid
  builds and signs with its own key, so registering does not disqualify Aura.
  Worth knowing before any public statement, because a submission and a
  registration read as contradictory to that audience.
  Source: https://f-droid.org/2026/02/24/open-letter-opposing-developer-verification.html
- **Accrescent:** took the opposite line, registering itself and obtaining early
  access to the program. Registration is therefore aligned with an Accrescent
  submission, but that channel is gated on other things first: it enforces the
  Play target-SDK bar by removing non-compliant apps rather than hiding them,
  and caps an APK set at 128 MiB. Aura is on targetSdk 35, so it does not
  qualify yet.
  Source: https://blog.accrescent.app/posts/android-developer-verification/


## Rollout and install paths

- **August 2026:** Android launches the one-time advanced flow for power users.
  It requires developer mode, risk acknowledgement, and a 24-hour wait. The
  setting follows the user's account to a new device.
- **September 30, 2026:** participating stores require registered apps for users
  in Brazil, Indonesia, Singapore, and Thailand. During this initial phase,
  unregistered apps remain sideloadable through ADB or the advanced flow.
- **2027 and beyond:** Android expands developer-verification enforcement
  globally after partner and community feedback.

ADB behavior and ADB updates are unchanged and are not subject to the advanced
flow's waiting period. Users installing through GitHub or Obtainium should use
the normal package installer while available, use the advanced flow if Android
requires it, or follow the checksum-verified ADB path in `README.md`.

Sources:

- Android rollout announcement: https://developer.android.com/blog/posts/android-developer-verification-building-a-safer-ecosystem-together
- Android verification FAQ and advanced flow: https://developer.android.com/developer-verification/guides/faq
- Distribution choices: https://developer.android.com/developer-verification/guides

## Remaining owner action

The repository cannot submit identity documents or accept console terms. The
release owner must:

1. Create a full-distribution Android Developer Console account. If Aura later
   ships on Google Play, use Play Console to manage both Play and outside-Play
   registration instead.
2. Complete individual or organization identity verification. Organizations
   should prepare a D-U-N-S number and Search Console-verified website.
3. Register package name `com.freevibe`.
4. Add the SHA-256 certificate fingerprint from the existing release key. Read
   it from the locally generated `apksigner.txt` or run
   `apksigner verify --verbose --print-certs` against the signed release APK.
5. Prove ownership by uploading the requested APK signed with that key. If the
   console supplies an asset-folder challenge, include it only in the proof APK
   requested by the console.
6. Keep release notes at `owner-confirmation-required` until the console reports
   both the package name and current key as registered.

Sources:

- Android Developer Console registration: https://developer.android.com/developer-verification/guides/android-developer-console
- Play Console path: https://developer.android.com/developer-verification/guides/full-distribution

## Release checklist

For each `v*` GitHub Release:

1. Confirm the local release preflight and signed APK/AAB build completed.
2. Confirm the release APK is named
   `Aura-vX.Y.Z-versionCode-N-universal-release.apk`.
3. Confirm `SHA256SUMS.txt` contains the APK digest.
4. Confirm release notes include the APK SHA-256, signing certificate SHA-256,
   and Android developer-verification status.
5. Verify the APK locally with `apksigner verify --verbose --print-certs`.
6. Compare the local APK SHA-256 to `SHA256SUMS.txt`.
7. Install or update through Obtainium and confirm the selected asset is the
   signed universal APK.

Use `registered` only after the owner confirms `com.freevibe` and the current
signing key in Android Developer Console or Play Console.

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

## F-Droid state

Run the no-build preflight before any F-Droid work:

```powershell
py -3 tools/fdroid_preflight.py --expect-blocked
```

Expected result: `F-Droid mainline status: ready-for-review`.

Do not open an F-Droid mainline metadata PR until the criteria in
`docs/distribution/channel-strategy.md` are met.
