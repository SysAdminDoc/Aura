from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def assert_contains(source: str, expected: str, label: str) -> None:
    assert expected in source, f"{label} missing {expected}"


def test_shared_browse_rail_uses_flat_touch_safe_tabs() -> None:
    source = read("app/src/main/java/com/freevibe/ui/components/BrowseRail.kt")

    for expected in (
        "data class BrowseRailItem",
        "fun BrowseRail(",
        "horizontalScroll(rememberScrollState())",
        "heightIn(min = 48.dp)",
        "shape = AuraShapes.None",
        "MaterialTheme.colorScheme.primary",
    ):
        assert_contains(source, expected, "BrowseRail")

    assert "FilterChip(" not in source
    assert "Icons.Default.Check" not in source


def test_primary_media_screens_keep_only_the_navigation_they_need() -> None:
    screens = {
        "wallpapers": read("app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt"),
        "videos": read("app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt"),
        "sounds": read("app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt"),
    }

    for expected in (
        "BrowseRail(",
        "browse_rail_popular",
        "browse_rail_newest",
        "browse_rail_categories",
        "browse_rail_collections",
    ):
        assert_contains(screens["wallpapers"], expected, "wallpapers")

    assert "BrowseRail(" not in screens["videos"]
    assert "BrowseRail(" not in screens["sounds"]
    for expected in ("searchExpanded", "showQuickMenu", "browse_rail_local"):
        assert_contains(screens["videos"], expected, "videos")
    for expected in ("searchExpanded", "showQuickActionsMenu", "SoundModeBar("):
        assert_contains(screens["sounds"], expected, "sounds")
    for expected in ("MIN_INITIAL_VIDEO_RESULTS = 24", "CircularProgressIndicator", "ShimmerBox"):
        assert_contains(screens["videos"], expected, "videos")
    for expected in ("CircularProgressIndicator", "ShimmerSoundList"):
        assert_contains(screens["sounds"], expected, "sounds")

    assert "metadataBadges" not in screens["videos"]
    assert "sounds_card_ready" not in screens["sounds"]
    assert "badges.joinToString" not in screens["sounds"]


def test_browse_rails_reuse_existing_category_collection_and_local_recovery_paths() -> None:
    root = read("app/src/main/java/com/freevibe/ui/FreeVibeRoot.kt")
    wallpapers = read("app/src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt")
    videos = read("app/src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt")
    sounds = read("app/src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt")

    for expected in ("onCategoriesClick", "onCollectionsClick"):
        assert_contains(wallpapers, expected, "wallpapers")
    for expected in (
        "onCategoriesClick = {",
        "Screen.Categories.route",
        "onCollectionsClick = {",
        "Screen.Collections.route",
    ):
        assert_contains(root, expected, "root")

    for expected in (
        "showFiltersSheet = true",
        "galleryLauncher.launch(videoWallpaperMimeTypes())",
        "secondaryAction = AuraStateAction(stringResource(R.string.video_wp_error_gallery)",
    ):
        assert_contains(videos, expected, "videos")

    for expected in (
        'createAudioPickerLauncher.launch("audio/*")',
        "secondaryAction = AuraStateAction(",
        "R.string.editor_sound_browse",
        "contentSounds",
    ):
        assert_contains(sounds, expected, "sounds")
    assert "Top 5 This Week" not in sounds
    assert "SoundCollectionCarousel(" not in sounds
    assert not (
        ROOT
        / "app/src/main/java/com/freevibe/ui/screens/sounds/SoundTopHitsLoader.kt"
    ).exists()
    assert_contains(videos, "VideoImmersivePager(", "videos")
    assert_contains(videos, "VerticalPager(", "videos")
