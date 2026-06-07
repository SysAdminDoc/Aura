#!/usr/bin/env python3
"""Build a dry-run backfill plan for legacy community upload metadata."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any
from urllib.parse import unquote, urlparse


ROOTS = {
    "community_sounds": {
        "contentType": "SOUND",
        "ownerRoot": "sounds",
        "publicPrefix": "cu_",
        "urlFields": ("downloadUrl", "url", "audioUrl"),
    },
    "community_wallpapers": {
        "contentType": "WALLPAPER",
        "ownerRoot": "wallpapers",
        "publicPrefix": "cw_",
        "urlFields": ("downloadUrl", "fullUrl", "thumbnailUrl", "url", "imageUrl"),
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a dry-run owner-index/storagePath backfill plan."
    )
    parser.add_argument("--database-export", required=True, help="JSON export containing community metadata roots.")
    parser.add_argument("--output", help="Optional plan path. Defaults to stdout.")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def sanitize_key(value: str) -> str:
    return value.strip().replace("/", "_").replace(".", "_").replace("#", "_").replace("$", "_").replace("[", "_").replace("]", "_")


def extract_storage_path(value: str) -> str:
    raw = value.strip()
    if raw.startswith("sounds/") or raw.startswith("wallpapers/"):
        return raw

    parsed = urlparse(raw)
    if not parsed.scheme or not parsed.netloc:
        return ""

    if parsed.netloc == "firebasestorage.googleapis.com":
        marker = "/o/"
        if marker not in parsed.path:
            return ""
        encoded = parsed.path.split(marker, 1)[1]
        return unquote(encoded).strip()

    if parsed.netloc == "storage.googleapis.com":
        parts = parsed.path.lstrip("/").split("/", 1)
        if len(parts) == 2:
            return unquote(parts[1]).strip()

    return ""


def first_string(record: dict[str, Any], fields: tuple[str, ...]) -> str:
    for field in fields:
        value = record.get(field)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def owner_uid(record: dict[str, Any]) -> str:
    return first_string(record, ("uploaderUid", "uploaderId", "ownerUid"))


def uploaded_at(record: dict[str, Any]) -> int:
    value = record.get("uploadedAt") or record.get("createdAt")
    if isinstance(value, (int, float)) and value > 0:
        return int(value)
    return 0


def title(record: dict[str, Any]) -> str:
    value = record.get("name") or record.get("title")
    if isinstance(value, str) and value.strip():
        return value.strip()[:80]
    return "Community upload"


def build_candidate(root: str, upload_id: str, record: dict[str, Any], storage_path: str) -> dict[str, Any]:
    config = ROOTS[root]
    owner = sanitize_key(owner_uid(record))
    safe_upload_id = sanitize_key(upload_id)
    owner_root = str(config["ownerRoot"])
    metadata_path = f"/{root}/{safe_upload_id}"
    owner_index_path = f"/owner_uploads/{owner}/{owner_root}/{safe_upload_id}"
    created_at = uploaded_at(record)
    owner_index = {
        "uploadId": safe_upload_id,
        "publicId": f"{config['publicPrefix']}{safe_upload_id}",
        "contentType": config["contentType"],
        "metadataPath": metadata_path,
        "storagePath": storage_path,
        "title": title(record),
        "createdAt": created_at,
    }
    updates: dict[str, Any] = {
        f"{metadata_path}/storagePath": storage_path,
        owner_index_path: owner_index,
    }
    if not str(record.get("uploaderUid", "")).strip():
        updates[f"{metadata_path}/uploaderUid"] = owner
    return {
        "root": root,
        "uploadId": safe_upload_id,
        "ownerUid": owner,
        "storagePath": storage_path,
        "updates": updates,
    }


def backfill_plan(database_export: Any) -> dict[str, Any]:
    if not isinstance(database_export, dict):
        raise ValueError("Database export must be a JSON object")

    candidates: list[dict[str, Any]] = []
    blocked: list[dict[str, str]] = []
    already_backfilled: list[dict[str, str]] = []

    for root, config in ROOTS.items():
        records = database_export.get(root, {})
        if records is None:
            continue
        if not isinstance(records, dict):
            raise ValueError(f"{root} must be a JSON object when present")
        expected_prefix = f"{config['ownerRoot']}/"
        for upload_id, raw_record in sorted(records.items()):
            if not isinstance(raw_record, dict):
                blocked.append({"root": root, "uploadId": str(upload_id), "reason": "metadata row is not an object"})
                continue
            existing_path = str(raw_record.get("storagePath", "")).strip()
            if existing_path:
                already_backfilled.append({"root": root, "uploadId": str(upload_id), "storagePath": existing_path})
                continue
            url_value = first_string(raw_record, tuple(config["urlFields"]))
            storage_path = extract_storage_path(url_value)
            if not storage_path:
                blocked.append({"root": root, "uploadId": str(upload_id), "reason": "storage path could not be derived"})
                continue
            if not storage_path.startswith(expected_prefix):
                blocked.append(
                    {
                        "root": root,
                        "uploadId": str(upload_id),
                        "storagePath": storage_path,
                        "reason": f"expected {expected_prefix} prefix",
                    }
                )
                continue
            if not owner_uid(raw_record):
                blocked.append({"root": root, "uploadId": str(upload_id), "storagePath": storage_path, "reason": "missing owner UID"})
                continue
            if uploaded_at(raw_record) <= 0:
                blocked.append({"root": root, "uploadId": str(upload_id), "storagePath": storage_path, "reason": "missing created timestamp"})
                continue
            candidates.append(build_candidate(root, str(upload_id), raw_record, storage_path))

    return {
        "schemaVersion": 1,
        "candidateCount": len(candidates),
        "blockedCount": len(blocked),
        "alreadyBackfilledCount": len(already_backfilled),
        "candidates": candidates,
        "blocked": blocked,
        "alreadyBackfilled": already_backfilled,
    }


def dump_plan(plan: dict[str, Any]) -> str:
    return json.dumps(plan, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    plan_text = dump_plan(backfill_plan(read_json(Path(args.database_export))))
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(plan_text, encoding="utf-8")
        print(f"wrote {output}")
    else:
        sys.stdout.write(plan_text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
