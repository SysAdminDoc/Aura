package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.legal.ProviderBuild
import com.freevibe.data.legal.ProviderChannel
import com.freevibe.data.legal.ProviderSetup
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.freevibe.data.repository.sanitizeVoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpapersScreenSectionsTest {

    @Test
    fun `source menu follows switches and keys and keeps the selected tab`() {
        val setup = ProviderSetup(
            enabled = mapOf(
                ContentSource.REDDIT to false,
                ContentSource.WALLHAVEN to true,
                ContentSource.PEXELS to true,
                ContentSource.PIXABAY to false,
                ContentSource.COMMUNITY to true,
            ),
            keyed = emptySet(),
        )
        fun menu(selected: WallpaperTab, build: ProviderBuild = ProviderBuild.FULL) =
            wallpaperSourceMenu(selected, setup, build, ProviderChannel.GITHUB)

        assertEquals(
            listOf(
                WallpaperSourceEntry(WallpaperTab.DISCOVER, needsKey = false),
                WallpaperSourceEntry(WallpaperTab.WALLHAVEN, needsKey = false),
                WallpaperSourceEntry(WallpaperTab.PEXELS, needsKey = true),
                WallpaperSourceEntry(WallpaperTab.COMMUNITY, needsKey = false),
            ),
            menu(WallpaperTab.DISCOVER),
        )
        assertTrue("Reddit stays listed while it's the open tab", WallpaperTab.REDDIT in menu(WallpaperTab.REDDIT).map { it.tab })
        assertTrue(WallpaperTab.SEARCH in menu(WallpaperTab.SEARCH).map { it.tab })
        assertFalse("FOSS has no community feed", WallpaperTab.COMMUNITY in menu(WallpaperTab.DISCOVER, ProviderBuild.FOSS).map { it.tab })
        assertEquals(
            false,
            wallpaperSourceMenu(WallpaperTab.DISCOVER, setup.copy(keyed = setOf(ContentSource.PEXELS)), ProviderBuild.FULL, ProviderChannel.GITHUB)
                .single { it.tab == WallpaperTab.PEXELS }.needsKey,
        )
    }

    @Test
    fun `paging waits for appended inventory to be laid out and latches empty pages`() {
        assertFalse(
            shouldRequestNextWallpaperPage(
                isNearEnd = true,
                isLoadingMore = false,
                laidOutItemCount = 40,
                visibleItemCount = 100,
                lastRequestedItemCount = 40,
            ),
        )
        assertFalse(
            shouldRequestNextWallpaperPage(
                isNearEnd = true,
                isLoadingMore = false,
                laidOutItemCount = 40,
                visibleItemCount = 40,
                lastRequestedItemCount = 40,
            ),
        )
        assertTrue(
            shouldRequestNextWallpaperPage(
                isNearEnd = true,
                isLoadingMore = false,
                laidOutItemCount = 44,
                visibleItemCount = 44,
                lastRequestedItemCount = 40,
            ),
        )
    }

    @Test
    fun `visible sections hide featured duplicates from feed`() {
        val dailyPick = wallpaper("hero")
        val topVoted = listOf(
            wallpaper("hero") to 10,
            wallpaper("top") to 9,
        )
        val feed = listOf(
            wallpaper("hero"),
            wallpaper("top"),
            wallpaper("feed"),
        )

        val sections = computeVisibleWallpaperSections(
            wallpapers = feed,
            hiddenIds = emptySet(),
            topVoted = topVoted,
            dailyPick = dailyPick,
            isDiscoverTab = true,
        )

        assertEquals("hero", sections.dailyPick?.id)
        assertEquals(listOf("top"), sections.topVoted.map { it.first.id })
        assertEquals(listOf("feed"), sections.feedWallpapers.map { it.id })
        assertEquals(listOf("hero", "top", "feed"), sections.pagerWallpapers.map { it.id })
        assertTrue(sections.hasRenderableContent)
    }

    @Test
    fun `discover sections place every reddit item before other providers`() {
        val sections = computeVisibleWallpaperSections(
            wallpapers = listOf(
                wallpaper("provider-feed", ContentSource.PEXELS),
                wallpaper("reddit-one", ContentSource.REDDIT),
                wallpaper("reddit-two", ContentSource.REDDIT),
            ),
            hiddenIds = emptySet(),
            topVoted = listOf(
                wallpaper("top-provider", ContentSource.WALLHAVEN) to 8,
                wallpaper("top-reddit", ContentSource.REDDIT) to 7,
            ),
            dailyPick = wallpaper("daily-bing", ContentSource.BING),
            isDiscoverTab = true,
        )

        assertEquals(
            listOf("reddit-one", "reddit-two", "top-reddit"),
            sections.pagerWallpapers.takeWhile { it.source == ContentSource.REDDIT }.map { it.id },
        )
        assertEquals(
            listOf("daily-bing", "top-provider", "provider-feed"),
            sections.pagerWallpapers.dropWhile { it.source == ContentSource.REDDIT }.map { it.id },
        )
    }

    @Test
    fun `visible sections respect hidden ids across hero top voted and feed`() {
        val sections = computeVisibleWallpaperSections(
            wallpapers = listOf(wallpaper("hero"), wallpaper("top"), wallpaper("feed")),
            hiddenIds = setOf("hero", "top"),
            topVoted = listOf(wallpaper("top") to 6),
            dailyPick = wallpaper("hero"),
            isDiscoverTab = true,
        )

        assertNull(sections.dailyPick)
        assertTrue(sections.topVoted.isEmpty())
        assertEquals(listOf("feed"), sections.feedWallpapers.map { it.id })
        assertTrue(sections.hasRenderableContent)
    }

    @Test
    fun `visible sections report no renderable content when everything is hidden`() {
        val sections = computeVisibleWallpaperSections(
            wallpapers = listOf(wallpaper("hidden_feed")),
            hiddenIds = setOf("hidden_feed", "hidden_top", "hidden_hero"),
            topVoted = listOf(wallpaper("hidden_top") to 4),
            dailyPick = wallpaper("hidden_hero"),
            isDiscoverTab = true,
        )

        assertNull(sections.dailyPick)
        assertTrue(sections.topVoted.isEmpty())
        assertTrue(sections.feedWallpapers.isEmpty())
        assertFalse(sections.hasRenderableContent)
    }

    @Test
    fun `visible sections honor stable hidden ids without hiding other providers`() {
        val keptWallpaper = wallpaper("shared_42", source = ContentSource.PIXABAY)
        val hiddenWallpaper = wallpaper("shared_42", source = ContentSource.PEXELS)

        val sections = computeVisibleWallpaperSections(
            wallpapers = listOf(hiddenWallpaper, keptWallpaper),
            hiddenIds = setOf(hiddenWallpaper.stableKey()),
            topVoted = emptyList(),
            dailyPick = null,
            isDiscoverTab = true,
        )

        assertEquals(listOf(ContentSource.PIXABAY), sections.feedWallpapers.map { it.source })
        assertEquals(listOf(ContentSource.PIXABAY), sections.pagerWallpapers.map { it.source })
        assertTrue(sections.hasRenderableContent)
    }

    @Test
    fun `visible sections honor sanitized moderation ids for raw provider keys`() {
        val moderatedWallpaper = wallpaper("folder/item", source = ContentSource.REDDIT)
        val visibleWallpaper = wallpaper("plain-item", source = ContentSource.REDDIT)

        val sections = computeVisibleWallpaperSections(
            wallpapers = listOf(moderatedWallpaper, visibleWallpaper),
            hiddenIds = setOf(sanitizeVoteKey(moderatedWallpaper.stableKey())),
            topVoted = emptyList(),
            dailyPick = moderatedWallpaper,
            isDiscoverTab = true,
        )

        assertNull(sections.dailyPick)
        assertEquals(listOf("plain-item"), sections.feedWallpapers.map { it.id })
        assertEquals(listOf("plain-item"), sections.pagerWallpapers.map { it.id })
    }

    @Test
    fun `pager items preserve feed order and open on tapped wallpaper`() {
        val sharedWallpapers = listOf(
            wallpaper("first"),
            wallpaper("second"),
            wallpaper("third"),
        )

        val pagerItems = computeWallpaperPagerItems(
            currentWallpaper = sharedWallpapers[1],
            sharedWallpapers = sharedWallpapers,
            hiddenIds = emptySet(),
        )

        assertEquals(listOf("first", "second", "third"), pagerItems.wallpapers.map { it.id })
        assertEquals(1, pagerItems.initialPage)
    }

    @Test
    fun `pager items ignore stale shared list when tapped wallpaper is not present`() {
        val pagerItems = computeWallpaperPagerItems(
            currentWallpaper = wallpaper("current"),
            sharedWallpapers = listOf(wallpaper("stale"), wallpaper("other")),
            hiddenIds = emptySet(),
        )

        assertEquals(listOf("current"), pagerItems.wallpapers.map { it.id })
        assertEquals(0, pagerItems.initialPage)
    }

    private fun wallpaper(id: String, source: ContentSource = ContentSource.WALLHAVEN) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "https://example.com/$id.jpg",
        fullUrl = "https://example.com/$id.jpg",
        width = 1440,
        height = 3200,
    )
}
