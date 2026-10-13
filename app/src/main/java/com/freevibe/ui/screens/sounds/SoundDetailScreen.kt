package com.freevibe.ui.screens.sounds

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.ShimmerBox
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.SoundAction
import com.freevibe.data.model.SoundActionDecision
import com.freevibe.data.model.SoundLicenseCapabilities
import com.freevibe.data.model.isSourceUnavailable
import com.freevibe.data.model.soundLicenseCapabilities
import com.freevibe.data.model.stableKey
import com.freevibe.ui.components.CommunityReportDialog
import com.freevibe.ui.policy.CommunityUploadPolicyKind
import com.freevibe.ui.policy.communityBlockConfirmationCopy
import com.freevibe.ui.policy.communityOwnerDeleteConfirmationCopy
import com.freevibe.util.tryStartActivity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SoundDetailScreen(
    soundId: String,
    fallbackSound: Sound? = null,
    onBack: () -> Unit,
    onEdit: (Sound) -> Unit = {},
    onContactPicker: (Sound) -> Unit = {},
    onOpenSound: (Sound) -> Unit = {},
    onSearchTag: (String) -> Unit = {},
    viewModel: SoundsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedSound by viewModel.selectedSound.collectAsStateWithLifecycle()
    val fontScale = LocalDensity.current.fontScale
    val targetSource = fallbackSound?.source
    val targetPreviewUrl = fallbackSound?.previewUrl?.takeIf { it.isNotBlank() }
    val targetDownloadUrl = fallbackSound?.downloadUrl?.takeIf { it.isNotBlank() }
    val detailIdentityKey = remember(soundId, targetSource, targetPreviewUrl, targetDownloadUrl) {
        listOf(
            soundId,
            targetSource?.name.orEmpty(),
            targetPreviewUrl.orEmpty(),
            targetDownloadUrl.orEmpty(),
        ).joinToString("|")
    }
    var restoreResolved by remember(detailIdentityKey) { mutableStateOf(false) }
    var resolvedSound by remember(detailIdentityKey) { mutableStateOf<Sound?>(null) }

    LaunchedEffect(soundId, targetSource, targetPreviewUrl, targetDownloadUrl) {
        resolvedSound = fallbackSound?.let {
            viewModel.resolveSound(
                id = soundId,
                source = targetSource,
                previewUrl = targetPreviewUrl,
                downloadUrl = targetDownloadUrl,
            ) ?: it
        } ?: viewModel.resolveSound(soundId)
        restoreResolved = true
    }

    val s = selectedSound?.takeIf { matchesSoundIdentity(it, soundId, targetSource, targetPreviewUrl, targetDownloadUrl) }
        ?: state.sounds.firstOrNull { matchesSoundIdentity(it, soundId, targetSource, targetPreviewUrl, targetDownloadUrl) }
        ?: resolvedSound
    if (s == null) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!restoreResolved) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.sound_detail_opening),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AuraStateCard(
                    icon = Icons.Default.MusicOff,
                    title = stringResource(R.string.sound_detail_unavailable_title),
                    description = stringResource(R.string.sound_detail_unavailable_body),
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStateAction(stringResource(R.string.contact_picker_back_to_sounds), Icons.AutoMirrored.Filled.ArrowBack, onBack),
                )
            }
        }
        return
    }
    val isFavorite by viewModel.isFavorite(s).collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    val autoPreview by viewModel.autoPreview.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val isPlaying = state.playingId == s.stableKey()
    val showUploader = s.uploaderName.isNotEmpty() &&
        s.uploaderName != "Unknown" &&
        !(s.source == ContentSource.BUNDLED && s.uploaderName in setOf("Aura", "Aura Picks"))
    val detailBadges = remember(s, state.selectedTab) { soundBadges(s, state.selectedTab) }
    val sourceTone = soundSourceTone(s.source)
    val sourceLabel = sourceTone.label
    val sourceColor = sourceTone.colorForSurface()
    val sourceUnavailable = s.isSourceUnavailable()
    val licenseCapabilities = remember(s) { s.soundLicenseCapabilities() }
    var pendingSoundAction by remember(s.stableKey()) { mutableStateOf<PendingSoundAction?>(null) }
    val shareBody = remember(s, sourceUnavailable, licenseCapabilities) {
        if (sourceUnavailable || !licenseCapabilities.canUse(SoundAction.SHARE)) {
            ""
        } else {
            buildSoundShareBody(s, licenseCapabilities)
        }
    }
    val canShareSound = shareBody.isNotBlank()
    val canApplySound = !sourceUnavailable && licenseCapabilities.canUse(SoundAction.APPLY)
    val canDownloadSound = !sourceUnavailable && licenseCapabilities.canUse(SoundAction.DOWNLOAD)
    val canEditSound = !sourceUnavailable && licenseCapabilities.canUse(SoundAction.EDIT)
    val canReportSound = s.source != ContentSource.LOCAL && s.source != ContentSource.BUNDLED
    var canDeleteUpload by remember(s.stableKey()) { mutableStateOf(false) }
    LaunchedEffect(s.stableKey()) {
        canDeleteUpload = viewModel.canDeleteCommunitySound(s)
    }
    val canBlockCreator = viewModel.canBlockCommunitySound(s) && !canDeleteUpload
    val policyMessages = remember(licenseCapabilities) { soundPolicyMessages(licenseCapabilities) }
    val playPreviewLabel = stringResource(R.string.a11y_play_preview)
    val pausePreviewLabel = stringResource(R.string.a11y_pause_preview)
    val addFavoriteLabel = stringResource(R.string.a11y_add_favorite)
    val removeFavoriteLabel = stringResource(R.string.a11y_remove_favorite)
    val playingState = stringResource(R.string.a11y_preview_playing)
    val readyState = stringResource(R.string.a11y_ready)
    val writeSettingsTitle = stringResource(R.string.write_settings_title)
    val writeSettingsBody = stringResource(R.string.write_settings_body)
    val openSettingsLabel = stringResource(R.string.write_settings_open)
    val writeSettingsUnavailable = stringResource(R.string.write_settings_unavailable)
    val errorMessage = state.error?.let { stringResource(R.string.common_error_format, it) }
    val applySoundTitle = stringResource(R.string.sounds_quick_apply_title)
    val editSoundTitle = stringResource(R.string.sound_detail_edit_sound_title)
    val saveSoundTitle = stringResource(R.string.sounds_quick_apply_save_title)
    val shareSoundTitle = stringResource(R.string.sound_detail_share_sound_title)
    val shareSoundChooserTitle = stringResource(R.string.sound_detail_share_sound_chooser)
    val shareFailedMessage = stringResource(R.string.common_share_failed)
    var showReportDialog by remember(s.stableKey()) { mutableStateOf(false) }
    var showBlockCreatorDialog by remember(s.stableKey()) { mutableStateOf(false) }
    var showDeleteUploadDialog by remember(s.stableKey()) { mutableStateOf(false) }

    val currentSoundKey = s.stableKey()
    val similarSounds = remember(currentSoundKey) { mutableStateOf<List<Sound>>(emptyList()) }
    val similarLoading = remember(currentSoundKey) { mutableStateOf(false) }

    DisposableEffect(currentSoundKey) {
        onDispose { viewModel.stopIfPlaying(s) }
    }
    LaunchedEffect(currentSoundKey, autoPreview) {
        if (autoPreview && state.playingId != s.stableKey()) viewModel.togglePlayback(s)
    }

    val snackbarHostState = remember { SnackbarHostState() }
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
        if (!context.tryStartActivity(viewModel.requestWriteSettings(), "write settings")) {
            scope.launch { snackbarHostState.showSnackbar(writeSettingsUnavailable) }
        }
    }
    LaunchedEffect(state.applySuccess) { state.applySuccess?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() } }
    LaunchedEffect(errorMessage) { errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearError() } }

    pendingSoundAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingSoundAction = null },
            title = { Text(pending.title) },
            text = { Text(pending.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSoundAction = null
                        pending.onConfirm()
                    },
                ) {
                    Text(stringResource(R.string.common_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSoundAction = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showReportDialog) {
        CommunityReportDialog(
            title = stringResource(R.string.sound_detail_report_title),
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, note -> viewModel.reportSound(s, reason, note) },
        )
    }

    if (showDeleteUploadDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteUploadDialog = false },
            title = { Text(stringResource(R.string.sound_detail_delete_upload_title)) },
            text = { Text(communityOwnerDeleteConfirmationCopy(CommunityUploadPolicyKind.SOUND)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteUploadDialog = false
                        viewModel.deleteCommunitySound(s, onDeleted = onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUploadDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showBlockCreatorDialog) {
        AlertDialog(
            onDismissRequest = { showBlockCreatorDialog = false },
            title = { Text(stringResource(R.string.sound_detail_block_creator_title)) },
            text = { Text(communityBlockConfirmationCopy(CommunityUploadPolicyKind.SOUND)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlockCreatorDialog = false
                        viewModel.blockCommunitySound(s, onBlocked = onBack)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.common_block))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockCreatorDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    fun runSoundAction(action: SoundAction, title: String, onConfirm: () -> Unit) {
        val capability = licenseCapabilities.capability(action)
        when (capability.decision) {
            SoundActionDecision.ALLOWED -> onConfirm()
            SoundActionDecision.CONFIRMATION_REQUIRED -> {
                pendingSoundAction = PendingSoundAction(title, capability.reason, onConfirm)
            }
            SoundActionDecision.DISABLED -> Unit
        }
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleFavorite(s) },
                        modifier = Modifier.semantics {
                            stateDescription = if (isFavorite) removeFavoriteLabel else addFavoriteLabel
                            onClick(label = if (isFavorite) removeFavoriteLabel else addFavoriteLabel, action = null)
                        },
                    ) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) removeFavoriteLabel else addFavoriteLabel,
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val containerWidthDp = with(LocalDensity.current) { constraints.maxWidth.toDp() }
            val useStackedActions = shouldStackSoundActions(
                availableWidthDp = (containerWidthDp - (SOUND_DETAIL_HORIZONTAL_PADDING_DP * 2).dp).value.toInt(),
                itemCount = 4,
                minItemWidthDp = SOUND_SECONDARY_ACTION_MIN_WIDTH_DP,
                fontScale = fontScale,
            )
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            // Waveform with integrated play button
            Box(
                modifier = Modifier.fillMaxWidth().height(156.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (s.duration > 0) {
                    DetailWaveform(
                        duration = s.duration, isPlaying = isPlaying,
                        progress = if (isPlaying) playbackProgress else 0f,
                        onSeek = { frac -> if (!isPlaying) viewModel.togglePlayback(s); viewModel.seekTo(frac) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                }
                // Play overlay
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(52.dp)
                        .semantics {
                            contentDescription = if (isPlaying) pausePreviewLabel else playPreviewLabel
                            stateDescription = if (isPlaying) playingState else readyState
                            onClick(label = if (isPlaying) pausePreviewLabel else playPreviewLabel, action = null)
                        },
                    onClick = { viewModel.togglePlayback(s) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // Sound name
            Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)

            // Metadata row
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(sourceLabel, style = MaterialTheme.typography.labelMedium, color = sourceColor)
                Text(formatSoundDetailDuration(s.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showUploader) {
                    Text(stringResource(R.string.sound_detail_by_creator, s.uploaderName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val detailCodecBadge = remember(s) { formatSoundCodecBadge(s) }
                if (detailCodecBadge != null) {
                    Text(detailCodecBadge, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val fileSizeLabel = remember(s.fileSize) {
                    com.freevibe.ui.screens.wallpapers.formatFileSizeLabel(s.fileSize)
                }
                if (fileSizeLabel != null) {
                    Text(fileSizeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.sampleRate > 0) {
                    Text(stringResource(R.string.sound_detail_sample_rate_khz, s.sampleRate / 1000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (s.license.isNotEmpty()) {
                    Text(s.license, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (detailBadges.isNotEmpty()) {
                Text(
                    detailBadges.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (useStackedActions) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApplyButton(stringResource(R.string.editor_sound_apply_ringtone), Icons.Default.Call, !state.isApplying && canWriteSettings && canApplySound, state.isApplying, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { viewModel.applySound(s, ContentType.RINGTONE, confirmed = true) }
                    }
                    ApplyButton(stringResource(R.string.editor_sound_apply_notification), Icons.Default.Notifications, !state.isApplying && canWriteSettings && canApplySound, state.isApplying, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { viewModel.applySound(s, ContentType.NOTIFICATION, confirmed = true) }
                    }
                    ApplyButton(stringResource(R.string.editor_sound_apply_alarm), Icons.Default.Alarm, !state.isApplying && canWriteSettings && canApplySound, state.isApplying, Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { viewModel.applySound(s, ContentType.ALARM, confirmed = true) }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApplyButton(stringResource(R.string.editor_sound_apply_ringtone), Icons.Default.Call, !state.isApplying && canWriteSettings && canApplySound, state.isApplying, Modifier.weight(1f)) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { viewModel.applySound(s, ContentType.RINGTONE, confirmed = true) }
                    }
                    ApplyButton(stringResource(R.string.editor_sound_apply_notification), Icons.Default.Notifications, !state.isApplying && canWriteSettings && canApplySound, state.isApplying, Modifier.weight(1f)) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { viewModel.applySound(s, ContentType.NOTIFICATION, confirmed = true) }
                    }
                    ApplyButton(stringResource(R.string.editor_sound_apply_alarm), Icons.Default.Alarm, !state.isApplying && canWriteSettings && canApplySound, state.isApplying, Modifier.weight(1f)) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { viewModel.applySound(s, ContentType.ALARM, confirmed = true) }
                    }
                }
            }

            if (sourceUnavailable) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.downloads_source_unavailable), style = MaterialTheme.typography.labelLarge)
                            Text(
                                s.sourceAvailabilityReason.ifBlank { stringResource(R.string.sound_detail_source_unavailable_body) },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (policyMessages.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.sound_detail_source_policy), style = MaterialTheme.typography.labelLarge)
                        // Source policy is a rights statement; truncating it hides the
                        // condition the user is agreeing to, so it always wraps in full.
                        Text(
                            policyMessages.joinToString(" "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Tags
            if (s.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.tags.take(5).forEach { tag ->
                        TextButton(onClick = { onSearchTag(tag) }) {
                            Text(stringResource(R.string.sound_detail_tag, tag), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Permission warning
            if (!canWriteSettings) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(writeSettingsTitle, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = ::openWriteSettings, enabled = canOpenWriteSettings) {
                            Text(openSettingsLabel)
                        }
                    }
                    // Permission copy explains why apply is blocked; it must stay readable
                    // in full rather than ellipsizing mid-sentence.
                    Text(
                        writeSettingsBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (useStackedActions) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 2,
                ) {
                    SecondarySoundAction(stringResource(R.string.sound_detail_trim), Icons.Default.ContentCut, Modifier.weight(1f).widthIn(min = 136.dp), enabled = canEditSound) {
                        runSoundAction(SoundAction.EDIT, editSoundTitle) { onEdit(s) }
                    }
                    SecondarySoundAction(stringResource(R.string.sound_detail_contact), Icons.Default.Contacts, Modifier.weight(1f).widthIn(min = 136.dp), enabled = canApplySound) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { onContactPicker(s) }
                    }
                    SecondarySoundAction(stringResource(R.string.sound_detail_save), Icons.Default.Download, Modifier.weight(1f).widthIn(min = 136.dp), enabled = canDownloadSound) {
                        runSoundAction(SoundAction.DOWNLOAD, saveSoundTitle) { viewModel.downloadSound(s, confirmed = true) }
                    }
                    SecondarySoundAction(stringResource(R.string.common_share), Icons.Default.Share, Modifier.weight(1f).widthIn(min = 136.dp), enabled = canShareSound) {
                        runSoundAction(SoundAction.SHARE, shareSoundTitle) {
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareBody); putExtra(Intent.EXTRA_SUBJECT, s.name) }
                            if (!context.tryStartActivity(Intent.createChooser(intent, shareSoundChooserTitle), "sound share")) {
                                scope.launch { snackbarHostState.showSnackbar(shareFailedMessage) }
                            }
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondarySoundAction(stringResource(R.string.sound_detail_trim), Icons.Default.ContentCut, Modifier.weight(1f), enabled = canEditSound) {
                        runSoundAction(SoundAction.EDIT, editSoundTitle) { onEdit(s) }
                    }
                    SecondarySoundAction(stringResource(R.string.sound_detail_contact), Icons.Default.Contacts, Modifier.weight(1f), enabled = canApplySound) {
                        runSoundAction(SoundAction.APPLY, applySoundTitle) { onContactPicker(s) }
                    }
                    SecondarySoundAction(stringResource(R.string.sound_detail_save), Icons.Default.Download, Modifier.weight(1f), enabled = canDownloadSound) {
                        runSoundAction(SoundAction.DOWNLOAD, saveSoundTitle) { viewModel.downloadSound(s, confirmed = true) }
                    }
                    SecondarySoundAction(stringResource(R.string.common_share), Icons.Default.Share, Modifier.weight(1f), enabled = canShareSound) {
                        runSoundAction(SoundAction.SHARE, shareSoundTitle) {
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareBody); putExtra(Intent.EXTRA_SUBJECT, s.name) }
                            if (!context.tryStartActivity(Intent.createChooser(intent, shareSoundChooserTitle), "sound share")) {
                                scope.launch { snackbarHostState.showSnackbar(shareFailedMessage) }
                            }
                        }
                    }
                }
            }
            if (canReportSound) {
                OutlinedButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sound_detail_report_content))
                }
            }
            if (canBlockCreator) {
                OutlinedButton(
                    onClick = { showBlockCreatorDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sound_detail_block_creator))
                }
            }
            if (canDeleteUpload) {
                OutlinedButton(
                    onClick = { showDeleteUploadDialog = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.sound_detail_delete_upload))
                }
            }

            // More Like This
            Spacer(Modifier.height(4.dp))
            if (!sourceUnavailable) {
                SimilarSoundsSection(
                    sound = s,
                    similarSounds = similarSounds,
                    isLoading = similarLoading,
                    currentPlayingId = state.playingId,
                    viewModel = viewModel,
                ) { similar ->
                    viewModel.selectSound(similar)
                    onOpenSound(similar)
                }
            }

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
        }
    }
}

private data class PendingSoundAction(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit,
)

private fun soundPolicyMessages(capabilities: SoundLicenseCapabilities): List<String> =
    SoundAction.entries
        .map { capabilities.capability(it) }
        .filter { it.decision == SoundActionDecision.DISABLED && it.reason.isNotBlank() }
        .map { it.reason }
        .distinct()

private fun buildSoundShareBody(sound: Sound, capabilities: SoundLicenseCapabilities): String {
    val sourceUrl = sound.sourcePageUrl.ifBlank { sound.downloadUrl }
    if (sourceUrl.isBlank()) return ""
    return buildList {
        add(sound.name)
        if (sound.uploaderName.isNotBlank() && sound.uploaderName != "Unknown") {
            add("Creator: ${sound.uploaderName}")
        }
        add("Source: ${sound.source.name}")
        add("License: ${capabilities.normalizedLicense}")
        add("Link: $sourceUrl")
    }.joinToString("\n")
}

@Composable
internal fun ApplyButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, isLoading: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp), enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SecondarySoundAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SimilarSoundsSection(
    sound: Sound,
    similarSounds: MutableState<List<Sound>>,
    isLoading: MutableState<Boolean>,
    currentPlayingId: String?,
    viewModel: SoundsViewModel,
    onSoundClick: (Sound) -> Unit,
) {
    var loaded by remember(sound.stableKey()) { mutableStateOf(false) }
    var loadFailed by remember(sound.stableKey()) { mutableStateOf(false) }
    // `loaded` is a key so the Retry button (loaded = false) restarts the effect.
    LaunchedEffect(sound.stableKey(), loaded) {
        if (!loaded && !isLoading.value) {
            isLoading.value = true; similarSounds.value = emptyList(); loadFailed = false
            try {
                similarSounds.value = viewModel.loadSimilar(sound)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // A network failure is not "no close matches" — show honest copy.
                loadFailed = true
            }
            isLoading.value = false; loaded = true
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.sound_detail_more_like_this), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (isLoading.value) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(3) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.width(184.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBox(Modifier.size(48.dp), shape = RoundedCornerShape(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                ShimmerBox(Modifier.width(78.dp).height(12.dp), shape = RoundedCornerShape(5.dp))
                                ShimmerBox(Modifier.width(44.dp).height(10.dp), shape = RoundedCornerShape(5.dp))
                            }
                        }
                    }
                }
            }
        } else if (similarSounds.value.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(similarSounds.value, key = { it.stableKey() }) { similar ->
                    val similarPlaying = currentPlayingId == similar.stableKey()
                    val similarActionLabel = if (similarPlaying) {
                        stringResource(R.string.a11y_pause_preview)
                    } else {
                        stringResource(R.string.a11y_play_preview)
                    }
                    val similarState = if (similarPlaying) {
                        stringResource(R.string.a11y_preview_playing)
                    } else {
                        stringResource(R.string.a11y_ready)
                    }
                    Surface(
                        onClick = { onSoundClick(similar) }, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.width(184.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { viewModel.togglePlayback(similar) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (similarPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                                    .semantics {
                                        stateDescription = similarState
                                        onClick(label = similarActionLabel, action = null)
                                    },
                            ) {
                                Icon(
                                    if (similarPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (similarPlaying) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(similar.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatSoundDetailDuration(similar.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else if (loaded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(
                            if (loadFailed) R.string.sound_detail_similar_load_failed
                            else R.string.sound_detail_no_close_matches,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loadFailed) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { loaded = false }) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailWaveform(duration: Double, isPlaying: Boolean, modifier: Modifier = Modifier, progress: Float = 0f, onSeek: ((Float) -> Unit)? = null) {
    val barColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val activeColor = MaterialTheme.colorScheme.primary
    val barCount = 60
    val heights = remember(duration) {
        val seed = (duration * 1000).toInt()
        List(barCount) { i -> (0.15f + 0.85f * ((kotlin.math.sin((seed + i * 37) % 360 * 0.0174533) + 1f) / 2f).toFloat()) }
    }
    val waveformDescription = stringResource(
        if (onSeek != null) R.string.a11y_seekable_waveform else R.string.a11y_playback_waveform,
    )
    val waveformState = if (isPlaying) {
        stringResource(R.string.a11y_playing_percent, (progress * 100).toInt())
    } else {
        stringResource(R.string.a11y_stopped)
    }
    Canvas(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics {
                contentDescription = waveformDescription
                stateDescription = waveformState
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                if (onSeek != null) {
                    setProgress(label = waveformDescription) { target ->
                        onSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .then(
                if (onSeek != null) Modifier.pointerInput(Unit) { detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) } } else Modifier,
            ),
    ) {
        val barWidth = size.width / barCount
        heights.forEachIndexed { i, height ->
            val x = i * barWidth + barWidth / 2; val barH = size.height * height * 0.85f
            drawLine(
                color = if (isPlaying && (i.toFloat() / barCount) < progress) activeColor else barColor,
                start = Offset(x, size.height / 2 - barH / 2), end = Offset(x, size.height / 2 + barH / 2),
                strokeWidth = (barWidth - 1.5f).coerceAtLeast(1f), cap = StrokeCap.Round,
            )
        }
        if (isPlaying && progress > 0f) {
            drawLine(activeColor, Offset(size.width * progress, 0f), Offset(size.width * progress, size.height), strokeWidth = 2f)
        }
    }
}
