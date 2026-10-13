package com.freevibe.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.data.model.LocalWallpaperEntity
import com.freevibe.data.model.LocalWallpaperFolderEntity
import com.freevibe.data.model.LocalWallpaperFolderScanStatus
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.data.repository.CommunityBlockedUser
import com.freevibe.data.repository.GeneratedAssetAudit
import com.freevibe.service.BackgroundWorkDiagnostics
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.CrashDiagnosticsSummary
import com.freevibe.service.ExternalAutomationDiagnostics
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.WallpaperClockOverlayMode
import com.freevibe.service.WallpaperClockOverlayPosition

/**
 * Where Settings can send you.
 *
 * These were nine loose lambda parameters on `SettingsScreen`, which is held to a
 * 500-line ceiling it kept bumping into — every new destination cost a line in
 * the signature and a line at the call site. Grouping them also stops the
 * parameter list from being the place a reader has to look to find out what
 * Settings links to.
 */
@Immutable
data class SettingsNavigation(
    val onDownloadsClick: () -> Unit = {},
    val onLicensesClick: () -> Unit = {},
    val onCategoriesClick: () -> Unit = {},
    val onHistoryClick: () -> Unit = {},
    val onCollectionsClick: () -> Unit = {},
    val onCreatorProfileClick: () -> Unit = {},
    val onCommunityReportsClick: () -> Unit = {},
    val onGeneratedWallpapersClick: () -> Unit = {},
    val onRotationHealthClick: () -> Unit = {},
)

internal data class SettingsScreenState(
    val autoWpEnabled: Boolean,
    val autoWpInterval: Long,
    val autoWpSource: String,
    val autoWpTarget: String,
    val localWallpaperFolderUri: String,
    val localFolderPermissionActive: Boolean,
    val localWallpaperFolders: List<LocalWallpaperFolderEntity>,
    val localWallpaperItems: List<LocalWallpaperEntity>,
    val localWallpaperCatalogReady: Boolean,
    val autoWpRequiresCharging: Boolean,
    val autoWpRequiresWiFi: Boolean,
    val autoWpRequiresIdle: Boolean,
    val autoWallpaperDarkenPercent: Int,
    val autoWallpaperNightVariantEnabled: Boolean,
    val autoBackupEnabled: Boolean,
    val autoBackupFolderUri: String,
    val autoBackupFolderPermissionActive: Boolean,
    val autoBackupIntervalHours: Long,
    val autoBackupKeepCount: Int,
    val rotateOnUnlock: Boolean,
    val rotateOnScreenOff: Boolean,
    val avoidRecentRepeats: Boolean,
    val autoPreview: Boolean,
    val wallpaperHistory: List<WallpaperHistoryEntity>,
    val gridColumns: Int,
    val ytRingtonesQuery: String,
    val ytNotificationsQuery: String,
    val ytAlarmsQuery: String,
    val ytBlockedWords: String,
    val youtubeProviderEnabled: Boolean,
    val youtubePoTokenProviderUrl: String,
    val previewVolume: Float,
    val soundProfilesEnabled: Boolean,
    val liveWallpaperDimEnabled: Boolean,
    val liveWallpaperColorsEnabled: Boolean,
    val wallpaperClockOverlayEnabled: Boolean,
    val wallpaperClockOverlayMode: WallpaperClockOverlayMode,
    val wallpaperClockOverlayPosition: WallpaperClockOverlayPosition,
    val soundProfilesJson: String,
    val wallpaperPackEnabled: Boolean,
    val wallpaperPackJson: String,
    val preferredRes: String,
    val userStyles: String,
    val wallpaperStyleLearningSignalCount: Int,
    val schedulerEnabled: Boolean,
    val schedulerInterval: Long,
    val schedulerSource: String,
    val schedulerDaySource: String,
    val schedulerNightSource: String,
    val schedulerDayNightMode: String,
    val schedulerDayStartHour: Int,
    val schedulerNightStartHour: Int,
    val schedulerHome: Boolean,
    val schedulerLock: Boolean,
    val schedulerShuffle: Boolean,
    val weatherEffects: Boolean,
    val adaptiveTint: Boolean,
    val adaptiveTintIntensity: Float,
    val reduceAnimations: Boolean,
    val darkModeSwitch: Boolean,
    val darkModeWallpaperId: String,
    val lightModeWallpaperId: String,
    val liveWallpaperShaderPreset: String,
    val videoFpsLimit: Int,
    val videoPlaybackSpeed: Float,
    val wallhavenApiKey: String,
    val pexelsApiKey: String,
    val pixabayApiKey: String,
    val generatedWallpaperProviderKey: String,
    val providerCredentialStorageUnavailable: Boolean,
    val providerCredentialReentryRequired: Boolean,
    val providerCredentialReentryKeys: Set<String>,
    val generatedContentProviderEnabled: Boolean,
    val generatedContentDisclosureAccepted: Boolean,
    val wallhavenProviderEnabled: Boolean,
    val bingProviderEnabled: Boolean,
    val redditProviderEnabled: Boolean,
    val redditSubreddits: String,
    val redditVideoSubreddits: String,
    val pexelsProviderEnabled: Boolean,
    val pixabayProviderEnabled: Boolean,
    val communityProviderEnabled: Boolean,
    val communityGuidelinesAccepted: Boolean,
    val communityGuidelinesAcceptedVersion: Int,
    val blockedCommunityCreators: List<CommunityBlockedUser>,
    val communityBlockAction: CommunityBlockActionState,
    val communityIdentityCleanup: CommunityIdentityCleanupState,
    val communityIdentitySummary: CommunityIdentitySummary,
    val showSketchyContent: Boolean,
    val showNsfwContent: Boolean,
    val videoFpsOverlayEnabled: Boolean,
    val videoAutoBatterySaver: Boolean,
    val cacheUsage: CacheUsageState,
    val generatedAssets: GeneratedAssetAudit,
    val diagnostics: List<SourceMetrics.SourceStats>,
    val crashDiagnostics: CrashDiagnosticsSummary,
    val liveWallpaperLiveness: com.freevibe.service.LiveWallpaperLivenessState?,
    val backgroundWorkDiagnostics: BackgroundWorkDiagnostics,
    val externalAutomationDiagnostics: ExternalAutomationDiagnostics,
    val videoWallpaperSelectionResult: VideoWallpaperSelectionResult?,
    val ytDlpUpdate: YtDlpUpdateUiState,
    val themePackTransfer: ThemePackTransferState,
    val parallaxGalleryResult: ParallaxGalleryResult?,
    val videoBatteryDashboard: VideoBatteryDashboardState,
    val selectedStyleCount: Int,
    val configuredApiKeys: Int,
)

