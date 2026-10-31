package com.freevibe.service

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.freevibe.data.local.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records that a boot actually reached Aura.
 *
 * "Did the boot receiver fire?" cannot be answered after the fact from anything
 * the platform keeps — either Aura wrote it down at the time or the answer is
 * unknown forever. A missing record on a device that has certainly rebooted is
 * itself the finding: the OEM never delivered the broadcast.
 */
@Singleton
class BootObservationStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun lastBootUtc(): String? = prefs.getString(KEY_LAST_BOOT_UTC, null)

    fun bootCount(): Int = prefs.getInt(KEY_BOOT_COUNT, 0)

    companion object {
        private const val PREFS_NAME = "rotation_boot_observations"
        private const val KEY_LAST_BOOT_UTC = "last_boot_utc"
        private const val KEY_BOOT_COUNT = "boot_count"

        /**
         * The same write, reachable from a BroadcastReceiver.
         *
         * The receiver has no injected graph and must not build one under the
         * broadcast deadline, but it also must not carry its own copy of these
         * key names — a drifted key reads as "no boot ever happened", which is
         * the exact wrong answer.
         */
        fun recordBoot(context: Context, nowMs: Long = System.currentTimeMillis()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LAST_BOOT_UTC, CrashDiagnosticsCollector.timestampWithZone(nowMs))
                .putInt(KEY_BOOT_COUNT, prefs.getInt(KEY_BOOT_COUNT, 0) + 1)
                .apply()
        }
    }
}

interface RotationHealthReader {
    suspend fun read(): RotationHealthSnapshot

    /**
     * Runs a rotation now, outside the schedule.
     *
     * This is the part of the screen that turns a diagnosis into a test: reading
     * that a schedule looks healthy is not the same as watching one fire.
     */
    fun runNow()
}

@Singleton
class AndroidRotationHealthReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesManager,
    private val receiptStore: BackgroundWorkReceiptStore,
    private val bootObservations: BootObservationStore,
) : RotationHealthReader {

    override suspend fun read(): RotationHealthSnapshot {
        val enabled = runCatching { preferences.autoWallpaperEnabled.first() }
            .getOrDefault(false)
        val intervalMinutes = runCatching { preferences.autoWallpaperInterval.first() }.getOrNull()
        val source = runCatching { preferences.autoWallpaperSource.first() }.getOrNull()
        val receipt = receiptStore.read(AutoWallpaperWorker.WORK_NAME)
        val guidance = backgroundBatteryGuidanceForManufacturer(Build.MANUFACTURER)
        val exempt = readBatteryOptimizationExemption()

        val work = readWorkInfo()

        val verdict = classifyRotationHealth(
            rotationEnabled = enabled,
            workState = work.state,
            hasNextFire = work.nextFireUtc != null,
            stopReason = work.stopReason,
            lastResult = receipt.lastResult,
            ignoringBatteryOptimizations = exempt,
        )

        return RotationHealthSnapshot(
            verdict = verdict,
            rotationEnabled = enabled,
            intervalMinutes = intervalMinutes,
            sourceLabel = source,
            workState = work.state,
            lastFireUtc = receipt.lastSuccessUtc,
            lastFailureUtc = receipt.lastFailureUtc,
            nextFireUtc = work.nextFireUtc,
            stopReason = work.stopReason,
            lastErrorClass = receipt.lastErrorClass,
            lastResult = receipt.lastResult,
            lastDeferralReason = receipt.lastDeferralReason,
            bootReceiverLastUtc = bootObservations.lastBootUtc(),
            ignoringBatteryOptimizations = exempt,
            batteryGuidance = guidance,
            actionHint = rotationHealthActionHint(
                verdict = verdict,
                ignoringBatteryOptimizations = exempt,
                batteryGuidance = guidance,
                lastErrorClass = receipt.lastErrorClass,
                lastDeferralReason = receipt.lastDeferralReason,
            ),
            readError = work.readError,
        )
    }

    override fun runNow() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                RUN_NOW_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<AutoWallpaperWorker>()
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString(
                                AutoWallpaperWorker.RECEIPT_WORK_NAME_KEY,
                                AutoWallpaperWorker.WORK_NAME,
                            )
                            .putBoolean(AutoWallpaperWorker.RESTART_COUNTDOWN_KEY, true)
                            .build(),
                    )
                    .build(),
            )
        }
    }

    private data class WorkReading(
        val state: String,
        val nextFireUtc: String?,
        val stopReason: String?,
        val readError: String? = null,
    )

    private fun readWorkInfo(): WorkReading = runCatching {
        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AutoWallpaperWorker.WORK_NAME)
            .get(WORK_INFO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        WorkReading(
            state = summarizeWorkInfoStates(infos.map { it.state }),
            nextFireUtc = nextScheduledFireUtc(infos),
            stopReason = summarizeWorkInfoStopReasons(infos.map { it.stopReason }),
        )
    }.getOrElse { error ->
        WorkReading(
            state = WORK_INFO_STATUS_READ_FAILED,
            nextFireUtc = null,
            stopReason = null,
            readError = error.javaClass.simpleName,
        )
    }

    private fun readBatteryOptimizationExemption(): Boolean? = runCatching {
        val power = context.getSystemService(PowerManager::class.java) ?: return@runCatching null
        power.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrNull()

    private companion object {
        const val WORK_INFO_TIMEOUT_SECONDS = 2L
        const val RUN_NOW_WORK_NAME = "auto_wallpaper_run_now"
    }
}

/**
 * The soonest real next-fire time across the records for this unique work.
 *
 * WorkManager reports [Long.MAX_VALUE] for "not scheduled", and a periodic worker
 * the OS is holding reports exactly that. Treating the sentinel as a timestamp
 * would print a date in the year 292278994 and read as healthy, so it is filtered
 * out here rather than formatted.
 */
internal fun nextScheduledFireUtc(infos: List<WorkInfo>): String? = infos
    .map { it.nextScheduleTimeMillis }
    .filter { it > 0L && it != Long.MAX_VALUE }
    .minOrNull()
    ?.let(CrashDiagnosticsCollector::timestampWithZone)
