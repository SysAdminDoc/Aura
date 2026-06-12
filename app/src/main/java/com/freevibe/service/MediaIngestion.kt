package com.freevibe.service

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class MediaIngestionLimitExceeded(
    message: String,
) : IOException(message)

internal fun advertisedLengthExceeds(
    contentLength: Long,
    maxBytes: Long,
): Boolean = contentLength in 1..Long.MAX_VALUE && contentLength > maxBytes

internal fun copyStreamCapped(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long,
    bufferSize: Int = DEFAULT_MEDIA_INGESTION_BUFFER_BYTES,
): Long {
    require(maxBytes > 0) { "maxBytes must be positive" }
    val buffer = ByteArray(bufferSize)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        copied += read
        if (copied > maxBytes) {
            throw MediaIngestionLimitExceeded("Media exceeds size limit ($maxBytes bytes)")
        }
        output.write(buffer, 0, read)
    }
    return copied
}

internal fun readStreamCapped(
    input: InputStream,
    maxBytes: Long,
): ByteArray {
    val output = ByteArrayOutputStream()
    copyStreamCapped(input, output, maxBytes)
    return output.toByteArray()
}

private const val DEFAULT_MEDIA_INGESTION_BUFFER_BYTES = 8 * 1024
