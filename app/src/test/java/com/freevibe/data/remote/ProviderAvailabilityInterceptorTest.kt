package com.freevibe.data.remote

import com.freevibe.data.model.ContentSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderAvailabilityInterceptorTest {

    @Test
    fun `legacy provider hosts are blocked before network`() {
        val chain = mockk<Interceptor.Chain>()
        val request = request("api.freesound.org")
        every { chain.request() } returns request

        val error = assertThrows(IOException::class.java) {
            ProviderAvailabilityInterceptor().intercept(chain)
        }

        assertTrue(error.message.orEmpty().contains("FREESOUND"))
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `artifact-excluded active provider is blocked`() {
        val chain = mockk<Interceptor.Chain>()
        val request = request("www.youtube.com")
        every { chain.request() } returns request
        val interceptor = ProviderAvailabilityInterceptor { source ->
            source != ContentSource.YOUTUBE
        }

        assertThrows(IOException::class.java) { interceptor.intercept(chain) }
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `available and unrelated hosts proceed unchanged`() {
        val chain = mockk<Interceptor.Chain>()
        val reddit = request("www.reddit.com")
        val unrelated = request("example.com")
        every { chain.request() } returnsMany listOf(reddit, unrelated)
        every { chain.proceed(reddit) } returns response(reddit)
        every { chain.proceed(unrelated) } returns response(unrelated)
        val interceptor = ProviderAvailabilityInterceptor { true }

        assertEquals(200, interceptor.intercept(chain).code)
        assertEquals(200, interceptor.intercept(chain).code)
        verify(exactly = 1) { chain.proceed(reddit) }
        verify(exactly = 1) { chain.proceed(unrelated) }
    }

    @Test
    fun `host lookup uses exact or subdomain suffixes only`() {
        assertEquals(ContentSource.FREESOUND, providerSourceForHost("cdn.freesound.org"))
        assertEquals(ContentSource.PIXABAY, providerSourceForHost("pixabay.com"))
        assertEquals(null, providerSourceForHost("freesound.org.example.com"))
    }

    @Test
    fun `Reddit's picture hosts belong to Reddit and a lookalike belongs to nobody`() {
        assertEquals(ContentSource.REDDIT, providerSourceForHost("i.redd.it"))
        assertEquals(ContentSource.REDDIT, providerSourceForHost("preview.redd.it"))
        assertEquals(ContentSource.REDDIT, providerSourceForHost("www.reddit.com"))
        assertEquals(null, providerSourceForHost("evilredd.it"))
        assertEquals(null, providerSourceForHost("redd.it.example.com"))
    }

    private fun request(host: String): Request = Request.Builder()
        .url("https://$host/media".toHttpUrl())
        .build()

    private fun response(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("".toResponseBody())
        .build()
}
