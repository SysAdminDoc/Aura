package com.freevibe.data.model

import androidx.annotation.StringRes
import com.freevibe.R
import com.freevibe.data.local.DEFAULT_REDDIT_VIDEO_SUBREDDITS
import com.freevibe.data.local.DEFAULT_REDDIT_WALLPAPER_SUBREDDITS
import com.freevibe.data.local.validateRedditSubredditList
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import java.util.Locale

enum class RedditFeedMediaType { WALLPAPER, VIDEO }

/** Listing orders the public Reddit feed serves. The time window only changes what Top returns. */
internal val REDDIT_FEED_SORTS = listOf("hot", "new", "top", "rising")
internal val REDDIT_FEED_TIME_WINDOWS = listOf("day", "week", "month", "year", "all")
internal const val REDDIT_FEED_PRESET_STORE_VERSION = 1
internal const val MAX_REDDIT_FEED_PRESETS_PER_TYPE = 20
internal const val MAX_REDDIT_FEED_PRESET_NAME = 40
internal const val MAX_REDDIT_FEED_MIN_DIMENSION = 8_000
internal const val MAX_REDDIT_FEED_MIN_DURATION_SECONDS = 600
internal const val MAX_REDDIT_FEED_PRESET_DOCUMENT_CHARS = 64_000
private val REDDIT_FEED_PRESET_ID = Regex("[A-Za-z0-9_-]{1,64}")

@JsonClass(generateAdapter = true)
data class RedditFeedPreset(
    val id: String,
    val name: String,
    val mediaType: RedditFeedMediaType,
    val subreddits: List<String>,
    val sort: String = "new",
    val timeWindow: String = "week",
    /** Lets posts marked adult through, and only while Show NSFW content is on as well. */
    val includeAdult: Boolean = false,
    /** Minimums judge the posts that list a size or a length. Posts that don't still show. */
    val minWidth: Int = 0,
    val minHeight: Int = 0,
    val minDurationSeconds: Int = 0,
)

@JsonClass(generateAdapter = true)
data class RedditFeedPresetStore(
    val version: Int = REDDIT_FEED_PRESET_STORE_VERSION,
    val presets: List<RedditFeedPreset> = emptyList(),
    val activeWallpaperPresetId: String = "",
    val activeVideoPresetId: String = "",
    /** The wallpaper preset rotation draws from. Blank follows the one active on the Reddit tab. */
    val rotationPresetId: String = "",
)

/** The editable presets Aura starts with. None of them lets adult posts through. */
internal enum class RedditFeedDefault(
    val id: String,
    @StringRes val nameRes: Int,
    val mediaType: RedditFeedMediaType,
    val subreddits: String,
    val sort: String,
    val timeWindow: String = "week",
    val minHeight: Int = 0,
) {
    PHONE("default_phone", R.string.reddit_preset_default_phone, RedditFeedMediaType.WALLPAPER, DEFAULT_REDDIT_WALLPAPER_SUBREDDITS, "new"),
    AMOLED("default_amoled", R.string.reddit_preset_default_amoled, RedditFeedMediaType.WALLPAPER, "Amoledbackgrounds,MinimalWallpaper", "top"),
    ANIME("default_anime", R.string.reddit_preset_default_anime, RedditFeedMediaType.WALLPAPER, "AnimePhoneWallpapers,Animewallpaper", "top"),
    HIGH_RES(
        "default_high_res",
        R.string.reddit_preset_default_high_res,
        RedditFeedMediaType.WALLPAPER,
        "WQHD_Wallpaper,MobileWallpaper,Verticalwallpapers,iWallpaper",
        "top",
        timeWindow = "month",
        minHeight = 2400,
    ),
    MOTION("default_motion", R.string.reddit_preset_default_motion, RedditFeedMediaType.VIDEO, DEFAULT_REDDIT_VIDEO_SUBREDDITS, "new"),
    CINEMAGRAPHS("default_cinemagraphs", R.string.reddit_preset_default_cinemagraphs, RedditFeedMediaType.VIDEO, "Cinemagraphs,perfectloops", "top"),
    ;

    fun preset(name: String) = RedditFeedPreset(
        id = id,
        name = name,
        mediaType = mediaType,
        subreddits = validateRedditSubredditList(subreddits).subreddits,
        sort = sort,
        timeWindow = timeWindow,
        minHeight = minHeight,
    )
}

