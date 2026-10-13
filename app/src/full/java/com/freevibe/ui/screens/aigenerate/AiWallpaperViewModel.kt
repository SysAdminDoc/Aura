package com.freevibe.ui.screens.aigenerate

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.R
import com.freevibe.data.model.CommunityReportInput
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.AiStyle
import com.freevibe.data.repository.AiWallpaperRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.WallpaperUploadRepository
import java.util.Locale
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.SourceMetrics
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperApplyPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val SOURCE_AI_GENERATED = "ai_generated"
const val GENERATED_CONTENT_DISABLED_MESSAGE = "Generated wallpapers are disabled in Settings."
const val GENERATED_CONTENT_DISCLOSURE_REQUIRED_MESSAGE =
    "Review and accept the generated wallpaper disclosure before generating."
const val GENERATED_CONTENT_IN_FLIGHT_MESSAGE =
    "Generation already in progress. Wait for it to finish before starting another Stability request."
const val GENERATED_PROMPT_REQUIRED_MESSAGE = "Describe your wallpaper to get started."
const val GENERATED_API_KEY_REQUIRED_MESSAGE = "Enter your Stability AI key to generate images."

private val GENERATED_PROMPT_WHITESPACE_RE = "\\s+".toRegex()

data class GeneratedWallpaperRequestSignature(
    val normalizedPrompt: String,
    val style: AiStyle,
)

@androidx.annotation.StringRes
internal fun AiStyle.labelRes(): Int = when (this) {
    AiStyle.PHOTOGRAPHIC -> R.string.ai_style_photo
    AiStyle.ANIME -> R.string.ai_style_anime
    AiStyle.DIGITAL_ART -> R.string.ai_style_digital_art
    AiStyle.CINEMATIC -> R.string.ai_style_cinematic
    AiStyle.FANTASY -> R.string.ai_style_fantasy
    AiStyle.NEON_PUNK -> R.string.ai_style_neon
    AiStyle.PIXEL_ART -> R.string.ai_style_pixel_art
    AiStyle.NONE -> R.string.ai_style_none
}

data class DuplicateGenerationConfirmation(
    val promptPreview: String,
    @androidx.annotation.StringRes val styleLabelRes: Int,
)

fun generatedWallpaperRequestSignature(
    prompt: String,
    style: AiStyle,
): GeneratedWallpaperRequestSignature? {
    val normalizedPrompt = prompt.trim().replace(GENERATED_PROMPT_WHITESPACE_RE, " ").lowercase(Locale.ROOT)
    if (normalizedPrompt.isBlank()) return null
    return GeneratedWallpaperRequestSignature(normalizedPrompt, style)
}

fun duplicateGenerationConfirmation(
    prompt: String,
    style: AiStyle,
    lastSuccessfulRequest: GeneratedWallpaperRequestSignature?,
): DuplicateGenerationConfirmation? {
    val current = generatedWallpaperRequestSignature(prompt, style) ?: return null
    if (current != lastSuccessfulRequest) return null
    val preview = prompt.trim().replace(GENERATED_PROMPT_WHITESPACE_RE, " ").take(80)
    return DuplicateGenerationConfirmation(
        promptPreview = preview.ifBlank { "Generated wallpaper" },
        styleLabelRes = style.labelRes(),
    )
}

fun generatedWallpaperRequestError(
    providerEnabled: Boolean,
    prompt: String,
    apiKey: String,
    disclosureAccepted: Boolean,
    isGenerating: Boolean = false,
): String? = when {
    !providerEnabled -> GENERATED_CONTENT_DISABLED_MESSAGE
    isGenerating -> GENERATED_CONTENT_IN_FLIGHT_MESSAGE
    prompt.isBlank() -> GENERATED_PROMPT_REQUIRED_MESSAGE
    apiKey.isBlank() -> GENERATED_API_KEY_REQUIRED_MESSAGE
    !disclosureAccepted -> GENERATED_CONTENT_DISCLOSURE_REQUIRED_MESSAGE
    else -> null
}

