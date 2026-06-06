package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommunityUploadRightsTest {

    @Test
    fun `validateCommunityUploadRights normalizes supported licenses and source urls`() {
        val rights = validateCommunityUploadRights(
            license = " cc-by ",
            rightsAttested = true,
            sourceUrl = "https://example.com/source",
        )

        assertEquals("CC BY", rights.license)
        assertEquals(true, rights.rightsAttested)
        assertEquals("https://example.com/source", rights.sourceUrl)
    }

    @Test
    fun `validateCommunityUploadRights requires attestation`() {
        try {
            validateCommunityUploadRights("CC0", rightsAttested = false)
            fail("Expected missing attestation to throw")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun `validateCommunityUploadRights rejects unsupported licenses and non https sources`() {
        try {
            validateCommunityUploadRights("All Rights Reserved", rightsAttested = true)
            fail("Expected unsupported license to throw")
        } catch (_: IllegalArgumentException) {
        }

        try {
            validateCommunityUploadRights("CC0", rightsAttested = true, sourceUrl = "http://example.com/source")
            fail("Expected non-HTTPS source URL to throw")
        } catch (_: IllegalArgumentException) {
        }
    }
}
