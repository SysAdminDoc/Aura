#!/usr/bin/env python3
"""Validate private GitHub repository security settings evidence."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import (
    ReviewError,
    require_non_empty_string,
    require_object,
    sha256_json,
    sha256_text,
    utc_now,
)


SHA256_HEX = re.compile(r"^[a-fA-F0-9]{64}$")
REQUIRED_STATUS_CHECK_KEYWORDS = ("verify", "firebase-rules")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate private Aura GitHub security settings evidence."
    )
    parser.add_argument("--workflow-policy", default="docs/distribution/github-security-workflows.json")
    parser.add_argument("--dependabot-config", default=".github/dependabot.yml")
    parser.add_argument("--settings-evidence", required=True)
    parser.add_argument("--support-reference", required=True)
    parser.add_argument("--output", help="Optional redacted receipt path. Defaults to stdout.")
    return parser.parse_args()


def require_sha256(value: Any, label: str) -> str:
    text = require_non_empty_string(value, label)
    if not SHA256_HEX.fullmatch(text):
        raise ReviewError(f"{label} must be a SHA-256 hex digest")
    return text.lower()


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise ReviewError(f"{label} must be a boolean")
    return value


def require_enabled(value: Any, label: str) -> str:
    text = require_non_empty_string(value, label)
    if text != "enabled":
        raise ReviewError(f"{label} must be enabled")
    return text


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ReviewError(f"{label} must be a non-empty list")
    values: list[str] = []
    for index, item in enumerate(value):
        values.append(require_non_empty_string(item, f"{label}[{index}]"))
    if len(values) != len(set(values)):
        raise ReviewError(f"{label} contains duplicate values")
    return values


def workflow_policy_hash(workflow_policy: dict[str, Any]) -> str:
    return sha256_json(workflow_policy)


def dependabot_config_hash(dependabot_config_text: str) -> str:
    return sha256_text(dependabot_config_text)


def validate_branch_protection(settings: dict[str, Any]) -> dict[str, Any]:
    branch = require_object(settings.get("branchProtection"), "Branch protection settings")
    if require_non_empty_string(branch.get("defaultBranch"), "Branch protection defaultBranch") != "main":
        raise ReviewError("Branch protection defaultBranch must be main")
    if require_bool(branch.get("requiresStatusChecks"), "Branch protection requiresStatusChecks") is not True:
        raise ReviewError("Branch protection must require status checks")
    if require_bool(branch.get("requiresUpToDateBranch"), "Branch protection requiresUpToDateBranch") is not True:
        raise ReviewError("Branch protection must require an up-to-date branch")
    if require_bool(branch.get("allowsForcePushes"), "Branch protection allowsForcePushes") is not False:
        raise ReviewError("Branch protection must block force pushes")
    if require_bool(branch.get("allowsDeletions"), "Branch protection allowsDeletions") is not False:
        raise ReviewError("Branch protection must block branch deletion")

    checks = require_string_list(branch.get("requiredStatusChecks"), "Branch protection requiredStatusChecks")
    lowered = [check.lower() for check in checks]
    missing = [
        keyword
        for keyword in REQUIRED_STATUS_CHECK_KEYWORDS
        if not any(keyword in check for check in lowered)
    ]
    if missing:
        raise ReviewError(f"Branch protection requiredStatusChecks missing: {', '.join(missing)}")

    return {
        "defaultBranch": "main",
        "requiresStatusChecks": True,
        "requiresUpToDateBranch": True,
        "allowsForcePushes": False,
        "allowsDeletions": False,
        "requiredStatusCheckCount": len(checks),
        "requiredStatusCheckHashes": [sha256_text(check) for check in sorted(checks)],
    }


def validate_dependabot(settings: dict[str, Any]) -> dict[str, Any]:
    dependabot = require_object(settings.get("dependabot"), "Dependabot settings")
    if require_bool(dependabot.get("versionUpdatesConfigured"), "Dependabot versionUpdatesConfigured") is not True:
        raise ReviewError("Dependabot version updates must be configured")
    return {
        "versionUpdatesConfigured": True,
        "alerts": require_enabled(dependabot.get("alerts"), "Dependabot alerts"),
        "securityUpdates": require_enabled(
            dependabot.get("securityUpdates"),
            "Dependabot securityUpdates",
        ),
    }


def validate_code_scanning(settings: dict[str, Any]) -> dict[str, Any]:
    code_scanning = require_object(settings.get("codeScanning"), "Code scanning settings")
    return {
        "scorecardSarif": require_enabled(code_scanning.get("scorecardSarif"), "Code scanning Scorecard SARIF"),
    }


def validate_secret_scanning(settings: dict[str, Any]) -> dict[str, Any]:
    secret_scanning = require_object(settings.get("secretScanning"), "Secret scanning settings")
    return {
        "status": require_enabled(secret_scanning.get("status"), "Secret scanning status"),
    }


def validate_release_attestations(settings: dict[str, Any]) -> dict[str, Any]:
    release_attestations = require_object(
        settings.get("releaseAttestations"),
        "Release attestation settings",
    )
    if require_non_empty_string(
        release_attestations.get("releaseWorkflowAttestation"),
        "Release workflow attestation",
    ) != "configured":
        raise ReviewError("Release workflow attestation must be configured")
    require_non_empty_string(
        release_attestations.get("privateEvidenceReference"),
        "Release attestation privateEvidenceReference",
    )
    require_sha256(
        release_attestations.get("privateEvidenceHash"),
        "Release attestation privateEvidenceHash",
    )
    return {
        "releaseWorkflowAttestation": "configured",
        "privateEvidenceReferenceHash": sha256_text(release_attestations["privateEvidenceReference"]),
        "privateEvidenceHash": require_sha256(
            release_attestations.get("privateEvidenceHash"),
            "Release attestation privateEvidenceHash",
        ),
    }


def validate_security_settings_evidence(
    evidence: Any,
    workflow_policy: dict[str, Any],
    dependabot_config_text: str,
    support_reference: str,
) -> dict[str, Any]:
    evidence_object = require_object(evidence, "GitHub security settings evidence")
    if evidence_object.get("schemaVersion") != 1:
        raise ReviewError("GitHub security settings evidence schemaVersion must be 1")
    if evidence_object.get("evidenceKind") != "githubSecuritySettingsEvidence":
        raise ReviewError("GitHub security settings evidence has the wrong evidenceKind")
    if evidence_object.get("evidenceStatus") != "collected":
        raise ReviewError("GitHub security settings evidence evidenceStatus must be collected")
    if evidence_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("GitHub security settings evidence supportReference does not match")

    for field in (
        "repository",
        "collectedAt",
        "collectedBy",
        "ownerApprovalReference",
        "privateEvidenceReference",
        "privateEvidenceHash",
    ):
        require_non_empty_string(evidence_object.get(field), f"GitHub security settings evidence {field}")
    require_sha256(
        evidence_object.get("privateEvidenceHash"),
        "GitHub security settings evidence privateEvidenceHash",
    )

    policy_hashes = require_object(evidence_object.get("policyHashes"), "GitHub security settings policyHashes")
    if policy_hashes.get("workflowPolicyHash") != workflow_policy_hash(workflow_policy):
        raise ReviewError("GitHub security settings workflowPolicyHash does not match")
    if policy_hashes.get("dependabotConfigHash") != dependabot_config_hash(dependabot_config_text):
        raise ReviewError("GitHub security settings dependabotConfigHash does not match")

    settings = require_object(evidence_object.get("settings"), "GitHub security settings")
    return {
        "evidence": evidence_object,
        "branchProtection": validate_branch_protection(settings),
        "dependabot": validate_dependabot(settings),
        "codeScanning": validate_code_scanning(settings),
        "secretScanning": validate_secret_scanning(settings),
        "releaseAttestations": validate_release_attestations(settings),
    }


def build_github_security_settings_receipt(
    workflow_policy: Any,
    dependabot_config_text: str,
    settings_evidence: Any,
    support_reference: str,
    receipted_at: str | None = None,
) -> dict[str, Any]:
    policy_object = require_object(workflow_policy, "GitHub security workflow policy")
    validated = validate_security_settings_evidence(
        settings_evidence,
        policy_object,
        dependabot_config_text,
        support_reference,
    )
    evidence = validated["evidence"]
    repository = require_non_empty_string(evidence.get("repository"), "GitHub security settings repository")

    return {
        "schemaVersion": 1,
        "receiptKind": "githubSecuritySettingsEvidence",
        "evidenceStatus": "settingsVerified",
        "supportReference": evidence.get("supportReference"),
        "repositoryHash": sha256_text(repository),
        "collectedAt": evidence.get("collectedAt"),
        "collectedBy": evidence.get("collectedBy"),
        "ownerApprovalReference": evidence.get("ownerApprovalReference"),
        "workflowPolicyHash": workflow_policy_hash(policy_object),
        "dependabotConfigHash": dependabot_config_hash(dependabot_config_text),
        "settingsEvidenceHash": sha256_json(evidence),
        "privateEvidenceReferenceHash": sha256_text(
            require_non_empty_string(
                evidence.get("privateEvidenceReference"),
                "GitHub security settings privateEvidenceReference",
            )
        ),
        "privateEvidenceHash": require_sha256(
            evidence.get("privateEvidenceHash"),
            "GitHub security settings privateEvidenceHash",
        ),
        "branchProtection": validated["branchProtection"],
        "dependabot": validated["dependabot"],
        "codeScanning": validated["codeScanning"],
        "secretScanning": validated["secretScanning"],
        "releaseAttestations": validated["releaseAttestations"],
        "receiptedAt": receipted_at or utc_now(),
        "privacyNote": "This receipt omits raw repository names, private evidence paths, screenshots, API responses, credentials, and tokens.",
    }


def validate_github_security_settings_receipt(receipt: Any, support_reference: str) -> dict[str, Any]:
    receipt_object = require_object(receipt, "GitHub security settings receipt")
    if receipt_object.get("schemaVersion") != 1:
        raise ReviewError("GitHub security settings receipt schemaVersion must be 1")
    if receipt_object.get("receiptKind") != "githubSecuritySettingsEvidence":
        raise ReviewError("GitHub security settings receipt has the wrong receiptKind")
    if receipt_object.get("evidenceStatus") != "settingsVerified":
        raise ReviewError("GitHub security settings receipt evidenceStatus must be settingsVerified")
    if receipt_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("GitHub security settings receipt supportReference does not match")

    for field in (
        "collectedAt",
        "collectedBy",
        "ownerApprovalReference",
        "receiptedAt",
    ):
        require_non_empty_string(receipt_object.get(field), f"GitHub security settings receipt {field}")
    for field in (
        "repositoryHash",
        "workflowPolicyHash",
        "dependabotConfigHash",
        "settingsEvidenceHash",
        "privateEvidenceReferenceHash",
        "privateEvidenceHash",
    ):
        require_sha256(receipt_object.get(field), f"GitHub security settings receipt {field}")

    branch = require_object(receipt_object.get("branchProtection"), "Receipt branchProtection")
    if branch.get("defaultBranch") != "main" or branch.get("requiresStatusChecks") is not True:
        raise ReviewError("GitHub security settings receipt branch protection is invalid")
    checks = branch.get("requiredStatusCheckHashes")
    if not isinstance(checks, list) or not checks:
        raise ReviewError("GitHub security settings receipt requiredStatusCheckHashes must be a non-empty list")
    for index, check_hash in enumerate(checks):
        require_sha256(check_hash, f"GitHub security settings receipt requiredStatusCheckHashes[{index}]")

    dependabot = require_object(receipt_object.get("dependabot"), "Receipt Dependabot settings")
    if dependabot.get("versionUpdatesConfigured") is not True:
        raise ReviewError("GitHub security settings receipt Dependabot version updates are invalid")
    if dependabot.get("alerts") != "enabled" or dependabot.get("securityUpdates") != "enabled":
        raise ReviewError("GitHub security settings receipt Dependabot settings are invalid")

    code_scanning = require_object(receipt_object.get("codeScanning"), "Receipt codeScanning")
    if code_scanning.get("scorecardSarif") != "enabled":
        raise ReviewError("GitHub security settings receipt code scanning is invalid")
    secret_scanning = require_object(receipt_object.get("secretScanning"), "Receipt secretScanning")
    if secret_scanning.get("status") != "enabled":
        raise ReviewError("GitHub security settings receipt secret scanning is invalid")
    release_attestations = require_object(
        receipt_object.get("releaseAttestations"),
        "Receipt releaseAttestations",
    )
    if release_attestations.get("releaseWorkflowAttestation") != "configured":
        raise ReviewError("GitHub security settings receipt release attestation is invalid")
    require_sha256(
        release_attestations.get("privateEvidenceReferenceHash"),
        "Receipt release attestation privateEvidenceReferenceHash",
    )
    require_sha256(
        release_attestations.get("privateEvidenceHash"),
        "Receipt release attestation privateEvidenceHash",
    )
    return receipt_object


def dump_github_security_settings_receipt(receipt: dict[str, Any]) -> str:
    return json.dumps(receipt, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        receipt = build_github_security_settings_receipt(
            read_json(Path(args.workflow_policy)),
            Path(args.dependabot_config).read_text(encoding="utf-8"),
            read_json(Path(args.settings_evidence)),
            args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    receipt_text = dump_github_security_settings_receipt(receipt)
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(receipt_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(receipt_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
