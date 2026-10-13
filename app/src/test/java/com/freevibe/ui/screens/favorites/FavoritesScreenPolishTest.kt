package com.freevibe.ui.screens.favorites

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.LocalMediaStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoritesScreenPolishTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

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
            "Amber lockscreen. Selected. Minimal, 1440 by 3200, Wallhaven",
            favoriteWallpaperSummary(
                resources = resources,
                favorite = favorite,
                isSelected = true,
                sourceUnavailable = false,
            ),
        )
        assertEquals(
            "Amber lockscreen. Source unavailable. Minimal, 1440 by 3200, Wallhaven",
            favoriteWallpaperSummary(
                resources = resources,
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
            "Soft chime. Saved sound. 12 seconds. YouTube",
            favoriteSoundSummary(resources, favorite, sourceUnavailable = false),
        )
        assertEquals(
            "one second reads in the singular",
            "Soft chime. Saved sound. 1 second. YouTube",
            favoriteSoundSummary(resources, favorite.copy(duration = 1.2), sourceUnavailable = false),
        )
    }

    @Test
    fun `favorite summaries distinguish missing revoked and corrupt local media`() {
        val favorite = FavoriteEntity(
            id = "local-1",
            source = "LOCAL",
            type = "WALLPAPER",
            thumbnailUrl = "content://old/photo",
            fullUrl = "content://old/photo",
            offlinePath = "content://old/photo",
            localMediaStatus = LocalMediaStatus.PERMISSION_REVOKED,
        )

        assertEquals(
            "local-1. Local file permission revoked. Local",
            favoriteWallpaperSummary(resources, favorite, isSelected = false, sourceUnavailable = false),
        )
        assertEquals("Local file corrupt", favoriteLocalMediaHealthLabel(resources, LocalMediaStatus.CORRUPT))
        assertEquals("Local file missing", favoriteLocalMediaHealthLabel(resources, LocalMediaStatus.MISSING))
    }

    @Test
    fun `batch progress summary includes outcomes and current item`() {
        assertEquals(
            "Downloading favorites 3 of 10. 1 failed, 2 blocked. Current item: sky.jpg",
            favoritesBatchProgressSummary(
                resources = resources,
                processed = 3,
                total = 10,
                failed = 1,
                blocked = 2,
                currentItem = "sky.jpg",
            ),
        )
    }

    @Test
    @Config(qualifiers = "zh")
    fun `favorite summaries read in chinese`() {
        val favorite = FavoriteEntity(
            id = "tone-1",
            source = "BUNDLED",
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "https://example.com/sound",
            name = "Soft chime",
            duration = 12.0,
        )

        val summary = favoriteSoundSummary(resources, favorite, sourceUnavailable = false)

        assertEquals(
            "Soft chime. ${resources.getString(com.freevibe.R.string.favorites_state_saved_sound)}. 12 秒. " +
                resources.getString(com.freevibe.R.string.sound_shuffle_originals_title),
            summary,
        )
        assertEquals(
            "正在下载收藏：第 3 个，共 10 个",
            favoritesBatchProgressSummary(resources, processed = 3, total = 10, failed = 0, blocked = 0, currentItem = ""),
        )
    }
}
