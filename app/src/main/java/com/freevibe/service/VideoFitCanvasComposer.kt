@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.freevibe.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.FitCanvasSize
import com.freevibe.data.model.ResolvedFitCanvas
import com.freevibe.data.model.boundedFitCanvasSize
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class PreparedVideoFitCanvas(
    val file: File,
    val backgroundFile: File,
    val resolvedCanvas: ResolvedFitCanvas,
    val bakedIntoVideo: Boolean,
)

internal fun boundedVideoFrameSize(width: Int, height: Int, maxLongEdge: Int = 512): Pair<Int, Int> {
    require(maxLongEdge > 0)
    if (width <= 0 || height <= 0) return maxLongEdge to maxLongEdge
    val longest = max(width, height)
    if (longest <= maxLongEdge) return width to height
    val scale = maxLongEdge.toFloat() / longest
    return (width * scale).toInt().coerceAtLeast(1) to
        (height * scale).toInt().coerceAtLeast(1)
}

internal fun boundedBitmapSampleSize(width: Int, height: Int, maxLongEdge: Int = 512): Int {
    require(maxLongEdge > 0)
    if (width <= 0 || height <= 0) return 1
    var sampleSize = 1
    while (max(width, height) / sampleSize > maxLongEdge && sampleSize <= Int.MAX_VALUE / 2) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun boundedVideoCanvasSize(
    width: Int,
    height: Int,
    maxPixels: Long,
): FitCanvasSize {
    require(maxPixels >= 4L)
    val bounded = boundedFitCanvasSize(
        width.coerceAtLeast(2),
        height.coerceAtLeast(2),
        maxPixels,
    )
    var evenWidth = bounded.width.coerceAtLeast(2).let { it - (it % 2) }
    var evenHeight = bounded.height.coerceAtLeast(2).let { it - (it % 2) }
    if (evenWidth.toLong() * evenHeight > maxPixels) {
        if (evenWidth >= evenHeight) {
            evenWidth = (maxPixels / evenHeight).toInt().coerceAtLeast(2).let { it - (it % 2) }
        } else {
            evenHeight = (maxPixels / evenWidth).toInt().coerceAtLeast(2).let { it - (it % 2) }
        }
    }
    return FitCanvasSize(evenWidth, evenHeight)
}

internal fun decodeBoundedBitmapFile(path: String, maxLongEdge: Int = 512): Bitmap? {
    require(maxLongEdge > 0)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    return BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = boundedBitmapSampleSize(bounds.outWidth, bounds.outHeight, maxLongEdge)
        },
    )
}

internal fun decodeBoundedVideoFrame(path: String, maxLongEdge: Int = 512): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        val sourceWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val sourceHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        val (targetWidth, targetHeight) = boundedVideoFrameSize(sourceWidth, sourceHeight, maxLongEdge)
        listOf(0L, 100_000L, 500_000L, 1_000_000L).firstNotNullOfOrNull { timeUs ->
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(
                        timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetWidth,
                        targetHeight,
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { full ->
                            if (full.width <= targetWidth && full.height <= targetHeight) {
                                full
                            } else {
                                Bitmap.createScaledBitmap(full, targetWidth, targetHeight, true).also {
                                    if (it !== full) full.recycle()
                                }
                            }
                        }
                }
            }.getOrNull()
        }
    } catch (_: Throwable) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Where a video lands when fitted inside a canvas. Rounded inward, so the background's opening
 * never shows the black bars Media3 leaves around the frame.
 */
internal fun fittedVideoRect(
    videoWidth: Int,
    videoHeight: Int,
    canvasWidth: Int,
    canvasHeight: Int,
): VideoRect {
    require(videoWidth > 0 && videoHeight > 0 && canvasWidth > 0 && canvasHeight > 0)
    val scale = min(canvasWidth.toDouble() / videoWidth, canvasHeight.toDouble() / videoHeight)
    val fittedWidth = videoWidth * scale
    val fittedHeight = videoHeight * scale
    return VideoRect(
        left = ceil((canvasWidth - fittedWidth) / 2 - ROUNDING_SLACK).toInt().coerceAtLeast(0),
        top = ceil((canvasHeight - fittedHeight) / 2 - ROUNDING_SLACK).toInt().coerceAtLeast(0),
        right = floor((canvasWidth + fittedWidth) / 2 + ROUNDING_SLACK).toInt().coerceAtMost(canvasWidth),
        bottom = floor((canvasHeight + fittedHeight) / 2 + ROUNDING_SLACK).toInt().coerceAtMost(canvasHeight),
    )
}

