package com.freevibe.ui.screens.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.icu.text.ListFormatter
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.IntentCompat
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.freevibe.R
import com.freevibe.service.SOUND_PROFILE_DEFAULT_END_HOUR
import com.freevibe.service.SOUND_PROFILE_DEFAULT_START_HOUR
import com.freevibe.service.SoundProfile
import com.freevibe.service.SoundShuffleTarget
import com.freevibe.service.changesASound
import com.freevibe.service.isAllDay
import com.freevibe.service.isOvernight
import com.freevibe.service.moving
import com.freevibe.service.overlappingSoundProfiles
import com.freevibe.service.parseProfiles
import com.freevibe.service.saving
import com.freevibe.service.serializeProfiles
import com.freevibe.service.soundFor
import com.freevibe.service.withEditableHours
import com.freevibe.service.withEndStepped
import com.freevibe.service.withSound
import com.freevibe.service.withStartStepped
import com.freevibe.ui.theme.AuraShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.UUID

private const val MAX_PROFILE_NAME_LENGTH = 40

/** The profile being edited survives rotation and a trip to the system sound picker. */
private val SoundProfileDraftSaver = Saver<SoundProfile?, String>(
    save = { draft -> draft?.let { serializeProfiles(listOf(it)) }.orEmpty() },
    restore = { saved -> parseProfiles(saved).firstOrNull() },
)

@get:StringRes
private val SoundShuffleTarget.soundProfileLabelRes: Int
    get() = when (this) {
        SoundShuffleTarget.RINGTONE -> R.string.sound_shuffle_target_ringtone
        SoundShuffleTarget.NOTIFICATION -> R.string.sound_shuffle_target_notification
        SoundShuffleTarget.ALARM -> R.string.sound_shuffle_target_alarm
    }

/**
 * Lists the time-of-day sound profiles and edits one at a time. A sound comes from the system's
 * own picker, so what gets stored is an address Android can use as a ringtone after a restart.
 */
