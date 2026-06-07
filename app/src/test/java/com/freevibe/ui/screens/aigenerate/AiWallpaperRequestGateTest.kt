package com.freevibe.ui.screens.aigenerate

import com.freevibe.data.model.CommunityReportReason
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.repository.AiStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `active generation blocks duplicate submits before another request`() {
        assertEquals(
            GENERATED_CONTENT_IN_FLIGHT_MESSAGE,
            generatedWallpaperRequestError(
                providerEnabled = true,
                prompt = "misty canyon",
                apiKey = "sk-test",
                disclosureAccepted = true,
                isGenerating = true,
            ),
        )
    }

    @Test
    fun `duplicate confirmation matches normalized prompt and style`() {
        val last = generatedWallpaperRequestSignature(
            prompt = "Misty canyon at dawn",
            style = AiStyle.PHOTOGRAPHIC,
        )

        val confirmation = duplicateGenerationConfirmation(
            prompt = "  misty   canyon at dawn  ",
            style = AiStyle.PHOTOGRAPHIC,
            lastSuccessfulRequest = last,
        )

        assertEquals("misty canyon at dawn", confirmation?.promptPreview)
        assertEquals("Photo", confirmation?.styleLabel)
    }

    @Test
    fun `duplicate confirmation ignores different style`() {
        val last = generatedWallpaperRequestSignature(
            prompt = "Misty canyon at dawn",
            style = AiStyle.PHOTOGRAPHIC,
        )

        assertNull(
            duplicateGenerationConfirmation(
                prompt = "Misty canyon at dawn",
                style = AiStyle.CINEMATIC,
                lastSuccessfulRequest = last,
            ),
        )
    }

    @Test
    fun `generated wallpaper report input omits local file details`() {
        val wallpaper = Wallpaper(
            id = "generated-1",
            source = ContentSource.AI_GENERATED,
            thumbnailUrl = "file:///cache/thumb.png",
            fullUrl = "file:///cache/sk-secret-output.png",
            width = 1024,
            height = 1792,
            sourcePageUrl = "file:///cache/prompt.json",
        )

        val input = generatedWallpaperReportInput(
            wallpaper = wallpaper,
            reason = CommunityReportReason.DECEPTIVE,
            note = "looks like a login page",
        )

        assertEquals("WALLPAPER::AI_GENERATED::generated-1", input.contentId)
        assertEquals(ContentSource.AI_GENERATED, input.contentSource)
        assertEquals(CommunityReportReason.DECEPTIVE, input.reason)
        assertEquals("", input.sourceUrl)
        assertEquals("Generated wallpaper", input.license)
        assertEquals("Aura generated wallpaper", input.uploaderName)
        assertFalse(input.toString().contains("sk-secret"))
    }
}
