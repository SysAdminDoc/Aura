package com.freevibe.service

import de.sciss.jump3r.mp3.BitStream
import de.sciss.jump3r.mp3.GainAnalysis
import de.sciss.jump3r.mp3.GetAudio
import de.sciss.jump3r.mp3.ID3Tag
import de.sciss.jump3r.mp3.Lame
import de.sciss.jump3r.mp3.LameGlobalFlags
import de.sciss.jump3r.mp3.MPEGMode
import de.sciss.jump3r.mp3.Parse
import de.sciss.jump3r.mp3.Presets
import de.sciss.jump3r.mp3.Quantize
import de.sciss.jump3r.mp3.QuantizePVT
import de.sciss.jump3r.mp3.Reservoir
import de.sciss.jump3r.mp3.Takehiro
import de.sciss.jump3r.mp3.VBRTag
import de.sciss.jump3r.mp3.VbrMode
import de.sciss.jump3r.mp3.Version
import de.sciss.jump3r.mpg.Common
import de.sciss.jump3r.mpg.Interface
import de.sciss.jump3r.mpg.MPGLib
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val MP3_BLOCK_FRAMES = 4_608
private const val MP3_ENCODER_QUALITY = 5
private val MP3_SAMPLE_RATES = setOf(8_000, 11_025, 12_000, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000)

private const val FLAC_BLOCK_FRAMES = 4_096
private const val FLAC_BITS_PER_SAMPLE = 16
private const val FLAC_MAX_FIXED_ORDER = 4
private const val FLAC_MAX_PARTITION_ORDER = 5
private const val FLAC_MAX_RICE_PARAMETER = 14
private const val FLAC_STREAM_HEADER_BYTES = 42
internal const val FLAC_STREAM_MARKER = "fLaC"

internal data class PcmWavInfo(
    val sampleRate: Int,
    val channelCount: Int,
    val dataOffset: Long,
    val dataBytes: Long,
) {
    val bytesPerFrame: Int get() = channelCount * Short.SIZE_BYTES
}

internal fun readPcmWavInfo(file: File): PcmWavInfo {
    RandomAccessFile(file, "r").use { wav ->
        val tag = ByteArray(4)
        fun readTag(): String {
            wav.readFully(tag)
            return String(tag, Charsets.ISO_8859_1)
        }
        fun readLittleEndian(bytes: Int): Long {
            var value = 0L
            repeat(bytes) { value = value or (wav.readUnsignedByte().toLong() shl (8 * it)) }
            return value
        }
        require(readTag() == "RIFF") { "Not a WAV file" }
        readLittleEndian(4)
        require(readTag() == "WAVE") { "Not a WAV file" }
        var sampleRate = 0
        var channelCount = 0
        while (wav.filePointer + 8 <= wav.length()) {
            val chunk = readTag()
            val size = readLittleEndian(4)
            val body = wav.filePointer
            when (chunk) {
                "fmt " -> {
                    val encoding = readLittleEndian(2).toInt()
                    channelCount = readLittleEndian(2).toInt()
                    sampleRate = readLittleEndian(4).toInt()
                    wav.skipBytes(6)
                    val bitsPerSample = readLittleEndian(2).toInt()
                    require((encoding == 1 || encoding == 0xFFFE) && bitsPerSample == 16) {
                        "Only 16 bit PCM audio can be encoded"
                    }
                }
                "data" -> {
                    require(sampleRate > 0 && channelCount > 0) { "WAV audio data comes before its format" }
                    val available = wav.length() - body
                    return PcmWavInfo(sampleRate, channelCount, body, if (size in 1..available) size else available)
                }
            }
            wav.seek(body + size + (size and 1L))
        }
    }
    throw IllegalArgumentException("WAV file has no audio data")
}

