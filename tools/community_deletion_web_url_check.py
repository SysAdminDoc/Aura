#!/usr/bin/env python3
"""Validate the hosted account deletion web URL publication manifest."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


VALID_STATUSES = {"pendingOwnerUrl", "published"}


class WebUrlCheckError(ValueError):
    """Raised when the web deletion URL manifest is inconsistent."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Check Aura hosted account deletion web URL publication metadata."
    )
    parser.add_argument("--manifest", default="docs/support/community-account-deletion-web-url.json")
    parser.add_argument("--repo-root", default=".", help="Repository root used to resolve referenced docs.")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str):
        raise WebUrlCheckError(f"{label} must be a string")
    return value.strip()


def validate_https_url(url: str) -> str:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc:
        raise WebUrlCheckError("Published account deletion URL must be an HTTPS URL")
    return url


def referenced_text(repo_root: Path, relative_path: str, label: str) -> str:
    if not relative_path or Path(relative_path).is_absolute() or ".." in Path(relative_path).parts:
        raise WebUrlCheckError(f"{label} must be a repo-relative path")
    path = repo_root / relative_path
    if not path.exists():
        raise WebUrlCheckError(f"{label} does not exist: {relative_path}")
    return path.read_text(encoding="utf-8")


def validate_manifest(manifest: Any, repo_root: Path) -> dict[str, Any]:
    if not isinstance(manifest, dict):
        raise WebUrlCheckError("Web URL manifest must be a JSON object")
    if manifest.get("schemaVersion") != 1:
        raise WebUrlCheckError("Web URL manifest schemaVersion must be 1")

    status = require_string(manifest.get("status"), "status")
    if status not in VALID_STATUSES:
        raise WebUrlCheckError(f"status must be one of: {', '.join(sorted(VALID_STATUSES))}")

    privacy_path = require_string(manifest.get("privacyPolicyPath"), "privacyPolicyPath")
    support_path = require_string(manifest.get("supportDocPath"), "supportDocPath")
    require_string(manifest.get("lastReviewed"), "lastReviewed")
    require_string(manifest.get("requiredBefore"), "requiredBefore")

    public_url = require_string(manifest.get("publicUrl", ""), "publicUrl")
    privacy_text = referenced_text(repo_root, privacy_path, "privacyPolicyPath")
    support_text = referenced_text(repo_root, support_path, "supportDocPath")

    result = {
        "schemaVersion": 1,
        "status": status,
        "privacyPolicyPath": privacy_path,
        "supportDocPath": support_path,
    }

    if status == "pendingOwnerUrl":
        if public_url:
            raise WebUrlCheckError("pendingOwnerUrl status must keep publicUrl empty")
        if "pending owner publication" not in privacy_text:
            raise WebUrlCheckError("Privacy policy must state pending owner publication while URL is pending")
        result["publicationReady"] = False
        return result

    public_url = validate_https_url(public_url)
    if public_url not in privacy_text:
        raise WebUrlCheckError("Published account deletion URL is missing from the privacy policy")
    if public_url not in support_text:
        raise WebUrlCheckError("Published account deletion URL is missing from the support doc")
    result["publicationReady"] = True
    result["publicUrl"] = public_url
    return result


def main() -> int:
    args = parse_args()
    try:
        result = validate_manifest(read_json(Path(args.manifest)), Path(args.repo_root))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
