package com.freevibe.ui.accessibility

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun coreInteractivePatternsExposeAccessibleNamesAndStates() {
        composeRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.padding(24.dp)) {
                    Button(onClick = {}) {
                        Text("Play preview")
                    }
                    Button(onClick = {}) {
                        Text("Apply wallpaper")
                    }
                    val enabled = remember { mutableStateOf(true) }
                    Switch(
                        checked = enabled.value,
                        onCheckedChange = { enabled.value = it },
                        modifier = Modifier.semantics {
                            contentDescription = "Weather effects"
                        },
                    )
                    val trim = remember { mutableStateOf(0.35f) }
                    Slider(
                        value = trim.value,
                        onValueChange = { trim.value = it },
                        modifier = Modifier.semantics {
                            contentDescription = "Trim start"
                        },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Play preview").assertExists()
        composeRule.onNodeWithText("Apply wallpaper").assertExists()
        composeRule.onNodeWithContentDescription("Weather effects").assertExists()
        composeRule.onNodeWithContentDescription("Trim start").assertExists()
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }
}
