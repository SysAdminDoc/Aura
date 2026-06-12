#!/usr/bin/env python3
"""Validate Aura's yt-dlp netrc-cmd CVE reachability policy."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


class YtDlpCvePolicyError(ValueError):
    """Raised when the yt-dlp CVE policy is missing or stale."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura yt-dlp CVE reachability policy.")
    parser.add_argument("--policy", default="docs/security/ytdlp-cve-policy.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise YtDlpCvePolicyError(f"JSON file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise YtDlpCvePolicyError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise YtDlpCvePolicyError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise YtDlpCvePolicyError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise YtDlpCvePolicyError(f"{label} contains duplicate values")
    return values


def require_object_list(value: Any, label: str) -> list[dict[str, Any]]:
    if not isinstance(value, list) or not value:
        raise YtDlpCvePolicyError(f"{label} must be a non-empty list")
    return [require_object(item, f"{label}[{index}]") for index, item in enumerate(value)]


def parse_yt_dlp_version(value: str, label: str) -> tuple[int, int, int]:
    parts = value.split(".")
    if len(parts) != 3 or not all(part.isdecimal() for part in parts):
        raise YtDlpCvePolicyError(f"{label} must use YYYY.MM.DD format: {value}")
    year, month, day = (int(part) for part in parts)
    if not (2000 <= year <= 2999 and 1 <= month <= 12 and 1 <= day <= 31):
        raise YtDlpCvePolicyError(f"{label} is not a plausible date version: {value}")
    return year, month, day


def iter_ytdlp_versions(value: Any) -> list[str]:
    versions: list[str] = []
    if isinstance(value, dict):
        for key, child in value.items():
            if key == "yt-dlp version":
                versions.append(require_string(child, "yt-dlp version"))
            else:
                versions.extend(iter_ytdlp_versions(child))
    elif isinstance(value, list):
        for child in value:
            versions.extend(iter_ytdlp_versions(child))
    return versions


def bundled_ytdlp_version(lock: dict[str, Any]) -> str:
    versions = sorted(set(iter_ytdlp_versions(lock)))
    if not versions:
        raise YtDlpCvePolicyError("native compliance lock does not record a yt-dlp version")
    if len(versions) != 1:
        raise YtDlpCvePolicyError(f"native compliance lock has multiple yt-dlp versions: {versions}")
    return versions[0]


def version_in_affected_range(version: str, introduced: str, fixed: str) -> bool:
    current = parse_yt_dlp_version(version, "yt-dlp version")
    first_affected = parse_yt_dlp_version(introduced, "affectedVersionRange.introduced")
    first_fixed = parse_yt_dlp_version(fixed, "affectedVersionRange.fixed")
    return first_affected <= current < first_fixed


def source_files(repo_root: Path, source_roots: list[str]) -> list[Path]:
    files: list[Path] = []
    for root in source_roots:
        root_path = repo_root / root
        if not root_path.exists():
            raise YtDlpCvePolicyError(f"scan source root is missing: {root}")
        candidates = [root_path] if root_path.is_file() else root_path.rglob("*")
        for path in candidates:
            if path.is_file() and path.suffix in {".java", ".kt", ".kts"}:
                files.append(path)
    return sorted(files)


def forbidden_option_hits(repo_root: Path, source_roots: list[str], forbidden_options: list[str]) -> list[str]:
    hits: list[str] = []
    for path in source_files(repo_root, source_roots):
        text = path.read_text(encoding="utf-8", errors="ignore")
        for line_number, line in enumerate(text.splitlines(), start=1):
            for option in forbidden_options:
                if option in line:
                    hits.append(f"{path.relative_to(repo_root)}:{line_number}: {option}")
    return hits


def validate_required_call_sites(repo_root: Path, call_sites: list[dict[str, Any]]) -> list[str]:
    validated: list[str] = []
    for index, raw_site in enumerate(call_sites):
        site_id = require_string(raw_site.get("id"), f"requiredYtDlpCallSites[{index}].id")
        relative_path = require_string(raw_site.get("path"), f"requiredYtDlpCallSites[{index}].path")
        required_terms = require_string_list(
            raw_site.get("requiredTerms"),
            f"requiredYtDlpCallSites[{index}].requiredTerms",
        )
        path = repo_root / relative_path
        if not path.is_file():
            raise YtDlpCvePolicyError(f"{site_id} source file is missing: {relative_path}")
        text = path.read_text(encoding="utf-8", errors="ignore")
        for term in required_terms:
            if term not in text:
                raise YtDlpCvePolicyError(f"{relative_path} is missing required yt-dlp term: {term}")
        validated.append(site_id)
    return validated


def validate_policy(repo_root: Path, policy_path: Path) -> dict[str, Any]:
    policy = require_object(read_json(policy_path), "policy")
    if policy.get("schemaVersion") != 1:
        raise YtDlpCvePolicyError("schemaVersion must be 1")
    if require_string(policy.get("policyKind"), "policyKind") != "ytdlpNetrcCommandCveReachability":
        raise YtDlpCvePolicyError("policyKind must be ytdlpNetrcCommandCveReachability")
    if require_string(policy.get("cve"), "cve") != "CVE-2026-26331":
        raise YtDlpCvePolicyError("cve must be CVE-2026-26331")

    affected_range = require_object(policy.get("affectedVersionRange"), "affectedVersionRange")
    introduced = require_string(affected_range.get("introduced"), "affectedVersionRange.introduced")
    fixed = require_string(affected_range.get("fixed"), "affectedVersionRange.fixed")
    minimum_safe = require_string(policy.get("minimumSafeYtDlpVersion"), "minimumSafeYtDlpVersion")
    if minimum_safe != fixed:
        raise YtDlpCvePolicyError("minimumSafeYtDlpVersion must match affectedVersionRange.fixed")

    lock_path = repo_root / require_string(policy.get("nativeComplianceLockPath"), "nativeComplianceLockPath")
    lock = require_object(read_json(lock_path), "native compliance lock")
    ytdlp_version = bundled_ytdlp_version(lock)
    affected = version_in_affected_range(ytdlp_version, introduced, fixed)

    source_roots = require_string_list(policy.get("scanSourceRoots"), "scanSourceRoots")
    forbidden_options = require_string_list(policy.get("forbiddenOptions"), "forbiddenOptions")
    call_sites = validate_required_call_sites(
        repo_root,
        require_object_list(policy.get("requiredYtDlpCallSites"), "requiredYtDlpCallSites"),
    )
    hits = forbidden_option_hits(repo_root, source_roots, forbidden_options)
    if hits:
        raise YtDlpCvePolicyError(
            "forbidden yt-dlp netrc command option is reachable in Aura source:\n" + "\n".join(hits)
        )

    status = "affected_not_reachable" if affected else "fixed_or_unaffected"
    return {
        "status": status,
        "cve": policy["cve"],
        "advisory": require_string(policy.get("advisory"), "advisory"),
        "bundledYtDlpVersion": ytdlp_version,
        "minimumSafeYtDlpVersion": minimum_safe,
        "forbiddenOptions": forbidden_options,
        "validatedCallSites": call_sites,
        "scannedSourceRoots": source_roots,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    policy_path = (repo_root / args.policy).resolve()
    try:
        result = validate_policy(repo_root, policy_path)
    except YtDlpCvePolicyError as exc:
        print(f"ytdlp-cve-policy: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
