package com.freevibe.service

import android.content.Context
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.model.ContentType
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Request
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

/** Drives downloads over scripted HTTP into a fake MediaStore, across failures and restarts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadManagerResumeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private val http = ScriptedHttp()
    private val png: ByteArray = Base64.getDecoder().decode(ONE_PIXEL_PNG)
    private val stagingDir get() = File(context.cacheDir, "downloads")
    private lateinit var media: DownloadManagerActiveTest.FakeMediaProvider
    private lateinit var ledger: DownloadResumeLedger
    private lateinit var manager: DownloadManager

    @Before
    fun setUp() {
        stagingDir.deleteRecursively()
        media = Robolectric.setupContentProvider(DownloadManagerActiveTest.FakeMediaProvider::class.java, "media")
        coEvery { downloadDao.findMatching(any(), any(), any()) } returns emptyList()
        ledger = DownloadResumeLedger(context, Moshi.Builder().build())
        manager = newManager(ledger)
    }

    /** A manager whose start-up restore has finished, so it can't land in the middle of a test. */
    private fun newManager(ledger: DownloadResumeLedger): DownloadManager {
        val created = DownloadManager(
            context = context,
            okHttpClient = http.client(),
            downloadDao = downloadDao,
            downloadTrash = mockk(relaxed = true),
            mediaCopyStore = mockk(relaxed = true),
            resumeLedger = ledger,
        )
        runBlocking { created.startupRestore.join() }
        return created
    }

    /** A manager with nothing in memory, the way a new process starts. Only the staging directory carries over. */
    private fun restartedManager() = newManager(DownloadResumeLedger(context, Moshi.Builder().build()))

    /** The whole file is promised but the connection gives out after [KEPT] bytes. */
    private fun dropPartway(body: ByteArray = png) {
        http.enqueue { it.answer(200, body.copyOf(KEPT), STRONG_ETAG, declaredLength = body.size.toLong()) }
    }

    private fun Request.rest(body: ByteArray = png) = answer(
        206,
        body.copyOfRange(KEPT, body.size),
        mapOf("Content-Range" to "bytes $KEPT-${body.size - 1}/${body.size}", "ETag" to ETAG),
    )

    @Test
    fun `an interrupted download carries on from its bytes and saves a single copy`() = runBlocking {
        dropPartway()

        val first = manager.downloadWallpaper("w1", URL, "w1.png")

        assertTrue(first.isFailure)
        assertEquals(context.getString(R.string.download_failed_unknown), manager.activeDownloads.value.getValue(ID).error)
        assertEquals(KEPT.toLong(), ledger.find(ID)!!.bytesReceived)
        assertEquals("nothing is published until the whole file is here", 0, media.rows.size)

        http.enqueue { it.rest() }
        val retried = manager.retryDownload(ID)

        assertTrue(retried.isSuccess)
        assertEquals("bytes=$KEPT-", http.requests.last().header("Range"))
        assertEquals(ETAG, http.requests.last().header("If-Range"))
        assertEquals(1, media.rows.size)
        assertEquals(retried.getOrThrow(), media.rows.keys.single())
        assertArrayEquals(png, media.rows.values.single().readBytes())
        assertTrue(manager.activeDownloads.value.getValue(ID).isComplete)
        assertNull("published bytes aren't staged any longer", ledger.find(ID))
        assertEquals(emptyList<File>(), stagingDir.listFiles().orEmpty().toList())
        coVerify(exactly = 1) { downloadDao.insert(any()) }
    }

    @Test
    fun `a file that already arrived isn't fetched again when saving it failed`() = runBlocking {
        media.failPublishes = 1
        http.enqueue { it.answer(200, png, STRONG_ETAG) }

        assertTrue(manager.downloadWallpaper("w1", URL, "w1.png").isFailure)
        assertEquals(context.getString(R.string.download_failed_storage), manager.activeDownloads.value.getValue(ID).error)
        assertEquals("the half-written row was removed", 0, media.rows.size)
        assertTrue(ledger.find(ID)!!.isComplete)

        val retried = manager.retryDownload(ID)

        assertTrue(retried.isSuccess)
        assertEquals("the retry needed no request", 1, http.requests.size)
        assertEquals(1, media.rows.size)
        assertArrayEquals(png, media.rows.values.single().readBytes())
        assertNull(ledger.find(ID))
        coVerify(exactly = 1) { downloadDao.insert(any()) }
    }

    @Test
    fun `a resumed file is checked as a whole and bytes that aren't media are let go`() = runBlocking {
        val page = "<html><body>This is not a picture at all, however it is sent.</body></html>".toByteArray()
        dropPartway(page)
        assertTrue(manager.downloadWallpaper("w1", URL, "w1.png").isFailure)
        http.enqueue { it.rest(page) }

        val retried = manager.retryDownload(ID)

        assertTrue(retried.isFailure)
        assertEquals(context.getString(R.string.download_failed_not_media), manager.activeDownloads.value.getValue(ID).error)
        assertEquals(0, media.rows.size)
        assertNull(ledger.find(ID))
        assertFalse(ledger.partialFile(ID).exists())
    }

    @Test
    fun `a new process shows the unfinished download as a failure that Retry finishes`() = runBlocking {
        dropPartway()
        assertTrue(
            manager.downloadWallpaper("w1", URL, "w 1.png", source = "WALLHAVEN", provenanceUrl = PAGE_URL).isFailure,
        )

        val restarted = restartedManager()

        val card = restarted.activeDownloads.value.getValue(ID)
        assertEquals(context.getString(R.string.download_failed_interrupted), card.error)
        assertFalse(card.isComplete)
        assertEquals("w_1.png", card.fileName)
        assertEquals(KEPT.toLong(), card.downloadedBytes)
        assertEquals(png.size.toLong(), card.totalBytes)

        http.enqueue { it.rest() }
        val retried = restarted.retryDownload(ID)

        assertTrue(retried.isSuccess)
        assertEquals("bytes=$KEPT-", http.requests.last().header("Range"))
        assertEquals(1, media.rows.size)
        assertArrayEquals(png, media.rows.values.single().readBytes())
        assertTrue(restarted.activeDownloads.value.getValue(ID).isComplete)
        coVerify(exactly = 1) {
            downloadDao.insert(match { it.id == ID && it.source == "WALLHAVEN" && it.provenanceUrl == PAGE_URL })
        }
    }

    @Test
    fun `downloading again after a restart carries on from the staged bytes`() = runBlocking {
        dropPartway()
        assertTrue(manager.downloadWallpaper("w1", URL, "w1.png").isFailure)
        val restarted = restartedManager()
        http.enqueue { it.rest() }

        assertTrue(restarted.downloadWallpaper("w1", URL, "w1.png").isSuccess)

        assertEquals("bytes=$KEPT-", http.requests.last().header("Range"))
        assertEquals(1, media.rows.size)
        assertArrayEquals(png, media.rows.values.single().readBytes())
        assertNull(ledger.find(ID))
    }

    @Test
    fun `a restored sound is finished as the kind of sound it was`() = runBlocking {
        val ogg = "OggS".toByteArray() + ByteArray(60)
        dropPartway(ogg)
        assertTrue(manager.downloadSound("s1", URL, "s1.ogg", ContentType.ALARM).isFailure)
        assertEquals("the folder it was headed for is kept", "ALARM", ledger.find(SOUND_ID)!!.retry!!.soundType)

        val restarted = restartedManager()
        assertEquals(
            context.getString(R.string.download_failed_interrupted),
            restarted.activeDownloads.value.getValue(SOUND_ID).error,
        )
        http.enqueue { it.rest(ogg) }

        assertTrue(restarted.retryDownload(SOUND_ID).isSuccess)
        val saved = media.rows.keys.single().toString()
        assertTrue(saved, saved.startsWith(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()))
        assertArrayEquals(ogg, media.rows.values.single().readBytes())
    }

    @Test
    fun `a cancelled download keeps its bytes, and dismissing it lets them go`() = runBlocking {
        lateinit var download: Job
        http.enqueue { request ->
            request.answer(200, bodyInterruptedBy(png, chunkBytes = 16) { download.cancel() }, STRONG_ETAG)
        }
        download = launch { manager.downloadWallpaper("w1", URL, "w1.png") }
        download.join()

        assertTrue(download.isCancelled)
        val kept = ledger.find(ID)!!.bytesReceived.toInt()
        assertTrue("some bytes arrived, but not all of them: $kept", kept in 1 until png.size)
        assertEquals(kept.toLong(), ledger.partialFile(ID).length())
        assertEquals(
            "the card says the download stopped instead of looking like it's still running",
            context.getString(R.string.download_failed_interrupted),
            manager.activeDownloads.value.getValue(ID).error,
        )
        assertEquals(0, media.rows.size)

        manager.clearCompleted(ID)

        withTimeout(5_000) { while (ledger.find(ID) != null) delay(20) }
        assertEquals("no file is left that nothing will find again", emptyList<File>(), stagingDir.listFiles().orEmpty().toList())
        assertTrue(manager.retryDownload(ID).isFailure)
    }

    @Test
    fun `a new process clears out downloads and staging files that sat too long`() = runBlocking {
        dropPartway()
        assertTrue(manager.downloadWallpaper("w1", URL, "w1.png").isFailure)
        val longAgo = System.currentTimeMillis() - DOWNLOAD_RESUME_MAX_AGE_MS - 60_000L
        ledger.save(ledger.find(ID)!!.copy(updatedAtMs = longAgo))
        val leftover = File(stagingDir, "aura_download_1.tmp").apply {
            writeBytes(ByteArray(8))
            setLastModified(longAgo)
        }

        val restarted = restartedManager()

        assertTrue(ID !in restarted.activeDownloads.value)
        assertNull(ledger.find(ID))
        assertFalse(leftover.exists())
        assertEquals(emptyList<File>(), stagingDir.listFiles().orEmpty().toList())
    }

    private companion object {
        const val ID = "wallpaper:w1"
        const val SOUND_ID = "sound:s1"
        const val URL = "https://example.test/w1.png"
        const val PAGE_URL = "https://example.test/page/w1"
        const val KEPT = 30
        const val ETAG = "\"v1\""
        val STRONG_ETAG = mapOf("ETag" to ETAG)
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
