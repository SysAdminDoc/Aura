package com.freevibe.service

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Stands in for a server: each request gets the next scripted answer, and every request is kept
 * so a test can read the headers that were sent. A request nothing was scripted for fails the test.
 */
internal class ScriptedHttp : Interceptor {
    val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
    private val script = ConcurrentLinkedQueue<(Request) -> Response>()

    fun enqueue(answer: (Request) -> Response) {
        script += answer
    }

    fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(this).build()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        requests += request
        val answer = script.poll()
            ?: throw AssertionError("Nothing scripted for request ${requests.size}: Range=${request.header("Range")}")
        return answer(request)
    }
}

/**
 * A canned response. [declaredLength] is what the server claims the body holds, so a body
 * shorter than it plays a connection that dropped, and -1 a server that never said.
 */
internal fun Request.answer(
    code: Int,
    body: ByteArray = ByteArray(0),
    extraHeaders: Map<String, String> = emptyMap(),
    declaredLength: Long = body.size.toLong(),
): Response = answer(code, Buffer().write(body).asResponseBody(null, declaredLength), extraHeaders)

internal fun Request.answer(
    code: Int,
    body: ResponseBody,
    extraHeaders: Map<String, String> = emptyMap(),
): Response {
    val builder = Response.Builder()
        .request(this)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message("scripted")
    extraHeaders.forEach { (name, value) -> builder.header(name, value) }
    return builder.body(body).build()
}

/**
 * A whole body handed over [chunkBytes] at a time, which calls [onSecondRead] partway through,
 * after the first chunk is out.
 */
internal fun bodyInterruptedBy(
    bytes: ByteArray,
    chunkBytes: Long = SLOW_READ_BYTES,
    onSecondRead: () -> Unit,
): ResponseBody {
    val remaining = Buffer().write(bytes)
    var reads = 0
    val source = object : Source {
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (reads++ == 1) onSecondRead()
            return remaining.read(sink, minOf(byteCount, chunkBytes))
        }

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }
    return source.buffer().asResponseBody(null, bytes.size.toLong())
}

private const val SLOW_READ_BYTES = 4096L
