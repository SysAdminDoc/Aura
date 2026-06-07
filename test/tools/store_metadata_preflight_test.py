from __future__ import annotations

import struct
import tempfile
import unittest
import zlib
from pathlib import Path

from tools.store_metadata_preflight import (
    StoreMetadataPreflightError,
    validate_store_metadata,
)


REPO_ROOT = Path(__file__).resolve().parents[2]
LIVE_METADATA_ROOT = REPO_ROOT / "fastlane" / "metadata" / "android" / "en-US"
LIVE_APP_GRADLE = REPO_ROOT / "app" / "build.gradle.kts"
LIVE_PRIVACY_POLICY = REPO_ROOT / "docs" / "privacy" / "privacy-policy.md"


def png_chunk(kind: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def png_bytes(width: int, height: int, color_type: int = 2) -> bytes:
    channels = 4 if color_type == 6 else 3
    pixel = b"\x33\x66\x99\xff" if color_type == 6 else b"\x33\x66\x99"
    row = b"\x00" + pixel * width
    payload = zlib.compress(row * height, level=9)
    header = struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + png_chunk(b"IHDR", header) + png_chunk(b"IDAT", payload) + png_chunk(b"IEND", b"")


class StoreMetadataPreflightTest(unittest.TestCase):
    def make_repo(self) -> tuple[tempfile.TemporaryDirectory[str], Path, Path, Path]:
        tmpdir = tempfile.TemporaryDirectory()
        root = Path(tmpdir.name)
        metadata_root = root / "fastlane" / "metadata" / "android" / "en-US"
        metadata_root.mkdir(parents=True)
        (root / "app").mkdir()
        (root / "docs" / "privacy").mkdir(parents=True)
        (metadata_root / "changelogs").mkdir()

        (root / "app" / "build.gradle.kts").write_text(
            '''
android {
    defaultConfig {
        applicationId = "com.freevibe"
        versionCode = 112
        versionName = "6.31.1"
    }
}
'''.strip(),
            encoding="utf-8",
        )
        (metadata_root / "title.txt").write_text("Aura\n", encoding="utf-8")
        (metadata_root / "short_description.txt").write_text(
            "Open-source wallpapers, videos, ringtones, and sounds. No ads or tracking.\n",
            encoding="utf-8",
        )
        (metadata_root / "full_description.txt").write_text(
            "Aura provides wallpapers, videos, ringtones, and sounds.\n\n"
            "No ads, no tracking, no account required.\n\n"
            "Privacy policy: https://github.com/SysAdminDoc/Aura/blob/main/docs/privacy/privacy-policy.md\n",
            encoding="utf-8",
        )
        (metadata_root / "changelogs" / "112.txt").write_text("v6.31.1\n- Current release.\n", encoding="utf-8")
        (root / "docs" / "privacy" / "privacy-policy.md").write_text(
            "# Aura Privacy Policy\n\nCovers AI-generated wallpaper prompts and local storage.\n",
            encoding="utf-8",
        )
        return tmpdir, root, metadata_root, root / "app" / "build.gradle.kts"

    def validate_fixture(self, root: Path, metadata_root: Path, require_assets: bool = False) -> dict[str, object]:
        return validate_store_metadata(
            repo_root=root,
            metadata_root=metadata_root,
            app_gradle=root / "app" / "build.gradle.kts",
            privacy_policy=root / "docs" / "privacy" / "privacy-policy.md",
            require_assets=require_assets,
        )

    def test_live_store_metadata_text_preflight_passes(self) -> None:
        result = validate_store_metadata(
            repo_root=REPO_ROOT,
            metadata_root=LIVE_METADATA_ROOT,
            app_gradle=LIVE_APP_GRADLE,
            privacy_policy=LIVE_PRIVACY_POLICY,
        )

        self.assertEqual("ok", result["status"])
        self.assertEqual("skipped", result["assets"]["status"])
        self.assertLessEqual(result["text"]["shortDescriptionChars"], 80)

    def test_rejects_short_description_over_play_limit(self) -> None:
        tmpdir, root, metadata_root, _ = self.make_repo()
        self.addCleanup(tmpdir.cleanup)
        (metadata_root / "short_description.txt").write_text("x" * 81, encoding="utf-8")

        with self.assertRaises(StoreMetadataPreflightError):
            self.validate_fixture(root, metadata_root)

    def test_rejects_missing_current_version_changelog(self) -> None:
        tmpdir, root, metadata_root, _ = self.make_repo()
        self.addCleanup(tmpdir.cleanup)
        (metadata_root / "changelogs" / "112.txt").unlink()

        with self.assertRaises(StoreMetadataPreflightError):
            self.validate_fixture(root, metadata_root)

    def test_rejects_changelog_without_current_version_name(self) -> None:
        tmpdir, root, metadata_root, _ = self.make_repo()
        self.addCleanup(tmpdir.cleanup)
        (metadata_root / "changelogs" / "112.txt").write_text("v6.30.0\n- Old release.\n", encoding="utf-8")

        with self.assertRaises(StoreMetadataPreflightError):
            self.validate_fixture(root, metadata_root)

    def test_rejects_missing_privacy_url(self) -> None:
        tmpdir, root, metadata_root, _ = self.make_repo()
        self.addCleanup(tmpdir.cleanup)
        (metadata_root / "full_description.txt").write_text(
            "Aura provides wallpapers.\n\nNo ads, no tracking.\n",
            encoding="utf-8",
        )

        with self.assertRaises(StoreMetadataPreflightError):
            self.validate_fixture(root, metadata_root)

    def test_rejects_stale_branding(self) -> None:
        tmpdir, root, metadata_root, _ = self.make_repo()
        self.addCleanup(tmpdir.cleanup)
        (metadata_root / "title.txt").write_text("FreeVibe\n", encoding="utf-8")

        with self.assertRaises(StoreMetadataPreflightError):
            self.validate_fixture(root, metadata_root)

    def test_asset_mode_accepts_play_shaped_fastlane_assets(self) -> None:
        tmpdir, root, metadata_root, _ = self.make_repo()
        self.addCleanup(tmpdir.cleanup)
        images = metadata_root / "images"
        screenshots = images / "phoneScreenshots"
        screenshots.mkdir(parents=True)
        (images / "icon.png").write_bytes(png_bytes(512, 512))
        (images / "featureGraphic.png").write_bytes(png_bytes(1024, 500))
        (screenshots / "01-wallpapers.png").write_bytes(png_bytes(1080, 1920))
        (screenshots / "02-sounds.png").write_bytes(png_bytes(1080, 1920))

        result = self.validate_fixture(root, metadata_root, require_assets=True)

        self.assertEqual("ok", result["assets"]["status"])
        self.assertEqual(2, result["assets"]["phoneScreenshotCount"])

    def test_asset_mode_rejects_alpha_png_screenshot(self) -> None:
        tmpdir, root, metadata_root, _ = self.make_repo()
        self.addCleanup(tmpdir.cleanup)
        images = metadata_root / "images"
        screenshots = images / "phoneScreenshots"
        screenshots.mkdir(parents=True)
        (images / "icon.png").write_bytes(png_bytes(512, 512))
        (images / "featureGraphic.png").write_bytes(png_bytes(1024, 500))
        (screenshots / "01-wallpapers.png").write_bytes(png_bytes(1080, 1920, color_type=6))
        (screenshots / "02-sounds.png").write_bytes(png_bytes(1080, 1920))

        with self.assertRaises(StoreMetadataPreflightError):
            self.validate_fixture(root, metadata_root, require_assets=True)

    def test_live_asset_mode_currently_blocks_missing_screenshot_pipeline(self) -> None:
        with self.assertRaises(StoreMetadataPreflightError):
            validate_store_metadata(
                repo_root=REPO_ROOT,
                metadata_root=LIVE_METADATA_ROOT,
                app_gradle=LIVE_APP_GRADLE,
                privacy_policy=LIVE_PRIVACY_POLICY,
                require_assets=True,
            )


if __name__ == "__main__":
    unittest.main()
