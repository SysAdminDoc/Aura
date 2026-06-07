#!/usr/bin/env python3
"""Validate Aura background-work network and Data Saver posture."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


EXPECTED_POLICY_KIND = "backgroundWorkNetworkPosture"
EXPECTED_STATUS = "networkPostureCheckedSettingsPending"
REQUIRED_DOC_TERMS = {
    "Background work network posture",
    "Current decision",
    "Network posture matrix",
    "Data Saver handling",
    "Release gate",
    "Sources",
}
REQUIRED_SOURCE_URLS = {
    "https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work",
    "https://developer.android.com/develop/connectivity/network-ops/data-saver",
    "https://developer.android.com/reference/androidx/work/NetworkType",
    "https://developer.android.com/reference/android/net/ConnectivityManager",
}
VALID_POSTURES = {"connected", "unmetered", "userSelectableConnectedOrUnmetered"}
VALID_RELEASE_RISKS = {"Low", "Medium", "High"}


class BackgroundWorkNetworkError(ValueError):
    """Raised when the background-work network posture drifts."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura background-work network posture.")
    parser.add_argument("--policy", default="docs/background-work-network-posture.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise BackgroundWorkNetworkError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise BackgroundWorkNetworkError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise BackgroundWorkNetworkError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise BackgroundWorkNetworkError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise BackgroundWorkNetworkError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise BackgroundWorkNetworkError(f"{label} contains duplicate values")
    return values


def require_object_list(value: Any, label: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or not value:
        raise BackgroundWorkNetworkError(f"{label} must be a non-empty list")
    return [require_object(item, f"{label}[{index}]") for index, item in enumerate(value)]


def scheduling_rows(repo_root: Path, policy: dict[str, Any]) -> dict[str, str]:
    ledger_path = require_string(policy.get("schedulingLedgerPath"), "schedulingLedgerPath")
    ledger = require_object(read_json(repo_root / ledger_path), "background work scheduling ledger")
    rows: dict[str, str] = {}
    for index, raw_row in enumerate(require_object_list(ledger.get("workItems"), "schedulingLedger.workItems")):
        row_id = require_string(raw_row.get("id"), f"schedulingLedger.workItems[{index}].id")
        unique_name = require_string(
            raw_row.get("uniqueWorkName"),
            f"schedulingLedger.workItems[{index}].uniqueWorkName",
        )
        rows[row_id] = unique_name
    return rows


def validate_posture_row(repo_root: Path, row: dict[str, Any], index: int, scheduled: dict[str, str]) -> dict[str, str]:
    prefix = f"postureRows[{index}]"
    row_id = require_string(row.get("id"), f"{prefix}.id")
    unique_name = require_string(row.get("uniqueWorkName"), f"{prefix}.uniqueWorkName")
    if scheduled.get(row_id) != unique_name:
        raise BackgroundWorkNetworkError(f"{prefix} does not match scheduling ledger unique work name")
    posture = require_string(row.get("networkPosture"), f"{prefix}.networkPosture")
    if posture not in VALID_POSTURES:
        raise BackgroundWorkNetworkError(f"{prefix}.networkPosture must be one of {sorted(VALID_POSTURES)}")
    source_path = require_string(row.get("sourcePath"), f"{prefix}.sourcePath")
    settings_path = require_string(row.get("settingsSourcePath"), f"{prefix}.settingsSourcePath")
    release_risk = require_string(row.get("releaseRisk"), f"{prefix}.releaseRisk")
    if release_risk not in VALID_RELEASE_RISKS:
        raise BackgroundWorkNetworkError(f"{prefix}.releaseRisk must be one of {sorted(VALID_RELEASE_RISKS)}")
    for field in ("meteredPolicy", "dataSaverPolicy", "privacySurface"):
        value = require_string(row.get(field), f"{prefix}.{field}")
        if len(value) < 20:
            raise BackgroundWorkNetworkError(f"{prefix}.{field} must be descriptive")

    source_text = read_text(repo_root, source_path, f"{row_id} source")
    for term in require_string_list(row.get("requiredSourceTerms"), f"{prefix}.requiredSourceTerms"):
        if term not in source_text:
            raise BackgroundWorkNetworkError(f"{source_path} is missing required network term for {row_id}: {term}")

    settings_text = read_text(repo_root, settings_path, f"{row_id} settings source")
    for term in require_string_list(row.get("requiredSettingsTerms"), f"{prefix}.requiredSettingsTerms"):
        if term not in settings_text:
            raise BackgroundWorkNetworkError(f"{settings_path} is missing required settings term for {row_id}: {term}")

    return {"id": row_id, "uniqueWorkName": unique_name, "networkPosture": posture}


def validate_docs(repo_root: Path, policy: dict[str, Any], rows: list[dict[str, str]]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "background work network docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise BackgroundWorkNetworkError(f"{docs_path} is missing required section text: {term}")
    for term in (EXPECTED_STATUS, "tools/background_work_network_check.py", "ConnectivityManager"):
        if term not in docs_text:
            raise BackgroundWorkNetworkError(f"{docs_path} is missing network posture term: {term}")
    for row in rows:
        if row["uniqueWorkName"] not in docs_text:
            raise BackgroundWorkNetworkError(f"{docs_path} is missing unique work name: {row['uniqueWorkName']}")
    for url in REQUIRED_SOURCE_URLS:
        if url not in docs_text:
            raise BackgroundWorkNetworkError(f"{docs_path} is missing source URL: {url}")


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise BackgroundWorkNetworkError("sourceUrls missing required URLs: " + ", ".join(missing))
    for url in urls:
        if not url.startswith("https://"):
            raise BackgroundWorkNetworkError(f"sourceUrls entry must use HTTPS: {url}")
    return len(urls)


def validate_workflow_wiring(repo_root: Path) -> None:
    command = "tools/background_work_network_check.py"
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
            raise BackgroundWorkNetworkError(f"{label} missing background work network command")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise BackgroundWorkNetworkError("schemaVersion must be 1")
    if policy.get("policyKind") != EXPECTED_POLICY_KIND:
        raise BackgroundWorkNetworkError(f"policyKind must be {EXPECTED_POLICY_KIND}")
    if policy.get("status") != EXPECTED_STATUS:
        raise BackgroundWorkNetworkError(f"status must be {EXPECTED_STATUS}")

    scheduled = scheduling_rows(repo_root, policy)
    rows = [
        validate_posture_row(repo_root, row, index, scheduled)
        for index, row in enumerate(require_object_list(policy.get("postureRows"), "postureRows"))
    ]
    ids = [row["id"] for row in rows]
    unique_names = [row["uniqueWorkName"] for row in rows]
    if len(ids) != len(set(ids)):
        raise BackgroundWorkNetworkError("postureRows contain duplicate ids")
    if len(unique_names) != len(set(unique_names)):
        raise BackgroundWorkNetworkError("postureRows contain duplicate uniqueWorkName values")
    missing_ids = sorted(set(scheduled) - set(ids))
    if missing_ids:
        raise BackgroundWorkNetworkError("postureRows missing scheduling ledger ids: " + ", ".join(missing_ids))
    extra_ids = sorted(set(ids) - set(scheduled))
    if extra_ids:
        raise BackgroundWorkNetworkError("postureRows include ids absent from scheduling ledger: " + ", ".join(extra_ids))

    validate_docs(repo_root, policy, rows)
    source_url_count = validate_source_urls(policy)
    validate_workflow_wiring(repo_root)
    return {
        "status": "ok",
        "policyKind": EXPECTED_POLICY_KIND,
        "postureRowCount": len(rows),
        "sourceUrlCount": source_url_count,
        "networkPostures": sorted({row["networkPosture"] for row in rows}),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "background work network policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