/**
 * A device's encoder can settle on a nearby size or a smaller one, which still shows the whole
 * canvas. Only a different shape means the video is wrong.
 */
internal fun composedVideoMatchesCanvas(
    width: Int,
    height: Int,
    expectedWidth: Int,
    expectedHeight: Int,
): Boolean {
    if (width <= 0 || height <= 0 || expectedWidth <= 0 || expectedHeight <= 0) return false
    val ratio = (width.toDouble() / height) / (expectedWidth.toDouble() / expectedHeight)
    return abs(ratio - 1.0) <= COMPOSED_SHAPE_TOLERANCE
}

private const val ROUNDING_SLACK = 1e-6
private const val COMPOSED_SHAPE_TOLERANCE = 0.01

@Singleton
class VideoFitCanvasComposer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun prepare(file: File, style: FitCanvasStyle): Result<PreparedVideoFitCanvas> =
        prepareAtSize(
            file = file,
            style = style,
            targetWidth = context.resources.displayMetrics.widthPixels.coerceAtLeast(2),
            targetHeight = context.resources.displayMetrics.heightPixels.coerceAtLeast(2),
        )

    internal suspend fun prepareAtSize(
        file: File,
        style: FitCanvasStyle,
        targetWidth: Int,
        targetHeight: Int,
    ): Result<PreparedVideoFitCanvas> =
        withContext(Dispatchers.IO) {
            runCatching { prepareComposed(file, style, targetWidth, targetHeight) }
                .onFailure { it.rethrowIfCancelled() }
        }

    suspend fun writeCompatibleCopy(
        source: File,
        output: File,
        targetWidth: Int = context.resources.displayMetrics.widthPixels.coerceAtLeast(2),
        targetHeight: Int = context.resources.displayMetrics.heightPixels.coerceAtLeast(2),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (sourceWidth, sourceHeight) = readUprightVideoSize(source)
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                throw IOException("Video dimensions could not be read")
            }
            val maxLongEdge = (max(targetWidth, targetHeight).coerceAtLeast(2) * 2)
                .coerceAtMost(4_096)
            val (longEdgeWidth, longEdgeHeight) = boundedVideoFrameSize(
                sourceWidth,
                sourceHeight,
                maxLongEdge,
            )
            val bounded = boundedVideoCanvasSize(
                longEdgeWidth,
                longEdgeHeight,
                FitCanvasRenderer.MAX_OUTPUT_PIXELS,
            )
            val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(source)))
                .setRemoveAudio(true)
                .setEffects(
                    Effects(
                        emptyList(),
                        listOf(
                            Presentation.createForWidthAndHeight(
                                bounded.width,
                                bounded.height,
                                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP,
                            ),
                        ),
                    ),
                )
                .build()
            runMedia3VideoExport(
                builder = h264ExportBuilder(context),
                composition = sdrComposition(item),
                output = output,
                operation = "Video optimization",
            )
            val result = readMediaTechnicalMetadata(output, "video/mp4")
            if (result.width <= 0 || result.height <= 0) {
                throw IOException("Optimized video could not be validated")
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    private suspend fun prepareComposed(
        file: File,
        style: FitCanvasStyle,
        targetWidth: Int,
        targetHeight: Int,
    ): PreparedVideoFitCanvas {
        if (!file.exists() || !file.canRead()) throw IOException("Video wallpaper file is unavailable")
        // Versions that composed with FFmpeg unpacked its support libraries here.
        File(context.noBackupFilesDir, "aura-ffmpeg-runtime").deleteRecursively()
        val isGif = file.extension.equals("gif", ignoreCase = true)
        val target = boundedVideoCanvasSize(
            targetWidth.coerceAtLeast(2),
            targetHeight.coerceAtLeast(2),
            if (isGif) GIF_BACKGROUND_MAX_PIXELS else FitCanvasRenderer.MAX_OUTPUT_PIXELS,
        )
        val width = target.width
        val height = target.height
        val poster = extractRepresentativeFrame(file)
        val isHdr = isHdrVideo(file)
        val backgroundResult = try {
            FitCanvasRenderer.renderBackground(
                source = poster,
                targetWidth = width,
                targetHeight = height,
                style = style,
                isHdr = isHdr,
            )
        } finally {
            poster?.takeUnless { it.isRecycled }?.recycle()
        }

        val backgroundFile = File(context.filesDir, VIDEO_WALLPAPER_CANVAS_BACKGROUND_FILE)
        val backgroundTemp = File.createTempFile("aura-fit-canvas-", ".png", context.cacheDir)
        var overlay: Bitmap? = null
        try {
            backgroundTemp.outputStream().use { output ->
                if (!backgroundResult.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IOException("Could not write Fit Canvas background")
                }
            }
            if (isGif) {
                moveIntoPlace(backgroundTemp, backgroundFile)
                return PreparedVideoFitCanvas(
                    file = file,
                    backgroundFile = backgroundFile,
                    resolvedCanvas = backgroundResult.resolvedCanvas,
                    bakedIntoVideo = false,
                )
            }

            val (sourceWidth, sourceHeight) = readUprightVideoSize(file)
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                throw IOException("Video dimensions could not be read")
            }
            val composedOverlay = backgroundWithOpening(
                backgroundResult.bitmap,
                fittedVideoRect(sourceWidth, sourceHeight, width, height),
            )
            overlay = composedOverlay
            val output = File(context.filesDir, "live_wallpaper.fit.mp4")
            val tempOutput = File.createTempFile("aura-fit-canvas-", ".mp4", context.cacheDir)
            try {
                val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
                    .setRemoveAudio(true)
                    .setEffects(
                        Effects(
                            emptyList(),
                            listOf(
                                Presentation.createForWidthAndHeight(
                                    width,
                                    height,
                                    Presentation.LAYOUT_SCALE_TO_FIT,
                                ),
                                // The background goes over the frame with an opening where the
                                // video shows, so no frame gets blurred and nothing is drawn twice.
                                OverlayEffect(
                                    listOf(BitmapOverlay.createStaticBitmapOverlay(composedOverlay)),
                                ),
                            ),
                        ),
                    )
                    .build()
                runMedia3VideoExport(
                    builder = h264ExportBuilder(context),
                    composition = sdrComposition(item),
                    output = tempOutput,
                    operation = "Fit Canvas video",
                )
                requireValidComposedVideo(tempOutput, width, height)
                // Keep the active wallpaper intact until both staged assets have
                // been rendered and validated successfully.
                moveIntoPlace(backgroundTemp, backgroundFile)
                moveIntoPlace(tempOutput, output)
                return PreparedVideoFitCanvas(
                    file = output,
                    backgroundFile = backgroundFile,
                    resolvedCanvas = backgroundResult.resolvedCanvas,
                    bakedIntoVideo = true,
                )
            } finally {
                tempOutput.delete()
            }
        } finally {
            backgroundTemp.delete()
            backgroundResult.bitmap.recycle()
            overlay?.recycle()
        }
    }

    private fun backgroundWithOpening(background: Bitmap, opening: VideoRect): Bitmap {
        val overlay = background.copy(Bitmap.Config.ARGB_8888, true)
            ?: throw IOException("Could not prepare Fit Canvas background")
        Canvas(overlay).drawRect(
            opening.left.toFloat(),
            opening.top.toFloat(),
            opening.right.toFloat(),
            opening.bottom.toFloat(),
            Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) },
        )
        return overlay
    }

    private fun requireValidComposedVideo(file: File, expectedWidth: Int, expectedHeight: Int) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val probe = VideoWallpaperProbe(
                hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                    ?.equals("yes", ignoreCase = true) == true,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0,
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
            videoWallpaperProbeFailure(probe)?.let { throw IOException(it) }
            val (uprightWidth, uprightHeight) = uprightVideoSize(
                probe.width,
                probe.height,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0,
            )
            if (!composedVideoMatchesCanvas(uprightWidth, uprightHeight, expectedWidth, expectedHeight)) {
                throw IOException(
                    "Fit Canvas video dimensions ${uprightWidth}x$uprightHeight do not match " +
                        "${expectedWidth}x$expectedHeight",
                )
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Fit Canvas video could not be validated", error)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractRepresentativeFrame(file: File): Bitmap? {
        if (file.extension.equals("gif", ignoreCase = true)) {
            return decodeBoundedBitmapFile(file.absolutePath)
        }
        return decodeBoundedVideoFrame(file.absolutePath)
    }

    private fun isHdrVideo(file: File): Boolean {
        if (file.extension.equals("gif", ignoreCase = true)) return false
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val transfer = if (android.os.Build.VERSION.SDK_INT >= 30) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)?.toIntOrNull()
            } else {
                null
            }
            transfer == MediaFormat.COLOR_TRANSFER_HLG || transfer == MediaFormat.COLOR_TRANSFER_ST2084
        } catch (_: Throwable) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        private const val GIF_BACKGROUND_MAX_PIXELS = 250_000L
    }
}
