#!/usr/bin/env python3
"""Validate Aura release metadata consistency across store and release docs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any


POLICY_PATH = "docs/distribution/release-metadata-consistency.json"
TITLE_MAX_CHARS = 30
SHORT_DESCRIPTION_MAX_CHARS = 80
FULL_DESCRIPTION_MAX_CHARS = 4000
PACKAGE_RE = re.compile(r'applicationId\s*=\s*"([^"]+)"')
VERSION_NAME_RE = re.compile(r'versionName\s*=\s*"([^"]+)"')
VERSION_CODE_RE = re.compile(r"versionCode\s*=\s*(\d+)")
REQUIRED_DOC_TERMS = {
    "Release metadata consistency",
    "Current package",
    "Metadata surfaces",
    "Release preflights",
    "Release artifacts",
    "Release checklist",
    "Sources",
}
REQUIRED_SOURCE_URLS = {
    "https://developer.android.com/guide/app-bundle",
    "https://developer.android.com/studio/publish/app-signing",
    "https://developer.android.com/tools/bundletool",
    "https://support.google.com/googleplay/android-developer/answer/9859152",
    "https://support.google.com/googleplay/android-developer/answer/13393723",
    "https://support.google.com/googleplay/android-developer/answer/9842756",
    "https://docs.fastlane.tools/actions/supply/",
    "https://docs.github.com/en/repositories/releasing-projects-on-github/about-releases",
    "https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/",
}


# Room's schema version, which the exported schema directory is the truth for.
ROOM_SCHEMA_CLAIM = re.compile(r"Room\s*(?:DB\s*)?\(?v(\d+)\)?", re.IGNORECASE)
# Version strings quoted in prose, e.g. a README badge or a "Current: v6.41.0" line.
VERSION_NAME_CLAIM = re.compile(r"version-(\d+\.\d+\.\d+)-blue|\*\*Current:\*\*\s*v(\d+\.\d+\.\d+)")
VERSION_CODE_CLAIM = re.compile(r"versionCode\s*(\d+)")
COMPILE_SDK_RE = re.compile(r"compileSdk\s*=\s*(\d+)")
JVM_TARGET_RE = re.compile(r'jvmTarget\s*=\s*"([^"]+)"')
# CONTRIBUTING.md told contributors to install "Android SDK 35" while the build
# compiled against 36, and asked for "JDK 17+" when Gradle 8.12.1 rejects
# anything newer than 21. Both are things a reader acts on, so both are checked.
ANDROID_SDK_CLAIM = re.compile(r"Android SDK\s+(\d+)")
JAVA_TARGET_CLAIM = re.compile(r"Java\s+(\d+)\s+as the compile target", re.IGNORECASE)

# Prose surfaces that state release facts. CLAUDE.md is untracked working notes,
# so it is checked when present and skipped when it is not.
# ARCHITECTURE.md and CONTRIBUTING.md joined this list after both were found
# stating facts no gate read: ARCHITECTURE.md claimed Room v14 and a "Favorites"
# bottom-nav destination against a shipped v17 and "Library", and CONTRIBUTING.md
# asked for "JDK 17+ and Android SDK 35" against compileSdk 36.
FACT_SURFACES = ("README.md", "CLAUDE.md", "ARCHITECTURE.md", "CONTRIBUTING.md")

SCREEN_NAV = "app/src/main/java/com/freevibe/ui/navigation/Screen.kt"
BOTTOM_NAV_ITEMS = re.compile(r"bottomNavItems[^=]*=\s*listOf\(([^)]*)\)", re.DOTALL)
# "5 bottom nav tabs: A, B, C" / "**5 bottom nav tabs** — A, B, C" / bare count.
# The trailing \** absorbs a closing bold marker before the separator.
# Two shapes in the wild. README and CLAUDE.md write "5 bottom nav tabs: A, B,
# C"; ARCHITECTURE.md writes "5 bottom-nav tabs (A / B / C)" inside an ASCII box
# whose list wraps across a line. Both have to be read, or a stale destination
# name survives in whichever shape is not covered, which is how ARCHITECTURE.md
# kept claiming "Favorites" after the tab became Library.
NAV_TAB_CLAIM = re.compile(
    r"(\d+)\s+bottom[-\s]nav tabs\**"
    r"(?:\s*[:—–-]\s*(?P<listed>[^.\n)]+)|\s*\((?P<parenthesised>[^)]+)\))?",
    re.IGNORECASE,
)
# Box-drawing borders sit between a wrapped list and its continuation.
BOX_BORDER = re.compile(r"[│┃|]")
# Prose names the video destination "Videos"; the destination itself is VideoWallpapers.
NAV_PROSE_ALIASES = {"videos": "videowallpapers"}


class ReleaseMetadataConsistencyError(ValueError):
    """Raised when release metadata surfaces drift apart."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura release metadata consistency.")
    parser.add_argument("--policy", default=POLICY_PATH)
    parser.add_argument("--repo-root", default=".")
    return parser.parse_args()


