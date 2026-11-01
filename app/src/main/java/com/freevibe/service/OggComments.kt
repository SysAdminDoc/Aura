package com.freevibe.service

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.min

private const val OGG_PAGE_HEADER_BYTES = 27
private const val OGG_SEGMENT_COUNT_AT = 26
private const val OGG_CHECKSUM_AT = 22
private const val OGG_SEQUENCE_AT = 18
private const val OGG_SERIAL_AT = 14
private const val OGG_GRANULE_AT = 6
private const val OGG_FLAGS_AT = 5
private const val OGG_FLAG_CONTINUED = 1
private const val OGG_FLAG_FIRST_PAGE = 2
private const val OGG_MAX_SEGMENTS = 255
private const val OGG_MAX_SEGMENT_BYTES = 255
private const val OGG_CHECKSUM_POLYNOMIAL = 0x04C11DB7

private val OGG_CAPTURE_PATTERN = "OggS".toByteArray(Charsets.ISO_8859_1)
private val OPUS_HEAD = "OpusHead".toByteArray(Charsets.ISO_8859_1)
private val OPUS_TAGS = "OpusTags".toByteArray(Charsets.ISO_8859_1)
private val VORBIS_ID = "\u0001vorbis".toByteArray(Charsets.ISO_8859_1)
private val VORBIS_COMMENT = "\u0003vorbis".toByteArray(Charsets.ISO_8859_1)

private val OGG_CHECKSUM_TABLE = IntArray(256) { index ->
    var value = index shl 24
    repeat(8) {
        value = if (value < 0) (value shl 1) xor OGG_CHECKSUM_POLYNOMIAL else value shl 1
    }
    value
}

/**
 * Adds one comment to a finished Opus or Vorbis OGG file. Media3's OggMuxer always writes an empty
 * comment list, and its addMetadataEntry throws, so the tag has to go in after the export. Audio pages
 * keep their bytes, apart from their page number if the headers now fill a different number of pages.
 */
