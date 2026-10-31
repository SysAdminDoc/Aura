#!/usr/bin/env python3
"""Fail when a debug-only tool such as LeakCanary reaches a release APK."""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path
from typing import Any

# Dex stores descriptors and strings as MUTF-8, the binary manifest as UTF-16LE.
# R8 can rename the classes, but LeakCanary's manifest components and its strings stay.
DEBUG_TOOL_MARKERS = (("leakcanary", "LeakCanary"),)
SCANNED_ENTRY = re.compile(r"(?:classes\d*\.dex|AndroidManifest\.xml)")


class DebugToolApkScanError(ValueError):
    """Raised when a debug-only tool is found in a release APK."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scan Aura release APKs for debug-only tools.")
    parser.add_argument("--apk", action="append", required=True, help="Release APK path to scan; repeatable.")
    return parser.parse_args()


def scan_apk(apk_path: Path) -> list[dict[str, str]]:
    if not apk_path.is_file():
        raise DebugToolApkScanError(f"release APK is missing: {apk_path}")

    matches: list[dict[str, str]] = []
    try:
        with zipfile.ZipFile(apk_path) as apk:
            scanned = 0
            for entry in apk.infolist():
                name_lower = entry.filename.lower()
                for marker, tool in DEBUG_TOOL_MARKERS:
                    if marker in name_lower:
                        matches.append({"apk": str(apk_path), "entry": entry.filename, "tool": tool})
                if not SCANNED_ENTRY.fullmatch(entry.filename):
                    continue
                scanned += 1
                data = apk.read(entry)
                for marker, tool in DEBUG_TOOL_MARKERS:
                    if marker.encode("utf-8") in data or marker.encode("utf-16-le") in data:
                        matches.append({"apk": str(apk_path), "entry": entry.filename, "tool": tool})
    except zipfile.BadZipFile as exc:
        raise DebugToolApkScanError(f"release APK is not a readable ZIP: {apk_path}") from exc
    if scanned == 0:
        raise DebugToolApkScanError(f"no dex or manifest to scan in {apk_path}")
    return matches


def scan_release_apks(apk_paths: list[Path]) -> dict[str, Any]:
    matches: list[dict[str, str]] = []
    for apk_path in apk_paths:
        matches.extend(scan_apk(apk_path))

    if matches:
        summary = ", ".join(f"{match['tool']} in {match['apk']}!{match['entry']}" for match in matches)
        raise DebugToolApkScanError(f"debug-only tools found in release APK: {summary}")

    return {
        "apkCount": len(apk_paths),
        "checkedTools": [tool for _, tool in DEBUG_TOOL_MARKERS],
        "status": "ok",
    }


def main() -> int:
    args = parse_args()
    try:
        result = scan_release_apks([Path(path) for path in args.apk])
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
