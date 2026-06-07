from __future__ import annotations

import unittest
from unittest import mock

from tools.community_account_deletion_apply_simulator import simulate_account_deletion_apply
from tools.community_account_deletion_completion_receipt import (
    ReviewError,
    build_completion_receipt,
    dump_completion_receipt,
)
from tools.community_account_deletion_executor_package import build_executor_package
from tools.community_account_deletion_plan import build_account_deletion_plan
from tools.community_account_deletion_rest_executor import execute_package
from tools.community_account_deletion_review import review_account_deletion_request
from tools.community_deletion_request_lookup import deletion_request_code, lookup_deletion_request


def executor_package(database_export: dict[str, object], uid: str) -> dict[str, object]:
    request_code = deletion_request_code(uid)
    plan = build_account_deletion_plan(database_export, uid)
    lookup = lookup_deletion_request(database_export, request_code)
    review = review_account_deletion_request(lookup, plan, request_code, reviewed_at="2026-06-06T12:00:00Z")
    simulation, _snapshot = simulate_account_deletion_apply(database_export, plan, review, simulated_at="2026-06-06T12:30:00Z")
    return build_executor_package(plan, review, simulation, request_code, operator="ops-1", packaged_at="2026-06-06T13:00:00Z")


class FakeResponse:
    status = 200

    def __enter__(self) -> "FakeResponse":
        return self

    def __exit__(self, *_args: object) -> None:
        return None

    def read(self) -> bytes:
        return b'{"ok":true}'


class CommunityAccountDeletionCompletionReceiptTest(unittest.TestCase):
    def test_completion_receipt_is_user_safe(self) -> None:
        package = executor_package(
            {
                "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
                "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
            },
            "firebase-uid-123",
        )

        with mock.patch("tools.community_account_deletion_rest_executor.urllib.request.urlopen", return_value=FakeResponse()):
            rest_receipt = execute_package(
                package,
                "https://aura.firebaseio.com",
                mode="apply",
                access_token="token",
                confirm_request_code=package["requestCode"],
                confirm_plan_hash=package["planHash"],
                executed_at="2026-06-06T13:30:00Z",
            )

        completion = build_completion_receipt(
            package,
            rest_receipt,
            package["requestCode"],
            support_reference="ticket-123",
            completed_at="2026-06-06T13:45:00Z",
        )
        receipt_text = dump_completion_receipt(completion)

        self.assertEqual("completed", completion["completionStatus"])
        self.assertEqual("ticket-123", completion["supportReference"])
        self.assertEqual(2, completion["deletedRtdbMarkerCount"])
        self.assertEqual(package["requestCode"], completion["requestCode"])
        self.assertEqual(package["planHash"], completion["planHash"])
        self.assertNotIn("firebase-uid-123", receipt_text)
        self.assertNotIn("creator_profiles", receipt_text)
        self.assertNotIn("aura.firebaseio.com", receipt_text)
        self.assertNotIn("Bearer", receipt_text)

    def test_completion_receipt_rejects_dry_run_receipt(self) -> None:
        package = executor_package(
            {
                "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
                "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
            },
            "firebase-uid-123",
        )
        rest_receipt = execute_package(
            package,
            "https://aura.firebaseio.com",
            mode="dry-run",
            executed_at="2026-06-06T13:30:00Z",
        )

        with self.assertRaises(ReviewError):
            build_completion_receipt(package, rest_receipt, package["requestCode"], support_reference="ticket-123")

    def test_completion_receipt_rejects_rest_package_mismatch(self) -> None:
        package = executor_package(
            {
                "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
                "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
            },
            "firebase-uid-123",
        )

        with mock.patch("tools.community_account_deletion_rest_executor.urllib.request.urlopen", return_value=FakeResponse()):
            rest_receipt = execute_package(
                package,
                "https://aura.firebaseio.com",
                mode="apply",
                access_token="token",
                confirm_request_code=package["requestCode"],
                confirm_plan_hash=package["planHash"],
                executed_at="2026-06-06T13:30:00Z",
            )
        rest_receipt["packageHash"] = "bad"

        with self.assertRaises(ReviewError):
            build_completion_receipt(package, rest_receipt, package["requestCode"], support_reference="ticket-123")


if __name__ == "__main__":
    unittest.main()
