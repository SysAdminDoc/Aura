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
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.service.BackgroundWorkDiagnostics
import com.freevibe.service.BackgroundWorkStatusRow
import com.freevibe.service.COMMUNITY_DELETION_REQUEST_SUBJECT
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.CrashDiagnosticsSummary
import com.freevibe.service.ExternalAutomationDiagnostics
import com.freevibe.service.ExternalAutomationDispatcher
import com.freevibe.service.SourceMetrics
import com.freevibe.service.YtDlpUpdateStatus
import com.freevibe.service.communityDeletionRequestBody
import com.freevibe.ui.components.HighlightPill
import com.freevibe.ui.theme.AuraShapes
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

@Composable
internal fun backgroundWorkDiagnosticsSubtitle(status: BackgroundWorkDiagnostics): String {
    if (status.rows.isEmpty()) return stringResource(R.string.settings_diag_background_check_subtitle)
    val receiptCount = status.rows.count { it.workInfoCount > 0 && it.readError == null }
    return stringResource(
        R.string.settings_diag_background_status_subtitle,
        pluralStringResource(R.plurals.settings_diag_workinfo_receipts, receiptCount, receiptCount),
        meteredNetworkLabel(status.network.activeNetworkMetered),
        status.network.restrictBackgroundStatus,
    )
}

@Composable
internal fun externalAutomationSubtitle(status: ExternalAutomationDiagnostics): String {
    val state = stringResource(
        if (status.enabled) R.string.settings_diag_state_enabled else R.string.settings_diag_state_off,
    )
    val last = when {
        status.lastAcceptedAtMs > 0L -> stringResource(
            R.string.settings_external_automation_sub_accepted,
            formatExternalAutomationTime(status.lastAcceptedAtMs),
        )
        status.lastRejectedAtMs > 0L -> stringResource(
            R.string.settings_external_automation_sub_rejected,
            externalAutomationReasonLabel(status.lastRejectedReason),
        )
        else -> stringResource(R.string.settings_external_automation_sub_none)
    }
    return stringResource(R.string.settings_external_automation_subtitle, state, last)
}

@Composable
internal fun crashDiagnosticsSubtitle(summary: CrashDiagnosticsSummary): String {
    val base = if (summary.hasCrashLog) {
        stringResource(
            R.string.settings_diag_crash_last_subtitle,
            summary.lastCrashAt ?: stringResource(R.string.settings_diag_crash_recorded),
        )
    } else {
        stringResource(R.string.settings_diag_crash_none_subtitle)
    }
    // A memory-limiter kill never reaches the crash handler, so it has to be
    // reported next to "no crashes recorded" rather than inside it.
    if (summary.memoryLimiterExitCount <= 0) return base
    val limiter = pluralStringResource(
        R.plurals.settings_diag_memory_limiter_exits,
        summary.memoryLimiterExitCount,
        summary.memoryLimiterExitCount,
    )
    return "$base $limiter"
}

// -- External automation helpers --

internal fun externalAutomationRateLimitLabel(intervalMs: Long): String {
    val seconds = (intervalMs / 1000L).coerceAtLeast(1L)
    return "${seconds}s"
}

internal fun formatExternalAutomationTime(timestampMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(timestampMs))

@Composable
internal fun externalAutomationReasonLabel(reason: String): String = when (reason) {
    "disabled" -> stringResource(R.string.settings_external_automation_reason_disabled)
    "rate_limited" -> stringResource(R.string.settings_external_automation_reason_rate_limited)
    "unsupported_action" -> stringResource(R.string.settings_external_automation_reason_unsupported)
    "" -> stringResource(R.string.settings_diag_none)
    else -> reason
}

@Composable
internal fun externalAutomationActionLabel(action: String): String = when (action) {
    "com.freevibe.action.ROTATE_NOW" -> stringResource(R.string.settings_external_automation_action_rotate)
    "com.freevibe.action.SHUFFLE_NOW" -> stringResource(R.string.settings_external_automation_action_shuffle)
    "" -> stringResource(R.string.settings_diag_none)
    else -> stringResource(R.string.settings_external_automation_action_unsupported)
}

