package com.freevibe.ui.screens.sounds

import android.net.Uri
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.COMMUNITY_GUIDELINES_REQUIRED_MESSAGE
import com.freevibe.data.model.Sound
import com.freevibe.data.model.sanitizeCommunityOwnerKey
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.UploadRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.service.CommunityAudioRecorder
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SoundCommunityActions(
    val voteRepo: VoteRepository,
    private val reportRepo: CommunityReportRepository,
    private val communityBlockRepo: CommunityBlockRepository,
    val uploadRepo: UploadRepository,
    private val communityAudioRecorder: CommunityAudioRecorder,
    private val communityProviderEnabled: StateFlow<Boolean>,
    private val communityGuidelinesAccepted: StateFlow<Boolean>,
    private val state: MutableStateFlow<SoundsUiState>,
    private val topHits: MutableStateFlow<List<Sound>>,
    private val communityUploads: MutableStateFlow<List<Sound>>,
    private val scope: CoroutineScope,
    private val onStopIfPlaying: (Sound) -> Unit,
) {
    val hiddenIds = voteRepo.hiddenIds

    fun init() {
        communityAudioRecorder.pruneStaleRecordings()
    }

    fun isCommunityVoteId(id: String): Boolean =
        id.contains("::COMMUNITY::") || id.startsWith("cu_")

    fun communityActionBlocked(): Boolean {
        if (communityProviderEnabled.value && communityGuidelinesAccepted.value) return false
        showCommunityDisabledError()
        return true
    }

    fun showCommunityDisabledError() {
        state.update { it.copy(error = communityDisabledMessage()) }
    }

    fun showCommunityDisabledContent() {
        state.update {
            it.copy(
                sounds = emptyList(),
                isLoading = false,
                isLoadingMore = false,
                isRefreshing = false,
                hasMore = false,
                error = communityDisabledMessage(),
            )
        }
    }

    fun communityDisabledMessage(): String =
        if (!communityProviderEnabled.value) {
            "Community source is disabled in Settings"
        } else {
            COMMUNITY_GUIDELINES_REQUIRED_MESSAGE
        }

    fun upvote(id: String) {
        if (isCommunityVoteId(id) && communityActionBlocked()) return
        scope.launch {
            try { voteRepo.upvote(id) }
            catch (e: Exception) {
                e.rethrowIfCancelled()
                state.update { it.copy(error = e.message ?: "Failed to upvote") }
            }
        }
    }

    fun downvote(id: String) {
        if (isCommunityVoteId(id) && communityActionBlocked()) return
        scope.launch {
            try { voteRepo.downvote(id) }
            catch (e: Exception) {
                e.rethrowIfCancelled()
                state.update { it.copy(error = e.message ?: "Failed to downvote") }
            }
        }
    }

    fun startRecording() {
        if (state.value.isRecordingUpload) return
        if (communityActionBlocked()) return
        communityAudioRecorder.start()
            .onSuccess {
                state.update {
                    it.copy(
                        isRecordingUpload = true,
                        recordingStartedAtMs = System.currentTimeMillis(),
                        recordedUploadUri = null,
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                state.update {
                    it.copy(error = "Recording failed: ${e.message ?: "microphone unavailable"}")
                }
            }
    }

    fun stopRecording() {
        if (!state.value.isRecordingUpload) return
        communityAudioRecorder.stop()
            .onSuccess { uri ->
                state.update {
                    it.copy(
                        isRecordingUpload = false,
                        recordingStartedAtMs = 0L,
                        recordedUploadUri = uri,
                        applySuccess = "Recording ready to upload",
                    )
                }
            }
            .onFailure { e ->
                state.update {
                    it.copy(
                        isRecordingUpload = false,
                        recordingStartedAtMs = 0L,
                        error = e.message ?: "Recording could not be saved",
                    )
                }
            }
    }

    fun discardRecording() {
        communityAudioRecorder.cancel()
        state.update {
            it.copy(
                isRecordingUpload = false,
                recordingStartedAtMs = 0L,
                recordedUploadUri = null,
            )
        }
    }

    fun consumeRecordedUpload() {
        state.update { it.copy(recordedUploadUri = null) }
    }

    fun reportRecordingPermissionDenied() {
        if (communityActionBlocked()) return
        state.update { it.copy(error = "Microphone permission is required to record a community sound") }
    }

    fun uploadSound(
        localUri: Uri,
        name: String,
        category: String,
        tags: List<String> = emptyList(),
        rights: CommunityUploadRights,
    ) {
        if (state.value.isUploading) return
        if (communityActionBlocked()) return
        scope.launch {
            state.update { it.copy(isUploading = true, uploadProgress = 0f) }
            uploadRepo.uploadSound(
                localUri = localUri,
                name = name,
                category = category,
                tags = tags,
                rights = rights,
                onProgress = { progress ->
                    state.update { it.copy(uploadProgress = progress) }
                },
            ).onSuccess {
                state.update { it.copy(isUploading = false, uploadProgress = 0f, applySuccess = "Upload complete") }
            }.onFailure { e ->
                state.update { it.copy(isUploading = false, uploadProgress = 0f, error = "Upload failed: ${e.message}") }
            }
        }
    }

    suspend fun canDeleteSound(sound: Sound): Boolean {
        if (
            sound.source != ContentSource.COMMUNITY ||
            !communityProviderEnabled.value ||
            !communityGuidelinesAccepted.value
        ) return false
        return uploadRepo.canDeleteSoundUpload(sound.id)
    }

    fun deleteSound(sound: Sound) {
        if (communityActionBlocked()) return
        scope.launch {
            uploadRepo.deleteSoundUpload(sound.id)
                .onSuccess {
                    val key = sound.stableKey()
                    onStopIfPlaying(sound)
                    topHits.update { hits -> hits.filterNot { it.stableKey() == key } }
                    state.update { s ->
                        s.copy(
                            sounds = s.sounds.filterNot { it.stableKey() == key },
                            applySuccess = "Upload deleted",
                        )
                    }
                }
                .onFailure { error ->
                    state.update { it.copy(error = "Delete failed: ${error.message ?: "try again"}") }
                }
        }
    }

    fun reportSound(sound: Sound, reason: CommunityReportReason, note: String = "") {
        if (communityActionBlocked()) return
        scope.launch {
            reportRepo.submitReport(
                CommunityReportInput(
                    contentId = sound.stableKey(),
                    contentType = "SOUND",
                    contentSource = sound.source,
                    reason = reason,
                    note = note,
                    sourceUrl = reportSourceUrl(sound.sourcePageUrl, sound.downloadUrl),
                    license = sound.license,
                    uploaderName = sound.uploaderName,
                    uploaderUid = sound.communityUploaderId,
                ),
            ).onSuccess {
                state.update { it.copy(applySuccess = "Report submitted") }
            }.onFailure { error ->
                state.update { it.copy(error = "Report failed: ${error.message ?: "try again"}") }
            }
        }
    }

    fun canBlockSound(sound: Sound): Boolean =
        sound.source == ContentSource.COMMUNITY &&
            communityProviderEnabled.value &&
            communityGuidelinesAccepted.value &&
            sound.communityUploaderId.isNotBlank()

    fun blockSound(sound: Sound, onBlocked: () -> Unit = {}) {
        if (communityActionBlocked()) return
        val blockedUploaderId = sound.communityUploaderId
        if (sound.source != ContentSource.COMMUNITY || blockedUploaderId.isBlank()) {
            state.update { it.copy(error = "This sound does not expose a blockable community uploader") }
            return
        }
        scope.launch {
            communityBlockRepo.blockUser(blockedUploaderId, CommunityBlockReason.OTHER)
                .onSuccess {
                    onStopIfPlaying(sound)
                    topHits.update { hits -> hits.filterNot { it.matchesCommunityUploader(blockedUploaderId) } }
                    communityUploads.update { uploads -> uploads.filterNot { it.matchesCommunityUploader(blockedUploaderId) } }
                    state.update { s ->
                        s.copy(
                            sounds = s.sounds.filterNot { it.matchesCommunityUploader(blockedUploaderId) },
                            applySuccess = "Creator blocked",
                        )
                    }
                    onBlocked()
                }
                .onFailure { error ->
                    state.update { it.copy(error = "Block failed: ${error.message ?: "try again"}") }
                }
        }
    }

    fun cancelOnCleared() {
        communityAudioRecorder.cancel()
    }
}

private fun reportSourceUrl(primary: String, fallback: String): String =
    listOf(primary, fallback)
        .firstOrNull { it.startsWith("https://", ignoreCase = true) }
        .orEmpty()

private fun Sound.matchesCommunityUploader(uploaderId: String): Boolean =
    sanitizeCommunityOwnerKey(communityUploaderId).let { it.isNotBlank() && it == sanitizeCommunityOwnerKey(uploaderId) }
