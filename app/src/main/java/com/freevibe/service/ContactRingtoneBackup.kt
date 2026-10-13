package com.freevibe.service

import android.util.Log
import com.freevibe.data.local.PreferencesManager
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import com.freevibe.util.rethrowIfCancelled
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

internal const val CONTACT_RINGTONE_LEDGER_VERSION = 1

/**
 * One contact ringtone Aura set, in the shape a library backup carries.
 *
 * The contact is named by its lookup key and display name and the sound by where it came from. A
 * contact row id or a media row id means nothing on another phone, so neither is ever a field here.
 */
@JsonClass(generateAdapter = true)
data class ContactRingtoneExportEntry(
    val contactLookupKey: String = "",
    val contactDisplayName: String = "",
    val soundSource: String = "",
    val soundId: String = "",
    val soundTitle: String = "",
    val soundFileName: String = "",
    val soundUrl: String = "",
    val soundPageUrl: String = "",
    val soundLicense: String = "",
    val soundUploader: String = "",
    val assignedAt: Long = 0,
)

/**
 * Every contact ringtone Aura has set on this phone, newest last.
 *
 * Aura can't list contacts (it never asks to read them), so this record of its own writes is the
 * only place a backup can learn which contact got which sound.
 */
@JsonClass(generateAdapter = true)
data class ContactRingtoneLedger(
    val version: Int = CONTACT_RINGTONE_LEDGER_VERSION,
    val assignments: List<ContactRingtoneExportEntry> = emptyList(),
)

/** How one write to "the single contact that matches" ended. */
enum class ContactRingtoneWrite {
    APPLIED,

    /** No contact matched, and nothing was changed. */
    NO_MATCH,

    /** More than one contact matched, and nothing was changed. */
    AMBIGUOUS,

    /** Aura doesn't hold the permission to update contacts. */
    NOT_ALLOWED,

    FAILED,
}

/**
 * Sets a ringtone on the one contact that matches, without ever reading the contact list.
 *
 * Each call either changes exactly one contact or changes none.
 */
interface ContactRingtoneWriter {
    fun canWrite(): Boolean

    suspend fun setByLookupKey(lookupKey: String, ringtoneUri: String): ContactRingtoneWrite

    /** Exact, case-sensitive name match. Two contacts with the same name are [ContactRingtoneWrite.AMBIGUOUS]. */
    suspend fun setByDisplayName(displayName: String, ringtoneUri: String): ContactRingtoneWrite
}

/** A ringtone that is on this phone now. [savedNow] is true when the restore had to fetch it. */
data class ResolvedContactRingtone(
    val uri: String,
    val savedNow: Boolean,
)

/** Finds a backed-up sound on this phone, or gets it again from where it came from. */
interface ContactRingtoneSoundResolver {
    /** A ringtone that works on this phone, or null when the sound can't be found or fetched. */
    suspend fun resolve(entry: ContactRingtoneExportEntry): ResolvedContactRingtone?

    /** Removes a ringtone file [resolve] just saved that no contact ended up using. */
    suspend fun discard(ringtoneUri: String)

    /** The file name the saved ringtone goes by, or blank when it can't be read. */
    suspend fun fileNameOf(ringtoneUri: String): String
}

enum class ContactRingtoneRestoreStatus {
    RESTORED,

    /** Neither the lookup key nor the name found a contact. */
    MISSING_CONTACT,

    /** The lookup key found nobody and more than one contact has the name. */
    AMBIGUOUS_CONTACT,

    /** The sound isn't on this phone and couldn't be fetched again. */
    MISSING_SOUND,

    /** Aura isn't allowed to update contacts. */
    PERMISSION_MISSING,

    /** The contacts app refused the change. */
    WRITE_FAILED,
}

enum class ContactRingtoneMatch { LOOKUP_KEY, DISPLAY_NAME }

