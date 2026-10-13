package com.freevibe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.service.Daypart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun daypartLabelRes(daypart: Daypart): Int = when (daypart) {
    Daypart.MORNING -> R.string.settings_wp_pack_morning
    Daypart.DAY -> R.string.settings_wp_pack_day
    Daypart.EVENING -> R.string.settings_wp_pack_evening
    Daypart.NIGHT -> R.string.settings_wp_pack_night
}

private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * Edits the single 24H pack: one wallpaper per time of day, each with its own target.
 * Wallpapers come from the document picker with a persisted read grant, so the worker can
 * still open them after a reboot.
 */
@Composable
internal fun WallpaperPackEditorDialog(
    viewModel: SettingsViewModel,
    packJson: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pack = remember(packJson) { editablePack(packJson) }
    // Saved across rotation and the trip to the picker, which can outlive this screen.
    var name by rememberSaveable { mutableStateOf(pack.name) }
    var status by remember { mutableStateOf("") }
    var pendingDaypart by rememberSaveable { mutableStateOf<Daypart?>(null) }
    val scope = rememberCoroutineScope()
    val unnamedWallpaper = stringResource(R.string.settings_wp_pack_unnamed_wallpaper)
    val accessFailed = stringResource(R.string.settings_wp_pack_access_failed)
    val savedMessage = stringResource(R.string.settings_wp_pack_saved)
    val removedMessage = stringResource(R.string.settings_wp_pack_slot_removed)
    val deletedMessage = stringResource(R.string.settings_wp_pack_deleted)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val daypart = pendingDaypart
        pendingDaypart = null
        if (uri == null || daypart == null) return@rememberLauncherForActivityResult
        val kept = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!kept) {
            status = accessFailed
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val label = withContext(Dispatchers.IO) { displayNameOf(context, uri) } ?: unnamedWallpaper
            viewModel.setWallpaperPackJson(pack.withSlot(daypart, uri.toString(), label).toEditorJson())
            status = savedMessage
        }
    }

    fun dismiss() {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && trimmed != pack.name) {
            viewModel.setWallpaperPackJson(pack.copy(name = trimmed).toEditorJson())
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::dismiss,
        title = { Text(stringResource(R.string.settings_wp_pack_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_wp_pack_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val activeLabel = stringResource(R.string.settings_wp_pack_active_label)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(activeLabel, style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = pack.enabled,
                        onCheckedChange = { viewModel.setWallpaperPackJson(pack.copy(enabled = it).toEditorJson()) },
                        modifier = Modifier.semantics { contentDescription = activeLabel },
                    )
                }
                Daypart.entries.forEach { daypart ->
                    val slot = pack.slotFor(daypart)
                    val dayLabel = stringResource(daypartLabelRes(daypart))
                    val chooseDescription = stringResource(R.string.settings_wp_pack_choose_for, dayLabel)
                    Column(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dayLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = slot?.label?.ifBlank { unnamedWallpaper }
                                        ?: stringResource(R.string.settings_wp_pack_not_set),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(
                                onClick = {
                                    pendingDaypart = daypart
                                    picker.launch(arrayOf("image/*"))
                                },
                                modifier = Modifier.semantics { contentDescription = chooseDescription },
                            ) {
                                Text(
                                    stringResource(
                                        if (slot == null) R.string.settings_wp_pack_choose else R.string.settings_wp_pack_change,
                                    ),
                                )
                            }
                            if (slot != null) {
                                IconButton(onClick = {
                                    viewModel.setWallpaperPackJson(pack.withoutSlot(daypart).toEditorJson())
                                    status = removedMessage
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.settings_wp_pack_remove_slot, dayLabel),
                                    )
                                }
                            }
                        }
                        if (slot != null) {
                            val current = pack.effectiveTarget(slot)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "HOME" to R.string.common_home,
                                    "LOCK" to R.string.common_lock,
                                    "BOTH" to R.string.common_both,
                                ).forEach { (value, labelRes) ->
                                    FilterChip(
                                        selected = current == value,
                                        onClick = {
                                            viewModel.setWallpaperPackJson(pack.withSlotTarget(daypart, value).toEditorJson())
                                        },
                                        label = { Text(stringResource(labelRes)) },
                                    )
                                }
                            }
                        }
                    }
                }
                if (status.isNotBlank()) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                viewModel.setWallpaperPackJson("")
                name = ""
                status = deletedMessage
            }) { Text(stringResource(R.string.settings_wp_pack_delete)) }
        },
        confirmButton = {
            TextButton(onClick = ::dismiss) { Text(stringResource(R.string.common_done)) }
        },
    )
}
