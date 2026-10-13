package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.stableKey
import com.freevibe.service.WallpaperFingerprint
import com.freevibe.service.WallpaperStyleLearningProfile
import java.util.Locale
import kotlin.math.max

enum class WallpaperDiscoverFilter { FOR_YOU, AMOLED, HIGH_RES, PORTRAIT, ICON_SAFE }

@androidx.compose.runtime.Immutable
internal data class WallpaperQualityHints(
    val resolutionLabel: String,
    val orientationLabel: String,
    val isAmoled: Boolean,
    val isIconSafe: Boolean,
)

internal fun rankWallpapers(
    wallpapers: List<Wallpaper>,
    filter: WallpaperDiscoverFilter,
    preferredResolution: String = "",
    userStyles: List<String> = emptyList(),
    styleLearningProfile: WallpaperStyleLearningProfile = WallpaperStyleLearningProfile.EMPTY,
): List<Wallpaper> {
    val candidatePool = if (filter == WallpaperDiscoverFilter.FOR_YOU) {
        wallpapers
    } else {
        wallpapers.filter { it.matchesDiscoverFilter(filter) }
    }
    val deduped = dedupeWallpapers(
        wallpapers = candidatePool,
        filter = filter,
        preferredResolution = preferredResolution,
        userStyles = userStyles,
        styleLearningProfile = styleLearningProfile,
    )
    val rankedBase = deduped.ifEmpty {
        dedupeWallpapers(
            wallpapers = wallpapers,
            filter = WallpaperDiscoverFilter.FOR_YOU,
            preferredResolution = preferredResolution,
            userStyles = userStyles,
            styleLearningProfile = styleLearningProfile,
        )
    }
    val scored = rankedBase
        .map { wallpaper ->
            wallpaper to wallpaperQualityScore(
                wallpaper = wallpaper,
                filter = filter,
                preferredResolution = preferredResolution,
                userStyles = userStyles,
                styleLearningProfile = styleLearningProfile,
            )
        }
        .sortedByDescending { it.second }
    // Reddit is the primary discovery inventory. Rank quality within each tier,
    // then keep the Reddit tier intact ahead of secondary providers. A hard quality
    // floor used to discard most of each appended page and made the grid appear to
    // stop around forty items; low-signal candidates now remain available later in
    // the endless feed instead of silently disappearing.
    val (reddit, secondary) = scored.partition { it.first.source == ContentSource.REDDIT }
    return (reddit + secondary).map { it.first }
}

private fun dedupeWallpapers(
    wallpapers: List<Wallpaper>,
    filter: WallpaperDiscoverFilter,
    preferredResolution: String,
    userStyles: List<String>,
    styleLearningProfile: WallpaperStyleLearningProfile,
): List<Wallpaper> = wallpapers
    .groupBy(::wallpaperKey)
    .values
    .mapNotNull { variants ->
        variants.maxByOrNull { wallpaper ->
            wallpaperQualityScore(
                wallpaper = wallpaper,
                filter = filter,
                preferredResolution = preferredResolution,
                userStyles = userStyles,
                styleLearningProfile = styleLearningProfile,
            )
        }
    }

internal fun Wallpaper.qualityHints(): WallpaperQualityHints {
    val pixels = width.toLong() * height.toLong()
    val resolutionLabel = when {
        pixels >= 7_000_000L || max(width, height) >= 3600 -> "4K+"
        pixels >= 3_400_000L || max(width, height) >= 2500 -> "QHD"
        pixels >= 1_900_000L || max(width, height) >= 1900 -> "FHD"
        pixels > 0L -> "HD"
        else -> "Ready"
    }
    val orientationLabel = when {
        width <= 0 || height <= 0 -> "Phone"
        height >= width -> "Portrait"
        else -> "Wide"
    }
    return WallpaperQualityHints(
        resolutionLabel = resolutionLabel,
        orientationLabel = orientationLabel,
        isAmoled = isAmoledFriendly(),
        isIconSafe = isIconSafe(),
    )
}

internal fun Wallpaper.cardAccessibilitySummary(
    hints: WallpaperQualityHints = qualityHints(),
    isFavorite: Boolean,
    voteCount: Int,
): String = buildList {
    add("${source.readableLabel()} wallpaper")
    if (category.isNotBlank()) add(category)
    add(hints.resolutionLabel)
    add(hints.orientationLabel)
    if (hints.isAmoled) add("AMOLED friendly")
    if (hints.isIconSafe) add("icon-safe")
    if (isAiGenerated == true) add("AI-generated")
    if (voteCount > 0) add("$voteCount upvotes")
    add(if (isFavorite) "saved to favorites" else "not saved")
}.joinToString(", ")

