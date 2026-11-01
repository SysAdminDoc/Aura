package com.freevibe.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.freevibe.data.model.Wallpaper
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale

private val LOCAL_MEDIA_SCHEMES = setOf("rawresource", "android.resource", "content", "file")

/** A favorite's offline copy is a plain absolute path. Every other local source names its scheme. */
internal fun isLocalMediaLocator(locator: String): Boolean =
    locator.startsWith("/") ||
        locator.substringBefore(':', missingDelimiterValue = "")
            .lowercase(Locale.ROOT) in LOCAL_MEDIA_SCHEMES

internal fun localMediaUri(locator: String): Uri =
    if (locator.startsWith("/")) Uri.Builder().scheme("file").path(locator).build() else Uri.parse(locator)

/**
 * Where to read a wallpaper right now. A favorite's offline copy goes away when it's removed from
 * favorites or the cache is cleared, while an open screen or a saved selection still holds its path.
 */
internal fun Wallpaper.currentFullUrl(): String =
    if (remoteFullUrl.isNotBlank() && !File(fullUrl).exists()) remoteFullUrl else fullUrl

/** Opens a local media locator for reading. The caller closes the stream. */
internal fun openLocalMediaLocator(context: Context, locator: String): InputStream =
    openLocalMedia(context, localMediaUri(locator))

/** Decodes an image held at a local locator, for the flows that otherwise fetch it from the web. */
internal fun decodeLocalMediaImage(
    context: Context,
    locator: String,
    flow: MediaIngestionImageFlow,
    maxBytes: Long,
    maxLongEdge: Int,
): Bitmap {
    val uri = localMediaUri(locator)
    val bytes = openLocalMedia(context, uri).use { readStreamCapped(it, maxBytes) }
    return decodeImageBytesForFlow(
        bytes = bytes,
        flow = flow,
        extension = uri.lastPathSegment?.substringAfterLast('.', missingDelimiterValue = ""),
        maxLongEdge = maxLongEdge,
    )
}

/** Copies an app-accessible local media locator into a bounded staging file. */
internal fun stageLocalMediaLocator(
    context: Context,
    locator: String,
    tempDirectoryName: String,
    prefix: String,
    maxBytes: Long,
): File {
    val uri = localMediaUri(locator)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    require(scheme in LOCAL_MEDIA_SCHEMES) { "Unsupported local media locator" }

    val advertisedLength = localMediaLength(context, uri)
    if (advertisedLengthExceeds(advertisedLength, maxBytes)) {
        throw MediaIngestionLimitExceeded("Media exceeds size limit ($maxBytes bytes)")
    }

    val tempDirectory = File(context.cacheDir, tempDirectoryName).apply { mkdirs() }
    val tempFile = File.createTempFile(prefix, ".tmp", tempDirectory)
    try {
        openLocalMedia(context, uri).use { input ->
            tempFile.outputStream().use { output ->
                if (copyStreamCapped(input, output, maxBytes) <= 0L) {
                    throw IllegalStateException("Local media is empty")
                }
            }
        }
        return tempFile
    } catch (error: Exception) {
        tempFile.delete()
        throw error
    }
}

private fun openLocalMedia(context: Context, uri: Uri): InputStream = when (uri.scheme?.lowercase(Locale.ROOT)) {
    "rawresource" -> {
        val resourceId = uri.schemeSpecificPart.trim('/').toIntOrNull()
            ?: throw IllegalArgumentException("Invalid raw resource locator")
        context.resources.openRawResource(resourceId)
    }
    "file" -> FileInputStream(
        File(uri.path ?: throw IllegalArgumentException("Invalid file locator")),
    )
    "android.resource", "content" -> context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Local media could not be opened")
    else -> throw IllegalArgumentException("Unsupported local media locator")
}

private fun localMediaLength(context: Context, uri: Uri): Long = runCatching {
    when (uri.scheme?.lowercase(Locale.ROOT)) {
        "rawresource" -> {
            val resourceId = uri.schemeSpecificPart.trim('/').toInt()
            context.resources.openRawResourceFd(resourceId).use { it.length }
        }
        "file" -> File(uri.path.orEmpty()).length()
        "android.resource", "content" ->
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L
        else -> 0L
    }
}.getOrDefault(0L)
