package com.freevibe.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionExporterTest {
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
        val items = buildCollectionImportItems(
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

        assertEquals(2, items.size)
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
        assertTrue(exporter.contains("itemCount = importItems.size"))
        assertTrue(exporter.contains("json.toByteArray(Charsets.UTF_8).size > MAX_IMPORT_BYTES"))
        assertTrue(exporter.contains("file.items.size > MAX_IMPORT_ITEMS"))
        assertTrue(exporter.contains("MAX_QR_IMAGE_BYTES"))
        assertTrue(exporter.contains("readStreamCapped(input, MAX_QR_IMAGE_BYTES)"))
        assertTrue(exporter.contains("inJustDecodeBounds = true"))
        assertTrue(exporter.contains("MAX_QR_IMAGE_PIXELS"))
        assertTrue(exporter.contains("normalizeImportedContentSource(item.source, blankDefault = \"REDDIT\")"))
        assertTrue(exporter.contains("normalizeImportedHttpsUrl(item.fullUrl)"))
        assertTrue(exporter.contains("normalizeImportedHttpsUrl(item.thumbnailUrl, allowBlank = true)"))
        assertTrue(database.contains("@Transaction"))
        assertTrue(database.contains("suspend fun importCollection("))
        assertTrue(database.indexOf("@Transaction") < database.indexOf("suspend fun importCollection("))
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
