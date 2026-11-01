package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalMediaLocatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locator = "rawresource:///${R.raw.aura_notification_gentle_ding}"

    @Test
    fun `raw resource locator stages verified audio without network`() {
        assertTrue(isLocalMediaLocator(locator))
        assertFalse(isLocalMediaLocator("https://example.com/tone.ogg"))

        val staged = stageLocalMediaLocator(
            context = context,
            locator = locator,
            tempDirectoryName = "local_locator_test",
            prefix = "tone_",
            maxBytes = 1_000_000,
        )
        try {
            assertTrue(staged.length() > 0)
            assertEquals("audio/ogg", requireSniffedMediaFile(staged, MediaFamily.AUDIO, "Sound").mimeType)
        } finally {
            staged.delete()
        }
    }

    @Test
    fun `raw resource staging enforces the media size ceiling`() {
        assertThrows(MediaIngestionLimitExceeded::class.java) {
            stageLocalMediaLocator(
                context = context,
                locator = locator,
                tempDirectoryName = "local_locator_test",
                prefix = "tone_",
                maxBytes = 8,
            )
        }
    }

    @Test
    fun `a favorite's plain offline path is local and stages byte for byte`() {
        val bytes = ByteArray(64) { it.toByte() }
        val copy = offlineCopy("plain.jpg", bytes)

        assertTrue(isLocalMediaLocator(plainPath(copy)))
        val staged = stageLocalMediaLocator(
            context = context,
            locator = plainPath(copy),
            tempDirectoryName = "local_locator_test",
            prefix = "plain_",
            maxBytes = 1_000,
        )
        try {
            assertArrayEquals(bytes, staged.readBytes())
            assertTrue("staging copies, it never moves the favorite's file", copy.exists())
        } finally {
            staged.delete()
        }
    }

    @Test
    fun `a plain path keeps the media size ceiling`() {
        val copy = offlineCopy("large.jpg", ByteArray(64))

        assertThrows(MediaIngestionLimitExceeded::class.java) {
            stageLocalMediaLocator(
                context = context,
                locator = plainPath(copy),
                tempDirectoryName = "local_locator_test",
                prefix = "plain_",
                maxBytes = 8,
            )
        }
    }

    @Test
    fun `web addresses and blanks are never local`() {
        assertFalse(isLocalMediaLocator("https://example.com/x.png"))
        assertFalse(isLocalMediaLocator("example.com/x.png"))
        assertFalse(isLocalMediaLocator(""))
    }

    @Test
    fun `a wallpaper reads its offline copy until that copy is gone`() {
        val copy = offlineCopy("favorite.jpg", ByteArray(8))
        val favorite = wallpaper(fullUrl = plainPath(copy), remoteFullUrl = "https://example.com/full.jpg")

        assertEquals(plainPath(copy), favorite.currentFullUrl())

        copy.delete()

        assertEquals("https://example.com/full.jpg", favorite.currentFullUrl())
    }

    @Test
    fun `a wallpaper with no offline copy keeps its own address`() {
        val fromFeed = wallpaper(fullUrl = "https://example.com/full.jpg", remoteFullUrl = "")

        assertEquals("https://example.com/full.jpg", fromFeed.currentFullUrl())
    }

    private fun wallpaper(fullUrl: String, remoteFullUrl: String) = Wallpaper(
        id = "rd_1",
        source = ContentSource.REDDIT,
        thumbnailUrl = "https://example.com/thumb.jpg",
        fullUrl = fullUrl,
        width = 1080,
        height = 1920,
        remoteFullUrl = remoteFullUrl,
    )

    private fun offlineCopy(name: String, bytes: ByteArray): File =
        File(context.filesDir, "offline_favorites/$name").apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }

    // Windows test machines hand back a drive letter. The app only ever sees the part after it.
    private fun plainPath(file: File): String = file.absolutePath.replace('\\', '/').substringAfter(':')
}