def read_text(repo_root: Path, relative_path: str, label: str) -> str:
    path = repo_root / relative_path
    if not path.is_file():
        raise ReleaseMetadataConsistencyError(f"{label} is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def read_json(path: Path) -> Any:
    if not path.is_file():
        raise ReleaseMetadataConsistencyError(f"policy file is missing: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def require_object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ReleaseMetadataConsistencyError(f"{label} must be a JSON object")
    return value


def require_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ReleaseMetadataConsistencyError(f"{label} must be a non-empty string")
    return value.strip()


def require_int(value: Any, label: str) -> int:
    if not isinstance(value, int):
        raise ReleaseMetadataConsistencyError(f"{label} must be an integer")
    return value


def require_string_list(value: Any, label: str) -> list[str]:
    if not isinstance(value, list) or not value:
        raise ReleaseMetadataConsistencyError(f"{label} must be a non-empty list")
    values = [require_string(item, f"{label}[{index}]") for index, item in enumerate(value)]
    if len(values) != len(set(values)):
        raise ReleaseMetadataConsistencyError(f"{label} contains duplicate values")
    return values


def parse_gradle(repo_root: Path) -> dict[str, object]:
    text = read_text(repo_root, "app/build.gradle.kts", "app Gradle file")
    package = PACKAGE_RE.search(text)
    version_name = VERSION_NAME_RE.search(text)
    version_code = VERSION_CODE_RE.search(text)
    if not package or not version_name or not version_code:
        raise ReleaseMetadataConsistencyError("app/build.gradle.kts is missing package or version metadata")
    compile_sdk = COMPILE_SDK_RE.search(text)
    jvm_target = JVM_TARGET_RE.search(text)
    if not compile_sdk or not jvm_target:
        raise ReleaseMetadataConsistencyError(
            "app/build.gradle.kts is missing compileSdk or jvmTarget"
        )
    return {
        "packageName": package.group(1),
        "versionName": version_name.group(1),
        "versionCode": int(version_code.group(1)),
        "compileSdk": int(compile_sdk.group(1)),
        "jvmTarget": jvm_target.group(1),
    }


def validate_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    docs_path = require_string(policy.get("docsPath"), "docsPath")
    docs_text = read_text(repo_root, docs_path, "release metadata docs")
    for term in REQUIRED_DOC_TERMS:
        if term not in docs_text:
            raise ReleaseMetadataConsistencyError(f"{docs_path} is missing required section text: {term}")
    for source_url in REQUIRED_SOURCE_URLS:
        if source_url not in docs_text:
            raise ReleaseMetadataConsistencyError(f"{docs_path} is missing source URL: {source_url}")


def room_schema_version(repo_root: Path) -> int:
    """Highest exported Room schema, which is what the app actually ships."""
    schema_dir = repo_root / "app/schemas/com.freevibe.data.local.FreeVibeDatabase"
    versions = [
        int(path.stem)
        for path in schema_dir.glob("*.json")
        if path.stem.isdigit()
    ]
    if not versions:
        raise ReleaseMetadataConsistencyError(
            "no exported Room schemas found; cannot verify schema claims"
        )
    return max(versions)


def bottom_nav_destinations(repo_root: Path) -> list[str]:
    """The bottom navigation destinations the app actually builds."""
    text = read_text(repo_root, SCREEN_NAV, "navigation graph")
    match = BOTTOM_NAV_ITEMS.search(text)
    if not match:
        raise ReleaseMetadataConsistencyError(
            f"{SCREEN_NAV} no longer declares bottomNavItems; cannot verify tab claims"
        )
    return [entry.strip() for entry in match.group(1).split(",") if entry.strip()]


def check_nav_claims(
    relative_path: str, text: str, destinations: list[str]
) -> list[str]:
    """Compare 'N bottom nav tabs: ...' prose against the real destinations."""
    errors: list[str] = []
    known = {name.lower() for name in destinations}
    for match in NAV_TAB_CLAIM.finditer(BOX_BORDER.sub(" ", text)):
        claimed_count = int(match.group(1))
        if claimed_count != len(destinations):
            errors.append(
                f"{relative_path} claims {claimed_count} bottom nav tabs but the app "
                f"builds {len(destinations)}"
            )
        listed = match.group("listed") or match.group("parenthesised")
        if not listed:
            continue
        for raw in re.split(r"[,/]", listed):
            name = raw.strip().strip("*_`").lower()
            if not name:
                continue
            resolved = NAV_PROSE_ALIASES.get(name, name)
            if resolved not in known:
                errors.append(
                    f"{relative_path} names a '{raw.strip()}' bottom nav tab, but the "
                    f"destinations are {', '.join(destinations)}"
                )
    return errors


def validate_fact_surfaces(repo_root: Path, gradle: dict[str, object]) -> dict[str, object]:
    """Check version facts stated in prose against the build and the exported schema.

    README claimed Room v14 for several releases after the database reached v17,
    and passed every gate, because nothing compared the prose to the source of
    truth. Anything a reader could act on is checked here.
    """
    schema = room_schema_version(repo_root)
    destinations = bottom_nav_destinations(repo_root)
    checked: list[str] = []
    errors: list[str] = []

    for relative_path in FACT_SURFACES:
        path = repo_root / relative_path
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        checked.append(relative_path)

        for match in ROOM_SCHEMA_CLAIM.finditer(text):
            claimed = int(match.group(1))
            if claimed != schema:
                errors.append(
                    f"{relative_path} claims Room v{claimed} but the exported schema is v{schema}"
                )

        for match in VERSION_NAME_CLAIM.finditer(text):
            claimed = match.group(1) or match.group(2)
            if claimed != gradle["versionName"]:
                errors.append(
                    f"{relative_path} claims version {claimed} but the build declares "
                    f"{gradle['versionName']}"
                )

        for match in VERSION_CODE_CLAIM.finditer(text):
            claimed = int(match.group(1))
            if claimed != gradle["versionCode"]:
                errors.append(
                    f"{relative_path} claims versionCode {claimed} but the build declares "
                    f"{gradle['versionCode']}"
                )

        for match in ANDROID_SDK_CLAIM.finditer(text):
            claimed = int(match.group(1))
            if claimed != gradle["compileSdk"]:
                errors.append(
                    f"{relative_path} tells the reader to install Android SDK {claimed} "
                    f"but the build compiles against {gradle['compileSdk']}"
                )

        for match in JAVA_TARGET_CLAIM.finditer(text):
            claimed = match.group(1)
            if claimed != gradle["jvmTarget"]:
                errors.append(
                    f"{relative_path} claims Java {claimed} as the compile target "
                    f"but jvmTarget is {gradle['jvmTarget']}"
                )

        errors.extend(check_nav_claims(relative_path, text, destinations))

    if errors:
        raise ReleaseMetadataConsistencyError("; ".join(sorted(set(errors))))

    return {
        "factSurfaces": checked,
        "roomSchemaVersion": schema,
        "bottomNavDestinations": destinations,
    }


def validate_required_paths(repo_root: Path, policy: dict[str, Any]) -> None:
    for relative_path in require_string_list(policy.get("requiredEvidencePaths"), "requiredEvidencePaths"):
        if not (repo_root / relative_path).is_file():
            raise ReleaseMetadataConsistencyError(f"requiredEvidencePaths entry is missing: {relative_path}")


def validate_source_urls(policy: dict[str, Any]) -> int:
    urls = set(require_string_list(policy.get("sourceUrls"), "sourceUrls"))
    missing = sorted(REQUIRED_SOURCE_URLS - urls)
    if missing:
        raise ReleaseMetadataConsistencyError("sourceUrls missing required URLs: " + ", ".join(missing))
    return len(urls)


def validate_fastlane(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    metadata_root = require_string(policy.get("metadataRoot"), "metadataRoot")
    version_name = require_string(policy.get("versionName"), "versionName")
    version_code = require_int(policy.get("versionCode"), "versionCode")
    privacy_url = require_string(policy.get("privacyPolicyUrl"), "privacyPolicyUrl")
    title = read_text(repo_root, f"{metadata_root}/title.txt", "Fastlane title").strip()
    short_description = read_text(repo_root, f"{metadata_root}/short_description.txt", "Fastlane short description").strip()
    full_description = read_text(repo_root, f"{metadata_root}/full_description.txt", "Fastlane full description").strip()
    changelog = read_text(repo_root, f"{metadata_root}/changelogs/{version_code}.txt", "current Fastlane changelog")
    if not title or len(title) > TITLE_MAX_CHARS:
        raise ReleaseMetadataConsistencyError("Fastlane title is blank or too long")
    if not short_description or len(short_description) > SHORT_DESCRIPTION_MAX_CHARS or "\n" in short_description:
        raise ReleaseMetadataConsistencyError("Fastlane short description is blank, too long, or multiline")
    if not full_description or len(full_description) > FULL_DESCRIPTION_MAX_CHARS:
        raise ReleaseMetadataConsistencyError("Fastlane full description is blank or too long")
    if privacy_url not in full_description:
        raise ReleaseMetadataConsistencyError("Fastlane full description is missing privacyPolicyUrl")
    if "No ads" not in full_description or "no tracking" not in full_description:
        raise ReleaseMetadataConsistencyError("Fastlane full description is missing no ads/no tracking wording")
    if version_name not in changelog:
        raise ReleaseMetadataConsistencyError("current Fastlane changelog must mention versionName")
    if "Recent highlights:" not in changelog:
        raise ReleaseMetadataConsistencyError("current Fastlane changelog must keep recent highlights")
    return {
        "titleChars": len(title),
        "shortDescriptionChars": len(short_description),
        "fullDescriptionChars": len(full_description),
    }


def validate_readme(repo_root: Path, policy: dict[str, Any]) -> None:
    readme = read_text(repo_root, "README.md", "README")
    privacy_url = require_string(policy.get("privacyPolicyUrl"), "privacyPolicyUrl")
    if privacy_url not in readme:
        raise ReleaseMetadataConsistencyError("README is missing privacyPolicyUrl")
    for link in require_string_list(policy.get("requiredReadmeLinks"), "requiredReadmeLinks"):
        if link not in readme:
            raise ReleaseMetadataConsistencyError(f"README is missing required link: {link}")


def validate_packet_alignment(repo_root: Path, policy: dict[str, Any]) -> None:
    package_name = require_string(policy.get("packageName"), "packageName")
    privacy_url = require_string(policy.get("privacyPolicyUrl"), "privacyPolicyUrl")
    privacy_link = require_object(read_json(repo_root / "docs/privacy/privacy-policy-link.json"), "privacy link policy")
    play_packet = require_object(read_json(repo_root / "docs/distribution/play-app-content.json"), "Play App content packet")
    alt_packet = require_object(read_json(repo_root / "docs/distribution/alt-store-metadata.json"), "alt-store packet")
    if privacy_link.get("publicUrl") != privacy_url:
        raise ReleaseMetadataConsistencyError("privacy-policy link publicUrl does not match release metadata")
    if play_packet.get("packageName") != package_name:
        raise ReleaseMetadataConsistencyError("Play App content packageName does not match release metadata")
    play_privacy = require_object(
        require_object(play_packet.get("declarations"), "Play declarations").get("privacyPolicy"),
        "Play privacyPolicy",
    )
    if play_privacy.get("url") != privacy_url:
        raise ReleaseMetadataConsistencyError("Play App content privacy URL does not match release metadata")
    if alt_packet.get("packageName") != package_name:
        raise ReleaseMetadataConsistencyError("alt-store packageName does not match release metadata")


def validate_release_docs(repo_root: Path, policy: dict[str, Any]) -> None:
    commands = require_string_list(policy.get("requiredPreflightCommands"), "requiredPreflightCommands")
    artifacts = require_string_list(policy.get("requiredReleaseArtifacts"), "requiredReleaseArtifacts")
    docs = {
        "release dry-run docs": read_text(repo_root, "docs/distribution/release-dry-run.md", "release dry-run docs"),
        "release signing docs": read_text(repo_root, "docs/distribution/release-signing.md", "release signing docs"),
        "supply-chain docs": read_text(repo_root, "docs/distribution/supply-chain.md", "supply-chain docs"),
    }
    for command in commands:
        if not any(command in text for text in docs.values()):
            raise ReleaseMetadataConsistencyError(f"required preflight command is missing everywhere: {command}")
    for artifact in artifacts:
        if not any(artifact in text for text in docs.values()):
            raise ReleaseMetadataConsistencyError(f"required release artifact is missing from docs: {artifact}")
    release_docs = "\n".join(docs.values())
    for snippet in ("RELEASE_NOTES.md", "SHA256SUMS.txt"):
        if snippet not in release_docs:
            raise ReleaseMetadataConsistencyError(f"release docs missing snippet: {snippet}")


def validate_policy(repo_root: Path, policy: dict[str, Any]) -> dict[str, object]:
    if policy.get("schemaVersion") != 1:
        raise ReleaseMetadataConsistencyError("schemaVersion must be 1")
    if policy.get("policyKind") != "releaseMetadataConsistency":
        raise ReleaseMetadataConsistencyError("policyKind must be releaseMetadataConsistency")
    gradle = parse_gradle(repo_root)
    package_name = require_string(policy.get("packageName"), "packageName")
    version_name = require_string(policy.get("versionName"), "versionName")
    version_code = require_int(policy.get("versionCode"), "versionCode")
    if gradle["packageName"] != package_name:
        raise ReleaseMetadataConsistencyError("packageName does not match app/build.gradle.kts")
    # Name the stale file and both values. A version bump touches
    # app/build.gradle.kts and the Fastlane changelog but is easy to forget
    # here, and "does not match" alone left the reader guessing which of the
    # two files to edit (6.45.1 and 6.45.2 both shipped with this file stale).
    if gradle["versionName"] != version_name:
        raise ReleaseMetadataConsistencyError(
            f"{POLICY_PATH} records versionName {version_name!r} but "
            f"app/build.gradle.kts declares {gradle['versionName']!r}; "
            f"update versionName and versionCode in {POLICY_PATH}"
        )
    if gradle["versionCode"] != version_code:
        raise ReleaseMetadataConsistencyError(
            f"{POLICY_PATH} records versionCode {version_code} but "
            f"app/build.gradle.kts declares {gradle['versionCode']}; "
            f"update versionName and versionCode in {POLICY_PATH}"
        )
    validate_docs(repo_root, policy)
    validate_required_paths(repo_root, policy)
    source_url_count = validate_source_urls(policy)
    fastlane = validate_fastlane(repo_root, policy)
    validate_readme(repo_root, policy)
    validate_packet_alignment(repo_root, policy)
    validate_release_docs(repo_root, policy)
    facts = validate_fact_surfaces(repo_root, gradle)
    return {
        "status": "ok",
        "policyKind": "releaseMetadataConsistency",
        "packageName": package_name,
        "versionName": version_name,
        "versionCode": version_code,
        "sourceUrlCount": source_url_count,
        **fastlane,
        **facts,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    try:
        policy = require_object(read_json(repo_root / args.policy), "release metadata consistency policy")
        result = validate_policy(repo_root, policy)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
