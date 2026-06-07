#!/usr/bin/env python3
"""Resolve an Aura community deletion request code against an RTDB export."""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_plan import object_root, read_json, sanitize_key


UID_VALUE_FIELDS = {
    "blockedUid",
    "blockerUid",
    "createdByUid",
    "creatorId",
    "ownerUid",
    "reporterUid",
    "resolverUid",
    "uploaderId",
    "uploaderUid",
}

UID_KEY_ROOTS = {
    "community_blocked_by",
    "community_user_blocks",
    "creator_follows",
    "creator_profiles",
    "owner_uploads",
}

UID_VALUE_ROOTS = {
    "collection_shares",
    "community_blocked_by",
    "community_reports",
    "community_report_resolutions",
    "community_sounds",
    "community_takedown_receipts",
    "community_upload_deletions",
    "community_user_blocks",
    "community_wallpapers",
    "creator_follows",
    "owner_uploads",
    "shared_collections",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Map an Aura community deletion request code to candidate UID evidence."
    )
    parser.add_argument("--database-export", required=True, help="JSON export containing community RTDB roots.")
    parser.add_argument("--request-code", required=True, help="AURA- deletion request code from Settings.")
    parser.add_argument("--output", help="Optional lookup output path. Defaults to stdout.")
    return parser.parse_args()


def deletion_request_code(uid: str) -> str:
    normalized = uid.strip()
    if not normalized:
        return ""
    digest = hashlib.sha256(normalized.encode("utf-8")).hexdigest()[:12].upper()
    return f"AURA-{digest}"


def normalize_request_code(request_code: str) -> str:
    normalized = request_code.strip().upper()
    if not normalized.startswith("AURA-") or len(normalized) != 17:
        raise ValueError("Request code must look like AURA- plus 12 hex characters")
    try:
        int(normalized.removeprefix("AURA-"), 16)
    except ValueError as exc:
        raise ValueError("Request code suffix must be hexadecimal") from exc
    return normalized


def add_candidate(
    candidates: dict[str, set[str]],
    candidate_uid: Any,
    evidence_path: str,
) -> None:
    if not isinstance(candidate_uid, str) or not candidate_uid.strip():
        return
    candidates.setdefault(candidate_uid.strip(), set()).add(evidence_path)


def collect_uid_keys(
    candidates: dict[str, set[str]],
    root: str,
    value: Any,
    path: str,
) -> None:
    if not isinstance(value, dict):
        return
    for key, child in value.items():
        add_candidate(candidates, key, f"{path}/{key}")
        if root in {"community_blocked_by", "community_user_blocks", "creator_follows"} and isinstance(child, dict):
            for child_key in child:
                add_candidate(candidates, child_key, f"{path}/{key}/{child_key}")


def collect_uid_values(
    candidates: dict[str, set[str]],
    root: str,
    value: Any,
    path: str,
) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}/{key}"
            if key in UID_VALUE_FIELDS:
                add_candidate(candidates, child, child_path)
            collect_uid_values(candidates, root, child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            collect_uid_values(candidates, root, child, f"{path}/{index}")


def collect_vote_marker_uids(candidates: dict[str, set[str]], database_export: dict[str, Any]) -> None:
    for content_id, raw_vote in object_root(database_export, "votes").items():
        if not isinstance(raw_vote, dict):
            continue
        voters = raw_vote.get("voters", {})
        if not isinstance(voters, dict):
            continue
        for voter_key in voters:
            add_candidate(candidates, voter_key, f"/votes/{content_id}/voters/{voter_key}")

    for content_id, raw_voters in object_root(database_export, "voters").items():
        if not isinstance(raw_voters, dict):
            continue
        for voter_key in raw_voters:
            add_candidate(candidates, voter_key, f"/voters/{content_id}/{voter_key}")


def collect_uid_candidates(database_export: Any) -> dict[str, list[str]]:
    if not isinstance(database_export, dict):
        raise ValueError("Database export must be a JSON object")

    candidates: dict[str, set[str]] = {}
    collect_vote_marker_uids(candidates, database_export)

    for root in sorted(UID_KEY_ROOTS | UID_VALUE_ROOTS):
        root_value = object_root(database_export, root)
        if root in UID_KEY_ROOTS:
            collect_uid_keys(candidates, root, root_value, f"/{root}")
        collect_uid_values(candidates, root, root_value, f"/{root}")

    return {uid: sorted(paths) for uid, paths in sorted(candidates.items())}


def lookup_deletion_request(database_export: Any, request_code: str) -> dict[str, Any]:
    normalized_code = normalize_request_code(request_code)
    candidates = collect_uid_candidates(database_export)
    matches = []
    for uid, evidence in candidates.items():
        if deletion_request_code(uid) == normalized_code:
            matches.append(
                {
                    "uid": uid,
                    "safeUid": sanitize_key(uid),
                    "requestCode": normalized_code,
                    "evidence": evidence,
                }
            )

    return {
        "schemaVersion": 1,
        "requestCode": normalized_code,
        "candidateCount": len(candidates),
        "matchCount": len(matches),
        "matches": matches,
    }


def dump_lookup(lookup: dict[str, Any]) -> str:
    return json.dumps(lookup, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    lookup_text = dump_lookup(
        lookup_deletion_request(read_json(Path(args.database_export)), args.request_code)
    )
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(lookup_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(lookup_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
