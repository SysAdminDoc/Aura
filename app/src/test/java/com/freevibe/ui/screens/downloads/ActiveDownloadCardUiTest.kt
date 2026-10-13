package com.freevibe.ui.screens.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.freevibe.service.DownloadProgress
import com.freevibe.ui.theme.FreeVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActiveDownloadCardUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a failed card shows why and Retry names the file and retries it`() {
        var retries = 0
        var dismissals = 0
        val reason = "Couldn't reach the server. Check your connection."
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                ActiveDownloadCard(
                    dl = DownloadProgress("d1", "night.png", 0f, 0, 0, error = reason),
                    onDismiss = { dismissals++ },
                    onRetry = { retries++ },
                )
            }
        }

        composeRule.onNodeWithText(reason, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasText("Retry") and hasClickAction())
            .assert(
                SemanticsMatcher("its click action is labeled Retry night.png") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Retry night.png"
                },
            )
            .performClick()

        assertEquals(1, retries)
        assertEquals(0, dismissals)
    }

    @Test
    fun `a running card offers no Retry`() {
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                ActiveDownloadCard(
                    dl = DownloadProgress("d1", "night.png", 0.4f, 1000, 400),
                    onDismiss = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onAllNodesWithText("Retry", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `a card stays out of the live region while it counts up and joins it when it finishes or fails`() {
        var dl by mutableStateOf(DownloadProgress("d1", "night.png", 0.4f, 1000, 400))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                ActiveDownloadCard(dl = dl, onDismiss = {}, onRetry = {})
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        // The dismiss button names the file too, so the card is the match that can't be clicked.
        val card = hasContentDescription("night.png", substring = true) and !hasClickAction()
        val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        composeRule.onNode(card).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))

        // A write from the test thread only reaches a paused composition once the snapshot is applied.
        Snapshot.withMutableSnapshot { dl = DownloadProgress("d1", "night.png", 1f, 1000, 1000, isComplete = true) }
        composeRule.mainClock.advanceTimeBy(FEW_FRAMES_MS)
        composeRule.onNode(card).assert(polite)

        Snapshot.withMutableSnapshot { dl = DownloadProgress("d1", "night.png", 0f, 0, 0, error = "No connection") }
        composeRule.mainClock.advanceTimeBy(FEW_FRAMES_MS)
        composeRule.onNode(card).assert(polite)
    }

    private companion object {
        // A state change, the recomposition it causes, and a frame to spare.
        const val FEW_FRAMES_MS = 100L
    }
}
