package com.freevibe.ui.screens.sounds

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.util.rethrowIfCancelled
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.SoundAction
import com.freevibe.data.model.SoundActionDecision
import com.freevibe.data.model.Sound
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.sourceUnavailableReasonForFailure
import com.freevibe.data.model.soundLicenseCapabilities
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.UploadRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.remote.toSound
import com.freevibe.service.AudioPlaybackManager
import com.freevibe.service.AudioPreviewCache
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.CommunityAudioRecorder
import com.freevibe.service.DownloadManager
import com.freevibe.service.SeasonalContentManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
import com.freevibe.service.SourceMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class SoundsUiState(
    val sounds: List<Sound> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val selectedTab: SoundTab = SoundTab.RINGTONES,
    val playingId: String? = null,
    val resolvingId: String? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
    val filterKey: Int = 0,
    val isUploading: Boolean = false,
    val uploadProgress: Float = 0f,
    val searchReturnTab: SoundTab = SoundTab.RINGTONES,
    val qualityFilter: SoundQualityFilter = SoundQualityFilter.BEST,
    val isRecordingUpload: Boolean = false,
    val recordingStartedAtMs: Long = 0L,
    val recordedUploadUri: Uri? = null,
    val isRecordingPersonal: Boolean = false,
    val personalRecordingUri: Uri? = null,
    val degradedSources: Set<String> = emptySet(),
)

enum class SoundTab { RINGTONES, NOTIFICATIONS, ALARMS, YOUTUBE, COMMUNITY, SEARCH }

