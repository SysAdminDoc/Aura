#!/usr/bin/env python3
"""Validate curated high-risk dependency and payload review metadata."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


DEFAULT_OVERLAY = "docs/legal/dependency-notice-overrides.json"
DEFAULT_DEPENDENCY_LOCK = "docs/legal/dependency-notices.lock.json"
DEFAULT_NATIVE_LOCK = "docs/legal/native-compliance.lock.json"

REQUIRED_ENTRY_IDS = {
    "firebase-android-sdk",
    "ffmpeg-payload",
    "google-mlkit-subject-segmentation",
    "google-play-services",
    "newpipe-extractor",
    "profileinstaller",
    "python-runtime",
    "quickjs-runtime",
    "youtubedl-android",
    "yt-dlp-payload",
    "zxing-core",
}

REQUIRED_FIELDS = {
    "id",
    "licenseId",
    "reviewNote",
    "sourceUrl",
    "target",
    "targetType",
    "usage",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate curated dependency overlay entries against notice locks."
    )
    parser.add_argument("--overlay", default=DEFAULT_OVERLAY)
    parser.add_argument("--dependency-lock", default=DEFAULT_DEPENDENCY_LOCK)
    parser.add_argument("--native-lock", default=DEFAULT_NATIVE_LOCK)
    return parser.parse_args()


def load_json(path: Path) -> object:
    if not path.is_file():
        raise FileNotFoundError(f"Required file not found: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def dependency_coordinates(lock: dict[str, object]) -> set[str]:
    return {str(item["coordinate"]) for item in lock.get("dependencies", [])}


def native_coordinates(lock: dict[str, object]) -> set[str]:
    return {str(item["coordinate"]) for item in lock.get("coordinates", [])}


def payload_targets(lock: dict[str, object]) -> set[str]:
    targets: set[str] = set()
    for coordinate in lock.get("coordinates", []):
        for artifact in coordinate.get("artifacts", []):
            for entry in artifact.get("payloadEntries", []):
                entry_name = str(entry.get("entry", ""))
                facts = entry.get("facts", {})
                if "yt-dlp version" in facts:
                    targets.add("yt-dlp")
                if "python payload" in facts:
                    targets.add("python-runtime")
                if "libqjs" in entry_name:
                    targets.add("quickjs")
                if "ffmpeg" in entry_name or "ffprobe" in entry_name:
                    targets.add("ffmpeg")
    return targets


def group_of(coordinate: str) -> str:
    return coordinate.split(":", 1)[0]


def target_matches(
    *,
    entry: dict[str, object],
    coordinates: set[str],
    native_coordinate_set: set[str],
    payloads: set[str],
) -> bool:
    target_type = str(entry.get("targetType", ""))
    target = str(entry.get("target", ""))
    all_coordinates = coordinates | native_coordinate_set
    if target_type == "coordinate":
        return target in all_coordinates
    if target_type == "coordinate-prefix":
        return any(coordinate.startswith(target) for coordinate in all_coordinates)
    if target_type == "group":
        return any(group_of(coordinate) == target for coordinate in all_coordinates)
    if target_type == "payload":
        return target in payloads
    raise ValueError(f"Unsupported overlay targetType for {entry.get('id')}: {target_type}")


def validate_entry_shape(entry: dict[str, object]) -> list[str]:
    errors: list[str] = []
    missing = sorted(field for field in REQUIRED_FIELDS if not str(entry.get(field, "")).strip())
    if missing:
        errors.append(f"{entry.get('id', '<missing id>')}: missing fields: {', '.join(missing)}")
    source_url = str(entry.get("sourceUrl", ""))
    if source_url and not source_url.startswith("https://"):
        errors.append(f"{entry.get('id', '<missing id>')}: sourceUrl must be https")
    return errors


def validate_overlay(
    *,
    overlay: dict[str, object],
    dependency_lock: dict[str, object],
    native_lock: dict[str, object],
) -> list[str]:
    if overlay.get("schemaVersion") != 1:
        return ["overlay schemaVersion must be 1"]
    entries = overlay.get("entries")
    if not isinstance(entries, list):
        return ["overlay entries must be an array"]

    coordinates = dependency_coordinates(dependency_lock)
    native_coordinate_set = native_coordinates(native_lock)
    payloads = payload_targets(native_lock)
    errors: list[str] = []
    seen_ids: set[str] = set()

    for raw_entry in entries:
        if not isinstance(raw_entry, dict):
            errors.append("overlay entry is not an object")
            continue
        entry = raw_entry
        entry_id = str(entry.get("id", ""))
        if entry_id in seen_ids:
            errors.append(f"{entry_id}: duplicate entry id")
        seen_ids.add(entry_id)
        errors.extend(validate_entry_shape(entry))
        try:
            if not target_matches(
                entry=entry,
                coordinates=coordinates,
                native_coordinate_set=native_coordinate_set,
                payloads=payloads,
            ):
                errors.append(f"{entry_id}: target does not match dependency/native locks")
        except ValueError as exc:
            errors.append(str(exc))

    missing_required = sorted(REQUIRED_ENTRY_IDS - seen_ids)
    if missing_required:
        errors.append("missing required high-risk entries: " + ", ".join(missing_required))
    return errors


def main() -> int:
    args = parse_args()
    overlay = load_json(Path(args.overlay))
    dependency_lock = load_json(Path(args.dependency_lock))
    native_lock = load_json(Path(args.native_lock))
    if not isinstance(overlay, dict):
        raise ValueError("overlay root must be an object")
    if not isinstance(dependency_lock, dict):
        raise ValueError("dependency lock root must be an object")
    if not isinstance(native_lock, dict):
        raise ValueError("native lock root must be an object")
    errors = validate_overlay(
        overlay=overlay,
        dependency_lock=dependency_lock,
        native_lock=native_lock,
    )
    if errors:
        print("Dependency overlay validation failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(
        json.dumps(
            {
                "entries": len(overlay["entries"]),
                "overlay": args.overlay,
                "status": "ok",
            },
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
