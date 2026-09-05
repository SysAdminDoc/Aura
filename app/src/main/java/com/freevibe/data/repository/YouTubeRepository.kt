package com.freevibe.data.repository

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.BuildConfig
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.SourceMetrics
import com.freevibe.service.YtDlpUpdateManager
import com.freevibe.service.YouTubeYtDlpRequestFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.net.URLEncoder
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

internal fun parseYtDlpSearchOutput(raw: String): List<Sound> = raw.lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .mapNotNull { line ->
        val fields = line.split('\t', limit = 4)
        if (fields.size < 4) return@mapNotNull null
        val videoId = fields[0].trim().takeIf { YOUTUBE_VIDEO_ID.matches(it) }
            ?: return@mapNotNull null
        val title = fields[1].trim().takeIf { it.isNotBlank() }
            ?: return@mapNotNull null
        val uploader = fields[2].normalizedYtDlpValue().ifBlank { "Unknown" }
        val duration = fields[3].normalizedYtDlpValue().toDoubleOrNull()
            ?.takeIf { it > 0.0 }
            ?: return@mapNotNull null
        Sound(
            id = "yt_$videoId",
            source = ContentSource.YOUTUBE,
            name = title,
            description = "by $uploader",
            previewUrl = "",
            downloadUrl = "",
            duration = duration,
            tags = emptyList(),
            license = "YouTube",
            uploaderName = uploader,
            sourcePageUrl = "https://www.youtube.com/watch?v=$videoId",
        )
    }
    .toList()

/**
 * Builds the default YouTube search handler without NewPipe's API 33-only
 * URLEncoder.encode(String, Charset) call. The String overload is available
 * across Aura's full supported Android range.
 */
