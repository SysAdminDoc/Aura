package com.freevibe.service

import android.app.Application
import android.content.Context
import android.net.Uri
import com.freevibe.R
import com.freevibe.util.displayMessage
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class VideoWallpaperStoragePlaylistTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var storage: VideoWallpaperStorage

    @Before
    fun setUp() {
        storage = VideoWallpaperStorage(context, mockk(), mockk())
    }

    private fun prefs() = context.getSharedPreferences(VIDEO_WALLPAPER_PREFS_NAME, Context.MODE_PRIVATE)

    private fun clip(name: String) = File(context.filesDir, "$name.mp4").let { file ->
        file.writeBytes(ByteArray(8))
        VideoPlaylistClip(file.path, file.path, name, VIDEO_WALLPAPER_SCALE_MODE_ZOOM)
    }

    private fun store(vararg names: String, active: Boolean = false) {
        prefs().edit()
            .putString(VIDEO_WALLPAPER_PLAYLIST_PREF, encodeVideoPlaylist(VideoPlaylist(clips = names.map(::clip))))
            .putBoolean(VIDEO_WALLPAPER_PLAYLIST_ACTIVE_PREF, active)
            .commit()
    }

    private suspend fun names() = storage.readPlaylist().clips.map { it.name }

    private fun Result<*>.message() = exceptionOrNull()?.displayMessage(context)

    @Test
    fun `a clip moves to where it's asked and an edit is saved`() = runTest {
        store("a", "b", "c")

        assertEquals(listOf("b", "a", "c"), storage.movePlaylistClip(1, 0).getOrThrow().clips.map { it.name })

        assertEquals(listOf("b", "a", "c"), names())
    }

    @Test
    fun `a move or removal past the ends of the list changes nothing`() = runTest {
        store("a", "b")

        storage.movePlaylistClip(0, 5).getOrThrow()
        storage.movePlaylistClip(-1, 0).getOrThrow()
        storage.removePlaylistClip(9).getOrThrow()

        assertEquals(listOf("a", "b"), names())
    }

    @Test
    fun `taking a clip off the list deletes nothing`() = runTest {
        store("a", "b")
        val kept = File(context.filesDir, "a.mp4")

        storage.removePlaylistClip(0).getOrThrow()

        assertEquals(listOf("b"), names())
        assertTrue(kept.isFile)
    }

    @Test
    fun `removing the last clip switches the list off`() = runTest {
        store("a", active = true)
        assertTrue(storage.isPlaylistActive())

        storage.removePlaylistClip(0).getOrThrow()

        assertFalse(storage.isPlaylistActive())
    }

    @Test
    fun `shuffle and the timer are saved and an unknown interval means no timer`() = runTest {
        store("a", "b")

        storage.setPlaylistShuffle(true).getOrThrow()
        storage.setPlaylistAdvance(60).getOrThrow()
        assertTrue(storage.readPlaylist().shuffle)
        assertEquals(60, storage.readPlaylist().advanceSeconds)

        storage.setPlaylistAdvance(45).getOrThrow()
        assertEquals(0, storage.readPlaylist().advanceSeconds)
    }

    @Test
    fun `an empty list can't be switched on`() = runTest {
        assertFalse(storage.activatePlaylist().getOrThrow())
        assertFalse(storage.isPlaylistActive())

        store("a")
        assertTrue(storage.activatePlaylist().getOrThrow())
        assertTrue(storage.isPlaylistActive())
    }

    @Test
    fun `a full list refuses another clip and stays as it was`() = runTest {
        val full = Array(MAX_VIDEO_PLAYLIST_CLIPS) { "clip$it" }
        store(*full)

        val added = storage.addPlaylistClip(Uri.parse("content://nothing/another.mp4"))

        assertEquals(context.getString(R.string.settings_video_playlist_full, MAX_VIDEO_PLAYLIST_CLIPS), added.message())
        assertEquals(full.toList(), names())
    }

    @Test
    fun `a list at the storage limit refuses another clip`() = runTest {
        store("a")
        val big = File(context.filesDir, "a.mp4")
        try {
            RandomAccessFile(big, "rw").use { it.setLength(MAX_VIDEO_PLAYLIST_BYTES + 1) }

            val added = storage.addPlaylistClip(Uri.parse("content://nothing/another.mp4"))

            assertEquals(context.getString(R.string.settings_video_playlist_too_big), added.message())
            assertEquals(listOf("a"), names())
        } finally {
            big.delete()
        }
    }

    @Test
    fun `a clip whose source is gone doesn't count against the limit`() = runTest {
        store(*Array(MAX_VIDEO_PLAYLIST_CLIPS) { "clip$it" })
        File(context.filesDir, "clip0.mp4").delete()
        val uri = Uri.parse("content://nothing/moving.gif")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("GIF89a".toByteArray() + ByteArray(64)))

        val added = storage.addPlaylistClip(uri)

        assertEquals(
            "with a free slot it gets as far as reading the file",
            context.getString(R.string.settings_video_playlist_no_gifs),
            added.message(),
        )
    }

    @Test
    fun `a GIF can't join a list`() = runTest {
        val uri = Uri.parse("content://nothing/moving.gif")
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream("GIF89a".toByteArray() + ByteArray(64)))

        val added = storage.addPlaylistClip(uri)

        assertEquals(context.getString(R.string.settings_video_playlist_no_gifs), added.message())
        assertEquals(emptyList<String>(), names())
    }
}
