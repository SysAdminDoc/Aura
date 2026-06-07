#!/usr/bin/env python3
"""Scan release APKs for provider credential values from local.properties."""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path
from typing import Any

try:
    from .provider_credential_release_check import PROVIDER_KEYS, parse_properties
except ImportError:  # pragma: no cover - used when executed as a script.
    from provider_credential_release_check import PROVIDER_KEYS, parse_properties


class ProviderCredentialApkScanError(ValueError):
    """Raised when a provider credential value is found in a release APK."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scan Aura release APKs for provider credential values.")
    parser.add_argument("--local-properties", required=True)
    parser.add_argument("--apk", action="append", required=True, help="Release APK path to scan; repeatable.")
    return parser.parse_args()


def credential_values(local_properties: Path) -> list[dict[str, str]]:
    if not local_properties.is_file():
        raise ProviderCredentialApkScanError(f"local properties file is missing: {local_properties}")
    props = parse_properties(local_properties)
    credentials: list[dict[str, str]] = []
    for row in PROVIDER_KEYS:
        prop = row["property"]
        value = props.get(prop, "").strip()
        if value:
            credentials.append({"property": prop, "value": value})
    return credentials


def scan_apk(apk_path: Path, credentials: list[dict[str, str]]) -> list[dict[str, str]]:
    if not apk_path.is_file():
        raise ProviderCredentialApkScanError(f"release APK is missing: {apk_path}")

    matches: list[dict[str, str]] = []
    try:
        with zipfile.ZipFile(apk_path) as apk:
            for entry in apk.infolist():
                if entry.is_dir():
                    continue
                data = apk.read(entry)
                for credential in credentials:
                    if credential["value"].encode("utf-8") in data:
                        matches.append(
                            {
                                "apk": str(apk_path),
                                "entry": entry.filename,
                                "property": credential["property"],
                            }
                        )
    except zipfile.BadZipFile as exc:
        raise ProviderCredentialApkScanError(f"release APK is not a readable ZIP: {apk_path}") from exc
    return matches


def scan_release_apks(local_properties: Path, apk_paths: list[Path]) -> dict[str, Any]:
    credentials = credential_values(local_properties)
    matches: list[dict[str, str]] = []
    for apk_path in apk_paths:
        matches.extend(scan_apk(apk_path, credentials))

    if matches:
        summary = ", ".join(
            f"{match['property']} in {match['apk']}!{match['entry']}" for match in matches
        )
        raise ProviderCredentialApkScanError(f"provider credential values found in release APK: {summary}")

    return {
        "apkCount": len(apk_paths),
        "checkedProviderKeys": [credential["property"] for credential in credentials],
        "credentialValueCount": len(credentials),
        "status": "ok",
    }


def main() -> int:
    args = parse_args()
    try:
        result = scan_release_apks(
            Path(args.local_properties),
            [Path(path) for path in args.apk],
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
