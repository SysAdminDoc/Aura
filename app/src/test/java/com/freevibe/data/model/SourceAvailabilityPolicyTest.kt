package com.freevibe.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceAvailabilityPolicyTest {

    @Test
    fun `sourceUnavailableReasonForFailure classifies explicit removed statuses`() {
        assertEquals(
            "Pexels media is unavailable or removed",
            sourceUnavailableReasonForFailure(
                ContentSource.PEXELS,
                IllegalStateException("Download failed: HTTP 404"),
            ),
        )
        assertEquals(
            "YouTube media is unavailable or removed (gone)",
            sourceUnavailableReasonForFailure(
                ContentSource.YOUTUBE,
                IllegalStateException("HTTP 410"),
            ),
        )
        assertEquals(
            "Source post is unavailable or removed",
            sourceUnavailableReasonForFailure(
                ContentSource.REDDIT,
                IllegalStateException("post was removed"),
            ),
        )
        assertEquals(
            "Reddit public source is discontinued",
            sourceUnavailableReasonForFailure(
                ContentSource.REDDIT,
                IllegalStateException("HTTP 403 Forbidden"),
            ),
        )
    }

    @Test
    fun `sourceUnavailableReasonForFailure ignores transient failures`() {
        assertNull(sourceUnavailableReasonForFailure(ContentSource.PIXABAY, IllegalStateException("HTTP 500")))
        assertNull(sourceUnavailableReasonForFailure(ContentSource.PIXABAY, IllegalStateException("HTTP 403")))
        assertNull(sourceUnavailableReasonForFailure(ContentSource.PIXABAY, java.net.SocketTimeoutException("timeout")))
        assertNull(sourceUnavailableReasonForFailure(ContentSource.PIXABAY, null))
    }
}