@Composable
internal fun SoundProfilesDialog(
    profilesJson: String,
    profilesEnabled: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val profiles = remember(profilesJson) { parseProfiles(profilesJson) }
    var draft by rememberSaveable(stateSaver = SoundProfileDraftSaver) { mutableStateOf<SoundProfile?>(null) }
    var pickingTarget by rememberSaveable { mutableStateOf<String?>(null) }

    var permissionRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionRefresh += 1
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canWriteSettings = remember(permissionRefresh) { Settings.System.canWrite(context) }

    fun commit(updated: List<SoundProfile>) = onSave(serializeProfiles(updated))

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val target = SoundShuffleTarget.entries.firstOrNull { it.name == pickingTarget }
        pickingTarget = null
        val current = draft
        if (target == null || current == null || result.resultCode != Activity.RESULT_OK) {
            return@rememberLauncherForActivityResult
        }
        val picked = result.data?.let { data ->
            IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } ?: return@rememberLauncherForActivityResult
        draft = current.withSound(target, picked.toString())
    }

    fun pickSound(target: SoundShuffleTarget, existing: String) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, target.ringtoneManagerType)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
            .putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                resources.getString(
                    R.string.sound_profiles_picker_title,
                    resources.getString(target.soundProfileLabelRes),
                ),
            )
        if (existing.isNotBlank()) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
        }
        pickingTarget = target.name
        try {
            soundPicker.launch(intent)
        } catch (_: ActivityNotFoundException) {
            pickingTarget = null
            onFeedback(resources.getString(R.string.sound_profiles_picker_missing))
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp),
            shape = AuraShapes.Large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                val editing = draft
                val isNew = editing != null && profiles.none { it.id == editing.id }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (editing != null) {
                        IconButton(onClick = { draft = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    }
                    Text(
                        text = when {
                            editing == null -> stringResource(R.string.sound_profiles_dialog_title)
                            isNew -> stringResource(R.string.sound_profiles_new_title)
                            else -> editing.name
                        },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
                HorizontalDivider()
                if (!canWriteSettings) {
                    SoundProfilePermissionNotice(
                        onOpen = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    },
                                )
                            }.onFailure {
                                onFeedback(
                                    resources.getString(
                                        R.string.sound_shuffle_action_failed,
                                        it.message ?: resources.getString(R.string.common_retry_later),
                                    ),
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                }
                if (editing == null) {
                    SoundProfileList(
                        profiles = profiles,
                        profilesEnabled = profilesEnabled,
                        onAdd = {
                            draft = SoundProfile(
                                id = UUID.randomUUID().toString(),
                                name = resources.getString(R.string.sound_profiles_default_name, profiles.size + 1),
                                startHour = SOUND_PROFILE_DEFAULT_START_HOUR,
                                endHour = SOUND_PROFILE_DEFAULT_END_HOUR,
                            )
                        },
                        onOpen = { draft = it.withEditableHours() },
                        onEnabledChange = { profile, enabled ->
                            commit(profiles.saving(profile.copy(enabled = enabled)))
                        },
                        onMove = { profile, delta -> commit(profiles.moving(profile.id, delta)) },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    SoundProfileEditor(
                        draft = editing,
                        isNew = isNew,
                        overlapping = remember(profiles, editing) { overlappingSoundProfiles(profiles, editing) },
                        onChange = { draft = it },
                        onPickSound = { target -> pickSound(target, editing.soundFor(target)) },
                        onSave = {
                            commit(profiles.saving(editing.copy(name = editing.name.trim())))
                            draft = null
                            onFeedback(resources.getString(R.string.sound_profiles_saved))
                        },
                        onDelete = {
                            commit(profiles.filterNot { it.id == editing.id })
                            draft = null
                            onFeedback(resources.getString(R.string.sound_profiles_deleted))
                        },
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundProfilePermissionNotice(onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            stringResource(R.string.sound_shuffle_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = onOpen) { Text(stringResource(R.string.sound_shuffle_permission_open)) }
    }
}

@Composable
private fun SoundProfileList(
    profiles: List<SoundProfile>,
    profilesEnabled: Boolean,
    onAdd: () -> Unit,
    onOpen: (SoundProfile) -> Unit,
    onEnabledChange: (SoundProfile, Boolean) -> Unit,
    onMove: (SoundProfile, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (profiles.isEmpty()) {
            item { SoundProfileHint(stringResource(R.string.sound_profiles_empty)) }
        } else {
            if (!profilesEnabled) item { SoundProfileHint(stringResource(R.string.sound_profiles_turn_on_hint)) }
            if (profiles.size > 1) item { SoundProfileHint(stringResource(R.string.sound_profiles_order_hint)) }
        }
        // An import can carry two profiles with one id, so the position is part of the key.
        itemsIndexed(profiles, key = { index, profile -> "$index:${profile.id}" }) { index, profile ->
            SoundProfileRow(
                profile = profile,
                canMoveUp = index > 0,
                canMoveDown = index < profiles.lastIndex,
                onOpen = { onOpen(profile) },
                onEnabledChange = { onEnabledChange(profile, it) },
                onMove = { onMove(profile, it) },
            )
        }
        item {
            Button(onClick = onAdd, shape = AuraShapes.Medium) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.sound_profiles_add), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SoundProfileHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SoundProfileRow(
    profile: SoundProfile,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    val name = profile.name.ifBlank { stringResource(R.string.sound_profiles_new_title) }
    val switchLabel = stringResource(R.string.sound_profiles_row_enabled_label, name)
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = AuraShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(soundProfileWindowText(profile), style = MaterialTheme.typography.bodyMedium)
                Text(
                    soundProfileSoundsText(profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canMoveUp) {
                IconButton(onClick = { onMove(-1) }) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.reddit_presets_move_up),
                    )
                }
            }
            if (canMoveDown) {
                IconButton(onClick = { onMove(1) }) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.reddit_presets_move_down),
                    )
                }
            }
            Switch(
                checked = profile.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = switchLabel },
            )
        }
    }
}

