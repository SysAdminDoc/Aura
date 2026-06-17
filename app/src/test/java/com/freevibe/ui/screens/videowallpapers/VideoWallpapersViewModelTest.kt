package com.freevibe.ui.screens.videowallpapers

import com.freevibe.data.remote.pixabay.PixabayVideo
import com.freevibe.data.remote.pixabay.PixabayVideoFile
import com.freevibe.data.remote.pixabay.PixabayVideoFiles
import com.freevibe.data.repository.YouTubeVideoMetadata
import com.freevibe.data.repository.sanitizeVoteKey
import com.freevibe.util.rethrowIfCancelled
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class VideoWallpapersViewModelTest {

    @Test
    fun `resolveVideoLoadProgress stops pagination after three empty batches`() {
        val first = resolveVideoLoadProgress(previousEmptyLoadCount = 0, newItemCount = 0)
        val second = resolveVideoLoadProgress(previousEmptyLoadCount = first.emptyLoadCount, newItemCount = 0)
        val third = resolveVideoLoadProgress(previousEmptyLoadCount = second.emptyLoadCount, newItemCount = 0)

        assertTrue(first.hasMore)
        assertTrue(second.hasMore)
        assertFalse(third.hasMore)
        assertEquals(3, third.emptyLoadCount)
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
        assertEquals(
            PixabayVideoFetchSpec(query = "aurora", videoType = "all", page = 3),
            resolvePixabayVideoFetchSpec(searchQuery = "aurora", page = 3),
        )
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
        assertEquals("Unknown video dimensions", item.videoTechnicalSummary())
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
        assertEquals("1080x1920 (Portrait) · rotated 90deg · avc1.640028 · video/mp4", item.videoTechnicalSummary())
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
