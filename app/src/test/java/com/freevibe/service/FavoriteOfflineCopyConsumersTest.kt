package com.freevibe.service

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Split crop and parallax both started life expecting a web address, and a favorite hands them a path. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class FavoriteOfflineCopyConsumersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val okHttp = mockk<OkHttpClient>(relaxed = true)
    private lateinit var copy: File
    private lateinit var plainPath: String

    @Before
    fun setUp() {
        copy = File(context.filesDir, "offline_favorites/WALLPAPER__REDDIT__rd_1.png")
        copy.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(8, 16, Bitmap.Config.ARGB_8888)
        copy.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        plainPath = copy.absolutePath.replace('\\', '/').substringAfter(':')
    }

    @Test
    fun `split crop applies home and lock from the offline copy`() = runBlocking {
        val applier = mockk<WallpaperApplier>()
        coEvery { applier.applyFromBitmap(any(), any(), any()) } returns Result.success(Unit)
        val service = DualWallpaperService(context, applier, okHttp)
        val favorite = Wallpaper(
            id = "rd_1",
            source = ContentSource.REDDIT,
            thumbnailUrl = "https://example.com/t.jpg",
            fullUrl = plainPath,
            width = 8,
            height = 16,
            remoteFullUrl = "https://example.com/full.png",
        )

        val applied = service.applySplitCrop(favorite)

        assertTrue(applied.exceptionOrNull()?.toString(), applied.isSuccess)
        coVerify(exactly = 1) { applier.applyFromBitmap(any(), WallpaperTarget.HOME, any()) }
        coVerify(exactly = 1) { applier.applyFromBitmap(any(), WallpaperTarget.LOCK, any()) }
        verify(exactly = 0) { okHttp.newCall(any()) }
    }

    @Test
    fun `parallax saves the offline copy unchanged`() = runBlocking {
        val applier = WallpaperApplier(context, okHttp, mockk())

        val prepared = applier.prepareParallaxWallpaper(plainPath, "parallax_wp.png")

        assertTrue(prepared.exceptionOrNull()?.toString(), prepared.isSuccess)
        val saved = File(prepared.getOrThrow())
        assertArrayEquals(copy.readBytes(), saved.readBytes())
        assertEquals(
            saved.absolutePath,
            context.getSharedPreferences("freevibe_parallax", Context.MODE_PRIVATE).getString("image_path", null),
        )
        verify(exactly = 0) { okHttp.newCall(any()) }
    }
}
