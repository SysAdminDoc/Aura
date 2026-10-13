package com.freevibe.data.model

import com.freevibe.R
import com.freevibe.data.local.DEFAULT_REDDIT_WALLPAPER_SUBREDDITS
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RedditFeedPresetTest {

    private val defaults = defaultRedditFeedPresetStore(name = { "name-$it" })
    private val adapter = Moshi.Builder().build().adapter(RedditFeedPresetStore::class.java)

    @Test
    fun `aura ships editable feeds for both tabs and none of them lets adult posts in`() {
        assertEquals(4, defaults.presetsOf(RedditFeedMediaType.WALLPAPER).size)
        assertEquals(2, defaults.presetsOf(RedditFeedMediaType.VIDEO).size)
        assertTrue(defaults.presets.none { it.includeAdult })
        assertEquals("default_phone", defaults.activePreset(RedditFeedMediaType.WALLPAPER).id)
        assertEquals("default_motion", defaults.activePreset(RedditFeedMediaType.VIDEO).id)
        assertEquals("", defaults.rotationPresetId)
        assertEquals(defaults, defaults.normalized(defaults))
    }

    @Test
    fun `a subreddit list from before presets becomes the first feed and stays in use`() {
        val migrated = defaultRedditFeedPresetStore(
            name = { "name-$it" },
            legacyWallpaperSubreddits = "CustomWalls, r/OLED_Portraits",
            legacyVideoSubreddits = "livewallpapers,LiveWallpaper,Cinemagraphs,perfectloops",
        )
        val untouched = defaultRedditFeedPresetStore(
            name = { "name-$it" },
            legacyWallpaperSubreddits = DEFAULT_REDDIT_WALLPAPER_SUBREDDITS,
        )

        val custom = migrated.activePreset(RedditFeedMediaType.WALLPAPER)
        assertEquals("legacy_wallpaper", custom.id)
        assertEquals(listOf("CustomWalls", "OLED_Portraits"), custom.subreddits)
        assertEquals("an old default list isn't a choice", "default_motion", migrated.activePreset(RedditFeedMediaType.VIDEO).id)
        assertEquals(defaults, untouched)
    }

    @Test
    fun `presets can be created, renamed, duplicated, reordered and deleted`() {
        val created = defaults.upsert(preset("p_new", "Mine", "iWallpaper"))
        val renamed = created.upsert(created.presets.first { it.id == "p_new" }.copy(name = "Evenings"))
        val duplicated = renamed.duplicate("default_amoled", "p_copy") { "$it copy" }
        val moved = duplicated.move("p_new", -10)
        val deleted = moved.delete("default_anime")

        assertEquals("Evenings", renamed.presets.first { it.id == "p_new" }.name)
        val wallpaperOrder = duplicated.presetsOf(RedditFeedMediaType.WALLPAPER).map { it.id }
        assertEquals("default_amoled", wallpaperOrder[wallpaperOrder.indexOf("p_copy") - 1])
        assertEquals("name-${R.string.reddit_preset_default_amoled} copy", duplicated.presets.first { it.id == "p_copy" }.name)
        assertEquals("p_new", moved.presetsOf(RedditFeedMediaType.WALLPAPER).first().id)
        assertEquals(
            "moving a wallpaper feed leaves the video feeds where they were",
            duplicated.presetsOf(RedditFeedMediaType.VIDEO),
            moved.presetsOf(RedditFeedMediaType.VIDEO),
        )
        assertTrue(deleted.presets.none { it.id == "default_anime" })
    }

    @Test
    fun `an edit can't turn a wallpaper feed into a video feed`() {
        val edited = defaults.upsert(defaults.presets.first().copy(mediaType = RedditFeedMediaType.VIDEO))

        assertEquals(RedditFeedMediaType.WALLPAPER, edited.presets.first().mediaType)
    }

    @Test
    fun `deleting keeps one feed per tab and moves whatever pointed at the deleted one`() {
        val onlyMotion = defaults.delete("default_cinemagraphs")
        val bound = defaults.activate("default_anime").bindRotation("default_anime")
        val afterDelete = bound.delete("default_anime")

        assertSame("the last video feed stays", onlyMotion, onlyMotion.delete("default_motion"))
        assertEquals("default_phone", afterDelete.activePreset(RedditFeedMediaType.WALLPAPER).id)
        assertEquals("", afterDelete.rotationPresetId)
        assertEquals("default_phone", afterDelete.rotationPreset().id)
    }

    @Test
    fun `rotation only binds to a wallpaper feed`() {
        assertEquals("default_high_res", defaults.bindRotation("default_high_res").rotationPreset().id)
        assertEquals("", defaults.bindRotation("default_motion").rotationPresetId)
        assertEquals("", defaults.bindRotation("gone").rotationPresetId)
    }

    @Test
    fun `a preset survives subreddits that are gone or misspelled`() {
        val stored = defaults.copy(
            presets = defaults.presets + preset("p_mixed", "Mixed", "iWallpaper,not-valid!,../private") +
                preset("p_broken", "Broken", "no!,also bad"),
            activeWallpaperPresetId = "p_broken",
        )

        val normalized = stored.normalized(defaults)

        assertEquals(listOf("iWallpaper"), normalized.presets.first { it.id == "p_mixed" }.subreddits)
        assertTrue("nothing valid left to load", normalized.presets.none { it.id == "p_broken" })
        assertEquals("default_phone", normalized.activePreset(RedditFeedMediaType.WALLPAPER).id)
    }

    @Test
    fun `a store with no feeds for a tab gets the defaults back`() {
        val videoOnly = defaults.copy(presets = defaults.presetsOf(RedditFeedMediaType.VIDEO))

        val normalized = videoOnly.normalized(defaults)

        assertEquals(defaults.presetsOf(RedditFeedMediaType.WALLPAPER), normalized.presetsOf(RedditFeedMediaType.WALLPAPER))
    }

    @Test
    fun `adult posts need the preset and Show NSFW both`() {
        val safe = preset("p", "Safe", "iWallpaper")
        val open = safe.copy(includeAdult = true)

        assertFalse(safe.accepts("[NSFW] Night", 0, 0, showAdultContent = true))
        assertFalse(open.accepts("Beach 18+", 0, 0, showAdultContent = false))
        assertTrue(open.accepts("[nsfw] Night", 0, 0, showAdultContent = true))
        assertFalse(safe.accepts("Hentai art", 0, 0, showAdultContent = false))
        assertTrue("a word that only contains a marker is fine", safe.accepts("Unsafe bridge at dusk", 0, 0, showAdultContent = false))
    }

    @Test
    fun `minimums judge only what a post lists`() {
        val tall = preset("p", "Tall", "iWallpaper").copy(minWidth = 1080, minHeight = 2400, minDurationSeconds = 6)

        assertTrue(tall.accepts("Tall", 1440, 3200, showAdultContent = false))
        assertFalse(tall.accepts("Short", 1080, 1920, showAdultContent = false))
        assertTrue("no size listed", tall.accepts("Unknown", 0, 0, showAdultContent = false))
        assertFalse(tall.accepts("Brief", 1440, 3200, durationSeconds = 3, showAdultContent = false))
        assertTrue("no length listed", tall.accepts("Clip", 1440, 3200, durationSeconds = 0, showAdultContent = false))
    }

    @Test
    fun `the feed signature changes with every part of the preset that changes the feed`() {
        val base = preset("p", "Base", "iWallpaper,MobileWallpaper").copy(sort = "top")
        val variants = listOf(
            base,
            base.copy(subreddits = listOf("iWallpaper")),
            base.copy(sort = "hot"),
            base.copy(timeWindow = "year"),
            base.copy(includeAdult = true),
            base.copy(minWidth = 1080),
            base.copy(minHeight = 2400),
            base.copy(minDurationSeconds = 5),
        )

        assertEquals(variants.size, variants.map { it.feedSignature(showAdultContent = true) }.toSet().size)
        assertEquals("a rename isn't a new feed", base.feedSignature(false), base.copy(name = "Other", id = "q").feedSignature(false))
        assertEquals(
            "the time window only matters to Top",
            base.copy(sort = "new").feedSignature(false),
            base.copy(sort = "new", timeWindow = "year").feedSignature(false),
        )
    }

    @Test
    fun `a backup round trip restores the same feeds onto a fresh device`() {
        val edited = defaults
            .upsert(preset("p_mine", "Mine", "Amoledbackgrounds").copy(sort = "top", timeWindow = "all", minHeight = 1800))
            .activate("p_mine")
            .bindRotation("default_high_res")
            .normalized(defaults)

        val restored = defaults.mergeImported(parseRedditFeedPresetStoreStrict(adapter, adapter.toJson(edited))).normalized(defaults)

        assertEquals(edited, restored)
    }

    @Test
    fun `a restore brings back rotation that follows the Reddit tab`() {
        val device = defaults.bindRotation("default_anime")

        assertEquals("", device.mergeImported(defaults).rotationPresetId)
    }

    @Test
    fun `a restore keeps every backed-up feed when the device is near the cap`() {
        val device = (1..16).fold(defaults) { store, n -> store.upsert(preset("p_device_$n", "Device $n", "iWallpaper")) }
        val backup = defaults.copy(presets = (1..3).map { preset("p_backup_$it", "Backup $it", "MobileWallpaper") })

        val merged = device.mergeImported(backup).normalized(defaults)

        val wallpaperIds = merged.presetsOf(RedditFeedMediaType.WALLPAPER).map { it.id }
        assertEquals(MAX_REDDIT_FEED_PRESETS_PER_TYPE, wallpaperIds.size)
        assertTrue(wallpaperIds.containsAll(listOf("p_backup_1", "p_backup_2", "p_backup_3")))
        assertFalse("the device's last extras give way", wallpaperIds.contains("p_device_16"))
        assertTrue(wallpaperIds.contains("p_device_13"))
    }

    @Test
    fun `stored presets that can't be read fall back to the defaults`() {
        assertEquals(defaults, decodeRedditFeedPresetStore(adapter, "", defaults))
        assertEquals(defaults, decodeRedditFeedPresetStore(adapter, "{not json", defaults))
        assertTrue(runCatching { parseRedditFeedPresetStoreStrict(adapter, adapter.toJson(RedditFeedPresetStore(version = 2))) }.isFailure)
    }

    @Test
    fun `diagnostics describe the feeds without naming them`() {
        val summary = defaults.upsert(preset("p_secret", "Secret name", "PrivateSub")).activate("p_secret").diagnosticsSummary(false)

        assertFalse(summary.contains("Secret name"))
        assertFalse(summary.contains("PrivateSub"))
        assertTrue(summary.contains("wallpaper feed: custom, new, 1 subreddits, safe"))
    }

    private fun preset(id: String, name: String, subreddits: String) = RedditFeedPreset(
        id = id,
        name = name,
        mediaType = RedditFeedMediaType.WALLPAPER,
        subreddits = subreddits.split(','),
    )
}
