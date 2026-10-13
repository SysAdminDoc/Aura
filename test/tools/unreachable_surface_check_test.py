from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.unreachable_surface_check import UnreachableSurfaceError, validate


REPO_ROOT = Path(__file__).resolve().parents[2]
MAIN = "app/src/main/java/com/freevibe"

SCREEN = """package com.freevibe.ui.navigation

sealed class Screen(val route: String, val destinationPattern: String = route) {
    data object Home : Screen(route = "home")
    data object Detail : Screen(route = "detail/{id}") {
        fun createRoute(id: String) = "detail/$id"
    }
    data object Help : Screen(route = "help")
}
"""

PREFERENCES = """package com.freevibe.data.local

class PreferencesManager {
    suspend fun setTheme(value: String) = Unit
    suspend fun setFreesoundKey(value: String) = Unit
    suspend fun setRingtoneShuffleIntervalHours(value: Long) = Unit
}
"""

ROOT = """package com.freevibe.ui

val tabs = listOf(Screen.Home)

fun graph(prefs: PreferencesManager) {
    composable(Screen.Home.route) { }
    composable(
        Screen.Detail.destinationPattern,
    ) { }
    navigate(Screen.Detail.createRoute("1"))
    prefs.setTheme("dark")
}
"""


def write_tree(repo: Path, *, root: str = ROOT, preferences: str = PREFERENCES, extra: dict[str, str] | None = None) -> None:
    files = {
        f"{MAIN}/ui/navigation/Screen.kt": SCREEN,
        f"{MAIN}/data/local/PreferencesManager.kt": preferences,
        f"{MAIN}/ui/FreeVibeRoot.kt": root,
    }
    files.update(extra or {})
    for relative_path, text in files.items():
        path = repo / relative_path
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")


class UnreachableSurfaceCheckTest(unittest.TestCase):
    def test_live_repo_has_no_unreachable_surfaces(self) -> None:
        result = validate(REPO_ROOT)

        self.assertEqual("ok", result["status"])
        self.assertGreater(result["setterCount"], 50)

    def test_fixture_with_every_path_wired_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_tree(repo)

            self.assertEqual("ok", validate(repo)["status"])

    def test_registered_destination_with_no_navigation_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_tree(repo, root=ROOT.replace("    prefs.setTheme", "    composable(Screen.Help.route) { }\n    prefs.setTheme"))

            with self.assertRaisesRegex(UnreachableSurfaceError, "no navigation to them: Help"):
                validate(repo)

    def test_registration_alone_does_not_count_as_navigation(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_tree(repo, root=ROOT.replace('    navigate(Screen.Detail.createRoute("1"))\n', ""))

            with self.assertRaisesRegex(UnreachableSurfaceError, "Detail"):
                validate(repo)

    def test_setter_with_no_caller_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_tree(repo, root=ROOT.replace('    prefs.setTheme("dark")\n', ""))

            with self.assertRaisesRegex(UnreachableSurfaceError, r"setTheme \(no production caller\)"):
                validate(repo)

    def test_same_named_function_on_another_receiver_is_not_a_caller(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            delegate = "package com.freevibe.ui\n\nclass Delegate(private val media: Media) {\n    fun setTheme(value: String) = media.setTheme(value)\n}\n"
            write_tree(
                repo,
                root=ROOT.replace('    prefs.setTheme("dark")\n', ""),
                extra={f"{MAIN}/ui/Delegate.kt": delegate},
            )

            with self.assertRaisesRegex(UnreachableSurfaceError, "setTheme"):
                validate(repo)

    def test_setter_only_importers_call_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            importer = "package com.freevibe.service\n\nclass LibraryExporter(private val prefs: PreferencesManager) {\n    suspend fun restore() = prefs.setTheme(\"dark\")\n}\n"
            write_tree(
                repo,
                root=ROOT.replace('    prefs.setTheme("dark")\n', ""),
                extra={f"{MAIN}/service/LibraryExporter.kt": importer},
            )

            with self.assertRaisesRegex(UnreachableSurfaceError, "only importers call it"):
                validate(repo)

    def test_entry_point_receiver_counts_as_caller(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            root = ROOT.replace(
                '    prefs.setTheme("dark")\n',
                '    val preferencesManager = remember { entryPoint.preferencesManager() }\n    preferencesManager.setTheme("dark")\n',
            )
            write_tree(repo, root=root)

            self.assertEqual("ok", validate(repo)["status"])

    def test_waiver_for_a_removed_setter_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmpdir:
            repo = Path(tmpdir)
            write_tree(repo, preferences=PREFERENCES.replace("    suspend fun setFreesoundKey(value: String) = Unit\n", ""))

            with self.assertRaisesRegex(UnreachableSurfaceError, "no longer declares: setFreesoundKey"):
                validate(repo)


if __name__ == "__main__":
    unittest.main()
