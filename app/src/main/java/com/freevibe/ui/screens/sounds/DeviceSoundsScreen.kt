package com.freevibe.ui.screens.sounds

import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import com.freevibe.service.AudioPlaybackManager
import com.freevibe.service.DeviceSound
import com.freevibe.service.DeviceSoundCatalog
import com.freevibe.service.OriginalRestore
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundShufflePoolManager
import com.freevibe.service.SoundShuffleTarget
import com.freevibe.service.SystemSoundOriginal
import com.freevibe.service.SystemSoundWriter
import com.freevibe.service.parseProfiles
import com.freevibe.service.sameSystemSound
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.theme.AuraShapes
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the screen has to tell the user after an action. The text is resolved in the composable. */
internal sealed interface DeviceSoundsNotice {
    data class Applied(val title: String) : DeviceSoundsNotice
    data object Restored : DeviceSoundsNotice
    data object RestoredSilent : DeviceSoundsNotice
    data object RestoredButAutomated : DeviceSoundsNotice
    data object NeverChanged : DeviceSoundsNotice
    data object OriginalUnknown : DeviceSoundsNotice
    data object OriginalMissing : DeviceSoundsNotice
    data object NeedsPermission : DeviceSoundsNotice
    data object Failed : DeviceSoundsNotice
}

internal data class DeviceSoundsState(
    val target: SoundShuffleTarget = SoundShuffleTarget.RINGTONE,
    val sounds: List<DeviceSound> = emptyList(),
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The sound set for [target] right now, or null for no sound. */
    val currentUri: String? = null,
    /** What was set before Aura first changed [target], or null when Aura never has. */
    val original: SystemSoundOriginal? = null,
    val notice: DeviceSoundsNotice? = null,
)

/** The notice for a finished restore. [automated] is whether a shuffle pool or sound profile will change it again. */
internal fun restoreNotice(outcome: OriginalRestore, automated: Boolean): DeviceSoundsNotice = when (outcome) {
    OriginalRestore.NeverChanged -> DeviceSoundsNotice.NeverChanged
    OriginalRestore.Unknown -> DeviceSoundsNotice.OriginalUnknown
    OriginalRestore.Missing -> DeviceSoundsNotice.OriginalMissing
    is OriginalRestore.Restored -> when {
        automated -> DeviceSoundsNotice.RestoredButAutomated
        outcome.uri == null -> DeviceSoundsNotice.RestoredSilent
        else -> DeviceSoundsNotice.Restored
    }
}

/** Restoring is on offer only while a real original is recorded and it isn't what's already set. */
internal fun canRestoreOriginal(original: SystemSoundOriginal?, currentUri: String?): Boolean = when (original) {
    null, SystemSoundOriginal.Unknown -> false
    SystemSoundOriginal.Silent -> !currentUri.isNullOrEmpty()
    is SystemSoundOriginal.Sound -> !sameSystemSound(original.uri, currentUri)
}

private fun DeviceSound.previewSound() =
    Sound(id = uri, source = ContentSource.LOCAL, name = title, previewUrl = uri, downloadUrl = uri)

