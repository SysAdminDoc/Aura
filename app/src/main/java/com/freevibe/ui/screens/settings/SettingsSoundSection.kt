package com.freevibe.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.ContentSource
import com.freevibe.service.normalizeYouTubePoTokenProviderUrl
import com.freevibe.service.YtDlpUpdateConsent
import com.freevibe.service.SoundShuffleStore

@Composable
internal fun SoundSettingsSection(
    viewModel: SettingsViewModel,
    autoPreview: Boolean,
    previewVolume: Float,
    ytRingtonesQuery: String,
    ytNotificationsQuery: String,
    ytAlarmsQuery: String,
    ytBlockedWords: String,
    youtubeProviderEnabled: Boolean,
    youtubePoTokenProviderUrl: String,
    soundProfilesEnabled: Boolean,
    soundProfilesJson: String,
    ytDlpUpdate: YtDlpUpdateUiState,
    onLicensesClick: () -> Unit,
    onFeedback: (String) -> Unit,
) {
    var showYtSoundEditor by remember { mutableStateOf(false) }
    var showYtBlockedEditor by remember { mutableStateOf(false) }
    var showPoTokenProviderEditor by remember { mutableStateOf(false) }
    var showYtDlpConsent by remember { mutableStateOf(false) }
    var showSoundShufflePools by remember { mutableStateOf(false) }
    val soundShuffleStore by viewModel.soundShuffleStore.collectAsStateWithLifecycle(
        initialValue = SoundShuffleStore(),
    )
    val youtubeProviderAvailable = isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)
    val ytDlpUpdateNotice = ytDlpUpdateFeedbackMessage(ytDlpUpdate)

    LaunchedEffect(ytDlpUpdate.completedStatus, ytDlpUpdate.error) {
        ytDlpUpdateNotice?.let {
            onFeedback(it)
            viewModel.clearYtDlpUpdateNotice()
        }
    }

    SettingsSection(
        sectionKey = SettingsSectionKeys.SOUNDS,
        title = stringResource(R.string.settings_sounds_section_title),
        description = stringResource(R.string.settings_sounds_section_description),
    ) {
        SettingsToggle(
            icon = Icons.Default.PlayCircle,
            title = stringResource(R.string.settings_sounds_auto_preview_title),
            subtitle = if (autoPreview) {
                stringResource(R.string.settings_sounds_auto_preview_on_subtitle)
            } else {
                stringResource(R.string.settings_sounds_auto_preview_off_subtitle)
            },
            checked = autoPreview,
            onCheckedChange = viewModel::setAutoPreview,
        )
        PreviewVolumeSlider(
            previewVolume = previewVolume,
            onPreviewVolumeChange = viewModel::setPreviewVolume,
        )
        if (youtubeProviderAvailable) {
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
                onCheckedChange = viewModel::setYoutubeProviderEnabled,
                searchAliases = setOf("youtube", "video", "extractor"),
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
                        showYtDlpConsent = true
                    }
                },
            )
            SettingsItem(
                icon = Icons.Default.SmartDisplay,
                title = stringResource(R.string.settings_youtube_pot_provider_title),
                subtitle = if (youtubePoTokenProviderUrl.isBlank()) {
                    stringResource(R.string.settings_youtube_pot_provider_off)
                } else {
                    stringResource(R.string.settings_youtube_pot_provider_on)
                },
                onClick = { showPoTokenProviderEditor = true },
            )
            SettingsItem(
                icon = Icons.Default.Block,
                title = stringResource(R.string.settings_sounds_blocked_words_title),
                subtitle = stringResource(
                    R.string.settings_sounds_blocked_words_subtitle,
                    ytBlockedWords.split(",").count { it.isNotBlank() },
                ),
                onClick = { showYtBlockedEditor = true },
            )
        }
        SettingsItem(
            icon = Icons.Default.LibraryMusic,
            title = stringResource(R.string.settings_sounds_sources_title),
            subtitle = stringResource(R.string.settings_sounds_sources_subtitle),
            onClick = onLicensesClick,
        )
        SettingsItem(
            icon = Icons.Default.Shuffle,
            title = stringResource(R.string.settings_sounds_shuffle_pools_title),
            subtitle = if (soundShuffleStore.pools.isEmpty()) {
                stringResource(R.string.settings_sounds_shuffle_pools_empty_subtitle)
            } else {
                pluralStringResource(
                    R.plurals.settings_sounds_shuffle_pools_count_subtitle,
                    soundShuffleStore.pools.size,
                    soundShuffleStore.pools.size,
                    soundShuffleStore.pools.count { it.enabled },
                )
            },
            onClick = { showSoundShufflePools = true },
            searchAliases = setOf("ringtone", "notification", "alarm", "schedule", "random"),
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
            onCheckedChange = viewModel::setSoundProfilesEnabled,
        )
    }

    if (showSoundShufflePools) {
        SoundShufflePoolsDialog(
            viewModel = viewModel,
            onDismiss = { showSoundShufflePools = false },
            onFeedback = onFeedback,
        )
    }

    if (youtubeProviderAvailable && showYtDlpConsent) {
        AlertDialog(
            onDismissRequest = { showYtDlpConsent = false },
            title = { Text(stringResource(R.string.settings_ytdlp_consent_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_ytdlp_consent_body))
                    Text(
                        stringResource(R.string.settings_ytdlp_consent_warning),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showYtDlpConsent = false
                        viewModel.updateYtDlp(YtDlpUpdateConsent.REPOSITORY_CHECKS_BYPASS_CONFIRMED)
                    },
                ) {
                    Text(stringResource(R.string.settings_ytdlp_consent_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showYtDlpConsent = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (youtubeProviderAvailable && showYtSoundEditor) {
        YouTubeSoundQueriesDialog(
            ytRingtonesQuery = ytRingtonesQuery,
            ytNotificationsQuery = ytNotificationsQuery,
            ytAlarmsQuery = ytAlarmsQuery,
            onSave = { ringtone, notification, alarm ->
                viewModel.setYtRingtonesQuery(ringtone.trim())
                viewModel.setYtNotificationsQuery(notification.trim())
                viewModel.setYtAlarmsQuery(alarm.trim())
                showYtSoundEditor = false
            },
            onDismiss = { showYtSoundEditor = false },
        )
    }
    if (youtubeProviderAvailable && showYtBlockedEditor) {
        YouTubeBlockedWordsDialog(
            ytBlockedWords = ytBlockedWords,
            onSave = {
                viewModel.setYtBlockedWords(it.trim())
                showYtBlockedEditor = false
            },
            onDismiss = { showYtBlockedEditor = false },
        )
    }
    if (youtubeProviderAvailable && showPoTokenProviderEditor) {
        YouTubePoTokenProviderDialog(
            currentUrl = youtubePoTokenProviderUrl,
            onSave = {
                viewModel.setYoutubePoTokenProviderUrl(it)
                showPoTokenProviderEditor = false
            },
            onDismiss = { showPoTokenProviderEditor = false },
        )
    }
}

@Composable
private fun YouTubePoTokenProviderDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(currentUrl) { mutableStateOf(currentUrl) }
    var invalid by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_youtube_pot_provider_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.settings_youtube_pot_provider_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.settings_youtube_pot_provider_url_label)) },
                    placeholder = { Text("https://pot.example.org") },
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.settings_youtube_pot_provider_invalid)) }
                    } else null,
                    isError = invalid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized = normalizeYouTubePoTokenProviderUrl(value)
                    if (normalized == null) {
                        invalid = true
                    } else {
                        onSave(normalized)
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun PreviewVolumeSlider(
    previewVolume: Float,
    onPreviewVolumeChange: (Float) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
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
                Icon(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
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
                    onValueChange = onPreviewVolumeChange,
                    valueRange = 0f..1f,
                    modifier = Modifier.height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
            Text(
                stringResource(R.string.settings_sounds_volume_percent, (previewVolume * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YouTubeSoundQueriesDialog(
    ytRingtonesQuery: String,
    ytNotificationsQuery: String,
    ytAlarmsQuery: String,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var ringQ by remember { mutableStateOf(ytRingtonesQuery) }
    var notifQ by remember { mutableStateOf(ytNotificationsQuery) }
    var alarmQ by remember { mutableStateOf(ytAlarmsQuery) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
            TextButton(onClick = { onSave(ringQ, notifQ, alarmQ) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun YouTubeBlockedWordsDialog(
    ytBlockedWords: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var blockedText by remember { mutableStateOf(ytBlockedWords) }
    AlertDialog(
        onDismissRequest = onDismiss,
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
                Text(
                    stringResource(
                        R.string.settings_picker_blocked_words_count,
                        blockedText.split(",").filter { it.isNotBlank() }.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(blockedText) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
