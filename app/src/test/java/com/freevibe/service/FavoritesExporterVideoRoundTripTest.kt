package com.freevibe.service

import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.toVideoWallpaperItem
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Saved videos through the favorites file, written and read by the real exporter against Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoritesExporterVideoRoundTripTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private lateinit var db: FreeVibeDatabase
    private lateinit var exporter: FavoritesExporter

    private val picture = FavoriteEntity(
        id = "wall-1",
        source = "REDDIT",
        type = "WALLPAPER",
        thumbnailUrl = "https://i.redd.it/wall-1-thumb.jpg",
        fullUrl = "https://i.redd.it/wall-1.jpg",
        name = "Ridge",
        addedAt = 1L,
    )
    private val youtube = FavoriteEntity(
        id = "yt_dQw4w9WgXcQ",
        source = "YOUTUBE",
        type = "VIDEO",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        fullUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        name = "Neon rain loop",
        width = 1080,
        height = 1920,
        duration = 42.0,
        addedAt = 2L,
        uploaderName = "Loop Studio",
        sourcePageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        license = "YouTube",
        fileType = "video/mp4",
        views = 12_345L,
    )
    private val reddit = FavoriteEntity(
        id = "rd_1abc2de",
        source = "REDDIT",
        type = "VIDEO",
        thumbnailUrl = "https://b.thumbs.redditmedia.com/rain.jpg",
        fullUrl = "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8",
        name = "Rain on glass",
        width = 1080,
        height = 1920,
        duration = 9.0,
        addedAt = 3L,
        uploaderName = "u/maker",
        sourcePageUrl = "https://www.reddit.com/r/LiveWallpapers/comments/1abc2de/rain_on_glass/",
        fileType = "application/x-mpegURL",
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FreeVibeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        exporter = FavoritesExporter(context, db.favoriteDao(), Moshi.Builder().build())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `saved videos come back from a favorites file`() = runTest {
        val saved = listOf(picture, youtube, reddit)
        saved.forEach { db.favoriteDao().insert(it) }
        val file = File(context.cacheDir, "favorites-videos-${System.nanoTime()}.json")

        try {
            assertEquals(3, exporter.export(Uri.fromFile(file)).getOrThrow())
            val parsed = Moshi.Builder().build().adapter(FavoritesExportFile::class.java).fromJson(file.readText())!!
            // Videos are rows of a new type in the list the file already has, so its version stays put.
            assertEquals(2, parsed.version)
            assertEquals(listOf("VIDEO", "VIDEO", "WALLPAPER"), parsed.items.map { it.type })
            assertTrue(parsed.items.none { it.localMedia })
            saved.forEach { db.favoriteDao().deleteByIdentity(it.id, it.source, it.type) }
            assertEquals(0, db.favoriteDao().count().first())

            val outcome = exporter.import(Uri.fromFile(file)).getOrThrow()

            assertEquals(FavoriteImportOutcome(imported = 3, skipped = 0, failed = 0), outcome)
            val videos = db.favoriteDao().getByType("VIDEO").first()
            assertEquals(listOf(reddit, youtube), videos)
            assertEquals(listOf("Reddit", "YouTube"), videos.map { it.toVideoWallpaperItem()?.source })
            assertEquals(listOf(picture), db.favoriteDao().getByType("WALLPAPER").first())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `a bad video row is counted and the rest of the file is imported`() = runTest {
        val file = File(context.cacheDir, "favorites-mixed-${System.nanoTime()}.json")
        file.writeText(
            """
            {"version":2,"exportedAt":1,"items":[
              {"id":"yt_dQw4w9WgXcQ","source":"YOUTUBE","type":"VIDEO","thumbnailUrl":"",
               "fullUrl":"https://youtu.be/dQw4w9WgXcQ","name":"Neon rain loop"},
              {"id":"rd_1abc2de","source":"REDDIT","type":"VIDEO","thumbnailUrl":"",
               "fullUrl":"https://media.example/abc123xyz/HLSPlaylist.m3u8","name":"Stray playlist"},
              {"id":"wall-1","source":"REDDIT","type":"WALLPAPER",
               "thumbnailUrl":"https://i.redd.it/wall-1-thumb.jpg","fullUrl":"https://i.redd.it/wall-1.jpg","name":"Ridge"}
            ]}
            """.trimIndent(),
        )

        try {
            assertEquals(
                FavoriteImportOutcome(imported = 2, skipped = 0, failed = 1),
                exporter.import(Uri.fromFile(file)).getOrThrow(),
            )
            val video = db.favoriteDao().getByType("VIDEO").first().single()
            assertEquals("yt_dQw4w9WgXcQ", video.id)
            // Kept as the video's page, the way a video saved on this phone is.
            assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", video.fullUrl)
            assertEquals(listOf("wall-1"), db.favoriteDao().getByType("WALLPAPER").first().map { it.id })

            // The same file again adds nothing: both good rows are already here.
            assertEquals(
                FavoriteImportOutcome(imported = 0, skipped = 2, failed = 1),
                exporter.import(Uri.fromFile(file)).getOrThrow(),
            )
        } finally {
            file.delete()
        }
    }
}
