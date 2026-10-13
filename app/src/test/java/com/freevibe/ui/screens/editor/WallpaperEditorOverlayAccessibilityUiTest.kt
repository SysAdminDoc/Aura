package com.freevibe.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.freevibe.ui.theme.FreeVibeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the editor's layers through semantics only, the way TalkBack or Switch Access would. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperEditorOverlayAccessibilityUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var layers by mutableStateOf(
        listOf(
            WallpaperOverlayLayer(id = 1L, type = WallpaperOverlayType.TEXT, text = "Hello", y = 0.3f),
            WallpaperOverlayLayer(id = 2L, type = WallpaperOverlayType.STICKER, sticker = WallpaperSticker.HEART, y = 0.7f),
        ),
    )
    private var selected by mutableStateOf<Long?>(null)

    @Test
    fun `screen reader actions alone select move resize and delete a layer`() {
        showPreview()
        val sticker = composeRule.onNodeWithContentDescription("Heart sticker layer")
        sticker.assertIsNotSelected()

        sticker.performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2L, selected)
        sticker.assertIsSelected()

        sticker.runCustomAction("Move layer right")
        assertEquals(0.5f + OVERLAY_MOVE_STEP, layer(2L).x, 0.0001f)
        sticker.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "54% from left, 70% from top, 1.00x scale"),
        )

        sticker.runCustomAction("Make layer bigger")
        assertEquals(1f + OVERLAY_SCALE_STEP, layer(2L).scale, 0.0001f)

        sticker.runCustomAction("Delete layer")
        assertEquals(listOf(1L), layers.map { it.id })
        composeRule.onAllNodesWithContentDescription("Heart sticker layer").assertCountEquals(0)
    }

    @Test
    fun `every tappable layer is labeled and text layers aren't read twice`() {
        showPreview()

        val tappable = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals(2, tappable.size)
        tappable.forEach { node ->
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            assertTrue("node ${node.id} has no spoken label", label.any { it.isNotBlank() })
        }
        composeRule.onNodeWithContentDescription("Text layer, Hello")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    private fun showPreview() {
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    WallpaperEditorPreview(
                        bitmap = ImageBitmap(72, 128),
                        contentDescription = "Edited wallpaper",
                        modifier = Modifier.fillMaxSize(),
                        bitmapWidth = 72,
                        bitmapHeight = 128,
                        overlays = layers,
                        selectedOverlayId = selected,
                        onSelectOverlay = { selected = it },
                        onMoveOverlay = { id, dx, dy ->
                            update(id) { it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) }
                        },
                        onResizeOverlay = { id, delta -> update(id) { it.copy(scale = (it.scale + delta).coerceIn(0.5f, 2.25f)) } },
                        onDeleteOverlay = { id -> layers = layers.filterNot { it.id == id } },
                    )
                }
            }
        }
    }

    private fun update(id: Long, transform: (WallpaperOverlayLayer) -> WallpaperOverlayLayer) {
        layers = layers.map { if (it.id == id) transform(it) else it }
        selected = id
    }

    private fun layer(id: Long) = layers.single { it.id == id }

    private fun SemanticsNodeInteraction.runCustomAction(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
        composeRule.waitForIdle()
    }
}
