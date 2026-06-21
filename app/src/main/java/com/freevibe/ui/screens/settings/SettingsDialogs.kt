package com.freevibe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER
import com.freevibe.data.repository.CommunityBlockedUser
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.VIDEO_STATS_PREFS_NAME
import com.freevibe.service.effectiveVideoFpsLimit
import com.freevibe.service.shouldUseVideoBatterySaver
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
        title = { Text("Community identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    summary.authLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Identity suffix: ${communityIdentitySuffixLabel(summary)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.deletionRequestCode.isNotBlank()) {
                    Text(
                        "Deletion request code: ${summary.deletionRequestCode}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "No backend deletion request code is available until a Firebase identity exists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Deletion planning covers vote markers, follows, block rows, shares, and local community caches. Public uploads, moderation records, and Firebase Auth deletion use the retained-data review path.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Clear local only removes this device's fallback community identity. It does not delete backend, Auth, or public upload records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
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
                        Text("Clear local")
                    }
                }
                if (summary.deletionRequestCode.isNotBlank()) {
                    TextButton(onClick = { onCopyCode(summary.deletionRequestCode) }) {
                        Text("Copy code")
                    }
                    TextButton(onClick = { onShareRequest(summary) }) {
                        Text("Share")
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
        title = { Text("Blocked creators") },
        text = {
            if (blockedCreators.isEmpty()) {
                Text(
                    "No community creators are hidden for your account.",
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
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
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
    val intervals = listOf(1L to "1 hour", 3L to "3 hours", 6L to "6 hours",
        12L to "12 hours", 24L to "24 hours", 48L to "2 days")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wallpaper change interval") },
        text = {
            Column {
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
                        "No wallpapers applied yet",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Apply at least one wallpaper from the Wallpapers tab and it will show up here as a slot option.",
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
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

// ── Source picker dialog ─────────────────────────────────────────────

@Composable
internal fun SourcePickerDialog(
    currentSource: String,
    wallhavenProviderEnabled: Boolean,
    bingProviderEnabled: Boolean,
    pixabayProviderEnabled: Boolean,
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
    onDismiss: () -> Unit,
    onChooseLocalFolder: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val localFolderReady = isLocalWallpaperFolderReady(localFolderUri, localFolderPermissionActive)
    val sources = listOf(
        "discover" to "Discover (mixed)",
        "favorites" to "My Favorites",
        WALLPAPER_SOURCE_LOCAL_FOLDER to if (localFolderReady) "Local folder" else "Local folder (choose folder)",
        "wallhaven" to "Wallhaven",
        "pixabay" to "Pixabay",
        "bing" to "Bing Daily",
    ).filter { (key, _) ->
        when (key) {
            "wallhaven" -> wallhavenProviderEnabled || currentSource == "wallhaven"
            "pixabay" -> pixabayProviderEnabled || currentSource == "pixabay"
            "bing" -> bingProviderEnabled || currentSource == "bing"
            else -> true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-wallpaper source") },
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
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    val stats = context.getSharedPreferences(VIDEO_STATS_PREFS_NAME, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val lastSeenMs = stats.getLong("last_seen_ms", 0L)
    val serviceFresh = lastSeenMs > 0L && now - lastSeenMs <= 45_000L
    val statsBatteryPercent = if (serviceFresh && stats.contains("battery_percent")) {
        stats.getInt("battery_percent", -1).takeIf { it >= 0 }
    } else {
        null
    }
    val batteryPercent = battery.percent ?: statsBatteryPercent
    val isCharging = battery.isCharging || (serviceFresh && stats.getBoolean("charging", false))
    val statsRequestedFps = if (serviceFresh) stats.getInt("requested_fps", requestedFps) else requestedFps
    val localLowBatterySaver = shouldUseVideoBatterySaver(
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        autoSaverEnabled = autoBatterySaverEnabled,
    )
    val lowBatterySaverActive = localLowBatterySaver ||
        (serviceFresh && stats.getBoolean("low_battery_saver_active", false))
    val effectiveFps = if (serviceFresh) {
        stats.getInt("effective_fps", effectiveVideoFpsLimit(statsRequestedFps, lowBatterySaverActive))
    } else {
        effectiveVideoFpsLimit(statsRequestedFps, lowBatterySaverActive)
    }
    return VideoBatteryDashboardState(
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        serviceFresh = serviceFresh,
        serviceVisible = serviceFresh && stats.getBoolean("visible", false),
        mediaType = if (serviceFresh) stats.getString("media_type", "none") ?: "none" else "none",
        requestedFps = statsRequestedFps,
        effectiveFps = effectiveFps,
        fpsOverlayEnabled = fpsOverlayEnabled,
        lowBatterySaverActive = lowBatterySaverActive,
        scaleMode = if (serviceFresh) stats.getString("scale_mode", "zoom") ?: "zoom" else "zoom",
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
    val batteryLabel = state.batteryPercent?.let { "$it%" } ?: "Unknown"
    val serviceLabel = when {
        state.serviceVisible -> "Active"
        state.serviceFresh -> "Paused"
        else -> "No heartbeat"
    }
    val mediaLabel = when (state.mediaType) {
        "gif" -> "GIF"
        "video" -> "Video"
        else -> "Idle"
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
                    Text("Battery dashboard", style = MaterialTheme.typography.titleMedium)
                    Text(
                        videoBatteryImpactSummary(
                            requestedFps = state.requestedFps,
                            effectiveFps = state.effectiveFps,
                            fpsOverlayEnabled = state.fpsOverlayEnabled,
                            lowBatterySaverActive = state.lowBatterySaverActive,
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
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (state.lowBatterySaverActive) {
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
                    label = "Battery",
                    value = batteryLabel,
                    detail = if (state.isCharging) "Charging" else "Unplugged",
                )
                VideoDashboardMetric(
                    label = "Service",
                    value = serviceLabel,
                    detail = mediaLabel,
                )
                VideoDashboardMetric(
                    label = "Target",
                    value = "${state.effectiveFps} FPS",
                    detail = if (state.lowBatterySaverActive) "Auto-capped" else "Selected",
                )
                VideoDashboardMetric(
                    label = "Presentation",
                    value = if (state.scaleMode == "fit") "Fit" else "Fill",
                    detail = if (state.fpsOverlayEnabled) "Overlay on" else "Overlay off",
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
    val setupSummary = remember(
        selectedStyleCount,
        schedulerEnabled,
        schedulerInterval,
        weatherEffects,
        adaptiveTint,
        autoPreview,
    ) {
        buildList {
            if (selectedStyleCount > 0) add("$selectedStyleCount style preferences")
            if (schedulerEnabled) add("rotation every ${formatInterval(schedulerInterval)}")
            if (weatherEffects) add("weather overlays")
            if (adaptiveTint) add("time-of-day tint")
            if (autoPreview) add("sound previews")
        }.let { enabled ->
            if (enabled.isEmpty()) {
                "Aura is set up with calm defaults. Adjust discovery, automation, and playback here whenever you want."
            } else {
                "Active setup: ${enabled.joinToString(" • ")}."
            }
        }
    }

    GlassCard(modifier = modifier) {
        HighlightPill(
            label = "Personalization overview",
            icon = Icons.Default.Tune,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Make Aura feel intentional",
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
                label = if (selectedStyleCount == 0) "No style bias yet" else "$selectedStyleCount styles selected",
                icon = Icons.Default.Wallpaper,
                tint = MaterialTheme.colorScheme.primary,
            )
            HighlightPill(
                label = if (schedulerEnabled) "Rotation on" else "Rotation off",
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.secondary,
            )
            HighlightPill(
                label = "$videoFpsLimit FPS video",
                icon = Icons.Default.VideoLibrary,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            HighlightPill(
                label = "$configuredApiKeys provider keys",
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
                label = "Automation",
                value = if (schedulerEnabled) formatInterval(schedulerInterval) else "Manual",
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.primary,
            )
            SettingsMetric(
                modifier = Modifier.weight(1f),
                label = "Storage",
                value = cacheUsage.fileUsageLabel,
                icon = Icons.Default.Folder,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

// ── Helper functions ─────────────────────────────────────────────────

internal fun blockedCreatorSubtitle(blocked: CommunityBlockedUser): String {
    val reason = blocked.reason.storageValue.lowercase(Locale.ROOT)
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
    val blockedAt = blocked.createdAt.takeIf { it > 0L }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
    }
    return listOfNotNull(
        "Reason: $reason",
        blockedAt?.let { "Blocked: $it" },
    ).joinToString(" - ")
}

internal fun communityIdentitySubtitle(summary: CommunityIdentitySummary): String =
    if (summary.hasFirebaseIdentity) {
        "${summary.authLabel} - ${communityIdentitySuffixLabel(summary)}"
    } else {
        "No Firebase identity created"
    }

internal fun communityIdentitySuffixLabel(summary: CommunityIdentitySummary): String =
    if (summary.identitySuffix == "Not created") summary.identitySuffix else "...${summary.identitySuffix}"

internal fun isLocalWallpaperFolderReady(
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): Boolean = localFolderUri.isNotBlank() && localFolderPermissionActive

internal fun localWallpaperFolderSubtitle(
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): String = when {
    localFolderUri.isBlank() -> "Choose a local image folder for offline rotation"
    localFolderPermissionActive -> "Folder selected for local-only wallpaper rotation"
    else -> "Permission needs repair; choose the folder again"
}

internal fun wallpaperRotationSourceLabel(
    source: String,
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): String = when (source) {
    WALLPAPER_SOURCE_LOCAL_FOLDER -> when {
        localFolderUri.isBlank() -> "Local folder (choose folder)"
        localFolderPermissionActive -> "Local folder"
        else -> "Local folder (permission needed)"
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

internal fun darkenPercentLabel(percent: Int): String =
    if (percent <= 0) "Off" else "${percent.coerceIn(0, 100)}%"

internal fun rotationDarkenSubtitle(percent: Int, rotationActive: Boolean): String = when {
    percent <= 0 && rotationActive -> "Keep rotated wallpapers unchanged"
    percent <= 0 -> "Saved for the next auto-rotation or trigger you enable"
    rotationActive -> "Darkens rotated wallpapers for clock and status-bar legibility"
    else -> "Dimming is ready but no rotation trigger is active"
}

internal fun autoBackupStatusSubtitle(
    enabled: Boolean,
    folderUri: String,
    folderPermissionActive: Boolean,
    intervalHours: Long,
    keepCount: Int,
): String = when {
    !enabled && folderUri.isBlank() -> "Choose a folder to unlock local, account-free scheduled backups"
    !enabled && !folderPermissionActive -> "Folder permission needs repair before backup can be enabled"
    !enabled -> "Ready. ${formatAutoBackupInterval(intervalHours)} and keeping ${keepCount.coerceAtLeast(1)} files"
    folderUri.isBlank() -> "Choose a backup folder to start scheduled exports"
    !folderPermissionActive -> "Paused. Folder permission needs repair before Aura can write backups"
    else -> "${formatAutoBackupInterval(intervalHours)}; keeping ${keepCount.coerceAtLeast(1)} newest backups"
}

internal fun autoBackupFolderSubtitle(
    folderUri: String,
    folderPermissionActive: Boolean,
): String = when {
    folderUri.isBlank() -> "Choose where Aura should write JSON backup files"
    folderPermissionActive -> "Writable folder selected for scheduled backup"
    else -> "Permission needs repair; choose the folder again"
}

internal fun formatAutoBackupInterval(hours: Long): String = when (hours) {
    12L -> "Every 12 hours"
    24L -> "Daily"
    168L -> "Weekly"
    720L -> "Monthly"
    else -> "Every ${hours.coerceAtLeast(1)} hours"
}

internal fun autoBackupRetentionLabel(keepCount: Int): String =
    "Keep ${keepCount.coerceAtLeast(1)} newest backup${if (keepCount == 1) "" else "s"}"

internal fun countSelectedStyles(raw: String): Int =
    raw.split(",").count { it.trim().isNotBlank() }

internal fun userStylesSummary(raw: String): String {
    val styles = raw.split(",")
        .map { it.trim().lowercase(java.util.Locale.ROOT) }
        .filter { it.isNotBlank() }
    if (styles.isEmpty()) return "No style preference"
    return styles.joinToString(" • ") { stylePreferenceLabel(it) }
}

internal fun stylePreferenceLabel(style: String): String =
    style.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

internal fun touchEffectSummary(raw: String): String = when (raw.uppercase(java.util.Locale.ROOT)) {
    "SUBTLE" -> "Subtle ripples on live wallpapers"
    "STRONG" -> "Ripples and spark bursts on touch"
    else -> "Off"
}

internal fun cacheUsageSubtitle(cacheUsage: CacheUsageState): String =
    buildString {
        append("Using ${cacheUsage.fileUsageLabel} of temp files and offline saves")
        if (cacheUsage.hasWallpaperMetadataCache) {
            append(" + wallpaper feed cache")
        }
    }

internal fun clearCacheConfirmation(cacheUsage: CacheUsageState): String =
    buildString {
        append("This will remove ${cacheUsage.fileUsageLabel} of temporary media and offline favorites")
        if (cacheUsage.hasWallpaperMetadataCache) {
            append(", and reset cached wallpaper feeds")
        }
        append(". Downloaded files are not affected.")
    }
