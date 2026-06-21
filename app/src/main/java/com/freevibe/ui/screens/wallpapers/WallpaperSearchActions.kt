package com.freevibe.ui.screens.wallpapers

import android.content.Context
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SourceMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class WallpaperSearchActions(
    private val context: Context,
    private val wallpaperRepo: WallpaperRepository,
    private val favoritesRepo: FavoritesRepository,
    private val selectedContent: SelectedContentHolder,
    private val cacheManager: com.freevibe.data.local.WallpaperCacheManager,
    private val sourceMetrics: SourceMetrics,
    private val wallhavenProviderEnabled: StateFlow<Boolean>,
    private val state: MutableStateFlow<WallpapersUiState>,
    private val topVoted: MutableStateFlow<List<Pair<Wallpaper, Int>>>,
    private val dailyPick: MutableStateFlow<Wallpaper?>,
    private val scope: CoroutineScope,
) {

    fun findSimilar(wallpaper: Wallpaper) {
        if (!wallhavenProviderEnabled.value) {
            sourceMetrics.recordDisabled(SOURCE_WALLHAVEN)
            state.update {
                it.copy(
                    error = wallhavenDisabledMessage(),
                    errorSource = WallpaperTab.SEARCH.name,
                    isLoading = false,
                )
            }
            return
        }
        val returnTab = state.value.selectedTab
            .takeIf { it != WallpaperTab.SEARCH && it != WallpaperTab.COLOR }
            ?: state.value.browseTab
        scope.launch {
            state.update {
                it.copy(
                    selectedTab = WallpaperTab.SEARCH,
                    browseTab = returnTab,
                    query = "Similar",
                    selectedColor = null,
                    wallpapers = emptyList(),
                    isLoading = true,
                    currentPage = 1,
                )
            }
            try {
                val results = mutableListOf<Wallpaper>()
                if (wallpaper.source == ContentSource.WALLHAVEN) {
                    val whId = wallpaper.id.removePrefix("wh_")
                    val similar = wallpaperRepo.findSimilar(whId)
                    results.addAll(similar.items)
                }
                if (wallpaper.colors.isNotEmpty()) {
                    val existingIds = results.map { it.stableKey() }.toSet()
                    val colorResult = wallpaperRepo.searchByColor(wallpaper.colors.first().removePrefix("#"))
                    results.addAll(colorResult.items.filter { it.stableKey() !in existingIds })
                }
                state.update { it.copy(wallpapers = results, isLoading = false, hasMore = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun findSimilarById(
        wallpaperId: String,
        source: ContentSource? = null,
        fullUrl: String? = null,
    ) {
        scope.launch {
            val wallpaper = resolveWallpaperSelection(wallpaperId, source, fullUrl)?.first
            if (wallpaper != null) {
                findSimilar(wallpaper)
            } else {
                state.update {
                    it.copy(
                        error = "Wallpaper unavailable",
                        errorSource = WallpaperTab.SEARCH.name,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                    )
                }
            }
        }
    }

    fun loadRandom() {
        if (!wallhavenProviderEnabled.value) {
            sourceMetrics.recordDisabled(SOURCE_WALLHAVEN)
            state.update {
                it.copy(
                    error = wallhavenDisabledMessage(),
                    errorSource = WallpaperTab.SEARCH.name,
                    isLoading = false,
                )
            }
            return
        }
        scope.launch {
            state.update { it.copy(selectedTab = WallpaperTab.SEARCH, query = "Random", wallpapers = emptyList(), isLoading = true, currentPage = 1) }
            try {
                val result = wallpaperRepo.getRandomWallhaven()
                state.update { it.copy(wallpapers = result.items, isLoading = false, hasMore = false) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun searchByPickedColor(colorInt: Int) {
        val hex = String.format(java.util.Locale.ROOT, "%06x", colorInt and 0xFFFFFF)
        searchByColor(hex)
    }

    fun matchMyTheme() {
        scope.launch {
            try {
                val color = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                val hex = if (color) {
                    val accent = context.getColor(android.R.color.system_accent1_500)
                    String.format(java.util.Locale.ROOT, "%06x", accent and 0xFFFFFF)
                } else {
                    "424153"
                }
                searchByColor(hex)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                searchByColor("424153")
            }
        }
    }

    fun searchByColor(hex: String) {
        if (!wallhavenProviderEnabled.value) {
            sourceMetrics.recordDisabled(SOURCE_WALLHAVEN)
            state.update {
                it.copy(
                    error = wallhavenDisabledMessage(),
                    errorSource = WallpaperTab.COLOR.name,
                    isLoading = false,
                )
            }
            return
        }
        val returnTab = state.value.selectedTab
            .takeIf { it != WallpaperTab.SEARCH && it != WallpaperTab.COLOR }
            ?: state.value.browseTab
        scope.launch {
            state.update {
                it.copy(
                    selectedTab = WallpaperTab.COLOR,
                    browseTab = returnTab,
                    selectedColor = hex,
                    query = "",
                    wallpapers = emptyList(),
                    isLoading = true,
                    currentPage = 1,
                )
            }
            try {
                val result = wallpaperRepo.searchByColor(hex)
                state.update { it.copy(wallpapers = result.items, isLoading = false, hasMore = result.hasMore) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    internal suspend fun resolveWallpaperSelection(
        id: String,
        source: ContentSource? = null,
        fullUrl: String? = null,
    ): Pair<Wallpaper, List<Wallpaper>>? {
        val shared = selectedContent.wallpaperList.value
        val sharedAnchorKey = selectedContent.wallpaperListAnchorKey.value
        selectedContent.selectedWallpaper.value
            ?.takeIf { matchesWallpaperIdentity(it, id, source, fullUrl) }
            ?.let {
                return it to if (shared.isNotEmpty() && sharedAnchorKey == it.stableKey()) {
                    shared
                } else {
                    listOf(it)
                }
            }

        shared.firstOrNull { matchesWallpaperIdentity(it, id, source, fullUrl) }?.let {
            return it to if (shared.isNotEmpty() && sharedAnchorKey == it.stableKey()) {
                shared
            } else {
                listOf(it)
            }
        }

        val current = state.value.wallpapers
        current.firstOrNull { matchesWallpaperIdentity(it, id, source, fullUrl) }?.let {
            return it to current
        }

        val topVotedWallpapers = topVoted.value.map { pair -> pair.first }
        topVotedWallpapers.firstOrNull { matchesWallpaperIdentity(it, id, source, fullUrl) }?.let {
            return it to topVotedWallpapers
        }

        dailyPick.value?.takeIf { matchesWallpaperIdentity(it, id, source, fullUrl) }?.let {
            return it to listOf(it)
        }

        (source?.let {
            favoritesRepo.getByIdentity(
                FavoriteIdentity(
                    id = id,
                    source = it.name,
                    type = "WALLPAPER",
                )
            )
        } ?: favoritesRepo.getLatestByIdAndType(id, "WALLPAPER"))
            ?.takeIf { it.type == "WALLPAPER" }
            ?.toWallpaper()
            ?.takeIf { matchesWallpaperIdentity(it, id, source, fullUrl) }
            ?.let {
                return it to listOf(it)
            }

        cacheManager.getByIds(listOf(id)).firstOrNull {
            matchesWallpaperIdentity(it, id, source, fullUrl)
        }?.let {
            return it to listOf(it)
        }

        return null
    }

    private companion object {
        const val SOURCE_WALLHAVEN = "wallhaven"
    }
}

private fun wallhavenDisabledMessage(): String = "Wallhaven source is disabled in Settings"
