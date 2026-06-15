package com.freevibe.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePolishContractTest {

    @Test
    fun `compact search field does not consume unconstrained vertical space`() {
        val source = File("src/main/java/com/freevibe/ui/components/SharedComponents.kt").readText()
        val searchField = source.substringAfter("fun CompactSearchField(").substringBefore("// ── Source Badge")

        assertTrue(searchField.contains(".fillMaxWidth()"))
        assertTrue(searchField.contains(".heightIn(min = AuraMinimumTouchTarget)"))
        assertTrue(!searchField.contains(".fillMaxSize()"))
    }

    @Test
    fun `settings overview active setup is a complete sentence`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()
        val overview = source.substringAfter("private fun SettingsOverviewCard(").substringBefore("private fun SettingsMetric(")

        assertTrue(overview.contains("\"Active setup: ${'$'}{enabled.joinToString("))
        assertTrue(overview.contains("if (enabled.isEmpty())"))
    }

    @Test
    fun `settings toggle exposes one labeled accessibility target`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()
        val toggle = source.substringAfter("private fun SettingsToggle(").substringBefore("private fun PermissionTransparencyRow(")

        assertTrue(toggle.contains("semantics(mergeDescendants = true)"))
        assertTrue(toggle.contains("contentDescription = toggleDescription"))
        assertTrue(toggle.contains("stateDescription = toggleStateDescription"))
        assertTrue(toggle.contains("onClick(label = toggleActionLabel"))
        assertTrue(toggle.contains("onCheckedChange = null"))
    }

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
