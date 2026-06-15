package com.freevibe.ui.screens.sounds

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundDetailScreenPolishTest {

    @Test
    fun `sound detail actions adapt for large font scale`() {
        val source = File("src/main/java/com/freevibe/ui/screens/sounds/SoundDetailScreen.kt").readText()

        assertTrue(source.contains("LocalDensity.current.fontScale >= 1.3f"))
        assertTrue(source.contains("val useStackedActions"))
        assertTrue(source.contains("maxItemsInEachRow = 2"))
        assertTrue(source.contains("heightIn(min = 48.dp)"))
        assertTrue(source.contains("heightIn(min = 64.dp)"))
        assertTrue(source.contains("maxLines = 2"))
        assertTrue(source.contains("textAlign = TextAlign.Center"))
        assertTrue(source.contains("windowInsetsBottomHeight(WindowInsets.navigationBars)"))
        assertFalse(source.contains("modifier.height(48.dp)"))
        assertFalse(source.contains("modifier.height(64.dp)"))
        assertFalse(source.contains("Spacer(Modifier.height(80.dp))"))
    }
}
