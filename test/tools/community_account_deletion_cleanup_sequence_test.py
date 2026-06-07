from __future__ import annotations

import unittest

from tools.community_account_deletion_cleanup_sequence import (
    ReviewError,
    build_cleanup_sequence,
    dump_cleanup_sequence,
)
from tools.community_deletion_request_lookup import deletion_request_code


def completion_receipt(uid: str = "firebase-uid-123") -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "receiptKind": "communityAccountDeletionCompletion",
        "completionStatus": "completed",
        "requestCode": deletion_request_code(uid),
        "supportReference": "ticket-123",
        "uidKeySuffix": "...id-123",
        "deletedRtdbMarkerCount": 2,
        "completedAt": "2026-06-06T15:00:00Z",
        "planHash": "a" * 64,
        "updatesHash": "b" * 64,
        "packageHash": "c" * 64,
        "restReceiptHash": "d" * 64,
        "payloadHash": "e" * 64,
        "databaseHostHash": "f" * 64,
    }


class CommunityAccountDeletionCleanupSequenceTest(unittest.TestCase):
    def test_cleanup_sequence_starts_after_completion_receipt(self) -> None:
        sequence = build_cleanup_sequence(
            completion_receipt(),
            support_reference="ticket-123",
            sequenced_at="2026-06-06T15:05:00Z",
        )
        sequence_text = dump_cleanup_sequence(sequence)

        self.assertEqual("readyForLocalAndAuthCleanup", sequence["sequenceStatus"])
        self.assertEqual(deletion_request_code("firebase-uid-123"), sequence["requestCode"])
        self.assertEqual("ticket-123", sequence["supportReference"])
        self.assertEqual(3, len(sequence["steps"]))
        self.assertEqual("requester", sequence["steps"][0]["owner"])
        self.assertEqual("operator", sequence["steps"][1]["owner"])
        self.assertNotIn("firebase-uid-123", sequence_text)
        self.assertNotIn("aura.firebaseio.com", sequence_text)

    def test_cleanup_sequence_requires_completed_backend_receipt(self) -> None:
        receipt = completion_receipt()
        receipt["completionStatus"] = "dryRun"

        with self.assertRaises(ReviewError):
            build_cleanup_sequence(receipt, support_reference="ticket-123")

    def test_cleanup_sequence_requires_matching_support_reference(self) -> None:
        with self.assertRaises(ReviewError):
            build_cleanup_sequence(completion_receipt(), support_reference="ticket-456")

    def test_cleanup_sequence_rejects_invalid_marker_count(self) -> None:
        receipt = completion_receipt()
        receipt["deletedRtdbMarkerCount"] = -1

        with self.assertRaises(ReviewError):
            build_cleanup_sequence(receipt, support_reference="ticket-123")


if __name__ == "__main__":
    unittest.main()
