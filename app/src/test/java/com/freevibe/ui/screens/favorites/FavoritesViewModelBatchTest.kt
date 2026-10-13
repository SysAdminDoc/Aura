package com.freevibe.ui.screens.favorites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.service.BatchDownloadService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FavoritesViewModelBatchTest {

    private val dispatcher = StandardTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(service: BatchDownloadService) = FavoritesViewModel(
        context = context,
        favoritesRepo = mockk<FavoritesRepository>(relaxed = true) {
            every { getWallpapers() } returns flowOf(emptyList())
            every { getSounds() } returns flowOf(emptyList())
        },
        exporter = mockk(relaxed = true),
        selectedContent = mockk(relaxed = true),
        batchDownloadService = service,
        aiWallpaperRepository = mockk(relaxed = true),
        localMediaRelinkManager = mockk(relaxed = true),
    )

    @Test
    fun `a batch that can't be saved or queued shows a message instead of crashing`() = runTest(dispatcher) {
        val service = mockk<BatchDownloadService>(relaxed = true) {
            coEvery { downloadBatch(any()) } throws IOException("disk full")
        }
        val vm = viewModel(service)

        vm.startBatch(listOf(wallpaper()))
        advanceUntilIdle()

        assertEquals(context.getString(R.string.favorites_batch_start_failed), vm.message.value)
    }

    @Test
    fun `cancel stops a queued batch`() = runTest(dispatcher) {
        val service = mockk<BatchDownloadService>(relaxed = true)
        val vm = viewModel(service)

        vm.cancelBatch()
        advanceUntilIdle()

        coVerify(exactly = 1) { service.cancel() }
    }

    private fun wallpaper() = Wallpaper(
        id = "w1",
        source = ContentSource.REDDIT,
        thumbnailUrl = "https://example.com/thumb.jpg",
        fullUrl = "https://example.com/full.jpg",
        width = 1080,
        height = 1920,
        category = "Nature",
        tags = listOf("Forest"),
    )
}
