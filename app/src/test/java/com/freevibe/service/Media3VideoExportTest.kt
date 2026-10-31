package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class Media3VideoExportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `crop region becomes normalized device coordinates with y running up`() {
        val crop = normalizedCrop(VideoCropRegion(x = 0, y = 0, width = 540, height = 960), 1_080, 1_920)

        assertEquals(-1f, crop.left, 0.0001f)
        assertEquals(0f, crop.right, 0.0001f)
        assertEquals(0f, crop.bottom, 0.0001f)
        assertEquals(1f, crop.top, 0.0001f)
    }

    @Test
    fun `a clip window only exists when the end is past the start`() {
        assertEquals(1_000L to 3_000L, videoClipWindow(1_000L, 3_000L))
        assertEquals(0L to 3_000L, videoClipWindow(-5L, 3_000L))
        assertEquals(null, videoClipWindow(3_000L, 3_000L))
    }

    @Test
    fun `a rotated video swaps its width and height`() {
        assertEquals(1_080 to 1_920, uprightVideoSize(1_920, 1_080, 90))
        assertEquals(1_920 to 1_080, uprightVideoSize(1_920, 1_080, 180))
    }

    @Test
    fun `a failed HLS download never shows the export's own English text`() {
        assertEquals(
            "This video is over the size limit",
            hlsDownloadFailureText(context, VideoExportTooLargeException("HLS download")),
        )
        assertEquals(
            "Couldn't save this stream as an MP4",
            hlsDownloadFailureText(context, IOException("HLS download timed out")),
        )
    }

    @Test
    @Config(qualifiers = "zh")
    fun `a failed HLS download reads in Chinese on a Chinese phone`() {
        val tooLarge = hlsDownloadFailureText(context, VideoExportTooLargeException("HLS download"))
        val other = hlsDownloadFailureText(context, IOException("HLS download timed out"))

        assertEquals(context.getString(com.freevibe.R.string.video_wp_err_too_large), tooLarge)
        assertEquals(context.getString(com.freevibe.R.string.video_wp_err_hls), other)
        assertTrue(tooLarge, tooLarge.any { it.code > 0x2E80 })
        assertTrue(other, other.any { it.code > 0x2E80 })
    }
}
