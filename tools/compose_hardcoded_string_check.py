#!/usr/bin/env python3
"""Validate Aura's hardcoded user-facing string baseline.

Every remaining entry carries a reviewed reason, and write mode only ever shrinks the baseline:
a new literal has to be extracted to a string resource or added by hand with its reason.
"""

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
SEMANTICS_RE = re.compile(
    r"\b(?P<sink>stateDescription|onClickLabel|onLongClickLabel|paneTitle|actionLabel)\s*=\s*(?P<literal>"
    + STRING_LITERAL_PATTERN
    + r")",
    re.DOTALL,
)
ICON_DESCRIPTION_RE = re.compile(
    r"\b(?P<sink>Icon|Image)\s*\(\s*(?:[\w.]+|painterResource\([^()]*\))\s*,\s*(?P<literal>"
    + STRING_LITERAL_PATTERN
    + r")",
    re.DOTALL,
)
STATE_MESSAGE_RE = re.compile(
    r"\b(?P<sink>success|error|notice|message|warning|qualityWarning|fileUsageLabel"
    r"|[a-z]\w*(?:Success|Error|Notice|Message|Warning))\s*=\s*(?P<literal>"
    + STRING_LITERAL_PATTERN
    + r")",
    re.DOTALL,
)
EXCEPTION_RE = re.compile(
    r"(?:\b\w*Exception|(?<![.\w])error)\s*\(\s*(?P<literal>" + STRING_LITERAL_PATTERN + r")",
    re.DOTALL,
)
EDITOR_CONTROL_RE = re.compile(
    r"\b(?P<sink>EditorPreset|FilterControl|CustomAccessibilityAction)\s*\(\s*(?:label\s*=\s*)?(?P<literal>"
    + STRING_LITERAL_PATTERN
    + r")",
    re.DOTALL,
)
BLANK_DEFAULT_RE = re.compile(
    r"\bifBlank\s*\{\s*(?P<literal>" + STRING_LITERAL_PATTERN + r")",
    re.DOTALL,
)

# Plain functions that build spoken or visible text. Their prose literals are findings even when
# no Compose sink is in sight, since the result reaches TalkBack or a Text later.
TEXT_BUILDER_FUNCTION_RE = re.compile(
    r"^(?P<indent>[ \t]*)(?:(?:private|internal|public|protected|override)\s+)*fun\s+(?:[\w.<>?, ]+\.)?"
    r"(?P<name>\w*(?:Label|Summary|Description|Title|Text|Message|Caption|Announcement|Hint))\s*\(",
    re.MULTILINE,
)
# What ends a builder: the next declaration or closing brace at the builder's own indent.
DECLARATION_AT_INDENT = (
    r"(?:\}|@|(?:(?:private|internal|public|protected|override|const|companion|open|abstract|"
    r"inline|suspend|operator|lateinit)\s+)*(?:fun|val|var|class|object|enum|data|sealed|interface|init)\b)"
)
# A string built inline for a Text or a description, e.g. Text(buildString { append("...") }).
INLINE_BUILDER_RE = re.compile(
    r"\b(?P<sink>Text|contentDescription|text|stateDescription)\s*(?:\(|=)\s*(?:buildString|buildAnnotatedString)\s*\{"
)
# Where a lone lowercase word in a builder becomes text, versus a key it's compared against.
LONE_WORD_OUTPUT_BEFORE_RE = re.compile(r"(?:->|(?<![=!<>])=|\+|\?:|\belse|\breturn|\bappend\(|Of\(|,|\(|\))\s*$")
LONE_WORD_KEY_AFTER_RE = re.compile(r"\s*(?:,\s*\"[^\"]*\"\s*)*->")
LONE_WORD_KEY_BEFORE_RE = re.compile(
    r"(?:==|!=|\b(?:equals|contains|startsWith|endsWith|removePrefix|removeSuffix|split|replace|"
    r"substringBefore|substringAfter|get|getString|optString|optInt|optBoolean|has|key|Regex)\()\s*$"
)
STRING_LITERAL_RE = re.compile(STRING_LITERAL_PATTERN)
FORMAT_OR_TEMPLATE_RE = re.compile(r"\$\{[^}]*\}|\$\w+|%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")

