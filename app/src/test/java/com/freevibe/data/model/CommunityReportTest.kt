package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommunityReportTest {

    @Test
    fun `community content reports expose provider policy reason`() {
        assertEquals(
            listOf(
                CommunityReportReason.RIGHTS,
                CommunityReportReason.SOURCE_REMOVED,
                CommunityReportReason.SAFETY,
                CommunityReportReason.SPAM,
                CommunityReportReason.PROVIDER_POLICY,
                CommunityReportReason.OTHER,
            ),
            COMMUNITY_REPORT_REASONS,
        )
    }

    @Test
    fun `generated content reports expose required policy categories`() {
        assertEquals(
            listOf(
                CommunityReportReason.OFFENSIVE,
                CommunityReportReason.UNSAFE,
                CommunityReportReason.DECEPTIVE,
                CommunityReportReason.OTHER,
            ),
            GENERATED_CONTENT_REPORT_REASONS,
        )
    }

    @Test
    fun `buildCommunityReportPayload normalizes report metadata`() {
        val payload = buildCommunityReportPayload(
            input = CommunityReportInput(
                contentId = "WALLPAPER::COMMUNITY::cw_123",
                contentType = "wallpaper",
                contentSource = ContentSource.COMMUNITY,
                reason = CommunityReportReason.RIGHTS,
                note = "  license\nlooks wrong  ",
                sourceUrl = "https://example.com/source",
                license = "CC BY",
                uploaderName = "Creator",
                uploaderUid = "creator-1",
            ),
            reporterUid = "uid-1",
            reportedAt = 123L,
        )

        assertEquals("WALLPAPER", payload["contentType"])
        assertEquals("RIGHTS", payload["reason"])
        assertEquals("license looks wrong", payload["note"])
        assertEquals("https://example.com/source", payload["sourceUrl"])
        assertEquals("OPEN", payload["status"])
        assertEquals("uid-1", payload["reporterUid"])
        assertEquals("creator-1", payload["uploaderUid"])
    }

    @Test
    fun `buildCommunityReportCallablePayload omits server owned fields`() {
        val payload = buildCommunityReportCallablePayload(
            CommunityReportInput(
                contentId = " WALLPAPER::COMMUNITY::cw_123 ",
                contentType = "wallpaper",
                contentSource = ContentSource.COMMUNITY,
                reason = CommunityReportReason.RIGHTS,
                note = "  license\nlooks wrong  ",
                sourceUrl = "https://example.com/source",
                license = "CC BY",
                uploaderName = "Creator",
                uploaderUid = "creator-1",
            ),
        )

        assertEquals("WALLPAPER::COMMUNITY::cw_123", payload["contentId"])
        assertEquals("WALLPAPER", payload["contentType"])
        assertEquals("COMMUNITY", payload["contentSource"])
        assertEquals("RIGHTS", payload["reason"])
        assertEquals("license looks wrong", payload["note"])
        assertEquals("creator-1", payload["uploaderUid"])
        assertEquals(false, payload.containsKey("reporterUid"))
        assertEquals(false, payload.containsKey("reportedAt"))
        assertEquals(false, payload.containsKey("status"))
        assertEquals(false, payload.containsKey("contentKey"))
    }

    @Test
    fun `buildCommunityReportPayload rejects blank ids and non https sources`() {
        try {
            buildCommunityReportPayload(
                input = CommunityReportInput(
                    contentId = "",
                    contentType = "SOUND",
                    contentSource = ContentSource.COMMUNITY,
                    reason = CommunityReportReason.SAFETY,
                ),
                reporterUid = "uid-1",
                reportedAt = 123L,
            )
            fail("Expected blank content ID to throw")
        } catch (_: IllegalArgumentException) {
        }

        try {
            buildCommunityReportPayload(
                input = CommunityReportInput(
                    contentId = "SOUND::COMMUNITY::cu_1",
                    contentType = "SOUND",
                    contentSource = ContentSource.COMMUNITY,
                    reason = CommunityReportReason.SOURCE_REMOVED,
                    sourceUrl = "http://example.com/source",
                ),
                reporterUid = "uid-1",
                reportedAt = 123L,
            )
            fail("Expected non-HTTPS source URL to throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `buildCommunityReportResolutionPayload rejects open status`() {
        val payload = buildCommunityReportResolutionPayload(
            reportId = "report-1",
            status = CommunityReportResolutionStatus.HIDDEN,
            resolverUid = "admin-1",
            resolvedAt = 456L,
            note = "Confirmed rights issue",
        )

        assertEquals("report-1", payload["reportId"])
        assertEquals("HIDDEN", payload["status"])
        assertEquals("admin-1", payload["resolverUid"])

        try {
            buildCommunityReportResolutionPayload(
                reportId = "report-1",
                status = CommunityReportResolutionStatus.OPEN,
                resolverUid = "admin-1",
                resolvedAt = 456L,
            )
            fail("Expected open resolution status to throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `buildCommunityTakedownReceiptPayload requires rights community upload handles`() {
        val payload = buildCommunityTakedownReceiptPayload(
            reportId = "report-1",
            contentId = "SOUND::COMMUNITY::cu_sound_1",
            contentType = "sound",
            contentSource = "community",
            reason = CommunityReportReason.RIGHTS,
            action = CommunityTakedownAction.HIDE,
            status = CommunityReportResolutionStatus.HIDDEN,
            uploadId = "cu_sound_1",
            metadataPath = "/community_sounds/sound_1",
            storagePath = "sounds/uploader-1/sound.mp3",
            uploaderUid = "uploader-1",
            resolverUid = "admin-1",
            resolvedAt = 456L,
            note = " Confirmed\nrights issue ",
        )

        assertEquals("report-1", payload["reportId"])
        assertEquals("SOUND::COMMUNITY::cu_sound_1", payload["contentId"])
        assertEquals("SOUND", payload["contentType"])
        assertEquals("COMMUNITY", payload["contentSource"])
        assertEquals("RIGHTS", payload["reason"])
        assertEquals("HIDE", payload["action"])
        assertEquals("HIDDEN", payload["status"])
        assertEquals("sound_1", payload["uploadId"])
        assertEquals("/community_sounds/sound_1", payload["metadataPath"])
        assertEquals("sounds/uploader-1/sound.mp3", payload["storagePath"])
        assertEquals("Confirmed rights issue", payload["note"])
    }

    @Test
    fun `buildCommunityTakedownReceiptPayload rejects non rights or mismatched handles`() {
        assertEquals(
            "",
            communityTakedownUploadIdFromContentId(
                contentId = "SOUND::COMMUNITY::cw_wall_1",
                kind = CommunityUploadKind.SOUND,
            ),
        )

        try {
            buildCommunityTakedownReceiptPayload(
                reportId = "report-1",
                contentId = "WALLPAPER::COMMUNITY::cw_wall_1",
                contentType = "WALLPAPER",
                contentSource = "COMMUNITY",
                reason = CommunityReportReason.SPAM,
                action = CommunityTakedownAction.HIDE,
                status = CommunityReportResolutionStatus.HIDDEN,
                uploadId = "cw_wall_1",
                metadataPath = "/community_wallpapers/wall_1",
                storagePath = "wallpapers/uploader-1/wall.jpg",
                uploaderUid = "uploader-1",
                resolverUid = "admin-1",
                resolvedAt = 456L,
            )
            fail("Expected non-rights receipt to throw")
        } catch (_: IllegalArgumentException) {
        }

        try {
            buildCommunityTakedownReceiptPayload(
                reportId = "report-1",
                contentId = "WALLPAPER::COMMUNITY::cw_wall_1",
                contentType = "WALLPAPER",
                contentSource = "COMMUNITY",
                reason = CommunityReportReason.RIGHTS,
                action = CommunityTakedownAction.HIDE,
                status = CommunityReportResolutionStatus.HIDDEN,
                uploadId = "cw_wall_1",
                metadataPath = "/community_sounds/wall_1",
                storagePath = "sounds/uploader-1/wall.mp3",
                uploaderUid = "uploader-1",
                resolverUid = "admin-1",
                resolvedAt = 456L,
            )
            fail("Expected mismatched handle receipt to throw")
        } catch (_: IllegalArgumentException) {
        }
    }
}
