package com.freevibe.service

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Fully stage bytes before opening an external destination. A failed publish runs
 * destination cleanup and always removes the private staging file.
 */
internal fun stageAndPublishBytes(
    stagingDirectory: File,
    bytes: ByteArray,
    openDestination: () -> OutputStream?,
    cleanupDestination: () -> Unit,
) {
    check(stagingDirectory.exists() || stagingDirectory.mkdirs()) {
        "Could not create the export staging directory."
    }
    val staged = File.createTempFile("aura-export-", ".tmp", stagingDirectory)
    var destinationOpened = false
    try {
        FileOutputStream(staged).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(staged.length() == bytes.size.toLong()) {
            "The staged export size does not match the prepared payload."
        }
        val destination = openDestination()
            ?: throw IllegalStateException("Failed to open output stream")
        destinationOpened = true
        destination.use { output ->
            staged.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var publishedBytes = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    publishedBytes += read
                }
                check(publishedBytes == staged.length()) {
                    "The published export size does not match the staged payload."
                }
            }
            output.flush()
            if (output is FileOutputStream) output.fd.sync()
        }
    } catch (error: Throwable) {
        if (destinationOpened) runCatching(cleanupDestination)
        throw error
    } finally {
        staged.delete()
    }
}

internal fun publishStagedDocument(context: Context, outputUri: Uri, bytes: ByteArray) {
    stageAndPublishBytes(
        stagingDirectory = File(context.cacheDir, "export-staging"),
        bytes = bytes,
        openDestination = {
            runCatching { context.contentResolver.openOutputStream(outputUri, "rwt") }
                .getOrNull()
                ?: context.contentResolver.openOutputStream(outputUri)
        },
        cleanupDestination = {
            val deleted = runCatching {
                DocumentsContract.isDocumentUri(context, outputUri) &&
                    DocumentsContract.deleteDocument(context.contentResolver, outputUri)
            }.getOrDefault(false)
            if (!deleted) {
                runCatching { context.contentResolver.openOutputStream(outputUri, "rwt")?.close() }
            }
        },
    )
}

/** Publish an internal share file by same-directory atomic replacement. */
internal fun publishAtomicLocalFile(destination: File, bytes: ByteArray) {
    val directory = destination.parentFile
        ?: throw IllegalArgumentException("Export destination has no parent directory.")
    check(directory.exists() || directory.mkdirs()) {
        "Could not create the export directory."
    }
    val staged = File.createTempFile(".${destination.name}.", ".tmp", directory)
    try {
        FileOutputStream(staged).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        moveStagedFileIntoPlace(staged, destination)
    } finally {
        staged.delete()
    }
}

/**
 * Replace [destination] with a fully written [staged] file in one rename, so a reader never sees
 * a half-copied file. Callers stage in the destination's directory, where the rename is atomic.
 */
internal fun moveStagedFileIntoPlace(staged: File, destination: File) {
    try {
        Files.move(
            staged.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            staged.toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}
