package com.freevibe.service

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Palette only needs a small sample, and Coil decodes straight to it. */
internal const val PALETTE_DECODE_EDGE = 200

/**
 * The palette's image request. It goes through the app's shared Coil loader so it reads the same
 * disk cache as the displayed image, and with [allowNetwork] off it never downloads anything.
 */
internal fun paletteRequest(context: Context, url: String, allowNetwork: Boolean): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .size(PALETTE_DECODE_EDGE)
        // Palette reads pixels, which a hardware bitmap can't hand back.
        .allowHardware(false)
        // A 200 px copy shouldn't push display-sized bitmaps out of the memory cache.
        .memoryCachePolicy(CachePolicy.DISABLED)
        .networkCachePolicy(if (allowNetwork) CachePolicy.ENABLED else CachePolicy.DISABLED)
        .build()

/**
 * Extracts Material You-style color palette from wallpaper images.
 * Uses Android Palette API to extract dominant, vibrant, and muted colors.
 * Shows users what their system theme colors would look like before applying.
 */
@Singleton
class ColorExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class WallpaperPalette(
        val dominantColor: Int = 0,
        val vibrantColor: Int = 0,
        val vibrantDark: Int = 0,
        val vibrantLight: Int = 0,
        val mutedColor: Int = 0,
        val mutedDark: Int = 0,
        val mutedLight: Int = 0,
        val dominantSwatch: Palette.Swatch? = null,
        /**
         * Best accent for Material You preview.
         *
         * Falls back through the ladder when [dominantColor] would render as
         * dim gray on cartoon / solid-color images: dominant (if HSL passes
         * the quality gate) → darkVibrant → vibrant → lightVibrant →
         * mutedDark → muted → mutedLight → dominant. See
         * [ColorAccentSelector.selectAccent] for the gate logic.
         *
         * Always non-zero when ANY swatch is non-zero.
         */
        val bestAccentColor: Int = 0,
    )

    /**
     * Palette for the image at [url], decoded at [PALETTE_DECODE_EDGE] px. With [allowNetwork] off it
     * only reads what Coil already cached, so a screen showing the same image never downloads it twice;
     * null means it isn't cached yet.
     */
    suspend fun extractFromUrl(url: String, allowNetwork: Boolean = true): WallpaperPalette? {
        val result = try {
            context.imageLoader.execute(paletteRequest(context, url, allowNetwork))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("ColorExtractor", "Palette image failed: ${e.javaClass.simpleName}")
            return null
        }
        val image = (result as? SuccessResult)?.image ?: return null
        return withContext(Dispatchers.Default) { extractFromBitmap(image.toBitmap()) }
    }

    /** Extract color palette from a bitmap */
    fun extractFromBitmap(bitmap: Bitmap): WallpaperPalette {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val dominant = palette.getDominantColor(0)
        val vibrant = palette.getVibrantColor(0)
        val vibrantDark = palette.getDarkVibrantColor(0)
        val vibrantLight = palette.getLightVibrantColor(0)
        val muted = palette.getMutedColor(0)
        val mutedDark = palette.getDarkMutedColor(0)
        val mutedLight = palette.getLightMutedColor(0)
        return WallpaperPalette(
            dominantColor = dominant,
            vibrantColor = vibrant,
            vibrantDark = vibrantDark,
            vibrantLight = vibrantLight,
            mutedColor = muted,
            mutedDark = mutedDark,
            mutedLight = mutedLight,
            dominantSwatch = palette.dominantSwatch,
            bestAccentColor = ColorAccentSelector.selectAccent(
                dominant = dominant,
                vibrantDark = vibrantDark,
                vibrant = vibrant,
                vibrantLight = vibrantLight,
                mutedDark = mutedDark,
                muted = muted,
                mutedLight = mutedLight,
                hslOf = { color ->
                    val hsl = FloatArray(3)
                    ColorUtils.colorToHSL(color, hsl)
                    hsl
                },
            ),
        )
    }

}