private inline fun forEachPcmBlock(
    file: File,
    wav: PcmWavInfo,
    blockFrames: Int,
    block: (raw: ByteArray, frames: Int) -> Unit,
) {
    RandomAccessFile(file, "r").use { input ->
        input.seek(wav.dataOffset)
        val raw = ByteArray(blockFrames * wav.bytesPerFrame)
        var remaining = wav.dataBytes - wav.dataBytes % wav.bytesPerFrame
        while (remaining > 0L) {
            val size = min(remaining, raw.size.toLong()).toInt()
            input.readFully(raw, 0, size)
            block(raw, size / wav.bytesPerFrame)
            remaining -= size
        }
    }
}

private fun pcm16At(raw: ByteArray, offset: Int): Int =
    (raw[offset].toInt() and 0xFF) or (raw[offset + 1].toInt() shl 8)

private class Mp3Encoder(sampleRate: Int, channelCount: Int, bitrateKbps: Int) {
    private val lame = Lame()
    private val flags: LameGlobalFlags

    init {
        val gainAnalysis = GainAnalysis()
        val bitStream = BitStream()
        val presets = Presets()
        val quantizeTables = QuantizePVT()
        val quantize = Quantize()
        val vbrTag = VBRTag()
        val version = Version()
        val id3 = ID3Tag()
        val reservoir = Reservoir()
        val takehiro = Takehiro()
        val parse = Parse()
        val decoder = MPGLib()
        val decoderInterface = Interface()
        val common = Common()
        lame.setModules(gainAnalysis, bitStream, presets, quantizeTables, quantize, vbrTag, version, id3, decoder)
        bitStream.setModules(gainAnalysis, decoder, version, vbrTag)
        id3.setModules(bitStream, version)
        presets.setModules(lame)
        quantize.setModules(bitStream, reservoir, quantizeTables, takehiro)
        quantizeTables.setModules(takehiro, reservoir, lame.enc.psy)
        reservoir.setModules(bitStream)
        takehiro.setModules(quantizeTables)
        vbrTag.setModules(lame, bitStream, version)
        GetAudio().setModules(parse, decoder)
        parse.setModules(version, id3, presets)
        decoder.setModules(decoderInterface, common)
        decoderInterface.setModules(vbrTag, common)

        flags = lame.lame_init()
        flags.num_channels = channelCount
        flags.in_samplerate = sampleRate
        // A rate MP3 has no frame for, such as 96 kHz, is left for the encoder to resample.
        flags.out_samplerate = sampleRate.takeIf(MP3_SAMPLE_RATES::contains) ?: 0
        flags.mode = if (channelCount == 1) MPEGMode.MONO else MPEGMode.JOINT_STEREO
        flags.VBR = VbrMode.vbr_off
        flags.brate = bitrateKbps
        flags.quality = MP3_ENCODER_QUALITY
        id3.id3tag_init(flags)
        flags.write_id3tag_automatic = false
        flags.findReplayGain = false
        flags.bWriteVbrTag = false
        check(lame.lame_init_params(flags) >= 0) { "The MP3 encoder refused $sampleRate Hz at $bitrateKbps kbps" }
    }

    fun encode(left: IntArray, right: IntArray, frames: Int, output: ByteArray): Int =
        checkEncoded(lame.lame_encode_buffer_int(flags, left, right, frames, output, 0, output.size))

    fun finish(output: ByteArray): Int {
        val written = checkEncoded(lame.lame_encode_flush(flags, output, 0, output.size))
        lame.lame_close(flags)
        return written
    }

    private fun checkEncoded(written: Int): Int {
        check(written >= 0) { "The MP3 encoder failed with code $written" }
        return written
    }
}

