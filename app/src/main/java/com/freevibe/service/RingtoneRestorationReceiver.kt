package com.freevibe.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freevibe.data.local.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Restores Aura-applied ringtone/notification/alarm sounds after a reboot or app
 * update (some OEM updates reset sound URIs to defaults).
 *
 * The receiver only enqueues [RingtoneRestorationWorker] and returns. The previous
 * goAsync() + coroutine design did DataStore and ContentResolver reads under the
 * broadcast deadline, which ANR'd on-device (Android 16, post-boot CPU pressure):
 * BOOT_COMPLETED work must not race a ~10s timer against boot-time disk contention.
 */
class RingtoneRestorationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // Nothing the platform keeps can answer "did the boot broadcast reach
        // Aura?" after the fact, so it has to be written down while it is true.
        // One small put, inside the broadcast deadline. A device that has plainly
        // rebooted with no record here is an OEM that never delivered it, and
        // Rotation Health reports exactly that.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            runCatching { BootObservationStore.recordBoot(context) }
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            RingtoneRestorationWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RingtoneRestorationWorker>().build(),
        )
    }
}

@HiltWorker
class RingtoneRestorationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val prefs: PreferencesManager,
    private val livenessMonitor: LiveWallpaperLivenessMonitor,
    private val soundShufflePoolManager: SoundShufflePoolManager,
    private val systemSounds: SystemSoundWriter,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            var anyFailed = false
            runCatching { prefs.lastAppliedRingtoneUri.first() }
                .onSuccess { if (!restoreIfNeeded(RingtoneManager.TYPE_RINGTONE, it)) anyFailed = true }
            runCatching { prefs.lastAppliedNotificationUri.first() }
                .onSuccess { if (!restoreIfNeeded(RingtoneManager.TYPE_NOTIFICATION, it)) anyFailed = true }
            runCatching { prefs.lastAppliedAlarmUri.first() }
                .onSuccess { if (!restoreIfNeeded(RingtoneManager.TYPE_ALARM, it)) anyFailed = true }
            runCatching {
                soundShufflePoolManager.migrateLegacyIfNeeded()
                soundShufflePoolManager.restoreSchedules()
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Sound pool schedule restore failed", error)
                anyFailed = true
            }
            runCatching { livenessMonitor.refresh() }
            if (anyFailed) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Ringtone restoration failed", e)
            Result.retry()
        }
    }

    private suspend fun restoreIfNeeded(type: Int, lastAppliedUri: String): Boolean {
        val typeName = when (type) {
            RingtoneManager.TYPE_RINGTONE -> "ringtone"
            RingtoneManager.TYPE_NOTIFICATION -> "notification"
            RingtoneManager.TYPE_ALARM -> "alarm"
            else -> "type-$type"
        }
        if (lastAppliedUri.isBlank()) return true
        val expected = Uri.parse(lastAppliedUri)
        val current = RingtoneManager.getActualDefaultRingtoneUri(applicationContext, type)
        if (current == expected) {
            Log.d(TAG, "Boot restore: $typeName already correct")
            return true
        }
        try {
            applicationContext.contentResolver.openInputStream(expected)?.close()
                ?: run {
                    Log.w(TAG, "Boot restore: $typeName source missing ($expected)")
                    return false
                }
            systemSounds.write(SoundShuffleTarget.entries.first { it.ringtoneManagerType == type }, expected)
        } catch (e: SecurityException) {
            Log.w(TAG, "Boot restore: $typeName write refused", e)
            return false
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Boot restore: $typeName failed", e)
            return false
        }
        val actual = RingtoneManager.getActualDefaultRingtoneUri(applicationContext, type)
        if (actual != expected) {
            Log.w(TAG, "Boot restore: $typeName set call succeeded but read-back differs ($actual != $expected)")
            return false
        }
        Log.d(TAG, "Boot restore: $typeName restored to $expected")
        return true
    }

    companion object {
        private const val TAG = "RingtoneRestoration"
        const val WORK_NAME = "ringtone_restoration"
    }
}
