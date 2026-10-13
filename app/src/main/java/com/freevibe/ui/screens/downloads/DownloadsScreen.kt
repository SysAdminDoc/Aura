package com.freevibe.ui.screens.downloads

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.MediaTechnicalMetadata
import com.freevibe.data.model.RotationExclusionIndex
import com.freevibe.data.model.isSourceUnavailable
import com.freevibe.data.model.needsLocalMediaRelink
import com.freevibe.data.model.optimizedTechnicalMetadata
import com.freevibe.data.model.originalTechnicalMetadata
import com.freevibe.data.model.rotationIdentity
import com.freevibe.service.DownloadProgress
import com.freevibe.service.LocalMediaRelinkOutcome
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.rotation.RotationExclusionsViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    focusDownloadId: String? = null,
    viewModel: DownloadsViewModel = hiltViewModel(),
    rotationExclusionsViewModel: RotationExclusionsViewModel = hiltViewModel(),
) {
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val rotationExclusions by rotationExclusionsViewModel.exclusions.collectAsStateWithLifecycle()
    val rotationExclusionIndex = remember(rotationExclusions) { RotationExclusionIndex(rotationExclusions) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.downloads_tab_all),
        stringResource(R.string.nav_wallpapers),
        stringResource(R.string.nav_videos),
        stringResource(R.string.nav_sounds),
    )
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    val missingPathMessage = stringResource(R.string.downloads_file_path_missing)
    val missingFileMessage = stringResource(R.string.downloads_file_no_longer_exists)
    val cannotOpenMessage = stringResource(R.string.downloads_cannot_open_file)

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val displayList = remember(allDownloads, selectedTab) {
        when (selectedTab) {
            1 -> allDownloads.filter { it.type == "WALLPAPER" }
            2 -> allDownloads.filter { it.type == "VIDEO" }
            3 -> allDownloads.filter { it.type == "SOUND" }
            else -> allDownloads
        }
    }
    var brokenIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(displayList) {
        brokenIds = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            displayList.filter { it.localPath.isNotBlank() }.mapNotNullTo(mutableSetOf()) { item ->
                val file = downloadLocalFile(item.localPath)
                if (file != null && !file.exists()) item.id else null
            }
        }
    }
    // A row opened from Universal Search: scroll to it once, keep it highlighted while here.
    val listState = rememberLazyListState()
    // Keyed on the target, so a second search result opened into this same entry is handled too.
    var focusHandled by rememberSaveable(focusDownloadId) { mutableStateOf(false) }
    var focusedId by rememberSaveable(focusDownloadId) { mutableStateOf<String?>(null) }
    var focusScrolled by rememberSaveable(focusDownloadId) { mutableStateOf(false) }
    LaunchedEffect(focusDownloadId) {
        if (focusHandled || focusDownloadId == null) return@LaunchedEffect
        focusHandled = true
        if (viewModel.downloadExists(focusDownloadId)) {
            selectedTab = 0
            focusedId = focusDownloadId
        } else {
            snackbarHostState.showSnackbar(resources.getString(R.string.downloads_focus_missing))
        }
    }
    LaunchedEffect(focusedId, displayList) {
        if (focusScrolled) return@LaunchedEffect
        val index = downloadFocusIndex(displayList, focusedId) ?: return@LaunchedEffect
        focusScrolled = true
        listState.scrollToItem(index)
    }
    var pendingRelink by remember { mutableStateOf<DownloadEntity?>(null) }
    suspend fun finishRelink(download: DownloadEntity, uri: Uri, acceptMismatch: Boolean = false) {
        when (val outcome = viewModel.relinkDownload(download.id, uri, acceptMismatch)) {
            is LocalMediaRelinkOutcome.Relinked -> {
                brokenIds = brokenIds - download.id
                snackbarHostState.showSnackbar(resources.getString(R.string.media_relink_success))
            }
            is LocalMediaRelinkOutcome.Rejected -> snackbarHostState.showSnackbar(outcome.message)
            is LocalMediaRelinkOutcome.ReviewRequired -> {
                val result = snackbarHostState.showSnackbar(
                    message = resources.getString(
                        R.string.media_relink_mismatch,
                        outcome.differences.joinToString(", "),
                    ),
                    actionLabel = resources.getString(R.string.media_relink_use_anyway),
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) finishRelink(download, uri, acceptMismatch = true)
            }
        }
    }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val download = pendingRelink
        pendingRelink = null
        if (uri != null && download != null) scope.launch { finishRelink(download, uri) }
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
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
            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }

            // Active downloads
            if (activeDownloads.isNotEmpty()) {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.downloads_active), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    activeDownloads.forEach { (id, dl) ->
                        ActiveDownloadCard(
                            dl = dl,
                            onDismiss = { viewModel.dismissActive(id) },
                            onRetry = { viewModel.retryActive(id) },
                        )
                    }
                }
            }

            if (displayList.isEmpty() && activeDownloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.Download,
                        title = stringResource(R.string.downloads_empty_title),
                        description = stringResource(R.string.downloads_empty_body),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(displayList, key = { it.id }, contentType = { "download_card" }) { download ->
                        val rotationIdentity = download.rotationIdentity()
                        val rotationExclusion = rotationExclusionIndex.find(rotationIdentity)
                        val needsRelink = download.localPath.isBlank() || download.id in brokenIds ||
                            download.needsLocalMediaRelink()
                        DownloadHistoryCard(
                            download = download,
                            broken = needsRelink,
                            sourceUnavailable = download.isSourceUnavailable(),
                            highlighted = download.id == focusedId,
                            onOpen = {
                                try {
                                    val path = download.localPath
                                    if (path.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar(missingPathMessage) }
                                        return@DownloadHistoryCard
                                    }
                                    val localFile = downloadLocalFile(path)
                                    if (localFile != null && !localFile.exists()) {
                                        scope.launch { snackbarHostState.showSnackbar(missingFileMessage) }
                                        return@DownloadHistoryCard
                                    }
                                    val uri = downloadOpenUri(context, path, localFile)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, downloadOpenMimeType(download))
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar(cannotOpenMessage) }
                                }
                            },
                            onDelete = {
                                // Deletion stages the file rather than destroying it, so
                                // Undo can restore both the row and its bytes.
                                viewModel.deleteDownload(download.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = resources.getString(
                                            R.string.downloads_deleted,
                                            download.name.ifBlank { download.id },
                                        ),
                                        actionLabel = resources.getString(R.string.common_undo),
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restoreDownload(download.id)
                                    }
                                }
                            },
                            onDeleteOptimized = if (download.optimizedPath.isNotBlank()) ({
                                scope.launch {
                                    val deleted = try {
                                        viewModel.deleteOptimizedCopy(download.id)
                                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        false
                                    }
                                    snackbarHostState.showSnackbar(
                                        resources.getString(
                                            if (deleted) R.string.downloads_optimized_deleted
                                            else R.string.downloads_optimized_delete_failed,
                                        ),
                                    )
                                }
                            }) else null,
                            onRelink = if (needsRelink) ({
                                pendingRelink = download
                                relinkLauncher.launch(arrayOf(downloadOpenMimeType(download)))
                            }) else null,
                            rotationExcluded = rotationExclusion != null,
                            onToggleRotationExclusion = if (download.type == "WALLPAPER") ({
                                scope.launch {
                                    if (rotationExclusion != null) {
                                        rotationExclusionsViewModel.restoreNow(rotationExclusion.stableId)
                                        snackbarHostState.showSnackbar(
                                            resources.getString(
                                                R.string.rotation_restored_message,
                                                download.name.ifBlank { download.id },
                                            ),
                                        )
                                    } else {
                                        val exclusion = rotationExclusionsViewModel.exclude(rotationIdentity)
                                        val result = snackbarHostState.showSnackbar(
                                            message = resources.getString(
                                                R.string.rotation_excluded_message,
                                                download.name.ifBlank { download.id },
                                            ),
                                            actionLabel = resources.getString(R.string.common_undo),
                                            duration = SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            rotationExclusionsViewModel.restoreNow(exclusion.stableId)
                                        }
                                    }
                                }
                            }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActiveDownloadCard(dl: DownloadProgress, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val statusLabel = downloadProgressStatusLabel(dl)
    val dismissLabel = stringResource(R.string.downloads_dismiss_file, dl.fileName)
    val retryLabel = stringResource(R.string.downloads_retry_file, dl.fileName)
    val summary = stringResource(R.string.downloads_active_summary, dl.fileName, statusLabel)
    Surface(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = summary
            stateDescription = statusLabel
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (dl.isComplete) Icons.Default.CheckCircle
                    else if (dl.error != null) Icons.Default.Error
                    else Icons.Default.Download,
                    null, Modifier.size(18.dp),
                    tint = when {
                        dl.isComplete -> MaterialTheme.colorScheme.secondary
                        dl.error != null -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.width(8.dp))
                Text(dl.fileName, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dl.error != null) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.semantics { onClick(label = retryLabel, action = null) },
                    ) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
                if (dl.isComplete || dl.error != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { onClick(label = dismissLabel, action = null) },
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = dismissLabel,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            dl.error?.let { reason ->
                Spacer(Modifier.height(4.dp))
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!dl.isComplete && dl.error == null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { dl.progress },
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(dl.progress, 0f..1f)
                        },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
internal fun DownloadHistoryCard(
    download: DownloadEntity,
    broken: Boolean = false,
    sourceUnavailable: Boolean = false,
    highlighted: Boolean = false,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDeleteOptimized: (() -> Unit)? = null,
    onRelink: (() -> Unit)? = null,
    rotationExcluded: Boolean = false,
    onToggleRotationExclusion: (() -> Unit)? = null,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val dateLabel = remember(download.downloadedAt) { dateFormat.format(Date(download.downloadedAt)) }
    val healthLabel = downloadHealthLabel(download, broken, sourceUnavailable)
    val originalLabel = stringResource(R.string.downloads_original)
    val optimizedLabel = stringResource(R.string.downloads_optimized_copy)
    val unavailableDetails = stringResource(R.string.downloads_details_unavailable)
    val originalDetails = formatMediaTechnicalMetadata(
        download.originalTechnicalMetadata(),
        unavailableDetails,
    )
    val optimizedDetails = formatMediaTechnicalMetadata(
        download.optimizedTechnicalMetadata(),
        unavailableDetails,
    )
    val itemSummary = downloadHistorySummary(
        download,
        broken,
        sourceUnavailable,
        dateLabel,
        buildList {
            add(originalLabel to originalDetails)
            if (download.optimizedPath.isNotBlank()) add(optimizedLabel to optimizedDetails)
        },
    )
    val openLabel = downloadOpenActionLabel(download, broken)
    val deleteLabel = stringResource(R.string.downloads_delete_file, download.name.ifEmpty { download.id })
    val deleteOptimizedLabel = stringResource(R.string.downloads_delete_optimized)
    val relinkLabel = stringResource(R.string.media_relink_action)

    Surface(
        onClick = onOpen,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = itemSummary
            stateDescription = healthLabel
            onClick(label = openLabel, action = null)
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        border = if (highlighted) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    downloadHistoryIcon(download, broken, sourceUnavailable),
                    null,
                    Modifier.size(24.dp),
                    tint = if (broken || sourceUnavailable) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        download.name.ifEmpty { download.id },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (broken) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when {
                            broken -> Text(
                                stringResource(
                                    when (download.localMediaStatus) {
                                        LocalMediaStatus.PERMISSION_REVOKED -> R.string.media_status_permission_revoked
                                        LocalMediaStatus.CORRUPT -> R.string.media_status_corrupt
                                        else -> R.string.media_status_missing
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            )
                            sourceUnavailable -> Text(stringResource(R.string.downloads_source_unavailable), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                            else -> Text(
                                stringResource(downloadTypeLabel(download.type)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (onToggleRotationExclusion != null) {
                    val exclusionLabel = stringResource(
                        if (rotationExcluded) R.string.rotation_restore_action else R.string.rotation_exclude_action,
                    )
                    IconButton(
                        onClick = onToggleRotationExclusion,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { onClick(label = exclusionLabel, action = null) },
                    ) {
                        Icon(
                            if (rotationExcluded) Icons.Default.Restore else Icons.Default.PlaylistRemove,
                            contentDescription = exclusionLabel,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { onClick(label = deleteLabel, action = null) },
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = deleteLabel,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
            }
            if (broken && onRelink != null) {
                Text(
                    text = download.localMediaReason?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.media_relink_missing_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = onRelink) {
                    Icon(Icons.Default.FindReplace, contentDescription = null)
                    Text(relinkLabel)
                }
            }
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f),
            )
            MediaCopyDetails(
                label = originalLabel,
                details = originalDetails,
            )
            if (download.optimizedPath.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                MediaCopyDetails(
                    label = optimizedLabel,
                    details = listOf(
                        download.optimizationReason,
                        optimizedDetails,
                    ).filter(String::isNotBlank).joinToString(" · "),
                    action = {
                        IconButton(
                            onClick = { onDeleteOptimized?.invoke() },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics { onClick(label = deleteOptimizedLabel, action = null) },
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = deleteOptimizedLabel,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MediaCopyDetails(
    label: String,
    details: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(details, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}

private fun downloadHistoryIcon(
    download: DownloadEntity,
    broken: Boolean,
    sourceUnavailable: Boolean,
) = when {
    broken || sourceUnavailable -> Icons.Default.Warning
    download.type == "WALLPAPER" -> Icons.Default.Image
    download.type == "VIDEO" -> Icons.Default.VideoLibrary
    else -> Icons.Default.MusicNote
}

private fun downloadTypeLabel(type: String): Int = when (type) {
    "WALLPAPER" -> R.string.downloads_type_wallpaper
    "VIDEO" -> R.string.downloads_type_video
    else -> R.string.downloads_type_sound
}

internal fun downloadHealthLabel(
    download: DownloadEntity,
    broken: Boolean,
    sourceUnavailable: Boolean,
): String = when {
    download.localMediaStatus == LocalMediaStatus.PERMISSION_REVOKED -> "Permission revoked"
    download.localMediaStatus == LocalMediaStatus.CORRUPT -> "File corrupt"
    download.localMediaStatus == LocalMediaStatus.MISSING -> "File missing"
    broken -> "File missing"
    sourceUnavailable -> "Source unavailable"
    download.type == "WALLPAPER" -> "Wallpaper"
    download.type == "VIDEO" -> "Video"
    else -> "Sound"
}

internal fun downloadFocusIndex(downloads: List<DownloadEntity>, focusId: String?): Int? =
    focusId?.let { id -> downloads.indexOfFirst { it.id == id }.takeIf { it >= 0 } }

internal fun downloadOpenMimeType(download: DownloadEntity): String = when (download.type) {
    "WALLPAPER" -> "image/*"
    "VIDEO" -> "video/*"
    else -> "audio/*"
}

internal fun downloadLocalFile(locator: String): java.io.File? {
    val normalized = locator.trim()
    if (normalized.startsWith('/')) return java.io.File(normalized)
    if (!normalized.startsWith("file:", ignoreCase = true)) return null
    val path = runCatching { java.net.URI(normalized).path }.getOrNull()
    return path?.takeIf(String::isNotBlank)?.let { java.io.File(it) }
}

private fun downloadOpenUri(
    context: android.content.Context,
    locator: String,
    localFile: java.io.File?,
): Uri {
    if (localFile != null) {
        val originalRoot = runCatching {
            java.io.File(context.filesDir, "media_originals").canonicalFile
        }.getOrNull()
        val canonicalFile = runCatching { localFile.canonicalFile }.getOrNull()
        if (
            originalRoot != null &&
            canonicalFile != null &&
            canonicalFile.path.startsWith(originalRoot.path + java.io.File.separator)
        ) {
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                canonicalFile,
            )
        }
    }
    return Uri.parse(locator)
}

internal fun formatMediaTechnicalMetadata(
    metadata: MediaTechnicalMetadata,
    unavailableLabel: String = "Details unavailable",
): String {
    val facts = buildList {
        if (metadata.width > 0 && metadata.height > 0) add("${metadata.width}×${metadata.height}")
        metadata.codec.trim().takeIf(String::isNotBlank)?.let(::add)
        if (metadata.durationMs > 0) {
            val totalSeconds = metadata.durationMs / 1_000L
            add("${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}")
        }
        if (metadata.sizeBytes > 0) add(formatMediaSize(metadata.sizeBytes))
        if (metadata.isHdr) add("HDR")
    }
    return facts.joinToString(" · ").ifBlank { unavailableLabel }
}

private fun formatMediaSize(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.coerceAtLeast(0L).toDouble()
    var unit = 0
    while (value >= 1_024.0 && unit < units.lastIndex) {
        value /= 1_024.0
        unit += 1
    }
    val formatted = if (unit == 0 || value >= 10.0) {
        value.toLong().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
    }
    return "$formatted ${units[unit]}"
}

internal fun downloadHistorySummary(
    download: DownloadEntity,
    broken: Boolean,
    sourceUnavailable: Boolean,
    downloadedAtLabel: String,
    copyDetails: List<Pair<String, String>> = emptyList(),
): String {
    val name = download.name.ifEmpty { download.id }
    return buildString {
        append("$name. ${downloadHealthLabel(download, broken, sourceUnavailable)}. Downloaded $downloadedAtLabel.")
        copyDetails.forEach { (label, details) -> append(" $label: $details.") }
    }
}

internal fun downloadOpenActionLabel(download: DownloadEntity, broken: Boolean): String =
    if (broken) {
        "Review missing file"
    } else {
        "Open ${download.name.ifEmpty { download.id }}"
    }

internal fun downloadProgressStatusLabel(download: DownloadProgress): String = when {
    download.isComplete -> "Download complete"
    download.error != null -> "Download failed: ${download.error}"
    download.totalBytes > 0 -> {
        val percent = (download.progress * 100).toInt().coerceIn(0, 100)
        "$percent percent downloaded"
    }
    else -> "Download in progress"
}
