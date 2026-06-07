from __future__ import annotations

import copy
import json
import unittest
from pathlib import Path

from tools.community_account_deletion_review import ReviewError, sha256_json, sha256_text
from tools.github_security_settings_receipt import (
    build_github_security_settings_receipt,
    dump_github_security_settings_receipt,
    validate_github_security_settings_receipt,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def read_json(relative_path: str) -> dict[str, object]:
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def live_workflow_policy() -> dict[str, object]:
    return read_json("docs/distribution/github-security-workflows.json")


def live_dependabot_config_text() -> str:
    return (REPO_ROOT / ".github" / "dependabot.yml").read_text(encoding="utf-8")


def settings_evidence(
    workflow_policy: dict[str, object] | None = None,
    dependabot_config_text: str | None = None,
) -> dict[str, object]:
    workflow_policy = workflow_policy or live_workflow_policy()
    dependabot_config_text = dependabot_config_text or live_dependabot_config_text()
    return {
        "schemaVersion": 1,
        "evidenceKind": "githubSecuritySettingsEvidence",
        "evidenceStatus": "collected",
        "supportReference": "github-settings-123",
        "repository": "SysAdminDoc/Aura",
        "collectedAt": "2026-06-07T21:30:00Z",
        "collectedBy": "owner-ops",
        "ownerApprovalReference": "approval-github-settings-123",
        "privateEvidenceReference": "private/github/settings-api-response.json",
        "privateEvidenceHash": "f" * 64,
        "policyHashes": {
            "workflowPolicyHash": sha256_json(workflow_policy),
            "dependabotConfigHash": sha256_text(dependabot_config_text),
        },
        "settings": {
            "branchProtection": {
                "defaultBranch": "main",
                "requiresStatusChecks": True,
                "requiresUpToDateBranch": True,
                "allowsForcePushes": False,
                "allowsDeletions": False,
                "requiredStatusChecks": [
                    "Verify (build + unit tests + lint) / verify",
                    "Verify (build + unit tests + lint) / firebase-rules",
                ],
            },
            "dependabot": {
                "versionUpdatesConfigured": True,
                "alerts": "enabled",
                "securityUpdates": "enabled",
            },
            "codeScanning": {"scorecardSarif": "enabled"},
            "secretScanning": {"status": "enabled"},
            "releaseAttestations": {
                "releaseWorkflowAttestation": "configured",
                "privateEvidenceReference": "private/github/latest-release-attestation.json",
                "privateEvidenceHash": "e" * 64,
            },
        },
    }


class GitHubSecuritySettingsReceiptTest(unittest.TestCase):
    def test_security_settings_receipt_redacts_repository_and_private_paths(self) -> None:
        receipt = build_github_security_settings_receipt(
            live_workflow_policy(),
            live_dependabot_config_text(),
            settings_evidence(),
            support_reference="github-settings-123",
            receipted_at="2026-06-07T21:35:00Z",
        )
        receipt_text = dump_github_security_settings_receipt(receipt)

        self.assertEqual("settingsVerified", receipt["evidenceStatus"])
        self.assertEqual("enabled", receipt["dependabot"]["alerts"])  # type: ignore[index]
        self.assertEqual(2, receipt["branchProtection"]["requiredStatusCheckCount"])  # type: ignore[index]
        self.assertNotIn("SysAdminDoc/Aura", receipt_text)
        self.assertNotIn("private/github/settings-api-response.json", receipt_text)
        self.assertNotIn("private/github/latest-release-attestation.json", receipt_text)
        self.assertNotIn("Verify (build + unit tests + lint)", receipt_text)
        validate_github_security_settings_receipt(receipt, support_reference="github-settings-123")

    def test_security_settings_receipt_rejects_missing_required_check(self) -> None:
        evidence = settings_evidence()
        evidence["settings"]["branchProtection"]["requiredStatusChecks"] = [  # type: ignore[index]
            "Verify (build + unit tests + lint) / verify"
        ]

        with self.assertRaises(ReviewError):
            build_github_security_settings_receipt(
                live_workflow_policy(),
                live_dependabot_config_text(),
                evidence,
                support_reference="github-settings-123",
            )

    def test_security_settings_receipt_rejects_policy_hash_drift(self) -> None:
        evidence = settings_evidence()
        evidence["policyHashes"]["workflowPolicyHash"] = "0" * 64  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_github_security_settings_receipt(
                live_workflow_policy(),
                live_dependabot_config_text(),
                evidence,
                support_reference="github-settings-123",
            )

    def test_security_settings_receipt_rejects_disabled_dependabot_alerts(self) -> None:
        evidence = settings_evidence()
        evidence["settings"]["dependabot"]["alerts"] = "disabled"  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_github_security_settings_receipt(
                live_workflow_policy(),
                live_dependabot_config_text(),
                evidence,
                support_reference="github-settings-123",
            )

    def test_security_settings_receipt_rejects_disabled_secret_scanning(self) -> None:
        evidence = settings_evidence()
        evidence["settings"]["secretScanning"]["status"] = "disabled"  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_github_security_settings_receipt(
                live_workflow_policy(),
                live_dependabot_config_text(),
                evidence,
                support_reference="github-settings-123",
            )

    def test_security_settings_receipt_rejects_support_reference_drift(self) -> None:
        receipt = build_github_security_settings_receipt(
            live_workflow_policy(),
            live_dependabot_config_text(),
            settings_evidence(),
            support_reference="github-settings-123",
        )
        bad_receipt = copy.deepcopy(receipt)
        bad_receipt["supportReference"] = "wrong"

        with self.assertRaises(ReviewError):
            validate_github_security_settings_receipt(
                bad_receipt,
                support_reference="github-settings-123",
            )


if __name__ == "__main__":
    unittest.main()
