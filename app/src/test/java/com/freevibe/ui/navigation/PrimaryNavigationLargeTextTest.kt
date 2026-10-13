package com.freevibe.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.freevibe.ui.PrimaryNavigationBar
import com.freevibe.ui.PrimaryNavigationRail
import com.freevibe.ui.theme.FreeVibeTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** All five destinations stay reachable and named when the text is too large for their labels. */
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

        titles.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText(titles[2]).assertIsSelected()
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

        titles.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w891dp-h411dp-xhdpi")
    fun `the landscape rail keeps five named 48 dp targets at 200 percent text`() {
        setRail(fontScale = 2.0f, width = 891.dp, height = 411.dp)

        assertIconOnlyDestinations()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/primary_navigation_rail_light_font2_0_891x411.png")
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
