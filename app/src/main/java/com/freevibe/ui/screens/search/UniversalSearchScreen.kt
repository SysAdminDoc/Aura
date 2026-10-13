package com.freevibe.ui.screens.search

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.freevibe.R
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.remote.toSound
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.components.CompactSearchField
import com.freevibe.ui.components.SearchHistoryDropdown
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@Immutable
data class UniversalSearchState(
    val query: String = "",
    val recentQueries: List<String> = emptyList(),
    val localResults: List<UniversalSearchItem> = emptyList(),
    val providerActions: List<UniversalProviderAction> = emptyList(),
) {
    val hasQuery: Boolean get() = query.isNotBlank()
}

@Immutable
data class UniversalSearchItem(
    val id: String,
    val section: UniversalSearchSection,
    val title: String,
    val subtitle: String,
    val badge: UniversalSearchBadge,
    val target: UniversalSearchTarget,
)

@Immutable
data class UniversalProviderAction(
    val id: String,
    val section: UniversalProviderSection,
    val enabled: Boolean,
    val disabledReason: UniversalProviderDisabledReason?,
)

enum class UniversalSearchSection {
    WALLPAPERS,
    VIDEOS,
    SOUNDS,
    COLLECTIONS,
    DOWNLOADS,
    FAVORITES,
    LOCAL_FILES,
}

enum class UniversalProviderSection {
    WALLPAPERS,
    VIDEOS,
    SOUNDS,
}

enum class UniversalSearchBadge {
    SAVED,
    OFFLINE,
    LOCAL,
}

enum class UniversalProviderDisabledReason {
    NEED_QUERY,
    OFFLINE,
    PROVIDER_DISABLED,
}

sealed interface UniversalSearchTarget {
    data class WallpaperFavorite(val entity: FavoriteEntity) : UniversalSearchTarget
    data class SoundFavorite(val entity: FavoriteEntity) : UniversalSearchTarget
    data class Collection(val entity: WallpaperCollectionEntity) : UniversalSearchTarget
    data class Download(val entity: DownloadEntity) : UniversalSearchTarget
    data class FavoriteBucket(val type: String) : UniversalSearchTarget
}

private data class LocalSearchIndex(
    val favorites: List<FavoriteEntity>,
    val downloads: List<DownloadEntity>,
    val collections: List<WallpaperCollectionEntity>,
)

private data class ProviderAvailability(
    val online: Boolean,
    val wallpaperProvidersEnabled: Boolean,
    val videoProvidersEnabled: Boolean,
    val soundProvidersEnabled: Boolean,
)

