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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
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
        restore = {
            prefs.setWallpaperPackEnabled(enabled)
            prefs.setWallpaperPackJson(json)
            prefs.setWallpaperPackLastAppliedDaypart(lastDaypart)
        }
        prefs.setWallpaperPackEnabled(true)
        prefs.setWallpaperPackJson(serializePack(pack))
        prefs.setWallpaperPackLastAppliedDaypart("")
        coEvery { exclusions.isExcluded(any()) } returns false
    }

    @After
    fun tearDown(): Unit = runBlocking { restore() }

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
    fun `a failure that may pass is tried again on the next run`() = runBlocking {
        applyFailsWith(IOException("Download failed: 503"))

        runWorker()
        runWorker()

        verifyApplyAttempts(2)
        assertEquals("", prefs.wallpaperPackLastAppliedDaypart.first())
    }

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
                    )
                },
            )
            .build()
            .doWork()
}
