from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.alt_store_metadata_check import AltStoreMetadataError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/distribution/alt-store-metadata.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = set(policy["requiredEvidencePaths"])  # type: ignore[arg-type]
    paths.update(
        {
            policy["docsPath"],  # type: ignore[arg-type]
            "docs/distribution/alt-store-metadata.json",
            "docs/legal/provider-runtime-controls.md",
            "docs/privacy/privacy-policy.md",
            "docs/security/network-endpoints.json",
        }
    )
    for relative_path in paths:
        source = REPO_ROOT / str(relative_path)
        target = destination / str(relative_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class AltStoreMetadataCheckTest(unittest.TestCase):
    def test_live_alt_store_metadata_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("com.freevibe", result["packageName"])
        self.assertEqual(14, result["permissionCount"])
        self.assertEqual(15, result["networkServiceCount"])

    def test_rejects_missing_manifest_permission_row(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            policy = copy.deepcopy(live_policy())
            policy["permissions"] = [  # type: ignore[index]
                row for row in policy["permissions"] if row["name"] != "android.permission.RECORD_AUDIO"  # type: ignore[index]
            ]

            with self.assertRaises(AltStoreMetadataError):
                validate_policy(repo, policy)

    def test_rejects_missing_network_service_row(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            policy = copy.deepcopy(live_policy())
            policy["networkServices"] = [  # type: ignore[index]
                row for row in policy["networkServices"] if row["id"] != "firebase-community"  # type: ignore[index]
            ]

            with self.assertRaises(AltStoreMetadataError):
                validate_policy(repo, policy)

    def test_rejects_missing_proprietary_dependency_marker(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            app_gradle = repo / "app/build.gradle.kts"
            app_gradle.write_text(
                app_gradle.read_text(encoding="utf-8").replace("com.google.firebase:firebase-storage", "firebase-storage-disabled"),
                encoding="utf-8",
            )

            with self.assertRaises(AltStoreMetadataError):
                validate_policy(repo, live_policy())

    def test_rejects_fdroid_mainline_ready_claim(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            policy = copy.deepcopy(live_policy())
            for row in policy["channels"]:  # type: ignore[index]
                if row["id"] == "fdroid-mainline":  # type: ignore[index]
                    row["status"] = "supported"  # type: ignore[index]

            with self.assertRaises(AltStoreMetadataError):
                validate_policy(repo, policy)


if __name__ == "__main__":
    unittest.main()
