from __future__ import annotations

import argparse
import json
from pathlib import Path


class CommunityIdentityLazinessError(ValueError):
    pass


FORBIDDEN_STARTUP_TERMS = (
    "warmCommunityIdentity",
    "CommunityIdentityProvider",
    "ensureSignedIn(",
    "refreshAdminFromClaims(",
)

REQUIRED_LAZY_WRITE_PATHS = (
    "app/src/main/java/com/freevibe/data/repository/UploadRepository.kt",
    "app/src/main/java/com/freevibe/data/repository/WallpaperUploadRepository.kt",
    "app/src/main/java/com/freevibe/data/repository/VoteRepository.kt",
    "app/src/main/java/com/freevibe/data/repository/CommunityReportRepository.kt",
    "app/src/main/java/com/freevibe/data/repository/CreatorProfileRepository.kt",
    "app/src/main/java/com/freevibe/data/repository/CommunityBlockRepository.kt",
)


def read_text(path: Path) -> str:
    if not path.is_file():
        raise CommunityIdentityLazinessError(f"missing file: {path}")
    return path.read_text(encoding="utf-8")


def validate_community_identity_laziness(repo_root: Path) -> dict[str, int | str]:
    app_text = read_text(repo_root / "app/src/main/java/com/freevibe/FreeVibeApp.kt")
    for term in FORBIDDEN_STARTUP_TERMS:
        if term in app_text:
            raise CommunityIdentityLazinessError(f"FreeVibeApp must not eagerly create or refresh community identity: {term}")

    provider_text = read_text(repo_root / "app/src/main/java/com/freevibe/service/CommunityIdentityProvider.kt")
    if "currentIdentitySummary" not in provider_text or "hasFirebaseIdentity" not in provider_text:
        raise CommunityIdentityLazinessError("Community identity summary must expose auth state without forcing sign-in")
    if 'identitySuffix = displayId?.let(::communityIdentitySuffix) ?: "Not created"' not in provider_text:
        raise CommunityIdentityLazinessError("Community identity summary must keep fresh installs in the Not created state")

    lazy_write_count = 0
    for relative_path in REQUIRED_LAZY_WRITE_PATHS:
        text = read_text(repo_root / relative_path)
        if "ensureSignedIn(" not in text:
            raise CommunityIdentityLazinessError(f"community write path no longer creates identity lazily: {relative_path}")
        lazy_write_count += text.count("ensureSignedIn(")

    settings_text = read_text(repo_root / "app/src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt")
    if "communityIdentitySummary" not in settings_text or "Not created" not in settings_text:
        raise CommunityIdentityLazinessError("Settings must expose current community identity state before deletion/cleanup actions")

    return {
        "status": "ok",
        "lazyWritePathCount": len(REQUIRED_LAZY_WRITE_PATHS),
        "lazyEnsureSignedInCalls": lazy_write_count,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate that community identity creation is lazy.")
    parser.add_argument("--repo-root", default=".", type=Path)
    args = parser.parse_args()
    print(json.dumps(validate_community_identity_laziness(args.repo_root.resolve()), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
