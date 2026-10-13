package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * WorkManager-integration harness for wallpaper rotation reliability — the failure class that
 * dominates comparable OSS changers (changer stalls after reboot, metered background fetches).
 * Verifies through a real WorkManager that (a) the unique periodic work re-arms idempotently
 * (mirroring the boot-time reconcile), (b) it carries no network constraint, so a wallpaper saved
 * ahead of time can apply offline, and (c) the Wi-Fi-only preference puts an UNMETERED constraint
 * on the catch-up one-shot, so a metered network cannot satisfy the fetch.
 */
@RunWith(RobolectricTestRunner::class)
class AutoWallpaperWorkerSchedulingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private fun uniqueWork(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AutoWallpaperWorker.WORK_NAME)
            .get()

    private fun waitingCatchUp(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AutoWallpaperWorker.CATCH_UP_WORK_NAME)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }

    private fun queueCatchUp(requiresWiFiOnly: Boolean) = AutoWallpaperWorker.enqueueCatchUp(
        context = context,
        receiptWorkName = AutoWallpaperWorker.WORK_NAME,
        triggeredRotation = false,
        requiresCharging = false,
        requiresWiFiOnly = requiresWiFiOnly,
        requiresIdle = false,
    )

    @Test
    fun `the periodic rotation waits on the battery floor and on no network`() {
        AutoWallpaperWorker.scheduleWithConstraints(
            context = context,
            requiresCharging = false,
            requiresIdle = false,
        )

        val infos = uniqueWork()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos[0].state)
        assertEquals(NetworkType.NOT_REQUIRED, infos[0].constraints.requiredNetworkType)
        assertTrue(infos[0].constraints.requiresBatteryNotLow())
    }

    @Test
    fun `re-arming after reboot keeps a single periodic work`() {
        AutoWallpaperWorker.scheduleWithConstraints(
            context = context,
            requiresCharging = false,
            requiresIdle = false,
        )
        assertEquals(1, uniqueWork().size)

        // Simulate the boot-time reconcile re-arming the same unique work.
        AutoWallpaperWorker.scheduleWithConstraints(
            context = context,
            requiresCharging = false,
            requiresIdle = false,
        )

        val infos = uniqueWork()
        assertEquals("re-arm stays idempotent", 1, infos.size)
        assertEquals(NetworkType.NOT_REQUIRED, infos[0].constraints.requiredNetworkType)
    }

    @Test
    fun `the idle option schedules instead of failing to build`() {
        // WorkManager rejects idle-mode work that sets backoff criteria, so that work keeps the default.
        AutoWallpaperWorker.scheduleWithConstraints(
            context = context,
            requiresCharging = false,
            requiresIdle = true,
        )

        assertEquals(1, enqueuedWork().size)
    }

    @Test
    fun `work an older build scheduled loses its network constraint and keeps its countdown`() = runBlocking {
        // Stands in for what a build before this one left in WorkManager's database.
        val old = PeriodicWorkRequestBuilder<AutoWallpaperWorker>(90, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.UNMETERED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(AutoWallpaperWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, old)
        val before = enqueuedWork().single()
        assertEquals(NetworkType.UNMETERED, before.constraints.requiredNetworkType)

        AutoWallpaperWorker.relaxStoredNetworkConstraint(context)

        val after = enqueuedWork().single()
        assertEquals("UPDATE keeps the same work item and its period start", before.id, after.id)
        assertEquals(NetworkType.NOT_REQUIRED, after.constraints.requiredNetworkType)
        assertEquals(TimeUnit.MINUTES.toMillis(90), after.periodicityInfo?.repeatIntervalMillis)
        assertEquals("work that never waited before its first run still doesn't", 0L, after.initialDelayMillis)
        assertEquals("the next run stays where it was", before.nextScheduleTimeMillis, after.nextScheduleTimeMillis)
    }

    @Test
    fun `work that already has no network constraint is left alone`() = runBlocking {
        arm(restartCountdown = false)
        val before = enqueuedWork().single()

        AutoWallpaperWorker.relaxStoredNetworkConstraint(context)

        assertEquals(before.id, enqueuedWork().single().id)
        assertEquals(before.generation, enqueuedWork().single().generation)
    }

    @Test
    fun `nothing is scheduled for a rotation that was never on`() = runBlocking {
        AutoWallpaperWorker.relaxStoredNetworkConstraint(context)

        assertTrue(uniqueWork().isEmpty())
    }

    @Test
    fun `a catch-up waits for the network the Wi-Fi only choice allows`() {
        queueCatchUp(requiresWiFiOnly = true)
        val first = waitingCatchUp().single()
        assertEquals(NetworkType.UNMETERED, first.constraints.requiredNetworkType)
        assertTrue(first.constraints.requiresBatteryNotLow())

        // A newer hand-off replaces the waiting one, so the request carries the current setting.
        queueCatchUp(requiresWiFiOnly = false)

        val second = waitingCatchUp().single()
        assertNotEquals(first.id, second.id)
        assertEquals(NetworkType.CONNECTED, second.constraints.requiredNetworkType)
    }

    @Test
    fun `turning rotation off drops a waiting catch-up`() {
        arm(restartCountdown = false)
        queueCatchUp(requiresWiFiOnly = false)

        AutoWallpaperWorker.cancel(context)

        assertTrue(enqueuedWork().isEmpty())
        assertTrue(waitingCatchUp().isEmpty())
    }

    @Test
    fun `a manual apply restart drops a waiting catch-up and a plain re-arm keeps it`() {
        queueCatchUp(requiresWiFiOnly = false)

        arm(restartCountdown = false)
        assertEquals(1, waitingCatchUp().size)

        arm(restartCountdown = true)
        assertTrue(waitingCatchUp().isEmpty())
    }

    private fun enqueuedWork(): List<WorkInfo> = uniqueWork().filter { it.state == WorkInfo.State.ENQUEUED }

    private fun arm(restartCountdown: Boolean) = AutoWallpaperWorker.scheduleWithConstraints(
        context = context,
        requiresCharging = false,
        requiresIdle = false,
        restartCountdown = restartCountdown,
    )

    @Test
    fun `a plain re-arm keeps the running countdown`() {
        arm(restartCountdown = false)
        val first = enqueuedWork().single()
        assertEquals("turning rotation on rotates without waiting", 0L, first.initialDelayMillis)

        arm(restartCountdown = false)

        val rearmed = enqueuedWork().single()
        assertEquals("UPDATE keeps the same work item and its period start", first.id, rearmed.id)
        assertEquals(0L, rearmed.initialDelayMillis)
        assertEquals(first.nextScheduleTimeMillis, rearmed.nextScheduleTimeMillis)
    }

    @Test
    fun `a manual apply restart replaces the work so the interval starts over`() {
        arm(restartCountdown = false)
        val first = enqueuedWork().single()

        arm(restartCountdown = true)

        val restarted = enqueuedWork().single()
        assertNotEquals("restart must enqueue a fresh periodic work item", first.id, restarted.id)
        assertTrue(
            "the replaced item must not stay scheduled",
            uniqueWork().filter { it.id == first.id }.all { it.state == WorkInfo.State.CANCELLED },
        )
        // A fresh periodic item runs its first period at once unless it waits out the interval itself.
        assertEquals(DEFAULT_INTERVAL_MS, restarted.initialDelayMillis)
        assertTrue(
            "the next rotation is a whole interval after the change, not seconds after it",
            restarted.nextScheduleTimeMillis >= first.nextScheduleTimeMillis + DEFAULT_INTERVAL_MS,
        )
    }

    @Test
    fun `a settings change during a restarted countdown doesn't rotate at once`() = runBlocking {
        arm(restartCountdown = false)
        arm(restartCountdown = true)
        val restarted = enqueuedWork().single()

        // What Settings does for a charging, idle, Wi-Fi only or source change.
        AutoWallpaperWorker.schedule(context, intervalMinutes = 360)

        val rearmed = enqueuedWork().single()
        assertEquals("UPDATE keeps the restarted work item", restarted.id, rearmed.id)
        assertEquals(DEFAULT_INTERVAL_MS, rearmed.initialDelayMillis)
        assertEquals(
            "the countdown the restart began is still running",
            restarted.nextScheduleTimeMillis,
            rearmed.nextScheduleTimeMillis,
        )
    }

    @Test
    fun `a new interval chosen during a restarted countdown counts from the change`() = runBlocking {
        arm(restartCountdown = false)
        arm(restartCountdown = true)
        val restarted = enqueuedWork().single()

        AutoWallpaperWorker.schedule(context, intervalMinutes = 60)

        val rearmed = enqueuedWork().single()
        val hour = TimeUnit.MINUTES.toMillis(60)
        assertEquals(restarted.id, rearmed.id)
        assertEquals(hour, rearmed.periodicityInfo?.repeatIntervalMillis)
        assertEquals(hour, rearmed.initialDelayMillis)
        assertEquals(
            restarted.nextScheduleTimeMillis - DEFAULT_INTERVAL_MS + hour,
            rearmed.nextScheduleTimeMillis,
        )
    }

    @Test
    fun `only a restarted countdown waits before its first run`() {
        val interval = TimeUnit.MINUTES.toMillis(45)

        assertEquals(interval, rotationInitialDelayMillis(restartCountdown = true, interval, storedInitialDelayMillis = 0L))
        assertEquals(0L, rotationInitialDelayMillis(restartCountdown = false, interval, storedInitialDelayMillis = 0L))
        // A re-arm of restarted work keeps waiting, by the interval now in force.
        assertEquals(
            interval,
            rotationInitialDelayMillis(
                restartCountdown = false,
                intervalMillis = interval,
                storedInitialDelayMillis = TimeUnit.HOURS.toMillis(6),
            ),
        )
    }

    @Test
    fun `restart interval follows the scheduler when it owns the work`() {
        assertEquals(
            45L,
            rotationRestartIntervalMinutes(
                restartOnManual = true,
                schedulerEnabled = true,
                schedulerIntervalMinutes = 45L,
                autoWallpaperEnabled = true,
                autoWallpaperIntervalHours = 12L,
            ),
        )
    }

    @Test
    fun `restart interval converts the hourly rotation to minutes`() {
        assertEquals(
            720L,
            rotationRestartIntervalMinutes(
                restartOnManual = true,
                schedulerEnabled = false,
                schedulerIntervalMinutes = 45L,
                autoWallpaperEnabled = true,
                autoWallpaperIntervalHours = 12L,
            ),
        )
    }

    @Test
    fun `no restart when the option is off or nothing rotates`() {
        assertNull(
            rotationRestartIntervalMinutes(
                restartOnManual = false,
                schedulerEnabled = true,
                schedulerIntervalMinutes = 45L,
                autoWallpaperEnabled = true,
                autoWallpaperIntervalHours = 12L,
            ),
        )
        assertNull(
            rotationRestartIntervalMinutes(
                restartOnManual = true,
                schedulerEnabled = false,
                schedulerIntervalMinutes = 45L,
                autoWallpaperEnabled = false,
                autoWallpaperIntervalHours = 12L,
            ),
        )
    }

    private companion object {
        /** What [AutoWallpaperWorker.scheduleWithConstraints] uses when no interval is passed. */
        val DEFAULT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(360)
    }
}
