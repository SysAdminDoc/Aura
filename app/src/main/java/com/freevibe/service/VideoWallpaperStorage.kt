package com.freevibe.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed interface VideoWallpaperSelectionResult {
    data object Preparing : VideoWallpaperSelectionResult
    data object Ready : VideoWallpaperSelectionResult
    data class Failure(val message: String) : VideoWallpaperSelectionResult
}

internal const val VIDEO_WALLPAPER_SCALE_MODE_ZOOM = "zoom"
internal const val VIDEO_WALLPAPER_SCALE_MODE_FIT = "fit"

internal fun videoWallpaperMimeTypes(): Array<String> = arrayOf("video/*", "image/gif")

internal const val MAX_VIDEO_WALLPAPER_BYTES = 256L * 1024L * 1024L
private const val MIN_VIDEO_WALLPAPER_DURATION_MS = 1_000L

internal data class VideoWallpaperProbe(
    val hasVideo: Boolean,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String?,
)

internal fun normalizeVideoWallpaperScaleMode(scaleMode: String?): String =
    when (scaleMode?.trim()?.lowercase(Locale.ROOT)) {
        VIDEO_WALLPAPER_SCALE_MODE_FIT -> VIDEO_WALLPAPER_SCALE_MODE_FIT
        else -> VIDEO_WALLPAPER_SCALE_MODE_ZOOM
    }

internal fun isGifVideoWallpaperSelection(
    mimeType: String?,
    fileName: String?,
): Boolean {
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    val normalizedName = fileName?.lowercase(Locale.ROOT).orEmpty()
    return normalizedMime == "image/gif" || normalizedName.endsWith(".gif")
}

internal fun resolveVideoWallpaperExtension(
    mimeType: String?,
    fileName: String?,
): String {
    val normalizedMime = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
    val normalizedName = fileName?.lowercase(Locale.ROOT).orEmpty()
    return when {
        isGifVideoWallpaperSelection(normalizedMime, normalizedName) -> "gif"
        normalizedMime == "video/webm" || normalizedName.endsWith(".webm") -> "webm"
        normalizedMime == "video/3gpp" || normalizedName.endsWith(".3gp") -> "3gp"
        normalizedMime == "video/ogg" || normalizedName.endsWith(".ogv") -> "ogv"
        normalizedMime == "video/quicktime" || normalizedName.endsWith(".mov") -> "mov"
        normalizedMime == "video/x-matroska" || normalizedName.endsWith(".mkv") -> "mkv"
        else -> "mp4"
    }
}

internal fun videoWallpaperProbeFailure(probe: VideoWallpaperProbe): String? =
    when {
        !probe.hasVideo -> "Selected file does not contain a video track"
        probe.durationMs < MIN_VIDEO_WALLPAPER_DURATION_MS -> "Selected video is too short"
        probe.width <= 0 || probe.height <= 0 -> "Selected video dimensions could not be read"
        else -> null
    }

internal fun hasValidGifHeader(header: ByteArray): Boolean =
    header.size >= 6 && (
        header.copyOfRange(0, 6).toString(Charsets.US_ASCII) == "GIF87a" ||
            header.copyOfRange(0, 6).toString(Charsets.US_ASCII) == "GIF89a"
        )

@Singleton
class VideoWallpaperStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun prepareFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            val extension = resolveVideoWallpaperExtension(mimeType, uri.lastPathSegment)
            val targetFile = managedVideoFile(extension)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

            try {
                val advertisedSize = resolver.advertisedSize(uri)
                if (advertisedLengthExceeds(advertisedSize, MAX_VIDEO_WALLPAPER_BYTES)) {
                    throw IOException("Selected file exceeds video wallpaper limit")
                }
                resolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        copyStreamCapped(input, output, MAX_VIDEO_WALLPAPER_BYTES)
                    }
                } ?: throw IOException("Could not open the selected file")

                validatePreparedMotionFile(tempFile, extension)
                commitPreparedVideo(tempFile, targetFile)
                persistSelectedVideoWallpaper(targetFile)
                targetFile
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    suspend fun prepareDownloadedVideo(
        extension: String = "mp4",
        writer: suspend (File) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val targetFile = managedVideoFile(extension)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

            try {
                writer(tempFile)
                validatePreparedMotionFile(tempFile, extension)
                commitPreparedVideo(tempFile, targetFile)
                persistSelectedVideoWallpaper(targetFile)
                targetFile
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    private fun validatePreparedMotionFile(file: File, extension: String) {
        if (!file.exists() || file.length() < 1024) {
            throw IOException("Selected file is empty or invalid")
        }
        if (file.length() > MAX_VIDEO_WALLPAPER_BYTES) {
            throw IOException("Selected file exceeds video wallpaper limit")
        }
        if (extension.equals("gif", ignoreCase = true)) {
            validateGifHeader(file)
            return
        }
        val probe = probeVideoFile(file)
        videoWallpaperProbeFailure(probe)?.let { throw IOException(it) }
    }

    private fun validateGifHeader(file: File) {
        val header = ByteArray(6)
        val read = file.inputStream().use { it.read(header) }
        if (read != header.size || !hasValidGifHeader(header)) {
            throw IOException("Selected GIF is invalid")
        }
    }

    private fun probeVideoFile(file: File): VideoWallpaperProbe {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                ?.equals("yes", ignoreCase = true) == true
            VideoWallpaperProbe(
                hasVideo = hasVideo,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0,
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0,
                mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            )
        } catch (e: Exception) {
            throw IOException("Selected video could not be decoded", e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun commitPreparedVideo(tempFile: File, targetFile: File) {
        if (!tempFile.renameTo(targetFile)) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        pruneOlderManagedCopies(targetFile)
    }

    private fun pruneOlderManagedCopies(activeFile: File) {
        context.filesDir.listFiles()
            ?.filter { candidate ->
                candidate.isFile &&
                    candidate.name.startsWith("live_wallpaper.") &&
                    candidate.absolutePath != activeFile.absolutePath
            }
            ?.forEach { stale ->
                try {
                    stale.delete()
                } catch (_: Exception) {
                }
            }
    }

    private fun managedVideoFile(extension: String): File =
        File(context.filesDir, "live_wallpaper.$extension")

    private fun android.content.ContentResolver.advertisedSize(uri: Uri): Long =
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index) else -1L
        } ?: -1L

    private fun persistSelectedVideoWallpaper(file: File) {
        context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE)
            .edit()
            .putString("video_path", file.absolutePath)
            .putString("scale_mode", VIDEO_WALLPAPER_SCALE_MODE_ZOOM)
            .apply()
    }
}
