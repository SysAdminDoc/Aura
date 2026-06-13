package com.freevibe.ui.screens.downloads

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.isSourceUnavailable
import com.freevibe.service.DownloadProgress
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("All", "Wallpapers", "Sounds")
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val displayList = remember(allDownloads, selectedTab) {
        when (selectedTab) {
            1 -> allDownloads.filter { it.type == "WALLPAPER" }
            2 -> allDownloads.filter { it.type == "SOUND" }
            else -> allDownloads
        }
    }
    var brokenIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(displayList) {
        brokenIds = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            displayList.filter { it.localPath.isNotBlank() }.mapNotNullTo(mutableSetOf()) { item ->
                val parsed = Uri.parse(item.localPath)
                if (parsed.scheme == "file") {
                    val path = parsed.path
                    if (path != null && !java.io.File(path).exists()) item.id else null
                } else null
            }
        }
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
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
                        ActiveDownloadCard(dl) { viewModel.dismissActive(id) }
                    }
                }
            }

            if (displayList.isEmpty() && activeDownloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.Download,
                        title = "No downloads yet",
                        description = "Saved wallpapers, sounds, and active transfers will appear here with file health and quick-open status.",
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(displayList, key = { it.id }, contentType = { "download_card" }) { download ->
                        DownloadHistoryCard(
                            download = download,
                            broken = download.localPath.isBlank() || download.id in brokenIds,
                            sourceUnavailable = download.isSourceUnavailable(),
                            onOpen = {
                                try {
                                    val path = download.localPath
                                    if (path.isBlank()) {
                                        scope.launch { snackbarHostState.showSnackbar("File path is missing") }
                                        return@DownloadHistoryCard
                                    }
                                    val uri = Uri.parse(path)
                                    if (uri.scheme == "file") {
                                        val file = java.io.File(uri.path ?: "")
                                        if (!file.exists()) {
                                            scope.launch { snackbarHostState.showSnackbar("File no longer exists") }
                                            return@DownloadHistoryCard
                                        }
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, if (download.type == "WALLPAPER") "image/*" else "audio/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Cannot open file") }
                                }
                            },
                            onDelete = { viewModel.deleteDownload(download.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadCard(dl: DownloadProgress, onDismiss: () -> Unit) {
    val statusLabel = downloadProgressStatusLabel(dl)
    val dismissLabel = "Dismiss ${dl.fileName}"
    Surface(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "${dl.fileName}. $statusLabel"
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
                if (dl.isComplete || dl.error != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { onClick(label = dismissLabel, action = null) },
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
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
private fun DownloadHistoryCard(
    download: DownloadEntity,
    broken: Boolean = false,
    sourceUnavailable: Boolean = false,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val dateLabel = remember(download.downloadedAt) { dateFormat.format(Date(download.downloadedAt)) }
    val healthLabel = downloadHealthLabel(download, broken, sourceUnavailable)
    val itemSummary = downloadHistorySummary(download, broken, sourceUnavailable, dateLabel)
    val openLabel = downloadOpenActionLabel(download, broken)
    val deleteLabel = "Delete ${download.name.ifEmpty { download.id }}"

    Surface(
        onClick = onOpen,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = itemSummary
            stateDescription = healthLabel
            onClick(label = openLabel, action = null)
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                if (broken || sourceUnavailable) Icons.Default.Warning
                else if (download.type == "WALLPAPER") Icons.Default.Image else Icons.Default.MusicNote,
                null, Modifier.size(24.dp),
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
                    if (broken) {
                        Text(stringResource(R.string.downloads_file_missing), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    } else if (sourceUnavailable) {
                        Text(stringResource(R.string.downloads_source_unavailable), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    } else {
                        Text(
                            if (download.type == "WALLPAPER") "Wallpaper" else "Sound",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                )
            }
        }
    }
}

internal fun downloadHealthLabel(
    download: DownloadEntity,
    broken: Boolean,
    sourceUnavailable: Boolean,
): String = when {
    broken -> "File missing"
    sourceUnavailable -> "Source unavailable"
    download.type == "WALLPAPER" -> "Wallpaper"
    else -> "Sound"
}

internal fun downloadHistorySummary(
    download: DownloadEntity,
    broken: Boolean,
    sourceUnavailable: Boolean,
    downloadedAtLabel: String,
): String {
    val name = download.name.ifEmpty { download.id }
    return "$name. ${downloadHealthLabel(download, broken, sourceUnavailable)}. Downloaded $downloadedAtLabel."
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
