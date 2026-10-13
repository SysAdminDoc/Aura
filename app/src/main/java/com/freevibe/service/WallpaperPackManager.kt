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
import com.freevibe.data.model.rotationIdentityForLocator
import com.freevibe.data.repository.RotationExclusionRepository
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
    val enabled: Boolean = true,
)

@Serializable
data class DaypartSlot(
    val daypart: Daypart,
    val wallpaperUri: String,
    val label: String = "",
    /** HOME, LOCK or BOTH. Blank means the pack's own target. */
    val target: String = "",
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

/** Slots that point at a wallpaper. A blank locator can never be applied. */
internal fun usableSlots(pack: WallpaperPack?): List<DaypartSlot> =
    pack?.slots.orEmpty().filter { it.wallpaperUri.isNotBlank() }

/** The one rule for whether the 15 minute worker should exist. */
internal fun shouldSchedulePack(toggleOn: Boolean, packJson: String): Boolean {
    if (!toggleOn) return false
    val pack = parsePack(packJson) ?: return false
    return pack.enabled && usableSlots(pack).isNotEmpty()
}

internal fun slotTarget(pack: WallpaperPack, slot: DaypartSlot): WallpaperTarget =
    WallpaperTarget.entries.firstOrNull { it.name == slot.target }
        ?: WallpaperTarget.entries.firstOrNull { it.name == pack.target }
        ?: WallpaperTarget.BOTH

/** Enqueues or cancels the worker to match [shouldSchedulePack]. Call after any toggle or pack change. */
fun syncWallpaperPackSchedule(context: Context, toggleOn: Boolean, packJson: String) {
    if (shouldSchedulePack(toggleOn, packJson)) WallpaperPackWorker.schedule(context)
    else WallpaperPackWorker.cancel(context)
}

/**
 * Matches the worker to a pack that was just replaced, such as by a restore, and runs it now when
 * it's needed. A plain sync keeps a running schedule's timing, which would leave the new pack unseen
 * until the next 15 minute tick.
 */
fun restartWallpaperPackSchedule(context: Context, toggleOn: Boolean, packJson: String) {
    WallpaperPackWorker.cancel(context)
    if (shouldSchedulePack(toggleOn, packJson)) WallpaperPackWorker.schedule(context)
}

/**
 * What the dedup marker holds for a time of day whose picture Aura may no longer open. It stops the
 * 15 minute retries without claiming the wallpaper was applied.
 */
internal fun needsRepickMarker(daypart: Daypart): String = "${daypart.name}:needs-repick"

/** True when an apply failed because Aura no longer has access to the picture. Trying again can't fix that. */
internal fun isLostAccess(error: Throwable): Boolean = error is SecurityException || error.cause is SecurityException

@HiltWorker
class WallpaperPackWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val prefs: PreferencesManager,
    private val wallpaperApplier: WallpaperApplier,
    private val rotationExclusions: RotationExclusionRepository,
    private val receiptStore: BackgroundWorkReceiptStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val packJson = prefs.wallpaperPackJson.first()
            if (!shouldSchedulePack(prefs.wallpaperPackEnabled.first(), packJson)) {
                // No slot can be applied, so stop waking up every 15 minutes for it.
                cancel(applicationContext)
                return Result.success()
            }
            val pack = parsePack(packJson) ?: return Result.success()

            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val slot = activeSlotForHour(pack, hour)
            if (slot == null || slot.wallpaperUri.isBlank()) {
                // Clear the dedup marker so a partial pack (e.g. morning-only) is
                // re-applied when its daypart comes around again tomorrow.
                if (prefs.wallpaperPackLastAppliedDaypart.first().isNotBlank()) {
                    prefs.setWallpaperPackLastAppliedDaypart("")
                }
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }

            val lastAppliedDaypart = prefs.wallpaperPackLastAppliedDaypart.first()
            if (lastAppliedDaypart == slot.daypart.name) {
                receiptStore.recordSuccess(WORK_NAME)
                return Result.success()
            }
            if (lastAppliedDaypart == needsRepickMarker(slot.daypart)) {
                // Already reported. Picking the picture again clears the marker, and until then the
                // receipt keeps saying why nothing changed.
                return Result.success()
            }

            val target = slotTarget(pack, slot)

            if (rotationExclusions.isExcluded(rotationIdentityForLocator(slot.wallpaperUri, slot.label))) {
                receiptStore.recordFailure(
                    uniqueWorkName = WORK_NAME,
                    errorClass = "RotationItemExcluded",
                    deferralReason = "The ${slot.daypart.displayName.lowercase()} wallpaper is excluded. Restore it in Settings, Rotation exclusions.",
                )
                return Result.success()
            }

            wallpaperApplier.applyByLocator(slot.wallpaperUri, target)
                .onSuccess {
                    prefs.setWallpaperPackLastAppliedDaypart(slot.daypart.name)
                    receiptStore.recordSuccess(WORK_NAME)
                }
                .onFailure { e ->
                    if (isLostAccess(e)) {
                        // A restored pack, or access the user took away. The grant won't come back by
                        // itself, so this time of day is left alone until the picture is picked again.
                        prefs.setWallpaperPackLastAppliedDaypart(needsRepickMarker(slot.daypart))
                        receiptStore.recordFailure(
                            uniqueWorkName = WORK_NAME,
                            errorClass = "SecurityException",
                            deferralReason = "Aura can no longer open the ${slot.daypart.displayName.lowercase()} " +
                                "wallpaper. Pick it again in Settings, Edit 24H pack.",
                        )
                    } else {
                        receiptStore.recordFailure(
                            uniqueWorkName = WORK_NAME,
                            errorClass = e.javaClass.simpleName,
                            deferralReason = e.message ?: "apply failed",
                        )
                    }
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
