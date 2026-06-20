package com.freevibe.data.repository

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.nasa.NasaApodApi
import com.freevibe.data.remote.wikimedia.WikimediaPotdApi
import com.freevibe.data.remote.bing.BingImage
import com.freevibe.data.remote.bing.BingImageResponse
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pexels.PexelsPhoto
import com.freevibe.data.remote.pexels.PexelsPhotoResponse
import com.freevibe.data.remote.pexels.PexelsPhotoSrc
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.pixabay.PixabayPhoto
import com.freevibe.data.remote.pixabay.PixabayPhotoResponse
import com.freevibe.data.remote.wallhaven.WallhavenApi
import com.freevibe.data.remote.wallhaven.WallhavenMeta
import com.freevibe.data.remote.wallhaven.WallhavenSearchResponse
import com.freevibe.data.remote.wallhaven.WallhavenThumbs
import com.freevibe.data.remote.wallhaven.WallhavenWallpaper
import com.freevibe.service.SourceMetrics
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.ConnectException
import java.net.UnknownHostException

class WallpaperRepositoryTest {

    @Test
    fun `mergeDiscoverResults keeps provider pagination instead of inferring from visible items`() {
        val merged = mergeDiscoverResults(
            results = listOf(
                SearchResult(
                    items = listOf(wallpaper("wallhaven_1"), wallpaper("wallhaven_2")),
                    totalCount = 120,
                    currentPage = 2,
                    hasMore = false,
                ),
                SearchResult(
                    items = listOf(wallpaper("pexels_1", source = ContentSource.PEXELS)),
                    totalCount = 30,
                    currentPage = 2,
                    hasMore = true,
                ),
            ),
            page = 2,
        )

        assertEquals(listOf("wallhaven_1", "pexels_1", "wallhaven_2"), merged.items.map { it.id })
        assertEquals(150, merged.totalCount)
        assertTrue(merged.hasMore)
    }

    @Test
    fun `mergeDiscoverResults ignores unknown totals and falls back to visible item count`() {
        val merged = mergeDiscoverResults(
            results = listOf(
                SearchResult(
                    items = listOf(wallpaper("reddit_1", source = ContentSource.REDDIT)),
                    totalCount = -1,
                    currentPage = 1,
                    hasMore = false,
                ),
                SearchResult(
                    items = listOf(wallpaper("reddit_2", source = ContentSource.REDDIT)),
                    totalCount = -1,
                    currentPage = 1,
                    hasMore = false,
                ),
            ),
            page = 1,
        )

        assertEquals(2, merged.totalCount)
        assertFalse(merged.hasMore)
    }

    @Test
    fun `mergeDiscoverResults limits item count while preserving provider interleave`() {
        val merged = mergeDiscoverResults(
            results = listOf(
                SearchResult(
                    items = listOf(
                        wallpaper("wallhaven_1"),
                        wallpaper("wallhaven_2"),
                        wallpaper("wallhaven_3"),
                    ),
                    totalCount = 100,
                    currentPage = 1,
                    hasMore = true,
                ),
                SearchResult(
                    items = listOf(
                        wallpaper("pexels_1", source = ContentSource.PEXELS),
                        wallpaper("pexels_2", source = ContentSource.PEXELS),
                        wallpaper("pexels_3", source = ContentSource.PEXELS),
                    ),
                    totalCount = 100,
                    currentPage = 1,
                    hasMore = true,
                ),
            ),
            page = 1,
            maxItems = 4,
        )

        assertEquals(
            listOf("wallhaven_1", "pexels_1", "wallhaven_2", "pexels_2"),
            merged.items.map { it.id },
        )
        assertTrue(merged.hasMore)
    }

    @Test
    fun `keepPexelsAsDiscoverEnhancement drops pexels-only discover inventory`() {
        val guarded = keepPexelsAsDiscoverEnhancement(
            listOf(
                SearchResult(
                    items = listOf(wallpaper("px_1", source = ContentSource.PEXELS)),
                    totalCount = 50,
                    currentPage = 1,
                    hasMore = true,
                ),
            ),
        )

        assertTrue(guarded.single().items.isEmpty())
        assertEquals(0, guarded.single().totalCount)
        assertFalse(guarded.single().hasMore)
    }

