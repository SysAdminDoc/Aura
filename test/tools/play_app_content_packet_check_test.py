from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.play_app_content_packet_check import PlayAppContentPacketError, validate_policy


REPO_ROOT = Path(__file__).resolve().parents[2]


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/distribution/play-app-content.json").read_text(encoding="utf-8"))


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def minimal_policy() -> dict[str, object]:
    source_urls = [
        "https://support.google.com/googleplay/android-developer/answer/9859455",
        "https://support.google.com/googleplay/android-developer/answer/9867159",
    ]
    return {
        "schemaVersion": 1,
        "policyKind": "playAppContentPacket",
        "packageName": "com.freevibe",
        "playSubmissionStatus": "ownerActionRequired",
        "docsPath": "docs/distribution/play-app-content.md",
        "requiredEvidencePaths": [
            "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml",
            "fastlane/metadata/android/en-US/full_description.txt",
            "docs/privacy/privacy-policy.md",
            "docs/privacy/data-safety.json",
            "docs/privacy/data-safety.md",
            "docs/privacy/ai-generation.md",
            "docs/support/community-reporting.md",
            "docs/community-block-user-policy.md",
            "docs/community-upload-deletion.md",
            "docs/support/community-account-deletion.md",
            "docs/support/community-account-deletion-web-url.json",
        ],
        "declarations": {
            "privacyPolicy": {
                "url": "https://example.com/privacy",
                "evidenceRefs": [
                    "docs/privacy/privacy-policy.md",
                    "fastlane/metadata/android/en-US/full_description.txt",
                ],
            },
            "ads": {
                "containsAds": False,
                "answer": "No ads.",
                "evidenceRefs": [
                    "docs/privacy/privacy-policy.md",
                    "fastlane/metadata/android/en-US/full_description.txt",
                ],
            },
            "appAccess": {
                "restrictedAccess": False,
                "reviewerInstructions": "No login required.",
                "evidenceRefs": ["docs/privacy/privacy-policy.md"],
            },
            "targetAudience": {
                "designedForChildren": False,
                "ageGroups": ["18+"],
                "rationale": "Not designed for children.",
                "evidenceRefs": ["fastlane/metadata/android/en-US/full_description.txt"],
            },
            "contentRatingNotes": [
                {
                    "formArea": "User-generated content",
                    "answer": "Yes.",
                    "evidenceRefs": ["docs/support/community-reporting.md"],
                },
                {
                    "formArea": "Generated content",
                    "answer": "Yes.",
                    "evidenceRefs": ["docs/privacy/ai-generation.md"],
                },
                {
                    "formArea": "Ads and purchases",
                    "answer": "No.",
                    "evidenceRefs": ["fastlane/metadata/android/en-US/full_description.txt"],
                },
            ],
            "dataSafety": {
                "policyPath": "docs/privacy/data-safety.json",
                "docsPath": "docs/privacy/data-safety.md",
                "privacyPolicyPath": "docs/privacy/privacy-policy.md",
                "evidenceRefs": [
                    "docs/privacy/data-safety.json",
                    "docs/privacy/data-safety.md",
                    "docs/privacy/privacy-policy.md",
                ],
            },
            "ugc": {
                "hasUserGeneratedContent": True,
                "reportAndBlockAvailable": True,
                "moderationQueueAvailable": True,
                "termsOrGuidelinesStatus": "ownerActionRequired",
                "answer": "Needs owner action.",
                "evidenceRefs": [
                    "docs/support/community-reporting.md",
                    "docs/community-block-user-policy.md",
                ],
            },
            "generatedContent": {
                "hasGeneratedContent": True,
                "generationProvider": "test",
                "reportAvailable": True,
                "disclosureRequired": True,
                "answer": "Generated content can be reported.",
                "evidenceRefs": ["docs/privacy/ai-generation.md"],
            },
            "sensitivePermissions": [
                {
                    "name": "android.permission.RECORD_AUDIO",
                    "declaration": "Microphone.",
                    "evidenceRefs": ["docs/privacy/data-safety.md"],
                }
            ],
        },
        "ownerActions": [
            {
                "id": "confirm-ugc-guidelines-consent",
                "status": "requiredBeforePlayProduction",
                "description": "Confirm guidelines.",
                "evidenceRefs": ["docs/support/community-reporting.md"],
            }
        ],
        "sourceUrls": source_urls,
    }


def seed_repo(repo: Path) -> Path:
    policy = minimal_policy()
    write(repo / "app/build.gradle.kts", 'android { defaultConfig { applicationId = "com.freevibe" } }\n')
    write(repo / "app/src/main/AndroidManifest.xml", "<manifest />\n")
    write(
        repo / "fastlane/metadata/android/en-US/full_description.txt",
        "Aura. No ads. No tracking. Privacy: https://example.com/privacy\n",
    )
    write(repo / "docs/privacy/privacy-policy.md", "# Aura Privacy Policy\n\nNo ads. No cross-app tracking.\n")
    write(repo / "docs/privacy/data-safety.md", "`android.permission.RECORD_AUDIO`\n")
    write(
        repo / "docs/privacy/data-safety.json",
        json.dumps(
            {
                "schemaVersion": 1,
                "policyKind": "privacyDataSafetyMatrix",
                "permissions": [{"name": "android.permission.RECORD_AUDIO"}],
            }
        ),
    )
    for path in policy["requiredEvidencePaths"]:  # type: ignore[index]
        candidate = repo / str(path)
        if not candidate.exists():
            write(candidate, "evidence\n")
    docs_terms = "\n".join(
        [
            "# Play App Content Declaration Packet",
            "Privacy Policy",
            "Ads",
            "App Access",
            "Target Audience",
            "Content Rating",
            "Data Safety",
            "User-Generated Content",
            "Generated Content",
            "Sensitive Permissions",
            "Owner Actions",
            "Release Checklist",
            "Sources",
            "confirm-ugc-guidelines-consent",
            *policy["sourceUrls"],  # type: ignore[list-item]
        ]
    )
    write(repo / "docs/distribution/play-app-content.md", docs_terms)
    return repo


class PlayAppContentPacketCheckTest(unittest.TestCase):
    def test_live_play_app_content_packet_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("com.freevibe", result["packageName"])
        self.assertGreaterEqual(result["ownerActionCount"], 1)
        self.assertGreaterEqual(result["sensitivePermissionCount"], 1)

    def test_rejects_package_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["packageName"] = "com.example.bad"

            with self.assertRaises(PlayAppContentPacketError):
                validate_policy(repo, policy)

    def test_rejects_missing_evidence_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["requiredEvidencePaths"] = ["docs/missing.md"]

            with self.assertRaises(PlayAppContentPacketError):
                validate_policy(repo, policy)

    def test_rejects_child_target_age_group(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = copy.deepcopy(minimal_policy())
            policy["declarations"]["targetAudience"]["ageGroups"] = ["under 13"]  # type: ignore[index]

            with self.assertRaises(PlayAppContentPacketError):
                validate_policy(repo, policy)

    def test_rejects_missing_ugc_owner_action(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = minimal_policy()
            policy["ownerActions"] = []

            with self.assertRaises(PlayAppContentPacketError):
                validate_policy(repo, policy)

    def test_rejects_sensitive_permission_missing_from_matrix(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = seed_repo(Path(tmpdir))
            policy = copy.deepcopy(minimal_policy())
            policy["declarations"]["sensitivePermissions"][0]["name"] = "android.permission.CAMERA"  # type: ignore[index]

            with self.assertRaises(PlayAppContentPacketError):
                validate_policy(repo, policy)


if __name__ == "__main__":
    unittest.main()
