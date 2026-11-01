package com.freevibe.ui.screens.collections

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.freevibe.service.renderCollectionQr
import com.freevibe.ui.theme.FreeVibeTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens and closes the real QR dialog ten times and logs what `dumpsys gfxinfo` counted, with the
 * activity on the real Choreographer rather than a test frame clock. Frame times in a debug build
 * are a worst case, so the janky count goes to logcat under [TAG] and only the counts are asserted.
 */
@RunWith(AndroidJUnit4::class)
class CollectionQrDialogFrameInstrumentedTest {

    @Test
    fun tenOpensDrawTenCodes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        val showing = mutableStateOf(false)
        val built = AtomicInteger()
        val state = CollectionQrState(
            collectionName = "Frame check",
            shareLink = "aura://collection/import/0123456789abcdef0123456789abcdef",
            itemCount = 12,
        )
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    FreeVibeTheme(darkTheme = true, dynamicColor = false) {
                        if (showing.value) {
                            CollectionQrDialogHost(
                                state = state,
                                buildBitmap = { link ->
                                    renderCollectionQr(link).asImageBitmap().also { built.incrementAndGet() }
                                },
                                onCopyLink = {},
                                onDismiss = {},
                            )
                        }
                    }
                }
            }
            SystemClock.sleep(SETTLE_MS)
            shell("dumpsys gfxinfo $packageName reset")
            repeat(ROUNDS) {
                instrumentation.runOnMainSync { showing.value = true }
                SystemClock.sleep(OPEN_MS)
                instrumentation.runOnMainSync { showing.value = false }
                SystemClock.sleep(CLOSED_MS)
            }
            val report = shell("dumpsys gfxinfo $packageName")
            val totalFrames = readCount(report, "Total frames rendered")
            val jankyFrames = readCount(report, "Janky frames")
            val percentiles = report.lineSequence()
                .filter { it.contains("th percentile:") }
                .joinToString(" ") { it.trim() }
            Log.i(TAG, "rounds=$ROUNDS built=${built.get()} totalFrames=$totalFrames jankyFrames=$jankyFrames $percentiles")

            assertEquals(ROUNDS, built.get())
            assertTrue("gfxinfo counted only $totalFrames frames", totalFrames > ROUNDS)
        }
    }

    private fun shell(command: String): String {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
    }

    private fun readCount(report: String, label: String): Int =
        Regex("""$label: (\d+)""").find(report)?.groupValues?.get(1)?.toInt() ?: -1

    private companion object {
        const val TAG = "CollectionQrFrames"
        const val ROUNDS = 10
        const val SETTLE_MS = 1_500L
        const val OPEN_MS = 900L
        const val CLOSED_MS = 500L
    }
}
