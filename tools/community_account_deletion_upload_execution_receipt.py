#!/usr/bin/env python3
"""Validate private public-upload deletion execution evidence."""

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
from tools.community_deletion_request_lookup import normalize_request_code


VALID_METHODS = {"adminSdk", "firebaseConsole", "ownerAdminWorkflow", "ownerApprovedCli"}
SHA256_HEX = re.compile(r"^[a-fA-F0-9]{64}$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate private Aura public-upload deletion execution evidence."
    )
    parser.add_argument("--upload-plan", required=True, help="JSON output from community_account_deletion_upload_plan.py.")
    parser.add_argument("--execution-evidence", required=True, help="Private JSON evidence from the owner/admin upload deletion workflow.")
    parser.add_argument("--support-reference", required=True, help="User-safe ticket or support reference label.")
    parser.add_argument("--output", help="Optional redacted upload execution receipt path. Defaults to stdout.")
    return parser.parse_args()


def require_sha256(value: Any, label: str) -> str:
    text = require_non_empty_string(value, label)
    if not SHA256_HEX.fullmatch(text):
        raise ReviewError(f"{label} must be a SHA-256 hex digest")
    return text.lower()


def validate_upload_plan(plan: Any, support_reference: str) -> dict[str, Any]:
    plan_object = require_object(plan, "Upload deletion plan")
    if plan_object.get("schemaVersion") != 1:
        raise ReviewError("Upload deletion plan schemaVersion must be 1")
    if plan_object.get("planKind") != "communityAccountDeletionUploadHandoff":
        raise ReviewError("Upload deletion plan has the wrong planKind")
    if plan_object.get("planStatus") != "readyForOwnerAdminUploadWorkflow":
        raise ReviewError("Upload deletion execution requires a ready upload plan with no blocked rows")
    if plan_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("Upload deletion plan supportReference does not match")
    normalize_request_code(require_non_empty_string(plan_object.get("requestCode"), "Request code"))

    candidates = plan_object.get("candidates")
    if not isinstance(candidates, list):
        raise ReviewError("Upload deletion plan candidates must be a list")
    candidate_count = plan_object.get("candidateCount")
    if not isinstance(candidate_count, int) or candidate_count != len(candidates):
        raise ReviewError("Upload deletion plan candidateCount must match candidates")
    if candidate_count <= 0:
        raise ReviewError("Upload deletion execution requires at least one candidate")
    if plan_object.get("blockedCount") != 0:
        raise ReviewError("Upload deletion execution requires blockedCount 0")

    seen: set[tuple[str, str]] = set()
    for candidate in candidates:
        candidate_object = require_object(candidate, "Upload deletion plan candidate")
        key = (
            require_non_empty_string(candidate_object.get("root"), "Candidate root"),
            require_non_empty_string(candidate_object.get("uploadId"), "Candidate uploadId"),
        )
        if key in seen:
            raise ReviewError("Upload deletion plan contains duplicate candidates")
        seen.add(key)
        for field in ("metadataPath", "ownerIndexPath", "deletionTombstonePath", "storagePath", "contentType"):
            require_non_empty_string(candidate_object.get(field), f"Candidate {field}")
    return plan_object


def candidate_key(row: dict[str, Any]) -> tuple[str, str]:
    return (
        require_non_empty_string(row.get("root"), "Upload row root"),
        require_non_empty_string(row.get("uploadId"), "Upload row uploadId"),
    )


def require_true(value: Any, label: str) -> None:
    if value is not True:
        raise ReviewError(f"{label} must be true")


def validate_deleted_upload_row(row: Any, candidate: dict[str, Any]) -> dict[str, Any]:
    row_object = require_object(row, "Deleted upload row")
    for field in ("root", "uploadId", "contentType", "metadataPath", "ownerIndexPath", "deletionTombstonePath", "storagePath"):
        if row_object.get(field) != candidate.get(field):
            raise ReviewError(f"Deleted upload row {field} does not match the plan candidate")
    for field in ("storageDeleted", "metadataDeleted", "ownerIndexDeleted", "tombstoneWritten"):
        require_true(row_object.get(field), f"Deleted upload row {field}")
    require_non_empty_string(row_object.get("privateEvidenceReference"), "Deleted upload privateEvidenceReference")
    require_sha256(row_object.get("privateEvidenceHash"), "Deleted upload privateEvidenceHash")
    return row_object


