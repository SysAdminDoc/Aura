from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.community_deletion_web_url_check import WebUrlCheckError, validate_manifest


def write_doc(repo_root: Path, relative_path: str, text: str) -> None:
    path = repo_root / relative_path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def manifest(status: str = "pendingOwnerUrl", public_url: str = "") -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "status": status,
        "publicUrl": public_url,
        "privacyPolicyPath": "docs/privacy/privacy-policy.md",
        "supportDocPath": "docs/support/community-account-deletion-web-intake.md",
        "lastReviewed": "2026-06-07",
        "requiredBefore": "Play production submission",
    }


class CommunityDeletionWebUrlCheckTest(unittest.TestCase):
    def test_pending_manifest_passes_with_empty_url_and_policy_marker(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_doc(
                repo_root,
                "docs/privacy/privacy-policy.md",
                "Hosted web deletion URL is pending owner publication.",
            )
            write_doc(repo_root, "docs/support/community-account-deletion-web-intake.md", "Support intake")

            result = validate_manifest(manifest(), repo_root)

        self.assertFalse(result["publicationReady"])
        self.assertEqual("pendingOwnerUrl", result["status"])

    def test_pending_manifest_rejects_non_empty_url(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_doc(
                repo_root,
                "docs/privacy/privacy-policy.md",
                "Hosted web deletion URL is pending owner publication.",
            )
            write_doc(repo_root, "docs/support/community-account-deletion-web-intake.md", "Support intake")

            with self.assertRaises(WebUrlCheckError):
                validate_manifest(manifest(public_url="https://example.com/delete"), repo_root)

    def test_published_manifest_requires_https_url_in_both_docs(self) -> None:
        url = "https://example.com/aura/delete-account"
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_doc(repo_root, "docs/privacy/privacy-policy.md", f"Delete account at {url}.")
            write_doc(
                repo_root,
                "docs/support/community-account-deletion-web-intake.md",
                f"Use the hosted deletion form: {url}.",
            )

            result = validate_manifest(manifest(status="published", public_url=url), repo_root)

        self.assertTrue(result["publicationReady"])
        self.assertEqual(url, result["publicUrl"])

    def test_published_manifest_rejects_missing_support_link(self) -> None:
        url = "https://example.com/aura/delete-account"
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_doc(repo_root, "docs/privacy/privacy-policy.md", f"Delete account at {url}.")
            write_doc(repo_root, "docs/support/community-account-deletion-web-intake.md", "Support intake")

            with self.assertRaises(WebUrlCheckError):
                validate_manifest(manifest(status="published", public_url=url), repo_root)

    def test_published_manifest_rejects_non_https_url(self) -> None:
        url = "http://example.com/aura/delete-account"
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_doc(repo_root, "docs/privacy/privacy-policy.md", f"Delete account at {url}.")
            write_doc(
                repo_root,
                "docs/support/community-account-deletion-web-intake.md",
                f"Use the hosted deletion form: {url}.",
            )

            with self.assertRaises(WebUrlCheckError):
                validate_manifest(manifest(status="published", public_url=url), repo_root)


if __name__ == "__main__":
    unittest.main()
