package com.freevibe.ui.screens.downloads

import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.MediaTechnicalMetadata
import com.freevibe.service.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadsScreenPolishTest {

    @Test
    fun `focus index finds the row a search result opened`() {
        val rows = listOf(
            DownloadEntity(id = "a", source = "WALLHAVEN", type = "WALLPAPER", localPath = "/a.jpg"),
            DownloadEntity(id = "b", source = "PEXELS", type = "VIDEO", localPath = "/b.mp4"),
        )

        assertEquals(1, downloadFocusIndex(rows, "b"))
        assertNull("a row deleted since the search has nothing to scroll to", downloadFocusIndex(rows, "gone"))
        assertNull(downloadFocusIndex(rows, null))
    }

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

    @Test
    fun `video rows open with video mime and expose copy metadata`() {
        val video = DownloadEntity(
            id = "video-1",
            source = "REDDIT",
            type = "VIDEO",
            localPath = "/path/loop.webm",
            name = "Night loop",
        )

        assertEquals("video/*", downloadOpenMimeType(video))
        assertEquals("Video", downloadHealthLabel(video, broken = false, sourceUnavailable = false))
        assertEquals(
            "3840×2160 · H264 · 1:02 · 12 MB · HDR",
            formatMediaTechnicalMetadata(
                MediaTechnicalMetadata(
                    codec = "H264",
                    width = 3840,
                    height = 2160,
                    durationMs = 62_000,
                    sizeBytes = 12L * 1024L * 1024L,
                    isHdr = true,
                ),
            ),
        )
        assertEquals(
            "Night loop. Video. Downloaded Jun 12, 9:30 AM. Original: 1920×1080 · H264 · 8 MB. Optimized copy: 1080×1920 · H264 · 3 MB.",
            downloadHistorySummary(
                download = video,
                broken = false,
                sourceUnavailable = false,
                downloadedAtLabel = "Jun 12, 9:30 AM",
                copyDetails = listOf(
                    "Original" to "1920×1080 · H264 · 8 MB",
                    "Optimized copy" to "1080×1920 · H264 · 3 MB",
                ),
            ),
        )
    }

    @Test
    fun `absolute and file locators are checked while content uris stay provider backed`() {
        assertEquals(
            "loop.mp4",
            downloadLocalFile("/data/user/0/com.freevibe/files/media_originals/loop.mp4")?.name,
        )
        assertEquals(
            "loop.mp4",
            downloadLocalFile("file:///data/user/0/com.freevibe/files/media_originals/loop.mp4")?.name,
        )
        assertNull(downloadLocalFile("content://media/external/video/media/42"))
    }
}
