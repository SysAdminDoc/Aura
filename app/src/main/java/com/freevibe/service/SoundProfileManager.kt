package com.freevibe.service

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freevibe.data.local.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Serializable
data class SoundProfile(
    val id: String,
    val name: String,
    val ringtoneUri: String = "",
    val notificationUri: String = "",
    val alarmUri: String = "",
    val startHour: Int = 0,
    val endHour: Int = 24,
    val enabled: Boolean = true,
) {
    fun coversHour(hour: Int): Boolean {
        return if (startHour <= endHour) {
            hour in startHour until endHour
        } else {
            hour >= startHour || hour < endHour
        }
    }
}

internal val soundProfileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun parseProfiles(raw: String): List<SoundProfile> =
    if (raw.isBlank()) emptyList()
    else runCatching { soundProfileJson.decodeFromString<List<SoundProfile>>(raw) }.getOrDefault(emptyList())

internal fun serializeProfiles(profiles: List<SoundProfile>): String =
    soundProfileJson.encodeToString(profiles)

internal fun activeProfileForHour(profiles: List<SoundProfile>, hour: Int): SoundProfile? =
    profiles.filter { it.enabled }.firstOrNull { it.coversHour(hour) }

@HiltWorker
class SoundProfileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val prefs: PreferencesManager,
    private val receiptStore: BackgroundWorkReceiptStore,
    private val systemSounds: SystemSoundWriter,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val profiles = parseProfiles(prefs.soundProfilesJson.first())
            if (!soundProfilesNeedWorker(prefs.soundProfilesEnabled.first(), profiles)) {
                // Nothing here can change a sound, so stop waking up every 15 minutes for it.
                cancel(applicationContext)
                return Result.success()
            }

            if (!Settings.System.canWrite(applicationContext)) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "MissingPermission",
                    deferralReason = "WRITE_SETTINGS not granted",
                )
                return Result.failure()
            }

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val active = activeProfileForHour(profiles, hour)
            if (active == null) {
                // Clear the dedup marker so a single profile (e.g. night-only) is
                // re-applied when its window comes around again tomorrow.
                if (prefs.soundProfileLastAppliedId.first().isNotBlank()) {
                    prefs.setSoundProfileLastAppliedId("")
                }
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            val lastAppliedId = prefs.soundProfileLastAppliedId.first()
            if (lastAppliedId == active.id) {
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            // Persist lastApplied*Uri alongside each set so the boot/update restoration
            // receiver restores the profile sound instead of stomping it with a stale
            // manually-applied one.
            if (active.ringtoneUri.isNotBlank()) {
                systemSounds.write(SoundShuffleTarget.RINGTONE, Uri.parse(active.ringtoneUri))
                prefs.setLastAppliedRingtoneUri(active.ringtoneUri)
            }
            if (active.notificationUri.isNotBlank()) {
                systemSounds.write(SoundShuffleTarget.NOTIFICATION, Uri.parse(active.notificationUri))
                prefs.setLastAppliedNotificationUri(active.notificationUri)
            }
            if (active.alarmUri.isNotBlank()) {
                systemSounds.write(SoundShuffleTarget.ALARM, Uri.parse(active.alarmUri))
                prefs.setLastAppliedAlarmUri(active.alarmUri)
            }

            prefs.setSoundProfileLastAppliedId(active.id)
            receiptStore.recordSuccess(WORK_NAME)
            Result.success()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            receiptStore.recordFailure(
                uniqueWorkName = WORK_NAME,
                errorClass = e.javaClass.simpleName,
                deferralReason = e.message ?: "unknown error",
            )
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "sound_profile"
        private const val RUN_NOW_WORK_NAME = "sound_profile_now"

        /** Schedules the worker when a profile can change a sound, and cancels it when none can. */
        fun sync(context: Context, needed: Boolean) = if (needed) schedule(context) else cancel(context)

        /** Runs one pass now, so an edit is heard without waiting for the next 15 minute tick. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                RUN_NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SoundProfileWorker>().build(),
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SoundProfileWorker>(
                15L, TimeUnit.MINUTES,
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
            WorkManager.getInstance(context).cancelUniqueWork(RUN_NOW_WORK_NAME)
        }
    }
}
