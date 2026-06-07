package com.freevibe.service

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class BackgroundWorkDiagnostics(
    val network: BackgroundNetworkDiagnostics = BackgroundNetworkDiagnostics(),
    val rows: List<BackgroundWorkStatusRow> = emptyList(),
)

data class BackgroundNetworkDiagnostics(
    val activeNetworkMetered: Boolean? = null,
    val restrictBackgroundStatus: String = "unavailable",
    val readError: String? = null,
)

data class BackgroundWorkStatusRow(
    val label: String,
    val uniqueWorkName: String,
    val workInfoStatus: String,
    val workInfoCount: Int = 0,
    val maxRunAttemptCount: Int? = null,
    val lastSuccessUtc: String? = null,
    val lastFailureUtc: String? = null,
    val lastErrorClass: String? = null,
    val lastResult: String? = null,
    val lastDeferralReason: String? = null,
    val readError: String? = null,
)

interface BackgroundWorkDiagnosticsReader {
    suspend fun read(): BackgroundWorkDiagnostics
}

@Singleton
class AndroidBackgroundWorkDiagnosticsReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptStore: BackgroundWorkReceiptStore,
) : BackgroundWorkDiagnosticsReader {

    override suspend fun read(): BackgroundWorkDiagnostics {
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
        }
        return BackgroundWorkDiagnostics(
            network = readNetworkDiagnostics(),
            rows = rows,
        )
    }

    private fun readWorkInfo(
        manager: WorkManager,
        item: BackgroundWorkItem,
    ): BackgroundWorkStatusRow = runCatching {
        val infos = manager.getWorkInfosForUniqueWork(item.uniqueWorkName)
            .get(WORK_INFO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        val receipt = receiptStore.read(item.uniqueWorkName)
        BackgroundWorkStatusRow(
            label = item.label,
            uniqueWorkName = item.uniqueWorkName,
            workInfoStatus = summarizeWorkInfoStates(infos.map { it.state }),
            workInfoCount = infos.size,
            maxRunAttemptCount = infos.maxOfOrNull { it.runAttemptCount },
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
        val uniqueWorkName: String,
    )

    private companion object {
        const val WORK_INFO_TIMEOUT_SECONDS = 2L

        val BACKGROUND_WORK_ITEMS = listOf(
            BackgroundWorkItem("Auto wallpaper rotation", AutoWallpaperWorker.WORK_NAME),
            BackgroundWorkItem("Daily wallpaper notification", DailyWallpaperWorker.WORK_NAME),
            BackgroundWorkItem("Weather wallpaper refresh", WeatherUpdateWorker.WORK_NAME),
            BackgroundWorkItem("Aura Originals download", "aura_originals_download"),
            BackgroundWorkItem("Rotation trigger one-shot", "rotation_trigger_oneshot"),
        )
    }
}

internal fun summarizeWorkInfoStates(states: List<WorkInfo.State>): String {
    if (states.isEmpty()) return "No WorkInfo records"
    return states
        .groupingBy { it.name }
        .eachCount()
        .toSortedMap()
        .entries
        .joinToString(", ") { (state, count) -> "$state=$count" }
}

internal fun restrictBackgroundStatusLabel(status: Int): String = when (status) {
    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "disabled"
    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "whitelisted"
    ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "enabled"
    else -> "unknown($status)"
}
