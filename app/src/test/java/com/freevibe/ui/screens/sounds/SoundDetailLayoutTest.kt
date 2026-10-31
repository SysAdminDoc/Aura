package com.freevibe.ui.screens.sounds

import androidx.compose.ui.graphics.Color
import com.freevibe.data.model.ContentSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The API 35 device audit found "Contact" ellipsized in the four-action row on a
 * 411x891 screen at *default* font scale, and YouTube red failing WCAG 2.2
 * normal-text contrast on white. Both rules are pure, so both are guarded here.
 */
class SoundDetailLayoutTest {

    private val contentWidth411 = 411 - SOUND_DETAIL_HORIZONTAL_PADDING_DP * 2

    @Test
    fun `four secondary actions stack on a default-scale 411dp phone`() {
        assertTrue(
            shouldStackSoundActions(
                availableWidthDp = contentWidth411,
                itemCount = 4,
                minItemWidthDp = SOUND_SECONDARY_ACTION_MIN_WIDTH_DP,
                fontScale = 1.0f,
            ),
        )
    }

    @Test
    fun `four secondary actions stay in one row on a tablet width`() {
        assertFalse(
            shouldStackSoundActions(
                availableWidthDp = 800 - SOUND_DETAIL_HORIZONTAL_PADDING_DP * 2,
                itemCount = 4,
                minItemWidthDp = SOUND_SECONDARY_ACTION_MIN_WIDTH_DP,
                fontScale = 1.0f,
            ),
        )
    }

    @Test
    fun `a large font scale always stacks`() {
        assertTrue(
            shouldStackSoundActions(
                availableWidthDp = 2000,
                itemCount = 4,
                minItemWidthDp = SOUND_SECONDARY_ACTION_MIN_WIDTH_DP,
                fontScale = 2.0f,
            ),
        )
    }

    @Test
    fun `a single action never stacks`() {
        assertFalse(
            shouldStackSoundActions(
                availableWidthDp = 10,
                itemCount = 1,
                minItemWidthDp = SOUND_SECONDARY_ACTION_MIN_WIDTH_DP,
                fontScale = 2.0f,
            ),
        )
    }

    @Test
    fun `spacing counts against the available width`() {
        // Exactly the item widths with no room for the three 8dp gaps.
        val widthWithoutGaps = 4 * SOUND_SECONDARY_ACTION_MIN_WIDTH_DP
        assertTrue(
            shouldStackSoundActions(
                availableWidthDp = widthWithoutGaps,
                itemCount = 4,
                minItemWidthDp = SOUND_SECONDARY_ACTION_MIN_WIDTH_DP,
                fontScale = 1.0f,
            ),
        )
        assertFalse(
            shouldStackSoundActions(
                availableWidthDp = widthWithoutGaps + 3 * SOUND_ACTION_SPACING_DP,
                itemCount = 4,
                minItemWidthDp = SOUND_SECONDARY_ACTION_MIN_WIDTH_DP,
                fontScale = 1.0f,
            ),
        )
    }

    // -- contrast --

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Light theme `surface` and `background` from `FreeVibeTheme`. */
    private val lightSurfaces = listOf(Color(0xFFFFFFFF), Color(0xFFF6F7F8))

    /** Dark theme `surface` and AMOLED `background` from `FreeVibeTheme`. */
    private val darkSurfaces = listOf(Color(0xFF0B0D10), Color(0xFF050607))

    @Test
    fun `every source tone meets WCAG normal-text contrast in both themes`() {
        ContentSource.entries.forEach { source ->
            val tone = soundSourceTone(source)
            lightSurfaces.forEach { surface ->
                val ratio = contrastRatio(tone.onLight, surface)
                assertTrue(
                    "${source.name} light tone is $ratio:1 on $surface, below 4.5:1",
                    ratio >= 4.5,
                )
            }
            darkSurfaces.forEach { surface ->
                val ratio = contrastRatio(tone.onDark, surface)
                assertTrue(
                    "${source.name} dark tone is $ratio:1 on $surface, below 4.5:1",
                    ratio >= 4.5,
                )
            }
        }
    }

    @Test
    fun `the YouTube tone is no longer raw brand red on light surfaces`() {
        val tone = soundSourceTone(ContentSource.YOUTUBE)

        assertTrue(contrastRatio(Color(0xFFFF0000), Color(0xFFFFFFFF)) < 4.5)
        assertTrue(contrastRatio(tone.onLight, Color(0xFFFFFFFF)) >= 4.5)
    }
}
