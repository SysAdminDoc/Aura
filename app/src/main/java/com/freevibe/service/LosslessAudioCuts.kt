package com.freevibe.service

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private const val MP3_HEADER_BYTES = 4
private const val MPEG1_TAG_KBPS = 128
private const val MPEG2_TAG_KBPS = 64
private const val FLAC_STREAM_INFO_BYTES = 34
private const val FLAC_LAST_BLOCK_FLAG = 0x80
private const val FLAC_BLOCK_STREAM_INFO = 0
private const val FLAC_BLOCK_VORBIS_COMMENT = 4
private const val FLAC_BLOCK_PICTURE = 6
private const val ID3V1_TAG_BYTES = 128L

private val MPEG1_LAYER3_KBPS = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
private val MPEG2_LAYER3_KBPS = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
private val MPEG1_SAMPLE_RATES = intArrayOf(44_100, 48_000, 32_000)

internal data class Mp3FrameInfo(
    val bitrateKbps: Int,
    val sampleRate: Int,
    val mpeg1: Boolean,
    val sideInfoBytes: Int,
)

internal fun parseMp3FrameHeader(frame: ByteArray): Mp3FrameInfo? {
    if (frame.size < MP3_HEADER_BYTES) return null
    val second = frame[1].toInt() and 0xFF
    val third = frame[2].toInt() and 0xFF
    val fourth = frame[3].toInt() and 0xFF
    if (frame[0].toInt() and 0xFF != 0xFF || second and 0xE0 != 0xE0) return null
    val version = (second ushr 3) and 3
    val layer = (second ushr 1) and 3
    if (version == 1 || layer != 1) return null
    val bitrateIndex = third ushr 4
    val rateIndex = (third ushr 2) and 3
    if (bitrateIndex == 0 || bitrateIndex == 15 || rateIndex == 3) return null
    val mpeg1 = version == 3
    val bitrateKbps = (if (mpeg1) MPEG1_LAYER3_KBPS else MPEG2_LAYER3_KBPS)[bitrateIndex]
    val sampleRate = when (version) {
        3 -> MPEG1_SAMPLE_RATES[rateIndex]
        2 -> MPEG1_SAMPLE_RATES[rateIndex] / 2
        else -> MPEG1_SAMPLE_RATES[rateIndex] / 4
    }
    val mono = fourth ushr 6 == 3
    return Mp3FrameInfo(
        bitrateKbps = bitrateKbps,
        sampleRate = sampleRate,
        mpeg1 = mpeg1,
        sideInfoBytes = when {
            mpeg1 -> if (mono) 17 else 32
            else -> if (mono) 9 else 17
        },
    )
}

/**
 * Without this frame a player guesses a variable bitrate file's length from its first frame, and
 * the cut would report the wrong duration. It holds no audio, so decoders skip it. It keeps the
 * first frame's format at a bitrate roomy enough for the tag, since that frame may be tiny.
 */
internal fun mp3VbrTagFrame(firstFrame: ByteArray, audioFrameCount: Int, audioBytes: Long): ByteArray {
    val info = requireNotNull(parseMp3FrameHeader(firstFrame)) { "Not an MP3 frame" }
    val tagOffset = MP3_HEADER_BYTES + info.sideInfoBytes
    val tag = ByteArray(
        if (info.mpeg1) 144 * MPEG1_TAG_KBPS * 1_000 / info.sampleRate else 72 * MPEG2_TAG_KBPS * 1_000 / info.sampleRate,
    )
    val bitrateIndex = if (info.mpeg1) MPEG1_LAYER3_KBPS.indexOf(MPEG1_TAG_KBPS) else MPEG2_LAYER3_KBPS.indexOf(MPEG2_TAG_KBPS)
    tag[0] = firstFrame[0]
    tag[1] = (firstFrame[1].toInt() or 1).toByte()
    tag[2] = ((bitrateIndex shl 4) or (firstFrame[2].toInt() and 0x0C)).toByte()
    tag[3] = firstFrame[3]
    "Xing".forEachIndexed { index, char -> tag[tagOffset + index] = char.code.toByte() }
    val hasFramesAndBytes = 3
    val fields = intArrayOf(hasFramesAndBytes, audioFrameCount, (audioBytes + tag.size).toInt())
    fields.forEachIndexed { index, value ->
        val at = tagOffset + 4 + index * 4
        tag[at] = (value ushr 24).toByte()
        tag[at + 1] = (value ushr 16).toByte()
        tag[at + 2] = (value ushr 8).toByte()
        tag[at + 3] = value.toByte()
    }
    return tag
}

private class FlacFrameHeader(
    val length: Int,
    val numberLength: Int,
    val blockFrames: Int,
    val variableBlocks: Boolean,
)

