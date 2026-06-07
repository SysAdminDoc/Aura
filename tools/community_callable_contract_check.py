#!/usr/bin/env python3
"""Validate Aura community callable quota contract metadata."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


LOWER_CAMEL = re.compile(r"^[a-z][A-Za-z0-9]*$")


EXPECTED_SURFACES: dict[str, dict[str, Any]] = {
    "reports": {
        "dailyLimit": 10,
        "minIntervalMillis": 120000,
        "dedupeKey": "content key + reason",
        "enforcement": ["APP_CHECKED_CALLABLE"],
        "functionName": "submitCommunityReport",
        "payloadSchema": "CommunityReportInput",
        "finalWritePaths": ["/community_reports/{reportId}"],
        "consumeLimitedUseAppCheckToken": True,
    },
    "sound_uploads": {
        "dailyLimit": 3,
        "minIntervalMillis": 900000,
        "dedupeKey": "storagePath",
        "enforcement": ["APP_CHECKED_CALLABLE", "STORAGE_RULES"],
        "functionName": "finalizeCommunitySoundUpload",
        "payloadSchema": "CommunitySoundUploadMetadata",
        "finalWritePaths": [
            "/community_sounds/{uploadId}",
            "/owner_uploads/{uid}/sounds/{uploadId}",
        ],
        "consumeLimitedUseAppCheckToken": True,
    },
    "wallpaper_uploads": {
        "dailyLimit": 5,
        "minIntervalMillis": 600000,
        "dedupeKey": "storagePath",
        "enforcement": ["APP_CHECKED_CALLABLE", "STORAGE_RULES"],
        "functionName": "finalizeCommunityWallpaperUpload",
        "payloadSchema": "CommunityWallpaperUploadMetadata",
        "finalWritePaths": [
            "/community_wallpapers/{uploadId}",
            "/owner_uploads/{uid}/wallpapers/{uploadId}",
        ],
        "consumeLimitedUseAppCheckToken": True,
    },
    "votes": {
        "dailyLimit": 100,
        "minIntervalMillis": 3000,
        "dedupeKey": "contentId",
        "enforcement": ["APP_CHECKED_CALLABLE", "RTDB_TRANSACTION"],
        "functionName": "recordCommunityVote",
        "payloadSchema": "CommunityVoteInput",
        "finalWritePaths": [
            "/votes/{contentId}",
            "/voters/{contentId}/{uid}",
        ],
        "consumeLimitedUseAppCheckToken": False,
    },
    "follows": {
        "dailyLimit": 50,
        "minIntervalMillis": 5000,
        "dedupeKey": "creatorId + desired state",
        "enforcement": ["APP_CHECKED_CALLABLE"],
        "functionName": "setCreatorFollow",
        "payloadSchema": "CommunityFollowInput",
        "finalWritePaths": ["/creator_follows/{uid}/{creatorId}"],
        "consumeLimitedUseAppCheckToken": False,
    },
    "user_blocks": {
        "dailyLimit": 100,
        "minIntervalMillis": 1000,
        "dedupeKey": "blockedUid + desired state",
        "enforcement": ["APP_CHECKED_CALLABLE"],
        "functionName": "setCommunityUserBlock",
        "payloadSchema": "CommunityUserBlockInput",
        "finalWritePaths": [
            "/community_user_blocks/{uid}/{blockedUid}",
            "/community_blocked_by/{blockedUid}/{uid}",
        ],
        "consumeLimitedUseAppCheckToken": False,
    },
    "profile_edits": {
        "dailyLimit": 12,
        "minIntervalMillis": 300000,
        "dedupeKey": "profileUid + normalized profile hash",
        "enforcement": ["APP_CHECKED_CALLABLE"],
        "functionName": "updateCreatorProfile",
        "payloadSchema": "CreatorProfileUpdateInput",
        "finalWritePaths": ["/creator_profiles/{uid}"],
        "consumeLimitedUseAppCheckToken": False,
    },
}


class CallableContractError(ValueError):
    """Raised when callable contract metadata is invalid."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura community callable contract metadata.")
    parser.add_argument("--contract", default="docs/community-callable-contract.json")
    return parser.parse_args()


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise CallableContractError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise CallableContractError(f"{label} must be a non-empty string")
    return value.strip()


def expected_quota_path(surface_key: str) -> str:
    return f"/community_write_quotas/{{uid}}/{{yyyyMMdd}}/{surface_key}"


def expected_dedupe_path(surface_key: str) -> str:
    return f"/community_write_dedupe/{{uid}}/{surface_key}/{{dedupeKey}}"


