package com.freevibe.service

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.data.local.DownloadDao
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
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
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLHandshakeException

/** Drives real downloads from a file locator into a fake MediaStore, so saved rows can be counted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadManagerActiveTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private lateinit var media: FakeMediaProvider
    private lateinit var manager: DownloadManager
    private lateinit var locator: String

    @Before
    fun setUp() {
        media = Robolectric.setupContentProvider(FakeMediaProvider::class.java, "media")
        coEvery { downloadDao.findMatching(any(), any(), any()) } returns emptyList()
        manager = DownloadManager(
            context = context,
            okHttpClient = OkHttpClient(),
            downloadDao = downloadDao,
            downloadTrash = mockk(relaxed = true),
            mediaCopyStore = mockk(relaxed = true),
            resumeLedger = DownloadResumeLedger(context, Moshi.Builder().build()),
        )
        val source = File(context.filesDir, "source_wallpaper.png")
        source.writeBytes(Base64.getDecoder().decode(ONE_PIXEL_PNG))
        locator = Uri.fromFile(source).toString()
    }

    @Test
    fun `a failed download shows its reason and Retry saves a single copy`() = runBlocking {
        media.failPublishes = 1

        val first = manager.downloadWallpaper("w1", locator, "w1.png")

        assertTrue(first.isFailure)
        val failed = manager.activeDownloads.value.getValue(ID)
        assertEquals(context.getString(R.string.download_failed_storage), failed.error)
        assertEquals("the snackbar gets the same reason", failed.error, first.exceptionOrNull()?.message)
        assertFalse(failed.isComplete)
        assertEquals("the half-written row was removed", 0, media.rows.size)

        val retried = manager.retryDownload(ID)

        assertTrue(retried.isSuccess)
        assertEquals(1, media.rows.size)
        assertEquals(retried.getOrThrow(), media.rows.keys.single())
        val done = manager.activeDownloads.value.getValue(ID)
        assertTrue(done.isComplete)
        assertNull(done.error)
        coVerify(exactly = 1) { downloadDao.insert(any()) }
    }

    @Test
    fun `a finished download leaves Active once history has it`() = runBlocking {
        manager.completedProgressVisibleMs = 50

        assertTrue(manager.downloadWallpaper("w1", locator, "w1.png").isSuccess)
        assertTrue(manager.activeDownloads.value.getValue(ID).isComplete)

        withTimeout(5_000) { manager.activeDownloads.first { ID !in it } }
        assertEquals(1, media.rows.size)
    }

    @Test
    fun `a dismissed failure can't be retried`() = runBlocking {
        media.failPublishes = 1
        assertTrue(manager.downloadWallpaper("w1", locator, "w1.png").isFailure)

        manager.clearCompleted(ID)
        val retried = manager.retryDownload(ID)

        assertTrue(retried.isFailure)
        assertTrue(ID !in manager.activeDownloads.value)
        assertEquals(0, media.rows.size)
    }

    @Test
    fun `the timer leaves a newer attempt on screen`() {
        val finished = DownloadProgress(ID, "w1.png", 1f, 10, 10, isComplete = true)
        val newer = DownloadProgress(ID, "w1.png", 0f, 0, 0)

        assertEquals(emptyMap<String, DownloadProgress>(), withoutFinishedProgress(mapOf(ID to finished), ID, finished))
        assertEquals(mapOf(ID to newer), withoutFinishedProgress(mapOf(ID to newer), ID, finished))
    }

    @Test
    fun `a failure always has a reason in the app's words and never echoes the exception`() {
        fun reason(error: Throwable) = downloadFailureReason(context, error)

        assertEquals(context.getString(R.string.download_failed_unknown), reason(IllegalStateException()))
        assertEquals(context.getString(R.string.download_failed_unknown), reason(IllegalStateException(" ")))
        assertEquals("The server answered with error 404.", reason(IllegalStateException("Download failed: HTTP 404")))
        assertEquals(
            context.getString(R.string.download_failed_offline),
            reason(UnknownHostException("Unable to resolve host \"cdn.example.com\"")),
        )
        assertEquals(
            context.getString(R.string.download_failed_timeout),
            reason(IOException("call failed", SocketTimeoutException("timeout"))),
        )
        assertEquals(context.getString(R.string.download_failed_secure), reason(SSLHandshakeException("bad cert")))
        assertEquals(
            context.getString(R.string.download_failed_too_large),
            reason(IllegalStateException("Download exceeds size limit (9 > 1)")),
        )
        assertEquals(
            context.getString(R.string.download_failed_not_media),
            reason(IOException("Wallpaper content type mismatch: expected image")),
        )
        assertEquals(
            context.getString(R.string.download_failed_storage),
            reason(IllegalStateException("Saved original did not match its source bytes")),
        )
        listOf(
            UnknownHostException("Unable to resolve host \"secret.example.com\""),
            IllegalStateException("GET https://secret.example.com/file?token=abc failed"),
        ).forEach { assertFalse(reason(it).contains("secret")) }
    }

    class FakeMediaProvider : ContentProvider() {
        val rows = ConcurrentHashMap<Uri, File>()
        @Volatile var failPublishes = 0
        private var nextId = 1L

        override fun onCreate() = true

        @Synchronized
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val row = ContentUris.withAppendedId(uri, nextId++)
            rows[row] = File.createTempFile("media_row_", ".bin", context!!.cacheDir)
            return row
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(rows.getValue(uri), ParcelFileDescriptor.parseMode(mode))

        @Synchronized
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (failPublishes > 0) {
                failPublishes--
                return 0
            }
            return if (rows.containsKey(uri)) 1 else 0
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            rows.remove(uri)?.let { it.delete(); 1 } ?: 0

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = null

        override fun getType(uri: Uri): String? = null
    }

    private companion object {
        const val ID = "wallpaper:w1"
        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    }
}