def validate_upload_execution_evidence(
    evidence: Any,
    plan: dict[str, Any],
    support_reference: str,
) -> dict[str, Any]:
    evidence_object = require_object(evidence, "Upload execution evidence")
    if evidence_object.get("schemaVersion") != 1:
        raise ReviewError("Upload execution evidence schemaVersion must be 1")
    if evidence_object.get("evidenceKind") != "communityAccountDeletionUploadExecution":
        raise ReviewError("Upload execution evidence has the wrong evidenceKind")
    if evidence_object.get("executionStatus") != "completed":
        raise ReviewError("Upload execution evidence executionStatus must be completed")
    if evidence_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("Upload execution evidence supportReference does not match")

    request_code = normalize_request_code(
        require_non_empty_string(evidence_object.get("requestCode"), "Upload execution evidence requestCode")
    )
    if request_code != plan.get("requestCode"):
        raise ReviewError("Upload execution evidence requestCode does not match the upload plan")

    method = require_non_empty_string(evidence_object.get("method"), "Upload execution method")
    if method not in VALID_METHODS:
        raise ReviewError(f"Upload execution method must be one of: {', '.join(sorted(VALID_METHODS))}")
    for field in ("projectId", "executedBy", "executedAt", "ownerApprovalReference"):
        require_non_empty_string(evidence_object.get(field), f"Upload execution evidence {field}")

    candidates = {candidate_key(candidate): candidate for candidate in plan["candidates"]}
    deleted_uploads = evidence_object.get("deletedUploads")
    if not isinstance(deleted_uploads, list):
        raise ReviewError("Upload execution evidence deletedUploads must be a list")
    if len(deleted_uploads) != len(candidates):
        raise ReviewError("Upload execution evidence deletedUploads count must match plan candidates")

    seen: set[tuple[str, str]] = set()
    for raw_row in deleted_uploads:
        row_object = require_object(raw_row, "Deleted upload row")
        key = candidate_key(row_object)
        if key in seen:
            raise ReviewError("Upload execution evidence contains duplicate deleted upload rows")
        if key not in candidates:
            raise ReviewError("Upload execution evidence contains a row that is not in the plan")
        seen.add(key)
        validate_deleted_upload_row(row_object, candidates[key])

    missing = sorted(set(candidates).difference(seen))
    if missing:
        raise ReviewError("Upload execution evidence is missing planned upload rows")
    return evidence_object


