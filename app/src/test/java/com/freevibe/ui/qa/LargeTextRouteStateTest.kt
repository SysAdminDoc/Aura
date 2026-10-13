package com.freevibe.ui.qa

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.freevibe.R
import com.freevibe.ui.LargeTextHost
import com.freevibe.ui.assertNoClippedText
import com.freevibe.ui.branchApartFrom
import com.freevibe.ui.textLayoutOrNull
import com.freevibe.ui.unclippedBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The routes behind the 200 percent baselines, checked without their pixels: no text ends in an
 * ellipsis or splits a word, cards that can't sit side by side stack, and recent searches start
 * under the search field. The accessibility release gate cites this class for noClippedText.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class LargeTextRouteStateTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `settings shows all of its helper text at 200 percent`() {
        show(ProductionRouteScenario.SettingsProviderDisabled, fontScale = 2.0f)

        composeRule.assertNoClippedText(atLeast = 12)
        // Both were cut to two lines before, and both need more than two at this size.
        assertTrue(layoutOf(R.string.settings_services_section_description).lineCount > 2)
        assertTrue(layoutOf(R.string.settings_wp_reddit_off_subtitle).lineCount > 2)
    }

    @Test
    fun `settings diagnostics cards take a row each at 200 percent`() {
        show(ProductionRouteScenario.SettingsProviderDisabled, fontScale = 2.0f)

        val (crash, source) = diagnosticsCards()
        assertTrue("the second card at $source should start under the first at $crash", source.top >= crash.bottom - 0.5f)
        assertEquals(crash.left, source.left, 0.5f)
        assertEquals(crash.width, source.width, 0.5f)
    }

    @Test
    fun `settings diagnostics cards share a row at the default text size`() {
        show(ProductionRouteScenario.SettingsProviderDisabled, fontScale = 1.0f)

        val (crash, source) = diagnosticsCards()
        assertEquals(crash.top, source.top, 0.5f)
        assertTrue("the second card at $source should start after the first at $crash", source.left >= crash.right - 0.5f)
    }

    @Test
    fun `library shows all of its text at 200 percent`() {
        show(ProductionRouteScenario.LibraryHub, fontScale = 2.0f)

        composeRule.assertNoClippedText(atLeast = 8)
        // The header description and a card description, both cut to two lines before.
        assertTrue(layoutOf(R.string.library_subtitle).lineCount > 2)
        assertTrue(layoutOf(R.string.library_search_body).lineCount > 2)
    }

    @Test
    fun `library counts take a row each at 200 percent`() {
        show(ProductionRouteScenario.LibraryHub, fontScale = 2.0f)

        libraryCounts().zipWithNext().forEach { (above, below) ->
            assertTrue("the count at $below should start under the one at $above", below.top >= above.bottom - 0.5f)
            assertEquals(above.left, below.left, 0.5f)
        }
    }

    @Test
    fun `library counts share a row at the default text size`() {
        show(ProductionRouteScenario.LibraryHub, fontScale = 1.0f)

        libraryCounts().zipWithNext().forEach { (before, after) ->
            assertEquals(before.top, after.top, 0.5f)
            assertTrue("the count at $after should start after the one at $before", after.left >= before.right - 0.5f)
        }
    }

    @Test
    fun `recent searches start under a search field that wraps at 200 percent`() {
        show(ProductionRouteScenario.WallpapersSearchHistory, fontScale = 2.0f)

        val field = searchField()
        assertTrue(
            "the hint should wrap and make the field taller than its 48 dp minimum",
            field.unclippedBounds().height > px(48.dp) + 1f,
        )
        assertRecentSearchesUnder(field)
        composeRule.assertNoClippedText(atLeast = 6)
    }

    @Test
    fun `recent searches start under the search field at the default text size`() {
        show(ProductionRouteScenario.WallpapersSearchHistory, fontScale = 1.0f)

        assertRecentSearchesUnder(searchField())
    }

    private fun show(scenario: ProductionRouteScenario, fontScale: Float) {
        composeRule.setContent {
            LargeTextHost(fontScale = fontScale) {
                ProductionRouteState(scenario)
            }
        }
        composeRule.waitForIdle()
    }

    /** The layout of the one text node that shows [text], not of a row it is merged into. */
    private fun layoutOf(@StringRes text: Int): TextLayoutResult {
        val shown = context.getString(text)
        val node = composeRule.onNodeWithText(shown, useUnmergedTree = true).fetchSemanticsNode()
        return checkNotNull(node.textLayoutOrNull()) { "\"$shown\" reports no text layout" }
    }

    private fun diagnosticsCards(): Pair<Rect, Rect> = Pair(
        labelledValue(
            context.getString(R.string.settings_diag_crash_title),
            context.getString(R.string.settings_diagnostics_section_description),
        ),
        labelledValue(
            context.getString(R.string.settings_diag_source_title),
            context.getString(R.string.settings_diag_source_empty_subtitle),
        ),
    )

    private fun libraryCounts(): List<Rect> = listOf(
        labelledValue(context.getString(R.string.library_summary_favorites), "128"),
        labelledValue(context.getString(R.string.library_summary_downloads), "36"),
        labelledValue(context.getString(R.string.library_summary_collections), "7"),
    )

    /** A card that reads as "label: value", found by that description. */
    private fun labelledValue(label: String, value: String): Rect =
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.a11y_label_value, label, value))
            .fetchSemanticsNode()
            .unclippedBounds()

    private fun searchField(): SemanticsNode =
        composeRule
            .onNodeWithContentDescription(context.getString(R.string.wallpapers_search_placeholder))
            .fetchSemanticsNode()

    private fun assertRecentSearchesUnder(field: SemanticsNode) {
        val fieldBounds = field.unclippedBounds()
        val list = composeRule
            .onNodeWithText(context.getString(R.string.search_history_title), useUnmergedTree = true)
            .fetchSemanticsNode()
            .branchApartFrom(field)
        assertTrue(
            "the recent searches at $list start above the bottom of the search field at $fieldBounds",
            list.top >= fieldBounds.bottom - 0.5f,
        )
        assertTrue("the recent searches have no height", list.height > 0f)
    }

    private fun px(value: Dp): Float = with(composeRule.density) { value.toPx() }
}
