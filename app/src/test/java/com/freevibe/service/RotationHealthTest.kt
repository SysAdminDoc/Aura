package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five situations a stopped rotation can be in.
 *
 * They look identical from the home screen — the wallpaper simply is not
 * changing — and each needs a different response, so the classifier getting one
 * wrong sends the user to fix something that was never broken.
 */
class RotationHealthClassifierTest {

    private fun classify(
        enabled: Boolean = true,
        workState: String = "ENQUEUED=1",
        hasNextFire: Boolean = true,
        stopReason: String? = null,
        lastResult: String? = "success",
        exempt: Boolean? = true,
    ) = classifyRotationHealth(
        rotationEnabled = enabled,
        workState = workState,
        hasNextFire = hasNextFire,
        stopReason = stopReason,
        lastResult = lastResult,
        ignoringBatteryOptimizations = exempt,
    )

    @Test
    fun `rotation turned off outranks every other reading`() {
        // Even a lost schedule and a failed run are not findings when the user
        // switched rotation off; reporting them would be alarming and wrong.
        assertEquals(
            RotationHealthVerdict.DISABLED,
            classify(
                enabled = false,
                workState = "No WorkInfo records",
                hasNextFire = false,
                lastResult = "failure",
            ),
        )
    }

    @Test
    fun `an enqueued worker with a next fire and a clean last run is healthy`() {
        assertEquals(RotationHealthVerdict.HEALTHY, classify())
    }

    @Test
    fun `a running worker counts as scheduled`() {
        assertEquals(RotationHealthVerdict.HEALTHY, classify(workState = "RUNNING=1"))
    }

    @Test
    fun `enabled with no WorkInfo at all means the schedule was lost`() {
        assertEquals(
            RotationHealthVerdict.NOT_SCHEDULED,
            classify(workState = "No WorkInfo records", hasNextFire = false),
        )
    }

    @Test
    fun `a cancelled worker is a lost schedule, not a healthy one`() {
        assertEquals(
            RotationHealthVerdict.NOT_SCHEDULED,
            classify(workState = "CANCELLED=1", hasNextFire = false),
        )
    }

    @Test
    fun `a failed last run outranks a throttling stop reason`() {
        // Both are true at once often enough that the order matters: a failure is
        // the more specific thing to tell the user about.
        assertEquals(
            RotationHealthVerdict.FAILING,
            classify(lastResult = "failure", stopReason = "QUOTA=1"),
        )
    }

    @Test
    fun `an OS-chosen stop reason is throttling`() {
        assertEquals(
            RotationHealthVerdict.THROTTLED,
            classify(stopReason = "APP_STANDBY=2"),
        )
    }

    @Test
    fun `a stop the app or user asked for is not throttling`() {
        assertEquals(
            RotationHealthVerdict.HEALTHY,
            classify(stopReason = "CANCELLED_BY_APP=1"),
        )
        assertEquals(RotationHealthVerdict.HEALTHY, classify(stopReason = "USER=1"))
    }

    @Test
    fun `no next fire on a restricted app is throttling`() {
        assertEquals(
            RotationHealthVerdict.THROTTLED,
            classify(hasNextFire = false, exempt = false),
        )
    }

    @Test
    fun `no next fire on an exempt app is just between runs`() {
        // An exempt app with no next fire has usually just finished one. Calling
        // that throttled would send the user to a battery screen already correct.
        assertEquals(
            RotationHealthVerdict.HEALTHY,
            classify(hasNextFire = false, exempt = true),
        )
    }

    @Test
    fun `no next fire and an unreadable exemption is not called throttled`() {
        // The device refused to say. Guessing "throttled" here would be a false
        // accusation on every OEM whose PowerManager throws.
        assertEquals(
            RotationHealthVerdict.HEALTHY,
            classify(hasNextFire = false, exempt = null),
        )
    }

    @Test
    fun `a retry is treated as throttling rather than failure`() {
        assertEquals(RotationHealthVerdict.THROTTLED, classify(lastResult = "retry"))
    }

    @Test
    fun `a run that waited for a connection is not a problem to report`() {
        // Offline with nothing saved, or on mobile data with Wi-Fi only on: the schedule is fine.
        assertEquals(RotationHealthVerdict.HEALTHY, classify(lastResult = "deferred"))
    }

