package com.freevibe.data.local

import org.junit.Assert.assertFalse
import org.junit.Test

class PreferencesManagerDefaultsTest {

    @Test
    fun `fresh installs keep optional cloud surfaces disabled by default`() {
        assertFalse(PreferencesManager.DEFAULT_GENERATED_CONTENT_PROVIDER_ENABLED)
        assertFalse(PreferencesManager.DEFAULT_COMMUNITY_PROVIDER_ENABLED)
    }
}
