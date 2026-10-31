package com.freevibe.ui.screens.wallpapers

import android.content.Context
import android.net.Uri
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.WallpaperUploadRepository
import com.freevibe.service.SourceMetrics
import com.freevibe.util.displayMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.freevibe.data.repository.HiddenItemLabel

internal class WallpaperCommunityActions(
    private val context: Context,
    val voteRepo: VoteRepository,
    private val reportRepo: CommunityReportRepository,
    private val communityBlockRepo: CommunityBlockRepository,
    private val wallpaperUploadRepo: WallpaperUploadRepository,
    private val cacheManager: WallpaperCacheManager,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
    private val communityProviderEnabled: StateFlow<Boolean>,
    private val communityGuidelinesAccepted: StateFlow<Boolean>,
    private val state: MutableStateFlow<WallpapersUiState>,
    private val topVoted: MutableStateFlow<List<Pair<Wallpaper, Int>>>,
    private val scope: CoroutineScope,
    private val fetchTopVoted: (List<Wallpaper>) -> Unit,
) {

    val hiddenIds = voteRepo.hiddenIds

    fun getVoteCount(contentId: String) =
        if (communityProviderEnabled.value && communityGuidelinesAccepted.value) voteRepo.getVoteCount(contentId) else flowOf(0)

    fun upvote(contentId: String) {
        if (communityActionBlocked()) return
        scope.launch {
            val success = voteRepo.upvote(contentId)
            if (!success) state.update { it.copy(applySuccess = context.getString(R.string.wallpaper_feedback_already_voted)) }
        }
    }

    fun downvote(contentId: String, label: HiddenItemLabel? = null) {
        if (communityActionBlocked()) return
        scope.launch {
            voteRepo.downvote(contentId, label)
            state.update { it.copy(applySuccess = context.getString(
                        if (voteRepo.isAdmin) R.string.wallpaper_feedback_moderated else R.string.wallpaper_feedback_hidden,
                    ),
                ) }
        }
    }

    fun reportWallpaper(wallpaper: Wallpaper, reason: CommunityReportReason, note: String = "") {
        val isGeneratedWallpaper = wallpaper.source == ContentSource.AI_GENERATED
        if (!isGeneratedWallpaper && communityActionBlocked()) return
        scope.launch {
            reportRepo.submitReport(
                CommunityReportInput(
                    contentId = wallpaper.stableKey(),
                    contentType = "WALLPAPER",
                    contentSource = wallpaper.source,
                    reason = reason,
                    note = note,
                    sourceUrl = if (isGeneratedWallpaper) "" else reportSourceUrl(wallpaper.sourcePageUrl, wallpaper.fullUrl),
                    license = if (isGeneratedWallpaper) "Generated wallpaper" else wallpaper.license,
                    uploaderName = if (isGeneratedWallpaper) "Aura generated wallpaper" else wallpaper.uploaderName,
                    uploaderUid = if (isGeneratedWallpaper) "" else wallpaper.communityUploaderId,
                ),
            ).onSuccess {
                state.update { it.copy(applySuccess = context.getString(R.string.feedback_report_submitted)) }
            }.onFailure { error ->
                state.update {
                    it.copy(
                        error = context.getString(
                            R.string.feedback_report_failed,
                            error.displayMessage(context) ?: context.getString(R.string.feedback_try_again),
                        ),
                    )
                }
            }
        }
    }

    fun canBlockCommunityWallpaper(wallpaper: Wallpaper): Boolean =
        wallpaper.source == ContentSource.COMMUNITY &&
            communityProviderEnabled.value &&
            communityGuidelinesAccepted.value &&
            wallpaper.communityUploaderId.isNotBlank()

    fun blockCommunityWallpaper(wallpaper: Wallpaper, onBlocked: () -> Unit = {}) {
        if (communityActionBlocked()) return
        val blockedUploaderId = wallpaper.communityUploaderId
        if (wallpaper.source != ContentSource.COMMUNITY || blockedUploaderId.isBlank()) {
            state.update { it.copy(error = context.getString(R.string.wallpaper_feedback_unblockable_uploader)) }
            return
        }
        scope.launch {
            communityBlockRepo.blockUser(blockedUploaderId, CommunityBlockReason.OTHER)
                .onSuccess {
                    topVoted.update { rows -> rows.filterNot { it.first.matchesCommunityUploader(blockedUploaderId) } }
                    state.update { s ->
                        s.copy(
                            wallpapers = s.wallpapers.filterNot { it.matchesCommunityUploader(blockedUploaderId) },
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
                                error.displayMessage(context) ?: context.getString(R.string.feedback_try_again),
                            ),
                        )
                    }
                }
        }
    }

    suspend fun canDeleteCommunityWallpaper(wallpaper: Wallpaper): Boolean {
        if (
            wallpaper.source != ContentSource.COMMUNITY ||
            !communityProviderEnabled.value ||
            !communityGuidelinesAccepted.value
        ) return false
        return wallpaperUploadRepo.canDeleteWallpaperUpload(wallpaper.id)
    }

    fun deleteCommunityWallpaper(wallpaper: Wallpaper) {
        if (communityActionBlocked()) return
        scope.launch {
            wallpaperUploadRepo.deleteWallpaperUpload(wallpaper.id)
                .onSuccess {
                    val key = wallpaper.stableKey()
                    state.update { s ->
                        s.copy(
                            wallpapers = s.wallpapers.filterNot { it.stableKey() == key },
                            applySuccess = context.getString(R.string.feedback_upload_deleted),
                        )
                    }
                }
                .onFailure { error ->
                    state.update {
                        it.copy(
                            error = context.getString(
                                R.string.feedback_delete_failed,
                                error.displayMessage(context) ?: context.getString(R.string.feedback_try_again),
                            ),
                        )
                    }
                }
        }
    }

    fun uploadCommunityWallpaper(
        localUri: Uri,
        name: String,
        category: String,
        tags: List<String>,
        rights: CommunityUploadRights,
        isAiGenerated: Boolean = false,
    ) {
        if (state.value.isUploadingWallpaper) return
        if (communityActionBlocked()) return
        scope.launch {
            state.update {
                it.copy(
                    isUploadingWallpaper = true,
                    wallpaperUploadComplete = false,
                    wallpaperUploadProgress = 0f,
                    error = null,
                    errorSource = null,
                )
            }
            wallpaperUploadRepo.uploadWallpaper(
                localUri = localUri,
                name = name,
                category = category,
                tags = tags,
                rights = rights,
                isAiGenerated = isAiGenerated,
                onProgress = { progress ->
                    state.update { s -> s.copy(wallpaperUploadProgress = progress) }
                },
            ).onSuccess { wallpaper ->
                cacheManager.cache("community_wallpapers_recent", listOf(wallpaper))
                state.update {
                    val shouldInsert = it.selectedTab == WallpaperTab.COMMUNITY
                    it.copy(
                        isUploadingWallpaper = false,
                        wallpaperUploadComplete = true,
                        wallpaperUploadProgress = 0f,
                        applySuccess = context.getString(R.string.wallpaper_feedback_upload_complete),
                        wallpapers = if (shouldInsert) {
                            (listOf(wallpaper) + it.wallpapers).distinctBy { candidate -> candidate.stableKey() }
                        } else {
                            it.wallpapers
                        },
                    )
                }
                fetchTopVoted(listOf(wallpaper))
            }.onFailure { e ->
                state.update {
                    it.copy(
                        isUploadingWallpaper = false,
                        wallpaperUploadProgress = 0f,
                        error = context.getString(
                            R.string.wallpaper_feedback_upload_failed,
                            e.displayMessage(context) ?: context.getString(R.string.wallpaper_feedback_try_another_image),
                        ),
                        errorSource = WallpaperTab.COMMUNITY.name,
                    )
                }
            }
        }
    }

    fun acceptCommunityGuidelines() {
        scope.launch { prefs.acceptCommunityGuidelines() }
    }

    private fun communityActionBlocked(): Boolean {
        if (communityProviderEnabled.value && communityGuidelinesAccepted.value) return false
        sourceMetrics.recordDisabled(SOURCE_COMMUNITY)
        state.update { it.copy(error = communityDisabledMessage()) }
        return true
    }

    fun communityDisabledMessage(): String =
        if (!communityProviderEnabled.value) {
            context.getString(R.string.sound_feedback_community_disabled)
        } else {
            context.getString(R.string.community_guidelines_action_required)
        }

    private companion object {
        const val SOURCE_COMMUNITY = "community"
    }
}
