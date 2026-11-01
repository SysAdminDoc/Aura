package com.freevibe.ui.screens.videowallpapers

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.VideoFavoriteStream
import com.freevibe.data.model.VideoWallpaperAction
import com.freevibe.data.model.canUseVideoAction
import com.freevibe.data.model.toVideoFavoriteEntity
import com.freevibe.data.model.toVideoWallpaperItem
import com.freevibe.data.model.videoActionMessage
import com.freevibe.data.model.videoFavoriteIdentity
import com.freevibe.data.model.videoFavoriteStream
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.HiddenItemLabel
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_ZOOM
import com.freevibe.service.VideoPreviewCache
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.VideoWallpaperService
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.YouTubeYtDlpRequestFactory
import com.freevibe.service.YtDlpUpdateManager
import com.freevibe.service.shouldPrebufferVideoPreview
import com.freevibe.ui.LiveWallpaperSource
import com.freevibe.ui.liveWallpaperBlock
import com.freevibe.ui.liveWallpaperBlockedMessage
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@HiltViewModel
class VideoWallpapersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val redditRepo: RedditRepository,
    private val pexelsApi: PexelsApi,
    private val pixabayApi: com.freevibe.data.remote.pixabay.PixabayApi,
    private val prefs: PreferencesManager,
    private val okHttpClient: OkHttpClient,
    private val videoWallpaperStorage: VideoWallpaperStorage,
    private val sourceMetrics: SourceMetrics,
    private val ytDlpUpdateManager: YtDlpUpdateManager,
    private val ytDlpRequestFactory: YouTubeYtDlpRequestFactory,
    private val videoPreviewCache: VideoPreviewCache,
    private val av1CodecSupport: com.freevibe.service.Av1CodecSupport,
    val voteRepo: VoteRepository,
    private val reportRepo: CommunityReportRepository,
    private val favoritesRepo: FavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoWallpapersState())
    val state = _state.asStateFlow()
    private val _gallerySelectionResult = MutableStateFlow<VideoWallpaperSelectionResult?>(null)
    val gallerySelectionResult = _gallerySelectionResult.asStateFlow()
    val videoWallpaperPresentation = prefs.videoWallpaperPresentation.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL,
    )
    val videoFitCanvasMode = prefs.videoFitCanvasMode.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        FitCanvasMode.AMOLED_BLACK.preferenceValue,
    )
    val videoFitCanvasColor = prefs.videoFitCanvasColor.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.freevibe.data.model.DEFAULT_FIT_CANVAS_COLOR,
    )
    /** Report goes to the community queue, so it's only offered while that provider is on, never before that's known. */
    val communityReportsEnabled = flow { emitAll(prefs.communityProviderEnabled) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _resolvedIds = MutableStateFlow<Set<String>>(emptySet())
    val resolvedIds = _resolvedIds.asStateFlow()

    /** Videos whose stream couldn't be found, so the preview says so instead of spinning. */
    private val _streamFailedIds = MutableStateFlow<Set<String>>(emptySet())
    val streamFailedIds = _streamFailedIds.asStateFlow()

    /** IDs of the videos saved under Favorites, so a card and the full-screen preview show which are. */
    val favoriteVideoIds = favoritesRepo.getVideos()
        .map { favorites -> favorites.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Cache of resolved video stream URLs
    // Bounded cache — evict oldest when exceeding 200 entries. Eviction must also
    // un-mark the id as resolved: a resolved id with no cached URL renders a
    // permanent spinner and misroutes Apply into the yt-dlp re-resolve path.
    private val streamUrls = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            val evict = size > 200
            if (evict && eldest != null) {
                _resolvedIds.update { ids -> ids - eldest.key }
            }
            return evict
        }
    }.let { java.util.Collections.synchronizedMap(it) }
    private val previewResolveInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val loadLock = Any()
    private val feedStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    private val feed = VideoFeedActions(
        context = context,
        youtubeRepo = youtubeRepo,
        redditRepo = redditRepo,
        pexelsApi = pexelsApi,
        pixabayApi = pixabayApi,
        prefs = prefs,
        okHttpClient = okHttpClient,
        sourceMetrics = sourceMetrics,
        state = _state,
        resolvedIds = _resolvedIds,
        streamUrls = streamUrls,
        ensureStreamResolved = ::ensureStreamResolved,
        rememberStreamUrl = ::rememberStreamUrl,
    )
    private val downloads = VideoDownloadActions(
        context = context,
        youtubeRepo = youtubeRepo,
        prefs = prefs,
        okHttpClient = okHttpClient,
        videoWallpaperStorage = videoWallpaperStorage,
        sourceMetrics = sourceMetrics,
        ytDlpUpdateManager = ytDlpUpdateManager,
        ytDlpRequestFactory = ytDlpRequestFactory,
        av1CodecSupport = av1CodecSupport,
        state = _state,
        streamUrls = streamUrls,
        scope = viewModelScope,
        rememberStreamUrl = ::rememberStreamUrl,
    )

    /**
     * Loads the feed the first time the Videos tab shows, and reports whether this call started it.
     * Favorites borrows this class to preview, apply and download a saved video. It has no feed to
     * show, so it never calls this and no provider is searched on its behalf.
     */
    fun startFeed(): Boolean {
        if (!feedStarted.compareAndSet(false, true)) return false
        load()
        return true
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun refresh() {
        _state.update {
            it.copy(
                isRefreshing = true,
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                pixabayPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        load()
    }

    fun loadMore() {
        load(loadMore = true)
    }

    fun setOrientation(orientation: OrientationFilter) {
        _state.update {
            it.copy(
                orientation = orientation,
                items = emptyList(),
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                // pixabayPage too: a stale high page against a new filter/query makes
                // Pixabay return HTTP 400 (page out of range) until pull-to-refresh.
                pixabayPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        streamUrls.clear()
        previewResolveInFlight.clear()
        _resolvedIds.value = emptySet()
        _streamFailedIds.value = emptySet()
        load()
    }

    fun setFocusFilter(filter: VideoFocusFilter) {
        _state.update {
            it.copy(
                focusFilter = filter,
                items = rankVideoWallpapers(it.items, filter, it.orientation),
            )
        }
    }

    fun search(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                items = emptyList(),
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                pixabayPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        streamUrls.clear()
        previewResolveInFlight.clear()
        _resolvedIds.value = emptySet()
        _streamFailedIds.value = emptySet()
        load()
    }

    fun getStreamUrl(id: String): String? = streamUrls[id]

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun previewMediaSourceFactory() = videoPreviewCache.mediaSourceFactory()

    fun ensureStreamResolved(item: VideoWallpaperItem) {
        if (!canPreviewVideoInCurrentArtifact(item)) {
            streamUrls.remove(item.id)
            _resolvedIds.update { it - item.id }
            _streamFailedIds.update { it + item.id }
            return
        }
        val cachedUrl = streamUrls[item.id]
        if (cachedUrl != null) {
            prebufferPreview(item, cachedUrl)
            return
        }
        if (item.source != "YouTube" || item.videoId.isBlank() || !previewResolveInFlight.add(item.id)) return
        viewModelScope.launch {
            try {
                val url = youtubeRepo.getVideoStreamUrl(item.videoId)
                if (url != null) {
                    rememberStreamUrl(item, url)
                    prebufferPreview(item, url)
                } else {
                    _streamFailedIds.update { it + item.id }
                }
            } catch (e: Throwable) {
                e.rethrowIfCancelled()
                _streamFailedIds.update { it + item.id }
            } finally {
                previewResolveInFlight.remove(item.id)
            }
        }
    }

    /**
     * Finds a saved video's stream again so the preview, Apply and Download can use it.
     *
     * An address that stays valid (Reddit's playlist rebuilt from the clip ID, a provider's file) is
     * known at once. A YouTube stream address expires within hours, so it is resolved from the video
     * ID by [ensureStreamResolved], the way the feed does it.
     */
    fun resolveSavedVideo(favorite: FavoriteEntity) {
        val item = favorite.toVideoWallpaperItem() ?: return
        val stream = favorite.videoFavoriteStream()
        if (stream is VideoFavoriteStream.Direct && streamUrls[item.id] == null) {
            rememberStreamUrl(item, stream.url)
        }
        ensureStreamResolved(item)
    }

    /**
     * Saves [item] under Favorites, or removes it when it's already there. [onChange] hears what
     * happened so the screen can say so. A video is saved with what finds its stream again (the
     * provider, its ID and its page), never with a stream address that expires.
     */
    fun toggleFavorite(item: VideoWallpaperItem, onChange: (VideoFavoriteChange) -> Unit = {}) {
        viewModelScope.launch {
            val identity = item.videoFavoriteIdentity()
            val change = if (favoritesRepo.getByIdentity(identity) != null) {
                favoritesRepo.remove(identity)
                VideoFavoriteChange.REMOVED
            } else {
                val favorite = item.toVideoFavoriteEntity(streamUrls[item.id])
                if (favorite == null) {
                    VideoFavoriteChange.UNAVAILABLE
                } else {
                    favoritesRepo.add(favorite)
                    VideoFavoriteChange.ADDED
                }
            }
            onChange(change)
        }
    }

    private fun prebufferPreview(item: VideoWallpaperItem, url: String) {
        if (!canPreviewVideoInCurrentArtifact(item) || !shouldPrebufferVideoPreview(url)) return
        viewModelScope.launch {
            runCatching { videoPreviewCache.prebuffer(item.id, url) }
        }
    }

    fun upvote(id: String) { viewModelScope.launch { voteRepo.upvote(id) } }
    fun downvote(id: String, label: HiddenItemLabel? = null) { viewModelScope.launch { voteRepo.downvote(id, label) } }
    fun restoreHidden(ids: Collection<String>) = voteRepo.unhideLocally(ids)
    fun undoDownvote(id: String) { viewModelScope.launch { voteRepo.undoDownvote(id) } }
    fun clearGallerySelectionResult() { _gallerySelectionResult.value = null }

    fun setVideoFitCanvasPreferences(presentation: String, style: FitCanvasStyle) {
        viewModelScope.launch { prefs.setVideoFitCanvasPreferences(presentation, style) }
    }

    fun prepareGalleryVideoWallpaper(uri: android.net.Uri) {
        viewModelScope.launch {
            _gallerySelectionResult.value = VideoWallpaperSelectionResult.Preparing
            val result = videoWallpaperStorage.prepareFromUri(uri)
            _gallerySelectionResult.value = result.fold(
                onSuccess = { VideoWallpaperSelectionResult.Ready },
                onFailure = {
                    VideoWallpaperSelectionResult.Failure(it.message ?: context.getString(com.freevibe.R.string.video_wp_prepare_failed))
                },
            )
        }
    }

    fun applyVideoWallpaper(
        item: VideoWallpaperItem,
        scaleMode: String = VIDEO_WALLPAPER_SCALE_MODE_ZOOM,
        canvasStyle: FitCanvasStyle = FitCanvasStyle(),
    ) {
        if (_state.value.isApplying != null) return
        if (item.id in _state.value.downloadingIds) {
            Toast.makeText(context, context.getString(com.freevibe.R.string.video_wp_download_in_progress), Toast.LENGTH_SHORT).show()
            return
        }
        if (!item.canUseVideoAction(VideoWallpaperAction.APPLY)) {
            Toast.makeText(
                context,
                item.videoActionMessage(VideoWallpaperAction.APPLY),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        // Checked before the download: a video can only ever be a live wallpaper.
        liveWallpaperBlock(context, ComponentName(context, VideoWallpaperService::class.java))?.let { block ->
            Toast.makeText(
                context,
                liveWallpaperBlockedMessage(context, block, LiveWallpaperSource.VIDEO),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        viewModelScope.launch { downloads.applyOriginal(item, scaleMode, canvasStyle) }
    }

    /**
     * Saves [item]'s original to Downloads. It's the managed copy Apply makes and reuses, kept under one
     * record per video, so downloading twice or applying afterwards never makes a second copy.
     */
    fun downloadVideo(item: VideoWallpaperItem) = downloads.downloadVideo(item)

    /** Sends [item] to the community moderation queue, the same service wallpaper and sound reports use. */
    fun reportVideo(item: VideoWallpaperItem, reason: CommunityReportReason, note: String) {
        viewModelScope.launch {
            val message = when {
                !prefs.communityProviderEnabled.first() ->
                    context.getString(com.freevibe.R.string.sound_feedback_community_disabled)
                !prefs.communityGuidelinesAccepted.first() ->
                    context.getString(com.freevibe.R.string.video_wp_report_guidelines)
                else -> reportRepo.submitReport(item.reportInput(reason, note, streamUrls[item.id])).fold(
                    onSuccess = { context.getString(com.freevibe.R.string.feedback_report_submitted) },
                    onFailure = { error ->
                        context.getString(
                            com.freevibe.R.string.feedback_report_failed,
                            error.message ?: context.getString(com.freevibe.R.string.settings_ytdlp_update_unknown_error),
                        )
                    },
                )
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onCleared() {
        synchronized(loadLock) {
            loadGeneration++
            loadJob?.cancel()
            loadJob = null
        }
        super.onCleared()
    }

    private fun load(loadMore: Boolean = false) {
        // A search or refresh starts the feed as well, so a later startFeed() doesn't load it twice.
        feedStarted.set(true)
        val job = synchronized(loadLock) {
            if (loadMore) {
                val current = _state.value
                val feedLoadPending = loadJob != null
                if (current.isLoading || current.isLoadingMore || !current.hasMore || feedLoadPending) {
                    return
                }
                _state.update { it.copy(isLoadingMore = true) }
            } else {
                loadJob?.cancel()
            }
            val generation = ++loadGeneration
            viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                feed.loadBatch(loadMore, this)
            } finally {
                // Only the CURRENT load may clear the flags: a cancelled load's finally
                // runs after its replacement already set isLoading = true, and clearing
                // here would flash the empty-state card for the whole new load.
                synchronized(loadLock) {
                    if (loadGeneration == generation) {
                        loadJob = null
                        _state.update {
                            it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false, redditWaiting = false)
                        }
                    }
                }
            }
            }.also { loadJob = it }
        }
        job.start()
    }

    private fun rememberStreamUrl(item: VideoWallpaperItem, url: String) {
        if (!canPreviewVideoInCurrentArtifact(item)) return
        streamUrls[item.id] = url
        _resolvedIds.update { it + item.id }
        _streamFailedIds.update { it - item.id }
    }
}