internal fun addOggComment(file: File, comment: String) {
    val tagged = File(file.parentFile, "${file.name}.tagged")
    try {
        DataInputStream(file.inputStream().buffered()).use { input ->
            tagged.outputStream().buffered().use { output ->
                copyWithOggComment(input, output, comment.toByteArray(Charsets.UTF_8))
            }
        }
        Files.move(tagged.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    } finally {
        tagged.delete()
    }
}

/**
 * Walks the audio packets of an Opus or Vorbis OGG file as they sit in the file, skipping the codec's
 * headers. MediaExtractor can't stand in for this: it adds four bytes to every Vorbis packet that
 * depend on how the file's pages are laid out. [block] answers false to stop early, and the second
 * value it gets is always 0, since nothing here needs a time.
 */
internal fun forEachOggAudioPacket(path: String, block: (ByteArray, Long) -> Boolean): Boolean {
    DataInputStream(File(path).inputStream().buffered()).use { input ->
        val partial = ByteArrayOutputStream()
        var headersLeft = -1
        while (true) {
            val page = readOggPage(input) ?: return true
            val segments = page[OGG_SEGMENT_COUNT_AT].toInt() and 0xFF
            var at = OGG_PAGE_HEADER_BYTES + segments
            for (segment in 0 until segments) {
                val length = page[OGG_PAGE_HEADER_BYTES + segment].toInt() and 0xFF
                partial.write(page, at, length)
                at += length
                if (length == OGG_MAX_SEGMENT_BYTES) continue
                val packet = partial.toByteArray()
                partial.reset()
                when {
                    headersLeft < 0 -> headersLeft = when {
                        packet.startsWithBytes(OPUS_HEAD) -> 1
                        packet.startsWithBytes(VORBIS_ID) -> 2
                        else -> throw IllegalArgumentException("OGG file isn't Opus or Vorbis")
                    }
                    headersLeft > 0 -> headersLeft -= 1
                    !block(packet, 0L) -> return false
                }
            }
        }
    }
}

private fun copyWithOggComment(input: DataInputStream, output: OutputStream, comment: ByteArray) {
    val firstPage = readOggPage(input) ?: throw IllegalArgumentException("OGG file is empty")
    require(firstPage[OGG_FLAGS_AT].toInt() and OGG_FLAG_FIRST_PAGE != 0) { "OGG file has no first page" }
    val identification = oggPageBody(firstPage)
    val commentSignature: ByteArray
    val headerPacketsAfterFirst: Int
    when {
        identification.startsWithBytes(OPUS_HEAD) -> {
            commentSignature = OPUS_TAGS
            headerPacketsAfterFirst = 1
        }
        identification.startsWithBytes(VORBIS_ID) -> {
            commentSignature = VORBIS_COMMENT
            headerPacketsAfterFirst = 2
        }
        else -> throw IllegalArgumentException("OGG file isn't Opus or Vorbis")
    }
    output.write(firstPage)

    val packets = ArrayList<ByteArray>()
    val partial = ByteArrayOutputStream()
    var headerPages = 0
    while (packets.size < headerPacketsAfterFirst) {
        val page = readOggPage(input) ?: throw IllegalArgumentException("OGG headers are cut short")
        headerPages += 1
        val segments = page[OGG_SEGMENT_COUNT_AT].toInt() and 0xFF
        var at = OGG_PAGE_HEADER_BYTES + segments
        for (segment in 0 until segments) {
            val length = page[OGG_PAGE_HEADER_BYTES + segment].toInt() and 0xFF
            partial.write(page, at, length)
            at += length
            if (length < OGG_MAX_SEGMENT_BYTES) {
                packets += partial.toByteArray()
                partial.reset()
            }
        }
    }
    // Both codecs start their audio on a fresh page, which is what lets these pages be rebuilt alone.
    require(packets.size == headerPacketsAfterFirst && partial.size() == 0) {
        "OGG audio shares a page with the headers"
    }
    require(packets[0].startsWithBytes(commentSignature)) { "OGG comment header is missing" }
    packets[0] = commentPacketWith(packets[0], commentSignature.size, comment)

    val serial = firstPage.copyOfRange(OGG_SERIAL_AT, OGG_SERIAL_AT + 4)
    val rewrittenPages = writeOggHeaderPages(output, serial, firstSequence = 1, packets)
    val shift = rewrittenPages - headerPages
    while (true) {
        val page = readOggPage(input) ?: break
        if (shift != 0) {
            writeLittleEndianInt(page, OGG_SEQUENCE_AT, readLittleEndianInt(page, OGG_SEQUENCE_AT) + shift)
            writeOggChecksum(page)
        }
        output.write(page)
    }
}

/** Whatever follows the comment list stays: Vorbis ends with a framing bit, and Opus may carry padding. */
private fun commentPacketWith(packet: ByteArray, signatureBytes: Int, comment: ByteArray): ByteArray {
    fun lengthAt(at: Int): Int {
        require(at >= 0 && at + 4 <= packet.size) { "OGG comment header is cut short" }
        val length = readLittleEndianInt(packet, at)
        require(length >= 0 && length <= packet.size - at - 4) { "OGG comment header is damaged" }
        return length
    }
    val countAt = signatureBytes + 4 + lengthAt(signatureBytes)
    require(countAt + 4 <= packet.size) { "OGG comment header is cut short" }
    val count = readLittleEndianInt(packet, countAt)
    require(count >= 0) { "OGG comment header is damaged" }
    var listEnd = countAt + 4
    repeat(count) { listEnd += 4 + lengthAt(listEnd) }

    val updated = ByteArray(packet.size + 4 + comment.size)
    packet.copyInto(updated, 0, 0, listEnd)
    writeLittleEndianInt(updated, countAt, count + 1)
    writeLittleEndianInt(updated, listEnd, comment.size)
    comment.copyInto(updated, listEnd + 4)
    packet.copyInto(updated, listEnd + 4 + comment.size, listEnd, packet.size)
    return updated
}

private fun writeOggHeaderPages(
    output: OutputStream,
    serial: ByteArray,
    firstSequence: Int,
    packets: List<ByteArray>,
): Int {
    val segments = ArrayList<Int>()
    val body = ByteArrayOutputStream()
    packets.forEach { packet ->
        body.write(packet)
        repeat(packet.size / OGG_MAX_SEGMENT_BYTES) { segments += OGG_MAX_SEGMENT_BYTES }
        segments += packet.size % OGG_MAX_SEGMENT_BYTES
    }
    val bodyBytes = body.toByteArray()

    var pages = 0
    var segment = 0
    var bodyAt = 0
    var continued = false
    while (segment < segments.size) {
        val onPage = segments.subList(segment, min(segment + OGG_MAX_SEGMENTS, segments.size))
        val pageBodyBytes = onPage.sum()
        val page = ByteArray(OGG_PAGE_HEADER_BYTES + onPage.size + pageBodyBytes)
        OGG_CAPTURE_PATTERN.copyInto(page)
        page[OGG_FLAGS_AT] = (if (continued) OGG_FLAG_CONTINUED else 0).toByte()
        // A page where no packet ends has no position of its own, and Ogg spells that as all ones.
        val endsPacket = onPage.any { it < OGG_MAX_SEGMENT_BYTES }
        if (!endsPacket) page.fill(0xFF.toByte(), OGG_GRANULE_AT, OGG_GRANULE_AT + 8)
        serial.copyInto(page, OGG_SERIAL_AT)
        writeLittleEndianInt(page, OGG_SEQUENCE_AT, firstSequence + pages)
        page[OGG_SEGMENT_COUNT_AT] = onPage.size.toByte()
        onPage.forEachIndexed { index, length -> page[OGG_PAGE_HEADER_BYTES + index] = length.toByte() }
        bodyBytes.copyInto(page, OGG_PAGE_HEADER_BYTES + onPage.size, bodyAt, bodyAt + pageBodyBytes)
        writeOggChecksum(page)
        output.write(page)

        continued = onPage.last() == OGG_MAX_SEGMENT_BYTES
        segment += onPage.size
        bodyAt += pageBodyBytes
        pages += 1
    }
    return pages
}

private fun readOggPage(input: DataInputStream): ByteArray? {
    val first = input.read()
    if (first < 0) return null
    val header = ByteArray(OGG_PAGE_HEADER_BYTES)
    header[0] = first.toByte()
    input.readFully(header, 1, header.size - 1)
    require(header.copyOf(OGG_CAPTURE_PATTERN.size).contentEquals(OGG_CAPTURE_PATTERN)) { "Not an OGG page" }
    val segments = header[OGG_SEGMENT_COUNT_AT].toInt() and 0xFF
    val table = ByteArray(segments)
    input.readFully(table)
    val page = ByteArray(header.size + segments + table.sumOf { it.toInt() and 0xFF })
    header.copyInto(page)
    table.copyInto(page, header.size)
    input.readFully(page, header.size + segments, page.size - header.size - segments)
    return page
}

private fun oggPageBody(page: ByteArray): ByteArray =
    page.copyOfRange(OGG_PAGE_HEADER_BYTES + (page[OGG_SEGMENT_COUNT_AT].toInt() and 0xFF), page.size)

private fun writeOggChecksum(page: ByteArray) {
    writeLittleEndianInt(page, OGG_CHECKSUM_AT, 0)
    var checksum = 0
    page.forEach { byte ->
        checksum = (checksum shl 8) xor OGG_CHECKSUM_TABLE[(checksum ushr 24) xor (byte.toInt() and 0xFF)]
    }
    writeLittleEndianInt(page, OGG_CHECKSUM_AT, checksum)
}

private fun readLittleEndianInt(bytes: ByteArray, at: Int): Int =
    (bytes[at].toInt() and 0xFF) or
        ((bytes[at + 1].toInt() and 0xFF) shl 8) or
        ((bytes[at + 2].toInt() and 0xFF) shl 16) or
        ((bytes[at + 3].toInt() and 0xFF) shl 24)

private fun writeLittleEndianInt(bytes: ByteArray, at: Int, value: Int) {
    bytes[at] = value.toByte()
    bytes[at + 1] = (value ushr 8).toByte()
    bytes[at + 2] = (value ushr 16).toByte()
    bytes[at + 3] = (value ushr 24).toByte()
}

private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
