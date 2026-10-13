package com.freevibe.service

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DownloadResumeLedgerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val directory get() = File(temp.root, "downloads")

    private fun ledger(maxAgeMs: Long = 1_000L, maxEntries: Int = 3) = DownloadResumeLedger(
        directory = directory,
        moshi = Moshi.Builder().build(),
        maxAgeMs = maxAgeMs,
        maxEntries = maxEntries,
    )

    /** Saves a record for [id] and writes [bytes] of staged data under it, the way a transfer does. */
    private fun DownloadResumeLedger.stage(id: String, bytes: Int = 10, updatedAtMs: Long = 0L): DownloadResumeRecord {
        val record = DownloadResumeRecord(
            id = id,
            url = "https://example.test/$id.png",
            tempPath = partialFile(id).absolutePath,
            bytesReceived = bytes.toLong(),
            expectedBytes = 100L,
            etag = "\"v1\"",
            lastModified = "Wed, 21 Oct 2015 07:28:00 GMT",
            serverDate = "Wed, 21 Oct 2015 07:30:00 GMT",
            updatedAtMs = updatedAtMs,
            retry = DownloadRetryRequest(
                kind = "WALLPAPER",
                contentId = id,
                fileName = "$id.png",
                source = "WALLHAVEN",
                provenanceUrl = "https://example.test/page/$id",
            ),
        )
        save(record)
        partialFile(id).writeBytes(ByteArray(bytes))
        return record
    }

    @Test
    fun `a new process finds the same record and the same bytes`() {
        val saved = ledger().stage("wallpaper:w1", bytes = 40, updatedAtMs = 7L)

        val later = ledger()

        assertEquals(saved, later.find("wallpaper:w1"))
        assertEquals(40L, later.partialFile("wallpaper:w1").length())
        assertEquals(listOf(saved), later.all())
        assertNull(later.find("wallpaper:w2"))
    }

    @Test
    fun `saving again replaces the record in place`() {
        val ledger = ledger()
        val first = ledger.stage("wallpaper:w1", bytes = 10)

        ledger.save(first.copy(bytesReceived = 60L, updatedAtMs = 9L))

        assertEquals(60L, ledger.find("wallpaper:w1")?.bytesReceived)
        assertEquals("only the record and its bytes are left behind", 2, directory.listFiles()!!.size)
    }

    @Test
    fun `discarding a download removes its bytes and its record`() {
        val ledger = ledger()
        ledger.stage("wallpaper:w1")
        ledger.stage("sound:s1")

        ledger.discard("wallpaper:w1")

        assertNull(ledger.find("wallpaper:w1"))
        assertFalse(ledger.partialFile("wallpaper:w1").exists())
        assertEquals(listOf("sound:s1"), ledger.all().map { it.id })
        assertEquals(2, directory.listFiles()!!.size)
    }

    @Test
    fun `a record that won't parse is dropped along with its bytes`() {
        val ledger = ledger()
        ledger.stage("wallpaper:w1")
        val recordFile = directory.listFiles()!!.single { it.name.endsWith(".json") }
        recordFile.writeText("{ not json")

        assertNull(ledger.find("wallpaper:w1"))

        assertFalse(ledger.partialFile("wallpaper:w1").exists())
        assertFalse(recordFile.exists())
    }

    @Test
    fun `a record pointing at some other file is never believed`() {
        val ledger = ledger()
        val outside = temp.newFile("keep-me.bin")
        val record = ledger.stage("wallpaper:w1")

        ledger.save(record.copy(tempPath = outside.absolutePath))

        assertNull(ledger.find("wallpaper:w1"))
        assertTrue("a stored path can't send a delete anywhere else", outside.exists())
    }

    @Test
    fun `a sweep removes downloads past the age limit and keeps the fresh ones`() {
        val ledger = ledger(maxAgeMs = 1_000L)
        ledger.stage("wallpaper:old", updatedAtMs = 0L)
        ledger.stage("wallpaper:fresh", updatedAtMs = 900L)

        val removed = ledger.sweep(nowMs = 1_000L)

        assertEquals(1, removed)
        assertEquals(listOf("wallpaper:fresh"), ledger.all().map { it.id })
        assertFalse(ledger.partialFile("wallpaper:old").exists())
        assertTrue(ledger.partialFile("wallpaper:fresh").exists())
    }

    @Test
    fun `a sweep keeps only the newest downloads once there are too many`() {
        val ledger = ledger(maxAgeMs = Long.MAX_VALUE, maxEntries = 2)
        ledger.stage("wallpaper:a", updatedAtMs = 1L)
        ledger.stage("wallpaper:b", updatedAtMs = 2L)
        ledger.stage("wallpaper:c", updatedAtMs = 3L)

        ledger.sweep(nowMs = 10L)

        assertEquals(listOf("wallpaper:c", "wallpaper:b"), ledger.all().map { it.id })
        assertFalse(ledger.partialFile("wallpaper:a").exists())
    }

    @Test
    fun `a sweep never touches a download that is being written`() {
        val ledger = ledger(maxAgeMs = 1_000L, maxEntries = 1)
        ledger.stage("wallpaper:busy", updatedAtMs = 0L)
        ledger.stage("wallpaper:idle", updatedAtMs = 5_000L)

        ledger.sweep(nowMs = 5_000L, keep = setOf("wallpaper:busy"))

        assertEquals("the one being written already fills the limit", listOf("wallpaper:busy"), ledger.all().map { it.id })
        assertTrue(ledger.partialFile("wallpaper:busy").exists())
    }

    @Test
    fun `a sweep removes bytes nothing can find and old staging leftovers`() {
        // Ages are whole seconds apart, so a file system that rounds timestamps can't change the outcome.
        val ledger = ledger(maxAgeMs = 10_000L)
        ledger.stage("wallpaper:kept", updatedAtMs = 60_000L)
        // Bytes whose record is gone, and a record whose bytes are gone.
        ledger.stage("wallpaper:orphan", updatedAtMs = 60_000L)
        ledger.stage("wallpaper:hollow", updatedAtMs = 60_000L)
        val recordFor = { id: String ->
            File(ledger.partialFile(id).path.removeSuffix(".part") + ".json")
        }
        assertTrue(recordFor("wallpaper:orphan").delete())
        assertTrue(ledger.partialFile("wallpaper:hollow").delete())
        // What a killed process used to leave behind, next to a copy that is still in use.
        val abandoned = File(directory, "aura_download_1.tmp").apply { writeBytes(ByteArray(4)); setLastModified(20_000L) }
        val inUse = File(directory, "aura_download_2.tmp").apply { writeBytes(ByteArray(4)); setLastModified(56_000L) }

        ledger.sweep(nowMs = 60_000L)

        assertEquals(listOf("wallpaper:kept"), ledger.all().map { it.id })
        assertFalse(ledger.partialFile("wallpaper:orphan").exists())
        assertFalse(recordFor("wallpaper:hollow").exists())
        assertFalse(abandoned.exists())
        assertTrue(inUse.exists())
        assertEquals(3, directory.listFiles()!!.size)
    }

    @Test
    fun `a sweep of a directory that was never created does nothing`() {
        assertEquals(0, ledger().sweep(nowMs = 5_000L))
        assertEquals(emptyList<DownloadResumeRecord>(), ledger().all())
    }
}
