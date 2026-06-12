package com.freevibe.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRulesContractTest {

    @Test
    fun `android 11 backup excludes private identity secrets database and local media`() {
        val backupRules = File("src/main/res/xml/backup_rules.xml").readText()

        requiredBackupExclusions.forEach { exclusion ->
            assertTrue(
                "Missing backup exclusion for ${exclusion.domain}:${exclusion.path}",
                backupRules.contains(exclusion.xmlSnippet()),
            )
        }
    }

    @Test
    fun `android 12 data extraction excludes the same surfaces from cloud and device transfer`() {
        val dataExtractionRules = File("src/main/res/xml/data_extraction_rules.xml").readText()

        requiredBackupExclusions.forEach { exclusion ->
            assertEquals(
                "Expected cloud and device-transfer exclusion for ${exclusion.domain}:${exclusion.path}",
                2,
                exclusion.xmlSnippet().toRegex(RegexOption.LITERAL).findAll(dataExtractionRules).count(),
            )
        }
    }
}

private data class BackupExclusion(
    val domain: String,
    val path: String,
) {
    fun xmlSnippet(): String = """<exclude domain="$domain" path="$path" />"""
}

private val requiredBackupExclusions = listOf(
    BackupExclusion("database", "freevibe.db"),
    BackupExclusion("database", "freevibe.db-journal"),
    BackupExclusion("database", "freevibe.db-shm"),
    BackupExclusion("database", "freevibe.db-wal"),
    BackupExclusion("file", "datastore/freevibe_prefs.preferences_pb"),
    BackupExclusion("file", "crash.log"),
    BackupExclusion("file", "offline_favorites/"),
    BackupExclusion("file", "ai_wallpapers/"),
    BackupExclusion("file", "aura_originals/"),
    BackupExclusion("file", "parallax/"),
    BackupExclusion("file", "live_wallpaper.mp4"),
    BackupExclusion("file", "live_wallpaper.webm"),
    BackupExclusion("file", "live_wallpaper.mov"),
    BackupExclusion("file", "live_wallpaper.mkv"),
    BackupExclusion("file", "live_wallpaper.gif"),
    BackupExclusion("sharedpref", "aura_community_identity.xml"),
    BackupExclusion("sharedpref", "aura_votes.xml"),
    BackupExclusion("sharedpref", "background_work_receipts.xml"),
    BackupExclusion("sharedpref", "freevibe_dark_mode.xml"),
    BackupExclusion("sharedpref", "freevibe_live_wp.xml"),
    BackupExclusion("sharedpref", "freevibe_weather_wp.xml"),
    BackupExclusion("sharedpref", "freevibe_parallax.xml"),
    BackupExclusion("sharedpref", "freevibe_pixabay_video_cache.xml"),
    BackupExclusion("sharedpref", "freevibe_selected_content.xml"),
    BackupExclusion("sharedpref", "freevibe_video_stats.xml"),
    BackupExclusion("sharedpref", "freevibe_widget.xml"),
)
