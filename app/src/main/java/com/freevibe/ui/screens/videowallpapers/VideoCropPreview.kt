package com.freevibe.ui.screens.videowallpapers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.freevibe.R
import kotlinx.coroutines.delay

/** What the crop surface shows, so a video that's still loading doesn't look like a broken one. */
internal sealed interface VideoCropPreview {
    /** The player is preparing and hasn't drawn a frame yet. */
    data object Preparing : VideoCropPreview

    /** A frame is on the surface. */
    data object Ready : VideoCropPreview

    data class Failed(val reason: VideoCropFailure) : VideoCropPreview
}

internal enum class VideoCropFailure {
    /** No decoder for the format, or a damaged file. Trying again won't help. */
    UNSUPPORTED,

    /** Loading stopped (network, server or file access). Trying again can work. */
    LOAD,
}

/** Parsing (3xxx), decoding (4xxx) and frame processing (7xxx) errors are the file's; the rest can pass. */
internal fun videoCropFailureFor(errorCode: Int): VideoCropFailure = when (errorCode / 1_000) {
    3, 4, 7 -> VideoCropFailure.UNSUPPORTED
    else -> VideoCropFailure.LOAD
}

/** After this long without a first frame, the loading state offers a way out. */
internal const val VIDEO_CROP_SLOW_LOAD_MS = 12_000L

/** Turns player callbacks into a [VideoCropPreview] the crop screen reads. */
internal class VideoCropPreviewTracker : Player.Listener {
    var state: VideoCropPreview by mutableStateOf(VideoCropPreview.Preparing)
        private set

    override fun onRenderedFirstFrame() {
        if (state !is VideoCropPreview.Failed) state = VideoCropPreview.Ready
    }

    override fun onPlayerError(error: PlaybackException) {
        state = VideoCropPreview.Failed(videoCropFailureFor(error.errorCode))
    }

    // ExoPlayer skips a video track it can't decode and plays the audio with no error, which
    // would leave the surface black forever, so a missing or unplayable video track fails here.
    override fun onTracksChanged(tracks: Tracks) {
        if (tracks.isEmpty || state != VideoCropPreview.Preparing) return
        val playable = tracks.containsType(C.TRACK_TYPE_VIDEO) &&
            tracks.isTypeSupported(C.TRACK_TYPE_VIDEO, /* allowExceedsCapabilities = */ true)
        if (!playable) state = VideoCropPreview.Failed(VideoCropFailure.UNSUPPORTED)
    }

    /** Call before preparing the player again. */
    fun retry() {
        state = VideoCropPreview.Preparing
    }
}

/** Covers the crop surface until the first frame, or explains why there won't be one. */
@Composable
internal fun VideoCropPreviewOverlay(
    preview: VideoCropPreview,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (preview == VideoCropPreview.Ready) return
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (preview) {
            VideoCropPreview.Ready -> Unit
            VideoCropPreview.Preparing -> {
                var slow by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(VIDEO_CROP_SLOW_LOAD_MS)
                    slow = true
                }
                CircularProgressIndicator(color = Color.White)
                Text(
                    stringResource(if (slow) R.string.video_crop_loading_slow else R.string.video_crop_loading),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (slow) {
                    TextButton(onClick = onChooseAnother) {
                        Text(stringResource(R.string.video_crop_choose_another), color = Color.White)
                    }
                }
            }
            is VideoCropPreview.Failed -> {
                val unsupported = preview.reason == VideoCropFailure.UNSUPPORTED
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                Text(
                    stringResource(if (unsupported) R.string.video_crop_unsupported_title else R.string.video_crop_load_failed_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(if (unsupported) R.string.video_crop_unsupported_body else R.string.video_crop_load_failed_body),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                if (!unsupported) {
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.common_retry), color = Color.White)
                    }
                }
                Button(onClick = onChooseAnother) {
                    Text(stringResource(R.string.video_crop_choose_another))
                }
            }
        }
    }
}
