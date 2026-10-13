package com.freevibe.service

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.providerNetworkPoliciesBySource
import com.freevibe.data.model.stableKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
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

internal const val BATCH_ITEM_PENDING = "PENDING"
internal const val BATCH_ITEM_DONE = "DONE"
internal const val BATCH_ITEM_FAILED = "FAILED"

/** One wallpaper in a batch, with everything the worker needs to download it after process death. */
@JsonClass(generateAdapter = true)
data class BatchDownloadItem(
    val downloadId: String,
    val url: String,
    val fileName: String,
    val source: String,
    val provenanceUrl: String,
    val displayName: String,
    /** [BATCH_ITEM_PENDING], [BATCH_ITEM_DONE] or [BATCH_ITEM_FAILED]. */
    val status: String = BATCH_ITEM_PENDING,
)

@JsonClass(generateAdapter = true)
data class BatchDownloadRecord(
    val totalCount: Int = 0,
    val blockedCount: Int = 0,
    val items: List<BatchDownloadItem> = emptyList(),
    val cancelled: Boolean = false,
)

/**
 * The current or last favorites batch, kept in DataStore so [BatchDownloadWorker] can resume it
 * after process death and the Library banner can show its progress again.
 */
@Singleton
class BatchDownloadStore @Inject constructor(
    private val prefs: PreferencesManager,
    private val moshi: Moshi,
) {
    private val mutex = Mutex()

    val record: Flow<BatchDownloadRecord> = prefs.batchDownloadJson
        .map { parseBatchDownloadRecord(moshi, it) }
        .distinctUntilChanged()

    suspend fun read(): BatchDownloadRecord = parseBatchDownloadRecord(moshi, prefs.batchDownloadJson.first())

    suspend fun replace(record: BatchDownloadRecord) = mutex.withLock { write(record) }

    /** Read-modify-write under one lock; the worker updates items from several downloads at once. */
    suspend fun update(transform: (BatchDownloadRecord) -> BatchDownloadRecord) = mutex.withLock {
        write(transform(read()))
    }

    private suspend fun write(record: BatchDownloadRecord) {
        prefs.setBatchDownloadJson(moshi.adapter(BatchDownloadRecord::class.java).toJson(record))
    }
}

/**
 * Starts favorites batches as unique WorkManager work ([BatchDownloadWorker]), so a long batch
 * survives backgrounding and process death. Progress comes from the persisted
 * [BatchDownloadRecord] plus the work's state, never from in-process memory alone.
 */
@Singleton
class BatchDownloadService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: BatchDownloadStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val startMutex = Mutex()
    // Covers the gap between persisting a batch and WorkManager reporting its work.
    private val starting = MutableStateFlow(false)
    private val workInfo: Flow<WorkInfo?> = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(BatchDownloadWorker.WORK_NAME)
        .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull() }

    val state: StateFlow<BatchDownloadState> = combine(store.record, workInfo, starting) { record, info, isStarting ->
        batchDownloadStateOf(
            record = record,
            workUnfinished = isStarting || info?.state?.isFinished == false,
            currentItem = info?.progress?.getString(BatchDownloadWorker.PROGRESS_CURRENT_ITEM),
        )
    }.stateIn(scope, SharingStarted.Eagerly, BatchDownloadState())

    suspend fun downloadBatch(wallpapers: List<Wallpaper>): BatchDownloadStartResult = startMutex.withLock {
        val plan = planBatchDownloads(wallpapers)
        if (workInfo.first()?.state?.isFinished == false) {
            return BatchDownloadStartResult(alreadyRunning = true)
        }
        if (plan.allowed.isEmpty()) {
            store.replace(BatchDownloadRecord(totalCount = wallpapers.size, blockedCount = plan.blockedCount))
            return BatchDownloadStartResult(acceptedCount = 0, blockedCount = plan.blockedCount)
        }
        starting.value = true
        try {
            store.replace(
                BatchDownloadRecord(
                    totalCount = wallpapers.size,
                    blockedCount = plan.blockedCount,
                    items = plan.allowed.map(::batchDownloadItem),
                ),
            )
            val requestId = BatchDownloadWorker.enqueue(context)
            // An earlier finished batch can still be listed, so wait for this request by id.
            withTimeoutOrNull(START_HANDOFF_TIMEOUT_MS) { workInfo.first { it?.id == requestId } }
        } finally {
            starting.value = false
        }
        BatchDownloadStartResult(acceptedCount = plan.allowed.size, blockedCount = plan.blockedCount)
    }

    /** Stops the batch. Items it never reached count as failed. */
    suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(BatchDownloadWorker.WORK_NAME)
        store.update { it.copy(cancelled = true) }
    }

    /** Stops any batch and clears its progress. */
    suspend fun reset() {
        WorkManager.getInstance(context).cancelUniqueWork(BatchDownloadWorker.WORK_NAME)
        store.replace(BatchDownloadRecord())
    }

    private companion object {
        const val START_HANDOFF_TIMEOUT_MS = 5_000L
    }
}

/**
 * Items the batch hasn't reached yet count as failed once its work is no longer queued or
 * running (finished, cancelled, or lost with a backup restore), so the banner always settles.
 */
internal fun batchDownloadStateOf(
    record: BatchDownloadRecord,
    workUnfinished: Boolean,
    currentItem: String?,
): BatchDownloadState {
    val running = workUnfinished && !record.cancelled
    val done = record.items.count { it.status == BATCH_ITEM_DONE }
    val failed = record.items.count { it.status == BATCH_ITEM_FAILED }
    val pending = record.items.size - done - failed
    return BatchDownloadState(
        totalCount = record.totalCount,
        completedCount = done,
        failedCount = failed + if (running) 0 else pending,
        blockedCount = record.blockedCount,
        isRunning = running,
        currentItem = if (running) currentItem.orEmpty() else "",
    )
}

internal fun BatchDownloadRecord.withItemStatus(downloadId: String, status: String): BatchDownloadRecord =
    copy(items = items.map { if (it.downloadId == downloadId) it.copy(status = status) else it })

internal fun parseBatchDownloadRecord(moshi: Moshi, raw: String): BatchDownloadRecord {
    if (raw.isBlank()) return BatchDownloadRecord()
    return runCatching { moshi.adapter(BatchDownloadRecord::class.java).fromJson(raw) }
        .getOrNull()
        ?: BatchDownloadRecord()
}

internal fun batchDownloadItem(wallpaper: Wallpaper): BatchDownloadItem = BatchDownloadItem(
    downloadId = buildBatchDownloadId(wallpaper),
    url = wallpaper.fullUrl,
    fileName = buildBatchFileName(wallpaper, guessBatchExtension(wallpaper.fileType)),
    source = wallpaper.source.name,
    provenanceUrl = wallpaper.sourcePageUrl.ifBlank { wallpaper.fullUrl },
    displayName = batchDisplayName(wallpaper),
)

internal fun guessBatchExtension(fileType: String): String = when {
    fileType.contains("png", true) -> "png"
    fileType.contains("webp", true) -> "webp"
    fileType.contains("gif", true) -> "gif"
    fileType.contains("jpeg", true) || fileType.contains("jpg", true) -> "jpg"
    else -> "jpg"
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
