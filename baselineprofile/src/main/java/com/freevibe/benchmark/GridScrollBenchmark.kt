package com.freevibe.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class GridScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun wallpapersScrollWithBaselineProfile() = measureTabScroll("Wallpapers", swipes = 3)

    @Test
    fun videosScrollWithBaselineProfile() = measureTabScroll("Videos", swipes = 3)

    @Test
    fun soundsScrollWithBaselineProfile() = measureTabScroll("Sounds", swipes = 3)

    @Test
    fun favoritesScrollWithBaselineProfile() = measureTabScroll("Favorites", swipes = 1)

    private fun measureTabScroll(tabLabel: String, swipes: Int) {
        benchmarkRule.measureRepeated(
            packageName = AURA_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = 5,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.Require,
            ),
            setupBlock = {
                prepareAuraHome()
            },
            measureBlock = {
                startAuraHome()
                device.navigateAndScroll(tabLabel, swipes)
            },
        )
    }
}
