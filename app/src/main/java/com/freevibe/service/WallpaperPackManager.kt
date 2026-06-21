package com.freevibe.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.WallpaperTarget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * A 24H wallpaper pack maps dayparts (morning/day/evening/night) to wallpaper
 * URIs. The worker checks every 15 minutes and applies the wallpaper for the
 * current daypart if it hasn't already been applied.
 */
@Serializable
data class WallpaperPack(
    val id: String,
    val name: String,
    val target: String = "BOTH",
    val slots: List<DaypartSlot> = emptyList(),
)

@Serializable
data class DaypartSlot(
    val daypart: Daypart,
    val wallpaperUri: String,
    val label: String = "",
)

@Serializable
enum class Daypart(val startHour: Int, val endHour: Int, val displayName: String) {
    MORNING(6, 12, "Morning"),
    DAY(12, 17, "Day"),
    EVENING(17, 21, "Evening"),
    NIGHT(21, 6, "Night");

    fun coversHour(hour: Int): Boolean =
        if (startHour <= endHour) hour in startHour until endHour
        else hour >= startHour || hour < endHour

    companion object {
        fun forHour(hour: Int): Daypart = entries.first { it.coversHour(hour) }
    }
}

internal val wallpaperPackJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

internal fun parsePack(raw: String): WallpaperPack? =
    if (raw.isBlank()) null
    else runCatching { wallpaperPackJson.decodeFromString<WallpaperPack>(raw) }.getOrNull()

internal fun serializePack(pack: WallpaperPack): String =
    wallpaperPackJson.encodeToString(pack)

internal fun activeSlotForHour(pack: WallpaperPack, hour: Int): DaypartSlot? {
    val daypart = Daypart.forHour(hour)
    return pack.slots.firstOrNull { it.daypart == daypart }
}

@HiltWorker
class WallpaperPackWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val prefs: PreferencesManager,
    private val wallpaperApplier: WallpaperApplier,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (!prefs.wallpaperPackEnabled.first()) {
                return Result.success()
            }

            val pack = parsePack(prefs.wallpaperPackJson.first())
            if (pack == null || pack.slots.isEmpty()) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "NoPack",
                    deferralReason = "no wallpaper pack defined",
                )
                return Result.success()
            }

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val slot = activeSlotForHour(pack, hour)
            if (slot == null || slot.wallpaperUri.isBlank()) {
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            val lastAppliedDaypart = prefs.wallpaperPackLastAppliedDaypart.first()
            if (lastAppliedDaypart == slot.daypart.name) {
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            val target = runCatching { WallpaperTarget.valueOf(pack.target) }
                .getOrDefault(WallpaperTarget.BOTH)

            wallpaperApplier.applyByLocator(slot.wallpaperUri, target)
                .onSuccess {
                    prefs.setWallpaperPackLastAppliedDaypart(slot.daypart.name)
                    receiptStore.recordSuccess(WORK_NAME)
                }
                .onFailure { e ->
                    receiptStore.recordFailure(
                        uniqueWorkName = WORK_NAME,
                        errorClass = e.javaClass.simpleName,
                        deferralReason = e.message ?: "apply failed",
                    )
                }

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
        const val WORK_NAME = "wallpaper_pack"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WallpaperPackWorker>(
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
