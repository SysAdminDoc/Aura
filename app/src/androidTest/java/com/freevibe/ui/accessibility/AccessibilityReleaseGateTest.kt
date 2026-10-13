package com.freevibe.ui.accessibility

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.freevibe.ui.qa.ProductionRouteScenario
import com.freevibe.ui.qa.ProductionRouteState
import com.freevibe.ui.theme.FreeVibeTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Release accessibility gate for production route-state renderers.
 *
 * These checks render the same production subcomposables used by the live
 * Wallpapers, Sounds, Settings, Videos, and editor/detail routes. Scenario data
 * is deterministic, but the UI under test is compiled into the release source set.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityReleaseGateTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Surfaces whose content loads, fails or changes while they're open. Each one has to carry a
     * polite live region, or TalkBack has nothing to read when that happens.
     */
    private val asyncScenarios = setOf(
        ProductionRouteScenario.WallpapersGridSuccess,
        ProductionRouteScenario.WallpapersOfflineEmpty,
        ProductionRouteScenario.SoundDetailReady,
        ProductionRouteScenario.VideoWallpapersError,
        ProductionRouteScenario.WallpaperEditorLoading,
        ProductionRouteScenario.DownloadsMediaCopies,
        ProductionRouteScenario.DownloadsMissingMedia,
    )

    @Before
    fun enableComposeAccessibilityChecks() {
        composeRule.enableAccessibilityChecks()
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun wallpapersGridProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.WallpapersGridSuccess)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun wallpapersOfflineProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.WallpapersOfflineEmpty, darkTheme = false)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun soundsDetailProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.SoundDetailReady)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun settingsDiagnosticsProductionStatePassesAccessibilityChecks() {
        renderScenario(
            ProductionRouteScenario.SettingsProviderDisabled,
            darkTheme = false,
            fontScale = 2.0f,
        )
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun videoWallpapersProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.VideoWallpapersError)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun wallpaperEditorProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.WallpaperEditorLoading, darkTheme = false)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun downloadsMediaCopiesProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.DownloadsMediaCopies)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun downloadsMissingMediaProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.DownloadsMissingMedia)
    }

    @SdkSuppress(minSdkVersion = 34)
    @Test
    fun soundShufflePoolsProductionStatePassesAccessibilityChecks() {
        renderScenario(ProductionRouteScenario.SoundShufflePools)
    }

    private fun renderScenario(
        scenario: ProductionRouteScenario,
        darkTheme: Boolean = true,
        fontScale: Float = 1.0f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FreeVibeTheme(darkTheme = darkTheme, dynamicColor = false) {
                    ProductionRouteState(scenario = scenario)
                }
            }
        }

        composeRule.waitForIdle()
        // State cards wait a moment before they become live regions, and the test clock only moves
        // when it's told to.
        composeRule.mainClock.advanceTimeBy(LIVE_REGION_SETTLE_MS)
        composeRule.onNodeWithText(composeRule.activity.getString(scenario.assertionResource)).assertExists()
        if (scenario in asyncScenarios) {
            composeRule
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
                .onFirst()
                .assertExists()
        }
        composeRule.onRoot().tryPerformAccessibilityChecks()
    }

    private companion object {
        const val LIVE_REGION_SETTLE_MS = 1_000L
    }
}
