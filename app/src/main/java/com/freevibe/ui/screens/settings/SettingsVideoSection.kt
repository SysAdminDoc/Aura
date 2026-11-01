package com.freevibe.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.freevibe.R
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.service.VIDEO_PLAYBACK_SPEEDS
import com.freevibe.service.VideoWallpaperService
import com.freevibe.service.sanitizeVideoPlaybackSpeed
import com.freevibe.service.videoBatteryImpactSummary
import com.freevibe.ui.LiveWallpaperSource
import com.freevibe.ui.launchLiveWallpaperWithFeedback
import java.math.BigDecimal

@Composable
internal fun VideoSettingsSection(
    viewModel: SettingsViewModel,
    videoFpsLimit: Int,
    videoPlaybackSpeed: Float,
    videoFpsOverlayEnabled: Boolean,
    videoAutoBatterySaver: Boolean,
    videoBatteryDashboard: VideoBatteryDashboardState,
    onFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    var showFpsPicker by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    val playlistState by viewModel.videoPlaylistState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.refreshVideoPlaylist()
        viewModel.videoPlaylistMessages.collect { message -> onFeedback(message) }
    }

    SettingsSection(
        sectionKey = SettingsSectionKeys.VIDEO,
        title = stringResource(R.string.settings_video_section_title),
        description = stringResource(R.string.settings_video_section_description),
    ) {
        VideoBatteryDashboardCard(
            state = videoBatteryDashboard,
            modifier = Modifier.fillMaxWidth(),
        )
        RedditFeedPresetManager(RedditFeedMediaType.VIDEO)
        SettingsToggle(
            icon = Icons.Default.BatteryChargingFull,
            title = stringResource(R.string.settings_video_battery_saver_title),
            subtitle = if (videoAutoBatterySaver) {
                stringResource(R.string.settings_video_battery_saver_on_subtitle)
            } else {
                stringResource(R.string.settings_video_battery_saver_off_subtitle)
            },
            checked = videoAutoBatterySaver,
            onCheckedChange = viewModel::setVideoAutoBatterySaver,
            searchAliases = setOf("battery saver", "battery", "power"),
        )
        SettingsToggle(
            icon = Icons.Default.Speed,
            title = stringResource(R.string.settings_video_fps_overlay_title),
            subtitle = if (videoFpsOverlayEnabled) {
                stringResource(R.string.settings_video_fps_overlay_on_subtitle)
            } else {
                stringResource(R.string.settings_video_fps_overlay_off_subtitle)
            },
            checked = videoFpsOverlayEnabled,
            onCheckedChange = viewModel::setVideoFpsOverlayEnabled,
        )
        SettingsItem(
            icon = Icons.Default.Speed,
            title = stringResource(R.string.settings_video_fps_limit_title),
            subtitle = videoBatteryImpactSummary(
                requestedFps = videoBatteryDashboard.requestedFps,
                effectiveFps = videoBatteryDashboard.effectiveFps,
                fpsOverlayEnabled = videoBatteryDashboard.fpsOverlayEnabled,
                lowBatterySaverActive = videoBatteryDashboard.lowBatterySaverActive,
                motionPausedForPowerSave = videoBatteryDashboard.motionPausedForPowerSave,
            ).resolve(LocalContext.current),
            onClick = { showFpsPicker = true },
        )
        SettingsItem(
            icon = Icons.Default.SlowMotionVideo,
            title = stringResource(R.string.settings_video_speed_title),
            subtitle = stringResource(
                R.string.settings_video_speed_subtitle,
                videoPlaybackSpeedLabel(sanitizeVideoPlaybackSpeed(videoPlaybackSpeed)),
            ),
            onClick = { showSpeedPicker = true },
        )
        SettingsItem(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            title = stringResource(R.string.settings_video_playlist_title),
            subtitle = videoPlaylistSubtitle(playlistState.playlist),
            onClick = { showPlaylist = true },
            searchAliases = setOf("playlist", "clips"),
        )
    }

    if (showPlaylist) {
        VideoPlaylistDialog(
            state = playlistState,
            onAddClips = viewModel::addVideoPlaylistClips,
            onSetScale = viewModel::setVideoPlaylistClipScale,
            onMove = viewModel::moveVideoPlaylistClip,
            onRemove = viewModel::removeVideoPlaylistClip,
            onSetShuffle = viewModel::setVideoPlaylistShuffle,
            onSetAdvance = viewModel::setVideoPlaylistAdvance,
            onApply = {
                showPlaylist = false
                viewModel.activateVideoPlaylist {
                    onFeedback(
                        launchLiveWallpaperWithFeedback(
                            context = context,
                            serviceClass = VideoWallpaperService::class.java,
                            tag = "SettingsVideoPlaylist",
                            source = LiveWallpaperSource.VIDEO,
                            directRes = R.string.settings_feedback_video_direct,
                            chooserRes = R.string.settings_feedback_video_chooser,
                        ),
                    )
                }
            },
            onDismiss = { showPlaylist = false },
        )
    }

    if (showSpeedPicker) {
        val current = sanitizeVideoPlaybackSpeed(videoPlaybackSpeed)
        AlertDialog(
            onDismissRequest = { showSpeedPicker = false },
            title = { Text(stringResource(R.string.settings_video_speed_title)) },
            text = {
                Column {
                    VIDEO_PLAYBACK_SPEEDS.forEach { speed ->
                        SettingsRadioOptionRow(
                            label = videoPlaybackSpeedLabel(speed),
                            selected = current == speed,
                            onClick = {
                                viewModel.setVideoPlaybackSpeed(speed)
                                showSpeedPicker = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    if (showFpsPicker) {
        AlertDialog(
            onDismissRequest = { showFpsPicker = false },
            title = { Text(stringResource(R.string.settings_video_fps_dialog_title)) },
            text = {
                Column {
                    listOf(
                        15 to stringResource(R.string.settings_video_fps_15),
                        30 to stringResource(R.string.settings_video_fps_30),
                        60 to stringResource(R.string.settings_video_fps_60),
                    ).forEach { (fps, label) ->
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
            confirmButton = {
                TextButton(onClick = { showFpsPicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun videoPlaybackSpeedLabel(speed: Float): String =
    if (speed == 1f) {
        stringResource(R.string.settings_video_speed_normal)
    } else {
        stringResource(R.string.settings_video_speed_value, BigDecimal(speed.toString()).stripTrailingZeros().toPlainString())
    }
