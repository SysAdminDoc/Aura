package com.freevibe.service

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** Drives the transport against scripted responses. No Room, no MediaStore, no Android. */
class ResumableDownloadTransportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val http = ScriptedHttp()
    private val ledger by lazy {
        DownloadResumeLedger(directory = File(temp.root, "downloads"), moshi = Moshi.Builder().build())
    }
    private val transport by lazy { ResumableDownloadTransport(http.client(), ledger) }

    private val file = ByteArray(FILE_BYTES) { (it % 251).toByte() }
    private val partial get() = ledger.partialFile(ID)

    private suspend fun fetch(url: String = URL, maxBytes: Long = MAX_BYTES): File = transport.fetch(ID, url, maxBytes)

    private fun failure(block: suspend () -> Unit): Throwable {
        val thrown = runCatching { runBlocking { block() } }.exceptionOrNull()
        return thrown ?: throw AssertionError("Expected the download to fail")
    }

    /** The whole file is promised but the connection gives out after [received] bytes. */
    private fun dropAfter(received: Int, headers: Map<String, String> = STRONG_ETAG) {
        http.enqueue { it.answer(200, file.copyOf(received), headers, declaredLength = FILE_BYTES.toLong()) }
        assertTrue(failure { fetch() } is IOException)
    }

    private fun Request.rest(from: Int, body: ByteArray = file) = answer(
        206,
        body.copyOfRange(from, body.size),
        mapOf("Content-Range" to "bytes $from-${body.size - 1}/${body.size}", "ETag" to ETAG),
    )

    @Test
    fun `a first download is fetched whole and remembers what the server said about it`() = runBlocking {
        http.enqueue {
            it.answer(200, file, mapOf("ETag" to ETAG, "Last-Modified" to MODIFIED, "Date" to SERVED))
        }

        val done = fetch()

        assertArrayEquals(file, done.readBytes())
        assertEquals(partial, done)
        assertNull("a first request asks for everything", http.requests.single().header("Range"))
        assertNull(http.requests.single().header("If-Range"))
        val record = ledger.find(ID)!!
        assertEquals(URL, record.url)
        assertEquals(partial.absolutePath, record.tempPath)
        assertEquals(FILE_BYTES.toLong(), record.bytesReceived)
        assertEquals(FILE_BYTES.toLong(), record.expectedBytes)
        assertEquals(ETAG, record.etag)
        assertEquals(MODIFIED, record.lastModified)
        assertTrue(record.isComplete)
    }

    @Test
    fun `a retry asks only for the missing bytes and a matching 206 finishes the file`() = runBlocking {
        dropAfter(KEPT)
        assertEquals(KEPT.toLong(), ledger.find(ID)!!.bytesReceived)
        assertEquals(KEPT.toLong(), partial.length())
        http.enqueue { it.rest(from = KEPT) }

        val done = fetch()

        assertArrayEquals(file, done.readBytes())
        val retry = http.requests.last()
        assertEquals("bytes=$KEPT-", retry.header("Range"))
        assertEquals(ETAG, retry.header("If-Range"))
        assertEquals("identity", retry.header("Accept-Encoding"))
        assertEquals(2, http.requests.size)
        assertTrue(ledger.find(ID)!!.isComplete)
    }

    @Test
    fun `a 200 on the retry replaces what was kept`() = runBlocking {
        dropAfter(KEPT)
        val changed = ByteArray(FILE_BYTES - 500) { 7 }
        http.enqueue { it.answer(200, changed, mapOf("ETag" to "\"v2\"")) }

        val done = fetch()

        assertArrayEquals("nothing of the old file is left under the new one", changed, done.readBytes())
        assertEquals("bytes=$KEPT-", http.requests.last().header("Range"))
        assertEquals("\"v2\"", ledger.find(ID)!!.etag)
        assertEquals(changed.size.toLong(), ledger.find(ID)!!.expectedBytes)
    }

    @Test
    fun `a failed precondition or an unsatisfiable range starts again from byte zero`() {
        listOf(412, 416).forEach { status ->
            runBlocking {
                ledger.discard(ID)
                http.requests.clear()
                dropAfter(KEPT)
                http.enqueue { it.answer(status) }
                http.enqueue { it.answer(200, file, STRONG_ETAG) }

                val done = fetch()

                assertArrayEquals("HTTP $status", file, done.readBytes())
                assertEquals(3, http.requests.size)
                assertEquals("bytes=$KEPT-", http.requests[1].header("Range"))
                assertNull("the restart is a plain request", http.requests[2].header("Range"))
                assertNull(http.requests[2].header("If-Range"))
            }
        }
    }

    @Test
    fun `a 206 that doesn't line up with the kept bytes is thrown away`() {
        val misaligned = listOf(
            "bytes ${KEPT + 1}-${FILE_BYTES - 1}/$FILE_BYTES",
            "bytes $KEPT-${FILE_BYTES - 1}/${FILE_BYTES + 1}",
            "bytes $KEPT-${FILE_BYTES - 2}/$FILE_BYTES",
            "bytes $KEPT-${FILE_BYTES - 1}/*",
        )
        misaligned.forEach { contentRange ->
            runBlocking {
                ledger.discard(ID)
                http.requests.clear()
                dropAfter(KEPT)
                http.enqueue {
                    it.answer(206, file.copyOfRange(KEPT, FILE_BYTES), mapOf("Content-Range" to contentRange))
                }
                http.enqueue { it.answer(200, file, STRONG_ETAG) }

                val done = fetch()

                assertArrayEquals(contentRange, file, done.readBytes())
                assertNull(contentRange, http.requests.last().header("Range"))
            }
        }
    }

    @Test
    fun `a 206 for a file with a different entity tag is thrown away`() = runBlocking {
        dropAfter(KEPT)
        val changed = ByteArray(FILE_BYTES) { 9 }
        http.enqueue {
            it.answer(
                206,
                changed.copyOfRange(KEPT, FILE_BYTES),
                mapOf("Content-Range" to "bytes $KEPT-${FILE_BYTES - 1}/$FILE_BYTES", "ETag" to "\"v2\""),
            )
        }
        http.enqueue { it.answer(200, changed, mapOf("ETag" to "\"v2\"")) }

        assertArrayEquals(changed, fetch().readBytes())
    }

    @Test
    fun `a weak validator is never used to resume`() = runBlocking {
        dropAfter(KEPT, headers = mapOf("ETag" to "W/\"v1\"", "Last-Modified" to MODIFIED, "Date" to SERVED))
        assertNull("bytes that can't be resumed aren't kept", ledger.find(ID))
        assertFalse(partial.exists())
        http.enqueue { it.answer(200, file) }

        assertArrayEquals(file, fetch().readBytes())
        assertNull(http.requests.last().header("Range"))
        assertNull(http.requests.last().header("If-Range"))
    }

    @Test
    fun `a date resumes a download only when the server sent no entity tag`() = runBlocking {
        dropAfter(KEPT, headers = mapOf("Last-Modified" to MODIFIED, "Date" to SERVED))
        http.enqueue {
            it.answer(
                206,
                file.copyOfRange(KEPT, FILE_BYTES),
                mapOf("Content-Range" to "bytes $KEPT-${FILE_BYTES - 1}/$FILE_BYTES", "Last-Modified" to MODIFIED),
            )
        }

        assertArrayEquals(file, fetch().readBytes())
        assertEquals(MODIFIED, http.requests.last().header("If-Range"))
    }

    @Test
    fun `only a strong validator is offered as If-Range`() {
        assertEquals(ETAG, strongIfRangeValidator(ETAG, null, null))
        assertEquals(ETAG, strongIfRangeValidator(" $ETAG ", MODIFIED, SERVED))
        assertNull(strongIfRangeValidator("W/\"v1\"", null, null))
        assertNull("a weak tag isn't rescued by a date", strongIfRangeValidator("W/\"v1\"", MODIFIED, SERVED))
        assertNull("a tag without quotes isn't a tag", strongIfRangeValidator("v1", MODIFIED, SERVED))
        assertEquals(MODIFIED, strongIfRangeValidator(null, MODIFIED, SERVED))
        assertEquals(MODIFIED, strongIfRangeValidator("", MODIFIED, "Wed, 21 Oct 2015 07:28:01 GMT"))
        assertNull("saved and served in the same second", strongIfRangeValidator(null, MODIFIED, MODIFIED))
        assertNull(strongIfRangeValidator(null, MODIFIED, null))
        assertNull(strongIfRangeValidator(null, "yesterday", SERVED))
        assertNull(strongIfRangeValidator(null, null, null))
    }

    @Test
    fun `Content-Range is read strictly`() {
        assertEquals(ContentRange(100, 199, 200), parseContentRange("bytes 100-199/200"))
        assertEquals(ContentRange(0, 0, 1), parseContentRange(" Bytes 0-0/1 "))
        assertNull(parseContentRange("bytes 100-199/*"))
        assertNull(parseContentRange("bytes */200"))
        assertNull(parseContentRange("bytes 150-100/200"))
        assertNull(parseContentRange("bytes 100-200/200"))
        assertNull(parseContentRange("items 0-1/2"))
        assertNull(parseContentRange(null))
    }

    @Test
    fun `the size cap counts the bytes that were already kept`() = runBlocking {
        dropAfter(KEPT)
        // The server claims a matching range, then sends more than the file has room for.
        val tooMuch = ByteArray(MAX_BYTES.toInt() - KEPT + 1)
        http.enqueue {
            it.answer(
                206,
                tooMuch,
                mapOf("Content-Range" to "bytes $KEPT-${FILE_BYTES - 1}/$FILE_BYTES", "ETag" to ETAG),
                declaredLength = -1L,
            )
        }

        val error = failure { fetch() }

        assertTrue(error.message, error.message.orEmpty().contains("size limit"))
        assertNull("an oversized file is never resumed", ledger.find(ID))
        assertFalse(partial.exists())
    }

    @Test
    fun `a file the server says is too big is refused before a byte is kept`() {
        http.enqueue { it.answer(200, file, STRONG_ETAG) }

        val error = failure { fetch(maxBytes = FILE_BYTES - 1L) }

        assertTrue(error.message, error.message.orEmpty().contains("size limit"))
        assertNull(ledger.find(ID))
        assertFalse(partial.exists())
    }

    @Test
    fun `a file whose every byte arrived is handed back without another request`() = runBlocking {
        http.enqueue { it.answer(200, file, STRONG_ETAG) }
        fetch()

        val again = fetch()

        assertArrayEquals(file, again.readBytes())
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `a server that never gives a length still ends with a complete file`() = runBlocking {
        http.enqueue { it.answer(200, file, STRONG_ETAG, declaredLength = -1L) }

        assertArrayEquals(file, fetch().readBytes())

        val record = ledger.find(ID)!!
        assertTrue(record.isComplete)
        assertNull("without a length there is nothing to line a range up with", record.etag)
    }

    @Test
    fun `bytes kept for one address are never built on for another`() = runBlocking {
        dropAfter(KEPT)
        val other = ByteArray(300) { 3 }
        http.enqueue { it.answer(200, other, STRONG_ETAG) }

        val done = fetch(url = "https://example.test/other.png")

        assertArrayEquals(other, done.readBytes())
        assertNull(http.requests.last().header("Range"))
    }

    @Test
    fun `a file that is longer than its last durable count is cut back before it grows`() = runBlocking {
        dropAfter(KEPT)
        // A killed process can leave bytes on disk that were never counted.
        partial.appendBytes(ByteArray(900) { 1 })
        http.enqueue { it.rest(from = KEPT) }

        val done = fetch()

        assertEquals("bytes=$KEPT-", http.requests.last().header("Range"))
        assertArrayEquals(file, done.readBytes())
    }

    @Test
    fun `a count ahead of the bytes on disk starts again`() = runBlocking {
        dropAfter(KEPT)
        partial.writeBytes(file.copyOf(KEPT - 1))
        http.enqueue { it.answer(200, file, STRONG_ETAG) }

        assertArrayEquals(file, fetch().readBytes())
        assertNull(http.requests.last().header("Range"))
    }

    @Test
    fun `a file that is gone from the server gives up its kept bytes`() {
        dropAfter(KEPT)
        http.enqueue { it.answer(404) }

        val error = failure { fetch() }

        assertEquals("Download failed: HTTP 404", error.message)
        assertNull(ledger.find(ID))
        assertFalse(partial.exists())
    }

    @Test
    fun `a server error on the retry keeps the bytes for next time`() = runBlocking {
        dropAfter(KEPT)
        http.enqueue { it.answer(503) }
        assertEquals("Download failed: HTTP 503", failure { fetch() }.message)
        assertEquals(KEPT.toLong(), ledger.find(ID)!!.bytesReceived)
        http.enqueue { it.rest(from = KEPT) }

        assertArrayEquals(file, fetch().readBytes())
    }

    @Test
    fun `a cancelled download keeps its bytes and the next attempt carries on from them`() = runBlocking {
        lateinit var attempt: Job
        http.enqueue { request ->
            request.answer(200, bodyInterruptedBy(file) { attempt.cancel() }, STRONG_ETAG)
        }
        attempt = launch { fetch() }
        attempt.join()
        assertTrue(attempt.isCancelled)

        val kept = ledger.find(ID)!!.bytesReceived.toInt()
        assertTrue("some bytes arrived, but not all of them: $kept", kept in 1 until FILE_BYTES)
        assertEquals(kept.toLong(), partial.length())
        http.enqueue { it.rest(from = kept) }

        assertArrayEquals(file, fetch().readBytes())
        assertEquals("bytes=$kept-", http.requests.last().header("Range"))
    }

    @Test
    fun `a cancelled download with nothing to resume on leaves no file behind`() = runBlocking {
        lateinit var attempt: Job
        http.enqueue { request ->
            request.answer(200, bodyInterruptedBy(file) { attempt.cancel() })
        }
        attempt = launch { fetch() }
        attempt.join()

        assertTrue(attempt.isCancelled)
        assertNull(ledger.find(ID))
        assertEquals(emptyList<File>(), ledger.partialFile(ID).parentFile?.listFiles().orEmpty().toList())
    }

    @Test
    fun `progress counts the kept bytes toward the whole file`() = runBlocking {
        dropAfter(KEPT)
        http.enqueue { it.rest(from = KEPT) }
        val seen = mutableListOf<Pair<Long, Long>>()

        transport.fetch(ID, URL, MAX_BYTES) { received, total -> seen += received to total }

        assertTrue(seen.isNotEmpty())
        assertTrue("every report is past what was kept", seen.all { it.first > KEPT && it.second == FILE_BYTES.toLong() })
        assertEquals(FILE_BYTES.toLong(), seen.last().first)
    }

    @Test
    fun `durable counts are written as the bytes arrive`() = runBlocking {
        val counting = ResumableDownloadTransport(http.client(), ledger, checkpointBytes = 4_096L)
        val counts = mutableListOf<Long>()
        http.enqueue { it.answer(200, file, STRONG_ETAG) }

        counting.fetch(ID, URL, MAX_BYTES) { _, _ -> counts += ledger.find(ID)!!.bytesReceived }

        assertTrue("the record moved before the transfer ended: $counts", counts.any { it in 1 until FILE_BYTES })
        assertEquals(counts.sorted(), counts)
    }

    private companion object {
        const val ID = "wallpaper:w1"
        const val URL = "https://example.test/w1.png"
        const val FILE_BYTES = 20_000
        const val KEPT = 6_000
        const val MAX_BYTES = 32_000L
        const val ETAG = "\"v1\""
        const val MODIFIED = "Wed, 21 Oct 2015 07:28:00 GMT"
        const val SERVED = "Wed, 21 Oct 2015 07:30:00 GMT"
        val STRONG_ETAG = mapOf("ETag" to ETAG)
    }
}
