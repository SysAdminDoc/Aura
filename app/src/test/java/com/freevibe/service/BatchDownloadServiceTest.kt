package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchDownloadServiceTest {

    @Test
    fun `progress counts failures toward completion`() {
        val state = BatchDownloadState(
            totalCount = 4,
            completedCount = 2,
            failedCount = 2,
        )

        assertEquals(1f, state.progress, 0.0001f)
        assertTrue(state.isComplete)
    }

    @Test
    fun `progress counts policy-blocked items toward completion`() {
        val state = BatchDownloadState(
            totalCount = 4,
            completedCount = 1,
            failedCount = 1,
            blockedCount = 2,
        )

        assertEquals(4, state.processedCount)
        assertEquals(1f, state.progress, 0.0001f)
        assertTrue(state.isComplete)
    }

    @Test
    fun `batch download ids are scoped by wallpaper source`() {
        val redditWallpaper = wallpaper(id = "shared_42", source = ContentSource.REDDIT)
        val pexelsWallpaper = wallpaper(id = "shared_42", source = ContentSource.PEXELS)

        assertNotEquals(buildBatchDownloadId(redditWallpaper), buildBatchDownloadId(pexelsWallpaper))
    }

    @Test
    fun `batch file names include source to avoid collisions`() {
        val redditWallpaper = wallpaper(id = "shared_42", source = ContentSource.REDDIT)
        val pexelsWallpaper = wallpaper(id = "shared_42", source = ContentSource.PEXELS)

        assertNotEquals(
            buildBatchFileName(redditWallpaper, "jpg"),
            buildBatchFileName(pexelsWallpaper, "jpg"),
        )
    }

    @Test
    fun `batch download plan caps each provider by policy`() {
        val pixabay = (1..35).map { wallpaper(id = "px_$it", source = ContentSource.PIXABAY) }
        val local = (1..3).map { wallpaper(id = "local_$it", source = ContentSource.LOCAL) }

        val plan = planBatchDownloads(pixabay + local)

        assertEquals(33, plan.allowed.size)
        assertEquals(5, plan.blockedCount)
        assertEquals(30, plan.allowed.count { it.source == ContentSource.PIXABAY })
        assertEquals(3, plan.allowed.count { it.source == ContentSource.LOCAL })
    }

    @Test
    fun `a queued batch keeps unreached items pending after process death`() {
        val record = record(BATCH_ITEM_DONE, BATCH_ITEM_FAILED, BATCH_ITEM_PENDING, BATCH_ITEM_PENDING)

        val state = batchDownloadStateOf(record, workUnfinished = true, currentItem = "Nature")

        assertTrue(state.isRunning)
        assertEquals(1, state.completedCount)
        assertEquals(1, state.failedCount)
        assertEquals("Nature", state.currentItem)
        assertFalse(state.isComplete)
    }

    @Test
    fun `a batch whose work is gone settles with unreached items counted as failed`() {
        val record = record(BATCH_ITEM_DONE, BATCH_ITEM_PENDING, BATCH_ITEM_PENDING)

        val state = batchDownloadStateOf(record, workUnfinished = false, currentItem = "Nature")

        assertFalse(state.isRunning)
        assertEquals(2, state.failedCount)
        assertEquals("", state.currentItem)
        assertTrue(state.isComplete)
    }

    @Test
    fun `a cancelled batch stops reporting as running even before its work finishes`() {
        val record = record(BATCH_ITEM_PENDING).copy(cancelled = true)

        val state = batchDownloadStateOf(record, workUnfinished = true, currentItem = null)

        assertFalse(state.isRunning)
        assertTrue(state.isComplete)
    }

    @Test
    fun `item status updates touch only the named item`() {
        val record = record(BATCH_ITEM_PENDING, BATCH_ITEM_PENDING)

        val updated = record.withItemStatus("id_1", BATCH_ITEM_DONE)

        assertEquals(listOf(BATCH_ITEM_PENDING, BATCH_ITEM_DONE), updated.items.map { it.status })
    }

    @Test
    fun `the stored batch round-trips and a damaged one reads as empty`() {
        val moshi = Moshi.Builder().build()
        val record = BatchDownloadRecord(
            totalCount = 3,
            blockedCount = 1,
            items = listOf(batchDownloadItem(wallpaper(id = "a", source = ContentSource.PEXELS))),
        )
        val json = moshi.adapter(BatchDownloadRecord::class.java).toJson(record)

        assertEquals(record, parseBatchDownloadRecord(moshi, json))
        assertEquals(BatchDownloadRecord(), parseBatchDownloadRecord(moshi, "{not json"))
        assertEquals(BatchDownloadRecord(), parseBatchDownloadRecord(moshi, ""))
    }

    @Test
    fun `a stored item carries everything the worker needs`() {
        val wallpaper = wallpaper(id = "a", source = ContentSource.PEXELS)

        val item = batchDownloadItem(wallpaper)

        assertEquals("batch_${wallpaper.stableKey()}", item.downloadId)
        assertEquals("https://example.com/full.jpg", item.url)
        assertEquals("Aura_pexels_a.jpg", item.fileName)
        assertEquals("PEXELS", item.source)
        assertEquals("https://example.com/full.jpg", item.provenanceUrl)
        assertEquals("Nature", item.displayName)
        assertEquals(BATCH_ITEM_PENDING, item.status)
    }

    private fun record(vararg statuses: String) = BatchDownloadRecord(
        totalCount = statuses.size,
        items = statuses.mapIndexed { index, status ->
            BatchDownloadItem(
                downloadId = "id_$index",
                url = "https://example.com/$index.jpg",
                fileName = "Aura_test_$index.jpg",
                source = "PEXELS",
                provenanceUrl = "https://example.com/$index",
                displayName = "Nature",
                status = status,
            )
        },
    )

    private fun wallpaper(id: String, source: ContentSource) = Wallpaper(
        id = id,
        source = source,
        thumbnailUrl = "https://example.com/thumb.jpg",
        fullUrl = "https://example.com/full.jpg",
        width = 1080,
        height = 1920,
        category = "Nature",
        tags = listOf("Forest"),
    )
}
