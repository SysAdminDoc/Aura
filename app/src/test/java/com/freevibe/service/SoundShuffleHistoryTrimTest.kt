package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundShuffleHistoryTrimTest {

    private fun row(
        id: String,
        status: SoundShuffleHistoryStatus,
        itemId: String = "item",
        reason: String = SoundShuffleReason.DOWNLOAD_MISSING.name,
    ) = SoundShuffleHistoryEntry(
        historyId = id,
        poolId = "pool",
        poolName = "Pool",
        itemId = itemId,
        target = SoundShuffleTarget.RINGTONE,
        status = status,
        reason = reason,
    )

    @Test
    fun `a repeated skip replaces the last identical one`() {
        val history = listOf(row("old-skip", SoundShuffleHistoryStatus.SKIPPED), row("applied", SoundShuffleHistoryStatus.APPLIED))

        val next = withSoundShuffleSkip(history, row("new-skip", SoundShuffleHistoryStatus.SKIPPED))

        assertEquals(listOf("new-skip", "applied"), next.map { it.historyId })
    }

    @Test
    fun `a skip for another item or reason still gets its own row`() {
        val history = listOf(row("skip-a", SoundShuffleHistoryStatus.SKIPPED, itemId = "a"))

        val otherItem = withSoundShuffleSkip(history, row("skip-b", SoundShuffleHistoryStatus.SKIPPED, itemId = "b"))
        val otherReason = withSoundShuffleSkip(
            history,
            row("skip-a2", SoundShuffleHistoryStatus.SKIPPED, itemId = "a", reason = SoundShuffleReason.POOL_EMPTY.name),
        )

        assertEquals(2, otherItem.size)
        assertEquals(2, otherReason.size)
    }

    @Test
    fun `trimming gives up skip rows before applied rows`() {
        val skips = (1..10).map { row("skip-$it", SoundShuffleHistoryStatus.SKIPPED, itemId = "bad-$it") }
        val applied = (1..3).map { row("applied-$it", SoundShuffleHistoryStatus.APPLIED) }

        val trimmed = trimSoundShuffleHistory(skips + applied, max = 5)

        assertEquals(5, trimmed.size)
        assertTrue(trimmed.map { it.historyId }.containsAll(applied.map { it.historyId }))
        assertEquals(listOf("skip-1", "skip-2", "applied-1", "applied-2", "applied-3"), trimmed.map { it.historyId })
    }

    @Test
    fun `history under the cap is left alone`() {
        val history = listOf(row("a", SoundShuffleHistoryStatus.SKIPPED), row("b", SoundShuffleHistoryStatus.APPLIED))

        assertEquals(history, trimSoundShuffleHistory(history, max = 5))
    }
}
