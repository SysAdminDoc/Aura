from __future__ import annotations

import unittest

from tools.community_account_deletion_auth_package import build_auth_deletion_package
from tools.community_account_deletion_upload_plan import (
    ReviewError,
    build_account_upload_deletion_plan,
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


class CommunityAccountDeletionUploadPlanTest(unittest.TestCase):
    def test_upload_plan_collects_owned_uploads_and_blocks_missing_handles(self) -> None:
        plan = build_account_upload_deletion_plan(
            {
                "community_sounds": {
                    "sound1": {
                        "uploaderUid": "firebase-uid-123",
                        "storagePath": "sounds/firebase-uid-123/clip.mp3",
                        "uploadedAt": 1000,
                        "name": "Clip",
                    },
                    "sound2": {
                        "uploaderUid": "firebase-uid-123",
                        "uploadedAt": 1001,
                    },
                    "other": {
                        "uploaderUid": "other-uid",
                        "storagePath": "sounds/other-uid/clip.mp3",
                    },
                },
                "community_wallpapers": {
                    "wall1": {
                        "uploaderId": "firebase-uid-123",
                        "storagePath": "wallpapers/firebase-uid-123/wall.jpg",
                        "createdAt": 1002,
                        "title": "Wall",
                    },
                    "wall2": {
                        "uploaderUid": "firebase-uid-123",
                        "storagePath": "sounds/firebase-uid-123/wrong.mp3",
                    },
                },
            },
            auth_package(),
            planned_at="2026-06-06T16:00:00Z",
        )

        self.assertEqual("blockedUntilUploadHandlesReviewed", plan["planStatus"])
        self.assertEqual(2, plan["candidateCount"])
        self.assertEqual(2, plan["blockedCount"])
        self.assertEqual(2, plan["storageDeleteCount"])

        sound = next(candidate for candidate in plan["candidates"] if candidate["uploadId"] == "sound1")
        self.assertEqual("/community_sounds/sound1", sound["metadataPath"])
        self.assertEqual("/owner_uploads/firebase-uid-123/sounds/sound1", sound["ownerIndexPath"])
        self.assertEqual("/community_upload_deletions/cu_sound1", sound["deletionTombstonePath"])
        self.assertEqual("sounds/firebase-uid-123/clip.mp3", sound["storagePath"])

        blocked_reasons = {row["uploadId"]: row["reason"] for row in plan["blocked"]}
        self.assertIn("Upload storagePath must be a non-empty string", blocked_reasons["sound2"])
        self.assertIn("Upload storagePath must start with wallpapers/firebase-uid-123/", blocked_reasons["wall2"])

    def test_upload_plan_ready_when_all_owned_uploads_have_handles(self) -> None:
        plan = build_account_upload_deletion_plan(
            {
                "community_sounds": {
                    "sound1": {
                        "uploaderUid": "firebase-uid-123",
                        "storagePath": "sounds/firebase-uid-123/clip.mp3",
                    },
                },
            },
            auth_package(),
        )

        self.assertEqual("readyForOwnerAdminUploadWorkflow", plan["planStatus"])
        self.assertEqual(1, plan["candidateCount"])
        self.assertEqual(0, plan["blockedCount"])

    def test_upload_plan_rejects_invalid_auth_package(self) -> None:
        package = auth_package()
        package["packageStatus"] = "draft"

        with self.assertRaises(ReviewError):
            build_account_upload_deletion_plan({}, package)


if __name__ == "__main__":
    unittest.main()
