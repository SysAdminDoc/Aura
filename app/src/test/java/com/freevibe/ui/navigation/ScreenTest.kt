package com.freevibe.ui.navigation

import com.freevibe.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTest {

    @Test
    fun `bottomNavItems exposes all expected routes`() {
        assertEquals(
            listOf("wallpapers", "video_wallpapers", "sounds", "favorites", "settings"),
            Screen.bottomNavItems.map { it.route },
        )
    }

    @Test
    fun `sound editor route carries edit confirmation flag`() {
        assertTrue(Screen.SoundEditor.destinationPattern.contains("editConfirmed={editConfirmed}"))
    }

    @Test
    fun `navigation titles are resource backed`() {
        assertEquals(R.string.nav_wallpapers, Screen.Wallpapers.titleRes)
        assertEquals(R.string.nav_videos, Screen.VideoWallpapers.titleRes)
        assertEquals(R.string.nav_sounds, Screen.Sounds.titleRes)
        assertEquals(R.string.nav_favorites, Screen.Favorites.titleRes)
        assertEquals(R.string.nav_settings, Screen.Settings.titleRes)
        assertTrue(Screen.bottomNavItems.all { it.titleRes != 0 })
    }
}
