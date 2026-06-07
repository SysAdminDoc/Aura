from __future__ import annotations

import json
import unittest
from unittest import mock

from tools.community_account_deletion_apply_simulator import simulate_account_deletion_apply
from tools.community_account_deletion_executor_package import build_executor_package
from tools.community_account_deletion_plan import build_account_deletion_plan
from tools.community_account_deletion_rest_executor import (
    ReviewError,
    database_endpoint,
    execute_package,
    patch_payload,
)
from tools.community_account_deletion_review import review_account_deletion_request
from tools.community_deletion_request_lookup import deletion_request_code, lookup_deletion_request


def executor_package(database_export: dict[str, object], uid: str) -> dict[str, object]:
    request_code = deletion_request_code(uid)
    plan = build_account_deletion_plan(database_export, uid)
    lookup = lookup_deletion_request(database_export, request_code)
    review = review_account_deletion_request(lookup, plan, request_code, reviewed_at="2026-06-06T12:00:00Z")
    simulation, _snapshot = simulate_account_deletion_apply(database_export, plan, review, simulated_at="2026-06-06T12:30:00Z")
    return build_executor_package(plan, review, simulation, request_code, operator="ops-1", packaged_at="2026-06-06T13:00:00Z")


class CommunityAccountDeletionRestExecutorTest(unittest.TestCase):
    def test_database_endpoint_allows_https_and_local_emulator(self) -> None:
        self.assertEqual("https://aura.firebaseio.com/.json", database_endpoint("https://aura.firebaseio.com/"))
        self.assertEqual("http://localhost:9000/.json", database_endpoint("http://localhost:9000"))

        with self.assertRaises(ReviewError):
            database_endpoint("http://example.com")

    def test_patch_payload_strips_leading_slashes_and_keeps_null_deletes(self) -> None:
        payload = patch_payload({"/creator_profiles/uid1": None})

        self.assertEqual({"creator_profiles/uid1": None}, payload)

    def test_dry_run_receipt_does_not_contact_firebase(self) -> None:
        package = executor_package(
            {
                "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
                "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
            },
            "firebase-uid-123",
        )

        with mock.patch("tools.community_account_deletion_rest_executor.urllib.request.urlopen") as urlopen:
            receipt = execute_package(
                package,
                "https://aura.firebaseio.com",
                mode="dry-run",
                executed_at="2026-06-06T13:30:00Z",
            )

        urlopen.assert_not_called()
        self.assertEqual("dryRun", receipt["executionStatus"])
        self.assertEqual("aura.firebaseio.com", receipt["databaseHost"])
        self.assertEqual(2, receipt["updateCount"])

    def test_apply_requires_matching_confirmations(self) -> None:
        package = executor_package(
            {
                "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
                "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
            },
            "firebase-uid-123",
        )

        with self.assertRaises(ReviewError):
            execute_package(
                package,
                "https://aura.firebaseio.com",
                mode="apply",
                access_token="token",
                confirm_request_code=package["requestCode"],
                confirm_plan_hash="bad",
            )

    def test_apply_sends_patch_with_bearer_token(self) -> None:
        package = executor_package(
            {
                "votes": {"content1": {"upvotes": 3, "voters": {"firebase-uid-123": True}}},
                "creator_profiles": {"firebase-uid-123": {"label": "Creator"}},
            },
            "firebase-uid-123",
        )

        class FakeResponse:
            status = 200

            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, *_args: object) -> None:
                return None

            def read(self) -> bytes:
                return b'{"ok":true}'

        with mock.patch("tools.community_account_deletion_rest_executor.urllib.request.urlopen", return_value=FakeResponse()) as urlopen:
            receipt = execute_package(
                package,
                "https://aura.firebaseio.com",
                mode="apply",
                access_token="token",
                confirm_request_code=package["requestCode"],
                confirm_plan_hash=package["planHash"],
                executed_at="2026-06-06T13:30:00Z",
            )

        request = urlopen.call_args.args[0]
        self.assertEqual("PATCH", request.method)
        self.assertEqual("Bearer token", request.headers["Authorization"])
        self.assertEqual("https://aura.firebaseio.com/.json", request.full_url)
        self.assertEqual("applied", receipt["executionStatus"])
        self.assertEqual(200, receipt["httpStatus"])
        self.assertEqual({"creator_profiles/firebase-uid-123": None, "votes/content1/voters/firebase-uid-123": None}, json.loads(request.data.decode("utf-8")))


if __name__ == "__main__":
    unittest.main()
