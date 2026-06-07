from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.rotation_boot_permission_check import RotationBootPermissionError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/rotation-trigger-boot-behavior.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = {
        "app/src/main/AndroidManifest.xml",
        ".github/workflows/verify.yml",
        ".github/workflows/release.yml",
        "docs/distribution/release-dry-run.md",
        "docs/distribution/release-signing.md",
        "docs/distribution/release-metadata-consistency.md",
        "docs/rotation-trigger-boot-behavior.json",
    }
    paths.update(str(path) for path in policy["requiredDocs"])  # type: ignore[index]
    source_root = REPO_ROOT / "app/src/main/java"
    for source in source_root.rglob("*.kt"):
        paths.add(str(source.relative_to(REPO_ROOT)).replace("\\", "/"))
    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class RotationBootPermissionCheckTest(unittest.TestCase):
    def test_live_rotation_boot_permission_policy_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("permissionRemoved", result["decision"])
        self.assertEqual("android.permission.RECEIVE_BOOT_COMPLETED", result["removedPermission"])

    def test_rejects_permission_returning_to_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            manifest = repo / "app/src/main/AndroidManifest.xml"
            manifest.write_text(
                manifest.read_text(encoding="utf-8").replace(
                    "<uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />",
                    "<uses-permission android:name=\"android.permission.RECEIVE_BOOT_COMPLETED\" />\n"
                    "    <uses-permission android:name=\"android.permission.FOREGROUND_SERVICE\" />",
                ),
                encoding="utf-8",
            )

            with self.assertRaises(RotationBootPermissionError):
                validate_policy(repo, live_policy())

    def test_rejects_boot_completed_source_term(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            source = repo / "app/src/main/java/com/freevibe/BootReceiver.kt"
            source.parent.mkdir(parents=True, exist_ok=True)
            source.write_text("val action = Intent.ACTION_BOOT_COMPLETED\n", encoding="utf-8")

            with self.assertRaises(RotationBootPermissionError):
                validate_policy(repo, live_policy())

    def test_rejects_stale_required_doc(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            stale_doc = repo / "docs/privacy/data-safety.md"
            stale_doc.write_text(
                stale_doc.read_text(encoding="utf-8")
                + "\n`android.permission.RECEIVE_BOOT_COMPLETED` boot scheduling.\n",
                encoding="utf-8",
            )

            with self.assertRaises(RotationBootPermissionError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_workflow_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            workflow = repo / ".github/workflows/verify.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace("tools/rotation_boot_permission_check.py", ""),
                encoding="utf-8",
            )

            with self.assertRaises(RotationBootPermissionError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
