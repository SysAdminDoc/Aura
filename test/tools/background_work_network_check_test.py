from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.background_work_network_check import BackgroundWorkNetworkError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/background-work-network-posture.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = {
        ".github/workflows/verify.yml",
        ".github/workflows/release.yml",
        "docs/background-work-network-posture.md",
        "docs/background-work-network-posture.json",
        "docs/background-work-scheduling-ledger.json",
        "docs/distribution/release-dry-run.md",
        "docs/distribution/release-signing.md",
        "docs/distribution/release-metadata-consistency.md",
        "docs/distribution/supply-chain.md",
    }
    for row in policy["postureRows"]:  # type: ignore[index]
        paths.add(str(row["sourcePath"]))
        paths.add(str(row["settingsSourcePath"]))

    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class BackgroundWorkNetworkCheckTest(unittest.TestCase):
    def test_live_background_work_network_posture_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual(5, result["postureRowCount"])
        self.assertIn("unmetered", result["networkPostures"])

    def test_rejects_missing_scheduling_row(self) -> None:
        policy = live_policy()
        policy["postureRows"] = [
            row for row in policy["postureRows"]  # type: ignore[index]
            if row["id"] != "aura-originals-one-shot"
        ]

        with self.assertRaises(BackgroundWorkNetworkError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_unique_work_mismatch(self) -> None:
        policy = live_policy()
        policy["postureRows"][0]["uniqueWorkName"] = "wrong_name"  # type: ignore[index]

        with self.assertRaises(BackgroundWorkNetworkError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_source_network_drift(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            source = repo / "app/src/main/java/com/freevibe/service/AuraOriginalsDownloader.kt"
            source.write_text(
                source.read_text(encoding="utf-8").replace(
                    "setRequiredNetworkType(NetworkType.UNMETERED)",
                    "setRequiredNetworkType(NetworkType.CONNECTED)",
                ),
                encoding="utf-8",
            )

            with self.assertRaises(BackgroundWorkNetworkError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_data_saver_policy(self) -> None:
        policy = live_policy()
        policy["postureRows"][0]["dataSaverPolicy"] = ""  # type: ignore[index]

        with self.assertRaises(BackgroundWorkNetworkError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_missing_workflow_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            workflow = repo / ".github/workflows/release.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace("tools/background_work_network_check.py", ""),
                encoding="utf-8",
            )

            with self.assertRaises(BackgroundWorkNetworkError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
