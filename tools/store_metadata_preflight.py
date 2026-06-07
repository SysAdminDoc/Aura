#!/usr/bin/env python3
"""Validate Aura's committed store-listing metadata."""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
from pathlib import Path
from typing import Any


TITLE_MAX_CHARS = 30
SHORT_DESCRIPTION_MAX_CHARS = 80
FULL_DESCRIPTION_MAX_CHARS = 4000
DEFAULT_METADATA_ROOT = "fastlane/metadata/android/en-US"
STALE_BRAND_RE = re.compile(r"\bFreeVibe\b")
PRIVACY_URL_RE = re.compile(r"https?://\S*(?:privacy|privacy-policy)\S*", re.IGNORECASE)
SHORT_DESCRIPTION_FORBIDDEN = (
    "download now",
    "install now",
    "play now",
    "try now",
    "#1",
    "million downloads",
)
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
JPEG_SIGNATURE = b"\xff\xd8"


class StoreMetadataPreflightError(ValueError):
    """Raised when committed store metadata is stale or incomplete."""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate Aura Fastlane store metadata.")
    parser.add_argument("--repo-root", default=".")
    parser.add_argument("--metadata-root", default=DEFAULT_METADATA_ROOT)
    parser.add_argument("--app-gradle", default="app/build.gradle.kts")
    parser.add_argument("--privacy-policy", default="docs/privacy/privacy-policy.md")
    parser.add_argument(
        "--require-assets",
        action="store_true",
        help="Also require Play/Fastlane image assets. Disabled until the screenshot pipeline lands.",
    )
    parser.add_argument("--min-phone-screenshots", type=int, default=2)
    return parser.parse_args()


def read_text(path: Path, label: str) -> str:
    if not path.is_file():
        raise StoreMetadataPreflightError(f"{label} is missing: {path}")
    return path.read_text(encoding="utf-8")


def read_store_text(path: Path, label: str) -> str:
    text = read_text(path, label).strip()
    if not text:
        raise StoreMetadataPreflightError(f"{label} must not be blank")
    return text


def parse_gradle_metadata(app_gradle: Path) -> dict[str, Any]:
    text = read_text(app_gradle, "app Gradle file")
    patterns = {
        "applicationId": r'applicationId\s*=\s*"([^"]+)"',
        "versionName": r'versionName\s*=\s*"([^"]+)"',
        "versionCode": r"versionCode\s*=\s*(\d+)",
    }
    values: dict[str, Any] = {}
    for key, pattern in patterns.items():
        match = re.search(pattern, text)
        if not match:
            raise StoreMetadataPreflightError(f"app Gradle file is missing {key}")
        values[key] = match.group(1)
    values["versionCode"] = int(values["versionCode"])
    return values


def validate_text_metadata(metadata_root: Path, privacy_policy: Path, gradle: dict[str, Any]) -> dict[str, Any]:
    title = read_store_text(metadata_root / "title.txt", "store title")
    short_description = read_store_text(metadata_root / "short_description.txt", "short description")
    full_description = read_store_text(metadata_root / "full_description.txt", "full description")

    if len(title) > TITLE_MAX_CHARS:
        raise StoreMetadataPreflightError(f"store title is {len(title)} chars; max is {TITLE_MAX_CHARS}")
    if len(short_description) > SHORT_DESCRIPTION_MAX_CHARS:
        raise StoreMetadataPreflightError(
            f"short description is {len(short_description)} chars; max is {SHORT_DESCRIPTION_MAX_CHARS}"
        )
    if "\n" in short_description or "\r" in short_description:
        raise StoreMetadataPreflightError("short description must be a single line")
    lowered_short = short_description.lower()
    for phrase in SHORT_DESCRIPTION_FORBIDDEN:
        if phrase in lowered_short:
            raise StoreMetadataPreflightError(f"short description contains disallowed phrase: {phrase}")
    if len(full_description) > FULL_DESCRIPTION_MAX_CHARS:
        raise StoreMetadataPreflightError(
            f"full description is {len(full_description)} chars; max is {FULL_DESCRIPTION_MAX_CHARS}"
        )
    for label, text in (
        ("store title", title),
        ("short description", short_description),
        ("full description", full_description),
    ):
        if STALE_BRAND_RE.search(text):
            raise StoreMetadataPreflightError(f"{label} contains stale FreeVibe branding")

    if not PRIVACY_URL_RE.search(full_description):
        raise StoreMetadataPreflightError("full description must include a public privacy-policy URL")
    if "no ads" not in full_description.lower() or "no tracking" not in full_description.lower():
        raise StoreMetadataPreflightError("full description must preserve the no ads / no tracking claim")

    privacy_text = read_text(privacy_policy, "privacy policy").lower()
    if "aura" not in privacy_text or "generated wallpaper" not in privacy_text:
        raise StoreMetadataPreflightError("privacy policy must cover Aura and generated content")

    changelog_path = metadata_root / "changelogs" / f"{gradle['versionCode']}.txt"
    changelog = read_store_text(changelog_path, f"versionCode {gradle['versionCode']} changelog")
    if gradle["versionName"] not in changelog:
        raise StoreMetadataPreflightError(
            f"versionCode {gradle['versionCode']} changelog must mention versionName {gradle['versionName']}"
        )

    return {
        "titleChars": len(title),
        "shortDescriptionChars": len(short_description),
        "fullDescriptionChars": len(full_description),
        "changelog": str(changelog_path),
    }


def png_info(path: Path) -> tuple[int, int, int, int]:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE) or len(data) < 33:
        raise StoreMetadataPreflightError(f"{path} is not a valid PNG")
    length = struct.unpack(">I", data[8:12])[0]
    chunk_type = data[12:16]
    if chunk_type != b"IHDR" or length != 13:
        raise StoreMetadataPreflightError(f"{path} is missing a valid PNG IHDR")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    return width, height, bit_depth, color_type


