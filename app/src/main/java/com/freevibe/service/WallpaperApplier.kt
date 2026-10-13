package com.freevibe.service

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.freevibe.data.model.MediaTechnicalMetadata
import com.freevibe.data.model.FitCanvasStyle
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.model.decideWallpaperOptimization
import com.freevibe.data.model.mediaOptimizationKey
import com.freevibe.data.model.originalTechnicalMetadata
import com.freevibe.data.model.wallpaperOptimizationReason
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FilterInputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

internal fun nightWallpaperVariantColorMatrix(): FloatArray = floatArrayOf(
    0.72f, 0f, 0f, 0f, -24f,
    0f, 0.72f, 0f, 0f, -24f,
    0f, 0f, 0.72f, 0f, -24f,
    0f, 0f, 0f, 1f, 0f,
)

@Singleton
class WallpaperApplier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val mediaCopyStore: MediaCopyStore,
) {
    private val wallpaperManager = WallpaperManager.getInstance(context)

    /** Download image from URL and apply as wallpaper */
    suspend fun applyFromUrl(
        url: String,
        target: WallpaperTarget = WallpaperTarget.BOTH,
        cropRect: Rect? = null,
        nightVariant: Boolean = false,
        fitCanvasStyle: FitCanvasStyle? = null,
        imageFlow: MediaIngestionImageFlow = MediaIngestionImageFlow.LOCAL_APPLY,
        savedOriginalId: String? = null,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
    ): Result<Unit> = applyByLocator(
        locator = url,
        target = target,
        cropRect = cropRect,
        nightVariant = nightVariant,
        fitCanvasStyle = fitCanvasStyle,
        imageFlow = imageFlow,
        savedOriginalId = savedOriginalId,
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
    )

    /**
     * Apply a wallpaper from any locator — http(s) URL, file:// URI, content:// URI,
     * or a bare absolute path. Earlier revisions only spoke HTTP via [applyFromUrl];
     * callers that need to handle locally-stored wallpapers (AI-generated, gallery,
     * parallax cache, user uploads) should use this entrypoint instead.
     */
    suspend fun applyByLocator(
        locator: String,
        target: WallpaperTarget = WallpaperTarget.BOTH,
        cropRect: Rect? = null,
        darkenPercent: Int = 0,
        nightVariant: Boolean = false,
        fitCanvasStyle: FitCanvasStyle? = null,
        imageFlow: MediaIngestionImageFlow = MediaIngestionImageFlow.LOCAL_APPLY,
        savedOriginalId: String? = null,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (savedOriginal, effectiveLocator) = mediaCopyStore.findOriginal(savedOriginalId, locator)
            val targetDimensions = getScreenDimensions()
            val knownMetadata = savedOriginal?.originalTechnicalMetadata()?.let { metadata ->
                if (metadata.width > 0 && metadata.height > 0) metadata else metadata.copy(
                    width = sourceWidth,
                    height = sourceHeight,
                )
            } ?: MediaTechnicalMetadata(width = sourceWidth, height = sourceHeight)
            val overlayEnabled = isWallpaperClockOverlayEnabled(context)
            val hasPixelTransform = darkenPercent > 0 || nightVariant || fitCanvasStyle != null ||
                cropRect != null || overlayEnabled
            val optimization = decideWallpaperOptimization(
                metadata = knownMetadata,
                targetWidth = targetDimensions.first,
                targetHeight = targetDimensions.second,
                hasPixelTransform = hasPixelTransform,
            )
            // WallpaperManager can consume the encoded source directly. Keep the bitmap
            // path below for transformations, but avoid expanding a normal JPEG/PNG into
            // a full ARGB bitmap before the system receives it.
            if (!optimization.required && streamLocatorToWallpaper(effectiveLocator, target, cropRect)
            ) {
                return@runCatching Unit
            }
            val optimizationKey = mediaOptimizationKey(
                "wallpaper",
                savedOriginal?.originalSha256.orEmpty().ifBlank { effectiveLocator },
                target.name,
                cropRect?.flattenToString().orEmpty(),
                darkenPercent,
                nightVariant,
                fitCanvasStyle?.mode?.preferenceValue.orEmpty(),
                fitCanvasStyle?.customColor ?: 0,
                overlayEnabled,
                targetDimensions.first,
                targetDimensions.second,
            )
            if (savedOriginal != null) {
                mediaCopyStore.reusableCopy(savedOriginal.id, optimizationKey)?.let { reusable ->
                    if (streamLocalFile(reusable.file.absolutePath) { input ->
                            wallpaperManager.setStream(
                                WallpaperByteLimitInputStream(input, MAX_WALLPAPER_BYTES),
                                cropRect,
                                true,
                                wallpaperFlags(target),
                            )
                        }
                    ) {
                        return@runCatching Unit
                    }
                }
            }
            val decodeLongEdge = if (fitCanvasStyle != null) {
                val (screenWidth, screenHeight) = targetDimensions
                maxOf(screenWidth, screenHeight).coerceAtLeast(1)
            } else {
                targetWallpaperDecodeLongEdge()
            }
            var bitmap = decodeFromLocator(effectiveLocator, imageFlow, decodeLongEdge)
                ?: throw IllegalStateException("Failed to decode wallpaper image")
            try {
                if (darkenPercent > 0) {
                    val darkened = applyDarken(bitmap, darkenPercent.coerceIn(1, 100))
                    bitmap.recycle()
                    bitmap = darkened
                }
                if (nightVariant) {
                    val nightBitmap = applyNightVariant(bitmap)
                    bitmap.recycle()
                    bitmap = nightBitmap
                }
                if (fitCanvasStyle != null) {
                    val (targetWidth, targetHeight) = getScreenDimensions()
                    val fitted = FitCanvasRenderer.render(
                        source = bitmap,
                        targetWidth = targetWidth,
                        targetHeight = targetHeight,
                        style = fitCanvasStyle,
                    ).bitmap
                    bitmap.recycle()
                    bitmap = fitted
                }
                val overlayBitmap = bitmapWithWallpaperClockOverlay(context, bitmap)
                if (overlayBitmap !== bitmap) {
                    bitmap.recycle()
                    bitmap = overlayBitmap
                }
                if (savedOriginal != null) {
                    val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
                    val reason = wallpaperOptimizationReason(
                        optimization.reason,
                        savedOriginal.originalHdr,
                    )
                    val sourceBitmap = bitmap
                    val prepared = mediaCopyStore.prepareCopy(
                        downloadId = savedOriginal.id,
                        sourceIdentity = savedOriginal.originalSha256.ifBlank { effectiveLocator },
                        optimizationKey = optimizationKey,
                        reason = reason,
                        extension = extension,
                        expectedBytes = sourceBitmap.byteCount.toLong().coerceAtMost(MAX_WALLPAPER_BYTES),
                        maxBytes = MAX_WALLPAPER_BYTES,
                    ) { pending ->
                        pending.outputStream().use { output ->
                            if (!sourceBitmap.compress(format, 94, output)) {
                                throw java.io.IOException("Could not write optimized wallpaper copy")
                            }
                        }
                    }
                    streamLocalFile(prepared.file.absolutePath) { input ->
                        wallpaperManager.setStream(
                            WallpaperByteLimitInputStream(input, MAX_WALLPAPER_BYTES),
                            cropRect,
                            true,
                            wallpaperFlags(target),
                        )
                    }
                } else {
                    wallpaperManager.setBitmap(bitmap, cropRect, true, wallpaperFlags(target))
                }
                Unit
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Stream an encoded source directly to WallpaperManager when no pixel transform is needed. */
    private fun streamLocatorToWallpaper(
        locator: String,
        target: WallpaperTarget,
        cropRect: Rect?,
    ): Boolean {
        if (locator.isBlank()) return false
        fun apply(input: InputStream) {
            wallpaperManager.setStream(
                WallpaperByteLimitInputStream(input, MAX_WALLPAPER_BYTES),
                cropRect,
                true,
                wallpaperFlags(target),
            )
        }

        return when {
            locator.startsWith("http://", ignoreCase = true) ||
                locator.startsWith("https://", ignoreCase = true) -> {
                okHttpClient.newCall(Request.Builder().url(locator).build()).execute().use { response ->
                    if (!response.isSuccessful) throw java.io.IOException("Download failed: ${response.code}")
                    val body = response.body ?: throw java.io.IOException("Empty response body")
                    val advertised = body.contentLength()
                    if (advertised > MAX_WALLPAPER_BYTES) {
                        throw java.io.IOException("Wallpaper too large: $advertised > $MAX_WALLPAPER_BYTES bytes")
                    }
                    apply(body.byteStream())
                }
                true
            }
            locator.startsWith("content://", ignoreCase = true) -> {
                val input = context.contentResolver.openInputStream(android.net.Uri.parse(locator))
                    ?: throw java.io.IOException("Could not open wallpaper content")
                input.use(::apply)
                true
            }
            locator.startsWith("file:", ignoreCase = true) -> {
                val path = android.net.Uri.parse(locator).path ?: return false
                streamLocalFile(path, ::apply)
            }
            locator.startsWith("/") -> streamLocalFile(locator, ::apply)
            else -> false
        }
    }

    private fun streamLocalFile(path: String, apply: (InputStream) -> Unit): Boolean {
        val file = java.io.File(path)
        if (!file.exists() || !file.canRead()) {
            throw java.io.IOException("Wallpaper file is unavailable")
        }
        if (file.length() > MAX_WALLPAPER_BYTES) {
            throw java.io.IOException("Wallpaper too large: ${file.length()} > $MAX_WALLPAPER_BYTES bytes")
        }
        file.inputStream().use(apply)
        return true
    }

    /** Apply wallpaper from an already-loaded bitmap */
    suspend fun applyFromBitmap(
        bitmap: Bitmap,
        target: WallpaperTarget = WallpaperTarget.BOTH,
        fitCanvasStyle: FitCanvasStyle? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fittedBitmap = fitCanvasStyle?.let { style ->
                val (targetWidth, targetHeight) = getScreenDimensions()
                FitCanvasRenderer.render(bitmap, targetWidth, targetHeight, style).bitmap
            }
            val sourceBitmap = fittedBitmap ?: bitmap
            val overlayBitmap = bitmapWithWallpaperClockOverlay(context, sourceBitmap)
            try {
                wallpaperManager.setBitmap(overlayBitmap, null, true, wallpaperFlags(target))
            } finally {
                if (overlayBitmap !== sourceBitmap && !overlayBitmap.isRecycled) overlayBitmap.recycle()
                if (fittedBitmap != null && !fittedBitmap.isRecycled) fittedBitmap.recycle()
            }
            Unit
        }.onFailure { it.rethrowIfCancelled() }
    }

    /**
     * Download image from URL, save to internal storage, and store path in
     * SharedPreferences for ParallaxWallpaperService to read.
     * Returns the saved file path on success.
     */
    suspend fun prepareParallaxWallpaper(url: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
                val dir = java.io.File(context.filesDir, "parallax")
                dir.mkdirs()
                val file = java.io.File(dir, fileName)
                // Atomic temp-then-rename: if copy is interrupted mid-stream, the
                // ParallaxWallpaperService used to read a truncated file on the next surface
                // creation. Write to a sibling .tmp, rename on success, and clean up the .tmp
                // on any failure so orphaned partial writes never accumulate.
                val tempFile = java.io.File(dir, "$fileName.tmp")
                val body = resp.body ?: throw java.io.IOException("Empty response body")
                try {
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            copyCapped(input, output, MAX_WALLPAPER_BYTES)
                        }
                    }
                    if (!tempFile.renameTo(file)) {
                        // renameTo can fail across filesystems; fall back to copy+delete.
                        tempFile.copyTo(file, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    try { tempFile.delete() } catch (_: Exception) {}
                    throw e
                }
                // Store path for ParallaxWallpaperService
                context.getSharedPreferences("freevibe_parallax", Context.MODE_PRIVATE)
                    .edit()
                    .putString("image_path", file.absolutePath)
                    .apply()
                file.absolutePath
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    /**
     * Saves the image at [url] to [destination] without applying it, under the same size cap as an
     * apply. Rotation uses it to fetch the next wallpaper ahead of time. Returns the bytes written.
     */
    suspend fun downloadToFile(url: String, destination: java.io.File): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
                val body = resp.body ?: throw java.io.IOException("Empty response body")
                val advertised = body.contentLength()
                if (advertised > MAX_WALLPAPER_BYTES) {
                    throw java.io.IOException("Wallpaper too large: $advertised > $MAX_WALLPAPER_BYTES bytes")
                }
                val tempFile = java.io.File(destination.parentFile, "${destination.name}.tmp")
                try {
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            copyCapped(input, output, MAX_WALLPAPER_BYTES)
                        }
                    }
                    if (tempFile.length() == 0L) throw java.io.IOException("Empty response body")
                    if (!tempFile.renameTo(destination)) {
                        tempFile.copyTo(destination, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    try { tempFile.delete() } catch (_: Exception) {}
                    throw e
                }
                destination.length()
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    /**
     * Parallax variant for a user-supplied local URI (gallery / share intent). Same output
     * path + atomic write as prepareParallaxWallpaper(url), so ParallaxWallpaperService
     * reads from the same SharedPreferences key.
     */
    suspend fun prepareParallaxFromUri(uri: android.net.Uri, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.filesDir, "parallax")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            val tempFile = java.io.File(dir, "$fileName.tmp")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Could not open photo")
                input.use { inStream ->
                    tempFile.outputStream().use { output ->
                        copyCapped(inStream, output, MAX_WALLPAPER_BYTES)
                    }
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                try { tempFile.delete() } catch (_: Exception) {}
                throw e
            }
            context.getSharedPreferences("freevibe_parallax", Context.MODE_PRIVATE)
                .edit()
                .putString("image_path", file.absolutePath)
                .apply()
            file.absolutePath
        }.onFailure { it.rethrowIfCancelled() }
    }

    /**
     * Parallax variant for editor-generated bitmaps. This writes through the same
     * atomic file + SharedPreferences path as URL/gallery parallax sources.
     */
    suspend fun prepareParallaxFromBitmap(bitmap: Bitmap, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.filesDir, "parallax")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            val tempFile = java.io.File(dir, "$fileName.tmp")
            try {
                tempFile.outputStream().use { output ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)) {
                        throw java.io.IOException("Could not write parallax image")
                    }
                }
                if (tempFile.length() <= 0L) {
                    throw java.io.IOException("Parallax image is empty")
                }
                if (tempFile.length() > MAX_WALLPAPER_BYTES) {
                    throw java.io.IOException("Parallax image too large: ${tempFile.length()} > $MAX_WALLPAPER_BYTES bytes")
                }
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            } catch (e: Exception) {
                try { tempFile.delete() } catch (_: Exception) {}
                throw e
            }
            context.getSharedPreferences("freevibe_parallax", Context.MODE_PRIVATE)
                .edit()
                .putString("image_path", file.absolutePath)
                .apply()
            file.absolutePath
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Check if wallpaper operations are supported */
    fun isSupported(): Boolean {
        return wallpaperManager.isWallpaperSupported && wallpaperManager.isSetWallpaperAllowed
    }

    /** Get screen dimensions for optimal crop suggestions */
    fun getScreenDimensions(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    /**
     * Dispatch decode by scheme. Returns null on unknown scheme or decode failure.
     * Visible for tests (internal).
     */
    internal suspend fun decodeFromLocator(
        locator: String,
        imageFlow: MediaIngestionImageFlow = MediaIngestionImageFlow.LOCAL_APPLY,
        maxLongEdge: Int = targetWallpaperDecodeLongEdge(),
    ): Bitmap? {
        if (locator.isBlank()) return null
        return when {
            locator.startsWith("http://", ignoreCase = true) ||
                locator.startsWith("https://", ignoreCase = true) ->
                downloadBitmap(locator, imageFlow, maxLongEdge)
            locator.startsWith("content://", ignoreCase = true) ->
                decodeFromContentUri(locator, imageFlow, maxLongEdge)
            locator.startsWith("file:", ignoreCase = true) -> {
                // Both file:/path and file:///path produce a parseable Uri; decode the
                // raw path so local wallpaper files share the same bounded image helper.
                val path = android.net.Uri.parse(locator).path
                if (path.isNullOrBlank()) null else decodeLocalPath(path, imageFlow, maxLongEdge)
            }
            locator.startsWith("/") -> decodeLocalPath(locator, imageFlow, maxLongEdge)
            else -> null
        }
    }

    private suspend fun decodeFromContentUri(
        uri: String,
        imageFlow: MediaIngestionImageFlow,
        maxLongEdge: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val parsed = runCatching { android.net.Uri.parse(uri) }.getOrNull() ?: return@withContext null
        decodeImageUriForFlow(
            context = context,
            uri = parsed,
            flow = imageFlow,
            maxLongEdge = maxLongEdge,
        )
    }

    private suspend fun decodeLocalPath(
        path: String,
        imageFlow: MediaIngestionImageFlow,
        maxLongEdge: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = java.io.File(path)
        if (!file.exists() || !file.canRead()) return@withContext null
        // Cap local files at MAX_WALLPAPER_BYTES — even if the file is on user storage we
        // don't want a runaway 200 MB PNG to wedge the WallpaperManager IPC.
        if (file.length() > MAX_WALLPAPER_BYTES) return@withContext null
        decodeImageFileForFlow(
            file = file,
            flow = imageFlow,
            maxLongEdge = maxLongEdge,
        )
    }

    private fun targetWallpaperDecodeLongEdge(): Int {
        val metrics = context.resources.displayMetrics
        return (maxOf(metrics.widthPixels, metrics.heightPixels) * 2).coerceAtLeast(1)
    }

    private suspend fun downloadBitmap(
        url: String,
        imageFlow: MediaIngestionImageFlow,
        maxLongEdge: Int,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
            val body = resp.body ?: throw java.io.IOException("Empty response body")
            // Reject oversized payloads up front so a hostile or misbehaving CDN can't make us
            // allocate a huge byte[] just to OOM during decode. 64 MB is larger than any real
            // 8K JPG/PNG/WEBP wallpaper. We still stream-cap below in case Content-Length
            // is missing (chunked transfer) or lies.
            val advertised = body.contentLength()
            if (advertised in 1..Long.MAX_VALUE && advertised > MAX_WALLPAPER_BYTES) {
                throw java.io.IOException("Wallpaper too large: $advertised > $MAX_WALLPAPER_BYTES bytes")
            }
            // Stream into a bounded buffer rather than calling body.bytes(), which has no
            // upper bound and will happily allocate gigabytes when Content-Length is unknown
            // or wrong. Abort the read the moment we exceed the cap.
            val bytes = readCapped(body.byteStream(), MAX_WALLPAPER_BYTES)
            if (bytes.isEmpty()) throw java.io.IOException("Empty response body")

            decodeImageBytesForFlow(
                bytes = bytes,
                flow = imageFlow,
                declaredMimeType = body.contentType()?.toString(),
                extension = url.substringBefore('?').substringAfterLast('.', missingDelimiterValue = ""),
                maxLongEdge = maxLongEdge,
            )
        }
    }

    /**
     * Copy bytes from [input] to [output], aborting (and throwing) if more than [cap]
     * bytes have been written. The output stream's bytes-so-far are intentionally
     * left in place so callers can rely on their own try/finally cleanup.
     */
    private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream, cap: Long) {
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > cap) {
                throw java.io.IOException("Source exceeds cap of $cap bytes")
            }
            output.write(chunk, 0, read)
        }
    }

    /**
     * Read at most [cap] bytes from [input] into a ByteArray, throwing IOException if
     * the source produces more. Used to defend against unbounded responses where
     * Content-Length is absent or lies.
     */
    private fun readCapped(input: java.io.InputStream, cap: Long): ByteArray {
        val buffer = java.io.ByteArrayOutputStream(64 * 1024)
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            total += read
            if (total > cap) {
                throw java.io.IOException("Wallpaper too large: exceeds $cap bytes")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun applyDarken(src: Bitmap, percent: Int): Bitmap {
        val brightness = -2.55f * percent
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, brightness,
                    0f, 1f, 0f, 0f, brightness,
                    0f, 0f, 1f, 0f, brightness,
                    0f, 0f, 0f, 1f, 0f,
                )))
            }
            canvas.drawBitmap(src, 0f, 0f, paint)
        } catch (t: Throwable) {
            result.recycle()
            throw t
        }
        return result
    }

    private fun applyNightVariant(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(result)
            val paint = Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix(nightWallpaperVariantColorMatrix()))
            }
            canvas.drawBitmap(src, 0f, 0f, paint)
        } catch (t: Throwable) {
            result.recycle()
            throw t
        }
        return result
    }

    private fun wallpaperFlags(target: WallpaperTarget): Int = when (target) {
        WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
        WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
        WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }

    private companion object {
        /** Hard cap on single-wallpaper downloads — mirrors DownloadManager's ceiling. */
        private const val MAX_WALLPAPER_BYTES = 64L * 1024 * 1024
    }
}

/** InputStream guard used by WallpaperManager.setStream, including chunked HTTP responses. */
internal class WallpaperByteLimitInputStream(
    input: InputStream,
    private val maxBytes: Long,
) : FilterInputStream(input) {
    private var bytesRead = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) recordBytes(1)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val remaining = (maxBytes - bytesRead + 1).coerceAtMost(length.toLong()).toInt()
        val count = super.read(buffer, offset, remaining)
        if (count > 0) recordBytes(count.toLong())
        return count
    }

    override fun skip(byteCount: Long): Long {
        val remaining = (maxBytes - bytesRead).coerceAtLeast(0L)
        val skipped = super.skip(byteCount.coerceAtMost(remaining))
        bytesRead += skipped
        return skipped
    }

    private fun recordBytes(count: Long) {
        bytesRead += count
        if (bytesRead > maxBytes) {
            throw java.io.IOException("Wallpaper too large: exceeds $maxBytes bytes")
        }
    }
}
