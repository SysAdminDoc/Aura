package com.freevibe.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.os.PowerManager
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.ExoPlayer
import com.freevibe.service.soak.SoakSurfaceHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The video engine's side of the player: what it asks for and when. Real decoding is
 * covered on a device by `VideoWallpaperPlayerInstrumentedTest`, and the bookkeeping
 * across hundreds of surface cycles by the soak.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoWallpaperEngineTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val players = mutableListOf<FakePlayer>()
    private val holder = SoakSurfaceHolder()
    private val originalFactory = videoWallpaperPlayerFactory
    private lateinit var file: File

    /** A player that does what it's told and lets the test decide how the decoder is doing. */
    private class FakePlayer {
        val mock = mockk<ExoPlayer>(relaxed = true)
        val counters = DecoderCounters()
        var wantsToPlay = false
        var position = 0L
        var startPosition = -1L
        var item: MediaItem? = null
        var format: Format? = null
        private val listener = slot<Player.Listener>()

        init {
            every { mock.addListener(capture(listener)) } answers { }
            every { mock.playWhenReady } answers { wantsToPlay }
            every { mock.playWhenReady = any() } answers { wantsToPlay = firstArg() }
            every { mock.play() } answers { wantsToPlay = true }
            every { mock.pause() } answers { wantsToPlay = false }
            every { mock.currentPosition } answers { position }
            every { mock.videoDecoderCounters } answers { counters }
            every { mock.videoFormat } answers { format }
            every { mock.setMediaItem(any<MediaItem>(), any<Long>()) } answers {
                item = firstArg()
                startPosition = secondArg()
            }
        }

        fun fail() = listener.captured.onPlayerError(
            PlaybackException("decoder died", null, PlaybackException.ERROR_CODE_DECODING_FAILED),
        )
    }

    @Before
    fun setUp() {
        file = File(context.filesDir, "wallpaper.mp4").apply { writeBytes(ByteArray(64)) }
        prefs().edit().putString("video_path", file.absolutePath).commit()
        videoWallpaperPlayerFactory = { FakePlayer().also { players += it }.mock }
    }

    @After
    fun tearDown() {
        videoWallpaperPlayerFactory = originalFactory
        holder.release()
    }

    private fun prefs() = context.getSharedPreferences(VIDEO_WALLPAPER_PREFS_NAME, Context.MODE_PRIVATE)

    private fun engine(): VideoWallpaperService.VideoEngine {
        val service = Robolectric.buildService(VideoWallpaperService::class.java).create().get()
        return service.onCreateEngine() as VideoWallpaperService.VideoEngine
    }

    private fun VideoWallpaperService.VideoEngine.showOnScreen() {
        onSurfaceCreated(holder)
        onVisibilityChanged(true)
        idleFor(0)
    }

    private fun idleFor(ms: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }

    /** Advances time in watchdog steps, with [onStep] showing what the decoder did in each. */
    private fun runWatchdog(ms: Long, onStep: () -> Unit = {}) {
        var elapsed = 0L
        while (elapsed < ms) {
            onStep()
            idleFor(VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS)
            elapsed += VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS
        }
    }

    @Test
    fun `the engine plays the file on a loop on its own surface`() {
        engine().showOnScreen()

        val player = players.single()
        verify { player.mock.setVideoSurface(holder.getSurface()) }
        verify { player.mock.repeatMode = Player.REPEAT_MODE_ONE }
        verify { player.mock.prepare() }
        assertEquals(Uri.fromFile(file), player.item?.localConfiguration?.uri)
        assertEquals(0L, player.startPosition)
        assertTrue(player.wantsToPlay)
    }

    @Test
    fun `zoom crops the clip and fit shows all of it`() {
        engine().showOnScreen()
        verify {
            players.single().mock.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }

        prefs().edit().putString("scale_mode", VIDEO_WALLPAPER_SCALE_MODE_FIT).commit()
        engine().showOnScreen()
        verify { players.last().mock.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT }
    }

    @Test
    fun `the playback speed setting reaches the player`() {
        context.getSharedPreferences(VIDEO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(VIDEO_PLAYBACK_SPEED_PREF, 1.5f).commit()

        engine().showOnScreen()

        verify { players.single().mock.setPlaybackSpeed(1.5f) }
    }

    @Test
    fun `a surface that isn't on screen yet doesn't start playing`() {
        engine().onSurfaceCreated(holder)
        idleFor(0)

        assertFalse(players.single().wantsToPlay)
    }

    @Test
    fun `hiding pauses, and coming back starts the clip from the top`() {
        val engine = engine()
        engine.showOnScreen()
        val player = players.single()

        engine.onVisibilityChanged(false)
        assertFalse(player.wantsToPlay)

        engine.onVisibilityChanged(true)
        verify { player.mock.seekTo(0) }
        assertTrue(player.wantsToPlay)
        assertEquals("coming back must reuse the same player", 1, players.size)
    }

    @Test
    fun `battery saver holds the picture and lets it go again`() {
        val engine = engine()
        engine.showOnScreen()
        val player = players.single()

        setPowerSave(true)
        assertFalse(player.wantsToPlay)

        setPowerSave(false)
        assertTrue(player.wantsToPlay)
        assertEquals(1, players.size)
    }

    private fun setPowerSave(enabled: Boolean) {
        shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(enabled)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        idleFor(0)
    }

    @Test
    fun `battery saver stays in charge when a surface comes up`() {
        setPowerSave(true)

        engine().showOnScreen()

        assertFalse(players.single().wantsToPlay)
    }

    @Test
    fun `a player error rebuilds after the backoff and resumes where it stopped`() {
        engine().showOnScreen()
        val first = players.single()
        first.position = 4_200L

        first.fail()

        verify { first.mock.removeListener(any()) }
        verify { first.mock.release() }
        idleFor(VideoWallpaperRecovery.backoffMs(1) - 1)
        assertEquals("the rebuild must wait for the backoff", 1, players.size)
        idleFor(1)
        assertEquals(2, players.size)
        assertEquals(4_200L, players.last().startPosition)
    }

    @Test
    fun `a player that won't build is retried`() {
        val working = videoWallpaperPlayerFactory
        videoWallpaperPlayerFactory = { throw IllegalStateException("no decoder") }
        engine().showOnScreen()
        videoWallpaperPlayerFactory = working

        idleFor(VideoWallpaperRecovery.backoffMs(1))

        assertEquals(1, players.size)
    }

    @Test
    fun `a failure while setting the player up still releases it`() {
        val working = videoWallpaperPlayerFactory
        videoWallpaperPlayerFactory = {
            working(context).also { every { it.setVideoSurface(any<Surface>()) } throws IllegalStateException("gone") }
        }

        engine().showOnScreen()

        verify { players.single().mock.release() }
    }

    @Test
    fun `a stopped decoder is rebuilt even while the position keeps moving`() {
        engine().showOnScreen()
        val player = players.single()

        // With no audio renderer the position is a plain clock, so this is what a hung
        // decoder looks like: the clock runs on and no frame ever comes back.
        runWatchdog(VideoWallpaperRecovery.STALL_TIMEOUT_MS * 2) {
            player.position += VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS
        }
        idleFor(VideoWallpaperRecovery.backoffMs(1))

        verify { player.mock.release() }
        assertEquals(2, players.size)
    }

    @Test
    fun `a decoder that keeps producing frames is left alone`() {
        engine().showOnScreen()
        val player = players.single()

        runWatchdog(120_000L) { player.counters.renderedOutputBufferCount += 30 }

        assertEquals(1, players.size)
    }

    @Test
    fun `frames dropped by a slow device still count as a working decoder`() {
        engine().showOnScreen()
        val player = players.single()

        runWatchdog(60_000L) { player.counters.droppedBufferCount += 30 }

        assertEquals(1, players.size)
    }

    @Test
    fun `a slideshow clip gets as long as its own frames take`() {
        engine().showOnScreen()
        val player = players.single()
        // One picture every ten seconds, so several watchdog windows pass between frames.
        player.format = Format.Builder().setFrameRate(0.1f).build()

        runWatchdog(24_000L)
        idleFor(VideoWallpaperRecovery.BASE_BACKOFF_MS)
        assertEquals("that wait is normal for this clip", 1, players.size)

        runWatchdog(12_000L)
        idleFor(VideoWallpaperRecovery.BASE_BACKOFF_MS)
        assertEquals("no frame at all for three of its intervals is a stall", 2, players.size)
    }

    @Test
    fun `a hidden wallpaper never counts as stalled`() {
        val engine = engine()
        engine.showOnScreen()
        engine.onVisibilityChanged(false)

        runWatchdog(600_000L)

        assertEquals(1, players.size)
    }

    @Test
    fun `battery saver's pause never counts as stalled`() {
        engine().showOnScreen()
        setPowerSave(true)

        runWatchdog(600_000L)

        assertEquals(1, players.size)
    }

    @Test
    fun `an old sample doesn't make a new player look stalled`() {
        val engine = engine()
        engine.showOnScreen()
        idleFor(VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS)
        engine.onSurfaceDestroyed(holder)
        idleFor(60_000L)

        engine.showOnScreen()
        idleFor(VideoWallpaperRecovery.WATCHDOG_INTERVAL_MS)
        idleFor(VideoWallpaperRecovery.BASE_BACKOFF_MS)

        assertEquals(2, players.size)
        verify(exactly = 0) { players.last().mock.release() }
    }

    @Test
    fun `after its attempts run out the engine holds still`() {
        val engine = engine()
        engine.showOnScreen()

        repeat(VideoWallpaperRecovery.MAX_ATTEMPTS + 1) { attempt ->
            players.last().fail()
            idleFor(VideoWallpaperRecovery.backoffMs(attempt + 1))
        }

        assertEquals(VideoWallpaperRecovery.MAX_ATTEMPTS + 1, players.size)
        idleFor(600_000L)
        assertEquals("no rebuild once the budget is gone", VideoWallpaperRecovery.MAX_ATTEMPTS + 1, players.size)
        assertEquals(0, engine.resourceSnapshot().players)
    }

    @Test
    fun `a replaced file gets a new player from the top`() {
        val engine = engine()
        engine.showOnScreen()
        players.last().position = 900L
        engine.onVisibilityChanged(false)

        file.writeBytes(ByteArray(128))
        file.setLastModified(file.lastModified() + 5_000L)
        engine.onVisibilityChanged(true)

        assertEquals(2, players.size)
        verify { players.first().mock.release() }
        assertEquals(0L, players.last().startPosition)
    }

    @Test
    fun `nothing survives the surface and engine going away`() {
        val engine = engine()
        engine.onCreate(holder)
        engine.showOnScreen()
        val failed = players.single()
        failed.fail()

        engine.onSurfaceDestroyed(holder)
        engine.onDestroy()
        idleFor(600_000L)

        verify(exactly = 1) { failed.mock.release() }
        assertEquals("a pending rebuild must not outlive the surface", 1, players.size)
        assertTrue(engine.resourceSnapshot().isDrained)
    }

    @Test
    fun `the real player has a video renderer and nothing that could make a sound`() {
        val player = buildVideoWallpaperPlayer(context)
        try {
            assertEquals(1, player.rendererCount)
            assertEquals(C.TRACK_TYPE_VIDEO, player.getRendererType(0))
            assertEquals(
                "the engine's FPS cap sets the surface's frame rate, so the player must not",
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
                player.videoChangeFrameRateStrategy,
            )
        } finally {
            player.release()
        }
    }
}
