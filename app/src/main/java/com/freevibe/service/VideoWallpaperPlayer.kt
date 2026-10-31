package com.freevibe.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer

/**
 * Buffers stay small on purpose. MediaPlayer kept its samples in the media server,
 * but ExoPlayer keeps them in this app's own heap, next to the rest of Aura, and its
 * default of up to 128 MB is meant for streams. A wallpaper plays a local file, so a
 * few seconds of it is plenty.
 */
private const val MIN_BUFFER_MS = 1_000
private const val MAX_BUFFER_MS = 3_000
private const val BUFFER_FOR_PLAYBACK_MS = 250
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 500
internal const val VIDEO_WALLPAPER_TARGET_BUFFER_BYTES = 16 * 1024 * 1024

private const val ALLOWED_JOINING_TIME_MS = 2_000L
private const val MAX_DROPPED_FRAMES_TO_NOTIFY = 50

/** Builds the engine's player. Tests swap it so they can drive the engine without a decoder. */
internal var videoWallpaperPlayerFactory: (Context) -> ExoPlayer = ::buildVideoWallpaperPlayer

/**
 * A player with a video renderer and nothing else. With no audio renderer there is
 * no AudioTrack and no audio focus, so Android 17's background audio hardening has
 * nothing to act on, and a clip's sound can never play from the home screen.
 */
@OptIn(UnstableApi::class)
internal fun buildVideoWallpaperPlayer(context: Context): ExoPlayer {
    val videoOnly = RenderersFactory { handler, videoListener, _, _, _ ->
        arrayOf(
            MediaCodecVideoRenderer.Builder(context)
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                .setEnableDecoderFallback(true)
                .setAllowedJoiningTimeMs(ALLOWED_JOINING_TIME_MS)
                .setEventHandler(handler)
                .setEventListener(videoListener)
                .setMaxDroppedFramesToNotify(MAX_DROPPED_FRAMES_TO_NOTIFY)
                .build(),
        )
    }
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            MIN_BUFFER_MS,
            MAX_BUFFER_MS,
            BUFFER_FOR_PLAYBACK_MS,
            BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
        )
        .setTargetBufferBytes(VIDEO_WALLPAPER_TARGET_BUFFER_BYTES)
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()
    return ExoPlayer.Builder(context, videoOnly)
        .setLoadControl(loadControl)
        .build()
        .apply {
            // The engine sets the surface's frame rate from the FPS cap, so the player
            // must not set its own over it.
            videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        }
}

/**
 * Every frame the decoder has handed back, however it was used. A slow device can drop
 * most of a heavy clip's frames, and that still counts as a decoder that's alive.
 */
@OptIn(UnstableApi::class)
internal fun ExoPlayer.decodedFrameCount(): Int {
    val counters = videoDecoderCounters ?: return 0
    counters.ensureUpdated()
    return counters.renderedOutputBufferCount +
        counters.skippedOutputBufferCount +
        counters.droppedBufferCount
}

/** The clip's own frame rate, for the watchdog, or null when the file doesn't say. */
@OptIn(UnstableApi::class)
internal fun ExoPlayer.knownFrameRate(): Float? =
    videoFormat?.frameRate?.takeIf { it > 0f }