fun generatedWallpaperReportInput(
    wallpaper: Wallpaper,
    reason: CommunityReportReason,
    note: String = "",
): CommunityReportInput = CommunityReportInput(
    contentId = wallpaper.stableKey(),
    contentType = "WALLPAPER",
    contentSource = ContentSource.AI_GENERATED,
    reason = reason,
    note = note,
    sourceUrl = "",
    license = "Generated wallpaper",
    uploaderName = "Aura generated wallpaper",
)

internal fun generatedWallpaperCommunityAiFlag(wallpaper: Wallpaper): Boolean =
    wallpaper.source == ContentSource.AI_GENERATED || wallpaper.isAiGenerated == true

private val GENERATED_WALLPAPER_SAFE_TAGS =
    setOf("ai-generated") + AiStyle.entries.mapNotNull { it.preset.takeIf(String::isNotBlank) }

fun generatedWallpaperFavoriteEntity(wallpaper: Wallpaper): FavoriteEntity = FavoriteEntity(
    id = wallpaper.id,
    source = ContentSource.AI_GENERATED.name,
    type = "WALLPAPER",
    thumbnailUrl = wallpaper.thumbnailUrl,
    fullUrl = wallpaper.fullUrl,
    name = "Generated wallpaper",
    width = wallpaper.width,
    height = wallpaper.height,
    tags = wallpaper.tags
        .filter { it in GENERATED_WALLPAPER_SAFE_TAGS }
        .joinToString(",")
        .ifBlank { "ai-generated" },
    category = "AI Generated",
    uploaderName = "AI",
)

data class AiWallpaperUiState(
    val prompt: String = "",
    val selectedStyle: AiStyle = AiStyle.PHOTOGRAPHIC,
    val isGenerating: Boolean = false,
    val result: Wallpaper? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
    val isSaved: Boolean = false,
    val error: String? = null,
    val sessionGenerationCount: Int = 0,
    val pendingDuplicateConfirmation: DuplicateGenerationConfirmation? = null,
    val isUploadingToCommunity: Boolean = false,
    val communityUploadProgress: Float = 0f,
    val communityUploadComplete: Boolean = false,
)

