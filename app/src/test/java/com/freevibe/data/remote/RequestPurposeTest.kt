package com.freevibe.data.remote

import java.io.File
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestPurposeTest {
    private val metrics = RequestPurposeMetrics()

    @Test
    fun `tagged requests count under their purpose and provider`() {
        send("https://wallhaven.cc/api/v1/search", RequestPurpose.DOWNLOAD)
        send("https://wallhaven.cc/api/v1/search", RequestPurpose.DOWNLOAD)
        send("https://wallhaven.cc/api/v1/search", RequestPurpose.APPLY)

        assertEquals(listOf("wallhaven: download=2 apply=1"), metrics.rows())
    }

    @Test
    fun `an untagged request counts as catalog, which is what every Retrofit call is`() {
        send("https://wallhaven.cc/api/v1/search", purpose = null)

        assertEquals(listOf("wallhaven: catalog=1"), metrics.rows())
    }

    @Test
    fun `hosts outside the provider list share one row`() {
        send("https://api.github.com/repos/x/y/releases", RequestPurpose.UPDATE_CHECK)
        send("https://example.org/a.jpg", RequestPurpose.DOWNLOAD)

        assertEquals(listOf("other hosts: download=1 update_check=1"), metrics.rows())
    }

    @Test
    fun `rows never carry a url path or query`() {
        send("https://wallhaven.cc/api/v1/search?q=private-search-term&apikey=sentinel", RequestPurpose.DOWNLOAD)

        val row = metrics.rows().single()
        assertFalse(row.contains("private-search-term"))
        assertFalse(row.contains("sentinel"))
        assertFalse(row.contains("/api"))
    }

    @Test
    fun `a client default tags only what its caller left untagged`() {
        val images = client(DefaultRequestPurposeInterceptor(RequestPurpose.IMAGE))

        images.newCall(request("https://wallhaven.cc/a.jpg", purpose = null)).execute().close()
        images.newCall(request("https://wallhaven.cc/b.jpg", RequestPurpose.APPLY)).execute().close()

        assertEquals(listOf("wallhaven: image=1 apply=1"), metrics.rows())
    }

    @Test
    fun `a retry inside the chain counts as one request`() {
        val retrying = OkHttpClient.Builder()
            .addInterceptor(RequestPurposeInterceptor(metrics))
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request()).close()
                chain.proceed(chain.request())
            })
            .addInterceptor(cannedResponse)
            .build()

        retrying.newCall(request("https://wallhaven.cc/a.jpg", RequestPurpose.DOWNLOAD)).execute().close()

        assertEquals(listOf("wallhaven: download=1"), metrics.rows())
    }

    @Test
    fun `every request the app builds says what it is for`() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue("wrong working directory", sources.isNotEmpty())

        val untagged = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                "${file.name}:${index + 1}".takeIf { UNTAGGED_BUILDER.containsMatchIn(line) }
            }
        }

        assertEquals(emptyList<String>(), untagged)
    }

    private fun send(url: String, purpose: RequestPurpose?) {
        client().newCall(request(url, purpose)).execute().close()
    }

    private fun request(url: String, purpose: RequestPurpose?): Request = Request.Builder()
        .url(url)
        .apply { if (purpose != null) purpose(purpose) }
        .build()

    private fun client(default: Interceptor? = null): OkHttpClient = OkHttpClient.Builder()
        .apply { if (default != null) addInterceptor(default) }
        .addInterceptor(RequestPurposeInterceptor(metrics))
        .addInterceptor(cannedResponse)
        .build()

    private val cannedResponse = Interceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("".toResponseBody())
            .build()
    }

    private companion object {
        val UNTAGGED_BUILDER = Regex("""Request\.Builder\(\)(?!\.purpose\()""")
    }
}
