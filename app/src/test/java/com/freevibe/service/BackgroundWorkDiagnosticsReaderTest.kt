package com.freevibe.service

import android.net.ConnectivityManager
import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundWorkDiagnosticsReaderTest {

    @Test
    fun summarizeWorkInfoStatesCountsSortedStates() {
        val summary = summarizeWorkInfoStates(
            listOf(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
                WorkInfo.State.RUNNING,
                WorkInfo.State.SUCCEEDED,
            ),
        )

        assertEquals("ENQUEUED=1, RUNNING=2, SUCCEEDED=1", summary)
    }

    @Test
    fun summarizeWorkInfoStatesHandlesEmptyReceipt() {
        assertEquals("No WorkInfo records", summarizeWorkInfoStates(emptyList()))
    }

    @Test
    fun restrictBackgroundStatusLabelMapsConnectivityStatuses() {
        assertEquals(
            "disabled",
            restrictBackgroundStatusLabel(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED),
        )
        assertEquals(
            "whitelisted",
            restrictBackgroundStatusLabel(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED),
        )
        assertEquals(
            "enabled",
            restrictBackgroundStatusLabel(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED),
        )
        assertEquals("unknown(99)", restrictBackgroundStatusLabel(99))
    }

    @Test
    fun backgroundWorkActionHintPrioritizesDataSaverRestriction() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Daily wallpaper notification",
                uniqueWorkName = DailyWallpaperWorker.WORK_NAME,
                workInfoStatus = "ENQUEUED=1",
                lastResult = "retry",
                lastDeferralReason = "no eligible Reddit daily wallpaper was available",
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = true,
                restrictBackgroundStatus = "enabled",
            ),
        )

        assertEquals(
            "Data Saver is restricting background data; allow unrestricted data for Aura or use Wi-Fi, then refresh diagnostics.",
            hint,
        )
    }

    @Test
    fun backgroundWorkActionHintExplainsUnmeteredDownloadWait() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Aura Originals download",
                uniqueWorkName = "aura_originals_download",
                workInfoStatus = "ENQUEUED=1",
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = true,
                restrictBackgroundStatus = "disabled",
            ),
        )

        assertEquals(
            "Waiting for Wi-Fi or another unmetered network before this larger download can run.",
            hint,
        )
    }

    @Test
    fun backgroundWorkActionHintExplainsSourceSpecificDeferral() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Daily wallpaper notification",
                uniqueWorkName = DailyWallpaperWorker.WORK_NAME,
                workInfoStatus = "RUNNING=1",
                lastResult = "retry",
                lastDeferralReason = "no eligible Reddit daily wallpaper was available",
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = false,
                restrictBackgroundStatus = "disabled",
            ),
        )

        assertEquals(
            "No safe Reddit wallpaper was available; review subreddit settings or wait for the next daily run.",
            hint,
        )
    }
}
