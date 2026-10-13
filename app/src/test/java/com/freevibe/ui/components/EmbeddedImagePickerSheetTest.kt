package com.freevibe.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtMost
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.freevibe.ui.theme.FreeVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EmbeddedImagePickerSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the picker shrinks to the window and gives way when there is no room`() {
        assertEquals("a tall phone keeps the old height", MAX_EMBEDDED_PICKER_DP, embeddedPickerHeightDp(891, 1f))
        assertEquals("320 x 480 still gets a short picker", 202, embeddedPickerHeightDp(480, 1f))
        assertEquals(400, embeddedPickerHeightDp(700, 1f))
        assertEquals("200 percent text leaves less room", 290, embeddedPickerHeightDp(700, 2f))
        assertNull("a landscape phone has no room", embeddedPickerHeightDp(360, 1f))
        assertNull("nor does a short split-screen half", embeddedPickerHeightDp(400, 1f))
        assertNull("320 x 480 at 200 percent text", embeddedPickerHeightDp(480, 2f))
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h480dp")
    fun `on a 320 x 480 window the picker and the button both fit`() {
        var fallbacks = 0
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    EmbeddedImagePickerSheetContent(
                        title = "Choose a wallpaper",
                        body = "Pick a photo to share with the community.",
                        fallbackLabel = "Open full picker",
                        windowHeightDp = 480,
                        pickerMaxHeightDp = embeddedPickerHeightDp(480, 1f),
                        onFallback = { fallbacks++ },
                    ) {
                        Box(Modifier.fillMaxSize().testTag("picker"))
                    }
                }
            }
        }

        composeRule.onNodeWithTag("picker").assertIsDisplayed().assertHeightIsAtMost(202.dp)
        val button = composeRule.onNodeWithText("Open full picker").assertIsDisplayed()
        val bottom = button.getUnclippedBoundsInRoot().bottom
        assertTrue("button ends at $bottom, inside the 480dp window", bottom <= 480.dp)
        button.performClick()
        assertEquals(1, fallbacks)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w640dp-h360dp")
    fun `a landscape window offers the full picker as the main action`() {
        var fallbacks = 0
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                EmbeddedImagePickerSheetContent(
                    title = "Choose a wallpaper",
                    body = "Pick a photo to share with the community.",
                    fallbackLabel = "Open full picker",
                    windowHeightDp = 360,
                    pickerMaxHeightDp = embeddedPickerHeightDp(360, 1f),
                    onFallback = { fallbacks++ },
                ) {
                    Box(Modifier.fillMaxSize().testTag("picker"))
                }
            }
        }

        composeRule.onNodeWithTag("picker").assertDoesNotExist()
        composeRule.onNodeWithText("This window is too short for the picker, so open the full one instead.").assertIsDisplayed()
        composeRule.onNodeWithText("Open full picker").assertIsDisplayed().performClick()
        assertEquals(1, fallbacks)
    }
}
