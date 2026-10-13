package com.freevibe.ui.screens.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.service.UpdateCheckFailure
import com.freevibe.service.UpdateCheckResult
import com.freevibe.service.UpdateCheckSkip
import com.freevibe.ui.components.UpdateNotesDialog
import com.freevibe.ui.util.openExternalUrl

internal const val AURA_SOURCE_URL = "https://github.com/SysAdminDoc/Aura"
internal const val AURA_PRIVACY_POLICY_URL = "https://github.com/SysAdminDoc/Aura/blob/main/docs/privacy/privacy-policy.md"
internal const val OPEN_METEO_LICENCE_URL = "https://open-meteo.com/en/licence"

internal enum class SettingsPermissionPrompt {
    DAILY_NOTIFICATION_REQUEST,
    DAILY_NOTIFICATION_RECOVERY,
    WEATHER_LOCATION_REQUEST,
    WEATHER_LOCATION_RECOVERY,
}

@Composable
internal fun SettingsPermissionPromptDialog(
    prompt: SettingsPermissionPrompt,
    onDismiss: () -> Unit,
    onLaunchNotificationPermission: () -> Unit,
    onLaunchLocationPermission: () -> Unit,
    onEnableDailyWallpaper: () -> Unit,
    onOpenNotificationSettings: () -> Boolean,
    onOpenAppSettings: () -> Boolean,
    onFeedback: (String) -> Unit,
) {
    val settingsUnavailable = stringResource(R.string.settings_feedback_settings_unavailable)
    val isRecovery = when (prompt) {
        SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY,
        SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY -> true
        SettingsPermissionPrompt.DAILY_NOTIFICATION_REQUEST,
        SettingsPermissionPrompt.WEATHER_LOCATION_REQUEST -> false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    onDismiss()
                    val settingsOpened = when (prompt) {
                        SettingsPermissionPrompt.DAILY_NOTIFICATION_REQUEST -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                onLaunchNotificationPermission()
                            } else {
                                onEnableDailyWallpaper()
                            }
                            true
                        }
                        SettingsPermissionPrompt.DAILY_NOTIFICATION_RECOVERY -> onOpenNotificationSettings()
                        SettingsPermissionPrompt.WEATHER_LOCATION_REQUEST -> {
                            onLaunchLocationPermission()
                            true
                        }
                        SettingsPermissionPrompt.WEATHER_LOCATION_RECOVERY -> onOpenAppSettings()
                    }
                    if (!settingsOpened) {
                        onFeedback(settingsUnavailable)
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
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.permission_not_now))
            }
        },
    )
}

