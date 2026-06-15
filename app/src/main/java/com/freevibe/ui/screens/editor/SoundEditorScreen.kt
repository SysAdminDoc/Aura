package com.freevibe.ui.screens.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

// ── UI ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundEditorScreen(
    soundId: String? = null,
    fallbackSound: Sound? = null,
    initialLocalUri: Uri? = null,
    editConfirmed: Boolean = false,
    onBack: () -> Unit,
    recoveryViewModel: com.freevibe.ui.screens.sounds.SoundsViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    viewModel: SoundEditorViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentSelectedSound by recoveryViewModel.selectedSound.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val playPreviewLabel = stringResource(R.string.a11y_play_preview)
    val pausePreviewLabel = stringResource(R.string.a11y_pause_preview)
    val playingState = stringResource(R.string.a11y_preview_playing)
    val readyState = stringResource(R.string.a11y_ready)
    val fadeInLabel = stringResource(R.string.a11y_fade_in)
    val fadeOutLabel = stringResource(R.string.a11y_fade_out)
    val fadeInState = stringResource(R.string.a11y_duration_ms, state.fadeInMs)
    val fadeOutState = stringResource(R.string.a11y_duration_ms, state.fadeOutMs)
    val writeSettingsTitle = stringResource(R.string.write_settings_title)
    val writeSettingsBody = stringResource(R.string.write_settings_body)
    val openSettingsLabel = stringResource(R.string.write_settings_open)
    val writeSettingsUnavailable = stringResource(R.string.write_settings_unavailable)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var writeSettingsRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                writeSettingsRefresh += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val canWriteSettings = remember(writeSettingsRefresh, state.isApplying) { viewModel.canWriteSettings() }
    val canOpenWriteSettings = remember(writeSettingsRefresh) { viewModel.canOpenWriteSettings() }
    fun openWriteSettings() {
        if (!canOpenWriteSettings) {
            scope.launch { snackbarHostState.showSnackbar(writeSettingsUnavailable) }
            return
        }
        runCatching { context.startActivity(viewModel.requestWriteSettings()) }
            .onFailure { scope.launch { snackbarHostState.showSnackbar(writeSettingsUnavailable) } }
    }
    val editorIdentityKey = remember(soundId, fallbackSound?.source, fallbackSound?.previewUrl, fallbackSound?.downloadUrl, initialLocalUri, editConfirmed) {
        listOf(
            soundId.orEmpty(),
            fallbackSound?.source?.name.orEmpty(),
            fallbackSound?.previewUrl.orEmpty(),
            fallbackSound?.downloadUrl.orEmpty(),
            initialLocalUri?.toString().orEmpty(),
            editConfirmed.toString(),
        ).joinToString("|")
    }
    var selectionResolved by remember(editorIdentityKey) {
        mutableStateOf<Boolean?>(if (soundId == null) true else null)
    }

    // Local file picker
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadFromLocalUri(it) }
    }

    LaunchedEffect(initialLocalUri) {
        initialLocalUri?.let { viewModel.loadFromLocalUri(it) }
    }

    LaunchedEffect(state.success) {
        state.success?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearMessages() }
    }
    LaunchedEffect(soundId, fallbackSound?.source, fallbackSound?.previewUrl, fallbackSound?.downloadUrl, editConfirmed) {
        if (soundId == null) {
            selectionResolved = true
        } else {
            val sound = fallbackSound?.let {
                recoveryViewModel.resolveSound(
                    id = soundId,
                    source = it.source,
                    previewUrl = it.previewUrl.takeIf { url -> url.isNotBlank() },
                    downloadUrl = it.downloadUrl.takeIf { url -> url.isNotBlank() },
                ) ?: it
            } ?: recoveryViewModel.resolveSound(soundId)
            selectionResolved = sound?.let { viewModel.loadSound(it, editConfirmed = editConfirmed) } ?: false
        }
    }
    LaunchedEffect(soundId, currentSelectedSound?.stableKey(), initialLocalUri) {
        if (soundId == null && initialLocalUri == null) {
            currentSelectedSound?.let { viewModel.loadSound(it) }
        }
    }

    // NX-13: unsaved-changes guard. Audio edits (trim, fade, normalize) survive
    // FFmpeg invocation cost. Backing out unintentionally costs the user a
    // careful trim pass and a 2-5 s FFmpeg roundtrip.
    val hasUnsavedChanges = state.trimStartFraction != 0f ||
        state.trimEndFraction != 1f ||
        state.fadeInMs != 0L ||
        state.fadeOutMs != 0L
    var showSoundDiscardConfirm by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = hasUnsavedChanges && !state.isApplying) {
        showSoundDiscardConfirm = true
    }
    if (showSoundDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showSoundDiscardConfirm = false },
            title = { Text(stringResource(R.string.editor_sound_discard_title)) },
            text = { Text(stringResource(R.string.editor_sound_discard_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSoundDiscardConfirm = false
                    onBack()
                }) { Text(stringResource(R.string.common_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showSoundDiscardConfirm = false }) { Text(stringResource(R.string.common_keep_editing)) }
            },
            shape = RoundedCornerShape(8.dp),
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (soundId == null) R.string.editor_sound_create_title else R.string.editor_sound_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
                },
                actions = {
                    if (viewModel.canUndo) {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.editor_sound_undo))
                        }
                    }
                    IconButton(onClick = { filePicker.launch("audio/*") }) {
                        Icon(Icons.Default.FolderOpen, stringResource(R.string.editor_sound_open_file))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (soundId != null && selectionResolved == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.editor_sound_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }

        if (soundId != null && selectionResolved == false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                AuraStateCard(
                    icon = Icons.Default.MusicOff,
                    title = stringResource(R.string.editor_sound_unavailable_title),
                    description = stringResource(R.string.editor_sound_unavailable_body),
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStateAction(stringResource(R.string.editor_sound_unavailable_action), Icons.AutoMirrored.Filled.ArrowBack, onBack),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // File name
            Text(
                state.fileName.ifEmpty { stringResource(R.string.editor_sound_no_audio) },
                style = MaterialTheme.typography.titleLarge,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.editor_sound_preparing_waveform), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.editor_sound_preparing_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (state.waveform.isEmpty() && state.localFilePath == null) {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    AuraStateCard(
                        icon = Icons.Default.AudioFile,
                        title = stringResource(R.string.editor_sound_open_title),
                        description = stringResource(R.string.editor_sound_open_body),
                        tone = MaterialTheme.colorScheme.primary,
                        primaryAction = AuraStateAction(
                            label = stringResource(R.string.editor_sound_browse),
                            icon = Icons.Default.FolderOpen,
                            onClick = { filePicker.launch("audio/*") },
                        ),
                    )
                }
            } else if (state.waveform.isNotEmpty()) {
                // Waveform with trim handles
                WaveformView(
                    waveform = state.waveform,
                    trimStart = state.trimStartFraction,
                    trimEnd = state.trimEndFraction,
                    playbackPosition = state.playbackPosition,
                    isPlaying = state.isPlaying,
                    fadeInFraction = if (state.durationMs > 0) state.fadeInMs.toFloat() / state.durationMs else 0f,
                    fadeOutFraction = if (state.durationMs > 0) state.fadeOutMs.toFloat() / state.durationMs else 0f,
                    onDragStart = { viewModel.saveUndo() },
                    onTrimStartChange = { viewModel.setTrimStart(it) },
                    onTrimEndChange = { viewModel.setTrimEnd(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )

                // Time display
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        formatMs(state.trimStartMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.editor_sound_duration, formatMs(state.trimDurationMs)),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        formatMs(state.trimEndMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                TrimGuidance(trimDurationMs = state.trimDurationMs)

                // Playback controls
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        onClick = { viewModel.togglePlayback() },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .semantics {
                                contentDescription = if (state.isPlaying) pausePreviewLabel else playPreviewLabel
                                stateDescription = if (state.isPlaying) playingState else readyState
                                onClick(label = if (state.isPlaying) pausePreviewLabel else playPreviewLabel, action = null)
                            },
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                // Fade controls
                Text(stringResource(R.string.editor_sound_fade_effects), style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Fade In
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.editor_sound_fade_in, state.fadeInMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        var fadeInUndoSaved by remember { mutableStateOf(false) }
                        Slider(
                            value = state.fadeInMs.toFloat(),
                            onValueChange = {
                                if (!fadeInUndoSaved) { viewModel.saveUndo(); fadeInUndoSaved = true }
                                viewModel.setFadeIn(it.toLong())
                            },
                            onValueChangeFinished = { fadeInUndoSaved = false },
                            valueRange = 0f..(state.trimDurationMs / 2f).coerceAtLeast(1f),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .semantics {
                                    contentDescription = fadeInLabel
                                    stateDescription = fadeInState
                                    progressBarRangeInfo = ProgressBarRangeInfo(
                                        state.fadeInMs.toFloat(),
                                        0f..(state.trimDurationMs / 2f).coerceAtLeast(1f),
                                    )
                                },
                        )
                    }
                    // Fade Out
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.editor_sound_fade_out, state.fadeOutMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        var fadeOutUndoSaved by remember { mutableStateOf(false) }
                        Slider(
                            value = state.fadeOutMs.toFloat(),
                            onValueChange = {
                                if (!fadeOutUndoSaved) { viewModel.saveUndo(); fadeOutUndoSaved = true }
                                viewModel.setFadeOut(it.toLong())
                            },
                            onValueChangeFinished = { fadeOutUndoSaved = false },
                            valueRange = 0f..(state.trimDurationMs / 2f).coerceAtLeast(1f),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .semantics {
                                    contentDescription = fadeOutLabel
                                    stateDescription = fadeOutState
                                    progressBarRangeInfo = ProgressBarRangeInfo(
                                        state.fadeOutMs.toFloat(),
                                        0f..(state.trimDurationMs / 2f).coerceAtLeast(1f),
                                    )
                                },
                        )
                    }
                }

                // Format convert
                Text("Convert Format", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val formats = listOf("mp3", "ogg", "opus", "wav", "flac", "m4a")
                    val currentExt = state.localFilePath
                        ?.substringAfterLast(".", "")
                        ?.lowercase(java.util.Locale.ROOT)
                        ?: ""
                    for (fmt in formats) {
                        FilterChip(
                            selected = currentExt == fmt,
                            onClick = { if (currentExt != fmt) viewModel.convertFormat(fmt) },
                            label = { Text(fmt.uppercase(java.util.Locale.ROOT), style = MaterialTheme.typography.labelSmall) },
                            enabled = !state.isApplying && currentExt != fmt,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Apply buttons
                Text(stringResource(R.string.editor_sound_apply_as), style = MaterialTheme.typography.labelLarge)
                if (!canWriteSettings) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Column(Modifier.weight(1f)) {
                                Text(writeSettingsTitle, style = MaterialTheme.typography.labelLarge)
                                Text(writeSettingsBody, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = ::openWriteSettings, enabled = canOpenWriteSettings) {
                                Text(openSettingsLabel)
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ApplyBtn(stringResource(R.string.editor_sound_apply_ringtone), Modifier.weight(1f), state.isApplying, enabled = canWriteSettings) {
                        viewModel.applyTrimmed(ContentType.RINGTONE)
                    }
                    ApplyBtn(stringResource(R.string.editor_sound_apply_notification), Modifier.weight(1f), state.isApplying, enabled = canWriteSettings) {
                        viewModel.applyTrimmed(ContentType.NOTIFICATION)
                    }
                    ApplyBtn(stringResource(R.string.editor_sound_apply_alarm), Modifier.weight(1f), state.isApplying, enabled = canWriteSettings) {
                        viewModel.applyTrimmed(ContentType.ALARM)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrimGuidance(trimDurationMs: Long) {
    val isIdealRingtoneLength = trimDurationMs in MIN_RINGTONE_TRIM_MS..MAX_RINGTONE_TRIM_MS
    val isVeryShort = trimDurationMs in 1 until MIN_RINGTONE_TRIM_MS
    val containerColor = when {
        isIdealRingtoneLength -> MaterialTheme.colorScheme.primaryContainer
        isVeryShort -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        isIdealRingtoneLength -> MaterialTheme.colorScheme.onPrimaryContainer
        isVeryShort -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val title = when {
        isIdealRingtoneLength -> stringResource(R.string.editor_sound_guidance_ready)
        isVeryShort -> stringResource(R.string.editor_sound_guidance_short)
        else -> stringResource(R.string.editor_sound_guidance_long)
    }
    val body = when {
        isIdealRingtoneLength -> stringResource(R.string.editor_sound_guidance_ready_body)
        isVeryShort -> stringResource(R.string.editor_sound_guidance_short_body)
        else -> stringResource(R.string.editor_sound_guidance_long_body)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ApplyBtn(text: String, modifier: Modifier, isLoading: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val applyLabel = stringResource(R.string.a11y_set_trimmed_audio_as, text)
    val applyState = if (isLoading) {
        stringResource(R.string.a11y_applying)
    } else if (!enabled) {
        stringResource(R.string.write_settings_required_short)
    } else {
        stringResource(R.string.a11y_ready)
    }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .semantics {
                stateDescription = applyState
                onClick(label = applyLabel, action = null)
            },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Waveform visualization with draggable trim handles + fade overlays */
@Composable
private fun WaveformView(
    waveform: FloatArray,
    trimStart: Float,
    trimEnd: Float,
    playbackPosition: Float,
    isPlaying: Boolean,
    onTrimStartChange: (Float) -> Unit,
    onTrimEndChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fadeInFraction: Float = 0f,
    fadeOutFraction: Float = 0f,
    onDragStart: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val playhead = MaterialTheme.colorScheme.tertiary
    val fadeColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
    val trimStartPercent = (trimStart.coerceIn(0f, 1f) * 100).toInt()
    val trimEndPercent = (trimEnd.coerceIn(0f, 1f) * 100).toInt()
    val trimDescription = stringResource(R.string.a11y_trim_waveform)
    val trimState = stringResource(R.string.a11y_trim_range, trimStartPercent, trimEndPercent)
    val playbackState = if (isPlaying) {
        stringResource(R.string.a11y_playing_percent, (playbackPosition.coerceIn(0f, 1f) * 100).toInt())
    } else {
        stringResource(R.string.a11y_stopped)
    }
    val trimPlaybackState = stringResource(R.string.a11y_trim_playback_state, trimState, playbackState)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics {
                contentDescription = trimDescription
                stateDescription = trimPlaybackState
                progressBarRangeInfo = ProgressBarRangeInfo(playbackPosition.coerceIn(0f, 1f), 0f..1f)
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { onDragStart() },
                        onHorizontalDrag = { change, _ ->
                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            val distToStart = abs(fraction - trimStart)
                            val distToEnd = abs(fraction - trimEnd)
                            if (distToStart < distToEnd) onTrimStartChange(fraction)
                            else onTrimEndChange(fraction)
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val centerY = h / 2
            val barWidth = w / waveform.size
            val maxAmp = h * 0.45f

            // Draw waveform bars
            for (i in waveform.indices) {
                val x = i * barWidth
                val amplitude = waveform[i] * maxAmp
                val fraction = i.toFloat() / waveform.size
                val inTrim = fraction in trimStart..trimEnd
                val color = if (inTrim) primary else dimmed

                drawLine(
                    color = color,
                    start = Offset(x + barWidth / 2, centerY - amplitude),
                    end = Offset(x + barWidth / 2, centerY + amplitude),
                    strokeWidth = max(barWidth - 1f, 1f),
                )
            }

            // Dimmed overlay outside trim region
            drawRect(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(w * trimStart, h),
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.4f),
                topLeft = Offset(w * trimEnd, 0f),
                size = androidx.compose.ui.geometry.Size(w * (1f - trimEnd), h),
            )

            // Fade in overlay (triangle)
            if (fadeInFraction > 0f) {
                val fadeInX = w * (trimStart + fadeInFraction)
                val trimStartX = w * trimStart
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trimStartX, 0f)
                    lineTo(trimStartX, h)
                    lineTo(fadeInX, h)
                    close()
                }
                drawPath(path, fadeColor)
            }

            // Fade out overlay (triangle)
            if (fadeOutFraction > 0f) {
                val fadeOutStartX = w * (trimEnd - fadeOutFraction)
                val trimEndX = w * trimEnd
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trimEndX, 0f)
                    lineTo(trimEndX, h)
                    lineTo(fadeOutStartX, h)
                    close()
                }
                drawPath(path, fadeColor)
            }

            // Trim handles
            drawTrimHandle(w * trimStart, h, primary)
            drawTrimHandle(w * trimEnd, h, primary)

            // Playback position
            if (isPlaying) {
                drawLine(
                    color = playhead,
                    start = Offset(w * playbackPosition, 0f),
                    end = Offset(w * playbackPosition, h),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

private fun DrawScope.drawTrimHandle(x: Float, height: Float, color: Color) {
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, height),
        strokeWidth = 3.dp.toPx(),
    )
    drawCircle(
        color = color,
        radius = 8.dp.toPx(),
        center = Offset(x, 8.dp.toPx()),
    )
    drawCircle(
        color = color,
        radius = 8.dp.toPx(),
        center = Offset(x, height - 8.dp.toPx()),
    )
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    val frac = (ms % 1000) / 100
    // Locale.ROOT: this is a mm:ss.f timestamp, not a localized number — keep '.' as separator
    // so Arabic/Persian locales don't substitute Eastern-Arabic digits and confuse layout math.
    return String.format(java.util.Locale.ROOT, "%d:%02d.%d", min, sec, frac)
}
