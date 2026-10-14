package com.freevibe.ui.screens.favorites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.service.BatchDownloadService
import com.freevibe.service.BatchDownloadStartResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

    private fun favorites(
        pictures: List<FavoriteEntity> = emptyList(),
        videos: List<FavoriteEntity> = emptyList(),
    ) = mockk<FavoritesRepository>(relaxed = true) {
        every { getWallpapers() } returns flowOf(pictures)
        every { getSounds() } returns flowOf(emptyList())
        every { getVideos() } returns flowOf(videos)
        every { getAll() } returns flowOf(pictures + videos)
    }

    private fun viewModel(
        service: BatchDownloadService,
        favoritesRepo: FavoritesRepository = favorites(),
    ) = FavoritesViewModel(
        context = context,
        favoritesRepo = favoritesRepo,
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

    @Test
    fun `download all sends the pictures and never a saved video`() = runTest(dispatcher) {
        val service = mockk<BatchDownloadService>(relaxed = true) {
            coEvery { downloadBatch(any()) } returns BatchDownloadStartResult(acceptedCount = 1)
        }
        val vm = viewModel(service, favorites(pictures = listOf(savedPicture), videos = listOf(savedVideo)))
        val pictures = launch { vm.wallpapers.collect {} }
        val videos = launch { vm.videos.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(savedVideo), vm.videos.value)

        vm.downloadAllWallpapers()
        advanceUntilIdle()

        coVerify(exactly = 1) { service.downloadBatch(match { batch -> batch.map { it.id } == listOf("wall-1") }) }
        coVerify(exactly = 0) { service.downloadBatch(match { batch -> batch.any { it.id == savedVideo.id } }) }
        pictures.cancel()
        videos.cancel()
    }

    @Test
    fun `with only videos saved, download all starts nothing`() = runTest(dispatcher) {
        val service = mockk<BatchDownloadService>(relaxed = true)
        val vm = viewModel(service, favorites(videos = listOf(savedVideo)))
        val pictures = launch { vm.wallpapers.collect {} }
        val videos = launch { vm.videos.collect {} }
        advanceUntilIdle()

        vm.downloadAllWallpapers()
        advanceUntilIdle()

        coVerify(exactly = 0) { service.downloadBatch(any()) }
        pictures.cancel()
        videos.cancel()
    }

    @Test
    fun `the bulk actions leave a saved video alone`() = runTest(dispatcher) {
        val service = mockk<BatchDownloadService>(relaxed = true)
        val repo = favorites(pictures = listOf(savedPicture), videos = listOf(savedVideo))
        val vm = viewModel(service, repo)
        val pictures = launch { vm.wallpapers.collect {} }
        val videos = launch { vm.videos.collect {} }
        advanceUntilIdle()

        vm.bulkDownload(setOf(savedVideo.stableKey()))
        vm.bulkDelete(setOf(savedVideo.stableKey()))
        advanceUntilIdle()

        coVerify(exactly = 0) { service.downloadBatch(any()) }
        coVerify(exactly = 0) { repo.remove(any()) }
        assertEquals(context.getString(R.string.favorites_select_wallpaper), vm.message.value)
        pictures.cancel()
        videos.cancel()
    }

    private val savedPicture = FavoriteEntity(
        id = "wall-1",
        source = "REDDIT",
        type = "WALLPAPER",
        thumbnailUrl = "https://i.redd.it/wall-1-thumb.jpg",
        fullUrl = "https://i.redd.it/wall-1.jpg",
        name = "Ridge",
    )

    private val savedVideo = FavoriteEntity(
        id = "yt_dQw4w9WgXcQ",
        source = "YOUTUBE",
        type = "VIDEO",
        thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        fullUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        name = "Neon rain loop",
    )

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
