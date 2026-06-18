package com.freevibe.ui.screens.fixtures

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.freevibe.ui.theme.FreeVibeTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class AuraRouteStateScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wallpapersGridAmoled() {
        captureFixture(AuraRouteFixture.WallpapersGridSuccess, darkTheme = true)
    }

    @Test
    fun wallpapersOfflineLight() {
        captureFixture(AuraRouteFixture.WallpapersOfflineEmpty, darkTheme = false)
    }

    @Test
    fun soundDetailAmoled() {
        captureFixture(AuraRouteFixture.SoundDetailReady, darkTheme = true)
    }

    @Test
    fun settingsProviderDisabledLightLargeFont() {
        captureFixture(
            fixture = AuraRouteFixture.SettingsProviderDisabled,
            darkTheme = false,
            fontScale = 2.0f,
        )
    }

    @Test
    fun videoWallpapersErrorAmoledRtl() {
        captureFixture(
            fixture = AuraRouteFixture.VideoWallpapersError,
            darkTheme = true,
            layoutDirection = LayoutDirection.Rtl,
        )
    }

    @Test
    fun wallpaperEditorLoadingLight() {
        captureFixture(AuraRouteFixture.WallpaperEditorLoading, darkTheme = false)
    }

    private fun captureFixture(
        fixture: AuraRouteFixture,
        darkTheme: Boolean,
        fontScale: Float = 1.0f,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val variant = buildString {
            append(fixture.screenshotName)
            append(if (darkTheme) "_amoled" else "_light")
            if (fontScale > 1.0f) append("_font${fontScale.toString().replace(".", "_")}")
            if (layoutDirection == LayoutDirection.Rtl) append("_rtl")
        }

        composeRule.setContent {
            FixtureRoot(
                darkTheme = darkTheme,
                fontScale = fontScale,
                layoutDirection = layoutDirection,
            ) {
                AuraRouteStateFixture(fixture)
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$variant.png")
    }
}

@Composable
private fun FixtureRoot(
    darkTheme: Boolean,
    fontScale: Float,
    layoutDirection: LayoutDirection,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, fontScale),
        LocalLayoutDirection provides layoutDirection,
    ) {
        FreeVibeTheme(darkTheme = darkTheme, dynamicColor = false) {
            Surface(
                modifier = Modifier.size(width = 411.dp, height = 891.dp),
                color = MaterialTheme.colorScheme.background,
                content = content,
            )
        }
    }
}
