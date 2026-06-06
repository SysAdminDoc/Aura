package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommunityReportTest {

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
}
