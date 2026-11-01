package com.freevibe.service

import android.content.Context
import android.graphics.Bitmap
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperPair
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.remote.RequestPurpose
import com.freevibe.data.remote.purpose
import com.freevibe.util.rethrowIfCancelled
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DualWallpaperService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wallpaperApplier: WallpaperApplier,
    private val okHttpClient: OkHttpClient,
) {
    /** Apply a wallpaper pair: home wallpaper + lock wallpaper simultaneously */
    suspend fun applyPair(pair: WallpaperPair): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Download both concurrently
            val homeBitmap = async { downloadBitmap(pair.home.currentFullUrl()) }
            val lockBitmap = async { downloadBitmap(pair.lock.currentFullUrl()) }

            val home = try {
                homeBitmap.await()
            } catch (e: Exception) {
                lockBitmap.cancel()
                // Recycle if lock download already completed
                try { lockBitmap.await().recycle() } catch (_: Exception) {}
                throw e
            }
            val lock = try {
                lockBitmap.await()
            } catch (e: Exception) {
                home.recycle()
                throw e
            }
            try {
                wallpaperApplier.applyFromBitmap(home, WallpaperTarget.HOME).getOrThrow()
                wallpaperApplier.applyFromBitmap(lock, WallpaperTarget.LOCK).getOrThrow()
            } finally {
                home.recycle()
                lock.recycle()
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    /** Apply same wallpaper with different crops to home and lock */
    suspend fun applySplitCrop(
        wallpaper: Wallpaper,
        homeCropTop: Float = 0f,       // 0.0 - 0.5 range
        lockCropTop: Float = 0.3f,     // Offset for lock screen
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = downloadBitmap(wallpaper.currentFullUrl())
            var homeCrop: Bitmap? = null
            var lockCrop: Bitmap? = null
            try {
                val height = bitmap.height
                val width = bitmap.width
                val cropHeight = (height * 0.7f).toInt().coerceIn(1, height)

                val homeY = (height * homeCropTop).toInt().coerceIn(0, (height - cropHeight).coerceAtLeast(0))
                val lockY = (height * lockCropTop).toInt().coerceIn(0, (height - cropHeight).coerceAtLeast(0))

                homeCrop = Bitmap.createBitmap(bitmap, 0, homeY, width, cropHeight)
                lockCrop = Bitmap.createBitmap(bitmap, 0, lockY, width, cropHeight)

                wallpaperApplier.applyFromBitmap(homeCrop, WallpaperTarget.HOME).getOrThrow()
                wallpaperApplier.applyFromBitmap(lockCrop, WallpaperTarget.LOCK).getOrThrow()
            } finally {
                if (homeCrop !== bitmap) homeCrop?.recycle()
                if (lockCrop !== bitmap) lockCrop?.recycle()
                bitmap.recycle()
            }
        }.onFailure { it.rethrowIfCancelled() }
    }

    private fun downloadBitmap(url: String): Bitmap {
        if (isLocalMediaLocator(url)) {
            return decodeLocalMediaImage(
                context = context,
                locator = url,
                flow = MediaIngestionImageFlow.LOCAL_APPLY,
                maxBytes = MAX_WALLPAPER_BYTES,
                maxLongEdge = targetWallpaperDecodeLongEdge(),
            )
        }
        val request = Request.Builder().purpose(RequestPurpose.APPLY).url(url).build()
        val response = okHttpClient.newCall(request).execute()
        return response.use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("Download failed: ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("Empty response body")
            // Reject oversized payloads before allocating the byte[].
            val advertised = body.contentLength()
            if (advertisedLengthExceeds(advertised, MAX_WALLPAPER_BYTES)) {
                throw java.io.IOException("Wallpaper too large: $advertised > $MAX_WALLPAPER_BYTES bytes")
            }
            val bytes = readStreamCapped(body.byteStream(), MAX_WALLPAPER_BYTES)
            if (bytes.isEmpty()) throw java.io.IOException("Empty response body")

            decodeImageBytes(bytes, maxLongEdge = targetWallpaperDecodeLongEdge())
                ?: throw IllegalStateException("Failed to decode image")
        }
    }

    private fun targetWallpaperDecodeLongEdge(): Int {
        val metrics = context.resources.displayMetrics
        return (maxOf(metrics.widthPixels, metrics.heightPixels) * 2).coerceAtLeast(1)
    }

    private companion object {
        /** Mirrors WallpaperApplier's cap — pair wallpapers go through the same flow. */
        private const val MAX_WALLPAPER_BYTES = 64L * 1024 * 1024
    }
}