@HiltViewModel
class SoundsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val favoritesRepo: FavoritesRepository,
    private val soundApplier: SoundApplier,
    private val downloadManager: DownloadManager,
    private val selectedContent: SelectedContentHolder,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val audioTrimmer: com.freevibe.service.AudioTrimmer,
    private val prefs: PreferencesManager,
    val voteRepo: VoteRepository,
    private val reportRepo: CommunityReportRepository,
    private val communityBlockRepo: CommunityBlockRepository,
    private val bundledContent: BundledContentProvider,
    private val audioPlaybackManager: AudioPlaybackManager,
    private val audioPreviewCache: AudioPreviewCache,
    val uploadRepo: UploadRepository,
    private val soundUrlResolver: SoundUrlResolver,
    private val seasonalContentManager: SeasonalContentManager,
    private val communityAudioRecorder: CommunityAudioRecorder,
    private val sourceMetrics: SourceMetrics,
) : ViewModel() {

    private val _state = MutableStateFlow(SoundsUiState())
    val state = _state.asStateFlow()

    /** Non-null only when a seasonal theme is currently active (holiday, summer, etc.). */
    val seasonalTheme = seasonalContentManager.currentTheme()

    val selectedSound = selectedContent.selectedSound

    val autoPreview = prefs.autoPreviewSounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val previewVolume = prefs.soundPreviewVolume.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)
    val youtubeProviderEnabled = prefs.youtubeProviderEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val communityProviderEnabled = prefs.communityProviderEnabled.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PreferencesManager.DEFAULT_COMMUNITY_PROVIDER_ENABLED,
    )
    val communityGuidelinesAccepted = prefs.communityGuidelinesAccepted.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _previewReadyIds = MutableStateFlow<Set<String>>(emptySet())
    val previewReadyIds = _previewReadyIds.asStateFlow()

    val recentSearches = searchHistoryRepo.getRecentSoundSearches(8)
        .map { list -> list.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _topHits = MutableStateFlow<List<Sound>>(emptyList())
    val topHits = _topHits.asStateFlow()

    private var loadJob: Job? = null
    private var communityJob: Job? = null
    private val ytResolveSemaphore = Semaphore(6)

    private val titleBlocklist = Regex(
        "hindi|telugu|pack|trending|popular|\\bnew\\b|\\btop\\b|\\bbest\\b|timer|countdown|quiz|comparison|tutorial|how to|turn on|turn off|notification spam",
        RegexOption.IGNORE_CASE,
    )
    private val WORD_SPLIT_REGEX = Regex("[^a-zA-Z0-9]+")
    private val YOUTUBE_ID_PATTERNS = listOf(
        Regex("""(?:youtube\.com/watch\?.*v=|youtu\.be/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""),
        Regex("""^([a-zA-Z0-9_-]{11})$"""),
    )

    private val _communityUploads = MutableStateFlow<List<Sound>>(emptyList())
    val communityUploads = _communityUploads.asStateFlow()

    internal val community = SoundCommunityActions(
        voteRepo = voteRepo,
        reportRepo = reportRepo,
        communityBlockRepo = communityBlockRepo,
        uploadRepo = uploadRepo,
        communityAudioRecorder = communityAudioRecorder,
        communityProviderEnabled = communityProviderEnabled,
        communityGuidelinesAccepted = communityGuidelinesAccepted,
        state = _state,
        topHits = _topHits,
        communityUploads = _communityUploads,
        scope = viewModelScope,
        onStopIfPlaying = ::stopIfPlaying,
    )

    val hiddenIds = community.hiddenIds

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    internal val playback = SoundPlaybackActions(
        audioPlaybackManager = audioPlaybackManager,
        audioPreviewCache = audioPreviewCache,
        selectedContent = selectedContent,
        youtubeProviderEnabled = youtubeProviderEnabled,
        autoPreview = autoPreview,
        previewVolume = previewVolume,
        state = _state,
        topHits = _topHits,
        communityUploads = _communityUploads,
        previewReadyIds = _previewReadyIds,
        playbackProgress = _playbackProgress,
        scope = viewModelScope,
        resolveYouTubePreview = { sound ->
            val videoId = sound.youtubeVideoId() ?: return@SoundPlaybackActions null
            youtubeRepo.getAudioPreviewUrl(videoId)
        },
        shouldRefreshYouTubePreview = ::shouldRefreshYouTubePreview,
        youtubeDisabledMessage = ::youtubeDisabledMessage,
    )

    init {
        community.init()
        loadSounds()
        fetchTopHits()
        viewModelScope.launch {
            sourceMetrics.version.collect {
                _state.update { s -> s.copy(degradedSources = sourceMetrics.degradedSources()) }
            }
        }
        // Sync playingId from AudioPlaybackManager
        viewModelScope.launch {
            audioPlaybackManager.currentSoundId.collect { soundId ->
                _state.update {
                    it.copy(
                        playingId = soundId,
                        resolvingId = if (soundId == null) null else it.resolvingId,
                    )
                }
                if (soundId == null) _playbackProgress.value = 0f
            }
        }
        viewModelScope.launch {
            audioPlaybackManager.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    _state.update { it.copy(resolvingId = null) }
                }
            }
        }
    }

    // -- Top 5 This Week --

    private fun fetchTopHits() {
        viewModelScope.launch {
            try {
                if (!isYouTubeProviderEnabled()) {
                    val fallbackHits = rankSounds(
                        sounds = bundledContent.getRingtones(),
                        tab = SoundTab.RINGTONES,
                        filter = SoundQualityFilter.BEST,
                    ).take(5)
                    _topHits.value = fallbackHits
                    schedulePreviewPrebuffer(fallbackHits)
                    return@launch
                }
                val blocked = try {
                    prefs.ytSoundBlockedWords.first()
                        .split(",").map { it.trim() }.filter { it.isNotBlank() }
                } catch (e: Exception) {
                    e.rethrowIfCancelled()
                    emptyList()
                }

                val queries = PreferencesManager.defaultTopHitQueries()
                val allHits = mutableListOf<Sound>()
                val seenFingerprints = mutableSetOf<String>()
                for (q in queries) {
                    if (allHits.size >= 5) break
                    try {
                        val result = youtubeRepo.searchSounds(
                            query = q, maxDuration = 40, minDuration = 8,
                            blockedWords = blocked,
                        )
                        result.items
                            .filter { !titleBlocklist.containsMatchIn(it.name) }
                            .forEach {
                                if (seenFingerprints.add(soundFingerprint(it)) && allHits.size < 5) {
                                    allHits.add(it)
                                }
                            }
                    } catch (e: Exception) {
                        e.rethrowIfCancelled()
                    }
                }
                currentCoroutineContext().ensureActive()
                val rankedHits = rankSounds(allHits, SoundTab.RINGTONES, SoundQualityFilter.BEST).take(5)
                _topHits.value = rankedHits
                schedulePreviewPrebuffer(rankedHits)

                // Pre-resolve preview URLs
                supervisorScope {
                    allHits.forEach { hit ->
                        launch {
                            ytResolveSemaphore.acquire()
                            try {
                                youtubeRepo.getAudioPreviewUrl(hit.id.removePrefix("yt_"))?.let { url ->
                                    currentCoroutineContext().ensureActive()
                                    cacheResolvedPreview(hit, url)
                                }
                            } catch (e: Exception) {
                                e.rethrowIfCancelled()
                            } finally { ytResolveSemaphore.release() }
                        }
                    }
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
            }
        }
    }

    // -- Tab & Search --

    private fun nextFilterKey() = _state.value.filterKey + 1

    fun setQualityFilter(filter: SoundQualityFilter) {
        val currentTab = _state.value.selectedTab
        var rankedSounds: List<Sound> = emptyList()
        _state.update {
            rankedSounds = rankSounds(it.sounds, currentTab, filter)
            it.copy(
                qualityFilter = filter,
                sounds = rankedSounds,
                filterKey = nextFilterKey(),
            )
        }
        schedulePreviewPrebuffer(rankedSounds)
    }

    fun selectTab(tab: SoundTab) {
        stopPlayback()
        communityJob?.cancel()
        if (tab == SoundTab.YOUTUBE && !youtubeProviderEnabled.value) {
            sourceMetrics.recordDisabled(SOURCE_YOUTUBE)
            redirectToRingtones()
            return
        }
        if (tab == SoundTab.COMMUNITY && !communityProviderEnabled.value) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            redirectToRingtones()
            return
        }
        if (tab == SoundTab.COMMUNITY && !communityGuidelinesAccepted.value) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            redirectToRingtones()
            _state.update { it.copy(error = communityDisabledMessage()) }
            return
        }
        _state.update {
            it.copy(
                selectedTab = tab, query = "", sounds = emptyList(),
                currentPage = 1, hasMore = true, error = null, filterKey = nextFilterKey(),
                isRefreshing = false,
                searchReturnTab = if (tab == SoundTab.SEARCH) it.searchReturnTab else tab,
            )
        }
        when (tab) {
            SoundTab.COMMUNITY -> loadCommunityTab()
            SoundTab.YOUTUBE -> loadDefaultYouTube()
            else -> loadSounds()
        }
    }

    private fun redirectToRingtones() {
        communityJob?.cancel()
        loadJob?.cancel()
        _state.update {
            it.copy(
                selectedTab = SoundTab.RINGTONES, query = "", sounds = emptyList(),
                currentPage = 1, hasMore = true, error = null, filterKey = nextFilterKey(),
                isRefreshing = false,
                searchReturnTab = SoundTab.RINGTONES,
            )
        }
        loadSounds()
    }

    private fun showCommunityDisabledContent() {
        sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
        community.showCommunityDisabledContent()
    }

    private fun communityActionBlocked(): Boolean = community.communityActionBlocked()

    fun search(query: String) {
        if (query.isBlank()) return
        stopPlayback()
        val returnTab = _state.value.selectedTab.takeIf { it != SoundTab.SEARCH } ?: _state.value.searchReturnTab
        _state.update {
            it.copy(
                query = query, selectedTab = SoundTab.SEARCH,
                sounds = emptyList(), currentPage = 1, hasMore = true, filterKey = nextFilterKey(),
                searchReturnTab = returnTab,
            )
        }
        viewModelScope.launch { searchHistoryRepo.addSoundSearch(query) }
        loadSounds()
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        if (!youtubeProviderEnabled.value) {
            _state.update { it.copy(error = youtubeDisabledMessage()) }
            return
        }
        stopPlayback()
        _state.update {
            it.copy(
                query = query, selectedTab = SoundTab.YOUTUBE,
                sounds = emptyList(), currentPage = 1, hasMore = true,
                filterKey = nextFilterKey(), isLoading = true, error = null,
                isRefreshing = false,
                searchReturnTab = SoundTab.YOUTUBE,
            )
        }
        viewModelScope.launch { searchHistoryRepo.addSoundSearch(query) }
        executeYouTubeSearch(query)
    }

    fun importYouTubeUrl(url: String) {
        if (!youtubeProviderEnabled.value) {
            _state.update { it.copy(error = youtubeDisabledMessage()) }
            return
        }
        val videoId = extractYouTubeId(url)
        if (videoId == null) {
            _state.update { it.copy(error = "Not a valid YouTube URL") }
            return
        }
        stopPlayback()
        _state.update {
            it.copy(
                selectedTab = SoundTab.YOUTUBE, isLoading = true, error = null,
                sounds = emptyList(), filterKey = nextFilterKey(),
            )
        }
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val service = org.schabi.newpipe.extractor.NewPipe.getService(
                        org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId
                    )
                    val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                    extractor.fetchPage()
                    extractor
                }
                val sound = Sound(
                    id = "yt_$videoId",
                    source = ContentSource.YOUTUBE,
                    name = info.name ?: "YouTube Video",
                    description = "by ${info.uploaderName ?: "Unknown"}",
                    previewUrl = "",
                    downloadUrl = "",
                    duration = info.length.toDouble(),
                    tags = emptyList(),
                    license = "YouTube",
                    uploaderName = info.uploaderName ?: "Unknown",
                    sourcePageUrl = "https://www.youtube.com/watch?v=$videoId",
                )
                _state.update { it.copy(sounds = listOf(sound), isLoading = false) }
                youtubeRepo.getAudioPreviewUrl(videoId)?.let {
                    cacheResolvedPreview(sound, it)
                }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _state.update { it.copy(isLoading = false, error = "Could not load video: ${e.message}") }
            }
        }
    }

    private fun extractYouTubeId(url: String): String? {
        val trimmed = url.trim()
        for (p in YOUTUBE_ID_PATTERNS) {
            p.find(trimmed)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    fun removeSearch(query: String) {
        viewModelScope.launch { searchHistoryRepo.removeSearch(query, "SOUND") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepo.clearSoundHistory() }
    }

    fun clearSearchMode() {
        stopPlayback()
        val returnTab = when (val tab = _state.value.searchReturnTab) {
            SoundTab.YOUTUBE -> if (youtubeProviderEnabled.value) tab else SoundTab.RINGTONES
            SoundTab.COMMUNITY -> if (communityProviderEnabled.value) tab else SoundTab.RINGTONES
            else -> tab
        }
        communityJob?.cancel()
        _state.update {
            it.copy(
                selectedTab = returnTab,
                query = "",
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
                searchReturnTab = returnTab,
                filterKey = nextFilterKey(),
            )
        }
        when (returnTab) {
            SoundTab.COMMUNITY -> loadCommunityTab()
            SoundTab.YOUTUBE -> if (youtubeProviderEnabled.value) loadDefaultYouTube() else selectTab(SoundTab.RINGTONES)
            else -> loadSounds()
        }
    }

    fun clearYouTubeSearch() {
        stopPlayback()
        if (youtubeProviderEnabled.value) loadDefaultYouTube() else selectTab(SoundTab.RINGTONES)
    }

    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.isLoadingMore || !s.hasMore) return
        _state.update { it.copy(currentPage = it.currentPage + 1) }
        loadSounds(loadMore = true)
    }

    fun refresh() {
        stopPlayback()
        when (val tab = _state.value.selectedTab) {
            SoundTab.COMMUNITY -> {
                if (!communityProviderEnabled.value) {
                    selectTab(SoundTab.RINGTONES)
                    return
                }
                _state.update { it.copy(isRefreshing = true, error = null) }
                loadCommunityTab(isRefresh = true)
            }
            SoundTab.YOUTUBE -> {
                if (!youtubeProviderEnabled.value) {
                    selectTab(SoundTab.RINGTONES)
                    return
                }
                val query = _state.value.query
                if (query.isBlank()) {
                    loadDefaultYouTube(isRefresh = true)
                } else {
                    _state.update { it.copy(isRefreshing = true, error = null) }
                    executeYouTubeSearch(query)
                }
            }
            else -> {
                _state.update { it.copy(isRefreshing = true, currentPage = 1, error = null) }
                loadSounds(isRefresh = true)
                if (tab == SoundTab.RINGTONES) fetchTopHits()
            }
        }
    }

    // -- Playback --

    fun selectSound(sound: Sound) { selectedContent.selectSound(sound) }

    suspend fun resolveSound(
        id: String,
        source: ContentSource? = null,
        previewUrl: String? = null,
        downloadUrl: String? = null,
    ): Sound? {
        selectedContent.selectedSound.value
            ?.takeIf { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }
            ?.let { return it }

        _state.value.sounds.firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        _communityUploads.value.firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        _topHits.value.firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        listOf(
            bundledContent.getRingtones(),
            bundledContent.getNotifications(),
            bundledContent.getAlarms(),
        ).flatten().firstOrNull { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }?.let { return it }

        (source?.let {
            favoritesRepo.getByIdentity(
                FavoriteIdentity(
                    id = id,
                    source = it.name,
                    type = "SOUND",
                )
            )
        } ?: favoritesRepo.getLatestByIdAndType(id, "SOUND"))
            ?.takeIf { it.type == "SOUND" }
            ?.toSound()
            ?.takeIf { matchesSoundIdentity(it, id, source, previewUrl, downloadUrl) }
            ?.let { return it }

        return null
    }

    suspend fun ensureSelectedSound(
        id: String,
        source: ContentSource? = null,
        previewUrl: String? = null,
        downloadUrl: String? = null,
    ): Boolean {
        val resolved = resolveSound(id, source, previewUrl, downloadUrl) ?: return false
        selectedContent.selectSound(resolved)
        return true
    }

    // -- Playback operations delegated to SoundPlaybackActions --

    fun togglePlayback(sound: Sound) = playback.togglePlayback(sound)
    fun seekTo(fraction: Float) = playback.seekTo(fraction)
    fun stopIfPlaying(sound: Sound) = playback.stopIfPlaying(sound)
    private fun stopPlayback() = playback.stopPlayback()
    private fun schedulePreviewPrebuffer(sounds: List<Sound>) = playback.schedulePreviewPrebuffer(sounds)
    private fun cacheResolvedPreview(sound: Sound, previewUrl: String) = playback.cacheResolvedPreview(sound, previewUrl)

    // -- Apply & Download --

    fun applySound(sound: Sound, type: ContentType, confirmed: Boolean = false) {
        viewModelScope.launch {
            soundActionGateMessage(sound, SoundAction.APPLY, confirmed)?.let { message ->
                _state.update { it.copy(isApplying = false, error = message) }
                return@launch
            }
            if (!soundApplier.canWriteSettings()) {
                _state.update {
                    it.copy(
                        isApplying = false,
                        error = "System settings access is required before applying sounds.",
                    )
                }
                return@launch
            }
            _state.update { it.copy(isApplying = true, applySuccess = null) }
            val url = resolveDownloadUrl(sound)
                ?: run {
                    _state.update { it.copy(isApplying = false, error = "Could not resolve audio") }
                    return@launch
                }
            soundApplier.downloadAndApply(url, sound.name, type)
                .onSuccess {
                    val label = when (type) {
                        ContentType.RINGTONE -> "ringtone"
                        ContentType.NOTIFICATION -> "notification sound"
                        ContentType.ALARM -> "alarm sound"
                        else -> "sound"
                    }
                    _state.update { it.copy(isApplying = false, applySuccess = "Set as $label") }
                }
                .onFailure { e ->
                    markSoundSourceUnavailableIfRemoved(sound, e)
                    _state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun downloadSound(sound: Sound, confirmed: Boolean = false) {
        viewModelScope.launch {
            soundActionGateMessage(sound, SoundAction.DOWNLOAD, confirmed)?.let { message ->
                _state.update { it.copy(error = message) }
                return@launch
            }
            val dlUrl = resolveDownloadUrl(sound) ?: run {
                _state.update { it.copy(error = "Could not resolve audio stream URL") }
                return@launch
            }
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3").lowercase(java.util.Locale.ROOT)
            downloadManager.downloadSound(
                id = sound.stableKey(), url = dlUrl,
                fileName = buildSoundDownloadFileName(sound, ext),
                type = currentDownloadType(),
                source = sound.source.name,
            ).fold(
                onSuccess = { _state.update { it.copy(applySuccess = "Download started") } },
                onFailure = { error ->
                    markSoundSourceUnavailableIfRemoved(sound, error)
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    fun canWriteSettings(): Boolean = soundApplier.canWriteSettings()
    fun canOpenWriteSettings(): Boolean = soundApplier.canOpenWriteSettings()
    fun requestWriteSettings() = soundApplier.requestWriteSettings()

    fun toggleFavorite(sound: Sound) {
        viewModelScope.launch {
            val entity = sound.toFavoriteEntity()
            val isFav = favoritesRepo.isFavorite(sound.favoriteIdentity()).first()
            favoritesRepo.toggle(entity, isFav)
            _state.update { it.copy(applySuccess = if (isFav) "Removed from favorites" else "Added to favorites") }
        }
    }

    private fun buildSoundDownloadFileName(sound: Sound, extension: String): String =
        "Aura_${sound.source.name.lowercase(java.util.Locale.ROOT)}_${sound.id}_${sound.name.take(24)}.$extension"

    fun isFavorite(sound: Sound): Flow<Boolean> = favoritesRepo.isFavorite(sound.favoriteIdentity())

    private fun soundActionGateMessage(sound: Sound, action: SoundAction, confirmed: Boolean): String? {
        val capability = sound.soundLicenseCapabilities().capability(action)
        return when (capability.decision) {
            SoundActionDecision.ALLOWED -> null
            SoundActionDecision.CONFIRMATION_REQUIRED -> capability.reason.takeUnless { confirmed }
            SoundActionDecision.DISABLED -> capability.reason
        }
    }

    private suspend fun markSoundSourceUnavailableIfRemoved(sound: Sound, failure: Throwable) {
        sourceUnavailableReasonForFailure(sound.source, failure)?.let { reason ->
            favoritesRepo.markSourceUnavailable(sound.favoriteIdentity(), reason)
        }
    }

    private fun shouldRefreshYouTubePreview(sound: Sound): Boolean {
        if (!youtubeProviderEnabled.value) return false
        val videoId = sound.youtubeVideoId() ?: return false
        return sound.previewUrl.isBlank() || !youtubeRepo.isCached(videoId)
    }

    private suspend fun resolveDownloadUrl(sound: Sound): String? {
        val videoId = sound.youtubeVideoId()
        return if (videoId != null) {
            if (!isYouTubeProviderEnabled()) return null
            youtubeRepo.getAudioStreamUrl(videoId)
        } else {
            soundUrlResolver.resolve(sound)
        }
    }

    suspend fun loadSimilar(sound: Sound): List<Sound> {
        if (!isYouTubeProviderEnabled()) return emptyList()
        val keywords = sound.name.split(WORD_SPLIT_REGEX)
            .filter { it.length > 2 }.take(4).joinToString(" ")
        if (keywords.isBlank()) return emptyList()
        return try {
            val blocked = prefs.ytSoundBlockedWords.first()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val youtubeResults = youtubeRepo.searchSounds(
                query = "$keywords sound effect",
                minDuration = 0,
                maxDuration = 60,
                blockedWords = blocked,
            ).items.filter { it.stableKey() != sound.stableKey() }
            rankSounds(
                sounds = youtubeResults,
                tab = SoundTab.SEARCH,
                filter = SoundQualityFilter.BEST,
            ).take(10)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            emptyList()
        }
    }

    fun normalizeAudio(inputPath: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch { onResult(audioTrimmer.normalize(inputPath)) }
    }

    fun convertAudio(inputPath: String, targetFormat: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch { onResult(audioTrimmer.convert(inputPath, targetFormat)) }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    fun upvote(id: String) = community.upvote(id)
    fun downvote(id: String) = community.downvote(id)

    fun startCommunityRecording() = community.startRecording()
    fun stopCommunityRecording() = community.stopRecording()
    fun discardCommunityRecording() = community.discardRecording()
    fun consumeRecordedUpload() = community.consumeRecordedUpload()
    fun reportRecordingPermissionDenied() = community.reportRecordingPermissionDenied()

    fun startPersonalRecording() {
        if (_state.value.isRecordingPersonal) return
        communityAudioRecorder.start()
            .onSuccess {
                _state.update { it.copy(isRecordingPersonal = true, personalRecordingUri = null) }
            }
    }

    fun stopPersonalRecording() {
        if (!_state.value.isRecordingPersonal) return
        communityAudioRecorder.stop()
            .onSuccess { uri ->
                _state.update { it.copy(isRecordingPersonal = false, personalRecordingUri = uri) }
            }
            .onFailure {
                _state.update { it.copy(isRecordingPersonal = false) }
            }
    }

    fun cancelPersonalRecording() {
        communityAudioRecorder.cancel()
        _state.update { it.copy(isRecordingPersonal = false, personalRecordingUri = null) }
    }

    fun consumePersonalRecording() {
        _state.update { it.copy(personalRecordingUri = null) }
    }

    override fun onCleared() {
        loadJob?.cancel()
        playback.cancelProgress()
        communityJob?.cancel()
        community.cancelOnCleared()
        audioPlaybackManager.stop()
        super.onCleared()
    }

    // -- Main Load --

    private fun loadSounds(loadMore: Boolean = false, isRefresh: Boolean = false) {
        val tab = _state.value.selectedTab
        if (tab == SoundTab.YOUTUBE || tab == SoundTab.COMMUNITY) return

        if (!loadMore) loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val s = _state.value
            val loadTab = s.selectedTab
            if (loadTab == SoundTab.SEARCH && s.query.isBlank()) {
                _state.update {
                    it.copy(
                        sounds = emptyList(),
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = false,
                        error = null,
                    )
                }
                return@launch
            }
            if (!isRefresh && !loadMore) {
                _state.update { it.copy(isLoading = true, error = null) }
            } else if (loadMore) {
                _state.update { it.copy(isLoadingMore = true) }
            }

            if (!isYouTubeProviderEnabled()) {
                if (loadMore) {
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMore = false,
                        )
                    }
                    return@launch
                }
                val fallbackSounds = rankSounds(
                    sounds = bundledSoundsFor(loadTab),
                    tab = loadTab,
                    filter = _state.value.qualityFilter,
                )
                _state.update {
                    it.copy(
                        sounds = fallbackSounds,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = false,
                        error = if (loadTab == SoundTab.SEARCH) youtubeDisabledMessage() else null,
                    )
                }
                schedulePreviewPrebuffer(fallbackSounds)
                return@launch
            }

            val allResults = mutableListOf<Sound>()
            val resultLock = Any()
            val seenKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val seenFingerprints = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val firstFailure = AtomicReference<Exception?>(null)

            if (loadMore) {
                s.sounds.forEach { sound ->
                    seenKeys.add(sound.stableKey())
                    seenFingerprints.add(soundFingerprint(sound))
                }
            }

            fun addUnique(sound: Sound): Boolean {
                if (sound.source !in ACTIVE_SOUND_SOURCES) return false
                if (titleBlocklist.containsMatchIn(sound.name)) return false
                val fingerprint = soundFingerprint(sound)
                return if (seenKeys.add(sound.stableKey()) && seenFingerprints.add(fingerprint)) {
                    synchronized(resultLock) { allResults.add(sound) }
                    true
                } else {
                    false
                }
            }

            suspend fun flushToUi() {
                currentCoroutineContext().ensureActive()
                _state.update { st ->
                    val snapshot = rankSounds(
                        sounds = synchronized(resultLock) { allResults.toList() },
                        tab = loadTab,
                        filter = st.qualityFilter,
                    )
                    val existingKeys = st.sounds.mapTo(mutableSetOf()) { it.stableKey() }
                    st.copy(
                        sounds = if (loadMore) {
                            st.sounds + snapshot.filter { snd -> existingKeys.add(snd.stableKey()) }
                        } else {
                            snapshot
                        },
                    )
                }
                schedulePreviewPrebuffer(_state.value.sounds)
            }

            fun noteFailure(error: Exception) {
                firstFailure.compareAndSet(null, error)
            }

            try {
                if (loadMore) {
                    _state.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMore = false,
                        )
                    }
                    return@launch
                }

                val queries = buildQueries(s)
                val (cappedMin, cappedMax) = tabDurationRange(s)

                supervisorScope {
                    if (queries.ytQueries.isNotEmpty()) {
                        val blocked = try {
                            prefs.ytSoundBlockedWords.first()
                                .split(",").map { it.trim() }.filter { it.isNotBlank() }
                        } catch (e: Exception) {
                            e.rethrowIfCancelled()
                            emptyList()
                        }

                        queries.ytQueries.forEach { ytQ ->
                            launch {
                                try {
                                    val result = youtubeRepo.searchSounds(
                                        query = ytQ, maxDuration = cappedMax,
                                        minDuration = cappedMin, blockedWords = blocked,
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    if (added) flushToUi()

                                    result.items.forEach { yt ->
                                        launch {
                                            ytResolveSemaphore.acquire()
                                            try {
                                                youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))?.let { url ->
                                                    currentCoroutineContext().ensureActive()
                                                    cacheResolvedPreview(yt, url)
                                                }
                                            } catch (e: Exception) {
                                                e.rethrowIfCancelled()
                                            } finally { ytResolveSemaphore.release() }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.rethrowIfCancelled()
                                    noteFailure(e)
                                }
                            }
                        }
                    }
                }

                currentCoroutineContext().ensureActive()
                val combined = rankSounds(
                    sounds = synchronized(resultLock) { allResults.toList() },
                    tab = loadTab,
                    filter = _state.value.qualityFilter,
                )
                val preserveCurrentFeed = !loadMore && combined.isEmpty() && s.sounds.isNotEmpty()
                val surfacedError = firstFailure.get()
                    ?.takeIf { combined.isEmpty() }
                    ?.let(::categorizeError)
                var visibleSoundsAfterLoad: List<Sound> = emptyList()
                _state.update {
                    val nextSounds = when {
                        loadMore -> {
                            val existingKeys = it.sounds.mapTo(mutableSetOf()) { sound -> sound.stableKey() }
                            it.sounds + combined.filter { snd -> existingKeys.add(snd.stableKey()) }
                        }
                        preserveCurrentFeed -> it.sounds
                        else -> combined
                    }
                    visibleSoundsAfterLoad = nextSounds
                    it.copy(
                        sounds = nextSounds,
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = false,
                        error = when {
                            preserveCurrentFeed && surfacedError != null -> "$surfacedError. Showing your last good results."
                            else -> surfacedError
                        },
                    )
                }
                schedulePreviewPrebuffer(visibleSoundsAfterLoad)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = it.hasMore,
                        error = if (it.sounds.isNotEmpty()) {
                            "${categorizeError(e)}. Showing your last good results."
                        } else {
                            categorizeError(e)
                        },
                    )
                }
            }
        }
    }

    // -- Query Building --

    private data class QuerySet(
        val ytQueries: List<String>,
    )

    private suspend fun buildQueries(s: SoundsUiState): QuerySet {
        if (!isYouTubeProviderEnabled()) return QuerySet(emptyList())

        fun compactQueries(vararg queries: String): List<String> =
            queries.map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(3)

        val ytRingQ = prefs.ytSoundQueryRingtones.first()
            .ifBlank { PreferencesManager.defaultRingtoneQuery() }
        val ytNotifQ = prefs.ytSoundQueryNotifications.first()
            .ifBlank { PreferencesManager.defaultNotificationQuery() }
        val ytAlarmQ = prefs.ytSoundQueryAlarms.first()
            .ifBlank { PreferencesManager.defaultAlarmQuery() }

        return when (s.selectedTab) {
            SoundTab.RINGTONES -> QuerySet(
                ytQueries = compactQueries(ytRingQ, "phone ringtone sound effect", "classic phone ringtones"),
            )
            SoundTab.NOTIFICATIONS -> QuerySet(
                ytQueries = compactQueries(ytNotifQ, "notification sound effect short", "phone notification sound effect"),
            )
            SoundTab.ALARMS -> QuerySet(
                ytQueries = compactQueries(ytAlarmQ, "alarm sound effect short", "alarm clock sound effect"),
            )
            SoundTab.YOUTUBE -> QuerySet(emptyList())
            SoundTab.COMMUNITY -> QuerySet(emptyList())
            SoundTab.SEARCH -> QuerySet(
                ytQueries = compactQueries(s.query, "${s.query} sound effect", "${s.query} ringtone"),
            )
        }
    }

    private fun bundledSoundsFor(tab: SoundTab): List<Sound> = when (tab) {
        SoundTab.RINGTONES -> bundledContent.getRingtones()
        SoundTab.NOTIFICATIONS -> bundledContent.getNotifications()
        SoundTab.ALARMS -> bundledContent.getAlarms()
        else -> emptyList()
    }

    private fun tabDurationRange(s: SoundsUiState): Pair<Int, Int> = when (s.selectedTab) {
        SoundTab.RINGTONES -> 5 to 45
        SoundTab.NOTIFICATIONS -> 0 to 8
        SoundTab.ALARMS -> 5 to 60
        SoundTab.YOUTUBE -> 0 to 600
        SoundTab.COMMUNITY -> 0 to 600
        SoundTab.SEARCH -> 0 to 60
    }

    private fun currentDownloadType(tab: SoundTab = _state.value.selectedTab): ContentType = when (tab) {
        SoundTab.NOTIFICATIONS -> ContentType.NOTIFICATION
        SoundTab.ALARMS -> ContentType.ALARM
        else -> ContentType.RINGTONE
    }

    // -- Community Uploads --

    private fun loadCommunityTab(isRefresh: Boolean = false) {
        communityJob?.cancel()
        if (communityActionBlocked()) {
            showCommunityDisabledContent()
            return
        }
        _state.update {
            if (isRefresh) it.copy(isRefreshing = true, error = null)
            else it.copy(isLoading = true, error = null)
        }
        communityJob = viewModelScope.launch {
            val timeoutJob = launch {
                kotlinx.coroutines.delay(10_000L)
                val state = _state.value
                if (state.isLoading || state.isRefreshing) {
                    _state.update { it.copy(isLoading = false, isRefreshing = false, error = "Community uploads timed out") }
                }
            }
            try {
                uploadRepo.getCommunityUploads(limit = 50).collect { sounds ->
                    timeoutJob.cancel()
                    var rankedSounds: List<Sound> = emptyList()
                    _state.update {
                        rankedSounds = rankSounds(sounds, SoundTab.COMMUNITY, it.qualityFilter)
                        it.copy(
                            sounds = rankedSounds,
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = false,
                        )
                    }
                    schedulePreviewPrebuffer(rankedSounds)
                }
                // Flow completed without emitting (empty community tab). Clear loading and
                // kill the timeout so structured concurrency doesn't block the parent launch
                // waiting for the 10 s delay to fire a spurious "timed out" error.
                _state.update { it.copy(isLoading = false, isRefreshing = false) }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            } finally {
                timeoutJob.cancel()
            }
        }
    }

    private fun loadDefaultYouTube(isRefresh: Boolean = false) {
        if (!youtubeProviderEnabled.value) {
            selectTab(SoundTab.RINGTONES)
            return
        }
        loadJob?.cancel()
        _state.update {
            it.copy(
                selectedTab = SoundTab.YOUTUBE,
                sounds = if (isRefresh) it.sounds else emptyList(),
                currentPage = 1,
                hasMore = false,
                error = null,
                isLoading = !isRefresh,
                isLoadingMore = false,
                isRefreshing = isRefresh,
                filterKey = nextFilterKey(),
                searchReturnTab = SoundTab.YOUTUBE,
            )
        }
        loadJob = viewModelScope.launch {
            val query = defaultYouTubeQuery()
            _state.update { it.copy(query = query) }
            runYouTubeSearch(
                query = query,
                minDuration = 5,
                maxDuration = 45,
                rankTab = SoundTab.RINGTONES,
            )
        }
    }

    private suspend fun defaultYouTubeQuery(): String =
        prefs.ytSoundQueryRingtones.first().trim()
            .takeIf { it.isNotBlank() }
            ?: PreferencesManager.defaultRingtoneQuery()

    private fun executeYouTubeSearch(query: String) {
        if (!youtubeProviderEnabled.value) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasMore = false,
                    error = youtubeDisabledMessage(),
                )
            }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            runYouTubeSearch(query)
        }
    }

    private suspend fun runYouTubeSearch(
        query: String,
        minDuration: Int = 0,
        maxDuration: Int = 600,
        rankTab: SoundTab = SoundTab.YOUTUBE,
    ) {
        try {
            if (!isYouTubeProviderEnabled()) {
                _state.update {
                    it.copy(
                        sounds = emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = false,
                        error = youtubeDisabledMessage(),
                    )
                }
                return
            }
            val blocked = try {
                prefs.ytSoundBlockedWords.first()
                    .split(",").map { it.trim() }.filter { it.isNotBlank() }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                emptyList()
            }

            val result = youtubeRepo.searchSounds(
                query = query,
                maxDuration = maxDuration,
                minDuration = minDuration,
                blockedWords = blocked,
            )
            var rankedSounds: List<Sound> = emptyList()
            _state.update {
                rankedSounds = rankSounds(result.items, rankTab, it.qualityFilter)
                it.copy(
                    sounds = rankedSounds,
                    isLoading = false,
                    isRefreshing = false,
                    // We do not support paginating the YouTube tab yet, so avoid advertising
                    // "more" when the generic loadMore() path cannot service it.
                    hasMore = false,
                )
            }
            schedulePreviewPrebuffer(rankedSounds)

            supervisorScope {
                result.items.forEach { yt ->
                    launch {
                        ytResolveSemaphore.acquire()
                        try {
                            youtubeRepo.getAudioPreviewUrl(yt.id.removePrefix("yt_"))?.let { url ->
                                currentCoroutineContext().ensureActive()
                                cacheResolvedPreview(yt, url)
                            }
                        } catch (e: Exception) {
                            e.rethrowIfCancelled()
                        } finally {
                            ytResolveSemaphore.release()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = categorizeError(e),
                )
            }
        }
    }

    private fun categorizeError(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "No internet connection"
        is java.net.SocketTimeoutException -> "Connection timed out — try again"
        is java.net.ConnectException -> "Could not connect to server"
        else -> e.message ?: "Something went wrong"
    }

    private suspend fun isYouTubeProviderEnabled(): Boolean = prefs.youtubeProviderEnabled.first()

    private fun youtubeDisabledMessage(): String = "YouTube features are disabled in Settings"

    private fun communityDisabledMessage(): String = community.communityDisabledMessage()

    fun acceptCommunityGuidelines() =
        viewModelScope.launch { prefs.acceptCommunityGuidelines() }

    fun uploadSound(
        localUri: android.net.Uri,
        name: String,
        category: String,
        tags: List<String> = emptyList(),
        rights: CommunityUploadRights,
    ) = community.uploadSound(localUri, name, category, tags, rights)

    suspend fun canDeleteCommunitySound(sound: Sound): Boolean = community.canDeleteSound(sound)

    fun deleteCommunitySound(sound: Sound) = community.deleteSound(sound)

    fun reportSound(sound: Sound, reason: CommunityReportReason, note: String = "") =
        community.reportSound(sound, reason, note)

    fun canBlockCommunitySound(sound: Sound): Boolean = community.canBlockSound(sound)

    fun blockCommunitySound(sound: Sound, onBlocked: () -> Unit = {}) =
        community.blockSound(sound, onBlocked)

    private companion object {
        const val FIRST_VISIBLE_PREVIEW_COUNT = 5
        const val SOURCE_YOUTUBE = "youtube"
        const val SOURCE_COMMUNITY = "community"
        val ACTIVE_SOUND_SOURCES = setOf(
            ContentSource.YOUTUBE,
            ContentSource.BUNDLED,
        )
    }
}

private fun Sound.youtubeVideoId(): String? =
    takeIf { source == ContentSource.YOUTUBE }
        ?.id
        ?.removePrefix("yt_")
        ?.takeIf { it.isNotBlank() && it != id }

internal fun matchesSoundIdentity(
    sound: Sound,
    id: String,
    source: ContentSource? = null,
    previewUrl: String? = null,
    downloadUrl: String? = null,
): Boolean {
    if (sound.id != id) return false
    if (source != null && sound.source != source) return false
    if (!previewUrl.isNullOrBlank() && sound.previewUrl != previewUrl) return false
    if (!downloadUrl.isNullOrBlank() && sound.downloadUrl != downloadUrl) return false
    return true
}