def jpeg_info(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    if not data.startswith(JPEG_SIGNATURE):
        raise StoreMetadataPreflightError(f"{path} is not a valid JPEG")
    index = 2
    while index + 9 < len(data):
        if data[index] != 0xFF:
            index += 1
            continue
        marker = data[index + 1]
        index += 2
        if marker in {0xD8, 0xD9}:
            continue
        if index + 2 > len(data):
            break
        segment_length = struct.unpack(">H", data[index : index + 2])[0]
        if marker in {0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7, 0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF}:
            if index + 7 > len(data):
                break
            height, width = struct.unpack(">HH", data[index + 3 : index + 7])
            return width, height
        index += segment_length
    raise StoreMetadataPreflightError(f"{path} is missing JPEG dimensions")


def image_info(path: Path) -> dict[str, Any]:
    suffix = path.suffix.lower()
    if suffix == ".png":
        width, height, bit_depth, color_type = png_info(path)
        return {
            "path": str(path),
            "format": "png",
            "width": width,
            "height": height,
            "bitDepth": bit_depth,
            "colorType": color_type,
            "hasAlpha": color_type in {4, 6},
        }
    if suffix in {".jpg", ".jpeg"}:
        width, height = jpeg_info(path)
        return {
            "path": str(path),
            "format": "jpeg",
            "width": width,
            "height": height,
            "bitDepth": None,
            "colorType": None,
            "hasAlpha": False,
        }
    raise StoreMetadataPreflightError(f"{path} must be PNG or JPEG")


def require_24bit_or_jpeg(info: dict[str, Any], label: str) -> None:
    if info["format"] == "png" and (info["bitDepth"] != 8 or info["colorType"] != 2):
        raise StoreMetadataPreflightError(f"{label} must be a 24-bit PNG without alpha or a JPEG: {info['path']}")
    if info["hasAlpha"]:
        raise StoreMetadataPreflightError(f"{label} must not contain alpha: {info['path']}")


def validate_assets(metadata_root: Path, min_phone_screenshots: int) -> dict[str, Any]:
    images_root = metadata_root / "images"
    icon_path = images_root / "icon.png"
    if not icon_path.is_file():
        raise StoreMetadataPreflightError("Fastlane images/icon.png is required")
    icon = image_info(icon_path)
    if icon["format"] != "png" or icon["width"] != 512 or icon["height"] != 512:
        raise StoreMetadataPreflightError("Fastlane icon must be a 512x512 PNG")

    feature_candidates = [
        images_root / "featureGraphic.png",
        images_root / "featureGraphic.jpg",
        images_root / "featureGraphic.jpeg",
    ]
    feature_path = next((path for path in feature_candidates if path.is_file()), None)
    if feature_path is None:
        raise StoreMetadataPreflightError("Fastlane featureGraphic PNG/JPEG is required")
    feature = image_info(feature_path)
    require_24bit_or_jpeg(feature, "feature graphic")
    if feature["width"] != 1024 or feature["height"] != 500:
        raise StoreMetadataPreflightError("feature graphic must be 1024x500")

    screenshots_root = images_root / "phoneScreenshots"
    screenshots = sorted(
        path
        for path in screenshots_root.glob("*")
        if path.is_file() and path.suffix.lower() in {".png", ".jpg", ".jpeg"}
    )
    if len(screenshots) < min_phone_screenshots:
        raise StoreMetadataPreflightError(
            f"at least {min_phone_screenshots} phone screenshots are required"
        )

    screenshot_infos = []
    for path in screenshots:
        info = image_info(path)
        require_24bit_or_jpeg(info, "phone screenshot")
        smallest = min(info["width"], info["height"])
        largest = max(info["width"], info["height"])
        if smallest < 320 or largest > 3840:
            raise StoreMetadataPreflightError(f"phone screenshot dimensions are outside Play limits: {path}")
        if largest > smallest * 2:
            raise StoreMetadataPreflightError(f"phone screenshot aspect ratio is outside Play limits: {path}")
        screenshot_infos.append(info)

    return {
        "status": "ok",
        "icon": icon,
        "featureGraphic": feature,
        "phoneScreenshotCount": len(screenshot_infos),
    }


def validate_store_metadata(
    repo_root: Path,
    metadata_root: Path,
    app_gradle: Path,
    privacy_policy: Path,
    require_assets: bool = False,
    min_phone_screenshots: int = 2,
) -> dict[str, Any]:
    gradle = parse_gradle_metadata(app_gradle)
    text_result = validate_text_metadata(metadata_root, privacy_policy, gradle)
    asset_result: dict[str, Any] = {"status": "skipped"}
    if require_assets:
        asset_result = validate_assets(metadata_root, min_phone_screenshots)

    return {
        "policyKind": "storeMetadataPreflight",
        "schemaVersion": 1,
        "status": "ok",
        "applicationId": gradle["applicationId"],
        "versionCode": gradle["versionCode"],
        "versionName": gradle["versionName"],
        "metadataRoot": str(metadata_root.relative_to(repo_root) if metadata_root.is_relative_to(repo_root) else metadata_root),
        "text": text_result,
        "assets": asset_result,
    }


def main() -> int:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    metadata_root = (repo_root / args.metadata_root).resolve()
    app_gradle = (repo_root / args.app_gradle).resolve()
    privacy_policy = (repo_root / args.privacy_policy).resolve()
    try:
        result = validate_store_metadata(
            repo_root=repo_root,
            metadata_root=metadata_root,
            app_gradle=app_gradle,
            privacy_policy=privacy_policy,
            require_assets=args.require_assets,
            min_phone_screenshots=args.min_phone_screenshots,
        )
    except (OSError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
