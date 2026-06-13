package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperLicensePolicyTest {

    @Test
    fun `pexels wallpapers allow all actions with attribution`() {
        val capabilities = wallpaper(
            source = ContentSource.PEXELS,
            license = "Pexels License",
            sourcePageUrl = "https://pexels.com/photo/1/",
        ).wallpaperLicenseCapabilities()

        assertEquals("Pexels License", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(WallpaperAction.SHARE))
        assertTrue(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `pixabay wallpapers allow all actions with attribution`() {
        val capabilities = wallpaper(
            source = ContentSource.PIXABAY,
            license = "Pixabay License",
            sourcePageUrl = "https://pixabay.com/photos/1/",
        ).wallpaperLicenseCapabilities()

        assertEquals("Pixabay License", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(WallpaperAction.SHARE))
        assertTrue(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `bing daily images require confirmation for download and edit and disable share`() {
        val capabilities = wallpaper(
            source = ContentSource.BING,
            license = "",
            sourcePageUrl = "https://www.bing.com/",
        ).wallpaperLicenseCapabilities()

        assertEquals("Bing Daily", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertEquals(WallpaperActionDecision.CONFIRMATION_REQUIRED, capabilities.capability(WallpaperAction.DOWNLOAD).decision)
        assertEquals(WallpaperActionDecision.CONFIRMATION_REQUIRED, capabilities.capability(WallpaperAction.EDIT).decision)
        assertFalse(capabilities.canUse(WallpaperAction.SHARE))
    }

    @Test
    fun `reddit wallpapers require confirmation and disable edit`() {
        val capabilities = wallpaper(
            source = ContentSource.REDDIT,
            license = "",
            sourcePageUrl = "https://reddit.com/r/wallpapers/comments/abc/",
        ).wallpaperLicenseCapabilities()

        assertEquals("Reddit", capabilities.normalizedLicense)
        assertEquals(WallpaperActionDecision.CONFIRMATION_REQUIRED, capabilities.capability(WallpaperAction.APPLY).decision)
        assertEquals(WallpaperActionDecision.CONFIRMATION_REQUIRED, capabilities.capability(WallpaperAction.DOWNLOAD).decision)
        assertFalse(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `community wallpapers with user upload license require confirmation`() {
        val capabilities = wallpaper(
            source = ContentSource.COMMUNITY,
            license = "User Upload",
        ).wallpaperLicenseCapabilities()

        assertEquals("User Upload", capabilities.normalizedLicense)
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.EDIT))
    }

    @Test
    fun `community wallpapers with selected CC0 license allow all actions`() {
        val capabilities = wallpaper(
            source = ContentSource.COMMUNITY,
            license = "CC0",
        ).wallpaperLicenseCapabilities()

        assertEquals("CC0", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(WallpaperAction.SHARE))
        assertTrue(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `local wallpapers allow all actions`() {
        val capabilities = wallpaper(
            source = ContentSource.LOCAL,
            license = "",
        ).wallpaperLicenseCapabilities()

        assertEquals("Local User Content", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(WallpaperAction.SHARE))
        assertTrue(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `ai generated wallpapers require confirmation to share`() {
        val capabilities = wallpaper(
            source = ContentSource.AI_GENERATED,
            license = "",
        ).wallpaperLicenseCapabilities()

        assertEquals("AI Generated", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.SHARE))
        assertTrue(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `unavailable source disables all wallpaper actions`() {
        val capabilities = wallpaper(
            source = ContentSource.WALLHAVEN,
            license = "CC BY 4.0",
            sourcePageUrl = "https://wallhaven.cc/w/abc123",
            sourceAvailability = SOURCE_AVAILABILITY_UNAVAILABLE,
        ).wallpaperLicenseCapabilities()

        assertFalse(capabilities.canUse(WallpaperAction.APPLY))
        assertFalse(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertFalse(capabilities.canUse(WallpaperAction.SHARE))
        assertFalse(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `noncommercial wallpapers require confirmation for apply, download, and edit`() {
        val capabilities = wallpaper(
            source = ContentSource.WIKIMEDIA,
            license = "CC BY-NC 4.0",
            sourcePageUrl = "https://commons.wikimedia.org/wiki/File:Example.jpg",
        ).wallpaperLicenseCapabilities()

        assertEquals("CC BY-NC", capabilities.normalizedLicense)
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.EDIT))
    }

    @Test
    fun `no derivatives wallpaper disables edit`() {
        val capabilities = wallpaper(
            source = ContentSource.WIKIMEDIA,
            license = "CC BY-ND 4.0",
            sourcePageUrl = "https://commons.wikimedia.org/wiki/File:Example.jpg",
        ).wallpaperLicenseCapabilities()

        assertEquals("CC BY-ND", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertFalse(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `missing source link disables share`() {
        val capabilities = wallpaper(
            source = ContentSource.WALLHAVEN,
            license = "CC BY 4.0",
            sourcePageUrl = "",
        ).wallpaperLicenseCapabilities()

        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertFalse(capabilities.canUse(WallpaperAction.SHARE))
        assertTrue(capabilities.canUse(WallpaperAction.EDIT))
    }

    @Test
    fun `remote source with unknown license requires confirmation`() {
        val capabilities = wallpaper(
            source = ContentSource.WALLHAVEN,
            license = "",
            sourcePageUrl = "https://wallhaven.cc/w/abc123",
        ).wallpaperLicenseCapabilities()

        assertEquals("Unknown", capabilities.normalizedLicense)
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.SHARE))
        assertTrue(capabilities.requiresConfirmation(WallpaperAction.EDIT))
    }

    @Test
    fun `wallhaven cc by wallpapers allow all actions with attribution`() {
        val capabilities = wallpaper(
            source = ContentSource.WALLHAVEN,
            license = "CC BY 4.0",
            sourcePageUrl = "https://wallhaven.cc/w/abc123",
        ).wallpaperLicenseCapabilities()

        assertEquals("CC BY", capabilities.normalizedLicense)
        assertTrue(capabilities.attributionRequired)
        assertTrue(capabilities.sourceLinkRequired)
        assertTrue(capabilities.canUse(WallpaperAction.APPLY))
        assertTrue(capabilities.canUse(WallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(WallpaperAction.SHARE))
        assertTrue(capabilities.canUse(WallpaperAction.EDIT))
    }

    private fun wallpaper(
        source: ContentSource,
        license: String,
        sourcePageUrl: String = "",
        sourceAvailability: String = SOURCE_AVAILABILITY_AVAILABLE,
    ) = Wallpaper(
        id = "wallpaper_1",
        source = source,
        thumbnailUrl = "https://example.com/thumb.jpg",
        fullUrl = "https://example.com/full.jpg",
        width = 1920,
        height = 1080,
        license = license,
        uploaderName = "Creator",
        sourcePageUrl = sourcePageUrl,
        sourceAvailability = sourceAvailability,
    )
}
