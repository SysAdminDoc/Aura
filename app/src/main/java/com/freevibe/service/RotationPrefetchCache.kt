package com.freevibe.service

import android.content.Context
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A wallpaper downloaded ahead of the rotation that will apply it. */
data class PrefetchedWallpaper(val wallpaper: Wallpaper, val file: File)

/** What happened the last time rotation tried to save the next wallpaper ahead of time. */
enum class RotationPrefetchOutcome {
    STORED,

    /** The connection is metered. The next rotation fetches the usual way instead. */
    DEFERRED_METERED,

    /** Data Saver is on, so nothing is downloaded in the background. */
    DEFERRED_DATA_SAVER,

    /** The phone had no connection when the rotation ran. */
    DEFERRED_OFFLINE,

    /** The download failed or came back empty. */
    FAILED,

    /** The file alone is bigger than the whole cache is allowed to be. */
    OVER_BUDGET,
}

data class RotationPrefetchDiagnostics(
    /** Rotations that applied a saved wallpaper without touching the network. */
    val hits: Int = 0,
    /** Rotations that found nothing usable saved and fetched the usual way. */
    val misses: Int = 0,
    val savedCount: Int = 0,
    val savedBytes: Long = 0,
    val lastOutcome: RotationPrefetchOutcome? = null,
)

@JsonClass(generateAdapter = true)
internal data class RotationPrefetchRecord(
    val slot: String,
    val fileName: String,
    val bytes: Long,
    val savedAt: Long,
    val id: String,
    val source: String,
    val thumbnailUrl: String,
    val fullUrl: String,
    val width: Int,
    val height: Int,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val fileSize: Long = 0,
    val fileType: String = "",
    val sourcePageUrl: String = "",
    val license: String = "",
    val uploaderName: String = "",
    val communityUploaderId: String = "",
    val isAiGenerated: Boolean? = null,
    val contentHash: String = "",
)

@JsonClass(generateAdapter = true)
internal data class RotationPrefetchIndex(
    val records: List<RotationPrefetchRecord> = emptyList(),
    val hits: Int = 0,
    val misses: Int = 0,
    val lastOutcome: String = "",
)

/**
 * Holds the next wallpaper for each rotation setup so a rotation that fires with no usable network
 * still has something to apply. It is a cache in the plain sense: anything missing, stale or
 * unreadable is treated as nothing saved, and the rotation falls back to fetching.
 *
 * A slot names one setup (source plus screens). Each slot keeps one wallpaper. The whole cache is
 * held to [maxEntries] files and [maxBytes] bytes, oldest out first. A file the index doesn't name
 * is swept when a download ends and once when the folder is first read, so a run cancelled or
 * killed in the middle of a download can't leave its picture behind.
 */
