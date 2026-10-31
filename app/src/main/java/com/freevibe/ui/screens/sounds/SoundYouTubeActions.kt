package com.freevibe.ui.screens.sounds

import android.content.Context
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class SoundYouTubeActions(
    private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val prefs: PreferencesManager,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val youtubeProviderEnabled: StateFlow<Boolean>,
    private val state: MutableStateFlow<SoundsUiState>,
    private val scope: CoroutineScope,
    private val nextFilterKey: () -> Int,
    private val onProviderDisabled: () -> Unit,
    private val schedulePreviewPrebuffer: (List<Sound>) -> Unit,
    private val cacheResolvedPreview: (Sound, String) -> Sound,
    private val requestPreviewWindow: (List<Sound>) -> Unit,
) {
    private var loadJob: Job? = null

    fun cancel() {
        loadJob?.cancel()
    }

    fun searchYouTube(query: String) {
        if (query.isBlank()) return
        if (!youtubeProviderEnabled.value) {
            state.update { it.copy(error = youtubeDisabledMessage(context)) }
            return
        }
        state.update {
            it.copy(
                query = query,
                selectedTab = SoundTab.YOUTUBE,
                sounds = emptyList(),
                currentPage = 1,
                hasMore = true,
                filterKey = nextFilterKey(),
                isLoading = true,
                error = null,
                isRefreshing = false,
                searchReturnTab = SoundTab.YOUTUBE,
            )
        }
        scope.launch { searchHistoryRepo.addSoundSearch(query) }
        executeYouTubeSearch(query)
    }

    fun importYouTubeUrl(url: String) {
        if (!youtubeProviderEnabled.value) {
            state.update { it.copy(error = youtubeDisabledMessage(context)) }
            return
        }
        val videoId = extractYouTubeId(url)
        if (videoId == null) {
            state.update { it.copy(error = context.getString(com.freevibe.R.string.sound_error_invalid_youtube_url)) }
            return
        }
        state.update {
            it.copy(
                selectedTab = SoundTab.YOUTUBE,
                isLoading = true,
                error = null,
                sounds = emptyList(),
                filterKey = nextFilterKey(),
                searchReturnTab = SoundTab.YOUTUBE,
            )
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val info = withContext(Dispatchers.IO) {
                    val service = org.schabi.newpipe.extractor.NewPipe.getService(
                        org.schabi.newpipe.extractor.ServiceList.YouTube.serviceId,
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
                state.update { it.copy(sounds = listOf(sound), isLoading = false) }
                youtubeRepo.getAudioPreviewUrl(videoId)?.let { cacheResolvedPreview(sound, it) }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                state.update { it.copy(isLoading = false, error = context.getString(com.freevibe.R.string.sound_error_video_load_failed, e.message.orEmpty())) }
            }
        }
    }

    fun loadDefaultYouTube(isRefresh: Boolean = false) {
        if (!youtubeProviderEnabled.value) {
            onProviderDisabled()
            return
        }
        loadJob?.cancel()
        state.update {
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
        loadJob = scope.launch {
            val query = defaultYouTubeQuery()
            state.update { it.copy(query = query) }
            runYouTubeSearch(
                query = query,
                minDuration = 5,
                maxDuration = 45,
                rankTab = SoundTab.RINGTONES,
            )
        }
    }

    fun executeYouTubeSearch(query: String) {
        if (!youtubeProviderEnabled.value) {
            state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasMore = false,
                    error = youtubeDisabledMessage(context),
                )
            }
            return
        }
        loadJob?.cancel()
        loadJob = scope.launch {
            runYouTubeSearch(query)
        }
    }

    fun shouldRefreshYouTubePreview(sound: Sound): Boolean {
        if (!youtubeProviderEnabled.value) return false
        val videoId = sound.youtubeVideoId() ?: return false
        return sound.previewUrl.isBlank() || !youtubeRepo.isCached(videoId)
    }

    suspend fun loadSimilar(sound: Sound): List<Sound> {
        if (!isYouTubeProviderEnabled()) return emptyList()
        val keywords = sound.name.split(WORD_SPLIT_REGEX)
            .filter { it.length > 2 }
            .take(4)
            .joinToString(" ")
        if (keywords.isBlank()) return emptyList()
        return try {
            val blocked = blockedWords()
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

    private suspend fun runYouTubeSearch(
        query: String,
        minDuration: Int = 0,
        maxDuration: Int = 600,
        rankTab: SoundTab = SoundTab.YOUTUBE,
    ) {
        try {
            if (!isYouTubeProviderEnabled()) {
                state.update {
                    it.copy(
                        sounds = emptyList(),
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = false,
                        error = youtubeDisabledMessage(context),
                    )
                }
                return
            }
            val result = youtubeRepo.searchSounds(
                query = query,
                maxDuration = maxDuration,
                minDuration = minDuration,
                blockedWords = blockedWords(),
            )
            var rankedSounds: List<Sound> = emptyList()
            state.update {
                rankedSounds = rankSounds(result.items, rankTab, it.qualityFilter)
                it.copy(
                    sounds = rankedSounds,
                    isLoading = false,
                    isRefreshing = false,
                    hasMore = false,
                )
            }
            schedulePreviewPrebuffer(rankedSounds)
            // Only the top of the list resolves now; the rest waits until it scrolls into view.
            requestPreviewWindow(rankedSounds.take(INITIAL_PREVIEW_RESOLVE_WINDOW))
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = categorizeSoundError(context, e),
                )
            }
        }
    }

    private suspend fun defaultYouTubeQuery(): String =
        prefs.ytSoundQueryRingtones.first().trim()
            .takeIf { it.isNotBlank() }
            ?: PreferencesManager.defaultRingtoneQuery()

    private suspend fun blockedWords(): List<String> =
        try {
            prefs.ytSoundBlockedWords.first()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            emptyList()
        }

    private suspend fun isYouTubeProviderEnabled(): Boolean = prefs.youtubeProviderEnabled.first()

    private fun extractYouTubeId(url: String): String? {
        val trimmed = url.trim()
        for (pattern in YOUTUBE_ID_PATTERNS) {
            pattern.find(trimmed)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    private companion object {
        val WORD_SPLIT_REGEX = Regex("[^a-zA-Z0-9]+")
        val YOUTUBE_ID_PATTERNS = listOf(
            Regex("""(?:youtube\.com/watch\?.*v=|youtu\.be/|youtube\.com/shorts/)([a-zA-Z0-9_-]{11})"""),
            Regex("""^([a-zA-Z0-9_-]{11})$"""),
        )
    }
}