/** What happened to one contact ringtone from a backup. Every row in the backup gets one. */
data class ContactRingtoneRestoreResult(
    val entry: ContactRingtoneExportEntry,
    val status: ContactRingtoneRestoreStatus,
    val matchedBy: ContactRingtoneMatch? = null,
    /** Where the ringtone lives on this phone. Kept in memory only and never written to a backup. */
    val ringtoneUri: String? = null,
) {
    val restored: Boolean get() = status == ContactRingtoneRestoreStatus.RESTORED

    /** The contact as the report names it. */
    val contactLabel: String get() = entry.contactDisplayName.ifBlank { entry.contactLookupKey }

    /** The sound as the report names it. Blank when the backup only knew the sound by its id. */
    val soundLabel: String get() = entry.soundTitle.ifBlank { entry.soundFileName }
}

/** The backup row for a ringtone Aura just set, or null when it names no contact or no sound. */
internal fun contactRingtoneEntry(
    lookupKey: String,
    displayName: String,
    sound: Sound,
    fileName: String,
    assignedAt: Long,
): ContactRingtoneExportEntry? = normalizeImportedContactRingtone(
    ContactRingtoneExportEntry(
        contactLookupKey = lookupKey,
        contactDisplayName = displayName,
        soundSource = sound.source.name,
        soundId = sound.id,
        soundTitle = sound.name,
        soundFileName = fileName,
        soundUrl = sound.downloadUrl.ifBlank { sound.previewUrl },
        soundPageUrl = sound.sourcePageUrl,
        soundLicense = sound.license,
        soundUploader = sound.uploaderName,
        assignedAt = assignedAt,
    ),
)

/** The sound to fetch again, or null when the row only names a file that lived on the old phone. */
internal fun ContactRingtoneExportEntry.toSoundOrNull(): Sound? {
    val source = runCatching { ContentSource.valueOf(soundSource) }.getOrNull() ?: return null
    if (soundId.isBlank() || source == ContentSource.LOCAL) return null
    return Sound(
        id = soundId,
        source = source,
        name = soundTitle.ifBlank { soundId },
        previewUrl = soundUrl,
        downloadUrl = soundUrl,
        license = soundLicense,
        uploaderName = soundUploader,
        sourcePageUrl = soundPageUrl,
    )
}

/** True when both rows are about the same contact, so the newer one replaces the older. */
internal fun ContactRingtoneExportEntry.sameContactAs(other: ContactRingtoneExportEntry): Boolean =
    if (contactLookupKey.isNotBlank() && other.contactLookupKey.isNotBlank()) {
        contactLookupKey == other.contactLookupKey
    } else {
        contactDisplayName.isNotBlank() && contactDisplayName == other.contactDisplayName
    }

private fun ContactRingtoneExportEntry.soundKey(): String =
    listOf(soundSource, soundId, soundFileName, soundTitle).joinToString("\u001f")

/** Reads the stored ledger. A blank or unreadable value is an empty ledger, never a crash. */
internal fun parseContactRingtoneLedger(moshi: Moshi, raw: String): ContactRingtoneLedger {
    if (raw.isBlank()) return ContactRingtoneLedger()
    val parsed = runCatching { moshi.adapter(ContactRingtoneLedger::class.java).fromJson(raw) }.getOrNull()
        ?: return ContactRingtoneLedger()
    return ContactRingtoneLedger(assignments = mergeContactRingtones(emptyList(), parsed.assignments))
}

internal fun encodeContactRingtoneLedger(moshi: Moshi, ledger: ContactRingtoneLedger): String =
    moshi.adapter(ContactRingtoneLedger::class.java).toJson(ledger)

/**
 * Adds [incoming] to [existing] with one row per contact. A later row for the same contact replaces
 * the earlier one, and the oldest rows drop off once the backup limit is reached.
 */