    @Test
    fun `keepPexelsAsDiscoverEnhancement keeps pexels when base inventory is present`() {
        val guarded = keepPexelsAsDiscoverEnhancement(
            listOf(
                SearchResult(
                    items = listOf(wallpaper("wh_1", source = ContentSource.WALLHAVEN)),
                    totalCount = 1,
                    currentPage = 1,
                    hasMore = false,
                ),
                SearchResult(
                    items = listOf(wallpaper("px_1", source = ContentSource.PEXELS)),
                    totalCount = 1,
                    currentPage = 1,
                    hasMore = false,
                ),
            ),
        )

        assertEquals(listOf(ContentSource.WALLHAVEN, ContentSource.PEXELS), guarded.flatMap { it.items }.map { it.source })
    }

    @Test
    fun `shouldRetryBingHost only retries transient network failures`() {
        assertTrue(shouldRetryBingHost(UnknownHostException("dns")))
        assertTrue(shouldRetryBingHost(ConnectException("connect")))
        assertFalse(shouldRetryBingHost(IllegalArgumentException("bad request")))
    }

    @Test
    fun `getBingDaily disabled records disabled source and skips api`() = runTest {
        val bingApi = mockk<BingDailyApi>()
        val sourceMetrics = SourceMetrics()
        val repo = wallpaperRepository(
            bingApi = bingApi,
            sourceMetrics = sourceMetrics,
            bingProviderEnabled = false,
        )

        val result = repo.getBingDaily(page = 2)

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.totalCount)
        assertEquals(2, result.currentPage)
        assertFalse(result.hasMore)
        assertEquals(1L, sourceMetrics.snapshot("bing")?.disabledCount)
        coVerify(exactly = 0) { bingApi.getImages(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getWallpaperOfTheDay prefers Bing daily over Wallhaven`() = runTest {
        val bingApi = mockk<BingDailyApi>()
        val wallhavenApi = mockk<WallhavenApi>()
        coEvery { bingApi.getImages(any(), any(), any(), any(), any()) } returns bingResponse("daily_bing")
        val repo = wallpaperRepository(
            bingApi = bingApi,
            wallhavenApi = wallhavenApi,
        )

        val result = repo.getWallpaperOfTheDay()

        assertEquals(ContentSource.BING, result?.source)
        assertTrue(result?.id.orEmpty().startsWith("bing_20260611_"))
        coVerify(exactly = 0) {
            wallhavenApi.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `getWallpaperOfTheDay falls back to Wallhaven when Bing is unavailable`() = runTest {
        val bingApi = mockk<BingDailyApi>()
        val wallhavenApi = mockk<WallhavenApi>()
        coEvery { bingApi.getImages(any(), any(), any(), any(), any()) } throws UnknownHostException("dns")
        coEvery {
            wallhavenApi.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns wallhavenResponse("daily_wh")
        val repo = wallpaperRepository(
            bingApi = bingApi,
            wallhavenApi = wallhavenApi,
        )

        val result = repo.getWallpaperOfTheDay()

        assertEquals(ContentSource.WALLHAVEN, result?.source)
        assertEquals("wh_daily_wh", result?.id)
    }

    @Test
    fun `getWallhaven disabled records disabled source and skips api`() = runTest {
        val wallhavenApi = mockk<WallhavenApi>()
        val sourceMetrics = SourceMetrics()
        val repo = wallpaperRepository(
            wallhavenApi = wallhavenApi,
            sourceMetrics = sourceMetrics,
            wallhavenProviderEnabled = false,
        )

        val result = repo.getWallhaven(page = 3)

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.totalCount)
        assertEquals(3, result.currentPage)
        assertFalse(result.hasMore)
        assertEquals(1L, sourceMetrics.snapshot("wallhaven")?.disabledCount)
        coVerify(exactly = 0) {
            wallhavenApi.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `getDiscover with pexels disabled still returns base wallpaper sources`() = runTest {
        val wallhavenApi = mockk<WallhavenApi>()
        val pixabayApi = mockk<PixabayApi>()
        val pexelsApi = mockk<PexelsApi>()
        val sourceMetrics = SourceMetrics()
        coEvery {
            wallhavenApi.search(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns wallhavenResponse("base_wh")
        coEvery {
            pixabayApi.searchPhotos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns pixabayPhotoResponse("base_pb")
        val repo = wallpaperRepository(
            wallhavenApi = wallhavenApi,
            pixabayApi = pixabayApi,
            pexelsApi = pexelsApi,
            sourceMetrics = sourceMetrics,
            pexelsProviderEnabled = false,
            pixabayApiKey = "pixabay-key",
        )

        val result = repo.getDiscover(page = 1)

        assertTrue(result.items.any { it.source == ContentSource.WALLHAVEN })
        assertTrue(result.items.any { it.source == ContentSource.PIXABAY })
        assertTrue(result.items.none { it.source == ContentSource.PEXELS })
        assertEquals(1L, sourceMetrics.snapshot("pexels")?.disabledCount)
        coVerify(exactly = 0) { pexelsApi.curatedPhotos(any(), any(), any()) }
        coVerify(exactly = 0) { pexelsApi.searchPhotos(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `getPexelsCurated keeps photographer and source page context`() = runTest {
        val pexelsApi = mockk<PexelsApi>()
        coEvery { pexelsApi.curatedPhotos(any(), any(), any()) } returns PexelsPhotoResponse(
            totalResults = 1,
            page = 1,
            photos = listOf(
                PexelsPhoto(
                    id = 42,
                    width = 1440,
                    height = 3200,
                    url = "https://www.pexels.com/photo/aurora-42/",
                    photographer = "Mira Lane",
                    src = PexelsPhotoSrc(
                        original = "https://images.pexels.com/photos/42/original.jpg",
                        medium = "https://images.pexels.com/photos/42/medium.jpg",
                    ),
                ),
            ),
        )
        val repo = wallpaperRepository(
            pexelsApi = pexelsApi,
            pexelsApiKey = "pexels-key",
        )

        val wallpaper = repo.getPexelsCurated(page = 1).items.single()

        assertEquals(ContentSource.PEXELS, wallpaper.source)
        assertEquals("Mira Lane", wallpaper.uploaderName)
        assertEquals("https://www.pexels.com/photo/aurora-42/", wallpaper.sourcePageUrl)
    }

    @Test
    fun `getPixabay uses fresh cache before api call`() = runTest {
        val pixabayApi = mockk<PixabayApi>()
        val cacheManager = mockk<WallpaperCacheManager>()
        val cached = listOf(wallpaper("pb_cached", source = ContentSource.PIXABAY))
        coEvery { cacheManager.getCached("pixabay_0_1", ContentSource.PIXABAY) } returns cached

        val repo = wallpaperRepository(
            pixabayApi = pixabayApi,
            cacheManager = cacheManager,
            pixabayApiKey = "pixabay-key",
        )

        val result = repo.getPixabay(page = 1)

        assertEquals(cached, result.items)
        assertEquals(1, result.totalCount)
        assertFalse(result.hasMore)
        coVerify(exactly = 0) {
            pixabayApi.searchPhotos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `pixabay rate-limit backoff reads retry headers`() {
        assertEquals(42_000L, pixabayRateLimitBackoffMillis(http429("Retry-After" to "42")))
        assertEquals(7_000L, pixabayRateLimitBackoffMillis(http429("X-RateLimit-Reset" to "7")))
        assertNull(pixabayRateLimitBackoffMillis(IllegalStateException("not rate limited")))
    }

    private fun wallpaperRepository(
        wallhavenApi: WallhavenApi = mockk(),
        bingApi: BingDailyApi = mockk(),
        nasaApodApi: NasaApodApi = mockk(relaxed = true),
        wikimediaPotdApi: WikimediaPotdApi = mockk(relaxed = true),
        pixabayApi: PixabayApi = mockk(),
        pexelsApi: PexelsApi = mockk(),
        cacheManager: WallpaperCacheManager = mockk(relaxed = true),
        sourceMetrics: SourceMetrics = SourceMetrics(),
        wallhavenProviderEnabled: Boolean = true,
        bingProviderEnabled: Boolean = true,
        pexelsProviderEnabled: Boolean = true,
        pixabayProviderEnabled: Boolean = true,
        pexelsApiKey: String = "",
        pixabayApiKey: String = "",
    ): WallpaperRepository {
        val prefs = mockk<PreferencesManager>()
        every { prefs.wallhavenProviderEnabled } returns flowOf(wallhavenProviderEnabled)
        every { prefs.bingProviderEnabled } returns flowOf(bingProviderEnabled)
        every { prefs.pexelsProviderEnabled } returns flowOf(pexelsProviderEnabled)
        every { prefs.pixabayProviderEnabled } returns flowOf(pixabayProviderEnabled)
        every { prefs.wallhavenApiKey } returns flowOf("")
        every { prefs.pexelsApiKey } returns flowOf(pexelsApiKey)
        every { prefs.pixabayApiKey } returns flowOf(pixabayApiKey)
        every { prefs.showSketchyContent } returns flowOf(false)
        every { prefs.showNsfwContent } returns flowOf(false)
        every { prefs.preferredResolution } returns flowOf("1080x1920")
        return WallpaperRepository(
            wallhavenApi = wallhavenApi,
            bingApi = bingApi,
            nasaApodApi = nasaApodApi,
            wikimediaPotdApi = wikimediaPotdApi,
            pixabayApi = pixabayApi,
            pexelsApi = pexelsApi,
            cacheManager = cacheManager,
            prefs = prefs,
            sourceMetrics = sourceMetrics,
        )
    }

    private fun http429(vararg headers: Pair<String, String>): HttpException {
        val headerPairs = headers.flatMap { listOf(it.first, it.second) }.toTypedArray()
        val rawResponse = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://pixabay.com/api/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(429)
            .message("Too Many Requests")
            .headers(Headers.headersOf(*headerPairs))
            .build()
        return HttpException(Response.error<Any>("".toResponseBody(null), rawResponse))
    }

    private fun wallhavenResponse(id: String) = WallhavenSearchResponse(
        data = listOf(
            WallhavenWallpaper(
                id = id,
                url = "https://wallhaven.cc/w/$id",
                dimensionX = 1440,
                dimensionY = 3200,
                path = "https://w.wallhaven.cc/full/$id.jpg",
                thumbs = WallhavenThumbs(
                    large = "https://th.wallhaven.cc/lg/$id.jpg",
                ),
            ),
        ),
        meta = WallhavenMeta(currentPage = 1, lastPage = 1, total = 1),
    )

    private fun pixabayPhotoResponse(id: String) = PixabayPhotoResponse(
        total = 1,
        totalHits = 1,
        hits = listOf(
            PixabayPhoto(
                id = id.hashCode().toLong(),
                pageUrl = "https://pixabay.com/photos/$id/",
                tags = "abstract, phone",
                webformatUrl = "https://cdn.pixabay.com/photo/$id-web.jpg",
                largeImageUrl = "https://cdn.pixabay.com/photo/$id-large.jpg",
                imageWidth = 1440,
                imageHeight = 3200,
                user = "Pixabay Maker",
            ),
        ),
    )

    private fun bingResponse(id: String) = BingImageResponse(
        images = listOf(
            BingImage(
                startDate = "20260611",
        urlbase = "/th?id=OHR.$id",
                copyright = "Daily image",
                title = "Daily image",
            ),
        ),
    )

    private fun wallpaper(
        id: String,
        source: ContentSource = ContentSource.WALLHAVEN,
    ) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        fullUrl = "https://example.com/$id.jpg",
        width = 1440,
        height = 3200,
    )
}