@HiltViewModel
class DeviceSoundsViewModel @Inject constructor(
    private val catalog: DeviceSoundCatalog,
    private val systemSounds: SystemSoundWriter,
    private val soundApplier: SoundApplier,
    private val playback: AudioPlaybackManager,
    private val prefs: PreferencesManager,
    private val shufflePools: SoundShufflePoolManager,
) : ViewModel() {
    private val _state = MutableStateFlow(DeviceSoundsState())
    internal val state: StateFlow<DeviceSoundsState> = _state.asStateFlow()

    /** The URI of the device sound being previewed, if one is. */
    val previewingUri: StateFlow<String?> =
        combine(playback.currentSoundId, playback.isPlaying, _state) { playingId, isPlaying, state ->
            state.sounds.firstOrNull { isPlaying && it.previewSound().stableKey() == playingId }?.uri
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load(SoundShuffleTarget.RINGTONE)
    }

    fun select(target: SoundShuffleTarget) {
        if (target == _state.value.target) return
        stopPreview()
        load(target)
    }

    fun retry() = load(_state.value.target)

    fun togglePreview(sound: DeviceSound) {
        if (previewingUri.value == sound.uri) {
            playback.stop()
            return
        }
        viewModelScope.launch {
            playback.play(sound.previewSound(), sound.uri, prefs.soundPreviewVolume.first())
        }
    }

    fun setAsDefault(sound: DeviceSound) {
        if (!soundApplier.canWriteSettings()) return post(DeviceSoundsNotice.NeedsPermission)
        val target = _state.value.target
        viewModelScope.launch {
            val applied = soundApplier.applyExistingUri(Uri.parse(sound.uri), target.contentType).isSuccess
            refreshCurrent(target, if (applied) DeviceSoundsNotice.Applied(sound.title) else DeviceSoundsNotice.Failed)
        }
    }

    fun restoreOriginal() {
        if (!soundApplier.canWriteSettings()) return post(DeviceSoundsNotice.NeedsPermission)
        val target = _state.value.target
        viewModelScope.launch {
            val outcome = runCatching { systemSounds.restoreOriginal(target) }
                .onFailure { it.rethrowIfCancelled() }
                .getOrNull()
            // A restore that worked is reported as one even if the automation check can't be read.
            val automated = runCatching { stillAutomated(target) }
                .onFailure { it.rethrowIfCancelled() }
                .getOrDefault(false)
            refreshCurrent(target, outcome?.let { restoreNotice(it, automated) } ?: DeviceSoundsNotice.Failed)
        }
    }

    fun writeSettingsIntent() = soundApplier.requestWriteSettings()

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    override fun onCleared() {
        stopPreview()
    }

    private fun post(notice: DeviceSoundsNotice) = _state.update { it.copy(notice = notice) }

    private fun stopPreview() {
        if (previewingUri.value != null) playback.stop()
    }

    private fun load(target: SoundShuffleTarget) {
        _state.update { it.copy(target = target, sounds = emptyList(), loading = true, loadFailed = false) }
        viewModelScope.launch {
            val sounds = catalog.list(target)
            val original = SystemSoundOriginal.decode(prefs.systemSoundOriginal(target.contentType).first())
            _state.update {
                if (it.target != target) return@update it
                it.copy(
                    sounds = sounds.getOrDefault(emptyList()),
                    loading = false,
                    loadFailed = sounds.isFailure,
                    currentUri = systemSounds.current(target),
                    original = original,
                )
            }
        }
    }

    private suspend fun refreshCurrent(target: SoundShuffleTarget, notice: DeviceSoundsNotice) {
        val original = SystemSoundOriginal.decode(prefs.systemSoundOriginal(target.contentType).first())
        _state.update {
            if (it.target != target) return@update it.copy(notice = notice)
            it.copy(currentUri = systemSounds.current(target), original = original, notice = notice)
        }
    }

    /** Whether something Aura runs on a schedule will set [target] again after a restore. */
    private suspend fun stillAutomated(target: SoundShuffleTarget): Boolean {
        val pooled = shufflePools.store.first().pools.any { it.enabled && target in it.targets }
        val profiled = prefs.soundProfilesEnabled.first() &&
            parseProfiles(prefs.soundProfilesJson.first()).any { profile ->
                profile.enabled && when (target) {
                    SoundShuffleTarget.RINGTONE -> profile.ringtoneUri
                    SoundShuffleTarget.NOTIFICATION -> profile.notificationUri
                    SoundShuffleTarget.ALARM -> profile.alarmUri
                }.isNotBlank()
            }
        return pooled || profiled
    }
}

