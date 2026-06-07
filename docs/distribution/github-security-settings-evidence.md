# GitHub Security Settings Evidence

Cycle 119 adds a redacted receipt gate for future live GitHub repository
security settings proof. It does not replace owner/admin verification in
GitHub. It defines how private branch-protection, Dependabot, code-scanning,
secret-scanning, and release-attestation evidence is validated before a safe
receipt is shared in support or release artifacts.

## Private Evidence

The private evidence file must use:

- `schemaVersion: 1`
- `evidenceKind: githubSecuritySettingsEvidence`
- `evidenceStatus: collected`
- `supportReference` matching the release or repository security evidence
  packet
- `repository`, `collectedBy`, `collectedAt`, and `ownerApprovalReference`
- `privateEvidenceReference` and `privateEvidenceHash`
- `policyHashes.workflowPolicyHash` from
  `docs/distribution/github-security-workflows.json`
- `policyHashes.dependabotConfigHash` from `.github/dependabot.yml`
- `settings.branchProtection` with `main`, required status checks,
  up-to-date branch requirement, force-push block, and branch deletion block
- `settings.dependabot` with version updates configured, alerts enabled, and
  security updates enabled
- `settings.codeScanning.scorecardSarif: enabled`
- `settings.secretScanning.status: enabled`
- `settings.releaseAttestations.releaseWorkflowAttestation: configured`

The branch-protection status-check list must include the verify job and the
Firebase rules job. Store raw screenshots, `gh api` output, console exports,
or GitHub UI captures in private evidence storage only.

## Receipt Command

Run from the repo root after collecting the private evidence:

```powershell
py -3 tools\github_security_settings_receipt.py `
  --workflow-policy docs\distribution\github-security-workflows.json `
  --dependabot-config .github\dependabot.yml `
  --settings-evidence private\github-security-settings-evidence.json `
  --support-reference github-settings-<ticket-or-release> `
  --output artifacts\github-security-settings-receipt.json
```

The receipt omits raw repository names, private evidence references, raw
required-check names, screenshots, API responses, credentials, and tokens. It
keeps hashes for the repository name, policy files, private evidence, release
attestation evidence, and required status checks.

## Acceptance

The receipt is valid only when:

- The private evidence references the current workflow policy and Dependabot
  config by hash.
- Branch protection covers `main`, requires status checks, includes verify and
  Firebase rules checks, blocks force pushes, and blocks branch deletion.
- Dependabot version updates are configured and Dependabot alerts plus
  security updates are enabled.
- Scorecard SARIF code scanning and secret scanning are enabled.
- Release artifact attestation evidence is present and hashed.
- The receipt validates with
  `validate_github_security_settings_receipt()` or the backend tool unittest
  suite.

## Remaining Gates

Do not claim live repository security settings from source configuration alone.
Owner/admin evidence from GitHub is still required for branch protection,
required checks, Dependabot alerts/security updates, code scanning, secret
scanning, and release-attestation visibility.
