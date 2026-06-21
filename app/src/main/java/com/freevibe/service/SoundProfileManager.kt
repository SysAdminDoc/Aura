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

            if (!prefs.soundProfilesEnabled.first()) {
                return Result.success()
            }

            val profiles = parseProfiles(prefs.soundProfilesJson.first())
            if (profiles.isEmpty()) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "NoProfiles",
                    deferralReason = "no sound profiles defined",
                )
                return Result.success()
            }

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val active = activeProfileForHour(profiles, hour)
            if (active == null) {
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            val lastAppliedId = prefs.soundProfileLastAppliedId.first()
            if (lastAppliedId == active.id) {
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            if (active.ringtoneUri.isNotBlank()) {
                RingtoneManager.setActualDefaultRingtoneUri(
                    applicationContext,
                    RingtoneManager.TYPE_RINGTONE,
                    Uri.parse(active.ringtoneUri),
                )
            }
            if (active.notificationUri.isNotBlank()) {
                RingtoneManager.setActualDefaultRingtoneUri(
                    applicationContext,
                    RingtoneManager.TYPE_NOTIFICATION,
                    Uri.parse(active.notificationUri),
                )
            }
            if (active.alarmUri.isNotBlank()) {
                RingtoneManager.setActualDefaultRingtoneUri(
                    applicationContext,
                    RingtoneManager.TYPE_ALARM,
                    Uri.parse(active.alarmUri),
                )
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
        }
    }
}
