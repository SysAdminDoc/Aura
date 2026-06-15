#!/usr/bin/env python3
"""Validate Aura's on-device wallpaper generation decision gate."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


REQUIRED_CRITERIA = {
    "device-baseline",
    "model-size-storage",
    "latency-battery-thermal",
    "license-redistribution",
    "moderation-reporting",
    "fallback-user-choice",
    "foss-channel-impact",
}
SUPPORTED_DECISIONS = {"hold", "prototypeOnly", "approved"}
SUPPORTED_CRITERION_STATUSES = {"met", "needsEvidence", "notMet"}
PRODUCTION_SCAN_PATHS = (
    "app/src/main",
    "app/build.gradle.kts",
    "gradle/libs.versions.toml",
    "settings.gradle.kts",
)
PRODUCTION_SCAN_EXCLUDES = (
    "app/src/main/generated/baselineProfiles/",
)
TEXT_SUFFIXES = {
    ".gradle",
    ".json",
    ".kts",
    ".kt",
    ".properties",
    ".pro",
    ".toml",
    ".txt",
    ".xml",
}
MODEL_ARTIFACT_SUFFIXES = {".tflite", ".onnx", ".safetensors", ".gguf", ".bin", ".pte"}


class OnDeviceAiDecisionError(ValueError):
    """Raised when the on-device AI wallpaper decision packet is stale."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura's on-device wallpaper decision gate.")
    parser.add_argument("--policy", default="docs/ai/on-device-wallpaper-decision.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise OnDeviceAiDecisionError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise OnDeviceAiDecisionError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise OnDeviceAiDecisionError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise OnDeviceAiDecisionError(f"{label} contains duplicate values")
    return values


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise OnDeviceAiDecisionError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def relative_path(repo_root: Path, path: Path) -> str:
    try:
        return path.resolve().relative_to(repo_root.resolve()).as_posix()
    except ValueError:
        return str(path)


def validate_decision_doc(repo_root: Path, policy: dict[str, Any], decision: str) -> str:
    decision_doc = require_string(policy.get("decisionDoc"), "decisionDoc")
    doc_path = repo_root / decision_doc
    if not doc_path.is_file():
        raise OnDeviceAiDecisionError(f"decisionDoc is missing: {decision_doc}")
    doc_text = doc_path.read_text(encoding="utf-8").lower()
    if decision == "hold" and "status: hold" not in doc_text:
        raise OnDeviceAiDecisionError("decisionDoc must document Status: hold")
    if decision == "prototypeOnly" and "prototype" not in doc_text:
        raise OnDeviceAiDecisionError("decisionDoc must document prototype-only status")
    if decision == "approved" and "approved" not in doc_text:
        raise OnDeviceAiDecisionError("decisionDoc must document approved status")
    return decision_doc


def validate_criteria(policy: dict[str, Any], decision: str) -> list[dict[str, Any]]:
    criteria_raw = policy.get("criteria")
    if not isinstance(criteria_raw, list) or not criteria_raw:
        raise OnDeviceAiDecisionError("criteria must be a non-empty list")

    criteria: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for index, raw_criterion in enumerate(criteria_raw):
        criterion = require_object(raw_criterion, f"criteria[{index}]")
        criterion_id = require_string(criterion.get("id"), f"criteria[{index}].id")
        if criterion_id in seen_ids:
            raise OnDeviceAiDecisionError(f"duplicate criterion id: {criterion_id}")
        seen_ids.add(criterion_id)

        status = require_string(criterion.get("status"), f"{criterion_id}.status")
        if status not in SUPPORTED_CRITERION_STATUSES:
            raise OnDeviceAiDecisionError(f"{criterion_id}.status is unsupported")

        if status == "met":
            require_string_list(criterion.get("evidenceRefs"), f"{criterion_id}.evidenceRefs")
        else:
            require_string_list(criterion.get("requiredEvidence"), f"{criterion_id}.requiredEvidence")
        criteria.append(criterion)

    if seen_ids != REQUIRED_CRITERIA:
        missing = sorted(REQUIRED_CRITERIA - seen_ids)
        extra = sorted(seen_ids - REQUIRED_CRITERIA)
        details = []
        if missing:
            details.append(f"missing: {', '.join(missing)}")
        if extra:
            details.append(f"extra: {', '.join(extra)}")
        raise OnDeviceAiDecisionError(f"criteria set is stale ({'; '.join(details)})")

    if decision == "approved":
        unmet = sorted(require_string(criterion["id"], "criterion.id") for criterion in criteria if criterion["status"] != "met")
        if unmet:
            raise OnDeviceAiDecisionError(f"approved decision requires met criteria: {', '.join(unmet)}")

    return criteria


def iter_production_files(repo_root: Path) -> list[Path]:
    files: list[Path] = []
    for relative in PRODUCTION_SCAN_PATHS:
        path = repo_root / relative
        if not path.exists():
            continue
        if path.is_file():
            if not is_excluded_production_file(repo_root, path):
                files.append(path)
            continue
        files.extend(
            child for child in path.rglob("*")
            if child.is_file() and not is_excluded_production_file(repo_root, child)
        )
    return sorted(files)


def is_excluded_production_file(repo_root: Path, path: Path) -> bool:
    relative = relative_path(repo_root, path)
    return any(relative.startswith(prefix) for prefix in PRODUCTION_SCAN_EXCLUDES)


def validate_no_early_implementation(
    repo_root: Path,
    forbidden_signals: list[str],
    decision: str,
) -> dict[str, Any]:
    files = iter_production_files(repo_root)
    artifact_matches = [
        relative_path(repo_root, path)
        for path in files
        if path.suffix.lower() in MODEL_ARTIFACT_SUFFIXES
    ]
    if artifact_matches and decision != "approved":
        raise OnDeviceAiDecisionError(
            "on-device model artifacts are not allowed before approval: "
            + ", ".join(artifact_matches)
        )

    lowered_signals = [signal.lower() for signal in forbidden_signals]
    signal_matches: list[str] = []
    for path in files:
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        try:
            text = path.read_text(encoding="utf-8").lower()
        except UnicodeDecodeError:
            continue
        for signal in lowered_signals:
            if signal in text:
                signal_matches.append(f"{relative_path(repo_root, path)}:{signal}")
    if signal_matches and decision != "approved":
        raise OnDeviceAiDecisionError(
            "on-device generation implementation signals are not allowed before approval: "
            + ", ".join(sorted(signal_matches))
        )

    return {
        "status": "ok",
        "filesScanned": len(files),
        "textSignals": len(lowered_signals),
        "modelArtifactSuffixes": sorted(MODEL_ARTIFACT_SUFFIXES),
    }


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise OnDeviceAiDecisionError("on-device AI decision schemaVersion must be 1")
    if policy.get("policyKind") != "onDeviceAiWallpaperDecision":
        raise OnDeviceAiDecisionError("on-device AI decision policyKind is invalid")

    decision = require_string(policy.get("decision"), "decision")
    if decision not in SUPPORTED_DECISIONS:
        raise OnDeviceAiDecisionError("decision must be hold, prototypeOnly, or approved")
    require_string(policy.get("summary"), "summary")

    hosted_fallback = require_string(policy.get("hostedFallback"), "hostedFallback").lower()
    if "stability" not in hosted_fallback or ("byo" not in hosted_fallback and "key" not in hosted_fallback):
        raise OnDeviceAiDecisionError("hostedFallback must preserve Stability BYO-key fallback")

    decision_doc = validate_decision_doc(repo_root, policy, decision)
    criteria = validate_criteria(policy, decision)
    forbidden_signals = require_string_list(policy.get("forbiddenImplementationSignals"), "forbiddenImplementationSignals")
    source_urls = require_string_list(policy.get("sourceUrls"), "sourceUrls")
    for url in source_urls:
        if not url.startswith("https://"):
            raise OnDeviceAiDecisionError(f"sourceUrls must use HTTPS: {url}")

    scan_result = validate_no_early_implementation(repo_root, forbidden_signals, decision)
    return {
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "status": "ok",
        "decision": decision,
        "decisionDoc": decision_doc,
        "criteriaCount": len(criteria),
        "sourceUrlCount": len(source_urls),
        "scan": scan_result,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json((repo_root / args.policy).resolve()), "on-device AI decision policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
