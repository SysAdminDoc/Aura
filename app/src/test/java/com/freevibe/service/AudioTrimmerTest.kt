package com.freevibe.service

import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioTrimmerTest {

    @Test
    fun `a file's tags don't stop an export to WAV or OGG`() {
        listOf(PlatformAudioContainer.WAV, PlatformAudioContainer.OGG).forEach { container ->
            val output = File.createTempFile("tagged", ".audio")
            try {
                PlatformAudioMuxerFactory(PlatformAudioExportPlan(MimeTypes.AUDIO_RAW, container, null))
                    .create(output.path)
                    .use { muxer -> muxer.addMetadataEntry(mockk<Metadata.Entry>()) }
            } finally {
                output.delete()
            }
        }
    }

    @Test
    fun `a copied Vorbis packet loses the count the extractor put on its end`() {
        val written = mutableListOf<Pair<Int, BufferInfo>>()
        val delegate = mockk<Muxer>()
        every { delegate.writeSampleData(any(), any(), any()) } answers {
            written += secondArg<ByteBuffer>().remaining() to thirdArg<BufferInfo>()
        }

        VorbisPacketCopyMuxer(delegate).writeSampleData(0, ByteBuffer.allocate(20), BufferInfo(1_234L, 20, C.BUFFER_FLAG_KEY_FRAME))

        val (remaining, info) = written.single()
        assertEquals(16, remaining)
        assertEquals(16, info.size)
        assertEquals(1_234L, info.presentationTimeUs)
        assertEquals(C.BUFFER_FLAG_KEY_FRAME, info.flags)
    }

    @Test
    fun `only a copied Vorbis stream needs that count taken off`() {
        fun lossless(mimeType: String) =
            platformAudioExportPlan(AudioExportFormat.OGG, emptySet(), sdkInt = 35, losslessInputMimeType = mimeType)

        assertTrue(checkNotNull(lossless(MimeTypes.AUDIO_VORBIS)).copiesVorbisPackets)
        assertFalse(checkNotNull(lossless(MimeTypes.AUDIO_OPUS)).copiesVorbisPackets)
        val encoded = platformAudioExportPlan(AudioExportFormat.OGG, setOf(MimeTypes.AUDIO_VORBIS), sdkInt = 35)
        assertFalse(checkNotNull(encoded).copiesVorbisPackets)
    }

    @Test
    fun `platform plans route AAC WAV Vorbis and modern Opus`() {
        val availableEncoders = setOf(
            MimeTypes.AUDIO_AAC,
            MimeTypes.AUDIO_VORBIS,
            MimeTypes.AUDIO_OPUS,
        )

        assertEquals(
            PlatformAudioExportPlan(MimeTypes.AUDIO_AAC, PlatformAudioContainer.MP4, 192),
            platformAudioExportPlan(AudioExportFormat.M4A, availableEncoders, sdkInt = 26),
        )
        assertEquals(
            PlatformAudioExportPlan(MimeTypes.AUDIO_RAW, PlatformAudioContainer.WAV, null),
            platformAudioExportPlan(AudioExportFormat.WAV, emptySet(), sdkInt = 26),
        )
        assertEquals(
            PlatformAudioExportPlan(MimeTypes.AUDIO_VORBIS, PlatformAudioContainer.OGG, 192),
            platformAudioExportPlan(AudioExportFormat.OGG, availableEncoders, sdkInt = 26),
        )
        assertNull(platformAudioExportPlan(AudioExportFormat.OPUS, availableEncoders, sdkInt = 28))
        assertEquals(
            PlatformAudioExportPlan(MimeTypes.AUDIO_OPUS, PlatformAudioContainer.OGG, 96),
            platformAudioExportPlan(AudioExportFormat.OPUS, availableEncoders, sdkInt = 29),
        )
        assertNull(platformAudioExportPlan(AudioExportFormat.MP3, availableEncoders, sdkInt = 35))
        assertNull(platformAudioExportPlan(AudioExportFormat.FLAC, availableEncoders, sdkInt = 35))
    }

    @Test
    fun `lossless platform plans preserve only compatible containers`() {
        assertEquals(
            PlatformAudioExportPlan(MimeTypes.AUDIO_AAC, PlatformAudioContainer.MP4, null),
            platformAudioExportPlan(
                exportFormat = AudioExportFormat.M4A,
                availableEncoderMimeTypes = emptySet(),
                sdkInt = 26,
                losslessInputMimeType = MimeTypes.AUDIO_AAC,
            ),
        )
        assertEquals(
            PlatformAudioExportPlan(MimeTypes.AUDIO_OPUS, PlatformAudioContainer.OGG, null),
            platformAudioExportPlan(
                exportFormat = AudioExportFormat.OGG,
                availableEncoderMimeTypes = emptySet(),
                sdkInt = 26,
                losslessInputMimeType = MimeTypes.AUDIO_OPUS,
            ),
        )
        assertNull(
            platformAudioExportPlan(
                exportFormat = AudioExportFormat.MP3,
                availableEncoderMimeTypes = emptySet(),
                sdkInt = 35,
                losslessInputMimeType = MimeTypes.AUDIO_MPEG,
            ),
        )
    }

    @Test
    fun `PCM fade fixture corpus is byte comparable across all curves`() {
        val fixture = shortArrayOf(10_000, 10_000, 10_000, 10_000)

        assertArrayEquals(
            shortArrayOf(0, 5_000, 10_000, 5_000),
            processPcmFixture(fixture, AudioFadeCurve.LINEAR),
        )
        assertArrayEquals(
            shortArrayOf(0, 7_071, 10_000, 7_071),
            processPcmFixture(fixture, AudioFadeCurve.SMOOTH),
        )
        assertArrayEquals(
            shortArrayOf(0, 1_250, 10_000, 1_250),
            processPcmFixture(fixture, AudioFadeCurve.EXPONENTIAL),
        )
    }

    @Test
    fun `fade curves expose stable endpoint gains`() {
        AudioFadeCurve.entries.forEach { curve ->
            assertEquals(0f, curve.gain(0f), 0.0001f)
            assertEquals(1f, curve.gain(1f), 0.0001f)
        }
        assertEquals(0.5f, AudioFadeCurve.LINEAR.gain(0.5f), 0.0001f)
        assertEquals(0.125f, AudioFadeCurve.EXPONENTIAL.gain(0.5f), 0.0001f)
    }

    @Test
    fun `speed adjusts output duration while preserving exact one-times duration`() {
        assertEquals(4_000L, speedAdjustedDurationMs(4_000L, 1f))
        assertEquals(2_000L, speedAdjustedDurationMs(4_000L, 2f))
        assertEquals(8_000L, speedAdjustedDurationMs(4_000L, 0.5f))
    }

    @Test
    fun `OGG falls back to Opus when no Vorbis encoder exists on Android 10 and later`() {
        assertEquals(
            PlatformAudioExportPlan(MimeTypes.AUDIO_OPUS, PlatformAudioContainer.OGG, 192),
            platformAudioExportPlan(AudioExportFormat.OGG, setOf(MimeTypes.AUDIO_OPUS), sdkInt = 29),
        )
        assertNull(platformAudioExportPlan(AudioExportFormat.OGG, setOf(MimeTypes.AUDIO_OPUS), sdkInt = 28))
        assertNull(platformAudioExportPlan(AudioExportFormat.OGG, setOf(MimeTypes.AUDIO_AAC), sdkInt = 35))
    }

    @Test
    fun `export chips hide only the formats a device has no encoder for`() {
        val everything = AudioExportFormat.entries.toList()

        assertEquals(
            everything,
            exportableAudioFormats(setOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_OPUS), sdkInt = 35),
        )
        assertEquals(
            everything - AudioExportFormat.OGG - AudioExportFormat.OPUS,
            exportableAudioFormats(setOf(MimeTypes.AUDIO_AAC), sdkInt = 28),
        )
        assertEquals(
            everything - AudioExportFormat.M4A - AudioExportFormat.OGG - AudioExportFormat.OPUS,
            exportableAudioFormats(emptySet(), sdkInt = 35),
        )
    }

    @Test
    fun `lossless cut accepts only a contiguous packet copy without effects`() {
        val source = listOf(
            byteArrayOf(1, 2),
            byteArrayOf(3, 4),
            byteArrayOf(5, 6),
        )

        assertTrue(isLosslessCutAllowed(0L, 0L, playbackSpeed = 1f))
        assertFalse(isLosslessCutAllowed(1L, 0L, playbackSpeed = 1f))
        assertFalse(isLosslessCutAllowed(0L, 0L, playbackSpeed = 1.25f))
        assertTrue(
            areEncodedAudioPacketsContiguousCopy(
                source,
                listOf(byteArrayOf(3, 4), byteArrayOf(5, 6)),
            ),
        )
        assertFalse(
            areEncodedAudioPacketsContiguousCopy(
                source,
                listOf(byteArrayOf(3, 9), byteArrayOf(5, 6)),
            ),
        )
    }

    @Test
    fun `trim duration tolerance covers both codec frame boundaries`() {
        assertTrue(isTrimDurationWithinCodecBoundaries(5_000L, 5_046L, 23L))
        assertFalse(isTrimDurationWithinCodecBoundaries(5_000L, 5_047L, 23L))
    }

    @Test
    fun `window takes the exact frames out of one buffer`() {
        val processor = windowProcessor(skipMs = 2L, keepMs = 3L)

        assertArrayEquals(shortArrayOf(3, 4, 5), processor.feed(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun `window carries its count across buffers`() {
        val processor = windowProcessor(skipMs = 2L, keepMs = 5L)

        assertArrayEquals(shortArrayOf(), processor.feed(shortArrayOf(1, 2)))
        assertArrayEquals(shortArrayOf(3, 4), processor.feed(shortArrayOf(3, 4)))
        assertArrayEquals(shortArrayOf(5, 6, 7), processor.feed(shortArrayOf(5, 6, 7, 8, 9)))
        assertArrayEquals(shortArrayOf(), processor.feed(shortArrayOf(10, 11)))
    }

    @Test
    fun `window never splits a stereo frame`() {
        val processor = windowProcessor(skipMs = 1L, keepMs = 2L, channels = 2)

        assertArrayEquals(shortArrayOf(3, 4, 5, 6), processor.feed(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun `window lets a short stream end early with nothing added`() {
        val processor = windowProcessor(skipMs = 1L, keepMs = 50L)

        assertArrayEquals(shortArrayOf(2, 3), processor.feed(shortArrayOf(1, 2, 3)))
        processor.queueEndOfStream()
        assertEquals(0, processor.output.remaining())
        assertTrue(processor.isEnded)
    }

    @Test
    fun `window counts from the top again after a flush`() {
        val processor = windowProcessor(skipMs = 1L, keepMs = 1L)
        assertArrayEquals(shortArrayOf(2), processor.feed(shortArrayOf(1, 2, 3)))

        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        assertArrayEquals(shortArrayOf(5), processor.feed(shortArrayOf(4, 5, 6)))
    }

    private fun windowProcessor(skipMs: Long, keepMs: Long, channels: Int = 1) =
        PcmWindowAudioProcessor(skipUs = skipMs * 1_000L, durationUs = keepMs * 1_000L).apply {
            configure(AudioProcessor.AudioFormat(1_000, channels, C.ENCODING_PCM_16BIT))
            flush(AudioProcessor.StreamMetadata.DEFAULT)
        }

    private fun PcmWindowAudioProcessor.feed(samples: ShortArray): ShortArray {
        val input = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        samples.forEach { sample -> input.putShort(sample) }
        input.flip()
        queueInput(input)
        assertEquals(0, input.remaining())
        val pcm = output.order(ByteOrder.nativeOrder())
        return ShortArray(pcm.remaining() / Short.SIZE_BYTES) { pcm.getShort() }
    }

    private fun processPcmFixture(
        samples: ShortArray,
        curve: AudioFadeCurve,
    ): ShortArray {
        val processor = PcmFadeAudioProcessor(
            durationMs = 4L,
            fadeInMs = 2L,
            fadeOutMs = 2L,
            fadeCurve = curve,
        )
        processor.configure(
            AudioProcessor.AudioFormat(
                1_000,
                1,
                C.ENCODING_PCM_16BIT,
            ),
        )
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        samples.forEach { sample -> input.putShort(sample) }
        input.flip()
        processor.queueInput(input)
        processor.queueEndOfStream()

        val output = processor.output.order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / Short.SIZE_BYTES) { output.getShort() }
            .also { processor.reset() }
    }
}
