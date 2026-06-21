package com.freevibe.ui.screens.wallpapers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.COMMUNITY_GUIDELINES_REQUIRED_MESSAGE
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.sanitizeCommunityOwnerKey
import com.freevibe.data.model.sourceUnavailableReasonForFailure
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.AiWallpaperRepository
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.data.repository.WallpaperUploadRepository
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.remote.toWallpaper
import com.freevibe.service.ApplyFeedbackBus
import com.freevibe.service.ApplyFeedbackEvent
import com.freevibe.service.ColorExtractor
import com.freevibe.service.DualWallpaperService
import com.freevibe.service.DownloadManager
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.SeasonalContentManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SourceMetrics
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class WallpapersUiState(
    val wallpapers: List<Wallpaper> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,       // #4: Pull-to-refresh
    val error: String? = null,
    val errorSource: String? = null,         // #5: Which source failed
    val query: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val selectedTab: WallpaperTab = WallpaperTab.DISCOVER,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
    val pendingLiveWallpaperLaunch: Boolean = false,  // true when parallax file is ready to launch picker
    val selectedColor: String? = null,       // #9: Color filter
    val topRange: String = "1M",             // Wallhaven toplist time range
    val discoverFilter: WallpaperDiscoverFilter = WallpaperDiscoverFilter.FOR_YOU,
    val browseTab: WallpaperTab = WallpaperTab.DISCOVER,
    val isUploadingWallpaper: Boolean = false,
    val wallpaperUploadProgress: Float = 0f,
    val degradedSources: Set<String> = emptySet(),
)

enum class WallpaperTab { DISCOVER, PEXELS, PIXABAY, REDDIT, WALLHAVEN, COMMUNITY, COLOR, SEARCH }

