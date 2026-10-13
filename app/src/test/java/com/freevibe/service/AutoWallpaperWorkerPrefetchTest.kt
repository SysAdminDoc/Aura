package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.RotationCandidateSet
import com.freevibe.data.repository.RotationExclusionRepository
import com.freevibe.data.repository.WallpaperRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Runs the real worker against a remote source, with the source, the apply and the network faked. */
@RunWith(RobolectricTestRunner::class)
class AutoWallpaperWorkerPrefetchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var cache: RotationPrefetchCache
    private lateinit var restore: suspend () -> Unit
    private var posture = NetworkPosture(metered = false, dataSaver = false)
    private var clock = 1_000L

    private val first = wallpaper("first")
    private val second = wallpaper("second")
    private val third = wallpaper("third")
    private val slot = rotationPrefetchSlot("wallhaven", WallpaperTarget.BOTH)

    private val repo = mockk<WallpaperRepository>()
    private val applier = mockk<WallpaperApplier>()
    private val exclusions = mockk<RotationExclusionRepository>()
    private val history = mockk<WallpaperHistoryManager>(relaxed = true)

    @Before
    fun setUp(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        prefs = PreferencesManager(context)
        // DataStore outlives a single test in this process, so put back whatever this class changes.
        val enabled = prefs.schedulerEnabled.first()
        val source = prefs.schedulerSource.first()
        val shuffle = prefs.schedulerShuffle.first()
        val home = prefs.schedulerHomeEnabled.first()
        val lock = prefs.schedulerLockEnabled.first()
        val avoid = prefs.avoidRecentRepeats.first()
        val wallhaven = prefs.wallhavenProviderEnabled.first()
        restore = {
            prefs.setSchedulerEnabled(enabled)
            prefs.setSchedulerSource(source)
            prefs.setSchedulerShuffle(shuffle)
            prefs.setSchedulerHome(home)
            prefs.setSchedulerLock(lock)
            prefs.setAvoidRecentRepeats(avoid)
            prefs.setWallhavenProviderEnabled(wallhaven)
            prefs.clearRecentRotationIds()
        }
        prefs.setSchedulerEnabled(true)
        prefs.setSchedulerSource("wallhaven")
        // In order, and never the same one twice, so each test knows which wallpaper comes next.
        prefs.setSchedulerShuffle(false)
        prefs.setAvoidRecentRepeats(true)
        prefs.clearRecentRotationIds()
        prefs.setSchedulerHome(true)
        prefs.setSchedulerLock(true)
        prefs.setWallhavenProviderEnabled(true)

        cache = RotationPrefetchCache(
            directory = File(temp.root, "prefetch"),
            posture = { posture },
            moshi = Moshi.Builder().build(),
            now = { clock++ },
        )
        coEvery { repo.getWallhaven(any(), any(), any(), any()) } returns
            SearchResult(listOf(first, second, third), totalCount = 3, currentPage = 1, hasMore = false)
        coEvery { exclusions.filter(any()) } answers { RotationCandidateSet(firstArg(), 0, false) }
        every { history.getRecent(any()) } returns flowOf(emptyList())
        coEvery {
            applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
        coEvery { applier.downloadToFile(any(), any()) } coAnswers {
            secondArg<File>().writeBytes(ByteArray(64))
            Result.success(64L)
        }
    }

    @After
    fun tearDown(): Unit = runBlocking { restore() }

    @Test
    fun `a rotation with nothing saved fetches as before and then saves the next wallpaper`() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 1) {
            applier.applyByLocator(first.fullUrl, WallpaperTarget.BOTH, any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 1) { applier.downloadToFile(second.fullUrl, any()) }
        assertEquals(second, cache.peek(slot)?.wallpaper)
        val diagnostics = cache.diagnostics()
        assertEquals(0, diagnostics.hits)
        assertEquals(1, diagnostics.misses)
        assertEquals(RotationPrefetchOutcome.STORED, diagnostics.lastOutcome)
    }

    @Test
    fun `the saved wallpaper applies from its file when the source can't be reached`() = runBlocking {
        runWorker()
        val savedFile = cache.peek(slot)!!.file
        coEvery { repo.getWallhaven(any(), any(), any(), any()) } throws java.io.IOException("offline")

        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 1) {
            applier.applyByLocator(
                savedFile.absolutePath, WallpaperTarget.BOTH, any(), any(), any(), any(), any(), any(), any(), any(),
            )
        }
        coVerify(exactly = 1) { history.record(second, WallpaperTarget.BOTH) }
        assertEquals(1, cache.diagnostics().hits)
        assertNull("a used wallpaper is not offered twice", cache.peek(slot))
    }

    @Test
    fun `after a saved wallpaper is used the cache refills from the source`() = runBlocking {
        runWorker()

        runWorker()

        coVerify(exactly = 1) { applier.downloadToFile(third.fullUrl, any()) }
        assertEquals(third, cache.peek(slot)?.wallpaper)
    }

    @Test
    fun `a metered connection rotates as usual and saves nothing ahead`() = runBlocking {
        posture = NetworkPosture(metered = true, dataSaver = false)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 1) {
            applier.applyByLocator(first.fullUrl, WallpaperTarget.BOTH, any(), any(), any(), any(), any(), any(), any(), any())
        }
        coVerify(exactly = 0) { applier.downloadToFile(any(), any()) }
        assertNull(cache.peek(slot))
        assertEquals(RotationPrefetchOutcome.DEFERRED_METERED, cache.diagnostics().lastOutcome)
    }

    @Test
    fun `a saved wallpaper that was excluded since is dropped and the rotation fetches`() = runBlocking {
        runWorker()
        coEvery { exclusions.filter(listOf(second)) } returns RotationCandidateSet(emptyList(), 1, true)
        coEvery { exclusions.filter(listOf(first, second, third)) } returns
            RotationCandidateSet(listOf(first, third), 1, false)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 1) {
            applier.applyByLocator(third.fullUrl, WallpaperTarget.BOTH, any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertEquals(0, cache.diagnostics().hits)
        assertEquals(2, cache.diagnostics().misses)
    }

    @Test
    fun `a saved file that won't apply falls back to a fetch in the same run`() = runBlocking {
        runWorker()
        val savedFile = cache.peek(slot)!!.file
        coEvery {
            applier.applyByLocator(
                savedFile.absolutePath, any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        } returns Result.failure(java.io.IOException("corrupt"))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        coVerify(exactly = 1) {
            applier.applyByLocator(second.fullUrl, WallpaperTarget.BOTH, any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertEquals(0, cache.diagnostics().hits)
    }

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<AutoWallpaperWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = AutoWallpaperWorker(
                        appContext,
                        workerParameters,
                        wallpaperRepo = repo,
                        redditRepo = mockk(relaxed = true),
                        favoritesRepo = mockk(relaxed = true),
                        collectionRepo = mockk(relaxed = true),
                        rotationExclusions = exclusions,
                        wallpaperApplier = applier,
                        historyManager = history,
                        prefs = prefs,
                        localWallpaperCatalog = mockk(relaxed = true),
                        receiptStore = mockk(relaxed = true),
                        prefetch = cache,
                    )
                },
            )
            .build()
            .doWork()

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.WALLHAVEN,
        thumbnailUrl = "https://example.test/$id-thumb.jpg",
        fullUrl = "https://example.test/$id.jpg",
        width = 1080,
        height = 2400,
    )
}
