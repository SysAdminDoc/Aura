package com.freevibe.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.model.ContentType
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A caller with less to spend than the usual ceiling gets a download no bigger than it asked for. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SoundApplierDownloadLimitTest {

    private val http = ScriptedHttp()
    private val applier = SoundApplier(
        context = ApplicationProvider.getApplicationContext<Context>(),
        okHttpClient = http.client(),
        prefs = mockk(),
        mediaCopyStore = mockk(),
        audioTrimmer = mockk(),
        systemSounds = mockk(),
    )

    @Test
    fun `a download that says it is bigger than the allowance is refused before it is read`() = runTest {
        http.enqueue { it.answer(200, ByteArray(4_096)) }

        val failure = applier.downloadOnly(URL, "Bell", ContentType.RINGTONE, maxBytes = 1_024).exceptionOrNull()

        assertTrue("got $failure", failure is IllegalStateException && "too large" in failure.message.orEmpty())
    }

    @Test
    fun `a download that hides its size is cut off at the allowance`() = runTest {
        http.enqueue { it.answer(200, ByteArray(4_096), declaredLength = -1) }

        val failure = applier.downloadOnly(URL, "Bell", ContentType.RINGTONE, maxBytes = 1_024).exceptionOrNull()

        assertTrue("got $failure", failure is MediaIngestionLimitExceeded)
    }

    @Test
    fun `without a smaller allowance the same download is not refused for its size`() = runTest {
        http.enqueue { it.answer(200, ByteArray(4_096)) }

        val failure = applier.downloadOnly(URL, "Bell", ContentType.RINGTONE).exceptionOrNull()

        // A run of zeros isn't audio, so the save still fails, but for that reason and not for its size.
        assertFalse(
            "got $failure",
            failure is MediaIngestionLimitExceeded || "too large" in failure?.message.orEmpty(),
        )
    }

    private companion object {
        const val URL = "https://sounds.example/bell.mp3"
    }
}
