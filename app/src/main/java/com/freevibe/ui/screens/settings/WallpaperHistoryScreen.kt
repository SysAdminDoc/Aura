package com.freevibe.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freevibe.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.data.model.RotationExclusionIndex
import com.freevibe.data.model.rotationIdentity
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.rotation.RotationExclusionsViewModel
import com.freevibe.ui.theme.AuraShapes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperHistoryScreen(
    onBack: () -> Unit,
    onWallpaperClick: (WallpaperHistoryEntity) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    rotationExclusionsViewModel: RotationExclusionsViewModel = hiltViewModel(),
) {
    val history by viewModel.wallpaperHistory.collectAsStateWithLifecycle()
    val rotationExclusions by rotationExclusionsViewModel.exclusions.collectAsStateWithLifecycle()
    val rotationExclusionIndex = remember(rotationExclusions) { RotationExclusionIndex(rotationExclusions) }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, stringResource(R.string.history_clear_cd))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                AuraStateCard(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.history_empty_title),
                    description = stringResource(R.string.history_empty_body),
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(history, key = { it.historyId }) { entry ->
                    val identity = entry.rotationIdentity()
                    val exclusion = rotationExclusionIndex.find(identity)
                    HistoryCard(
                        entry = entry,
                        onClick = { onWallpaperClick(entry) },
                        rotationExcluded = exclusion != null,
                        onToggleRotationExclusion = {
                            scope.launch {
                                if (exclusion != null) {
                                    rotationExclusionsViewModel.restoreNow(exclusion.stableId)
                                    snackbarHostState.showSnackbar(
                                        resources.getString(R.string.rotation_restored_message, entry.wallpaperId),
                                    )
                                } else {
                                    val added = rotationExclusionsViewModel.exclude(identity)
                                    val result = snackbarHostState.showSnackbar(
                                        message = resources.getString(R.string.rotation_excluded_message, entry.wallpaperId),
                                        actionLabel = resources.getString(R.string.common_undo),
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        rotationExclusionsViewModel.restoreNow(added.stableId)
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearWallpaperHistory()
                    showClearConfirm = false
                }) { Text(stringResource(R.string.history_clear_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun HistoryCard(
    entry: WallpaperHistoryEntity,
    onClick: () -> Unit,
    rotationExcluded: Boolean,
    onToggleRotationExclusion: () -> Unit,
) {
    val dateStr = remember(entry.appliedAt) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(entry.appliedAt))
    }
    val targetLabel = stringResource(
        when (entry.target) {
            "HOME" -> R.string.common_home
            "LOCK" -> R.string.common_lock
            else -> R.string.common_both
        },
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AuraShapes.Medium)
            .clickable(onClick = onClick),
        shape = AuraShapes.Medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box {
            AsyncImage(
                model = entry.thumbnailUrl,
                contentDescription = stringResource(R.string.history_applied_wallpaper_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        if (entry.width > 0 && entry.height > 0)
                            (entry.width.toFloat() / entry.height).coerceIn(0.5f, 1.0f)
                        else 0.67f
                    ),
            )

            IconButton(
                onClick = onToggleRotationExclusion,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.58f), AuraShapes.Medium),
            ) {
                Icon(
                    if (rotationExcluded) Icons.Default.Restore else Icons.Default.PlaylistRemove,
                    contentDescription = stringResource(
                        if (rotationExcluded) R.string.rotation_restore_action else R.string.rotation_exclude_action,
                    ),
                    tint = Color.White,
                )
            }

            // Bottom overlay
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(8.dp),
            ) {
                Text(
                    dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        when (entry.target) {
                            "HOME" -> Icons.Default.Home
                            "LOCK" -> Icons.Default.Lock
                            else -> Icons.Default.Smartphone
                        },
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        targetLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    if (entry.width > 0) {
                        Text(
                            stringResource(R.string.detail_resolution, entry.width, entry.height),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}
