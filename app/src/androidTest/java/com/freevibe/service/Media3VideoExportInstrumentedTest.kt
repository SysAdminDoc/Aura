package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class Media3VideoExportInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val workDirectory get() = File(context.cacheDir, "media3_export_fixtures").apply { mkdirs() }

    @After
    fun cleanGeneratedMedia() {
        workDirectory.deleteRecursively()
    }

    @Test
    fun cropWorksOnTheUprightFrameOfARotatedVideo() = runBlocking {
        val input = copyAsset("crop_rotated_input.mp4")
        val output = File(workDirectory, "cropped.mp4")
        val source = firstFrame(input)
        val top = source.getPixel(source.width / 2, source.height / 4)
        val bottom = source.getPixel(source.width / 2, source.height * 3 / 4)
        source.recycle()
        assertFalse("The fixture has to differ top to bottom", isNear(top, bottom))

        exportCroppedVideo(
            context = context,
            input = input,
            region = VideoCropRegion(x = 0, y = 0, width = 180, height = 160),
            videoWidth = 180,
            videoHeight = 320,
            loopStartMs = 0L,
            loopEndMs = 0L,
            output = output,
        )

        assertEquals(180 to 160, readUprightVideoSize(output))
        val frame = firstFrame(output)
        try {
            assertTrue(isNear(top, frame.getPixel(frame.width / 2, frame.height / 4)))
            assertTrue(isNear(top, frame.getPixel(frame.width / 2, frame.height * 3 / 4)))
        } finally {
            frame.recycle()
        }
    }

    @Test
    fun cropKeepsOnlyTheChosenStretchAndDropsAudio() = runBlocking {
        val input = copyAsset("crop_rotated_input.mp4")
        val output = File(workDirectory, "clipped.mp4")

        exportCroppedVideo(
            context = context,
            input = input,
            region = VideoCropRegion(x = 0, y = 0, width = 180, height = 320),
            videoWidth = 180,
            videoHeight = 320,
            loopStartMs = 400L,
            loopEndMs = 1_200L,
            output = output,
        )

        assertTrue(durationMs(output) in 600L..1_000L)
        assertEquals(listOf("video"), trackFamilies(output))
    }

    @Test
    fun hlsPlaylistBecomesOneMp4WithItsTracksCopied() = runBlocking {
        val playlist = copyAsset("hls/playlist.m3u8")
        copyAsset("hls/seg0.mpegts")
        copyAsset("hls/seg1.mpegts")
        val output = File(workDirectory, "playlist.mp4")

        transmuxHlsToMp4(context, Uri.fromFile(playlist).toString(), output)

        assertEquals(64 to 64, readUprightVideoSize(output))
        assertTrue(durationMs(output) in 1_500L..2_500L)
        assertEquals(listOf("audio", "video"), trackFamilies(output).sorted())
    }

    @Test
    fun hlsDownloadOverTheSizeLimitFails() = runBlocking {
        val playlist = copyAsset("hls/playlist.m3u8")
        copyAsset("hls/seg0.mpegts")
        copyAsset("hls/seg1.mpegts")
        val output = File(workDirectory, "too_large.mp4")

        try {
            transmuxHlsToMp4(context, Uri.fromFile(playlist).toString(), output, maxBytes = 128L)
            fail("The size limit was ignored")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("limit"))
        }
    }

    /** Pass a real playlist with -e hlsUrl to check a live source by hand. */
    @Test
    fun liveHlsPlaylistFromInstrumentationArgument() = runBlocking {
        val url = InstrumentationRegistry.getArguments().getString("hlsUrl")
        assumeTrue(!url.isNullOrBlank())
        val output = File(workDirectory, "live.mp4")

        transmuxHlsToMp4(context, url!!, output)

        val (width, height) = readUprightVideoSize(output)
        assertTrue(width > 0 && height > 0 && maxOf(width, height) <= 1_920)
        assertTrue(durationMs(output) > 0L)
    }

    private suspend fun copyAsset(name: String): File = withContext(Dispatchers.IO) {
        val target = File(workDirectory, name.substringAfterLast('/'))
        instrumentation.context.assets.open(name).use { source ->
            target.outputStream().use { destination -> source.copyTo(destination) }
        }
        target
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

    private fun durationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    private fun trackFamilies(file: File): List<String> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).map { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME).orEmpty().substringBefore('/')
            }
        } finally {
            extractor.release()
        }
    }

    private fun isNear(expected: Int, actual: Int): Boolean =
        abs(Color.red(expected) - Color.red(actual)) < 50 &&
            abs(Color.green(expected) - Color.green(actual)) < 50 &&
            abs(Color.blue(expected) - Color.blue(actual)) < 50
}
