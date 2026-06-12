from __future__ import annotations

import json
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


def copy_required_tree(destination: Path) -> Path:
    paths = [
        ".github/workflows/verify.yml",
        "app/build.gradle.kts",
        "gradle/libs.versions.toml",
        "docs/qa/accessibility-release-gate.json",
        "app/src/androidTest/java/com/freevibe/ui/accessibility/AccessibilityReleaseGateTest.kt",
    ]
    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    return destination


if __name__ == "__main__":
    unittest.main()
