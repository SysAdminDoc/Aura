from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.privacy_data_safety_check import PrivacyDataSafetyError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/privacy/data-safety.json").read_text(encoding="utf-8"))


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def minimal_permission(name: str, max_sdk: int | None = None) -> dict[str, object]:
    row: dict[str, object] = {
        "name": name,
        "category": "test",
        "purpose": "Test purpose.",
        "userAction": "User action.",
        "dataTypes": ["App interactions"],
        "collectionStatus": "localOnly",
        "sharingStatus": "notShared",
        "retention": "No retained data.",
        "deletionPath": "Clear app data.",
        "denialBehavior": "Feature unavailable.",
        "playDeclaration": "Reviewed.",
    }
    if max_sdk is not None:
        row["maxSdkVersion"] = max_sdk
    return row


def minimal_policy() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "policyKind": "privacyDataSafetyMatrix",
        "manifest": "app/src/main/AndroidManifest.xml",
        "docsPath": "docs/privacy/data-safety.md",
        "privacyPolicy": "docs/privacy/privacy-policy.md",
        "permissions": [
            minimal_permission("android.permission.INTERNET"),
            minimal_permission("android.permission.WRITE_EXTERNAL_STORAGE", 28),
        ],
        "sourceUrls": ["https://support.google.com/googleplay/android-developer/answer/10787469"],
    }


def seed_repo(repo: Path) -> Path:
    write(
        repo / "app/src/main/AndroidManifest.xml",
        """
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
</manifest>
""".strip(),
    )
    write(
        repo / "docs/privacy/data-safety.md",
        "`android.permission.INTERNET`\n`android.permission.WRITE_EXTERNAL_STORAGE`\n",
    )
    write(
        repo / "docs/privacy/privacy-policy.md",
        "No ads. No cross-app tracking. Anonymous Firebase identity. Generated wallpaper prompts.\n",
    )
    return repo


class PrivacyDataSafetyCheckTest(unittest.TestCase):
    def test_live_privacy_data_safety_matrix_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual(result["manifestPermissionCount"], result["matrixPermissionCount"])
        self.assertGreaterEqual(result["sensitiveOrSharedRowCount"], 1)

    def test_rejects_missing_permission_row(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["permissions"] = policy["permissions"][:-1]  # type: ignore[index]

            with self.assertRaises(PrivacyDataSafetyError):
                validate_policy(repo, policy)

    def test_rejects_extra_permission_row(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["permissions"].append(minimal_permission("android.permission.CAMERA"))  # type: ignore[union-attr]

            with self.assertRaises(PrivacyDataSafetyError):
                validate_policy(repo, policy)

    def test_rejects_max_sdk_drift(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["permissions"][1]["maxSdkVersion"] = 29  # type: ignore[index]

            with self.assertRaises(PrivacyDataSafetyError):
                validate_policy(repo, policy)

    def test_rejects_missing_required_field(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["permissions"][0].pop("deletionPath")  # type: ignore[index]

            with self.assertRaises(PrivacyDataSafetyError):
                validate_policy(repo, policy)

    def test_rejects_docs_without_permission_row(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            write(repo / "docs/privacy/data-safety.md", "`android.permission.INTERNET`\n")

            with self.assertRaises(PrivacyDataSafetyError):
                validate_policy(repo, policy)

    def test_rejects_non_https_source_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = copy.deepcopy(minimal_policy())
            policy["sourceUrls"] = ["http://example.com"]

            with self.assertRaises(PrivacyDataSafetyError):
                validate_policy(repo, policy)


if __name__ == "__main__":
    unittest.main()
