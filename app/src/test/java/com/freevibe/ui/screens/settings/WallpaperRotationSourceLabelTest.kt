package com.freevibe.ui.screens.settings

import com.freevibe.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperRotationSourceLabelTest {
    @Test
    fun `every picker source uses the picker's own translated label`() {
        assertEquals(R.string.settings_dialogs_source_discover, wallpaperRotationSourceNameRes("discover"))
        assertEquals(R.string.settings_dialogs_source_favorites, wallpaperRotationSourceNameRes("favorites"))
        assertEquals(R.string.settings_dialogs_source_wallhaven, wallpaperRotationSourceNameRes("wallhaven"))
        assertEquals(R.string.settings_dialogs_source_pixabay, wallpaperRotationSourceNameRes("pixabay"))
        assertEquals(R.string.settings_dialogs_source_bing, wallpaperRotationSourceNameRes("bing"))
    }

    @Test
    fun `retired stored keys read as the Discover source rotation really uses`() {
        assertEquals(R.string.settings_dialogs_source_discover, wallpaperRotationSourceNameRes("reddit"))
        assertEquals(R.string.settings_dialogs_source_discover, wallpaperRotationSourceNameRes("unsplash"))
        assertEquals(R.string.settings_dialogs_source_discover, wallpaperRotationSourceNameRes(""))
    }

    @Test
    fun `an unknown key falls through to the source name lookup`() {
        assertNull(wallpaperRotationSourceNameRes("some_new_provider"))
    }
}
