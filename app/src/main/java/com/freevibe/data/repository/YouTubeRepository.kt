package com.freevibe.data.repository

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.BuildConfig
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.SourceMetrics
import com.freevibe.service.YtDlpUpdateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

internal data class VideoDisplayDimensions(
    val width: Int,
    val height: Int,
)

internal data class YouTubeVideoMetadata(
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val durationSeconds: Long = 0L,
    val mimeType: String = "",
    val videoCodec: String = "",
) {
    val hasDimensions: Boolean get() = width > 0 && height > 0
}

internal fun displayCorrectVideoDimensions(
    width: Int,
    height: Int,
    rotationDegrees: Int,
): VideoDisplayDimensions {
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    val shouldSwap = normalizedRotation == 90 || normalizedRotation == 270
    return if (shouldSwap && width > 0 && height > 0) {
        VideoDisplayDimensions(width = height, height = width)
    } else {
        VideoDisplayDimensions(width = width.coerceAtLeast(0), height = height.coerceAtLeast(0))
    }
}

internal fun parseYtDlpVideoMetadataOutput(raw: String): YouTubeVideoMetadata? {
    val fields = raw.lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        .toMap()

    val rawWidth = fields["width"].asPositiveInt()
    val rawHeight = fields["height"].asPositiveInt()
    val rotation = fields["rotation"].asIntOrZero()
    val display = displayCorrectVideoDimensions(
        width = rawWidth,
        height = rawHeight,
        rotationDegrees = rotation,
    )
    val duration = fields["duration"].asDurationSeconds()
    val ext = fields["ext"].normalizedYtDlpValue()
    val codec = fields["vcodec"].normalizedYtDlpValue()
    val mimeType = videoMimeTypeForExtension(ext)

    val hasMetadata = display.width > 0 ||
        display.height > 0 ||
        rotation != 0 ||
        duration > 0 ||
        mimeType.isNotBlank() ||
        codec.isNotBlank()
    if (!hasMetadata) return null

    return YouTubeVideoMetadata(
        width = display.width,
        height = display.height,
        rotationDegrees = rotation,
        durationSeconds = duration,
        mimeType = mimeType,
        videoCodec = codec,
    )
}

/**
 * YouTube search + stream extraction via NewPipe Extractor.
 * Scrapes YouTube directly — no API key, no Piped instances, no quotas.
 */
