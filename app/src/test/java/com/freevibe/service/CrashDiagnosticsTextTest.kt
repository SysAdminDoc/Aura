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
    fun sanitizeRedactsProviderSpecificFixturesAndKeepsContext() {
        val raw = """
            source=wallhaven request=https://wallhaven.cc/api/v1/search?apikey=WALLHAVEN_QUERY_SENTINEL&q=forest
            source=pixabay request=https://pixabay.com/api/?key=PIXABAY_KEY_SENTINEL&image_type=photo
            source=freesound request=https://freesound.org/apiv2/search/text/?token=FREESOUND_TOKEN_SENTINEL
            source=soundcloud request=https://api.soundcloud.com/tracks?client_id=SOUNDCLOUD_CLIENT_SENTINEL
            source=pexels Authorization: Bearer PEXELS_BEARER_SENTINEL
            source=settings apiKey=SETTINGS_APIKEY_SENTINEL from local.properties
            local.properties pexels.api.key=PEXELS_LOCAL_PROPERTY_SENTINEL
            local.properties soundcloud.client.id=SOUNDCLOUD_LOCAL_CLIENT_SENTINEL
            local.properties stability.ai.key=STABILITY_LOCAL_SENTINEL
            cache=file:///storage/emulated/0/Download/private-provider.jpg
            appPath=/storage/emulated/0/Android/data/com.freevibe/files/images/private.png
            exact=/tmp/aura/files/local.properties
        """.trimIndent()

        val sanitized = CrashDiagnosticsText.sanitize(
            raw = raw,
            appPaths = listOf("/tmp/aura/files"),
        )

        listOf(
            "WALLHAVEN_QUERY_SENTINEL",
            "PIXABAY_KEY_SENTINEL",
            "FREESOUND_TOKEN_SENTINEL",
            "SOUNDCLOUD_CLIENT_SENTINEL",
            "PEXELS_BEARER_SENTINEL",
            "SETTINGS_APIKEY_SENTINEL",
            "PEXELS_LOCAL_PROPERTY_SENTINEL",
            "SOUNDCLOUD_LOCAL_CLIENT_SENTINEL",
            "STABILITY_LOCAL_SENTINEL",
            "/storage/emulated/0/Android/data/com.freevibe",
            "/tmp/aura/files",
            "file:///storage",
        ).forEach { sentinel ->
            assertFalse("Raw sentinel leaked: $sentinel", sanitized.contains(sentinel))
        }
        listOf("wallhaven", "pixabay", "freesound", "soundcloud", "pexels", "local.properties").forEach { context ->
            assertTrue("Context missing: $context", sanitized.contains(context))
        }
        assertTrue(sanitized.contains("apikey=<redacted>"))
        assertTrue(sanitized.contains("key=<redacted>"))
        assertTrue(sanitized.contains("token=<redacted>"))
        assertTrue(sanitized.contains("client_id=<redacted>"))
        assertTrue(sanitized.contains("authorization=<redacted>"))
        assertTrue(sanitized.contains("apiKey=<redacted>"))
        assertTrue(sanitized.contains("pexels.api.key=<redacted>"))
        assertTrue(sanitized.contains("soundcloud.client.id=<redacted>"))
        assertTrue(sanitized.contains("stability.ai.key=<redacted>"))
        assertTrue(sanitized.contains("file://<redacted-path>"))
        assertTrue(sanitized.contains("<app-private-path>"))
    }

    @Test
    fun tailTruncatesAndKeepsMarker() {
        val raw = (1..100).joinToString(separator = "\n") { "line-$it" }

        val tail = CrashDiagnosticsText.tail(raw, maxChars = 80)

        assertTrue(tail.startsWith("[tail truncated]"))
        assertTrue(tail.contains("line-100"))
        assertFalse(tail.contains("line-1\n"))
    }

    @Test
    fun formatBackgroundWorkSectionIncludesWorkNamesAndPendingReceipts() {
        val section = CrashDiagnosticsText.formatBackgroundWorkSection(
            rows = listOf(
                BackgroundWorkDiagnosticsRow(
                    label = "Auto wallpaper rotation",
                    uniqueWorkName = "auto_wallpaper",
                    enabledState = "enabled",
                    networkPosture = "unmetered network",
                    constraints = listOf("NetworkType.UNMETERED", "battery not low", "charging"),
                ),
                BackgroundWorkDiagnosticsRow(
                    label = "Rotation trigger one-shot",
                    uniqueWorkName = "rotation_trigger_oneshot",
                    enabledState = "unlock enabled",
                    networkPosture = "connected network",
                    constraints = listOf("NetworkType.CONNECTED", "battery not low"),
                ),
            ),
        )

        assertTrue(section.startsWith("## Background work"))
        assertTrue(section.contains("auto_wallpaper"))
        assertTrue(section.contains("rotation_trigger_oneshot"))
        assertTrue(section.contains("state=enabled"))
        assertTrue(section.contains("network=unmetered network"))
        assertTrue(section.contains("constraints=NetworkType.UNMETERED, battery not low, charging"))
        assertTrue(section.contains("WorkInfo=pending Settings WorkInfo receipt"))
        assertTrue(section.contains("Data Saver=pending ConnectivityManager Data Saver receipt"))
        assertTrue(section.contains("Live WorkManager receipt: pending Settings diagnostics"))
        assertTrue(section.contains("Live Data Saver receipt: pending Settings diagnostics"))
    }
}