internal fun Wallpaper.matchesDiscoverFilter(filter: WallpaperDiscoverFilter): Boolean = when (filter) {
    WallpaperDiscoverFilter.FOR_YOU -> true
    WallpaperDiscoverFilter.AMOLED -> isAmoledFriendly()
    WallpaperDiscoverFilter.HIGH_RES -> hasHighResolution()
    WallpaperDiscoverFilter.PORTRAIT -> isPortraitPreferred()
    WallpaperDiscoverFilter.ICON_SAFE -> isIconSafe()
}

private fun wallpaperQualityScore(
    wallpaper: Wallpaper,
    filter: WallpaperDiscoverFilter,
    preferredResolution: String,
    userStyles: List<String>,
    styleLearningProfile: WallpaperStyleLearningProfile,
): Int {
    val pixels = wallpaper.width.toLong() * wallpaper.height.toLong()
    val normalizedTags = wallpaper.searchableTerms()
    val preferredPixels = preferredResolution.toPixelHint()
    var score = 40

    score += when (wallpaper.source) {
        ContentSource.REDDIT -> 80
        ContentSource.WALLHAVEN -> 18
        ContentSource.BING -> 16
        ContentSource.PEXELS -> 15
        ContentSource.PIXABAY -> 11
        else -> 8
    }
    if (wallpaper.source == ContentSource.REDDIT) {
        score += wallpaper.redditMobileSourceBonus()
    }
    score += when {
        pixels >= 7_000_000L -> 26
        pixels >= 3_400_000L -> 18
        pixels >= 1_900_000L -> 10
        pixels > 0L -> 3
        else -> 0
    }
    if (preferredPixels > 0L && pixels >= preferredPixels) score += 10
    if (wallpaper.isPortraitPreferred()) score += 8
    if (wallpaper.isAmoledFriendly()) score += 10
    if (wallpaper.isIconSafe()) score += 10
    if (pixels in 1L until 1_200_000L) score -= 12
    if (wallpaper.tags.size >= 3) score += 4
    if (wallpaper.favorites > 0) score += minOf(12, wallpaper.favorites / 60)
    if (wallpaper.views > 0) score += minOf(8, wallpaper.views / 400)
    if (normalizedTags.any { it in BUSY_WALLPAPER_TERMS }) score -= 14
    if (normalizedTags.any { it in CLEAN_WALLPAPER_TERMS }) score += 8
    if (normalizedTags.any { it in LOW_SIGNAL_WALLPAPER_TERMS }) score -= 18
    if (userStyles.isNotEmpty()) {
        val styleHits = normalizedTags.count { it in userStyles.toSet() }
        score += styleHits * 6
    }
    score += styleLearningProfile.scoreFor(wallpaper)
    score += when (filter) {
        WallpaperDiscoverFilter.FOR_YOU -> 0
        WallpaperDiscoverFilter.AMOLED -> if (wallpaper.isAmoledFriendly()) 18 else -8
        WallpaperDiscoverFilter.HIGH_RES -> if (wallpaper.hasHighResolution()) 16 else -10
        WallpaperDiscoverFilter.PORTRAIT -> if (wallpaper.isPortraitPreferred()) 18 else -8
        WallpaperDiscoverFilter.ICON_SAFE -> if (wallpaper.isIconSafe()) 18 else -10
    }
    return score
}

private fun Wallpaper.redditMobileSourceBonus(): Int {
    val subreddit = tags.firstOrNull { !it.equals("reddit", ignoreCase = true) }
        ?.lowercase(Locale.ROOT)
        ?: return 0
    val priority = REDDIT_MOBILE_SOURCE_PRIORITY.indexOf(subreddit)
    return if (priority >= 0) (REDDIT_MOBILE_SOURCE_PRIORITY.size - priority) * 2 else 0
}

private fun Wallpaper.isPortraitPreferred(): Boolean = height > 0 && height >= width

private fun Wallpaper.hasHighResolution(): Boolean =
    width >= 2160 || height >= 2160 || (width.toLong() * height.toLong()) >= 3_400_000L

private fun Wallpaper.isAmoledFriendly(): Boolean {
    val terms = searchableTerms()
    if (terms.any { it in AMOLED_TERMS }) return true
    return colors.any { color ->
        val clean = color.removePrefix("#")
        clean.length == 6 && runCatching { clean.toInt(16) }.getOrNull()?.let { rgb ->
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            (r + g + b) < 90
        } == true
    }
}

private fun Wallpaper.isIconSafe(): Boolean {
    val terms = searchableTerms()
    if (terms.any { it in BUSY_WALLPAPER_TERMS }) return false
    if (terms.any { it in CLEAN_WALLPAPER_TERMS }) return true
    return colors.size in 1..3 || category.contains("minimal", ignoreCase = true)
}

