from __future__ import annotations

import unittest

from tools.community_upload_backfill_plan import backfill_plan, extract_storage_path


class CommunityUploadBackfillPlanTest(unittest.TestCase):
    def test_extract_storage_path_from_firebase_download_url(self) -> None:
        url = "https://firebasestorage.googleapis.com/v0/b/aura.appspot.com/o/sounds%2Fuid1%2Fclip.mp3?alt=media&token=abc"

        self.assertEqual("sounds/uid1/clip.mp3", extract_storage_path(url))

    def test_extract_storage_path_from_storage_googleapis_url(self) -> None:
        url = "https://storage.googleapis.com/aura.appspot.com/wallpapers/uid1/wall.jpg"

        self.assertEqual("wallpapers/uid1/wall.jpg", extract_storage_path(url))

    def test_backfill_plan_builds_metadata_and_owner_index_updates(self) -> None:
        database_export = {
            "community_sounds": {
                "sound1": {
                    "downloadUrl": "https://firebasestorage.googleapis.com/v0/b/aura.appspot.com/o/sounds%2Fuid1%2Fclip.mp3",
                    "uploaderId": "uid1",
                    "uploadedAt": 1234,
                    "name": "Clip",
                }
            },
            "community_wallpapers": {
                "wall1": {
                    "downloadUrl": "wallpapers/uid2/wall.jpg",
                    "uploaderUid": "uid2",
                    "uploadedAt": 5678,
                    "name": "Wall",
                }
            },
        }

        plan = backfill_plan(database_export)

        self.assertEqual(2, plan["candidateCount"])
        sound = plan["candidates"][0]
        self.assertEqual("sounds/uid1/clip.mp3", sound["storagePath"])
        self.assertEqual("uid1", sound["ownerUid"])
        self.assertEqual("sounds/uid1/clip.mp3", sound["updates"]["/community_sounds/sound1/storagePath"])
        self.assertEqual("uid1", sound["updates"]["/community_sounds/sound1/uploaderUid"])
        self.assertEqual(
            {
                "uploadId": "sound1",
                "publicId": "cu_sound1",
                "contentType": "SOUND",
                "metadataPath": "/community_sounds/sound1",
                "storagePath": "sounds/uid1/clip.mp3",
                "title": "Clip",
                "createdAt": 1234,
            },
            sound["updates"]["/owner_uploads/uid1/sounds/sound1"],
        )

    def test_backfill_plan_blocks_unresolvable_or_unsafe_rows(self) -> None:
        database_export = {
            "community_sounds": {
                "missingUrl": {"uploaderUid": "uid1", "uploadedAt": 1},
                "wrongPrefix": {"downloadUrl": "wallpapers/uid1/wall.jpg", "uploaderUid": "uid1", "uploadedAt": 1},
                "missingOwner": {"downloadUrl": "sounds/uid1/clip.mp3", "uploadedAt": 1},
                "missingTime": {"downloadUrl": "sounds/uid1/clip.mp3", "uploaderUid": "uid1"},
                "done": {"storagePath": "sounds/uid1/done.mp3"},
            }
        }

        plan = backfill_plan(database_export)

        self.assertEqual(0, plan["candidateCount"])
        self.assertEqual(4, plan["blockedCount"])
        self.assertEqual(1, plan["alreadyBackfilledCount"])
        self.assertEqual(
            [
                "expected sounds/ prefix",
                "missing created timestamp",
                "missing owner UID",
                "storage path could not be derived",
            ],
            sorted(item["reason"] for item in plan["blocked"]),
        )


if __name__ == "__main__":
    unittest.main()
