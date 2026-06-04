package com.freevibe.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDiagnosticsTextTest {

    @Test
    fun parseLastCrashAtReadsNewestSyntheticCrashEntry() {
        val first = CrashDiagnosticsText.formatCrashEntry(
            timestampLabel = "2026-06-04 08:10:00",
            threadName = "main",
            throwable = IllegalStateException("first crash"),
        )
        val second = CrashDiagnosticsText.formatCrashEntry(
            timestampLabel = "2026-06-04 08:45:00",
            threadName = "DefaultDispatcher-worker-1",
            throwable = RuntimeException("newest crash"),
        )

        val raw = first + second

        assertEquals("2026-06-04 08:45:00", CrashDiagnosticsText.parseLastCrashAt(raw))
        assertTrue(raw.contains("RuntimeException"))
        assertTrue(raw.contains("DefaultDispatcher-worker-1"))
    }

    @Test
    fun sanitizeRedactsSecretsTokensAndPrivatePaths() {
        val raw = """
            authorization: Bearer abc.def-123
            apiKey=plain-secret
            callback=https://example.invalid/crash?token=query-token&safe=value
            path=/data/user/0/com.freevibe/files/crash.log
            file=file:///storage/emulated/0/DCIM/private.jpg
            exact=/tmp/app/files/cache/crash.log
        """.trimIndent()

        val sanitized = CrashDiagnosticsText.sanitize(
            raw = raw,
            appPaths = listOf("/tmp/app/files"),
        )

        assertFalse(sanitized.contains("abc.def-123"))
        assertFalse(sanitized.contains("plain-secret"))
        assertFalse(sanitized.contains("query-token"))
        assertFalse(sanitized.contains("/data/user/0/com.freevibe"))
        assertFalse(sanitized.contains("/tmp/app/files"))
        assertFalse(sanitized.contains("file:///storage"))
        assertTrue(sanitized.contains("authorization=<redacted>"))
        assertTrue(sanitized.contains("apiKey=<redacted>"))
        assertTrue(sanitized.contains("token=<redacted>"))
        assertTrue(sanitized.contains("<app-private-path>"))
        assertTrue(sanitized.contains("file://<redacted-path>"))
    }

    @Test
    fun tailTruncatesAndKeepsMarker() {
        val raw = (1..100).joinToString(separator = "\n") { "line-$it" }

        val tail = CrashDiagnosticsText.tail(raw, maxChars = 80)

        assertTrue(tail.startsWith("[tail truncated]"))
        assertTrue(tail.contains("line-100"))
        assertFalse(tail.contains("line-1\n"))
    }
}
