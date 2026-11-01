package com.freevibe.service

import android.app.Application
import android.content.Context
import androidx.media3.common.Player
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoWallpaperPlaylistTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun prefs() = context.getSharedPreferences(VIDEO_WALLPAPER_PREFS_NAME, Context.MODE_PRIVATE)

    private fun clip(name: String, scaleMode: String = VIDEO_WALLPAPER_SCALE_MODE_ZOOM) = VideoPlaylistClip(
        path = "/clips/$name.fit.mp4",
        originalPath = "/clips/$name.mp4",
        name = name,
        scaleMode = scaleMode,
    )

    private fun fileOnDisk(name: String) = File(context.filesDir, name).apply { writeBytes(ByteArray(8)) }

    private fun storeActive(playlist: VideoPlaylist) {
        prefs().edit()
            .putString(VIDEO_WALLPAPER_PLAYLIST_PREF, encodeVideoPlaylist(playlist))
            .putBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, true)
            .commit()
    }

    @Test
    fun `a list survives being saved`() {
        val playlist = VideoPlaylist(
            clips = listOf(clip("beach"), clip("rain", VIDEO_WALLPAPER_SCALE_MODE_FIT)),
            shuffle = true,
            advanceSeconds = 60,
        )

        assertEquals(playlist, decodeVideoPlaylist(encodeVideoPlaylist(playlist)))
    }

    @Test
    fun `nothing saved and broken JSON both read as an empty list`() {
        assertEquals(VideoPlaylist(), decodeVideoPlaylist(null))
        assertEquals(VideoPlaylist(), decodeVideoPlaylist(""))
        assertEquals(VideoPlaylist(), decodeVideoPlaylist("{not json"))
    }

    @Test
    fun `an interval the settings don't offer means play to the end`() {
        val json = encodeVideoPlaylist(VideoPlaylist(clips = listOf(clip("beach")), advanceSeconds = 7))

        assertEquals(0, decodeVideoPlaylist(json).advanceSeconds)
    }

    @Test
    fun `a list longer than the limit is cut to it`() {
        val tooMany = VideoPlaylist(clips = (0 until MAX_VIDEO_PLAYLIST_CLIPS + 5).map { clip("clip$it") })

        val read = decodeVideoPlaylist(encodeVideoPlaylist(tooMany))

        assertEquals(MAX_VIDEO_PLAYLIST_CLIPS, read.clips.size)
        assertEquals("clip0", read.clips.first().name)
    }

    @Test
    fun `an entry with no path is dropped and missing details get filled in`() {
        val json = """{"clips":[{"path":""},{"path":"/clips/beach.mp4"}],"shuffle":false,"advanceSeconds":0}"""

        val clip = decodeVideoPlaylist(json).clips.single()

        assertEquals("/clips/beach.mp4", clip.path)
        assertEquals("/clips/beach.mp4", clip.originalPath)
        assertEquals("beach.mp4", clip.name)
        assertEquals(VIDEO_WALLPAPER_SCALE_MODE_ZOOM, clip.scaleMode)
    }

    @Test
    fun `a list that isn't switched on plays nothing`() {
        val beach = fileOnDisk("beach.mp4")
        storeActive(VideoPlaylist(clips = listOf(clip("beach").copy(path = beach.path, originalPath = beach.path))))
        prefs().edit().putBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, false).commit()

        assertNull(readActiveVideoPlaylist(prefs()))
    }

    @Test
    fun `a clip whose prepared copy went missing plays its saved source`() {
        val source = fileOnDisk("beach.mp4")
        storeActive(
            VideoPlaylist(clips = listOf(clip("beach").copy(path = "/gone/beach.fit.mp4", originalPath = source.path))),
        )

        assertEquals(source.path, readActiveVideoPlaylist(prefs())?.clips?.single()?.path)
    }

    @Test
    fun `a clip with nothing left on disk is left out and the rest still play`() {
        val rain = fileOnDisk("rain.mp4")
        storeActive(
            VideoPlaylist(
                clips = listOf(
                    clip("beach").copy(path = "/gone/beach.fit.mp4", originalPath = "/gone/beach.mp4"),
                    clip("rain").copy(path = rain.path, originalPath = rain.path),
                ),
            ),
        )

        assertEquals(listOf("rain"), readActiveVideoPlaylist(prefs())?.clips?.map { it.name })
    }

    @Test
    fun `a list with nothing playable left reads as no list`() {
        storeActive(VideoPlaylist(clips = listOf(clip("beach"))))

        assertNull(readActiveVideoPlaylist(prefs()))
    }

    @Test
    fun `a list repeats as a whole unless a timer moves it or there's one clip`() {
        val two = listOf(clip("beach"), clip("rain"))

        assertEquals(Player.REPEAT_MODE_ALL, videoPlaylistRepeatMode(VideoPlaylist(clips = two)))
        assertEquals(Player.REPEAT_MODE_ONE, videoPlaylistRepeatMode(VideoPlaylist(clips = two, advanceSeconds = 30)))
        assertEquals(Player.REPEAT_MODE_ONE, videoPlaylistRepeatMode(VideoPlaylist(clips = two.take(1))))
    }

    @Test
    fun `the next clip wraps at the end of the list`() {
        assertEquals(1, nextVideoPlaylistIndex(0, 3))
        assertEquals(0, nextVideoPlaylistIndex(2, 3))
        assertEquals(0, nextVideoPlaylistIndex(0, 0))
    }

    @Test
    fun `reordering a list changes its key`() {
        val one = VideoPlaylist(clips = listOf(clip("beach"), clip("rain")))
        val other = one.copy(clips = one.clips.reversed())

        assertNotEquals(videoPlaylistKey(one), videoPlaylistKey(other))
        assertEquals(videoPlaylistKey(one), videoPlaylistKey(one.copy()))
    }

    @Test
    fun `picking a single video switches the list off and keeps it`() {
        val video = fileOnDisk("single.mp4")
        storeActive(VideoPlaylist(clips = listOf(clip("beach"))))

        persistVideoWallpaperSelection(context, video)

        assertFalse(prefs().getBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, true))
        assertTrue(prefs().getString(VIDEO_WALLPAPER_PLAYLIST_PREF, null).orEmpty().contains("beach"))
    }
}
