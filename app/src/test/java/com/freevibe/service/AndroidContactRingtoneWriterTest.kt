package com.freevibe.service

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentValues
import android.content.OperationApplicationException
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The write-only matching against a provider that, like the real contacts provider, runs a batch
 * as one transaction and undoes it when an update changes the wrong number of contacts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidContactRingtoneWriterTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val writer = AndroidContactRingtoneWriter(application)
    private lateinit var provider: FakeContactsProvider

    @Before
    fun setUp() {
        provider = Robolectric.setupContentProvider(FakeContactsProvider::class.java, ContactsContract.AUTHORITY)
        shadowOf(application).grantPermissions(Manifest.permission.WRITE_CONTACTS)
    }

    @Test
    fun `the contact with that lookup key gets the ringtone`() = runTest {
        val ada = provider.add("key-ada", "Ada Lovelace")
        val bob = provider.add("key-bob", "Bob")

        assertEquals(ContactRingtoneWrite.APPLIED, writer.setByLookupKey("key-ada", RINGTONE))

        assertEquals(RINGTONE, ada.ringtone)
        assertNull(bob.ringtone)
        assertEquals("one match needs one batch", 1, provider.batches)
    }

    @Test
    fun `a lookup key nobody has changes nothing`() = runTest {
        val ada = provider.add("key-ada", "Ada Lovelace")

        assertEquals(ContactRingtoneWrite.NO_MATCH, writer.setByLookupKey("key-gone", RINGTONE))

        assertNull(ada.ringtone)
    }

    @Test
    fun `the one contact with that exact name gets the ringtone`() = runTest {
        val ada = provider.add("key-new", "Ada Lovelace")
        val other = provider.add("key-other", "Ada")

        assertEquals(ContactRingtoneWrite.APPLIED, writer.setByDisplayName("Ada Lovelace", RINGTONE))

        assertEquals(RINGTONE, ada.ringtone)
        assertNull(other.ringtone)
    }

    @Test
    fun `a name two contacts share is ambiguous and neither is changed`() = runTest {
        val first = provider.add("key-one", "Ada Lovelace").apply { ringtone = "content://settings/system/ringtone" }
        val second = provider.add("key-two", "Ada Lovelace")

        assertEquals(ContactRingtoneWrite.AMBIGUOUS, writer.setByDisplayName("Ada Lovelace", RINGTONE))

        assertEquals("content://settings/system/ringtone", first.ringtone)
        assertNull(second.ringtone)
    }

    @Test
    fun `a name nobody has is no match`() = runTest {
        provider.add("key-ada", "Ada Lovelace")

        assertEquals(ContactRingtoneWrite.NO_MATCH, writer.setByDisplayName("Grace Hopper", RINGTONE))
    }

    @Test
    fun `a refused write is reported as not allowed`() = runTest {
        val ada = provider.add("key-ada", "Ada Lovelace")
        provider.denyWrites = true

        assertEquals(ContactRingtoneWrite.NOT_ALLOWED, writer.setByLookupKey("key-ada", RINGTONE))

        assertNull(ada.ringtone)
    }

    @Test
    fun `a blank key or name never reaches the provider`() = runTest {
        provider.add("", "")

        assertEquals(ContactRingtoneWrite.NO_MATCH, writer.setByLookupKey(" ", RINGTONE))
        assertEquals(ContactRingtoneWrite.NO_MATCH, writer.setByDisplayName("", RINGTONE))

        assertEquals(0, provider.batches)
    }

    @Test
    fun `can write follows the contacts permission`() {
        assertTrue(writer.canWrite())

        shadowOf(application).denyPermissions(Manifest.permission.WRITE_CONTACTS)

        assertFalse(writer.canWrite())
    }

    class FakeContactsProvider : ContentProvider() {
        class Row(val lookupKey: String, val name: String, var ringtone: String? = null)

        val rows = mutableListOf<Row>()
        var denyWrites = false
        var batches = 0

        fun add(lookupKey: String, name: String): Row = Row(lookupKey, name).also { rows += it }

        override fun onCreate() = true

        override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
            batches++
            val before = rows.map { it.ringtone }
            return try {
                super.applyBatch(operations)
            } catch (e: OperationApplicationException) {
                rows.forEachIndexed { index, row -> row.ringtone = before[index] }
                throw e
            }
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            if (denyWrites) throw SecurityException("no write access to $uri")
            val wanted = selectionArgs?.singleOrNull() ?: return 0
            val matched = when (selection) {
                "${ContactsContract.Contacts.LOOKUP_KEY} = ?" -> rows.filter { it.lookupKey == wanted }
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?" -> rows.filter { it.name == wanted }
                else -> emptyList()
            }
            matched.forEach { it.ringtone = values?.getAsString(ContactsContract.Contacts.CUSTOM_RINGTONE) }
            return matched.size
        }

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? = throw SecurityException("Aura holds no permission to read contacts")

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun getType(uri: Uri): String? = null
    }

    private companion object {
        const val RINGTONE = "content://media/external/audio/media/42"
    }
}