@HiltViewModel
class AiWallpaperViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: AiWallpaperRepository,
    private val applyCoordinator: com.freevibe.service.WallpaperApplyCoordinator,
    private val favoritesRepo: FavoritesRepository,
    private val reportRepo: CommunityReportRepository,
    private val wallpaperUploadRepo: WallpaperUploadRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val prefs: PreferencesManager,
    private val sourceMetrics: SourceMetrics,
) : ViewModel() {

    private val _state = MutableStateFlow(AiWallpaperUiState())
    val state: StateFlow<AiWallpaperUiState> = _state.asStateFlow()

    // Tracks the in-flight generation coroutine so back-navigation can cancel it
    // (NX-13). Cancelling a generation that has already hit Stability AI's
    // billing endpoint won't refund the credit, but it stops the spinner from
    // re-surfacing on resume and frees the OkHttp connection promptly.
    private var generationJob: Job? = null
    private var lastSuccessfulGeneration: GeneratedWallpaperRequestSignature? = null

    // API key is read from DataStore so changes in Settings propagate live.
    val stabilityAiKey: StateFlow<String> = prefs.generatedWallpaperProviderKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val generatedContentProviderEnabled: StateFlow<Boolean> = prefs.generatedContentProviderEnabled
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PreferencesManager.DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED,
        )
    val generatedContentDisclosureAccepted: StateFlow<Boolean> = prefs.generatedContentDisclosureAccepted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val communityProviderEnabled: StateFlow<Boolean> = prefs.communityProviderEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val communityGuidelinesAccepted: StateFlow<Boolean> = prefs.communityGuidelinesAccepted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPrompt(p: String) {
        _state.update { it.copy(prompt = p.take(500), pendingDuplicateConfirmation = null) }
    }

    fun setStyle(s: AiStyle) {
        _state.update { it.copy(selectedStyle = s, pendingDuplicateConfirmation = null) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { prefs.setGeneratedWallpaperProviderKey(key) }
    }

    fun acceptGeneratedContentDisclosure() {
        viewModelScope.launch { prefs.setGeneratedContentDisclosureAccepted(true) }
    }

    fun resetGeneratedContentDisclosure() {
        viewModelScope.launch { prefs.setGeneratedContentDisclosureAccepted(false) }
    }

    fun acceptDisclosureAndGenerate(apiKey: String) {
        viewModelScope.launch {
            prefs.setGeneratedContentDisclosureAccepted(true)
            generate(apiKey, disclosureAcceptedOverride = true)
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _state.update { it.copy(applySuccess = null) }
    }

    fun generate(
        apiKey: String,
        disclosureAcceptedOverride: Boolean? = null,
        allowDuplicate: Boolean = false,
    ) {
        val current = _state.value
        val providerEnabled = generatedContentProviderEnabled.value
        val generationActive = current.isGenerating || generationJob?.isActive == true
        val requestError = generatedWallpaperRequestError(
            providerEnabled = providerEnabled,
            prompt = current.prompt,
            apiKey = apiKey,
            disclosureAccepted = disclosureAcceptedOverride ?: generatedContentDisclosureAccepted.value,
            isGenerating = generationActive,
        )
        if (requestError != null) {
            if (!providerEnabled) sourceMetrics.recordDisabled(SOURCE_AI_GENERATED)
            _state.update { it.copy(error = localizedRequestError(requestError)) }
            return
        }

        val duplicateConfirmation = if (allowDuplicate) {
            null
        } else {
            duplicateGenerationConfirmation(
                prompt = current.prompt,
                style = current.selectedStyle,
                lastSuccessfulRequest = lastSuccessfulGeneration,
            )
        }
        if (duplicateConfirmation != null) {
            _state.update {
                it.copy(
                    pendingDuplicateConfirmation = duplicateConfirmation,
                    error = null,
                )
            }
            return
        }

        generationJob?.cancel()
        val requestSignature = generatedWallpaperRequestSignature(current.prompt, current.selectedStyle)
        generationJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isGenerating = true,
                    error = null,
                    result = null,
                    isSaved = false,
                    pendingDuplicateConfirmation = null,
                )
            }
            repo.generate(
                prompt = current.prompt,
                style = current.selectedStyle,
                apiKey = apiKey,
            ).onSuccess { wallpaper ->
                lastSuccessfulGeneration = requestSignature
                _state.update {
                    it.copy(
                        isGenerating = false,
                        result = wallpaper,
                        sessionGenerationCount = it.sessionGenerationCount + 1,
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isGenerating = false,
                        error = e.message?.let { message ->
                            context.getString(R.string.ai_feedback_generation_failed_detail, message)
                        } ?: context.getString(R.string.ai_feedback_generation_failed),
                    )
                }
            }
        }
    }

    fun confirmDuplicateGeneration(apiKey: String) {
        _state.update { it.copy(pendingDuplicateConfirmation = null) }
        generate(apiKey = apiKey, allowDuplicate = true)
    }

    fun dismissDuplicateGeneration() {
        _state.update { it.copy(pendingDuplicateConfirmation = null) }
    }

    /**
     * Cancels any in-flight generation (NX-13). Invoked by the screen's
     * [androidx.activity.compose.BackHandler] when the user presses back
     * while a generation is still streaming.
     */
    fun cancelGeneration() {
        val job = generationJob ?: return
        if (job.isActive) {
            job.cancel()
            _state.update {
                it.copy(
                    isGenerating = false,
                    error = context.getString(R.string.ai_feedback_generation_cancelled),
                )
            }
        }
        generationJob = null
    }

    override fun onCleared() {
        generationJob?.cancel()
        generationJob = null
        super.onCleared()
    }

    fun applyWallpaper(target: WallpaperTarget = WallpaperTarget.BOTH) {
        val wallpaper = _state.value.result ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, error = null) }
            // Route through WallpaperApplier.applyByLocator so the disk read + decode + sampling
            // all happen on the IO dispatcher inside the applier (the prior version decoded the
            // full-resolution PNG on the Main coroutine context — a 3-4 MB PNG → ~10 MB bitmap
            // synchronously on the UI thread). applyByLocator handles file:// URIs natively.
            // Routed through the coordinator so a generated wallpaper lands in
            // history and can be undone, exactly like a browsed one.
            applyCoordinator.apply(
                wallpaper = wallpaper,
                target = target,
                policy = WallpaperApplyPolicy.DERIVED,
            ) { wallpaperApplier.applyByLocator(wallpaper.fullUrl, target) }
                .onSuccess { receipt ->
                    _state.update {
                        it.copy(isApplying = false, applySuccess = receipt.feedbackMessage)
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isApplying = false,
                            error = context.getString(
                                R.string.apply_feedback_apply_failed,
                                e.message ?: context.getString(R.string.apply_feedback_unknown_error),
                            ),
                        )
                    }
                }
        }
    }

    fun saveToFavorites() {
        val wallpaper = _state.value.result ?: return
        viewModelScope.launch {
            favoritesRepo.add(generatedWallpaperFavoriteEntity(wallpaper))
            _state.update { it.copy(isSaved = true) }
        }
    }

    fun shareGeneratedWallpaper(
        name: String,
        category: String,
        tags: List<String>,
        rights: CommunityUploadRights,
    ) {
        val wallpaper = _state.value.result ?: return
        if (_state.value.isUploadingToCommunity) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isUploadingToCommunity = true,
                    communityUploadProgress = 0f,
                    communityUploadComplete = false,
                    error = null,
                )
            }
            wallpaperUploadRepo.uploadWallpaper(
                localUri = Uri.parse(wallpaper.fullUrl),
                name = name,
                category = category,
                tags = tags,
                rights = rights,
                isAiGenerated = generatedWallpaperCommunityAiFlag(wallpaper),
                onProgress = { progress ->
                    _state.update { state -> state.copy(communityUploadProgress = progress) }
                },
            ).onSuccess {
                _state.update {
                    it.copy(
                        isUploadingToCommunity = false,
                        communityUploadProgress = 0f,
                        communityUploadComplete = true,
                        applySuccess = context.getString(R.string.ai_share_community_complete),
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isUploadingToCommunity = false,
                        communityUploadProgress = 0f,
                        error = context.getString(
                            R.string.ai_share_community_failed,
                            error.message ?: context.getString(R.string.feedback_try_again),
                        ),
                    )
                }
            }
        }
    }

    fun acknowledgeCommunityUpload() {
        _state.update { it.copy(communityUploadComplete = false) }
    }

    fun reportGeneratedWallpaper(wallpaper: Wallpaper, reason: CommunityReportReason, note: String = "") {
        viewModelScope.launch {
            reportRepo.submitReport(generatedWallpaperReportInput(wallpaper, reason, note))
                .onSuccess {
                    _state.update { it.copy(applySuccess = context.getString(R.string.feedback_report_submitted)) }
                }
                .onFailure { error ->
                    _state.update {
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

    private fun localizedRequestError(error: String): String {
        val stringRes = when (error) {
            GENERATED_CONTENT_DISABLED_MESSAGE -> R.string.ai_feedback_provider_disabled
            GENERATED_CONTENT_IN_FLIGHT_MESSAGE -> R.string.ai_feedback_generation_in_progress
            GENERATED_PROMPT_REQUIRED_MESSAGE -> R.string.ai_feedback_prompt_required
            GENERATED_API_KEY_REQUIRED_MESSAGE -> R.string.ai_feedback_key_required
            GENERATED_CONTENT_DISCLOSURE_REQUIRED_MESSAGE -> R.string.ai_feedback_disclosure_required
            else -> return error
        }
        return context.getString(stringRes)
    }
}
