package com.freevibe.ui.screens.favorites

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.ui.theme.FreeVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoriteVideosTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val youtube = FavoriteEntity(
        id = "yt_dQw4w9WgXcQ",
        source = "YOUTUBE",
        type = "VIDEO",
        thumbnailUrl = "",
        fullUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        name = "Neon rain loop",
        width = 1080,
        height = 1920,
        duration = 42.0,
    )
    private val reddit = FavoriteEntity(
        id = "rd_1abc2de",
        source = "REDDIT",
        type = "VIDEO",
        thumbnailUrl = "",
        fullUrl = "https://v.redd.it/abc123xyz/HLSPlaylist.m3u8",
        name = "Rain on glass",
    )

    private fun show(
        videos: List<FavoriteEntity>,
        onOpen: (FavoriteEntity) -> Unit = {},
        onRemove: (FavoriteEntity) -> Unit = {},
        onImportBackup: () -> Unit = {},
    ) {
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                FavoriteVideosTab(
                    videos = videos,
                    onOpen = onOpen,
                    onRemove = onRemove,
                    onImportBackup = onImportBackup,
                )
            }
        }
    }

    @Test
    fun `a saved video's card opens it and its heart removes it`() {
        val opened = mutableListOf<String>()
        val removed = mutableListOf<String>()
        show(listOf(youtube, reddit), onOpen = { opened += it.id }, onRemove = { removed += it.id })

        composeRule
            .onNodeWithContentDescription("Neon rain loop. Saved video. 42 seconds. 1080 by 1920. YouTube")
            .performClick()
        assertEquals(listOf("yt_dQw4w9WgXcQ"), opened)
        assertTrue(removed.isEmpty())

        composeRule.onNodeWithContentDescription("Remove Rain on glass").performClick()
        assertEquals(listOf("rd_1abc2de"), removed)
        assertEquals("the heart doesn't open the video as well", listOf("yt_dQw4w9WgXcQ"), opened)
    }

    @Test
    fun `each card shows the video's name, provider and length`() {
        show(listOf(youtube, reddit))

        composeRule.onNodeWithText("Neon rain loop", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("YouTube · 42s", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Rain on glass", useUnmergedTree = true).assertExists()
        // No length on record for this one, so only the provider is shown.
        composeRule.onNodeWithText("Reddit", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a row the preview can't read is marked instead of looking playable`() {
        val unreadable = reddit.copy(name = "Stray clip", fullUrl = "https://media.example/abc123xyz/HLSPlaylist.m3u8")
        show(listOf(unreadable))

        composeRule.onNodeWithContentDescription("Stray clip. Source unavailable. Reddit").assertExists()
        composeRule.onNodeWithText("Source unavailable", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `with nothing saved the tab says so and offers a backup import`() {
        var imports = 0
        show(emptyList(), onImportBackup = { imports++ })

        composeRule.onNodeWithText("No favorite videos yet").assertExists()
        composeRule.onNodeWithText("Import backup").performClick()
        assertEquals(1, imports)
    }
}
