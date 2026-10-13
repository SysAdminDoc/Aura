package com.freevibe.service

import com.squareup.moshi.Moshi
import com.freevibe.data.model.FitCanvasMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryExporterFormatTest {

    private val adapter = Moshi.Builder().build().adapter(LibraryExportFile::class.java)

    @Test
    fun `current library export omits device local downloads`() {
        val json = adapter.toJson(
            LibraryExportFile(
                favorites = listOf(
                    FavoriteExportEntry(
                        id = "wallpaper-1",
                        source = "WALLHAVEN",
                        type = "WALLPAPER",
                    ),
                ),
            ),
        )

        assertTrue(json.contains("\"version\":4"))
        assertTrue(json.contains("\"favorites\""))
        assertFalse(json.contains("\"downloads\""))
        assertFalse(json.contains("localPath"))
    }

    @Test
    fun `legacy download rows are ignored while portable data still imports`() {
        val parsed = adapter.fromJson(
            """
                {
                  "version": 1,
                  "favorites": [{
                    "id": "wallpaper-1",
                    "source": "WALLHAVEN",
                    "type": "WALLPAPER"
                  }],
                  "downloads": [{
                    "id": "download-1",
                    "source": "WALLHAVEN",
                    "type": "WALLPAPER",
                    "localPath": "/storage/emulated/0/old-device.jpg"
                  }]
                }
            """.trimIndent(),
        )!!

        assertEquals(1, parsed.version)
        assertEquals(listOf("wallpaper-1"), parsed.favorites.map { it.id })
    }

    @Test
    fun `library export and import never carry provider credentials`() {
        val sentinel = "PROVIDER_CREDENTIAL_EXPORT_SENTINEL"
        val parsed = adapter.fromJson(
            """
                {
                  "version": 2,
                  "wallhaven_api_key": "$sentinel",
                  "stability_ai_key": "$sentinel",
                  "favorites": []
                }
            """.trimIndent(),
        )!!

        val exported = adapter.toJson(parsed)

        listOf(
            sentinel,
            "wallhaven_api_key",
            "pexels_api_key",
            "pixabay_api_key",
            "freesound_api_key",
            "stability_ai_key",
        ).forEach { forbidden ->
            assertFalse("Credential material reached library export: $forbidden", exported.contains(forbidden))
        }
    }

    @Test
    fun `fit canvas preferences round trip as portable settings`() {
        val original = LibraryExportFile(
            fitCanvasPreferences = FitCanvasPreferencesExport(
                staticPresentation = "fit",
                staticCanvasMode = FitCanvasMode.CUSTOM_COLOR.preferenceValue,
                staticCanvasColor = 0xFF123456.toInt(),
                videoPresentation = "fit",
                videoCanvasMode = FitCanvasMode.BLURRED_EDGE.preferenceValue,
            ),
        )

        val restored = adapter.fromJson(adapter.toJson(original))!!.fitCanvasPreferences!!

        assertEquals("fit", restored.staticPresentation)
        assertEquals(FitCanvasMode.CUSTOM_COLOR.preferenceValue, restored.staticCanvasMode)
        assertEquals(0xFF123456.toInt(), restored.staticCanvasColor)
        assertEquals(FitCanvasMode.BLURRED_EDGE, restored.toPreferences().videoStyle.mode)
    }

    @Test
    fun `rotation exclusions round trip without raw local locators`() {
        val original = LibraryExportFile(
            rotationExclusions = listOf(
                RotationExclusionExportEntry(
                    mediaType = "WALLPAPER",
                    source = "LOCAL",
                    contentId = "document-4",
                    contentHash = "ab".repeat(32),
                    title = "Family photo",
                    locatorDigest = "cd".repeat(32),
                ),
            ),
        )

        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)!!.rotationExclusions.single()

        assertFalse(json.contains("content://"))
        assertFalse(json.contains("locator\""))
        assertEquals("ab".repeat(32), restored.contentHash)
        assertEquals("cd".repeat(32), restored.locatorDigest)
    }
}
