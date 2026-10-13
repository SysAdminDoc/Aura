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
