package com.freevibe.ui.screens.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

/** Edit and Crop on a favorite read its offline copy, and go back to the web once that copy is gone. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [35])
class WallpaperLocalSourceLoadTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val okHttp = mockk<OkHttpClient>(relaxed = true)
    private val viewModels = mutableListOf<ViewModel>()
    private lateinit var copy: File

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        copy = File(context.filesDir, "offline_favorites/WALLPAPER__REDDIT__rd_1.png")
        copy.parentFile?.mkdirs()
        val bitmap = Bitmap.createBitmap(4, 8, Bitmap.Config.ARGB_8888)
        copy.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @After
    fun tearDown() {
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        Dispatchers.resetMain()
    }

    private fun favorite() = Wallpaper(
        id = "rd_1",
        source = ContentSource.REDDIT,
        thumbnailUrl = "https://example.com/t.jpg",
        fullUrl = copy.absolutePath.replace('\\', '/').substringAfter(':'),
        width = 4,
        height = 8,
        remoteFullUrl = "https://example.com/full.png",
    )

    private fun editor() = WallpaperEditorViewModel(
        context = context,
        wallpaperApplier = mockk(relaxed = true),
        depthPortraitComposer = mockk(relaxed = true),
        okHttpClient = okHttp,
        applyCoordinator = mockk(relaxed = true),
    ).also { viewModels += it }

    private fun crop() = WallpaperCropViewModel(
        wallpaperApplier = mockk(relaxed = true),
        okHttpClient = okHttp,
        smartCropDetector = mockk(relaxed = true),
        applyCoordinator = mockk(relaxed = true),
        appContext = context,
    ).also { viewModels += it }

    /** The web call fails on purpose. These tests only ask where the screen went looking. */
    private fun captureWebRequest(): io.mockk.CapturingSlot<Request> {
        val request = slot<Request>()
        val call = mockk<Call>()
        every { call.execute() } throws IOException("offline")
        every { okHttp.newCall(capture(request)) } returns call
        return request
    }

    @Test
    fun `Edit opens a favorite from its offline copy with no web request`() = runBlocking {
        val viewModel = editor()

        viewModel.loadWallpaper(favorite())

        val state = withTimeout(10_000) { viewModel.state.first { it.originalBitmap != null || it.error != null } }
        assertNull(state.error)
        assertNotNull(state.originalBitmap)
        verify(exactly = 0) { okHttp.newCall(any()) }
    }

    @Test
    fun `Edit goes to the web once the offline copy is gone`() = runBlocking {
        val request = captureWebRequest()
        val wallpaper = favorite()
        copy.delete()
        val viewModel = editor()

        viewModel.loadWallpaper(wallpaper)

        withTimeout(10_000) { viewModel.state.first { it.originalBitmap != null || it.error != null } }
        assertEquals("https://example.com/full.png", request.captured.url.toString())
    }

    @Test
    fun `Crop opens a favorite from its offline copy and leaves the copy alone`() = runBlocking {
        val viewModel = crop()

        viewModel.loadWallpaper(favorite())

        val state = withTimeout(10_000) { viewModel.state.first { it.bitmap != null || it.error != null } }
        assertNull(state.error)
        assertNotNull(state.bitmap)
        assertEquals(true, copy.exists())
        verify(exactly = 0) { okHttp.newCall(any()) }
    }

    @Test
    fun `Crop goes to the web once the offline copy is gone`() = runBlocking {
        val request = captureWebRequest()
        val wallpaper = favorite()
        copy.delete()
        val viewModel = crop()

        viewModel.loadWallpaper(wallpaper)

        withTimeout(10_000) { viewModel.state.first { it.bitmap != null || it.error != null } }
        assertEquals("https://example.com/full.png", request.captured.url.toString())
    }
}
