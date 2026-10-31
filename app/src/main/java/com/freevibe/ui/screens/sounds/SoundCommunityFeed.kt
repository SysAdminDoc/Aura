package com.freevibe.ui.screens.sounds

import android.content.Context
import com.freevibe.data.model.Sound
import com.freevibe.data.repository.UploadRepository
import com.freevibe.service.SourceMetrics
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SoundCommunityFeed(
    private val context: Context,
    private val uploadRepo: UploadRepository,
    private val sourceMetrics: SourceMetrics,
    private val state: MutableStateFlow<SoundsUiState>,
    private val communityUploads: MutableStateFlow<List<Sound>>,
    private val scope: CoroutineScope,
    private val communityActionBlocked: () -> Boolean,
    private val showCommunityDisabledContent: () -> Unit,
    private val cancelYouTubeLoad: () -> Unit,
    private val schedulePreviewPrebuffer: (List<Sound>) -> Unit,
) {
    private var communityJob: Job? = null

    fun cancel() {
        communityJob?.cancel()
    }

    fun loadCommunityTab(isRefresh: Boolean = false) {
        communityJob?.cancel()
        cancelYouTubeLoad()
        if (communityActionBlocked()) {
            sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
            showCommunityDisabledContent()
            return
        }
        state.update {
            if (isRefresh) it.copy(isRefreshing = true, error = null)
            else it.copy(isLoading = true, error = null)
        }
        communityJob = scope.launch {
            val timeoutJob = launch {
                kotlinx.coroutines.delay(10_000L)
                val snapshot = state.value
                if (snapshot.isLoading || snapshot.isRefreshing) {
                    state.update { it.copy(isLoading = false, isRefreshing = false, error = context.getString(com.freevibe.R.string.sound_error_community_timed_out)) }
                }
            }
            try {
                uploadRepo.getCommunityUploads(limit = 50).collect { sounds ->
                    timeoutJob.cancel()
                    var rankedSounds: List<Sound> = emptyList()
                    state.update {
                        rankedSounds = rankSounds(sounds, SoundTab.COMMUNITY, it.qualityFilter)
                        it.copy(
                            sounds = rankedSounds,
                            isLoading = false,
                            isRefreshing = false,
                            hasMore = false,
                        )
                    }
                    communityUploads.value = rankedSounds
                    schedulePreviewPrebuffer(rankedSounds)
                }
                state.update { it.copy(isLoading = false, isRefreshing = false) }
            } catch (e: Exception) {
                e.rethrowIfCancelled()
                state.update { it.copy(isLoading = false, isRefreshing = false, error = e.message) }
            } finally {
                timeoutJob.cancel()
            }
        }
    }

    private companion object {
        const val SOURCE_COMMUNITY = "community"
    }
}
