#!/usr/bin/env python3
"""Validate Aura's Dependabot update configuration."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


EXPECTED_UPDATES = {
    ("github-actions", "/"): "09:30",
    ("gradle", "/"): "10:00",
    ("npm", "/"): "10:30",
    ("npm", "/functions"): "11:00",
}
REQUIRED_LABELS = {"dependencies", "security"}


class DependabotConfigError(ValueError):
    """Raised when Dependabot configuration validation fails."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura Dependabot configuration.")
    parser.add_argument("--config", default=".github/dependabot.yml")
    return parser.parse_args()


def parse_scalar(raw_value: str) -> str:
    value = raw_value.strip()
    if (
        len(value) >= 2
        and ((value[0] == value[-1] == '"') or (value[0] == value[-1] == "'"))
    ):
        return value[1:-1]
    return value


def parse_key_value(text: str, label: str) -> tuple[str, str]:
    if ":" not in text:
        raise DependabotConfigError(f"{label} must be a key/value entry")
    key, value = text.split(":", 1)
    key = key.strip()
    if not key:
        raise DependabotConfigError(f"{label} key must not be empty")
    return key, parse_scalar(value)


def parse_dependabot_config(text: str) -> dict[str, Any]:
    if "\t" in text:
        raise DependabotConfigError("Dependabot config must not contain tabs")

    version: str | None = None
    updates: list[dict[str, Any]] = []
    current_update: dict[str, Any] | None = None
    nested_object: str | None = None
    current_list: str | None = None
    in_updates = False

    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue

        if raw_line.startswith("version:"):
            version = parse_scalar(raw_line.split(":", 1)[1])
            continue

        if raw_line == "updates:":
            in_updates = True
            continue

        if not in_updates:
            raise DependabotConfigError(f"Unsupported top-level entry on line {line_number}: {raw_line}")

        if raw_line.startswith("  - "):
            current_update = {}
            updates.append(current_update)
            nested_object = None
            current_list = None
            remainder = raw_line[4:].strip()
            if remainder:
                key, value = parse_key_value(remainder, f"line {line_number}")
                current_update[key] = value
            continue

        if current_update is None:
            raise DependabotConfigError(f"Dependabot update entry missing before line {line_number}")

        if raw_line.startswith("    ") and not raw_line.startswith("      "):
            entry = raw_line[4:].strip()
            if entry.endswith(":"):
                key = entry[:-1].strip()
                if key == "labels":
                    current_update[key] = []
                    current_list = key
                    nested_object = None
                else:
                    current_update[key] = {}
                    nested_object = key
                    current_list = None
                continue
            key, value = parse_key_value(entry, f"line {line_number}")
            current_update[key] = value
            nested_object = None
            current_list = None
            continue

        if raw_line.startswith("      - "):
            if not current_list:
                raise DependabotConfigError(f"Unexpected list item on line {line_number}")
            current_update[current_list].append(parse_scalar(raw_line[8:]))
            continue

        if raw_line.startswith("      "):
            if not nested_object:
                raise DependabotConfigError(f"Unexpected nested entry on line {line_number}")
            key, value = parse_key_value(raw_line[6:].strip(), f"line {line_number}")
            current_update[nested_object][key] = value
            continue

        raise DependabotConfigError(f"Unsupported indentation on line {line_number}: {raw_line}")

    return {"version": version, "updates": updates}


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value:
        raise DependabotConfigError(f"{label} must be a non-empty string")
    return value


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise DependabotConfigError(f"{label} must be an object")
    return value


def validate_update(update: dict[str, Any], index: int) -> tuple[str, str]:
    ecosystem = require_string(update.get("package-ecosystem"), f"updates[{index}].package-ecosystem")
    directory = require_string(update.get("directory"), f"updates[{index}].directory")
    key = (ecosystem, directory)
    if key not in EXPECTED_UPDATES:
        raise DependabotConfigError(f"Unexpected Dependabot update entry: {ecosystem} {directory}")

    if require_string(update.get("target-branch"), f"{ecosystem} {directory} target-branch") != "main":
        raise DependabotConfigError(f"{ecosystem} {directory} must target main")

    schedule = require_object(update.get("schedule"), f"{ecosystem} {directory} schedule")
    if require_string(schedule.get("interval"), f"{ecosystem} {directory} schedule.interval") != "weekly":
        raise DependabotConfigError(f"{ecosystem} {directory} schedule interval must be weekly")
    if require_string(schedule.get("day"), f"{ecosystem} {directory} schedule.day") != "monday":
        raise DependabotConfigError(f"{ecosystem} {directory} schedule day must be monday")
    if require_string(schedule.get("timezone"), f"{ecosystem} {directory} schedule.timezone") != "America/New_York":
        raise DependabotConfigError(f"{ecosystem} {directory} schedule timezone must be America/New_York")
    time = require_string(schedule.get("time"), f"{ecosystem} {directory} schedule.time")
    if time != EXPECTED_UPDATES[key] or not re.fullmatch(r"[0-2][0-9]:[0-5][0-9]", time):
        raise DependabotConfigError(f"{ecosystem} {directory} schedule time must be {EXPECTED_UPDATES[key]}")

    raw_limit = require_string(update.get("open-pull-requests-limit"), f"{ecosystem} {directory} open PR limit")
    try:
        limit = int(raw_limit)
    except ValueError as exc:
        raise DependabotConfigError(f"{ecosystem} {directory} open PR limit must be an integer") from exc
    if limit < 1 or limit > 5:
        raise DependabotConfigError(f"{ecosystem} {directory} open PR limit must be between 1 and 5")

    commit_message = require_object(update.get("commit-message"), f"{ecosystem} {directory} commit-message")
    if require_string(commit_message.get("prefix"), f"{ecosystem} {directory} commit prefix") != "deps":
        raise DependabotConfigError(f"{ecosystem} {directory} commit prefix must be deps")

    labels = update.get("labels")
    if not isinstance(labels, list):
        raise DependabotConfigError(f"{ecosystem} {directory} labels must be a list")
    if not REQUIRED_LABELS.issubset(set(labels)):
        raise DependabotConfigError(f"{ecosystem} {directory} labels must include dependencies and security")

    return key


def validate_dependabot_config(path: Path) -> dict[str, Any]:
    parsed = parse_dependabot_config(path.read_text(encoding="utf-8"))
    if parsed.get("version") != "2":
        raise DependabotConfigError("Dependabot config version must be 2")
    updates = parsed.get("updates")
    if not isinstance(updates, list) or not updates:
        raise DependabotConfigError("Dependabot config updates must be a non-empty list")

    seen: set[tuple[str, str]] = set()
    for index, raw_update in enumerate(updates):
        update = require_object(raw_update, f"updates[{index}]")
        key = validate_update(update, index)
        if key in seen:
            raise DependabotConfigError(f"Duplicate Dependabot update entry: {key[0]} {key[1]}")
        seen.add(key)

    missing = sorted(set(EXPECTED_UPDATES) - seen)
    if missing:
        formatted = ", ".join(f"{ecosystem} {directory}" for ecosystem, directory in missing)
        raise DependabotConfigError(f"Missing Dependabot update entries: {formatted}")

    return {
        "config": str(path),
        "status": "ok",
        "updateCount": len(updates),
        "updates": [
            {"packageEcosystem": ecosystem, "directory": directory, "time": EXPECTED_UPDATES[(ecosystem, directory)]}
            for ecosystem, directory in sorted(seen)
        ],
    }


def main() -> int:
    args = parse_args()
    try:
        result = validate_dependabot_config(Path(args.config))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
