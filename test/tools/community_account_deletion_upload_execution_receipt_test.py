from __future__ import annotations

import unittest

from tools.community_account_deletion_auth_package import build_auth_deletion_package
from tools.community_account_deletion_upload_execution_receipt import (
    ReviewError,
    build_upload_execution_receipt,
    dump_upload_execution_receipt,
    validate_upload_execution_receipt,
)
from tools.community_account_deletion_upload_plan import build_account_upload_deletion_plan
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


def ready_upload_plan() -> dict[str, object]:
    return build_account_upload_deletion_plan(
        {
            "community_sounds": {
                "sound1": {
                    "uploaderUid": "firebase-uid-123",
                    "storagePath": "sounds/firebase-uid-123/clip.mp3",
                    "uploadedAt": 1000,
                    "name": "Clip",
                },
            },
            "community_wallpapers": {
                "wall1": {
                    "uploaderId": "firebase-uid-123",
                    "storagePath": "wallpapers/firebase-uid-123/wall.jpg",
                    "createdAt": 1002,
                    "title": "Wall",
                },
            },
        },
        auth_package(),
        planned_at="2026-06-07T18:00:00Z",
    )


def blocked_upload_plan() -> dict[str, object]:
    return build_account_upload_deletion_plan(
        {
            "community_sounds": {
                "sound1": {
                    "uploaderUid": "firebase-uid-123",
                    "uploadedAt": 1000,
                },
            },
        },
        auth_package(),
    )


def execution_evidence(plan: dict[str, object]) -> dict[str, object]:
    deleted_uploads = []
    for index, candidate in enumerate(plan["candidates"], start=1):  # type: ignore[index]
        row = dict(candidate)
        row.update(
            {
                "storageDeleted": True,
                "metadataDeleted": True,
                "ownerIndexDeleted": True,
                "tombstoneWritten": True,
                "privateEvidenceReference": f"private-support/ticket-123/upload-delete-{index}.txt",
                "privateEvidenceHash": f"{index}" * 64,
            }
        )
        deleted_uploads.append(row)
    return {
        "schemaVersion": 1,
        "evidenceKind": "communityAccountDeletionUploadExecution",
        "executionStatus": "completed",
        "requestCode": plan["requestCode"],
        "supportReference": "ticket-123",
        "method": "ownerAdminWorkflow",
        "projectId": "aura-production",
        "executedBy": "owner-ops",
        "executedAt": "2026-06-07T18:10:00Z",
        "ownerApprovalReference": "approval-uploads-123",
        "deletedUploads": deleted_uploads,
    }


class CommunityAccountDeletionUploadExecutionReceiptTest(unittest.TestCase):
    def test_upload_execution_receipt_redacts_paths_and_counts_deletes(self) -> None:
        plan = ready_upload_plan()
        receipt = build_upload_execution_receipt(
            plan,
            execution_evidence(plan),
            support_reference="ticket-123",
            receipted_at="2026-06-07T18:15:00Z",
        )
        receipt_text = dump_upload_execution_receipt(receipt)

        self.assertEqual("uploadsDeleted", receipt["executionStatus"])
        self.assertEqual(2, receipt["deletedUploadCount"])
        self.assertEqual(2, receipt["storageDeleteCount"])
        self.assertEqual(2, receipt["metadataDeleteCount"])
        self.assertEqual(2, receipt["ownerIndexDeleteCount"])
        self.assertEqual(2, receipt["tombstoneCount"])
        self.assertNotIn("firebase-uid-123", receipt_text)
        self.assertNotIn("sounds/firebase-uid-123/clip.mp3", receipt_text)
        self.assertNotIn("/community_sounds/sound1", receipt_text)
        self.assertNotIn("aura-production", receipt_text)
        validate_upload_execution_receipt(receipt, support_reference="ticket-123")

    def test_upload_execution_receipt_rejects_blocked_plan(self) -> None:
        plan = blocked_upload_plan()

        with self.assertRaises(ReviewError):
            build_upload_execution_receipt(plan, execution_evidence({"candidates": [], "requestCode": plan["requestCode"]}), "ticket-123")

    def test_upload_execution_receipt_rejects_missing_candidate_evidence(self) -> None:
        plan = ready_upload_plan()
        evidence = execution_evidence(plan)
        evidence["deletedUploads"] = evidence["deletedUploads"][:1]  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_upload_execution_receipt(plan, evidence, support_reference="ticket-123")

    def test_upload_execution_receipt_rejects_incomplete_delete_row(self) -> None:
        plan = ready_upload_plan()
        evidence = execution_evidence(plan)
        evidence["deletedUploads"][0]["tombstoneWritten"] = False  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_upload_execution_receipt(plan, evidence, support_reference="ticket-123")

    def test_upload_execution_receipt_rejects_path_mismatch(self) -> None:
        plan = ready_upload_plan()
        evidence = execution_evidence(plan)
        evidence["deletedUploads"][0]["storagePath"] = "sounds/firebase-uid-123/other.mp3"  # type: ignore[index]

        with self.assertRaises(ReviewError):
            build_upload_execution_receipt(plan, evidence, support_reference="ticket-123")


if __name__ == "__main__":
    unittest.main()
