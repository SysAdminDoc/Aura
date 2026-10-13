package com.freevibe.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sign

/** The text size the line caps were drawn for. Anything larger shows every line. */
private const val DEFAULT_FONT_SCALE = 1f

/**
 * Line cap for helper, summary and description text.
 *
 * [maxLines] keeps rows even at the default text size. With larger text the cap is lifted: the same
 * sentence needs about twice the lines at 200 percent, and a cap would end it in an ellipsis for the
 * people who asked for text they can read.
 */
@Composable
@ReadOnlyComposable
fun lineCapAtDefaultTextSize(maxLines: Int): Int =
    lineCapForFontScale(maxLines, LocalDensity.current.fontScale)

internal fun lineCapForFontScale(maxLines: Int, fontScale: Float): Int =
    if (fontScale > DEFAULT_FONT_SCALE) Int.MAX_VALUE else maxLines

/**
 * Lays [content] out as equal columns, or as one full-width column when a column would be too
 * narrow for a child's longest word.
 *
 * A child's minimum intrinsic width is the width of its widest unbreakable piece, so the columns
 * are only used while no word has to be split across lines. Side by side, the children are measured
 * and placed exactly as a Row of equal weights with [spacing] between them.
 */
@Composable
fun EqualColumnsOrStack(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val columnWidths = if (constraints.hasBoundedWidth && measurables.size > 1) {
            equalColumnWidths(constraints.maxWidth, measurables.size, gap)
        } else {
            null
        }
        val everyWordFits = columnWidths != null && measurables.indices.all { index ->
            measurables[index].minIntrinsicWidth(constraints.maxHeight) <= columnWidths[index]
        }
        if (columnWidths != null && everyWordFits) {
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(
                    Constraints(
                        minWidth = columnWidths[index],
                        maxWidth = columnWidths[index],
                        maxHeight = constraints.maxHeight,
                    ),
                )
            }
            val height = constraints.constrainHeight(placeables.maxOf { it.height })
            layout(constraints.maxWidth, height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, 0)
                    x += placeable.width + gap
                }
            }
        } else {
            val fullWidth = constraints.copy(
                minWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth,
                minHeight = 0,
            )
            val placeables = measurables.map { it.measure(fullWidth) }
            val width = constraints.constrainWidth(placeables.maxOfOrNull { it.width } ?: 0)
            val stackedHeight = placeables.sumOf { it.height } + gap * max(0, placeables.size - 1)
            layout(width, constraints.constrainHeight(stackedHeight)) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + gap
                }
            }
        }
    }
}

/**
 * The widths a Row gives [count] children of equal weight across [totalWidth] with [gap] between
 * them: the share rounded to a whole pixel, with the rounding error handed to the first children.
 */
internal fun equalColumnWidths(totalWidth: Int, count: Int, gap: Int): List<Int> {
    if (count <= 0) return emptyList()
    val shared = max(0, totalWidth - gap * (count - 1))
    val rounded = (shared / count.toFloat() + 0.5f).toInt()
    var remainder = shared - rounded * count
    return List(count) {
        val adjustment = remainder.sign
        remainder -= adjustment
        max(0, rounded + adjustment)
    }
}
