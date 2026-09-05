"""Keeps the cross-engine live-wallpaper soak harness honest.

The soak only means something while every shipped engine is actually wired into
it. A new wallpaper service, or an engine that quietly stops reporting what it
holds, would leave the harness green while covering less than it claims - so the
wiring itself is gated here rather than trusted.
"""

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVICE_DIR = ROOT / "app/src/main/java/com/freevibe/service"
HARNESS = "app/src/debug/java/com/freevibe/service/soak/LiveWallpaperSoak.kt"
JVM_SOAK = "app/src/test/java/com/freevibe/service/LiveWallpaperSoakTest.kt"
DEVICE_SOAK = "app/src/androidTestDebug/java/com/freevibe/service/LiveWallpaperSoakInstrumentedTest.kt"


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def wallpaper_services() -> dict:
    """Maps every shipped WallpaperService file name to its source."""
    found = {}
    for path in sorted(SERVICE_DIR.glob("*.kt")):
        source = path.read_text(encoding="utf-8")
        if re.search(r"class\s+\w+\s*:\s*WallpaperService\(\)", source):
            found[path.name] = source
    return found


def test_every_shipped_wallpaper_service_is_discovered():
    names = set(wallpaper_services())
    assert names == {
        "VideoWallpaperService.kt",
        "WeatherWallpaperService.kt",
        "ParallaxWallpaperService.kt",
    }, names


def test_every_engine_reports_the_resources_it_holds():
    for name, source in wallpaper_services().items():
        assert "LiveWallpaperResourceReporter" in source, name
        assert "override fun resourceSnapshot()" in source, name


def test_every_engine_is_named_as_a_soak_target():
    harness = read(HARNESS)
    for name in wallpaper_services():
        assert name.removesuffix(".kt") + "::class.java" in harness, name


def test_the_gif_path_is_soaked_separately_from_video():
    # GIF and video share one service but share almost no code below it: Movie
    # decode with a hand-posted frame loop versus MediaPlayer. One target for both
    # would leave whichever path lost the coin toss untested.
    harness = read(HARNESS)
    assert "GIF(" in harness
    assert '"soak-wallpaper.gif"' in harness


def test_the_soak_covers_every_lifecycle_axis_that_churns_in_the_field():
    harness = read(HARNESS)
    for scenario in (
        "surface_churn",
        "visibility_churn",
        "power_saver",
        "unlock",
        "media_replacement",
    ):
        assert '"' + scenario + '"' in harness, scenario
    for step in (
        "SURFACE_CREATED",
        "SURFACE_CHANGED",
        "SURFACE_DESTROYED",
        "POWER_SAVE_ON",
        "POWER_SAVE_OFF",
        "REPLACE_MEDIA",
        "DESTROY",
    ):
        assert step in harness, step


def test_both_soak_runs_drive_the_shared_scenario_script():
    # A drifting second copy of the script is worse than no second run at all,
    # because the two would claim to agree while testing different things.
    for path in (JVM_SOAK, DEVICE_SOAK):
        source = read(path)
        assert "LiveWallpaperSoakScenarios.ALL" in source, path
        assert "LiveWallpaperSoakDriver" in source, path
        assert "LiveWallpaperSoakTarget.entries" in source, path


def test_the_soak_asserts_boundedness_rather_than_a_hand_picked_ceiling():
    for path in (JVM_SOAK, DEVICE_SOAK):
        source = read(path)
        assert "SHORT_RUN" in source, path
        assert "LONG_RUN" in source, path
        assert "isDrained" in source, path


def test_bitmap_engines_serialize_decodes_instead_of_spawning_threads():
    # Both bitmap engines load from onSurfaceCreated and onSurfaceChanged, so a
    # bare Thread per load accumulated one full-screen decode per surface churn.
    for name in ("WeatherWallpaperService.kt", "ParallaxWallpaperService.kt"):
        source = (SERVICE_DIR / name).read_text(encoding="utf-8")
        assert "LiveWallpaperMediaLoader(" in source, name
        assert "mediaLoader.shutdown()" in source, name
        assert "Thread {" not in source, name

    loader = read("app/src/main/java/com/freevibe/service/LiveWallpaperMediaLoader.kt")
    assert "newSingleThreadExecutor" in loader
    assert "MAX_OUTSTANDING" in loader


def test_every_engine_releases_its_render_loop_when_the_surface_goes_away():
    for name, source in wallpaper_services().items():
        destroyed = source.split("override fun onSurfaceDestroyed(")[1].split(
            "override fun "
        )[0]
        released = "cancelDraw()" in destroyed or "stopTelemetryHeartbeat()" in destroyed
        assert released, name


def test_parallax_frees_its_heavy_layers_with_the_surface():
    source = (SERVICE_DIR / "ParallaxWallpaperService.kt").read_text(encoding="utf-8")
    destroyed = source.split("override fun onSurfaceDestroyed(")[1].split(
        "override fun "
    )[0]
    # A destroyed surface cannot draw these, and the wallpaper process lives for
    # days, so holding them until onDestroy is a real retention bug.
    assert "recycleBitmaps()" in destroyed
    assert "releaseSegmenter()" in destroyed
    assert "unregisterSensor()" in destroyed


def test_the_harness_documents_that_it_does_not_replace_device_captures():
    harness = read(HARNESS)
    assert "Roadmap_Blocked.md" in harness
