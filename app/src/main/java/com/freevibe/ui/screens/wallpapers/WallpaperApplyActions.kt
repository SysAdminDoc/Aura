package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.sourceUnavailableReasonForFailure
import com.freevibe.data.model.stableKey
import com.freevibe.data.remote.toFavoriteEntity
import com.freevibe.data.repository.AiWallpaperRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.service.ApplyFeedbackBus
import com.freevibe.service.ApplyFeedbackEvent
import com.freevibe.service.DownloadManager
import com.freevibe.service.DualWallpaperService
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class WallpaperApplyActions(
    private val wallpaperApplier: WallpaperApplier,
    private val downloadManager: DownloadManager,
    private val dualWallpaperService: DualWallpaperService,
    private val historyManager: WallpaperHistoryManager,
    private val favoritesRepo: FavoritesRepository,
    private val offlineFavorites: OfflineFavoritesManager,
    private val aiWallpaperRepository: AiWallpaperRepository,
    private val applyFeedbackBus: ApplyFeedbackBus,
    private val state: MutableStateFlow<WallpapersUiState>,
    private val scope: CoroutineScope,
) {

    val activeDownloads = downloadManager.activeDownloads

    fun applyWallpaper(wallpaper: Wallpaper, target: WallpaperTarget) {
        scope.launch {
            state.update { it.copy(isApplying = true, applySuccess = null) }
            wallpaperApplier.applyFromUrl(wallpaper.fullUrl, target)
                .onSuccess {
                    historyManager.record(wallpaper, target)
                    val undoTarget = historyManager.previousSnapshot()
                    val label = when (target) {
                        WallpaperTarget.HOME -> "home screen"
                        WallpaperTarget.LOCK -> "lock screen"
                        WallpaperTarget.BOTH -> "home & lock screen"
                    }
                    state.update { it.copy(isApplying = false, applySuccess = "Set as $label wallpaper") }
                    applyFeedbackBus.post(
                        ApplyFeedbackEvent(
                            message = "Applied to $label",
                            undoTarget = undoTarget,
                        )
                    )
                }
                .onFailure { e ->
                    markSourceUnavailableIfRemoved(wallpaper, e)
                    state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun undoApply(entry: com.freevibe.data.model.WallpaperHistoryEntity) {
        scope.launch {
            state.update { it.copy(isApplying = true) }
            val target = runCatching { WallpaperTarget.valueOf(entry.target) }
                .getOrDefault(WallpaperTarget.BOTH)
            wallpaperApplier.applyFromUrl(entry.fullUrl, target)
                .onSuccess {
                    state.update { it.copy(isApplying = false, applySuccess = "Reverted") }
                    applyFeedbackBus.post(ApplyFeedbackEvent(message = "Reverted to previous wallpaper", undoTarget = null))
                }
                .onFailure { e ->
                    state.update { it.copy(isApplying = false, error = "Undo failed: ${e.message}") }
                }
        }
    }

    fun applySplitCrop(wallpaper: Wallpaper) {
        scope.launch {
            state.update { it.copy(isApplying = true, applySuccess = null) }
            dualWallpaperService.applySplitCrop(wallpaper)
                .onSuccess {
                    historyManager.record(wallpaper, WallpaperTarget.BOTH)
                    state.update { it.copy(isApplying = false, applySuccess = "Split crop applied to home & lock") }
                }
                .onFailure { e ->
                    state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun applyParallax(wallpaper: Wallpaper) {
        scope.launch {
            state.update { it.copy(isApplying = true, applySuccess = null) }
            val ext = guessImageExtension(wallpaper.fileType, wallpaper.fullUrl)
            wallpaperApplier.prepareParallaxWallpaper(wallpaper.fullUrl, "parallax_wp.$ext")
                .onSuccess {
                    state.update { it.copy(isApplying = false, pendingLiveWallpaperLaunch = true) }
                }
                .onFailure { e ->
                    state.update { it.copy(isApplying = false, error = e.message) }
                }
        }
    }

    fun clearPendingLaunch() = state.update { it.copy(pendingLiveWallpaperLaunch = false) }

    fun downloadWallpaper(wallpaper: Wallpaper) {
        scope.launch {
            val ext = guessImageExtension(wallpaper.fileType, wallpaper.fullUrl)
            downloadManager.downloadWallpaper(
                id = wallpaper.stableKey(),
                url = wallpaper.fullUrl,
                fileName = buildWallpaperDownloadFileName(wallpaper, ext),
                source = wallpaper.source.name,
            ).onFailure { error ->
                markSourceUnavailableIfRemoved(wallpaper, error)
                state.update { it.copy(error = error.message) }
            }
        }
    }

    fun dismissDownload(id: String) {
        downloadManager.clearCompleted(id)
    }

    fun toggleFavorite(wallpaper: Wallpaper) {
        scope.launch {
            val entity = wallpaper.toFavoriteEntity()
            val isFav = favoritesRepo.isFavorite(wallpaper.favoriteIdentity()).first()
            favoritesRepo.toggle(entity, isFav)
            if (!isFav) {
                offlineFavorites.cacheOffline(entity, wallpaper.fullUrl)
            } else {
                offlineFavorites.removeOffline(entity)
                if (wallpaper.source == ContentSource.AI_GENERATED) {
                    aiWallpaperRepository.deleteGeneratedWallpaper(wallpaper.fullUrl)
                    if (wallpaper.thumbnailUrl != wallpaper.fullUrl) {
                        aiWallpaperRepository.deleteGeneratedWallpaper(wallpaper.thumbnailUrl)
                    }
                }
            }
            state.update { it.copy(applySuccess = if (isFav) "Removed from favorites" else "Added to favorites") }
        }
    }

    fun isFavorite(wallpaper: Wallpaper): Flow<Boolean> = favoritesRepo.isFavorite(wallpaper.favoriteIdentity())

    private suspend fun markSourceUnavailableIfRemoved(wallpaper: Wallpaper, failure: Throwable) {
        sourceUnavailableReasonForFailure(wallpaper.source, failure)?.let { reason ->
            favoritesRepo.markSourceUnavailable(wallpaper.favoriteIdentity(), reason)
        }
    }
}

internal fun guessImageExtension(fileType: String, url: String): String {
    if (fileType.isNotBlank()) {
        return when {
            fileType.contains("png", true) -> "png"
            fileType.contains("webp", true) -> "webp"
            fileType.contains("gif", true) -> "gif"
            else -> "jpg"
        }
    }
    val path = url.substringBefore("?").substringBefore("#").lowercase(java.util.Locale.ROOT)
    return when {
        path.endsWith(".png") -> "png"
        path.endsWith(".webp") -> "webp"
        path.endsWith(".gif") -> "gif"
        else -> "jpg"
    }
}

