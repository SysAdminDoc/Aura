package com.freevibe.ui.screens.favorites

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freevibe.R
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.isSourceUnavailable
import com.freevibe.data.model.stableKey
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.components.AuraStateCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    onWallpaperClick: (FavoriteEntity) -> Unit,
    onSoundClick: (FavoriteEntity) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val wallpapers by viewModel.wallpapers.collectAsStateWithLifecycle()
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val batchState by viewModel.batchState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var sortBy by rememberSaveable { mutableStateOf("recent") } // recent, name, oldest
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val tabs = listOf("Wallpapers (${wallpapers.size})", "Sounds (${sounds.size})")

    // -- Bulk selection state (wallpaper tab only in v1) --
    var selectionMode by remember { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    fun exitSelection() { selectionMode = false; selectedKeys = emptySet() }
    fun toggleSelect(key: String) {
        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
        if (selectedKeys.isEmpty()) selectionMode = false
    }
    // Exit selection on back before closing the screen.
    BackHandler(enabled = selectionMode) { exitSelection() }
    // Also exit if the user switches tabs — selection is scoped to the wallpaper tab.
    LaunchedEffect(selectedTab) { if (selectedTab != 0) exitSelection() }

    val sortedWallpapers = remember(wallpapers, sortBy) {
        when (sortBy) {
            "name" -> wallpapers.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            "oldest" -> wallpapers.sortedBy { it.addedAt }
            else -> wallpapers.sortedByDescending { it.addedAt }
        }
    }
    val sortedSounds = remember(sounds, sortBy) {
        when (sortBy) {
            "name" -> sounds.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            "oldest" -> sounds.sortedBy { it.addedAt }
            else -> sounds.sortedByDescending { it.addedAt }
        }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportFavorites(it) } }

    // Import launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFavorites(it) } }

    LaunchedEffect(message) {
        message?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(snackbarHost = { AuraSnackbarHost(snackbarHostState) }) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedKeys.size} selected", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Exit selection")
                        }
                    },
                    actions = {
                        val allKeys = sortedWallpapers.map { it.stableKey() }.toSet()
                        val allSelected = allKeys.isNotEmpty() && selectedKeys.containsAll(allKeys)
                        IconButton(onClick = {
                            selectedKeys = if (allSelected) emptySet() else allKeys
                            if (selectedKeys.isEmpty()) selectionMode = false
                        }) {
                            Icon(
                                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                                contentDescription = if (allSelected) "Deselect all" else "Select all",
                            )
                        }
                        IconButton(
                            enabled = selectedKeys.isNotEmpty(),
                            onClick = {
                                viewModel.bulkDownload(selectedKeys)
                                exitSelection()
                            },
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = "Download ${selectedKeys.size} selected wallpaper${if (selectedKeys.size == 1) "" else "s"}",
                            )
                        }
                        IconButton(
                            enabled = selectedKeys.isNotEmpty(),
                            onClick = {
                                val snapshot = selectedKeys
                                val snapshotItems = sortedWallpapers.filter { it.stableKey() in snapshot }
                                viewModel.bulkDelete(snapshot)
                                exitSelection()
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Removed ${snapshot.size} favorite${if (snapshot.size == 1) "" else "s"}",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        snapshotItems.forEach { viewModel.restoreFavorite(it) }
                                    } else {
                                        viewModel.deleteGeneratedWallpaperFiles(snapshotItems)
                                    }
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove ${selectedKeys.size} selected favorite${if (selectedKeys.size == 1) "" else "s"}",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            } else {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.collections_more))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.favorites_export)) },
                                onClick = {
                                    showMenu = false
                                    exportLauncher.launch("freevibe_favorites.json")
                                },
                                leadingIcon = { Icon(Icons.Default.Upload, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.favorites_import)) },
                                onClick = {
                                    showMenu = false
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                            )
                            if (wallpapers.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.favorites_download_all)) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.downloadAllWallpapers()
                                    },
                                    leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.favorites_sort_recent)) },
                                onClick = { sortBy = "recent"; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.Schedule, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.favorites_sort_name)) },
                                onClick = { sortBy = "name"; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.favorites_sort_oldest)) },
                                onClick = { sortBy = "oldest"; showMenu = false },
                                leadingIcon = { Icon(Icons.Default.History, null) },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }

            // Batch-download progress banner: previously the "Download all" action started work
            // but surfaced no progress. Now we render a compact linear indicator + counts whenever
            // BatchDownloadService is running.
            if (batchState.isRunning || (batchState.totalCount > 0 && !batchState.isComplete)) {
                val batchProgressDescription = favoritesBatchProgressSummary(
                    processed = batchState.processedCount,
                    total = batchState.totalCount,
                    failed = batchState.failedCount,
                    blocked = batchState.blockedCount,
                    currentItem = batchState.currentItem,
                )
                val batchProgressText = if (batchState.failedCount > 0 || batchState.blockedCount > 0) {
                    stringResource(
                        R.string.favorites_batch_downloading_with_outcomes,
                        batchState.processedCount,
                        batchState.totalCount,
                        batchState.failedCount,
                        batchState.blockedCount,
                    )
                } else {
                    stringResource(
                        R.string.favorites_batch_downloading,
                        batchState.processedCount,
                        batchState.totalCount,
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = batchProgressDescription
                            stateDescription = batchProgressText
                        },
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(9.dp)
                                    .size(20.dp),
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = batchProgressText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (batchState.currentItem.isNotBlank()) {
                                    Text(
                                        text = batchState.currentItem,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 150.dp),
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { batchState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .semantics {
                                        progressBarRangeInfo = ProgressBarRangeInfo(
                                            batchState.progress.coerceIn(0f, 1f),
                                            0f..1f,
                                        )
                                    },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    if (sortedWallpapers.isEmpty()) {
                        EmptyState(
                            title = "No favorite wallpapers yet",
                            description = "Long-press a wallpaper in the feed or save from detail to build a personal library.",
                            icon = Icons.Default.Wallpaper,
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sortedWallpapers, key = { it.stableKey() }, contentType = { "favorite_card" }) { fav ->
                                val key = fav.stableKey()
                                val isSelected = key in selectedKeys
                                val sourceUnavailable = fav.isSourceUnavailable()
                                val selectedDescription = stringResource(
                                    if (isSelected) R.string.a11y_selected else R.string.a11y_not_selected,
                                )
                                val openLabel = "Open ${favoriteDisplayName(fav)}"
                                val selectionLabel = if (isSelected) {
                                    "Deselect ${favoriteDisplayName(fav)}"
                                } else {
                                    "Select ${favoriteDisplayName(fav)}"
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .then(
                                            if (isSelected) {
                                                Modifier.border(
                                                    width = 3.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            } else Modifier,
                                        )
                                        .combinedClickable(
                                            onClickLabel = if (selectionMode) selectionLabel else openLabel,
                                            onClick = {
                                                if (selectionMode) {
                                                    toggleSelect(key)
                                                } else {
                                                    viewModel.selectWallpaper(fav, sortedWallpapers)
                                                    onWallpaperClick(fav)
                                                }
                                            },
                                            onLongClickLabel = selectionLabel,
                                            onLongClick = {
                                                if (!selectionMode) selectionMode = true
                                                toggleSelect(key)
                                            },
                                        )
                                        .semantics(mergeDescendants = true) {
                                            contentDescription = favoriteWallpaperSummary(
                                                favorite = fav,
                                                isSelected = isSelected,
                                                sourceUnavailable = sourceUnavailable,
                                            )
                                            stateDescription = selectedDescription
                                            onClick(label = if (selectionMode) selectionLabel else openLabel, action = null)
                                            customActions = listOf(
                                                CustomAccessibilityAction(openLabel) {
                                                    viewModel.selectWallpaper(fav, sortedWallpapers)
                                                    onWallpaperClick(fav)
                                                    true
                                                },
                                                CustomAccessibilityAction(selectionLabel) {
                                                    if (!selectionMode) selectionMode = true
                                                    toggleSelect(key)
                                                    true
                                                },
                                            )
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = fav.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(0.67f),
                                        )
                                        if (sourceUnavailable) {
                                            SourceUnavailableBadge(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(8.dp),
                                            )
                                        }
                                        if (selectionMode) {
                                            // Dim unselected cards to emphasize the selection.
                                            if (!isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                                                )
                                            }
                                            // Selection indicator: filled check for selected, outlined circle otherwise.
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(8.dp)
                                                    .size(26.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (sortedSounds.isEmpty()) {
                        EmptyState(
                            title = "No favorite sounds yet",
                            description = "Save ringtones, notifications, and alarms here for quick playback and export.",
                            icon = Icons.Default.MusicNote,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(sortedSounds, key = { it.stableKey() }, contentType = { "favorite_card" }) { fav ->
                                val sourceUnavailable = fav.isSourceUnavailable()
                                val soundSummary = favoriteSoundSummary(fav, sourceUnavailable)
                                val openLabel = "Open ${favoriteDisplayName(fav)}"
                                val removeLabel = "Remove ${favoriteDisplayName(fav)}"
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value != SwipeToDismissBoxValue.Settled) {
                                            viewModel.removeFavorite(fav)
                                            scope.launch {
                                                val result = snackbarHostState.showSnackbar(
                                                    message = "Removed ${fav.name}",
                                                    actionLabel = "Undo",
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.restoreFavorite(fav)
                                                }
                                            }
                                            true
                                        } else false
                                    },
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    backgroundContent = {
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                .padding(horizontal = 20.dp),
                                            contentAlignment = Alignment.CenterEnd,
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = removeLabel,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    },
                                    enableDismissFromStartToEnd = false,
                                ) {
                                    Surface(
                                        onClick = {
                                            viewModel.selectSound(fav)
                                            onSoundClick(fav)
                                        },
                                        modifier = Modifier.semantics(mergeDescendants = true) {
                                            contentDescription = soundSummary
                                            stateDescription = if (sourceUnavailable) "Source unavailable" else "Saved sound"
                                            onClick(label = openLabel, action = null)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f)),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(fav.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (fav.duration > 0 || sourceUnavailable) {
                                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        if (fav.duration > 0) {
                                                            Text(
                                                                "${fav.duration.toInt()}s",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            )
                                                        }
                                                        if (sourceUnavailable) {
                                                            Text(
                                                                "Source unavailable",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.error,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceUnavailableBadge(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(stringResource(R.string.favorites_source_unavailable), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyState(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AuraStateCard(
            icon = icon,
            title = title,
            description = description,
            modifier = Modifier.padding(24.dp),
        )
    }
}

internal fun favoriteDisplayName(favorite: FavoriteEntity): String =
    favorite.name.ifBlank { favorite.id }

internal fun favoriteWallpaperSummary(
    favorite: FavoriteEntity,
    isSelected: Boolean,
    sourceUnavailable: Boolean,
): String {
    val status = when {
        sourceUnavailable -> "source unavailable"
        isSelected -> "selected"
        else -> "saved wallpaper"
    }
    val details = buildList {
        favorite.category?.takeIf { it.isNotBlank() }?.let(::add)
        if (favorite.width > 0 && favorite.height > 0) add("${favorite.width} by ${favorite.height}")
        favorite.source.takeIf { it.isNotBlank() }?.let { add(sourceDisplayLabel(it)) }
    }.joinToString(", ")
    return listOf(favoriteDisplayName(favorite), status, details)
        .filter { it.isNotBlank() }
        .joinToString(". ")
}

internal fun favoriteSoundSummary(
    favorite: FavoriteEntity,
    sourceUnavailable: Boolean,
): String {
    val status = if (sourceUnavailable) "source unavailable" else "saved sound"
    val duration = if (favorite.duration > 0) "${favorite.duration.toInt()} seconds" else ""
    return listOf(favoriteDisplayName(favorite), status, duration, sourceDisplayLabel(favorite.source))
        .filter { it.isNotBlank() }
        .joinToString(". ")
}

internal fun favoritesBatchProgressSummary(
    processed: Int,
    total: Int,
    failed: Int,
    blocked: Int,
    currentItem: String,
): String {
    val outcomes = buildList {
        if (failed > 0) add("$failed failed")
        if (blocked > 0) add("$blocked blocked")
    }.joinToString(", ")
    val current = currentItem.takeIf { it.isNotBlank() }?.let { "Current item: $it" }.orEmpty()
    return listOf(
        "Downloading favorites $processed of $total",
        outcomes,
        current,
    ).filter { it.isNotBlank() }.joinToString(". ")
}

private fun sourceDisplayLabel(source: String): String =
    when (source.uppercase(java.util.Locale.ROOT)) {
        "YOUTUBE" -> "YouTube"
        "CCMIXTER" -> "ccMixter"
        "SOUNDCLOUD" -> "SoundCloud"
        "BUNDLED" -> "Aura Picks"
        else -> source.split('_', '-')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase(java.util.Locale.ROOT)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