private val WORD_SPLIT_REGEX = Regex("[^a-zA-Z0-9]+")

private val REDDIT_MOBILE_SOURCE_PRIORITY = listOf(
    "iwallpaper",
    "amoledbackgrounds",
    "mobilewallpaper",
    "animephonewallpapers",
    "phonewallpapers",
    "iphonewallpapers",
    "mobilewallpapers",
    "verticalwallpapers",
    "wqhd_wallpaper",
    "minimalwallpaper",
    "iphonexwallpapers",
)

private fun Wallpaper.searchableTerms(): List<String> =
    buildList {
        addAll(tags.map { it.normalizeFeedTerm() })
        addAll(category.split(WORD_SPLIT_REGEX).map { it.normalizeFeedTerm() })
        addAll(uploaderName.split(WORD_SPLIT_REGEX).map { it.normalizeFeedTerm() })
    }.filter { it.isNotBlank() }

private fun wallpaperKey(wallpaper: Wallpaper): String {
    val stableUrl = wallpaperUrlIdentity(wallpaper.fullUrl.ifBlank { wallpaper.thumbnailUrl })
    if (stableUrl.isNotBlank()) return stableUrl
    return listOf(
        wallpaper.id.lowercase(java.util.Locale.ROOT),
        wallpaper.width.toString(),
        wallpaper.height.toString(),
    ).joinToString("|")
}

/**
 * Feed identity for a media URL. Only the scheme and host are case-insensitive, so only they are
 * lowercased: Firebase Storage and Reddit paths are case-sensitive. The query stays, because some
 * providers name the image there (Bing's `th?id=`), except on [TRANSFORM_QUERY_HOSTS] and Lemmy
 * `pictrs` paths, where it only resizes, reformats or signs the file the path already names.
 */
internal fun wallpaperUrlIdentity(url: String): String {
    val trimmed = url.trim().substringBefore('#')
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return trimmed
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return trimmed
    val host = uri.host?.lowercase(Locale.ROOT) ?: return trimmed
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = uri.rawPath.orEmpty()
    val query = uri.rawQuery
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { TRANSFORM_QUERY_HOSTS.any { known -> host == known || host.endsWith(".$known") } }
        ?.takeUnless { "/pictrs/image/" in path }
    return "$scheme://$host$port$path" + (query?.let { "?$it" } ?: "")
}

/** Hosts whose query string only resizes, reformats or signs the file named by the path. */
private val TRANSFORM_QUERY_HOSTS = listOf(
    "redd.it",
    "redditmedia.com",
    "pexels.com",
    "pixabay.com",
    "wallhaven.cc",
    "firebasestorage.googleapis.com",
    "wikimedia.org",
)

/**
 * Hides a card whose thumbnail fingerprint matches an earlier card's, so one image posted under
 * two URLs shows once. The feed is ranked before this runs, so the card kept is the better one.
 */
internal fun coalesceContentDuplicates(
    wallpapers: List<Wallpaper>,
    fingerprints: Map<String, WallpaperFingerprint>,
): List<Wallpaper> {
    if (fingerprints.isEmpty()) return wallpapers
    val seen = HashSet<WallpaperFingerprint>()
    return wallpapers.filter { wallpaper ->
        val fingerprint = fingerprints[wallpaper.stableKey()]?.takeIf { it.isDistinctive } ?: return@filter true
        seen.add(fingerprint)
    }
}

private val PIXEL_HINT_REGEX = Regex("""(\d{3,5})\s*[xX]\s*(\d{3,5})""")

private fun String.toPixelHint(): Long {
    val match = PIXEL_HINT_REGEX.find(this) ?: return 0L
    val first = match.groupValues[1].toLongOrNull() ?: return 0L
    val second = match.groupValues[2].toLongOrNull() ?: return 0L
    return first * second
}

private fun String.normalizeFeedTerm(): String = lowercase(Locale.ROOT).trim()

private fun ContentSource.readableLabel(): String =
    name.split("_").joinToString(" ") { part ->
        part.lowercase(Locale.ROOT).replaceFirstChar { char -> char.uppercase(Locale.ROOT) }
    }

private val AMOLED_TERMS = setOf(
    "amoled", "oled", "black", "dark", "night", "midnight", "shadow", "space", "neon",
)

private val CLEAN_WALLPAPER_TERMS = setOf(
    "minimal", "clean", "gradient", "abstract", "blur", "simple", "soft", "geometry",
)

private val BUSY_WALLPAPER_TERMS = setOf(
    "text", "quote", "poster", "collage", "character", "car", "vehicle", "face", "people",
)

private val LOW_SIGNAL_WALLPAPER_TERMS = setOf(
    "logo", "brand", "advert", "promo", "screenshot", "ui", "meme", "app", "engine",
)
