package com.freevibe.service

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherPrivacyContractTest {

    @Test
    fun `weather coordinates are rounded before local retention`() {
        assertEquals(39.74f, roundWeatherCoordinate(39.7392), 0.0001f)
        assertEquals(-104.99f, roundWeatherCoordinate(-104.9903), 0.0001f)
    }

    @Test
    fun `manifest grants weather coarse location only`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.ACCESS_COARSE_LOCATION"))
        assertFalse(manifest.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse(manifest.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
    }
}
