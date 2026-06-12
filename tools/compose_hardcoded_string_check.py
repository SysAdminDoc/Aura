#!/usr/bin/env python3
"""Validate Aura's Compose hardcoded-string localization baseline."""

from __future__ import annotations

import argparse
import ast
import hashlib
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


class ComposeHardcodedStringError(ValueError):
    """Raised when the hardcoded-string baseline is missing or stale."""


STRING_LITERAL_PATTERN = r'"(?:\\.|[^"\\])*"'
TEXT_CALL_RE = re.compile(r"\bText\s*\(\s*(?P<literal>" + STRING_LITERAL_PATTERN + r")", re.DOTALL)
CONTENT_DESCRIPTION_RE = re.compile(
    r"\bcontentDescription\s*=\s*(?P<literal>" + STRING_LITERAL_PATTERN + r")",
    re.DOTALL,
)
TOAST_RE = re.compile(
    r"\bToast\s*\.\s*makeText\s*\([^,\n]+,\s*(?P<literal>" + STRING_LITERAL_PATTERN + r")",
    re.DOTALL,
)
SNACKBAR_RE = re.compile(
    r"\bshowSnackbar\s*\(\s*(?P<literal>" + STRING_LITERAL_PATTERN + r")",
    re.DOTALL,
)
LABELLED_ARG_RE = re.compile(
    r"\b(?P<sink>label|title|text|placeholder|supportingText)\s*=\s*(?P<literal>"
    + STRING_LITERAL_PATTERN
    + r")",
    re.DOTALL,
)

SOURCE_SUFFIXES = {".kt", ".kts"}
DEFAULT_SCAN_ROOTS = ["app/src/main/java/com/freevibe/ui"]
DEFAULT_BASELINE = "docs/localization/hardcoded-string-baseline.json"
DEFAULT_IGNORED_PATH_FRAGMENTS = [
    "/build/",
    "/generated/",
    "/test/",
    "/androidTest/",
    "/debug/",
]
DEFAULT_MIGRATION_PLAN = {
    "targetResourceFile": "app/src/main/res/values/strings.xml",
    "nextSteps": [
        "Extract stable navigation labels, screen titles, and primary actions first.",
        "Replace dynamic status and error templates with resource placeholders.",
        "Keep locale generation disabled until pseudo-localization and RTL smoke checks pass.",
    ],
}


@dataclass(frozen=True)
class Finding:
    path: str
    sink: str
    text: str
    line: int

    @property
    def key(self) -> tuple[str, str, str]:
        return (self.path, self.sink, self.text)

    @property
    def id(self) -> str:
        payload = "|".join(self.key).encode("utf-8")
        return hashlib.sha256(payload).hexdigest()[:16]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura Compose hardcoded string baseline.")
    parser.add_argument("--baseline", default=DEFAULT_BASELINE)
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--mode", choices=["check", "write"], default="check")
    return parser.parse_args()


def decode_kotlin_string(literal: str) -> str:
    try:
        return ast.literal_eval(literal)
    except (SyntaxError, ValueError):
        return literal[1:-1]


def normalize_path(path: Path, repo_root: Path) -> str:
    return path.relative_to(repo_root).as_posix()


