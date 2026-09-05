package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.PixabayVideoCacheStore
import com.freevibe.data.local.PreferencesManager
import com.freevibe.util.rethrowIfCancelled
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pixabay.PixabayVideo
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.repository.YouTubeVideoMetadata
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.createLegacyCompatibleYouTubeSearchHandler
import com.freevibe.data.repository.parseRedditRssPage
import com.freevibe.data.repository.pixabayRateLimitBackoffMillis
import com.freevibe.service.MAX_VIDEO_WALLPAPER_BYTES
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_ZOOM
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.VideoPreviewCache
import com.freevibe.service.shouldPrebufferVideoPreview
import com.freevibe.service.YtDlpUpdateManager
import com.freevibe.service.YouTubeYtDlpRequestFactory
import com.freevibe.service.advertisedLengthExceeds
import com.freevibe.service.applyYtDlpDownloadBounds
import com.freevibe.service.moveIntoPlace
import com.freevibe.service.copyStreamCapped
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Properties
import javax.inject.Inject

internal const val PIXABAY_VIDEO_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
internal const val REDDIT_RSS_MOTION_CACHE_TTL_MS = 2 * 60 * 60 * 1000L
private const val PIXABAY_VIDEO_RATE_LIMITED_UNTIL_KEY = "pixabay_video_rate_limited_until_ms"
private const val YOUTUBE_VIDEO_METADATA_PROBE_LIMIT = 30
private const val MAX_CACHED_VIDEO_FEED_ITEMS = 120
internal const val MAX_CONSECUTIVE_EMPTY_VIDEO_LOADS = 4

internal data class VideoLoadProgress(
    val hasMore: Boolean,
    val emptyLoadCount: Int,
)

internal data class PixabayVideoFetchSpec(
    val query: String,
    val videoType: String,
    val page: Int,
    val perPage: Int = 30,
)

internal data class PixabayVideoMetadataResult(
    val items: List<VideoWallpaperItem>,
    val streamUrls: Map<String, String>,
)

internal data class CachedPixabayVideoMetadata(
    val result: PixabayVideoMetadataResult,
    val cachedAtMs: Long,
    val nextAfter: String? = null,
    val pageExhausted: Boolean? = null,
)

internal data class RedditMotionFeedGroup(
    val key: String,
    val subreddits: List<String>,
)

private val LEGACY_REDDIT_MOTION_SUBREDDITS = listOf(
    "livewallpapers", "LiveWallpaper", "Cinemagraphs", "perfectloops",
)
private val VALIDATED_REDDIT_MOTION_SUBREDDITS = listOf(
    "livewallpapers",
    "Cinemagraphs",
    "perfectloops",
    "phonewallpapers",
    "AnimatedPixelArt",
    "LivingBackgrounds",
    "wallpaperengine",
)

private data class RedditRssMotionPage(
    val result: PixabayVideoMetadataResult,
    val groupKey: String,
    val nextAfter: String?,
    val exhausted: Boolean,
)

internal data class RedditMotionFeedSelection(
    val group: RedditMotionFeedGroup,
    val after: String?,
    val count: Int,
    val nextSubIndex: Int,
)

internal fun resolveRedditMotionFeedGroups(configuredSubreddits: String): List<RedditMotionFeedGroup> {
    val configured = configuredSubreddits
        .split(',')
        .map { it.trim().removePrefix("r/") }
        .filter { it.matches(Regex("[A-Za-z0-9_]{2,40}")) }
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }
    val normalizedConfigured = configured.map { it.lowercase(java.util.Locale.ROOT) }
    val normalizedLegacy = LEGACY_REDDIT_MOTION_SUBREDDITS.map { it.lowercase(java.util.Locale.ROOT) }
    val subreddits = when {
        configured.isEmpty() || normalizedConfigured == normalizedLegacy -> VALIDATED_REDDIT_MOTION_SUBREDDITS
        else -> configured.filterNot { it.equals("LiveWallpaper", ignoreCase = true) }
            .ifEmpty { VALIDATED_REDDIT_MOTION_SUBREDDITS }
    }
    return subreddits.chunked(2).mapIndexed { index, group ->
        val signature = group.joinToString("+").lowercase(java.util.Locale.ROOT)
        RedditMotionFeedGroup(
            key = "group_${index}_${signature.hashCode()}",
            subreddits = group,
        )
    }
}

internal fun redditAfterToken(rawId: String?): String? {
    val postId = rawId
        ?.removePrefix("rd_")
        ?.removePrefix("t3_")
        ?.trim()
        ?.takeIf { it.matches(Regex("[A-Za-z0-9]+")) }
        ?: return null
    return "t3_$postId"
}

internal fun selectRedditMotionFeed(
    groups: List<RedditMotionFeedGroup>,
    startIndex: Int,
    afters: Map<String, String?>,
): RedditMotionFeedSelection? {
    if (groups.isEmpty()) return null
    val normalizedStart = startIndex.coerceAtLeast(0)
    for (offset in groups.indices) {
        val absoluteIndex = normalizedStart + offset
        val group = groups[absoluteIndex % groups.size]
        if (afters.containsKey(group.key) && afters[group.key] == null) continue
        return RedditMotionFeedSelection(
            group = group,
            after = afters[group.key],
            count = (absoluteIndex / groups.size) * 100,
            nextSubIndex = absoluteIndex + 1,
        )
    }
    return null
}

internal fun isRedditMotionPageExhausted(rawEntryCount: Int, nextAfter: String?): Boolean =
    rawEntryCount < 100 || nextAfter == null

internal fun redditRssMotionCacheKey(
    group: RedditMotionFeedGroup,
    after: String?,
): String {
    val cursor = after ?: "start"
    return "reddit_rss_motion_v2_${group.key}_$cursor"
}

internal fun redditRssMotionUrl(
    group: RedditMotionFeedGroup,
    after: String?,
    count: Int = if (after == null) 0 else 100,
): String = "https://www.reddit.com/r/${group.subreddits.joinToString("+")}/new/.rss"
    .toHttpUrl()
    .newBuilder()
    .addQueryParameter("limit", "100")
    .apply {
        after?.let {
            addQueryParameter("count", count.coerceAtLeast(1).toString())
            addQueryParameter("after", it)
        }
    }
    .build()
    .toString()

internal fun redditPlayableMotionUrl(mediaUrl: String): String {
    val bareVideo = Regex(
        "^https://v\\.redd\\.it/([A-Za-z0-9]+)/?(?:\\?.*)?$",
        RegexOption.IGNORE_CASE,
    ).matchEntire(mediaUrl.trim()) ?: return mediaUrl
    return "https://v.redd.it/${bareVideo.groupValues[1]}/HLSPlaylist.m3u8"
}

