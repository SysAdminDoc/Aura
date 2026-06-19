package com.freevibe.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.service.BackgroundWorkDiagnostics
import com.freevibe.service.BackgroundWorkStatusRow
import com.freevibe.service.COMMUNITY_DELETION_REQUEST_SUBJECT
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.CrashDiagnosticsSummary
import com.freevibe.service.ExternalAutomationDiagnostics
import com.freevibe.service.SourceMetrics
import com.freevibe.service.YtDlpUpdateStatus
import com.freevibe.service.communityDeletionRequestBody
import com.freevibe.ui.components.HighlightPill
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostics-related composables and helper functions extracted from SettingsScreen.kt.
 *
 * Covers source diagnostics, background work diagnostics, external automation diagnostics,
 * crash diagnostics, yt-dlp update status, and clipboard/share actions for diagnostics bundles.
 */

// -- Subtitle formatters --

internal fun backgroundWorkDiagnosticsSubtitle(status: BackgroundWorkDiagnostics): String {
    if (status.rows.isEmpty()) return "Check WorkManager and Data Saver state"
    val receiptCount = status.rows.count { it.workInfoCount > 0 && it.readError == null }
    return "$receiptCount WorkInfo receipts • ${meteredNetworkLabel(status.network.activeNetworkMetered)} • Data Saver ${status.network.restrictBackgroundStatus}"
}

internal fun externalAutomationSubtitle(status: ExternalAutomationDiagnostics): String {
    val state = if (status.enabled) "Enabled" else "Off"
    val last = when {
        status.lastAcceptedAtMs > 0L -> "last accepted ${formatExternalAutomationTime(status.lastAcceptedAtMs)}"
        status.lastRejectedAtMs > 0L -> {
            "last rejected: ${externalAutomationReasonLabel(status.lastRejectedReason)}"
        }
        else -> "no external triggers recorded"
    }
    return "$state - $last"
}

internal fun crashDiagnosticsSubtitle(summary: CrashDiagnosticsSummary): String =
    if (summary.hasCrashLog) {
        "Last crash ${summary.lastCrashAt ?: "recorded"} • Copy or share a sanitized issue bundle"
    } else {
        "No local crash log yet • Copy or share environment details if the app freezes"
    }

// -- External automation helpers --

internal fun externalAutomationRateLimitLabel(intervalMs: Long): String {
    val seconds = (intervalMs / 1000L).coerceAtLeast(1L)
    return "${seconds}s"
}

internal fun formatExternalAutomationTime(timestampMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(timestampMs))

internal fun externalAutomationReasonLabel(reason: String): String = when (reason) {
    "disabled" -> "disabled"
    "rate_limited" -> "rate limited"
    "unsupported_action" -> "unsupported action"
    "" -> "none"
    else -> reason
}

internal fun externalAutomationActionLabel(action: String): String = when (action) {
    "com.freevibe.action.ROTATE_NOW" -> "rotate"
    "com.freevibe.action.SHUFFLE_NOW" -> "shuffle"
    "" -> "none"
    else -> "unsupported"
}

internal fun externalAutomationCallerLabel(callerPackage: String): String =
    callerPackage.ifBlank { "not provided" }.let { label ->
        if (label.length <= 28) label else "${label.take(25)}..."
    }

internal fun meteredNetworkLabel(activeNetworkMetered: Boolean?): String = when (activeNetworkMetered) {
    true -> "metered"
    false -> "unmetered"
    null -> "meter unknown"
}

// -- yt-dlp update helpers --

@Composable
internal fun ytDlpUpdateSubtitle(
    state: YtDlpUpdateUiState,
    youtubeProviderEnabled: Boolean,
): String {
    if (!youtubeProviderEnabled) return stringResource(R.string.settings_ytdlp_update_disabled)
    if (state.isUpdating) return stringResource(R.string.settings_ytdlp_update_checking)
    val snapshot = state.snapshot
    val version = snapshot.activeVersionName
        ?: snapshot.activeVersion
        ?: stringResource(R.string.settings_ytdlp_update_version_unknown)
    val lastCheck = if (snapshot.lastAttemptAtMs > 0L) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(snapshot.lastAttemptAtMs))
    } else {
        stringResource(R.string.settings_ytdlp_update_never_checked)
    }
    return stringResource(
        R.string.settings_ytdlp_update_status,
        version,
        lastCheck,
        ytDlpUpdateStatusLabel(snapshot.lastStatus),
    )
}

