package com.freevibe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.CommunityBlockedUser
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.effectiveVideoFpsLimit
import com.freevibe.service.shouldUseVideoBatterySaver
import com.freevibe.service.shouldPauseVideoMotionForPowerSave
import com.freevibe.service.videoBatteryImpactSummary
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// ── Community identity dialog ────────────────────────────────────────

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CommunityIdentityDialog(
    summary: CommunityIdentitySummary,
    cleanupBusy: Boolean,
    onRefresh: () -> Unit,
    onClearLocal: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareRequest: (CommunityIdentitySummary) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dialogs_identity_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    summary.authLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.settings_dialogs_identity_suffix, communityIdentitySuffixLabel(summary)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.deletionRequestCode.isNotBlank()) {
                    Text(
                        stringResource(R.string.settings_dialogs_identity_deletion_code, summary.deletionRequestCode),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        stringResource(R.string.settings_dialogs_identity_no_deletion_code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.settings_dialogs_identity_deletion_planning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.settings_dialogs_identity_clear_local_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onRefresh) { Text(stringResource(R.string.common_refresh)) }
                TextButton(
                    onClick = onClearLocal,
                    enabled = !cleanupBusy,
                ) {
                    if (cleanupBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.settings_dialogs_identity_clear_local))
                    }
                }
                if (summary.deletionRequestCode.isNotBlank()) {
                    TextButton(onClick = { onCopyCode(summary.deletionRequestCode) }) {
                        Text(stringResource(R.string.settings_dialogs_identity_copy_code))
                    }
                    TextButton(onClick = { onShareRequest(summary) }) {
                        Text(stringResource(R.string.settings_dialogs_identity_share))
                    }
                }
            }
        },
    )
}

// ── Blocked creators dialog ──────────────────────────────────────────

