package com.freevibe.ui.screens.sounds

import android.content.Context
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.SourceMetrics
import com.freevibe.service.SoundFeedCache
import com.freevibe.service.soundFeedCacheKey
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

internal class SoundBrowseViewModel(
    private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val queries: SoundBrowseQueries,
    private val sourceMetrics: SourceMetrics,
    private val youtubeProviderEnabled: StateFlow<Boolean>,
    private val communityProviderEnabled: StateFlow<Boolean>,
    private val communityGuidelinesAccepted: StateFlow<Boolean>,
    private val state: MutableStateFlow<SoundsUiState>,
    private val scope: CoroutineScope,
    private val communityFeed: SoundCommunityFeed,
    private val nextFilterKey: () -> Int,
    private val communityDisabledMessage: () -> String,
    private val loadDefaultYouTube: (Boolean) -> Unit,
    private val executeYouTubeSearch: (String) -> Unit,
    private val cancelYouTubeLoad: () -> Unit,
    private val schedulePreviewPrebuffer: (List<Sound>) -> Unit,
    private val requestPreviewWindow: (List<Sound>) -> Unit,
    private val soundFeedCache: SoundFeedCache,
) {
    private var loadJob: Job? = null
    private val titleBlocklist = Regex(
        "hindi|telugu|pack|trending|popular|\\bnew\\b|\\btop\\b|\\bbest\\b|timer|countdown|quiz|comparison|tutorial|how to|turn on|turn off|notification spam",
        RegexOption.IGNORE_CASE,
    )

    fun start() {
        hydrateCachedFeed(state.value.selectedTab, state.value.query)
        loadSounds()
    }

    fun cancel() {
        loadJob?.cancel()
        communityFeed.cancel()
    }

    fun setQualityFilter(filter: SoundQualityFilter) {
        val currentTab = state.value.selectedTab
        var rankedSounds: List<Sound> = emptyList()
        state.update {
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
        communityFeed.cancel()
        if (tab != SoundTab.YOUTUBE) cancelYouTubeLoad()
        // Always cancel the in-flight browse load: switching to COMMUNITY (whose feed
        // never touches loadJob) must not let a stale RINGTONES/SEARCH load finish
        // later and overwrite the community list.
        loadJob?.cancel()

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
            state.update { it.copy(error = communityDisabledMessage()) }
            return
        }

        state.update {
            it.copy(
                selectedTab = tab,
                query = "",
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                filterKey = nextFilterKey(),
                isRefreshing = false,
                searchReturnTab = if (tab == SoundTab.SEARCH) it.searchReturnTab else tab,
            )
        }
        if (tab !in setOf(SoundTab.COMMUNITY, SoundTab.YOUTUBE)) {
            hydrateCachedFeed(tab, "")
        }
        when (tab) {
            SoundTab.COMMUNITY -> communityFeed.loadCommunityTab()
            SoundTab.YOUTUBE -> loadDefaultYouTube(false)
            else -> loadSounds()
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return
        communityFeed.cancel()
        cancelYouTubeLoad()
        val returnTab = state.value.selectedTab.takeIf { it != SoundTab.SEARCH } ?: state.value.searchReturnTab
        state.update {
            it.copy(
                query = query,
                selectedTab = SoundTab.SEARCH,
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                filterKey = nextFilterKey(),
                searchReturnTab = returnTab,
            )
        }
        loadSounds()
    }

    fun clearSearchMode() {
        val returnTab = when (val tab = state.value.searchReturnTab) {
            SoundTab.YOUTUBE -> if (youtubeProviderEnabled.value) tab else SoundTab.RINGTONES
            SoundTab.COMMUNITY -> if (communityProviderEnabled.value) tab else SoundTab.RINGTONES
            else -> tab
        }
        communityFeed.cancel()
        cancelYouTubeLoad()
        // A still-running SEARCH load would otherwise clobber the restored tab's feed.
        loadJob?.cancel()
        state.update {
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
            SoundTab.COMMUNITY -> communityFeed.loadCommunityTab()
            SoundTab.YOUTUBE -> if (youtubeProviderEnabled.value) loadDefaultYouTube(false) else selectTab(SoundTab.RINGTONES)
            else -> loadSounds()
        }
    }

    fun clearYouTubeSearch() {
        if (youtubeProviderEnabled.value) {
            loadDefaultYouTube(false)
        } else {
            selectTab(SoundTab.RINGTONES)
        }
    }

    fun loadMore() {
        val snapshot = state.value
        if (snapshot.isLoading || snapshot.isLoadingMore || !snapshot.hasMore) return
        state.update { it.copy(currentPage = it.currentPage + 1) }
        loadSounds(loadMore = true)
    }

    fun refresh() {
        when (val tab = state.value.selectedTab) {
            SoundTab.COMMUNITY -> {
                if (!communityProviderEnabled.value) {
                    selectTab(SoundTab.RINGTONES)
                    return
                }
                state.update { it.copy(isRefreshing = true, error = null) }
                communityFeed.loadCommunityTab(isRefresh = true)
            }
            SoundTab.YOUTUBE -> {
                if (!youtubeProviderEnabled.value) {
                    selectTab(SoundTab.RINGTONES)
                    return
                }
                val query = state.value.query
                if (query.isBlank()) {
                    loadDefaultYouTube(true)
                } else {
                    state.update { it.copy(isRefreshing = true, error = null) }
                    executeYouTubeSearch(query)
                }
            }
            else -> {
                state.update { it.copy(isRefreshing = true, currentPage = 1, error = null) }
                loadSounds(isRefresh = true)
            }
        }
    }

    fun currentDownloadType(tab: SoundTab = state.value.selectedTab): ContentType = when (tab) {
        SoundTab.NOTIFICATIONS -> ContentType.NOTIFICATION
        SoundTab.ALARMS -> ContentType.ALARM
        else -> ContentType.RINGTONE
    }

    private fun redirectToRingtones() {
        communityFeed.cancel()
        loadJob?.cancel()
        cancelYouTubeLoad()
        state.update {
            it.copy(
                selectedTab = SoundTab.RINGTONES,
                query = "",
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                error = null,
                filterKey = nextFilterKey(),
                isRefreshing = false,
                searchReturnTab = SoundTab.RINGTONES,
            )
        }
        loadSounds()
    }

    private fun loadSounds(loadMore: Boolean = false, isRefresh: Boolean = false) {
        val tab = state.value.selectedTab
        if (tab == SoundTab.YOUTUBE || tab == SoundTab.COMMUNITY) return
        if (!loadMore) {
            loadJob?.cancel()
            cancelYouTubeLoad()
        }
        loadJob = scope.launch {
            val snapshot = state.value
            val loadTab = snapshot.selectedTab
            if (loadTab == SoundTab.SEARCH && snapshot.query.isBlank()) {
                state.update {
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
                state.update { it.copy(isLoading = it.sounds.isEmpty(), error = null) }
            } else if (loadMore) {
                state.update { it.copy(isLoadingMore = true) }
            }

            if (!queries.isYouTubeProviderEnabled()) {
                handleYouTubeDisabledFeed(loadTab, loadMore, isRefresh)
                return@launch
            }

            val allResults = mutableListOf<Sound>()
            val resultLock = Any()
            val seenKeys = ConcurrentHashMap.newKeySet<String>()
            val seenFingerprints = ConcurrentHashMap.newKeySet<String>()
            val firstFailure = AtomicReference<Exception?>(null)

            if (loadMore) {
                snapshot.sounds.forEach { sound ->
                    seenKeys.add(sound.stableKey())
                    seenFingerprints.add(soundFingerprint(sound))
                }
            }

            fun addUnique(sound: Sound): Boolean {
                if (sound.source !in ACTIVE_SOUND_SOURCES) return false
                // The blocklist screens YouTube search spam; curated creator clips skip it.
                if (sound.source != ContentSource.TIKTOK && titleBlocklist.containsMatchIn(sound.name)) return false
                val fingerprint = soundFingerprint(sound)
                return if (seenKeys.add(sound.stableKey()) && seenFingerprints.add(fingerprint)) {
                    synchronized(resultLock) { allResults.add(sound) }
                    true
                } else {
                    false
                }
            }

            queries.bundledSoundsFor(loadTab, snapshot.query).forEach(::addUnique)

            suspend fun flushToUi() {
                currentCoroutineContext().ensureActive()
                state.update { current ->
                    val ranked = rankSounds(
                        sounds = synchronized(resultLock) { allResults.toList() },
                        tab = loadTab,
                        filter = current.qualityFilter,
                    )
                    val existingKeys = current.sounds.mapTo(mutableSetOf()) { it.stableKey() }
                    current.copy(
                        sounds = if (loadMore) {
                            current.sounds + ranked.filter { sound -> existingKeys.add(sound.stableKey()) }
                        } else {
                            ranked
                        },
                    )
                }
                schedulePreviewPrebuffer(state.value.sounds)
                persistFeed(state.value.sounds, loadTab, snapshot.query)
            }

            try {
                if (loadMore) {
                    state.update { it.copy(isLoadingMore = false, hasMore = false) }
                    return@launch
                }

                val querySet = queries.buildQueries(snapshot)
                val (cappedMin, cappedMax) = queries.tabDurationRange(snapshot)

                if (allResults.isNotEmpty()) flushToUi()

                supervisorScope {
                    launch {
                        try {
                            var added = false
                            queries.tiktokSoundsFor(loadTab, forceRefresh = isRefresh)
                                .forEach { if (addUnique(it)) added = true }
                            if (added) flushToUi()
                        } catch (e: Exception) {
                            e.rethrowIfCancelled()
                            firstFailure.compareAndSet(null, e)
                        }
                    }
                    if (querySet.ytQueries.isNotEmpty()) {
                        val blocked = queries.blockedWords()
                        querySet.ytQueries.forEach { ytQuery ->
                            launch {
                                try {
                                    val result = youtubeRepo.searchSounds(
                                        query = ytQuery,
                                        maxDuration = cappedMax,
                                        minDuration = cappedMin,
                                        blockedWords = blocked,
                                    )
                                    var added = false
                                    result.items.forEach { if (addUnique(it)) added = true }
                                    // Previews resolve from the list's visible window, not per query.
                                    if (added) flushToUi()
                                } catch (e: Exception) {
                                    e.rethrowIfCancelled()
                                    firstFailure.compareAndSet(null, e)
                                }
                            }
                        }
                    }
                }

                currentCoroutineContext().ensureActive()
                val combined = rankSounds(
                    sounds = synchronized(resultLock) { allResults.toList() },
                    tab = loadTab,
                    filter = state.value.qualityFilter,
                )
                val preserveCurrentFeed = !loadMore && combined.isEmpty() && snapshot.sounds.isNotEmpty()
                val surfacedError = firstFailure.get()
                    ?.takeIf { combined.isEmpty() }
                    ?.let { categorizeSoundError(context, it) }
                var visibleSoundsAfterLoad: List<Sound> = emptyList()
                state.update {
                    val nextSounds = when {
                        loadMore -> {
                            val existingKeys = it.sounds.mapTo(mutableSetOf()) { sound -> sound.stableKey() }
                            it.sounds + combined.filter { sound -> existingKeys.add(sound.stableKey()) }
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
                requestPreviewWindow(visibleSoundsAfterLoad.take(INITIAL_PREVIEW_RESOLVE_WINDOW))
                persistFeed(visibleSoundsAfterLoad, loadTab, snapshot.query)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                state.update {
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        isRefreshing = false,
                        hasMore = it.hasMore,
                        error = if (it.sounds.isNotEmpty()) {
                            context.getString(com.freevibe.R.string.sound_error_last_good, categorizeSoundError(context, e))
                        } else {
                            categorizeSoundError(context, e)
                        },
                    )
                }
            }
        }
    }

    private suspend fun handleYouTubeDisabledFeed(loadTab: SoundTab, loadMore: Boolean, isRefresh: Boolean) {
        if (loadMore) {
            state.update { it.copy(isLoadingMore = false, hasMore = false) }
            return
        }
        // TikTok is its own provider, so switching YouTube off keeps the creator clips.
        val tiktokSounds = try {
            queries.tiktokSoundsFor(loadTab, forceRefresh = isRefresh)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            emptyList()
        }
        val fallbackSounds = rankSounds(
            sounds = queries.bundledSoundsFor(loadTab, state.value.query) + tiktokSounds,
            tab = loadTab,
            filter = state.value.qualityFilter,
        )
        state.update {
            it.copy(
                sounds = fallbackSounds,
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
                hasMore = false,
                error = if (loadTab == SoundTab.SEARCH) youtubeDisabledMessage(context) else null,
            )
        }
        schedulePreviewPrebuffer(fallbackSounds)
        persistFeed(fallbackSounds, loadTab, state.value.query)
    }

    private fun hydrateCachedFeed(tab: SoundTab, query: String) {
        val cached = soundFeedCache.read(soundFeedCacheKey(tab.name, query)) ?: return
        cached.sounds.forEach { sound ->
            if (sound.source == ContentSource.YOUTUBE && sound.previewUrl.isNotBlank()) {
                sound.youtubeVideoId()?.let { videoId ->
                    youtubeRepo.rememberAudioPreviewUrl(videoId, sound.previewUrl, cached.cachedAtMs)
                }
            }
        }
        val ranked = rankSounds(cached.sounds, tab, state.value.qualityFilter)
        state.update { current ->
            if (current.selectedTab != tab || current.query != query || current.sounds.isNotEmpty()) current
            else current.copy(sounds = ranked, isLoading = false)
        }
        schedulePreviewPrebuffer(ranked)
    }

    private fun persistFeed(sounds: List<Sound>, tab: SoundTab, query: String) {
        if (sounds.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            soundFeedCache.write(soundFeedCacheKey(tab.name, query), sounds)
        }
    }

    private companion object {
        const val SOURCE_YOUTUBE = "youtube"
        const val SOURCE_COMMUNITY = "community"
        val ACTIVE_SOUND_SOURCES = setOf(
            ContentSource.YOUTUBE,
            ContentSource.TIKTOK,
            ContentSource.BUNDLED,
        )
    }
}
