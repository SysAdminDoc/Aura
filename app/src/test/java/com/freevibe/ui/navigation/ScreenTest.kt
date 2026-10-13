package com.freevibe.ui.navigation

import com.freevibe.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    @Test
    fun `bottomNavItems exposes all expected routes`() {
        assertEquals(
            listOf("wallpapers", "video_wallpapers", "sounds", "library", "settings"),
            Screen.bottomNavItems.map { it.route },
        )
    }

    @Test
    fun `sound editor route carries edit confirmation flag`() {
        assertTrue(Screen.SoundEditor.destinationPattern.contains("editConfirmed={editConfirmed}"))
    }

    @Test
    fun `downloads route takes an optional row to focus`() {
        assertTrue(Screen.Downloads.destinationPattern.contains("focus={focus}"))
        assertEquals("downloads", Screen.Downloads.createRoute())
        assertEquals("downloads", Screen.Downloads.createRoute(" "))
    }

    @Test
    fun `searchable media routes expose query parameters`() {
        assertTrue(Screen.Wallpapers.destinationPattern.contains("query={query}"))
        assertTrue(Screen.VideoWallpapers.destinationPattern.contains("query={query}"))
        assertTrue(Screen.Sounds.destinationPattern.contains("query={query}"))
        assertTrue(Screen.UniversalSearch.destinationPattern.contains("query={query}"))
    }

    @Test
    fun `settings route carries section anchors`() {
        assertTrue(Screen.Settings.destinationPattern.contains("section={section}"))
        assertEquals("settings?section=backup", Screen.Settings.createRoute(Screen.Settings.BACKUP_SECTION))
        assertEquals("settings", Screen.Settings.createRoute())
        assertEquals("settings", Screen.Settings.createRoute("unknown"))
    }

    @Test
    fun `navigation titles are resource backed`() {
        assertEquals(R.string.nav_wallpapers, Screen.Wallpapers.titleRes)
        assertEquals(R.string.nav_videos, Screen.VideoWallpapers.titleRes)
        assertEquals(R.string.nav_sounds, Screen.Sounds.titleRes)
        assertEquals(R.string.nav_library, Screen.Library.titleRes)
        assertEquals(R.string.nav_global_search, Screen.UniversalSearch.titleRes)
        assertEquals(R.string.nav_favorites, Screen.Favorites.titleRes)
        assertEquals(R.string.nav_settings, Screen.Settings.titleRes)
        assertTrue(Screen.bottomNavItems.all { it.titleRes != 0 })
    }
}
