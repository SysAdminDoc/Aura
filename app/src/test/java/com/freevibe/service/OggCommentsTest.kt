package com.freevibe.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OggCommentsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class OggPage(
        val flags: Int,
        val granule: Long,
        val serial: Int,
        val sequence: Int,
        val lacing: List<Int>,
        val body: ByteArray,
    )

    private fun fixture(name: String): File {
        val file = folder.newFile()
        file.writeBytes(checkNotNull(javaClass.getResourceAsStream("/ogg/$name")).use { it.readBytes() })
        return file
    }

    // A bit at a time on purpose, so it shares nothing with the table in the code under test. FFmpeg's
    // own pages in the fixtures have to pass it, which is what makes it a fair judge of ours.
    private fun checksum(bytes: ByteArray): Int {
        var crc = 0
        bytes.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 24)
            repeat(8) { crc = if (crc < 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
        }
        return crc
    }

    private fun readPages(file: File): List<OggPage> {
        val bytes = file.readBytes()
        val pages = ArrayList<OggPage>()
        var at = 0
        while (at < bytes.size) {
            assertEquals("OggS", String(bytes, at, 4, Charsets.ISO_8859_1))
            val header = ByteBuffer.wrap(bytes, at, 27).order(ByteOrder.LITTLE_ENDIAN)
            val segments = bytes[at + 26].toInt() and 0xFF
            val lacing = (0 until segments).map { bytes[at + 27 + it].toInt() and 0xFF }
            val size = 27 + segments + lacing.sum()
            val page = bytes.copyOfRange(at, at + size)
            val stored = header.getInt(at + 22)
            page.fill(0, 22, 26)
            assertEquals("page ${pages.size} checksum", stored, checksum(page))
            pages += OggPage(
                flags = bytes[at + 5].toInt() and 0xFF,
                granule = header.getLong(at + 6),
                serial = header.getInt(at + 14),
                sequence = header.getInt(at + 18),
                lacing = lacing,
                body = page.copyOfRange(27 + segments, size),
            )
            at += size
        }
        return pages
    }

    private fun packets(pages: List<OggPage>): List<ByteArray> {
        val packets = ArrayList<ByteArray>()
        var partial = ByteArray(0)
        pages.forEach { page ->
            var at = 0
            page.lacing.forEach { length ->
                partial += page.body.copyOfRange(at, at + length)
                at += length
                if (length < 255) {
                    packets += partial
                    partial = ByteArray(0)
                }
            }
        }
        assertEquals("no packet left open", 0, partial.size)
        return packets
    }

    private fun comments(packet: ByteArray, signatureBytes: Int): List<String> {
        val reader = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        reader.position(signatureBytes)
        val vendorBytes = reader.int
        reader.position(reader.position() + vendorBytes)
        return List(reader.int) {
            val value = ByteArray(reader.int)
            reader.get(value)
            String(value, Charsets.UTF_8)
        }
    }

    private fun assertSameBytes(expected: List<ByteArray>, actual: List<ByteArray>) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertArrayEquals("packet $it", expected[it], actual[it]) }
    }

    private fun assertWellFormedStream(pages: List<OggPage>) {
        assertEquals(pages.indices.toList(), pages.map { it.sequence })
        assertEquals(setOf(pages[0].serial), pages.map { it.serial }.toSet())
    }

    @Test
    fun `Opus file gains the comment and keeps every other packet`() {
        val file = fixture("tone-opus.ogg")
        val before = readPages(file)
        val packetsBefore = packets(before)
        assertEquals("the fixture starts with no tags", emptyList<String>(), comments(packetsBefore[1], 8).filter { it.startsWith("ANDROID") })

        addOggComment(file, "ANDROID_LOOP=true")

        val after = readPages(file)
        val packetsAfter = packets(after)
        assertWellFormedStream(after)
        assertEquals(comments(packetsBefore[1], 8) + "ANDROID_LOOP=true", comments(packetsAfter[1], 8))
        assertArrayEquals("identification", packetsBefore[0], packetsAfter[0])
        assertSameBytes(packetsBefore.drop(2), packetsAfter.drop(2))
        assertEquals(before.last().granule, after.last().granule)
    }

    @Test
    fun `Vorbis file keeps its framing bit and its setup header`() {
        val file = fixture("tone-vorbis.ogg")
        val packetsBefore = packets(readPages(file))
        val commentsBefore = comments(packetsBefore[1], 7)

        addOggComment(file, "ANDROID_LOOP=true")

        val after = readPages(file)
        val packetsAfter = packets(after)
        assertWellFormedStream(after)
        assertEquals(commentsBefore + "ANDROID_LOOP=true", comments(packetsAfter[1], 7))
        assertEquals("framing bit", 1, packetsAfter[1].last().toInt())
        assertArrayEquals("setup", packetsBefore[2], packetsAfter[2])
        assertSameBytes(packetsBefore.drop(3), packetsAfter.drop(3))
    }

    @Test
    fun `audio packets come straight from the file with the headers left out`() {
        listOf("tone-vorbis.ogg" to 3, "tone-opus.ogg" to 2).forEach { (name, headers) ->
            val file = fixture(name)
            val seen = mutableListOf<ByteArray>()

            val finished = forEachOggAudioPacket(file.path) { packet, _ ->
                seen += packet
                true
            }

            assertTrue(name, finished)
            assertSameBytes(packets(readPages(file)).drop(headers), seen)
        }
    }

    @Test
    fun `walking audio packets stops when asked`() {
        val file = fixture("tone-vorbis.ogg")
        var seen = 0

        val finished = forEachOggAudioPacket(file.path) { _, _ ->
            seen += 1
            seen < 2
        }

        assertFalse(finished)
        assertEquals(2, seen)
    }

    @Test
    fun `comment too long for one page renumbers the audio pages`() {
        val file = fixture("tone-vorbis.ogg")
        val before = readPages(file)
        val packetsBefore = packets(before)
        val longComment = "ANDROID_LOOP=" + "x".repeat(70_000)

        addOggComment(file, longComment)

        val after = readPages(file)
        assertWellFormedStream(after)
        assertTrue("more pages than before", after.size > before.size)
        assertEquals("nothing ends on the first comment page", -1L, after[1].granule)
        assertTrue("the next page says it continues", after[2].flags and 1 != 0)
        val packetsAfter = packets(after)
        assertEquals(longComment, comments(packetsAfter[1], 7).last())
        assertSameBytes(packetsBefore.drop(2), packetsAfter.drop(2))
    }

    @Test
    fun `packet ending exactly on a segment boundary still closes`() {
        val file = fixture("tone-opus.ogg")
        val packetsBefore = packets(readPages(file))
        val grownBy = 255 - (packetsBefore[1].size + 4) % 255
        val comment = "x".repeat(grownBy)

        addOggComment(file, comment)

        val after = readPages(file)
        val packetsAfter = packets(after)
        assertEquals(0, packetsAfter[1].size % 255)
        assertEquals(0, after[1].lacing.last())
        assertEquals(comment, comments(packetsAfter[1], 8).last())
        assertSameBytes(packetsBefore.drop(2), packetsAfter.drop(2))
    }

    @Test
    fun `second comment lands after the first`() {
        val file = fixture("tone-opus.ogg")
        val before = comments(packets(readPages(file))[1], 8)

        addOggComment(file, "FIRST=1")
        addOggComment(file, "SECOND=2")

        assertEquals(before + listOf("FIRST=1", "SECOND=2"), comments(packets(readPages(file))[1], 8))
    }

    @Test
    fun `other files are refused and left alone`() {
        val notOgg = folder.newFile().apply { writeBytes(ByteArray(4_096) { 7 }) }
        val other = fixture("tone-opus.ogg")
        val otherBytes = other.readBytes()
        // Turns OpusHead into an unknown codec, leaving the page itself readable.
        otherBytes[28] = 'X'.code.toByte()
        other.writeBytes(otherBytes)

        assertThrows(IllegalArgumentException::class.java) { addOggComment(notOgg, "A=1") }
        assertThrows(IllegalArgumentException::class.java) { addOggComment(other, "A=1") }

        assertArrayEquals(otherBytes, other.readBytes())
        assertFalse(File(other.parentFile, "${other.name}.tagged").exists())
        assertFalse(File(notOgg.parentFile, "${notOgg.name}.tagged").exists())
    }
}
