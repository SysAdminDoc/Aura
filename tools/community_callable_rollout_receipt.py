#!/usr/bin/env python3
"""Validate private community callable rollout execution evidence."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import (
    ReviewError,
    require_non_empty_string,
    require_object,
    sha256_json,
    sha256_text,
    utc_now,
)


SHA256_HEX = re.compile(r"^[a-fA-F0-9]{64}$")
VALID_APP_CHECK_STATES = {"enforced", "monitoring"}
VALID_INVOCATION_STATUSES = {"accepted", "duplicate"}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate private Aura community callable rollout evidence."
    )
    parser.add_argument("--contract", default="docs/community-callable-contract.json")
    parser.add_argument("--wire-protocol", default="docs/community-callable-wire-protocol.json")
    parser.add_argument("--execution-evidence", required=True)
    parser.add_argument("--support-reference", required=True)
    parser.add_argument("--output", help="Optional redacted rollout receipt path. Defaults to stdout.")
    return parser.parse_args()


def require_sha256(value: Any, label: str) -> str:
    text = require_non_empty_string(value, label)
    if not SHA256_HEX.fullmatch(text):
        raise ReviewError(f"{label} must be a SHA-256 hex digest")
    return text.lower()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ReviewError(f"{label} must be a non-empty list")
    values: list[str] = []
    for index, item in enumerate(value):
        values.append(require_non_empty_string(item, f"{label}[{index}]"))
    if len(values) != len(set(values)):
        raise ReviewError(f"{label} contains duplicate values")
    return values


def callable_surfaces(contract: dict[str, Any], protocol: dict[str, Any]) -> dict[str, dict[str, Any]]:
    contract_rows = {
        require_non_empty_string(surface.get("surfaceKey"), "Contract surfaceKey"): require_object(
            surface.get("callable"), "Contract callable"
        )
        for surface in contract.get("surfaces", [])
    }
    protocol_rows: dict[str, dict[str, Any]] = {}
    for raw_surface in protocol.get("surfaces", []):
        surface = require_object(raw_surface, "Wire protocol surface")
        surface_key = require_non_empty_string(surface.get("surfaceKey"), "Wire protocol surfaceKey")
        if surface_key in protocol_rows:
            raise ReviewError(f"Duplicate wire protocol surface: {surface_key}")
        if surface_key not in contract_rows:
            raise ReviewError(f"Unexpected wire protocol surface: {surface_key}")
        function_name = require_non_empty_string(surface.get("functionName"), f"{surface_key} functionName")
        if function_name != contract_rows[surface_key].get("functionName"):
            raise ReviewError(f"{surface_key} functionName does not match callable contract")
        protocol_rows[surface_key] = {
            "surfaceKey": surface_key,
            "functionName": function_name,
            "resourceIdField": require_non_empty_string(
                surface.get("resultResourceIdField"),
                f"{surface_key} resultResourceIdField",
            ),
            "operationPrefixes": require_string_list(
                surface.get("operationPrefixes"),
                f"{surface_key} operationPrefixes",
            ),
            "appCheckTokenMode": (
                "limitedUse"
                if contract_rows[surface_key].get("consumeLimitedUseAppCheckToken") is True
                else "standard"
            ),
        }
    if set(protocol_rows) != set(contract_rows):
        missing = sorted(set(contract_rows) - set(protocol_rows))
        raise ReviewError(f"Wire protocol missing callable surfaces: {', '.join(missing)}")
    return protocol_rows


def validate_invocation(row: Any, surface: dict[str, Any]) -> dict[str, Any]:
    invocation = require_object(row, "Callable invocation evidence row")
    label = surface["surfaceKey"]
    if invocation.get("surfaceKey") != label:
        raise ReviewError(f"{label} invocation surfaceKey does not match")
    if invocation.get("functionName") != surface["functionName"]:
        raise ReviewError(f"{label} invocation functionName does not match")
    if invocation.get("resourceIdField") != surface["resourceIdField"]:
        raise ReviewError(f"{label} invocation resourceIdField does not match")
    if invocation.get("appCheckTokenMode") != surface["appCheckTokenMode"]:
        raise ReviewError(f"{label} invocation App Check token mode does not match")

    status = require_non_empty_string(invocation.get("invocationStatus"), f"{label} invocationStatus")
    if status not in VALID_INVOCATION_STATUSES:
        raise ReviewError(f"{label} invocationStatus must be one of: {', '.join(sorted(VALID_INVOCATION_STATUSES))}")

    operation_id = require_non_empty_string(invocation.get("operationId"), f"{label} operationId")
    if not any(operation_id.startswith(f"{prefix}_") for prefix in surface["operationPrefixes"]):
        raise ReviewError(f"{label} operationId does not use a contracted prefix")

    for field in (
        "resourceIdValue",
        "authUidHash",
        "privateEvidenceReference",
        "privateEvidenceHash",
    ):
        require_non_empty_string(invocation.get(field), f"{label} invocation {field}")
    require_sha256(invocation.get("authUidHash"), f"{label} invocation authUidHash")
    require_sha256(invocation.get("privateEvidenceHash"), f"{label} invocation privateEvidenceHash")
    return invocation


def validate_callable_rollout_evidence(
    evidence: Any,
    contract: dict[str, Any],
    protocol: dict[str, Any],
    support_reference: str,
) -> tuple[dict[str, Any], dict[str, dict[str, Any]]]:
    evidence_object = require_object(evidence, "Callable rollout evidence")
    if evidence_object.get("schemaVersion") != 1:
        raise ReviewError("Callable rollout evidence schemaVersion must be 1")
    if evidence_object.get("evidenceKind") != "communityCallableRolloutExecution":
        raise ReviewError("Callable rollout evidence has the wrong evidenceKind")
    if evidence_object.get("executionStatus") != "completed":
        raise ReviewError("Callable rollout evidence executionStatus must be completed")
    if evidence_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("Callable rollout evidence supportReference does not match")
    if evidence_object.get("contractHash") != sha256_json(contract):
        raise ReviewError("Callable rollout evidence contractHash does not match")
    if evidence_object.get("wireProtocolHash") != sha256_json(protocol):
        raise ReviewError("Callable rollout evidence wireProtocolHash does not match")

    for field in (
        "projectId",
        "executedBy",
        "executedAt",
        "ownerApprovalReference",
        "privateEvidenceReference",
        "privateEvidenceHash",
    ):
        require_non_empty_string(evidence_object.get(field), f"Callable rollout evidence {field}")
    require_sha256(evidence_object.get("privateEvidenceHash"), "Callable rollout evidence privateEvidenceHash")

    app_check_state = require_object(evidence_object.get("appCheckState"), "Callable rollout appCheckState")
    functions_state = require_non_empty_string(app_check_state.get("functions"), "Functions App Check state")
    if functions_state not in VALID_APP_CHECK_STATES:
        raise ReviewError(f"Functions App Check state must be one of: {', '.join(sorted(VALID_APP_CHECK_STATES))}")

    surfaces = callable_surfaces(contract, protocol)
    invocations = evidence_object.get("invocations")
    if not isinstance(invocations, list):
        raise ReviewError("Callable rollout evidence invocations must be a list")
    if len(invocations) != len(surfaces):
        raise ReviewError("Callable rollout evidence must include one invocation per callable surface")

    seen: set[str] = set()
    for raw_invocation in invocations:
        invocation = require_object(raw_invocation, "Callable invocation evidence row")
        surface_key = require_non_empty_string(invocation.get("surfaceKey"), "Callable invocation surfaceKey")
        if surface_key in seen:
            raise ReviewError(f"Duplicate callable invocation evidence: {surface_key}")
        if surface_key not in surfaces:
            raise ReviewError(f"Unexpected callable invocation evidence: {surface_key}")
        seen.add(surface_key)
        validate_invocation(invocation, surfaces[surface_key])

    missing = sorted(set(surfaces) - seen)
    if missing:
        raise ReviewError(f"Callable rollout evidence missing surfaces: {', '.join(missing)}")
    return evidence_object, surfaces


def redacted_invocation_rows(invocations: list[Any]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for raw_invocation in invocations:
        invocation = require_object(raw_invocation, "Callable invocation evidence row")
        rows.append(
            {
                "surfaceKey": invocation.get("surfaceKey"),
                "functionName": invocation.get("functionName"),
                "invocationStatus": invocation.get("invocationStatus"),
                "appCheckTokenMode": invocation.get("appCheckTokenMode"),
                "resourceIdField": invocation.get("resourceIdField"),
                "operationIdHash": sha256_text(
                    require_non_empty_string(invocation.get("operationId"), "Invocation operationId")
                ),
                "resourceIdHash": sha256_text(
                    require_non_empty_string(invocation.get("resourceIdValue"), "Invocation resourceIdValue")
                ),
                "authUidHash": require_sha256(invocation.get("authUidHash"), "Invocation authUidHash"),
                "privateEvidenceReferenceHash": sha256_text(
                    require_non_empty_string(
                        invocation.get("privateEvidenceReference"),
                        "Invocation privateEvidenceReference",
                    )
                ),
                "privateEvidenceHash": require_sha256(
                    invocation.get("privateEvidenceHash"),
                    "Invocation privateEvidenceHash",
                ),
            }
        )
    return sorted(rows, key=lambda row: str(row["surfaceKey"]))


def build_callable_rollout_receipt(
    contract: Any,
    protocol: Any,
    execution_evidence: Any,
    support_reference: str,
    receipted_at: str | None = None,
) -> dict[str, Any]:
    contract_object = require_object(contract, "Callable contract")
    protocol_object = require_object(protocol, "Callable wire protocol")
    evidence, _ = validate_callable_rollout_evidence(
        execution_evidence,
        contract_object,
        protocol_object,
        support_reference,
    )
    project_id = require_non_empty_string(evidence.get("projectId"), "Callable rollout evidence projectId")
    app_check_state = require_object(evidence.get("appCheckState"), "Callable rollout appCheckState")
    invocations = evidence.get("invocations", [])

    return {
        "schemaVersion": 1,
        "receiptKind": "communityCallableRolloutExecution",
        "executionStatus": "callablesInvoked",
        "supportReference": evidence.get("supportReference"),
        "projectIdHash": sha256_text(project_id),
        "executedAt": evidence.get("executedAt"),
        "executedBy": evidence.get("executedBy"),
        "ownerApprovalReference": evidence.get("ownerApprovalReference"),
        "appCheckFunctionsState": app_check_state.get("functions"),
        "callableSurfaceCount": len(invocations),
        "contractHash": sha256_json(contract_object),
        "wireProtocolHash": sha256_json(protocol_object),
        "executionEvidenceHash": sha256_json(evidence),
        "privateEvidenceReferenceHash": sha256_text(
            require_non_empty_string(evidence.get("privateEvidenceReference"), "Callable rollout privateEvidenceReference")
        ),
        "privateEvidenceHash": require_sha256(
            evidence.get("privateEvidenceHash"),
            "Callable rollout privateEvidenceHash",
        ),
        "invocations": redacted_invocation_rows(invocations),
        "receiptedAt": receipted_at or utc_now(),
        "privacyNote": "This receipt omits project ID, raw operation IDs, raw resource IDs, RTDB paths, Storage paths, command output, credentials, and tokens.",
    }


def validate_callable_rollout_receipt(receipt: Any, support_reference: str) -> dict[str, Any]:
    receipt_object = require_object(receipt, "Callable rollout receipt")
    if receipt_object.get("schemaVersion") != 1:
        raise ReviewError("Callable rollout receipt schemaVersion must be 1")
    if receipt_object.get("receiptKind") != "communityCallableRolloutExecution":
        raise ReviewError("Callable rollout receipt has the wrong receiptKind")
    if receipt_object.get("executionStatus") != "callablesInvoked":
        raise ReviewError("Callable rollout receipt must be callablesInvoked")
    if receipt_object.get("supportReference") != require_non_empty_string(support_reference, "Support reference"):
        raise ReviewError("Callable rollout receipt supportReference does not match")
    if receipt_object.get("appCheckFunctionsState") not in VALID_APP_CHECK_STATES:
        raise ReviewError("Callable rollout receipt functions App Check state is invalid")
    for field in (
        "projectIdHash",
        "executedAt",
        "executedBy",
        "ownerApprovalReference",
        "contractHash",
        "wireProtocolHash",
        "executionEvidenceHash",
        "privateEvidenceReferenceHash",
        "privateEvidenceHash",
        "receiptedAt",
    ):
        require_non_empty_string(receipt_object.get(field), f"Callable rollout receipt {field}")
    require_sha256(receipt_object.get("privateEvidenceHash"), "Callable rollout receipt privateEvidenceHash")

    invocation_count = receipt_object.get("callableSurfaceCount")
    invocations = receipt_object.get("invocations")
    if not isinstance(invocation_count, int) or invocation_count <= 0:
        raise ReviewError("Callable rollout receipt callableSurfaceCount must be positive")
    if not isinstance(invocations, list) or len(invocations) != invocation_count:
        raise ReviewError("Callable rollout receipt invocations count must match")
    seen: set[str] = set()
    for raw_row in invocations:
        row = require_object(raw_row, "Callable rollout receipt invocation")
        surface_key = require_non_empty_string(row.get("surfaceKey"), "Receipt invocation surfaceKey")
        if surface_key in seen:
            raise ReviewError(f"Duplicate callable rollout receipt invocation: {surface_key}")
        seen.add(surface_key)
        if row.get("invocationStatus") not in VALID_INVOCATION_STATUSES:
            raise ReviewError("Callable rollout receipt invocationStatus is invalid")
        if row.get("appCheckTokenMode") not in {"limitedUse", "standard"}:
            raise ReviewError("Callable rollout receipt appCheckTokenMode is invalid")
        for field in (
            "functionName",
            "resourceIdField",
            "operationIdHash",
            "resourceIdHash",
            "authUidHash",
            "privateEvidenceReferenceHash",
            "privateEvidenceHash",
        ):
            require_non_empty_string(row.get(field), f"Receipt invocation {field}")
        require_sha256(row.get("authUidHash"), "Receipt invocation authUidHash")
        require_sha256(row.get("privateEvidenceHash"), "Receipt invocation privateEvidenceHash")
    return receipt_object


def dump_callable_rollout_receipt(receipt: dict[str, Any]) -> str:
    return json.dumps(receipt, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        receipt = build_callable_rollout_receipt(
            read_json(Path(args.contract)),
            read_json(Path(args.wire_protocol)),
            read_json(Path(args.execution_evidence)),
            args.support_reference,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    receipt_text = dump_callable_rollout_receipt(receipt)
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
