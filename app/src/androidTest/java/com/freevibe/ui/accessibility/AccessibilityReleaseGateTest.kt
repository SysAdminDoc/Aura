package com.freevibe.ui.accessibility

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.freevibe.R
import com.freevibe.ui.components.AuraStateAction
import com.freevibe.ui.components.AuraStateCard
import com.freevibe.ui.screens.settings.SettingsItem
import com.freevibe.ui.screens.settings.SettingsMetric
import com.freevibe.ui.screens.settings.SettingsSection
import com.freevibe.ui.screens.settings.SettingsToggle
import com.freevibe.ui.screens.settings.SettingsValueSlider
import com.freevibe.ui.theme.FreeVibeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Aura UI accessibility gate test. Renders actual Aura components
 * (SettingsSection, SettingsToggle, SettingsItem, AuraStateCard, etc.)
 * under Compose accessibility checks to catch missing content descriptions,
 * touch-target violations, and contrast issues.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityReleaseGateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun enableComposeAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun settingsComponentsExposeAccessibleNamesAndStates() {
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    SettingsSection(
                        title = "Wallpapers",
                        description = "Tune discovery quality and density.",
                    ) {
                        SettingsToggle(
                            icon = Icons.Default.Schedule,
                            title = "Auto-rotate wallpapers",
                            subtitle = "Change wallpaper every 6 hours from Discover",
                            checked = true,
                            onCheckedChange = {},
                        )
                        SettingsItem(
                            icon = Icons.Default.FolderOpen,
                            title = "Local rotation folder",
                            subtitle = "Choose a local image folder for offline rotation",
                            onClick = {},
                        )
                        SettingsValueSlider(
                            icon = Icons.Default.BatteryAlert,
                            title = "Rotation dimming",
                            subtitle = "Darkens rotated wallpapers for legibility",
                            valueLabel = "25%",
                            value = 25f,
                            valueRange = 0f..100f,
                            steps = 9,
                            onValueChange = {},
                        )
                    }

                    SettingsSection(
                        title = "Sounds",
                        description = "Control previews and search quality.",
                    ) {
                        SettingsToggle(
                            icon = Icons.Default.Schedule,
                            title = "Auto-preview sounds",
                            subtitle = "Play a preview when browsing",
                            checked = false,
                            onCheckedChange = {},
                        )
                    }

                    SettingsMetric(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Automation",
                        value = "6 hours",
                        icon = Icons.Default.Schedule,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        composeRule.onNodeWithText("Wallpapers").assertExists()
        composeRule.onNodeWithText("Auto-rotate wallpapers").assertExists()
        composeRule.onNodeWithText("Local rotation folder").assertExists()
        composeRule.onNodeWithText("Rotation dimming").assertExists()
        composeRule.onNodeWithText("Sounds").assertExists()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun emptyStatesExposeAccessibleActionsAndLabels() {
        composeRule.setContent {
            FreeVibeTheme(darkTheme = true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    AuraStateCard(
                        icon = Icons.Default.Favorite,
                        title = "No favorite wallpapers yet",
                        description = "Save wallpapers from detail, or restore a backup.",
                        primaryAction = AuraStateAction(
                            label = "Import backup",
                            icon = Icons.Default.FolderOpen,
                            onClick = {},
                        ),
                    )
                }
            }
        }

        composeRule.onNodeWithText("No favorite wallpapers yet").assertExists()
        composeRule.onNodeWithText("Import backup").assertExists()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
