package com.freevibe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeRepositoryTest {

    @Test
    fun `legacy compatible NewPipe search handler uses the Android 1 encoder path`() {
        val handler = createLegacyCompatibleYouTubeSearchHandler("alarm & rain")

        assertEquals("alarm & rain", handler.searchString)
        assertEquals(
            "https://www.youtube.com/results?search_query=alarm+%26+rain&sp=8AEB",
            handler.url,
        )
        assertTrue(handler.contentFilters.isEmpty())
    }

    @Test
    fun `display dimensions are swapped for rotated portrait video`() {
        val dimensions = displayCorrectVideoDimensions(
            width = 1920,
            height = 1080,
            rotationDegrees = 90,
        )

        assertEquals(1080, dimensions.width)
        assertEquals(1920, dimensions.height)
    }

    @Test
    fun `yt-dlp metadata parser normalizes rotated stream metadata`() {
        val metadata = parseYtDlpVideoMetadataOutput(
            """
            width=1920
            height=1080
            rotation=90
            duration=14.2
            ext=mp4
            vcodec=avc1.640028
            """.trimIndent(),
        )

        assertEquals(1080, metadata?.width)
        assertEquals(1920, metadata?.height)
        assertEquals(90, metadata?.rotationDegrees)
        assertEquals(14L, metadata?.durationSeconds)
        assertEquals("video/mp4", metadata?.mimeType)
        assertEquals("avc1.640028", metadata?.videoCodec)
    }

    @Test
    fun `yt-dlp metadata parser returns null for all unknown values`() {
        val metadata = parseYtDlpVideoMetadataOutput(
            """
            width=NA
            height=NA
            rotation=NA
            duration=NA
            ext=NA
            vcodec=none
            """.trimIndent(),
        )

        assertNull(metadata)
    }

    @Test
    fun `yt-dlp search parser builds sounds and drops malformed rows`() {
        val sounds = parseYtDlpSearchOutput(
            listOf(
                "abcdefghijk\tWarm Bell\tAura Studio\t12.5",
                "invalid\tBad row\tUnknown\t8",
                "lmnopqrstuv\tMissing duration\tCreator\tNA",
            ).joinToString("\n"),
        )

        assertEquals(1, sounds.size)
        assertEquals("yt_abcdefghijk", sounds.single().id)
        assertEquals("Warm Bell", sounds.single().name)
        assertEquals("Aura Studio", sounds.single().uploaderName)
        assertEquals(12.5, sounds.single().duration, 0.0)
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", sounds.single().sourcePageUrl)
    }
}
