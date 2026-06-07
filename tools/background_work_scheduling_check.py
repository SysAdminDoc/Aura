#!/usr/bin/env python3
"""Validate Aura background-work scheduling ledger posture."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


EXPECTED_POLICY_KIND = "backgroundWorkSchedulingLedger"
EXPECTED_STATUS = "ledgerReadySettingsPending"
REQUIRED_WORK_IDS = {
    "auto-wallpaper-periodic",
    "daily-wallpaper-periodic",
    "weather-update-periodic",
    "aura-originals-one-shot",
    "rotation-trigger-one-shot",
}
REQUIRED_DOC_TERMS = {
    "Background work scheduling ledger",
    "Current decision",
    "Scheduling matrix",
    "Deferral reasons",
    "Settings and support gaps",
    "Release gate",
    "Sources",
}
REQUIRED_STATUS_FIELDS = {
    "uniqueWorkName",
    "enabledState",
    "lastSuccessUtc",
    "lastFailureUtc",
    "lastErrorClass",
    "currentWorkInfoState",
    "constraints",
    "deferralReasons",
}
REQUIRED_SOURCE_URLS = {
    "https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work",
    "https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/manage-work",
    "https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/states",
    "https://developer.android.com/training/monitoring-device-state/doze-standby",
}
VALID_WORK_KINDS = {"periodic", "oneTime"}


class BackgroundWorkSchedulingError(ValueError):
    """Raised when the background-work scheduling ledger drifts."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura background-work scheduling ledger.")
    parser.add_argument("--policy", default="docs/background-work-scheduling-ledger.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise BackgroundWorkSchedulingError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise BackgroundWorkSchedulingError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise BackgroundWorkSchedulingError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise BackgroundWorkSchedulingError(f"{label} must be a non-empty string")
    return value.strip()


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise BackgroundWorkSchedulingError(f"{label} must be a boolean")
    return value


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise BackgroundWorkSchedulingError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise BackgroundWorkSchedulingError(f"{label} contains duplicate values")
    return values


def require_object_list(value: Any, label: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or not value:
        raise BackgroundWorkSchedulingError(f"{label} must be a non-empty list")
    return [require_object(item, f"{label}[{index}]") for index, item in enumerate(value)]


def validate_work_item(repo_root: Path, row: dict[str, Any], index: int) -> dict[str, str]:
    prefix = f"workItems[{index}]"
    row_id = require_string(row.get("id"), f"{prefix}.id")
    unique_name = require_string(row.get("uniqueWorkName"), f"{prefix}.uniqueWorkName")
    worker_class = require_string(row.get("workerClass"), f"{prefix}.workerClass")
    source_path = require_string(row.get("sourcePath"), f"{prefix}.sourcePath")
    work_kind = require_string(row.get("workKind"), f"{prefix}.workKind")
    if work_kind not in VALID_WORK_KINDS:
        raise BackgroundWorkSchedulingError(f"{prefix}.workKind must be one of {sorted(VALID_WORK_KINDS)}")
    enqueue_api = require_string(row.get("enqueueApi"), f"{prefix}.enqueueApi")
    existing_policy = require_string(row.get("existingWorkPolicy"), f"{prefix}.existingWorkPolicy")
    require_string(row.get("interval"), f"{prefix}.interval")
    require_string(row.get("initialDelay"), f"{prefix}.initialDelay")
    require_string(row.get("backoff"), f"{prefix}.backoff")
    require_string(row.get("enabledSurface"), f"{prefix}.enabledSurface")
    require_string(row.get("scheduleTrigger"), f"{prefix}.scheduleTrigger")
    require_string(row.get("cancelTrigger"), f"{prefix}.cancelTrigger")
    require_bool(row.get("expedited"), f"{prefix}.expedited")
    constraints = require_string_list(row.get("constraints"), f"{prefix}.constraints")
    deferrals = require_string_list(row.get("deferralReasons"), f"{prefix}.deferralReasons")
    source_terms = require_string_list(row.get("sourceTerms"), f"{prefix}.sourceTerms")
    settings_sources = require_string_list(row.get("settingsSources"), f"{prefix}.settingsSources")

    if len(constraints) < 1:
        raise BackgroundWorkSchedulingError(f"{prefix}.constraints must not be empty")
    if len(deferrals) < 2:
        raise BackgroundWorkSchedulingError(f"{prefix}.deferralReasons must list actionable delays")
    if enqueue_api not in {"enqueueUniqueWork", "enqueueUniquePeriodicWork"}:
        raise BackgroundWorkSchedulingError(f"{prefix}.enqueueApi must be a unique work API")
    if "Existing" not in existing_policy:
        raise BackgroundWorkSchedulingError(f"{prefix}.existingWorkPolicy must name a WorkManager policy")

    source_text = read_text(repo_root, source_path, f"{row_id} source")
    for term in (unique_name, worker_class, enqueue_api, existing_policy, *source_terms):
        if term not in source_text:
            raise BackgroundWorkSchedulingError(f"{source_path} is missing required term for {row_id}: {term}")
    for settings_source in settings_sources:
        settings_text = read_text(repo_root, settings_source, f"{row_id} settings source")
        if row_id == "rotation-trigger-one-shot":
            if "RotationTriggerService" not in settings_text and "TaskerActionReceiver" not in settings_text:
                raise BackgroundWorkSchedulingError(f"{settings_source} missing rotation trigger reference")
        elif worker_class not in settings_text and unique_name not in settings_text and "enqueueAuraOriginalsDownload" not in settings_text:
            raise BackgroundWorkSchedulingError(f"{settings_source} missing scheduling reference for {row_id}")

    return {"id": row_id, "uniqueWorkName": unique_name}


def validate_docs(repo_root: Path, policy: dict[str, Any], rows: list[dict[str, str]]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "background work scheduling docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise BackgroundWorkSchedulingError(f"{docs_path} is missing required section text: {term}")
    for term in (EXPECTED_STATUS, "tools/background_work_scheduling_check.py"):
        if term not in docs_text:
            raise BackgroundWorkSchedulingError(f"{docs_path} is missing policy term: {term}")
    for row in rows:
        if row["uniqueWorkName"] not in docs_text:
            raise BackgroundWorkSchedulingError(f"{docs_path} is missing unique work name: {row['uniqueWorkName']}")
    for url in REQUIRED_SOURCE_URLS:
        if url not in docs_text:
            raise BackgroundWorkSchedulingError(f"{docs_path} is missing source URL: {url}")


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise BackgroundWorkSchedulingError("sourceUrls missing required URLs: " + ", ".join(missing))
    for url in urls:
        if not url.startswith("https://"):
            raise BackgroundWorkSchedulingError(f"sourceUrls entry must use HTTPS: {url}")
    return len(urls)


def validate_workflow_wiring(repo_root: Path) -> None:
    command = "tools/background_work_scheduling_check.py"
    for label, relative_path in (
        ("verify workflow", ".github/workflows/verify.yml"),
        ("release workflow", ".github/workflows/release.yml"),
        ("release dry-run docs", "docs/distribution/release-dry-run.md"),
        ("release signing docs", "docs/distribution/release-signing.md"),
        ("release metadata docs", "docs/distribution/release-metadata-consistency.md"),
        ("supply-chain docs", "docs/distribution/supply-chain.md"),
    ):
        text = read_text(repo_root, relative_path, label)
        if command not in text:
            raise BackgroundWorkSchedulingError(f"{label} missing background work scheduling command")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise BackgroundWorkSchedulingError("schemaVersion must be 1")
    if policy.get("policyKind") != EXPECTED_POLICY_KIND:
        raise BackgroundWorkSchedulingError(f"policyKind must be {EXPECTED_POLICY_KIND}")
    if policy.get("status") != EXPECTED_STATUS:
        raise BackgroundWorkSchedulingError(f"status must be {EXPECTED_STATUS}")

    status_fields = set(require_string_list(policy.get("requiredSettingsStatusFields"), "requiredSettingsStatusFields"))
    missing_fields = sorted(REQUIRED_STATUS_FIELDS - status_fields)
    if missing_fields:
        raise BackgroundWorkSchedulingError("requiredSettingsStatusFields missing: " + ", ".join(missing_fields))

    rows = [
        validate_work_item(repo_root, row, index)
        for index, row in enumerate(require_object_list(policy.get("workItems"), "workItems"))
    ]
    ids = [row["id"] for row in rows]
    unique_names = [row["uniqueWorkName"] for row in rows]
    if len(ids) != len(set(ids)):
        raise BackgroundWorkSchedulingError("workItems contain duplicate ids")
    if len(unique_names) != len(set(unique_names)):
        raise BackgroundWorkSchedulingError("workItems contain duplicate uniqueWorkName values")
    missing_ids = sorted(REQUIRED_WORK_IDS - set(ids))
    if missing_ids:
        raise BackgroundWorkSchedulingError("workItems missing required ids: " + ", ".join(missing_ids))

    validate_docs(repo_root, policy, rows)
    source_url_count = validate_source_urls(policy)
    validate_workflow_wiring(repo_root)
    return {
        "status": "ok",
        "policyKind": EXPECTED_POLICY_KIND,
        "workItemCount": len(rows),
        "sourceUrlCount": source_url_count,
        "uniqueWorkNames": sorted(unique_names),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "background work scheduling policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
