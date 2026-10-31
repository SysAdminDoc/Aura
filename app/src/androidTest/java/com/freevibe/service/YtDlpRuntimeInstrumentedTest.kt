package com.freevibe.service

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.freevibe.R
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The APK ships no FFmpeg, and the wrapper still points yt-dlp at where it would be. Nothing Aura
 * asks yt-dlp for merges streams, so it should warn and carry on. Pass a page with `-e ytUrl <url>`.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class YtDlpRuntimeInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pageUrl get() = InstrumentationRegistry.getArguments().getString("ytUrl")

    @Test
    fun resolvesAStreamUrlWithoutFfmpeg() {
        val url = pageUrl
        Assume.assumeTrue("Pass -e ytUrl <url> to run this", !url.isNullOrBlank())
        YoutubeDL.getInstance().init(context)

        val response = YoutubeDL.getInstance().execute(
            YoutubeDLRequest(url!!).apply {
                addOption("-f", "bestvideo[ext=mp4]/best[ext=mp4]/best")
                addOption("--get-url")
            },
        )

        Log.i(TAG, "yt-dlp stderr: ${response.err}")
        assertTrue(response.out, response.out.trim().startsWith("https://"))
    }

    @Test
    fun downloadsAVideoWithoutFfmpeg() {
        val url = pageUrl
        Assume.assumeTrue("Pass -e ytUrl <url> to run this", !url.isNullOrBlank())
        YoutubeDL.getInstance().init(context)
        val output = File(context.cacheDir, "yt_dlp_runtime_check.mp4")
        output.delete()

        try {
            val response = YoutubeDL.getInstance().execute(
                YoutubeDLRequest(url!!).apply {
                    addOption("-f", "bestvideo[ext=mp4][height<=360]/best[ext=mp4]/best")
                    addOption("-o", output.absolutePath)
                    addOption("--force-overwrites")
                    addOption("--no-playlist")
                    addOption("--max-filesize", (60L * 1024L * 1024L).toString())
                },
            )

            Log.i(TAG, "yt-dlp stderr: ${response.err}")
            assertTrue("no file at $output", output.length() > 0L)
        } finally {
            output.delete()
        }
    }

    /**
     * The mweb client's adaptive formats are skipped until a PO token turns up, so format 140 shows
     * what the provider changes. Pass a self-hosted server with `-e potUrl <https url>` too.
     */
    @Test
    fun poTokenProviderUnlocksTheFormatsMwebHidesWithoutIt() {
        val url = pageUrl
        val rawProviderUrl = InstrumentationRegistry.getArguments().getString("potUrl")
        Assume.assumeTrue(
            "Pass -e ytUrl <url> and -e potUrl <https url> to run this",
            !url.isNullOrBlank() && !rawProviderUrl.isNullOrBlank(),
        )
        val providerUrl = checkNotNull(normalizeYouTubePoTokenProviderUrl(rawProviderUrl!!))
        YoutubeDL.getInstance().init(context)
        val plugins = File(context.cacheDir, "yt_dlp_plugin_check").apply {
            deleteRecursively()
            mkdirs()
        }
        context.resources.openRawResource(R.raw.bgutil_ytdlp_pot_provider).use { input ->
            File(plugins, "bgutil-ytdlp-pot-provider-1.3.1.zip").outputStream().use { input.copyTo(it) }
        }
        val output = File(context.cacheDir, "po_token_check.m4a")
        output.delete()

        try {
            val withoutProvider = runCatching {
                YoutubeDL.getInstance().execute(
                    YoutubeDLRequest(url!!).apply {
                        addOption("--extractor-args", "youtube:player_client=mweb")
                        addOption("-f", "140")
                        addOption("--get-url")
                    },
                )
            }
            Log.i(TAG, "without a provider: ${withoutProvider.exceptionOrNull()?.message}")
            assertTrue("format 140 turned up with no PO token", withoutProvider.isFailure)

            val response = YoutubeDL.getInstance().execute(
                YoutubeDLRequest(url!!).apply {
                    applyYouTubePoTokenOptions(this, plugins, providerUrl)
                    addOption("--verbose")
                    addOption("-f", "140")
                    addOption("-o", output.absolutePath)
                    addOption("--force-overwrites")
                    addOption("--no-playlist")
                },
            )

            Log.i(TAG, "with the provider: ${response.err}")
            assertTrue(response.err, response.err.contains("Retrieved a gvs PO Token for mweb client"))
            assertTrue("no file at $output", output.length() > 0L)
        } finally {
            output.delete()
            plugins.deleteRecursively()
        }
    }

    private companion object {
        const val TAG = "YtDlpRuntimeTest"
    }
}
