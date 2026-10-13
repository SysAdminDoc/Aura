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

internal const val FINGERPRINT_GRID = 9
private const val FINGERPRINT_DECODE_PX = 64
private val DISTINCTIVE_BITS = 8..56

/**
 * Left-right and top-bottom difference hashes of a decoded thumbnail, plus the size it decoded
 * at. The same file posted under two URLs gives the same value; the decoded size stands in for
 * the pixel size, which Reddit often doesn't report.
 */
data class WallpaperFingerprint(
    val horizontal: Long,
    val vertical: Long,
    val sampleWidth: Int,
    val sampleHeight: Int,
) {
    /** Near-flat images, such as plain black AMOLED walls, hash alike even when they differ. */
    val isDistinctive: Boolean
        get() = sampleWidth > 0 && sampleHeight > 0 &&
            java.lang.Long.bitCount(horizontal) in DISTINCTIVE_BITS &&
            java.lang.Long.bitCount(vertical) in DISTINCTIVE_BITS
}

/** A thumbnail reduced to a [FINGERPRINT_GRID] square of luma values, row by row. */
internal class ThumbnailSample(val luma: IntArray, val width: Int, val height: Int)

internal fun wallpaperFingerprintOf(sample: ThumbnailSample): WallpaperFingerprint {
    val luma = sample.luma
    require(luma.size == FINGERPRINT_GRID * FINGERPRINT_GRID) { "Expected a $FINGERPRINT_GRID by $FINGERPRINT_GRID grid" }
    var horizontal = 0L
    var vertical = 0L
    var bit = 0
    for (y in 0 until FINGERPRINT_GRID - 1) {
        for (x in 0 until FINGERPRINT_GRID - 1) {
            val here = luma[y * FINGERPRINT_GRID + x]
            if (here > luma[y * FINGERPRINT_GRID + x + 1]) horizontal = horizontal or (1L shl bit)
            if (here > luma[(y + 1) * FINGERPRINT_GRID + x]) vertical = vertical or (1L shl bit)
            bit++
        }
    }
    return WallpaperFingerprint(horizontal, vertical, sample.width, sample.height)
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

    /** Samples up to [limit] thumbnails not tried yet and publishes the results in one update. */
    suspend fun fingerprint(wallpapers: List<Wallpaper>, limit: Int = PASS_LIMIT) {
        val pending = synchronized(attempted) {
            wallpapers.asSequence()
                .filter { it.thumbnailUrl.isNotBlank() }
                .map { it.stableKey() to it.thumbnailUrl }
                .filter { (key, _) -> key !in attempted }
                .distinctBy { (key, _) -> key }
                .take(limit)
                .toList()
        }
        if (pending.isEmpty()) return
        val gate = Semaphore(PARALLEL_SAMPLES)
        val results = coroutineScope {
            pending.map { (key, url) ->
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
        const val PASS_LIMIT = 40
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
