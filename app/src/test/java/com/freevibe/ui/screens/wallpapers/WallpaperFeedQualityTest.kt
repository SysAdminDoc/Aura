package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.SearchResult
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.freevibe.service.WallpaperFingerprint
import com.freevibe.service.WallpaperStyleLearningProfile
import com.freevibe.service.WallpaperStyleLearningSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperFeedQualityTest {

    @Test
    fun `Bing images named in the query string stay separate`() {
        val ranked = rankWallpapers(
            wallpapers = listOf(
                wallpaper("bing_1", ContentSource.BING, "https://www.bing.com/th?id=OHR.Lighthouse_EN-US1_UHD.jpg", 3840, 2160),
                wallpaper("bing_2", ContentSource.BING, "https://www.bing.com/th?id=OHR.Glacier_EN-US2_UHD.jpg", 3840, 2160),
            ),
            filter = WallpaperDiscoverFilter.FOR_YOU,
        )

        assertEquals(setOf("bing_1", "bing_2"), ranked.map { it.id }.toSet())
    }

    @Test
    fun `paths that differ only in case stay separate`() {
        val ranked = rankWallpapers(
            wallpapers = listOf(
                wallpaper("cw_1", ContentSource.COMMUNITY, "https://firebasestorage.googleapis.com/v0/b/aura/o/w%2F-NxAbC.jpg?alt=media&token=1", 1440, 3200),
                wallpaper("cw_2", ContentSource.COMMUNITY, "https://firebasestorage.googleapis.com/v0/b/aura/o/w%2F-Nxabc.jpg?alt=media&token=2", 1440, 3200),
            ),
            filter = WallpaperDiscoverFilter.FOR_YOU,
        )

        assertEquals(2, ranked.size)
    }

    @Test
    fun `resized and signed variants of one file count once`() {
        assertEquals(
            wallpaperUrlIdentity("https://images.pexels.com/photos/7/a.jpeg?auto=compress&w=640"),
            wallpaperUrlIdentity("HTTPS://Images.Pexels.com/photos/7/a.jpeg?w=1280#top"),
        )
        assertEquals(
            "https://preview.redd.it/x1.jpeg",
            wallpaperUrlIdentity("https://preview.redd.it/x1.jpeg?width=1080&format=pjpg&auto=webp&s=abc"),
        )
        assertEquals(
            "https://lemmy.world/pictrs/image/a1.jpeg",
            wallpaperUrlIdentity("https://lemmy.world/pictrs/image/a1.jpeg?format=webp&thumbnail=256"),
        )
        assertEquals("https://example.org/Wall.JPG?id=9", wallpaperUrlIdentity("https://Example.org/Wall.JPG?id=9"))
        assertEquals("not a url |", wallpaperUrlIdentity(" not a url | "))
    }

    @Test
    fun `one image posted under two URLs shows once, on every page`() {
        val first = wallpaper("rd_1wpu3qi", ContentSource.REDDIT, "https://i.redd.it/aaa.jpeg", 0, 0)
        val other = wallpaper("rd_other", ContentSource.REDDIT, "https://i.redd.it/bbb.jpeg", 0, 0)
        val repost = wallpaper("rd_1wpu03j", ContentSource.REDDIT, "https://i.redd.it/ccc.jpeg", 0, 0)
        val same = WallpaperFingerprint(0x0F0F_3C3C_5A5A_6969L, 0x1234_5678_9ABC_DEF0L, 31, 64)
        val fingerprints = mapOf(
            first.stableKey() to same,
            other.stableKey() to same.copy(horizontal = 0x7777_1111_2222_4444L),
            repost.stableKey() to same,
        )

        assertEquals(listOf(first, other), coalesceContentDuplicates(listOf(first, other, repost), fingerprints))
        val nextPage = listOf(first, other) + listOf(repost, wallpaper("rd_new", ContentSource.REDDIT, "https://i.redd.it/ddd.jpeg", 0, 0))
        assertEquals(listOf("rd_1wpu3qi", "rd_other", "rd_new"), coalesceContentDuplicates(nextPage, fingerprints).map { it.id })
    }

    @Test
    fun `flat or unsampled images are never merged`() {
        val a = wallpaper("a", ContentSource.REDDIT, "https://i.redd.it/a.jpeg", 0, 0)
        val b = wallpaper("b", ContentSource.REDDIT, "https://i.redd.it/b.jpeg", 0, 0)
        val c = wallpaper("c", ContentSource.REDDIT, "https://i.redd.it/c.jpeg", 0, 0)
        val black = WallpaperFingerprint(0L, 0L, 31, 64)

        assertEquals(
            listOf(a, b, c),
            coalesceContentDuplicates(listOf(a, b, c), mapOf(a.stableKey() to black, b.stableKey() to black)),
        )
    }

    @Test
    fun `rankWallpapers keeps stronger duplicate instead of first duplicate`() {
        val weaker = wallpaper(
            id = "px_1",
            source = ContentSource.PEXELS,
            url = "https://example.com/shared.jpg",
            width = 1440,
            height = 2560,
            tags = listOf("photo"),
        )
        val stronger = wallpaper(
            id = "wh_1",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/shared.jpg",
            width = 2160,
            height = 3840,
            tags = listOf("amoled", "minimal"),
            favorites = 900,
        )

        val ranked = rankWallpapers(
            wallpapers = listOf(weaker, stronger),
            filter = WallpaperDiscoverFilter.FOR_YOU,
        )

        assertEquals(1, ranked.size)
        assertEquals("wh_1", ranked.first().id)
    }

    @Test
    fun `amoled filter keeps dark candidates`() {
        val dark = wallpaper(
            id = "dark",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/dark.jpg",
            width = 1440,
            height = 3200,
            tags = listOf("amoled", "black"),
            colors = listOf("#000000"),
        )
        val bright = wallpaper(
            id = "bright",
            source = ContentSource.PEXELS,
            url = "https://example.com/bright.jpg",
            width = 1440,
            height = 3200,
            tags = listOf("nature"),
            colors = listOf("#f5d142"),
        )

        val ranked = rankWallpapers(
            wallpapers = listOf(bright, dark),
            filter = WallpaperDiscoverFilter.AMOLED,
        )

        assertEquals(listOf("dark"), ranked.map { it.id })
    }

    @Test
    fun `quality hints expose resolution and icon safety`() {
        val wallpaper = wallpaper(
            id = "icon_safe",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/icon.jpg",
            width = 2160,
            height = 3840,
            tags = listOf("minimal", "gradient"),
            colors = listOf("#111111", "#222222"),
        )

        val hints = wallpaper.qualityHints()

        assertEquals("4K+", hints.resolutionLabel)
        assertTrue(hints.isIconSafe)
    }

    @Test
    fun `wallpaper card summary gives screen readers useful state`() {
        val wallpaper = wallpaper(
            id = "community_pick",
            source = ContentSource.COMMUNITY,
            url = "https://example.com/community.jpg",
            width = 1440,
            height = 3200,
            tags = listOf("amoled", "minimal"),
            colors = listOf("#000000"),
        ).copy(category = "Minimal")

        val summary = wallpaper.cardAccessibilitySummary(
            isFavorite = true,
            voteCount = 12,
        )

        assertEquals(
            "Community wallpaper, Minimal, QHD, Portrait, AMOLED friendly, icon-safe, 12 upvotes, saved to favorites",
            summary,
        )
    }

    // Color filter label test removed: clearWallpaperColorFilterLabel() and
    // wallpaperColorFilterActionLabel() are now @Composable (backed by string resources)
    // and cannot be called from plain JUnit tests.

    @Test
    fun `low signal wallpaper remains at the end instead of truncating endless inventory`() {
        val strongCandidates = listOf(
            wallpaper(
                id = "strong_one",
                source = ContentSource.WALLHAVEN,
                url = "https://example.com/strong-1.jpg",
                width = 2160,
                height = 3840,
                tags = listOf("minimal", "amoled", "gradient"),
                colors = listOf("#000000"),
                favorites = 900,
            ),
            wallpaper(
                id = "strong_two",
                source = ContentSource.BING,
                url = "https://example.com/strong-2.jpg",
                width = 2160,
                height = 3840,
                tags = listOf("clean", "abstract", "dark"),
                colors = listOf("#050505", "#101010"),
                favorites = 500,
            ),
            wallpaper(
                id = "strong_three",
                source = ContentSource.PEXELS,
                url = "https://example.com/strong-3.jpg",
                width = 1440,
                height = 3200,
                tags = listOf("minimal", "soft", "nature"),
                colors = listOf("#101820", "#13293d"),
                favorites = 420,
            ),
            wallpaper(
                id = "strong_four",
                source = ContentSource.REDDIT,
                url = "https://example.com/strong-4.jpg",
                width = 1440,
                height = 3200,
                tags = listOf("amoled", "simple", "space"),
                colors = listOf("#000000", "#0b0f1a"),
                favorites = 300,
            ),
        )
        val weakCandidate = wallpaper(
            id = "weak_logo",
            source = ContentSource.PEXELS,
            url = "https://example.com/weak.jpg",
            width = 720,
            height = 720,
            tags = listOf("logo", "promo"),
            colors = listOf("#f1f1f1"),
        )

        val ranked = rankWallpapers(
            wallpapers = listOf(weakCandidate) + strongCandidates,
            filter = WallpaperDiscoverFilter.FOR_YOU,
        )

        assertEquals(5, ranked.size)
        assertEquals("weak_logo", ranked.last().id)
    }

    @Test
    fun `reddit inventory is an explicit first tier ahead of secondary providers`() {
        val exceptionalSecondary = wallpaper(
            id = "wallhaven_featured",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/featured.jpg",
            width = 4320,
            height = 7680,
            tags = listOf("minimal", "amoled", "clean"),
            colors = listOf("#000000"),
            favorites = 50_000,
        )
        val reddit = wallpaper(
            id = "reddit_mobile",
            source = ContentSource.REDDIT,
            url = "https://i.redd.it/mobile.jpg",
            width = 0,
            height = 0,
            tags = listOf("reddit", "MobileWallpaper"),
        )

        val ranked = rankWallpapers(
            wallpapers = listOf(exceptionalSecondary, reddit),
            filter = WallpaperDiscoverFilter.FOR_YOU,
        )

        assertEquals(listOf("reddit_mobile", "wallhaven_featured"), ranked.map { it.id })
    }

    @Test
    fun `home merge puts reddit first and preserves pagination from either tier`() {
        val reddit = wallpaper(
            id = "reddit_1",
            source = ContentSource.REDDIT,
            url = "https://i.redd.it/one.jpg",
            width = 1440,
            height = 3200,
        )
        val secondary = wallpaper(
            id = "wallhaven_1",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/one.jpg",
            width = 1440,
            height = 3200,
        )

        val merged = mergeRedditFirstHomeResults(
            reddit = SearchResult(listOf(reddit), -1, 3, true),
            secondary = SearchResult(listOf(secondary), 200, 3, false),
            page = 3,
        )

        assertEquals(listOf(ContentSource.REDDIT, ContentSource.WALLHAVEN), merged.items.map { it.source })
        assertEquals(3, merged.currentPage)
        assertTrue(merged.hasMore)
    }

    @Test
    fun `style learning adapts for you ranking after enough local signals`() {
        val liked = wallpaper(
            id = "liked_forest",
            source = ContentSource.WALLHAVEN,
            url = "https://example.com/liked.jpg",
            width = 1440,
            height = 3200,
            tags = listOf("forest", "nature"),
            colors = listOf("#226633"),
        )
        val skipped = wallpaper(
            id = "skipped_minimal",
            source = ContentSource.PEXELS,
            url = "https://example.com/skipped.jpg",
            width = 1440,
            height = 3200,
            tags = listOf("minimal", "gradient", "amoled"),
            colors = listOf("#050505"),
            favorites = 900,
        )
        val earlyProfile = WallpaperStyleLearningProfile.EMPTY
            .record(liked, WallpaperStyleLearningSignal.FAVORITED)
            .record(skipped, WallpaperStyleLearningSignal.SKIPPED)
        val learnedProfile = earlyProfile.record(liked, WallpaperStyleLearningSignal.APPLIED)
            .record(skipped, WallpaperStyleLearningSignal.SKIPPED)

        val earlyRanked = rankWallpapers(
            wallpapers = listOf(skipped, liked),
            filter = WallpaperDiscoverFilter.FOR_YOU,
            styleLearningProfile = earlyProfile,
        )
        val learnedRanked = rankWallpapers(
            wallpapers = listOf(skipped, liked),
            filter = WallpaperDiscoverFilter.FOR_YOU,
            styleLearningProfile = learnedProfile,
        )

        assertEquals(listOf("skipped_minimal", "liked_forest"), earlyRanked.map { it.id })
        assertEquals(listOf("liked_forest", "skipped_minimal"), learnedRanked.map { it.id })
    }

    private fun wallpaper(
        id: String,
        source: ContentSource,
        url: String,
        width: Int,
        height: Int,
        tags: List<String> = emptyList(),
        colors: List<String> = emptyList(),
        favorites: Int = 0,
    ) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = url,
        fullUrl = url,
        width = width,
        height = height,
        tags = tags,
        colors = colors,
        favorites = favorites,
    )
}
