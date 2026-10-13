package com.freevibe.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.freevibe.ui.theme.FreeVibeTheme
import org.junit.Assert.assertTrue

/**
 * A phone-sized light surface at [fontScale], hosted the way the route screenshot tests host theirs.
 *
 * [content] sits in the top start corner and takes the size it asks for. A Surface hands its fixed
 * size straight to its child, so without the Box a width set in a test would be ignored.
 */
@Composable
internal fun LargeTextHost(
    fontScale: Float,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    width: Dp = 411.dp,
    height: Dp = 891.dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale),
        LocalLayoutDirection provides layoutDirection,
    ) {
        FreeVibeTheme(darkTheme = false, dynamicColor = false) {
            Surface(modifier = Modifier.size(width, height)) {
                Box(contentAlignment = Alignment.TopStart) { content() }
            }
        }
    }
}

/** Every node that lays text out, which is where clipping can be read back without looking at pixels. */
internal val hasTextLayout = SemanticsMatcher("lays out text") { node ->
    node.config.contains(SemanticsActions.GetTextLayoutResult)
}

/** How the node's text was laid out in the room it was given, or null when it has no layout to report. */
internal fun SemanticsNode.textLayoutOrNull(): TextLayoutResult? {
    val results = mutableListOf<TextLayoutResult>()
    config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
    return results.firstOrNull()
}

/** Where the node sits in the root. Unlike boundsInRoot this isn't cut by a scroll container. */
internal fun SemanticsNode.unclippedBounds(): Rect = Rect(positionInRoot, size.toSize())

/**
 * The box of the part of the layout that holds this node and not [other]: the highest ancestor of
 * this node that [other] isn't inside. For a recent search and the field above it that is the whole
 * list of recent searches, which has no semantics of its own to look up.
 */
internal fun SemanticsNode.branchApartFrom(other: SemanticsNode): Rect {
    val shared = generateSequence(other.layoutInfo) { it.parentInfo }.toSet()
    var branch: LayoutInfo = layoutInfo
    while (true) {
        val parent = checkNotNull(branch.parentInfo) { "the two nodes share no ancestor" }
        if (parent in shared) break
        branch = parent
    }
    val coordinates = branch.coordinates
    return Rect(coordinates.positionInRoot(), coordinates.size.toSize())
}

/**
 * What keeps the text from being read in full, or null when all of it is drawn.
 *
 * didOverflowWidth and hasVisualOverflow are left out on purpose. A plain Text reports a layout
 * rebuilt at the widest it was allowed to be, so both are true for any label narrower than the room
 * it had. An ellipsis, lines that don't fit the height and a line wider than the node cover the
 * same ground without that false alarm.
 */
internal fun TextLayoutResult.clippingProblem(): String? = when {
    (0 until lineCount).any { isLineEllipsized(it) } -> "ends in an ellipsis"
    didOverflowHeight -> "has more lines than fit the room it has"
    (0 until lineCount).any { getLineRight(it) - getLineLeft(it) > size.width + 1f } ->
        "has a line wider than the room it has"
    else -> null
}

/** Words the layout broke in the middle because a line was too narrow for them, shown as "troublesho|oting". */
internal fun TextLayoutResult.wordsSplitAcrossLines(): List<String> {
    val text = layoutInput.text.text
    return (0 until lineCount - 1).mapNotNull { line ->
        val end = getLineEnd(line)
        val splitsWord = end in 1 until text.length &&
            text[end - 1].isLetterOrDigit() &&
            text[end].isLetterOrDigit()
        if (splitsWord) text.wordAround(end) else null
    }
}

private fun String.wordAround(index: Int): String {
    val start = (index - 1 downTo 0).firstOrNull { !this[it].isLetterOrDigit() }?.plus(1) ?: 0
    val end = (index until length).firstOrNull { !this[it].isLetterOrDigit() } ?: length
    return substring(start, index) + "|" + substring(index, end)
}

/**
 * Fails when any text on screen ends in an ellipsis, doesn't fit its box or has a word split across
 * lines. [atLeast] is how many pieces of text the surface is known to show, so an empty or
 * half-rendered surface can't pass by having nothing to check.
 */
internal fun ComposeContentTestRule.assertNoClippedText(atLeast: Int) {
    val layouts = onAllNodes(hasTextLayout, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .mapNotNull { it.textLayoutOrNull() }
        .filter { it.layoutInput.text.isNotEmpty() }
    assertTrue(
        "only ${layouts.size} pieces of text were laid out where at least $atLeast are expected",
        layouts.size >= atLeast,
    )
    val problems = layouts.flatMap { layout ->
        val text = layout.layoutInput.text.text
        listOfNotNull(layout.clippingProblem()?.let { "\"$text\" $it" }) +
            layout.wordsSplitAcrossLines().map { "\"$text\" splits a word: $it" }
    }
    assertTrue(problems.joinToString(separator = "\n"), problems.isEmpty())
}
