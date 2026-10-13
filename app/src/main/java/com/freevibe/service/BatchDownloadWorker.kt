package com.freevibe.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

/**
 * Downloads the pending items of the persisted favorites batch. Each item is marked done or
 * failed as it finishes, so a run that Android stops or kills resumes with what's left.
 */
@HiltWorker
class BatchDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val store: BatchDownloadStore,
    private val downloadManager: DownloadManager,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        val record = store.read()
        val pending = record.items.filter { it.status == BATCH_ITEM_PENDING }
        if (!record.cancelled && pending.isNotEmpty()) {
            val permits = Semaphore(CONCURRENCY)
            coroutineScope {
                pending.map { item ->
                    async {
                        permits.withPermit {
                            setProgress(workDataOf(PROGRESS_CURRENT_ITEM to item.displayName))
                            val status = if (download(item)) BATCH_ITEM_DONE else BATCH_ITEM_FAILED
                            store.update { it.withItemStatus(item.downloadId, status) }
                        }
                    }
                }.awaitAll()
            }
        }
        recordOutcome(store.read())
        Result.success()
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        receiptStore.recordRetry(
            uniqueWorkName = WORK_NAME,
            errorClass = error.javaClass.simpleName,
            deferralReason = error.message ?: "batch download record unavailable",
        )
        Result.retry()
    }

    private suspend fun download(item: BatchDownloadItem): Boolean = try {
        downloadManager.downloadWallpaper(
            id = item.downloadId,
            url = item.url,
            fileName = item.fileName,
            source = item.source,
            provenanceUrl = item.provenanceUrl,
        ).isSuccess
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        false
    }

    private fun recordOutcome(record: BatchDownloadRecord) {
        val done = record.items.count { it.status == BATCH_ITEM_DONE }
        val failed = record.items.count { it.status == BATCH_ITEM_FAILED }
        if (failed > 0 && done == 0) {
            receiptStore.recordFailure(
                uniqueWorkName = WORK_NAME,
                errorClass = "BatchDownloadFailed",
                deferralReason = "$failed of ${record.items.size} downloads failed",
            )
        } else {
            receiptStore.recordSuccess(WORK_NAME)
        }
    }

    companion object {
        const val WORK_NAME = "favorites_batch_download"
        internal const val PROGRESS_CURRENT_ITEM = "current_item"
        private const val CONCURRENCY = 3

        /** Queues the stored batch. KEEP leaves a batch that is already queued or running alone. */
        fun enqueue(context: Context): UUID {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<BatchDownloadWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
            return request.id
        }
    }
}
