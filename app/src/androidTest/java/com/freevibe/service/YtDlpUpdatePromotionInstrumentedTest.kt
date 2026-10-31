package com.freevibe.service

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs an update the way Settings does, against the real yt-dlp and Python on the phone. Each test
 * ends with a real extraction, since an update only counts once one works. Pass a page with
 * `-e ytUrl <url>`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class YtDlpUpdatePromotionInstrumentedTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pageUrl get() = InstrumentationRegistry.getArguments().getString("ytUrl")
    private val root get() = File(context.noBackupFilesDir, "youtubedl-android")
    private val runtimeDir get() = File(root, "yt-dlp")
    private val manager by lazy { YtDlpUpdateManager(context, Dispatchers.IO) }

    @Before
    fun startFromTheBundledRuntime() {
        Assume.assumeTrue("Pass -e ytUrl <url> to run this", !pageUrl.isNullOrBlank())
        resetRuntime()
        // init only runs once per process, and init_ytdlp puts the bundled copy back when it's gone.
        YoutubeDL.getInstance().init(context)
        YoutubeDL.getInstance().init_ytdlp(context, runtimeDir)
        // The wrapper keeps no version for the copy it bundles, only for one it downloaded.
        assertEquals(null, YoutubeDL.getInstance().version(context))
    }

    @After
    fun leaveTheBundledRuntime() {
        resetRuntime()
    }

    @Test
    fun aRealUpdateIsPromotedAfterItsFirstExtraction() = runBlocking<Unit> {
        val result = manager.updateStable(YtDlpUpdateConsent.REPOSITORY_CHECKS_BYPASS_CONFIRMED)

        assertEquals(result.snapshot.lastError, YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION, result.status)
        assertTrue(result.snapshot.pendingValidation)
        assertTrue(result.snapshot.rollbackAvailable)
        val updated = checkNotNull(result.snapshot.activeVersion)
        Log.i(TAG, "updated to $updated (${result.snapshot.activeVersionName})")

        assertExtractionWorks()
        manager.recordExtractionSuccess()

        val validated = manager.snapshot()
        assertEquals(YtDlpUpdateStatus.VALIDATED, validated.lastStatus)
        assertFalse(validated.pendingValidation)
        assertFalse(validated.rollbackAvailable)
        assertEquals(updated, validated.activeVersion)
    }

    @Test
    fun aBrokenUpdateIsRolledBackAndThePreviousExtractorStillWorks() = runBlocking<Unit> {
        manager.runtime = BrokenPayloadRuntime()

        val result = manager.updateStable(YtDlpUpdateConsent.REPOSITORY_CHECKS_BYPASS_CONFIRMED)

        assertEquals(result.snapshot.lastError, YtDlpUpdateStatus.UPDATED_PENDING_VALIDATION, result.status)
        assertEquals(BROKEN_VERSION, manager.snapshot().activeVersion)
        val failure = runCatching { extractStreamUrl() }.exceptionOrNull()
        Log.i(TAG, "broken payload answered: $failure")
        assertTrue("the broken payload still extracted", failure != null)

        assertTrue(manager.recordExtractionFailure(checkNotNull(failure)))

        val rolledBack = manager.snapshot()
        assertEquals(YtDlpUpdateStatus.ROLLED_BACK, rolledBack.lastStatus)
        assertFalse(rolledBack.pendingValidation)
        assertEquals(null, rolledBack.activeVersion)
        assertExtractionWorks()
    }

    @Test
    fun anUpdateBelowTheSecurityFloorIsRefusedAndThePreviousExtractorStillWorks() = runBlocking<Unit> {
        manager.runtime = BrokenPayloadRuntime(reportedName = "2020.01.01")

        val result = manager.updateStable(YtDlpUpdateConsent.REPOSITORY_CHECKS_BYPASS_CONFIRMED)

        assertEquals(YtDlpUpdateStatus.FAILED, result.status)
        assertFalse(result.snapshot.pendingValidation)
        assertEquals(null, YoutubeDL.getInstance().version(context))
        assertExtractionWorks()
    }

    private fun assertExtractionWorks() {
        assertTrue(extractStreamUrl().startsWith("https://"))
    }

    private fun extractStreamUrl(): String {
        val response = YoutubeDL.getInstance().execute(
            YoutubeDLRequest(pageUrl!!).apply {
                addOption("-f", "best[ext=mp4]/best")
                addOption("--get-url")
                addOption("--no-playlist")
            },
        )
        Log.i(TAG, "yt-dlp stderr: ${response.err}")
        return response.out.trim()
    }

    private fun resetRuntime() {
        listOf("yt-dlp", "yt-dlp.rollback").forEach { name ->
            check(File(root, name).deleteRecursively()) { "couldn't clear $name" }
        }
        context.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("freevibe_ytdlp_update", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Stands in for a download that gets past the wrapper and turns out not to run. */
    private inner class BrokenPayloadRuntime(
        private val reportedName: String = "2099.01.01",
    ) : YtDlpRuntime {
        override fun init(context: Context) = YoutubeDL.getInstance().init(context)

        override fun updateStable(context: Context): YoutubeDL.UpdateStatus {
            File(runtimeDir, "yt-dlp").writeText("this is not a zipapp\n")
            context.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE).edit()
                .putString("dlpVersion", BROKEN_VERSION)
                .putString("dlpVersionName", reportedName)
                .commit()
            return YoutubeDL.UpdateStatus.DONE
        }

        override fun version(context: Context): String? = YoutubeDL.getInstance().version(context)

        override fun versionName(context: Context): String? = YoutubeDL.getInstance().versionName(context)

        override fun initYtDlp(context: Context, runtimeDir: File) =
            YoutubeDL.getInstance().init_ytdlp(context, runtimeDir)
    }

    private companion object {
        const val TAG = "YtDlpUpdateTest"
        const val BROKEN_VERSION = "broken-payload"
    }
}
