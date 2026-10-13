package com.freevibe.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import java.util.Locale
import kotlin.math.pow

/** WCAG's minimum contrast for normal-size text. */
internal const val MIN_TEXT_CONTRAST = 4.5

/** GlassCard paints its container colour at this alpha over whatever image sits behind it. */
internal const val GLASS_CARD_ALPHA = 0.72f

/** Brand tones for the wallpaper provider badge, keyed by source name. Shown through [readableOn], never raw. */
internal val SourceBrandTones: Map<String, Color> = mapOf(
    "BING" to Color(0xFF00809D),
    "WIKIMEDIA" to Color(0xFF006699),
    "INTERNET_ARCHIVE" to Color(0xFFFF8C00),
    "REDDIT" to Color(0xFFFF4500),
    "NASA" to Color(0xFF0B3D91),
    "FREESOUND" to Color(0xFF3DB2CE),
    "JAMENDO" to Color(0xFF7E57C2),
    "AUDIUS" to Color(0xFF00C2A8),
    "CCMIXTER" to Color(0xFF8E24AA),
    "YOUTUBE" to Color(0xFFFF0000),
    "TIKTOK" to Color(0xFF00A3AD),
    "PEXELS" to Color(0xFF05A081),
    "PIXABAY" to Color(0xFF00AB6C),
    "KLIPY" to Color(0xFFE040FB),
    "SOUNDCLOUD" to Color(0xFFFF5500),
    "COMMUNITY" to Color(0xFF4CAF50),
    "BUNDLED" to Color(0xFFFFB300),
)

/** The two extremes a glass card can show: its tint over pure black and over pure white. */
internal fun glassBackdrops(container: Color): List<Color> =
    listOf(Color.Black, Color.White).map { container.copy(alpha = GLASS_CARD_ALPHA).compositeOver(it) }

/**
 * The provider badge colour for [source] in [scheme]. The badge sits on a GlassCard over a wallpaper, so the
 * tone has to read at 4.5:1 whatever the image behind the glass is.
 */
internal fun sourceBadgeTone(source: String, scheme: ColorScheme): Color {
    val key = source.uppercase(Locale.ROOT)
    val tone = SourceBrandTones[key] ?: when (key) {
        "WALLHAVEN" -> scheme.primary
        "PICSUM" -> scheme.outline
        else -> scheme.onSurfaceVariant
    }
    return readableOn(tone, glassBackdrops(scheme.surfaceContainer))
}

/**
 * [tone] if it already reads at [minimum] contrast on every one of [backgrounds], otherwise the first step
 * toward white or black, whichever stands further from all of them, that does.
 */
internal fun readableOn(tone: Color, backgrounds: List<Color>, minimum: Double = MIN_TEXT_CONTRAST): Color {
    fun worst(color: Color) = backgrounds.minOf { contrastRatio(color, it) }
    if (worst(tone) >= minimum) return tone
    val target = if (worst(Color.White) >= worst(Color.Black)) Color.White else Color.Black
    for (step in 1..CONTRAST_STEPS) {
        val fraction = step.toFloat() / CONTRAST_STEPS
        val candidate = Color(
            red = tone.red + (target.red - tone.red) * fraction,
            green = tone.green + (target.green - tone.green) * fraction,
            blue = tone.blue + (target.blue - tone.blue) * fraction,
        )
        if (worst(candidate) >= minimum) return candidate
    }
    return target
}

/** WCAG 2 contrast ratio between two opaque colours, from 1.0 to 21.0. */
internal fun contrastRatio(first: Color, second: Color): Double {
    val lighter = maxOf(relativeLuminance(first), relativeLuminance(second))
    val darker = minOf(relativeLuminance(first), relativeLuminance(second))
    return (lighter + 0.05) / (darker + 0.05)
}

private const val CONTRAST_STEPS = 20

private fun relativeLuminance(color: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}
