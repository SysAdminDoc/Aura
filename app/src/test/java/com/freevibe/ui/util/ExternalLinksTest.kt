package com.freevibe.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {
    @Test
    fun `supported external links allow web and mail schemes`() {
        assertTrue(isSupportedExternalUrl("https://example.com/source"))
        assertTrue(isSupportedExternalUrl("HTTP://example.com/source"))
        assertTrue(isSupportedExternalUrl("mailto:privacy@example.com"))
        assertTrue(isSupportedExternalUrl("  https://example.com/source  "))
    }

    @Test
    fun `supported external links reject local executable and script schemes`() {
        assertFalse(isSupportedExternalUrl(""))
        assertFalse(isSupportedExternalUrl("example.com/source"))
        assertFalse(isSupportedExternalUrl("file:///data/user/0/com.freevibe/token"))
        assertFalse(isSupportedExternalUrl("content://media/external/images/1"))
        assertFalse(isSupportedExternalUrl("javascript:alert(1)"))
        assertFalse(isSupportedExternalUrl("data:text/html;base64,PHNjcmlwdD4="))
    }
}
