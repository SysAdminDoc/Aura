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
}
