#!/usr/bin/env python3
"""Build the local/Auth cleanup sequence after backend account deletion."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import (
    ReviewError,
    require_non_empty_string,
    require_object,
    sha256_json,
    utc_now,
)
from tools.community_deletion_request_lookup import normalize_request_code


CLEANUP_STEPS = [
    {
        "order": 1,
        "owner": "requester",
        "action": "Clear Aura app data, uninstall/reinstall Aura, or use the app cache cleanup surface after support confirms backend completion.",
        "scope": "Local fallback identity, local community cache, and offline app artifacts on the requester device.",
        "status": "manualRequesterAction",
    },
    {
        "order": 2,
        "owner": "operator",
        "action": "Delete the Firebase Authentication user only after backend marker deletion is complete and the private UID has been reverified.",
        "scope": "Firebase Authentication account record for the verified private UID.",
        "status": "requiresPrivateUidAndOwnerAccess",
    },
    {
        "order": 3,
        "owner": "operator",
        "action": "Route public uploads through the owner/admin upload deletion workflow when the requester asks to remove upload content.",
        "scope": "Public upload metadata, owner-upload indexes, Storage objects, and upload deletion tombstones.",
        "status": "separateUploadDeletionWorkflow",
    },
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build local/Auth cleanup sequencing after an Aura account deletion completion receipt."
    )
    parser.add_argument("--completion-receipt", required=True, help="JSON output from community_account_deletion_completion_receipt.py.")
    parser.add_argument("--support-reference", required=True, help="User-safe ticket or support reference label.")
    parser.add_argument("--output", help="Optional cleanup sequence path. Defaults to stdout.")
    return parser.parse_args()


def validate_completion_receipt(receipt: Any, support_reference: str) -> dict[str, Any]:
    receipt_object = require_object(receipt, "Account deletion completion receipt")
    if receipt_object.get("schemaVersion") != 1:
        raise ReviewError("Account deletion completion receipt schemaVersion must be 1")
    if receipt_object.get("receiptKind") != "communityAccountDeletionCompletion":
        raise ReviewError("Account deletion completion receipt has the wrong receiptKind")
    if receipt_object.get("completionStatus") != "completed":
        raise ReviewError("Account deletion cleanup requires a completed backend receipt")

    normalized_code = normalize_request_code(
        require_non_empty_string(receipt_object.get("requestCode"), "Request code")
    )
    if receipt_object.get("requestCode") != normalized_code:
        raise ReviewError("Account deletion completion receipt requestCode is not normalized")

    expected_support_reference = require_non_empty_string(support_reference, "Support reference")
    if receipt_object.get("supportReference") != expected_support_reference:
        raise ReviewError("Account deletion completion receipt supportReference does not match")

    marker_count = receipt_object.get("deletedRtdbMarkerCount")
    if not isinstance(marker_count, int) or marker_count < 0:
        raise ReviewError("Account deletion completion receipt deletedRtdbMarkerCount must be a non-negative integer")

    for field in ("planHash", "updatesHash", "packageHash", "restReceiptHash", "payloadHash", "databaseHostHash"):
        require_non_empty_string(receipt_object.get(field), f"Account deletion completion receipt {field}")
    require_non_empty_string(receipt_object.get("completedAt"), "Account deletion completion receipt completedAt")
    return receipt_object


def build_cleanup_sequence(
    completion_receipt: Any,
    support_reference: str,
    sequenced_at: str | None = None,
) -> dict[str, Any]:
    receipt = validate_completion_receipt(completion_receipt, support_reference)
    return {
        "schemaVersion": 1,
        "sequenceKind": "communityAccountDeletionLocalAuthCleanup",
        "sequenceStatus": "readyForLocalAndAuthCleanup",
        "requestCode": receipt.get("requestCode"),
        "supportReference": receipt.get("supportReference"),
        "uidKeySuffix": receipt.get("uidKeySuffix"),
        "backendCompletedAt": receipt.get("completedAt"),
        "sequencedAt": sequenced_at or utc_now(),
        "deletedRtdbMarkerCount": receipt.get("deletedRtdbMarkerCount"),
        "completionReceiptHash": sha256_json(receipt),
        "steps": CLEANUP_STEPS,
        "privacyNote": "This sequence omits full Firebase UIDs, RTDB paths, database hosts, update payloads, requester contact, and access tokens.",
    }


def dump_cleanup_sequence(sequence: dict[str, Any]) -> str:
    return json.dumps(sequence, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        sequence = build_cleanup_sequence(
            read_json(Path(args.completion_receipt)),
            args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    sequence_text = dump_cleanup_sequence(sequence)
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(sequence_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(sequence_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
