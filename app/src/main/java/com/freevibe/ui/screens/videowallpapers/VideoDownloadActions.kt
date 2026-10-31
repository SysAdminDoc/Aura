package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.VideoWallpaperAction
import com.freevibe.data.model.canUseVideoAction
import com.freevibe.data.model.videoActionMessage
import com.freevibe.data.remote.RequestPurpose
import com.freevibe.data.remote.purpose
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.MAX_VIDEO_WALLPAPER_BYTES
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.YouTubeYtDlpRequestFactory
import com.freevibe.service.YtDlpUpdateManager
import com.freevibe.service.advertisedLengthExceeds
import com.freevibe.service.applyYtDlpDownloadBounds
import com.freevibe.service.copyStreamCapped
import com.freevibe.service.moveIntoPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

internal class VideoDownloadActions(
    private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val prefs: PreferencesManager,
    private val okHttpClient: OkHttpClient,
    private val videoWallpaperStorage: VideoWallpaperStorage,
    private val sourceMetrics: SourceMetrics,
    private val ytDlpUpdateManager: YtDlpUpdateManager,
    private val ytDlpRequestFactory: YouTubeYtDlpRequestFactory,
    private val av1CodecSupport: com.freevibe.service.Av1CodecSupport,
    private val state: MutableStateFlow<VideoWallpapersState>,
    private val streamUrls: MutableMap<String, String>,
    private val scope: CoroutineScope,
    private val rememberStreamUrl: (VideoWallpaperItem, String) -> Unit,
) {

    /** The work behind Apply once the ViewModel has checked it may start: save the original, prepare it, hand it to the picker. */
    suspend fun applyOriginal(
        item: VideoWallpaperItem,
        scaleMode: String,
        canvasStyle: FitCanvasStyle,
    ) {
        state.update { it.copy(isApplying = item.id) }
        try {
            // A Download or an earlier apply already saved the original, so reapply works offline.
            val original = (videoWallpaperStorage.findSavedDownloadedVideo(item.id, item.source) ?: downloadOriginal(item))
                .also(::requirePlayableCodec)
            val preparedFile = videoWallpaperStorage.preparePresentation(
                file = original,
                scaleMode = scaleMode,
                canvasStyle = canvasStyle,
            ).getOrElse { throw it }
            launchOrExportVideoWallpaper(
                context = context,
                file = preparedFile,
                scaleMode = scaleMode,
                canvasStyle = canvasStyle,
                // Storage persists the resolved fallback mode and generated
                // background. Do not replace it with the user's pre-fallback
                // request just before opening the system picker.
                selectionAlreadyPersisted = true,
            )
            state.update { it.copy(isApplying = null) }
        } catch (stop: VideoFetchStopped) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, stop.message, Toast.LENGTH_SHORT).show()
            }
            state.update { it.copy(isApplying = null) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Apply failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(
                        com.freevibe.R.string.video_wp_apply_failed,
                        e.message ?: context.getString(com.freevibe.R.string.settings_ytdlp_update_unknown_error),
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            state.update { it.copy(isApplying = null) }
        }
    }

    /**
     * Saves [item]'s original to Downloads. It's the managed copy Apply makes and reuses, kept under one
     * record per video, so downloading twice or applying afterwards never makes a second copy.
     */
    fun downloadVideo(item: VideoWallpaperItem) {
        if (item.id in state.value.downloadingIds) return
        if (state.value.isApplying == item.id) {
            // Apply is saving this same original, and it lands in Downloads when that finishes.
            Toast.makeText(context, context.getString(com.freevibe.R.string.video_wp_download_while_applying), Toast.LENGTH_SHORT).show()
            return
        }
        if (!item.canUseVideoAction(VideoWallpaperAction.DOWNLOAD)) {
            Toast.makeText(context, item.videoActionMessage(VideoWallpaperAction.DOWNLOAD), Toast.LENGTH_SHORT).show()
            return
        }
        state.update { it.copy(downloadingIds = it.downloadingIds + item.id) }
        scope.launch {
            val message = try {
                if (videoWallpaperStorage.findSavedDownloadedVideo(item.id, item.source) != null) {
                    context.getString(com.freevibe.R.string.video_wp_download_already_saved)
                } else {
                    downloadOriginal(item)
                    context.getString(com.freevibe.R.string.video_wp_downloaded)
                }
            } catch (stop: VideoFetchStopped) {
                stop.message
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Download failed", e)
                context.getString(
                    com.freevibe.R.string.video_wp_download_failed,
                    e.message ?: context.getString(com.freevibe.R.string.settings_ytdlp_update_unknown_error),
                )
            } finally {
                state.update { it.copy(downloadingIds = it.downloadingIds - item.id) }
            }
            withContext(Dispatchers.Main) { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
        }
    }

    /**
     * Fetches [item]'s original into managed storage and records it in Downloads under one ID per video.
     * Throws [VideoFetchStopped] when the user only needs to read why (YouTube off, no stream URL).
     */
    private suspend fun downloadOriginal(item: VideoWallpaperItem): java.io.File {
        if (item.source == "YouTube" && !prefs.youtubeProviderEnabled.first()) {
            sourceMetrics.recordDisabled("youtube")
            throw VideoFetchStopped(context.getString(com.freevibe.R.string.video_wp_youtube_disabled))
        }
        // Get stream URL (cached or resolve)
        val videoUrl = streamUrls[item.id] ?: run {
            if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Resolving stream URL for download: ${item.videoId}")
            val url = youtubeRepo.getVideoStreamUrl(item.videoId)
            if (url != null) rememberStreamUrl(item, url)
            url
        } ?: throw VideoFetchStopped(context.getString(com.freevibe.R.string.ytdlp_video_url_update_hint))

        if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Downloading video (source: ${item.source})...")
        val downloadedExtension = when {
            videoUrl.substringBefore('?').endsWith(".gif", ignoreCase = true) -> "gif"
            videoUrl.substringBefore('?').endsWith(".webm", ignoreCase = true) -> "webm"
            else -> "mp4"
        }
        val file = videoWallpaperStorage.prepareDownloadedVideo(
            extension = downloadedExtension,
            contentId = item.id,
            source = item.source,
            displayName = item.title,
            provenanceUrl = item.sourcePageUrl.ifBlank { videoUrl },
        ) { cacheFile ->
            if (isHlsMotionUrl(videoUrl)) {
                // A Reddit HLS URL is a playlist, not a video file. Media3 fetches its
                // segments into a bounded MP4; raw-copying the m3u8 bytes would create an
                // invalid wallpaper file.
                val hlsOutput = java.io.File(cacheFile.parentFile, "${cacheFile.name}.hls.mp4")
                try {
                    try {
                        com.freevibe.service.transmuxHlsToMp4(context, videoUrl, hlsOutput)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        throw java.io.IOException(com.freevibe.service.hlsDownloadFailureText(context, e), e)
                    }
                    // Move rather than copy: both files live in the same
                    // directory, so a copy would briefly need twice the
                    // video's size on a device already near its cap.
                    moveIntoPlace(hlsOutput, cacheFile)
                } finally {
                    hlsOutput.delete()
                }
            } else if (item.source == "YouTube" && item.videoId.isNotEmpty()) {
                // YouTube: use yt-dlp for download
                try {
                    val ytUrl = "https://www.youtube.com/watch?v=${item.videoId}"
                    val request = ytDlpRequestFactory.create(ytUrl)
                    val formatSelector = if (av1CodecSupport.hasHardwareAv1Decode) {
                        "bestvideo[ext=mp4][height<=1080]/best[ext=mp4]/best"
                    } else {
                        "bestvideo[ext=mp4][height<=1080][vcodec!~='av0?1']/best[ext=mp4][vcodec!~='av0?1']/best"
                    }
                    request.addOption("-f", formatSelector)
                    request.addOption("-o", cacheFile.absolutePath)
                    request.addOption("--force-overwrites")
                    applyYtDlpDownloadBounds(request)
                    com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                    ytDlpUpdateManager.recordExtractionSuccess()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ytDlpUpdateManager.recordExtractionFailure(e)
                    if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "yt-dlp download failed: ${e.message}, using stream URL")
                    okHttpClient.newCall(Request.Builder().purpose(RequestPurpose.DOWNLOAD).url(videoUrl).build()).execute().use { resp ->
                        if (!resp.isSuccessful) throw Exception(context.getString(com.freevibe.R.string.download_failed_http, resp.code))
                        val body = resp.body ?: throw Exception(context.getString(com.freevibe.R.string.video_wp_err_empty_body))
                        if (advertisedLengthExceeds(body.contentLength(), MAX_VIDEO_WALLPAPER_BYTES)) {
                            throw Exception(context.getString(com.freevibe.R.string.video_wp_err_too_large))
                        }
                        body.byteStream().use { input ->
                            cacheFile.parentFile?.mkdirs()
                            cacheFile.outputStream().use { output ->
                                copyStreamCapped(input, output, MAX_VIDEO_WALLPAPER_BYTES)
                            }
                        }
                    }
                }
            } else {
                // Pexels / direct URL: simple download
                okHttpClient.newCall(Request.Builder().purpose(RequestPurpose.DOWNLOAD).url(videoUrl).build()).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception(context.getString(com.freevibe.R.string.download_failed_http, resp.code))
                    val body = resp.body ?: throw Exception(context.getString(com.freevibe.R.string.video_wp_err_empty_body))
                    if (advertisedLengthExceeds(body.contentLength(), MAX_VIDEO_WALLPAPER_BYTES)) {
                        throw Exception(context.getString(com.freevibe.R.string.video_wp_err_too_large))
                    }
                    body.byteStream().use { input ->
                        cacheFile.parentFile?.mkdirs()
                        cacheFile.outputStream().use { output ->
                            copyStreamCapped(input, output, MAX_VIDEO_WALLPAPER_BYTES)
                        }
                    }
                }
            }
            if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Downloaded: ${cacheFile.length() / 1024}KB")
        }.getOrElse { throw it }
        return file
    }

    /**
     * Apply stops on an AV1 original the device can't decode in hardware. It runs on every original,
     * saved or fresh, since a Download keeps an AV1 file that is still worth having.
     */
    private fun requirePlayableCodec(file: java.io.File) {
        if (av1CodecSupport.hasHardwareAv1Decode) return
        val metadata = com.freevibe.service.readMediaTechnicalMetadata(file)
        if (metadata.codec?.contains("av01", ignoreCase = true) == true ||
            metadata.codec?.contains("av1", ignoreCase = true) == true
        ) {
            throw IllegalStateException(context.getString(com.freevibe.R.string.video_wp_err_av1))
        }
    }
}

/** A stop the user only needs to read, like a disabled source. Not logged as a failure. */
private class VideoFetchStopped(override val message: String) : Exception(message)
