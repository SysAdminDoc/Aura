package com.freevibe.service

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class BackgroundWorkDiagnostics(
    val network: BackgroundNetworkDiagnostics = BackgroundNetworkDiagnostics(),
    val rows: List<BackgroundWorkStatusRow> = emptyList(),
    val batteryGuidance: BackgroundBatteryGuidance = backgroundBatteryGuidanceForManufacturer(""),
    val prefetch: RotationPrefetchDiagnostics = RotationPrefetchDiagnostics(),
)

data class BackgroundNetworkDiagnostics(
    val activeNetworkMetered: Boolean? = null,
    val restrictBackgroundStatus: String = "unavailable",
    val readError: String? = null,
)

data class BackgroundBatteryGuidance(
    val manufacturer: String,
    val summary: String,
)

data class BackgroundWorkStatusRow(
    val label: String,
    val uniqueWorkName: String,
    val workInfoStatus: String,
    val workInfoCount: Int = 0,
    val maxRunAttemptCount: Int? = null,
    val stopReasonStatus: String? = null,
    val lastSuccessUtc: String? = null,
    val lastFailureUtc: String? = null,
    val lastErrorClass: String? = null,
    val lastResult: String? = null,
    val lastDeferralReason: String? = null,
    val actionHint: String? = null,
    val readError: String? = null,
)

interface BackgroundWorkDiagnosticsReader {
    suspend fun read(): BackgroundWorkDiagnostics
}

