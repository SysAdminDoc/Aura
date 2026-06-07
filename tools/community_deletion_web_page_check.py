#!/usr/bin/env python3
"""Validate the hosted account deletion web page content draft."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


REQUIRED_TERMS = {
    "brand": "Aura",
    "request_code": "AURA-",
    "delete_identity": "community identity",
    "associated_data": "associated community data",
    "retained_records": "retained",
    "public_uploads": "public uploads",
    "privacy_policy": "privacy policy",
}

REQUIRED_FORM_FIELDS = [
    "requestCode",
    "contact",
    "requesterStatement",
    "attestations.deleteCommunityIdentity",
    "attestations.understandsRetainedRecords",
    "attestations.understandsPublicUploadsSeparate",
]

FORBIDDEN_PATTERNS = [
    re.compile(r"firebase\s+uid", re.IGNORECASE),
    re.compile(r"\buid\b", re.IGNORECASE),
    re.compile(r"access\s+token", re.IGNORECASE),
    re.compile(r"database\s+export", re.IGNORECASE),
    re.compile(r"password", re.IGNORECASE),
]


class WebPageCheckError(ValueError):
    """Raised when the hosted deletion page draft is incomplete or unsafe."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura hosted deletion web page copy.")
    parser.add_argument("--page", default="docs/support/community-account-deletion-web-page.md")
    return parser.parse_args()


def validate_web_page(text: str) -> dict[str, Any]:
    if not text.strip():
        raise WebPageCheckError("Hosted deletion page must not be empty")

    lower_text = text.lower()
    missing_terms = [
        label
        for label, term in REQUIRED_TERMS.items()
        if term.lower() not in lower_text
    ]
    if missing_terms:
        raise WebPageCheckError(f"Hosted deletion page missing required terms: {', '.join(missing_terms)}")

    missing_fields = [field for field in REQUIRED_FORM_FIELDS if field not in text]
    if missing_fields:
        raise WebPageCheckError(f"Hosted deletion page missing form fields: {', '.join(missing_fields)}")

    forbidden_matches = []
    for pattern in FORBIDDEN_PATTERNS:
        match = pattern.search(text)
        if match:
            forbidden_matches.append(match.group(0))
    if forbidden_matches:
        raise WebPageCheckError(
            "Hosted deletion page must not request sensitive identifiers or secrets: "
            + ", ".join(sorted(set(forbidden_matches)))
        )

    if "must not send users back to the app" not in lower_text:
        raise WebPageCheckError("Hosted deletion page must state that app access is not required")

    return {
        "schemaVersion": 1,
        "pageKind": "communityAccountDeletionHostedPage",
        "status": "readyForOwnerPublication",
        "requiredFieldCount": len(REQUIRED_FORM_FIELDS),
        "requiredTermCount": len(REQUIRED_TERMS),
    }


def main() -> int:
    args = parse_args()
    try:
        result = validate_web_page(Path(args.page).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