@Composable
internal fun PermissionsSettingsSection(context: Context) {
    SettingsSection(
        sectionKey = SettingsSectionKeys.PERMISSIONS,
        title = stringResource(R.string.settings_permissions_section_title),
        description = stringResource(R.string.settings_permissions_section_description),
    ) {
        PermissionTransparencyRow(
            icon = Icons.Default.Wallpaper,
            permission = stringResource(R.string.settings_perm_wallpaper),
            scope = PermissionScope.LOCAL,
            description = stringResource(R.string.settings_perm_wallpaper_desc),
        )
        PermissionTransparencyRow(
            icon = Icons.Default.Language,
            permission = stringResource(R.string.settings_perm_internet),
            scope = PermissionScope.REMOTE,
            description = stringResource(R.string.settings_perm_internet_desc),
        )
        PermissionTransparencyRow(
            icon = Icons.Default.Notifications,
            permission = stringResource(R.string.settings_perm_notifications),
            scope = PermissionScope.LOCAL,
            description = stringResource(R.string.settings_perm_notifications_desc),
            granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            },
        )
        PermissionTransparencyRow(
            icon = Icons.Default.LocationOn,
            permission = stringResource(R.string.settings_perm_location),
            scope = PermissionScope.REMOTE,
            description = stringResource(R.string.settings_perm_location_desc),
            granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
        PermissionTransparencyRow(
            icon = Icons.Default.Contacts,
            permission = stringResource(R.string.settings_perm_contacts),
            scope = PermissionScope.LOCAL,
            description = stringResource(R.string.settings_perm_contacts_desc),
            granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED,
        )
        PermissionTransparencyRow(
            icon = Icons.Default.Notifications,
            permission = stringResource(R.string.settings_perm_dnd),
            scope = PermissionScope.LOCAL,
            description = stringResource(R.string.settings_perm_dnd_desc),
            granted = context.getSystemService(NotificationManager::class.java)
                ?.isNotificationPolicyAccessGranted == true,
        )
        PermissionTransparencyRow(
            icon = Icons.Default.Mic,
            permission = stringResource(R.string.settings_perm_microphone),
            scope = PermissionScope.LOCAL,
            description = stringResource(R.string.settings_perm_microphone_desc),
            granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
        PermissionTransparencyRow(
            icon = Icons.Default.Settings,
            permission = stringResource(R.string.settings_perm_modify_settings),
            scope = PermissionScope.LOCAL,
            description = stringResource(R.string.settings_perm_modify_settings_desc),
        )
        PermissionTransparencyRow(
            icon = Icons.Default.PlayCircle,
            permission = stringResource(R.string.settings_perm_foreground),
            scope = PermissionScope.LOCAL,
            description = stringResource(R.string.settings_perm_foreground_desc),
        )
    }
}

/** The line under "Check now": what the last check found, or what a tap will do. */
@Composable
internal fun updateCheckStatusText(checking: Boolean, result: UpdateCheckResult?): String = when {
    checking -> stringResource(R.string.settings_update_check_status_checking)
    result is UpdateCheckResult.Available ->
        stringResource(R.string.update_available_title, result.release.versionName)
    result is UpdateCheckResult.UpToDate ->
        stringResource(R.string.settings_update_check_status_current, result.latestVersionName)
    result is UpdateCheckResult.NoRelease -> stringResource(R.string.settings_update_check_status_no_release)
    result is UpdateCheckResult.Failed -> stringResource(
        when (result.reason) {
            UpdateCheckFailure.NETWORK -> R.string.settings_update_check_status_offline
            UpdateCheckFailure.RATE_LIMITED -> R.string.settings_update_check_status_rate_limited
            UpdateCheckFailure.UNREADABLE -> R.string.settings_update_check_status_unreadable
        },
    )
    result is UpdateCheckResult.Skipped && result.reason == UpdateCheckSkip.METERED ->
        stringResource(R.string.settings_update_check_status_metered)
    result is UpdateCheckResult.Skipped && result.reason == UpdateCheckSkip.DATA_SAVER ->
        stringResource(R.string.settings_update_check_status_data_saver)
    else -> stringResource(R.string.settings_update_check_status_idle)
}

@Composable
internal fun AboutSettingsSection(
    context: Context,
    viewModel: SettingsViewModel,
    onLicensesClick: () -> Unit,
) {
    val updateCheckEnabled by viewModel.updateCheckEnabled.collectAsStateWithLifecycle(initialValue = false)
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle(initialValue = null)
    val updateCheckRunning by viewModel.updateCheckRunning.collectAsStateWithLifecycle()
    val updateCheckResult by viewModel.updateCheckResult.collectAsStateWithLifecycle()
    var showUpdateNotes by rememberSaveable { mutableStateOf(false) }

    val shownUpdate = availableUpdate
    if (showUpdateNotes && shownUpdate != null) {
        UpdateNotesDialog(release = shownUpdate, onDismiss = { showUpdateNotes = false })
    }

    SettingsSection(
        sectionKey = SettingsSectionKeys.ABOUT,
        title = stringResource(R.string.settings_about_section_title),
        description = stringResource(R.string.settings_about_section_description),
    ) {
        SettingsItem(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_about_app_title),
            subtitle = stringResource(R.string.settings_about_app_subtitle, com.freevibe.BuildConfig.VERSION_NAME),
            onClick = {},
        )
        // A Play build has no update rows: Play keeps it current and the checker never runs there.
        if (viewModel.updateCheckOffered) {
            SettingsToggle(
                icon = Icons.Default.SystemUpdate,
                title = stringResource(R.string.settings_update_check_title),
                subtitle = stringResource(R.string.settings_update_check_subtitle),
                checked = updateCheckEnabled,
                onCheckedChange = viewModel::setUpdateCheckEnabled,
            )
        }
        if (shownUpdate != null) {
            SettingsItem(
                icon = Icons.Default.NewReleases,
                title = stringResource(R.string.update_available_title, shownUpdate.versionName),
                subtitle = stringResource(R.string.settings_update_available_subtitle),
                onClick = { showUpdateNotes = true },
            )
        }
        if (viewModel.updateCheckOffered && updateCheckEnabled) {
            SettingsItem(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.settings_update_check_now_title),
                subtitle = updateCheckStatusText(updateCheckRunning, updateCheckResult),
                onClick = viewModel::checkForUpdateNow,
                enabled = !updateCheckRunning,
            )
        }
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
}
