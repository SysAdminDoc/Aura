from __future__ import annotations

import unittest

from tools.community_account_deletion_apply_simulator import simulate_account_deletion_apply
from tools.community_account_deletion_executor_package import (
    ReviewError,
    build_executor_package,
)
from tools.community_account_deletion_plan import build_account_deletion_plan
from tools.community_account_deletion_review import review_account_deletion_request
from tools.community_deletion_request_lookup import deletion_request_code, lookup_deletion_request


def reviewed_simulation(database_export: dict[str, object], uid: str) -> tuple[dict[str, object], dict[str, object], dict[str, object], str]:
    request_code = deletion_request_code(uid)
    plan = build_account_deletion_plan(database_export, uid)
    lookup = lookup_deletion_request(database_export, request_code)
    review = review_account_deletion_request(
        lookup,
        plan,
        request_code,
        reviewed_at="2026-06-06T12:00:00Z",
    )
    simulation, _snapshot = simulate_account_deletion_apply(
        database_export,
        plan,
        review,
        simulated_at="2026-06-06T12:30:00Z",
    )
    return plan, review, simulation, request_code


class CommunityAccountDeletionExecutorPackageTest(unittest.TestCase):
    def test_executor_package_contains_reviewed_updates_and_hashes(self) -> None:
        database_export = {
            "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
            "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
        }
        plan, review, simulation, request_code = reviewed_simulation(database_export, "firebase-uid-123")

        package = build_executor_package(
            plan,
            review,
            simulation,
            request_code,
            operator="ops-1",
            packaged_at="2026-06-06T13:00:00Z",
        )

        self.assertEqual("readyForTrustedExecutor", package["packageStatus"])
        self.assertEqual("ops-1", package["operator"])
        self.assertEqual(2, package["updateCount"])
        self.assertEqual(plan["updates"], package["updates"])
        self.assertEqual(review["planHash"], package["planHash"])
        self.assertEqual(simulation["snapshotHash"], package["snapshotHash"])
        self.assertIn("/creator_profiles/firebase-uid-123", package["updates"])

    def test_executor_package_rejects_failed_simulation(self) -> None:
        database_export = {
            "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
            "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
        }
        plan, review, simulation, request_code = reviewed_simulation(database_export, "firebase-uid-123")
        simulation["simulationStatus"] = "failed"

        with self.assertRaises(ReviewError):
            build_executor_package(plan, review, simulation, request_code, operator="ops-1")

    def test_executor_package_rejects_review_simulation_mismatch(self) -> None:
        database_export = {
            "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
            "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
        }
        plan, review, simulation, request_code = reviewed_simulation(database_export, "firebase-uid-123")
        simulation["reviewHash"] = "bad"

        with self.assertRaises(ReviewError):
            build_executor_package(plan, review, simulation, request_code, operator="ops-1")

    def test_executor_package_requires_operator(self) -> None:
        database_export = {
            "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
            "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
        }
        plan, review, simulation, request_code = reviewed_simulation(database_export, "firebase-uid-123")

        with self.assertRaises(ReviewError):
            build_executor_package(plan, review, simulation, request_code, operator=" ")


if __name__ == "__main__":
    unittest.main()
