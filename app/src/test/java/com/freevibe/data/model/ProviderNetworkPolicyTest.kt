package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderNetworkPolicyTest {

    @Test
    fun `network policies cover every source exactly once`() {
        val covered = providerNetworkPolicies.map { it.source }

        assertEquals(ContentSource.entries.toSet(), covered.toSet())
        assertEquals(covered.size, covered.toSet().size)
    }

    @Test
    fun `pixabay declares twenty four hour cache and retry policy`() {
        val policy = providerNetworkPoliciesBySource.getValue(ContentSource.PIXABAY)

        assertEquals(PROVIDER_CACHE_TTL_PIXABAY_MS, policy.requestCacheTtlMs)
        assertEquals(PROVIDER_CACHE_TTL_PIXABAY_MS, policy.mediaUrlTtlMs)
        assertEquals(RetryAfterHandling.DELTA_SECONDS, policy.retryAfterHandling)
        assertTrue(policy.hostSuffixes.contains("pixabay.com"))
        assertTrue(policy.allowsAutomaticPrefetch(30))
        assertFalse(policy.allowsAutomaticPrefetch(31))
        assertTrue(policy.allowsBatchDownload(30))
        assertFalse(policy.allowsBatchDownload(31))
    }

    @Test
    fun `freesound and openverse share retry policy lookup`() {
        val policy = providerNetworkPoliciesBySource.getValue(ContentSource.FREESOUND)

        assertEquals(RetryAfterHandling.DELTA_SECONDS, policy.retryAfterHandling)
        assertSame(policy, providerNetworkPolicyForSourceKey("freesound"))
        assertSame(policy, providerNetworkPolicyForSourceKey("openverse"))
    }

    @Test
    fun `retry host suffixes are policy derived`() {
        val hosts = providerRetryAfterHostSuffixes()

        assertTrue(hosts.contains("freesound.org"))
        assertTrue(hosts.contains("openverse.org"))
        assertTrue(hosts.contains("pixabay.com"))
        assertFalse(hosts.contains("wallhaven.cc"))
    }
}
