package com.freevibe.service

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** How long an unfinished download is kept before its bytes are given up on. */
internal const val DOWNLOAD_RESUME_MAX_AGE_MS: Long = 3L * 24L * 60L * 60L * 1000L

/** Most unfinished downloads kept at once. Each is held to its own size cap, so this bounds the directory. */
internal const val DOWNLOAD_RESUME_MAX_ENTRIES = 6

/** What the download manager needs to start an unfinished download again once the process is gone. */
@JsonClass(generateAdapter = true)
data class DownloadRetryRequest(
    /** The history type the download belongs to: `WALLPAPER` or `SOUND`. */
    val kind: String,
    val contentId: String,
    val fileName: String,
    val source: String,
    val provenanceUrl: String,
    /** Which sound folder it was headed for. Blank for a wallpaper. */
    val soundType: String = "",
)

/** One download that started and hasn't been published yet. */
@JsonClass(generateAdapter = true)
data class DownloadResumeRecord(
    /** The download's history ID. It outlives the process, unlike the request that started it. */
    val id: String,
    val url: String,
    val tempPath: String,
    /** Bytes known to be on disk. A file that is longer is cut back to this before anything is added. */
    val bytesReceived: Long,
    /** The whole file's size as the server gave it, or zero when it didn't say. */
    val expectedBytes: Long,
    val etag: String? = null,
    val lastModified: String? = null,
    /** The response's own `Date`, which decides whether [lastModified] is exact enough to resume on. */
    val serverDate: String? = null,
    val updatedAtMs: Long = 0L,
    val retry: DownloadRetryRequest? = null,
)

/** Every byte has arrived, so the file only needs publishing. */
val DownloadResumeRecord.isComplete: Boolean get() = expectedBytes > 0L && bytesReceived == expectedBytes

/**
 * Remembers downloads that stopped partway, so the next attempt can carry on instead of paying
 * for the same bytes again.
 *
 * Each download gets two files named after its ID: the bytes received so far and a small JSON
 * record describing them. Both live in the download staging directory under the cache, so
 * Clear cache and the system's own cache trimming remove them together, and anything missing
 * or unreadable simply counts as nothing saved. That is also why this isn't a Room table: the
 * record has to disappear with its bytes, and it is far too small to justify a migration.
 *
 * The ledger keeps one promise the transport relies on: bytes are never on disk without the
 * record that finds them. A record is written before its file and deleted after it.
 */
@Singleton
class DownloadResumeLedger internal constructor(
    private val directory: File,
    moshi: Moshi,
    private val maxAgeMs: Long = DOWNLOAD_RESUME_MAX_AGE_MS,
    private val maxEntries: Int = DOWNLOAD_RESUME_MAX_ENTRIES,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        moshi: Moshi,
    ) : this(
        directory = File(context.cacheDir, DOWNLOAD_STAGING_DIRECTORY),
        moshi = moshi,
    )

    private val adapter = moshi.adapter(DownloadResumeRecord::class.java)

    /** Where [id]'s bytes are staged. The name comes from the ID alone, so a later process finds the same file. */
    fun partialFile(id: String): File = File(directory, stem(id) + PARTIAL_SUFFIX)

    /** The record saved for [id], or null. One that won't parse or describes something else is dropped with its bytes. */
    @Synchronized
    fun find(id: String): DownloadResumeRecord? {
        val record = read(recordFile(id))
        if (record == null || record.id != id) {
            discard(id)
            return null
        }
        return record
    }

    @Synchronized
    fun save(record: DownloadResumeRecord) {
        directory.mkdirs()
        val target = recordFile(record.id)
        val staged = File(directory, target.name + STAGED_SUFFIX)
        try {
            FileOutputStream(staged).use { output ->
                output.write(adapter.toJson(record).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            moveStagedFileIntoPlace(staged, target)
        } finally {
            staged.delete()
        }
    }

    /** Forgets [id]. Its bytes go first, so a file is never left without the record that finds it. */
    @Synchronized
    fun discard(id: String) {
        partialFile(id).delete()
        recordFile(id).delete()
    }

    /** Every unfinished download, newest first. */
    @Synchronized
    fun all(): List<DownloadResumeRecord> = stagedFiles()
        .filter { it.name.endsWith(RECORD_SUFFIX) }
        .mapNotNull(::read)
        .sortedByDescending { it.updatedAtMs }

    /**
     * Removes what is no longer worth keeping: records past [maxAgeMs] or beyond [maxEntries]
     * (oldest first), records whose bytes are gone, bytes with no record, and staging files a
     * killed process left behind. A download in [keep] is being written right now and is never
     * touched.
     *
     * @return how many downloads and stray files were removed.
     */
    @Synchronized
    fun sweep(nowMs: Long, keep: Set<String> = emptySet()): Int {
        var removed = 0
        val idle = mutableListOf<DownloadResumeRecord>()
        var busy = 0
        stagedFiles().filter { it.name.endsWith(RECORD_SUFFIX) }.forEach { file ->
            val record = read(file)
            when {
                record == null -> {
                    File(directory, file.name.removeSuffix(RECORD_SUFFIX) + PARTIAL_SUFFIX).delete()
                    file.delete()
                    removed++
                }
                record.id in keep -> busy++
                nowMs - record.updatedAtMs >= maxAgeMs || !partialFile(record.id).isFile -> {
                    discard(record.id)
                    removed++
                }
                else -> idle.add(record)
            }
        }
        idle.sortedByDescending { it.updatedAtMs }
            .drop((maxEntries - busy).coerceAtLeast(0))
            .forEach { record ->
                discard(record.id)
                removed++
            }
        stagedFiles().forEach { file ->
            val stray = when {
                file.name.endsWith(RECORD_SUFFIX) -> false
                file.name.endsWith(PARTIAL_SUFFIX) ->
                    !File(directory, file.name.removeSuffix(PARTIAL_SUFFIX) + RECORD_SUFFIX).isFile
                else -> nowMs - file.lastModified() >= maxAgeMs
            }
            if (stray && file.delete()) removed++
        }
        return removed
    }

    private fun stagedFiles(): List<File> = directory.listFiles()?.filter(File::isFile).orEmpty()

    private fun recordFile(id: String): File = File(directory, stem(id) + RECORD_SUFFIX)

    /**
     * A record is only believed when it sits under its own ID's name and points at its own
     * bytes, so nothing a stored path says can send a read or a delete anywhere else.
     */
    private fun read(file: File): DownloadResumeRecord? {
        val text = runCatching { file.takeIf(File::isFile)?.readText() }.getOrNull() ?: return null
        val record = runCatching { adapter.fromJson(text) }.getOrNull() ?: return null
        return record.takeIf {
            recordFile(it.id) == file && it.tempPath == partialFile(it.id).absolutePath
        }
    }

    private fun stem(id: String): String =
        FILE_PREFIX + sha256InputStream(id.toByteArray(Charsets.UTF_8).inputStream()).take(STEM_HEX_LENGTH)

    private companion object {
        const val FILE_PREFIX = "aura_resume_"
        const val PARTIAL_SUFFIX = ".part"
        const val RECORD_SUFFIX = ".json"
        const val STAGED_SUFFIX = ".tmp"
        const val STEM_HEX_LENGTH = 32
    }
}

/** The cache directory downloads are staged in before they're published. */
internal const val DOWNLOAD_STAGING_DIRECTORY = "downloads"
