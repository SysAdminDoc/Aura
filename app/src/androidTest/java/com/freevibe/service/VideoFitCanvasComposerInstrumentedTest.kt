package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@LargeTest
class VideoFitCanvasComposerInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val testAssets get() = instrumentation.context.assets
    private val inputFile get() = File(context.cacheDir, "fit_canvas_input.mp4")

    @After
    fun cleanGeneratedMedia() {
        inputFile.delete()
        File(context.filesDir, "live_wallpaper.fit.mp4").delete()
        File(context.filesDir, VIDEO_WALLPAPER_CANVAS_BACKGROUND_FILE).delete()
    }

    @Test
    fun media3BuildsAndValidatesOneCachedCanvasVideo() = runBlocking {
        withContext(Dispatchers.IO) {
            testAssets.open("fit_canvas_input.mp4").use { source ->
                inputFile.outputStream().use { destination -> source.copyTo(destination) }
            }
        }

        val prepared = VideoFitCanvasComposer(context).prepareAtSize(
            file = inputFile,
            style = FitCanvasStyle(FitCanvasMode.BLURRED_EDGE),
            targetWidth = 180,
            targetHeight = 400,
        ).getOrThrow()

        assertTrue(prepared.bakedIntoVideo)
        assertTrue(prepared.file.exists() && prepared.file.length() > 1_024L)
        assertTrue(prepared.backgroundFile.exists() && prepared.backgroundFile.length() > 0L)
        assertEquals(FitCanvasMode.BLURRED_EDGE, prepared.resolvedCanvas.mode)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(prepared.file.absolutePath)
            assertEquals(
                180,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt(),
            )
            assertEquals(
                400,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt(),
            )
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            assertTrue(durationMs in 1_000L..1_500L)
        } finally {
            retriever.release()
        }
    }

    @Test
    fun canvasShowsAroundTheVideoAndTheVideoShowsThroughIt() = runBlocking {
        withContext(Dispatchers.IO) {
            testAssets.open("fit_canvas_input.mp4").use { source ->
                inputFile.outputStream().use { destination -> source.copyTo(destination) }
            }
        }
        val canvas = Color.rgb(0, 200, 0)

        val prepared = VideoFitCanvasComposer(context).prepareAtSize(
            file = inputFile,
            style = FitCanvasStyle(FitCanvasMode.CUSTOM_COLOR, customColor = canvas),
            targetWidth = 180,
            targetHeight = 400,
        ).getOrThrow()

        val frame = firstFrame(prepared.file)
        try {
            assertEquals(180, frame.width)
            assertEquals(400, frame.height)
            assertTrue(isNear(canvas, frame.getPixel(90, 10)))
            assertTrue(isNear(canvas, frame.getPixel(90, 390)))
            assertFalse(isNear(canvas, frame.getPixel(90, 200)))
        } finally {
            frame.recycle()
        }
    }

    private fun firstFrame(file: File): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            checkNotNull(retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC))
        } finally {
            retriever.release()
        }
    }

    private fun isNear(expected: Int, actual: Int): Boolean =
        abs(Color.red(expected) - Color.red(actual)) < 50 &&
            abs(Color.green(expected) - Color.green(actual)) < 50 &&
            abs(Color.blue(expected) - Color.blue(actual)) < 50
}
