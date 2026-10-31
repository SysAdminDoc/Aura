@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.freevibe.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.effect.Crop
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.freevibe.BuildConfig
import com.freevibe.R
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val VIDEO_EXPORT_TIMEOUT_MS = 180_000L
private const val HLS_DOWNLOAD_TIMEOUT_MS = 300_000L
private const val OUTPUT_SIZE_CHECK_INTERVAL_MS = 250L
private const val HLS_MAX_VIDEO_EDGE = 1_920
private const val HLS_MAX_MS_BETWEEN_SAMPLES = 60_000L

internal class VideoExportTooLargeException(operation: String) :
    IOException("$operation exceeds the video wallpaper limit")

internal data class VideoCropRegion(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class NormalizedCrop(val left: Float, val right: Float, val bottom: Float, val top: Float)

internal data class VideoRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Media3 crops in normalized device coordinates: (0, 0) is the center and y runs up. */
internal fun normalizedCrop(
    region: VideoCropRegion,
    videoWidth: Int,
    videoHeight: Int,
): NormalizedCrop {
    require(videoWidth > 0 && videoHeight > 0)
    return NormalizedCrop(
        left = region.x * 2f / videoWidth - 1f,
        right = (region.x + region.width) * 2f / videoWidth - 1f,
        bottom = 1f - (region.y + region.height) * 2f / videoHeight,
        top = 1f - region.y * 2f / videoHeight,
    )
}

/** The part of the source to keep, or null when the whole video stays. */
internal fun videoClipWindow(loopStartMs: Long, loopEndMs: Long): Pair<Long, Long>? {
    val start = loopStartMs.coerceAtLeast(0L)
    return if (loopEndMs > start) start to loopEndMs else null
}

internal fun uprightVideoSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
    if (rotationDegrees % 180 != 0) height to width else width to height

internal fun readUprightVideoSize(file: File): Pair<Int, Int> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        uprightVideoSize(
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0,
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0,
            rotationDegrees = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0,
        )
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * One item with HDR tone mapped to SDR, because the bitmaps these exports draw are SDR and
 * the wallpaper pipeline expects an 8-bit H.264 result.
 */
internal fun sdrComposition(item: EditedMediaItem): Composition =
    Composition.Builder(EditedMediaItemSequence.Builder(item).build())
        .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
        .build()

/** A silent H.264 export that can fall back to a software decoder for a codec the hardware lacks. */
internal fun h264ExportBuilder(context: Context): Transformer.Builder =
    Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        // The wallpaper code reads the coded size, so a portrait video stays portrait
        // instead of being encoded sideways with a rotation flag.
        .setPortraitEncodingEnabled(true)
        .setAssetLoaderFactory(
            DefaultAssetLoaderFactory(
                context,
                DefaultDecoderFactory.Builder(context).setEnableDecoderFallback(true).build(),
                Clock.DEFAULT,
                null,
            ),
        )

/**
 * Runs one export on the main looper. Past the time limit or the size limit the export stops
 * and this throws an IOException, and a cancelled caller stops the export too. Partial output
 * stays where it is for the caller to delete.
 */
internal suspend fun runMedia3VideoExport(
    builder: Transformer.Builder,
    composition: Composition,
    output: File,
    operation: String,
    maxBytes: Long = MAX_VIDEO_WALLPAPER_BYTES,
    timeoutMs: Long = VIDEO_EXPORT_TIMEOUT_MS,
) {
    val finished = withTimeoutOrNull(timeoutMs) {
        coroutineScope {
            val sizeWatch = launch(Dispatchers.IO) {
                while (true) {
                    delay(OUTPUT_SIZE_CHECK_INTERVAL_MS)
                    if (output.length() > maxBytes) throw VideoExportTooLargeException(operation)
                }
            }
            awaitTransformer(builder, composition, output)
            sizeWatch.cancel()
        }
        true
    }
    if (finished == null) throw IOException("$operation timed out")
    withContext(Dispatchers.IO) {
        if (output.length() > maxBytes) throw VideoExportTooLargeException(operation)
    }
}

private suspend fun awaitTransformer(
    builder: Transformer.Builder,
    composition: Composition,
    output: File,
) = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine<Unit> { continuation ->
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                if (continuation.isActive) continuation.resumeWithException(exportException)
            }
        }
        val transformer = builder
            .setLooper(Looper.getMainLooper())
            .addListener(listener)
            .build()
        continuation.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post { transformer.cancel() }
        }
        try {
            transformer.start(composition, output.absolutePath)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }
}

