package com.freevibe.ui.screens.wallpapers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.freevibe.data.legal.ProviderSetup
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.DEFAULT_FIT_CANVAS_COLOR
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
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
import com.freevibe.service.ApplyFeedbackBus
import com.freevibe.service.ColorExtractor
import com.freevibe.service.DualWallpaperService
import com.freevibe.service.DownloadManager
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.SeasonalContentManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SourceMetrics
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperContentFingerprints
import com.freevibe.service.WallpaperHistoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    private val applyCoordinator: com.freevibe.service.WallpaperApplyCoordinator,
    val voteRepo: VoteRepository,
    private val reportRepo: CommunityReportRepository,
    private val communityBlockRepo: CommunityBlockRepository,
    private val seasonalContentManager: SeasonalContentManager,
    private val wallpaperUploadRepo: WallpaperUploadRepository,
    private val sourceMetrics: SourceMetrics,
    private val contentFingerprints: WallpaperContentFingerprints,
) : ViewModel() {

    private val _state = MutableStateFlow(WallpapersUiState())
    val state = _state.asStateFlow()

    /** Non-null only when a seasonal theme is currently active (holiday, summer, etc.). */
    val seasonalTheme = seasonalContentManager.currentTheme()

    // Null until the first route call, so the first one always loads.
    private var lastRouteFilters: WallpaperRouteFilters? = null

    val selectedWallpaper = selectedContent.selectedWallpaper
    val sharedWallpaperList = selectedContent.wallpaperList
    val sharedWallpaperListAnchorKey = selectedContent.wallpaperListAnchorKey

    // #9: Grid columns preference
    val gridColumns = prefs.wallpaperGridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)
    val staticWallpaperPresentation = prefs.staticWallpaperPresentation.stateIn(viewModelScope, SharingStarted.Eagerly, WALLPAPER_PRESENTATION_FILL)
    val staticFitCanvasMode = prefs.staticFitCanvasMode.stateIn(viewModelScope, SharingStarted.Eagerly, FitCanvasMode.AMOLED_BLACK.preferenceValue)
    val staticFitCanvasColor = prefs.staticFitCanvasColor.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_FIT_CANVAS_COLOR)
    val wallhavenProviderEnabled = prefs.wallhavenProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val redditProviderEnabled = prefs.redditProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val pexelsProviderEnabled = prefs.pexelsProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val pixabayProviderEnabled = prefs.pixabayProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val communityProviderEnabled = prefs.communityProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_COMMUNITY_PROVIDER_ENABLED)
    val communityGuidelinesAccepted = prefs.communityGuidelinesAccepted.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val providerSetup = prefs.providerSetup.stateIn(viewModelScope, SharingStarted.Eagerly, ProviderSetup())
    val generatedContentProviderEnabled = prefs.generatedContentProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, PreferencesManager.DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED)

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

    internal val browse = WallpaperBrowseViewModel(
        wallpaperRepo = wallpaperRepo,
        redditRepo = redditRepo,
        prefs = prefs,
        cacheManager = cacheManager,
        voteRepo = voteRepo,
        wallpaperUploadRepo = wallpaperUploadRepo,
        sourceMetrics = sourceMetrics,
        wallhavenProviderEnabled = wallhavenProviderEnabled,
        redditProviderEnabled = redditProviderEnabled,
        pexelsProviderEnabled = pexelsProviderEnabled,
        pixabayProviderEnabled = pixabayProviderEnabled,
        providerSetup = providerSetup,
        communityProviderEnabled = communityProviderEnabled,
        communityGuidelinesAccepted = communityGuidelinesAccepted,
        state = _state,
        topVoted = _topVoted,
        dailyPick = _dailyPick,
        scope = viewModelScope,
    )

    internal val styleActions = WallpaperStyleActions(
        context = context,
        prefs = prefs,
        browse = browse,
        state = _state,
        scope = viewModelScope,
    )

    internal val applyActions = WallpaperApplyActions(
        context = context,
        prefs = prefs,
        wallpaperApplier = wallpaperApplier,
        downloadManager = downloadManager,
        dualWallpaperService = dualWallpaperService,
        historyManager = historyManager,
        favoritesRepo = favoritesRepo,
        offlineFavorites = offlineFavorites,
        aiWallpaperRepository = aiWallpaperRepository,
        applyFeedbackBus = applyFeedbackBus,
        applyCoordinator = applyCoordinator,
        state = _state,
        scope = viewModelScope,
        onStyleSignal = styleActions::recordSignal,
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
        cancelBrowseLoad = browse::cancel,
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
        fetchTopVoted = browse::fetchTopVoted,
    )

    /** Thumbnail fingerprints the screen uses to show an image posted under two URLs once. */
    val contentFingerprintMap = contentFingerprints.known

    init {
        browse.start()
        viewModelScope.launch {
            _state.map { it.wallpapers }.distinctUntilChanged().collectLatest { contentFingerprints.fingerprint(it) }
        }
    }

    override fun onCleared() {
        browse.cancel()
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
        val filters = WallpaperRouteFilters.of(query, color, similarId, similarSource, similarFullUrl)
        // Skip dedup only for non-initial calls with identical filters
        if (filters == lastRouteFilters) return
        lastRouteFilters = filters

        when {
            filters.query != null -> {
                if (_state.value.selectedTab != WallpaperTab.SEARCH || _state.value.query != filters.query) {
                    search(filters.query)
                }
            }
            filters.color != null -> {
                if (_state.value.selectedTab != WallpaperTab.COLOR || _state.value.selectedColor != filters.color) {
                    searchByColor(filters.color)
                }
            }
            filters.similarId != null -> findSimilarById(
                wallpaperId = filters.similarId,
                source = filters.resolvedSimilarSource,
                fullUrl = filters.similarFullUrl,
            )
            _state.value.wallpapers.isEmpty() && !_state.value.isLoading -> browse.loadWallpapers()
        }
    }

    fun selectTab(tab: WallpaperTab) {
        val targetTab = if (browse.isProviderDisabledTab(tab)) WallpaperTab.DISCOVER else tab
        if (targetTab == WallpaperTab.REDDIT) redditRepo.resetPagination()
        searchActions.cancel()
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
        browse.loadWallpapers()
    }

    fun setTopRange(range: String) {
        _state.update { it.copy(topRange = range, wallpapers = emptyList(), currentPage = 1, hasMore = true) }
        browse.loadWallpapers()
    }

    fun setDiscoverFilter(filter: WallpaperDiscoverFilter) = styleActions.setDiscoverFilter(filter)

    fun search(query: String) {
        if (query.isBlank()) {
            clearActiveFilter()
            return
        }
        val returnTab = _state.value.selectedTab
            .takeIf { it != WallpaperTab.SEARCH && it != WallpaperTab.COLOR }
            ?: _state.value.browseTab
        searchActions.cancel()
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
        browse.loadWallpapers()
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
        val returnTab = if (browse.isProviderDisabledTab(_state.value.browseTab)) {
            WallpaperTab.DISCOVER
        } else {
            _state.value.browseTab
        }
        if (returnTab == WallpaperTab.REDDIT) redditRepo.resetPagination()
        searchActions.cancel()
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
        browse.loadWallpapers()
    }

    // #4: Pull-to-refresh
    fun refresh() {
        val tab = _state.value.selectedTab
        if (browse.isProviderDisabledTab(tab)) {
            selectTab(WallpaperTab.DISCOVER)
            return
        }
        _state.update { it.copy(isRefreshing = true, currentPage = 1, error = null, errorSource = null) }
        if (tab == WallpaperTab.DISCOVER || tab == WallpaperTab.REDDIT) {
            redditRepo.resetPagination(forceRefresh = true)
        }
        browse.loadWallpapers(isRefresh = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        browse.loadWallpapers(loadMore = true)
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
    fun skipWallpaper(wallpaper: Wallpaper) = styleActions.skipWallpaper(wallpaper)
    fun undoSkipWallpaper(wallpaper: Wallpaper) = styleActions.undoSkipWallpaper(wallpaper)
    fun resetWallpaperStyleLearning() = styleActions.resetStyleLearning()
    fun clearError() = _state.update { it.copy(error = null, errorSource = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    // -- Community operations delegated to WallpaperCommunityActions --

    val hiddenIds = community.hiddenIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    fun getVoteCount(contentId: String) = community.getVoteCount(contentId)
    fun upvote(contentId: String) = community.upvote(contentId)
    fun downvote(contentId: String) = community.downvote(contentId)
    fun undoDownvote(contentId: String) = viewModelScope.launch { voteRepo.undoDownvote(contentId) }
    fun reportWallpaper(wallpaper: Wallpaper, reason: CommunityReportReason, note: String = "") = community.reportWallpaper(wallpaper, reason, note)
    fun canBlockCommunityWallpaper(wallpaper: Wallpaper) = community.canBlockCommunityWallpaper(wallpaper)
    fun blockCommunityWallpaper(wallpaper: Wallpaper, onBlocked: () -> Unit = {}) = community.blockCommunityWallpaper(wallpaper, onBlocked)
    suspend fun canDeleteCommunityWallpaper(wallpaper: Wallpaper) = community.canDeleteCommunityWallpaper(wallpaper)
    fun deleteCommunityWallpaper(wallpaper: Wallpaper) = community.deleteCommunityWallpaper(wallpaper)
    fun uploadCommunityWallpaper(
        localUri: Uri,
        name: String,
        category: String,
        tags: List<String>,
        rights: CommunityUploadRights,
        isAiGenerated: Boolean = false,
    ) = community.uploadCommunityWallpaper(localUri, name, category, tags, rights, isAiGenerated)

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

    fun setStaticFitCanvasPreferences(presentation: String, style: FitCanvasStyle) = viewModelScope.launch { prefs.setStaticFitCanvasPreferences(presentation, style) }

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

    fun acceptCommunityGuidelines() = community.acceptCommunityGuidelines()
}
