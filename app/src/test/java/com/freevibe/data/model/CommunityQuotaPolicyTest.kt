package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityQuotaPolicyTest {

    @Test
    fun `all community write surfaces have quota policies`() {
        val expected = setOf(
            "reports",
            "sound_uploads",
            "wallpaper_uploads",
            "votes",
            "follows",
            "profile_edits",
        )

        assertEquals(expected, CommunityQuotaPolicies.all.map { it.surfaceKey }.toSet())
    }

    @Test
    fun `quota ledger paths stay in protected namespaces`() {
        CommunityQuotaPolicies.all.forEach { policy ->
            assertTrue(policy.quotaLedgerPath, policy.quotaLedgerPath.startsWith("/community_write_quotas/{uid}/{yyyyMMdd}/"))
            assertTrue(policy.dedupeLedgerPath, policy.dedupeLedgerPath.startsWith("/community_write_dedupe/{uid}/"))
        }
    }

    @Test
    fun `limits are bounded and cooldowns are positive`() {
        CommunityQuotaPolicies.all.forEach { policy ->
            assertTrue("${policy.surfaceKey} daily limit", policy.dailyLimit in 1..500)
            assertTrue("${policy.surfaceKey} cooldown", policy.minIntervalMillis > 0L)
            assertTrue("${policy.surfaceKey} dedupe key", policy.dedupeKey.isNotBlank())
            assertTrue(
                "${policy.surfaceKey} callable enforcement",
                CommunityQuotaEnforcement.APP_CHECKED_CALLABLE in policy.enforcement,
            )
        }
    }

    @Test
    fun `upload policies require storage rule coverage`() {
        assertTrue(CommunityQuotaPolicies.soundUploads.storageBacked)
        assertTrue(CommunityQuotaPolicies.wallpaperUploads.storageBacked)
    }
}
