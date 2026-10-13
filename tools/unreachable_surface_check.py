#!/usr/bin/env python3
"""Fail when a navigation destination or a saved setting has no user path to it.

Two defect classes are checked:

* A ``Screen`` object registered as a ``composable`` destination that nothing
  navigates to (no ``createRoute`` style call, no ``Screen.X.route`` use outside
  the registration, no object reference such as a tab list entry).
* A ``PreferencesManager`` setter that no production code calls on a
  ``PreferencesManager`` receiver, or that only importers call. Such a setting can
  be read but never changed by the user.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


SCREEN_SOURCE = "app/src/main/java/com/freevibe/ui/navigation/Screen.kt"
PREFERENCES_SOURCE = "app/src/main/java/com/freevibe/data/local/PreferencesManager.kt"
MAIN_SOURCE_ROOT = "app/src/main/java"

# Files that only replay saved data. A setter reached from nowhere else can be
# restored from a backup but never changed in the app.
IMPORTER_SOURCES = frozenset({
    "com/freevibe/service/LibraryExporter.kt",
    "com/freevibe/service/ThemePackRecipeManager.kt",
})

# Setters allowed to have no user path, each with the reason it is still there.
SETTER_WAIVERS = {
    "setFreesoundKey": "Freesound is a dormant provider. The setter goes with it in the dormant sound provider removal.",
    "setRingtoneShuffleIntervalHours": "Legacy interval that the sound pool migration reads once. Only the migration tests write it.",
    "setRedditFeedPresetsJson": "Backup restore puts the raw preset document back after a failed import. Users edit presets through updateRedditFeedPresets.",
}

SCREEN_OBJECT = re.compile(r"(?:data\s+)?object\s+(\w+)\s*:\s*Screen\(")
REGISTRATION = re.compile(r"\b(?:composable|dialog)\(\s*(?:route\s*=\s*)?Screen\.(\w+)\.(?:route|destinationPattern)\b")
SETTER = re.compile(r"^\s*(?:suspend\s+)?fun\s+(set[A-Z]\w*)\s*\(", re.MULTILINE)
TYPED_RECEIVER = re.compile(r"\b(\w+)\s*:\s*(?:com\.freevibe\.data\.local\.)?PreferencesManager\b")
ENTRY_POINT_RECEIVER = re.compile(r"\bval\s+(\w+)\s*=.*\bpreferencesManager\(\)")


class UnreachableSurfaceError(ValueError):
    """Raised when a destination or a setting has no user path."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Fail on unreachable destinations and caller-less preference setters.")
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_text(repo_root: Path, relative_path: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise UnreachableSurfaceError(f"source is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def main_sources(repo_root: Path) -> dict[str, str]:
    root = repo_root / MAIN_SOURCE_ROOT
    if not root.is_dir():
        raise UnreachableSurfaceError(f"main source root is missing: {MAIN_SOURCE_ROOT}")
    return {
        path.relative_to(root).as_posix(): path.read_text(encoding="utf-8")
        for path in sorted(root.rglob("*.kt"))
    }


def unreachable_screens(screen_source: str, sources: dict[str, str]) -> list[str]:
    screens = SCREEN_OBJECT.findall(screen_source)
    if not screens:
        raise UnreachableSurfaceError("Screen.kt declares no Screen objects")
    screen_file = Path(SCREEN_SOURCE).relative_to(MAIN_SOURCE_ROOT).as_posix()
    registered: set[str] = set()
    navigation_refs: dict[str, int] = {name: 0 for name in screens}
    for relative_path, text in sources.items():
        if relative_path == screen_file:
            continue
        registrations = REGISTRATION.findall(text)
        registered.update(registrations)
        for name in screens:
            total = len(re.findall(r"\bScreen\.%s\b" % re.escape(name), text))
            navigation_refs[name] += total - registrations.count(name)
    return sorted(name for name in screens if name in registered and navigation_refs[name] <= 0)


def preference_receivers(text: str) -> set[str]:
    return set(TYPED_RECEIVER.findall(text)) | set(ENTRY_POINT_RECEIVER.findall(text))


def setter_callers(preferences_source: str, sources: dict[str, str]) -> dict[str, set[str]]:
    setters = SETTER.findall(preferences_source)
    if not setters:
        raise UnreachableSurfaceError("PreferencesManager declares no setters")
    preferences_file = Path(PREFERENCES_SOURCE).relative_to(MAIN_SOURCE_ROOT).as_posix()
    callers: dict[str, set[str]] = {name: set() for name in setters}
    for relative_path, text in sources.items():
        if relative_path == preferences_file:
            continue
        receivers = preference_receivers(text)
        if not receivers:
            continue
        receiver = "|".join(re.escape(name) for name in sorted(receivers))
        for name in setters:
            call = r"\b(?:%s)\s*\??\.\s*%s\s*\(|\b(?:%s)::%s\b" % (receiver, name, receiver, name)
            if re.search(call, text):
                callers[name].add(relative_path)
    return callers


def unreachable_setters(callers: dict[str, set[str]]) -> dict[str, str]:
    problems: dict[str, str] = {}
    for name, files in callers.items():
        if name in SETTER_WAIVERS:
            continue
        if not files:
            problems[name] = "no production caller"
        elif files <= IMPORTER_SOURCES:
            problems[name] = "only importers call it: " + ", ".join(sorted(files))
    return problems


def validate(repo_root: Path) -> dict[str, Any]:
    sources = main_sources(repo_root)
    screens = unreachable_screens(read_text(repo_root, SCREEN_SOURCE), sources)
    callers = setter_callers(read_text(repo_root, PREFERENCES_SOURCE), sources)
    stale_waivers = sorted(name for name in SETTER_WAIVERS if name not in callers)
    setters = unreachable_setters(callers)
    errors = []
    if screens:
        errors.append("destinations registered with no navigation to them: " + ", ".join(screens))
    if setters:
        errors.append("preference setters the user can't reach: " + "; ".join(f"{k} ({v})" for k, v in sorted(setters.items())))
    if stale_waivers:
        errors.append("waivers name setters PreferencesManager no longer declares: " + ", ".join(stale_waivers))
    if errors:
        raise UnreachableSurfaceError(". ".join(errors))
    return {
        "policyKind": "unreachableSurfaces",
        "schemaVersion": 1,
        "status": "ok",
        "setterCount": len(callers),
        "waivedSetters": sorted(SETTER_WAIVERS),
    }


def main() -> int:
    args = parse_args()
    try:
        result = validate(Path(args.repo_root))
    except UnreachableSurfaceError as error:
        print(json.dumps({"status": "fail", "error": str(error)}, indent=2, sort_keys=True))
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
