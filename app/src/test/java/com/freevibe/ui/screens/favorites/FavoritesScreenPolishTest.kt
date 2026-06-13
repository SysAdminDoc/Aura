package com.freevibe.ui.screens.favorites

import com.freevibe.data.model.FavoriteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesScreenPolishTest {

    @Test
    fun `favorite wallpaper summary names selection and source health`() {
        val favorite = FavoriteEntity(
            id = "wall-1",
            source = "WALLHAVEN",
            type = "WALLPAPER",
            thumbnailUrl = "https://example.com/thumb.jpg",
            fullUrl = "https://example.com/full.jpg",
            name = "Amber lockscreen",
            width = 1440,
            height = 3200,
            category = "Minimal",
        )

        assertEquals(
            "Amber lockscreen. selected. Minimal, 1440 by 3200, Wallhaven",
            favoriteWallpaperSummary(
                favorite = favorite,
                isSelected = true,
                sourceUnavailable = false,
            ),
        )
        assertEquals(
            "Amber lockscreen. source unavailable. Minimal, 1440 by 3200, Wallhaven",
            favoriteWallpaperSummary(
                favorite = favorite,
                isSelected = false,
                sourceUnavailable = true,
            ),
        )
    }

    @Test
    fun `favorite sound summary includes duration and source`() {
        val favorite = FavoriteEntity(
            id = "tone-1",
            source = "YOUTUBE",
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "https://example.com/sound",
            name = "Soft chime",
            duration = 12.4,
        )

        assertEquals(
            "Soft chime. saved sound. 12 seconds. YouTube",
            favoriteSoundSummary(favorite, sourceUnavailable = false),
        )
    }

    @Test
    fun `batch progress summary includes outcomes and current item`() {
        assertEquals(
            "Downloading favorites 3 of 10. 1 failed, 2 blocked. Current item: sky.jpg",
            favoritesBatchProgressSummary(
                processed = 3,
                total = 10,
                failed = 1,
                blocked = 2,
                currentItem = "sky.jpg",
            ),
        )
    }
}
