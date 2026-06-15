package com.freevibe.service

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import com.freevibe.data.model.ProviderNetworkPolicy
import com.freevibe.data.model.providerNetworkPolicyForSourceKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceMetrics private constructor(
    private val prefs: SharedPreferences?,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )

    internal constructor() : this(prefs = null)

    /** Snapshot of one source's stats, taken at read time. */
    data class SourceStats(
        val source: String,
        val totalRequests: Long,
        val successCount: Long,
        val failureCount: Long,
        val disabledCount: Long,
        val lastErrorClass: String?,
        val lastErrorMessage: String?,
        val lastSuccessAtMs: Long,
        val lastFailureAtMs: Long,
        val lastDisabledAtMs: Long,
        val consecutiveFailureCount: Long,
        val recentLatenciesMs: List<Long>,
        val providerPolicy: ProviderNetworkPolicy? = providerNetworkPolicyForSourceKey(source),
    ) {
        /** Successes / active provider attempts. Disabled decisions are not outages. */
        val activeRequests: Long = (totalRequests - disabledCount).coerceAtLeast(0L)
        val successRatio: Double = if (activeRequests == 0L) 1.0
            else successCount.toDouble() / activeRequests.toDouble()

        /** Median latency over the rolling window, or null if empty. */
        val p50Ms: Long? = recentLatenciesMs.takeIf { it.isNotEmpty() }
            ?.sorted()?.let { it[it.size / 2] }

        /** 95th-percentile latency over the rolling window, or null if empty. */
        val p95Ms: Long? = recentLatenciesMs.takeIf { it.isNotEmpty() }
            ?.sorted()?.let { sorted ->
                val idx = ((sorted.size - 1) * 0.95).toInt().coerceAtLeast(0)
                sorted[idx]
            }

        val isPersistentlyFailing: Boolean =
            consecutiveFailureCount >= PERSISTENT_FAILURE_THRESHOLD
    }

    private class MutableEntry {
        val total = AtomicLong(0L)
        val success = AtomicLong(0L)
        val failure = AtomicLong(0L)
        val disabled = AtomicLong(0L)
        val consecutiveFailures = AtomicLong(0L)
        @Volatile var lastErrorClass: String? = null
        @Volatile var lastErrorMessage: String? = null
        @Volatile var lastSuccessAtMs: Long = 0L
        @Volatile var lastFailureAtMs: Long = 0L
        @Volatile var lastDisabledAtMs: Long = 0L
        // Bounded ring buffer for latency samples — keeps memory bounded.
        val latencies = java.util.ArrayDeque<Long>()
        val latencyLock = Any()
    }

    private val entries = ConcurrentHashMap<String, MutableEntry>()
    private val _version = MutableStateFlow(0L)
    val version: StateFlow<Long> = _version.asStateFlow()

    suspend fun <T> measure(source: String, block: suspend () -> T): T {
        val startedAt = System.currentTimeMillis()
        return try {
            val result = block()
            recordSuccess(source, System.currentTimeMillis() - startedAt)
            result
        } catch (e: Throwable) {
            recordFailure(source, e)
            throw e
        }
    }

    /**
     * Record a successful call. [latencyMs] is the elapsed wall-clock time;
     * negative values are clamped to 0 to keep percentile math sane.
     */
    fun recordSuccess(source: String, latencyMs: Long) {
        if (source.isBlank()) return
        val e = entries.computeIfAbsent(source) { MutableEntry().also { loadPersistedState(source, it) } }
        val wasDegraded = e.consecutiveFailures.get() >= PERSISTENT_FAILURE_THRESHOLD
        e.total.incrementAndGet()
        e.success.incrementAndGet()
        e.consecutiveFailures.set(0L)
        e.lastSuccessAtMs = System.currentTimeMillis()
        synchronized(e.latencyLock) {
            e.latencies.addLast(latencyMs.coerceAtLeast(0L))
            while (e.latencies.size > MAX_LATENCY_SAMPLES) e.latencies.pollFirst()
        }
        if (wasDegraded) persistRecovery(source)
        _version.update { it + 1 }
    }

    fun recordFailure(source: String, error: Throwable) {
        if (source.isBlank()) return
        if (error is kotlinx.coroutines.CancellationException) return
        val e = entries.computeIfAbsent(source) { MutableEntry().also { loadPersistedState(source, it) } }
        e.total.incrementAndGet()
        e.failure.incrementAndGet()
        e.consecutiveFailures.incrementAndGet()
        e.lastErrorClass = error.javaClass.simpleName
        e.lastErrorMessage = error.message
            ?.let(RequestRedactor::redact)
            ?.take(200)
        e.lastFailureAtMs = System.currentTimeMillis()
        persistFailureState(source, e)
        _version.update { it + 1 }
    }

    fun recordDisabled(source: String) {
        if (source.isBlank()) return
        val e = entries.computeIfAbsent(source) { MutableEntry().also { loadPersistedState(source, it) } }
        e.total.incrementAndGet()
        e.disabled.incrementAndGet()
        e.lastDisabledAtMs = System.currentTimeMillis()
        _version.update { it + 1 }
    }

    /** Atomic-ish snapshot of one source. Returns null if never seen. */
    fun snapshot(source: String): SourceStats? {
        val e = entries[source] ?: return null
        val latencies = synchronized(e.latencyLock) { e.latencies.toList() }
        return SourceStats(
            source = source,
            totalRequests = e.total.get(),
            successCount = e.success.get(),
            failureCount = e.failure.get(),
            disabledCount = e.disabled.get(),
            lastErrorClass = e.lastErrorClass,
            lastErrorMessage = e.lastErrorMessage,
            lastSuccessAtMs = e.lastSuccessAtMs,
            lastFailureAtMs = e.lastFailureAtMs,
            lastDisabledAtMs = e.lastDisabledAtMs,
            consecutiveFailureCount = e.consecutiveFailures.get(),
            recentLatenciesMs = latencies,
        )
    }

    /** Snapshot of every recorded source, sorted by most-failed first. */
    fun snapshotAll(): List<SourceStats> = entries.keys
        .mapNotNull { snapshot(it) }
        .sortedWith(
            compareByDescending<SourceStats> { if (it.isPersistentlyFailing) 1 else 0 }
                .thenByDescending { it.failureCount }
                .thenByDescending { it.consecutiveFailureCount }
                .thenByDescending { it.disabledCount }
                .thenByDescending { it.totalRequests },
        )

    /** Forget all recorded stats (developer-facing reset). */
    fun reset() {
        entries.clear()
        prefs?.edit()?.clear()?.apply()
        _version.update { it + 1 }
    }

    fun isDegraded(source: String): Boolean {
        val e = entries[source]
        if (e != null && e.consecutiveFailures.get() >= PERSISTENT_FAILURE_THRESHOLD) {
            val elapsed = System.currentTimeMillis() - e.lastFailureAtMs
            if (elapsed < DEGRADATION_COOLDOWN_MS) return true
        }
        val persistedFailures = prefs?.getLong("${source}_consecutive_failures", 0L) ?: 0L
        if (persistedFailures >= PERSISTENT_FAILURE_THRESHOLD) {
            val persistedLastFailure = prefs?.getLong("${source}_last_failure_at", 0L) ?: 0L
            val elapsed = System.currentTimeMillis() - persistedLastFailure
            if (elapsed < DEGRADATION_COOLDOWN_MS) return true
        }
        return false
    }

    fun degradedSources(): Set<String> {
        val keys = mutableSetOf<String>()
        keys.addAll(entries.keys.filter { isDegraded(it) })
        val allPrefs = prefs?.all ?: emptyMap()
        for ((key, _) in allPrefs) {
            if (key.endsWith("_consecutive_failures")) {
                val source = key.removeSuffix("_consecutive_failures")
                if (source.isNotBlank() && isDegraded(source)) keys.add(source)
            }
        }
        return keys
    }

    private fun persistFailureState(source: String, entry: MutableEntry) {
        prefs?.edit()
            ?.putLong("${source}_consecutive_failures", entry.consecutiveFailures.get())
            ?.putLong("${source}_last_failure_at", entry.lastFailureAtMs)
            ?.putString("${source}_last_error", entry.lastErrorClass)
            ?.apply()
    }

    private fun persistRecovery(source: String) {
        prefs?.edit()
            ?.putLong("${source}_consecutive_failures", 0L)
            ?.apply()
    }

    private fun loadPersistedState(source: String, entry: MutableEntry) {
        val persisted = prefs?.getLong("${source}_consecutive_failures", 0L) ?: 0L
        if (persisted > 0L) {
            entry.consecutiveFailures.set(persisted)
            entry.lastFailureAtMs = prefs?.getLong("${source}_last_failure_at", 0L) ?: 0L
            entry.lastErrorClass = prefs?.getString("${source}_last_error", null)
        }
    }

    private companion object {
        const val PREFS_NAME = "source_metrics_degradation"
        const val MAX_LATENCY_SAMPLES = 50
        const val PERSISTENT_FAILURE_THRESHOLD = 10L
        const val DEGRADATION_COOLDOWN_MS = 24L * 60 * 60 * 1000 // 24 hours
    }
}
