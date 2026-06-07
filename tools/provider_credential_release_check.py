#!/usr/bin/env python3
"""Check that provider credentials are not bundled into release builds."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


PROVIDER_KEYS = [
    {"property": "pexels.api.key", "buildConfig": "PEXELS_API_KEY"},
    {"property": "pixabay.api.key", "buildConfig": "PIXABAY_API_KEY"},
    {"property": "freesound.api.key", "buildConfig": "FREESOUND_API_KEY"},
    {"property": "soundcloud.client.id", "buildConfig": "SOUNDCLOUD_CLIENT_ID"},
    {"property": "stability.ai.key", "buildConfig": "STABILITY_AI_KEY"},
]


class ProviderCredentialReleaseError(ValueError):
    """Raised when release provider credential validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura release provider credential policy.")
    parser.add_argument("--app-gradle", default="app/build.gradle.kts")
    parser.add_argument("--release-workflow", default=".github/workflows/release.yml")
    parser.add_argument("--local-properties")
    parser.add_argument(
        "--allow-nonblank-local-provider-keys",
        action="store_true",
        help="Return a warning status instead of failing when local provider keys are nonblank.",
    )
    return parser.parse_args()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def validate_app_gradle(path: Path) -> list[str]:
    text = read_text(path)
    checked: list[str] = []
    for row in PROVIDER_KEYS:
        build_config = row["buildConfig"]
        prop = row["property"]
        if f'buildConfigField("String", "{build_config}"' not in text:
            raise ProviderCredentialReleaseError(f"{path} is missing BuildConfig field {build_config}")
        expected = f'localProps.getProperty("{prop}", "")'
        if expected not in text:
            raise ProviderCredentialReleaseError(f"{path} must default {prop} to a blank release value")
        checked.append(prop)
    return checked


def validate_release_workflow(path: Path) -> list[str]:
    text = read_text(path)
    checked: list[str] = []
    for row in PROVIDER_KEYS:
        prop = row["property"]
        blank_assignment = re.compile(rf"^\s*printf\s+'{re.escape(prop)}=\\n'\s*$", re.MULTILINE)
        if not blank_assignment.search(text):
            raise ProviderCredentialReleaseError(f"{path} must write blank {prop} in release local.properties")
        checked.append(prop)
    return checked


def parse_properties(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    props: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or line.startswith("!"):
            continue
        separator_index = -1
        for separator in ("=", ":"):
            candidate = line.find(separator)
            if candidate >= 0 and (separator_index == -1 or candidate < separator_index):
                separator_index = candidate
        if separator_index == -1:
            props[line] = ""
            continue
        key = line[:separator_index].strip()
        value = line[separator_index + 1 :].strip()
        props[key] = value
    return props


def validate_local_properties(path: Path | None, allow_nonblank: bool) -> dict[str, Any]:
    if path is None:
        return {"checked": False, "status": "notRequested"}
    props = parse_properties(path)
    nonblank = sorted(
        row["property"]
        for row in PROVIDER_KEYS
        if props.get(row["property"], "").strip()
    )
    if nonblank and not allow_nonblank:
        raise ProviderCredentialReleaseError(
            f"{path} contains nonblank provider keys that would be bundled into BuildConfig: {', '.join(nonblank)}"
        )
    return {
        "checked": True,
        "path": str(path),
        "nonblankProviderKeys": nonblank,
        "status": "nonblankAllowed" if nonblank else "ok",
    }


def validate_provider_credentials(
    app_gradle: Path,
    release_workflow: Path,
    local_properties: Path | None,
    allow_nonblank: bool = False,
) -> dict[str, Any]:
    gradle_keys = validate_app_gradle(app_gradle)
    workflow_keys = validate_release_workflow(release_workflow)
    local_result = validate_local_properties(local_properties, allow_nonblank)
    return {
        "appGradle": str(app_gradle),
        "buildConfigProviderKeys": gradle_keys,
        "localProperties": local_result,
        "releaseWorkflow": str(release_workflow),
        "releaseWorkflowBlankProviderKeys": workflow_keys,
        "status": "warning" if local_result.get("status") == "nonblankAllowed" else "ok",
    }


def main() -> int:
    args = parse_args()
    try:
        result = validate_provider_credentials(
            Path(args.app_gradle),
            Path(args.release_workflow),
            Path(args.local_properties) if args.local_properties else None,
            args.allow_nonblank_local_provider_keys,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
