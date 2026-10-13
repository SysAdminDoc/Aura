package com.freevibe.data.repository

import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoteLegacyFallbackTest {

    @Test
    fun `the public count wins and the legacy tally fills in only while it is missing`() {
        assertEquals(4, preferredUpvotes(current = 4, legacy = 9))
        assertEquals(0, preferredUpvotes(current = 0, legacy = 9))
        assertEquals(9, preferredUpvotes(current = null, legacy = 9))
        assertEquals(0, preferredUpvotes(current = null, legacy = null))
    }

    @Test
    fun `one-shot reads only touch the legacy tally when the public row is missing`() = runTest {
        var legacyReads = 0
        assertEquals(5, upvotesWithLegacyFallback(readCurrent = { 5 }, readLegacy = { legacyReads++; 9 }))
        assertEquals(0, legacyReads)

        assertEquals(9, upvotesWithLegacyFallback(readCurrent = { null }, readLegacy = { legacyReads++; 9 }))
        assertEquals(1, legacyReads)
    }

    @Test
    fun `a refused legacy read leaves the id out instead of failing the batch`() = runTest {
        val counts = collectVoteCounts(listOf("migrated", "legacy", "refused")) { id ->
            upvotesWithLegacyFallback(
                readCurrent = { if (id == "migrated") 3 else null },
                readLegacy = {
                    when (id) {
                        "legacy" -> 7
                        else -> throw IOException("Permission denied")
                    }
                },
            )
        }
        assertEquals(mapOf("migrated" to 3, "legacy" to 7), counts)
    }

    @Test
    fun `the legacy leaderboard is used only while the public one is empty`() = runTest {
        var legacyReads = 0
        val legacyRows = listOf("a" to 2, "b" to 0, "c" to 8)

        val migrated = topVotedWithLegacyFallback(
            limit = 10,
            readCurrent = { listOf("x" to 1, "y" to 4) },
            readLegacy = { legacyReads++; legacyRows },
        )
        assertEquals(listOf("y" to 4, "x" to 1), migrated)
        assertEquals(0, legacyReads)

        val unmigrated = topVotedWithLegacyFallback(
            limit = 10,
            readCurrent = { listOf("z" to 0) },
            readLegacy = { legacyReads++; legacyRows },
        )
        assertEquals(listOf("c" to 8, "a" to 2), unmigrated)
        assertEquals(1, legacyReads)

        val refused = topVotedWithLegacyFallback(
            limit = 10,
            readCurrent = { emptyList() },
            readLegacy = { throw IOException("Permission denied") },
        )
        assertEquals(emptyList<Pair<String, Int>>(), refused)
    }

    @Test
    fun `a live count waits for both listeners so it never flashes zero before the legacy tally`() {
        val tally = UpvoteTally()
        assertNull(tally.onCurrent(null))
        assertEquals(6, tally.onLegacy(6))
        assertEquals(2, tally.onCurrent(2))
        assertEquals(6, tally.onCurrent(null))

        val refusedLegacy = UpvoteTally()
        assertNull(refusedLegacy.onLegacy(null))
        assertEquals(0, refusedLegacy.onCurrent(null))
    }

    @Test
    fun `a refused public count is never reported as zero`() {
        val noLegacy = UpvoteTally()
        assertNull(noLegacy.onLegacy(null))
        assertNull("an error must not read as zero votes", noLegacy.onCurrentFailed())

        val withLegacy = UpvoteTally()
        assertNull(withLegacy.onCurrentFailed())
        assertEquals(4, withLegacy.onLegacy(4))
    }
}