@Singleton
class AndroidBackgroundWorkDiagnosticsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptStore: BackgroundWorkReceiptStore,
    private val prefetch: RotationPrefetchCache,
) : BackgroundWorkDiagnosticsReader {

    override suspend fun read(): BackgroundWorkDiagnostics {
        val network = readNetworkDiagnostics()
        val batteryGuidance = backgroundBatteryGuidanceForManufacturer(Build.MANUFACTURER)
        val managerResult = runCatching { WorkManager.getInstance(context) }
        val rows = BACKGROUND_WORK_ITEMS.map { item ->
            managerResult.fold(
                onSuccess = { manager -> readWorkInfo(manager, item) },
                onFailure = { error ->
                    val receipt = receiptStore.read(item.uniqueWorkName)
                    BackgroundWorkStatusRow(
                        label = item.label,
                        uniqueWorkName = item.uniqueWorkName,
                        workInfoStatus = "WorkManager unavailable",
                        lastSuccessUtc = receipt.lastSuccessUtc,
                        lastFailureUtc = receipt.lastFailureUtc,
                        lastErrorClass = receipt.lastErrorClass,
                        lastResult = receipt.lastResult,
                        lastDeferralReason = receipt.lastDeferralReason,
                        readError = error.javaClass.simpleName,
                    )
                },
            )
        }.map { row ->
            row.copy(actionHint = backgroundWorkActionHint(row, network, batteryGuidance))
        }
        return BackgroundWorkDiagnostics(
            network = network,
            rows = rows,
            batteryGuidance = batteryGuidance,
            prefetch = prefetch.diagnostics(),
        )
    }

    private fun readWorkInfo(
        manager: WorkManager,
        item: BackgroundWorkItem,
    ): BackgroundWorkStatusRow = runCatching {
        val infos = (item.workTag?.let(manager::getWorkInfosByTag) ?: manager.getWorkInfosForUniqueWork(item.uniqueWorkName))
            .get(WORK_INFO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val receipt = receiptStore.read(item.uniqueWorkName)
        BackgroundWorkStatusRow(
            label = item.label,
            uniqueWorkName = item.uniqueWorkName,
            workInfoStatus = summarizeWorkInfoStates(infos.map { it.state }),
            workInfoCount = infos.size,
            maxRunAttemptCount = infos.maxOfOrNull { it.runAttemptCount },
            stopReasonStatus = summarizeWorkInfoStopReasons(infos.map { it.stopReason }),
            lastSuccessUtc = receipt.lastSuccessUtc,
            lastFailureUtc = receipt.lastFailureUtc,
            lastErrorClass = receipt.lastErrorClass,
            lastResult = receipt.lastResult,
            lastDeferralReason = receipt.lastDeferralReason,
        )
    }.getOrElse { error ->
        val receipt = receiptStore.read(item.uniqueWorkName)
        BackgroundWorkStatusRow(
            label = item.label,
            uniqueWorkName = item.uniqueWorkName,
            workInfoStatus = "WorkInfo read failed",
            lastSuccessUtc = receipt.lastSuccessUtc,
            lastFailureUtc = receipt.lastFailureUtc,
            lastErrorClass = receipt.lastErrorClass,
            lastResult = receipt.lastResult,
            lastDeferralReason = receipt.lastDeferralReason,
            readError = error.javaClass.simpleName,
        )
    }

    private fun readNetworkDiagnostics(): BackgroundNetworkDiagnostics = runCatching {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching BackgroundNetworkDiagnostics(
                restrictBackgroundStatus = "connectivity unavailable",
            )
        BackgroundNetworkDiagnostics(
            activeNetworkMetered = connectivity.isActiveNetworkMetered,
            restrictBackgroundStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                restrictBackgroundStatusLabel(connectivity.restrictBackgroundStatus)
            } else {
                "unavailable before Android 7"
            },
        )
    }.getOrElse { error ->
        BackgroundNetworkDiagnostics(
            restrictBackgroundStatus = "read failed",
            readError = error.javaClass.simpleName,
        )
    }

    private data class BackgroundWorkItem(
        val label: String,
        /** Where the worker writes its receipts. */
        val uniqueWorkName: String,
        /** Read WorkInfo by tag when one row stands for several unique works, such as one per sound pool. */
        val workTag: String? = null,
    )

    private companion object {
        const val WORK_INFO_TIMEOUT_SECONDS = 2L

        val BACKGROUND_WORK_ITEMS = listOf(
            BackgroundWorkItem("Auto wallpaper rotation", AutoWallpaperWorker.WORK_NAME),
            BackgroundWorkItem("Automatic backup", AutoBackupWorker.WORK_NAME),
            BackgroundWorkItem("Daily wallpaper notification", DailyWallpaperWorker.WORK_NAME),
            BackgroundWorkItem("Ringtone restoration", RingtoneRestorationWorker.WORK_NAME),
            // WorkManager tags every request with its worker class, which covers every pool's periodic and run-now work.
            BackgroundWorkItem("Sound pools", RingtoneShuffleWorker.WORK_NAME, workTag = RingtoneShuffleWorker::class.java.name),
            BackgroundWorkItem("Sound profile", SoundProfileWorker.WORK_NAME),
            BackgroundWorkItem("Wallpaper pack", WallpaperPackWorker.WORK_NAME),
            BackgroundWorkItem("Theme wallpaper switching", SystemThemeListener.WORK_NAME),
            BackgroundWorkItem("Weather wallpaper refresh", WeatherUpdateWorker.WORK_NAME),
            BackgroundWorkItem("Aura Originals download", AuraOriginalsDownloader.WORK_NAME),
            BackgroundWorkItem("Favorites batch download", BatchDownloadWorker.WORK_NAME),
            BackgroundWorkItem("Rotation trigger one-shot", RotationTriggerService.WORK_NAME),
        )
    }
}

internal fun summarizeWorkInfoStates(states: List<WorkInfo.State>): String {
    if (states.isEmpty()) return "No WorkInfo records"
    // groupBy, not groupingBy: groupingBy emits an anonymous Grouping class with no Kotlin
    // metadata, and Kotlin 2.1.0's incremental compiler asserts when it reads that class
    // back ("Couldn't load KotlinClass"), forcing a full non-incremental recompile.
    return states
        .groupBy { it.name }
        .mapValues { (_, group) -> group.size }
        .toSortedMap()
        .entries
        .joinToString(", ") { (state, count) -> "$state=$count" }
}

internal fun summarizeWorkInfoStopReasons(reasons: List<Int>): String? {
    val stopped = reasons.filter { it != WorkInfo.STOP_REASON_NOT_STOPPED }
    if (stopped.isEmpty()) return null
    return stopped
        .map(::workInfoStopReasonLabel)
        .groupBy { it }
        .mapValues { (_, group) -> group.size }
        .toSortedMap()
        .entries
        .joinToString(", ") { (reason, count) -> "$reason=$count" }
}

