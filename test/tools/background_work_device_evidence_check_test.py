from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.background_work_device_evidence_check import (
    BackgroundWorkDeviceEvidenceError,
    validate_policy,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/background-work-device-evidence.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    paths = {
        ".github/workflows/verify.yml",
        ".github/workflows/release.yml",
        "app/build.gradle.kts",
        "docs/background-work-device-evidence.json",
        "docs/background-work-scheduling-ledger.json",
    }
    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class BackgroundWorkDeviceEvidenceCheckTest(unittest.TestCase):
    def test_live_background_work_device_evidence_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual(5, result["scenarioCount"])
        self.assertIn("rotation_trigger_oneshot", result["coveredWorkNames"])

    def test_rejects_missing_required_scenario(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["scenarios"] = [
            row for row in policy["scenarios"] if row["id"] != "doze-standby"  # type: ignore[index]
        ]

        with self.assertRaises(BackgroundWorkDeviceEvidenceError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_unknown_work_name(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["scenarios"][0]["coversWorkNames"].append("unknown_work")  # type: ignore[index]

        with self.assertRaises(BackgroundWorkDeviceEvidenceError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_artifact_path_escape(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["scenarios"][0]["requiredArtifacts"][0] = "../jobscheduler.txt"  # type: ignore[index]

        with self.assertRaises(BackgroundWorkDeviceEvidenceError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_missing_workflow_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            workflow = repo / ".github/workflows/release.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace(
                    "tools/background_work_device_evidence_check.py",
                    "",
                ),
                encoding="utf-8",
            )

            with self.assertRaises(BackgroundWorkDeviceEvidenceError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
