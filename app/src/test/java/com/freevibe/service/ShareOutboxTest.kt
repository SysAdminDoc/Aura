package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShareOutboxTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun directoryStaysUnderShareOut() {
        val cacheDir = temp.newFolder("cache")

        val dir = ShareOutbox.directory(cacheDir, "community_recordings")

        assertEquals(
            File(cacheDir, "share_out/community_recordings").canonicalFile,
            dir.canonicalFile,
        )
        assertTrue(dir.exists())
    }

    @Test
    fun directoryRejectsPathTraversalSegments() {
        val cacheDir = temp.newFolder("cache")

        val result = runCatching { ShareOutbox.directory(cacheDir, "../offline_favorites") }

        assertTrue(result.isFailure)
    }

    @Test
    fun pruneStaleFilesKeepsFreshFilesAndRemovesEmptyDirs() {
        val shareRoot = temp.newFolder("share_out")
        val oldDir = File(shareRoot, "old").apply { mkdirs() }
        val oldFile = File(oldDir, "old.json").apply {
            writeText("{}")
            setLastModified(1_000L)
        }
        val freshFile = File(shareRoot, "fresh.json").apply {
            writeText("{}")
            setLastModified(90_000L)
        }

        ShareOutbox.pruneStaleFiles(
            root = shareRoot,
            nowMs = 100_000L,
            maxAgeMs = 50_000L,
        )

        assertFalse(oldFile.exists())
        assertFalse(oldDir.exists())
        assertTrue(freshFile.exists())
    }
}
