#!/usr/bin/env python3
"""Validate Aura's manifest permission privacy/data-safety matrix."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
REQUIRED_PERMISSION_FIELDS = {
    "name",
    "category",
    "purpose",
    "userAction",
    "dataTypes",
    "collectionStatus",
    "sharingStatus",
    "retention",
    "deletionPath",
    "denialBehavior",
    "playDeclaration",
}
SUPPORTED_COLLECTION_STATUSES = {
    "localOnly",
    "notCollected",
    "userInitiatedCollection",
    "featureDependentCollection",
}
SUPPORTED_SHARING_STATUSES = {
    "notShared",
    "sharedWhenUploaded",
    "sharedWithSelectedProviders",
    "sharedWithWeatherProvider",
}


class PrivacyDataSafetyError(ValueError):
    """Raised when Aura's data-safety matrix is stale."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura privacy data-safety matrix.")
    parser.add_argument("--policy", default="docs/privacy/data-safety.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise PrivacyDataSafetyError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise PrivacyDataSafetyError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise PrivacyDataSafetyError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise PrivacyDataSafetyError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise PrivacyDataSafetyError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise PrivacyDataSafetyError(f"{label} contains duplicate values")
    return values


def parse_manifest_permissions(path: Path) -> dict[str, int | None]:
    if not path.is_file():
        raise PrivacyDataSafetyError(f"manifest is missing: {path}")
    root = ET.fromstring(path.read_text(encoding="utf-8"))
    permissions: dict[str, int | None] = {}
    for element in root.findall("uses-permission"):
        name = element.attrib.get(f"{ANDROID_NS}name")
        if not name:
            raise PrivacyDataSafetyError("uses-permission is missing android:name")
        if name in permissions:
            raise PrivacyDataSafetyError(f"duplicate manifest permission: {name}")
        max_sdk_raw = element.attrib.get(f"{ANDROID_NS}maxSdkVersion")
        permissions[name] = int(max_sdk_raw) if max_sdk_raw else None
    return permissions


def validate_permission_rows(manifest_permissions: dict[str, int | None], rows_raw: Any) -> list[dict[str, Any]]:
    if not isinstance(rows_raw, list) or not rows_raw:
        raise PrivacyDataSafetyError("permissions must be a non-empty list")

    rows: list[dict[str, Any]] = []
    row_names: set[str] = set()
    for index, raw_row in enumerate(rows_raw):
        row = require_object(raw_row, f"permissions[{index}]")
        missing = sorted(REQUIRED_PERMISSION_FIELDS - set(row))
        if missing:
            raise PrivacyDataSafetyError(f"permissions[{index}] missing fields: {', '.join(missing)}")
        name = require_string(row.get("name"), f"permissions[{index}].name")
        if name in row_names:
            raise PrivacyDataSafetyError(f"duplicate permission row: {name}")
        row_names.add(name)

        for field in REQUIRED_PERMISSION_FIELDS - {"dataTypes"}:
            require_string(row.get(field), f"{name}.{field}")
        require_string_list(row.get("dataTypes"), f"{name}.dataTypes")

        collection_status = require_string(row.get("collectionStatus"), f"{name}.collectionStatus")
        if collection_status not in SUPPORTED_COLLECTION_STATUSES:
            raise PrivacyDataSafetyError(f"{name}.collectionStatus is unsupported")
        sharing_status = require_string(row.get("sharingStatus"), f"{name}.sharingStatus")
        if sharing_status not in SUPPORTED_SHARING_STATUSES:
            raise PrivacyDataSafetyError(f"{name}.sharingStatus is unsupported")

        expected_max_sdk = manifest_permissions.get(name)
        row_max_sdk = row.get("maxSdkVersion")
        if row_max_sdk is not None and not isinstance(row_max_sdk, int):
            raise PrivacyDataSafetyError(f"{name}.maxSdkVersion must be an integer")
        if expected_max_sdk != row_max_sdk:
            raise PrivacyDataSafetyError(
                f"{name}.maxSdkVersion mismatch: manifest={expected_max_sdk}, policy={row_max_sdk}"
            )
        rows.append(row)

    manifest_names = set(manifest_permissions)
    if row_names != manifest_names:
        missing = sorted(manifest_names - row_names)
        extra = sorted(row_names - manifest_names)
        details = []
        if missing:
            details.append(f"missing rows: {', '.join(missing)}")
        if extra:
            details.append(f"extra rows: {', '.join(extra)}")
        raise PrivacyDataSafetyError(f"permission matrix does not match manifest ({'; '.join(details)})")

    return rows


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise PrivacyDataSafetyError("privacy data-safety schemaVersion must be 1")
    if policy.get("policyKind") != "privacyDataSafetyMatrix":
        raise PrivacyDataSafetyError("privacy data-safety policyKind is invalid")

    manifest_path = require_string(policy.get("manifest"), "manifest")
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    privacy_policy_path = require_string(policy.get("privacyPolicy"), "privacyPolicy")
    manifest_permissions = parse_manifest_permissions(repo_root / manifest_path)
    rows = validate_permission_rows(manifest_permissions, policy.get("permissions"))

    docs_text = read_text(repo_root, docs_path, "data-safety docs")
    privacy_text = read_text(repo_root, privacy_policy_path, "privacy policy").lower()
    for row in rows:
        name = require_string(row.get("name"), "permission.name")
        if name not in docs_text:
            raise PrivacyDataSafetyError(f"{docs_path} is missing permission row for {name}")
    for required_term in ("no ads", "cross-app tracking", "anonymous firebase identity", "generated wallpaper prompts"):
        if required_term not in " ".join(privacy_text.split()):
            raise PrivacyDataSafetyError(f"{privacy_policy_path} is missing privacy term: {required_term}")

    source_urls = require_string_list(policy.get("sourceUrls"), "sourceUrls")
    for url in source_urls:
        if not url.startswith("https://"):
            raise PrivacyDataSafetyError(f"sourceUrls must use HTTPS: {url}")

    sensitive_count = sum(
        1
        for row in rows
        if row["collectionStatus"] != "localOnly" or row["sharingStatus"] != "notShared"
    )
    return {
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "status": "ok",
        "manifestPermissionCount": len(manifest_permissions),
        "matrixPermissionCount": len(rows),
        "sensitiveOrSharedRowCount": sensitive_count,
        "sourceUrlCount": len(source_urls),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json((repo_root / args.policy).resolve()), "privacy data-safety policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
