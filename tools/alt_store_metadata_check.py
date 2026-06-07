#!/usr/bin/env python3
"""Validate Aura's alternative-store disclosure matrix."""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
REQUIRED_DOC_TERMS = {
    "Alternative-store disclosure matrix",
    "Channel status",
    "Anti-feature notes",
    "Sensitive permissions",
    "Network services",
    "Generated wallpaper provider key behavior",
    "UGC moderation",
    "F-Droid state",
    "IzzyOnDroid submission notes",
    "Release checklist",
    "Sources",
}
REQUIRED_CHANNELS = {
    "github-releases": "supported",
    "obtainium": "supported",
    "izzyondroid": "candidate",
    "fdroid-mainline": "blocked",
}
REQUIRED_ANTI_FEATURES = {
    "NonFreeDep": True,
    "NonFreeNet": True,
    "Tracking": False,
    "Ads": False,
}
REQUIRED_SOURCE_URLS = {
    "https://f-droid.org/docs/Inclusion_Policy/",
    "https://f-droid.org/en/docs/Anti-Features/",
    "https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/",
    "https://izzyondroid.org/docs/general/YamlMetadata/",
    "https://apt.izzysoft.de/fdroid/index/apk",
}


class AltStoreMetadataError(ValueError):
    """Raised when the alternative-store disclosure matrix is incomplete."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura alternative-store metadata.")
    parser.add_argument("--policy", default="docs/distribution/alt-store-metadata.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise AltStoreMetadataError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise AltStoreMetadataError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise AltStoreMetadataError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise AltStoreMetadataError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise AltStoreMetadataError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise AltStoreMetadataError(f"{label} contains duplicate values")
    return values


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise AltStoreMetadataError(f"{label} must be a boolean")
    return value


def validate_package(repo_root: Path, expected_package: str) -> None:
    app_gradle = read_text(repo_root, "app/build.gradle.kts", "app Gradle file")
    match = PACKAGE_RE.search(app_gradle)
    if not match:
        raise AltStoreMetadataError("app/build.gradle.kts is missing applicationId")
    if match.group(1) != expected_package:
        raise AltStoreMetadataError(
            f"packageName mismatch: app Gradle={match.group(1)}, policy={expected_package}"
        )


def manifest_permissions(repo_root: Path) -> set[str]:
    manifest_path = repo_root / "app/src/main/AndroidManifest.xml"
    if not manifest_path.is_file():
        raise AltStoreMetadataError("AndroidManifest.xml is missing")
    root = ET.fromstring(manifest_path.read_text(encoding="utf-8"))
    permissions: set[str] = set()
    for node in root.findall("uses-permission"):
        name = node.attrib.get(f"{ANDROID_NS}name")
        if name:
            permissions.add(name)
    return permissions


def network_endpoint_ids(repo_root: Path) -> set[str]:
    inventory = require_object(
        read_json(repo_root / "docs/security/network-endpoints.json"),
        "network endpoint inventory",
    )
    endpoints = inventory.get("endpoints")
    if not isinstance(endpoints, list) or not endpoints:
        raise AltStoreMetadataError("network endpoint inventory must include endpoints")
    ids = {require_string(require_object(row, f"endpoints[{index}]").get("id"), f"endpoints[{index}].id") for index, row in enumerate(endpoints)}
    if len(ids) != len(endpoints):
        raise AltStoreMetadataError("network endpoint inventory contains duplicate IDs")
    return ids


def validate_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "alternative-store metadata docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise AltStoreMetadataError(f"{docs_path} is missing required section text: {term}")
    for source_url in REQUIRED_SOURCE_URLS:
        if source_url not in docs_text:
            raise AltStoreMetadataError(f"{docs_path} is missing source URL: {source_url}")


def validate_required_paths(repo_root: Path, policy: dict[str, Any]) -> None:
    for relative_path in require_string_list(policy.get("requiredEvidencePaths"), "requiredEvidencePaths"):
        if not (repo_root / relative_path).is_file():
            raise AltStoreMetadataError(f"requiredEvidencePaths entry is missing: {relative_path}")


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise AltStoreMetadataError("sourceUrls missing required URLs: " + ", ".join(missing))
    return len(urls)


def validate_channels(policy: dict[str, Any]) -> int:
    rows = policy.get("channels")
    if not isinstance(rows, list) or not rows:
        raise AltStoreMetadataError("channels must be a non-empty list")
    statuses: dict[str, str] = {}
    for index, raw_row in enumerate(rows):
        row = require_object(raw_row, f"channels[{index}]")
        channel_id = require_string(row.get("id"), f"channels[{index}].id")
        status = require_string(row.get("status"), f"{channel_id}.status")
        require_string(row.get("name"), f"{channel_id}.name")
        require_string(row.get("artifact"), f"{channel_id}.artifact")
        require_string(row.get("disclosure"), f"{channel_id}.disclosure")
        if channel_id in statuses:
            raise AltStoreMetadataError(f"duplicate channel row: {channel_id}")
        statuses[channel_id] = status
    for channel_id, expected_status in REQUIRED_CHANNELS.items():
        if statuses.get(channel_id) != expected_status:
            raise AltStoreMetadataError(
                f"{channel_id} status must be {expected_status}, got {statuses.get(channel_id)}"
            )
    return len(rows)


def validate_anti_features(repo_root: Path, policy: dict[str, Any]) -> int:
    rows = policy.get("antiFeatures")
    if not isinstance(rows, list) or not rows:
        raise AltStoreMetadataError("antiFeatures must be a non-empty list")
    features: dict[str, bool] = {}
    for index, raw_row in enumerate(rows):
        row = require_object(raw_row, f"antiFeatures[{index}]")
        feature_id = require_string(row.get("id"), f"antiFeatures[{index}].id")
        applies = require_bool(row.get("applies"), f"{feature_id}.applies")
        details = require_object(row.get("details"), f"{feature_id}.details")
        require_string(details.get("en-US"), f"{feature_id}.details.en-US")
        for ref in require_string_list(row.get("evidenceRefs"), f"{feature_id}.evidenceRefs"):
            if not (repo_root / ref).is_file():
                raise AltStoreMetadataError(f"{feature_id}.evidenceRefs entry is missing: {ref}")
        if feature_id in features:
            raise AltStoreMetadataError(f"duplicate anti-feature row: {feature_id}")
        features[feature_id] = applies
    for feature_id, expected in REQUIRED_ANTI_FEATURES.items():
        if features.get(feature_id) is not expected:
            raise AltStoreMetadataError(
                f"{feature_id}.applies must be {str(expected).lower()}, got {features.get(feature_id)}"
            )
    return len(rows)


def validate_dependency_markers(repo_root: Path, policy: dict[str, Any]) -> int:
    rows = policy.get("proprietaryDependencyMarkers")
    if not isinstance(rows, list) or not rows:
        raise AltStoreMetadataError("proprietaryDependencyMarkers must be a non-empty list")
    seen: set[str] = set()
    for index, raw_row in enumerate(rows):
        row = require_object(raw_row, f"proprietaryDependencyMarkers[{index}]")
        marker_id = require_string(row.get("id"), f"proprietaryDependencyMarkers[{index}].id")
        path = require_string(row.get("path"), f"{marker_id}.path")
        marker = require_string(row.get("marker"), f"{marker_id}.marker")
        disclosure = require_string(row.get("disclosure"), f"{marker_id}.disclosure")
        if marker_id in seen:
            raise AltStoreMetadataError(f"duplicate proprietaryDependencyMarkers row: {marker_id}")
        seen.add(marker_id)
        if marker not in read_text(repo_root, path, f"{marker_id} marker file"):
            raise AltStoreMetadataError(f"{marker_id} marker is missing from {path}: {marker}")
        if len(disclosure) < 20:
            raise AltStoreMetadataError(f"{marker_id}.disclosure is too short")
    return len(rows)


def validate_permissions(repo_root: Path, policy: dict[str, Any]) -> int:
    rows = policy.get("permissions")
    if not isinstance(rows, list) or not rows:
        raise AltStoreMetadataError("permissions must be a non-empty list")
    names: set[str] = set()
    for index, raw_row in enumerate(rows):
        row = require_object(raw_row, f"permissions[{index}]")
        name = require_string(row.get("name"), f"permissions[{index}].name")
        require_string(row.get("purpose"), f"{name}.purpose")
        require_string(row.get("disclosure"), f"{name}.disclosure")
        if name in names:
            raise AltStoreMetadataError(f"duplicate permission row: {name}")
        names.add(name)
    actual = manifest_permissions(repo_root)
    if names != actual:
        missing = sorted(actual - names)
        extra = sorted(names - actual)
        raise AltStoreMetadataError(f"permission rows drifted; missing={missing}; extra={extra}")
    return len(rows)


def validate_network_services(repo_root: Path, policy: dict[str, Any]) -> int:
    rows = policy.get("networkServices")
    if not isinstance(rows, list) or not rows:
        raise AltStoreMetadataError("networkServices must be a non-empty list")
    ids: set[str] = set()
    for index, raw_row in enumerate(rows):
        row = require_object(raw_row, f"networkServices[{index}]")
        service_id = require_string(row.get("id"), f"networkServices[{index}].id")
        require_string(row.get("disclosure"), f"{service_id}.disclosure")
        if service_id in ids:
            raise AltStoreMetadataError(f"duplicate network service row: {service_id}")
        ids.add(service_id)
    actual = network_endpoint_ids(repo_root)
    if ids != actual:
        missing = sorted(actual - ids)
        extra = sorted(ids - actual)
        raise AltStoreMetadataError(f"network service rows drifted; missing={missing}; extra={extra}")
    return len(rows)


def validate_izzy_submission(policy: dict[str, Any]) -> None:
    section = require_object(policy.get("izzySubmission"), "izzySubmission")
    if require_string(section.get("status"), "izzySubmission.status") != "ownerSubmissionRequired":
        raise AltStoreMetadataError("izzySubmission.status must remain ownerSubmissionRequired")
    notes = require_string_list(section.get("notes"), "izzySubmission.notes")
    required_terms = ("signed GitHub Release APK", "anti-feature", "F-Droid mainline")
    joined = "\n".join(notes)
    for term in required_terms:
        if term not in joined:
            raise AltStoreMetadataError(f"izzySubmission.notes missing term: {term}")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise AltStoreMetadataError("schemaVersion must be 1")
    if policy.get("policyKind") != "altStoreDisclosureMatrix":
        raise AltStoreMetadataError("policyKind must be altStoreDisclosureMatrix")
    package_name = require_string(policy.get("packageName"), "packageName")
    validate_package(repo_root, package_name)
    validate_docs(repo_root, policy)
    validate_required_paths(repo_root, policy)
    source_url_count = validate_source_urls(policy)
    channel_count = validate_channels(policy)
    anti_feature_count = validate_anti_features(repo_root, policy)
    marker_count = validate_dependency_markers(repo_root, policy)
    permission_count = validate_permissions(repo_root, policy)
    network_service_count = validate_network_services(repo_root, policy)
    validate_izzy_submission(policy)
    return {
        "status": "ok",
        "policyKind": "altStoreDisclosureMatrix",
        "packageName": package_name,
        "channelCount": channel_count,
        "antiFeatureCount": anti_feature_count,
        "proprietaryDependencyMarkerCount": marker_count,
        "permissionCount": permission_count,
        "networkServiceCount": network_service_count,
        "sourceUrlCount": source_url_count,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "alternative-store metadata policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, ET.ParseError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
