package com.freevibe.service

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTransferContractTest {

    @Test
    fun `count limit accepts zero limit minus one and limit`() {
        val limit = LibraryTransferContract.MAX_FAVORITES

        requireWithinTransferLimit("favorites", 0, limit)
        requireWithinTransferLimit("favorites", limit - 1, limit)
        requireWithinTransferLimit("favorites", limit, limit)
    }

    @Test
    fun `count limit refuses limit plus one`() {
        val limit = LibraryTransferContract.MAX_FAVORITES

        val failure = runCatching {
            requireWithinTransferLimit("favorites", limit + 1, limit)
        }.exceptionOrNull()

        assertTrue(failure is LibraryTransferLimitExceededException)
        assertTrue(failure?.message.orEmpty().contains((limit + 1).toString()))
        assertTrue(failure?.message.orEmpty().contains(limit.toString()))
    }

    @Test
    fun `document destination opens only after the complete payload is staged`() {
        val directory = temporaryDirectory()
        val destination = ByteArrayOutputStream()
        val payload = "complete export".toByteArray()

        stageAndPublishBytes(
            stagingDirectory = directory,
            bytes = payload,
            openDestination = {
                val staged = directory.listFiles().orEmpty().single()
                assertArrayEquals(payload, staged.readBytes())
                destination
            },
            cleanupDestination = { error("cleanup must not run") },
        )

        assertArrayEquals(payload, destination.toByteArray())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `partial destination failure runs cleanup and removes staging file`() {
        val directory = temporaryDirectory()
        var cleaned = false
        val destination = object : OutputStream() {
            private var written = 0

            override fun write(value: Int) {
                if (written++ >= 3) throw IllegalStateException("destination full")
            }
        }

        val failure = runCatching {
            stageAndPublishBytes(
                stagingDirectory = directory,
                bytes = "not partial".toByteArray(),
                openDestination = { destination },
                cleanupDestination = { cleaned = true },
            )
        }.exceptionOrNull()

        assertEquals("destination full", failure?.message)
        assertTrue(cleaned)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `cancellation is rethrown after destination and staging cleanup`() {
        val directory = temporaryDirectory()
        var cleaned = false
        val cancelled = CancellationException("cancelled")

        val failure = runCatching {
            stageAndPublishBytes(
                stagingDirectory = directory,
                bytes = "payload".toByteArray(),
                openDestination = {
                    object : OutputStream() {
                        override fun write(value: Int) = throw cancelled
                    }
                },
                cleanupDestination = { cleaned = true },
            )
        }.exceptionOrNull()

        assertTrue(failure === cancelled)
        assertTrue(cleaned)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `local publication replaces the destination with complete bytes`() {
        val directory = temporaryDirectory()
        val destination = File(directory, "collection.json").apply { writeText("old") }
        val payload = "new complete file".toByteArray()

        publishAtomicLocalFile(destination, payload)

        assertArrayEquals(payload, destination.readBytes())
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    @Test
    fun `a staged cache file replaces the destination and leaves no part file`() {
        val directory = temporaryDirectory()
        val destination = File(directory, "clip.mp3").apply { writeText("stale") }
        val staged = File(directory, ".clip.mp3.1.part").apply { writeText("complete audio") }

        moveStagedFileIntoPlace(staged, destination)

        assertEquals("complete audio", destination.readText())
        assertFalse(staged.exists())
    }

    private fun temporaryDirectory(): File =
        kotlin.io.path.createTempDirectory("aura-transfer-test-").toFile().also {
            it.deleteOnExit()
        }
}
