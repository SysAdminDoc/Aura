#!/usr/bin/env python3
"""Validate private web intake exports for community deletion requests."""

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
    sha256_text,
    utc_now,
)
from tools.community_deletion_request_lookup import normalize_request_code


REQUIRED_ATTESTATIONS = {
    "deleteCommunityIdentity": "Requester asked Aura support to delete their community identity data.",
    "understandsRetainedRecords": "Requester saw that private moderation, rights, safety, and abuse-prevention records may be retained.",
    "understandsPublicUploadsSeparate": "Requester saw that public uploads and Storage objects may need separate owner/admin deletion handling.",
}

ALLOWED_CHANNELS = {"private-web", "support-email", "manual-import"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate an Aura account deletion web intake export without emitting raw requester contact data."
    )
    parser.add_argument("--request", required=True, help="Private web form export JSON.")
    parser.add_argument("--support-reference", required=True, help="User-safe ticket or support reference label.")
    parser.add_argument("--output", help="Optional intake receipt path. Defaults to stdout.")
    return parser.parse_args()


def normalized_contact(contact: str) -> str:
    value = require_non_empty_string(contact, "Requester contact").lower()
    if len(value) > 320:
        raise ReviewError("Requester contact is too long")
    return value


def normalized_channel(channel: Any) -> str:
    value = require_non_empty_string(channel or "private-web", "Request channel")
    if value not in ALLOWED_CHANNELS:
        raise ReviewError(f"Request channel must be one of: {', '.join(sorted(ALLOWED_CHANNELS))}")
    return value


def validated_attestations(value: Any) -> dict[str, bool]:
    attestations = require_object(value, "Request attestations")
    normalized: dict[str, bool] = {}
    missing: list[str] = []
    for key in sorted(REQUIRED_ATTESTATIONS):
        if attestations.get(key) is not True:
            missing.append(key)
        normalized[key] = True
    if missing:
        raise ReviewError(f"Request attestations missing: {', '.join(missing)}")
    return normalized


def build_web_intake_receipt(
    request: Any,
    support_reference: str,
    received_at: str | None = None,
) -> dict[str, Any]:
    request_object = require_object(request, "Deletion web intake request")
    request_code = normalize_request_code(require_non_empty_string(request_object.get("requestCode"), "Request code"))
    contact = normalized_contact(require_non_empty_string(request_object.get("contact"), "Requester contact"))
    statement = require_non_empty_string(request_object.get("requesterStatement"), "Requester statement")
    if len(statement) < 20:
        raise ReviewError("Requester statement is too short")
    if len(statement) > 2000:
        raise ReviewError("Requester statement is too long")

    support_reference_value = require_non_empty_string(support_reference, "Support reference")
    channel = normalized_channel(request_object.get("channel", "private-web"))
    attestations = validated_attestations(request_object.get("attestations"))
    submitted_at = request_object.get("submittedAt")
    locale = request_object.get("locale", "und")

    receipt: dict[str, Any] = {
        "schemaVersion": 1,
        "receiptKind": "communityDeletionWebIntake",
        "intakeStatus": "readyForOperatorLookup",
        "requestCode": request_code,
        "supportReference": support_reference_value,
        "channel": channel,
        "contactProvided": True,
        "contactHash": sha256_text(contact),
        "requesterStatementHash": sha256_text(statement.strip()),
        "attestations": attestations,
        "attestationLabels": REQUIRED_ATTESTATIONS,
        "receivedAt": received_at or utc_now(),
        "nextRequiredGate": "Verify the requester privately, then run the request-code lookup against a current RTDB export.",
        "privacyNote": "This receipt omits raw requester contact, requester statement text, full Firebase UIDs, RTDB paths, and database exports.",
    }
    if isinstance(submitted_at, str) and submitted_at.strip():
        receipt["submittedAt"] = submitted_at.strip()
    if isinstance(locale, str) and locale.strip():
        receipt["locale"] = locale.strip()
    return receipt


def dump_web_intake_receipt(receipt: dict[str, Any]) -> str:
    return json.dumps(receipt, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        receipt = build_web_intake_receipt(
            read_json(Path(args.request)),
            args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    receipt_text = dump_web_intake_receipt(receipt)
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
