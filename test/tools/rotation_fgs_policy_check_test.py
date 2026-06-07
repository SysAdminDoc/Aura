from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.rotation_fgs_policy_check import RotationFgsPolicyError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/rotation-trigger-fgs-policy.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = {
        "app/src/main/AndroidManifest.xml",
        ".github/workflows/verify.yml",
        ".github/workflows/release.yml",
        "docs/distribution/release-dry-run.md",
        "docs/distribution/release-signing.md",
        "docs/distribution/release-metadata-consistency.md",
        "docs/rotation-trigger-fgs-policy.md",
        "docs/rotation-trigger-fgs-policy.json",
    }
    paths.add(str(policy["serviceSource"]))  # type: ignore[index]
    paths.add(str(policy["playPacketDocs"]))  # type: ignore[index]
    paths.add(str(policy["playPacketPolicy"]))  # type: ignore[index]
    paths.update(str(path) for path in policy["settingsSources"])  # type: ignore[index]

    play_packet = json.loads((REPO_ROOT / str(policy["playPacketPolicy"])).read_text(encoding="utf-8"))  # type: ignore[index]
    for row in play_packet["declarations"]["foregroundServices"]:
        for ref in row["evidenceRefs"]:
            paths.add(str(ref))
    for action in play_packet["ownerActions"]:
        for ref in action["evidenceRefs"]:
            paths.add(str(ref))

    for relative_path in paths:
        source = REPO_ROOT / relative_path
        target = destination / relative_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


class RotationFgsPolicyCheckTest(unittest.TestCase):
    def test_live_rotation_fgs_policy_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual(".service.RotationTriggerService", result["serviceName"])
        self.assertEqual("specialUse", result["foregroundServiceType"])

    def test_rejects_missing_special_use_permission(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            manifest = repo / "app/src/main/AndroidManifest.xml"
            manifest.write_text(
                manifest.read_text(encoding="utf-8").replace(
                    '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />\n',
                    "",
                ),
                encoding="utf-8",
            )

            with self.assertRaises(RotationFgsPolicyError):
                validate_policy(repo, live_policy())

    def test_rejects_service_type_drift(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            manifest = repo / "app/src/main/AndroidManifest.xml"
            manifest.write_text(
                manifest.read_text(encoding="utf-8").replace(
                    'android:foregroundServiceType="specialUse"',
                    'android:foregroundServiceType="dataSync"',
                ),
                encoding="utf-8",
            )

            with self.assertRaises(RotationFgsPolicyError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_source_safeguard(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            source = repo / str(live_policy()["serviceSource"])
            source.write_text(
                source.read_text(encoding="utf-8").replace("ExistingWorkPolicy.KEEP", "ExistingWorkPolicy.REPLACE"),
                encoding="utf-8",
            )

            with self.assertRaises(RotationFgsPolicyError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_play_owner_action(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            play_path = repo / str(live_policy()["playPacketPolicy"])
            play_packet = json.loads(play_path.read_text(encoding="utf-8"))
            play_packet["ownerActions"] = [
                action
                for action in play_packet["ownerActions"]
                if action["id"] != "capture-foreground-service-declaration-evidence"
            ]
            play_path.write_text(json.dumps(play_packet), encoding="utf-8")

            with self.assertRaises(RotationFgsPolicyError):
                validate_policy(repo, live_policy())

    def test_rejects_missing_workflow_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            copy_required_tree(repo)
            workflow = repo / ".github/workflows/verify.yml"
            workflow.write_text(
                workflow.read_text(encoding="utf-8").replace("tools/rotation_fgs_policy_check.py", ""),
                encoding="utf-8",
            )

            with self.assertRaises(RotationFgsPolicyError):
                validate_policy(repo, live_policy())


if __name__ == "__main__":
    unittest.main()
