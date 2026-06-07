from __future__ import annotations

import unittest

from tools.community_storage_orphan_report import build_report, load_storage_objects


class CommunityStorageOrphanReportTest(unittest.TestCase):
    def test_report_classifies_orphans_and_legacy_rows(self) -> None:
        storage_objects = load_storage_objects(
            [
                {"name": "sounds/user1/kept.mp3"},
                {"name": "sounds/user1/orphan.mp3"},
                {"name": "wallpapers/user1/kept.jpg"},
                {"name": "wallpapers/user1/orphan.jpg"},
                {"name": "avatars/user1.png"},
            ]
        )
        database_export = {
            "community_sounds": {
                "sound1": {"storagePath": "sounds/user1/kept.mp3"},
                "sound2": {},
                "sound3": {"storagePath": "wallpapers/user1/wrong.jpg"},
                "sound4": {"storagePath": "sounds/user1/missing.mp3"},
            },
            "community_wallpapers": {
                "wall1": {"storagePath": "wallpapers/user1/kept.jpg"},
            },
        }

        report = build_report(storage_objects, database_export)

        self.assertEqual(
            ["sounds/user1/orphan.mp3", "wallpapers/user1/orphan.jpg"],
            report["orphanCandidates"],
        )
        self.assertEqual(["sounds/user1/missing.mp3"], report["metadataWithMissingObject"])
        self.assertEqual(
            [{"root": "community_sounds", "uploadId": "sound2"}],
            report["legacyRowsMissingStoragePath"],
        )
        self.assertEqual(
            [
                {
                    "root": "community_sounds",
                    "uploadId": "sound3",
                    "storagePath": "wallpapers/user1/wrong.jpg",
                    "reason": "expected sounds/ prefix",
                }
            ],
            report["invalidMetadataStoragePaths"],
        )
        self.assertEqual(["avatars/user1.png"], report["unmanagedObjects"])

    def test_storage_export_accepts_plain_string_paths(self) -> None:
        self.assertEqual({"sounds/user1/a.mp3"}, load_storage_objects(["sounds/user1/a.mp3", ""]))


if __name__ == "__main__":
    unittest.main()
