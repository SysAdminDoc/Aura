package com.freevibe.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePolishContractTest {

    @Test
    fun `community report dialog is scrollable and ime aware`() {
        val source = File("src/main/java/com/freevibe/ui/components/CommunityReportDialog.kt").readText()

        assertTrue(source.contains("verticalScroll(rememberScrollState())"))
        assertTrue(source.contains("imePadding()"))
        assertTrue(source.contains("FlowRow("))
    }

    @Test
    fun `sound upload dialog wraps chips and avoids keyboard occlusion`() {
        val source = File("src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt").readText()
        val uploadDialog = source.substringAfter("private fun UploadDialog(")

        assertTrue(uploadDialog.contains("verticalScroll(scrollState)"))
        assertTrue(uploadDialog.contains("imePadding()"))
        assertTrue(uploadDialog.contains("FlowRow("))
        assertTrue(uploadDialog.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"))
    }

    @Test
    fun `wallpaper upload dialog remains usable on compact ime screens`() {
        val source = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt").readText()
        val uploadDialog = source.substringAfter("private fun WallpaperUploadDialog(")

        assertTrue(uploadDialog.contains("verticalScroll(scrollState)"))
        assertTrue(uploadDialog.contains("imePadding()"))
        assertTrue(uploadDialog.contains("FlowRow("))
        assertTrue(uploadDialog.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"))
    }
}
