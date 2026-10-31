package com.freevibe.service

import android.content.Context
import android.graphics.ImageFormat
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player the video wallpaper really uses, on real decoders, drawing into a surface
 * this test can read. `wallpaper_loop_with_audio.mp4` is 2 seconds of a bright moving
 * pattern at 30 fps with a sine tone, and none of its frames is dark, so a dark frame
 * here can only come from the player.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@LargeTest
class VideoWallpaperPlayerInstrumentedTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    private data class Frame(val atMs: Long, val brightness: Double)

    private class Playback(
        val frames: List<Frame>,
        val decoded: Int,
        val error: PlaybackException?,
        val selectedAudio: Boolean,
        val audioFormatWasNull: Boolean,
    )

    private fun clip(asset: String): File =
        File(context.cacheDir, "player-test-$asset").also { file ->
            instrumentation.context.assets.open(asset).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }

    /** Plays [file] on a loop for [playMs] and reports every frame that reached the surface. */
    private fun play(file: File, width: Int, height: Int, playMs: Long): Playback {
        val frames = CopyOnWriteArrayList<Frame>()
        val readerThread = HandlerThread("wallpaper-player-test").apply { start() }
        val reader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 4)
        reader.setOnImageAvailableListener({ source ->
            source.acquireLatestImage()?.use { image ->
                val plane = image.planes[0]
                val buffer = plane.buffer
                var sum = 0L
                var count = 0
                var row = 0
                while (row < image.height) {
                    var column = 0
                    while (column < image.width) {
                        sum += buffer.get(row * plane.rowStride + column * plane.pixelStride).toInt() and 0xFF
                        count += 1
                        column += SAMPLE_STEP
                    }
                    row += SAMPLE_STEP
                }
                frames += Frame(SystemClock.uptimeMillis(), sum.toDouble() / count)
            }
        }, Handler(readerThread.looper))

        val playerRef = AtomicReference<ExoPlayer>()
        val error = AtomicReference<PlaybackException?>()
        try {
            instrumentation.runOnMainSync {
                val player = buildVideoWallpaperPlayer(context)
                playerRef.set(player)
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(failure: PlaybackException) = error.set(failure)
                })
                player.setVideoSurface(reader.surface)
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), 0L)
                player.playWhenReady = true
                player.prepare()
            }
            Thread.sleep(playMs)
            var decoded = 0
            var selectedAudio = false
            var audioFormatWasNull = false
            instrumentation.runOnMainSync {
                val player = playerRef.get()
                decoded = player.decodedFrameCount()
                selectedAudio = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                audioFormatWasNull = player.audioFormat == null
            }
            return Playback(frames.toList(), decoded, error.get(), selectedAudio, audioFormatWasNull)
        } finally {
            instrumentation.runOnMainSync { playerRef.get()?.release() }
            reader.close()
            readerThread.quitSafely()
            file.delete()
        }
    }

    @Test
    fun loopingHasNoDarkFrameAndNoLongGapAtTheSeam() {
        val playback = play(clip(LOOP_CLIP), 320, 180, playMs = LOOPS * CLIP_MS + STARTUP_MS)

        assertNull("the clip must play: " + playback.error, playback.error)
        val frames = playback.frames
        assertTrue("only " + frames.size + " frames reached the surface", frames.size > LOOPS * CLIP_FRAMES / 2)

        val darkest = frames.minOf { it.brightness }
        val gaps = frames.zipWithNext { a, b -> b.atMs - a.atMs }
        val longest = gaps.max()
        Log.i(
            TAG,
            "frames=" + frames.size + " decoded=" + playback.decoded + " darkest=" + darkest +
                " longestGapMs=" + longest + " over " + LOOPS + " loops",
        )
        assertTrue("a dark frame reached the surface, brightness " + darkest, darkest > DARK_BELOW)
        assertTrue("the picture stood still for " + longest + " ms", longest < LONGEST_GAP_MS)
    }

    @Test
    fun aClipsAudioIsNeverSelected() {
        val playback = play(clip(LOOP_CLIP), 320, 180, playMs = 1_000L)

        assertNull(playback.error)
        assertFalse("the audio track must not be selected", playback.selectedAudio)
        assertTrue("no audio format may be active", playback.audioFormatWasNull)
    }

    @Test
    fun theFrameCountMovesWhilePlaying() {
        val playback = play(clip(LOOP_CLIP), 320, 180, playMs = 2_000L)

        assertNull(playback.error)
        assertTrue("the watchdog reads this count, and it was " + playback.decoded, playback.decoded > 0)
    }

    @Test
    fun aRotatedClipPlays() {
        val playback = play(clip(ROTATED_CLIP), 180, 320, playMs = 2_000L)

        assertNull(playback.error)
        assertTrue(playback.frames.isNotEmpty())
    }

    @Test
    fun aFileThatIsNotAVideoReportsAnError() {
        val notVideo = File(context.cacheDir, "player-test-not-video.mp4").apply { writeBytes(ByteArray(4_096)) }

        val playback = play(notVideo, 320, 180, playMs = 1_500L)

        assertNotNull("the engine's recovery depends on hearing about this", playback.error)
    }

    private companion object {
        const val TAG = "VideoWallpaperPlayerTest"
        const val LOOP_CLIP = "wallpaper_loop_with_audio.mp4"
        const val ROTATED_CLIP = "crop_rotated_input.mp4"
        const val LOOPS = 4
        const val CLIP_MS = 2_000L
        const val CLIP_FRAMES = 60
        const val STARTUP_MS = 1_000L
        const val SAMPLE_STEP = 8
        const val DARK_BELOW = 30.0
        const val LONGEST_GAP_MS = 250L
    }
}
