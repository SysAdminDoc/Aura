package com.freevibe.service

import org.junit.Assert.assertEquals
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
}