@Composable
internal fun ytDlpUpdateStatusLabel(status: YtDlpUpdateStatus): String = when (status) {
    YtDlpUpdateStatus.NEVER_RUN -> stringResource(R.string.settings_ytdlp_update_never_checked)
    YtDlpUpdateStatus.CHECKING -> stringResource(R.string.settings_ytdlp_update_checking_short)
    YtDlpUpdateStatus.ALREADY_UP_TO_DATE -> stringResource(R.string.settings_ytdlp_update_already)
    YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION -> stringResource(R.string.settings_ytdlp_update_pending)
    YtDlpUpdateStatus.VALIDATED -> stringResource(R.string.settings_ytdlp_update_validated)
    YtDlpUpdateStatus.ROLLED_BACK -> stringResource(R.string.settings_ytdlp_update_rolled_back)
    YtDlpUpdateStatus.FAILED -> stringResource(R.string.settings_ytdlp_update_failed)
}

@Composable
internal fun ytDlpUpdateFeedbackMessage(state: YtDlpUpdateUiState): String? =
    when (state.completedStatus) {
        YtDlpUpdateStatus.ALREADY_UP_TO_DATE -> stringResource(R.string.settings_ytdlp_update_toast_current)
        YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION -> stringResource(R.string.settings_ytdlp_update_toast_updated)
        YtDlpUpdateStatus.VALIDATED -> stringResource(R.string.settings_ytdlp_update_toast_validated)
        YtDlpUpdateStatus.ROLLED_BACK -> stringResource(R.string.settings_ytdlp_update_toast_rolled_back)
        YtDlpUpdateStatus.FAILED -> stringResource(
            R.string.settings_ytdlp_update_toast_failed,
            state.error ?: stringResource(R.string.settings_ytdlp_update_unknown_error),
        )
        else -> null
    }

// -- Clipboard/share actions --

internal fun copyCrashDiagnosticsBundle(
    context: Context,
    bundle: String,
    onFeedback: (String) -> Unit,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Aura diagnostics", bundle))
    onFeedback("Diagnostics copied")
}

internal fun copyCommunityDeletionCode(
    context: Context,
    code: String,
    onFeedback: (String) -> Unit,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Aura deletion request code", code))
    onFeedback("Deletion request code copied")
}

internal fun shareCommunityDeletionRequest(
    context: Context,
    summary: CommunityIdentitySummary,
    onFeedback: (String) -> Unit,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, COMMUNITY_DELETION_REQUEST_SUBJECT)
        putExtra(Intent.EXTRA_TEXT, communityDeletionRequestBody(summary))
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share deletion request"))
    } catch (_: Exception) {
        onFeedback("No app can share deletion requests")
    }
}

internal fun shareCrashDiagnosticsBundle(
    context: Context,
    bundle: String,
    onFeedback: (String) -> Unit,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Aura diagnostics bundle")
        putExtra(Intent.EXTRA_TEXT, bundle)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Share diagnostics"))
    } catch (_: Exception) {
        onFeedback("No app can share diagnostics")
    }
}

