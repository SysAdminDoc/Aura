package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matching rules and the shape of a backup row, with no contacts provider involved. A restore
 * writes to the user's contacts, so the rule "one contact or none" is pinned from every side.
 */
class ContactRingtoneBackupTest {

    private val writer = FakeContactRingtoneWriter()
    private val sounds = FakeContactRingtoneSounds().apply { onPhone["fs_100"] = BELL_URI }
    private val moshi = Moshi.Builder().build()

    // -- matching --

    @Test
    fun `a contact with the same lookup key gets its ringtone back`() = runTest {
        val ada = writer.add("key-ada", "Ada Lovelace")
        val other = writer.add("key-bob", "Bob")

        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.RESTORED, result.status)
        assertEquals(ContactRingtoneMatch.LOOKUP_KEY, result.matchedBy)
        assertEquals(BELL_URI, result.ringtoneUri)
        assertEquals(BELL_URI, ada.ringtone)
        assertNull(other.ringtone)
    }

    @Test
    fun `a changed lookup key falls back to the display name`() = runTest {
        val ada = writer.add("key-from-the-new-phone", "Ada Lovelace")

        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.RESTORED, result.status)
        assertEquals(ContactRingtoneMatch.DISPLAY_NAME, result.matchedBy)
        assertEquals(BELL_URI, ada.ringtone)
    }

    @Test
    fun `the display name has to match exactly`() = runTest {
        val close = writer.add("key-other", "ada lovelace")

        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.MISSING_CONTACT, result.status)
        assertNull(close.ringtone)
    }

    @Test
    fun `a missing contact is reported and the rest still restore`() = runTest {
        val bob = writer.add("key-bob", "Bob")

        val results = restoreContactRingtones(
            listOf(contactRingtone(), contactRingtone(lookupKey = "key-bob", name = "Bob")),
            writer,
            sounds,
        )

        assertEquals(
            listOf(ContactRingtoneRestoreStatus.MISSING_CONTACT, ContactRingtoneRestoreStatus.RESTORED),
            results.map { it.status },
        )
        assertNull(results.first().matchedBy)
        assertEquals("Ada Lovelace", results.first().contactLabel)
        assertEquals("Bell", results.first().soundLabel)
        assertEquals(BELL_URI, bob.ringtone)
    }

    @Test
    fun `two contacts with the same name are left alone and reported`() = runTest {
        val first = writer.add("key-one", "Ada Lovelace")
        val second = writer.add("key-two", "Ada Lovelace")

        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.AMBIGUOUS_CONTACT, result.status)
        assertNull(first.ringtone)
        assertNull(second.ringtone)
    }

    @Test
    fun `the lookup key settles it when the name is shared`() = runTest {
        val backedUp = writer.add("key-ada", "Ada Lovelace")
        val namesake = writer.add("key-two", "Ada Lovelace")

        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneMatch.LOOKUP_KEY, result.matchedBy)
        assertEquals(BELL_URI, backedUp.ringtone)
        assertNull(namesake.ringtone)
    }

    @Test
    fun `a row with no name and a key nobody has is a missing contact`() = runTest {
        writer.add("key-bob", "Bob")

        val result = restoreContactRingtones(listOf(contactRingtone(name = "")), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.MISSING_CONTACT, result.status)
        assertEquals("key-ada", result.contactLabel)
    }

    // -- sounds --

    @Test
    fun `a missing sound is reported and the contact keeps what it had`() = runTest {
        val ada = writer.add("key-ada", "Ada Lovelace").apply { ringtone = "content://settings/system/ringtone" }
        val bob = writer.add("key-bob", "Bob")

        val results = restoreContactRingtones(
            listOf(
                contactRingtone(soundId = "fs_gone", soundTitle = "Gone"),
                contactRingtone(lookupKey = "key-bob", name = "Bob"),
            ),
            writer,
            sounds,
        )

        assertEquals(
            listOf(ContactRingtoneRestoreStatus.MISSING_SOUND, ContactRingtoneRestoreStatus.RESTORED),
            results.map { it.status },
        )
        assertEquals("content://settings/system/ringtone", ada.ringtone)
        assertEquals(BELL_URI, bob.ringtone)
        assertEquals("only the contact with a sound is written to", 1, writer.writeAttempts)
    }

    @Test
    fun `a source that throws counts as a missing sound`() = runTest {
        writer.add("key-ada", "Ada Lovelace")
        sounds.failResolve = true

        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.MISSING_SOUND, result.status)
    }

    @Test
    fun `one sound shared by two contacts is looked up once`() = runTest {
        writer.add("key-ada", "Ada Lovelace")
        writer.add("key-bob", "Bob")

        val results = restoreContactRingtones(
            listOf(contactRingtone(), contactRingtone(lookupKey = "key-bob", name = "Bob")),
            writer,
            sounds,
        )

        assertTrue(results.all { it.restored })
        assertEquals(listOf("fs_100"), sounds.resolved)
    }

    @Test
    fun `nothing is looked up or downloaded for a contact that isn't on this phone`() = runTest {
        sounds.fetchable += "fs_200"

        val result = restoreContactRingtones(
            listOf(contactRingtone(soundId = "fs_200")),
            writer,
            sounds,
        ).single()

        assertEquals(ContactRingtoneRestoreStatus.MISSING_CONTACT, result.status)
        assertTrue("the sound was never asked for", sounds.resolved.isEmpty())
        assertTrue(sounds.fetched.isEmpty())
        assertTrue(sounds.discarded.isEmpty())
        assertEquals(0, writer.writeAttempts)
    }

    @Test
    fun `a sound fetched for a name two contacts share is removed again`() = runTest {
        val first = writer.add("key-one", "Ada Lovelace")
        val second = writer.add("key-two", "Ada Lovelace")
        sounds.fetchable += "fs_200"

        val result = restoreContactRingtones(
            listOf(contactRingtone(soundId = "fs_200")),
            writer,
            sounds,
        ).single()

        assertEquals(ContactRingtoneRestoreStatus.AMBIGUOUS_CONTACT, result.status)
        assertEquals(listOf(FakeContactRingtoneSounds.fetchedUri("fs_200")), sounds.discarded)
        assertNull(first.ringtone)
        assertNull(second.ringtone)
    }

    @Test
    fun `a contacts app that gives the probe no answer leaves it to the write`() = runTest {
        val ada = writer.add("key-ada", "Ada Lovelace")
        writer.probesAnswer = false

        val results = restoreContactRingtones(
            listOf(contactRingtone(), contactRingtone(lookupKey = "key-gone", name = "Nobody Here")),
            writer,
            sounds,
        )

        assertEquals(
            listOf(ContactRingtoneRestoreStatus.RESTORED, ContactRingtoneRestoreStatus.MISSING_CONTACT),
            results.map { it.status },
        )
        assertEquals(BELL_URI, ada.ringtone)
    }

    // -- how much one restore may download --

    @Test
    fun `one restore downloads only so many sounds and reports the rest`() = runTest {
        val limit = LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOADS
        val entries = downloadableEntries(limit + 3)

        val results = restoreContactRingtones(entries, writer, sounds)

        assertEquals(limit, results.count { it.restored })
        assertEquals(
            List(3) { ContactRingtoneRestoreStatus.DOWNLOAD_LIMIT },
            results.drop(limit).map { it.status },
        )
        assertEquals(limit, sounds.fetched.size)
        assertEquals("no contact past the limit was changed", limit, writer.contacts.count { it.ringtone != null })
    }

    @Test
    fun `a download that fails still counts toward the limit`() = runTest {
        val limit = LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOADS
        val entries = downloadableEntries(limit + 1)
        sounds.fetchable.clear()

        val results = restoreContactRingtones(entries, writer, sounds)

        assertEquals(
            List(limit) { ContactRingtoneRestoreStatus.MISSING_SOUND } + ContactRingtoneRestoreStatus.DOWNLOAD_LIMIT,
            results.map { it.status },
        )
        assertEquals("only so many sources were tried", limit, sounds.allowances.count { it > 0 })
    }

    @Test
    fun `one restore stops downloading once its bytes are spent`() = runTest {
        val budget = LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOAD_BYTES
        sounds.fetchedBytes = budget / 2
        val entries = downloadableEntries(3)

        val results = restoreContactRingtones(entries, writer, sounds)

        assertEquals(
            listOf(
                ContactRingtoneRestoreStatus.RESTORED,
                ContactRingtoneRestoreStatus.RESTORED,
                ContactRingtoneRestoreStatus.DOWNLOAD_LIMIT,
            ),
            results.map { it.status },
        )
        assertEquals("each download may only be as big as what is left", listOf(budget, budget / 2, 0L), sounds.allowances)
    }

    @Test
    fun `a sound already on this phone is used after the download limit is reached`() = runTest {
        val limit = LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOADS
        val ada = writer.add("key-ada", "Ada Lovelace")
        val entries = downloadableEntries(limit) + contactRingtone(assignedAt = 9_000)

        val results = restoreContactRingtones(entries, writer, sounds)

        assertTrue(results.all { it.restored })
        assertEquals(BELL_URI, ada.ringtone)
    }

    // -- Wi-Fi only --

    @Test
    fun `while waiting for Wi-Fi a sound is not downloaded but one already here is used`() = runTest {
        val ada = writer.add("key-ada", "Ada Lovelace")
        val bob = writer.add("key-bob", "Bob")
        sounds.fetchable += "fs_200"

        val results = restoreContactRingtones(
            listOf(
                contactRingtone(),
                contactRingtone(lookupKey = "key-bob", name = "Bob", soundId = "fs_200", soundTitle = "Chime", soundFileName = ""),
            ),
            writer,
            sounds,
            waitingForWifi = { true },
        )

        assertEquals(
            listOf(ContactRingtoneRestoreStatus.RESTORED, ContactRingtoneRestoreStatus.WAITING_FOR_WIFI),
            results.map { it.status },
        )
        assertEquals(BELL_URI, ada.ringtone)
        assertNull(bob.ringtone)
        assertTrue(sounds.fetched.isEmpty())
        assertEquals(listOf(0L, 0L), sounds.allowances)
    }

    @Test
    fun `Aura's own tones come back while waiting for Wi-Fi because they need no download`() = runTest {
        val ada = writer.add("key-ada", "Ada Lovelace")
        sounds.fetchable += "bundled_ringtone_01"

        val result = restoreContactRingtones(
            listOf(contactRingtone(soundId = "bundled_ringtone_01", soundSource = "BUNDLED", soundFileName = "")),
            writer,
            sounds,
            waitingForWifi = { true },
        ).single()

        assertEquals(ContactRingtoneRestoreStatus.RESTORED, result.status)
        assertEquals(FakeContactRingtoneSounds.fetchedUri("bundled_ringtone_01"), ada.ringtone)
    }

    @Test
    fun `a sound that only lived on the old phone is a missing sound whatever the connection`() = runTest {
        writer.add("key-ada", "Ada Lovelace")

        val result = restoreContactRingtones(
            listOf(contactRingtone(soundId = "", soundSource = "LOCAL", soundTitle = "Voice memo", soundFileName = "Voice_memo.m4a")),
            writer,
            sounds,
            waitingForWifi = { true },
        ).single()

        assertEquals(ContactRingtoneRestoreStatus.MISSING_SOUND, result.status)
        assertEquals("there is nothing to download for it", listOf(0L), sounds.allowances)
    }

    @Test
    fun `a fetched sound that a contact uses is kept`() = runTest {
        val ada = writer.add("key-ada", "Ada Lovelace")
        sounds.fetchable += "fs_200"

        restoreContactRingtones(
            listOf(
                contactRingtone(soundId = "fs_200"),
                contactRingtone(lookupKey = "key-nobody", name = "Nobody", soundId = "fs_200"),
            ),
            writer,
            sounds,
        )

        assertEquals(FakeContactRingtoneSounds.fetchedUri("fs_200"), ada.ringtone)
        assertTrue(sounds.discarded.isEmpty())
    }

    @Test
    fun `a ringtone that was already on the phone is never removed`() = runTest {
        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.MISSING_CONTACT, result.status)
        assertTrue(sounds.discarded.isEmpty())
    }

    // -- permission and failures --

    @Test
    fun `without contacts access every row is reported and nothing is fetched`() = runTest {
        writer.allowed = false
        val ada = writer.add("key-ada", "Ada Lovelace")
        sounds.fetchable += "fs_200"

        val results = restoreContactRingtones(
            listOf(contactRingtone(), contactRingtone(lookupKey = "key-bob", name = "Bob", soundId = "fs_200")),
            writer,
            sounds,
        )

        assertEquals(2, results.size)
        assertTrue(results.all { it.status == ContactRingtoneRestoreStatus.PERMISSION_MISSING })
        assertNull(ada.ringtone)
        assertTrue(sounds.resolved.isEmpty())
        assertEquals(0, writer.writeAttempts)
    }

    @Test
    fun `a write the contacts app refuses is reported as failed`() = runTest {
        val ada = writer.add("key-ada", "Ada Lovelace")
        writer.failWrites = true

        val result = restoreContactRingtones(listOf(contactRingtone()), writer, sounds).single()

        assertEquals(ContactRingtoneRestoreStatus.WRITE_FAILED, result.status)
        assertNull(ada.ringtone)
    }

    @Test
    fun `an empty backup section does nothing`() = runTest {
        assertTrue(restoreContactRingtones(emptyList(), writer, sounds).isEmpty())
        assertEquals(0, writer.writeAttempts)
    }

    // -- the shape of a row --

    @Test
    fun `a row names the contact and the sound and carries no row id or address on the phone`() {
        val entry = contactRingtoneEntry(
            lookupKey = "0r7-2F4A",
            displayName = "Ada Lovelace",
            sound = Sound(
                id = "fs_100",
                source = ContentSource.FREESOUND,
                name = "Bell",
                previewUrl = "https://sounds.example/preview.mp3",
                downloadUrl = "https://sounds.example/bell.mp3",
                license = "CC0",
                uploaderName = "someone",
                sourcePageUrl = "https://sounds.example/page/100",
            ),
            fileName = "Bell.mp3",
            assignedAt = 42,
        )!!

        assertEquals("0r7-2F4A", entry.contactLookupKey)
        assertEquals("Ada Lovelace", entry.contactDisplayName)
        assertEquals("FREESOUND", entry.soundSource)
        assertEquals("fs_100", entry.soundId)
        assertEquals("Bell", entry.soundTitle)
        assertEquals("Bell.mp3", entry.soundFileName)
        assertEquals("https://sounds.example/bell.mp3", entry.soundUrl)
        assertEquals("CC0", entry.soundLicense)

        val json = moshi.adapter(ContactRingtoneExportEntry::class.java).toJson(entry)
        assertEquals(
            setOf(
                "contactLookupKey", "contactDisplayName", "soundSource", "soundId", "soundTitle",
                "soundFileName", "soundUrl", "soundPageUrl", "soundLicense", "soundUploader", "assignedAt",
            ),
            Regex("\"(\\w+)\":").findAll(json).map { it.groupValues[1] }.toSet(),
        )
        assertFalse(json.contains("\"_id\""))
        assertFalse(json.contains("contactId", ignoreCase = true))
        assertFalse(json.contains("content://"))
    }

    @Test
    fun `a sound picked from the phone is named by title and file only`() {
        val entry = contactRingtoneEntry(
            lookupKey = "key-ada",
            displayName = "Ada Lovelace",
            sound = Sound(
                id = "content://media/external/audio/media/918",
                source = ContentSource.LOCAL,
                name = "Voice memo",
                previewUrl = "content://media/external/audio/media/918",
                downloadUrl = "content://media/external/audio/media/918",
            ),
            fileName = "Voice_memo.m4a",
            assignedAt = 42,
        )!!

        assertEquals("", entry.soundId)
        assertEquals("", entry.soundUrl)
        assertEquals("Voice memo", entry.soundTitle)
        assertEquals("Voice_memo.m4a", entry.soundFileName)
        assertNull("there is nothing to fetch for it on another phone", entry.toSoundOrNull())
        assertFalse(moshi.adapter(ContactRingtoneExportEntry::class.java).toJson(entry).contains("918"))
    }

    @Test
    fun `addresses that expire or live in the app are left out`() {
        listOf(ContentSource.YOUTUBE to "yt_abc123", ContentSource.TIKTOK to "tt_77", ContentSource.BUNDLED to "bundled_ringtone_01")
            .forEach { (source, id) ->
                val entry = contactRingtoneEntry(
                    lookupKey = "key-ada",
                    displayName = "Ada Lovelace",
                    sound = Sound(
                        id = id,
                        source = source,
                        name = "Tone",
                        previewUrl = "https://cdn.example/expires-soon.m4a",
                        downloadUrl = "https://cdn.example/expires-soon.m4a",
                        sourcePageUrl = "https://source.example/watch",
                    ),
                    fileName = "Tone.m4a",
                    assignedAt = 1,
                )!!

                assertEquals(id, entry.soundId)
                assertEquals("$source keeps no direct address", "", entry.soundUrl)
                assertEquals("https://source.example/watch", entry.soundPageUrl)
                assertNotNull(entry.toSoundOrNull())
            }
    }

    @Test
    fun `a row with no contact or no sound is refused`() {
        assertNull(normalizeImportedContactRingtone(contactRingtone(lookupKey = " ", name = "")))
        assertNull(
            normalizeImportedContactRingtone(
                contactRingtone(soundId = "", soundTitle = " ", soundFileName = ""),
            ),
        )
        assertNotNull(normalizeImportedContactRingtone(contactRingtone(lookupKey = "", name = "Ada Lovelace")))
        assertNotNull(normalizeImportedContactRingtone(contactRingtone(name = "")))
    }

    @Test
    fun `an imported row is cleaned of anything that points into a phone`() {
        val cleaned = normalizeImportedContactRingtone(
            ContactRingtoneExportEntry(
                contactLookupKey = "  key-ada  ",
                contactDisplayName = "  Ada Lovelace ",
                soundSource = "freesound",
                soundId = "content://media/external/audio/media/5",
                soundTitle = "Bell",
                soundFileName = "/storage/emulated/0/Ringtones/Bell.mp3",
                soundUrl = "file:///sdcard/Bell.mp3",
                soundPageUrl = "http://insecure.example/page",
                assignedAt = -5,
            ),
        )!!

        assertEquals("key-ada", cleaned.contactLookupKey)
        assertEquals("Ada Lovelace", cleaned.contactDisplayName)
        assertEquals("FREESOUND", cleaned.soundSource)
        assertEquals("", cleaned.soundId)
        assertEquals("", cleaned.soundFileName)
        assertEquals("", cleaned.soundUrl)
        assertEquals("", cleaned.soundPageUrl)
        assertEquals(0L, cleaned.assignedAt)
    }

    @Test
    fun `source ids are kept and device addresses are not`() {
        assertEquals("yt_abc-123", portableContactRingtoneSoundId("YOUTUBE", " yt_abc-123 "))
        assertEquals("", portableContactRingtoneSoundId("LOCAL", "anything"))
        assertEquals("", portableContactRingtoneSoundId("", "fs_1"))
        assertEquals("", portableContactRingtoneSoundId("FREESOUND", "content://media/external/audio/media/5"))
        assertEquals("", portableContactRingtoneSoundId("FREESOUND", "file:///sdcard/a.mp3"))
        assertEquals("", portableContactRingtoneSoundId("FREESOUND", "/sdcard/a.mp3"))
        assertEquals("", portableContactRingtoneSoundId("FREESOUND", "C:\\music\\a.mp3"))
        assertEquals("", portableContactRingtoneSoundId("FREESOUND", "has space"))
    }

    @Test
    fun `file names are bare names`() {
        assertEquals("Bell (1).mp3", normalizeImportedFileName(" Bell (1).mp3 "))
        assertEquals("", normalizeImportedFileName("Ringtones/Bell.mp3"))
        assertEquals("", normalizeImportedFileName("..\\Bell.mp3"))
        assertEquals("", normalizeImportedFileName("content:Bell.mp3"))
        assertEquals("", normalizeImportedFileName(".."))
        assertEquals("", normalizeImportedFileName("a".repeat(256)))
    }

    // -- the ledger --

    @Test
    fun `a newer ringtone for the same contact replaces the older one`() {
        val merged = mergeContactRingtones(
            existing = listOf(contactRingtone(), contactRingtone(lookupKey = "key-bob", name = "Bob")),
            incoming = listOf(contactRingtone(soundId = "fs_300", soundTitle = "Chime", assignedAt = 2_000)),
        )

        assertEquals(listOf("Bob", "Ada Lovelace"), merged.map { it.contactDisplayName })
        assertEquals("fs_300", merged.last().soundId)
    }

    @Test
    fun `contacts without a shared key are told apart by name`() {
        val merged = mergeContactRingtones(
            existing = listOf(contactRingtone(lookupKey = "", name = "Ada Lovelace")),
            incoming = listOf(
                contactRingtone(lookupKey = "key-ada", name = "Ada Lovelace", soundId = "fs_300"),
                contactRingtone(lookupKey = "", name = "Bob"),
            ),
        )

        assertEquals(listOf("fs_300", "fs_100"), merged.map { it.soundId })
        assertEquals(listOf("Ada Lovelace", "Bob"), merged.map { it.contactDisplayName })
    }

    @Test
    fun `two different lookup keys stay two contacts even with one name`() {
        val merged = mergeContactRingtones(
            existing = listOf(contactRingtone(lookupKey = "key-one")),
            incoming = listOf(contactRingtone(lookupKey = "key-two")),
        )

        assertEquals(listOf("key-one", "key-two"), merged.map { it.contactLookupKey })
    }

    @Test
    fun `the ledger survives a round trip and shrugs off a broken value`() {
        val ledger = ContactRingtoneLedger(assignments = listOf(contactRingtone()))

        val parsed = parseContactRingtoneLedger(moshi, encodeContactRingtoneLedger(moshi, ledger))

        assertEquals(ledger, parsed)
        assertTrue(parseContactRingtoneLedger(moshi, "").assignments.isEmpty())
        assertTrue(parseContactRingtoneLedger(moshi, "{not json").assignments.isEmpty())
        assertTrue(parseContactRingtoneLedger(moshi, """{"assignments":[{"soundTitle":"orphan"}]}""").assignments.isEmpty())
    }

    /** [count] contacts that are on this phone, each with a sound of its own that has to be downloaded. */
    private fun downloadableEntries(count: Int): List<ContactRingtoneExportEntry> = (0 until count).map { index ->
        writer.add("key-$index", "Contact $index")
        sounds.fetchable += "fs_new_$index"
        contactRingtone(
            lookupKey = "key-$index",
            name = "Contact $index",
            soundId = "fs_new_$index",
            soundTitle = "Sound $index",
            soundFileName = "",
            assignedAt = index.toLong(),
        )
    }

    private companion object {
        const val BELL_URI = "content://media/external/audio/media/bell"
    }
}
