package com.freevibe.ui.screens.settings

import android.content.Context
import android.net.Uri
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.local.WallpaperCacheManager
import com.freevibe.data.model.CommunityBlockReason
import com.freevibe.data.repository.CommunityBlockedUser
import com.freevibe.data.repository.CommunityBlockRepository
import com.freevibe.data.repository.VoteRepository
import com.freevibe.service.BackgroundNetworkDiagnostics
import com.freevibe.service.BackgroundWorkDiagnostics
import com.freevibe.service.BackgroundWorkDiagnosticsReader
import com.freevibe.service.BackgroundWorkStatusRow
import com.freevibe.service.CommunityIdentityProvider
import com.freevibe.service.CommunityIdentitySummary
import com.freevibe.service.CrashDiagnosticsCollector
import com.freevibe.service.OfflineFavoritesManager
import com.freevibe.service.VideoWallpaperSelectionResult
import com.freevibe.service.VideoWallpaperStorage
import com.freevibe.service.WallpaperHistoryManager
import com.freevibe.service.YtDlpUpdateManager
import com.freevibe.service.YtDlpUpdateResult
import com.freevibe.service.YtDlpUpdateSnapshot
import com.freevibe.service.YtDlpUpdateStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tempDirs = mutableListOf<File>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun `cacheUsage includes offline favorites and wallpaper metadata`() = runTest(dispatcher) {
        val root = createTempDirectory("settings-cache-usage").toFile().also(tempDirs::add)
        val cacheDir = File(root, "cache").apply { mkdirs() }
        File(cacheDir, "discover.json").writeBytes(ByteArray(1024))

        val viewModel = createViewModel(
            cacheDir = cacheDir,
            offlineFavoritesSize = 2048L,
            wallpaperCacheCounts = listOf(3),
        )

        waitForIdle {
            advanceUntilIdle()
            viewModel.cacheUsage.value.fileUsageLabel == "3.0 KB" &&
                viewModel.cacheUsage.value.hasWallpaperMetadataCache
        }

        assertEquals("3.0 KB", viewModel.cacheUsage.value.fileUsageLabel)
        assertTrue(viewModel.cacheUsage.value.hasWallpaperMetadataCache)
    }

    @Test
    fun `clearCache clears temp files and metadata cache but preserves trimmed exports`() = runTest(dispatcher) {
        val root = createTempDirectory("settings-clear-cache").toFile().also(tempDirs::add)
        val cacheDir = File(root, "cache").apply { mkdirs() }
        val tempFile = File(cacheDir, "temp-preview.jpg").apply { writeText("preview") }
        val trimmedDir = File(cacheDir, "trimmed").apply { mkdirs() }
        val trimmedFile = File(trimmedDir, "clip.mp3").apply { writeText("keep me") }

        val offlineFavorites = mockk<OfflineFavoritesManager>()
        every { offlineFavorites.getCacheSize() } returns 0L
        coEvery { offlineFavorites.clearAll() } returns Unit

        val wallpaperCacheManager = mockk<WallpaperCacheManager>()
        coEvery { wallpaperCacheManager.countEntries() } returnsMany listOf(2, 0)
        coEvery { wallpaperCacheManager.clearAll() } returns Unit

        val viewModel = createViewModel(
            cacheDir = cacheDir,
            offlineFavoritesOverride = offlineFavorites,
            wallpaperCacheManagerOverride = wallpaperCacheManager,
        )

        advanceUntilIdle()
        viewModel.clearCache()
        waitForIdle {
            advanceUntilIdle()
            !tempFile.exists() &&
                viewModel.cacheUsage.value.fileUsageLabel == "0 B" &&
                !viewModel.cacheUsage.value.hasWallpaperMetadataCache
        }

        assertFalse(tempFile.exists())
        assertTrue(trimmedFile.exists())
        assertEquals("0 B", viewModel.cacheUsage.value.fileUsageLabel)
        assertFalse(viewModel.cacheUsage.value.hasWallpaperMetadataCache)
        coVerify(exactly = 1) { offlineFavorites.clearAll() }
        coVerify(exactly = 1) { wallpaperCacheManager.clearAll() }
    }

    @Test
    fun `prepareVideoWallpaperFromUri publishes ready when storage succeeds`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val storage = mockk<VideoWallpaperStorage>()
        coEvery { storage.prepareFromUri(uri) } returns Result.success(File("live_wallpaper.mp4"))
        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-video-ready").toFile().also(tempDirs::add),
            videoWallpaperStorageOverride = storage,
        )

        viewModel.prepareVideoWallpaperFromUri(uri)
        advanceUntilIdle()

        assertEquals(VideoWallpaperSelectionResult.Ready, viewModel.videoWallpaperSelectionResult.value)
    }

    @Test
    fun `prepareVideoWallpaperFromUri publishes failure when storage rejects input`() = runTest(dispatcher) {
        val uri = mockk<Uri>()
        val storage = mockk<VideoWallpaperStorage>()
        coEvery { storage.prepareFromUri(uri) } returns Result.failure(
            IllegalStateException("Selected file is empty or invalid"),
        )
        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-video-failure").toFile().also(tempDirs::add),
            videoWallpaperStorageOverride = storage,
        )

        viewModel.prepareVideoWallpaperFromUri(uri)
        advanceUntilIdle()

        val failure = viewModel.videoWallpaperSelectionResult.value as VideoWallpaperSelectionResult.Failure
        assertEquals("Selected file is empty or invalid", failure.message)
    }

    @Test
    fun `isAdmin exposes community moderation access`() = runTest(dispatcher) {
        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-admin").toFile().also(tempDirs::add),
            isAdmin = true,
        )

        assertTrue(viewModel.isAdmin)
    }

    @Test
    fun `unblockCommunityCreator delegates to block repository and reports success`() = runTest(dispatcher) {
        val blockRepo = mockk<CommunityBlockRepository>()
        every { blockRepo.blockedUsers() } returns flowOf(
            listOf(CommunityBlockedUser("creator_1", CommunityBlockReason.OTHER, 123L)),
        )
        coEvery { blockRepo.unblockUser("creator_1") } returns Result.success(Unit)

        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-unblock").toFile().also(tempDirs::add),
            communityBlockRepoOverride = blockRepo,
        )
        advanceUntilIdle()

        assertEquals(listOf("creator_1"), viewModel.blockedCommunityCreators.value.map { it.userId })

        viewModel.unblockCommunityCreator("creator_1")
        advanceUntilIdle()

        assertEquals("Creator unblocked", viewModel.communityBlockAction.value.message)
        coVerify(exactly = 1) { blockRepo.unblockUser("creator_1") }
    }

    @Test
    fun `refreshCommunityIdentitySummary exposes redacted identity request code`() = runTest(dispatcher) {
        val identityProvider = mockk<CommunityIdentityProvider>()
        every { identityProvider.currentIdentitySummary() } returnsMany listOf(
            CommunityIdentitySummary(),
            CommunityIdentitySummary(
                authLabel = "Anonymous Firebase identity",
                identitySuffix = "abcd1234",
                deletionRequestCode = "AURA-123456789ABC",
                hasFirebaseIdentity = true,
            ),
        )

        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-identity").toFile().also(tempDirs::add),
            communityIdentityProviderOverride = identityProvider,
        )

        assertEquals("Not created", viewModel.communityIdentitySummary.value.identitySuffix)

        viewModel.refreshCommunityIdentitySummary()

        assertEquals("Anonymous Firebase identity", viewModel.communityIdentitySummary.value.authLabel)
        assertEquals("abcd1234", viewModel.communityIdentitySummary.value.identitySuffix)
        assertEquals("AURA-123456789ABC", viewModel.communityIdentitySummary.value.deletionRequestCode)
        assertTrue(viewModel.communityIdentitySummary.value.hasFirebaseIdentity)
    }

    @Test
    fun `clearLocalCommunityIdentity clears fallback identity and refreshes summary`() = runTest(dispatcher) {
        val identityProvider = mockk<CommunityIdentityProvider>()
        every { identityProvider.currentIdentitySummary() } returnsMany listOf(
            CommunityIdentitySummary(
                authLabel = "Local identity",
                identitySuffix = "local1234",
            ),
            CommunityIdentitySummary(),
        )
        every { identityProvider.clearLocalFallbackIdentity() } returns true

        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-local-cleanup").toFile().also(tempDirs::add),
            communityIdentityProviderOverride = identityProvider,
        )

        assertEquals("local1234", viewModel.communityIdentitySummary.value.identitySuffix)

        viewModel.clearLocalCommunityIdentity()
        advanceUntilIdle()

        assertEquals("Not created", viewModel.communityIdentitySummary.value.identitySuffix)
        assertEquals("Local community identity cleared", viewModel.communityIdentityCleanup.value.message)
        verify(exactly = 1) { identityProvider.clearLocalFallbackIdentity() }
    }

    @Test
    fun `refreshBackgroundWorkDiagnostics publishes WorkInfo and network receipts`() = runTest(dispatcher) {
        val reader = FakeBackgroundWorkDiagnosticsReader(
            BackgroundWorkDiagnostics(
                network = BackgroundNetworkDiagnostics(
                    activeNetworkMetered = true,
                    restrictBackgroundStatus = "enabled",
                ),
                rows = listOf(
                    BackgroundWorkStatusRow(
                        label = "Auto wallpaper rotation",
                        uniqueWorkName = "auto_wallpaper",
                        workInfoStatus = "ENQUEUED=1",
                        workInfoCount = 1,
                        maxRunAttemptCount = 2,
                        lastSuccessUtc = "2026-06-07T12:00:00Z",
                        lastResult = "success",
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-background-diagnostics").toFile().also(tempDirs::add),
            backgroundWorkDiagnosticsReaderOverride = reader,
        )

        advanceUntilIdle()

        assertEquals("enabled", viewModel.backgroundWorkDiagnostics.value.network.restrictBackgroundStatus)
        assertEquals(true, viewModel.backgroundWorkDiagnostics.value.network.activeNetworkMetered)
        assertEquals("auto_wallpaper", viewModel.backgroundWorkDiagnostics.value.rows.single().uniqueWorkName)
        assertEquals("ENQUEUED=1", viewModel.backgroundWorkDiagnostics.value.rows.single().workInfoStatus)
        assertEquals("2026-06-07T12:00:00Z", viewModel.backgroundWorkDiagnostics.value.rows.single().lastSuccessUtc)
        assertEquals("success", viewModel.backgroundWorkDiagnostics.value.rows.single().lastResult)

        reader.snapshot = BackgroundWorkDiagnostics(
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = false,
                restrictBackgroundStatus = "disabled",
            ),
            rows = listOf(
                BackgroundWorkStatusRow(
                    label = "Weather wallpaper refresh",
                    uniqueWorkName = "weather_update",
                    workInfoStatus = "No WorkInfo records",
                ),
            ),
        )
        viewModel.refreshBackgroundWorkDiagnostics()
        advanceUntilIdle()

        assertEquals("disabled", viewModel.backgroundWorkDiagnostics.value.network.restrictBackgroundStatus)
        assertEquals(false, viewModel.backgroundWorkDiagnostics.value.network.activeNetworkMetered)
        assertEquals("weather_update", viewModel.backgroundWorkDiagnostics.value.rows.single().uniqueWorkName)
        assertEquals("No WorkInfo records", viewModel.backgroundWorkDiagnostics.value.rows.single().workInfoStatus)
    }

    @Test
    fun `updateYtDlp delegates to manager and publishes pending validation state`() = runTest(dispatcher) {
        val manager = mockk<YtDlpUpdateManager>()
        val initial = YtDlpUpdateSnapshot(activeVersionName = "bundled")
        val updated = YtDlpUpdateSnapshot(
            activeVersionName = "2026.06.12",
            lastStatus = YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION,
            lastAttemptAtMs = 123L,
            pendingValidation = true,
            rollbackAvailable = true,
        )
        every { manager.snapshot() } returns initial
        coEvery { manager.updateStable() } returns YtDlpUpdateResult(
            status = YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION,
            snapshot = updated,
        )
        val viewModel = createViewModel(
            cacheDir = createTempDirectory("settings-ytdlp").toFile().also(tempDirs::add),
            ytDlpUpdateManagerOverride = manager,
        )

        viewModel.updateYtDlp()
        advanceUntilIdle()

        assertFalse(viewModel.ytDlpUpdate.value.isUpdating)
        assertEquals(YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION, viewModel.ytDlpUpdate.value.completedStatus)
        assertEquals(updated, viewModel.ytDlpUpdate.value.snapshot)
        coVerify(exactly = 1) { manager.updateStable() }
    }

    private fun createViewModel(
        cacheDir: File,
        offlineFavoritesSize: Long = 0L,
        wallpaperCacheCounts: List<Int> = listOf(0),
        offlineFavoritesOverride: OfflineFavoritesManager? = null,
        wallpaperCacheManagerOverride: WallpaperCacheManager? = null,
        videoWallpaperStorageOverride: VideoWallpaperStorage? = null,
        communityBlockRepoOverride: CommunityBlockRepository? = null,
        communityIdentityProviderOverride: CommunityIdentityProvider? = null,
        backgroundWorkDiagnosticsReaderOverride: BackgroundWorkDiagnosticsReader? = null,
        ytDlpUpdateManagerOverride: YtDlpUpdateManager? = null,
        isAdmin: Boolean = false,
    ): SettingsViewModel {
        val context = mockk<Context>(relaxed = true).also {
            every { it.cacheDir } returns cacheDir
            every { it.filesDir } returns cacheDir.parentFile ?: cacheDir
            every { it.applicationContext } returns it
        }
        val prefs = mockPreferences()
        val historyManager = mockk<WallpaperHistoryManager>(relaxed = true).also {
            every { it.getRecent(any()) } returns flowOf(emptyList())
        }
        val offlineFavorites = offlineFavoritesOverride ?: mockk<OfflineFavoritesManager>().also {
            every { it.getCacheSize() } returns offlineFavoritesSize
            coEvery { it.clearAll() } returns Unit
        }
        val wallpaperCacheManager = wallpaperCacheManagerOverride ?: mockk<WallpaperCacheManager>().also {
            coEvery { it.countEntries() } returnsMany wallpaperCacheCounts
            coEvery { it.clearAll() } returns Unit
        }

        val collectionRepo = mockk<com.freevibe.data.repository.CollectionRepository>().also {
            every { it.getAll() } returns flowOf(emptyList())
        }
        val wallpaperApplier = mockk<com.freevibe.service.WallpaperApplier>(relaxed = true)
        val videoWallpaperStorage = videoWallpaperStorageOverride ?: mockk(relaxed = true)
        val voteRepo = mockk<VoteRepository>(relaxed = true).also {
            every { it.isAdmin } returns isAdmin
        }
        val communityBlockRepo = communityBlockRepoOverride ?: mockk<CommunityBlockRepository>(relaxed = true).also {
            every { it.blockedUsers() } returns flowOf(emptyList())
        }
        val communityIdentityProvider = communityIdentityProviderOverride ?: mockk<CommunityIdentityProvider>(relaxed = true).also {
            every { it.currentIdentitySummary() } returns CommunityIdentitySummary()
        }
        val ytDlpUpdateManager = ytDlpUpdateManagerOverride ?: mockk<YtDlpUpdateManager>().also {
            every { it.snapshot() } returns YtDlpUpdateSnapshot()
        }
        return SettingsViewModel(
            context = context,
            prefs = prefs,
            historyManager = historyManager,
            offlineFavorites = offlineFavorites,
            wallpaperCacheManager = wallpaperCacheManager,
            collectionRepo = collectionRepo,
            wallpaperApplier = wallpaperApplier,
            videoWallpaperStorage = videoWallpaperStorage,
            sourceMetrics = com.freevibe.service.SourceMetrics(),
            crashDiagnosticsCollector = CrashDiagnosticsCollector(
                context = context,
                prefs = prefs,
                sourceMetrics = com.freevibe.service.SourceMetrics(),
                backgroundWorkDiagnosticsReader = backgroundWorkDiagnosticsReaderOverride
                    ?: FakeBackgroundWorkDiagnosticsReader(BackgroundWorkDiagnostics()),
                ytDlpUpdateManager = ytDlpUpdateManager,
                liveWallpaperReceiptStore = mockk(relaxed = true),
            ),
            backgroundWorkDiagnosticsReader = backgroundWorkDiagnosticsReaderOverride
                ?: FakeBackgroundWorkDiagnosticsReader(BackgroundWorkDiagnostics()),
            voteRepo = voteRepo,
            communityBlockRepo = communityBlockRepo,
            communityIdentityProvider = communityIdentityProvider,
            ytDlpUpdateManager = ytDlpUpdateManager,
            ioDispatcher = dispatcher,
        )
    }

    private fun mockPreferences(): PreferencesManager =
        mockk<PreferencesManager>().also { prefs ->
            every { prefs.autoWallpaperEnabled } returns flowOf(false)
            every { prefs.autoWallpaperInterval } returns flowOf(12L)
            every { prefs.autoWallpaperSource } returns flowOf("discover")
            every { prefs.localWallpaperFolderUri } returns flowOf("")
            every { prefs.schedulerEnabled } returns flowOf(false)
            every { prefs.schedulerIntervalMinutes } returns flowOf(360L)
            every { prefs.schedulerSource } returns flowOf("discover")
            every { prefs.schedulerHomeEnabled } returns flowOf(true)
            every { prefs.schedulerLockEnabled } returns flowOf(true)
            every { prefs.schedulerShuffle } returns flowOf(true)
            every { prefs.weatherEffectsEnabled } returns flowOf(false)
            every { prefs.adaptiveTintEnabled } returns flowOf(false)
            every { prefs.darkModeAutoSwitch } returns flowOf(false)
            every { prefs.autoPreviewSounds } returns flowOf(true)
            every { prefs.wallpaperGridColumns } returns flowOf(2)
            every { prefs.soundPreviewVolume } returns flowOf(0.7f)
            every { prefs.redditSubreddits } returns flowOf("wallpapers")
            every { prefs.redditProviderEnabled } returns flowOf(true)
            every { prefs.preferredResolution } returns flowOf("")
            every { prefs.userStyles } returns flowOf("")
            every { prefs.ytSoundQueryRingtones } returns flowOf("ringtone")
            every { prefs.ytSoundQueryNotifications } returns flowOf("notification")
            every { prefs.ytSoundQueryAlarms } returns flowOf("alarm")
            every { prefs.ytSoundBlockedWords } returns flowOf("mix")
            every { prefs.youtubeProviderEnabled } returns flowOf(true)
            every { prefs.videoFpsLimit } returns flowOf(30)
            every { prefs.videoFpsOverlayEnabled } returns flowOf(false)
            every { prefs.videoAutoBatterySaver } returns flowOf(true)
            every { prefs.wallhavenApiKey } returns flowOf("")
            every { prefs.pexelsApiKey } returns flowOf("")
            every { prefs.pixabayApiKey } returns flowOf("")
            every { prefs.wallhavenProviderEnabled } returns flowOf(true)
            every { prefs.bingProviderEnabled } returns flowOf(true)
            every { prefs.pexelsProviderEnabled } returns flowOf(true)
            every { prefs.pixabayProviderEnabled } returns flowOf(true)
            every { prefs.communityProviderEnabled } returns flowOf(true)
            every { prefs.communityGuidelinesAccepted } returns flowOf(false)
            every { prefs.communityGuidelinesAcceptedVersion } returns flowOf(0)
            every { prefs.generatedContentProviderEnabled } returns flowOf(true)
            every { prefs.generatedContentDisclosureAccepted } returns flowOf(false)
            every { prefs.freesoundApiKey } returns flowOf("")
            every { prefs.schedulerCollectionId } returns flowOf(-1L)
            every { prefs.showSketchyContent } returns flowOf(false)
            every { prefs.showNsfwContent } returns flowOf(false)
            every { prefs.autoWallpaperRequiresCharging } returns flowOf(false)
            every { prefs.autoWallpaperRequiresWiFiOnly } returns flowOf(false)
            every { prefs.autoWallpaperRequiresIdle } returns flowOf(false)
            every { prefs.autoWallpaperDarkenPercent } returns flowOf(0)
            every { prefs.autoBackupEnabled } returns flowOf(false)
            every { prefs.autoBackupFolderUri } returns flowOf("")
            every { prefs.autoBackupIntervalHours } returns flowOf(24L)
            every { prefs.autoBackupKeepCount } returns flowOf(5)
            every { prefs.rotateOnUnlock } returns flowOf(false)
            every { prefs.rotateOnScreenOff } returns flowOf(false)
            every { prefs.avoidRecentRepeats } returns flowOf(false)
            every { prefs.alarmShuffleEnabled } returns flowOf(false)
            every { prefs.soundProfilesEnabled } returns flowOf(false)
            every { prefs.liveWallpaperDimEnabled } returns flowOf(false)
            every { prefs.soundProfilesJson } returns flowOf("")
            every { prefs.soundProfileLastAppliedId } returns flowOf("")
            // Added in v6.13/v6.14 — these StateFlow-backed VM fields fail-fast on
            // first collection if not stubbed. Pre-existing test fixture gap caught
            // during a deeper audit pass.
            every { prefs.adaptiveTintIntensity } returns flowOf(0.3f)
            every { prefs.darkModeWallpaperId } returns flowOf("") // Phase 6.2 dark slot
            every { prefs.lightModeWallpaperId } returns flowOf("") // Phase 6.2 light slot
            every { prefs.stabilityAiKey } returns flowOf("")       // Phase 3.1 AI
            every { prefs.reduceAnimations } returns flowOf(false) // Reduced-motion a11y
            every { prefs.ringtoneShuffleEnabled } returns flowOf(false)
            every { prefs.ringtoneShuffleIntervalHours } returns flowOf(24L)
        }

    private class FakeBackgroundWorkDiagnosticsReader(
        var snapshot: BackgroundWorkDiagnostics,
    ) : BackgroundWorkDiagnosticsReader {
        override suspend fun read(): BackgroundWorkDiagnostics = snapshot
    }

    private fun waitForIdle(
        timeoutMs: Long = 2000L,
        condition: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(25)
        }
        fail("Timed out waiting for background work to finish")
    }
}
