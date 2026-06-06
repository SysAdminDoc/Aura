package com.freevibe.data.repository

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.wallhaven.WallhavenApi
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
        pixabayApi: PixabayApi = mockk(),
        cacheManager: WallpaperCacheManager = mockk(relaxed = true),
        sourceMetrics: SourceMetrics = SourceMetrics(),
        wallhavenProviderEnabled: Boolean = true,
        bingProviderEnabled: Boolean = true,
        pixabayProviderEnabled: Boolean = true,
        pixabayApiKey: String = "",
    ): WallpaperRepository {
        val prefs = mockk<PreferencesManager>()
        every { prefs.wallhavenProviderEnabled } returns flowOf(wallhavenProviderEnabled)
        every { prefs.bingProviderEnabled } returns flowOf(bingProviderEnabled)
        every { prefs.pixabayProviderEnabled } returns flowOf(pixabayProviderEnabled)
        every { prefs.pixabayApiKey } returns flowOf(pixabayApiKey)
        return WallpaperRepository(
            wallhavenApi = wallhavenApi,
            bingApi = bingApi,
            pixabayApi = pixabayApi,
            pexelsApi = mockk<PexelsApi>(),
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
