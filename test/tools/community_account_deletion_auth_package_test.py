from __future__ import annotations

import unittest

from tools.community_account_deletion_auth_package import (
    ReviewError,
    build_auth_deletion_package,
)
from tools.community_deletion_request_lookup import deletion_request_code, lookup_deletion_request


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


def lookup_for_uid(uid: str = "firebase-uid-123") -> dict[str, object]:
    return lookup_deletion_request(
        {
            "votes": {"content1": {"voters": {uid: True}}},
            "creator_profiles": {uid: {"label": "Creator"}},
        },
        deletion_request_code(uid),
    )


class CommunityAccountDeletionAuthPackageTest(unittest.TestCase):
    def test_auth_package_contains_private_uid_after_backend_completion(self) -> None:
        package = build_auth_deletion_package(
            lookup_for_uid(),
            completion_receipt(),
            deletion_request_code("firebase-uid-123"),
            support_reference="ticket-123",
            operator="ops-1",
            packaged_at="2026-06-06T15:30:00Z",
        )

        self.assertEqual("readyForAuthDeletion", package["packageStatus"])
        self.assertEqual("firebase-uid-123", package["uid"])
        self.assertEqual("firebase-uid-123", package["safeUid"])
        self.assertEqual("...id-123", package["uidKeySuffix"])
        self.assertEqual("ops-1", package["operator"])
        self.assertIn("Private package", package["executorWarning"])
        self.assertIn("completionReceiptHash", package)
        self.assertIn("lookupEvidenceHash", package)

    def test_auth_package_requires_completed_backend_receipt(self) -> None:
        receipt = completion_receipt()
        receipt["completionStatus"] = "dryRun"

        with self.assertRaises(ReviewError):
            build_auth_deletion_package(
                lookup_for_uid(),
                receipt,
                deletion_request_code("firebase-uid-123"),
                support_reference="ticket-123",
                operator="ops-1",
            )

    def test_auth_package_rejects_completion_request_mismatch(self) -> None:
        receipt = completion_receipt()
        receipt["requestCode"] = deletion_request_code("other-uid")

        with self.assertRaises(ReviewError):
            build_auth_deletion_package(
                lookup_for_uid(),
                receipt,
                deletion_request_code("firebase-uid-123"),
                support_reference="ticket-123",
                operator="ops-1",
            )

    def test_auth_package_rejects_lookup_uid_that_does_not_match_code(self) -> None:
        code = deletion_request_code("firebase-uid-123")
        lookup = {
            "schemaVersion": 1,
            "requestCode": code,
            "candidateCount": 1,
            "matchCount": 1,
            "matches": [
                {
                    "uid": "other-uid",
                    "safeUid": "other-uid",
                    "requestCode": code,
                    "evidence": ["/creator_profiles/other-uid"],
                }
            ],
        }

        with self.assertRaises(ReviewError):
            build_auth_deletion_package(
                lookup,
                completion_receipt(),
                code,
                support_reference="ticket-123",
                operator="ops-1",
            )


if __name__ == "__main__":
    unittest.main()
