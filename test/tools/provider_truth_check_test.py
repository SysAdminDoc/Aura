from __future__ import annotations

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from tools.provider_truth_check import ProviderTruthError, validate_provider_truth

REPO_ROOT = Path(__file__).resolve().parents[2]
MANIFEST = "docs/providers/provider-manifest.json"
FIXTURE_PATHS = (
    MANIFEST,
    "app/src/main/java/com/freevibe/data/legal/ProviderCapability.kt",
    "app/src/main/java/com/freevibe/data/model/Models.kt",
    "README.md",
    "fastlane/metadata/android/en-US/full_description.txt",
    "docs/distribution/play-app-content.json",
    "docs/distribution/alt-store-metadata.json",
    "app/build.gradle.kts",
    "app/src/main/java/com/freevibe/data/local/PreferencesManager.kt",
    "app/src/main/java/com/freevibe/FreeVibeApp.kt",
    "app/src/main/java/com/freevibe/data/remote/ProviderAvailabilityInterceptor.kt",
    "app/src/main/java/com/freevibe/di/AppModule.kt",
    "app/src/main/java/com/freevibe/ui/screens/licenses/LicensesScreen.kt",
    "app/src/main/java/com/freevibe/ui/screens/settings/SettingsSoundSection.kt",
    "app/src/main/java/com/freevibe/data/model/ProviderNetworkPolicy.kt",
    "app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperBrowseViewModel.kt",
    "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpaperQuality.kt",
    "app/src/main/java/com/freevibe/ui/screens/sounds/SoundQuality.kt",
    "app/src/main/java/com/freevibe/data/model/SoundLicensePolicy.kt",
    "app/src/main/java/com/freevibe/ui/screens/sounds/SoundPlaybackActions.kt",
    "app/src/main/java/com/freevibe/data/model/WallpaperLicensePolicy.kt",
    "app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperApplyActions.kt",
    "app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt",
    "app/src/main/java/com/freevibe/ui/FreeVibeRoot.kt",
    "app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperPreviewScreen.kt",
    "app/src/main/java/com/freevibe/data/model/VideoWallpaperLicensePolicy.kt",
    "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt",
    "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoFeedActions.kt",
    "app/src/main/java/com/freevibe/service/VideoPreviewCache.kt",
    "app/src/main/java/com/freevibe/data/repository/YouTubeRepository.kt",
    "docs/distribution/release-dry-run.md",
    "docs/distribution/release-signing.md",
    "docs/distribution/supply-chain.md",
)


def make_fixture(destination: Path) -> dict[str, object]:
    for relative in FIXTURE_PATHS:
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(REPO_ROOT / relative, target)
    manifest_path = destination / MANIFEST
    return json.loads(manifest_path.read_text(encoding="utf-8"))


def write_manifest(root: Path, manifest: dict[str, object]) -> None:
    (root / MANIFEST).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


