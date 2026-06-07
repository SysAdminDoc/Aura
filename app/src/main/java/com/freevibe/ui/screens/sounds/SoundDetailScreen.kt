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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.freevibe.ui.policy.communityOwnerDeleteConfirmationCopy

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
    val topHits by viewModel.topHits.collectAsStateWithLifecycle()
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
        ?: topHits.firstOrNull { matchesSoundIdentity(it, soundId, targetSource, targetPreviewUrl, targetDownloadUrl) }
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
                        "Opening sound...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AuraStateCard(
                    icon = Icons.Default.MusicOff,
                    title = "Sound unavailable",
                    description = "This sound could not be restored from its source. Return to Sounds and choose another result.",
                    tone = MaterialTheme.colorScheme.tertiary,
                    primaryAction = AuraStateAction("Back to sounds", Icons.AutoMirrored.Filled.ArrowBack, onBack),
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
        !(s.source == ContentSource.BUNDLED && s.uploaderName == "Aura Picks")
    val detailBadges = remember(s, state.selectedTab) { soundBadges(s, state.selectedTab) }
    val (sourceLabel, sourceColor) = soundSourceTone(s.source)
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
    val canDeleteUpload by produceState(false, s.stableKey()) {
        value = viewModel.canDeleteCommunitySound(s)
    }
    val policyMessages = remember(licenseCapabilities) { soundPolicyMessages(licenseCapabilities) }
    var showReportDialog by remember(s.stableKey()) { mutableStateOf(false) }
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
    LaunchedEffect(state.applySuccess) { state.applySuccess?.let { snackbarHostState.showSnackbar(it); viewModel.clearSuccess() } }
    LaunchedEffect(state.error) { state.error?.let { snackbarHostState.showSnackbar("Error: $it"); viewModel.clearError() } }

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
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSoundAction = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showReportDialog) {
        CommunityReportDialog(
            title = "Report sound",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason, note -> viewModel.reportSound(s, reason, note) },
        )
    }

    if (showDeleteUploadDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteUploadDialog = false },
            title = { Text("Delete upload?") },
            text = { Text(communityOwnerDeleteConfirmationCopy(CommunityUploadPolicyKind.SOUND)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteUploadDialog = false
                        viewModel.deleteCommunitySound(s)
                        onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteUploadDialog = false }) {
                    Text("Cancel")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite(s) }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favorite",
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Waveform with integrated play button
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(12.dp)),
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
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    contentColor = Color.White,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                    modifier = Modifier.size(52.dp),
                    onClick = { viewModel.togglePlayback(s) },
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause preview" else "Play preview",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }

            // Sound name
            Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)

            // Metadata row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = sourceColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(sourceLabel, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = sourceColor)
                }
                Text(formatDuration(s.duration), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showUploader) {
                    Text("by ${s.uploaderName}", modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.license.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(s.license, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }

            if (detailBadges.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detailBadges.forEach { badge ->
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                badge,
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (sourceUnavailable) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Column(Modifier.weight(1f)) {
                            Text("Source unavailable", style = MaterialTheme.typography.labelLarge)
                            Text(
                                s.sourceAvailabilityReason.ifBlank { "This saved sound is no longer represented as live provider content." },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (policyMessages.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Default.Policy, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Column(Modifier.weight(1f)) {
                            Text("Source policy", style = MaterialTheme.typography.labelLarge)
                            Text(
                                policyMessages.joinToString(" "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // Tags
            if (s.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.tags.take(5).forEach { tag ->
                        Surface(
                            onClick = { onSearchTag(tag) },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("#$tag", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Permission warning
            if (!viewModel.canWriteSettings()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.18f)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(10.dp).size(20.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Allow ringtone changes", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Aura needs system settings access before it can set ringtones, notifications, and alarms.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { context.startActivity(viewModel.requestWriteSettings()) }) { Text("Open") }
                    }
                }
            }

            // 3 Apply buttons side-by-side
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApplyButton("Ringtone", Icons.Default.Call, !state.isApplying && viewModel.canWriteSettings() && canApplySound, state.isApplying, Modifier.weight(1f)) {
                    runSoundAction(SoundAction.APPLY, "Apply sound") { viewModel.applySound(s, ContentType.RINGTONE, confirmed = true) }
                }
                ApplyButton("Notification", Icons.Default.Notifications, !state.isApplying && viewModel.canWriteSettings() && canApplySound, state.isApplying, Modifier.weight(1f)) {
                    runSoundAction(SoundAction.APPLY, "Apply sound") { viewModel.applySound(s, ContentType.NOTIFICATION, confirmed = true) }
                }
                ApplyButton("Alarm", Icons.Default.Alarm, !state.isApplying && viewModel.canWriteSettings() && canApplySound, state.isApplying, Modifier.weight(1f)) {
                    runSoundAction(SoundAction.APPLY, "Apply sound") { viewModel.applySound(s, ContentType.ALARM, confirmed = true) }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondarySoundAction("Trim", Icons.Default.ContentCut, Modifier.weight(1f), enabled = canEditSound) {
                    runSoundAction(SoundAction.EDIT, "Edit sound") { onEdit(s) }
                }
                SecondarySoundAction("Contact", Icons.Default.Contacts, Modifier.weight(1f), enabled = canApplySound) {
                    runSoundAction(SoundAction.APPLY, "Apply sound") { onContactPicker(s) }
                }
                SecondarySoundAction("Save", Icons.Default.Download, Modifier.weight(1f), enabled = canDownloadSound) {
                    runSoundAction(SoundAction.DOWNLOAD, "Save sound") { viewModel.downloadSound(s, confirmed = true) }
                }
                SecondarySoundAction("Share", Icons.Default.Share, Modifier.weight(1f), enabled = canShareSound) {
                    runSoundAction(SoundAction.SHARE, "Share sound") {
                        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareBody); putExtra(Intent.EXTRA_SUBJECT, s.name) }
                        try { context.startActivity(Intent.createChooser(intent, "Share sound")) } catch (_: Exception) {}
                    }
                }
            }
            if (canReportSound) {
                OutlinedButton(
                    onClick = { showReportDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Report, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Report content")
                }
            }
            if (canDeleteUpload) {
                OutlinedButton(
                    onClick = { showDeleteUploadDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Delete upload")
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

            Spacer(Modifier.height(80.dp)) // bottom padding for nav bar
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
private fun ApplyButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, isLoading: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(48.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        if (isLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        else { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(text, style = MaterialTheme.typography.labelMedium) }
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
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 0.28f else 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
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
    LaunchedEffect(sound.stableKey()) {
        if (!loaded && !isLoading.value) {
            isLoading.value = true; similarSounds.value = emptyList()
            try { similarSounds.value = viewModel.loadSimilar(sound) } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
            isLoading.value = false; loaded = true
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Text("More Like This", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (isLoading.value) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(3) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(160.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShimmerBox(Modifier.size(34.dp), shape = RoundedCornerShape(10.dp))
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
                    Surface(
                        onClick = { onSoundClick(similar) }, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.width(160.dp),
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { viewModel.togglePlayback(similar) },
                                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (currentPlayingId == similar.stableKey()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer),
                            ) {
                                Icon(
                                    if (currentPlayingId == similar.stableKey()) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (currentPlayingId == similar.stableKey()) "Pause preview" else "Play preview",
                                    tint = if (currentPlayingId == similar.stableKey()) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Column(Modifier.weight(1f)) {
                                Text(similar.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formatDuration(similar.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else if (loaded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No close matches yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailWaveform(duration: Double, isPlaying: Boolean, modifier: Modifier = Modifier, progress: Float = 0f, onSeek: ((Float) -> Unit)? = null) {
    val barColor = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val activeColor = MaterialTheme.colorScheme.primary
    val barCount = 60
    val heights = remember(duration) {
        val seed = (duration * 1000).toInt()
        List(barCount) { i -> (0.15f + 0.85f * ((kotlin.math.sin((seed + i * 37) % 360 * 0.0174533) + 1f) / 2f).toFloat()) }
    }
    Canvas(
        modifier.background(MaterialTheme.colorScheme.surfaceContainer).then(
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

private fun formatDuration(seconds: Double): String {
    val total = seconds.toInt(); val m = total / 60; val s = total % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
