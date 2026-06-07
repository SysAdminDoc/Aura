#!/usr/bin/env python3
"""Compare exported community Storage objects with RTDB metadata."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


MANAGED_PREFIXES = ("sounds/", "wallpapers/")
COMMUNITY_ROOTS = {
    "community_sounds": "sounds/",
    "community_wallpapers": "wallpapers/",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a dry-run orphan report for Aura community uploads."
    )
    parser.add_argument("--storage-objects", required=True, help="JSON file with Storage object names.")
    parser.add_argument("--database-export", required=True, help="JSON export containing community metadata roots.")
    parser.add_argument("--output", help="Optional report path. Defaults to stdout.")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def storage_object_name(item: Any) -> str:
    if isinstance(item, str):
        return item.strip()
    if isinstance(item, dict):
        for key in ("name", "path", "object", "fullPath"):
            value = item.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()
    return ""


def load_storage_objects(raw: Any) -> set[str]:
    if isinstance(raw, dict):
        raw = raw.get("objects", [])
    if not isinstance(raw, list):
        raise ValueError("Storage object export must be a JSON array or an object with an objects array")
    objects = {storage_object_name(item) for item in raw}
    objects.discard("")
    return objects


def metadata_rows(raw_database: Any) -> tuple[set[str], list[dict[str, str]], list[dict[str, str]]]:
    if not isinstance(raw_database, dict):
        raise ValueError("Database export must be a JSON object")

    referenced_paths: set[str] = set()
    legacy_rows: list[dict[str, str]] = []
    invalid_rows: list[dict[str, str]] = []

    for root, expected_prefix in COMMUNITY_ROOTS.items():
        records = raw_database.get(root, {})
        if records is None:
            continue
        if not isinstance(records, dict):
            raise ValueError(f"{root} must be a JSON object when present")
        for upload_id, record in sorted(records.items()):
            if not isinstance(record, dict):
                invalid_rows.append(
                    {"root": root, "uploadId": str(upload_id), "storagePath": "", "reason": "metadata row is not an object"}
                )
                continue
            storage_path = str(record.get("storagePath", "")).strip()
            if not storage_path:
                legacy_rows.append({"root": root, "uploadId": str(upload_id)})
            elif not storage_path.startswith(expected_prefix):
                invalid_rows.append(
                    {
                        "root": root,
                        "uploadId": str(upload_id),
                        "storagePath": storage_path,
                        "reason": f"expected {expected_prefix} prefix",
                    }
                )
            else:
                referenced_paths.add(storage_path)

    return referenced_paths, legacy_rows, invalid_rows


def build_report(storage_objects: set[str], database_export: Any) -> dict[str, Any]:
    referenced_paths, legacy_rows, invalid_rows = metadata_rows(database_export)
    managed_objects = {path for path in storage_objects if path.startswith(MANAGED_PREFIXES)}
    unmanaged_objects = storage_objects - managed_objects
    orphan_candidates = managed_objects - referenced_paths
    metadata_missing_objects = referenced_paths - managed_objects

    return {
        "schemaVersion": 1,
        "managedPrefixes": list(MANAGED_PREFIXES),
        "storageObjectCount": len(storage_objects),
        "managedStorageObjectCount": len(managed_objects),
        "referencedStoragePathCount": len(referenced_paths),
        "orphanCandidates": sorted(orphan_candidates),
        "metadataWithMissingObject": sorted(metadata_missing_objects),
        "legacyRowsMissingStoragePath": legacy_rows,
        "invalidMetadataStoragePaths": invalid_rows,
        "unmanagedObjects": sorted(unmanaged_objects),
    }


def dump_report(report: dict[str, Any]) -> str:
    return json.dumps(report, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    storage_objects = load_storage_objects(read_json(Path(args.storage_objects)))
    database_export = read_json(Path(args.database_export))
    report_text = dump_report(build_report(storage_objects, database_export))

    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(report_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(report_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
