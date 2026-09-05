from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.legacy_android_linkage_check import (
    HAZARDS,
    LegacyAndroidLinkageError,
    validate_legacy_linkage,
)


REPO_ROOT = Path(__file__).resolve().parents[2]


class LegacyAndroidLinkageCheckTest(unittest.TestCase):
    def _repo_with(self, source: str) -> Path:
        tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(tmpdir.cleanup)
        root = Path(tmpdir.name)
        target = root / "app" / "src" / "main" / "Sample.kt"
        target.parent.mkdir(parents=True)
        target.write_text(source, encoding="utf-8")
        return root

    def test_live_sources_guard_every_search_extractor_call(self) -> None:
        result = validate_legacy_linkage(REPO_ROOT)

        self.assertEqual("ok", result["status"])
        self.assertGreater(result["scannedFileCount"], 200)
        # Sounds search and the video-wallpaper feed are the two production
        # call sites; both must be routed through the legacy helper.
        self.assertGreaterEqual(result["guardedCallCount"], 2)

    def test_every_hazard_names_a_replacement(self) -> None:
        for hazard in HAZARDS:
            self.assertTrue(hazard["pattern"])
            self.assertTrue(hazard["required"])
            self.assertTrue(hazard["reason"])
            self.assertTrue(hazard["replacement"])
            self.assertGreater(hazard["window"], 0)

    def test_rejects_a_raw_query_search_extractor_call(self) -> None:
        root = self._repo_with(
            "fun search(service: Any, query: String) =\n"
            "    service.getSearchExtractor(query)\n"
        )

        with self.assertRaises(LegacyAndroidLinkageError) as ctx:
            validate_legacy_linkage(root)

        message = str(ctx.exception)
        self.assertIn("app/src/main/Sample.kt:2", message)
        self.assertIn("getSearchExtractor(", message)
        self.assertIn("createLegacyCompatibleYouTubeSearchHandler(", message)

    def test_accepts_the_legacy_handler_on_one_line(self) -> None:
        root = self._repo_with(
            "fun search(service: Any, query: String) =\n"
            "    service.getSearchExtractor("
            "createLegacyCompatibleYouTubeSearchHandler(query))\n"
        )

        result = validate_legacy_linkage(root)

        self.assertEqual("ok", result["status"])
        self.assertEqual(1, result["guardedCallCount"])

    def test_accepts_the_legacy_handler_across_lines(self) -> None:
        root = self._repo_with(
            "fun search(service: Any, query: String) =\n"
            "    service.getSearchExtractor(\n"
            "        createLegacyCompatibleYouTubeSearchHandler(query),\n"
            "    )\n"
        )

        result = validate_legacy_linkage(root)

        self.assertEqual("ok", result["status"])
        self.assertEqual(1, result["guardedCallCount"])

    def test_rejects_a_second_unguarded_call_beside_a_guarded_one(self) -> None:
        root = self._repo_with(
            "fun guarded(service: Any, query: String) =\n"
            "    service.getSearchExtractor(\n"
            "        createLegacyCompatibleYouTubeSearchHandler(query),\n"
            "    )\n"
            "\n"
            "fun unguarded(service: Any, query: String) =\n"
            "    service.getSearchExtractor(query)\n"
        )

        with self.assertRaises(LegacyAndroidLinkageError) as ctx:
            validate_legacy_linkage(root)

        self.assertIn("app/src/main/Sample.kt:7", str(ctx.exception))

    def test_helper_declaration_further_away_than_the_window_does_not_count(self) -> None:
        root = self._repo_with(
            "fun search(service: Any, query: String) =\n"
            "    service.getSearchExtractor(query)\n"
            + "// filler\n" * 40
            + "// createLegacyCompatibleYouTubeSearchHandler(query)\n"
        )

        with self.assertRaises(LegacyAndroidLinkageError):
            validate_legacy_linkage(root)

    def test_scanner_finding_nothing_is_an_error(self) -> None:
        tmpdir = tempfile.TemporaryDirectory()
        self.addCleanup(tmpdir.cleanup)

        with self.assertRaises(LegacyAndroidLinkageError) as ctx:
            validate_legacy_linkage(Path(tmpdir.name))

        self.assertIn("not reading anything", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
