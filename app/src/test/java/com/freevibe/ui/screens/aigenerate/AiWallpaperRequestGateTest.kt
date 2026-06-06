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
            ),
        )

        assertEquals(
            "Enter your Stability AI key to generate images.",
            generatedWallpaperRequestError(
                providerEnabled = true,
                prompt = "misty canyon",
                apiKey = "",
            ),
        )
    }

    @Test
    fun `enabled generated source accepts populated prompt and key`() {
        assertNull(
            generatedWallpaperRequestError(
                providerEnabled = true,
                prompt = "misty canyon",
                apiKey = "sk-test",
            ),
        )
    }
}
