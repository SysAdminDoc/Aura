package com.freevibe.service.soak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.service.wallpaper.WallpaperService
import android.view.Surface
import android.view.SurfaceHolder
import com.freevibe.service.LiveWallpaperReceiptStore
import com.freevibe.service.LiveWallpaperResourceReporter
import com.freevibe.service.LiveWallpaperResourceSnapshot
import com.freevibe.service.ParallaxWallpaperService
import com.freevibe.service.VIDEO_PLAYLIST_ADVANCE_CHOICES
import com.freevibe.service.VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF
import com.freevibe.service.VIDEO_WALLPAPER_PLAYLIST_PREF
import com.freevibe.service.VIDEO_WALLPAPER_PREFS_NAME
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_ZOOM
import com.freevibe.service.VideoPlaylist
import com.freevibe.service.VideoPlaylistClip
import com.freevibe.service.VideoWallpaperService
import com.freevibe.service.WeatherWallpaperService
import com.freevibe.service.encodeVideoPlaylist
import java.io.File

/**
 * A cross-engine lifecycle soak harness for Aura's live wallpapers.
 *
 * Video, GIF, weather, and parallax are the only components in the app that run
 * for days inside a process nobody restarts. Their failure mode is not a crash
 * that a normal test would catch - it is one extra retained player, posted
 * callback, sensor listener, segmenter, or full-screen bitmap per surface cycle,
 * which only becomes visible after the platform has churned the surface a few
 * hundred times.
 *
 * So the harness does not assert against hand-picked ceilings, which would only
 * encode today's numbers. It runs the same scenario at two very different cycle
 * counts and requires the peak resource usage to be *identical*: anything that
 * accumulates per cycle shows up as a difference, and anything merely large but
 * bounded does not produce a false failure.
 *
 * This lives in the debug source set so both the JVM (Robolectric) soak and the
 * instrumented on-device soak drive byte-for-byte the same script. It complements
 * rather than replaces the physical-device captures parked in
 * `Roadmap_Blocked.md`: those cover real decoders and real OEM power management,
 * which no harness can simulate.
 */
enum class LiveWallpaperSoakStep {
    /** Engine attach. */
    CREATE,
    SURFACE_CREATED,
    SURFACE_CHANGED,
    VISIBLE,
    HIDDEN,
    SURFACE_DESTROYED,
    /** Engine detach. */
    DESTROY,
    POWER_SAVE_ON,
    POWER_SAVE_OFF,
    /** The user replaced the wallpaper file underneath a live engine. */
    REPLACE_MEDIA,
    /** Let posted callbacks and decode threads settle. */
    SETTLE,
}

data class LiveWallpaperSoakScenario(
    val name: String,
    /** Steps repeated once per cycle, between CREATE and DESTROY. */
    val steps: List<LiveWallpaperSoakStep>,
)

object LiveWallpaperSoakScenarios {

    /** Rotation, launcher restarts, and preview teardown all churn the surface. */
    val SURFACE_CHURN = LiveWallpaperSoakScenario(
        name = "surface_churn",
        steps = listOf(
            LiveWallpaperSoakStep.SURFACE_CREATED,
            LiveWallpaperSoakStep.SURFACE_CHANGED,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.SETTLE,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.SURFACE_DESTROYED,
        ),
    )

    /** Opening and dismissing an app over the wallpaper, hundreds of times a day. */
    val VISIBILITY_CHURN = LiveWallpaperSoakScenario(
        name = "visibility_churn",
        steps = listOf(
            LiveWallpaperSoakStep.SURFACE_CREATED,
            LiveWallpaperSoakStep.SURFACE_CHANGED,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.SETTLE,
            LiveWallpaperSoakStep.SURFACE_DESTROYED,
        ),
    )

    /** Battery saver toggling while the wallpaper is on screen. */
    val POWER_SAVER = LiveWallpaperSoakScenario(
        name = "power_saver",
        steps = listOf(
            LiveWallpaperSoakStep.SURFACE_CREATED,
            LiveWallpaperSoakStep.SURFACE_CHANGED,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.POWER_SAVE_ON,
            LiveWallpaperSoakStep.SETTLE,
            LiveWallpaperSoakStep.POWER_SAVE_OFF,
            LiveWallpaperSoakStep.SETTLE,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.SURFACE_DESTROYED,
        ),
    )

    /**
     * Keyguard teardown. Several OEMs destroy and rebuild the wallpaper surface
     * around an unlock rather than only toggling visibility, so the engine has to
     * survive the heavier of the two shapes.
     */
    val UNLOCK = LiveWallpaperSoakScenario(
        name = "unlock",
        steps = listOf(
            LiveWallpaperSoakStep.SURFACE_CREATED,
            LiveWallpaperSoakStep.SURFACE_CHANGED,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.SURFACE_DESTROYED,
            LiveWallpaperSoakStep.SURFACE_CREATED,
            LiveWallpaperSoakStep.SURFACE_CHANGED,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.SETTLE,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.SURFACE_DESTROYED,
        ),
    )

