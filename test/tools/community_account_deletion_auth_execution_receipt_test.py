from __future__ import annotations

import unittest

from tools.community_account_deletion_auth_execution_receipt import (
    ReviewError,
    build_auth_execution_receipt,
    dump_auth_execution_receipt,
    validate_auth_execution_receipt,
)
from tools.community_account_deletion_auth_package import build_auth_deletion_package
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


def auth_package(uid: str = "firebase-uid-123") -> dict[str, object]:
    lookup = lookup_deletion_request(
        {
            "votes": {"content1": {"voters": {uid: True}}},
            "creator_profiles": {uid: {"label": "Creator"}},
        },
        deletion_request_code(uid),
    )
    return build_auth_deletion_package(
        lookup,
        completion_receipt(uid),
        deletion_request_code(uid),
        support_reference="ticket-123",
        operator="ops-1",
        packaged_at="2026-06-06T15:30:00Z",
    )


def execution_evidence(uid: str = "firebase-uid-123") -> dict[str, object]:
    return {
        "schemaVersion": 1,
        "evidenceKind": "communityAccountDeletionAuthExecution",
        "executionStatus": "deleted",
        "requestCode": deletion_request_code(uid),
        "supportReference": "ticket-123",
        "uid": uid,
        "uidHash": "26b0245c1ba2a4fb9f5da8b60373dd75e05cf1e79e12623c59ef114e312719d5",
        "method": "adminSdk",
        "projectId": "aura-production",
        "executedBy": "owner-ops",
        "executedAt": "2026-06-07T17:00:00Z",
        "ownerApprovalReference": "approval-123",
        "postDeleteVerification": "notFound",
        "privateEvidenceReference": "private-support/ticket-123/auth-delete.txt",
        "privateEvidenceHash": "f" * 64,
    }


class CommunityAccountDeletionAuthExecutionReceiptTest(unittest.TestCase):
    def test_auth_execution_receipt_redacts_private_uid_and_project_id(self) -> None:
        receipt = build_auth_execution_receipt(
            auth_package(),
            execution_evidence(),
            support_reference="ticket-123",
            receipted_at="2026-06-07T17:05:00Z",
        )
        receipt_text = dump_auth_execution_receipt(receipt)

        self.assertEqual("authDeleted", receipt["executionStatus"])
        self.assertEqual("adminSdk", receipt["method"])
        self.assertEqual("notFound", receipt["postDeleteVerification"])
        self.assertEqual("...id-123", receipt["uidKeySuffix"])
        self.assertIn("authPackageHash", receipt)
        self.assertIn("executionEvidenceHash", receipt)
        self.assertNotIn("firebase-uid-123", receipt_text)
        self.assertNotIn("aura-production", receipt_text)
        validate_auth_execution_receipt(receipt, support_reference="ticket-123")

    def test_auth_execution_receipt_rejects_uid_mismatch(self) -> None:
        evidence = execution_evidence()
        evidence["uid"] = "other-uid"

        with self.assertRaises(ReviewError):
            build_auth_execution_receipt(auth_package(), evidence, support_reference="ticket-123")

    def test_auth_execution_receipt_rejects_support_reference_mismatch(self) -> None:
        evidence = execution_evidence()
        evidence["supportReference"] = "ticket-999"

        with self.assertRaises(ReviewError):
            build_auth_execution_receipt(auth_package(), evidence, support_reference="ticket-123")

    def test_auth_execution_receipt_rejects_unapproved_method(self) -> None:
        evidence = execution_evidence()
        evidence["method"] = "mobileClient"

        with self.assertRaises(ReviewError):
            build_auth_execution_receipt(auth_package(), evidence, support_reference="ticket-123")

    def test_auth_execution_receipt_requires_private_evidence_hash(self) -> None:
        evidence = execution_evidence()
        evidence["privateEvidenceHash"] = "not-a-digest"

        with self.assertRaises(ReviewError):
            build_auth_execution_receipt(auth_package(), evidence, support_reference="ticket-123")


if __name__ == "__main__":
    unittest.main()
