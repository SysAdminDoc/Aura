package com.freevibe.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class MediaIngestionTest {

    @Test
    fun `advertisedLengthExceeds only rejects known oversized lengths`() {
        assertFalse(advertisedLengthExceeds(-1, 10))
        assertFalse(advertisedLengthExceeds(0, 10))
        assertFalse(advertisedLengthExceeds(10, 10))
        assertTrue(advertisedLengthExceeds(11, 10))
    }

    @Test
    fun `copyStreamCapped copies bytes within limit`() {
        val output = ByteArrayOutputStream()

        val copied = copyStreamCapped(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, maxBytes = 3)

        assertEquals(3, copied)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test
    fun `copyStreamCapped rejects chunked oversized input`() {
        val output = ByteArrayOutputStream()

        assertThrows(MediaIngestionLimitExceeded::class.java) {
            copyStreamCapped(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), output, maxBytes = 3)
        }
    }

    @Test
    fun `readStreamCapped returns exact bytes`() {
        assertArrayEquals(
            byteArrayOf(9, 8, 7),
            readStreamCapped(ByteArrayInputStream(byteArrayOf(9, 8, 7)), maxBytes = 3),
        )
    }

    @Test
    fun `sniffMediaType detects supported images`() {
        assertEquals("image/jpeg", sniffMediaType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))?.mimeType)
        assertEquals("image/png", sniffMediaType(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))?.mimeType)
        assertEquals("image/gif", sniffMediaType("GIF89a".toByteArray())?.mimeType)
        assertEquals("image/webp", sniffMediaType("RIFF....WEBP".toByteArray())?.mimeType)
    }

    @Test
    fun `sniffMediaType detects supported audio`() {
        assertEquals("audio/mpeg", sniffMediaType("ID3....".toByteArray())?.mimeType)
        assertEquals("audio/mpeg", sniffMediaType(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x00))?.mimeType)
        assertEquals("audio/aac", sniffMediaType(byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50))?.mimeType)
        assertEquals("audio/ogg", sniffMediaType("OggS....".toByteArray())?.mimeType)
        assertEquals("audio/wav", sniffMediaType("RIFF....WAVE".toByteArray())?.mimeType)
        assertEquals("audio/flac", sniffMediaType("fLaC....".toByteArray())?.mimeType)
        assertEquals("audio/mp4", sniffMediaType("....ftypM4A ".toByteArray())?.mimeType)
    }

    @Test
    fun `requireSniffedMediaFile rejects wrong media family`() {
        val file = File.createTempFile("aura", ".jpg").apply {
            writeText("<html>not an image</html>")
            deleteOnExit()
        }

        assertThrows(java.io.IOException::class.java) {
            requireSniffedMediaFile(file, MediaFamily.IMAGE, "Wallpaper")
        }
    }

    @Test
    fun `normalizeMediaFileName replaces misleading extension`() {
        val name = normalizeMediaFileName(
            "wallpaper.jpg",
            SniffedMediaType(MediaFamily.IMAGE, "image/png", "png"),
        )

        assertEquals("wallpaper.png", name)
    }
}
