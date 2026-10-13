package com.freevibe.service

import android.content.Context
import android.hardware.display.DisplayManager
import android.service.wallpaper.WallpaperService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers

/**
 * One wallpaper service runs an engine per screen, so a phone with a monitor attached has two
 * engines of the same class at once. Each has to measure the screen it draws on. These tests put
 * two engines on displays that differ in size and density and read back what each one sized for.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveWallpaperDisplayContextTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private val engines = mutableListOf<WallpaperService.Engine>()

    @After
    fun tearDown() {
        engines.forEach { it.onDestroy() }
    }

    /** 360 x 800 dp at 3x, the size of a tall phone screen: 1080 x 2400 px. */
    private fun phoneDisplay(): Context = displayContext("w360dp-h800dp-xxhdpi")

    /** 1280 x 720 dp at 1x, a small external monitor: 1280 x 720 px. */
    private fun monitorDisplay(): Context = displayContext("w1280dp-h720dp-mdpi")

    private fun displayContext(qualifiers: String): Context {
        val displayId = ShadowDisplayManager.addDisplay(qualifiers)
        val display = app.getSystemService(DisplayManager::class.java).getDisplay(displayId)
        return app.createDisplayContext(display)
    }

    private fun engineOn(serviceClass: Class<out WallpaperService>, display: Context): WallpaperService.Engine {
        val service = Robolectric.buildService(serviceClass).create().get()
        return service.onCreateEngine().also { engine ->
            // The platform sets this when it attaches the engine to a display.
            ReflectionHelpers.setField(WallpaperService.Engine::class.java, engine, "mDisplayContext", display)
            engines += engine
        }
    }

    private fun decodeTarget(engine: WallpaperService.Engine): Pair<Int, Int> =
        ReflectionHelpers.callInstanceMethod(engine, "resolveDecodeTarget")

    private fun screenSize(engine: WallpaperService.Engine): Pair<Int, Int> {
        ReflectionHelpers.callInstanceMethod<Any?>(engine, "resolveScreenSize")
        return ReflectionHelpers.getField<Int>(engine, "screenWidth") to
            ReflectionHelpers.getField<Int>(engine, "screenHeight")
    }

    @Test
    fun `two parallax engines each decode for their own display`() {
        val onPhone = engineOn(ParallaxWallpaperService::class.java, phoneDisplay())
        val onMonitor = engineOn(ParallaxWallpaperService::class.java, monitorDisplay())

        // The parallax layer overscans by its travel on each side: 30 px twice.
        assertEquals(1080 + 60 to 2400 + 60, decodeTarget(onPhone))
        assertEquals(1280 + 60 to 720 + 60, decodeTarget(onMonitor))
    }

    @Test
    fun `two weather engines each decode for their own display`() {
        val onPhone = engineOn(WeatherWallpaperService::class.java, phoneDisplay())
        val onMonitor = engineOn(WeatherWallpaperService::class.java, monitorDisplay())

        assertEquals(1080 to 2400, decodeTarget(onPhone))
        assertEquals(1280 to 720, decodeTarget(onMonitor))
    }

    @Test
    fun `two video engines each measure their own display`() {
        val onPhone = engineOn(VideoWallpaperService::class.java, phoneDisplay())
        val onMonitor = engineOn(VideoWallpaperService::class.java, monitorDisplay())

        assertEquals(1080 to 2400, screenSize(onPhone))
        assertEquals(1280 to 720, screenSize(onMonitor))
    }

    @Test
    fun `an engine with no display of its own falls back to the service's screen`() {
        val service = Robolectric.buildService(ParallaxWallpaperService::class.java).create().get()
        val engine = service.onCreateEngine().also { engines += it }
        val metrics = service.resources.displayMetrics

        assertEquals(metrics.widthPixels + 60 to metrics.heightPixels + 60, decodeTarget(engine))
    }
}
