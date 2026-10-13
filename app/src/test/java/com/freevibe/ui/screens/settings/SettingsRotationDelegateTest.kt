package com.freevibe.ui.screens.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.CollectionRepository
import com.freevibe.service.LocalWallpaperCatalog
import com.freevibe.service.NetworkPosture
import com.freevibe.service.RotationPrefetchCache
import com.freevibe.service.rotationPrefetchSlot
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.robolectric.annotation.Config
import java.io.File

/**
 * The wallpaper saved ahead belongs to the rotation setup it was picked for. Settings empties the
 * saved-ahead folder whenever that setup is turned off or pointed at something else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRotationDelegateTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var cache: RotationPrefetchCache
    private lateinit var delegate: SettingsRotationDelegate
    private lateinit var restore: suspend () -> Unit
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var clock = 1_000L
    private val slot = rotationPrefetchSlot("wallhaven", WallpaperTarget.BOTH)

    @Before
    fun setUp(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        prefs = PreferencesManager(context)
        // DataStore outlives a single test in this process, so put back whatever this class changes.
        val schedulerEnabled = prefs.schedulerEnabled.first()
        val schedulerSource = prefs.schedulerSource.first()
        val schedulerNightSource = prefs.schedulerNightSource.first()
        val schedulerCollection = prefs.schedulerCollectionId.first()
        val schedulerInterval = prefs.schedulerIntervalMinutes.first()
        val schedulerShuffle = prefs.schedulerShuffle.first()
        val autoEnabled = prefs.autoWallpaperEnabled.first()
        val autoSource = prefs.autoWallpaperSource.first()
        restore = {
            prefs.setSchedulerEnabled(schedulerEnabled)
            prefs.setSchedulerSource(schedulerSource)
            prefs.setSchedulerNightSource(schedulerNightSource)
            prefs.setSchedulerCollection(schedulerCollection)
            prefs.setSchedulerInterval(schedulerInterval)
            prefs.setSchedulerShuffle(schedulerShuffle)
            prefs.setAutoWallpaperEnabled(autoEnabled)
            prefs.setAutoWallpaperSource(autoSource)
        }
        // Off, so a setter that re-arms the schedule when rotation is on has nothing to re-arm.
        prefs.setSchedulerEnabled(false)
        prefs.setAutoWallpaperEnabled(false)
        prefs.setSchedulerSource("wallhaven")
        prefs.setSchedulerNightSource("")
        prefs.setSchedulerCollection(-1L)
        prefs.setAutoWallpaperSource("wallhaven")

        cache = RotationPrefetchCache(
            directory = File(temp.root, "prefetch"),
            posture = { NetworkPosture(metered = false, dataSaver = false) },
            moshi = Moshi.Builder().build(),
            now = { clock++ },
        )
        val collections = mockk<CollectionRepository>()
        every { collections.getAll() } returns flowOf(emptyList())
        val catalog = mockk<LocalWallpaperCatalog>()
        every { catalog.folders } returns flowOf(emptyList())
        every { catalog.items } returns flowOf(emptyList())
        coEvery { catalog.migrateLegacyFolder(any()) } returns null
        delegate = SettingsRotationDelegate(
            context = context,
            prefs = prefs,
            collectionRepo = collections,
            localWallpaperCatalog = catalog,
            rotationPrefetch = cache,
            scope = scope,
        )

        cache.store(slot, wallpaper("saved")) { file ->
            file.writeBytes(ByteArray(64))
            Result.success(64L)
        }
        assertEquals("saved", cache.peek(slot)?.wallpaper?.id)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        scope.cancel()
        restore()
    }

    @Test
    fun `turning the scheduler off empties what was saved ahead`(): Unit = runBlocking {
        delegate.setSchedulerEnabled(false).join()

        assertNothingSaved()
    }

    @Test
    fun `turning auto wallpaper off empties what was saved ahead`(): Unit = runBlocking {
        delegate.setAutoWallpaper(false).join()

        assertNothingSaved()
    }

    @Test
    fun `choosing another scheduler source empties what was saved ahead`(): Unit = runBlocking {
        delegate.setSchedulerSource("pixabay").join()

        assertEquals("pixabay", prefs.schedulerSource.first())
        assertNothingSaved()
    }

    @Test
    fun `choosing a night source empties what was saved ahead`(): Unit = runBlocking {
        delegate.setSchedulerSource(SchedulerSourceTarget.NIGHT, "bing").join()

        assertEquals("bing", prefs.schedulerNightSource.first())
        assertNothingSaved()
    }

    @Test
    fun `choosing another collection empties what was saved ahead`(): Unit = runBlocking {
        delegate.setSchedulerCollection(8L).join()

        assertEquals(8L, prefs.schedulerCollectionId.first())
        assertEquals("collection", prefs.schedulerSource.first())
        assertNothingSaved()
    }

    @Test
    fun `choosing another auto wallpaper source empties what was saved ahead`(): Unit = runBlocking {
        delegate.setAutoWpSource("bing").join()

        assertEquals("bing", prefs.autoWallpaperSource.first())
        assertNothingSaved()
    }

    @Test
    fun `a setting that leaves the source alone keeps what was saved ahead`(): Unit = runBlocking {
        delegate.setSchedulerInterval(120L).join()
        delegate.setSchedulerShuffle(false).join()

        assertEquals(120L, prefs.schedulerIntervalMinutes.first())
        assertEquals("saved", cache.peek(slot)?.wallpaper?.id)
        assertEquals(64L, cache.peek(slot)?.file?.length())
    }

    private suspend fun assertNothingSaved() {
        assertNull(cache.peek(slot))
        assertEquals(0, cache.diagnostics().savedCount)
        assertEquals(listOf("index.json"), File(temp.root, "prefetch").list()!!.toList())
    }

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.WALLHAVEN,
        thumbnailUrl = "https://example.test/$id-thumb.jpg",
        fullUrl = "https://example.test/$id.jpg",
        width = 1080,
        height = 2400,
    )
}