@Singleton
class RotationPrefetchCache internal constructor(
    private val directory: File,
    private val posture: NetworkPostureReader,
    moshi: Moshi,
    private val now: () -> Long,
    private val maxEntries: Int = MAX_ENTRIES,
    private val maxBytes: Long = MAX_BYTES,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        moshi: Moshi,
    ) : this(
        directory = File(context.cacheDir, "rotation_prefetch"),
        posture = AndroidNetworkPosture(context),
        moshi = moshi,
        now = System::currentTimeMillis,
    )

    private val lock = Mutex()
    private val adapter = moshi.adapter(RotationPrefetchIndex::class.java)
    private val indexFile get() = File(directory, INDEX_FILE)

    /** Files a download is still filling. They aren't in the index yet, and a sweep must leave them. */
    private val downloading: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** False until this process has swept the folder once. Only touched under [lock]. */
    private var sweptOnce = false

    /** The wallpaper saved for [slot], or null. A record whose file is gone counts as nothing saved. */
    suspend fun peek(slot: String): PrefetchedWallpaper? = locked {
        val record = readIndex().records.firstOrNull { it.slot == slot } ?: return@locked null
        val file = File(directory, record.fileName)
        if (!file.isFile || file.length() == 0L) return@locked null
        val wallpaper = record.toWallpaper() ?: return@locked null
        PrefetchedWallpaper(wallpaper, file)
    }

    /** Forgets what is saved for [slot] and deletes its file. */
    suspend fun discard(slot: String): Unit = locked {
        val index = readIndex()
        index.records.filter { it.slot == slot }.forEach { File(directory, it.fileName).delete() }
        writeIndex(index.copy(records = index.records.filterNot { it.slot == slot }))
    }

    suspend fun recordHit(): Unit = locked { readIndex().let { writeIndex(it.copy(hits = it.hits + 1)) } }

    suspend fun recordMiss(): Unit = locked { readIndex().let { writeIndex(it.copy(misses = it.misses + 1)) } }

    /**
     * Saves [wallpaper] for [slot] by handing [download] a file to fill. Nothing is downloaded on a
     * metered connection or with Data Saver on: a background download is exactly what both exist
     * to stop, and the next rotation can still fetch for itself.
     */
    suspend fun store(
        slot: String,
        wallpaper: Wallpaper,
        download: suspend (File) -> Result<Long>,
    ): RotationPrefetchOutcome {
        val outcome = fetch(slot, wallpaper, download)
        locked { writeIndex(readIndex().copy(lastOutcome = outcome.name)) }
        return outcome
    }

    /** The download runs outside the lock, so a slow one never holds up a read of the cache. */
    private suspend fun fetch(
        slot: String,
        wallpaper: Wallpaper,
        download: suspend (File) -> Result<Long>,
    ): RotationPrefetchOutcome {
        val network = posture.read()
        if (network.dataSaver) return RotationPrefetchOutcome.DEFERRED_DATA_SAVER
        if (network.metered) return RotationPrefetchOutcome.DEFERRED_METERED

        val fileName = "${slot.hashCode().toUInt().toString(16)}-${now()}.${wallpaper.prefetchExtension()}"
        // Named before the first byte lands, so a sweep that runs meanwhile leaves this download alone.
        downloading += fileName
        try {
            val file = withContext(Dispatchers.IO) {
                directory.mkdirs()
                File(directory, fileName)
            }
            val bytes = download(file).getOrNull()
            return locked {
                when {
                    bytes == null || bytes <= 0L || !file.isFile -> RotationPrefetchOutcome.FAILED
                    bytes > maxBytes -> RotationPrefetchOutcome.OVER_BUDGET
                    else -> {
                        val index = readIndex()
                        index.records.filter { it.slot == slot }.forEach { File(directory, it.fileName).delete() }
                        val kept = index.records.filterNot { it.slot == slot }.sortedBy { it.savedAt }.toMutableList()
                        // Oldest out first until the new file fits under both limits.
                        while (kept.isNotEmpty() &&
                            (kept.size + 1 > maxEntries || kept.sumOf { it.bytes } + bytes > maxBytes)
                        ) {
                            File(directory, kept.removeAt(0).fileName).delete()
                        }
                        writeIndex(index.copy(records = kept + wallpaper.toRecord(slot, fileName, bytes, now())))
                        RotationPrefetchOutcome.STORED
                    }
                }
            }
        } finally {
            // A cancelled run ends up here too. Its blocking write still finishes, and then nothing
            // records the file. Whatever the index didn't take goes, along with the .tmp beside it.
            withContext(NonCancellable) {
                locked {
                    downloading -= fileName
                    sweepUnindexed(readIndex())
                }
            }
        }
    }

    /**
     * True when nothing should be downloaded ahead right now. Callers ask before they fetch a
     * source's list, so a metered connection isn't spent on a wallpaper that then isn't saved.
     */
    suspend fun shouldDefer(): Boolean {
        val network = posture.read()
        val outcome = when {
            network.dataSaver -> RotationPrefetchOutcome.DEFERRED_DATA_SAVER
            network.metered -> RotationPrefetchOutcome.DEFERRED_METERED
            else -> return false
        }
        locked { writeIndex(readIndex().copy(lastOutcome = outcome.name)) }
        return true
    }

    /** Notes why nothing was saved ahead when the rotation worked that out before asking the cache. */
    suspend fun recordSkipped(outcome: RotationPrefetchOutcome): Unit =
        locked { writeIndex(readIndex().copy(lastOutcome = outcome.name)) }

    suspend fun diagnostics(): RotationPrefetchDiagnostics = locked {
        val index = readIndex()
        RotationPrefetchDiagnostics(
            hits = index.hits,
            misses = index.misses,
            savedCount = index.records.size,
            savedBytes = index.records.sumOf { it.bytes },
            lastOutcome = RotationPrefetchOutcome.entries.firstOrNull { it.name == index.lastOutcome },
        )
    }

    /** Drops every saved wallpaper and keeps the counts. */
    suspend fun clear(): Unit = locked {
        val index = readIndex()
        index.records.forEach { File(directory, it.fileName).delete() }
        writeIndex(index.copy(records = emptyList()))
    }

    private suspend fun <T> locked(block: suspend () -> T): T = lock.withLock {
        withContext(Dispatchers.IO) {
            if (!sweptOnce) {
                // A process killed in the middle of a download left its file with nothing pointing at it.
                sweptOnce = true
                sweepUnindexed(readIndex())
            }
            block()
        }
    }

    /**
     * Deletes every file the index doesn't name. File names carry a timestamp, so a picture that was
     * never recorded, or the `.tmp` a killed download was writing, is never overwritten or found
     * again. A download still running is left alone, and so is anything a slot's record points at.
     */
    private fun sweepUnindexed(index: RotationPrefetchIndex) {
        val keep = hashSetOf(INDEX_FILE)
        index.records.mapTo(keep) { it.fileName }
        downloading.forEach { name ->
            keep += name
            keep += "$name.tmp"
        }
        directory.listFiles()?.forEach { file -> if (file.isFile && file.name !in keep) file.delete() }
    }

    private fun readIndex(): RotationPrefetchIndex {
        val text = runCatching { indexFile.takeIf(File::isFile)?.readText() }.getOrNull()
            ?: return RotationPrefetchIndex()
        // An index that won't parse is thrown away with its files, never trusted in part.
        return runCatching { adapter.fromJson(text) }.getOrNull() ?: RotationPrefetchIndex().also {
            directory.listFiles()?.forEach(File::delete)
        }
    }

    private fun writeIndex(index: RotationPrefetchIndex) {
        directory.mkdirs()
        val temp = File(directory, "$INDEX_FILE.tmp")
        temp.writeText(adapter.toJson(index))
        if (!temp.renameTo(indexFile)) {
            temp.copyTo(indexFile, overwrite = true)
            temp.delete()
        }
    }

    companion object {
        /** One per rotation setup. A person switching sources leaves a few behind at most. */
        internal const val MAX_ENTRIES = 4
        internal const val MAX_BYTES = 96L * 1024L * 1024L
        private const val INDEX_FILE = "index.json"
    }
}

