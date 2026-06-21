package com.freevibe.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.di.IoDispatcher
import com.freevibe.service.AutoWallpaperWorker
import com.freevibe.service.AutoBackupWorker
import com.freevibe.service.RingtoneShuffleWorker
import com.freevibe.service.BackgroundWorkDiagnostics
import com.freevibe.service.BackgroundWorkDiagnosticsReader
import com.freevibe.service.CommunityIdentityProvider
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.CrashDiagnosticsCollector
import com.freevibe.service.CrashDiagnosticsSummary
import com.freevibe.service.ExternalAutomationDiagnostics
import com.freevibe.service.ExternalAutomationGate
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import com.freevibe.service.YtDlpUpdateManager
import com.freevibe.service.YtDlpUpdateResult
import com.freevibe.service.YtDlpUpdateSnapshot
import com.freevibe.service.YtDlpUpdateStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CacheUsageState(
    val fileUsageLabel: String = "Calculating...",
    val hasWallpaperMetadataCache: Boolean = false,
)

data class CommunityBlockActionState(
    val unblockingUserId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

data class CommunityIdentityCleanupState(
    val clearing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

data class YtDlpUpdateUiState(
    val snapshot: YtDlpUpdateSnapshot = YtDlpUpdateSnapshot(),
    val isUpdating: Boolean = false,
    val completedStatus: YtDlpUpdateStatus? = null,
    val error: String? = null,
)

sealed interface ParallaxGalleryResult {
    data object Preparing : ParallaxGalleryResult
    data object Ready : ParallaxGalleryResult
    data class Failure(val message: String) : ParallaxGalleryResult
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val prefs: PreferencesManager,
    private val historyManager: WallpaperHistoryManager,
    private val offlineFavorites: OfflineFavoritesManager,
    private val wallpaperCacheManager: WallpaperCacheManager,
    private val collectionRepo: CollectionRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val videoWallpaperStorage: VideoWallpaperStorage,
    private val sourceMetrics: SourceMetrics,
    private val crashDiagnosticsCollector: CrashDiagnosticsCollector,
    private val backgroundWorkDiagnosticsReader: BackgroundWorkDiagnosticsReader,
    private val voteRepo: VoteRepository,
    private val communityBlockRepo: CommunityBlockRepository,
    private val communityIdentityProvider: CommunityIdentityProvider,
    private val ytDlpUpdateManager: YtDlpUpdateManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _parallaxGalleryResult = MutableStateFlow<ParallaxGalleryResult?>(null)
    val parallaxGalleryResult: StateFlow<ParallaxGalleryResult?> = _parallaxGalleryResult.asStateFlow()
    private val _videoWallpaperSelectionResult = MutableStateFlow<VideoWallpaperSelectionResult?>(null)
    val videoWallpaperSelectionResult: StateFlow<VideoWallpaperSelectionResult?> = _videoWallpaperSelectionResult.asStateFlow()

    fun clearParallaxGalleryResult() { _parallaxGalleryResult.value = null }
    fun clearVideoWallpaperSelectionResult() { _videoWallpaperSelectionResult.value = null }

    /**
     * Turn the user's gallery photo into a parallax live wallpaper. The caller (Settings
     * screen) is expected to launch the system's live-wallpaper picker once success is
     * observed — we can't safely call it from a ViewModel without an Activity.
     */
    fun applyParallaxFromGallery(uri: android.net.Uri) = viewModelScope.launch {
        _parallaxGalleryResult.value = ParallaxGalleryResult.Preparing
        val fileName = "parallax_user_photo.jpg"
        val result = wallpaperApplier.prepareParallaxFromUri(uri, fileName)
        _parallaxGalleryResult.value = result.fold(
            onSuccess = { ParallaxGalleryResult.Ready },
            onFailure = { ParallaxGalleryResult.Failure(it.message ?: "Could not prepare photo") },
        )
    }

    fun prepareVideoWallpaperFromUri(uri: android.net.Uri) = viewModelScope.launch {
        _videoWallpaperSelectionResult.value = VideoWallpaperSelectionResult.Preparing
        val result = videoWallpaperStorage.prepareFromUri(uri)
        _videoWallpaperSelectionResult.value = result.fold(
            onSuccess = { VideoWallpaperSelectionResult.Ready },
            onFailure = { VideoWallpaperSelectionResult.Failure(it.message ?: "Could not prepare video") },
        )
    }

    val autoWpEnabled = prefs.autoWallpaperEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoWpInterval = prefs.autoWallpaperInterval.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 12L)
    val autoWpSource = prefs.autoWallpaperSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "wallhaven")
    val localWallpaperFolderUri = prefs.localWallpaperFolderUri.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val autoWpRequiresCharging = prefs.autoWallpaperRequiresCharging.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoWpRequiresWiFi = prefs.autoWallpaperRequiresWiFiOnly.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoWpRequiresIdle = prefs.autoWallpaperRequiresIdle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoWallpaperDarkenPercent = prefs.autoWallpaperDarkenPercent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val autoBackupEnabled = prefs.autoBackupEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val autoBackupFolderUri = prefs.autoBackupFolderUri.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val autoBackupIntervalHours = prefs.autoBackupIntervalHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24L)
    val autoBackupKeepCount = prefs.autoBackupKeepCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)
    // NX-6: per-unlock / screen-off trigger opt-ins. Drive RotationTriggerService lifecycle.
    val rotateOnUnlock = prefs.rotateOnUnlock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val rotateOnScreenOff = prefs.rotateOnScreenOff.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val avoidRecentRepeats = prefs.avoidRecentRepeats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // Enhanced scheduler
    val schedulerEnabled = prefs.schedulerEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val schedulerInterval = prefs.schedulerIntervalMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 360L)
    val schedulerSource = prefs.schedulerSource.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "discover")
    val schedulerHome = prefs.schedulerHomeEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val schedulerLock = prefs.schedulerLockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val schedulerShuffle = prefs.schedulerShuffle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val weatherEffects = prefs.weatherEffectsEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val adaptiveTint = prefs.adaptiveTintEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val adaptiveTintIntensity = prefs.adaptiveTintIntensity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.3f)
    val reduceAnimations = prefs.reduceAnimations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val darkModeSwitch = prefs.darkModeAutoSwitch.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val darkModeWallpaperId = prefs.darkModeWallpaperId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val lightModeWallpaperId = prefs.lightModeWallpaperId.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val autoPreview = prefs.autoPreviewSounds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val gridColumns = prefs.wallpaperGridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)
    val previewVolume = prefs.soundPreviewVolume.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.7f)
    val ringtoneShuffleEnabled = prefs.ringtoneShuffleEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val ringtoneShuffleIntervalHours = prefs.ringtoneShuffleIntervalHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 24L)
    val alarmShuffleEnabled = prefs.alarmShuffleEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val soundProfilesEnabled = prefs.soundProfilesEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val liveWallpaperDimEnabled = prefs.liveWallpaperDimEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val soundProfilesJson = prefs.soundProfilesJson.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val redditSubs = prefs.redditSubreddits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "wallpapers,MobileWallpaper,MinimalWallpaper")
    val redditProviderEnabled = prefs.redditProviderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val preferredRes = prefs.preferredResolution.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val userStyles = prefs.userStyles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val ytRingtonesQuery = prefs.ytSoundQueryRingtones.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultRingtoneQuery())
    val ytNotificationsQuery = prefs.ytSoundQueryNotifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultNotificationQuery())
    val ytAlarmsQuery = prefs.ytSoundQueryAlarms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultAlarmQuery())
    val ytBlockedWords = prefs.ytSoundBlockedWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "compilation,mix,playlist,ranked,tier list,reaction,review,tutorial,how to,podcast,interview,live stream,part,episode")
    val youtubeProviderEnabled = prefs.youtubeProviderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val videoFpsLimit = prefs.videoFpsLimit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)
    val videoFpsOverlayEnabled = prefs.videoFpsOverlayEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val videoAutoBatterySaver = prefs.videoAutoBatterySaver.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val wallhavenApiKey = prefs.wallhavenApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pexelsApiKey = prefs.pexelsApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val pixabayApiKey = prefs.pixabayApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val freesoundApiKey = prefs.freesoundApiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val stabilityAiKey = prefs.stabilityAiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val generatedContentProviderEnabled = prefs.generatedContentProviderEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PreferencesManager.DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED,
    )
    val generatedContentDisclosureAccepted = prefs.generatedContentDisclosureAccepted.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )
    val wallhavenProviderEnabled = prefs.wallhavenProviderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val bingProviderEnabled = prefs.bingProviderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val pexelsProviderEnabled = prefs.pexelsProviderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val pixabayProviderEnabled = prefs.pixabayProviderEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val communityProviderEnabled = prefs.communityProviderEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PreferencesManager.DEFAULT_COMMUNITY_PROVIDER_ENABLED,
    )
    val communityGuidelinesAccepted = prefs.communityGuidelinesAccepted.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false,
    )
    val communityGuidelinesAcceptedVersion = prefs.communityGuidelinesAcceptedVersion.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0,
    )
    val showSketchyContent = prefs.showSketchyContent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showNsfwContent = prefs.showNsfwContent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isAdmin: Boolean get() = voteRepo.isAdmin
    val blockedCommunityCreators = communityBlockRepo.blockedUsers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _communityBlockAction = MutableStateFlow(CommunityBlockActionState())
    val communityBlockAction = _communityBlockAction.asStateFlow()
    private val _communityIdentityCleanup = MutableStateFlow(CommunityIdentityCleanupState())
    val communityIdentityCleanup = _communityIdentityCleanup.asStateFlow()
    private val _communityIdentitySummary = MutableStateFlow(communityIdentityProvider.currentIdentitySummary())
    val communityIdentitySummary = _communityIdentitySummary.asStateFlow()

    fun setShowSketchy(show: Boolean) = viewModelScope.launch { prefs.setShowSketchy(show) }
    fun setShowNsfw(show: Boolean) = viewModelScope.launch { prefs.setShowNsfw(show) }

    fun setYtRingtonesQuery(q: String) = viewModelScope.launch { prefs.setYtSoundQueryRingtones(q) }
    fun setYtNotificationsQuery(q: String) = viewModelScope.launch { prefs.setYtSoundQueryNotifications(q) }
    fun setYtAlarmsQuery(q: String) = viewModelScope.launch { prefs.setYtSoundQueryAlarms(q) }
    fun setYtBlockedWords(w: String) = viewModelScope.launch { prefs.setYtSoundBlockedWords(w) }
    fun setYoutubeProviderEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setYoutubeProviderEnabled(enabled) }

    // #11: Wallpaper history
    val wallpaperHistory = historyManager.getRecent(20).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    private val _cacheUsage = MutableStateFlow(CacheUsageState())
    val cacheUsage: StateFlow<CacheUsageState> = _cacheUsage.asStateFlow()
    private val _crashDiagnostics = MutableStateFlow(CrashDiagnosticsSummary())
    val crashDiagnostics: StateFlow<CrashDiagnosticsSummary> = _crashDiagnostics.asStateFlow()
    private val _backgroundWorkDiagnostics = MutableStateFlow(BackgroundWorkDiagnostics())
    val backgroundWorkDiagnostics: StateFlow<BackgroundWorkDiagnostics> =
        _backgroundWorkDiagnostics.asStateFlow()
    private val _externalAutomationDiagnostics = MutableStateFlow(
        ExternalAutomationGate.readDiagnostics(context),
    )
    val externalAutomationDiagnostics: StateFlow<ExternalAutomationDiagnostics> =
        _externalAutomationDiagnostics.asStateFlow()
    private val _ytDlpUpdate = MutableStateFlow(
        YtDlpUpdateUiState(snapshot = ytDlpUpdateManager.snapshot()),
    )
    val ytDlpUpdate = _ytDlpUpdate.asStateFlow()

    init {
        refreshCacheUsage()
        refreshCrashDiagnostics()
        refreshBackgroundWorkDiagnostics()
        refreshExternalAutomationDiagnostics()
    }

    fun setAutoWallpaper(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoWallpaperEnabled(enabled)
        if (enabled) {
            AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
        } else {
            AutoWallpaperWorker.cancel(context)
        }
    }

    fun setAutoWpInterval(hours: Long) = viewModelScope.launch {
        prefs.setAutoWallpaperInterval(hours)
        if (autoWpEnabled.value) {
            AutoWallpaperWorker.schedule(context, hours * 60)
        }
    }

    // #10: Set auto-wallpaper source
    fun setAutoWpSource(source: String) = viewModelScope.launch {
        prefs.setAutoWallpaperSource(source)
    }

    fun setLocalWallpaperFolderUri(uri: String) = viewModelScope.launch {
        val nextUri = uri.trim()
        val previousUri = prefs.localWallpaperFolderUri.first().trim()
        prefs.setLocalWallpaperFolderUri(nextUri)
        if (previousUri.isNotBlank() && previousUri != nextUri) {
            releasePersistedUriPermission(previousUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clearLocalWallpaperFolderUri() = viewModelScope.launch {
        val uri = prefs.localWallpaperFolderUri.first().trim()
        if (uri.isNotBlank()) releasePersistedUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.setLocalWallpaperFolderUri("")
    }

    // T-7: Auto-wallpaper rotation constraints. Re-schedule on toggle so the
    // running worker picks up the new constraint set without waiting for the
    // next interval boundary.
    fun setAutoWallpaperRequiresCharging(v: Boolean) = viewModelScope.launch {
        prefs.setAutoWallpaperRequiresCharging(v)
        if (autoWpEnabled.value) AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
    }
    fun setAutoWallpaperRequiresWiFiOnly(v: Boolean) = viewModelScope.launch {
        prefs.setAutoWallpaperRequiresWiFiOnly(v)
        if (autoWpEnabled.value) AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
    }
    // NX-6: toggle per-unlock / screen-off triggers and reconcile the foreground
    // [RotationTriggerService]. Service is stopped automatically when both flags
    // settle to false. The user sees a "Wallpaper triggers active" notification
    // while at least one trigger is on.
    fun setRotateOnUnlock(v: Boolean) = viewModelScope.launch {
        prefs.setRotateOnUnlock(v)
        com.freevibe.service.RotationTriggerService.reconcile(
            context,
            unlock = v,
            screenOff = rotateOnScreenOff.value,
        )
    }
    fun setRotateOnScreenOff(v: Boolean) = viewModelScope.launch {
        prefs.setRotateOnScreenOff(v)
        com.freevibe.service.RotationTriggerService.reconcile(
            context,
            unlock = rotateOnUnlock.value,
            screenOff = v,
        )
    }

    fun setAvoidRecentRepeats(v: Boolean) = viewModelScope.launch {
        prefs.setAvoidRecentRepeats(v)
        if (!v) prefs.clearRecentRotationIds()
    }

    fun setAutoWallpaperRequiresIdle(v: Boolean) = viewModelScope.launch {
        prefs.setAutoWallpaperRequiresIdle(v)
        if (autoWpEnabled.value) AutoWallpaperWorker.schedule(context, autoWpInterval.value * 60)
    }

    fun setAutoWallpaperDarkenPercent(percent: Int) = viewModelScope.launch {
        prefs.setAutoWallpaperDarkenPercent(percent)
    }

    fun setAutoBackupEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoBackupEnabled(enabled)
        if (enabled) {
            AutoBackupWorker.schedule(context)
        } else {
            AutoBackupWorker.cancel(context)
        }
    }

    fun setAutoBackupFolderUri(uri: String) = viewModelScope.launch {
        val nextUri = uri.trim()
        val previousUri = prefs.autoBackupFolderUri.first().trim()
        prefs.setAutoBackupFolderUri(nextUri)
        if (previousUri.isNotBlank() && previousUri != nextUri) {
            releasePersistedUriPermission(
                previousUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        if (autoBackupEnabled.value) AutoBackupWorker.schedule(context)
    }

    fun clearAutoBackupFolderUri() = viewModelScope.launch {
        val uri = prefs.autoBackupFolderUri.first().trim()
        if (uri.isNotBlank()) {
            releasePersistedUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.setAutoBackupEnabled(false)
        prefs.setAutoBackupFolderUri("")
        AutoBackupWorker.cancel(context)
    }

    fun setAutoBackupIntervalHours(hours: Long) = viewModelScope.launch {
        prefs.setAutoBackupIntervalHours(hours.coerceAtLeast(1L))
        if (autoBackupEnabled.value) AutoBackupWorker.schedule(context)
    }

    fun setAutoBackupKeepCount(count: Int) = viewModelScope.launch {
        prefs.setAutoBackupKeepCount(count.coerceAtLeast(1))
        if (autoBackupEnabled.value) AutoBackupWorker.schedule(context)
    }

    private fun releasePersistedUriPermission(uriString: String, flags: Int) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)
        }
    }

    // T-6: Source diagnostics. Emits live snapshots while the dialog is visible.
    val diagnostics = sourceMetrics.version
        .map { sourceMetrics.snapshotAll() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sourceMetrics.snapshotAll())
    fun resetDiagnostics() = sourceMetrics.reset()

    fun refreshCrashDiagnostics() = viewModelScope.launch {
        _crashDiagnostics.value = withContext(ioDispatcher) {
            crashDiagnosticsCollector.readSummary()
        }
    }

    suspend fun buildCrashDiagnosticsBundle(): String = withContext(ioDispatcher) {
        crashDiagnosticsCollector.buildBundle()
    }

    fun refreshBackgroundWorkDiagnostics() = viewModelScope.launch {
        _backgroundWorkDiagnostics.value = withContext(ioDispatcher) {
            backgroundWorkDiagnosticsReader.read()
        }
    }

    fun setExternalAutomationEnabled(enabled: Boolean) = viewModelScope.launch {
        _externalAutomationDiagnostics.value = withContext(ioDispatcher) {
            ExternalAutomationGate.setEnabled(context, enabled)
            ExternalAutomationGate.readDiagnostics(context)
        }
    }

    fun refreshExternalAutomationDiagnostics() = viewModelScope.launch {
        _externalAutomationDiagnostics.value = withContext(ioDispatcher) {
            ExternalAutomationGate.readDiagnostics(context)
        }
    }

    fun updateYtDlp() {
        if (_ytDlpUpdate.value.isUpdating) return
        viewModelScope.launch {
            _ytDlpUpdate.update {
                it.copy(
                    snapshot = ytDlpUpdateManager.snapshot(),
                    isUpdating = true,
                    completedStatus = null,
                    error = null,
                )
            }
            val result = runCatching { ytDlpUpdateManager.updateStable() }
                .getOrElse { error ->
                    YtDlpUpdateResult(
                        status = YtDlpUpdateStatus.FAILED,
                        snapshot = ytDlpUpdateManager.snapshot().copy(
                            lastStatus = YtDlpUpdateStatus.FAILED,
                            lastError = error.message ?: error.javaClass.simpleName,
                        ),
                    )
                }
            _ytDlpUpdate.value = YtDlpUpdateUiState(
                snapshot = result.snapshot,
                isUpdating = false,
                completedStatus = result.status,
                error = result.snapshot.lastError,
            )
        }
    }

    fun clearYtDlpUpdateNotice() {
        _ytDlpUpdate.update { it.copy(completedStatus = null, error = null) }
    }

    fun clearWallpaperHistory() = viewModelScope.launch {
        historyManager.clearAll()
    }

    fun setAutoPreview(enabled: Boolean) = viewModelScope.launch {
        prefs.setAutoPreview(enabled)
    }

    fun setGridColumns(columns: Int) = viewModelScope.launch {
        prefs.setGridColumns(columns)
    }

    fun setPreviewVolume(volume: Float) = viewModelScope.launch {
        prefs.setPreviewVolume(volume)
    }

    fun setRingtoneShuffleEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setRingtoneShuffleEnabled(enabled)
        if (enabled) {
            val interval = prefs.ringtoneShuffleIntervalHours.first()
            RingtoneShuffleWorker.schedule(context, interval)
        } else {
            RingtoneShuffleWorker.cancel(context)
        }
    }

    fun setRingtoneShuffleIntervalHours(hours: Long) = viewModelScope.launch {
        prefs.setRingtoneShuffleIntervalHours(hours)
        if (prefs.ringtoneShuffleEnabled.first()) {
            RingtoneShuffleWorker.schedule(context, hours)
        }
    }

    fun setAlarmShuffleEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setAlarmShuffleEnabled(enabled)
        if (enabled && !prefs.ringtoneShuffleEnabled.first()) {
            val interval = prefs.ringtoneShuffleIntervalHours.first()
            RingtoneShuffleWorker.schedule(context, interval)
        }
    }

    fun setSoundProfilesEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSoundProfilesEnabled(enabled)
        if (enabled) {
            com.freevibe.service.SoundProfileWorker.schedule(context)
        } else {
            com.freevibe.service.SoundProfileWorker.cancel(context)
        }
    }

    fun setSoundProfilesJson(json: String) = viewModelScope.launch {
        prefs.setSoundProfilesJson(json)
        prefs.setSoundProfileLastAppliedId("")
    }

    fun setRedditSubs(subs: String) = viewModelScope.launch {
        prefs.setRedditSubreddits(subs)
    }
    fun setRedditProviderEnabled(enabled: Boolean) = viewModelScope.launch { prefs.setRedditProviderEnabled(enabled) }

    fun setPreferredRes(res: String) = viewModelScope.launch {
        prefs.setPreferredResolution(res)
    }

    fun setUserStyles(styles: String) = viewModelScope.launch {
        prefs.setUserStyles(styles)
    }

    fun setWallhavenKey(key: String) = viewModelScope.launch {
        prefs.setWallhavenKey(key)
    }

    fun setPexelsKey(key: String) = viewModelScope.launch {
        prefs.setPexelsKey(key)
    }

    fun setPixabayKey(key: String) = viewModelScope.launch {
        prefs.setPixabayKey(key)
    }

    fun setWallhavenProviderEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setWallhavenProviderEnabled(enabled)
    }

    fun setBingProviderEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setBingProviderEnabled(enabled)
    }

    fun setPexelsProviderEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setPexelsProviderEnabled(enabled)
    }

    fun setPixabayProviderEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setPixabayProviderEnabled(enabled)
    }

    fun setCommunityProviderEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setCommunityProviderEnabled(enabled)
    }
    fun acceptCommunityGuidelines() = viewModelScope.launch {
        prefs.acceptCommunityGuidelines()
    }
    fun resetCommunityGuidelines() = viewModelScope.launch {
        prefs.resetCommunityGuidelines()
    }

    fun unblockCommunityCreator(userId: String) = viewModelScope.launch {
        if (userId.isBlank()) return@launch
        _communityBlockAction.value = CommunityBlockActionState(unblockingUserId = userId)
        communityBlockRepo.unblockUser(userId)
            .onSuccess {
                _communityBlockAction.value = CommunityBlockActionState(message = "Creator unblocked")
            }
            .onFailure { error ->
                _communityBlockAction.value = CommunityBlockActionState(
                    error = "Unblock failed: ${error.message ?: "try again"}",
                )
            }
    }

    fun clearCommunityBlockAction() {
        _communityBlockAction.value = CommunityBlockActionState()
    }

    fun refreshCommunityIdentitySummary() {
        _communityIdentitySummary.value = communityIdentityProvider.currentIdentitySummary()
    }

    fun clearLocalCommunityIdentity() = viewModelScope.launch {
        _communityIdentityCleanup.value = CommunityIdentityCleanupState(clearing = true)
        val result = withContext(ioDispatcher) {
            runCatching { communityIdentityProvider.clearLocalFallbackIdentity() }
        }
        result
            .onSuccess { cleared ->
                refreshCommunityIdentitySummary()
                _communityIdentityCleanup.value = CommunityIdentityCleanupState(
                    message = if (cleared) {
                        "Local community identity cleared"
                    } else {
                        "No local community identity was stored"
                    },
                )
            }
            .onFailure { error ->
                _communityIdentityCleanup.value = CommunityIdentityCleanupState(
                    error = "Local cleanup failed: ${error.message ?: "try again"}",
                )
            }
    }

    fun clearCommunityIdentityCleanupState() {
        _communityIdentityCleanup.value = CommunityIdentityCleanupState()
    }

    fun setFreesoundKey(key: String) = viewModelScope.launch {
        prefs.setFreesoundKey(key)
    }

    fun setVideoFpsLimit(fps: Int) = viewModelScope.launch {
        prefs.setVideoFpsLimit(fps)
    }
    fun setVideoFpsOverlayEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setVideoFpsOverlayEnabled(enabled)
    }
    fun setVideoAutoBatterySaver(enabled: Boolean) = viewModelScope.launch {
        prefs.setVideoAutoBatterySaver(enabled)
    }

    fun setSchedulerEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setSchedulerEnabled(enabled)
        if (enabled) {
            AutoWallpaperWorker.schedule(context, schedulerInterval.value)
        } else {
            AutoWallpaperWorker.cancel(context)
        }
    }

    fun setSchedulerInterval(minutes: Long) = viewModelScope.launch {
        prefs.setSchedulerInterval(minutes)
        if (schedulerEnabled.value) AutoWallpaperWorker.schedule(context, minutes)
    }

    fun setSchedulerSource(source: String) = viewModelScope.launch { prefs.setSchedulerSource(source) }

    // Collection rotation ----------------------------------------------------
    val collections: StateFlow<List<WallpaperCollectionEntity>> = collectionRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val schedulerCollectionId = prefs.schedulerCollectionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1L)

    /**
     * Pick a specific collection to rotate from. Also flips the source to "collection" so
     * the next scheduler tick actually reads from it.
     */
    fun setSchedulerCollection(id: Long) = viewModelScope.launch {
        prefs.setSchedulerCollection(id)
        prefs.setSchedulerSource("collection")
    }
    fun setSchedulerHome(enabled: Boolean) = viewModelScope.launch { prefs.setSchedulerHome(enabled) }
    fun setSchedulerLock(enabled: Boolean) = viewModelScope.launch { prefs.setSchedulerLock(enabled) }
    fun setSchedulerShuffle(shuffle: Boolean) = viewModelScope.launch { prefs.setSchedulerShuffle(shuffle) }
    fun setWeatherEffects(enabled: Boolean) = viewModelScope.launch { prefs.setWeatherEffectsEnabled(enabled) }
    fun setReduceAnimations(enabled: Boolean) = viewModelScope.launch {
        prefs.setReduceAnimations(enabled)
        context.getSharedPreferences("freevibe_weather_wp", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("reduce_animations", enabled).apply()
    }
    fun setAdaptiveTint(enabled: Boolean) = viewModelScope.launch {
        prefs.setAdaptiveTintEnabled(enabled)
        // Bridge to SharedPreferences so WeatherWallpaperService can read it synchronously
        context.getSharedPreferences("freevibe_weather_wp", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("adaptive_tint_enabled", enabled).apply()
    }
    fun setAdaptiveTintIntensity(intensity: Float) = viewModelScope.launch {
        prefs.setAdaptiveTintIntensity(intensity)
        context.getSharedPreferences("freevibe_weather_wp", android.content.Context.MODE_PRIVATE)
            .edit().putFloat("adaptive_tint_intensity", intensity).apply()
    }
    fun setLiveWallpaperDimEnabled(enabled: Boolean) = viewModelScope.launch {
        prefs.setLiveWallpaperDimEnabled(enabled)
        context.getSharedPreferences("freevibe_weather_wp", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("live_wallpaper_dim_enabled", enabled).apply()
    }
    fun setStabilityKey(key: String) = viewModelScope.launch { prefs.setStabilityKey(key) }
    fun setGeneratedContentProviderEnabled(enabled: Boolean) =
        viewModelScope.launch { prefs.setGeneratedContentProviderEnabled(enabled) }
    fun acceptGeneratedContentDisclosure() =
        viewModelScope.launch { prefs.setGeneratedContentDisclosureAccepted(true) }
    fun resetGeneratedContentDisclosure() =
        viewModelScope.launch { prefs.setGeneratedContentDisclosureAccepted(false) }
    fun setDarkModeSwitch(enabled: Boolean) = viewModelScope.launch { prefs.setDarkModeAutoSwitch(enabled) }
    fun setDarkModeWallpaperId(id: String) = viewModelScope.launch { prefs.setDarkModeWallpaperId(id) }
    fun setLightModeWallpaperId(id: String) = viewModelScope.launch { prefs.setLightModeWallpaperId(id) }

    fun clearCache() = viewModelScope.launch {
        withContext(ioDispatcher) {
            val cacheDir = context.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name != "trimmed") {
                    file.deleteRecursively()
                }
            }
            offlineFavorites.clearAll()
            wallpaperCacheManager.clearAll()
        }
        refreshCacheUsage()
    }

    private fun refreshCacheUsage() = viewModelScope.launch {
        _cacheUsage.value = withContext(ioDispatcher) {
            val cacheBytes = context.cacheDir
                .takeIf { it.exists() }
                ?.walkTopDown()
                ?.filter { it.isFile && it.parentFile?.name != "trimmed" }
                ?.sumOf { it.length() }
                ?: 0L
            CacheUsageState(
                fileUsageLabel = formatBytes(cacheBytes + offlineFavorites.getCacheSize()),
                hasWallpaperMetadataCache = wallpaperCacheManager.countEntries() > 0,
            )
        }
    }

    private fun formatBytes(bytes: Long): String {
        // Use Locale.ROOT so the decimal separator is always '.' — displaying "1,5 MB" in
        // German/French locales looks broken for a raw byte label.
        val root = java.util.Locale.ROOT
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(root, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format(root, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(root, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}
