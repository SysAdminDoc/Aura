#!/usr/bin/env python3
"""Validate Aura rotation-trigger foreground-service policy posture."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
EXPECTED_POLICY_KIND = "rotationTriggerForegroundServicePolicy"
EXPECTED_STATUS = "ownerActionRequired"
REQUIRED_DOC_TERMS = {
    "Rotation trigger foreground-service policy",
    "Current decision",
    "Service contract",
    "Play Console declaration packet",
    "Owner evidence",
    "Release gate",
    "Sources",
}
REQUIRED_SOURCE_URLS = {
    "https://developer.android.com/develop/background-work/services/fgs/service-types#special-use",
    "https://developer.android.com/about/versions/14/changes/fgs-types-required",
    "https://support.google.com/googleplay/android-developer/answer/13392821",
}


class RotationFgsPolicyError(ValueError):
    """Raised when the rotation-trigger foreground-service policy drifts."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura rotation-trigger foreground-service policy.")
    parser.add_argument("--policy", default="docs/rotation-trigger-fgs-policy.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise RotationFgsPolicyError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise RotationFgsPolicyError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise RotationFgsPolicyError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise RotationFgsPolicyError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise RotationFgsPolicyError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise RotationFgsPolicyError(f"{label} contains duplicate values")
    return values


def manifest_root(repo_root: Path, relative_path: str) -> ET.Element:
    try:
        return ET.fromstring(read_text(repo_root, relative_path, "Android manifest"))
    except ET.ParseError as exc:
        raise RotationFgsPolicyError(f"Android manifest is invalid XML: {exc}") from exc


def manifest_permissions(root: ET.Element) -> set[str]:
    permissions: set[str] = set()
    for node in root.findall("uses-permission"):
        name = node.attrib.get(f"{ANDROID_NS}name")
        if name:
            permissions.add(name)
    return permissions


def find_service(root: ET.Element, service_name: str) -> ET.Element:
    for service in root.findall(".//service"):
        if service.attrib.get(f"{ANDROID_NS}name") == service_name:
            return service
    raise RotationFgsPolicyError(f"manifest is missing service: {service_name}")


def validate_manifest(repo_root: Path, policy: dict[str, Any]) -> None:
    root = manifest_root(repo_root, require_string(policy.get("manifestPath"), "manifestPath"))
    permissions = manifest_permissions(root)
    for permission in require_string_list(policy.get("requiredPermissions"), "requiredPermissions"):
        if permission not in permissions:
            raise RotationFgsPolicyError(f"manifest is missing required permission: {permission}")
    for permission in require_string_list(policy.get("forbiddenManifestPermissions"), "forbiddenManifestPermissions"):
        if permission in permissions:
            raise RotationFgsPolicyError(f"manifest declares forbidden permission: {permission}")

    service_name = require_string(policy.get("serviceName"), "serviceName")
    service = find_service(root, service_name)
    if service.attrib.get(f"{ANDROID_NS}exported") != "false":
        raise RotationFgsPolicyError(f"{service_name} must be exported=false")
    service_type = require_string(policy.get("foregroundServiceType"), "foregroundServiceType")
    declared_types = service.attrib.get(f"{ANDROID_NS}foregroundServiceType", "")
    if service_type not in {item.strip() for item in declared_types.split("|")}:
        raise RotationFgsPolicyError(f"{service_name} must declare foregroundServiceType={service_type}")

    subtype = require_object(policy.get("subtypeProperty"), "subtypeProperty")
    expected_property_name = require_string(subtype.get("name"), "subtypeProperty.name")
    expected_property_value = require_string(subtype.get("value"), "subtypeProperty.value")
    for prop in service.findall("property"):
        if prop.attrib.get(f"{ANDROID_NS}name") == expected_property_name:
            if prop.attrib.get(f"{ANDROID_NS}value") != expected_property_value:
                raise RotationFgsPolicyError(f"{expected_property_name} has the wrong value")
            return
    raise RotationFgsPolicyError(f"{service_name} is missing property {expected_property_name}")


def validate_source_terms(repo_root: Path, policy: dict[str, Any]) -> None:
    service_source = require_string(policy.get("serviceSource"), "serviceSource")
    service_text = read_text(repo_root, service_source, "rotation trigger service source")
    for term in require_string_list(policy.get("requiredServiceTerms"), "requiredServiceTerms"):
        if term not in service_text:
            raise RotationFgsPolicyError(f"{service_source} is missing required term: {term}")

    settings_text = "\n".join(
        read_text(repo_root, source, f"settings source {source}")
        for source in require_string_list(policy.get("settingsSources"), "settingsSources")
    )
    for term in require_string_list(policy.get("requiredSettingsTerms"), "requiredSettingsTerms"):
        if term not in settings_text:
            raise RotationFgsPolicyError(f"settings sources are missing required term: {term}")


def validate_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "rotation FGS policy docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise RotationFgsPolicyError(f"{docs_path} is missing required section text: {term}")
    for term in (
        EXPECTED_STATUS,
        require_string(policy.get("serviceName"), "serviceName"),
        require_string(policy.get("foregroundServiceType"), "foregroundServiceType"),
        require_string(require_object(policy.get("subtypeProperty"), "subtypeProperty").get("value"), "subtypeProperty.value"),
    ):
        if term not in docs_text:
            raise RotationFgsPolicyError(f"{docs_path} is missing policy term: {term}")
    for url in REQUIRED_SOURCE_URLS:
        if url not in docs_text:
            raise RotationFgsPolicyError(f"{docs_path} is missing source URL: {url}")


def validate_play_packet(repo_root: Path, policy: dict[str, Any]) -> None:
    play_docs_path = require_string(policy.get("playPacketDocs"), "playPacketDocs")
    play_docs = read_text(repo_root, play_docs_path, "Play App content docs")
    for term in require_string_list(policy.get("requiredPlayTerms"), "requiredPlayTerms"):
        if term not in play_docs:
            raise RotationFgsPolicyError(f"{play_docs_path} is missing required FGS term: {term}")

    play_policy_path = require_string(policy.get("playPacketPolicy"), "playPacketPolicy")
    play_policy = require_object(read_json(repo_root / play_policy_path), "Play App content policy")
    declarations = require_object(play_policy.get("declarations"), "Play App content declarations")
    services = declarations.get("foregroundServices")
    if not isinstance(services, list) or not services:
        raise RotationFgsPolicyError("Play App content policy is missing foregroundServices")

    expected_type = require_string(policy.get("foregroundServiceType"), "foregroundServiceType")
    expected_service = require_string(policy.get("serviceName"), "serviceName")
    expected_subtype = require_string(
        require_object(policy.get("subtypeProperty"), "subtypeProperty").get("value"),
        "subtypeProperty.value",
    )
    matched = False
    for index, raw_row in enumerate(services):
        row = require_object(raw_row, f"foregroundServices[{index}]")
        if row.get("type") != expected_type:
            continue
        matched = True
        if row.get("service") != expected_service:
            raise RotationFgsPolicyError("foregroundServices specialUse row has the wrong service")
        if row.get("manifestSubtype") != expected_subtype:
            raise RotationFgsPolicyError("foregroundServices specialUse row has the wrong manifestSubtype")
        if row.get("demoVideoStatus") != EXPECTED_STATUS:
            raise RotationFgsPolicyError("foregroundServices specialUse row must require owner demo video evidence")
        for field in ("functionalityDescription", "deferredImpact", "interruptedImpact"):
            require_string(row.get(field), f"foregroundServices specialUse {field}")
        evidence_refs = require_string_list(row.get("evidenceRefs"), "foregroundServices specialUse evidenceRefs")
        for ref in evidence_refs:
            if not (repo_root / ref).is_file():
                raise RotationFgsPolicyError(f"foregroundServices evidence ref is missing: {ref}")
    if not matched:
        raise RotationFgsPolicyError("Play App content policy is missing specialUse foreground service row")

    owner_actions = play_policy.get("ownerActions")
    if not isinstance(owner_actions, list):
        raise RotationFgsPolicyError("Play App content policy ownerActions must be a list")
    if not any(
        require_object(action, "owner action").get("id") == "capture-foreground-service-declaration-evidence"
        for action in owner_actions
    ):
        raise RotationFgsPolicyError("Play App content policy is missing FGS declaration owner action")


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise RotationFgsPolicyError("sourceUrls missing required URLs: " + ", ".join(missing))
    for url in urls:
        if not url.startswith("https://"):
            raise RotationFgsPolicyError(f"sourceUrls entry must use HTTPS: {url}")
    return len(urls)


def validate_workflow_wiring(repo_root: Path) -> None:
    command = "tools/rotation_fgs_policy_check.py"
    for label, relative_path in (
        ("verify workflow", ".github/workflows/verify.yml"),
        ("release workflow", ".github/workflows/release.yml"),
        ("release dry-run docs", "docs/distribution/release-dry-run.md"),
        ("release signing docs", "docs/distribution/release-signing.md"),
        ("release metadata docs", "docs/distribution/release-metadata-consistency.md"),
    ):
        text = read_text(repo_root, relative_path, label)
        if command not in text:
            raise RotationFgsPolicyError(f"{label} missing rotation FGS policy command")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise RotationFgsPolicyError("schemaVersion must be 1")
    if policy.get("policyKind") != EXPECTED_POLICY_KIND:
        raise RotationFgsPolicyError(f"policyKind must be {EXPECTED_POLICY_KIND}")
    if policy.get("status") != EXPECTED_STATUS:
        raise RotationFgsPolicyError(f"status must be {EXPECTED_STATUS}")
    validate_manifest(repo_root, policy)
    validate_source_terms(repo_root, policy)
    validate_docs(repo_root, policy)
    validate_play_packet(repo_root, policy)
    source_url_count = validate_source_urls(policy)
    validate_workflow_wiring(repo_root)
    return {
        "status": "ok",
        "policyKind": EXPECTED_POLICY_KIND,
        "serviceName": require_string(policy.get("serviceName"), "serviceName"),
        "foregroundServiceType": require_string(policy.get("foregroundServiceType"), "foregroundServiceType"),
        "sourceUrlCount": source_url_count,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "rotation FGS policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