internal fun encodeWavToMp3(
    input: File,
    output: File,
    bitrateKbps: Int,
    beforeEachBlock: () -> Unit = {},
) {
    val wav = readPcmWavInfo(input)
    val outputChannels = min(wav.channelCount, 2)
    val encoder = Mp3Encoder(wav.sampleRate, outputChannels, bitrateKbps)
    val left = IntArray(MP3_BLOCK_FRAMES)
    val right = IntArray(MP3_BLOCK_FRAMES)
    val encoded = ByteArray(MP3_BLOCK_FRAMES + MP3_BLOCK_FRAMES / 4 + 7_200)
    FileOutputStream(output).use { mp3 ->
        forEachPcmBlock(input, wav, MP3_BLOCK_FRAMES) { raw, frames ->
            beforeEachBlock()
            for (frame in 0 until frames) {
                val offset = frame * wav.bytesPerFrame
                if (wav.channelCount <= 2) {
                    left[frame] = pcm16At(raw, offset) shl 16
                    right[frame] = if (wav.channelCount == 2) pcm16At(raw, offset + 2) shl 16 else left[frame]
                } else {
                    var leftSum = 0
                    var rightSum = 0
                    for (channel in 0 until wav.channelCount) {
                        val sample = pcm16At(raw, offset + channel * 2)
                        if (channel % 2 == 0) leftSum += sample else rightSum += sample
                    }
                    left[frame] = (leftSum / ((wav.channelCount + 1) / 2)) shl 16
                    right[frame] = (rightSum / (wav.channelCount / 2)) shl 16
                }
            }
            mp3.write(encoded, 0, encoder.encode(left, right, frames, encoded))
        }
        mp3.write(encoded, 0, encoder.finish(encoded))
    }
}

/** Writes a complete FLAC file from a 16 bit WAV, and every sample decodes back unchanged. */
internal fun encodeWavToFlac(
    input: File,
    output: File,
    beforeEachBlock: () -> Unit = {},
) {
    val wav = readPcmWavInfo(input)
    require(wav.channelCount in 1..8) { "FLAC holds up to 8 channels, not ${wav.channelCount}" }
    require(wav.dataBytes >= wav.bytesPerFrame) { "WAV file has no audio data" }
    val md5 = MessageDigest.getInstance("MD5")
    val channels = Array(wav.channelCount) { IntArray(FLAC_BLOCK_FRAMES) }
    val frameEncoder = FlacFrameEncoder(wav.channelCount, wav.sampleRate)
    var totalFrames = 0L
    var frameNumber = 0L
    var smallestFrame = Int.MAX_VALUE
    var largestFrame = 0
    RandomAccessFile(output, "rw").use { flac ->
        flac.setLength(0L)
        flac.write(ByteArray(FLAC_STREAM_HEADER_BYTES))
        forEachPcmBlock(input, wav, FLAC_BLOCK_FRAMES) { raw, frames ->
            beforeEachBlock()
            md5.update(raw, 0, frames * wav.bytesPerFrame)
            for (frame in 0 until frames) {
                for (channel in 0 until wav.channelCount) {
                    channels[channel][frame] = pcm16At(raw, (frame * wav.channelCount + channel) * 2)
                }
            }
            val encoded = frameEncoder.encode(channels, frames, frameNumber)
            flac.write(encoded.bytes, 0, encoded.size)
            smallestFrame = min(smallestFrame, encoded.size)
            largestFrame = max(largestFrame, encoded.size)
            totalFrames += frames
            frameNumber += 1
        }
        val header = FlacBitWriter(FLAC_STREAM_HEADER_BYTES)
        FLAC_STREAM_MARKER.forEach { header.writeBits(it.code, 8) }
        header.writeBits(0x80, 8)
        header.writeBits(34, 24)
        header.writeBits(FLAC_BLOCK_FRAMES, 16)
        header.writeBits(FLAC_BLOCK_FRAMES, 16)
        header.writeBits(smallestFrame, 24)
        header.writeBits(largestFrame, 24)
        header.writeBits(wav.sampleRate, 20)
        header.writeBits(wav.channelCount - 1, 3)
        header.writeBits(FLAC_BITS_PER_SAMPLE - 1, 5)
        header.writeBits((totalFrames ushr 32).toInt(), 4)
        header.writeBits(totalFrames.toInt(), 32)
        md5.digest().forEach { header.writeBits(it.toInt(), 8) }
        flac.seek(0L)
        flac.write(header.bytes, 0, header.size)
    }
}

internal class FlacBitWriter(capacity: Int) {
    var bytes = ByteArray(capacity)
        private set
    var size = 0
        private set
    private var accumulator = 0L
    private var pendingBits = 0

    fun reset() {
        size = 0
        accumulator = 0L
        pendingBits = 0
    }

