package com.freevibe.service

import com.freevibe.data.model.SOURCE_AVAILABILITY_AVAILABLE
import com.freevibe.data.model.SOURCE_AVAILABILITY_UNAVAILABLE
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportFormatCompatibilityTest {

    private val moshi = Moshi.Builder().build()
    private val fileAdapter = moshi.adapter(FavoritesExportFile::class.java)

    @Test
    fun `v1 wallpaper fixture round-trips with all provenance fields`() {
        val json = """
        {
          "version": 1,
          "exportedAt": 1718400000000,
          "items": [{
            "id": "wh_abc123",
            "source": "WALLHAVEN",
            "type": "WALLPAPER",
            "thumbnailUrl": "https://th.wallhaven.cc/small/ab/abc123.jpg",
            "fullUrl": "https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg",
            "name": "",
            "width": 1920,
            "height": 1080,
            "tags": "nature ||| mountains",
            "colors": "#1a1a2e ||| #16213e",
            "category": "general",
            "uploaderName": "photographer",
            "sourcePageUrl": "https://wallhaven.cc/w/abc123",
            "license": "CC BY 4.0",
            "fileSize": 2048000,
            "fileType": "image/jpeg",
            "views": 5000,
            "favoritesCount": 120,
            "addedAt": 1718400000000,
            "sourceAvailability": "AVAILABLE",
            "sourceAvailabilityReason": null
          }]
        }
        """.trimIndent()

        val parsed = fileAdapter.fromJson(json)!!
        assertEquals(1, parsed.version)
        assertEquals(1, parsed.items.size)

        val item = parsed.items[0]
        assertEquals("wh_abc123", item.id)
        assertEquals("WALLHAVEN", item.source)
        assertEquals("CC BY 4.0", item.license)
        assertEquals("https://wallhaven.cc/w/abc123", item.sourcePageUrl)
        assertEquals("photographer", item.uploaderName)

        val entity = item.toValidatedEntity()
        assertNotNull(entity)
        assertEquals("CC BY 4.0", entity!!.license)
        assertEquals("https://wallhaven.cc/w/abc123", entity.sourcePageUrl)
    }

    @Test
    fun `v1 sound fixture round-trips with license and source availability`() {
        val json = """
        {
          "version": 1,
          "exportedAt": 1718400000000,
          "items": [{
            "id": "fs_12345",
            "source": "FREESOUND",
            "type": "SOUND",
            "thumbnailUrl": "",
            "fullUrl": "https://freesound.org/data/previews/12345.mp3",
            "name": "Forest ambience",
            "duration": 15.5,
            "license": "CC0",
            "uploaderName": "naturalist",
            "sourcePageUrl": "https://freesound.org/people/naturalist/sounds/12345/",
            "sourceAvailability": "SOURCE_UNAVAILABLE",
            "sourceAvailabilityReason": "Remote source returned 404"
          }]
        }
        """.trimIndent()

        val parsed = fileAdapter.fromJson(json)!!
        val item = parsed.items[0]
        assertEquals("CC0", item.license)
        assertEquals("SOURCE_UNAVAILABLE", item.sourceAvailability)

        val entity = item.toValidatedEntity()
        assertNotNull(entity)
        assertEquals(SOURCE_AVAILABILITY_UNAVAILABLE, entity!!.sourceAvailability)
        assertEquals("Remote source returned 404", entity.sourceAvailabilityReason)
    }

    @Test
    fun `v1 minimal wallpaper fixture imports with null optional fields`() {
        val json = """
        {
          "version": 1,
          "exportedAt": 1718400000000,
          "items": [{
            "id": "px_99",
            "source": "PEXELS",
            "type": "WALLPAPER",
            "thumbnailUrl": "https://images.pexels.com/photos/99/landscape.jpeg",
            "fullUrl": "https://images.pexels.com/photos/99/landscape.jpeg"
          }]
        }
        """.trimIndent()

        val parsed = fileAdapter.fromJson(json)!!
        val item = parsed.items[0]
        assertNull(item.license)
        assertNull(item.sourcePageUrl)
        assertNull(item.uploaderName)
        assertNull(item.sourceAvailability)

        val entity = item.toValidatedEntity()
        assertNotNull(entity)
        assertEquals(SOURCE_AVAILABILITY_AVAILABLE, entity!!.sourceAvailability)
        assertNull(entity.license)
    }

    @Test
    fun `future version 2 payload with unknown fields still parses items`() {
        val json = """
        {
          "version": 2,
          "exportedAt": 1718400000000,
          "exporterApp": "AuraFuture",
          "items": [{
            "id": "wh_future",
            "source": "WALLHAVEN",
            "type": "WALLPAPER",
            "thumbnailUrl": "https://th.wallhaven.cc/small/fu/future.jpg",
            "fullUrl": "https://w.wallhaven.cc/full/fu/wallhaven-future.jpg",
            "license": "CC0",
            "actionCapabilities": {"APPLY": "ALLOWED"},
            "provenance": {"curator": "bot", "reviewedAt": 1718400000000}
          }]
        }
        """.trimIndent()

        val parsed = fileAdapter.fromJson(json)!!
        assertEquals(2, parsed.version)
        assertEquals(1, parsed.items.size)
        assertEquals("wh_future", parsed.items[0].id)
        assertEquals("CC0", parsed.items[0].license)

        val entity = parsed.items[0].toValidatedEntity()
        assertNotNull(entity)
    }

    @Test
    fun `legacy v0 plain list format still parses`() {
        val json = """
        [{
          "id": "wh_legacy",
          "source": "WALLHAVEN",
          "type": "WALLPAPER",
          "thumbnailUrl": "https://th.wallhaven.cc/small/lg/legacy.jpg",
          "fullUrl": "https://w.wallhaven.cc/full/lg/wallhaven-legacy.jpg"
        }]
        """.trimIndent()

        val listType = com.squareup.moshi.Types.newParameterizedType(
            List::class.java,
            FavoriteExportItem::class.java,
        )
        val listAdapter = moshi.adapter<List<FavoriteExportItem>>(listType)
        val items = listAdapter.fromJson(json)!!
        assertEquals(1, items.size)
        assertEquals("wh_legacy", items[0].id)

        val entity = items[0].toValidatedEntity()
        assertNotNull(entity)
    }

    @Test
    fun `community upload preserves license through export round-trip`() {
        val json = """
        {
          "version": 1,
          "exportedAt": 1718400000000,
          "items": [{
            "id": "community_sound_abc",
            "source": "COMMUNITY",
            "type": "SOUND",
            "thumbnailUrl": "",
            "fullUrl": "https://firebasestorage.googleapis.com/v0/b/x/o/sounds%2Fuid%2F123.mp3",
            "name": "My ringtone",
            "license": "CC BY-SA 4.0",
            "uploaderName": "creator42",
            "sourcePageUrl": "https://example.com/original"
          }]
        }
        """.trimIndent()

        val parsed = fileAdapter.fromJson(json)!!
        val entity = parsed.items[0].toValidatedEntity()
        assertNotNull(entity)
        assertEquals("CC BY-SA 4.0", entity!!.license)
        assertEquals("creator42", entity.uploaderName)
        assertEquals("https://example.com/original", entity.sourcePageUrl)
    }

    @Test
    fun `youtube sound preserves source page url`() {
        val json = """
        {
          "version": 1,
          "exportedAt": 1718400000000,
          "items": [{
            "id": "yt_dQw4w9WgXcQ",
            "source": "YOUTUBE",
            "type": "SOUND",
            "thumbnailUrl": "",
            "fullUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "name": "Song title",
            "license": "YouTube",
            "sourcePageUrl": "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
          }]
        }
        """.trimIndent()

        val parsed = fileAdapter.fromJson(json)!!
        val entity = parsed.items[0].toValidatedEntity()
        assertNotNull(entity)
        assertEquals("YouTube", entity!!.license)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", entity.sourcePageUrl)
    }
}
