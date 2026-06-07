package com.freevibe.ui.screens.aigenerate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiWallpaperRequestGateTest {

    @Test
    fun `disabled generated source wins before prompt and key checks`() {
        assertEquals(
            GENERATED_CONTENT_DISABLED_MESSAGE,
            generatedWallpaperRequestError(
                providerEnabled = false,
                prompt = "",
                apiKey = "",
                disclosureAccepted = false,
            ),
        )
    }

    @Test
    fun `enabled generated source still requires prompt and key`() {
        assertEquals(
            "Describe your wallpaper to get started.",
            generatedWallpaperRequestError(
                providerEnabled = true,
                prompt = "",
                apiKey = "",
                disclosureAccepted = false,
            ),
        )

        assertEquals(
            "Enter your Stability AI key to generate images.",
            generatedWallpaperRequestError(
                providerEnabled = true,
                prompt = "misty canyon",
                apiKey = "",
                disclosureAccepted = false,
            ),
        )
    }

    @Test
    fun `enabled generated source requires disclosure acceptance before request`() {
        assertEquals(
            GENERATED_CONTENT_DISCLOSURE_REQUIRED_MESSAGE,
            generatedWallpaperRequestError(
                providerEnabled = true,
                prompt = "misty canyon",
                apiKey = "sk-test",
                disclosureAccepted = false,
            ),
        )
    }

    @Test
    fun `enabled generated source accepts populated prompt key and disclosure`() {
        assertNull(
            generatedWallpaperRequestError(
                providerEnabled = true,
                prompt = "misty canyon",
                apiKey = "sk-test",
                disclosureAccepted = true,
            ),
        )
    }
}
