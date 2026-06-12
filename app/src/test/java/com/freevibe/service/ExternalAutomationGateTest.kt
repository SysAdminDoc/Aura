package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAutomationGateTest {

    @Test
    fun `external automation is disabled by default`() {
        val decision = ExternalAutomationGate.decide(
            action = TaskerActionReceiver.ACTION_ROTATE_NOW,
            enabled = false,
            lastAcceptedAtMs = 0L,
            nowMs = 10_000L,
        )

        assertFalse(decision.accepted)
        assertEquals("disabled", decision.reason)
    }

    @Test
    fun `supported actions are accepted when enabled and outside rate limit`() {
        val rotateDecision = ExternalAutomationGate.decide(
            action = TaskerActionReceiver.ACTION_ROTATE_NOW,
            enabled = true,
            lastAcceptedAtMs = 10_000L,
            nowMs = 40_000L,
        )
        val shuffleDecision = ExternalAutomationGate.decide(
            action = TaskerActionReceiver.ACTION_SHUFFLE_NOW,
            enabled = true,
            lastAcceptedAtMs = 40_000L,
            nowMs = 70_000L,
        )

        assertTrue(rotateDecision.accepted)
        assertEquals("accepted", rotateDecision.reason)
        assertTrue(shuffleDecision.accepted)
    }

    @Test
    fun `burst broadcasts are rate limited`() {
        val decision = ExternalAutomationGate.decide(
            action = TaskerActionReceiver.ACTION_ROTATE_NOW,
            enabled = true,
            lastAcceptedAtMs = 10_000L,
            nowMs = 39_999L,
        )

        assertFalse(decision.accepted)
        assertEquals("rate_limited", decision.reason)
        assertEquals(40_000L, decision.nextAllowedAtMs)
    }

    @Test
    fun `unsupported action is rejected before enablement or rate limit checks`() {
        val decision = ExternalAutomationGate.decide(
            action = "com.example.UNREVIEWED",
            enabled = true,
            lastAcceptedAtMs = 0L,
            nowMs = 10_000L,
        )

        assertFalse(decision.accepted)
        assertEquals("unsupported_action", decision.reason)
    }

    @Test
    fun `caller package extra is stored only when it is diagnostic safe`() {
        assertEquals(
            "net.dinglisch.android.taskerm",
            ExternalAutomationGate.sanitizeCallerPackage(" net.dinglisch.android.taskerm "),
        )
        assertEquals("com.arlosoft.macrodroid", ExternalAutomationGate.sanitizeCallerPackage("com.arlosoft.macrodroid"))
        assertEquals("", ExternalAutomationGate.sanitizeCallerPackage("bad package\nname"))
        assertEquals("", ExternalAutomationGate.sanitizeCallerPackage("a".repeat(97)))
    }
}
