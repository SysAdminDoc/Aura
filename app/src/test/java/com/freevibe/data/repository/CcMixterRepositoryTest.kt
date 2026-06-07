package com.freevibe.data.repository

import com.freevibe.data.remote.ccmixter.CcMixterApi
import com.freevibe.data.remote.ccmixter.CcMixterUpload
import com.freevibe.service.SourceMetrics
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import javax.net.ssl.SSLHandshakeException

class CcMixterRepositoryTest {

    @Test
    fun `search fails closed on TLS failure without HTTP downgrade`() = runTest {
        val metrics = SourceMetrics()
        val repository = CcMixterRepository(
            api = object : CcMixterApi {
                override suspend fun searchUploads(
                    format: String,
                    search: String,
                    limit: Int,
                    sort: String,
                ): List<CcMixterUpload> = throw SSLHandshakeException("broken cert chain")
            },
            sourceMetrics = metrics,
        )

        try {
            repository.search(query = "ring tone", limit = 15)
            fail("Expected ccMixter TLS failures to propagate")
        } catch (error: SSLHandshakeException) {
            assertEquals("broken cert chain", error.message)
        }

        val snapshot = metrics.snapshot("ccmixter")
        assertEquals(1L, snapshot?.failureCount)
        assertEquals("SSLHandshakeException", snapshot?.lastErrorClass)
    }
}
