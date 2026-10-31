package com.freevibe.ui.screens.editor

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.freevibe.R
import com.freevibe.service.DepthPortraitComposer
import com.freevibe.service.DepthPortraitResult
import com.freevibe.service.WallpaperApplier
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Who owns which bitmap in the editor, and what the user is told when a
 * composition is thrown away.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WallpaperEditorBitmapOwnershipTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var depthPortraitComposer: DepthPortraitComposer
    private lateinit var wallpaperApplier: WallpaperApplier
    private lateinit var viewModel: WallpaperEditorViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        wallpaperApplier = mockk(relaxed = true)
        depthPortraitComposer = mockk(relaxed = true)
        viewModel = WallpaperEditorViewModel(
            context = mockk<Context>(relaxed = true).also {
                every { it.getString(R.string.editor_wallpaper_depth_replaced_notice) } returns
                    "Filters replaced your depth portrait. Compose it again to bring it back."
            },
            wallpaperApplier = wallpaperApplier,
            depthPortraitComposer = depthPortraitComposer,
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

    private fun liveBitmap(): Bitmap = mockk(relaxed = true) {
        every { isRecycled } returns false
    }

    @Test
    fun `a successful compose marks the edit as a depth portrait`() = runTest {
        val source = liveBitmap()
        coEvery { depthPortraitComposer.compose(source, any()) } returns
            DepthPortraitResult(liveBitmap(), segmentationApplied = true)

        viewModel.setSourceBitmap(source)
        viewModel.composeDepthPortrait()

        assertTrue(viewModel.state.value.depthPortraitComposed)
        assertNull(viewModel.state.value.notice)
    }

    /**
     * Segmentation was unavailable, so the original was kept — there is no
     * composition to lose, and a later filter must not claim there was one.
     */
    @Test
    fun `a compose that fell back is not treated as a composition`() = runTest {
        val source = liveBitmap()
        coEvery { depthPortraitComposer.compose(source, any()) } returns
            DepthPortraitResult(liveBitmap(), segmentationApplied = false)

        viewModel.setSourceBitmap(source)
        viewModel.composeDepthPortrait()

        assertFalse(viewModel.state.value.depthPortraitComposed)
    }

    @Test
    fun `moving a filter back to default replaces the portrait and says so`() = runTest {
        val source = liveBitmap()
        val composed = liveBitmap()
        coEvery { depthPortraitComposer.compose(source, any()) } returns
            DepthPortraitResult(composed, segmentationApplied = true)

        viewModel.setSourceBitmap(source)
        viewModel.composeDepthPortrait()
        assertSame(composed, viewModel.state.value.editedBitmap)

        // Filters render from the original, so the composition cannot survive.
        viewModel.updateBrightness(0f)

        val state = viewModel.state.value
        assertSame(source, state.editedBitmap)
        assertFalse(state.depthPortraitComposed)
        assertEquals(
            "Filters replaced your depth portrait. Compose it again to bring it back.",
            state.notice,
        )
    }

    @Test
    fun `the replacement notice is only raised once, not on every later filter move`() = runTest {
        val source = liveBitmap()
        coEvery { depthPortraitComposer.compose(source, any()) } returns
            DepthPortraitResult(liveBitmap(), segmentationApplied = true)

        viewModel.setSourceBitmap(source)
        viewModel.composeDepthPortrait()
        viewModel.updateBrightness(0f)
        viewModel.clearNotice()
        viewModel.updateContrast(1f)

        assertNull(viewModel.state.value.notice)
    }

    @Test
    fun `a new source clears the portrait state and any standing notice`() = runTest {
        val source = liveBitmap()
        coEvery { depthPortraitComposer.compose(source, any()) } returns
            DepthPortraitResult(liveBitmap(), segmentationApplied = true)

        viewModel.setSourceBitmap(source)
        viewModel.composeDepthPortrait()
        viewModel.updateBrightness(0f)

        viewModel.setSourceBitmap(liveBitmap())

        val state = viewModel.state.value
        assertFalse(state.depthPortraitComposed)
        assertNull(state.notice)
    }

    @Test
    fun `resetting the editor drops the portrait state`() = runTest {
        val source = liveBitmap()
        coEvery { depthPortraitComposer.compose(source, any()) } returns
            DepthPortraitResult(liveBitmap(), segmentationApplied = true)

        viewModel.setSourceBitmap(source)
        viewModel.composeDepthPortrait()
        viewModel.resetAll()

        assertFalse(viewModel.state.value.depthPortraitComposed)
        assertNull(viewModel.state.value.notice)
    }
}

/**
 * The rendered-output bitmap is freed after an apply, export, or parallax write.
 * Getting this wrong in either direction is bad: freeing something the editor is
 * still showing crashes the next draw, and freeing nothing leaks a full-size
 * bitmap per write.
 */
class RecycleRenderedBitmapTest {

    private fun liveBitmap(): Bitmap = mockk(relaxed = true) {
        every { isRecycled } returns false
    }

    @Test
    fun `a bitmap allocated purely for output is freed`() {
        val rendered = liveBitmap()
        val state = EditorState(originalBitmap = liveBitmap(), editedBitmap = liveBitmap())

        recycleRenderedBitmap(rendered, state, state)

        verify(exactly = 1) { rendered.recycle() }
    }

    @Test
    fun `the edited bitmap itself is never freed`() {
        val edited = liveBitmap()
        val state = EditorState(originalBitmap = liveBitmap(), editedBitmap = edited)

        recycleRenderedBitmap(edited, state, state)

        verify(exactly = 0) { edited.recycle() }
    }

    @Test
    fun `the original bitmap is never freed`() {
        val original = liveBitmap()
        val state = EditorState(originalBitmap = original, editedBitmap = original)

        recycleRenderedBitmap(original, state, state)

        verify(exactly = 0) { original.recycle() }
    }

    /**
     * The stale-snapshot case. A render read the state as it was; by the time the
     * write finished a filter job had put that same bitmap back on screen. Keying
     * only on the snapshot would recycle it out from under the composition.
     */
    @Test
    fun `a bitmap the editor has since adopted is spared`() {
        val rendered = liveBitmap()
        val renderedFrom = EditorState(originalBitmap = liveBitmap(), editedBitmap = liveBitmap())
        val live = renderedFrom.copy(editedBitmap = rendered)

        recycleRenderedBitmap(rendered, renderedFrom, live)

        verify(exactly = 0) { rendered.recycle() }
    }

    @Test
    fun `an already recycled bitmap is not recycled twice`() {
        val dead = mockk<Bitmap>(relaxed = true) { every { isRecycled } returns true }
        val state = EditorState()

        recycleRenderedBitmap(dead, state, state)

        verify(exactly = 0) { dead.recycle() }
    }
}
