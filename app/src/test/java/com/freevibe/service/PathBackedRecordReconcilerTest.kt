package com.freevibe.service

import android.content.Context
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.model.DownloadEntity
import com.freevibe.data.model.FavoriteEntity
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathBackedRecordReconcilerTest {

    @Test
    fun `blank paths are not cleared again`() {
        assertFalse(shouldClearPathBackedRecord("") { false })
        assertFalse(shouldClearPathBackedRecord("   ") { false })
    }

    @Test
    fun `filesystem paths are cleared only when missing`() {
        assertFalse(shouldClearPathBackedRecord("/tmp/existing.jpg") { it == "/tmp/existing.jpg" })
        assertTrue(shouldClearPathBackedRecord("/tmp/missing.jpg") { false })
    }

    @Test
    fun `file uri resolves before probing filesystem`() {
        val checked = mutableListOf<String>()
        val exists = pathBackedRecordExists(
            rawPath = "file:///tmp/aura/restored.jpg",
            fileExists = {
                checked += it
                it == "/tmp/aura/restored.jpg"
            },
            contentUriExists = { false },
        )

        assertTrue(exists)
        assertEquals(listOf("/tmp/aura/restored.jpg"), checked)
    }

    @Test
    fun `content uri delegates to content probe`() {
        val checked = mutableListOf<String>()
        val exists = pathBackedRecordExists(
            rawPath = "content://media/external/images/media/42",
            fileExists = { false },
            contentUriExists = {
                checked += it
                true
            },
        )

        assertTrue(exists)
        assertEquals(listOf("content://media/external/images/media/42"), checked)
    }

    @Test
    fun `reconcile clears missing favorite and download paths`() = runTest {
        val existing = File.createTempFile("aura-existing", ".dat")
        val missing = File(existing.parentFile, "aura-missing-${System.nanoTime()}.dat")
        val favoriteDao = mockk<FavoriteDao>(relaxed = true)
        val downloadDao = mockk<DownloadDao>(relaxed = true)

        every { favoriteDao.getAll() } returns flowOf(
            listOf(
                favorite("fav-existing", existing.absolutePath),
                favorite("fav-missing", missing.absolutePath),
                favorite("fav-blank", ""),
            )
        )
        every { downloadDao.getAll() } returns flowOf(
            listOf(
                download("download-existing", existing.absolutePath),
                download("download-missing", missing.absolutePath),
                download("download-blank", ""),
            )
        )

        val result = PathBackedRecordReconciler(
            context = mockk<Context>(relaxed = true),
            favoriteDao = favoriteDao,
            downloadDao = downloadDao,
        ).reconcile()

        assertEquals(PathBackedRecordReconciliationResult(favoritesCleared = 1, downloadsCleared = 1), result)
        coVerify(exactly = 1) { favoriteDao.updateOfflinePath("fav-missing", "WALLHAVEN", "WALLPAPER", "") }
        coVerify(exactly = 1) { downloadDao.updateLocalPath("download-missing", "") }
        coVerify(exactly = 0) { favoriteDao.updateOfflinePath("fav-existing", "WALLHAVEN", "WALLPAPER", "") }
        coVerify(exactly = 0) { favoriteDao.updateOfflinePath("fav-blank", "WALLHAVEN", "WALLPAPER", "") }
        coVerify(exactly = 0) { downloadDao.updateLocalPath("download-existing", "") }
        coVerify(exactly = 0) { downloadDao.updateLocalPath("download-blank", "") }

        existing.delete()
    }

    @Test
    fun `startup and UI contracts keep missing files visible`() {
        val app = File("src/main/java/com/freevibe/FreeVibeApp.kt").readText()
        val database = File("src/main/java/com/freevibe/data/local/Database.kt").readText()
        val downloads = File("src/main/java/com/freevibe/ui/screens/downloads/DownloadsScreen.kt").readText()

        assertTrue(app.contains("lateinit var pathBackedRecordReconciler"))
        assertTrue(app.contains("pathBackedRecordReconciler.reconcile()"))
        assertTrue(database.contains("suspend fun updateLocalPath(id: String, path: String)"))
        assertTrue(downloads.contains("download.localPath.isBlank() || download.id in brokenIds"))
    }

    private fun favorite(id: String, offlinePath: String): FavoriteEntity = FavoriteEntity(
        id = id,
        source = "WALLHAVEN",
        type = "WALLPAPER",
        thumbnailUrl = "https://example.test/thumb.jpg",
        fullUrl = "https://example.test/full.jpg",
        offlinePath = offlinePath,
    )

    private fun download(id: String, localPath: String): DownloadEntity = DownloadEntity(
        id = id,
        source = "WALLHAVEN",
        type = "WALLPAPER",
        localPath = localPath,
        name = id,
    )
}
