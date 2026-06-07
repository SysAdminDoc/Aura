#!/usr/bin/env python3
"""Dry-run or apply a private account deletion package through RTDB REST."""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from tools.community_account_deletion_executor_package import validate_executor_package
from tools.community_account_deletion_plan import read_json
from tools.community_account_deletion_review import ReviewError, require_non_empty_string, sha256_json, utc_now


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Dry-run or apply an Aura account deletion executor package through RTDB REST."
    )
    parser.add_argument("--package", required=True, help="JSON output from community_account_deletion_executor_package.py.")
    parser.add_argument("--database-url", required=True, help="Realtime Database root URL, for example https://project.firebaseio.com.")
    parser.add_argument("--mode", choices=("dry-run", "apply"), default="dry-run", help="Default is dry-run and never contacts Firebase.")
    parser.add_argument("--access-token", help="OAuth2 access token. Apply mode also reads FIREBASE_DATABASE_ACCESS_TOKEN.")
    parser.add_argument("--confirm-request-code", help="Required in apply mode; must equal the package requestCode.")
    parser.add_argument("--confirm-plan-hash", help="Required in apply mode; must equal the package planHash.")
    parser.add_argument("--timeout-seconds", type=int, default=30, help="HTTP timeout for apply mode.")
    parser.add_argument("--output", help="Optional executor receipt path. Defaults to stdout.")
    return parser.parse_args()


def database_endpoint(database_url: str) -> str:
    parsed = urllib.parse.urlparse(database_url.strip())
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ReviewError("Database URL must be an http(s) URL")
    if parsed.scheme != "https" and parsed.hostname not in {"localhost", "127.0.0.1", "::1"}:
        raise ReviewError("Non-HTTPS database URLs are allowed only for local emulator hosts")
    root = urllib.parse.urlunparse((parsed.scheme, parsed.netloc, parsed.path.rstrip("/"), "", "", ""))
    return f"{root}/.json"


def database_label(database_url: str) -> str:
    parsed = urllib.parse.urlparse(database_url.strip())
    return parsed.netloc or database_url.strip()


def patch_payload(updates: dict[str, Any]) -> dict[str, Any]:
    payload: dict[str, Any] = {}
    for path, value in sorted(updates.items()):
        if not isinstance(path, str) or not path.startswith("/") or path == "/":
            raise ReviewError("Executor package update paths must be absolute non-root paths")
        if value is not None:
            raise ReviewError("Executor package updates must be null deletes")
        payload[path.lstrip("/")] = None
    return payload


def validate_apply_confirmations(package: dict[str, Any], request_code: str | None, plan_hash: str | None) -> None:
    if package.get("requestCode") != request_code:
        raise ReviewError("Apply mode requires --confirm-request-code to match the package requestCode")
    if package.get("planHash") != plan_hash:
        raise ReviewError("Apply mode requires --confirm-plan-hash to match the package planHash")


def apply_patch_request(endpoint: str, payload: dict[str, Any], access_token: str, timeout_seconds: int) -> tuple[int, bytes]:
    token = require_non_empty_string(access_token, "Access token")
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, sort_keys=True, separators=(",", ":")).encode("utf-8"),
        method="PATCH",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return int(response.status), response.read()


def execute_package(
    package: Any,
    database_url: str,
    mode: str = "dry-run",
    access_token: str | None = None,
    confirm_request_code: str | None = None,
    confirm_plan_hash: str | None = None,
    timeout_seconds: int = 30,
    executed_at: str | None = None,
) -> dict[str, Any]:
    package_object = validate_executor_package(package)
    updates = package_object["updates"]
    payload = patch_payload(updates)
    endpoint = database_endpoint(database_url)
    package_hash = sha256_json(package_object)
    payload_hash = sha256_json(payload)

    receipt: dict[str, Any] = {
        "schemaVersion": 1,
        "executionMode": mode,
        "databaseHost": database_label(database_url),
        "requestCode": package_object.get("requestCode"),
        "updateCount": len(payload),
        "updatesHash": package_object.get("updatesHash"),
        "payloadHash": payload_hash,
        "packageHash": package_hash,
        "planHash": package_object.get("planHash"),
        "executedAt": executed_at or utc_now(),
    }

    if mode == "dry-run":
        receipt["executionStatus"] = "dryRun"
        receipt["nextRequiredGate"] = "Apply mode requires matching request-code and plan-hash confirmations plus an OAuth2 access token."
        return receipt

    if mode != "apply":
        raise ReviewError("Execution mode must be dry-run or apply")

    validate_apply_confirmations(package_object, confirm_request_code, confirm_plan_hash)
    token = access_token or os.environ.get("FIREBASE_DATABASE_ACCESS_TOKEN", "")
    status, body = apply_patch_request(endpoint, payload, token, timeout_seconds)
    if status != 200:
        raise ReviewError(f"Realtime Database REST PATCH returned HTTP {status}")
    receipt["executionStatus"] = "applied"
    receipt["httpStatus"] = status
    receipt["responseHash"] = sha256_json(json.loads(body.decode("utf-8")) if body else None)
    return receipt


def dump_receipt(receipt: dict[str, Any]) -> str:
    return json.dumps(receipt, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        receipt = execute_package(
            read_json(Path(args.package)),
            args.database_url,
            mode=args.mode,
            access_token=args.access_token,
            confirm_request_code=args.confirm_request_code,
            confirm_plan_hash=args.confirm_plan_hash,
            timeout_seconds=args.timeout_seconds,
        )
    except (OSError, ValueError, urllib.error.URLError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    receipt_text = dump_receipt(receipt)
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
