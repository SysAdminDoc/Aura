from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.sbom_readiness_check import SbomReadinessError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/distribution/sbom-readiness.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = set(policy["currentEvidencePaths"])  # type: ignore[arg-type]
    paths.add(policy["docsPath"])  # type: ignore[arg-type]
    paths.add("app/build.gradle.kts")
    paths.add("docs/distribution/sbom-readiness.json")
    paths.add("docs/distribution/supply-chain.md")
    paths.add("docs/distribution/release-dry-run.md")
    paths.add("docs/distribution/release-signing.md")
    for relative_path in paths:
        source = REPO_ROOT / str(relative_path)
        target = destination / str(relative_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class SbomReadinessCheckTest(unittest.TestCase):
    def test_live_sbom_readiness_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("com.freevibe", result["packageName"])
        self.assertEqual("deferredUntilN1ToolchainUpgrade", result["decision"])
        self.assertEqual(3, result["futureSbomArtifactCount"])

    def test_rejects_status_drift(self) -> None:
        policy = copy.deepcopy(live_policy())
        policy["status"] = "ready"

        with self.assertRaises(SbomReadinessError):
            validate_policy(REPO_ROOT, policy)

    def test_rejects_missing_future_artifact_doc_row(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            docs = repo / "docs/distribution/sbom-readiness.md"
            docs.write_text(docs.read_text(encoding="utf-8").replace("SBOM.spdx.json", ""), encoding="utf-8")

            with self.assertRaises(SbomReadinessError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_verify_workflow_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            workflow = repo / ".github/workflows/verify.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace("tools/sbom_readiness_check.py", ""),
                encoding="utf-8",
            )

            with self.assertRaises(SbomReadinessError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_current_evidence_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            (repo / "docs/legal/native-compliance.lock.json").unlink()

            with self.assertRaises(SbomReadinessError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