@HiltViewModel
class WallpapersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallpaperRepo: WallpaperRepository,
    private val redditRepo: RedditRepository,
    private val favoritesRepo: FavoritesRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val downloadManager: DownloadManager,
    private val dualWallpaperService: DualWallpaperService,
    private val collectionRepo: CollectionRepository,
    private val selectedContent: SelectedContentHolder,
    private val historyManager: WallpaperHistoryManager,
    private val offlineFavorites: OfflineFavoritesManager,
    private val aiWallpaperRepository: AiWallpaperRepository,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val prefs: PreferencesManager,
    private val colorExtractor: ColorExtractor,
    private val cacheManager: com.freevibe.data.local.WallpaperCacheManager,
    private val applyFeedbackBus: ApplyFeedbackBus,
    val voteRepo: VoteRepository,
    private val reportRepo: CommunityReportRepository,
    private val communityBlockRepo: CommunityBlockRepository,
    private val seasonalContentManager: SeasonalContentManager,
    private val wallpaperUploadRepo: WallpaperUploadRepository,
    private val sourceMetrics: SourceMetrics,
) : ViewModel() {

    private val _state = MutableStateFlow(WallpapersUiState())
    val state = _state.asStateFlow()

    /** Non-null only when a seasonal theme is currently active (holiday, summer, etc.). */
    val seasonalTheme = seasonalContentManager.currentTheme()

    private var loadJob: Job? = null
    private var lastRouteQuery: String? = null
    private var lastRouteColor: String? = null
    private var lastRouteSimilarId: String? = null
    private var lastRouteSimilarSource: String? = null
    private var lastRouteSimilarFullUrl: String? = null
    private var hasInitiallyLoaded = false

    val selectedWallpaper = selectedContent.selectedWallpaper
    val sharedWallpaperList = selectedContent.wallpaperList
    val sharedWallpaperListAnchorKey = selectedContent.wallpaperListAnchorKey

    // #9: Grid columns preference
    val gridColumns = prefs.wallpaperGridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)
    val wallhavenProviderEnabled = prefs.wallhavenProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val redditProviderEnabled = prefs.redditProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val pexelsProviderEnabled = prefs.pexelsProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val pixabayProviderEnabled = prefs.pixabayProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val communityProviderEnabled = prefs.communityProviderEnabled.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesManager.DEFAULT_COMMUNITY_PROVIDER_ENABLED,
    )
    val communityGuidelinesAccepted = prefs.communityGuidelinesAccepted.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val generatedContentProviderEnabled =
        prefs.generatedContentProviderEnabled.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PreferencesManager.DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED,
        )

    val recentSearches = searchHistoryRepo.getRecentWallpaperSearches(8)
        .map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteIdentities = favoritesRepo.allIdentities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Daily wallpaper pick from active non-retired sources. */
    private val _dailyPick = MutableStateFlow<Wallpaper?>(null)
    val dailyPick = _dailyPick.asStateFlow()

    /** Top community-upvoted wallpapers (resolved from cache) */
    private val _topVoted = MutableStateFlow<List<Pair<Wallpaper, Int>>>(emptyList())
    val topVoted = _topVoted.asStateFlow()

    internal val applyActions = WallpaperApplyActions(
        wallpaperApplier = wallpaperApplier,
        downloadManager = downloadManager,
        dualWallpaperService = dualWallpaperService,
        historyManager = historyManager,
        favoritesRepo = favoritesRepo,
        offlineFavorites = offlineFavorites,
        aiWallpaperRepository = aiWallpaperRepository,
        applyFeedbackBus = applyFeedbackBus,
        state = _state,
        scope = viewModelScope,
    )

    internal val searchActions = WallpaperSearchActions(
        context = context,
        wallpaperRepo = wallpaperRepo,
        favoritesRepo = favoritesRepo,
        selectedContent = selectedContent,
        cacheManager = cacheManager,
        sourceMetrics = sourceMetrics,
        wallhavenProviderEnabled = wallhavenProviderEnabled,
        state = _state,
        topVoted = _topVoted,
        dailyPick = _dailyPick,
        scope = viewModelScope,
    )

    internal val community = WallpaperCommunityActions(
        voteRepo = voteRepo,
        reportRepo = reportRepo,
        communityBlockRepo = communityBlockRepo,
        wallpaperUploadRepo = wallpaperUploadRepo,
        cacheManager = cacheManager,
        prefs = prefs,
        sourceMetrics = sourceMetrics,
        communityProviderEnabled = communityProviderEnabled,
        communityGuidelinesAccepted = communityGuidelinesAccepted,
        state = _state,
        topVoted = _topVoted,
        scope = viewModelScope,
        fetchTopVoted = ::fetchTopVoted,
    )

    init {
        fetchDailyPick()
        fetchTopVoted()
        viewModelScope.launch {
            sourceMetrics.version.collect {
                _state.update { s -> s.copy(degradedSources = sourceMetrics.degradedSources()) }
            }
        }
    }

    private fun fetchTopVoted(seedWallpapers: List<Wallpaper> = emptyList()) {
        viewModelScope.launch {
            try {
                if (!isCommunityProviderEnabled()) {
                    sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
                    _topVoted.value = emptyList()
                    return@launch
                }
                val topIds = withTimeoutOrNull(5000L) { voteRepo.getTopVotedIds(50) } ?: return@launch
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Top voted IDs from Firebase: ${topIds.size} entries, first=${topIds.firstOrNull()}")
                if (topIds.isEmpty()) return@launch

                val allIds = topIds.flatMap { (voteKey, _) -> extractWallpaperLookupIds(voteKey) }.distinct()
                val cachedWallpapers = cacheManager.getByIds(allIds)
                val resolvedIds = (seedWallpapers + cachedWallpapers).map { it.id }.toSet()
                val missingCommunityKeys = allIds
                    .filter { it.startsWith("cw_") && it !in resolvedIds }
                    .map { it.removePrefix("cw_") }
                    .toSet()
                val remoteWallpapers = if (missingCommunityKeys.isNotEmpty()) {
                    wallpaperUploadRepo.fetchWallpapersByKeys(missingCommunityKeys)
                } else {
                    emptyList()
                }
                val wallpapers = (seedWallpapers + cachedWallpapers + remoteWallpapers).distinctBy { it.stableKey() }
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Resolved ${wallpapers.size} wallpapers (${remoteWallpapers.size} hydrated from RTDB) for ${allIds.size} ID variants")

                val voteMap = topIds.toMap()
                val ambiguousLegacyIds = wallpapers
                    .groupBy { it.id }
                    .filterValues { matches -> matches.size > 1 }
                    .keys
                val sorted = wallpapers
                    .mapNotNull { wp ->
                        resolveWallpaperVoteCount(
                            wallpaper = wp,
                            voteMap = voteMap,
                            ambiguousLegacyIds = ambiguousLegacyIds,
                            sanitizeKey = voteRepo::sanitizeKey,
                        )?.let { wp to it }
                    }
                    .distinctBy { it.first.stableKey() }
                    .sortedByDescending { it.second }
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.d("WallpapersVM", "Final top voted: ${sorted.size} wallpapers, top=${sorted.firstOrNull()?.let { "${it.first.id}=${it.second}" }}")
                _topVoted.value = sorted
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (com.freevibe.BuildConfig.DEBUG) android.util.Log.e("WallpapersVM", "fetchTopVoted failed: ${e.message}", e)
            }
        }
    }

    private fun fetchDailyPick() {
        viewModelScope.launch {
            try {
                _dailyPick.value = withTimeoutOrNull(5000L) { wallpaperRepo.getWallpaperOfTheDay() }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        colorExtractionJob?.cancel()
        super.onCleared()
    }

    fun handleRouteFilters(
        query: String?,
        color: String?,
        similarId: String? = null,
        similarSource: String? = null,
        similarFullUrl: String? = null,
    ) {
        val normalizedQuery = query?.ifBlank { null }
        val normalizedColor = color?.ifBlank { null }
        val normalizedSimilarId = similarId?.ifBlank { null }
        val normalizedSimilarSource = similarSource?.ifBlank { null }
        val normalizedSimilarFullUrl = similarFullUrl?.ifBlank { null }

        // Skip dedup only for non-initial calls with identical filters
        if (
            hasInitiallyLoaded &&
            normalizedQuery == lastRouteQuery &&
            normalizedColor == lastRouteColor &&
            normalizedSimilarId == lastRouteSimilarId &&
            normalizedSimilarSource == lastRouteSimilarSource &&
            normalizedSimilarFullUrl == lastRouteSimilarFullUrl
        ) return

        lastRouteQuery = normalizedQuery
        lastRouteColor = normalizedColor
        lastRouteSimilarId = normalizedSimilarId
        lastRouteSimilarSource = normalizedSimilarSource
        lastRouteSimilarFullUrl = normalizedSimilarFullUrl
        hasInitiallyLoaded = true

        val resolvedSimilarSource = normalizedSimilarSource?.let { sourceName ->
            runCatching { ContentSource.valueOf(sourceName) }.getOrNull()
        }

        when {
            normalizedQuery != null -> {
                if (_state.value.selectedTab != WallpaperTab.SEARCH || _state.value.query != normalizedQuery) {
                    search(normalizedQuery)
                }
            }
            normalizedColor != null -> {
                if (_state.value.selectedTab != WallpaperTab.COLOR || _state.value.selectedColor != normalizedColor) {
                    searchByColor(normalizedColor)
                }
            }
            normalizedSimilarId != null -> findSimilarById(
                wallpaperId = normalizedSimilarId,
                source = resolvedSimilarSource,
                fullUrl = normalizedSimilarFullUrl,
            )
            _state.value.wallpapers.isEmpty() && !_state.value.isLoading -> loadWallpapers()
        }
    }

    fun selectTab(tab: WallpaperTab) {
        val targetTab = if (isProviderDisabledTab(tab)) WallpaperTab.DISCOVER else tab
        if (targetTab == WallpaperTab.REDDIT) redditRepo.resetPagination()
        _state.update {
            it.copy(
                selectedTab = targetTab,
                browseTab = if (targetTab == WallpaperTab.SEARCH || targetTab == WallpaperTab.COLOR) it.browseTab else targetTab,
                query = "",
                wallpapers = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                errorSource = null,
                selectedColor = null,
            )
        }
        loadWallpapers()
    }

    fun setTopRange(range: String) {
        _state.update { it.copy(topRange = range, wallpapers = emptyList(), currentPage = 1, hasMore = true) }
        loadWallpapers()
    }

    fun setDiscoverFilter(filter: WallpaperDiscoverFilter) {
        viewModelScope.launch {
            val preferredResolution = prefs.preferredResolution.first()
            val userStyles = loadUserStyles()
            val ranked = rankWallpapers(
                wallpapers = _state.value.wallpapers,
                filter = filter,
                preferredResolution = preferredResolution,
                userStyles = userStyles,
            )
            _state.update {
                it.copy(
                    discoverFilter = filter,
                    wallpapers = ranked,
                )
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            clearActiveFilter()
            return
        }
        val returnTab = _state.value.selectedTab
            .takeIf { it != WallpaperTab.SEARCH && it != WallpaperTab.COLOR }
            ?: _state.value.browseTab
        _state.update {
            it.copy(
                query = query,
                selectedTab = WallpaperTab.SEARCH,
                browseTab = returnTab,
                selectedColor = null,
                wallpapers = emptyList(),
                currentPage = 1,
                hasMore = true,
            )
        }
        viewModelScope.launch { searchHistoryRepo.addWallpaperSearch(query) }
        loadWallpapers()
    }

    fun removeSearch(query: String) {
        viewModelScope.launch { searchHistoryRepo.removeSearch(query, "WALLPAPER") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepo.clearWallpaperHistory() }
    }

    // #9: Color-based search
    fun searchByColor(color: String) {
        if (color.isBlank()) {
            clearActiveFilter()
            return
        }
        searchActions.searchByColor(color)
    }

    fun clearActiveFilter() {
        val returnTab = if (isProviderDisabledTab(_state.value.browseTab)) WallpaperTab.DISCOVER else _state.value.browseTab
        if (returnTab == WallpaperTab.REDDIT) redditRepo.resetPagination()
        _state.update {
            it.copy(
                selectedTab = returnTab,
                query = "",
                selectedColor = null,
                wallpapers = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                errorSource = null,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
            )
        }
        loadWallpapers()
    }

    // #4: Pull-to-refresh
    fun refresh() {
        val tab = _state.value.selectedTab
        if (isProviderDisabledTab(tab)) {
            selectTab(WallpaperTab.DISCOVER)
            return
        }
        _state.update { it.copy(isRefreshing = true, currentPage = 1, error = null, errorSource = null) }
        if (tab == WallpaperTab.REDDIT) redditRepo.resetPagination()
        loadWallpapers(isRefresh = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        loadWallpapers(loadMore = true)
    }

    fun selectWallpaper(wallpaper: Wallpaper, wallpapers: List<Wallpaper> = _state.value.wallpapers) {
        selectedContent.selectWallpaper(wallpaper, wallpapers)
    }

    suspend fun resolveWallpaper(
        id: String,
        source: ContentSource? = null,
        fullUrl: String? = null,
    ): Wallpaper? = resolveWallpaperSelection(id, source, fullUrl)?.first

    suspend fun ensureSelectedWallpaper(
        id: String,
        source: ContentSource? = null,
        fullUrl: String? = null,
    ): Boolean {
        val resolved = resolveWallpaperSelection(id, source, fullUrl) ?: return false
        selectedContent.selectWallpaper(resolved.first, resolved.second.ifEmpty { listOf(resolved.first) })
        return true
    }

    /** Update selected wallpaper without overwriting the shared list (used by detail pager) */
    fun selectWallpaperOnly(wallpaper: Wallpaper) {
        selectedContent.updateSelectedWallpaper(wallpaper)
    }

    // -- Apply/Download/Favorite operations delegated to WallpaperApplyActions --

    val activeDownloads = applyActions.activeDownloads
    fun applyWallpaper(wallpaper: Wallpaper, target: WallpaperTarget) = applyActions.applyWallpaper(wallpaper, target)
    fun undoApply(entry: com.freevibe.data.model.WallpaperHistoryEntity) = applyActions.undoApply(entry)
    fun applySplitCrop(wallpaper: Wallpaper) = applyActions.applySplitCrop(wallpaper)
    fun applyParallax(wallpaper: Wallpaper) = applyActions.applyParallax(wallpaper)
    fun clearPendingLaunch() = applyActions.clearPendingLaunch()
    fun downloadWallpaper(wallpaper: Wallpaper) = applyActions.downloadWallpaper(wallpaper)
    fun dismissDownload(id: String) = applyActions.dismissDownload(id)
    fun toggleFavorite(wallpaper: Wallpaper) = applyActions.toggleFavorite(wallpaper)
    fun isFavorite(wallpaper: Wallpaper): Flow<Boolean> = applyActions.isFavorite(wallpaper)

    fun clearError() = _state.update { it.copy(error = null, errorSource = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    // -- Community operations delegated to WallpaperCommunityActions --

    val hiddenIds = community.hiddenIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun getVoteCount(contentId: String) = community.getVoteCount(contentId)
    fun upvote(contentId: String) = community.upvote(contentId)
    fun downvote(contentId: String) = community.downvote(contentId)
    fun reportWallpaper(wallpaper: Wallpaper, reason: CommunityReportReason, note: String = "") = community.reportWallpaper(wallpaper, reason, note)
    fun canBlockCommunityWallpaper(wallpaper: Wallpaper) = community.canBlockCommunityWallpaper(wallpaper)
    fun blockCommunityWallpaper(wallpaper: Wallpaper, onBlocked: () -> Unit = {}) = community.blockCommunityWallpaper(wallpaper, onBlocked)
    suspend fun canDeleteCommunityWallpaper(wallpaper: Wallpaper) = community.canDeleteCommunityWallpaper(wallpaper)
    fun deleteCommunityWallpaper(wallpaper: Wallpaper) = community.deleteCommunityWallpaper(wallpaper)
    fun uploadCommunityWallpaper(localUri: Uri, name: String, category: String, tags: List<String>, rights: CommunityUploadRights) =
        community.uploadCommunityWallpaper(localUri, name, category, tags, rights)

    // -- Collections --

    val collections = collectionRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createCollection(name: String, wallpaper: Wallpaper? = null) {
        viewModelScope.launch {
            val id = collectionRepo.create(name)
            wallpaper?.let { collectionRepo.addWallpaper(id, it) }
            _state.update { it.copy(applySuccess = "Created \"$name\"") }
        }
    }

    // -- Color extraction (Material You preview) --

    private val _colorPalette = MutableStateFlow<ColorExtractor.WallpaperPalette?>(null)
    val colorPalette = _colorPalette.asStateFlow()
    private var colorExtractionJob: Job? = null

    fun extractColors(wallpaperUrl: String) {
        // Cancel any stale extraction so back-to-back swipes don't leak results
        // and don't race with a later call's reset-to-null.
        colorExtractionJob?.cancel()
        _colorPalette.value = null
        colorExtractionJob = viewModelScope.launch {
            val palette = colorExtractor.extractFromUrl(wallpaperUrl)
            _colorPalette.value = palette
        }
    }

    fun applyRandom() {
        val wallpapers = _state.value.wallpapers
        val wp = wallpapers.randomOrNull() ?: return
        applyWallpaper(wp, WallpaperTarget.BOTH)
    }

    fun addToCollection(collectionId: Long, wallpaper: Wallpaper) {
        viewModelScope.launch {
            collectionRepo.addWallpaper(collectionId, wallpaper)
            _state.update { it.copy(applySuccess = "Added to collection") }
        }
    }

    private fun loadWallpapers(loadMore: Boolean = false, isRefresh: Boolean = false) {
        if (!loadMore) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            if (!isRefresh && !loadMore) {
                _state.update { it.copy(isLoading = true, error = null, errorSource = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }

            // Instant cache hit for Discover — show cached results immediately while refreshing
            if (s.selectedTab == WallpaperTab.DISCOVER && !loadMore && !isRefresh) {
                val cached = wallpaperRepo.getCachedDiscover(s.currentPage)
                val visibleCached = cached
                    ?.filter { it.source != ContentSource.WALLHAVEN || wallhavenProviderEnabled.value }
                    .orEmpty()
                if (visibleCached.isNotEmpty()) {
                    val preferredResolution = prefs.preferredResolution.first()
                    val userStyles = loadUserStyles()
                    val rankedCached = rankWallpapers(
                        wallpapers = visibleCached,
                        filter = _state.value.discoverFilter,
                        preferredResolution = preferredResolution,
                        userStyles = userStyles,
                    )
                    _state.update {
                        it.copy(
                            wallpapers = rankedCached,
                            hasMore = true,
                        )
                    }
                    // Keep isLoading = true — network request still in progress
                }
            }

            val currentTab = _state.value.selectedTab
            val currentPage = _state.value.currentPage
            try {
                val userStyles = loadUserStyles()
                if (currentTab == WallpaperTab.REDDIT && !isRedditProviderEnabled()) {
                    redditRepo.getMultiSubreddit()
                    _state.update {
                        it.copy(
                            wallpapers = emptyList(),
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            hasMore = false,
                            error = redditDisabledMessage(),
                            errorSource = WallpaperTab.REDDIT.name,
                        )
                    }
                    return@launch
                }
                if (isProviderDisabledTab(currentTab)) {
                    recordDisabledProvider(currentTab)
                    _state.update {
                        it.copy(
                            wallpapers = emptyList(),
                            isLoading = false,
                            isLoadingMore = false,
                            isRefreshing = false,
                            hasMore = false,
                            error = providerDisabledMessage(currentTab),
                            errorSource = currentTab.name,
                        )
                    }
                    return@launch
                }
                val result = when (currentTab) {
                    WallpaperTab.DISCOVER -> wallpaperRepo.getDiscover(
                        page = currentPage,
                        userStyles = userStyles,
                    )
                    WallpaperTab.PIXABAY -> wallpaperRepo.getPixabay(currentPage)
                    WallpaperTab.PEXELS -> wallpaperRepo.getPexelsCurated(currentPage)
                    WallpaperTab.REDDIT -> redditRepo.getMultiSubreddit()
                    WallpaperTab.WALLHAVEN -> wallpaperRepo.getWallhaven(page = currentPage, topRange = _state.value.topRange)
                    WallpaperTab.COMMUNITY -> wallpaperUploadRepo.getCommunityWallpapers()
                    WallpaperTab.SEARCH -> wallpaperRepo.searchAll(_state.value.query, page = currentPage)
                    WallpaperTab.COLOR -> wallpaperRepo.searchByColor(_state.value.selectedColor ?: "", currentPage)
                }
                val preferredResolution = prefs.preferredResolution.first()
                val activeFilter = if (currentTab == WallpaperTab.DISCOVER) _state.value.discoverFilter else WallpaperDiscoverFilter.FOR_YOU
                val combined = if (loadMore) _state.value.wallpapers + result.items else result.items
                val rankedWallpapers = if (currentTab == WallpaperTab.COMMUNITY) {
                    combined.distinctBy { it.stableKey() }
                } else {
                    rankWallpapers(
                        wallpapers = combined,
                        filter = activeFilter,
                        preferredResolution = preferredResolution,
                        userStyles = userStyles,
                    )
                }
                val preserveExistingDiscoverFeed =
                    currentTab == WallpaperTab.DISCOVER &&
                        !loadMore &&
                        result.items.isEmpty() &&
                        _state.value.wallpapers.isNotEmpty()
                _state.update {
                    it.copy(
                        wallpapers = if (preserveExistingDiscoverFeed) it.wallpapers else rankedWallpapers,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = result.hasMore,
                        error = null,
                        errorSource = null,
                    )
                }
                if (currentTab == WallpaperTab.DISCOVER && (!loadMore || _topVoted.value.isEmpty())) {
                    fetchTopVoted(result.items)
                }
                if (currentTab == WallpaperTab.COMMUNITY && result.items.isNotEmpty()) {
                    cacheManager.cache("community_wallpapers_$currentPage", result.items)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // #5: Source-specific error handling
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        error = categorizeError(e),
                        errorSource = currentTab.name,
                    )
                }
            }
        }
    }

    // -- Search/find-similar operations delegated to WallpaperSearchActions --

    fun findSimilar(wallpaper: Wallpaper) = searchActions.findSimilar(wallpaper)
    fun findSimilarById(wallpaperId: String, source: ContentSource? = null, fullUrl: String? = null) =
        searchActions.findSimilarById(wallpaperId, source, fullUrl)
    fun loadRandom() = searchActions.loadRandom()
    fun searchByTag(tagName: String) { search(tagName) }
    fun searchByPickedColor(colorInt: Int) = searchActions.searchByPickedColor(colorInt)
    fun matchMyTheme() = searchActions.matchMyTheme()

    internal suspend fun resolveWallpaperSelection(
        id: String,
        source: ContentSource? = null,
        fullUrl: String? = null,
    ) = searchActions.resolveWallpaperSelection(id, source, fullUrl)

    private fun categorizeError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection"
        is java.net.SocketTimeoutException -> "Connection timed out — try again"
        is java.net.ConnectException -> "Could not connect to server"
        is retrofit2.HttpException -> when (e.code()) {
            401, 403 -> "API key invalid or expired"
            404 -> "Content not found"
            429 -> "Rate limited — wait a moment and retry"
            in 500..599 -> "Server error — try again later"
            else -> "Service temporarily unavailable"
        }
        else -> e.message ?: "Failed to load wallpapers"
    }

    private suspend fun loadUserStyles(): List<String> =
        prefs.userStyles.first()
            .split(",")
            .map { it.trim().lowercase(java.util.Locale.ROOT) }
            .filter { it.isNotBlank() }

    private suspend fun isRedditProviderEnabled(): Boolean = false
    private suspend fun isCommunityProviderEnabled(): Boolean =
        prefs.communityProviderEnabled.first() && prefs.communityGuidelinesAccepted.first()

    private fun isProviderDisabledTab(tab: WallpaperTab): Boolean = when (tab) {
        WallpaperTab.WALLHAVEN -> !wallhavenProviderEnabled.value
        WallpaperTab.REDDIT -> true
        WallpaperTab.PEXELS -> !pexelsProviderEnabled.value
        WallpaperTab.PIXABAY -> !pixabayProviderEnabled.value
        WallpaperTab.COMMUNITY -> !communityProviderEnabled.value || !communityGuidelinesAccepted.value
        else -> false
    }

    private fun recordDisabledProvider(tab: WallpaperTab) {
        val source = when (tab) {
            WallpaperTab.WALLHAVEN -> SOURCE_WALLHAVEN
            WallpaperTab.PEXELS -> "pexels"
            WallpaperTab.PIXABAY -> "pixabay"
            WallpaperTab.COMMUNITY -> "community"
            else -> return
        }
        sourceMetrics.recordDisabled(source)
    }

    private fun redditDisabledMessage(): String =
        "Reddit source is discontinued. Saved Reddit items keep their metadata, but new Reddit feeds are off."

    private fun providerDisabledMessage(tab: WallpaperTab): String = when (tab) {
        WallpaperTab.WALLHAVEN -> wallhavenDisabledMessage()
        WallpaperTab.PEXELS -> "Pexels source is disabled in Settings"
        WallpaperTab.PIXABAY -> "Pixabay source is disabled in Settings"
        WallpaperTab.COMMUNITY -> community.communityDisabledMessage()
        else -> redditDisabledMessage()
    }

    private fun wallhavenDisabledMessage(): String = "Wallhaven source is disabled in Settings"

    fun acceptCommunityGuidelines() = community.acceptCommunityGuidelines()

    private fun communityActionBlocked(): Boolean {
        if (communityProviderEnabled.value && communityGuidelinesAccepted.value) return false
        sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
        _state.update { it.copy(error = community.communityDisabledMessage()) }
        return true
    }

    private companion object {
        const val SOURCE_WALLHAVEN = "wallhaven"
        const val SOURCE_COMMUNITY = "community"
    }
}

internal fun reportSourceUrl(primary: String, fallback: String): String =
    listOf(primary, fallback)
        .firstOrNull { it.startsWith("https://", ignoreCase = true) }
        .orEmpty()

internal fun Wallpaper.matchesCommunityUploader(uploaderId: String): Boolean =
    sanitizeCommunityOwnerKey(communityUploaderId).let { it.isNotBlank() && it == sanitizeCommunityOwnerKey(uploaderId) }

internal fun matchesWallpaperIdentity(
    wallpaper: Wallpaper,
    id: String,
    source: ContentSource? = null,
    fullUrl: String? = null,
): Boolean {
    if (wallpaper.id != id) return false
    if (source != null && wallpaper.source != source) return false
    return fullUrl.isNullOrBlank() || wallpaper.fullUrl == fullUrl
}

internal fun buildWallpaperDownloadFileName(
    wallpaper: Wallpaper,
    extension: String,
): String = "Aura_${wallpaper.source.name.lowercase(java.util.Locale.ROOT)}_${wallpaper.id}.$extension"

internal fun extractWallpaperLookupIds(voteKey: String): List<String> {
    if ("::" in voteKey && !voteKey.startsWith("WALLPAPER::")) return emptyList()
    val rawId = parseWallpaperVoteRawId(voteKey) ?: voteKey
    return listOf(rawId, rawId.replace("_", "."), rawId.replace("_", "/")).distinct()
}

internal fun parseWallpaperVoteRawId(voteKey: String): String? {
    val parts = voteKey.split("::", limit = 3)
    return if (parts.size == 3 && parts[0] == "WALLPAPER") parts[2] else null
}

internal fun resolveWallpaperVoteCount(
    wallpaper: Wallpaper,
    voteMap: Map<String, Int>,
    ambiguousLegacyIds: Set<String>,
    sanitizeKey: (String) -> String,
): Int? {
    val stableCandidates = listOf(wallpaper.stableKey(), sanitizeKey(wallpaper.stableKey())).distinct()
    stableCandidates.firstNotNullOfOrNull(voteMap::get)?.let { return it }

    if (wallpaper.id in ambiguousLegacyIds) return null

    val legacyCandidates = listOf(wallpaper.id, sanitizeKey(wallpaper.id)).distinct()
    return legacyCandidates.firstNotNullOfOrNull(voteMap::get)
}