@Composable
private fun SoundProfileEditor(
    draft: SoundProfile,
    isNew: Boolean,
    overlapping: List<SoundProfile>,
    onChange: (SoundProfile) -> Unit,
    onPickSound: (SoundShuffleTarget) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onChange(draft.copy(name = it.take(MAX_PROFILE_NAME_LENGTH))) },
            label = { Text(stringResource(R.string.reddit_preset_name_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SoundProfileHourStepper(
            label = stringResource(R.string.sound_profiles_starts),
            time = formatProfileHour(context, draft.startHour),
            earlierLabel = stringResource(R.string.sound_profiles_start_earlier),
            laterLabel = stringResource(R.string.sound_profiles_start_later),
            onEarlier = { onChange(draft.withStartStepped(-1)) },
            onLater = { onChange(draft.withStartStepped(1)) },
        )
        SoundProfileHourStepper(
            label = stringResource(R.string.sound_profiles_ends),
            time = formatProfileHour(context, draft.endHour),
            earlierLabel = stringResource(R.string.sound_profiles_end_earlier),
            laterLabel = stringResource(R.string.sound_profiles_end_later),
            onEarlier = { onChange(draft.withEndStepped(-1)) },
            onLater = { onChange(draft.withEndStepped(1)) },
        )
        SoundProfileHint(soundProfileWindowText(draft))
        SoundShuffleTarget.entries.forEach { target ->
            SoundProfileSoundRow(
                target = target,
                uri = draft.soundFor(target),
                onPick = { onPickSound(target) },
                onClear = { onChange(draft.withSound(target, "")) },
            )
        }
        if (!draft.changesASound) {
            SoundProfileHint(stringResource(R.string.sound_profiles_need_sound))
        }
        if (overlapping.isNotEmpty()) {
            SoundProfileHint(
                stringResource(R.string.sound_profiles_overlap, formatNameList(overlapping.map { it.name })),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isNew) {
                OutlinedButton(onClick = onDelete, shape = AuraShapes.Medium) {
                    Text(stringResource(R.string.common_delete))
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onSave,
                enabled = draft.changesASound && draft.name.isNotBlank(),
                shape = AuraShapes.Medium,
            ) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

@Composable
private fun SoundProfileHourStepper(
    label: String,
    time: String,
    earlierLabel: String,
    laterLabel: String,
    onEarlier: () -> Unit,
    onLater: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onEarlier) {
            Icon(Icons.Default.Remove, contentDescription = earlierLabel)
        }
        Text(
            time,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            // The hour changes under the buttons, so a screen reader is told the new one.
            modifier = Modifier
                .widthIn(min = 72.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        IconButton(onClick = onLater) {
            Icon(Icons.Default.Add, contentDescription = laterLabel)
        }
    }
}

@Composable
private fun SoundProfileSoundRow(
    target: SoundShuffleTarget,
    uri: String,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val label = stringResource(target.soundProfileLabelRes)
    val pickLabel = stringResource(R.string.sound_profiles_picker_title, label)
    // Blank while the name is being read, null when Android can't name the sound any more.
    val title by produceState<String?>(initialValue = "", uri) {
        value = if (uri.isBlank()) {
            ""
        } else {
            withContext(Dispatchers.IO) {
                runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = when {
                    uri.isBlank() -> stringResource(R.string.sound_profiles_sound_unchanged)
                    else -> title ?: stringResource(R.string.sound_profiles_sound_missing)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onPick,
            modifier = Modifier.semantics { contentDescription = pickLabel },
        ) {
            Text(stringResource(R.string.sound_profiles_choose))
        }
        if (uri.isNotBlank()) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.sound_profiles_clear_sound, label),
                )
            }
        }
    }
}

@Composable
private fun soundProfileWindowText(profile: SoundProfile): String {
    val context = LocalContext.current
    val start = formatProfileHour(context, profile.startHour)
    val end = formatProfileHour(context, profile.endHour)
    return when {
        profile.isAllDay -> stringResource(R.string.sound_profiles_window_all_day)
        profile.isOvernight -> stringResource(R.string.sound_profiles_window_overnight, start, end)
        else -> stringResource(R.string.sound_profiles_window, start, end)
    }
}

/** Names the sounds the profile changes, or says none is picked. */
@Composable
private fun soundProfileSoundsText(profile: SoundProfile): String {
    val labels = SoundShuffleTarget.entries
        .filter { profile.soundFor(it).isNotBlank() }
        .map { stringResource(it.soundProfileLabelRes) }
    return if (labels.isEmpty()) stringResource(R.string.sound_profiles_row_no_sound) else formatNameList(labels)
}

/** Joins names the way the person's language does ("A, B and C"), with no English glue in code. */
@Composable
private fun formatNameList(names: List<String>): String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    return ListFormatter.getInstance(locale).format(names)
}

/** The hour in the phone's own 12 or 24 hour style. 24 is midnight at the end of the day. */
private fun formatProfileHour(context: Context, hour: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, Math.floorMod(hour, 24))
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
    }
    return android.text.format.DateFormat.getTimeFormat(context).format(calendar.time)
}