internal fun workInfoStopReasonLabel(reason: Int): String = when (reason) {
    WorkInfo.STOP_REASON_FOREGROUND_SERVICE_TIMEOUT -> "FOREGROUND_SERVICE_TIMEOUT"
    WorkInfo.STOP_REASON_UNKNOWN -> "UNKNOWN"
    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "CANCELLED_BY_APP"
    WorkInfo.STOP_REASON_PREEMPT -> "PREEMPT"
    WorkInfo.STOP_REASON_TIMEOUT -> "TIMEOUT"
    WorkInfo.STOP_REASON_DEVICE_STATE -> "DEVICE_STATE"
    WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "CONSTRAINT_BATTERY_NOT_LOW"
    WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "CONSTRAINT_CHARGING"
    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "CONSTRAINT_CONNECTIVITY"
    WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "CONSTRAINT_DEVICE_IDLE"
    WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "CONSTRAINT_STORAGE_NOT_LOW"
    WorkInfo.STOP_REASON_QUOTA -> "QUOTA"
    WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "BACKGROUND_RESTRICTION"
    WorkInfo.STOP_REASON_APP_STANDBY -> "APP_STANDBY"
    WorkInfo.STOP_REASON_USER -> "USER"
    WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "SYSTEM_PROCESSING"
    WorkInfo.STOP_REASON_ESTIMATED_APP_LAUNCH_TIME_CHANGED -> "ESTIMATED_APP_LAUNCH_TIME_CHANGED"
    else -> "UNKNOWN($reason)"
}

internal fun restrictBackgroundStatusLabel(status: Int): String = when (status) {
    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
    else -> "unknown($status)"
}

internal fun backgroundWorkActionHint(
    row: BackgroundWorkStatusRow,
    network: BackgroundNetworkDiagnostics,
    batteryGuidance: BackgroundBatteryGuidance = backgroundBatteryGuidanceForManufacturer(""),
): String? {
    row.readError?.let {
        return withBatteryGuidance(
            "Refresh diagnostics; include the support bundle if WorkInfo still cannot be read.",
            batteryGuidance,
        )
    }

    if (network.restrictBackgroundStatus == "enabled" && row.usesNetwork()) {
        return withBatteryGuidance(
            "Data Saver is restricting background data; allow unrestricted data for Aura or use Wi-Fi, then refresh diagnostics.",
            batteryGuidance,
        )
    }
    if (network.activeNetworkMetered == true && row.requiresUnmeteredNetwork()) {
        return withBatteryGuidance(
            "Waiting for Wi-Fi or another unmetered network before this larger download can run.",
            batteryGuidance,
        )
    }
    if (row.lastResult.equals("deferred", ignoreCase = true)) {
        // The reason line already says what the run is waiting for, and none of the fixes below apply.
        return "Nothing to fix. The last run waited for a connection, and the next one tries again on its own."
    }
    val reason = row.lastDeferralReason.orEmpty().lowercase(Locale.ROOT)
    if (row.lastErrorClass == "RotationItemExcluded" || reason.contains("excluded")) {
        return "Restore the item in Settings > Wallpaper rotation > Rotation exclusions, then change theme again."
    }
    if (row.workInfoStatus == "No WorkInfo records") {
        return withBatteryGuidance(
            "No WorkInfo records are visible yet; open Aura once after reboot, wait for the next schedule window, then refresh diagnostics.",
            batteryGuidance,
        )
    }

    if (reason.contains("no eligible bing or wallhaven")) {
        return "No daily wallpaper was available from Bing or Wallhaven; check enabled providers or wait for the next run."
    }
    if (reason.contains("no eligible reddit")) {
        return "No safe Reddit wallpaper was available from a legacy run; switch daily wallpaper to active sources."
    }
    if (reason.contains("hash") || reason.contains("bundle")) {
        return "Aura Originals will retry; repeated failures point to a bundle download, size, hash, or file-write validation problem."
    }
    if (reason.contains("network") || reason.contains("remote") || row.lastErrorClass == "IOException") {
        return "Check connection and provider availability; WorkManager will retry with exponential backoff."
    }
    if (reason.contains("permission")) {
        return "Review the listed Android permission, then refresh diagnostics after granting or changing it."
    }
    if (reason.contains("apply")) {
        return "Open the wallpaper source and try a manual apply; if manual apply fails too, include this support bundle."
    }

    val lastResult = row.lastResult.orEmpty().lowercase(Locale.ROOT)
    if (lastResult == "retry") {
        return withBatteryGuidance(
            "WorkManager scheduled a retry; check network, source settings, battery, charging, and Wi-Fi-only constraints.",
            batteryGuidance,
        )
    }
    if (lastResult == "failure") {
        return "The worker failed instead of retrying; include the support bundle with the last error class."
    }
    if (row.workInfoStatus.contains("ENQUEUED")) {
        return withBatteryGuidance(
            "Waiting for the next run window or constraints such as network, battery, charging, idle, or unmetered network.",
            batteryGuidance,
        )
    }
    return null
}

