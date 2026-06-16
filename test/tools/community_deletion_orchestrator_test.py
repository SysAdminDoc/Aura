from __future__ import annotations

import json
import unittest
from unittest import mock

from tools.community_deletion_orchestrator import (
    ReviewError,
    build_deletion_orchestration,
    build_upload_inventory,
    format_requester_receipt,
)
from tools.community_deletion_request_lookup import deletion_request_code


UID = "firebase-uid-123"
REQUEST_CODE = deletion_request_code(UID)


def seeded_database() -> dict[str, object]:
    return {
        "votes": {
            "wallpaper-1": {
                "upvotes": 3,
                "voters": {UID: True, "other-uid": True},
            },
        },
        "voters": {
            "sound-1": {UID: True},
        },
        "creator_follows": {
            UID: {"creator-a": {"creatorId": "creator-a"}},
            "fan-uid": {UID: {"creatorId": UID}},
        },
        "creator_profiles": {
            UID: {"label": "Creator"},
        },
        "community_user_blocks": {
            UID: {"blocked-uid": {"blockedUid": "blocked-uid"}},
        },
        "community_sounds": {
            "sound1": {
                "uploaderUid": UID,
                "storagePath": f"sounds/{UID}/clip.mp3",
                "uploadedAt": 1000,
                "name": "Clip",
            },
            "sound2": {
                "uploaderUid": UID,
                "uploadedAt": 1001,
                "name": "Missing handle",
            },
            "other": {
                "uploaderUid": "other-uid",
                "storagePath": "sounds/other-uid/clip.mp3",
            },
        },
        "community_wallpapers": {
            "wall1": {
                "uploaderId": UID,
                "storagePath": f"wallpapers/{UID}/wall.jpg",
                "createdAt": 1002,
                "title": "Wall",
            },
        },
        "owner_uploads": {
            UID: {
                "sounds": {
                    "sound1": True,
                    "orphan": True,
                },
                "wallpapers": {
                    "wall1": True,
                },
            },
        },
    }


class CommunityDeletionOrchestratorTest(unittest.TestCase):
    def test_orchestration_builds_private_bundle_and_requester_safe_receipt(self) -> None:
        with mock.patch("tools.community_account_deletion_rest_executor.urllib.request.urlopen") as urlopen:
            orchestration = build_deletion_orchestration(
                seeded_database(),
                REQUEST_CODE,
                "https://aura.firebaseio.com",
                operator="ops-1",
                support_reference="ticket-123",
                orchestrated_at="2026-06-16T12:00:00Z",
            )

        urlopen.assert_not_called()
        self.assertEqual("communityDeletionTrustedDryRun", orchestration["orchestrationKind"])
        self.assertEqual("readyForTrustedRtdbApplyWithUploadReview", orchestration["orchestrationStatus"])
        self.assertEqual("dry-run", orchestration["rtdb"]["executionMode"])
        self.assertEqual("dryRun", orchestration["rtdb"]["executionStatus"])
        self.assertEqual(0, orchestration["postDeleteChecks"]["simulatedRtdbUpdatePathsRemaining"])
        self.assertEqual(2, orchestration["uploads"]["candidateCount"])
        self.assertEqual(2, orchestration["uploads"]["blockedCount"])

        private_package = orchestration["privateArtifacts"]["executorPackage"]
        self.assertIn(f"/creator_profiles/{UID}", private_package["updates"])
        upload_inventory = orchestration["privateArtifacts"]["uploadInventory"]
        storage_paths = {candidate["storagePath"] for candidate in upload_inventory["candidates"]}
        self.assertEqual({f"sounds/{UID}/clip.mp3", f"wallpapers/{UID}/wall.jpg"}, storage_paths)

        requester_receipt = orchestration["requesterSafeReceipt"]
        requester_text = json.dumps(requester_receipt, sort_keys=True) + format_requester_receipt(requester_receipt)
        self.assertIn(REQUEST_CODE, requester_text)
        self.assertNotIn(UID, requester_text)
        self.assertNotIn("creator_profiles", requester_text)
        self.assertNotIn("sounds/firebase-uid-123", requester_text)
        self.assertNotIn("aura.firebaseio.com", requester_text)

    def test_upload_inventory_blocks_missing_handles_and_orphan_owner_indexes(self) -> None:
        inventory = build_upload_inventory(
            seeded_database(),
            UID,
            REQUEST_CODE,
            support_reference="ticket-123",
            inventoried_at="2026-06-16T12:00:00Z",
        )

        self.assertEqual("blockedUntilUploadHandlesReviewed", inventory["inventoryStatus"])
        self.assertEqual(2, inventory["candidateCount"])
        self.assertEqual(2, inventory["blockedCount"])
        self.assertEqual(3, inventory["ownerIndexCount"])
        self.assertEqual(1, inventory["orphanOwnerIndexCount"])

        reasons = " ".join(row["reason"] for row in inventory["blocked"])
        self.assertIn("Upload storagePath must be a non-empty string", reasons)
        self.assertIn("Owner upload index has no matching public metadata row", reasons)

    def test_orchestration_rejects_missing_request_code_match(self) -> None:
        with self.assertRaises(ReviewError):
            build_deletion_orchestration(
                {"creator_profiles": {"other-uid": {"label": "Other"}}},
                "AURA-000000000000",
                "https://aura.firebaseio.com",
                operator="ops-1",
                support_reference="ticket-123",
            )

    def test_upload_inventory_rejects_uid_code_mismatch(self) -> None:
        with self.assertRaises(ReviewError):
            build_upload_inventory(
                seeded_database(),
                UID,
                deletion_request_code("other-uid"),
                support_reference="ticket-123",
            )


if __name__ == "__main__":
    unittest.main()
