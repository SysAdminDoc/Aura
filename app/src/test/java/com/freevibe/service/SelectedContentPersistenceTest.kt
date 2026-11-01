package com.freevibe.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A second holder reads only what the first one wrote to disk, the way a recreated process does. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SelectedContentPersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().build()

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.REDDIT,
        thumbnailUrl = "https://example.com/$id-thumb.jpg",
        fullUrl = "/data/user/0/com.freevibe/files/offline_favorites/$id.jpg",
        width = 1080,
        height = 1920,
        tags = listOf("forest", "night"),
        isAiGenerated = true,
        remoteFullUrl = "https://example.com/$id.jpg",
    )

    private fun restoredHolder(until: (SelectedContentHolder) -> Boolean): SelectedContentHolder {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            val holder = SelectedContentHolder(context, moshi)
            if (until(holder)) return holder
            if (System.currentTimeMillis() > deadline) return holder
            Thread.sleep(10)
        }
    }

    @Test
    fun `an open wallpaper and its list come back after the process dies`() {
        val selected = wallpaper("a")
        val list = listOf(selected, wallpaper("b"))

        SelectedContentHolder(context, moshi).selectWallpaper(selected, list)

        val restored = restoredHolder { it.selectedWallpaper.value != null && it.wallpaperList.value.isNotEmpty() }
        assertEquals(selected, restored.selectedWallpaper.value)
        assertEquals(list, restored.wallpaperList.value)
        assertEquals(selected.stableKey(), restored.wallpaperListAnchorKey.value)
    }

    @Test
    fun `an open sound comes back with its source after the process dies`() {
        val sound = Sound(
            id = "bundled_chime",
            source = ContentSource.BUNDLED,
            name = "Crystal Chime",
            previewUrl = "rawresource:///123",
            downloadUrl = "rawresource:///123",
            duration = 11.0,
            tags = listOf("chime"),
            isAiGenerated = false,
        )

        SelectedContentHolder(context, moshi).selectSound(sound)

        val restored = restoredHolder { it.selectedSound.value != null }
        assertEquals(sound, restored.selectedSound.value)
    }

    @Test
    fun `selecting a wallpaper with no list clears the saved list too`() {
        val first = SelectedContentHolder(context, moshi)
        first.selectWallpaper(wallpaper("a"), listOf(wallpaper("a"), wallpaper("b")))
        val next = wallpaper("c")

        first.selectWallpaper(next)

        val restored = restoredHolder { it.selectedWallpaper.value?.id == "c" && it.wallpaperList.value.isEmpty() }
        assertEquals(next, restored.selectedWallpaper.value)
        assertTrue(restored.wallpaperList.value.isEmpty())
    }

    @Test
    fun `quick selections end with the last one on disk`() {
        val holder = SelectedContentHolder(context, moshi)

        repeat(50) { holder.selectWallpaper(wallpaper("w$it")) }

        val restored = restoredHolder { it.selectedWallpaper.value?.id == "w49" }
        assertEquals("w49", restored.selectedWallpaper.value?.id)
        // A slow write landing out of order would overwrite it, so give one the chance.
        Thread.sleep(200)
        assertEquals("w49", SelectedContentHolder(context, moshi).selectedWallpaper.value?.id)
    }
}
