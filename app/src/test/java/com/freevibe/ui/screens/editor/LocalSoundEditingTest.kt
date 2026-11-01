package com.freevibe.ui.screens.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.service.MediaIngestionLimitExceeded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Trim on an Aura Original starts from a locator no web client can read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalSoundEditingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val original = "rawresource:///${R.raw.aura_ringtone_crystal_chime}"

    @Test
    fun `an Aura Original lands in the editor's cache under its real format`() {
        val staged = stageLocalSoundForEditing(context, original, "crystal_chime", maxBytes = 5_000_000)
        try {
            assertEquals(File(context.cacheDir, "audio_edit"), staged.parentFile)
            assertEquals("ogg", staged.extension)
            assertEquals("OggS", staged.inputStream().use { String(it.readNBytes(4), Charsets.US_ASCII) })
        } finally {
            staged.delete()
        }
    }

    @Test
    fun `a file that isn't audio is refused and leaves nothing behind`() {
        val folder = File(context.cacheDir, "audio_edit")
        val notAudio = File(context.filesDir, "not_audio.bin").apply { writeBytes(ByteArray(256) { 7 }) }

        assertThrows(Exception::class.java) {
            stageLocalSoundForEditing(context, notAudio.toURI().toString(), "not_audio", maxBytes = 5_000_000)
        }

        assertTrue(folder.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `a sound over the editor's limit is refused`() {
        assertThrows(MediaIngestionLimitExceeded::class.java) {
            stageLocalSoundForEditing(context, original, "crystal_chime", maxBytes = 8)
        }
    }

    @Test
    fun `the editor sends local sounds through staging, not the web client`() {
        val source = File("src/main/java/com/freevibe/ui/screens/editor/SoundEditorViewModel.kt").readText()
        val download = source.substringAfter("fun downloadToCache(")
        val localBranch = download.indexOf("isLocalMediaLocator(url)")
        val webRequest = download.indexOf("Request.Builder()")

        assertTrue(localBranch >= 0)
        assertFalse("the local check has to come before the request is built", webRequest in 0 until localBranch)
        assertTrue(download.substring(localBranch, webRequest).contains("stageLocalSoundForEditing("))
    }
}
