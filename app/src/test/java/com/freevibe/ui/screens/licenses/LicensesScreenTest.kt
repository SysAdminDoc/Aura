package com.freevibe.ui.screens.licenses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicensesScreenTest {
    @Test
    fun releaseNoticeLinksExposeGeneratedReleaseArtifacts() {
        val names = releaseNoticeLinks.map { it.name }

        assertEquals(
            listOf(
                "Generated dependency notices",
                "Raw Google OSS inputs",
                "Native compliance packet",
            ),
            names,
        )
        assertTrue(releaseNoticeLinks.all { it.url == "https://github.com/SysAdminDoc/Aura/releases/latest" })
        assertTrue(releaseNoticeLinks.all { it.license == "Release artifact" })
        assertTrue(releaseNoticeLinks.any { it.description.contains("THIRD-PARTY-NOTICES.md") })
        assertTrue(releaseNoticeLinks.any { it.description.contains("GOOGLE-OSS-RAW-INPUTS.zip") })
        assertTrue(releaseNoticeLinks.any { it.description.contains("NATIVE-COMPLIANCE.md") })
    }

    @Test
    fun googleOssNoticeReaderParsesMetadataRanges() {
        val apache = "http://www.apache.org/licenses/LICENSE-2.0.txt\n"
        val mit = "MIT License\nPermission is hereby granted.\n"
        val bytes = (apache + mit).toByteArray(Charsets.UTF_8)
        val apacheBytes = apache.toByteArray(Charsets.UTF_8)
        val mitBytes = mit.toByteArray(Charsets.UTF_8)
        val metadata = """
            0:${apacheBytes.size} Activity
            ${apacheBytes.size}:${mitBytes.size} Moshi
            999:10 Broken
        """.trimIndent()

        val notices = GoogleOssNoticeReader.parse(
            metadataText = metadata,
            licenseBytes = bytes,
        )

        assertEquals(2, notices.size)
        assertEquals("Activity", notices[0].name)
        assertEquals("Apache 2.0", notices[0].licenseLabel)
        assertEquals(apache.trim(), notices[0].licenseText)
        assertEquals("Moshi", notices[1].name)
        assertEquals("MIT", notices[1].licenseLabel)
        assertEquals(mit.trim(), notices[1].licenseText)
    }
}
