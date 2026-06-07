#!/usr/bin/env python3
"""Build a private executor package for reviewed account deletion updates."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_apply_simulator import validate_review
from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import (
    ReviewError,
    require_non_empty_string,
    require_object,
    sha256_json,
    utc_now,
    validate_plan,
)
from tools.community_deletion_request_lookup import normalize_request_code


ACCEPTED_SIMULATION_STATUSES = {"passed", "passedWithMissingPaths"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a private Aura account deletion executor package."
    )
    parser.add_argument("--plan", required=True, help="JSON output from community_account_deletion_plan.py.")
    parser.add_argument("--review", required=True, help="JSON output from community_account_deletion_review.py.")
    parser.add_argument("--simulation", required=True, help="JSON output from community_account_deletion_apply_simulator.py.")
    parser.add_argument("--request-code", required=True, help="AURA- request code from the user request.")
    parser.add_argument("--operator", required=True, help="Private operator initials or ticket handle.")
    parser.add_argument("--output", help="Optional executor package path. Defaults to stdout.")
    return parser.parse_args()


def validate_simulation(simulation: Any, review: dict[str, Any], update_count: int) -> dict[str, Any]:
    simulation_object = require_object(simulation, "Account deletion simulation")
    if simulation_object.get("schemaVersion") != 1:
        raise ReviewError("Account deletion simulation schemaVersion must be 1")
    if simulation_object.get("simulationStatus") not in ACCEPTED_SIMULATION_STATUSES:
        raise ReviewError("Account deletion simulation must pass before packaging")
    if simulation_object.get("remainingUpdatePathCount") != 0:
        raise ReviewError("Account deletion simulation still has remaining update paths")
    if simulation_object.get("updateCount") != update_count:
        raise ReviewError("Account deletion simulation updateCount does not match the plan")
    if simulation_object.get("planHash") != review.get("planHash"):
        raise ReviewError("Account deletion simulation planHash does not match the review")
    if simulation_object.get("reviewHash") != sha256_json(review):
        raise ReviewError("Account deletion simulation reviewHash does not match the review")

    require_non_empty_string(simulation_object.get("snapshotHash"), "Account deletion simulation snapshotHash")
    return simulation_object


def build_executor_package(
    plan: Any,
    review: Any,
    simulation: Any,
    request_code: str,
    operator: str,
    packaged_at: str | None = None,
) -> dict[str, Any]:
    normalized_code = normalize_request_code(request_code)
    operator_label = require_non_empty_string(operator, "Operator")
    plan_uid, updates, _categories, retained_roots = validate_plan(plan)
    review_object = validate_review(review, plan_uid, updates, retained_roots)
    simulation_object = validate_simulation(simulation, review_object, len(updates))

    if review_object.get("requestCode") != normalized_code:
        raise ReviewError("Account deletion review requestCode does not match the package request code")
    if simulation_object.get("requestCode") != normalized_code:
        raise ReviewError("Account deletion simulation requestCode does not match the package request code")

    return {
        "schemaVersion": 1,
        "packageStatus": "readyForTrustedExecutor",
        "requestCode": normalized_code,
        "operator": operator_label,
        "packagedAt": packaged_at or utc_now(),
        "uidKeySuffix": review_object.get("uidKeySuffix"),
        "updateCount": len(updates),
        "updatesHash": sha256_json(updates),
        "updates": updates,
        "planHash": review_object.get("planHash"),
        "reviewHash": sha256_json(review_object),
        "simulationHash": sha256_json(simulation_object),
        "snapshotHash": simulation_object.get("snapshotHash"),
        "retainedRoots": retained_roots,
        "executorWarning": "Private package: contains full RTDB update paths. Do not publish; apply only through the trusted executor after requester verification.",
    }


def dump_package(package: dict[str, Any]) -> str:
    return json.dumps(package, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        package = build_executor_package(
            read_json(Path(args.plan)),
            read_json(Path(args.review)),
            read_json(Path(args.simulation)),
            args.request_code,
            args.operator,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    package_text = dump_package(package)
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
