package com.freevibe.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class SearchOpenFocusUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun setHost(startOpen: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                var open by remember { mutableStateOf(startOpen) }
                val focus = rememberSearchOpenFocus()
                Column {
                    Button(
                        onClick = {
                            open = !open
                            if (open) focus.request()
                        },
                    ) { Text("Toggle") }
                    if (open) {
                        CompactSearchField(
                            value = "",
                            onValueChange = {},
                            placeholder = "Search",
                            onClear = {},
                            modifier = Modifier.focusRequester(focus.requester),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `opening search puts the cursor in the field`() {
        setHost()

        compose.onNodeWithText("Toggle").performClick()
        compose.waitForIdle()

        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `a search that's already open stays unfocused so coming back doesn't pop the keyboard`() {
        setHost(startOpen = true)
        compose.waitForIdle()

        compose.onNode(hasSetTextAction()).assertIsNotFocused()
    }

    @Test
    fun `closing and reopening search focuses the field again`() {
        setHost()

        compose.onNodeWithText("Toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Toggle").performClick()
        compose.waitForIdle()

        compose.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `every search screen asks for focus when its icon opens the field`() {
        listOf(
            "src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt",
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt",
            "src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt",
        ).forEach { path ->
            val source = File(path).readText()

            assertTrue(path, source.contains("searchOpenFocus.request()"))
            assertTrue(path, source.contains(".focusRequester(searchOpenFocus.requester)"))
        }
    }
}
