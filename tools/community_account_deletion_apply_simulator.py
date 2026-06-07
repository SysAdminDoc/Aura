#!/usr/bin/env python3
"""Simulate reviewed account deletion RTDB null updates against an export."""

from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import (
    ReviewError,
    require_object,
    sha256_json,
    sha256_text,
    utc_now,
    validate_plan,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Simulate a reviewed Aura account deletion plan against an RTDB export."
    )
    parser.add_argument("--database-export", required=True, help="JSON export containing community RTDB roots.")
    parser.add_argument("--plan", required=True, help="JSON output from community_account_deletion_plan.py.")
    parser.add_argument("--review", required=True, help="JSON output from community_account_deletion_review.py.")
    parser.add_argument("--output", help="Optional simulation receipt path. Defaults to stdout.")
    parser.add_argument("--snapshot-output", help="Optional simulated post-delete export path.")
    return parser.parse_args()


def path_parts(path: str) -> list[str]:
    if not isinstance(path, str) or not path.startswith("/") or path == "/":
        raise ReviewError("RTDB update paths must be absolute non-root paths")
    parts = [part for part in path.split("/") if part]
    if not parts:
        raise ReviewError("RTDB update paths must contain at least one key")
    return parts


def path_exists(root: dict[str, Any], path: str) -> bool:
    current: Any = root
    for part in path_parts(path):
        if not isinstance(current, dict) or part not in current:
            return False
        current = current[part]
    return True


def delete_path(root: dict[str, Any], path: str) -> bool:
    parts = path_parts(path)
    current: Any = root
    stack: list[tuple[dict[str, Any], str]] = []
    for part in parts[:-1]:
        if not isinstance(current, dict) or part not in current:
            return False
        stack.append((current, part))
        current = current[part]

    leaf = parts[-1]
    if not isinstance(current, dict) or leaf not in current:
        return False
    del current[leaf]

    for parent, key in reversed(stack):
        child = parent.get(key)
        if isinstance(child, dict) and not child:
            del parent[key]
        else:
            break
    return True


def prune_empty_objects(value: Any) -> bool:
    if not isinstance(value, dict):
        return False
    for key in list(value.keys()):
        if prune_empty_objects(value[key]):
            del value[key]
    return not value


def find_overlapping_paths(paths: list[str]) -> list[str]:
    sorted_paths = sorted(paths)
    overlaps: list[str] = []
    for index, current in enumerate(sorted_paths):
        current_prefix = f"{current.rstrip('/')}/"
        for candidate in sorted_paths[index + 1 :]:
            if candidate.startswith(current_prefix):
                overlaps.append(f"{current} -> {candidate}")
    return overlaps


def validate_review(review: Any, plan_uid: str, updates: dict[str, None], retained_roots: list[str]) -> dict[str, Any]:
    review_object = require_object(review, "Account deletion review")
    if review_object.get("schemaVersion") != 1:
        raise ReviewError("Account deletion review schemaVersion must be 1")
    if review_object.get("reviewStatus") != "readyForTrustedApply":
        raise ReviewError("Account deletion review must be readyForTrustedApply")
    if review_object.get("updateCount") != len(updates):
        raise ReviewError("Account deletion review updateCount does not match the plan")
    if review_object.get("uidKeyHash") != sha256_text(plan_uid):
        raise ReviewError("Account deletion review uidKeyHash does not match the plan")
    if sorted(review_object.get("retainedRoots", [])) != retained_roots:
        raise ReviewError("Account deletion review retainedRoots do not match the plan")

    expected_plan_hash = sha256_json(
        {
            "uid": plan_uid,
            "updates": updates,
            "retainedRoots": retained_roots,
        }
    )
    if review_object.get("planHash") != expected_plan_hash:
        raise ReviewError("Account deletion review planHash does not match the plan")
    return review_object


def simulate_account_deletion_apply(
    database_export: Any,
    plan: Any,
    review: Any,
    simulated_at: str | None = None,
) -> tuple[dict[str, Any], dict[str, Any]]:
    if not isinstance(database_export, dict):
        raise ReviewError("Database export must be a JSON object")

    plan_uid, updates, categories, retained_roots = validate_plan(plan)
    review_object = validate_review(review, plan_uid, updates, retained_roots)
    overlaps = find_overlapping_paths(list(updates))
    if overlaps:
        raise ReviewError(f"Deletion plan contains overlapping update paths: {', '.join(overlaps)}")

    snapshot = copy.deepcopy(database_export)
    existing_before = sorted(path for path in updates if path_exists(snapshot, path))
    missing_before = sorted(path for path in updates if path not in existing_before)

    for path in sorted(updates):
        delete_path(snapshot, path)
    prune_empty_objects(snapshot)

    remaining_paths = sorted(path for path in updates if path_exists(snapshot, path))
    if remaining_paths:
        status = "failed"
    elif missing_before:
        status = "passedWithMissingPaths"
    else:
        status = "passed"

    category_counts = {key: len(categories.get(key, [])) for key in sorted(categories)}
    receipt = {
        "schemaVersion": 1,
        "simulationStatus": status,
        "requestCode": review_object.get("requestCode"),
        "uidKeySuffix": review_object.get("uidKeySuffix"),
        "updateCount": len(updates),
        "deletedPathCount": len(existing_before),
        "missingBeforeCount": len(missing_before),
        "remainingUpdatePathCount": len(remaining_paths),
        "categoryCounts": category_counts,
        "existingBeforeHash": sha256_json(existing_before),
        "missingBeforeHash": sha256_json(missing_before),
        "remainingUpdatePathHash": sha256_json(remaining_paths),
        "planHash": review_object.get("planHash"),
        "reviewHash": sha256_json(review_object),
        "snapshotHash": sha256_json(snapshot),
        "retainedRoots": retained_roots,
        "simulatedAt": simulated_at or utc_now(),
        "nextRequiredGate": "This simulator did not contact Firebase; apply only through a trusted executor after requester verification.",
    }
    return receipt, snapshot


def dump_json(value: Any) -> str:
    return json.dumps(value, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        receipt, snapshot = simulate_account_deletion_apply(
            read_json(Path(args.database_export)),
            read_json(Path(args.plan)),
            read_json(Path(args.review)),
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    if args.snapshot_output:
        snapshot_output = Path(args.snapshot_output)
        snapshot_output.parent.mkdir(parents=True, exist_ok=True)
        snapshot_output.write_text(dump_json(snapshot), encoding="utf-8")
        print(f"wrote {snapshot_output}")

    receipt_text = dump_json(receipt)
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