    fun writeBits(value: Int, count: Int) {
        accumulator = (accumulator shl count) or (value.toLong() and ((1L shl count) - 1L))
        pendingBits += count
        while (pendingBits >= 8) {
            pendingBits -= 8
            if (size == bytes.size) bytes = bytes.copyOf(bytes.size * 2)
            bytes[size++] = (accumulator ushr pendingBits).toByte()
        }
        accumulator = accumulator and ((1L shl pendingBits) - 1L)
    }

    fun writeBytes(values: ByteArray) {
        values.forEach { writeBits(it.toInt(), 8) }
    }

    fun writeRice(value: Int, parameter: Int) {
        var zeros = value ushr parameter
        while (zeros >= 32) {
            writeBits(0, 32)
            zeros -= 32
        }
        writeBits(1, zeros + 1)
        if (parameter > 0) writeBits(value, parameter)
    }

    fun alignToByte() {
        if (pendingBits > 0) writeBits(0, 8 - pendingBits)
    }
}

private class FlacPredictorChoice(val order: Int, val totalError: Long)

private class FlacRicePartitions(val partitionOrder: Int, val parameters: IntArray, val bits: Long)

private class FlacFrameEncoder(private val channelCount: Int, sampleRate: Int) {
    private val writer = FlacBitWriter(FLAC_BLOCK_FRAMES * channelCount * 3)
    private val mid = IntArray(FLAC_BLOCK_FRAMES)
    private val side = IntArray(FLAC_BLOCK_FRAMES)
    private val residual = IntArray(FLAC_BLOCK_FRAMES)
    private val sampleRateCode = when (sampleRate) {
        88_200 -> 1
        176_400 -> 2
        192_000 -> 3
        8_000 -> 4
        16_000 -> 5
        22_050 -> 6
        24_000 -> 7
        32_000 -> 8
        44_100 -> 9
        48_000 -> 10
        96_000 -> 11
        else -> 0
    }

    fun encode(channels: Array<IntArray>, frames: Int, frameNumber: Long): FlacBitWriter {
        writer.reset()
        if (channelCount == 2) {
            writeStereo(channels[0], channels[1], frames, frameNumber)
        } else {
            writeFrameHeader(frames, frameNumber, channelCount - 1)
            for (channel in 0 until channelCount) writeSubframe(channels[channel], frames, FLAC_BITS_PER_SAMPLE)
        }
        writer.alignToByte()
        writer.writeBits(flacCrc16(writer.bytes, 0, writer.size), 16)
        return writer
    }

    /** Stereo usually compresses better as a sum and a difference, so each frame takes its cheapest pairing. */
    private fun writeStereo(left: IntArray, right: IntArray, frames: Int, frameNumber: Long) {
        for (frame in 0 until frames) {
            mid[frame] = (left[frame] + right[frame]) shr 1
            side[frame] = left[frame] - right[frame]
        }
        val leftError = bestFixedPredictor(left, frames).totalError
        val rightError = bestFixedPredictor(right, frames).totalError
        val midError = bestFixedPredictor(mid, frames).totalError
        val sideError = bestFixedPredictor(side, frames).totalError
        val sideBits = FLAC_BITS_PER_SAMPLE + 1
        when (minOf(leftError + rightError, leftError + sideError, rightError + sideError, midError + sideError)) {
            leftError + rightError -> {
                writeFrameHeader(frames, frameNumber, 1)
                writeSubframe(left, frames, FLAC_BITS_PER_SAMPLE)
                writeSubframe(right, frames, FLAC_BITS_PER_SAMPLE)
            }
            leftError + sideError -> {
                writeFrameHeader(frames, frameNumber, 8)
                writeSubframe(left, frames, FLAC_BITS_PER_SAMPLE)
                writeSubframe(side, frames, sideBits)
            }
            rightError + sideError -> {
                writeFrameHeader(frames, frameNumber, 9)
                writeSubframe(side, frames, sideBits)
                writeSubframe(right, frames, FLAC_BITS_PER_SAMPLE)
            }
            else -> {
                writeFrameHeader(frames, frameNumber, 10)
                writeSubframe(mid, frames, FLAC_BITS_PER_SAMPLE)
                writeSubframe(side, frames, sideBits)
            }
        }
    }

