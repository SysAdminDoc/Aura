from __future__ import annotations

import argparse
import json
import re

# The Compose BOM that first carried the UI-test artifacts this gate relies on.
# A floor, not a pin: newer BOMs are the expected state.
MINIMUM_COMPOSE_BOM = (2025, 6, 0)
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

REQUIRED_EXECUTED_SURFACES = {
    "wallpapers-grid",
    "wallpapers-offline",
    "sounds-detail",
    "settings-diagnostics",
    "video-wallpapers",
    "wallpaper-editor",
}

PRODUCTION_ROUTE_SOURCE = "app/src/main/java/com/freevibe/ui/qa/ProductionRouteState.kt"
SCREEN_SOURCE = "app/src/main/java/com/freevibe/ui/navigation/Screen.kt"
SCREEN_OBJECT = re.compile(r"data object (\w+)\s*:\s*Screen\b")
APP_GRADLE = "app/build.gradle.kts"
VERSION_NAME_RE = re.compile(r'versionName\s*=\s*"([^"]+)"')

FORBIDDEN_DIRECT_PRIMITIVES = {
    "SettingsSection(",
    "SettingsToggle(",
    "SettingsItem(",
    "SettingsMetric(",
    "SettingsValueSlider(",
    "AuraStateCard(",
}


def screen_destinations(repo_root: Path) -> list[str]:
    """Every navigable destination, read from Screen.kt rather than restated.

    The gate used to describe itself as the accessibility release gate while
    checking six surfaces, so the twenty destinations outside them were not
    merely unchecked, they were invisible. Enumerating the real list is what
    makes an omission an error instead of an absence.
    """
    names = SCREEN_OBJECT.findall(read_text(repo_root / SCREEN_SOURCE))
    if not names:
        raise AccessibilityReleaseGateError(
            f"{SCREEN_SOURCE} yielded no Screen destinations; the scanner is reading nothing"
        )
    return sorted(set(names))


def declared_version_name(repo_root: Path) -> str:
    match = VERSION_NAME_RE.search(read_text(repo_root / APP_GRADLE))
    if not match:
        raise AccessibilityReleaseGateError(f"{APP_GRADLE} declares no versionName")
    return match.group(1)


def validate_destination_coverage(
    repo_root: Path,
    policy: dict[str, Any],
    executed_ids: set[str],
) -> dict[str, int]:
    """Every destination is either exercised or carries a written reason."""
    coverage = policy.get("destinationCoverage")
    if not isinstance(coverage, dict):
        raise AccessibilityReleaseGateError(
            "destinationCoverage must be an object mapping every Screen destination "
            "to either an executed surface id or a reason it cannot be exercised"
        )

    destinations = screen_destinations(repo_root)
    missing = [name for name in destinations if name not in coverage]
    if missing:
        raise AccessibilityReleaseGateError(
            "destinationCoverage is missing Screen destinations, so they would be "
            "silently unchecked: " + ", ".join(missing)
        )
    stale = [name for name in coverage if name not in destinations]
    if stale:
        raise AccessibilityReleaseGateError(
            "destinationCoverage names destinations Screen.kt no longer declares: "
            + ", ".join(sorted(stale))
        )

    executed = 0
    excused = 0
    for name in destinations:
        entry = require_object(coverage[name], f"destinationCoverage.{name}")
        surface = entry.get("executedSurface")
        reason = entry.get("reason")
        if surface is not None and reason is not None:
            raise AccessibilityReleaseGateError(
                f"destinationCoverage.{name} sets both executedSurface and reason; "
                "a destination is either exercised or excused, not both"
            )
        if surface is not None:
            surface_id = require_string(surface, f"destinationCoverage.{name}.executedSurface")
            if surface_id not in executed_ids:
                raise AccessibilityReleaseGateError(
                    f"destinationCoverage.{name} names executed surface '{surface_id}', "
                    "which is not in automatedGate.executedSurfaces"
                )
            executed += 1
            continue
        if reason is None:
            raise AccessibilityReleaseGateError(
                f"destinationCoverage.{name} has neither executedSurface nor reason"
            )
        text = require_string(reason, f"destinationCoverage.{name}.reason")
        if len(text.split()) < 4:
            raise AccessibilityReleaseGateError(
                f"destinationCoverage.{name}.reason is too short to be a reason: {text!r}"
            )
        excused += 1

    return {"destinationCount": len(destinations), "executed": executed, "excused": excused}


