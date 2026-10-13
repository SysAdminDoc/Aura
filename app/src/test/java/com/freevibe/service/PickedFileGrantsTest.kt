package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.FavoriteEntity
import io.mockk.every
import io.mockk.mockk
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
 * Which read grants go back to Android when the 24H pack stops using a picked picture, and which
 * stay because something else in Aura opens the same file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PickedFileGrantsTest {

    private lateinit var context: Context
    private lateinit var db: FreeVibeDatabase
    private val prefs = mockk<PreferencesManager>()

    private var darkSlot = ""
    private var lightSlot = ""
    private var soundPools = ""

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FreeVibeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        every { prefs.darkModeWallpaperId } answers { flowOf(darkSlot) }
        every { prefs.lightModeWallpaperId } answers { flowOf(lightSlot) }
        every { prefs.lastNightVariantWallpaperLocator } returns flowOf("")
        every { prefs.soundShufflePoolsJson } answers { flowOf(soundPools) }
    }

    @After
    fun tearDown() = db.close()

    // -- what goes back --

    @Test
    fun `a picture the pack stopped using gives its grant back`() = runBlocking {
        grantRead(MORNING)
        grantRead(NIGHT)

        grants().releaseDroppedFromPack(
            before = pack(Daypart.MORNING to MORNING, Daypart.NIGHT to NIGHT),
            after = pack(Daypart.NIGHT to NIGHT),
        )

        assertEquals(setOf(NIGHT), held())
    }

    @Test
    fun `an emptied pack gives every picture's grant back`() = runBlocking {
        grantRead(MORNING)
        grantRead(NIGHT)

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to MORNING, Daypart.NIGHT to NIGHT), after = "")

        assertEquals(emptySet<String>(), held())
    }

    // -- what stays --

    @Test
    fun `a picture another time of day still uses keeps its grant`() = runBlocking {
        grantRead(MORNING)

        grants().releaseDroppedFromPack(
            before = pack(Daypart.MORNING to MORNING, Daypart.NIGHT to MORNING),
            after = pack(Daypart.NIGHT to MORNING),
        )

        assertEquals(setOf(MORNING), held())
    }

    @Test
    fun `a picture a saved download still opens keeps its grant`() = runBlocking {
        grantRead(MORNING)
        db.downloadDao().insert(DownloadEntity(id = "d1", source = "LOCAL", type = "WALLPAPER", localPath = MORNING))

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to MORNING), after = "")

        assertEquals(setOf(MORNING), held())
    }

    @Test
    fun `a picture a favorite still opens keeps its grant`() = runBlocking {
        grantRead(MORNING)
        db.favoriteDao().insert(
            FavoriteEntity(id = "f1", source = "LOCAL", type = "WALLPAPER", thumbnailUrl = "", fullUrl = MORNING),
        )

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to MORNING), after = "")

        assertEquals(setOf(MORNING), held())
    }

    @Test
    fun `a picture set as the day or night wallpaper keeps its grant`() = runBlocking {
        grantRead(MORNING)
        grantRead(NIGHT)
        darkSlot = "LOCAL|night-1|$NIGHT"
        lightSlot = "LOCAL|day-1|$MORNING"

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to MORNING, Daypart.NIGHT to NIGHT), after = "")

        assertEquals(setOf(MORNING, NIGHT), held())
    }

    @Test
    fun `a file a sound pool also opens keeps its grant`() = runBlocking {
        grantRead(MORNING)
        soundPools = """{"pools":[{"poolId":"p1","items":[{"itemId":"local:1","locator":"$MORNING"}]}]}"""

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to MORNING), after = "")

        assertEquals(setOf(MORNING), held())
    }

    @Test
    fun `a folder's grant is never given back by the pack`() = runBlocking {
        // A backup can name any address this install holds a grant for, a whole folder included.
        grantRead(FOLDER)

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to FOLDER), after = "")

        assertEquals(setOf(FOLDER), held())
    }

    @Test
    fun `a pack that can't be read gives nothing back`() = runBlocking {
        grantRead(MORNING)

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to MORNING), after = "not a pack")

        assertEquals(setOf(MORNING), held())
    }

    @Test
    fun `a picture that never had a grant of its own changes nothing`() = runBlocking {
        grantRead(NIGHT)

        grants().releaseDroppedFromPack(pack(Daypart.MORNING to MORNING), after = "")

        assertEquals(setOf(NIGHT), held())
    }

    // -- which addresses count --

    @Test
    fun `only picked files the new pack lacks count as dropped`() {
        val before = pack(
            Daypart.MORNING to MORNING,
            Daypart.DAY to "https://w.wallhaven.cc/full/ab/one.jpg",
            Daypart.EVENING to "file:///data/user/0/com.freevibe/files/ai_wallpapers/a.png",
            Daypart.NIGHT to NIGHT,
        )

        assertEquals(setOf(MORNING), droppedPackLocators(before, pack(Daypart.NIGHT to NIGHT)))
        assertEquals(setOf(MORNING, NIGHT), droppedPackLocators(before, ""))
        assertEquals(emptySet<String>(), droppedPackLocators(before, before))
        assertEquals(emptySet<String>(), droppedPackLocators("not a pack", ""))
        assertEquals(emptySet<String>(), droppedPackLocators("", before))
    }

    private fun grants() = PickedFileGrants(context, db, prefs)

    private fun pack(vararg slots: Pair<Daypart, String>): String = serializePack(
        WallpaperPack(
            id = "main",
            name = "Mine",
            slots = slots.map { (daypart, locator) -> DaypartSlot(daypart, locator) },
        ),
    )

    /** Stands in for a picture chosen with the document picker. */
    private fun grantRead(locator: String) =
        context.contentResolver.takePersistableUriPermission(Uri.parse(locator), Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun held(): Set<String> =
        context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()

    private companion object {
        const val MORNING = "content://com.android.providers.media.documents/document/image%3A101"
        const val NIGHT = "content://com.android.providers.media.documents/document/image%3A102"
        const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3APictures"
    }
}
