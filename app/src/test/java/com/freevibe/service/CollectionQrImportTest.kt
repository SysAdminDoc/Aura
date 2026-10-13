package com.freevibe.service

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Runs the import through the real BitmapFactory sampling and getPixels path, not a hand-built luma array. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionQrImportTest {

    private val shareLink = "aura://collection/import/abc123_DEF-456"

    @Test
    fun `an exported QR inside the largest allowed photo decodes through the import path`() {
        val edge = LibraryTransferContract.MAX_QR_IMAGE_DIMENSION
        // 4096 x 2929, the widest photo the pixel cap allows at the maximum edge.
        val height = (LibraryTransferContract.MAX_QR_IMAGE_PIXELS / edge).toInt()
        val qr = encodeCollectionQr(shareLink, 768)
        val photo = Bitmap.createBitmap(edge, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            setPixels(qrPixels(qr), 0, qr.width, 1_900, 1_300, qr.width, qr.height)
        }
        val jpeg = encode(photo, Bitmap.CompressFormat.JPEG)
        photo.recycle()

        assertTrue("under the import cap: ${jpeg.size}", jpeg.size <= LibraryTransferContract.MAX_QR_IMAGE_BYTES)
        assertEquals(4, qrDecodeSampleSize(edge, height))
        assertEquals(shareLink, decodeQrImageBytes(jpeg))
    }

    @Test
    fun `the exported QR decodes at full size`() {
        val qr = encodeCollectionQr(shareLink, 768)
        val export = Bitmap.createBitmap(qr.width, qr.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(qrPixels(qr), 0, qr.width, 0, 0, qr.width, qr.height)
        }

        assertEquals(shareLink, decodeQrImageBytes(encode(export, Bitmap.CompressFormat.PNG)))
    }

    @Test
    fun `images past a cap or not images at all are refused`() {
        val tooWide = Bitmap.createBitmap(LibraryTransferContract.MAX_QR_IMAGE_DIMENSION + 1, 4, Bitmap.Config.ARGB_8888)
        val png = encode(tooWide, Bitmap.CompressFormat.PNG)

        val wide = assertThrows(IllegalArgumentException::class.java) { decodeQrImageBytes(png) }
        assertEquals("QR image is too large to import safely.", wide.message)
        assertThrows(IllegalArgumentException::class.java) { decodeQrImageBytes(byteArrayOf(1, 2, 3)) }
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(format, 90, out)
            out.toByteArray()
        }
}
