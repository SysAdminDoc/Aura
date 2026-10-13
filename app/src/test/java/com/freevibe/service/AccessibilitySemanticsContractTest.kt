package com.freevibe.service

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySemanticsContractTest {

    @Test
    fun `custom component semantics matrix stays covered`() {
        val matrix = listOf(
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/components/SharedComponents.kt",
                tokens = listOf(
                    "fun DownloadProgressBar",
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "fun AuraSnackbarHost",
                    "rememberSystemAnimationsEnabled",
                    "fun HighlightPill",
                    "clearAndSetSemantics",
                    "fun SourceBadge",
                    "contentDescription = label",
                    "fun AuraStateCard",
                    "AuraStateMessage {",
                    "onClick(label = action.label",
                    "fun CountBadge",
                    "contentDescription = countDescription",
                    "announceOnceSettled(download.isComplete || download.error != null)",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/components/LiveAnnouncements.kt",
                tokens = listOf(
                    "fun Modifier.politeAnnouncement",
                    "liveRegion = LiveRegionMode.Polite",
                    "fun AuraStateMessage",
                    "fun AuraFeedStatus",
                    "fun Modifier.feedTraversalRoot",
                    "fun Modifier.feedSection",
                    "isTraversalGroup = true",
                    "traversalIndex = traversalOrder",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/downloads/DownloadsScreen.kt",
                tokens = listOf(
                    "fun downloadHistorySummary",
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "stateDescription = healthLabel",
                    "onClick(label = openLabel",
                    "announceOnceSettled(dl.isComplete || dl.error != null)",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/favorites/FavoritesScreen.kt",
                tokens = listOf(
                    "fun favoriteWallpaperSummary",
                    "fun favoritesBatchProgressSummary",
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "customActions = listOf",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/settings/SettingsComponents.kt",
                tokens = listOf(
                    "internal fun SettingsSection",
                    "heading()",
                    "internal fun SettingsItem",
                    "onClick(label = title",
                    "internal fun SettingsToggle",
                    "stateDescription = toggleStateDescription",
                    "internal fun SettingsMetric",
                    "contentDescription = metricDescription",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/wallpapers/WallpapersScreen.kt",
                tokens = listOf(
                    "customActions = cardActions",
                    "stateDescription = if (selectedColor == hex)",
                    "clickable(onClickLabel = colorLabel)",
                    "feedTraversalRoot()",
                    "feedSection(AuraFeedSection.Header)",
                    "feedSection(AuraFeedSection.Status)",
                    "feedSection(AuraFeedSection.Content)",
                    "AuraFeedStatus(",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt",
                tokens = listOf(
                    "customActions = cardActions",
                    "contentDescription = openSoundDetailsLabel",
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "onClick(label = playButtonDescription",
                    "stateDescription = voteStateDescription",
                    "feedTraversalRoot()",
                    "feedSection(AuraFeedSection.Header)",
                    "feedSection(AuraFeedSection.Status)",
                    "feedSection(AuraFeedSection.Content)",
                    "AuraFeedStatus(",
                    "PreviewPlaybackStatus(",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt",
                tokens = listOf(
                    "stateDescription = videoStateDescription",
                    "clickable(onClickLabel = openVideoPreviewLabel",
                    "contentDescription = openVideoPreviewLabel",
                    "contentDescription = immersivePreviewDescription",
                    "IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS",
                    "onClick(label = applyVideoLabel",
                    "feedTraversalRoot()",
                    "feedSection(AuraFeedSection.Header)",
                    "feedSection(AuraFeedSection.Status)",
                    "feedSection(AuraFeedSection.Content)",
                    "AuraFeedStatus(",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/components/FitCanvasControls.kt",
                tokens = listOf(
                    "FilterChip(",
                    "selected = selected",
                    "label = { Text(stringResource(R.string.fit_canvas_custom)) }",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/editor/SoundEditorScreen.kt",
                tokens = listOf(
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "stateDescription = waveformState",
                    "awaitEachGesture",
                    "calculateZoom()",
                    "fun MillisecondField",
                    "AudioFadeCurve.entries",
                    "AudioExportFormat.entries",
                    "R.string.editor_sound_export_to_music",
                    "onClick(label = if (state.isPlaying) pausePreviewLabel else playPreviewLabel",
                    "onClick(label = applyLabel",
                ),
            ),
        )

        matrix.forEach { requirement ->
            val source = File(requirement.path).readText()
            requirement.tokens.forEach { token ->
                assertTrue("${requirement.path} should contain $token", source.contains(token))
            }
        }
    }

    private data class SemanticsRequirement(
        val path: String,
        val tokens: List<String>,
    )
}
