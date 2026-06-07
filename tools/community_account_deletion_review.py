#!/usr/bin/env python3
"""Review lookup and dry-run plan artifacts before trusted account deletion."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_plan import read_json, sanitize_key
from tools.community_deletion_request_lookup import normalize_request_code


REQUIRED_CATEGORY_KEYS = {
    "collectionShares",
    "communityBlocks",
    "creatorFollows",
    "creatorProfile",
    "voteMarkers",
}

REQUIRED_RETAINED_ROOTS = {
    "/votes/*/upvotes",
    "/community_reports and moderation audit roots",
    "/community_sounds, /community_wallpapers, /owner_uploads, and Storage objects",
}


class ReviewError(ValueError):
    """Raised when account deletion review artifacts are inconsistent."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Review an Aura deletion request lookup and dry-run RTDB plan."
    )
    parser.add_argument("--lookup", required=True, help="JSON output from community_deletion_request_lookup.py.")
    parser.add_argument("--plan", required=True, help="JSON output from community_account_deletion_plan.py.")
    parser.add_argument("--request-code", required=True, help="AURA- request code from the user request.")
    parser.add_argument("--output", help="Optional review receipt path. Defaults to stdout.")
    return parser.parse_args()


def canonical_json_bytes(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha256_json(value: Any) -> str:
    return hashlib.sha256(canonical_json_bytes(value)).hexdigest()


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def redact_uid_key(uid_key: str) -> str:
    cleaned = uid_key.strip()
    if not cleaned:
        return ""
    suffix = cleaned[-6:] if len(cleaned) > 6 else cleaned
    return f"...{suffix}"


def utc_now() -> str:
    now = dt.datetime.now(dt.timezone.utc).replace(microsecond=0)
    return now.isoformat().replace("+00:00", "Z")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReviewError(f"{label} must be a JSON object")
    return value


def require_non_empty_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ReviewError(f"{label} must be a non-empty string")
    return value.strip()


def validate_lookup(lookup: Any, request_code: str) -> dict[str, Any]:
    normalized_code = normalize_request_code(request_code)
    lookup_object = require_object(lookup, "Lookup")

    if lookup_object.get("schemaVersion") != 1:
        raise ReviewError("Lookup schemaVersion must be 1")
    if lookup_object.get("requestCode") != normalized_code:
        raise ReviewError("Lookup requestCode does not match the requested code")
    if lookup_object.get("matchCount") != 1:
        raise ReviewError("Lookup must have exactly one match")

    matches = lookup_object.get("matches")
    if not isinstance(matches, list) or len(matches) != 1:
        raise ReviewError("Lookup matches must contain exactly one item")

    match = require_object(matches[0], "Lookup match")
    if match.get("requestCode") != normalized_code:
        raise ReviewError("Lookup match requestCode does not match the requested code")

    require_non_empty_string(match.get("uid"), "Lookup match uid")
    require_non_empty_string(match.get("safeUid"), "Lookup match safeUid")

    evidence = match.get("evidence")
    if not isinstance(evidence, list) or not evidence:
        raise ReviewError("Lookup match evidence must be a non-empty list")
    for evidence_path in evidence:
        if not isinstance(evidence_path, str) or not evidence_path.startswith("/"):
            raise ReviewError("Lookup evidence paths must be absolute RTDB paths")

    return match


def validate_plan(plan: Any) -> tuple[str, dict[str, None], dict[str, list[str]], list[str]]:
    plan_object = require_object(plan, "Deletion plan")

    if plan_object.get("schemaVersion") != 1:
        raise ReviewError("Deletion plan schemaVersion must be 1")

    plan_uid = require_non_empty_string(plan_object.get("uid"), "Deletion plan uid")
    updates = plan_object.get("updates")
    if not isinstance(updates, dict):
        raise ReviewError("Deletion plan updates must be a JSON object")
    if not updates:
        raise ReviewError("Deletion plan must contain at least one update")

    update_count = plan_object.get("updateCount")
    if not isinstance(update_count, int) or update_count != len(updates):
        raise ReviewError("Deletion plan updateCount must equal the number of updates")

    normalized_updates: dict[str, None] = {}
    for update_path, update_value in updates.items():
        if not isinstance(update_path, str) or not update_path.startswith("/"):
            raise ReviewError("Deletion plan update paths must be absolute RTDB paths")
        if update_value is not None:
            raise ReviewError("Deletion plan updates must only contain null values")
        normalized_updates[update_path] = None

    categories = plan_object.get("categories")
    if not isinstance(categories, dict):
        raise ReviewError("Deletion plan categories must be a JSON object")

    missing_categories = sorted(REQUIRED_CATEGORY_KEYS.difference(categories))
    if missing_categories:
        raise ReviewError(f"Deletion plan categories missing: {', '.join(missing_categories)}")

    categorized_paths: set[str] = set()
    normalized_categories: dict[str, list[str]] = {}
    for category_name, paths in categories.items():
        if not isinstance(category_name, str) or not isinstance(paths, list):
            raise ReviewError("Deletion plan categories must map names to path lists")
        normalized_paths: list[str] = []
        for path in paths:
            if not isinstance(path, str) or not path.startswith("/"):
                raise ReviewError("Deletion plan category paths must be absolute RTDB paths")
            if path not in normalized_updates:
                raise ReviewError(f"Deletion plan category path is not in updates: {path}")
            normalized_paths.append(path)
            categorized_paths.add(path)
        normalized_categories[category_name] = sorted(normalized_paths)

    uncategorized_paths = sorted(set(normalized_updates).difference(categorized_paths))
    if uncategorized_paths:
        raise ReviewError(f"Deletion plan has uncategorized updates: {', '.join(uncategorized_paths)}")

    retained = plan_object.get("retained")
    if not isinstance(retained, list):
        raise ReviewError("Deletion plan retained roots must be a list")
    retained_roots: list[str] = []
    for retained_item in retained:
        if not isinstance(retained_item, dict):
            raise ReviewError("Deletion plan retained entries must be objects")
        retained_roots.append(require_non_empty_string(retained_item.get("root"), "Retained root"))

    missing_retained = sorted(REQUIRED_RETAINED_ROOTS.difference(retained_roots))
    if missing_retained:
        raise ReviewError(f"Deletion plan retained roots missing: {', '.join(missing_retained)}")

    return plan_uid, dict(sorted(normalized_updates.items())), normalized_categories, sorted(retained_roots)


def review_account_deletion_request(
    lookup: Any,
    plan: Any,
    request_code: str,
    reviewed_at: str | None = None,
) -> dict[str, Any]:
    normalized_code = normalize_request_code(request_code)
    match = validate_lookup(lookup, normalized_code)
    plan_uid, updates, categories, retained_roots = validate_plan(plan)

    match_uid = require_non_empty_string(match.get("uid"), "Lookup match uid")
    match_safe_uid = require_non_empty_string(match.get("safeUid"), "Lookup match safeUid")
    if sanitize_key(match_uid) != plan_uid or match_safe_uid != plan_uid:
        raise ReviewError("Lookup UID does not match deletion plan uid")

    category_counts = {key: len(categories.get(key, [])) for key in sorted(categories)}
    evidence = sorted(match["evidence"])
    reviewed_at_value = reviewed_at or utc_now()

    return {
        "schemaVersion": 1,
        "reviewStatus": "readyForTrustedApply",
        "requestCode": normalized_code,
        "uidKeySuffix": redact_uid_key(plan_uid),
        "uidKeyHash": sha256_text(plan_uid),
        "updateCount": len(updates),
        "categoryCounts": category_counts,
        "evidenceCount": len(evidence),
        "lookupEvidenceHash": sha256_json(
            {
                "requestCode": normalized_code,
                "safeUid": plan_uid,
                "evidence": evidence,
            }
        ),
        "planHash": sha256_json(
            {
                "uid": plan_uid,
                "updates": updates,
                "retainedRoots": retained_roots,
            }
        ),
        "retainedRoots": retained_roots,
        "reviewedAt": reviewed_at_value,
        "nextRequiredGate": "Apply only through a trusted executor or callable orchestrator after requester verification.",
    }


def dump_review(review: dict[str, Any]) -> str:
    return json.dumps(review, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        review = review_account_deletion_request(
            read_json(Path(args.lookup)),
            read_json(Path(args.plan)),
            args.request_code,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    review_text = dump_review(review)
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(review_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(review_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
