from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.store_asset_pipeline_check import StoreAssetPipelineError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/distribution/store-assets.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    paths = {
        "app/build.gradle.kts",
        "docs/distribution/store-assets.md",
        "docs/distribution/store-assets.json",
        "docs/distribution/release-dry-run.md",
        "docs/distribution/release-signing.md",
        "docs/distribution/release-metadata-consistency.md",
        "tools/store_metadata_preflight.py",
        ".github/workflows/verify.yml",
        ".github/workflows/release.yml",
    }
    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class StoreAssetPipelineCheckTest(unittest.TestCase):
    def test_live_store_asset_pipeline_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("com.freevibe", result["packageName"])
        self.assertEqual("capturePending", result["decision"])
        self.assertEqual(4, result["plannedShotCount"])

    def test_rejects_status_drift(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["status"] = "ready"

        with self.assertRaises(StoreAssetPipelineError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_missing_required_shot_id(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["plannedShots"] = [
            shot for shot in policy["plannedShots"] if shot["id"] != "sounds-editor"  # type: ignore[index]
        ]

        with self.assertRaises(StoreAssetPipelineError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_forbidden_shot_text(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["plannedShots"][0]["coverage"] = "Download now from the wallpaper grid."  # type: ignore[index]

        with self.assertRaises(StoreAssetPipelineError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_missing_workflow_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            workflow = repo / ".github/workflows/release.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace("tools/store_asset_pipeline_check.py", ""),
                encoding="utf-8",
            )

            with self.assertRaises(StoreAssetPipelineError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
