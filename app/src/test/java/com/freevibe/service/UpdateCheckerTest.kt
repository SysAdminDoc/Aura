package com.freevibe.service

import com.freevibe.data.local.PreferencesManager
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class UpdateCheckerTest {
    private val enabled = MutableStateFlow(true)
    private val lastAt = MutableStateFlow(0L)
    private val stored = MutableStateFlow("")
    private val dismissedTag = MutableStateFlow("")
    private val prefs = mockk<PreferencesManager>().also {
        every { it.updateCheckEnabled } returns enabled
        every { it.updateCheckLastAt } returns lastAt
        every { it.updateCheckRelease } returns stored
        every { it.updateCheckDismissedTag } returns dismissedTag
        coEvery { it.setUpdateCheckEnabled(any()) } answers { enabled.value = firstArg() }
        coEvery { it.setUpdateCheckLastAt(any()) } answers { lastAt.value = firstArg() }
        coEvery { it.setUpdateCheckRelease(any()) } answers { stored.value = firstArg() }
        coEvery { it.setUpdateCheckDismissedTag(any()) } answers { dismissedTag.value = firstArg() }
    }
    private val feed = FakeReleaseFeed()
    private var posture = NetworkPosture(metered = false, dataSaver = false)
    private var clock = NOW

    private fun checker(
        installedVersionCode: Int = 151,
        installedVersionName: String = "6.46.1",
        offered: Boolean = true,
    ) = UpdateChecker(
        prefs = prefs,
        feed = feed,
        posture = { posture },
        moshi = Moshi.Builder().build(),
        installedVersionCode = installedVersionCode,
        installedVersionName = installedVersionName,
        offered = offered,
        now = { clock },
    )

    @Test
    fun `no published release reports NoRelease and counts as today's check`() = runTest {
        feed.response = ReleaseFeedResponse(404, "")
        val checker = checker()

        assertEquals(UpdateCheckResult.NoRelease, checker.checkIfDue())
        assertEquals(NOW, lastAt.value)
        assertNull(checker.available.first())
    }

    @Test
    fun `the installed version reports UpToDate and drops an older stored notice`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.46.1", versionCode = 151))
        val checker = checker()
        stored.value = """{"tag":"v6.46.0","versionName":"6.46.0","versionCode":150,"notes":"","pageUrl":"$PAGE"}"""

        assertEquals(UpdateCheckResult.UpToDate("6.46.1"), checker.checkIfDue())
        assertEquals("", stored.value)
        assertNull(checker.available.first())
    }

    @Test
    fun `a newer versionCode is offered, stored and raised as a notice`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.47.0", versionCode = 152, body = "## Fixed\n- **Crop** works"))
        val checker = checker()

        val result = checker.checkIfDue() as UpdateCheckResult.Available

        assertEquals("6.47.0", result.release.versionName)
        assertEquals(152, result.release.versionCode)
        assertEquals("Fixed\n• Crop works", result.release.notes)
        assertEquals(result.release, checker.available.first())
        assertEquals(result.release, checker.notice.first())
        assertEquals(result, checker.lastResult.value)
        assertEquals(NOW, lastAt.value)
    }

    @Test
    fun `a network failure keeps the last answer and doesn't count as today's check`() = runTest {
        val checker = checker()
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.47.0", versionCode = 152))
        checker.checkNow()
        val found = stored.value
        clock += 1_000
        feed.failure = IOException("offline")

        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.NETWORK), checker.checkNow())
        assertEquals(found, stored.value)
        assertEquals(NOW, lastAt.value)
        assertFalse(checker.checking.value)
    }

    @Test
    fun `rate limits and unreadable answers are failures`() = runTest {
        val checker = checker()
        feed.response = ReleaseFeedResponse(403, "")
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.RATE_LIMITED), checker.checkNow())
        feed.response = ReleaseFeedResponse(429, "")
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.RATE_LIMITED), checker.checkNow())
        feed.response = ReleaseFeedResponse(500, "")
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.UNREADABLE), checker.checkNow())
        feed.response = ReleaseFeedResponse(200, "<html>")
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.UNREADABLE), checker.checkNow())
        feed.response = ReleaseFeedResponse(200, """{"tag_name":"nightly"}""")
        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.UNREADABLE), checker.checkNow())
        assertEquals(0L, lastAt.value)
    }

    @Test
    fun `nothing is requested while the check is turned off`() = runTest {
        enabled.value = false
        val checker = checker()

        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.TURNED_OFF), checker.checkIfDue())
        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.TURNED_OFF), checker.checkNow())
        assertEquals(0, feed.calls)
    }

    @Test
    fun `the automatic check waits for an unmetered network and a new day`() = runTest {
        feed.response = ReleaseFeedResponse(404, "")
        val checker = checker()

        posture = NetworkPosture(metered = true, dataSaver = false)
        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.METERED), checker.checkIfDue())
        posture = NetworkPosture(metered = false, dataSaver = true)
        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.DATA_SAVER), checker.checkIfDue())
        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.DATA_SAVER), checker.lastResult.value)
        assertEquals(0, feed.calls)

        posture = NetworkPosture(metered = false, dataSaver = false)
        assertEquals(UpdateCheckResult.NoRelease, checker.checkIfDue())
        clock += AUTOMATIC_CHECK_INTERVAL_MS - 1
        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.CHECKED_RECENTLY), checker.checkIfDue())
        assertEquals(1, feed.calls)
    }

    @Test
    fun `one automatic try per app run even when it fails`() = runTest {
        feed.failure = IOException("offline")
        val checker = checker()

        assertEquals(UpdateCheckResult.Failed(UpdateCheckFailure.NETWORK), checker.checkIfDue())
        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.CHECKED_RECENTLY), checker.checkIfDue())
        assertEquals(1, feed.calls)
    }

    @Test
    fun `a Play build never asks, even with the setting left on`() = runTest {
        stored.value = """{"tag":"v6.47.0","versionName":"6.47.0","versionCode":152,"notes":"","pageUrl":"$PAGE"}"""
        val checker = checker(offered = false)

        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.TURNED_OFF), checker.checkIfDue())
        assertEquals(UpdateCheckResult.Skipped(UpdateCheckSkip.TURNED_OFF), checker.checkNow())
        assertEquals(0, feed.calls)
        assertNull(checker.available.first())
    }

    @Test
    fun `a check the user asks for runs on mobile data`() = runTest {
        posture = NetworkPosture(metered = true, dataSaver = true)
        feed.response = ReleaseFeedResponse(404, "")

        assertEquals(UpdateCheckResult.NoRelease, checker().checkNow())
        assertEquals(1, feed.calls)
    }

    @Test
    fun `a clock that moved backwards doesn't block the next automatic check`() {
        val open = NetworkPosture(metered = false, dataSaver = false)
        assertNull(automaticCheckSkip(enabled = true, lastCheckedAt = NOW + 5_000, now = NOW, posture = open))
        assertNull(automaticCheckSkip(enabled = true, lastCheckedAt = 0, now = NOW, posture = open))
        assertNull(automaticCheckSkip(enabled = true, lastCheckedAt = NOW - AUTOMATIC_CHECK_INTERVAL_MS, now = NOW, posture = open))
    }

    @Test
    fun `a release without versionCode file names is compared by version name`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.46.10", versionCode = null))
        assertTrue(checker().checkNow() is UpdateCheckResult.Available)

        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.46.1", versionCode = null))
        assertEquals(UpdateCheckResult.UpToDate("6.46.1"), checker().checkNow())

        assertTrue(compareVersionNames("6.47", "6.46.9") > 0)
        assertEquals(0, compareVersionNames("6.46", "6.46.0"))
        assertTrue(compareVersionNames("6.9.0", "6.10.0") < 0)
    }

    @Test
    fun `an older published release is never offered`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.45.3", versionCode = 148))
        assertEquals(UpdateCheckResult.UpToDate("6.45.3"), checker().checkNow())
    }

    @Test
    fun `drafts and prereleases are not offered`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.47.0", versionCode = 152, extra = ""","draft":true"""))
        assertEquals(UpdateCheckResult.NoRelease, checker().checkNow())
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.47.0", versionCode = 152, extra = ""","prerelease":true"""))
        assertEquals(UpdateCheckResult.NoRelease, checker().checkNow())
    }

    @Test
    fun `only a page under Aura's own releases is opened`() {
        assertEquals(PAGE, releasePageUrl(PAGE))
        assertEquals(RELEASES_PAGE_URL, releasePageUrl(null))
        assertEquals(RELEASES_PAGE_URL, releasePageUrl("https://example.com/SysAdminDoc/Aura/releases/tag/v6.47.0"))
        assertEquals(RELEASES_PAGE_URL, releasePageUrl("https://github.com/SysAdminDoc/Aura/releases/tag/v6.47.0 x"))
        assertEquals(RELEASES_PAGE_URL, releasePageUrl("http://github.com/SysAdminDoc/Aura/releases/tag/v6.47.0"))
    }

    @Test
    fun `release notes lose their Markdown and stay bounded`() {
        assertEquals(
            "Added\n• Crop presets, see the guide\n  • Nested point\n\nRun gradlew to build",
            plainReleaseNotes("### Added\r\n- **Crop** presets, see [the guide](https://example.com/g)\r\n  * Nested point\r\n\r\n\r\n\r\nRun `gradlew` to build\r\n"),
        )
        val long = plainReleaseNotes("x".repeat(MAX_RELEASE_NOTES_CHARS + 50))
        assertEquals(MAX_RELEASE_NOTES_CHARS + 1, long.length)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun `closing the notice hides it but Settings still lists the version`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.47.0", versionCode = 152))
        val checker = checker()
        val release = (checker.checkNow() as UpdateCheckResult.Available).release

        checker.dismissNotice(release)

        assertNull(checker.notice.first())
        assertEquals(release, checker.available.first())

        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.48.0", versionCode = 153))
        checker.checkNow()
        assertEquals("v6.48.0", checker.notice.first()?.tag)
    }

    @Test
    fun `installing the offered version clears it`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.47.0", versionCode = 152))
        checker().checkNow()

        assertNull(checker(installedVersionCode = 152, installedVersionName = "6.47.0").available.first())
    }

    @Test
    fun `turning the check off forgets what it found`() = runTest {
        feed.response = ReleaseFeedResponse(200, releaseJson(tag = "v6.47.0", versionCode = 152))
        val checker = checker()
        checker.dismissNotice((checker.checkNow() as UpdateCheckResult.Available).release)

        checker.setEnabled(false)

        assertFalse(enabled.value)
        assertEquals("", stored.value)
        assertEquals(0L, lastAt.value)
        assertEquals("", dismissedTag.value)
        assertNull(checker.lastResult.value)
        assertNull(checker.available.first())
    }

    private class FakeReleaseFeed : ReleaseFeed {
        var response: ReleaseFeedResponse = ReleaseFeedResponse(404, "")
        var failure: IOException? = null
        var calls = 0

        override suspend fun latest(): ReleaseFeedResponse {
            calls++
            failure?.let { throw it }
            return response
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val PAGE = "https://github.com/SysAdminDoc/Aura/releases/tag/v6.47.0"

        fun releaseJson(tag: String, versionCode: Int?, body: String = "", extra: String = ""): String {
            val assets = if (versionCode == null) {
                """[{"name":"Aura.apk"}]"""
            } else {
                """[{"name":"Aura-$tag-versionCode-$versionCode-arm64-v8a.apk"},{"name":"SHA256SUMS.txt"}]"""
            }
            val notes = body.replace("\n", "\\n")
            return """{"tag_name":"$tag","body":"$notes","html_url":"$PAGE","assets":$assets$extra}"""
        }
    }
}
