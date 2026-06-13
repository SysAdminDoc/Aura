package com.freevibe.service

import android.content.Context
import android.os.Build
import com.freevibe.BuildConfig
import com.freevibe.data.local.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CrashDiagnosticsSummary(
    val lastCrashAt: String? = null,
    val crashLogBytes: Long = 0L,
    val hasCrashLog: Boolean = false,
)

internal data class BackgroundWorkDiagnosticsRow(
    val label: String,
    val uniqueWorkName: String,
    val enabledState: String,
    val networkPosture: String,
    val constraints: List<String>,
    val workInfoReceipt: String = "pending Settings WorkInfo receipt",
    val dataSaverReceipt: String = "pending ConnectivityManager Data Saver receipt",
)

@Singleton
class CrashDiagnosticsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
    private val backgroundWorkDiagnosticsReader: BackgroundWorkDiagnosticsReader,
    private val ytDlpUpdateManager: YtDlpUpdateManager,
    private val liveWallpaperReceiptStore: LiveWallpaperReceiptStore,
) {
    fun readSummary(): CrashDiagnosticsSummary {
        val logFile = crashLogFile()
        if (!logFile.exists() || logFile.length() <= 0L) return CrashDiagnosticsSummary()
        val raw = runCatching { logFile.readText(Charsets.UTF_8) }.getOrDefault("")
        return CrashDiagnosticsSummary(
            lastCrashAt = CrashDiagnosticsText.parseLastCrashAt(raw),
            crashLogBytes = logFile.length(),
            hasCrashLog = raw.isNotBlank(),
        )
    }

    suspend fun buildBundle(): String {
        val summary = readSummary()
        val crashTail = sanitizedCrashLogTail()
        val activeSource = mostRecentSource()
        val autoSource = readPref { prefs.autoWallpaperSource.first() }
        val schedulerSource = readPref { prefs.schedulerSource.first() }
        val schedulerEnabled = runCatching { prefs.schedulerEnabled.first() }.getOrDefault(false)
        val autoWallpaperEnabled = runCatching { prefs.autoWallpaperEnabled.first() }.getOrDefault(false)
        val requiresCharging = runCatching { prefs.autoWallpaperRequiresCharging.first() }.getOrDefault(false)
        val requiresWiFiOnly = runCatching { prefs.autoWallpaperRequiresWiFiOnly.first() }.getOrDefault(false)
        val requiresIdle = runCatching { prefs.autoWallpaperRequiresIdle.first() }.getOrDefault(false)
        val rotateOnUnlock = runCatching { prefs.rotateOnUnlock.first() }.getOrDefault(false)
        val rotateOnScreenOff = runCatching { prefs.rotateOnScreenOff.first() }.getOrDefault(false)
        val weatherEffectsEnabled = runCatching { prefs.weatherEffectsEnabled.first() }.getOrDefault(false)
        val dailyWallpaperEnabled = runCatching {
            context.getSharedPreferences(WEATHER_WALLPAPER_PREFS, Context.MODE_PRIVATE)
                .getBoolean(DAILY_WALLPAPER_ENABLED_KEY, false)
        }.getOrDefault(false)
        val videoFps = runCatching { prefs.videoFpsLimit.first() }.getOrDefault(0)
        val ytDlpSnapshot = ytDlpUpdateManager.snapshot()
        val generatedAt = timestampWithZone(System.currentTimeMillis())
        val backgroundWork = CrashDiagnosticsText.formatBackgroundWorkSection(
            backgroundWorkRows(
                autoWallpaperEnabled = autoWallpaperEnabled,
                schedulerEnabled = schedulerEnabled,
                requiresCharging = requiresCharging,
                requiresWiFiOnly = requiresWiFiOnly,
                requiresIdle = requiresIdle,
                dailyWallpaperEnabled = dailyWallpaperEnabled,
                weatherEffectsEnabled = weatherEffectsEnabled,
                rotateOnUnlock = rotateOnUnlock,
                rotateOnScreenOff = rotateOnScreenOff,
            ),
        )
        val liveBackgroundWork = runCatching {
            backgroundWorkDiagnosticsReader.read()
        }.getOrNull()

        return buildString {
            appendLine("# Aura diagnostics bundle")
            appendLine()
            appendLine("Generated: $generatedAt")
            appendLine("Automatic upload: disabled. This bundle is only sent if the user copies or shares it.")
            appendLine()
            appendLine("## App")
            appendLine("- Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("- Build type: ${BuildConfig.BUILD_TYPE}")
            appendLine()
            appendLine("## Device")
            appendLine("- Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("- Security patch: ${Build.VERSION.SECURITY_PATCH}")
            appendLine("- Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("- ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
            appendLine()
            appendLine("## Source/provider context")
            appendLine("- Active source/provider this session: ${activeSource ?: "none recorded"}")
            appendLine("- Auto-wallpaper source: $autoSource")
            appendLine("- Scheduler source: $schedulerSource")
            appendLine("- Scheduler enabled: $schedulerEnabled")
            appendLine("- Video wallpaper FPS limit: ${if (videoFps > 0) videoFps else "unavailable"}")
            appendLine("- yt-dlp active version: ${ytDlpSnapshot.activeVersionName ?: ytDlpSnapshot.activeVersion ?: "bundled or unknown"}")
            appendLine(
                "- yt-dlp update status: ${ytDlpSnapshot.lastStatus.name}; " +
                    "last attempt=${if (ytDlpSnapshot.lastAttemptAtMs > 0L) timestampWithZone(ytDlpSnapshot.lastAttemptAtMs) else "never"}; " +
                    "pending validation=${ytDlpSnapshot.pendingValidation}; " +
                    "rollback available=${ytDlpSnapshot.rollbackAvailable}",
            )
            val degraded = sourceMetrics.degradedSources()
            if (degraded.isNotEmpty()) {
                appendLine("- Degraded sources (auto-fallback active): ${degraded.sorted().joinToString(", ")}")
            } else {
                appendLine("- Degraded sources: none")
            }
            appendLine()
            appendLine(backgroundWork)
            if (liveBackgroundWork != null) {
                appendLine()
                appendLine(CrashDiagnosticsText.formatLiveBackgroundWorkReceipts(liveBackgroundWork))
            }
            appendLine()
            appendLine(formatLiveWallpaperReceipts())
            appendLine()
            appendLine("## Last local crash")
            appendLine("- Last crash timestamp: ${summary.lastCrashAt ?: "none recorded"}")
            appendLine("- Crash log bytes: ${summary.crashLogBytes}")
            appendLine()
            appendLine("## Reproduction")
            appendLine("- What happened:")
            appendLine("- What did you expect:")
            appendLine("- Steps to reproduce:")
            appendLine("- How often does it happen:")
            appendLine("- After restart, can you reproduce it:")
            appendLine("- For ANR/freeze: approximate freeze duration and whether Android showed an ANR dialog:")
            appendLine()
            appendLine("## Sanitized crash log tail")
            if (crashTail.isBlank()) {
                appendLine("No local crash log found.")
            } else {
                appendLine("```")
                appendLine(crashTail)
                appendLine("```")
            }
        }
    }

    private fun crashLogFile(): File = File(context.filesDir, CRASH_LOG_FILE_NAME)

    private fun sanitizedCrashLogTail(): String {
        val logFile = crashLogFile()
        if (!logFile.exists() || logFile.length() <= 0L) return ""
        val raw = runCatching { logFile.readText(Charsets.UTF_8) }.getOrDefault("")
        return CrashDiagnosticsText.sanitize(
            raw = CrashDiagnosticsText.tail(raw, MAX_CRASH_LOG_CHARS),
            appPaths = appPrivatePaths(),
        )
    }

    private fun appPrivatePaths(): List<String> = buildList {
        add(context.filesDir.absolutePath)
        add(context.cacheDir.absolutePath)
        add(context.noBackupFilesDir.absolutePath)
    }.filter { it.isNotBlank() }.distinct()

    private fun mostRecentSource(): String? = sourceMetrics.snapshotAll()
        .filter { it.totalRequests > 0L }
        .maxByOrNull { maxOf(it.lastSuccessAtMs, it.lastFailureAtMs, it.lastDisabledAtMs) }
        ?.let { stat ->
            val disabled = if (stat.disabledCount > 0L) ", ${stat.disabledCount} disabled" else ""
            val health = "${stat.successCount}/${stat.activeRequests} successful$disabled"
            if (stat.lastErrorClass != null) {
                "${stat.source} ($health, last error ${stat.lastErrorClass})"
            } else {
                "${stat.source} ($health)"
            }
        }

    private suspend fun readPref(block: suspend () -> String): String =
        runCatching { block().ifBlank { "none" } }.getOrDefault("unavailable")

    private fun backgroundWorkRows(
        autoWallpaperEnabled: Boolean,
        schedulerEnabled: Boolean,
        requiresCharging: Boolean,
        requiresWiFiOnly: Boolean,
        requiresIdle: Boolean,
        dailyWallpaperEnabled: Boolean,
        weatherEffectsEnabled: Boolean,
        rotateOnUnlock: Boolean,
        rotateOnScreenOff: Boolean,
    ): List<BackgroundWorkDiagnosticsRow> = listOf(
        BackgroundWorkDiagnosticsRow(
            label = "Auto wallpaper rotation",
            uniqueWorkName = AutoWallpaperWorker.WORK_NAME,
            enabledState = if (autoWallpaperEnabled || schedulerEnabled) "enabled" else "disabled",
            networkPosture = if (requiresWiFiOnly) "unmetered network" else "connected network",
            constraints = buildList {
                add(if (requiresWiFiOnly) "NetworkType.UNMETERED" else "NetworkType.CONNECTED")
                add("battery not low")
                if (requiresCharging) add("charging")
                if (requiresIdle) add("device idle")
            },
        ),
        BackgroundWorkDiagnosticsRow(
            label = "Daily wallpaper notification",
            uniqueWorkName = DailyWallpaperWorker.WORK_NAME,
            enabledState = if (dailyWallpaperEnabled) "enabled" else "disabled",
            networkPosture = "connected network",
            constraints = listOf("NetworkType.CONNECTED", "notification permission at runtime"),
        ),
        BackgroundWorkDiagnosticsRow(
            label = "Weather wallpaper refresh",
            uniqueWorkName = WeatherUpdateWorker.WORK_NAME,
            enabledState = if (weatherEffectsEnabled) "enabled" else "disabled",
            networkPosture = "connected network",
            constraints = listOf("NetworkType.CONNECTED", "coarse or fine location permission"),
        ),
        BackgroundWorkDiagnosticsRow(
            label = "Aura Originals download",
            uniqueWorkName = AURA_ORIGINALS_WORK_NAME,
            enabledState = "startup enqueue",
            networkPosture = "unmetered network",
            constraints = listOf("NetworkType.UNMETERED", "hash verification", "80 MB bundle cap"),
        ),
        BackgroundWorkDiagnosticsRow(
            label = "Rotation trigger one-shot",
            uniqueWorkName = ROTATION_TRIGGER_WORK_NAME,
            enabledState = when {
                rotateOnUnlock && rotateOnScreenOff -> "unlock and screen-off enabled"
                rotateOnUnlock -> "unlock enabled"
                rotateOnScreenOff -> "screen-off enabled"
                else -> "disabled"
            },
            networkPosture = "connected network",
            constraints = listOf("NetworkType.CONNECTED", "battery not low", "foreground-service trigger opt-in"),
        ),
    )

    private fun formatLiveWallpaperReceipts(): String = buildString {
        appendLine("## Live wallpaper engine receipts")
        val receipts = liveWallpaperReceiptStore.readAll()
        val hasAny = receipts.any { it.lastSurfaceCreatedUtc != null }
        if (!hasAny) {
            append("- No live wallpaper engine activity recorded.")
            return@buildString
        }
        receipts.forEach { r ->
            if (r.lastSurfaceCreatedUtc == null) return@forEach
            appendLine(
                "- ${r.engine}: " +
                    "surfaceCreated=${r.lastSurfaceCreatedUtc}; " +
                    "surfaceDestroyed=${r.lastSurfaceDestroyedUtc ?: "none"}; " +
                    "lastVisible=${r.lastVisibleUtc ?: "none"}; " +
                    "lastHidden=${r.lastHiddenUtc ?: "none"}; " +
                    "lastDraw=${r.lastDrawUtc ?: "none"}; " +
                    "stale=${r.isStale}; " +
                    "recreations=${r.surfaceRecreationCount}; " +
                    "media=${r.mediaPath ?: "none"}; " +
                    "lastError=${r.lastErrorMessage ?: "none"}${if (r.lastErrorUtc != null) " at ${r.lastErrorUtc}" else ""}; " +
                    "lastRecovery=${r.lastRecoveryAction ?: "none"}${if (r.lastRecoveryUtc != null) " at ${r.lastRecoveryUtc}" else ""}",
            )
        }
    }.trimEnd()

    companion object {
        const val CRASH_LOG_FILE_NAME = "crash.log"
        private const val MAX_CRASH_LOG_CHARS = 16_000
        private const val WEATHER_WALLPAPER_PREFS = "freevibe_weather_wp"
        private const val DAILY_WALLPAPER_ENABLED_KEY = "daily_wallpaper_enabled"
        private const val AURA_ORIGINALS_WORK_NAME = "aura_originals_download"
        private const val ROTATION_TRIGGER_WORK_NAME = "rotation_trigger_oneshot"

        fun timestampWithZone(timeMs: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(timeMs))
    }
}

internal object CrashDiagnosticsText {
    private val crashHeaderRegex = Regex("""--- Crash at (.+?) on thread .+? ---""")
    private val appPrivatePathRegex = Regex(
        """(?:/data/(?:user/\d+/|data/)com\.freevibe|/storage/emulated/\d+/Android/data/com\.freevibe)[^\s)'">]*""",
    )
    private val fileUriRegex = Regex("""file://[^\s)'">]+""")

    fun formatCrashEntry(timestampLabel: String, threadName: String, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return "--- Crash at $timestampLabel on thread $threadName ---\n$sw\n"
    }

    fun parseLastCrashAt(raw: String): String? =
        crashHeaderRegex.findAll(raw).lastOrNull()?.groupValues?.getOrNull(1)

    fun tail(raw: String, maxChars: Int): String {
        if (raw.length <= maxChars) return raw.trimEnd()
        val tail = raw.takeLast(maxChars).substringAfter('\n', missingDelimiterValue = raw.takeLast(maxChars))
        return "[tail truncated]\n${tail.trimEnd()}"
    }

    fun sanitize(raw: String, appPaths: List<String> = emptyList()): String {
        var result = raw
        appPaths.filter { it.isNotBlank() }.distinct().forEach { path ->
            result = result.replace(Regex(Regex.escape(path)), "<app-private-path>")
        }
        result = appPrivatePathRegex.replace(result, "<app-private-path>")
        result = fileUriRegex.replace(result, "file://<redacted-path>")
        result = RequestRedactor.redact(result)
        return result.trimEnd()
    }

    fun formatBackgroundWorkSection(rows: List<BackgroundWorkDiagnosticsRow>): String = buildString {
        appendLine("## Background work")
        if (rows.isEmpty()) {
            appendLine("- No background work rows available.")
        } else {
            rows.forEach { row ->
                appendLine(
                    "- ${row.label} (`${row.uniqueWorkName}`): " +
                        "state=${row.enabledState}; " +
                        "network=${row.networkPosture}; " +
                        "constraints=${row.constraints.joinToString(", ")}; " +
                        "WorkInfo=${row.workInfoReceipt}; " +
                        "Data Saver=${row.dataSaverReceipt}",
                )
            }
        }
        appendLine("- Live WorkManager receipt: pending Settings diagnostics via unique-work status lookup.")
        appendLine("- Live Data Saver receipt: pending Settings diagnostics via restricted-background status.")
    }.trimEnd()

    fun formatLiveBackgroundWorkReceipts(status: BackgroundWorkDiagnostics): String = buildString {
        appendLine("## Background work live receipts")
        appendLine(
            "- Network: meter=${status.network.activeNetworkMetered?.let { if (it) "metered" else "unmetered" } ?: "unknown"}; " +
                "Data Saver=${status.network.restrictBackgroundStatus}" +
                (status.network.readError?.let { "; readError=$it" } ?: ""),
        )
        if (status.rows.isEmpty()) {
            appendLine("- No WorkInfo rows available.")
        } else {
            status.rows.forEach { row ->
                appendLine(
                    "- ${row.label} (`${row.uniqueWorkName}`): " +
                        "WorkInfo=${row.workInfoStatus}; " +
                        "records=${row.workInfoCount}; " +
                        "maxAttempts=${row.maxRunAttemptCount ?: 0}; " +
                        "lastResult=${row.lastResult ?: "none"}; " +
                        "lastSuccess=${row.lastSuccessUtc ?: "none"}; " +
                        "lastFailure=${row.lastFailureUtc ?: "none"}; " +
                        "lastError=${row.lastErrorClass ?: "none"}; " +
                        "deferral=${row.lastDeferralReason ?: "none"}; " +
                        "action=${row.actionHint ?: "none"}" +
                        (row.readError?.let { "; readError=$it" } ?: ""),
                )
            }
        }
    }.trimEnd()
}
