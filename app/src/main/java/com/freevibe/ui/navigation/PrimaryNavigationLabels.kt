package com.freevibe.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Height of the bottom bar and width of the rail. Both stay fixed; only the labels come and go. */
internal val PrimaryNavigationBarHeight = 64.dp
internal val PrimaryNavigationBarSidePadding = 4.dp
internal val PrimaryNavigationRailWidth = 86.dp

/** Space above the rail's first destination. */
internal val PrimaryNavigationRailTopSpace = 12.dp

/** A destination's icon with its selection underline: 22 dp icon, 3 dp gap, 2 dp line. */
internal val PrimaryNavigationIconHeight = 27.dp

// What Material 3 puts around an item's icon and label. The bar item and the rail item share
// these numbers: 8 dp between icon and label, at least 4 dp above and below the pair, 8 dp
// between bar items, 4 dp between rail items and at the rail's ends, and a 56 dp rail item.
// A label is measured against the whole item. Material 3 1.4.0 puts no padding of its own around
// it, so nothing more comes off the bar slot or the rail width below. PrimaryNavigationLargeTextTest
// draws a label that only just fits its slot, and fails if a later version starts taking some.
private val IconToLabel = 8.dp
private val ItemVerticalPadding = 4.dp
private val BarItemSpacing = 8.dp
private val RailItemMinHeight = 56.dp
private val RailItemSpacing = 4.dp
private val RailVerticalPadding = 4.dp

private fun labelledItemHeight(label: DpSize): Dp =
    PrimaryNavigationIconHeight + IconToLabel + label.height + ItemVerticalPadding * 2

/**
 * Whether every destination in the bottom bar can show its label whole.
 *
 * At large text a label outgrows its slot long before the icon does. When this is false the bar
 * shows icons alone, and each icon keeps the destination's full name for screen readers.
 */
internal fun bottomBarLabelsFit(barWidth: Dp, barHeight: Dp, itemCount: Int, label: DpSize): Boolean {
    if (itemCount <= 0) return true
    val slotWidth = (barWidth - BarItemSpacing * (itemCount - 1)) / itemCount
    return label.width <= slotWidth && labelledItemHeight(label) <= barHeight
}

/**
 * Whether the rail can show every label whole and still fit all its destinations on screen.
 *
 * A rail too short for labelled items would push the last destination out of reach, so height
 * counts here as much as width.
 */
internal fun railLabelsFit(railWidth: Dp, railHeight: Dp, itemCount: Int, label: DpSize): Boolean {
    if (itemCount <= 0) return true
    val labelled = labelledItemHeight(label)
    val itemHeight = if (labelled > RailItemMinHeight) labelled else RailItemMinHeight
    // The top space is one more child of the rail's column, so it gets a gap of its own.
    val needed = PrimaryNavigationRailTopSpace + itemHeight * itemCount +
        RailItemSpacing * itemCount + RailVerticalPadding * 2
    return label.width <= railWidth && needed <= railHeight
}

/** The widest and tallest single-line label among [titles] at the current font scale. */
@Composable
internal fun rememberWidestNavigationLabel(titles: List<String>, style: TextStyle): DpSize {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(titles, style, density) {
        var width = 0
        var height = 0
        titles.forEach { title ->
            val size = measurer.measure(text = title, style = style, maxLines = 1).size
            if (size.width > width) width = size.width
            if (size.height > height) height = size.height
        }
        with(density) { DpSize(width.toDp(), height.toDp()) }
    }
}
