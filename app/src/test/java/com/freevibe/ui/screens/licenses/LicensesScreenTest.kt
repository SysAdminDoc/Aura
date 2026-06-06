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
}
