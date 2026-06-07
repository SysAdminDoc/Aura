package com.freevibe.data.repository

import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
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
