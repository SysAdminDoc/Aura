package com.freevibe.data.repository

import java.util.concurrent.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseReadTimeoutTest {
    @Test
    fun `completed reads return their value`() = runTest {
        val result = awaitFirebaseRead(surface = "Community profile", timeoutMs = 1_000) {
            "loaded"
        }

        assertEquals("loaded", result)
    }

    @Test
    fun `timed out reads fail without cancelling caller`() = runTest {
        val result = runCatching {
            awaitFirebaseRead(surface = "Community profile", timeoutMs = 1) {
                delay(10)
                "loaded"
            }
        }

        val error = result.exceptionOrNull()
        assertTrue(error is FirebaseReadTimeoutException)
        assertEquals("Community profile did not respond in 1 ms", error?.message)
        assertFalse(error is CancellationException)
    }
}
