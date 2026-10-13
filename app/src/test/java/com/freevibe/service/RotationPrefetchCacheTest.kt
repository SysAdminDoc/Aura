package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.squareup.moshi.Moshi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RotationPrefetchCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var posture = NetworkPosture(metered = false, dataSaver = false)
    private var clock = 1_000L
    private var downloads = 0

    private fun cache(maxEntries: Int = 4, maxBytes: Long = 1_000L) = RotationPrefetchCache(
        directory = File(temp.root, "prefetch"),
        posture = { posture },
        moshi = Moshi.Builder().build(),
        now = { clock++ },
        maxEntries = maxEntries,
        maxBytes = maxBytes,
    )

    private fun wallpaper(id: String, url: String = "https://example.test/$id.png") = Wallpaper(
        id = id,
        source = ContentSource.WALLHAVEN,
        thumbnailUrl = "https://example.test/$id-thumb.jpg",
        fullUrl = url,
        width = 1080,
        height = 2400,
        uploaderName = "someone",
        license = "CC0",
        tags = listOf("hills", "fog"),
    )

    private fun download(bytes: Int): suspend (File) -> Result<Long> = { file ->
        downloads++
        file.writeBytes(ByteArray(bytes))
        Result.success(bytes.toLong())
    }

    @Test
    fun `a saved wallpaper comes back whole with its file`() = runBlocking {
        val cache = cache()

        assertEquals(RotationPrefetchOutcome.STORED, cache.store("wallhaven|BOTH", wallpaper("a"), download(40)))

        val saved = cache.peek("wallhaven|BOTH")!!
        assertEquals(wallpaper("a"), saved.wallpaper)
        assertEquals(40L, saved.file.length())
        assertEquals("the picture keeps an extension the decoder knows", "png", saved.file.extension)
    }

    @Test
    fun `a new cache has nothing saved and a slot only answers for itself`() = runBlocking {
        val cache = cache()
        assertNull(cache.peek("wallhaven|BOTH"))

        cache.store("wallhaven|BOTH", wallpaper("a"), download(40))

        assertNull("another setup's wallpaper is never handed over", cache.peek("wallhaven|HOME"))
        assertNull(cache.peek("pixabay|BOTH"))
    }

    @Test
    fun `what was saved survives a new instance over the same folder`() = runBlocking {
        cache().store("wallhaven|BOTH", wallpaper("a"), download(40))

        assertEquals("a", cache().peek("wallhaven|BOTH")?.wallpaper?.id)
    }

    @Test
    fun `nothing is downloaded on a metered connection`() = runBlocking {
        posture = NetworkPosture(metered = true, dataSaver = false)
        val cache = cache()

        assertTrue(cache.shouldDefer())
        assertEquals(RotationPrefetchOutcome.DEFERRED_METERED, cache.store("wallhaven|BOTH", wallpaper("a"), download(40)))

        assertEquals(0, downloads)
        assertNull(cache.peek("wallhaven|BOTH"))
        assertEquals(RotationPrefetchOutcome.DEFERRED_METERED, cache.diagnostics().lastOutcome)
    }

    @Test
    fun `nothing is downloaded with Data Saver on`() = runBlocking {
        posture = NetworkPosture(metered = false, dataSaver = true)
        val cache = cache()

        assertTrue(cache.shouldDefer())
        assertEquals(
            RotationPrefetchOutcome.DEFERRED_DATA_SAVER,
            cache.store("wallhaven|BOTH", wallpaper("a"), download(40)),
        )
        assertEquals(0, downloads)
    }

    @Test
    fun `an unmetered connection is not deferred`() = runBlocking {
        assertFalse(cache().shouldDefer())
    }

    @Test
    fun `the oldest wallpaper goes when the count limit is reached`() = runBlocking {
        val cache = cache(maxEntries = 2)
        cache.store("wallhaven|BOTH", wallpaper("a"), download(10))
        val oldest = cache.peek("wallhaven|BOTH")!!.file
        cache.store("pixabay|BOTH", wallpaper("b"), download(10))

        cache.store("bing|BOTH", wallpaper("c"), download(10))

        assertNull(cache.peek("wallhaven|BOTH"))
        assertFalse("the evicted file is deleted, not orphaned", oldest.exists())
        assertEquals("b", cache.peek("pixabay|BOTH")?.wallpaper?.id)
        assertEquals("c", cache.peek("bing|BOTH")?.wallpaper?.id)
        assertEquals(2, cache.diagnostics().savedCount)
    }

    @Test
    fun `older wallpapers go until the newest fits the byte limit`() = runBlocking {
        val cache = cache(maxBytes = 100)
        cache.store("wallhaven|BOTH", wallpaper("a"), download(60))
        cache.store("pixabay|BOTH", wallpaper("b"), download(30))

        cache.store("bing|BOTH", wallpaper("c"), download(60))

        assertNull(cache.peek("wallhaven|BOTH"))
        assertEquals("b", cache.peek("pixabay|BOTH")?.wallpaper?.id)
        assertEquals("c", cache.peek("bing|BOTH")?.wallpaper?.id)
        assertEquals(90L, cache.diagnostics().savedBytes)
    }

    @Test
    fun `a file bigger than the whole cache is dropped and nothing else is evicted for it`() = runBlocking {
        val cache = cache(maxBytes = 100)
        cache.store("wallhaven|BOTH", wallpaper("a"), download(60))

        assertEquals(RotationPrefetchOutcome.OVER_BUDGET, cache.store("bing|BOTH", wallpaper("c"), download(101)))

        assertNull(cache.peek("bing|BOTH"))
        assertEquals("a", cache.peek("wallhaven|BOTH")?.wallpaper?.id)
        assertEquals(1, File(temp.root, "prefetch").listFiles { file -> file.name != "index.json" }!!.size)
    }

    @Test
    fun `saving again for a setup replaces what was there`() = runBlocking {
        val cache = cache()
        cache.store("wallhaven|BOTH", wallpaper("a"), download(10))
        val first = cache.peek("wallhaven|BOTH")!!.file

        cache.store("wallhaven|BOTH", wallpaper("b"), download(10))

        assertEquals("b", cache.peek("wallhaven|BOTH")?.wallpaper?.id)
        assertFalse(first.exists())
        assertEquals(1, cache.diagnostics().savedCount)
    }

    @Test
    fun `a failed download saves nothing and leaves no file behind`() = runBlocking {
        val cache = cache()

        val outcome = cache.store("wallhaven|BOTH", wallpaper("a")) { file ->
            file.writeBytes(ByteArray(5))
            Result.failure(java.io.IOException("reset"))
        }

        assertEquals(RotationPrefetchOutcome.FAILED, outcome)
        assertNull(cache.peek("wallhaven|BOTH"))
        assertTrue(File(temp.root, "prefetch").listFiles { file -> file.name != "index.json" }!!.isEmpty())
    }

    @Test
    fun `discarding removes the record and the file`() = runBlocking {
        val cache = cache()
        cache.store("wallhaven|BOTH", wallpaper("a"), download(10))
        val file = cache.peek("wallhaven|BOTH")!!.file

        cache.discard("wallhaven|BOTH")

        assertNull(cache.peek("wallhaven|BOTH"))
        assertFalse(file.exists())
    }

    @Test
    fun `a file deleted behind the cache counts as nothing saved`() = runBlocking {
        val cache = cache()
        cache.store("wallhaven|BOTH", wallpaper("a"), download(10))
        cache.peek("wallhaven|BOTH")!!.file.delete()

        assertNull(cache.peek("wallhaven|BOTH"))
    }

    @Test
    fun `an index that won't parse is thrown away with its files`() = runBlocking {
        val cache = cache()
        cache.store("wallhaven|BOTH", wallpaper("a"), download(10))
        File(temp.root, "prefetch/index.json").writeText("{ not json")

        assertNull(cache.peek("wallhaven|BOTH"))
        assertEquals(RotationPrefetchDiagnostics(), cache.diagnostics())
    }

    @Test
    fun `hits and misses are counted and kept when the cache is cleared`() = runBlocking {
        val cache = cache()
        cache.store("wallhaven|BOTH", wallpaper("a"), download(10))
        cache.recordHit()
        cache.recordMiss()
        cache.recordMiss()

        cache.clear()

        val diagnostics = cache.diagnostics()
        assertEquals(1, diagnostics.hits)
        assertEquals(2, diagnostics.misses)
        assertEquals(0, diagnostics.savedCount)
        assertEquals(RotationPrefetchOutcome.STORED, diagnostics.lastOutcome)
    }

    @Test
    fun `only a remote picture is worth saving ahead`() {
        assertTrue(wallpaper("a").isPrefetchable())
        assertFalse(wallpaper("a", url = "content://media/external/images/media/12").isPrefetchable())
        assertFalse(wallpaper("a", url = "/storage/emulated/0/Pictures/a.jpg").isPrefetchable())
    }

    @Test
    fun `a setup is named by its source and its screens`() {
        assertEquals("wallhaven|BOTH", rotationPrefetchSlot("wallhaven", com.freevibe.data.model.WallpaperTarget.BOTH))
        assertEquals("wallhaven|LOCK", rotationPrefetchSlot("wallhaven", com.freevibe.data.model.WallpaperTarget.LOCK))
    }
}
