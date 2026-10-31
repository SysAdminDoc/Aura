package com.freevibe.ui.screens.sounds

import android.content.Context
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.SoundAction
import com.freevibe.data.model.SoundActionDecision
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.soundLicenseCapabilities
import com.freevibe.data.model.isSourceUnavailable
import com.freevibe.data.model.sourceUnavailableReasonForFailure
import com.freevibe.data.model.stableKey
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.DownloadManager
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
import com.freevibe.service.downloadHistoryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SoundApplyActions(
    private val context: Context,
    private val soundApplier: SoundApplier,
    private val downloadManager: DownloadManager,
    private val favoritesRepo: FavoritesRepository,
    private val youtubeRepo: YouTubeRepository,
    private val soundUrlResolver: SoundUrlResolver,
    private val youtubeProviderEnabled: StateFlow<Boolean>,
    private val state: MutableStateFlow<SoundsUiState>,
    private val scope: CoroutineScope,
    private val currentDownloadType: () -> ContentType,
) {

    fun applySound(sound: Sound, type: ContentType, confirmed: Boolean = false) {
        scope.launch {
            soundActionGateMessage(sound, SoundAction.APPLY, confirmed)?.let { message ->
                state.update { it.copy(isApplying = false, error = message) }
                return@launch
            }
            if (!soundApplier.canWriteSettings()) {
                state.update {
                    it.copy(
                        isApplying = false,
                        error = context.getString(com.freevibe.R.string.sound_apply_write_settings_required),
                    )
                }
                return@launch
            }
            state.update { it.copy(isApplying = true, applySuccess = null) }
            val url = resolveDownloadUrl(sound)
                ?: run {
                    state.update { it.copy(isApplying = false, error = context.getString(com.freevibe.R.string.sound_error_resolve_audio)) }
                    return@launch
                }
            soundApplier.downloadAndApply(
                url = url,
                fileName = sound.name,
                type = type,
                savedOriginalId = downloadHistoryId("SOUND", sound.stableKey()),
            )
                .onSuccess {
                    val setAsLabel = context.getString(
                        when (type) {
                            ContentType.RINGTONE -> com.freevibe.R.string.sounds_quick_apply_set_ringtone
                            ContentType.NOTIFICATION -> com.freevibe.R.string.sounds_quick_apply_set_notification
                            ContentType.ALARM -> com.freevibe.R.string.sounds_quick_apply_set_alarm
                            else -> com.freevibe.R.string.sound_set_as_sound
                        },
                    )
                    clearSoundSourceUnavailableAfterSuccess(sound)
                    state.update { it.copy(isApplying = false, applySuccess = setAsLabel) }
                }
                .onFailure { e ->
                    markSoundSourceUnavailableIfRemoved(sound, e)
                    state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun downloadSound(sound: Sound, confirmed: Boolean = false) {
        scope.launch {
            soundActionGateMessage(sound, SoundAction.DOWNLOAD, confirmed)?.let { message ->
                state.update { it.copy(error = message) }
                return@launch
            }
            val dlUrl = resolveDownloadUrl(sound) ?: run {
                state.update { it.copy(error = context.getString(com.freevibe.R.string.sound_error_resolve_stream)) }
                return@launch
            }
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3").lowercase(java.util.Locale.ROOT)
            downloadManager.downloadSound(
                id = sound.stableKey(), url = dlUrl,
                fileName = buildSoundDownloadFileName(sound, ext),
                type = currentDownloadType(),
                source = sound.source.name,
                provenanceUrl = sound.sourcePageUrl.ifBlank { dlUrl },
            ).fold(
                onSuccess = {
                    clearSoundSourceUnavailableAfterSuccess(sound)
                    state.update { it.copy(applySuccess = context.getString(com.freevibe.R.string.sound_download_started)) }
                },
                onFailure = { error ->
                    markSoundSourceUnavailableIfRemoved(sound, error)
                    state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    fun canWriteSettings(): Boolean = soundApplier.canWriteSettings()
    fun canOpenWriteSettings(): Boolean = soundApplier.canOpenWriteSettings()
    fun requestWriteSettings() = soundApplier.requestWriteSettings()

    fun toggleFavorite(sound: Sound) {
        scope.launch {
            val entity = sound.toFavoriteEntity()
            val isFav = favoritesRepo.isFavorite(sound.favoriteIdentity()).first()
            favoritesRepo.toggle(entity, isFav)
            state.update { it.copy(applySuccess = if (isFav) context.getString(com.freevibe.R.string.wallpaper_feedback_favorite_removed) else context.getString(com.freevibe.R.string.wallpaper_feedback_favorite_added)) }
        }
    }

    fun isFavorite(sound: Sound): Flow<Boolean> = favoritesRepo.isFavorite(sound.favoriteIdentity())

    internal fun soundActionGateMessage(sound: Sound, action: SoundAction, confirmed: Boolean): String? {
        val capability = sound.soundLicenseCapabilities().capability(action)
        return when (capability.decision) {
            SoundActionDecision.ALLOWED -> null
            SoundActionDecision.CONFIRMATION_REQUIRED -> capability.reason.takeUnless { confirmed }
            SoundActionDecision.DISABLED -> capability.reason
        }
    }

    internal suspend fun resolveDownloadUrl(sound: Sound): String? {
        val videoId = sound.youtubeVideoId()
        return if (videoId != null) {
            if (!youtubeProviderEnabled.value) return null
            youtubeRepo.getAudioStreamUrl(videoId)
        } else {
            soundUrlResolver.resolve(sound)
        }
    }

    /**
     * Persist an unavailable state only when the remote item is genuinely gone.
     * A 403, a throttle, or a timeout is about this attempt, not the item.
     */
    private suspend fun markSoundSourceUnavailableIfRemoved(sound: Sound, failure: Throwable) {
        sourceUnavailableReasonForFailure(sound.source, failure)?.let { reason ->
            favoritesRepo.markSourceUnavailable(sound.favoriteIdentity(), reason)
        }
    }

    /** A success proves the source works again, so clear any recorded state. */
    private suspend fun clearSoundSourceUnavailableAfterSuccess(sound: Sound) {
        if (!sound.isSourceUnavailable()) return
        favoritesRepo.clearSourceUnavailable(sound.favoriteIdentity())
    }
}

internal fun buildSoundDownloadFileName(sound: Sound, extension: String): String =
    "Aura_${sound.source.name.lowercase(java.util.Locale.ROOT)}_${sound.id}_${sound.name.take(24)}.$extension"
