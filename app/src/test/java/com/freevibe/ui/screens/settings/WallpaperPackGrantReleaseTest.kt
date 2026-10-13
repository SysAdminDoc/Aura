package com.freevibe.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.Daypart
import com.freevibe.service.PickedFileGrants
import com.freevibe.service.parsePack
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Editing the 24H pack gives back the read access kept for a picture once the pack no longer uses
 * it: after a replace, after a time of day is cleared, and after the pack is deleted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperPackGrantReleaseTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var db: FreeVibeDatabase
    private lateinit var restore: suspend () -> Unit

    /** In these tests the pack is the only thing in Aura that points at the pictures. */
    private val nothingElse = mockk<PreferencesManager>()

    /** Stands in for viewModelScope. */
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(context, FreeVibeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        every { nothingElse.darkModeWallpaperId } returns flowOf("")
        every { nothingElse.lightModeWallpaperId } returns flowOf("")
        every { nothingElse.lastNightVariantWallpaperLocator } returns flowOf("")
        every { nothingElse.soundShufflePoolsJson } returns flowOf("")
        prefs = PreferencesManager(context)
        // DataStore outlives a single test in this process, so put back whatever this class changes.
        val enabled = prefs.wallpaperPackEnabled.first()
        val json = prefs.wallpaperPackJson.first()
        val lastDaypart = prefs.wallpaperPackLastAppliedDaypart.first()
        restore = {
            prefs.setWallpaperPackEnabled(enabled)
            prefs.setWallpaperPackJson(json)
            prefs.setWallpaperPackLastAppliedDaypart(lastDaypart)
        }
        prefs.setWallpaperPackEnabled(false)
        prefs.setWallpaperPackJson("")
        prefs.setWallpaperPackLastAppliedDaypart("")
    }

    @After
    fun tearDown(): Unit = runBlocking {
        settingsScope.cancel()
        restore()
        db.close()
    }

    @Test
    fun `replacing a picture gives the old one's grant back`() = runBlocking {
        grantRead(OLD)
        prefs.setWallpaperPackJson(editablePack("").withSlot(Daypart.MORNING, OLD, "old.jpg").toEditorJson())
        // The editor takes the new picture's grant before it hands the pick over.
        grantRead(NEW)

        delegate().setWallpaperPackSlot(Daypart.MORNING, Uri.parse(NEW), "Wallpaper").join()

        assertEquals(NEW, parsePack(prefs.wallpaperPackJson.first())!!.slotFor(Daypart.MORNING)?.wallpaperUri)
        assertEquals(setOf(NEW), held())
    }

    @Test
    fun `clearing a time of day gives its picture's grant back`() = runBlocking {
        grantRead(OLD)
        grantRead(NIGHT)
        val pack = editablePack("")
            .withSlot(Daypart.MORNING, OLD, "old.jpg")
            .withSlot(Daypart.NIGHT, NIGHT, "stars.jpg")
        prefs.setWallpaperPackJson(pack.toEditorJson())

        delegate().editWallpaperPack { it.withoutSlot(Daypart.MORNING) }.join()

        assertEquals(setOf(NIGHT), held())
    }

    @Test
    fun `deleting the pack gives every picture's grant back`() = runBlocking {
        grantRead(OLD)
        grantRead(NIGHT)
        val pack = editablePack("")
            .withSlot(Daypart.MORNING, OLD, "old.jpg")
            .withSlot(Daypart.NIGHT, NIGHT, "stars.jpg")
        prefs.setWallpaperPackJson(pack.toEditorJson())

        delegate().deleteWallpaperPack().join()

        assertEquals("", prefs.wallpaperPackJson.first())
        assertEquals(emptySet<String>(), held())
    }

    @Test
    fun `a picture still used for another time of day keeps its grant`() = runBlocking {
        grantRead(OLD)
        val pack = editablePack("")
            .withSlot(Daypart.MORNING, OLD, "old.jpg")
            .withSlot(Daypart.NIGHT, OLD, "old.jpg")
        prefs.setWallpaperPackJson(pack.toEditorJson())

        delegate().editWallpaperPack { it.withoutSlot(Daypart.MORNING) }.join()

        assertEquals(setOf(OLD), held())
    }

    @Test
    fun `renaming the pack or changing where a picture goes gives nothing back`() = runBlocking {
        grantRead(OLD)
        val pack = editablePack("").withSlot(Daypart.MORNING, OLD, "old.jpg")
        prefs.setWallpaperPackJson(pack.toEditorJson())

        delegate().editWallpaperPack { it.copy(name = "Weekdays") }.join()
        delegate().editWallpaperPack { it.withSlotTarget(Daypart.MORNING, "LOCK") }.join()

        val saved = parsePack(prefs.wallpaperPackJson.first())!!
        assertEquals("Weekdays", saved.name)
        assertEquals("LOCK", saved.slotFor(Daypart.MORNING)?.target)
        assertEquals(setOf(OLD), held())
    }

    @Test
    fun `a tap in an editor that hasn't caught up keeps the picture that was just picked`() = runBlocking {
        grantRead(OLD)
        prefs.setWallpaperPackJson(editablePack("").withSlot(Daypart.MORNING, OLD, "old.jpg").toEditorJson())
        grantRead(NEW)
        val settings = delegate()
        settings.setWallpaperPackSlot(Daypart.MORNING, Uri.parse(NEW), "Wallpaper").join()

        // The editor still shows the old picture when this tap comes in. The change is made on the
        // stored pack, so it can't bring the old picture back after its grant has gone.
        settings.editWallpaperPack { it.withSlotTarget(Daypart.MORNING, "LOCK") }.join()

        val slot = parsePack(prefs.wallpaperPackJson.first())!!.slotFor(Daypart.MORNING)!!
        assertEquals(NEW, slot.wallpaperUri)
        assertEquals("LOCK", slot.target)
        assertEquals(setOf(NEW), held())
    }

    private fun delegate() =
        SettingsMediaDelegate(context, prefs, settingsScope, PickedFileGrants(context, db, nothingElse))

    /** Stands in for a picture chosen with the document picker. */
    private fun grantRead(locator: String) =
        context.contentResolver.takePersistableUriPermission(Uri.parse(locator), Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun held(): Set<String> =
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()

    private companion object {
        const val OLD = "content://com.android.providers.media.documents/document/image%3A201"
        const val NEW = "content://com.android.providers.media.documents/document/image%3A202"
        const val NIGHT = "content://com.android.providers.media.documents/document/image%3A203"
    }
}
