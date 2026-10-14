package com.freevibe.service

import androidx.test.core.app.ApplicationProvider
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * The download the 24H pack uses for a picture at a web address. Every address is asked about
 * before anything is sent to it, a redirect's target included.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperApplierCheckedDownloadTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val http = ScriptedHttp()
    private val asked = mutableListOf<String>()
    private lateinit var applier: WallpaperApplier
    private lateinit var destination: File

    @Before
    fun setUp() {
        applier = WallpaperApplier(ApplicationProvider.getApplicationContext(), http.client(), mockk())
        destination = File(folder.root, "slot.img")
    }

    @Test
    fun `a redirect the check allows is followed to the picture`() = runBlocking {
        http.enqueue { it.answer(302, extraHeaders = mapOf("Location" to FILE_ADDRESS)) }
        http.enqueue { it.answer(200, PICTURE) }

        val result = applier.downloadCheckedToFile(START, destination) { address ->
            asked += address
            true
        }

        assertEquals(PICTURE.size.toLong(), result.getOrThrow())
        assertEquals("each address is asked about, in order", listOf(START, FILE_ADDRESS), asked)
        assertEquals(listOf(START, FILE_ADDRESS), requested())
        assertArrayEquals(PICTURE, destination.readBytes())
    }

    @Test
    fun `a redirect to a path is read against the address it came from`() = runBlocking {
        http.enqueue { it.answer(301, extraHeaders = mapOf("Location" to "/full/ab/abc.jpg")) }
        http.enqueue { it.answer(200, PICTURE) }

        applier.downloadCheckedToFile(START, destination) { address ->
            asked += address
            true
        }.getOrThrow()

        assertEquals(listOf(START, "https://wallhaven.cc/full/ab/abc.jpg"), asked)
        assertEquals(asked, requested())
    }

    @Test
    fun `a redirect the check refuses ends the download before the next host is contacted`() = runBlocking {
        http.enqueue { it.answer(302, extraHeaders = mapOf("Location" to TRACKER)) }
        // What a client that followed the redirect anyway would be served.
        http.enqueue { it.answer(200, PICTURE) }

        val result = applier.downloadCheckedToFile(START, destination) { address -> address == START }

        val error = result.exceptionOrNull()
        assertTrue("$error", error is WallpaperAddressRefusedException)
        assertEquals("tracker.example", (error as WallpaperAddressRefusedException).host)
        assertEquals(listOf(START), requested())
        assertFalse(destination.exists())
    }

    @Test
    fun `an address the check refuses is never contacted`() = runBlocking {
        http.enqueue { it.answer(200, PICTURE) }

        val result = applier.downloadCheckedToFile(TRACKER, destination) { false }

        assertTrue("${result.exceptionOrNull()}", result.exceptionOrNull() is WallpaperAddressRefusedException)
        assertEquals(emptyList<String>(), requested())
        assertFalse(destination.exists())
    }

    @Test
    fun `redirects that never end are given up on`() = runBlocking {
        repeat(20) { hop ->
            http.enqueue { it.answer(302, extraHeaders = mapOf("Location" to "$START?hop=$hop")) }
        }

        val result = applier.downloadCheckedToFile(START, destination) { true }

        val error = result.exceptionOrNull()
        assertTrue("$error", error is IOException && error !is WallpaperAddressRefusedException)
        assertTrue("stopped after ${http.requests.size} requests", http.requests.size in 2..6)
        assertFalse(destination.exists())
    }

    @Test
    fun `a failed answer is a failure and leaves no file`() = runBlocking {
        http.enqueue { it.answer(503) }

        val result = applier.downloadCheckedToFile(START, destination) { true }

        assertTrue("${result.exceptionOrNull()}", result.exceptionOrNull() is IOException)
        assertFalse(destination.exists())
        assertFalse(File(folder.root, "slot.img.tmp").exists())
    }

    @Test
    fun `the checked download never lets the client follow a redirect by itself`() {
        // The scripted server answers ahead of the client's own redirect handling, so the tests
        // above pass either way. On a real connection the client would follow a redirect before
        // the check was asked, unless this is off.
        assertFalse(applier.redirectCheckedClient.followRedirects)
        assertFalse(applier.redirectCheckedClient.followSslRedirects)
        assertEquals(
            "it is the app's client in every other way",
            http.client().interceptors.size,
            applier.redirectCheckedClient.interceptors.size,
        )
    }

    private fun requested(): List<String> = http.requests.map { it.url.toString() }

    private companion object {
        const val START = "https://wallhaven.cc/w/abc"
        const val FILE_ADDRESS = "https://w.wallhaven.cc/full/ab/abc.jpg"
        const val TRACKER = "https://tracker.example/pixel.jpg"
        val PICTURE = ByteArray(2048) { (it % 251).toByte() }
    }
}
