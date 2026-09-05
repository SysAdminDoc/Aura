from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.release_manifest import read_manifest
from tools.release_metadata_consistency_check import (
    ReleaseMetadataConsistencyError,
    parse_gradle,
    validate_policy,
)


class FixtureNotClean(AssertionError):
    """The fixture failed before its test seeded anything.

    Raised as an AssertionError so it reads as a test-setup failure rather than
    as the gate rejecting a seeded defect.
    """


REPO_ROOT = Path(__file__).resolve().parents[2]
SCREEN_NAV = "app/src/main/java/com/freevibe/ui/navigation/Screen.kt"
SCHEMA_DIR = "app/schemas/com.freevibe.data.local.FreeVibeDatabase"
FASTLANE_DIR = "fastlane/metadata/android/en-US"


def live_policy() -> dict[str, object]:
    return json.loads((REPO_ROOT / "docs/distribution/release-metadata-consistency.json").read_text(encoding="utf-8"))


def copy_required_tree(destination: Path) -> None:
    policy = live_policy()
    paths = set(policy["requiredEvidencePaths"])  # type: ignore[arg-type]
    paths.add(policy["docsPath"])  # type: ignore[arg-type]
    paths.add("docs/distribution/release-metadata-consistency.json")
    # Sources of truth the fact-surface checks compare prose against.
    paths.add(SCREEN_NAV)
    paths.update(
        path.relative_to(REPO_ROOT).as_posix()
        for path in (REPO_ROOT / SCHEMA_DIR).glob("*.json")
    )
    # Fastlane metadata is validated before the fact surfaces, so a fixture
    # without it fails early and hides whatever the test was actually asserting.
    paths.update(
        path.relative_to(REPO_ROOT).as_posix()
        for path in (REPO_ROOT / FASTLANE_DIR).rglob("*")
        if path.is_file()
    )
    for relative_path in paths:
        source = REPO_ROOT / str(relative_path)
        target = destination / str(relative_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(source.read_text(encoding="utf-8"), encoding="utf-8")


def consistent_fixture(destination: Path) -> dict[str, object]:
    """Copy the evidence tree and return a policy that agrees with it.

    `validate_policy` checks the policy's versionName/versionCode against
    app/build.gradle.kts before it reaches any other check. A seeded-defect
    test that passes `live_policy()` therefore stops testing what it claims
    the moment the live policy and the live build drift apart: it still
    raises, so it still passes, but for the wrong reason. That is exactly
    what happened while the policy sat at 6.45.0 through two version bumps.

    Pinning the fixture's policy to the fixture's own build file makes every
    seeded defect the only thing left that can fail.

    Version drift was the observed failure, but it is not the only live state a
    fixture inherits: `copy_required_tree` copies README.md, the Fastlane
    metadata and every other evidence file verbatim, so a stale value in any of
    them breaks every fixture test at once. The fixture is therefore validated
    before the caller seeds anything, and a failure there is raised as
    `FixtureNotClean` naming the live file at fault. That does not decouple the
    tests from live data, but it stops an unrelated live defect from wearing the
    costume of a seeded one.
    """
    copy_required_tree(destination)
    policy = copy.deepcopy(live_policy())
    # parse_gradle is the gate's own reader, so the fixture cannot disagree
    # with the check it is about to run.
    gradle = parse_gradle(destination)
    policy["versionName"] = gradle["versionName"]
    policy["versionCode"] = gradle["versionCode"]
    try:
        validate_policy(destination, policy)
    except ReleaseMetadataConsistencyError as exc:
        raise FixtureNotClean(
            "the fixture copied from the live repository does not pass before any "
            f"defect was seeded, so no seeded-defect assertion below is meaningful: {exc}"
        ) from exc
    return policy


class ReleaseMetadataConsistencyCheckTest(unittest.TestCase):
    def test_live_release_metadata_consistency_passes(self) -> None:
        result = validate_policy(REPO_ROOT, live_policy())

        self.assertEqual("ok", result["status"])
        self.assertEqual("com.freevibe", result["packageName"])
        # Derived from the release manifest, not restated: a hardcoded literal
        # here is exactly the stale fixture this gate is supposed to catch.
        manifest = read_manifest(REPO_ROOT)
        self.assertEqual(manifest["versionName"], result["versionName"])
        self.assertEqual(manifest["versionCode"], result["versionCode"])

    def test_rejects_version_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            policy = consistent_fixture(repo)
            actual_code = parse_gradle(repo)["versionCode"]
            policy["versionCode"] = 999

            with self.assertRaises(ReleaseMetadataConsistencyError) as ctx:
                validate_policy(repo, policy)

            # The message has to say which file is stale and what to put in it.
            # 6.45.1 and 6.45.2 both shipped with this file behind the build,
            # so "does not match" on its own was not enough to act on.
            message = str(ctx.exception)
            self.assertIn("docs/distribution/release-metadata-consistency.json", message)
            self.assertIn("999", message)
            self.assertIn(str(actual_code), message)
            self.assertIn("versionName and versionCode", message)

    def test_rejects_version_name_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            policy = consistent_fixture(repo)
            actual_name = parse_gradle(repo)["versionName"]
            policy["versionName"] = "0.0.1-stale"

            with self.assertRaises(ReleaseMetadataConsistencyError) as ctx:
                validate_policy(repo, policy)

            message = str(ctx.exception)
            self.assertIn("docs/distribution/release-metadata-consistency.json", message)
            self.assertIn("0.0.1-stale", message)
            self.assertIn(str(actual_name), message)

    def test_rejects_missing_readme_link(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            policy = consistent_fixture(repo)
            readme = repo / "README.md"
            readme.write_text(
                readme.read_text(encoding="utf-8").replace("docs/distribution/alt-store-metadata.md", ""),
                encoding="utf-8",
            )

            with self.assertRaises(ReleaseMetadataConsistencyError) as ctx:
                validate_policy(repo, policy)

            self.assertIn("alt-store-metadata.md", str(ctx.exception))

    def test_rejects_missing_release_preflight_command(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            policy = consistent_fixture(repo)
            for relative_path in (
                "docs/distribution/release-dry-run.md",
                "docs/distribution/release-signing.md",
                "docs/distribution/supply-chain.md",
            ):
                release_docs = repo / relative_path
                release_docs.write_text(
                    release_docs.read_text(encoding="utf-8").replace("tools/alt_store_metadata_check.py", ""),
                    encoding="utf-8",
                )

            with self.assertRaises(ReleaseMetadataConsistencyError) as ctx:
                validate_policy(repo, policy)

            self.assertIn("alt_store_metadata_check.py", str(ctx.exception))

    def test_rejects_privacy_url_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            policy = consistent_fixture(repo)
            privacy_link = repo / "docs/privacy/privacy-policy-link.json"
            data = json.loads(privacy_link.read_text(encoding="utf-8"))
            data["publicUrl"] = "https://example.invalid/privacy"
            privacy_link.write_text(json.dumps(data), encoding="utf-8")

            with self.assertRaises(ReleaseMetadataConsistencyError) as ctx:
                validate_policy(repo, policy)

            self.assertIn("privacy", str(ctx.exception).lower())

    def _drifted(self, mutate, surface: str = "README.md") -> str:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            policy = consistent_fixture(repo)
            target = repo / surface
            target.write_text(mutate(target.read_text(encoding="utf-8")), encoding="utf-8")

            with self.assertRaises(ReleaseMetadataConsistencyError) as ctx:
                validate_policy(repo, policy)
            return str(ctx.exception)

    def _clean_fixture_result(self) -> dict[str, object]:
        """Run the gate on an unmutated fixture, so a live drift cannot mask it."""
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            policy = consistent_fixture(repo)
            return validate_policy(repo, policy)

    def test_fact_surfaces_are_actually_read(self) -> None:
        result = self._clean_fixture_result()

        self.assertIn("README.md", result["factSurfaces"])
        self.assertEqual(read_manifest(REPO_ROOT)["roomSchemaVersion"], result["roomSchemaVersion"])

    def test_rejects_a_stale_room_schema_claim(self) -> None:
        message = self._drifted(lambda text: text.replace("Room DB v17", "Room DB v14"))

        self.assertIn("Room v14", message)
        self.assertIn("v17", message)

    def test_rejects_a_stale_version_badge(self) -> None:
        # Derived, not hardcoded: a literal version here becomes the stale
        # fixture this gate exists to catch the moment the app is bumped.
        current = str(read_manifest(REPO_ROOT)["versionName"])
        stale = "0.0.1"
        message = self._drifted(
            lambda text: text.replace(f"version-{current}-blue", f"version-{stale}-blue")
        )

        self.assertIn(stale, message)

    def test_rejects_a_tab_count_that_does_not_match_the_app(self) -> None:
        message = self._drifted(lambda text: text.replace("5 bottom nav tabs", "4 bottom nav tabs"))

        self.assertIn("4 bottom nav tabs", message)

    def test_rejects_a_tab_name_the_app_does_not_build(self) -> None:
        message = self._drifted(
            lambda text: text.replace(
                "Wallpapers, Videos, Sounds, Library, Settings",
                "Wallpapers, Videos, Sounds, Favorites, Settings",
            )
        )

        self.assertIn("Favorites", message)

    def test_rejects_a_stale_nav_name_in_the_architecture_diagram(self) -> None:
        # ARCHITECTURE.md writes its tab list as "(A / B / C)" inside an ASCII
        # box that wraps mid-list, which no gate read, so it claimed a
        # "Favorites" tab long after the destination became Library.
        message = self._drifted(
            lambda text: text.replace("Library / Settings)", "Favorites / Settings)"),
            surface="ARCHITECTURE.md",
        )

        self.assertIn("ARCHITECTURE.md names a 'Favorites' bottom nav tab", message)
        self.assertIn("Library", message)

    def test_rejects_a_stale_tab_count_in_the_architecture_diagram(self) -> None:
        message = self._drifted(
            lambda text: text.replace("5 bottom-nav tabs", "4 bottom-nav tabs"),
            surface="ARCHITECTURE.md",
        )

        self.assertIn("ARCHITECTURE.md claims 4 bottom nav tabs but the app builds 5", message)

    def test_rejects_a_stale_room_version_in_architecture(self) -> None:
        message = self._drifted(
            lambda text: text.replace("Room DB v17", "Room DB v14"),
            surface="ARCHITECTURE.md",
        )

        self.assertIn("ARCHITECTURE.md claims Room v14", message)
        self.assertIn("v17", message)

    def test_rejects_a_contributing_sdk_claim_the_build_contradicts(self) -> None:
        message = self._drifted(
            lambda text: text.replace("Android SDK 36", "Android SDK 35"),
            surface="CONTRIBUTING.md",
        )

        self.assertIn("install Android SDK 35", message)
        self.assertIn("compiles against 36", message)

    def test_rejects_a_contributing_java_target_the_build_contradicts(self) -> None:
        message = self._drifted(
            lambda text: text.replace(
                "Java 17 as the compile target", "Java 21 as the compile target"
            ),
            surface="CONTRIBUTING.md",
        )

        self.assertIn("claims Java 21 as the compile target", message)
        self.assertIn("jvmTarget is 17", message)

    def test_accepts_the_videos_alias_for_the_video_destination(self) -> None:
        """Prose says "Videos"; the destination is VideoWallpapers. Both are correct."""
        result = self._clean_fixture_result()

        self.assertIn("VideoWallpapers", result["bottomNavDestinations"])
        self.assertEqual("ok", result["status"])


if __name__ == "__main__":
    unittest.main()
