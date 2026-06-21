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
            }) { Text(stringResource(R.string.settings_apikey_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = canClear,
                    onClick = {
                        onSave("")
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.settings_apikey_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
    val alarmShuffleEnabled by viewModel.alarmShuffleEnabled.collectAsStateWithLifecycle()
    val soundProfilesEnabled by viewModel.soundProfilesEnabled.collectAsStateWithLifecycle()
    val soundProfilesJson by viewModel.soundProfilesJson.collectAsStateWithLifecycle()
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
                    context.getString(R.string.settings_feedback_local_folder_saved)
                } else {
                    context.getString(R.string.settings_feedback_local_folder_no_persist)
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
                showSettingsFeedback(context.getString(R.string.settings_feedback_backup_folder_on))
            } else if (persisted) {
                showSettingsFeedback(context.getString(R.string.settings_feedback_backup_folder_saved))
            } else {
                showSettingsFeedback(context.getString(R.string.settings_feedback_backup_folder_no_persist))
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
                    LiveWallpaperLaunchMode.DIRECT -> showSettingsFeedback(context.getString(R.string.settings_feedback_parallax_direct))
                    LiveWallpaperLaunchMode.CHOOSER -> showSettingsFeedback(context.getString(R.string.settings_feedback_parallax_chooser))
                    null -> showSettingsFeedback(context.getString(R.string.settings_feedback_parallax_manual))
                }
                viewModel.clearParallaxGalleryResult()
            }
            is com.freevibe.ui.screens.settings.ParallaxGalleryResult.Failure -> {
                showSettingsFeedback(context.getString(R.string.settings_feedback_parallax_failed, result.message))
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
                        showSettingsFeedback(context.getString(R.string.settings_feedback_video_direct))
                    }
                    LiveWallpaperLaunchMode.CHOOSER -> {
                        showSettingsFeedback(context.getString(R.string.settings_feedback_video_chooser))
                    }
                    null -> {
                        showSettingsFeedback(context.getString(R.string.settings_feedback_video_manual))
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
                            showSettingsFeedback(context.getString(R.string.settings_feedback_settings_unavailable))
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
            title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall) },
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
            title = stringResource(R.string.settings_wallpapers_section_title),
            description = stringResource(R.string.settings_wallpapers_section_description),
        ) {
            SettingsToggle(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.settings_wp_auto_change_title),
                subtitle = stringResource(R.string.settings_wp_auto_change_subtitle),
                checked = autoWpEnabled,
                onCheckedChange = { viewModel.setAutoWallpaper(it) },
            )
            if (autoWpEnabled) {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.settings_wp_change_interval_title),
                    subtitle = stringResource(R.string.settings_wp_change_interval_subtitle, autoWpInterval),
                    onClick = { showIntervalPicker = true },
                )
                // #10: Source picker
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = stringResource(R.string.settings_wp_source_title),
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
                    title = stringResource(R.string.settings_wp_charging_only_title),
                    subtitle = stringResource(R.string.settings_wp_charging_only_subtitle),
                    checked = autoWpRequiresCharging,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresCharging(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Wifi,
                    title = stringResource(R.string.settings_wp_wifi_only_title),
                    subtitle = stringResource(R.string.settings_wp_wifi_only_subtitle),
                    checked = autoWpRequiresWiFi,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresWiFiOnly(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Bedtime,
                    title = stringResource(R.string.settings_wp_idle_only_title),
                    subtitle = stringResource(R.string.settings_wp_idle_only_subtitle),
                    checked = autoWpRequiresIdle,
                    onCheckedChange = { viewModel.setAutoWallpaperRequiresIdle(it) },
                )
            }
            SettingsValueSlider(
                icon = Icons.Default.Brightness4,
                title = stringResource(R.string.settings_wp_dimming_title),
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
                        title = stringResource(R.string.settings_wp_oem_battery_title, oemGuide.manufacturer),
                        subtitle = oemGuide.summary,
                        onClick = {
                            val intent = oemGuide.settingsIntent
                            if (intent != null) {
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    showSettingsFeedback(context.getString(R.string.settings_feedback_oem_battery_open_failed, oemGuide.manufacturer))
                                }
                            } else {
                                showSettingsFeedback(context.getString(R.string.settings_feedback_oem_battery_not_found, oemGuide.manufacturer))
                            }
                        },
                    )
                }
            }
            SettingsItem(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.settings_wp_local_folder_title),
                subtitle = localWallpaperFolderSubtitle(
                    localWallpaperFolderUri,
                    localFolderPermissionActive,
                ),
                onClick = { chooseLocalWallpaperFolder() },
            )
            if (localWallpaperFolderUri.isNotBlank()) {
                SettingsItem(
                    icon = Icons.Default.DeleteOutline,
                    title = stringResource(R.string.settings_wp_clear_local_folder_title),
                    subtitle = stringResource(R.string.settings_wp_clear_local_folder_subtitle),
                    onClick = { viewModel.clearLocalWallpaperFolderUri() },
                )
            }
            // NX-6: trigger-based rotation (per-unlock + screen-off pre-stage).
            // Always visible — independent of the periodic worker — so users who
            // disable timer-based rotation can still get unlock-driven changes.
            SettingsToggle(
                icon = Icons.Default.LockOpen,
                title = stringResource(R.string.settings_wp_unlock_title),
                subtitle = stringResource(R.string.settings_wp_unlock_subtitle),
                checked = rotateOnUnlock,
                onCheckedChange = { viewModel.setRotateOnUnlock(it) },
            )
            SettingsToggle(
                icon = Icons.Default.PowerSettingsNew,
                title = stringResource(R.string.settings_wp_screen_off_title),
                subtitle = stringResource(R.string.settings_wp_screen_off_subtitle),
                checked = rotateOnScreenOff,
                onCheckedChange = { viewModel.setRotateOnScreenOff(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Shuffle,
                title = stringResource(R.string.settings_wp_avoid_repeats_title),
                subtitle = stringResource(R.string.settings_wp_avoid_repeats_subtitle),
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
                title = stringResource(R.string.settings_wp_grid_columns_title),
                subtitle = stringResource(R.string.settings_wp_grid_columns_subtitle, gridColumns),
                onClick = { showColumnsPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.VideoFile,
                title = stringResource(R.string.settings_wp_video_gif_title),
                subtitle = stringResource(R.string.settings_wp_video_gif_subtitle),
                onClick = { videoPickerLauncher.launch(videoWallpaperMimeTypes()) },
            )
            // v6.1.0 — parallax from user photo
            SettingsItem(
                icon = Icons.Default.PhotoLibrary,
                title = stringResource(R.string.settings_wp_parallax_title),
                subtitle = stringResource(R.string.settings_wp_parallax_subtitle),
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
                title = stringResource(R.string.settings_wp_resolution_title),
                subtitle = if (preferredRes.isEmpty()) stringResource(R.string.settings_wp_resolution_any) else preferredRes,
                onClick = { showResPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.settings_wp_style_title),
                subtitle = userStylesSummary(userStyles),
                onClick = { showStylePicker = true },
            )
            SettingsItem(
                icon = Icons.Default.Forum,
                title = stringResource(R.string.settings_wp_reddit_title),
                subtitle = stringResource(R.string.settings_wp_reddit_subtitle),
                onClick = { showSettingsFeedback(context.getString(R.string.settings_feedback_reddit_discontinued)) },
            )
            SettingsToggle(
                icon = Icons.Default.ImageSearch,
                title = stringResource(R.string.settings_wp_bing_title),
                subtitle = if (bingProviderEnabled) {
                    stringResource(R.string.settings_wp_bing_on_subtitle)
                } else {
                    stringResource(R.string.settings_wp_bing_off_subtitle)
                },
                checked = bingProviderEnabled,
                onCheckedChange = { viewModel.setBingProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Category,
                title = stringResource(R.string.settings_wp_categories_title),
                subtitle = stringResource(R.string.settings_wp_categories_subtitle),
                onClick = onCategoriesClick,
            )
            SettingsItem(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.settings_wp_collections_title),
                subtitle = stringResource(R.string.settings_wp_collections_subtitle),
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
                    title = stringResource(R.string.settings_wp_history_title),
                    subtitle = stringResource(R.string.settings_wp_history_subtitle, wallpaperHistory.size),
                    onClick = onHistoryClick,
                )
            }
        }

        // Wallpaper Scheduler
        SettingsSection(
            title = stringResource(R.string.settings_scheduler_section_title),
            description = stringResource(R.string.settings_scheduler_section_description),
        ) {
            var showSchedulerInterval by remember { mutableStateOf(false) }
            var showSchedulerSource by remember { mutableStateOf(false) }

            SettingsToggle(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.settings_sched_auto_rotate_title),
                subtitle = if (schedulerEnabled) stringResource(R.string.settings_sched_auto_rotate_on_subtitle, formatInterval(schedulerInterval)) else stringResource(R.string.settings_sched_auto_rotate_off_subtitle),
                checked = schedulerEnabled,
                onCheckedChange = { viewModel.setSchedulerEnabled(it) },
            )
            if (schedulerEnabled) {
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.settings_sched_interval_title),
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
                        context.getString(R.string.settings_sched_collection_prefix, activeCollectionName)
                    schedulerSource == "collection" ->
                        context.getString(R.string.settings_sched_collection_none)
                    else ->
                        wallpaperRotationSourceLabel(
                            source = schedulerSource,
                            localFolderUri = localWallpaperFolderUri,
                            localFolderPermissionActive = localFolderPermissionActive,
                        )
                }
                SettingsItem(
                    icon = Icons.Default.Source,
                    title = stringResource(R.string.settings_sched_source_title),
                    subtitle = sourceSubtitle,
                    onClick = { showSchedulerSource = true },
                )
                SettingsToggle(
                    icon = Icons.Default.Home,
                    title = stringResource(R.string.settings_sched_home_title),
                    subtitle = stringResource(R.string.settings_sched_home_subtitle),
                    checked = schedulerHome,
                    onCheckedChange = { viewModel.setSchedulerHome(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.settings_sched_lock_title),
                    subtitle = stringResource(R.string.settings_sched_lock_subtitle),
                    checked = schedulerLock,
                    onCheckedChange = { viewModel.setSchedulerLock(it) },
                )
                SettingsToggle(
                    icon = Icons.Default.Shuffle,
                    title = stringResource(R.string.settings_sched_shuffle_title),
                    subtitle = if (schedulerShuffle) stringResource(R.string.settings_sched_shuffle_on_subtitle) else stringResource(R.string.settings_sched_shuffle_off_subtitle),
                    checked = schedulerShuffle,
                    onCheckedChange = { viewModel.setSchedulerShuffle(it) },
                )
            }

            if (showSchedulerInterval) {
                val intervals = listOf(
                    15L to stringResource(R.string.settings_sched_interval_15m), 30L to stringResource(R.string.settings_sched_interval_30m), 60L to stringResource(R.string.settings_sched_interval_1h),
                    120L to stringResource(R.string.settings_sched_interval_2h), 360L to stringResource(R.string.settings_sched_interval_6h), 720L to stringResource(R.string.settings_sched_interval_12h),
                    1440L to stringResource(R.string.settings_sched_interval_24h), 2880L to stringResource(R.string.settings_sched_interval_2d),
                )
                AlertDialog(
                    onDismissRequest = { showSchedulerInterval = false },
                    title = { Text(stringResource(R.string.settings_sched_interval_title)) },
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
                    confirmButton = { TextButton(onClick = { showSchedulerInterval = false }) { Text(stringResource(R.string.common_cancel)) } },
                )
            }

            var showCollectionPicker by remember { mutableStateOf(false) }
            if (showSchedulerSource) {
                val sources = listOf(
                    "discover" to stringResource(R.string.settings_sched_source_discover), "favorites" to stringResource(R.string.settings_sched_source_favorites),
                    WALLPAPER_SOURCE_LOCAL_FOLDER to stringResource(R.string.settings_sched_source_local),
                    "wallhaven" to stringResource(R.string.settings_sched_source_wallhaven), "pixabay" to stringResource(R.string.settings_sched_source_pixabay),
                    "bing" to stringResource(R.string.settings_sched_source_bing), "collection" to stringResource(R.string.settings_sched_source_collection),
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
                    title = { Text(stringResource(R.string.settings_sched_wp_source_title)) },
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
                    confirmButton = { TextButton(onClick = { showSchedulerSource = false }) { Text(stringResource(R.string.common_cancel)) } },
                )
            }

            if (showCollectionPicker) {
                val collections by viewModel.collections.collectAsStateWithLifecycle()
                val activeId by viewModel.schedulerCollectionId.collectAsStateWithLifecycle()
                AlertDialog(
                    onDismissRequest = { showCollectionPicker = false },
                    title = { Text(stringResource(R.string.settings_sched_collection_picker_title)) },
                    text = {
                        if (collections.isEmpty()) {
                            // Empty-state guidance: we can't rotate through something that
                            // doesn't exist yet.
                            Column {
                                Text(
                                    stringResource(R.string.settings_sched_collection_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.settings_sched_collection_empty_hint),
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
                    confirmButton = { TextButton(onClick = { showCollectionPicker = false }) { Text(stringResource(R.string.common_cancel)) } },
                )
            }
        }

        // Library backup
        SettingsSection(
            title = stringResource(R.string.settings_backup_section_title),
            description = stringResource(R.string.settings_backup_section_description),
        ) {
            SettingsToggle(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.settings_backup_scheduled_title),
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
                        showSettingsFeedback(context.getString(R.string.settings_feedback_backup_choose_folder))
                    } else {
                        viewModel.setAutoBackupEnabled(true)
                    }
                },
            )
            SettingsItem(
                icon = Icons.Default.FolderOpen,
                title = stringResource(R.string.settings_backup_folder_title),
                subtitle = autoBackupFolderSubtitle(
                    folderUri = autoBackupFolderUri,
                    folderPermissionActive = autoBackupFolderPermissionActive,
                ),
                onClick = { chooseAutoBackupFolder() },
            )
            if (autoBackupFolderUri.isNotBlank()) {
                SettingsItem(
                    icon = Icons.Default.DeleteOutline,
                    title = stringResource(R.string.settings_backup_clear_title),
                    subtitle = stringResource(R.string.settings_backup_clear_subtitle),
                    onClick = { viewModel.clearAutoBackupFolderUri() },
                )
            }
            SettingsItem(
                icon = Icons.Default.Timer,
                title = stringResource(R.string.settings_backup_interval_title),
                subtitle = formatAutoBackupInterval(autoBackupIntervalHours),
                onClick = { showAutoBackupIntervalPicker = true },
            )
            SettingsItem(
                icon = Icons.Default.History,
                title = stringResource(R.string.settings_backup_keep_title),
                subtitle = autoBackupRetentionLabel(autoBackupKeepCount),
                onClick = { showAutoBackupKeepPicker = true },
            )
        }

        // Smart Features
        SettingsSection(
            title = stringResource(R.string.settings_smart_section_title),
            description = stringResource(R.string.settings_smart_section_description),
        ) {
            SettingsToggle(
                icon = Icons.Default.Today,
                title = stringResource(R.string.settings_smart_daily_wp_title),
                subtitle = stringResource(R.string.settings_smart_daily_wp_subtitle),
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
                title = stringResource(R.string.settings_smart_tint_title),
                subtitle = stringResource(R.string.settings_smart_tint_subtitle),
                checked = adaptiveTint,
                onCheckedChange = { viewModel.setAdaptiveTint(it) },
            )
            if (adaptiveTint) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.settings_smart_tint_intensity), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Slider(
                        value = adaptiveTintIntensity,
                        onValueChange = { viewModel.setAdaptiveTintIntensity(it) },
                        valueRange = 0.1f..1f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.settings_smart_tint_range),
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
                                stringResource(R.string.settings_smart_weather_data),
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                stringResource(R.string.settings_smart_weather_license),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            SettingsToggle(
                icon = Icons.Default.Brightness4,
                title = stringResource(R.string.settings_smart_dark_switch_title),
                subtitle = stringResource(R.string.settings_smart_dark_switch_subtitle),
                checked = darkModeSwitch,
                onCheckedChange = { viewModel.setDarkModeSwitch(it) },
            )
            if (darkModeSwitch) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.settings_smart_wallpaper_slots), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text(stringResource(R.string.settings_smart_light_mode), style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (lightModeWallpaperId.isEmpty()) stringResource(R.string.settings_smart_slot_not_set) else stringResource(R.string.settings_smart_slot_set),
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
                            Text(stringResource(R.string.settings_smart_dark_mode), style = MaterialTheme.typography.labelSmall)
                            Text(
                                if (darkModeWallpaperId.isEmpty()) stringResource(R.string.settings_smart_slot_not_set) else stringResource(R.string.settings_smart_slot_set),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.settings_smart_slot_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // VFX particle overlays
            var showVfxPicker by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.AutoFixHigh,
                title = stringResource(R.string.settings_smart_vfx_title),
                subtitle = stringResource(R.string.settings_smart_vfx_subtitle),
                onClick = { showVfxPicker = true },
            )
            if (showVfxPicker) {
                val effects = listOf(
                    "NONE" to stringResource(R.string.settings_smart_vfx_none), "FIREFLIES" to stringResource(R.string.settings_smart_vfx_fireflies),
                    "SAKURA" to stringResource(R.string.settings_smart_vfx_sakura), "EMBERS" to stringResource(R.string.settings_smart_vfx_embers),
                    "BUBBLES" to stringResource(R.string.settings_smart_vfx_bubbles), "LEAVES" to stringResource(R.string.settings_smart_vfx_leaves),
                    "SPARKLES" to stringResource(R.string.settings_smart_vfx_sparkles),
                )
                var currentVfx by remember {
                    mutableStateOf(
                        context.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                            .getString("vfx_effect", "NONE") ?: "NONE"
                    )
                }
                AlertDialog(
                    onDismissRequest = { showVfxPicker = false },
                    title = { Text(stringResource(R.string.settings_smart_vfx_dialog_title)) },
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
                    confirmButton = { TextButton(onClick = { showVfxPicker = false }) { Text(stringResource(R.string.common_close)) } },
                )
            }
            var showTouchEffectsPicker by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.TouchApp,
                title = stringResource(R.string.settings_smart_touch_title),
                subtitle = touchEffectSummary(touchEffectStrength),
                onClick = { showTouchEffectsPicker = true },
            )
            if (showTouchEffectsPicker) {
                val modes = listOf(
                    "OFF" to stringResource(R.string.settings_smart_touch_off),
                    "SUBTLE" to stringResource(R.string.settings_smart_touch_subtle),
                    "STRONG" to stringResource(R.string.settings_smart_touch_strong),
                )
                AlertDialog(
                    onDismissRequest = { showTouchEffectsPicker = false },
                    title = { Text(stringResource(R.string.settings_smart_touch_dialog_title)) },
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
                    confirmButton = { TextButton(onClick = { showTouchEffectsPicker = false }) { Text(stringResource(R.string.common_close)) } },
                )
            }
            SettingsToggle(
                icon = Icons.Default.Accessibility,
                title = stringResource(R.string.settings_smart_reduce_title),
                subtitle = stringResource(R.string.settings_smart_reduce_subtitle),
                checked = reduceAnimations,
                onCheckedChange = { viewModel.setReduceAnimations(it) },
            )
            // Dark/light mode wallpaper pickers.
            // Earlier revision bailed silently when wallpaperHistory was empty, leaving the
            // user clicking the slot card with no feedback. Now the dialog opens regardless
            // and shows an explanatory empty state so the affordance isn't a dead click.
            if (showDarkModeWallpaperPicker) {
                WallpaperSlotPickerDialog(
                    title = stringResource(R.string.settings_smart_dark_wp_picker),
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
                    title = stringResource(R.string.settings_smart_light_wp_picker),
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
            title = stringResource(R.string.settings_sounds_section_title),
            description = stringResource(R.string.settings_sounds_section_description),
        ) {
            SettingsToggle(
                icon = Icons.Default.PlayCircle,
                title = stringResource(R.string.settings_sounds_auto_preview_title),
                subtitle = if (autoPreview) stringResource(R.string.settings_sounds_auto_preview_on_subtitle) else stringResource(R.string.settings_sounds_auto_preview_off_subtitle),
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
                        Text(stringResource(R.string.settings_sounds_volume_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.settings_sounds_volume_subtitle),
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
                    Text(stringResource(R.string.settings_sounds_volume_percent, (previewVolume * 100).toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SettingsItem(
                icon = Icons.Default.SmartDisplay,
                title = stringResource(R.string.settings_sounds_yt_queries_title),
                subtitle = stringResource(R.string.settings_sounds_yt_queries_subtitle),
                onClick = { showYtSoundEditor = true },
            )
            SettingsToggle(
                icon = Icons.Default.SmartDisplay,
                title = stringResource(R.string.settings_sounds_yt_enable_title),
                subtitle = if (youtubeProviderEnabled) {
                    stringResource(R.string.settings_sounds_yt_on_subtitle)
                } else {
                    stringResource(R.string.settings_sounds_yt_off_subtitle)
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
                title = stringResource(R.string.settings_sounds_blocked_words_title),
                subtitle = stringResource(R.string.settings_sounds_blocked_words_subtitle, ytBlockedWords.split(",").count { it.isNotBlank() }),
                onClick = { showYtBlockedEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.LibraryMusic,
                title = stringResource(R.string.settings_sounds_sources_title),
                subtitle = stringResource(R.string.settings_sounds_sources_subtitle),
                onClick = onLicensesClick,
            )
            SettingsToggle(
                icon = Icons.Default.Shuffle,
                title = stringResource(R.string.settings_sounds_ringtone_shuffle_title),
                subtitle = if (ringtoneShuffleEnabled) {
                    stringResource(R.string.settings_sounds_ringtone_shuffle_on_subtitle, formatInterval(ringtoneShuffleIntervalHours * 60))
                } else {
                    stringResource(R.string.settings_sounds_ringtone_shuffle_off_subtitle)
                },
                checked = ringtoneShuffleEnabled,
                onCheckedChange = { viewModel.setRingtoneShuffleEnabled(it) },
            )
            if (ringtoneShuffleEnabled) {
                var showShuffleIntervalPicker by remember { mutableStateOf(false) }
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = stringResource(R.string.settings_sounds_shuffle_interval_title),
                    subtitle = formatInterval(ringtoneShuffleIntervalHours * 60),
                    onClick = { showShuffleIntervalPicker = true },
                )
                if (showShuffleIntervalPicker) {
                    val intervals = listOf(1L to stringResource(R.string.settings_sounds_shuffle_every_hour), 6L to stringResource(R.string.settings_sounds_shuffle_every_6h), 12L to stringResource(R.string.settings_sounds_shuffle_every_12h), 24L to stringResource(R.string.settings_sounds_shuffle_every_day), 72L to stringResource(R.string.settings_sounds_shuffle_every_3d))
                    AlertDialog(
                        onDismissRequest = { showShuffleIntervalPicker = false },
                        title = { Text(stringResource(R.string.settings_sounds_shuffle_interval_title)) },
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
                        confirmButton = { TextButton(onClick = { showShuffleIntervalPicker = false }) { Text(stringResource(R.string.common_cancel)) } },
                    )
                }
            }
            SettingsToggle(
                icon = Icons.Default.Alarm,
                title = stringResource(R.string.settings_sounds_alarm_shuffle_title),
                subtitle = if (alarmShuffleEnabled) {
                    stringResource(R.string.settings_sounds_alarm_shuffle_on_subtitle)
                } else {
                    stringResource(R.string.settings_sounds_alarm_shuffle_off_subtitle)
                },
                checked = alarmShuffleEnabled,
                onCheckedChange = { viewModel.setAlarmShuffleEnabled(it) },
            )
            val profileCount = remember(soundProfilesJson) {
                com.freevibe.service.parseProfiles(soundProfilesJson).size
            }
            SettingsToggle(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.settings_sounds_profiles_title),
                subtitle = if (soundProfilesEnabled) {
                    stringResource(R.string.settings_sounds_profiles_on_subtitle, profileCount)
                } else {
                    stringResource(R.string.settings_sounds_profiles_off_subtitle)
                },
                checked = soundProfilesEnabled,
                onCheckedChange = { viewModel.setSoundProfilesEnabled(it) },
            )
        }

        // Video Wallpapers
        SettingsSection(
            title = stringResource(R.string.settings_video_section_title),
            description = stringResource(R.string.settings_video_section_description),
        ) {
            var showFpsPicker by remember { mutableStateOf(false) }
            VideoBatteryDashboardCard(
                state = videoBatteryDashboard,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingsToggle(
                icon = Icons.Default.BatteryChargingFull,
                title = stringResource(R.string.settings_video_battery_saver_title),
                subtitle = if (videoAutoBatterySaver)
                    stringResource(R.string.settings_video_battery_saver_on_subtitle)
                else
                    stringResource(R.string.settings_video_battery_saver_off_subtitle),
                checked = videoAutoBatterySaver,
                onCheckedChange = { viewModel.setVideoAutoBatterySaver(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.settings_video_fps_overlay_title),
                subtitle = if (videoFpsOverlayEnabled)
                    stringResource(R.string.settings_video_fps_overlay_on_subtitle)
                else
                    stringResource(R.string.settings_video_fps_overlay_off_subtitle),
                checked = videoFpsOverlayEnabled,
                onCheckedChange = { viewModel.setVideoFpsOverlayEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Speed,
                title = stringResource(R.string.settings_video_fps_limit_title),
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
                    title = { Text(stringResource(R.string.settings_video_fps_dialog_title)) },
                    text = {
                        Column {
                            listOf(15 to stringResource(R.string.settings_video_fps_15), 30 to stringResource(R.string.settings_video_fps_30), 60 to stringResource(R.string.settings_video_fps_60)).forEach { (fps, label) ->
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
                    confirmButton = { TextButton(onClick = { showFpsPicker = false }) { Text(stringResource(R.string.common_cancel)) } },
                )
            }
        }

        // API Keys
        SettingsSection(
            title = stringResource(R.string.settings_services_section_title),
            description = stringResource(R.string.settings_services_section_description),
        ) {
            SettingsToggle(
                icon = Icons.Default.Groups,
                title = stringResource(R.string.settings_services_community_title),
                subtitle = if (communityProviderEnabled) {
                    stringResource(R.string.settings_services_community_on_subtitle)
                } else {
                    stringResource(R.string.settings_services_community_off_subtitle)
                },
                checked = communityProviderEnabled,
                onCheckedChange = { viewModel.setCommunityProviderEnabled(it) },
            )
            if (communityProviderEnabled) {
                SettingsItem(
                    icon = Icons.Default.VerifiedUser,
                    title = stringResource(R.string.settings_services_guidelines_title),
                    subtitle = if (communityGuidelinesAccepted) {
                        stringResource(R.string.settings_services_guidelines_accepted_subtitle, communityGuidelinesAcceptedVersion)
                    } else {
                        stringResource(R.string.settings_services_guidelines_required_subtitle)
                    },
                    onClick = { showCommunityGuidelines = true },
                )
            }
            if (communityProviderEnabled && communityGuidelinesAccepted) {
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.settings_services_identity_title),
                    subtitle = communityIdentitySubtitle(communityIdentitySummary),
                    onClick = {
                        viewModel.refreshCommunityIdentitySummary()
                        showCommunityIdentity = true
                    },
                )
                SettingsItem(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.settings_services_creator_title),
                    subtitle = stringResource(R.string.settings_services_creator_subtitle),
                    onClick = onCreatorProfileClick,
                )
                SettingsItem(
                    icon = Icons.Default.Block,
                    title = stringResource(R.string.settings_services_blocked_title),
                    subtitle = if (blockedCommunityCreators.isEmpty()) {
                        stringResource(R.string.settings_services_blocked_none_subtitle)
                    } else {
                        stringResource(R.string.settings_services_blocked_count_subtitle, blockedCommunityCreators.size)
                    },
                    onClick = { showBlockedCreators = true },
                )
                if (viewModel.isAdmin) {
                    SettingsItem(
                        icon = Icons.Default.Report,
                        title = stringResource(R.string.settings_services_reports_title),
                        subtitle = stringResource(R.string.settings_services_reports_subtitle),
                        onClick = onCommunityReportsClick,
                    )
                }
            }
            var showWallhavenKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.Key,
                title = stringResource(R.string.settings_services_wallhaven_key_title),
                subtitle = stringResource(R.string.settings_services_wallhaven_key_subtitle),
                onClick = { showWallhavenKey = true },
            )
            SettingsToggle(
                icon = Icons.Default.ImageSearch,
                title = stringResource(R.string.settings_services_wallhaven_enable_title),
                subtitle = if (wallhavenProviderEnabled) {
                    stringResource(R.string.settings_services_wallhaven_on_subtitle)
                } else {
                    stringResource(R.string.settings_services_wallhaven_off_subtitle)
                },
                checked = wallhavenProviderEnabled,
                onCheckedChange = { viewModel.setWallhavenProviderEnabled(it) },
            )
            if (showWallhavenKey) {
                ProviderApiKeyDialog(
                    title = stringResource(R.string.settings_services_wallhaven_dialog_title),
                    description = stringResource(R.string.settings_services_wallhaven_dialog_desc),
                    value = wallhavenApiKey,
                    placeholder = stringResource(R.string.settings_services_wallhaven_dialog_placeholder),
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
                title = stringResource(R.string.settings_services_sketchy_title),
                subtitle = if (wallhavenApiKey.isBlank())
                    stringResource(R.string.settings_services_sketchy_no_key_subtitle)
                else
                    stringResource(R.string.settings_services_sketchy_subtitle),
                checked = showSketchyContent,
                onCheckedChange = { viewModel.setShowSketchy(it) },
            )
            SettingsToggle(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.settings_services_nsfw_title),
                subtitle = if (wallhavenApiKey.isBlank())
                    stringResource(R.string.settings_services_nsfw_no_key_subtitle)
                else
                    stringResource(R.string.settings_services_nsfw_subtitle),
                checked = showNsfwContent,
                onCheckedChange = { viewModel.setShowNsfw(it) },
            )
            var showPexelsKey by remember { mutableStateOf(false) }
            SettingsToggle(
                icon = Icons.Default.PhotoLibrary,
                title = stringResource(R.string.settings_services_pexels_enable_title),
                subtitle = if (pexelsProviderEnabled) {
                    stringResource(R.string.settings_services_pexels_on_subtitle)
                } else {
                    stringResource(R.string.settings_services_pexels_off_subtitle)
                },
                checked = pexelsProviderEnabled,
                onCheckedChange = { viewModel.setPexelsProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Key,
                title = stringResource(R.string.settings_services_pexels_key_title),
                subtitle = stringResource(R.string.settings_services_pexels_key_subtitle),
                onClick = { showPexelsKey = true },
            )
            if (showPexelsKey) {
                ProviderApiKeyDialog(
                    title = stringResource(R.string.settings_services_pexels_dialog_title),
                    description = stringResource(R.string.settings_services_pexels_dialog_desc),
                    value = pexelsApiKey,
                    placeholder = stringResource(R.string.settings_services_pexels_dialog_placeholder),
                    onSave = viewModel::setPexelsKey,
                    onDismiss = { showPexelsKey = false },
                )
            }
            var showPixabayKey by remember { mutableStateOf(false) }
            SettingsToggle(
                icon = Icons.Default.Collections,
                title = stringResource(R.string.settings_services_pixabay_enable_title),
                subtitle = if (pixabayProviderEnabled) {
                    stringResource(R.string.settings_services_pixabay_on_subtitle)
                } else {
                    stringResource(R.string.settings_services_pixabay_off_subtitle)
                },
                checked = pixabayProviderEnabled,
                onCheckedChange = { viewModel.setPixabayProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Key,
                title = stringResource(R.string.settings_services_pixabay_key_title),
                subtitle = stringResource(R.string.settings_services_pixabay_key_subtitle),
                onClick = { showPixabayKey = true },
            )
            if (showPixabayKey) {
                ProviderApiKeyDialog(
                    title = stringResource(R.string.settings_services_pixabay_dialog_title),
                    description = stringResource(R.string.settings_services_pixabay_dialog_desc),
                    value = pixabayApiKey,
                    placeholder = stringResource(R.string.settings_services_pixabay_dialog_placeholder),
                    onSave = viewModel::setPixabayKey,
                    onDismiss = { showPixabayKey = false },
                )
            }
            var showFreesoundKey by remember { mutableStateOf(false) }
            SettingsItem(
                icon = Icons.Default.MusicNote,
                title = stringResource(R.string.settings_services_freesound_key_title),
                subtitle = stringResource(R.string.settings_services_freesound_key_subtitle),
                onClick = { showFreesoundKey = true },
            )
            if (showFreesoundKey) {
                ProviderApiKeyDialog(
                    title = stringResource(R.string.settings_services_freesound_dialog_title),
                    description = stringResource(R.string.settings_services_freesound_dialog_desc),
                    value = freesoundApiKey,
                    placeholder = stringResource(R.string.settings_services_freesound_dialog_placeholder),
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
                title = stringResource(R.string.settings_services_generated_enable_title),
                subtitle = if (generatedContentProviderEnabled) {
                    stringResource(R.string.settings_services_generated_on_subtitle)
                } else {
                    stringResource(R.string.settings_services_generated_off_subtitle)
                },
                checked = generatedContentProviderEnabled,
                onCheckedChange = { viewModel.setGeneratedContentProviderEnabled(it) },
            )
            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_services_generated_disclosure_title),
                subtitle = if (generatedContentDisclosureAccepted) {
                    stringResource(R.string.settings_services_generated_disclosure_accepted_subtitle)
                } else {
                    stringResource(R.string.settings_services_generated_disclosure_subtitle)
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
                    title = stringResource(R.string.settings_services_generated_studio_title),
                    subtitle = stringResource(R.string.settings_services_generated_studio_subtitle),
                    onClick = onGeneratedWallpapersClick,
                )
                SettingsItem(
                    icon = Icons.Default.Key,
                    title = stringResource(R.string.settings_services_stability_key_title),
                    subtitle = stringResource(R.string.settings_services_stability_key_subtitle),
                    onClick = { showStabilityKey = true },
                )
            }
            if (generatedContentProviderEnabled && showStabilityKey) {
                ProviderApiKeyDialog(
                    title = stringResource(R.string.settings_services_stability_dialog_title),
                    description = stringResource(R.string.settings_services_stability_dialog_desc),
                    value = stabilityAiKey,
                    placeholder = stringResource(R.string.settings_services_stability_dialog_placeholder),
                    onSave = viewModel::setStabilityKey,
                    onDismiss = { showStabilityKey = false },
                )
            }
        }

        // Storage
        SettingsSection(
            title = stringResource(R.string.settings_storage_section_title),
            description = stringResource(R.string.settings_storage_section_description),
        ) {
            SettingsItem(
                icon = Icons.Default.Download,
                title = stringResource(R.string.settings_storage_downloads_title),
                subtitle = stringResource(R.string.settings_storage_downloads_subtitle),
                onClick = onDownloadsClick,
            )
            SettingsItem(
                icon = Icons.Default.Folder,
                title = stringResource(R.string.settings_storage_free_up_title),
                subtitle = cacheUsageSubtitle(cacheUsage),
                onClick = { showClearCacheConfirm = true },
            )
        }

        // Diagnostics — opt-in surface for "why is X tab loading slowly?".
        // Reads in-memory metrics collected by SourceMetrics; resets on process death.
        var showDiagnostics by remember { mutableStateOf(false) }
        SettingsSection(
            title = stringResource(R.string.settings_diagnostics_section_title),
            description = stringResource(R.string.settings_diagnostics_section_description),
        ) {
            SettingsItem(
                icon = Icons.Default.BugReport,
                title = stringResource(R.string.settings_diag_crash_title),
                subtitle = crashDiagnosticsSubtitle(crashDiagnostics),
                onClick = {
                    viewModel.refreshCrashDiagnostics()
                    showCrashDiagnostics = true
                },
            )
            SettingsItem(
                icon = Icons.Default.Schedule,
                title = stringResource(R.string.settings_diag_background_title),
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
                title = stringResource(R.string.settings_diag_source_title),
                subtitle = if (diagnostics.isEmpty()) {
                    stringResource(R.string.settings_diag_source_empty_subtitle)
                } else {
                    stringResource(R.string.settings_diag_source_count_subtitle, diagnostics.size)
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
                title = { Text(stringResource(R.string.settings_diag_background_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_diag_background_dialog_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        BackgroundWorkDiagnosticsSummary(snapshot)
                        if (snapshot.rows.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_diag_background_no_rows),
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
                    TextButton(onClick = { showBackgroundWorkDiagnostics = false }) { Text(stringResource(R.string.common_close)) }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.refreshBackgroundWorkDiagnostics() }) { Text(stringResource(R.string.common_refresh)) }
                },
            )
        }
        if (showDiagnostics) {
            val snapshots = diagnostics
            AlertDialog(
                onDismissRequest = { showDiagnostics = false },
                title = { Text(stringResource(R.string.settings_diag_source_dialog_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_diag_source_dialog_body),
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
                confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text(stringResource(R.string.common_close)) } },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.resetDiagnostics()
                    }) { Text(stringResource(R.string.common_reset)) }
                },
            )
        }
        if (showCrashDiagnostics) {
            AlertDialog(
                onDismissRequest = { if (!crashDiagnosticsBusy) showCrashDiagnostics = false },
                title = { Text(stringResource(R.string.settings_diag_crash_dialog_title)) },
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
                            stringResource(R.string.settings_diag_crash_dialog_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.settings_diag_crash_dialog_no_send),
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
                                    showSettingsFeedback(context.getString(R.string.settings_feedback_diagnostics_failed))
                                } finally {
                                    crashDiagnosticsBusy = false
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.settings_diag_crash_copy)) }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = !crashDiagnosticsBusy,
                            onClick = { showCrashDiagnostics = false },
                        ) { Text(stringResource(R.string.common_close)) }
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
                                        showSettingsFeedback(context.getString(R.string.settings_feedback_diagnostics_failed))
                                    } finally {
                                        crashDiagnosticsBusy = false
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.settings_diag_crash_share)) }
                    }
                },
            )
        }

        // Permissions and sources
        SettingsSection(
            title = stringResource(R.string.settings_permissions_section_title),
            description = stringResource(R.string.settings_permissions_section_description),
        ) {
            PermissionTransparencyRow(
                icon = Icons.Default.Wallpaper,
                permission = stringResource(R.string.settings_perm_wallpaper),
                scope = stringResource(R.string.settings_perm_wallpaper_scope),
                description = stringResource(R.string.settings_perm_wallpaper_desc),
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Language,
                permission = stringResource(R.string.settings_perm_internet),
                scope = stringResource(R.string.settings_perm_internet_scope),
                description = stringResource(R.string.settings_perm_internet_desc),
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Notifications,
                permission = stringResource(R.string.settings_perm_notifications),
                scope = stringResource(R.string.settings_perm_notifications_scope),
                description = stringResource(R.string.settings_perm_notifications_desc),
                granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.LocationOn,
                permission = stringResource(R.string.settings_perm_location),
                scope = stringResource(R.string.settings_perm_location_scope),
                description = stringResource(R.string.settings_perm_location_desc),
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Contacts,
                permission = stringResource(R.string.settings_perm_contacts),
                scope = stringResource(R.string.settings_perm_contacts_scope),
                description = stringResource(R.string.settings_perm_contacts_desc),
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Mic,
                permission = stringResource(R.string.settings_perm_microphone),
                scope = stringResource(R.string.settings_perm_microphone_scope),
                description = stringResource(R.string.settings_perm_microphone_desc),
                granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            )
            PermissionTransparencyRow(
                icon = Icons.Default.Settings,
                permission = stringResource(R.string.settings_perm_modify_settings),
                scope = stringResource(R.string.settings_perm_modify_settings_scope),
                description = stringResource(R.string.settings_perm_modify_settings_desc),
            )
            PermissionTransparencyRow(
                icon = Icons.Default.PlayCircle,
                permission = stringResource(R.string.settings_perm_foreground),
                scope = stringResource(R.string.settings_perm_foreground_scope),
                description = stringResource(R.string.settings_perm_foreground_desc),
            )
        }

        // About
        SettingsSection(
            title = stringResource(R.string.settings_about_section_title),
            description = stringResource(R.string.settings_about_section_description),
        ) {
            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_about_app_title),
                subtitle = stringResource(R.string.settings_about_app_subtitle, com.freevibe.BuildConfig.VERSION_NAME),
                onClick = {},
            )
            SettingsItem(
                icon = Icons.Default.Code,
                title = stringResource(R.string.settings_about_source_title),
                subtitle = stringResource(R.string.settings_about_source_subtitle),
                onClick = { openExternalUrl(context, AURA_SOURCE_URL) },
            )
            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_about_privacy_title),
                subtitle = stringResource(R.string.settings_about_privacy_subtitle),
                onClick = { openExternalUrl(context, AURA_PRIVACY_POLICY_URL) },
            )
            SettingsItem(
                icon = Icons.Default.Description,
                title = stringResource(R.string.settings_about_licenses_title),
                subtitle = stringResource(R.string.settings_about_licenses_subtitle),
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
            12L to stringResource(R.string.settings_picker_backup_interval_12h),
            24L to stringResource(R.string.settings_picker_backup_interval_daily),
            168L to stringResource(R.string.settings_picker_backup_interval_weekly),
            720L to stringResource(R.string.settings_picker_backup_interval_monthly),
        )
        AlertDialog(
            onDismissRequest = { showAutoBackupIntervalPicker = false },
            title = { Text(stringResource(R.string.settings_picker_backup_interval_title)) },
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
                TextButton(onClick = { showAutoBackupIntervalPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showAutoBackupKeepPicker) {
        val keepCounts = listOf(3, 5, 10, 20)
        AlertDialog(
            onDismissRequest = { showAutoBackupKeepPicker = false },
            title = { Text(stringResource(R.string.settings_picker_backup_keep_title)) },
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
                TextButton(onClick = { showAutoBackupKeepPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    // #9: Grid columns picker
    if (showColumnsPicker) {
        AlertDialog(
            onDismissRequest = { showColumnsPicker = false },
            title = { Text(stringResource(R.string.settings_picker_grid_columns_title)) },
            text = {
                Column {
                    listOf(1 to stringResource(R.string.settings_picker_grid_1), 2 to stringResource(R.string.settings_picker_grid_2), 3 to stringResource(R.string.settings_picker_grid_3), 4 to stringResource(R.string.settings_picker_grid_4)).forEach { (count, label) ->
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
                TextButton(onClick = { showColumnsPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    // Resolution picker
    if (showResPicker) {
        AlertDialog(
            onDismissRequest = { showResPicker = false },
            title = { Text(stringResource(R.string.settings_picker_resolution_title)) },
            text = {
                Column {
                    listOf("" to stringResource(R.string.settings_picker_resolution_any), "1920x1080" to stringResource(R.string.settings_picker_resolution_fhd), "2560x1440" to stringResource(R.string.settings_picker_resolution_qhd), "3840x2160" to stringResource(R.string.settings_picker_resolution_4k)).forEach { (res, label) ->
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
                TextButton(onClick = { showResPicker = false }) { Text(stringResource(R.string.common_cancel)) }
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
            title = { Text(stringResource(R.string.settings_picker_styles_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_picker_styles_hint),
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
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showStylePicker = false }) { Text(stringResource(R.string.common_cancel)) }
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
            title = { Text(stringResource(R.string.settings_picker_yt_queries_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_picker_yt_queries_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = ringQ,
                        onValueChange = { ringQ = it },
                        label = { Text(stringResource(R.string.settings_picker_yt_ringtones_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = notifQ,
                        onValueChange = { notifQ = it },
                        label = { Text(stringResource(R.string.settings_picker_yt_notifications_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = alarmQ,
                        onValueChange = { alarmQ = it },
                        label = { Text(stringResource(R.string.settings_picker_yt_alarms_label)) },
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
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showYtSoundEditor = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // YouTube blocked words editor
    if (showYtBlockedEditor) {
        var blockedText by remember { mutableStateOf(ytBlockedWords) }
        AlertDialog(
            onDismissRequest = { showYtBlockedEditor = false },
            title = { Text(stringResource(R.string.settings_picker_blocked_words_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_picker_blocked_words_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = blockedText,
                        onValueChange = { blockedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 5,
                        placeholder = { Text(stringResource(R.string.settings_picker_blocked_words_placeholder)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Text(stringResource(R.string.settings_picker_blocked_words_count, blockedText.split(",").filter { it.isNotBlank() }.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setYtBlockedWords(blockedText.trim())
                    showYtBlockedEditor = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showYtBlockedEditor = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // Confirm clear cache
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_picker_clear_cache_title)) },
            text = { Text(clearCacheConfirmation(cacheUsage)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheConfirm = false
                }) { Text(stringResource(R.string.settings_picker_clear_action), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

}

// Dialogs, battery dashboard, overview card, and helper functions extracted to SettingsDialogs.kt
