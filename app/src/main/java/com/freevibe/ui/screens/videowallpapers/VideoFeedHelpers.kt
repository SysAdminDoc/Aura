package com.freevibe.ui.screens.videowallpapers

import com.freevibe.data.legal.ProviderAction
import com.freevibe.data.legal.ProviderBuild
import com.freevibe.data.legal.ProviderChannel
import com.freevibe.data.legal.currentProviderBuild
import com.freevibe.data.legal.currentProviderChannel
import com.freevibe.data.legal.isProviderActionPermitted
import com.freevibe.data.legal.isProviderActionPermittedIn
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.accepts
import com.freevibe.data.model.feedSignature
import com.freevibe.data.remote.pixabay.PixabayVideo
import com.freevibe.data.repository.RedditRssMediaEntry
import com.freevibe.data.repository.YouTubeVideoMetadata
import com.freevibe.data.repository.pixabayRateLimitBackoffMillis
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Properties
import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val PIXABAY_VIDEO_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
internal const val REDDIT_RSS_MOTION_CACHE_TTL_MS = 2 * 60 * 60 * 1000L
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

internal fun filterVideoMetadataForArtifact(
    result: PixabayVideoMetadataResult,
    build: ProviderBuild = currentProviderBuild,
    channel: ProviderChannel = currentProviderChannel,
): PixabayVideoMetadataResult {
    val allowedItems = result.items.filter { item ->
        val source = item.providerSource()
        isProviderActionPermittedIn(source, ProviderAction.BROWSE, build, channel) &&
            isProviderActionPermittedIn(source, ProviderAction.PREVIEW, build, channel)
    }
    val allowedIds = allowedItems.mapTo(hashSetOf()) { it.id }
    return PixabayVideoMetadataResult(
        items = allowedItems,
        streamUrls = result.streamUrls.filterKeys { it in allowedIds },
    )
}

internal fun filterVideoMetadataForCurrentArtifact(
    result: PixabayVideoMetadataResult,
): PixabayVideoMetadataResult = filterVideoMetadataForArtifact(result)

internal fun canPreviewVideoInCurrentArtifact(item: VideoWallpaperItem): Boolean =
    isProviderActionPermitted(item.providerSource(), ProviderAction.PREVIEW)

internal data class RedditMotionFeedGroup(
    val key: String,
    val subreddits: List<String>,
    val sort: String = "new",
    val timeWindow: String = "week",
)

internal data class RedditMotionFeedSelection(
    val group: RedditMotionFeedGroup,
    val after: String?,
    val count: Int,
    val nextSubIndex: Int,
)

/**
 * Splits [preset] into two-subreddit requests, so one busy subreddit can't bury the others. Each key
 * carries the whole preset, so cursors and cached pages never cross presets.
 */
internal fun resolveRedditMotionFeedGroups(
    preset: RedditFeedPreset,
    showAdultContent: Boolean = false,
): List<RedditMotionFeedGroup> {
    val presetSignature = preset.feedSignature(showAdultContent)
    return preset.subreddits.chunked(2).mapIndexed { index, group ->
        val signature = "$presetSignature|${group.joinToString("+").lowercase(java.util.Locale.ROOT)}"
        RedditMotionFeedGroup(
            key = "group_${index}_${signature.hashCode()}",
            subreddits = group,
            sort = preset.sort,
            timeWindow = preset.timeWindow,
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
    return "reddit_rss_motion_v3_${group.key}_$cursor"
}

internal fun redditRssMotionUrl(
    group: RedditMotionFeedGroup,
    after: String?,
    count: Int = if (after == null) 0 else 100,
): String = "https://www.reddit.com/r/${group.subreddits.joinToString("+")}/${group.sort}/.rss"
    .toHttpUrl()
    .newBuilder()
    .addQueryParameter("limit", "100")
    .apply {
        if (group.sort == "top") addQueryParameter("t", group.timeWindow)
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

private val REDDIT_HOSTED_VIDEO = Regex("^https://v\\.redd\\.it/([A-Za-z0-9]+)(?:[/?].*)?$", RegexOption.IGNORE_CASE)
private val DASH_DURATION = Regex("""mediaPresentationDuration\s*=\s*"P(?:\d+D)?T(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?""")

/** The v.redd.it ID of a Reddit-hosted video. GIFs and other hosts have none, so their length stays unknown. */
internal fun redditHostedVideoId(mediaUrl: String): String? =
    REDDIT_HOSTED_VIDEO.matchEntire(mediaUrl.trim())?.groupValues?.get(1)

/** Whole seconds from a DASH manifest's mediaPresentationDuration, or null when it doesn't give one. */
internal fun dashManifestDurationSeconds(manifest: String): Int? {
    val (hours, minutes, seconds) = DASH_DURATION.find(manifest)?.destructured ?: return null
    val total = (hours.toDoubleOrNull() ?: 0.0) * 3600 + (minutes.toDoubleOrNull() ?: 0.0) * 60 + (seconds.toDoubleOrNull() ?: 0.0)
    return if (total > 0) total.toInt().coerceAtLeast(1) else null
}

/** The entries [preset] keeps. [lengths] holds seconds for clips whose length was read; the rest pass the minimum. */
internal fun redditMotionEntriesFor(
    entries: List<RedditRssMediaEntry>,
    preset: RedditFeedPreset?,
    showAdultContent: Boolean,
    lengths: Map<String, Int> = emptyMap(),
): List<RedditRssMediaEntry> = entries.filter { entry ->
    preset?.accepts(entry.title, entry.width, entry.height, (lengths[entry.id] ?: 0).toLong(), showAdultContent) != false
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
    fallbackTitle: String,
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
                    .ifBlank { fallbackTitle },
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
