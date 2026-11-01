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
        var items: List<MediaItem> = emptyList()
        var index = 0
        private val listener = slot<Player.Listener>()

        init {
            every { mock.setMediaItems(any<List<MediaItem>>(), any<Int>(), any<Long>()) } answers {
                items = firstArg()
                index = secondArg()
                startPosition = thirdArg()
            }
            every { mock.currentMediaItemIndex } answers { index }
            every { mock.hasNextMediaItem() } answers { index < items.lastIndex }
            every { mock.seekToNextMediaItem() } answers { index += 1 }
            every { mock.seekToDefaultPosition(any<Int>()) } answers { index = firstArg() }
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

    private fun storePlaylist(
        clipCount: Int = 3,
        shuffle: Boolean = false,
        advanceSeconds: Int = 0,
        order: List<Int> = (0 until clipCount).toList(),
    ): List<File> {
        val files = (0 until clipCount).map { File(context.filesDir, "clip$it.mp4").apply { writeBytes(ByteArray(64)) } }
        val playlist = VideoPlaylist(
            clips = order.map { index ->
                VideoPlaylistClip(files[index].path, files[index].path, files[index].name, VIDEO_WALLPAPER_SCALE_MODE_ZOOM)
            },
            shuffle = shuffle,
            advanceSeconds = advanceSeconds,
        )
        prefs().edit()
            .putString(VIDEO_WALLPAPER_PLAYLIST_PREF, encodeVideoPlaylist(playlist))
            .putBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, true)
            .commit()
        return files
    }

    /** Time on screen with a decoder that keeps drawing, so only the timer can move the list. */
    private fun FakePlayer.playFor(ms: Long) = runWatchdog(ms) { counters.renderedOutputBufferCount += 30 }

    @Test
    fun `a playlist plays every clip on one player`() {
        val files = storePlaylist()

        engine().showOnScreen()

        val player = players.single()
        assertEquals(files.map { Uri.fromFile(it) }, player.items.map { it.localConfiguration?.uri })
        assertEquals(0, player.index)
        assertEquals(0L, player.startPosition)
        assertTrue(player.wantsToPlay)
        verify { player.mock.repeatMode = Player.REPEAT_MODE_ALL }
        verify { player.mock.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING }
        verify {
            player.mock.preloadConfiguration = match { it.targetPreloadDurationUs == VIDEO_PLAYLIST_PRELOAD_US }
        }
        verify { player.mock.prepare() }
        verify(exactly = 0) { player.mock.setMediaItem(any<MediaItem>(), any<Long>()) }
    }

    @Test
    fun `shuffle reaches the player`() {
        storePlaylist(shuffle = true)

        engine().showOnScreen()

        verify { players.single().mock.shuffleModeEnabled = true }
    }

    @Test
    fun `the speed setting and the battery saver govern a playlist too`() {
        storePlaylist()
        context.getSharedPreferences(VIDEO_PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(VIDEO_PLAYBACK_SPEED_PREF, 1.5f).commit()

        engine().showOnScreen()
        val player = players.single()
        verify { player.mock.setPlaybackSpeed(1.5f) }

        setPowerSave(true)
        assertFalse(player.wantsToPlay)

        setPowerSave(false)
        assertTrue(player.wantsToPlay)
        assertEquals(1, players.size)
    }

    @Test
    fun `the timer moves to the next clip at the interval and loops one clip until then`() {
        storePlaylist(advanceSeconds = 30)

        engine().showOnScreen()
        val player = players.single()
        verify { player.mock.repeatMode = Player.REPEAT_MODE_ONE }

        player.playFor(28_000L)
        assertEquals(0, player.index)

        player.playFor(2_000L)
        assertEquals(1, player.index)
        assertEquals("moving on must reuse the same player", 1, players.size)
    }

    @Test
    fun `the timer wraps from the last clip back to the first`() {
        storePlaylist(clipCount = 2, advanceSeconds = 30)

        engine().showOnScreen()
        val player = players.single()

        player.playFor(30_000L)
        assertEquals(1, player.index)

        player.playFor(30_000L)
        assertEquals(0, player.index)
        verify { player.mock.seekToDefaultPosition(0) }
    }

    @Test
    fun `the timer only counts time on screen`() {
        storePlaylist(advanceSeconds = 30)
        val engine = engine()
        engine.showOnScreen()
        val player = players.single()

        player.playFor(10_000L)
        engine.onVisibilityChanged(false)
        idleFor(600_000L)
        assertEquals("a hidden wallpaper keeps its clip", 0, player.index)

        engine.onVisibilityChanged(true)
        player.playFor(18_000L)
        assertEquals(0, player.index)

        player.playFor(2_000L)
        assertEquals(1, player.index)
    }

    @Test
    fun `battery saver stops the timer and lets it finish afterwards`() {
        storePlaylist(advanceSeconds = 30)
        engine().showOnScreen()
        val player = players.single()

        player.playFor(10_000L)
        setPowerSave(true)
        idleFor(600_000L)
        assertEquals(0, player.index)

        setPowerSave(false)
        player.playFor(18_000L)
        assertEquals(0, player.index)

        player.playFor(2_000L)
        assertEquals(1, player.index)
    }

    @Test
    fun `a list with no timer leaves moving on to the player`() {
        storePlaylist()
        engine().showOnScreen()
        val player = players.single()

        player.playFor(600_000L)

        verify(exactly = 0) { player.mock.seekToNextMediaItem() }
        assertEquals(1, players.size)
    }

    @Test
    fun `a clip that fails hands over to the next one`() {
        storePlaylist()
        engine().showOnScreen()
        val first = players.single()

        first.fail()
        idleFor(VideoWallpaperRecovery.BASE_BACKOFF_MS)

        verify { first.mock.release() }
        assertEquals(2, players.size)
        assertEquals(1, players.last().index)
        assertEquals(0L, players.last().startPosition)
    }

    @Test
    fun `coming back to a playlist carries on where it was`() {
        storePlaylist(advanceSeconds = 30)
        val engine = engine()
        engine.showOnScreen()
        val player = players.single()
        player.playFor(30_000L)

        engine.onVisibilityChanged(false)
        engine.onVisibilityChanged(true)

        assertEquals(1, player.index)
        verify(exactly = 0) { player.mock.seekTo(0) }
        assertEquals("coming back must reuse the same player", 1, players.size)
        assertTrue(player.wantsToPlay)
    }

    @Test
    fun `a list edited while hidden is picked up on the way back`() {
        val files = storePlaylist()
        val engine = engine()
        engine.showOnScreen()
        val first = players.single()

        engine.onVisibilityChanged(false)
        storePlaylist(order = listOf(2, 1, 0))
        engine.onVisibilityChanged(true)
        idleFor(0)

        verify { first.mock.release() }
        assertEquals(2, players.size)
        assertEquals(
            files.reversed().map { Uri.fromFile(it) },
            players.last().items.map { it.localConfiguration?.uri },
        )
    }

    @Test
    fun `a list switched off while hidden gives way to the single video`() {
        storePlaylist()
        val engine = engine()
        engine.showOnScreen()

        engine.onVisibilityChanged(false)
        prefs().edit().putBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, false).commit()
        engine.onVisibilityChanged(true)
        idleFor(0)

        assertEquals(2, players.size)
        assertEquals(Uri.fromFile(file), players.last().item?.localConfiguration?.uri)
    }

    @Test
    fun `a surface the system described keeps its own file`() {
        storePlaylist()
        val service = Robolectric.buildService(VideoWallpaperService::class.java).create().get()

        service.VideoEngine(file.absolutePath).showOnScreen()

        val player = players.single()
        assertEquals(Uri.fromFile(file), player.item?.localConfiguration?.uri)
        assertTrue(player.items.isEmpty())
    }

    @Test
    fun `nothing outlives the engine with a timer still running`() {
        storePlaylist(advanceSeconds = 30)
        val engine = engine()
        engine.showOnScreen()
        val player = players.single()
        player.playFor(10_000L)
        assertFalse(engine.resourceSnapshot().isDrained)

        engine.onSurfaceDestroyed(holder)
        engine.onDestroy()
        idleFor(600_000L)

        verify(exactly = 1) { player.mock.release() }
        assertEquals(0, player.index)
        assertEquals(1, players.size)
        assertTrue(engine.resourceSnapshot().isDrained)
    }
}