@Composable
internal fun BlockedCreatorsDialog(
    blockedCreators: List<CommunityBlockedUser>,
    actionState: CommunityBlockActionState,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dialogs_blocked_title)) },
        text = {
            if (blockedCreators.isEmpty()) {
                Text(
                    stringResource(R.string.settings_dialogs_blocked_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    blockedCreators.forEach { blocked ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        blocked.userId,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        blockedCreatorSubtitle(blocked),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val isBusy = actionState.unblockingUserId == blocked.userId
                                TextButton(
                                    onClick = { onUnblock(blocked.userId) },
                                    enabled = !isBusy,
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text(stringResource(R.string.settings_dialogs_blocked_unblock))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

// ── Interval picker dialog ───────────────────────────────────────────

@Composable
internal fun IntervalPickerDialog(
    currentInterval: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val intervals = listOf(
        1L to stringResource(R.string.settings_dialogs_interval_1h),
        3L to stringResource(R.string.settings_dialogs_interval_3h),
        6L to stringResource(R.string.settings_dialogs_interval_6h),
        12L to stringResource(R.string.settings_dialogs_interval_12h),
        24L to stringResource(R.string.settings_dialogs_interval_24h),
        48L to stringResource(R.string.settings_dialogs_interval_2d),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dialogs_interval_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                intervals.forEach { (hours, label) ->
                    SettingsRadioOptionRow(
                        label = label,
                        selected = currentInterval == hours,
                        onClick = { onSelect(hours) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

// ── Rotation target picker dialog ────────────────────────────────────

/** Reads the stored rotation target the way AutoWallpaperWorker does, so the label matches what runs. */
internal fun autoWallpaperTargetOf(stored: String): WallpaperTarget =
    WallpaperTarget.entries.find { it.name == stored } ?: WallpaperTarget.BOTH

@Composable
internal fun rotationTargetLabel(target: WallpaperTarget): String = when (target) {
    WallpaperTarget.HOME -> stringResource(R.string.detail_home_screen)
    WallpaperTarget.LOCK -> stringResource(R.string.detail_lock_screen)
    WallpaperTarget.BOTH -> stringResource(R.string.detail_home_lock)
}

@Composable
internal fun RotationTargetPickerDialog(
    currentTarget: WallpaperTarget,
    onDismiss: () -> Unit,
    onSelect: (WallpaperTarget) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_wp_target_title)) },
        text = {
            Column {
                listOf(WallpaperTarget.BOTH, WallpaperTarget.HOME, WallpaperTarget.LOCK).forEach { target ->
                    SettingsRadioOptionRow(
                        label = rotationTargetLabel(target),
                        selected = currentTarget == target,
                        onClick = { onSelect(target) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

// ── Wallpaper slot picker dialog ─────────────────────────────────────

@Composable
internal fun WallpaperSlotPickerDialog(
    title: String,
    history: List<com.freevibe.data.model.WallpaperHistoryEntity>,
    onPick: (com.freevibe.data.model.WallpaperHistoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (history.isEmpty()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.settings_dialogs_slot_empty_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_dialogs_slot_empty_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(modifier = Modifier.heightIn(max = 300.dp)) {
                    LazyColumn {
                        items(history.take(10)) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onPick(entry) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(entry.source, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    entry.wallpaperId.take(20),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

// ── Source picker dialog ─────────────────────────────────────────────

@Composable
internal fun SourcePickerDialog(
    currentSource: String,
    wallhavenProviderEnabled: Boolean,
    bingProviderEnabled: Boolean,
    pixabayUsable: Boolean,
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
    localCatalogReady: Boolean = false,
    onDismiss: () -> Unit,
    onChooseLocalFolder: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val localFolderReady = isLocalWallpaperFolderReady(localFolderUri, localFolderPermissionActive) || localCatalogReady
    val sources = listOf(
        "discover" to stringResource(R.string.settings_dialogs_source_discover),
        "favorites" to stringResource(R.string.settings_dialogs_source_favorites),
        WALLPAPER_SOURCE_LOCAL_FOLDER to if (localFolderReady) stringResource(R.string.settings_dialogs_source_local_folder) else stringResource(R.string.settings_dialogs_source_local_folder_choose),
        "wallhaven" to stringResource(R.string.settings_dialogs_source_wallhaven),
        "pixabay" to stringResource(R.string.settings_dialogs_source_pixabay),
        "bing" to stringResource(R.string.settings_dialogs_source_bing),
    ).filter { (key, _) ->
        when (key) {
            "wallhaven" -> wallhavenProviderEnabled || currentSource == "wallhaven"
            "pixabay" -> pixabayUsable || currentSource == "pixabay"
            "bing" -> bingProviderEnabled || currentSource == "bing"
            else -> true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_dialogs_source_title)) },
        text = {
            Column {
                sources.forEach { (key, label) ->
                    val isSelected = currentSource == key
                    val onSelectSource = {
                        if (key == WALLPAPER_SOURCE_LOCAL_FOLDER && !localFolderReady) {
                            onChooseLocalFolder()
                        } else {
                            onSelect(key)
                        }
                    }
                    SettingsRadioOptionRow(
                        label = label,
                        selected = isSelected,
                        onClick = onSelectSource,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

// ── Video battery dashboard ──────────────────────────────────────────

internal data class SettingsBatterySnapshot(
    val percent: Int?,
    val isCharging: Boolean,
)

internal data class VideoBatteryDashboardState(
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val serviceFresh: Boolean,
    val serviceVisible: Boolean,
    val mediaType: String,
    val requestedFps: Int,
    val effectiveFps: Int,
    val fpsOverlayEnabled: Boolean,
    val lowBatterySaverActive: Boolean,
    val systemPowerSaveMode: Boolean,
    val motionPausedForPowerSave: Boolean,
    val scaleMode: String,
)

@Composable
internal fun rememberVideoBatteryDashboardState(
    context: Context,
    requestedFps: Int,
    fpsOverlayEnabled: Boolean,
    autoBatterySaverEnabled: Boolean,
): State<VideoBatteryDashboardState> {
    val appContext = remember(context) { context.applicationContext }
    val state = remember(appContext, requestedFps, fpsOverlayEnabled, autoBatterySaverEnabled) {
        mutableStateOf(
            readVideoBatteryDashboardState(
                context = appContext,
                requestedFps = requestedFps,
                fpsOverlayEnabled = fpsOverlayEnabled,
                autoBatterySaverEnabled = autoBatterySaverEnabled,
            ),
        )
    }
    LaunchedEffect(appContext, requestedFps, fpsOverlayEnabled, autoBatterySaverEnabled) {
        while (true) {
            state.value = readVideoBatteryDashboardState(
                context = appContext,
                requestedFps = requestedFps,
                fpsOverlayEnabled = fpsOverlayEnabled,
                autoBatterySaverEnabled = autoBatterySaverEnabled,
            )
            delay(2_000L)
        }
    }
    return state
}

private fun readVideoBatteryDashboardState(
    context: Context,
    requestedFps: Int,
    fpsOverlayEnabled: Boolean,
    autoBatterySaverEnabled: Boolean,
): VideoBatteryDashboardState {
    val battery = readSettingsBatterySnapshot(context)
    val stats = PreferencesManager.readVideoBatteryStats(context)
    val now = System.currentTimeMillis()
    val serviceFresh = stats.lastSeenMs > 0L && now - stats.lastSeenMs <= 45_000L
    val statsBatteryPercent = stats.batteryPercent.takeIf { serviceFresh }
    val batteryPercent = battery.percent ?: statsBatteryPercent
    val isCharging = battery.isCharging || (serviceFresh && stats.charging)
    val statsRequestedFps = if (serviceFresh) stats.requestedFps else requestedFps
    val localLowBatterySaver = shouldUseVideoBatterySaver(
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        autoSaverEnabled = autoBatterySaverEnabled,
    )
    val lowBatterySaverActive = localLowBatterySaver ||
        (serviceFresh && stats.lowBatterySaverActive)
    val localSystemPowerSaveMode = try {
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    } catch (_: Exception) {
        false
    }
    val systemPowerSaveMode = localSystemPowerSaveMode ||
        (serviceFresh && stats.systemPowerSaveMode)
    val motionPausedForPowerSave = shouldPauseVideoMotionForPowerSave(
        systemPowerSaveMode = systemPowerSaveMode,
        autoSaverEnabled = autoBatterySaverEnabled,
    ) || (serviceFresh && stats.motionPausedForPowerSave)
    val effectiveFps = if (serviceFresh) {
        stats.effectiveFps
    } else {
        effectiveVideoFpsLimit(statsRequestedFps, lowBatterySaverActive)
    }
    return VideoBatteryDashboardState(
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        serviceFresh = serviceFresh,
        serviceVisible = serviceFresh && stats.visible,
        mediaType = if (serviceFresh) stats.mediaType else "none",
        requestedFps = statsRequestedFps,
        effectiveFps = effectiveFps,
        fpsOverlayEnabled = fpsOverlayEnabled,
        lowBatterySaverActive = lowBatterySaverActive,
        systemPowerSaveMode = systemPowerSaveMode,
        motionPausedForPowerSave = motionPausedForPowerSave,
        scaleMode = if (serviceFresh) stats.scaleMode else "zoom",
    )
}

private fun readSettingsBatterySnapshot(context: Context): SettingsBatterySnapshot {
    val intent = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (_: Exception) {
        null
    }
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale).toInt().coerceIn(0, 100)
    } else {
        null
    }
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    return SettingsBatterySnapshot(
        percent = percent,
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoBatteryDashboardCard(
    state: VideoBatteryDashboardState,
    modifier: Modifier = Modifier,
) {
    val batteryLabel = state.batteryPercent?.let { "$it%" } ?: stringResource(R.string.settings_dialogs_battery_unknown)
    val serviceLabel = when {
        state.motionPausedForPowerSave -> stringResource(R.string.settings_dialogs_battery_static)
        state.serviceVisible -> stringResource(R.string.settings_dialogs_battery_active)
        state.serviceFresh -> stringResource(R.string.settings_dialogs_battery_paused)
        else -> stringResource(R.string.settings_dialogs_battery_no_heartbeat)
    }
    val mediaLabel = when (state.mediaType) {
        "gif" -> stringResource(R.string.settings_dialogs_battery_gif)
        "video" -> stringResource(R.string.settings_dialogs_battery_video)
        else -> stringResource(R.string.settings_dialogs_battery_idle)
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_dialogs_battery_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        videoBatteryImpactSummary(
                            requestedFps = state.requestedFps,
                            effectiveFps = state.effectiveFps,
                            fpsOverlayEnabled = state.fpsOverlayEnabled,
                            lowBatterySaverActive = state.lowBatterySaverActive,
                            motionPausedForPowerSave = state.motionPausedForPowerSave,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.batteryPercent?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (state.lowBatterySaverActive || state.motionPausedForPowerSave) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VideoDashboardMetric(
                    label = stringResource(R.string.settings_dialogs_battery_label),
                    value = batteryLabel,
                    detail = if (state.isCharging) stringResource(R.string.settings_dialogs_battery_charging) else stringResource(R.string.settings_dialogs_battery_unplugged),
                )
                VideoDashboardMetric(
                    label = stringResource(R.string.settings_dialogs_battery_service),
                    value = serviceLabel,
                    detail = mediaLabel,
                )
                VideoDashboardMetric(
                    label = stringResource(R.string.settings_dialogs_battery_target),
                    value = if (state.motionPausedForPowerSave) {
                        stringResource(R.string.settings_dialogs_battery_static)
                    } else {
                        stringResource(R.string.settings_dialogs_battery_fps, state.effectiveFps)
                    },
                    detail = when {
                        state.motionPausedForPowerSave -> stringResource(R.string.settings_dialogs_battery_system_saver)
                        state.lowBatterySaverActive -> stringResource(R.string.settings_dialogs_battery_auto_capped)
                        else -> stringResource(R.string.settings_dialogs_battery_selected)
                    },
                )
                VideoDashboardMetric(
                    label = stringResource(R.string.settings_dialogs_battery_presentation),
                    value = if (state.scaleMode == "fit") stringResource(R.string.settings_dialogs_battery_fit) else stringResource(R.string.settings_dialogs_battery_fill),
                    detail = if (state.fpsOverlayEnabled) stringResource(R.string.settings_dialogs_battery_overlay_on) else stringResource(R.string.settings_dialogs_battery_overlay_off),
                )
            }
        }
    }
}

@Composable
private fun VideoDashboardMetric(
    label: String,
    value: String,
    detail: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 116.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Settings overview card ───────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsOverviewCard(
    modifier: Modifier = Modifier,
    selectedStyleCount: Int,
    schedulerEnabled: Boolean,
    schedulerInterval: Long,
    weatherEffects: Boolean,
    adaptiveTint: Boolean,
    autoPreview: Boolean,
    videoFpsLimit: Int,
    cacheUsage: CacheUsageState,
    configuredApiKeys: Int,
) {
    val storageLabel = cacheUsage.fileUsageLabel.ifBlank {
        stringResource(R.string.settings_storage_calculating)
    }
    val strStyleCount = stringResource(R.string.settings_dialogs_overview_style_count, selectedStyleCount)
    val strRotationEvery = stringResource(R.string.settings_dialogs_overview_rotation_every, formatInterval(schedulerInterval))
    val strWeatherOverlays = stringResource(R.string.settings_dialogs_overview_weather_overlays)
    val strTimeTint = stringResource(R.string.settings_dialogs_overview_time_tint)
    val strSoundPreviews = stringResource(R.string.settings_dialogs_overview_sound_previews)
    val strDefaultSummary = stringResource(R.string.settings_dialogs_overview_default_summary)
    val enabledFeatures = remember(
        strStyleCount,
        strRotationEvery,
        strWeatherOverlays,
        strTimeTint,
        strSoundPreviews,
        selectedStyleCount,
        schedulerEnabled,
        weatherEffects,
        adaptiveTint,
        autoPreview,
    ) {
        buildList {
            if (selectedStyleCount > 0) add(strStyleCount)
            if (schedulerEnabled) add(strRotationEvery)
            if (weatherEffects) add(strWeatherOverlays)
            if (adaptiveTint) add(strTimeTint)
            if (autoPreview) add(strSoundPreviews)
        }
    }
    val setupSummary = if (enabledFeatures.isEmpty()) {
        strDefaultSummary
    } else {
        stringResource(R.string.settings_dialogs_overview_active_summary, enabledFeatures.joinToString(" • "))
    }

    GlassCard(modifier = modifier) {
        HighlightPill(
            label = stringResource(R.string.settings_dialogs_overview_pill),
            icon = Icons.Default.Tune,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.settings_dialogs_overview_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = setupSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HighlightPill(
                label = if (selectedStyleCount == 0) stringResource(R.string.settings_dialogs_overview_no_style) else stringResource(R.string.settings_dialogs_overview_styles_selected, selectedStyleCount),
                icon = Icons.Default.Wallpaper,
                tint = MaterialTheme.colorScheme.primary,
            )
            HighlightPill(
                label = if (schedulerEnabled) stringResource(R.string.settings_dialogs_overview_rotation_on) else stringResource(R.string.settings_dialogs_overview_rotation_off),
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.secondary,
            )
            HighlightPill(
                label = stringResource(R.string.settings_dialogs_overview_fps_video, videoFpsLimit),
                icon = Icons.Default.VideoLibrary,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            HighlightPill(
                label = stringResource(R.string.settings_dialogs_overview_provider_keys, configuredApiKeys),
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsMetric(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_dialogs_overview_automation),
                value = if (schedulerEnabled) formatInterval(schedulerInterval) else stringResource(R.string.settings_dialogs_overview_manual),
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.primary,
            )
            SettingsMetric(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.settings_dialogs_overview_storage),
                value = storageLabel,
                icon = Icons.Default.Folder,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

// ── Helper functions ─────────────────────────────────────────────────

@Composable
internal fun blockedCreatorSubtitle(blocked: CommunityBlockedUser): String {
    val reason = blocked.reason.storageValue.lowercase(Locale.ROOT)
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
    val blockedAt = blocked.createdAt.takeIf { it > 0L }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
    }
    return if (blockedAt != null) {
        stringResource(R.string.settings_dialogs_blocked_reason_time, reason, blockedAt)
    } else {
        stringResource(R.string.settings_dialogs_blocked_reason, reason)
    }
}

@Composable
internal fun communityIdentitySubtitle(summary: CommunityIdentitySummary): String =
    if (summary.hasFirebaseIdentity) {
        "${summary.authLabel} - ${communityIdentitySuffixLabel(summary)}"
    } else {
        stringResource(R.string.settings_dialogs_identity_none)
    }

internal fun communityIdentitySuffixLabel(summary: CommunityIdentitySummary): String =
    if (summary.identitySuffix == "Not created") summary.identitySuffix else "...${summary.identitySuffix}"

internal fun isLocalWallpaperFolderReady(
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): Boolean = localFolderUri.isNotBlank() && localFolderPermissionActive

@Composable
internal fun localWallpaperFolderSubtitle(
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): String = when {
    localFolderUri.isBlank() -> stringResource(R.string.settings_wp_local_folder_choose_subtitle)
    localFolderPermissionActive -> stringResource(R.string.settings_wp_local_folder_selected_subtitle)
    else -> stringResource(R.string.settings_folder_permission_repair)
}

@Composable
internal fun wallpaperRotationSourceLabel(
    source: String,
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
    localCatalogReady: Boolean = false,
): String = when (source) {
    WALLPAPER_SOURCE_LOCAL_FOLDER -> when {
        localFolderPermissionActive || localCatalogReady -> stringResource(R.string.settings_dialogs_source_local_folder)
        localFolderUri.isBlank() -> stringResource(R.string.settings_dialogs_source_local_folder_choose)
        else -> stringResource(R.string.settings_dialogs_source_local_folder_permission)
    }
    else -> sourceDisplayName(source)
}

internal fun hasPersistedReadPermission(context: Context, uriString: String): Boolean {
    if (uriString.isBlank()) return false
    return runCatching {
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri.toString() == uriString
        }
    }.getOrDefault(false)
}

internal fun hasPersistedWritePermission(context: Context, uriString: String): Boolean {
    if (uriString.isBlank()) return false
    return runCatching {
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.isWritePermission && permission.uri.toString() == uriString
        }
    }.getOrDefault(false)
}

@Composable
internal fun darkenPercentLabel(percent: Int): String =
    if (percent <= 0) {
        stringResource(R.string.common_off)
    } else {
        stringResource(R.string.settings_wp_dimming_percent, percent.coerceIn(0, 100))
    }

@Composable
internal fun rotationDarkenSubtitle(percent: Int, rotationActive: Boolean): String = when {
    percent <= 0 && rotationActive -> stringResource(R.string.settings_wp_dimming_keep_unchanged)
    percent <= 0 -> stringResource(R.string.settings_wp_dimming_saved_for_next)
    rotationActive -> stringResource(R.string.settings_wp_dimming_active)
    else -> stringResource(R.string.settings_wp_dimming_ready)
}

@Composable
internal fun autoBackupStatusSubtitle(
    enabled: Boolean,
    folderUri: String,
    folderPermissionActive: Boolean,
    intervalHours: Long,
    keepCount: Int,
): String {
    val safeKeepCount = keepCount.coerceAtLeast(1)
    return when {
        !enabled && folderUri.isBlank() -> stringResource(R.string.settings_backup_status_choose_folder)
        !enabled && !folderPermissionActive -> stringResource(R.string.settings_backup_status_permission_repair)
        !enabled -> stringResource(
            R.string.settings_backup_status_ready,
            formatAutoBackupInterval(intervalHours),
            pluralStringResource(R.plurals.settings_backup_keeping_files, safeKeepCount, safeKeepCount),
        )
        folderUri.isBlank() -> stringResource(R.string.settings_backup_status_choose_folder_start)
        !folderPermissionActive -> stringResource(R.string.settings_backup_status_paused)
        else -> stringResource(
            R.string.settings_backup_status_active,
            formatAutoBackupInterval(intervalHours),
            pluralStringResource(R.plurals.settings_backup_keeping_newest, safeKeepCount, safeKeepCount),
        )
    }
}

@Composable
internal fun autoBackupFolderSubtitle(
    folderUri: String,
    folderPermissionActive: Boolean,
): String = when {
    folderUri.isBlank() -> stringResource(R.string.settings_backup_folder_choose_subtitle)
    folderPermissionActive -> stringResource(R.string.settings_backup_folder_selected_subtitle)
    else -> stringResource(R.string.settings_folder_permission_repair)
}

@Composable
internal fun formatAutoBackupInterval(hours: Long): String = when (hours) {
    24L -> stringResource(R.string.settings_picker_backup_interval_daily)
    168L -> stringResource(R.string.settings_picker_backup_interval_weekly)
    720L -> stringResource(R.string.settings_picker_backup_interval_monthly)
    else -> {
        val safeHours = hours.coerceAtLeast(1L).toInt()
        pluralStringResource(R.plurals.settings_backup_interval_every_hours, safeHours, safeHours)
    }
}

@Composable
internal fun autoBackupRetentionLabel(keepCount: Int): String {
    val safeKeepCount = keepCount.coerceAtLeast(1)
    return pluralStringResource(R.plurals.settings_backup_retention_keep, safeKeepCount, safeKeepCount)
}

internal fun countSelectedStyles(raw: String): Int =
    raw.split(",").count { it.trim().isNotBlank() }

@Composable
internal fun userStylesSummary(raw: String): String {
    val styles = raw.split(",")
        .map { it.trim().lowercase(java.util.Locale.ROOT) }
        .filter { it.isNotBlank() }
    if (styles.isEmpty()) return stringResource(R.string.settings_wp_style_no_preference)
    return styles.joinToString(" • ") { stylePreferenceLabel(it) }
}

internal fun stylePreferenceLabel(style: String): String =
    style.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

@Composable
internal fun touchEffectSummary(raw: String): String = when (raw.uppercase(java.util.Locale.ROOT)) {
    "SUBTLE" -> stringResource(R.string.settings_smart_touch_subtle_summary)
    "STRONG" -> stringResource(R.string.settings_smart_touch_strong_summary)
    else -> stringResource(R.string.settings_smart_touch_off)
}

@Composable
internal fun cacheUsageSubtitle(cacheUsage: CacheUsageState): String {
    val storageLabel = cacheUsage.fileUsageLabel.ifBlank {
        stringResource(R.string.settings_storage_calculating)
    }
    return if (cacheUsage.hasWallpaperMetadataCache) {
        stringResource(R.string.settings_storage_cache_usage_with_feed, storageLabel)
    } else {
        stringResource(R.string.settings_storage_cache_usage, storageLabel)
    }
}

@Composable
internal fun clearCacheConfirmation(cacheUsage: CacheUsageState): String {
    val storageLabel = cacheUsage.fileUsageLabel.ifBlank {
        stringResource(R.string.settings_storage_calculating)
    }
    return if (cacheUsage.hasWallpaperMetadataCache) {
        stringResource(R.string.settings_storage_clear_cache_confirmation_with_feed, storageLabel)
    } else {
        stringResource(R.string.settings_storage_clear_cache_confirmation, storageLabel)
    }
}