    @Test
    fun `last result casing does not change the verdict`() {
        assertEquals(RotationHealthVerdict.FAILING, classify(lastResult = "FAILURE"))
        assertEquals(RotationHealthVerdict.THROTTLED, classify(lastResult = "Retry"))
    }

    @Test
    fun `a multi-reason stop string is parsed reason by reason`() {
        assertEquals(
            RotationHealthVerdict.THROTTLED,
            classify(stopReason = "CANCELLED_BY_APP=1, CONSTRAINT_CONNECTIVITY=2"),
        )
    }
}

class RotationHealthActionHintTest {

    private val guidance = backgroundBatteryGuidanceForManufacturer("samsung")

    private fun hint(
        verdict: RotationHealthVerdict,
        exempt: Boolean? = true,
        errorClass: String? = null,
        deferral: String? = null,
    ) = rotationHealthActionHint(verdict, exempt, guidance, errorClass, deferral)

    @Test
    fun `a healthy schedule gets no advice`() {
        // A row that always has advice is a row nobody reads when it matters.
        assertNull(hint(RotationHealthVerdict.HEALTHY))
    }

    @Test
    fun `every other verdict gets advice`() {
        for (verdict in RotationHealthVerdict.entries - RotationHealthVerdict.HEALTHY) {
            assertNotNull("$verdict should have an action hint", hint(verdict))
        }
    }

    @Test
    fun `a restricted throttled app is pointed at its own OEM battery screen`() {
        val text = hint(RotationHealthVerdict.THROTTLED, exempt = false).orEmpty()
        assertTrue(text.contains("Samsung"))
        assertTrue(text.contains("Sleeping"))
    }

    @Test
    fun `an exempt throttled app is not sent to the battery screen again`() {
        val text = hint(RotationHealthVerdict.THROTTLED, exempt = true).orEmpty()
        assertFalse(text.contains("Samsung"))
        assertTrue(text.contains("waiting on a condition"))
    }

    @Test
    fun `a network failure names the connection rather than the support bundle`() {
        val text = hint(
            RotationHealthVerdict.FAILING,
            errorClass = "IOException",
        ).orEmpty()
        assertTrue(text.contains("IOException"))
        assertTrue(text.contains("connection"))
    }

    @Test
    fun `a permission deferral points at the permission`() {
        val text = hint(
            RotationHealthVerdict.FAILING,
            deferral = "missing permission POST_NOTIFICATIONS",
        ).orEmpty()
        assertTrue(text.contains("permission"))
    }

    @Test
    fun `an unclassified failure falls back to run now`() {
        val text = hint(RotationHealthVerdict.FAILING).orEmpty()
        assertTrue(text.contains("Run now"))
    }

    @Test
    fun `a lost schedule tells the user how to rebuild it`() {
        val text = hint(RotationHealthVerdict.NOT_SCHEDULED).orEmpty()
        assertTrue(text.contains("Run now"))
    }
}

class RotationHealthSnapshotTest {

    @Test
    fun `only the three actionable verdicts need attention`() {
        val needing = RotationHealthVerdict.entries.filter {
            RotationHealthSnapshot(verdict = it).needsAttention
        }
        assertEquals(
            listOf(
                RotationHealthVerdict.THROTTLED,
                RotationHealthVerdict.NOT_SCHEDULED,
                RotationHealthVerdict.FAILING,
            ).sorted(),
            needing.sorted(),
        )
    }

    @Test
    fun `a boot is only reported as seen when one was recorded`() {
        assertFalse(RotationHealthSnapshot().bootReceiverFired)
        assertTrue(
            RotationHealthSnapshot(bootReceiverLastUtc = "2026-08-20T10:00:00Z")
                .bootReceiverFired,
        )
    }

    @Test
    fun `a fresh snapshot reads as disabled rather than broken`() {
        val snapshot = RotationHealthSnapshot()
        assertEquals(RotationHealthVerdict.DISABLED, snapshot.verdict)
        assertFalse(snapshot.needsAttention)
        assertNull(snapshot.nextFireUtc)
    }
}
