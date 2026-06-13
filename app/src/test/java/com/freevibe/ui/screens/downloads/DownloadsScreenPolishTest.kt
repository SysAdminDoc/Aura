package com.freevibe.ui.screens.downloads

import com.freevibe.data.model.DownloadEntity
import com.freevibe.service.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadsScreenPolishTest {

    @Test
    fun `download history summary includes file health and date`() {
        val download = DownloadEntity(
            id = "wall-1",
            source = "WALLHAVEN",
            type = "WALLPAPER",
            localPath = "",
            name = "Night grid",
        )

        assertEquals(
            "Night grid. File missing. Downloaded Jun 12, 9:30 AM.",
            downloadHistorySummary(
                download = download,
                broken = true,
                sourceUnavailable = false,
                downloadedAtLabel = "Jun 12, 9:30 AM",
            ),
        )
        assertEquals("Review missing file", downloadOpenActionLabel(download, broken = true))
    }

    @Test
    fun `active download status labels expose progress and failures`() {
        assertEquals(
            "42 percent downloaded",
            downloadProgressStatusLabel(
                DownloadProgress(
                    id = "sound-1",
                    fileName = "tone.mp3",
                    progress = 0.42f,
                    totalBytes = 1000L,
                    downloadedBytes = 420L,
                ),
            ),
        )
        assertEquals(
            "Download failed: Network timeout",
            downloadProgressStatusLabel(
                DownloadProgress(
                    id = "sound-2",
                    fileName = "tone.mp3",
                    progress = 0.1f,
                    totalBytes = 1000L,
                    downloadedBytes = 100L,
                    error = "Network timeout",
                ),
            ),
        )
    }
}