def validate_surface(surface: Any) -> dict[str, Any]:
    surface_object = require_object(surface, "Callable surface")
    surface_key = require_string(surface_object.get("surfaceKey"), "surfaceKey")
    if surface_key not in EXPECTED_SURFACES:
        raise CallableContractError(f"Unexpected callable surface: {surface_key}")

    expected = EXPECTED_SURFACES[surface_key]
    for field in ("dailyLimit", "minIntervalMillis", "dedupeKey"):
        if surface_object.get(field) != expected[field]:
            raise CallableContractError(f"{surface_key} {field} does not match the Android quota policy")

    if surface_object.get("quotaLedgerPath") != expected_quota_path(surface_key):
        raise CallableContractError(f"{surface_key} quotaLedgerPath does not match protected namespace")
    if surface_object.get("dedupeLedgerPath") != expected_dedupe_path(surface_key):
        raise CallableContractError(f"{surface_key} dedupeLedgerPath does not match protected namespace")

    enforcement = surface_object.get("enforcement")
    if not isinstance(enforcement, list) or sorted(enforcement) != sorted(expected["enforcement"]):
        raise CallableContractError(f"{surface_key} enforcement does not match the Android quota policy")
    if "APP_CHECKED_CALLABLE" not in enforcement:
        raise CallableContractError(f"{surface_key} must require callable enforcement")

    callable_object = require_object(surface_object.get("callable"), f"{surface_key} callable")
    function_name = require_string(callable_object.get("functionName"), f"{surface_key} functionName")
    if function_name != expected["functionName"]:
        raise CallableContractError(f"{surface_key} functionName does not match the Android quota policy")
    if not LOWER_CAMEL.fullmatch(function_name):
        raise CallableContractError(f"{surface_key} functionName must be lower camel case")

    if callable_object.get("payloadSchema") != expected["payloadSchema"]:
        raise CallableContractError(f"{surface_key} payloadSchema does not match the Android quota policy")
    if callable_object.get("finalWritePaths") != expected["finalWritePaths"]:
        raise CallableContractError(f"{surface_key} finalWritePaths do not match the Android quota policy")
    if callable_object.get("consumeLimitedUseAppCheckToken") != expected["consumeLimitedUseAppCheckToken"]:
        raise CallableContractError(f"{surface_key} limited-use App Check setting does not match policy")
    if callable_object.get("requiresAuth") is not True:
        raise CallableContractError(f"{surface_key} callable must require Firebase Auth")
    if callable_object.get("requiresAppCheck") is not True:
        raise CallableContractError(f"{surface_key} callable must require App Check")

    for path in callable_object["finalWritePaths"]:
        if not isinstance(path, str) or not path.startswith("/"):
            raise CallableContractError(f"{surface_key} final write paths must be absolute RTDB paths")
    return surface_object


def validate_contract(contract: Any) -> dict[str, Any]:
    contract_object = require_object(contract, "Callable contract")
    if contract_object.get("schemaVersion") != 1:
        raise CallableContractError("Callable contract schemaVersion must be 1")
    if contract_object.get("contractKind") != "communityCallableQuotaContract":
        raise CallableContractError("Callable contract has the wrong contractKind")
    if contract_object.get("quotaDayBoundary") != "UTC":
        raise CallableContractError("Callable contract quotaDayBoundary must be UTC")

    surfaces = contract_object.get("surfaces")
    if not isinstance(surfaces, list):
        raise CallableContractError("Callable contract surfaces must be a list")

    validated = [validate_surface(surface) for surface in surfaces]
    surface_keys = [surface["surfaceKey"] for surface in validated]
    if len(surface_keys) != len(set(surface_keys)):
        raise CallableContractError("Callable contract contains duplicate surface keys")
    expected_keys = sorted(EXPECTED_SURFACES)
    if sorted(surface_keys) != expected_keys:
        raise CallableContractError(f"Callable contract surfaces must be: {', '.join(expected_keys)}")

    function_names = [surface["callable"]["functionName"] for surface in validated]
    if len(function_names) != len(set(function_names)):
        raise CallableContractError("Callable contract contains duplicate function names")

    return {
        "schemaVersion": 1,
        "contractKind": "communityCallableQuotaContract",
        "surfaceCount": len(validated),
        "functionNames": sorted(function_names),
        "quotaDayBoundary": "UTC",
    }


def main() -> int:
    args = parse_args()
    try:
        result = validate_contract(read_json(Path(args.contract)))
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
