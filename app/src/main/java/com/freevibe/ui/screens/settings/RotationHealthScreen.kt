package com.freevibe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.service.RotationHealthSnapshot
import com.freevibe.service.RotationHealthVerdict
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard

/**
 * Everything that decides whether the next automatic wallpaper change happens.
 *
 * Auto-rotation quietly stopping is the most-reported failure in this category
 * and no competitor shows the scheduler at all, so a user whose wallpaper stops
 * changing has nothing to look at. The screen's job is to distinguish "off",
 * "waiting", "the OS is holding it", "the schedule was lost", and "it ran and
 * failed" — five situations that look identical from the home screen and need
 * five different responses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotationHealthScreen(
    onBack: () -> Unit,
    viewModel: RotationHealthViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val testFireRequested by viewModel.testFireRequested.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rotation_health_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            stringResource(R.string.rotation_health_refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        val current = snapshot
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        RotationHealthContent(
            snapshot = current,
            refreshing = refreshing,
            testFireRequested = testFireRequested,
            onRunNow = { viewModel.runNow() },
            onOpenBatterySettings = { openBatteryOptimizationSettings(context) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        )
    }
}

/**
 * The screen's body, taking state rather than a ViewModel.
 *
 * Split out so every verdict can be rendered in a test without WorkManager, a
 * PowerManager, or a Hilt graph — the tracked "test production composables, not
 * look-alike fixtures" item is about exactly this shape.
 */
@Composable
internal fun RotationHealthContent(
    snapshot: RotationHealthSnapshot,
    refreshing: Boolean,
    testFireRequested: Boolean,
    onRunNow: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AuraStateCard(
            icon = snapshot.verdict.icon(),
            title = stringResource(snapshot.verdict.titleRes()),
            description = snapshot.actionHint?.resolve(LocalContext.current)
                ?: stringResource(R.string.rotation_health_healthy_body),
            tone = snapshot.verdict.tone(),
            primaryAction = AuraStateAction(
                label = if (refreshing) {
                    stringResource(R.string.rotation_health_running)
                } else {
                    stringResource(R.string.rotation_health_run_now)
                },
                icon = Icons.Default.PlayArrow,
                onClick = onRunNow,
            ),
            secondaryAction = if (snapshot.ignoringBatteryOptimizations == false) {
                AuraStateAction(
                    label = stringResource(R.string.rotation_health_battery_settings),
                    icon = Icons.Default.BatteryAlert,
                    onClick = onOpenBatterySettings,
                )
            } else {
                null
            },
        )

        if (testFireRequested) {
            Text(
                text = stringResource(R.string.rotation_health_run_now_queued),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        RotationHealthRow(
            label = stringResource(R.string.rotation_health_row_enabled),
            value = stringResource(
                if (snapshot.rotationEnabled) R.string.rotation_health_on
                else R.string.rotation_health_off,
            ),
        )
        snapshot.intervalMinutes?.let {
            RotationHealthRow(
                label = stringResource(R.string.rotation_health_row_interval),
                value = stringResource(R.string.rotation_health_interval_minutes, it),
            )
        }
        snapshot.sourceLabel?.let {
            RotationHealthRow(
                label = stringResource(R.string.rotation_health_row_source),
                value = wallpaperRotationSourceLabel(
                    source = it,
                    localFolderUri = "",
                    localFolderPermissionActive = true,
                ),
            )
        }
        RotationHealthRow(
            label = stringResource(R.string.rotation_health_row_last_fire),
            value = snapshot.lastFireUtc ?: stringResource(R.string.rotation_health_never),
        )
        RotationHealthRow(
            label = stringResource(R.string.rotation_health_row_next_fire),
            // Absent is a real reading, not a gap: WorkManager reports no next
            // fire for a periodic worker the OS is holding, and that is the whole
            // signal a throttled schedule gives off.
            value = snapshot.nextFireUtc
                ?: stringResource(R.string.rotation_health_next_fire_unscheduled),
        )
        RotationHealthRow(
            label = stringResource(R.string.rotation_health_row_work_state),
            value = workInfoStatusText(snapshot.workState),
        )
        snapshot.stopReason?.let {
            RotationHealthRow(
                label = stringResource(R.string.rotation_health_row_stop_reason),
                value = it,
            )
        }
        RotationHealthRow(
            label = stringResource(R.string.rotation_health_row_boot_receiver),
            value = snapshot.bootReceiverLastUtc
                ?: stringResource(R.string.rotation_health_boot_never_seen),
        )
        RotationHealthRow(
            label = stringResource(R.string.rotation_health_row_battery_exemption),
            value = when (snapshot.ignoringBatteryOptimizations) {
                true -> stringResource(R.string.rotation_health_battery_exempt)
                false -> stringResource(R.string.rotation_health_battery_restricted)
                // The device refused to answer. Saying so beats printing either
                // state, because both would be a guess presented as a fact.
                null -> stringResource(R.string.rotation_health_unknown)
            },
        )
        snapshot.lastFailureUtc?.let {
            RotationHealthRow(
                label = stringResource(R.string.rotation_health_row_last_failure),
                value = snapshot.lastErrorClass?.let { error -> "$it ($error)" } ?: it,
            )
        }
        snapshot.lastDeferralReason?.let {
            RotationHealthRow(
                label = stringResource(R.string.rotation_health_row_deferral),
                value = receiptReasonText(it),
            )
        }
        snapshot.readError?.let {
            RotationHealthRow(
                label = stringResource(R.string.rotation_health_row_read_error),
                value = it,
            )
        }
    }
}

@Composable
private fun RotationHealthRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun RotationHealthVerdict.tone(): Color = when (this) {
    RotationHealthVerdict.HEALTHY -> MaterialTheme.colorScheme.primary
    RotationHealthVerdict.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
    RotationHealthVerdict.THROTTLED -> MaterialTheme.colorScheme.tertiary
    RotationHealthVerdict.NOT_SCHEDULED, RotationHealthVerdict.FAILING ->
        MaterialTheme.colorScheme.error
}

private fun RotationHealthVerdict.icon(): ImageVector = when (this) {
    RotationHealthVerdict.HEALTHY -> Icons.Default.CheckCircle
    RotationHealthVerdict.DISABLED -> Icons.Default.PauseCircleOutline
    RotationHealthVerdict.THROTTLED -> Icons.Default.BatteryAlert
    RotationHealthVerdict.NOT_SCHEDULED -> Icons.Default.HourglassEmpty
    RotationHealthVerdict.FAILING -> Icons.Default.ErrorOutline
}

private fun RotationHealthVerdict.titleRes(): Int = when (this) {
    RotationHealthVerdict.HEALTHY -> R.string.rotation_health_verdict_healthy
    RotationHealthVerdict.DISABLED -> R.string.rotation_health_verdict_disabled
    RotationHealthVerdict.THROTTLED -> R.string.rotation_health_verdict_throttled
    RotationHealthVerdict.NOT_SCHEDULED -> R.string.rotation_health_verdict_not_scheduled
    RotationHealthVerdict.FAILING -> R.string.rotation_health_verdict_failing
}

/**
 * Opens the per-app battery screen, falling back to the global list.
 *
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is the direct route but Play
 * policy restricts it and several OEMs do not resolve it at all, so the settings
 * list is the reliable destination even though it costs the user a tap.
 */
private fun openBatteryOptimizationSettings(context: Context) {
    val candidates = listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )
    for (intent in candidates) {
        val launched = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (launched) return
    }
}
