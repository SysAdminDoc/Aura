package com.freevibe.service

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundWorkReceiptStoreTest {

    @Test
    fun recordRetryAndSuccessUpdateReceiptFields() {
        val store = BackgroundWorkReceiptStore(mockContextWithPrefs())

        store.recordRetry(
            uniqueWorkName = "weather_update",
            errorClass = "IOException",
            deferralReason = "network unavailable",
        )

        val retry = store.read("weather_update")
        assertEquals("retry", retry.lastResult)
        assertEquals("IOException", retry.lastErrorClass)
        assertEquals("network unavailable", retry.lastDeferralReason)
        assertNotNull(retry.lastFailureUtc)
        assertTrue(retry.lastFailureUtc!!.endsWith("Z"))

        store.recordSuccess("weather_update")

        val success = store.read("weather_update")
        assertEquals("success", success.lastResult)
        assertNotNull(success.lastSuccessUtc)
        assertNull(success.lastErrorClass)
        assertNull(success.lastDeferralReason)
        assertEquals(retry.lastFailureUtc, success.lastFailureUtc)
    }

    @Test
    fun recordFailureStoresErrorAndReason() {
        val store = BackgroundWorkReceiptStore(mockContextWithPrefs())

        store.recordFailure(
            uniqueWorkName = "auto_wallpaper",
            errorClass = "IllegalStateException",
            deferralReason = "worker failed",
        )

        val failure = store.read("auto_wallpaper")
        assertEquals("failure", failure.lastResult)
        assertEquals("IllegalStateException", failure.lastErrorClass)
        assertEquals("worker failed", failure.lastDeferralReason)
        assertNotNull(failure.lastFailureUtc)
    }

    @Test
    fun recordDeferredKeepsBothTimestampsAndDropsTheError() {
        val store = BackgroundWorkReceiptStore(mockContextWithPrefs())
        store.recordSuccess("auto_wallpaper")
        store.recordRetry(
            uniqueWorkName = "auto_wallpaper",
            errorClass = "IOException",
            deferralReason = "network unavailable",
        )
        val before = store.read("auto_wallpaper")

        store.recordDeferred("auto_wallpaper", "waiting for a connection")

        val deferred = store.read("auto_wallpaper")
        assertEquals("deferred", deferred.lastResult)
        assertEquals("waiting for a connection", deferred.lastDeferralReason)
        assertNull("waiting is not an error", deferred.lastErrorClass)
        assertEquals("nothing rotated, so the last success stands", before.lastSuccessUtc, deferred.lastSuccessUtc)
        assertEquals("nothing failed, so the last failure stands", before.lastFailureUtc, deferred.lastFailureUtc)
    }

    private fun mockContextWithPrefs(): Context {
        val values = linkedMapOf<String, String>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            values[firstArg<String>()] = secondArg<String>()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }
        every { editor.apply() } returns Unit

        val prefs = mockk<SharedPreferences>()
        every { prefs.getString(any(), any()) } answers {
            values[firstArg<String>()] ?: secondArg()
        }
        every { prefs.edit() } returns editor

        return mockk<Context>().also { context ->
            every { context.getSharedPreferences(any(), any()) } returns prefs
        }
    }
}
