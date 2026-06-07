#!/usr/bin/env python3
"""Validate Aura release metadata consistency across store and release docs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


TITLE_MAX_CHARS = 30
SHORT_DESCRIPTION_MAX_CHARS = 80
FULL_DESCRIPTION_MAX_CHARS = 4000
PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')
VERSION_NAME_RE = re.compile(r'versionName\s*=\s*"([^"]+)"')
VERSION_CODE_RE = re.compile(r"versionCode\s*=\s*(\d+)")
REQUIRED_DOC_TERMS = {
    "Release metadata consistency",
    "Current package",
    "Metadata surfaces",
    "Release preflights",
    "Release artifacts",
    "Release checklist",
    "Sources",
}
REQUIRED_SOURCE_URLS = {
    "https://support.google.com/googleplay/android-developer/answer/9859152",
    "https://support.google.com/googleplay/android-developer/answer/13393723",
    "https://docs.fastlane.tools/actions/supply/",
    "https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts",
    "https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases",
    "https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/",
}


class ReleaseMetadataConsistencyError(ValueError):
    """Raised when release metadata surfaces drift apart."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura release metadata consistency.")
    parser.add_argument("--policy", default="docs/distribution/release-metadata-consistency.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise ReleaseMetadataConsistencyError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise ReleaseMetadataConsistencyError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReleaseMetadataConsistencyError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ReleaseMetadataConsistencyError(f"{label} must be a non-empty string")
    return value.strip()


def require_int(value: Any, label: str) -> int:
    if not isinstance(value, int):
        raise ReleaseMetadataConsistencyError(f"{label} must be an integer")
    return value


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ReleaseMetadataConsistencyError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise ReleaseMetadataConsistencyError(f"{label} contains duplicate values")
    return values


def parse_gradle(repo_root: Path) -> dict[str, object]:
    text = read_text(repo_root, "app/build.gradle.kts", "app Gradle file")
    package = PACKAGE_RE.search(text)
    version_name = VERSION_NAME_RE.search(text)
    version_code = VERSION_CODE_RE.search(text)
    if not package or not version_name or not version_code:
        raise ReleaseMetadataConsistencyError("app/build.gradle.kts is missing package or version metadata")
    return {
        "packageName": package.group(1),
        "versionName": version_name.group(1),
        "versionCode": int(version_code.group(1)),
    }


def validate_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "release metadata docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise ReleaseMetadataConsistencyError(f"{docs_path} is missing required section text: {term}")
    for source_url in REQUIRED_SOURCE_URLS:
        if source_url not in docs_text:
            raise ReleaseMetadataConsistencyError(f"{docs_path} is missing source URL: {source_url}")


def validate_required_paths(repo_root: Path, policy: dict[str, Any]) -> None:
    for relative_path in require_string_list(policy.get("requiredEvidencePaths"), "requiredEvidencePaths"):
        if not (repo_root / relative_path).is_file():
            raise ReleaseMetadataConsistencyError(f"requiredEvidencePaths entry is missing: {relative_path}")


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise ReleaseMetadataConsistencyError("sourceUrls missing required URLs: " + ", ".join(missing))
    return len(urls)


def validate_fastlane(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    metadata_root = require_string(policy.get("metadataRoot"), "metadataRoot")
    version_name = require_string(policy.get("versionName"), "versionName")
    version_code = require_int(policy.get("versionCode"), "versionCode")
    privacy_url = require_string(policy.get("privacyPolicyUrl"), "privacyPolicyUrl")
    title = read_text(repo_root, f"{metadata_root}/title.txt", "Fastlane title").strip()
    short_description = read_text(repo_root, f"{metadata_root}/short_description.txt", "Fastlane short description").strip()
    full_description = read_text(repo_root, f"{metadata_root}/full_description.txt", "Fastlane full description").strip()
    changelog = read_text(repo_root, f"{metadata_root}/changelogs/{version_code}.txt", "current Fastlane changelog")
    if not title or len(title) > TITLE_MAX_CHARS:
        raise ReleaseMetadataConsistencyError("Fastlane title is blank or too long")
    if not short_description or len(short_description) > SHORT_DESCRIPTION_MAX_CHARS or "\n" in short_description:
        raise ReleaseMetadataConsistencyError("Fastlane short description is blank, too long, or multiline")
    if not full_description or len(full_description) > FULL_DESCRIPTION_MAX_CHARS:
        raise ReleaseMetadataConsistencyError("Fastlane full description is blank or too long")
    if privacy_url not in full_description:
        raise ReleaseMetadataConsistencyError("Fastlane full description is missing privacyPolicyUrl")
    if "No ads" not in full_description or "no tracking" not in full_description:
        raise ReleaseMetadataConsistencyError("Fastlane full description is missing no ads/no tracking wording")
    if version_name not in changelog:
        raise ReleaseMetadataConsistencyError("current Fastlane changelog must mention versionName")
    if "Recent highlights:" not in changelog:
        raise ReleaseMetadataConsistencyError("current Fastlane changelog must keep recent highlights")
    return {
        "titleChars": len(title),
        "shortDescriptionChars": len(short_description),
        "fullDescriptionChars": len(full_description),
    }


def validate_readme(repo_root: Path, policy: dict[str, Any]) -> None:
    readme = read_text(repo_root, "README.md", "README")
    privacy_url = require_string(policy.get("privacyPolicyUrl"), "privacyPolicyUrl")
    if privacy_url not in readme:
        raise ReleaseMetadataConsistencyError("README is missing privacyPolicyUrl")
    for link in require_string_list(policy.get("requiredReadmeLinks"), "requiredReadmeLinks"):
        if link not in readme:
            raise ReleaseMetadataConsistencyError(f"README is missing required link: {link}")


def validate_packet_alignment(repo_root: Path, policy: dict[str, Any]) -> None:
    package_name = require_string(policy.get("packageName"), "packageName")
    privacy_url = require_string(policy.get("privacyPolicyUrl"), "privacyPolicyUrl")
    privacy_link = require_object(read_json(repo_root / "docs/privacy/privacy-policy-link.json"), "privacy link policy")
    play_packet = require_object(read_json(repo_root / "docs/distribution/play-app-content.json"), "Play App content packet")
    alt_packet = require_object(read_json(repo_root / "docs/distribution/alt-store-metadata.json"), "alt-store packet")
    if privacy_link.get("publicUrl") != privacy_url:
        raise ReleaseMetadataConsistencyError("privacy-policy link publicUrl does not match release metadata")
    if play_packet.get("packageName") != package_name:
        raise ReleaseMetadataConsistencyError("Play App content packageName does not match release metadata")
    play_privacy = require_object(
        require_object(play_packet.get("declarations"), "Play declarations").get("privacyPolicy"),
        "Play privacyPolicy",
    )
    if play_privacy.get("url") != privacy_url:
        raise ReleaseMetadataConsistencyError("Play App content privacy URL does not match release metadata")
    if alt_packet.get("packageName") != package_name:
        raise ReleaseMetadataConsistencyError("alt-store packageName does not match release metadata")


def validate_release_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    commands = require_string_list(policy.get("requiredPreflightCommands"), "requiredPreflightCommands")
    artifacts = require_string_list(policy.get("requiredReleaseArtifacts"), "requiredReleaseArtifacts")
    docs = {
        "release dry-run docs": read_text(repo_root, "docs/distribution/release-dry-run.md", "release dry-run docs"),
        "release signing docs": read_text(repo_root, "docs/distribution/release-signing.md", "release signing docs"),
        "supply-chain docs": read_text(repo_root, "docs/distribution/supply-chain.md", "supply-chain docs"),
        "verify workflow": read_text(repo_root, ".github/workflows/verify.yml", "verify workflow"),
        "release workflow": read_text(repo_root, ".github/workflows/release.yml", "release workflow"),
    }
    for command in commands:
        if not any(command in text for text in docs.values()):
            raise ReleaseMetadataConsistencyError(f"required preflight command is missing everywhere: {command}")
        if command != "tools/release_artifact_bundle_check.py":
            for label in ("verify workflow", "release workflow"):
                if command not in docs[label]:
                    raise ReleaseMetadataConsistencyError(f"{label} missing preflight command: {command}")
    for artifact in artifacts:
        if not any(artifact in text for text in docs.values()):
            raise ReleaseMetadataConsistencyError(f"required release artifact is missing from docs: {artifact}")
    release_workflow = docs["release workflow"]
    for snippet in ("RELEASE_NOTES.md", "SHA256SUMS.txt", "actions/attest@v4"):
        if snippet not in release_workflow:
            raise ReleaseMetadataConsistencyError(f"release workflow missing snippet: {snippet}")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise ReleaseMetadataConsistencyError("schemaVersion must be 1")
    if policy.get("policyKind") != "releaseMetadataConsistency":
        raise ReleaseMetadataConsistencyError("policyKind must be releaseMetadataConsistency")
    gradle = parse_gradle(repo_root)
    package_name = require_string(policy.get("packageName"), "packageName")
    version_name = require_string(policy.get("versionName"), "versionName")
    version_code = require_int(policy.get("versionCode"), "versionCode")
    if gradle["packageName"] != package_name:
        raise ReleaseMetadataConsistencyError("packageName does not match app/build.gradle.kts")
    if gradle["versionName"] != version_name:
        raise ReleaseMetadataConsistencyError("versionName does not match app/build.gradle.kts")
    if gradle["versionCode"] != version_code:
        raise ReleaseMetadataConsistencyError("versionCode does not match app/build.gradle.kts")
    validate_docs(repo_root, policy)
    validate_required_paths(repo_root, policy)
    source_url_count = validate_source_urls(policy)
    fastlane = validate_fastlane(repo_root, policy)
    validate_readme(repo_root, policy)
    validate_packet_alignment(repo_root, policy)
    validate_release_docs(repo_root, policy)
    return {
        "status": "ok",
        "policyKind": "releaseMetadataConsistency",
        "packageName": package_name,
        "versionName": version_name,
        "versionCode": version_code,
        "sourceUrlCount": source_url_count,
        **fastlane,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "release metadata consistency policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