/** Names one rotation setup: the wallpaper saved for one never fits another's source or screens. */
internal fun rotationPrefetchSlot(source: String, target: com.freevibe.data.model.WallpaperTarget): String =
    "$source|${target.name}"

private val PREFETCH_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif")

/** The picture's own extension when the address shows one, so the decoder sees a familiar name. */
private fun Wallpaper.prefetchExtension(): String =
    fullUrl.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        .takeIf { it in PREFETCH_EXTENSIONS } ?: "img"

/** Only a remote picture is worth saving ahead. A local one is already on the phone. */
internal fun Wallpaper.isPrefetchable(): Boolean =
    fullUrl.startsWith("https://", ignoreCase = true) || fullUrl.startsWith("http://", ignoreCase = true)

private fun Wallpaper.toRecord(slot: String, fileName: String, bytes: Long, savedAt: Long) = RotationPrefetchRecord(
    slot = slot,
    fileName = fileName,
    bytes = bytes,
    savedAt = savedAt,
    id = id,
    source = source.name,
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
    category = category,
    tags = tags,
    colors = colors,
    fileSize = fileSize,
    fileType = fileType,
    sourcePageUrl = sourcePageUrl,
    license = license,
    uploaderName = uploaderName,
    communityUploaderId = communityUploaderId,
    isAiGenerated = isAiGenerated,
    contentHash = contentHash,
)

/** Null when the source is one this build no longer knows, so the record is dropped. */
private fun RotationPrefetchRecord.toWallpaper(): Wallpaper? {
    val knownSource = ContentSource.entries.firstOrNull { it.name == source } ?: return null
    return Wallpaper(
        id = id,
        source = knownSource,
        thumbnailUrl = thumbnailUrl,
        fullUrl = fullUrl,
        width = width,
        height = height,
        category = category,
        tags = tags,
        colors = colors,
        fileSize = fileSize,
        fileType = fileType,
        sourcePageUrl = sourcePageUrl,
        license = license,
        uploaderName = uploaderName,
        communityUploaderId = communityUploaderId,
        isAiGenerated = isAiGenerated,
        contentHash = contentHash,
    )
}
