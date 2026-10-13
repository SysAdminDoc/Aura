package com.freevibe.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

@HiltWorker
class RingtoneShuffleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val poolManager: SoundShufflePoolManager,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val poolId = inputData.getString(INPUT_POOL_ID).orEmpty()
        if (poolId.isBlank()) {
            return try {
                poolManager.migrateLegacyIfNeeded()
                poolManager.restoreSchedules()
                receiptStore.recordSuccess(WORK_NAME)
                Result.success()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = error.javaClass.simpleName,
                    deferralReason = error.message ?: "sound pool migration failed",
                )
                Result.retry()
            }
        }

        val uniqueName = workName(poolId)
        return try {
            val outcome = poolManager.runPool(poolId)
            if (outcome.applied == 0 && (outcome.failed > 0 || outcome.skipped > 0)) {
                val errorClass = if (outcome.failed > 0) {
                    "SoundPoolApplyFailed"
                } else {
                    "NoCompatiblePoolSounds"
                }
                receiptStore.recordFailure(
                    uniqueWorkName = uniqueName,
                    errorClass = errorClass,
                    deferralReason = outcome.summary,
                )
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = errorClass,
                    deferralReason = outcome.summary,
                )
            } else {
                receiptStore.recordSuccess(uniqueName)
                receiptStore.recordSuccess(WORK_NAME)
            }
            if (outcome.shouldRetry) Result.retry() else Result.success()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            receiptStore.recordFailure(
                uniqueWorkName = uniqueName,
                errorClass = error.javaClass.simpleName,
                deferralReason = error.message ?: "sound pool failed",
            )
            receiptStore.recordFailure(
                uniqueWorkName = WORK_NAME,
                errorClass = error.javaClass.simpleName,
                deferralReason = error.message ?: "sound pool failed",
            )
            Result.retry()
        }
    }

    companion object {
        /** Pre-pool shuffle job. Nothing enqueues it now; a leftover run migrates and cancels it. */
        const val WORK_NAME = "ringtone_shuffle"
        internal const val INPUT_POOL_ID = "sound_shuffle_pool_id"

        fun workName(poolId: String): String = "sound_shuffle_pool_$poolId"

        private fun runNowWorkName(poolId: String): String = "sound_shuffle_pool_${poolId}_now"

        fun schedule(context: Context, poolId: String, intervalHours: Long) {
            val request = PeriodicWorkRequestBuilder<RingtoneShuffleWorker>(
                intervalHours.coerceAtLeast(1),
                TimeUnit.HOURS,
            ).setInputData(
                workDataOf(INPUT_POOL_ID to poolId),
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15,
                TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName(poolId),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun runNow(context: Context, poolId: String) {
            val request = OneTimeWorkRequestBuilder<RingtoneShuffleWorker>()
                .setInputData(workDataOf(INPUT_POOL_ID to poolId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            // KEEP: a second tap mustn't cancel a run that's halfway through applying sounds.
            WorkManager.getInstance(context).enqueueUniqueWork(
                runNowWorkName(poolId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, poolId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(poolId))
            WorkManager.getInstance(context).cancelUniqueWork(runNowWorkName(poolId))
        }

        fun cancelLegacy(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