@Composable
internal fun externalAutomationCallerLabel(callerPackage: String): String =
    callerPackage.ifBlank { stringResource(R.string.settings_external_automation_caller_missing) }.let { label ->
        if (label.length <= 28) label else "${label.take(25)}..."
    }

@Composable
internal fun externalAutomationEntryPointLabel(entryPoint: String): String = when (entryPoint) {
    ExternalAutomationDispatcher.ENTRY_POINT_RECEIVER ->
        stringResource(R.string.settings_external_automation_entry_receiver)
    ExternalAutomationDispatcher.ENTRY_POINT_ACTIVITY ->
        stringResource(R.string.settings_external_automation_entry_activity)
    else -> stringResource(R.string.settings_diag_none)
}

@Composable
internal fun meteredNetworkLabel(activeNetworkMetered: Boolean?): String = when (activeNetworkMetered) {
    true -> stringResource(R.string.settings_diag_network_metered)
    false -> stringResource(R.string.settings_diag_network_unmetered)
    null -> stringResource(R.string.settings_diag_network_meter_unknown)
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
    onFeedback(context.getString(R.string.settings_feedback_diagnostics_copied))
}

internal fun copyCommunityDeletionCode(
    context: Context,
    code: String,
    onFeedback: (String) -> Unit,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Aura deletion request code", code))
    onFeedback(context.getString(R.string.settings_feedback_deletion_code_copied))
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
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.settings_share_deletion_request_title)),
        )
    } catch (_: Exception) {
        onFeedback(context.getString(R.string.settings_feedback_share_deletion_unavailable))
    }
}

