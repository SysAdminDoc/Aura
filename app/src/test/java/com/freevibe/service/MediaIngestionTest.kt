package com.freevibe.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MediaIngestionTest {

    @Test
    fun `advertisedLengthExceeds only rejects known oversized lengths`() {
        assertFalse(advertisedLengthExceeds(-1, 10))
        assertFalse(advertisedLengthExceeds(0, 10))
        assertFalse(advertisedLengthExceeds(10, 10))
        assertTrue(advertisedLengthExceeds(11, 10))
    }

    @Test
    fun `copyStreamCapped copies bytes within limit`() {
        val output = ByteArrayOutputStream()

        val copied = copyStreamCapped(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, maxBytes = 3)

        assertEquals(3, copied)
        assertArrayEquals(byteArrayOf(1, 2, 3), output.toByteArray())
    }

    @Test
    fun `copyStreamCapped rejects chunked oversized input`() {
        val output = ByteArrayOutputStream()

        assertThrows(MediaIngestionLimitExceeded::class.java) {
            copyStreamCapped(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), output, maxBytes = 3)
        }
    }

    @Test
    fun `readStreamCapped returns exact bytes`() {
        assertArrayEquals(
            byteArrayOf(9, 8, 7),
            readStreamCapped(ByteArrayInputStream(byteArrayOf(9, 8, 7)), maxBytes = 3),
        )
    }
}
