package com.freevibe.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class AudioFileEncodersTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun wavFile(
        samples: ShortArray,
        sampleRate: Int = 44_100,
        channelCount: Int = 1,
        leadingChunk: ByteArray = ByteArray(0),
        bitsPerSample: Int = 16,
    ): File {
        val file = folder.newFile()
        val out = ByteBuffer.allocate(44 + leadingChunk.size + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray()).putInt(36 + leadingChunk.size + samples.size * 2).put("WAVE".toByteArray())
        out.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channelCount.toShort())
        out.putInt(sampleRate).putInt(sampleRate * channelCount * bitsPerSample / 8)
        out.putShort((channelCount * bitsPerSample / 8).toShort()).putShort(bitsPerSample.toShort())
        out.put(leadingChunk)
        out.put("data".toByteArray()).putInt(samples.size * 2)
        samples.forEach { out.putShort(it) }
        file.writeBytes(out.array())
        return file
    }

    private fun sine(frames: Int, sampleRate: Int = 44_100, hz: Double = 440.0, peak: Double = 12_000.0) =
        ShortArray(frames) { (sin(2.0 * PI * hz * it / sampleRate) * peak).roundToInt().toShort() }

    private fun correlatedStereo(frames: Int) = ShortArray(frames * 2) { index ->
        val frame = index / 2
        val tone = sin(2.0 * PI * 440.0 * frame / 44_100.0) * 12_000.0
        (if (index % 2 == 0) tone else tone * 0.9 + 40.0).roundToInt().toShort()
    }

    private fun flacOf(samples: ShortArray, sampleRate: Int = 44_100, channelCount: Int = 1): File {
        val flac = folder.newFile()
        encodeWavToFlac(wavFile(samples, sampleRate, channelCount), flac)
        return flac
    }

    private fun assertLosslessRoundTrip(samples: ShortArray, sampleRate: Int = 44_100, channelCount: Int = 1) {
        val decoded = decodeFlac(flacOf(samples, sampleRate, channelCount))
        assertEquals(sampleRate, decoded.sampleRate)
        assertEquals(channelCount, decoded.channelCount)
        assertEquals((samples.size / channelCount).toLong(), decoded.totalFrames)
        assertArrayEquals(samples, decoded.interleaved)
        assertArrayEquals(MessageDigest.getInstance("MD5").digest(littleEndian(samples)), decoded.md5)
    }

    @Test
    fun `WAV reader walks past other chunks to the audio`() {
        val list = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN)
            .put("LIST".toByteArray()).putInt(5).put(byteArrayOf(1, 2, 3, 4, 5, 0))
            .array()

        val info = readPcmWavInfo(wavFile(ShortArray(10), sampleRate = 22_050, channelCount = 2, leadingChunk = list))

        assertEquals(PcmWavInfo(22_050, 2, 44L + list.size, 20L), info)
        assertEquals(4, info.bytesPerFrame)
    }

    @Test
    fun `WAV reader trusts the file over a data size that overruns it`() {
        val file = wavFile(ShortArray(4))
        val bytes = file.readBytes()
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(40, 9_999)
        file.writeBytes(bytes)

        assertEquals(8L, readPcmWavInfo(file).dataBytes)
    }

    @Test
    fun `WAV reader refuses files the encoders cannot read`() {
        assertThrows(IllegalArgumentException::class.java) { readPcmWavInfo(wavFile(ShortArray(4), bitsPerSample = 8)) }
        val notWav = folder.newFile().apply { writeBytes(ByteArray(64)) }
        assertThrows(IllegalArgumentException::class.java) { readPcmWavInfo(notWav) }
    }

    @Test
    fun `FLAC keeps every sample of a mono tone and compresses it`() {
        val samples = sine(44_100)

        assertLosslessRoundTrip(samples)
        assertTrue(flacOf(samples).length() < samples.size * 2 * 3 / 4)
    }

    @Test
    fun `FLAC keeps every sample of correlated stereo and picks a difference channel`() {
        val samples = correlatedStereo(20_000)

        assertLosslessRoundTrip(samples, channelCount = 2)
        assertTrue(decodeFlac(flacOf(samples, channelCount = 2)).assignments.any { it in 8..10 })
    }

    @Test
    fun `FLAC writes silence as constant subframes`() {
        val samples = ShortArray(FLAC_FRAME * 3)

        assertLosslessRoundTrip(samples)
        assertEquals(setOf(0), decodeFlac(flacOf(samples)).subframeTypes.toSet())
    }

    @Test
    fun `FLAC stores incompressible noise without growing much`() {
        val random = Random(7)
        val samples = ShortArray(FLAC_FRAME * 2) { random.nextInt(-32_768, 32_768).toShort() }

        assertLosslessRoundTrip(samples)
        assertTrue(decodeFlac(flacOf(samples)).subframeTypes.contains(1))
        assertTrue(flacOf(samples).length() < samples.size * 2 + 200)
    }

    @Test
    fun `FLAC holds full scale swings in the seventeen bit difference channel`() {
        val samples = ShortArray(FLAC_FRAME * 2) { index ->
            when (index % 4) {
                0 -> Short.MAX_VALUE
                1 -> Short.MIN_VALUE
                2 -> Short.MIN_VALUE
                else -> Short.MAX_VALUE
            }
        }

        assertLosslessRoundTrip(samples, channelCount = 2)
    }

    @Test
    fun `FLAC handles a short last block and tiny files`() {
        assertLosslessRoundTrip(sine(FLAC_FRAME + 100))
        assertLosslessRoundTrip(sine(FLAC_FRAME + 300))
        assertLosslessRoundTrip(sine(5))
        assertLosslessRoundTrip(sine(3))
        assertLosslessRoundTrip(shortArrayOf(1_234))
    }

    @Test
    fun `FLAC takes three channels and a sample rate with no header code`() {
        assertLosslessRoundTrip(sine(9_000), channelCount = 3)
        assertLosslessRoundTrip(sine(9_000), sampleRate = 12_345)
    }

    @Test
    fun `FLAC numbers its frames from zero`() {
        assertEquals(listOf(0L, 1L, 2L), decodeFlac(flacOf(sine(FLAC_FRAME * 3))).frameNumbers)
    }

    @Test
    fun `FLAC frame numbers use the extended UTF-8 shape`() {
        assertArrayEquals(byteArrayOf(0x7F), flacCodedNumber(0x7F))
        assertArrayEquals(bytes(0xC2, 0x80), flacCodedNumber(0x80))
        assertArrayEquals(bytes(0xDF, 0xBF), flacCodedNumber(0x7FF))
        assertArrayEquals(bytes(0xE0, 0xA0, 0x80), flacCodedNumber(0x800))
        assertArrayEquals(bytes(0xFD, 0x80, 0x80, 0x80, 0x80, 0x80), flacCodedNumber(0x40000000L))
        assertArrayEquals(bytes(0xFE, 0x82, 0x80, 0x80, 0x80, 0x80, 0x80), flacCodedNumber(0x80000000L))
    }

    @Test
    fun `FLAC checksums match the published check values`() {
        val check = "123456789".toByteArray()

        assertEquals(0xF4, flacCrc8(check, 0, check.size))
        assertEquals(0xFEE8, flacCrc16(check, 0, check.size))
    }

    @Test
    fun `an encoder stops when its block hook throws`() {
        val wav = wavFile(sine(FLAC_FRAME * 4))
        var flacBlocks = 0
        assertThrows(IllegalStateException::class.java) {
            encodeWavToFlac(wav, folder.newFile()) {
                flacBlocks += 1
                if (flacBlocks == 2) error("cancelled")
            }
        }
        var mp3Blocks = 0
        assertThrows(IllegalStateException::class.java) {
            encodeWavToMp3(wav, folder.newFile(), 128) {
                mp3Blocks += 1
                if (mp3Blocks == 2) error("cancelled")
            }
        }

        assertEquals(2, flacBlocks)
        assertEquals(2, mp3Blocks)
    }

    @Test
    fun `MP3 stereo holds whole frames at the chosen bitrate`() {
        val mp3 = folder.newFile()

        encodeWavToMp3(wavFile(correlatedStereo(44_100), channelCount = 2), mp3, 192)

        val frames = walkMp3(mp3.readBytes())
        assertTrue(frames.all { it.info.bitrateKbps == 192 && it.info.sampleRate == 44_100 && it.info.mpeg1 })
        assertTrue(frames.none { it.mono })
        assertTrue("got ${frames.size} frames", frames.size in 38..42)
    }

    @Test
    fun `MP3 mono at a low rate uses MPEG 2 frames`() {
        val mp3 = folder.newFile()

        encodeWavToMp3(wavFile(sine(22_050, sampleRate = 22_050), sampleRate = 22_050), mp3, 64)

        val frames = walkMp3(mp3.readBytes())
        assertTrue(frames.all { it.info.bitrateKbps == 64 && it.info.sampleRate == 22_050 && !it.info.mpeg1 && it.mono })
        assertTrue("got ${frames.size} frames", frames.size in 36..41)
    }

    @Test
    fun `MP3 folds extra channels into stereo and resamples rates it has no frame for`() {
        val surround = folder.newFile()
        encodeWavToMp3(wavFile(sine(9_000), channelCount = 3), surround, 128)
        assertTrue(walkMp3(surround.readBytes()).none { it.mono })

        val highRate = folder.newFile()
        encodeWavToMp3(wavFile(sine(96_000, sampleRate = 96_000), sampleRate = 96_000), highRate, 128)
        val frames = walkMp3(highRate.readBytes())
        assertTrue(frames.isNotEmpty())
        assertTrue(frames.all { it.info.sampleRate in setOf(32_000, 44_100, 48_000) })
    }

    @Test
    fun `MP3 frame headers reject anything but Layer III`() {
        assertNull(parseMp3FrameHeader(bytes(0xFF, 0xFB)))
        assertNull(parseMp3FrameHeader(bytes(0x12, 0xFB, 0x90, 0x00)))
        assertNull(parseMp3FrameHeader(bytes(0xFF, 0xFD, 0x90, 0x00)))
        assertNull(parseMp3FrameHeader(bytes(0xFF, 0xFB, 0xF0, 0x00)))
        assertNull(parseMp3FrameHeader(bytes(0xFF, 0xFB, 0x9C, 0x00)))
        assertEquals(Mp3FrameInfo(128, 44_100, true, 32), parseMp3FrameHeader(bytes(0xFF, 0xFB, 0x90, 0x00)))
        assertEquals(Mp3FrameInfo(64, 22_050, false, 17), parseMp3FrameHeader(bytes(0xFF, 0xF3, 0x80, 0x00)))
        assertEquals(Mp3FrameInfo(32, 11_025, false, 9), parseMp3FrameHeader(bytes(0xFF, 0xE3, 0x40, 0xC0)))
    }

    @Test
    fun `MP3 length tag fits even when the first frame is tiny`() {
        val tiny = bytes(0xFF, 0xFA, 0x12, 0xC4)

        val tag = mp3VbrTagFrame(tiny, audioFrameCount = 77, audioBytes = 12_345L)

        val info = checkNotNull(parseMp3FrameHeader(tag))
        assertEquals(128, info.bitrateKbps)
        assertEquals(44_100, info.sampleRate)
        assertEquals(417, tag.size)
        assertEquals("no checksum", 1, tag[1].toInt() and 1)
        assertEquals("no padding", 0, tag[2].toInt() and 2)
        val fields = ByteBuffer.wrap(tag, 4 + info.sideInfoBytes, 16)
        assertEquals("Xing", ByteArray(4).also { fields.get(it) }.toString(Charsets.ISO_8859_1))
        assertEquals(3, fields.int)
        assertEquals(77, fields.int)
        assertEquals(12_345 + 417, fields.int)
    }

    @Test
    fun `MP3 length tag keeps the format of an MPEG 2 file`() {
        val tag = mp3VbrTagFrame(bytes(0xFF, 0xF3, 0x20, 0x00), audioFrameCount = 1, audioBytes = 100L)

        val info = checkNotNull(parseMp3FrameHeader(tag))
        assertEquals(64, info.bitrateKbps)
        assertEquals(22_050, info.sampleRate)
        assertEquals(208, tag.size)
    }

    @Test
    fun `FLAC cut decodes to the same samples at frame boundaries and renumbers`() {
        val samples = correlatedStereo(44_100 * 3 / 2)
        val source = flacOf(samples, channelCount = 2)
        val cut = folder.newFile()

        cutFlacFrames(source, cut, startMs = 200L, endMs = 700L)

        val original = decodeFlac(source)
        val decoded = decodeFlac(cut)
        assertEquals(listOf(0L, 1L, 2L, 3L, 4L, 5L), decoded.frameNumbers)
        assertEquals(FLAC_FRAME * 6L, decoded.totalFrames)
        assertArrayEquals(samples.copyOfRange(FLAC_FRAME * 2 * 2, FLAC_FRAME * 8 * 2), decoded.interleaved)
        assertEquals(
            original.frameBodies.subList(2, 8).map { it.toList() },
            decoded.frameBodies.map { it.toList() },
        )
        assertArrayEquals("no checksum for the cut", ByteArray(16), decoded.md5)
    }

    @Test
    fun `FLAC cut keeps tags and drops padding`() {
        val comment = "vendor and some tags".toByteArray()
        val tagged = withExtraMetadata(flacOf(sine(FLAC_FRAME * 4)), comment)
        val cut = folder.newFile()

        cutFlacFrames(tagged, cut, startMs = 0L, endMs = 100L)

        val decoded = decodeFlac(cut)
        assertEquals(listOf(0, 4), decoded.metadataTypes)
        assertArrayEquals(comment, decoded.vorbisComment)
    }

    @Test
    fun `FLAC cut steps over an ID3 tag on either end`() {
        val plain = flacOf(sine(FLAC_FRAME * 4))
        val wrapped = folder.newFile().apply {
            val id3Body = ByteArray(300) { 0x55 }
            writeBytes(
                "ID3".toByteArray() + bytes(4, 0, 0, 0, 0, 2, 44) + id3Body + plain.readBytes() +
                    "TAG".toByteArray() + ByteArray(125),
            )
        }
        val plainCut = folder.newFile()
        val wrappedCut = folder.newFile()

        cutFlacFrames(plain, plainCut, startMs = 100L, endMs = 10_000L)
        cutFlacFrames(wrapped, wrappedCut, startMs = 100L, endMs = 10_000L)

        assertArrayEquals(plainCut.readBytes(), wrappedCut.readBytes())
        assertEquals(3, decodeFlac(wrappedCut).frameNumbers.size)
    }

    @Test
    fun `FLAC cut refuses other files and a stretch past the end`() {
        val notFlac = folder.newFile().apply { writeBytes(ByteArray(4_096)) }
        assertThrows(IllegalArgumentException::class.java) { cutFlacFrames(notFlac, folder.newFile(), 0L, 100L) }

        val short = flacOf(sine(FLAC_FRAME))
        assertThrows(IllegalArgumentException::class.java) { cutFlacFrames(short, folder.newFile(), 60_000L, 70_000L) }
    }

    @Test
    fun `FLAC cut stops on a damaged frame header`() {
        val damaged = flacOf(sine(FLAC_FRAME * 4)).apply {
            val bytes = readBytes()
            bytes[42] = 0
            writeBytes(bytes)
        }

        assertThrows(IllegalArgumentException::class.java) { cutFlacFrames(damaged, folder.newFile(), 0L, 100L) }
    }

    private fun withExtraMetadata(flac: File, comment: ByteArray): File {
        val source = flac.readBytes()
        val out = ByteArrayOutputStream()
        out.write(source, 0, 4)
        out.write(0)
        out.write(source, 5, 37)
        out.write(4)
        out.write(bytes(0, 0, comment.size))
        out.write(comment)
        out.write(0x80 or 1)
        out.write(bytes(0, 0, 32))
        out.write(ByteArray(32))
        out.write(source, 42, source.size - 42)
        return folder.newFile().apply { writeBytes(out.toByteArray()) }
    }

    private class Mp3Frame(val info: Mp3FrameInfo, val mono: Boolean)

    /** A frame's length comes from its header, so a clean walk to the last byte proves the file is whole frames. */
    private fun walkMp3(bytes: ByteArray): List<Mp3Frame> {
        val frames = ArrayList<Mp3Frame>()
        var at = 0
        while (at < bytes.size) {
            val header = bytes.copyOfRange(at, minOf(at + 4, bytes.size))
            val info = checkNotNull(parseMp3FrameHeader(header)) { "no frame at byte $at of ${bytes.size}" }
            val samplesFactor = if (info.mpeg1) 144 else 72
            val padding = (header[2].toInt() ushr 1) and 1
            frames += Mp3Frame(info, mono = (header[3].toInt() and 0xFF) ushr 6 == 3)
            at += samplesFactor * info.bitrateKbps * 1_000 / info.sampleRate + padding
        }
        assertEquals(bytes.size, at)
        return frames
    }

    private class DecodedFlac(
        val sampleRate: Int,
        val channelCount: Int,
        val totalFrames: Long,
        val md5: ByteArray,
        val metadataTypes: List<Int>,
        val vorbisComment: ByteArray?,
        val frameNumbers: List<Long>,
        val frameBodies: List<ByteArray>,
        val assignments: List<Int>,
        val subframeTypes: List<Int>,
        val interleaved: ShortArray,
    )

    private class BitReader(private val bytes: ByteArray, start: Int) {
        private var bit = start * 8L
        val bytePosition: Int get() = (bit / 8L).toInt()

        fun read(count: Int): Int = readLong(count).toInt()

        fun readLong(count: Int): Long {
            var value = 0L
            repeat(count) {
                val byte = bytes[(bit / 8L).toInt()].toInt()
                value = (value shl 1) or ((byte ushr (7 - (bit % 8L).toInt())) and 1).toLong()
                bit += 1L
            }
            return value
        }

        fun readSigned(count: Int): Int {
            val value = read(count)
            return if (count < 32 && value and (1 shl (count - 1)) != 0) value - (1 shl count) else value
        }

        fun readRice(parameter: Int): Int {
            var zeros = 0
            while (read(1) == 0) zeros += 1
            val folded = (zeros shl parameter) or if (parameter > 0) read(parameter) else 0
            return (folded ushr 1) xor -(folded and 1)
        }

        fun align() {
            if (bit % 8L != 0L) bit += 8L - bit % 8L
        }
    }

    private fun decodeFlac(file: File): DecodedFlac {
        val bytes = file.readBytes()
        assertEquals("fLaC", String(bytes, 0, 4, Charsets.ISO_8859_1))
        var at = 4
        val metadataTypes = ArrayList<Int>()
        var streamInfo: ByteArray? = null
        var vorbisComment: ByteArray? = null
        while (true) {
            val lead = bytes[at].toInt() and 0xFF
            val length = BitReader(bytes, at + 1).read(24)
            at += 4
            val body = bytes.copyOfRange(at, at + length)
            metadataTypes += lead and 0x7F
            when (lead and 0x7F) {
                0 -> streamInfo = body
                4 -> vorbisComment = body
            }
            at += length
            if (lead and 0x80 != 0) break
        }
        val infoBytes = checkNotNull(streamInfo)
        val info = BitReader(infoBytes, 0)
        info.read(16)
        info.read(16)
        info.read(24)
        info.read(24)
        val sampleRate = info.read(20)
        val channelCount = info.read(3) + 1
        assertEquals(16, info.read(5) + 1)
        val totalFrames = info.readLong(36)
        val md5 = infoBytes.copyOfRange(18, 34)

        val frameNumbers = ArrayList<Long>()
        val frameBodies = ArrayList<ByteArray>()
        val assignments = ArrayList<Int>()
        val subframeTypes = ArrayList<Int>()
        val pcm = ArrayList<ShortArray>()
        while (at < bytes.size) {
            val reader = BitReader(bytes, at)
            assertEquals(0b11111111111110, reader.read(14))
            assertEquals(0, reader.read(2))
            val blockCode = reader.read(4)
            reader.read(4)
            val assignment = reader.read(4)
            assertEquals(0b100, reader.read(3))
            assertEquals(0, reader.read(1))
            frameNumbers += readCodedNumber(reader)
            val frames = when (blockCode) {
                0b0110 -> reader.read(8) + 1
                0b0111 -> reader.read(16) + 1
                0b1100 -> FLAC_FRAME
                else -> throw AssertionError("unexpected block code $blockCode")
            }
            assertEquals("header checksum", flacCrc8(bytes, at, reader.bytePosition), reader.read(8))
            val bodyStart = reader.bytePosition
            assignments += assignment
            val subframes = when (assignment) {
                8 -> arrayOf(
                    readSubframe(reader, frames, 16, subframeTypes),
                    readSubframe(reader, frames, 17, subframeTypes),
                ).also { it[1] = IntArray(frames) { i -> it[0][i] - it[1][i] } }
                9 -> arrayOf(
                    readSubframe(reader, frames, 17, subframeTypes),
                    readSubframe(reader, frames, 16, subframeTypes),
                ).also { it[0] = IntArray(frames) { i -> it[0][i] + it[1][i] } }
                10 -> {
                    val mid = readSubframe(reader, frames, 16, subframeTypes)
                    val side = readSubframe(reader, frames, 17, subframeTypes)
                    arrayOf(
                        IntArray(frames) { i -> (((mid[i] shl 1) or (side[i] and 1)) + side[i]) shr 1 },
                        IntArray(frames) { i -> (((mid[i] shl 1) or (side[i] and 1)) - side[i]) shr 1 },
                    )
                }
                else -> Array(assignment + 1) { readSubframe(reader, frames, 16, subframeTypes) }
            }
            reader.align()
            val bodyEnd = reader.bytePosition
            assertEquals("frame checksum", flacCrc16(bytes, at, bodyEnd), reader.read(16))
            frameBodies += bytes.copyOfRange(bodyStart, bodyEnd)
            pcm += ShortArray(frames * subframes.size) { index ->
                subframes[index % subframes.size][index / subframes.size].toShort()
            }
            at = reader.bytePosition
        }
        val interleaved = ShortArray(pcm.sumOf { it.size })
        var offset = 0
        pcm.forEach {
            it.copyInto(interleaved, offset)
            offset += it.size
        }
        return DecodedFlac(
            sampleRate = sampleRate,
            channelCount = channelCount,
            totalFrames = totalFrames,
            md5 = md5,
            metadataTypes = metadataTypes,
            vorbisComment = vorbisComment,
            frameNumbers = frameNumbers,
            frameBodies = frameBodies,
            assignments = assignments,
            subframeTypes = subframeTypes,
            interleaved = interleaved,
        )
    }

    private fun readCodedNumber(reader: BitReader): Long {
        val lead = reader.read(8)
        if (lead < 0x80) return lead.toLong()
        val continuation = Integer.numberOfLeadingZeros(lead.inv() and 0xFF) - 24 - 1
        var value = (lead and (0x7F ushr (continuation + 1))).toLong()
        repeat(continuation) { value = (value shl 6) or (reader.read(8) and 0x3F).toLong() }
        return value
    }

    private fun readSubframe(reader: BitReader, frames: Int, bitsPerSample: Int, types: MutableList<Int>): IntArray {
        assertEquals(0, reader.read(1))
        val type = reader.read(6)
        assertEquals(0, reader.read(1))
        types += type
        return when {
            type == 0 -> reader.readSigned(bitsPerSample).let { value -> IntArray(frames) { value } }
            type == 1 -> IntArray(frames) { reader.readSigned(bitsPerSample) }
            type and 0b111000 == 0b001000 -> readFixedSubframe(reader, frames, bitsPerSample, type and 7)
            else -> throw AssertionError("unexpected subframe type $type")
        }
    }

    private fun readFixedSubframe(reader: BitReader, frames: Int, bitsPerSample: Int, order: Int): IntArray {
        val samples = IntArray(frames)
        for (i in 0 until order) samples[i] = reader.readSigned(bitsPerSample)
        assertEquals(0, reader.read(2))
        val partitionOrder = reader.read(4)
        val partitionFrames = frames shr partitionOrder
        var i = order
        for (partition in 0 until (1 shl partitionOrder)) {
            val parameter = reader.read(4)
            assertTrue("no escape codes expected", parameter < 15)
            val end = if (partition == (1 shl partitionOrder) - 1) frames else (partition + 1) * partitionFrames
            while (i < end) {
                val error = reader.readRice(parameter)
                samples[i] = error + when (order) {
                    0 -> 0
                    1 -> samples[i - 1]
                    2 -> 2 * samples[i - 1] - samples[i - 2]
                    3 -> 3 * samples[i - 1] - 3 * samples[i - 2] + samples[i - 3]
                    else -> 4 * samples[i - 1] - 6 * samples[i - 2] + 4 * samples[i - 3] - samples[i - 4]
                }
                i += 1
            }
        }
        assertEquals(frames, i)
        return samples
    }

    private fun littleEndian(samples: ShortArray): ByteArray {
        val out = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { out.putShort(it) }
        return out.array()
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private companion object {
        const val FLAC_FRAME = 4_096
    }
}
