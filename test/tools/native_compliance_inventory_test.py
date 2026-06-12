import tempfile
import unittest
from pathlib import Path

from tools import native_compliance_inventory


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


class NativeComplianceInventoryTest(unittest.TestCase):
    def test_discovers_native_coordinates_from_gradle_dependencies(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_text(
                repo_root / "app/build.gradle.kts",
                """
                dependencies {
                    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.27.0")
                    implementation("io.github.junkfood02.youtubedl-android:library:0.19.0")
                    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.19.0")
                }
                """,
            )

            coordinates = native_compliance_inventory.discover_default_coordinates(repo_root)

            self.assertEqual(
                [
                    "io.github.junkfood02.youtubedl-android:common:0.19.0",
                    "io.github.junkfood02.youtubedl-android:library:0.19.0",
                    "io.github.junkfood02.youtubedl-android:ffmpeg:0.19.0",
                    "com.github.teamnewpipe:NewPipeExtractor:v0.27.0",
                ],
                [coordinate.label for coordinate in coordinates],
            )

    def test_rejects_mismatched_youtubedl_android_versions(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            write_text(
                repo_root / "app/build.gradle.kts",
                """
                dependencies {
                    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.3")
                    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
                    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.19.0")
                }
                """,
            )

            with self.assertRaisesRegex(
                ValueError,
                "library and ffmpeg versions must match",
            ):
                native_compliance_inventory.discover_default_coordinates(repo_root)

    def test_falls_back_for_external_report_fixtures_without_gradle_files(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            coordinates = native_compliance_inventory.discover_default_coordinates(Path(temp_dir))

            self.assertEqual(
                list(native_compliance_inventory.DEFAULT_COORDINATE_FALLBACK),
                [coordinate.label for coordinate in coordinates],
            )


if __name__ == "__main__":
    unittest.main()