// Lists older builds shipped as their defaults. A saved copy of one isn't a choice worth keeping.
private const val LEGACY_REDDIT_WALLPAPER_SUBREDDITS =
    "wallpapers,MobileWallpaper,wallpaper,WQHD_Wallpaper,MinimalWallpaper,phonewallpapers,iWallpaper"
private const val LEGACY_REDDIT_VIDEO_SUBREDDITS = "livewallpapers,LiveWallpaper,Cinemagraphs,perfectloops"

/**
 * The presets a device starts with. A custom subreddit list from before presets becomes its own
 * preset, first and active, so nobody loses the feed they set up.
 */
internal fun defaultRedditFeedPresetStore(
    name: (Int) -> String,
    legacyWallpaperSubreddits: String = "",
    legacyVideoSubreddits: String = "",
): RedditFeedPresetStore {
    val custom = listOfNotNull(
        legacyCustomPreset(
            id = "legacy_wallpaper",
            mediaType = RedditFeedMediaType.WALLPAPER,
            raw = legacyWallpaperSubreddits,
            ignored = listOf(DEFAULT_REDDIT_WALLPAPER_SUBREDDITS, LEGACY_REDDIT_WALLPAPER_SUBREDDITS),
            name = name,
        ),
        legacyCustomPreset(
            id = "legacy_video",
            mediaType = RedditFeedMediaType.VIDEO,
            raw = legacyVideoSubreddits,
            ignored = listOf(DEFAULT_REDDIT_VIDEO_SUBREDDITS, LEGACY_REDDIT_VIDEO_SUBREDDITS),
            name = name,
        ),
    )
    val presets = custom + RedditFeedDefault.entries.map { it.preset(name(it.nameRes)) }
    return RedditFeedPresetStore(
        presets = presets,
        activeWallpaperPresetId = presets.first { it.mediaType == RedditFeedMediaType.WALLPAPER }.id,
        activeVideoPresetId = presets.first { it.mediaType == RedditFeedMediaType.VIDEO }.id,
    )
}

private fun legacyCustomPreset(
    id: String,
    mediaType: RedditFeedMediaType,
    raw: String,
    ignored: List<String>,
    name: (Int) -> String,
): RedditFeedPreset? {
    // r/LiveWallpaper closed, so an old video list never carries it forward.
    val subreddits = validateRedditSubredditList(raw).subreddits
        .filterNot { mediaType == RedditFeedMediaType.VIDEO && it.equals("LiveWallpaper", ignoreCase = true) }
    val key = subreddits.lowercaseKey()
    if (subreddits.isEmpty() || ignored.any { validateRedditSubredditList(it).subreddits.lowercaseKey() == key }) return null
    return RedditFeedPreset(
        id = id,
        name = name(R.string.reddit_preset_legacy_custom),
        mediaType = mediaType,
        subreddits = subreddits,
    )
}

private fun List<String>.lowercaseKey() = map { it.lowercase(Locale.ROOT) }

/** Cleans each preset and drops the ones that can't load: no valid subreddit, no name, a bad or repeated ID. */
internal fun RedditFeedPresetStore.sanitized(): RedditFeedPresetStore {
    val ids = mutableSetOf<String>()
    val kept = presets.mapNotNull { it.sanitized() }.filter { ids.add(it.id) }
    return copy(
        version = REDDIT_FEED_PRESET_STORE_VERSION,
        presets = RedditFeedMediaType.entries.flatMap { type ->
            kept.filter { it.mediaType == type }.take(MAX_REDDIT_FEED_PRESETS_PER_TYPE)
        },
    )
}