SOURCE_SUFFIXES = {".kt", ".kts"}
DEFAULT_SCAN_ROOTS = [
    "app/src/main/java/com/freevibe/ui",
    "app/src/full/java/com/freevibe/ui",
]
DEFAULT_BASELINE = "docs/localization/hardcoded-string-baseline.json"
DEFAULT_IGNORED_PATH_FRAGMENTS = [
    "/build/",
    "/generated/",
    "/test/",
    "/androidTest/",
    "/debug/",
    "/ui/preview/",
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


def is_prose_literal(value: str) -> bool:
    """A word a person reads: a lowercase letter pair, and a space or a capitalized word.

    Unit and format tokens like MB, QHD or %.1f stay out, as do URLs and bare template fragments.
    """
    stripped = FORMAT_OR_TEMPLATE_RE.sub("", value).strip()
    if stripped.startswith(("http://", "https://")):
        return False
    if not re.search(r"[a-z]{2,}", stripped):
        return False
    return " " in stripped or bool(re.match(r"[A-Z][a-z]", stripped))


def is_lone_output_word(text: str, start: int, end: int, value: str) -> bool:
    """A single lowercase word like "selected" that a builder puts into its text.

    The same word compared against, used as a when-branch key or looked up stays out.
    """
    if not re.fullmatch(r"[a-z]{3,}", FORMAT_OR_TEMPLATE_RE.sub("", value).strip()):
        return False
    line_start = text.rfind("\n", 0, start) + 1
    line_end = text.find("\n", end)
    before = text[line_start:start]
    after = text[end : line_end if line_end != -1 else len(text)]
    if LONE_WORD_KEY_AFTER_RE.match(after) or LONE_WORD_KEY_BEFORE_RE.search(before):
        return False
    return bool(LONE_WORD_OUTPUT_BEFORE_RE.search(before)) or not before.strip()


def block_end(text: str, open_brace: int) -> int:
    """Offset just past the brace that closes the block opening at open_brace."""
    depth = 0
    for offset in range(open_brace, len(text)):
        if text[offset] == "{":
            depth += 1
        elif text[offset] == "}":
            depth -= 1
            if depth == 0:
                return offset + 1
    return len(text)


def text_builder_ranges(text: str) -> list[tuple[int, int]]:
    """Signature and body of each non-Composable text builder, top level or inside a class."""
    ranges: list[tuple[int, int]] = []
    for match in TEXT_BUILDER_FUNCTION_RE.finditer(text):
        if "@Composable" in text[max(0, match.start() - 40) : match.start()]:
            continue
        indent = re.escape(match.group("indent"))
        end = re.compile(rf"^{indent}{DECLARATION_AT_INDENT}", re.MULTILINE).search(text, match.end())
        ranges.append((match.end(), end.start() if end else len(text)))
    return ranges


def is_compose_animation_label(source: str, offset: int) -> bool:
    context = source[max(0, offset - 500) : offset]
    return any(
        token in context
        for token in (
            "rememberInfiniteTransition(",
            "animateFloat(",
            "animateFloatAsState(",
            "animateDpAsState(",
            "animateColorAsState(",
            "updateTransition(",
        )
    )


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
        sink = match.group("sink")
        if sink == "label" and is_compose_animation_label(text, match.start()):
            continue
        if is_visible_literal(value):
            findings.append(
                Finding(
                    path=relative_path,
                    sink=sink,
                    text=value,
                    line=line_number_for_offset(text, match.start("literal")),
                )
            )

    for pattern, default_sink in (
        (SEMANTICS_RE, ""),
        (ICON_DESCRIPTION_RE, ""),
        (STATE_MESSAGE_RE, ""),
        (EDITOR_CONTROL_RE, ""),
        (BLANK_DEFAULT_RE, "ifBlank"),
        (EXCEPTION_RE, "exception"),
    ):
        for match in pattern.finditer(text):
            value = decode_kotlin_string(match.group("literal"))
            if is_visible_literal(value):
                findings.append(
                    Finding(
                        path=relative_path,
                        sink=match.groupdict().get("sink") or default_sink,
                        text=value,
                        line=line_number_for_offset(text, match.start("literal")),
                    )
                )

    claimed = {(finding.line, finding.text) for finding in findings}
    builders = [(start, end, "textBuilder") for start, end in text_builder_ranges(text)]
    builders += [
        (match.end(), block_end(text, match.end() - 1), match.group("sink"))
        for match in INLINE_BUILDER_RE.finditer(text)
    ]
    for start, end, sink in builders:
        for match in STRING_LITERAL_RE.finditer(text, start, end):
            value = decode_kotlin_string(match.group(0))
            line = line_number_for_offset(text, match.start())
            if (line, value) in claimed:
                continue
            if not is_prose_literal(value) and not is_lone_output_word(text, match.start(), match.end(), value):
                continue
            claimed.add((line, value))
            findings.append(Finding(path=relative_path, sink=sink, text=value, line=line))
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
    """Prune extracted strings and lower counts. Never adds an entry or raises a count."""
    existing: dict[str, Any] = {}
    if baseline_path.is_file():
        loaded = json.loads(baseline_path.read_text(encoding="utf-8"))
        if not isinstance(loaded, dict):
            raise ComposeHardcodedStringError("baseline file must contain a JSON object")
        existing = loaded
    scan_roots = existing.get("scanSourceRoots") or DEFAULT_SCAN_ROOTS
    ignored_path_fragments = existing.get("ignoredPathFragments") or DEFAULT_IGNORED_PATH_FRAGMENTS
    actual_entries = summarize_findings(find_hardcoded_strings(repo_root, scan_roots, ignored_path_fragments))
    known = {
        entry.get("id"): entry
        for entry in existing.get("baseline", [])
        if isinstance(entry, dict) and entry.get("id")
    }
    growth = [
        {key: entry[key] for key in ("path", "sink", "text", "count", "firstLine")}
        for entry in actual_entries
        if entry["id"] not in known or entry["count"] > known[entry["id"]].get("count", 0)
    ]
    if growth:
        raise ComposeHardcodedStringError(
            "write mode only shrinks the baseline. Move these strings to resources, or add each entry "
            "by hand with a reason: " + json.dumps(growth, indent=2, sort_keys=True)
        )
    baseline = {
        **existing,
        "schemaVersion": 1,
        "policyKind": "composeHardcodedStringBaseline",
        "scanSourceRoots": scan_roots,
        "ignoredPathFragments": ignored_path_fragments,
        "allowedExistingStatus": "baselineGateActiveExtractionPending",
        "migrationPlan": existing.get("migrationPlan", DEFAULT_MIGRATION_PLAN),
        "baseline": [{**entry, "reason": known[entry["id"]].get("reason", "")} for entry in actual_entries],
    }
    baseline_path.parent.mkdir(parents=True, exist_ok=True)
    baseline_path.write_text(
        json.dumps(baseline, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )
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
        require_string(entry.get("reason"), f"baseline[{index}].reason")
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
            "hardcoded-string baseline drifted. Move new strings to resources; after an extraction, "
            "tools/compose_hardcoded_string_check.py --mode write prunes the baseline. "
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
