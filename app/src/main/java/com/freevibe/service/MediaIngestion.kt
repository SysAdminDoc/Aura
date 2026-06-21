package com.freevibe.service

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

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

internal enum class MediaFamily {
    IMAGE,
    AUDIO,
}

internal data class SniffedMediaType(
    val family: MediaFamily,
    val mimeType: String,
    val extension: String,
)

internal fun sniffMediaType(header: ByteArray): SniffedMediaType? {
    if (header.startsWith(0xFF, 0xD8, 0xFF)) {
        return SniffedMediaType(MediaFamily.IMAGE, "image/jpeg", "jpg")
    }
    if (header.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
        return SniffedMediaType(MediaFamily.IMAGE, "image/png", "png")
    }
    if (header.asciiAt(0, "GIF87a") || header.asciiAt(0, "GIF89a")) {
        return SniffedMediaType(MediaFamily.IMAGE, "image/gif", "gif")
    }
    if (header.asciiAt(0, "RIFF") && header.asciiAt(8, "WEBP")) {
        return SniffedMediaType(MediaFamily.IMAGE, "image/webp", "webp")
    }
    if (header.hasAacAdtsSync()) {
        return SniffedMediaType(MediaFamily.AUDIO, "audio/aac", "aac")
    }
    if (header.asciiAt(0, "ID3") || header.hasMp3FrameSync()) {
        return SniffedMediaType(MediaFamily.AUDIO, "audio/mpeg", "mp3")
    }
    if (header.asciiAt(0, "OggS")) {
        return SniffedMediaType(MediaFamily.AUDIO, "audio/ogg", "ogg")
    }
    if (header.asciiAt(0, "RIFF") && header.asciiAt(8, "WAVE")) {
        return SniffedMediaType(MediaFamily.AUDIO, "audio/wav", "wav")
    }
    if (header.asciiAt(0, "fLaC")) {
        return SniffedMediaType(MediaFamily.AUDIO, "audio/flac", "flac")
    }
    if (header.asciiAt(4, "ftyp")) {
        return sniffFtypBrand(header)
    }
    return null
}

private fun sniffFtypBrand(header: ByteArray): SniffedMediaType {
    val brand = if (header.size >= 12) {
        String(header, 8, 4, Charsets.US_ASCII).trim().lowercase(Locale.ROOT)
    } else {
        ""
    }
    return when {
        brand.startsWith("heic") || brand.startsWith("heix") || brand == "mif1" ->
            SniffedMediaType(MediaFamily.IMAGE, "image/heif", "heic")
        brand.startsWith("avif") || brand.startsWith("avis") ->
            SniffedMediaType(MediaFamily.IMAGE, "image/avif", "avif")
        else ->
            SniffedMediaType(MediaFamily.AUDIO, "audio/mp4", "m4a")
    }
}

internal fun sniffMediaFile(file: File): SniffedMediaType? {
    val header = ByteArray(MEDIA_SNIFF_BYTES)
    val read = FileInputStream(file).use { it.read(header) }
    return if (read > 0) sniffMediaType(header.copyOf(read)) else null
}

internal fun requireSniffedMediaFile(
    file: File,
    expectedFamily: MediaFamily,
    label: String,
): SniffedMediaType {
    val sniffed = sniffMediaFile(file)
        ?: throw IOException("$label content type could not be verified")
    if (sniffed.family != expectedFamily) {
        throw IOException("$label content type mismatch: expected ${expectedFamily.name.lowercase(Locale.ROOT)}")
    }
    return sniffed
}

internal fun normalizeMediaFileName(
    fileName: String,
    sniffed: SniffedMediaType,
): String {
    val trimmed = fileName.trim().ifBlank { "aura_media" }
    val base = trimmed.substringBeforeLast('.', trimmed).ifBlank { "aura_media" }
    return "$base.${sniffed.extension}"
}

private const val DEFAULT_MEDIA_INGESTION_BUFFER_BYTES = 8 * 1024
private const val MEDIA_SNIFF_BYTES = 64

private fun ByteArray.startsWith(vararg expected: Int): Boolean =
    size >= expected.size && expected.indices.all { index -> this[index].toInt() and 0xFF == expected[index] }

private fun ByteArray.asciiAt(offset: Int, value: String): Boolean {
    if (offset < 0 || size < offset + value.length) return false
    return value.indices.all { index -> this[offset + index].toInt() == value[index].code }
}

private fun ByteArray.hasMp3FrameSync(): Boolean {
    if (size < 2) return false
    val first = this[0].toInt() and 0xFF
    val second = this[1].toInt() and 0xFF
    val layerBits = second and 0x06
    return first == 0xFF && second and 0xE0 == 0xE0 && layerBits != 0
}

private fun ByteArray.hasAacAdtsSync(): Boolean {
    if (size < 2) return false
    val first = this[0].toInt() and 0xFF
    val second = this[1].toInt() and 0xFF
    return first == 0xFF && second and 0xF6 == 0xF0
}
