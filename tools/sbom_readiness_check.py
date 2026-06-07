#!/usr/bin/env python3
"""Validate Aura SBOM readiness policy and release workflow wiring."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')
REQUIRED_DOC_TERMS = {
    "SBOM readiness",
    "Current decision",
    "Current release evidence",
    "Future SBOM lane",
    "Release gate",
    "Sources",
}
REQUIRED_SOURCE_URLS = {
    "https://github.com/CycloneDX/cyclonedx-gradle-plugin",
    "https://cyclonedx.org/tool-center/",
    "https://docs.github.com/en/actions/concepts/security/artifact-attestations",
    "https://docs.github.com/actions/security-guides/using-artifact-attestations-to-establish-provenance-for-builds",
    "https://spdx.dev/about/overview/",
}
EXPECTED_STATUS = "deferredUntilN1ToolchainUpgrade"


class SbomReadinessError(ValueError):
    """Raised when SBOM readiness policy validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura SBOM readiness policy.")
    parser.add_argument("--policy", default="docs/distribution/sbom-readiness.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise SbomReadinessError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise SbomReadinessError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise SbomReadinessError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise SbomReadinessError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise SbomReadinessError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise SbomReadinessError(f"{label} contains duplicate values")
    return values


def parse_package_name(repo_root: Path) -> str:
    text = read_text(repo_root, "app/build.gradle.kts", "app Gradle file")
    match = PACKAGE_RE.search(text)
    if not match:
        raise SbomReadinessError("app/build.gradle.kts is missing applicationId")
    return match.group(1)


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    for url in urls:
        if not url.startswith("https://"):
            raise SbomReadinessError(f"sourceUrls entry must be HTTPS: {url}")
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise SbomReadinessError("sourceUrls missing required URLs: " + ", ".join(missing))
    return len(urls)


def validate_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    text = read_text(repo_root, docs_path, "SBOM readiness docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in text:
            raise SbomReadinessError(f"{docs_path} is missing required section text: {term}")
    if EXPECTED_STATUS not in text:
        raise SbomReadinessError(f"{docs_path} is missing current status: {EXPECTED_STATUS}")
    for url in REQUIRED_SOURCE_URLS:
        if url not in text:
            raise SbomReadinessError(f"{docs_path} is missing source URL: {url}")
    for artifact in require_string_list(policy.get("currentReleaseArtifacts"), "currentReleaseArtifacts"):
        if artifact not in text:
            raise SbomReadinessError(f"{docs_path} is missing current release artifact: {artifact}")
    for artifact in require_string_list(policy.get("futureSbomArtifacts"), "futureSbomArtifacts"):
        if artifact not in text:
            raise SbomReadinessError(f"{docs_path} is missing future SBOM artifact: {artifact}")
    for scope in require_string_list(policy.get("futureSbomScope"), "futureSbomScope"):
        if scope not in text:
            raise SbomReadinessError(f"{docs_path} is missing future SBOM scope: {scope}")


def validate_evidence_paths(repo_root: Path, policy: dict[str, Any]) -> int:
    paths = require_string_list(policy.get("currentEvidencePaths"), "currentEvidencePaths")
    for relative_path in paths:
        if not (repo_root / relative_path).is_file():
            raise SbomReadinessError(f"currentEvidencePaths entry is missing: {relative_path}")
    return len(paths)


def require_before(text: str, first: str, second: str, label: str) -> None:
    first_index = text.find(first)
    if first_index == -1:
        raise SbomReadinessError(f"{label} is missing required command: {first}")
    second_index = text.find(second)
    if second_index == -1:
        raise SbomReadinessError(f"{label} is missing marker: {second}")
    if first_index > second_index:
        raise SbomReadinessError(f"{label} must run {first} before {second}")


def validate_workflow_wiring(repo_root: Path, policy: dict[str, Any]) -> None:
    commands = require_string_list(policy.get("requiredWorkflowCommands"), "requiredWorkflowCommands")
    verify_workflow = read_text(repo_root, ".github/workflows/verify.yml", "verify workflow")
    release_workflow = read_text(repo_root, ".github/workflows/release.yml", "release workflow")
    supply_chain = read_text(repo_root, "docs/distribution/supply-chain.md", "supply-chain docs")
    release_dry_run = read_text(repo_root, "docs/distribution/release-dry-run.md", "release dry-run docs")
    release_signing = read_text(repo_root, "docs/distribution/release-signing.md", "release signing docs")

    for command in commands:
        if command not in verify_workflow and command != "tools/release_artifact_bundle_check.py":
            raise SbomReadinessError(f"verify workflow missing command: {command}")
        if command not in release_workflow:
            raise SbomReadinessError(f"release workflow missing command: {command}")
        if command not in supply_chain:
            raise SbomReadinessError(f"supply-chain docs missing command: {command}")

    require_before(verify_workflow, "tools/sbom_readiness_check.py", "Dependency notice drift", "verify workflow")
    require_before(release_workflow, "tools/sbom_readiness_check.py", "Build signed release APK", "release workflow")

    for artifact in require_string_list(policy.get("currentReleaseArtifacts"), "currentReleaseArtifacts"):
        if artifact not in supply_chain and artifact != "Aura-vX.Y.Z-versionCode-N-universal-release.apk":
            raise SbomReadinessError(f"supply-chain docs missing release artifact: {artifact}")
        if artifact not in release_dry_run and artifact not in release_signing:
            raise SbomReadinessError(f"release docs missing release artifact: {artifact}")
    for artifact in require_string_list(policy.get("futureSbomArtifacts"), "futureSbomArtifacts"):
        if artifact not in supply_chain:
            raise SbomReadinessError(f"supply-chain docs missing future SBOM artifact: {artifact}")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise SbomReadinessError("schemaVersion must be 1")
    if policy.get("policyKind") != "sbomReadiness":
        raise SbomReadinessError("policyKind must be sbomReadiness")
    if policy.get("status") != EXPECTED_STATUS:
        raise SbomReadinessError(f"status must be {EXPECTED_STATUS}")

    package_name = require_string(policy.get("packageName"), "packageName")
    if parse_package_name(repo_root) != package_name:
        raise SbomReadinessError("packageName does not match app/build.gradle.kts")

    validate_docs(repo_root, policy)
    source_url_count = validate_source_urls(policy)
    evidence_path_count = validate_evidence_paths(repo_root, policy)
    validate_workflow_wiring(repo_root, policy)

    return {
        "status": "ok",
        "policyKind": "sbomReadiness",
        "packageName": package_name,
        "decision": EXPECTED_STATUS,
        "sourceUrlCount": source_url_count,
        "evidencePathCount": evidence_path_count,
        "futureSbomArtifactCount": len(require_string_list(policy.get("futureSbomArtifacts"), "futureSbomArtifacts")),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "SBOM readiness policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
