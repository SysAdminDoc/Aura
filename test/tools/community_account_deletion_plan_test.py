from __future__ import annotations

import unittest

from tools.community_account_deletion_plan import build_account_deletion_plan, sanitize_key


class CommunityAccountDeletionPlanTest(unittest.TestCase):
    def test_sanitize_key_matches_rtdb_key_rules(self) -> None:
        self.assertEqual("uid_1_2_3_4_5_6", sanitize_key("uid/1.2#3$4[5]6"))

    def test_plan_removes_private_markers_and_identity_rows(self) -> None:
        database_export = {
            "votes": {
                "content1": {"upvotes": 4, "voters": {"uid_1": True, "other": True}},
                "content2": {"upvotes": 1, "voters": {"other": True}},
            },
            "voters": {
                "content1": {"uid_1": True},
                "content3": {"uid_1": True, "other": True},
            },
            "creator_follows": {
                "uid_1": {"creator-a": {"creatorId": "creator-a"}},
                "fan-1": {
                    "uid_1": {"creatorId": "uid/1"},
                    "creator-b": {"creatorId": "creator-b"},
                },
            },
            "creator_profiles": {
                "uid_1": {"label": "Deleted"},
                "other": {"label": "Other"},
            },
            "community_user_blocks": {
                "uid_1": {
                    "blocked-1": {"blockerUid": "uid_1", "blockedUid": "blocked-1"},
                },
                "blocker-1": {
                    "uid_1": {"blockerUid": "blocker-1", "blockedUid": "uid/1"},
                },
            },
            "community_blocked_by": {
                "uid_1": {
                    "blocker-1": {"blockerUid": "blocker-1", "blockedUid": "uid_1"},
                },
                "blocked-1": {
                    "uid_1": {"blockerUid": "uid_1", "blockedUid": "blocked-1"},
                },
            },
            "shared_collections": {
                "share-1": {"createdByUid": "uid/1"},
                "share-2": {"createdByUid": "other"},
            },
            "collection_shares": {
                "legacy-1": {"ownerUid": "uid_1"},
            },
        }

        plan = build_account_deletion_plan(database_export, "uid/1")

        self.assertEqual("uid_1", plan["uid"])
        self.assertEqual(
            {
                "/collection_shares/legacy-1": None,
                "/community_blocked_by/blocked-1/uid_1": None,
                "/community_blocked_by/uid_1": None,
                "/community_user_blocks/blocker-1/uid_1": None,
                "/community_user_blocks/uid_1": None,
                "/creator_follows/fan-1/uid_1": None,
                "/creator_follows/uid_1": None,
                "/creator_profiles/uid_1": None,
                "/shared_collections/share-1": None,
                "/voters/content1/uid_1": None,
                "/voters/content3/uid_1": None,
                "/votes/content1/voters/uid_1": None,
            },
            plan["updates"],
        )
        self.assertEqual(12, plan["updateCount"])
        self.assertEqual(["/voters/content1/uid_1", "/voters/content3/uid_1", "/votes/content1/voters/uid_1"], plan["categories"]["voteMarkers"])
        self.assertTrue(any(item["root"] == "/votes/*/upvotes" for item in plan["retained"]))

    def test_plan_rejects_blank_uid_and_invalid_roots(self) -> None:
        with self.assertRaises(ValueError):
            build_account_deletion_plan({}, "   ")

        with self.assertRaises(ValueError):
            build_account_deletion_plan({"votes": []}, "uid-1")


if __name__ == "__main__":
    unittest.main()
