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

    @Test
    fun `creator profile edit dialog is scrollable and keyboard aware`() {
        val source = File("src/main/java/com/freevibe/ui/screens/community/CreatorProfileScreen.kt").readText()
        val dialog = source.substringAfter("private fun CreatorProfileEditDialog(").substringBefore("private fun CreatorMetric(")

        assertTrue(dialog.contains("verticalScroll(rememberScrollState())"))
        assertTrue(dialog.contains("imePadding()"))
        assertTrue(dialog.contains("KeyboardType.Uri"))
        assertTrue(dialog.contains("if (!isSaving) onDismiss()"))
    }

    @Test
    fun `contact picker selected contact state can scroll on compact screens`() {
        val source = File("src/main/java/com/freevibe/ui/screens/sounds/ContactPickerScreen.kt").readText()
        val selectedState = source.substringAfter("state.selectedContact ?: return@Scaffold").substringBefore("ContactAssignmentCard(")

        assertTrue(selectedState.contains(".weight(1f)"))
        assertTrue(selectedState.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(selectedState.contains(".imePadding()"))
    }

    @Test
    fun `settings radio dialogs expose full row touch targets`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()
        val radioRow = source.substringAfter("private fun SettingsRadioOptionRow(").substringBefore("@Composable\nprivate fun IntervalPickerDialog(")
        val intervalDialog = source.substringAfter("private fun IntervalPickerDialog(").substringBefore("private data class SettingsBatterySnapshot(")
        val sourceDialog = source.substringAfter("private fun SourcePickerDialog(")

        assertTrue(radioRow.contains(".heightIn(min = 48.dp)"))
        assertTrue(radioRow.contains("role = Role.RadioButton"))
        assertTrue(radioRow.contains("onClick = null"))
        assertTrue(intervalDialog.contains("SettingsRadioOptionRow("))
        assertTrue(sourceDialog.contains("SettingsRadioOptionRow("))
    }

    @Test
    fun `settings feedback uses aura snackbar chrome instead of raw toasts`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()

        assertTrue(source.contains("snackbarHost = { AuraSnackbarHost(snackbarHostState) }"))
        assertTrue(source.contains("fun showSettingsFeedback(message: String)"))
        assertTrue(source.contains("copyCrashDiagnosticsBundle("))
        assertTrue(source.contains("onFeedback: (String) -> Unit"))
        assertTrue(!source.contains("Toast.makeText"))
    }

    @Test
    fun `settings inline picker dialogs use shared full row radio targets`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()
        val screen = source.substringAfter("fun SettingsScreen(").substringBefore("@Composable\n@OptIn(ExperimentalLayoutApi::class)\nprivate fun CommunityIdentityDialog(")

        assertTrue(screen.contains("showSchedulerInterval"))
        assertTrue(screen.contains("showSchedulerSource"))
        assertTrue(screen.contains("showFpsPicker"))
        assertTrue(screen.contains("showColumnsPicker"))
        assertTrue(screen.contains("showResPicker"))
        assertTrue(screen.split("SettingsRadioOptionRow(").size >= 8)
        assertTrue(!screen.contains("RadioButton(selected = schedulerInterval"))
        assertTrue(!screen.contains("RadioButton(selected = videoFpsLimit"))
        assertTrue(!screen.contains("RadioButton(selected = gridColumns"))
        assertTrue(!screen.contains("RadioButton(selected = preferredRes"))
    }

    @Test
    fun `favorites empty states expose restore action instead of a dead end`() {
        val source = File("src/main/java/com/freevibe/ui/screens/favorites/FavoritesScreen.kt").readText()

        assertTrue(source.contains("No favorite wallpapers yet"))
        assertTrue(source.contains("No favorite sounds yet"))
        assertTrue(source.contains("primaryAction = AuraStateAction("))
        assertTrue(source.contains("label = \"Import backup\""))
        assertTrue(source.contains("importLauncher.launch(arrayOf(\"application/json\"))"))
    }

    @Test
    fun `settings credential and youtube edit dialogs avoid ime occlusion`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsScreen.kt").readText()
        val apiDialog = source.substringAfter("private fun ProviderApiKeyDialog(").substringBefore("@OptIn(")
        val ytQueriesDialog = source.substringAfter("// YouTube sound search queries editor").substringBefore("// YouTube blocked words editor")
        val blockedWordsDialog = source.substringAfter("// YouTube blocked words editor").substringBefore("// Confirm clear cache")

        assertTrue(apiDialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(apiDialog.contains(".imePadding()"))
        assertTrue(apiDialog.contains("keyboardType = KeyboardType.Password"))
        assertTrue(apiDialog.contains("imeAction = ImeAction.Done"))
        assertTrue(ytQueriesDialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(ytQueriesDialog.contains(".imePadding()"))
        assertTrue(ytQueriesDialog.contains("ImeAction.Next"))
        assertTrue(blockedWordsDialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(blockedWordsDialog.contains(".imePadding()"))
        assertTrue(blockedWordsDialog.contains("imeAction = ImeAction.Done"))
    }

    @Test
    fun `browse filter controls keep release touch targets and bounded shapes`() {
        val wallpaperSource = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt").readText()
        val wallpaperModeBar = wallpaperSource.substringAfter("Spacer(Modifier.height(8.dp))").substringBefore("// Download progress")
        val wallpaperRefineSheet = wallpaperSource.substringAfter("private fun WallpaperFiltersSheet(").substringBefore("private fun ColorPickerRow(")
        val videoSource = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt").readText()
        val videoModeBar = videoSource.substringAfter("keyboardActions = KeyboardActions(onSearch = {").substringBefore("if (state.degradedSources.isNotEmpty())")
        val videoRefineSheet = videoSource.substringAfter("private fun VideoFiltersSheet(").substringBefore("private fun videoSourceHealthSummary(")
        val soundSource = File("src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt").readText()
        val soundModeBar = soundSource.substringAfter("private fun SoundFilterButton(").substringBefore("private fun soundTabLabel(")
        val aiSource = File("src/main/java/com/freevibe/ui/screens/aigenerate/AiWallpaperScreen.kt").readText()
        val aiStylePicker = aiSource.substringAfter("// ── Style picker").substringBefore("// ── Generate button")

        assertTrue(!wallpaperModeBar.contains("heightIn(min = 34.dp)"))
        assertTrue(wallpaperModeBar.contains("heightIn(min = 48.dp)"))
        assertTrue(wallpaperModeBar.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(wallpaperRefineSheet.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(!videoModeBar.contains("heightIn(min = 34.dp)"))
        assertTrue(videoModeBar.contains("heightIn(min = 48.dp)"))
        assertTrue(videoModeBar.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(videoRefineSheet.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(soundModeBar.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(aiStylePicker.contains("shape = RoundedCornerShape(8.dp)"))
    }

    @Test
    fun `long disclosure dialogs keep policy copy scrollable on compact screens`() {
        val guidelines = File("src/main/java/com/freevibe/ui/components/CommunityGuidelinesDialog.kt").readText()
        val aiDisclosure = File("src/main/java/com/freevibe/ui/screens/aigenerate/AiWallpaperScreen.kt")
            .readText()
            .substringAfter("fun GeneratedWallpaperDisclosureDialog(")
            .substringBefore("@OptIn(")

        assertTrue(guidelines.contains("verticalScroll(rememberScrollState())"))
        assertTrue(guidelines.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(aiDisclosure.contains("verticalScroll(rememberScrollState())"))
        assertTrue(aiDisclosure.contains("shape = RoundedCornerShape(8.dp)"))
    }

    @Test
    fun `collection import and picker forms avoid compact ime occlusion`() {
        val collections = File("src/main/java/com/freevibe/ui/screens/collections/CollectionsScreen.kt").readText()
        val importSheet = collections.substringAfter("private fun ImportCollectionSheet(").substringBefore("private fun CollectionQrDialog(")
        val qrDialog = collections.substringAfter("private fun CollectionQrDialog(").substringBefore("private fun WallpaperCollectionItemEntity.toWallpaper")
        val detail = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt").readText()
        val pickerSheet = detail.substringAfter("private fun CollectionPickerSheet(").substringBefore("internal fun wallpaperDetailTitle(")

        assertTrue(importSheet.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(importSheet.contains(".imePadding()"))
        assertTrue(qrDialog.contains("verticalScroll(rememberScrollState())"))
        assertTrue(qrDialog.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(pickerSheet.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(pickerSheet.contains(".imePadding()"))
    }

    @Test
    fun `wallpaper detail horizontal action chips keep labels when clipped`() {
        val source = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt").readText()
        val actionPill = source.substringAfter("private fun DetailActionPill(").substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(actionPill.contains("semantics(mergeDescendants = true)"))
        assertTrue(actionPill.contains("contentDescription = label"))
    }

    @Test
    fun `release ui avoids fully circular chrome backdrops`() {
        val uiFiles = listOf(
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt",
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt",
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperPreviewScreen.kt",
            "src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpaperPreviewScreen.kt",
            "src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt",
            "src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt",
        )

        uiFiles.forEach { path ->
            assertTrue("$path should use bounded corner radii instead of CircleShape", !File(path).readText().contains("CircleShape"))
        }
    }
}
