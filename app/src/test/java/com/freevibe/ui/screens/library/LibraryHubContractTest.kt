package com.freevibe.ui.screens.library

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryHubContractTest {
    @Test
    fun `library screen links all local library surfaces`() {
        val source = File("src/main/java/com/freevibe/ui/screens/library/LibraryScreen.kt").readText()

        listOf(
            "onSearchClick",
            "onFavoritesClick",
            "onDownloadsClick",
            "onCollectionsClick",
            "onLocalImportsClick",
            "onRecentActivityClick",
            "onBackupRestoreClick",
            "library_search_title",
            "library_favorites_title",
            "library_downloads_title",
            "library_collections_title",
            "library_imports_title",
            "library_recent_title",
            "library_backup_title",
        ).forEach { expected ->
            assertTrue("Missing Library hub contract: $expected", source.contains(expected))
        }
    }

    @Test
    fun `library route owns existing local child destinations`() {
        val root = File("src/main/java/com/freevibe/ui/FreeVibeRoot.kt").readText()
        val screen = File("src/main/java/com/freevibe/ui/navigation/Screen.kt").readText()

        assertTrue(screen.contains("data object Library"))
        assertTrue(screen.contains("data object UniversalSearch"))
        assertTrue(screen.contains("listOf(Wallpapers, VideoWallpapers, Sounds, Library, Settings)"))
        assertTrue(root.contains("composable(Screen.Library.route)"))
        assertTrue(root.contains("Screen.UniversalSearch.route"))
        assertTrue(root.contains("Screen.Favorites.route"))
        assertTrue(root.contains("Screen.Downloads.route"))
        assertTrue("Downloads keeps the Library tab selected", root.contains("Screen.Downloads.destinationPattern,"))
        assertTrue(root.contains("Screen.Collections.route"))
        assertTrue(root.contains("Screen.WallpaperHistory.route"))
        assertTrue(root.contains("screen == Screen.Library && favoritesCount > 0"))
    }

    @Test
    fun `backup row anchors the settings backup section`() {
        val root = File("src/main/java/com/freevibe/ui/FreeVibeRoot.kt").readText()
        val settings = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()
        val anchor = File("src/main/java/com/freevibe/ui/screens/settings/SettingsSectionNavigation.kt").readText()

        assertTrue(root.contains("Screen.Settings.createRoute(Screen.Settings.BACKUP_SECTION)"))
        assertTrue(root.contains("route = Screen.Settings.destinationPattern"))
        assertTrue(root.contains("initialSection = backStackEntry.arguments?.getString(\"section\")"))
        assertTrue(settings.contains("SettingsSectionAnchorTarget(Screen.Settings.BACKUP_SECTION, initialSection)"))
        assertTrue(anchor.contains("BringIntoViewRequester"))
        assertTrue(anchor.contains("requester.bringIntoView()"))
    }

    @Test
    fun `library strings stay local first`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val libraryBlock = Regex("""(?s)<!-- Library screen -->(.*?)<!-- Favorites screen -->""")
            .find(strings)
            ?.groupValues
            ?.get(1)
            .orEmpty()

        assertTrue(libraryBlock.contains("library_title"))
        listOf("account", "follower", "credit", "remote profile", "remote-profile").forEach { forbidden ->
            assertFalse("Library copy must avoid '$forbidden'", libraryBlock.contains(forbidden, ignoreCase = true))
        }
    }
}
