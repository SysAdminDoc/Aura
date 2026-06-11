package com.freevibe.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactPermissionContractTest {

    @Test
    fun `manifest declares write contacts without broad read contacts`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse(manifest.contains("android.permission.READ_CONTACTS"))
        assertTrue(manifest.contains("android.permission.WRITE_CONTACTS"))
    }

    @Test
    fun `contact assignment uses system picker and write-only runtime permission`() {
        val screen = File("src/main/java/com/freevibe/ui/screens/sounds/ContactPickerScreen.kt").readText()

        assertTrue(screen.contains("Intent.ACTION_PICK"))
        assertTrue(screen.contains("ContactsContract.Contacts.CONTENT_URI"))
        assertTrue(screen.contains("ActivityResultContracts.RequestPermission"))
        assertFalse(screen.contains("ActivityResultContracts.RequestMultiplePermissions"))
        assertFalse(screen.contains("Manifest.permission.READ_CONTACTS"))
    }
}
