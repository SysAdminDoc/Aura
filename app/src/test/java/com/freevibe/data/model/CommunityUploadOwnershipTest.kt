package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommunityUploadOwnershipTest {

    @Test
    fun `buildCommunityOwnerUploadIndexPayload stores deletion handles`() {
        val payload = buildCommunityOwnerUploadIndexPayload(
            kind = CommunityUploadKind.SOUND,
            uploadId = "cu_sound.1",
            storagePath = "sounds/uid_1/123_sound.mp3",
            title = "  My\nSound  ",
            createdAt = 123L,
        )

        assertEquals("sound_1", payload["uploadId"])
        assertEquals("cu_sound_1", payload["publicId"])
        assertEquals("SOUND", payload["contentType"])
        assertEquals("/community_sounds/sound_1", payload["metadataPath"])
        assertEquals("sounds/uid_1/123_sound.mp3", payload["storagePath"])
        assertEquals("My Sound", payload["title"])
    }

    @Test
    fun `buildCommunityOwnerUploadIndexPayload rejects wrong storage root`() {
        try {
            buildCommunityOwnerUploadIndexPayload(
                kind = CommunityUploadKind.WALLPAPER,
                uploadId = "wallpaper-1",
                storagePath = "sounds/uid_1/123_sound.mp3",
                title = "Wallpaper",
                createdAt = 123L,
            )
            fail("Expected wrong storage root to throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `buildCommunityUploadDeleteUpdates removes metadata and owner index`() {
        val updates = buildCommunityUploadDeleteUpdates(
            kind = CommunityUploadKind.WALLPAPER,
            ownerUid = "uid/1",
            uploadId = "cw_wallpaper.1",
        )

        assertEquals(
            mapOf(
                "/community_wallpapers/wallpaper_1" to null,
                "/owner_uploads/uid_1/wallpapers/wallpaper_1" to null,
            ),
            updates,
        )
    }
}
