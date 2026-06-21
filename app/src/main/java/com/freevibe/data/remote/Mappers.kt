package com.freevibe.data.remote

import com.freevibe.data.model.*
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.bing.BingImage
import com.freevibe.data.remote.lemmy.LemmyPostView
import com.freevibe.data.remote.nasa.NasaApodResponse
import com.freevibe.data.remote.pixabay.PixabayPhoto
import com.freevibe.data.remote.reddit.RedditPost
import com.freevibe.data.remote.wallhaven.WallhavenWallpaper
import com.freevibe.data.remote.wikimedia.WikimediaPotdImage

// -- Wallhaven -> Wallpaper --

fun WallhavenWallpaper.toWallpaper() = Wallpaper(
    id = "wh_$id",
    source = ContentSource.WALLHAVEN,
    thumbnailUrl = thumbs.large.ifEmpty { thumbs.original },
    fullUrl = path,
    width = dimensionX,
    height = dimensionY,
    category = category,
    tags = tags?.map { it.name } ?: emptyList(),
    colors = colors,
    fileSize = fileSize,
    fileType = fileType,
    sourcePageUrl = url,
    views = views,
    favorites = favorites,
)

// -- Bing Daily -> Wallpaper --

private val BING_COPYRIGHT_REGEX = Regex("""\(([^)]+)\)""")

fun BingImage.toWallpaper(bingBaseUrl: String = BingDailyApi.BASE_URL) = Wallpaper(
    id = "bing_${startDate}_${urlbase.hashCode().toUInt()}",
    source = ContentSource.BING,
    thumbnailUrl = BingDailyApi.thumbUrl(urlbase, bingBaseUrl),
    fullUrl = BingDailyApi.fullUrl(urlbase, bingBaseUrl),
    width = 3840,  // UHD
    height = 2160,
    category = "daily",
    tags = listOf("bing", "daily", "curated"),
    sourcePageUrl = copyrightLink,
    uploaderName = BING_COPYRIGHT_REGEX.find(copyright)?.groupValues?.get(1)
        ?: copyright.take(80),
)

// -- NASA APOD -> Wallpaper --

fun NasaApodResponse.toWallpaper(): Wallpaper? {
    if (mediaType != "image") return null
    val imageUrl = hdUrl ?: url
    if (imageUrl.isBlank()) return null
    return Wallpaper(
        id = "nasa_apod_$date",
        source = ContentSource.NASA,
        thumbnailUrl = thumbnailUrl ?: url,
        fullUrl = imageUrl,
        width = 0,
        height = 0,
        category = "astronomy",
        tags = listOf("nasa", "apod", "astronomy", "space"),
        sourcePageUrl = "https://apod.nasa.gov/apod/ap${date.replace("-", "").drop(2)}.html",
        uploaderName = copyright?.trim() ?: "NASA",
    )
}

// -- Wikipedia POTD -> Wallpaper --

private val HTML_TAG_REGEX = Regex("<[^>]+>")

fun WikimediaPotdImage.toWallpaper(date: String): Wallpaper? {
    val fullSource = image?.source ?: return null
    if (fullSource.isBlank()) return null
    val thumbSource = thumbnail?.source ?: fullSource
    val artistName = artist?.text
        ?.replace(HTML_TAG_REGEX, "")
        ?.trim()
        ?.take(80)
        ?: "Wikimedia Commons"
    return Wallpaper(
        id = "wiki_potd_$date",
        source = ContentSource.WIKIMEDIA,
        thumbnailUrl = thumbSource,
        fullUrl = fullSource,
        width = image.width,
        height = image.height,
        category = "photography",
        tags = listOf("wikipedia", "potd", "featured", "commons"),
        sourcePageUrl = filePage ?: "",
        uploaderName = artistName,
    )
}

// -- Pixabay -> Wallpaper --

fun PixabayPhoto.toWallpaper() = Wallpaper(
    id = "pb_$id",
    source = ContentSource.PIXABAY,
    thumbnailUrl = webformatUrl,
    fullUrl = largeImageUrl,
    width = imageWidth,
    height = imageHeight,
    tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    fileSize = imageSize,
    sourcePageUrl = pageUrl,
    uploaderName = user,
    views = views,
    favorites = likes,
)

// -- Domain -> FavoriteEntity --

fun Wallpaper.toFavoriteEntity() = FavoriteEntity(
    id = id,
    source = source.name,
    type = "WALLPAPER",
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
    tags = tags.takeIf { it.isNotEmpty() }?.joinToString(" ||| "),
    colors = colors.takeIf { it.isNotEmpty() }?.joinToString(" ||| "),
    category = category.takeIf { it.isNotEmpty() },
    uploaderName = uploaderName.takeIf { it.isNotEmpty() },
    sourcePageUrl = sourcePageUrl.takeIf { it.isNotEmpty() },
    license = license.takeIf { it.isNotEmpty() },
    fileSize = fileSize.takeIf { it > 0 },
    fileType = fileType.takeIf { it.isNotEmpty() },
    views = views.toLong().takeIf { it > 0 },
    favoritesCount = favorites.toLong().takeIf { it > 0 },
    sourceAvailability = normalizeSourceAvailability(sourceAvailability),
    sourceAvailabilityReason = sourceAvailabilityReason.takeIf { it.isNotBlank() },
)