def validate_scenario_currency(
    policy: dict[str, Any],
    scenario_rows: list[dict[str, Any]],
    version_name: str,
) -> dict[str, int]:
    """A manual scenario must be current for the version being released.

    Manual evidence with no version attached is indistinguishable from manual
    evidence nobody ever gathered, which is the state this gate shipped in. Each
    scenario now records either an execution or an explicit waiver, and both are
    stamped with the version they apply to, so a version bump invalidates them
    and forces a decision rather than carrying the claim forward silently.
    """
    executed = 0
    waived = 0
    for row in scenario_rows:
        scenario_id = require_string(row.get("id"), "manualScenarios[].id")
        evidence = row.get("lastExecuted")
        waiver = row.get("waiver")
        if evidence is not None and waiver is not None:
            raise AccessibilityReleaseGateError(
                f"manual scenario '{scenario_id}' records both an execution and a waiver"
            )
        if evidence is not None:
            entry = require_object(evidence, f"manualScenarios[{scenario_id}].lastExecuted")
            stamped = require_string(entry.get("version"), "lastExecuted.version")
            require_string(entry.get("date"), "lastExecuted.date")
            if stamped != version_name:
                raise AccessibilityReleaseGateError(
                    f"manual scenario '{scenario_id}' was last executed against "
                    f"{stamped} but the build declares {version_name}; re-run it or "
                    "record a waiver for this version"
                )
            executed += 1
            continue
        if waiver is None:
            raise AccessibilityReleaseGateError(
                f"manual scenario '{scenario_id}' carries neither lastExecuted nor waiver"
            )
        entry = require_object(waiver, f"manualScenarios[{scenario_id}].waiver")
        stamped = require_string(entry.get("version"), "waiver.version")
        require_string(entry.get("owner"), "waiver.owner")
        reason = require_string(entry.get("reason"), "waiver.reason")
        if len(reason.split()) < 4:
            raise AccessibilityReleaseGateError(
                f"manual scenario '{scenario_id}' waiver reason is too short: {reason!r}"
            )
        if stamped != version_name:
            raise AccessibilityReleaseGateError(
                f"manual scenario '{scenario_id}' is waived for {stamped} but the build "
                f"declares {version_name}; a waiver does not carry across a release"
            )
        waived += 1

    return {"scenariosExecuted": executed, "scenariosWaived": waived}


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
        "ProductionRouteScenario",
        "ProductionRouteState",
        "renderScenario",
    ):
        if term not in test_text:
            raise AccessibilityReleaseGateError(f"{test_path} missing automated accessibility term: {term}")
    for term in FORBIDDEN_DIRECT_PRIMITIVES:
        if term in test_text:
            raise AccessibilityReleaseGateError(f"{test_path} must render production route states, not {term} directly")

    production_route_text = read_text(repo_root / PRODUCTION_ROUTE_SOURCE)
    executed_rows = [
        require_object(item, "automatedGate.executedSurfaces[]")
        for item in require_list(automated.get("executedSurfaces"), "automatedGate.executedSurfaces")
    ]
    executed_ids = {require_string(row.get("id"), "automatedGate.executedSurfaces[].id") for row in executed_rows}
    missing_executed = sorted(REQUIRED_EXECUTED_SURFACES - executed_ids)
    if missing_executed:
        raise AccessibilityReleaseGateError("automatedGate.executedSurfaces missing: " + ", ".join(missing_executed))
    for row in executed_rows:
        require_string(row.get("surface"), f"{row.get('id')}.surface")
        scenario = require_string(row.get("scenario"), f"{row.get('id')}.scenario")
        assertion_resource = require_string(
            row.get("assertionResource"), f"{row.get('id')}.assertionResource"
        )
        if not scenario.startswith("ProductionRouteScenario."):
            raise AccessibilityReleaseGateError(f"{row.get('id')}.scenario must name a ProductionRouteScenario")
        scenario_name = scenario.rsplit(".", 1)[-1]
        if scenario not in test_text:
            raise AccessibilityReleaseGateError(f"{test_path} missing executed scenario: {scenario}")
        if assertion_resource not in production_route_text:
            raise AccessibilityReleaseGateError(
                f"production route source missing executed assertion resource: {assertion_resource}"
            )
        if scenario_name not in production_route_text:
            raise AccessibilityReleaseGateError(f"production route source missing scenario: {scenario_name}")

    version_catalog = read_text(repo_root / "gradle/libs.versions.toml")
    app_gradle = read_text(repo_root / "app/build.gradle.kts")
    # This said "or newer" while matching one exact string, so it was a pin
    # wearing a floor's label and it failed on the first upgrade. The BOM version
    # is calendar-versioned (YYYY.MM.PP), which compares correctly as a tuple.
    bom_match = re.search(r'compose-bom\s*=\s*"(\d{4})\.(\d{2})\.(\d{2})"', version_catalog)
    if not bom_match:
        raise AccessibilityReleaseGateError(
            "version catalog declares no calendar-versioned compose-bom, so Compose UI "
            "test artifacts are not aligned by a BOM at all"
        )
    bom_version = tuple(int(part) for part in bom_match.groups())
    if bom_version < MINIMUM_COMPOSE_BOM:
        declared = ".".join(f"{part:02d}" if index else str(part) for index, part in enumerate(bom_version))
        raise AccessibilityReleaseGateError(
            f"compose-bom {declared} is older than the "
            f"{'.'.join(f'{p:02d}' if i else str(p) for i, p in enumerate(MINIMUM_COMPOSE_BOM))} "
            "floor the accessibility test artifacts need"
        )
    for coordinate in required_dependencies:
        group, name = coordinate.split(":", 1)
        if group not in version_catalog or name not in version_catalog:
            raise AccessibilityReleaseGateError(f"version catalog missing dependency coordinate: {coordinate}")
    for term in (
        "libs.compose.ui.test.junit4",
        "libs.compose.ui.test.junit4.accessibility",
        "libs.compose.ui.test.manifest",
        "testImplementation(platform(libs.compose.bom))",
        "androidTestImplementation(platform(libs.compose.bom))",
    ):
        if term not in app_gradle:
            raise AccessibilityReleaseGateError(f"app/build.gradle.kts missing accessibility test dependency: {term}")

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

    executed_ids = {
        require_string(row.get("id"), "executedSurfaces[].id") for row in executed_rows
    }
    version_name = declared_version_name(repo_root)
    coverage = validate_destination_coverage(repo_root, policy, executed_ids)
    currency = validate_scenario_currency(policy, scenario_rows, version_name)

    return {
        "status": "ok",
        "policyKind": "accessibilityReleaseGate",
        "scenarioCount": len(scenario_rows),
        "executedSurfaceCount": len(executed_rows),
        "sourceUrlCount": len(source_urls),
        "versionName": version_name,
        **coverage,
        **currency,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate Aura accessibility release gate.")
    parser.add_argument("--repo-root", default=".", type=Path)
    parser.add_argument("--policy", default="docs/qa/accessibility-release-gate.json")
    args = parser.parse_args()
    print(json.dumps(validate_accessibility_release_gate(args.repo_root.resolve(), args.policy), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
