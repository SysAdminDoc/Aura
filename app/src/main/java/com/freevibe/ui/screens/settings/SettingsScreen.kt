package com.freevibe.ui.screens.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER
import com.freevibe.data.repository.CommunityBlockedUser
import com.freevibe.service.COMMUNITY_DELETION_REQUEST_SUBJECT
import com.freevibe.service.BackgroundWorkDiagnostics
import com.freevibe.service.BackgroundWorkStatusRow
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.CrashDiagnosticsSummary
import com.freevibe.service.DailyWallpaperWorker
import com.freevibe.service.ExternalAutomationDiagnostics
import com.freevibe.service.OemBatteryGuidance
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VIDEO_STATS_PREFS_NAME
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.WeatherUpdateWorker
import com.freevibe.service.VideoWallpaperService
import com.freevibe.service.YtDlpUpdateStatus
import com.freevibe.service.communityDeletionRequestBody
import com.freevibe.service.effectiveVideoFpsLimit
import com.freevibe.service.shouldUseVideoBatterySaver
import com.freevibe.service.videoBatteryImpactSummary
import com.freevibe.service.videoWallpaperMimeTypes
import com.freevibe.ui.LiveWallpaperLaunchMode
import com.freevibe.ui.components.CommunityGuidelinesDialog
import com.freevibe.ui.components.GlassCard
import com.freevibe.ui.components.HighlightPill
import com.freevibe.ui.components.AuraSnackbarHost
import com.freevibe.ui.screens.aigenerate.GeneratedWallpaperDisclosureDialog
import com.freevibe.ui.launchLiveWallpaperPicker
import com.freevibe.ui.util.openExternalUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val AURA_SOURCE_URL = "https://github.com/SysAdminDoc/Aura"
private const val AURA_PRIVACY_POLICY_URL = "https://github.com/SysAdminDoc/Aura/blob/main/docs/privacy/privacy-policy.md"
private const val OPEN_METEO_LICENCE_URL = "https://open-meteo.com/en/licence"

private enum class SettingsPermissionPrompt {
    DAILY_NOTIFICATION_REQUEST,
    DAILY_NOTIFICATION_RECOVERY,
    WEATHER_LOCATION_REQUEST,
    WEATHER_LOCATION_RECOVERY,
}