private fun parseFlacFrameHeader(source: ByteBuffer, at: Int, limit: Int): FlacFrameHeader? {
    if (at + 6 > limit) return null
    val first = source.get(at).toInt() and 0xFF
    val second = source.get(at + 1).toInt() and 0xFF
    if (first != 0xFF || second and 0xFE != 0xF8) return null
    val third = source.get(at + 2).toInt() and 0xFF
    val fourth = source.get(at + 3).toInt() and 0xFF
    val blockCode = third ushr 4
    val rateCode = third and 0x0F
    val channelCode = fourth ushr 4
    val sizeCode = (fourth ushr 1) and 7
    if (blockCode == 0 || rateCode == 15 || channelCode > 10 || sizeCode == 3 || fourth and 1 != 0) return null
    val numberLead = source.get(at + 4).toInt() and 0xFF
    val numberLength = when {
        numberLead and 0x80 == 0 -> 1
        numberLead and 0xE0 == 0xC0 -> 2
        numberLead and 0xF0 == 0xE0 -> 3
        numberLead and 0xF8 == 0xF0 -> 4
        numberLead and 0xFC == 0xF8 -> 5
        numberLead and 0xFE == 0xFC -> 6
        numberLead == 0xFE -> 7
        else -> return null
    }
    var cursor = at + 4 + numberLength
    val blockFrames = when (blockCode) {
        1 -> 192
        in 2..5 -> 576 shl (blockCode - 2)
        6 -> {
            if (cursor >= limit) return null
            (source.get(cursor++).toInt() and 0xFF) + 1
        }
        7 -> {
            if (cursor + 1 >= limit) return null
            val high = source.get(cursor++).toInt() and 0xFF
            ((high shl 8) or (source.get(cursor++).toInt() and 0xFF)) + 1
        }
        else -> 256 shl (blockCode - 8)
    }
    cursor += when (rateCode) {
        12 -> 1
        13, 14 -> 2
        else -> 0
    }
    if (cursor >= limit) return null
    val header = ByteArray(cursor - at)
    source.copyTo(at, header, 0, header.size)
    if (flacCrc8(header, 0, header.size) != source.get(cursor).toInt() and 0xFF) return null
    return FlacFrameHeader(
        length = cursor + 1 - at,
        numberLength = numberLength,
        blockFrames = blockFrames,
        variableBlocks = second and 1 == 1,
    )
}

private fun ByteBuffer.copyTo(from: Int, target: ByteArray, targetOffset: Int, count: Int) {
    val view = duplicate()
    view.position(from)
    view.get(target, targetOffset, count)
}

/** A frame ends where its checksum comes out clean and a real frame header or the file's end follows. */
private fun flacFrameEnd(source: ByteBuffer, start: Int, header: FlacFrameHeader, limit: Int): Int? {
    var crc = 0
    var at = start
    while (at < start + header.length) crc = flacCrc16Step(crc, source.get(at++))
    while (at < limit) {
        crc = flacCrc16Step(crc, source.get(at++))
        if (
            crc == 0 &&
            at - start >= header.length + 2 &&
            (at == limit || parseFlacFrameHeader(source, at, limit) != null)
        ) {
            return at
        }
    }
    return null
}

private fun renumberedFlacFrame(
    source: ByteBuffer,
    start: Int,
    end: Int,
    header: FlacFrameHeader,
    number: Long,
): ByteArray {
    val coded = flacCodedNumber(number)
    val leadBytes = 4
    val afterNumber = leadBytes + header.numberLength
    val extraHeaderBytes = header.length - afterNumber - 1
    val bodyBytes = end - start - header.length - 2
    val frame = ByteArray(leadBytes + coded.size + extraHeaderBytes + 1 + bodyBytes + 2)
    source.copyTo(start, frame, 0, leadBytes)
    coded.copyInto(frame, leadBytes)
    var cursor = leadBytes + coded.size
    source.copyTo(start + afterNumber, frame, cursor, extraHeaderBytes)
    cursor += extraHeaderBytes
    frame[cursor] = flacCrc8(frame, 0, cursor).toByte()
    cursor += 1
    source.copyTo(start + header.length, frame, cursor, bodyBytes)
    cursor += bodyBytes
    val crc = flacCrc16(frame, 0, cursor)
    frame[cursor] = (crc ushr 8).toByte()
    frame[cursor + 1] = crc.toByte()
    return frame
}

/**
 * Copies the FLAC frames that cover the chosen stretch. Each frame's audio stays byte for byte as it
 * was. Only its number changes, so the cut starts at zero and players don't wait out the missing time.
 */
