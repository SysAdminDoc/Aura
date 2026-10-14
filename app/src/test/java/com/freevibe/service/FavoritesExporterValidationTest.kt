package com.freevibe.service

import com.freevibe.data.model.SOURCE_AVAILABILITY_AVAILABLE
import com.freevibe.data.model.SOURCE_AVAILABILITY_UNAVAILABLE
import com.freevibe.data.model.isSourceUnavailable
import com.freevibe.data.model.LocalMediaStatus
import com.freevibe.data.model.VideoFavoriteStream
import com.freevibe.data.model.toVideoWallpaperItem
import com.freevibe.data.model.videoFavoriteStream
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesExporterValidationTest {

    private val adapter = Moshi.Builder().build().adapter(FavoritesExportFile::class.java)

    @Test
    fun `imported favorites only allow https urls`() {
        // v6.5.0 hardened the import path to HTTPS-only (defense-in-depth against
        // exported payloads that embed cleartext or local-file URLs).
        assertTrue(isAllowedImportedFavoriteUrl("https://example.com/wallpaper.jpg"))
        assertFalse(isAllowedImportedFavoriteUrl("http://example.com/sound.mp3"))
        assertFalse(isAllowedImportedFavoriteUrl("file:///sdcard/payload.jpg"))
        assertFalse(isAllowedImportedFavoriteUrl("content://media/external/images/1"))
        assertFalse(isAllowedImportedFavoriteUrl("javascript:alert(1)"))
        assertFalse(isAllowedImportedFavoriteUrl("https://example.com/" + "x".repeat(2048)))
    }

    @Test
    fun `validated favorite entity rejects unsafe full urls`() {
        val entity = FavoriteExportItem(
            id = "sound_1",
            source = "YOUTUBE",
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "file:///sdcard/ringtone.mp3",
            name = "Bad import",
        ).toValidatedEntity()

        assertFalse(entity != null)
    }

    @Test
    fun `locator free local favorite restores as relinkable metadata`() {
        val hash = "ab".repeat(32)
        val entity = FavoriteExportItem(
            id = "local-$hash",
            source = "LOCAL",
            type = "WALLPAPER",
            thumbnailUrl = "",
            fullUrl = "",
            name = "Family photo",
            width = 1080,
            height = 1920,
            localMedia = true,
            localMediaSha256 = hash,
        ).toValidatedEntity()

        assertNotNull(entity)
        assertEquals(LocalMediaStatus.MISSING, entity?.localMediaStatus)
        assertEquals("", entity?.fullUrl)
        assertEquals(hash, entity?.localMediaSha256)
    }

    @Test
    fun `portable local identity never contains the device locator`() {
        val locator = "content://private.provider/tree/photo.png"

        val id = portableLocalMediaId("LOCAL", locator)

        assertTrue(isSafePortableLocalMediaId(id))
        assertFalse(id.contains(locator))
        assertFalse(id.contains("private.provider"))
    }

    @Test
    fun `validated favorite entity keeps supported remote urls`() {
        val entity = FavoriteExportItem(
            id = "wallpaper_1",
            source = "WALLHAVEN",
            type = "WALLPAPER",
            thumbnailUrl = "https://example.com/thumb.jpg",
            fullUrl = "https://example.com/full.jpg",
            sourcePageUrl = "https://example.com/post",
        ).toValidatedEntity()

        assertNotNull(entity)
        assertEquals(SOURCE_AVAILABILITY_AVAILABLE, entity?.sourceAvailability)
    }

    @Test
    fun `validated favorite entity rejects unknown sources`() {
        val entity = FavoriteExportItem(
            id = "wallpaper_1",
            source = "UNKNOWN_PROVIDER",
            type = "WALLPAPER",
            thumbnailUrl = "https://example.com/thumb.jpg",
            fullUrl = "https://example.com/full.jpg",
        ).toValidatedEntity()

        assertFalse(entity != null)
    }

    @Test
    fun `validated favorite entity preserves source unavailable metadata`() {
        val entity = FavoriteExportItem(
            id = "rd_missing",
            source = "reddit",
            type = "wallpaper",
            thumbnailUrl = "https://example.com/thumb.jpg",
            fullUrl = "https://example.com/full.jpg",
            sourceAvailability = SOURCE_AVAILABILITY_UNAVAILABLE,
            sourceAvailabilityReason = "Post was removed",
        ).toValidatedEntity()

        assertNotNull(entity)
        assertTrue(entity?.isSourceUnavailable() == true)
        assertEquals("Post was removed", entity?.sourceAvailabilityReason)
    }

    @Test
    fun `validated sound favorite preserves license metadata`() {
        val entity = FavoriteExportItem(
            id = "fs_42",
            source = "freesound",
            type = "sound",
            thumbnailUrl = "",
            fullUrl = "https://example.com/sound.mp3",
            name = "Bird call",
            license = "CC BY-NC",
            uploaderName = "recorder",
            sourcePageUrl = "https://freesound.org/s/42/",
        ).toValidatedEntity()

        assertNotNull(entity)
        assertEquals("CC BY-NC", entity?.license)
    }

    private fun videoItem(
        id: String = "yt_dQw4w9WgXcQ",
        source: String = "YOUTUBE",
        type: String = "VIDEO",
        fullUrl: String = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        thumbnailUrl: String = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        localMedia: Boolean = false,
    ) = FavoriteExportItem(
        id = id,
        source = source,
        type = type,
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        name = "Neon rain loop",
        width = 1080,
        height = 1920,
        duration = 42.0,
        uploaderName = "Loop Studio",
        sourcePageUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        license = "YouTube",
        fileType = "video/mp4",
        views = 12_345L,
        addedAt = 5L,
        localMedia = localMedia,
    )

    @Test
    fun `an imported video favorite keeps what finds its stream again`() {
        val entity = videoItem().toValidatedEntity()

        assertNotNull(entity)
        assertEquals("VIDEO", entity?.type)
        assertEquals("YOUTUBE", entity?.source)
        assertEquals("yt_dQw4w9WgXcQ", entity?.id)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", entity?.fullUrl)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", entity?.sourcePageUrl)
        assertEquals("Loop Studio", entity?.uploaderName)
        assertEquals("YouTube", entity?.license)
        assertEquals(1080, entity?.width)
        assertEquals(1920, entity?.height)
        assertEquals(42.0, entity?.duration ?: -1.0, 0.0)
        assertEquals("video/mp4", entity?.fileType)
        assertEquals(12_345L, entity?.views)
        assertEquals(LocalMediaStatus.AVAILABLE, entity?.localMediaStatus)
        assertEquals(VideoFavoriteStream.YouTube("dQw4w9WgXcQ"), entity?.videoFavoriteStream())
        assertEquals("YouTube", entity?.toVideoWallpaperItem()?.source)
    }

    @Test
    fun `an imported video address is kept the one way the app writes it`() {
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            videoItem(type = "video", fullUrl = "https://youtu.be/dQw4w9WgXcQ").toValidatedEntity()?.fullUrl,
        )
        // A video needs no thumbnail to play, so a row without one is still restored.
        val reddit = videoItem(
            id = "rd_1abc2de",
            source = "reddit",
            fullUrl = "https://v.redd.it/abc123xyz",
            thumbnailUrl = "",
        ).toValidatedEntity()
        assertEquals("REDDIT", reddit?.source)
        assertEquals("https://v.redd.it/abc123xyz/HLSPlaylist.m3u8", reddit?.fullUrl)
        assertEquals(
            VideoFavoriteStream.Direct("https://v.redd.it/abc123xyz/HLSPlaylist.m3u8"),
            reddit?.videoFavoriteStream(),
        )
        assertEquals(
            "https://videos.pexels.com/video-files/9/9-hd.mp4",
            videoItem(
                id = "px_9",
                source = "PEXELS",
                fullUrl = "https://videos.pexels.com/video-files/9/9-hd.mp4",
            ).toValidatedEntity()?.fullUrl,
        )
    }

    @Test
    fun `an imported video favorite that points somewhere else is refused`() {
        listOf(
            // Another video's page, a look-alike host, cleartext, a stream address, nothing at all.
            videoItem(fullUrl = "https://www.youtube.com/watch?v=AAAAAAAAAAA"),
            videoItem(fullUrl = "https://media.example/watch?v=dQw4w9WgXcQ"),
            videoItem(fullUrl = "http://www.youtube.com/watch?v=dQw4w9WgXcQ"),
            videoItem(fullUrl = "https://rr1---sn-abc.googlevideo.com/videoplayback?id=dQw4w9WgXcQ"),
            videoItem(fullUrl = ""),
            // A playlist the downloader would follow to a host of the file's choosing.
            videoItem(id = "rd_1abc2de", source = "REDDIT", fullUrl = "https://media.example/abc123xyz/HLSPlaylist.m3u8"),
            videoItem(id = "px_9", source = "PEXELS", fullUrl = "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8"),
            // Something on the phone that made the file.
            videoItem(id = "px_9", source = "PEXELS", fullUrl = "content://media/external/video/media/9"),
            videoItem(id = "px_9", source = "PEXELS", fullUrl = "file:///storage/emulated/0/Movies/9.mp4"),
            videoItem(id = "local-" + "ab".repeat(32), source = "LOCAL", fullUrl = "", thumbnailUrl = "", localMedia = true),
            // A provider's video marked as a file to pick again would come back with no address.
            videoItem(localMedia = true),
            // An ID that isn't the provider's, and a source with no videos.
            videoItem(id = "dQw4w9WgXcQ"),
            videoItem(id = "wall-1", source = "WALLHAVEN", fullUrl = "https://w.wallhaven.cc/full/wall-1.mp4"),
            // The thumbnail gets the same https check every imported favorite gets.
            videoItem(thumbnailUrl = "http://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"),
        ).forEach { item ->
            assertNull("${item.source} ${item.id} ${item.fullUrl}", item.toValidatedEntity())
        }
    }

    @Test
    fun `a favorite of a type this build doesn't know is refused on its own`() {
        // The path a build from before saved videos takes for a VIDEO row: it counts as failed and
        // the rows around it are still imported.
        assertNull(videoItem(type = "HOLOGRAM").toValidatedEntity())
        assertNotNull(videoItem().toValidatedEntity())
    }

    @Test
    fun `a video favorite survives the favorites file as written`() {
        val file = FavoritesExportFile(version = 2, exportedAt = 1, items = listOf(videoItem()))

        val restored = adapter.fromJson(adapter.toJson(file))

        assertEquals(2, restored?.version)
        assertEquals(listOf(videoItem()), restored?.items)
    }

    @Test
    fun `documented maximum favorites round trips without loss`() {
        val expected = List(LibraryTransferContract.MAX_FAVORITES) { index ->
            FavoriteExportItem(
                id = "favorite-$index",
                source = "REDDIT",
                type = "WALLPAPER",
                thumbnailUrl = "https://i.example/$index.jpg",
                fullUrl = "https://preview.example/$index.jpg",
            )
        }
        val file = FavoritesExportFile(version = 1, exportedAt = 1, items = expected)

        val restored = adapter.fromJson(adapter.toJson(file))

        assertEquals(expected, restored?.items)
    }
}
