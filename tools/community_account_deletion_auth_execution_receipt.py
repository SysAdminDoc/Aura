#!/usr/bin/env python3
"""Validate private Firebase Auth deletion execution evidence."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_auth_package import validate_auth_deletion_package
from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import (
    ReviewError,
    redact_uid_key,
    require_non_empty_string,
    require_object,
    sha256_json,
    sha256_text,
    utc_now,
)
from tools.community_deletion_request_lookup import normalize_request_code


VALID_METHODS = {"adminSdk", "firebaseConsole", "ownerApprovedCli"}
VALID_EXECUTION_STATUSES = {"deleted", "notFoundAfterDeletionCheck"}
VALID_POST_DELETE_STATES = {"notFound"}
SHA256_HEX = re.compile(r"^[a-fA-F0-9]{64}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate private Aura Firebase Auth deletion execution evidence."
    )
    parser.add_argument("--auth-package", required=True, help="JSON output from community_account_deletion_auth_package.py.")
    parser.add_argument("--execution-evidence", required=True, help="Private JSON evidence from the owner-approved Auth deletion action.")
    parser.add_argument("--support-reference", required=True, help="User-safe ticket or support reference label.")
    parser.add_argument("--output", help="Optional redacted Auth execution receipt path. Defaults to stdout.")
    return parser.parse_args()


def require_sha256(value: Any, label: str) -> str:
    text = require_non_empty_string(value, label)
    if not SHA256_HEX.fullmatch(text):
        raise ReviewError(f"{label} must be a SHA-256 hex digest")
    return text.lower()


def validate_auth_execution_evidence(
    evidence: Any,
    package: dict[str, Any],
    support_reference: str,
) -> dict[str, Any]:
    evidence_object = require_object(evidence, "Auth execution evidence")
    if evidence_object.get("schemaVersion") != 1:
        raise ReviewError("Auth execution evidence schemaVersion must be 1")
    if evidence_object.get("evidenceKind") != "communityAccountDeletionAuthExecution":
        raise ReviewError("Auth execution evidence has the wrong evidenceKind")

    request_code = normalize_request_code(
        require_non_empty_string(evidence_object.get("requestCode"), "Auth execution evidence requestCode")
    )
    if request_code != package.get("requestCode"):
        raise ReviewError("Auth execution evidence requestCode does not match the Auth package")

    expected_support_reference = require_non_empty_string(support_reference, "Support reference")
    if evidence_object.get("supportReference") != expected_support_reference:
        raise ReviewError("Auth execution evidence supportReference does not match")
    if package.get("supportReference") != expected_support_reference:
        raise ReviewError("Auth package supportReference does not match")

    uid = require_non_empty_string(evidence_object.get("uid"), "Auth execution evidence uid")
    if uid != package.get("uid"):
        raise ReviewError("Auth execution evidence uid does not match the Auth package uid")
    if evidence_object.get("uidHash") != sha256_text(uid):
        raise ReviewError("Auth execution evidence uidHash does not match uid")

    method = require_non_empty_string(evidence_object.get("method"), "Auth execution method")
    if method not in VALID_METHODS:
        raise ReviewError(f"Auth execution method must be one of: {', '.join(sorted(VALID_METHODS))}")

    execution_status = require_non_empty_string(
        evidence_object.get("executionStatus"), "Auth execution status"
    )
    if execution_status not in VALID_EXECUTION_STATUSES:
        raise ReviewError(
            f"Auth execution status must be one of: {', '.join(sorted(VALID_EXECUTION_STATUSES))}"
        )

    post_delete_state = require_non_empty_string(
        evidence_object.get("postDeleteVerification"), "Post-delete verification"
    )
    if post_delete_state not in VALID_POST_DELETE_STATES:
        raise ReviewError("Post-delete verification must prove the Auth user is not found")

    for field in ("projectId", "executedBy", "executedAt", "ownerApprovalReference", "privateEvidenceReference"):
        require_non_empty_string(evidence_object.get(field), f"Auth execution evidence {field}")
    require_sha256(evidence_object.get("privateEvidenceHash"), "Auth execution privateEvidenceHash")

    return evidence_object


def build_auth_execution_receipt(
    auth_package: Any,
    execution_evidence: Any,
    support_reference: str,
    receipted_at: str | None = None,
) -> dict[str, Any]:
    package = validate_auth_deletion_package(auth_package)
    evidence = validate_auth_execution_evidence(execution_evidence, package, support_reference)
    project_id = require_non_empty_string(evidence.get("projectId"), "Auth execution evidence projectId")

    return {
        "schemaVersion": 1,
        "receiptKind": "communityAccountDeletionAuthExecution",
        "executionStatus": "authDeleted",
        "requestCode": package.get("requestCode"),
        "supportReference": package.get("supportReference"),
        "uidHash": package.get("uidHash"),
        "uidKeySuffix": redact_uid_key(require_non_empty_string(package.get("safeUid"), "Auth package safeUid")),
        "method": evidence.get("method"),
        "projectIdHash": sha256_text(project_id),
        "executedAt": evidence.get("executedAt"),
        "executedBy": evidence.get("executedBy"),
        "ownerApprovalReference": evidence.get("ownerApprovalReference"),
        "postDeleteVerification": evidence.get("postDeleteVerification"),
        "privateEvidenceReference": evidence.get("privateEvidenceReference"),
        "privateEvidenceHash": require_sha256(evidence.get("privateEvidenceHash"), "Auth execution privateEvidenceHash"),
        "authPackageHash": sha256_json(package),
        "executionEvidenceHash": sha256_json(evidence),
        "receiptedAt": receipted_at or utc_now(),
        "privacyNote": "This receipt omits the full Firebase UID, project ID, service-account credentials, raw command output, console exports, and tokens.",
    }


def validate_auth_execution_receipt(receipt: Any, support_reference: str) -> dict[str, Any]:
    receipt_object = require_object(receipt, "Auth execution receipt")
    if receipt_object.get("schemaVersion") != 1:
        raise ReviewError("Auth execution receipt schemaVersion must be 1")
    if receipt_object.get("receiptKind") != "communityAccountDeletionAuthExecution":
        raise ReviewError("Auth execution receipt has the wrong receiptKind")
    if receipt_object.get("executionStatus") != "authDeleted":
        raise ReviewError("Auth execution receipt must be authDeleted")
    if receipt_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("Auth execution receipt supportReference does not match")
    normalize_request_code(require_non_empty_string(receipt_object.get("requestCode"), "Request code"))
    for field in (
        "uidHash",
        "uidKeySuffix",
        "method",
        "projectIdHash",
        "executedAt",
        "executedBy",
        "ownerApprovalReference",
        "privateEvidenceReference",
        "privateEvidenceHash",
        "authPackageHash",
        "executionEvidenceHash",
        "receiptedAt",
    ):
        require_non_empty_string(receipt_object.get(field), f"Auth execution receipt {field}")
    if receipt_object.get("postDeleteVerification") not in VALID_POST_DELETE_STATES:
        raise ReviewError("Auth execution receipt postDeleteVerification must prove notFound")
    require_sha256(receipt_object.get("privateEvidenceHash"), "Auth execution receipt privateEvidenceHash")
    return receipt_object


def dump_auth_execution_receipt(receipt: dict[str, Any]) -> str:
    return json.dumps(receipt, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        receipt = build_auth_execution_receipt(
            read_json(Path(args.auth_package)),
            read_json(Path(args.execution_evidence)),
            args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    receipt_text = dump_auth_execution_receipt(receipt)
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
