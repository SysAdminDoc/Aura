package com.freevibe.ui.screens.settings

import android.Manifest
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.freevibe.R

@Composable
internal fun BackupSettingsSection(
    context: Context,
    viewModel: SettingsViewModel,
    autoBackupEnabled: Boolean,
    autoBackupFolderUri: String,
    autoBackupFolderPermissionActive: Boolean,
    autoBackupIntervalHours: Long,
    autoBackupKeepCount: Int,
    themePackTransfer: ThemePackTransferState,
    onChooseAutoBackupFolder: (Boolean) -> Unit,
    onFeedback: (String) -> Unit,
) {
    var showAutoBackupIntervalPicker by remember { mutableStateOf(false) }
    var showAutoBackupKeepPicker by remember { mutableStateOf(false) }
    val themePackExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        uri?.let(viewModel::exportThemePack)
    }
    val themePackImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let(viewModel::importThemePack)
    }
    val libraryExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        uri?.let(viewModel::exportLibrary)
    }
    val libraryImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let(viewModel::importLibrary)
    }
    val contactsPermissionRequest = themePackTransfer.contactsPermissionRequest
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _: Boolean ->
        // Allowed or not, the import goes ahead. Without access the report names the contacts left out.
        contactsPermissionRequest?.let { viewModel.importLibraryAfterContactsPrompt(it.backupUri) }
    }
    var themePackInstructions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(themePackTransfer) {
        themePackTransfer.message?.let { onFeedback(it) }
        themePackTransfer.error?.let { onFeedback(it) }
        if (themePackTransfer.instructions.isNotEmpty()) {
            themePackInstructions = themePackTransfer.instructions
        }
        if (
            themePackTransfer.message != null ||
            themePackTransfer.error != null ||
            themePackTransfer.instructions.isNotEmpty()
        ) {
            viewModel.clearThemePackTransferNotice()
        }
    }

    SettingsSection(
        sectionKey = SettingsSectionKeys.BACKUP,
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
                    onChooseAutoBackupFolder(true)
                    onFeedback(context.getString(R.string.settings_feedback_backup_choose_folder))
                } else {
                    viewModel.setAutoBackupEnabled(true)
                }
            },
            searchAliases = setOf("backup", "restore", "export", "import"),
        )
        SettingsItem(
            icon = Icons.Default.FolderOpen,
            title = stringResource(R.string.settings_backup_folder_title),
            subtitle = autoBackupFolderSubtitle(
                folderUri = autoBackupFolderUri,
                folderPermissionActive = autoBackupFolderPermissionActive,
            ),
            onClick = { onChooseAutoBackupFolder(false) },
        )
        if (autoBackupFolderUri.isNotBlank()) {
            SettingsItem(
                icon = Icons.Default.DeleteOutline,
                title = stringResource(R.string.settings_backup_clear_title),
                subtitle = stringResource(R.string.settings_backup_clear_subtitle),
                onClick = viewModel::clearAutoBackupFolderUri,
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
        SettingsItem(
            icon = Icons.Default.FolderOpen,
            title = stringResource(R.string.settings_theme_pack_export_title),
            subtitle = if (themePackTransfer.inProgress) {
                stringResource(R.string.settings_theme_pack_working_subtitle)
            } else {
                stringResource(R.string.settings_theme_pack_export_subtitle)
            },
            onClick = {
                themePackExportLauncher.launch(context.getString(R.string.settings_theme_pack_default_filename))
            },
            enabled = !themePackTransfer.inProgress,
        )
        SettingsItem(
            icon = Icons.Default.History,
            title = stringResource(R.string.settings_theme_pack_import_title),
            subtitle = if (themePackTransfer.inProgress) {
                stringResource(R.string.settings_theme_pack_working_subtitle)
            } else {
                stringResource(R.string.settings_theme_pack_import_subtitle)
            },
            onClick = {
                themePackImportLauncher.launch(
                    arrayOf(
                        "application/zip",
                        "application/json",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
            enabled = !themePackTransfer.inProgress,
        )
        SettingsItem(
            icon = Icons.Default.FolderOpen,
            title = stringResource(R.string.settings_library_export_title),
            subtitle = if (themePackTransfer.inProgress) {
                stringResource(R.string.settings_theme_pack_working_subtitle)
            } else {
                stringResource(R.string.settings_library_export_subtitle)
            },
            onClick = {
                libraryExportLauncher.launch(context.getString(R.string.settings_library_default_filename))
            },
            enabled = !themePackTransfer.inProgress,
        )
        SettingsItem(
            icon = Icons.Default.History,
            title = stringResource(R.string.settings_library_import_title),
            subtitle = if (themePackTransfer.inProgress) {
                stringResource(R.string.settings_theme_pack_working_subtitle)
            } else {
                stringResource(R.string.settings_library_import_subtitle)
            },
            onClick = {
                libraryImportLauncher.launch(
                    arrayOf("application/json", "application/octet-stream", "*/*"),
                )
            },
            enabled = !themePackTransfer.inProgress,
        )
    }

    if (themePackInstructions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { themePackInstructions = emptyList() },
            title = { Text(stringResource(R.string.settings_theme_pack_instructions_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    themePackInstructions.forEach { instruction ->
                        Text(stringResource(R.string.settings_theme_pack_instruction_row, instruction))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { themePackInstructions = emptyList() }) {
                    Text(stringResource(R.string.common_done))
                }
            },
        )
    }

    if (contactsPermissionRequest != null) {
        // Leaving the dialog any other way than "Continue" restores the rest of the backup and
        // reports the contact ringtones as waiting on this permission.
        val importWithoutContacts = { viewModel.importLibraryAfterContactsPrompt(contactsPermissionRequest.backupUri) }
        AlertDialog(
            onDismissRequest = importWithoutContacts,
            title = { Text(stringResource(R.string.settings_library_contacts_permission_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.settings_library_contacts_permission_body,
                        contactsPermissionRequest.contactCount,
                        contactsPermissionRequest.contactCount,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { contactsPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS) }) {
                    Text(stringResource(R.string.permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = importWithoutContacts) {
                    Text(stringResource(R.string.permission_not_now))
                }
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
}