internal fun cutFlacFrames(
    input: File,
    output: File,
    startMs: Long,
    endMs: Long,
    beforeEachFrame: () -> Unit = {},
) {
    RandomAccessFile(input, "r").use { file ->
        require(file.length() in 1L..Int.MAX_VALUE.toLong()) { "FLAC file is too large to cut" }
        val source = file.channel.map(FileChannel.MapMode.READ_ONLY, 0L, file.length())
        var limit = file.length().toInt()
        if (limit > ID3V1_TAG_BYTES && source.matchesAscii(limit - ID3V1_TAG_BYTES.toInt(), "TAG")) {
            limit -= ID3V1_TAG_BYTES.toInt()
        }

        var cursor = 0
        if (source.matchesAscii(0, "ID3") && limit > 10) {
            var tagBody = 0
            for (index in 6..9) tagBody = (tagBody shl 7) or (source.get(index).toInt() and 0x7F)
            cursor = 10 + tagBody
        }
        require(source.matchesAscii(cursor, FLAC_STREAM_MARKER)) { "Not a FLAC file" }
        cursor += FLAC_STREAM_MARKER.length

        var streamInfo: ByteArray? = null
        val keptBlocks = ArrayList<Pair<Int, ByteArray>>()
        while (true) {
            require(cursor + 4 <= limit) { "FLAC metadata is cut short" }
            val lead = source.get(cursor).toInt() and 0xFF
            val type = lead and 0x7F
            val length = ((source.get(cursor + 1).toInt() and 0xFF) shl 16) or
                ((source.get(cursor + 2).toInt() and 0xFF) shl 8) or
                (source.get(cursor + 3).toInt() and 0xFF)
            cursor += 4
            require(cursor + length <= limit) { "FLAC metadata is cut short" }
            if (type == FLAC_BLOCK_STREAM_INFO || type == FLAC_BLOCK_VORBIS_COMMENT || type == FLAC_BLOCK_PICTURE) {
                val body = ByteArray(length)
                source.copyTo(cursor, body, 0, length)
                if (type == FLAC_BLOCK_STREAM_INFO) streamInfo = body else keptBlocks += type to body
            }
            cursor += length
            if (lead and FLAC_LAST_BLOCK_FLAG != 0) break
        }
        val info = streamInfo?.takeIf { it.size == FLAC_STREAM_INFO_BYTES }
            ?: throw IllegalArgumentException("FLAC file has no stream information")
        val sampleRate = ((info[10].toInt() and 0xFF) shl 12) or
            ((info[11].toInt() and 0xFF) shl 4) or
            ((info[12].toInt() and 0xF0) ushr 4)
        require(sampleRate > 0) { "FLAC file has no sample rate" }
        val startFrame = startMs * sampleRate / 1_000L
        val endFrame = ceil(endMs * sampleRate / 1_000.0).toLong()

        RandomAccessFile(output, "rw").use { cut ->
            cut.setLength(0L)
            cut.write(FLAC_STREAM_MARKER.toByteArray(Charsets.ISO_8859_1))
            val blocks = listOf(FLAC_BLOCK_STREAM_INFO to info) + keptBlocks
            blocks.forEachIndexed { index, (type, body) ->
                cut.writeByte(if (index == blocks.lastIndex) type or FLAC_LAST_BLOCK_FLAG else type)
                cut.writeByte(body.size ushr 16)
                cut.writeByte(body.size ushr 8)
                cut.writeByte(body.size)
                cut.write(body)
            }

            var sourcePosition = 0L
            var copiedFrames = 0L
            var copiedSamples = 0L
            var smallest = Int.MAX_VALUE
            var largest = 0
            while (cursor < limit && sourcePosition < endFrame) {
                beforeEachFrame()
                val header = parseFlacFrameHeader(source, cursor, limit)
                    ?: throw IllegalArgumentException("FLAC audio is damaged at byte $cursor")
                val end = flacFrameEnd(source, cursor, header, limit) ?: break
                if (sourcePosition + header.blockFrames > startFrame) {
                    val number = if (header.variableBlocks) copiedSamples else copiedFrames
                    val frame = renumberedFlacFrame(source, cursor, end, header, number)
                    cut.write(frame)
                    smallest = min(smallest, frame.size)
                    largest = max(largest, frame.size)
                    copiedFrames += 1
                    copiedSamples += header.blockFrames
                }
                sourcePosition += header.blockFrames
                cursor = end
            }
            require(copiedFrames > 0L) { "The chosen stretch is past the end of the FLAC audio" }

            val streamInfoBody = FLAC_STREAM_MARKER.length + 4L
            info[4] = (smallest ushr 16).toByte()
            info[5] = (smallest ushr 8).toByte()
            info[6] = smallest.toByte()
            info[7] = (largest ushr 16).toByte()
            info[8] = (largest ushr 8).toByte()
            info[9] = largest.toByte()
            info[13] = ((info[13].toInt() and 0xF0) or ((copiedSamples ushr 32).toInt() and 0x0F)).toByte()
            info[14] = (copiedSamples ushr 24).toByte()
            info[15] = (copiedSamples ushr 16).toByte()
            info[16] = (copiedSamples ushr 8).toByte()
            info[17] = copiedSamples.toByte()
            // An all zero checksum means none was recorded, and the source's covers the whole file.
            info.fill(0, 18, FLAC_STREAM_INFO_BYTES)
            cut.seek(streamInfoBody)
            cut.write(info)
        }
    }
}

private fun ByteBuffer.matchesAscii(at: Int, text: String): Boolean =
    at + text.length <= capacity() && text.indices.all { get(at + it).toInt() == text[it].code }