def line_number_for_offset(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def is_visible_literal(value: str) -> bool:
    stripped = value.strip()
    if not stripped:
        return False
    if re.fullmatch(r"[\W_]+", stripped):
        return False
    if stripped.startswith("http://") or stripped.startswith("https://"):
        return False
    return True


def iter_source_files(repo_root: Path, scan_roots: Iterable[str], ignored_path_fragments: Iterable[str]) -> list[Path]:
    files: list[Path] = []
    ignored = tuple(fragment.replace("\\", "/") for fragment in ignored_path_fragments)
    for root in scan_roots:
        root_path = repo_root / root
        if not root_path.exists():
            raise ComposeHardcodedStringError(f"scan source root is missing: {root}")
        candidates = [root_path] if root_path.is_file() else root_path.rglob("*")
        for path in candidates:
            relative = normalize_path(path, repo_root) if path.is_file() else ""
            if path.is_file() and path.suffix in SOURCE_SUFFIXES and not any(fragment in relative for fragment in ignored):
                files.append(path)
    return sorted(files)


def iter_file_findings(path: Path, repo_root: Path) -> list[Finding]:
    text = path.read_text(encoding="utf-8", errors="ignore")
    relative_path = normalize_path(path, repo_root)
    findings: list[Finding] = []
    patterns = [
        ("Text", TEXT_CALL_RE),
        ("contentDescription", CONTENT_DESCRIPTION_RE),
        ("Toast", TOAST_RE),
        ("Snackbar", SNACKBAR_RE),
    ]
    for sink, pattern in patterns:
        for match in pattern.finditer(text):
            value = decode_kotlin_string(match.group("literal"))
            if is_visible_literal(value):
                findings.append(
                    Finding(
                        path=relative_path,
                        sink=sink,
                        text=value,
                        line=line_number_for_offset(text, match.start("literal")),
                    )
                )

    for match in LABELLED_ARG_RE.finditer(text):
        value = decode_kotlin_string(match.group("literal"))
        if is_visible_literal(value):
            findings.append(
                Finding(
                    path=relative_path,
                    sink=match.group("sink"),
                    text=value,
                    line=line_number_for_offset(text, match.start("literal")),
                )
            )
    return findings


def find_hardcoded_strings(
    repo_root: Path,
    scan_roots: Iterable[str],
    ignored_path_fragments: Iterable[str],
) -> list[Finding]:
    findings: list[Finding] = []
    for path in iter_source_files(repo_root, scan_roots, ignored_path_fragments):
        findings.extend(iter_file_findings(path, repo_root))
    return sorted(findings, key=lambda item: (item.path, item.sink, item.text, item.line))


def summarize_findings(findings: Iterable[Finding]) -> list[dict[str, Any]]:
    counts: Counter[tuple[str, str, str]] = Counter(finding.key for finding in findings)
    first_lines: dict[tuple[str, str, str], int] = {}
    for finding in findings:
        first_lines.setdefault(finding.key, finding.line)
    entries: list[dict[str, Any]] = []
    for path, sink, text in sorted(counts):
        finding = Finding(path=path, sink=sink, text=text, line=first_lines[(path, sink, text)])
        entries.append(
            {
                "id": finding.id,
                "path": path,
                "sink": sink,
                "text": text,
                "count": counts[(path, sink, text)],
                "firstLine": finding.line,
            }
        )
    return entries


def write_baseline(repo_root: Path, baseline_path: Path) -> dict[str, Any]:
    scan_roots = DEFAULT_SCAN_ROOTS
    ignored_path_fragments = DEFAULT_IGNORED_PATH_FRAGMENTS
    findings = find_hardcoded_strings(repo_root, scan_roots, ignored_path_fragments)
    baseline = {
        "schemaVersion": 1,
        "policyKind": "composeHardcodedStringBaseline",
        "scanSourceRoots": scan_roots,
        "ignoredPathFragments": ignored_path_fragments,
        "allowedExistingStatus": "baselineGateActiveExtractionPending",
        "migrationPlan": DEFAULT_MIGRATION_PLAN,
        "baseline": summarize_findings(findings),
    }
    baseline_path.parent.mkdir(parents=True, exist_ok=True)
    baseline_path.write_text(json.dumps(baseline, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return baseline


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise ComposeHardcodedStringError(f"baseline file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ComposeHardcodedStringError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ComposeHardcodedStringError(f"{label} must be a non-empty string")
    return value.strip()


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ComposeHardcodedStringError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise ComposeHardcodedStringError(f"{label} contains duplicate values")
    return values


def require_baseline_entries(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        raise ComposeHardcodedStringError("baseline must be a JSON list")
    entries: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    for index, raw_entry in enumerate(value):
        entry = require_object(raw_entry, f"baseline[{index}]")
        entry_id = require_string(entry.get("id"), f"baseline[{index}].id")
        if entry_id in seen_ids:
            raise ComposeHardcodedStringError(f"baseline contains duplicate id: {entry_id}")
        seen_ids.add(entry_id)
        require_string(entry.get("path"), f"baseline[{index}].path")
        require_string(entry.get("sink"), f"baseline[{index}].sink")
        require_string(entry.get("text"), f"baseline[{index}].text")
        if not isinstance(entry.get("count"), int) or entry["count"] < 1:
            raise ComposeHardcodedStringError(f"baseline[{index}].count must be a positive integer")
        entries.append(entry)
    return entries


def validate_migration_plan(repo_root: Path, raw_plan: Any) -> dict[str, Any]:
    plan = require_object(raw_plan, "migrationPlan")
    target_resource_file = require_string(plan.get("targetResourceFile"), "migrationPlan.targetResourceFile")
    if not (repo_root / target_resource_file).is_file():
        raise ComposeHardcodedStringError(f"migrationPlan target resource file is missing: {target_resource_file}")
    next_steps = require_string_list(plan.get("nextSteps"), "migrationPlan.nextSteps")
    return {"targetResourceFile": target_resource_file, "nextSteps": next_steps}


def validate_baseline(repo_root: Path, baseline_path: Path) -> dict[str, Any]:
    baseline = require_object(read_json(baseline_path), "baseline file")
    if baseline.get("schemaVersion") != 1:
        raise ComposeHardcodedStringError("schemaVersion must be 1")
    if require_string(baseline.get("policyKind"), "policyKind") != "composeHardcodedStringBaseline":
        raise ComposeHardcodedStringError("policyKind must be composeHardcodedStringBaseline")
    if require_string(baseline.get("allowedExistingStatus"), "allowedExistingStatus") != (
        "baselineGateActiveExtractionPending"
    ):
        raise ComposeHardcodedStringError("allowedExistingStatus must be baselineGateActiveExtractionPending")

    scan_roots = require_string_list(baseline.get("scanSourceRoots"), "scanSourceRoots")
    ignored_path_fragments = require_string_list(baseline.get("ignoredPathFragments"), "ignoredPathFragments")
    migration_plan = validate_migration_plan(repo_root, baseline.get("migrationPlan"))
    expected_entries = require_baseline_entries(baseline.get("baseline"))
    actual_entries = summarize_findings(find_hardcoded_strings(repo_root, scan_roots, ignored_path_fragments))

    expected_by_id = {entry["id"]: entry for entry in expected_entries}
    actual_by_id = {entry["id"]: entry for entry in actual_entries}
    if len(actual_by_id) != len(actual_entries):
        raise ComposeHardcodedStringError("current scan produced duplicate ids")

    new_entries = [entry for entry in actual_entries if entry["id"] not in expected_by_id]
    removed_entries = [entry for entry in expected_entries if entry["id"] not in actual_by_id]
    changed_counts = [
        {
            "id": entry_id,
            "path": actual_by_id[entry_id]["path"],
            "sink": actual_by_id[entry_id]["sink"],
            "text": actual_by_id[entry_id]["text"],
            "baselineCount": expected_by_id[entry_id]["count"],
            "actualCount": actual_by_id[entry_id]["count"],
        }
        for entry_id in sorted(expected_by_id.keys() & actual_by_id.keys())
        if expected_by_id[entry_id]["count"] != actual_by_id[entry_id]["count"]
    ]
    if new_entries or removed_entries or changed_counts:
        details = {
            "newHardcodedStrings": new_entries,
            "removedOrExtractedStrings": removed_entries,
            "countChanges": changed_counts,
        }
        raise ComposeHardcodedStringError(
            "Compose hardcoded-string baseline drifted; run "
            "tools/compose_hardcoded_string_check.py --mode write after intentional extraction. "
            + json.dumps(details, indent=2, sort_keys=True)
        )

    return {
        "status": "ok",
        "baselineEntries": len(expected_entries),
        "currentFindings": sum(entry["count"] for entry in actual_entries),
        "migrationPlan": migration_plan,
        "scannedSourceRoots": scan_roots,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    baseline_path = (repo_root / args.baseline).resolve()
    try:
        if args.mode == "write":
            result = write_baseline(repo_root, baseline_path)
            output = {
                "status": "written",
                "baseline": str(baseline_path.relative_to(repo_root)),
                "baselineEntries": len(result["baseline"]),
                "currentFindings": sum(entry["count"] for entry in result["baseline"]),
            }
        else:
            output = validate_baseline(repo_root, baseline_path)
    except ComposeHardcodedStringError as exc:
        print(f"compose-hardcoded-string-check: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(output, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
