#!/usr/bin/env python3
"""Validate curated release dependency license policy."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


DEFAULT_POLICY = "docs/legal/dependency-license-policy.json"
DEFAULT_OVERLAY = "docs/legal/dependency-notice-overrides.json"
DEFAULT_DEPENDENCY_LOCK = "docs/legal/dependency-notices.lock.json"
DEFAULT_NATIVE_LOCK = "docs/legal/native-compliance.lock.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Fail release checks on disallowed or unreviewed dependency license IDs."
    )
    parser.add_argument("--policy", default=DEFAULT_POLICY)
    parser.add_argument("--overlay", default=DEFAULT_OVERLAY)
    parser.add_argument("--dependency-lock", default=DEFAULT_DEPENDENCY_LOCK)
    parser.add_argument("--native-lock", default=DEFAULT_NATIVE_LOCK)
    return parser.parse_args()


def load_json(path: Path) -> object:
    if not path.is_file():
        raise FileNotFoundError(f"Required file not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_dict(value: object, label: str) -> dict[str, object]:
    if not isinstance(value, dict):
        raise ValueError(f"{label} root must be an object")
    return value


def require_string_list(policy: dict[str, object], field: str) -> list[str]:
    value = policy.get(field)
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        raise ValueError(f"policy {field} must be an array of strings")
    return value


def require_object_list(policy: dict[str, object], field: str) -> list[dict[str, object]]:
    value = policy.get(field)
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise ValueError(f"policy {field} must be an array of objects")
    return value


def dependency_coordinates(lock: dict[str, object]) -> set[str]:
    return {str(item["coordinate"]) for item in lock.get("dependencies", [])}


def native_coordinates(lock: dict[str, object]) -> set[str]:
    return {str(item["coordinate"]) for item in lock.get("coordinates", [])}


def payload_targets(lock: dict[str, object]) -> set[str]:
    targets: set[str] = set()
    for coordinate in lock.get("coordinates", []):
        for artifact in coordinate.get("artifacts", []):
            for entry in artifact.get("payloadEntries", []):
                entry_name = str(entry.get("entry", ""))
                facts = entry.get("facts", {})
                if not isinstance(facts, dict):
                    facts = {}
                if "yt-dlp version" in facts:
                    targets.add("yt-dlp")
                if "python payload" in facts:
                    targets.add("python-runtime")
                if "libqjs" in entry_name:
                    targets.add("quickjs")
                if "ffmpeg" in entry_name or "ffprobe" in entry_name:
                    targets.add("ffmpeg")
    return targets


def native_fact_values(lock: dict[str, object]) -> list[str]:
    values: list[str] = []
    for coordinate in lock.get("coordinates", []):
        for artifact in coordinate.get("artifacts", []):
            for entry in artifact.get("payloadEntries", []):
                facts = entry.get("facts", {})
                if isinstance(facts, dict):
                    values.extend(str(value) for value in facts.values())
    return values


def overlay_entries(overlay: dict[str, object]) -> list[dict[str, object]]:
    entries = overlay.get("entries")
    if not isinstance(entries, list) or not all(isinstance(item, dict) for item in entries):
        raise ValueError("overlay entries must be an array of objects")
    return entries


def classify_license(license_id: str, policy: dict[str, object]) -> str:
    allowed_ids = set(require_string_list(policy, "allowedLicenseIds"))
    review_ids = set(require_string_list(policy, "reviewRequiredLicenseIds"))
    review_patterns = require_string_list(policy, "reviewRequiredLicensePatterns")
    disallowed_patterns = require_string_list(policy, "disallowedLicensePatterns")
    lower_license = license_id.lower()

    for pattern in disallowed_patterns:
        if pattern.lower() in lower_license:
            return "disallowed"
    if license_id in allowed_ids:
        return "allowed"
    if license_id in review_ids:
        return "review-required"
    for pattern in review_patterns:
        if pattern.lower() in lower_license:
            return "review-required"
    return "unknown"


def validate_policy_shape(policy: dict[str, object]) -> list[str]:
    errors: list[str] = []
    if policy.get("schemaVersion") != 1:
        errors.append("policy schemaVersion must be 1")
    for field in (
        "allowedLicenseIds",
        "disallowedLicensePatterns",
        "requiredOverlayIds",
        "reviewRequiredLicenseIds",
        "reviewRequiredLicensePatterns",
    ):
        try:
            values = require_string_list(policy, field)
        except ValueError as exc:
            errors.append(str(exc))
            continue
        if len(values) != len(set(values)):
            errors.append(f"policy {field} contains duplicates")
    for field in ("requiredCoordinatePrefixes", "requiredPayloadTargets"):
        try:
            require_object_list(policy, field)
        except ValueError as exc:
            errors.append(str(exc))
    return errors


def validate_required_overlay_ids(
    *, policy: dict[str, object], entries_by_id: dict[str, dict[str, object]]
) -> list[str]:
    errors: list[str] = []
    required = set(require_string_list(policy, "requiredOverlayIds"))
    missing = sorted(required - set(entries_by_id))
    if missing:
        errors.append("missing required policy overlay entries: " + ", ".join(missing))
    return errors


def validate_coordinate_coverage(
    *,
    policy: dict[str, object],
    entries_by_id: dict[str, dict[str, object]],
    coordinates: set[str],
) -> list[str]:
    errors: list[str] = []
    for rule in require_object_list(policy, "requiredCoordinatePrefixes"):
        prefix = str(rule.get("prefix", ""))
        overlay_id = str(rule.get("overlayId", ""))
        if not prefix or not overlay_id:
            errors.append("requiredCoordinatePrefixes entries need prefix and overlayId")
            continue
        if any(coordinate.startswith(prefix) for coordinate in coordinates) and overlay_id not in entries_by_id:
            errors.append(f"{prefix}: current coordinates require overlay entry {overlay_id}")
    return errors


def validate_payload_coverage(
    *,
    policy: dict[str, object],
    entries_by_id: dict[str, dict[str, object]],
    payloads: set[str],
) -> list[str]:
    errors: list[str] = []
    for rule in require_object_list(policy, "requiredPayloadTargets"):
        target = str(rule.get("target", ""))
        overlay_id = str(rule.get("overlayId", ""))
        if not target or not overlay_id:
            errors.append("requiredPayloadTargets entries need target and overlayId")
            continue
        if target in payloads and overlay_id not in entries_by_id:
            errors.append(f"{target}: current payloads require overlay entry {overlay_id}")
    return errors


def validate_native_facts(*, policy: dict[str, object], native_lock: dict[str, object]) -> list[str]:
    errors: list[str] = []
    disallowed_patterns = require_string_list(policy, "disallowedLicensePatterns")
    for value in native_fact_values(native_lock):
        lower_value = value.lower()
        for pattern in disallowed_patterns:
            if pattern.lower() in lower_value:
                errors.append(f"native payload fact matches disallowed policy pattern {pattern!r}: {value}")
    return errors


def validate_license_policy(
    *,
    policy: dict[str, object],
    overlay: dict[str, object],
    dependency_lock: dict[str, object],
    native_lock: dict[str, object],
) -> tuple[list[str], dict[str, int]]:
    errors = validate_policy_shape(policy)
    if errors:
        return errors, {}

    entries = overlay_entries(overlay)
    entries_by_id = {str(entry.get("id", "")): entry for entry in entries}
    coordinates = dependency_coordinates(dependency_lock) | native_coordinates(native_lock)
    payloads = payload_targets(native_lock)
    counts = {"allowed": 0, "disallowed": 0, "review-required": 0, "unknown": 0}

    errors.extend(validate_required_overlay_ids(policy=policy, entries_by_id=entries_by_id))
    errors.extend(
        validate_coordinate_coverage(
            policy=policy,
            entries_by_id=entries_by_id,
            coordinates=coordinates,
        )
    )
    errors.extend(
        validate_payload_coverage(
            policy=policy,
            entries_by_id=entries_by_id,
            payloads=payloads,
        )
    )
    errors.extend(validate_native_facts(policy=policy, native_lock=native_lock))

    for entry in entries:
        entry_id = str(entry.get("id", "<missing id>"))
        license_id = str(entry.get("licenseId", "")).strip()
        category = classify_license(license_id, policy)
        counts[category] += 1
        if category == "unknown":
            errors.append(f"{entry_id}: licenseId is not covered by policy: {license_id}")
        elif category == "disallowed":
            errors.append(f"{entry_id}: licenseId is disallowed by policy: {license_id}")
        elif category == "review-required" and not str(entry.get("reviewNote", "")).strip():
            errors.append(f"{entry_id}: review-required license needs a reviewNote")

    return errors, counts


def main() -> int:
    args = parse_args()
    policy = require_dict(load_json(Path(args.policy)), "policy")
    overlay = require_dict(load_json(Path(args.overlay)), "overlay")
    dependency_lock = require_dict(load_json(Path(args.dependency_lock)), "dependency lock")
    native_lock = require_dict(load_json(Path(args.native_lock)), "native lock")

    errors, counts = validate_license_policy(
        policy=policy,
        overlay=overlay,
        dependency_lock=dependency_lock,
        native_lock=native_lock,
    )
    if errors:
        print("Dependency license policy validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(
        json.dumps(
            {
                "counts": counts,
                "overlay": args.overlay,
                "policy": args.policy,
                "status": "ok",
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
