package com.freevibe.ui.screens.sounds

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.remote.tiktok.isTikTokMediaUrlUsable
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.TikTokSoundRepository
import com.freevibe.data.repository.UploadRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.AudioPlaybackManager
import com.freevibe.service.AudioPreviewCache
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.CommunityAudioRecorder
import com.freevibe.service.DownloadManager
import com.freevibe.service.SeasonalContentManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
import com.freevibe.service.SoundFeedCache
import com.freevibe.service.soundFeedCacheKey
import com.freevibe.service.SourceMetrics
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import com.freevibe.data.repository.HiddenItemLabel

@HiltViewModel
class SoundsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val tiktokRepo: TikTokSoundRepository,
    private val favoritesRepo: FavoritesRepository,
    private val soundApplier: SoundApplier,
    private val downloadManager: DownloadManager,
    private val selectedContent: SelectedContentHolder,
    private val searchHistoryRepo: SearchHistoryRepository,
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
    private val soundFeedCache: SoundFeedCache,
) : ViewModel() {

    private val _state = MutableStateFlow(SoundsUiState())
    val state = _state.asStateFlow()

    /** Non-null only when a seasonal theme is currently active (holiday, summer, etc.). */
    val seasonalTheme = seasonalContentManager.currentTheme()

    val selectedSound = selectedContent.selectedSound

    val autoPreview = prefs.autoPreviewSounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val previewVolume = prefs.soundPreviewVolume.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)
    val youtubeProviderEnabled = prefs.youtubeProviderEnabled.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE),
    )
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

    private val _communityUploads = MutableStateFlow<List<Sound>>(emptyList())
    val communityUploads = _communityUploads.asStateFlow()

    internal val community = SoundCommunityActions(
        context = context,
        voteRepo = voteRepo,
        reportRepo = reportRepo,
        communityBlockRepo = communityBlockRepo,
        uploadRepo = uploadRepo,
        communityAudioRecorder = communityAudioRecorder,
        communityProviderEnabled = communityProviderEnabled,
        communityGuidelinesAccepted = communityGuidelinesAccepted,
        state = _state,
        communityUploads = _communityUploads,
        scope = viewModelScope,
        onStopIfPlaying = ::stopIfPlaying,
    )

    val hiddenIds = community.hiddenIds

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress = _playbackProgress.asStateFlow()

    private val previewWorkPermits = Semaphore(PREVIEW_WORK_CONCURRENCY)

    internal val playback: SoundPlaybackActions = SoundPlaybackActions(
        audioPlaybackManager = audioPlaybackManager,
        audioPreviewCache = audioPreviewCache,
        selectedContent = selectedContent,
        youtubeProviderEnabled = youtubeProviderEnabled,
        autoPreview = autoPreview,
        previewVolume = previewVolume,
        state = _state,
        communityUploads = _communityUploads,
        previewReadyIds = _previewReadyIds,
        playbackProgress = _playbackProgress,
        scope = viewModelScope,
        resolveRemotePreview = ::resolveRemotePreview,
        shouldRefreshRemotePreview = ::shouldRefreshRemotePreview,
        youtubeDisabledMessage = ::youtubeDisabledMessage,
        persistFeed = { snapshot ->
            viewModelScope.launch(Dispatchers.IO) {
                soundFeedCache.write(
                    soundFeedCacheKey(snapshot.selectedTab.name, snapshot.query),
                    snapshot.sounds,
                )
            }
        },
        previewWorkPermits = previewWorkPermits,
    )

    internal val previewWarmup = SoundPreviewWarmup(
        scope = viewModelScope,
        permits = previewWorkPermits,
        needsResolve = ::shouldRefreshRemotePreview,
        resolve = ::resolveRemotePreview,
        onResolved = { sound, url -> playback.cacheResolvedPreview(sound, url) },
    )

    internal val youtubeActions: SoundYouTubeActions = SoundYouTubeActions(
        youtubeRepo = youtubeRepo,
        prefs = prefs,
        searchHistoryRepo = searchHistoryRepo,
        youtubeProviderEnabled = youtubeProviderEnabled,
        state = _state,
        scope = viewModelScope,
        nextFilterKey = ::nextFilterKey,
        onProviderDisabled = ::selectRingtonesFromProviderFallback,
        schedulePreviewPrebuffer = playback::schedulePreviewPrebuffer,
        cacheResolvedPreview = playback::cacheResolvedPreview,
        requestPreviewWindow = ::requestPreviewWindow,
    )

    internal val communityFeed: SoundCommunityFeed = SoundCommunityFeed(
        uploadRepo = uploadRepo,
        sourceMetrics = sourceMetrics,
        state = _state,
        communityUploads = _communityUploads,
        scope = viewModelScope,
        communityActionBlocked = community::communityActionBlocked,
        showCommunityDisabledContent = community::showCommunityDisabledContent,
        cancelYouTubeLoad = ::cancelYouTubeLoad,
        schedulePreviewPrebuffer = playback::schedulePreviewPrebuffer,
    )

    internal val browseQueries: SoundBrowseQueries = SoundBrowseQueries(
        prefs = prefs,
        bundledContent = bundledContent,
        tiktokRingtones = tiktokRepo::ringtones,
    )

    internal val browse: SoundBrowseViewModel = SoundBrowseViewModel(
        youtubeRepo = youtubeRepo,
        queries = browseQueries,
        sourceMetrics = sourceMetrics,
        youtubeProviderEnabled = youtubeProviderEnabled,
        communityProviderEnabled = communityProviderEnabled,
        communityGuidelinesAccepted = communityGuidelinesAccepted,
        state = _state,
        scope = viewModelScope,
        communityFeed = communityFeed,
        nextFilterKey = ::nextFilterKey,
        communityDisabledMessage = community::communityDisabledMessage,
        loadDefaultYouTube = ::loadDefaultYouTube,
        executeYouTubeSearch = ::executeYouTubeSearch,
        cancelYouTubeLoad = ::cancelYouTubeLoad,
        schedulePreviewPrebuffer = playback::schedulePreviewPrebuffer,
        requestPreviewWindow = ::requestPreviewWindow,
        soundFeedCache = soundFeedCache,
    )

    internal val applyActions: SoundApplyActions = SoundApplyActions(
        soundApplier = soundApplier,
        downloadManager = downloadManager,
        favoritesRepo = favoritesRepo,
        youtubeRepo = youtubeRepo,
        soundUrlResolver = soundUrlResolver,
        youtubeProviderEnabled = youtubeProviderEnabled,
        state = _state,
        scope = viewModelScope,
        currentDownloadType = browse::currentDownloadType,
    )

    private val selectionResolver = SoundSelectionResolver(
        selectedContent = selectedContent,
        favoritesRepo = favoritesRepo,
        bundledContent = bundledContent,
        state = state,
        communityUploads = communityUploads,
    )

    init {
        community.init()
        // A new tab, query, or refresh bumps filterKey; the old feed's resolves stop there.
        viewModelScope.launch {
            _state.map { it.filterKey }.distinctUntilChanged().collect { key ->
                previewWarmup.switchFeed(key)
                playback.switchPrebufferFeed(key)
            }
        }
        browse.start()
        viewModelScope.launch {
            youtubeRepo.extractionStatus.collect { status ->
                _state.update { it.copy(youtubeExtractionStatus = status) }
            }
        }
        viewModelScope.launch {
            sourceMetrics.version.collect {
                _state.update { s -> s.copy(degradedSources = sourceMetrics.degradedSources()) }
            }
        }
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

    fun setQualityFilter(filter: SoundQualityFilter) = browse.setQualityFilter(filter)
    fun selectTab(tab: SoundTab) = browse.selectTab(tab)
    fun search(query: String) {
        browse.search(query)
        if (query.isNotBlank()) viewModelScope.launch { searchHistoryRepo.addSoundSearch(query) }
    }

    fun searchYouTube(query: String) = youtubeActions.searchYouTube(query)
    fun importYouTubeUrl(url: String) = youtubeActions.importYouTubeUrl(url)
    fun removeSearch(query: String) {
        viewModelScope.launch { searchHistoryRepo.removeSearch(query, "SOUND") }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryRepo.clearSoundHistory() }
    }

    fun clearSearchMode() = browse.clearSearchMode()
    fun clearYouTubeSearch() = browse.clearYouTubeSearch()
    fun loadMore() = browse.loadMore()
    fun refresh() = browse.refresh()

    fun selectSound(sound: Sound) = selectionResolver.selectSound(sound)

    suspend fun resolveSound(
        id: String,
        source: ContentSource? = null,
        previewUrl: String? = null,
        downloadUrl: String? = null,
    ): Sound? = selectionResolver.resolveSound(id, source, previewUrl, downloadUrl)

    suspend fun ensureSelectedSound(
        id: String,
        source: ContentSource? = null,
        previewUrl: String? = null,
        downloadUrl: String? = null,
    ): Boolean = selectionResolver.ensureSelectedSound(id, source, previewUrl, downloadUrl)

    /** The list reports its visible rows plus lookahead; only those resolve ahead of a tap. */
    fun onVisibleSoundsChanged(window: List<Sound>) = requestPreviewWindow(window)

    private fun requestPreviewWindow(window: List<Sound>) = previewWarmup.request(_state.value.filterKey, window)

    fun togglePlayback(sound: Sound) = playback.togglePlayback(sound)
    fun seekTo(fraction: Float) = playback.seekTo(fraction)
    fun stopIfPlaying(sound: Sound) = playback.stopIfPlaying(sound)

    fun applySound(sound: Sound, type: ContentType, confirmed: Boolean = false) = applyActions.applySound(sound, type, confirmed)
    fun downloadSound(sound: Sound, confirmed: Boolean = false) = applyActions.downloadSound(sound, confirmed)
    fun canWriteSettings(): Boolean = applyActions.canWriteSettings()
    fun canOpenWriteSettings(): Boolean = applyActions.canOpenWriteSettings()
    fun requestWriteSettings() = applyActions.requestWriteSettings()
    fun toggleFavorite(sound: Sound) = applyActions.toggleFavorite(sound)
    fun isFavorite(sound: Sound): Flow<Boolean> = applyActions.isFavorite(sound)

    suspend fun loadSimilar(sound: Sound): List<Sound> = youtubeActions.loadSimilar(sound)

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearSuccess() = _state.update { it.copy(applySuccess = null) }

    fun upvote(id: String) = community.upvote(id)
    fun downvote(id: String, label: HiddenItemLabel? = null) = community.downvote(id, label)
    fun undoDownvote(id: String) = community.undoDownvote(id)

    fun startCommunityRecording() = community.startRecording()
    fun stopCommunityRecording() = community.stopRecording()
    fun discardCommunityRecording() = community.discardRecording()
    fun consumeRecordedUpload() = community.consumeRecordedUpload()
    fun reportRecordingPermissionDenied() = community.reportRecordingPermissionDenied()

    fun startPersonalRecording() {
        if (_state.value.isRecordingPersonal) return
        communityAudioRecorder.start(onMaxDurationReached = ::stopPersonalRecording)
            .onSuccess {
                _state.update { it.copy(isRecordingPersonal = true, personalRecordingUri = null) }
            }
            .onFailure { e ->
                // Mic held by a call/another app: without feedback the record FAB
                // silently does nothing.
                _state.update {
                    it.copy(
                        error = context.getString(
                            R.string.sound_feedback_recording_failed,
                            e.message ?: context.getString(R.string.sound_feedback_microphone_unavailable),
                        ),
                    )
                }
            }
    }

    fun stopPersonalRecording() {
        if (!_state.value.isRecordingPersonal) return
        communityAudioRecorder.stop()
            .onSuccess { uri ->
                _state.update { it.copy(isRecordingPersonal = false, personalRecordingUri = uri) }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        isRecordingPersonal = false,
                        error = e.message?.let {
                            context.getString(R.string.sound_feedback_recording_save_failed, it)
                        } ?: context.getString(R.string.sound_feedback_recording_could_not_save),
                    )
                }
            }
    }

    fun cancelPersonalRecording() {
        communityAudioRecorder.cancel()
        _state.update { it.copy(isRecordingPersonal = false, personalRecordingUri = null) }
    }

    fun consumePersonalRecording() {
        _state.update { it.copy(personalRecordingUri = null) }
    }

    fun acceptCommunityGuidelines() =
        viewModelScope.launch { prefs.acceptCommunityGuidelines() }

    fun uploadSound(
        localUri: Uri,
        name: String,
        category: String,
        tags: List<String> = emptyList(),
        rights: CommunityUploadRights,
        isAiGenerated: Boolean = false,
    ) = community.uploadSound(localUri, name, category, tags, rights, isAiGenerated)

    suspend fun canDeleteCommunitySound(sound: Sound): Boolean = community.canDeleteSound(sound)
    fun deleteCommunitySound(sound: Sound, onDeleted: () -> Unit = {}) =
        community.deleteSound(sound, onDeleted)
    fun reportSound(sound: Sound, reason: CommunityReportReason, note: String = "") =
        community.reportSound(sound, reason, note)

    fun canBlockCommunitySound(sound: Sound): Boolean = community.canBlockSound(sound)
    fun blockCommunitySound(sound: Sound, onBlocked: () -> Unit = {}) =
        community.blockSound(sound, onBlocked)

    override fun onCleared() {
        browse.cancel()
        youtubeActions.cancel()
        previewWarmup.cancel()
        playback.cancelProgress()
        community.cancelOnCleared()
        audioPlaybackManager.stop()
        super.onCleared()
    }

    private fun nextFilterKey() = _state.value.filterKey + 1
    private fun shouldRefreshYouTubePreview(sound: Sound): Boolean = youtubeActions.shouldRefreshYouTubePreview(sound)

    /** YouTube previews need a stream resolve; TikTok ones need a fresh signed link once theirs expires. */
    private fun shouldRefreshRemotePreview(sound: Sound): Boolean = when (sound.source) {
        ContentSource.YOUTUBE -> shouldRefreshYouTubePreview(sound)
        ContentSource.TIKTOK -> !isTikTokMediaUrlUsable(sound.previewUrl, System.currentTimeMillis() / 1000)
        else -> false
    }

    private suspend fun resolveRemotePreview(sound: Sound): String? = when (sound.source) {
        ContentSource.TIKTOK -> try {
            tiktokRepo.playableMediaUrl(sound)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            null
        }
        else -> sound.youtubeVideoId()?.let { youtubeRepo.getAudioPreviewUrl(it) }
    }
    private fun cancelYouTubeLoad() = youtubeActions.cancel()
    private fun loadDefaultYouTube(isRefresh: Boolean) = youtubeActions.loadDefaultYouTube(isRefresh)
    private fun executeYouTubeSearch(query: String) = youtubeActions.executeYouTubeSearch(query)
    private fun selectRingtonesFromProviderFallback() = browse.selectTab(SoundTab.RINGTONES)
}
