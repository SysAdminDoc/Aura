#!/usr/bin/env python3
"""Validate Aura rotation-trigger boot behavior and permission posture."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
EXPECTED_STATUS = "permissionRemoved"
REQUIRED_DOC_TERMS = {
    "Rotation trigger boot behavior",
    "Current decision",
    "Behavior",
    "Release gate",
    "Future boot receiver option",
    "Sources",
}
REQUIRED_SOURCE_URLS = {
    "https://developer.android.com/reference/android/content/Intent#ACTION_BOOT_COMPLETED",
    "https://developer.android.com/about/versions/14/changes/fgs-types-required",
    "https://support.google.com/googleplay/android-developer/answer/13392821",
}


class RotationBootPermissionError(ValueError):
    """Raised when Aura's boot permission decision drifts."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura rotation-trigger boot behavior.")
    parser.add_argument("--policy", default="docs/rotation-trigger-boot-behavior.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise RotationBootPermissionError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise RotationBootPermissionError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise RotationBootPermissionError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise RotationBootPermissionError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise RotationBootPermissionError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise RotationBootPermissionError(f"{label} contains duplicate values")
    return values


def manifest_permissions(repo_root: Path, relative_path: str) -> set[str]:
    root = ET.fromstring(read_text(repo_root, relative_path, "Android manifest"))
    names: set[str] = set()
    for node in root.findall("uses-permission"):
        name = node.attrib.get(f"{ANDROID_NS}name")
        if name:
            names.add(name)
    return names


def source_files(repo_root: Path, source_roots: list[str]) -> list[Path]:
    files: list[Path] = []
    for relative_root in source_roots:
        root = repo_root / relative_root
        if root.is_file():
            files.append(root)
            continue
        if not root.is_dir():
            raise RotationBootPermissionError(f"sourceRoots entry is missing: {relative_root}")
        for path in root.rglob("*"):
            if path.is_file() and path.suffix.lower() in {".kt", ".java", ".xml"}:
                files.append(path)
    if not files:
        raise RotationBootPermissionError("sourceRoots did not match any source files")
    return files


def validate_docs(repo_root: Path, policy: dict[str, Any], removed_permission: str) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "rotation boot behavior docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise RotationBootPermissionError(f"{docs_path} is missing required section text: {term}")
    for term in (EXPECTED_STATUS, removed_permission, "Rotation triggers resume after opening Aura"):
        if term not in docs_text:
            raise RotationBootPermissionError(f"{docs_path} is missing policy term: {term}")
    for url in REQUIRED_SOURCE_URLS:
        if url not in docs_text:
            raise RotationBootPermissionError(f"{docs_path} is missing source URL: {url}")

    for relative_path in require_string_list(policy.get("requiredDocs"), "requiredDocs"):
        text = read_text(repo_root, relative_path, f"required doc {relative_path}")
        if relative_path == docs_path:
            continue
        if removed_permission in text:
            raise RotationBootPermissionError(f"{relative_path} still mentions {removed_permission}")
        if "boot scheduling" in text.lower():
            raise RotationBootPermissionError(f"{relative_path} still claims boot scheduling")


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    for url in urls:
        if not url.startswith("https://"):
            raise RotationBootPermissionError(f"sourceUrls entry must be HTTPS: {url}")
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise RotationBootPermissionError("sourceUrls missing required URLs: " + ", ".join(missing))
    return len(urls)


def validate_source_terms(repo_root: Path, policy: dict[str, Any]) -> None:
    forbidden_terms = require_string_list(policy.get("forbiddenSourceTerms"), "forbiddenSourceTerms")
    files = source_files(repo_root, require_string_list(policy.get("sourceRoots"), "sourceRoots"))
    for path in files:
        text = path.read_text(encoding="utf-8")
        for term in forbidden_terms:
            if term in text:
                raise RotationBootPermissionError(
                    f"forbidden boot-completed source term {term!r} found in {path.relative_to(repo_root)}"
                )


def validate_workflow_wiring(repo_root: Path) -> None:
    command = "tools/rotation_boot_permission_check.py"
    for label, relative_path in (
        ("verify workflow", ".github/workflows/verify.yml"),
        ("release workflow", ".github/workflows/release.yml"),
        ("release dry-run docs", "docs/distribution/release-dry-run.md"),
        ("release signing docs", "docs/distribution/release-signing.md"),
        ("release metadata docs", "docs/distribution/release-metadata-consistency.md"),
    ):
        text = read_text(repo_root, relative_path, label)
        if command not in text:
            raise RotationBootPermissionError(f"{label} missing rotation boot permission command")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise RotationBootPermissionError("schemaVersion must be 1")
    if policy.get("policyKind") != "rotationTriggerBootBehavior":
        raise RotationBootPermissionError("policyKind must be rotationTriggerBootBehavior")
    if policy.get("status") != EXPECTED_STATUS:
        raise RotationBootPermissionError(f"status must be {EXPECTED_STATUS}")
    manifest_path = require_string(policy.get("manifestPath"), "manifestPath")
    removed_permission = require_string(policy.get("removedPermission"), "removedPermission")
    if removed_permission in manifest_permissions(repo_root, manifest_path):
        raise RotationBootPermissionError(f"{removed_permission} must not be declared")
    validate_docs(repo_root, policy, removed_permission)
    source_url_count = validate_source_urls(policy)
    validate_source_terms(repo_root, policy)
    validate_workflow_wiring(repo_root)
    return {
        "status": "ok",
        "policyKind": "rotationTriggerBootBehavior",
        "decision": EXPECTED_STATUS,
        "removedPermission": removed_permission,
        "sourceUrlCount": source_url_count,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "rotation boot behavior policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