internal fun backgroundBatteryGuidanceForManufacturer(manufacturer: String): BackgroundBatteryGuidance {
    val normalized = manufacturer.lowercase(Locale.ROOT)
    return when {
        normalized.contains("samsung") -> BackgroundBatteryGuidance(
            manufacturer = "Samsung",
            summary = "Open Settings > Battery > Background usage limits and remove Aura from Sleeping and Deep sleeping apps.",
        )
        normalized.contains("google") || normalized.contains("pixel") -> BackgroundBatteryGuidance(
            manufacturer = "Pixel",
            summary = "Open Settings > Apps > Aura > App battery usage and allow background usage; disable Battery Saver while testing schedules.",
        )
        normalized.contains("xiaomi") || normalized.contains("redmi") || normalized.contains("poco") ->
            BackgroundBatteryGuidance(
                manufacturer = "Xiaomi",
                summary = "Open Settings > Apps > Aura > Battery saver and choose No restrictions; also allow Auto-start.",
            )
        normalized.contains("oneplus") || normalized.contains("oppo") || normalized.contains("realme") ->
            BackgroundBatteryGuidance(
                manufacturer = "OnePlus/OPPO",
                summary = "Open Settings > Battery > Battery optimization > Aura and choose Don't optimize.",
            )
        normalized.contains("huawei") || normalized.contains("honor") -> BackgroundBatteryGuidance(
            manufacturer = "Huawei",
            summary = "Open Settings > Battery > App launch > Aura and allow Auto-launch, Secondary launch, and Run in background.",
        )
        else -> BackgroundBatteryGuidance(
            manufacturer = "Android",
            summary = "Open App info > Battery for Aura and allow unrestricted or background battery usage before retesting schedules.",
        )
    }
}

private fun withBatteryGuidance(base: String, batteryGuidance: BackgroundBatteryGuidance): String =
    "$base OEM recovery (${batteryGuidance.manufacturer}): ${batteryGuidance.summary}"

private fun BackgroundWorkStatusRow.usesNetwork(): Boolean = uniqueWorkName in NETWORK_WORK_NAMES

private fun BackgroundWorkStatusRow.requiresUnmeteredNetwork(): Boolean =
    uniqueWorkName == AURA_ORIGINALS_UNIQUE_WORK_NAME ||
        lastDeferralReason.orEmpty().contains("unmetered", ignoreCase = true) ||
        lastDeferralReason.orEmpty().contains("Wi-Fi", ignoreCase = true)

private const val AURA_ORIGINALS_UNIQUE_WORK_NAME = AuraOriginalsDownloader.WORK_NAME

private val NETWORK_WORK_NAMES = setOf(
    AutoWallpaperWorker.WORK_NAME,
    DailyWallpaperWorker.WORK_NAME,
    WeatherUpdateWorker.WORK_NAME,
    AURA_ORIGINALS_UNIQUE_WORK_NAME,
    "rotation_trigger_oneshot",
)
