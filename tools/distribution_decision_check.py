#!/usr/bin/env python3
"""Hold the developer-verification decision record to its own claims.

Nothing read `docs/distribution/developer-verification.md`, so it drifted twice
without anyone noticing. It asserted that F-Droid mainline was "Blocked until a
real FOSS flavor removes or isolates Firebase" long after that flavor shipped,
and it told the reader to expect `fdroid_preflight.py` to print `blocked` while
the tool actually printed `ready-for-review`. A runbook that disagrees with the
command it tells you to run is worse than no runbook.

Three things are checked:

  * the decision carries a review date, and that date has not passed;
  * the status table names every channel Aura distributes through or is
    deciding about, so a new channel cannot be silently omitted;
  * the F-Droid status the doc tells the reader to expect is the status
    `fdroid_preflight.py` actually reports.

Exit 0 if clean, 1 if violations found.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import sys
from pathlib import Path

if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from tools.fdroid_preflight import analyze as analyze_fdroid_readiness

DOC_PATH = "docs/distribution/developer-verification.md"
REVIEW_RE = re.compile(r"\*\*Review by (\d{4}-\d{2}-\d{2})\.\*\*")
DECISION_RE = re.compile(r"\*\*Decision \((\d{4}-\d{2}-\d{2})\):\*\*")
EXPECTED_STATUS_RE = re.compile(r"Expected result: `F-Droid mainline status: ([a-z-]+)`")
REQUIRED_CHANNELS = ("F-Droid mainline", "IzzyOnDroid", "Accrescent")


class DistributionDecisionError(ValueError):
    """Raised when the distribution decision record is stale or self-contradictory."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate the developer-verification decision record.",
    )
    parser.add_argument("--repo-root", default=".")
    parser.add_argument(
        "--today",
        default=None,
        help="ISO date used for the review-date check; defaults to the system date.",
    )
    return parser.parse_args()


def validate_distribution_decision(
    repo_root: Path,
    today: dt.date,
    fdroid_status: str | None = None,
) -> dict[str, object]:
    """`fdroid_status` is injectable because fdroid_preflight.analyze() reads the
    real repository through module-level paths and cannot be pointed at a fixture."""
    path = repo_root / DOC_PATH
    if not path.is_file():
        raise DistributionDecisionError(f"{DOC_PATH} is missing")
    text = path.read_text(encoding="utf-8")

    decision = DECISION_RE.search(text)
    if not decision:
        raise DistributionDecisionError(
            f"{DOC_PATH} has no dated decision record; expected a '**Decision (YYYY-MM-DD):**' line"
        )

    review = REVIEW_RE.search(text)
    if not review:
        raise DistributionDecisionError(
            f"{DOC_PATH} records a decision but no review date; add a '**Review by YYYY-MM-DD.**' line"
        )
    review_by = dt.date.fromisoformat(review.group(1))
    if review_by < today:
        raise DistributionDecisionError(
            f"{DOC_PATH} was due for review on {review_by.isoformat()} and it is now "
            f"{today.isoformat()}; re-check the enforcement timetable and move the date"
        )

    missing = [channel for channel in REQUIRED_CHANNELS if f"| {channel} |" not in text]
    if missing:
        raise DistributionDecisionError(
            f"{DOC_PATH} status table is missing channels: " + ", ".join(missing)
        )

    expected = EXPECTED_STATUS_RE.search(text)
    if not expected:
        raise DistributionDecisionError(
            f"{DOC_PATH} does not state the F-Droid preflight status it expects"
        )
    actual = fdroid_status if fdroid_status is not None else str(analyze_fdroid_readiness()["status"])
    if expected.group(1) != actual:
        raise DistributionDecisionError(
            f"{DOC_PATH} tells the reader to expect F-Droid status "
            f"'{expected.group(1)}' but tools/fdroid_preflight.py reports '{actual}'"
        )

    return {
        "status": "ok",
        "policyKind": "distributionDecision",
        "schemaVersion": 1,
        "decisionDate": decision.group(1),
        "reviewBy": review_by.isoformat(),
        "fdroidStatus": actual,
        "channels": list(REQUIRED_CHANNELS),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    today = dt.date.fromisoformat(args.today) if args.today else dt.date.today()
    try:
        result = validate_distribution_decision(repo_root, today)
    except (DistributionDecisionError, OSError, ValueError) as exc:
        print(json.dumps({"status": "fail", "error": str(exc)}, indent=2, sort_keys=True))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
