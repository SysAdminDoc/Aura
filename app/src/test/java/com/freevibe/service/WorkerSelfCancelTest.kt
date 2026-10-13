package com.freevibe.service

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.PreferencesManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * The pack and profile workers cancel their own schedule when nothing can apply. A save made at the
 * same moment asks for a schedule too, and the worker's cancel must not be the last word on it.
 * The pack also gets the start-up repair the profiles already had.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkerSelfCancelTest {

    private lateinit var context: Context
    private val prefs = mockk<PreferencesManager>()
    private val receipts = mockk<BackgroundWorkReceiptStore>(relaxed = true)

    private val savedPack = serializePack(
        WallpaperPack(id = "main", name = "Mine", slots = listOf(DaypartSlot(Daypart.NIGHT, "content://picked/night"))),
    )
    private val savedProfiles = serializeProfiles(
        listOf(SoundProfile(id = "night", name = "Night", ringtoneUri = "content://media/internal/audio/media/11")),
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        // Without the write-settings switch the profile worker has no sounds to give back first,
        // so these tests see the scheduling and nothing else.
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_WRITE_SETTINGS,
            Process.myUid(),
            context.packageName,
            AppOpsManager.MODE_ERRORED,
        )
    }

    // -- a save that lands while the worker is stopping --

    @Test
    fun `a pack saved while the worker is stopping keeps its schedule`() = runBlocking {
        // The run's first look finds nothing to apply. The save lands before the run's cancel does.
        every { prefs.wallpaperPackJson } returnsMany listOf(flowOf(""), flowOf(savedPack))
        every { prefs.wallpaperPackEnabled } returns flowOf(true)
        val before = enqueueWaiting<WallpaperPackWorker>(WallpaperPackWorker.WORK_NAME)

        packWorker().doWork()

        assertTrue(
            "the schedule the save asked for is put back",
            work(WallpaperPackWorker.WORK_NAME).any { it.id != before },
        )
    }

    @Test
    fun `a pack that is still empty after the cancel stays unscheduled`() = runBlocking {
        every { prefs.wallpaperPackJson } returns flowOf("")
        every { prefs.wallpaperPackEnabled } returns flowOf(true)
        val before = enqueueWaiting<WallpaperPackWorker>(WallpaperPackWorker.WORK_NAME)

        packWorker().doWork()

        assertEquals(
            listOf(before to WorkInfo.State.CANCELLED),
            work(WallpaperPackWorker.WORK_NAME).map { it.id to it.state },
        )
    }

    @Test
    fun `a profile saved while the worker is stopping keeps its schedule and its run`() = runBlocking {
        every { prefs.soundProfilesJson } returnsMany listOf(flowOf(""), flowOf(savedProfiles))
        every { prefs.soundProfilesEnabled } returns flowOf(true)
        val before = enqueueWaiting<SoundProfileWorker>(SoundProfileWorker.WORK_NAME)

        profileWorker().doWork()

        assertTrue(
            "the schedule the save asked for is put back",
            work(SoundProfileWorker.WORK_NAME).any { it.id != before },
        )
        assertTrue("and so is the run that makes the edit heard now", work(RUN_NOW_WORK_NAME).isNotEmpty())
    }

    @Test
    fun `profiles that still can't change a sound after the cancel stay unscheduled`() = runBlocking {
        every { prefs.soundProfilesJson } returns flowOf("")
        every { prefs.soundProfilesEnabled } returns flowOf(true)
        val before = enqueueWaiting<SoundProfileWorker>(SoundProfileWorker.WORK_NAME)

        profileWorker().doWork()

        assertEquals(
            listOf(before to WorkInfo.State.CANCELLED),
            work(SoundProfileWorker.WORK_NAME).map { it.id to it.state },
        )
        assertTrue(work(RUN_NOW_WORK_NAME).isEmpty())
    }

    // -- the start-up repair for the pack --

    @Test
    fun `starting the app puts back a pack schedule that went missing`() = runBlocking {
        every { prefs.wallpaperPackEnabled } returns flowOf(true)
        every { prefs.wallpaperPackJson } returns flowOf(savedPack)

        reconcileWallpaperPackSchedule(context, prefs)

        assertTrue(work(WallpaperPackWorker.WORK_NAME).isNotEmpty())
    }

    @Test
    fun `starting the app stops a pack schedule that has nothing to apply`() = runBlocking {
        every { prefs.wallpaperPackEnabled } returns flowOf(true)
        every { prefs.wallpaperPackJson } returns flowOf("")
        val before = enqueueWaiting<WallpaperPackWorker>(WallpaperPackWorker.WORK_NAME)

        reconcileWallpaperPackSchedule(context, prefs)

        assertEquals(
            listOf(before to WorkInfo.State.CANCELLED),
            work(WallpaperPackWorker.WORK_NAME).map { it.id to it.state },
        )
    }

    @Test
    fun `starting the app with the pack switch off touches nothing`() = runBlocking {
        every { prefs.wallpaperPackEnabled } returns flowOf(false)
        val before = enqueueWaiting<WallpaperPackWorker>(WallpaperPackWorker.WORK_NAME)

        reconcileWallpaperPackSchedule(context, prefs)

        assertEquals(
            listOf(before to WorkInfo.State.ENQUEUED),
            work(WallpaperPackWorker.WORK_NAME).map { it.id to it.state },
        )
    }

    @Test
    fun `the app runs the pack repair when it starts`() {
        val app = File("src/main/java/com/freevibe/FreeVibeApp.kt").readText()
        val onCreate = app.substringAfter("override fun onCreate()").substringBefore("\n    }")

        assertTrue(onCreate.contains("reconcileWallpaperPackWork()"))
        assertTrue(app.contains("reconcileWallpaperPackSchedule("))
    }

    private fun work(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    /** Stands in for the real schedule with a first run an hour away, so a run doesn't start by itself. */
    private inline fun <reified W : ListenableWorker> enqueueWaiting(name: String): UUID {
        val request = PeriodicWorkRequestBuilder<W>(15L, TimeUnit.MINUTES)
            .setInitialDelay(1L, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
            .result
            .get()
        return request.id
    }

    private fun packWorker(): WallpaperPackWorker =
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
                        wallpaperApplier = mockk(),
                        rotationExclusions = mockk(),
                        receiptStore = receipts,
                    )
                },
            )
            .build()

    private fun profileWorker(): SoundProfileWorker =
        TestListenableWorkerBuilder<SoundProfileWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = SoundProfileWorker(
                        appContext,
                        workerParameters,
                        prefs = prefs,
                        receiptStore = receipts,
                        systemSounds = mockk(),
                    )
                },
            )
            .build()

    private companion object {
        const val RUN_NOW_WORK_NAME = "sound_profile_now"
    }
}
