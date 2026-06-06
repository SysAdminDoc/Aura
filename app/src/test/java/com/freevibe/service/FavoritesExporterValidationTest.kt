package com.freevibe.service

import com.freevibe.data.model.SOURCE_AVAILABILITY_AVAILABLE
import com.freevibe.data.model.SOURCE_AVAILABILITY_UNAVAILABLE
import com.freevibe.data.model.isSourceUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesExporterValidationTest {

    @Test
    fun `imported favorites only allow https urls`() {
        // v6.5.0 hardened the import path to HTTPS-only (defense-in-depth against
        // exported payloads that embed cleartext or local-file URLs).
        assertTrue(isAllowedImportedFavoriteUrl("https://example.com/wallpaper.jpg"))
        assertFalse(isAllowedImportedFavoriteUrl("http://example.com/sound.mp3"))
        assertFalse(isAllowedImportedFavoriteUrl("file:///sdcard/payload.jpg"))
        assertFalse(isAllowedImportedFavoriteUrl("content://media/external/images/1"))
        assertFalse(isAllowedImportedFavoriteUrl("javascript:alert(1)"))
    }

    @Test
    fun `validated favorite entity rejects unsafe full urls`() {
        val entity = FavoriteExportItem(
            id = "sound_1",
            source = "YOUTUBE",
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "file:///sdcard/ringtone.mp3",
            name = "Bad import",
        ).toValidatedEntity()

        assertFalse(entity != null)
    }

    @Test
    fun `validated favorite entity keeps supported remote urls`() {
        val entity = FavoriteExportItem(
            id = "wallpaper_1",
            source = "WALLHAVEN",
            type = "WALLPAPER",
            thumbnailUrl = "https://example.com/thumb.jpg",
            fullUrl = "https://example.com/full.jpg",
            sourcePageUrl = "https://example.com/post",
        ).toValidatedEntity()

        assertNotNull(entity)
        assertEquals(SOURCE_AVAILABILITY_AVAILABLE, entity?.sourceAvailability)
    }

    @Test
    fun `validated favorite entity preserves source unavailable metadata`() {
        val entity = FavoriteExportItem(
            id = "rd_missing",
            source = "reddit",
            type = "wallpaper",
            thumbnailUrl = "https://example.com/thumb.jpg",
            fullUrl = "https://example.com/full.jpg",
            sourceAvailability = SOURCE_AVAILABILITY_UNAVAILABLE,
            sourceAvailabilityReason = "Post was removed",
        ).toValidatedEntity()

        assertNotNull(entity)
        assertTrue(entity?.isSourceUnavailable() == true)
        assertEquals("Post was removed", entity?.sourceAvailabilityReason)
    }
}
