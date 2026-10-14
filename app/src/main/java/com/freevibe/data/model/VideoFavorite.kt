package com.freevibe.data.model

import com.freevibe.service.isAllowedImportedHttpsUrl
import com.freevibe.ui.screens.videowallpapers.VideoWallpaperItem
import com.freevibe.ui.screens.videowallpapers.isHlsMotionUrl
import com.freevibe.ui.screens.videowallpapers.redditHostedVideoId
import com.freevibe.ui.screens.videowallpapers.redditPlayableMotionUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * A provider a motion wallpaper can be saved from. [idPrefix] and [label] are the ones the Videos
 * feed gives its items, so a saved video keeps the identity its download and apply records use.
 */
private data class VideoFavoriteProvider(
    val source: ContentSource,
    val idPrefix: String,
    val label: String,
)

private val VIDEO_FAVORITE_PROVIDERS = listOf(
    VideoFavoriteProvider(ContentSource.YOUTUBE, "yt_", "YouTube"),
    VideoFavoriteProvider(ContentSource.REDDIT, "rd_", "Reddit"),
    VideoFavoriteProvider(ContentSource.PEXELS, "px_", "Pexels"),
    VideoFavoriteProvider(ContentSource.PIXABAY, "pbv_", "Pixabay"),
)

private val YOUTUBE_FAVORITE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private val YOUTUBE_WATCH_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
private const val YOUTUBE_SHORT_HOST = "youtu.be"

/** How a saved video's stream is found again when it's opened. */
sealed interface VideoFavoriteStream {
    /** A YouTube stream address expires within hours, so it is resolved again from the video ID. */
    data class YouTube(val videoId: String) : VideoFavoriteStream

    /** An address that stays valid: Reddit's playlist rebuilt from the clip ID, or a provider's file. */
    data class Direct(val url: String) : VideoFavoriteStream
}

private fun videoFavoriteProvider(source: String): VideoFavoriteProvider? {
    val name = source.trim().uppercase(Locale.ROOT)
    return VIDEO_FAVORITE_PROVIDERS.firstOrNull { it.source.name == name }
}

private fun youTubeWatchVideoId(url: String): String? {
    val parsed = url.trim().toHttpUrlOrNull()?.takeIf { it.isHttps } ?: return null
    return when (parsed.host.lowercase(Locale.ROOT)) {
        in YOUTUBE_WATCH_HOSTS -> parsed.queryParameter("v")
        YOUTUBE_SHORT_HOST -> parsed.pathSegments.firstOrNull()
        else -> null
    }
}

/**
 * The way back to a saved video's stream, or null when the row can't name one.
 *
 * The same rule runs when a video is saved, when a backup is read and when a favorite is opened, so
 * a row that passes here is one the preview, Apply and Download can use.
 *
 * - YouTube rows are known by the video ID in [id]. [fullUrl] has to be that video's own page.
 * - A Reddit-hosted clip is known by its clip ID. Its playlist address is rebuilt from the ID, so a
 *   playlist on any other host is refused: the downloader would otherwise be sent wherever the row
 *   pointed.
 * - Anything else has to be an https file address.
 */
fun videoFavoriteStream(source: String, id: String, fullUrl: String): VideoFavoriteStream? {
    val provider = videoFavoriteProvider(source) ?: return null
    val providerId = id.trim().removePrefix(provider.idPrefix)
    if (!id.trim().startsWith(provider.idPrefix) || providerId.isBlank()) return null
    val locator = fullUrl.trim()
    if (provider.source == ContentSource.YOUTUBE) {
        if (!YOUTUBE_FAVORITE_VIDEO_ID.matches(providerId)) return null
        return VideoFavoriteStream.YouTube(providerId).takeIf { youTubeWatchVideoId(locator) == providerId }
    }
    if (!isAllowedImportedHttpsUrl(locator)) return null
    val playable = if (provider.source == ContentSource.REDDIT) redditPlayableMotionUrl(locator) else locator
    if (isHlsMotionUrl(playable)) {
        if (provider.source != ContentSource.REDDIT) return null
        val clipId = redditHostedVideoId(playable) ?: return null
        return VideoFavoriteStream.Direct(redditPlayableMotionUrl("https://v.redd.it/$clipId"))
    }
    return VideoFavoriteStream.Direct(playable)
}

