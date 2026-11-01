package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.util.Log
import com.freevibe.data.legal.searchReach
import com.freevibe.data.local.PixabayVideoCacheStore
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.activePreset
import com.freevibe.data.remote.RequestPurpose
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.purpose
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.RedditRssMediaEntry
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.repository.createLegacyCompatibleYouTubeSearchHandler
import com.freevibe.data.repository.parseRedditRssPage
import com.freevibe.service.SourceMetrics
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private const val REDDIT_CLIP_LENGTH_PARALLELISM = 4
// A clip's DASH manifest is a few KB, and the duration sits in its opening tag.
private const val REDDIT_CLIP_MANIFEST_MAX_BYTES = 64L * 1024L
private const val PIXABAY_VIDEO_RATE_LIMITED_UNTIL_KEY = "pixabay_video_rate_limited_until_ms"
private const val YOUTUBE_VIDEO_METADATA_PROBE_LIMIT = 30
private const val MAX_CACHED_VIDEO_FEED_ITEMS = 120
// Another feed can get to Reddit's one request a minute first, so a load may lose its turn a time or two.
private const val MAX_REDDIT_BUDGET_WAITS = 3

private data class RedditRssMotionPage(
    val result: PixabayVideoMetadataResult,
    val groupKey: String,
    val nextAfter: String?,
    val exhausted: Boolean,
    /** Reddit's request budget was spent and no cached page could stand in, so this page is still to come. */
    val deferred: Boolean = false,
)

private class RedditFeedRateLimited : java.io.IOException("Reddit RSS rate limit")