class ProviderTruthCheckTest(unittest.TestCase):
    def test_live_provider_contract_passes(self) -> None:
        result = validate_provider_truth(REPO_ROOT, MANIFEST)

        self.assertEqual("ok", result["status"])
        self.assertEqual(23, result["providerCount"])
        self.assertEqual(8, result["legacyProviderCount"])
        self.assertEqual(4, result["publicSurfaceCount"])
        self.assertEqual(26, result["runtimeSurfaceCount"])

    def test_runtime_priority_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            runtime_path = root / "app/src/main/java/com/freevibe/data/legal/ProviderCapability.kt"
            runtime = runtime_path.read_text(encoding="utf-8")
            runtime = runtime.replace("ProviderMediaType.WALLPAPER to 0", "ProviderMediaType.WALLPAPER to 5", 1)
            runtime_path.write_text(runtime, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, r"REDDIT\.defaultPriority"):
                validate_provider_truth(root, MANIFEST)

    def test_lifecycle_change_without_readme_update_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            manifest = make_fixture(root)
            wallhaven = next(  # type: ignore[arg-type]
                item for item in manifest["providers"] if item["id"] == "WALLHAVEN"
            )
            wallhaven["lifecycle"] = "COMMUNITY"
            write_manifest(root, manifest)

            runtime_path = root / "app/src/main/java/com/freevibe/data/legal/ProviderCapability.kt"
            runtime = runtime_path.read_text(encoding="utf-8")
            before, wallhaven_and_after = runtime.split("source = ContentSource.WALLHAVEN,", 1)
            wallhaven_block, after = wallhaven_and_after.split("legacy(ContentSource.PICSUM", 1)
            wallhaven_block = wallhaven_block.replace(
                "lifecycle = ProviderLifecycle.ACTIVE",
                "lifecycle = ProviderLifecycle.COMMUNITY",
                1,
            )
            runtime_path.write_text(
                before + "source = ContentSource.WALLHAVEN," + wallhaven_block + "legacy(ContentSource.PICSUM" + after,
                encoding="utf-8",
            )

            with self.assertRaisesRegex(ProviderTruthError, "README.md provider table"):
                validate_provider_truth(root, MANIFEST)

    def test_channel_change_without_store_or_packet_update_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            manifest = make_fixture(root)
            youtube = next(  # type: ignore[arg-type]
                item for item in manifest["providers"] if item["id"] == "YOUTUBE"
            )
            youtube["releaseChannels"].append("PLAY")
            write_manifest(root, manifest)

            runtime_path = root / "app/src/main/java/com/freevibe/data/legal/ProviderCapability.kt"
            runtime = runtime_path.read_text(encoding="utf-8")
            runtime = runtime.replace("channels = GITHUB_ONLY", "channels = ALL_CHANNELS", 1)
            runtime_path.write_text(runtime, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "Fastlane provider availability"):
                validate_provider_truth(root, MANIFEST)

    def test_legacy_provider_must_be_described_as_attribution_only(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            manifest = make_fixture(root)
            freesound = next(  # type: ignore[arg-type]
                item for item in manifest["providers"] if item["id"] == "FREESOUND"
            )
            freesound["publicSummary"] = "Browse older sound content."
            write_manifest(root, manifest)

            with self.assertRaisesRegex(ProviderTruthError, "Legacy attribution only"):
                validate_provider_truth(root, MANIFEST)

    def test_youtube_channel_gate_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/data/local/PreferencesManager.kt"
            text = path.read_text(encoding="utf-8").replace(
                "enabled && youtubeProviderAvailable",
                "enabled",
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "preferences runtime surface"):
                validate_provider_truth(root, MANIFEST)

    def test_manifest_driven_video_ranking_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpaperQuality.kt"
            text = path.read_text(encoding="utf-8").replace(
                "orderedCurrentProviderCapabilities",
                "orderedProviderCapabilities",
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "videoFeed runtime surface"):
                validate_provider_truth(root, MANIFEST)

    def test_provider_action_ceiling_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/data/model/SoundLicensePolicy.kt"
            text = path.read_text(encoding="utf-8").replace(
                "isProviderActionPermitted",
                "providerActionPermitted",
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "soundActions runtime surface"):
                validate_provider_truth(root, MANIFEST)

    def test_wallpaper_preview_apply_bypass_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/ui/FreeVibeRoot.kt"
            text = path.read_text(encoding="utf-8").replace(
                "if (!canApplyFromWallpaperPreview(wallpaper))",
                "if (false)",
                1,
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "wallpaper preview navigation"):
                validate_provider_truth(root, MANIFEST)

    def test_video_cache_restore_without_artifact_filter_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoFeedActions.kt"
            text = path.read_text(encoding="utf-8").replace(
                "filterVideoMetadataForCurrentArtifact(cached.result)",
                "cached.result",
                1,
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "video cache restore"):
                validate_provider_truth(root, MANIFEST)

    def test_video_preview_transport_without_provider_guard_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/service/VideoPreviewCache.kt"
            text = path.read_text(encoding="utf-8").replace(
                "if (!isVideoPreviewHostAllowed(host))",
                "if (false)",
                1,
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "video preview transport"):
                validate_provider_truth(root, MANIFEST)

    def test_youtube_extractor_unconditional_init_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/data/repository/YouTubeRepository.kt"
            text = path.read_text(encoding="utf-8").replace(
                "if (isYouTubeRuntimeAvailable()) {",
                "if (true) {",
                1,
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "YouTube extractor init"):
                validate_provider_truth(root, MANIFEST)

    def test_artifact_action_gate_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/data/legal/ProviderCapability.kt"
            text = path.read_text(encoding="utf-8").replace(
                "capability.lifecycle == ProviderLifecycle.LEGACY || capability.availableIn(build, channel)",
                "true",
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "artifact-action marker"):
                validate_provider_truth(root, MANIFEST)

    def test_active_nasa_legacy_diagnostics_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "app/src/main/java/com/freevibe/data/model/ProviderNetworkPolicy.kt"
            text = path.read_text(encoding="utf-8").replace(
                "One APOD request per Discover refresh; random history requests stay inside the secondary-source budget.",
                "Legacy restored records only; no active automatic fetching.",
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "active NASA lifecycle"):
                validate_provider_truth(root, MANIFEST)

    def test_play_bundle_without_channel_property_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            root = Path(tmpdir)
            make_fixture(root)
            path = root / "docs/distribution/release-dry-run.md"
            text = path.read_text(encoding="utf-8").replace(
                "-PauraReleaseChannel=play :app:bundleFullRelease",
                ":app:bundleFullRelease",
            )
            path.write_text(text, encoding="utf-8")

            with self.assertRaisesRegex(ProviderTruthError, "releaseDryRun runtime surface"):
                validate_provider_truth(root, MANIFEST)


if __name__ == "__main__":
    unittest.main()
