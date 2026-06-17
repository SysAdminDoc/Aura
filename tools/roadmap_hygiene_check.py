#!/usr/bin/env python3
"""Verify ROADMAP.md active backlog contains only incomplete items.

Rejects [x] completed checkmarks in active backlog sections (Researcher
Queue, Now, Next, Research-Driven Additions). Ignores historical reference
sections (State of the Repo, What is shipped, Shipped Inventory, Themes,
Risk Register, Implementation Log, Appendix, Later, Under Consideration,
Rejected).

Exit 0 if clean, 1 if violations found.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

COMPLETED_PATTERN = re.compile(r"^\s*-\s*\[x\]", re.IGNORECASE)

REFERENCE_SECTIONS = {
    "state of the repo",
    "what is shipped",
    "shipped inventory",
    "themes",
    "risk register",
    "implementation log",
    "appendix",
    "later",
    "under consideration",
    "rejected",
    "how to read this document",
}


def _is_reference_heading(heading_text: str) -> bool:
    lower = heading_text.lower().strip()
    for ref in REFERENCE_SECTIONS:
        if lower.startswith(ref):
            return True
    return False


def validate_roadmap(roadmap_path: Path) -> dict:
    if not roadmap_path.is_file():
        return {
            "status": "skip",
            "reason": f"{roadmap_path} not found",
            "violations": [],
        }

    violations: list[dict] = []
    lines = roadmap_path.read_text(encoding="utf-8").splitlines()
    in_reference_section = False

    for i, line in enumerate(lines, start=1):
        if line.startswith("## "):
            heading = line.lstrip("#").strip()
            heading = re.sub(r"\s*—.*", "", heading)
            in_reference_section = _is_reference_heading(heading)

        if in_reference_section:
            continue

        if COMPLETED_PATTERN.search(line):
            violations.append(
                {"line": i, "type": "completed_checkmark", "text": line.strip()[:120]}
            )

    return {
        "status": "pass" if not violations else "fail",
        "roadmap": str(roadmap_path),
        "violations": violations,
        "violation_count": len(violations),
    }


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument(
        "--repo-root",
        default=".",
        help="Repository root (default: cwd)",
    )
    p.add_argument(
        "--roadmap",
        default="ROADMAP.md",
        help="Roadmap file relative to repo root",
    )
    return p.parse_args()


def main() -> int:
    args = parse_args()
    roadmap_path = Path(args.repo_root) / args.roadmap
    result = validate_roadmap(roadmap_path)
    print(json.dumps(result, indent=2, sort_keys=True))
    if result["status"] == "fail":
        print(
            f"\n{result['violation_count']} violation(s) found in {args.roadmap}",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
