from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.background_work_scheduling_check import BackgroundWorkSchedulingError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/background-work-scheduling-ledger.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = {
        ".github/workflows/verify.yml",
        ".github/workflows/release.yml",
        "docs/background-work-scheduling-ledger.md",
        "docs/background-work-scheduling-ledger.json",
        "docs/distribution/release-dry-run.md",
        "docs/distribution/release-signing.md",
        "docs/distribution/release-metadata-consistency.md",
        "docs/distribution/supply-chain.md",
    }
    for row in policy["workItems"]:  # type: ignore[index]
        paths.add(str(row["sourcePath"]))
        paths.update(str(path) for path in row["settingsSources"])

    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class BackgroundWorkSchedulingCheckTest(unittest.TestCase):
    def test_live_background_work_ledger_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual(5, result["workItemCount"])
        self.assertIn("rotation_trigger_oneshot", result["uniqueWorkNames"])

    def test_rejects_missing_required_work_item(self) -> None:
        policy = live_policy()
        policy["workItems"] = [
            row for row in policy["workItems"]  # type: ignore[index]
            if row["id"] != "weather-update-periodic"
        ]

        with self.assertRaises(BackgroundWorkSchedulingError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_source_policy_drift(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            source = repo / "app/src/main/java/com/freevibe/service/AutoWallpaperWorker.kt"
            source.write_text(
                source.read_text(encoding="utf-8").replace(
                    "ExistingPeriodicWorkPolicy.UPDATE",
                    "ExistingPeriodicWorkPolicy.KEEP",
                ),
                encoding="utf-8",
            )

            with self.assertRaises(BackgroundWorkSchedulingError):
                validate_policy(repo, live_policy())

    def test_rejects_empty_deferral_reason_list(self) -> None:
        policy = live_policy()
        first_row = policy["workItems"][0]  # type: ignore[index]
        first_row["deferralReasons"] = ["network unavailable"]

        with self.assertRaises(BackgroundWorkSchedulingError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_missing_doc_source_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            docs = repo / "docs/background-work-scheduling-ledger.md"
            docs.write_text(
                docs.read_text(encoding="utf-8").replace(
                    "https://developer.android.com/training/monitoring-device-state/doze-standby",
                    "",
                ),
                encoding="utf-8",
            )

            with self.assertRaises(BackgroundWorkSchedulingError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_workflow_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            workflow = repo / ".github/workflows/verify.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace("tools/background_work_scheduling_check.py", ""),
                encoding="utf-8",
            )

            with self.assertRaises(BackgroundWorkSchedulingError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