@Composable
fun DeviceSoundsScreen(
    onBack: () -> Unit,
    viewModel: DeviceSoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val previewingUri by viewModel.previewingUri.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current

    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        val needsPermission = notice == DeviceSoundsNotice.NeedsPermission
        val result = snackbarHostState.showSnackbar(
            message = when (notice) {
                is DeviceSoundsNotice.Applied -> resources.getString(R.string.device_sounds_applied, notice.title)
                DeviceSoundsNotice.Restored -> resources.getString(R.string.device_sounds_restored)
                DeviceSoundsNotice.RestoredSilent -> resources.getString(R.string.device_sounds_restored_silent)
                DeviceSoundsNotice.RestoredButAutomated -> resources.getString(R.string.device_sounds_restored_automated)
                DeviceSoundsNotice.NeverChanged -> resources.getString(R.string.device_sounds_never_changed)
                DeviceSoundsNotice.OriginalUnknown -> resources.getString(R.string.device_sounds_original_unknown)
                DeviceSoundsNotice.OriginalMissing -> resources.getString(R.string.device_sounds_original_missing)
                DeviceSoundsNotice.NeedsPermission -> resources.getString(R.string.device_sounds_needs_permission)
                DeviceSoundsNotice.Failed -> resources.getString(R.string.device_sounds_failed)
            },
            actionLabel = if (needsPermission) resources.getString(R.string.write_settings_open) else null,
            duration = if (needsPermission) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (needsPermission && result == SnackbarResult.ActionPerformed) {
            runCatching { context.startActivity(viewModel.writeSettingsIntent()) }
        }
        // Cleared only once the snackbar is done: clearing first would restart this effect and cancel it.
        viewModel.consumeNotice()
    }

    DeviceSoundsContent(
        state = state,
        previewingUri = previewingUri,
        onSelect = viewModel::select,
        onTogglePreview = viewModel::togglePreview,
        onApply = viewModel::setAsDefault,
        onRestore = viewModel::restoreOriginal,
        onRetry = viewModel::retry,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceSoundsContent(
    state: DeviceSoundsState,
    previewingUri: String?,
    onSelect: (SoundShuffleTarget) -> Unit,
    onTogglePreview: (DeviceSound) -> Unit,
    onApply: (DeviceSound) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_device_sounds)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoundShuffleTarget.entries.forEach { target ->
                    FilterChip(
                        selected = state.target == target,
                        onClick = { onSelect(target) },
                        label = { Text(stringResource(targetLabel(target))) },
                    )
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadFailed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.MusicOff,
                        title = stringResource(R.string.device_sounds_load_failed),
                        description = stringResource(R.string.device_sounds_load_failed_body),
                        modifier = Modifier.padding(24.dp),
                        primaryAction = AuraStateAction(stringResource(R.string.common_retry), Icons.Default.Refresh, onRetry),
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "current") { CurrentSoundCard(state = state, onRestore = onRestore) }
                    if (state.sounds.isEmpty()) {
                        item(key = "empty") {
                            AuraStateCard(
                                icon = Icons.Default.MusicOff,
                                title = stringResource(R.string.device_sounds_empty_title),
                                description = stringResource(R.string.device_sounds_empty_body),
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(state.sounds, key = { it.uri }) { sound ->
                        DeviceSoundRow(
                            sound = sound,
                            isCurrent = sameSystemSound(sound.uri, state.currentUri),
                            isPreviewing = previewingUri == sound.uri,
                            onTogglePreview = { onTogglePreview(sound) },
                            onApply = { onApply(sound) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentSoundCard(state: DeviceSoundsState, onRestore: () -> Unit) {
    val currentTitle = when {
        state.currentUri.isNullOrEmpty() -> stringResource(R.string.device_sounds_none)
        else -> state.sounds.firstOrNull { sameSystemSound(it.uri, state.currentUri) }?.title
            ?: stringResource(R.string.device_sounds_unlisted)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AuraShapes.Large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.device_sounds_set_now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Text(currentTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when (state.original) {
                        null -> R.string.device_sounds_never_changed
                        SystemSoundOriginal.Unknown -> R.string.device_sounds_original_unknown
                        else -> if (canRestoreOriginal(state.original, state.currentUri)) {
                            R.string.device_sounds_restore_body
                        } else {
                            R.string.device_sounds_original_is_set
                        }
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canRestoreOriginal(state.original, state.currentUri)) {
                OutlinedButton(onClick = onRestore, shape = AuraShapes.Medium, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.device_sounds_restore))
                }
            }
        }
    }
}

@Composable
private fun DeviceSoundRow(
    sound: DeviceSound,
    isCurrent: Boolean,
    isPreviewing: Boolean,
    onTogglePreview: () -> Unit,
    onApply: () -> Unit,
) {
    val previewDescription = stringResource(
        if (isPreviewing) R.string.device_sounds_stop_cd else R.string.device_sounds_play_cd,
        sound.title,
    )
    val applyDescription = stringResource(R.string.device_sounds_set_cd, sound.title)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = AuraShapes.Large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 64.dp).padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onTogglePreview) {
                Icon(if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow, previewDescription)
            }
            Column(Modifier.weight(1f)) {
                Text(sound.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(if (sound.builtIn) R.string.device_sounds_built_in else R.string.device_sounds_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isCurrent) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.device_sounds_current),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                TextButton(onClick = onApply, modifier = Modifier.semantics { contentDescription = applyDescription }) {
                    Text(stringResource(R.string.device_sounds_set))
                }
            }
        }
    }
}

private fun targetLabel(target: SoundShuffleTarget): Int = when (target) {
    SoundShuffleTarget.RINGTONE -> R.string.sound_shuffle_target_ringtone
    SoundShuffleTarget.NOTIFICATION -> R.string.sound_shuffle_target_notification
    SoundShuffleTarget.ALARM -> R.string.sound_shuffle_target_alarm
}