/** [sanitized], then refilled from [defaults] where a media type has nothing left, with every choice pointing somewhere real. */
internal fun RedditFeedPresetStore.normalized(defaults: RedditFeedPresetStore): RedditFeedPresetStore {
    val clean = sanitized()
    val presets = RedditFeedMediaType.entries.flatMap { type ->
        clean.presets.filter { it.mediaType == type }.ifEmpty { defaults.presets.filter { it.mediaType == type } }
    }
    fun chosen(type: RedditFeedMediaType, id: String) =
        id.takeIf { wanted -> presets.any { it.id == wanted && it.mediaType == type } }
            ?: presets.first { it.mediaType == type }.id
    return clean.copy(
        presets = presets,
        activeWallpaperPresetId = chosen(RedditFeedMediaType.WALLPAPER, activeWallpaperPresetId),
        activeVideoPresetId = chosen(RedditFeedMediaType.VIDEO, activeVideoPresetId),
        rotationPresetId = rotationPresetId
            .takeIf { id -> presets.any { it.id == id && it.mediaType == RedditFeedMediaType.WALLPAPER } }
            .orEmpty(),
    )
}

private fun RedditFeedPreset.sanitized(): RedditFeedPreset? {
    // Subreddits that are gone or misspelled drop out, and the preset keeps the rest.
    val validSubreddits = validateRedditSubredditList(subreddits.joinToString(",")).subreddits
    val cleanName = name.trim().take(MAX_REDDIT_FEED_PRESET_NAME)
    if (!REDDIT_FEED_PRESET_ID.matches(id) || cleanName.isBlank() || validSubreddits.isEmpty()) return null
    return copy(
        name = cleanName,
        subreddits = validSubreddits,
        sort = sort.lowercase(Locale.ROOT).takeIf { it in REDDIT_FEED_SORTS } ?: "new",
        timeWindow = timeWindow.lowercase(Locale.ROOT).takeIf { it in REDDIT_FEED_TIME_WINDOWS } ?: "week",
        minWidth = minWidth.coerceIn(0, MAX_REDDIT_FEED_MIN_DIMENSION),
        minHeight = minHeight.coerceIn(0, MAX_REDDIT_FEED_MIN_DIMENSION),
        minDurationSeconds = minDurationSeconds.coerceIn(0, MAX_REDDIT_FEED_MIN_DURATION_SECONDS),
    )
}

internal fun RedditFeedPresetStore.presetsOf(type: RedditFeedMediaType): List<RedditFeedPreset> =
    presets.filter { it.mediaType == type }

internal fun RedditFeedPresetStore.activePreset(type: RedditFeedMediaType): RedditFeedPreset {
    val activeId = if (type == RedditFeedMediaType.WALLPAPER) activeWallpaperPresetId else activeVideoPresetId
    return presets.firstOrNull { it.id == activeId && it.mediaType == type } ?: presetsOf(type).first()
}

internal fun RedditFeedPresetStore.rotationPreset(): RedditFeedPreset =
    presets.firstOrNull { it.id == rotationPresetId && it.mediaType == RedditFeedMediaType.WALLPAPER }
        ?: activePreset(RedditFeedMediaType.WALLPAPER)

/** Saves [preset] over the one with its ID, or adds it when it's new. An edit never changes a preset's media type. */
internal fun RedditFeedPresetStore.upsert(preset: RedditFeedPreset): RedditFeedPresetStore {
    val existing = presets.firstOrNull { it.id == preset.id }
        ?: return if (presetsOf(preset.mediaType).size < MAX_REDDIT_FEED_PRESETS_PER_TYPE) copy(presets = presets + preset) else this
    return copy(presets = presets.map { if (it.id == preset.id) preset.copy(mediaType = existing.mediaType) else it })
}

