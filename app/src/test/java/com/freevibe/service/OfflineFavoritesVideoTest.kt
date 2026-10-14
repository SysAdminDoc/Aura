package com.freevibe.service

import android.content.Context
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.FavoriteEntity
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineFavoritesVideoTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val client = mockk<OkHttpClient>(relaxed = true)
    private val dao = mockk<FavoriteDao>(relaxed = true)

    private val video = FavoriteEntity(
        id = "yt_dQw4w9WgXcQ",
        source = "YOUTUBE",
        type = "VIDEO",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        fullUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        name = "Neon rain loop",
    )

    @Test
    fun `a saved video is never fetched or filed as an offline picture`() = runTest {
        val manager = OfflineFavoritesManager(context, client, dao)

        assertNull(manager.cacheOffline(video, video.fullUrl))
        assertNull(manager.cacheOffline(video.copy(type = "video"), video.thumbnailUrl))

        verify(exactly = 0) { client.newCall(any()) }
        coVerify(exactly = 0) { dao.updateOfflinePath(any(), any(), any(), any()) }
        assertTrue(File(context.filesDir, "offline_favorites").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a picture with the same address still goes to the network`() = runTest {
        // The control for the test above: only the row's type keeps a video out.
        val manager = OfflineFavoritesManager(context, client, dao)

        manager.cacheOffline(video.copy(type = "WALLPAPER"), video.thumbnailUrl)

        verify(exactly = 1) { client.newCall(any()) }
    }
}
