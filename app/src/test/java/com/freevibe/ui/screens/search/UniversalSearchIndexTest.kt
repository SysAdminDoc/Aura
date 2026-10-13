package com.freevibe.ui.screens.search

import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.WallpaperCollectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalSearchIndexTest {
    @Test
    fun `blank query returns no local results`() {
        assertTrue(
            buildUniversalSearchResults(
                query = " ",
                favorites = emptyList(),
                downloads = emptyList(),
                collections = emptyList(),
            ).isEmpty(),
        )
    }

    @Test
    fun `query returns segmented offline library results`() {
        val results = buildUniversalSearchResults(
            query = "neon",
            favorites = listOf(
                FavoriteEntity(
                    id = "wall-1",
                    source = "WALLHAVEN",
                    type = "WALLPAPER",
                    thumbnailUrl = "",
                    fullUrl = "https://example.test/neon-wall.jpg",
                    name = "Neon city",
                    category = "AMOLED",
                    offlinePath = "/storage/emulated/0/Pictures/neon-wall.jpg",
                ),
                FavoriteEntity(
                    id = "sound-1",
                    source = "YOUTUBE",
                    type = "SOUND",
                    thumbnailUrl = "",
                    fullUrl = "https://example.test/neon-chime.mp3",
                    name = "Neon chime",
                    fileType = "mp3",
                ),
            ),
            downloads = listOf(
                DownloadEntity(
                    id = "video-1",
                    source = "YOUTUBE",
                    type = "VIDEO",
                    localPath = "/storage/emulated/0/Movies/neon-loop.mp4",
                    name = "Neon loop",
                ),
                DownloadEntity(
                    id = "download-1",
                    source = "WALLHAVEN",
                    type = "WALLPAPER",
                    localPath = "/storage/emulated/0/Pictures/neon-local.jpg",
                    name = "Neon local",
                ),
            ),
            collections = listOf(
                WallpaperCollectionEntity(
                    collectionId = 7,
                    name = "Neon pack",
                ),
            ),
        )

        val sections = results.map { it.section }.toSet()
        listOf(
            UniversalSearchSection.WALLPAPERS,
            UniversalSearchSection.VIDEOS,
            UniversalSearchSection.SOUNDS,
            UniversalSearchSection.COLLECTIONS,
            UniversalSearchSection.DOWNLOADS,
            UniversalSearchSection.LOCAL_FILES,
        ).forEach { section ->
            assertTrue("Missing section $section in $sections", section in sections)
        }
        // FAVORITES is an overflow section: both favorites already appear in their
        // typed sections, so listing them again would duplicate results.
        assertTrue(results.none { it.section == UniversalSearchSection.FAVORITES })
        assertEquals(
            UniversalSearchBadge.LOCAL,
            results.first { it.section == UniversalSearchSection.DOWNLOADS }.badge,
        )
    }

    @Test
    fun `download results carry the download itself so Open can land on its row`() {
        val video = DownloadEntity(
            id = "pexels_video_9",
            source = "PEXELS",
            type = "VIDEO",
            localPath = "/storage/emulated/0/Movies/tide-loop.mp4",
            name = "Tide loop",
        )
        val results = buildUniversalSearchResults(
            query = "tide",
            favorites = emptyList(),
            downloads = listOf(video),
            collections = emptyList(),
        )

        listOf(
            UniversalSearchSection.VIDEOS,
            UniversalSearchSection.DOWNLOADS,
            UniversalSearchSection.LOCAL_FILES,
        ).forEach { section ->
            assertEquals(
                "$section result must target the exact download",
                UniversalSearchTarget.Download(video),
                results.single { it.section == section }.target,
            )
        }
    }

    @Test
    fun `url and raw path fields do not match queries`() {
        val results = buildUniversalSearchResults(
            query = "https",
            favorites = listOf(
                FavoriteEntity(
                    id = "wall-1",
                    source = "WALLHAVEN",
                    type = "WALLPAPER",
                    thumbnailUrl = "https://example.test/thumb.jpg",
                    fullUrl = "https://example.test/full.jpg",
                    name = "Sunset ridge",
                ),
            ),
            downloads = listOf(
                DownloadEntity(
                    id = "download-1",
                    source = "WALLHAVEN",
                    type = "WALLPAPER",
                    localPath = "/storage/emulated/0/Pictures/sunset.jpg",
                    name = "Sunset local",
                ),
            ),
            collections = emptyList(),
        )

        assertTrue("Generic URL tokens must not match every item: $results", results.isEmpty())
    }
}