internal fun RedditFeedPresetStore.duplicate(id: String, newId: String, copyName: (String) -> String): RedditFeedPresetStore {
    val index = presets.indexOfFirst { it.id == id }
    if (index < 0 || presetsOf(presets[index].mediaType).size >= MAX_REDDIT_FEED_PRESETS_PER_TYPE) return this
    val source = presets[index]
    val copy = source.copy(id = newId, name = copyName(source.name).take(MAX_REDDIT_FEED_PRESET_NAME))
    return copy(presets = presets.toMutableList().apply { add(index + 1, copy) })
}

/** Moves a preset [offset] places among the presets of its own media type. */
internal fun RedditFeedPresetStore.move(id: String, offset: Int): RedditFeedPresetStore {
    val preset = presets.firstOrNull { it.id == id } ?: return this
    val sameType = presetsOf(preset.mediaType).toMutableList()
    val from = sameType.indexOf(preset)
    val to = (from + offset).coerceIn(0, sameType.lastIndex)
    if (from == to) return this
    sameType.add(to, sameType.removeAt(from))
    val reordered = sameType.iterator()
    return copy(presets = presets.map { if (it.mediaType == preset.mediaType) reordered.next() else it })
}

/** Deletes a preset unless it's the last of its media type. Whatever pointed at it falls back. */
internal fun RedditFeedPresetStore.delete(id: String): RedditFeedPresetStore {
    val preset = presets.firstOrNull { it.id == id } ?: return this
    if (presetsOf(preset.mediaType).size <= 1) return this
    val remaining = presets.filterNot { it.id == id }
    val fallback = remaining.first { it.mediaType == preset.mediaType }.id
    return copy(
        presets = remaining,
        activeWallpaperPresetId = if (activeWallpaperPresetId == id) fallback else activeWallpaperPresetId,
        activeVideoPresetId = if (activeVideoPresetId == id) fallback else activeVideoPresetId,
        rotationPresetId = if (rotationPresetId == id) "" else rotationPresetId,
    )
}

internal fun RedditFeedPresetStore.activate(id: String): RedditFeedPresetStore {
    val preset = presets.firstOrNull { it.id == id } ?: return this
    return when (preset.mediaType) {
        RedditFeedMediaType.WALLPAPER -> copy(activeWallpaperPresetId = id)
        RedditFeedMediaType.VIDEO -> copy(activeVideoPresetId = id)
    }
}

internal fun RedditFeedPresetStore.bindRotation(id: String): RedditFeedPresetStore = copy(
    rotationPresetId = id.takeIf { wanted ->
        presets.any { it.id == wanted && it.mediaType == RedditFeedMediaType.WALLPAPER }
    }.orEmpty(),
)

/**
 * Restores presets from a backup. A preset with the same ID comes back as the backup had it, new ones
 * are added, and the backup's active and rotation choices win where they still point at a preset.
 */
internal fun RedditFeedPresetStore.mergeImported(imported: RedditFeedPresetStore): RedditFeedPresetStore {
    val incoming = imported.presets.associateBy { it.id }
    val merged = presets.map { incoming[it.id] ?: it } + imported.presets.filter { new -> presets.none { it.id == new.id } }
    fun pick(importedId: String, currentId: String) = importedId.takeIf { id -> merged.any { it.id == id } } ?: currentId
    return copy(
        presets = merged,
        activeWallpaperPresetId = pick(imported.activeWallpaperPresetId, activeWallpaperPresetId),
        activeVideoPresetId = pick(imported.activeVideoPresetId, activeVideoPresetId),
        rotationPresetId = pick(imported.rotationPresetId, rotationPresetId),
    )
}

private val ADULT_TITLE_MARKER = Regex("""(?i)\b(?:nsfw|nsfl|nudes?|nudity|porn\w*|hentai|lewd)\b|\b18\+""")

internal fun isMarkedAdult(title: String): Boolean = ADULT_TITLE_MARKER.containsMatchIn(title)

