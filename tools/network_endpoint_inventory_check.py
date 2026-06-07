#!/usr/bin/env python3
"""Validate Aura's reviewed network endpoint inventory."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


class NetworkInventoryError(ValueError):
    """Raised when the network endpoint inventory is stale or malformed."""


URL_LITERAL_RE = re.compile(r'"([^"\n]*https?://[^"\n]*)"')
HOST_RE = re.compile(r"https?://([^/\s\"'?#$:]+)")
REQUIRED_ENDPOINT_FIELDS = {
    "id",
    "hosts",
    "schemes",
    "surface",
    "authLocation",
    "dataSent",
    "mediaCached",
    "rateLimitCachePolicy",
    "fallbackBehavior",
    "killSwitch",
    "releaseOwner",
    "sourceFiles",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura network endpoint inventory.")
    parser.add_argument("--inventory", default="docs/security/network-endpoints.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise NetworkInventoryError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise NetworkInventoryError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list):
        raise NetworkInventoryError(f"{label} must be a list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise NetworkInventoryError(f"{label} contains duplicate values")
    return values


def normalize_host(host: str) -> str:
    return host.strip().lower().removeprefix("www.")


def literal_hosts_in_file(path: Path) -> list[tuple[str, str]]:
    hosts: list[tuple[str, str]] = []
    text = path.read_text(encoding="utf-8", errors="ignore")
    for match in URL_LITERAL_RE.finditer(text):
        literal = match.group(1)
        if literal.count("://") != 1:
            continue
        host_match = HOST_RE.search(literal)
        if not host_match:
            continue
        hosts.append((host_match.group(1).lower(), literal))
    return hosts


def collect_literal_hosts(repo_root: Path, source_roots: list[str]) -> dict[str, list[str]]:
    found: dict[str, list[str]] = {}
    for root in source_roots:
        root_path = repo_root / root
        if not root_path.exists():
            raise NetworkInventoryError(f"scan source root is missing: {root}")
        files = [root_path] if root_path.is_file() else list(root_path.rglob("*"))
        for path in files:
            if path.suffix not in {".kt", ".kts", ".java"}:
                continue
            for host, literal in literal_hosts_in_file(path):
                found.setdefault(host, []).append(f"{path.relative_to(repo_root)}: {literal}")
    return found


def validate_inventory_shape(inventory: dict[str, Any]) -> tuple[list[str], list[dict[str, Any]], str]:
    if inventory.get("schemaVersion") != 1:
        raise NetworkInventoryError("network inventory schemaVersion must be 1")
    if inventory.get("policyKind") != "networkEndpointInventory":
        raise NetworkInventoryError("network inventory policyKind is invalid")
    docs_path = require_string(inventory.get("docsPath"), "docsPath")
    scan = require_object(inventory.get("scan"), "scan")
    source_roots = require_string_list(scan.get("sourceRoots"), "scan.sourceRoots")
    endpoints_raw = inventory.get("endpoints")
    if not isinstance(endpoints_raw, list) or not endpoints_raw:
        raise NetworkInventoryError("endpoints must be a non-empty list")

    seen_ids: set[str] = set()
    endpoints: list[dict[str, Any]] = []
    for index, raw_endpoint in enumerate(endpoints_raw):
        endpoint = require_object(raw_endpoint, f"endpoints[{index}]")
        missing = sorted(REQUIRED_ENDPOINT_FIELDS - set(endpoint))
        if missing:
            raise NetworkInventoryError(f"endpoints[{index}] missing fields: {', '.join(missing)}")
        endpoint_id = require_string(endpoint["id"], f"endpoints[{index}].id")
        if endpoint_id in seen_ids:
            raise NetworkInventoryError(f"duplicate endpoint id: {endpoint_id}")
        seen_ids.add(endpoint_id)
        endpoint["hosts"] = require_string_list(endpoint["hosts"], f"{endpoint_id}.hosts")
        endpoint["schemes"] = require_string_list(endpoint["schemes"], f"{endpoint_id}.schemes")
        endpoint["sourceFiles"] = require_string_list(endpoint["sourceFiles"], f"{endpoint_id}.sourceFiles")
        for field in REQUIRED_ENDPOINT_FIELDS - {"id", "hosts", "schemes", "sourceFiles"}:
            endpoint[field] = require_string(endpoint[field], f"{endpoint_id}.{field}")
        endpoints.append(endpoint)
    return source_roots, endpoints, docs_path


def validate_docs(repo_root: Path, docs_path: str, endpoints: list[dict[str, Any]]) -> None:
    path = repo_root / docs_path
    if not path.is_file():
        raise NetworkInventoryError(f"network endpoint docs file is missing: {docs_path}")
    text = path.read_text(encoding="utf-8")
    missing_terms: list[str] = []
    for endpoint in endpoints:
        if endpoint["id"] not in text:
            missing_terms.append(endpoint["id"])
        missing_terms.extend(host for host in endpoint["hosts"] if host not in text)
    if missing_terms:
        raise NetworkInventoryError(f"network endpoint docs missing terms: {', '.join(sorted(set(missing_terms)))}")


def validate_inventory(repo_root: Path, inventory: dict[str, Any]) -> dict[str, Any]:
    source_roots, endpoints, docs_path = validate_inventory_shape(inventory)
    validate_docs(repo_root, docs_path, endpoints)

    inventory_hosts = {normalize_host(host) for endpoint in endpoints for host in endpoint["hosts"]}
    literal_hosts = collect_literal_hosts(repo_root, source_roots)
    unexpected = sorted(
        host for host in literal_hosts
        if normalize_host(host) not in inventory_hosts
    )
    if unexpected:
        details = "; ".join(f"{host}: {literal_hosts[host][0]}" for host in unexpected)
        raise NetworkInventoryError(f"unreviewed network literal hosts: {details}")

    return {
        "policyKind": inventory["policyKind"],
        "schemaVersion": inventory["schemaVersion"],
        "endpointCount": len(endpoints),
        "scannedLiteralHostCount": len(literal_hosts),
        "scannedLiteralHosts": sorted(literal_hosts),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root)
    try:
        inventory = require_object(read_json(repo_root / args.inventory), "network inventory")
        result = validate_inventory(repo_root, inventory)
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
