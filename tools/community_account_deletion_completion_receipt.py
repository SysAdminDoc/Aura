#!/usr/bin/env python3
"""Build a user-safe completion receipt for account deletion requests."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_executor_package import validate_executor_package
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


RETAINED_DATA_CATEGORIES = [
    "Aggregate vote counts after per-user markers are removed.",
    "Private moderation, rights, safety, and abuse-prevention records.",
    "Public upload metadata and Storage objects until the owner/admin upload deletion workflow handles them.",
]

COMPLETED_ACTIONS = [
    "Removed reviewed Realtime Database community identity marker paths through the guarded executor.",
]

REMAINING_OPERATOR_ACTIONS = [
    "Review public uploads and Storage objects through the owner/admin upload deletion workflow.",
    "Handle Firebase Authentication deletion only after the trusted orchestrator owns final sequencing.",
    "Leave local app cache and fallback identity cleanup to the user's device or a future in-app cleanup flow.",
]

USER_NEXT_STEPS = [
    "If Aura is still installed, clear local app data or reinstall after support confirms completion.",
    "If public uploads should also be removed, submit the upload URLs or IDs through the support channel.",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a redacted Aura account deletion completion receipt."
    )
    parser.add_argument("--package", required=True, help="Private executor package JSON.")
    parser.add_argument("--rest-receipt", required=True, help="Apply receipt from community_account_deletion_rest_executor.py.")
    parser.add_argument("--request-code", required=True, help="AURA- request code from the user request.")
    parser.add_argument("--support-reference", required=True, help="User-safe ticket or support reference label.")
    parser.add_argument("--output", help="Optional completion receipt path. Defaults to stdout.")
    return parser.parse_args()


def validate_rest_apply_receipt(rest_receipt: Any, package_object: dict[str, Any]) -> dict[str, Any]:
    receipt = require_object(rest_receipt, "Account deletion REST receipt")
    if receipt.get("schemaVersion") != 1:
        raise ReviewError("Account deletion REST receipt schemaVersion must be 1")
    if receipt.get("executionMode") != "apply" or receipt.get("executionStatus") != "applied":
        raise ReviewError("Account deletion completion requires an applied REST receipt")
    if receipt.get("httpStatus") != 200:
        raise ReviewError("Account deletion REST receipt must have httpStatus 200")
    if receipt.get("requestCode") != package_object.get("requestCode"):
        raise ReviewError("Account deletion REST receipt requestCode does not match the package")
    if receipt.get("updateCount") != package_object.get("updateCount"):
        raise ReviewError("Account deletion REST receipt updateCount does not match the package")
    if receipt.get("updatesHash") != package_object.get("updatesHash"):
        raise ReviewError("Account deletion REST receipt updatesHash does not match the package")
    if receipt.get("planHash") != package_object.get("planHash"):
        raise ReviewError("Account deletion REST receipt planHash does not match the package")
    if receipt.get("packageHash") != sha256_json(package_object):
        raise ReviewError("Account deletion REST receipt packageHash does not match the package")

    require_non_empty_string(receipt.get("payloadHash"), "Account deletion REST receipt payloadHash")
    require_non_empty_string(receipt.get("responseHash"), "Account deletion REST receipt responseHash")
    require_non_empty_string(receipt.get("databaseHost"), "Account deletion REST receipt databaseHost")
    require_non_empty_string(receipt.get("executedAt"), "Account deletion REST receipt executedAt")
    return receipt


def build_completion_receipt(
    package: Any,
    rest_receipt: Any,
    request_code: str,
    support_reference: str,
    completed_at: str | None = None,
) -> dict[str, Any]:
    package_object = validate_executor_package(package)
    normalized_code = normalize_request_code(request_code)
    if package_object.get("requestCode") != normalized_code:
        raise ReviewError("Account deletion package requestCode does not match the completion request code")

    receipt = validate_rest_apply_receipt(rest_receipt, package_object)
    support_reference_value = require_non_empty_string(support_reference, "Support reference")
    database_host = require_non_empty_string(receipt.get("databaseHost"), "Account deletion REST receipt databaseHost")

    return {
        "schemaVersion": 1,
        "receiptKind": "communityAccountDeletionCompletion",
        "completionStatus": "completed",
        "requestCode": normalized_code,
        "supportReference": support_reference_value,
        "uidKeySuffix": package_object.get("uidKeySuffix"),
        "deletedRtdbMarkerCount": package_object.get("updateCount"),
        "completedAt": completed_at or utc_now(),
        "executedAt": receipt.get("executedAt"),
        "planHash": package_object.get("planHash"),
        "updatesHash": package_object.get("updatesHash"),
        "packageHash": receipt.get("packageHash"),
        "restReceiptHash": sha256_json(receipt),
        "payloadHash": receipt.get("payloadHash"),
        "databaseHostHash": sha256_text(database_host),
        "retainedDataCategories": RETAINED_DATA_CATEGORIES,
        "completedActions": COMPLETED_ACTIONS,
        "remainingOperatorActions": REMAINING_OPERATOR_ACTIONS,
        "userNextSteps": USER_NEXT_STEPS,
        "privacyNote": "This user-safe receipt omits full Firebase UIDs, database hosts, RTDB paths, update payloads, and access tokens.",
    }


def dump_completion_receipt(receipt: dict[str, Any]) -> str:
    return json.dumps(receipt, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        completion = build_completion_receipt(
            read_json(Path(args.package)),
            read_json(Path(args.rest_receipt)),
            args.request_code,
            args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    receipt_text = dump_completion_receipt(completion)
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
