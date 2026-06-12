package com.freevibe.service

import kotlinx.coroutines.test.runTest
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.providerNetworkPoliciesBySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SourceMetricsTest {

    @Test
    fun `unrecorded source returns null snapshot`() {
        val m = SourceMetrics()
        assertNull(m.snapshot("freesound"))
    }

    @Test
    fun `recordSuccess populates totals + ratio`() {
        val m = SourceMetrics()
        m.recordSuccess("freesound", latencyMs = 120L)
        m.recordSuccess("freesound", latencyMs = 240L)
        val s = m.snapshot("freesound")!!
        assertEquals(2L, s.totalRequests)
        assertEquals(2L, s.successCount)
        assertEquals(0L, s.failureCount)
        assertEquals(1.0, s.successRatio, 0.001)
        assertNotNull(s.p50Ms)
        assertNotNull(s.p95Ms)
    }

    @Test
    fun `snapshot exposes provider quota and cache policy when source is known`() {
        val m = SourceMetrics()
        m.recordSuccess("pixabay", latencyMs = 120L)

        val s = m.snapshot("pixabay")!!

        assertEquals(providerNetworkPoliciesBySource.getValue(ContentSource.PIXABAY), s.providerPolicy)
        assertTrue(s.providerPolicy!!.diagnosticSummary.contains("request cache 1d"))
    }

    @Test
    fun `recordFailure sets last error and bumps failure count`() {
        val m = SourceMetrics()
        m.recordFailure("wallhaven", IOException("timeout"))
        val s = m.snapshot("wallhaven")!!
        assertEquals(1L, s.totalRequests)
        assertEquals(0L, s.successCount)
        assertEquals(1L, s.failureCount)
        assertEquals(1L, s.consecutiveFailureCount)
        assertEquals("IOException", s.lastErrorClass)
        assertEquals("timeout", s.lastErrorMessage)
        assertEquals(0.0, s.successRatio, 0.001)
        assertFalse(s.isPersistentlyFailing)
    }

    @Test
    fun `recordFailure redacts provider credentials before snapshot storage`() {
        val m = SourceMetrics()
        m.recordFailure(
            "wallhaven",
            IOException(
                "GET https://wallhaven.cc/api/v1/search?apikey=WALLHAVEN_SECRET&q=forest failed; " +
                    "Authorization: Bearer PEXELS_SECRET; local.properties stability.ai.key=STABILITY_SECRET",
            ),
        )

        val message = m.snapshot("wallhaven")!!.lastErrorMessage!!

        assertFalse(message.contains("WALLHAVEN_SECRET"))
        assertFalse(message.contains("PEXELS_SECRET"))
        assertFalse(message.contains("STABILITY_SECRET"))
        assertTrue(message.contains("apikey=<redacted>"))
        assertTrue(message.contains("authorization=<redacted>"))
        assertTrue(message.contains("stability.ai.key=<redacted>"))
    }

    @Test
    fun `cancellation is excluded from failure stats`() {
        val m = SourceMetrics()
        m.recordSuccess("freesound", 50L)
        m.recordFailure("freesound", kotlinx.coroutines.CancellationException("user backed out"))
        val s = m.snapshot("freesound")!!
        // Cancellation must NOT show up — it's structured-concurrency teardown,
        // not a "source failed" signal.
        assertEquals(1L, s.totalRequests)
        assertEquals(1L, s.successCount)
        assertEquals(0L, s.failureCount)
        assertNull(s.lastErrorClass)
    }

    @Test
    fun `disabled source is tracked separately from outage`() {
        val m = SourceMetrics()
        m.recordDisabled("youtube")
        val s = m.snapshot("youtube")!!
        assertEquals(1L, s.totalRequests)
        assertEquals(0L, s.successCount)
        assertEquals(0L, s.failureCount)
        assertEquals(0L, s.consecutiveFailureCount)
        assertEquals(1L, s.disabledCount)
        assertEquals(0L, s.activeRequests)
        assertEquals(1.0, s.successRatio, 0.001)
        assertNull(s.lastErrorClass)
    }

    @Test
    fun `persistent failure starts after ten consecutive failures and resets on success`() {
        val m = SourceMetrics()
        repeat(9) { m.recordFailure("reddit", IOException("403")) }

        var s = m.snapshot("reddit")!!
        assertEquals(9L, s.consecutiveFailureCount)
        assertFalse(s.isPersistentlyFailing)

        m.recordFailure("reddit", IOException("403"))
        s = m.snapshot("reddit")!!
        assertEquals(10L, s.consecutiveFailureCount)
        assertTrue(s.isPersistentlyFailing)

        m.recordSuccess("reddit", 80L)
        s = m.snapshot("reddit")!!
        assertEquals(0L, s.consecutiveFailureCount)
        assertFalse(s.isPersistentlyFailing)
    }

    @Test
    fun `disabled source names are silently ignored`() {
        val m = SourceMetrics()
        m.recordDisabled("")
        m.recordDisabled("   ")
        assertTrue(m.snapshotAll().isEmpty())
    }

    @Test
    fun `latency ring buffer caps at 50 samples`() {
        val m = SourceMetrics()
        repeat(75) { m.recordSuccess("wallhaven", it.toLong()) }
        val s = m.snapshot("wallhaven")!!
        assertEquals(75L, s.totalRequests)
        // Newest 50 retained — the oldest 25 (latencies 0..24) evicted.
        assertEquals(50, s.recentLatenciesMs.size)
        assertTrue("oldest retained sample is the 26th call", s.recentLatenciesMs.first() >= 25L)
    }

    @Test
    fun `negative latency clamped to zero`() {
        val m = SourceMetrics()
        m.recordSuccess("freesound", latencyMs = -50L)
        val s = m.snapshot("freesound")!!
        assertEquals(listOf(0L), s.recentLatenciesMs)
    }

    @Test
    fun `snapshotAll orders by failure count desc then total desc`() {
        val m = SourceMetrics()
        m.recordSuccess("wallhaven", 100L)
        m.recordSuccess("wallhaven", 100L)
        m.recordFailure("freesound", IOException("x"))
        m.recordFailure("freesound", IOException("y"))
        m.recordSuccess("reddit", 100L)

        val all = m.snapshotAll().map { it.source }
        // freesound (2 failures) first; wallhaven (2 reqs, 0 failures) before reddit (1 req).
        assertEquals(listOf("freesound", "wallhaven", "reddit"), all)
    }

    @Test
    fun `snapshotAll promotes persistent failures before transient failures`() {
        val m = SourceMetrics()
        repeat(11) { m.recordFailure("reddit", IOException("403")) }
        repeat(20) { m.recordFailure("wallhaven", IOException("timeout")) }
        m.recordSuccess("wallhaven", 100L)

        val all = m.snapshotAll().map { it.source }

        assertEquals(listOf("reddit", "wallhaven"), all.take(2))
    }

    @Test
    fun `reset clears everything`() {
        val m = SourceMetrics()
        m.recordSuccess("wallhaven", 100L)
        m.recordFailure("freesound", IOException("x"))
        m.reset()
        assertNull(m.snapshot("wallhaven"))
        assertNull(m.snapshot("freesound"))
        assertTrue(m.snapshotAll().isEmpty())
    }

    @Test
    fun `blank source name is silently ignored`() {
        val m = SourceMetrics()
        m.recordSuccess("", 100L)
        m.recordFailure("   ", IOException("x"))
        assertTrue(m.snapshotAll().isEmpty())
    }

    @Test
    fun `measure records success and live update tick`() = runTest {
        val m = SourceMetrics()
        val result = m.measure("youtube") { "ok" }
        assertEquals("ok", result)
        assertEquals(1L, m.version.value)
        assertEquals(1L, m.snapshot("youtube")!!.successCount)
    }

    @Test
    fun `measure records failure and rethrows`() = runTest {
        val m = SourceMetrics()
        try {
            m.measure("youtube") { throw IOException("offline") }
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }
        assertEquals(1L, m.version.value)
        assertEquals(1L, m.snapshot("youtube")!!.failureCount)
    }

    @Test
    fun `reset publishes live update tick`() {
        val m = SourceMetrics()
        m.recordSuccess("wallhaven", 100L)
        assertEquals(1L, m.version.value)
        m.reset()
        assertEquals(2L, m.version.value)
        assertTrue(m.snapshotAll().isEmpty())
    }
}