// -- Diagnostics composables --

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ExternalAutomationDiagnosticsSummary(status: ExternalAutomationDiagnostics) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiagnosticMetricPill(
            "State",
            if (status.enabled) "Enabled" else "Off",
            if (status.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        DiagnosticMetricPill(
            "Rate limit",
            externalAutomationRateLimitLabel(status.minIntervalMs),
            MaterialTheme.colorScheme.secondary,
        )
        DiagnosticMetricPill(
            "Last action",
            externalAutomationActionLabel(status.lastAction),
            MaterialTheme.colorScheme.tertiary,
        )
        DiagnosticMetricPill(
            "Caller",
            externalAutomationCallerLabel(status.lastCallerPackage),
            MaterialTheme.colorScheme.tertiary,
        )
    }
    if (status.lastAcceptedAtMs > 0L) {
        Text(
            stringResource(
                R.string.settings_external_automation_last_accepted,
                formatExternalAutomationTime(status.lastAcceptedAtMs),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (status.lastRejectedAtMs > 0L) {
        Text(
            stringResource(
                R.string.settings_external_automation_last_rejected,
                formatExternalAutomationTime(status.lastRejectedAtMs),
                externalAutomationReasonLabel(status.lastRejectedReason),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun BackgroundWorkDiagnosticsSummary(status: BackgroundWorkDiagnostics) {
    val network = status.network
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiagnosticMetricPill("Rows", status.rows.size.toString(), MaterialTheme.colorScheme.primary)
        DiagnosticMetricPill(
            "Receipts",
            status.rows.count { it.workInfoCount > 0 && it.readError == null }.toString(),
            MaterialTheme.colorScheme.secondary,
        )
        DiagnosticMetricPill(
            "Network",
            meteredNetworkLabel(network.activeNetworkMetered),
            MaterialTheme.colorScheme.tertiary,
        )
        DiagnosticMetricPill(
            "Data Saver",
            network.restrictBackgroundStatus,
            if (network.restrictBackgroundStatus == "enabled") {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.tertiary
            },
        )
    }
    network.readError?.let { error ->
        Text(
            "Network diagnostics read failed: $error",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun BackgroundWorkDiagnosticRow(row: BackgroundWorkStatusRow) {
    val hasError = row.readError != null
    val hasReceipt = row.workInfoCount > 0
    val tint = when {
        hasError -> MaterialTheme.colorScheme.error
        hasReceipt -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        row.uniqueWorkName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HighlightPill(
                    label = when {
                        hasError -> "Read failed"
                        hasReceipt -> "Receipt found"
                        else -> "No receipt"
                    },
                    icon = when {
                        hasError -> Icons.Default.Error
                        hasReceipt -> Icons.Default.CheckCircle
                        else -> Icons.Default.Schedule
                    },
                    tint = tint,
                )
            }
            Text(
                row.workInfoStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${row.workInfoCount} records • max attempts ${row.maxRunAttemptCount ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.lastResult?.let { result ->
                Text(
                    "Last result: $result",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.lastSuccessUtc != null || row.lastFailureUtc != null) {
                Text(
                    "Last success ${row.lastSuccessUtc ?: "none"} • last failure ${row.lastFailureUtc ?: "none"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.lastErrorClass?.let { error ->
                Text(
                    "Last error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            row.lastDeferralReason?.let { reason ->
                Text(
                    "Deferral: $reason",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.actionHint?.let { hint ->
                Text(
                    "Action: $hint",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.lastResult == "success") {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            row.readError?.let { error ->
                Text(
                    "Read error: $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun SourceDiagnosticsEmptyState() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.76f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("No activity yet", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Open Wallpapers, Videos, or Sounds to record provider health for this app session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun SourceDiagnosticsSummary(snapshots: List<SourceMetrics.SourceStats>) {
    val totalRequests = remember(snapshots) { snapshots.sumOf { it.totalRequests } }
    val failures = remember(snapshots) { snapshots.sumOf { it.failureCount } }
    val disabled = remember(snapshots) { snapshots.sumOf { it.disabledCount } }
    val activeSources = remember(snapshots) { snapshots.count { it.totalRequests > 0L } }
    val p95Worst = remember(snapshots) { snapshots.mapNotNull { it.p95Ms }.maxOrNull() }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DiagnosticMetricPill("Sources", activeSources.toString(), MaterialTheme.colorScheme.primary)
        DiagnosticMetricPill("Requests", totalRequests.toString(), MaterialTheme.colorScheme.secondary)
        DiagnosticMetricPill("Failures", failures.toString(), if (failures > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
        DiagnosticMetricPill("Disabled", disabled.toString(), MaterialTheme.colorScheme.tertiary)
        DiagnosticMetricPill("Worst p95", p95Worst?.let { "${it}ms" } ?: "n/a", MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
internal fun DiagnosticMetricPill(
    label: String,
    value: String,
    tint: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, color = tint)
        }
    }
}

@Composable
internal fun SourceDiagnosticRow(stat: SourceMetrics.SourceStats) {
    val persistentFailure = stat.isPersistentlyFailing
    val successPercent = (stat.successRatio * 100).toInt().coerceIn(0, 100)
    val hasFailure = stat.failureCount > 0L
    val hasDisabled = stat.disabledCount > 0L
    val tint = when {
        persistentFailure -> MaterialTheme.colorScheme.error
        hasFailure -> MaterialTheme.colorScheme.error
        hasDisabled -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val latency = if (stat.p50Ms != null) {
        "p50 ${stat.p50Ms}ms / p95 ${stat.p95Ms}ms"
    } else {
        "No latency yet"
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.24f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sourceDisplayName(stat.source), style = MaterialTheme.typography.titleSmall)
                HighlightPill(
                    label = when {
                        persistentFailure -> "Persistent failure"
                        hasFailure -> "Needs attention"
                        hasDisabled -> "Disabled"
                        else -> "Healthy"
                    },
                    icon = when {
                        persistentFailure -> Icons.Default.ReportProblem
                        hasFailure -> Icons.Default.Error
                        hasDisabled -> Icons.Default.Block
                        else -> Icons.Default.CheckCircle
                    },
                    tint = tint,
                )
            }
            LinearProgressIndicator(
                progress = { stat.successRatio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = tint,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Text(
                "${stat.totalRequests} requests • $successPercent% success • ${stat.consecutiveFailureCount} consecutive failures • ${stat.disabledCount} disabled • $latency",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stat.providerPolicy?.let { policy ->
                Text(
                    stringResource(R.string.settings_diagnostics_policy, policy.diagnosticSummary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    policy.quotaSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (persistentFailure) {
                Text(
                    "This source has failed repeatedly without a successful response. Try another source or check provider status before retrying.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (stat.lastErrorClass != null) {
                Text(
                    "Last error: ${stat.lastErrorClass} — ${stat.lastErrorMessage ?: "no detail"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

internal fun sourceDisplayName(source: String): String =
    source.split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        .ifBlank { source }
