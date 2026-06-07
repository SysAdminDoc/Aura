from __future__ import annotations

import unittest

from tools.community_deletion_request_lookup import (
    collect_uid_candidates,
    deletion_request_code,
    lookup_deletion_request,
    normalize_request_code,
)


class CommunityDeletionRequestLookupTest(unittest.TestCase):
    def test_deletion_request_code_matches_android_format(self) -> None:
        code = deletion_request_code("firebase-uid-123")

        self.assertTrue(code.startswith("AURA-"))
        self.assertEqual(17, len(code))
        self.assertEqual(code, deletion_request_code(" firebase-uid-123 "))
        self.assertEqual("", deletion_request_code(" "))

    def test_lookup_matches_request_code_and_reports_evidence(self) -> None:
        database_export = {
            "votes": {
                "content1": {"voters": {"firebase-uid-123": True}},
            },
            "creator_profiles": {
                "firebase-uid-123": {"label": "Creator"},
            },
            "community_sounds": {
                "sound1": {"uploaderUid": "firebase-uid-123"},
            },
            "shared_collections": {
                "share1": {"createdByUid": "firebase-uid-123"},
            },
            "community_reports": {
                "report1": {"reporterUid": "other-user", "uploaderUid": "firebase-uid-123"},
            },
        }

        lookup = lookup_deletion_request(database_export, deletion_request_code("firebase-uid-123"))

        self.assertEqual(1, lookup["matchCount"])
        self.assertGreaterEqual(lookup["candidateCount"], 2)
        match = lookup["matches"][0]
        self.assertEqual("firebase-uid-123", match["uid"])
        self.assertEqual("firebase-uid-123", match["safeUid"])
        self.assertIn("/votes/content1/voters/firebase-uid-123", match["evidence"])
        self.assertIn("/community_sounds/sound1/uploaderUid", match["evidence"])
        self.assertIn("/shared_collections/share1/createdByUid", match["evidence"])

    def test_collect_uid_candidates_reads_nested_keys_and_values(self) -> None:
        candidates = collect_uid_candidates(
            {
                "community_user_blocks": {
                    "blocker-1": {
                        "blocked-1": {"blockedUid": "blocked/raw"},
                    },
                },
                "owner_uploads": {
                    "owner-1": {"sounds": {"sound1": True}},
                },
            }
        )

        self.assertIn("blocker-1", candidates)
        self.assertIn("blocked-1", candidates)
        self.assertIn("blocked/raw", candidates)
        self.assertIn("owner-1", candidates)

    def test_lookup_rejects_invalid_input(self) -> None:
        with self.assertRaises(ValueError):
            normalize_request_code("not-a-code")

        with self.assertRaises(ValueError):
            lookup_deletion_request([], "AURA-123456789ABC")


if __name__ == "__main__":
    unittest.main()