internal class VideoFeedActions(
    private val context: Context,
    private val youtubeRepo: YouTubeRepository,
    private val redditRepo: RedditRepository,
    private val pexelsApi: PexelsApi,
    private val pixabayApi: PixabayApi,
    private val prefs: PreferencesManager,
    private val okHttpClient: OkHttpClient,
    private val sourceMetrics: SourceMetrics,
    private val state: MutableStateFlow<VideoWallpapersState>,
    private val resolvedIds: MutableStateFlow<Set<String>>,
    private val streamUrls: MutableMap<String, String>,
    private val ensureStreamResolved: (VideoWallpaperItem) -> Unit,
    private val rememberStreamUrl: (VideoWallpaperItem, String) -> Unit,
) {
    private val pixabayVideoCache = PixabayVideoCacheStore(context)
    @Volatile
    private var pixabayVideoRateLimitedUntilMs: Long = 0L

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

    /**
     * One load of the feed: the body the ViewModel runs inside its load job. [scope] is that job's scope,
     * so the stream pre-resolves launched at the end belong to the job the way they did before the split.
     * [redditWaits] counts the passes this load has spent waiting on Reddit. Those later passes only
     * fetch the page that had to wait, since the other sources have already answered.
     */
    suspend fun loadBatch(loadMore: Boolean, scope: CoroutineScope, redditWaits: Int = 0) {
    val redditOnly = redditWaits > 0
    if (!loadMore) {
        state.update {
            it.copy(
                isLoading = !it.isRefreshing && it.items.isEmpty(),
                error = null,
                degradedSources = if (redditOnly) it.degradedSources else emptyList(),
                redditWaiting = redditOnly && it.redditWaiting,
            )
        }
    }

    val s = state.value
    val searchQ = s.searchQuery.ifBlank { null }
    if (!loadMore && !s.isRefreshing && s.items.isEmpty()) {
        readPixabayVideoCache(
            cacheKey = videoFeedCacheKey(s.searchQuery, s.orientation, s.focusFilter),
            freshOnly = true,
        )?.let { cachedFeed ->
            val warmFeed = cachedFeed.copy(
                items = rankVideoWallpapers(cachedFeed.items, s.focusFilter, s.orientation),
            ).let(::filterVideoMetadataForCurrentArtifact)
            rememberPixabayVideoMetadata(warmFeed)
            state.update { current ->
                current.copy(items = warmFeed.items, isLoading = false)
            }
            warmFeed.items.take(2).forEach(ensureStreamResolved)
            if (com.freevibe.BuildConfig.DEBUG) {
                Log.d("VideoWP", "Warm feed ready: ${warmFeed.items.size} cached items")
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
    // Same rule Universal Search uses: Pexels alone can't fill the feed, and keyless
    // providers are skipped, so say so instead of showing an empty feed with no reason.
    val videoReach = com.freevibe.data.legal.ProviderSetup(
        enabled = mapOf(
            com.freevibe.data.model.ContentSource.REDDIT to redditEnabled,
            com.freevibe.data.model.ContentSource.YOUTUBE to youtubeEnabled,
            com.freevibe.data.model.ContentSource.PEXELS to pexelsEnabled,
            com.freevibe.data.model.ContentSource.PIXABAY to pixabayEnabled,
        ),
        keyed = buildSet {
            if (prefs.pexelsApiKey.first().isNotBlank()) add(com.freevibe.data.model.ContentSource.PEXELS)
            if (prefs.pixabayApiKey.first().isNotBlank()) add(com.freevibe.data.model.ContentSource.PIXABAY)
        },
    ).searchReach(com.freevibe.data.legal.ProviderMediaType.VIDEO)
    val showAdultContent = redditEnabled && prefs.showNsfwContent.first()
    val redditPreset = if (redditEnabled) {
        prefs.redditFeedPresets.first().activePreset(RedditFeedMediaType.VIDEO)
    } else {
        null
    }
    val redditFeedGroups = redditPreset?.let { resolveRedditMotionFeedGroups(it, showAdultContent) }.orEmpty()
    val redditFeedSelection = selectRedditMotionFeed(
        groups = redditFeedGroups,
        startIndex = s.redditSubIndex,
        afters = s.redditAfters,
    )
    var loadedRedditPage: RedditRssMotionPage? = null

    kotlinx.coroutines.supervisorScope {
        // 1. Pexels
        val pexelsJob = async(Dispatchers.IO) {
            if (redditOnly) return@async emptyList<VideoWallpaperItem>()
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
                        rememberStreamUrl(item, it.link)
                        resolvedIds.update { it + item.id }
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
                    preset = redditPreset,
                    showAdultContent = showAdultContent,
                )
                if (page.deferred && redditWaits >= MAX_REDDIT_BUDGET_WAITS) {
                    throw java.io.IOException("Reddit's request budget stayed spent")
                }
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
            if (redditOnly) return@async emptyList<VideoWallpaperItem>()
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
            if (redditOnly) return@async emptyList<VideoWallpaperItem>()
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

    val redditDeferred = loadedRedditPage?.deferred == true
    // Nothing to show yet only because Reddit asked to wait, so this batch says nothing about the catalog.
    val waitingOnReddit = redditDeferred && mixed.isEmpty()
    val loadProgress = if (waitingOnReddit) {
        VideoLoadProgress(hasMore = true, emptyLoadCount = s.emptyLoadCount)
    } else {
        resolveVideoLoadProgress(
            previousEmptyLoadCount = s.emptyLoadCount,
            newItemCount = mixed.size,
        )
    }
    val sourceFailures = failedSources.toList().sorted()
    val sourceFailureSet = sourceFailures.toSet()
    val attemptedCount = attemptedSources.size
    // A pass for the waiting Reddit page only asks Reddit, so its failure says nothing about feed items already showing.
    val allAttemptedFailed = attemptedCount > 0 && sourceFailures.size == attemptedCount &&
        !(redditOnly && s.items.isNotEmpty())
    val preserveCurrentFeed = !loadMore && mixed.isEmpty() && state.value.items.isNotEmpty()

    currentCoroutineContext().ensureActive()
    state.update {
        it.copy(
            items = when {
                loadMore -> it.items + mixed
                preserveCurrentFeed -> it.items
                else -> mixed
            },
            isLoading = waitingOnReddit && !loadMore && it.items.isEmpty(),
            isLoadingMore = waitingOnReddit && loadMore,
            isRefreshing = false,
            redditWaiting = waitingOnReddit && it.items.isEmpty(),
            error = when {
                waitingOnReddit -> null
                videoReach.block != null && mixed.isEmpty() && !loadMore && !preserveCurrentFeed ->
                    context.getString(com.freevibe.R.string.video_wp_no_sources)
                allAttemptedFailed && preserveCurrentFeed ->
                    context.getString(com.freevibe.R.string.video_wp_sources_stale)
                allAttemptedFailed -> context.getString(com.freevibe.R.string.video_wp_sources_unavailable)
                sourceFailures.isNotEmpty() && mixed.isEmpty() && !preserveCurrentFeed ->
                    context.getString(com.freevibe.R.string.video_wp_sources_limited)
                else -> null
            },
            degradedSources = if (redditOnly) (it.degradedSources + sourceFailures).distinct().sorted() else sourceFailures,
            hasMore = loadProgress.hasMore,
            pexelsPage = if ("Pexels" !in sourceFailureSet && "Pexels" in attemptedSources) it.pexelsPage + 1 else it.pexelsPage,
            pixabayPage = if ("Pixabay" !in sourceFailureSet && "Pixabay" in attemptedSources) it.pixabayPage + 1 else it.pixabayPage,
            ytQueryIndex = if ("YouTube" !in sourceFailureSet && "YouTube" in attemptedSources) it.ytQueryIndex + 1 else it.ytQueryIndex,
            redditSubIndex = if ("Reddit" !in sourceFailureSet && "Reddit" in attemptedSources && !redditDeferred) {
                redditFeedSelection?.nextSubIndex ?: it.redditSubIndex
            } else {
                it.redditSubIndex
            },
            redditAfters = loadedRedditPage?.takeUnless { page -> page.deferred }?.let { page ->
                it.redditAfters + (page.groupKey to page.nextAfter.takeUnless { page.exhausted })
            } ?: it.redditAfters,
            emptyLoadCount = loadProgress.emptyLoadCount,
        )
    }
    if (com.freevibe.BuildConfig.DEBUG) {
        Log.d("VideoWP", "Feed ready: ${state.value.items.size} items; batch=${mixed.size}; loadMore=$loadMore")
    }
    cacheVisibleVideoFeed(s)
    state.value.items.take(3).forEach(ensureStreamResolved)

    // Pre-resolve YouTube URLs
    mixed.filter { youtubeEnabled && it.source == "YouTube" && !streamUrls.containsKey(it.id) }.take(4).let { ytItems ->
        val sem = Semaphore(5)
        ytItems.forEach { item ->
            scope.launch {
                sem.acquire()
                try {
                    youtubeRepo.getVideoStreamUrl(item.videoId)?.let { url ->
                        rememberStreamUrl(item, url)
                    }
                } catch (t: Throwable) {
                    t.rethrowIfCancelled()
                } finally {
                    sem.release()
                }
            }
        }
    }

    // Everything else is on screen by now. A newer load cancels this wait along with the rest of the job.
    if (redditDeferred) {
        delay(redditRepo.retryDelayMs().coerceAtLeast(250L) + 100L)
        loadBatch(
            loadMore = loadMore || state.value.items.isNotEmpty(),
            scope = scope,
            redditWaits = redditWaits + 1,
        )
    }
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
            mapPixabayVideosToMetadata(
                response.hits,
                context.getString(com.freevibe.R.string.video_wp_pixabay_fallback_title),
            )
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

    /** Seconds for each Reddit-hosted clip whose DASH manifest says. A clip that can't be read is left out and passes. */
    private suspend fun redditClipLengths(entries: List<RedditRssMediaEntry>): Map<String, Int> = coroutineScope {
        val permits = Semaphore(REDDIT_CLIP_LENGTH_PARALLELISM)
        entries.mapNotNull { entry -> redditHostedVideoId(entry.mediaUrl)?.let { entry.id to it } }
            .map { (entryId, videoId) ->
                async { permits.withPermit { readRedditClipLength(videoId)?.let { entryId to it } } }
            }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    private fun readRedditClipLength(videoId: String): Int? = try {
        val request = Request.Builder().purpose(RequestPurpose.RESOLVE)
            .url("https://v.redd.it/$videoId/DASHPlaylist.mpd")
            .header("User-Agent", "Aura/${com.freevibe.BuildConfig.VERSION_NAME} open-source wallpaper reader")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null
            else response.body?.source()?.let { body ->
                body.request(REDDIT_CLIP_MANIFEST_MAX_BYTES)
                dashManifestDurationSeconds(body.buffer.snapshot().utf8())
            }
        }
    } catch (e: java.io.IOException) {
        if (com.freevibe.BuildConfig.DEBUG) Log.w("VideoWP", "Reddit clip length unread for $videoId: ${e.message}")
        null
    }

    private suspend fun loadRedditRssMotionMetadata(
        group: RedditMotionFeedGroup,
        after: String?,
        count: Int,
        preset: RedditFeedPreset?,
        showAdultContent: Boolean,
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
        if (!redditRepo.tryClaimRssRequest()) {
            return staleRedditMotionPage(group, cacheKey) ?: deferredRedditMotionPage(group)
        }
        return try {
            val request = Request.Builder().purpose(RequestPurpose.CATALOG)
                .url(redditRssMotionUrl(group, after, count))
                .header(
                    "User-Agent",
                    "Aura/${com.freevibe.BuildConfig.VERSION_NAME} open-source wallpaper reader",
                )
                .header("Accept", "application/atom+xml, application/xml;q=0.9")
                .build()
            val rssPage = sourceMetrics.measure("reddit") {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        redditRepo.recordRssRateLimit(response.headers)
                        throw RedditFeedRateLimited()
                    }
                    if (!response.isSuccessful) throw java.io.IOException("Reddit RSS HTTP ${response.code}")
                    val xml = response.body?.string().orEmpty()
                    if (xml.isBlank()) throw java.io.IOException("Reddit RSS returned an empty feed")
                    parseRedditRssPage(xml, group.subreddits.first())
                }
            }
            val urls = linkedMapOf<String, String>()
            val candidates = redditMotionEntriesFor(
                rssPage.entries.filter { it.isAnimated }.filter { entry -> junkPatterns.none { it.containsMatchIn(entry.title) } },
                preset,
                showAdultContent,
            )
            // The feed never lists a clip's length, so a minimum costs one small manifest read per Reddit video.
            val lengths = if ((preset?.minDurationSeconds ?: 0) > 0) redditClipLengths(candidates) else emptyMap()
            val items = redditMotionEntriesFor(candidates, preset, showAdultContent, lengths)
                .map { entry ->
                    val playbackUrl = redditPlayableMotionUrl(entry.mediaUrl)
                    VideoWallpaperItem(
                        id = "rd_${entry.id}",
                        title = entry.title,
                        thumbnailUrl = entry.thumbnailUrl,
                        source = "Reddit",
                        duration = lengths[entry.id]?.toLong() ?: 0L,
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
            staleRedditMotionPage(group, cacheKey)
                ?: if (e is RedditFeedRateLimited) deferredRedditMotionPage(group) else throw e
        }
    }

    private suspend fun staleRedditMotionPage(group: RedditMotionFeedGroup, cacheKey: String): RedditRssMotionPage? {
        val stale = readVideoMetadataCache(
            cacheKey = cacheKey,
            freshOnly = false,
            freshnessTtlMs = REDDIT_RSS_MOTION_CACHE_TTL_MS,
        ) ?: return null
        return RedditRssMotionPage(
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

    private fun deferredRedditMotionPage(group: RedditMotionFeedGroup) = RedditRssMotionPage(
        result = PixabayVideoMetadataResult(items = emptyList(), streamUrls = emptyMap()),
        groupKey = group.key,
        nextAfter = null,
        exhausted = false,
        deferred = true,
    )

    private suspend fun readPixabayVideoCache(
        cacheKey: String,
        freshOnly: Boolean,
        freshnessTtlMs: Long = PIXABAY_VIDEO_CACHE_TTL_MS,
    ): PixabayVideoMetadataResult? =
        readVideoMetadataCache(cacheKey, freshOnly, freshnessTtlMs)?.result

    private suspend fun readVideoMetadataCache(
        cacheKey: String,
        freshOnly: Boolean,
        freshnessTtlMs: Long,
    ): CachedPixabayVideoMetadata? {
        val raw = pixabayVideoCache.readString(cacheKey) ?: return null
        // A cached feed holds up to MAX_CACHED_VIDEO_FEED_ITEMS rows; keep the parse off the main thread.
        return withContext(Dispatchers.Default) {
            decodePixabayVideoCache(
                raw = raw,
                nowMs = System.currentTimeMillis(),
                requireFresh = freshOnly,
                freshnessTtlMs = freshnessTtlMs,
            )?.let { cached ->
                val filtered = filterVideoMetadataForCurrentArtifact(cached.result)
                if (cached.result.items.isNotEmpty() && filtered.items.isEmpty()) {
                    null
                } else {
                    cached.copy(result = filtered)
                }
            }
        }
    }

    private suspend fun writePixabayVideoCache(
        cacheKey: String,
        result: PixabayVideoMetadataResult,
        nextAfter: String? = null,
        pageExhausted: Boolean? = null,
    ) {
        val encoded = withContext(Dispatchers.Default) {
            encodePixabayVideoCache(
                CachedPixabayVideoMetadata(
                    result = result,
                    cachedAtMs = System.currentTimeMillis(),
                    nextAfter = nextAfter,
                    pageExhausted = pageExhausted,
                ),
            )
        }
        pixabayVideoCache.writeString(cacheKey, encoded)
    }

    private fun rememberPixabayVideoMetadata(result: PixabayVideoMetadataResult) {
        val filtered = filterVideoMetadataForCurrentArtifact(result)
        filtered.items.forEach { item ->
            filtered.streamUrls[item.id]?.let { url -> rememberStreamUrl(item, url) }
        }
    }

    private suspend fun cacheVisibleVideoFeed(snapshot: VideoWallpapersState) {
        val items = state.value.items.take(MAX_CACHED_VIDEO_FEED_ITEMS)
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

    private suspend fun activePixabayVideoRateLimitUntilMs(): Long {
        val persisted = pixabayVideoCache.readLong(PIXABAY_VIDEO_RATE_LIMITED_UNTIL_KEY)
        pixabayVideoRateLimitedUntilMs = maxOf(pixabayVideoRateLimitedUntilMs, persisted)
        return pixabayVideoRateLimitedUntilMs
    }

    private suspend fun updatePixabayVideoRateLimit(backoffMs: Long) {
        val untilMs = System.currentTimeMillis() + backoffMs
        pixabayVideoRateLimitedUntilMs = maxOf(pixabayVideoRateLimitedUntilMs, untilMs)
        pixabayVideoCache.writeLong(PIXABAY_VIDEO_RATE_LIMITED_UNTIL_KEY, pixabayVideoRateLimitedUntilMs)
    }
}
