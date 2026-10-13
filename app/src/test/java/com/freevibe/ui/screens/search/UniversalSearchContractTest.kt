package com.freevibe.ui.screens.search

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchContractTest {
    @Test
    fun `universal search keeps history local and provider handoffs labeled`() {
        val screen = File("src/main/java/com/freevibe/ui/screens/search/UniversalSearchScreen.kt").readText()
        val repository = File("src/main/java/com/freevibe/data/repository/SearchHistoryRepository.kt").readText()

        listOf(
            "getRecentUniversalSearches",
            "addUniversalSearch",
            "clearUniversalHistory",
            "search_section_online_providers",
            "networkAvailableFlow",
            "ProviderAvailability",
            "UniversalProviderDisabledReason.OFFLINE",
        ).forEach { expected ->
            assertTrue("Missing universal search contract: $expected", screen.contains(expected) || repository.contains(expected))
        }
    }

    @Test
    fun `root navigation exposes universal search and provider query routes`() {
        val root = File("src/main/java/com/freevibe/ui/FreeVibeRoot.kt").readText()
        val screen = File("src/main/java/com/freevibe/ui/navigation/Screen.kt").readText()

        listOf(
            "Screen.UniversalSearch.destinationPattern",
            "UniversalSearchScreen(",
            "Screen.Wallpapers.createRoute(query = query)",
            "Screen.VideoWallpapers.createRoute(query = query)",
            "Screen.Sounds.createRoute(query = query)",
            "Screen.Downloads.createRoute(download.id)",
            "video_wallpapers?query={query}",
            "global_search?query={query}",
        ).forEach { expected ->
            assertTrue("Missing navigation contract: $expected", root.contains(expected) || screen.contains(expected))
        }
    }
}