internal fun isHlsMotionUrl(url: String): Boolean =
    url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

internal data class YouTubeVideoSearchItem(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
    val duration: Long,
    val uploaderName: String,
    val viewCount: Long,
)

internal fun resolveVideoLoadProgress(
    previousEmptyLoadCount: Int,
    newItemCount: Int,
): VideoLoadProgress {
    val emptyLoadCount = if (newItemCount == 0) previousEmptyLoadCount + 1 else 0
    return VideoLoadProgress(
        // Discovery rotates through independent queries and paged providers, so a single
        // empty batch is not the end of the catalog. But once several consecutive batches
        // come back empty (all providers exhausted/disabled/offline) we stop, otherwise the
        // screen's "auto-fill until MIN_INITIAL_VIDEO_RESULTS" effect spins loadMore forever.
        hasMore = emptyLoadCount < MAX_CONSECUTIVE_EMPTY_VIDEO_LOADS,
        emptyLoadCount = emptyLoadCount,
    )
}

internal fun resolvePexelsVideoQuery(
    page: Int,
    searchQuery: String?,
    fallbackQueries: List<String>,
): String {
    val normalizedQuery = searchQuery?.takeIf { it.isNotBlank() }
    if (normalizedQuery != null) return normalizedQuery
    if (fallbackQueries.isEmpty()) return "mobile wallpaper"
    val queryIndex = (page - 1).coerceAtLeast(0) % fallbackQueries.size
    return fallbackQueries[queryIndex]
}

internal fun resolvePexelsOrientationParam(
    orientation: OrientationFilter,
): String? = when (orientation) {
    OrientationFilter.PORTRAIT -> "portrait"
    OrientationFilter.LANDSCAPE -> "landscape"
    OrientationFilter.ALL -> null
}

internal fun resolvePixabayVideoFetchSpec(
    searchQuery: String?,
    page: Int,
): PixabayVideoFetchSpec = PixabayVideoFetchSpec(
    query = searchQuery ?: "abstract loop",
    videoType = if (searchQuery == null) "animation" else "all",
    page = page,
)

internal fun pixabayVideoCacheKey(spec: PixabayVideoFetchSpec): String =
    "pixabay_video_${spec.query.hashCode()}_${spec.videoType}_${spec.page}_${spec.perPage}"

internal fun videoFeedCacheKey(
    searchQuery: String,
    orientation: OrientationFilter,
    focusFilter: VideoFocusFilter,
): String = "video_feed_v2_${searchQuery.trim().lowercase().hashCode()}_${orientation.name}_${focusFilter.name}"

internal fun isPixabayVideoCacheFresh(cachedAtMs: Long, nowMs: Long): Boolean =
    cachedAtMs > 0 && nowMs - cachedAtMs <= PIXABAY_VIDEO_CACHE_TTL_MS

internal fun pixabayVideoRateLimitBackoffMillis(error: Throwable): Long? =
    pixabayRateLimitBackoffMillis(error)

internal fun keepPexelsVideosAsEnhancement(
    items: List<VideoWallpaperItem>,
): List<VideoWallpaperItem> {
    val hasBaseInventory = items.any { !it.source.equals("Pexels", ignoreCase = true) }
    return if (hasBaseInventory) items else items.filterNot { it.source.equals("Pexels", ignoreCase = true) }
}

internal fun mapYouTubeVideoSearchItem(
    item: YouTubeVideoSearchItem,
    metadata: YouTubeVideoMetadata?,
): VideoWallpaperItem = VideoWallpaperItem(
    id = "yt_${item.videoId}",
    title = item.title,
    thumbnailUrl = item.thumbnailUrl,
    source = "YouTube",
    duration = metadata?.durationSeconds?.takeIf { it > 0 } ?: item.duration,
    uploaderName = item.uploaderName,
    videoId = item.videoId,
    popularity = item.viewCount,
    videoWidth = metadata?.width?.takeIf { it > 0 } ?: 0,
    videoHeight = metadata?.height?.takeIf { it > 0 } ?: 0,
    videoRotationDegrees = metadata?.rotationDegrees ?: 0,
    videoMimeType = metadata?.mimeType.orEmpty(),
    videoCodec = metadata?.videoCodec.orEmpty(),
    contentSource = com.freevibe.data.model.ContentSource.YOUTUBE,
    license = "YouTube",
    sourcePageUrl = "https://www.youtube.com/watch?v=${item.videoId}",
)

internal fun mapPixabayVideosToMetadata(
    videos: List<PixabayVideo>,
): PixabayVideoMetadataResult {
    val urls = linkedMapOf<String, String>()
    val items = videos.filter { it.duration in 2..60 }.mapNotNull { video ->
        val file = (video.videos.medium ?: video.videos.small ?: video.videos.large)
            ?.takeIf { it.url.isNotBlank() }
        file?.let {
            val item = VideoWallpaperItem(
                id = "pbv_${video.id}",
                title = video.tags
                    .split(",")
                    .take(3)
                    .joinToString(" ") { tag -> tag.trim() }
                    .ifBlank { "Pixabay video" },
                thumbnailUrl = video.thumbnailUrl,
                source = "Pixabay",
                duration = video.duration.toLong(),
                uploaderName = video.user,
                popularity = video.views.toLong(),
                videoWidth = it.width,
                videoHeight = it.height,
                contentSource = com.freevibe.data.model.ContentSource.PIXABAY,
                license = "Pixabay License",
                sourcePageUrl = "https://pixabay.com/videos/id-${video.id}/",
            )
            urls[item.id] = it.url
            item
        }
    }
    return PixabayVideoMetadataResult(items = items, streamUrls = urls)
}