fun Sound.toFavoriteEntity() = FavoriteEntity(
    id = id,
    source = source.name,
    type = "SOUND",
    thumbnailUrl = "",
    fullUrl = when (source) {
        ContentSource.YOUTUBE -> sourcePageUrl.ifBlank { downloadUrl.ifBlank { previewUrl } }
        else -> downloadUrl.ifBlank { previewUrl }
    },
    name = name,
    duration = duration,
    tags = tags.takeIf { it.isNotEmpty() }?.joinToString(" ||| "),
    category = null,
    uploaderName = uploaderName.takeIf { it.isNotEmpty() },
    sourcePageUrl = sourcePageUrl.takeIf { it.isNotEmpty() },
    license = license.takeIf { it.isNotEmpty() },
    fileSize = fileSize.takeIf { it > 0 },
    fileType = fileType.takeIf { it.isNotEmpty() },
    sourceAvailability = normalizeSourceAvailability(sourceAvailability),
    sourceAvailabilityReason = sourceAvailabilityReason.takeIf { it.isNotBlank() },
)

// -- FavoriteEntity -> Domain --

fun FavoriteEntity.toWallpaper() = Wallpaper(
    id = id,
    source = try { ContentSource.valueOf(source) } catch (_: Exception) { ContentSource.WALLHAVEN },
    thumbnailUrl = thumbnailUrl,
    fullUrl = offlinePath.ifBlank { fullUrl },
    width = width,
    height = height,
    tags = tags?.split(" ||| ")?.filter { it.isNotEmpty() } ?: emptyList(),
    colors = colors?.split(" ||| ")?.filter { it.isNotEmpty() } ?: emptyList(),
    category = category ?: "",
    uploaderName = uploaderName ?: "",
    sourcePageUrl = sourcePageUrl ?: "",
    license = license ?: "",
    fileSize = fileSize ?: 0L,
    fileType = fileType ?: "",
    views = views?.toInt() ?: 0,
    favorites = favoritesCount?.toInt() ?: 0,
    sourceAvailability = normalizeSourceAvailability(sourceAvailability),
    sourceAvailabilityReason = sourceAvailabilityReason ?: "",
)

fun FavoriteEntity.toSound(): Sound {
    val restoredSource = try {
        ContentSource.valueOf(source)
    } catch (_: Exception) {
        ContentSource.FREESOUND
    }
    val restoredSourcePageUrl = when {
        !sourcePageUrl.isNullOrBlank() -> sourcePageUrl
        restoredSource == ContentSource.YOUTUBE && fullUrl.isYouTubePageUrl() -> fullUrl
        else -> ""
    }
    val restoredDirectUrl = if (restoredSource == ContentSource.YOUTUBE) "" else fullUrl

    return Sound(
        id = id,
        source = restoredSource,
        name = name,
        previewUrl = restoredDirectUrl,
        downloadUrl = restoredDirectUrl,
        duration = duration,
        tags = tags?.split(" ||| ")?.filter { it.isNotEmpty() } ?: emptyList(),
        license = license ?: "",
        uploaderName = uploaderName ?: "",
        sourcePageUrl = restoredSourcePageUrl ?: "",
        fileSize = fileSize ?: 0L,
        fileType = fileType ?: "",
        sourceAvailability = normalizeSourceAvailability(sourceAvailability),
        sourceAvailabilityReason = sourceAvailabilityReason ?: "",
    )
}

private fun String.isYouTubePageUrl(): Boolean =
    contains("youtube.com", ignoreCase = true) || contains("youtu.be", ignoreCase = true)

// -- Reddit Post -> Wallpaper --

fun RedditPost.toWallpaper(): Wallpaper {
    val res = parsedResolution
    return Wallpaper(
        id = "rd_$id",
        source = ContentSource.REDDIT,
        thumbnailUrl = thumbUrl,
        fullUrl = imageUrl,
        width = res?.first ?: 0,
        height = res?.second ?: 0,
        category = subreddit,
        tags = listOf(subreddit),
        sourcePageUrl = "https://www.reddit.com$permalink",
        uploaderName = author,
    )
}

// -- Lemmy -> Wallpaper --

private val IMAGE_URL_REGEX = Regex("""(?i)\.(jpe?g|png|webp|gif|avif|heic)(\?.*)?$""")

fun LemmyPostView.toWallpaper(): Wallpaper? {
    val imageUrl = post.url ?: return null
    if (!IMAGE_URL_REGEX.containsMatchIn(imageUrl)) return null
    if (post.nsfw) return null
    return Wallpaper(
        id = "lemmy_${post.id}",
        source = ContentSource.LEMMY,
        thumbnailUrl = post.thumbnailUrl ?: imageUrl,
        fullUrl = imageUrl,
        width = 0,
        height = 0,
        category = "community",
        tags = listOf("lemmy", "community"),
        sourcePageUrl = post.apId.ifBlank { "https://lemmy.world/post/${post.id}" },
        uploaderName = creator.displayName ?: creator.name,
        views = counts.score,
        favorites = counts.upvotes,
    )
}

