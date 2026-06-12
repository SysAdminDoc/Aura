package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.providerNetworkPoliciesBySource
import com.freevibe.data.model.stableKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class BatchDownloadState(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val blockedCount: Int = 0,
    val isRunning: Boolean = false,
    val currentItem: String = "",
) {
    val processedCount: Int
        get() = completedCount + failedCount + blockedCount

    val progress: Float
        get() = if (totalCount > 0) processedCount.toFloat() / totalCount else 0f
    val isComplete: Boolean get() = processedCount >= totalCount && totalCount > 0
}

data class BatchDownloadStartResult(
    val acceptedCount: Int = 0,
    val blockedCount: Int = 0,
    val alreadyRunning: Boolean = false,
)

internal data class BatchDownloadPlan(
    val allowed: List<Wallpaper>,
    val blockedCount: Int,
)

@Singleton
class BatchDownloadService @Inject constructor(
    private val downloadManager: DownloadManager,
) {
    private val _state = MutableStateFlow(BatchDownloadState())
    val state = _state.asStateFlow()

    private val lock = Any()
    private var scope: CoroutineScope? = null

    fun downloadBatch(wallpapers: List<Wallpaper>, concurrency: Int = 3): BatchDownloadStartResult {
        val plan = planBatchDownloads(wallpapers)
        val newScope = synchronized(lock) {
            if (_state.value.isRunning) return BatchDownloadStartResult(alreadyRunning = true)
            // Cancel any previous scope (e.g., from a prior cancelled run that never cleared state) and
            // publish the fresh state + new scope atomically so a concurrent reset()/cancel() cannot
            // see a half-initialized state.
            scope?.cancel()
            val fresh = CoroutineScope(Dispatchers.IO + SupervisorJob())
            scope = fresh
            _state.value = BatchDownloadState(
                totalCount = wallpapers.size,
                blockedCount = plan.blockedCount,
                isRunning = plan.allowed.isNotEmpty(),
            )
            fresh
        }
        if (plan.allowed.isEmpty()) {
            synchronized(lock) {
                newScope.cancel()
                scope = null
            }
            return BatchDownloadStartResult(acceptedCount = 0, blockedCount = plan.blockedCount)
        }
        newScope.launch {
            try {
                val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)

                plan.allowed.map { wp ->
                    async {
                        semaphore.acquire()
                        try {
                            val batchId = buildBatchDownloadId(wp)
                            val ext = guessBatchExtension(wp.fileType)
                            _state.update { s -> s.copy(currentItem = batchDisplayName(wp)) }
                            try {
                                downloadManager.downloadWallpaper(
                                    id = batchId,
                                    url = wp.fullUrl,
                                    fileName = buildBatchFileName(wp, ext),
                                    source = wp.source.name,
                                ).onSuccess {
                                    _state.update { s -> s.copy(completedCount = s.completedCount + 1) }
                                }.onFailure {
                                    _state.update { s -> s.copy(failedCount = s.failedCount + 1) }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                _state.update { s -> s.copy(failedCount = s.failedCount + 1) }
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }.awaitAll()
            } catch (_: CancellationException) {
                // Scope cancelled: count all unfinished items as failed
                _state.update { s ->
                    val remaining = s.totalCount - s.completedCount - s.failedCount - s.blockedCount
                    s.copy(failedCount = s.failedCount + remaining)
                }
            } finally {
                _state.update { s -> s.copy(isRunning = false, currentItem = "") }
            }
        }
        return BatchDownloadStartResult(
            acceptedCount = plan.allowed.size,
            blockedCount = plan.blockedCount,
        )
    }

    fun cancel() {
        synchronized(lock) {
            scope?.cancel()
            scope = null
            // The coroutine's finally block handles isRunning = false.
            // Force it here too in case the scope had no active coroutine.
            _state.update { it.copy(isRunning = false, currentItem = "") }
        }
    }

    fun reset() {
        synchronized(lock) {
            // Tear down any in-flight batch before wiping progress, otherwise the running
            // coroutine keeps updating a discarded state object while the UI thinks nothing
            // is happening.
            scope?.cancel()
            scope = null
            _state.value = BatchDownloadState()
        }
    }

    private fun guessBatchExtension(fileType: String): String = when {
        fileType.contains("png", true) -> "png"
        fileType.contains("webp", true) -> "webp"
        fileType.contains("gif", true) -> "gif"
        fileType.contains("jpeg", true) || fileType.contains("jpg", true) -> "jpg"
        else -> "jpg"
    }
}

internal fun buildBatchDownloadId(wallpaper: Wallpaper): String = "batch_${wallpaper.stableKey()}"

internal fun planBatchDownloads(wallpapers: List<Wallpaper>): BatchDownloadPlan {
    if (wallpapers.isEmpty()) return BatchDownloadPlan(allowed = emptyList(), blockedCount = 0)
    val acceptedBySource = mutableMapOf<ContentSource, Int>()
    val allowed = mutableListOf<Wallpaper>()
    var blocked = 0

    wallpapers.forEach { wallpaper ->
        val policy = providerNetworkPoliciesBySource[wallpaper.source]
        val limit = policy?.maxBatchDownloadPerUserAction ?: Int.MAX_VALUE
        val accepted = acceptedBySource[wallpaper.source] ?: 0
        if (accepted < limit) {
            acceptedBySource[wallpaper.source] = accepted + 1
            allowed += wallpaper
        } else {
            blocked += 1
        }
    }

    return BatchDownloadPlan(allowed = allowed, blockedCount = blocked)
}

internal fun buildBatchFileName(wallpaper: Wallpaper, extension: String): String {
    val sourceName = wallpaper.source.name.lowercase(Locale.ROOT)
    return "Aura_${sourceName}_${wallpaper.id}.$extension"
}

internal fun batchDisplayName(wallpaper: Wallpaper): String =
    wallpaper.category.takeIf { it.isNotBlank() }
        ?: wallpaper.tags.firstOrNull { it.isNotBlank() }
        ?: wallpaper.source.name
            .lowercase(Locale.ROOT)
            .replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
