package com.freevibe.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.local.SCHEDULER_DAY_NIGHT_MODE_CLOCK
import com.freevibe.data.local.SCHEDULER_DAY_NIGHT_MODE_SINGLE
import com.freevibe.data.local.SCHEDULER_DAY_NIGHT_MODE_SYSTEM_THEME
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER

@Composable
internal fun SchedulerSettingsSection(
    context: Context,
    viewModel: SettingsViewModel,
    schedulerEnabled: Boolean,
    schedulerInterval: Long,
    schedulerSource: String,
    schedulerDaySource: String,
    schedulerNightSource: String,
    schedulerDayNightMode: String,
    schedulerDayStartHour: Int,
    schedulerNightStartHour: Int,
    schedulerHome: Boolean,
    schedulerLock: Boolean,
    schedulerShuffle: Boolean,
    localWallpaperFolderUri: String,
    localFolderPermissionActive: Boolean,
    localCatalogReady: Boolean,
    wallhavenProviderEnabled: Boolean,
    pixabayUsable: Boolean,
    bingProviderEnabled: Boolean,
    onChooseLocalWallpaperFolder: (String?) -> Unit,
) {
    var showSchedulerInterval by remember { mutableStateOf(false) }
    var schedulerSourceTarget by remember { mutableStateOf<SchedulerSourceTarget?>(null) }
    var collectionPickerTarget by remember { mutableStateOf<SchedulerSourceTarget?>(null) }
    var showDayNightMode by remember { mutableStateOf(false) }
    var startHourTarget by remember { mutableStateOf<SchedulerSourceTarget?>(null) }

    SettingsSection(
        sectionKey = SettingsSectionKeys.SCHEDULER,
        title = stringResource(R.string.settings_scheduler_section_title),
        description = stringResource(R.string.settings_scheduler_section_description),
    ) {
        SettingsToggle(
            icon = Icons.Default.Schedule,
            title = stringResource(R.string.settings_sched_auto_rotate_title),
            subtitle = if (schedulerEnabled) {
                stringResource(R.string.settings_sched_auto_rotate_on_subtitle, formatInterval(schedulerInterval))
            } else {
                stringResource(R.string.settings_sched_auto_rotate_off_subtitle)
            },
            checked = schedulerEnabled,
            onCheckedChange = viewModel::setSchedulerEnabled,
        )
        if (schedulerEnabled) {
            SettingsItem(
                icon = Icons.Default.Timer,
                title = stringResource(R.string.settings_sched_interval_title),
                subtitle = formatInterval(schedulerInterval),
                onClick = { showSchedulerInterval = true },
            )
            val collectionsList by viewModel.collections.collectAsStateWithLifecycle()
            val activeCollectionId by viewModel.schedulerCollectionId.collectAsStateWithLifecycle()
            val activeCollectionName = remember(collectionsList, activeCollectionId) {
                collectionsList.firstOrNull { it.collectionId == activeCollectionId }?.name
            }
            @Composable
            fun sourceSubtitle(source: String, fallbackToMain: Boolean = false): String {
                if (source.isBlank() && fallbackToMain) {
                    return context.getString(
                        R.string.settings_sched_source_same_as_main,
                        sourceSubtitle(schedulerSource),
                    )
                }
                return when {
                    source == "collection" && activeCollectionName != null ->
                        context.getString(R.string.settings_sched_collection_prefix, activeCollectionName)
                    source == "collection" -> context.getString(R.string.settings_sched_collection_none)
                    else -> wallpaperRotationSourceLabel(
                        source = source,
                        localFolderUri = localWallpaperFolderUri,
                        localFolderPermissionActive = localFolderPermissionActive,
                        localCatalogReady = localCatalogReady,
                    )
                }
            }
            SettingsItem(
                icon = Icons.Default.Source,
                title = stringResource(R.string.settings_sched_source_title),
                subtitle = sourceSubtitle(schedulerSource),
                onClick = { schedulerSourceTarget = SchedulerSourceTarget.DEFAULT },
            )
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.settings_sched_day_night_mode_title),
                subtitle = when (schedulerDayNightMode) {
                    SCHEDULER_DAY_NIGHT_MODE_CLOCK -> stringResource(
                        R.string.settings_sched_day_night_clock_summary,
                        formatSchedulerHour(context, schedulerDayStartHour),
                        formatSchedulerHour(context, schedulerNightStartHour),
                    )
                    SCHEDULER_DAY_NIGHT_MODE_SYSTEM_THEME ->
                        stringResource(R.string.settings_sched_day_night_system_theme_summary)
                    else -> stringResource(R.string.settings_sched_day_night_single_summary)
                },
                onClick = { showDayNightMode = true },
            )
            if (schedulerDayNightMode != SCHEDULER_DAY_NIGHT_MODE_SINGLE) {
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = stringResource(R.string.settings_sched_day_source_title),
                    subtitle = sourceSubtitle(schedulerDaySource, fallbackToMain = true),
                    onClick = { schedulerSourceTarget = SchedulerSourceTarget.DAY },
                )
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = stringResource(R.string.settings_sched_night_source_title),
                    subtitle = sourceSubtitle(schedulerNightSource, fallbackToMain = true),
                    onClick = { schedulerSourceTarget = SchedulerSourceTarget.NIGHT },
                )
                if (schedulerDayNightMode == SCHEDULER_DAY_NIGHT_MODE_CLOCK) {
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = stringResource(R.string.settings_sched_day_starts_title),
                        subtitle = formatSchedulerHour(context, schedulerDayStartHour),
                        onClick = { startHourTarget = SchedulerSourceTarget.DAY },
                    )
                    SettingsItem(
                        icon = Icons.Default.Timer,
                        title = stringResource(R.string.settings_sched_night_starts_title),
                        subtitle = formatSchedulerHour(context, schedulerNightStartHour),
                        onClick = { startHourTarget = SchedulerSourceTarget.NIGHT },
                    )
                }
            }
            SettingsToggle(
                icon = Icons.Default.Home,
                title = stringResource(R.string.settings_sched_home_title),
                subtitle = stringResource(R.string.settings_sched_home_subtitle),
                checked = schedulerHome,
                onCheckedChange = viewModel::setSchedulerHome,
            )
            SettingsToggle(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_sched_lock_title),
                subtitle = stringResource(R.string.settings_sched_lock_subtitle),
                checked = schedulerLock,
                onCheckedChange = viewModel::setSchedulerLock,
            )
            SettingsToggle(
                icon = Icons.Default.Shuffle,
                title = stringResource(R.string.settings_sched_shuffle_title),
                subtitle = if (schedulerShuffle) {
                    stringResource(R.string.settings_sched_shuffle_on_subtitle)
                } else {
                    stringResource(R.string.settings_sched_shuffle_off_subtitle)
                },
                checked = schedulerShuffle,
                onCheckedChange = viewModel::setSchedulerShuffle,
            )
        }
    }

    if (showSchedulerInterval) {
        val intervals = listOf(
            15L to stringResource(R.string.settings_sched_interval_15m),
            30L to stringResource(R.string.settings_sched_interval_30m),
            60L to stringResource(R.string.settings_sched_interval_1h),
            120L to stringResource(R.string.settings_sched_interval_2h),
            360L to stringResource(R.string.settings_sched_interval_6h),
            720L to stringResource(R.string.settings_sched_interval_12h),
            1440L to stringResource(R.string.settings_sched_interval_24h),
            2880L to stringResource(R.string.settings_sched_interval_2d),
        )
        AlertDialog(
            onDismissRequest = { showSchedulerInterval = false },
            title = { Text(stringResource(R.string.settings_sched_interval_title)) },
            text = {
                Column {
                    intervals.forEach { (min, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = schedulerInterval == min,
                            onClick = {
                                viewModel.setSchedulerInterval(min)
                                showSchedulerInterval = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSchedulerInterval = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    schedulerSourceTarget?.let { sourceTarget ->
        val selectedSource = when (sourceTarget) {
            SchedulerSourceTarget.DEFAULT -> schedulerSource
            SchedulerSourceTarget.DAY -> schedulerDaySource
            SchedulerSourceTarget.NIGHT -> schedulerNightSource
        }
        val sources = buildList {
            if (sourceTarget != SchedulerSourceTarget.DEFAULT) {
                add("" to context.getString(R.string.settings_sched_source_use_main))
            }
            add("discover" to context.getString(R.string.settings_sched_source_discover))
            add("favorites" to context.getString(R.string.settings_sched_source_favorites))
            add(WALLPAPER_SOURCE_LOCAL_FOLDER to context.getString(R.string.settings_sched_source_local))
            add("wallhaven" to context.getString(R.string.settings_sched_source_wallhaven))
            add("pixabay" to context.getString(R.string.settings_sched_source_pixabay))
            add("bing" to context.getString(R.string.settings_sched_source_bing))
            add("collection" to context.getString(R.string.settings_sched_source_collection))
        }.filter { (key, _) ->
            when (key) {
                "wallhaven" -> wallhavenProviderEnabled || selectedSource == "wallhaven"
                "pixabay" -> pixabayUsable || selectedSource == "pixabay"
                "bing" -> bingProviderEnabled || selectedSource == "bing"
                else -> true
            }
        }
        AlertDialog(
            onDismissRequest = { schedulerSourceTarget = null },
            title = { Text(stringResource(R.string.settings_sched_wp_source_title)) },
            text = {
                Column {
                    sources.forEach { (key, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = selectedSource == key,
                            onClick = {
                                if (key == "collection") {
                                    schedulerSourceTarget = null
                                    collectionPickerTarget = sourceTarget
                                } else if (
                                    key == WALLPAPER_SOURCE_LOCAL_FOLDER &&
                                    !isLocalWallpaperFolderReady(
                                        localWallpaperFolderUri,
                                        localFolderPermissionActive,
                                    ) && !localCatalogReady
                                ) {
                                    schedulerSourceTarget = null
                                    onChooseLocalWallpaperFolder(
                                        when (sourceTarget) {
                                            SchedulerSourceTarget.DEFAULT -> "scheduler"
                                            SchedulerSourceTarget.DAY -> "scheduler_day"
                                            SchedulerSourceTarget.NIGHT -> "scheduler_night"
                                        },
                                    )
                                } else {
                                    viewModel.setSchedulerSource(sourceTarget, key)
                                    schedulerSourceTarget = null
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { schedulerSourceTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    collectionPickerTarget?.let { sourceTarget ->
        val collections by viewModel.collections.collectAsStateWithLifecycle()
        val activeId by viewModel.schedulerCollectionId.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { collectionPickerTarget = null },
            title = { Text(stringResource(R.string.settings_sched_collection_picker_title)) },
            text = {
                if (collections.isEmpty()) {
                    Column {
                        Text(
                            stringResource(R.string.settings_sched_collection_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            stringResource(R.string.settings_sched_collection_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        collections.forEach { collection ->
                            SettingsRadioOptionRow(
                                label = collection.name,
                                selected = activeId == collection.collectionId,
                                onClick = {
                                    viewModel.setSchedulerCollection(collection.collectionId, sourceTarget)
                                    collectionPickerTarget = null
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { collectionPickerTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showDayNightMode) {
        val modes = listOf(
            SCHEDULER_DAY_NIGHT_MODE_SINGLE to stringResource(R.string.settings_sched_day_night_single),
            SCHEDULER_DAY_NIGHT_MODE_CLOCK to stringResource(R.string.settings_sched_day_night_clock),
            SCHEDULER_DAY_NIGHT_MODE_SYSTEM_THEME to stringResource(R.string.settings_sched_day_night_system_theme),
        )
        AlertDialog(
            onDismissRequest = { showDayNightMode = false },
            title = { Text(stringResource(R.string.settings_sched_day_night_mode_title)) },
            text = {
                Column {
                    modes.forEach { (mode, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = schedulerDayNightMode == mode,
                            onClick = {
                                viewModel.setSchedulerDayNightMode(mode)
                                showDayNightMode = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDayNightMode = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    startHourTarget?.let { target ->
        val selectedHour = if (target == SchedulerSourceTarget.DAY) schedulerDayStartHour else schedulerNightStartHour
        AlertDialog(
            onDismissRequest = { startHourTarget = null },
            title = {
                Text(
                    stringResource(
                        if (target == SchedulerSourceTarget.DAY) {
                            R.string.settings_sched_day_starts_title
                        } else {
                            R.string.settings_sched_night_starts_title
                        },
                    ),
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    (0..23).forEach { hour ->
                        SettingsRadioOptionRow(
                            label = formatSchedulerHour(context, hour),
                            selected = selectedHour == hour,
                            onClick = {
                                if (target == SchedulerSourceTarget.DAY) {
                                    viewModel.setSchedulerDayStartHour(hour)
                                } else {
                                    viewModel.setSchedulerNightStartHour(hour)
                                }
                                startHourTarget = null
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { startHourTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

internal fun formatSchedulerHour(context: Context, hour: Int): String {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
        set(java.util.Calendar.MINUTE, 0)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}
