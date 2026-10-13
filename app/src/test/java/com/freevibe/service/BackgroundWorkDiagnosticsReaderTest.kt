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
    fun summarizeWorkInfoStopReasonsOmitsActiveRecordsAndCountsStops() {
        assertEquals(
            "QUOTA=2, TIMEOUT=1",
            summarizeWorkInfoStopReasons(
                listOf(
                    WorkInfo.STOP_REASON_NOT_STOPPED,
                    WorkInfo.STOP_REASON_QUOTA,
                    WorkInfo.STOP_REASON_TIMEOUT,
                    WorkInfo.STOP_REASON_QUOTA,
                ),
            ),
        )
        assertEquals(null, summarizeWorkInfoStopReasons(listOf(WorkInfo.STOP_REASON_NOT_STOPPED)))
    }

    @Test
    fun workInfoStopReasonLabelPreservesUnknownPlatformValues() {
        assertEquals("BACKGROUND_RESTRICTION", workInfoStopReasonLabel(WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION))
        assertEquals("UNKNOWN(99)", workInfoStopReasonLabel(99))
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
            "Data Saver is restricting background data; allow unrestricted data for Aura or use Wi-Fi, then refresh diagnostics. OEM recovery (Android): Open App info > Battery for Aura and allow unrestricted or background battery usage before retesting schedules.",
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
            "Waiting for Wi-Fi or another unmetered network before this larger download can run. OEM recovery (Android): Open App info > Battery for Aura and allow unrestricted or background battery usage before retesting schedules.",
            hint,
        )
    }

    @Test
    fun backgroundWorkActionHintExplainsDailyWallpaperSourceDeferral() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Daily wallpaper notification",
                uniqueWorkName = DailyWallpaperWorker.WORK_NAME,
                workInfoStatus = "RUNNING=1",
                lastResult = "retry",
                lastDeferralReason = "no eligible Bing or Wallhaven daily wallpaper was available",
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = false,
                restrictBackgroundStatus = "disabled",
            ),
        )

        assertEquals(
            "No daily wallpaper was available from Bing or Wallhaven; check enabled providers or wait for the next run.",
            hint,
        )
    }

    @Test
    fun backgroundWorkActionHintExplainsExcludedThemeItemWithoutWorkInfo() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Theme wallpaper switching",
                uniqueWorkName = SystemThemeListener.WORK_NAME,
                workInfoStatus = "No WorkInfo records",
                lastResult = "failure",
                lastErrorClass = "RotationItemExcluded",
                lastDeferralReason = "The selected theme wallpaper is excluded.",
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = false,
                restrictBackgroundStatus = "disabled",
            ),
        )

        assertEquals(
            "Restore the item in Settings > Wallpaper rotation > Rotation exclusions, then change theme again.",
            hint,
        )
    }

    @Test
    fun backgroundWorkActionHintTreatsAWaitForConnectionAsNothingToFix() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Auto wallpaper rotation",
                uniqueWorkName = AutoWallpaperWorker.WORK_NAME,
                workInfoStatus = "ENQUEUED=1",
                lastResult = "deferred",
                lastDeferralReason = rotationHoldReason(RotationNetworkAccess.OFFLINE),
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = false,
                restrictBackgroundStatus = "disabled",
            ),
        )

        assertEquals(
            "Nothing to fix. The last run waited for a connection, and the next one tries again on its own.",
            hint,
        )
    }

    @Test
    fun backgroundBatteryGuidanceSelectsSamsungRecovery() {
        val guide = backgroundBatteryGuidanceForManufacturer("Samsung")

        assertEquals("Samsung", guide.manufacturer)
        assertEquals(
            "Open Settings > Battery > Background usage limits and remove Aura from Sleeping and Deep sleeping apps.",
            guide.summary,
        )
    }

    @Test
    fun backgroundBatteryGuidanceSelectsPixelRecovery() {
        val guide = backgroundBatteryGuidanceForManufacturer("Google")

        assertEquals("Pixel", guide.manufacturer)
        assertEquals(
            "Open Settings > Apps > Aura > App battery usage and allow background usage; disable Battery Saver while testing schedules.",
            guide.summary,
        )
    }

    @Test
    fun backgroundBatteryGuidanceFallsBackToGenericAndroidRecovery() {
        val guide = backgroundBatteryGuidanceForManufacturer("Fairphone")

        assertEquals("Android", guide.manufacturer)
        assertEquals(
            "Open App info > Battery for Aura and allow unrestricted or background battery usage before retesting schedules.",
            guide.summary,
        )
    }

    @Test
    fun backgroundWorkActionHintExplainsMissingWorkInfoWithSamsungRecovery() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Weather wallpaper refresh",
                uniqueWorkName = WeatherUpdateWorker.WORK_NAME,
                workInfoStatus = "No WorkInfo records",
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = false,
                restrictBackgroundStatus = "disabled",
            ),
            batteryGuidance = backgroundBatteryGuidanceForManufacturer("Samsung"),
        )

        assertEquals(
            "No WorkInfo records are visible yet; open Aura once after reboot, wait for the next schedule window, then refresh diagnostics. OEM recovery (Samsung): Open Settings > Battery > Background usage limits and remove Aura from Sleeping and Deep sleeping apps.",
            hint,
        )
    }

    @Test
    fun backgroundWorkActionHintExplainsLongEnqueuedWorkWithPixelRecovery() {
        val hint = backgroundWorkActionHint(
            row = BackgroundWorkStatusRow(
                label = "Auto wallpaper rotation",
                uniqueWorkName = AutoWallpaperWorker.WORK_NAME,
                workInfoStatus = "ENQUEUED=1",
            ),
            network = BackgroundNetworkDiagnostics(
                activeNetworkMetered = false,
                restrictBackgroundStatus = "disabled",
            ),
            batteryGuidance = backgroundBatteryGuidanceForManufacturer("Google Pixel"),
        )

        assertEquals(
            "Waiting for the next run window or constraints such as network, battery, charging, idle, or unmetered network. OEM recovery (Pixel): Open Settings > Apps > Aura > App battery usage and allow background usage; disable Battery Saver while testing schedules.",
            hint,
        )
    }
}
