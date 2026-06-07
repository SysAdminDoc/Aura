#!/usr/bin/env python3
"""Validate Android callable wire protocol coverage against the backend contract."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


class WireProtocolError(ValueError):
    """Raised when callable wire protocol metadata or Android coverage drifts."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura Android callable wire protocol coverage.")
    parser.add_argument("--contract", default="docs/community-callable-contract.json")
    parser.add_argument("--protocol", default="docs/community-callable-wire-protocol.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise WireProtocolError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise WireProtocolError(f"{label} must be a non-empty string")
    return value.strip()


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise WireProtocolError(f"{label} must be a boolean")
    return value


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise WireProtocolError(f"{label} must be a non-empty list")
    values: list[str] = []
    for index, item in enumerate(value):
        values.append(require_string(item, f"{label}[{index}]"))
    if len(values) != len(set(values)):
        raise WireProtocolError(f"{label} contains duplicate values")
    return values


def extract_method_body(source: str, method_name: str) -> str:
    marker = re.search(rf"\n\s+suspend fun {re.escape(method_name)}\b", source)
    if marker is None:
        raise WireProtocolError(f"CommunityCallableClient missing method {method_name}")
    start = marker.start()
    next_method = re.search(r"\n\s+suspend fun \w+\b", source[marker.end():])
    if next_method is None:
        end = source.find("\n}", marker.end())
        if end == -1:
            end = len(source)
    else:
        end = marker.end() + next_method.start()
    return source[start:end]


def validate_protocol_metadata(contract: dict[str, Any], protocol: dict[str, Any]) -> list[dict[str, Any]]:
    if protocol.get("schemaVersion") != 1:
        raise WireProtocolError("Wire protocol schemaVersion must be 1")
    if protocol.get("contractKind") != "communityCallableWireProtocol":
        raise WireProtocolError("Wire protocol contractKind is invalid")

    android_client = require_object(protocol.get("androidClient"), "androidClient")
    for field in ("sourcePath", "testPath", "envelopeBuilder", "resultType"):
        require_string(android_client.get(field), f"androidClient.{field}")

    contract_surfaces = {
        require_string(surface.get("surfaceKey"), "contract surfaceKey"): require_object(surface, "contract surface")
        for surface in contract.get("surfaces", [])
    }
    surfaces = protocol.get("surfaces")
    if not isinstance(surfaces, list):
        raise WireProtocolError("Wire protocol surfaces must be a list")
    if len(surfaces) != len(contract_surfaces):
        raise WireProtocolError("Wire protocol surface count does not match callable contract")

    validated: list[dict[str, Any]] = []
    seen: set[str] = set()
    for raw_surface in surfaces:
        surface = require_object(raw_surface, "wire protocol surface")
        surface_key = require_string(surface.get("surfaceKey"), "surfaceKey")
        if surface_key in seen:
            raise WireProtocolError(f"Duplicate wire protocol surface: {surface_key}")
        seen.add(surface_key)
        if surface_key not in contract_surfaces:
            raise WireProtocolError(f"Unexpected wire protocol surface: {surface_key}")

        contract_callable = require_object(contract_surfaces[surface_key].get("callable"), f"{surface_key} callable")
        for field in (
            "functionName",
            "quotaPolicyAccessor",
            "androidMethod",
            "payloadSchema",
            "androidInputType",
            "payloadBuilder",
            "resultResourceIdField",
            "focusedTest",
        ):
            require_string(surface.get(field), f"{surface_key}.{field}")
        operation_prefixes = require_string_list(surface.get("operationPrefixes"), f"{surface_key}.operationPrefixes")
        consume_limited = require_bool(
            surface.get("consumeLimitedUseAppCheckToken"),
            f"{surface_key}.consumeLimitedUseAppCheckToken",
        )

        if surface["functionName"] != contract_callable.get("functionName"):
            raise WireProtocolError(f"{surface_key} functionName does not match callable contract")
        if surface["payloadSchema"] != contract_callable.get("payloadSchema"):
            raise WireProtocolError(f"{surface_key} payloadSchema does not match callable contract")
        if consume_limited != contract_callable.get("consumeLimitedUseAppCheckToken"):
            raise WireProtocolError(f"{surface_key} limited-use token choice does not match callable contract")

        validated.append({**surface, "operationPrefixes": operation_prefixes})

    if seen != set(contract_surfaces):
        missing = sorted(set(contract_surfaces) - seen)
        raise WireProtocolError(f"Wire protocol missing surfaces: {', '.join(missing)}")
    return validated


def validate_android_client(
    repo_root: Path,
    protocol: dict[str, Any],
    surfaces: list[dict[str, Any]],
) -> dict[str, Any]:
    android_client = require_object(protocol["androidClient"], "androidClient")
    source_path = repo_root / require_string(android_client["sourcePath"], "androidClient.sourcePath")
    test_path = repo_root / require_string(android_client["testPath"], "androidClient.testPath")
    source = source_path.read_text(encoding="utf-8")
    tests = test_path.read_text(encoding="utf-8")

    envelope_builder = require_string(android_client["envelopeBuilder"], "androidClient.envelopeBuilder")
    result_type = require_string(android_client["resultType"], "androidClient.resultType")
    if f"data class {result_type}" not in source:
        raise WireProtocolError(f"Android client missing result type {result_type}")
    if f"fun {envelope_builder}" not in source:
        raise WireProtocolError(f"Android client missing envelope builder {envelope_builder}")

    for surface in surfaces:
        method = surface["androidMethod"]
        body = extract_method_body(source, method)
        signature = body.split("{", maxsplit=1)[0]
        label = surface["surfaceKey"]

        if surface["androidInputType"] not in signature:
            raise WireProtocolError(f"{label} Android method signature does not accept {surface['androidInputType']}")
        if f"CommunityQuotaPolicies.{surface['quotaPolicyAccessor']}.callable" not in body:
            raise WireProtocolError(f"{label} does not read its quota policy")
        if surface["payloadBuilder"] not in body:
            raise WireProtocolError(f"{label} does not use {surface['payloadBuilder']}")
        if "buildCommunityCallableEnvelope" not in body:
            raise WireProtocolError(f"{label} does not use the shared callable envelope")
        if "consumeLimitedUseAppCheckToken = policy.consumeLimitedUseAppCheckToken" not in body:
            raise WireProtocolError(f"{label} does not route limited-use token policy")
        if f'resourceIdField = "{surface["resultResourceIdField"]}"' not in body:
            raise WireProtocolError(f"{label} response resource ID mapping drifted")

        prefixes = surface["operationPrefixes"]
        if len(prefixes) == 1:
            expected = f'communityOperationId("{prefixes[0]}")'
            if expected not in body:
                raise WireProtocolError(f"{label} missing operation prefix {prefixes[0]}")
        else:
            if "communityOperationId(operationPrefix)" not in body:
                raise WireProtocolError(f"{label} does not route stateful operation prefixes")
            for prefix in prefixes:
                if f'"{prefix}"' not in body:
                    raise WireProtocolError(f"{label} missing stateful operation prefix {prefix}")

        if surface["focusedTest"] not in tests:
            raise WireProtocolError(f"{label} missing focused client test")
        if f'assertEquals("{surface["functionName"]}", request.functionName)' not in tests:
            raise WireProtocolError(f"{label} client test does not assert function name")
        token_assertion = "assertTrue(request.consumeLimitedUseAppCheckToken)" if surface[
            "consumeLimitedUseAppCheckToken"
        ] else "assertFalse(request.consumeLimitedUseAppCheckToken)"
        if token_assertion not in tests:
            raise WireProtocolError(f"{label} client test does not assert token choice")
        for prefix in prefixes:
            if f'startsWith("{prefix}_")' not in tests:
                raise WireProtocolError(f"{label} client test does not assert operation prefix {prefix}")

    return {
        "androidClient": str(source_path),
        "clientTest": str(test_path),
        "surfaceCount": len(surfaces),
        "functionNames": sorted(surface["functionName"] for surface in surfaces),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        contract = require_object(read_json(repo_root / args.contract), "Callable contract")
        protocol = require_object(read_json(repo_root / args.protocol), "Wire protocol")
        surfaces = validate_protocol_metadata(contract, protocol)
        result = validate_android_client(repo_root, protocol, surfaces)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
