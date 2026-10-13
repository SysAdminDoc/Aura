package com.freevibe.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Fetches one file over HTTP into the ledger's staging file, and carries on from an earlier
 * attempt when the server can prove the file hasn't changed since.
 *
 * A second attempt asks for the missing bytes only when the first response left a strong
 * validator behind, and sends it as `If-Range` so the server itself decides between "here is
 * the rest" (`206`) and "it changed, here is all of it" (`200`). Anything short of a `206` that
 * starts exactly where the file ends, for the same total size, starts again from byte zero.
 *
 * The transport knows nothing about MediaStore or the download history. It hands back a
 * complete file and leaves its record in the ledger. The caller checks and publishes the bytes,
 * then discards the record. Callers run one fetch per ID at a time.
 */
internal class ResumableDownloadTransport(
    private val okHttpClient: OkHttpClient,
    private val ledger: DownloadResumeLedger,
    private val now: () -> Long = System::currentTimeMillis,
    private val checkpointBytes: Long = CHECKPOINT_BYTES,
) {
    /** Downloads being written right now, which a sweep must leave alone. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns [id]'s complete file, downloading only what is still missing.
     *
     * [maxBytes] caps the whole file: bytes already on disk count toward it. [retry] is stored
     * with the record for the caller's own use after a restart.
     */
    suspend fun fetch(
        id: String,
        url: String,
        maxBytes: Long,
        retry: DownloadRetryRequest? = null,
        onProgress: (receivedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        require(maxBytes > 0) { "maxBytes must be positive" }
        inFlight += id
        try {
            ledger.sweep(now(), inFlight)
            val partial = ledger.partialFile(id)
            val saved = usableRecord(id, url, partial, maxBytes)?.let { it.copy(retry = retry ?: it.retry) }
            val validator = saved?.let { strongIfRangeValidator(it.etag, it.lastModified, it.serverDate) }
            when {
                saved == null -> fetchFromStart(id, url, partial, maxBytes, retry, onProgress)
                saved.isComplete -> {
                    // Every byte arrived last time and only publishing failed. They're the same
                    // bytes that attempt would have saved, so nothing is fetched again.
                    truncate(partial, saved.expectedBytes)
                    onProgress(saved.expectedBytes, saved.expectedBytes)
                    partial
                }
                validator == null -> {
                    ledger.discard(id)
                    fetchFromStart(id, url, partial, maxBytes, retry, onProgress)
                }
                else -> fetchRemainder(saved, validator, partial, maxBytes, onProgress)
            }
        } catch (e: Exception) {
            // Bytes are kept only when a later attempt could still use them. That goes for
            // cancellation too, so a stopped download never leaves a file nothing will resume.
            val kept = ledger.find(id)
            if (e is UnusableDownloadException || kept == null || !kept.canBeContinued()) ledger.discard(id)
            throw e
        } finally {
            inFlight -= id
        }
    }

    /** Runs the ledger's cleanup without disturbing a download this transport is writing. */
    fun sweep(): Int = ledger.sweep(now(), inFlight)

    /** The record for [id] if its bytes can still be built on. Anything else is discarded. */
    private fun usableRecord(id: String, url: String, partial: File, maxBytes: Long): DownloadResumeRecord? {
        val record = ledger.find(id) ?: return null
        val usable = record.url == url &&
            record.expectedBytes in 1..maxBytes &&
            record.bytesReceived in 1..record.expectedBytes &&
            partial.isFile &&
            partial.length() >= record.bytesReceived
        if (!usable) ledger.discard(id)
        return record.takeIf { usable }
    }

    private suspend fun fetchFromStart(
        id: String,
        url: String,
        partial: File,
        maxBytes: Long,
        retry: DownloadRetryRequest?,
        onProgress: (Long, Long) -> Unit,
    ): File {
        val request = Request.Builder().url(url).build()
        return okHttpClient.newCall(request).execute().use { response ->
            receiveWholeFile(response, id, url, partial, maxBytes, retry, onProgress)
        }
    }

    private suspend fun fetchRemainder(
        saved: DownloadResumeRecord,
        validator: String,
        partial: File,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ): File {
        val request = Request.Builder()
            .url(saved.url)
            .header("Range", "bytes=${saved.bytesReceived}-")
            .header("If-Range", validator)
            // An offset only means something in the file's own bytes, never in a compressed copy.
            .header("Accept-Encoding", "identity")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            when {
                response.code == HTTP_PARTIAL_CONTENT && continues(response, saved) ->
                    return receive(response, saved, partial, maxBytes, onProgress)
                // The validator no longer matches, or the server doesn't do ranges: this is the
                // whole file again, so it replaces what was kept.
                response.code == HTTP_OK ->
                    return receiveWholeFile(response, saved.id, saved.url, partial, maxBytes, saved.retry, onProgress)
                response.code in RESTART_STATUS_CODES -> Unit
                else -> {
                    if (response.code in GONE_STATUS_CODES) ledger.discard(saved.id)
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
            }
        }
        // A range the server won't serve, a failed precondition or a 206 for some other span:
        // what was kept can't be trusted to line up, so it goes and one plain request follows.
        ledger.discard(saved.id)
        return fetchFromStart(saved.id, saved.url, partial, maxBytes, saved.retry, onProgress)
    }

    private suspend fun receiveWholeFile(
        response: Response,
        id: String,
        url: String,
        partial: File,
        maxBytes: Long,
        retry: DownloadRetryRequest?,
        onProgress: (Long, Long) -> Unit,
    ): File {
        if (!response.isSuccessful) {
            throw IllegalStateException("Download failed: HTTP ${response.code}")
        }
        val expected = (response.body?.contentLength() ?: -1L).coerceAtLeast(0L)
        // Reject an oversized download up front when the server advertises a size.
        if (expected > maxBytes) {
            throw UnusableDownloadException("Download exceeds size limit ($expected > $maxBytes)")
        }
        // A length that isn't known, or bytes that were re-encoded on the way, can't be lined
        // up with a byte range later. Validators are only kept when they could be used.
        val rangeable = expected > 0L &&
            !isEncoded(response) &&
            !response.header("Accept-Ranges").orEmpty().trim().equals("none", ignoreCase = true)
        val record = DownloadResumeRecord(
            id = id,
            url = url,
            tempPath = partial.absolutePath,
            bytesReceived = 0L,
            expectedBytes = expected,
            etag = response.header("ETag")?.trim()?.takeIf { rangeable && it.isNotEmpty() },
            lastModified = response.header("Last-Modified")?.trim()?.takeIf { rangeable && it.isNotEmpty() },
            serverDate = response.header("Date")?.trim()?.takeIf { rangeable && it.isNotEmpty() },
            updatedAtMs = now(),
            retry = retry,
        )
        return receive(response, record, partial, maxBytes, onProgress)
    }

    /**
     * Writes [response]'s body after the [DownloadResumeRecord.bytesReceived] bytes [record]
     * already counts, cutting the file back to that point first.
     */
    private suspend fun receive(
        response: Response,
        record: DownloadResumeRecord,
        partial: File,
        maxBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ): File {
        val body = response.body ?: throw UnusableDownloadException("Empty response body")
        val startAt = record.bytesReceived
        val expected = record.expectedBytes
        var received = startAt
        var durable = startAt
        // The record goes down before the bytes do, so a file on disk always has one describing it.
        ledger.save(record)
        try {
            RandomAccessFile(partial, "rw").use { file ->
                file.setLength(startAt)
                file.seek(startAt)
                try {
                    body.byteStream().use { input ->
                        val buffer = ByteArray(BUFFER_BYTES)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            // The cap is on the whole file: what was kept counts toward it.
                            if (received + read > maxBytes) {
                                throw UnusableDownloadException("Download exceeds size limit ($maxBytes bytes)")
                            }
                            file.write(buffer, 0, read)
                            received += read
                            if (received - durable >= checkpointBytes) {
                                // Synced before it's counted: a count never runs ahead of the disk.
                                file.fd.sync()
                                durable = received
                                ledger.save(record.copy(bytesReceived = durable, updatedAtMs = now()))
                            }
                            onProgress(received, expected)
                        }
                    }
                    file.fd.sync()
                    durable = received
                } catch (e: Exception) {
                    // A transfer that stops early keeps what it got, as long as it reached the disk.
                    if (runCatching { file.fd.sync() }.isSuccess) durable = received
                    throw e
                }
            }
        } finally {
            ledger.save(record.copy(bytesReceived = durable, updatedAtMs = now()))
        }
        when {
            received <= 0L -> throw UnusableDownloadException("Empty response body")
            expected > 0L && received > expected ->
                throw UnusableDownloadException("Download is longer than the server said it would be")
            expected > 0L && received < expected ->
                throw IOException("Download ended before the whole file arrived")
        }
        if (expected <= 0L) {
            // A server that never gave a length has now shown it, which is what marks the file complete.
            ledger.save(record.copy(bytesReceived = received, expectedBytes = received, updatedAtMs = now()))
        }
        return partial
    }

    /** True when a `206` picks up exactly where [saved] left off, for the same file. */
    private fun continues(response: Response, saved: DownloadResumeRecord): Boolean {
        val range = parseContentRange(response.header("Content-Range")) ?: return false
        if (range.start != saved.bytesReceived || range.total != saved.expectedBytes || range.end != range.total - 1) {
            return false
        }
        // If-Range already made the server compare. A validator it sends back still has to agree.
        val etag = response.header("ETag")?.trim()
        if (saved.etag != null && etag != null && etag != saved.etag) return false
        val lastModified = response.header("Last-Modified")?.trim()
        if (saved.lastModified != null && lastModified != null && lastModified != saved.lastModified) return false
        if (isEncoded(response)) return false
        val length = response.body?.contentLength() ?: -1L
        return length < 0L || length == range.total - range.start
    }

    private fun isEncoded(response: Response): Boolean =
        response.header("Content-Encoding")?.trim().orEmpty().let { it.isNotEmpty() && !it.equals("identity", ignoreCase = true) }

    private fun truncate(file: File, length: Long) {
        if (file.length() > length) RandomAccessFile(file, "rw").use { it.setLength(length) }
    }
}

