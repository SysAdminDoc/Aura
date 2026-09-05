from __future__ import annotations

import datetime as dt
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.distribution_decision_check import (
    DOC_PATH,
    DistributionDecisionError,
    validate_distribution_decision,
)


REPO_ROOT = Path(__file__).resolve().parents[2]
LIVE_FDROID_STATUS = "ready-for-review"
BEFORE_REVIEW = dt.date(2026, 9, 5)


class DistributionDecisionCheckTest(unittest.TestCase):
    def _fixture(self, mutate=None) -> Path:
        tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(tmpdir.cleanup)
        root = Path(tmpdir.name)
        target = root / DOC_PATH
        target.parent.mkdir(parents=True, exist_ok=True)
        text = (REPO_ROOT / DOC_PATH).read_text(encoding="utf-8")
        target.write_text(mutate(text) if mutate else text, encoding="utf-8")
        return root

    def test_live_decision_record_is_current(self) -> None:
        result = validate_distribution_decision(REPO_ROOT, BEFORE_REVIEW)

        self.assertEqual("ok", result["status"])
        self.assertEqual(LIVE_FDROID_STATUS, result["fdroidStatus"])
        self.assertEqual("2026-07-16", result["decisionDate"])

    def test_rejects_a_review_date_that_has_passed(self) -> None:
        root = self._fixture()

        with self.assertRaises(DistributionDecisionError) as ctx:
            validate_distribution_decision(
                root, dt.date(2027, 1, 1), fdroid_status=LIVE_FDROID_STATUS
            )

        message = str(ctx.exception)
        self.assertIn("due for review on 2026-12-31", message)
        self.assertIn("2027-01-01", message)

    def test_accepts_the_review_date_on_its_last_day(self) -> None:
        root = self._fixture()

        result = validate_distribution_decision(
            root, dt.date(2026, 12, 31), fdroid_status=LIVE_FDROID_STATUS
        )

        self.assertEqual("ok", result["status"])

    def test_rejects_a_decision_with_no_review_date(self) -> None:
        root = self._fixture(
            lambda text: text.replace("**Review by 2026-12-31.**", "Reviewed whenever.")
        )

        with self.assertRaises(DistributionDecisionError) as ctx:
            validate_distribution_decision(root, BEFORE_REVIEW, fdroid_status=LIVE_FDROID_STATUS)

        self.assertIn("no review date", str(ctx.exception))

    def test_rejects_a_doc_with_no_dated_decision(self) -> None:
        root = self._fixture(
            lambda text: text.replace("**Decision (2026-07-16):**", "Decision:")
        )

        with self.assertRaises(DistributionDecisionError) as ctx:
            validate_distribution_decision(root, BEFORE_REVIEW, fdroid_status=LIVE_FDROID_STATUS)

        self.assertIn("no dated decision record", str(ctx.exception))

    def test_rejects_a_status_table_missing_a_channel(self) -> None:
        root = self._fixture(lambda text: text.replace("| Accrescent |", "| Somewhere else |"))

        with self.assertRaises(DistributionDecisionError) as ctx:
            validate_distribution_decision(root, BEFORE_REVIEW, fdroid_status=LIVE_FDROID_STATUS)

        self.assertIn("missing channels: Accrescent", str(ctx.exception))

    def test_rejects_an_expected_fdroid_status_the_tool_does_not_report(self) -> None:
        # The exact drift this gate was written for: the doc said `blocked`
        # while fdroid_preflight.py reported `ready-for-review`.
        root = self._fixture(
            lambda text: text.replace(
                "Expected result: `F-Droid mainline status: ready-for-review`",
                "Expected result: `F-Droid mainline status: blocked`",
            )
        )

        with self.assertRaises(DistributionDecisionError) as ctx:
            validate_distribution_decision(root, BEFORE_REVIEW, fdroid_status="ready-for-review")

        message = str(ctx.exception)
        self.assertIn("expect F-Droid status 'blocked'", message)
        self.assertIn("reports 'ready-for-review'", message)

    def test_rejects_a_doc_that_states_no_expected_status(self) -> None:
        root = self._fixture(
            lambda text: text.replace("Expected result: `F-Droid mainline status:", "Result: `whatever:")
        )

        with self.assertRaises(DistributionDecisionError) as ctx:
            validate_distribution_decision(root, BEFORE_REVIEW, fdroid_status=LIVE_FDROID_STATUS)

        self.assertIn("does not state the F-Droid preflight status", str(ctx.exception))

    def test_a_missing_doc_is_an_error(self) -> None:
        tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(DistributionDecisionError) as ctx:
            validate_distribution_decision(
                Path(tmpdir.name), BEFORE_REVIEW, fdroid_status=LIVE_FDROID_STATUS
            )

        self.assertIn("is missing", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
