package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperContentFingerprintsTest {

    @Test
    fun `difference hashes compare each cell with its right and lower neighbour`() {
        val fallingLeftToRight = ThumbnailSample(IntArray(81) { i -> (8 - i % 9) * 10 }, 40, 80)

        val fingerprint = wallpaperFingerprintOf(fallingLeftToRight)

        assertEquals(-1L, fingerprint.horizontal)
        assertEquals(0L, fingerprint.vertical)
        assertEquals(40, fingerprint.sampleWidth)
        assertFalse("every bit the same says nothing about the picture", fingerprint.isDistinctive)
    }

    @Test
    fun `the same thumbnail fingerprints the same and a detailed one is distinctive`() {
        assertEquals(wallpaperFingerprintOf(detailed()), wallpaperFingerprintOf(detailed()))
        assertTrue(wallpaperFingerprintOf(detailed()).isDistinctive)
        assertFalse(wallpaperFingerprintOf(ThumbnailSample(IntArray(81), 40, 80)).isDistinctive)
    }

    @Test
    fun `a pass samples at most the limit and never retries a failed thumbnail`() = runTest {
        val calls = mutableListOf<String>()
        val store = WallpaperContentFingerprints { url ->
            calls += url
            if (url.endsWith("broken.jpg")) error("decode failed") else detailed()
        }
        val feed = listOf(wallpaper("a"), wallpaper("broken"), wallpaper("c"))

        store.fingerprint(feed, limit = 2)
        store.fingerprint(feed, limit = 2)
        store.fingerprint(feed, limit = 2)

        assertEquals(listOf("a.jpg", "broken.jpg", "c.jpg"), calls.map { it.substringAfterLast('/') })
        assertEquals(setOf(wallpaper("a").stableKey(), wallpaper("c").stableKey()), store.known.value.keys)
    }

    @Test
    fun `a pass cancelled by a newer feed leaves its thumbnails for the next pass`() = runTest {
        var hold = true
        val store = WallpaperContentFingerprints { if (hold) awaitCancellation() else detailed() }
        val feed = listOf(wallpaper("a"))

        val pass = launch { store.fingerprint(feed) }
        runCurrent()
        pass.cancel()
        hold = false
        store.fingerprint(feed)

        assertEquals(1, store.known.value.size)
    }

    private fun detailed() = ThumbnailSample(IntArray(81) { i -> (i % 9 * 53 + i / 9 * 97 + i % 9 * (i / 9) * 13) % 256 }, 40, 80)

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.REDDIT,
        thumbnailUrl = "https://preview.redd.it/$id.jpg",
        fullUrl = "https://i.redd.it/$id.jpg",
        width = 0,
        height = 0,
    )
}