    /** Auto-rotation rewrites the wallpaper file underneath a running engine. */
    val MEDIA_REPLACEMENT = LiveWallpaperSoakScenario(
        name = "media_replacement",
        steps = listOf(
            LiveWallpaperSoakStep.SURFACE_CREATED,
            LiveWallpaperSoakStep.SURFACE_CHANGED,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.REPLACE_MEDIA,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.VISIBLE,
            LiveWallpaperSoakStep.SETTLE,
            LiveWallpaperSoakStep.HIDDEN,
            LiveWallpaperSoakStep.SURFACE_DESTROYED,
        ),
    )

    val ALL = listOf(SURFACE_CHURN, VISIBILITY_CHURN, POWER_SAVER, UNLOCK, MEDIA_REPLACEMENT)
}

/** The five engine behaviours Aura ships across three wallpaper services. */
enum class LiveWallpaperSoakTarget(
    val serviceClass: Class<out WallpaperService>,
    val engineId: String,
    val prefsName: String,
    val pathKey: String,
    val mediaFileName: String,
) {
    VIDEO(
        VideoWallpaperService::class.java,
        LiveWallpaperReceiptStore.ENGINE_VIDEO,
        "freevibe_live_wp",
        "video_path",
        "soak-wallpaper.mp4",
    ),
    GIF(
        VideoWallpaperService::class.java,
        LiveWallpaperReceiptStore.ENGINE_VIDEO,
        "freevibe_live_wp",
        "video_path",
        "soak-wallpaper.gif",
    ),
    VIDEO_PLAYLIST(
        VideoWallpaperService::class.java,
        LiveWallpaperReceiptStore.ENGINE_VIDEO,
        "freevibe_live_wp",
        "video_path",
        "soak-playlist.mp4",
    ),
    WEATHER(
        WeatherWallpaperService::class.java,
        LiveWallpaperReceiptStore.ENGINE_WEATHER,
        "freevibe_weather_wp",
        "wallpaper_path",
        "soak-wallpaper.png",
    ),
    PARALLAX(
        ParallaxWallpaperService::class.java,
        LiveWallpaperReceiptStore.ENGINE_PARALLAX,
        "freevibe_parallax",
        "image_path",
        "soak-parallax.png",
    ),
}

/** The two clips [LiveWallpaperSoakTarget.VIDEO_PLAYLIST] plays, the first being its media file. */
fun soakPlaylistClips(mediaFile: File): List<File> =
    listOf(mediaFile, File(mediaFile.parentFile, "soak-playlist-second.mp4"))

/**
 * Turns the list on for [LiveWallpaperSoakTarget.VIDEO_PLAYLIST] and off for every other target. A
 * list is told apart by its clips rather than their bytes, so [generation] swaps their order, which
 * is how a media replacement reaches a playing list. The timer is set, so a soak holds that too.
 */
fun writeSoakPlaylist(context: Context, target: LiveWallpaperSoakTarget, mediaFile: File, generation: Int) {
    val clips = soakPlaylistClips(mediaFile).map { clip ->
        VideoPlaylistClip(clip.absolutePath, clip.absolutePath, clip.name, VIDEO_WALLPAPER_SCALE_MODE_ZOOM)
    }
    val playlist = VideoPlaylist(
        clips = if (generation % 2 == 0) clips else clips.asReversed(),
        advanceSeconds = VIDEO_PLAYLIST_ADVANCE_CHOICES.first { it > 0 },
    )
    context.getSharedPreferences(VIDEO_WALLPAPER_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(VIDEO_WALLPAPER_PLAYLIST_PREF, encodeVideoPlaylist(playlist))
        .putBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, target == LiveWallpaperSoakTarget.VIDEO_PLAYLIST)
        .commit()
}

/**
 * The parts of a soak step that only the host can perform: toggling platform
 * power-save state, rewriting the media file, and letting queued work run.
 */
interface LiveWallpaperSoakEnvironment {
    fun setPowerSaveMode(enabled: Boolean)
    fun replaceMedia()
    fun settle()
}

/** Peak-and-final resource usage observed over a soak run. */
data class LiveWallpaperSoakReport(
    val target: LiveWallpaperSoakTarget,
    val scenario: String,
    val cycles: Int,
    /** Highest value seen for each resource kind at any point in the run. */
    val peak: Map<String, Int>,
    /** What the engine still held after the final `onDestroy`. */
    val residual: LiveWallpaperResourceSnapshot,
) {
    val isDrained: Boolean get() = residual.isDrained
}

/**
 * A minimal SurfaceHolder.
 *
 * Locking a canvas returns null so engines take their "surface not ready" path -
 * the harness is about lifecycle bookkeeping, not pixels. [getSurface] is real,
 * though: the video engine hands it to the player, and a stub would send the decoder
 * down its failure branch on a device, which would silently mean real playback is
 * never soaked.
 */
