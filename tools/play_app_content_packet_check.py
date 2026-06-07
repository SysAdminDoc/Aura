#!/usr/bin/env python3
"""Validate Aura's Google Play App content declaration packet."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


REQUIRED_DOC_TERMS = {
    "Privacy Policy",
    "Ads",
    "App Access",
    "Target Audience",
    "Content Rating",
    "Data Safety",
    "User-Generated Content",
    "Generated Content",
    "Sensitive Permissions",
    "Owner Actions",
    "Release Checklist",
    "Sources",
}
REQUIRED_DECLARATIONS = {
    "privacyPolicy",
    "ads",
    "appAccess",
    "targetAudience",
    "contentRatingNotes",
    "dataSafety",
    "ugc",
    "generatedContent",
    "sensitivePermissions",
}
SUPPORTED_PLAY_STATUSES = {
    "ownerActionRequired",
    "readyForPlayReview",
}
SUPPORTED_OWNER_ACTION_STATUSES = {
    "requiredBeforePlayProduction",
    "ownerConfirmationRequired",
    "monitoringRequired",
}
CHILD_TARGET_AGE_GROUPS = {"under 5", "6-8", "9-12", "under 13"}
PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')


class PlayAppContentPacketError(ValueError):
    """Raised when the Play App content packet is stale or incomplete."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura Play App content packet.")
    parser.add_argument("--policy", default="docs/distribution/play-app-content.json")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise PlayAppContentPacketError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise PlayAppContentPacketError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise PlayAppContentPacketError(f"{label} must be a JSON object")
    return value


