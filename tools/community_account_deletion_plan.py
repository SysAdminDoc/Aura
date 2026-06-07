#!/usr/bin/env python3
"""Build a dry-run account deletion plan for community identity data."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


FIREBASE_KEY_CHARS = ("/", ".", "#", "$", "[", "]")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build dry-run RTDB deletion updates for an Aura community UID."
    )
    parser.add_argument("--database-export", required=True, help="JSON export containing community RTDB roots.")
    parser.add_argument("--uid", required=True, help="Firebase UID to delete from private/community marker paths.")
    parser.add_argument("--output", help="Optional plan path. Defaults to stdout.")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def sanitize_key(value: str) -> str:
    safe = value.strip()
    for char in FIREBASE_KEY_CHARS:
        safe = safe.replace(char, "_")
    return safe


def matches_uid(value: Any, safe_uid: str) -> bool:
    return isinstance(value, str) and sanitize_key(value) == safe_uid


def object_root(database_export: dict[str, Any], root: str) -> dict[str, Any]:
    value = database_export.get(root, {})
    if value is None:
        return {}
    if not isinstance(value, dict):
        raise ValueError(f"{root} must be a JSON object when present")
    return value


def add_update(updates: dict[str, None], categories: dict[str, list[str]], category: str, path: str) -> None:
    updates[path] = None
    categories.setdefault(category, []).append(path)


def build_account_deletion_plan(database_export: Any, uid: str) -> dict[str, Any]:
    if not isinstance(database_export, dict):
        raise ValueError("Database export must be a JSON object")

    safe_uid = sanitize_key(uid)
    if not safe_uid:
        raise ValueError("UID is required")

    updates: dict[str, None] = {}
    categories: dict[str, list[str]] = {
        "voteMarkers": [],
        "creatorFollows": [],
        "creatorProfile": [],
        "communityBlocks": [],
        "collectionShares": [],
    }
    retained: list[dict[str, str]] = [
        {
            "root": "/votes/*/upvotes",
            "reason": "Aggregate vote counts are retained after per-user markers are removed.",
        },
        {
            "root": "/community_reports and moderation audit roots",
            "reason": "Private moderation, takedown, and abuse records are retained for safety and legal review.",
        },
        {
            "root": "/community_sounds, /community_wallpapers, /owner_uploads, and Storage objects",
            "reason": "Public uploads require the owner/admin upload deletion workflow so blobs, metadata, owner indexes, and tombstones stay consistent.",
        },
    ]

    for content_id, raw_vote in object_root(database_export, "votes").items():
        if not isinstance(raw_vote, dict):
            continue
        voters = raw_vote.get("voters", {})
        if not isinstance(voters, dict):
            continue
        for voter_key in sorted(voters):
            if matches_uid(voter_key, safe_uid):
                add_update(updates, categories, "voteMarkers", f"/votes/{content_id}/voters/{voter_key}")

    for content_id, raw_voters in object_root(database_export, "voters").items():
        if not isinstance(raw_voters, dict):
            continue
        for voter_key in sorted(raw_voters):
            if matches_uid(voter_key, safe_uid):
                add_update(updates, categories, "voteMarkers", f"/voters/{content_id}/{voter_key}")

    follows = object_root(database_export, "creator_follows")
    if safe_uid in follows:
        add_update(updates, categories, "creatorFollows", f"/creator_follows/{safe_uid}")
    for follower_uid, raw_followed in follows.items():
        if not isinstance(raw_followed, dict) or matches_uid(follower_uid, safe_uid):
            continue
        for creator_key, raw_follow in sorted(raw_followed.items()):
            creator_id = raw_follow.get("creatorId") if isinstance(raw_follow, dict) else creator_key
            if matches_uid(creator_key, safe_uid) or matches_uid(creator_id, safe_uid):
                add_update(
                    updates,
                    categories,
                    "creatorFollows",
                    f"/creator_follows/{follower_uid}/{creator_key}",
                )

    if safe_uid in object_root(database_export, "creator_profiles"):
        add_update(updates, categories, "creatorProfile", f"/creator_profiles/{safe_uid}")

    user_blocks = object_root(database_export, "community_user_blocks")
    if safe_uid in user_blocks:
        add_update(updates, categories, "communityBlocks", f"/community_user_blocks/{safe_uid}")
    for blocker_uid, raw_blocked in user_blocks.items():
        if not isinstance(raw_blocked, dict) or matches_uid(blocker_uid, safe_uid):
            continue
        for blocked_key, raw_block in sorted(raw_blocked.items()):
            blocked_uid = raw_block.get("blockedUid") if isinstance(raw_block, dict) else blocked_key
            if matches_uid(blocked_key, safe_uid) or matches_uid(blocked_uid, safe_uid):
                add_update(
                    updates,
                    categories,
                    "communityBlocks",
                    f"/community_user_blocks/{blocker_uid}/{blocked_key}",
                )

    blocked_by = object_root(database_export, "community_blocked_by")
    if safe_uid in blocked_by:
        add_update(updates, categories, "communityBlocks", f"/community_blocked_by/{safe_uid}")
    for blocked_uid, raw_blockers in blocked_by.items():
        if not isinstance(raw_blockers, dict) or matches_uid(blocked_uid, safe_uid):
            continue
        for blocker_key in sorted(raw_blockers):
            if matches_uid(blocker_key, safe_uid):
                add_update(
                    updates,
                    categories,
                    "communityBlocks",
                    f"/community_blocked_by/{blocked_uid}/{blocker_key}",
                )

    for root in ("shared_collections", "collection_shares"):
        for token, raw_share in object_root(database_export, root).items():
            if not isinstance(raw_share, dict):
                continue
            owner = raw_share.get("createdByUid") or raw_share.get("ownerUid")
            if matches_uid(owner, safe_uid):
                add_update(updates, categories, "collectionShares", f"/{root}/{token}")

    for paths in categories.values():
        paths.sort()

    return {
        "schemaVersion": 1,
        "uid": safe_uid,
        "updateCount": len(updates),
        "updates": dict(sorted(updates.items())),
        "categories": categories,
        "retained": retained,
    }


def dump_plan(plan: dict[str, Any]) -> str:
    return json.dumps(plan, indent=2, sort_keys=True) + "\n"


def main() -> int:
    args = parse_args()
    plan_text = dump_plan(build_account_deletion_plan(read_json(Path(args.database_export)), args.uid))
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
