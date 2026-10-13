package com.freevibe.ui.screens.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.Daypart
import com.freevibe.service.parsePack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A wallpaper picked for the 24H pack is saved by the settings view model's scope, which outlives
 * the editor dialog, and into the pack as it is stored at that moment.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallpaperPackSlotSaveTest {

    private lateinit var context: Context
    private lateinit var prefs: PreferencesManager
    private lateinit var restore: suspend () -> Unit

    /** Stands in for viewModelScope. No editor scope exists in these tests, as after a dismiss. */
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before
    fun setUp(): Unit = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
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
    }

    @Test
    fun `a picked wallpaper is added to the pack that is stored, not to an older copy`() = runBlocking {
        // The editor's own copy can be an edit behind this by the time a file's name has been read.
        prefs.setWallpaperPackJson(
            editablePack("")
                .copy(name = "Mine")
                .withSlot(Daypart.MORNING, "content://picked/morning", "sunrise.jpg")
                .toEditorJson(),
        )
        prefs.setWallpaperPackLastAppliedDaypart("MORNING")

        delegate().setWallpaperPackSlot(Daypart.NIGHT, Uri.parse("content://picked/night"), "Wallpaper").join()

        val pack = parsePack(prefs.wallpaperPackJson.first())!!
        assertEquals("Mine", pack.name)
        assertEquals(listOf(Daypart.MORNING, Daypart.NIGHT), pack.slots.map { it.daypart })
        assertEquals("content://picked/night", pack.slotFor(Daypart.NIGHT)?.wallpaperUri)
        assertEquals("nothing gave the file a name, so the stand-in is used", "Wallpaper", pack.slotFor(Daypart.NIGHT)?.label)
        assertEquals("the change should show at the next run", "", prefs.wallpaperPackLastAppliedDaypart.first())
    }

    @Test
    fun `the wallpaper goes by its file name when the picker's provider gives one`() = runBlocking {
        Robolectric.setupContentProvider(NamedFiles::class.java, NAMED_AUTHORITY)

        delegate().setWallpaperPackSlot(Daypart.DAY, Uri.parse("content://$NAMED_AUTHORITY/1"), "Wallpaper").join()

        assertEquals("beach.jpg", parsePack(prefs.wallpaperPackJson.first())!!.slotFor(Daypart.DAY)?.label)
    }

    private fun delegate() = SettingsMediaDelegate(context, prefs, settingsScope)

    /** A document provider that answers the one question the editor asks: what is this file called. */
    class NamedFiles : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf<Any>("beach.jpg")) }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }

    private companion object {
        const val NAMED_AUTHORITY = "com.freevibe.test.named"
    }
}
