package com.freevibe.data.repository

import com.freevibe.BuildConfig
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.accepts
import com.freevibe.data.model.activePreset
import com.freevibe.data.model.feedSignature
import com.freevibe.data.model.isMarkedAdult
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.service.SourceMetrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val SOURCE_REDDIT = "reddit"
private const val REDDIT_RSS_COOLDOWN_MS = 60_000L
private const val REDDIT_RSS_PAGE_SIZE = 100
private const val REDDIT_END_CURSOR = "__END__"

private data class RedditWallpaperPage(
    val items: List<Wallpaper>,
    val hasMore: Boolean,
)

/** Which posts a feed keeps, and the key its cache rows and cursors live under. */
private class RedditEntryFilter(
    val feedKey: String,
    val accepts: (RedditRssMediaEntry) -> Boolean,
)

@Singleton
class RedditRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cacheManager: WallpaperCacheManager,
    private val sourceMetrics: SourceMetrics,
    private val prefs: PreferencesManager,
) {
    private val requestMutex = Mutex()
    private var lastNetworkAttemptAtMs = 0L
    private val nextAfterByFeed = ConcurrentHashMap<String, String>()
    private val deferredFeeds = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var forceRefreshRoot = false

    suspend fun getSubredditWallpapers(
        subreddit: String = "wallpapers",
        sort: String = "hot",
        timeRange: String = "week",
        after: String? = null,
    ): SearchResult<Wallpaper> {
        if (!isProviderEnabled()) return providerDisabledResult()
        val safeSubreddit = sanitizeSubreddit(subreddit)
        if (safeSubreddit.isBlank()) return emptyWallpaperResult()
        val showAdult = prefs.showNsfwContent.first()
        val items = loadRssWallpapers(
            subreddits = listOf(safeSubreddit),
            sort = sort,
            timeRange = timeRange,
            afterOverride = after,
            filter = RedditEntryFilter(
                feedKey = "${safeSubreddit.lowercase(Locale.ROOT)}|$sort|$timeRange|${if (showAdult) "adult" else "safe"}",
                accepts = { showAdult || !isMarkedAdult(it.title) },
            ),
        )
        return SearchResult(
            items = items.items,
            totalCount = -1,
            currentPage = 1,
            hasMore = items.hasMore,
        )
    }

    suspend fun searchSubreddit(
        subreddit: String = "wallpapers",
        query: String,
        after: String? = null,
    ): SearchResult<Wallpaper> {
        val result = getSubredditWallpapers(
            subreddit = subreddit,
            sort = "top",
            timeRange = "year",
            after = after,
        )
        if (query.isBlank()) return result
        val terms = query.lowercase(Locale.ROOT)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        val filtered = result.items.filter { wallpaper ->
            val searchable = buildString {
                append(wallpaper.category)
                append(' ')
                append(wallpaper.tags.joinToString(" "))
                append(' ')
                append(wallpaper.uploaderName)
            }.lowercase(Locale.ROOT)
            terms.all(searchable::contains)
        }
        return result.copy(items = filtered, totalCount = -1)
    }

    /**
     * Reddit-first mobile inventory backed by the public Atom listing, read through [preset] or the
     * preset active on the Reddit tab. Reddit omits a rel=next link, so [loadRssWallpapers] carries
     * the last raw `t3_...` entry into the next request's `after` query parameter.
     */
    suspend fun getMultiSubreddit(
        page: Int = 1,
        preset: RedditFeedPreset? = null,
    ): SearchResult<Wallpaper> {
        if (!isProviderEnabled()) return providerDisabledResult()
        val feed = preset ?: prefs.redditFeedPresets.first().activePreset(RedditFeedMediaType.WALLPAPER)
        val showAdult = prefs.showNsfwContent.first()
        val safeSubreddits = feed.subreddits.map(::sanitizeSubreddit)
            .filter { it.isNotBlank() }
            .distinct()
            .take(12)
        if (safeSubreddits.isEmpty()) return emptyWallpaperResult()
        val safePage = page.coerceAtLeast(1)
        val rssPage = loadRssWallpapers(
            subreddits = safeSubreddits,
            sort = feed.sort,
            timeRange = feed.timeWindow,
            page = safePage,
            filter = RedditEntryFilter(feed.feedSignature(showAdult)) { entry ->
                feed.accepts(entry.title, entry.width, entry.height, showAdultContent = showAdult)
            },
        )
        return SearchResult(
            items = rssPage.items,
            totalCount = -1,
            currentPage = safePage,
            hasMore = rssPage.hasMore,
        )
    }

    suspend fun getDailyTopWallpaper(): Wallpaper? {
        if (!isProviderEnabled()) {
            sourceMetrics.recordDisabled(SOURCE_REDDIT)
            return null
        }
        // Reuse the same first page as the home feed. This prevents a decorative
        // daily-pick request from consuming Reddit's conservative request budget just
        // before the content grid asks for its first page.
        return getMultiSubreddit(page = 1).items.firstOrNull()
    }

    fun resetPagination(forceRefresh: Boolean = false) {
        nextAfterByFeed.clear()
        deferredFeeds.clear()
        if (forceRefresh) forceRefreshRoot = true
    }

    fun hasDeferredRequest(): Boolean = deferredFeeds.isNotEmpty()

    suspend fun retryDelayMs(): Long {
        val persisted = prefs.getRedditRssNextAllowedAtMs()
        val inProcess = lastNetworkAttemptAtMs + REDDIT_RSS_COOLDOWN_MS
        return (maxOf(persisted, inProcess) - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private suspend fun loadRssWallpapers(
        subreddits: List<String>,
        sort: String,
        timeRange: String,
        page: Int = 1,
        afterOverride: String? = null,
        filter: RedditEntryFilter,
    ): RedditWallpaperPage {
        val normalizedSort = sort.lowercase(Locale.ROOT).takeIf { it in RSS_SORTS } ?: "top"
        val normalizedTimeRange = timeRange.lowercase(Locale.ROOT).takeIf { it in RSS_TIME_RANGES } ?: "month"
        val joinedSubreddits = subreddits.joinToString("+")
        val safePage = page.coerceAtLeast(1)
        val feedKey = filter.feedKey
        val normalizedOverride = afterOverride?.normalizeRedditAfter()
        val requestAfter = when {
            normalizedOverride != null -> normalizedOverride
            safePage <= 1 -> null
            else -> {
                val next = nextAfterByFeed[feedKey]
                    ?: prefs.getRedditRssNextCursor(feedKey.hashCode(), requestAfter = null)
                        .takeIf { it.isNotBlank() }
                when (next) {
                    REDDIT_END_CURSOR -> {
                        deferredFeeds.remove(feedKey)
                        return RedditWallpaperPage(items = emptyList(), hasMore = false)
                    }
                    null -> return RedditWallpaperPage(items = emptyList(), hasMore = true)
                    else -> next.normalizeRedditAfter()
                        ?: return RedditWallpaperPage(items = emptyList(), hasMore = false)
                }
            }
        }
        val cacheKey = redditPageCacheKey(feedKey, requestAfter)
        val staleBeforeFreshnessCheck = cacheManager.getStaleCached(cacheKey).orEmpty()
        val cachedMetadata = prefs.getRedditRssNextCursor(feedKey.hashCode(), requestAfter)
        val bypassFreshCache = normalizedOverride == null && safePage == 1 && forceRefreshRoot
        if (!bypassFreshCache && cachedMetadata.isNotBlank()) {
            cacheManager.getCached(cacheKey, ContentSource.REDDIT)?.let { cached ->
                deferredFeeds.remove(feedKey)
                rememberPageMetadata(feedKey, requestAfter, cachedMetadata)
                return RedditWallpaperPage(items = cached, hasMore = cachedMetadata != REDDIT_END_CURSOR)
            }
        }

        return requestMutex.withLock {
            val metadata = prefs.getRedditRssNextCursor(feedKey.hashCode(), requestAfter)
            if (!bypassFreshCache && metadata.isNotBlank()) {
                cacheManager.getCached(cacheKey, ContentSource.REDDIT)?.let { cached ->
                    deferredFeeds.remove(feedKey)
                    rememberPageMetadata(feedKey, requestAfter, metadata)
                    return@withLock RedditWallpaperPage(items = cached, hasMore = metadata != REDDIT_END_CURSOR)
                }
            }
            val stale = staleBeforeFreshnessCheck.ifEmpty { cacheManager.getStaleCached(cacheKey).orEmpty() }
            val now = System.currentTimeMillis()
            val nextAllowedAt = maxOf(
                lastNetworkAttemptAtMs + REDDIT_RSS_COOLDOWN_MS,
                prefs.getRedditRssNextAllowedAtMs(),
            )
            if (now < nextAllowedAt) {
                deferredFeeds.add(feedKey)
                if (metadata.isNotBlank()) rememberPageMetadata(feedKey, requestAfter, metadata)
                return@withLock RedditWallpaperPage(
                    items = stale,
                    hasMore = metadata.ifBlank { null } != REDDIT_END_CURSOR,
                )
            }
            deferredFeeds.remove(feedKey)
            lastNetworkAttemptAtMs = now
            prefs.setRedditRssNextAllowedAtMs(now + REDDIT_RSS_COOLDOWN_MS)
            if (requestAfter == null) forceRefreshRoot = false

            try {
                val rssPage = sourceMetrics.measure(SOURCE_REDDIT) {
                    fetchRss(
                        url = redditRssUrl(
                            subreddits = joinedSubreddits,
                            sort = normalizedSort,
                            timeRange = normalizedTimeRange,
                            after = requestAfter,
                            count = (safePage - 1) * REDDIT_RSS_PAGE_SIZE,
                        ),
                        fallbackSubreddit = subreddits.first(),
                    )
                }
                val nextCursor = rssPage.nextAfter
                    ?.takeIf { rssPage.rawEntryCount >= REDDIT_RSS_PAGE_SIZE }
                    ?: REDDIT_END_CURSOR
                prefs.setRedditRssNextCursor(feedKey.hashCode(), requestAfter, nextCursor)
                rememberPageMetadata(feedKey, requestAfter, nextCursor)
                val wallpapers = rssPage.entries
                    .asSequence()
                    .filterNot { it.isAnimated }
                    .filter(filter.accepts)
                    .map(::toWallpaper)
                    .distinctBy { it.id }
                    .toList()
                if (wallpapers.isNotEmpty()) {
                    cacheManager.cache(cacheKey, wallpapers)
                }
                RedditWallpaperPage(
                    items = wallpapers.ifEmpty { stale },
                    hasMore = nextCursor != REDDIT_END_CURSOR,
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is RedditRateLimitException) {
                    deferredFeeds.add(feedKey)
                    prefs.setRedditRssNextAllowedAtMs(
                        maxOf(
                            prefs.getRedditRssNextAllowedAtMs(),
                            System.currentTimeMillis() + error.retryAfterMs,
                        ),
                    )
                }
                if (stale.isNotEmpty()) {
                    if (metadata.isNotBlank()) rememberPageMetadata(feedKey, requestAfter, metadata)
                    RedditWallpaperPage(
                        items = stale,
                        hasMore = metadata.ifBlank { null } != REDDIT_END_CURSOR,
                    )
                } else if (error is RedditRateLimitException) {
                    RedditWallpaperPage(items = emptyList(), hasMore = true)
                } else {
                    throw error
                }
            }
        }
    }

    private suspend fun fetchRss(
        url: String,
        fallbackSubreddit: String,
    ): RedditRssPage = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Aura/${BuildConfig.VERSION_NAME} open-source wallpaper reader",
            )
            .header("Accept", "application/atom+xml, application/xml;q=0.9")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 429) {
                throw RedditRateLimitException(redditRetryAfterMs(response.headers))
            }
            if (!response.isSuccessful) throw IOException("Reddit RSS HTTP ${response.code}")
            val xml = response.body?.string().orEmpty()
            if (xml.isBlank()) throw IOException("Reddit RSS returned an empty feed")
            parseRedditRssPage(xml, fallbackSubreddit)
        }
    }

    private fun rememberPageMetadata(feedKey: String, requestAfter: String?, nextCursor: String) {
        if (nextCursor == REDDIT_END_CURSOR || nextCursor.normalizeRedditAfter() != null) {
            nextAfterByFeed.compute(feedKey) { _, activeCursor ->
                advanceRedditCursor(activeCursor, requestAfter, nextCursor)
            }
        }
    }

    private fun redditPageCacheKey(feedKey: String, requestAfter: String?): String =
        "reddit_rss_v4_${feedKey.hashCode()}_${requestAfter?.hashCode() ?: "root"}"

    private fun toWallpaper(entry: RedditRssMediaEntry): Wallpaper {
        val extension = entry.mediaUrl.substringBefore('?').substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
        return Wallpaper(
            id = "rd_${entry.id}",
            source = ContentSource.REDDIT,
            thumbnailUrl = entry.thumbnailUrl,
            fullUrl = entry.mediaUrl,
            width = entry.width,
            height = entry.height,
            category = entry.title,
            tags = listOf("reddit", entry.subreddit).filter { it.isNotBlank() },
            fileType = extension,
            sourcePageUrl = entry.sourcePageUrl,
            license = "Reddit",
            uploaderName = entry.author,
        )
    }

    private suspend fun isProviderEnabled(): Boolean = prefs.redditProviderEnabled.first()

    private fun providerDisabledResult(): SearchResult<Wallpaper> {
        sourceMetrics.recordDisabled(SOURCE_REDDIT)
        return emptyWallpaperResult()
    }

    private fun emptyWallpaperResult() = SearchResult<Wallpaper>(
        items = emptyList(),
        totalCount = 0,
        currentPage = 1,
        hasMore = false,
    )

    private fun sanitizeSubreddit(value: String): String = value
        .removePrefix("r/")
        .filter { it.isLetterOrDigit() || it == '_' }

    private companion object {
        val RSS_SORTS = setOf("hot", "new", "top", "controversial", "rising")
        val RSS_TIME_RANGES = setOf("hour", "day", "week", "month", "year", "all")
    }
}

