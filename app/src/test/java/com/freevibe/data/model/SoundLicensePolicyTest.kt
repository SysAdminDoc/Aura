package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLicensePolicyTest {

    @Test
    fun `cc0 bundled sounds allow reviewed Aura Originals actions`() {
        val capabilities = sound(
            source = ContentSource.BUNDLED,
            license = "CC0 1.0",
            sourcePageUrl = "https://freesound.org/s/1/",
        ).soundLicenseCapabilities()

        assertEquals("CC0", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(SoundAction.APPLY))
        assertTrue(capabilities.canUse(SoundAction.DOWNLOAD))
        assertTrue(capabilities.canUse(SoundAction.EDIT))
        assertTrue(capabilities.canUse(SoundAction.BUNDLE))
    }

    @Test
    fun `youtube sounds require confirmation for apply and download but disable edit and bundle`() {
        val capabilities = sound(
            source = ContentSource.YOUTUBE,
            license = "YouTube",
            sourcePageUrl = "https://www.youtube.com/watch?v=abc12345678",
        ).soundLicenseCapabilities()

        assertEquals(SoundActionDecision.CONFIRMATION_REQUIRED, capabilities.capability(SoundAction.APPLY).decision)
        assertEquals(SoundActionDecision.CONFIRMATION_REQUIRED, capabilities.capability(SoundAction.DOWNLOAD).decision)
        assertEquals(SoundActionDecision.DISABLED, capabilities.capability(SoundAction.EDIT).decision)
        assertEquals(SoundActionDecision.DISABLED, capabilities.capability(SoundAction.BUNDLE).decision)
    }

    @Test
    fun `soundcloud sounds are link only until permissions are reviewed`() {
        val capabilities = sound(
            source = ContentSource.SOUNDCLOUD,
            license = "SoundCloud",
            sourcePageUrl = "https://soundcloud.com/artist/track",
        ).soundLicenseCapabilities()

        assertEquals(SoundActionDecision.DISABLED, capabilities.capability(SoundAction.APPLY).decision)
        assertEquals(SoundActionDecision.DISABLED, capabilities.capability(SoundAction.DOWNLOAD).decision)
        assertEquals(SoundActionDecision.DISABLED, capabilities.capability(SoundAction.EDIT).decision)
        assertTrue(capabilities.canUse(SoundAction.SHARE))
    }

    @Test
    fun `noncommercial licenses require confirmation and cannot be bundled`() {
        val capabilities = sound(
            source = ContentSource.FREESOUND,
            license = "Attribution-NonCommercial 4.0",
            sourcePageUrl = "https://freesound.org/s/42/",
        ).soundLicenseCapabilities()

        assertEquals("CC BY-NC", capabilities.normalizedLicense)
        assertTrue(capabilities.requiresConfirmation(SoundAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(SoundAction.DOWNLOAD))
        assertTrue(capabilities.requiresConfirmation(SoundAction.EDIT))
        assertFalse(capabilities.canUse(SoundAction.BUNDLE))
    }

    @Test
    fun `community uploads require confirmation but can share stored provenance`() {
        val capabilities = sound(
            source = ContentSource.COMMUNITY,
            license = "User Upload",
        ).soundLicenseCapabilities()

        assertEquals("User Upload", capabilities.normalizedLicense)
        assertTrue(capabilities.requiresConfirmation(SoundAction.APPLY))
        assertTrue(capabilities.requiresConfirmation(SoundAction.DOWNLOAD))
        assertTrue(capabilities.requiresConfirmation(SoundAction.EDIT))
        assertTrue(capabilities.canUse(SoundAction.SHARE))
        assertFalse(capabilities.canUse(SoundAction.BUNDLE))
    }

    @Test
    fun `community uploads with selected CC0 license allow normal personal actions`() {
        val capabilities = sound(
            source = ContentSource.COMMUNITY,
            license = "CC0",
        ).soundLicenseCapabilities()

        assertEquals("CC0", capabilities.normalizedLicense)
        assertTrue(capabilities.canUse(SoundAction.APPLY))
        assertTrue(capabilities.canUse(SoundAction.DOWNLOAD))
        assertTrue(capabilities.canUse(SoundAction.EDIT))
        assertTrue(capabilities.canUse(SoundAction.SHARE))
        assertFalse(capabilities.canUse(SoundAction.BUNDLE))
    }

    @Test
    fun `missing remote license disables sound actions`() {
        val capabilities = sound(
            source = ContentSource.FREESOUND,
            license = "",
            sourcePageUrl = "https://freesound.org/s/42/",
        ).soundLicenseCapabilities()

        assertEquals("Unknown", capabilities.normalizedLicense)
        assertFalse(capabilities.canUse(SoundAction.APPLY))
        assertFalse(capabilities.canUse(SoundAction.DOWNLOAD))
        assertFalse(capabilities.canUse(SoundAction.SHARE))
        assertFalse(capabilities.canUse(SoundAction.EDIT))
    }

    private fun sound(
        source: ContentSource,
        license: String,
        sourcePageUrl: String = "",
    ) = Sound(
        id = "sound_1",
        source = source,
        name = "Policy Tone",
        previewUrl = "https://example.com/preview.mp3",
        downloadUrl = "https://example.com/download.mp3",
        license = license,
        uploaderName = "Creator",
        sourcePageUrl = sourcePageUrl,
    )
}
