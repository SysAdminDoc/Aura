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

@Singleton
class CrashDiagnosticsCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
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
        val videoFps = runCatching { prefs.videoFpsLimit.first() }.getOrDefault(0)
        val generatedAt = timestampWithZone(System.currentTimeMillis())

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
        .maxByOrNull { maxOf(it.lastSuccessAtMs, it.lastFailureAtMs) }
        ?.let { stat ->
            val health = "${stat.successCount}/${stat.totalRequests} successful"
            if (stat.lastErrorClass != null) {
                "${stat.source} ($health, last error ${stat.lastErrorClass})"
            } else {
                "${stat.source} ($health)"
            }
        }

    private suspend fun readPref(block: suspend () -> String): String =
        runCatching { block().ifBlank { "none" } }.getOrDefault("unavailable")

    companion object {
        const val CRASH_LOG_FILE_NAME = "crash.log"
        private const val MAX_CRASH_LOG_CHARS = 16_000

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
    private val authorizationHeaderRegex = Regex("""(?i)\bauthorization\s*[:=]\s*Bearer\s+[A-Za-z0-9._~+/=-]+""")
    private val bearerRegex = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val assignmentSecretRegex = Regex(
        """(?i)\b(api[_-]?key|apikey|access[_-]?token|token|password|secret|client[_-]?id|authorization)\b\s*[:=]\s*["']?[^"',\s)&]+""",
    )
    private val querySecretRegex = Regex(
        """(?i)([?&](?:api[_-]?key|apikey|key|access[_-]?token|token|client[_-]?id|password|secret)=)[^&\s]+""",
    )

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
        result = authorizationHeaderRegex.replace(result, "authorization=<redacted>")
        result = bearerRegex.replace(result, "Bearer <redacted>")
        result = assignmentSecretRegex.replace(result) { match ->
            "${match.groupValues[1]}=<redacted>"
        }
        result = querySecretRegex.replace(result) { match ->
            "${match.groupValues[1]}<redacted>"
        }
        return result.trimEnd()
    }
}
