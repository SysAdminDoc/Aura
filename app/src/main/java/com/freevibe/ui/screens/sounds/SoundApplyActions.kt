package com.freevibe.ui.screens.sounds

import com.freevibe.data.model.ContentType
import com.freevibe.data.model.Sound
import com.freevibe.data.model.SoundAction
import com.freevibe.data.model.SoundActionDecision
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.soundLicenseCapabilities
import com.freevibe.data.model.sourceUnavailableReasonForFailure
import com.freevibe.data.model.stableKey
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.DownloadManager
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundUrlResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class SoundApplyActions(
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
                        error = "System settings access is required before applying sounds.",
                    )
                }
                return@launch
            }
            state.update { it.copy(isApplying = true, applySuccess = null) }
            val url = resolveDownloadUrl(sound)
                ?: run {
                    state.update { it.copy(isApplying = false, error = "Could not resolve audio") }
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
                    state.update { it.copy(isApplying = false, applySuccess = "Set as $label") }
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
                state.update { it.copy(error = "Could not resolve audio stream URL") }
                return@launch
            }
            val ext = sound.fileType.substringAfterLast("/", "mp3").substringAfterLast(".", "mp3").lowercase(java.util.Locale.ROOT)
            downloadManager.downloadSound(
                id = sound.stableKey(), url = dlUrl,
                fileName = buildSoundDownloadFileName(sound, ext),
                type = currentDownloadType(),
                source = sound.source.name,
            ).fold(
                onSuccess = { state.update { it.copy(applySuccess = "Download started") } },
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
            state.update { it.copy(applySuccess = if (isFav) "Removed from favorites" else "Added to favorites") }
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

    private suspend fun markSoundSourceUnavailableIfRemoved(sound: Sound, failure: Throwable) {
        sourceUnavailableReasonForFailure(sound.source, failure)?.let { reason ->
            favoritesRepo.markSourceUnavailable(sound.favoriteIdentity(), reason)
        }
    }
}

internal fun buildSoundDownloadFileName(sound: Sound, extension: String): String =
    "Aura_${sound.source.name.lowercase(java.util.Locale.ROOT)}_${sound.id}_${sound.name.take(24)}.$extension"
