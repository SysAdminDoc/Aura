package com.freevibe.data.model

import java.net.URI
import java.util.Locale

private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_NAME = 80
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_FILE_NAME = 240
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_SHORT_TEXT = 120
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_URL = 2048
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_STORAGE_PATH = 512
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_BYTES = 4 * 1024 * 1024
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_DIMENSION = 2560
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_TAGS = 8
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_TAG_LENGTH = 24
private const val MAX_COMMUNITY_WALLPAPER_UPLOAD_COLORS = 6
private val COMMUNITY_WALLPAPER_UPLOAD_CONTROL_REGEX = Regex("[\\u0000-\\u001F\\u007F]")
private val COMMUNITY_WALLPAPER_UPLOAD_WHITESPACE_REGEX = Regex("\\s+")
private val COMMUNITY_WALLPAPER_UPLOAD_TAG_REGEX = Regex("[^a-z0-9_\\- ]")
private val COMMUNITY_WALLPAPER_UPLOAD_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
private val COMMUNITY_WALLPAPER_UPLOAD_CATEGORIES = setOf(
    "abstract",
    "amoled",
    "nature",
    "minimal",
    "city",
    "space",
    "other",
)

data class CommunityWallpaperUploadMetadataInput(
    val name: String,
    val category: String,
    val tags: List<String>,
    val colors: List<String>,
    val thumbnailUrl: String,
    val fullUrl: String,
    val downloadUrl: String,
    val storagePath: String,
    val width: Int,
    val height: Int,
    val fileSize: Int,
    val fileType: String,
    val originalFileName: String,
    val uploaderLabel: String,
    val license: String,
    val rightsAttested: Boolean,
    val sourceUrl: String = "",
)

fun buildCommunityWallpaperUploadCallablePayload(input: CommunityWallpaperUploadMetadataInput): Map<String, Any> {
    val normalizedName = normalizeCommunityWallpaperUploadText(input.name, MAX_COMMUNITY_WALLPAPER_UPLOAD_NAME)
    require(normalizedName.isNotBlank()) { "Wallpaper name is required" }
    val normalizedCategory = normalizeCommunityWallpaperUploadText(input.category, 40).lowercase(Locale.ROOT)
    require(normalizedCategory in COMMUNITY_WALLPAPER_UPLOAD_CATEGORIES) { "Invalid wallpaper category" }
    val normalizedStoragePath = input.storagePath.trim().take(MAX_COMMUNITY_WALLPAPER_UPLOAD_STORAGE_PATH)
    require(normalizedStoragePath.startsWith("wallpapers/")) { "Wallpaper storage path is required" }
    require(input.width in 1..MAX_COMMUNITY_WALLPAPER_UPLOAD_DIMENSION) { "Wallpaper width is out of range" }
    require(input.height in 1..MAX_COMMUNITY_WALLPAPER_UPLOAD_DIMENSION) { "Wallpaper height is out of range" }
    require(input.fileSize in 1..MAX_COMMUNITY_WALLPAPER_UPLOAD_BYTES) { "Wallpaper file size is out of range" }
    val normalizedFileType = normalizeCommunityWallpaperUploadText(
        input.fileType,
        MAX_COMMUNITY_WALLPAPER_UPLOAD_SHORT_TEXT,
    ).lowercase(Locale.ROOT)
    require(normalizedFileType == "image/jpeg") { "Wallpaper upload file type is invalid" }
    require(input.rightsAttested) { "Wallpaper upload rights must be confirmed" }

    return mapOf(
        "name" to normalizedName,
        "category" to normalizedCategory,
        "tags" to normalizeCommunityWallpaperUploadTags(input.tags),
        "colors" to normalizeCommunityWallpaperUploadColors(input.colors),
        "thumbnailUrl" to normalizeCommunityWallpaperUploadHttpsUrl(input.thumbnailUrl, "thumbnailUrl"),
        "fullUrl" to normalizeCommunityWallpaperUploadHttpsUrl(input.fullUrl, "fullUrl"),
        "downloadUrl" to normalizeCommunityWallpaperUploadHttpsUrl(input.downloadUrl, "downloadUrl"),
        "storagePath" to normalizedStoragePath,
        "width" to input.width,
        "height" to input.height,
        "fileSize" to input.fileSize,
        "fileType" to normalizedFileType,
        "originalFileName" to normalizeCommunityWallpaperUploadText(
            input.originalFileName,
            MAX_COMMUNITY_WALLPAPER_UPLOAD_FILE_NAME,
        ).ifBlank { "community-wallpaper.jpg" },
        "uploaderLabel" to normalizeCommunityWallpaperUploadText(
            input.uploaderLabel,
            MAX_COMMUNITY_WALLPAPER_UPLOAD_SHORT_TEXT,
        ),
        "license" to normalizeCommunityUploadLicense(input.license),
        "rightsAttested" to true,
        "sourceUrl" to normalizeCommunitySourceUrl(input.sourceUrl),
    )
}

private fun normalizeCommunityWallpaperUploadText(value: String, maxLength: Int): String =
    value
        .replace(COMMUNITY_WALLPAPER_UPLOAD_CONTROL_REGEX, " ")
        .replace(COMMUNITY_WALLPAPER_UPLOAD_WHITESPACE_REGEX, " ")
        .trim()
        .take(maxLength)

private fun normalizeCommunityWallpaperUploadTags(tags: List<String>): List<String> =
    tags.asSequence()
        .map { tag ->
            normalizeCommunityWallpaperUploadText(tag, MAX_COMMUNITY_WALLPAPER_UPLOAD_TAG_LENGTH)
                .lowercase(Locale.ROOT)
                .replace(COMMUNITY_WALLPAPER_UPLOAD_TAG_REGEX, "")
                .trim()
        }
        .filter { it.length in 2..MAX_COMMUNITY_WALLPAPER_UPLOAD_TAG_LENGTH }
        .distinct()
        .take(MAX_COMMUNITY_WALLPAPER_UPLOAD_TAGS)
        .toList()

private fun normalizeCommunityWallpaperUploadColors(colors: List<String>): List<String> =
    colors.asSequence()
        .map { it.trim().uppercase(Locale.ROOT) }
        .map { color ->
            require(COMMUNITY_WALLPAPER_UPLOAD_COLOR_REGEX.matches(color)) {
                "Wallpaper upload colors must be #RRGGBB values"
            }
            color
        }
        .distinct()
        .take(MAX_COMMUNITY_WALLPAPER_UPLOAD_COLORS)
        .toList()

private fun normalizeCommunityWallpaperUploadHttpsUrl(value: String, field: String): String {
    val normalized = normalizeCommunityWallpaperUploadText(value, MAX_COMMUNITY_WALLPAPER_UPLOAD_URL)
    require(normalized.isNotBlank()) { "$field is required" }
    val uri = runCatching { URI(normalized) }.getOrNull()
    require(uri?.scheme?.lowercase(Locale.ROOT) == "https") { "$field must use HTTPS" }
    require(!uri.host.isNullOrBlank()) { "$field must include a host" }
    return normalized
}