/**
 * The address a VIDEO favorite keeps in `fullUrl`, in the one form the app writes it, or null when
 * the row is not a usable video favorite. YouTube keeps the page address and never a stream.
 */
fun normalizedVideoFavoriteLocator(source: String, id: String, fullUrl: String): String? =
    when (val stream = videoFavoriteStream(source, id, fullUrl)) {
        is VideoFavoriteStream.YouTube -> youTubeWatchUrl(stream.videoId)
        is VideoFavoriteStream.Direct -> stream.url
        null -> null
    }

private fun youTubeWatchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

fun FavoriteEntity.isVideoFavorite(): Boolean = type.equals(MEDIA_TYPE_VIDEO, ignoreCase = true)

fun FavoriteEntity.videoFavoriteStream(): VideoFavoriteStream? =
    if (isVideoFavorite()) videoFavoriteStream(source, id, fullUrl) else null

fun VideoWallpaperItem.videoFavoriteIdentity(): FavoriteIdentity = FavoriteIdentity(
    id = id,
    source = contentSource.name,
    type = MEDIA_TYPE_VIDEO,
)

/**
 * The row that saves this video, or null when it can't be found again later (a video picked from
 * the phone, or a feed item whose stream address isn't known yet).
 *
 * [streamUrl] is the address the feed plays. It is kept for Reddit, Pexels and Pixabay, where it
 * stays valid. For YouTube it is ignored and the page address is kept instead.
 */
fun VideoWallpaperItem.toVideoFavoriteEntity(
    streamUrl: String?,
    addedAt: Long = System.currentTimeMillis(),
): FavoriteEntity? {
    val locatorSource = when (contentSource) {
        ContentSource.YOUTUBE -> videoId.takeIf { it.isNotBlank() }?.let(::youTubeWatchUrl)
        else -> streamUrl
    } ?: return null
    val locator = normalizedVideoFavoriteLocator(contentSource.name, id, locatorSource) ?: return null
    return FavoriteEntity(
        id = id,
        source = contentSource.name,
        type = MEDIA_TYPE_VIDEO,
        thumbnailUrl = thumbnailUrl,
        fullUrl = locator,
        name = title,
        width = videoWidth,
        height = videoHeight,
        duration = duration.coerceAtLeast(0L).toDouble(),
        addedAt = addedAt,
        uploaderName = uploaderName.takeIf { it.isNotBlank() },
        sourcePageUrl = sourcePageUrl.takeIf { it.isNotBlank() },
        license = license.takeIf { it.isNotBlank() },
        fileType = videoMimeType.takeIf { it.isNotBlank() },
        views = popularity.takeIf { it > 0 },
    )
}

/**
 * The feed item a saved video stands for, or null when the row isn't a usable video favorite.
 *
 * Apply, Download and Share take this item through the same license checks as a feed item, and the
 * ID and provider label match the feed's, so a copy already saved to Downloads is found and reused.
 */
fun FavoriteEntity.toVideoWallpaperItem(): VideoWallpaperItem? {
    val provider = videoFavoriteProvider(source) ?: return null
    val stream = videoFavoriteStream() ?: return null
    return VideoWallpaperItem(
        id = id,
        title = name,
        thumbnailUrl = thumbnailUrl,
        source = provider.label,
        duration = duration.toLong().coerceAtLeast(0L),
        uploaderName = uploaderName.orEmpty(),
        videoId = (stream as? VideoFavoriteStream.YouTube)?.videoId.orEmpty(),
        popularity = (views ?: 0L).coerceAtLeast(0L),
        videoWidth = width.coerceAtLeast(0),
        videoHeight = height.coerceAtLeast(0),
        videoMimeType = fileType.orEmpty(),
        contentSource = provider.source,
        license = license.orEmpty(),
        sourcePageUrl = sourcePageUrl.orEmpty(),
    )
}

/** True when this video comes from a provider it can be found at again. A clip picked from the phone can't be saved. */
fun VideoWallpaperItem.canBeVideoFavorite(): Boolean = videoFavoriteProvider(contentSource.name) != null
