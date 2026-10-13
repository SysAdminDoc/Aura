package com.freevibe.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.freevibe.util.ActivityLaunchTest.LaunchFailingContext
import com.freevibe.util.tryStartActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetLaunchFeedbackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("widget_launch_feedback_test", Context.MODE_PRIVATE) }

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `a widget tap that can't open Aura shows on the widget`() {
        val opened = LaunchFailingContext(context, ActivityNotFoundException("no launcher"))
            .tryStartActivity(Intent(), "widget app")

        assertFalse(opened)
        assertTrue("the widget must refresh to show the failure", recordWidgetLaunch(prefs, opened, nowMs = 1_000L))
        assertTrue(widgetOpenFailedVisible(prefs.getLong("open_failed_at", 0L), nowMs = 2_000L))
    }

    @Test
    fun `the next open that works clears the failure line`() {
        recordWidgetLaunch(prefs, opened = false, nowMs = 1_000L)

        assertTrue(recordWidgetLaunch(prefs, opened = true, nowMs = 2_000L))
        assertFalse(widgetOpenFailedVisible(prefs.getLong("open_failed_at", 0L), nowMs = 2_000L))
        assertFalse("nothing to refresh when nothing was shown", recordWidgetLaunch(prefs, opened = true, nowMs = 3_000L))
    }

    @Test
    fun `the failure line fades after a while`() {
        assertTrue(widgetOpenFailedVisible(failedAtMs = 1_000L, nowMs = 1_000L + WIDGET_OPEN_FAILED_VISIBLE_MS))
        assertFalse(widgetOpenFailedVisible(failedAtMs = 1_000L, nowMs = 1_001L + WIDGET_OPEN_FAILED_VISIBLE_MS))
        assertFalse(widgetOpenFailedVisible(failedAtMs = 0L, nowMs = 1_000L))
    }
}
