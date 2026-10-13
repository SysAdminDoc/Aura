package com.freevibe.ui

import android.app.Application
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.freevibe.service.VideoWallpaperService
import com.freevibe.ui.screens.videowallpapers.videoWallpaperBlockedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LiveWallpaperLauncherTest {

    private class FakePlatform(
        override val hasLiveWallpaperFeature: Boolean = true,
        override val hasWallpaperService: Boolean = true,
        private val resolvable: Set<LiveWallpaperLaunchMode> = LiveWallpaperLaunchMode.entries.toSet(),
        private val failures: Map<LiveWallpaperLaunchMode, Exception> = emptyMap(),
    ) : LiveWallpaperPlatform {
        val started = mutableListOf<LiveWallpaperLaunchMode>()

        override fun resolves(mode: LiveWallpaperLaunchMode): Boolean = mode in resolvable

        override fun start(mode: LiveWallpaperLaunchMode) {
            started += mode
            failures[mode]?.let { throw it }
        }
    }

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val videoService = ComponentName(app, VideoWallpaperService::class.java)
    private val systemPicker = ComponentName("com.android.wallpaper.livepicker", "com.android.wallpaper.livepicker.LiveWallpaperChange")

    private fun blocked(reason: LiveWallpaperBlock) = LiveWallpaperLaunchResult.Blocked(reason)

    private fun opened(mode: LiveWallpaperLaunchMode) = LiveWallpaperLaunchResult.Opened(mode)

    @Test
    fun `a device without the live wallpaper feature is unsupported and nothing is started`() {
        val platform = FakePlatform(hasLiveWallpaperFeature = false)

        assertEquals(LiveWallpaperBlock.UNSUPPORTED, liveWallpaperBlock(platform))
        assertEquals(blocked(LiveWallpaperBlock.UNSUPPORTED), launchLiveWallpaperPicker(platform, "test"))
        assertTrue(platform.started.isEmpty())
    }

    @Test
    fun `a missing wallpaper service is unavailable and nothing is started`() {
        val platform = FakePlatform(hasWallpaperService = false)

        assertEquals(LiveWallpaperBlock.UNAVAILABLE, liveWallpaperBlock(platform))
        assertEquals(blocked(LiveWallpaperBlock.UNAVAILABLE), launchLiveWallpaperPicker(platform, "test"))
        assertTrue(platform.started.isEmpty())
    }

    @Test
    fun `an action nothing answers is never started`() {
        val platform = FakePlatform(resolvable = emptySet())

        assertEquals(LiveWallpaperBlock.UNAVAILABLE, liveWallpaperBlock(platform))
        assertEquals(blocked(LiveWallpaperBlock.UNAVAILABLE), launchLiveWallpaperPicker(platform, "test"))
        assertTrue(platform.started.isEmpty())
    }

    @Test
    fun `the direct picker wins when both resolve`() {
        val platform = FakePlatform()

        assertNull(liveWallpaperBlock(platform))
        assertEquals(opened(LiveWallpaperLaunchMode.DIRECT), launchLiveWallpaperPicker(platform, "test"))
        assertEquals(listOf(LiveWallpaperLaunchMode.DIRECT), platform.started)
    }

    @Test
    fun `the chooser opens when only it resolves`() {
        val platform = FakePlatform(resolvable = setOf(LiveWallpaperLaunchMode.CHOOSER))

        assertEquals(opened(LiveWallpaperLaunchMode.CHOOSER), launchLiveWallpaperPicker(platform, "test"))
        assertEquals(listOf(LiveWallpaperLaunchMode.CHOOSER), platform.started)
    }

    @Test
    fun `a refused direct picker still falls back to the chooser`() {
        val platform = FakePlatform(failures = mapOf(LiveWallpaperLaunchMode.DIRECT to SecurityException("no")))

        assertEquals(opened(LiveWallpaperLaunchMode.CHOOSER), launchLiveWallpaperPicker(platform, "test"))
        assertEquals(LiveWallpaperLaunchMode.entries, platform.started)
    }

    @Test
    fun `a security refusal on every picker is reported as denied`() {
        val platform = FakePlatform(
            failures = LiveWallpaperLaunchMode.entries.associateWith { SecurityException("no") },
        )

        assertEquals(blocked(LiveWallpaperBlock.DENIED), launchLiveWallpaperPicker(platform, "test"))
    }

    @Test
    fun `an activity that vanishes between the check and the launch is unavailable, not a success`() {
        val platform = FakePlatform(
            failures = LiveWallpaperLaunchMode.entries.associateWith { ActivityNotFoundException() },
        )

        assertEquals(blocked(LiveWallpaperBlock.UNAVAILABLE), launchLiveWallpaperPicker(platform, "test"))
    }

    @Test
    fun `a picture offers a still wallpaper and a video never does`() {
        LiveWallpaperBlock.entries.forEach { block ->
            assertTrue(liveWallpaperFallback(block, LiveWallpaperSource.IMAGE).offersStaticApply)
            assertFalse(liveWallpaperFallback(block, LiveWallpaperSource.VIDEO).offersStaticApply)
        }
    }

    @Test
    fun `each reason reads differently and a video adds why it can't be set another way`() {
        assertEquals(
            "This device doesn't support live wallpapers.",
            liveWallpaperBlockedMessage(app, LiveWallpaperBlock.UNSUPPORTED, LiveWallpaperSource.IMAGE),
        )
        assertEquals(
            "This device has no live wallpaper picker Aura can open.",
            liveWallpaperBlockedMessage(app, LiveWallpaperBlock.UNAVAILABLE, LiveWallpaperSource.IMAGE),
        )
        assertEquals(
            "This device doesn't support live wallpapers. A video can only be set as a live wallpaper.",
            liveWallpaperBlockedMessage(app, LiveWallpaperBlock.UNSUPPORTED, LiveWallpaperSource.VIDEO),
        )
        // A refusal leaves the manual route open, so the copy points there instead of at a dead end.
        assertEquals(
            "This device stopped Aura from opening the live wallpaper picker. " +
                "Open Settings > Wallpaper > Live Wallpapers to finish.",
            liveWallpaperBlockedMessage(app, LiveWallpaperBlock.DENIED, LiveWallpaperSource.VIDEO),
        )
    }

    @Test
    fun `a saved video only promises manual setup when a picker is there to finish it`() {
        assertEquals(
            "This device doesn't support live wallpapers. The video was saved to Movies/Aura.",
            videoWallpaperBlockedMessage(app, LiveWallpaperBlock.UNSUPPORTED, saved = true, isCropped = false),
        )
        assertEquals(
            "This device has no live wallpaper picker Aura can open. A video can only be set as a live wallpaper.",
            videoWallpaperBlockedMessage(app, LiveWallpaperBlock.UNAVAILABLE, saved = false, isCropped = true),
        )
        assertEquals(
            "Video saved to Movies/Aura for manual setup.",
            videoWallpaperBlockedMessage(app, LiveWallpaperBlock.DENIED, saved = true, isCropped = false),
        )
    }

    @Test
    fun `the device check reads the system feature before anything else`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER, false)

        assertEquals(LiveWallpaperBlock.UNSUPPORTED, liveWallpaperBlock(app, videoService))
        assertEquals(blocked(LiveWallpaperBlock.UNSUPPORTED), launchLiveWallpaperPicker(app, videoService, "test"))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `the device check treats a service the manifest doesn't declare as missing`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER, true)
        registerSystemPicker()

        val unknown = ComponentName(app, "com.freevibe.service.NoSuchWallpaperService")
        assertEquals(LiveWallpaperBlock.UNAVAILABLE, liveWallpaperBlock(app, unknown))
    }

    @Test
    fun `the device check finds no picker when no activity answers either action`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER, true)

        assertEquals(blocked(LiveWallpaperBlock.UNAVAILABLE), launchLiveWallpaperPicker(app, videoService, "test"))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test
    fun `a supported device opens the direct picker on Aura's own service`() {
        shadowOf(app.packageManager).setSystemFeature(PackageManager.FEATURE_LIVE_WALLPAPER, true)
        registerSystemPicker()

        assertNull(liveWallpaperBlock(app, videoService))
        assertEquals(opened(LiveWallpaperLaunchMode.DIRECT), launchLiveWallpaperPicker(app, videoService, "test"))

        val started = shadowOf(app).nextStartedActivity
        assertEquals(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER, started.action)
        @Suppress("DEPRECATION")
        val target = started.getParcelableExtra<ComponentName>(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT)
        assertEquals(videoService, target)
    }

    private fun registerSystemPicker() {
        val packageManager = shadowOf(app.packageManager)
        packageManager.addActivityIfNotPresent(systemPicker)
        packageManager.addIntentFilterForActivity(
            systemPicker,
            IntentFilter(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply { addCategory(Intent.CATEGORY_DEFAULT) },
        )
    }
}
