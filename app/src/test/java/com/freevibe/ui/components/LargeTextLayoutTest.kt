package com.freevibe.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LargeTextLayoutTest {
    @Test
    fun `the line cap holds at the default text size and below`() {
        assertEquals(2, lineCapForFontScale(maxLines = 2, fontScale = 1.0f))
        assertEquals(1, lineCapForFontScale(maxLines = 1, fontScale = 0.85f))
    }

    @Test
    fun `the line cap is lifted for any larger text`() {
        listOf(1.15f, 1.3f, 2.0f).forEach { fontScale ->
            assertEquals(
                "at font scale $fontScale",
                Int.MAX_VALUE,
                lineCapForFontScale(maxLines = 2, fontScale = fontScale),
            )
        }
    }

    @Test
    fun `equal columns split the width that is left after the gaps`() {
        assertEquals(listOf(339, 339), equalColumnWidths(totalWidth = 694, count = 2, gap = 16))
        assertEquals(listOf(100, 100, 100), equalColumnWidths(totalWidth = 332, count = 3, gap = 16))
    }

    @Test
    fun `a width that leaves a remainder gives or takes one pixel from the first columns`() {
        assertEquals(listOf(100, 101), equalColumnWidths(totalWidth = 201, count = 2, gap = 0))
        assertEquals(listOf(106, 105, 105), equalColumnWidths(totalWidth = 348, count = 3, gap = 16))
        assertEquals(listOf(105, 106, 106), equalColumnWidths(totalWidth = 349, count = 3, gap = 16))
    }

    @Test
    fun `column widths stop at zero when the gaps take the whole width`() {
        assertEquals(listOf(0, 0), equalColumnWidths(totalWidth = 5, count = 2, gap = 16))
        assertEquals(emptyList<Int>(), equalColumnWidths(totalWidth = 100, count = 0, gap = 16))
    }
}
