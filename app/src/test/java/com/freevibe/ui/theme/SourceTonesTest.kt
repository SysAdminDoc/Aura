package com.freevibe.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The provider badge is plain text on a GlassCard over a wallpaper, so its colour has to read against the
 * glass tint over a black image (the AMOLED case) and over a white one, in both themes.
 */
class SourceTonesTest {

    private val schemes = mapOf("dark" to DarkColorScheme, "light" to LightColorScheme)
    private val sources = SourceBrandTones.keys + listOf("WALLHAVEN", "PICSUM", "SOMETHING_NEW")

    @Test
    fun `every provider badge reads at 4_5 to 1 on the glass card in both themes`() {
        schemes.forEach { (theme, scheme) ->
            val backdrops = glassBackdrops(scheme.surfaceContainer) + scheme.surfaceContainer + scheme.background
            sources.forEach { source ->
                val tone = sourceBadgeTone(source, scheme)
                backdrops.forEach { backdrop ->
                    val ratio = contrastRatio(tone, backdrop)
                    assertTrue("$source on $theme backdrop $backdrop is ${"%.2f".format(ratio)}:1", ratio >= MIN_TEXT_CONTRAST)
                }
            }
        }
    }

    @Test
    fun `the glass card's worst backdrops are its tint over black and over white`() {
        val backdrops = glassBackdrops(DarkColorScheme.surfaceContainer)
        assertEquals(2, backdrops.size)
        assertTrue(contrastRatio(backdrops[0], Color.Black) < 1.2)
        assertTrue(contrastRatio(backdrops[1], Color.Black) > 2.0)
    }

    @Test
    fun `a tone that already reads is left alone`() {
        assertEquals(Color.White, readableOn(Color.White, listOf(Color.Black)))
        assertEquals(Color(0xFF0B3D91), readableOn(Color(0xFF0B3D91), listOf(Color.White)))
    }

    @Test
    fun `a tone that does not read moves toward the side that contrasts more`() {
        val onBlack = readableOn(Color(0xFF0B3D91), listOf(Color.Black))
        assertTrue(contrastRatio(onBlack, Color.Black) >= MIN_TEXT_CONTRAST)
        assertTrue(onBlack.red > Color(0xFF0B3D91).red)

        val onWhite = readableOn(Color(0xFFFFB300), listOf(Color.White))
        assertTrue(contrastRatio(onWhite, Color.White) >= MIN_TEXT_CONTRAST)
        assertTrue(onWhite.green < Color(0xFFFFB300).green)
    }

    @Test
    fun `contrast ratio matches the WCAG extremes`() {
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.01)
        assertEquals(1.0, contrastRatio(Color.Black, Color.Black), 0.0001)
    }

    @Test
    fun `badge tones follow the scheme rather than a fixed colour`() {
        val custom: ColorScheme = LightColorScheme.copy(onSurfaceVariant = Color(0xFF8A8A8A))
        val tone = sourceBadgeTone("SOMETHING_NEW", custom)
        glassBackdrops(custom.surfaceContainer).forEach { assertTrue(contrastRatio(tone, it) >= MIN_TEXT_CONTRAST) }
    }
}
