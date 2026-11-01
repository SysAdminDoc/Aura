package com.freevibe.service

import com.freevibe.data.local.WallpaperHistoryDao
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperHistoryEntity
import com.freevibe.data.model.WallpaperTarget
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperHistoryManagerTest {

    @Test
    fun `history keeps a favorite's web address, not its offline path`() = runTest {
        val dao = mockk<WallpaperHistoryDao>(relaxed = true)
        val inserted = slot<WallpaperHistoryEntity>()
        coEvery { dao.insert(capture(inserted)) } returns Unit
        val manager = WallpaperHistoryManager(
            context = mockk(relaxed = true),
            dao = dao,
            colorExtractor = mockk(relaxed = true),
        )
        val favorite = Wallpaper(
            id = "rd_1",
            source = ContentSource.REDDIT,
            thumbnailUrl = "https://example.com/t.jpg",
            fullUrl = "/data/user/0/com.freevibe/files/offline_favorites/rd_1.jpg",
            width = 1080,
            height = 1920,
            remoteFullUrl = "https://example.com/full.jpg",
        )

        manager.record(favorite, WallpaperTarget.HOME)

        assertEquals("https://example.com/full.jpg", inserted.captured.fullUrl)
    }
}
