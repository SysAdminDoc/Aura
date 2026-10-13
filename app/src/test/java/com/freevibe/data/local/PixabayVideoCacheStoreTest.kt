package com.freevibe.data.local

import com.freevibe.data.local.PixabayVideoCacheStore.CachedEntry
import com.freevibe.data.local.PixabayVideoCacheStore.Companion.selectCacheEvictions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixabayVideoCacheStoreTest {

    @Test
    fun `entries over the count limit are evicted oldest first`() {
        val entries = listOf(
            CachedEntry("newer", chars = 10, writtenAtMs = 300),
            CachedEntry("legacy", chars = 10, writtenAtMs = 0),
            CachedEntry("older", chars = 10, writtenAtMs = 100),
            CachedEntry("written", chars = 10, writtenAtMs = Long.MAX_VALUE),
        )

        val evicted = selectCacheEvictions(entries, keep = "written", maxEntries = 2, maxChars = 1_000)

        assertEquals(listOf("legacy", "older"), evicted)
    }

    @Test
    fun `entries over the size limit are evicted until the rest fit`() {
        val entries = listOf(
            CachedEntry("big-old", chars = 600, writtenAtMs = 1),
            CachedEntry("small-old", chars = 100, writtenAtMs = 2),
            CachedEntry("written", chars = 300, writtenAtMs = Long.MAX_VALUE),
        )

        val evicted = selectCacheEvictions(entries, keep = "written", maxEntries = 10, maxChars = 500)

        assertEquals(listOf("big-old"), evicted)
    }

    @Test
    fun `the entry being written survives even when it alone breaks the size limit`() {
        val entries = listOf(
            CachedEntry("old", chars = 10, writtenAtMs = 1),
            CachedEntry("written", chars = 900, writtenAtMs = Long.MAX_VALUE),
        )

        val evicted = selectCacheEvictions(entries, keep = "written", maxEntries = 10, maxChars = 500)

        assertEquals(listOf("old"), evicted)
    }

    @Test
    fun `nothing is evicted inside both limits`() {
        val entries = (1..5).map { CachedEntry("k$it", chars = 10, writtenAtMs = it.toLong()) }

        assertTrue(selectCacheEvictions(entries, keep = "k5", maxEntries = 5, maxChars = 50).isEmpty())
    }
}
