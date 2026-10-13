package com.freevibe.ui.screens.sounds

import com.freevibe.BuildConfig
import android.content.Context
import com.freevibe.R
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.legal.isProviderAvailableInCurrentArtifact
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.CommunityUploadRights
import com.freevibe.data.model.ContentType
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Sound
import com.freevibe.data.model.SearchHistoryEntity
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.CommunityReportRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.repository.SearchHistoryRepository
import com.freevibe.data.repository.TikTokSoundRepository
import com.freevibe.data.repository.UploadRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.data.repository.YouTubeRepository
import com.freevibe.data.repository.YouTubeExtractionStatus
import com.freevibe.service.AudioPlaybackManager
import com.freevibe.service.AudioPreviewCache
import com.freevibe.service.BundledContentProvider
import com.freevibe.service.DownloadManager
import com.freevibe.service.SeasonalContentManager
import com.freevibe.service.SelectedContentHolder
import com.freevibe.service.SoundApplier
import com.freevibe.service.SoundFeedCache
import com.freevibe.service.SoundUrlResolver
import com.freevibe.service.testSelectedContentHolder
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
class SoundsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial sound feed uses youtube only and does not advertise pagination`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("yt_focus", ContentSource.YOUTUBE, "Aura Focus Tone")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf("yt_focus"), state.sounds.map { it.id }.toSet())
        assertFalse(state.hasMore)
    }

    @Test
    fun `offline originals appear immediately and remain beside youtube results`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()
        val bundled = testSound("bundled_ring", ContentSource.BUNDLED, "Crystal Chime")

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("yt_focus", ContentSource.YOUTUBE, "Aura Focus Tone")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            bundledRingtones = listOf(bundled),
        )

        advanceUntilIdle()

        assertEquals(setOf("bundled_ring", "yt_focus"), viewModel.state.value.sounds.map { it.id }.toSet())
        assertEquals(ContentSource.YOUTUBE, viewModel.state.value.sounds.first().source)
    }

    @Test
    fun `youtube disabled uses bundled ringtone fallback and skips provider calls`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val bundled = listOf(testSound("bundled_ring", ContentSource.BUNDLED, "Bundled Ring"))

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            youtubeProviderEnabled = false,
            bundledRingtones = bundled,
        )

        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("bundled_ring"), state.sounds.map { it.id })
        assertFalse(state.hasMore)
        coVerify(exactly = 0) { youtubeRepo.searchSounds(any(), any(), any(), any()) }
        coVerify(exactly = 0) { youtubeRepo.getAudioPreviewUrl(any()) }
    }

    @Test
    fun `tiktok creator clips lead the ringtones tab and stay off other tabs`() = runTest(dispatcher) {
        assumeTrue(isProviderAvailableInCurrentArtifact(ContentSource.TIKTOK))
        val youtubeRepo = mockk<YouTubeRepository>()
        stubCommonDependencies(youtubeRepo)
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("yt_abc12345678", ContentSource.YOUTUBE, "Crystal Chime")),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )
        // "new" is on the YouTube spam blocklist; a creator clip must survive it.
        val tiktok = listOf(
            tiktokSound("7688705749270252814", "Nokia Banger"),
            tiktokSound("7689085301091781901", "Sprinkles Remix | new iphone ringtone"),
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            bundledRingtones = listOf(testSound("bundled_ring", ContentSource.BUNDLED, "Bundled Ring")),
            tiktokRingtones = tiktok,
        )
        advanceUntilIdle()

        val ringtoneIds = viewModel.state.value.sounds.map { it.id }
        assertEquals(listOf("tt_7688705749270252814", "tt_7689085301091781901"), ringtoneIds.take(2))
        assertTrue(ringtoneIds.contains("bundled_ring"))

        viewModel.selectTab(SoundTab.NOTIFICATIONS)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sounds.none { it.source == ContentSource.TIKTOK })
    }

    @Test
    fun `tiktok clips stay in ringtones when youtube is disabled`() = runTest(dispatcher) {
        assumeTrue(isProviderAvailableInCurrentArtifact(ContentSource.TIKTOK))
        val youtubeRepo = mockk<YouTubeRepository>()
        stubCommonDependencies(youtubeRepo)

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            youtubeProviderEnabled = false,
            bundledRingtones = listOf(testSound("bundled_ring", ContentSource.BUNDLED, "Bundled Ring")),
            tiktokRingtones = listOf(tiktokSound("7688705749270252814", "Nokia Banger")),
        )
        advanceUntilIdle()

        assertEquals(listOf("tt_7688705749270252814", "bundled_ring"), viewModel.state.value.sounds.map { it.id })
        coVerify(exactly = 0) { youtubeRepo.searchSounds(any(), any(), any(), any()) }
    }

    @Test
    fun `an expired tiktok preview link is refreshed before playback`() = runTest(dispatcher) {
        assumeTrue(isProviderAvailableInCurrentArtifact(ContentSource.TIKTOK))
        val youtubeRepo = mockk<YouTubeRepository>()
        stubCommonDependencies(youtubeRepo)
        val refreshed = "https://v16m.tiktokcdn-us.com/ffffffffffffffffffffffffffffffff/7fffffff/video/tos/new/"
        val tiktokRepo = mockk<TikTokSoundRepository>()
        coEvery { tiktokRepo.ringtones(any()) } returns emptyList()
        coEvery { tiktokRepo.playableMediaUrl(any(), any()) } returns refreshed
        val playbackManager = mockk<AudioPlaybackManager>(relaxed = true).also {
            every { it.currentSoundId } returns MutableStateFlow(null)
            every { it.currentPosition } returns MutableStateFlow(0L)
            every { it.duration } returns MutableStateFlow(0L)
            every { it.isPlaying } returns MutableStateFlow(false)
        }
        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            audioPlaybackManagerOverride = playbackManager,
            tiktokRepoOverride = tiktokRepo,
        )
        advanceUntilIdle()
        val expired = tiktokSound("7688705749270252814", "Nokia Banger", expiresAtSec = 1_000_000_000L)

        viewModel.togglePlayback(expired)
        advanceUntilIdle()

        coVerify(exactly = 1) { tiktokRepo.playableMediaUrl(match { it.id == expired.id }, any()) }
        verify { playbackManager.play(match { it.id == expired.id }, refreshed, any()) }
    }

    @Test
    fun `youtube disabled blocks youtube tab search and import`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            youtubeProviderEnabled = false,
        )

        advanceUntilIdle()
        clearMocks(youtubeRepo, answers = false, recordedCalls = true)

        viewModel.searchYouTube("focus")
        viewModel.importYouTubeUrl("https://www.youtube.com/watch?v=abc12345678")
        advanceUntilIdle()

        assertEquals("YouTube features are disabled in Settings", viewModel.state.value.error)
        coVerify(exactly = 0) { youtubeRepo.searchSounds(any(), any(), any(), any()) }
        coVerify(exactly = 0) { youtubeRepo.getAudioPreviewUrl(any()) }
    }

    @Test
    fun `youtube disabled blocks youtube download resolution`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val downloadManager = mockk<DownloadManager>(relaxed = true)

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            youtubeProviderEnabled = false,
            downloadManagerOverride = downloadManager,
        )

        advanceUntilIdle()
        val youtubeSound = testSound("yt_abc12345678", ContentSource.YOUTUBE, "Blocked Download")

        viewModel.downloadSound(youtubeSound, confirmed = true)
        advanceUntilIdle()

        val expectedError = if (isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)) {
            "Could not resolve audio stream URL"
        } else {
            "This provider is unavailable in this Aura build."
        }
        assertEquals(expectedError, viewModel.state.value.error)
        coVerify(exactly = 0) { youtubeRepo.getAudioStreamUrl(any()) }
        coVerify(exactly = 0) { downloadManager.downloadSound(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `downloadSound requires confirmation before resolving youtube stream`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val downloadManager = mockk<DownloadManager>(relaxed = true)

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            downloadManagerOverride = downloadManager,
        )

        advanceUntilIdle()
        val youtubeSound = testSound("yt_terms12345", ContentSource.YOUTUBE, "Terms Download").copy(
            sourcePageUrl = "https://www.youtube.com/watch?v=terms12345",
        )

        viewModel.downloadSound(youtubeSound)
        advanceUntilIdle()

        val expectedError = if (isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE)) {
            "Confirm YouTube source terms before downloading this sound."
        } else {
            "This provider is unavailable in this Aura build."
        }
        assertEquals(expectedError, viewModel.state.value.error)
        coVerify(exactly = 0) { youtubeRepo.getAudioStreamUrl(any()) }
        coVerify(exactly = 0) { downloadManager.downloadSound(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `community disabled redirects community tab and blocks community actions`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            communityProviderEnabled = false,
        )

        advanceUntilIdle()

        viewModel.selectTab(SoundTab.COMMUNITY)
        advanceUntilIdle()
        assertEquals(SoundTab.RINGTONES, viewModel.state.value.selectedTab)

        viewModel.startCommunityRecording()
        viewModel.uploadSound(mockk(), "Tone", "ringtone", rights = CommunityUploadRights("CC0", true))
        viewModel.upvote("SOUND::COMMUNITY::cu_one")
        advanceUntilIdle()

        assertEquals("Community source is disabled in Settings", viewModel.state.value.error)
    }

    @Test
    fun `reportSound submits report metadata`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val reportRepo = mockk<CommunityReportRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { reportRepo.submitReport(any()) } returns Result.success("report-1")

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            reportRepoOverride = reportRepo,
        )
        advanceUntilIdle()
        val sound = testSound("cu_1", ContentSource.COMMUNITY, "Community Tone")
            .copy(communityUploaderId = "creator-1")

        viewModel.reportSound(sound, CommunityReportReason.RIGHTS, "wrong license")
        advanceUntilIdle()

        assertEquals("Report submitted", viewModel.state.value.applySuccess)
        coVerify {
            reportRepo.submitReport(
                match {
                    it.contentId == sound.stableKey() &&
                        it.contentType == "SOUND" &&
                        it.reason == CommunityReportReason.RIGHTS &&
                        it.license == "CC0" &&
                        it.uploaderUid == "creator-1"
                },
            )
        }
    }

    @Test
    fun `blockCommunitySound writes block and removes matching uploader sounds`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val uploadRepo = mockk<UploadRepository>(relaxed = true)
        val blockRepo = mockk<CommunityBlockRepository>()
        val blocked = testSound("cu_blocked", ContentSource.COMMUNITY, "Blocked").copy(communityUploaderId = "creator/1")
        val sameCreator = testSound("cu_same", ContentSource.COMMUNITY, "Same Creator").copy(communityUploaderId = "creator.1")
        val keep = testSound("cu_keep", ContentSource.COMMUNITY, "Keep").copy(communityUploaderId = "creator-2")

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        every { uploadRepo.getCommunityUploads(limit = 50) } returns flowOf(listOf(blocked, sameCreator, keep))
        coEvery { blockRepo.blockUser("creator/1", CommunityBlockReason.OTHER) } returns Result.success(Unit)

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            uploadRepoOverride = uploadRepo,
            communityBlockRepoOverride = blockRepo,
        )
        advanceUntilIdle()

        viewModel.selectTab(SoundTab.COMMUNITY)
        advanceUntilIdle()
        if (BuildConfig.FOSS_BUILD) {
            assertTrue(viewModel.state.value.sounds.isEmpty())
            coVerify(exactly = 0) { blockRepo.blockUser(any(), any()) }
            return@runTest
        }
        assertEquals(listOf("cu_blocked", "cu_same", "cu_keep"), viewModel.state.value.sounds.map { it.id })

        assertTrue(viewModel.canBlockCommunitySound(blocked))
        viewModel.blockCommunitySound(blocked)
        advanceUntilIdle()

        assertEquals(listOf("cu_keep"), viewModel.state.value.sounds.map { it.id })
        assertEquals("Creator blocked", viewModel.state.value.applySuccess)
        coVerify(exactly = 1) { blockRepo.blockUser("creator/1", CommunityBlockReason.OTHER) }
    }

    @Test
    fun `canDeleteCommunitySound delegates owner availability to upload repository`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val uploadRepo = mockk<UploadRepository>(relaxed = true)

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { uploadRepo.canDeleteSoundUpload("cu_owner_sound") } returns true

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            uploadRepoOverride = uploadRepo,
        )
        advanceUntilIdle()

        assertTrue(viewModel.canDeleteCommunitySound(testSound("cu_owner_sound", ContentSource.COMMUNITY, "Owner tone")))
        coVerify(exactly = 1) { uploadRepo.canDeleteSoundUpload("cu_owner_sound") }
        assertFalse(viewModel.canDeleteCommunitySound(testSound("yt_owner_sound", ContentSource.YOUTUBE, "Remote tone")))
    }

    @Test
    fun `deleteCommunitySound deletes through upload repository and reports success`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val uploadRepo = mockk<UploadRepository>(relaxed = true)
        val sound = testSound("cu_owner_sound", ContentSource.COMMUNITY, "Owner tone")

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { uploadRepo.deleteSoundUpload(sound.id) } returns Result.success(Unit)

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            uploadRepoOverride = uploadRepo,
        )
        advanceUntilIdle()

        var deletionCompleted = false
        viewModel.deleteCommunitySound(sound) { deletionCompleted = true }
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadRepo.deleteSoundUpload(sound.id) }
        assertEquals("Upload deleted", viewModel.state.value.applySuccess)
        assertTrue(deletionCompleted)
    }

    @Test
    fun `deleteCommunitySound keeps detail open and reports failure`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val uploadRepo = mockk<UploadRepository>(relaxed = true)
        val sound = testSound("cu_owner_sound", ContentSource.COMMUNITY, "Owner tone")

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { uploadRepo.deleteSoundUpload(sound.id) } returns Result.failure(IllegalStateException("Offline"))

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            uploadRepoOverride = uploadRepo,
        )
        advanceUntilIdle()

        var deletionCompleted = false
        viewModel.deleteCommunitySound(sound) { deletionCompleted = true }
        advanceUntilIdle()

        coVerify(exactly = 1) { uploadRepo.deleteSoundUpload(sound.id) }
        assertFalse(deletionCompleted)
        assertEquals("Delete failed: Offline", viewModel.state.value.error)
    }

    @Test
    fun `initial load prebuffers first eight provider preview urls`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()
        val audioPreviewCache = mockk<AudioPreviewCache>()
        val providerSounds = (1..9).map { index ->
            testSound("yt_$index", ContentSource.YOUTUBE, "Clean Tone $index")
        }

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns SearchResult(
            items = providerSounds,
            totalCount = providerSounds.size,
            currentPage = 1,
            hasMore = false,
        )
        coEvery { audioPreviewCache.prebuffer(any()) } returns true

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            audioPreviewCacheOverride = audioPreviewCache,
        )

        advanceUntilIdle()

        val readyIds = viewModel.previewReadyIds.value
        providerSounds.take(8).forEach { sound ->
            assertTrue(readyIds.contains(sound.stableKey()))
            coVerify(exactly = 1) { audioPreviewCache.prebuffer(match { it.id == sound.id }) }
        }
        assertFalse(readyIds.contains(providerSounds[8].stableKey()))
        coVerify(exactly = 0) { audioPreviewCache.prebuffer(match { it.id == providerSounds[8].id }) }
    }

    @Test
    fun `switching tabs cancels the old feed's prebuffers so the new feed gets the slots`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()
        val audioPreviewCache = mockk<AudioPreviewCache>()
        val ringtones = (1..8).map { testSound("yt_ring_$it", ContentSource.YOUTUBE, "Ring Tone $it") }
        val alerts = (1..3).map { testSound("yt_alert_$it", ContentSource.YOUTUBE, "Alert Tone $it") }

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns
            SearchResult(items = ringtones, totalCount = ringtones.size, currentPage = 1, hasMore = false)
        var startedRingtonePrebuffers = 0
        var cancelledRingtonePrebuffers = 0
        // Ringtone clips never finish, so without cancellation they hold every shared slot.
        coEvery { audioPreviewCache.prebuffer(any()) } coAnswers {
            if (firstArg<Sound>().id.startsWith("yt_ring_")) {
                startedRingtonePrebuffers++
                try {
                    awaitCancellation()
                } finally {
                    cancelledRingtonePrebuffers++
                }
            } else {
                true
            }
        }

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            audioPreviewCacheOverride = audioPreviewCache,
        )
        advanceUntilIdle()
        assertTrue(startedRingtonePrebuffers > 0)
        assertEquals(0, cancelledRingtonePrebuffers)

        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns
            SearchResult(items = alerts, totalCount = alerts.size, currentPage = 1, hasMore = false)
        viewModel.selectTab(SoundTab.NOTIFICATIONS)
        advanceUntilIdle()

        assertEquals(startedRingtonePrebuffers, cancelledRingtonePrebuffers)
        alerts.forEach { assertTrue(viewModel.previewReadyIds.value.contains(it.stableKey())) }
    }

    @Test
    fun `loadMore is disabled for youtube sound feed`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("yt_page1", ContentSource.YOUTUBE, "Morning Bell")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        assertEquals(setOf("yt_page1"), viewModel.state.value.sounds.map { it.id }.toSet())
        assertFalse(viewModel.state.value.hasMore)
        clearMocks(youtubeRepo, answers = false, recordedCalls = true)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(setOf("yt_page1"), viewModel.state.value.sounds.map { it.id }.toSet())
        assertFalse(viewModel.state.value.hasMore)
        coVerify(exactly = 0) { youtubeRepo.searchSounds(any(), any(), any(), any()) }
    }

    @Test
    fun `youtube search does not expose unsupported pagination`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        coEvery { youtubeRepo.searchSounds("focus", any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("yt_focus", ContentSource.YOUTUBE, "Focus Loop")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        viewModel.searchYouTube("focus")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SoundTab.YOUTUBE, state.selectedTab)
        assertEquals(listOf("yt_focus"), state.sounds.map { it.id })
        assertFalse(state.hasMore)
    }

    @Test
    fun `thirty result youtube search resolves previews only for the visible window`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        val results = (1..30).map { testSound("yt_clip$it", ContentSource.YOUTUBE, "Clip $it") }
        coEvery { youtubeRepo.searchSounds("focus", any(), any(), any()) } returns SearchResult(
            items = results,
            totalCount = results.size,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )
        advanceUntilIdle()
        viewModel.searchYouTube("focus")
        advanceUntilIdle()

        val feed = viewModel.state.value.sounds
        assertEquals(30, feed.size)
        coVerify(atMost = INITIAL_PREVIEW_RESOLVE_WINDOW) { youtubeRepo.getAudioPreviewUrl(any()) }

        // Scrolling to rows 10..13 resolves those plus one lookahead, nothing further down.
        val window = previewResolveWindow(feed, firstVisibleIndex = 10, lastVisibleIndex = 13)
        viewModel.onVisibleSoundsChanged(window)
        viewModel.onVisibleSoundsChanged(window)
        advanceUntilIdle()

        window.forEach { sound ->
            coVerify(exactly = 1) { youtubeRepo.getAudioPreviewUrl(sound.id.removePrefix("yt_")) }
        }
        feed.drop(15).forEach { sound ->
            coVerify(exactly = 0) { youtubeRepo.getAudioPreviewUrl(sound.id.removePrefix("yt_")) }
        }
    }

    @Test
    fun `selecting youtube tab loads default results`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.searchSounds("Ringtones", any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("yt_default", ContentSource.YOUTUBE, "Default Loop")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        clearMocks(youtubeRepo, answers = false, recordedCalls = true)

        viewModel.selectTab(SoundTab.YOUTUBE)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SoundTab.YOUTUBE, state.selectedTab)
        assertEquals("Ringtones", state.query)
        assertEquals(listOf("yt_default"), state.sounds.map { it.id })
        assertFalse(state.hasMore)
        coVerify(exactly = 1) { youtubeRepo.searchSounds("Ringtones", 45, 5, listOf("mix", "podcast")) }
    }

    @Test
    fun `notification tab uses youtube queries with short duration cap`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        clearMocks(youtubeRepo, answers = false, recordedCalls = true)

        viewModel.selectTab(SoundTab.NOTIFICATIONS)
        advanceUntilIdle()

        coVerify(exactly = 1) { youtubeRepo.searchSounds("Notifications", 8, 0, listOf("mix", "podcast")) }
        coVerify(exactly = 1) { youtubeRepo.searchSounds("notification sound effect short", 8, 0, listOf("mix", "podcast")) }
        coVerify(exactly = 1) { youtubeRepo.searchSounds("phone notification sound effect", 8, 0, listOf("mix", "podcast")) }
    }

    @Test
    fun `resolving youtube preview updates selected sound`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.getAudioPreviewUrl("focus") } returns "https://example.com/focus.mp3"

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        val unresolved = testSound("yt_focus", ContentSource.YOUTUBE, "Focus Loop").copy(
            previewUrl = "",
            downloadUrl = "",
        )

        viewModel.selectSound(unresolved)
        viewModel.togglePlayback(unresolved)
        advanceUntilIdle()

        assertEquals("https://example.com/focus.mp3", viewModel.selectedSound.value?.previewUrl)
    }

    @Test
    fun `youtube sounds refresh stale preview urls before playback`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        every { youtubeRepo.isCached("focus12345") } returns false
        coEvery { youtubeRepo.getAudioPreviewUrl("focus12345") } returns "https://example.com/fresh-focus.mp3"

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        val staleFavorite = testSound("yt_focus12345", ContentSource.YOUTUBE, "Focus Loop").copy(
            previewUrl = "https://expired.example.com/focus-preview.mp3",
            downloadUrl = "https://expired.example.com/focus-download.mp3",
        )

        viewModel.selectSound(staleFavorite)
        viewModel.togglePlayback(staleFavorite)
        advanceUntilIdle()

        assertEquals("https://example.com/fresh-focus.mp3", viewModel.selectedSound.value?.previewUrl)
    }

    @Test
    fun `cached youtube playback shows loading state until audio starts`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()
        val currentSoundId = MutableStateFlow<String?>(null)
        val isPlaying = MutableStateFlow(false)
        val audioPlaybackManager = mockk<AudioPlaybackManager>(relaxed = true)

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        every { youtubeRepo.isCached("focus12345") } returns true
        every { audioPlaybackManager.currentSoundId } returns currentSoundId
        every { audioPlaybackManager.currentPosition } returns MutableStateFlow(0L)
        every { audioPlaybackManager.duration } returns MutableStateFlow(1_000L)
        every { audioPlaybackManager.isPlaying } returns isPlaying

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            audioPlaybackManagerOverride = audioPlaybackManager,
        )

        advanceUntilIdle()
        val youtubeSound = testSound("yt_focus12345", ContentSource.YOUTUBE, "Focus Loop")

        viewModel.togglePlayback(youtubeSound)
        advanceUntilIdle()

        assertEquals(youtubeSound.stableKey(), viewModel.state.value.resolvingId)

        isPlaying.value = true
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.resolvingId)
    }

    @Test
    fun `tab changes keep current playback alive`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val currentSoundId = MutableStateFlow<String?>(null)
        val audioPlaybackManager = mockk<AudioPlaybackManager>(relaxed = true)

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        every { audioPlaybackManager.currentSoundId } returns currentSoundId
        every { audioPlaybackManager.currentPosition } returns MutableStateFlow(0L)
        every { audioPlaybackManager.duration } returns MutableStateFlow(1_000L)
        every { audioPlaybackManager.isPlaying } returns MutableStateFlow(true)

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            audioPlaybackManagerOverride = audioPlaybackManager,
        )

        advanceUntilIdle()
        val sound = testSound("bundled_keep_playing", ContentSource.BUNDLED, "Keep Playing")
        currentSoundId.value = sound.stableKey()
        advanceUntilIdle()
        clearMocks(audioPlaybackManager, answers = false, recordedCalls = true)

        viewModel.selectTab(SoundTab.NOTIFICATIONS)
        advanceUntilIdle()

        assertEquals(SoundTab.NOTIFICATIONS, viewModel.state.value.selectedTab)
        assertEquals(sound.stableKey(), viewModel.state.value.playingId)
        verify(exactly = 0) { audioPlaybackManager.stop() }
    }

    @Test
    fun `downloadSound refreshes youtube stream urls instead of reusing stale favorites`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()
        val downloadManager = mockk<DownloadManager>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.getAudioStreamUrl("focus12345") } returns "https://example.com/fresh-focus-download.mp3"
        coEvery {
            downloadManager.downloadSound(
                id = testSound("yt_focus12345", ContentSource.YOUTUBE, "Focus Loop").stableKey(),
                url = "https://example.com/fresh-focus-download.mp3",
                fileName = any(),
                type = ContentType.RINGTONE,
                source = ContentSource.YOUTUBE.name,
            )
        } returns Result.success(mockk(relaxed = true))

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            downloadManagerOverride = downloadManager,
        )

        advanceUntilIdle()
        val staleFavorite = testSound("yt_focus12345", ContentSource.YOUTUBE, "Focus Loop").copy(
            downloadUrl = "https://expired.example.com/focus-download.mp3",
        )

        viewModel.downloadSound(staleFavorite, confirmed = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { youtubeRepo.getAudioStreamUrl("focus12345") }
        coVerify(exactly = 1) {
            downloadManager.downloadSound(
                id = staleFavorite.stableKey(),
                url = "https://example.com/fresh-focus-download.mp3",
                fileName = match { it == "Aura_youtube_yt_focus12345_Focus Loop.mp3" },
                type = ContentType.RINGTONE,
                source = ContentSource.YOUTUBE.name,
            )
        }
    }

    @Test
    fun `downloadSound blocks legacy attribution-only records before transport`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val downloadManager = mockk<DownloadManager>()
        val favoritesRepo = mockk<FavoritesRepository>()
        val sound = testSound("fs_42", ContentSource.FREESOUND, "Removed tone").copy(
            downloadUrl = "https://cdn.freesound.org/removed.mp3",
            sourcePageUrl = "https://freesound.org/s/42/",
        )

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { favoritesRepo.markSourceUnavailable(any(), any()) } returns Unit
        coEvery { downloadManager.downloadSound(any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("Download failed: HTTP 410"))

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            downloadManagerOverride = downloadManager,
            favoritesRepoOverride = favoritesRepo,
        )

        advanceUntilIdle()
        viewModel.downloadSound(sound)
        advanceUntilIdle()

        coVerify(exactly = 0) { downloadManager.downloadSound(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { favoritesRepo.markSourceUnavailable(any(), any()) }
        assertTrue(viewModel.state.value.error.orEmpty().contains("attribution only"))
    }

    @Test
    fun `blank search state does not hit providers`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        clearMocks(youtubeRepo, answers = false, recordedCalls = true)

        viewModel.selectTab(SoundTab.SEARCH)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SoundTab.SEARCH, state.selectedTab)
        assertTrue(state.sounds.isEmpty())
        assertFalse(state.hasMore)
        assertEquals("", state.query)
        coVerify(exactly = 0) { youtubeRepo.searchSounds(any(), any(), any(), any()) }
    }

    @Test
    fun `empty search surfaces provider failure`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        coEvery { youtubeRepo.searchSounds(match { it.contains("focus") }, any(), any(), any()) } throws UnknownHostException()

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        viewModel.search("focus")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SoundTab.SEARCH, state.selectedTab)
        assertTrue(state.sounds.isEmpty())
        assertFalse(state.hasMore)
        assertEquals("No internet connection", state.error)
    }

    @Test
    fun `refresh preserves current sounds when providers return empty`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()
        val providerSound = testSound("yt_keep", ContentSource.YOUTUBE, "Clean Bell")

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(providerSound),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        assertEquals(listOf("yt_keep"), viewModel.state.value.sounds.map { it.id })

        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns emptySoundResult()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("yt_keep"), state.sounds.map { it.id })
        assertEquals(null, state.error)
    }

    @Test
    fun `refresh preserves current search results but updates pagination when providers go empty`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        coEvery { youtubeRepo.searchSounds(match { it.contains("focus") }, any(), any(), any()) } returns SearchResult(
            items = listOf(testSound("focus_keep", ContentSource.YOUTUBE, "Focus Keep")),
            totalCount = 1,
            currentPage = 1,
            hasMore = true,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        viewModel.search("focus")
        advanceUntilIdle()

        assertEquals(listOf("focus_keep"), viewModel.state.value.sounds.map { it.id })
        assertFalse(viewModel.state.value.hasMore)

        coEvery { youtubeRepo.searchSounds(match { it.contains("focus") }, any(), any(), any()) } returns emptySoundResult()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("focus_keep"), state.sounds.map { it.id })
        assertFalse(state.hasMore)
        assertEquals(null, state.error)
    }

    @Test
    fun `refresh preserves current sounds and surfaces degraded error`() = runTest(dispatcher) {
        assumeYouTubeAvailable()
        val youtubeRepo = mockk<YouTubeRepository>()
        val providerSound = testSound("yt_keep", ContentSource.YOUTUBE, "Clean Bell")

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns SearchResult(
            items = listOf(providerSound),
            totalCount = 1,
            currentPage = 1,
            hasMore = false,
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
        )

        advanceUntilIdle()
        assertEquals(listOf("yt_keep"), viewModel.state.value.sounds.map { it.id })

        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } throws UnknownHostException()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("yt_keep"), state.sounds.map { it.id })
        assertEquals("No internet connection. Showing your last good results.", state.error)
    }

    @Test
    fun `stopIfPlaying stops matching stable-key playback`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val currentSoundId = MutableStateFlow<String?>(null)
        val audioPlaybackManager = mockk<AudioPlaybackManager>(relaxed = true)

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )
        every { audioPlaybackManager.currentSoundId } returns currentSoundId
        every { audioPlaybackManager.currentPosition } returns MutableStateFlow(0L)
        every { audioPlaybackManager.duration } returns MutableStateFlow(1_000L)
        every { audioPlaybackManager.isPlaying } returns MutableStateFlow(false)

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            audioPlaybackManagerOverride = audioPlaybackManager,
        )

        advanceUntilIdle()

        val sound = testSound("dup_sound", ContentSource.BUNDLED, "Aura Bell")
        viewModel.selectSound(sound)
        currentSoundId.value = sound.stableKey()
        advanceUntilIdle()

        viewModel.stopIfPlaying(sound)

        verify(exactly = 1) { audioPlaybackManager.stop() }
    }

    @Test
    fun `resolveSound uses type-scoped favorite lookup when raw ids collide across content types`() = runTest(dispatcher) {
        val youtubeRepo = mockk<YouTubeRepository>()
        val favoritesRepo = mockk<FavoritesRepository>()

        stubCommonDependencies(
            youtubeRepo = youtubeRepo,
        )

        coEvery { favoritesRepo.getByIdentity(any()) } returns null
        coEvery { favoritesRepo.getLatestById(any()) } returns FavoriteEntity(
            id = "shared_raw",
            source = ContentSource.PEXELS.name,
            type = "WALLPAPER",
            thumbnailUrl = "thumb",
            fullUrl = "https://example.com/wallpaper.jpg",
        )
        coEvery { favoritesRepo.getLatestByIdAndType("shared_raw", "SOUND") } returns FavoriteEntity(
            id = "shared_raw",
            source = ContentSource.YOUTUBE.name,
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "https://example.com/audio.mp3",
            name = "Recovered tone",
            duration = 12.0,
            sourcePageUrl = "https://youtube.com/watch?v=abc123",
        )

        val viewModel = createViewModel(
            youtubeRepo = youtubeRepo,
            favoritesRepoOverride = favoritesRepo,
        )

        advanceUntilIdle()

        val resolved = viewModel.resolveSound("shared_raw")

        assertEquals(ContentSource.YOUTUBE, resolved?.source)
        assertEquals("Recovered tone", resolved?.name)
        coVerify(exactly = 1) { favoritesRepo.getLatestByIdAndType("shared_raw", "SOUND") }
    }

    private fun createViewModel(
        youtubeRepo: YouTubeRepository,
        bundledRingtones: List<Sound> = emptyList(),
        bundledNotifications: List<Sound> = emptyList(),
        bundledAlarms: List<Sound> = emptyList(),
        audioPlaybackManagerOverride: AudioPlaybackManager? = null,
        audioPreviewCacheOverride: AudioPreviewCache? = null,
        downloadManagerOverride: DownloadManager? = null,
        favoritesRepoOverride: FavoritesRepository? = null,
        reportRepoOverride: CommunityReportRepository? = null,
        communityBlockRepoOverride: CommunityBlockRepository? = null,
        uploadRepoOverride: UploadRepository? = null,
        youtubeProviderEnabled: Boolean = isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE),
        communityProviderEnabled: Boolean = true,
        tiktokRingtones: List<Sound> = emptyList(),
        tiktokRepoOverride: TikTokSoundRepository? = null,
    ): SoundsViewModel {
        val prefs = mockk<PreferencesManager>()
        every { prefs.autoPreviewSounds } returns flowOf(true)
        every { prefs.soundPreviewVolume } returns flowOf(0.7f)
        every { prefs.ytSoundQueryRingtones } returns flowOf("Ringtones")
        every { prefs.ytSoundQueryNotifications } returns flowOf("Notifications")
        every { prefs.ytSoundQueryAlarms } returns flowOf("Alarms")
        every { prefs.ytSoundBlockedWords } returns flowOf("mix,podcast")
        every { prefs.youtubeProviderEnabled } returns flowOf(youtubeProviderEnabled)
        every { prefs.communityProviderEnabled } returns flowOf(communityProviderEnabled)
        every { prefs.communityGuidelinesAccepted } returns flowOf(true)

        val searchHistoryRepo = mockk<SearchHistoryRepository>()
        every { searchHistoryRepo.getRecentSoundSearches(any()) } returns flowOf(emptyList<SearchHistoryEntity>())
        coEvery { searchHistoryRepo.addSoundSearch(any()) } returns Unit
        coEvery { searchHistoryRepo.removeSearch(any(), any()) } returns Unit
        coEvery { searchHistoryRepo.clearSoundHistory() } returns Unit

        val bundledContent = mockk<BundledContentProvider>()
        every { bundledContent.getRingtones() } returns bundledRingtones
        every { bundledContent.getNotifications() } returns bundledNotifications
        every { bundledContent.getAlarms() } returns bundledAlarms

        val audioPlaybackManager = audioPlaybackManagerOverride ?: mockk<AudioPlaybackManager>(relaxed = true).also {
            every { it.currentSoundId } returns MutableStateFlow(null)
            every { it.currentPosition } returns MutableStateFlow(0L)
            every { it.duration } returns MutableStateFlow(0L)
            every { it.isPlaying } returns MutableStateFlow(false)
        }

        val audioPreviewCache = audioPreviewCacheOverride ?: mockk<AudioPreviewCache>().also {
            coEvery { it.prebuffer(any()) } returns true
        }

        val soundUrlResolver = mockk<SoundUrlResolver>()
        coEvery { soundUrlResolver.resolve(any()) } answers { firstArg<Sound>().downloadUrl }

        val favoritesRepo = favoritesRepoOverride ?: mockk<FavoritesRepository>(relaxed = true).also {
            coEvery { it.getByIdentity(any()) } returns null
            coEvery { it.getLatestByIdAndType(any(), any()) } returns null
        }

        val tiktokRepo = tiktokRepoOverride ?: mockk<TikTokSoundRepository>().also {
            coEvery { it.ringtones(any()) } returns tiktokRingtones
        }

        return SoundsViewModel(
            context = localizedFeedbackContext(),
            youtubeRepo = youtubeRepo,
            tiktokRepo = tiktokRepo,
            favoritesRepo = favoritesRepo,
            soundApplier = mockk<SoundApplier>(relaxed = true),
            downloadManager = downloadManagerOverride ?: mockk<DownloadManager>(relaxed = true),
            selectedContent = testSelectedContentHolder(),
            searchHistoryRepo = searchHistoryRepo,
            prefs = prefs,
            voteRepo = mockk<VoteRepository>(relaxed = true),
            reportRepo = reportRepoOverride ?: mockk<CommunityReportRepository>(relaxed = true),
            communityBlockRepo = communityBlockRepoOverride ?: mockk<CommunityBlockRepository>(relaxed = true),
            bundledContent = bundledContent,
            audioPlaybackManager = audioPlaybackManager,
            audioPreviewCache = audioPreviewCache,
            uploadRepo = uploadRepoOverride ?: mockk<UploadRepository>(relaxed = true),
            soundUrlResolver = soundUrlResolver,
            seasonalContentManager = SeasonalContentManager(),
            communityAudioRecorder = mockk<com.freevibe.service.CommunityAudioRecorder>(relaxed = true),
            sourceMetrics = com.freevibe.service.SourceMetrics(),
            soundFeedCache = mockk<SoundFeedCache>(relaxed = true),
        )
    }

    private fun localizedFeedbackContext(): Context = mockk<Context>(relaxed = true).also { context ->
        every { context.getString(R.string.sound_feedback_community_disabled) } returns
            "Community source is disabled in Settings"
        every { context.getString(R.string.feedback_report_submitted) } returns "Report submitted"
        every { context.getString(R.string.feedback_creator_blocked) } returns "Creator blocked"
        every { context.getString(R.string.feedback_upload_deleted) } returns "Upload deleted"
        every { context.getString(R.string.feedback_delete_failed, "Offline") } returns "Delete failed: Offline"
    }

    private fun stubCommonDependencies(
        youtubeRepo: YouTubeRepository,
    ) {
        every { youtubeRepo.extractionStatus } returns MutableStateFlow(YouTubeExtractionStatus())
        every { youtubeRepo.isCached(any()) } returns false
        coEvery { youtubeRepo.searchSounds(any(), any(), any(), any()) } returns emptySoundResult()
        coEvery { youtubeRepo.getAudioPreviewUrl(any()) } returns null
        coEvery { youtubeRepo.getAudioStreamUrl(any()) } returns null
    }

    private fun emptySoundResult(page: Int = 1) = SearchResult(
        items = emptyList<Sound>(),
        totalCount = 0,
        currentPage = page,
        hasMore = false,
    )

    private fun assumeYouTubeAvailable() {
        assumeTrue(
            "YouTube is intentionally excluded from this artifact",
            isProviderAvailableInCurrentArtifact(ContentSource.YOUTUBE),
        )
    }

    private fun tiktokSound(
        videoId: String,
        name: String,
        expiresAtSec: Long = System.currentTimeMillis() / 1000 + 86_400,
    ): Sound {
        val url = "https://v16m.tiktokcdn-us.com/2efdb7eb5474a4275cb2492f10c5c70a/" +
            "${expiresAtSec.toString(16).padStart(8, '0')}/video/tos/$videoId/"
        return Sound(
            id = "tt_$videoId",
            source = ContentSource.TIKTOK,
            name = name,
            previewUrl = url,
            downloadUrl = url,
            duration = 20.0,
            license = "TikTok",
            uploaderName = "Ringtones for iPhone",
            sourcePageUrl = "https://www.tiktok.com/@ringtonesforiphone/video/$videoId",
        )
    }

    private fun testSound(id: String, source: ContentSource, name: String) = Sound(
        id = id,
        source = source,
        name = name,
        description = "",
        previewUrl = "https://example.com/$id.mp3",
        downloadUrl = "https://example.com/$id.mp3",
        duration = 12.0,
        tags = listOf("clean", "tone"),
        license = "CC0",
        uploaderName = "Tester",
    )
}
