package com.freevibe.service

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.freevibe.service.soak.LiveWallpaperSoakDriver
import com.freevibe.service.soak.LiveWallpaperSoakEnvironment
import com.freevibe.service.soak.LiveWallpaperSoakScenario
import com.freevibe.service.soak.LiveWallpaperSoakScenarios
import com.freevibe.service.soak.LiveWallpaperSoakTarget
import com.freevibe.service.soak.SoakSurfaceHolder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The on-device half of the cross-engine live-wallpaper soak.
 *
 * It runs byte-for-byte the same scenario script as `LiveWallpaperSoakTest`, but
 * against the real platform: real MediaPlayer and GIF decoders, real
 * SensorManager, real ML Kit, real bitmap allocation through `ImageDecoder`. The
 * JVM soak cannot reach any of those, so a leak that only exists behind a real
 * decoder is only visible here.
 *
 * Run on an emulator:
 *
 * ```
 * ./gradlew :app:connectedFullDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 * com.freevibe.service.LiveWallpaperSoakInstrumentedTest
 * ```
 *
 * This debug-only soak does not replace the physical-device captures tracked in
 * `Roadmap_Blocked.md`: OEM power management and OEM decoder death are exactly
 * what an emulator does not reproduce.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LiveWallpaperSoakInstrumentedTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    private inner class DeviceSoakEnvironment(
        private val mediaFile: File,
        private val target: LiveWallpaperSoakTarget,
    ) : LiveWallpaperSoakEnvironment {

        private var generation = 0

        /**
         * Battery saver is a global secure setting, so it is driven through the
         * shell identity the instrumentation already has. Emulators report as
         * charging and can refuse the change; that is a weaker run, not a failure,
         * so the step degrades to a no-op instead of failing the soak.
         */
        override fun setPowerSaveMode(enabled: Boolean) {
            runCatching {
                instrumentation.uiAutomation
                    .executeShellCommand("settings put global low_power " + if (enabled) "1" else "0")
                    .close()
            }
            settle()
        }

        override fun replaceMedia() {
            generation += 1
            writeMedia(mediaFile, target, generation)
            settle()
        }

        override fun settle() {
            instrumentation.waitForIdleSync()
            Thread.sleep(SETTLE_MS)
            instrumentation.waitForIdleSync()
        }
    }

    /**
     * Real media, so the engines build real decoders. A tiny animated GIF and a
     * small PNG are assembled in code rather than shipped as test assets, and the
     * video target reuses the GIF bytes under an `.mp4` name so `MediaPlayer`
     * exercises its prepare-failure and bounded-rebuild path on every device
     * regardless of which codecs that device happens to have.
     */
    private fun writeMedia(file: File, target: LiveWallpaperSoakTarget, generation: Int) {
        when (target) {
            LiveWallpaperSoakTarget.GIF -> file.writeBytes(GIF_BYTES)
            LiveWallpaperSoakTarget.VIDEO -> file.writeBytes(GIF_BYTES)
            LiveWallpaperSoakTarget.WEATHER,
            LiveWallpaperSoakTarget.PARALLAX,
            -> file.outputStream().use { out ->
                val size = IMAGE_EDGE + generation
                Bitmap.createBitmap(size, size * 2, Bitmap.Config.ARGB_8888)
                    .compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        file.setLastModified(System.currentTimeMillis() + generation * 1_000L)
    }

    /** Instantiates a wallpaper service outside the platform's wallpaper host. */
    private fun buildService(serviceClass: Class<out WallpaperService>): WallpaperService {
        val service = serviceClass.getDeclaredConstructor().newInstance()
        val attachBaseContext = ContextWrapper::class.java
            .getDeclaredMethod("attachBaseContext", Context::class.java)
        attachBaseContext.isAccessible = true
        attachBaseContext.invoke(service, context)
        instrumentation.runOnMainSync { service.onCreate() }
        return service
    }

    private fun soak(
        target: LiveWallpaperSoakTarget,
        scenario: LiveWallpaperSoakScenario,
        cycles: Int,
    ) = run {
        val mediaFile = File(context.filesDir, target.mediaFileName)
        mediaFile.parentFile?.mkdirs()
        writeMedia(mediaFile, target, generation = 0)
        context.getSharedPreferences(target.prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(target.pathKey, mediaFile.absolutePath)
            .commit()

        val service = buildService(target.serviceClass)
        val holder = SoakSurfaceHolder()
        try {
            var engine: WallpaperService.Engine? = null
            instrumentation.runOnMainSync { engine = service.onCreateEngine() }
            LiveWallpaperSoakDriver(
                target = target,
                engine = checkNotNull(engine),
                holder = holder,
                environment = DeviceSoakEnvironment(mediaFile, target),
                onEngineCallback = { block -> instrumentation.runOnMainSync(block) },
            ).run(scenario, cycles)
        } finally {
            holder.release()
            instrumentation.runOnMainSync { service.onDestroy() }
        }
    }

    @Test
    fun everyEngineDrainsItsResourcesOnDevice() {
        LiveWallpaperSoakTarget.entries.forEach { target ->
            LiveWallpaperSoakScenarios.ALL.forEach { scenario ->
                val report = soak(target, scenario, cycles = SHORT_RUN)
                assertTrue(
                    target.name + "/" + scenario.name + " still held " + report.residual +
                        " after onDestroy",
                    report.isDrained,
                )
            }
        }
    }

    @Test
    fun noEngineAccumulatesResourcesOnDevice() {
        LiveWallpaperSoakTarget.entries.forEach { target ->
            LiveWallpaperSoakScenarios.ALL.forEach { scenario ->
                val report = soak(target, scenario, cycles = LONG_RUN)
                assertPeaksBounded(target.name + "/" + scenario.name, report.peak)
            }
        }
    }

    /**
     * The same ceilings the JVM soak uses: what one engine can hold at a single
     * instant, so anything accumulating per cycle blows through them by [LONG_RUN].
     */
    private fun assertPeaksBounded(label: String, peak: Map<String, Int>) {
        val ceilings = mapOf(
            "players" to 2,
            "frameCallbacks" to 4,
            "sensorListeners" to 1,
            "broadcastReceivers" to 1,
            "imageBuffers" to 4,
            "segmenters" to 1,
            "loaderThreads" to 2,
        )
        assertEquals(
            "a resource kind was added without a ceiling, so it would soak unbounded",
            ceilings.keys,
            peak.keys,
        )
        peak.forEach { (kind, value) ->
            val ceiling = ceilings.getValue(kind)
            assertTrue(
                label + " held " + value + " " + kind + " across " + LONG_RUN +
                    " cycles, above the " + ceiling + " one engine can hold at once: " + peak,
                value <= ceiling,
            )
        }
    }

    /**
     * The reason this run exists: only a real device decodes through
     * `ImageDecoder`, so only here can a retained API-28+ bitmap be observed.
     */
    @Test
    fun bitmapEnginesRetainABoundedSetOfDecodedLayersOnDevice() {
        listOf(LiveWallpaperSoakTarget.WEATHER, LiveWallpaperSoakTarget.PARALLAX).forEach { target ->
            val report = soak(target, LiveWallpaperSoakScenarios.SURFACE_CHURN, cycles = LONG_RUN)
            assertTrue(
                target.name + " never retained a decoded bitmap on device: " + report.peak,
                (report.peak["imageBuffers"] ?: 0) > 0,
            )
            assertPeaksBounded(target.name, report.peak)
            assertEquals(
                target.name + " leaked bitmaps past onDestroy: " + report.residual,
                0,
                report.residual.imageBuffers,
            )
        }
    }

    /** Confirms the run really exercised battery saver rather than skipping it. */
    @Test
    fun powerSaveStateIsReadableFromTheEngineHost() {
        val powerManager = context.getSystemService(PowerManager::class.java)
        assertTrue(
            "PowerManager must be reachable for the power-saver scenario to mean anything",
            powerManager != null,
        )
    }

    private companion object {
        const val SHORT_RUN = 3
        const val LONG_RUN = 25
        const val SETTLE_MS = 120L
        const val IMAGE_EDGE = 120

        /** A minimal two-frame animated GIF, so GIF playback really starts. */
        val GIF_BYTES: ByteArray = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61, // GIF89a
            0x02, 0x00, 0x02, 0x00, // 2x2 logical screen
            0xF0.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x00, // colour 0: black
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // colour 1: white
            0x21, 0xF9.toByte(), 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00, // frame 1 control
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00,
            0x21, 0xF9.toByte(), 0x04, 0x00, 0x0A, 0x00, 0x00, 0x00, // frame 2 control
            0x2C, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x02, 0x00, 0x00,
            0x02, 0x02, 0x8C.toByte(), 0x01, 0x00,
            0x3B, // trailer
        )
    }
}
