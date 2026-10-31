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


def read_settings_surface_text(repo_root: Path) -> str:
    settings_dir = repo_root / "app/src/main/java/com/freevibe/ui/screens/settings"
    if not settings_dir.is_dir():
        raise CommunityIdentityLazinessError(f"missing settings package: {settings_dir}")
    return "\n".join(
        path.read_text(encoding="utf-8")
        for path in sorted(settings_dir.glob("*.kt"))
        if path.is_file()
    )


VOTE_REPOSITORY_PATH = "app/src/main/java/com/freevibe/data/repository/VoteRepository.kt"

CONSENT_PREFERENCES = ("communityProviderEnabled", "communityGuidelinesAccepted")


def iter_init_block_bodies(text: str) -> list[str]:
    """Return the body of every `init {` block, matched by brace depth."""
    bodies: list[str] = []
    marker = "init {"
    index = text.find(marker)
    while index != -1:
        cursor = index + len(marker) - 1
        depth = 0
        for position in range(cursor, len(text)):
            char = text[position]
            if char == "{":
                depth += 1
            elif char == "}":
                depth -= 1
                if depth == 0:
                    bodies.append(text[cursor + 1 : position])
                    break
        index = text.find(marker, index + len(marker))
    return bodies


def validate_moderation_listener_consent(repo_root: Path) -> int:
    """The moderation listener opens an RTDB socket, so consent must gate it.

    Both consent preferences default to false and VoteRepository is a @Singleton
    constructed as soon as any injecting screen opens, so attaching from `init`
    puts a non-consenting user on the network for the life of the process.
    """
    text = read_text(repo_root / VOTE_REPOSITORY_PATH)

    for body in iter_init_block_bodies(text):
        if "addValueEventListener" in body:
            raise CommunityIdentityLazinessError(
                "VoteRepository must not attach a Firebase listener from an init block; "
                "gate it behind the community consent preferences"
            )

    if "attachModerationListener" not in text or "detachModerationListener" not in text:
        raise CommunityIdentityLazinessError(
            "VoteRepository must attach and detach the moderation listener explicitly"
        )

    for preference in CONSENT_PREFERENCES:
        if preference not in text:
            raise CommunityIdentityLazinessError(
                f"VoteRepository moderation listener must observe {preference}"
            )

    if "removeEventListener" not in text:
        raise CommunityIdentityLazinessError(
            "VoteRepository must remove the moderation listener when consent is withdrawn"
        )

    return text.count("addValueEventListener")


def validate_community_identity_laziness(repo_root: Path) -> dict[str, int | str]:
    app_text = read_text(repo_root / "app/src/main/java/com/freevibe/FreeVibeApp.kt")
    for term in FORBIDDEN_STARTUP_TERMS:
        if term in app_text:
            raise CommunityIdentityLazinessError(f"FreeVibeApp must not eagerly create or refresh community identity: {term}")

    provider_text = read_text(repo_root / "app/src/main/java/com/freevibe/service/CommunityIdentityProvider.kt")
    if "currentIdentitySummary" not in provider_text or "hasFirebaseIdentity" not in provider_text:
        raise CommunityIdentityLazinessError("Community identity summary must expose auth state without forcing sign-in")
    if 'identitySuffix = displayId?.let(::communityIdentitySuffix) ?: IDENTITY_SUFFIX_NOT_CREATED' not in provider_text or 'const val IDENTITY_SUFFIX_NOT_CREATED = "Not created"' not in provider_text:
        raise CommunityIdentityLazinessError("Community identity summary must keep fresh installs in the Not created state")

    lazy_write_count = 0
    for relative_path in REQUIRED_LAZY_WRITE_PATHS:
        text = read_text(repo_root / relative_path)
        if "ensureSignedIn(" not in text:
            raise CommunityIdentityLazinessError(f"community write path no longer creates identity lazily: {relative_path}")
        lazy_write_count += text.count("ensureSignedIn(")

    settings_text = read_settings_surface_text(repo_root)
    if "communityIdentitySummary" not in settings_text or "IDENTITY_SUFFIX_NOT_CREATED" not in settings_text:
        raise CommunityIdentityLazinessError("Settings must expose current community identity state before deletion/cleanup actions")

    moderation_listener_count = validate_moderation_listener_consent(repo_root)

    return {
        "status": "ok",
        "lazyWritePathCount": len(REQUIRED_LAZY_WRITE_PATHS),
        "lazyEnsureSignedInCalls": lazy_write_count,
        "consentGatedModerationListeners": moderation_listener_count,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Validate that community identity creation is lazy.")
    parser.add_argument("--repo-root", default=".", type=Path)
    args = parser.parse_args()
    print(json.dumps(validate_community_identity_laziness(args.repo_root.resolve()), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
