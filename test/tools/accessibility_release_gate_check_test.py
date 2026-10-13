from __future__ import annotations

import json
import re
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.accessibility_release_gate_check import (
    REQUIRED_PRIMARY_DESTINATIONS,
    AccessibilityReleaseGateError,
    validate_accessibility_release_gate,
)


REPO_ROOT = Path(__file__).resolve().parents[2]
POLICY = "docs/qa/accessibility-release-gate.json"
TRUNCATED_BASELINE = "app/src/test/screenshots/settings_provider_disabled_light_font2_0.png"


class AccessibilityReleaseGateCheckTest(unittest.TestCase):
    def test_live_gate_blocks_release_until_primary_evidence_exists(self) -> None:
        # The live policy is honest about the state of 6.46.1: one reviewed
        # 200 percent baseline, and it clips. The gate has to say so.
        with self.assertRaises(AccessibilityReleaseGateError) as ctx:
            validate_accessibility_release_gate(REPO_ROOT, POLICY)

        message = str(ctx.exception)
        self.assertIn("Settings/light: noClippedText is fail", message)
        self.assertIn("no evidence for", message)
        self.assertIn("Onboarding/dark", message)

    def test_passes_with_complete_primary_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            complete_primary_matrix(repo)

            result = validate_accessibility_release_gate(repo, POLICY)

        self.assertEqual("ok", result["status"])
        self.assertEqual("accessibilityReleaseGate", result["policyKind"])
        self.assertGreaterEqual(result["scenarioCount"], 5)
        self.assertGreaterEqual(result["executedSurfaceCount"], 6)
        self.assertEqual(len(REQUIRED_PRIMARY_DESTINATIONS) * 2, result["primaryCells"])

    def test_the_reviewed_truncated_baseline_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            copy_file(TRUNCATED_BASELINE, repo)
            complete_primary_matrix(repo, keep_live_rows=True)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(repo, POLICY)

        message = str(ctx.exception)
        self.assertIn("Settings/light: noClippedText is fail", message)
        self.assertNotIn("no evidence for", message)
        self.assertNotIn("doesn't exist", message)

    def test_removing_any_required_primary_destination_fails(self) -> None:
        for destination in sorted(REQUIRED_PRIMARY_DESTINATIONS):
            with self.subTest(destination=destination), tempfile.TemporaryDirectory() as tmpdir:
                repo = copy_required_tree(Path(tmpdir))
                complete_primary_matrix(repo)
                policy = self._policy(repo)
                matrix = policy["primaryMatrix"]
                matrix["destinations"].remove(destination)
                matrix["evidence"] = [row for row in matrix["evidence"] if row["destination"] != destination]
                self._write_policy(repo, policy)

                with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                    validate_accessibility_release_gate(repo, POLICY)

                self.assertIn(destination, str(ctx.exception))

    def test_dropping_a_theme_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            complete_primary_matrix(repo)
            policy = self._policy(repo)
            matrix = policy["primaryMatrix"]
            matrix["themes"] = ["dark"]
            matrix["evidence"] = [row for row in matrix["evidence"] if row["theme"] == "dark"]
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(repo, POLICY)

            self.assertIn("themes is missing: light", str(ctx.exception))

    def test_a_primary_cell_cannot_be_waived(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            complete_primary_matrix(repo)
            policy = self._policy(repo)
            policy["primaryMatrix"]["evidence"][0]["waiver"] = {
                "owner": "SysAdminDoc",
                "reason": "could not reach a phone this time",
            }
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(repo, POLICY)

            self.assertIn("can't be waived", str(ctx.exception))

    def test_a_missing_cell_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            complete_primary_matrix(repo)
            policy = self._policy(repo)
            policy["primaryMatrix"]["evidence"] = [
                row
                for row in policy["primaryMatrix"]["evidence"]
                if (row["destination"], row["theme"]) != ("Downloads", "light")
            ]
            self._write_policy(repo, policy)

            with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                validate_accessibility_release_gate(repo, POLICY)

            self.assertIn("no evidence for Downloads/light", str(ctx.exception))

    def test_stale_low_scale_or_unbacked_evidence_fails(self) -> None:
        cases = {
            "version": ("0.0.1", "evidence is for 0.0.1"),
            "fontScale": (1.3, "below 2.0"),
            "source": ("docs/qa/evidence/never-written.png", "doesn't exist"),
        }
        for field, (value, expected) in cases.items():
            with self.subTest(field=field), tempfile.TemporaryDirectory() as tmpdir:
                repo = copy_required_tree(Path(tmpdir))
                complete_primary_matrix(repo)
                policy = self._policy(repo)
                policy["primaryMatrix"]["evidence"][0][field] = value
                self._write_policy(repo, policy)

                with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                    validate_accessibility_release_gate(repo, POLICY)

                self.assertIn(expected, str(ctx.exception))

    def test_every_required_check_must_pass(self) -> None:
        for check in ("semantics", "contrast", "touchTargets", "noClippedText"):
            with self.subTest(check=check), tempfile.TemporaryDirectory() as tmpdir:
                repo = copy_required_tree(Path(tmpdir))
                complete_primary_matrix(repo)
                policy = self._policy(repo)
                del policy["primaryMatrix"]["evidence"][0]["results"][check]
                self._write_policy(repo, policy)

                with self.assertRaises(AccessibilityReleaseGateError) as ctx:
                    validate_accessibility_release_gate(repo, POLICY)

                self.assertIn(f"{check} is not recorded", str(ctx.exception))

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

            complete_primary_matrix(repo)
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
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = copy_required_tree(Path(tmpdir))
            complete_primary_matrix(repo)
            result = validate_accessibility_release_gate(repo, POLICY)

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
            complete_primary_matrix(repo)

            result = validate_accessibility_release_gate(
                repo, "docs/qa/accessibility-release-gate.json"
            )

            self.assertEqual(1, result["scenariosExecuted"])
            self.assertEqual(len(policy["manualScenarios"]) - 1, result["scenariosWaived"])

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


def copy_file(relative_path: str, destination: Path) -> None:
    target = destination / relative_path
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(REPO_ROOT / relative_path, target)


def complete_primary_matrix(repo: Path, keep_live_rows: bool = False) -> None:
    """Fill every primary cell with passing evidence for the declared version.

    With keep_live_rows, cells the live policy already records keep their real
    results, so a test can show that real evidence fails on its own merits.
    """
    policy_path = repo / POLICY
    policy = json.loads(policy_path.read_text(encoding="utf-8"))
    version = re.search(
        r'versionName\s*=\s*"([^"]+)"',
        (repo / "app/build.gradle.kts").read_text(encoding="utf-8"),
    ).group(1)
    evidence_file = repo / "docs/qa/evidence/primary-matrix-fixture.txt"
    evidence_file.parent.mkdir(parents=True, exist_ok=True)
    evidence_file.write_text("fixture evidence\n", encoding="utf-8")

    matrix = policy["primaryMatrix"]
    live = {(row["destination"], row["theme"]): row for row in matrix["evidence"]} if keep_live_rows else {}
    rows = []
    for destination in matrix["destinations"]:
        for theme in matrix["themes"]:
            if (destination, theme) in live:
                rows.append(live[(destination, theme)])
                continue
            rows.append(
                {
                    "destination": destination,
                    "theme": theme,
                    "fontScale": matrix["fontScale"],
                    "version": version,
                    "date": "2026-10-13",
                    "source": "docs/qa/evidence/primary-matrix-fixture.txt",
                    "results": {check: "pass" for check in matrix["checks"]},
                }
            )
    matrix["evidence"] = rows
    policy_path.write_text(json.dumps(policy, indent=2) + "\n", encoding="utf-8")


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