internal fun shareCrashDiagnosticsBundle(
    context: Context,
    bundle: String,
    onFeedback: (String) -> Unit,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_share_diagnostics_subject))
        putExtra(Intent.EXTRA_TEXT, bundle)
    }
    try {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.settings_share_diagnostics_title)),
        )
    } catch (_: Exception) {
        onFeedback(context.getString(R.string.settings_feedback_share_diagnostics_unavailable))
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
            stringResource(R.string.settings_diag_metric_state),
            if (status.enabled) stringResource(R.string.settings_diag_state_enabled) else stringResource(R.string.settings_diag_state_off),
            if (status.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        )
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_rate_limit),
            externalAutomationRateLimitLabel(status.minIntervalMs),
            MaterialTheme.colorScheme.secondary,
        )
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_last_action),
            externalAutomationActionLabel(status.lastAction),
            MaterialTheme.colorScheme.tertiary,
        )
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_caller),
            externalAutomationCallerLabel(status.lastCallerPackage),
            MaterialTheme.colorScheme.tertiary,
        )
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_entry_point),
            externalAutomationEntryPointLabel(status.lastEntryPoint),
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
        DiagnosticMetricPill(stringResource(R.string.settings_diag_metric_rows), status.rows.size.toString(), MaterialTheme.colorScheme.primary)
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_receipts),
            status.rows.count { it.workInfoCount > 0 && it.readError == null }.toString(),
            MaterialTheme.colorScheme.secondary,
        )
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_network),
            meteredNetworkLabel(network.activeNetworkMetered),
            MaterialTheme.colorScheme.tertiary,
        )
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_data_saver),
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
            stringResource(R.string.settings_diag_network_read_failed, error),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Text(
        stringResource(
            R.string.settings_diag_background_oem_guidance,
            status.batteryGuidance.manufacturer,
            status.batteryGuidance.summary,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
        shape = AuraShapes.Medium,
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
                        hasError -> stringResource(R.string.settings_diag_receipt_read_failed)
                        hasReceipt -> stringResource(R.string.settings_diag_receipt_found)
                        else -> stringResource(R.string.settings_diag_receipt_missing)
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
                stringResource(R.string.settings_diag_background_record_summary, row.workInfoCount, row.maxRunAttemptCount ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.stopReasonStatus?.let { stopReasons ->
                Text(
                    stringResource(R.string.settings_diag_stop_reasons, stopReasons),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            row.lastResult?.let { result ->
                Text(
                    stringResource(R.string.settings_diag_last_result, result),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.lastSuccessUtc != null || row.lastFailureUtc != null) {
                val noneLabel = stringResource(R.string.settings_diag_none)
                Text(
                    stringResource(R.string.settings_diag_last_success_failure, row.lastSuccessUtc ?: noneLabel, row.lastFailureUtc ?: noneLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.lastErrorClass?.let { error ->
                Text(
                    stringResource(R.string.settings_diag_last_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            row.lastDeferralReason?.let { reason ->
                Text(
                    stringResource(R.string.settings_diag_deferral, reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.actionHint?.let { hint ->
                Text(
                    stringResource(R.string.settings_diag_action, hint),
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
                    stringResource(R.string.settings_diag_read_error, error),
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
        shape = AuraShapes.Medium,
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
                Text(stringResource(R.string.settings_diag_source_no_activity_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.settings_diag_source_no_activity_body),
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
        DiagnosticMetricPill(stringResource(R.string.settings_diag_metric_sources), activeSources.toString(), MaterialTheme.colorScheme.primary)
        DiagnosticMetricPill(stringResource(R.string.settings_diag_metric_requests), totalRequests.toString(), MaterialTheme.colorScheme.secondary)
        DiagnosticMetricPill(stringResource(R.string.settings_diag_metric_failures), failures.toString(), if (failures > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
        DiagnosticMetricPill(stringResource(R.string.settings_diag_metric_disabled), disabled.toString(), MaterialTheme.colorScheme.tertiary)
        DiagnosticMetricPill(
            stringResource(R.string.settings_diag_metric_worst_p95),
            p95Worst?.let { stringResource(R.string.settings_diag_latency_ms, it) } ?: stringResource(R.string.settings_diag_not_available),
            MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
internal fun DiagnosticMetricPill(
    label: String,
    value: String,
    tint: Color,
) {
    Surface(
        shape = AuraShapes.Medium,
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
internal fun SourceDiagnosticRow(
    stat: SourceMetrics.SourceStats,
    onRetry: ((String) -> Unit)? = null,
) {
    val persistentFailure = stat.isPersistentlyFailing
    val successPercent = (stat.successRatio * 100).toInt().coerceIn(0, 100)
    val hasActiveFailure = stat.consecutiveFailureCount > 0L
    val disabledOnly = stat.healthState == SourceMetrics.SourceHealthState.DISABLED
    val tint = when {
        persistentFailure -> MaterialTheme.colorScheme.error
        hasActiveFailure -> MaterialTheme.colorScheme.error
        disabledOnly -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val latency = if (stat.p50Ms != null) {
        stringResource(R.string.settings_diag_source_latency, stat.p50Ms, stat.p95Ms ?: 0L)
    } else {
        stringResource(R.string.settings_diag_source_no_latency)
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
        shape = AuraShapes.Medium,
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
                        persistentFailure -> stringResource(R.string.settings_diag_source_persistent_failure)
                        hasActiveFailure -> stringResource(R.string.settings_diag_source_needs_attention)
                        disabledOnly -> stringResource(R.string.settings_diag_source_disabled)
                        else -> stringResource(R.string.settings_diag_source_healthy)
                    },
                    icon = when {
                        persistentFailure -> Icons.Default.ReportProblem
                        hasActiveFailure -> Icons.Default.Error
                        disabledOnly -> Icons.Default.Block
                        else -> Icons.Default.CheckCircle
                    },
                    tint = tint,
                )
            }
            LinearProgressIndicator(
                progress = { stat.successRatio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(AuraShapes.Small),
                color = tint,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            Text(
                stringResource(
                    R.string.settings_diag_source_request_summary,
                    stat.totalRequests,
                    successPercent,
                    stat.consecutiveFailureCount,
                    stat.disabledCount,
                    latency,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.settings_diag_source_last_activity,
                    formatSourceDiagnosticTime(stat.lastSuccessAtMs),
                    formatSourceDiagnosticTime(stat.lastFailureAtMs),
                    formatSourceDiagnosticTime(stat.lastDisabledAtMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.settings_diag_source_fallback_status,
                    sourceFallbackStatusLabel(stat.fallbackStatus),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(
                    R.string.settings_diag_source_retry_action,
                    sourceRetryActionLabel(stat.retryAction),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (persistentFailure || hasActiveFailure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
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
                    stringResource(R.string.settings_diag_source_persistent_failure_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (stat.lastErrorClass != null) {
                Text(
                    stringResource(
                        R.string.settings_diag_last_error_with_detail,
                        stat.lastErrorClass,
                        stat.lastErrorMessage ?: stringResource(R.string.settings_diag_no_detail),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (
                onRetry != null &&
                stat.retryAction != SourceMetrics.SourceRetryAction.REFRESH_IF_NEEDED &&
                stat.retryAction != SourceMetrics.SourceRetryAction.ENABLE_SOURCE
            ) {
                TextButton(onClick = { onRetry(stat.source) }) {
                    Text(stringResource(R.string.settings_diag_source_retry_button))
                }
            }
        }
    }
}

@Composable
internal fun formatSourceDiagnosticTime(timestampMs: Long): String =
    if (timestampMs <= 0L) {
        stringResource(R.string.settings_diag_none)
    } else {
        // Locale.getDefault() is not a composition read, so changing the device
        // language would leave every timestamp on this screen formatted in the old
        // one until something else happened to recompose it.
        // No Locale.getDefault() fallback: locales[0] is non-empty from API 24 and
        // minSdk is 26, so the fallback was unreachable — and reading it here is
        // exactly the non-observable call this avoids.
        val locale = LocalConfiguration.current.locales[0]
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale)
            .format(Date(timestampMs))
    }

@Composable
private fun sourceFallbackStatusLabel(status: SourceMetrics.SourceFallbackStatus): String =
    when (status) {
        SourceMetrics.SourceFallbackStatus.SAVED_OFFLINE_AVAILABLE ->
            stringResource(R.string.settings_diag_source_fallback_saved_offline)
        SourceMetrics.SourceFallbackStatus.CACHE_OR_LOCAL_AVAILABLE ->
            stringResource(R.string.settings_diag_source_fallback_cache_or_local)
        SourceMetrics.SourceFallbackStatus.AUTO_FALLBACK_ACTIVE ->
            stringResource(R.string.settings_diag_source_fallback_auto_active)
        SourceMetrics.SourceFallbackStatus.DISABLED_LOCAL_AVAILABLE ->
            stringResource(R.string.settings_diag_source_fallback_disabled_local)
        SourceMetrics.SourceFallbackStatus.LOCAL_ONLY ->
            stringResource(R.string.settings_diag_source_fallback_local_only)
    }

@Composable
private fun sourceRetryActionLabel(action: SourceMetrics.SourceRetryAction): String =
    when (action) {
        SourceMetrics.SourceRetryAction.REFRESH_IF_NEEDED ->
            stringResource(R.string.settings_diag_source_retry_refresh_if_needed)
        SourceMetrics.SourceRetryAction.RETRY_SOURCE ->
            stringResource(R.string.settings_diag_source_retry_source)
        SourceMetrics.SourceRetryAction.CLEAR_DEGRADED_AND_RETRY ->
            stringResource(R.string.settings_diag_source_retry_clear_degraded)
        SourceMetrics.SourceRetryAction.ENABLE_SOURCE ->
            stringResource(R.string.settings_diag_source_retry_enable_source)
    }

internal fun sourceDisplayName(source: String): String =
    source.split('_', '-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        .ifBlank { source }
