package com.freevibe.ui.screens.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freevibe.R
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.service.WallpaperHistoryManager
import com.freevibe.ui.components.AuraScreenHeader
import com.freevibe.ui.theme.AuraShapes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import androidx.compose.material.icons.filled.VisibilityOff
import com.freevibe.data.repository.VoteRepository

@Immutable
data class LibraryHubState(
    val favoriteCount: Int = 0,
    val downloadCount: Int = 0,
    val collectionCount: Int = 0,
    val recentActivityCount: Int = 0,
    val recentSearchCount: Int = 0,
    val hiddenCount: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    favoritesRepository: FavoritesRepository,
    downloadDao: DownloadDao,
    collectionRepository: CollectionRepository,
    searchHistoryRepository: SearchHistoryRepository,
    wallpaperHistoryManager: WallpaperHistoryManager,
    voteRepository: VoteRepository,
) : ViewModel() {
    private val recentSearchCount = combine(
        searchHistoryRepository.getRecentWallpaperSearches(limit = 50),
        searchHistoryRepository.getRecentSoundSearches(limit = 50),
    ) { wallpapers, sounds ->
        (wallpapers.map { "WALLPAPER:${it.query}" } + sounds.map { "SOUND:${it.query}" }).distinct().size
    }

    val state = combine(
        favoritesRepository.count(),
        downloadDao.getAll().map { it.size },
        collectionRepository.getAll().map { it.size },
        wallpaperHistoryManager.getRecent(limit = 25).map { it.size },
        recentSearchCount,
    ) { favoriteCount, downloadCount, collectionCount, recentActivityCount, recentSearchCount ->
        LibraryHubState(
            favoriteCount = favoriteCount,
            downloadCount = downloadCount,
            collectionCount = collectionCount,
            recentActivityCount = recentActivityCount,
            recentSearchCount = recentSearchCount,
        )
    }.combine(voteRepository.localHiddenIds) { hub, hidden -> hub.copy(hiddenCount = hidden.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryHubState())
}

@Composable
fun LibraryScreen(
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onCollectionsClick: () -> Unit,
    onLocalImportsClick: () -> Unit,
    onRecentActivityClick: () -> Unit,
    onHiddenItemsClick: () -> Unit,
    onBackupRestoreClick: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = listOf(
        LibraryHubItem(
            title = stringResource(R.string.library_search_title),
            body = stringResource(R.string.library_search_body),
            status = stringResource(R.string.library_search_status),
            icon = Icons.Default.Search,
            onClick = onSearchClick,
        ),
        LibraryHubItem(
            title = stringResource(R.string.library_favorites_title),
            body = stringResource(R.string.library_favorites_body),
            status = stringResource(R.string.library_favorites_count, state.favoriteCount),
            icon = Icons.Default.Favorite,
            onClick = onFavoritesClick,
        ),
        LibraryHubItem(
            title = stringResource(R.string.library_downloads_title),
            body = stringResource(R.string.library_downloads_body),
            status = stringResource(R.string.library_downloads_count, state.downloadCount),
            icon = Icons.Default.CloudDownload,
            onClick = onDownloadsClick,
        ),
        LibraryHubItem(
            title = stringResource(R.string.library_collections_title),
            body = stringResource(R.string.library_collections_body),
            status = pluralStringResource(
                R.plurals.library_collections_count,
                state.collectionCount,
                state.collectionCount,
            ),
            icon = Icons.Default.Folder,
            onClick = onCollectionsClick,
        ),
        LibraryHubItem(
            title = stringResource(R.string.library_imports_title),
            body = stringResource(R.string.library_imports_body),
            status = stringResource(R.string.library_imports_status),
            icon = Icons.Default.ImportExport,
            onClick = onLocalImportsClick,
        ),
        LibraryHubItem(
            title = stringResource(R.string.library_recent_title),
            body = stringResource(R.string.library_recent_body),
            status = stringResource(R.string.library_recent_count, state.recentActivityCount, state.recentSearchCount),
            icon = Icons.Default.History,
            onClick = onRecentActivityClick,
        ),
        LibraryHubItem(
            title = stringResource(R.string.library_hidden_title),
            body = stringResource(R.string.library_hidden_body),
            status = stringResource(R.string.library_hidden_count, state.hiddenCount),
            icon = Icons.Default.VisibilityOff,
            onClick = onHiddenItemsClick,
        ),
        LibraryHubItem(
            title = stringResource(R.string.library_backup_title),
            body = stringResource(R.string.library_backup_body),
            status = stringResource(R.string.library_backup_status),
            icon = Icons.Default.Restore,
            onClick = onBackupRestoreClick,
        ),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(contentType = "library_header") {
            AuraScreenHeader(
                label = stringResource(R.string.nav_library),
                icon = Icons.Default.Inventory2,
                title = stringResource(R.string.library_title),
                subtitle = stringResource(R.string.library_subtitle),
            ) {
                LibrarySummaryRow(state)
            }
        }
        items(items, key = { it.title }, contentType = { "library_item" }) { item ->
            LibraryHubCard(item)
        }
        item(contentType = "library_bottom_spacer") {
            Spacer(Modifier.height(76.dp))
        }
    }
}

@Composable
private fun LibrarySummaryRow(state: LibraryHubState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LibrarySummaryMetric(
            label = stringResource(R.string.library_summary_favorites),
            value = state.favoriteCount.toString(),
            modifier = Modifier.weight(1f),
        )
        LibrarySummaryMetric(
            label = stringResource(R.string.library_summary_downloads),
            value = state.downloadCount.toString(),
            modifier = Modifier.weight(1f),
        )
        LibrarySummaryMetric(
            label = stringResource(R.string.library_summary_collections),
            value = state.collectionCount.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LibrarySummaryMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val summaryDescription = stringResource(R.string.a11y_label_value, label, value)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = summaryDescription
        },
        shape = AuraShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class LibraryHubItem(
    val title: String,
    val body: String,
    val status: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun LibraryHubCard(item: LibraryHubItem) {
    val actionLabel = stringResource(R.string.library_open_item, item.title)
    val itemDescription = stringResource(R.string.library_item_description, item.title, item.body, item.status)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = itemDescription
                onClick(label = actionLabel, action = null)
            },
        shape = AuraShapes.Medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = AuraShapes.Medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            ) {
                Icon(
                    item.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    item.title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