internal fun encodePixabayVideoCache(
    cached: CachedPixabayVideoMetadata,
): String {
    val properties = Properties()
    properties.setProperty("cachedAtMs", cached.cachedAtMs.toString())
    cached.nextAfter?.let { properties.setProperty("nextAfter", it) }
    cached.pageExhausted?.let { properties.setProperty("pageExhausted", it.toString()) }
    properties.setProperty("count", cached.result.items.size.toString())
    cached.result.items.forEachIndexed { index, item ->
        val prefix = "item.$index."
        properties.setProperty("${prefix}id", item.id)
        properties.setProperty("${prefix}title", item.title)
        properties.setProperty("${prefix}thumbnailUrl", item.thumbnailUrl)
        properties.setProperty("${prefix}source", item.source)
        properties.setProperty("${prefix}duration", item.duration.toString())
        properties.setProperty("${prefix}uploaderName", item.uploaderName)
        properties.setProperty("${prefix}videoId", item.videoId)
        properties.setProperty("${prefix}popularity", item.popularity.toString())
        properties.setProperty("${prefix}videoWidth", item.videoWidth.toString())
        properties.setProperty("${prefix}videoHeight", item.videoHeight.toString())
        properties.setProperty("${prefix}contentSource", item.contentSource.name)
        properties.setProperty("${prefix}license", item.license)
        properties.setProperty("${prefix}sourcePageUrl", item.sourcePageUrl)
        properties.setProperty("${prefix}streamUrl", cached.result.streamUrls[item.id].orEmpty())
    }
    val output = ByteArrayOutputStream()
    properties.store(output, null)
    return output.toString(StandardCharsets.ISO_8859_1.name())
}

internal fun decodePixabayVideoCache(
    raw: String?,
    nowMs: Long,
    requireFresh: Boolean = true,
    freshnessTtlMs: Long = PIXABAY_VIDEO_CACHE_TTL_MS,
): CachedPixabayVideoMetadata? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val properties = Properties()
        properties.load(ByteArrayInputStream(raw.toByteArray(StandardCharsets.ISO_8859_1)))
        val cachedAtMs = properties.getProperty("cachedAtMs")?.toLongOrNull() ?: 0L
        if (requireFresh && (cachedAtMs <= 0 || nowMs - cachedAtMs > freshnessTtlMs)) return null
        val pageExhausted = properties.getProperty("pageExhausted")?.toBooleanStrictOrNull()
        val count = properties.getProperty("count")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val items = mutableListOf<VideoWallpaperItem>()
        val urls = linkedMapOf<String, String>()
        for (i in 0 until count) {
            val prefix = "item.$i."
            val id = properties.getProperty("${prefix}id").orEmpty()
            val streamUrl = properties.getProperty("${prefix}streamUrl").orEmpty()
            if (id.isBlank() || streamUrl.isBlank()) continue
            val item = VideoWallpaperItem(
                id = id,
                title = properties.getProperty("${prefix}title").orEmpty(),
                thumbnailUrl = properties.getProperty("${prefix}thumbnailUrl").orEmpty(),
                source = properties.getProperty("${prefix}source", "Pixabay"),
                duration = properties.getProperty("${prefix}duration")?.toLongOrNull() ?: 0L,
                uploaderName = properties.getProperty("${prefix}uploaderName").orEmpty(),
                videoId = properties.getProperty("${prefix}videoId").orEmpty(),
                popularity = properties.getProperty("${prefix}popularity")?.toLongOrNull() ?: 0L,
                videoWidth = properties.getProperty("${prefix}videoWidth")?.toIntOrNull() ?: 0,
                videoHeight = properties.getProperty("${prefix}videoHeight")?.toIntOrNull() ?: 0,
                contentSource = runCatching { com.freevibe.data.model.ContentSource.valueOf(properties.getProperty("${prefix}contentSource", "PIXABAY")) }.getOrDefault(com.freevibe.data.model.ContentSource.PIXABAY),
                license = properties.getProperty("${prefix}license").orEmpty(),
                sourcePageUrl = properties.getProperty("${prefix}sourcePageUrl").orEmpty(),
            )
            items += item
            urls[id] = streamUrl
        }
        if (items.isEmpty() && pageExhausted == null) return null
        CachedPixabayVideoMetadata(
            result = PixabayVideoMetadataResult(items = items, streamUrls = urls),
            cachedAtMs = cachedAtMs,
            nextAfter = properties.getProperty("nextAfter")?.takeIf { it.isNotBlank() },
            pageExhausted = pageExhausted,
        )
    }.getOrNull()
}

