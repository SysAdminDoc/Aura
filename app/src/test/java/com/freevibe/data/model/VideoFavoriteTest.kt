package com.freevibe.data.model

import com.freevibe.ui.screens.videowallpapers.VideoWallpaperItem
import com.freevibe.ui.screens.videowallpapers.itemActionAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFavoriteTest {

    private val youtubeItem = VideoWallpaperItem(
        id = "yt_dQw4w9WgXcQ",
        title = "Neon rain loop",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        source = "YouTube",
        duration = 42,
        uploaderName = "Loop Studio",
        videoId = "dQw4w9WgXcQ",
        popularity = 12_345,
        videoWidth = 1080,
        videoHeight = 1920,
        videoMimeType = "video/mp4",
        contentSource = ContentSource.YOUTUBE,
        license = "YouTube",
        sourcePageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    )

    private val redditItem = VideoWallpaperItem(
        id = "rd_1abc2de",
        title = "Rain on glass",
        thumbnailUrl = "https://b.thumbs.redditmedia.com/rain.jpg",
        source = "Reddit",
        duration = 9,
        uploaderName = "u/maker",
        videoWidth = 1080,
        videoHeight = 1920,
        videoMimeType = "application/x-mpegURL",
        contentSource = ContentSource.REDDIT,
        sourcePageUrl = "https://www.reddit.com/r/LiveWallpapers/comments/1abc2de/rain_on_glass/",
    )

    private val pexelsItem = VideoWallpaperItem(
        id = "px_9",
        title = "By Ana",
        thumbnailUrl = "https://images.pexels.com/videos/9/preview.jpg",
        source = "Pexels",
        duration = 14,
        uploaderName = "Ana",
        videoWidth = 2160,
        videoHeight = 3840,
        contentSource = ContentSource.PEXELS,
        license = "Pexels License",
        sourcePageUrl = "https://www.pexels.com/video/9/",
    )

    private val pixabayItem = VideoWallpaperItem(
        id = "pbv_42",
        title = "ink smoke loop",
        thumbnailUrl = "https://i.vimeocdn.com/video/42_640x360.jpg",
        source = "Pixabay",
        duration = 20,
        uploaderName = "Kim",
        popularity = 900,
        videoWidth = 1080,
        videoHeight = 1920,
        contentSource = ContentSource.PIXABAY,
        license = "Pixabay License",
        sourcePageUrl = "https://pixabay.com/videos/id-42/",
    )

    private val redditClip = "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8"
    private val pexelsFile = "https://videos.pexels.com/video-files/9/9-uhd_2160_3840_30fps.mp4"
    private val pixabayFile = "https://cdn.pixabay.com/video/2024/01/01/42-large.mp4"

    private fun saved(item: VideoWallpaperItem, streamUrl: String?): FavoriteEntity =
        requireNotNull(item.toVideoFavoriteEntity(streamUrl, addedAt = 1_000L)) { "${item.id} was not saved" }

    @Test
    fun `a YouTube video is saved by its page and never by its stream`() {
        val row = saved(youtubeItem, "https://rr3---sn-abc.googlevideo.com/videoplayback?expire=1760000000&id=x")

        assertEquals("yt_dQw4w9WgXcQ", row.id)
        assertEquals("YOUTUBE", row.source)
        assertEquals("VIDEO", row.type)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", row.fullUrl)
        assertEquals(VideoFavoriteStream.YouTube("dQw4w9WgXcQ"), row.videoFavoriteStream())
        // No stream is needed to save it: the video ID alone finds it again.
        assertEquals(row, saved(youtubeItem, streamUrl = null))
    }

    @Test
    fun `a saved row holds what Apply and the preview need`() {
        val row = saved(youtubeItem, streamUrl = null)

        assertEquals("Neon rain loop", row.name)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", row.thumbnailUrl)
        assertEquals(1080, row.width)
        assertEquals(1920, row.height)
        assertEquals(42.0, row.duration, 0.0)
        assertEquals("Loop Studio", row.uploaderName)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", row.sourcePageUrl)
        assertEquals("YouTube", row.license)
        assertEquals("video/mp4", row.fileType)
        assertEquals(12_345L, row.views)
        assertEquals(1_000L, row.addedAt)
        assertEquals("", row.offlinePath)
        assertEquals(
            FavoriteIdentity(id = "yt_dQw4w9WgXcQ", source = "YOUTUBE", type = "VIDEO"),
            youtubeItem.videoFavoriteIdentity(),
        )
    }

    @Test
    fun `a saved video comes back as the feed item it was`() {
        listOf(
            youtubeItem to null,
            redditItem to redditClip,
            pexelsItem to pexelsFile,
            pixabayItem to pixabayFile,
        ).forEach { (item, streamUrl) ->
            assertEquals(item, saved(item, streamUrl).toVideoWallpaperItem())
        }
    }

    @Test
    fun `a Reddit hosted clip is found again by its clip ID`() {
        listOf(
            "https://v.redd.it/abc123xyz",
            "https://v.redd.it/abc123xyz/",
            "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8",
            "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8?a=1762000000%2Cdeadbeef&v=1&f=sd",
            "https://v.redd.it/abc123xyz/HLS_540.m3u8",
        ).forEach { streamUrl ->
            val row = saved(redditItem, streamUrl)
            assertEquals(streamUrl, redditClip, row.fullUrl)
            assertEquals(streamUrl, VideoFavoriteStream.Direct(redditClip), row.videoFavoriteStream())
        }
    }

    @Test
    fun `a Reddit file keeps its own address`() {
        listOf(
            "https://i.redd.it/loop.gif",
            "https://preview.redd.it/loop.mp4?format=mp4&s=abc",
            "https://v.redd.it/abc123xyz/DASH_720.mp4",
        ).forEach { streamUrl ->
            assertEquals(VideoFavoriteStream.Direct(streamUrl), saved(redditItem, streamUrl).videoFavoriteStream())
        }
        assertEquals(VideoFavoriteStream.Direct(pexelsFile), saved(pexelsItem, pexelsFile).videoFavoriteStream())
    }

    @Test
    fun `a playlist on any host but Reddit's is refused`() {
        listOf(
            "https://media.example/stream/HLSPlaylist.m3u8",
            "https://v.redd.it.media.example/abc123xyz/HLSPlaylist.m3u8",
            "https://v.redd.it@media.example/abc123xyz/HLSPlaylist.m3u8",
            "https://media.example/abc123xyz/HLSPlaylist.m3u8?next=https://v.redd.it/abc123xyz",
        ).forEach { streamUrl ->
            assertNull(streamUrl, redditItem.toVideoFavoriteEntity(streamUrl))
            assertNull(streamUrl, videoFavoriteStream("REDDIT", "rd_1abc2de", streamUrl))
        }
        // Only a Reddit row may carry a playlist, so Reddit's own playlist on a Pexels row is refused too.
        assertNull(pexelsItem.toVideoFavoriteEntity("https://videos.pexels.com/9/master.m3u8"))
        assertNull(videoFavoriteStream("PEXELS", "px_9", redditClip))
        assertNull(videoFavoriteStream("PIXABAY", "pbv_42", "https://cdn.pixabay.com/video/42/index.M3U8?x=1"))
    }

    @Test
    fun `a YouTube row has to name its own video page`() {
        val id = "yt_dQw4w9WgXcQ"
        listOf(
            "https://www.youtube.com/watch?v=AAAAAAAAAAA",
            "https://media.example/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com.media.example/watch?v=dQw4w9WgXcQ",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://rr1---sn-abc.googlevideo.com/videoplayback?v=dQw4w9WgXcQ",
            "https://www.youtube.com/",
            "",
        ).forEach { page ->
            assertNull(page, videoFavoriteStream("YOUTUBE", id, page))
            assertNull(page, normalizedVideoFavoriteLocator("YOUTUBE", id, page))
        }
        // The ID in the row is what gets resolved, so it has to look like one.
        assertNull(videoFavoriteStream("YOUTUBE", "yt_short", "https://www.youtube.com/watch?v=short"))
        assertNull(videoFavoriteStream("YOUTUBE", "dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(youtubeItem.copy(videoId = "").toVideoFavoriteEntity("https://rr1.googlevideo.com/videoplayback"))
    }

    @Test
    fun `any spelling of a YouTube page is kept as the watch page`() {
        listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            " https://youtu.be/dQw4w9WgXcQ?t=3 ",
            "https://m.youtube.com/watch?feature=share&v=dQw4w9WgXcQ",
            "https://youtube.com/watch?v=dQw4w9WgXcQ&list=PL1",
        ).forEach { page ->
            assertEquals(
                page,
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                normalizedVideoFavoriteLocator("youtube", "yt_dQw4w9WgXcQ", page),
            )
        }
    }

    @Test
    fun `only a video provider's own row is a video favorite`() {
        // A source with no videos, and an ID that isn't that provider's.
        assertNull(videoFavoriteStream("LOCAL", "local_1", pexelsFile))
        assertNull(videoFavoriteStream("WALLHAVEN", "yt_dQw4w9WgXcQ", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(videoFavoriteStream("REDDIT", "yt_dQw4w9WgXcQ", redditClip))
        assertNull(videoFavoriteStream("REDDIT", "rd_", redditClip))
        // An address that isn't https, or isn't an address.
        listOf(
            "http://videos.pexels.com/video-files/9/9.mp4",
            "content://media/external/video/media/9",
            "file:///storage/emulated/0/Movies/9.mp4",
            "/storage/emulated/0/Movies/9.mp4",
            " ",
        ).forEach { locator ->
            assertNull(locator, videoFavoriteStream("PEXELS", "px_9", locator))
        }
    }

    @Test
    fun `a clip from the phone or a video with no stream yet is not saved`() {
        val galleryClip = VideoWallpaperItem(
            id = "gallery_42",
            title = "My clip",
            thumbnailUrl = "",
            source = "Gallery",
        )

        assertFalse(galleryClip.canBeVideoFavorite())
        assertNull(galleryClip.toVideoFavoriteEntity("content://media/external/video/media/42"))
        assertNull(galleryClip.toVideoFavoriteEntity(pexelsFile))

        assertTrue(redditItem.canBeVideoFavorite())
        assertNull(redditItem.toVideoFavoriteEntity(streamUrl = null))
        assertNull(pexelsItem.toVideoFavoriteEntity(streamUrl = null))
    }

    @Test
    fun `a wallpaper or sound row is never read as a video`() {
        val row = saved(youtubeItem, streamUrl = null)

        listOf("WALLPAPER", "SOUND").forEach { type ->
            val other = row.copy(type = type)
            assertFalse(type, other.isVideoFavorite())
            assertNull(type, other.videoFavoriteStream())
            assertNull(type, other.toVideoWallpaperItem())
        }
        assertTrue(row.copy(type = "video").isVideoFavorite())
    }

    @Test
    fun `a saved video goes through the feed's own license checks`() {
        listOf(
            youtubeItem to null,
            redditItem to redditClip,
            pexelsItem to pexelsFile,
            pixabayItem to pixabayFile,
        ).forEach { (item, streamUrl) ->
            val restored = requireNotNull(saved(item, streamUrl).toVideoWallpaperItem()) { "${item.id} did not come back" }

            // The policy reads these four, so each has to survive the trip.
            assertEquals(item.contentSource, restored.contentSource)
            assertEquals(item.license, restored.license)
            assertEquals(item.sourcePageUrl, restored.sourcePageUrl)
            assertEquals(item.uploaderName, restored.uploaderName)
            assertEquals(item.id, item.videoWallpaperLicenseCapabilities(), restored.videoWallpaperLicenseCapabilities())
            assertEquals(item.id, item.itemActionAvailability(), restored.itemActionAvailability())
            VideoWallpaperAction.entries.forEach { action ->
                assertEquals("${item.id} $action", item.canUseVideoAction(action), restored.canUseVideoAction(action))
                assertEquals(
                    "${item.id} $action",
                    item.requiresVideoActionConfirmation(action),
                    restored.requiresVideoActionConfirmation(action),
                )
            }
        }
    }

    @Test
    fun `a saved row with no creator loses Share the way a feed item does`() {
        val row = saved(redditItem, redditClip).copy(uploaderName = null)
        val restored = requireNotNull(row.toVideoWallpaperItem())
        val feedItem = redditItem.copy(uploaderName = "")

        assertEquals(feedItem.videoWallpaperLicenseCapabilities(), restored.videoWallpaperLicenseCapabilities())
        assertFalse(restored.canUseVideoAction(VideoWallpaperAction.SHARE))
        assertFalse(restored.itemActionAvailability().canShare)
    }
}
