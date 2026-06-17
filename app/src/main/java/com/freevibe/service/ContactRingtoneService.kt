package com.freevibe.service

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactInfo(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String? = null,
    val currentRingtoneUri: String? = null,
)

@Singleton
class ContactRingtoneService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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
            if (!cursor.moveToFirst() || idIdx < 0 || nameIdx < 0) {
                null
            } else {
                ContactInfo(
                    id = cursor.getLong(idIdx),
                    lookupKey = if (lookupIdx >= 0) cursor.getString(lookupIdx) ?: "" else "",
                    name = cursor.getString(nameIdx) ?: "Unknown",
                    photoUri = if (photoIdx >= 0) cursor.getString(photoIdx) else null,
                    currentRingtoneUri = if (ringtoneIdx >= 0) cursor.getString(ringtoneIdx) else null,
                )
            }
        }
    }

    /** Set a custom ringtone for a specific contact */
    suspend fun setContactRingtone(contactId: Long, ringtoneUri: Uri): Result<Unit> =
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
}
