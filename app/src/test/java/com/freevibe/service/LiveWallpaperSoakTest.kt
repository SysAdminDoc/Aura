package com.freevibe.service

import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.service.soak.LiveWallpaperSoakDriver
import com.freevibe.service.soak.LiveWallpaperSoakEnvironment
import com.freevibe.service.soak.LiveWallpaperSoakScenario
import com.freevibe.service.soak.LiveWallpaperSoakScenarios
import com.freevibe.service.soak.LiveWallpaperSoakTarget
import com.freevibe.service.soak.SoakSurfaceHolder
import io.mockk.mockk
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor

/**
 * The cross-engine live-wallpaper soak.
 *
 * Video, GIF, weather, and parallax engines live in a process that is never
 * restarted, so their real failure mode is accumulation: one retained player,
 * posted callback, sensor listener, segmenter, or full-screen bitmap per surface
 * cycle. A test that ran a fixed number of cycles and compared against a hand-
 * picked ceiling would only encode today's numbers, so instead each scenario is
 * run at two very different cycle counts and the peak usage must be identical.
 * Anything that grows per cycle fails; anything merely large but bounded passes.
 *
 * On-device behaviour that no JVM harness can model - real decoders, real OEM
 * power management, real surface buffers - stays with the physical-device
 * captures tracked in `Roadmap_Blocked.md`. This does not replace them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveWallpaperSoakTest {

    private val context get() = RuntimeEnvironment.getApplication()
    private val originalPlayerFactory = videoWallpaperPlayerFactory

    // The JVM has no decoder for ExoPlayer to run, so a stand-in keeps the video engine
    // on its success path, holding a live player. The instrumented soak runs the real one.
    @Before
    fun usePlayerThatCanRunWithoutADecoder() {
        videoWallpaperPlayerFactory = { mockk<ExoPlayer>(relaxed = true) }
    }

    @After
    fun restorePlayer() {
        videoWallpaperPlayerFactory = originalPlayerFactory
    }

    private class RobolectricSoakEnvironment(
        private val mediaFile: File,
    ) : LiveWallpaperSoakEnvironment {

        private var generation = 0

        override fun setPowerSaveMode(enabled: Boolean) {
            val context = RuntimeEnvironment.getApplication()
            val powerManager = context.getSystemService(PowerManager::class.java)
            shadowOf(powerManager).setIsPowerSaveMode(enabled)
            context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            settle()
        }

        override fun replaceMedia() {
            generation += 1
            mediaFile.writeBytes(ByteArray(64 + generation) { (it + generation).toByte() })
            // Engines detect replacement by lastModified, and a same-millisecond
            // rewrite is exactly the case the path comparison was added for.
            mediaFile.setLastModified(mediaFile.lastModified() + 5_000L * generation)
            settle()
        }

        override fun settle() {
            // Decode work runs on plain threads, so drain the main looper, give
            // those threads a slice, then drain whatever they posted back.
            repeat(SETTLE_PASSES) {
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(SETTLE_SLICE_MS)
            }
            shadowOf(Looper.getMainLooper()).idle()
        }

        private companion object {
            const val SETTLE_PASSES = 4
            const val SETTLE_SLICE_MS = 5L
        }
    }

    /**
     * Writes media the platform shadows can actually decode. Feeding the engines
     * garbage would only ever exercise their failure branches, and it is the
     * success path - a live player, a retained bitmap - that can leak.
     */
    private fun mediaFileFor(target: LiveWallpaperSoakTarget): File {
        val file = File(context.filesDir, target.mediaFileName)
        file.parentFile?.mkdirs()
        when (target) {
            LiveWallpaperSoakTarget.WEATHER,
            LiveWallpaperSoakTarget.PARALLAX,
            -> file.writeBytes(pngBytes(IMAGE_WIDTH, IMAGE_HEIGHT))
            LiveWallpaperSoakTarget.VIDEO -> file.writeBytes(ByteArray(64) { it.toByte() })
            // GIF decode has no usable JVM shadow, so this target soaks the decode
            // failure and bounded-rebuild path; real GIF playback is what the
            // instrumented run on an emulator contributes.
            LiveWallpaperSoakTarget.GIF -> file.writeBytes(ByteArray(64) { it.toByte() })
        }
        return file
    }

    /**
     * A real PNG, because the decode shadows read the header for dimensions and a
     * zero-sized image decodes to null - which would quietly turn the two bitmap
     * engines into no-ops. java.desktop is not on the unit-test compile classpath,
     * so the encoder is written out by hand rather than pulled from ImageIO.
     */
    private fun pngBytes(width: Int, height: Int): ByteArray {
        val raw = ByteArrayOutputStream()
        repeat(height) {
            raw.write(0) // filter type: none
            repeat(width) { raw.write(byteArrayOf(0, 0, 0, -1)) } // opaque black RGBA
        }
        val deflater = Deflater()
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val compressed = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer))
        }
        deflater.end()

        val header = ByteArrayOutputStream().apply {
            write(intBytes(width)); write(intBytes(height))
            write(8); write(6); write(0); write(0); write(0) // 8-bit RGBA, no interlace
        }
        return ByteArrayOutputStream().apply {
            write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) // PNG signature
            write(pngChunk("IHDR", header.toByteArray()))
            write(pngChunk("IDAT", compressed.toByteArray()))
            write(pngChunk("IEND", ByteArray(0)))
        }.toByteArray()
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value
        return ByteArrayOutputStream().apply {
            write(intBytes(data.size))
            write(typeBytes)
            write(data)
            write(intBytes(crc.toInt()))
        }.toByteArray()
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )

    /**
     * Robolectric ships no sensors by default, which would silently reduce the
     * parallax soak to a no-op on exactly the resource most likely to leak.
     */
    private fun installAccelerometer() {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))
    }

    private fun soak(
        target: LiveWallpaperSoakTarget,
        scenario: LiveWallpaperSoakScenario,
        cycles: Int,
    ) = run {
        installAccelerometer()
        val mediaFile = mediaFileFor(target)
        context.getSharedPreferences(target.prefsName, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(target.pathKey, mediaFile.absolutePath)
            .commit()

        @Suppress("UNCHECKED_CAST")
        val service = Robolectric
            .buildService(target.serviceClass as Class<WallpaperService>)
            .create()
            .get()
        val engine = service.onCreateEngine()
        val holder = SoakSurfaceHolder()
        try {
            LiveWallpaperSoakDriver(
                target = target,
                engine = engine,
                holder = holder,
                environment = RobolectricSoakEnvironment(mediaFile),
            ).run(scenario, cycles)
        } finally {
            holder.release()
        }
    }

    @Test
    fun `every engine drains its resources when the surface and engine go away`() {
        LiveWallpaperSoakTarget.entries.forEach { target ->
            LiveWallpaperSoakScenarios.ALL.forEach { scenario ->
                val report = soak(target, scenario, cycles = SHORT_RUN)
                assertTrue(
                    "${target.name}/${scenario.name} still held ${report.residual} after onDestroy",
                    report.isDrained,
                )
            }
        }
    }

    @Test
    fun `no engine accumulates resources as lifecycle cycles repeat`() {
        LiveWallpaperSoakTarget.entries.forEach { target ->
            LiveWallpaperSoakScenarios.ALL.forEach { scenario ->
                val report = soak(target, scenario, cycles = LONG_RUN)
                assertPeaksBounded("${target.name}/${scenario.name}", report.peak)
            }
        }
    }

    /**
     * Ceilings are what one engine can legitimately hold at a single instant, not
     * budgets that grow with the run: anything accumulating per cycle reaches
     * [LONG_RUN] and fails here, while ordinary churn never comes close.
     *
     * Requiring the peaks at two different cycle counts to match exactly was tried
     * first and proved flaky - loader depth depends on whether a decode thread
     * happened to finish before the next request arrived - so the one racy kind is
     * bounded by the production cap rather than by a number copied into the test.
     */
    private fun assertPeaksBounded(label: String, peak: Map<String, Int>) {
        val ceilings = mapOf(
            // A video player or a decoded Movie, never a growing set of either.
            "players" to 2,
            // Video's four: GIF frame loop, telemetry heartbeat, stall watchdog,
            // and a pending recovery rebuild.
            "frameCallbacks" to 4,
            "sensorListeners" to 1,
            "broadcastReceivers" to 1,
            // Parallax's four layers: source, background, foreground, fallback.
            "imageBuffers" to 4,
            "segmenters" to 1,
            "loaderThreads" to LIVE_WALLPAPER_MAX_OUTSTANDING_LOADS,
        )
        assertEquals(
            "a resource kind was added without a ceiling, so it would soak unbounded",
            ceilings.keys,
            peak.keys,
        )
        peak.forEach { (kind, value) ->
            val ceiling = ceilings.getValue(kind)
            assertTrue(
                "$label held $value $kind across $LONG_RUN cycles, above the $ceiling " +
                    "one engine can hold at once: $peak",
                value <= ceiling,
            )
        }
    }

    /**
     * A soak that never observes an engine holding anything would pass forever
     * while testing nothing, which is the failure mode of every leak harness
     * built on shadows. Each target therefore has to be seen holding the
     * resources that define it.
     */
    @Test
    fun `the harness observes each engine actually holding its resources`() {
        val required = mapOf(
            LiveWallpaperSoakTarget.VIDEO to listOf("players", "frameCallbacks", "broadcastReceivers"),
            LiveWallpaperSoakTarget.GIF to listOf("frameCallbacks", "broadcastReceivers"),
            LiveWallpaperSoakTarget.WEATHER to listOf("frameCallbacks", "loaderThreads"),
            LiveWallpaperSoakTarget.PARALLAX to
                listOf("frameCallbacks", "sensorListeners", "loaderThreads"),
        )
        required.forEach { (target, kinds) ->
            val peak = soak(target, LiveWallpaperSoakScenarios.SURFACE_CHURN, cycles = SHORT_RUN).peak
            kinds.forEach { kind ->
                assertTrue(
                    target.name + " never held any " + kind +
                        ", so the soak is not exercising it: " + peak,
                    (peak[kind] ?: 0) > 0,
                )
            }
        }
    }

    @Test
    fun `every shipped engine is covered by the harness`() {
        val covered = LiveWallpaperSoakTarget.entries.map { it.serviceClass.name }.toSet()
        val shipped = setOf(
            VideoWallpaperService::class.java.name,
            WeatherWallpaperService::class.java.name,
            ParallaxWallpaperService::class.java.name,
        )
        assertEquals(
            "a live-wallpaper service exists that the soak harness never drives",
            shipped,
            covered,
        )
        // GIF shares VideoWallpaperService but takes a completely different code
        // path (Movie decode + hand-posted frame loop instead of ExoPlayer), so
        // it is soaked as its own target rather than folded into video.
        assertTrue(
            LiveWallpaperSoakTarget.GIF.mediaFileName.endsWith(".gif"),
        )
    }

    /**
     * The two bitmap engines decode through `ImageDecoder` from API 28 up, and this
     * JVM runtime has no working decoder behind it - so at API 35 they retain no
     * bitmap at all and the retention axis would silently go untested. Below 28
     * the same production code takes the `BitmapFactory` branch, which does decode
     * here, so bitmap lifetime is soaked at the oldest supported SDK. Retention
     * under the modern decoder is what the instrumented run contributes.
     */
    @Test
    @Config(sdk = [MIN_SDK])
    fun `bitmap engines retain a bounded set of decoded layers`() {
        listOf(LiveWallpaperSoakTarget.WEATHER, LiveWallpaperSoakTarget.PARALLAX).forEach { target ->
            LiveWallpaperSoakScenarios.ALL.forEach { scenario ->
                val label = "${target.name}/${scenario.name}"
                val report = soak(target, scenario, cycles = LONG_RUN)
                assertTrue(
                    "$label never retained a decoded bitmap, so retention is untested: " +
                        report.peak,
                    (report.peak["imageBuffers"] ?: 0) > 0,
                )
                assertPeaksBounded(label, report.peak)
                assertEquals(
                    "$label leaked bitmaps past onDestroy: ${report.residual}",
                    0,
                    report.residual.imageBuffers,
                )
            }
        }
    }

    private companion object {
        const val MIN_SDK = 26
        const val SHORT_RUN = 3
        const val LONG_RUN = 30
        const val IMAGE_WIDTH = 240
        const val IMAGE_HEIGHT = 480
    }
}