def redacted_upload_receipt_rows(deleted_uploads: list[Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for row in deleted_uploads:
        row_object = require_object(row, "Deleted upload row")
        rows.append(
            {
                "contentType": row_object.get("contentType"),
                "uploadRefHash": sha256_json(
                    {
                        "root": row_object.get("root"),
                        "uploadId": row_object.get("uploadId"),
                        "metadataPath": row_object.get("metadataPath"),
                        "storagePath": row_object.get("storagePath"),
                    }
                ),
                "privateEvidenceReferenceHash": sha256_text(
                    require_non_empty_string(
                        row_object.get("privateEvidenceReference"), "Deleted upload privateEvidenceReference"
                    )
                ),
                "privateEvidenceHash": require_sha256(row_object.get("privateEvidenceHash"), "Deleted upload privateEvidenceHash"),
            }
        )
    return sorted(rows, key=lambda item: (str(item["contentType"]), str(item["uploadRefHash"])))


def build_upload_execution_receipt(
    upload_plan: Any,
    execution_evidence: Any,
    support_reference: str,
    receipted_at: str | None = None,
) -> dict[str, Any]:
    plan = validate_upload_plan(upload_plan, support_reference)
    evidence = validate_upload_execution_evidence(execution_evidence, plan, support_reference)
    project_id = require_non_empty_string(evidence.get("projectId"), "Upload execution evidence projectId")
    deleted_uploads = evidence.get("deletedUploads", [])

    return {
        "schemaVersion": 1,
        "receiptKind": "communityAccountDeletionUploadExecution",
        "executionStatus": "uploadsDeleted",
        "requestCode": plan.get("requestCode"),
        "supportReference": plan.get("supportReference"),
        "uidKeySuffix": plan.get("uidKeySuffix"),
        "method": evidence.get("method"),
        "projectIdHash": sha256_text(project_id),
        "executedAt": evidence.get("executedAt"),
        "executedBy": evidence.get("executedBy"),
        "ownerApprovalReference": evidence.get("ownerApprovalReference"),
        "deletedUploadCount": len(deleted_uploads),
        "storageDeleteCount": len(deleted_uploads),
        "metadataDeleteCount": len(deleted_uploads),
        "ownerIndexDeleteCount": len(deleted_uploads),
        "tombstoneCount": len(deleted_uploads),
        "uploadPlanHash": sha256_json(plan),
        "executionEvidenceHash": sha256_json(evidence),
        "deletedUploads": redacted_upload_receipt_rows(deleted_uploads),
        "receiptedAt": receipted_at or utc_now(),
        "privacyNote": "This receipt omits full Firebase UIDs, raw upload IDs, RTDB paths, Storage paths, project ID, command output, credentials, and tokens.",
    }


def validate_upload_execution_receipt(receipt: Any, support_reference: str) -> dict[str, Any]:
    receipt_object = require_object(receipt, "Upload execution receipt")
    if receipt_object.get("schemaVersion") != 1:
        raise ReviewError("Upload execution receipt schemaVersion must be 1")
    if receipt_object.get("receiptKind") != "communityAccountDeletionUploadExecution":
        raise ReviewError("Upload execution receipt has the wrong receiptKind")
    if receipt_object.get("executionStatus") != "uploadsDeleted":
        raise ReviewError("Upload execution receipt must be uploadsDeleted")
    if receipt_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("Upload execution receipt supportReference does not match")
    normalize_request_code(require_non_empty_string(receipt_object.get("requestCode"), "Request code"))
    for field in (
        "uidKeySuffix",
        "method",
        "projectIdHash",
        "executedAt",
        "executedBy",
        "ownerApprovalReference",
        "uploadPlanHash",
        "executionEvidenceHash",
        "receiptedAt",
    ):
        require_non_empty_string(receipt_object.get(field), f"Upload execution receipt {field}")
    deleted_count = receipt_object.get("deletedUploadCount")
    deleted_uploads = receipt_object.get("deletedUploads")
    if not isinstance(deleted_count, int) or deleted_count <= 0:
        raise ReviewError("Upload execution receipt deletedUploadCount must be positive")
    if not isinstance(deleted_uploads, list) or len(deleted_uploads) != deleted_count:
        raise ReviewError("Upload execution receipt deletedUploads count must match")
    for field in ("storageDeleteCount", "metadataDeleteCount", "ownerIndexDeleteCount", "tombstoneCount"):
        if receipt_object.get(field) != deleted_count:
            raise ReviewError(f"Upload execution receipt {field} must match deletedUploadCount")
    for row in deleted_uploads:
        row_object = require_object(row, "Upload execution receipt deletedUpload")
        require_non_empty_string(row_object.get("contentType"), "Receipt deleted upload contentType")
        require_non_empty_string(row_object.get("uploadRefHash"), "Receipt deleted upload uploadRefHash")
        require_non_empty_string(row_object.get("privateEvidenceReferenceHash"), "Receipt deleted upload privateEvidenceReferenceHash")
        require_sha256(row_object.get("privateEvidenceHash"), "Receipt deleted upload privateEvidenceHash")
    return receipt_object


def dump_upload_execution_receipt(receipt: dict[str, Any]) -> str:
    return json.dumps(receipt, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        receipt = build_upload_execution_receipt(
            read_json(Path(args.upload_plan)),
            read_json(Path(args.execution_evidence)),
            args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    receipt_text = dump_upload_execution_receipt(receipt)
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
