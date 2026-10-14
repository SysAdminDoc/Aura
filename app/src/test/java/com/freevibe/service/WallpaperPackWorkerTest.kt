package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.repository.RotationExclusionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/** What the 15 minute pack worker does when the wallpaper for this time of day can't be applied. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperPackWorkerTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var restore: suspend () -> Unit
    private val applier = mockk<WallpaperApplier>()
    private val exclusions = mockk<RotationExclusionRepository>()
    private val receipts = mockk<BackgroundWorkReceiptStore>(relaxed = true)
    private var networkState = RotationNetworkState(connected = true, metered = false)

    /** What the worker handed the checked download to ask about each address, once one has run. */
    private var mayFetch: ((String) -> Boolean)? = null

    /** A picture for every time of day, so whatever the clock says there is one to apply. */
    private val pack = WallpaperPack(
        id = "main",
        name = "Mine",
        slots = Daypart.entries.map { daypart ->
            DaypartSlot(daypart, "content://picked/${daypart.name.lowercase()}", label = "${daypart.name}.jpg")
        },
    )

    @Before
    fun setUp(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        prefs = PreferencesManager(context)
        // DataStore outlives a single test in this process, so put back whatever this class changes.
        val enabled = prefs.wallpaperPackEnabled.first()
        val json = prefs.wallpaperPackJson.first()
        val lastDaypart = prefs.wallpaperPackLastAppliedDaypart.first()
        val wifiOnly = prefs.autoWallpaperRequiresWiFiOnly.first()
        restore = {
            prefs.setWallpaperPackEnabled(enabled)
            prefs.setWallpaperPackJson(json)
            prefs.setWallpaperPackLastAppliedDaypart(lastDaypart)
            prefs.setAutoWallpaperRequiresWiFiOnly(wifiOnly)
        }
        prefs.setWallpaperPackEnabled(true)
        prefs.setWallpaperPackJson(serializePack(pack))
        prefs.setWallpaperPackLastAppliedDaypart("")
        prefs.setAutoWallpaperRequiresWiFiOnly(false)
        coEvery { exclusions.isExcluded(any()) } returns false
    }

    @After
    fun tearDown(): Unit = runBlocking {
        restore()
        stagedFile().delete()
    }

    @Test
    fun `a picture Aura can no longer open is tried once and not every 15 minutes`() = runBlocking {
        applyFailsWith(SecurityException("Permission Denial: opening provider"))

        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(ListenableWorker.Result.success(), runWorker())
        runWorker()

        verifyApplyAttempts(1)
        assertTrue(prefs.wallpaperPackLastAppliedDaypart.first() in Daypart.entries.map(::needsRepickMarker))
        verify(exactly = 1) {
            receipts.recordFailure(
                WallpaperPackWorker.WORK_NAME,
                "SecurityException",
                match { "Pick it again" in it },
            )
        }
        // A later run must not report success over the reason nothing changed.
        verify(exactly = 0) { receipts.recordSuccess(any()) }
    }

    @Test
    fun `picking the picture again lets the pack apply`() = runBlocking {
        applyFailsWith(SecurityException("Permission Denial: opening provider"))
        runWorker()

        // Saving the pack in the editor clears the marker.
        prefs.setWallpaperPackLastAppliedDaypart("")
        coEvery {
            applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        runWorker()

        verifyApplyAttempts(2)
        assertTrue(prefs.wallpaperPackLastAppliedDaypart.first() in Daypart.entries.map { it.name })
    }

    @Test
    fun `a picture swapped out while the run was opening it isn't reported as lost`() = runBlocking {
        // The editor replaced every picture after this run had read the pack, and the old pictures
        // gave their grants back. The save had already cleared the marker.
        val swapped = pack.copy(slots = pack.slots.map { it.copy(wallpaperUri = it.wallpaperUri + "-new") })
        coEvery { exclusions.isExcluded(any()) } coAnswers {
            prefs.setWallpaperPackJson(serializePack(swapped))
            false
        }
        applyFailsWith(SecurityException("Permission Denial: opening provider"))

        runWorker()

        assertEquals("the next run should apply the new picture", "", prefs.wallpaperPackLastAppliedDaypart.first())
        verify(exactly = 0) { receipts.recordFailure(any(), any(), any()) }
    }

    @Test
    fun `a failure that may pass is tried again on the next run`() = runBlocking {
        applyFailsWith(IOException("Download failed: 503"))

        runWorker()
        runWorker()

        verifyApplyAttempts(2)
        assertEquals("", prefs.wallpaperPackLastAppliedDaypart.first())
    }

    // -- a picture at a web address --

    @Test
    fun `a web picture waits for Wi-Fi when Wi-Fi only is on and the connection is metered`() = runBlocking {
        useWebPack(PROVIDER_PICTURE)
        downloadsAndApplies()
        prefs.setAutoWallpaperRequiresWiFiOnly(true)
        networkState = RotationNetworkState(connected = true, metered = true)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 0) { applier.downloadCheckedToFile(any(), any(), any()) }
        verifyApplyAttempts(0)
        verify(exactly = 1) { receipts.recordDeferred(WallpaperPackWorker.WORK_NAME, match { "Wi-Fi" in it }) }
        verify(exactly = 0) { receipts.recordFailure(any(), any(), any()) }
        assertEquals("the next run asks again", "", prefs.wallpaperPackLastAppliedDaypart.first())
    }

    @Test
    fun `a web picture is fetched on a metered connection when Wi-Fi only is off`() = runBlocking {
        useWebPack(PROVIDER_PICTURE)
        downloadsAndApplies()
        networkState = RotationNetworkState(connected = true, metered = true)

        runWorker()

        coVerify(exactly = 1) { applier.downloadCheckedToFile(PROVIDER_PICTURE, stagedFile(), any()) }
        // Applied from the file the checked download wrote, never from the address.
        coVerify(exactly = 1) {
            applier.applyByLocator(stagedFile().absolutePath, any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        verifyApplyAttempts(1)
        assertFalse("the staged file goes when the run ends", stagedFile().exists())
        assertTrue(prefs.wallpaperPackLastAppliedDaypart.first() in Daypart.entries.map { it.name })
    }

    @Test
    fun `a web picture waits when the phone is offline`() = runBlocking {
        useWebPack(PROVIDER_PICTURE)
        downloadsAndApplies()
        networkState = RotationNetworkState(connected = false, metered = false)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 0) { applier.downloadCheckedToFile(any(), any(), any()) }
        verifyApplyAttempts(0)
        verify(exactly = 1) { receipts.recordDeferred(WallpaperPackWorker.WORK_NAME, match { "No connection" in it }) }
        assertEquals("", prefs.wallpaperPackLastAppliedDaypart.first())
    }

    @Test
    fun `a picture on the phone applies with no connection and Wi-Fi only on`() = runBlocking {
        downloadsAndApplies()
        prefs.setAutoWallpaperRequiresWiFiOnly(true)
        networkState = RotationNetworkState(connected = false, metered = true)

        runWorker()

        verifyApplyAttempts(1)
        coVerify(exactly = 0) { applier.downloadCheckedToFile(any(), any(), any()) }
        verify(exactly = 0) { receipts.recordDeferred(any(), any()) }
        assertTrue(prefs.wallpaperPackLastAppliedDaypart.first() in Daypart.entries.map { it.name })
    }

    @Test
    fun `a web picture off Aura's sources is never fetched and waits for a new pick`() = runBlocking {
        useWebPack("https://tracker.example/pixel.jpg")
        downloadsAndApplies()

        assertEquals(ListenableWorker.Result.success(), runWorker())
        runWorker()

        coVerify(exactly = 0) { applier.downloadCheckedToFile(any(), any(), any()) }
        verifyApplyAttempts(0)
        assertTrue(prefs.wallpaperPackLastAppliedDaypart.first() in Daypart.entries.map(::needsRepickMarker))
        verify(exactly = 1) {
            receipts.recordFailure(
                WallpaperPackWorker.WORK_NAME,
                "WallpaperAddressRefused",
                match { "Pick it again" in it },
            )
        }
        verify(exactly = 0) { receipts.recordSuccess(any()) }
    }

    @Test
    fun `a redirect is followed only while it stays with the picture's provider`() = runBlocking {
        useWebPack(PROVIDER_PICTURE)
        downloadsAndApplies()

        runWorker()

        val check = requireNotNull(mayFetch) { "the checked download was never asked for" }
        assertTrue("the address itself", check(PROVIDER_PICTURE))
        assertTrue("another host of the same provider", check("https://wallhaven.cc/w/abc"))
        assertFalse("any other host", check("https://tracker.example/pixel.jpg"))
        assertFalse("another provider", check("https://images.pexels.com/photos/1/a.jpeg"))
        assertFalse("not https", check("http://w.wallhaven.cc/full/ab/wallhaven-night.jpg"))
    }

    @Test
    fun `a download that was refused applies nothing and is tried again`() = runBlocking {
        useWebPack(PROVIDER_PICTURE)
        downloadsAndApplies()
        coEvery { applier.downloadCheckedToFile(any(), any(), any()) } returns
            Result.failure(WallpaperAddressRefusedException("tracker.example"))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        verifyApplyAttempts(0)
        verify(exactly = 1) {
            receipts.recordFailure(
                WallpaperPackWorker.WORK_NAME,
                "WallpaperAddressRefusedException",
                match { "tracker.example" in it },
            )
        }
        assertEquals("", prefs.wallpaperPackLastAppliedDaypart.first())
    }

    /** The same web address for every time of day, so whatever the clock says it is the one to apply. */
    private suspend fun useWebPack(address: String) {
        prefs.setWallpaperPackJson(
            serializePack(pack.copy(slots = Daypart.entries.map { DaypartSlot(it, address, label = "${it.name}.jpg") })),
        )
    }

    /** A download that writes its file and an apply that works, so a test sees only what the worker asked for. */
    private fun downloadsAndApplies() {
        coEvery { applier.downloadCheckedToFile(any(), any(), any()) } coAnswers {
            mayFetch = thirdArg()
            secondArg<File>().writeBytes(ByteArray(64))
            Result.success(64L)
        }
        coEvery {
            applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
    }

    private fun stagedFile(): File = File(context.cacheDir, WallpaperPackWorker.STAGED_SLOT_FILE)

    private fun applyFailsWith(error: Exception) {
        coEvery {
            applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(error)
    }

    private fun verifyApplyAttempts(times: Int) = coVerify(exactly = times) {
        applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
    }

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<WallpaperPackWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = WallpaperPackWorker(
                        appContext,
                        workerParameters,
                        prefs = prefs,
                        wallpaperApplier = applier,
                        rotationExclusions = exclusions,
                        receiptStore = receipts,
                        network = RotationNetworkGate { networkState },
                    )
                },
            )
            .build()
            .doWork()

    private companion object {
        /** A web address the worker may fetch by itself: https, on a provider's own host. */
        const val PROVIDER_PICTURE = "https://w.wallhaven.cc/full/ab/wallhaven-night.jpg"
    }
}
