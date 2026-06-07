#!/usr/bin/env python3
"""Plan public upload deletion handoff for an account deletion request."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

from tools.community_account_deletion_auth_package import validate_auth_deletion_package
from tools.community_account_deletion_plan import read_json, sanitize_key
from tools.community_account_deletion_review import (
    ReviewError,
    require_non_empty_string,
    require_object,
    sha256_json,
    utc_now,
)


UPLOAD_ROOTS = {
    "community_sounds": {
        "contentType": "SOUND",
        "ownerRoot": "sounds",
        "publicIdPrefix": "cu_",
        "expectedStoragePrefix": "sounds/",
    },
    "community_wallpapers": {
        "contentType": "WALLPAPER",
        "ownerRoot": "wallpapers",
        "publicIdPrefix": "cw_",
        "expectedStoragePrefix": "wallpapers/",
    },
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a private public-upload deletion handoff plan for an Aura account deletion request."
    )
    parser.add_argument("--database-export", required=True, help="JSON export containing community upload roots.")
    parser.add_argument("--auth-package", required=True, help="JSON output from community_account_deletion_auth_package.py.")
    parser.add_argument("--output", help="Optional upload deletion plan path. Defaults to stdout.")
    return parser.parse_args()


def owner_uid(record: dict[str, Any]) -> str:
    for field in ("uploaderUid", "uploaderId", "ownerUid"):
        value = record.get(field)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return ""


def uploaded_at(record: dict[str, Any]) -> int:
    value = record.get("uploadedAt") or record.get("createdAt")
    if isinstance(value, (int, float)) and value > 0:
        return int(value)
    return 0


def upload_title(record: dict[str, Any]) -> str:
    value = record.get("name") or record.get("title")
    if isinstance(value, str) and value.strip():
        return value.strip()[:80]
    return "Community upload"


def matches_owner(record: dict[str, Any], uid: str, safe_uid: str) -> bool:
    owner = owner_uid(record)
    return owner == uid or sanitize_key(owner) == safe_uid


def build_upload_candidate(
    root: str,
    upload_id: str,
    record: dict[str, Any],
    uid: str,
    safe_uid: str,
) -> dict[str, Any]:
    config = UPLOAD_ROOTS[root]
    safe_upload_id = sanitize_key(upload_id)
    owner_root = str(config["ownerRoot"])
    public_id = f"{config['publicIdPrefix']}{safe_upload_id}"
    metadata_path = f"/{root}/{safe_upload_id}"
    storage_path = require_non_empty_string(record.get("storagePath"), "Upload storagePath")
    expected_prefix = f"{config['expectedStoragePrefix']}{safe_uid}/"
    if not storage_path.startswith(expected_prefix):
        raise ReviewError(f"Upload storagePath must start with {expected_prefix}")

    return {
        "root": root,
        "uploadId": safe_upload_id,
        "publicId": public_id,
        "contentType": config["contentType"],
        "metadataPath": metadata_path,
        "ownerIndexPath": f"/owner_uploads/{safe_uid}/{owner_root}/{safe_upload_id}",
        "deletionTombstonePath": f"/community_upload_deletions/{public_id}",
        "storagePath": storage_path,
        "title": upload_title(record),
        "createdAt": uploaded_at(record),
        "ownerUidHash": auth_owner_hash(uid),
        "requiredWorkflow": "Use the owner/admin upload deletion workflow: delete Storage object, remove public metadata, remove owner index, and write private deletion tombstone.",
    }


def auth_owner_hash(uid: str) -> str:
    return sha256_json({"uid": uid})


def build_account_upload_deletion_plan(
    database_export: Any,
    auth_package: Any,
    planned_at: str | None = None,
) -> dict[str, Any]:
    database = require_object(database_export, "Database export")
    auth = validate_auth_deletion_package(auth_package)
    uid = require_non_empty_string(auth.get("uid"), "Auth package uid")
    safe_uid = require_non_empty_string(auth.get("safeUid"), "Auth package safeUid")

    candidates: list[dict[str, Any]] = []
    blocked: list[dict[str, Any]] = []
    for root, config in UPLOAD_ROOTS.items():
        records = database.get(root, {})
        if records is None:
            continue
        if not isinstance(records, dict):
            raise ReviewError(f"{root} must be a JSON object when present")
        expected_prefix = f"{config['expectedStoragePrefix']}{safe_uid}/"
        for upload_id, raw_record in sorted(records.items()):
            if not isinstance(raw_record, dict):
                continue
            if not matches_owner(raw_record, uid, safe_uid):
                continue
            try:
                candidates.append(build_upload_candidate(root, str(upload_id), raw_record, uid, safe_uid))
            except ReviewError as exc:
                blocked.append(
                    {
                        "root": root,
                        "uploadId": sanitize_key(str(upload_id)),
                        "metadataPath": f"/{root}/{sanitize_key(str(upload_id))}",
                        "expectedStoragePrefix": expected_prefix,
                        "reason": str(exc),
                    }
                )

    return {
        "schemaVersion": 1,
        "planKind": "communityAccountDeletionUploadHandoff",
        "planStatus": "readyForOwnerAdminUploadWorkflow" if not blocked else "blockedUntilUploadHandlesReviewed",
        "requestCode": auth.get("requestCode"),
        "supportReference": auth.get("supportReference"),
        "uidKeySuffix": auth.get("uidKeySuffix"),
        "authPackageHash": sha256_json(auth),
        "plannedAt": planned_at or utc_now(),
        "candidateCount": len(candidates),
        "blockedCount": len(blocked),
        "storageDeleteCount": len(candidates),
        "candidates": candidates,
        "blocked": blocked,
        "executorWarning": "Private plan: review every upload before using owner/admin upload deletion tooling. This plan does not delete Storage objects or RTDB metadata.",
    }


def dump_upload_deletion_plan(plan: dict[str, Any]) -> str:
    return json.dumps(plan, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    try:
        plan = build_account_upload_deletion_plan(
            read_json(Path(args.database_export)),
            read_json(Path(args.auth_package)),
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    plan_text = dump_upload_deletion_plan(plan)
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
