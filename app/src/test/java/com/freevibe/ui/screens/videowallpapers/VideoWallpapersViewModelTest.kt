package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.content.SharedPreferences
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.pixabay.PixabayVideo
import com.freevibe.data.remote.pixabay.PixabayVideoFile
import com.freevibe.data.remote.pixabay.PixabayVideoFiles
import com.freevibe.data.remote.pixabay.PixabayVideoResponse
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.createLegacyCompatibleYouTubeSearchHandler
import com.freevibe.data.repository.parseRedditRssPage
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.repository.YouTubeVideoMetadata
import com.freevibe.data.repository.sanitizeVoteKey
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VideoPreviewCache
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.YouTubeYtDlpRequestFactory
import com.freevibe.service.YtDlpUpdateManager
import com.freevibe.util.rethrowIfCancelled
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class VideoWallpapersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `warm cache keeps one cold load in flight and later pagination appends`() = runTest(dispatcher) {
        val context = mockk<Context>()
        val cachePreferences = mockk<SharedPreferences>()
        val cacheEditor = mockk<SharedPreferences.Editor>()
        val cacheValues = mutableMapOf<String, Any?>()
        every { context.applicationContext } returns context
        every {
            context.getSharedPreferences("freevibe_pixabay_video_cache", Context.MODE_PRIVATE)
        } returns cachePreferences
        every { cachePreferences.getString(any(), any()) } answers {
            cacheValues[firstArg<String>()] as? String ?: secondArg()
        }
        every { cachePreferences.getLong(any(), any()) } answers {
            cacheValues[firstArg<String>()] as? Long ?: secondArg()
        }
        every { cachePreferences.edit() } returns cacheEditor
        every { cacheEditor.clear() } answers { cacheValues.clear(); cacheEditor }
        every { cacheEditor.putString(any(), any()) } answers {
            cacheValues[firstArg<String>()] = secondArg<String?>()
            cacheEditor
        }
        every { cacheEditor.putLong(any(), any()) } answers {
            cacheValues[firstArg<String>()] = secondArg<Long>()
            cacheEditor
        }
        every { cacheEditor.apply() } just runs
        every { cacheEditor.commit() } returns true

        val cachedItem = VideoWallpaperItem(
            id = "cached",
            title = "Cached loop",
            thumbnailUrl = "https://example.com/cached.jpg",
            source = "Pixabay",
            duration = 12,
        )
        cacheValues[videoFeedCacheKey("", OrientationFilter.PORTRAIT, VideoFocusFilter.BEST)] =
            encodePixabayVideoCache(
                CachedPixabayVideoMetadata(
                    result = PixabayVideoMetadataResult(
                        items = listOf(cachedItem),
                        streamUrls = mapOf("cached" to "file:///cached.mp4"),
                    ),
                    cachedAtMs = System.currentTimeMillis(),
                ),
            )

        val prefs = mockk<PreferencesManager>()
        every { prefs.youtubeProviderEnabled } returns flowOf(false)
        every { prefs.redditProviderEnabled } returns flowOf(false)
        every { prefs.pexelsProviderEnabled } returns flowOf(false)
        every { prefs.pixabayProviderEnabled } returns flowOf(true)
        every { prefs.redditVideoSubreddits } returns flowOf("")
        every { prefs.pexelsApiKey } returns flowOf("")
        every { prefs.pixabayApiKey } returns flowOf("test-key")

        val pixabayApi = mockk<PixabayApi>()
        val requestCount = AtomicInteger(0)
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        val firstRequestFinished = CompletableDeferred<Unit>()
        val secondRequestStarted = CompletableDeferred<Unit>()
        val secondRequestFinished = CompletableDeferred<Unit>()
        coEvery {
            pixabayApi.searchVideos(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } coAnswers {
            when (requestCount.incrementAndGet()) {
                1 -> {
                    firstRequestStarted.complete(Unit)
                    releaseFirstRequest.await()
                    firstRequestFinished.complete(Unit)
                    PixabayVideoResponse()
                }
                else -> {
                    secondRequestStarted.complete(Unit)
                    val response = PixabayVideoResponse(
                        hits = listOf(
                            PixabayVideo(
                                id = 42,
                                duration = 12,
                                pictureId = "append",
                                videos = PixabayVideoFiles(
                                    medium = PixabayVideoFile(
                                        url = "https://example.com/appended.mp4",
                                        width = 1080,
                                        height = 1920,
                                    ),
                                ),
                            ),
                        ),
                    )
                    secondRequestFinished.complete(Unit)
                    response
                }
            }
        }

        val viewModel = VideoWallpapersViewModel(
            context = context,
            youtubeRepo = mockk<YouTubeRepository>(relaxed = true),
            pexelsApi = mockk<PexelsApi>(relaxed = true),
            pixabayApi = pixabayApi,
            prefs = prefs,
            okHttpClient = mockk(relaxed = true),
            videoWallpaperStorage = mockk<VideoWallpaperStorage>(relaxed = true),
            sourceMetrics = SourceMetrics(),
            ytDlpUpdateManager = mockk<YtDlpUpdateManager>(relaxed = true),
            ytDlpRequestFactory = mockk<YouTubeYtDlpRequestFactory>(relaxed = true),
            videoPreviewCache = mockk<VideoPreviewCache>(relaxed = true),
            voteRepo = mockk<VoteRepository>(relaxed = true),
        )

        runCurrent()
        firstRequestStarted.await()
        runCurrent()
        assertEquals(listOf("cached"), viewModel.state.value.items.map { it.id })

        viewModel.loadMore()
        runCurrent()
        assertEquals(1, requestCount.get())

        releaseFirstRequest.complete(Unit)
        firstRequestFinished.await()
        awaitFeedJobCompletion(viewModel)
        advanceUntilIdle()
        assertEquals(listOf("cached"), viewModel.state.value.items.map { it.id })
        assertTrue("Unexpected state after cold load: ${viewModel.state.value}", viewModel.state.value.hasMore)
        assertFalse("Cold load still marked active: ${viewModel.state.value}", viewModel.state.value.isLoading)
        assertFalse("Pagination still marked active: ${viewModel.state.value}", viewModel.state.value.isLoadingMore)

        viewModel.loadMore()
        runCurrent()
        assertTrue("Pagination did not start: ${viewModel.state.value}", viewModel.state.value.isLoadingMore)
        secondRequestStarted.await()
        secondRequestFinished.await()
        awaitFeedJobCompletion(viewModel)
        advanceUntilIdle()

        assertEquals(2, requestCount.get())
        assertEquals(listOf("cached", "pbv_42"), viewModel.state.value.items.map { it.id })
        cacheValues.clear()
    }

    private suspend fun awaitFeedJobCompletion(viewModel: VideoWallpapersViewModel) {
        val loadJobField = VideoWallpapersViewModel::class.java
            .getDeclaredField("loadJob")
            .apply { isAccessible = true }
        while (true) {
            val job = loadJobField.get(viewModel) as? Job ?: return
            job.join()
            if (loadJobField.get(viewModel) == null) return
        }
    }

    @Test
    fun `resolveVideoLoadProgress keeps discovery open through a few empty batches then stops`() {
        val first = resolveVideoLoadProgress(previousEmptyLoadCount = 0, newItemCount = 0)
        val second = resolveVideoLoadProgress(previousEmptyLoadCount = first.emptyLoadCount, newItemCount = 0)
        val third = resolveVideoLoadProgress(previousEmptyLoadCount = second.emptyLoadCount, newItemCount = 0)

        // Rotating queries mean a single empty batch is not the end, so early empties stay loadable.
        assertTrue(first.hasMore)
        assertTrue(second.hasMore)
        assertTrue(third.hasMore)
        assertEquals(3, third.emptyLoadCount)

        // ...but once the empty streak reaches the backstop, pagination must terminate so the
        // auto-fill effect cannot spin loadMore forever on an exhausted/offline catalog.
        val atLimit = resolveVideoLoadProgress(
            previousEmptyLoadCount = MAX_CONSECUTIVE_EMPTY_VIDEO_LOADS - 1,
            newItemCount = 0,
        )
        assertEquals(MAX_CONSECUTIVE_EMPTY_VIDEO_LOADS, atLimit.emptyLoadCount)
        assertFalse(atLimit.hasMore)
    }

    @Test
    fun `resolveVideoLoadProgress resets empty streak after new results`() {
        val progress = resolveVideoLoadProgress(previousEmptyLoadCount = 2, newItemCount = 4)

        assertTrue(progress.hasMore)
        assertEquals(0, progress.emptyLoadCount)
    }

    @Test
    fun `resolvePexelsVideoQuery starts from first fallback query on page one`() {
        val query = resolvePexelsVideoQuery(
            page = 1,
            searchQuery = null,
            fallbackQueries = listOf("mobile wallpaper", "phone wallpaper", "abstract background"),
        )

        assertEquals("mobile wallpaper", query)
    }

    @Test
    fun `resolvePexelsOrientationParam omits orientation for all mode`() {
        assertNull(resolvePexelsOrientationParam(OrientationFilter.ALL))
        assertEquals("portrait", resolvePexelsOrientationParam(OrientationFilter.PORTRAIT))
        assertEquals("landscape", resolvePexelsOrientationParam(OrientationFilter.LANDSCAPE))
    }

    @Test
    fun `keepPexelsVideosAsEnhancement drops pexels-only batches`() {
        val items = keepPexelsVideosAsEnhancement(
            listOf(
                VideoWallpaperItem(
                    id = "px_1",
                    title = "by Mira",
                    thumbnailUrl = "https://example.com/px.jpg",
                    source = "Pexels",
                ),
            ),
        )

        assertTrue(items.isEmpty())
    }

    @Test
    fun `keepPexelsVideosAsEnhancement keeps pexels when fallback sources are present`() {
        val items = keepPexelsVideosAsEnhancement(
            listOf(
                VideoWallpaperItem(
                    id = "px_1",
                    title = "by Mira",
                    thumbnailUrl = "https://example.com/px.jpg",
                    source = "Pexels",
                ),
                VideoWallpaperItem(
                    id = "pbv_1",
                    title = "Aurora loop",
                    thumbnailUrl = "https://example.com/pb.jpg",
                    source = "Pixabay",
                ),
            ),
        )

        assertEquals(listOf("Pexels", "Pixabay"), items.map { it.source })
    }

    @Test
    fun `resolvePixabayVideoFetchSpec uses animation catalog when no search query exists`() {
        assertEquals(
            PixabayVideoFetchSpec(query = "abstract loop", videoType = "animation", page = 2),
            resolvePixabayVideoFetchSpec(searchQuery = null, page = 2),
        )
        assertEquals(30, resolvePixabayVideoFetchSpec(searchQuery = null, page = 2).perPage)
        assertEquals(
            PixabayVideoFetchSpec(query = "aurora", videoType = "all", page = 3),
            resolvePixabayVideoFetchSpec(searchQuery = "aurora", page = 3),
        )
    }

    @Test
    fun `video feed cache keys separate orientation focus and search`() {
        val portrait = videoFeedCacheKey("", OrientationFilter.PORTRAIT, VideoFocusFilter.BEST)
        val landscape = videoFeedCacheKey("", OrientationFilter.LANDSCAPE, VideoFocusFilter.BEST)
        val battery = videoFeedCacheKey("", OrientationFilter.PORTRAIT, VideoFocusFilter.LOW_BATTERY)
        val search = videoFeedCacheKey("ocean", OrientationFilter.PORTRAIT, VideoFocusFilter.BEST)

        assertNotEquals(portrait, landscape)
        assertNotEquals(portrait, battery)
        assertNotEquals(portrait, search)
        assertTrue(portrait.startsWith("video_feed_v2_"))
    }

    @Test
    fun `reddit motion feed groups preserve validated source priority`() {
        val groups = resolveRedditMotionFeedGroups(
            "livewallpapers,LiveWallpaper,Cinemagraphs,perfectloops,livewallpapers,not-valid!",
        )
        val customGroups = resolveRedditMotionFeedGroups("CustomLoops,OLED_Motion,PixelCycles")

        assertEquals(
            listOf(
                listOf("livewallpapers", "Cinemagraphs"),
                listOf("perfectloops", "phonewallpapers"),
                listOf("AnimatedPixelArt", "LivingBackgrounds"),
                listOf("wallpaperengine"),
            ),
            groups.map { it.subreddits },
        )
        assertEquals(
            listOf(listOf("CustomLoops", "OLED_Motion"), listOf("PixelCycles")),
            customGroups.map { it.subreddits },
        )
    }

    @Test
    fun `reddit motion feed url requests one hundred and continues after t3 cursor`() {
        val group = resolveRedditMotionFeedGroups("Cinemagraphs,perfectloops").single()
        val after = redditAfterToken("rd_abc123")
        val url = Request.Builder()
            .url(redditRssMotionUrl(group, after))
            .build()
            .url

        assertEquals("100", url.queryParameter("limit"))
        assertEquals("100", url.queryParameter("count"))
        assertEquals("t3_abc123", url.queryParameter("after"))
        assertTrue(url.encodedPath.contains("Cinemagraphs+perfectloops"))
        assertTrue(url.encodedPath.endsWith("/new/.rss"))

        val deeperPage = Request.Builder()
            .url(redditRssMotionUrl(group, after, count = 300))
            .build()
            .url
        assertEquals("300", deeperPage.queryParameter("count"))
    }

    @Test
    fun `bare reddit video urls resolve to public hls playlists`() {
        assertEquals(
            "https://v.redd.it/abc123/HLSPlaylist.m3u8",
            redditPlayableMotionUrl("https://v.redd.it/abc123"),
        )
        assertEquals(
            "https://i.redd.it/loop.gif",
            redditPlayableMotionUrl("https://i.redd.it/loop.gif"),
        )
        assertTrue(isHlsMotionUrl("https://v.redd.it/abc123/HLSPlaylist.m3u8?source=fallback"))
        assertFalse(isHlsMotionUrl("https://i.redd.it/loop.gif"))
    }

    @Test
    fun `reddit motion cache keys separate feed groups and cursors`() {
        val groups = resolveRedditMotionFeedGroups(
            "livewallpapers,LiveWallpaper,Cinemagraphs,perfectloops",
        )

        val firstPage = redditRssMotionCacheKey(groups[0], after = null)
        val continued = redditRssMotionCacheKey(groups[0], after = "t3_abc123")
        val otherGroup = redditRssMotionCacheKey(groups[1], after = null)

        assertNotEquals(firstPage, continued)
        assertNotEquals(firstPage, otherGroup)
        assertEquals("t3_abc123", redditAfterToken("t3_abc123"))
        assertNull(redditAfterToken("not/a/post"))
    }

    @Test
    fun `reddit motion selection skips exhausted groups without restarting them`() {
        val groups = resolveRedditMotionFeedGroups(
            "livewallpapers,Cinemagraphs,perfectloops,phonewallpapers",
        )
        val selection = selectRedditMotionFeed(
            groups = groups,
            startIndex = 0,
            afters = mapOf(
                groups[0].key to null,
                groups[1].key to "t3_next",
            ),
        )

        assertEquals(groups[1], selection?.group)
        assertEquals("t3_next", selection?.after)
        assertEquals(2, selection?.nextSubIndex)
        assertNull(
            selectRedditMotionFeed(
                groups = groups,
                startIndex = 0,
                afters = groups.associate { it.key to null },
            ),
        )
    }

    @Test
    fun `reddit motion page ends on short raw listing or missing cursor`() {
        assertFalse(isRedditMotionPageExhausted(rawEntryCount = 100, nextAfter = "t3_next"))
        assertTrue(isRedditMotionPageExhausted(rawEntryCount = 99, nextAfter = "t3_last"))
        assertTrue(isRedditMotionPageExhausted(rawEntryCount = 100, nextAfter = null))
    }

    @Test
    fun `mapPixabayVideosToMetadata filters unsuitable videos and stores stream urls`() {
        val result = mapPixabayVideosToMetadata(
            listOf(
                pixabayVideo(id = 1, duration = 12, mediumUrl = "https://cdn.example.com/one.mp4"),
                pixabayVideo(id = 2, duration = 90, mediumUrl = "https://cdn.example.com/two.mp4"),
                pixabayVideo(id = 3, duration = 10, mediumUrl = ""),
            ),
        )

        assertEquals(listOf("pbv_1"), result.items.map { it.id })
        assertEquals("https://cdn.example.com/one.mp4", result.streamUrls["pbv_1"])
        assertEquals("Pixabay", result.items.first().source)
        assertEquals(720, result.items.first().videoHeight)
    }

    @Test
    fun `youtube mapper does not use thumbnail dimensions as video dimensions`() {
        val item = mapYouTubeVideoSearchItem(
            item = youtubeSearchItem(
                thumbnailWidth = 1280,
                thumbnailHeight = 720,
            ),
            metadata = null,
        )

        assertFalse(item.hasDimensions)
        assertEquals(0, item.videoWidth)
        assertEquals(0, item.videoHeight)
        assertEquals("Unknown video dimensions · 16s", item.videoTechnicalSummary())
    }

    @Test
    fun `youtube mapper stores probed metadata when available`() {
        val item = mapYouTubeVideoSearchItem(
            item = youtubeSearchItem(duration = 45),
            metadata = YouTubeVideoMetadata(
                width = 1080,
                height = 1920,
                rotationDegrees = 90,
                durationSeconds = 12,
                mimeType = "video/mp4",
                videoCodec = "avc1.640028",
            ),
        )

        assertTrue(item.hasDimensions)
        assertEquals(1080, item.videoWidth)
        assertEquals(1920, item.videoHeight)
        assertEquals(90, item.videoRotationDegrees)
        assertEquals(12L, item.duration)
        assertEquals("1080x1920 (Portrait) · 0.56:1 · 12s · rotated 90deg · avc1.640028 · video/mp4", item.videoTechnicalSummary())
    }

    @Test
    fun `pixabay video cache round trips fresh metadata`() {
        val result = PixabayVideoMetadataResult(
            items = listOf(
                VideoWallpaperItem(
                    id = "pbv_42",
                    title = "aurora loop",
                    thumbnailUrl = "https://example.com/thumb.jpg",
                    source = "Pixabay",
                    duration = 14,
                    uploaderName = "maker",
                    popularity = 99,
                    videoWidth = 1080,
                    videoHeight = 1920,
                ),
            ),
            streamUrls = mapOf("pbv_42" to "https://example.com/video.mp4"),
        )
        val encoded = encodePixabayVideoCache(
            CachedPixabayVideoMetadata(
                result = result,
                cachedAtMs = 1_000L,
            ),
        )

        val decoded = decodePixabayVideoCache(encoded, nowMs = 2_000L)

        assertNotNull(decoded)
        assertEquals(result.items, decoded!!.result.items)
        assertEquals(result.streamUrls, decoded.result.streamUrls)
    }

    @Test
    fun `pixabay video cache rejects expired fresh reads but allows stale fallback`() {
        val encoded = encodePixabayVideoCache(
            CachedPixabayVideoMetadata(
                result = PixabayVideoMetadataResult(
                    items = listOf(
                        VideoWallpaperItem(
                            id = "pbv_stale",
                            title = "stale",
                            thumbnailUrl = "https://example.com/stale.jpg",
                            source = "Pixabay",
                        ),
                    ),
                    streamUrls = mapOf("pbv_stale" to "https://example.com/stale.mp4"),
                ),
                cachedAtMs = 1_000L,
            ),
        )
        val expiredNow = 1_000L + PIXABAY_VIDEO_CACHE_TTL_MS + 1L

        assertNull(decodePixabayVideoCache(encoded, nowMs = expiredNow, requireFresh = true))
        assertEquals(
            "pbv_stale",
            decodePixabayVideoCache(encoded, nowMs = expiredNow, requireFresh = false)
                ?.result
                ?.items
                ?.single()
                ?.id,
        )
    }

    @Test
    fun `reddit motion metadata uses shorter two hour freshness`() {
        val encoded = encodePixabayVideoCache(
            CachedPixabayVideoMetadata(
                result = PixabayVideoMetadataResult(
                    items = listOf(
                        VideoWallpaperItem(
                            id = "rd_abc123",
                            title = "Cinemagraph",
                            thumbnailUrl = "https://example.com/reddit.jpg",
                            source = "Reddit",
                        ),
                    ),
                    streamUrls = mapOf("rd_abc123" to "https://i.redd.it/loop.gif"),
                ),
                cachedAtMs = 1_000L,
            ),
        )
        val afterRedditTtl = 1_000L + REDDIT_RSS_MOTION_CACHE_TTL_MS + 1L

        assertNull(
            decodePixabayVideoCache(
                raw = encoded,
                nowMs = afterRedditTtl,
                requireFresh = true,
                freshnessTtlMs = REDDIT_RSS_MOTION_CACHE_TTL_MS,
            ),
        )
        assertNotNull(decodePixabayVideoCache(encoded, nowMs = afterRedditTtl, requireFresh = true))
    }

    @Test
    fun `reddit motion cache preserves raw cursor when atom tail is non media`() {
        val xml = buildString {
            append("<feed>")
            append(
                """
                <entry>
                  <id>t3_motion</id>
                  <title>Loop</title>
                  <content type="html">&lt;a href=&quot;https://i.redd.it/motion.gif&quot;&gt;loop&lt;/a&gt;</content>
                </entry>
                """.trimIndent(),
            )
            repeat(99) { index ->
                append("<entry><id>t3_text$index</id><title>Text tail $index</title></entry>")
            }
            append("</feed>")
        }
        val rssPage = parseRedditRssPage(xml, "Cinemagraphs")
        val result = PixabayVideoMetadataResult(
            items = listOf(
                VideoWallpaperItem(
                    id = "rd_motion",
                    title = "Loop",
                    thumbnailUrl = "https://example.com/motion.jpg",
                    source = "Reddit",
                ),
            ),
            streamUrls = mapOf("rd_motion" to "https://i.redd.it/motion.gif"),
        )

        val decoded = decodePixabayVideoCache(
            raw = encodePixabayVideoCache(
                CachedPixabayVideoMetadata(
                    result = result,
                    cachedAtMs = 1_000L,
                    nextAfter = rssPage.nextAfter,
                    pageExhausted = isRedditMotionPageExhausted(rssPage.rawEntryCount, rssPage.nextAfter),
                ),
            ),
            nowMs = 2_000L,
        )

        assertEquals(100, rssPage.rawEntryCount)
        assertEquals("t3_text98", decoded?.nextAfter)
        assertEquals(false, decoded?.pageExhausted)
        assertEquals("rd_motion", decoded?.result?.items?.single()?.id)
    }

    @Test
    fun `pixabay video rate-limit backoff reads retry headers`() {
        assertEquals(11_000L, pixabayVideoRateLimitBackoffMillis(http429("Retry-After" to "11")))
        assertEquals(5_000L, pixabayVideoRateLimitBackoffMillis(http429("X-RateLimit-Reset" to "5")))
        assertNull(pixabayVideoRateLimitBackoffMillis(IllegalStateException("not rate limited")))
    }

    @Test
    fun `rethrowIfCancelled rethrows cancellation exceptions`() {
        val expected = CancellationException("cancelled")

        try {
            expected.rethrowIfCancelled()
            fail("Expected cancellation to be rethrown")
        } catch (actual: CancellationException) {
            assertSame(expected, actual)
        }
    }

    @Test
    fun `rethrowIfCancelled ignores ordinary failures`() {
        IllegalStateException("boom").rethrowIfCancelled()
    }

    @Test
    fun `isVideoWallpaperHidden matches sanitized moderation ids`() {
        val item = VideoWallpaperItem(
            id = "reddit/post/42",
            title = "Aurora",
            thumbnailUrl = "https://example.com/thumb.jpg",
            source = "Reddit",
        )

        assertTrue(
            isVideoWallpaperHidden(
                item = item,
                hiddenIds = setOf(sanitizeVoteKey(item.id)),
            )
        )
    }

    @Test
    fun `resolveVideoLoopRange preserves requested range when valid`() {
        val range = resolveVideoLoopRange(
            durationMs = 20_000,
            startFraction = 0.25f,
            endFraction = 0.75f,
        )

        assertEquals(5_000, range.startMs)
        assertEquals(15_000, range.endMs)
        assertEquals(10_000, range.durationMs)
    }

    @Test
    fun `resolveVideoLoopRange expands short selections to minimum loop`() {
        val range = resolveVideoLoopRange(
            durationMs = 10_000,
            startFraction = 0.5f,
            endFraction = 0.55f,
        )

        assertEquals(5_000, range.startMs)
        assertEquals(7_000, range.endMs)
        assertEquals(2_000, range.durationMs)
    }

    @Test
    fun `videoTrimArgs formats ffmpeg time arguments`() {
        assertEquals(
            listOf("-ss", "1.250", "-t", "3.500"),
            videoTrimArgs(loopStartMs = 1_250, loopEndMs = 4_750),
        )
        assertEquals(emptyList<String>(), videoTrimArgs(loopStartMs = 2_000, loopEndMs = 2_000))
    }

    @Test
    fun `timelineFrameTimes spreads bounded frame samples across duration`() {
        assertEquals(
            listOf(0L, 2_000L, 4_000L, 6_000L),
            timelineFrameTimes(durationMs = 6_001L, frameCount = 4),
        )
        assertEquals(emptyList<Long>(), timelineFrameTimes(durationMs = 0L, frameCount = 4))
        assertEquals(6, timelineFrameTimes(durationMs = 60_000L, frameCount = 20).size)
    }

    @Test
    fun `video feed queries survive the legacy search handler`() {
        // The video feed appends an orientation suffix to the user's search
        // text before handing it to NewPipe. Those queries carry spaces, and
        // the curated fallbacks carry punctuation, so the handler has to encode
        // them with the API 1 URLEncoder overload rather than NewPipe's own
        // API 33 one (issue #2). searchString must stay verbatim, because the
        // downstream junk-pattern and title filters match against it.
        val userQuery = "northern lights vertical wallpaper"
        val userHandler = createLegacyCompatibleYouTubeSearchHandler(userQuery)

        assertEquals(userQuery, userHandler.searchString)
        assertEquals(
            "https://www.youtube.com/results" +
                "?search_query=northern+lights+vertical+wallpaper&sp=8AEB",
            userHandler.url,
        )

        val curatedQuery = "4k live wallpaper loop (amoled) & dark"
        val curatedHandler = createLegacyCompatibleYouTubeSearchHandler(curatedQuery)

        assertEquals(curatedQuery, curatedHandler.searchString)
        assertEquals(
            "https://www.youtube.com/results" +
                "?search_query=4k+live+wallpaper+loop+%28amoled%29+%26+dark&sp=8AEB",
            curatedHandler.url,
        )
        assertEquals(emptyList<String>(), curatedHandler.contentFilters)
    }

    private fun pixabayVideo(
        id: Long,
        duration: Int,
        mediumUrl: String,
    ) = PixabayVideo(
        id = id,
        tags = "aurora, loop, amoled",
        duration = duration,
        pictureId = "picture$id",
        videos = PixabayVideoFiles(
            medium = PixabayVideoFile(
                url = mediumUrl,
                width = 1280,
                height = 720,
            ),
        ),
        views = 123,
        user = "Pixabay maker",
    )

    private fun youtubeSearchItem(
        thumbnailWidth: Int = 0,
        thumbnailHeight: Int = 0,
        duration: Long = 16,
    ) = YouTubeVideoSearchItem(
        videoId = "abc123",
        title = "Aurora loop",
        thumbnailUrl = "https://img.youtube.com/vi/abc123/maxresdefault.jpg",
        thumbnailWidth = thumbnailWidth,
        thumbnailHeight = thumbnailHeight,
        duration = duration,
        uploaderName = "Channel",
        viewCount = 12_000,
    )

    private fun http429(vararg headers: Pair<String, String>): HttpException {
        val headerPairs = headers.flatMap { listOf(it.first, it.second) }.toTypedArray()
        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://pixabay.com/api/videos/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .headers(Headers.headersOf(*headerPairs))
            .build()
        return HttpException(Response.error<Any>("".toResponseBody(null), rawResponse))
    }
}