/** Bytes that must not be kept for a later attempt: too many, none at all, or not what was promised. */
internal class UnusableDownloadException(message: String) : IllegalStateException(message)

/** A record a later attempt can build on: a resumable part, or a whole file waiting to be published. */
internal fun DownloadResumeRecord.canBeContinued(): Boolean =
    isComplete ||
        (bytesReceived > 0L && expectedBytes > 0L && strongIfRangeValidator(etag, lastModified, serverDate) != null)

internal data class ContentRange(val start: Long, val end: Long, val total: Long)

/** Reads `bytes first-last/total`. An unknown total (`*`) or an unsatisfied range gives null. */
internal fun parseContentRange(value: String?): ContentRange? {
    val match = CONTENT_RANGE_REGEX.matchEntire(value?.trim().orEmpty()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val total = match.groupValues[3].toLongOrNull() ?: return null
    return ContentRange(start, end, total).takeIf { start <= end && end < total }
}

/**
 * The validator to send as `If-Range`, or null when the first response left nothing strong
 * enough to resume on (RFC 9110, sections 8.8.1, 8.8.2.2 and 13.1.5).
 *
 * A strong entity tag is used as is. A weak one (`W/"..."`) only promises an equivalent file,
 * not the same bytes, so it rules resuming out, and so does a tag that isn't well formed. A
 * date is only allowed when the server sent no entity tag at all, and only counts as strong
 * when the response's own `Date` is at least a second later: two versions saved within the
 * same second would otherwise share one `Last-Modified`.
 */
internal fun strongIfRangeValidator(etag: String?, lastModified: String?, serverDate: String?): String? {
    val tag = etag?.trim().orEmpty()
    if (tag.isNotEmpty()) return tag.takeIf(::isStrongEntityTag)
    val modified = lastModified?.trim().orEmpty()
    val modifiedAt = httpDateMillis(modified) ?: return null
    val servedAt = httpDateMillis(serverDate?.trim().orEmpty()) ?: return null
    return modified.takeIf { servedAt - modifiedAt >= STRONG_DATE_GAP_MS }
}

private fun isStrongEntityTag(tag: String): Boolean =
    tag.length >= 2 && tag.startsWith('"') && tag.endsWith('"') && tag.indexOf('"', 1) == tag.lastIndex

/** Only the fixed-length format servers are required to send. The two obsolete ones count as unreadable. */
private fun httpDateMillis(value: String): Long? = runCatching {
    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
}.getOrNull()

private val CONTENT_RANGE_REGEX = Regex("""bytes\s+(\d+)-(\d+)/(\d+)""", RegexOption.IGNORE_CASE)
private const val STRONG_DATE_GAP_MS = 1_000L
private const val BUFFER_BYTES = 8192

/** How much arrives between durable counts. A killed process downloads at most this much twice. */
private const val CHECKPOINT_BYTES = 1024L * 1024L
private const val HTTP_OK = 200
private const val HTTP_PARTIAL_CONTENT = 206

/** A `206` that doesn't line up, a failed precondition and a range the server can't satisfy. */
private val RESTART_STATUS_CODES = setOf(206, 412, 416)

/** The file isn't at this address anymore, so its kept bytes are no use. */
private val GONE_STATUS_CODES = setOf(404, 410)
