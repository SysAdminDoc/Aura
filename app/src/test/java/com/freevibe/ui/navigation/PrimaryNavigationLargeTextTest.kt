package com.freevibe.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.core.app.ApplicationProvider
import com.freevibe.ui.PrimaryNavigationBar
import com.freevibe.ui.PrimaryNavigationRail
import com.freevibe.ui.theme.FreeVibeTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * All five destinations stay reachable and named when the text is too large for their labels, and
 * a label that is shown is shown whole. "Displayed" is not enough for that: a label cut short by
 * its slot is still displayed, so the labels are checked against the room their text needs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class PrimaryNavigationLargeTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val titles: List<String>
        get() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            return Screen.bottomNavItems.map { context.getString(it.titleRes) }
        }

    @Test
    fun `the bar shows every label at normal text`() {
        setBar(fontScale = 1.0f)

        assertEveryLabelWhole()
        composeRule.onNodeWithText(titles[2]).assertIsSelected()
    }

    @Test
    fun `a bar label that only just fits its slot is drawn whole`() {
        composeRule.setContent {
            NavigationHost(fontScale = 1.0f, width = 411.dp, height = 891.dp) {
                val widest = rememberWidestNavigationLabel(titles, MaterialTheme.typography.labelSmall)
                // The narrowest whole-dp bar the fit rule still gives labels to, plus 1 dp an item so
                // pixel rounding decides nothing. Padding around the label inside its item would cut
                // every label here. Material 3 1.4.0 has none, and this notices a version that does.
                val narrowest = (200..411).firstOrNull { width ->
                    bottomBarLabelsFit(
                        barWidth = width.dp - PrimaryNavigationBarSidePadding * 2,
                        barHeight = PrimaryNavigationBarHeight,
                        itemCount = titles.size,
                        label = widest,
                    )
                } ?: 411
                Box(contentAlignment = Alignment.BottomStart) {
                    Box(modifier = Modifier.width((narrowest + titles.size).dp)) {
                        PrimaryNavigationBar(
                            selected = Screen.bottomNavItems[2],
                            favoritesCount = 3,
                            onNavigate = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        assertEveryLabelWhole()
    }

    @Test
    fun `the bar never shows a cut-off label at 130 percent text`() {
        setBar(fontScale = 1.3f)

        assertLabelsWholeOrIconsOnly()
    }

    @Test
    fun `the bar never shows a cut-off label at 150 percent text`() {
        setBar(fontScale = 1.5f)

        assertLabelsWholeOrIconsOnly()
    }

    @Test
    fun `the bar keeps five named 48 dp targets at 200 percent text`() {
        setBar(fontScale = 2.0f)

        assertIconOnlyDestinations()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/primary_navigation_bar_light_font2_0.png")
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-xhdpi")
    fun `the rail shows every label at normal text in a tall enough window`() {
        setRail(fontScale = 1.0f, width = 891.dp, height = 411.dp)

        assertEveryLabelWhole()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-xhdpi")
    fun `the rail never shows a cut-off label at 130 percent text`() {
        setRail(fontScale = 1.3f, width = 891.dp, height = 411.dp)

        assertLabelsWholeOrIconsOnly()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-xhdpi")
    fun `the rail never shows a cut-off label at 150 percent text`() {
        setRail(fontScale = 1.5f, width = 891.dp, height = 411.dp)

        assertLabelsWholeOrIconsOnly()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-xhdpi")
    fun `the landscape rail keeps five named 48 dp targets at 200 percent text`() {
        setRail(fontScale = 2.0f, width = 891.dp, height = 411.dp)

        assertIconOnlyDestinations()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/primary_navigation_rail_light_font2_0_891x411.png")
    }

    /**
     * Between normal and 200 percent text either answer is right: every label whole, or none at all
     * with the icons carrying the names. A label that is there but cut short is the one wrong answer.
     */
    private fun assertLabelsWholeOrIconsOnly() {
        val labelled = composeRule.onAllNodesWithText(titles[0], useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
        if (labelled) assertEveryLabelWhole() else assertIconOnlyDestinations()
    }

    /** Every label is there, on its one line with all of its text, inside its own destination. */
    private fun assertEveryLabelWhole() {
        titles.forEach { title ->
            // The label alone, not the destination it is merged into for screen readers.
            val label = composeRule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()
            val layout = label.textLayout()
            assertFalse(
                "\"$title\" needs more than its one line or more height than it got (${layout.size})",
                layout.didOverflowHeight,
            )
            val needed = layout.multiParagraph.maxIntrinsicWidth
            assertTrue(
                "\"$title\" needs $needed px on one line and got ${label.size.width}",
                label.size.width + 1 >= needed,
            )
            val destination = composeRule.onNodeWithText(title).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val drawn = Rect(label.positionInRoot, label.size.toSize())
            assertTrue(
                "\"$title\" at $drawn reaches outside its destination at $destination",
                drawn.left >= destination.left - 0.5f && drawn.right <= destination.right + 0.5f &&
                    drawn.top >= destination.top - 0.5f && drawn.bottom <= destination.bottom + 0.5f,
            )
        }
    }

    /** How the text was laid out in the room it was given, as the text node itself reports it. */
    private fun SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        assertTrue("the label reports no text layout", results.isNotEmpty())
        return results.first()
    }

    /** No visible label, and each destination still announces its full name and selected state. */
    private fun assertIconOnlyDestinations() {
        titles.forEachIndexed { index, title ->
            composeRule.onNodeWithText(title).assertDoesNotExist()
            val destination = composeRule.onNodeWithContentDescription(title)
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
            if (index == 2) destination.assertIsSelected() else destination.assertIsNotSelected()
        }
    }

    private fun setBar(fontScale: Float) {
        composeRule.setContent {
            NavigationHost(fontScale = fontScale, width = 411.dp, height = 891.dp) {
                Box(contentAlignment = Alignment.BottomCenter) {
                    PrimaryNavigationBar(
                        selected = Screen.bottomNavItems[2],
                        favoritesCount = 3,
                        onNavigate = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun setRail(fontScale: Float, width: Dp, height: Dp) {
        composeRule.setContent {
            NavigationHost(fontScale = fontScale, width = width, height = height) {
                Box(contentAlignment = Alignment.TopStart) {
                    PrimaryNavigationRail(
                        selected = Screen.bottomNavItems[2],
                        favoritesCount = 3,
                        onNavigate = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}

@Composable
private fun NavigationHost(
    fontScale: Float,
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        FreeVibeTheme(darkTheme = false, dynamicColor = false) {
            Surface(modifier = Modifier.size(width, height), content = content)
        }
    }
}
