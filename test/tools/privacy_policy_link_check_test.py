from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.privacy_policy_link_check import PrivacyPolicyLinkError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]
PUBLIC_URL = "https://github.com/SysAdminDoc/Aura/blob/main/docs/privacy/privacy-policy.md"


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/privacy/privacy-policy-link.json").read_text(encoding="utf-8"))


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def minimal_policy() -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "policyKind": "privacyPolicyLink",
        "publicUrl": PUBLIC_URL,
        "policyDoc": "docs/privacy/privacy-policy.md",
        "settingsScreen": "SettingsScreen.kt",
        "fastlaneFullDescription": "fastlane/metadata/android/en-US/full_description.txt",
        "readme": "README.md",
        "verifyWorkflow": ".github/workflows/verify.yml",
        "releaseWorkflow": ".github/workflows/release.yml",
        "releaseDryRunDoc": "docs/distribution/release-dry-run.md",
        "requiredPolicyHeadings": [
            "## Account Model",
            "## Data Stored On The Device",
            "## Community Data",
            "## Account Deletion",
            "## Diagnostics",
            "## Third-Party Services",
            "## Contact",
        ],
        "requiredPolicyTerms": [
            "No ads",
            "cross-app tracking",
            "API keys entered by the user",
            "anonymous Firebase identity",
            "Generated wallpaper prompts",
            "Stability",
            "not upload itself automatically",
            "hosted web deletion request URL",
        ],
        "sourceUrls": [
            "https://support.google.com/googleplay/android-developer/answer/10144311",
            "https://support.google.com/googleplay/android-developer/answer/10787469",
        ],
    }


def seed_repo(repo: Path) -> Path:
    policy_text = """
# Aura Privacy Policy

Aura has No ads and no cross-app tracking.

## Account Model
Uses an anonymous Firebase identity for community actions.

## Data Stored On The Device
API keys entered by the user stay local.

## Community Data
Community upload metadata can be public.

## Account Deletion
The hosted web deletion request URL is tracked before release.

## Diagnostics
The diagnostics bundle does not upload itself automatically.

## Third-Party Services
Generated wallpaper prompts can be sent to Stability.

## Contact
Use the project support channel.
""".strip()
    write(repo / "docs/privacy/privacy-policy.md", policy_text)
    write(repo / "SettingsScreen.kt", f'const val URL = "{PUBLIC_URL}"\nText("Privacy policy")\n')
    write(repo / "fastlane/metadata/android/en-US/full_description.txt", f"Privacy policy: {PUBLIC_URL}\n")
    write(repo / "README.md", f"[Privacy policy]({PUBLIC_URL})\n")
    write(repo / ".github/workflows/verify.yml", "python3 tools/privacy_policy_link_check.py\n")
    write(repo / ".github/workflows/release.yml", "python3 tools/privacy_policy_link_check.py\n")
    write(repo / "docs/distribution/release-dry-run.md", "python3 tools/privacy_policy_link_check.py\n")
    return repo


class PrivacyPolicyLinkCheckTest(unittest.TestCase):
    def test_live_privacy_policy_link_contract_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("ok", result["releaseGate"])
        self.assertEqual("ok", result["inAppLink"])

    def test_rejects_non_https_public_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["publicUrl"] = "http://example.com/privacy"

            with self.assertRaises(PrivacyPolicyLinkError):
                validate_policy(repo, policy)

    def test_rejects_settings_screen_without_privacy_link(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            write(repo / "SettingsScreen.kt", 'Text("Open source licenses")\n')

            with self.assertRaises(PrivacyPolicyLinkError):
                validate_policy(repo, policy)

    def test_rejects_fastlane_description_without_privacy_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            write(repo / "fastlane/metadata/android/en-US/full_description.txt", "Aura metadata.\n")

            with self.assertRaises(PrivacyPolicyLinkError):
                validate_policy(repo, policy)

    def test_rejects_missing_policy_term(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            write(repo / "docs/privacy/privacy-policy.md", "# Aura Privacy Policy\n\n## Account Model\n")

            with self.assertRaises(PrivacyPolicyLinkError):
                validate_policy(repo, policy)

    def test_rejects_release_workflow_without_gate(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            write(repo / ".github/workflows/release.yml", "python3 tools/store_metadata_preflight.py\n")

            with self.assertRaises(PrivacyPolicyLinkError):
                validate_policy(repo, policy)

    def test_rejects_non_google_source_url(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = copy.deepcopy(minimal_policy())
            policy["sourceUrls"] = ["https://example.com/privacy"]

            with self.assertRaises(PrivacyPolicyLinkError):
                validate_policy(repo, policy)


if __name__ == "__main__":
    unittest.main()
