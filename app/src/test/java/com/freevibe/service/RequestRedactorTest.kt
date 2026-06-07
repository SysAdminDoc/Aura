package com.freevibe.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestRedactorTest {

    @Test
    fun redactRedactsProviderQueryHeaderAndAssignmentSecrets() {
        val raw = """
            GET https://wallhaven.cc/api/v1/search?apikey=WALLHAVEN_SECRET&q=forest
            GET https://pixabay.com/api/?key=PIXABAY_SECRET
            GET https://freesound.org/apiv2/search/text/?token=FREESOUND_SECRET
            GET https://api.soundcloud.com/tracks?client_id=SOUNDCLOUD_SECRET
            Authorization: Bearer PEXELS_SECRET
            local.properties pexels.api.key=PEXELS_LOCAL_SECRET
            local.properties soundcloud.client.id=SOUNDCLOUD_LOCAL_SECRET
            local.properties stability.ai.key=STABILITY_SECRET
        """.trimIndent()

        val redacted = RequestRedactor.redact(raw)

        listOf(
            "WALLHAVEN_SECRET",
            "PIXABAY_SECRET",
            "FREESOUND_SECRET",
            "SOUNDCLOUD_SECRET",
            "PEXELS_SECRET",
            "PEXELS_LOCAL_SECRET",
            "SOUNDCLOUD_LOCAL_SECRET",
            "STABILITY_SECRET",
        ).forEach { secret ->
            assertFalse("Raw secret leaked: $secret", redacted.contains(secret))
        }
        assertTrue(redacted.contains("apikey=<redacted>"))
        assertTrue(redacted.contains("key=<redacted>"))
        assertTrue(redacted.contains("token=<redacted>"))
        assertTrue(redacted.contains("client_id=<redacted>"))
        assertTrue(redacted.contains("authorization=<redacted>"))
        assertTrue(redacted.contains("pexels.api.key=<redacted>"))
        assertTrue(redacted.contains("soundcloud.client.id=<redacted>"))
        assertTrue(redacted.contains("stability.ai.key=<redacted>"))
    }

    @Test
    fun formatRequestKeepsHostPathStatusAndRedactedQueryOnly() {
        val formatted = RequestRedactor.formatRequest(
            method = "get",
            url = "https://api.soundcloud.com/tracks/123/stream?client_id=SOUNDCLOUD_SECRET&quality=mp3#fragment",
            statusCode = 401,
        )

        assertTrue(formatted.contains("GET"))
        assertTrue(formatted.contains("api.soundcloud.com/tracks/123/stream"))
        assertTrue(formatted.contains("client_id=<redacted>"))
        assertTrue(formatted.contains("quality=mp3"))
        assertTrue(formatted.contains("status=401"))
        assertFalse(formatted.contains("https://"))
        assertFalse(formatted.contains("SOUNDCLOUD_SECRET"))
        assertFalse(formatted.contains("fragment"))
    }
}
