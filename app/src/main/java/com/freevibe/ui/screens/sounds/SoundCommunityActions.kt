package com.freevibe.ui.screens.sounds

import android.content.Context
import android.net.Uri
import com.freevibe.R
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
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
import com.freevibe.data.repository.HiddenItemLabel

internal class SoundCommunityActions(
    private val context: Context,
    val voteRepo: VoteRepository,
    private val reportRepo: CommunityReportRepository,
    private val communityBlockRepo: CommunityBlockRepository,
    val uploadRepo: UploadRepository,
    private val communityAudioRecorder: CommunityAudioRecorder,
    private val communityProviderEnabled: StateFlow<Boolean>,
    private val communityGuidelinesAccepted: StateFlow<Boolean>,
    private val state: MutableStateFlow<SoundsUiState>,
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
            context.getString(R.string.sound_feedback_community_disabled)
        } else {
            context.getString(R.string.community_guidelines_action_required)
        }

    fun upvote(id: String) {
        if (isCommunityVoteId(id) && communityActionBlocked()) return
        scope.launch {
            try { voteRepo.upvote(id) }
            catch (e: Exception) {
                e.rethrowIfCancelled()
                state.update { it.copy(error = e.message ?: context.getString(R.string.sound_feedback_upvote_failed)) }
            }
        }
    }

    fun downvote(id: String, label: HiddenItemLabel? = null) {
        if (isCommunityVoteId(id) && communityActionBlocked()) return
        scope.launch {
            try { voteRepo.downvote(id, label) }
            catch (e: Exception) {
                e.rethrowIfCancelled()
                state.update { it.copy(error = e.message ?: context.getString(R.string.sound_feedback_downvote_failed)) }
            }
        }
    }

    fun undoDownvote(id: String) {
        scope.launch {
            try { voteRepo.undoDownvote(id) }
            catch (e: Exception) { e.rethrowIfCancelled() }
        }
    }

    fun startRecording() {
        if (state.value.isRecordingUpload) return
        if (communityActionBlocked()) return
        communityAudioRecorder.start(onMaxDurationReached = ::stopRecording)
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
                    it.copy(
                        error = context.getString(
                            R.string.sound_feedback_recording_failed,
                            e.message ?: context.getString(R.string.sound_feedback_microphone_unavailable),
                        ),
                    )
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
                        applySuccess = context.getString(R.string.sound_feedback_recording_ready),
                    )
                }
            }
            .onFailure { e ->
                state.update {
                    it.copy(
                        isRecordingUpload = false,
                        recordingStartedAtMs = 0L,
                        error = e.message?.let {
                            context.getString(R.string.sound_feedback_recording_save_failed, it)
                        } ?: context.getString(R.string.sound_feedback_recording_could_not_save),
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
        state.update { it.copy(error = context.getString(R.string.sound_feedback_microphone_permission)) }
    }

    fun uploadSound(
        localUri: Uri,
        name: String,
        category: String,
        tags: List<String> = emptyList(),
        rights: CommunityUploadRights,
        isAiGenerated: Boolean = false,
    ) {
        if (state.value.isUploading) return
        if (communityActionBlocked()) return
        scope.launch {
            state.update { it.copy(isUploading = true, uploadComplete = false, uploadProgress = 0f) }
            uploadRepo.uploadSound(
                localUri = localUri,
                name = name,
                category = category,
                tags = tags,
                rights = rights,
                isAiGenerated = isAiGenerated,
                onProgress = { progress ->
                    state.update { it.copy(uploadProgress = progress) }
                },
            ).onSuccess {
                state.update {
                    it.copy(
                        isUploading = false,
                        uploadComplete = true,
                        uploadProgress = 0f,
                        applySuccess = context.getString(R.string.sound_feedback_upload_complete),
                    )
                }
            }.onFailure { e ->
                state.update {
                    it.copy(
                        isUploading = false,
                        uploadProgress = 0f,
                        error = context.getString(
                            R.string.sound_feedback_upload_failed,
                            e.message ?: context.getString(R.string.feedback_try_again),
                        ),
                    )
                }
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

    fun deleteSound(sound: Sound, onDeleted: () -> Unit = {}) {
        if (communityActionBlocked()) return
        scope.launch {
            uploadRepo.deleteSoundUpload(sound.id)
                .onSuccess {
                    val key = sound.stableKey()
                    onStopIfPlaying(sound)
                    state.update { s ->
                        s.copy(
                            sounds = s.sounds.filterNot { it.stableKey() == key },
                            applySuccess = context.getString(R.string.feedback_upload_deleted),
                        )
                    }
                    onDeleted()
                }
                .onFailure { error ->
                    state.update {
                        it.copy(
                            error = context.getString(
                                R.string.feedback_delete_failed,
                                error.message ?: context.getString(R.string.feedback_try_again),
                            ),
                        )
                    }
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
                state.update { it.copy(applySuccess = context.getString(R.string.feedback_report_submitted)) }
            }.onFailure { error ->
                state.update {
                    it.copy(
                        error = context.getString(
                            R.string.feedback_report_failed,
                            error.message ?: context.getString(R.string.feedback_try_again),
                        ),
                    )
                }
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
            state.update { it.copy(error = context.getString(R.string.sound_feedback_unblockable_uploader)) }
            return
        }
        scope.launch {
            communityBlockRepo.blockUser(blockedUploaderId, CommunityBlockReason.OTHER)
                .onSuccess {
                    onStopIfPlaying(sound)
                    communityUploads.update { uploads -> uploads.filterNot { it.matchesCommunityUploader(blockedUploaderId) } }
                    state.update { s ->
                        s.copy(
                            sounds = s.sounds.filterNot { it.matchesCommunityUploader(blockedUploaderId) },
                            applySuccess = context.getString(R.string.feedback_creator_blocked),
                        )
                    }
                    onBlocked()
                }
                .onFailure { error ->
                    state.update {
                        it.copy(
                            error = context.getString(
                                R.string.feedback_block_failed,
                                error.message ?: context.getString(R.string.feedback_try_again),
                            ),
                        )
                    }
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
