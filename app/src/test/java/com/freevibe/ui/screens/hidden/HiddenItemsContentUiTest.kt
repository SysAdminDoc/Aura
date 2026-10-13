package com.freevibe.ui.screens.hidden

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.freevibe.data.repository.HiddenItem
import com.freevibe.data.repository.HiddenItemKind
import com.freevibe.data.repository.HiddenItemLabel
import com.freevibe.ui.theme.FreeVibeTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiddenItemsContentUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val video = HiddenItem("reddit_a", HiddenItemLabel(HiddenItemKind.VIDEO, "Neon rain loop", sourceName = "Reddit"), 3L)
    private val wallpaper = HiddenItem("wh_1", HiddenItemLabel(HiddenItemKind.WALLPAPER, "", sourceName = "Wallhaven"), 2L)
    private val legacy = HiddenItem("legacy-id", null, 0L)

    @Test
    fun `rows restore one item and Restore all takes only the filtered list`() {
        val restored = mutableListOf<List<HiddenItem>>()
        var filter by mutableStateOf<HiddenItemKind?>(null)
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                HiddenItemsContent(
                    items = listOf(video, wallpaper, legacy),
                    filter = filter,
                    onFilterChange = { filter = it },
                    onRestore = { restored += it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Neon rain loop").assertIsDisplayed()
        composeRule.onNodeWithText("Wallpaper").assertIsDisplayed()
        composeRule.onNodeWithText("Hidden before names were saved").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Restore Neon rain loop").performClick()
        assertEquals(listOf(video), restored.last())

        composeRule.onNodeWithText("Videos").performClick()
        composeRule.onNodeWithText("Hidden before names were saved").assertDoesNotExist()
        composeRule.onNodeWithText("Restore all").performClick()
        assertEquals(listOf(video), restored.last())

        composeRule.onNodeWithText("All").performClick()
        composeRule.onNodeWithText("Restore all").performClick()
        assertEquals(listOf(video, wallpaper, legacy), restored.last())
    }

    @Test
    fun `an empty list explains itself and offers no Restore all`() {
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                HiddenItemsContent(items = emptyList(), filter = HiddenItemKind.VIDEO, onFilterChange = {}, onRestore = {}, onBack = {})
            }
        }

        composeRule.onNodeWithText("Nothing hidden here").assertIsDisplayed()
        composeRule.onNodeWithText("Restore all").assertDoesNotExist()
    }

    @Test
    fun `an all-hidden video feed restores its batch or opens the list`() {
        val screen = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt").readText()
        val root = File("src/main/java/com/freevibe/ui/FreeVibeRoot.kt").readText()

        assertTrue(screen.contains("localHidesFor(state.items.map { it.id }, localHiddenIds)"))
        assertTrue(screen.contains("viewModel.restoreHidden(restorable)"))
        assertTrue(screen.contains("R.string.video_wp_manage_hidden"))
        assertTrue(root.contains("Screen.HiddenItems.createRoute(HiddenItemKind.VIDEO)"))
        assertTrue(root.contains("onHiddenItemsClick = { navController.navigate(Screen.HiddenItems.createRoute())"))
    }
}
