package com.freevibe.service

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.local.PreferencesManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemePackImportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var prefs: PreferencesManager
    private val history = mockk<WallpaperHistoryManager> {
        every { getRecent(any()) } returns flowOf(emptyList())
    }
    private val assetEntry = "assets/wallpaper-pack-morning-abc.jpg"

    @Before
    fun seedExistingSettings() = runTest {
        prefs = PreferencesManager(context)
        prefs.setWallpaperPackJson(
            serializePack(WallpaperPack(id = "old", name = "Old", slots = listOf(DaypartSlot(Daypart.DAY, "https://example.com/old.jpg")))),
        )
        prefs.setSoundProfilesJson(serializeProfiles(listOf(SoundProfile(id = "old", name = "Old"))))
        prefs.setLastAppliedRingtoneUri("content://media/internal/audio/media/1")
        prefs.setLastAppliedNotificationUri("")
        prefs.setLastAppliedAlarmUri("")
        context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE).edit()
            .putString("video_path", "/old/video.mp4").remove("scale_mode").commit()
        context.getSharedPreferences("freevibe_widget", Context.MODE_PRIVATE).edit()
            .putInt("tint_vibrant", 11).remove("tint_accent").remove("tint_dominant").putInt("shuffle_count", 2).commit()
        File(context.filesDir, "theme_packs").deleteRecursively()
    }

    @Test
    fun `packs that fail validation leave settings and files exactly as they were`() = runTest {
        val manager = ThemePackRecipeManager(context, prefs, history)
        val before = snapshot()
        val fixtures = mapOf(
            "bad final recipe" to validRecipe().copy(soundProfilesJson = "[{\"id\": "),
            "missing asset" to validRecipe().copy(
                videoWallpaper = ThemePackMediaReference(
                    role = "video_wallpaper",
                    label = "Video wallpaper",
                    locator = "/storage/emulated/0/Movies/loop.mp4",
                    assetKey = "assets/not-in-the-archive.mp4",
                ),
            ),
            "duplicate slot" to validRecipe().copy(
                wallpaperPackJson = serializePack(
                    WallpaperPack(
                        id = "dup",
                        name = "Dup",
                        slots = listOf(
                            DaypartSlot(Daypart.NIGHT, "content://media/external/images/media/31"),
                            DaypartSlot(Daypart.NIGHT, "content://media/external/images/media/32"),
                        ),
                    ),
                ),
            ),
            "slot at a web address off Aura's sources" to validRecipe().copy(
                wallpaperPackJson = serializePack(
                    WallpaperPack(
                        id = "hostile",
                        name = "Hostile",
                        slots = listOf(
                            DaypartSlot(Daypart.MORNING, "content://media/external/images/media/5"),
                            DaypartSlot(Daypart.NIGHT, "https://tracker.example/pixel.jpg"),
                        ),
                    ),
                ),
            ),
            "profile sound at a web address" to validRecipe().copy(
                soundProfilesJson = serializeProfiles(
                    listOf(SoundProfile(id = "night", name = "Night", ringtoneUri = "https://tracker.example/ring.mp3")),
                ),
            ),
            "invalid locator" to validRecipe().copy(sounds = ThemePackSoundState(alarmUri = "intent://alarm#Intent;end")),
        )

        fixtures.forEach { (name, recipe) ->
            val result = manager.importThemePack(writePack(recipe))

            assertTrue("$name should fail", result.exceptionOrNull() is ThemePackValidationException)
            assertEquals(name, before, snapshot())
            assertEquals("$name left an import directory behind", emptyList<String>(), importDirs())
        }
    }

    @Test
    fun `a valid pack imports whole and its files stay on disk`() = runTest {
        val manager = ThemePackRecipeManager(context, prefs, history)

        val report = manager.importThemePack(writePack(validRecipe())).getOrThrow()

        val dirs = importDirs()
        assertEquals(1, dirs.size)
        val slot = parsePack(prefs.wallpaperPackJson.first())!!.slots.single { it.daypart == Daypart.MORNING }
        assertTrue(slot.wallpaperUri, slot.wallpaperUri.contains(dirs.single()))
        assertTrue(File(slot.wallpaperUri).readBytes().contentEquals(ASSET_BYTES))
        assertEquals("content://media/external/audio/media/9", prefs.lastAppliedNotificationUri.first())
        assertEquals("night", parseProfiles(prefs.soundProfilesJson.first()).single().id)
        val widget = context.getSharedPreferences("freevibe_widget", Context.MODE_PRIVATE)
        assertEquals(0xFF102030.toInt(), widget.getInt("tint_vibrant", 0))
        assertEquals(4, report.importedItemCount)
    }

    @Test
    fun `a settings write that fails puts the widget back and removes the import`() = runTest {
        val failingPrefs = spyk(prefs)
        coEvery {
            failingPrefs.applyThemePackImport(any(), any(), any(), any(), any())
        } throws IOException("disk full")
        val manager = ThemePackRecipeManager(context, failingPrefs, history)
        val before = snapshot()

        val result = manager.importThemePack(writePack(validRecipe()))

        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals(before, snapshot())
        assertEquals(emptyList<String>(), importDirs())
    }

    private suspend fun snapshot(): Map<String, Any?> {
        val video = context.getSharedPreferences("freevibe_live_wp", Context.MODE_PRIVATE).all
        val widget = context.getSharedPreferences("freevibe_widget", Context.MODE_PRIVATE).all
        return mapOf(
            "pack" to prefs.wallpaperPackJson.first(),
            "profiles" to prefs.soundProfilesJson.first(),
            "ringtone" to prefs.lastAppliedRingtoneUri.first(),
            "notification" to prefs.lastAppliedNotificationUri.first(),
            "alarm" to prefs.lastAppliedAlarmUri.first(),
            "video_path" to video["video_path"],
            "scale_mode" to video["scale_mode"],
            "tint_vibrant" to widget["tint_vibrant"],
            "shuffle_count" to widget["shuffle_count"],
        )
    }

    private fun importDirs(): List<String> =
        File(context.filesDir, "theme_packs")
            .listFiles { file -> file.isDirectory && file.name.startsWith("import-") }
            .orEmpty()
            .map { it.name }

    private fun writePack(recipe: ThemePackRecipe): Uri {
        val file = File.createTempFile("theme-pack", ".zip", context.cacheDir)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(assetEntry))
            zip.write(ASSET_BYTES)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("theme-pack.json"))
            zip.write(serializeThemePackRecipe(recipe).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return Uri.fromFile(file)
    }

    private fun validRecipe() = ThemePackRecipe(
        id = "pack-1",
        name = "Night desk",
        media = listOf(
            ThemePackMediaReference(
                role = "wallpaper_pack_morning",
                label = "Morning wallpaper",
                locator = "content://media/external/images/media/5",
                assetKey = assetEntry,
            ),
        ),
        sounds = ThemePackSoundState(notificationUri = "content://media/external/audio/media/9"),
        widget = ThemePackWidgetState(primaryTint = 0xFF102030.toInt(), shuffleCount = 1),
        wallpaperPackJson = serializePack(
            WallpaperPack(
                id = "dayparts",
                name = "Dayparts",
                slots = listOf(DaypartSlot(Daypart.MORNING, "content://media/external/images/media/5")),
            ),
        ),
        soundProfilesJson = serializeProfiles(listOf(SoundProfile(id = "night", name = "Night", startHour = 22, endHour = 6))),
    )

    private companion object {
        val ASSET_BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 4)
    }
}
