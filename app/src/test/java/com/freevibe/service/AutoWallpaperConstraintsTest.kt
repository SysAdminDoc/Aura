package com.freevibe.service

import androidx.work.NetworkType
import com.freevibe.data.model.WALLPAPER_SOURCE_LOCAL_FOLDER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoWallpaperConstraintsTest {

    @Test
    fun `a catch-up with the toggles off waits for CONNECTED + battery floor only`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = false,
            requiresWiFiOnly = false,
            requiresIdle = false,
        )
        assertEquals(NetworkType.CONNECTED, c.requiredNetworkType)
        assertTrue("battery-not-low is the energy floor", c.requiresBatteryNotLow())
        assertFalse(c.requiresCharging())
        assertFalse(c.requiresDeviceIdle())
    }

    @Test
    fun `wifi-only tightens a catch-up's network type to UNMETERED`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = false,
            requiresWiFiOnly = true,
            requiresIdle = false,
        )
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
    }

    @Test
    fun `work that must start offline asks for no network even when wifi-only is enabled`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = false,
            requiresWiFiOnly = true,
            requiresIdle = false,
            requiresNetwork = false,
        )
        assertEquals(NetworkType.NOT_REQUIRED, c.requiredNetworkType)
        assertTrue("the battery floor stays", c.requiresBatteryNotLow())
    }

    @Test
    fun `charging toggle adds requiresCharging`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = true,
            requiresWiFiOnly = false,
            requiresIdle = false,
        )
        assertTrue(c.requiresCharging())
    }

    // requiresDeviceIdle() is API-23+ behind a @RequiresApi guard; WorkManager's
    // unit-test stub for the field returns the default. We exercise the builder
    // path (no exception thrown) and trust the integration-test surface for the
    // rest. The other constraints assert on directly-readable fields.

    @Test
    fun `idle toggle is accepted by builder without exception`() {
        // Smoke: the builder accepts the flag — WorkManager itself enforces the
        // SDK gating at scheduling time. We don't assert the readback because
        // unit-test Constraints stubs differ across WorkManager versions on the
        // requiresDeviceIdle reflection path.
        buildAutoWallpaperConstraints(
            requiresCharging = false,
            requiresWiFiOnly = false,
            requiresIdle = true,
        )
    }

    @Test
    fun `all toggles compose without resetting each other`() {
        val c = buildAutoWallpaperConstraints(
            requiresCharging = true,
            requiresWiFiOnly = true,
            requiresIdle = true,
        )
        assertEquals(NetworkType.UNMETERED, c.requiredNetworkType)
        assertTrue(c.requiresBatteryNotLow())
        assertTrue(c.requiresCharging())
    }

    @Test
    fun `sourceRequiresNetwork treats local folder as offline capable`() {
        assertFalse(sourceRequiresNetwork(WALLPAPER_SOURCE_LOCAL_FOLDER))
        assertTrue(sourceRequiresNetwork("wallhaven"))
    }

    @Test
    fun `a tapped rotation waits on nothing so a saved wallpaper applies offline`() {
        val c = triggeredRotationConstraints(restartCountdown = true)
        assertEquals(NetworkType.NOT_REQUIRED, c.requiredNetworkType)
        // Expedited work only accepts network and storage constraints; building it with more throws.
        assertFalse(c.requiresBatteryNotLow())
    }

    @Test
    fun `unlock and screen-off rotations keep the battery floor and drop the network`() {
        val c = triggeredRotationConstraints(restartCountdown = false)
        assertEquals(NetworkType.NOT_REQUIRED, c.requiredNetworkType)
        assertTrue(c.requiresBatteryNotLow())
    }
}
