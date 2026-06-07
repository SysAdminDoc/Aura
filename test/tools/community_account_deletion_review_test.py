from __future__ import annotations

import json
import unittest

from tools.community_account_deletion_plan import sanitize_key
from tools.community_account_deletion_review import (
    ReviewError,
    redact_uid_key,
    review_account_deletion_request,
)
from tools.community_deletion_request_lookup import deletion_request_code


def sample_artifacts(uid: str = "firebase-uid-123") -> tuple[dict[str, object], dict[str, object], str]:
    safe_uid = sanitize_key(uid)
    request_code = deletion_request_code(uid)
    lookup = {
        "schemaVersion": 1,
        "requestCode": request_code,
        "candidateCount": 3,
        "matchCount": 1,
        "matches": [
            {
                "uid": uid,
                "safeUid": safe_uid,
                "requestCode": request_code,
                "evidence": [
                    f"/creator_profiles/{safe_uid}",
                    f"/votes/content1/voters/{safe_uid}",
                ],
            }
        ],
    }
    plan = {
        "schemaVersion": 1,
        "uid": safe_uid,
        "updateCount": 2,
        "updates": {
            f"/creator_profiles/{safe_uid}": None,
            f"/votes/content1/voters/{safe_uid}": None,
        },
        "categories": {
            "voteMarkers": [f"/votes/content1/voters/{safe_uid}"],
            "creatorFollows": [],
            "creatorProfile": [f"/creator_profiles/{safe_uid}"],
            "communityBlocks": [],
            "collectionShares": [],
        },
        "retained": [
            {
                "root": "/votes/*/upvotes",
                "reason": "Aggregate counts are retained.",
            },
            {
                "root": "/community_reports and moderation audit roots",
                "reason": "Moderation records are retained.",
            },
            {
                "root": "/community_sounds, /community_wallpapers, /owner_uploads, and Storage objects",
                "reason": "Uploads require owner/admin deletion.",
            },
        ],
    }
    return lookup, plan, request_code


class CommunityAccountDeletionReviewTest(unittest.TestCase):
    def test_review_emits_redacted_receipt_for_matching_artifacts(self) -> None:
        lookup, plan, request_code = sample_artifacts()

        review = review_account_deletion_request(
            lookup,
            plan,
            request_code,
            reviewed_at="2026-06-06T12:00:00Z",
        )

        self.assertEqual("readyForTrustedApply", review["reviewStatus"])
        self.assertEqual(request_code, review["requestCode"])
        self.assertEqual(2, review["updateCount"])
        self.assertEqual(2, review["evidenceCount"])
        self.assertEqual(1, review["categoryCounts"]["voteMarkers"])
        self.assertEqual(64, len(review["planHash"]))
        self.assertEqual(64, len(review["lookupEvidenceHash"]))
        self.assertEqual("...id-123", review["uidKeySuffix"])
        self.assertNotIn("firebase-uid-123", json.dumps(review))

    def test_redact_uid_key_uses_suffix_only(self) -> None:
        self.assertEqual("...uid123", redact_uid_key("firebase-uid123"))
        self.assertEqual("...short", redact_uid_key("short"))
        self.assertEqual("", redact_uid_key(" "))

    def test_review_rejects_ambiguous_lookup(self) -> None:
        lookup, plan, request_code = sample_artifacts()
        lookup["matchCount"] = 2

        with self.assertRaises(ReviewError):
            review_account_deletion_request(lookup, plan, request_code)

    def test_review_rejects_plan_uid_mismatch(self) -> None:
        lookup, plan, request_code = sample_artifacts()
        plan["uid"] = "other-user"

        with self.assertRaises(ReviewError):
            review_account_deletion_request(lookup, plan, request_code)

    def test_review_rejects_non_null_updates(self) -> None:
        lookup, plan, request_code = sample_artifacts()
        plan["updates"] = {"/creator_profiles/firebase-uid-123": {"deleted": True}}
        plan["updateCount"] = 1

        with self.assertRaises(ReviewError):
            review_account_deletion_request(lookup, plan, request_code)

    def test_review_rejects_uncategorized_updates(self) -> None:
        lookup, plan, request_code = sample_artifacts()
        plan["updates"]["/creator_follows/firebase-uid-123"] = None
        plan["updateCount"] = 3

        with self.assertRaises(ReviewError):
            review_account_deletion_request(lookup, plan, request_code)

    def test_review_rejects_missing_retained_roots(self) -> None:
        lookup, plan, request_code = sample_artifacts()
        plan["retained"] = []

        with self.assertRaises(ReviewError):
            review_account_deletion_request(lookup, plan, request_code)


if __name__ == "__main__":
    unittest.main()
