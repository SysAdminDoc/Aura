package com.freevibe.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.freevibe.service.DownloadProgress
import com.freevibe.ui.screens.sounds.PreviewPlaybackStatus
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
class LiveAnnouncementsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    private val notLive = SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion)
    private val nothingToSay = SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)

    @Test
    fun `a state card reads its title and description as one polite heading once it has settled`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                AuraStateCard(icon = Icons.Default.CloudOff, title = "Nothing here", description = "Try another search")
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        val message = hasText("Nothing here") and hasText("Try another search")
        composeRule.onNode(message).assert(isHeading()).assert(notLive)

        composeRule.mainClock.advanceTimeBy(STATE_MESSAGE_SETTLE_MS + 100)

        composeRule.onNode(message).assert(isHeading()).assert(polite)
    }

    @Test
    fun `a settled state card keeps the same words when the screen recomposes around it`() {
        var tick by mutableStateOf(0)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                Column {
                    Text("tick $tick")
                    AuraStateCard(icon = Icons.Default.CloudOff, title = "Nothing here", description = "Try another search")
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(STATE_MESSAGE_SETTLE_MS + 100)
        val message = hasText("Nothing here") and hasText("Try another search")
        val before = composeRule.onNode(message).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)

        change { tick++ }

        composeRule.onNodeWithText("tick 1").assertExists()
        // Still live without a second wait, and nothing about it changed for TalkBack to read again.
        composeRule.onNode(message).assert(polite)
        assertEquals(before, composeRule.onNode(message).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text))
    }

    @Test
    fun `a card that replaces another waits its own turn before it reads itself out`() {
        var failed by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                if (failed) {
                    AuraStateCard(icon = Icons.Default.CloudOff, title = "Couldn't load", description = "Check your connection")
                } else {
                    AuraStateCard(icon = Icons.Default.CloudOff, title = "Loading", description = "One moment")
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(STATE_MESSAGE_SETTLE_MS + 100)
        composeRule.onNode(hasText("Loading")).assert(polite)

        change { failed = true }
        composeRule.onNode(hasText("Couldn't load")).assert(notLive)

        composeRule.mainClock.advanceTimeBy(STATE_MESSAGE_SETTLE_MS + 100)
        composeRule.onNode(hasText("Couldn't load")).assert(polite)
    }

    @Test
    fun `a live status is polite and only has a description when there is something to say`() {
        var status by mutableStateOf("")
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { AuraLiveStatus(status, Modifier.testTag(STATUS)) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(STATUS).assert(polite).assert(nothingToSay)

        change { status = "Wallpapers loaded" }

        composeRule.onNodeWithTag(STATUS).assert(polite).assertContentDescriptionEquals("Wallpapers loaded")
    }

    @Test
    fun `a feed status says nothing about a list that was already there and reports a refresh over it`() {
        var loading by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AuraFeedStatus(
                isLoading = loading,
                hasContent = true,
                loading = "Loading feed",
                ready = "Feed loaded",
                modifier = Modifier.testTag(STATUS),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(STATUS).assert(polite).assert(nothingToSay)

        change { loading = true }
        composeRule.onNodeWithTag(STATUS).assertContentDescriptionEquals("Loading feed")

        change { loading = false }
        composeRule.onNodeWithTag(STATUS).assertContentDescriptionEquals("Feed loaded")
    }

    @Test
    fun `a feed status leaves a first load to the loading row and says ready when results land`() {
        var loading by mutableStateOf(true)
        var hasContent by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AuraFeedStatus(
                isLoading = loading,
                hasContent = hasContent,
                loading = "Loading feed",
                ready = "Feed loaded",
                modifier = Modifier.testTag(STATUS),
            )
        }
        composeRule.mainClock.advanceTimeBy(FEW_FRAMES_MS)
        composeRule.onNodeWithTag(STATUS).assert(nothingToSay)

        change {
            loading = false
            hasContent = true
        }

        composeRule.onNodeWithTag(STATUS).assertContentDescriptionEquals("Feed loaded")
    }

    @Test
    fun `feed status wording follows the load and stays blank without content`() {
        fun status(isLoading: Boolean, hasContent: Boolean, sawLoad: Boolean) =
            feedStatus(isLoading, hasContent, sawLoad, loading = "Loading feed", ready = "Feed loaded")

        assertEquals("", status(isLoading = true, hasContent = false, sawLoad = true))
        assertEquals("", status(isLoading = false, hasContent = false, sawLoad = true))
        assertEquals("", status(isLoading = false, hasContent = true, sawLoad = false))
        assertEquals("Loading feed", status(isLoading = true, hasContent = true, sawLoad = false))
        assertEquals("Feed loaded", status(isLoading = false, hasContent = true, sawLoad = true))
    }

    @Test
    fun `feed sections are traversal groups read header first, then status, then content`() {
        composeRule.setContent {
            // Declared back to front, so the order can only come from the sections themselves.
            Column(Modifier.feedTraversalRoot().testTag("root")) {
                Box(Modifier.feedSection(AuraFeedSection.Content).size(10.dp).testTag("content"))
                Box(Modifier.feedSection(AuraFeedSection.Status).size(10.dp).testTag("status"))
                Box(Modifier.feedSection(AuraFeedSection.Header).size(10.dp).testTag("header"))
            }
        }
        val isGroup = SemanticsMatcher.expectValue(SemanticsProperties.IsTraversalGroup, true)

        composeRule.onNodeWithTag("root")
            .assert(isGroup)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.TraversalIndex))
        val order = listOf("header", "status", "content").map { tag ->
            composeRule.onNodeWithTag(tag).assert(isGroup)
                .fetchSemanticsNode().config.getOrNull(SemanticsProperties.TraversalIndex)
        }
        assertEquals(listOf(-2f, -1f, 0f), order)
        // Anything a screen leaves unmarked sits at 0, so it has to land with the content.
        assertTrue(AuraFeedSection.entries.all { it.traversalOrder <= 0f })
    }

    @Test
    fun `a download row only becomes a live region when it finishes or fails`() {
        var download by mutableStateOf(DownloadProgress("d1", "night.png", 0.4f, 1000, 400))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                DownloadProgressBar(downloads = mapOf("d1" to download), onDismiss = {})
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("night.png").assert(notLive)

        change { download = DownloadProgress("d1", "night.png", 0.9f, 1000, 900) }
        composeRule.onNodeWithContentDescription("night.png").assert(notLive)

        change { download = DownloadProgress("d1", "night.png", 1f, 1000, 1000, isComplete = true) }
        composeRule.onNodeWithContentDescription("night.png").assert(polite)

        change { download = DownloadProgress("d1", "night.png", 0f, 0, 0, error = "No connection") }
        composeRule.onNodeWithContentDescription("night.png").assert(polite)
    }

    @Test
    fun `playback status is quiet until something plays, then names the sound and says when it stops`() {
        var preparing by mutableStateOf(false)
        var playing by mutableStateOf(false)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PreviewPlaybackStatus(
                isPreparing = preparing,
                isPlaying = playing,
                soundName = "Ocean Breeze",
                modifier = Modifier.testTag(STATUS),
            )
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(STATUS).assert(polite).assert(nothingToSay)

        change { preparing = true }
        composeRule.onNodeWithTag(STATUS).assertContentDescriptionEquals("Preparing preview")

        change {
            preparing = false
            playing = true
        }
        composeRule.onNodeWithTag(STATUS).assertContentDescriptionEquals("Playing Ocean Breeze")

        change { playing = false }
        composeRule.onNodeWithTag(STATUS).assertContentDescriptionEquals("Preview stopped")
    }

    @Test
    fun `playback status falls back to plain Playing when the sound has no name`() {
        composeRule.setContent {
            PreviewPlaybackStatus(
                isPreparing = false,
                isPlaying = true,
                soundName = null,
                modifier = Modifier.testTag(STATUS),
            )
        }

        composeRule.onNodeWithTag(STATUS).assert(polite).assertContentDescriptionEquals("Playing")
    }

    /**
     * A write from the test thread sits in the global snapshot until something applies it, and a
     * paused clock with nothing animating never does. Applying it here is what lets the frames
     * that follow recompose.
     */
    private fun change(write: () -> Unit) {
        Snapshot.withMutableSnapshot(write)
        composeRule.mainClock.advanceTimeBy(FEW_FRAMES_MS)
    }

    private companion object {
        const val STATUS = "status"

        // Enough for a state change, the recomposition it causes, and one more that a side effect asks for.
        const val FEW_FRAMES_MS = 100L
    }
}
