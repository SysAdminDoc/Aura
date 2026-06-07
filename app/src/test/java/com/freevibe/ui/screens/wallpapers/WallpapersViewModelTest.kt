package com.freevibe.ui.screens.wallpapers

import android.content.Context
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteIdentity
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperCollectionEntity
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.RedditRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.WallpaperRepository
import com.freevibe.data.repository.WallpaperUploadRepository
import com.freevibe.service.DownloadManager
import com.freevibe.service.DualWallpaperService
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.SeasonalContentManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.testSelectedContentHolder
import com.freevibe.service.WallpaperApplier
import com.freevibe.service.WallpaperHistoryManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WallpapersViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `clearActiveFilter returns to previous wallpaper tab`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { wallpaperRepo.getPexelsCurated(1) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.searchAll("mountain", 1) } returns SearchResult(
            items = listOf(wallpaper("wh_search", color = "#112233")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        viewModel.selectTab(WallpaperTab.PEXELS)
        advanceUntilIdle()
        viewModel.search("mountain")
        advanceUntilIdle()

        assertEquals(WallpaperTab.SEARCH, viewModel.state.value.selectedTab)
        assertEquals(WallpaperTab.PEXELS, viewModel.state.value.browseTab)

        viewModel.clearActiveFilter()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(WallpaperTab.PEXELS, state.selectedTab)
        assertEquals(WallpaperTab.PEXELS, state.browseTab)
        assertEquals("", state.query)
        assertNull(state.selectedColor)
    }

    @Test
    fun `findSimilarById resolves wallpaper from shared holder`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val selectedContent = testSelectedContentHolder()
        val source = wallpaper(id = "wh_123", color = "#112233")
        selectedContent.selectWallpaper(source, listOf(source))

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { wallpaperRepo.findSimilar("123", 1) } returns SearchResult(
            items = listOf(wallpaper(id = "wh_like", color = "#112233")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )
        coEvery { wallpaperRepo.searchByColor("112233", 1) } returns SearchResult(
            items = listOf(wallpaper(id = "wh_color", color = "#112233")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            selectedContent = selectedContent,
        )

        viewModel.findSimilarById("wh_123")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(WallpaperTab.SEARCH, state.selectedTab)
        assertEquals("Similar", state.query)
        assertEquals(setOf("wh_like", "wh_color"), state.wallpapers.map { it.id }.toSet())
    }

    @Test
    fun `findSimilarById uses route identity when raw ids collide across providers`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val selectedContent = testSelectedContentHolder()
        val pexelsWallpaper = wallpaper(
            id = "shared_42",
            color = "#112233",
            source = ContentSource.PEXELS,
            fullUrl = "https://example.com/pexels-shared_42.jpg",
        )
        val pixabayWallpaper = wallpaper(
            id = "shared_42",
            color = "#445566",
            source = ContentSource.PIXABAY,
            fullUrl = "https://example.com/pixabay-shared_42.jpg",
        )
        selectedContent.selectWallpaper(pexelsWallpaper, listOf(pexelsWallpaper, pixabayWallpaper))

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { wallpaperRepo.searchByColor("445566", 1) } returns SearchResult(
            items = listOf(
                wallpaper(
                    id = "pixabay_like",
                    color = "#445566",
                    source = ContentSource.PIXABAY,
                    fullUrl = "https://example.com/pixabay-like.jpg",
                )
            ),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            selectedContent = selectedContent,
        )

        viewModel.findSimilarById(
            wallpaperId = "shared_42",
            source = ContentSource.PIXABAY,
            fullUrl = "https://example.com/pixabay-shared_42.jpg",
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { wallpaperRepo.searchByColor("445566", 1) }
        coVerify(exactly = 0) { wallpaperRepo.searchByColor("112233", 1) }
        assertEquals(listOf("pixabay_like"), viewModel.state.value.wallpapers.map { it.id })
    }

    @Test
    fun `refresh preserves discover feed but updates pagination when refreshed results are empty`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val seededWallpaper = wallpaper(id = "wh_seed", color = "#101820")

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { wallpaperRepo.getDiscover(any(), any(), any()) } returnsMany listOf(
            SearchResult(
                items = listOf(seededWallpaper),
                totalCount = 1,
                currentPage = 1,
                hasMore = true,
            ),
            emptyWallpaperResult(),
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        viewModel.handleRouteFilters(query = null, color = null, similarId = null)
        advanceUntilIdle()
        assertEquals(listOf("wh_seed"), viewModel.state.value.wallpapers.map { it.id })

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("wh_seed"), state.wallpapers.map { it.id })
        assertEquals(false, state.hasMore)
        assertNull(state.error)
    }

    @Test
    fun `reddit disabled skips daily pick and redirects reddit tab to discover`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )
        coEvery { redditRepo.getDailyTopWallpaper() } returns wallpaper(
            id = "rd_daily",
            color = "#101820",
            source = ContentSource.REDDIT,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            redditProviderEnabled = false,
        )

        advanceUntilIdle()
        assertNull(viewModel.dailyPick.value)
        coVerify(exactly = 0) { redditRepo.getDailyTopWallpaper() }

        viewModel.selectTab(WallpaperTab.REDDIT)
        advanceUntilIdle()

        assertEquals(WallpaperTab.DISCOVER, viewModel.state.value.selectedTab)
        coVerify(exactly = 0) { redditRepo.getMultiSubreddit() }
    }

    @Test
    fun `disabled pexels and pixabay tabs redirect to discover`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            pexelsProviderEnabled = false,
            pixabayProviderEnabled = false,
        )

        advanceUntilIdle()

        viewModel.selectTab(WallpaperTab.PEXELS)
        advanceUntilIdle()
        assertEquals(WallpaperTab.DISCOVER, viewModel.state.value.selectedTab)
        coVerify(exactly = 0) { wallpaperRepo.getPexelsCurated(any()) }

        viewModel.selectTab(WallpaperTab.PIXABAY)
        advanceUntilIdle()
        assertEquals(WallpaperTab.DISCOVER, viewModel.state.value.selectedTab)
        coVerify(exactly = 0) { wallpaperRepo.getPixabay(any(), any()) }
    }

    @Test
    fun `wallhaven disabled redirects tab and blocks wallhaven only actions`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            wallhavenProviderEnabled = false,
        )

        advanceUntilIdle()

        viewModel.selectTab(WallpaperTab.WALLHAVEN)
        advanceUntilIdle()
        assertEquals(WallpaperTab.DISCOVER, viewModel.state.value.selectedTab)
        coVerify(exactly = 0) { wallpaperRepo.getWallhaven(any(), any(), any()) }

        viewModel.searchByColor("112233")
        advanceUntilIdle()
        assertEquals("Wallhaven source is disabled in Settings", viewModel.state.value.error)
        coVerify(exactly = 0) { wallpaperRepo.searchByColor(any(), any()) }

        viewModel.loadRandom()
        advanceUntilIdle()
        assertEquals("Wallhaven source is disabled in Settings", viewModel.state.value.error)
        coVerify(exactly = 0) { wallpaperRepo.getRandomWallhaven() }
    }

    @Test
    fun `community disabled skips top votes and redirects community tab to discover`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val voteRepo = mockk<VoteRepository>().also {
            every { it.hiddenIds } returns flowOf(emptySet())
            every { it.sanitizeKey(any()) } answers {
                firstArg<String>().replace(Regex("[.#$\\[\\]/]"), "_")
            }
            coEvery { it.getTopVotedIds(any()) } returns listOf("WALLPAPER::COMMUNITY::cw_one" to 4)
        }

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            voteRepoOverride = voteRepo,
            communityProviderEnabled = false,
        )

        advanceUntilIdle()
        assertTrue(viewModel.topVoted.value.isEmpty())
        coVerify(exactly = 0) { voteRepo.getTopVotedIds(any()) }

        viewModel.selectTab(WallpaperTab.COMMUNITY)
        advanceUntilIdle()

        assertEquals(WallpaperTab.DISCOVER, viewModel.state.value.selectedTab)
    }

    @Test
    fun `reportWallpaper submits report metadata`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val reportRepo = mockk<CommunityReportRepository>()
        stubCommonDependencies(wallpaperRepo, redditRepo)
        coEvery { reportRepo.submitReport(any()) } returns Result.success("report-1")

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            reportRepoOverride = reportRepo,
        )
        val wallpaper = wallpaper(id = "cw_1", source = ContentSource.COMMUNITY, color = "#223344")
            .copy(communityUploaderId = "creator-1")

        viewModel.reportWallpaper(wallpaper, CommunityReportReason.SOURCE_REMOVED, "gone upstream")
        advanceUntilIdle()

        assertEquals("Report submitted", viewModel.state.value.applySuccess)
        coVerify {
            reportRepo.submitReport(
                match {
                        it.contentId == wallpaper.stableKey() &&
                        it.contentType == "WALLPAPER" &&
                        it.reason == CommunityReportReason.SOURCE_REMOVED &&
                        it.uploaderUid == "creator-1"
                },
            )
        }
    }

    @Test
    fun `reportWallpaper submits generated content report when community source is disabled`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val reportRepo = mockk<CommunityReportRepository>()
        stubCommonDependencies(wallpaperRepo, redditRepo)
        coEvery { reportRepo.submitReport(any()) } returns Result.success("report-1")

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            reportRepoOverride = reportRepo,
            communityProviderEnabled = false,
        )
        val wallpaper = wallpaper(id = "generated-1", source = ContentSource.AI_GENERATED, color = "#223344")
            .copy(fullUrl = "file:///cache/generated-1.png")

        viewModel.reportWallpaper(wallpaper, CommunityReportReason.OFFENSIVE, "offensive output")
        advanceUntilIdle()

        assertEquals("Report submitted", viewModel.state.value.applySuccess)
        coVerify {
            reportRepo.submitReport(
                match {
                    it.contentId == wallpaper.stableKey() &&
                        it.contentType == "WALLPAPER" &&
                        it.contentSource == ContentSource.AI_GENERATED &&
                        it.reason == CommunityReportReason.OFFENSIVE &&
                        it.sourceUrl == "" &&
                        it.license == "Generated wallpaper" &&
                        it.uploaderName == "Aura generated wallpaper" &&
                        it.uploaderUid == ""
                },
            )
        }
    }

    @Test
    fun `blockCommunityWallpaper writes block and removes matching uploader wallpapers`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val uploadRepo = mockk<WallpaperUploadRepository>(relaxed = true)
        val blockRepo = mockk<CommunityBlockRepository>()
        val blocked = wallpaper("cw_blocked", color = "#112233", source = ContentSource.COMMUNITY)
            .copy(communityUploaderId = "creator/1")
        val sameCreator = wallpaper("cw_same", color = "#223344", source = ContentSource.COMMUNITY)
            .copy(communityUploaderId = "creator.1")
        val keep = wallpaper("cw_keep", color = "#334455", source = ContentSource.COMMUNITY)
            .copy(communityUploaderId = "creator-2")

        stubCommonDependencies(wallpaperRepo, redditRepo)
        coEvery { uploadRepo.getCommunityWallpapers() } returns SearchResult(
            items = listOf(blocked, sameCreator, keep),
            totalCount = 3,
            currentPage = 1,
            hasMore = false,
        )
        coEvery { blockRepo.blockUser("creator/1", CommunityBlockReason.OTHER) } returns Result.success(Unit)

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            wallpaperUploadRepoOverride = uploadRepo,
            communityBlockRepoOverride = blockRepo,
        )

        viewModel.selectTab(WallpaperTab.COMMUNITY)
        advanceUntilIdle()
        assertEquals(listOf("cw_blocked", "cw_same", "cw_keep"), viewModel.state.value.wallpapers.map { it.id })

        assertTrue(viewModel.canBlockCommunityWallpaper(blocked))
        viewModel.blockCommunityWallpaper(blocked)
        advanceUntilIdle()

        assertEquals(listOf("cw_keep"), viewModel.state.value.wallpapers.map { it.id })
        assertEquals("Creator blocked", viewModel.state.value.applySuccess)
        coVerify(exactly = 1) { blockRepo.blockUser("creator/1", CommunityBlockReason.OTHER) }
    }

    @Test
    fun `canDeleteCommunityWallpaper delegates owner availability to upload repository`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val uploadRepo = mockk<WallpaperUploadRepository>(relaxed = true)

        stubCommonDependencies(wallpaperRepo, redditRepo)
        coEvery { uploadRepo.canDeleteWallpaperUpload("cw_owner_wallpaper") } returns true

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            wallpaperUploadRepoOverride = uploadRepo,
        )

        assertTrue(
            viewModel.canDeleteCommunityWallpaper(
                wallpaper("cw_owner_wallpaper", color = "#112233", source = ContentSource.COMMUNITY),
            ),
        )
        coVerify(exactly = 1) { uploadRepo.canDeleteWallpaperUpload("cw_owner_wallpaper") }
        assertTrue(
            !viewModel.canDeleteCommunityWallpaper(
                wallpaper("wh_owner_wallpaper", color = "#112233", source = ContentSource.WALLHAVEN),
            ),
        )
    }

    @Test
    fun `deleteCommunityWallpaper deletes through upload repository and reports success`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val uploadRepo = mockk<WallpaperUploadRepository>(relaxed = true)
        val ownerWallpaper = wallpaper("cw_owner_wallpaper", color = "#112233", source = ContentSource.COMMUNITY)

        stubCommonDependencies(wallpaperRepo, redditRepo)
        coEvery { uploadRepo.deleteWallpaperUpload(ownerWallpaper.id) } returns Result.success(Unit)

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            wallpaperUploadRepoOverride = uploadRepo,
        )

        viewModel.deleteCommunityWallpaper(ownerWallpaper)
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadRepo.deleteWallpaperUpload(ownerWallpaper.id) }
        assertEquals("Upload deleted", viewModel.state.value.applySuccess)
    }

    @Test
    fun `downloadWallpaper scopes download identity by source`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val downloadManager = mockk<DownloadManager>()
        val wallpaper = wallpaper(
            id = "shared_42",
            color = "#101820",
            source = ContentSource.PEXELS,
            fullUrl = "https://example.com/pexels-shared_42.png",
        )

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery {
            downloadManager.downloadWallpaper(
                id = wallpaper.stableKey(),
                url = wallpaper.fullUrl,
                fileName = match { it == "Aura_pexels_shared_42.png" },
                source = ContentSource.PEXELS.name,
            )
        } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            downloadManagerOverride = downloadManager,
        )

        viewModel.downloadWallpaper(wallpaper)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            downloadManager.downloadWallpaper(
                id = wallpaper.stableKey(),
                url = wallpaper.fullUrl,
                fileName = "Aura_pexels_shared_42.png",
                source = ContentSource.PEXELS.name,
            )
        }
    }

    @Test
    fun `downloadWallpaper marks saved favorite unavailable when provider returns removed status`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val downloadManager = mockk<DownloadManager>()
        val favoritesRepo = mockk<FavoritesRepository>()
        val wallpaper = wallpaper(
            id = "removed_42",
            color = "#101820",
            source = ContentSource.PEXELS,
            fullUrl = "https://images.pexels.com/photos/removed.jpg",
        )

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )
        coEvery { favoritesRepo.markSourceUnavailable(any(), any()) } returns Unit
        coEvery {
            downloadManager.downloadWallpaper(any(), any(), any(), any())
        } returns Result.failure(IllegalStateException("Download failed: HTTP 404"))

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            downloadManagerOverride = downloadManager,
            favoritesRepoOverride = favoritesRepo,
        )

        viewModel.downloadWallpaper(wallpaper)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            favoritesRepo.markSourceUnavailable(
                wallpaper.favoriteIdentity(),
                "Pexels media is unavailable or removed",
            )
        }
        assertEquals("Download failed: HTTP 404", viewModel.state.value.error)
    }

    @Test
    fun `fetchTopVoted ignores ambiguous legacy vote ids when stable vote ids exist`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val voteRepo = mockk<VoteRepository>()
        val cacheManager = mockk<WallpaperCacheManager>()
        val pexelsWallpaper = wallpaper(
            id = "shared_42",
            color = "#112233",
            source = ContentSource.PEXELS,
            fullUrl = "https://example.com/pexels-shared_42.jpg",
        )
        val pixabayWallpaper = wallpaper(
            id = "shared_42",
            color = "#445566",
            source = ContentSource.PIXABAY,
            fullUrl = "https://example.com/pixabay-shared_42.jpg",
        )

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        every { voteRepo.hiddenIds } returns flowOf(emptySet())
        every { voteRepo.sanitizeKey(any()) } answers {
            firstArg<String>().replace(Regex("[.#$\\[\\]/]"), "_")
        }
        coEvery { voteRepo.getTopVotedIds(any()) } returns listOf(
            pexelsWallpaper.stableKey() to 7,
            "shared_42" to 20,
        )
        coEvery { cacheManager.getByIds(any()) } returns listOf(pexelsWallpaper, pixabayWallpaper)

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            voteRepoOverride = voteRepo,
            cacheManagerOverride = cacheManager,
        )

        advanceUntilIdle()

        val topVoted = viewModel.topVoted.value
        assertEquals(1, topVoted.size)
        assertEquals(ContentSource.PEXELS, topVoted.first().first.source)
        assertEquals(7, topVoted.first().second)
    }

    @Test
    fun `resolveWallpaper uses type-scoped favorite lookup when raw ids collide across content types`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val favoritesRepo = mockk<FavoritesRepository>()

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )

        coEvery { favoritesRepo.getByIdentity(any()) } returns null
        coEvery { favoritesRepo.getLatestById(any()) } returns com.freevibe.data.model.FavoriteEntity(
            id = "shared_raw",
            source = ContentSource.YOUTUBE.name,
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "https://example.com/audio.mp3",
            name = "Audio collision",
        )
        coEvery { favoritesRepo.getLatestByIdAndType("shared_raw", "WALLPAPER") } returns com.freevibe.data.model.FavoriteEntity(
            id = "shared_raw",
            source = ContentSource.PIXABAY.name,
            type = "WALLPAPER",
            thumbnailUrl = "https://example.com/shared-thumb.jpg",
            fullUrl = "https://example.com/shared.jpg",
            width = 1080,
            height = 2400,
            colors = "#123456",
        )

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            favoritesRepoOverride = favoritesRepo,
        )

        advanceUntilIdle()

        val resolved = viewModel.resolveWallpaper("shared_raw")

        assertEquals(ContentSource.PIXABAY, resolved?.source)
        assertEquals("https://example.com/shared.jpg", resolved?.fullUrl)
        coVerify(exactly = 1) { favoritesRepo.getLatestByIdAndType("shared_raw", "WALLPAPER") }
    }

    @Test
    fun `resolveWallpaper uses cache identity when raw ids collide across providers`() = runTest(dispatcher) {
        val wallpaperRepo = mockk<WallpaperRepository>()
        val redditRepo = mockk<RedditRepository>()
        val cacheManager = mockk<WallpaperCacheManager>()
        val pexelsWallpaper = wallpaper(
            id = "shared_raw",
            color = "#112233",
            source = ContentSource.PEXELS,
            fullUrl = "https://example.com/pexels-shared.jpg",
        )
        val pixabayWallpaper = wallpaper(
            id = "shared_raw",
            color = "#445566",
            source = ContentSource.PIXABAY,
            fullUrl = "https://example.com/pixabay-shared.jpg",
        )

        stubCommonDependencies(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
        )
        coEvery { cacheManager.getByIds(listOf("shared_raw")) } returns listOf(pexelsWallpaper, pixabayWallpaper)

        val viewModel = createViewModel(
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            cacheManagerOverride = cacheManager,
        )

        advanceUntilIdle()

        val resolved = viewModel.resolveWallpaper(
            id = "shared_raw",
            source = ContentSource.PIXABAY,
            fullUrl = "https://example.com/pixabay-shared.jpg",
        )

        assertEquals(ContentSource.PIXABAY, resolved?.source)
        assertEquals("https://example.com/pixabay-shared.jpg", resolved?.fullUrl)
    }

    private fun createViewModel(
        wallpaperRepo: WallpaperRepository,
        redditRepo: RedditRepository,
        selectedContent: SelectedContentHolder = testSelectedContentHolder(),
        downloadManagerOverride: DownloadManager? = null,
        voteRepoOverride: VoteRepository? = null,
        cacheManagerOverride: WallpaperCacheManager? = null,
        favoritesRepoOverride: FavoritesRepository? = null,
        reportRepoOverride: CommunityReportRepository? = null,
        communityBlockRepoOverride: CommunityBlockRepository? = null,
        wallpaperUploadRepoOverride: WallpaperUploadRepository? = null,
        wallhavenProviderEnabled: Boolean = true,
        redditProviderEnabled: Boolean = true,
        pexelsProviderEnabled: Boolean = true,
        pixabayProviderEnabled: Boolean = true,
        communityProviderEnabled: Boolean = true,
        generatedContentProviderEnabled: Boolean = true,
    ): WallpapersViewModel {
        val favoritesRepo = favoritesRepoOverride ?: mockk<FavoritesRepository>()
        every { favoritesRepo.allIdentities() } returns flowOf(emptySet<FavoriteIdentity>())
        every { favoritesRepo.isFavorite(any()) } returns flowOf(false)
        if (favoritesRepoOverride == null) {
            coEvery { favoritesRepo.getByIdentity(any()) } returns null
            coEvery { favoritesRepo.getLatestById(any()) } returns null
            coEvery { favoritesRepo.getLatestByIdAndType(any(), any()) } returns null
        }

        val searchHistoryRepo = mockk<SearchHistoryRepository>()
        every { searchHistoryRepo.getRecentWallpaperSearches(any()) } returns flowOf(emptyList<SearchHistoryEntity>())
        coEvery { searchHistoryRepo.addWallpaperSearch(any()) } returns Unit
        coEvery { searchHistoryRepo.removeSearch(any(), any()) } returns Unit
        coEvery { searchHistoryRepo.clearWallpaperHistory() } returns Unit

        val prefs = mockk<PreferencesManager>()
        every { prefs.wallpaperGridColumns } returns flowOf(2)
        every { prefs.wallhavenProviderEnabled } returns flowOf(wallhavenProviderEnabled)
        every { prefs.redditProviderEnabled } returns flowOf(redditProviderEnabled)
        every { prefs.pexelsProviderEnabled } returns flowOf(pexelsProviderEnabled)
        every { prefs.pixabayProviderEnabled } returns flowOf(pixabayProviderEnabled)
        every { prefs.communityProviderEnabled } returns flowOf(communityProviderEnabled)
        every { prefs.generatedContentProviderEnabled } returns flowOf(generatedContentProviderEnabled)
        every { prefs.preferredResolution } returns flowOf("1080x1920")
        every { prefs.userStyles } returns flowOf("minimal,nature")

        val collectionRepo = mockk<CollectionRepository>()
        every { collectionRepo.getAll() } returns flowOf(emptyList<WallpaperCollectionEntity>())

        val downloadManager = downloadManagerOverride ?: mockk<DownloadManager>()
        every { downloadManager.activeDownloads } returns MutableStateFlow(emptyMap())

        val voteRepo = voteRepoOverride ?: mockk<VoteRepository>().also {
            every { it.hiddenIds } returns flowOf(emptySet())
            every { it.sanitizeKey(any()) } answers {
                firstArg<String>().replace(Regex("[.#$\\[\\]/]"), "_")
            }
            coEvery { it.getTopVotedIds(any()) } returns emptyList()
        }

        val cacheManager = cacheManagerOverride ?: mockk<WallpaperCacheManager>().also {
            coEvery { it.getByIds(any()) } returns emptyList()
        }

        return WallpapersViewModel(
            context = mockk<Context>(relaxed = true),
            wallpaperRepo = wallpaperRepo,
            redditRepo = redditRepo,
            favoritesRepo = favoritesRepo,
            wallpaperApplier = mockk<WallpaperApplier>(relaxed = true),
            downloadManager = downloadManager,
            dualWallpaperService = mockk<DualWallpaperService>(relaxed = true),
            collectionRepo = collectionRepo,
            selectedContent = selectedContent,
            historyManager = mockk<WallpaperHistoryManager>(relaxed = true),
            offlineFavorites = mockk<OfflineFavoritesManager>(relaxed = true),
            searchHistoryRepo = searchHistoryRepo,
            prefs = prefs,
            colorExtractor = mockk(relaxed = true),
            cacheManager = cacheManager,
            applyFeedbackBus = mockk(relaxed = true),
            voteRepo = voteRepo,
            reportRepo = reportRepoOverride ?: mockk<CommunityReportRepository>(relaxed = true),
            communityBlockRepo = communityBlockRepoOverride ?: mockk<CommunityBlockRepository>(relaxed = true),
            seasonalContentManager = SeasonalContentManager(),
            wallpaperUploadRepo = wallpaperUploadRepoOverride ?: mockk(relaxed = true),
            sourceMetrics = com.freevibe.service.SourceMetrics(),
        )
    }

    private fun stubCommonDependencies(
        wallpaperRepo: WallpaperRepository,
        redditRepo: RedditRepository,
    ) {
        coEvery { wallpaperRepo.searchAll(any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.searchByColor(any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getPexelsCurated(any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getPixabay(any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getWallhaven(any(), any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getDiscover(any(), any(), any()) } returns emptyWallpaperResult()
        coEvery { wallpaperRepo.getCachedDiscover(any()) } returns emptyList()
        coEvery { wallpaperRepo.findSimilar(any(), any()) } returns emptyWallpaperResult()
        coEvery { redditRepo.getDailyTopWallpaper() } returns null
        every { redditRepo.resetPagination() } just runs
        coEvery { redditRepo.getMultiSubreddit() } returns emptyWallpaperResult()
    }

    private fun emptyWallpaperResult() = SearchResult(
        items = emptyList<Wallpaper>(),
        totalCount = 0,
        currentPage = 1,
        hasMore = false,
    )

    private fun wallpaper(
        id: String,
        color: String,
        source: ContentSource = ContentSource.WALLHAVEN,
        fullUrl: String = "https://example.com/$id.jpg",
    ) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        fullUrl = fullUrl,
        width = 1440,
        height = 3200,
        tags = listOf("minimal"),
        colors = listOf(color),
    )
}
