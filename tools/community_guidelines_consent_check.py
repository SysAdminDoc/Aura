#!/usr/bin/env python3
"""Validate Aura's community guidelines consent implementation."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


REQUIRED_DOC_TERMS = {
    "Aura Community Guidelines",
    "community_guidelines_accepted_version",
    "Users must accept the current Community Guidelines version before using",
    "Do not upload illegal, hateful, harassing, sexual, violent, deceptive",
    "report, block, owner delete, and takedown routes",
    "https://support.google.com/googleplay/android-developer/answer/12923286",
}

REQUIRED_CODE_MARKERS = {
    "app/src/main/java/com/freevibe/data/model/CommunityGuidelinesPolicy.kt": [
        "COMMUNITY_GUIDELINES_VERSION",
        "COMMUNITY_GUIDELINES_REQUIRED_MESSAGE",
        "CommunityGuidelinesPolicy",
        "postingRules",
    ],
    "app/src/main/java/com/freevibe/data/local/PreferencesManager.kt": [
        "communityGuidelinesAcceptedVersion",
        "communityGuidelinesAccepted",
        "community_guidelines_accepted_version",
        "acceptCommunityGuidelines",
    ],
    "app/src/main/java/com/freevibe/ui/components/CommunityGuidelinesDialog.kt": [
        "CommunityGuidelinesDialog",
        "Read and accept these rules before using community uploads",
        "Reset",
    ],
    "app/src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt": [
        "CommunityGuidelinesDialog",
        "Community guidelines",
        "Required before uploads, votes, reports, blocks, follows, and profiles",
        "resetCommunityGuidelines",
    ],
    "app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt": [
        "CommunityGuidelinesDialog",
        "communityGuidelinesAccepted",
    ],
    "app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt": [
        "CommunityGuidelinesDialog",
        "communityGuidelinesAccepted",
    ],
    "app/src/main/java/com/freevibe/data/repository/UploadRepository.kt": [
        "prefs.communityGuidelinesAccepted.first()",
    ],
    "app/src/main/java/com/freevibe/data/repository/WallpaperUploadRepository.kt": [
        "prefs.communityGuidelinesAccepted.first()",
    ],
    "app/src/main/java/com/freevibe/data/repository/CommunityBlockRepository.kt": [
        "prefs.communityGuidelinesAccepted.first()",
    ],
    "app/src/main/java/com/freevibe/data/repository/CreatorProfileRepository.kt": [
        "prefs.communityGuidelinesAccepted.first()",
    ],
    "app/src/main/java/com/freevibe/data/repository/VoteRepository.kt": [
        "prefs.communityGuidelinesAccepted.first()",
    ],
    "app/src/main/java/com/freevibe/FreeVibeApp.kt": [
        "prefs.communityGuidelinesAccepted.first()",
    ],
}


class CommunityGuidelinesConsentError(ValueError):
    """Raised when the community guidelines consent path is incomplete."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura community guidelines consent.")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--docs", default="docs/legal/community-guidelines.md")
    parser.add_argument("--play-packet", default="docs/distribution/play-app-content.json")
    return parser.parse_args()


def read_text(path: Path) -> str:
    if not path.is_file():
        raise CommunityGuidelinesConsentError(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def validate_docs(repo_root: Path, docs_path: str) -> None:
    docs_text = read_text(repo_root / docs_path)
    missing = sorted(term for term in REQUIRED_DOC_TERMS if term not in docs_text)
    if missing:
        raise CommunityGuidelinesConsentError(f"{docs_path} missing terms: {', '.join(missing)}")


def validate_code_markers(repo_root: Path) -> None:
    for relative_path, markers in REQUIRED_CODE_MARKERS.items():
        text = read_text(repo_root / relative_path)
        missing = [marker for marker in markers if marker not in text]
        if missing:
            raise CommunityGuidelinesConsentError(
                f"{relative_path} missing markers: {', '.join(missing)}"
            )


def validate_play_packet(repo_root: Path, packet_path: str) -> None:
    packet = json.loads(read_text(repo_root / packet_path))
    ugc = packet.get("declarations", {}).get("ugc", {})
    if ugc.get("termsOrGuidelinesStatus") != "implemented":
        raise CommunityGuidelinesConsentError("Play packet UGC guidelines status must be implemented")
    refs = set(ugc.get("evidenceRefs", []))
    required_refs = {
        "docs/legal/community-guidelines.md",
        "app/src/main/java/com/freevibe/data/model/CommunityGuidelinesPolicy.kt",
        "app/src/main/java/com/freevibe/data/local/PreferencesManager.kt",
        "app/src/main/java/com/freevibe/ui/components/CommunityGuidelinesDialog.kt",
    }
    missing = sorted(required_refs - refs)
    if missing:
        raise CommunityGuidelinesConsentError(
            "Play packet UGC evidence is missing refs: " + ", ".join(missing)
        )
    for action in packet.get("ownerActions", []):
        if action.get("id") == "confirm-ugc-guidelines-consent":
            raise CommunityGuidelinesConsentError("stale UGC guidelines owner action remains")


def validate(repo_root: Path, docs_path: str, packet_path: str) -> dict[str, object]:
    validate_docs(repo_root, docs_path)
    validate_code_markers(repo_root)
    validate_play_packet(repo_root, packet_path)
    return {
        "status": "ok",
        "policyKind": "communityGuidelinesConsent",
        "docsPath": docs_path,
        "checkedCodeSurfaceCount": len(REQUIRED_CODE_MARKERS),
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        result = validate(repo_root, args.docs, args.play_packet)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
