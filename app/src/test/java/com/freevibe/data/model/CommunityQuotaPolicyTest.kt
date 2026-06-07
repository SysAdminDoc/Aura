package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            "user_blocks",
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
            assertTrue("${policy.surfaceKey} callable auth", policy.callable.requiresAuth)
            assertTrue("${policy.surfaceKey} callable App Check", policy.callable.requiresAppCheck)
        }
    }

    @Test
    fun `upload policies require storage rule coverage`() {
        assertTrue(CommunityQuotaPolicies.soundUploads.storageBacked)
        assertTrue(CommunityQuotaPolicies.wallpaperUploads.storageBacked)
    }

    @Test
    fun `callable function names are unique lower camel names`() {
        val functionNames = CommunityQuotaPolicies.all.map { it.callable.functionName }

        assertEquals(functionNames.toSet().size, functionNames.size)
        functionNames.forEach { functionName ->
            assertTrue(functionName, functionName.matches(Regex("[a-z][A-Za-z0-9]*")))
        }
    }

    @Test
    fun `callable contracts include protected ledgers and final write paths`() {
        CommunityQuotaPolicies.all.forEach { policy ->
            assertTrue("${policy.surfaceKey} payload", policy.callable.payloadSchema.isNotBlank())
            assertTrue(
                "${policy.surfaceKey} final writes",
                policy.callable.finalWritePaths.all { it.startsWith("/") },
            )
            assertTrue("${policy.surfaceKey} quota ledger", policy.quotaLedgerPath in policy.callableWritePaths)
            assertTrue("${policy.surfaceKey} dedupe ledger", policy.dedupeLedgerPath in policy.callableWritePaths)
        }
    }

    @Test
    fun `publish and report callables require limited use App Check tokens`() {
        assertTrue(CommunityQuotaPolicies.reports.callable.consumeLimitedUseAppCheckToken)
        assertTrue(CommunityQuotaPolicies.soundUploads.callable.consumeLimitedUseAppCheckToken)
        assertTrue(CommunityQuotaPolicies.wallpaperUploads.callable.consumeLimitedUseAppCheckToken)
        assertFalse(CommunityQuotaPolicies.votes.callable.consumeLimitedUseAppCheckToken)
        assertFalse(CommunityQuotaPolicies.follows.callable.consumeLimitedUseAppCheckToken)
        assertFalse(CommunityQuotaPolicies.userBlocks.callable.consumeLimitedUseAppCheckToken)
        assertFalse(CommunityQuotaPolicies.profileEdits.callable.consumeLimitedUseAppCheckToken)
    }

    @Test
    fun `callable final writes match community data namespaces`() {
        assertEquals(listOf("/community_reports/{reportId}"), CommunityQuotaPolicies.reports.callable.finalWritePaths)
        assertEquals(
            listOf("/community_sounds/{uploadId}", "/owner_uploads/{uid}/sounds/{uploadId}"),
            CommunityQuotaPolicies.soundUploads.callable.finalWritePaths,
        )
        assertEquals(
            listOf("/community_wallpapers/{uploadId}", "/owner_uploads/{uid}/wallpapers/{uploadId}"),
            CommunityQuotaPolicies.wallpaperUploads.callable.finalWritePaths,
        )
        assertEquals(
            listOf("/votes/{contentId}", "/voters/{contentId}/{uid}"),
            CommunityQuotaPolicies.votes.callable.finalWritePaths,
        )
        assertEquals(listOf("/creator_follows/{uid}/{creatorId}"), CommunityQuotaPolicies.follows.callable.finalWritePaths)
        assertEquals(
            listOf("/community_user_blocks/{uid}/{blockedUid}", "/community_blocked_by/{blockedUid}/{uid}"),
            CommunityQuotaPolicies.userBlocks.callable.finalWritePaths,
        )
        assertEquals(listOf("/creator_profiles/{uid}"), CommunityQuotaPolicies.profileEdits.callable.finalWritePaths)
    }
}
