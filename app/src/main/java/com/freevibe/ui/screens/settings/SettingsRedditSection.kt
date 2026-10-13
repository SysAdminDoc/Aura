package com.freevibe.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.local.validateRedditSubredditList
import com.freevibe.data.model.MAX_REDDIT_FEED_MIN_DIMENSION
import com.freevibe.data.model.MAX_REDDIT_FEED_MIN_DURATION_SECONDS
import com.freevibe.data.model.MAX_REDDIT_FEED_PRESETS_PER_TYPE
import com.freevibe.data.model.MAX_REDDIT_FEED_PRESET_NAME
import com.freevibe.data.model.REDDIT_FEED_SORTS
import com.freevibe.data.model.REDDIT_FEED_TIME_WINDOWS
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.activePreset
import com.freevibe.data.model.presetsOf
import com.freevibe.ui.screens.reddit.RedditFeedPresetsViewModel
import com.freevibe.ui.screens.reddit.newRedditFeedPreset

/** The named Reddit feeds for one media type: pick the one in use, and create, edit, reorder or delete them. */
@Composable
internal fun RedditFeedPresetManager(
    mediaType: RedditFeedMediaType,
    viewModel: RedditFeedPresetsViewModel = hiltViewModel(),
) {
    val store by viewModel.store.collectAsStateWithLifecycle()
    val showAdultContent by viewModel.showAdultContent.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    var showList by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RedditFeedPreset?>(null) }
    val presets = store?.presetsOf(mediaType).orEmpty()
    val active = store?.activePreset(mediaType)
    val title = stringResource(
        if (mediaType == RedditFeedMediaType.WALLPAPER) R.string.reddit_presets_wallpaper_title else R.string.reddit_presets_video_title,
    )

    SettingsItem(
        icon = Icons.Default.Forum,
        title = title,
        subtitle = active?.let { pluralStringResource(R.plurals.reddit_presets_summary, presets.size, it.name, presets.size) }.orEmpty(),
        onClick = { showList = true },
        enabled = active != null,
        searchAliases = setOf("reddit", "subreddit", "feed"),
    )

    if (showList && active != null) {
        AlertDialog(
            onDismissRequest = { showList = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    presets.forEachIndexed { index, preset ->
                        RedditFeedPresetRow(
                            preset = preset,
                            isActive = preset.id == active.id,
                            canMoveUp = index > 0,
                            canMoveDown = index < presets.lastIndex,
                            canDuplicate = presets.size < MAX_REDDIT_FEED_PRESETS_PER_TYPE,
                            canDelete = presets.size > 1,
                            onUse = { viewModel.activate(preset.id) },
                            onEdit = { editing = preset },
                            onDuplicate = {
                                viewModel.duplicate(preset.id) { name -> resources.getString(R.string.reddit_preset_copy_name, name) }
                            },
                            onMove = { offset -> viewModel.move(preset.id, offset) },
                            onDelete = { viewModel.delete(preset.id) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showList = false }) { Text(stringResource(R.string.common_done)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { editing = newRedditFeedPreset(mediaType) },
                    enabled = presets.size < MAX_REDDIT_FEED_PRESETS_PER_TYPE,
                ) {
                    Text(stringResource(R.string.reddit_presets_new))
                }
            },
        )
    }

    editing?.let { preset ->
        RedditFeedPresetEditorDialog(
            preset = preset,
            isNew = presets.none { it.id == preset.id },
            showAdultContent = showAdultContent,
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RedditFeedPresetRow(
    preset: RedditFeedPreset,
    isActive: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDuplicate: Boolean,
    canDelete: Boolean,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(preset.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                redditFeedPresetSummary(preset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isActive) {
            Text(
                stringResource(R.string.reddit_presets_in_use),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        } else {
            TextButton(onClick = onUse) { Text(stringResource(R.string.reddit_presets_use)) }
        }
        Box {
            IconButton(onClick = { showActions = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.reddit_presets_actions, preset.name))
            }
            DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                fun close(action: () -> Unit): () -> Unit = {
                    showActions = false
                    action()
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.reddit_presets_edit)) }, onClick = close(onEdit))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reddit_presets_duplicate)) },
                    onClick = close(onDuplicate),
                    enabled = canDuplicate,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reddit_presets_move_up)) },
                    onClick = close { onMove(-1) },
                    enabled = canMoveUp,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reddit_presets_move_down)) },
                    onClick = close { onMove(1) },
                    enabled = canMoveDown,
                )
                // The last feed of a type stays, so a tab always has something to load.
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.common_delete)) },
                    onClick = close(onDelete),
                    enabled = canDelete,
                )
            }
        }
    }
}

