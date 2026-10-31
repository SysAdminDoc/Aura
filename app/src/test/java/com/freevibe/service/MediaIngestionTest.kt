package com.freevibe.service

import com.freevibe.R
import com.freevibe.util.uiText
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
        assertEquals("image/heif", sniffMediaType("....ftypheic".toByteArray())?.mimeType)
        assertEquals("image/heif", sniffMediaType("....ftypmif1".toByteArray())?.mimeType)
        assertEquals("image/avif", sniffMediaType("....ftypavif".toByteArray())?.mimeType)
        assertEquals("image/avif", sniffMediaType("....ftypavis".toByteArray())?.mimeType)
        assertEquals("heic", sniffMediaType("....ftypheic".toByteArray())?.extension)
        assertEquals("avif", sniffMediaType("....ftypavif".toByteArray())?.extension)
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
    fun `image ingestion policy defines formats per flow`() {
        val uploadPolicy = imageIngestionPolicy(MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD)
        val editorPolicy = imageIngestionPolicy(MediaIngestionImageFlow.EDITOR)

        assertTrue(IngestedImageFormat.HEIF in uploadPolicy.inputFormats)
        assertTrue(IngestedImageFormat.AVIF in uploadPolicy.inputFormats)
        assertFalse(IngestedImageFormat.GIF in uploadPolicy.inputFormats)
        assertEquals("image/jpeg", uploadPolicy.outputMimeType)
        assertTrue(uploadPolicy.stripsMetadata)
        assertTrue(IngestedImageFormat.GIF in editorPolicy.inputFormats)
        assertFalse(editorPolicy.stripsMetadata)
    }

    @Test
    fun `image ingestion support gates AVIF by Android version`() {
        val supported = imageFormatSupportForFlow(
            MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD,
            "image/avif",
            sdkInt = 34,
        )
        val rejected = imageFormatSupportForFlow(
            MediaIngestionImageFlow.COMMUNITY_WALLPAPER_UPLOAD,
            "image/avif",
            sdkInt = 33,
        )

        assertTrue(supported.supported)
        assertEquals("image/jpeg", supported.outputMimeType)
        assertTrue(supported.stripsMetadata)
        assertFalse(rejected.supported)
        assertEquals(uiText(R.string.media_image_avif_needs_android14_scrub), rejected.message)
    }

    @Test
    fun `image ingestion support accepts HEIF aliases and extension fallbacks`() {
        assertTrue(
            imageFormatSupportForFlow(
                MediaIngestionImageFlow.LOCAL_APPLY,
                "image/heic",
                sdkInt = 26,
            ).supported,
        )
        assertEquals("image/heif", imageMimeTypeFromExtension("heic"))
        assertEquals("image/heif", imageMimeTypeFromExtension(".heif"))
        assertEquals("image/avif", imageMimeTypeFromExtension("avif"))
    }

    @Test
    fun `image flow validation trusts sniffed bytes over a misleading image declaration`() {
        val support = imageFormatSupportForInput(
            flow = MediaIngestionImageFlow.EDITOR,
            header = "ID3 fake audio".toByteArray(),
            declaredMimeType = "image/jpeg",
            extension = "jpg",
            sdkInt = 35,
        )

        assertFalse(support.supported)
        assertThrows(MediaIngestionImageRejected::class.java) {
            requireImageFormatSupport(
                flow = MediaIngestionImageFlow.LOCAL_APPLY,
                header = "ID3 fake audio".toByteArray(),
                declaredMimeType = "image/jpeg",
                extension = "jpg",
                sdkInt = 35,
            )
        }
    }

    @Test
    fun `image flow validation uses extension for generic providers`() {
        val support = imageFormatSupportForInput(
            flow = MediaIngestionImageFlow.AUTO_ROTATION,
            header = byteArrayOf(),
            declaredMimeType = "application/octet-stream",
            extension = ".heic",
            sdkInt = 26,
        )

        assertTrue(support.supported)
        assertEquals(IngestedImageFormat.HEIF, support.format)
    }

    @Test
    fun `local AVIF rejection explains Android requirement without upload scrub copy`() {
        val support = imageFormatSupportForInput(
            flow = MediaIngestionImageFlow.LOCAL_APPLY,
            header = "....ftypavif".toByteArray(),
            declaredMimeType = null,
            sdkInt = 33,
        )

        assertFalse(support.supported)
        assertEquals(uiText(R.string.media_image_avif_needs_android14), support.message)
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
    fun `sniffMediaType recognises the EBML header used by WebM audio`() {
        // Real EBML/Matroska header bytes as emitted by YouTube's Opus-in-WebM audio streams.
        val header = byteArrayOf(
            0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(),
            0x9F.toByte(), 0x42.toByte(), 0x86.toByte(), 0x81.toByte(),
            0x01.toByte(), 0x42.toByte(), 0xF7.toByte(), 0x81.toByte(),
            0x01.toByte(), 0x42.toByte(), 0xF2.toByte(), 0x81.toByte(),
        )

        val sniffed = sniffMediaType(header)

        assertEquals(MediaFamily.CONTAINER, sniffed?.family)
        assertEquals("webm", sniffed?.extension)
    }

    @Test
    fun `WebM audio downloads are accepted as sounds`() {
        // Issue #44: applying a YouTube ringtone failed with
        // "Sound content type could not be verified" because WebM had no signature.
        val file = File.createTempFile("aura", ".webm").apply {
            writeBytes(
                byteArrayOf(
                    0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(),
                    0x9F.toByte(), 0x42.toByte(), 0x86.toByte(), 0x81.toByte(),
                    0x01.toByte(), 0x42.toByte(), 0xF7.toByte(), 0x81.toByte(),
                ),
            )
            deleteOnExit()
        }

        val sniffed = requireSniffedMediaFile(file, MediaFamily.AUDIO, "Sound")

        assertEquals(MediaFamily.AUDIO, sniffed.family)
        assertEquals("audio/webm", sniffed.mimeType)
        assertEquals("ringtone.webm", normalizeMediaFileName("ringtone.mp3", sniffed))
    }

    @Test
    fun `WebM is still rejected where an image is required`() {
        val file = File.createTempFile("aura", ".webm").apply {
            writeBytes(byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x9F.toByte()))
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
