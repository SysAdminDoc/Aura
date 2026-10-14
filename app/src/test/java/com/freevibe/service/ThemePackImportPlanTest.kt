package com.freevibe.service

import com.freevibe.data.model.ContentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePackImportPlanTest {

    private val morningAsset = "assets/wallpaper-pack-morning-abc.jpg"
    private val ringtoneAsset = "assets/sound-ringtone-def.mp3"
    private val videoAsset = "assets/video-wallpaper-123.mp4"

    /** A web address the pack worker may fetch by itself: https, on a provider's own host. */
    private val providerPicture = "https://w.wallhaven.cc/full/ab/wallhaven-night.jpg"
    private val assets = mapOf(
        morningAsset to "/data/user/0/com.freevibe/files/theme_packs/import-1/a_morning.jpg",
        ringtoneAsset to "/data/user/0/com.freevibe/files/theme_packs/import-1/b_ringtone.mp3",
        videoAsset to "/data/user/0/com.freevibe/files/theme_packs/import-1/c_loop.mp4",
    )

    @Test
    fun `a valid pack plans every setting with embedded files pointed at the import`() {
        val plan = planThemePackImport(validRecipe(), assets)

        val pack = parsePack(plan.wallpaperPackJson!!)!!
        assertEquals(assets[morningAsset], pack.slots.first { it.daypart == Daypart.MORNING }.wallpaperUri)
        assertEquals(providerPicture, pack.slots.first { it.daypart == Daypart.NIGHT }.wallpaperUri)
        assertEquals(assets[ringtoneAsset], parseProfiles(plan.soundProfilesJson!!).single().ringtoneUri)
        assertEquals(assets[ringtoneAsset], plan.ringtoneUri)
        assertEquals("content://media/internal/audio/media/7", plan.notificationUri)
        assertNull(plan.alarmUri)
        assertEquals(assets[videoAsset], plan.videoPath)
        assertEquals(0, plan.widget!!.shuffleCount)
        assertEquals(6, plan.importedItemCount)
    }

    @Test
    fun `a bad sound profile recipe at the end rejects the whole pack`() {
        assertRejected(validRecipe().copy(soundProfilesJson = "{\"not\": \"a list\""), "sound profiles")
    }

    @Test
    fun `a reference to a file the archive does not hold rejects the pack`() {
        assertRejected(planFor(validRecipe(), assets - videoAsset), "missing the file")
    }

    @Test
    fun `two slots for the same daypart reject the pack`() {
        val recipe = validRecipe().copy(
            wallpaperPackJson = serializePack(
                WallpaperPack(
                    id = "dayparts",
                    name = "Dayparts",
                    slots = listOf(
                        DaypartSlot(Daypart.MORNING, "content://media/external/images/media/31"),
                        DaypartSlot(Daypart.MORNING, "content://media/external/images/media/32"),
                    ),
                ),
            ),
        )

        assertRejected(recipe, "two Morning slots")
    }

    @Test
    fun `locations Aura cannot open reject the pack wherever they appear`() {
        assertRejected(validRecipe().copy(sounds = ThemePackSoundState(ringtoneUri = "javascript:alert(1)")), "ringtone")
        assertRejected(
            validRecipe().copy(
                wallpaperPackJson = serializePack(
                    WallpaperPack(id = "p", name = "P", slots = listOf(DaypartSlot(Daypart.DAY, "../../shared_prefs/x.xml"))),
                ),
            ),
            "Day wallpaper",
        )
        assertRejected(
            validRecipe().copy(
                soundProfilesJson = serializeProfiles(
                    listOf(SoundProfile(id = "night", name = "Night", alarmUri = "intent://scan#Intent;end")),
                ),
            ),
            "Night sound",
        )
        assertRejected(
            validRecipe().copy(
                media = validRecipe().media + ThemePackMediaReference(
                    role = "current_wallpaper",
                    label = "Current wallpaper",
                    locator = "file://host/share/wall.jpg",
                ),
            ),
            "Current wallpaper",
        )
    }

    @Test
    fun `duplicate roles and profile ids, bad hours, targets and versions reject the pack`() {
        val recipe = validRecipe()
        assertRejected(recipe.copy(media = recipe.media + recipe.media.first()), "twice")
        assertRejected(
            recipe.copy(
                soundProfilesJson = serializeProfiles(
                    listOf(SoundProfile(id = "a", name = "A"), SoundProfile(id = "a", name = "B")),
                ),
            ),
            "twice",
        )
        assertRejected(
            recipe.copy(soundProfilesJson = serializeProfiles(listOf(SoundProfile(id = "a", name = "A", endHour = 25)))),
            "hours",
        )
        assertRejected(
            recipe.copy(wallpaperPackJson = serializePack(WallpaperPack(id = "p", name = "P", target = "CEILING"))),
            "unknown target",
        )
        assertRejected(recipe.copy(version = THEME_PACK_VERSION + 1), "not supported yet")
        assertRejected(recipe.copy(version = 0), "not valid")
    }

    @Test
    fun `locator rules accept what Aura stores and refuse the rest`() {
        listOf(
            "https://example.com/wall.jpg",
            "http://example.com/wall.jpg",
            "content://media/external/images/media/12",
            "android.resource://com.freevibe/raw/chime",
            "rawresource://bundled/chime",
            "file:///storage/emulated/0/Pictures/a.jpg",
            "/data/user/0/com.freevibe/files/theme_packs/import-1/a.jpg",
            "/storage/emulated/0/Ringtones/odd:name.mp3",
        ).forEach { assertTrue(it, isAcceptableThemePackLocator(it)) }

        listOf(
            "",
            " https://example.com/a.jpg",
            "relative/path.jpg",
            "/data/../data/user/0/x",
            "file:///sdcard/../data/x",
            "file://host/x",
            "javascript:alert(1)",
            "data:image/png;base64,AAAA",
            "https:",
            "content://media/\u0000x",
        ).forEach { assertFalse(it, isAcceptableThemePackLocator(it)) }
    }

    @Test
    fun `an AI wallpaper saved with a single-slash file locator imports`() {
        // AiWallpaperRepository stores `File.toURI().toString()`, which is `file:/data/...`.
        val generated = "file:/data/user/0/com.freevibe/files/ai_wallpapers/generated-1.png"
        val recipe = validRecipe().copy(
            media = validRecipe().media + ThemePackMediaReference(
                role = "current_wallpaper",
                label = "Current wallpaper",
                locator = generated,
            ),
        )

        val plan = planThemePackImport(recipe, assets)

        assertEquals(assets[videoAsset], plan.videoPath)
        assertTrue(isAcceptableThemePackLocator(generated))
        listOf("file:/", "file:/data/../etc/x", "FILE://host/x").forEach { assertFalse(it, isAcceptableThemePackLocator(it)) }
    }

    @Test
    fun `a pack slot at a web address off Aura's sources rejects the pack`() {
        listOf(
            "https://tracker.example/pixel.jpg",
            "https://w.wallhaven.cc.tracker.example/night.jpg",
            "https://evilredd.it/night.jpg",
            "http://w.wallhaven.cc/full/ab/wallhaven-night.jpg",
            "HTTPS://tracker.example/pixel.jpg",
        ).forEach { hostile ->
            assertRejected(withNightSlot(hostile), "web address for Night wallpaper")
        }
    }

    @Test
    fun `a pack slot on a provider's own host is taken as it is`() {
        listOf(providerPicture, "https://i.redd.it/abc123.jpg").forEach { picture ->
            val plan = planThemePackImport(withNightSlot(picture), assets)

            assertEquals(picture, parsePack(plan.wallpaperPackJson!!)!!.slots.single().wallpaperUri)
        }
    }

    @Test
    fun `a pack slot on a provider this build can't use rejects the pack`() {
        val recipe = withNightSlot(providerPicture)

        val error = runCatching {
            planThemePackImport(recipe, assets) { it != ContentSource.WALLHAVEN }
        }.exceptionOrNull()

        assertTrue("$error", error is ThemePackValidationException)
        // The same pack is fine where Wallhaven can be used, so the provider was what decided it.
        assertEquals(providerPicture, parsePack(planThemePackImport(recipe, assets) { true }.wallpaperPackJson!!)!!.slots.single().wallpaperUri)
    }

    @Test
    fun `a slot the pack carries a file for is checked as that file, not as the address it had`() {
        // The address the picture had on the phone that made the pack. The import stores the file.
        val was = "https://tracker.example/was-here.jpg"
        val recipe = validRecipe().copy(
            media = validRecipe().media.map { if (it.assetKey == morningAsset) it.copy(locator = was) else it },
            wallpaperPackJson = serializePack(
                WallpaperPack(id = "dayparts", name = "Dayparts", slots = listOf(DaypartSlot(Daypart.MORNING, was))),
            ),
        )

        val plan = planThemePackImport(recipe, assets)

        assertEquals(assets[morningAsset], parsePack(plan.wallpaperPackJson!!)!!.slots.single().wallpaperUri)
    }

    @Test
    fun `a profile sound at any web address rejects the pack`() {
        listOf(
            "https://tracker.example/ring.mp3",
            "http://tracker.example/ring.mp3",
            // A provider's host too: the system plays a profile sound, and it would go online to do it.
            "https://cdn.pixabay.com/audio/ring.mp3",
        ).forEach { hostile ->
            listOf(
                SoundProfile(id = "night", name = "Night", ringtoneUri = hostile),
                SoundProfile(id = "night", name = "Night", notificationUri = hostile),
                SoundProfile(id = "night", name = "Night", alarmUri = hostile),
            ).forEach { profile ->
                assertRejected(
                    validRecipe().copy(soundProfilesJson = serializeProfiles(listOf(profile))),
                    "web address for Night sound",
                )
            }
        }
    }

    @Test
    fun `a profile sound on the phone or in the pack is taken`() {
        val recipe = validRecipe().copy(
            soundProfilesJson = serializeProfiles(
                listOf(
                    SoundProfile(
                        id = "night",
                        name = "Night",
                        // The pack carries a file for this one.
                        ringtoneUri = "content://media/external/audio/media/2",
                        notificationUri = "content://media/internal/audio/media/7",
                        alarmUri = "/storage/emulated/0/Alarms/soft.ogg",
                    ),
                ),
            ),
        )

        val profile = parseProfiles(planThemePackImport(recipe, assets).soundProfilesJson!!).single()

        assertEquals(assets[ringtoneAsset], profile.ringtoneUri)
        assertEquals("content://media/internal/audio/media/7", profile.notificationUri)
        assertEquals("/storage/emulated/0/Alarms/soft.ogg", profile.alarmUri)
    }

    private fun withNightSlot(locator: String): ThemePackRecipe = validRecipe().copy(
        wallpaperPackJson = serializePack(
            WallpaperPack(id = "dayparts", name = "Dayparts", slots = listOf(DaypartSlot(Daypart.NIGHT, locator))),
        ),
    )

    private fun planFor(recipe: ThemePackRecipe, assetsByKey: Map<String, String>): () -> ThemePackImportPlan =
        { planThemePackImport(recipe, assetsByKey) }

    private fun assertRejected(recipe: ThemePackRecipe, messagePart: String) =
        assertRejected(planFor(recipe, assets), messagePart)

    private fun assertRejected(plan: () -> ThemePackImportPlan, messagePart: String) {
        val error = runCatching { plan() }.exceptionOrNull()
        assertTrue("expected a validation error, got $error", error is ThemePackValidationException)
        assertTrue("${error?.message} should mention $messagePart", error!!.message!!.contains(messagePart))
    }

    private fun validRecipe() = ThemePackRecipe(
        id = "pack-1",
        name = "Desk",
        media = listOf(
            ThemePackMediaReference(
                role = "wallpaper_pack_morning",
                label = "Morning wallpaper",
                locator = "content://media/external/images/media/1",
                assetKey = morningAsset,
            ),
            ThemePackMediaReference(
                role = "sound_ringtone",
                label = "Ringtone",
                locator = "content://media/external/audio/media/2",
                assetKey = ringtoneAsset,
            ),
        ),
        videoWallpaper = ThemePackMediaReference(
            role = "video_wallpaper",
            label = "Video wallpaper",
            locator = "/storage/emulated/0/Movies/loop.mp4",
            assetKey = videoAsset,
        ),
        sounds = ThemePackSoundState(
            ringtoneUri = "content://media/external/audio/media/2",
            notificationUri = "content://media/internal/audio/media/7",
        ),
        widget = ThemePackWidgetState(primaryTint = 0xFF336699.toInt(), shuffleCount = -3),
        wallpaperPackJson = serializePack(
            WallpaperPack(
                id = "dayparts",
                name = "Dayparts",
                slots = listOf(
                    DaypartSlot(Daypart.MORNING, "content://media/external/images/media/1"),
                    DaypartSlot(Daypart.NIGHT, providerPicture),
                ),
            ),
        ),
        soundProfilesJson = serializeProfiles(
            listOf(
                SoundProfile(
                    id = "workday",
                    name = "Workday",
                    ringtoneUri = "content://media/external/audio/media/2",
                    startHour = 9,
                    endHour = 17,
                ),
            ),
        ),
    )
}
