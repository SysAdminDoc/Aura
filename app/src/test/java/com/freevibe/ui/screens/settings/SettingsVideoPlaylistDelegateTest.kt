package com.freevibe.ui.screens.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import com.freevibe.R
import com.freevibe.service.VIDEO_WALLPAPER_SCALE_MODE_ZOOM
import com.freevibe.service.VideoPlaylist
import com.freevibe.service.VideoPlaylistClip
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.util.UiTextException
import com.freevibe.util.uiText
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsVideoPlaylistDelegateTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val storage = mockk<VideoWallpaperStorage>()
    private val clip = VideoPlaylistClip("/clips/a.mp4", "/clips/a.mp4", "a", VIDEO_WALLPAPER_SCALE_MODE_ZOOM)

    private fun TestScope.delegate(messages: MutableList<String>): SettingsVideoPlaylistDelegate {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val delegate = SettingsVideoPlaylistDelegate(context, storage, scope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            delegate.messages.collect { messages += it }
        }
        return delegate
    }

    @Test
    fun `opening the dialog shows what's saved`() = runTest {
        val saved = VideoPlaylist(clips = listOf(clip), advanceSeconds = 60)
        coEvery { storage.readPlaylist() } returns saved
        coEvery { storage.isPlaylistActive() } returns true
        val delegate = delegate(mutableListOf())

        delegate.refresh()

        assertEquals(saved, delegate.state.value.playlist)
        assertTrue(delegate.state.value.active)
    }

    @Test
    fun `one refused clip doesn't stop the others from being added`() = runTest {
        val refused = Uri.parse("content://nothing/moving.gif")
        val accepted = Uri.parse("content://nothing/beach.mp4")
        val text = uiText(R.string.settings_video_playlist_no_gifs)
        coEvery { storage.addPlaylistClip(refused) } returns Result.failure(UiTextException(text))
        coEvery { storage.addPlaylistClip(accepted) } returns Result.success(VideoPlaylist(clips = listOf(clip)))
        val messages = mutableListOf<String>()
        val delegate = delegate(messages)

        delegate.addClips(listOf(refused, accepted))

        assertEquals(listOf(context.getString(R.string.settings_video_playlist_no_gifs)), messages)
        assertEquals(listOf(clip), delegate.state.value.playlist.clips)
        assertFalse("the spinner must stop when the work is done", delegate.state.value.working)
    }

    @Test
    fun `a failure with nothing to say still tells the person something`() = runTest {
        coEvery { storage.setPlaylistShuffle(true) } returns Result.failure(IOException(""))
        val messages = mutableListOf<String>()

        delegate(messages).setShuffle(true)

        assertEquals(listOf(context.getString(R.string.settings_feedback_prepare_video_failed)), messages)
    }

    @Test
    fun `removing the last clip reads as switched off`() = runTest {
        coEvery { storage.readPlaylist() } returns VideoPlaylist(clips = listOf(clip))
        coEvery { storage.isPlaylistActive() } returns true
        coEvery { storage.removePlaylistClip(0) } returns Result.success(VideoPlaylist())
        val delegate = delegate(mutableListOf())
        delegate.refresh()

        delegate.remove(0)

        assertFalse(delegate.state.value.active)
        coVerify { storage.removePlaylistClip(0) }
    }

    @Test
    fun `the wallpaper picker only opens once a list is switched on`() = runTest {
        var opened = 0
        coEvery { storage.activatePlaylist() } returns Result.success(false)
        val delegate = delegate(mutableListOf())

        delegate.activate { opened += 1 }
        assertEquals(0, opened)

        coEvery { storage.activatePlaylist() } returns Result.success(true)
        delegate.activate { opened += 1 }
        assertEquals(1, opened)
        assertTrue(delegate.state.value.active)
    }
}
