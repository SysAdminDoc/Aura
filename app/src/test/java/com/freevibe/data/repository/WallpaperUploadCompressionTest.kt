package com.freevibe.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperUploadCompressionTest {

    @Test
    fun `a wallpaper shrunk to fit reports the size it was encoded at`() {
        val source = noise(900, 1_600)
        // Just under the full-size, lowest-quality encode, so only a smaller image fits.
        val budget = jpegSize(source, quality = 68) * 8 / 10

        val compressed = compressWallpaperForUpload(source, maxBytes = budget)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(compressed.bytes, 0, compressed.bytes.size, bounds)

        assertTrue("expected a downscale", compressed.width < source.width)
        assertTrue(compressed.bytes.size <= budget)
        assertEquals(bounds.outWidth, compressed.width)
        assertEquals(bounds.outHeight, compressed.height)
        source.recycle()
    }

    @Test
    fun `a wallpaper that fits first time keeps its size`() {
        val source = noise(720, 1_280)

        val compressed = compressWallpaperForUpload(source)

        assertEquals(720, compressed.width)
        assertEquals(1_280, compressed.height)
        source.recycle()
    }

    private fun noise(width: Int, height: Int): Bitmap {
        val random = Random(7)
        val pixels = IntArray(width * height) { 0xFF000000.toInt() or random.nextInt(0x1000000) }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    private fun jpegSize(bitmap: Bitmap, quality: Int): Long =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.size().toLong()
}
