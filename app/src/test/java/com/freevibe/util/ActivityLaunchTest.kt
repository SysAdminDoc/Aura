package com.freevibe.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.freevibe.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityLaunchTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a missing activity is reported instead of swallowed`() {
        val throwing = LaunchFailingContext(context, ActivityNotFoundException("no handler"))

        assertFalse(throwing.tryStartActivity(Intent(Intent.ACTION_VIEW), "test"))
    }

    @Test
    fun `a refused or blocked launch is reported instead of thrown`() {
        assertFalse(LaunchFailingContext(context, SecurityException("denied")).tryStartActivity(Intent(), "test"))
        assertFalse(LaunchFailingContext(context, IllegalStateException("blocked")).tryStartActivity(Intent(), "test"))
    }

    @Test
    fun `a launch that opens reports success`() {
        val opening = LaunchFailingContext(context, failure = null)

        assertTrue(opening.tryStartActivity(Intent(Intent.ACTION_VIEW), "test"))
        assertEquals(1, opening.started)
    }

    @Test
    fun `Aura opens even when the package has no launch intent`() {
        val intent = appLaunchIntent(context, launchIntent = null)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /** Stands in for a device where startActivity throws, which Robolectric never does by itself. */
    internal class LaunchFailingContext(base: Context, private val failure: RuntimeException?) : ContextWrapper(base) {
        var started = 0
            private set

        override fun startActivity(intent: Intent?) {
            failure?.let { throw it }
            started++
        }
    }
}