def require_bool(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise PlayAppContentPacketError(f"{label} must be a boolean")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise PlayAppContentPacketError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise PlayAppContentPacketError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise PlayAppContentPacketError(f"{label} contains duplicate values")
    return values


def require_evidence_refs(repo_root: Path, value: Any, label: str) -> list[str]:
    refs = require_string_list(value, label)
    for ref in refs:
        if not (repo_root / ref).is_file():
            raise PlayAppContentPacketError(f"{label} entry is missing: {ref}")
    return refs


def validate_package(repo_root: Path, expected_package: str) -> None:
    app_gradle = read_text(repo_root, "app/build.gradle.kts", "app Gradle file")
    match = PACKAGE_RE.search(app_gradle)
    if not match:
        raise PlayAppContentPacketError("app/build.gradle.kts is missing applicationId")
    if match.group(1) != expected_package:
        raise PlayAppContentPacketError(
            f"packageName mismatch: app Gradle={match.group(1)}, policy={expected_package}"
        )


def validate_required_paths(repo_root: Path, policy: dict[str, Any]) -> None:
    for path in require_string_list(policy.get("requiredEvidencePaths"), "requiredEvidencePaths"):
        if not (repo_root / path).is_file():
            raise PlayAppContentPacketError(f"requiredEvidencePaths entry is missing: {path}")


def validate_docs(repo_root: Path, policy: dict[str, Any]) -> str:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "Play App content docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise PlayAppContentPacketError(f"{docs_path} is missing required section text: {term}")
    return docs_text


def validate_privacy_policy(repo_root: Path, docs_text: str, raw: Any) -> None:
    section = require_object(raw, "privacyPolicy")
    url = require_string(section.get("url"), "privacyPolicy.url")
    if not url.startswith("https://"):
        raise PlayAppContentPacketError("privacyPolicy.url must use HTTPS")
    require_evidence_refs(repo_root, section.get("evidenceRefs"), "privacyPolicy.evidenceRefs")
    fastlane_text = read_text(repo_root, "fastlane/metadata/android/en-US/full_description.txt", "Fastlane full description")
    privacy_text = read_text(repo_root, "docs/privacy/privacy-policy.md", "privacy policy")
    if url not in fastlane_text:
        raise PlayAppContentPacketError("Fastlane full description is missing the privacy policy URL")
    if "Privacy Policy" not in docs_text or "privacy policy" not in privacy_text.lower():
        raise PlayAppContentPacketError("privacy policy docs are missing expected policy wording")


def validate_ads(repo_root: Path, raw: Any) -> None:
    section = require_object(raw, "ads")
    contains_ads = require_bool(section.get("containsAds"), "ads.containsAds")
    require_string(section.get("answer"), "ads.answer")
    require_evidence_refs(repo_root, section.get("evidenceRefs"), "ads.evidenceRefs")
    privacy_text = " ".join(
        read_text(repo_root, "docs/privacy/privacy-policy.md", "privacy policy").lower().split()
    )
    fastlane_text = " ".join(
        read_text(repo_root, "fastlane/metadata/android/en-US/full_description.txt", "Fastlane full description")
        .lower()
        .split()
    )
    if contains_ads:
        raise PlayAppContentPacketError("ads.containsAds must remain false for Aura's current no-ads claim")
    if "no ads" not in privacy_text or "no ads" not in fastlane_text:
        raise PlayAppContentPacketError("no-ads evidence is missing no-ads wording")
    if "cross-app tracking" not in privacy_text or "no tracking" not in fastlane_text:
        raise PlayAppContentPacketError("tracking evidence is missing reviewed wording")


def validate_app_access(repo_root: Path, raw: Any) -> None:
    section = require_object(raw, "appAccess")
    require_bool(section.get("restrictedAccess"), "appAccess.restrictedAccess")
    require_string(section.get("reviewerInstructions"), "appAccess.reviewerInstructions")
    require_evidence_refs(repo_root, section.get("evidenceRefs"), "appAccess.evidenceRefs")


def validate_target_audience(repo_root: Path, raw: Any) -> None:
    section = require_object(raw, "targetAudience")
    designed_for_children = require_bool(section.get("designedForChildren"), "targetAudience.designedForChildren")
    age_groups = {age.lower() for age in require_string_list(section.get("ageGroups"), "targetAudience.ageGroups")}
    require_string(section.get("rationale"), "targetAudience.rationale")
    require_evidence_refs(repo_root, section.get("evidenceRefs"), "targetAudience.evidenceRefs")
    if designed_for_children:
        raise PlayAppContentPacketError("targetAudience.designedForChildren must remain false for the current packet")
    if age_groups & CHILD_TARGET_AGE_GROUPS:
        raise PlayAppContentPacketError("targetAudience.ageGroups includes a child age group")


def validate_content_rating_notes(repo_root: Path, raw: Any) -> int:
    rows = raw
    if not isinstance(rows, list) or not rows:
        raise PlayAppContentPacketError("contentRatingNotes must be a non-empty list")
    seen: set[str] = set()
    for index, raw_row in enumerate(rows):
        row = require_object(raw_row, f"contentRatingNotes[{index}]")
        form_area = require_string(row.get("formArea"), f"contentRatingNotes[{index}].formArea")
        if form_area in seen:
            raise PlayAppContentPacketError(f"duplicate contentRatingNotes row: {form_area}")
        seen.add(form_area)
        require_string(row.get("answer"), f"{form_area}.answer")
        require_evidence_refs(repo_root, row.get("evidenceRefs"), f"{form_area}.evidenceRefs")
    required_areas = {"User-generated content", "Generated content", "Ads and purchases"}
    missing = sorted(required_areas - seen)
    if missing:
        raise PlayAppContentPacketError(f"contentRatingNotes missing required areas: {', '.join(missing)}")
    return len(rows)


def validate_data_safety(repo_root: Path, raw: Any) -> dict[str, Any]:
    section = require_object(raw, "dataSafety")
    policy_path = require_string(section.get("policyPath"), "dataSafety.policyPath")
    docs_path = require_string(section.get("docsPath"), "dataSafety.docsPath")
    privacy_policy_path = require_string(section.get("privacyPolicyPath"), "dataSafety.privacyPolicyPath")
    require_evidence_refs(repo_root, section.get("evidenceRefs"), "dataSafety.evidenceRefs")
    data_safety = require_object(read_json(repo_root / policy_path), "data safety policy")
    read_text(repo_root, docs_path, "data safety docs")
    read_text(repo_root, privacy_policy_path, "privacy policy")
    if data_safety.get("policyKind") != "privacyDataSafetyMatrix":
        raise PlayAppContentPacketError("dataSafety.policyPath does not point to the Data safety matrix")
    return data_safety


def validate_ugc(repo_root: Path, raw: Any, owner_action_ids: set[str]) -> None:
    section = require_object(raw, "ugc")
    has_ugc = require_bool(section.get("hasUserGeneratedContent"), "ugc.hasUserGeneratedContent")
    report_and_block = require_bool(section.get("reportAndBlockAvailable"), "ugc.reportAndBlockAvailable")
    require_bool(section.get("moderationQueueAvailable"), "ugc.moderationQueueAvailable")
    guidelines_status = require_string(section.get("termsOrGuidelinesStatus"), "ugc.termsOrGuidelinesStatus")
    require_string(section.get("answer"), "ugc.answer")
    require_evidence_refs(repo_root, section.get("evidenceRefs"), "ugc.evidenceRefs")
    if has_ugc and not report_and_block:
        raise PlayAppContentPacketError("UGC is enabled but report/block is not marked available")
    if guidelines_status != "implemented" and "confirm-ugc-guidelines-consent" not in owner_action_ids:
        raise PlayAppContentPacketError("UGC guidelines owner action is missing")


def validate_generated_content(repo_root: Path, raw: Any) -> None:
    section = require_object(raw, "generatedContent")
    has_generated_content = require_bool(section.get("hasGeneratedContent"), "generatedContent.hasGeneratedContent")
    report_available = require_bool(section.get("reportAvailable"), "generatedContent.reportAvailable")
    require_bool(section.get("disclosureRequired"), "generatedContent.disclosureRequired")
    require_string(section.get("generationProvider"), "generatedContent.generationProvider")
    require_string(section.get("answer"), "generatedContent.answer")
    require_evidence_refs(repo_root, section.get("evidenceRefs"), "generatedContent.evidenceRefs")
    if has_generated_content and not report_available:
        raise PlayAppContentPacketError("generated content is enabled but reportAvailable is false")


def validate_sensitive_permissions(repo_root: Path, raw: Any, data_safety: dict[str, Any]) -> int:
    rows = raw
    if not isinstance(rows, list) or not rows:
        raise PlayAppContentPacketError("sensitivePermissions must be a non-empty list")
    matrix_permissions = {
        require_string(require_object(row, "data safety permission row").get("name"), "data safety permission name")
        for row in data_safety.get("permissions", [])
    }
    seen: set[str] = set()
    for index, raw_row in enumerate(rows):
        row = require_object(raw_row, f"sensitivePermissions[{index}]")
        name = require_string(row.get("name"), f"sensitivePermissions[{index}].name")
        if name in seen:
            raise PlayAppContentPacketError(f"duplicate sensitive permission row: {name}")
        seen.add(name)
        if name not in matrix_permissions:
            raise PlayAppContentPacketError(f"sensitive permission is missing from Data safety matrix: {name}")
        require_string(row.get("declaration"), f"{name}.declaration")
        require_evidence_refs(repo_root, row.get("evidenceRefs"), f"{name}.evidenceRefs")
    return len(rows)


def validate_owner_actions(repo_root: Path, policy: dict[str, Any], docs_text: str) -> set[str]:
    play_status = require_string(policy.get("playSubmissionStatus"), "playSubmissionStatus")
    if play_status not in SUPPORTED_PLAY_STATUSES:
        raise PlayAppContentPacketError("playSubmissionStatus is unsupported")
    raw_actions = policy.get("ownerActions")
    if not isinstance(raw_actions, list) or (play_status == "ownerActionRequired" and not raw_actions):
        raise PlayAppContentPacketError("ownerActions must be non-empty when owner action is required")
    action_ids: set[str] = set()
    for index, raw_action in enumerate(raw_actions):
        action = require_object(raw_action, f"ownerActions[{index}]")
        action_id = require_string(action.get("id"), f"ownerActions[{index}].id")
        if action_id in action_ids:
            raise PlayAppContentPacketError(f"duplicate owner action: {action_id}")
        action_ids.add(action_id)
        status = require_string(action.get("status"), f"{action_id}.status")
        if status not in SUPPORTED_OWNER_ACTION_STATUSES:
            raise PlayAppContentPacketError(f"{action_id}.status is unsupported")
        require_string(action.get("description"), f"{action_id}.description")
        require_evidence_refs(repo_root, action.get("evidenceRefs"), f"{action_id}.evidenceRefs")
        if action_id not in docs_text:
            raise PlayAppContentPacketError(f"docs are missing owner action row: {action_id}")
    return action_ids


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, Any]:
    if policy.get("schemaVersion") != 1:
        raise PlayAppContentPacketError("Play App content schemaVersion must be 1")
    if policy.get("policyKind") != "playAppContentPacket":
        raise PlayAppContentPacketError("Play App content policyKind is invalid")
    package_name = require_string(policy.get("packageName"), "packageName")
    validate_package(repo_root, package_name)
    validate_required_paths(repo_root, policy)
    docs_text = validate_docs(repo_root, policy)
    owner_action_ids = validate_owner_actions(repo_root, policy, docs_text)

    declarations = require_object(policy.get("declarations"), "declarations")
    missing = sorted(REQUIRED_DECLARATIONS - set(declarations))
    if missing:
        raise PlayAppContentPacketError(f"declarations missing fields: {', '.join(missing)}")
    validate_privacy_policy(repo_root, docs_text, declarations.get("privacyPolicy"))
    validate_ads(repo_root, declarations.get("ads"))
    validate_app_access(repo_root, declarations.get("appAccess"))
    validate_target_audience(repo_root, declarations.get("targetAudience"))
    content_rating_note_count = validate_content_rating_notes(repo_root, declarations.get("contentRatingNotes"))
    data_safety = validate_data_safety(repo_root, declarations.get("dataSafety"))
    validate_ugc(repo_root, declarations.get("ugc"), owner_action_ids)
    validate_generated_content(repo_root, declarations.get("generatedContent"))
    sensitive_permission_count = validate_sensitive_permissions(
        repo_root,
        declarations.get("sensitivePermissions"),
        data_safety,
    )

    source_urls = require_string_list(policy.get("sourceUrls"), "sourceUrls")
    for url in source_urls:
        if not url.startswith("https://"):
            raise PlayAppContentPacketError(f"sourceUrls must use HTTPS: {url}")
        if url not in docs_text:
            raise PlayAppContentPacketError(f"docs are missing source URL: {url}")

    return {
        "policyKind": policy["policyKind"],
        "schemaVersion": policy["schemaVersion"],
        "status": "ok",
        "packageName": package_name,
        "playSubmissionStatus": policy["playSubmissionStatus"],
        "ownerActionCount": len(owner_action_ids),
        "contentRatingNoteCount": content_rating_note_count,
        "sensitivePermissionCount": sensitive_permission_count,
        "sourceUrlCount": len(source_urls),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json((repo_root / args.policy).resolve()), "Play App content policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