    private fun writeFrameHeader(frames: Int, frameNumber: Long, assignment: Int) {
        writer.writeBits(0b11111111111110, 14)
        writer.writeBits(0, 2)
        val blockCode = when {
            frames == FLAC_BLOCK_FRAMES -> 0b1100
            frames <= 256 -> 0b0110
            else -> 0b0111
        }
        writer.writeBits(blockCode, 4)
        writer.writeBits(sampleRateCode, 4)
        writer.writeBits(assignment, 4)
        writer.writeBits(0b100, 3)
        writer.writeBits(0, 1)
        writer.writeBytes(flacCodedNumber(frameNumber))
        when (blockCode) {
            0b0110 -> writer.writeBits(frames - 1, 8)
            0b0111 -> writer.writeBits(frames - 1, 16)
        }
        writer.writeBits(flacCrc8(writer.bytes, 0, writer.size), 8)
    }

    private fun writeSubframe(samples: IntArray, frames: Int, bitsPerSample: Int) {
        val constant = (1 until frames).all { samples[it] == samples[0] }
        if (constant) {
            writer.writeBits(0, 8)
            writer.writeBits(samples[0], bitsPerSample)
            return
        }
        val order = bestFixedPredictor(samples, frames).order
        val count = frames - order
        fillFoldedResidual(samples, frames, order)
        val partitions = bestRicePartitions(frames, order)
        if (order * bitsPerSample + partitions.bits >= frames.toLong() * bitsPerSample) {
            writer.writeBits(0b00000010, 8)
            for (frame in 0 until frames) writer.writeBits(samples[frame], bitsPerSample)
            return
        }
        writer.writeBits(0, 1)
        writer.writeBits(0b001000 or order, 6)
        writer.writeBits(0, 1)
        for (frame in 0 until order) writer.writeBits(samples[frame], bitsPerSample)
        writer.writeBits(0, 2)
        writer.writeBits(partitions.partitionOrder, 4)
        val partitionFrames = frames shr partitions.partitionOrder
        var next = 0
        partitions.parameters.forEachIndexed { index, parameter ->
            val end = min((index + 1) * partitionFrames - order, count)
            writer.writeBits(parameter, 4)
            while (next < end) writer.writeRice(residual[next++], parameter)
        }
    }

    private fun bestFixedPredictor(samples: IntArray, frames: Int): FlacPredictorChoice {
        if (frames <= FLAC_MAX_FIXED_ORDER) {
            var total = 0L
            for (frame in 0 until frames) total += abs(samples[frame])
            return FlacPredictorChoice(0, total)
        }
        val totals = LongArray(FLAC_MAX_FIXED_ORDER + 1)
        for (i in FLAC_MAX_FIXED_ORDER until frames) {
            val s0 = samples[i]
            val s1 = samples[i - 1]
            val s2 = samples[i - 2]
            val s3 = samples[i - 3]
            val s4 = samples[i - 4]
            totals[0] += abs(s0).toLong()
            totals[1] += abs(s0 - s1).toLong()
            totals[2] += abs(s0 - 2 * s1 + s2).toLong()
            totals[3] += abs(s0 - 3 * s1 + 3 * s2 - s3).toLong()
            totals[4] += abs(s0 - 4 * s1 + 6 * s2 - 4 * s3 + s4).toLong()
        }
        var order = 0
        for (candidate in 1..FLAC_MAX_FIXED_ORDER) {
            if (totals[candidate] < totals[order]) order = candidate
        }
        return FlacPredictorChoice(order, totals[order])
    }

    private fun fillFoldedResidual(samples: IntArray, frames: Int, order: Int) {
        for (i in order until frames) {
            val error = when (order) {
                0 -> samples[i]
                1 -> samples[i] - samples[i - 1]
                2 -> samples[i] - 2 * samples[i - 1] + samples[i - 2]
                3 -> samples[i] - 3 * samples[i - 1] + 3 * samples[i - 2] - samples[i - 3]
                else -> samples[i] - 4 * samples[i - 1] + 6 * samples[i - 2] - 4 * samples[i - 3] + samples[i - 4]
            }
            residual[i - order] = (error shl 1) xor (error shr 31)
        }
    }

