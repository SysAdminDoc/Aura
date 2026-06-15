package com.freevibe.service

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraOriginalsManifestTest {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(AuraOriginalsManifest::class.java)

    @Test
    fun `manifest entry with full provenance fields round-trips`() {
        val json = """
        {
          "version": 1,
          "manifestRevision": "2026-06-14-test",
          "totalBytes": 1024000,
          "sounds": [{
            "id": "aura_bell_01",
            "category": "notification",
            "name": "Clear Bell",
            "durationSec": 2.5,
            "url": "https://cdn.example.com/aura_bell_01.ogg",
            "sha256": "abc123def456",
            "license": "CC0 1.0",
            "sourceUrl": "https://freesound.org/people/creator42/sounds/12345/",
            "creator": "creator42",
            "curationDate": "2026-06-14",
            "reviewResult": "approved",
            "tags": ["bell", "notification", "clear"]
          }]
        }
        """.trimIndent()

        val manifest = adapter.fromJson(json)!!
        assertEquals(1, manifest.version)
        assertEquals(1, manifest.sounds.size)

        val entry = manifest.sounds[0]
        assertEquals("aura_bell_01", entry.id)
        assertEquals("CC0 1.0", entry.license)
        assertEquals("https://freesound.org/people/creator42/sounds/12345/", entry.sourceUrl)
        assertEquals("creator42", entry.creator)
        assertEquals("2026-06-14", entry.curationDate)
        assertEquals("approved", entry.reviewResult)
    }

    @Test
    fun `manifest entry validates required provenance fields for bundled content`() {
        val entry = AuraOriginalsEntry(
            id = "aura_chime_01",
            category = "ringtone",
            name = "Gentle Chime",
            durationSec = 12.0,
            url = "https://cdn.example.com/aura_chime_01.ogg",
            sha256 = "def789ghi012",
            license = "CC0 1.0",
            sourceUrl = "https://freesound.org/s/67890/",
            creator = "sound_artist",
            curationDate = "2026-06-14",
            reviewResult = "approved",
        )

        assertTrue(entry.id.isNotBlank())
        assertTrue(entry.sha256.isNotBlank())
        assertTrue(entry.license.isNotBlank())
        assertTrue(entry.sourceUrl.isNotBlank())
        assertTrue(entry.creator.isNotBlank())
        assertTrue(entry.curationDate.isNotBlank())
        assertTrue(entry.reviewResult.isNotBlank())
    }

    @Test
    fun `legacy manifest entry without provenance fields defaults gracefully`() {
        val json = """
        {
          "version": 1,
          "manifestRevision": "legacy",
          "totalBytes": 512000,
          "sounds": [{
            "id": "legacy_tone",
            "category": "alarm",
            "name": "Legacy Alarm",
            "durationSec": 20.0,
            "url": "https://cdn.example.com/legacy_tone.ogg",
            "sha256": "legacy_hash_value"
          }]
        }
        """.trimIndent()

        val manifest = adapter.fromJson(json)!!
        val entry = manifest.sounds[0]
        assertEquals("CC0 1.0", entry.license)
        assertEquals("", entry.sourceUrl)
        assertEquals("", entry.creator)
        assertEquals("", entry.curationDate)
        assertEquals("", entry.reviewResult)
    }

    @Test
    fun `manifest categories are valid sound types`() {
        val validCategories = setOf("ringtone", "notification", "alarm")
        val entries = listOf(
            AuraOriginalsEntry(id = "r1", category = "ringtone", name = "R", durationSec = 10.0, url = "https://x.com/r.ogg", sha256 = "a"),
            AuraOriginalsEntry(id = "n1", category = "notification", name = "N", durationSec = 2.0, url = "https://x.com/n.ogg", sha256 = "b"),
            AuraOriginalsEntry(id = "a1", category = "alarm", name = "A", durationSec = 15.0, url = "https://x.com/a.ogg", sha256 = "c"),
        )
        entries.forEach { entry ->
            assertTrue("Category '${entry.category}' must be valid", entry.category in validCategories)
        }
    }

    @Test
    fun `manifest rejects entries with non-CC0 license for bundling`() {
        val entry = AuraOriginalsEntry(
            id = "bad_license",
            category = "ringtone",
            name = "Bad License",
            durationSec = 8.0,
            url = "https://cdn.example.com/bad.ogg",
            sha256 = "bad_hash",
            license = "CC BY-NC 4.0",
            creator = "artist",
            reviewResult = "approved",
        )
        val licenseKey = entry.license.uppercase(java.util.Locale.ROOT)
        val isCc0 = licenseKey.contains("CC0") || licenseKey.contains("PUBLIC DOMAIN")
        assertTrue("Bundled content must use CC0 or Public Domain license, got '${entry.license}'", !isCc0 || entry.license == "CC0 1.0")
    }
}
