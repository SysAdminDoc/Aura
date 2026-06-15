package com.freevibe.data.model

import com.freevibe.ui.screens.videowallpapers.VideoWallpaperItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoWallpaperLicensePolicyTest {

    @Test
    fun `pexels videos allow apply and share but require download confirmation`() {
        val capabilities = videoItem(
            contentSource = ContentSource.PEXELS,
            license = "Pexels License",
            sourcePageUrl = "https://pexels.com/video/1/",
        ).videoWallpaperLicenseCapabilities()

        assertEquals("Pexels License", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(VideoWallpaperAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(VideoWallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(VideoWallpaperAction.SHARE))
    }

    @Test
    fun `pixabay videos allow apply and share but require download confirmation`() {
        val capabilities = videoItem(
            contentSource = ContentSource.PIXABAY,
            license = "Pixabay License",
            sourcePageUrl = "https://pixabay.com/videos/id-1/",
        ).videoWallpaperLicenseCapabilities()

        assertEquals("Pixabay License", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(VideoWallpaperAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(VideoWallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(VideoWallpaperAction.SHARE))
    }

    @Test
    fun `youtube videos require confirmation for apply and download and disable share`() {
        val capabilities = videoItem(
            contentSource = ContentSource.YOUTUBE,
            license = "",
            sourcePageUrl = "https://www.youtube.com/watch?v=abc",
        ).videoWallpaperLicenseCapabilities()

        assertEquals("YouTube", capabilities.normalizedLicense)
        assertTrue(capabilities.requiresConfirmation(VideoWallpaperAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(VideoWallpaperAction.DOWNLOAD))
        assertFalse(capabilities.canUse(VideoWallpaperAction.SHARE))
    }

    @Test
    fun `reddit videos require confirmation for apply and download`() {
        val capabilities = videoItem(
            contentSource = ContentSource.REDDIT,
            license = "",
            sourcePageUrl = "https://www.reddit.com/r/wallpapers/",
        ).videoWallpaperLicenseCapabilities()

        assertEquals("Reddit", capabilities.normalizedLicense)
        assertTrue(capabilities.requiresConfirmation(VideoWallpaperAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(VideoWallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(VideoWallpaperAction.SHARE))
    }

    @Test
    fun `local videos allow all actions`() {
        val capabilities = videoItem(
            contentSource = ContentSource.LOCAL,
            license = "",
        ).videoWallpaperLicenseCapabilities()

        assertEquals("Local User Content", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(VideoWallpaperAction.APPLY))
        assertTrue(capabilities.canUse(VideoWallpaperAction.DOWNLOAD))
        assertTrue(capabilities.canUse(VideoWallpaperAction.SHARE))
    }

    @Test
    fun `missing source link disables share for remote sources`() {
        val capabilities = videoItem(
            contentSource = ContentSource.PEXELS,
            license = "Pexels License",
            sourcePageUrl = "",
        ).videoWallpaperLicenseCapabilities()

        assertTrue(capabilities.canUse(VideoWallpaperAction.APPLY))
        assertFalse(capabilities.canUse(VideoWallpaperAction.SHARE))
    }

    @Test
    fun `missing uploader disables share for remote sources`() {
        val capabilities = videoItem(
            contentSource = ContentSource.PIXABAY,
            license = "Pixabay License",
            sourcePageUrl = "https://pixabay.com/videos/id-1/",
            uploaderName = "",
        ).videoWallpaperLicenseCapabilities()

        assertTrue(capabilities.canUse(VideoWallpaperAction.APPLY))
        assertFalse(capabilities.canUse(VideoWallpaperAction.SHARE))
    }

    @Test
    fun `normalize video license returns correct values for each source`() {
        assertEquals("YouTube", normalizeVideoWallpaperLicense(ContentSource.YOUTUBE, ""))
        assertEquals("YouTube", normalizeVideoWallpaperLicense(ContentSource.YOUTUBE, "anything"))
        assertEquals("Reddit", normalizeVideoWallpaperLicense(ContentSource.REDDIT, ""))
        assertEquals("Local User Content", normalizeVideoWallpaperLicense(ContentSource.LOCAL, ""))
        assertEquals("Pexels License", normalizeVideoWallpaperLicense(ContentSource.PEXELS, "Pexels License"))
        assertEquals("Pixabay License", normalizeVideoWallpaperLicense(ContentSource.PIXABAY, "Pixabay License"))
        assertEquals("Unknown", normalizeVideoWallpaperLicense(ContentSource.PEXELS, ""))
    }

    @Test
    fun `attribution and source link are required for remote video sources`() {
        val capabilities = videoItem(
            contentSource = ContentSource.PEXELS,
            license = "Pexels License",
            sourcePageUrl = "https://pexels.com/video/1/",
        ).videoWallpaperLicenseCapabilities()

        assertTrue(capabilities.attributionRequired)
        assertTrue(capabilities.sourceLinkRequired)
        assertTrue(capabilities.uploaderRequired)
    }

    @Test
    fun `local videos do not require attribution`() {
        val capabilities = videoItem(
            contentSource = ContentSource.LOCAL,
            license = "",
        ).videoWallpaperLicenseCapabilities()

        assertFalse(capabilities.attributionRequired)
        assertFalse(capabilities.sourceLinkRequired)
        assertFalse(capabilities.uploaderRequired)
    }

    private fun videoItem(
        contentSource: ContentSource,
        license: String,
        sourcePageUrl: String = "",
        uploaderName: String = "Creator",
    ) = VideoWallpaperItem(
        id = "test_1",
        title = "Test Video",
        thumbnailUrl = "https://example.com/thumb.jpg",
        source = contentSource.name,
        uploaderName = uploaderName,
        contentSource = contentSource,
        license = license,
        sourcePageUrl = sourcePageUrl,
    )
}
