package com.freevibe.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Side of the luma grid a thumbnail is reduced to; each hash has (side - 1) squared bits. */
internal const val FINGERPRINT_GRID = 17
private const val HASH_CELLS = FINGERPRINT_GRID - 1
private const val HASH_WORDS = HASH_CELLS * HASH_CELLS / Long.SIZE_BITS
private const val TONE_BLOCK = HASH_CELLS / 4
private const val FINGERPRINT_DECODE_PX = 64
private val DISTINCTIVE_BITS = (HASH_CELLS * HASH_CELLS / 8)..(HASH_CELLS * HASH_CELLS * 7 / 8)

/**
 * Left-right and top-bottom difference hashes of a decoded thumbnail, a coarse 4 by 4 tone map,
 * and the size it decoded at. The same file posted under two URLs gives the same value; the tone
 * map keeps two gradients with the same shape but different brightness apart, and the decoded size
 * stands in for the pixel size, which Reddit often doesn't report.
 */
data class WallpaperFingerprint(
    val horizontal: List<Long>,
    val vertical: List<Long>,
    val tone: Long,
    val sampleWidth: Int,
    val sampleHeight: Int,
) {
    /** Near-flat images, such as plain black AMOLED walls, hash alike even when they differ. */
    val isDistinctive: Boolean
        get() = sampleWidth > 0 && sampleHeight > 0 &&
            horizontal.sumOf { it.countOneBits() } in DISTINCTIVE_BITS &&
            vertical.sumOf { it.countOneBits() } in DISTINCTIVE_BITS
}

/** A thumbnail reduced to a [FINGERPRINT_GRID] square of luma values (0 to 255), row by row. */
internal class ThumbnailSample(val luma: IntArray, val width: Int, val height: Int)

internal fun wallpaperFingerprintOf(sample: ThumbnailSample): WallpaperFingerprint {
    val luma = sample.luma
    require(luma.size == FINGERPRINT_GRID * FINGERPRINT_GRID) { "Expected a $FINGERPRINT_GRID by $FINGERPRINT_GRID grid" }
    val horizontal = LongArray(HASH_WORDS)
    val vertical = LongArray(HASH_WORDS)
    val toneSums = IntArray(16)
    var bit = 0
    for (y in 0 until HASH_CELLS) {
        for (x in 0 until HASH_CELLS) {
            val here = luma[y * FINGERPRINT_GRID + x]
            val word = bit / Long.SIZE_BITS
            val mask = 1L shl (bit % Long.SIZE_BITS)
            if (here > luma[y * FINGERPRINT_GRID + x + 1]) horizontal[word] = horizontal[word] or mask
            if (here > luma[(y + 1) * FINGERPRINT_GRID + x]) vertical[word] = vertical[word] or mask
            toneSums[(y / TONE_BLOCK) * 4 + x / TONE_BLOCK] += here
            bit++
        }
    }
    var tone = 0L
    toneSums.forEachIndexed { block, sum ->
        val level = (sum / (TONE_BLOCK * TONE_BLOCK)).coerceIn(0, 255) shr 4
        tone = tone or (level.toLong() shl (block * 4))
    }
    return WallpaperFingerprint(horizontal.toList(), vertical.toList(), tone, sample.width, sample.height)
}

/**
 * Fingerprints feed thumbnails in the background so the wallpaper feed can show an image once
 * when two posts link it under different URLs. Results live for the process, so a later page or
 * a restored feed cache doesn't bring a known duplicate back.
 */
@Singleton
class WallpaperContentFingerprints internal constructor(
    private val sampleThumbnail: suspend (url: String) -> ThumbnailSample?,
) {
    @Inject constructor(@ApplicationContext context: Context) : this({ url -> decodeThumbnailSample(context, url) })

    private val _known = MutableStateFlow<Map<String, WallpaperFingerprint>>(emptyMap())
    val known: StateFlow<Map<String, WallpaperFingerprint>> = _known.asStateFlow()

    // Keys already tried, failures included, so a broken thumbnail isn't fetched on every pass.
    private val attempted = LinkedHashSet<String>()

    /**
     * Samples every thumbnail in [wallpapers] not tried yet, [batchSize] at a time, publishing
     * each batch as it finishes so a long restored feed starts coalescing early.
     */
    suspend fun fingerprint(wallpapers: List<Wallpaper>, batchSize: Int = BATCH_SIZE) {
        val pending = synchronized(attempted) {
            wallpapers.asSequence()
                .filter { it.thumbnailUrl.isNotBlank() }
                .map { it.stableKey() to it.thumbnailUrl }
                .filter { (key, _) -> key !in attempted }
                .distinctBy { (key, _) -> key }
                .toList()
        }
        pending.chunked(batchSize.coerceAtLeast(1)).forEach { batch -> sampleBatch(batch) }
    }

    private suspend fun sampleBatch(batch: List<Pair<String, String>>) {
        val gate = Semaphore(PARALLEL_SAMPLES)
        val results = coroutineScope {
            batch.map { (key, url) ->
                async {
                    val sample = gate.withPermit {
                        try {
                            sampleThumbnail(url)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                    }
                    // Marked only once tried, so a pass cancelled by a newer feed doesn't skip it for good.
                    synchronized(attempted) {
                        attempted += key
                        while (attempted.size > MAX_ENTRIES) attempted.remove(attempted.first())
                    }
                    sample?.let { key to wallpaperFingerprintOf(it) }
                }
            }.awaitAll().filterNotNull()
        }
        if (results.isEmpty()) return
        _known.update { current ->
            val merged = current + results
            if (merged.size <= MAX_ENTRIES) merged else merged.entries.drop(merged.size - MAX_ENTRIES).associate { it.key to it.value }
        }
    }

    private companion object {
        const val BATCH_SIZE = 40
        const val PARALLEL_SAMPLES = 3
        const val MAX_ENTRIES = 1_500
    }
}

/** Decodes [url] small through Coil, sharing the disk cache the feed grid fills. */
private suspend fun decodeThumbnailSample(context: Context, url: String): ThumbnailSample? = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context)
        .data(url)
        .size(FINGERPRINT_DECODE_PX)
        .allowHardware(false)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .build()
    val bitmap = (context.imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
        ?: return@withContext null
    if (bitmap.width <= 0 || bitmap.height <= 0) return@withContext null
    val grid = Bitmap.createScaledBitmap(bitmap, FINGERPRINT_GRID, FINGERPRINT_GRID, true)
    val pixels = IntArray(FINGERPRINT_GRID * FINGERPRINT_GRID)
    grid.getPixels(pixels, 0, FINGERPRINT_GRID, 0, 0, FINGERPRINT_GRID, FINGERPRINT_GRID)
    if (grid !== bitmap) grid.recycle()
    val luma = IntArray(pixels.size) { i ->
        val color = pixels[i]
        (299 * Color.red(color) + 587 * Color.green(color) + 114 * Color.blue(color)) / 1_000
    }
    ThumbnailSample(luma, bitmap.width, bitmap.height)
}
