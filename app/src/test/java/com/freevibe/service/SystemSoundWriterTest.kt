package com.freevibe.service

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemSoundWriterTest {
    private val stock = "content://media/internal/audio/media/42"
    private val first = "content://media/external/audio/media/900"
    private val second = "content://media/external/audio/media/901"

    private lateinit var prefs: PreferencesManager
    private lateinit var store: FakeSoundStore
    private lateinit var writer: SystemSoundWriter

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        prefs = PreferencesManager(context)
        runBlocking {
            SoundShuffleTarget.entries.forEach {
                prefs.setSystemSoundOriginal(it.contentType, "")
                prefs.setLastAppliedSoundUri(it.contentType, "")
            }
        }
        store = FakeSoundStore(mutableMapOf<SoundShuffleTarget, String?>(SoundShuffleTarget.RINGTONE to stock))
        writer = SystemSoundWriter(prefs, store)
    }

    @Test
    fun `the first apply keeps the stock sound and a repeat apply does not replace it`() = runTest {
        applyLikeSoundApplier(SoundShuffleTarget.RINGTONE, first)
        assertEquals("uri:$stock", prefs.systemSoundOriginal(ContentType.RINGTONE).first())

        applyLikeSoundApplier(SoundShuffleTarget.RINGTONE, second)

        assertEquals("uri:$stock", prefs.systemSoundOriginal(ContentType.RINGTONE).first())
        assertEquals(second, store.current(SoundShuffleTarget.RINGTONE))
    }

    @Test
    fun `restore puts the stock sound back and stops treating the sound as Aura's`() = runTest {
        applyLikeSoundApplier(SoundShuffleTarget.RINGTONE, first)

        val outcome = writer.restoreOriginal(SoundShuffleTarget.RINGTONE)

        assertEquals(OriginalRestore.Restored(stock), outcome)
        assertEquals(stock, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals("", prefs.lastAppliedRingtoneUri.first())
        assertEquals("uri:$stock", prefs.systemSoundOriginal(ContentType.RINGTONE).first())
    }

    @Test
    fun `a missing original changes nothing`() = runTest {
        applyLikeSoundApplier(SoundShuffleTarget.RINGTONE, first)
        store.unreadable += stock

        val outcome = writer.restoreOriginal(SoundShuffleTarget.RINGTONE)

        assertEquals(OriginalRestore.Missing, outcome)
        assertEquals(first, store.current(SoundShuffleTarget.RINGTONE))
        assertEquals(first, prefs.lastAppliedRingtoneUri.first())
    }

    @Test
    fun `each sound type keeps its own original`() = runTest {
        applyLikeSoundApplier(SoundShuffleTarget.ALARM, first)

        assertEquals("silent", prefs.systemSoundOriginal(ContentType.ALARM).first())
        assertEquals("", prefs.systemSoundOriginal(ContentType.RINGTONE).first())
        assertEquals(OriginalRestore.NeverChanged, writer.restoreOriginal(SoundShuffleTarget.RINGTONE))
        assertEquals(OriginalRestore.Restored(null), writer.restoreOriginal(SoundShuffleTarget.ALARM))
        assertEquals(null, store.current(SoundShuffleTarget.ALARM))
    }

    @Test
    fun `a sound an older build applied is not mistaken for the original`() = runTest {
        store.sounds[SoundShuffleTarget.NOTIFICATION] = first
        prefs.setLastAppliedSoundUri(ContentType.NOTIFICATION, first)

        applyLikeSoundApplier(SoundShuffleTarget.NOTIFICATION, second)

        assertEquals("unknown", prefs.systemSoundOriginal(ContentType.NOTIFICATION).first())
        assertEquals(OriginalRestore.Unknown, writer.restoreOriginal(SoundShuffleTarget.NOTIFICATION))
        assertEquals(second, store.current(SoundShuffleTarget.NOTIFICATION))
    }

    /** SoundApplier writes the sound, then records it as the last one Aura applied. */
    private suspend fun applyLikeSoundApplier(target: SoundShuffleTarget, uri: String) {
        writer.write(target, Uri.parse(uri))
        prefs.setLastAppliedSoundUri(target.contentType, uri)
    }

    private class FakeSoundStore(val sounds: MutableMap<SoundShuffleTarget, String?>) : SystemSoundStore {
        val unreadable = mutableSetOf<String>()

        override fun current(target: SoundShuffleTarget): String? = sounds[target]

        override fun set(target: SoundShuffleTarget, uri: String?) {
            sounds[target] = uri
        }

        override fun isReadable(uri: String): Boolean = uri !in unreadable
    }
}