class SoakSurfaceHolder(
    private val width: Int = 1080,
    private val height: Int = 1920,
) : SurfaceHolder {

    private val texture by lazy { SurfaceTexture(0) }
    private val backingSurface by lazy { Surface(texture) }

    /** Releases the backing surface once a soak run is finished with it. */
    fun release() {
        runCatching { backingSurface.release() }
        runCatching { texture.release() }
    }

    override fun addCallback(callback: SurfaceHolder.Callback?) = Unit
    override fun removeCallback(callback: SurfaceHolder.Callback?) = Unit
    override fun isCreating(): Boolean = false

    @Deprecated("Deprecated in Java")
    override fun setType(type: Int) = Unit
    override fun setFixedSize(width: Int, height: Int) = Unit
    override fun setSizeFromLayout() = Unit
    override fun setFormat(format: Int) = Unit
    override fun setKeepScreenOn(screenOn: Boolean) = Unit
    override fun lockCanvas(): Canvas? = null
    override fun lockCanvas(dirty: Rect?): Canvas? = null
    override fun unlockCanvasAndPost(canvas: Canvas?) = Unit
    override fun getSurfaceFrame(): Rect = Rect(0, 0, width, height)
    override fun getSurface(): Surface = backingSurface
}

/**
 * Drives one engine through a scenario and reports what it held along the way.
 *
 * @param onEngineCallback how to reach the thread the platform would deliver
 *   engine callbacks on. The JVM soak already runs on the main thread; the
 *   instrumented soak hands in `runOnMainSync` so Handlers and the player see
 *   the same thread they would in production.
 */
class LiveWallpaperSoakDriver(
    private val target: LiveWallpaperSoakTarget,
    private val engine: WallpaperService.Engine,
    private val holder: SurfaceHolder,
    private val environment: LiveWallpaperSoakEnvironment,
    private val onEngineCallback: (() -> Unit) -> Unit = { it() },
) {

    private val reporter: LiveWallpaperResourceReporter = engine as? LiveWallpaperResourceReporter
        ?: error("${target.name} engine must implement LiveWallpaperResourceReporter")

    private val peak = linkedMapOf<String, Int>()

    fun run(scenario: LiveWallpaperSoakScenario, cycles: Int): LiveWallpaperSoakReport {
        require(cycles > 0) { "a soak needs at least one cycle" }
        apply(LiveWallpaperSoakStep.CREATE)
        repeat(cycles) {
            scenario.steps.forEach(::apply)
        }
        apply(LiveWallpaperSoakStep.DESTROY)
        environment.settle()
        val residual = snapshot()
        record(residual)
        return LiveWallpaperSoakReport(
            target = target,
            scenario = scenario.name,
            cycles = cycles,
            peak = peak.toMap(),
            residual = residual,
        )
    }

    private fun apply(step: LiveWallpaperSoakStep) {
        when (step) {
            LiveWallpaperSoakStep.CREATE -> onEngine { engine.onCreate(holder) }
            LiveWallpaperSoakStep.SURFACE_CREATED -> onEngine { engine.onSurfaceCreated(holder) }
            LiveWallpaperSoakStep.SURFACE_CHANGED ->
                onEngine { engine.onSurfaceChanged(holder, PIXEL_FORMAT_RGBA_8888, WIDTH, HEIGHT) }
            LiveWallpaperSoakStep.VISIBLE -> onEngine { engine.onVisibilityChanged(true) }
            LiveWallpaperSoakStep.HIDDEN -> onEngine { engine.onVisibilityChanged(false) }
            LiveWallpaperSoakStep.SURFACE_DESTROYED -> onEngine { engine.onSurfaceDestroyed(holder) }
            LiveWallpaperSoakStep.DESTROY -> onEngine { engine.onDestroy() }
            LiveWallpaperSoakStep.POWER_SAVE_ON -> environment.setPowerSaveMode(true)
            LiveWallpaperSoakStep.POWER_SAVE_OFF -> environment.setPowerSaveMode(false)
            LiveWallpaperSoakStep.REPLACE_MEDIA -> environment.replaceMedia()
            LiveWallpaperSoakStep.SETTLE -> environment.settle()
        }
        record(snapshot())
    }

    private fun onEngine(block: () -> Unit) = onEngineCallback(block)

    private fun snapshot(): LiveWallpaperResourceSnapshot {
        // Reading engine fields from the harness thread would race the very
        // Handler callbacks being counted, so the read goes to the same thread the
        // callbacks were delivered on.
        lateinit var result: LiveWallpaperResourceSnapshot
        onEngineCallback { result = reporter.resourceSnapshot() }
        return result
    }

    private fun record(snapshot: LiveWallpaperResourceSnapshot) {
        snapshot.asMap().forEach { (kind, value) ->
            peak[kind] = maxOf(peak[kind] ?: 0, value)
        }
    }

    private companion object {
        const val PIXEL_FORMAT_RGBA_8888 = 1
        const val WIDTH = 1080
        const val HEIGHT = 1920
    }
}
