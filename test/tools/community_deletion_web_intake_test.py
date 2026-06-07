from __future__ import annotations

import unittest

from tools.community_deletion_request_lookup import deletion_request_code
from tools.community_deletion_web_intake import (
    ReviewError,
    build_web_intake_receipt,
    dump_web_intake_receipt,
)


def intake_request(uid: str = "firebase-uid-123") -> dict[str, object]:
    return {
        "requestCode": deletion_request_code(uid).lower(),
        "contact": "Requester@Example.com",
        "requesterStatement": "Please delete my Aura community identity and associated community data.",
        "channel": "private-web",
        "submittedAt": "2026-06-06T14:00:00Z",
        "locale": "en-US",
        "attestations": {
            "deleteCommunityIdentity": True,
            "understandsRetainedRecords": True,
            "understandsPublicUploadsSeparate": True,
        },
    }


class CommunityDeletionWebIntakeTest(unittest.TestCase):
    def test_web_intake_receipt_hashes_private_contact_and_statement(self) -> None:
        request = intake_request()

        receipt = build_web_intake_receipt(
            request,
            support_reference="ticket-123",
            received_at="2026-06-06T14:05:00Z",
        )
        receipt_text = dump_web_intake_receipt(receipt)

        self.assertEqual("readyForOperatorLookup", receipt["intakeStatus"])
        self.assertEqual(deletion_request_code("firebase-uid-123"), receipt["requestCode"])
        self.assertEqual("ticket-123", receipt["supportReference"])
        self.assertTrue(receipt["contactProvided"])
        self.assertIn("contactHash", receipt)
        self.assertIn("requesterStatementHash", receipt)
        self.assertNotIn("Requester@Example.com", receipt_text)
        self.assertNotIn("requester@example.com", receipt_text)
        self.assertNotIn("Please delete my Aura", receipt_text)
        self.assertNotIn("firebase-uid-123", receipt_text)

    def test_web_intake_requires_all_attestations(self) -> None:
        request = intake_request()
        request["attestations"] = {
            "deleteCommunityIdentity": True,
            "understandsRetainedRecords": True,
            "understandsPublicUploadsSeparate": False,
        }

        with self.assertRaises(ReviewError):
            build_web_intake_receipt(request, support_reference="ticket-123")

    def test_web_intake_rejects_invalid_request_code(self) -> None:
        request = intake_request()
        request["requestCode"] = "bad-code"

        with self.assertRaises(ValueError):
            build_web_intake_receipt(request, support_reference="ticket-123")


if __name__ == "__main__":
    unittest.main()
