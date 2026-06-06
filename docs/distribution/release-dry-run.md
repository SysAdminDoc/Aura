# Release dry-run validation

Use a manual release workflow run before a tagged public release when release packaging, signing, notices, native compliance, or artifact provenance changed.

## GitHub Actions dry run

The release workflow is configured with `workflow_dispatch`. GitHub documents manual workflow runs through the Actions tab, GitHub CLI, and REST API, and workflow artifacts are the supported way to store generated files from a run. Aura uses that manual branch run as the dry-run lane.

Manual branch runs:

- Build the signed release APK with the same release signing secrets as a tag release.
- Generate `THIRD-PARTY-NOTICES.md`.
- Generate `NATIVE-COMPLIANCE.md`.
- Check dependency notice, native compliance, and curated overlay drift gates.
- Generate `SHA256SUMS.txt` and `RELEASE_NOTES.md`.
- Run `tools/release_artifact_bundle_check.py` against the final `release/` directory.
- Upload the bundle with `actions/upload-artifact`.
- Do not create or update a GitHub Release, because the public release upload step is tag-only.

Run from GitHub UI:

1. Open Actions.
2. Select `Build & Release APK`.
3. Select `Run workflow`.
4. Choose `main`.
5. Set `android_developer_verification_status` to the current owner-confirmed state, usually `owner-confirmation-required`.
6. Start the run.
7. Download the `aura-release-*` workflow artifact.
8. Confirm it contains the APK, `THIRD-PARTY-NOTICES.md`, `NATIVE-COMPLIANCE.md`, `SHA256SUMS.txt`, `RELEASE_NOTES.md`, `apksigner.txt`, and `aapt-badging.txt`.

Run from GitHub CLI:

```powershell
gh workflow run "Build & Release APK" --ref main -f android_developer_verification_status=owner-confirmation-required
```

## Bundle validator

The release workflow validates the final directory before upload:

```bash
python3 tools/release_artifact_bundle_check.py \
  --release-dir "$RELEASE_DIR" \
  --apk-name "$APK_NAME" \
  --version-name "$VERSION_NAME" \
  --version-code "$VERSION_CODE"
```

The check fails when:

- A required artifact is missing or empty.
- The APK name does not match the version name and version code.
- `SHA256SUMS.txt` is missing the APK, third-party notice, or native-compliance digest.
- A recorded checksum does not match the file bytes.
- `RELEASE_NOTES.md` lacks the APK digest, notice/native-compliance entries, signing certificate, attestation line, build type, or package ID.
- `apksigner.txt` lacks the signing certificate SHA-256 digest.
- `aapt-badging.txt` reports `application-debuggable`.

## Local script smoke test

The validator can be exercised without a full APK build by creating a temporary release directory with tiny placeholder files. This checks parser and checksum behavior only; it does not replace a GitHub Actions dry run with real signing secrets.

```powershell
$tmp = Join-Path $env:TEMP "aura-release-bundle-smoke"
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $tmp | Out-Null
$apk = "Aura-v6.31.1-versionCode-112-universal-release.apk"
"apk" | Set-Content -Encoding ascii (Join-Path $tmp $apk)
"third-party" | Set-Content -Encoding ascii (Join-Path $tmp "THIRD-PARTY-NOTICES.md")
"native" | Set-Content -Encoding ascii (Join-Path $tmp "NATIVE-COMPLIANCE.md")
"Signer #1 certificate SHA-256 digest: test" | Set-Content -Encoding ascii (Join-Path $tmp "apksigner.txt")
"package: name='com.freevibe'" | Set-Content -Encoding ascii (Join-Path $tmp "aapt-badging.txt")
$apkHash = (Get-FileHash (Join-Path $tmp $apk) -Algorithm SHA256).Hash.ToLower()
$thirdHash = (Get-FileHash (Join-Path $tmp "THIRD-PARTY-NOTICES.md") -Algorithm SHA256).Hash.ToLower()
$nativeHash = (Get-FileHash (Join-Path $tmp "NATIVE-COMPLIANCE.md") -Algorithm SHA256).Hash.ToLower()
@"
$apkHash  $apk
$thirdHash  THIRD-PARTY-NOTICES.md
$nativeHash  NATIVE-COMPLIANCE.md
"@ | Set-Content -Encoding ascii (Join-Path $tmp "SHA256SUMS.txt")
@"
Aura 6.31.1 (versionCode 112)

Signed release artifact:
- APK: $apk
- APK SHA-256: $apkHash
- Third-party notices: THIRD-PARTY-NOTICES.md
- Native compliance packet: NATIVE-COMPLIANCE.md
- Signing certificate SHA-256: test
- GitHub artifact attestation: test
- Build type: release, android:debuggable=false

Android developer verification:
- Package: com.freevibe
"@ | Set-Content -Encoding ascii (Join-Path $tmp "RELEASE_NOTES.md")
python tools\release_artifact_bundle_check.py --release-dir $tmp --apk-name $apk --version-name 6.31.1 --version-code 112
```

Expected output:

```json
{"apk": "Aura-v6.31.1-versionCode-112-universal-release.apk", "releaseDir": "<temp path>", "status": "ok", "versionCode": "112", "versionName": "6.31.1"}
```

## Sources

- GitHub manual workflow runs: https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow
- GitHub workflow dispatch event: https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows
- GitHub workflow artifacts: https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts
