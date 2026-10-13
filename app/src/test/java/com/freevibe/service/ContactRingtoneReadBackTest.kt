package com.freevibe.service

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContactRingtoneReadBackTest {

    private lateinit var provider: FakeContactsProvider
    private val service = ContactRingtoneService(ApplicationProvider.getApplicationContext<Context>())
    private val ringtone = Uri.parse("content://media/external/audio/media/42")
    private val pickedUri = Uri.parse("content://com.android.contacts/contacts/lookup/abc/7")

    @Before
    fun setUp() {
        provider = Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
    }

    @Test
    fun `a write the contact keeps succeeds`() = runTest {
        val result = service.setContactRingtone(7L, ringtone, pickedUri)

        assertTrue(result.isSuccess)
        assertEquals(pickedUri, provider.lastQueryUri)
    }

    @Test
    fun `a write that reads back empty is reported as not kept`() = runTest {
        provider.keepWrites = false

        val failure = service.setContactRingtone(7L, ringtone, pickedUri).exceptionOrNull()

        assertTrue(failure is ContactRingtoneNotKeptException)
        assertNull((failure as ContactRingtoneNotKeptException).storedUri)
    }

    @Test
    fun `a write that reads back another ringtone is reported as not kept`() = runTest {
        provider.keepWrites = false
        provider.stored = "content://settings/system/ringtone"

        val failure = service.setContactRingtone(7L, ringtone, pickedUri).exceptionOrNull()

        assertEquals("content://settings/system/ringtone", (failure as ContactRingtoneNotKeptException).storedUri)
    }

    @Test
    fun `a lapsed picker grant leaves the write unverified rather than failed`() = runTest {
        provider.denyReads = true

        assertTrue(service.setContactRingtone(7L, ringtone, pickedUri).isSuccess)
    }

    @Test
    fun `without a picker uri nothing is read back`() = runTest {
        provider.keepWrites = false

        assertTrue(service.setContactRingtone(7L, ringtone).isSuccess)
        assertNull(provider.lastQueryUri)
    }

    @Test
    fun `read back compares the exact stored value`() {
        assertNull(contactRingtoneReadBackFailure(ringtone, ringtone.toString()))
        assertEquals("", contactRingtoneReadBackFailure(ringtone, "")?.storedUri)
    }

    class FakeContactsProvider : ContentProvider() {
        var stored: String? = null
        var keepWrites = true
        var denyReads = false
        var lastQueryUri: Uri? = null

        override fun onCreate() = true

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (keepWrites) stored = values?.getAsString(ContactsContract.Contacts.CUSTOM_RINGTONE)
            return 1
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            lastQueryUri = uri
            if (denyReads) throw SecurityException("no grant for $uri")
            return MatrixCursor(arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE)).apply { addRow(arrayOf(stored)) }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }
}