internal fun mergeContactRingtones(
    existing: List<ContactRingtoneExportEntry>,
    incoming: List<ContactRingtoneExportEntry>,
): List<ContactRingtoneExportEntry> {
    val merged = mutableListOf<ContactRingtoneExportEntry>()
    (existing + incoming).forEach { candidate ->
        val entry = normalizeImportedContactRingtone(candidate) ?: return@forEach
        merged.removeAll { it.sameContactAs(entry) }
        merged += entry
    }
    return merged.takeLast(LibraryTransferContract.MAX_CONTACT_RINGTONES)
}

/**
 * Puts backed-up contact ringtones back, one contact at a time.
 *
 * A contact is matched by lookup key first. When that key finds nobody (a new phone, or a contact
 * that was merged since), the exact display name is tried, and only when one contact has it. A row
 * that can't be matched or whose sound is gone is reported and the rest carry on.
 */
internal suspend fun restoreContactRingtones(
    entries: List<ContactRingtoneExportEntry>,
    writer: ContactRingtoneWriter,
    sounds: ContactRingtoneSoundResolver,
): List<ContactRingtoneRestoreResult> {
    if (entries.isEmpty()) return emptyList()
    if (!writer.canWrite()) {
        return entries.map { ContactRingtoneRestoreResult(it, ContactRingtoneRestoreStatus.PERMISSION_MISSING) }
    }
    // Several contacts often share one sound, so each sound is looked up or fetched once.
    val resolved = mutableMapOf<String, ResolvedContactRingtone?>()
    val used = mutableSetOf<String>()
    val results = mutableListOf<ContactRingtoneRestoreResult>()
    for (entry in entries) {
        val key = entry.soundKey()
        if (key !in resolved) resolved[key] = resolveQuietly(sounds, entry)
        val ringtone = resolved[key]
        if (ringtone == null) {
            results += ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.MISSING_SOUND)
            continue
        }
        val result = writeToContact(entry, ringtone.uri, writer)
        if (result.restored) used += ringtone.uri
        results += result
    }
    // A file fetched for a contact that turned out to be missing would only clutter the phone.
    resolved.values.filterNotNull()
        .filter { it.savedNow && it.uri !in used }
        .map { it.uri }
        .distinct()
        .forEach { unused ->
            try {
                sounds.discard(unused)
            } catch (e: Exception) {
                e.rethrowIfCancelled()
            }
        }
    return results
}

private suspend fun resolveQuietly(
    sounds: ContactRingtoneSoundResolver,
    entry: ContactRingtoneExportEntry,
): ResolvedContactRingtone? = try {
    sounds.resolve(entry)?.takeIf { it.uri.isNotBlank() }
} catch (e: Exception) {
    e.rethrowIfCancelled()
    null
}

private suspend fun writeToContact(
    entry: ContactRingtoneExportEntry,
    ringtoneUri: String,
    writer: ContactRingtoneWriter,
): ContactRingtoneRestoreResult {
    if (entry.contactLookupKey.isNotBlank()) {
        when (writeQuietly { writer.setByLookupKey(entry.contactLookupKey, ringtoneUri) }) {
            ContactRingtoneWrite.APPLIED -> return ContactRingtoneRestoreResult(
                entry,
                ContactRingtoneRestoreStatus.RESTORED,
                ContactRingtoneMatch.LOOKUP_KEY,
                ringtoneUri,
            )
            ContactRingtoneWrite.NOT_ALLOWED ->
                return ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.PERMISSION_MISSING)
            // The key no longer finds one contact here, so the name gets its turn.
            ContactRingtoneWrite.NO_MATCH,
            ContactRingtoneWrite.AMBIGUOUS,
            ContactRingtoneWrite.FAILED -> Unit
        }
    }
    if (entry.contactDisplayName.isBlank()) {
        return ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.MISSING_CONTACT)
    }
    return when (writeQuietly { writer.setByDisplayName(entry.contactDisplayName, ringtoneUri) }) {
        ContactRingtoneWrite.APPLIED -> ContactRingtoneRestoreResult(
            entry,
            ContactRingtoneRestoreStatus.RESTORED,
            ContactRingtoneMatch.DISPLAY_NAME,
            ringtoneUri,
        )
        ContactRingtoneWrite.NO_MATCH ->
            ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.MISSING_CONTACT)
        ContactRingtoneWrite.AMBIGUOUS ->
            ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.AMBIGUOUS_CONTACT)
        ContactRingtoneWrite.NOT_ALLOWED ->
            ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.PERMISSION_MISSING)
        ContactRingtoneWrite.FAILED ->
            ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.WRITE_FAILED)
    }
}

