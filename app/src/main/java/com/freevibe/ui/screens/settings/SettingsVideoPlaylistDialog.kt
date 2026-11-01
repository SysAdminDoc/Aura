package com.freevibe.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freevibe.R
import com.freevibe.service.VIDEO_PLAYLIST_ADVANCE_CHOICES
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_FIT
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_ZOOM
import com.freevibe.service.VideoPlaylist
import com.freevibe.service.VideoPlaylistClip

@Composable
internal fun VideoPlaylistDialog(
    state: VideoPlaylistUiState,
    onAddClips: (List<Uri>) -> Unit,
    onSetScale: (index: Int, scaleMode: String) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onSetShuffle: (Boolean) -> Unit,
    onSetAdvance: (seconds: Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val clips = state.playlist.clips
    val addClips = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        onAddClips(uris)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_video_playlist_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (clips.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_video_playlist_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                clips.forEachIndexed { index, clip ->
                    VideoPlaylistClipRow(
                        clip = clip,
                        canMoveUp = index > 0,
                        canMoveDown = index < clips.lastIndex,
                        enabled = !state.working,
                        onSetScale = { scaleMode -> onSetScale(index, scaleMode) },
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                        onRemove = { onRemove(index) },
                    )
                }
                TextButton(
                    onClick = { addClips.launch(arrayOf("video/*")) },
                    enabled = !state.working,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResource(R.string.settings_video_playlist_add),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (clips.size > 1) {
                    HorizontalDivider()
                    PlaylistOptionHeading(R.string.settings_video_playlist_order_title)
                    SettingsRadioOptionRow(
                        label = stringResource(R.string.settings_video_playlist_in_order),
                        selected = !state.playlist.shuffle,
                        onClick = { onSetShuffle(false) },
                    )
                    SettingsRadioOptionRow(
                        label = stringResource(R.string.settings_video_playlist_shuffle),
                        selected = state.playlist.shuffle,
                        onClick = { onSetShuffle(true) },
                    )
                    PlaylistOptionHeading(R.string.settings_video_playlist_advance_title)
                    VIDEO_PLAYLIST_ADVANCE_CHOICES.forEach { seconds ->
                        SettingsRadioOptionRow(
                            label = stringResource(videoPlaylistAdvanceLabel(seconds)),
                            selected = state.playlist.advanceSeconds == seconds,
                            onClick = { onSetAdvance(seconds) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply, enabled = clips.isNotEmpty() && !state.working) {
                Text(stringResource(R.string.settings_video_playlist_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

// The name gets its own line and the controls wrap, so nothing clips at large font sizes.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoPlaylistClipRow(
    clip: VideoPlaylistClip,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    enabled: Boolean,
    onSetScale: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = clip.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = clip.scaleMode == VIDEO_WALLPAPER_SCALE_MODE_FIT,
                onClick = { onSetScale(VIDEO_WALLPAPER_SCALE_MODE_FIT) },
                enabled = enabled,
                label = { Text(stringResource(R.string.video_wp_presentation_fit)) },
            )
            FilterChip(
                selected = clip.scaleMode == VIDEO_WALLPAPER_SCALE_MODE_ZOOM,
                onClick = { onSetScale(VIDEO_WALLPAPER_SCALE_MODE_ZOOM) },
                enabled = enabled,
                label = { Text(stringResource(R.string.video_wp_presentation_fill)) },
            )
            IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.settings_video_playlist_move_up, clip.name),
                )
            }
            IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.settings_video_playlist_move_down, clip.name),
                )
            }
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.settings_video_playlist_remove, clip.name),
                )
            }
        }
    }
}

@Composable
private fun PlaylistOptionHeading(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
internal fun videoPlaylistSubtitle(playlist: VideoPlaylist): String {
    if (playlist.clips.isEmpty()) return stringResource(R.string.settings_video_playlist_empty_subtitle)
    val count = playlist.clips.size
    return stringResource(
        R.string.settings_video_playlist_subtitle,
        pluralStringResource(R.plurals.settings_video_playlist_clips, count, count),
        stringResource(
            if (playlist.shuffle) {
                R.string.settings_video_playlist_shuffle
            } else {
                R.string.settings_video_playlist_in_order
            },
        ),
        stringResource(videoPlaylistAdvanceLabel(playlist.advanceSeconds)),
    )
}

@StringRes
private fun videoPlaylistAdvanceLabel(seconds: Int): Int = when (seconds) {
    30 -> R.string.settings_video_playlist_advance_30s
    60 -> R.string.settings_video_playlist_advance_60s
    300 -> R.string.settings_video_playlist_advance_300s
    else -> R.string.settings_video_playlist_advance_end
}
