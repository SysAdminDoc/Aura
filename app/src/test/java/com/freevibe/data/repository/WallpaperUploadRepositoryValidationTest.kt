package com.freevibe.data.repository

import com.freevibe.service.ColorExtractor
import com.freevibe.R
import com.freevibe.service.MediaIngestionImageFlow
import com.freevibe.service.imageFormatSupportForFlow
import com.freevibe.util.uiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WallpaperUploadRepositoryValidationTest {

    @Test
    fun `normalizeWallpaperUploadCategory trims and lowercases supported categories`() {
        assertEquals("amoled", normalizeWallpaperUploadCategory(" AMOLED "))
        assertEquals("nature", normalizeWallpaperUploadCategory("nature"))
    }

    @Test
    fun `normalizeWallpaperUploadCategory rejects unsupported values`() {
        try {
            normalizeWallpaperUploadCategory("ringtones")
            fail("Expected invalid category to throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `sanitizeWallpaperUploadTags trims dedupes and caps tags`() {
        val tags = sanitizeWallpaperUploadTags(
            listOf(
                " Dark ",
                "DARK",
                "lock-screen",
                "wallpaper!!!",
                "a",
                "night drive",
                "focus_mode",
                "minimal",
                "calm",
                "extra-tag",
            )
        )

        assertEquals(
            listOf("dark", "lock-screen", "wallpaper", "night drive", "focus_mode", "minimal", "calm", "extra-tag"),
            tags,
        )
    }

    @Test
    fun `isSupportedWallpaperUploadMime only allows approved image formats`() {
        assertTrue(isSupportedWallpaperUploadMime("image/jpeg", sdkInt = 26))
        assertTrue(isSupportedWallpaperUploadMime("image/webp", sdkInt = 26))
        assertTrue(isSupportedWallpaperUploadMime("image/heif", sdkInt = 26))
        assertTrue(isSupportedWallpaperUploadMime("image/heic", sdkInt = 26))
        assertTrue(isSupportedWallpaperUploadMime("image/avif", sdkInt = 34))
        assertFalse(isSupportedWallpaperUploadMime("image/avif", sdkInt = 33))
        assertFalse(isSupportedWallpaperUploadMime("image/gif", sdkInt = 34))
        assertFalse(isSupportedWallpaperUploadMime("", sdkInt = 34))
        assertEquals(
            uiText(R.string.media_image_avif_needs_android14_scrub),
            unsupportedWallpaperUploadFormatMessage("image/avif", sdkInt = 33),
        )
    }

    @Test
    fun `centerCropBounds crops landscape to phone portrait ratio`() {
        assertRect(left = 420, top = 0, right = 1500, bottom = 1920, centerCropBounds(1920, 1920, 9f / 16f))
        assertRect(left = 0, top = 0, right = 1080, bottom = 1920, centerCropBounds(1080, 1920, 9f / 16f))
    }

    @Test
    fun `paletteColorsToHex dedupes nonzero colors`() {
        val colors = paletteColorsToHex(
            ColorExtractor.WallpaperPalette(
                dominantColor = 0xFF112233.toInt(),
                vibrantColor = 0xFF445566.toInt(),
                mutedColor = 0xFF112233.toInt(),
                bestAccentColor = 0xFF778899.toInt(),
            )
        )

        assertEquals(listOf("#778899", "#112233", "#445566"), colors)
    }

    @Test
    fun `community wallpaper upload policy transcodes for metadata scrub`() {
        val support = imageFormatSupportForFlow(
            MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD,
            "image/heif",
            sdkInt = 26,
        )

        assertTrue(support.supported)
        assertTrue(support.stripsMetadata)
        assertEquals("image/jpeg", support.outputMimeType)
        assertEquals(uiText(R.string.media_image_transcode_notice), support.message)
    }

    @Test
    fun `shouldDisplayCommunityWallpaper hides negative vote scores`() {
        assertTrue(shouldDisplayCommunityWallpaper(0))
        assertTrue(shouldDisplayCommunityWallpaper(8))
        assertFalse(shouldDisplayCommunityWallpaper(-1))
    }

    private fun assertRect(left: Int, top: Int, right: Int, bottom: Int, actual: WallpaperCropBounds) {
        assertEquals(left, actual.left)
        assertEquals(top, actual.top)
        assertEquals(right, actual.right)
        assertEquals(bottom, actual.bottom)
    }
}