private suspend fun writeQuietly(write: suspend () -> ContactRingtoneWrite): ContactRingtoneWrite = try {
    write()
} catch (e: Exception) {
    e.rethrowIfCancelled()
    ContactRingtoneWrite.FAILED
}

/** Keeps the ledger of contact ringtones Aura set, and turns it into and back out of a backup. */
@Singleton
class ContactRingtoneBackupManager @Inject constructor(
    private val prefs: PreferencesManager,
    private val moshi: Moshi,
    private val writer: ContactRingtoneWriter,
    private val sounds: ContactRingtoneSoundResolver,
) {
    /** True when a restore could update contacts right now. */
    fun canWriteContacts(): Boolean = writer.canWrite()

    /**
     * Remembers that [contact] now rings with [sound]. Called after the write went through, so a
     * failure here only costs the backup row and is never shown as a failed assignment.
     */
    suspend fun record(contact: ContactInfo, sound: Sound, ringtoneUri: String) {
        try {
            val entry = contactRingtoneEntry(
                lookupKey = contact.lookupKey,
                // A contact with no name is shown under a stand-in that must never be matched on.
                displayName = contact.name.takeUnless { it == UNNAMED_CONTACT }.orEmpty(),
                sound = sound,
                fileName = sounds.fileNameOf(ringtoneUri),
                assignedAt = System.currentTimeMillis(),
            ) ?: return
            remember(listOf(entry))
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            Log.w(TAG, "Couldn't note the contact ringtone for backup: ${e.javaClass.simpleName}")
        }
    }

    /** The rows a backup carries. Read from Aura's own ledger, so no contacts permission is involved. */
    suspend fun exportEntries(): List<ContactRingtoneExportEntry> =
        parseContactRingtoneLedger(moshi, prefs.contactRingtoneLedgerJson.first()).assignments

    /**
     * Restores [entries] and remembers the ones that went through, so the next backup from this phone
     * carries them on. Rows that weren't restored stay out of the ledger: it only lists what is set.
     */
    suspend fun restore(entries: List<ContactRingtoneExportEntry>): List<ContactRingtoneRestoreResult> {
        val results = restoreContactRingtones(entries.sortedBy { it.assignedAt }, writer, sounds)
        try {
            val restored = results.filter { it.restored }.map { result ->
                // The file may have a new name on this phone, and the next restore looks for that one.
                val fileName = result.ringtoneUri?.let { sounds.fileNameOf(it) }.orEmpty()
                if (fileName.isBlank()) result.entry else result.entry.copy(soundFileName = fileName)
            }
            if (restored.isNotEmpty()) remember(restored)
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            Log.w(TAG, "Couldn't note the restored contact ringtones: ${e.javaClass.simpleName}")
        }
        return results
    }

    private suspend fun remember(entries: List<ContactRingtoneExportEntry>) {
        prefs.updateContactRingtoneLedgerJson { raw ->
            val current = parseContactRingtoneLedger(moshi, raw)
            encodeContactRingtoneLedger(
                moshi,
                ContactRingtoneLedger(assignments = mergeContactRingtones(current.assignments, entries)),
            )
        }
    }

    private companion object {
        const val TAG = "ContactRingtoneBackup"
    }
}