internal fun redditRssUrl(
    subreddits: String,
    sort: String,
    timeRange: String,
    after: String? = null,
    count: Int = 0,
    limit: Int = REDDIT_RSS_PAGE_SIZE,
): String {
    val query = buildList {
        add("limit=${limit.coerceIn(1, REDDIT_RSS_PAGE_SIZE)}")
        if (sort == "top" || sort == "controversial") add("t=$timeRange")
        after?.normalizeRedditAfter()?.let { cursor ->
            add("count=${count.coerceAtLeast(0)}")
            add("after=$cursor")
        }
    }.joinToString("&")
    return "https://www.reddit.com/r/$subreddits/$sort/.rss?$query"
}

private fun String.normalizeRedditAfter(): String? = trim()
    .takeIf { it.matches(Regex("t3_[a-zA-Z0-9]+")) }

private class RedditRateLimitException(
    val retryAfterMs: Long,
) : IOException("Reddit RSS rate limit")

internal fun redditRetryAfterMs(headers: okhttp3.Headers): Long {
    val retryAfterMs = headers["Retry-After"]
        ?.trim()
        ?.toDoubleOrNull()
        ?.times(1_000.0)
        ?.toLong()
    val resetMs = headers["X-Ratelimit-Reset"]
        ?.trim()
        ?.toDoubleOrNull()
        ?.times(1_000.0)
        ?.toLong()
    return maxOf(retryAfterMs ?: 0L, resetMs ?: 0L, REDDIT_RSS_COOLDOWN_MS)
}

internal fun advanceRedditCursor(
    activeCursor: String?,
    requestAfter: String?,
    responseNextCursor: String,
): String = if (activeCursor == null || activeCursor == requestAfter) {
    responseNextCursor
} else {
    activeCursor
}
