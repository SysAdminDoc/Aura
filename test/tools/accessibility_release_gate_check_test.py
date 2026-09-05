from __future__ import annotations

import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.accessibility_release_gate_check import (
    AccessibilityReleaseGateError,
    validate_accessibility_release_gate,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


class AccessibilityReleaseGateCheckTest(unittest.TestCase):
    def test_live_accessibility_release_gate_passes(self) -> None:
        result = validate_accessibility_release_gate(REPO_ROOT, "docs/qa/accessibility-release-gate.json")

        self.assertEqual("ok", result["status"])
        self.assertEqual("accessibilityReleaseGate", result["policyKind"])
        self.assertGreaterEqual(result["scenarioCount"], 6)
        self.assertGreaterEqual(result["executedSurfaceCount"], 6)

    def test_rejects_missing_automated_api(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            test_path = repo / "app/src/androidTest/java/com/freevibe/ui/accessibility/AccessibilityReleaseGateTest.kt"
            test_path.write_text(test_path.read_text(encoding="utf-8").replace("enableAccessibilityChecks", ""), encoding="utf-8")

            with self.assertRaises(AccessibilityReleaseGateError):
                validate_accessibility_release_gate(repo, "docs/qa/accessibility-release-gate.json")

    def test_rejects_missing_manual_scenario(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy_path = repo / "docs/qa/accessibility-release-gate.json"
            policy = json.loads(policy_path.read_text(encoding="utf-8"))
            policy["manualScenarios"] = [
                row for row in policy["manualScenarios"] if row["id"] != "sounds-editor"
            ]
            policy_path.write_text(json.dumps(policy), encoding="utf-8")

            with self.assertRaises(AccessibilityReleaseGateError):
                validate_accessibility_release_gate(repo, "docs/qa/accessibility-release-gate.json")

    def test_rejects_compose_test_dependencies_without_supported_bom(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            catalog_path = repo / "gradle/libs.versions.toml"
            # Rewrite whatever version is declared rather than one literal: the
            # previous form replaced a string the catalog had already moved past,
            # so it silently stopped testing anything.
            catalog_path.write_text(
                re.sub(
                    r'compose-bom\s*=\s*"[^"]+"',
                    'compose-bom = "2024.12.01"',
                    catalog_path.read_text(encoding="utf-8"),
                ),
                encoding="utf-8",
            )

            with self.assertRaises(AccessibilityReleaseGateError):
                validate_accessibility_release_gate(repo, "docs/qa/accessibility-release-gate.json")

    def test_accepts_a_bom_newer_than_the_floor(self) -> None:
        """The floor is a floor. The old exact-match check failed every upgrade."""
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            catalog_path = repo / "gradle/libs.versions.toml"
            catalog_path.write_text(
                re.sub(
                    r'compose-bom\s*=\s*"[^"]+"',
                    'compose-bom = "2027.01.00"',
                    catalog_path.read_text(encoding="utf-8"),
                ),
                encoding="utf-8",
            )

            result = validate_accessibility_release_gate(
                repo, "docs/qa/accessibility-release-gate.json"
            )
            self.assertEqual("ok", result["status"])

    def test_rejects_a_catalog_with_no_compose_bom_at_all(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            catalog_path = repo / "gradle/libs.versions.toml"
            catalog_path.write_text(
                re.sub(
                    r'compose-bom\s*=\s*"[^"]+"\n',
                    "",
                    catalog_path.read_text(encoding="utf-8"),
                ),
                encoding="utf-8",
            )

            with self.assertRaises(AccessibilityReleaseGateError):
                validate_accessibility_release_gate(repo, "docs/qa/accessibility-release-gate.json")

    def test_rejects_missing_executed_surface(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy_path = repo / "docs/qa/accessibility-release-gate.json"
            policy = json.loads(policy_path.read_text(encoding="utf-8"))
            policy["automatedGate"]["executedSurfaces"] = [
                row for row in policy["automatedGate"]["executedSurfaces"] if row["id"] != "wallpaper-editor"
            ]
            policy_path.write_text(json.dumps(policy), encoding="utf-8")

            with self.assertRaises(AccessibilityReleaseGateError):
                validate_accessibility_release_gate(repo, "docs/qa/accessibility-release-gate.json")

    def _policy(self, repo: Path) -> dict:
        return json.loads(
            (repo / "docs/qa/accessibility-release-gate.json").read_text(encoding="utf-8")
        )

    def _write_policy(self, repo: Path, policy: dict) -> None:
        (repo / "docs/qa/accessibility-release-gate.json").write_text(
            json.dumps(policy, indent=2) + "\n", encoding="utf-8"
        )

    def test_every_screen_destination_is_covered_or_excused(self) -> None:
        result = validate_accessibility_release_gate(
            REPO_ROOT, "docs/qa/accessibility-release-gate.json"
        )

        # Derived, not restated: the point is that the count tracks Screen.kt.
        screen_source = (
            REPO_ROOT / "app/src/main/java/com/freevibe/ui/navigation/Screen.kt"
        ).read_text(encoding="utf-8")
        declared = len(set(re.findall(r"data object (\w+)\s*:\s*Screen\b", screen_source)))
        self.assertEqual(declared, result["destinationCount"])
        self.assertEqual(declared, result["executed"] + result["excused"])
        self.assertGreater(result["excused"], 0)

    def test_rejects_a_new_destination_with_no_coverage_entry(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            screen = repo / "app/src/main/java/com/freevibe/ui/navigation/Screen.kt"
            screen.write_text(
                screen.read_text(encoding="utf-8")
                + '\n    data object BrandNewThing : Screen(route = "brand_new")\n',
                encoding="utf-8",
            )

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(
                    repo, "docs/qa/accessibility-release-gate.json"
                )

            self.assertIn("BrandNewThing", str(ctx.exception))

    def test_rejects_coverage_for_a_destination_that_no_longer_exists(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy = self._policy(repo)
            policy["destinationCoverage"]["DeletedScreen"] = {
                "reason": "left behind after the destination was removed"
            }
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(
                    repo, "docs/qa/accessibility-release-gate.json"
                )

            self.assertIn("DeletedScreen", str(ctx.exception))

    def test_rejects_coverage_naming_a_surface_that_is_not_executed(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy = self._policy(repo)
            policy["destinationCoverage"]["Wallpapers"] = {
                "executedSurface": "not-a-real-surface"
            }
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(
                    repo, "docs/qa/accessibility-release-gate.json"
                )

            self.assertIn("not-a-real-surface", str(ctx.exception))

    def test_rejects_a_hand_wave_instead_of_a_reason(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy = self._policy(repo)
            policy["destinationCoverage"]["Licenses"] = {"reason": "later"}
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(
                    repo, "docs/qa/accessibility-release-gate.json"
                )

            self.assertIn("too short to be a reason", str(ctx.exception))

    def test_rejects_a_destination_claiming_both_execution_and_a_reason(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy = self._policy(repo)
            policy["destinationCoverage"]["Wallpapers"] = {
                "executedSurface": "wallpapers-grid",
                "reason": "also excused for some reason",
            }
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(
                    repo, "docs/qa/accessibility-release-gate.json"
                )

            self.assertIn("not both", str(ctx.exception))

    def test_rejects_a_waiver_from_an_earlier_release(self) -> None:
        # Manual evidence that does not name the version it applies to is
        # indistinguishable from evidence nobody gathered, so a waiver expires
        # at the next bump instead of carrying forward.
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy = self._policy(repo)
            policy["manualScenarios"][0]["waiver"]["version"] = "0.0.1"
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(
                    repo, "docs/qa/accessibility-release-gate.json"
                )

            message = str(ctx.exception)
            self.assertIn("waived for 0.0.1", message)
            self.assertIn("does not carry across a release", message)

    def test_rejects_a_scenario_with_neither_execution_nor_waiver(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy = self._policy(repo)
            policy["manualScenarios"][0].pop("waiver")
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(
                    repo, "docs/qa/accessibility-release-gate.json"
                )

            self.assertIn("neither lastExecuted nor waiver", str(ctx.exception))

    def test_accepts_a_scenario_executed_against_the_current_version(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            policy = self._policy(repo)
            version = re.search(
                r'versionName\s*=\s*"([^"]+)"',
                (repo / "app/build.gradle.kts").read_text(encoding="utf-8"),
            ).group(1)
            policy["manualScenarios"][0].pop("waiver")
            policy["manualScenarios"][0]["lastExecuted"] = {
                "version": version,
                "date": "2026-09-05",
            }
            self._write_policy(repo, policy)

            result = validate_accessibility_release_gate(
                repo, "docs/qa/accessibility-release-gate.json"
            )

            self.assertEqual(1, result["scenariosExecuted"])
            self.assertEqual(5, result["scenariosWaived"])

    def test_rejects_direct_primitive_only_test(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            test_path = repo / "app/src/androidTest/java/com/freevibe/ui/accessibility/AccessibilityReleaseGateTest.kt"
            test_path.write_text(
                test_path.read_text(encoding="utf-8") + "\n@Suppress(\"unused\") fun primitiveOnly() { SettingsToggle() }\n",
                encoding="utf-8",
            )

            with self.assertRaises(AccessibilityReleaseGateError):
                validate_accessibility_release_gate(repo, "docs/qa/accessibility-release-gate.json")


def copy_required_tree(destination: Path) -> Path:
    paths = [
        "app/build.gradle.kts",
        "gradle/libs.versions.toml",
        "docs/qa/accessibility-release-gate.json",
        "app/src/main/java/com/freevibe/ui/qa/ProductionRouteState.kt",
        "app/src/androidTest/java/com/freevibe/ui/accessibility/AccessibilityReleaseGateTest.kt",
        # The destination list is derived from Screen.kt, so a fixture without
        # it cannot check coverage and would pass for the wrong reason.
        "app/src/main/java/com/freevibe/ui/navigation/Screen.kt",
    ]
    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    return destination


if __name__ == "__main__":
    unittest.main()