@Composable
private fun ProviderApiKeyDialog(
    title: String,
    description: String,
    value: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyText by remember(value) { mutableStateOf(value) }
    val canClear = keyText.isNotBlank() || value.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(keyText.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = canClear,
                    onClick = {
                        onSave("")
                        onDismiss()
                    },
                ) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onDownloadsClick: () -> Unit = {},
    onLicensesClick: () -> Unit = {},
    onCategoriesClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onCollectionsClick: () -> Unit = {},
    onCreatorProfileClick: () -> Unit = {},
    onCommunityReportsClick: () -> Unit = {},
    onGeneratedWallpapersClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val diagnosticsScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    fun showSettingsFeedback(message: String) {
        diagnosticsScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val autoWpEnabled by viewModel.autoWpEnabled.collectAsStateWithLifecycle()
    val autoWpInterval by viewModel.autoWpInterval.collectAsStateWithLifecycle()
    val autoWpSource by viewModel.autoWpSource.collectAsStateWithLifecycle()
    val localWallpaperFolderUri by viewModel.localWallpaperFolderUri.collectAsStateWithLifecycle()
    val autoWpRequiresCharging by viewModel.autoWpRequiresCharging.collectAsStateWithLifecycle()
    val autoWpRequiresWiFi by viewModel.autoWpRequiresWiFi.collectAsStateWithLifecycle()
    val autoWpRequiresIdle by viewModel.autoWpRequiresIdle.collectAsStateWithLifecycle()
    val autoWallpaperDarkenPercent by viewModel.autoWallpaperDarkenPercent.collectAsStateWithLifecycle()
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
    val previewVolume by viewModel.previewVolume.collectAsStateWithLifecycle()
    val ringtoneShuffleEnabled by viewModel.ringtoneShuffleEnabled.collectAsStateWithLifecycle()
    val ringtoneShuffleIntervalHours by viewModel.ringtoneShuffleIntervalHours.collectAsStateWithLifecycle()
    val preferredRes by viewModel.preferredRes.collectAsStateWithLifecycle()
    val userStyles by viewModel.userStyles.collectAsStateWithLifecycle()
    val schedulerEnabled by viewModel.schedulerEnabled.collectAsStateWithLifecycle()
    val schedulerInterval by viewModel.schedulerInterval.collectAsStateWithLifecycle()
    val schedulerSource by viewModel.schedulerSource.collectAsStateWithLifecycle()
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
    val videoFpsLimit by viewModel.videoFpsLimit.collectAsStateWithLifecycle()
    val wallhavenApiKey by viewModel.wallhavenApiKey.collectAsStateWithLifecycle()
    val pexelsApiKey by viewModel.pexelsApiKey.collectAsStateWithLifecycle()
    val pixabayApiKey by viewModel.pixabayApiKey.collectAsStateWithLifecycle()
    val freesoundApiKey by viewModel.freesoundApiKey.collectAsStateWithLifecycle()
    val stabilityAiKey by viewModel.stabilityAiKey.collectAsStateWithLifecycle()
    val generatedContentProviderEnabled by viewModel.generatedContentProviderEnabled.collectAsStateWithLifecycle()
    val generatedContentDisclosureAccepted by viewModel.generatedContentDisclosureAccepted.collectAsStateWithLifecycle()
    val wallhavenProviderEnabled by viewModel.wallhavenProviderEnabled.collectAsStateWithLifecycle()
    val bingProviderEnabled by viewModel.bingProviderEnabled.collectAsStateWithLifecycle()
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
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val crashDiagnostics by viewModel.crashDiagnostics.collectAsStateWithLifecycle()
    val backgroundWorkDiagnostics by viewModel.backgroundWorkDiagnostics.collectAsStateWithLifecycle()
    val externalAutomationDiagnostics by viewModel.externalAutomationDiagnostics.collectAsStateWithLifecycle()
    val videoWallpaperSelectionResult by viewModel.videoWallpaperSelectionResult.collectAsStateWithLifecycle()
    val ytDlpUpdate by viewModel.ytDlpUpdate.collectAsStateWithLifecycle()
    val videoBatteryDashboard by rememberVideoBatteryDashboardState(
        context = context,
        requestedFps = videoFpsLimit,
        fpsOverlayEnabled = videoFpsOverlayEnabled,
        autoBatterySaverEnabled = videoAutoBatterySaver,
    )
    var dailyWp by remember {
        mutableStateOf(
            context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                .getBoolean("daily_wallpaper_enabled", false)
        )
    }
    val localFolderPermissionActive = remember(localWallpaperFolderUri) {
        hasPersistedReadPermission(context, localWallpaperFolderUri)
    }
    val autoBackupFolderPermissionActive = remember(autoBackupFolderUri) {
        hasPersistedWritePermission(context, autoBackupFolderUri)
    }

    fun setDailyWallpaperEnabled(enabled: Boolean) {
        dailyWp = enabled
        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
            .edit().putBoolean("daily_wallpaper_enabled", enabled).apply()
        if (enabled) DailyWallpaperWorker.schedule(context)
        else DailyWallpaperWorker.cancel(context)
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

    fun openAppSettings(): Boolean {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openNotificationSettings(): Boolean {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: android.content.ActivityNotFoundException) {
            // Some OEM Android builds (e.g. custom MIUI/EMUI skins without the stock settings
            // activity) don't handle ACTION_APP_NOTIFICATION_SETTINGS and crash with ANFE.
            // Fall back to the app-details page which every Android install ships.
            openAppSettings()
        } catch (_: Exception) {
            false
        }
    }

    // Video wallpaper picker
    var pendingLocalFolderSource by remember { mutableStateOf<String?>(null) }
    val localFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val target = pendingLocalFolderSource
        pendingLocalFolderSource = null
        if (uri != null) {
            val persisted = runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            viewModel.setLocalWallpaperFolderUri(uri.toString())
            when (target) {
                "auto" -> viewModel.setAutoWpSource(WALLPAPER_SOURCE_LOCAL_FOLDER)
                "scheduler" -> viewModel.setSchedulerSource(WALLPAPER_SOURCE_LOCAL_FOLDER)
            }
            showSettingsFeedback(
                if (persisted) {
                    "Local wallpaper folder saved"
                } else {
                    "Folder selected. If rotation cannot read it, choose the folder again."
                },
            )
        }
    }

    fun chooseLocalWallpaperFolder(target: String? = null) {
        pendingLocalFolderSource = target
        localFolderPickerLauncher.launch(null)
    }

    var enableAutoBackupAfterFolder by remember { mutableStateOf(false) }
    val backupFolderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
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
                showSettingsFeedback("Backup folder saved. Scheduled backup is on.")
            } else if (persisted) {
                showSettingsFeedback("Backup folder saved")
            } else {
                showSettingsFeedback("Folder selected. If backups cannot write there, choose the folder again.")
            }
        }
    }

    fun chooseAutoBackupFolder(enableAfterSelection: Boolean = false) {
        enableAutoBackupAfterFolder = enableAfterSelection
        backupFolderPickerLauncher.launch(null)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.prepareVideoWallpaperFromUri(it) }
    }
    // Gallery picker for parallax-from-user-photo (v6.1.0).
    // NX-11: AuraPickVisualMedia attaches the Android 17 9:16 portrait grid hint.
    val parallaxGalleryLauncher = rememberLauncherForActivityResult(
        com.freevibe.service.AuraPickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.applyParallaxFromGallery(it) }
    }
    val parallaxGalleryResult by viewModel.parallaxGalleryResult.collectAsStateWithLifecycle()
    LaunchedEffect(parallaxGalleryResult) {
        val result = parallaxGalleryResult
        when (result) {
            com.freevibe.ui.screens.settings.ParallaxGalleryResult.Ready -> {
                when (
                    launchLiveWallpaperPicker(
                        context = context,
                        serviceComponent = ComponentName(context, com.freevibe.service.ParallaxWallpaperService::class.java),
                        tag = "SettingsParallaxGallery",
                    )
                ) {
                    LiveWallpaperLaunchMode.DIRECT -> showSettingsFeedback("Aura Parallax opened. Set wallpaper to finish.")
                    LiveWallpaperLaunchMode.CHOOSER -> showSettingsFeedback("Choose 'Aura Parallax' in the picker, then tap Set wallpaper.")
                    null -> showSettingsFeedback("Photo ready. Open Settings > Wallpaper > Live Wallpapers to finish.")
                }
                viewModel.clearParallaxGalleryResult()
            }
            is com.freevibe.ui.screens.settings.ParallaxGalleryResult.Failure -> {
                showSettingsFeedback("Couldn't use that photo: ${result.message}")
                viewModel.clearParallaxGalleryResult()
            }
            else -> Unit
        }
    }
    LaunchedEffect(videoWallpaperSelectionResult) {
        when (val result = videoWallpaperSelectionResult) {
            VideoWallpaperSelectionResult.Ready -> {
                when (
                    launchLiveWallpaperPicker(
                        context = context,
                        serviceComponent = ComponentName(context, VideoWallpaperService::class.java),
                        tag = "SettingsVideoWallpaper",
                    )
                ) {
                    LiveWallpaperLaunchMode.DIRECT -> {
                        showSettingsFeedback("Aura Video Wallpaper opened. Set wallpaper to finish.")
                    }
                    LiveWallpaperLaunchMode.CHOOSER -> {
                        showSettingsFeedback("Choose 'Aura Video Wallpaper' in the picker, then tap Set wallpaper.")
                    }
                    null -> {
                        showSettingsFeedback("Motion wallpaper selected. Open Settings > Wallpaper > Live Wallpapers to finish setup.")
                    }
                }
                viewModel.clearVideoWallpaperSelectionResult()
            }
            is VideoWallpaperSelectionResult.Failure -> {
                showSettingsFeedback(result.message)
                viewModel.clearVideoWallpaperSelectionResult()
            }
            else -> Unit
        }
    }
    LaunchedEffect(communityBlockAction.message, communityBlockAction.error) {
        communityBlockAction.message?.let {
            showSettingsFeedback(it)
            viewModel.clearCommunityBlockAction()
        }
        communityBlockAction.error?.let {
            showSettingsFeedback(it)
            viewModel.clearCommunityBlockAction()
        }
    }
    LaunchedEffect(communityIdentityCleanup.message, communityIdentityCleanup.error) {
        communityIdentityCleanup.message?.let {
            showSettingsFeedback(it)
            viewModel.clearCommunityIdentityCleanupState()
        }
        communityIdentityCleanup.error?.let {
            showSettingsFeedback(it)
            viewModel.clearCommunityIdentityCleanupState()
        }
    }
    val ytDlpUpdateNotice = ytDlpUpdateFeedbackMessage(ytDlpUpdate)
    LaunchedEffect(ytDlpUpdate.completedStatus, ytDlpUpdate.error) {
        ytDlpUpdateNotice?.let {
            showSettingsFeedback(it)
            viewModel.clearYtDlpUpdateNotice()
        }
    }

    var settingsPermissionPrompt by remember { mutableStateOf<SettingsPermissionPrompt?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            setDailyWallpaperEnabled(true)
        } else {
            setDailyWallpaperEnabled(false)
            settingsPermissionPrompt = SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableWeatherEffects()
        } else {
            disableWeatherEffects()
            settingsPermissionPrompt = SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY
        }
    }

    // Dialog state
    var showIntervalPicker by remember { mutableStateOf(false) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showColumnsPicker by remember { mutableStateOf(false) }
    var showResPicker by remember { mutableStateOf(false) }
    var showStylePicker by remember { mutableStateOf(false) }
    var showYtSoundEditor by remember { mutableStateOf(false) }
    var showYtBlockedEditor by remember { mutableStateOf(false) }
    var showBlockedCreators by remember { mutableStateOf(false) }
    var showCommunityIdentity by remember { mutableStateOf(false) }
    var showCommunityGuidelines by remember { mutableStateOf(false) }
    var showDarkModeWallpaperPicker by remember { mutableStateOf(false) }
    var showLightModeWallpaperPicker by remember { mutableStateOf(false) }
    var showBackgroundWorkDiagnostics by remember { mutableStateOf(false) }
    var showExternalAutomationDiagnostics by remember { mutableStateOf(false) }
    var showCrashDiagnostics by remember { mutableStateOf(false) }
    var showAutoBackupIntervalPicker by remember { mutableStateOf(false) }
    var showAutoBackupKeepPicker by remember { mutableStateOf(false) }
    var crashDiagnosticsBusy by remember { mutableStateOf(false) }
    var touchEffectStrength by remember {
        mutableStateOf(
            context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                .getString("touch_effect_strength", "OFF") ?: "OFF"
        )
    }
    val selectedStyleCount = remember(userStyles) { countSelectedStyles(userStyles) }
    val configuredApiKeys = remember(
        wallhavenApiKey,
        pexelsApiKey,
        pixabayApiKey,
        freesoundApiKey,
        stabilityAiKey,
    ) {
        listOf(wallhavenApiKey, pexelsApiKey, pixabayApiKey, freesoundApiKey, stabilityAiKey).count { it.isNotBlank() }
    }

    settingsPermissionPrompt?.let { prompt ->
        val isRecovery = when (prompt) {
            SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY,
            SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY -> true
            SettingsPermissionPrompt.DAILY_NOTIFICATION_REQUEST,
            SettingsPermissionPrompt.WEATHER_LOCATION_REQUEST -> false
        }
        AlertDialog(
            onDismissRequest = { settingsPermissionPrompt = null },
            title = {
                Text(
                    when (prompt) {
                        SettingsPermissionPrompt.DAILY_NOTIFICATION_REQUEST,
                        SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY -> stringResource(R.string.permission_notification_title)
                        SettingsPermissionPrompt.WEATHER_LOCATION_REQUEST,
                        SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY -> stringResource(R.string.permission_location_title)
                    },
                )
            },
            text = {
                Text(
                    when (prompt) {
                        SettingsPermissionPrompt.DAILY_NOTIFICATION_REQUEST -> stringResource(R.string.permission_notification_body)
                        SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY -> stringResource(R.string.permission_notification_denied_body)
                        SettingsPermissionPrompt.WEATHER_LOCATION_REQUEST -> stringResource(R.string.permission_location_body)
                        SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY -> stringResource(R.string.permission_location_denied_body)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsPermissionPrompt = null
                        val settingsOpened = when (prompt) {
                            SettingsPermissionPrompt.DAILY_NOTIFICATION_REQUEST -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    setDailyWallpaperEnabled(true)
                                }
                                true
                            }
                            SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY -> openNotificationSettings()
                            SettingsPermissionPrompt.WEATHER_LOCATION_REQUEST -> {
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                true
                            }
                            SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY -> openAppSettings()
                        }
                        if (!settingsOpened) {
                            showSettingsFeedback("Android settings could not be opened on this device.")
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (isRecovery) R.string.permission_open_settings else R.string.permission_continue,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsPermissionPrompt = null }) {
                    Text(stringResource(R.string.permission_not_now))
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { AuraSnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        ),
                    ),
                )
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        TopAppBar(
            modifier = Modifier.fillMaxWidth(),
            title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )

        SettingsOverviewCard(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            selectedStyleCount = selectedStyleCount,
            schedulerEnabled = schedulerEnabled,
            schedulerInterval = schedulerInterval,
            weatherEffects = weatherEffects,
            adaptiveTint = adaptiveTint,
            autoPreview = autoPreview,
            videoFpsLimit = videoFpsLimit,
            cacheUsage = cacheUsage,
            configuredApiKeys = configuredApiKeys,
        )

        // Wallpapers
        SettingsSection(
            title = "Wallpapers",
            description = "Tune discovery quality, density, and the overall look of your feed.",
        ) {
            SettingsToggle(
                icon = Icons.Default.AutoAwesome,
                title = "Auto-change wallpaper",
                subtitle = "Periodically rotate wallpapers",
                checked = autoWpEnabled,
                onCheckedChange = { viewModel.setAutoWallpaper(it) },
            )
            if (autoWpEnabled) {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Change interval",
                    subtitle = "Every $autoWpInterval hours",
                    onClick = { showIntervalPicker = true },
                )
                // #10: Source picker
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = "Wallpaper source",
                    subtitle = wallpaperRotationSourceLabel(
                        source = autoWpSource,
                        localFolderUri = localWallpaperFolderUri,
                        localFolderPermissionActive = localFolderPermissionActive,
                    ),
                    onClick = { showSourcePicker = true },
                )
                // T-7: Rotation execution constraints. WorkManager gates the worker on
                // these — toggle on, then the worker only fires when ALL satisfied.
                // Off-by-default so existing users keep current behavior on upgrade.
                SettingsToggle(
                    icon = Icons.Default.BatteryChargingFull,
                    title = "Charging only",
                    subtitle = "Hold rotation until the device is plugged in",
                    checked = autoWpRequiresCharging,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresCharging(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Wifi,
                    title = "Wi-Fi only",
                    subtitle = "Skip cellular fetches; honors data-saver",
                    checked = autoWpRequiresWiFi,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresWiFiOnly(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Bedtime,
                    title = "Device idle only",
                    subtitle = "Defer rotation until you're not actively using the phone",
                    checked = autoWpRequiresIdle,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresIdle(it) },
                )
            }
            SettingsValueSlider(
                icon = Icons.Default.Brightness4,
                title = "Rotation dimming",
                subtitle = rotationDarkenSubtitle(
                    percent = autoWallpaperDarkenPercent,
                    rotationActive = autoWpEnabled || schedulerEnabled || rotateOnUnlock || rotateOnScreenOff,
                ),
                valueLabel = darkenPercentLabel(autoWallpaperDarkenPercent),
                value = autoWallpaperDarkenPercent.toFloat(),
                valueRange = 0f..100f,
                steps = 9,
                onValueChange = { viewModel.setAutoWallpaperDarkenPercent(it.roundToInt()) },
            )
            val rotationActive = autoWpEnabled || schedulerEnabled || rotateOnUnlock || rotateOnScreenOff
            if (rotationActive) {
                val oemGuide = remember { OemBatteryGuidance.detect(context) }
                if (oemGuide != null) {
                    SettingsItem(
                        icon = Icons.Default.BatteryAlert,
                        title = "${oemGuide.manufacturer} battery optimization",
                        subtitle = oemGuide.summary,
                        onClick = {
                            val intent = oemGuide.settingsIntent
                            if (intent != null) {
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    showSettingsFeedback("Could not open ${oemGuide.manufacturer} battery settings on this device.")
                                }
                            } else {
                                showSettingsFeedback("Could not find ${oemGuide.manufacturer} battery settings on this device.")
                            }
                        },
                    )
                }
            }
            SettingsItem(
                icon = Icons.Default.FolderOpen,
                title = "Local rotation folder",
                subtitle = localWallpaperFolderSubtitle(
                    localWallpaperFolderUri,
                    localFolderPermissionActive,
                ),
                onClick = { chooseLocalWallpaperFolder() },
            )
            if (localWallpaperFolderUri.isNotBlank()) {
                SettingsItem(
                    icon = Icons.Default.DeleteOutline,
                    title = "Clear local rotation folder",
                    subtitle = "Remove the saved folder grant from Aura",
                    onClick = { viewModel.clearLocalWallpaperFolderUri() },
                )
            }
            // NX-6: trigger-based rotation (per-unlock + screen-off pre-stage).
            // Always visible — independent of the periodic worker — so users who
            // disable timer-based rotation can still get unlock-driven changes.
            SettingsToggle(
                icon = Icons.Default.LockOpen,
                title = "Change on every unlock",
                subtitle = "Rotate when you wake the phone (runs a low-priority notification while active)",
                checked = rotateOnUnlock,
                onCheckedChange = { viewModel.setRotateOnUnlock(it) },
            )
            SettingsToggle(
                icon = Icons.Default.PowerSettingsNew,
                title = "Pre-stage on screen off",
                subtitle = "Pick the next wallpaper while the screen is off so unlock shows the new one",
                checked = rotateOnScreenOff,
                onCheckedChange = { viewModel.setRotateOnScreenOff(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Shuffle,
                title = "Avoid recent repeats",
                subtitle = "Track applied wallpapers and skip recently shown ones until the full pool has cycled",
                checked = avoidRecentRepeats,
                onCheckedChange = { viewModel.setAvoidRecentRepeats(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.settings_external_automation_title),
                subtitle = externalAutomationSubtitle(externalAutomationDiagnostics),
                checked = externalAutomationDiagnostics.enabled,
                onCheckedChange = { viewModel.setExternalAutomationEnabled(it) },
            )
            // #9: Grid columns
            SettingsItem(
                icon = Icons.Default.GridView,
                title = "Grid columns",
                subtitle = "$gridColumns columns",
                onClick = { showColumnsPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.VideoFile,
                title = "Video or GIF wallpaper",
                subtitle = "Import a local clip or animated GIF as live wallpaper",
                onClick = { videoPickerLauncher.launch(videoWallpaperMimeTypes()) },
            )
            // v6.1.0 — parallax from user photo
            SettingsItem(
                icon = Icons.Default.PhotoLibrary,
                title = "Parallax from my photo",
                subtitle = "Turn one of your photos into a depth-tilt live wallpaper",
                onClick = {
                    parallaxGalleryLauncher.launch(
                        androidx.activity.result.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
            )
            SettingsItem(
                icon = Icons.Default.PhotoSizeSelectLarge,
                title = "Preferred resolution",
                subtitle = if (preferredRes.isEmpty()) "Any resolution" else preferredRes,
                onClick = { showResPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Style preferences",
                subtitle = userStylesSummary(userStyles),
                onClick = { showStylePicker = true },
            )
            SettingsItem(
                icon = Icons.Default.Forum,
                title = "Reddit source discontinued",
                subtitle = "Public feeds are retired; saved Reddit wallpapers keep attribution and unavailable-source states",
                onClick = { showSettingsFeedback("Reddit public feeds are no longer available in Aura.") },
            )
            SettingsToggle(
                icon = Icons.Default.ImageSearch,
                title = "Enable Bing Daily source",
                subtitle = if (bingProviderEnabled) {
                    "Adds Bing Image of the Day to Discover and rotations"
                } else {
                    "Skips Bing daily-image calls and hides it from rotation pickers"
                },
                checked = bingProviderEnabled,
                onCheckedChange = { viewModel.setBingProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Category,
                title = "Browse categories",
                subtitle = "Nature, Space, Anime, Dark, Neon + 12 more",
                onClick = onCategoriesClick,
            )
            SettingsItem(
                icon = Icons.Default.Folder,
                title = "Collections",
                subtitle = "Organize wallpapers into folders",
                onClick = onCollectionsClick,
            )
            if (showCommunityIdentity) {
                CommunityIdentityDialog(
                    summary = communityIdentitySummary,
                    cleanupBusy = communityIdentityCleanup.clearing,
                    onRefresh = viewModel::refreshCommunityIdentitySummary,
                    onClearLocal = viewModel::clearLocalCommunityIdentity,
                    onCopyCode = { code ->
                        copyCommunityDeletionCode(context, code) { message -> showSettingsFeedback(message) }
                    },
                    onShareRequest = { summary ->
                        shareCommunityDeletionRequest(context, summary) { message -> showSettingsFeedback(message) }
                    },
                    onDismiss = { showCommunityIdentity = false },
                )
            }
            if (showBlockedCreators) {
                BlockedCreatorsDialog(
                    blockedCreators = blockedCommunityCreators,
                    actionState = communityBlockAction,
                    onUnblock = viewModel::unblockCommunityCreator,
                    onDismiss = { showBlockedCreators = false },
                )
            }
            if (showCommunityGuidelines) {
                CommunityGuidelinesDialog(
                    onAccept = {
                        viewModel.acceptCommunityGuidelines()
                        showCommunityGuidelines = false
                    },
                    onReset = if (communityGuidelinesAccepted) {
                        {
                            viewModel.resetCommunityGuidelines()
                            showCommunityGuidelines = false
                        }
                    } else null,
                    onDismiss = { showCommunityGuidelines = false },
                )
            }
            // #2: Wallpaper history — opens browsable grid
            if (wallpaperHistory.isNotEmpty()) {
                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Wallpaper history",
                    subtitle = "${wallpaperHistory.size} recently applied",
                    onClick = onHistoryClick,
                )
            }
        }

        // Wallpaper Scheduler
        SettingsSection(
            title = "Wallpaper Scheduler",
            description = "Automate rotation across sources, collections, and screen targets.",
        ) {
            var showSchedulerInterval by remember { mutableStateOf(false) }
            var showSchedulerSource by remember { mutableStateOf(false) }

            SettingsToggle(
                icon = Icons.Default.Schedule,
                title = "Auto-rotate wallpapers",
                subtitle = if (schedulerEnabled) "Every ${formatInterval(schedulerInterval)}" else "Disabled",
                checked = schedulerEnabled,
                onCheckedChange = { viewModel.setSchedulerEnabled(it) },
            )
            if (schedulerEnabled) {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Rotation interval",
                    subtitle = formatInterval(schedulerInterval),
                    onClick = { showSchedulerInterval = true },
                )
                val collectionsList by viewModel.collections.collectAsStateWithLifecycle()
                val activeCollectionId by viewModel.schedulerCollectionId.collectAsStateWithLifecycle()
                val activeCollectionName = remember(collectionsList, activeCollectionId) {
                    collectionsList.firstOrNull { it.collectionId == activeCollectionId }?.name
                }
                val sourceSubtitle = when {
                    schedulerSource == "collection" && activeCollectionName != null ->
                        "Collection: $activeCollectionName"
                    schedulerSource == "collection" ->
                        "Collection (none selected)"
                    else ->
                        wallpaperRotationSourceLabel(
                            source = schedulerSource,
                            localFolderUri = localWallpaperFolderUri,
                            localFolderPermissionActive = localFolderPermissionActive,
                        )
                }
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = "Source",
                    subtitle = sourceSubtitle,
                    onClick = { showSchedulerSource = true },
                )
                SettingsToggle(
                    icon = Icons.Default.Home,
                    title = "Home screen",
                    subtitle = "Change home screen wallpaper",
                    checked = schedulerHome,
                    onCheckedChange = { viewModel.setSchedulerHome(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Lock,
                    title = "Lock screen",
                    subtitle = "Change lock screen wallpaper",
                    checked = schedulerLock,
                    onCheckedChange = { viewModel.setSchedulerLock(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Shuffle,
                    title = "Shuffle",
                    subtitle = if (schedulerShuffle) "Random order" else "Sequential order",
                    checked = schedulerShuffle,
                    onCheckedChange = { viewModel.setSchedulerShuffle(it) },
                )
            }

            if (showSchedulerInterval) {
                val intervals = listOf(
                    15L to "15 minutes", 30L to "30 minutes", 60L to "1 hour",
                    120L to "2 hours", 360L to "6 hours", 720L to "12 hours",
                    1440L to "24 hours", 2880L to "2 days",
                )
                AlertDialog(
                    onDismissRequest = { showSchedulerInterval = false },
                    title = { Text("Rotation interval") },
                    text = {
                        Column {
                            intervals.forEach { (min, label) ->
                                SettingsRadioOptionRow(
                                    label = label,
                                    selected = schedulerInterval == min,
                                    onClick = {
                                        viewModel.setSchedulerInterval(min)
                                        showSchedulerInterval = false
                                    },
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSchedulerInterval = false }) { Text("Cancel") } },
                )
            }

            var showCollectionPicker by remember { mutableStateOf(false) }
            if (showSchedulerSource) {
                val sources = listOf(
                    "discover" to "Discover (mixed)", "favorites" to "My Favorites",
                    WALLPAPER_SOURCE_LOCAL_FOLDER to "Local folder",
                    "wallhaven" to "Wallhaven", "pixabay" to "Pixabay",
                    "bing" to "Bing Daily", "collection" to "A collection…",
                ).filter { (key, _) ->
                    when (key) {
                        "wallhaven" -> wallhavenProviderEnabled || schedulerSource == "wallhaven"
                        "pixabay" -> pixabayProviderEnabled || schedulerSource == "pixabay"
                        "bing" -> bingProviderEnabled || schedulerSource == "bing"
                        else -> true
                    }
                }
                AlertDialog(
                    onDismissRequest = { showSchedulerSource = false },
                    title = { Text("Wallpaper source") },
                    text = {
                        Column {
                            sources.forEach { (key, label) ->
                                SettingsRadioOptionRow(
                                    label = label,
                                    selected = schedulerSource == key,
                                    onClick = {
                                        if (key == "collection") {
                                            showSchedulerSource = false
                                            showCollectionPicker = true
                                        } else if (
                                            key == WALLPAPER_SOURCE_LOCAL_FOLDER &&
                                            !isLocalWallpaperFolderReady(
                                                localWallpaperFolderUri,
                                                localFolderPermissionActive,
                                            )
                                        ) {
                                            showSchedulerSource = false
                                            chooseLocalWallpaperFolder("scheduler")
                                        } else {
                                            viewModel.setSchedulerSource(key)
                                            showSchedulerSource = false
                                        }
                                    },
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showSchedulerSource = false }) { Text("Cancel") } },
                )
            }

            if (showCollectionPicker) {
                val collections by viewModel.collections.collectAsStateWithLifecycle()
                val activeId by viewModel.schedulerCollectionId.collectAsStateWithLifecycle()
                AlertDialog(
                    onDismissRequest = { showCollectionPicker = false },
                    title = { Text("Rotate from which collection?") },
                    text = {
                        if (collections.isEmpty()) {
                            // Empty-state guidance: we can't rotate through something that
                            // doesn't exist yet.
                            Column {
                                Text(
                                    "You haven't created any collections yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Save wallpapers to a collection from the wallpaper detail screen, then come back here.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                                collections.forEach { c ->
                                    SettingsRadioOptionRow(
                                        label = c.name,
                                        selected = activeId == c.collectionId,
                                        onClick = {
                                            viewModel.setSchedulerCollection(c.collectionId)
                                            showCollectionPicker = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showCollectionPicker = false }) { Text("Cancel") } },
                )
            }
        }

        // Library backup
        SettingsSection(
            title = "Library Backup",
            description = "Keep favorites recoverable without creating an account or uploading your library.",
        ) {
            SettingsToggle(
                icon = Icons.Default.FolderOpen,
                title = "Scheduled favorites backup",
                subtitle = autoBackupStatusSubtitle(
                    enabled = autoBackupEnabled,
                    folderUri = autoBackupFolderUri,
                    folderPermissionActive = autoBackupFolderPermissionActive,
                    intervalHours = autoBackupIntervalHours,
                    keepCount = autoBackupKeepCount,
                ),
                checked = autoBackupEnabled,
                onCheckedChange = { enabled ->
                    if (!enabled) {
                        viewModel.setAutoBackupEnabled(false)
                    } else if (!autoBackupFolderPermissionActive) {
                        chooseAutoBackupFolder(enableAfterSelection = true)
                        showSettingsFeedback("Choose a writable folder before enabling scheduled backups.")
                    } else {
                        viewModel.setAutoBackupEnabled(true)
                    }
                },
            )
            SettingsItem(
                icon = Icons.Default.FolderOpen,
                title = "Backup folder",
                subtitle = autoBackupFolderSubtitle(
                    folderUri = autoBackupFolderUri,
                    folderPermissionActive = autoBackupFolderPermissionActive,
                ),
                onClick = { chooseAutoBackupFolder() },
            )
            if (autoBackupFolderUri.isNotBlank()) {
                SettingsItem(
                    icon = Icons.Default.DeleteOutline,
                    title = "Clear backup folder",
                    subtitle = "Disable scheduled backup and remove Aura's saved folder grant",
                    onClick = { viewModel.clearAutoBackupFolderUri() },
                )
            }
            SettingsItem(
                icon = Icons.Default.Timer,
                title = "Backup interval",
                subtitle = formatAutoBackupInterval(autoBackupIntervalHours),
                onClick = { showAutoBackupIntervalPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.History,
                title = "Backups to keep",
                subtitle = autoBackupRetentionLabel(autoBackupKeepCount),
                onClick = { showAutoBackupKeepPicker = true },
            )
        }

        // Smart Features
        SettingsSection(
            title = "Smart Features",
            description = "Ambient enhancements that make Aura feel more adaptive and alive.",
        ) {
            SettingsToggle(
                icon = Icons.Default.Today,
                title = "Daily wallpaper",
                subtitle = "Get a daily wallpaper recommendation notification",
                checked = dailyWp,
                onCheckedChange = {
                    if (!it) {
                        setDailyWallpaperEnabled(false)
                    } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        setDailyWallpaperEnabled(false)
                        settingsPermissionPrompt = SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        setDailyWallpaperEnabled(false)
                        settingsPermissionPrompt = SettingsPermissionPrompt.DAILY_NOTIFICATION_REQUEST
                    } else {
                        setDailyWallpaperEnabled(true)
                    }
                },
            )
            SettingsToggle(
                icon = Icons.Default.WbSunny,
                title = "Time-of-day tint",
                subtitle = "Warm tones at sunrise/sunset, cool at night",
                checked = adaptiveTint,
                onCheckedChange = { viewModel.setAdaptiveTint(it) },
            )
            if (adaptiveTint) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("Intensity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = adaptiveTintIntensity,
                        onValueChange = { viewModel.setAdaptiveTintIntensity(it) },
                        valueRange = 0.1f..1f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Subtle ← → Intense",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingsToggle(
                icon = Icons.Default.Cloud,
                title = stringResource(R.string.settings_weather_effects_title),
                subtitle = stringResource(R.string.settings_weather_effects_subtitle),
                checked = weatherEffects,
                onCheckedChange = {
                    if (!it) {
                        disableWeatherEffects()
                    } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                        enableWeatherEffects()
                    } else {
                        disableWeatherEffects()
                        settingsPermissionPrompt = SettingsPermissionPrompt.WEATHER_LOCATION_REQUEST
                    }
                },
            )
            if (weatherEffects) {
                Surface(
                    onClick = { openExternalUrl(context, OPEN_METEO_LICENCE_URL) },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Weather data by Open-Meteo.com",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                "Licensed under CC BY 4.0",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            SettingsToggle(
                icon = Icons.Default.Brightness4,
                title = "Auto-switch wallpaper for dark mode",
                subtitle = "Apply different wallpapers when system theme changes",
                checked = darkModeSwitch,
                onCheckedChange = { viewModel.setDarkModeSwitch(it) },
            )
            if (darkModeSwitch) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text("Wallpaper slots", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Light mode slot
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { showLightModeWallpaperPicker = true }
                                .padding(12.dp),
                        ) {
                            Text("Light mode", style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (lightModeWallpaperId.isEmpty()) "Not set" else "Set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Dark mode slot
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .clickable { showDarkModeWallpaperPicker = true }
                                .padding(12.dp),
                        ) {
                            Text("Dark mode", style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (darkModeWallpaperId.isEmpty()) "Not set" else "Set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "Tap either slot to choose which wallpaper to apply when the system switches to that theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // VFX particle overlays
            var showVfxPicker by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.AutoFixHigh,
                title = "Decorative effects",
                subtitle = "Fireflies, sakura, embers, bubbles, leaves, sparkles",
                onClick = { showVfxPicker = true },
            )
            if (showVfxPicker) {
                val effects = listOf(
                    "NONE" to "None", "FIREFLIES" to "Fireflies",
                    "SAKURA" to "Sakura petals", "EMBERS" to "Fire embers",
                    "BUBBLES" to "Bubbles", "LEAVES" to "Autumn leaves",
                    "SPARKLES" to "Sparkles",
                )
                var currentVfx by remember {
                    mutableStateOf(
                        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                            .getString("vfx_effect", "NONE") ?: "NONE"
                    )
                }
                AlertDialog(
                    onDismissRequest = { showVfxPicker = false },
                    title = { Text("Decorative overlay") },
                    text = {
                        Column {
                            effects.forEach { (key, label) ->
                                SettingsRadioOptionRow(
                                    label = label,
                                    selected = currentVfx == key,
                                    onClick = {
                                        currentVfx = key
                                        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                                            .edit().putString("vfx_effect", key).apply()
                                        showVfxPicker = false
                                    },
                                )
                            }
                        }
                    },
                    // "Close" not "Cancel" — each radio click already commits the selection
                    // synchronously, so there is nothing to cancel by the time this button
                    // is reachable.
                    confirmButton = { TextButton(onClick = { showVfxPicker = false }) { Text("Close") } },
                )
            }
            var showTouchEffectsPicker by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.TouchApp,
                title = "Touch effects",
                subtitle = touchEffectSummary(touchEffectStrength),
                onClick = { showTouchEffectsPicker = true },
            )
            if (showTouchEffectsPicker) {
                val modes = listOf(
                    "OFF" to "Off",
                    "SUBTLE" to "Subtle ripples",
                    "STRONG" to "Ripples + sparkles",
                )
                AlertDialog(
                    onDismissRequest = { showTouchEffectsPicker = false },
                    title = { Text("Touch effects") },
                    text = {
                        Column {
                            modes.forEach { (key, label) ->
                                SettingsRadioOptionRow(
                                    label = label,
                                    selected = touchEffectStrength == key,
                                    onClick = {
                                        touchEffectStrength = key
                                        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                                            .edit().putString("touch_effect_strength", key).apply()
                                        showTouchEffectsPicker = false
                                    },
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showTouchEffectsPicker = false }) { Text("Close") } },
                )
            }
            SettingsToggle(
                icon = Icons.Default.Accessibility,
                title = "Reduce animations",
                subtitle = "Disable particle, weather, and touch effects on live wallpapers",
                checked = reduceAnimations,
                onCheckedChange = { viewModel.setReduceAnimations(it) },
            )
            // Dark/light mode wallpaper pickers.
            // Earlier revision bailed silently when wallpaperHistory was empty, leaving the
            // user clicking the slot card with no feedback. Now the dialog opens regardless
            // and shows an explanatory empty state so the affordance isn't a dead click.
            if (showDarkModeWallpaperPicker) {
                WallpaperSlotPickerDialog(
                    title = "Choose dark mode wallpaper",
                    history = wallpaperHistory,
                    onPick = { entry ->
                        val wallpaperId = "${entry.source}|${entry.wallpaperId}|${entry.fullUrl}"
                        viewModel.setDarkModeWallpaperId(wallpaperId)
                        showDarkModeWallpaperPicker = false
                    },
                    onDismiss = { showDarkModeWallpaperPicker = false },
                )
            }
            if (showLightModeWallpaperPicker) {
                WallpaperSlotPickerDialog(
                    title = "Choose light mode wallpaper",
                    history = wallpaperHistory,
                    onPick = { entry ->
                        val wallpaperId = "${entry.source}|${entry.wallpaperId}|${entry.fullUrl}"
                        viewModel.setLightModeWallpaperId(wallpaperId)
                        showLightModeWallpaperPicker = false
                    },
                    onDismiss = { showLightModeWallpaperPicker = false },
                )
            }
        }

        // Sound settings
        SettingsSection(
            title = "Sounds",
            description = "Control previews, search quality, and how results are filtered before playback.",
        ) {
            SettingsToggle(
                icon = Icons.Default.PlayCircle,
                title = "Auto-preview sounds",
                subtitle = if (autoPreview) "Starts playback when you open sound details" else "Open sound details without autoplay",
                checked = autoPreview,
                onCheckedChange = { viewModel.setAutoPreview(it) },
            )
            // Preview volume slider
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
                ),
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    ) {
                        @Suppress("DEPRECATION")
                        Icon(
                            Icons.Default.VolumeUp,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preview volume", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Choose how assertive previews should feel while browsing sounds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(
                            value = previewVolume,
                            onValueChange = { viewModel.setPreviewVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.height(24.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                    Text("${(previewVolume * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsItem(
                icon = Icons.Default.SmartDisplay,
                title = "YouTube search queries",
                subtitle = "Refine ringtone, notification, and alarm searches for each tab",
                onClick = { showYtSoundEditor = true },
            )
            SettingsToggle(
                icon = Icons.Default.SmartDisplay,
                title = "Enable YouTube features",
                subtitle = if (youtubeProviderEnabled) {
                    "Shows YouTube sound search, imports, top hits, and video wallpaper results"
                } else {
                    "Hides YouTube browsing and blocks stream resolution"
                },
                checked = youtubeProviderEnabled,
                onCheckedChange = { viewModel.setYoutubeProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Update,
                title = stringResource(R.string.settings_ytdlp_update_title),
                subtitle = ytDlpUpdateSubtitle(
                    state = ytDlpUpdate,
                    youtubeProviderEnabled = youtubeProviderEnabled,
                ),
                onClick = {
                    if (youtubeProviderEnabled && !ytDlpUpdate.isUpdating) {
                        viewModel.updateYtDlp()
                    }
                },
            )
            SettingsItem(
                icon = Icons.Default.Block,
                title = "Blocked words",
                subtitle = "${ytBlockedWords.split(",").count { it.isNotBlank() }} words filtered from YouTube results",
                onClick = { showYtBlockedEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = "Sound sources",
                subtitle = "YouTube powers the sound feed; community uploads and legacy attributions remain documented",
                onClick = onLicensesClick,
            )
            SettingsToggle(
                icon = Icons.Default.Shuffle,
                title = "Shuffle ringtone",
                subtitle = if (ringtoneShuffleEnabled) {
                    "Changes your ringtone from downloaded sounds every ${formatInterval(ringtoneShuffleIntervalHours * 60)}"
                } else {
                    "Periodically set a random downloaded sound as your ringtone"
                },
                checked = ringtoneShuffleEnabled,
                onCheckedChange = { viewModel.setRingtoneShuffleEnabled(it) },
            )
            if (ringtoneShuffleEnabled) {
                var showShuffleIntervalPicker by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "Shuffle interval",
                    subtitle = formatInterval(ringtoneShuffleIntervalHours * 60),
                    onClick = { showShuffleIntervalPicker = true },
                )
                if (showShuffleIntervalPicker) {
                    val intervals = listOf(1L to "Every hour", 6L to "Every 6 hours", 12L to "Every 12 hours", 24L to "Every day", 72L to "Every 3 days")
                    AlertDialog(
                        onDismissRequest = { showShuffleIntervalPicker = false },
                        title = { Text("Shuffle interval") },
                        text = {
                            Column {
                                intervals.forEach { (hours, label) ->
                                    SettingsRadioOptionRow(
                                        label = label,
                                        selected = ringtoneShuffleIntervalHours == hours,
                                        onClick = {
                                            viewModel.setRingtoneShuffleIntervalHours(hours)
                                            showShuffleIntervalPicker = false
                                        },
                                    )
                                }
                            }
                        },
                        confirmButton = { TextButton(onClick = { showShuffleIntervalPicker = false }) { Text("Cancel") } },
                    )
                }
            }
        }

        // Video Wallpapers
        SettingsSection(
            title = "Video Wallpapers",
            description = "Monitor live-wallpaper cost and keep motion responsive without wasting battery.",
        ) {
            var showFpsPicker by remember { mutableStateOf(false) }
            VideoBatteryDashboardCard(
                state = videoBatteryDashboard,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsToggle(
                icon = Icons.Default.BatteryChargingFull,
                title = "Auto battery saver",
                subtitle = if (videoAutoBatterySaver)
                    "Caps video and GIF wallpapers at 15 FPS below 15% battery"
                else
                    "Keep the selected FPS limit even when battery is low",
                checked = videoAutoBatterySaver,
                onCheckedChange = { viewModel.setVideoAutoBatterySaver(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Speed,
                title = "FPS overlay",
                subtitle = if (videoFpsOverlayEnabled)
                    "Show a small debug FPS readout on Canvas-rendered motion wallpapers"
                else
                    "Hidden unless you need to inspect frame pacing",
                checked = videoFpsOverlayEnabled,
                onCheckedChange = { viewModel.setVideoFpsOverlayEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Speed,
                title = "FPS limit",
                subtitle = videoBatteryImpactSummary(
                    requestedFps = videoBatteryDashboard.requestedFps,
                    effectiveFps = videoBatteryDashboard.effectiveFps,
                    fpsOverlayEnabled = videoBatteryDashboard.fpsOverlayEnabled,
                    lowBatterySaverActive = videoBatteryDashboard.lowBatterySaverActive,
                ),
                onClick = { showFpsPicker = true },
            )
            if (showFpsPicker) {
                AlertDialog(
                    onDismissRequest = { showFpsPicker = false },
                    title = { Text("Video FPS limit") },
                    text = {
                        Column {
                            listOf(15 to "15 FPS (battery saver)", 30 to "30 FPS (balanced)", 60 to "60 FPS (smooth)").forEach { (fps, label) ->
                                SettingsRadioOptionRow(
                                    label = label,
                                    selected = videoFpsLimit == fps,
                                    onClick = {
                                        viewModel.setVideoFpsLimit(fps)
                                        showFpsPicker = false
                                    },
                                )
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showFpsPicker = false }) { Text("Cancel") } },
                )
            }
        }

        // API Keys
        SettingsSection(
            title = "Advanced external services",
            description = "Provider keys and opt-in cloud features. Generated wallpapers and community actions stay off until you enable them here.",
        ) {
            SettingsToggle(
                icon = Icons.Default.Groups,
                title = "Enable external Community source",
                subtitle = if (communityProviderEnabled) {
                    "Shows Firebase-backed feeds, uploads, votes, and creator surfaces"
                } else {
                    "Off by default. Hides community tabs and blocks Firebase-backed actions"
                },
                checked = communityProviderEnabled,
                onCheckedChange = { viewModel.setCommunityProviderEnabled(it) },
            )
            if (communityProviderEnabled) {
                SettingsItem(
                    icon = Icons.Default.VerifiedUser,
                    title = "Community guidelines",
                    subtitle = if (communityGuidelinesAccepted) {
                        "Accepted v$communityGuidelinesAcceptedVersion"
                    } else {
                        "Required before uploads, votes, reports, blocks, follows, and profiles"
                    },
                    onClick = { showCommunityGuidelines = true },
                )
            }
            if (communityProviderEnabled && communityGuidelinesAccepted) {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Community identity",
                    subtitle = communityIdentitySubtitle(communityIdentitySummary),
                    onClick = {
                        viewModel.refreshCommunityIdentitySummary()
                        showCommunityIdentity = true
                    },
                )
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = "Creator profile",
                    subtitle = "Uploads, votes, follows, and leaderboard",
                    onClick = onCreatorProfileClick,
                )
                SettingsItem(
                    icon = Icons.Default.Block,
                    title = "Blocked creators",
                    subtitle = if (blockedCommunityCreators.isEmpty()) {
                        "No community creators hidden"
                    } else {
                        "${blockedCommunityCreators.size} community creators hidden"
                    },
                    onClick = { showBlockedCreators = true },
                )
                if (viewModel.isAdmin) {
                    SettingsItem(
                        icon = Icons.Default.Report,
                        title = "Community reports",
                        subtitle = "Review open rights, source, and safety reports",
                        onClick = onCommunityReportsClick,
                    )
                }
            }
            var showWallhavenKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Wallhaven API Key",
                subtitle = "Optional: higher limits + NSFW (wallhaven.cc/settings)",
                onClick = { showWallhavenKey = true },
            )
            SettingsToggle(
                icon = Icons.Default.ImageSearch,
                title = "Enable Wallhaven source",
                subtitle = if (wallhavenProviderEnabled) {
                    "Shows Wallhaven featured, color, similar, random, and Discover results"
                } else {
                    "Hides Wallhaven browsing and skips Wallhaven API calls"
                },
                checked = wallhavenProviderEnabled,
                onCheckedChange = { viewModel.setWallhavenProviderEnabled(it) },
            )
            if (showWallhavenKey) {
                ProviderApiKeyDialog(
                    title = "Wallhaven API Key",
                    description = "Get your key at wallhaven.cc/settings",
                    value = wallhavenApiKey,
                    placeholder = "Paste API key",
                    onSave = viewModel::setWallhavenKey,
                    onDismiss = { showWallhavenKey = false },
                )
            }
            // Wallhaven SafeSearch toggles. Without an API key both remain UI-visible
            // but ineffective — Wallhaven rejects non-SFW requests when unauthenticated,
            // and computeWallhavenPurity coerces back to "100" so the user still sees
            // results instead of an empty grid.
            SettingsToggle(
                icon = Icons.Default.Visibility,
                title = "Show sketchy wallpapers",
                subtitle = if (wallhavenApiKey.isBlank())
                    "Requires a Wallhaven API key to take effect"
                else
                    "Suggestive imagery short of explicit nudity",
                checked = showSketchyContent,
                onCheckedChange = { viewModel.setShowSketchy(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Warning,
                title = "Show NSFW wallpapers",
                subtitle = if (wallhavenApiKey.isBlank())
                    "Requires a Wallhaven API key to take effect"
                else
                    "Explicit content from authenticated Wallhaven account",
                checked = showNsfwContent,
                onCheckedChange = { viewModel.setShowNsfw(it) },
            )
            var showPexelsKey by remember { mutableStateOf(false) }
            SettingsToggle(
                icon = Icons.Default.PhotoLibrary,
                title = "Enable Pexels source",
                subtitle = if (pexelsProviderEnabled) {
                    "Shows Pexels photos and video wallpapers when a key is available"
                } else {
                    "Hides Pexels browsing and skips Pexels API calls"
                },
                checked = pexelsProviderEnabled,
                onCheckedChange = { viewModel.setPexelsProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Pexels API Key",
                subtitle = "Free key for video wallpapers (pexels.com/api)",
                onClick = { showPexelsKey = true },
            )
            if (showPexelsKey) {
                ProviderApiKeyDialog(
                    title = "Pexels API Key",
                    description = "Get a free key at pexels.com/api/new",
                    value = pexelsApiKey,
                    placeholder = "Paste API key here",
                    onSave = viewModel::setPexelsKey,
                    onDismiss = { showPexelsKey = false },
                )
            }
            var showPixabayKey by remember { mutableStateOf(false) }
            SettingsToggle(
                icon = Icons.Default.Collections,
                title = "Enable Pixabay source",
                subtitle = if (pixabayProviderEnabled) {
                    "Shows Pixabay photos, widget shuffles, rotations, and video loops"
                } else {
                    "Hides Pixabay browsing and skips Pixabay API calls"
                },
                checked = pixabayProviderEnabled,
                onCheckedChange = { viewModel.setPixabayProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Key,
                title = "Pixabay API Key",
                subtitle = "Free key for photos + videos (pixabay.com/api/docs)",
                onClick = { showPixabayKey = true },
            )
            if (showPixabayKey) {
                ProviderApiKeyDialog(
                    title = "Pixabay API Key",
                    description = "Get a free key at pixabay.com/api/docs",
                    value = pixabayApiKey,
                    placeholder = "Paste API key here",
                    onSave = viewModel::setPixabayKey,
                    onDismiss = { showPixabayKey = false },
                )
            }
            var showFreesoundKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = "Freesound API Key",
                subtitle = "Optional token for Freesound sound search",
                onClick = { showFreesoundKey = true },
            )
            if (showFreesoundKey) {
                ProviderApiKeyDialog(
                    title = "Freesound API Key",
                    description = "Paste a Freesound API token for sound search.",
                    value = freesoundApiKey,
                    placeholder = "Paste API token here",
                    onSave = viewModel::setFreesoundKey,
                    onDismiss = { showFreesoundKey = false },
                )
            }
            var showStabilityKey by remember { mutableStateOf(false) }
            var showGeneratedDisclosure by remember { mutableStateOf(false) }
            LaunchedEffect(generatedContentProviderEnabled) {
                if (!generatedContentProviderEnabled) showStabilityKey = false
            }
            SettingsToggle(
                icon = Icons.Default.AutoAwesome,
                title = "Enable external generated wallpapers",
                subtitle = if (generatedContentProviderEnabled) {
                    "Shows generation entry points and allows Stability requests"
                } else {
                    "Off by default. Hides generation entry points and blocks Stability requests"
                },
                checked = generatedContentProviderEnabled,
                onCheckedChange = { viewModel.setGeneratedContentProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Generated wallpaper disclosure",
                subtitle = if (generatedContentDisclosureAccepted) {
                    "Accepted; review prompt sharing, key use, and local storage"
                } else {
                    "Review prompt sharing, provider credits, and local storage"
                },
                onClick = { showGeneratedDisclosure = true },
            )
            if (showGeneratedDisclosure) {
                GeneratedWallpaperDisclosureDialog(
                    accepted = generatedContentDisclosureAccepted,
                    onAccept = viewModel::acceptGeneratedContentDisclosure,
                    onReset = viewModel::resetGeneratedContentDisclosure,
                    onDismiss = { showGeneratedDisclosure = false },
                )
            }
            if (generatedContentProviderEnabled) {
                SettingsItem(
                    icon = Icons.Default.AutoAwesome,
                    title = "Open generation studio",
                    subtitle = "Create wallpapers with Stability after disclosure and key setup",
                    onClick = onGeneratedWallpapersClick,
                )
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = "Stability AI API Key",
                    subtitle = "For advanced image generation (stability.ai)",
                    onClick = { showStabilityKey = true },
                )
            }
            if (generatedContentProviderEnabled && showStabilityKey) {
                ProviderApiKeyDialog(
                    title = "Stability AI API Key",
                    description = "Get a free key at stability.ai/account/keys",
                    value = stabilityAiKey,
                    placeholder = "Paste API key here",
                    onSave = viewModel::setStabilityKey,
                    onDismiss = { showStabilityKey = false },
                )
            }
        }

        // Storage
        SettingsSection(
            title = "Storage",
            description = "Keep downloads accessible while trimming temporary media and cached feeds when needed.",
        ) {
            SettingsItem(
                icon = Icons.Default.Download,
                title = "Downloads",
                subtitle = "Review wallpapers, sounds, and videos saved by Aura",
                onClick = onDownloadsClick,
            )
            SettingsItem(
                icon = Icons.Default.Folder,
                title = "Free up storage",
                subtitle = cacheUsageSubtitle(cacheUsage),
                onClick = { showClearCacheConfirm = true },
            )
        }

        // Diagnostics — opt-in surface for "why is X tab loading slowly?".
        // Reads in-memory metrics collected by SourceMetrics; resets on process death.
        var showDiagnostics by remember { mutableStateOf(false) }
        SettingsSection(
            title = "Diagnostics",
            description = "Local-only troubleshooting details. Nothing is uploaded automatically.",
        ) {
            SettingsItem(
                icon = Icons.Default.BugReport,
                title = "Crash diagnostics bundle",
                subtitle = crashDiagnosticsSubtitle(crashDiagnostics),
                onClick = {
                    viewModel.refreshCrashDiagnostics()
                    showCrashDiagnostics = true
                },
            )
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = "Background work",
                subtitle = backgroundWorkDiagnosticsSubtitle(backgroundWorkDiagnostics),
                onClick = {
                    viewModel.refreshBackgroundWorkDiagnostics()
                    showBackgroundWorkDiagnostics = true
                },
            )
            SettingsItem(
                icon = Icons.Default.SettingsInputComponent,
                title = stringResource(R.string.settings_external_automation_title),
                subtitle = externalAutomationSubtitle(externalAutomationDiagnostics),
                onClick = {
                    viewModel.refreshExternalAutomationDiagnostics()
                    showExternalAutomationDiagnostics = true
                },
            )
            SettingsItem(
                icon = Icons.Default.MonitorHeart,
                title = "Source diagnostics",
                subtitle = if (diagnostics.isEmpty()) {
                    "Live provider health appears here after browsing"
                } else {
                    "${diagnostics.size} active sources tracked this session"
                },
                onClick = { showDiagnostics = true },
            )
        }
        if (showExternalAutomationDiagnostics) {
            val snapshot = externalAutomationDiagnostics
            AlertDialog(
                onDismissRequest = { showExternalAutomationDiagnostics = false },
                title = { Text(stringResource(R.string.settings_external_automation_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(
                                R.string.settings_external_automation_dialog_body,
                                externalAutomationRateLimitLabel(snapshot.minIntervalMs),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ExternalAutomationDiagnosticsSummary(snapshot)
                        Text(
                            stringResource(R.string.settings_external_automation_public_contract),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExternalAutomationDiagnostics = false }) {
                        Text(stringResource(R.string.common_close))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.refreshExternalAutomationDiagnostics() }) {
                        Text(stringResource(R.string.common_refresh))
                    }
                },
            )
        }
        if (showBackgroundWorkDiagnostics) {
            val snapshot = backgroundWorkDiagnostics
            AlertDialog(
                onDismissRequest = { showBackgroundWorkDiagnostics = false },
                title = { Text("Background work") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Local WorkManager status and Data Saver state for scheduled wallpaper, weather, and bundled-content jobs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BackgroundWorkDiagnosticsSummary(snapshot)
                        if (snapshot.rows.isEmpty()) {
                            Text(
                                "No background-work rows are available.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            snapshot.rows.forEach { row ->
                                BackgroundWorkDiagnosticRow(row)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBackgroundWorkDiagnostics = false }) { Text("Close") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.refreshBackgroundWorkDiagnostics() }) { Text("Refresh") }
                },
            )
        }
        if (showDiagnostics) {
            val snapshots = diagnostics
            AlertDialog(
                onDismissRequest = { showDiagnostics = false },
                title = { Text("Source diagnostics") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            "Live in-session health for wallpaper, video, and sound providers. Cached results can still keep browsing usable when a source fails.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (snapshots.isEmpty()) {
                            SourceDiagnosticsEmptyState()
                        } else {
                            SourceDiagnosticsSummary(snapshots)
                            snapshots.forEach { stat ->
                                SourceDiagnosticRow(stat)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("Close") } },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.resetDiagnostics()
                    }) { Text("Reset") }
                },
            )
        }
        if (showCrashDiagnostics) {
            AlertDialog(
                onDismissRequest = { if (!crashDiagnosticsBusy) showCrashDiagnostics = false },
                title = { Text("Crash diagnostics") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            crashDiagnosticsSubtitle(crashDiagnostics),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "The bundle includes app and Android versions, ABI, active source/provider context, reproduction fields, and a sanitized tail of the local crash log.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Aura does not send this data unless you copy it or choose a share target.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (crashDiagnosticsBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !crashDiagnosticsBusy,
                        onClick = {
                            diagnosticsScope.launch {
                                crashDiagnosticsBusy = true
                                try {
                                    val bundle = viewModel.buildCrashDiagnosticsBundle()
                                    copyCrashDiagnosticsBundle(
                                        context = context,
                                        bundle = bundle,
                                        onFeedback = { message -> showSettingsFeedback(message) },
                                    )
                                    viewModel.refreshCrashDiagnostics()
                                } catch (_: Exception) {
                                    showSettingsFeedback("Could not build diagnostics")
                                } finally {
                                    crashDiagnosticsBusy = false
                                }
                            }
                        },
                    ) { Text("Copy") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !crashDiagnosticsBusy,
                            onClick = { showCrashDiagnostics = false },
                        ) { Text("Close") }
                        TextButton(
                            enabled = !crashDiagnosticsBusy,
                            onClick = {
                                diagnosticsScope.launch {
                                    crashDiagnosticsBusy = true
                                    try {
                                        val bundle = viewModel.buildCrashDiagnosticsBundle()
                                        shareCrashDiagnosticsBundle(
                                            context = context,
                                            bundle = bundle,
                                            onFeedback = { message -> showSettingsFeedback(message) },
                                        )
                                        viewModel.refreshCrashDiagnostics()
                                    } catch (_: Exception) {
                                        showSettingsFeedback("Could not build diagnostics")
                                    } finally {
                                        crashDiagnosticsBusy = false
                                    }
                                }
                            },
                        ) { Text("Share") }
                    }
                },
            )
        }

        // Permissions and sources
        SettingsSection(
            title = "Permissions and sources",
            description = "Every permission Aura requests and how it uses data.",
        ) {
            PermissionTransparencyRow(
                icon = Icons.Default.Wallpaper,
                permission = "Set wallpaper",
                scope = "Local",
                description = "Apply images as home or lock screen wallpaper. No data leaves the device.",
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Language,
                permission = "Internet",
                scope = "Remote",
                description = "Fetch wallpapers, videos, and sounds from Wallhaven, Pexels, Pixabay, YouTube (NewPipe/yt-dlp), and Freesound. Community features use Firebase.",
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Notifications,
                permission = "Notifications",
                scope = "Local",
                description = "Daily wallpaper reminders and download completion alerts. Only active when you enable daily wallpaper or download content.",
                granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.LocationOn,
                permission = "Approximate location",
                scope = "Remote",
                description = "Weather wallpaper effects fetch local conditions from Open-Meteo. Coordinates are rounded and cleared when weather effects are disabled.",
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Contacts,
                permission = "Contacts",
                scope = "Local",
                description = "Assign per-contact ringtones. Contact data stays on-device and is never uploaded.",
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Mic,
                permission = "Microphone",
                scope = "Local",
                description = "Record audio for custom sounds. Recording starts only on tap and stays local until you choose to upload.",
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Settings,
                permission = "Modify settings",
                scope = "Local",
                description = "Set default ringtone, notification, or alarm sound via system RingtoneManager.",
            )
            PermissionTransparencyRow(
                icon = Icons.Default.PlayCircle,
                permission = "Foreground service",
                scope = "Local",
                description = "Live wallpaper playback and wallpaper rotation triggers. Runs only when live wallpaper or rotation is active.",
            )
        }

        // About
        SettingsSection(
            title = "About",
            description = "Project details, source code, and the open-source building blocks behind Aura.",
        ) {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Aura",
                subtitle = "Version ${com.freevibe.BuildConfig.VERSION_NAME} • Open-source device personalization studio",
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = "Source code",
                subtitle = "Browse the project on GitHub",
                onClick = { openExternalUrl(context, AURA_SOURCE_URL) },
            )
            SettingsItem(
                icon = Icons.Default.Security,
                title = "Privacy policy",
                subtitle = "Review data use, diagnostics, community deletion, and generated wallpaper handling",
                onClick = { openExternalUrl(context, AURA_PRIVACY_POLICY_URL) },
            )
            SettingsItem(
                icon = Icons.Default.Description,
                title = "Open source licenses",
                subtitle = "See generated notices, library licenses, and content-source attributions",
                onClick = onLicensesClick,
            )
        }

        Spacer(Modifier.height(32.dp))
    }
    }

    // Interval picker
    if (showIntervalPicker) {
        IntervalPickerDialog(
            currentInterval = autoWpInterval,
            onDismiss = { showIntervalPicker = false },
            onSelect = { hours ->
                viewModel.setAutoWpInterval(hours)
                showIntervalPicker = false
            },
        )
    }

    // #10: Source picker dialog
    if (showSourcePicker) {
        SourcePickerDialog(
            currentSource = autoWpSource,
            wallhavenProviderEnabled = wallhavenProviderEnabled,
            bingProviderEnabled = bingProviderEnabled,
            pixabayProviderEnabled = pixabayProviderEnabled,
            localFolderUri = localWallpaperFolderUri,
            localFolderPermissionActive = localFolderPermissionActive,
            onDismiss = { showSourcePicker = false },
            onChooseLocalFolder = {
                showSourcePicker = false
                chooseLocalWallpaperFolder("auto")
            },
            onSelect = { source ->
                viewModel.setAutoWpSource(source)
                showSourcePicker = false
            },
        )
    }

    if (showAutoBackupIntervalPicker) {
        val intervals = listOf(
            12L to "Every 12 hours",
            24L to "Daily",
            168L to "Weekly",
            720L to "Monthly",
        )
        AlertDialog(
            onDismissRequest = { showAutoBackupIntervalPicker = false },
            title = { Text("Backup interval") },
            text = {
                Column {
                    intervals.forEach { (hours, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = autoBackupIntervalHours == hours,
                            onClick = {
                                viewModel.setAutoBackupIntervalHours(hours)
                                showAutoBackupIntervalPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoBackupIntervalPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showAutoBackupKeepPicker) {
        val keepCounts = listOf(3, 5, 10, 20)
        AlertDialog(
            onDismissRequest = { showAutoBackupKeepPicker = false },
            title = { Text("Backups to keep") },
            text = {
                Column {
                    keepCounts.forEach { count ->
                        SettingsRadioOptionRow(
                            label = autoBackupRetentionLabel(count),
                            selected = autoBackupKeepCount == count,
                            onClick = {
                                viewModel.setAutoBackupKeepCount(count)
                                showAutoBackupKeepPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoBackupKeepPicker = false }) { Text("Cancel") }
            },
        )
    }

    // #9: Grid columns picker
    if (showColumnsPicker) {
        AlertDialog(
            onDismissRequest = { showColumnsPicker = false },
            title = { Text("Grid columns") },
            text = {
                Column {
                    listOf(1 to "1 column", 2 to "2 columns", 3 to "3 columns", 4 to "4 columns").forEach { (count, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = gridColumns == count,
                            onClick = {
                                viewModel.setGridColumns(count)
                                showColumnsPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColumnsPicker = false }) { Text("Cancel") }
            },
        )
    }

    // Resolution picker
    if (showResPicker) {
        AlertDialog(
            onDismissRequest = { showResPicker = false },
            title = { Text("Preferred resolution") },
            text = {
                Column {
                    listOf("" to "Any resolution", "1920x1080" to "1920x1080 (FHD)", "2560x1440" to "2560x1440 (QHD)", "3840x2160" to "3840x2160 (4K)").forEach { (res, label) ->
                        SettingsRadioOptionRow(
                            label = label,
                            selected = preferredRes == res,
                            onClick = {
                                viewModel.setPreferredRes(res)
                                showResPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResPicker = false }) { Text("Cancel") }
            },
        )
    }

    if (showStylePicker) {
        val styleOptions = remember {
            listOf(
                "minimal",
                "amoled",
                "nature",
                "space",
                "anime",
                "abstract",
                "neon",
                "city",
                "gradient",
                "dark",
            )
        }
        var selectedStyles by remember(showStylePicker, userStyles) {
            mutableStateOf(
                userStyles.split(",")
                    .map { it.trim().lowercase(java.util.Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet()
            )
        }
        AlertDialog(
            onDismissRequest = { showStylePicker = false },
            title = { Text("Style preferences") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "These styles are prioritized across wallpaper discovery and ranking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        styleOptions.forEach { style ->
                            FilterChip(
                                selected = style in selectedStyles,
                                onClick = {
                                    selectedStyles = if (style in selectedStyles) {
                                        selectedStyles - style
                                    } else {
                                        selectedStyles + style
                                    }
                                },
                                label = { Text(stylePreferenceLabel(style)) },
                                leadingIcon = if (style in selectedStyles) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setUserStyles(selectedStyles.sorted().joinToString(","))
                    showStylePicker = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showStylePicker = false }) { Text("Cancel") }
            },
        )
    }

    // YouTube sound search queries editor
    if (showYtSoundEditor) {
        var ringQ by remember { mutableStateOf(ytRingtonesQuery) }
        var notifQ by remember { mutableStateOf(ytNotificationsQuery) }
        var alarmQ by remember { mutableStateOf(ytAlarmsQuery) }
        AlertDialog(
            onDismissRequest = { showYtSoundEditor = false },
            title = { Text("YouTube Search Queries") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Customize what YouTube searches for in each sound tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = ringQ,
                        onValueChange = { ringQ = it },
                        label = { Text("Ringtones") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = notifQ,
                        onValueChange = { notifQ = it },
                        label = { Text("Notifications") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = alarmQ,
                        onValueChange = { alarmQ = it },
                        label = { Text("Alarms") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setYtRingtonesQuery(ringQ.trim())
                    viewModel.setYtNotificationsQuery(notifQ.trim())
                    viewModel.setYtAlarmsQuery(alarmQ.trim())
                    showYtSoundEditor = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showYtSoundEditor = false }) { Text("Cancel") } },
        )
    }

    // YouTube blocked words editor
    if (showYtBlockedEditor) {
        var blockedText by remember { mutableStateOf(ytBlockedWords) }
        AlertDialog(
            onDismissRequest = { showYtBlockedEditor = false },
            title = { Text("Blocked Words") },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Comma-separated words. YouTube results containing any of these are hidden.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = blockedText,
                        onValueChange = { blockedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 5,
                        placeholder = { Text("compilation,mix,playlist...") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Text("${blockedText.split(",").filter { it.isNotBlank() }.size} words", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setYtBlockedWords(blockedText.trim())
                    showYtBlockedEditor = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showYtBlockedEditor = false }) { Text("Cancel") } },
        )
    }

    // Confirm clear cache
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear cache and offline saves?") },
            text = { Text(clearCacheConfirmation(cacheUsage)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheConfirm = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text("Cancel") }
            },
        )
    }

}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CommunityIdentityDialog(
    summary: CommunityIdentitySummary,
    cleanupBusy: Boolean,
    onRefresh: () -> Unit,
    onClearLocal: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareRequest: (CommunityIdentitySummary) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Community identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    summary.authLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "Identity suffix: ${communityIdentitySuffixLabel(summary)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.deletionRequestCode.isNotBlank()) {
                    Text(
                        "Deletion request code: ${summary.deletionRequestCode}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "No backend deletion request code is available until a Firebase identity exists.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Deletion planning covers vote markers, follows, block rows, shares, and local community caches. Public uploads, moderation records, and Firebase Auth deletion use the retained-data review path.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Clear local only removes this device's fallback community identity. It does not delete backend, Auth, or public upload records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                TextButton(
                    onClick = onClearLocal,
                    enabled = !cleanupBusy,
                ) {
                    if (cleanupBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Clear local")
                    }
                }
                if (summary.deletionRequestCode.isNotBlank()) {
                    TextButton(onClick = { onCopyCode(summary.deletionRequestCode) }) {
                        Text("Copy code")
                    }
                    TextButton(onClick = { onShareRequest(summary) }) {
                        Text("Share")
                    }
                }
            }
        },
    )
}

@Composable
private fun BlockedCreatorsDialog(
    blockedCreators: List<CommunityBlockedUser>,
    actionState: CommunityBlockActionState,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blocked creators") },
        text = {
            if (blockedCreators.isEmpty()) {
                Text(
                    "No community creators are hidden for your account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    blockedCreators.forEach { blocked ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        blocked.userId,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                    )
                                    Text(
                                        blockedCreatorSubtitle(blocked),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                val isBusy = actionState.unblockingUserId == blocked.userId
                                TextButton(
                                    onClick = { onUnblock(blocked.userId) },
                                    enabled = !isBusy,
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text("Unblock")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun blockedCreatorSubtitle(blocked: CommunityBlockedUser): String {
    val reason = blocked.reason.storageValue.lowercase(Locale.ROOT)
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
    val blockedAt = blocked.createdAt.takeIf { it > 0L }?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
    }
    return listOfNotNull(
        "Reason: $reason",
        blockedAt?.let { "Blocked: $it" },
    ).joinToString(" - ")
}

private fun communityIdentitySubtitle(summary: CommunityIdentitySummary): String =
    if (summary.hasFirebaseIdentity) {
        "${summary.authLabel} - ${communityIdentitySuffixLabel(summary)}"
    } else {
        "No Firebase identity created"
    }

private fun communityIdentitySuffixLabel(summary: CommunityIdentitySummary): String =
    if (summary.identitySuffix == "Not created") summary.identitySuffix else "...${summary.identitySuffix}"

// SettingsRadioOptionRow — extracted to SettingsComponents.kt

@Composable
private fun IntervalPickerDialog(
    currentInterval: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val intervals = listOf(1L to "1 hour", 3L to "3 hours", 6L to "6 hours",
        12L to "12 hours", 24L to "24 hours", 48L to "2 days")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wallpaper change interval") },
        text = {
            Column {
                intervals.forEach { (hours, label) ->
                    SettingsRadioOptionRow(
                        label = label,
                        selected = currentInterval == hours,
                        onClick = { onSelect(hours) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private data class SettingsBatterySnapshot(
    val percent: Int?,
    val isCharging: Boolean,
)

private data class VideoBatteryDashboardState(
    val batteryPercent: Int?,
    val isCharging: Boolean,
    val serviceFresh: Boolean,
    val serviceVisible: Boolean,
    val mediaType: String,
    val requestedFps: Int,
    val effectiveFps: Int,
    val fpsOverlayEnabled: Boolean,
    val lowBatterySaverActive: Boolean,
    val scaleMode: String,
)

@Composable
private fun rememberVideoBatteryDashboardState(
    context: Context,
    requestedFps: Int,
    fpsOverlayEnabled: Boolean,
    autoBatterySaverEnabled: Boolean,
): State<VideoBatteryDashboardState> {
    val appContext = remember(context) { context.applicationContext }
    val state = remember(appContext, requestedFps, fpsOverlayEnabled, autoBatterySaverEnabled) {
        mutableStateOf(
            readVideoBatteryDashboardState(
                context = appContext,
                requestedFps = requestedFps,
                fpsOverlayEnabled = fpsOverlayEnabled,
                autoBatterySaverEnabled = autoBatterySaverEnabled,
            ),
        )
    }
    LaunchedEffect(appContext, requestedFps, fpsOverlayEnabled, autoBatterySaverEnabled) {
        while (true) {
            state.value = readVideoBatteryDashboardState(
                context = appContext,
                requestedFps = requestedFps,
                fpsOverlayEnabled = fpsOverlayEnabled,
                autoBatterySaverEnabled = autoBatterySaverEnabled,
            )
            delay(2_000L)
        }
    }
    return state
}

private fun readVideoBatteryDashboardState(
    context: Context,
    requestedFps: Int,
    fpsOverlayEnabled: Boolean,
    autoBatterySaverEnabled: Boolean,
): VideoBatteryDashboardState {
    val battery = readSettingsBatterySnapshot(context)
    val stats = context.getSharedPreferences(VIDEO_STATS_PREFS_NAME, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val lastSeenMs = stats.getLong("last_seen_ms", 0L)
    val serviceFresh = lastSeenMs > 0L && now - lastSeenMs <= 45_000L
    val statsBatteryPercent = if (serviceFresh && stats.contains("battery_percent")) {
        stats.getInt("battery_percent", -1).takeIf { it >= 0 }
    } else {
        null
    }
    val batteryPercent = battery.percent ?: statsBatteryPercent
    val isCharging = battery.isCharging || (serviceFresh && stats.getBoolean("charging", false))
    val statsRequestedFps = if (serviceFresh) stats.getInt("requested_fps", requestedFps) else requestedFps
    val localLowBatterySaver = shouldUseVideoBatterySaver(
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        autoSaverEnabled = autoBatterySaverEnabled,
    )
    val lowBatterySaverActive = localLowBatterySaver ||
        (serviceFresh && stats.getBoolean("low_battery_saver_active", false))
    val effectiveFps = if (serviceFresh) {
        stats.getInt("effective_fps", effectiveVideoFpsLimit(statsRequestedFps, lowBatterySaverActive))
    } else {
        effectiveVideoFpsLimit(statsRequestedFps, lowBatterySaverActive)
    }
    return VideoBatteryDashboardState(
        batteryPercent = batteryPercent,
        isCharging = isCharging,
        serviceFresh = serviceFresh,
        serviceVisible = serviceFresh && stats.getBoolean("visible", false),
        mediaType = if (serviceFresh) stats.getString("media_type", "none") ?: "none" else "none",
        requestedFps = statsRequestedFps,
        effectiveFps = effectiveFps,
        fpsOverlayEnabled = fpsOverlayEnabled,
        lowBatterySaverActive = lowBatterySaverActive,
        scaleMode = if (serviceFresh) stats.getString("scale_mode", "zoom") ?: "zoom" else "zoom",
    )
}

private fun readSettingsBatterySnapshot(context: Context): SettingsBatterySnapshot {
    val intent = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (_: Exception) {
        null
    }
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale).toInt().coerceIn(0, 100)
    } else {
        null
    }
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    return SettingsBatterySnapshot(
        percent = percent,
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoBatteryDashboardCard(
    state: VideoBatteryDashboardState,
    modifier: Modifier = Modifier,
) {
    val batteryLabel = state.batteryPercent?.let { "$it%" } ?: "Unknown"
    val serviceLabel = when {
        state.serviceVisible -> "Active"
        state.serviceFresh -> "Paused"
        else -> "No heartbeat"
    }
    val mediaLabel = when (state.mediaType) {
        "gif" -> "GIF"
        "video" -> "Video"
        else -> "Idle"
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(10.dp).size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Battery dashboard", style = MaterialTheme.typography.titleMedium)
                    Text(
                        videoBatteryImpactSummary(
                            requestedFps = state.requestedFps,
                            effectiveFps = state.effectiveFps,
                            fpsOverlayEnabled = state.fpsOverlayEnabled,
                            lowBatterySaverActive = state.lowBatterySaverActive,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.batteryPercent?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (state.lowBatterySaverActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VideoDashboardMetric(
                    label = "Battery",
                    value = batteryLabel,
                    detail = if (state.isCharging) "Charging" else "Unplugged",
                )
                VideoDashboardMetric(
                    label = "Service",
                    value = serviceLabel,
                    detail = mediaLabel,
                )
                VideoDashboardMetric(
                    label = "Target",
                    value = "${state.effectiveFps} FPS",
                    detail = if (state.lowBatterySaverActive) "Auto-capped" else "Selected",
                )
                VideoDashboardMetric(
                    label = "Presentation",
                    value = if (state.scaleMode == "fit") "Fit" else "Fill",
                    detail = if (state.fpsOverlayEnabled) "Overlay on" else "Overlay off",
                )
            }
        }
    }
}

@Composable
private fun VideoDashboardMetric(
    label: String,
    value: String,
    detail: String,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 116.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsOverviewCard(
    modifier: Modifier = Modifier,
    selectedStyleCount: Int,
    schedulerEnabled: Boolean,
    schedulerInterval: Long,
    weatherEffects: Boolean,
    adaptiveTint: Boolean,
    autoPreview: Boolean,
    videoFpsLimit: Int,
    cacheUsage: CacheUsageState,
    configuredApiKeys: Int,
) {
    val setupSummary = remember(
        selectedStyleCount,
        schedulerEnabled,
        schedulerInterval,
        weatherEffects,
        adaptiveTint,
        autoPreview,
    ) {
        buildList {
            if (selectedStyleCount > 0) add("$selectedStyleCount style preferences")
            if (schedulerEnabled) add("rotation every ${formatInterval(schedulerInterval)}")
            if (weatherEffects) add("weather overlays")
            if (adaptiveTint) add("time-of-day tint")
            if (autoPreview) add("sound previews")
        }.let { enabled ->
            if (enabled.isEmpty()) {
                "Aura is set up with calm defaults. Adjust discovery, automation, and playback here whenever you want."
            } else {
                "Active setup: ${enabled.joinToString(" • ")}."
            }
        }
    }

    GlassCard(modifier = modifier) {
        HighlightPill(
            label = "Personalization overview",
            icon = Icons.Default.Tune,
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Make Aura feel intentional",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = setupSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HighlightPill(
                label = if (selectedStyleCount == 0) "No style bias yet" else "$selectedStyleCount styles selected",
                icon = Icons.Default.Wallpaper,
                tint = MaterialTheme.colorScheme.primary,
            )
            HighlightPill(
                label = if (schedulerEnabled) "Rotation on" else "Rotation off",
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.secondary,
            )
            HighlightPill(
                label = "$videoFpsLimit FPS video",
                icon = Icons.Default.VideoLibrary,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            HighlightPill(
                label = "$configuredApiKeys provider keys",
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsMetric(
                modifier = Modifier.weight(1f),
                label = "Automation",
                value = if (schedulerEnabled) formatInterval(schedulerInterval) else "Manual",
                icon = Icons.Default.Schedule,
                tint = MaterialTheme.colorScheme.primary,
            )
            SettingsMetric(
                modifier = Modifier.weight(1f),
                label = "Storage",
                value = cacheUsage.fileUsageLabel,
                icon = Icons.Default.Folder,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

// SettingsMetric — extracted to SettingsComponents.kt

// SettingsSection — extracted to SettingsComponents.kt

// SettingsItem — extracted to SettingsComponents.kt

// SettingsToggle — extracted to SettingsComponents.kt

// SettingsValueSlider — extracted to SettingsComponents.kt

// PermissionTransparencyRow — extracted to SettingsComponents.kt

// Diagnostics subtitle formatters — extracted to DiagnosticsComponents.kt

// ExternalAutomationDiagnosticsSummary — extracted to DiagnosticsComponents.kt

// BackgroundWorkDiagnosticsSummary — extracted to DiagnosticsComponents.kt

// BackgroundWorkDiagnosticRow, crashDiagnosticsSubtitle, ytDlpUpdate*, copy/share
// actions, SourceDiagnostics*, DiagnosticMetricPill, SourceDiagnosticRow,
// sourceDisplayName — all extracted to DiagnosticsComponents.kt

private fun isLocalWallpaperFolderReady(
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): Boolean = localFolderUri.isNotBlank() && localFolderPermissionActive

private fun localWallpaperFolderSubtitle(
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): String = when {
    localFolderUri.isBlank() -> "Choose a local image folder for offline rotation"
    localFolderPermissionActive -> "Folder selected for local-only wallpaper rotation"
    else -> "Permission needs repair; choose the folder again"
}

private fun wallpaperRotationSourceLabel(
    source: String,
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
): String = when (source) {
    WALLPAPER_SOURCE_LOCAL_FOLDER -> when {
        localFolderUri.isBlank() -> "Local folder (choose folder)"
        localFolderPermissionActive -> "Local folder"
        else -> "Local folder (permission needed)"
    }
    else -> sourceDisplayName(source)
}

private fun hasPersistedReadPermission(context: Context, uriString: String): Boolean {
    if (uriString.isBlank()) return false
    return runCatching {
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.isReadPermission && permission.uri.toString() == uriString
        }
    }.getOrDefault(false)
}

private fun hasPersistedWritePermission(context: Context, uriString: String): Boolean {
    if (uriString.isBlank()) return false
    return runCatching {
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.isWritePermission && permission.uri.toString() == uriString
        }
    }.getOrDefault(false)
}

private fun darkenPercentLabel(percent: Int): String =
    if (percent <= 0) "Off" else "${percent.coerceIn(0, 100)}%"

private fun rotationDarkenSubtitle(percent: Int, rotationActive: Boolean): String = when {
    percent <= 0 && rotationActive -> "Keep rotated wallpapers unchanged"
    percent <= 0 -> "Saved for the next auto-rotation or trigger you enable"
    rotationActive -> "Darkens rotated wallpapers for clock and status-bar legibility"
    else -> "Dimming is ready but no rotation trigger is active"
}

private fun autoBackupStatusSubtitle(
    enabled: Boolean,
    folderUri: String,
    folderPermissionActive: Boolean,
    intervalHours: Long,
    keepCount: Int,
): String = when {
    !enabled && folderUri.isBlank() -> "Choose a folder to unlock local, account-free scheduled backups"
    !enabled && !folderPermissionActive -> "Folder permission needs repair before backup can be enabled"
    !enabled -> "Ready. ${formatAutoBackupInterval(intervalHours)} and keeping ${keepCount.coerceAtLeast(1)} files"
    folderUri.isBlank() -> "Choose a backup folder to start scheduled exports"
    !folderPermissionActive -> "Paused. Folder permission needs repair before Aura can write backups"
    else -> "${formatAutoBackupInterval(intervalHours)}; keeping ${keepCount.coerceAtLeast(1)} newest backups"
}

private fun autoBackupFolderSubtitle(
    folderUri: String,
    folderPermissionActive: Boolean,
): String = when {
    folderUri.isBlank() -> "Choose where Aura should write JSON backup files"
    folderPermissionActive -> "Writable folder selected for scheduled backup"
    else -> "Permission needs repair; choose the folder again"
}

private fun formatAutoBackupInterval(hours: Long): String = when (hours) {
    12L -> "Every 12 hours"
    24L -> "Daily"
    168L -> "Weekly"
    720L -> "Monthly"
    else -> "Every ${hours.coerceAtLeast(1)} hours"
}

private fun autoBackupRetentionLabel(keepCount: Int): String =
    "Keep ${keepCount.coerceAtLeast(1)} newest backup${if (keepCount == 1) "" else "s"}"

private fun countSelectedStyles(raw: String): Int =
    raw.split(",").count { it.trim().isNotBlank() }

private fun userStylesSummary(raw: String): String {
    val styles = raw.split(",")
        .map { it.trim().lowercase(java.util.Locale.ROOT) }
        .filter { it.isNotBlank() }
    if (styles.isEmpty()) return "No style preference"
    return styles.joinToString(" • ") { stylePreferenceLabel(it) }
}

private fun stylePreferenceLabel(style: String): String =
    style.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun touchEffectSummary(raw: String): String = when (raw.uppercase(java.util.Locale.ROOT)) {
    "SUBTLE" -> "Subtle ripples on live wallpapers"
    "STRONG" -> "Ripples and spark bursts on touch"
    else -> "Off"
}

// formatInterval — extracted to SettingsComponents.kt

private fun cacheUsageSubtitle(cacheUsage: CacheUsageState): String =
    buildString {
        append("Using ${cacheUsage.fileUsageLabel} of temp files and offline saves")
        if (cacheUsage.hasWallpaperMetadataCache) {
            append(" + wallpaper feed cache")
        }
    }

private fun clearCacheConfirmation(cacheUsage: CacheUsageState): String =
    buildString {
        append("This will remove ${cacheUsage.fileUsageLabel} of temporary media and offline favorites")
        if (cacheUsage.hasWallpaperMetadataCache) {
            append(", and reset cached wallpaper feeds")
        }
        append(". Downloaded files are not affected.")
    }

/**
 * Picker dialog for the dark/light mode wallpaper slot. Renders the wallpaper history
 * list (most recent 10) or an explanatory empty state when the user hasn't applied any
 * wallpapers yet — both branches use the same shell so the dialog never opens-then-
 * silently-closes the way a top-level `if (history.isNotEmpty())` guard would.
 */
@Composable
private fun WallpaperSlotPickerDialog(
    title: String,
    history: List<com.freevibe.data.model.WallpaperHistoryEntity>,
    onPick: (com.freevibe.data.model.WallpaperHistoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (history.isEmpty()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        "No wallpapers applied yet",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Apply at least one wallpaper from the Wallpapers tab and it will show up here as a slot option.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(modifier = Modifier.heightIn(max = 300.dp)) {
                    LazyColumn {
                        items(history.take(10)) { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onPick(entry) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(entry.source, style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    entry.wallpaperId.take(20),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun SourcePickerDialog(
    currentSource: String,
    wallhavenProviderEnabled: Boolean,
    bingProviderEnabled: Boolean,
    pixabayProviderEnabled: Boolean,
    localFolderUri: String,
    localFolderPermissionActive: Boolean,
    onDismiss: () -> Unit,
    onChooseLocalFolder: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val localFolderReady = isLocalWallpaperFolderReady(localFolderUri, localFolderPermissionActive)
    val sources = listOf(
        "discover" to "Discover (mixed)",
        "favorites" to "My Favorites",
        WALLPAPER_SOURCE_LOCAL_FOLDER to if (localFolderReady) "Local folder" else "Local folder (choose folder)",
        "wallhaven" to "Wallhaven",
        "pixabay" to "Pixabay",
        "bing" to "Bing Daily",
    ).filter { (key, _) ->
        when (key) {
            "wallhaven" -> wallhavenProviderEnabled || currentSource == "wallhaven"
            "pixabay" -> pixabayProviderEnabled || currentSource == "pixabay"
            "bing" -> bingProviderEnabled || currentSource == "bing"
            else -> true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-wallpaper source") },
        text = {
            Column {
                sources.forEach { (key, label) ->
                    val isSelected = currentSource == key
                    val onSelectSource = {
                        if (key == WALLPAPER_SOURCE_LOCAL_FOLDER && !localFolderReady) {
                            onChooseLocalFolder()
                        } else {
                            onSelect(key)
                        }
                    }
                    SettingsRadioOptionRow(
                        label = label,
                        selected = isSelected,
                        onClick = onSelectSource,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