    private fun bestRicePartitions(frames: Int, order: Int): FlacRicePartitions {
        var best: FlacRicePartitions? = null
        for (partitionOrder in 0..FLAC_MAX_PARTITION_ORDER) {
            val partitionCount = 1 shl partitionOrder
            val partitionFrames = frames shr partitionOrder
            if (frames % partitionCount != 0 || partitionFrames <= order) break
            val parameters = IntArray(partitionCount)
            var bits = 6L
            var start = 0
            for (index in 0 until partitionCount) {
                val end = (index + 1) * partitionFrames - order
                var sum = 0L
                for (i in start until end) sum += residual[i].toLong()
                val count = end - start
                val mean = sum / count
                val guess = if (mean == 0L) 0 else 63 - java.lang.Long.numberOfLeadingZeros(mean)
                var bestParameter = 0
                var bestBits = Long.MAX_VALUE
                for (parameter in max(guess - 1, 0)..min(guess + 1, FLAC_MAX_RICE_PARAMETER)) {
                    var candidateBits = count * (parameter + 1L)
                    for (i in start until end) candidateBits += (residual[i] ushr parameter).toLong()
                    if (candidateBits < bestBits) {
                        bestBits = candidateBits
                        bestParameter = parameter
                    }
                }
                // Above the largest parameter the guess range is empty, so fall back to that largest one.
                if (bestBits == Long.MAX_VALUE) {
                    bestParameter = FLAC_MAX_RICE_PARAMETER
                    bestBits = count * (bestParameter + 1L)
                    for (i in start until end) bestBits += (residual[i] ushr bestParameter).toLong()
                }
                parameters[index] = bestParameter
                bits += 4L + bestBits
                start = end
            }
            if (best == null || bits < best.bits) best = FlacRicePartitions(partitionOrder, parameters, bits)
        }
        return checkNotNull(best)
    }
}

/** The variable length number in a FLAC frame header: UTF-8's shape, extended to 36 bits. */
internal fun flacCodedNumber(value: Long): ByteArray {
    if (value < 0x80L) return byteArrayOf(value.toByte())
    val continuationBytes = when {
        value < 0x800L -> 1
        value < 0x10000L -> 2
        value < 0x200000L -> 3
        value < 0x4000000L -> 4
        value < 0x80000000L -> 5
        else -> 6
    }
    val coded = ByteArray(continuationBytes + 1)
    var rest = value
    for (index in continuationBytes downTo 1) {
        coded[index] = (0x80L or (rest and 0x3FL)).toByte()
        rest = rest ushr 6
    }
    coded[0] = (((0xFF00 ushr (continuationBytes + 1)) and 0xFF) or rest.toInt()).toByte()
    return coded
}

private val FLAC_CRC8_TABLE = IntArray(256) { index ->
    var crc = index
    repeat(8) { crc = if (crc and 0x80 != 0) ((crc shl 1) xor 0x07) and 0xFF else (crc shl 1) and 0xFF }
    crc
}

private val FLAC_CRC16_TABLE = IntArray(256) { index ->
    var crc = index shl 8
    repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x8005) and 0xFFFF else (crc shl 1) and 0xFFFF }
    crc
}

internal fun flacCrc8(bytes: ByteArray, from: Int, until: Int): Int {
    var crc = 0
    for (index in from until until) crc = FLAC_CRC8_TABLE[crc xor (bytes[index].toInt() and 0xFF)]
    return crc
}

internal fun flacCrc16Step(crc: Int, byte: Byte): Int =
    ((crc shl 8) and 0xFFFF) xor FLAC_CRC16_TABLE[(crc ushr 8) xor (byte.toInt() and 0xFF)]

internal fun flacCrc16(bytes: ByteArray, from: Int, until: Int): Int {
    var crc = 0
    for (index in from until until) crc = flacCrc16Step(crc, bytes[index])
    return crc
}
