package com.freevibe.ui.screens.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.freevibe.service.DepthPortraitComposer
import com.freevibe.service.WallpaperApplier
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The editor's source can arrive long after the screen opens. These cover the
 * three ways that used to go wrong: Apply firing with no bitmap, filter changes
 * made during the download never rendering, and an older URL's result landing on
 * top of a newer source.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperEditorSourceLoadingTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: WallpaperEditorViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = WallpaperEditorViewModel(
            context = mockk<Context>(relaxed = true),
            wallpaperApplier = mockk<WallpaperApplier>(relaxed = true),
            depthPortraitComposer = mockk<DepthPortraitComposer>(relaxed = true),
            okHttpClient = mockk(relaxed = true),
            applyCoordinator = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        // A render runs on the real Default dispatcher, and a cancelled one still hops back to Main when it
        // ends. Wait here so it can't land on the next test's dispatcher.
        runBlocking { viewModel.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
        Dispatchers.resetMain()
    }

    private fun bitmap(width: Int = 8, height: Int = 8): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    @Test
    fun `output controls stay disabled until a source is decoded`() = runTest {
        assertFalse(viewModel.state.value.isSourceReady)

        viewModel.setSourceBitmap(bitmap())

        assertTrue(viewModel.state.value.isSourceReady)
    }

    /**
     * The filter render hops to `Dispatchers.Default`, a real dispatcher, so this
     * one waits in real time rather than on the test scheduler's virtual clock.
     */
    @Test
    fun `filter changes made before the source arrives replay once it does`() = runBlocking {
        // The user drags brightness while the URL is still downloading.
        viewModel.updateBrightness(40f)
        assertFalse(viewModel.state.value.isSourceReady)
        assertTrue(viewModel.state.value.editedBitmap == null)

        val source = bitmap()
        viewModel.setSourceBitmap(source)

        val edited = withTimeout(10_000) {
            viewModel.state.first { it.editedBitmap != null && it.editedBitmap !== source }
        }.editedBitmap

        assertNotNull("a pending filter must render once the source lands", edited)
        Unit
    }

    @Test
    fun `an unchanged filter set leaves the source bitmap in place`() = runTest {
        val source = bitmap()

        viewModel.setSourceBitmap(source)

        assertSame(source, viewModel.state.value.editedBitmap)
    }

    @Test
    fun `replacing the source clears the previous edit state`() = runTest {
        viewModel.updateBrightness(40f)
        viewModel.setSourceBitmap(bitmap())
        val first = viewModel.state.value.originalBitmap

        val second = bitmap(16, 16)
        viewModel.setSourceBitmap(second)

        assertSame(second, viewModel.state.value.originalBitmap)
        assertTrue(first !== second)
        assertTrue(viewModel.state.value.overlayLayers.isEmpty())
        assertFalse(viewModel.state.value.canUndoOverlay)
    }

    @Test
    fun `url loads carry an ownership token so a stale result cannot win`() {
        val source = File("src/main/java/com/freevibe/ui/screens/editor/WallpaperEditorViewModel.kt").readText()

        assertTrue("a load must be cancellable", source.contains("loadJob?.cancel()"))
        assertTrue("each load must claim ownership", source.contains("val token = ++loadToken"))
        assertTrue(
            "a stale success must not overwrite the newer source",
            source.contains("if (token != loadToken) {"),
        )
        assertTrue(
            "a stale bitmap must be released rather than leaked",
            source.contains("bitmap.recycle()"),
        )
        assertTrue(
            "a stale failure must not surface an error for a source the user replaced",
            source.contains("if (token != loadToken) return@launch"),
        )
        assertTrue("the load job must be cancelled with the ViewModel", source.contains("loadJob?.cancel()"))
    }

    @Test
    fun `apply controls in the screen require a ready source`() {
        val screen = File("src/main/java/com/freevibe/ui/screens/editor/WallpaperEditorScreen.kt").readText()

        assertTrue(
            "apply must be gated on a decoded source",
            screen.contains("val canApply = state.isSourceReady && !state.isApplying"),
        )
        assertFalse(
            "apply must not be enabled purely on the applying flag",
            screen.contains("enabled = !state.isApplying,"),
        )
    }
}
