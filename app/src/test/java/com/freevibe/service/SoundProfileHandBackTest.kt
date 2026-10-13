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
import com.freevibe.ui.screens.settings.SettingsMediaDelegate
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** A profile borrows the default sounds for its window. When the window ends they go back. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundProfileHandBackTest {

    private val stock = "content://media/internal/audio/media/42"
    private val stockChime = "content://media/internal/audio/media/43"
    private val quiet = "content://media/external/audio/media/900"
    private val quietChime = "content://media/external/audio/media/901"
    private val pickedByHand = "content://media/external/audio/media/902"

    private val night = SoundProfile(id = "night", name = "Night", ringtoneUri = quiet)

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var store: FakeSoundStore
    private lateinit var restore: suspend () -> Unit

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
        val lastAppliedId = prefs.soundProfileLastAppliedId.first()
        val replaced = prefs.soundProfilePreviousSoundsJson.first()
        val lastApplied = SoundShuffleTarget.entries
            .associateWith { prefs.lastAppliedSoundUri(it.contentType).first() }
        val originals = SoundShuffleTarget.entries
            .associateWith { prefs.systemSoundOriginal(it.contentType).first() }
        restore = {
            prefs.setSoundProfilesEnabled(enabled)
            prefs.setSoundProfilesJson(json)
            prefs.setSoundProfileLastAppliedId(lastAppliedId)
            prefs.setSoundProfilePreviousSoundsJson(replaced)
            SoundShuffleTarget.entries.forEach {
                prefs.setLastAppliedSoundUri(it.contentType, lastApplied.getValue(it))
                prefs.setSystemSoundOriginal(it.contentType, originals.getValue(it))
            }
        }
        prefs.setSoundProfilesEnabled(true)
        prefs.setSoundProfilesJson("")
        prefs.setSoundProfileLastAppliedId("")
        prefs.setSoundProfilePreviousSoundsJson("")
        SoundShuffleTarget.entries.forEach {
            prefs.setLastAppliedSoundUri(it.contentType, "")
            prefs.setSystemSoundOriginal(it.contentType, "")
        }
        store = FakeSoundStore(
            mutableMapOf<SoundShuffleTarget, String?>(
                SoundShuffleTarget.RINGTONE to stock,
                SoundShuffleTarget.NOTIFICATION to stockChime,
            ),
        )
        allowSystemWrites(AppOpsManager.MODE_ALLOWED)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        allowSystemWrites(AppOpsManager.MODE_ERRORED)
        restore()
    }

    @Test
    fun `when the window ends the ringtone goes back to the one the profile replaced`() = runBlocking {
        useProfiles(night)
        runWorker()
        assertEquals(quiet, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals(quiet, prefs.lastAppliedRingtoneUri.first())
        // Written to preferences, so it is still known after the app has been closed.
        assertEquals(
            stock,
            parsePreProfileSounds(prefs.soundProfilePreviousSoundsJson.first())[SoundShuffleTarget.RINGTONE]?.previous,
        )

        useProfiles(offNow(night))
        runWorker()

        assertEquals(stock, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals(
            "the profile never touched the notification sound",
            stockChime,
            store.current(SoundShuffleTarget.NOTIFICATION),
        )
        assertEquals("a reboot must not bring the profile's sound back", "", prefs.lastAppliedRingtoneUri.first())
        assertEquals("", prefs.soundProfileLastAppliedId.first())
        assertEquals("", prefs.soundProfilePreviousSoundsJson.first())
    }

    @Test
    fun `a sound Aura had applied before the profile is the one that comes back`() = runBlocking {
        store.sounds[SoundShuffleTarget.RINGTONE] = pickedByHand
        prefs.setLastAppliedRingtoneUri(pickedByHand)
        useProfiles(night)
        runWorker()

        useProfiles(offNow(night))
        runWorker()

        assertEquals(pickedByHand, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals(pickedByHand, prefs.lastAppliedRingtoneUri.first())
    }

    @Test
    fun `a sound changed by hand during the window stays and the others still go back`() = runBlocking {
        val both = night.copy(notificationUri = quietChime)
        useProfiles(both)
        runWorker()
        // Picked in the phone's own sound settings while the profile was on.
        store.sounds[SoundShuffleTarget.RINGTONE] = pickedByHand

        useProfiles(offNow(both))
        runWorker()

        assertEquals(pickedByHand, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals(stockChime, store.current(SoundShuffleTarget.NOTIFICATION))
        assertEquals(
            "Aura must stop treating the profile's ringtone as its own, or a reboot would undo the user's pick",
            "",
            prefs.lastAppliedRingtoneUri.first(),
        )
    }

    @Test
    fun `switching profiles off gives the sounds back and switching on again applies the profile`() = runBlocking {
        useProfiles(night)
        enqueueWaiting()
        runWorker()

        prefs.setSoundProfilesEnabled(false)
        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(stock, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals(WorkInfo.State.CANCELLED, periodicState())

        prefs.setSoundProfilesEnabled(true)
        runWorker()

        assertEquals(quiet, store.current(SoundShuffleTarget.RINGTONE))
    }

    @Test
    fun `deleting the last profile gives the sounds back`() = runBlocking {
        useProfiles(night)
        runWorker()

        prefs.setSoundProfilesJson("")
        runWorker()

        assertEquals(stock, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals("", prefs.soundProfilePreviousSoundsJson.first())
    }

    @Test
    fun `a profile that leaves the ringtone alone gives back the one an earlier profile set`() = runBlocking {
        useProfiles(night)
        runWorker()

        val chimeOnly = SoundProfile(id = "work", name = "Work", notificationUri = quietChime)
        useProfiles(chimeOnly)
        runWorker()

        assertEquals(stock, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals(quietChime, store.current(SoundShuffleTarget.NOTIFICATION))

        useProfiles(offNow(chimeOnly))
        runWorker()

        assertEquals(stockChime, store.current(SoundShuffleTarget.NOTIFICATION))
    }

    @Test
    fun `two profiles in a row still go back to the sound from before the first`() = runBlocking {
        useProfiles(night)
        runWorker()

        val evening = SoundProfile(id = "evening", name = "Evening", ringtoneUri = pickedByHand)
        useProfiles(evening)
        runWorker()
        assertEquals(pickedByHand, store.current(SoundShuffleTarget.RINGTONE))

        useProfiles(offNow(evening))
        runWorker()

        assertEquals(stock, store.current(SoundShuffleTarget.RINGTONE))
    }

    @Test
    fun `a ringtone that was silent before the profile is silent again after it`() = runBlocking {
        store.sounds[SoundShuffleTarget.RINGTONE] = null
        useProfiles(night)
        runWorker()
        assertEquals(quiet, store.current(SoundShuffleTarget.RINGTONE))

        useProfiles(offNow(night))
        runWorker()

        assertNull(store.current(SoundShuffleTarget.RINGTONE))
    }

    @Test
    fun `stopping the worker asks for one last run so the sounds can go back`() {
        enqueueWaiting()

        SoundProfileWorker.sync(context, needed = false)

        assertEquals(WorkInfo.State.CANCELLED, periodicState())
        assertTrue(uniqueWork(RUN_NOW_WORK_NAME).isNotEmpty())
    }

    @Test
    fun `turning the switch off in Settings stops the schedule and asks for the last run`() = runBlocking {
        useProfiles(night)
        enqueueWaiting()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        try {
            SettingsMediaDelegate(context, prefs, scope).setSoundProfilesEnabled(false).join()

            assertEquals(WorkInfo.State.CANCELLED, periodicState())
            assertTrue(uniqueWork(RUN_NOW_WORK_NAME).isNotEmpty())
        } finally {
            scope.cancel()
        }
    }

    private suspend fun useProfiles(vararg profiles: SoundProfile) =
        prefs.setSoundProfilesJson(serializeProfiles(profiles.toList()))

    /** The same profile with a window that starts two hours from now, so it isn't on. */
    private fun offNow(profile: SoundProfile): SoundProfile {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return profile.copy(startHour = (hour + 2) % 24, endHour = (hour + 3) % 24)
    }

    /** Settings.System.canWrite reads the write-settings app op, which Robolectric lets a test set. */
    private fun allowSystemWrites(mode: Int) {
        shadowOf(context.getSystemService(AppOpsManager::class.java))
            .setMode(AppOpsManager.OPSTR_WRITE_SETTINGS, Process.myUid(), context.packageName, mode)
    }

    /** Stands in for the real schedule with a first run an hour away, so a run doesn't start by itself. */
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

    /** Each run gets a new writer over the same phone sounds, the way a new process would. */
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
                        systemSounds = SystemSoundWriter(prefs, store),
                    )
                },
            )
            .build()
            .doWork()

    private class FakeSoundStore(val sounds: MutableMap<SoundShuffleTarget, String?>) : SystemSoundStore {
        override fun current(target: SoundShuffleTarget): String? = sounds[target]

        override fun set(target: SoundShuffleTarget, uri: String?) {
            sounds[target] = uri
        }

        override fun isReadable(uri: String): Boolean = true
    }

    private companion object {
        const val RUN_NOW_WORK_NAME = "sound_profile_now"
    }
}
