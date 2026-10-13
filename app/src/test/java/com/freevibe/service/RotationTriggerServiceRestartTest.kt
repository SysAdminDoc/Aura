package com.freevibe.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.PreferencesManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RotationTriggerServiceRestartTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        prefs = PreferencesManager(context)
    }

    @Test
    fun `a sticky restart restores the unlock trigger from preferences`() {
        storeToggles(unlock = true, screenOff = false)
        val controller = Robolectric.buildService(RotationTriggerService::class.java).create()
        val service = controller.get()

        assertEquals(Service.START_STICKY, service.onStartCommand(null, 0, 1))

        awaitMain {
            context.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT))
            rotationQueued()
        }
        assertFalse(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun `a sticky restart with both triggers off stops once the toggles are read`() {
        storeToggles(unlock = false, screenOff = false)
        val controller = Robolectric.buildService(RotationTriggerService::class.java).create()
        val service = controller.get()

        service.onStartCommand(null, 0, 1)

        awaitMain { shadowOf(service).isStoppedBySelf }
        assertEquals(1, shadowOf(service).stopSelfId)
        controller.destroy()
    }

    @Test
    fun `a start that arrives during the restart read keeps its newer toggles`() {
        storeToggles(unlock = false, screenOff = false)
        val controller = Robolectric.buildService(RotationTriggerService::class.java).create()
        val service = controller.get()

        service.onStartCommand(null, 0, 1)
        service.onStartCommand(
            Intent(context, RotationTriggerService::class.java)
                .putExtra(RotationTriggerService.EXTRA_UNLOCK, true)
                .putExtra(RotationTriggerService.EXTRA_SCREEN_OFF, false),
            0,
            2,
        )

        // The stale read would stop the service with both toggles off; give it time to land.
        settleMain(millis = 500)
        assertFalse(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    private fun storeToggles(unlock: Boolean, screenOff: Boolean) = runBlocking {
        // DataStore outlives a single test in this process, so every test states both toggles.
        prefs.setRotateOnUnlock(unlock)
        prefs.setRotateOnScreenOff(screenOff)
    }

    private fun rotationQueued(): Boolean =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RotationTriggerService.WORK_NAME)
            .get()
            .isNotEmpty()

    private fun awaitMain(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            check(System.currentTimeMillis() < deadline) { "condition not met within ${timeoutMillis}ms" }
            Thread.sleep(10)
        }
    }

    private fun settleMain(millis: Long) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
