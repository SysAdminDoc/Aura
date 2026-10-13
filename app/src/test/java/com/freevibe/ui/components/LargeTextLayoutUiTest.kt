package com.freevibe.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.service.DownloadProgress
import com.freevibe.ui.LargeTextHost
import com.freevibe.ui.branchApartFrom
import com.freevibe.ui.clippingProblem
import com.freevibe.ui.screens.settings.SettingsToggle
import com.freevibe.ui.textLayoutOrNull
import com.freevibe.ui.unclippedBounds
import com.freevibe.ui.wordsSplitAcrossLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pieces that keep helper text whole at large text sizes: the line cap that only applies at the
 * default size, the columns that turn into a stack, and the recent searches that follow the height
 * of the field above them. Each check reads the layout back, so none of them depends on a baseline.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class LargeTextLayoutUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `children that fit their columns share one row`() {
        setColumns(secondChildWidth = 120.dp)

        val first = bounds("first")
        val second = bounds("second")
        // 300 dp less the 8 dp gap leaves 146 dp a column.
        assertEquals(first.top, second.top, 0.5f)
        assertEquals(first.right + px(8.dp), second.left, 0.5f)
        assertEquals(px(146.dp), first.width, 0.5f)
        assertEquals(px(146.dp), second.width, 0.5f)
    }

    @Test
    fun `a child too wide for its column puts every child on a row of its own`() {
        setColumns(secondChildWidth = 160.dp)

        val first = bounds("first")
        val second = bounds("second")
        assertEquals(first.left, second.left, 0.5f)
        assertEquals(first.bottom + px(8.dp), second.top, 0.5f)
        assertEquals(px(300.dp), first.width, 0.5f)
        assertEquals(px(300.dp), second.width, 0.5f)
    }

    @Test
    fun `columns land on the same pixels as a row of equal weights`() {
        // Odd pixel widths at this density, so the rounding has a remainder to hand out.
        val widths = listOf(173.5f, 174f, 174.5f, 347f)
        val counts = listOf(2, 3)
        val directions = listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)
        composeRule.setContent {
            LargeTextHost(fontScale = 1.0f) {
                Column {
                    directions.forEach { direction ->
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            widths.forEach { width ->
                                counts.forEach { count ->
                                    WeightedRowAndColumns(case = "$direction-$width-$count", width = width.dp, count = count)
                                }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        directions.forEach { direction ->
            widths.forEach { width ->
                counts.forEach { count ->
                    val case = "$direction-$width-$count"
                    repeat(count) { index ->
                        val expected = bounds("row-$case-$index")
                        val actual = bounds("columns-$case-$index")
                        assertEquals("left edge of child $index in $case", expected.left, actual.left, 0.01f)
                        assertEquals("width of child $index in $case", expected.width, actual.width, 0.01f)
                    }
                }
            }
        }
    }

    @Test
    fun `a title that needs more than its column gets the full width and keeps its words whole`() {
        setTitles(fontScale = 2.0f)

        val longTitle = composeRule.onNodeWithText(LONG_TITLE).fetchSemanticsNode().unclippedBounds()
        val shortTitle = composeRule.onNodeWithText(SHORT_TITLE).fetchSemanticsNode().unclippedBounds()
        assertEquals(longTitle.left, shortTitle.left, 0.5f)
        assertEquals(longTitle.bottom + px(8.dp), shortTitle.top, 0.5f)
        val layout = layoutOf(LONG_TITLE)
        assertEquals(emptyList<String>(), layout.wordsSplitAcrossLines())
        assertNull(layout.clippingProblem())
    }

    @Test
    fun `the same titles share a row at the default text size`() {
        setTitles(fontScale = 1.0f)

        val longTitle = composeRule.onNodeWithText(LONG_TITLE).fetchSemanticsNode().unclippedBounds()
        val shortTitle = composeRule.onNodeWithText(SHORT_TITLE).fetchSemanticsNode().unclippedBounds()
        assertEquals(longTitle.top, shortTitle.top, 0.5f)
        assertEquals(longTitle.right + px(8.dp), shortTitle.left, 0.5f)
    }

    @Test
    fun `recent searches start below a field taller than a fixed offset allows for`() {
        val queries = listOf("aurora over mountains", "minimal dark")
        composeRule.setContent {
            LargeTextHost(fontScale = 2.0f) {
                SearchFieldWithHistory(
                    recentQueries = queries,
                    isHistoryVisible = true,
                    onQueryClick = {},
                    onDeleteQuery = {},
                    onClearAll = {},
                    modifier = Modifier.width(360.dp),
                ) {
                    Box(Modifier.fillMaxWidth().height(120.dp).testTag("field"))
                }
            }
        }
        composeRule.waitForIdle()

        val field = composeRule.onNodeWithTag("field").fetchSemanticsNode()
        val fieldBottom = field.unclippedBounds().bottom
        val list = composeRule
            .onNodeWithText(context.getString(R.string.search_history_title), useUnmergedTree = true)
            .fetchSemanticsNode()
            .branchApartFrom(field)
        assertTrue("the recent searches at $list start above the field's bottom at $fieldBottom", list.top >= fieldBottom - 0.5f)
        assertTrue("the recent searches have no height", list.height > 0f)
        queries.forEach { query ->
            val row = composeRule
                .onNodeWithContentDescription(context.getString(R.string.search_history_recent_query_cd, query))
                .fetchSemanticsNode()
                .unclippedBounds()
            assertTrue("\"$query\" at $row starts above the field's bottom at $fieldBottom", row.top >= fieldBottom)
        }
    }

    @Test
    fun `hidden recent searches take no room under the field`() {
        composeRule.setContent {
            LargeTextHost(fontScale = 1.0f) {
                Box(Modifier.testTag("search")) {
                    SearchFieldWithHistory(
                        recentQueries = listOf("minimal dark"),
                        isHistoryVisible = false,
                        onQueryClick = {},
                        onDeleteQuery = {},
                        onClearAll = {},
                        modifier = Modifier.width(360.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().height(56.dp).testTag("field"))
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.search_history_title)).assertDoesNotExist()
        assertEquals(px(56.dp), bounds("field").height, 0.5f)
        assertEquals(bounds("field").height, bounds("search").height, 0.5f)
    }

    @Test
    fun `a search hint too long for one line wraps inside a taller field at 200 percent`() {
        setSearchField(fontScale = 2.0f)

        val hint = layoutOf(LONG_HINT)
        assertTrue("the hint should wrap, it has ${hint.lineCount} line", hint.lineCount > 1)
        assertNull(hint.clippingProblem())
        assertEquals(emptyList<String>(), hint.wordsSplitAcrossLines())
        val field = composeRule.onNodeWithContentDescription(LONG_HINT).fetchSemanticsNode().unclippedBounds()
        val drawn = composeRule.onNodeWithText(LONG_HINT, useUnmergedTree = true).fetchSemanticsNode().unclippedBounds()
        assertTrue("the field should grow past its 48 dp minimum", field.height > px(48.dp) + 1f)
        assertTrue(
            "the hint at $drawn reaches outside the field at $field",
            drawn.top >= field.top - 0.5f && drawn.bottom <= field.bottom + 0.5f,
        )
    }

    @Test
    fun `the same search hint stays on one shortened line at the default text size`() {
        setSearchField(fontScale = 1.0f)

        val hint = layoutOf(LONG_HINT)
        assertEquals(1, hint.lineCount)
        assertTrue(hint.isLineEllipsized(0))
        val field = composeRule.onNodeWithContentDescription(LONG_HINT).fetchSemanticsNode().unclippedBounds()
        assertEquals(px(48.dp), field.height, 0.5f)
    }

    @Test
    fun `a long download name wraps at 200 percent`() {
        setDownload(fontScale = 2.0f)

        val name = layoutOf(LONG_FILE_NAME)
        assertTrue("the name should wrap, it has ${name.lineCount} line", name.lineCount > 1)
        assertNull(name.clippingProblem())
        assertEquals(emptyList<String>(), name.wordsSplitAcrossLines())
    }

    @Test
    fun `a long download name stays on one shortened line at the default text size`() {
        setDownload(fontScale = 1.0f)

        val name = layoutOf(LONG_FILE_NAME)
        assertEquals(1, name.lineCount)
        assertTrue(name.isLineEllipsized(0))
    }

    @Test
    fun `a screen header description shows every line at 200 percent`() {
        setHeader(fontScale = 2.0f)

        val description = layoutOf(LONG_DESCRIPTION)
        assertTrue("the description has ${description.lineCount} lines", description.lineCount > 2)
        assertNull(description.clippingProblem())
        assertEquals(emptyList<String>(), description.wordsSplitAcrossLines())
    }

    @Test
    fun `a screen header description is cut to two lines at the default text size`() {
        setHeader(fontScale = 1.0f)

        val description = layoutOf(LONG_DESCRIPTION)
        assertEquals(2, description.lineCount)
        assertTrue(description.isLineEllipsized(1))
    }

    @Test
    fun `a settings switch summary shows every line at 200 percent`() {
        setSwitch(fontScale = 2.0f)

        val summary = layoutOf(LONG_DESCRIPTION)
        assertTrue("the summary has ${summary.lineCount} lines", summary.lineCount > 2)
        assertNull(summary.clippingProblem())
        assertEquals(emptyList<String>(), summary.wordsSplitAcrossLines())
    }

    @Test
    fun `a settings switch summary is cut to two lines at the default text size`() {
        setSwitch(fontScale = 1.0f)

        val summary = layoutOf(LONG_DESCRIPTION)
        assertEquals(2, summary.lineCount)
        assertTrue(summary.isLineEllipsized(1))
    }

    private fun setColumns(secondChildWidth: Dp) {
        composeRule.setContent {
            LargeTextHost(fontScale = 1.0f) {
                Box(Modifier.width(300.dp)) {
                    EqualColumnsOrStack(spacing = 8.dp) {
                        Box(Modifier.width(120.dp).height(40.dp).testTag("first"))
                        Box(Modifier.width(secondChildWidth).height(40.dp).testTag("second"))
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setTitles(fontScale: Float) {
        composeRule.setContent {
            LargeTextHost(fontScale = fontScale) {
                Box(Modifier.width(300.dp)) {
                    EqualColumnsOrStack(spacing = 8.dp) {
                        Text(LONG_TITLE)
                        Text(SHORT_TITLE)
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setSearchField(fontScale: Float) {
        composeRule.setContent {
            LargeTextHost(fontScale = fontScale) {
                CompactSearchField(
                    value = "",
                    onValueChange = {},
                    placeholder = LONG_HINT,
                    modifier = Modifier.width(300.dp),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setDownload(fontScale: Float) {
        composeRule.setContent {
            LargeTextHost(fontScale = fontScale) {
                DownloadProgressBar(
                    downloads = mapOf(
                        "download" to DownloadProgress(
                            id = "download",
                            fileName = LONG_FILE_NAME,
                            progress = 0.4f,
                            totalBytes = 4_000_000L,
                            downloadedBytes = 1_600_000L,
                        ),
                    ),
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun setHeader(fontScale: Float) {
        composeRule.setContent {
            LargeTextHost(fontScale = fontScale) {
                AuraScreenHeader(
                    label = "Library",
                    icon = Icons.Default.Search,
                    title = "Local Library",
                    subtitle = LONG_DESCRIPTION,
                ) {}
            }
        }
        composeRule.waitForIdle()
    }

    private fun setSwitch(fontScale: Float) {
        composeRule.setContent {
            LargeTextHost(fontScale = fontScale) {
                SettingsToggle(
                    icon = Icons.Default.Search,
                    title = "Reddit wallpapers",
                    subtitle = LONG_DESCRIPTION,
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): Rect = composeRule.onNodeWithTag(tag).fetchSemanticsNode().unclippedBounds()

    /** The layout of the one text node that shows [text], not of a parent it is merged into. */
    private fun layoutOf(text: String): TextLayoutResult {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        return checkNotNull(node.textLayoutOrNull()) { "\"$text\" reports no text layout" }
    }

    private fun px(value: Dp): Float = with(composeRule.density) { value.toPx() }

    private companion object {
        const val LONG_TITLE = "Crash troubleshooting"
        const val SHORT_TITLE = "Sources"
        const val LONG_HINT = "Search wallpapers, colors, sources and saved collections"
        const val LONG_FILE_NAME = "Aurora borealis over a snowy mountain range at night in winter.jpg"
        const val LONG_DESCRIPTION = "Everything you have saved or downloaded lives here on this device, sorted by " +
            "when you last opened it, with the newest wallpapers and sounds first and the oldest at the very end " +
            "of the list."
    }
}

/** A row of equal weights and the same children in [EqualColumnsOrStack], both [width] wide. */
@Composable
private fun WeightedRowAndColumns(case: String, width: Dp, count: Int) {
    Row(
        modifier = Modifier.width(width),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(count) { index ->
            Box(Modifier.weight(1f).height(10.dp).testTag("row-$case-$index"))
        }
    }
    Box(Modifier.width(width)) {
        EqualColumnsOrStack(spacing = 8.dp) {
            repeat(count) { index ->
                Box(Modifier.height(10.dp).testTag("columns-$case-$index"))
            }
        }
    }
}
