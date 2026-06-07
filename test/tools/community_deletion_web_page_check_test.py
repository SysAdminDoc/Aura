from __future__ import annotations

import unittest

from tools.community_deletion_web_page_check import WebPageCheckError, validate_web_page


def page_text() -> str:
    return """
# Aura Community Account Deletion Request

Use this page when you cannot open Aura but need to request deletion of your
Aura community identity and associated community data.

- `requestCode`: required `AURA-` deletion request code.
- `contact`: required reply contact.
- `requesterStatement`: required deletion request statement.
- `attestations.deleteCommunityIdentity`: required confirmation.
- `attestations.understandsRetainedRecords`: required confirmation about retained records.
- `attestations.understandsPublicUploadsSeparate`: required confirmation about public uploads.

Public uploads may need separate owner/admin deletion handling.
Review the Aura privacy policy before submitting the request.
The hosted page must not send users back to the app as the only way to request deletion.
"""


class CommunityDeletionWebPageCheckTest(unittest.TestCase):
    def test_web_page_passes_with_required_copy_and_fields(self) -> None:
        result = validate_web_page(page_text())

        self.assertEqual("readyForOwnerPublication", result["status"])
        self.assertEqual(6, result["requiredFieldCount"])

    def test_web_page_rejects_missing_field(self) -> None:
        text = page_text().replace("requesterStatement", "statement")

        with self.assertRaises(WebPageCheckError):
            validate_web_page(text)

    def test_web_page_rejects_sensitive_identifier_request(self) -> None:
        text = page_text() + "\nPlease paste your Firebase UID.\n"

        with self.assertRaises(WebPageCheckError):
            validate_web_page(text)

    def test_web_page_requires_app_independent_request_path(self) -> None:
        text = page_text().replace(
            "The hosted page must not send users back to the app as the only way to request deletion.",
            "Open the app to finish deletion.",
        )

        with self.assertRaises(WebPageCheckError):
            validate_web_page(text)


if __name__ == "__main__":
    unittest.main()
