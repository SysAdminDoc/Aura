package com.freevibe.service

import android.net.Uri
import com.freevibe.data.model.MAX_SHARED_COLLECTION_DOCUMENT_BYTES
import com.freevibe.data.model.MAX_SHARED_COLLECTION_ITEMS
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.squareup.moshi.Moshi
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionExporterTest {

    private val adapter = Moshi.Builder().build().adapter(CollectionExportFile::class.java)
    @Test
    fun `zxing QR export decodes back to the Aura collection link`() {
        val shareLink = "aura://collection/import/abc123_DEF-456"
        val matrix = QRCodeWriter().encode(shareLink, BarcodeFormat.QR_CODE, 256, 256)
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }

        val decoded = MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width, matrix.height, pixels))),
        )

        assertEquals(shareLink, decoded.text)
    }

    @Test
    fun `exported QR pixels decode back through the import reader`() {
        val shareLink = "aura://collection/import/abc123_DEF-456"
        val matrix = encodeCollectionQr(shareLink, 768)
        val pixels = qrPixels(matrix)
        assertEquals(matrix.width * matrix.height, pixels.size)

        val luma = qrLuminance(matrix.width, matrix.height) { y, row ->
            System.arraycopy(pixels, y * matrix.width, row, 0, matrix.width)
        }

        assertEquals(shareLink, decodeCollectionQr(luma, matrix.width, matrix.height))
    }

    @Test
    fun `QR pixels fill one array row by row`() {
        val matrix = BitMatrix(3, 2).apply {
            set(2, 0)
            set(0, 1)
        }
        val dark = 0xFF000000.toInt()
        val light = 0xFFFFFFFF.toInt()

        assertEquals(listOf(light, light, dark, dark, light, light), qrPixels(matrix).toList())
    }

    @Test
    fun `largest allowed QR import is sampled down before it is decoded`() {
        val edge = LibraryTransferContract.MAX_QR_IMAGE_DIMENSION
        // The widest image the pixel cap allows at the maximum edge.
        val height = (LibraryTransferContract.MAX_QR_IMAGE_PIXELS / edge).toInt()
        val sample = qrDecodeSampleSize(edge, height)
        val decodedPixels = (edge / sample).toLong() * (height / sample)
        // Decoded ARGB bitmap, one byte of luma a pixel, and the capped compressed file.
        val peakBytes = decodedPixels * 5 + LibraryTransferContract.MAX_QR_IMAGE_BYTES

        assertEquals(4, sample)
        assertTrue("peak $peakBytes", peakBytes < 16L * 1024 * 1024)
        assertEquals(1, qrDecodeSampleSize(768, 768))
        assertEquals(1, qrDecodeSampleSize(2_047, 1_000))
        assertEquals(2, qrDecodeSampleSize(1_000, 2_048))
    }

    @Test
    fun `an exported QR inside the largest allowed photo still decodes after sampling`() {
        val shareLink = "aura://collection/import/abc123_DEF-456"
        val edge = LibraryTransferContract.MAX_QR_IMAGE_DIMENSION
        val height = (LibraryTransferContract.MAX_QR_IMAGE_PIXELS / edge).toInt()
        val sample = qrDecodeSampleSize(edge, height)
        // The 768 px export, shrunk by the same sample, sitting off-center in the photo.
        val qr = encodeCollectionQr(shareLink, 768 / sample)
        val qrArgb = qrPixels(qr)
        val width = edge / sample
        val sampledHeight = height / sample

        val luma = qrLuminance(width, sampledHeight) { y, row ->
            row.fill(0xFFFFFFFF.toInt())
            val qrRow = y - 120
            if (qrRow in 0 until qr.height) System.arraycopy(qrArgb, qrRow * qr.width, row, 300, qr.width)
        }

        assertEquals(shareLink, decodeCollectionQr(luma, width, sampledHeight))
    }

    @Test
    fun `extractCollectionShareToken accepts Aura deep links and plain tokens`() {
        assertEquals(
            "abc123_DEF",
            extractCollectionShareToken("aura://collection/import/abc123_DEF"),
        )
        assertEquals(
            "token-123",
            extractCollectionShareToken("Share this: https://aura.app/collections/import/token-123"),
        )
        assertEquals("rawToken_42", extractCollectionShareToken("rawToken_42"))
    }

    @Test
    fun `extractCollectionShareToken rejects unsupported or unsafe input`() {
        assertNull(extractCollectionShareToken("https://example.com/import/token-123"))
        assertNull(extractCollectionShareToken("aura://collection/import/no spaces"))
        assertNull(extractCollectionShareToken("short"))
    }

    @Test
    fun `sanitizeImportedCollectionName trims whitespace and caps length`() {
        val longName = "  Travel   Walls  " + "x".repeat(120)

        val sanitized = sanitizeImportedCollectionName(longName)

        assertEquals(80, sanitized.length)
        assertEquals("Travel Walls", sanitized.take("Travel Walls".length))
    }

    @Test
    fun `buildCollectionImportItems filters unsafe urls and dedupes normalized identities`() {
        val plan = buildCollectionImportPlan(
            listOf(
                exportItem(wallpaperId = "one", source = "pexels", fullUrl = "https://example.com/one.jpg"),
                exportItem(wallpaperId = "one", source = "PEXELS", fullUrl = "https://example.com/one-dup.jpg"),
                exportItem(wallpaperId = "two", source = "", fullUrl = "https://example.com/two.jpg", thumbnailUrl = ""),
                exportItem(wallpaperId = "unsafe", source = "wallhaven", fullUrl = "http://example.com/unsafe.jpg"),
                exportItem(wallpaperId = "unknown", source = "UNKNOWN_PROVIDER", fullUrl = "https://example.com/unknown.jpg"),
                exportItem(wallpaperId = "oversized", source = "WALLHAVEN", fullUrl = "https://example.com/" + "x".repeat(2048)),
                exportItem(wallpaperId = "", source = "wallhaven", fullUrl = "https://example.com/blank.jpg"),
            ),
        )
        val items = plan.items

        assertEquals(2, items.size)
        assertEquals(1, plan.skippedCount)
        assertEquals(4, plan.failedCount)
        assertEquals("one", items[0].wallpaperId)
        assertEquals("PEXELS", items[0].source)
        assertEquals(0, items[0].width)
        assertEquals(0, items[0].height)
        assertEquals("two", items[1].wallpaperId)
        assertEquals("REDDIT", items[1].source)
        assertEquals("https://example.com/two.jpg", items[1].thumbnailUrl)
    }

    @Test
    fun `collection import uses DAO transaction boundary`() {
        val exporter = File("src/main/java/com/freevibe/service/CollectionExporter.kt").readText()
        val database = File("src/main/java/com/freevibe/data/local/Database.kt").readText()

        assertTrue(exporter.contains("collectionDao.importCollection("))
        assertTrue(exporter.contains("itemCount = importPlan.items.size"))
        assertTrue(exporter.contains("LibraryTransferContract.MAX_COLLECTION_DOCUMENT_BYTES"))
        assertTrue(exporter.contains("LibraryTransferContract.MAX_COLLECTION_ITEMS"))
        assertTrue(exporter.contains("LibraryTransferContract.MAX_QR_IMAGE_BYTES"))
        assertTrue(
            exporter.contains(
                "readStreamCapped(input, LibraryTransferContract.MAX_QR_IMAGE_BYTES)"
            )
        )
        assertTrue(exporter.contains("inJustDecodeBounds = true"))
        assertTrue(exporter.contains("inSampleSize = qrDecodeSampleSize(width, height)"))
        assertFalse("QR pixels are set in one call", exporter.contains("setPixel("))
        assertFalse("imports don't copy the whole image into an IntArray", exporter.contains("IntArray(w * h)"))
        assertTrue(exporter.contains("LibraryTransferContract.MAX_QR_IMAGE_PIXELS"))
        assertFalse(exporter.contains("items.take(LibraryTransferContract.MAX_COLLECTION_ITEMS)"))
        assertTrue(exporter.contains("normalizeImportedContentSource(item.source, blankDefault = \"REDDIT\")"))
        assertTrue(exporter.contains("normalizeImportedHttpsUrl(item.fullUrl)"))
        assertTrue(exporter.contains("normalizeImportedHttpsUrl(item.thumbnailUrl, allowBlank = true)"))
        assertTrue(database.contains("@Transaction"))
        assertTrue(database.contains("suspend fun importCollection("))
        assertTrue(database.indexOf("@Transaction") < database.indexOf("suspend fun importCollection("))
    }

    @Test
    fun `documented maximum collection round trips without loss`() {
        val expected = List(LibraryTransferContract.MAX_COLLECTION_ITEMS) { index ->
            exportItem(
                wallpaperId = "wallpaper-$index",
                source = "REDDIT",
                fullUrl = "https://preview.example/$index.jpg",
            )
        }
        val file = CollectionExportFile(
            version = 1,
            exportedAt = 1,
            collectionName = "Maximum",
            items = expected,
        )

        val restored = adapter.fromJson(adapter.toJson(file))

        assertEquals(expected, restored?.items)
        assertEquals(expected.size, buildCollectionImportPlan(expected).items.size)
    }

    @Test
    fun `collection links stop at the server item and size limits`() {
        requireShareableAsLink("{}", MAX_SHARED_COLLECTION_ITEMS)

        val tooMany = runCatching { requireShareableAsLink("{}", MAX_SHARED_COLLECTION_ITEMS + 1) }.exceptionOrNull()
        val tooBig = runCatching {
            requireShareableAsLink("x".repeat(MAX_SHARED_COLLECTION_DOCUMENT_BYTES + 1), 1)
        }.exceptionOrNull()

        assertTrue(tooMany is IllegalArgumentException)
        assertTrue(tooMany?.message.orEmpty().contains("Share it as a file"))
        assertTrue(tooBig is IllegalArgumentException)
    }

    @Test
    fun `a refused read of an old share reads as an expired link`() {
        val denied = RuntimeException("Firebase Database error: Permission denied")

        assertTrue(isFirebasePermissionDenied(denied))
        assertTrue(isFirebasePermissionDenied(IllegalStateException("wrapped", denied)))
        assertFalse(isFirebasePermissionDenied(IllegalStateException("Network error")))
        assertEquals("Collection link is expired or unavailable.", EXPIRED_COLLECTION_LINK_MESSAGE)
    }

    @Test
    fun `share text leaves the link out when only the file could be shared`() {
        val uri = mockk<Uri>(relaxed = true)

        assertEquals(
            "Aura collection: Evening\n3 wallpapers\naura://collection/import/abc12345",
            buildCollectionShareText(CollectionShareBundle(uri, "Evening", "aura://collection/import/abc12345", 3)),
        )
        assertEquals(
            "Aura collection: Evening\n3 wallpapers",
            buildCollectionShareText(CollectionShareBundle(uri, "Evening", null, 3)),
        )
    }

    private fun exportItem(
        wallpaperId: String,
        source: String,
        fullUrl: String,
        thumbnailUrl: String = "https://example.com/thumb.jpg",
        width: Int = -1,
        height: Int = -4,
    ) = CollectionExportItem(
        wallpaperId = wallpaperId,
        source = source,
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        width = width,
        height = height,
    )
}
