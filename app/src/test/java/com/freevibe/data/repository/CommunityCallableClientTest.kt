package com.freevibe.data.repository

import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityFollowInput
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunitySoundUploadMetadataInput
import com.freevibe.data.model.CommunityUserBlockInput
import com.freevibe.data.model.CommunityWallpaperUploadMetadataInput
import com.freevibe.data.model.ContentSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityCallableClientTest {

    @Test
    fun `submitCommunityReport sends envelope and limited use token request`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "report_test",
                "status" to "accepted",
                "reportId" to "report_1",
                "targetPath" to "/community_reports/report_1",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.submitCommunityReport(
            CommunityReportInput(
                contentId = " SOUND::COMMUNITY::cu_1 ",
                contentType = "sound",
                contentSource = ContentSource.COMMUNITY,
                reason = CommunityReportReason.SPAM,
                note = " spam\nlisting ",
                sourceUrl = "https://example.com/sound",
                license = "CC BY",
                uploaderName = "Creator",
                uploaderUid = "creator_1",
            ),
        )

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("submitCommunityReport", request.functionName)
        assertTrue(request.consumeLimitedUseAppCheckToken)
        assertEquals("report_1", result.targetId())
        assertEquals("accepted", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("report_"))
        assertTrue((request.data["clientSentAt"] as Long) > 0L)
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("SOUND::COMMUNITY::cu_1", payload["contentId"])
        assertEquals("SOUND", payload["contentType"])
        assertEquals("COMMUNITY", payload["contentSource"])
        assertEquals("SPAM", payload["reason"])
        assertEquals("spam listing", payload["note"])
        assertFalse(payload.containsKey("reporterUid"))
        assertFalse(payload.containsKey("reportedAt"))
        assertFalse(payload.containsKey("contentKey"))
        assertFalse(payload.containsKey("status"))
    }

    @Test
    fun `recordCommunityVote sends envelope without limited use token request`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "vote_test",
                "status" to "accepted",
                "targetPath" to "/votes/SOUND::COMMUNITY::cu_1",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.recordCommunityVote(" SOUND::COMMUNITY::cu/1 ")

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("recordCommunityVote", request.functionName)
        assertFalse(request.consumeLimitedUseAppCheckToken)
        assertEquals("SOUND::COMMUNITY::cu_1", result.targetId())
        assertEquals("accepted", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("vote_"))
        assertTrue((request.data["clientSentAt"] as Long) > 0L)
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("SOUND::COMMUNITY::cu_1", payload["contentId"])
    }

    @Test
    fun `setCreatorFollow sends desired state without limited use token request`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "follow_test",
                "status" to "accepted",
                "targetPath" to "/creator_follows/user_1/creator_id_1",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.setCreatorFollow(
            CommunityFollowInput(
                creatorId = " creator/id#1 ",
                label = " Creator\nName ",
                following = true,
            ),
        )

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("setCreatorFollow", request.functionName)
        assertFalse(request.consumeLimitedUseAppCheckToken)
        assertEquals("creator_id_1", result.targetId())
        assertEquals("accepted", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("follow_"))
        assertTrue((request.data["clientSentAt"] as Long) > 0L)
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("creator/id#1", payload["creatorId"])
        assertEquals("Creator Name", payload["label"])
        assertEquals(true, payload["following"])
    }

    @Test
    fun `setCreatorFollow sends unfollow operation when desired state is false`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "unfollow_test",
                "status" to "duplicate",
                "targetPath" to "/creator_follows/user_1/creator_one",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.setCreatorFollow(
            CommunityFollowInput(
                creatorId = "creator/one",
                label = "",
                following = false,
            ),
        )

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("setCreatorFollow", request.functionName)
        assertFalse(request.consumeLimitedUseAppCheckToken)
        assertEquals("creator_one", result.targetId())
        assertEquals("duplicate", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("unfollow_"))
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("creator/one", payload["creatorId"])
        assertEquals(false, payload["following"])
    }

    @Test
    fun `setCommunityUserBlock sends block state without limited use token request`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "block_test",
                "status" to "accepted",
                "targetPath" to "/community_user_blocks/user_1/blocked_one___",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.setCommunityUserBlock(
            CommunityUserBlockInput(
                blockedUid = " blocked/one#[] ",
                reason = CommunityBlockReason.HARASSMENT,
                blocked = true,
            ),
        )

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("setCommunityUserBlock", request.functionName)
        assertFalse(request.consumeLimitedUseAppCheckToken)
        assertEquals("blocked_one___", result.targetId())
        assertEquals("accepted", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("block_"))
        assertTrue((request.data["clientSentAt"] as Long) > 0L)
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("blocked_one___", payload["blockedUid"])
        assertEquals("HARASSMENT", payload["reason"])
        assertEquals(true, payload["blocked"])
        assertFalse(payload.containsKey("uid"))
        assertFalse(payload.containsKey("blockerUid"))
    }

    @Test
    fun `setCommunityUserBlock sends unblock operation without reason`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "unblock_test",
                "status" to "duplicate",
                "targetPath" to "/community_user_blocks/user_1/blocked_one",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.setCommunityUserBlock(
            CommunityUserBlockInput(
                blockedUid = "blocked.one",
                blocked = false,
            ),
        )

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("setCommunityUserBlock", request.functionName)
        assertFalse(request.consumeLimitedUseAppCheckToken)
        assertEquals("blocked_one", result.targetId())
        assertEquals("duplicate", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("unblock_"))
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("blocked_one", payload["blockedUid"])
        assertEquals(false, payload["blocked"])
        assertFalse(payload.containsKey("reason"))
    }

    @Test
    fun `finalizeCommunitySoundUpload sends metadata with limited use token request`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "sound_upload_test",
                "status" to "accepted",
                "uploadId" to "soundA",
                "publicId" to "cu_soundA",
                "targetPath" to "/community_sounds/soundA",
                "ownerIndexPath" to "/owner_uploads/soundOwner1/sounds/soundA",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.finalizeCommunitySoundUpload(
            CommunitySoundUploadMetadataInput(
                name = " Soft\nBell ",
                category = " Notification ",
                tags = listOf(" Calm ", "CALM", "bell!!!", "lo-fi"),
                downloadUrl = "https://firebasestorage.googleapis.com/v0/b/aura/o/sounds%2FsoundOwner1%2Fbell.mp3",
                storagePath = "sounds/soundOwner1/1700000000000_soft_bell.mp3",
                fileType = " AUDIO/MPEG ",
                originalFileName = " Soft Bell.mp3 ",
                uploaderLabel = " Sound Owner ",
                license = "cc-by",
                rightsAttested = true,
                sourceUrl = "https://example.com/source",
            ),
        )

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("finalizeCommunitySoundUpload", request.functionName)
        assertTrue(request.consumeLimitedUseAppCheckToken)
        assertEquals("soundA", result.targetId())
        assertEquals("accepted", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("sound_upload_"))
        assertTrue((request.data["clientSentAt"] as Long) > 0L)
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("Soft Bell", payload["name"])
        assertEquals("notification", payload["category"])
        assertEquals(listOf("calm", "bell", "lo-fi"), payload["tags"])
        assertEquals("audio/mpeg", payload["fileType"])
        assertEquals("Soft Bell.mp3", payload["originalFileName"])
        assertEquals("Sound Owner", payload["uploaderLabel"])
        assertEquals("CC BY", payload["license"])
        assertEquals(true, payload["rightsAttested"])
        assertEquals("https://example.com/source", payload["sourceUrl"])
        assertFalse(payload.containsKey("uploaderId"))
        assertFalse(payload.containsKey("uploaderUid"))
        assertFalse(payload.containsKey("uploadedAt"))
        assertFalse(payload.containsKey("votes"))
    }

    @Test
    fun `finalizeCommunityWallpaperUpload sends metadata with limited use token request`() = runTest {
        val invoker = RecordingCommunityCallableInvoker(
            response = mapOf(
                "operationId" to "wallpaper_upload_test",
                "status" to "accepted",
                "uploadId" to "wallA",
                "publicId" to "cw_wallA",
                "targetPath" to "/community_wallpapers/wallA",
                "ownerIndexPath" to "/owner_uploads/wallOwner1/wallpapers/wallA",
                "serverTimeMillis" to 123L,
            ),
        )
        val client = CommunityCallableClient(invoker)

        val result = client.finalizeCommunityWallpaperUpload(
            CommunityWallpaperUploadMetadataInput(
                name = " Soft\nGradient ",
                category = " AMOLED ",
                tags = listOf(" Dark ", "DARK", "lock-screen!!!", "lo-fi"),
                colors = listOf("#112233", "#112233", "#abcdef"),
                thumbnailUrl = "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fsoft.jpg",
                fullUrl = "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fsoft.jpg",
                downloadUrl = "https://firebasestorage.googleapis.com/v0/b/aura/o/wallpapers%2FwallOwner1%2Fsoft.jpg",
                storagePath = "wallpapers/wallOwner1/1700000000000_soft.jpg",
                width = 1080,
                height = 1920,
                fileSize = 345_678,
                fileType = " IMAGE/JPEG ",
                originalFileName = " Soft Gradient.png ",
                uploaderLabel = " Wall Owner ",
                license = "attribution",
                rightsAttested = true,
                sourceUrl = "https://example.com/wall",
            ),
        )

        val request = requireNotNull(invoker.lastRequest)
        assertEquals("finalizeCommunityWallpaperUpload", request.functionName)
        assertTrue(request.consumeLimitedUseAppCheckToken)
        assertEquals("wallA", result.targetId())
        assertEquals("accepted", result.status)

        assertTrue((request.data["operationId"] as String).startsWith("wallpaper_upload_"))
        assertTrue((request.data["clientSentAt"] as Long) > 0L)
        val payload = request.data["payload"] as Map<*, *>
        assertEquals("Soft Gradient", payload["name"])
        assertEquals("amoled", payload["category"])
        assertEquals(listOf("dark", "lock-screen", "lo-fi"), payload["tags"])
        assertEquals(listOf("#112233", "#ABCDEF"), payload["colors"])
        assertEquals(1080, payload["width"])
        assertEquals(1920, payload["height"])
        assertEquals(345_678, payload["fileSize"])
        assertEquals("image/jpeg", payload["fileType"])
        assertEquals("Soft Gradient.png", payload["originalFileName"])
        assertEquals("Wall Owner", payload["uploaderLabel"])
        assertEquals("CC BY", payload["license"])
        assertEquals(true, payload["rightsAttested"])
        assertEquals("https://example.com/wall", payload["sourceUrl"])
        assertFalse(payload.containsKey("uploaderId"))
        assertFalse(payload.containsKey("uploaderUid"))
        assertFalse(payload.containsKey("uploadedAt"))
        assertFalse(payload.containsKey("rightsAttestedAt"))
        assertFalse(payload.containsKey("votes"))
    }

    @Test
    fun `buildCommunityCallableEnvelope validates operation metadata`() {
        val envelope = buildCommunityCallableEnvelope(
            payload = mapOf("contentId" to "item_1"),
            operationId = " report_1 ",
            clientSentAt = 123L,
        )

        assertEquals("report_1", envelope["operationId"])
        assertEquals(123L, envelope["clientSentAt"])
        assertNotNull(envelope["payload"])
    }
}

private class RecordingCommunityCallableInvoker(
    private val response: Map<String, Any?>,
) : CommunityCallableInvoker {
    var lastRequest: CommunityCallableRequest? = null

    override suspend fun call(request: CommunityCallableRequest): Map<String, Any?> {
        lastRequest = request
        return response
    }
}
