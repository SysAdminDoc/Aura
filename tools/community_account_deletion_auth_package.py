#!/usr/bin/env python3
"""Build a private Firebase Auth deletion package after backend completion."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_cleanup_sequence import validate_completion_receipt
from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import (
    ReviewError,
    redact_uid_key,
    require_non_empty_string,
    sha256_json,
    sha256_text,
    utc_now,
    validate_lookup,
)
from tools.community_deletion_request_lookup import deletion_request_code, normalize_request_code


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a private Aura Firebase Auth deletion package after backend account deletion completion."
    )
    parser.add_argument("--lookup", required=True, help="JSON output from community_deletion_request_lookup.py.")
    parser.add_argument("--completion-receipt", required=True, help="JSON output from community_account_deletion_completion_receipt.py.")
    parser.add_argument("--request-code", required=True, help="AURA- request code from the user request.")
    parser.add_argument("--support-reference", required=True, help="User-safe ticket or support reference label.")
    parser.add_argument("--operator", required=True, help="Private operator initials or ticket handle.")
    parser.add_argument("--output", help="Optional private auth deletion package path. Defaults to stdout.")
    return parser.parse_args()


def build_auth_deletion_package(
    lookup: Any,
    completion_receipt: Any,
    request_code: str,
    support_reference: str,
    operator: str,
    packaged_at: str | None = None,
) -> dict[str, Any]:
    normalized_code = normalize_request_code(request_code)
    match = validate_lookup(lookup, normalized_code)
    completion = validate_completion_receipt(completion_receipt, support_reference)
    if completion.get("requestCode") != normalized_code:
        raise ReviewError("Account deletion completion receipt requestCode does not match the auth package request code")

    uid = require_non_empty_string(match.get("uid"), "Lookup match uid")
    safe_uid = require_non_empty_string(match.get("safeUid"), "Lookup match safeUid")
    if deletion_request_code(uid) != normalized_code:
        raise ReviewError("Lookup match uid does not derive the requested deletion code")

    operator_label = require_non_empty_string(operator, "Operator")
    support_reference_value = require_non_empty_string(support_reference, "Support reference")
    evidence = match.get("evidence")
    if not isinstance(evidence, list) or not evidence:
        raise ReviewError("Lookup match evidence must be a non-empty list")

    return {
        "schemaVersion": 1,
        "packageKind": "communityAccountDeletionAuthDelete",
        "packageStatus": "readyForAuthDeletion",
        "requestCode": normalized_code,
        "supportReference": support_reference_value,
        "operator": operator_label,
        "packagedAt": packaged_at or utc_now(),
        "uid": uid,
        "safeUid": safe_uid,
        "uidHash": sha256_text(uid),
        "uidKeySuffix": redact_uid_key(safe_uid),
        "completionReceiptHash": sha256_json(completion),
        "lookupEvidenceHash": sha256_json(
            {
                "requestCode": normalized_code,
                "safeUid": safe_uid,
                "evidence": sorted(evidence),
            }
        ),
        "deleteCommandTemplate": "Delete this Firebase Authentication user with the owner-approved Firebase Console, Admin SDK, or CLI for the production project.",
        "executorWarning": "Private package: contains the full Firebase UID. Do not publish; use only after requester verification and backend completion evidence are archived.",
    }


def dump_auth_deletion_package(package: dict[str, Any]) -> str:
    return json.dumps(package, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        package = build_auth_deletion_package(
            read_json(Path(args.lookup)),
            read_json(Path(args.completion_receipt)),
            args.request_code,
            args.support_reference,
            args.operator,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    package_text = dump_auth_deletion_package(package)
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(package_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(package_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