@Singleton
class YouTubeRepository @Inject constructor(
    private val sourceMetrics: SourceMetrics,
    private val prefs: PreferencesManager,
    private val ytDlpUpdateManager: YtDlpUpdateManager,
) {

    // Cache resolved stream URLs with TTL to avoid stale URLs (YouTube tokens expire)
    private data class CachedStream(val url: String, val cachedAt: Long)
    private val streamCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedStream>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStream>?) = size > 50
        }
    )
    private val STREAM_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours (YouTube tokens last ~6h)
    private val sourceName = "youtube"

    /** Check if a video's audio URL is cached and fresh */
    fun isCached(videoId: String): Boolean {
        val cached = streamCache[videoId] ?: return false
        if (System.currentTimeMillis() - cached.cachedAt > STREAM_TTL_MS) {
            streamCache.remove(videoId)
            return false
        }
        return true
    }

    init {
        try {
            NewPipe.init(DownloaderImpl.instance)
            if (BuildConfig.DEBUG) android.util.Log.d("YouTubeRepo", "NewPipe Extractor initialized")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("YouTubeRepo", "Failed to init NewPipe: ${e.message}", e)
        }
    }

    companion object {
        private val junkPatterns = listOf(
            "top \\d+", "\\d+ best", "compilation", "mix 20\\d\\d", "playlist",
            "ranked", "tier list", "reaction", "review", "tutorial", "how to",
            "part \\d+", "episode", "ep\\.", "podcast", "interview", "live stream",
            "timer", "countdown", "comparison", "quiz", "turn on notifications",
            "turn off notifications", "enable notifications", "notification spam",
            "hindi", "telugu", "pack",
        ).map { Regex(it, RegexOption.IGNORE_CASE) }
    }

    suspend fun searchSounds(
        query: String,
        maxDuration: Int = 240,
        minDuration: Int = 0,
        blockedWords: List<String> = emptyList(),
    ): SearchResult<Sound> = withContext(Dispatchers.IO) {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(sourceName)
            return@withContext emptySearchResult()
        }
        try {
            sourceMetrics.measure(sourceName) {
                if (BuildConfig.DEBUG) android.util.Log.d("YouTubeRepo", "Searching YouTube for: $query")
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                val searchExtractor = service.getSearchExtractor(query)
                searchExtractor.fetchPage()

                // Combine hardcoded + user-provided blocked words
                val allBlocked = junkPatterns + blockedWords
                    .filter { it.isNotBlank() }
                    .map { Regex(Regex.escape(it.trim()), RegexOption.IGNORE_CASE) }

                val sounds = searchExtractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.duration > 0 }
                    .filter { it.duration in minDuration.toLong()..maxDuration.toLong() }
                    .filter { item -> allBlocked.none { it.containsMatchIn(item.name) } }
                    .filter { !it.name.contains("#") }
                    .map { it.toSound() }

                SearchResult(
                    items = sounds,
                    totalCount = sounds.size,
                    currentPage = 1,
                    hasMore = searchExtractor.initialPage.hasNextPage(),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("YouTubeRepo", "Search failed for '$query': ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    /** Fast preview URL — checks cache first, then extracts via yt-dlp with worstaudio for speed */
    suspend fun getAudioPreviewUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(sourceName)
            return@withContext null
        }
        streamCache[videoId]?.let { cached ->
            if (System.currentTimeMillis() - cached.cachedAt <= STREAM_TTL_MS) return@withContext cached.url
            streamCache.remove(videoId)
        }
        try {
            sourceMetrics.measure(sourceName) {
                val url = "https://www.youtube.com/watch?v=$videoId"
                if (BuildConfig.DEBUG) android.util.Log.d("YouTubeRepo", "Extracting preview audio via yt-dlp for $videoId")
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                request.addOption("-f", "worstaudio") // Fastest to resolve + smallest to buffer
                request.addOption("--get-url")
                val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                val streamUrl = response.out?.trim()?.takeIf { it.isNotBlank() }
                if (BuildConfig.DEBUG) android.util.Log.d("YouTubeRepo", "yt-dlp preview result: ${streamUrl?.take(80) ?: "NULL"}")
                if (streamUrl == null) {
                    recordEmptyExtractorResult()
                    null
                } else {
                    streamCache[videoId] = CachedStream(streamUrl, System.currentTimeMillis())
                    ytDlpUpdateManager.recordExtractionSuccess()
                    streamUrl
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ytDlpUpdateManager.recordExtractionFailure(e)
            if (BuildConfig.DEBUG) android.util.Log.e("YouTubeRepo", "getAudioPreviewUrl failed for $videoId: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /** High quality URL for download/apply — uses bestaudio */
    suspend fun getAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(sourceName)
            return@withContext null
        }
        try {
            sourceMetrics.measure(sourceName) {
                val url = "https://www.youtube.com/watch?v=$videoId"
                if (BuildConfig.DEBUG) android.util.Log.d("YouTubeRepo", "Extracting best audio via yt-dlp for $videoId")
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                request.addOption("-f", "bestaudio")
                request.addOption("--get-url")
                val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                val streamUrl = response.out?.trim()
                if (streamUrl.isNullOrBlank()) {
                    recordEmptyExtractorResult()
                    null
                } else {
                    ytDlpUpdateManager.recordExtractionSuccess()
                    streamUrl
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ytDlpUpdateManager.recordExtractionFailure(e)
            if (BuildConfig.DEBUG) android.util.Log.e("YouTubeRepo", "getAudioStreamUrl failed for $videoId: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    suspend fun getVideoStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(sourceName)
            return@withContext null
        }
        try {
            sourceMetrics.measure(sourceName) {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                request.addOption("--get-url")
                val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                val streamUrl = response.out?.trim()?.lines()?.firstOrNull()
                if (streamUrl.isNullOrBlank()) {
                    recordEmptyExtractorResult()
                    null
                } else {
                    ytDlpUpdateManager.recordExtractionSuccess()
                    streamUrl
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ytDlpUpdateManager.recordExtractionFailure(e)
            null
        }
    }

    internal suspend fun getVideoMetadata(videoId: String): YouTubeVideoMetadata? = withContext(Dispatchers.IO) {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(sourceName)
            return@withContext null
        }
        try {
            sourceMetrics.measure(sourceName) {
                val url = "https://www.youtube.com/watch?v=$videoId"
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(url)
                request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                request.addOption("--skip-download")
                request.addOption("--no-playlist")
                request.addOption("--print", "width=%(width)s")
                request.addOption("--print", "height=%(height)s")
                request.addOption("--print", "rotation=%(rotation)s")
                request.addOption("--print", "duration=%(duration)s")
                request.addOption("--print", "ext=%(ext)s")
                request.addOption("--print", "vcodec=%(vcodec)s")
                val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                val metadata = parseYtDlpVideoMetadataOutput(response.out.orEmpty())
                if (metadata != null) {
                    ytDlpUpdateManager.recordExtractionSuccess()
                }
                metadata
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ytDlpUpdateManager.recordExtractionFailure(e)
            if (BuildConfig.DEBUG) android.util.Log.e("YouTubeRepo", "getVideoMetadata failed for $videoId: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun isProviderEnabled(): Boolean = prefs.youtubeProviderEnabled.first()

    private suspend fun recordEmptyExtractorResult() {
        ytDlpUpdateManager.recordExtractionFailure(
            IllegalStateException("yt-dlp returned an empty stream URL"),
        )
    }

    private fun emptySearchResult() = SearchResult<Sound>(
        items = emptyList(),
        totalCount = 0,
        currentPage = 1,
        hasMore = false,
    )

    private fun StreamInfoItem.toSound() = Sound(
        id = "yt_${url.substringAfter("v=").substringBefore("&")}",
        source = ContentSource.YOUTUBE,
        name = name,
        description = "by ${uploaderName ?: "Unknown"}",
        previewUrl = "",
        downloadUrl = "",
        duration = duration.toDouble(),
        tags = emptyList(),
        license = "YouTube",
        uploaderName = uploaderName ?: "Unknown",
        sourcePageUrl = url,
    )
}

private fun String?.normalizedYtDlpValue(): String =
    this
        ?.trim()
        ?.takeUnless { it.equals("NA", ignoreCase = true) || it.equals("none", ignoreCase = true) }
        .orEmpty()

private fun String?.asPositiveInt(): Int =
    normalizedYtDlpValue()
        .toDoubleOrNull()
        ?.toInt()
        ?.takeIf { it > 0 }
        ?: 0

private fun String?.asIntOrZero(): Int =
    normalizedYtDlpValue()
        .toDoubleOrNull()
        ?.toInt()
        ?: 0

private fun String?.asDurationSeconds(): Long =
    normalizedYtDlpValue()
        .toDoubleOrNull()
        ?.toLong()
        ?.takeIf { it > 0L }
        ?: 0L

private fun videoMimeTypeForExtension(ext: String): String = when (ext.lowercase(java.util.Locale.ROOT)) {
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    "3gp", "3gpp" -> "video/3gpp"
    "ogv", "ogg" -> "video/ogg"
    else -> ""
}

/**
 * Minimal Downloader implementation required by NewPipe Extractor.
 * Uses Java's built-in HTTP client.
 */
class DownloaderImpl private constructor() : org.schabi.newpipe.extractor.downloader.Downloader() {

    override fun execute(request: org.schabi.newpipe.extractor.downloader.Request): org.schabi.newpipe.extractor.downloader.Response {
        val url = java.net.URL(request.url())
        val conn = url.openConnection() as java.net.HttpURLConnection
        try {
            conn.requestMethod = request.httpMethod()
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000

            request.headers().forEach { (key, values) ->
                values.forEach { conn.addRequestProperty(key, it) }
            }

            request.dataToSend()?.let { data ->
                conn.doOutput = true
                conn.outputStream.use { it.write(data) }
            }

            val responseCode = conn.responseCode
            val responseHeaders = conn.headerFields
                .filterKeys { it != null }
                .mapValues { (_, v) -> v }
            val responseBody = try {
                (if (responseCode < 400) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (_: Exception) { "" }

            return org.schabi.newpipe.extractor.downloader.Response(
                responseCode,
                conn.responseMessage ?: "",
                responseHeaders,
                responseBody,
                request.url(),
            )
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        val instance: DownloaderImpl by lazy { DownloaderImpl() }
    }
}
