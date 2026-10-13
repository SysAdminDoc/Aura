package com.freevibe.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.freevibe.R
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.Sound
import com.freevibe.data.model.normalizeLocalMediaStatus
import com.freevibe.service.SOUND_SHUFFLE_INTERVAL_HOURS
import com.freevibe.service.SoundShuffleHistoryEntry
import com.freevibe.service.SoundShuffleHistoryStatus
import com.freevibe.service.SoundShufflePool
import com.freevibe.service.SoundShufflePoolItem
import com.freevibe.service.SoundShuffleReason
import com.freevibe.service.SoundShuffleSourceKind
import com.freevibe.service.SoundShuffleStore
import com.freevibe.service.SoundShuffleTarget

@Composable
internal fun SoundShufflePoolsDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    // Null until the first read, so a restored selection isn't dropped against the empty placeholder.
    val loadedStore by viewModel.soundShuffleStore.collectAsStateWithLifecycle<SoundShuffleStore?>(initialValue = null)
    val store = loadedStore ?: SoundShuffleStore()
    val downloads by viewModel.soundShuffleDownloads.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedPoolId by rememberSaveable { mutableStateOf<String?>(null) }
    var relinkItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPool = store.pools.firstOrNull { it.poolId == selectedPoolId }
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

    // Keyed on the pools only: a pool that was just created is selected before the store emits it.
    LaunchedEffect(store.pools) {
        val pools = loadedStore?.pools ?: return@LaunchedEffect
        if (selectedPoolId != null && pools.none { it.poolId == selectedPoolId }) selectedPoolId = null
    }

    fun report(result: Result<Unit>, successMessage: Int) {
        val message = result.fold(
            onSuccess = { resources.getString(successMessage) },
            onFailure = {
                resources.getString(
                    R.string.sound_shuffle_action_failed,
                    it.message ?: resources.getString(R.string.common_retry_later),
                )
            },
        )
        onFeedback(message)
    }

    val localSoundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val poolId = selectedPoolId ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        val pendingItem = relinkItemId
        relinkItemId = null
        if (pendingItem == null) {
            viewModel.addSoundShuffleLocal(poolId, uri) {
                report(it, R.string.sound_shuffle_sound_added)
            }
        } else {
            viewModel.relinkSoundShuffleItem(poolId, pendingItem, uri) {
                report(it, R.string.sound_shuffle_pool_saved)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedPool != null) {
                        IconButton(onClick = { selectedPoolId = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                            )
                        }
                    }
                    Text(
                        text = selectedPool?.name ?: stringResource(R.string.sound_shuffle_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
                HorizontalDivider()
                if (selectedPool == null) {
                    SoundShufflePoolList(
                        pools = store.pools,
                        onCreate = { name ->
                            viewModel.createSoundShufflePool(name) { result ->
                                result.onSuccess { selectedPoolId = it }
                                onFeedback(
                                    result.fold(
                                        onSuccess = { resources.getString(R.string.sound_shuffle_pool_created) },
                                        onFailure = {
                                            resources.getString(
                                                R.string.sound_shuffle_action_failed,
                                                it.message ?: resources.getString(R.string.common_retry_later),
                                            )
                                        },
                                    ),
                                )
                            }
                        },
                        onOpen = { selectedPoolId = it },
                        onEnabledChange = { pool, enabled ->
                            viewModel.setSoundShufflePoolEnabled(pool.poolId, enabled) {
                                report(it, R.string.sound_shuffle_pool_saved)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    SoundShufflePoolEditorPanel(
                        pool = selectedPool,
                        downloads = downloads,
                        originals = viewModel.soundShuffleOriginals,
                        history = store.history.filter { it.poolId == selectedPool.poolId },
                        canWriteSettings = canWriteSettings,
                        onOpenWriteSettings = {
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
                        onRename = { name ->
                            viewModel.renameSoundShufflePool(selectedPool.poolId, name) {
                                report(it, R.string.sound_shuffle_pool_saved)
                            }
                        },
                        onDelete = {
                            viewModel.deleteSoundShufflePool(selectedPool.poolId) {
                                report(it, R.string.sound_shuffle_pool_deleted)
                            }
                            selectedPoolId = null
                        },
                        onEnabledChange = { enabled ->
                            viewModel.setSoundShufflePoolEnabled(selectedPool.poolId, enabled) {
                                report(it, R.string.sound_shuffle_pool_saved)
                            }
                        },
                        onTargetsChange = { targets ->
                            viewModel.setSoundShufflePoolTargets(selectedPool.poolId, targets) {
                                report(it, R.string.sound_shuffle_pool_saved)
                            }
                        },
                        onIntervalChange = { hours ->
                            viewModel.setSoundShufflePoolInterval(selectedPool.poolId, hours) {
                                report(it, R.string.sound_shuffle_pool_saved)
                            }
                        },
                        onRunNow = {
                            viewModel.runSoundShufflePoolNow(selectedPool.poolId)
                            onFeedback(resources.getString(R.string.sound_shuffle_queued))
                        },
                        onAddLocal = {
                            relinkItemId = null
                            localSoundPicker.launch(arrayOf("audio/*"))
                        },
                        onRelink = { itemId ->
                            relinkItemId = itemId
                            localSoundPicker.launch(arrayOf("audio/*"))
                        },
                        onRemove = { itemId ->
                            viewModel.removeSoundShuffleItem(selectedPool.poolId, itemId) {
                                report(it, R.string.sound_shuffle_sound_removed)
                            }
                        },
                        onToggleDownload = { download, added ->
                            if (added) {
                                viewModel.removeSoundShuffleItem(selectedPool.poolId, "download:${download.id}") {
                                    report(it, R.string.sound_shuffle_sound_removed)
                                }
                            } else {
                                viewModel.addSoundShuffleDownload(selectedPool.poolId, download.id) {
                                    report(it, R.string.sound_shuffle_sound_added)
                                }
                            }
                        },
                        onToggleOriginal = { original, added ->
                            if (added) {
                                viewModel.removeSoundShuffleItem(selectedPool.poolId, "bundled:${original.id}") {
                                    report(it, R.string.sound_shuffle_sound_removed)
                                }
                            } else {
                                viewModel.addSoundShuffleOriginal(selectedPool.poolId, original.id) {
                                    report(it, R.string.sound_shuffle_sound_added)
                                }
                            }
                        },
                        onUndo = { historyId ->
                            viewModel.undoSoundShuffle(historyId) {
                                report(it, R.string.sound_shuffle_undo_complete)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundShufflePoolList(
    pools: List<SoundShufflePool>,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onEnabledChange: (SoundShufflePool, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newPoolName by rememberSaveable { mutableStateOf("") }
    val defaultPoolName = stringResource(R.string.sound_shuffle_new_pool_default)
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = newPoolName,
                onValueChange = { newPoolName = it },
                label = { Text(stringResource(R.string.sound_shuffle_new_pool_label)) },
                placeholder = { Text(stringResource(R.string.sound_shuffle_new_pool_default)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Button(
                onClick = {
                    onCreate(newPoolName.ifBlank { defaultPoolName })
                    newPoolName = ""
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(
                    stringResource(R.string.sound_shuffle_create),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (pools.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.sound_shuffle_no_pools),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(pools, key = SoundShufflePool::poolId) { pool ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(pool.poolId) },
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pool.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            pluralStringResource(
                                R.plurals.sound_shuffle_pool_summary,
                                pool.items.size,
                                pool.items.size,
                                soundShuffleTargetSummary(pool.targets),
                                soundShuffleIntervalLabel(pool.intervalHours),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = pool.enabled,
                        onCheckedChange = { onEnabledChange(pool, it) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun SoundShufflePoolEditorPanel(
    pool: SoundShufflePool,
    downloads: List<DownloadEntity>,
    originals: List<Sound>,
    history: List<SoundShuffleHistoryEntry>,
    canWriteSettings: Boolean,
    onOpenWriteSettings: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onTargetsChange: (List<SoundShuffleTarget>) -> Unit,
    onIntervalChange: (Long) -> Unit,
    onRunNow: () -> Unit,
    onAddLocal: () -> Unit,
    onRelink: (String) -> Unit,
    onRemove: (String) -> Unit,
    onToggleDownload: (DownloadEntity, Boolean) -> Unit,
    onToggleOriginal: (Sound, Boolean) -> Unit,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editedName by rememberSaveable(pool.poolId) { mutableStateOf(pool.name) }
    var scheduleMenuOpen by remember { mutableStateOf(false) }
    val itemIds = remember(pool.items) { pool.items.mapTo(mutableSetOf(), SoundShufflePoolItem::itemId) }
    val downloadsById = remember(downloads) { downloads.associateBy(DownloadEntity::id) }
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!canWriteSettings) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            stringResource(R.string.sound_shuffle_permission_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            stringResource(R.string.sound_shuffle_permission_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = onOpenWriteSettings) {
                            Text(stringResource(R.string.sound_shuffle_permission_open))
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text(stringResource(R.string.sound_shuffle_pool_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRename(editedName) }) {
                    Text(stringResource(R.string.common_save))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete))
                }
            }
        }
        item {
            SettingCheckRow(
                label = stringResource(R.string.sound_shuffle_enabled),
                checked = pool.enabled,
                onCheckedChange = onEnabledChange,
                useSwitch = true,
            )
        }
        item { SectionLabel(stringResource(R.string.sound_shuffle_targets_title)) }
        items(SoundShuffleTarget.entries, key = SoundShuffleTarget::name) { target ->
            val selected = target in pool.targets
            SettingCheckRow(
                label = soundShuffleTargetLabel(target),
                checked = selected,
                onCheckedChange = { checked ->
                    val changed = if (checked) pool.targets + target else pool.targets - target
                    onTargetsChange(changed.distinct())
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        stringResource(R.string.sound_shuffle_schedule_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        soundShuffleIntervalLabel(pool.intervalHours),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    OutlinedButton(
                        onClick = { scheduleMenuOpen = true },
                        shape = RoundedCornerShape(8.dp),
                    ) { Text(soundShuffleIntervalLabel(pool.intervalHours)) }
                    DropdownMenu(
                        expanded = scheduleMenuOpen,
                        onDismissRequest = { scheduleMenuOpen = false },
                    ) {
                        SOUND_SHUFFLE_INTERVAL_HOURS.forEach { hours ->
                            DropdownMenuItem(
                                text = { Text(soundShuffleIntervalLabel(hours)) },
                                onClick = {
                                    scheduleMenuOpen = false
                                    onIntervalChange(hours)
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onRunNow,
                enabled = canWriteSettings && pool.enabled && pool.targets.isNotEmpty() && pool.items.isNotEmpty(),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(stringResource(R.string.sound_shuffle_run_now), Modifier.padding(start = 8.dp))
            }
        }
        item { SectionLabel(stringResource(R.string.sound_shuffle_members_title)) }
        if (pool.items.isEmpty()) {
            item { EmptyLabel(stringResource(R.string.sound_shuffle_members_empty)) }
        }
        items(pool.items, key = SoundShufflePoolItem::itemId) { item ->
            PoolMemberRow(
                item = item,
                currentDownload = item.sourceId.takeIf {
                    item.sourceKind == SoundShuffleSourceKind.DOWNLOAD
                }?.let(downloadsById::get),
                onRelink = onRelink,
                onRemove = onRemove,
            )
        }
        item {
            OutlinedButton(
                onClick = onAddLocal,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Text(stringResource(R.string.sound_shuffle_add_local), Modifier.padding(start = 8.dp))
            }
        }
        item { SectionLabel(stringResource(R.string.sound_shuffle_downloads_title)) }
        if (downloads.isEmpty()) {
            item { EmptyLabel(stringResource(R.string.sound_shuffle_downloads_empty)) }
        }
        items(downloads, key = DownloadEntity::id) { download ->
            val added = "download:${download.id}" in itemIds
            SoundChoiceRow(
                name = download.name.ifBlank { stringResource(R.string.sound_shuffle_source_download) },
                detail = buildString {
                    append(stringResource(R.string.sound_shuffle_source_download))
                    if (download.originalDurationMs > 0) {
                        append(" · ")
                        append(soundShuffleDurationLabel(download.originalDurationMs))
                    }
                },
                added = added,
                available = normalizeLocalMediaStatus(download.localMediaStatus) == LocalMediaStatus.AVAILABLE,
                onToggle = { onToggleDownload(download, added) },
            )
        }
        item { SectionLabel(stringResource(R.string.sound_shuffle_originals_title)) }
        items(originals, key = Sound::id) { original ->
            val added = "bundled:${original.id}" in itemIds
            SoundChoiceRow(
                name = original.name,
                detail = stringResource(
                    R.string.sound_shuffle_history_row,
                    stringResource(R.string.sound_shuffle_source_original),
                    soundShuffleDurationLabel((original.duration * 1_000).toLong()),
                    original.tags.firstOrNull().orEmpty(),
                ),
                added = added,
                available = true,
                onToggle = { onToggleOriginal(original, added) },
            )
        }
        item { SectionLabel(stringResource(R.string.sound_shuffle_history_title)) }
        if (history.isEmpty()) {
            item { EmptyLabel(stringResource(R.string.sound_shuffle_history_empty)) }
        }
        items(history.take(30), key = SoundShuffleHistoryEntry::historyId) { entry ->
            SoundShuffleHistoryRow(entry = entry, onUndo = onUndo)
        }
    }
}

@Composable
private fun SettingCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    useSwitch: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        if (useSwitch) {
            Switch(checked = checked, onCheckedChange = null)
        } else {
            Checkbox(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun PoolMemberRow(
    item: SoundShufflePoolItem,
    currentDownload: DownloadEntity?,
    onRelink: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val needsRelink = when (item.sourceKind) {
        SoundShuffleSourceKind.BUNDLED -> false
        SoundShuffleSourceKind.LOCAL ->
            normalizeLocalMediaStatus(item.localMediaStatus) != LocalMediaStatus.AVAILABLE || item.locator.isBlank()
        SoundShuffleSourceKind.DOWNLOAD ->
            currentDownload == null ||
                normalizeLocalMediaStatus(currentDownload.localMediaStatus) != LocalMediaStatus.AVAILABLE ||
                currentDownload.localPath.isBlank()
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (needsRelink) {
                            stringResource(R.string.sound_shuffle_needs_relink)
                        } else {
                            "${soundShuffleSourceLabel(item.sourceKind)} · ${soundShuffleDurationLabel(item.durationMs)}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (needsRelink) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (needsRelink && item.sourceKind != SoundShuffleSourceKind.BUNDLED) {
                    TextButton(onClick = { onRelink(item.itemId) }) {
                        Text(stringResource(R.string.sound_shuffle_relink))
                    }
                }
                TextButton(onClick = { onRemove(item.itemId) }) {
                    Text(stringResource(R.string.sound_shuffle_remove))
                }
            }
        }
    }
}

@Composable
private fun SoundChoiceRow(
    name: String,
    detail: String,
    added: Boolean,
    available: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (available) detail else stringResource(R.string.sound_shuffle_needs_relink),
                style = MaterialTheme.typography.bodySmall,
                color = if (available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onToggle, enabled = available || added) {
            Text(stringResource(if (added) R.string.sound_shuffle_remove else R.string.sound_shuffle_add))
        }
    }
}

@Composable
private fun SoundShuffleHistoryRow(
    entry: SoundShuffleHistoryEntry,
    onUndo: (String) -> Unit,
) {
    val status = if (entry.undoneAt > 0) {
        stringResource(R.string.sound_shuffle_status_undone)
    } else {
        when (entry.status) {
            SoundShuffleHistoryStatus.APPLIED -> stringResource(R.string.sound_shuffle_status_applied)
            SoundShuffleHistoryStatus.SKIPPED -> stringResource(R.string.sound_shuffle_status_skipped)
            SoundShuffleHistoryStatus.FAILED -> stringResource(R.string.sound_shuffle_status_failed)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.itemName.ifBlank { soundShuffleTargetLabel(entry.target) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(
                    R.string.sound_shuffle_history_row,
                    status,
                    soundShuffleTargetLabel(entry.target),
                    soundShuffleHistoryMessage(entry),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.status == SoundShuffleHistoryStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (entry.status == SoundShuffleHistoryStatus.APPLIED && entry.undoneAt == 0L) {
            TextButton(onClick = { onUndo(entry.historyId) }) {
                Text(stringResource(R.string.common_undo))
            }
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun EmptyLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun soundShuffleTargetLabel(target: SoundShuffleTarget): String = when (target) {
    SoundShuffleTarget.RINGTONE -> stringResource(R.string.sound_shuffle_target_ringtone)
    SoundShuffleTarget.NOTIFICATION -> stringResource(R.string.sound_shuffle_target_notification)
    SoundShuffleTarget.ALARM -> stringResource(R.string.sound_shuffle_target_alarm)
}

@Composable
private fun soundShuffleHistoryMessage(entry: SoundShuffleHistoryEntry): String {
    val reason = SoundShuffleReason.entries.firstOrNull { it.name == entry.reason } ?: return entry.message
    return when (reason) {
        SoundShuffleReason.APPLIED_AUTOMATICALLY -> stringResource(R.string.sound_shuffle_reason_applied)
        SoundShuffleReason.WRITE_SETTINGS_MISSING -> stringResource(R.string.sound_shuffle_reason_permission)
        SoundShuffleReason.POOL_EMPTY -> stringResource(R.string.sound_shuffle_reason_pool_empty)
        SoundShuffleReason.DOWNLOAD_MISSING -> stringResource(R.string.sound_shuffle_reason_download_missing)
        SoundShuffleReason.DOWNLOAD_NOT_AUDIO -> stringResource(R.string.sound_shuffle_reason_download_not_audio)
        SoundShuffleReason.DOWNLOAD_NEEDS_RELINK -> stringResource(R.string.sound_shuffle_reason_download_relink)
        SoundShuffleReason.ORIGINAL_UNAVAILABLE -> stringResource(R.string.sound_shuffle_reason_original_unavailable)
        SoundShuffleReason.LOCAL_NEEDS_RELINK -> stringResource(R.string.sound_shuffle_reason_local_relink)
        SoundShuffleReason.FILE_MISSING -> stringResource(R.string.sound_shuffle_reason_file_missing)
        SoundShuffleReason.FILE_UNREADABLE -> stringResource(R.string.sound_shuffle_reason_file_unreadable)
        SoundShuffleReason.DURATION_INCOMPATIBLE -> when (entry.target) {
            SoundShuffleTarget.RINGTONE -> stringResource(R.string.sound_shuffle_reason_duration_ringtone)
            SoundShuffleTarget.NOTIFICATION -> stringResource(R.string.sound_shuffle_reason_duration_notification)
            SoundShuffleTarget.ALARM -> stringResource(R.string.sound_shuffle_reason_duration_alarm)
        }
        SoundShuffleReason.NOT_AUDIO -> stringResource(R.string.sound_shuffle_reason_not_audio)
        // SoundApplier already localizes its own failures, so a recorded error stays more specific.
        SoundShuffleReason.APPLY_FAILED ->
            entry.message.ifBlank { stringResource(R.string.sound_shuffle_reason_apply_failed) }
    }
}

@Composable
private fun soundShuffleTargetSummary(targets: List<SoundShuffleTarget>): String {
    if (targets.isEmpty()) return stringResource(R.string.sound_shuffle_no_target)
    val labels = mutableListOf<String>()
    for (target in targets) labels += soundShuffleTargetLabel(target)
    return labels.joinToString(", ")
}

@Composable
private fun soundShuffleIntervalLabel(hours: Long): String = when (hours) {
    1L -> stringResource(R.string.settings_sounds_shuffle_every_hour)
    24L -> stringResource(R.string.sound_shuffle_every_day)
    72L -> stringResource(R.string.sound_shuffle_every_three_days)
    else -> stringResource(R.string.sound_shuffle_every_hours, hours)
}

@Composable
private fun soundShuffleDurationLabel(durationMs: Long): String =
    stringResource(R.string.sound_shuffle_duration_seconds, durationMs.coerceAtLeast(0) / 1_000.0)

@Composable
private fun soundShuffleSourceLabel(sourceKind: SoundShuffleSourceKind): String = when (sourceKind) {
    SoundShuffleSourceKind.DOWNLOAD -> stringResource(R.string.sound_shuffle_source_download)
    SoundShuffleSourceKind.LOCAL -> stringResource(R.string.sound_shuffle_source_local)
    SoundShuffleSourceKind.BUNDLED -> stringResource(R.string.sound_shuffle_source_original)
}
