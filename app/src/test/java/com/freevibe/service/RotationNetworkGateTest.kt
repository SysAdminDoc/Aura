package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Test

class RotationNetworkGateTest {

    private val wifi = RotationNetworkState(connected = true, metered = false)
    private val mobileData = RotationNetworkState(connected = true, metered = true)
    private val airplaneMode = RotationNetworkState(connected = false, metered = true)

    @Test
    fun `no connection is offline whatever the Wi-Fi only choice is`() {
        assertEquals(RotationNetworkAccess.OFFLINE, rotationNetworkAccess(airplaneMode, wifiOnly = false))
        assertEquals(RotationNetworkAccess.OFFLINE, rotationNetworkAccess(airplaneMode, wifiOnly = true))
    }

    @Test
    fun `Wi-Fi only holds a rotation back on mobile data and nowhere else`() {
        assertEquals(RotationNetworkAccess.WAITING_FOR_WIFI, rotationNetworkAccess(mobileData, wifiOnly = true))
        assertEquals(RotationNetworkAccess.ALLOWED, rotationNetworkAccess(mobileData, wifiOnly = false))
        assertEquals(RotationNetworkAccess.ALLOWED, rotationNetworkAccess(wifi, wifiOnly = true))
        assertEquals(RotationNetworkAccess.ALLOWED, rotationNetworkAccess(wifi, wifiOnly = false))
    }

    @Test
    fun `the gate reads the network again on every ask`() {
        var state = airplaneMode
        val gate = RotationNetworkGate { state }

        assertEquals(RotationNetworkAccess.OFFLINE, gate.access(wifiOnly = true))
        state = mobileData
        assertEquals(RotationNetworkAccess.WAITING_FOR_WIFI, gate.access(wifiOnly = true))
        state = wifi
        assertEquals(RotationNetworkAccess.ALLOWED, gate.access(wifiOnly = true))
    }

    @Test
    fun `a held run names what it is waiting for`() {
        assertEquals(
            "Wi-Fi only is on and this connection is metered. The next wallpaper loads on Wi-Fi.",
            rotationHoldReason(RotationNetworkAccess.WAITING_FOR_WIFI),
        )
        assertEquals(
            "No connection and no wallpaper saved ahead. The next one loads when the phone is back online.",
            rotationHoldReason(RotationNetworkAccess.OFFLINE),
        )
    }
}
