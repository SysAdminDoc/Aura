package com.freevibe.service

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.model.ContentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What the restore's download allowance means once it reaches the code that fetches a sound. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContactRingtoneSoundRestorerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val urls = mockk<SoundUrlResolver>()
    private val applier = mockk<SoundApplier>()
    private val restorer = ContactRingtoneSoundRestorer(context, BundledContentProvider(), urls, applier)

    // One of Aura's own tones, so nothing here depends on a provider being switched on.
    private val tone = BundledContentProvider().getRingtones().first()
    private val entry = contactRingtone(
        soundId = tone.id,
        soundSource = "BUNDLED",
        soundTitle = tone.name,
        soundFileName = "",
    )

    @Test
    fun `with no allowance no source is asked and nothing is downloaded`() = runTest {
        assertNull(restorer.resolve(entry, downloadAllowance = 0))

        coVerify(exactly = 0) { urls.resolve(any()) }
        coVerify(exactly = 0) { applier.downloadOnly(any(), any(), any(), any()) }
    }

    @Test
    fun `a download is held to the allowance the restore gave it`() = runTest {
        val saved = Uri.parse("content://media/external/audio/media/77")
        coEvery { urls.resolve(any()) } returns tone.downloadUrl
        coEvery { applier.downloadOnly(any(), any(), any(), any()) } returns Result.success(saved)

        val ringtone = restorer.resolve(entry, downloadAllowance = 5_000)

        assertEquals(saved.toString(), ringtone?.uri)
        assertEquals(true, ringtone?.savedNow)
        coVerify(exactly = 1) { applier.downloadOnly(tone.downloadUrl, tone.name, ContentType.RINGTONE, 5_000L) }
    }
}
