package com.freevibe.service

import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ledger: what gets remembered when Aura sets a contact ringtone, and what a restore adds to it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContactRingtoneBackupManagerTest {

    private val ledgerJson = MutableStateFlow("")
    private val prefs = mockk<PreferencesManager>()
    private val writer = FakeContactRingtoneWriter()
    private val sounds = FakeContactRingtoneSounds()
    private lateinit var manager: ContactRingtoneBackupManager

    private val bell = Sound(
        id = "fs_100",
        source = ContentSource.FREESOUND,
        name = "Bell",
        previewUrl = "https://sounds.example/fs_100.mp3",
        downloadUrl = "https://sounds.example/fs_100.mp3",
        license = "CC0",
        uploaderName = "someone",
        sourcePageUrl = "https://sounds.example/page/fs_100",
    )
    private val ada = ContactInfo(id = 7L, lookupKey = "key-ada", name = "Ada Lovelace")

    @Before
    fun setUp() {
        every { prefs.contactRingtoneLedgerJson } returns ledgerJson
        // Same read and rewrite the real DataStore edit does.
        coEvery { prefs.updateContactRingtoneLedgerJson(any()) } answers {
            ledgerJson.value = firstArg<(String) -> String>().invoke(ledgerJson.value)
        }
        sounds.fileNames[SAVED_URI] = "Bell.mp3"
        manager = ContactRingtoneBackupManager(prefs, Moshi.Builder().build(), writer, sounds)
    }

    @Test
    fun `setting a contact ringtone is remembered by key, name and sound`() = runTest {
        manager.record(ada, bell, SAVED_URI)

        val entry = manager.exportEntries().single()
        assertEquals("key-ada", entry.contactLookupKey)
        assertEquals("Ada Lovelace", entry.contactDisplayName)
        assertEquals("FREESOUND", entry.soundSource)
        assertEquals("fs_100", entry.soundId)
        assertEquals("Bell", entry.soundTitle)
        assertEquals("Bell.mp3", entry.soundFileName)
        assertTrue(entry.assignedAt > 0)
    }

    @Test
    fun `what is stored never holds the contact row id or the ringtone address`() = runTest {
        manager.record(ada, bell, SAVED_URI)

        assertFalse(ledgerJson.value.contains(SAVED_URI))
        assertFalse(ledgerJson.value.contains("content://"))
        assertFalse(ledgerJson.value.contains("\"id\""))
        assertFalse(ledgerJson.value.contains("\"_id\""))
    }

    @Test
    fun `a new ringtone for the same contact replaces the old row`() = runTest {
        manager.record(ada, bell, SAVED_URI)
        manager.record(ada, bell.copy(id = "fs_300", name = "Chime"), SAVED_URI)

        assertEquals(listOf("fs_300"), manager.exportEntries().map { it.soundId })
    }

    @Test
    fun `a contact with no name is remembered by its key alone`() = runTest {
        manager.record(ada.copy(name = UNNAMED_CONTACT), bell, SAVED_URI)

        val entry = manager.exportEntries().single()
        assertEquals("key-ada", entry.contactLookupKey)
        assertEquals("", entry.contactDisplayName)
    }

    @Test
    fun `a ledger that can't be written never fails the assignment`() = runTest {
        coEvery { prefs.updateContactRingtoneLedgerJson(any()) } throws IllegalStateException("disk full")

        manager.record(ada, bell, SAVED_URI)

        assertTrue(manager.exportEntries().isEmpty())
    }

    @Test
    fun `an empty ledger exports nothing and needs no contacts access`() = runTest {
        writer.allowed = false

        assertTrue(manager.exportEntries().isEmpty())
        assertFalse(manager.canWriteContacts())
    }

    @Test
    fun `a restore remembers what it set and leaves out what it could not`() = runTest {
        writer.add("key-ada", "Ada Lovelace")
        sounds.onPhone["fs_100"] = SAVED_URI

        val results = manager.restore(
            listOf(
                contactRingtone(lookupKey = "key-bob", name = "Bob", assignedAt = 5),
                contactRingtone(soundFileName = "Old name.mp3", assignedAt = 9),
            ),
        )

        assertEquals(
            listOf(ContactRingtoneRestoreStatus.MISSING_CONTACT, ContactRingtoneRestoreStatus.RESTORED),
            results.map { it.status },
        )
        val remembered = manager.exportEntries().single()
        assertEquals("Ada Lovelace", remembered.contactDisplayName)
        assertEquals("the file's name on this phone is the one kept", "Bell.mp3", remembered.soundFileName)
    }

    @Test
    fun `a restore without contacts access changes nothing`() = runTest {
        writer.allowed = false
        writer.add("key-ada", "Ada Lovelace")

        val results = manager.restore(listOf(contactRingtone()))

        assertEquals(ContactRingtoneRestoreStatus.PERMISSION_MISSING, results.single().status)
        assertTrue(manager.exportEntries().isEmpty())
    }

    private companion object {
        const val SAVED_URI = "content://media/external/audio/media/41"
    }
}
