package com.freevibe.ui.screens

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasePolishContractTest {

    private fun settingsSource(): String =
        File("src/main/java/com/freevibe/ui/screens/settings")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .sortedBy { it.name }
            .joinToString("\n") { it.readText() }

    @Test
    fun `settings screen root is feature decomposed`() {
        val settingsDir = File("src/main/java/com/freevibe/ui/screens/settings")
        val rootLineCount = settingsDir.resolve("SettingsScreen.kt").readLines().size
        val featureFiles = listOf(
            "SettingsWallpaperSection.kt",
            "SettingsSchedulerSection.kt",
            "SettingsBackupSection.kt",
            "SettingsSmartLiveSection.kt",
            "SettingsSoundSection.kt",
            "SettingsVideoSection.kt",
            "SettingsRedditSection.kt",
            "SettingsServicesSection.kt",
            "SettingsStorageSection.kt",
            "SettingsDiagnosticsSection.kt",
            "SettingsPermissionsAboutSection.kt",
        )

        assertTrue("SettingsScreen.kt should stay below 500 lines", rootLineCount < 500)
        featureFiles.forEach { fileName ->
            assertTrue("$fileName should own a Settings feature slice", settingsDir.resolve(fileName).isFile)
        }
    }

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
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsDialogs.kt").readText()
        val overview = source.substringAfter("internal fun SettingsOverviewCard(")

        assertTrue(
            "SettingsOverviewCard must reference both the active summary and the empty-state string resource",
            overview.contains("settings_dialogs_overview_active_summary") &&
                overview.contains(".isEmpty()"),
        )
    }

    @Test
    fun `settings toggle exposes one labeled accessibility target`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsComponents.kt").readText()
        val toggle = source.substringAfter("internal fun SettingsToggle(").substringBefore("internal fun SettingsValueSlider(")

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
    fun `sounds expose explicit youtube extractor fallback and outage states`() {
        val screen = File("src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt").readText()
        val state = File("src/main/java/com/freevibe/ui/screens/sounds/SoundsState.kt").readText()
        val strings = File("src/main/res/values/strings.xml").readText()

        assertTrue(screen.contains("YouTubeExtractionMode.BACKUP_ACTIVE"))
        assertTrue(screen.contains("sounds_youtube_unavailable_message"))
        assertTrue(state.contains("youtubeExtractionStatus"))
        assertTrue(strings.contains("YouTube changed something"))
        assertTrue(strings.contains("NewPipe and yt-dlp both failed"))
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
    fun `expanded wallpaper details stay bounded and scroll to every action`() {
        val source = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt").readText()
        val overlay = source
            .substringAfter("Column(modifier = Modifier.fillMaxSize())")
            .substringBefore("if (showApplyOptions)")

        assertTrue(source.contains("val detailsScrollState = rememberScrollState()"))
        assertTrue(overlay.contains("if (!showDetailsPanel) Spacer(Modifier.weight(1f))"))
        assertTrue(overlay.contains("if (showDetailsPanel) Modifier.weight(1f) else Modifier"))
        assertTrue(overlay.contains("if (showDetailsPanel) Modifier.verticalScroll(detailsScrollState) else Modifier"))
        assertTrue(overlay.contains("FlowRow("))
        assertTrue(overlay.contains("maxItemsInEachRow = 4"))
        assertTrue(!overlay.contains(".horizontalScroll(scrollState)"))
    }

    @Test
    fun `library and settings descriptions remain bounded on phone screens`() {
        val shared = File("src/main/java/com/freevibe/ui/components/SharedComponents.kt").readText()
        val header = shared.substringAfter("fun AuraScreenHeader(").substringBefore("fun SourceBadge(")
        val settings = File("src/main/java/com/freevibe/ui/screens/settings/SettingsComponents.kt").readText()

        assertTrue(header.contains("maxLines = 2"))
        assertTrue(settings.contains("maxLines = 2"))
        assertTrue(settings.contains("subtitleMaxLines: Int = 2"))
        assertTrue(settings.contains("maxLines = subtitleMaxLines"))
    }

    @Test
    fun `content heavy onboarding pages stay compact and let style copy grow`() {
        val source = File("src/main/java/com/freevibe/ui/screens/onboarding/OnboardingScreen.kt").readText()
        val stylePicker = source.substringAfter("private fun StylePickerPage(").substringBefore("private fun ReadyPage(")
        val pageLayout = source.substringAfter("private fun PageLayout(")

        assertTrue(source.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(stylePicker.contains(".heightIn(min = 148.dp)"))
        assertTrue(!stylePicker.contains(".height(132.dp)"))
        assertTrue(!stylePicker.contains("maxLines = 3"))
        assertTrue(pageLayout.contains("val hasSupportingContent = content != null"))
        assertTrue(pageLayout.contains(".size(if (hasSupportingContent) 68.dp else 96.dp)"))
    }

    @Test
    fun `video apply guidance uses localized user facing summaries`() {
        val source = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt").readText()
        val quality = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpaperQuality.kt").readText()

        assertTrue(source.contains("localizedVideoSummary(item)"))
        assertTrue(source.contains("video_wp_technical_adaptive_stream"))
        assertTrue(source.contains("video_wp_badge_loop_safe"))
        assertTrue(!quality.contains("Unknown video dimensions"))
        assertTrue(!quality.contains("Loop-safe"))
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
        val components = File("src/main/java/com/freevibe/ui/screens/settings/SettingsComponents.kt").readText()
        val radioRow = components.substringAfter("internal fun SettingsRadioOptionRow(").substringBefore("internal fun SettingsMetric(")
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsDialogs.kt").readText()
        val intervalDialog = source.substringAfter("internal fun IntervalPickerDialog(").substringBefore("internal fun WallpaperSlotPickerDialog(")
        val sourceDialog = source.substringAfter("internal fun SourcePickerDialog(")

        assertTrue(radioRow.contains(".heightIn(min = 48.dp)"))
        assertTrue(radioRow.contains("role = Role.RadioButton"))
        assertTrue(radioRow.contains("onClick = null"))
        assertTrue(intervalDialog.contains("SettingsRadioOptionRow("))
        assertTrue(sourceDialog.contains("SettingsRadioOptionRow("))
    }

    @Test
    fun `settings feedback uses aura snackbar chrome instead of raw toasts`() {
        val screen = settingsSource()
        val diagnostics = File("src/main/java/com/freevibe/ui/screens/settings/DiagnosticsComponents.kt").readText()

        assertTrue(screen.contains("snackbarHost = { AuraSnackbarHost(snackbarHostState) }"))
        assertTrue(screen.contains("fun showSettingsFeedback(message: String)"))
        assertTrue(screen.contains("copyCrashDiagnosticsBundle("))
        assertTrue(diagnostics.contains("onFeedback: (String) -> Unit"))
        assertTrue(!screen.contains("Toast.makeText"))
    }

    @Test
    fun `settings source diagnostics expose fallback and retry guidance`() {
        val diagnostics = File("src/main/java/com/freevibe/ui/screens/settings/DiagnosticsComponents.kt").readText()
        val sourceMetrics = File("src/main/java/com/freevibe/service/SourceMetrics.kt").readText()
        val viewModel = File("src/main/java/com/freevibe/ui/screens/settings/SettingsViewModel.kt").readText()

        assertTrue(diagnostics.contains("settings_diag_source_last_activity"))
        assertTrue(diagnostics.contains("settings_diag_source_fallback_status"))
        assertTrue(diagnostics.contains("settings_diag_source_retry_action"))
        assertTrue(diagnostics.contains("settings_diag_source_retry_button"))
        assertTrue(sourceMetrics.contains("enum class SourceFallbackStatus"))
        assertTrue(sourceMetrics.contains("enum class SourceRetryAction"))
        assertTrue(sourceMetrics.contains("fun reset(source: String)"))
        assertTrue(viewModel.contains("fun resetSourceDiagnostics(source: String)"))
    }

    @Test
    fun `settings inline picker dialogs use shared full row radio targets`() {
        val screen = settingsSource()

        assertTrue(screen.contains("showSchedulerInterval"))
        assertTrue(screen.contains("schedulerSourceTarget"))
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

        assertTrue(source.contains("favorites_empty_wallpapers_title") || source.contains("No favorite wallpapers yet"))
        assertTrue(source.contains("favorites_empty_sounds_title") || source.contains("No favorite sounds yet"))
        assertTrue(source.contains("primaryAction = AuraStateAction("))
        assertTrue(source.contains("favorites_import_backup") || source.contains("Import backup"))
        assertTrue(source.contains("importLauncher.launch(arrayOf(\"application/json\"))"))
    }

    @Test
    fun `settings exposes backup and rotation legibility controls`() {
        val source = settingsSource()

        assertTrue(source.contains("val autoWallpaperDarkenPercent by viewModel.autoWallpaperDarkenPercent.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val autoWallpaperNightVariantEnabled by viewModel.autoWallpaperNightVariantEnabled.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()"))
        assertTrue(source.contains("SettingsValueSlider("))
        assertTrue(source.contains("settings_wp_dimming_title") || source.contains("Rotation dimming"))
        assertTrue(source.contains("settings_wp_night_variant_title"))
        assertTrue(source.contains("backup") || source.contains("Backup"))
        assertTrue(source.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION"))
        assertTrue(source.contains("hasPersistedWritePermission(context, autoBackupFolderUri)"))
        assertTrue(source.contains("enableAutoBackupAfterFolder"))
        assertTrue(source.contains("onChooseAutoBackupFolder(true)"))
        assertTrue(source.contains("showAutoBackupIntervalPicker"))
        assertTrue(source.contains("showAutoBackupKeepPicker"))
    }

    @Test
    fun `night variant preserves the original locator across theme transitions`() {
        val worker = File("src/main/java/com/freevibe/service/AutoWallpaperWorker.kt").readText()
        val listener = File("src/main/java/com/freevibe/service/SystemThemeListener.kt").readText()
        val applier = File("src/main/java/com/freevibe/service/WallpaperApplier.kt").readText()

        assertTrue(worker.contains("shouldUseNightWallpaperVariant("))
        assertTrue(worker.contains("setLastNightVariantWallpaper("))
        assertTrue(listener.contains("lastNightVariantWallpaperLocator.first()"))
        assertTrue(listener.contains("nightVariant = isNight"))
        assertTrue(applier.contains("nightWallpaperVariantColorMatrix()"))
    }

    @Test
    fun `image ingestion policies guard apply rotation crop and editor decodes`() {
        val applier = File("src/main/java/com/freevibe/service/WallpaperApplier.kt").readText()
        val worker = File("src/main/java/com/freevibe/service/AutoWallpaperWorker.kt").readText()
        val editor = File("src/main/java/com/freevibe/ui/screens/editor/WallpaperEditorViewModel.kt").readText()
        val crop = File("src/main/java/com/freevibe/ui/screens/editor/WallpaperCropViewModel.kt").readText()

        assertTrue(applier.contains("decodeImageBytesForFlow("))
        assertTrue(applier.contains("decodeImageUriForFlow("))
        assertTrue(applier.contains("decodeImageFileForFlow("))
        assertTrue(applier.contains("MediaIngestionImageFlow.LOCAL_APPLY"))
        assertTrue(worker.contains("imageFlow = MediaIngestionImageFlow.AUTO_ROTATION"))
        assertTrue(editor.contains("flow = MediaIngestionImageFlow.EDITOR"))
        assertTrue(crop.split("flow = MediaIngestionImageFlow.EDITOR").size >= 3)
    }

    @Test
    fun `viewmodel feedback is resolved through application resources`() {
        val feedbackFiles = listOf(
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperApplyActions.kt",
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperStyleActions.kt",
            "src/main/java/com/freevibe/ui/screens/sounds/SoundCommunityActions.kt",
            "src/main/java/com/freevibe/ui/screens/sounds/SoundsViewModel.kt",
            "src/full/java/com/freevibe/ui/screens/aigenerate/AiWallpaperViewModel.kt",
        ).map { File(it).readText() }
        val source = feedbackFiles.joinToString("\n")

        assertTrue(source.contains("@ApplicationContext private val context: Context") || source.contains("private val context: Context"))
        assertTrue(source.contains("context.getString("))
        listOf(
            "Applied to $",
            "Reverted to previous wallpaper\"",
            "Recording failed:",
            "Hidden from this feed\"",
            "Upload complete\"",
            "Report submitted\"",
        ).forEach { literal -> assertTrue("feedback literal should be resource-backed: $literal", !source.contains(literal)) }
    }

    @Test
    fun `settings viewmodel schedules and cancels local backup work`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsRotationDelegate.kt").readText()
        val backupSlice = source.substringAfter("fun setAutoBackupEnabled(").substringBefore("fun setSchedulerEnabled")

        assertTrue(source.contains("val autoBackupEnabled = prefs.autoBackupEnabled.stateIn"))
        assertTrue(source.contains("val autoBackupFolderUri = prefs.autoBackupFolderUri.stateIn"))
        assertTrue(backupSlice.contains("AutoBackupWorker.schedule(context)"))
        assertTrue(backupSlice.contains("AutoBackupWorker.cancel(context)"))
        assertTrue(backupSlice.contains("prefs.autoBackupFolderUri.first().trim()"))
        assertTrue(backupSlice.contains("previousUri.isNotBlank() && previousUri != nextUri"))
        assertTrue(backupSlice.contains("releasePersistedUriPermission("))
        assertTrue(backupSlice.contains("setAutoBackupIntervalHours"))
        assertTrue(backupSlice.contains("setAutoBackupKeepCount"))
    }

    @Test
    fun `settings folder grants are released when replaced or cleared`() {
        val source = File("src/main/java/com/freevibe/ui/screens/settings/SettingsRotationDelegate.kt").readText()

        assertTrue(source.contains("private fun releasePersistedUriPermission(uriString: String, flags: Int)"))
        assertTrue(source.contains("prefs.localWallpaperFolderUri.first().trim()"))
        assertTrue(source.contains("prefs.autoBackupFolderUri.first().trim()"))
        assertTrue(source.contains("releasePersistedUriPermission(previousUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)"))
        assertTrue(source.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION"))
        assertTrue(source.contains("context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)"))
    }

    @Test
    fun `settings permission recovery reports dead settings intents`() {
        val source = settingsSource()

        assertTrue(source.contains("fun openNotificationSettings(): Boolean"))
        assertTrue(source.contains("fun openAppSettings(): Boolean"))
        assertTrue(source.contains("val settingsOpened = when (prompt)"))
        assertTrue(source.contains("if (!settingsOpened)"))
    }

    @Test
    fun `auto backup worker clamps persisted retention before pruning`() {
        val source = File("src/main/java/com/freevibe/service/AutoBackupWorker.kt").readText()

        assertTrue(source.contains("prefs.autoBackupKeepCount.first().coerceAtLeast(1)"))
        assertTrue(source.contains("val safeKeepCount = keepCount.coerceAtLeast(1)"))
        assertTrue(source.contains("backupFiles.size <= safeKeepCount"))
        assertTrue(source.contains("backupFiles.drop(safeKeepCount)"))
    }

    @Test
    fun `settings credential and youtube edit dialogs avoid ime occlusion`() {
        val servicesSource = File("src/main/java/com/freevibe/ui/screens/settings/SettingsServicesSection.kt").readText()
        val soundSource = File("src/main/java/com/freevibe/ui/screens/settings/SettingsSoundSection.kt").readText()
        val apiDialog = servicesSource.substringAfter("internal fun ProviderApiKeyDialog(")
        val ytQueriesDialog = soundSource.substringAfter("private fun YouTubeSoundQueriesDialog(").substringBefore("private fun YouTubeBlockedWordsDialog(")
        val blockedWordsDialog = soundSource.substringAfter("private fun YouTubeBlockedWordsDialog(")

        assertTrue(apiDialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(apiDialog.contains(".imePadding()"))
        assertTrue(apiDialog.contains("keyboardType = KeyboardType.Password"))
        assertTrue(apiDialog.contains("imeAction = ImeAction.Done"))
        assertTrue(apiDialog.contains("PasswordVisualTransformation()"))
        assertTrue(apiDialog.contains("settings_apikey_show"))
        assertTrue(apiDialog.contains("settings_apikey_hide"))
        assertTrue(ytQueriesDialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(ytQueriesDialog.contains(".imePadding()"))
        assertTrue(ytQueriesDialog.contains("ImeAction.Next"))
        assertTrue(blockedWordsDialog.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(blockedWordsDialog.contains(".imePadding()"))
        assertTrue(blockedWordsDialog.contains("imeAction = ImeAction.Done"))
    }

    @Test
    fun `settings exposes validated wallpaper and video subreddit editors`() {
        val screen = settingsSource()
        val preferences = File("src/main/java/com/freevibe/data/local/PreferencesManager.kt").readText()

        assertTrue(screen.contains("redditSubreddits = redditSubreddits"))
        assertTrue(screen.contains("redditVideoSubreddits = redditVideoSubreddits"))
        assertTrue(screen.contains("RedditSubredditListEditor("))
        assertTrue(screen.contains("viewModel::setRedditSubs"))
        assertTrue(screen.contains("viewModel::setRedditVideoSubs"))
        assertTrue(screen.contains("verticalScroll(rememberScrollState())"))
        assertTrue(screen.contains("imePadding()"))
        assertTrue(preferences.contains("normalizeRedditSubredditPreference"))
        assertTrue(preferences.contains("MAX_CONFIGURED_SUBREDDITS"))
    }

    @Test
    fun `browse filter controls keep release touch targets and quiet shapes`() {
        val browseRailSource = File("src/main/java/com/freevibe/ui/components/BrowseRail.kt").readText()
        val wallpaperSource = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt").readText()
        val wallpaperRefineSheet = wallpaperSource.substringAfter("private fun WallpaperFiltersSheet(").substringBefore("private fun ColorPickerRow(")
        val videoSource = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt").readText()
        val videoToolbar = videoSource.substringAfter("Scaffold(").substringBefore("if (state.degradedSources.isNotEmpty())")
        val videoRefineSheet = videoSource.substringAfter("private fun VideoFiltersSheet(").substringBefore("private fun videoSourceHealthSummary(")
        val soundSource = File("src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt").readText()
        val soundModeBar = soundSource.substringAfter("private fun SoundModeBar(").substringBefore("// -- Sounds List --")
        val aiSource = File("src/full/java/com/freevibe/ui/screens/aigenerate/AiWallpaperScreen.kt").readText()
        val aiStylePicker = aiSource.substringAfter("// ── Style picker").substringBefore("// ── Generate button")

        assertTrue(!browseRailSource.contains("FilterChip("))
        assertTrue(browseRailSource.contains("heightIn(min = 48.dp)"))
        assertTrue(browseRailSource.contains("shape = RoundedCornerShape(0.dp)"))
        assertTrue(wallpaperRefineSheet.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(!videoToolbar.contains("heightIn(min = 34.dp)"))
        assertTrue(videoToolbar.contains("heightIn(min = 48.dp)"))
        assertTrue(videoToolbar.contains("IconButton(onClick = { searchExpanded = !searchExpanded })"))
        assertTrue(videoRefineSheet.contains("shape = RoundedCornerShape(8.dp)"))
        assertTrue(soundModeBar.contains("heightIn(min = 44.dp)"))
        assertTrue(soundModeBar.contains("shape = RoundedCornerShape(0.dp)"))
        assertTrue(aiStylePicker.contains("shape = RoundedCornerShape(8.dp)"))
    }

    @Test
    fun `long disclosure dialogs keep policy copy scrollable on compact screens`() {
        val guidelines = File("src/main/java/com/freevibe/ui/components/CommunityGuidelinesDialog.kt").readText()
        val aiDisclosure = File("src/full/java/com/freevibe/ui/screens/aigenerate/AiWallpaperScreen.kt")
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
    fun `empty collections expose creation as a first class action`() {
        val source = File("src/main/java/com/freevibe/ui/screens/collections/CollectionsScreen.kt").readText()
        val emptyList = source.substringAfter("// Collection list").substringBefore("LazyColumn(")

        assertTrue(source.contains("fun createCollection(name: String)"))
        assertTrue(source.contains("private fun CreateCollectionDialog("))
        assertTrue(source.contains("Icon(Icons.Default.CreateNewFolder, stringResource(R.string.collections_create))"))
        assertTrue(emptyList.contains("primaryAction = AuraStateAction("))
        assertTrue(emptyList.contains("showCreateDialog = true"))
        assertTrue(source.contains("R.string.collections_import_summary"))
        assertTrue(source.contains("result.skippedCount"))
        assertTrue(source.contains("result.failedCount"))
        assertFalse(source.contains("Couldn't import this collection."))
    }

    @Test
    fun `wallpaper detail horizontal action chips keep labels when clipped`() {
        val source = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt").readText()
        val actionPill = source.substringAfter("private fun DetailActionPill(").substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(actionPill.contains("semantics(mergeDescendants = true)"))
        assertTrue(actionPill.contains("contentDescription = label"))
    }

    @Test
    fun `wallpaper toolbar hides the overflow menu when it has no actions`() {
        val source = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt").readText()

        assertTrue(source.contains("val hasQuickActionsMenu = showGeneratedContentEntry ||"))
        assertTrue(source.contains("if (hasQuickActionsMenu) {"))
    }

    @Test
    fun `categories are localized and require a searchable provider`() {
        val categories = File("src/main/java/com/freevibe/ui/screens/categories/CategoriesScreen.kt").readText()
        val settings = File("src/main/java/com/freevibe/ui/screens/settings/SettingsWallpaperSection.kt").readText()

        assertTrue(categories.contains("val nameRes: Int"))
        assertTrue(categories.contains("stringResource(category.nameRes)"))
        assertTrue(settings.contains("val categoriesAvailable = wallhavenProviderEnabled || pixabayProviderEnabled"))
        assertTrue(settings.contains("settings_wp_categories_unavailable_feedback"))
    }

    @Test
    fun `media discovery keeps warm caches and vertical swipe viewers`() {
        val videos = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt").readText()
        val videoModel = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersViewModel.kt").readText()
        val wallpaperDetail = File("src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt").readText()
        val playback = File("src/main/java/com/freevibe/service/AudioPlaybackManager.kt").readText()
        val soundBrowse = File("src/main/java/com/freevibe/ui/screens/sounds/SoundBrowseViewModel.kt").readText()

        assertTrue(videos.contains("private fun VideoImmersivePager("))
        assertTrue(videos.contains("VerticalPager("))
        assertTrue(videos.contains("beyondViewportPageCount = 1"))
        assertTrue(videos.contains("setCustomCacheKey(item.id)"))
        assertTrue(videoModel.contains("Warm feed ready:"))
        assertTrue(videoModel.contains("VideoPreviewCache"))
        assertTrue(wallpaperDetail.contains("VerticalPager("))
        assertTrue(wallpaperDetail.contains("context.imageLoader.enqueue("))
        assertTrue(playback.contains("setCustomCacheKey(sound.stableKey())"))
        assertTrue(soundBrowse.contains("hydrateCachedFeed("))
    }

    @Test
    fun `video previews keep one fixed size surface active`() {
        val videos = File("src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt").readText()

        assertTrue(!videos.contains("RESIZE_MODE_ZOOM"))
        assertTrue(Regex("RESIZE_MODE_FILL").findAll(videos).count() == 2)
        assertTrue(Regex("VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING").findAll(videos).count() == 2)
        assertTrue(videos.contains("shouldPreview = immersiveVideoIndex < 0 && item.id == activePreviewId"))
    }

    @Test
    fun `release ui avoids fully circular chrome backdrops`() {
        val uiFiles = listOf(
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt",
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperDetailScreen.kt",
            "src/main/java/com/freevibe/ui/screens/wallpapers/WallpaperPreviewScreen.kt",
            "src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt",
            "src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt",
        )

        uiFiles.forEach { path ->
            assertTrue("$path should use bounded corner radii instead of CircleShape", !File(path).readText().contains("CircleShape"))
        }
    }
}
