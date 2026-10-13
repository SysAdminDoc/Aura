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
        val fallingLeftToRight = ThumbnailSample(IntArray(289) { i -> (16 - i % 17) * 10 }, 40, 80)

        val fingerprint = wallpaperFingerprintOf(fallingLeftToRight)

        assertEquals(List(4) { -1L }, fingerprint.horizontal)
        assertEquals(List(4) { 0L }, fingerprint.vertical)
        assertEquals(40, fingerprint.sampleWidth)
        assertFalse("every bit the same says nothing about the picture", fingerprint.isDistinctive)
    }

    @Test
    fun `the same thumbnail fingerprints the same and a detailed one is distinctive`() {
        assertEquals(wallpaperFingerprintOf(detailed()), wallpaperFingerprintOf(detailed()))
        assertTrue(wallpaperFingerprintOf(detailed()).isDistinctive)
        assertFalse(wallpaperFingerprintOf(ThumbnailSample(IntArray(289), 40, 80)).isDistinctive)
    }

    @Test
    fun `the tone map keeps a brighter copy of the same shape apart`() {
        val brighter = ThumbnailSample(detailed().luma.map { (it / 2) + 120 }.toIntArray(), 40, 80)
        val darker = ThumbnailSample(detailed().luma.map { it / 2 }.toIntArray(), 40, 80)

        val a = wallpaperFingerprintOf(brighter)
        val b = wallpaperFingerprintOf(darker)

        assertEquals("same shape", a.horizontal, b.horizontal)
        assertTrue("different brightness", a != b)
    }

    @Test
    fun `a pass samples every new thumbnail in batches and never retries a failed one`() = runTest {
        val calls = mutableListOf<String>()
        val store = WallpaperContentFingerprints { url ->
            calls += url
            if (url.endsWith("broken.jpg")) error("decode failed") else detailed()
        }
        val feed = listOf(wallpaper("a"), wallpaper("broken"), wallpaper("c"))

        store.fingerprint(feed, batchSize = 2)
        store.fingerprint(feed, batchSize = 2)

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

    private fun detailed() = ThumbnailSample(IntArray(289) { i -> (i % 17 * 53 + i / 17 * 97 + i % 17 * (i / 17) * 13) % 256 }, 40, 80)

    private fun wallpaper(id: String) = Wallpaper(
        id = id,
        source = ContentSource.REDDIT,
        thumbnailUrl = "https://preview.redd.it/$id.jpg",
        fullUrl = "https://i.redd.it/$id.jpg",
        width = 0,
        height = 0,
    )
}
