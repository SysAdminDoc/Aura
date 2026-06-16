#!/usr/bin/env python3
"""Build a trusted dry-run bundle for Aura community deletion requests."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

REPO_ROOT = Path(__file__).resolve().parents[1]
if str(REPO_ROOT) not in sys.path:
    sys.path.insert(0, str(REPO_ROOT))

from tools.community_account_deletion_apply_simulator import simulate_account_deletion_apply
from tools.community_account_deletion_executor_package import build_executor_package
from tools.community_account_deletion_plan import (
    build_account_deletion_plan,
    read_json,
    sanitize_key,
)
from tools.community_account_deletion_rest_executor import execute_package
from tools.community_account_deletion_review import (
    ReviewError,
    redact_uid_key,
    require_non_empty_string,
    require_object,
    review_account_deletion_request,
    sha256_json,
    sha256_text,
    utc_now,
    validate_lookup,
)
from tools.community_account_deletion_upload_plan import (
    UPLOAD_ROOTS,
    build_upload_candidate,
    matches_owner,
)
from tools.community_deletion_request_lookup import (
    deletion_request_code,
    lookup_deletion_request,
    normalize_request_code,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build an Aura community deletion trusted dry-run bundle."
    )
    parser.add_argument("--database-export", required=True, help="JSON export containing community RTDB roots.")
    parser.add_argument("--request-code", required=True, help="Verified AURA- deletion request code.")
    parser.add_argument("--database-url", required=True, help="Realtime Database root URL used for the REST dry-run receipt.")
    parser.add_argument("--operator", required=True, help="Private operator initials or ticket handle.")
    parser.add_argument("--support-reference", required=True, help="User-safe support ticket/reference label.")
    parser.add_argument("--output", help="Optional private orchestration bundle path. Defaults to stdout.")
    parser.add_argument("--requester-receipt-output", help="Optional requester-safe JSON receipt path.")
    parser.add_argument("--requester-receipt-text-output", help="Optional requester-safe human-readable receipt path.")
    return parser.parse_args()


def owner_upload_index_paths(database: dict[str, Any], safe_uid: str) -> list[str]:
    owner_root = database.get("owner_uploads", {})
    if owner_root is None:
        return []
    if not isinstance(owner_root, dict):
        raise ReviewError("owner_uploads must be a JSON object when present")

    owned = owner_root.get(safe_uid, {})
    if owned is None:
        return []
    if not isinstance(owned, dict):
        raise ReviewError("owner_uploads user row must be a JSON object when present")

    paths: list[str] = []
    for owner_type, raw_uploads in sorted(owned.items()):
        if not isinstance(raw_uploads, dict):
            continue
        safe_owner_type = sanitize_key(str(owner_type))
        for upload_id in sorted(raw_uploads):
            paths.append(f"/owner_uploads/{safe_uid}/{safe_owner_type}/{sanitize_key(str(upload_id))}")
    return paths


def build_upload_inventory(
    database_export: Any,
    uid: str,
    request_code: str,
    support_reference: str,
    inventoried_at: str | None = None,
) -> dict[str, Any]:
    database = require_object(database_export, "Database export")
    normalized_code = normalize_request_code(request_code)
    full_uid = require_non_empty_string(uid, "Lookup match uid")
    if deletion_request_code(full_uid) != normalized_code:
        raise ReviewError("Lookup match uid does not derive the requested deletion code")

    support_reference_value = require_non_empty_string(support_reference, "Support reference")
    safe_uid = sanitize_key(full_uid)
    if not safe_uid:
        raise ReviewError("Lookup match safe uid is empty")

    candidates: list[dict[str, Any]] = []
    blocked: list[dict[str, Any]] = []
    for root, config in sorted(UPLOAD_ROOTS.items()):
        records = database.get(root, {})
        if records is None:
            continue
        if not isinstance(records, dict):
            raise ReviewError(f"{root} must be a JSON object when present")
        expected_prefix = f"{config['expectedStoragePrefix']}{safe_uid}/"
        for upload_id, raw_record in sorted(records.items()):
            if not isinstance(raw_record, dict):
                continue
            if not matches_owner(raw_record, full_uid, safe_uid):
                continue
            try:
                candidates.append(build_upload_candidate(root, str(upload_id), raw_record, full_uid, safe_uid))
            except ReviewError as exc:
                blocked.append(
                    {
                        "root": root,
                        "uploadId": sanitize_key(str(upload_id)),
                        "metadataPath": f"/{root}/{sanitize_key(str(upload_id))}",
                        "expectedStoragePrefix": expected_prefix,
                        "reason": str(exc),
                    }
                )

    candidate_owner_paths = {candidate["ownerIndexPath"] for candidate in candidates}
    owner_paths = owner_upload_index_paths(database, safe_uid)
    orphan_owner_paths = sorted(path for path in owner_paths if path not in candidate_owner_paths)
    for owner_path in orphan_owner_paths:
        blocked.append(
            {
                "root": "owner_uploads",
                "ownerIndexPath": owner_path,
                "reason": "Owner upload index has no matching public metadata row with a valid Storage handle in this export.",
            }
        )

    storage_paths = sorted(candidate["storagePath"] for candidate in candidates)
    metadata_paths = sorted(candidate["metadataPath"] for candidate in candidates)
    owner_index_paths = sorted(candidate_owner_paths)
    status = "readyForOwnerAdminUploadWorkflow" if not blocked else "blockedUntilUploadHandlesReviewed"

    return {
        "schemaVersion": 1,
        "inventoryKind": "communityAccountDeletionUploadInventory",
        "inventoryStatus": status,
        "requestCode": normalized_code,
        "supportReference": support_reference_value,
        "uidKeySuffix": redact_uid_key(safe_uid),
        "uidKeyHash": sha256_text(safe_uid),
        "inventoriedAt": inventoried_at or utc_now(),
        "candidateCount": len(candidates),
        "blockedCount": len(blocked),
        "storageDeleteCount": len(storage_paths),
        "ownerIndexCount": len(owner_paths),
        "orphanOwnerIndexCount": len(orphan_owner_paths),
        "storagePathHash": sha256_json(storage_paths),
        "metadataPathHash": sha256_json(metadata_paths),
        "ownerIndexPathHash": sha256_json(owner_index_paths),
        "blockedHash": sha256_json(blocked),
        "candidates": candidates,
        "blocked": blocked,
        "executorWarning": "Private inventory: contains Storage object paths and owner indexes. Do not publish; use only through the owner/admin upload deletion workflow.",
    }


def build_nonpublic_audit_receipt(
    support_reference: str,
    operator: str,
    request_code: str,
    review: dict[str, Any],
    simulation: dict[str, Any],
    executor_package: dict[str, Any],
    rest_dry_run: dict[str, Any],
    upload_inventory: dict[str, Any],
    orchestrated_at: str,
) -> dict[str, Any]:
    database_host = require_non_empty_string(rest_dry_run.get("databaseHost"), "Database host")
    return {
        "schemaVersion": 1,
        "receiptKind": "communityDeletionOrchestratorAudit",
        "receiptStatus": "dryRunReady",
        "supportReference": support_reference,
        "operator": operator,
        "requestCode": request_code,
        "uidKeySuffix": review.get("uidKeySuffix"),
        "uidKeyHash": review.get("uidKeyHash"),
        "rtdbUpdateCount": executor_package.get("updateCount"),
        "rtdbPlanHash": review.get("planHash"),
        "simulationHash": sha256_json(simulation),
        "executorPackageHash": sha256_json(executor_package),
        "restDryRunHash": sha256_json(rest_dry_run),
        "uploadInventoryHash": sha256_json(upload_inventory),
        "databaseHostHash": sha256_text(database_host),
        "orchestratedAt": orchestrated_at,
    }


def build_requester_safe_receipt(
    support_reference: str,
    request_code: str,
    review: dict[str, Any],
    simulation: dict[str, Any],
    upload_inventory: dict[str, Any],
    orchestrated_at: str,
) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "receiptKind": "communityDeletionRequesterPreview",
        "receiptStatus": "supportReviewReady",
        "supportReference": support_reference,
        "requestCode": request_code,
        "identitySuffix": review.get("uidKeySuffix"),
        "rtdbDeletionMarkerCount": review.get("updateCount"),
        "rtdbDryRunStatus": simulation.get("simulationStatus"),
        "publicUploadReadyCount": upload_inventory.get("candidateCount"),
        "publicUploadReviewCount": upload_inventory.get("blockedCount"),
        "authDeletionStatus": "requires owner-approved Firebase Auth deletion after backend completion",
        "publicUploadStatus": "requires owner/admin upload deletion workflow for Storage objects and public metadata",
        "preparedAt": orchestrated_at,
        "nextStep": "Support must apply the verified backend deletion, complete Auth/upload follow-up when approved, then issue a final completion receipt.",
    }


def format_requester_receipt(receipt: dict[str, Any]) -> str:
    lines = [
        "Aura community deletion request receipt",
        f"Support reference: {receipt.get('supportReference')}",
        f"Request code: {receipt.get('requestCode')}",
        f"Status: {receipt.get('receiptStatus')}",
        f"Identity suffix: {receipt.get('identitySuffix')}",
        f"Backend deletion markers prepared: {receipt.get('rtdbDeletionMarkerCount')}",
        f"Backend dry-run status: {receipt.get('rtdbDryRunStatus')}",
        f"Public uploads ready for owner/admin workflow: {receipt.get('publicUploadReadyCount')}",
        f"Public uploads needing handle review: {receipt.get('publicUploadReviewCount')}",
        f"Auth deletion: {receipt.get('authDeletionStatus')}",
        f"Prepared at: {receipt.get('preparedAt')}",
        f"Next step: {receipt.get('nextStep')}",
    ]
    return "\n".join(lines) + "\n"


def orchestration_status(upload_inventory: dict[str, Any]) -> str:
    if upload_inventory.get("blockedCount", 0) > 0:
        return "readyForTrustedRtdbApplyWithUploadReview"
    return "readyForTrustedRtdbApply"


def build_deletion_orchestration(
    database_export: Any,
    request_code: str,
    database_url: str,
    operator: str,
    support_reference: str,
    orchestrated_at: str | None = None,
) -> dict[str, Any]:
    database = require_object(database_export, "Database export")
    normalized_code = normalize_request_code(request_code)
    operator_label = require_non_empty_string(operator, "Operator")
    support_reference_value = require_non_empty_string(support_reference, "Support reference")
    timestamp = orchestrated_at or utc_now()

    lookup = lookup_deletion_request(database, normalized_code)
    match = validate_lookup(lookup, normalized_code)
    uid = require_non_empty_string(match.get("uid"), "Lookup match uid")

    plan = build_account_deletion_plan(database, uid)
    review = review_account_deletion_request(lookup, plan, normalized_code, reviewed_at=timestamp)
    simulation, _snapshot = simulate_account_deletion_apply(database, plan, review, simulated_at=timestamp)
    executor_package = build_executor_package(
        plan,
        review,
        simulation,
        normalized_code,
        operator=operator_label,
        packaged_at=timestamp,
    )
    rest_dry_run = execute_package(
        executor_package,
        database_url,
        mode="dry-run",
        executed_at=timestamp,
    )
    upload_inventory = build_upload_inventory(
        database,
        uid,
        normalized_code,
        support_reference_value,
        inventoried_at=timestamp,
    )
    requester_receipt = build_requester_safe_receipt(
        support_reference_value,
        normalized_code,
        review,
        simulation,
        upload_inventory,
        timestamp,
    )
    nonpublic_audit_receipt = build_nonpublic_audit_receipt(
        support_reference_value,
        operator_label,
        normalized_code,
        review,
        simulation,
        executor_package,
        rest_dry_run,
        upload_inventory,
        timestamp,
    )

    return {
        "schemaVersion": 1,
        "orchestrationKind": "communityDeletionTrustedDryRun",
        "orchestrationStatus": orchestration_status(upload_inventory),
        "requestCode": normalized_code,
        "supportReference": support_reference_value,
        "operator": operator_label,
        "orchestratedAt": timestamp,
        "uidKeySuffix": review.get("uidKeySuffix"),
        "uidKeyHash": review.get("uidKeyHash"),
        "rtdb": {
            "executionMode": "dry-run",
            "executionStatus": rest_dry_run.get("executionStatus"),
            "updateCount": executor_package.get("updateCount"),
            "planHash": review.get("planHash"),
            "updatesHash": executor_package.get("updatesHash"),
            "payloadHash": rest_dry_run.get("payloadHash"),
            "dryRunReceiptHash": sha256_json(rest_dry_run),
            "nextRequiredGate": rest_dry_run.get("nextRequiredGate"),
        },
        "uploads": {
            "inventoryStatus": upload_inventory.get("inventoryStatus"),
            "candidateCount": upload_inventory.get("candidateCount"),
            "blockedCount": upload_inventory.get("blockedCount"),
            "storageDeleteCount": upload_inventory.get("storageDeleteCount"),
            "storagePathHash": upload_inventory.get("storagePathHash"),
            "metadataPathHash": upload_inventory.get("metadataPathHash"),
            "ownerIndexPathHash": upload_inventory.get("ownerIndexPathHash"),
            "blockedHash": upload_inventory.get("blockedHash"),
        },
        "authDeletion": {
            "status": "requiresBackendCompletionAndOwnerAuthDeletion",
            "requestCode": normalized_code,
            "uidKeySuffix": review.get("uidKeySuffix"),
            "uidKeyHash": review.get("uidKeyHash"),
            "nextRequiredGate": "Build the private Auth deletion package only after an applied backend completion receipt is archived.",
        },
        "postDeleteChecks": {
            "simulatedRtdbUpdatePathsRemaining": simulation.get("remainingUpdatePathCount"),
            "simulatedDeletedPathCount": simulation.get("deletedPathCount"),
            "publicUploadsRequireSeparateWorkflow": upload_inventory.get("candidateCount", 0) + upload_inventory.get("blockedCount", 0),
            "snapshotHash": simulation.get("snapshotHash"),
        },
        "retryPlan": {
            "rtdbApply": "Re-run the guarded REST executor with the same package, matching request-code and plan-hash confirmations, and a fresh OAuth2 token.",
            "authDelete": "Rebuild Auth execution evidence from the private Auth package after backend completion; do not expose the full UID in requester receipts.",
            "uploadDelete": "Retry each owner/admin upload deletion candidate independently and record blocked handle review before public metadata or Storage deletes.",
        },
        "nonpublicAuditReceipt": nonpublic_audit_receipt,
        "requesterSafeReceipt": requester_receipt,
        "privateArtifacts": {
            "lookup": lookup,
            "deletionPlan": plan,
            "review": review,
            "simulationReceipt": simulation,
            "executorPackage": executor_package,
            "restDryRunReceipt": rest_dry_run,
            "uploadInventory": upload_inventory,
        },
        "executorWarning": "Private orchestration bundle: contains full UID-derived evidence, RTDB update paths, Storage handles, and database labels. Do not publish.",
    }


def dump_orchestration(orchestration: dict[str, Any]) -> str:
    return json.dumps(orchestration, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        orchestration = build_deletion_orchestration(
            read_json(Path(args.database_export)),
            args.request_code,
            args.database_url,
            operator=args.operator,
            support_reference=args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    if args.requester_receipt_output:
        output = Path(args.requester_receipt_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(
            json.dumps(orchestration["requesterSafeReceipt"], indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        print(f"wrote {output}")

    if args.requester_receipt_text_output:
        output = Path(args.requester_receipt_text_output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(format_requester_receipt(orchestration["requesterSafeReceipt"]), encoding="utf-8")
        print(f"wrote {output}")

    orchestration_text = dump_orchestration(orchestration)
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(orchestration_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(orchestration_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
