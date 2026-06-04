#!/usr/bin/env python3
"""Report Aura's current F-Droid mainline readiness without building APKs."""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
APP_GRADLE = ROOT / "app" / "build.gradle.kts"
SETTINGS_GRADLE = ROOT / "settings.gradle.kts"


@dataclass(frozen=True)
class Finding:
    file: str
    line: int
    label: str
    text: str


BLOCKERS = (
    (
        "Google Services Gradle plugin",
        re.compile(r"com\.google\.gms\.google-services"),
    ),
    (
        "Firebase dependency",
        re.compile(r"com\.google\.firebase:firebase-[A-Za-z0-9_.-]+|firebase-bom"),
    ),
    (
        "Google Play Services dependency",
        re.compile(r"com\.google\.android\.gms:play-services-[A-Za-z0-9_.-]+"),
    ),
)


def read_lines(path: Path) -> list[str]:
    if not path.exists():
        return []
    return path.read_text(encoding="utf-8").splitlines()


def scan_blockers(path: Path) -> list[Finding]:
    findings: list[Finding] = []
    for line_no, line in enumerate(read_lines(path), start=1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("#"):
            continue
        for label, pattern in BLOCKERS:
            if pattern.search(line):
                findings.append(
                    Finding(
                        file=str(path.relative_to(ROOT)).replace("\\", "/"),
                        line=line_no,
                        label=label,
                        text=stripped,
                    )
                )
    return findings


def has_product_flavors(path: Path) -> bool:
    return any("productFlavors" in line for line in read_lines(path))


def analyze() -> dict[str, object]:
    blockers = scan_blockers(APP_GRADLE)
    product_flavors = has_product_flavors(APP_GRADLE)
    status = "blocked" if blockers or not product_flavors else "ready-for-review"
    notes: list[str] = []

    if not product_flavors:
        notes.append("No productFlavors block found; Aura currently has one full-feature app variant.")
    if blockers:
        notes.append("Current app variant includes Firebase and/or Google Play Services dependencies.")
    if status == "blocked":
        notes.append("Do not open an F-Droid mainline metadata PR until these blockers are removed or isolated.")

    return {
        "status": status,
        "decision": "full-only-for-now" if status == "blocked" else "foss-review-ready",
        "productFlavors": product_flavors,
        "blockers": [asdict(item) for item in blockers],
        "notes": notes,
        "scanned": [
            str(APP_GRADLE.relative_to(ROOT)).replace("\\", "/"),
            str(SETTINGS_GRADLE.relative_to(ROOT)).replace("\\", "/"),
        ],
    }


def print_text(report: dict[str, object]) -> None:
    print(f"F-Droid mainline status: {report['status']}")
    print(f"Distribution decision: {report['decision']}")
    for note in report["notes"]:
        print(f"- {note}")
    blockers = report["blockers"]
    if blockers:
        print("\nBlockers:")
        for item in blockers:
            print(f"- {item['file']}:{item['line']} [{item['label']}] {item['text']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Print machine-readable JSON.")
    parser.add_argument(
        "--expect-blocked",
        action="store_true",
        help="Exit 0 only when the current tree is blocked for F-Droid mainline.",
    )
    args = parser.parse_args()

    report = analyze()
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_text(report)

    blocked = report["status"] == "blocked"
    if args.expect_blocked:
        return 0 if blocked else 1
    return 2 if blocked else 0


if __name__ == "__main__":
    raise SystemExit(main())
