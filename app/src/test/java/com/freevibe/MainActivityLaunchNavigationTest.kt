package com.freevibe

import android.os.Bundle
import com.freevibe.data.model.ContentSource
import com.freevibe.service.TaskerActionReceiver
import com.freevibe.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLaunchNavigationTest {
    @Test
    fun `buildLaunchNavigation supports route-only launches`() {
        val navigation = buildLaunchNavigation(route = "favorites")

        assertEquals("favorites", navigation?.route)
        assertNull(navigation?.wallpaper)
    }

    @Test
    fun `buildLaunchWallpaper preserves wallpaper metadata from notification extras`() {
        val wallpaper = buildLaunchWallpaper(
            wallpaperId = "reddit_123",
            fullUrl = "https://example.com/full.jpg",
            thumbnailUrl = "https://example.com/thumb.jpg",
            sourceName = ContentSource.REDDIT.name,
            width = 1440,
            height = 3200,
        )

        assertNotNull(wallpaper)
        assertEquals(ContentSource.REDDIT, wallpaper?.source)
        assertEquals(1440, wallpaper?.width)
        assertEquals(3200, wallpaper?.height)
        assertEquals("https://example.com/thumb.jpg", wallpaper?.thumbnailUrl)
    }

    @Test
    fun `saved state gates initial stale launch replay`() {
        assertFalse(shouldHandleInitialLaunchNavigation(Bundle()))
        assertTrue(shouldHandleInitialLaunchNavigation(null))
    }

    @Test
    fun `shortcut actions map to launcher routes`() {
        assertEquals(Screen.Wallpapers.route, routeForShortcutAction(TaskerActionReceiver.ACTION_SHUFFLE_NOW))
        assertEquals(Screen.Wallpapers.route, routeForShortcutAction(TaskerActionReceiver.ACTION_ROTATE_NOW))
        assertEquals(Screen.Wallpapers.route, routeForShortcutAction(ACTION_SHORTCUT_SEARCH))
        assertEquals(Screen.Downloads.route, routeForShortcutAction(ACTION_SHORTCUT_DOWNLOADS))
        assertNull(routeForShortcutAction("com.freevibe.action.UNKNOWN"))
    }

    @Test
    fun `rotation shortcut detection only accepts rotation actions`() {
        assertTrue(isRotationShortcutAction(TaskerActionReceiver.ACTION_SHUFFLE_NOW))
        assertTrue(isRotationShortcutAction(TaskerActionReceiver.ACTION_ROTATE_NOW))
        assertFalse(isRotationShortcutAction(ACTION_SHORTCUT_SEARCH))
        assertFalse(isRotationShortcutAction(ACTION_SHORTCUT_DOWNLOADS))
        assertFalse(isRotationShortcutAction(null))
    }

    @Test
    fun `buildLaunchWallpaper drops non-https urls from notification extras`() {
        // v6.5.0 HTTPS-only policy for deep-linked wallpaper URLs — cleartext or local
        // file URIs smuggled through a notification intent must not be rehydrated.
        listOf(
            "http://example.com/full.jpg",
            "file:///sdcard/payload.jpg",
            "content://media/external/images/1",
            "javascript:alert(1)",
        ).forEach { unsafe ->
            val wallpaper = buildLaunchWallpaper(
                wallpaperId = "reddit_123",
                fullUrl = unsafe,
                thumbnailUrl = "https://example.com/thumb.jpg",
                sourceName = ContentSource.REDDIT.name,
            )
            assertNull("Expected null for unsafe URL $unsafe", wallpaper)
        }
    }
}
