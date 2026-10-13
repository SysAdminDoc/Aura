package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RotationTriggerPolicyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun `a tap replaces trigger work and passive triggers coalesce`() {
        // A run that is already applying is protected by the worker's run lock, not by chaining the
        // tap behind it, which would fail the tap whenever that run failed.
        assertEquals(ExistingWorkPolicy.REPLACE, triggeredRotationPolicy(restartCountdown = true))
        assertEquals(ExistingWorkPolicy.KEEP, triggeredRotationPolicy(restartCountdown = false))
    }

    @Test
    fun `a second unlock coalesces and a tap takes the waiting unlock rotation's place`() {
        RotationTriggerService.enqueueRotation(context, restartCountdown = false)
        RotationTriggerService.enqueueRotation(context, restartCountdown = false)
        val passive = records().single()
        assertEquals(WorkInfo.State.ENQUEUED, passive.state)
        assertTrue("unlock rotations keep the battery floor", passive.constraints.requiresBatteryNotLow())
        assertEquals(
            "a saved wallpaper has to be able to apply offline",
            NetworkType.NOT_REQUIRED,
            passive.constraints.requiredNetworkType,
        )

        RotationTriggerService.enqueueRotation(context, restartCountdown = true)

        // Nothing holds a tap back any more, so this harness starts it at once and it may be over
        // already. What matters here is that it replaced the unlock rotation instead of being dropped.
        val tapped = records().single()
        assertNotEquals(passive.id, tapped.id)
        // Expedited work only accepts network and storage constraints; building it with more throws.
        assertFalse(tapped.constraints.requiresBatteryNotLow())
        assertEquals(NetworkType.NOT_REQUIRED, tapped.constraints.requiredNetworkType)
    }

    private fun records(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RotationTriggerService.WORK_NAME)
            .get()
}
