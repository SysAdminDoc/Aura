package com.freevibe.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.util.UiText
import com.freevibe.util.englishContext
import com.freevibe.util.uiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A Chinese screen and an English support bundle, both built from the same diagnostics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh")
class DiagnosticsLanguageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun String.hasChinese() = any { it in '一'..'鿿' }

    private fun rotationHint(verdict: RotationHealthVerdict, exempt: Boolean? = true): UiText = checkNotNull(
        rotationHealthActionHint(verdict, exempt, backgroundBatteryGuidanceForManufacturer("samsung"), "IOException", null),
    )

    private val retryRow = BackgroundWorkStatusRow(
        label = "Auto wallpaper rotation",
        uniqueWorkName = "auto_wallpaper",
        workInfoStatus = "ENQUEUED=1",
        lastResult = "retry",
        lastErrorClass = "IllegalStateException",
    )

    private val network = BackgroundNetworkDiagnostics(activeNetworkMetered = false, restrictBackgroundStatus = "disabled")

    @Test
    fun `battery guidance follows the app language`() {
        for (maker in listOf("samsung", "google", "xiaomi", "oneplus", "huawei", "motorola")) {
            val guide = backgroundBatteryGuidanceForManufacturer(maker)
            assertTrue(maker, guide.summary.resolve(context).hasChinese())
            assertFalse(maker, guide.summary.resolve(context.englishContext()).hasChinese())
        }
    }

    @Test
    fun `background work advice nests its parts in one language`() {
        val hint = checkNotNull(
            backgroundWorkActionHint(retryRow, network, backgroundBatteryGuidanceForManufacturer("samsung")),
        )
        assertTrue(hint.resolve(context).hasChinese())
        assertFalse(hint.resolve(context).contains("Open Settings"))
        assertFalse(hint.resolve(context.englishContext()).hasChinese())
    }

    @Test
    fun `rotation advice follows the app language`() {
        for (verdict in RotationHealthVerdict.entries - RotationHealthVerdict.HEALTHY) {
            assertTrue("$verdict", rotationHint(verdict).resolve(context).hasChinese())
        }
        assertTrue(rotationHint(RotationHealthVerdict.THROTTLED, exempt = false).resolve(context).hasChinese())
        assertTrue(rotationHint(RotationHealthVerdict.FAILING).resolve(context).contains("IOException"))
    }

    @Test
    fun `battery impact summary reads as one language with its numbers in place`() {
        val summary = videoBatteryImpactSummary(
            requestedFps = 60,
            effectiveFps = 15,
            fpsOverlayEnabled = false,
            lowBatterySaverActive = true,
        ).resolve(context)
        assertTrue(summary.hasChinese())
        assertTrue(summary.contains("15"))
        assertFalse(summary.contains("%"))
    }

    @Test
    fun `support bundle stays in English while the screen is Chinese`() {
        val english = context.englishContext()
        val status = BackgroundWorkDiagnostics(
            network = network,
            rows = listOf(
                retryRow.copy(
                    actionHint = backgroundWorkActionHint(retryRow, network, backgroundBatteryGuidanceForManufacturer("samsung")),
                ),
            ),
            batteryGuidance = backgroundBatteryGuidanceForManufacturer("samsung"),
        )
        val bundle = CrashDiagnosticsText.formatLiveBackgroundWorkReceipts(status) { it.resolve(english) }
        assertFalse(bundle, bundle.hasChinese())
        assertTrue(bundle, bundle.contains("Background usage limits"))
    }

    @Test
    fun `work labels resolve in both languages`() {
        val id = R.string.settings_diag_work_auto_wallpaper
        assertTrue(context.getString(id).hasChinese())
        assertEquals("Auto wallpaper rotation", context.englishContext().getString(id))
        assertEquals(context.getString(id), uiText(id).resolve(context))
    }
}