@HiltViewModel
class UniversalSearchViewModel @Inject constructor(
    favoritesRepository: FavoritesRepository,
    downloadDao: DownloadDao,
    collectionRepository: CollectionRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    preferencesManager: PreferencesManager,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val query = MutableStateFlow("")

    private val localIndex = combine(
        favoritesRepository.getAll(),
        downloadDao.getAll(),
        collectionRepository.getAll(),
    ) { favorites, downloads, collections ->
        LocalSearchIndex(
            favorites = favorites,
            downloads = downloads,
            collections = collections,
        )
    }

    private val wallpaperProvidersEnabled = combine(
        preferencesManager.wallhavenProviderEnabled,
        preferencesManager.pexelsProviderEnabled,
        preferencesManager.pixabayProviderEnabled,
        preferencesManager.communityProviderEnabled,
    ) { wallhaven, pexels, pixabay, community ->
        wallhaven || pexels || pixabay || community
    }

    private val videoProvidersEnabled = combine(
        preferencesManager.youtubeProviderEnabled,
        preferencesManager.pexelsProviderEnabled,
        preferencesManager.pixabayProviderEnabled,
    ) { youtube, pexels, pixabay ->
        youtube || pexels || pixabay
    }

    private val soundProvidersEnabled = combine(
        preferencesManager.youtubeProviderEnabled,
        preferencesManager.communityProviderEnabled,
    ) { youtube, community ->
        youtube || community
    }

    private val providerAvailability = combine(
        networkAvailableFlow(context),
        wallpaperProvidersEnabled,
        videoProvidersEnabled,
        soundProvidersEnabled,
    ) { online, wallpaperProvidersEnabled, videoProvidersEnabled, soundProvidersEnabled ->
        ProviderAvailability(
            online = online,
            wallpaperProvidersEnabled = wallpaperProvidersEnabled,
            videoProvidersEnabled = videoProvidersEnabled,
            soundProvidersEnabled = soundProvidersEnabled,
        )
    }

    val state = combine(
        query,
        searchHistoryRepository.getRecentUniversalSearches(limit = 8).map { entries -> entries.map { it.query } },
        localIndex,
        providerAvailability,
    ) { currentQuery, recentQueries, index, availability ->
        UniversalSearchState(
            query = currentQuery,
            recentQueries = recentQueries,
            localResults = buildUniversalSearchResults(currentQuery, index.favorites, index.downloads, index.collections),
            providerActions = buildProviderActions(currentQuery, availability),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UniversalSearchState())

    fun updateQuery(value: String) {
        query.value = value
    }

    fun submitSearch(value: String = query.value) {
        val trimmed = value.trim()
        query.value = trimmed
        if (trimmed.isBlank()) return
        viewModelScope.launch { searchHistoryRepository.addUniversalSearch(trimmed) }
    }

    fun removeSearch(query: String) {
        viewModelScope.launch { searchHistoryRepository.removeSearch(query, "UNIVERSAL") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepository.clearUniversalHistory() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalSearchScreen(
    initialQuery: String? = null,
    onBack: () -> Unit,
    onWallpaperClick: (FavoriteEntity) -> Unit,
    onSoundClick: (FavoriteEntity) -> Unit,
    onDownloadClick: (DownloadEntity) -> Unit,
    onCollectionsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onSearchWallpapers: (String) -> Unit,
    onSearchVideos: (String) -> Unit,
    onSearchSounds: (String) -> Unit,
    viewModel: UniversalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // rememberSaveable: activity recreation (rotation) must not wipe the user's
    // refined query back to the nav argument.
    var text by rememberSaveable { mutableStateOf(initialQuery.orEmpty()) }
    var initialQueryConsumed by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        if (!initialQueryConsumed) {
            initialQueryConsumed = true
            if (!initialQuery.isNullOrBlank()) {
                text = initialQuery
                viewModel.submitSearch(initialQuery)
                return@LaunchedEffect
            }
        }
        // Restored after process death: re-sync the ViewModel query from the field.
        if (text.isNotBlank()) viewModel.updateQuery(text)
    }
    // NOTE: no state.query -> text back-sync. The StateFlow lags fast typing (4-flow
    // combine), and syncing back drops keystrokes and jumps the cursor.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)),
            ) {
                Box(Modifier.padding(12.dp)) {
                    // Measured, not hardcoded: at large font scale the field grows past
                    // the old fixed 42dp offset and the dropdown would overlap the input.
                    var fieldHeightPx by remember { mutableIntStateOf(0) }
                    CompactSearchField(
                        value = text,
                        onValueChange = {
                            text = it
                            viewModel.updateQuery(it)
                        },
                        placeholder = stringResource(R.string.search_placeholder),
                        leadingIcon = Icons.Default.Search,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { fieldHeightPx = it.size.height },
                        onClear = {
                            text = ""
                            viewModel.updateQuery("")
                            focusManager.clearFocus()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                viewModel.submitSearch(text)
                                focusManager.clearFocus()
                            },
                        ),
                    )
                    SearchHistoryDropdown(
                        recentQueries = state.recentQueries,
                        isVisible = text.isBlank(),
                        onQueryClick = {
                            text = it
                            viewModel.submitSearch(it)
                            focusManager.clearFocus()
                        },
                        onDeleteQuery = viewModel::removeSearch,
                        onClearAll = viewModel::clearSearchHistory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = with(LocalDensity.current) { fieldHeightPx.toDp() } + 4.dp),
                    )
                }
            }

            if (!state.hasQuery) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AuraStateCard(
                        icon = Icons.Default.Search,
                        title = stringResource(R.string.search_empty_title),
                        description = stringResource(R.string.search_empty_body),
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else if (state.localResults.isEmpty() && state.providerActions.none { it.enabled }) {
                // Keep the disabled provider cards visible: their reason ("You're
                // offline", "provider disabled") is exactly what the user needs here.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AuraStateCard(
                        icon = Icons.Default.SearchOff,
                        title = stringResource(R.string.search_no_results_title),
                        description = stringResource(R.string.search_no_results_body, state.query),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.providerActions.forEach { action ->
                        UniversalProviderActionCard(action, onClick = {})
                    }
                }
            } else {
                UniversalSearchResultsList(
                    state = state,
                    onResultClick = { target ->
                        when (target) {
                            is UniversalSearchTarget.WallpaperFavorite -> onWallpaperClick(target.entity)
                            is UniversalSearchTarget.SoundFavorite -> onSoundClick(target.entity)
                            is UniversalSearchTarget.Collection -> onCollectionsClick()
                            is UniversalSearchTarget.Download -> onDownloadClick(target.entity)
                            is UniversalSearchTarget.FavoriteBucket -> onFavoritesClick()
                        }
                    },
                    onProviderClick = { action ->
                        when (action.section) {
                            UniversalProviderSection.WALLPAPERS -> onSearchWallpapers(state.query)
                            UniversalProviderSection.VIDEOS -> onSearchVideos(state.query)
                            UniversalProviderSection.SOUNDS -> onSearchSounds(state.query)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UniversalSearchResultsList(
    state: UniversalSearchState,
    onResultClick: (UniversalSearchTarget) -> Unit,
    onProviderClick: (UniversalProviderAction) -> Unit,
) {
    val sections = UniversalSearchSection.entries
    val grouped = state.localResults.groupBy { it.section }
    LazyColumn(
        contentPadding = PaddingValues(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sections.forEach { section ->
            val items = grouped[section].orEmpty()
            if (items.isNotEmpty()) {
                item(key = "section_${section.name}", contentType = "section_header") {
                    SearchSectionHeader(sectionTitle(section))
                }
                items(items, key = { it.id }, contentType = { "local_result" }) { result ->
                    UniversalSearchResultCard(result, onClick = { onResultClick(result.target) })
                }
            }
        }
        if (state.providerActions.isNotEmpty()) {
            item(key = "section_providers", contentType = "section_header") {
                SearchSectionHeader(stringResource(R.string.search_section_online_providers))
            }
            items(state.providerActions, key = { it.id }, contentType = { "provider_action" }) { action ->
                UniversalProviderActionCard(action, onClick = { onProviderClick(action) })
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun UniversalSearchResultCard(
    result: UniversalSearchItem,
    onClick: () -> Unit,
) {
    val actionLabel = stringResource(R.string.search_open_result, result.title)
    val badge = searchBadgeLabel(result.badge)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .clickable(onClickLabel = actionLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = listOf(result.title, result.subtitle, badge)
                    .filter { it.isNotBlank() }
                    .joinToString(". ")
                onClick(label = actionLabel, action = null)
            },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchResultIcon(result.section)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(result.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun UniversalProviderActionCard(
    action: UniversalProviderAction,
    onClick: () -> Unit,
) {
    val title = providerActionTitle(action.section)
    val subtitle = providerActionSubtitle(action.section)
    val disabledReason = action.disabledReason?.let { providerDisabledReasonLabel(it) }.orEmpty()
    val actionLabel = stringResource(R.string.search_open_result, title)
    val enabledDescription = stringResource(R.string.a11y_title_subtitle, title, subtitle)
    val disabledDescription = stringResource(R.string.a11y_title_subtitle, title, disabledReason)
    val enabledColor = MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .then(
                if (action.enabled) {
                    Modifier
                        .clickable(onClickLabel = actionLabel, onClick = onClick)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                            contentDescription = enabledDescription
                            onClick(label = actionLabel, action = null)
                        }
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = disabledDescription
                    }
                },
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (action.enabled) enabledColor else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (action.enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIcon(action.section, enabled = action.enabled)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (action.enabled) subtitle else disabledReason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(if (action.enabled) R.string.search_provider_online else R.string.search_provider_disabled),
                style = MaterialTheme.typography.labelSmall,
                color = if (action.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultIcon(section: UniversalSearchSection) {
    val icon = when (section) {
        UniversalSearchSection.WALLPAPERS -> Icons.Default.Image
        UniversalSearchSection.VIDEOS -> Icons.Default.SlowMotionVideo
        UniversalSearchSection.SOUNDS -> Icons.Default.LibraryMusic
        UniversalSearchSection.COLLECTIONS -> Icons.Default.Folder
        UniversalSearchSection.DOWNLOADS -> Icons.Default.CloudDownload
        UniversalSearchSection.FAVORITES -> Icons.Default.Favorite
        UniversalSearchSection.LOCAL_FILES -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
    IconShell(icon = icon, tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ProviderIcon(section: UniversalProviderSection, enabled: Boolean) {
    val icon = when (section) {
        UniversalProviderSection.WALLPAPERS -> Icons.Default.ImageSearch
        UniversalProviderSection.VIDEOS -> Icons.Default.SlowMotionVideo
        UniversalProviderSection.SOUNDS -> Icons.Default.LibraryMusic
    }
    IconShell(
        icon = icon,
        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IconShell(icon: ImageVector, tint: Color) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = tint.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = tint)
        }
    }
}

@Composable
private fun sectionTitle(section: UniversalSearchSection): String = when (section) {
    UniversalSearchSection.WALLPAPERS -> stringResource(R.string.search_section_wallpapers)
    UniversalSearchSection.VIDEOS -> stringResource(R.string.search_section_videos)
    UniversalSearchSection.SOUNDS -> stringResource(R.string.search_section_sounds)
    UniversalSearchSection.COLLECTIONS -> stringResource(R.string.search_section_collections)
    UniversalSearchSection.DOWNLOADS -> stringResource(R.string.search_section_downloads)
    UniversalSearchSection.FAVORITES -> stringResource(R.string.search_section_favorites)
    UniversalSearchSection.LOCAL_FILES -> stringResource(R.string.search_section_local_files)
}

@Composable
private fun searchBadgeLabel(badge: UniversalSearchBadge): String = when (badge) {
    UniversalSearchBadge.SAVED -> stringResource(R.string.search_badge_saved)
    UniversalSearchBadge.OFFLINE -> stringResource(R.string.search_badge_offline)
    UniversalSearchBadge.LOCAL -> stringResource(R.string.search_badge_local)
}

@Composable
private fun providerActionTitle(section: UniversalProviderSection): String = when (section) {
    UniversalProviderSection.WALLPAPERS -> stringResource(R.string.search_provider_wallpapers_title)
    UniversalProviderSection.VIDEOS -> stringResource(R.string.search_provider_videos_title)
    UniversalProviderSection.SOUNDS -> stringResource(R.string.search_provider_sounds_title)
}

@Composable
private fun providerActionSubtitle(section: UniversalProviderSection): String = when (section) {
    UniversalProviderSection.WALLPAPERS -> stringResource(R.string.search_provider_wallpapers_subtitle)
    UniversalProviderSection.VIDEOS -> stringResource(R.string.search_provider_videos_subtitle)
    UniversalProviderSection.SOUNDS -> stringResource(R.string.search_provider_sounds_subtitle)
}

@Composable
private fun providerDisabledReasonLabel(reason: UniversalProviderDisabledReason): String = when (reason) {
    UniversalProviderDisabledReason.NEED_QUERY -> stringResource(R.string.search_provider_need_query)
    UniversalProviderDisabledReason.OFFLINE -> stringResource(R.string.search_provider_offline)
    UniversalProviderDisabledReason.PROVIDER_DISABLED -> stringResource(R.string.search_provider_disabled_reason)
}

internal fun buildUniversalSearchResults(
    query: String,
    favorites: List<FavoriteEntity>,
    downloads: List<DownloadEntity>,
    collections: List<WallpaperCollectionEntity>,
): List<UniversalSearchItem> {
    val normalized = query.normalizedSearchNeedle()
    if (normalized.isBlank()) return emptyList()

    val matchedFavorites = favorites.filter { it.matchesFavoriteQuery(normalized) }
    val wallpaperFavorites = matchedFavorites
        .filter { it.type.equals("WALLPAPER", ignoreCase = true) }
        .take(6)
    val soundFavorites = matchedFavorites
        .filter { it.type.equals("SOUND", ignoreCase = true) }
        .take(6)

    val wallpaperResults = wallpaperFavorites.map { favorite ->
        UniversalSearchItem(
            id = "wallpaper_${favorite.stableSearchId()}",
            section = UniversalSearchSection.WALLPAPERS,
            title = favorite.displayTitle(),
            subtitle = listOf(favorite.category.orEmpty(), favorite.source).filter { it.isNotBlank() }.joinToString(" - "),
            badge = UniversalSearchBadge.OFFLINE,
            target = UniversalSearchTarget.WallpaperFavorite(favorite),
        )
    }

    val soundResults = soundFavorites.map { favorite ->
        UniversalSearchItem(
            id = "sound_${favorite.stableSearchId()}",
            section = UniversalSearchSection.SOUNDS,
            title = favorite.displayTitle(),
            subtitle = listOf(favorite.fileType.orEmpty(), favorite.source).filter { it.isNotBlank() }.joinToString(" - "),
            badge = UniversalSearchBadge.OFFLINE,
            target = UniversalSearchTarget.SoundFavorite(favorite),
        )
    }

    // FAVORITES is an overflow section: only favorites that didn't fit the per-type
    // caps above — the same item must not be listed twice in one result list.
    val shownFavoriteKeys = (wallpaperFavorites + soundFavorites).map { it.stableSearchId() }.toSet()
    val favoriteResults = matchedFavorites
        .filterNot { it.stableSearchId() in shownFavoriteKeys }
        .take(12)
        .map { favorite ->
            val type = favorite.type.uppercase(Locale.ROOT)
            UniversalSearchItem(
                id = "favorite_${favorite.stableSearchId()}",
                section = UniversalSearchSection.FAVORITES,
                title = favorite.displayTitle(),
                subtitle = "${favorite.source} ${type.lowercase(Locale.ROOT)}",
                badge = UniversalSearchBadge.SAVED,
                target = if (type == "SOUND") {
                    UniversalSearchTarget.SoundFavorite(favorite)
                } else {
                    UniversalSearchTarget.WallpaperFavorite(favorite)
                },
            )
        }

    val videoResults = downloads
        .filter { it.type.isVideoType() && it.matchesDownloadQuery(normalized) }
        .take(6)
        .map { download ->
            UniversalSearchItem(
                id = "video_${download.id}",
                section = UniversalSearchSection.VIDEOS,
                title = download.displayTitle(),
                subtitle = listOf(download.source, download.localPath.fileNameFromPath()).filter { it.isNotBlank() }.joinToString(" - "),
                badge = UniversalSearchBadge.LOCAL,
                target = UniversalSearchTarget.Download(download),
            )
        }

    val collectionResults = collections
        .filter { it.name.normalizedSearchHaystack().contains(normalized) }
        .take(8)
        .map { collection ->
            UniversalSearchItem(
                id = "collection_${collection.collectionId}",
                section = UniversalSearchSection.COLLECTIONS,
                title = collection.name,
                subtitle = "",
                badge = UniversalSearchBadge.LOCAL,
                target = UniversalSearchTarget.Collection(collection),
            )
        }

    val downloadResults = downloads
        .filter { it.matchesDownloadQuery(normalized) }
        .take(10)
        .map { download ->
            UniversalSearchItem(
                id = "download_${download.id}",
                section = UniversalSearchSection.DOWNLOADS,
                title = download.displayTitle(),
                subtitle = listOf(download.type.lowercase(Locale.ROOT), download.source, download.localPath.fileNameFromPath())
                    .filter { it.isNotBlank() }
                    .joinToString(" - "),
                badge = UniversalSearchBadge.LOCAL,
                target = UniversalSearchTarget.Download(download),
            )
        }

    val localFiles = (
        downloads
            .filter { it.localPath.isNotBlank() && it.matchesDownloadQuery(normalized) }
            .map { download ->
                UniversalSearchItem(
                    id = "local_download_${download.id}",
                    section = UniversalSearchSection.LOCAL_FILES,
                    title = download.localPath.fileNameFromPath().ifBlank { download.displayTitle() },
                    subtitle = download.localPath,
                    badge = UniversalSearchBadge.LOCAL,
                    target = UniversalSearchTarget.Download(download),
                )
            } +
            favorites
                .filter { it.offlinePath.isNotBlank() && it.matchesFavoriteQuery(normalized) }
                .map { favorite ->
                    UniversalSearchItem(
                        id = "local_favorite_${favorite.stableSearchId()}",
                        section = UniversalSearchSection.LOCAL_FILES,
                        title = favorite.offlinePath.fileNameFromPath().ifBlank { favorite.displayTitle() },
                        subtitle = favorite.offlinePath,
                        badge = UniversalSearchBadge.LOCAL,
                        // Route to the favorite itself: these files aren't listed on
                        // the Downloads screen, so a Downloads target is a dead end.
                        target = if (favorite.type.equals("SOUND", ignoreCase = true)) {
                            UniversalSearchTarget.SoundFavorite(favorite)
                        } else {
                            UniversalSearchTarget.WallpaperFavorite(favorite)
                        },
                    )
                }
        )
        .distinctBy { it.subtitle }
        .take(10)

    return wallpaperResults + videoResults + soundResults + collectionResults + downloadResults + favoriteResults + localFiles
}

private fun buildProviderActions(
    query: String,
    availability: ProviderAvailability,
): List<UniversalProviderAction> {
    val hasQuery = query.isNotBlank()
    return listOf(
        UniversalProviderAction(
            id = "provider_wallpapers",
            section = UniversalProviderSection.WALLPAPERS,
            enabled = hasQuery && availability.online && availability.wallpaperProvidersEnabled,
            disabledReason = providerDisabledReason(hasQuery, availability.online, availability.wallpaperProvidersEnabled),
        ),
        UniversalProviderAction(
            id = "provider_videos",
            section = UniversalProviderSection.VIDEOS,
            enabled = hasQuery && availability.online && availability.videoProvidersEnabled,
            disabledReason = providerDisabledReason(hasQuery, availability.online, availability.videoProvidersEnabled),
        ),
        UniversalProviderAction(
            id = "provider_sounds",
            section = UniversalProviderSection.SOUNDS,
            enabled = hasQuery && availability.online && availability.soundProvidersEnabled,
            disabledReason = providerDisabledReason(hasQuery, availability.online, availability.soundProvidersEnabled),
        ),
    )
}

private fun providerDisabledReason(
    hasQuery: Boolean,
    online: Boolean,
    providerEnabled: Boolean,
): UniversalProviderDisabledReason? = when {
    !hasQuery -> UniversalProviderDisabledReason.NEED_QUERY
    !online -> UniversalProviderDisabledReason.OFFLINE
    !providerEnabled -> UniversalProviderDisabledReason.PROVIDER_DISABLED
    else -> null
}

// URL and raw-path fields are deliberately excluded from matching: queries like
// "http", "com", or "storage" would otherwise match essentially every item.
private fun FavoriteEntity.matchesFavoriteQuery(normalized: String): Boolean =
    listOf(
        id,
        source,
        type,
        name,
        offlinePath.fileNameFromPath(),
        tags.orEmpty(),
        colors.orEmpty(),
        category.orEmpty(),
        uploaderName.orEmpty(),
        license.orEmpty(),
        fileType.orEmpty(),
    ).any { it.normalizedSearchHaystack().contains(normalized) }

private fun DownloadEntity.matchesDownloadQuery(normalized: String): Boolean =
    listOf(id, source, type, name, localPath.fileNameFromPath())
        .any { it.normalizedSearchHaystack().contains(normalized) }

private fun FavoriteEntity.displayTitle(): String =
    name.ifBlank { category.orEmpty() }.ifBlank { offlinePath.fileNameFromPath() }.ifBlank { id }

private fun DownloadEntity.displayTitle(): String =
    name.ifBlank { localPath.fileNameFromPath() }.ifBlank { id }

private fun String.fileNameFromPath(): String =
    substringAfterLast('/').substringAfterLast('\\').substringBefore('?').ifBlank { "" }

private fun String.isVideoType(): Boolean {
    val normalized = uppercase(Locale.ROOT)
    return normalized == "VIDEO" || normalized == "LIVE_WALLPAPER" || normalized == "VIDEO_WALLPAPER"
}

private fun FavoriteEntity.stableSearchId(): String = "${type}_${source}_$id"

private fun String.normalizedSearchNeedle(): String = trim().lowercase(Locale.ROOT)

private fun String.normalizedSearchHaystack(): String = lowercase(Locale.ROOT)

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
private fun currentNetworkAvailable(connectivityManager: ConnectivityManager): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Suppress("MissingPermission")
private fun networkAvailableFlow(context: Context): Flow<Boolean> = callbackFlow {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (connectivityManager == null) {
        trySend(false)
        close()
        return@callbackFlow
    }

    fun sendCurrent() {
        val available = runCatching { currentNetworkAvailable(connectivityManager) }.getOrDefault(false)
        trySend(available)
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = sendCurrent()
        override fun onLost(network: Network) = sendCurrent()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = sendCurrent()
    }

    sendCurrent()
    runCatching { connectivityManager.registerDefaultNetworkCallback(callback) }
        .onFailure { trySend(false) }
    awaitClose {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}.distinctUntilChanged()
