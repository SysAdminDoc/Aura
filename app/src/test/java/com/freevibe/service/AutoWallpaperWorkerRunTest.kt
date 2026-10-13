package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.RotationCandidateSet
import com.freevibe.data.repository.RotationExclusionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs the real worker against local folders, with the apply and the exclusion list faked. */
@RunWith(RobolectricTestRunner::class)
class AutoWallpaperWorkerRunTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private val home = wallpaper("home")
    private val lock = wallpaper("lock")
    private val applier = mockk<WallpaperApplier>()
    private val exclusions = mockk<RotationExclusionRepository>()
    private val catalog = mockk<LocalWallpaperCatalog>()
    private val history = mockk<WallpaperHistoryManager>(relaxed = true)

    @Before
    fun setUp(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        prefs = PreferencesManager(context)
        prefs.setSchedulerEnabled(true)
        prefs.setSchedulerSource(WALLPAPER_SOURCE_LOCAL_FOLDER)
        // DataStore outlives a single test in this process, so every test states both screens.
        prefs.setSchedulerHome(true)
        prefs.setSchedulerLock(true)
        coEvery { catalog.migrateLegacyFolder(any()) } returns null
        coEvery { catalog.rotationWallpapers(WallpaperTarget.HOME) } returns listOf(home)
        coEvery { catalog.rotationWallpapers(WallpaperTarget.LOCK) } returns listOf(lock)
        coEvery {
            applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.success(Unit)
    }

    @Test
    fun `a tapped rotation that changed home restarts the countdown even when every lock item is excluded`() = runBlocking {
        coEvery { exclusions.filter(listOf(home)) } returns RotationCandidateSet(listOf(home), 0, false)
        coEvery { exclusions.filter(listOf(lock)) } returns RotationCandidateSet(emptyList(), 1, true)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) {
            applier.applyByLocator(home.fullUrl, WallpaperTarget.HOME, any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertEquals("the countdown restarts from this change", 1, scheduledRotation().size)
    }

    @Test
    fun `a tapped rotation that changed nothing leaves the countdown alone`() = runBlocking {
        coEvery { exclusions.filter(any()) } returns RotationCandidateSet(emptyList(), 1, true)

        val result = runWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) {
            applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertTrue(scheduledRotation().isEmpty())
    }

    @Test
    fun `a run waits while another rotation holds the run lock`() = runBlocking {
        coEvery { exclusions.filter(any()) } answers { RotationCandidateSet(firstArg(), 0, false) }
        AutoWallpaperWorker.rotationRunLock.lock()
        val run = async(Dispatchers.Default) { runWorker() }
        try {
            delay(300)
            assertFalse("the run must not start while another rotation is applying", run.isCompleted)
            coVerify(exactly = 0) {
                applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
            }
        } finally {
            AutoWallpaperWorker.rotationRunLock.unlock()
        }

        assertEquals(ListenableWorker.Result.success(), withTimeout(10_000) { run.await() })
        coVerify(exactly = 2) {
            applier.applyByLocator(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `a run replaced while applying home still applies lock before it stops`() = runBlocking {
        coEvery { exclusions.filter(any()) } answers { RotationCandidateSet(firstArg(), 0, false) }
        val homeStarted = CompletableDeferred<Unit>()
        val releaseHome = CompletableDeferred<Unit>()
        coEvery {
            applier.applyByLocator(home.fullUrl, WallpaperTarget.HOME, any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            homeStarted.complete(Unit)
            releaseHome.await()
            Result.success(Unit)
        }
        val run = launch(Dispatchers.Default) { runWorker() }
        withTimeout(10_000) { homeStarted.await() }

        run.cancel()
        releaseHome.complete(Unit)
        withTimeout(10_000) { run.join() }

        coVerify(exactly = 1) {
            applier.applyByLocator(lock.fullUrl, WallpaperTarget.LOCK, any(), any(), any(), any(), any(), any(), any(), any())
        }
        assertFalse("the lock is free once the replaced run is gone", AutoWallpaperWorker.rotationRunLock.isLocked)
    }

    @Test
    fun `a single-screen run replaced while applying still records what it applied`() = runBlocking {
        prefs.setSchedulerLock(false)
        coEvery { exclusions.filter(any()) } answers { RotationCandidateSet(firstArg(), 0, false) }
        val homeStarted = CompletableDeferred<Unit>()
        val releaseHome = CompletableDeferred<Unit>()
        coEvery {
            applier.applyByLocator(home.fullUrl, WallpaperTarget.HOME, any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            homeStarted.complete(Unit)
            releaseHome.await()
            Result.success(Unit)
        }
        val run = launch(Dispatchers.Default) { runWorker() }
        withTimeout(10_000) { homeStarted.await() }

        run.cancel()
        releaseHome.complete(Unit)
        withTimeout(10_000) { run.join() }

        coVerify(exactly = 1) { history.record(home, WallpaperTarget.HOME) }
        assertEquals("the countdown restarts for the change it made", 1, scheduledRotation().size)
        assertFalse(AutoWallpaperWorker.rotationRunLock.isLocked)
    }

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<AutoWallpaperWorker>(context)
            .setInputData(workDataOf(AutoWallpaperWorker.RESTART_COUNTDOWN_KEY to true))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = AutoWallpaperWorker(
                        appContext,
                        workerParameters,
                        wallpaperRepo = mockk(relaxed = true),
                        redditRepo = mockk(relaxed = true),
                        favoritesRepo = mockk(relaxed = true),
                        collectionRepo = mockk(relaxed = true),
                        rotationExclusions = exclusions,
                        wallpaperApplier = applier,
                        historyManager = history,
                        prefs = prefs,
                        localWallpaperCatalog = catalog,
                        receiptStore = mockk(relaxed = true),
                        // Local folders never use the cache, so these runs must not touch it.
                        prefetch = mockk(),
                    )
                },
            )
            .build()
            .doWork()

    private fun scheduledRotation(): List<WorkInfo> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AutoWallpaperWorker.WORK_NAME)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.LOCAL,
        thumbnailUrl = "content://local/$id",
        fullUrl = "content://local/$id",
        width = 1080,
        height = 2400,
    )
}
