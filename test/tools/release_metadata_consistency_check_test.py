from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.release_metadata_consistency_check import (
    ReleaseMetadataConsistencyError,
    validate_policy,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/distribution/release-metadata-consistency.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = set(policy["requiredEvidencePaths"])  # type: ignore[arg-type]
    paths.add(policy["docsPath"])  # type: ignore[arg-type]
    paths.add("docs/distribution/release-metadata-consistency.json")
    for relative_path in paths:
        source = REPO_ROOT / str(relative_path)
        target = destination / str(relative_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class ReleaseMetadataConsistencyCheckTest(unittest.TestCase):
    def test_live_release_metadata_consistency_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("com.freevibe", result["packageName"])
        self.assertEqual("6.31.1", result["versionName"])
        self.assertEqual(112, result["versionCode"])

    def test_rejects_version_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            policy = copy.deepcopy(live_policy())
            policy["versionCode"] = 999

            with self.assertRaises(ReleaseMetadataConsistencyError):
                validate_policy(repo, policy)

    def test_rejects_missing_readme_link(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            readme = repo / "README.md"
            readme.write_text(
                readme.read_text(encoding="utf-8").replace("docs/distribution/alt-store-metadata.md", ""),
                encoding="utf-8",
            )

            with self.assertRaises(ReleaseMetadataConsistencyError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_release_preflight_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            release_workflow = repo / ".github/workflows/release.yml"
            release_workflow.write_text(
                release_workflow.read_text(encoding="utf-8").replace("tools/alt_store_metadata_check.py", ""),
                encoding="utf-8",
            )

            with self.assertRaises(ReleaseMetadataConsistencyError):
                validate_policy(repo, live_policy())

    def test_rejects_privacy_url_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            privacy_link = repo / "docs/privacy/privacy-policy-link.json"
            data = json.loads(privacy_link.read_text(encoding="utf-8"))
            data["publicUrl"] = "https://example.invalid/privacy"
            privacy_link.write_text(json.dumps(data), encoding="utf-8")

            with self.assertRaises(ReleaseMetadataConsistencyError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