/** Adult posts need both the preset's own switch and the app-wide Show NSFW content setting. */
internal fun RedditFeedPreset.allowsAdult(showAdultContent: Boolean): Boolean = includeAdult && showAdultContent

/** Whether a post belongs in this preset's feed. A size or length the post doesn't list never counts against it. */
internal fun RedditFeedPreset.accepts(
    title: String,
    width: Int,
    height: Int,
    durationSeconds: Long = 0,
    showAdultContent: Boolean,
): Boolean {
    if (!allowsAdult(showAdultContent) && isMarkedAdult(title)) return false
    if (width > 0 && height > 0 && (width < minWidth || height < minHeight)) return false
    return durationSeconds <= 0 || durationSeconds >= minDurationSeconds
}

/** Everything that changes what the feed returns, so caches and cursors never cross presets. */
internal fun RedditFeedPreset.feedSignature(showAdultContent: Boolean): String = listOf(
    subreddits.lowercaseKey().joinToString("+"),
    sort,
    if (sort == "top") timeWindow else "",
    if (allowsAdult(showAdultContent)) "adult" else "safe",
    "${minWidth}x$minHeight",
    "${minDurationSeconds}s",
).joinToString("|")

/** A diagnostics line with counts and settings only. Preset and subreddit names never leave the device. */
internal fun RedditFeedPresetStore.diagnosticsSummary(showAdultContent: Boolean): String {
    fun describe(preset: RedditFeedPreset) = listOf(
        if (preset.id.startsWith("default_")) "built-in" else "custom",
        if (preset.sort == "top") "top/${preset.timeWindow}" else preset.sort,
        "${preset.subreddits.size} subreddits",
        if (preset.allowsAdult(showAdultContent)) "adult allowed" else "safe",
        "min ${preset.minWidth}x${preset.minHeight} ${preset.minDurationSeconds}s",
    ).joinToString(", ")
    val rotation = if (rotationPresetId.isBlank()) "follows the Reddit tab" else describe(rotationPreset())
    return "${presetsOf(RedditFeedMediaType.WALLPAPER).size} wallpaper, ${presetsOf(RedditFeedMediaType.VIDEO).size} video; " +
        "wallpaper feed: ${describe(activePreset(RedditFeedMediaType.WALLPAPER))}; " +
        "video feed: ${describe(activePreset(RedditFeedMediaType.VIDEO))}; rotation: $rotation"
}

/** Reads a stored store, falling back to [defaults] when nothing is saved or it can't be read. */
internal fun decodeRedditFeedPresetStore(
    adapter: JsonAdapter<RedditFeedPresetStore>,
    raw: String?,
    defaults: RedditFeedPresetStore,
): RedditFeedPresetStore {
    if (raw.isNullOrBlank() || raw.length > MAX_REDDIT_FEED_PRESET_DOCUMENT_CHARS) return defaults
    val stored = runCatching { adapter.fromJson(raw) }.getOrNull() ?: return defaults
    return stored.normalized(defaults)
}

/** Reads presets from a backup, refusing anything this version can't restore exactly. */
internal fun parseRedditFeedPresetStoreStrict(adapter: JsonAdapter<RedditFeedPresetStore>, raw: String): RedditFeedPresetStore {
    require(raw.length <= MAX_REDDIT_FEED_PRESET_DOCUMENT_CHARS) { "Reddit feed preset data is too large" }
    val store = adapter.fromJson(raw) ?: throw IllegalArgumentException("Invalid Reddit feed preset data")
    require(store.version in 1..REDDIT_FEED_PRESET_STORE_VERSION) { "Unsupported Reddit feed preset version" }
    require(store.presets.size <= MAX_REDDIT_FEED_PRESETS_PER_TYPE * RedditFeedMediaType.entries.size) {
        "Too many Reddit feed presets"
    }
    return store.sanitized()
}
