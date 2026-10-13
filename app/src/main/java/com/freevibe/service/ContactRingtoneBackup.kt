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

/** Whether a contact is on this phone, as far as a look that reads nothing can tell. */
enum class ContactRingtoneProbe {
    /** No contact matched. */
    NOBODY,

    /** One contact matched, or several did. Only the write tells which. */
    SOMEBODY,

    /** Aura doesn't hold the permission to update contacts. */
    NOT_ALLOWED,

    /** The contacts app gave no answer, so the write has to find out. */
    UNKNOWN,
}

/**
 * Sets a ringtone on the one contact that matches, without ever reading the contact list.
 *
 * Each call either changes exactly one contact or changes none.
 */
interface ContactRingtoneWriter {
    fun canWrite(): Boolean

    /** Whether any contact has this lookup key. Never changes a contact. */
    suspend fun probeByLookupKey(lookupKey: String): ContactRingtoneProbe

    /** Whether any contact has exactly this name. Never changes a contact. */
    suspend fun probeByDisplayName(displayName: String): ContactRingtoneProbe

    suspend fun setByLookupKey(lookupKey: String, ringtoneUri: String): ContactRingtoneWrite

    /** Exact, case-sensitive name match. Two contacts with the same name are [ContactRingtoneWrite.AMBIGUOUS]. */
    suspend fun setByDisplayName(displayName: String, ringtoneUri: String): ContactRingtoneWrite
}

/**
 * A ringtone that is on this phone now. [savedNow] is true when the restore had to fetch it, and
 * [bytes] is the size of what it fetched (zero for a ringtone that was already here).
 */
data class ResolvedContactRingtone(
    val uri: String,
    val savedNow: Boolean,
    val bytes: Long = 0,
)

/** Finds a backed-up sound on this phone, or gets it again from where it came from. */
interface ContactRingtoneSoundResolver {
    /**
     * A ringtone that works on this phone, or null when the sound can't be found or fetched.
     *
     * A ringtone that is already on this phone is always returned. Anything else is fetched only
     * when [downloadAllowance] is above zero, and the fetched file may be no bigger than that.
     */
    suspend fun resolve(entry: ContactRingtoneExportEntry, downloadAllowance: Long): ResolvedContactRingtone?

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

    /** Wi-Fi only is on and the connection is metered, so the sound wasn't downloaded. */
    WAITING_FOR_WIFI,