@HiltViewModel
class VideoWallpapersViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val pexelsApi: PexelsApi,
    private val pixabayApi: com.freevibe.data.remote.pixabay.PixabayApi,
    private val prefs: PreferencesManager,
    private val okHttpClient: OkHttpClient,
    private val videoWallpaperStorage: VideoWallpaperStorage,
    private val sourceMetrics: SourceMetrics,
    private val ytDlpUpdateManager: YtDlpUpdateManager,
    private val ytDlpRequestFactory: YouTubeYtDlpRequestFactory,
    private val videoPreviewCache: VideoPreviewCache,
    val voteRepo: VoteRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VideoWallpapersState())
    val state = _state.asStateFlow()
    private val _gallerySelectionResult = MutableStateFlow<VideoWallpaperSelectionResult?>(null)
    val gallerySelectionResult = _gallerySelectionResult.asStateFlow()
    private val pixabayVideoCache = PixabayVideoCacheStore(context)
    @Volatile
    private var pixabayVideoRateLimitedUntilMs: Long = 0L

    private val _resolvedIds = MutableStateFlow<Set<String>>(emptySet())
    val resolvedIds = _resolvedIds.asStateFlow()

    // Cache of resolved video stream URLs
    // Bounded cache — evict oldest when exceeding 200 entries. Eviction must also
    // un-mark the id as resolved: a resolved id with no cached URL renders a
    // permanent spinner and misroutes Apply into the yt-dlp re-resolve path.
    private val streamUrls = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            val evict = size > 200
            if (evict && eldest != null) {
                _resolvedIds.update { ids -> ids - eldest.key }
            }
            return evict
        }
    }.let { java.util.Collections.synchronizedMap(it) }
    private val previewResolveInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val loadLock = Any()
    private var loadJob: Job? = null
    private var loadGeneration = 0L

    private val junkPatterns = listOf(
        "top \\d+", "\\d+ best", "how to", "tutorial", "review", "setup",
        "compilation", "reaction", "podcast", "interview", "unboxing",
        "FAQ", "help", "guide", "install", "download app", "engine",
        "ranked", "tier list", "vs\\.", "comparison", "explained",
        "official", "trailer", "teaser", "behind the scenes",
        "i tested", "i tried", "i bought", "i found", "must have",
        "you need", "don.?t buy", "worth it", "honest",
        "\\bmake\\b",
        "3d live", "app demo", "free download", "link in",
        "showing my", "on my phone", "on my android", "on my iphone",
        "samsung galaxy", "\\bios\\b", "\\bsettings\\b", "\\bidea\\b",
    ).map { Regex(it, RegexOption.IGNORE_CASE) }

    private val youtubePortraitQueries = listOf(
        "phone live wallpaper vertical loop",
        "AMOLED live wallpaper vertical phone loop",
        "vertical video wallpaper phone 4K loop",
        "live wallpaper android vertical abstract",
        "vertical neon cyberpunk live wallpaper 4K",
        "vertical space galaxy live wallpaper loop",
        "vertical anime scenery live wallpaper loop",
        "vertical nature rain live wallpaper 4K",
        "vertical ocean waves live wallpaper loop",
        "vertical dark AMOLED particles wallpaper",
        "vertical city night live wallpaper loop",
        "vertical relaxing ambient wallpaper 4K",
    )
    private val youtubeLandscapeQueries = listOf(
        "live wallpaper desktop 4K loop",
        "landscape live wallpaper widescreen loop",
        "cinematic background loop 4K",
        "nature landscape video wallpaper loop",
        "space nebula live wallpaper 4K loop",
        "cyberpunk city live wallpaper widescreen",
        "anime scenery live wallpaper 4K loop",
        "ambient rain window live wallpaper loop",
        "ocean waves cinematic wallpaper loop 4K",
        "dark AMOLED particles wallpaper loop",
        "forest waterfall live wallpaper 4K",
        "minimal abstract motion wallpaper loop",
    )
    private val youtubeAllQueries = listOf(
        "live wallpaper loop 4K",
        "AMOLED live wallpaper loop",
        "abstract video wallpaper loop",
        "live wallpaper android loop",
        "space galaxy live wallpaper 4K loop",
        "neon cyberpunk live wallpaper loop",
        "anime scenery live wallpaper loop 4K",
        "nature rain live wallpaper loop",
        "ocean waves video wallpaper 4K",
        "dark particles AMOLED live wallpaper",
        "cinematic city night wallpaper loop",
        "minimal ambient motion wallpaper loop",
    )

    private val pexelsQueries = listOf(
        "mobile wallpaper", "phone wallpaper", "abstract background",
        "nature loop", "neon lights", "space", "ocean waves",
        "rain window", "city night", "forest waterfall", "ink motion",
        "smoke", "particles", "underwater", "aurora",
    )

    init { load() }

    fun clearError() = _state.update { it.copy(error = null) }

    fun refresh() {
        _state.update {
            it.copy(
                isRefreshing = true,
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                pixabayPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        load()
    }

    fun loadMore() {
        load(loadMore = true)
    }

    fun setOrientation(orientation: OrientationFilter) {
        _state.update {
            it.copy(
                orientation = orientation,
                items = emptyList(),
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                // pixabayPage too: a stale high page against a new filter/query makes
                // Pixabay return HTTP 400 (page out of range) until pull-to-refresh.
                pixabayPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        streamUrls.clear()
        previewResolveInFlight.clear()
        _resolvedIds.value = emptySet()
        load()
    }

    fun setFocusFilter(filter: VideoFocusFilter) {
        _state.update {
            it.copy(
                focusFilter = filter,
                items = rankVideoWallpapers(it.items, filter, it.orientation),
            )
        }
    }

    fun search(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                items = emptyList(),
                error = null,
                degradedSources = emptyList(),
                pexelsPage = 1,
                pixabayPage = 1,
                ytQueryIndex = 0,
                redditSubIndex = 0,
                redditAfters = emptyMap(),
                emptyLoadCount = 0,
            )
        }
        streamUrls.clear()
        previewResolveInFlight.clear()
        _resolvedIds.value = emptySet()
        load()
    }

    fun getStreamUrl(id: String): String? = streamUrls[id]

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun previewMediaSourceFactory() = videoPreviewCache.mediaSourceFactory()

    fun ensureStreamResolved(item: VideoWallpaperItem) {
        val cachedUrl = streamUrls[item.id]
        if (cachedUrl != null) {
            prebufferPreview(item.id, cachedUrl)
            return
        }
        if (item.source != "YouTube" || item.videoId.isBlank() || !previewResolveInFlight.add(item.id)) return
        viewModelScope.launch {
            try {
                youtubeRepo.getVideoStreamUrl(item.videoId)?.let { url ->
                    streamUrls[item.id] = url
                    _resolvedIds.update { it + item.id }
                    prebufferPreview(item.id, url)
                }
            } catch (e: Throwable) {
                e.rethrowIfCancelled()
            } finally {
                previewResolveInFlight.remove(item.id)
            }
        }
    }

    private fun prebufferPreview(id: String, url: String) {
        if (!shouldPrebufferVideoPreview(url)) return
        viewModelScope.launch {
            runCatching { videoPreviewCache.prebuffer(id, url) }
        }
    }

    fun upvote(id: String) { viewModelScope.launch { voteRepo.upvote(id) } }
    fun downvote(id: String) { viewModelScope.launch { voteRepo.downvote(id) } }
    fun undoDownvote(id: String) { viewModelScope.launch { voteRepo.undoDownvote(id) } }
    fun clearGallerySelectionResult() { _gallerySelectionResult.value = null }

    fun prepareGalleryVideoWallpaper(uri: android.net.Uri) {
        viewModelScope.launch {
            _gallerySelectionResult.value = VideoWallpaperSelectionResult.Preparing
            val result = videoWallpaperStorage.prepareFromUri(uri)
            _gallerySelectionResult.value = result.fold(
                onSuccess = { VideoWallpaperSelectionResult.Ready },
                onFailure = {
                    VideoWallpaperSelectionResult.Failure(it.message ?: "Could not prepare video")
                },
            )
        }
    }

    fun applyVideoWallpaper(
        item: VideoWallpaperItem,
        scaleMode: String = VIDEO_WALLPAPER_SCALE_MODE_ZOOM,
    ) {
        if (_state.value.isApplying != null) return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = item.id) }
            try {
                if (item.source == "YouTube" && !prefs.youtubeProviderEnabled.first()) {
                    sourceMetrics.recordDisabled("youtube")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(com.freevibe.R.string.video_wp_youtube_disabled), Toast.LENGTH_SHORT).show()
                    }
                    _state.update { it.copy(isApplying = null) }
                    return@launch
                }
                // Get stream URL (cached or resolve)
                val videoUrl = streamUrls[item.id] ?: run {
                    if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Resolving stream URL for apply: ${item.videoId}")
                    val url = youtubeRepo.getVideoStreamUrl(item.videoId)
                    if (url != null) { streamUrls[item.id] = url }
                    url
                }

                if (videoUrl == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(com.freevibe.R.string.ytdlp_video_url_update_hint), Toast.LENGTH_SHORT).show()
                    }
                    _state.update { it.copy(isApplying = null) }
                    return@launch
                }

                if (com.freevibe.BuildConfig.DEBUG) Log.d("VideoWP", "Downloading video (source: ${item.source})...")
                val downloadedExtension = when {
                    videoUrl.substringBefore('?').endsWith(".gif", ignoreCase = true) -> "gif"
                    videoUrl.substringBefore('?').endsWith(".webm", ignoreCase = true) -> "webm"
                    else -> "mp4"
                }
                val file = videoWallpaperStorage.prepareDownloadedVideo(extension = downloadedExtension) { cacheFile ->
                    if (isHlsMotionUrl(videoUrl)) {
                        // A Reddit HLS URL is a playlist, not a video file. Let yt-dlp and
                        // ffmpeg fetch its segments and produce a bounded MP4; raw-copying
                        // the m3u8 bytes would create an invalid wallpaper file.
                        val hlsOutput = java.io.File(cacheFile.parentFile, "${cacheFile.name}.hls.mp4")
                        try {
                            val request = com.yausername.youtubedl_android.YoutubeDLRequest(videoUrl)
                            request.addOption("-f", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best")
                            request.addOption("--merge-output-format", "mp4")
                            request.addOption("--remux-video", "mp4")
                            request.addOption("--force-overwrites")
                            request.addOption("-o", hlsOutput.absolutePath)
                            applyYtDlpDownloadBounds(request)
                            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                            if (!hlsOutput.exists()) throw java.io.IOException("HLS download did not produce an MP4")
                            // Move rather than copy: both files live in the same
                            // directory, so a copy would briefly need twice the
                            // video's size on a device already near its cap.
                            moveIntoPlace(hlsOutput, cacheFile)
                            ytDlpUpdateManager.recordExtractionSuccess()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            ytDlpUpdateManager.recordExtractionFailure(e)
                            throw e
                        } finally {
                            hlsOutput.delete()
                        }
                    } else if (item.source == "YouTube" && item.videoId.isNotEmpty()) {
                        // YouTube: use yt-dlp for download
                        try {
                            val ytUrl = "https://www.youtube.com/watch?v=${item.videoId}"
                            val request = ytDlpRequestFactory.create(ytUrl)
                            request.addOption("-f", "bestvideo[ext=mp4][height<=1080]/best[ext=mp4]/best")
                            request.addOption("-o", cacheFile.absolutePath)
                            request.addOption("--force-overwrites")
                            applyYtDlpDownloadBounds(request)
                            com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request)
                            ytDlpUpdateManager.recordExtractionSuccess()
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            ytDlpUpdateManager.recordExtractionFailure(e)
                            if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "yt-dlp download failed: ${e.message}, using stream URL")
                            okHttpClient.newCall(Request.Builder().url(videoUrl).build()).execute().use { resp ->
                                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                                val body = resp.body ?: throw Exception("Empty response body")
                                if (advertisedLengthExceeds(body.contentLength(), MAX_VIDEO_WALLPAPER_BYTES)) {
                                    throw Exception("Video exceeds size limit")
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
                        okHttpClient.newCall(Request.Builder().url(videoUrl).build()).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            val body = resp.body ?: throw Exception("Empty response body")
                            if (advertisedLengthExceeds(body.contentLength(), MAX_VIDEO_WALLPAPER_BYTES)) {
                                throw Exception("Video exceeds size limit")
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

                launchOrExportVideoWallpaper(context, file, scaleMode = scaleMode)
                _state.update { it.copy(isApplying = null) }
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
                _state.update { it.copy(isApplying = null) }
            }
        }
    }

    override fun onCleared() {
        synchronized(loadLock) {
            loadGeneration++
            loadJob?.cancel()
            loadJob = null
        }
        super.onCleared()
    }

    private fun load(loadMore: Boolean = false) {
        val job = synchronized(loadLock) {
            if (loadMore) {
                val current = _state.value
                val feedLoadPending = loadJob != null
                if (current.isLoading || current.isLoadingMore || !current.hasMore || feedLoadPending) {
                    return
                }
                _state.update { it.copy(isLoadingMore = true) }
            } else {
                loadJob?.cancel()
            }
            val generation = ++loadGeneration
            viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
            if (!loadMore) {
                _state.update {
                    it.copy(
                        isLoading = !it.isRefreshing && it.items.isEmpty(),
                        error = null,
                        degradedSources = emptyList(),
                    )
                }
            }

            val s = _state.value
            val searchQ = s.searchQuery.ifBlank { null }
            if (!loadMore && !s.isRefreshing && s.items.isEmpty()) {
                readPixabayVideoCache(
                    cacheKey = videoFeedCacheKey(s.searchQuery, s.orientation, s.focusFilter),
                    freshOnly = true,
                )?.let { cachedFeed ->
                    rememberPixabayVideoMetadata(cachedFeed)
                    _state.update { current ->
                        current.copy(items = cachedFeed.items, isLoading = false)
                    }
                    cachedFeed.items.take(2).forEach(::ensureStreamResolved)
                    if (com.freevibe.BuildConfig.DEBUG) {
                        Log.d("VideoWP", "Warm feed ready: ${cachedFeed.items.size} cached items")
                    }
                }
            }
            val newItems = mutableListOf<VideoWallpaperItem>()
            val attemptedSources = java.util.Collections.synchronizedSet(mutableSetOf<String>())
            val failedSources = java.util.Collections.synchronizedSet(mutableSetOf<String>())
            val youtubeEnabled = prefs.youtubeProviderEnabled.first()
            val redditEnabled = prefs.redditProviderEnabled.first()
            val pexelsEnabled = prefs.pexelsProviderEnabled.first()
            val pixabayEnabled = prefs.pixabayProviderEnabled.first()
            val redditFeedGroups = resolveRedditMotionFeedGroups(
                configuredSubreddits = if (redditEnabled) prefs.redditVideoSubreddits.first() else "",
            )
            val redditFeedSelection = selectRedditMotionFeed(
                groups = redditFeedGroups,
                startIndex = s.redditSubIndex,
                afters = s.redditAfters,
            )
            var loadedRedditPage: RedditRssMotionPage? = null

            kotlinx.coroutines.supervisorScope {
                // 1. Pexels
                val pexelsJob = async(Dispatchers.IO) {
                    if (!pexelsEnabled) {
                        sourceMetrics.recordDisabled("pexels")
                        return@async emptyList<VideoWallpaperItem>()
                    }
                    try {
                        val key = prefs.pexelsApiKey.first()
                        if (key.isBlank()) return@async emptyList()
                        attemptedSources += "Pexels"
                        val query = resolvePexelsVideoQuery(
                            page = s.pexelsPage,
                            searchQuery = searchQ,
                            fallbackQueries = pexelsQueries,
                        )
                        val orientation = resolvePexelsOrientationParam(s.orientation)
                        val response = pexelsApi.searchVideos(apiKey = key, query = query, orientation = orientation, perPage = 30, page = s.pexelsPage)
                        response.videos.filter { it.duration in 5..120 }.mapNotNull { video ->
                            val file = video.videoFiles
                                .filter { it.fileType == "video/mp4" || it.link.endsWith(".mp4") }
                                .sortedByDescending { it.height ?: 0 }
                                .firstOrNull { (it.height ?: 0) <= 1920 }
                                ?: video.videoFiles.firstOrNull { it.link.endsWith(".mp4") }
                            file?.let {
                                val item = VideoWallpaperItem(id = "px_${video.id}", title = context.getString(com.freevibe.R.string.video_wp_by_creator, video.user.name), thumbnailUrl = video.image, source = "Pexels", duration = video.duration.toLong(), uploaderName = video.user.name, videoWidth = video.width, videoHeight = video.height, contentSource = com.freevibe.data.model.ContentSource.PEXELS, license = "Pexels License", sourcePageUrl = video.url)
                                streamUrls[item.id] = it.link
                                _resolvedIds.update { it + item.id }
                                item
                            }
                        }
                    } catch (e: Throwable) {
                        e.rethrowIfCancelled()
                        failedSources += "Pexels"
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Pexels: ${e.message}")
                        emptyList()
                    }
                }

                // 2. Reddit public Atom/RSS. Rotate small multi-subreddit feeds and retain
                // a cursor per group so subsequent batches continue beyond the first page.
                val redditJob = async(Dispatchers.IO) {
                    if (!redditEnabled) {
                        sourceMetrics.recordDisabled("reddit")
                        return@async null
                    }
                    val selection = redditFeedSelection ?: return@async null
                    attemptedSources += "Reddit"
                    try {
                        val page = loadRedditRssMotionMetadata(
                            group = selection.group,
                            after = selection.after,
                            count = selection.count,
                        )
                        rememberPixabayVideoMetadata(page.result)
                        page.copy(
                            result = page.result.copy(
                                items = page.result.items.filter { item ->
                                    searchQ.isNullOrBlank() || item.title.contains(searchQ, ignoreCase = true)
                                },
                            ),
                        )
                    } catch (e: Throwable) {
                        e.rethrowIfCancelled()
                        failedSources += "Reddit"
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Reddit RSS: ${e.message}")
                        null
                    }
                }

                // 3. YouTube
                val ytJob = async(Dispatchers.IO) {
                    if (!youtubeEnabled) {
                        sourceMetrics.recordDisabled("youtube")
                        return@async emptyList<VideoWallpaperItem>()
                    }
                    try {
                        attemptedSources += "YouTube"
                        val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                        val ytQueries = when (s.orientation) {
                            OrientationFilter.PORTRAIT -> youtubePortraitQueries
                            OrientationFilter.LANDSCAPE -> youtubeLandscapeQueries
                            OrientationFilter.ALL -> youtubeAllQueries
                        }
                        val orientSuffix = when (s.orientation) {
                            OrientationFilter.PORTRAIT -> " vertical wallpaper"
                            OrientationFilter.LANDSCAPE -> " landscape wallpaper"
                            OrientationFilter.ALL -> " wallpaper"
                        }
                        val query = searchQ?.let { "$it$orientSuffix" } ?: ytQueries[s.ytQueryIndex % ytQueries.size]
                        val extractor = service.getSearchExtractor(
                            createLegacyCompatibleYouTubeSearchHandler(query),
                        )
                        extractor.fetchPage()
                        val youtubeCandidates = extractor.initialPage.items
                            .filterIsInstance<StreamInfoItem>()
                            .filter { it.duration in 5..120 }
                            .filter { item -> junkPatterns.none { it.containsMatchIn(item.name) } }
                            .filter { !it.name.contains("#") }
                            .sortedByDescending { it.viewCount }
                            .take(YOUTUBE_VIDEO_METADATA_PROBE_LIMIT)
                            .map { item ->
                                val vid = item.url.substringAfter("v=").substringBefore("&")
                                val thumb = item.thumbnails.firstOrNull { it.width > 0 && it.height > 0 }
                                    ?: item.thumbnails.firstOrNull()
                                YouTubeVideoSearchItem(
                                    videoId = vid,
                                    title = item.name,
                                    thumbnailUrl = thumb?.url ?: "",
                                    thumbnailWidth = thumb?.width?.takeIf { it > 0 } ?: 0,
                                    thumbnailHeight = thumb?.height?.takeIf { it > 0 } ?: 0,
                                    duration = item.duration,
                                    uploaderName = item.uploaderName ?: "",
                                    viewCount = item.viewCount,
                                )
                            }
                        val metadataProbeSemaphore = Semaphore(4)
                        youtubeCandidates.map { candidate ->
                            async(Dispatchers.IO) {
                                val metadata = metadataProbeSemaphore.withPermit {
                                    youtubeRepo.getVideoMetadata(candidate.videoId)
                                }
                                mapYouTubeVideoSearchItem(candidate, metadata)
                            }
                        }.awaitAll()
                    } catch (e: Throwable) {
                        e.rethrowIfCancelled()
                        failedSources += "YouTube"
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "YouTube: ${e.message}")
                        emptyList()
                    }
                }

                // 4. Pixabay Videos (animated loops + short videos)
                val pixabayJob = async(Dispatchers.IO) {
                    if (!pixabayEnabled) {
                        sourceMetrics.recordDisabled("pixabay")
                        return@async emptyList<VideoWallpaperItem>()
                    }
                    try {
                        val pbKey = prefs.pixabayApiKey.first()
                        if (pbKey.isBlank()) return@async emptyList<VideoWallpaperItem>()
                        attemptedSources += "Pixabay"
                        val spec = resolvePixabayVideoFetchSpec(searchQuery = searchQ, page = s.pixabayPage)
                        val result = loadPixabayVideoMetadata(apiKey = pbKey, spec = spec)
                        if (result == null) {
                            failedSources += "Pixabay"
                            emptyList()
                        } else {
                            rememberPixabayVideoMetadata(result)
                            result.items
                        }
                    } catch (e: Throwable) {
                        e.rethrowIfCancelled()
                        failedSources += "Pixabay"
                        if (com.freevibe.BuildConfig.DEBUG) Log.e("VideoWP", "Pixabay: ${e.message}")
                        emptyList()
                    }
                }

                loadedRedditPage = redditJob.await()
                newItems.addAll(loadedRedditPage?.result?.items.orEmpty())
                newItems.addAll(pexelsJob.await())
                newItems.addAll(pixabayJob.await())
                newItems.addAll(ytJob.await())
            }

            // Filter by orientation (items with known dimensions are filtered; unknown pass through)
            val enhancementSafeItems = keepPexelsVideosAsEnhancement(newItems)
            val orientedItems = when (s.orientation) {
                OrientationFilter.ALL -> enhancementSafeItems
                OrientationFilter.PORTRAIT -> enhancementSafeItems.filter { !it.hasDimensions || it.isPortrait }
                OrientationFilter.LANDSCAPE -> enhancementSafeItems.filter { !it.hasDimensions || it.isLandscape }
            }

            // Deduplicate against existing items, then rank by fit / loop / battery heuristics.
            val existingIds = if (loadMore) s.items.map { it.id }.toSet() else emptySet()
            val deduped = orientedItems.filter { it.id !in existingIds }.distinctBy { it.id }
            val mixed = rankVideoWallpapers(
                items = deduped,
                filter = s.focusFilter,
                orientation = s.orientation,
            )

            val loadProgress = resolveVideoLoadProgress(
                previousEmptyLoadCount = s.emptyLoadCount,
                newItemCount = mixed.size,
            )
            val sourceFailures = failedSources.toList().sorted()
            val sourceFailureSet = sourceFailures.toSet()
            val attemptedCount = attemptedSources.size
            val allAttemptedFailed = attemptedCount > 0 && sourceFailures.size == attemptedCount
            val preserveCurrentFeed = !loadMore && mixed.isEmpty() && _state.value.items.isNotEmpty()

            currentCoroutineContext().ensureActive()
            _state.update {
                it.copy(
                    items = when {
                        loadMore -> it.items + mixed
                        preserveCurrentFeed -> it.items
                        else -> mixed
                    },
                    isLoading = false,
                    isLoadingMore = false,
                    isRefreshing = false,
                    error = when {
                        allAttemptedFailed && preserveCurrentFeed -> "Video sources are unavailable right now. Showing your last good results."
                        allAttemptedFailed -> "Video sources are unavailable right now."
                        sourceFailures.isNotEmpty() && mixed.isEmpty() && !preserveCurrentFeed -> "Limited source availability right now."
                        else -> null
                    },
                    degradedSources = sourceFailures,
                    hasMore = loadProgress.hasMore,
                    pexelsPage = if ("Pexels" !in sourceFailureSet && "Pexels" in attemptedSources) it.pexelsPage + 1 else it.pexelsPage,
                    pixabayPage = if ("Pixabay" !in sourceFailureSet && "Pixabay" in attemptedSources) it.pixabayPage + 1 else it.pixabayPage,
                    ytQueryIndex = if ("YouTube" !in sourceFailureSet && "YouTube" in attemptedSources) it.ytQueryIndex + 1 else it.ytQueryIndex,
                    redditSubIndex = if ("Reddit" !in sourceFailureSet && "Reddit" in attemptedSources) {
                        redditFeedSelection?.nextSubIndex ?: it.redditSubIndex
                    } else {
                        it.redditSubIndex
                    },
                    redditAfters = loadedRedditPage?.let { page ->
                        it.redditAfters + (page.groupKey to page.nextAfter.takeUnless { page.exhausted })
                    } ?: it.redditAfters,
                    emptyLoadCount = loadProgress.emptyLoadCount,
                )
            }
            if (com.freevibe.BuildConfig.DEBUG) {
                Log.d("VideoWP", "Feed ready: ${_state.value.items.size} items; batch=${mixed.size}; loadMore=$loadMore")
            }
            cacheVisibleVideoFeed(s)
            _state.value.items.take(3).forEach(::ensureStreamResolved)

            // Pre-resolve YouTube URLs
            mixed.filter { youtubeEnabled && it.source == "YouTube" && !streamUrls.containsKey(it.id) }.take(4).let { ytItems ->
                val sem = Semaphore(5)
                ytItems.forEach { item ->
                    launch {
                        sem.acquire()
                        try {
                            youtubeRepo.getVideoStreamUrl(item.videoId)?.let {
                                streamUrls[item.id] = it
                                _resolvedIds.update { ids -> ids + item.id }
                            }
                        } catch (t: Throwable) {
                            t.rethrowIfCancelled()
                        } finally {
                            sem.release()
                        }
                    }
                }
            }
            } finally {
                // Only the CURRENT load may clear the flags: a cancelled load's finally
                // runs after its replacement already set isLoading = true, and clearing
                // here would flash the empty-state card for the whole new load.
                synchronized(loadLock) {
                    if (loadGeneration == generation) {
                        loadJob = null
                        _state.update { it.copy(isLoading = false, isLoadingMore = false, isRefreshing = false) }
                    }
                }
            }
            }.also { loadJob = it }
        }
        job.start()
    }

    private suspend fun loadPixabayVideoMetadata(
        apiKey: String,
        spec: PixabayVideoFetchSpec,
    ): PixabayVideoMetadataResult? {
        val cacheKey = pixabayVideoCacheKey(spec)
        readPixabayVideoCache(cacheKey, freshOnly = true)?.let { return it }
        if (System.currentTimeMillis() < activePixabayVideoRateLimitUntilMs()) {
            return readPixabayVideoCache(cacheKey, freshOnly = false)
        }
        return try {
            val response = sourceMetrics.measure("pixabay") {
                pixabayApi.searchVideos(
                    apiKey = apiKey,
                    query = spec.query,
                    videoType = spec.videoType,
                    page = spec.page,
                    perPage = spec.perPage,
                )
            }
            mapPixabayVideosToMetadata(response.hits)
                .also { result ->
                    if (result.items.isNotEmpty()) writePixabayVideoCache(cacheKey, result)
                }
        } catch (e: Throwable) {
            e.rethrowIfCancelled()
            pixabayVideoRateLimitBackoffMillis(e)?.let { backoff ->
                updatePixabayVideoRateLimit(backoff)
            }
            readPixabayVideoCache(cacheKey, freshOnly = false) ?: throw e
        }
    }

    private suspend fun loadRedditRssMotionMetadata(
        group: RedditMotionFeedGroup,
        after: String?,
        count: Int,
    ): RedditRssMotionPage {
        val cacheKey = redditRssMotionCacheKey(group, after)
        readVideoMetadataCache(
            cacheKey = cacheKey,
            freshOnly = true,
            freshnessTtlMs = REDDIT_RSS_MOTION_CACHE_TTL_MS,
        )?.let { cached ->
            return RedditRssMotionPage(
                result = cached.result,
                groupKey = group.key,
                nextAfter = if (cached.pageExhausted != null) {
                    cached.nextAfter
                } else {
                    redditAfterToken(cached.result.items.lastOrNull()?.id)
                },
                exhausted = cached.pageExhausted ?: false,
            )
        }
        return try {
            val request = Request.Builder()
                .url(redditRssMotionUrl(group, after, count))
                .header(
                    "User-Agent",
                    "Aura/${com.freevibe.BuildConfig.VERSION_NAME} open-source wallpaper reader",
                )
                .header("Accept", "application/atom+xml, application/xml;q=0.9")
                .build()
            val rssPage = sourceMetrics.measure("reddit") {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("Reddit RSS HTTP ${response.code}")
                    val xml = response.body?.string().orEmpty()
                    if (xml.isBlank()) throw java.io.IOException("Reddit RSS returned an empty feed")
                    parseRedditRssPage(xml, group.subreddits.first())
                }
            }
            val urls = linkedMapOf<String, String>()
            val items = rssPage.entries
                .asSequence()
                .filter { it.isAnimated }
                .filter { entry -> junkPatterns.none { it.containsMatchIn(entry.title) } }
                .map { entry ->
                    val playbackUrl = redditPlayableMotionUrl(entry.mediaUrl)
                    VideoWallpaperItem(
                        id = "rd_${entry.id}",
                        title = entry.title,
                        thumbnailUrl = entry.thumbnailUrl,
                        source = "Reddit",
                        uploaderName = listOfNotNull(
                            entry.author.takeIf { it.isNotBlank() }?.let { "u/$it" },
                            entry.subreddit.takeIf { it.isNotBlank() }?.let { "r/$it" },
                        ).joinToString(" · "),
                        videoWidth = entry.width,
                        videoHeight = entry.height,
                        videoMimeType = when {
                            playbackUrl.substringBefore('?').endsWith(".gif", true) -> "image/gif"
                            playbackUrl.substringBefore('?').endsWith(".webm", true) -> "video/webm"
                            playbackUrl.substringBefore('?').endsWith(".m3u8", true) -> "application/x-mpegURL"
                            else -> "video/mp4"
                        },
                        contentSource = com.freevibe.data.model.ContentSource.REDDIT,
                        license = "Reddit",
                        sourcePageUrl = entry.sourcePageUrl,
                    ).also { item -> urls[item.id] = playbackUrl }
                }
                .toList()
            val result = PixabayVideoMetadataResult(items = items, streamUrls = urls)
            val exhausted = isRedditMotionPageExhausted(rssPage.rawEntryCount, rssPage.nextAfter)
            writePixabayVideoCache(
                cacheKey = cacheKey,
                result = result,
                nextAfter = rssPage.nextAfter,
                pageExhausted = exhausted,
            )
            RedditRssMotionPage(
                result = result,
                groupKey = group.key,
                nextAfter = rssPage.nextAfter,
                exhausted = exhausted,
            )
        } catch (e: Throwable) {
            e.rethrowIfCancelled()
            val stale = readVideoMetadataCache(
                cacheKey = cacheKey,
                freshOnly = false,
                freshnessTtlMs = REDDIT_RSS_MOTION_CACHE_TTL_MS,
            ) ?: throw e
            RedditRssMotionPage(
                result = stale.result,
                groupKey = group.key,
                nextAfter = if (stale.pageExhausted != null) {
                    stale.nextAfter
                } else {
                    redditAfterToken(stale.result.items.lastOrNull()?.id)
                },
                exhausted = stale.pageExhausted ?: false,
            )
        }
    }

    private fun readPixabayVideoCache(
        cacheKey: String,
        freshOnly: Boolean,
        freshnessTtlMs: Long = PIXABAY_VIDEO_CACHE_TTL_MS,
    ): PixabayVideoMetadataResult? =
        readVideoMetadataCache(cacheKey, freshOnly, freshnessTtlMs)?.result

    private fun readVideoMetadataCache(
        cacheKey: String,
        freshOnly: Boolean,
        freshnessTtlMs: Long,
    ): CachedPixabayVideoMetadata? =
        decodePixabayVideoCache(
            raw = pixabayVideoCache.readString(cacheKey),
            nowMs = System.currentTimeMillis(),
            requireFresh = freshOnly,
            freshnessTtlMs = freshnessTtlMs,
        )

    private fun writePixabayVideoCache(
        cacheKey: String,
        result: PixabayVideoMetadataResult,
        nextAfter: String? = null,
        pageExhausted: Boolean? = null,
    ) {
        pixabayVideoCache.writeString(
            cacheKey,
            encodePixabayVideoCache(
                CachedPixabayVideoMetadata(
                    result = result,
                    cachedAtMs = System.currentTimeMillis(),
                    nextAfter = nextAfter,
                    pageExhausted = pageExhausted,
                ),
            ),
        )
    }

    private fun rememberPixabayVideoMetadata(result: PixabayVideoMetadataResult) {
        streamUrls.putAll(result.streamUrls)
        if (result.streamUrls.isNotEmpty()) {
            _resolvedIds.update { it + result.streamUrls.keys }
        }
    }

    private fun cacheVisibleVideoFeed(snapshot: VideoWallpapersState) {
        val items = _state.value.items.take(MAX_CACHED_VIDEO_FEED_ITEMS)
        val urls = items.mapNotNull { item -> streamUrls[item.id]?.let { item.id to it } }.toMap()
        if (urls.isEmpty()) return
        writePixabayVideoCache(
            cacheKey = videoFeedCacheKey(snapshot.searchQuery, snapshot.orientation, snapshot.focusFilter),
            result = PixabayVideoMetadataResult(
                items = items.filter { it.id in urls },
                streamUrls = urls,
            ),
        )
    }

    private fun activePixabayVideoRateLimitUntilMs(): Long {
        val persisted = pixabayVideoCache.readLong(PIXABAY_VIDEO_RATE_LIMITED_UNTIL_KEY)
        pixabayVideoRateLimitedUntilMs = maxOf(pixabayVideoRateLimitedUntilMs, persisted)
        return pixabayVideoRateLimitedUntilMs
    }

    private fun updatePixabayVideoRateLimit(backoffMs: Long) {
        val untilMs = System.currentTimeMillis() + backoffMs
        pixabayVideoRateLimitedUntilMs = maxOf(pixabayVideoRateLimitedUntilMs, untilMs)
        pixabayVideoCache.writeLong(PIXABAY_VIDEO_RATE_LIMITED_UNTIL_KEY, pixabayVideoRateLimitedUntilMs)
    }

}
