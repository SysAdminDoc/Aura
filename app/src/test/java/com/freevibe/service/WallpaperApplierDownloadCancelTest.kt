package com.freevibe.service

import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A download belongs to the run that asked for it. When the run is stopped the transfer is closed
 * then, not whenever the server gets round to sending the rest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperApplierDownloadCancelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = StallingServer()
    private val client = OkHttpClient.Builder().addInterceptor(server).build()

    // Downloads run outside the test's own coroutine, so a test that fails isn't left waiting on one.
    private val downloads = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var applier: WallpaperApplier
    private lateinit var destination: File
    private lateinit var partial: File

    @Before
    fun setUp() {
        applier = WallpaperApplier(ApplicationProvider.getApplicationContext(), client, mockk())
        destination = File(folder.root, "next.img")
        partial = File(folder.root, "next.img.tmp")
    }

    @After
    fun tearDown(): Unit = runBlocking {
        server.letGo()
        // A download that outlived its test still has the file open. It ends before the folder goes.
        withTimeoutOrNull(STOP_LIMIT_MS) { downloads.coroutineContext[Job]?.cancelAndJoin() }
    }

    @Test
    fun `cancelling a download closes its transfer at once`(): Unit = runBlocking {
        server.stallAt = Stall.MID_BODY

        val call = stopWhileStalled { applier.downloadToFile(ADDRESS, destination) }

        assertTrue("the transfer is closed with the run", call.isCanceled())
        assertFalse(destination.exists())
        assertFalse("what had been written is cleared away", partial.exists())
    }

    @Test
    fun `cancelling a checked download closes its transfer at once`(): Unit = runBlocking {
        server.stallAt = Stall.MID_BODY

        val call = stopWhileStalled { applier.downloadCheckedToFile(ADDRESS, destination) { true } }

        assertTrue("the transfer is closed with the run", call.isCanceled())
        assertFalse(destination.exists())
        assertFalse("what had been written is cleared away", partial.exists())
    }

    @Test
    fun `cancelling before the server has answered ends the request`(): Unit = runBlocking {
        server.stallAt = Stall.BEFORE_ANSWER

        val call = stopWhileStalled { applier.downloadToFile(ADDRESS, destination) }

        assertTrue("the request is given up with the run", call.isCanceled())
        assertFalse(destination.exists())
        assertFalse(partial.exists())
    }

    @Test
    fun `a download that runs to its end leaves its call alone`(): Unit = runBlocking {
        val saved = applier.downloadToFile(ADDRESS, destination).getOrThrow()

        assertEquals(PICTURE.size.toLong(), saved)
        assertArrayEquals(PICTURE, destination.readBytes())
        // Ending the watch on a transfer that finished is not a cancellation of it.
        assertFalse(server.lastCall!!.isCanceled())
    }

    @Test
    fun `a cancelled transfer reports the cancellation, not the read it broke`(): Unit = runBlocking {
        val call = client.newCall(Request.Builder().url(ADDRESS).build())
        var closedInPlace = false
        var reported: Throwable? = null

        launch {
            reported = runCatching {
                cancelCallWithCoroutine<Unit>(call) {
                    this@launch.cancel()
                    // The call is cancelled by the time the cancel returns, not on some later turn.
                    closedInPlace = call.isCanceled()
                    throw IOException("Canceled")
                }
            }.exceptionOrNull()
        }.join()

        assertTrue("the call is cancelled as part of cancelling the run", closedInPlace)
        assertTrue("$reported", reported is CancellationException)
    }

    /** Starts [download], stops it once the server has stalled, and returns the call it was on. */
    private suspend fun stopWhileStalled(download: suspend () -> Result<Long>): Call {
        val running = downloads.launch { download() }
        val call = withTimeout(START_LIMIT_MS) { server.stalled.await() }

        running.cancel()
        val ended = withTimeoutOrNull(STOP_LIMIT_MS) { running.join() }

        assertNotNull("the download was still going $STOP_LIMIT_MS ms after its run was stopped", ended)
        return call
    }

    private enum class Stall { NOWHERE, BEFORE_ANSWER, MID_BODY }

    /**
     * Stands in for a server that goes quiet at one point. Like a socket, it fails the read it is
     * holding up as soon as the call is cancelled. Left alone it holds on for [STALL_LIMIT_MS].
     */
    private class StallingServer : Interceptor {
        @Volatile
        var stallAt = Stall.NOWHERE

        @Volatile
        var lastCall: Call? = null

        /** Completed with the call that is being held up, once it is. */
        val stalled = CompletableDeferred<Call>()

        @Volatile
        private var released = false

        fun letGo() {
            released = true
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val call = chain.call()
            lastCall = call
            return when (stallAt) {
                Stall.NOWHERE -> chain.request().answer(200, PICTURE)
                Stall.BEFORE_ANSWER -> {
                    holdUp(call)
                    chain.request().answer(200, PICTURE)
                }
                Stall.MID_BODY -> chain.request().answer(200, bodyThatStalls(call))
            }
        }

        /** Hands over the first part of the picture, then goes quiet. */
        private fun bodyThatStalls(call: Call): ResponseBody {
            var firstPartSent = false
            val source = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    if (!firstPartSent) {
                        firstPartSent = true
                        sink.write(PICTURE, 0, FIRST_PART_BYTES)
                        return FIRST_PART_BYTES.toLong()
                    }
                    holdUp(call)
                    return -1L
                }

                override fun timeout(): Timeout = Timeout.NONE

                override fun close() = Unit
            }
            return source.buffer().asResponseBody(null, -1L)
        }

        private fun holdUp(call: Call) {
            stalled.complete(call)
            val giveUpAt = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(STALL_LIMIT_MS)
            while (!released && System.nanoTime() < giveUpAt) {
                if (call.isCanceled()) throw IOException("Canceled")
                Thread.sleep(POLL_MS)
            }
        }
    }

    private companion object {
        const val ADDRESS = "https://w.wallhaven.cc/full/ab/next.jpg"
        const val FIRST_PART_BYTES = 1024
        const val POLL_MS = 5L
        const val START_LIMIT_MS = 10_000L
        const val STOP_LIMIT_MS = 5_000L
        const val STALL_LIMIT_MS = 60_000L
        val PICTURE = ByteArray(4096) { (it % 251).toByte() }
    }
}