/** Keeps one region and one stretch of time from a local video, without its audio. */
internal suspend fun exportCroppedVideo(
    context: Context,
    input: File,
    region: VideoCropRegion,
    videoWidth: Int,
    videoHeight: Int,
    loopStartMs: Long,
    loopEndMs: Long,
    output: File,
) {
    val crop = normalizedCrop(region, videoWidth, videoHeight)
    val mediaItem = MediaItem.Builder().setUri(Uri.fromFile(input))
    videoClipWindow(loopStartMs, loopEndMs)?.let { (startMs, endMs) ->
        mediaItem.setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(startMs)
                .setEndPositionMs(endMs)
                .build(),
        )
    }
    val item = EditedMediaItem.Builder(mediaItem.build())
        .setRemoveAudio(true)
        .setEffects(
            Effects(emptyList(), listOf(Crop(crop.left, crop.right, crop.bottom, crop.top))),
        )
        .build()
    runMedia3VideoExport(h264ExportBuilder(context), sdrComposition(item), output, "Video crop")
}

/**
 * Fetches the segments of an HLS playlist into one MP4, up to 1080p. The tracks are copied, not
 * re-encoded, so this costs network and no battery-heavy transcoding.
 */
internal suspend fun transmuxHlsToMp4(
    context: Context,
    url: String,
    output: File,
    maxBytes: Long = MAX_VIDEO_WALLPAPER_BYTES,
) {
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent("Aura/${BuildConfig.VERSION_NAME} (Android; Open Source)")
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(30_000)
        .setAllowCrossProtocolRedirects(false)
    val trackSelection = TrackSelector.Factory { selectorContext ->
        DefaultTrackSelector(
            selectorContext,
            DefaultTrackSelector.Parameters.Builder(selectorContext)
                .setForceHighestSupportedBitrate(true)
                .setMaxVideoSize(HLS_MAX_VIDEO_EDGE, HLS_MAX_VIDEO_EDGE)
                .build(),
        )
    }
    val builder = Transformer.Builder(context)
        // A phone on a slow network can sit between segments far longer than the default allows.
        .setMaxDelayBetweenMuxerSamplesMs(HLS_MAX_MS_BETWEEN_SAMPLES)
        .setAssetLoaderFactory(
            ExoPlayerAssetLoader.Factory(
                context,
                DefaultDecoderFactory.Builder(context).build(),
                Clock.DEFAULT,
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)),
                trackSelection,
                null,
                DefaultLoadControl(),
            ),
        )
    val item = EditedMediaItem.Builder(
        MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build(),
    ).build()
    runMedia3VideoExport(
        builder = builder,
        composition = Composition.Builder(EditedMediaItemSequence.Builder(item).build()).build(),
        output = output,
        operation = "HLS download",
        maxBytes = maxBytes,
        timeoutMs = HLS_DOWNLOAD_TIMEOUT_MS,
    )
    if (readUprightVideoSize(output).first <= 0) {
        throw IOException("HLS download did not produce a video")
    }
}

/** What a person sees for a failed HLS download. An export's own message is English and names internals. */
internal fun hlsDownloadFailureText(context: Context, error: Exception): String = context.getString(
    if (error is VideoExportTooLargeException) R.string.video_wp_err_too_large else R.string.video_wp_err_hls,
)
