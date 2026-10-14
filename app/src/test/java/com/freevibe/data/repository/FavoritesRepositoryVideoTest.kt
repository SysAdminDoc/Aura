package com.freevibe.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.VideoFavoriteStream
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.toVideoWallpaperItem
import com.freevibe.data.model.videoFavoriteStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoritesRepositoryVideoTest {
    private lateinit var database: FreeVibeDatabase
    private lateinit var repository: FavoritesRepository

    private val picture = FavoriteEntity(
        id = "wall-1",
        source = "REDDIT",
        type = "WALLPAPER",
        thumbnailUrl = "https://i.redd.it/wall-1-thumb.jpg",
        fullUrl = "https://i.redd.it/wall-1.jpg",
        name = "Ridge",
        addedAt = 1L,
    )
    private val sound = FavoriteEntity(
        id = "sound-1",
        source = "BUNDLED",
        type = "SOUND",
        thumbnailUrl = "",
        fullUrl = "rawresource:///aura_chime",
        name = "Chime",
        addedAt = 2L,
    )
    private val youtubeVideo = FavoriteEntity(
        id = "yt_dQw4w9WgXcQ",
        source = "YOUTUBE",
        type = "VIDEO",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        fullUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        name = "Neon rain loop",
        width = 1080,
        height = 1920,
        duration = 42.0,
        addedAt = 3L,
        uploaderName = "Loop Studio",
        sourcePageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        license = "YouTube",
        fileType = "video/mp4",
        views = 12_345L,
    )
    private val redditVideo = FavoriteEntity(
        id = "rd_1abc2de",
        source = "REDDIT",
        type = "VIDEO",
        thumbnailUrl = "https://b.thumbs.redditmedia.com/rain.jpg",
        fullUrl = "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8",
        name = "Rain on glass",
        width = 1080,
        height = 1920,
        duration = 9.0,
        addedAt = 4L,
        uploaderName = "u/maker",
        sourcePageUrl = "https://www.reddit.com/r/LiveWallpapers/comments/1abc2de/rain_on_glass/",
        fileType = "application/x-mpegURL",
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FreeVibeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = FavoritesRepository(database.favoriteDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `saved videos are their own list and stay out of the picture and sound lists`() = runTest {
        listOf(picture, sound, youtubeVideo, redditVideo).forEach { repository.add(it) }

        // Rotation and Download all read getWallpapers(), so a video must never show up there.
        assertEquals(listOf("wall-1"), repository.getWallpapers().first().map { it.id })
        assertEquals(listOf("sound-1"), repository.getSounds().first().map { it.id })
        assertEquals(listOf("rd_1abc2de", "yt_dQw4w9WgXcQ"), repository.getVideos().first().map { it.id })
        assertEquals(4, repository.count().first())
    }

    @Test
    fun `a saved video keeps every field after it is read back`() = runTest {
        repository.add(youtubeVideo)
        repository.add(redditVideo)

        val stored = repository.getVideos().first().associateBy { it.id }

        assertEquals(youtubeVideo, stored["yt_dQw4w9WgXcQ"])
        assertEquals(redditVideo, stored["rd_1abc2de"])
        assertEquals(
            VideoFavoriteStream.YouTube("dQw4w9WgXcQ"),
            stored.getValue("yt_dQw4w9WgXcQ").videoFavoriteStream(),
        )
        assertEquals(
            VideoFavoriteStream.Direct("https://v.redd.it/abc123xyz/HLSPlaylist.m3u8"),
            stored.getValue("rd_1abc2de").videoFavoriteStream(),
        )
        assertEquals("Reddit", stored.getValue("rd_1abc2de").toVideoWallpaperItem()?.source)
    }

    @Test
    fun `a picture and a video with one ID are two favorites`() = runTest {
        val pictureTwin = picture.copy(id = redditVideo.id)
        repository.add(pictureTwin)
        repository.add(redditVideo)

        assertEquals(listOf(pictureTwin), repository.getWallpapers().first())
        assertEquals(listOf(redditVideo), repository.getVideos().first())

        // Removing the video leaves the picture alone, and the other way round.
        repository.remove(redditVideo.favoriteIdentity())
        assertTrue(repository.getVideos().first().isEmpty())
        assertEquals(listOf(pictureTwin), repository.getWallpapers().first())
        assertNull(repository.getByIdentity(FavoriteIdentity(redditVideo.id, "REDDIT", "VIDEO")))
        assertEquals(pictureTwin, repository.getByIdentity(FavoriteIdentity(redditVideo.id, "REDDIT", "WALLPAPER")))
    }

    @Test
    fun `a video is a favorite only under its own type`() = runTest {
        repository.add(youtubeVideo)

        assertTrue(repository.isFavorite(FavoriteIdentity("yt_dQw4w9WgXcQ", "YOUTUBE", "VIDEO")).first())
        assertFalse(repository.isFavorite(FavoriteIdentity("yt_dQw4w9WgXcQ", "YOUTUBE", "WALLPAPER")).first())
        assertFalse(repository.isFavorite(FavoriteIdentity("yt_dQw4w9WgXcQ", "YOUTUBE", "SOUND")).first())
    }
}
