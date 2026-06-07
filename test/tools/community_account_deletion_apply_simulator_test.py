from __future__ import annotations

import unittest

from tools.community_account_deletion_apply_simulator import (
    ReviewError,
    simulate_account_deletion_apply,
)
from tools.community_account_deletion_plan import build_account_deletion_plan
from tools.community_account_deletion_review import review_account_deletion_request
from tools.community_deletion_request_lookup import deletion_request_code, lookup_deletion_request


def reviewed_plan(database_export: dict[str, object], uid: str) -> tuple[dict[str, object], dict[str, object]]:
    request_code = deletion_request_code(uid)
    plan = build_account_deletion_plan(database_export, uid)
    lookup = lookup_deletion_request(database_export, request_code)
    review = review_account_deletion_request(
        lookup,
        plan,
        request_code,
        reviewed_at="2026-06-06T12:00:00Z",
    )
    return plan, review


class CommunityAccountDeletionApplySimulatorTest(unittest.TestCase):
    def test_simulation_deletes_reviewed_marker_paths_and_keeps_retained_roots(self) -> None:
        database_export = {
            "votes": {
                "content1": {
                    "upvotes": 3,
                    "voters": {"firebase-uid-123": True, "other": True},
                },
            },
            "creator_profiles": {
                "firebase-uid-123": {"label": "Creator"},
            },
            "community_reports": {
                "report1": {"reporterUid": "firebase-uid-123"},
            },
        }
        plan, review = reviewed_plan(database_export, "firebase-uid-123")

        receipt, snapshot = simulate_account_deletion_apply(
            database_export,
            plan,
            review,
            simulated_at="2026-06-06T12:30:00Z",
        )

        self.assertEqual("passed", receipt["simulationStatus"])
        self.assertEqual(2, receipt["deletedPathCount"])
        self.assertEqual(0, receipt["missingBeforeCount"])
        self.assertEqual(0, receipt["remainingUpdatePathCount"])
        self.assertNotIn("firebase-uid-123", snapshot.get("creator_profiles", {}))
        self.assertNotIn("firebase-uid-123", snapshot["votes"]["content1"]["voters"])
        self.assertEqual(3, snapshot["votes"]["content1"]["upvotes"])
        self.assertIn("report1", snapshot["community_reports"])

    def test_simulation_reports_missing_paths_without_failing_remaining_checks(self) -> None:
        database_export = {
            "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
            "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
        }
        plan, review = reviewed_plan(database_export, "firebase-uid-123")
        del database_export["creator_profiles"]["firebase-uid-123"]

        receipt, snapshot = simulate_account_deletion_apply(database_export, plan, review)

        self.assertEqual("passedWithMissingPaths", receipt["simulationStatus"])
        self.assertEqual(1, receipt["deletedPathCount"])
        self.assertEqual(1, receipt["missingBeforeCount"])
        self.assertEqual(0, receipt["remainingUpdatePathCount"])
        self.assertNotIn("creator_profiles", snapshot)

    def test_simulation_rejects_review_plan_hash_mismatch(self) -> None:
        database_export = {
            "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
            "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
        }
        plan, review = reviewed_plan(database_export, "firebase-uid-123")
        review["planHash"] = "bad"

        with self.assertRaises(ReviewError):
            simulate_account_deletion_apply(database_export, plan, review)

    def test_review_rejects_retained_public_or_moderation_update_paths(self) -> None:
        database_export = {
            "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
            "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
        }
        plan, review = reviewed_plan(database_export, "firebase-uid-123")
        plan["updates"]["/votes/content1/upvotes"] = None
        plan["categories"]["voteMarkers"].append("/votes/content1/upvotes")
        plan["updateCount"] += 1

        with self.assertRaises(ReviewError):
            simulate_account_deletion_apply(database_export, plan, review)


if __name__ == "__main__":
    unittest.main()