@Composable
internal fun rememberSettingsScreenState(
    viewModel: SettingsViewModel,
    context: Context,
): SettingsScreenState {
    val autoWpEnabled by viewModel.autoWpEnabled.collectAsStateWithLifecycle()
    val autoWpInterval by viewModel.autoWpInterval.collectAsStateWithLifecycle()
    val autoWpSource by viewModel.autoWpSource.collectAsStateWithLifecycle()
    val autoWpTarget by viewModel.autoWpTarget.collectAsStateWithLifecycle()
    val localWallpaperFolderUri by viewModel.localWallpaperFolderUri.collectAsStateWithLifecycle()
    val localWallpaperFolders by viewModel.localWallpaperFolders.collectAsStateWithLifecycle()
    val localWallpaperItems by viewModel.localWallpaperItems.collectAsStateWithLifecycle()
    val localWallpaperCatalogReady = remember(localWallpaperFolders) {
        localWallpaperFolders.any {
            it.scanStatus == LocalWallpaperFolderScanStatus.READY ||
                it.scanStatus == LocalWallpaperFolderScanStatus.READY_LIMITED ||
                it.scanStatus == LocalWallpaperFolderScanStatus.READY_PARTIAL
        }
    }
    val autoWpRequiresCharging by viewModel.autoWpRequiresCharging.collectAsStateWithLifecycle()
    val autoWpRequiresWiFi by viewModel.autoWpRequiresWiFi.collectAsStateWithLifecycle()
    val autoWpRequiresIdle by viewModel.autoWpRequiresIdle.collectAsStateWithLifecycle()
    val autoWallpaperDarkenPercent by viewModel.autoWallpaperDarkenPercent.collectAsStateWithLifecycle()
    val autoWallpaperNightVariantEnabled by viewModel.autoWallpaperNightVariantEnabled.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupFolderUri by viewModel.autoBackupFolderUri.collectAsStateWithLifecycle()
    val autoBackupIntervalHours by viewModel.autoBackupIntervalHours.collectAsStateWithLifecycle()
    val autoBackupKeepCount by viewModel.autoBackupKeepCount.collectAsStateWithLifecycle()
    val rotateOnUnlock by viewModel.rotateOnUnlock.collectAsStateWithLifecycle()
    val rotateOnScreenOff by viewModel.rotateOnScreenOff.collectAsStateWithLifecycle()
    val avoidRecentRepeats by viewModel.avoidRecentRepeats.collectAsStateWithLifecycle()
    val autoPreview by viewModel.autoPreview.collectAsStateWithLifecycle()
    val wallpaperHistory by viewModel.wallpaperHistory.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
    val ytRingtonesQuery by viewModel.ytRingtonesQuery.collectAsStateWithLifecycle()
    val ytNotificationsQuery by viewModel.ytNotificationsQuery.collectAsStateWithLifecycle()
    val ytAlarmsQuery by viewModel.ytAlarmsQuery.collectAsStateWithLifecycle()
    val ytBlockedWords by viewModel.ytBlockedWords.collectAsStateWithLifecycle()
    val youtubeProviderEnabled by viewModel.youtubeProviderEnabled.collectAsStateWithLifecycle()
    val youtubePoTokenProviderUrl by viewModel.youtubePoTokenProviderUrl.collectAsStateWithLifecycle()
    val previewVolume by viewModel.previewVolume.collectAsStateWithLifecycle()
    val soundProfilesEnabled by viewModel.soundProfilesEnabled.collectAsStateWithLifecycle()
    val liveWallpaperDimEnabled by viewModel.liveWallpaperDimEnabled.collectAsStateWithLifecycle()
    val liveWallpaperColorsEnabled by viewModel.liveWallpaperColorsEnabled.collectAsStateWithLifecycle()
    val wallpaperClockOverlayEnabled by viewModel.wallpaperClockOverlayEnabled.collectAsStateWithLifecycle()
    val wallpaperClockOverlayMode by viewModel.wallpaperClockOverlayMode.collectAsStateWithLifecycle()
    val wallpaperClockOverlayPosition by viewModel.wallpaperClockOverlayPosition.collectAsStateWithLifecycle()
    val soundProfilesJson by viewModel.soundProfilesJson.collectAsStateWithLifecycle()
    val wallpaperPackEnabled by viewModel.wallpaperPackEnabled.collectAsStateWithLifecycle()
    val wallpaperPackJson by viewModel.wallpaperPackJson.collectAsStateWithLifecycle()
    val preferredRes by viewModel.preferredRes.collectAsStateWithLifecycle()
    val userStyles by viewModel.userStyles.collectAsStateWithLifecycle()
    val wallpaperStyleLearningSignalCount by viewModel.wallpaperStyleLearningSignalCount.collectAsStateWithLifecycle()
    val schedulerEnabled by viewModel.schedulerEnabled.collectAsStateWithLifecycle()
    val schedulerInterval by viewModel.schedulerInterval.collectAsStateWithLifecycle()
    val schedulerSource by viewModel.schedulerSource.collectAsStateWithLifecycle()
    val schedulerDaySource by viewModel.schedulerDaySource.collectAsStateWithLifecycle()
    val schedulerNightSource by viewModel.schedulerNightSource.collectAsStateWithLifecycle()
    val schedulerDayNightMode by viewModel.schedulerDayNightMode.collectAsStateWithLifecycle()
    val schedulerDayStartHour by viewModel.schedulerDayStartHour.collectAsStateWithLifecycle()
    val schedulerNightStartHour by viewModel.schedulerNightStartHour.collectAsStateWithLifecycle()
    val schedulerHome by viewModel.schedulerHome.collectAsStateWithLifecycle()
    val schedulerLock by viewModel.schedulerLock.collectAsStateWithLifecycle()
    val schedulerShuffle by viewModel.schedulerShuffle.collectAsStateWithLifecycle()
    val weatherEffects by viewModel.weatherEffects.collectAsStateWithLifecycle()
    val adaptiveTint by viewModel.adaptiveTint.collectAsStateWithLifecycle()
    val adaptiveTintIntensity by viewModel.adaptiveTintIntensity.collectAsStateWithLifecycle()
    val reduceAnimations by viewModel.reduceAnimations.collectAsStateWithLifecycle()
    val darkModeSwitch by viewModel.darkModeSwitch.collectAsStateWithLifecycle()
    val darkModeWallpaperId by viewModel.darkModeWallpaperId.collectAsStateWithLifecycle()
    val lightModeWallpaperId by viewModel.lightModeWallpaperId.collectAsStateWithLifecycle()
    val liveWallpaperShaderPreset by viewModel.liveWallpaperShaderPreset.collectAsStateWithLifecycle()
    val videoFpsLimit by viewModel.videoFpsLimit.collectAsStateWithLifecycle()
    val videoPlaybackSpeed by viewModel.videoPlaybackSpeed.collectAsStateWithLifecycle()
    val wallhavenApiKey by viewModel.wallhavenApiKey.collectAsStateWithLifecycle()
    val pexelsApiKey by viewModel.pexelsApiKey.collectAsStateWithLifecycle()
    val pixabayApiKey by viewModel.pixabayApiKey.collectAsStateWithLifecycle()
    val generatedWallpaperProviderKey by viewModel.generatedWallpaperProviderKey.collectAsStateWithLifecycle()
    val providerCredentialStorageUnavailable by viewModel.providerCredentialStorageUnavailable.collectAsStateWithLifecycle()
    val providerCredentialReentryRequired by viewModel.providerCredentialReentryRequired.collectAsStateWithLifecycle()
    val providerCredentialReentryKeys by viewModel.providerCredentialReentryKeys.collectAsStateWithLifecycle()
    val generatedContentProviderEnabled by viewModel.generatedContentProviderEnabled.collectAsStateWithLifecycle()
    val generatedContentDisclosureAccepted by viewModel.generatedContentDisclosureAccepted.collectAsStateWithLifecycle()
    val wallhavenProviderEnabled by viewModel.wallhavenProviderEnabled.collectAsStateWithLifecycle()
    val bingProviderEnabled by viewModel.bingProviderEnabled.collectAsStateWithLifecycle()
    val redditProviderEnabled by viewModel.redditProviderEnabled.collectAsStateWithLifecycle()
    val redditSubreddits by viewModel.redditSubs.collectAsStateWithLifecycle()
    val redditVideoSubreddits by viewModel.redditVideoSubs.collectAsStateWithLifecycle()
    val pexelsProviderEnabled by viewModel.pexelsProviderEnabled.collectAsStateWithLifecycle()
    val pixabayProviderEnabled by viewModel.pixabayProviderEnabled.collectAsStateWithLifecycle()
    val communityProviderEnabled by viewModel.communityProviderEnabled.collectAsStateWithLifecycle()
    val communityGuidelinesAccepted by viewModel.communityGuidelinesAccepted.collectAsStateWithLifecycle()
    val communityGuidelinesAcceptedVersion by viewModel.communityGuidelinesAcceptedVersion.collectAsStateWithLifecycle()
    val blockedCommunityCreators by viewModel.blockedCommunityCreators.collectAsStateWithLifecycle()
    val communityBlockAction by viewModel.communityBlockAction.collectAsStateWithLifecycle()
    val communityIdentityCleanup by viewModel.communityIdentityCleanup.collectAsStateWithLifecycle()
    val communityIdentitySummary by viewModel.communityIdentitySummary.collectAsStateWithLifecycle()
    val showSketchyContent by viewModel.showSketchyContent.collectAsStateWithLifecycle()
    val showNsfwContent by viewModel.showNsfwContent.collectAsStateWithLifecycle()
    val videoFpsOverlayEnabled by viewModel.videoFpsOverlayEnabled.collectAsStateWithLifecycle()
    val videoAutoBatterySaver by viewModel.videoAutoBatterySaver.collectAsStateWithLifecycle()
    val cacheUsage by viewModel.cacheUsage.collectAsStateWithLifecycle()
    val generatedAssets by viewModel.generatedAssets.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val crashDiagnostics by viewModel.crashDiagnostics.collectAsStateWithLifecycle()
    val liveWallpaperLiveness by viewModel.liveWallpaperLiveness.collectAsStateWithLifecycle()
    val backgroundWorkDiagnostics by viewModel.backgroundWorkDiagnostics.collectAsStateWithLifecycle()
    val externalAutomationDiagnostics by viewModel.externalAutomationDiagnostics.collectAsStateWithLifecycle()
    val videoWallpaperSelectionResult by viewModel.videoWallpaperSelectionResult.collectAsStateWithLifecycle()
    val ytDlpUpdate by viewModel.ytDlpUpdate.collectAsStateWithLifecycle()
    val themePackTransfer by viewModel.themePackTransfer.collectAsStateWithLifecycle()
    val parallaxGalleryResult by viewModel.parallaxGalleryResult.collectAsStateWithLifecycle()
    val videoBatteryDashboard by rememberVideoBatteryDashboardState(
        context = context,
        requestedFps = videoFpsLimit,
        fpsOverlayEnabled = videoFpsOverlayEnabled,
        autoBatterySaverEnabled = videoAutoBatterySaver,
    )
    val localFolderPermissionActive = remember(localWallpaperFolderUri) {
        hasPersistedReadPermission(context, localWallpaperFolderUri)
    }
    val autoBackupFolderPermissionActive = remember(autoBackupFolderUri) {
        hasPersistedWritePermission(context, autoBackupFolderUri)
    }
    val selectedStyleCount = remember(userStyles) { countSelectedStyles(userStyles) }
    val configuredApiKeys = remember(
        wallhavenApiKey,
        pexelsApiKey,
        pixabayApiKey,
        generatedWallpaperProviderKey,
    ) {
        listOf(wallhavenApiKey, pexelsApiKey, pixabayApiKey, generatedWallpaperProviderKey)
            .count { it.isNotBlank() }
    }

    return SettingsScreenState(
        autoWpEnabled = autoWpEnabled,
        autoWpInterval = autoWpInterval,
        autoWpSource = autoWpSource,
        autoWpTarget = autoWpTarget,
        localWallpaperFolderUri = localWallpaperFolderUri,
        localFolderPermissionActive = localFolderPermissionActive,
        localWallpaperFolders = localWallpaperFolders,
        localWallpaperItems = localWallpaperItems,
        localWallpaperCatalogReady = localWallpaperCatalogReady,
        autoWpRequiresCharging = autoWpRequiresCharging,
        autoWpRequiresWiFi = autoWpRequiresWiFi,
        autoWpRequiresIdle = autoWpRequiresIdle,
        autoWallpaperDarkenPercent = autoWallpaperDarkenPercent,
        autoWallpaperNightVariantEnabled = autoWallpaperNightVariantEnabled,
        autoBackupEnabled = autoBackupEnabled,
        autoBackupFolderUri = autoBackupFolderUri,
        autoBackupFolderPermissionActive = autoBackupFolderPermissionActive,
        autoBackupIntervalHours = autoBackupIntervalHours,
        autoBackupKeepCount = autoBackupKeepCount,
        rotateOnUnlock = rotateOnUnlock,
        rotateOnScreenOff = rotateOnScreenOff,
        avoidRecentRepeats = avoidRecentRepeats,
        autoPreview = autoPreview,
        wallpaperHistory = wallpaperHistory,
        gridColumns = gridColumns,
        ytRingtonesQuery = ytRingtonesQuery,
        ytNotificationsQuery = ytNotificationsQuery,
        ytAlarmsQuery = ytAlarmsQuery,
        ytBlockedWords = ytBlockedWords,
        youtubeProviderEnabled = youtubeProviderEnabled,
        youtubePoTokenProviderUrl = youtubePoTokenProviderUrl,
        previewVolume = previewVolume,
        soundProfilesEnabled = soundProfilesEnabled,
        liveWallpaperDimEnabled = liveWallpaperDimEnabled,
        liveWallpaperColorsEnabled = liveWallpaperColorsEnabled,
        wallpaperClockOverlayEnabled = wallpaperClockOverlayEnabled,
        wallpaperClockOverlayMode = WallpaperClockOverlayMode.fromPreference(wallpaperClockOverlayMode),
        wallpaperClockOverlayPosition = WallpaperClockOverlayPosition.fromPreference(wallpaperClockOverlayPosition),
        soundProfilesJson = soundProfilesJson,
        wallpaperPackEnabled = wallpaperPackEnabled,
        wallpaperPackJson = wallpaperPackJson,
        preferredRes = preferredRes,
        userStyles = userStyles,
        wallpaperStyleLearningSignalCount = wallpaperStyleLearningSignalCount,
        schedulerEnabled = schedulerEnabled,
        schedulerInterval = schedulerInterval,
        schedulerSource = schedulerSource,
        schedulerDaySource = schedulerDaySource,
        schedulerNightSource = schedulerNightSource,
        schedulerDayNightMode = schedulerDayNightMode,
        schedulerDayStartHour = schedulerDayStartHour,
        schedulerNightStartHour = schedulerNightStartHour,
        schedulerHome = schedulerHome,
        schedulerLock = schedulerLock,
        schedulerShuffle = schedulerShuffle,
        weatherEffects = weatherEffects,
        adaptiveTint = adaptiveTint,
        adaptiveTintIntensity = adaptiveTintIntensity,
        reduceAnimations = reduceAnimations,
        darkModeSwitch = darkModeSwitch,
        darkModeWallpaperId = darkModeWallpaperId,
        lightModeWallpaperId = lightModeWallpaperId,
        liveWallpaperShaderPreset = liveWallpaperShaderPreset,
        videoFpsLimit = videoFpsLimit,
        videoPlaybackSpeed = videoPlaybackSpeed,
        wallhavenApiKey = wallhavenApiKey,
        pexelsApiKey = pexelsApiKey,
        pixabayApiKey = pixabayApiKey,
        generatedWallpaperProviderKey = generatedWallpaperProviderKey,
        providerCredentialStorageUnavailable = providerCredentialStorageUnavailable,
        providerCredentialReentryRequired = providerCredentialReentryRequired,
        providerCredentialReentryKeys = providerCredentialReentryKeys,
        generatedContentProviderEnabled = generatedContentProviderEnabled,
        generatedContentDisclosureAccepted = generatedContentDisclosureAccepted,
        wallhavenProviderEnabled = wallhavenProviderEnabled,
        bingProviderEnabled = bingProviderEnabled,
        redditProviderEnabled = redditProviderEnabled,
        redditSubreddits = redditSubreddits,
        redditVideoSubreddits = redditVideoSubreddits,
        pexelsProviderEnabled = pexelsProviderEnabled,
        pixabayProviderEnabled = pixabayProviderEnabled,
        communityProviderEnabled = communityProviderEnabled,
        communityGuidelinesAccepted = communityGuidelinesAccepted,
        communityGuidelinesAcceptedVersion = communityGuidelinesAcceptedVersion,
        blockedCommunityCreators = blockedCommunityCreators,
        communityBlockAction = communityBlockAction,
        communityIdentityCleanup = communityIdentityCleanup,
        communityIdentitySummary = communityIdentitySummary,
        showSketchyContent = showSketchyContent,
        showNsfwContent = showNsfwContent,
        videoFpsOverlayEnabled = videoFpsOverlayEnabled,
        videoAutoBatterySaver = videoAutoBatterySaver,
        cacheUsage = cacheUsage,
        generatedAssets = generatedAssets,
        diagnostics = diagnostics,
        crashDiagnostics = crashDiagnostics,
        liveWallpaperLiveness = liveWallpaperLiveness,
        backgroundWorkDiagnostics = backgroundWorkDiagnostics,
        externalAutomationDiagnostics = externalAutomationDiagnostics,
        videoWallpaperSelectionResult = videoWallpaperSelectionResult,
        ytDlpUpdate = ytDlpUpdate,
        themePackTransfer = themePackTransfer,
        parallaxGalleryResult = parallaxGalleryResult,
        videoBatteryDashboard = videoBatteryDashboard,
        selectedStyleCount = selectedStyleCount,
        configuredApiKeys = configuredApiKeys,
    )
}
