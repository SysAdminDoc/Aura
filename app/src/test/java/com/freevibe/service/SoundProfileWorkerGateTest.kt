package com.freevibe.service

import android.app.AppOpsManager
import android.content.Context
import android.net.Uri
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/** The 15 minute worker only stays scheduled while a profile can change a sound. */
@RunWith(RobolectricTestRunner::class)
class SoundProfileWorkerGateTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var restore: suspend () -> Unit
    private val systemSounds = mockk<SystemSoundWriter>()

    private val allDay = SoundProfile(
        id = "all-day",
        name = "All day",
        ringtoneUri = "content://media/internal/audio/media/11",
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
        val enabled = prefs.soundProfilesEnabled.first()
        val json = prefs.soundProfilesJson.first()
        val lastApplied = prefs.soundProfileLastAppliedId.first()
        val lastRingtone = prefs.lastAppliedRingtoneUri.first()
        val replaced = prefs.soundProfilePreviousSoundsJson.first()
        restore = {
            prefs.setSoundProfilesEnabled(enabled)
            prefs.setSoundProfilesJson(json)
            prefs.setSoundProfileLastAppliedId(lastApplied)
            prefs.setLastAppliedRingtoneUri(lastRingtone)
            prefs.setSoundProfilePreviousSoundsJson(replaced)
        }
        prefs.setSoundProfileLastAppliedId("")
        prefs.setSoundProfilePreviousSoundsJson("")
        coEvery { systemSounds.write(any(), any()) } returns Unit
        every { systemSounds.current(any()) } returns null
        allowSystemWrites(AppOpsManager.MODE_ALLOWED)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        allowSystemWrites(AppOpsManager.MODE_ERRORED)
        restore()
    }

    @Test
    fun `sync schedules the worker when it's needed and cancels it when it isn't`() {
        SoundProfileWorker.sync(context, needed = true)
        assertTrue(uniqueWork(SoundProfileWorker.WORK_NAME).isNotEmpty())

        enqueueWaiting()
        SoundProfileWorker.sync(context, needed = false)
        assertEquals(WorkInfo.State.CANCELLED, periodicState())
    }

    @Test
    fun `a run with the switch on and no profile cancels its own schedule and changes nothing`() = runBlocking {
        prefs.setSoundProfilesEnabled(true)
        prefs.setSoundProfilesJson("")
        enqueueWaiting()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(WorkInfo.State.CANCELLED, periodicState())
        coVerify(exactly = 0) { systemSounds.write(any(), any()) }
    }

    @Test
    fun `a run with only a soundless profile cancels its own schedule too`() = runBlocking {
        prefs.setSoundProfilesEnabled(true)
        prefs.setSoundProfilesJson(serializeProfiles(listOf(SoundProfile(id = "empty", name = "Empty"))))
        enqueueWaiting()

        runWorker()

        assertEquals(WorkInfo.State.CANCELLED, periodicState())
    }

    @Test
    fun `a run with a usable profile applies it and stays scheduled`() = runBlocking {
        prefs.setSoundProfilesEnabled(true)
        prefs.setSoundProfilesJson(serializeProfiles(listOf(allDay)))
        enqueueWaiting()

        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 1) { systemSounds.write(SoundShuffleTarget.RINGTONE, Uri.parse(allDay.ringtoneUri)) }
        assertEquals("all-day", prefs.soundProfileLastAppliedId.first())
        // Boot restoration reads this, so it must name the profile's sound and not an older one.
        assertEquals(allDay.ringtoneUri, prefs.lastAppliedRingtoneUri.first())
        assertEquals(WorkInfo.State.ENQUEUED, periodicState())
    }

    @Test
    fun `an edit can ask for one run now without touching the schedule`() {
        enqueueWaiting()

        SoundProfileWorker.runNow(context)

        assertEquals(WorkInfo.State.ENQUEUED, periodicState())
        assertTrue(uniqueWork("sound_profile_now").isNotEmpty())
    }

    /** Settings.System.canWrite reads the write-settings app op, which Robolectric lets a test set. */
    private fun allowSystemWrites(mode: Int) {
        shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_WRITE_SETTINGS, Process.myUid(), context.packageName, mode)
    }

    /**
     * Stands in for the real schedule with a first run an hour away. The test WorkManager starts
     * unconstrained work at once, and these tests are about whether the schedule survives a run.
     */
    private fun enqueueWaiting() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SoundProfileWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            PeriodicWorkRequestBuilder<SoundProfileWorker>(15L, TimeUnit.MINUTES)
                .setInitialDelay(1L, TimeUnit.HOURS)
                .build(),
        ).result.get()
    }

    private fun uniqueWork(name: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    private fun periodicState(): WorkInfo.State? = uniqueWork(SoundProfileWorker.WORK_NAME).lastOrNull()?.state

    private suspend fun runWorker(): ListenableWorker.Result =
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
                        receiptStore = mockk(relaxed = true),
                        systemSounds = systemSounds,
                    )
                },
            )
            .build()
            .doWork()
}
