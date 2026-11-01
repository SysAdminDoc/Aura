package com.freevibe.ui.screens.videowallpapers

import android.content.Context
import android.content.SharedPreferences
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.DEFAULT_FIT_CANVAS_COLOR
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.FitCanvasMode
import com.freevibe.data.model.RedditFeedMediaType
import com.freevibe.data.model.RedditFeedPreset
import com.freevibe.data.model.RedditFeedPresetStore
import com.freevibe.data.model.WALLPAPER_PRESENTATION_FILL
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pexels.PexelsUser
import com.freevibe.data.remote.pexels.PexelsVideo
import com.freevibe.data.remote.pexels.PexelsVideoFile
import com.freevibe.data.remote.pexels.PexelsVideoResponse
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.pixabay.PixabayVideo
import com.freevibe.data.remote.pixabay.PixabayVideoFile
import com.freevibe.data.remote.pixabay.PixabayVideoFiles
import com.freevibe.data.remote.pixabay.PixabayVideoResponse
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.service.Av1CodecSupport
import com.freevibe.service.SourceMetrics
import com.freevibe.service.VideoPreviewCache
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.YouTubeYtDlpRequestFactory
import com.freevibe.service.YtDlpUpdateManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drives the Videos feed through the view model's public entry points with mocked providers.
 * It pins the load orchestration (what each source adds, how pages advance, which source counts as
 * failed), the load job's ownership and the stream URL cache's eviction, so the view model can be
 * split without changing any of it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoFeedLoadCharacterizationTest {

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

    private val cacheValues: MutableMap<String, Any?> = java.util.Collections.synchronizedMap(mutableMapOf())

    private fun fakeContext(): Context {
        val context = mockk<Context>(relaxed = true)
        val cachePreferences = mockk<SharedPreferences>()
        val cacheEditor = mockk<SharedPreferences.Editor>()
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
        every { cachePreferences.all } answers { synchronized(cacheValues) { cacheValues.toMap() } }
        every { cachePreferences.edit() } returns cacheEditor
        every { cacheEditor.clear() } answers { cacheValues.clear(); cacheEditor }
        every { cacheEditor.remove(any()) } answers {
            cacheValues.remove(firstArg<String>())
            cacheEditor
        }
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
        every { context.getString(R.string.video_wp_no_sources) } returns "NO_SOURCES"
        every { context.getString(R.string.video_wp_sources_unavailable) } returns "UNAVAILABLE"
        every { context.getString(R.string.video_wp_sources_limited) } returns "LIMITED"
        every { context.getString(R.string.video_wp_sources_stale) } returns "STALE"
        return context
    }

    private fun fakePrefs(
        youtube: Boolean = false,
        pexels: Boolean = false,
        pixabay: Boolean = false,
        reddit: Boolean = false,
        pexelsKey: String = "",
        pixabayKey: String = "",
    ): PreferencesManager = mockk(relaxed = true) {
        every { videoWallpaperPresentation } returns flowOf(WALLPAPER_PRESENTATION_FILL)
        every { videoFitCanvasMode } returns flowOf(FitCanvasMode.AMOLED_BLACK.preferenceValue)
        every { videoFitCanvasColor } returns flowOf(DEFAULT_FIT_CANVAS_COLOR)
        every { youtubeProviderEnabled } returns flowOf(youtube)
        every { redditProviderEnabled } returns flowOf(reddit)
        every { redditFeedPresets } returns flowOf(
            RedditFeedPresetStore(
                presets = listOf(redditVideoPreset),
                activeWallpaperPresetId = "",
                activeVideoPresetId = redditVideoPreset.id,
            ),
        )
        every { showNsfwContent } returns flowOf(false)
        every { pexelsProviderEnabled } returns flowOf(pexels)
        every { pixabayProviderEnabled } returns flowOf(pixabay)
        every { this@mockk.pexelsApiKey } returns flowOf(pexelsKey)
        every { this@mockk.pixabayApiKey } returns flowOf(pixabayKey)
    }

    private fun viewModel(
        context: Context = fakeContext(),
        prefs: PreferencesManager = fakePrefs(),
        pexelsApi: PexelsApi = mockk(relaxed = true),
        pixabayApi: PixabayApi = mockk(relaxed = true),
        youtubeRepo: YouTubeRepository = mockk(relaxed = true),
        okHttpClient: OkHttpClient = mockk(relaxed = true),
        redditRepo: RedditRepository = mockk(relaxed = true),
    ) = VideoWallpapersViewModel(
        context = context,
        youtubeRepo = youtubeRepo,
        redditRepo = redditRepo,
        pexelsApi = pexelsApi,
        pixabayApi = pixabayApi,
        prefs = prefs,
        okHttpClient = okHttpClient,
        videoWallpaperStorage = mockk<VideoWallpaperStorage>(relaxed = true),
        sourceMetrics = SourceMetrics(),
        ytDlpUpdateManager = mockk<YtDlpUpdateManager>(relaxed = true),
        ytDlpRequestFactory = mockk<YouTubeYtDlpRequestFactory>(relaxed = true),
        videoPreviewCache = mockk<VideoPreviewCache>(relaxed = true),
        av1CodecSupport = mockk<Av1CodecSupport>(relaxed = true),
        voteRepo = mockk<VoteRepository>(relaxed = true),
        reportRepo = mockk<CommunityReportRepository>(relaxed = true),
        favoritesRepo = mockk<FavoritesRepository>(relaxed = true) {
            every { getVideos() } returns flowOf(emptyList())
        },
    )

    private fun loadJob(viewModel: VideoWallpapersViewModel): Job? =
        VideoWallpapersViewModel::class.java.getDeclaredField("loadJob")
            .apply { isAccessible = true }
            .get(viewModel) as? Job

    private fun loadGeneration(viewModel: VideoWallpapersViewModel): Long =
        VideoWallpapersViewModel::class.java.getDeclaredField("loadGeneration")
            .apply { isAccessible = true }
            .getLong(viewModel)

    private suspend fun awaitLoad(viewModel: VideoWallpapersViewModel) {
        while (true) {
            val job = loadJob(viewModel) ?: return
            job.join()
            if (loadJob(viewModel) == null) return
        }
    }

    private fun pixabayHit(id: Long) = PixabayVideo(
        id = id,
        tags = "aurora, loop",
        duration = 12,
        pictureId = "picture$id",
        videos = PixabayVideoFiles(
            medium = PixabayVideoFile(url = "https://cdn.example.com/pb$id.mp4", width = 1080, height = 1920),
        ),
    )

    private fun pexelsClip(id: Long) = PexelsVideo(
        id = id,
        width = 1080,
        height = 1920,
        duration = 10,
        url = "https://www.pexels.com/video/$id/",
        image = "https://images.example.com/px$id.jpg",
        user = PexelsUser(name = "maker"),
        videoFiles = listOf(
            PexelsVideoFile(fileType = "video/mp4", width = 1080, height = 1920, link = "https://cdn.example.com/px$id.mp4"),
        ),
    )

    private fun PixabayApi.stubSearch(answer: suspend (page: Int) -> PixabayVideoResponse) {
        coEvery {
            searchVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers { answer(args[9] as Int) }
    }

    private fun PexelsApi.stubSearch(answer: suspend (page: Int) -> PexelsVideoResponse) {
        coEvery { searchVideos(any(), any(), any(), any(), any(), any()) } coAnswers { answer(args[5] as Int) }
    }

    @Test
    fun `a first load merges Pixabay and Pexels, remembers their streams and moves both pages on`() = runTest(dispatcher) {
        val pixabayApi = mockk<PixabayApi>().apply { stubSearch { PixabayVideoResponse(hits = listOf(pixabayHit(1))) } }
        val pexelsApi = mockk<PexelsApi>().apply { stubSearch { PexelsVideoResponse(videos = listOf(pexelsClip(7))) } }
        val viewModel = viewModel(
            prefs = fakePrefs(pexels = true, pixabay = true, pexelsKey = "px-key", pixabayKey = "pb-key"),
            pexelsApi = pexelsApi,
            pixabayApi = pixabayApi,
        )

        assertTrue(viewModel.startFeed())
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf("pbv_1", "px_7"), state.items.map { it.id }.toSet())
        assertEquals(2, state.pexelsPage)
        assertEquals(2, state.pixabayPage)
        assertEquals(0, state.ytQueryIndex)
        assertNull(state.error)
        assertTrue(state.degradedSources.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(loadJob(viewModel))
        assertEquals("https://cdn.example.com/px7.mp4", viewModel.getStreamUrl("px_7"))
        assertEquals("https://cdn.example.com/pb1.mp4", viewModel.getStreamUrl("pbv_1"))
        assertTrue(viewModel.resolvedIds.value.containsAll(listOf("px_7", "pbv_1")))
        coVerify(exactly = 1) {
            pixabayApi.searchVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), 1, any())
        }
    }

    @Test
    fun `load more asks each source for its next page and appends after the first batch`() = runTest(dispatcher) {
        val pixabayApi = mockk<PixabayApi>().apply {
            stubSearch { page -> PixabayVideoResponse(hits = listOf(pixabayHit(page.toLong()))) }
        }
        val pexelsApi = mockk<PexelsApi>().apply {
            stubSearch { page -> PexelsVideoResponse(videos = listOf(pexelsClip(page.toLong() + 6))) }
        }
        val viewModel = viewModel(
            prefs = fakePrefs(pexels = true, pixabay = true, pexelsKey = "px-key", pixabayKey = "pb-key"),
            pexelsApi = pexelsApi,
            pixabayApi = pixabayApi,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()
        val firstBatch = viewModel.state.value.items.map { it.id }
        assertEquals(setOf("pbv_1", "px_7"), firstBatch.toSet())

        viewModel.loadMore()
        assertTrue(viewModel.state.value.isLoadingMore)
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(firstBatch, state.items.map { it.id }.take(2))
        assertEquals(setOf("pbv_1", "px_7", "pbv_2", "px_8"), state.items.map { it.id }.toSet())
        assertEquals(3, state.pexelsPage)
        assertEquals(3, state.pixabayPage)
        assertFalse(state.isLoadingMore)
        coVerify(exactly = 1) {
            pixabayApi.searchVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), 2, any())
        }
        coVerify(exactly = 1) { pexelsApi.searchVideos(any(), any(), any(), any(), any(), 2) }
    }

    @Test
    fun `a refresh cancels the running load and the stale one can't touch the newer results`() = runTest(dispatcher) {
        val started = AtomicInteger(0)
        val firstStarted = CompletableDeferred<Unit>()
        val secondStarted = CompletableDeferred<Unit>()
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        val pixabayApi = mockk<PixabayApi>().apply {
            stubSearch {
                if (started.incrementAndGet() == 1) {
                    firstStarted.complete(Unit)
                    // The first request outlives its cancellation, the way a slow network call does.
                    withContext(NonCancellable) { firstGate.await() }
                    PixabayVideoResponse(hits = listOf(pixabayHit(100)))
                } else {
                    secondStarted.complete(Unit)
                    secondGate.await()
                    PixabayVideoResponse(hits = listOf(pixabayHit(200)))
                }
            }
        }
        val viewModel = viewModel(
            prefs = fakePrefs(pixabay = true, pixabayKey = "pb-key"),
            pixabayApi = pixabayApi,
        )

        viewModel.startFeed()
        runCurrent()
        firstStarted.await()
        runCurrent()
        val firstJob = loadJob(viewModel)
        assertNotNull(firstJob)
        assertEquals(1L, loadGeneration(viewModel))
        assertTrue(viewModel.state.value.isLoading)

        viewModel.refresh()
        runCurrent()
        secondStarted.await()
        runCurrent()
        assertEquals(2L, loadGeneration(viewModel))
        assertTrue(firstJob!!.isCancelled)
        val secondJob = loadJob(viewModel)
        assertNotNull(secondJob)
        assertTrue(secondJob !== firstJob)

        // The stale load unwinds after the refresh took over. It must not clear the refresh's flags
        // or its job, and it must not publish its item.
        firstGate.complete(Unit)
        firstJob.join()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isRefreshing)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertTrue(loadJob(viewModel) === secondJob)

        secondGate.complete(Unit)
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("pbv_200"), state.items.map { it.id })
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(loadJob(viewModel))
        assertEquals(2L, loadGeneration(viewModel))
        assertEquals(2, started.get())
    }

    @Test
    fun `one source failing is reported while the others still fill the feed`() = runTest(dispatcher) {
        val pixabayApi = mockk<PixabayApi>().apply { stubSearch { PixabayVideoResponse(hits = listOf(pixabayHit(1))) } }
        val pexelsApi = mockk<PexelsApi>()
        coEvery { pexelsApi.searchVideos(any(), any(), any(), any(), any(), any()) } throws
            java.io.IOException("pexels is down")
        mockkStatic(NewPipe::class)
        try {
            // NewPipe is a static service locator, so the YouTube path fails by it not being set up.
            every { NewPipe.getService(any<Int>()) } throws IllegalStateException("NewPipe isn't initialised")
            val viewModel = viewModel(
                prefs = fakePrefs(youtube = true, pexels = true, pixabay = true, pexelsKey = "px-key", pixabayKey = "pb-key"),
                pexelsApi = pexelsApi,
                pixabayApi = pixabayApi,
            )

            viewModel.startFeed()
            awaitLoad(viewModel)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("pbv_1"), state.items.map { it.id })
            assertEquals(listOf("Pexels", "YouTube"), state.degradedSources)
            assertNull(state.error)
            // A failed source keeps its place, so the next batch asks it again.
            assertEquals(1, state.pexelsPage)
            assertEquals(0, state.ytQueryIndex)
            assertEquals(2, state.pixabayPage)
            verify(exactly = 1) { NewPipe.getService(any<Int>()) }
        } finally {
            unmockkStatic(NewPipe::class)
        }
    }

    @Test
    fun `every attempted source failing leaves an empty feed and says the sources are unavailable`() = runTest(dispatcher) {
        val pixabayApi = mockk<PixabayApi>()
        coEvery {
            pixabayApi.searchVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws java.io.IOException("pixabay is down")
        val pexelsApi = mockk<PexelsApi>()
        coEvery { pexelsApi.searchVideos(any(), any(), any(), any(), any(), any()) } throws
            java.io.IOException("pexels is down")
        val viewModel = viewModel(
            prefs = fakePrefs(pexels = true, pixabay = true, pexelsKey = "px-key", pixabayKey = "pb-key"),
            pexelsApi = pexelsApi,
            pixabayApi = pixabayApi,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.items.isEmpty())
        assertEquals(listOf("Pexels", "Pixabay"), state.degradedSources)
        assertEquals("UNAVAILABLE", state.error)
        assertEquals(1, state.pexelsPage)
        assertEquals(1, state.pixabayPage)
        assertEquals(1, state.emptyLoadCount)
        assertFalse(state.isLoading)
    }

    @Test
    fun `with no source turned on the feed says there is nothing to search`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.items.isEmpty())
        assertEquals("NO_SOURCES", state.error)
        assertTrue(state.degradedSources.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `four empty batches in a row end discovery and a fifth load more does nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.hasMore)
        assertEquals(1, viewModel.state.value.emptyLoadCount)

        repeat(3) {
            viewModel.loadMore()
            awaitLoad(viewModel)
            advanceUntilIdle()
        }
        assertEquals(4, viewModel.state.value.emptyLoadCount)
        assertFalse(viewModel.state.value.hasMore)

        val generation = loadGeneration(viewModel)
        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(generation, loadGeneration(viewModel))
        assertNull(loadJob(viewModel))
    }

    private fun savedClip(index: Int) = FavoriteEntity(
        id = "rd_$index",
        source = "REDDIT",
        type = "VIDEO",
        thumbnailUrl = "https://b.thumbs.redditmedia.com/$index.jpg",
        fullUrl = "https://v.redd.it/clip$index/HLSPlaylist.m3u8",
        name = "Clip $index",
        uploaderName = "u/maker",
        sourcePageUrl = "https://www.reddit.com/r/LiveWallpapers/comments/$index/clip/",
    )

    @Test
    fun `the stream cache holds 200 and evicts the least recently used one with its resolved mark`() = runTest(dispatcher) {
        val viewModel = viewModel()

        for (index in 0 until 200) viewModel.resolveSavedVideo(savedClip(index))
        advanceUntilIdle()
        assertEquals(200, viewModel.resolvedIds.value.size)
        assertEquals("https://v.redd.it/clip0/HLSPlaylist.m3u8", viewModel.getStreamUrl("rd_0"))

        // Reading rd_0 made it the newest, so the next entry pushes rd_1 out instead.
        viewModel.resolveSavedVideo(savedClip(200))
        advanceUntilIdle()
        assertNull(viewModel.getStreamUrl("rd_1"))
        assertFalse("rd_1" in viewModel.resolvedIds.value)
        assertEquals("https://v.redd.it/clip0/HLSPlaylist.m3u8", viewModel.getStreamUrl("rd_0"))
        assertTrue("rd_0" in viewModel.resolvedIds.value)
        assertTrue("rd_200" in viewModel.resolvedIds.value)
        assertEquals(200, viewModel.resolvedIds.value.size)

        viewModel.resolveSavedVideo(savedClip(201))
        advanceUntilIdle()
        assertNull(viewModel.getStreamUrl("rd_2"))
        assertFalse("rd_2" in viewModel.resolvedIds.value)
        assertEquals(200, viewModel.resolvedIds.value.size)
    }

    @Test
    fun `changing the orientation or searching drops the cached streams`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.resolveSavedVideo(savedClip(1))
        advanceUntilIdle()
        assertEquals("https://v.redd.it/clip1/HLSPlaylist.m3u8", viewModel.getStreamUrl("rd_1"))

        viewModel.setOrientation(OrientationFilter.LANDSCAPE)
        assertNull(viewModel.getStreamUrl("rd_1"))
        assertTrue(viewModel.resolvedIds.value.isEmpty())
        assertTrue(viewModel.streamFailedIds.value.isEmpty())
        awaitLoad(viewModel)
        advanceUntilIdle()

        viewModel.resolveSavedVideo(savedClip(2))
        advanceUntilIdle()
        assertEquals("https://v.redd.it/clip2/HLSPlaylist.m3u8", viewModel.getStreamUrl("rd_2"))

        viewModel.search("rain")
        assertNull(viewModel.getStreamUrl("rd_2"))
        assertTrue(viewModel.resolvedIds.value.isEmpty())
        assertEquals("rain", viewModel.state.value.searchQuery)
        awaitLoad(viewModel)
        advanceUntilIdle()
    }

    // Reddit takes one anonymous request a minute, shared by every feed.

    private val redditVideoPreset = RedditFeedPreset(
        id = "video",
        name = "Video",
        mediaType = RedditFeedMediaType.VIDEO,
        subreddits = listOf("Cinemagraphs"),
    )

    private val redditFeedXml =
        "<feed><entry><id>t3_clip1</id><title>Aurora loop</title>" +
            "<content type=\"html\">&lt;a href=&quot;https://v.redd.it/clip1&quot;&gt;x&lt;/a&gt;</content></entry></feed>"

    /** Answers each request with the next code in [codes], then keeps repeating the last one. */
    private fun redditClient(requests: AtomicInteger, vararg codes: Int): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val code = codes[requests.getAndIncrement().coerceAtMost(codes.lastIndex)]
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("HTTP $code")
                    .body(redditFeedXml.toResponseBody("application/atom+xml".toMediaType()))
                    .build()
            }
            .build()

    private fun redditRepoWithClaims(vararg claims: Boolean) = mockk<RedditRepository>(relaxed = true) {
        coEvery { tryClaimRssRequest() } returnsMany claims.toList()
    }

    @Test
    fun `a spent Reddit budget is a wait with no error and the feed fills by itself`() = runTest(dispatcher) {
        val requests = AtomicInteger(0)
        val redditRepo = redditRepoWithClaims(false, true)
        val duringWait = mutableListOf<VideoWallpapersState>()
        lateinit var viewModel: VideoWallpapersViewModel
        coEvery { redditRepo.retryDelayMs() } coAnswers {
            duringWait += viewModel.state.value
            30_000L
        }
        viewModel = viewModel(
            prefs = fakePrefs(reddit = true),
            okHttpClient = redditClient(requests, 200),
            redditRepo = redditRepo,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val waiting = duringWait.single()
        assertNull(waiting.error)
        assertTrue(waiting.degradedSources.isEmpty())
        assertTrue(waiting.isLoading)
        assertTrue(waiting.redditWaiting)
        assertTrue(waiting.items.isEmpty())

        val state = viewModel.state.value
        assertEquals(listOf("rd_clip1"), state.items.map { it.id })
        assertEquals(1, requests.get())
        assertNull(state.error)
        assertTrue(state.degradedSources.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.redditWaiting)
    }

    @Test
    fun `a 429 from Reddit is the same wait and tells every other feed to hold back`() = runTest(dispatcher) {
        val requests = AtomicInteger(0)
        val redditRepo = redditRepoWithClaims(true, true)
        coEvery { redditRepo.retryDelayMs() } returns 30_000L
        val viewModel = viewModel(
            prefs = fakePrefs(reddit = true),
            okHttpClient = redditClient(requests, 429, 200),
            redditRepo = redditRepo,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("rd_clip1"), state.items.map { it.id })
        assertEquals(2, requests.get())
        assertNull(state.error)
        assertTrue(state.degradedSources.isEmpty())
        coVerify(exactly = 1) { redditRepo.recordRssRateLimit(any()) }
    }

    @Test
    fun `a budget that stays spent ends the wait and names Reddit`() = runTest(dispatcher) {
        val requests = AtomicInteger(0)
        val redditRepo = redditRepoWithClaims(false)
        coEvery { redditRepo.retryDelayMs() } returns 1L
        val viewModel = viewModel(
            prefs = fakePrefs(reddit = true),
            okHttpClient = redditClient(requests, 200),
            redditRepo = redditRepo,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("Reddit"), state.degradedSources)
        assertEquals("UNAVAILABLE", state.error)
        assertFalse(state.isLoading)
        assertFalse(state.isLoadingMore)
        assertFalse(state.redditWaiting)
        assertEquals(0, requests.get())
        coVerify(exactly = 3) { redditRepo.retryDelayMs() }
    }

    @Test
    fun `other sources show while Reddit waits and aren't asked twice`() = runTest(dispatcher) {
        val requests = AtomicInteger(0)
        val pixabayApi = mockk<PixabayApi>().apply { stubSearch { PixabayVideoResponse(hits = listOf(pixabayHit(1))) } }
        val redditRepo = redditRepoWithClaims(false, true)
        val duringWait = mutableListOf<VideoWallpapersState>()
        lateinit var viewModel: VideoWallpapersViewModel
        coEvery { redditRepo.retryDelayMs() } coAnswers {
            duringWait += viewModel.state.value
            30_000L
        }
        viewModel = viewModel(
            prefs = fakePrefs(reddit = true, pixabay = true, pixabayKey = "pb-key"),
            pixabayApi = pixabayApi,
            okHttpClient = redditClient(requests, 200),
            redditRepo = redditRepo,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val waiting = duringWait.single()
        assertEquals(listOf("pbv_1"), waiting.items.map { it.id })
        assertFalse(waiting.isLoading)
        assertFalse(waiting.redditWaiting)
        assertNull(waiting.error)

        val state = viewModel.state.value
        assertEquals(setOf("pbv_1", "rd_clip1"), state.items.map { it.id }.toSet())
        assertEquals(2, state.pixabayPage)
        assertNull(state.error)
        coVerify(exactly = 1) {
            pixabayApi.searchVideos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a Reddit server error stays an ordinary failure with no wait`() = runTest(dispatcher) {
        val requests = AtomicInteger(0)
        val redditRepo = redditRepoWithClaims(true)
        val viewModel = viewModel(
            prefs = fakePrefs(reddit = true),
            okHttpClient = redditClient(requests, 500),
            redditRepo = redditRepo,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("Reddit"), state.degradedSources)
        assertEquals("UNAVAILABLE", state.error)
        assertEquals(1, requests.get())
        coVerify(exactly = 0) { redditRepo.retryDelayMs() }
        coVerify(exactly = 0) { redditRepo.recordRssRateLimit(any()) }
    }

    @Test
    fun `an older cached Reddit page beats waiting`() = runTest(dispatcher) {
        val requests = AtomicInteger(0)
        val redditRepo = redditRepoWithClaims(false)
        val group = resolveRedditMotionFeedGroups(redditVideoPreset, showAdultContent = false).single()
        val item = VideoWallpaperItem(
            id = "rd_old",
            title = "Old loop",
            thumbnailUrl = "https://example.com/old.jpg",
            source = "Reddit",
            uploaderName = "u/maker",
            contentSource = ContentSource.REDDIT,
            license = "Reddit",
            sourcePageUrl = "https://www.reddit.com/r/Cinemagraphs/comments/old/loop/",
        )
        cacheValues[redditRssMotionCacheKey(group, after = null)] = encodePixabayVideoCache(
            CachedPixabayVideoMetadata(
                result = PixabayVideoMetadataResult(
                    items = listOf(item),
                    streamUrls = mapOf(item.id to "https://v.redd.it/old/HLSPlaylist.m3u8"),
                ),
                cachedAtMs = System.currentTimeMillis() - REDDIT_RSS_MOTION_CACHE_TTL_MS - 60_000L,
                nextAfter = null,
                pageExhausted = true,
            ),
        )
        val viewModel = viewModel(
            prefs = fakePrefs(reddit = true),
            okHttpClient = redditClient(requests, 200),
            redditRepo = redditRepo,
        )

        viewModel.startFeed()
        awaitLoad(viewModel)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("rd_old"), state.items.map { it.id })
        assertNull(state.error)
        assertEquals(0, requests.get())
        coVerify(exactly = 0) { redditRepo.retryDelayMs() }
    }
}
