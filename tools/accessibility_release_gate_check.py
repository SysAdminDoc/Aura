from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


class AccessibilityReleaseGateError(ValueError):
    pass


REQUIRED_SCENARIOS = {
    "wallpapers-feed-detail",
    "video-wallpapers",
    "sounds-editor",
    "settings-diagnostics",
    "adaptive-access",
    "widget-entrypoints",
}


def read_text(path: Path) -> str:
    if not path.is_file():
        raise AccessibilityReleaseGateError(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise AccessibilityReleaseGateError(f"{label} must be an object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise AccessibilityReleaseGateError(f"{label} must be a non-empty string")
    return value.strip()


def require_list(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list) or not value:
        raise AccessibilityReleaseGateError(f"{label} must be a non-empty list")
    return value


def validate_accessibility_release_gate(repo_root: Path, policy_path: str) -> dict[str, int | str]:
    policy = require_object(json.loads(read_text(repo_root / policy_path)), "policy")
    if policy.get("schemaVersion") != 1:
        raise AccessibilityReleaseGateError("schemaVersion must be 1")
    if policy.get("policyKind") != "accessibilityReleaseGate":
        raise AccessibilityReleaseGateError("policyKind must be accessibilityReleaseGate")
    if policy.get("status") != "releaseGateReadyManualEvidenceRequired":
        raise AccessibilityReleaseGateError("status must be releaseGateReadyManualEvidenceRequired")

    automated = require_object(policy.get("automatedGate"), "automatedGate")
    test_path = require_string(automated.get("instrumentedTest"), "automatedGate.instrumentedTest")
    required_api = require_string(automated.get("requiredApi"), "automatedGate.requiredApi")
    verify_command = require_string(automated.get("verifyCommand"), "automatedGate.verifyCommand")
    required_dependencies = [
        require_string(item, "automatedGate.requiredDependencies[]")
        for item in require_list(
            automated.get("requiredDependencies"),
            "automatedGate.requiredDependencies",
        )
    ]

    test_text = read_text(repo_root / test_path)
    for term in (
        required_api,
        "createAndroidComposeRule",
        "tryPerformAccessibilityChecks",
        "@SdkSuppress(minSdkVersion = 34)",
        "coreInteractivePatternsExposeAccessibleNamesAndStates",
    ):
        if term not in test_text:
            raise AccessibilityReleaseGateError(f"{test_path} missing automated accessibility term: {term}")

    version_catalog = read_text(repo_root / "gradle/libs.versions.toml")
    app_gradle = read_text(repo_root / "app/build.gradle.kts")
    if 'compose-ui-test = "1.8.0"' not in version_catalog:
        raise AccessibilityReleaseGateError("version catalog must pin Compose UI test artifacts to 1.8.0 or newer")
    for coordinate in required_dependencies:
        group, name = coordinate.split(":", 1)
        if group not in version_catalog or name not in version_catalog:
            raise AccessibilityReleaseGateError(f"version catalog missing dependency coordinate: {coordinate}")
    for term in (
        "libs.compose.ui.test.junit4",
        "libs.compose.ui.test.junit4.accessibility",
        "libs.compose.ui.test.manifest",
    ):
        if term not in app_gradle:
            raise AccessibilityReleaseGateError(f"app/build.gradle.kts missing accessibility test dependency: {term}")

    workflow_text = read_text(repo_root / ".github/workflows/verify.yml")
    if "tools/accessibility_release_gate_check.py" not in workflow_text:
        raise AccessibilityReleaseGateError("verify workflow missing accessibility release gate check")

    scenario_rows = [
        require_object(item, "manualScenarios[]")
        for item in require_list(policy.get("manualScenarios"), "manualScenarios")
    ]
    scenario_ids = {require_string(row.get("id"), "manualScenarios[].id") for row in scenario_rows}
    missing = sorted(REQUIRED_SCENARIOS - scenario_ids)
    if missing:
        raise AccessibilityReleaseGateError("manualScenarios missing: " + ", ".join(missing))
    for row in scenario_rows:
        require_string(row.get("surface"), f"{row.get('id')}.surface")
        checks = [
            require_string(item, f"{row.get('id')}.checks[]")
            for item in require_list(row.get("checks"), f"{row.get('id')}.checks")
        ]
        if len(checks) < 3:
            raise AccessibilityReleaseGateError(f"{row.get('id')}.checks must cover at least three checks")

    source_urls = [require_string(item, "sourceUrls[]") for item in require_list(policy.get("sourceUrls"), "sourceUrls")]
    if not all(url.startswith("https://developer.android.com/") for url in source_urls):
        raise AccessibilityReleaseGateError("sourceUrls must be Android developer HTTPS docs")
    if "explicit release waiver" not in require_string(policy.get("releaseRequirement"), "releaseRequirement"):
        raise AccessibilityReleaseGateError("releaseRequirement must define pass-or-waive behavior")
    if "connectedDebugAndroidTest" not in verify_command:
        raise AccessibilityReleaseGateError("verifyCommand must name connectedDebugAndroidTest")

    return {
        "status": "ok",
        "policyKind": "accessibilityReleaseGate",
        "scenarioCount": len(scenario_rows),
        "sourceUrlCount": len(source_urls),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate Aura accessibility release gate.")
    parser.add_argument("--repo-root", default=".", type=Path)
    parser.add_argument("--policy", default="docs/qa/accessibility-release-gate.json")
    args = parser.parse_args()
    print(json.dumps(validate_accessibility_release_gate(args.repo_root.resolve(), args.policy), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
