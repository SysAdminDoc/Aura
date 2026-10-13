package com.freevibe.data.local

import com.freevibe.data.legal.providerCapability
import com.freevibe.data.model.ContentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesManagerDefaultsTest {

    @Test
    fun `fresh installs keep optional cloud surfaces disabled by default`() {
        assertFalse(PreferencesManager.DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED)
        assertFalse(PreferencesManager.DEFAULT_COMMUNITY_PROVIDER_ENABLED)
    }

    @Test
    fun `fresh installs preserve reddit first provider defaults`() {
        assertTrue(providerCapability(ContentSource.REDDIT).enabledByDefault)
        assertFalse(providerCapability(ContentSource.WALLHAVEN).enabledByDefault)
        assertFalse(providerCapability(ContentSource.BING).enabledByDefault)
        assertFalse(providerCapability(ContentSource.PEXELS).enabledByDefault)
        assertFalse(providerCapability(ContentSource.PIXABAY).enabledByDefault)
    }

    @Test
    fun `subreddit preferences normalize prefixes separators and duplicate names`() {
        val validation = validateRedditSubredditList(
            " r/MinimalWallpaper, mobilewallpapers\nR/minimalwallpaper, WQHD_Wallpaper ",
        )

        assertTrue(validation.isValid)
        assertEquals(
            listOf("MinimalWallpaper", "mobilewallpapers", "WQHD_Wallpaper"),
            validation.subreddits,
        )
        assertEquals("MinimalWallpaper,mobilewallpapers,WQHD_Wallpaper", validation.normalized)
    }

    @Test
    fun `subreddit lists reject unsafe or oversized entries`() {
        val unsafe = validateRedditSubredditList("wallpapers,not-valid!,../private")
        val oversized = validateRedditSubredditList(
            (1..MAX_CONFIGURED_SUBREDDITS + 1).joinToString(",") { "wallpaper$it" },
        )

        assertFalse(unsafe.isValid)
        assertEquals(listOf("not-valid!", "../private"), unsafe.invalidEntries)
        assertFalse(oversized.isValid)
        assertTrue(oversized.exceedsLimit)
        assertEquals(MAX_CONFIGURED_SUBREDDITS, oversized.subreddits.size)
    }
}
