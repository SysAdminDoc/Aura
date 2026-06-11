#!/usr/bin/env python3
"""Validate Aura background-work device evidence capture planning."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


EXPECTED_POLICY_KIND = "backgroundWorkDeviceEvidence"
EXPECTED_STATUS = "deviceEvidencePending"
REQUIRED_SCENARIO_IDS = {
    "workmanager-baseline",
    "metered-data-saver",
    "low-battery-constraint",
    "doze-standby",
    "rotation-trigger-coalescing",
}
REQUIRED_SOURCE_URLS = {
    "https://developer.android.com/tools/adb",
    "https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work",
    "https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/states",
    "https://developer.android.com/training/monitoring-device-state/doze-standby",
    "https://developer.android.com/develop/connectivity/network-ops/data-saver",
}


class BackgroundWorkDeviceEvidenceError(ValueError):
    """Raised when the background-work device evidence plan drifts."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura background-work device evidence planning.")
    parser.add_argument("--policy", default="docs/background-work-device-evidence.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise BackgroundWorkDeviceEvidenceError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise BackgroundWorkDeviceEvidenceError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise BackgroundWorkDeviceEvidenceError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise BackgroundWorkDeviceEvidenceError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise BackgroundWorkDeviceEvidenceError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise BackgroundWorkDeviceEvidenceError(f"{label} contains duplicate values")
    return values


def require_object_list(value: Any, label: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or not value:
        raise BackgroundWorkDeviceEvidenceError(f"{label} must be a non-empty list")
    return [require_object(item, f"{label}[{index}]") for index, item in enumerate(value)]


def scheduling_unique_names(repo_root: Path, policy: dict[str, Any]) -> set[str]:
    ledger_path = require_string(policy.get("schedulingLedgerPath"), "schedulingLedgerPath")
    ledger = require_object(read_json(repo_root / ledger_path), "background work scheduling ledger")
    return {
        require_string(row.get("uniqueWorkName"), f"schedulingLedger.workItems[{index}].uniqueWorkName")
        for index, row in enumerate(require_object_list(ledger.get("workItems"), "schedulingLedger.workItems"))
    }


def validate_package_name(repo_root: Path, policy: dict[str, Any]) -> str:
    package_name = require_string(policy.get("packageName"), "packageName")
    gradle_path = require_string(policy.get("appGradlePath"), "appGradlePath")
    gradle_text = read_text(repo_root, gradle_path, "app Gradle file")
    if f'applicationId = "{package_name}"' not in gradle_text:
        raise BackgroundWorkDeviceEvidenceError(f"{gradle_path} does not declare applicationId {package_name}")
    return package_name


def validate_artifact_path(artifact_directory: str, artifact_path: str, label: str) -> None:
    normalized = artifact_path.replace("\\", "/")
    if normalized.startswith("/") or normalized.startswith("../") or "/../" in normalized:
        raise BackgroundWorkDeviceEvidenceError(f"{label} must be a relative artifact path")
    prefix = artifact_directory.rstrip("/") + "/"
    if not normalized.startswith(prefix):
        raise BackgroundWorkDeviceEvidenceError(f"{label} must live under {artifact_directory}")
    if not (normalized.endswith(".txt") or normalized.endswith(".json") or normalized.endswith(".md")):
        raise BackgroundWorkDeviceEvidenceError(f"{label} must be a text, JSON, or markdown artifact")


def validate_scenario(
    row: dict[str, Any],
    index: int,
    artifact_directory: str,
    known_work_names: set[str],
) -> dict[str, object]:
    prefix = f"scenarios[{index}]"
    row_id = require_string(row.get("id"), f"{prefix}.id")
    title = require_string(row.get("title"), f"{prefix}.title")
    work_names = set(require_string_list(row.get("coversWorkNames"), f"{prefix}.coversWorkNames"))
    unknown = sorted(work_names - known_work_names)
    if unknown:
        raise BackgroundWorkDeviceEvidenceError(f"{prefix}.coversWorkNames contains unknown work names: {', '.join(unknown)}")

    commands = require_string_list(row.get("requiredCommands"), f"{prefix}.requiredCommands")
    if not any(command.startswith("adb ") or command.startswith("adb.exe ") for command in commands):
        raise BackgroundWorkDeviceEvidenceError(f"{prefix}.requiredCommands must include adb commands")
    if not any("dumpsys" in command or "cmd jobscheduler" in command for command in commands):
        raise BackgroundWorkDeviceEvidenceError(f"{prefix}.requiredCommands must capture scheduler or device state")

    artifacts = require_string_list(row.get("requiredArtifacts"), f"{prefix}.requiredArtifacts")
    for artifact_index, artifact in enumerate(artifacts):
        validate_artifact_path(artifact_directory, artifact, f"{prefix}.requiredArtifacts[{artifact_index}]")

    evidence = require_string_list(row.get("expectedEvidence"), f"{prefix}.expectedEvidence")
    if len(evidence) < 2:
        raise BackgroundWorkDeviceEvidenceError(f"{prefix}.expectedEvidence must describe at least two checks")
    return {
        "id": row_id,
        "title": title,
        "coversWorkNames": sorted(work_names),
        "requiredCommands": commands,
        "requiredArtifacts": artifacts,
    }


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise BackgroundWorkDeviceEvidenceError("sourceUrls missing required URLs: " + ", ".join(missing))
    for url in urls:
        if not url.startswith("https://"):
            raise BackgroundWorkDeviceEvidenceError(f"sourceUrls entry must use HTTPS: {url}")
    return len(urls)


def validate_workflow_wiring(repo_root: Path) -> None:
    command = "tools/background_work_device_evidence_check.py"
    for label, relative_path in (
        ("verify workflow", ".github/workflows/verify.yml"),
        ("release workflow", ".github/workflows/release.yml"),
    ):
        text = read_text(repo_root, relative_path, label)
        if command not in text:
            raise BackgroundWorkDeviceEvidenceError(f"{label} missing background work device evidence command")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise BackgroundWorkDeviceEvidenceError("schemaVersion must be 1")
    if policy.get("policyKind") != EXPECTED_POLICY_KIND:
        raise BackgroundWorkDeviceEvidenceError(f"policyKind must be {EXPECTED_POLICY_KIND}")
    if policy.get("status") != EXPECTED_STATUS:
        raise BackgroundWorkDeviceEvidenceError(f"status must be {EXPECTED_STATUS}")

    validate_package_name(repo_root, policy)
    artifact_directory = require_string(policy.get("artifactDirectory"), "artifactDirectory").replace("\\", "/")
    known_work_names = scheduling_unique_names(repo_root, policy)
    scenarios = [
        validate_scenario(row, index, artifact_directory, known_work_names)
        for index, row in enumerate(require_object_list(policy.get("scenarios"), "scenarios"))
    ]
    scenario_ids = [str(row["id"]) for row in scenarios]
    if len(scenario_ids) != len(set(scenario_ids)):
        raise BackgroundWorkDeviceEvidenceError("scenarios contain duplicate ids")
    missing_ids = sorted(REQUIRED_SCENARIO_IDS - set(scenario_ids))
    if missing_ids:
        raise BackgroundWorkDeviceEvidenceError("scenarios missing required ids: " + ", ".join(missing_ids))

    covered_work_names = {name for row in scenarios for name in row["coversWorkNames"]}  # type: ignore[union-attr]
    missing_work_names = sorted(known_work_names - covered_work_names)
    if missing_work_names:
        raise BackgroundWorkDeviceEvidenceError("scenarios do not cover work names: " + ", ".join(missing_work_names))

    source_url_count = validate_source_urls(policy)
    validate_workflow_wiring(repo_root)
    return {
        "status": "ok",
        "policyKind": EXPECTED_POLICY_KIND,
        "scenarioCount": len(scenarios),
        "coveredWorkNames": sorted(covered_work_names),
        "sourceUrlCount": source_url_count,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "background work device evidence policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
