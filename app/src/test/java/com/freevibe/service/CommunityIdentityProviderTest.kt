package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityIdentityProviderTest {
    @Test
    fun `communityIdentitySuffix redacts to last eight characters`() {
        assertEquals("cdef1234", communityIdentitySuffix("abcd-cdef1234"))
        assertEquals("short", communityIdentitySuffix("short"))
        assertEquals("Not created", communityIdentitySuffix("   "))
    }

    @Test
    fun `communityDeletionRequestCode is stable and redacted`() {
        val code = communityDeletionRequestCode("firebase-uid-123")

        assertTrue(code.startsWith("AURA-"))
        assertEquals(17, code.length)
        assertEquals(code, communityDeletionRequestCode(" firebase-uid-123 "))
        assertEquals("", communityDeletionRequestCode(" "))
    }

    @Test
    fun `communityDeletionRequestBody includes request code without full identity`() {
        val body = communityDeletionRequestBody(
            CommunityIdentitySummary(
                authLabel = "Anonymous Firebase identity",
                identitySuffix = "cdef1234",
                deletionRequestCode = "AURA-123456789ABC",
                hasFirebaseIdentity = true,
            ),
        )

        assertTrue(body.contains("Request code: AURA-123456789ABC"))
        assertTrue(body.contains("Identity suffix: ...cdef1234"))
        assertTrue(body.contains("Auth state: Anonymous Firebase identity"))
        assertFalse(body.contains("firebase-uid-123"))
    }
}
