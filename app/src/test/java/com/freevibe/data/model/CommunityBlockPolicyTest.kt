package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CommunityBlockPolicyTest {

    @Test
    fun `buildCommunityUserBlockUpdates writes private list and reverse index`() {
        val updates = buildCommunityUserBlockUpdates(
            blockerUid = "blocker/1",
            blockedUid = "blocked.2",
            createdAt = 123L,
            reason = CommunityBlockReason.HARASSMENT,
        )

        val payload = mapOf(
            "blockerUid" to "blocker_1",
            "blockedUid" to "blocked_2",
            "createdAt" to 123L,
            "reason" to "HARASSMENT",
        )

        assertEquals(
            mapOf(
                "/community_user_blocks/blocker_1/blocked_2" to payload,
                "/community_blocked_by/blocked_2/blocker_1" to payload,
            ),
            updates,
        )
    }

    @Test
    fun `buildCommunityUserBlockUpdates rejects self block`() {
        try {
            buildCommunityUserBlockUpdates(
                blockerUid = "same-user",
                blockedUid = "same-user",
                createdAt = 123L,
                reason = CommunityBlockReason.OTHER,
            )
            fail("Expected self block to throw")
        } catch (_: IllegalArgumentException) {
        }
    }
}