internal fun createLegacyCompatibleYouTubeSearchHandler(query: String): SearchQueryHandler {
    val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
    val url = "https://www.youtube.com/results?search_query=$encodedQuery&sp=8AEB"
    return SearchQueryHandler(
        url,
        url,
        query,
        emptyList(),
        "",
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
    private val ytDlpRequestFactory: YouTubeYtDlpRequestFactory,
) {

    private val _extractionStatus = MutableStateFlow(YouTubeExtractionStatus())
    val extractionStatus: StateFlow<YouTubeExtractionStatus> = _extractionStatus.asStateFlow()

    // Cache resolved stream URLs with TTL to avoid stale URLs (YouTube tokens expire)
    private data class CachedStream(val url: String, val cachedAt: Long)
    private val streamCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, CachedStream>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedStream>?) = size > 50
        }
    )
    private val audioResolveLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
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

    /** Restore a still-fresh signed preview URL from the persistent sound feed cache. */
    fun rememberAudioPreviewUrl(videoId: String, url: String, cachedAtMs: Long) {
        if (videoId.isBlank() || url.isBlank()) return
        if (System.currentTimeMillis() - cachedAtMs > STREAM_TTL_MS) return
        streamCache[videoId] = CachedStream(url, cachedAtMs)
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
                val allBlocked = junkPatterns + blockedWords
                    .filter { it.isNotBlank() }
                    .map { Regex(Regex.escape(it.trim()), RegexOption.IGNORE_CASE) }
                val result = executeYouTubeFailover(
                    primaryEngine = YouTubeExtractionEngine.NEWPIPE,
                    fallbackEngine = YouTubeExtractionEngine.YT_DLP,
                    primary = {
                        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                        val extractor = service.getSearchExtractor(
                            createLegacyCompatibleYouTubeSearchHandler(query),
                        )
                        extractor.fetchPage()
                        val sounds = filterSearchSounds(
                            sounds = extractor.initialPage.items
                                .filterIsInstance<StreamInfoItem>()
                                .map { it.toSound() },
                            minDuration = minDuration,
                            maxDuration = maxDuration,
                            blocked = allBlocked,
                        )
                        SearchResult(
                            items = sounds,
                            totalCount = sounds.size,
                            currentPage = 1,
                            hasMore = extractor.initialPage.hasNextPage(),
                        )
                    },
                    fallback = {
                        val request = ytDlpRequestFactory.create("ytsearch30:$query")
                        request.addOption("--flat-playlist")
                        request.addOption("--playlist-end", "30")
                        request.addOption("--print", "%(id)s\t%(title)s\t%(channel)s\t%(duration)s")
                        val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                        val sounds = filterSearchSounds(
                            sounds = parseYtDlpSearchOutput(response.out.orEmpty()),
                            minDuration = minDuration,
                            maxDuration = maxDuration,
                            blocked = allBlocked,
                        )
                        SearchResult(
                            items = sounds,
                            totalCount = sounds.size,
                            currentPage = 1,
                            hasMore = false,
                        )
                    },
                )
                reportExtractionResult(result)
                result.value ?: throw YouTubeExtractionUnavailableException(
                    primaryError = result.primaryError,
                    fallbackError = result.fallbackError,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.e("YouTubeRepo", "Search failed for '$query': ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    /** Resolve a lightweight preview with NewPipe first; yt-dlp remains a fallback. */
    suspend fun getAudioPreviewUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(sourceName)
            return@withContext null
        }
        streamCache[videoId]?.let { cached ->
            if (System.currentTimeMillis() - cached.cachedAt <= STREAM_TTL_MS) return@withContext cached.url
            streamCache.remove(videoId)
        }
        val resolveLock = audioResolveLocks.computeIfAbsent(videoId) { Mutex() }
        try {
            resolveLock.withLock {
                streamCache[videoId]?.let { cached ->
                    if (System.currentTimeMillis() - cached.cachedAt <= STREAM_TTL_MS) return@withLock cached.url
                    streamCache.remove(videoId)
                }
                try {
                    sourceMetrics.measure(sourceName) {
                        val startedAt = android.os.SystemClock.elapsedRealtime()
                        val pageUrl = "https://www.youtube.com/watch?v=$videoId"
                        val result = executeYouTubeFailover(
                            primaryEngine = YouTubeExtractionEngine.NEWPIPE,
                            fallbackEngine = YouTubeExtractionEngine.YT_DLP,
                            primary = { resolveNewPipeAudio(pageUrl, preferLowBitrate = true) },
                            fallback = { resolveYtDlpAudio(pageUrl, format = "worstaudio") },
                            isUsable = String::isNotBlank,
                        )
                        reportExtractionResult(result)
                        val streamUrl = result.value ?: throw YouTubeExtractionUnavailableException(
                            primaryError = result.primaryError,
                            fallbackError = result.fallbackError,
                        )
                        if (BuildConfig.DEBUG) {
                            val resolver = result.engine?.displayName().orEmpty()
                            android.util.Log.d(
                                "YouTubeRepo",
                                "Audio preview resolved via $resolver in ${android.os.SystemClock.elapsedRealtime() - startedAt}ms for $videoId",
                            )
                        }
                        streamCache[videoId] = CachedStream(streamUrl, System.currentTimeMillis())
                        streamUrl
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ytDlpUpdateManager.recordExtractionFailure(e)
                    if (BuildConfig.DEBUG) android.util.Log.e("YouTubeRepo", "getAudioPreviewUrl failed for $videoId: ${e.javaClass.simpleName}: ${e.message}")
                    null
                }
            }
        } finally {
            audioResolveLocks.remove(videoId, resolveLock)
        }
    }

    /** High quality URL for download/apply — NewPipe first, yt-dlp fallback. */
    suspend fun getAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(sourceName)
            return@withContext null
        }
        try {
            sourceMetrics.measure(sourceName) {
                val pageUrl = "https://www.youtube.com/watch?v=$videoId"
                val result = executeYouTubeFailover(
                    primaryEngine = YouTubeExtractionEngine.NEWPIPE,
                    fallbackEngine = YouTubeExtractionEngine.YT_DLP,
                    primary = { resolveNewPipeAudio(pageUrl, preferLowBitrate = false) },
                    fallback = { resolveYtDlpAudio(pageUrl, format = "bestaudio") },
                    isUsable = String::isNotBlank,
                )
                reportExtractionResult(result)
                result.value ?: throw YouTubeExtractionUnavailableException(
                    primaryError = result.primaryError,
                    fallbackError = result.fallbackError,
                )
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
                val request = ytDlpRequestFactory.create(url)
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
                val request = ytDlpRequestFactory.create(url)
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

    private fun filterSearchSounds(
        sounds: List<Sound>,
        minDuration: Int,
        maxDuration: Int,
        blocked: List<Regex>,
    ): List<Sound> = sounds
        .filter { it.duration > 0.0 }
        .filter { it.duration in minDuration.toDouble()..maxDuration.toDouble() }
        .filter { sound -> blocked.none { it.containsMatchIn(sound.name) } }
        .filter { !it.name.contains("#") }

    private fun resolveNewPipeAudio(pageUrl: String, preferLowBitrate: Boolean): String? {
        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
        val streams = StreamInfo.getInfo(service, pageUrl).audioStreams
            .asSequence()
            .filter { it.isUrl && it.url?.isNotBlank() == true }
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val comparator = compareBy<org.schabi.newpipe.extractor.stream.AudioStream> {
            when (it.format) {
                MediaFormat.M4A -> 0
                MediaFormat.WEBMA, MediaFormat.WEBMA_OPUS -> 1
                else -> 2
            }
        }.thenBy { stream ->
            val bitrate = stream.averageBitrate.takeIf { it > 0 } ?: Int.MAX_VALUE
            if (preferLowBitrate) bitrate else -bitrate
        }
        return streams.sortedWith(comparator).firstOrNull()?.url
    }

    private suspend fun resolveYtDlpAudio(pageUrl: String, format: String): String? {
        val request = ytDlpRequestFactory.create(pageUrl)
        request.addOption("-f", format)
        request.addOption("--get-url")
        val response = com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
        return response.out?.trim()?.lineSequence()?.firstOrNull(String::isNotBlank)
    }

    private suspend fun <T> reportExtractionResult(result: YouTubeFailoverResult<T>) {
        _extractionStatus.value = result.toExtractionStatus()
        when {
            result.engine == YouTubeExtractionEngine.YT_DLP -> ytDlpUpdateManager.recordExtractionSuccess()
            result.value == null -> ytDlpUpdateManager.recordExtractionFailure(
                result.fallbackError ?: result.primaryError
                    ?: IllegalStateException("Both YouTube extractors returned no result"),
            )
        }
    }

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

private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

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
