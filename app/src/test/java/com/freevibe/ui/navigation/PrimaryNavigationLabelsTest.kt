package com.freevibe.ui.navigation

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryNavigationLabelsTest {

    // "Wallpapers" in labelSmall is about 55 dp wide and 16 dp tall at normal text.
    private val normalLabel = DpSize(55.dp, 16.dp)
    private val doubledLabel = DpSize(110.dp, 32.dp)

    @Test
    fun `a phone bar shows labels at normal text`() {
        assertTrue(bottomBarLabelsFit(barWidth = 385.dp, barHeight = 64.dp, itemCount = 5, label = normalLabel))
    }

    @Test
    fun `a phone bar drops labels at 200 percent text`() {
        assertFalse(bottomBarLabelsFit(barWidth = 385.dp, barHeight = 64.dp, itemCount = 5, label = doubledLabel))
    }

    @Test
    fun `a label that fits its slot but not the bar height is dropped`() {
        assertFalse(bottomBarLabelsFit(barWidth = 800.dp, barHeight = 64.dp, itemCount = 5, label = DpSize(60.dp, 24.dp)))
    }

    @Test
    fun `a label wider than its slot is dropped even when it is short enough`() {
        assertFalse(bottomBarLabelsFit(barWidth = 385.dp, barHeight = 64.dp, itemCount = 5, label = DpSize(72.dp, 16.dp)))
    }

    @Test
    fun `a bar label gets the whole slot between the gaps and no more`() {
        // 385 dp across five items with four 8 dp gaps leaves each one 70.6 dp.
        assertTrue(bottomBarLabelsFit(barWidth = 385.dp, barHeight = 64.dp, itemCount = 5, label = DpSize(70.dp, 16.dp)))
        assertFalse(bottomBarLabelsFit(barWidth = 385.dp, barHeight = 64.dp, itemCount = 5, label = DpSize(71.dp, 16.dp)))
    }

    @Test
    fun `a bar label fits up to the height the bar leaves under the icon`() {
        // 27 dp of icon, 8 dp down to the label and 4 dp above and below leave it 21 dp of the 64.
        assertTrue(bottomBarLabelsFit(barWidth = 800.dp, barHeight = 64.dp, itemCount = 5, label = DpSize(60.dp, 21.dp)))
        assertFalse(bottomBarLabelsFit(barWidth = 800.dp, barHeight = 64.dp, itemCount = 5, label = DpSize(60.dp, 22.dp)))
    }

    @Test
    fun `fewer destinations leave each label more of the same bar`() {
        val wide = DpSize(90.dp, 16.dp)

        assertFalse(bottomBarLabelsFit(barWidth = 385.dp, barHeight = 64.dp, itemCount = 5, label = wide))
        assertTrue(bottomBarLabelsFit(barWidth = 385.dp, barHeight = 64.dp, itemCount = 4, label = wide))
    }

    @Test
    fun `a rail label may be as wide as the rail and no wider`() {
        assertTrue(railLabelsFit(railWidth = 86.dp, railHeight = 760.dp, itemCount = 5, label = DpSize(86.dp, 16.dp)))
        assertFalse(railLabelsFit(railWidth = 86.dp, railHeight = 760.dp, itemCount = 5, label = DpSize(87.dp, 16.dp)))
    }

    @Test
    fun `a tall rail shows labels at normal text`() {
        assertTrue(railLabelsFit(railWidth = 86.dp, railHeight = 760.dp, itemCount = 5, label = normalLabel))
    }

    @Test
    fun `a rail drops labels that are wider than it is`() {
        assertFalse(railLabelsFit(railWidth = 86.dp, railHeight = 760.dp, itemCount = 5, label = doubledLabel))
    }

    @Test
    fun `a rail too short for labelled items drops labels so every destination stays on screen`() {
        // Five labelled items need 59 dp each here, plus the gaps: more than a 300 dp rail has.
        assertFalse(railLabelsFit(railWidth = 86.dp, railHeight = 300.dp, itemCount = 5, label = DpSize(55.dp, 16.dp)))
        assertTrue(railLabelsFit(railWidth = 86.dp, railHeight = 340.dp, itemCount = 5, label = DpSize(55.dp, 16.dp)))
    }

    @Test
    fun `nothing to show always fits`() {
        assertTrue(bottomBarLabelsFit(barWidth = 0.dp, barHeight = 0.dp, itemCount = 0, label = doubledLabel))
        assertTrue(railLabelsFit(railWidth = 0.dp, railHeight = 0.dp, itemCount = 0, label = doubledLabel))
    }
}
