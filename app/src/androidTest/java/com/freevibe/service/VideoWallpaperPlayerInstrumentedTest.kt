package com.freevibe.service

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Surface
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        val moves: Int,
    )

    private fun clip(asset: String, name: String = asset): File =
        File(context.cacheDir, "player-test-$name").also { file ->
            instrumentation.context.assets.open(asset).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }

    /** Plays [file] on a loop for [playMs] and reports every frame that reached the surface. */
    private fun play(file: File, width: Int, height: Int, playMs: Long): Playback =
        play(listOf(file), width, height, playMs) { it.repeatMode = Player.REPEAT_MODE_ONE }

    /** Plays [files] as one list, moving on by hand every [moveEveryMs] when that's given. */
    private fun play(
        files: List<File>,
        width: Int,
        height: Int,
        playMs: Long,
        moveEveryMs: Long = 0L,
        setUp: (ExoPlayer) -> Unit,
    ): Playback {
        val frames = CopyOnWriteArrayList<Frame>()
        val reader = SurfaceBrightnessReader(width, height) { brightness ->
            frames += Frame(SystemClock.uptimeMillis(), brightness)
        }

        val playerRef = AtomicReference<ExoPlayer>()
        val error = AtomicReference<PlaybackException?>()
        val moves = AtomicInteger()
        try {
            instrumentation.runOnMainSync {
                val player = buildVideoWallpaperPlayer(context)
                playerRef.set(player)
                player.addListener(object : Player.Listener {
                    override fun onPlayerError(failure: PlaybackException) = error.set(failure)

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                            reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                        ) {
                            moves.incrementAndGet()
                        }
                    }
                })
                player.setVideoSurface(reader.surface)
                setUp(player)
                player.setMediaItems(files.map { MediaItem.fromUri(Uri.fromFile(it)) }, 0, 0L)
                player.playWhenReady = true
                player.prepare()
            }
            if (moveEveryMs > 0L) {
                var left = playMs
                while (left > moveEveryMs) {
                    Thread.sleep(moveEveryMs)
                    left -= moveEveryMs
                    instrumentation.runOnMainSync { playerRef.get().moveToNextPlaylistClip() }
                }
                Thread.sleep(left)
            } else {
                Thread.sleep(playMs)
            }
            var decoded = 0
            var selectedAudio = false
            var audioFormatWasNull = false
            instrumentation.runOnMainSync {
                val player = playerRef.get()
                decoded = player.decodedFrameCount()
                selectedAudio = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                audioFormatWasNull = player.audioFormat == null
            }
            return Playback(frames.toList(), decoded, error.get(), selectedAudio, audioFormatWasNull, moves.get())
        } finally {
            instrumentation.runOnMainSync { playerRef.get()?.release() }
            reader.close()
            files.forEach(File::delete)
        }
    }

    private fun assertNoSeam(label: String, playback: Playback, moves: Int) {
        assertNull("the list must play: " + playback.error, playback.error)
        assertTrue("$label only moved " + playback.moves + " times", playback.moves >= moves)
        val frames = playback.frames
        assertTrue("only " + frames.size + " frames reached the surface", frames.size > 30)

        val darkest = frames.minOf { it.brightness }
        val longest = frames.zipWithNext { a, b -> b.atMs - a.atMs }.max()
        Log.i(
            TAG,
            label + " frames=" + frames.size + " moves=" + playback.moves + " darkest=" + darkest +
                " longestGapMs=" + longest,
        )
        assertTrue("$label: a dark frame reached the surface, brightness $darkest", darkest > DARK_BELOW)
        assertTrue("$label: the picture stood still for $longest ms", longest < LONGEST_GAP_MS)
    }

    private fun preloading(player: ExoPlayer, repeatMode: Int) {
        player.repeatMode = repeatMode
        player.preloadConfiguration = ExoPlayer.PreloadConfiguration(VIDEO_PLAYLIST_PRELOAD_US)
    }

    @Test
    fun aListRunningIntoItsNextClipHasNoDarkFrameOrLongGap() {
        val files = listOf(clip(LOOP_CLIP, "first.mp4"), clip(LOOP_CLIP, "second.mp4"))

        val playback = play(files, 320, 180, playMs = LOOPS * CLIP_MS + STARTUP_MS) {
            preloading(it, Player.REPEAT_MODE_ALL)
        }

        assertNoSeam("end of clip", playback, moves = LOOPS - 1)
    }

    @Test
    fun aTimerCuttingAClipShortHasNoDarkFrameOrLongGap() {
        val files = listOf(clip(LOOP_CLIP, "first.mp4"), clip(LOOP_CLIP, "second.mp4"))

        val playback = play(
            files,
            320,
            180,
            playMs = TIMED_MOVES * TIMED_MOVE_MS + STARTUP_MS,
            moveEveryMs = TIMED_MOVE_MS,
        ) {
            preloading(it, Player.REPEAT_MODE_ONE)
        }

        assertNoSeam("timer", playback, moves = TIMED_MOVES)
    }

    @Test
    fun clipsOfDifferentSizeAndFrameRateFollowEachOtherCleanly() {
        val files = listOf(clip(LOOP_CLIP), clip(ROTATED_CLIP))

        val playback = play(files, 320, 180, playMs = LOOPS * CLIP_MS + STARTUP_MS) {
            preloading(it, Player.REPEAT_MODE_ALL)
        }

        assertNoSeam("mixed clips", playback, moves = LOOPS - 1)
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
        const val TIMED_MOVES = 5
        const val TIMED_MOVE_MS = 1_300L
        const val SAMPLE_STEP = 8
        const val DARK_BELOW = 30.0
        const val LONGEST_GAP_MS = 250L
    }

    /**
     * Reads what a decoder draws through the GPU, which is how a wallpaper's surface shows it. An
     * ImageReader won't do here: on a Galaxy S25 a decoder's YUV frame hands back a plane with a null
     * pointer, and CheckJNI aborts a debuggable process as soon as it's read.
     */
    private class SurfaceBrightnessReader(
        private val width: Int,
        private val height: Int,
        private val onFrame: (Double) -> Unit,
    ) {
        private val thread = HandlerThread("wallpaper-player-test").apply { start() }
        private val handler = Handler(thread.looper)
        private val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        private val transform = FloatArray(16)
        private var display = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var pbuffer = EGL14.EGL_NO_SURFACE
        private var program = 0
        private var texture = 0
        private var transformUniform = 0
        private var closed = false
        private lateinit var surfaceTexture: SurfaceTexture
        lateinit var surface: Surface
            private set

        init {
            onReaderThread(::open)
        }

        private fun onReaderThread(block: () -> Unit) {
            val done = CountDownLatch(1)
            handler.post {
                block()
                done.countDown()
            }
            check(done.await(10, TimeUnit.SECONDS)) { "the frame reader's thread stopped answering" }
        }

        private fun open() {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "no EGL display" }
            val configs = arrayOfNulls<EGLConfig>(1)
            val found = IntArray(1)
            val wanted = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE,
            )
            check(EGL14.eglChooseConfig(display, wanted, 0, configs, 0, 1, found, 0) && found[0] > 0) { "no EGL config" }
            val contextAttributes = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
            val size = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
            pbuffer = EGL14.eglCreatePbufferSurface(display, configs[0], size, 0)
            check(EGL14.eglMakeCurrent(display, pbuffer, pbuffer, eglContext)) { "EGL context didn't start" }

            program = linkProgram()
            GLES20.glUseProgram(program)
            transformUniform = GLES20.glGetUniformLocation(program, "uTransform")
            val corners = ByteBuffer.allocateDirect(8 * Float.SIZE_BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer()
            corners.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
            val position = GLES20.glGetAttribLocation(program, "aPosition")
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, corners)

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            texture = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)

            surfaceTexture = SurfaceTexture(texture).apply {
                setDefaultBufferSize(width, height)
                setOnFrameAvailableListener({ if (!closed) readFrame() }, handler)
            }
            surface = Surface(surfaceTexture)
        }

        private fun linkProgram(): Int {
            val linked = GLES20.glCreateProgram()
            GLES20.glAttachShader(linked, compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER))
            GLES20.glAttachShader(linked, compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER))
            GLES20.glLinkProgram(linked)
            val status = IntArray(1)
            GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { GLES20.glGetProgramInfoLog(linked) }
            return linked
        }

        private fun compileShader(type: Int, source: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
            return shader
        }

        private fun readFrame() {
            surfaceTexture.updateTexImage()
            surfaceTexture.getTransformMatrix(transform)
            GLES20.glViewport(0, 0, width, height)
            GLES20.glUniformMatrix4fv(transformUniform, 1, false, transform, 0)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            pixels.clear()
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
            var sum = 0.0
            var count = 0
            var row = 0
            while (row < height) {
                var column = 0
                while (column < width) {
                    val at = (row * width + column) * 4
                    sum += 0.299 * (pixels.get(at).toInt() and 0xFF) +
                        0.587 * (pixels.get(at + 1).toInt() and 0xFF) +
                        0.114 * (pixels.get(at + 2).toInt() and 0xFF)
                    count += 1
                    column += SAMPLE_STEP
                }
                row += SAMPLE_STEP
            }
            onFrame(sum / count)
        }

        fun close() {
            onReaderThread {
                closed = true
                surfaceTexture.setOnFrameAvailableListener(null)
                surface.release()
                surfaceTexture.release()
                GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
                GLES20.glDeleteProgram(program)
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, pbuffer)
                EGL14.eglDestroyContext(display, eglContext)
                EGL14.eglReleaseThread()
            }
            thread.quitSafely()
        }

        private companion object {
            const val VERTEX_SHADER = """
                attribute vec2 aPosition;
                uniform mat4 uTransform;
                varying vec2 vCoord;
                void main() {
                    gl_Position = vec4(aPosition, 0.0, 1.0);
                    vCoord = (uTransform * vec4(aPosition * 0.5 + 0.5, 0.0, 1.0)).xy;
                }
            """
            const val FRAGMENT_SHADER = """#extension GL_OES_EGL_image_external : require
                precision mediump float;
                uniform samplerExternalOES uTexture;
                varying vec2 vCoord;
                void main() {
                    gl_FragColor = texture2D(uTexture, vCoord);
                }
            """
        }
    }
}
