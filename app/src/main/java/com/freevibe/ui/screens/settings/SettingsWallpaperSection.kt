package com.freevibe.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER
import com.freevibe.service.ExternalAutomationDiagnostics
import com.freevibe.service.OemBatteryGuidance
import com.freevibe.service.WallpaperClockOverlayMode
import com.freevibe.service.WallpaperClockOverlayPosition
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WallpaperRotationSettingsSection(
    context: Context,
    viewModel: SettingsViewModel,
    autoWpEnabled: Boolean,
    autoWpInterval: Long,
    autoWpSource: String,
    autoWpTarget: String,
    localWallpaperFolderUri: String,
    localFolderPermissionActive: Boolean,
    localWallpaperFolderCount: Int,
    localCatalogReady: Boolean,
    autoWpRequiresCharging: Boolean,
    autoWpRequiresWiFi: Boolean,
    autoWpRequiresIdle: Boolean,
    autoWallpaperDarkenPercent: Int,
    autoWallpaperNightVariantEnabled: Boolean,
    schedulerEnabled: Boolean,
    rotateOnUnlock: Boolean,
    rotateOnScreenOff: Boolean,
    avoidRecentRepeats: Boolean,
    restartRotationOnManualApply: Boolean,
    wallpaperPackEnabled: Boolean,
    wallpaperPackJson: String,
    externalAutomationDiagnostics: ExternalAutomationDiagnostics,
    gridColumns: Int,
    preferredRes: String,
    userStyles: String,
    wallpaperStyleLearningSignalCount: Int,
    bingProviderEnabled: Boolean,
    redditProviderEnabled: Boolean,
    wallhavenProviderEnabled: Boolean,
    /** Switched on and keyed; a switched-on Pixabay without a key loads nothing. */
    pixabayUsable: Boolean,
    /** Categories run a wallpaper search, which Pexels with a key can also answer. */
    wallpaperSearchReachable: Boolean,
    wallpaperHistoryCount: Int,
    rotationExclusionCount: Int,
    wallpaperClockOverlayEnabled: Boolean,
    wallpaperClockOverlayMode: WallpaperClockOverlayMode,
    wallpaperClockOverlayPosition: WallpaperClockOverlayPosition,
    onChooseLocalWallpaperFolder: (String?) -> Unit,
    onManageLocalWallpaperFolders: () -> Unit,
    onPickVideoWallpaper: () -> Unit,
    onPickParallaxImage: () -> Unit,
    onCategoriesClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onManageRotationExclusions: () -> Unit,
    onFeedback: (String) -> Unit,
) {
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showTargetPicker by remember { mutableStateOf(false) }
    var showColumnsPicker by remember { mutableStateOf(false) }
    var showPackEditor by remember { mutableStateOf(false) }
    var showResPicker by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showClockModePicker by remember { mutableStateOf(false) }
    var showClockPositionPicker by remember { mutableStateOf(false) }

    SettingsSection(
        sectionKey = SettingsSectionKeys.WALLPAPERS,
        title = stringResource(R.string.settings_wallpapers_section_title),
        description = stringResource(R.string.settings_wallpapers_section_description),
    ) {
        SettingsToggle(
            icon = Icons.Default.AutoAwesome,
            title = stringResource(R.string.settings_wp_auto_change_title),
            subtitle = stringResource(R.string.settings_wp_auto_change_subtitle),
            checked = autoWpEnabled,
            onCheckedChange = viewModel::setAutoWallpaper,
        )
        if (autoWpEnabled) {
            SettingsItem(
                icon = Icons.Default.Timer,
                title = stringResource(R.string.settings_wp_change_interval_title),
                subtitle = stringResource(R.string.settings_wp_change_interval_subtitle, autoWpInterval),
                onClick = { showIntervalPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.Source,
                title = stringResource(R.string.settings_wp_source_title),
                subtitle = wallpaperRotationSourceLabel(
                    source = autoWpSource,
                    localFolderUri = localWallpaperFolderUri,
                    localFolderPermissionActive = localFolderPermissionActive,
                    localCatalogReady = localCatalogReady,
                ),
                onClick = { showSourcePicker = true },
            )
            if (autoWpSource == "reddit") RedditRotationPresetPicker()
            SettingsItem(
                icon = Icons.Default.Wallpaper,
                title = stringResource(R.string.settings_wp_target_title),
                // The scheduler runs instead of this rotation when it's on, with its own home and lock switches.
                subtitle = if (schedulerEnabled) {
                    stringResource(R.string.settings_wp_target_scheduler_note)
                } else {
                    rotationTargetLabel(autoWallpaperTargetOf(autoWpTarget))
                },
                onClick = { showTargetPicker = true },
            )
            SettingsToggle(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.settings_wp_charging_only_title),
                subtitle = stringResource(R.string.settings_wp_charging_only_subtitle),
                checked = autoWpRequiresCharging,
                onCheckedChange = viewModel::setAutoWallpaperRequiresCharging,
            )
            SettingsToggle(
                icon = Icons.Default.Wifi,
                title = stringResource(R.string.settings_wp_wifi_only_title),
                subtitle = stringResource(R.string.settings_wp_wifi_only_subtitle),
                checked = autoWpRequiresWiFi,
                onCheckedChange = viewModel::setAutoWallpaperRequiresWiFiOnly,
                searchAliases = setOf("wifi", "wi-fi", "network"),
            )
            SettingsToggle(
                icon = Icons.Default.Bedtime,
                title = stringResource(R.string.settings_wp_idle_only_title),
                subtitle = stringResource(R.string.settings_wp_idle_only_subtitle),
                checked = autoWpRequiresIdle,
                onCheckedChange = viewModel::setAutoWallpaperRequiresIdle,
            )
        }
        SettingsValueSlider(
            icon = Icons.Default.Brightness4,
            title = stringResource(R.string.settings_wp_dimming_title),
            subtitle = rotationDarkenSubtitle(
                percent = autoWallpaperDarkenPercent,
                rotationActive = autoWpEnabled || schedulerEnabled || rotateOnUnlock || rotateOnScreenOff,
            ),
            valueLabel = darkenPercentLabel(autoWallpaperDarkenPercent),
            value = autoWallpaperDarkenPercent.toFloat(),
            valueRange = 0f..100f,
            steps = 9,
            onValueChange = { viewModel.setAutoWallpaperDarkenPercent(it.roundToInt()) },
        )
        SettingsToggle(
            icon = Icons.Default.Schedule,
            title = stringResource(R.string.settings_wp_clock_overlay_title),
            subtitle = if (wallpaperClockOverlayEnabled) {
                stringResource(R.string.settings_wp_clock_overlay_on_subtitle)
            } else {
                stringResource(R.string.settings_wp_clock_overlay_off_subtitle)
            },
            checked = wallpaperClockOverlayEnabled,
            onCheckedChange = viewModel::setWallpaperClockOverlayEnabled,
        )
        if (wallpaperClockOverlayEnabled) {
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.settings_wp_clock_overlay_mode_title),
                subtitle = when (wallpaperClockOverlayMode) {
                    WallpaperClockOverlayMode.TIME -> stringResource(R.string.settings_wp_clock_overlay_mode_time)
                    WallpaperClockOverlayMode.DATE -> stringResource(R.string.settings_wp_clock_overlay_mode_date)
                    WallpaperClockOverlayMode.TIME_AND_DATE -> stringResource(R.string.settings_wp_clock_overlay_mode_both)
                },
                onClick = { showClockModePicker = true },
            )
            SettingsItem(
                icon = Icons.Default.GridView,
                title = stringResource(R.string.settings_wp_clock_overlay_position_title),
                subtitle = when (wallpaperClockOverlayPosition) {
                    WallpaperClockOverlayPosition.TOP_LEFT -> stringResource(R.string.settings_wp_clock_overlay_position_top_left)
                    WallpaperClockOverlayPosition.TOP_RIGHT -> stringResource(R.string.settings_wp_clock_overlay_position_top_right)
                    WallpaperClockOverlayPosition.BOTTOM_LEFT -> stringResource(R.string.settings_wp_clock_overlay_position_bottom_left)
                    WallpaperClockOverlayPosition.BOTTOM_RIGHT -> stringResource(R.string.settings_wp_clock_overlay_position_bottom_right)
                },
                onClick = { showClockPositionPicker = true },
            )
        }
        SettingsToggle(
            icon = Icons.Default.Bedtime,
            title = stringResource(R.string.settings_wp_night_variant_title),
            subtitle = if (autoWallpaperNightVariantEnabled) {
                stringResource(R.string.settings_wp_night_variant_on_subtitle)
            } else {
                stringResource(R.string.settings_wp_night_variant_off_subtitle)
            },
            checked = autoWallpaperNightVariantEnabled,
            onCheckedChange = viewModel::setAutoWallpaperNightVariantEnabled,
            searchAliases = setOf("oled", "theme", "dark mode"),
        )
        val rotationActive = autoWpEnabled || schedulerEnabled || rotateOnUnlock || rotateOnScreenOff
        if (rotationActive) {
            val oemGuide = remember { OemBatteryGuidance.detect(context) }
            if (oemGuide != null) {
                SettingsItem(
                    icon = Icons.Default.BatteryAlert,
                    title = stringResource(R.string.settings_wp_oem_battery_title, oemGuide.manufacturer),
                    subtitle = oemGuide.summary,
                    onClick = {
                        val intent = oemGuide.settingsIntent
                        if (intent != null) {
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onFeedback(context.getString(R.string.settings_feedback_oem_battery_open_failed, oemGuide.manufacturer))
                            }
                        } else {
                            onFeedback(context.getString(R.string.settings_feedback_oem_battery_not_found, oemGuide.manufacturer))
                        }
                    },
                )
            }
        }
        SettingsItem(
            icon = Icons.Default.FolderOpen,
            title = stringResource(R.string.settings_wp_local_folder_title),
            subtitle = if (localWallpaperFolderCount > 0) {
                stringResource(R.string.settings_local_catalog_subtitle_count, localWallpaperFolderCount)
            } else {
                localWallpaperFolderSubtitle(
                    localWallpaperFolderUri,
                    localFolderPermissionActive,
                )
            },
            onClick = onManageLocalWallpaperFolders,
        )
        if (localWallpaperFolderUri.isNotBlank()) {
            SettingsItem(
                icon = Icons.Default.DeleteOutline,
                title = stringResource(R.string.settings_wp_clear_local_folder_title),
                subtitle = stringResource(R.string.settings_wp_clear_local_folder_subtitle),
                onClick = viewModel::clearLocalWallpaperFolderUri,
            )
        }
        SettingsToggle(
            icon = Icons.Default.LockOpen,
            title = stringResource(R.string.settings_wp_unlock_title),
            subtitle = stringResource(R.string.settings_wp_unlock_subtitle),
            checked = rotateOnUnlock,
            onCheckedChange = viewModel::setRotateOnUnlock,
        )
        SettingsToggle(
            icon = Icons.Default.PowerSettingsNew,
            title = stringResource(R.string.settings_wp_screen_off_title),
            subtitle = stringResource(R.string.settings_wp_screen_off_subtitle),
            checked = rotateOnScreenOff,
            onCheckedChange = viewModel::setRotateOnScreenOff,
        )
        SettingsToggle(
            icon = Icons.Default.Shuffle,
            title = stringResource(R.string.settings_wp_avoid_repeats_title),
            subtitle = stringResource(R.string.settings_wp_avoid_repeats_subtitle),
            checked = avoidRecentRepeats,
            onCheckedChange = viewModel::setAvoidRecentRepeats,
        )
        SettingsToggle(
            icon = Icons.Default.RestartAlt,
            title = stringResource(R.string.settings_wp_restart_on_manual_title),
            subtitle = stringResource(R.string.settings_wp_restart_on_manual_subtitle),
            checked = restartRotationOnManualApply,
            onCheckedChange = viewModel::setRestartRotationOnManualApply,
            searchAliases = setOf("countdown", "timer", "interval"),
        )
        SettingsItem(
            icon = Icons.Default.PlaylistRemove,
            title = stringResource(R.string.settings_rotation_exclusions_title),
            subtitle = if (rotationExclusionCount > 0) {
                stringResource(R.string.settings_rotation_exclusions_subtitle, rotationExclusionCount)
            } else {
                stringResource(R.string.settings_rotation_exclusions_manage_subtitle)
            },
            onClick = onManageRotationExclusions,
            searchAliases = setOf("exclude", "skip", "rotation", "restore"),
        )
        val packSlotCount = remember(wallpaperPackJson) {
            com.freevibe.service.usableSlots(com.freevibe.service.parsePack(wallpaperPackJson)).size
        }
        SettingsToggle(
            icon = Icons.Default.WbTwilight,
            title = stringResource(R.string.settings_wp_pack_title),
            subtitle = when {
                packSlotCount == 0 -> stringResource(R.string.settings_wp_pack_empty_subtitle)
                wallpaperPackEnabled -> stringResource(R.string.settings_wp_pack_on_subtitle, packSlotCount)
                else -> stringResource(R.string.settings_wp_pack_off_subtitle)
            },
            checked = wallpaperPackEnabled,
            onCheckedChange = { on ->
                viewModel.setWallpaperPackEnabled(on)
                if (on && packSlotCount == 0) {
                    onFeedback(context.getString(R.string.settings_wp_pack_needs_slot_feedback))
                }
            },
        )
        SettingsItem(
            icon = Icons.Default.WbTwilight,
            title = stringResource(R.string.settings_wp_pack_edit_title),
            subtitle = stringResource(R.string.settings_wp_pack_edit_subtitle, packSlotCount),
            onClick = { showPackEditor = true },
            searchAliases = setOf("morning", "evening", "night", "daypart", "time of day"),
        )
        SettingsToggle(
            icon = Icons.Default.Schedule,
            title = stringResource(R.string.settings_external_automation_title),
            subtitle = externalAutomationSubtitle(externalAutomationDiagnostics),
            checked = externalAutomationDiagnostics.enabled,
            onCheckedChange = viewModel::setExternalAutomationEnabled,
        )
        SettingsItem(
            icon = Icons.Default.GridView,
            title = stringResource(R.string.settings_wp_grid_columns_title),
            subtitle = stringResource(R.string.settings_wp_grid_columns_subtitle, gridColumns),
            onClick = { showColumnsPicker = true },
        )
        SettingsItem(
            icon = Icons.Default.VideoFile,
            title = stringResource(R.string.settings_wp_video_gif_title),
            subtitle = stringResource(R.string.settings_wp_video_gif_subtitle),
            onClick = onPickVideoWallpaper,
        )
        SettingsItem(
            icon = Icons.Default.PhotoLibrary,
            title = stringResource(R.string.settings_wp_parallax_title),
            subtitle = stringResource(R.string.settings_wp_parallax_subtitle),
            onClick = onPickParallaxImage,
        )
        SettingsItem(
            icon = Icons.Default.PhotoSizeSelectLarge,
            title = stringResource(R.string.settings_wp_resolution_title),
            subtitle = if (preferredRes.isEmpty()) stringResource(R.string.settings_wp_resolution_any) else preferredRes,
            onClick = { showResPicker = true },
        )
        SettingsItem(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.settings_wp_style_title),
            subtitle = userStylesSummary(userStyles),
            onClick = { showStylePicker = true },
        )
        if (wallpaperStyleLearningSignalCount > 0) {
            SettingsItem(
                icon = Icons.Default.DeleteOutline,
                title = stringResource(R.string.settings_wp_style_learning_reset_title),
                subtitle = stringResource(
                    R.string.settings_wp_style_learning_reset_subtitle,
                    wallpaperStyleLearningSignalCount,
                ),
                onClick = viewModel::resetWallpaperStyleLearning,
            )
        }
        SettingsToggle(
            icon = Icons.Default.Forum,
            title = stringResource(R.string.settings_wp_reddit_title),
            subtitle = if (redditProviderEnabled) {
                stringResource(R.string.settings_wp_reddit_on_subtitle)
            } else {
                stringResource(R.string.settings_wp_reddit_off_subtitle)
            },
            checked = redditProviderEnabled,
            onCheckedChange = viewModel::setRedditProviderEnabled,
        )
        RedditFeedPresetManager(RedditFeedMediaType.WALLPAPER)
        SettingsToggle(
            icon = Icons.Default.ImageSearch,
            title = stringResource(R.string.settings_wp_bing_title),
            subtitle = if (bingProviderEnabled) {
                stringResource(R.string.settings_wp_bing_on_subtitle)
            } else {
                stringResource(R.string.settings_wp_bing_off_subtitle)
            },
            checked = bingProviderEnabled,
            onCheckedChange = viewModel::setBingProviderEnabled,
        )
        val categoriesAvailable = wallpaperSearchReachable
        SettingsItem(
            icon = Icons.Default.Category,
            title = stringResource(R.string.settings_wp_categories_title),
            subtitle = stringResource(
                if (categoriesAvailable) {
                    R.string.settings_wp_categories_subtitle
                } else {
                    R.string.settings_wp_categories_unavailable_subtitle
                }
            ),
            onClick = {
                if (categoriesAvailable) {
                    onCategoriesClick()
                } else {
                    onFeedback(context.getString(R.string.settings_wp_categories_unavailable_feedback))
                }
            },
        )
        SettingsItem(
            icon = Icons.Default.Folder,
            title = stringResource(R.string.settings_wp_collections_title),
            subtitle = stringResource(R.string.settings_wp_collections_subtitle),
            onClick = onCollectionsClick,
        )
        if (wallpaperHistoryCount > 0) {
            SettingsItem(
                icon = Icons.Default.History,
                title = stringResource(R.string.settings_wp_history_title),
                subtitle = stringResource(R.string.settings_wp_history_subtitle, wallpaperHistoryCount),
                onClick = onHistoryClick,
            )
        }
    }

    if (showIntervalPicker) {
        IntervalPickerDialog(
            currentInterval = autoWpInterval,
            onDismiss = { showIntervalPicker = false },
            onSelect = { hours ->
                viewModel.setAutoWpInterval(hours)
                showIntervalPicker = false
            },
        )
    }

    if (showTargetPicker) {
        RotationTargetPickerDialog(
            currentTarget = autoWallpaperTargetOf(autoWpTarget),
            onDismiss = { showTargetPicker = false },
            onSelect = { target ->
                viewModel.setAutoWpTarget(target)
                showTargetPicker = false
            },
        )
    }

    if (showSourcePicker) {
        SourcePickerDialog(
            currentSource = autoWpSource,
            wallhavenProviderEnabled = wallhavenProviderEnabled,
            bingProviderEnabled = bingProviderEnabled,
            pixabayUsable = pixabayUsable,
            localFolderUri = localWallpaperFolderUri,
            localFolderPermissionActive = localFolderPermissionActive,
            localCatalogReady = localCatalogReady,
            onDismiss = { showSourcePicker = false },
            onChooseLocalFolder = {
                showSourcePicker = false
                onChooseLocalWallpaperFolder("auto")
            },
            onSelect = { source ->
                viewModel.setAutoWpSource(source)
                showSourcePicker = false
            },
        )
    }

    if (showPackEditor) {
        WallpaperPackEditorDialog(
            viewModel = viewModel,
            packJson = wallpaperPackJson,
            onDismiss = { showPackEditor = false },
        )
    }

    if (showColumnsPicker) {
        AlertDialog(
            onDismissRequest = { showColumnsPicker = false },
            title = { Text(stringResource(R.string.settings_picker_grid_columns_title)) },
            text = {
                Column {
                    listOf(
                        1 to stringResource(R.string.settings_picker_grid_1),
                        2 to stringResource(R.string.settings_picker_grid_2),
                        3 to stringResource(R.string.settings_picker_grid_3),
                        4 to stringResource(R.string.settings_picker_grid_4),
                    ).forEach { (count, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = gridColumns == count,
                            onClick = {
                                viewModel.setGridColumns(count)
                                showColumnsPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColumnsPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showResPicker) {
        AlertDialog(
            onDismissRequest = { showResPicker = false },
            title = { Text(stringResource(R.string.settings_picker_resolution_title)) },
            text = {
                Column {
                    listOf(
                        "" to stringResource(R.string.settings_picker_resolution_any),
                        "1920x1080" to stringResource(R.string.settings_picker_resolution_fhd),
                        "2560x1440" to stringResource(R.string.settings_picker_resolution_qhd),
                        "3840x2160" to stringResource(R.string.settings_picker_resolution_4k),
                    ).forEach { (res, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = preferredRes == res,
                            onClick = {
                                viewModel.setPreferredRes(res)
                                showResPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showStylePicker) {
        val styleOptions = remember {
            listOf(
                "minimal",
                "amoled",
                "nature",
                "space",
                "anime",
                "abstract",
                "neon",
                "city",
                "gradient",
                "dark",
            )
        }
        var selectedStyles by remember(showStylePicker, userStyles) {
            mutableStateOf(
                userStyles.split(",")
                    .map { it.trim().lowercase(java.util.Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet(),
            )
        }
        AlertDialog(
            onDismissRequest = { showStylePicker = false },
            title = { Text(stringResource(R.string.settings_picker_styles_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_picker_styles_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        styleOptions.forEach { style ->
                            FilterChip(
                                selected = style in selectedStyles,
                                onClick = {
                                    selectedStyles = if (style in selectedStyles) {
                                        selectedStyles - style
                                    } else {
                                        selectedStyles + style
                                    }
                                },
                                label = { Text(stylePreferenceLabel(style)) },
                                leadingIcon = if (style in selectedStyles) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setUserStyles(selectedStyles.sorted().joinToString(","))
                        showStylePicker = false
                    },
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStylePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showClockModePicker) {
        AlertDialog(
            onDismissRequest = { showClockModePicker = false },
            title = { Text(stringResource(R.string.settings_wp_clock_overlay_mode_title)) },
            text = {
                Column {
                    listOf(
                        WallpaperClockOverlayMode.TIME to stringResource(R.string.settings_wp_clock_overlay_mode_time),
                        WallpaperClockOverlayMode.DATE to stringResource(R.string.settings_wp_clock_overlay_mode_date),
                        WallpaperClockOverlayMode.TIME_AND_DATE to stringResource(R.string.settings_wp_clock_overlay_mode_both),
                    ).forEach { (mode, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = wallpaperClockOverlayMode == mode,
                            onClick = {
                                viewModel.setWallpaperClockOverlayMode(mode.preferenceValue)
                                showClockModePicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClockModePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showClockPositionPicker) {
        AlertDialog(
            onDismissRequest = { showClockPositionPicker = false },
            title = { Text(stringResource(R.string.settings_wp_clock_overlay_position_title)) },
            text = {
                Column {
                    listOf(
                        WallpaperClockOverlayPosition.TOP_LEFT to stringResource(R.string.settings_wp_clock_overlay_position_top_left),
                        WallpaperClockOverlayPosition.TOP_RIGHT to stringResource(R.string.settings_wp_clock_overlay_position_top_right),
                        WallpaperClockOverlayPosition.BOTTOM_LEFT to stringResource(R.string.settings_wp_clock_overlay_position_bottom_left),
                        WallpaperClockOverlayPosition.BOTTOM_RIGHT to stringResource(R.string.settings_wp_clock_overlay_position_bottom_right),
                    ).forEach { (position, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = wallpaperClockOverlayPosition == position,
                            onClick = {
                                viewModel.setWallpaperClockOverlayPosition(position.preferenceValue)
                                showClockPositionPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClockPositionPicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
