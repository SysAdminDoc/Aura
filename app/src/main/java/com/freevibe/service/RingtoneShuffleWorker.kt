package com.freevibe.service

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class RingtoneShuffleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val prefs: PreferencesManager,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (!Settings.System.canWrite(applicationContext)) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "MissingPermission",
                    deferralReason = "WRITE_SETTINGS not granted",
                )
                return Result.failure()
            }

            val soundDownloads = downloadDao.getByType("SOUND").first()
                .filter { it.localPath.isNotBlank() }

            if (soundDownloads.isEmpty()) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "NoSounds",
                    deferralReason = "no downloaded sounds available for shuffle",
                )
                return Result.failure()
            }

            val lastApplied = prefs.ringtoneShuffleLastAppliedId()
            val candidates = if (soundDownloads.size > 1) {
                soundDownloads.filter { it.id != lastApplied }
            } else {
                soundDownloads
            }
            val chosen = candidates.random()

            val uri = Uri.parse(chosen.localPath)
            RingtoneManager.setActualDefaultRingtoneUri(
                applicationContext,
                RingtoneManager.TYPE_RINGTONE,
                uri,
            )

            prefs.setRingtoneShuffleLastAppliedId(chosen.id)
            receiptStore.recordSuccess(WORK_NAME)
            Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            receiptStore.recordFailure(
                uniqueWorkName = WORK_NAME,
                errorClass = e.javaClass.simpleName,
                deferralReason = e.message ?: "unknown error",
            )
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "ringtone_shuffle"

        fun schedule(context: Context, intervalHours: Long = 24L) {
            val request = PeriodicWorkRequestBuilder<RingtoneShuffleWorker>(
                intervalHours.coerceAtLeast(1L), TimeUnit.HOURS,
            ).setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                15, TimeUnit.MINUTES,
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
