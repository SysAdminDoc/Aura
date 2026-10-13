package com.freevibe.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.freevibe.R
import com.freevibe.service.AuraPickVisualMedia
import com.freevibe.service.DailyWallpaperWorker
import com.freevibe.service.ParallaxWallpaperService
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.VideoWallpaperService
import com.freevibe.service.WeatherUpdateWorker
import com.freevibe.service.videoWallpaperMimeTypes
import com.freevibe.ui.LiveWallpaperSource
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.launchLiveWallpaperWithFeedback
import com.freevibe.ui.liveWallpaperBlockMessage
import com.freevibe.ui.navigation.Screen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialSection: String? = null,
    navigation: SettingsNavigation = SettingsNavigation(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    // Reading a string off LocalContext is not a composition read. LocalResources is.
    val resources = LocalResources.current
    val feedbackScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    fun showSettingsFeedback(message: String) {
        feedbackScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val settingsState = rememberSettingsScreenState(viewModel, context)
    var dailyWallpaperEnabled by remember(viewModel) {
        mutableStateOf(viewModel.isDailyWallpaperEnabled())
    }
    fun setDailyWallpaperEnabled(enabled: Boolean) {
        dailyWallpaperEnabled = enabled
        viewModel.setDailyWallpaperEnabled(enabled)
        if (enabled) DailyWallpaperWorker.schedule(context) else DailyWallpaperWorker.cancel(context)
    }

    fun enableWeatherEffects() {
        viewModel.setWeatherEffects(true)
        WeatherUpdateWorker.schedule(context)
    }

    fun disableWeatherEffects() {
        viewModel.setWeatherEffects(false)
        WeatherUpdateWorker.cancel(context)
        WeatherUpdateWorker.clearStoredWeatherState(context)
    }

    fun openAppSettings(): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null)),
        )
        true
    } catch (_: Exception) {
        false
    }

    fun openNotificationSettings(): Boolean {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            openAppSettings()
        } catch (_: Exception) {
            false
        }
    }

    var showLocalWallpaperCatalog by remember { mutableStateOf(false) }
    val chooseLocalWallpaperFolder = rememberLocalWallpaperFolderPicker(
        context = context,
        resources = resources,
        viewModel = viewModel,
        onFeedback = ::showSettingsFeedback,
    )
    var enableAutoBackupAfterFolder by remember { mutableStateOf(false) }
    val backupFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        val shouldEnableAfterFolder = enableAutoBackupAfterFolder
        enableAutoBackupAfterFolder = false
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.isSuccess
            viewModel.setAutoBackupFolderUri(uri.toString())
            if (persisted && shouldEnableAfterFolder) {
                viewModel.setAutoBackupEnabled(true)
                showSettingsFeedback(resources.getString(R.string.settings_feedback_backup_folder_on))
            } else if (persisted) {
                showSettingsFeedback(resources.getString(R.string.settings_feedback_backup_folder_saved))
            } else {
                showSettingsFeedback(resources.getString(R.string.settings_feedback_backup_folder_no_persist))
            }
        }
    }

    fun chooseAutoBackupFolder(enableAfterSelection: Boolean = false) {
        enableAutoBackupAfterFolder = enableAfterSelection
        backupFolderPickerLauncher.launch(null)
    }
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.prepareVideoWallpaperFromUri(it) }
    }
    val parallaxGalleryLauncher = rememberLauncherForActivityResult(
        AuraPickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { viewModel.applyParallaxFromGallery(it) }
    }

    LaunchedEffect(settingsState.parallaxGalleryResult) {
        when (val result = settingsState.parallaxGalleryResult) {
            ParallaxGalleryResult.Ready -> {
                showSettingsFeedback(
                    launchLiveWallpaperWithFeedback(
                        context, ParallaxWallpaperService::class.java, "SettingsParallaxGallery", LiveWallpaperSource.IMAGE,
                        R.string.settings_feedback_parallax_direct, R.string.settings_feedback_parallax_chooser,
                    ),
                )
                viewModel.clearParallaxGalleryResult()
            }
            is ParallaxGalleryResult.Failure -> {
                showSettingsFeedback(resources.getString(R.string.settings_feedback_parallax_failed, result.message))
                viewModel.clearParallaxGalleryResult()
            }
            else -> Unit
        }
    }

    LaunchedEffect(settingsState.videoWallpaperSelectionResult) {
        when (val result = settingsState.videoWallpaperSelectionResult) {
            VideoWallpaperSelectionResult.Ready -> {
                showSettingsFeedback(
                    launchLiveWallpaperWithFeedback(
                        context, VideoWallpaperService::class.java, "SettingsVideoWallpaper", LiveWallpaperSource.VIDEO,
                        R.string.settings_feedback_video_direct, R.string.settings_feedback_video_chooser,
                    ),
                )
                viewModel.clearVideoWallpaperSelectionResult()
            }
            is VideoWallpaperSelectionResult.Failure -> {
                showSettingsFeedback(result.message)
                viewModel.clearVideoWallpaperSelectionResult()
            }
            else -> Unit
        }
    }

    var settingsPermissionPrompt by remember { mutableStateOf<SettingsPermissionPrompt?>(null) }
    var settingsSearchQuery by remember { mutableStateOf("") }
    val settingsSearchRegistry = remember { SettingsSearchRegistry() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            setDailyWallpaperEnabled(true)
        } else {
            setDailyWallpaperEnabled(false)
            settingsPermissionPrompt = SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            enableWeatherEffects()
        } else {
            disableWeatherEffects()
            settingsPermissionPrompt = SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY
        }
    }

    settingsPermissionPrompt?.let { prompt ->
        SettingsPermissionPromptDialog(
            prompt = prompt,
            onDismiss = { settingsPermissionPrompt = null },
            onLaunchNotificationPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onLaunchLocationPermission = {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            },
            onEnableDailyWallpaper = { setDailyWallpaperEnabled(true) },
            onOpenNotificationSettings = ::openNotificationSettings,
            onOpenAppSettings = ::openAppSettings,
            onFeedback = ::showSettingsFeedback,
        )
    }

    val rotationExclusionControls = RotationExclusionSettingsHost(
        showLocalCatalog = showLocalWallpaperCatalog,
        settingsState = settingsState,
        settingsViewModel = viewModel,
        onDismissLocalCatalog = { showLocalWallpaperCatalog = false },
        onAddLocalFolder = { chooseLocalWallpaperFolder("catalog") },
        snackbarHostState = snackbarHostState,
    )

    with(settingsState) {
    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            SettingsSearchScope(
                context = context,
                registry = settingsSearchRegistry,
                query = settingsSearchQuery,
                onQueryChange = { settingsSearchQuery = it },
            ) { visibleSectionKeys ->
            if (SettingsSectionKeys.WALLPAPERS in visibleSectionKeys) WallpaperRotationSettingsSection(
                context = context,
                viewModel = viewModel,
                autoWpEnabled = autoWpEnabled,
                autoWpInterval = autoWpInterval,
                autoWpSource = autoWpSource,
                autoWpTarget = autoWpTarget,
                localWallpaperFolderUri = localWallpaperFolderUri,
                localFolderPermissionActive = localFolderPermissionActive,
                localWallpaperFolderCount = localWallpaperFolders.size,
                localCatalogReady = localWallpaperCatalogReady,
                autoWpRequiresCharging = autoWpRequiresCharging,
                autoWpRequiresWiFi = autoWpRequiresWiFi,
                autoWpRequiresIdle = autoWpRequiresIdle,
                autoWallpaperDarkenPercent = autoWallpaperDarkenPercent,
                autoWallpaperNightVariantEnabled = autoWallpaperNightVariantEnabled,
                schedulerEnabled = schedulerEnabled,
                rotateOnUnlock = rotateOnUnlock,
                rotateOnScreenOff = rotateOnScreenOff,
                avoidRecentRepeats = avoidRecentRepeats,
                restartRotationOnManualApply = restartRotationOnManualApply,
                wallpaperPackEnabled = wallpaperPackEnabled,
                wallpaperPackJson = wallpaperPackJson,
                externalAutomationDiagnostics = externalAutomationDiagnostics,
                gridColumns = gridColumns,
                preferredRes = preferredRes,
                userStyles = userStyles,
                wallpaperStyleLearningSignalCount = wallpaperStyleLearningSignalCount,
                bingProviderEnabled = bingProviderEnabled,
                redditProviderEnabled = redditProviderEnabled,
                wallhavenProviderEnabled = wallhavenProviderEnabled,
                pixabayUsable = pixabayUsable,
                wallpaperSearchReachable = wallpaperSearchReachable,
                wallpaperHistoryCount = wallpaperHistory.size,
                rotationExclusionCount = rotationExclusionControls.exclusionCount,
                wallpaperClockOverlayEnabled = wallpaperClockOverlayEnabled,
                wallpaperClockOverlayMode = wallpaperClockOverlayMode,
                wallpaperClockOverlayPosition = wallpaperClockOverlayPosition,
                onChooseLocalWallpaperFolder = chooseLocalWallpaperFolder,
                onManageLocalWallpaperFolders = { showLocalWallpaperCatalog = true },
                // Both rows check first, so nobody picks a file for a wallpaper the phone can't show.
                onPickVideoWallpaper = {
                    liveWallpaperBlockMessage(context, VideoWallpaperService::class.java, LiveWallpaperSource.VIDEO)
                        ?.let(::showSettingsFeedback)
                        ?: videoPickerLauncher.launch(videoWallpaperMimeTypes())
                },
                onPickParallaxImage = {
                    liveWallpaperBlockMessage(context, ParallaxWallpaperService::class.java, LiveWallpaperSource.IMAGE)
                        ?.let(::showSettingsFeedback)
                        ?: parallaxGalleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                },
                onCategoriesClick = navigation.onCategoriesClick,
                onCollectionsClick = navigation.onCollectionsClick,
                onHistoryClick = navigation.onHistoryClick,
                onManageRotationExclusions = rotationExclusionControls.openManager,
                onFeedback = ::showSettingsFeedback,
            )
            if (SettingsSectionKeys.SCHEDULER in visibleSectionKeys) SchedulerSettingsSection(
                context = context,
                viewModel = viewModel,
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
                localWallpaperFolderUri = localWallpaperFolderUri,
                localFolderPermissionActive = localFolderPermissionActive,
                localCatalogReady = localWallpaperCatalogReady,
                wallhavenProviderEnabled = wallhavenProviderEnabled,
                pixabayUsable = pixabayUsable,
                bingProviderEnabled = bingProviderEnabled,
                onChooseLocalWallpaperFolder = chooseLocalWallpaperFolder,
            )
            if (SettingsSectionKeys.BACKUP in visibleSectionKeys) SettingsSectionAnchorTarget(Screen.Settings.BACKUP_SECTION, initialSection) {
                BackupSettingsSection(
                    context = context,
                    viewModel = viewModel,
                    autoBackupEnabled = autoBackupEnabled,
                    autoBackupFolderUri = autoBackupFolderUri,
                    autoBackupFolderPermissionActive = autoBackupFolderPermissionActive,
                    autoBackupIntervalHours = autoBackupIntervalHours,
                    autoBackupKeepCount = autoBackupKeepCount,
                    themePackTransfer = themePackTransfer,
                    onChooseAutoBackupFolder = ::chooseAutoBackupFolder,
                    onFeedback = ::showSettingsFeedback,
                )
            }
            if (SettingsSectionKeys.SMART in visibleSectionKeys) SmartLiveWallpaperSettingsSection(
                context = context,
                viewModel = viewModel,
                dailyWallpaperEnabled = dailyWallpaperEnabled,
                adaptiveTint = adaptiveTint,
                adaptiveTintIntensity = adaptiveTintIntensity,
                weatherEffects = weatherEffects,
                darkModeSwitch = darkModeSwitch,
                darkModeWallpaperId = darkModeWallpaperId,
                lightModeWallpaperId = lightModeWallpaperId,
                liveWallpaperShaderPreset = liveWallpaperShaderPreset,
                wallpaperHistory = wallpaperHistory,
                reduceAnimations = reduceAnimations,
                liveWallpaperDimEnabled = liveWallpaperDimEnabled,
                liveWallpaperColorsEnabled = liveWallpaperColorsEnabled,
                initialVfxEffect = viewModel.weatherVfxEffect(),
                initialTouchEffectStrength = viewModel.touchEffectStrength(),
                onSetDailyWallpaperEnabled = ::setDailyWallpaperEnabled,
                onEnableWeatherEffects = ::enableWeatherEffects,
                onDisableWeatherEffects = ::disableWeatherEffects,
                onPermissionPrompt = { settingsPermissionPrompt = it },
            )

            if (SettingsSectionKeys.SOUNDS in visibleSectionKeys) SoundSettingsSection(
                viewModel = viewModel,
                autoPreview = autoPreview,
                previewVolume = previewVolume,
                ytRingtonesQuery = ytRingtonesQuery,
                ytNotificationsQuery = ytNotificationsQuery,
                ytAlarmsQuery = ytAlarmsQuery,
                ytBlockedWords = ytBlockedWords,
                youtubeProviderEnabled = youtubeProviderEnabled,
                youtubePoTokenProviderUrl = youtubePoTokenProviderUrl,
                soundProfilesEnabled = soundProfilesEnabled,
                soundProfilesJson = soundProfilesJson,
                ytDlpUpdate = ytDlpUpdate,
                onLicensesClick = navigation.onLicensesClick,
                onFeedback = ::showSettingsFeedback,
            )

            if (SettingsSectionKeys.VIDEO in visibleSectionKeys) VideoSettingsSection(
                viewModel = viewModel,
                videoFpsLimit = videoFpsLimit,
                videoPlaybackSpeed = videoPlaybackSpeed,
                videoFpsOverlayEnabled = videoFpsOverlayEnabled,
                videoAutoBatterySaver = videoAutoBatterySaver,
                videoBatteryDashboard = videoBatteryDashboard,
            )

            if (SettingsSectionKeys.SERVICES in visibleSectionKeys) ServicesCommunitySettingsSection(
                context = context,
                viewModel = viewModel,
                communityProviderEnabled = communityProviderEnabled,
                communityGuidelinesAccepted = communityGuidelinesAccepted,
                communityGuidelinesAcceptedVersion = communityGuidelinesAcceptedVersion,
                communityIdentitySummary = communityIdentitySummary,
                communityIdentityCleanup = communityIdentityCleanup,
                blockedCommunityCreators = blockedCommunityCreators,
                communityBlockAction = communityBlockAction,
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
                pexelsProviderEnabled = pexelsProviderEnabled,
                pixabayProviderEnabled = pixabayProviderEnabled,
                showSketchyContent = showSketchyContent,
                showNsfwContent = showNsfwContent,
                onCreatorProfileClick = navigation.onCreatorProfileClick,
                onCommunityReportsClick = navigation.onCommunityReportsClick,
                onGeneratedWallpapersClick = navigation.onGeneratedWallpapersClick,
                onFeedback = ::showSettingsFeedback,
            )

            if (SettingsSectionKeys.STORAGE in visibleSectionKeys) StorageSettingsSection(
                viewModel = viewModel,
                cacheUsage = cacheUsage,
                generatedAssets = generatedAssets,
                onDownloadsClick = navigation.onDownloadsClick,
            )

            if (SettingsSectionKeys.DIAGNOSTICS in visibleSectionKeys) DiagnosticsSettingsSection(
                context = context,
                viewModel = viewModel,
                diagnostics = diagnostics,
                crashDiagnostics = crashDiagnostics,
                liveWallpaperLiveness = liveWallpaperLiveness,
                backgroundWorkDiagnostics = backgroundWorkDiagnostics,
                externalAutomationDiagnostics = externalAutomationDiagnostics,
                onFeedback = ::showSettingsFeedback,
                onRotationHealth = navigation.onRotationHealthClick,
            )

            if (SettingsSectionKeys.PERMISSIONS in visibleSectionKeys) PermissionsSettingsSection(context)
            if (SettingsSectionKeys.ABOUT in visibleSectionKeys) {
                AboutSettingsSection(context = context, viewModel = viewModel, onLicensesClick = navigation.onLicensesClick)
            }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    }
}
