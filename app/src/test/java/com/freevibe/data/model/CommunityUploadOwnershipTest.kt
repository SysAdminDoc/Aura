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
            storagePath = "wallpapers/uid_1/123_wallpaper.jpg",
            deletedByUid = "uid/1",
            deletedAt = 456L,
            reason = CommunityUploadDeleteReason.OWNER_DELETE,
        )

        assertEquals(
            mapOf(
                "/community_wallpapers/wallpaper_1" to null,
                "/owner_uploads/uid_1/wallpapers/wallpaper_1" to null,
                "/community_upload_deletions/cw_wallpaper_1" to mapOf(
                    "publicId" to "cw_wallpaper_1",
                    "uploadId" to "wallpaper_1",
                    "contentType" to "WALLPAPER",
                    "metadataPath" to "/community_wallpapers/wallpaper_1",
                    "storagePath" to "wallpapers/uid_1/123_wallpaper.jpg",
                    "uploaderUid" to "uid_1",
                    "deletedByUid" to "uid_1",
                    "deletedAt" to 456L,
                    "reason" to "OWNER_DELETE",
                ),
            ),
            updates,
        )
    }

    @Test
    fun `buildCommunityUploadDeleteUpdates rejects wrong tombstone storage root or owner`() {
        try {
            buildCommunityUploadDeleteUpdates(
                kind = CommunityUploadKind.SOUND,
                ownerUid = "uid1",
                uploadId = "sound1",
                storagePath = "wallpapers/uid1/wall.jpg",
                deletedByUid = "uid1",
                deletedAt = 456L,
                reason = CommunityUploadDeleteReason.OWNER_DELETE,
            )
            fail("Expected wrong storage root to throw")
        } catch (_: IllegalArgumentException) {
        }

        try {
            buildCommunityUploadDeleteUpdates(
                kind = CommunityUploadKind.SOUND,
                ownerUid = "uid1",
                uploadId = "sound1",
                storagePath = "sounds/other_uid/sound.mp3",
                deletedByUid = "uid1",
                deletedAt = 456L,
                reason = CommunityUploadDeleteReason.OWNER_DELETE,
            )
            fail("Expected wrong storage owner to throw")
        } catch (_: IllegalArgumentException) {
        }
    }
}
