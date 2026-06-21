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
                    "onClick(label = action.label",
                    "fun CountBadge",
                    "contentDescription = countDescription",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/downloads/DownloadsScreen.kt",
                tokens = listOf(
                    "fun downloadHistorySummary",
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "stateDescription = healthLabel",
                    "onClick(label = openLabel",
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
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/sounds/SoundsScreen.kt",
                tokens = listOf(
                    "customActions = cardActions",
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "onClick(label = playButtonDescription",
                    "stateDescription = voteStateDescription",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/videowallpapers/VideoWallpapersScreen.kt",
                tokens = listOf(
                    "stateDescription = videoStateDescription",
                    "onClick(label = previewVideoLabel",
                    "onClick(label = applyVideoLabel",
                    "stateDescription = selectedDescription",
                ),
            ),
            SemanticsRequirement(
                path = "src/main/java/com/freevibe/ui/screens/editor/SoundEditorScreen.kt",
                tokens = listOf(
                    "progressBarRangeInfo = ProgressBarRangeInfo",
                    "stateDescription = trimPlaybackState",
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
