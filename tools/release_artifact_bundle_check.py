#!/usr/bin/env python3
"""Validate the final release artifact bundle before upload or publication."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path


REQUIRED_STATIC_FILES = {
    "THIRD-PARTY-NOTICES.md",
    "GOOGLE-OSS-RAW-INPUTS.zip",
    "NATIVE-COMPLIANCE.md",
    "SHA256SUMS.txt",
    "RELEASE_NOTES.md",
    "apksigner.txt",
    "aapt-badging.txt",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate an Aura release artifact directory."
    )
    parser.add_argument("--release-dir", default="release")
    parser.add_argument("--apk-name", required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True)
    return parser.parse_args()


def read_text(path: Path) -> str:
    if not path.is_file():
        raise FileNotFoundError(f"Required file not found: {path}")
    return path.read_text(encoding="utf-8", errors="replace")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_sha256sums(path: Path) -> dict[str, str]:
    entries: dict[str, str] = {}
    for line_number, raw_line in enumerate(read_text(path).splitlines(), start=1):
        line = raw_line.strip()
        if not line:
            continue
        match = re.fullmatch(r"([0-9a-fA-F]{64})\s+\*?(.+)", line)
        if not match:
            raise ValueError(f"{path}:{line_number}: invalid SHA256SUMS entry")
        digest, file_name = match.groups()
        if file_name in entries:
            raise ValueError(f"{path}:{line_number}: duplicate checksum for {file_name}")
        entries[file_name] = digest.lower()
    return entries


def require_non_empty(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"Required file not found: {path}")
    if path.stat().st_size <= 0:
        raise ValueError(f"Required file is empty: {path}")


def validate_bundle(
    *,
    release_dir: Path,
    apk_name: str,
    version_name: str,
    version_code: str,
) -> list[str]:
    errors: list[str] = []
    expected_files = set(REQUIRED_STATIC_FILES)
    expected_files.add(apk_name)

    if not release_dir.is_dir():
        return [f"Release directory not found: {release_dir}"]

    for file_name in sorted(expected_files):
        path = release_dir / file_name
        try:
            require_non_empty(path)
        except (FileNotFoundError, ValueError) as exc:
            errors.append(str(exc))

    apk_path = release_dir / apk_name
    expected_apk_fragment = f"versionCode-{version_code}-universal-release.apk"
    if not apk_name.startswith(f"Aura-v{version_name}-") or expected_apk_fragment not in apk_name:
        errors.append(
            f"APK name {apk_name} does not match versionName {version_name} "
            f"and versionCode {version_code}"
        )

    checksum_path = release_dir / "SHA256SUMS.txt"
    try:
        checksums = parse_sha256sums(checksum_path)
        expected_checksum_files = {
            apk_name,
            "THIRD-PARTY-NOTICES.md",
            "GOOGLE-OSS-RAW-INPUTS.zip",
            "NATIVE-COMPLIANCE.md",
        }
        missing = sorted(expected_checksum_files - set(checksums))
        extra = sorted(set(checksums) - expected_checksum_files)
        if missing:
            errors.append("SHA256SUMS.txt missing entries: " + ", ".join(missing))
        if extra:
            errors.append("SHA256SUMS.txt has unexpected entries: " + ", ".join(extra))
        for file_name in sorted(expected_checksum_files & set(checksums)):
            actual = sha256_file(release_dir / file_name)
            if checksums[file_name] != actual:
                errors.append(
                    f"SHA256 mismatch for {file_name}: "
                    f"expected {checksums[file_name]}, got {actual}"
                )
    except (FileNotFoundError, ValueError) as exc:
        errors.append(str(exc))

    notes_path = release_dir / "RELEASE_NOTES.md"
    try:
        release_notes = read_text(notes_path)
        required_note_fragments = [
            f"Aura {version_name} (versionCode {version_code})",
            apk_name,
            "APK SHA-256:",
            "THIRD-PARTY-NOTICES.md",
            "GOOGLE-OSS-RAW-INPUTS.zip",
            "NATIVE-COMPLIANCE.md",
            "Signing certificate SHA-256:",
            "GitHub artifact attestation:",
            "Build type: release, android:debuggable=false",
            "Package: com.freevibe",
        ]
        for fragment in required_note_fragments:
            if fragment not in release_notes:
                errors.append(f"RELEASE_NOTES.md missing fragment: {fragment}")
        valued_note_labels = [
            "APK SHA-256",
            "Signing certificate SHA-256",
            "GitHub artifact attestation",
        ]
        for label in valued_note_labels:
            if not re.search(rf"^- {re.escape(label)}:\s+\S+", release_notes, re.MULTILINE):
                errors.append(f"RELEASE_NOTES.md has blank value for: {label}")
        if apk_path.is_file():
            apk_digest = sha256_file(apk_path)
            if apk_digest not in release_notes:
                errors.append("RELEASE_NOTES.md missing APK SHA-256 value")
    except FileNotFoundError as exc:
        errors.append(str(exc))

    try:
        apksigner = read_text(release_dir / "apksigner.txt")
        if "Signer #1 certificate SHA-256 digest:" not in apksigner:
            errors.append("apksigner.txt missing signer SHA-256 digest")
    except FileNotFoundError as exc:
        errors.append(str(exc))

    try:
        aapt_badging = read_text(release_dir / "aapt-badging.txt")
        if "application-debuggable" in aapt_badging:
            errors.append("aapt-badging.txt marks the APK debuggable")
    except FileNotFoundError as exc:
        errors.append(str(exc))

    return errors


def main() -> int:
    args = parse_args()
    release_dir = Path(args.release_dir)
    errors = validate_bundle(
        release_dir=release_dir,
        apk_name=args.apk_name,
        version_name=args.version_name,
        version_code=args.version_code,
    )
    if errors:
        print("Release artifact bundle validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        json.dumps(
            {
                "apk": args.apk_name,
                "releaseDir": str(release_dir),
                "status": "ok",
                "versionCode": args.version_code,
                "versionName": args.version_name,
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