    /** This restore had already downloaded as much as one restore may. */
    DOWNLOAD_LIMIT,

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

/** Aura's own tones ship inside the app, so getting one again uses no connection. */
private fun ContactRingtoneExportEntry.needsNetwork(): Boolean = soundSource != ContentSource.BUNDLED.name

/** What looking for one sound came to: the ringtone, or the status its rows are reported under. */
private class SoundLookup(val ringtone: ResolvedContactRingtone?, val missingAs: ContactRingtoneRestoreStatus)

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
 *
 * The contact is looked for before its sound is, so nothing is downloaded for a contact that isn't
 * on this phone. A backup is a file anyone can hand over, so one restore also downloads no more than
 * [LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOADS] sounds and
 * [LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOAD_BYTES] bytes, and none while [waitingForWifi].
 */
internal suspend fun restoreContactRingtones(
    entries: List<ContactRingtoneExportEntry>,
    writer: ContactRingtoneWriter,
    sounds: ContactRingtoneSoundResolver,
    waitingForWifi: suspend () -> Boolean = { false },
): List<ContactRingtoneRestoreResult> {
    if (entries.isEmpty()) return emptyList()
    if (!writer.canWrite()) {
        return entries.map { ContactRingtoneRestoreResult(it, ContactRingtoneRestoreStatus.PERMISSION_MISSING) }
    }
    // Several contacts often share one sound, so each sound is looked up or fetched once.
    val lookups = mutableMapOf<String, SoundLookup>()
    val used = mutableSetOf<String>()
    val results = mutableListOf<ContactRingtoneRestoreResult>()
    var downloads = 0
    var downloadedBytes = 0L
    for (entry in entries) {
        when (findContact(entry, writer)) {
            ContactRingtoneProbe.NOBODY -> {
                results += ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.MISSING_CONTACT)
                continue
            }
            ContactRingtoneProbe.NOT_ALLOWED -> {
                results += ContactRingtoneRestoreResult(entry, ContactRingtoneRestoreStatus.PERMISSION_MISSING)
                continue
            }
            ContactRingtoneProbe.SOMEBODY, ContactRingtoneProbe.UNKNOWN -> Unit
        }
        val key = entry.soundKey()
        val lookup = lookups[key] ?: run {
            val fetchable = entry.toSoundOrNull() != null
            val held = when {
                !fetchable -> null
                downloads >= LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOADS ||
                    downloadedBytes >= LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOAD_BYTES ->
                    ContactRingtoneRestoreStatus.DOWNLOAD_LIMIT
                entry.needsNetwork() && waitingForWifi() -> ContactRingtoneRestoreStatus.WAITING_FOR_WIFI
                else -> null
            }
            val allowance = if (fetchable && held == null) {
                LibraryTransferContract.MAX_CONTACT_RINGTONE_DOWNLOAD_BYTES - downloadedBytes
            } else {
                0L
            }
            val found = resolveQuietly(sounds, entry, allowance)
            if (allowance > 0 && found?.savedNow != false) {
                // A fetch that failed still used the connection, so it counts as one of the downloads.
                downloads++
                downloadedBytes += found?.bytes?.coerceAtLeast(0L) ?: 0L
            }
            SoundLookup(found, held ?: ContactRingtoneRestoreStatus.MISSING_SOUND).also { lookups[key] = it }
        }
        val ringtone = lookup.ringtone
        if (ringtone == null) {
            results += ContactRingtoneRestoreResult(entry, lookup.missingAs)
            continue
        }
        val result = writeToContact(entry, ringtone.uri, writer)
        if (result.restored) used += ringtone.uri
        results += result
    }
    // A file fetched for a contact the write then couldn't settle on would only clutter the phone.
    lookups.values.mapNotNull { it.ringtone }
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
    downloadAllowance: Long,
): ResolvedContactRingtone? = try {
    sounds.resolve(entry, downloadAllowance)?.takeIf { it.uri.isNotBlank() }
} catch (e: Exception) {
    e.rethrowIfCancelled()
    null
}

/**
 * Whether [entry] names a contact on this phone, by the same key-then-name order the write uses.
 * A probe that couldn't answer is never read as "nobody": the write gets to decide instead.
 */
private suspend fun findContact(
    entry: ContactRingtoneExportEntry,
    writer: ContactRingtoneWriter,
): ContactRingtoneProbe {
    var unsure = false
    if (entry.contactLookupKey.isNotBlank()) {
        when (val byKey = probeQuietly { writer.probeByLookupKey(entry.contactLookupKey) }) {
            ContactRingtoneProbe.SOMEBODY, ContactRingtoneProbe.NOT_ALLOWED -> return byKey
            ContactRingtoneProbe.UNKNOWN -> unsure = true
            ContactRingtoneProbe.NOBODY -> Unit
        }
    }
    val byName = if (entry.contactDisplayName.isBlank()) {
        ContactRingtoneProbe.NOBODY
    } else {
        probeQuietly { writer.probeByDisplayName(entry.contactDisplayName) }
    }
    return if (byName == ContactRingtoneProbe.NOBODY && unsure) ContactRingtoneProbe.UNKNOWN else byName
}

private suspend fun probeQuietly(probe: suspend () -> ContactRingtoneProbe): ContactRingtoneProbe = try {
    probe()
} catch (e: Exception) {
    e.rethrowIfCancelled()
    ContactRingtoneProbe.UNKNOWN
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
    private val networkGate: RotationNetworkGate,
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
     *
     * A sound that has to be downloaded follows the Wi-Fi only setting, the same one rotation obeys.
     */
    suspend fun restore(entries: List<ContactRingtoneExportEntry>): List<ContactRingtoneRestoreResult> {
        val results = restoreContactRingtones(
            entries.sortedBy { it.assignedAt },
            writer,
            sounds,
            waitingForWifi = {
                networkGate.access(prefs.autoWallpaperRequiresWiFiOnly.first()) ==
                    RotationNetworkAccess.WAITING_FOR_WIFI
            },
        )
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
