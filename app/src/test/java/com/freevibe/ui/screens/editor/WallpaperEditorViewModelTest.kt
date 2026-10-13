package com.freevibe.ui.screens.editor

import android.content.Context
import android.graphics.Bitmap
import app.cash.turbine.test
import com.freevibe.R
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.service.DepthPortraitComposer
import com.freevibe.service.DepthPortraitResult
import com.freevibe.service.WallpaperApplier
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var wallpaperApplier: WallpaperApplier
    private lateinit var depthPortraitComposer: DepthPortraitComposer
    private lateinit var viewModel: WallpaperEditorViewModel
    private lateinit var context: Context

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        wallpaperApplier = mockk(relaxed = true)
        depthPortraitComposer = mockk(relaxed = true)
        context = mockk<Context>(relaxed = true).also {
            every { it.getString(R.string.editor_wallpaper_depth_ready) } returns "Depth portrait ready"
        }
        viewModel = WallpaperEditorViewModel(
            context = context,
            wallpaperApplier = wallpaperApplier,
            depthPortraitComposer = depthPortraitComposer,
            okHttpClient = mockk(relaxed = true),
            applyCoordinator = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has default filter values`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertEquals(0f, state.brightness)
            assertEquals(1f, state.contrast)
            assertEquals(1f, state.saturation)
            assertEquals(0f, state.blurRadius)
            assertEquals(0f, state.vignette)
            assertEquals(0f, state.grain)
            assertEquals(0f, state.amoledCrush)
            assertEquals(0f, state.warmth)
            assertEquals(1f, state.depthSubjectScale)
            assertNull(state.originalBitmap)
            assertNull(state.editedBitmap)
            assertFalse(state.isProcessing)
            assertFalse(state.isApplying)
            assertFalse(state.isDepthProcessing)
            assertFalse(state.isExporting)
            assertFalse(state.isPreparingParallax)
            assertFalse(state.pendingParallaxLaunch)
            assertTrue(state.overlayLayers.isEmpty())
            assertNull(state.selectedOverlayId)
            assertFalse(state.canUndoOverlay)
            assertNull(state.qualityWarning)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resetAll restores default filter values`() = runTest {
        viewModel.state.test {
            awaitItem() // initial

            // Change a value first so resetAll produces a new emission
            viewModel.updateBrightness(50f)
            val modified = awaitItem()
            assertEquals(50f, modified.brightness)

            viewModel.resetAll()
            val state = awaitItem()
            assertEquals(0f, state.brightness)
            assertEquals(1f, state.contrast)
            assertEquals(1f, state.saturation)
            assertEquals(0f, state.blurRadius)
            assertEquals(1f, state.depthSubjectScale)
            assertTrue(state.overlayLayers.isEmpty())
            assertNull(state.selectedOverlayId)
            assertFalse(state.canUndoOverlay)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `text overlay can move delete and undo`() = runTest {
        viewModel.addTextOverlay("Focus")

        val added = viewModel.state.value
        assertEquals(1, added.overlayLayers.size)
        assertEquals("Focus", added.overlayLayers.single().text)
        assertEquals(added.overlayLayers.single().id, added.selectedOverlayId)
        assertTrue(added.canUndoOverlay)

        val id = added.overlayLayers.single().id
        viewModel.moveOverlay(id, 0.2f, -0.1f)
        val moved = viewModel.state.value.overlayLayers.single()
        assertEquals(0.7f, moved.x, 0.001f)
        assertEquals(0.4f, moved.y, 0.001f)

        viewModel.undoOverlayEdit()
        val restored = viewModel.state.value.overlayLayers.single()
        assertEquals(0.5f, restored.x, 0.001f)
        assertEquals(0.5f, restored.y, 0.001f)

        viewModel.deleteSelectedOverlay()
        assertTrue(viewModel.state.value.overlayLayers.isEmpty())

        viewModel.undoOverlayEdit()
        assertEquals(1, viewModel.state.value.overlayLayers.size)
    }

    @Test
    fun `step resize and delete by id leave the other layer selected`() = runTest {
        viewModel.addTextOverlay("Keep")
        val keep = viewModel.state.value.overlayLayers.single().id
        viewModel.addStickerOverlay(WallpaperSticker.STAR)
        val star = viewModel.state.value.overlayLayers.last().id

        viewModel.scaleOverlay(star, 0.25f)
        assertEquals(1.25f, viewModel.state.value.overlayLayers.single { it.id == star }.scale, 0.001f)
        viewModel.scaleOverlay(star, 5f)
        assertEquals("a step can't pass the slider's maximum", 2.25f, viewModel.state.value.overlayLayers.single { it.id == star }.scale, 0.001f)

        viewModel.selectOverlay(keep)
        viewModel.deleteOverlay(star)
        assertEquals(listOf(keep), viewModel.state.value.overlayLayers.map { it.id })
        assertEquals(keep, viewModel.state.value.selectedOverlayId)

        viewModel.undoOverlayEdit()
        assertEquals(listOf(keep, star), viewModel.state.value.overlayLayers.map { it.id })
    }

    @Test
    fun `sticker overlay stores style scale rotation and color`() = runTest {
        viewModel.addStickerOverlay(WallpaperSticker.HEART)
        viewModel.updateSelectedSticker(WallpaperSticker.SPARKLE)
        viewModel.updateSelectedOverlayScale(1.8f)
        viewModel.updateSelectedOverlayRotation(45f)
        viewModel.updateSelectedOverlayColor(0xFF4FC3F7.toInt())

        val sticker = viewModel.state.value.overlayLayers.single()
        assertEquals(WallpaperOverlayType.STICKER, sticker.type)
        assertEquals(WallpaperSticker.SPARKLE, sticker.sticker)
        assertEquals(1.8f, sticker.scale, 0.001f)
        assertEquals(45f, sticker.rotationDegrees, 0.001f)
        assertEquals(0xFF4FC3F7.toInt(), sticker.color)
    }

    @Test
    fun `clearError clears error state`() = runTest {
        viewModel.clearError()
        viewModel.state.test {
            assertNull(awaitItem().error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearSuccess clears success state`() = runTest {
        viewModel.clearSuccess()
        viewModel.state.test {
            assertNull(awaitItem().success)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `composeDepthPortrait stores composed bitmap and success feedback`() = runTest {
        val source = mockk<Bitmap>(relaxed = true)
        val composed = mockk<Bitmap>(relaxed = true)
        coEvery { depthPortraitComposer.compose(source, any()) } returns
            DepthPortraitResult(composed, segmentationApplied = true)

        viewModel.setSourceBitmap(source)
        viewModel.composeDepthPortrait()

        val state = viewModel.state.value
        assertSame(composed, state.editedBitmap)
        assertEquals("Depth portrait ready", state.success)
        assertFalse(state.isDepthProcessing)
    }

    @Test
    fun `prepareDepthParallax raises pending launch after bitmap write`() = runTest {
        val source = mockk<Bitmap>(relaxed = true)
        coEvery { wallpaperApplier.prepareParallaxFromBitmap(source, any()) } returns Result.success("/tmp/parallax.jpg")

        viewModel.setSourceBitmap(source)
        viewModel.prepareDepthParallax()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.pendingParallaxLaunch)
        assertFalse(state.isPreparingParallax)
    }

    @Test
    fun `downscale warning describes reduced render dimensions`() {
        val warning = wallpaperEditorDownscalePercent(
            sourceWidth = 4000,
            sourceHeight = 3000,
            renderedWidth = 2000,
            renderedHeight = 1500,
        )

        assertEquals(50, warning)
    }

    @Test
    fun `downscale warning is absent for full size renders`() {
        assertNull(
            wallpaperEditorDownscalePercent(
                sourceWidth = 4000,
                sourceHeight = 3000,
                renderedWidth = 4000,
                renderedHeight = 3000,
            ),
        )
    }
}
