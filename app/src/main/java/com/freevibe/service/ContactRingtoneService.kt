package com.freevibe.service

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.OperationApplicationException
import android.content.pm.PackageManager
import android.app.NotificationManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shown for a contact that has no name. It is a stand-in, so a backup never records it as a name. */
internal const val UNNAMED_CONTACT = "Unknown"

data class ContactInfo(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String? = null,
    val currentRingtoneUri: String? = null,
    val isStarred: Boolean = false,
)

enum class ContactDndGuidance {
    NONE,
    POLICY_ACCESS_REQUIRED,
    CALLS_BLOCKED,
    PRIORITY_CALLS_DISABLED,
    CONTACT_MUST_BE_STARRED,
}

internal fun classifyContactDndGuidance(
    interruptionFilter: Int,
    hasPolicyAccess: Boolean,
    priorityCategories: Int,
    priorityCallSenders: Int,
    contactIsStarred: Boolean,
): ContactDndGuidance {
    if (
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL ||
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    ) {
        return ContactDndGuidance.NONE
    }
    if (!hasPolicyAccess) return ContactDndGuidance.POLICY_ACCESS_REQUIRED
    if (
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE ||
        interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS
    ) {
        return ContactDndGuidance.CALLS_BLOCKED
    }
    if (interruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
        return ContactDndGuidance.CALLS_BLOCKED
    }
    if (priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS == 0) {
        return ContactDndGuidance.PRIORITY_CALLS_DISABLED
    }
    return when {
        priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY -> ContactDndGuidance.NONE
        priorityCallSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED && !contactIsStarred ->
            ContactDndGuidance.CONTACT_MUST_BE_STARRED
        else -> ContactDndGuidance.NONE
    }
}

/**
 * The write reported success but the contact still points somewhere else. The usual causes are a
 * contact stored on the SIM card or a copy in another account that the update didn't reach.
 */
class ContactRingtoneNotKeptException(val storedUri: String?) :
    IllegalStateException("The contact didn't keep the new ringtone")

/**
 * Null when the stored value is the ringtone just written; otherwise the exception that explains
 * the mismatch. A provider may rewrite the volume name (`external` to `external_primary`), so the
 * same authority and media row id also count as kept.
 */
internal fun contactRingtoneReadBackFailure(expected: Uri, stored: String?): ContactRingtoneNotKeptException? {
    if (stored == expected.toString()) return null
    val storedUri = stored?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    val sameRow = storedUri != null &&
        storedUri.authority == expected.authority &&
        storedUri.lastPathSegment != null &&
        storedUri.lastPathSegment == expected.lastPathSegment
    return if (sameRow) null else ContactRingtoneNotKeptException(stored)
}

internal fun contactEditorUri(contact: ContactInfo): Uri =
    if (contact.lookupKey.isNotBlank()) {
        ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
            ?: ContactsContract.Contacts.CONTENT_URI.buildUpon().appendPath(contact.id.toString()).build()
    } else {
        ContactsContract.Contacts.CONTENT_URI.buildUpon().appendPath(contact.id.toString()).build()
    }

@Singleton
class ContactRingtoneService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getDndGuidance(contact: ContactInfo): ContactDndGuidance {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
            ?: return ContactDndGuidance.NONE
        return runCatching {
            val interruptionFilter = notificationManager.currentInterruptionFilter
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                return@runCatching classifyContactDndGuidance(
                    interruptionFilter = interruptionFilter,
                    hasPolicyAccess = false,
                    priorityCategories = 0,
                    priorityCallSenders = 0,
                    contactIsStarred = contact.isStarred,
                )
            }
            val policy = notificationManager.notificationPolicy
            classifyContactDndGuidance(
                interruptionFilter = interruptionFilter,
                hasPolicyAccess = true,
                priorityCategories = policy.priorityCategories,
                priorityCallSenders = policy.priorityCallSenders,
                contactIsStarred = contact.isStarred,
            )
        }.getOrElse { ContactDndGuidance.POLICY_ACCESS_REQUIRED }
    }

    suspend fun getContact(contactUri: Uri): ContactInfo? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        resolver.query(
            contactUri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.CUSTOM_RINGTONE,
                ContactsContract.Contacts.STARRED,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val lookupIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
            val ringtoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.CUSTOM_RINGTONE)
            val starredIdx = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
            if (!cursor.moveToFirst() || idIdx < 0 || nameIdx < 0) {
                null
            } else {
                ContactInfo(
                    id = cursor.getLong(idIdx),
                    lookupKey = if (lookupIdx >= 0) cursor.getString(lookupIdx) ?: "" else "",
                    name = cursor.getString(nameIdx) ?: UNNAMED_CONTACT,
                    photoUri = if (photoIdx >= 0) cursor.getString(photoIdx) else null,
                    currentRingtoneUri = if (ringtoneIdx >= 0) cursor.getString(ringtoneIdx) else null,
                    isStarred = starredIdx >= 0 && cursor.getInt(starredIdx) != 0,
                )
            }
        }
    }

    /**
     * Set a custom ringtone for a specific contact, then read it back through [readBackUri]. An
     * update can report one changed row while the contact keeps its old ringtone.
     *
     * Aura holds WRITE_CONTACTS but not READ_CONTACTS, so the read-back has to go through the URI
     * the system contact picker granted. Without one, the write is reported as it always was.
     */
    suspend fun setContactRingtone(contactId: Long, ringtoneUri: Uri, readBackUri: Uri? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri.toString())
                }
                val contactUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
                    .appendPath(contactId.toString())
                    .build()

                val updated = resolver.update(contactUri, values, null, null)
                if (updated == 0) throw IllegalStateException("Failed to update contact ringtone")
                if (readBackUri != null) {
                    val stored = try {
                        resolver.query(
                            readBackUri,
                            arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE),
                            null,
                            null,
                            null,
                        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                    } catch (e: SecurityException) {
                        // The picker's grant can lapse; an unverified write is still the write.
                        Log.w(TAG, "Couldn't read the contact back: ${e.javaClass.simpleName}")
                        ringtoneUri.toString()
                    }
                    contactRingtoneReadBackFailure(ringtoneUri, stored)?.let { throw it }
                }
            }.onFailure { it.rethrowIfCancelled() }
        }

    /** Clear custom ringtone for a contact (revert to default) */
    suspend fun clearContactRingtone(contactId: Long): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    putNull(ContactsContract.Contacts.CUSTOM_RINGTONE)
                }
                val contactUri = ContactsContract.Contacts.CONTENT_URI.buildUpon()
                    .appendPath(contactId.toString())
                    .build()
                resolver.update(contactUri, values, null, null)
                Unit
            }.onFailure { it.rethrowIfCancelled() }
        }

    private companion object {
        const val TAG = "ContactRingtone"
    }
}

/**
 * Sets a ringtone on the single contact that a lookup key or an exact name points at, holding
 * WRITE_CONTACTS and nothing else.
 *
 * Aura can't query contacts, so it can't look before it writes. Each update instead says how many
 * contacts it expects to change. The contacts provider runs a batch as one transaction and undoes
 * it when that count is off, so an update that would reach two contacts reaches none.
 */
@Singleton
class AndroidContactRingtoneWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactRingtoneWriter {

    override fun canWrite(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    override suspend fun probeByLookupKey(lookupKey: String): ContactRingtoneProbe =
        probe(ContactsContract.Contacts.LOOKUP_KEY, lookupKey)

    override suspend fun probeByDisplayName(displayName: String): ContactRingtoneProbe =
        probe(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, displayName)

    /**
     * An update that expects to change nobody. When it would have changed somebody the contacts
     * provider undoes it, so no contact is touched either way and the outcome says which it was.
     */
    private suspend fun probe(column: String, value: String): ContactRingtoneProbe = withContext(Dispatchers.IO) {
        if (value.isBlank()) return@withContext ContactRingtoneProbe.NOBODY
        when (updateExpecting(column, value, ringtoneUri = "", expectedCount = 0)) {
            BatchOutcome.APPLIED -> ContactRingtoneProbe.NOBODY
            BatchOutcome.COUNT_MISMATCH -> ContactRingtoneProbe.SOMEBODY
            BatchOutcome.NOT_ALLOWED -> ContactRingtoneProbe.NOT_ALLOWED
            BatchOutcome.FAILED -> ContactRingtoneProbe.UNKNOWN
        }
    }

    override suspend fun setByLookupKey(lookupKey: String, ringtoneUri: String): ContactRingtoneWrite =
        setOnTheOnlyMatch(ContactsContract.Contacts.LOOKUP_KEY, lookupKey, ringtoneUri)

    override suspend fun setByDisplayName(displayName: String, ringtoneUri: String): ContactRingtoneWrite =
        setOnTheOnlyMatch(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, displayName, ringtoneUri)

    private suspend fun setOnTheOnlyMatch(
        column: String,
        value: String,
        ringtoneUri: String,
    ): ContactRingtoneWrite = withContext(Dispatchers.IO) {
        if (value.isBlank() || ringtoneUri.isBlank()) return@withContext ContactRingtoneWrite.NO_MATCH
        when (updateExpecting(column, value, ringtoneUri, expectedCount = 1)) {
            BatchOutcome.APPLIED -> ContactRingtoneWrite.APPLIED
            BatchOutcome.NOT_ALLOWED -> ContactRingtoneWrite.NOT_ALLOWED
            BatchOutcome.FAILED -> ContactRingtoneWrite.FAILED
            // Not exactly one. An update that expects none tells "nobody" apart from "several",
            // and it is undone as well when there are several.
            BatchOutcome.COUNT_MISMATCH -> when (updateExpecting(column, value, ringtoneUri, expectedCount = 0)) {
                BatchOutcome.APPLIED -> ContactRingtoneWrite.NO_MATCH
                BatchOutcome.COUNT_MISMATCH -> ContactRingtoneWrite.AMBIGUOUS
                BatchOutcome.NOT_ALLOWED -> ContactRingtoneWrite.NOT_ALLOWED
                BatchOutcome.FAILED -> ContactRingtoneWrite.FAILED
            }
        }
    }

    private fun updateExpecting(
        column: String,
        value: String,
        ringtoneUri: String,
        expectedCount: Int,
    ): BatchOutcome {
        val operation = ContentProviderOperation.newUpdate(ContactsContract.Contacts.CONTENT_URI)
            .withSelection("$column = ?", arrayOf(value))
            .withValue(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri)
            .withExpectedCount(expectedCount)
            .build()
        return try {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(operation))
            // No result means no contacts provider answered, which isn't the same as no match.
            if (results.size == 1 && results[0].count == expectedCount) BatchOutcome.APPLIED else BatchOutcome.FAILED
        } catch (e: OperationApplicationException) {
            // The same exception reports a write the system's per-app switch turned down.
            if (e.message.orEmpty().contains("App op", ignoreCase = true)) {
                BatchOutcome.NOT_ALLOWED
            } else {
                BatchOutcome.COUNT_MISMATCH
            }
        } catch (e: SecurityException) {
            BatchOutcome.NOT_ALLOWED
        } catch (e: Exception) {
            e.rethrowIfCancelled()
            Log.w(TAG, "Couldn't update the contact ringtone: ${e.javaClass.simpleName}")
            BatchOutcome.FAILED
        }
    }

    private enum class BatchOutcome { APPLIED, COUNT_MISMATCH, NOT_ALLOWED, FAILED }

    private companion object {
        const val TAG = "ContactRingtone"
    }
}
