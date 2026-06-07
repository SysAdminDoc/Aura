#!/usr/bin/env python3
"""Validate Aura store screenshot and feature-graphic capture planning."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')
EXPECTED_STATUS = "capturePending"
REQUIRED_DOC_TERMS = {
    "Store assets",
    "Current status",
    "Required paths",
    "Planned shots",
    "Release gate",
    "Sources",
}
REQUIRED_SOURCE_URLS = {
    "https://support.google.com/googleplay/android-developer/answer/9866151",
    "https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/",
    "https://docs.fastlane.tools/actions/supply/",
}
REQUIRED_SHOT_IDS = {
    "wallpapers",
    "video-wallpapers",
    "sounds-editor",
    "settings-favorites-community",
}


class StoreAssetPipelineError(ValueError):
    """Raised when the store asset capture pipeline contract drifts."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura store asset capture pipeline.")
    parser.add_argument("--policy", default="docs/distribution/store-assets.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise StoreAssetPipelineError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise StoreAssetPipelineError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise StoreAssetPipelineError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise StoreAssetPipelineError(f"{label} must be a non-empty string")
    return value.strip()


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise StoreAssetPipelineError(f"{label} must be a boolean")
    return value


def require_int(value: Any, label: str) -> int:
    if not isinstance(value, int):
        raise StoreAssetPipelineError(f"{label} must be an integer")
    return value


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise StoreAssetPipelineError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise StoreAssetPipelineError(f"{label} contains duplicate values")
    return values


def require_dimension(value: Any, label: str) -> tuple[int, int]:
    if not isinstance(value, list) or len(value) != 2:
        raise StoreAssetPipelineError(f"{label} must be a [width, height] pair")
    width = require_int(value[0], f"{label}[0]")
    height = require_int(value[1], f"{label}[1]")
    if width <= 0 or height <= 0:
        raise StoreAssetPipelineError(f"{label} must contain positive dimensions")
    return width, height


def parse_package_name(repo_root: Path) -> str:
    text = read_text(repo_root, "app/build.gradle.kts", "app Gradle file")
    match = PACKAGE_RE.search(text)
    if not match:
        raise StoreAssetPipelineError("app/build.gradle.kts is missing applicationId")
    return match.group(1)


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    for url in urls:
        if not url.startswith("https://"):
            raise StoreAssetPipelineError(f"sourceUrls entry must be HTTPS: {url}")
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise StoreAssetPipelineError("sourceUrls missing required URLs: " + ", ".join(missing))
    return len(urls)


def validate_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "store asset docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise StoreAssetPipelineError(f"{docs_path} is missing required section text: {term}")
    for url in REQUIRED_SOURCE_URLS:
        if url not in docs_text:
            raise StoreAssetPipelineError(f"{docs_path} is missing source URL: {url}")
    for snippet in (
        EXPECTED_STATUS,
        require_string(policy.get("metadataRoot"), "metadataRoot"),
        require_string(policy.get("assetModeCommand"), "assetModeCommand"),
    ):
        if snippet not in docs_text:
            raise StoreAssetPipelineError(f"{docs_path} is missing policy snippet: {snippet}")


def validate_requirements(policy: dict[str, Any]) -> dict[str, object]:
    requirements = require_object(policy.get("requirements"), "requirements")
    icon_dimensions = require_dimension(requirements.get("iconDimensions"), "requirements.iconDimensions")
    feature_dimensions = require_dimension(
        requirements.get("featureGraphicDimensions"),
        "requirements.featureGraphicDimensions",
    )
    min_count = require_int(requirements.get("phoneScreenshotMinCount"), "requirements.phoneScreenshotMinCount")
    min_short_side = require_int(
        requirements.get("phoneScreenshotMinShortSide"),
        "requirements.phoneScreenshotMinShortSide",
    )
    if icon_dimensions != (512, 512):
        raise StoreAssetPipelineError("iconDimensions must be [512, 512]")
    if feature_dimensions != (1024, 500):
        raise StoreAssetPipelineError("featureGraphicDimensions must be [1024, 500]")
    if min_count < 4:
        raise StoreAssetPipelineError("phoneScreenshotMinCount must be at least 4")
    if min_short_side < 1080:
        raise StoreAssetPipelineError("phoneScreenshotMinShortSide must be at least 1080")
    if require_string(requirements.get("phoneScreenshotAspect"), "requirements.phoneScreenshotAspect") != "9:16":
        raise StoreAssetPipelineError("phoneScreenshotAspect must be 9:16")
    if not require_bool(requirements.get("noAlpha"), "requirements.noAlpha"):
        raise StoreAssetPipelineError("requirements.noAlpha must remain true")
    if not require_bool(requirements.get("actualInAppUiRequired"), "requirements.actualInAppUiRequired"):
        raise StoreAssetPipelineError("requirements.actualInAppUiRequired must remain true")
    for key in ("iconPath", "featureGraphicPath", "phoneScreenshotDirectory"):
        value = require_string(requirements.get(key), f"requirements.{key}")
        if Path(value).is_absolute() or ".." in Path(value).parts:
            raise StoreAssetPipelineError(f"requirements.{key} must stay inside the repository")
    return {
        "phoneScreenshotMinCount": min_count,
        "phoneScreenshotMinShortSide": min_short_side,
    }


def validate_planned_shots(policy: dict[str, Any]) -> int:
    raw_shots = policy.get("plannedShots")
    if not isinstance(raw_shots, list) or len(raw_shots) < 4:
        raise StoreAssetPipelineError("plannedShots must contain at least four shots")
    forbidden_terms = [term.lower() for term in require_string_list(policy.get("forbiddenTerms"), "forbiddenTerms")]
    seen_ids: set[str] = set()
    seen_targets: set[str] = set()
    for index, raw_shot in enumerate(raw_shots):
        shot = require_object(raw_shot, f"plannedShots[{index}]")
        shot_id = require_string(shot.get("id"), f"plannedShots[{index}].id")
        target_path = require_string(shot.get("targetPath"), f"plannedShots[{index}].targetPath")
        surface = require_string(shot.get("surface"), f"plannedShots[{index}].surface")
        coverage = require_string(shot.get("coverage"), f"plannedShots[{index}].coverage")
        alt_text = require_string(shot.get("altText"), f"plannedShots[{index}].altText")
        if shot_id in seen_ids:
            raise StoreAssetPipelineError(f"duplicate planned shot id: {shot_id}")
        if target_path in seen_targets:
            raise StoreAssetPipelineError(f"duplicate planned shot targetPath: {target_path}")
        if Path(target_path).is_absolute() or ".." in Path(target_path).parts:
            raise StoreAssetPipelineError(f"planned shot targetPath must stay inside the repository: {target_path}")
        if not target_path.startswith("fastlane/metadata/android/en-US/images/phoneScreenshots/"):
            raise StoreAssetPipelineError(f"planned shot targetPath must use phoneScreenshots: {target_path}")
        if not target_path.endswith(".png"):
            raise StoreAssetPipelineError(f"planned shot targetPath must be PNG: {target_path}")
        if len(alt_text) > 140:
            raise StoreAssetPipelineError(f"planned shot altText is too long: {shot_id}")
        if alt_text.lower().startswith(("photo of", "image of")):
            raise StoreAssetPipelineError(f"planned shot altText must not start with a generic image prefix: {shot_id}")
        combined = " ".join((surface, coverage, alt_text)).lower()
        for term in forbidden_terms:
            if term in combined:
                raise StoreAssetPipelineError(f"planned shot text contains forbidden term {term!r}: {shot_id}")
        seen_ids.add(shot_id)
        seen_targets.add(target_path)
    missing_ids = sorted(REQUIRED_SHOT_IDS - seen_ids)
    if missing_ids:
        raise StoreAssetPipelineError("plannedShots missing required IDs: " + ", ".join(missing_ids))
    return len(raw_shots)


def validate_workflow_and_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    command = "tools/store_asset_pipeline_check.py"
    asset_mode_command = require_string(policy.get("assetModeCommand"), "assetModeCommand")
    store_preflight = read_text(repo_root, "tools/store_metadata_preflight.py", "store metadata preflight")
    verify_workflow = read_text(repo_root, ".github/workflows/verify.yml", "verify workflow")
    release_workflow = read_text(repo_root, ".github/workflows/release.yml", "release workflow")
    release_dry_run = read_text(repo_root, "docs/distribution/release-dry-run.md", "release dry-run docs")
    release_signing = read_text(repo_root, "docs/distribution/release-signing.md", "release signing docs")
    release_metadata = read_text(
        repo_root,
        "docs/distribution/release-metadata-consistency.md",
        "release metadata docs",
    )
    if "--require-assets" not in store_preflight or "--min-phone-screenshots" not in store_preflight:
        raise StoreAssetPipelineError("store metadata preflight is missing asset mode flags")
    for label, text in (
        ("verify workflow", verify_workflow),
        ("release workflow", release_workflow),
        ("release dry-run docs", release_dry_run),
        ("release signing docs", release_signing),
        ("release metadata docs", release_metadata),
    ):
        if command not in text:
            raise StoreAssetPipelineError(f"{label} missing store asset pipeline command")
    if asset_mode_command not in release_dry_run and asset_mode_command not in release_metadata:
        raise StoreAssetPipelineError("release docs must mention the future asset-mode preflight command")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise StoreAssetPipelineError("schemaVersion must be 1")
    if policy.get("policyKind") != "storeAssetPipeline":
        raise StoreAssetPipelineError("policyKind must be storeAssetPipeline")
    if policy.get("status") != EXPECTED_STATUS:
        raise StoreAssetPipelineError(f"status must be {EXPECTED_STATUS}")
    package_name = require_string(policy.get("packageName"), "packageName")
    if parse_package_name(repo_root) != package_name:
        raise StoreAssetPipelineError("packageName does not match app/build.gradle.kts")
    validate_docs(repo_root, policy)
    source_url_count = validate_source_urls(policy)
    requirement_result = validate_requirements(policy)
    planned_shot_count = validate_planned_shots(policy)
    validate_workflow_and_docs(repo_root, policy)
    return {
        "status": "ok",
        "policyKind": "storeAssetPipeline",
        "packageName": package_name,
        "decision": EXPECTED_STATUS,
        "sourceUrlCount": source_url_count,
        "plannedShotCount": planned_shot_count,
        **requirement_result,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "store asset pipeline policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
