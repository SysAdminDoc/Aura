package com.freevibe.service

/**
 * A contact list that behaves like the real write-only path: a write lands on the one contact that
 * matches, and when none or several match nothing changes.
 */
internal class FakeContactRingtoneWriter(var allowed: Boolean = true) : ContactRingtoneWriter {

    class Contact(val lookupKey: String, val name: String, var ringtone: String? = null)

    val contacts = mutableListOf<Contact>()
    var failWrites = false
    var writeAttempts = 0

    fun add(lookupKey: String, name: String): Contact = Contact(lookupKey, name).also { contacts += it }

    override fun canWrite(): Boolean = allowed

    override suspend fun setByLookupKey(lookupKey: String, ringtoneUri: String): ContactRingtoneWrite =
        setOnTheOnlyMatch(ringtoneUri) { it.lookupKey == lookupKey }

    override suspend fun setByDisplayName(displayName: String, ringtoneUri: String): ContactRingtoneWrite =
        setOnTheOnlyMatch(ringtoneUri) { it.name == displayName }

    private fun setOnTheOnlyMatch(ringtoneUri: String, matches: (Contact) -> Boolean): ContactRingtoneWrite {
        writeAttempts++
        if (!allowed) return ContactRingtoneWrite.NOT_ALLOWED
        if (failWrites) return ContactRingtoneWrite.FAILED
        val matched = contacts.filter(matches)
        return when (matched.size) {
            0 -> ContactRingtoneWrite.NO_MATCH
            1 -> {
                matched.single().ringtone = ringtoneUri
                ContactRingtoneWrite.APPLIED
            }
            else -> ContactRingtoneWrite.AMBIGUOUS
        }
    }
}

/** Sounds this phone already has, plus the ones it could fetch again. Anything else is missing. */
internal class FakeContactRingtoneSounds : ContactRingtoneSoundResolver {

    /** Sound id to the ringtone already saved on this phone. */
    val onPhone = mutableMapOf<String, String>()

    /** Sound ids that can be downloaded again. */
    val fetchable = mutableSetOf<String>()
    val fileNames = mutableMapOf<String, String>()
    val resolved = mutableListOf<String>()
    val discarded = mutableListOf<String>()
    var failResolve = false

    override suspend fun resolve(entry: ContactRingtoneExportEntry): ResolvedContactRingtone? {
        resolved += entry.soundId
        if (failResolve) throw IllegalStateException("source is down")
        onPhone[entry.soundId]?.let { return ResolvedContactRingtone(it, savedNow = false) }
        if (entry.soundId in fetchable) return ResolvedContactRingtone(fetchedUri(entry.soundId), savedNow = true)
        return null
    }

    override suspend fun discard(ringtoneUri: String) {
        discarded += ringtoneUri
    }

    override suspend fun fileNameOf(ringtoneUri: String): String = fileNames[ringtoneUri].orEmpty()

    companion object {
        fun fetchedUri(soundId: String): String = "content://media/external/audio/media/fetched-$soundId"
    }
}

internal fun contactRingtone(
    lookupKey: String = "key-ada",
    name: String = "Ada Lovelace",
    soundId: String = "fs_100",
    soundSource: String = "FREESOUND",
    soundTitle: String = "Bell",
    soundFileName: String = "Bell.mp3",
    assignedAt: Long = 1_000,
): ContactRingtoneExportEntry = ContactRingtoneExportEntry(
    contactLookupKey = lookupKey,
    contactDisplayName = name,
    soundSource = soundSource,
    soundId = soundId,
    soundTitle = soundTitle,
    soundFileName = soundFileName,
    soundUrl = "https://sounds.example/$soundId.mp3",
    soundPageUrl = "https://sounds.example/page/$soundId",
    soundLicense = "CC0",
    soundUploader = "someone",
    assignedAt = assignedAt,
)