@Composable
private fun redditFeedPresetSummary(preset: RedditFeedPreset): String {
    val sort = stringResource(redditSortLabelRes(preset.sort))
    val order = if (preset.sort == "top") {
        stringResource(R.string.reddit_preset_sort_with_window, sort, stringResource(redditTimeWindowLabelRes(preset.timeWindow)))
    } else {
        sort
    }
    val count = pluralStringResource(R.plurals.reddit_preset_subreddit_count, preset.subreddits.size, preset.subreddits.size)
    return stringResource(R.string.reddit_preset_row_summary, order, count)
}

@StringRes
private fun redditSortLabelRes(sort: String): Int = when (sort) {
    "hot" -> R.string.reddit_sort_hot
    "top" -> R.string.reddit_sort_top
    "rising" -> R.string.reddit_sort_rising
    else -> R.string.reddit_sort_new
}

@StringRes
private fun redditTimeWindowLabelRes(window: String): Int = when (window) {
    "day" -> R.string.reddit_time_day
    "month" -> R.string.reddit_time_month
    "year" -> R.string.reddit_time_year
    "all" -> R.string.reddit_time_all
    else -> R.string.reddit_time_week
}

/** A blank field means no minimum. Anything past [max] is refused rather than clamped. */
internal fun parseRedditFeedMinimum(text: String, max: Int): Int? =
    if (text.isBlank()) 0 else text.trim().toIntOrNull()?.takeIf { it in 0..max }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RedditFeedPresetEditorDialog(
    preset: RedditFeedPreset,
    isNew: Boolean,
    showAdultContent: Boolean,
    onSave: (RedditFeedPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(preset.id) { mutableStateOf(preset.name) }
    var subreddits by remember(preset.id) { mutableStateOf(preset.subreddits.joinToString(", ")) }
    var sort by remember(preset.id) { mutableStateOf(preset.sort) }
    var timeWindow by remember(preset.id) { mutableStateOf(preset.timeWindow) }
    var includeAdult by remember(preset.id) { mutableStateOf(preset.includeAdult) }
    var minWidth by remember(preset.id) { mutableStateOf(preset.minWidth.takeIf { it > 0 }?.toString().orEmpty()) }
    var minHeight by remember(preset.id) { mutableStateOf(preset.minHeight.takeIf { it > 0 }?.toString().orEmpty()) }
    var minDuration by remember(preset.id) {
        mutableStateOf(preset.minDurationSeconds.takeIf { it > 0 }?.toString().orEmpty())
    }
    val validation = validateRedditSubredditList(subreddits)
    val nameValid = name.isNotBlank()
    val subredditsValid = validation.isValid && validation.subreddits.isNotEmpty()
    val width = parseRedditFeedMinimum(minWidth, MAX_REDDIT_FEED_MIN_DIMENSION)
    val height = parseRedditFeedMinimum(minHeight, MAX_REDDIT_FEED_MIN_DIMENSION)
    val duration = parseRedditFeedMinimum(minDuration, MAX_REDDIT_FEED_MIN_DURATION_SECONDS)
    val canSave = nameValid && subredditsValid && width != null && height != null && duration != null
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (isNew) R.string.reddit_preset_editor_new_title else R.string.reddit_preset_editor_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(MAX_REDDIT_FEED_PRESET_NAME) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.reddit_preset_name_label)) },
                    singleLine = true,
                    isError = !nameValid,
                    supportingText = if (nameValid) null else {
                        { Text(stringResource(R.string.reddit_preset_name_error)) }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = subreddits,
                    onValueChange = { subreddits = it.take(1024) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_reddit_subreddits_input_label)) },
                    isError = !subredditsValid,
                    supportingText = {
                        Text(
                            stringResource(
                                when {
                                    !validation.isValid -> R.string.settings_reddit_subreddits_error
                                    validation.subreddits.isEmpty() -> R.string.reddit_preset_subreddits_empty
                                    else -> R.string.reddit_preset_subreddits_hint
                                },
                            ),
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
                )
                Text(stringResource(R.string.reddit_preset_sort_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    REDDIT_FEED_SORTS.forEach { option ->
                        FilterChip(
                            selected = sort == option,
                            onClick = { sort = option },
                            label = { Text(stringResource(redditSortLabelRes(option))) },
                        )
                    }
                }
                if (sort == "top") {
                    Text(stringResource(R.string.reddit_preset_time_label), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        REDDIT_FEED_TIME_WINDOWS.forEach { option ->
                            FilterChip(
                                selected = timeWindow == option,
                                onClick = { timeWindow = option },
                                label = { Text(stringResource(redditTimeWindowLabelRes(option))) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = includeAdult,
                            // Off can always be chosen. On waits for the app-wide NSFW setting.
                            enabled = showAdultContent || includeAdult,
                            role = Role.Switch,
                            onValueChange = { includeAdult = it },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.reddit_preset_adult_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                when {
                                    !showAdultContent -> R.string.reddit_preset_adult_needs_nsfw
                                    includeAdult -> R.string.reddit_preset_adult_on
                                    else -> R.string.reddit_preset_adult_off
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = includeAdult, onCheckedChange = null, enabled = showAdultContent || includeAdult)
                }
                if (preset.mediaType == RedditFeedMediaType.WALLPAPER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minWidth,
                            onValueChange = { minWidth = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.reddit_preset_min_width_label)) },
                            singleLine = true,
                            isError = width == null,
                            keyboardOptions = numberKeyboard,
                        )
                        OutlinedTextField(
                            value = minHeight,
                            onValueChange = { minHeight = it.filter(Char::isDigit).take(5) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.reddit_preset_min_height_label)) },
                            singleLine = true,
                            isError = height == null,
                            keyboardOptions = numberKeyboard,
                        )
                    }
                    Text(
                        if (width == null || height == null) {
                            stringResource(R.string.reddit_preset_number_error, MAX_REDDIT_FEED_MIN_DIMENSION)
                        } else {
                            stringResource(R.string.reddit_preset_min_size_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (width == null || height == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedTextField(
                        value = minDuration,
                        onValueChange = { minDuration = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.reddit_preset_min_duration_label)) },
                        singleLine = true,
                        isError = duration == null,
                        supportingText = {
                            Text(
                                if (duration == null) {
                                    stringResource(R.string.reddit_preset_number_error, MAX_REDDIT_FEED_MIN_DURATION_SECONDS)
                                } else {
                                    stringResource(R.string.reddit_preset_min_duration_hint)
                                },
                            )
                        },
                        keyboardOptions = numberKeyboard,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        preset.copy(
                            name = name.trim(),
                            subreddits = validation.subreddits,
                            sort = sort,
                            timeWindow = timeWindow,
                            includeAdult = includeAdult,
                            minWidth = width ?: 0,
                            minHeight = height ?: 0,
                            minDurationSeconds = duration ?: 0,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Which wallpaper feed rotation draws from when its source is Reddit. */
@Composable
internal fun RedditRotationPresetPicker(viewModel: RedditFeedPresetsViewModel = hiltViewModel()) {
    val store by viewModel.store.collectAsStateWithLifecycle()
    val current = store ?: return
    var showPicker by remember { mutableStateOf(false) }
    val presets = current.presetsOf(RedditFeedMediaType.WALLPAPER)
    val bound = presets.firstOrNull { it.id == current.rotationPresetId }
    val title = stringResource(R.string.reddit_rotation_preset_title)

    SettingsItem(
        icon = Icons.Default.Forum,
        title = title,
        subtitle = bound?.name
            ?: stringResource(R.string.reddit_rotation_preset_follow_summary, current.activePreset(RedditFeedMediaType.WALLPAPER).name),
        onClick = { showPicker = true },
    )

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    SettingsRadioOptionRow(
                        label = stringResource(R.string.reddit_rotation_preset_follow),
                        selected = bound == null,
                        onClick = {
                            viewModel.bindRotation("")
                            showPicker = false
                        },
                    )
                    presets.forEach { preset ->
                        SettingsRadioOptionRow(
                            label = preset.name,
                            selected = bound?.id == preset.id,
                            onClick = {
                                viewModel.bindRotation(preset.id)
                                showPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_close)) }
            },
        )
    }
}
